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

/**
 * Pending (unpaid) BOLT11 invoice kept visible on the Spark Receive screen until
 * it is paid or the user generates a new one. Cached per wallet in SecureStorage
 * so it survives navigation, wallet reloads, and process death. The invoice is
 * public-by-design (it is shown as a QR for payers).
 */
data class SparkPendingLnInvoice(
    val paymentRequest: String,
    val amountSats: Long?,
    val description: String = "",
    val createdAtMs: Long = 0L,
)

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
    /** L1 txid for DEPOSIT / WITHDRAW payments (from SDK PaymentDetails). */
    val onchainTxid: String? = null,
    val onchainVout: UInt? = null,
    /** Human failure detail when known (SDK often only exposes status FAILED). */
    val failureReason: String? = null,
)

data class SparkUnclaimedDeposit(
    val txid: String,
    val vout: UInt,
    val amountSats: Long,
    val isMature: Boolean,
    val timestamp: Long? = null,
    val address: String? = null,
    val claimError: String? = null,
    /**
     * True when this deposit is our own center-Swap L1→Spark peg (recorded by
     * [SparkRepository.addLocalPendingDeposit], the only producer). The linked
     * L1 funding tx is not in wallet state yet at creation time, so history
     * titles key off this flag instead of waiting for the L1 link — otherwise
     * the row flickers Received → Swap on the next sync.
     */
    val isSwapDeposit: Boolean = false,
)

/**
 * Instant (0-conf) claim quote for a pending deposit (Breez 0.25+). The
 * provider may decline to offer one ([SparkRepository.fetchDepositClaimQuote]
 * returns null) — the deposit then simply matures normally.
 */
data class SparkDepositClaimQuote(
    val txid: String,
    val vout: UInt,
    val amountSats: Long,
    val confirmations: Long,
    /** What reaches the balance if claimed now. */
    val creditSats: Long,
    /** Spread fee taken from the deposit value. */
    val feeSats: Long,
    val feeRateSatPerVb: Long,
    /** True when the fee was derived from on-chain estimates, not quoted. */
    val isEstimate: Boolean,
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

    /** Sequential multi-payment preview (Spark addresses only). */
    data class MultiPreview(
        val items: List<MultiItem>,
        val totalAmountSats: Long,
        val totalFeeSats: Long,
    ) : SparkSendState {
        data class MultiItem(
            val paymentRequest: String,
            val amountSats: Long,
            val feeSats: Long,
        )
    }

    data object Sending : SparkSendState

    data class MultiSending(
        val completed: Int,
        val total: Int,
    ) : SparkSendState

    data class Sent(val paymentId: String?) : SparkSendState

    data class MultiSent(
        val succeeded: Int,
        val failed: Int,
        val detail: String? = null,
    ) : SparkSendState

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
    data class Paid(
        val kind: SparkReceiveKind,
        val paymentId: String,
        val amountSats: Long,
        val paymentRequest: String? = null,
    ) : SparkReceiveState
    data class Error(val message: String) : SparkReceiveState
}

sealed class SparkEvent {
    data class PaymentReceived(
        val paymentId: String,
        val amountSats: Long,
        val kind: SparkReceiveKind?,
    ) : SparkEvent()
}
