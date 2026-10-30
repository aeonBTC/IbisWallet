package github.aeonbtc.ibiswallet.util

import kotlin.math.ceil

/**
 * Dusting-attack protection policy (Samourai-style).
 *
 * A dusting attack sends tiny unsolicited outputs to victim addresses. When
 * the victim later co-spends that dust with other coins, the common-input
 * ownership heuristic links the addresses. Defense: detect suspected dust,
 * keep it out of automatic coin selection, and warn before manual spends.
 *
 * A UTXO is suspected dust when ALL hold:
 * - it is NOT change (internal keychain outputs are self-created),
 * - its value is at or under [maxDustSats] (default 1,000 sats — the same
 *   reference value BIP 451 uses for its configurable disposal threshold),
 * - it is uneconomical to spend alone: value < inputVSize × feeRate, where
 *   the fee rate never drops below [DUST_RELAY_FEE_RATE] (3 sat/vB, the
 *   Bitcoin Core `-dustrelayfee` default), and values under the per-type
 *   relay dust floor (Core `GetDustThreshold`: 294 P2WPKH, 330 P2TR/P2WSH,
 *   540 P2SH, 546 P2PKH) are always dust.
 *
 * Pure JVM — no Android or BDK dependency, unit tested on the local JVM.
 */
object DustPolicy {
    /** Default small-value cap in sats (BIP 451 reference value). */
    const val DEFAULT_MAX_DUST_SATS: Long = 1_000L

    /** Offered caps in Settings (sats). */
    val MAX_DUST_OPTIONS_SATS: List<Long> = listOf(500L, 1_000L)

    /** Upper bound for a user-entered custom cap (sats). */
    const val MAX_CUSTOM_DUST_SATS: Long = 10_000L

    /** Bitcoin Core `-dustrelayfee` default: floor for the fee-aware check. */
    const val DUST_RELAY_FEE_RATE: Double = 3.0

    /** Fallback rate when no fee estimates are available. */
    const val FALLBACK_FEE_RATE_SAT_PER_VB: Double = 5.0

    enum class ScriptType {
        P2PKH,
        P2SH,
        P2WPKH,
        P2WSH,
        P2TR,
        UNKNOWN,
    }

    /** Relay dust floor per script type (Core `GetDustThreshold` values). */
    fun relayDustFloorSats(scriptType: ScriptType): Long =
        when (scriptType) {
            ScriptType.P2PKH -> 546L
            ScriptType.P2SH -> 540L
            ScriptType.P2WPKH -> 294L
            ScriptType.P2WSH -> 330L
            ScriptType.P2TR -> 330L
            ScriptType.UNKNOWN -> 546L
        }

    /**
     * Estimated input vsize (vB) to spend an output of this type.
     * Conservative single-sig sizes; multisig inputs are larger, so plain
     * outputs of unknown type use the P2PKH weight.
     */
    fun inputVSizeVb(scriptType: ScriptType): Double =
        when (scriptType) {
            ScriptType.P2PKH -> 148.0
            ScriptType.P2SH -> 148.0
            ScriptType.P2WPKH -> 68.0
            ScriptType.P2WSH -> 68.0
            ScriptType.P2TR -> 57.5
            ScriptType.UNKNOWN -> 148.0
        }

    /** Fee cost (sats, rounded up) to spend an input of this type alone. */
    fun spendCostSats(
        scriptType: ScriptType,
        feeRateSatPerVb: Double,
    ): Long {
        val rate = feeRateSatPerVb.takeIf { it > 0.0 } ?: FALLBACK_FEE_RATE_SAT_PER_VB
        return ceil(inputVSizeVb(scriptType) * rate).toLong().coerceAtLeast(1L)
    }

    /**
     * Classify a raw scriptPubKey. Unknown scripts fall back to the most
     * conservative (largest) dust parameters.
     */
    fun classifyScript(scriptBytes: ByteArray): ScriptType {
        if (scriptBytes.size == 25 &&
            scriptBytes[0] == 0x76.toByte() &&
            scriptBytes[1] == 0xA9.toByte() &&
            scriptBytes[2] == 0x14.toByte() &&
            scriptBytes[23] == 0x88.toByte() &&
            scriptBytes[24] == 0xAC.toByte()
        ) {
            return ScriptType.P2PKH
        }
        if (scriptBytes.size == 23 &&
            scriptBytes[0] == 0xA9.toByte() &&
            scriptBytes[1] == 0x14.toByte() &&
            scriptBytes[22] == 0x87.toByte()
        ) {
            return ScriptType.P2SH
        }
        if (scriptBytes.size == 22 &&
            scriptBytes[0] == 0x00.toByte() &&
            scriptBytes[1] == 0x14.toByte()
        ) {
            return ScriptType.P2WPKH
        }
        if (scriptBytes.size == 34 &&
            scriptBytes[0] == 0x00.toByte() &&
            scriptBytes[1] == 0x20.toByte()
        ) {
            return ScriptType.P2WSH
        }
        if (scriptBytes.size == 34 &&
            scriptBytes[0] == 0x51.toByte() &&
            scriptBytes[1] == 0x20.toByte()
        ) {
            return ScriptType.P2TR
        }
        return ScriptType.UNKNOWN
    }

    /**
     * True when this UTXO should be treated as suspected dusting-attack dust.
     *
     * @param amountSats output value.
     * @param scriptType classified output script.
     * @param isChange true for internal-keychain (change) outputs — never dust.
     * @param feeRateSatPerVb current fee rate, or null when unknown (fallback used).
     * @param maxDustSats user-configured small-value cap.
     */
    fun isSuspectedDust(
        amountSats: Long,
        scriptType: ScriptType,
        isChange: Boolean,
        feeRateSatPerVb: Double?,
        maxDustSats: Long = DEFAULT_MAX_DUST_SATS,
    ): Boolean {
        if (isChange) return false
        if (amountSats <= 0L) return false
        if (amountSats > maxDustSats) return false
        // Under the relay dust floor: always dust, whatever the fee market does.
        if (amountSats < relayDustFloorSats(scriptType)) return true
        val effectiveRate = (feeRateSatPerVb?.takeIf { it > 0.0 } ?: FALLBACK_FEE_RATE_SAT_PER_VB)
            .coerceAtLeast(DUST_RELAY_FEE_RATE)
        // Uneconomical to spend alone at current rates.
        return amountSats < spendCostSats(scriptType, effectiveRate)
    }
}
