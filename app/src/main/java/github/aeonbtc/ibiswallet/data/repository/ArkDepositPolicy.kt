package github.aeonbtc.ibiswallet.data.repository

import github.aeonbtc.ibiswallet.data.model.ArkMovement
import java.time.Instant
import java.time.OffsetDateTime
import java.util.Locale

/**
 * Pure helpers for Ark on-chain deposit balance paint and confirmation display.
 * Bark 0.6 keeps on-chain balance and board history on separate paths; Ibis bridges them.
 *
 * Mailbox recovery restores spendable VTXOs, not full movement history. Fresh session DBs
 * often return empty [Wallet.history] while balance/VTXOs are non-zero — preserve prior
 * history rows until Bark publishes them again.
 */
object ArkDepositPolicy {
    /** Synthetic pending on-chain deposit row id (ArkRepository) — legacy single-row id. */
    const val PENDING_ONCHAIN_DEPOSIT_MOVEMENT_ID: Int = -1

    /** History status: confirmed on-chain but below ASP min board amount. */
    const val STATUS_BELOW_MIN: String = "below_min"

    /** History status: below-min deposit swept back to Layer 1. */
    const val STATUS_RECOVERED_L1: String = "recovered_l1"

    /** Synthetic recovered-deposit row ids are negative and not equal to [PENDING_ONCHAIN_DEPOSIT_MOVEMENT_ID]. */
    fun recoveredOnchainMovementId(fundingTxid: String): Int {
        val hash = fundingTxid.trim().lowercase(Locale.US).hashCode()
        // Keep in negative range, avoid -1 collision.
        val id = -(kotlin.math.abs(hash % 1_000_000_000) + 2)
        return if (id == PENDING_ONCHAIN_DEPOSIT_MOVEMENT_ID) -2 else id
    }

    /**
     * Stable synthetic id for an unboarded deposit outpoint (history row per UTXO).
     * Negative, avoids -1 and recovered-id collisions.
     */
    fun pendingOnchainOutpointMovementId(
        txid: String,
        vout: Int,
    ): Int {
        val key = "${txid.trim().lowercase(Locale.US)}:${vout.coerceAtLeast(0)}"
        if (key.length < 66) return PENDING_ONCHAIN_DEPOSIT_MOVEMENT_ID
        val hash = key.hashCode()
        var id = -(kotlin.math.abs(hash % 1_000_000_000) + 3)
        if (id == PENDING_ONCHAIN_DEPOSIT_MOVEMENT_ID) id = -3
        if (id == recoveredOnchainMovementId(txid)) id = id - 1
        return id
    }

    fun isSyntheticPendingOnchainDeposit(movement: ArkMovement): Boolean {
        if (movement.id == PENDING_ONCHAIN_DEPOSIT_MOVEMENT_ID) return true
        if (isRecoveredOnchainMovement(movement)) return false
        if (!isBoardDepositMovement(movement)) return false
        // Synthetic pending deposits use negative ids and have no completedAt.
        return movement.id < 0 && movement.completedAt.isNullOrBlank()
    }

    fun isRecoveredOnchainMovement(movement: ArkMovement): Boolean =
        movement.status.equals(STATUS_RECOVERED_L1, ignoreCase = true)

    fun isBelowMinOnchainMovement(movement: ArkMovement): Boolean =
        movement.status.equals(STATUS_BELOW_MIN, ignoreCase = true)

    /**
     * Whether [amountSats] is below the ASP board minimum.
     * Null/non-positive [minBoardAmountSats] means the limit is unknown — not below.
     */
    fun isBelowMinBoardAmount(
        amountSats: Long,
        minBoardAmountSats: Long?,
    ): Boolean {
        val min = minBoardAmountSats?.takeIf { it > 0L } ?: return false
        return amountSats > 0L && amountSats < min
    }

