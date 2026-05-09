package github.aeonbtc.ibiswallet.data.model

enum class SparkReceiveKind {
    SPARK_ADDRESS,
    SPARK_INVOICE,
    BITCOIN_ADDRESS,
    BOLT11_INVOICE,
}

enum class SparkOnchainFeeSpeed {
    SLOW,
    MEDIUM,
    FAST,
}

data class SparkOnchainFeeQuote(
    val speed: SparkOnchainFeeSpeed,
    val feeSats: Long,
)

data class SparkWalletState(
    val walletId: String? = null,
    val isInitialized: Boolean = false,
    val identityPubkey: String? = null,
    val balanceSats: Long = 0,
    val payments: List<SparkPayment> = emptyList(),
    val unclaimedDeposits: List<SparkUnclaimedDeposit> = emptyList(),
    val lightningAddress: String? = null,
    val isSyncing: Boolean = false,
    val lastSyncTimestamp: Long = 0,
    val error: String? = null,
)

data class SparkPayment(
    val id: String,
    val type: String,
    val status: String,
    val amountSats: Long,
    val feeSats: Long,
    val timestamp: Long,
    val method: String,
    val recipient: String? = null,
    val methodDetails: String = method,
)

data class SparkUnclaimedDeposit(
    val txid: String,
    val vout: UInt,
    val amountSats: Long,
    val isMature: Boolean,
    val timestamp: Long? = null,
    val address: String? = null,
    val claimError: String? = null,
)

sealed interface SparkSendState {
    data object Idle : SparkSendState
    data object Preparing : SparkSendState
    data class Preview(
        val paymentRequest: String,
        val amountSats: Long?,
        val feeSats: Long?,
        val method: String,
        val onchainFeeSpeed: SparkOnchainFeeSpeed? = null,
        val onchainFeeQuotes: List<SparkOnchainFeeQuote> = emptyList(),
    ) : SparkSendState
    data object Sending : SparkSendState
    data class Sent(val paymentId: String?) : SparkSendState
    data class Error(val message: String) : SparkSendState
}

sealed interface SparkReceiveState {
    data object Idle : SparkReceiveState
    data object Loading : SparkReceiveState
    data class Ready(
        val kind: SparkReceiveKind,
        val paymentRequest: String,
        val feeSats: Long,
    ) : SparkReceiveState
    data class Error(val message: String) : SparkReceiveState
}
