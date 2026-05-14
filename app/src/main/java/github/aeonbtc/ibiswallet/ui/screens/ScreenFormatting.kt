package github.aeonbtc.ibiswallet.ui.screens

import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Currency
import java.util.Date
import java.util.Locale

private const val SATS_PER_BTC = 100_000_000.0

fun formatBtc(sats: ULong): String {
    val btc = sats.toDouble() / SATS_PER_BTC
    return String.format(Locale.US, "%.8f", btc)
}

fun formatSats(sats: ULong): String = NumberFormat.getNumberInstance(Locale.US).format(sats.toLong())

/**
 * Formats a balance or tx amount. When [includeUnit] is true and [useSats], appends lowercase **sats**
 * after the number (never **Sats** — that casing is reserved for standalone denomination labels).
 */
fun formatAmount(
    sats: ULong,
    useSats: Boolean,
    includeUnit: Boolean = false,
): String {
    val amount = if (useSats) formatSats(sats) else formatBtc(sats)
    if (!includeUnit) return amount
    return if (useSats) "$amount sats" else "$amount BTC"
}

fun formatFiat(
    amount: Double,
    currencyCode: String,
): String {
    val normalizedCode = currencyCode.uppercase(Locale.US)
    return try {
        // USD: use US locale so the symbol is "$", not "US$" (default locale disambiguates $ currencies).
        val formatLocale = if (normalizedCode == "USD") Locale.US else Locale.getDefault()
        NumberFormat.getCurrencyInstance(formatLocale).run {
            currency = Currency.getInstance(normalizedCode)
            format(amount)
        }
    } catch (_: IllegalArgumentException) {
        val fallback =
            NumberFormat.getNumberInstance(Locale.US).apply {
                minimumFractionDigits = 2
                maximumFractionDigits = 2
            }
        "$normalizedCode ${fallback.format(amount)}"
    }
}

fun formatUsd(amount: Double): String = formatFiat(amount, "USD")

fun formatFullTimestamp(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM d, yyyy HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

fun formatVBytes(vBytes: Double): String {
    val formatted = String.format(Locale.US, "%.2f", vBytes)
    return formatted.trimEnd('0').trimEnd('.')
}