    /**
     * Confirmed Bark on-chain funds that cannot board (below ASP min) and are not already
     * in a pending board. Caller can top up or recover on-chain to Layer 1.
     */
    fun isStuckBelowMinBoard(
        onchainConfirmedSats: Long,
        pendingBoardSats: Long,
        minBoardAmountSats: Long?,
    ): Boolean {
        if (pendingBoardSats > 0L) return false
        return isBelowMinBoardAmount(onchainConfirmedSats, minBoardAmountSats)
    }

    /** Sats still needed so confirmed on-chain total meets [minBoardAmountSats]. */
    fun shortfallToMinBoard(
        onchainConfirmedSats: Long,
        minBoardAmountSats: Long?,
    ): Long? {
        val min = minBoardAmountSats?.takeIf { it > 0L } ?: return null
        val confirmed = onchainConfirmedSats.coerceAtLeast(0L)
        if (confirmed <= 0L || confirmed >= min) return null
        return min - confirmed
    }
    /**
     * Best-known deposit depth for UI (never hide funding behind board=0).
     * Kotlin `boardConfirmations ?: funding` is wrong when board is 0.
     * Prefer the larger positive count; fall back to 0 only when a side is explicitly zero.
     */
    fun depositDepthConfirmations(
        boardConfirmations: Int?,
        fundingConfirmations: Int?,
    ): Int? {
        val boardPositive = boardConfirmations?.takeIf { it > 0 }
        val fundingPositive = fundingConfirmations?.takeIf { it > 0 }
        if (boardPositive != null || fundingPositive != null) {
            return maxOf(boardPositive ?: 0, fundingPositive ?: 0)
        }
        if (boardConfirmations != null || fundingConfirmations != null) return 0
        return null
    }

    /**
     * Confirmations used for Pending X/required progress.
     * Once a board tx exists, ASP cares about board confs; before that, funding depth.
     */
    fun progressConfirmations(
        boardTxid: String?,
        boardConfirmations: Int?,
        fundingConfirmations: Int?,
    ): Int? =
        if (!boardTxid.isNullOrBlank()) {
            boardConfirmations
        } else {
            fundingConfirmations
                ?: depositDepthConfirmations(boardConfirmations, fundingConfirmations)
        }

    /** ASP board-tx threshold only — never treat funding depth as board confs met. */
    fun boardConfirmationsMet(
        boardConfirmations: Int?,
        requiredBoardConfirmations: Int,
    ): Boolean {
        val required = requiredBoardConfirmations.takeIf { it > 0 } ?: return false
        return (boardConfirmations ?: -1) >= required
    }

    fun boardProgressLabel(
        depthConfirmations: Int?,
        requiredBoardConfirmations: Int,
    ): String? {
        val required = requiredBoardConfirmations.takeIf { it > 0 } ?: return null
        val depth = depthConfirmations ?: return null
        return "${depth.coerceAtMost(required)}/$required"
    }

