package github.aeonbtc.ibiswallet.data.repository

import github.aeonbtc.ibiswallet.data.model.SparkExitLiveUtxo
import github.aeonbtc.ibiswallet.data.model.SparkExitQuote
import github.aeonbtc.ibiswallet.data.model.SparkExitTx
import github.aeonbtc.ibiswallet.data.model.SparkExitTxStatus

/**
 * Pure decision logic for Spark unilateral exits.
 *
 * The exit SDK calls need funding UTXOs whose signatures live in the witness
 * (native SegWit only). Key material never leaves the L1 wallet: funding
 * inputs are always described as [CpfpFundingKind.Custom] with an upper-bound
 * weight, and signing happens through the BDK wallet PSBT path.
 */
object SparkUnilateralExitPolicy {
    const val DEFAULT_EXIT_FEE_RATE_SAT_VB = 2L

    /**
     * Floor (sat/vB). The Spark SDK prices exits at integer sat/vB only, so
     * anything below 1 cannot be expressed — the floor reflects what the SDK
     * supports, not network relay rules.
     *
     * There is intentionally no ceiling: the SDK accepts any rate, and an
     * emergency exit must be buildable at spike market rates — the quote
     * review (recoverable vs. total fee in sats) is the real guardrail
     * against overpayment, not a rate cap.
     */
    const val MIN_EXIT_FEE_RATE_SAT_VB = 1L

    /**
     * Upper-bound signed input weights (weight units) for native-SegWit
     * funding inputs. Conservative maxima so quoted fees cover the real tx.
     */
    const val P2WPKH_SIGNED_INPUT_WEIGHT = 273L
    const val P2TR_KEYPATH_SIGNED_INPUT_WEIGHT = 231L
    // P2WSH witnesses are script-defined (multisig size is unbounded from the
    // scriptPubkey hash alone), so this is a generous ceiling covering common
    // multisigs. It over-quotes rather than under-funds the CPFP leg.
    const val P2WSH_SIGNED_INPUT_WEIGHT = 1500L

    enum class FundingScriptClass {
        P2WPKH,
        P2WSH,
        P2TR,
    }

    /**
     * Classifies a funding script as usable native SegWit or not.
     * Returns null for legacy (non-SegWit) scripts, which the SDK rejects.
     */
    fun classifyFundingScript(scriptPubkeyHex: String): FundingScriptClass? {
        val hex = scriptPubkeyHex.trim().lowercase()
        if (hex.length % 2 != 0 || hex.any { it !in '0'..'9' && it !in 'a'..'f' }) return null
        return when {
            // OP_0 <20 bytes> — P2WPKH (22 bytes)
            hex.length == 44 && hex.startsWith("0014") -> FundingScriptClass.P2WPKH
            // OP_0 <32 bytes> — P2WSH (custom signer path; weight is caller-provided)
            hex.length == 68 && hex.startsWith("0020") -> FundingScriptClass.P2WSH
            // OP_1 <32 bytes> — P2TR keypath
            hex.length == 68 && hex.startsWith("5120") -> FundingScriptClass.P2TR
            else -> null
        }
    }

    fun signedInputWeightFor(scriptClass: FundingScriptClass): Long =
        when (scriptClass) {
            FundingScriptClass.P2WPKH -> P2WPKH_SIGNED_INPUT_WEIGHT
            FundingScriptClass.P2WSH -> P2WSH_SIGNED_INPUT_WEIGHT
            FundingScriptClass.P2TR -> P2TR_KEYPATH_SIGNED_INPUT_WEIGHT
        }

    fun floorFeeRateSatPerVb(requested: Long): Long =
        requested.coerceAtLeast(MIN_EXIT_FEE_RATE_SAT_VB)

    /**
     * Upper bound (chars) for an imported exit-state hex blob. The blob is
     * MB-scale; anything larger is not an exit state.
     */    const val EXIT_STATE_HEX_MAX_CHARS = 16 * 1024 * 1024

