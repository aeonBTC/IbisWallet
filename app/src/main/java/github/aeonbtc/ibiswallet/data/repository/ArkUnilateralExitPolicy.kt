package github.aeonbtc.ibiswallet.data.repository

import github.aeonbtc.ibiswallet.data.model.ArkEmergencyExitFeeQuote
import github.aeonbtc.ibiswallet.data.repository.ArkUnilateralExitPolicy.planClaimPrepare


/**
 * Pure decision helpers for Ark unilateral exit + claim.
 * Keeps Bark-side I/O in [ArkRepository] while the start/progress/claim rules stay testable.
 */
object ArkUnilateralExitPolicy {

    const val DEFAULT_EXIT_FEE_RATE_SAT_VB: Long = 2L
    const val PROGRESS_EXIT_FEE_RATE_SAT_VB: Long = 5L
    const val CPFP_CHANGE_DUST_SATS: Long = 330L
    /** Confirmations for the final exit claim transaction to count the exit done. */
    const val EXIT_CLAIM_CONFIRMATIONS: Int = 1
    /** Hard ceiling so a typo (e.g. 500 vs 5) cannot burn the claim into fees. */
    const val MAX_EXIT_FEE_RATE_SAT_VB: Long = 200L

    sealed class StartExitPlan {
        data class EntireWallet(
            val markEntireWallet: Boolean = true,
        ) : StartExitPlan()

        data class Selected(
            val vtxoIds: List<String>,
            val markEntireWallet: Boolean,
        ) : StartExitPlan()

        data class Error(
            val reason: StartExitError,
        ) : StartExitPlan()
    }

    enum class StartExitError {
        WALLET_NOT_LOADED,
        ONCHAIN_WALLET_UNAVAILABLE,
        NO_SPENDABLE_VTXOS,
        PENDING_REFRESH,
    }

    sealed class ClaimPreparePlan {
        data class Ready(
            val destinationAddress: String,
            val vtxoIds: List<String>,
            val feeRateSatPerVb: Long,
        ) : ClaimPreparePlan()

        data class Error(
            val reason: ClaimPrepareError,
        ) : ClaimPreparePlan()
    }

    enum class ClaimPrepareError {
        WALLET_NOT_LOADED,
        INVALID_DESTINATION,
        NO_CLAIMABLE_EXITS,
    }

    enum class ClaimExecuteError {
        WALLET_NOT_LOADED,
        NOTHING_PREPARED,
    }

    /**
     * Decide which Bark start-exit API and VTXO set the repository should use.
     *
     * - [entireWallet]=true → start entire wallet when any spendable VTXO exists
     * - non-empty [requestedVtxoIds] → start intersection with spendable
     * - otherwise → start all currently spendable ids
     */
    fun planStartExit(
        walletLoaded: Boolean,
        entireWallet: Boolean,
        requestedVtxoIds: List<String>,
        spendableVtxoIds: List<String>,
        onchainWalletPresent: Boolean = true,
        excludedVtxoIds: Collection<String> = emptyList(),
    ): StartExitPlan {
        if (!walletLoaded) {
            return StartExitPlan.Error(StartExitError.WALLET_NOT_LOADED)
        }
        if (!onchainWalletPresent) {
            return StartExitPlan.Error(StartExitError.ONCHAIN_WALLET_UNAVAILABLE)
        }
        // VTXOs already submitted to a refresh round are locked by that round —
        // unilateral exit must not touch them (same rule as re-refresh). Strict:
        // any pending id in the start set refuses the whole start, never a
        // silent subset.
        val excluded = excludedVtxoIds.map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        val spendable = spendableVtxoIds.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (excluded.isNotEmpty()) {
            val requested =
                requestedVtxoIds.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
            if (requested.any { it in excluded }) {
                return StartExitPlan.Error(StartExitError.PENDING_REFRESH)
            }
            if ((entireWallet || requested.isEmpty()) && spendable.any { it in excluded }) {
                return StartExitPlan.Error(StartExitError.PENDING_REFRESH)
            }
        }
        val spendableSet = spendable.toSet() - excluded
        if (entireWallet) {
            if (spendable.isEmpty()) {
                return StartExitPlan.Error(StartExitError.NO_SPENDABLE_VTXOS)
            }
            return StartExitPlan.EntireWallet()
        }
        if (requestedVtxoIds.isNotEmpty()) {
            val selected =
                requestedVtxoIds
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .distinct()
                    .filter { it in spendableSet }
            if (selected.isEmpty()) {
                return StartExitPlan.Error(StartExitError.NO_SPENDABLE_VTXOS)
            }
            return StartExitPlan.Selected(
                vtxoIds = selected,
                markEntireWallet = false,
            )
        }
        if (spendable.isEmpty()) {
            return StartExitPlan.Error(StartExitError.NO_SPENDABLE_VTXOS)
        }
        // Empty selection with entireWallet=false: fall through to all spendable and
        // still report entireWallet=true (repository startUnilateralExit).
        return StartExitPlan.Selected(
            vtxoIds = spendable,
            markEntireWallet = true,
        )
    }

