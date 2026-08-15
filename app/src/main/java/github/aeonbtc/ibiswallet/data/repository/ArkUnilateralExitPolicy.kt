package github.aeonbtc.ibiswallet.data.repository

import github.aeonbtc.ibiswallet.data.repository.ArkUnilateralExitPolicy.planClaimPrepare


/**
 * Pure decision helpers for Ark unilateral exit + claim.
 * Keeps Bark-side I/O in [ArkRepository] while the start/progress/claim rules stay testable.
 */
object ArkUnilateralExitPolicy {

    const val DEFAULT_EXIT_FEE_RATE_SAT_VB: Long = 2L
    const val PROGRESS_EXIT_FEE_RATE_SAT_VB: Long = 5L
    const val CPFP_CHANGE_DUST_SATS: Long = 330L
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
        NO_SPENDABLE_VTXOS,
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
    ): StartExitPlan {
        if (!walletLoaded) {
            return StartExitPlan.Error(StartExitError.WALLET_NOT_LOADED)
        }
        val spendable = spendableVtxoIds.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        val spendableSet = spendable.toSet()
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

    fun shouldAutoBoardOnchainFunds(hasPendingExits: Boolean): Boolean = !hasPendingExits

    /** Mirrors Bark's exit affordability estimate: 2x total exit transaction weight. */
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
}
