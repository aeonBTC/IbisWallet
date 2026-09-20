package github.aeonbtc.ibiswallet.util

import kotlin.math.roundToLong

/**
 * Shared Ark amount parsing: commas/spaces stripped, overflow-guarded, finite-checked.
 * Single source of truth for Send / Transfer / Receive / Lifecycle paths.
 */
object ArkAmountUtils {
    fun parseAmountToSats(
        input: String,
        useSats: Boolean,
        isUsdMode: Boolean = false,
        btcPrice: Double? = null,
    ): Long? {
        val trimmed = input.trim().replace(",", "").replace(" ", "")
        if (trimmed.isBlank()) return null
        if (isUsdMode) {
            if (btcPrice == null || btcPrice <= 0) return null
            val fiat = trimmed.toDoubleOrNull() ?: return null
            if (!fiat.isFinite() || fiat <= 0.0) return null
            val sats = (fiat / btcPrice) * 100_000_000.0
            if (!sats.isFinite() || sats <= 0.0 || sats > Long.MAX_VALUE.toDouble()) return null
            return sats.roundToLong().takeIf { it > 0 }
        }
        return when {
            useSats -> trimmed.toLongOrNull()?.takeIf { it > 0 }
            else -> {
                val btc = trimmed.toDoubleOrNull() ?: return null
                if (!btc.isFinite() || btc <= 0.0) return null
                val sats = btc * 100_000_000.0
                if (!sats.isFinite() || sats <= 0.0 || sats > Long.MAX_VALUE.toDouble()) return null
                sats.roundToLong().takeIf { it > 0 }
            }
        }
    }

    fun filterAmountInput(
        value: String,
        useSats: Boolean,
        isUsdMode: Boolean,
    ): String {
        var v = value
        val pattern =
            when {
                isUsdMode -> Regex("^\\d*\\.?\\d{0,2}$")
                useSats -> Regex("^\\d*$")
                else -> Regex("^\\d*\\.?\\d{0,8}$")
            }
        while (v.isNotEmpty() && !v.matches(pattern)) {
            v = v.dropLast(1)
        }
        return v
    }
}