    /** VTXOs to lock before [startExitFor*] so refresh cannot spend them. */
    fun idsToLockBeforeStart(
        plan: StartExitPlan,
        spendableVtxoIds: List<String>,
    ): List<String> =
        when (plan) {
            is StartExitPlan.EntireWallet ->
                spendableVtxoIds.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
            is StartExitPlan.Selected -> plan.vtxoIds
            is StartExitPlan.Error -> emptyList()
        }

    fun mergeExitWeights(
        liveWeightsById: Map<String, Long>,
        previousWeightsById: Map<String, Long>,
        exitIds: Collection<String>,
    ): Map<String, Long> =
        exitIds.associate { id ->
            val live = liveWeightsById[id] ?: 0L
            val previous = previousWeightsById[id] ?: 0L
            id to if (live > 0L) live else previous
        }

    /** IDs to feed ExitStarted payload after Bark start. */
    fun resolveStartedVtxoIds(
        plan: StartExitPlan,
        vtxoIdsAfterEntireStart: List<String>,
    ): List<String> =
        when (plan) {
            is StartExitPlan.EntireWallet ->
                vtxoIdsAfterEntireStart.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
            is StartExitPlan.Selected -> plan.vtxoIds
            is StartExitPlan.Error -> emptyList()
        }

    fun markEntireWalletInResult(plan: StartExitPlan): Boolean =
        when (plan) {
            is StartExitPlan.EntireWallet -> plan.markEntireWallet
            is StartExitPlan.Selected -> plan.markEntireWallet
            is StartExitPlan.Error -> false
        }

    fun clampExitFeeRateSatPerVb(feeRateSatPerVb: Long): Long =
        feeRateSatPerVb.coerceIn(1L, MAX_EXIT_FEE_RATE_SAT_VB)

    /**
     * Mainnet-only claim destination gate with checksum validation.
     * Only 1… / 3… / bc1q… / bc1p… with valid checksums. Testnet and silent payments rejected.
     */
    fun isUsableBitcoinClaimAddress(value: String): Boolean {
        val address = value.trim()
        if (address.isEmpty()) return false
        return validateMainnetClaimAddressChecksum(address)
    }

    /**
     * Checksum-aware mainnet address validation (bech32/bech32m + base58check).
     * Mirrors SendScreen rules without Android UI dependencies.
     */
    private fun validateMainnetClaimAddressChecksum(address: String): Boolean {
        val trimmed = address.trim()
        val lower = trimmed.lowercase()
        return when {
            lower.startsWith("sp1") -> false // silent payments not usable for exit claim
            trimmed.startsWith("1") || trimmed.startsWith("3") ->
                validateBase58Check(trimmed)
            lower.startsWith("bc1q") -> validateBech32(trimmed, bech32m = false)
            lower.startsWith("bc1p") -> validateBech32(trimmed, bech32m = true)
            else -> false
        }
    }

    private fun validateBase58Check(address: String): Boolean {
        if (address.length !in 25..35) return false
        val alphabet = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
        if (address.any { it !in alphabet }) return false
        return runCatching {
            val decoded = decodeBase58(address)
            if (decoded.size < 25) return false
            val payload = decoded.copyOfRange(0, decoded.size - 4)
            val checksum = decoded.copyOfRange(decoded.size - 4, decoded.size)
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            val hash1 = digest.digest(payload)
            val hash2 = digest.digest(hash1)
            checksum.contentEquals(hash2.copyOfRange(0, 4))
        }.getOrDefault(false)
    }

