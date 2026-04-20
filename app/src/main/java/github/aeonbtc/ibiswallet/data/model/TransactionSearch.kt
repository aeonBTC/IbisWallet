package github.aeonbtc.ibiswallet.data.model

enum class TransactionSearchLayer(
    val dbValue: String,
) {
    BITCOIN("bitcoin"),
    LIQUID("liquid"),
    ;

    companion object {
        fun fromDbValue(value: String): TransactionSearchLayer? = entries.firstOrNull { it.dbValue == value }
    }
}

data class TransactionSearchFilters(
    val swapOnly: Boolean = false,
    val includeSwap: Boolean = true,
    val includeLightning: Boolean = true,
    val includeNative: Boolean = true,
    val includeUsdt: Boolean = true,
)

data class TransactionSearchDocument(
    val walletId: String,
    val layer: TransactionSearchLayer,
    val txid: String,
    val sortTimestampSeconds: Long?,
    val sortHeight: Long?,
    val isSwap: Boolean = false,
    val isLightning: Boolean = false,
    val isNative: Boolean = false,
    val hasUsdt: Boolean = false,
    val label: String = "",
    val address: String = "",
    val changeAddress: String = "",
    val recipientAddress: String = "",
    val addressLabel: String = "",
    val dateTokens: String = "",
)

data class TransactionSearchResult(
    val txids: List<String>,
    val totalCount: Int,
)
