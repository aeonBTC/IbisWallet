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

    /** History status: below-min deposit swept back to Layer 1. */
    const val STATUS_RECOVERED_L1: String = "recovered_l1"

    /** Max rows kept in the per-wallet SecureStorage movement journal. */
    const val MOVEMENT_JOURNAL_MAX_ROWS: Int = 200

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

    /** ASP board-tx threshold only — never treat funding depth as board confs met.
     * A required value of 0 is valid (ASP registers once the board tx is seen,
     * even in mempool); only negative is invalid. Mirrors Bark run_confirm,
     * which registers once `confs >= ark_info.required_board_confirmations`. */
    fun boardConfirmationsMet(
        boardConfirmations: Int?,
        requiredBoardConfirmations: Int,
    ): Boolean {
        if (requiredBoardConfirmations < 0) return false
        return (boardConfirmations ?: -1) >= requiredBoardConfirmations
    }

    fun boardProgressLabel(
        depthConfirmations: Int?,
        requiredBoardConfirmations: Int,
    ): String? {
        if (requiredBoardConfirmations < 0) return null
        val depth = depthConfirmations ?: return null
        return "${depth.coerceAtMost(requiredBoardConfirmations)}/$requiredBoardConfirmations"
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
     * Identity for history union + UI keys. Bark movement ids are per-DB row ids:
     * any session-DB recreation (forced rescan wipe, cache eviction, import)
     * restarts the id space, so a bare id is not a stable cross-session identity
     * and a new payment can evict an old row with a colliding id. The fingerprint
     * covers creation-time facts only: settlement mutates a row in place
     * (status pending→finished, effective 0→final, output VTXOs/txids filled in,
     * fees revealed), so anything assigned at completion is excluded — otherwise
     * a send paints twice, once Pending and once finished. Mutable enrichment
     * (labels, address annotations, Esplora-derived board/fee fields) is excluded
     * too, so updates still replace instead of duplicating.
     */
    fun movementUnionKey(movement: ArkMovement): String =
        listOf(
            movement.id.toString(),
            movement.createdAt.trim(),
            movement.intendedBalanceSats.toString(),
            movement.paymentHash.orEmpty().trim().lowercase(Locale.US),
            movement.lightningInvoice.orEmpty().trim(),
            movement.lightningOffer.orEmpty().trim(),
            movement.subsystemName.trim().lowercase(Locale.US),
            movement.subsystemKind.trim().lowercase(Locale.US),
        ).joinToString("|")

    /**
     * Rank for collapsing duplicate rows: Bark can return the same movement
     * twice in one history() call (stale pending ghost + settled row, same id),
     * and older paints may have journaled/cached both copies. Settled beats
     * pending; ties keep Bark's newest-first order.
     */
    fun movementSettledRank(status: String): Int =
        if (status.trim().equals("pending", ignoreCase = true)) 0 else 1

    /**
     * Paint-boundary sanitizer: collapse same-fingerprint rows to one (settled
     * wins) and return newest-first order. LazyColumn keys are fatal on
     * duplicates, so every path that can paint movements — merge output,
     * wallet-state cache, journal, backup sidecar — must go through this, not
     * just the merge. Stored copies self-heal on the next persist.
     */
    fun distinctPaintedMovements(movements: List<ArkMovement>): List<ArkMovement> {
        if (movements.isEmpty()) return movements
        val deduped =
            movements
                .groupBy(::movementUnionKey)
                .values
                .map { rows ->
                    rows.maxByOrNull { movementSettledRank(it.status) } ?: rows.first()
                }
        return sortMovementsChronologically(deduped)
    }

    /**
     * Union live Bark/history rows with previously painted movements.
     * Live wins on fingerprint collision (status/enrichment update of the same
     * payment). Duplicate live rows (pending ghost + settled copy) collapse to
     * the settled one so UI keys stay unique. Drops stale synthetic
     * pending-deposit (-1) when live already has a real board row or no longer
     * needs the placeholder.
     * Always returns newest-first chronological order (pending is not pinned).
     */
    fun mergePreservedMovements(
        live: List<ArkMovement>,
        previous: List<ArkMovement>,
    ): List<ArkMovement> {
        // Collapse same-fingerprint live rows first: same payment observed twice
        // (e.g. pending ghost + finished settlement) must paint once, and the
        // settled copy must win regardless of Bark's return order.
        val dedupedLive =
            live
                .groupBy(::movementUnionKey)
                .values
                .map { rows ->
                    // Settled beats pending; ties keep the first row, preserving
                    // Bark's newest-first order (maxByOrNull keeps first max).
                    rows.maxByOrNull { movementSettledRank(it.status) } ?: rows.first()
                }
        if (previous.isEmpty()) return sortMovementsChronologically(dedupedLive)
        // Mailbox recovery restores VTXOs, not history — keep prior rows until Bark has any.
        if (dedupedLive.isEmpty()) return sortMovementsChronologically(previous)

        val liveKeys = dedupedLive.mapTo(HashSet(dedupedLive.size)) { movementUnionKey(it) }
        val liveRecoveredFunding =
            live
                .filter { isRecoveredOnchainMovement(it) }
                .flatMap { it.onchainTxids }
                .map { it.lowercase(Locale.US) }
                .toHashSet()
        val preserved =
            previous.filter { prior ->
                if (movementUnionKey(prior) in liveKeys) return@filter false
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
        return distinctPaintedMovements(
            if (preserved.isEmpty()) dedupedLive else dedupedLive + preserved,
        )
    }

    /**
     * Newest first by movement time. Pending status must not float rows above newer txs.
     * Prefer createdAt, then completedAt, then updatedAt; tie-break on id.
     *
     * Causality anchor: a row funded by another row's on-chain spend (self-transfer
     * receive sighted after its send) sorts just above that send even when Bark
     * movement clocks and first-sighting clocks disagree by seconds — otherwise
     * causal pairs invert in the newest-first list. Anchors are outbound spends
     * only, matched by on-chain txid (never addresses: reuse would false-link).
     * Displayed times are untouched; only order is adjusted.
     */
    fun sortMovementsChronologically(movements: List<ArkMovement>): List<ArkMovement> {
        if (movements.size <= 1) return movements
        val outboundMillisByTxid = HashMap<String, Pair<Long, String>>()
        movements.forEach { candidate ->
            if (candidate.effectiveBalanceSats < 0L) {
                val millis = movementSortMillis(candidate)
                if (millis > Long.MIN_VALUE) {
                    val key = movementUnionKey(candidate)
                    movementChainTxids(candidate).forEach { txid ->
                        val current = outboundMillisByTxid[txid]
                        if (current == null || millis > current.first) {
                            outboundMillisByTxid[txid] = millis to key
                        }
                    }
                }
            }
        }
        fun effectiveMillis(movement: ArkMovement): Long {
            val own = movementSortMillis(movement)
            val selfKey = movementUnionKey(movement)
            var best = own
            movementChainTxids(movement).forEach { txid ->
                val anchor = outboundMillisByTxid[txid] ?: return@forEach
                // Same-fingerprint duplicates (pending ghost + settled copy) must
                // not anchor each other — dedupe handles those.
                if (anchor.second != selfKey && anchor.first > Long.MIN_VALUE) {
                    best = maxOf(best, anchor.first + 1)
                }
            }
            return best
        }
        return movements.sortedWith(
            compareByDescending<ArkMovement> { effectiveMillis(it) }
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