    private fun decodeBase58(input: String): ByteArray {
        val alphabet = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
        var num = java.math.BigInteger.ZERO
        val base = java.math.BigInteger.valueOf(58)
        for (ch in input) {
            val idx = alphabet.indexOf(ch)
            require(idx >= 0)
            num = num.multiply(base).add(java.math.BigInteger.valueOf(idx.toLong()))
        }
        val bytes = num.toByteArray()
        // Preserve leading zeros (1 → 0x00)
        var leading = 0
        for (ch in input) {
            if (ch == '1') leading++ else break
        }
        val stripped =
            if (bytes.isNotEmpty() && bytes[0] == 0.toByte()) {
                bytes.copyOfRange(1, bytes.size)
            } else {
                bytes
            }
        return ByteArray(leading) + stripped
    }

    private fun validateBech32(
        address: String,
        bech32m: Boolean,
    ): Boolean {
        val charset = "qpzry9x8gf2tvdw0s3jn54khce6mua7l"
        val lower = address.lowercase()
        if (address != lower && address != address.uppercase()) return false
        val sep = lower.lastIndexOf('1')
        if (sep < 1 || sep + 7 > lower.length || lower.length > 90) return false
        val hrp = lower.take(sep)
        if (hrp != "bc") return false
        val dataPart = lower.substring(sep + 1)
        val values = IntArray(dataPart.length)
        for (i in dataPart.indices) {
            val idx = charset.indexOf(dataPart[i])
            if (idx < 0) return false
            values[i] = idx
        }
        fun polymod(vals: IntArray): Int {
            val gen = intArrayOf(0x3b6a57b2, 0x26508e6d, 0x1ea119fa, 0x3d4233dd, 0x2a1462b3)
            var chk = 1
            for (v in vals) {
                val top = chk ushr 25
                chk = ((chk and 0x1ffffff) shl 5) xor v
                for (i in 0 until 5) {
                    if (((top ushr i) and 1) == 1) chk = chk xor gen[i]
                }
            }
            return chk
        }
        fun hrpExpand(h: String): IntArray {
            val ret = IntArray(h.length * 2 + 1)
            for (i in h.indices) ret[i] = h[i].code ushr 5
            ret[h.length] = 0
            for (i in h.indices) ret[h.length + 1 + i] = h[i].code and 31
            return ret
        }
        val expanded = hrpExpand(hrp) + values
        val expected = if (bech32m) 0x2bc830a3 else 1
        return polymod(expanded) == expected
    }

    fun planClaimPrepare(
        walletLoaded: Boolean,
        destinationAddress: String,
        requestedVtxoIds: List<String>,
        claimableVtxoIds: List<String>,
        feeRateSatPerVb: Long = DEFAULT_EXIT_FEE_RATE_SAT_VB,
    ): ClaimPreparePlan {
        if (!walletLoaded) {
            return ClaimPreparePlan.Error(ClaimPrepareError.WALLET_NOT_LOADED)
        }
        val dest = destinationAddress.trim()
        if (!isUsableBitcoinClaimAddress(dest)) {
            return ClaimPreparePlan.Error(ClaimPrepareError.INVALID_DESTINATION)
        }
        val claimableWallet =
            claimableVtxoIds.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        val claimableSet = claimableWallet.toSet()
        val claimable =
            if (requestedVtxoIds.isNotEmpty()) {
                requestedVtxoIds
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .distinct()
                    .filter { it in claimableSet }
            } else {
                claimableWallet
            }
        if (claimable.isEmpty()) {
            return ClaimPreparePlan.Error(ClaimPrepareError.NO_CLAIMABLE_EXITS)
        }
        return ClaimPreparePlan.Ready(
            destinationAddress = dest,
            vtxoIds = claimable,
            feeRateSatPerVb = clampExitFeeRateSatPerVb(feeRateSatPerVb),
        )
    }

    fun planClaimExecute(
        walletLoaded: Boolean,
        hasClaimPreview: Boolean,
    ): ClaimExecuteError? {
        if (!walletLoaded) return ClaimExecuteError.WALLET_NOT_LOADED
        if (!hasClaimPreview) return ClaimExecuteError.NOTHING_PREPARED
        return null
    }

    /**
     * Ledger amount for a claim history/journal row. Prefers the live query at
     * execute time; falls back to the preview-time total so a failed query
     * never persists a 0-sats claim for a real broadcast. Fail-open to 0 only
     * when both are missing — the txid (recorded separately) stays trackable.
     */
    fun resolveClaimLedgerAmount(
        liveAmountSats: Long?,
        previewAmountSats: Long?,
    ): Long =
        liveAmountSats?.takeIf { it > 0L }
            ?: previewAmountSats?.takeIf { it > 0L }
            ?: 0L

