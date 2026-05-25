package github.aeonbtc.ibiswallet.ui.screens

import github.aeonbtc.ibiswallet.data.local.SecureStorage
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

enum class BalanceDateFormat(
    val storageValue: String,
    val datePattern: String,
) {
    MONTH_DD_YYYY(SecureStorage.DATE_FORMAT_MONTH_DD_YYYY, "MMMM d, yyyy"),
    MM_DD_YY(SecureStorage.DATE_FORMAT_MM_DD_YY, "MM/dd/yy"),
    DD_MM_YY(SecureStorage.DATE_FORMAT_DD_MM_YY, "dd/MM/yy"),
    YYYY_MM_DD(SecureStorage.DATE_FORMAT_YYYY_MM_DD, "yyyy/MM/dd"),
    ;

    companion object {
        fun fromStorageValue(value: String?): BalanceDateFormat =
            entries.find { it.storageValue == value } ?: MONTH_DD_YYYY
    }
}

fun formatBalanceTimestamp(
    timestamp: Long,
    dateFormatStorageValue: String = SecureStorage.DATE_FORMAT_MONTH_DD_YYYY,
): String {
    if (timestamp <= 0L) return ""
    return formatTimestamp(
        timestampMillis = normalizeTimestampMillis(timestamp),
        dateFormatStorageValue = dateFormatStorageValue,
        separator = " · ",
    )
}

fun formatFullTimestamp(
    timestamp: Long,
    dateFormatStorageValue: String = SecureStorage.DATE_FORMAT_MONTH_DD_YYYY,
): String {
    if (timestamp <= 0L) return ""
    return formatTimestamp(
        timestampMillis = normalizeTimestampMillis(timestamp),
        dateFormatStorageValue = dateFormatStorageValue,
        separator = " ",
    )
}

private fun formatTimestamp(
    timestampMillis: Long,
    dateFormatStorageValue: String,
    separator: String,
): String {
    val date = Date(timestampMillis)
    val locale = Locale.getDefault()
    val balanceDateFormat = BalanceDateFormat.fromStorageValue(dateFormatStorageValue)
    val dateText = SimpleDateFormat(balanceDateFormat.datePattern, locale).format(date)
    val timeText = SimpleDateFormat("HH:mm", locale).format(date)
    return "$dateText$separator$timeText"
}

private fun normalizeTimestampMillis(timestamp: Long): Long {
    return if (timestamp > 10_000_000_000L) timestamp else timestamp * 1000L
}

fun formatVBytes(vBytes: Double): String {
    val formatted = String.format(Locale.US, "%.2f", vBytes)
    return formatted.trimEnd('0').trimEnd('.')
}
