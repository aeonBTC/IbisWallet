package github.aeonbtc.ibiswallet.util

import github.aeonbtc.ibiswallet.data.model.LiquidAsset
import github.aeonbtc.ibiswallet.data.model.LiquidTransaction
import github.aeonbtc.ibiswallet.data.model.LiquidTxSource
import github.aeonbtc.ibiswallet.data.model.TransactionDetails
import github.aeonbtc.ibiswallet.data.model.TransactionSearchDocument
import github.aeonbtc.ibiswallet.data.model.TransactionSearchLayer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val searchTokenDelimiterRegex = Regex("[^\\p{L}\\p{Nd}]+")
private val searchWhitespaceRegex = Regex("\\s+")

private val transactionSearchDatePatterns =
    listOf(
        "MMM d, yyyy · HH:mm",
        "MMM d yyyy HH:mm",
        "MMM d, yyyy",
        "MMM d yyyy",
        "MMM d",
        "MMMM d, yyyy",
        "MMMM d yyyy",
        "MMMM d",
        "d MMM yyyy",
        "d MMM",
        "d MMMM yyyy",
        "d MMMM",
        "yyyy-MM-dd HH:mm",
        "yyyy-MM-dd",
        "yyyy/MM/dd HH:mm",
        "yyyy/MM/dd",
        "M/d/yyyy HH:mm",
        "M/d/yyyy",
        "M/d",
        "MM/dd/yyyy",
        "MM/dd",
        "d/M/yyyy",
        "d/M",
        "dd/MM/yyyy",
        "dd/MM",
        "HH:mm",
        "yy",
        "yyyy",
    )

private fun normalizeSearchValue(value: String): String =
    value
        .lowercase(Locale.ROOT)
        .replace(searchTokenDelimiterRegex, " ")
        .trim()
        .replace(searchWhitespaceRegex, " ")

private fun tokenizeSearchValue(value: String): List<String> =
    normalizeSearchValue(value)
        .split(' ')
        .map { it.trim() }
        .filter { it.isNotBlank() }

private fun expandDateTokens(tokens: Iterable<String>): Set<String> =
    buildSet {
        tokens.forEach { token ->
            add(token)
            if (token.length == 4 && token.all(Char::isDigit)) {
                add(token.takeLast(2))
            }
        }
    }

private fun buildDateSearchTokens(timestampSeconds: Long?): String {
    if (timestampSeconds == null) return ""

    val date = Date(timestampSeconds * 1000L)
    val tokens = linkedSetOf<String>()
    val locales = linkedSetOf(Locale.getDefault(), Locale.US)

    transactionSearchDatePatterns.forEach { pattern ->
        locales.forEach { locale ->
            val formatted = SimpleDateFormat(pattern, locale).format(date)
            tokens += expandDateTokens(tokenizeSearchValue(formatted))
        }
    }

    return tokens.joinToString(separator = " ")
}

fun buildTransactionSearchMatchQuery(query: String): String? {
    val tokens = tokenizeSearchValue(query)
    if (tokens.isEmpty()) return null
    return tokens.joinToString(separator = " AND ") { "$it*" }
}

fun buildTransactionSearchableText(document: TransactionSearchDocument): String =
    listOf(
        document.txid,
        document.label,
        document.addressLabel,
        document.address,
        document.changeAddress,
        document.recipientAddress,
        document.dateTokens,
    ).filter { it.isNotBlank() }
        .joinToString(separator = " ")

fun buildBitcoinTransactionSearchDocument(
    walletId: String,
    transaction: TransactionDetails,
    transactionLabel: String?,
    addressLabel: String?,
): TransactionSearchDocument =
    TransactionSearchDocument(
        walletId = walletId,
        layer = TransactionSearchLayer.BITCOIN,
        txid = transaction.txid,
        sortTimestampSeconds = transaction.timestamp,
        sortHeight = transaction.confirmationTime?.height?.toLong(),
        isSwap = transaction.swapDetails != null,
        label = transactionLabel.orEmpty(),
        address = transaction.address.orEmpty(),
        changeAddress = transaction.changeAddress.orEmpty(),
        addressLabel = addressLabel.orEmpty(),
        dateTokens = buildDateSearchTokens(transaction.timestamp),
    )

fun buildLiquidTransactionSearchDocument(
    walletId: String,
    transaction: LiquidTransaction,
    transactionLabel: String? = null,
): TransactionSearchDocument {
    val source = transaction.source
    return TransactionSearchDocument(
        walletId = walletId,
        layer = TransactionSearchLayer.LIQUID,
        txid = transaction.txid,
        sortTimestampSeconds = transaction.timestamp,
        sortHeight = transaction.height?.toLong(),
        isSwap = source == LiquidTxSource.CHAIN_SWAP,
        isLightning =
            source == LiquidTxSource.LIGHTNING_RECEIVE_SWAP ||
                source == LiquidTxSource.LIGHTNING_SEND_SWAP,
        isNative = source == LiquidTxSource.NATIVE,
        hasUsdt = transaction.deltaForAsset(LiquidAsset.USDT_ASSET_ID) != 0L,
        label = transactionLabel ?: transaction.memo,
        address = transaction.walletAddress.orEmpty(),
        changeAddress = transaction.changeAddress.orEmpty(),
        recipientAddress = transaction.recipientAddress.orEmpty(),
        dateTokens = buildDateSearchTokens(transaction.timestamp),
    )
}