    /**
     * UI: Exit selected enabled when at least one selected id is currently spendable.
     * Prevents claimable/stale ids left in selection from enabling start.
     */
    fun canStartSelectedExit(
        selectedVtxoIds: Collection<String>,
        spendableVtxoIds: Collection<String> = selectedVtxoIds,
    ): Boolean {
        if (selectedVtxoIds.isEmpty()) return false
        val spendable = spendableVtxoIds.map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        if (spendable.isEmpty()) return false
        return selectedVtxoIds.any { it.trim() in spendable }
    }

    fun canStartEntireExit(spendableVtxoIds: Collection<String>): Boolean =
        spendableVtxoIds.any { it.trim().isNotEmpty() }

    /** UI: claim quote needs a non-blank destination (format checked in [planClaimPrepare]). */
    fun canQuoteClaim(destinationAddress: String): Boolean = destinationAddress.trim().isNotEmpty()

    /** Whether pending exits still need Push while some others are already claimable. */
    fun shouldShowProgressWithClaimable(
        hasPendingExits: Boolean,
        hasClaimableExits: Boolean,
    ): Boolean = hasPendingExits && hasClaimableExits

    /**
     * First non-blank per-VTXO error from a [progressExits] result list.
     * Bark often returns success with errors embedded per status instead of throwing.
     */
    fun firstProgressErrorFromMessages(errors: List<String?>): String? =
        errors
            .asSequence()
            .mapNotNull { it?.trim()?.takeIf { msg -> msg.isNotEmpty() } }
            .firstOrNull()

    /** Bark silently leaves exits here when its on-chain wallet cannot fund the CPFP child. */
    fun needsCpfpFunding(states: List<String>): Boolean =
        states.any {
            it.contains(ArkBarkMappers.EXIT_AWAITING_CPFP, ignoreCase = true) ||
                it.contains("awaiting-cpfp-broadcast", ignoreCase = true)
        }

    fun isActiveExitState(state: String): Boolean =
        state.trim().isNotEmpty() && !ArkBarkMappers.isTerminalExitLabel(state)

    fun computeHasPendingExits(
        barkHasPending: Boolean,
        exitStates: Collection<String>,
    ): Boolean = barkHasPending || exitStates.any { isActiveExitState(it) }

    fun cpfpWeightsForExits(
        exitVtxoWeightsWu: Collection<Long>,
        spendableWeightsWu: Collection<Long> = emptyList(),
    ): List<Long> {
        val fromExits = exitVtxoWeightsWu.filter { it > 0L }
        if (fromExits.isNotEmpty()) return fromExits
        return spendableWeightsWu.filter { it > 0L }
    }

    fun shouldAutoBoardOnchainFunds(hasPendingExits: Boolean): Boolean = !hasPendingExits

    fun canCancelPendingExits(states: Collection<String>): Boolean =
        states.any { ArkBarkMappers.canCancelLabel(it) }

    fun vtxosEligibleForCancel(idsToStates: Map<String, String>): List<String> =
        idsToStates
            .filter { (id, state) -> id.isNotBlank() && ArkBarkMappers.canCancelLabel(state) }
            .keys
            .toList()

    /**
     * Conservative pre-start CPFP budget: 2x total exit transaction weight at the
     * given rate, rounded up. The doubling covers the CPFP child package on top of
     * the exit chain itself. It is a single-shot total — unlike Bark's native
     * `fundable` walk it cannot detect the serial-funding stall (each bump's change
     * must confirm before funding the next), so every push re-estimates the
     * remaining walk and the shortfall gate re-checks there.
     */
    fun estimateCpfpFeeSats(
        exitTxWeightsWu: Collection<Long>,
        feeRateSatPerVb: Long = PROGRESS_EXIT_FEE_RATE_SAT_VB,
    ): Long? {
        val totalWeightWu = exitTxWeightsWu.filter { it > 0L }.sum()
        if (totalWeightWu <= 0L) return null
        val rate = clampExitFeeRateSatPerVb(feeRateSatPerVb)
        return kotlin.math.ceil(totalWeightWu.toDouble() * rate / 2.0).toLong()
    }