    /**
     * When Esplora sees a deposit but Bark on-chain still reports 0 (fresh session sync lag),
     * paint inbound sats so balance matches history. Do not double-count pending board or
     * already-spendable VTXOs (board completed — previous/Esplora funding must not stay painted).
     */
    fun resolveOnchainBuckets(
        liveConfirmedSats: Long,
        livePendingSats: Long,
        previousConfirmedSats: Long,
        previousPendingSats: Long,
        pendingBoardSats: Long,
        esploraAmountSats: Long,
        esploraFundingConfirmations: Int?,
        onchainWalletPresent: Boolean,
        preservePreviousWhenLiveZero: Boolean,
        spendableSats: Long = 0L,
    ): OnchainBuckets {
        if (!onchainWalletPresent) {
            return OnchainBuckets(confirmedSats = 0L, pendingSats = 0L)
        }
        val liveTotal = (liveConfirmedSats + livePendingSats).coerceAtLeast(0L)
        val previousTotal = (previousConfirmedSats + previousPendingSats).coerceAtLeast(0L)
        val board = pendingBoardSats.coerceAtLeast(0L)
        val spendable = spendableSats.coerceAtLeast(0L)

        if (liveTotal > 0L) {
            return OnchainBuckets(
                confirmedSats = liveConfirmedSats.coerceAtLeast(0L),
                pendingSats = livePendingSats.coerceAtLeast(0L),
            )
        }

        // Funds already in pending board — on-chain UTXO spent into board tx.
        if (board > 0L) {
            return OnchainBuckets(confirmedSats = 0L, pendingSats = 0L)
        }

        // Unspent Esplora UTXOs on deposit addresses are authoritative when Bark lag reports 0.
        // Prefer this before clearing for spendable — self-fund leaves VTXOs + new unboarded UTXO.
        val esplora = esploraAmountSats.coerceAtLeast(0L)
        if (esplora > 0L) {
            val confirmed =
                when {
                    esploraFundingConfirmations == null -> 0L
                    esploraFundingConfirmations > 0 -> esplora
                    else -> 0L
                }
            val pending = (esplora - confirmed).coerceAtLeast(0L)
            return OnchainBuckets(confirmedSats = confirmed, pendingSats = pending)
        }

        // Board finished: VTXOs spendable, Bark on-chain empty, no unspent Esplora UTXOs.
        // Drop stale previous paint so balance does not double-count.
        if (spendable > 0L) {
            return OnchainBuckets(confirmedSats = 0L, pendingSats = 0L)
        }

        if (preservePreviousWhenLiveZero && previousTotal > 0L) {
            return OnchainBuckets(
                confirmedSats = previousConfirmedSats.coerceAtLeast(0L),
                pendingSats = previousPendingSats.coerceAtLeast(0L),
            )
        }

        return OnchainBuckets(confirmedSats = 0L, pendingSats = 0L)
    }

    /**
     * Esplora tip height from confirmed status block height when tip endpoint is unavailable.
     * Returns null when status is missing/unconfirmed or heights are unusable.
     */
    fun confirmationCount(
        confirmed: Boolean,
        blockHeight: Int?,
        tipHeight: Int?,
    ): Int {
        if (!confirmed) return 0
        val height = blockHeight?.takeIf { it > 0 } ?: return 1
        val tip = tipHeight?.takeIf { it > 0 } ?: return 1
        return (tip - height + 1).coerceAtLeast(1)
    }

    data class OnchainBuckets(
        val confirmedSats: Long,
        val pendingSats: Long,
    ) {
        val totalSats: Long get() = confirmedSats + pendingSats
    }

    /**
     * Union live Bark/history rows with previously painted movements.
     * Live wins on id collision. Drops stale synthetic pending-deposit (-1) when live
     * already has a real board row or no longer needs the placeholder.
     * Always returns newest-first chronological order (pending is not pinned).
     */
    fun mergePreservedMovements(
        live: List<ArkMovement>,
        previous: List<ArkMovement>,
    ): List<ArkMovement> {
        if (previous.isEmpty()) return sortMovementsChronologically(live)
        // Mailbox recovery restores VTXOs, not history — keep prior rows until Bark has any.
        if (live.isEmpty()) return sortMovementsChronologically(previous)

        val liveIds = live.mapTo(HashSet(live.size)) { it.id }
        val liveRecoveredFunding =
            live
                .filter { isRecoveredOnchainMovement(it) }
                .flatMap { it.onchainTxids }
                .map { it.lowercase(Locale.US) }
                .toHashSet()
        val preserved =
            previous.filter { prior ->
                if (prior.id in liveIds) return@filter false
                // Synthetic pending deposits are always rebuilt on refresh — never preserve stale.
                if (isSyntheticPendingOnchainDeposit(prior)) return@filter false
                // Drop pending-era board placeholders once funding was recovered to L1.
                if (
                    isBoardDepositMovement(prior) &&
                    prior.onchainTxids.any {
                        it.lowercase(Locale.US) in liveRecoveredFunding
                    }
                ) {
                    return@filter false
                }
                // Always keep recovered-to-L1 rows until live re-emits them.
                if (isRecoveredOnchainMovement(prior)) return@filter true
                true
            }
        return sortMovementsChronologically(if (preserved.isEmpty()) live else live + preserved)
    }