    /**
     * Normalizes pasted/picked exit-state hex for import: strips surrounding
     * and embedded whitespace, then requires non-empty, even-length,
     * hex-only content within [EXIT_STATE_HEX_MAX_CHARS]. Returns the
     * canonical lowercase hex. Throws [IllegalArgumentException] naming the
     * problem — fail before the SDK parses anything.
     */
    fun normalizeExitStateHex(raw: String): String {
        val hex = raw.filterNot { it.isWhitespace() }.lowercase()
        require(hex.isNotEmpty()) { "Exit data is empty" }
        require(hex.length <= EXIT_STATE_HEX_MAX_CHARS) { "Exit data is too large" }
        require(hex.length % 2 == 0) { "Exit data is not valid hex" }
        require(hex.all { it in '0'..'9' || it in 'a'..'f' }) { "Exit data is not valid hex" }
        return hex
    }

    /**
     * Guards an instant deposit claim's fee ceiling before reaching the SDK:
     * the ceiling must be positive and below the deposit value (the claim
     * fails rather than paying more, but a ceiling at/above the value is
     * never what the user reviewed). Returns null when acceptable.
     */
    fun validateClaimCeiling(
        amountSats: Long,
        maxFeeSats: Long,
    ): String? {
        if (maxFeeSats <= 0L) return "Claim fee ceiling must be positive"
        if (maxFeeSats >= amountSats) return "Claim fee must be below the deposit value"
        return null
    }

    /**
     * Sanity filter for a fetched instant-claim quote: values must be
     * positive and internally consistent (credit + fee ≈ amount). Corrupt or
     * overflowing SDK numerics (coerced by [toLongSafe]-style parsing) must
     * hide the offer, never paint wrapped amounts.
     */
    fun isPlausibleClaimQuote(
        amountSats: Long,
        creditSats: Long,
        feeSats: Long,
    ): Boolean =
        amountSats > 0L &&
            creditSats > 0L &&
            feeSats >= 0L &&
            creditSats <= amountSats &&
            creditSats + feeSats <= amountSats + amountSats / 100L

