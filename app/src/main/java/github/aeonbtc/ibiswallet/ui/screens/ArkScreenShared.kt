package github.aeonbtc.ibiswallet.ui.screens

import java.util.Locale
import kotlin.math.roundToLong

internal const val ARK_HIDDEN_AMOUNT = "****"

internal fun formatArkAmount(
    amountSats: Long,
    useSats: Boolean,
): String =
    if (useSats) {
        "%,d sats".format(Locale.US, amountSats)
    } else {
        String.format(Locale.US, "%.8f BTC", amountSats / 100_000_000.0)
    }

internal fun parseAmountToSats(
    input: String,
    useSats: Boolean,
): Long? {
    val trimmed = input.trim().replace(",", "")
    if (trimmed.isBlank()) return null
    return if (useSats) {
        trimmed.toLongOrNull()?.takeIf { it > 0 }
    } else {
        trimmed.toDoubleOrNull()?.takeIf { it > 0 }?.let { (it * 100_000_000.0).roundToLong() }
    }
}