    fun estimateCpfpRequiredSats(
        exitTxWeightsWu: Collection<Long>,
        feeRateSatPerVb: Long = PROGRESS_EXIT_FEE_RATE_SAT_VB,
    ): Long? = estimateCpfpFeeSats(exitTxWeightsWu, feeRateSatPerVb)?.plus(CPFP_CHANGE_DUST_SATS)

    /**
     * Clamp a Bark ULong fee/count into Long for UI state. Real fee values fit
     * comfortably; the clamp only guards the FFI boundary, never real quotes.
     */
    fun clampBarkAmount(value: ULong): Long =
        if (value > Long.MAX_VALUE.toULong()) Long.MAX_VALUE else value.toLong()

    /**
     * Map a Bark `EmergencyExitFeeEstimate` onto [ArkEmergencyExitFeeQuote].
     * Pure so the FFI boundary stays testable without a native handle.
     */
    fun mapEmergencyExitFeeQuote(
        vtxoIds: List<String>,
        broadcastFeeSats: ULong,
        claimFeeSats: ULong,
        totalFeeSats: ULong,
        feeRateSatPerVb: ULong,
        txsToBroadcast: ULong,
        fundable: Boolean,
    ): ArkEmergencyExitFeeQuote =
        ArkEmergencyExitFeeQuote(
            vtxoIds = vtxoIds.map { it.trim() }.filter { it.isNotEmpty() }.distinct(),
            broadcastFeeSats = clampBarkAmount(broadcastFeeSats),
            claimFeeSats = clampBarkAmount(claimFeeSats),
            totalFeeSats = clampBarkAmount(totalFeeSats),
            feeRateSatPerVb = clampBarkAmount(feeRateSatPerVb),
            txsToBroadcast = clampBarkAmount(txsToBroadcast),
            fundable = fundable,
            isQuoting = false,
        )

    /** Whether the authoritative quote blocks exit start (known-unfundable). */
    fun isEmergencyExitBlockedByQuote(quote: ArkEmergencyExitFeeQuote?): Boolean =
        quote != null && !quote.isQuoting && quote.fundable == false

    /**
     * Shortfall of the authoritative broadcast leg against confirmed on-chain
     * funds. Null when the quote is missing/incomplete — fail-open like the
     * manual estimate gate.
     */
    fun emergencyExitBroadcastShortfallSats(
        quote: ArkEmergencyExitFeeQuote?,
        confirmedOnchainSats: Long,
    ): Long? {
        if (quote == null || quote.isQuoting) return null
        val broadcast = quote.broadcastFeeSats ?: return null
        return (broadcast - confirmedOnchainSats.coerceAtLeast(0L)).coerceAtLeast(0L)
    }

    /**
     * Lower-bound unilateral-exit completion estimate in blocks: one confirmation per
     * exit-tree level ([maxExitDepth], levels confirm sequentially), plus the ASP exit
     * timelock ([exitDeltaBlocks], Bark `vtxoExitDelta`), plus the final claim
     * confirmation. Assumes each transaction confirms promptly — congestion or low
     * fees add on top. Null when the ASP delay is unknown.
     */
    fun estimateExitCompletionBlocks(
        maxExitDepth: Int,
        exitDeltaBlocks: Int?,
    ): Int? {
        val delta = exitDeltaBlocks?.takeIf { it > 0 } ?: return null
        val depth = maxExitDepth.coerceAtLeast(0)
        return depth + delta + EXIT_CLAIM_CONFIRMATIONS
    }

    /**
     * Bitcoin txid for signed transaction bytes: double-SHA256, byte-reversed, hex.
     * Lets the claim journal record the txid even if `broadcastTx` throws after the
     * network accepted the transaction. Null on malformed input.
     */
    fun txidFromSignedHex(signedHex: String): String? {
        val clean = signedHex.trim().lowercase()
        if (clean.isEmpty() || clean.length % 2 != 0) return null
        if (clean.any { it !in '0'..'9' && it !in 'a'..'f' }) return null
        return runCatching {
            val raw =
                ByteArray(clean.length / 2) { index ->
                    clean.substring(index * 2, index * 2 + 2).toInt(16).toByte()
                }
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            val once = digest.digest(raw)
            digest.reset()
            val twice = digest.digest(once)
            twice.reversedArray().joinToString("") { "%02x".format(it) }
        }.getOrNull()
    }
}