    /**
     * Newest first by movement time. Pending status must not float rows above newer txs.
     * Prefer createdAt, then completedAt, then updatedAt; tie-break on id.
     */
    fun sortMovementsChronologically(movements: List<ArkMovement>): List<ArkMovement> {
        if (movements.size <= 1) return movements
        return movements.sortedWith(
            compareByDescending<ArkMovement> { movementSortMillis(it) }
                .thenByDescending { it.id },
        )
    }

    fun movementSortMillis(movement: ArkMovement): Long {
        parseMovementTimestampMillis(movement.createdAt)?.let { return it }
        movement.completedAt?.let { parseMovementTimestampMillis(it) }?.let { return it }
        parseMovementTimestampMillis(movement.updatedAt)?.let { return it }
        return Long.MIN_VALUE
    }

    private fun parseMovementTimestampMillis(raw: String): Long? {
        val value = raw.trim()
        if (value.isEmpty()) return null
        value.toLongOrNull()?.takeIf { it > 0L }?.let { epoch ->
            return if (epoch > 10_000_000_000L) epoch else epoch * 1000L
        }
        runCatching { return Instant.parse(value).toEpochMilli() }
        runCatching { return OffsetDateTime.parse(value).toInstant().toEpochMilli() }
        val normalized = value.replace(' ', 'T')
        runCatching { return Instant.parse(normalized).toEpochMilli() }
        // Loose RFC3339 with offset missing colon (+0000).
        if (normalized.matches(Regex(".*[+-]\\d{4}$"))) {
            val withColon = normalized.dropLast(2) + ":" + normalized.takeLast(2)
            runCatching { return OffsetDateTime.parse(withColon).toInstant().toEpochMilli() }
        }
        return null
    }

    fun isNoiseArkMovement(movement: ArkMovement): Boolean {
        if (movement.effectiveBalanceSats != 0L) return false
        if (movement.offchainFeeSats != 0L) return false
        if ((movement.onchainFeeSats ?: 0L) != 0L) return false

        val status = movement.status.trim().lowercase(Locale.US)
        val failedOrVoid =
            status in
                setOf(
                    "failed",
                    "error",
                    "cancelled",
                    "canceled",
                    "expired",
                    "unpaid",
                    "void",
                    "aborted",
                ) ||
                status.contains("fail") ||
                status.contains("error") ||
                status.contains("cancel") ||
                status.contains("expired") ||
                status.contains("unpaid") ||
                status.contains("abort")
        if (failedOrVoid && movement.intendedBalanceSats >= 0L) return true

        val kindBlob =
            listOf(movement.subsystemName, movement.subsystemKind)
                .joinToString(" ")
                .lowercase(Locale.US)
        val internalBookkeeping =
            kindBlob.contains("refresh") ||
                kindBlob.contains("round") ||
                kindBlob.contains("maintain") ||
                kindBlob.contains("forfeit") ||
                kindBlob.contains("rebalance") ||
                kindBlob.contains("participate") ||
                kindBlob.contains("cosign")
        if (internalBookkeeping) return true

        val hasPaymentEvidence =
            !movement.paymentHash.isNullOrBlank() ||
                !movement.lightningInvoice.isNullOrBlank() ||
                !movement.lightningOffer.isNullOrBlank() ||
                movement.onchainTxids.isNotEmpty() ||
                movement.sentToAddresses.isNotEmpty() ||
                movement.receivedOnAddresses.isNotEmpty() ||
                movement.intendedBalanceSats != 0L
        return !hasPaymentEvidence
    }

    fun isBoardDepositMovement(movement: ArkMovement): Boolean {
        val kind =
            "${movement.subsystemName} ${movement.subsystemKind}".lowercase(Locale.US)
        return kind.contains("board") && !kind.contains("offboard")
    }

    /** Funding / board chain ids on a movement (lowercase 64-char txids). */
    fun movementChainTxids(movement: ArkMovement): Set<String> =
        (
            movement.onchainTxids + listOfNotNull(movement.boardTxid)
        ).map { it.trim().lowercase(Locale.US) }
            .filter { it.length == 64 }
            .toSet()
}