    /**
     * Checksum-aware destination plausibility gate (network-agnostic: mainnet,
     * testnet, signet, regtest). Rejects empty input, silent-payment codes,
     * and anything that fails base58check/bech32 checksum verification.
     *
     * This is the first layer only — callers must additionally verify the
     * address against the L1 wallet network (BDK parse) before quoting, so a
     * testnet address can never fund a mainnet sweep.
     */
    fun isPlausibleBitcoinAddress(value: String): Boolean {
        val address = value.trim()
        if (address.isEmpty()) return false
        val lower = address.lowercase()
        if (lower.startsWith("sp1")) return false
        return when {
            address.startsWith("1") || address.startsWith("3") ||
                address.startsWith("m") || address.startsWith("n") ||
                address.startsWith("2") -> validateBase58Check(address)
            lower.startsWith("bc1q") -> validateBech32(address, "bc", bech32m = false)
            lower.startsWith("bc1p") -> validateBech32(address, "bc", bech32m = true)
            lower.startsWith("tb1q") || lower.startsWith("bcrt1q") -> {
                val hrp = lower.substringBefore("1")
                validateBech32(address, hrp, bech32m = false)
            }
            lower.startsWith("tb1p") || lower.startsWith("bcrt1p") -> {
                val hrp = lower.substringBefore("1")
                validateBech32(address, hrp, bech32m = true)
            }
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
            val hash2 = digest.digest(digest.digest(payload))
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
        hrp: String,
        bech32m: Boolean,
    ): Boolean {
        val charset = "qpzry9x8gf2tvdw0s3jn54khce6mua7l"
        val lower = address.lowercase()
        if (address != lower && address != address.uppercase()) return false
        val sep = lower.lastIndexOf('1')
        if (sep < 1 || sep + 7 > lower.length || lower.length > 90) return false
        if (lower.take(sep) != hrp) return false
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

    /**
     * Re-validates selected funding against the live L1 wallet view.
     * Returns null when every input is well-formed and still unspent,
     * confirmed, and unfrozen; otherwise a human-readable reason naming the
     * first conflicting outpoint (mirrors the SDK `FundingUtxoConflict` UX at
     * our own gate).
     */
    fun validateFundingAgainstLive(
        funding: List<github.aeonbtc.ibiswallet.data.model.SparkExitFundingUtxo>,
        liveUtxos: List<SparkExitLiveUtxo>,
    ): String? {
        if (funding.isEmpty()) return "Exit funding UTXO required"
        val liveByOutpoint = liveUtxos.associateBy { it.outpoint.lowercase() }
        for (input in funding) {
            if (input.txid.isBlank() || input.scriptPubkeyHex.isBlank() || input.valueSats <= 0L) {
                return "Exit funding entry is malformed — re-pick funding"
            }
            if (classifyFundingScript(input.scriptPubkeyHex) == null) {
                return "Funding ${input.txid}:${input.vout} is not native SegWit — re-pick funding"
            }
            val outpoint = "${input.txid}:${input.vout}".lowercase()
            val live = liveByOutpoint[outpoint]
                ?: return "Funding ${input.txid}:${input.vout} is no longer unspent — re-pick funding"
            if (!live.isConfirmed) return "Funding ${input.txid}:${input.vout} is unconfirmed — re-pick funding"
            if (live.isFrozen) return "Funding ${input.txid}:${input.vout} is frozen — re-pick funding"
        }
        return null
    }

    sealed interface BroadcastReadiness {
        /** SDK reports the step ready: broadcast it (with its CPFP package). */
        data object Ready : BroadcastReadiness

        data object AlreadyConfirmed : BroadcastReadiness

        /** A `depends_on` step has yet to confirm; timelock counts from there. */
        data object WaitingOnDependencies : BroadcastReadiness

        /**
         * Inputs confirmed but the relative timelock has yet to mature.
         * [spendableAtHeight] is the first block that can include the tx, when
         * the chain service could resolve it.
         */
        data class WaitingOnTimelock(
            val spendableAtHeight: UInt?,
        ) : BroadcastReadiness

        /**
         * Chain service could not determine status. Do not broadcast blindly —
         * refresh the exit once the chain service recovers.
         */
        data object Unknown : BroadcastReadiness
    }

    /**
     * Broadcast gate, driven by the SDK-resolved status (`check_unilateral_exit`
     * accounts for `depends_on` and `csv_timelock_blocks` against the chain
     * tip, so the app never guesses ordering itself).
     */
    fun broadcastReadinessOf(tx: SparkExitTx): BroadcastReadiness =
        when (tx.status) {
            SparkExitTxStatus.CONFIRMED -> BroadcastReadiness.AlreadyConfirmed
            SparkExitTxStatus.READY -> BroadcastReadiness.Ready
            SparkExitTxStatus.WAITING_FOR_DEPENDENCIES -> BroadcastReadiness.WaitingOnDependencies
            SparkExitTxStatus.WAITING_FOR_TIMELOCK ->
                BroadcastReadiness.WaitingOnTimelock(tx.spendableAtHeight)
            SparkExitTxStatus.UNVERIFIED -> BroadcastReadiness.Unknown
        }

    /**
     * Chain-truth completion: an exit is complete only when every transaction
     * reports chain-confirmed. There are no manual broadcast flags anymore —
     * resending a step is harmless, so completion never depends on UI clicks.
     */
    fun isExitComplete(txs: List<SparkExitTx>): Boolean =
        txs.isNotEmpty() && txs.all { it.status == SparkExitTxStatus.CONFIRMED }

    /**
     * Quote→build binding: the build must consume the exact quote the user
     * reviewed. A concurrent re-quote swapping the prepared response mid-build
     * must fail loudly instead of signing a non-reviewed set.
     */
    fun quotesMatch(
        reviewed: SparkExitQuote,
        prepared: SparkExitQuote,
    ): Boolean =
        reviewed.leafIds == prepared.leafIds &&
            reviewed.destination == prepared.destination &&
            reviewed.feeRateSatPerVb == prepared.feeRateSatPerVb

    /**
     * Quote-coverage check: quoted gross leaf values should sum to the settled
     * balance. Quotes are priced post-sync, so a material gap means Auto left
     * leaves out — usually dust worth less than its own exit cost (the SDK
     * keeps a leaf only when its value exceeds that cost). Fees never cause a
     * gap (leaf values are gross), so anything beyond dust + 1% relative
     * margin is a real signal. Returns true when coverage looks complete (or
     * inputs are corrupt, in which case nagging would be noise).
     */
    const val QUOTE_COVERAGE_GAP_FLOOR_SATS = 1_000L

    fun isQuoteCoveringBalance(balanceSats: Long, quotedLeafSats: Long): Boolean {
        if (balanceSats < 0L || quotedLeafSats < 0L) return true
        val gap = kotlin.math.abs(balanceSats - quotedLeafSats)
        if (gap <= QUOTE_COVERAGE_GAP_FLOOR_SATS) return true
        val relativePercent = if (balanceSats > 0L) gap * 100L / balanceSats else 100L
        return relativePercent <= 1L
    }

    sealed interface QuoteEconomics {
        data object WorthIt : QuoteEconomics

        data class Empty(
            val reason: String,
        ) : QuoteEconomics

        data class NotWorthIt(
            val recoverableValueSats: Long,
            val totalFeeSats: Long,
        ) : QuoteEconomics
    }

    /**
     * Applies the guide's two rules: non-empty selection and recoverable
     * strictly above total fee (fan-out included).
     */
    fun evaluateQuote(
        leafCount: Int,
        recoverableValueSats: Long,
        totalFeeSats: Long,
    ): QuoteEconomics {
        if (leafCount == 0) return QuoteEconomics.Empty("No leaves worth exiting at this fee rate")
        if (recoverableValueSats <= totalFeeSats) {
            return QuoteEconomics.NotWorthIt(recoverableValueSats, totalFeeSats)
        }
        return QuoteEconomics.WorthIt
    }

    /**
     * Per-branch funding skips the fan-out entirely, so it is always the
     * safer recommendation when the single-UTXO margin is thin (under 10%)
     * or negative.
     */
    fun preferPerBranchFunding(
        recoverableValueSats: Long,
        totalFeeSats: Long,
    ): Boolean {
        if (recoverableValueSats <= totalFeeSats) return true
        val margin = recoverableValueSats - totalFeeSats
        return margin * 10 < recoverableValueSats
    }

    /**
     * Total funding required across the chosen funding mode.
     */
    fun requiredFundingSats(
        singleUtxoFundingSats: Long,
        perBranchFunding: List<Long>,
        usePerBranch: Boolean,
    ): Long =
        if (usePerBranch) {
            perBranchFunding.fold(0L) { acc, v -> acc + v }
        } else {
            singleUtxoFundingSats
        }

    /**
     * Whether the user's funding selection satisfies the quoted requirement.
     * Single-UTXO mode needs exactly one UTXO covering the fan-out fee;
     * per-branch mode needs at least one UTXO per branch covering the summed
     * total. Mirrors the build gate: an empty selection never passes.
     */
    fun isFundingSelectionReady(
        selectedCount: Int,
        selectedTotalSats: Long,
        singleUtxoFundingSats: Long,
        perBranchFundingSats: List<Long>,
        usePerBranch: Boolean,
    ): Boolean {
        if (selectedCount == 0) return false
        return if (usePerBranch) {
            selectedCount >= perBranchFundingSats.size &&
                selectedTotalSats >= requiredFundingSats(singleUtxoFundingSats, perBranchFundingSats, true)
        } else {
            selectedCount == 1 && selectedTotalSats >= singleUtxoFundingSats
        }
    }

    /**
     * Repository build gate: derives the funding mode from the reviewed quote
     * (same rule the quote card uses) and enforces the selection requirement,
     * so a build can never proceed underfunded even if the UI gate is bypassed.
     */
    fun isExitBuildFundingSufficient(
        quote: SparkExitQuote,
        selectedCount: Int,
        selectedTotalSats: Long,
    ): Boolean {
        val usePerBranch = preferPerBranchFunding(quote.recoverableValueSats, quote.totalFeeSats)
        return isFundingSelectionReady(
            selectedCount = selectedCount,
            selectedTotalSats = selectedTotalSats,
            singleUtxoFundingSats = quote.singleUtxoFundingSats,
            perBranchFundingSats = quote.perBranchFunding.map { it.fundingSats },
            usePerBranch = usePerBranch,
        )
    }
}
