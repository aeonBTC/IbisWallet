package github.aeonbtc.ibiswallet.data.model

/**
 * Data models for Layer 2 (Liquid Network) wallet functionality.
 * Includes models for LWK wallet state, Boltz swaps, SideSwap pegs,
 * and Liquid Electrum server configuration.
 */

// ──────────────────────────────────────────────
// Liquid assets
// ──────────────────────────────────────────────

data class LiquidAsset(
    val assetId: String,
    val ticker: String,
    val name: String,
    val precision: Int = 8,
) {
    val isPolicyAsset: Boolean get() = assetId == LBTC_ASSET_ID

    companion object {
        const val LBTC_ASSET_ID =
            "6f0279e9ed041c3d710a9f57d0c02928416460c4b722ae3457a11eec381c526d"
        const val USDT_ASSET_ID =
            "ce091c998b83c78bb71a632313ba3760f1763d9cfcffae02258ffa9865a37bd2"

        val LBTC = LiquidAsset(
            assetId = LBTC_ASSET_ID,
            ticker = "L-BTC",
            name = "Liquid Bitcoin",
        )

        val USDT = LiquidAsset(
            assetId = USDT_ASSET_ID,
            ticker = "USDt",
            name = "Tether USD",
        )

        private val KNOWN_ASSETS = mapOf(
            LBTC_ASSET_ID to LBTC,
            USDT_ASSET_ID to USDT,
        )

        fun resolve(assetId: String): LiquidAsset =
            KNOWN_ASSETS[assetId] ?: LiquidAsset(
                assetId = assetId,
                ticker = assetId.take(8) + "\u2026",
                name = "Unknown Asset",
            )

        fun isPolicyAsset(assetId: String): Boolean = assetId == LBTC_ASSET_ID
    }
}

data class LiquidAssetBalance(
    val asset: LiquidAsset,
    val amount: Long,
)

// ──────────────────────────────────────────────
// Layer & wallet state
// ──────────────────────────────────────────────

/** Which layer the user is currently viewing */
enum class WalletLayer { LAYER1, LAYER2 }

/** Which Layer 2 backend is enabled for a wallet. */
enum class Layer2Provider {
    NONE,
    LIQUID,
    SPARK,
}

/** Liquid wallet state — parallel to WalletState for Bitcoin */
data class LiquidWalletState(
    val walletId: String? = null,
    val isInitialized: Boolean = false,
    val balanceSats: Long = 0,
    val assetBalances: List<LiquidAssetBalance> = emptyList(),
    val transactions: List<LiquidTransaction> = emptyList(),
    val currentAddress: String? = null,
    val currentAddressIndex: UInt? = null,
    val currentAddressLabel: String? = null,
    val isSyncing: Boolean = false,
    val isFullSyncing: Boolean = false,
    val syncProgress: SyncProgress? = null,
    val isTransactionHistoryLoading: Boolean = false,
    val lastSyncTimestamp: Long = 0,
    val error: String? = null,
) {
    fun balanceForAsset(assetId: String): Long =
        assetBalances.firstOrNull { it.asset.assetId == assetId }?.amount ?: 0L

    val usdtBalanceAmount: Long get() = balanceForAsset(LiquidAsset.USDT_ASSET_ID)
    val hasNonLbtcAssets: Boolean get() = assetBalances.any { !it.asset.isPolicyAsset }
}

data class LiquidTransaction(
    val txid: String,
    val balanceSatoshi: Long,
    val fee: Long,
    val feeRate: Double? = null,
    val vsize: Double? = null,
    val assetDeltas: Map<String, Long> = emptyMap(),
    val height: Int? = null,
    val timestamp: Long? = null,
    val unblindedUrl: String? = null,
    val walletAddress: String? = null,
    val walletAddressAmountSats: Long? = null,
    val changeAddress: String? = null,
    val changeAmountSats: Long? = null,
    val recipientAddress: String? = null,
    val memo: String = "",
    val source: LiquidTxSource = LiquidTxSource.NATIVE,
    val type: LiquidTxType = LiquidTxType.SEND,
    val swapDetails: LiquidSwapDetails? = null,
) {
    fun deltaForAsset(assetId: String): Long = assetDeltas[assetId] ?: 0L
    val involvesNonLbtcAsset: Boolean get() = assetDeltas.any { !LiquidAsset.isPolicyAsset(it.key) && it.value != 0L }
}

enum class LiquidTxType { SEND, RECEIVE, SWAP }

enum class LiquidTxSource {
    NATIVE,
    CHAIN_SWAP,
    LIGHTNING_RECEIVE_SWAP,
    LIGHTNING_SEND_SWAP,
}

enum class LiquidSwapTxRole {
    FUNDING,
    SETTLEMENT,
}

data class LiquidSwapDetails(
    val service: SwapService,
    val direction: SwapDirection,
    val swapId: String,
    val role: LiquidSwapTxRole,
    val depositAddress: String,
    val receiveAddress: String? = null,
    val refundAddress: String? = null,
    val sendAmountSats: Long = 0,
    val expectedReceiveAmountSats: Long = 0,
    val paymentInput: String? = null,
    val resolvedPaymentInput: String? = null,
    val invoice: String? = null,
    val status: String? = null,
    val timeoutBlockHeight: Int? = null,
    val refundPublicKey: String? = null,
    val claimPublicKey: String? = null,
    val swapTree: String? = null,
    val blindingKey: String? = null,
)

enum class LiquidSendKind {
    LBTC,
    LIQUID_ASSET,
    LIGHTNING_BOLT11,
    LIGHTNING_BOLT12,
    LIGHTNING_LNURL,
}

data class LiquidSendPreview(
    val kind: LiquidSendKind,
    val assetId: String? = null,
    val recipientDisplay: String,
    val recipients: List<Recipient> = emptyList(),
    val totalRecipientSats: Long? = null,
    val feeSats: Long? = null,
    val changeSats: Long? = null,
    val changeAddress: String? = null,
    val feeRate: Double = 0.1,
    val availableSats: Long = 0,
    val remainingSats: Long? = null,
    val label: String? = null,
    val note: String? = null,
    val isMaxSend: Boolean = false,
    val selectedUtxoCount: Int = 0,
    val txVBytes: Double? = null,
    val executionPlan: LightningPaymentExecutionPlan? = null,
) {
    val totalSpendSats: Long?
        get() = if (totalRecipientSats != null && feeSats != null) totalRecipientSats + feeSats else null

    val resolvedAsset: LiquidAsset
        get() = LiquidAsset.resolve(assetId ?: LiquidAsset.LBTC_ASSET_ID)

    val isLbtc: Boolean get() = assetId == null || LiquidAsset.isPolicyAsset(assetId)
}

/**
 * State for the Liquid PSET export/sign/broadcast flow (watch-only wallets).
 * Mirrors [github.aeonbtc.ibiswallet.viewmodel.PsbtState] for Bitcoin.
 */
data class LiquidPsetState(
    val isCreating: Boolean = false,
    val isBroadcasting: Boolean = false,
    val broadcastStatus: String? = null,
    val unsignedPsetBase64: String? = null,
    val signedPsetBase64: String? = null,
    val pendingLabel: String? = null,
    val error: String? = null,
    val recipientAddress: String? = null,
    val recipientAmountSats: Long = 0,
    val feeSats: Long = 0,
    val totalInputSats: Long = 0,
    val unsignedPsetInputCount: Int = 0,
    val unsignedPsetOutputCount: Int = 0,
    val assetId: String? = null,
)

sealed interface LightningPaymentExecutionPlan {
    val paymentInput: String

    data class BoltzQuote(
        override val paymentInput: String,
        val resolvedPaymentInput: String,
        val requestedAmountSats: Long?,
        val previewFundingAddress: String,
        val estimatedLockupAmountSats: Long,
        val paymentAmountSats: Long,
        val swapFeeSats: Long,
        val refundAddress: String? = null,
    ) : LightningPaymentExecutionPlan

    data class BoltzSwap(
        override val paymentInput: String,
        val backend: LightningPaymentBackend,
        val requestKey: String,
        val resolvedPaymentInput: String,
        val requestedAmountSats: Long?,
        val swapId: String,
        val lockupAddress: String,
        val lockupAmountSats: Long,
        val refundAddress: String,
        val fetchedInvoice: String? = null,
        val refundPublicKey: String? = null,
        val paymentAmountSats: Long,
        val swapFeeSats: Long,
    ) : LightningPaymentExecutionPlan

    data class DirectLiquid(
        override val paymentInput: String,
        val address: String,
        val amountSats: Long,
    ) : LightningPaymentExecutionPlan
}

sealed class LiquidSendState {
    data object Idle : LiquidSendState()

    data class Estimating(
        val kind: LiquidSendKind,
        val preview: LiquidSendPreview? = null,
    ) : LiquidSendState()

    data class ReviewReady(
        val preview: LiquidSendPreview,
    ) : LiquidSendState()

    data class Sending(
        val preview: LiquidSendPreview,
        val status: String,
        val refundAddress: String? = null,
        val detail: String? = null,
        val canDismiss: Boolean = false,
    ) : LiquidSendState()

    data class Success(
        val preview: LiquidSendPreview,
        val message: String,
        val fundingTxid: String? = null,
        val settlementTxid: String? = null,
    ) : LiquidSendState()

    data class Failed(
        val error: String,
        val preview: LiquidSendPreview? = null,
        val refundAddress: String? = null,
    ) : LiquidSendState()
}

// ──────────────────────────────────────────────
// Swap models (shared between Boltz & SideSwap)
// ──────────────────────────────────────────────

enum class SwapDirection {
    BTC_TO_LBTC,
    LBTC_TO_BTC,
}

enum class SwapService { BOLTZ, SIDESWAP }

/** Pre-quote limits for a given service + direction, fetched on tab selection */
data class SwapLimits(
    val service: SwapService,
    val direction: SwapDirection,
    val minAmount: Long,
    /** 0 = no published limit */
    val maxAmount: Long,
    /** Pre-quote service fee percentage, when published */
    val serviceFeePercent: Double? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
)

data class SwapQuote(
    val service: SwapService,
    val direction: SwapDirection,
    val sendAmount: Long,
    val receiveAmount: Long,
    val serviceFee: Long,
    /** BTC on-chain network fee (sats) — lockup or peg tx */
    val btcNetworkFee: Long,
    /** Liquid on-chain network fee (sats) — claim or delivery tx */
    val liquidNetworkFee: Long,
    /** Provider-side miner fee deducted from the swap amount */
    val providerMinerFee: Long = 0,
    /** BTC fee rate used for calculation (sat/vB) */
    val btcFeeRate: Double = 0.0,
    /** Liquid fee rate used for calculation (sat/vB) */
    val liquidFeeRate: Double = 0.0,
    /** Minimum swap amount for this service+direction (sats) */
    val minAmount: Long = 0,
    /** Maximum swap amount (0 = no published limit) */
    val maxAmount: Long = 0,
    val estimatedTime: String,
    val expiresAt: Long = 0,
    // Boltz-specific
    val boltzSwapId: String? = null,
    val boltzInvoice: String? = null,
    val boltzAddress: String? = null,
    val boltzTimeoutBlockHeight: Int? = null,
    // SideSwap-specific
    val sideSwapOrderId: String? = null,
    val sideSwapPegAddress: String? = null,
) {
    /** Total network fee across both chains */
    val totalNetworkFee: Long get() = btcNetworkFee + liquidNetworkFee + providerMinerFee

    /** Total of all fees */
    val totalFee: Long get() = serviceFee + totalNetworkFee
}

data class LightningInvoiceCreation(
    val swapId: String,
    val invoice: String,
    val claimAddress: String,
    val amountSats: Long,
)

data class PendingLightningReceive(
    val swapId: String,
    val claimAddress: String,
    val label: String,
)

data class PendingLightningInvoice(
    val swapId: String,
    val invoice: String,
    val amountSats: Long,
    val createdAt: Long,
    val claimAddress: String = "",
    val label: String = "",
    val lastClaimAttemptAt: Long? = null,
    val isClaiming: Boolean = false,
)

data class LightningInvoiceLimits(
    val minimumSats: Long,
    val maximumSats: Long,
)

sealed interface PreparedLightningPaymentPlan

data class DirectLightningPayment(
    val address: String,
    val amountSats: Long,
    val uri: String,
) : PreparedLightningPaymentPlan

data class BoltzChainSwapOrder(
    val swapId: String,
    val lockupAddress: String,
    val receiveAddress: String,
    val refundAddress: String,
    val snapshot: String,
)

enum class BoltzChainSwapDraftState {
    CREATING,
    CREATED_UNREVIEWED,
    REVIEW_READY,
    FUNDING_BROADCAST,
    IN_PROGRESS,
    COMPLETED,
    REFUNDABLE,
    FAILED,
}

data class BoltzChainFundingPreview(
    val feeSats: Long = 0L,
    val txVBytes: Double = 0.0,
    val effectiveFeeRate: Double = 0.0,
    val verifiedRecipientAmountSats: Long = 0L,
    val error: String? = null,
)

data class BoltzChainSwapDraft(
    val draftId: String,
    val requestKey: String,
    val direction: SwapDirection,
    val sendAmount: Long,
    val orderAmountSats: Long = sendAmount,
    val maxOrderAmountVerified: Boolean = false,
    val usesMaxAmount: Boolean = false,
    val selectedFundingOutpoints: List<String> = emptyList(),
    val estimatedTerms: EstimatedSwapTerms,
    val fundingLiquidFeeRate: Double = 0.0,
    val bitcoinAddress: String,
    val liquidAddress: String? = null,
    val swapId: String? = null,
    val depositAddress: String? = null,
    val receiveAddress: String? = null,
    val refundAddress: String? = null,
    val state: BoltzChainSwapDraftState = BoltzChainSwapDraftState.CREATING,
    val fundingPreview: BoltzChainFundingPreview? = null,
    val fundingTxid: String? = null,
    val settlementTxid: String? = null,
    val lastError: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt,
    val reviewExpiresAt: Long = 0L,
    val snapshot: String? = null,
)

enum class PendingSwapPhase { REVIEW, FUNDING, IN_PROGRESS, FAILED }

data class EstimatedSwapTerms(
    val receiveAmount: Long,
    val serviceFee: Long,
    val btcNetworkFee: Long,
    val liquidNetworkFee: Long,
    val providerMinerFee: Long = 0,
    val btcFeeRate: Double = 0.0,
    val displayLiquidFeeRate: Double = 0.0,
    val fundingNetworkFee: Long = 0,
    val fundingFeeRate: Double = 0.0,
    val payoutNetworkFee: Long = 0,
    val payoutFeeRate: Double = 0.0,
    val minAmount: Long = 0,
    val maxAmount: Long = 0,
    val estimatedTime: String,
) {
    val totalNetworkFee: Long get() = btcNetworkFee + liquidNetworkFee + providerMinerFee
    val totalFee: Long get() = serviceFee + totalNetworkFee

    private val hasSeparatedBreakdown: Boolean
        get() = fundingNetworkFee > 0L || payoutNetworkFee > 0L || fundingFeeRate > 0.0 || payoutFeeRate > 0.0

    fun fundingNetworkFeeFor(direction: SwapDirection): Long {
        return if (hasSeparatedBreakdown) {
            fundingNetworkFee
        } else {
            when (direction) {
                SwapDirection.BTC_TO_LBTC -> btcNetworkFee
                SwapDirection.LBTC_TO_BTC -> liquidNetworkFee
            }
        }
    }

    fun fundingFeeRateFor(direction: SwapDirection): Double {
        return if (hasSeparatedBreakdown) {
            fundingFeeRate
        } else {
            when (direction) {
                SwapDirection.BTC_TO_LBTC -> btcFeeRate
                SwapDirection.LBTC_TO_BTC -> displayLiquidFeeRate
            }
        }
    }

    fun payoutNetworkFeeFor(direction: SwapDirection): Long {
        return if (hasSeparatedBreakdown) {
            payoutNetworkFee
        } else {
            when (direction) {
                SwapDirection.BTC_TO_LBTC -> liquidNetworkFee
                SwapDirection.LBTC_TO_BTC -> btcNetworkFee
            }
        }
    }

    fun payoutFeeRateFor(direction: SwapDirection): Double {
        return if (hasSeparatedBreakdown) {
            payoutFeeRate
        } else {
            when (direction) {
                SwapDirection.BTC_TO_LBTC -> displayLiquidFeeRate
                SwapDirection.LBTC_TO_BTC -> btcFeeRate
            }
        }
    }

    fun otherDeductionsFor(direction: SwapDirection): Long {
        return serviceFee + providerMinerFee + payoutNetworkFeeFor(direction)
    }

    fun totalSwapDeductionsFor(direction: SwapDirection): Long {
        return fundingNetworkFeeFor(direction) + otherDeductionsFor(direction)
    }
}

data class PendingSwapSession(
    val service: SwapService,
    val direction: SwapDirection,
    val sendAmount: Long,
    val requestKey: String? = null,
    val label: String? = null,
    val usesMaxAmount: Boolean = false,
    val selectedFundingOutpoints: List<String> = emptyList(),
    val estimatedTerms: EstimatedSwapTerms,
    val swapId: String,
    val depositAddress: String,
    val receiveAddress: String? = null,
    val refundAddress: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val reviewExpiresAt: Long = 0L,
    val phase: PendingSwapPhase = PendingSwapPhase.REVIEW,
    val status: String = "",
    val fundingLiquidFeeRate: Double = 0.0,
    val boltzOrderAmountSats: Long? = null,
    val boltzVerifiedRecipientAmountSats: Long? = null,
    val boltzMaxOrderAmountVerified: Boolean = false,
    val actualFundingFeeSats: Long? = null,
    val actualFundingTxVBytes: Double? = null,
    val fundingTxid: String? = null,
    val settlementTxid: String? = null,
    val boltzSnapshot: String? = null,
)

sealed class SwapState {
    data object Idle : SwapState()
    data object FetchingQuote : SwapState()
    data object PreparingReview : SwapState()
    data class ReviewReady(val swap: PendingSwapSession) : SwapState()
    data class InProgress(val swap: PendingSwapSession, val status: String) : SwapState() {
        val swapId: String get() = swap.swapId
    }
    data class Completed(
        val swapId: String,
        val settlementTxid: String? = null,
        val fundingTxid: String? = null,
    ) : SwapState()
    data class Failed(val error: String, val swap: PendingSwapSession? = null) : SwapState()
}

data class PendingLightningInvoiceSession(
    val swapId: String,
    val snapshot: String,
    val invoice: String,
    val amountSats: Long,
    val createdAt: Long = System.currentTimeMillis(),
)

enum class PendingLightningPaymentPhase { PREPARED, FUNDING, IN_PROGRESS, REFUNDING, FAILED }

enum class LightningPaymentBackend {
    LWK_PREPARE_PAY,
    BOLTZ_REST_SUBMARINE,
}

data class PendingLightningPaymentSession(
    val swapId: String,
    val backend: LightningPaymentBackend = LightningPaymentBackend.LWK_PREPARE_PAY,
    val requestKey: String,
    val paymentInput: String,
    val lockupAddress: String,
    val lockupAmountSats: Long,
    val refundAddress: String,
    val snapshot: String? = null,
    val requestedAmountSats: Long? = null,
    val resolvedPaymentInput: String? = null,
    val fetchedInvoice: String? = null,
    val refundPublicKey: String? = null,
    val paymentAmountSats: Long = 0L,
    val swapFeeSats: Long = 0L,
    val createdAt: Long = System.currentTimeMillis(),
    val phase: PendingLightningPaymentPhase = PendingLightningPaymentPhase.PREPARED,
    val status: String = "",
    val fundingTxid: String? = null,
    val boltzClaimPublicKey: String? = null,
    val timeoutBlockHeight: Int? = null,
    val swapTree: String? = null,
    val blindingKey: String? = null,
)

// ──────────────────────────────────────────────
// Boltz API response models
// ──────────────────────────────────────────────

data class BoltzPairInfo(
    val hash: String,
    val rate: Double,
    val limits: BoltzLimits,
    val fees: BoltzFees,
)

data class BoltzLimits(
    val minimal: Long,
    val maximal: Long,
    val maximalZeroConf: Long = 0,
)

/**
 * Boltz fee structure.
 * Chain swaps: minerFees split into server + user {lockup, claim}
 * Submarine: flat minerFees (single Long)
 * Reverse: minerFees {lockup, claim}
 */
data class BoltzFees(
    val percentage: Double,
    /** Server-side miner fee (chain swaps only — Boltz's lockup + claim) */
    val serverMinerFee: Long = 0,
    /** User lockup tx miner fee estimate */
    val userLockupFee: Long = 0,
    /** User claim tx miner fee estimate */
    val userClaimFee: Long = 0,
)

/** Response from POST /v2/swap/submarine (L-BTC → Lightning) */
data class BoltzSubmarineResponse(
    val id: String,
    val address: String,
    val expectedAmount: Long,
    val claimPublicKey: String,
    val timeoutBlockHeight: Int,
    val swapTree: String,
    val blindingKey: String? = null,
)

data class BoltzSwapUpdate(
    val id: String,
    val status: String,
    val transactionHex: String? = null,
    val transactionId: String? = null,
)

// ──────────────────────────────────────────────
// SideSwap API models
// ──────────────────────────────────────────────

data class SideSwapServerStatus(
    val minPegInAmount: Long,
    val minPegOutAmount: Long,
    val serverFeePercentPegIn: Double,
    val serverFeePercentPegOut: Double,
    /** Fastest BTC fee rate in sat/vB (from bitcoin_fee_rates, blocks=2) */
    val bitcoinFeeRate: Double,
    /** Raw SideSwap elements_fee_rate value from server_status */
    val elementsFeeRate: Double,
    /** vsize of the Bitcoin tx SideSwap creates for peg-out payouts */
    val pegOutBitcoinTxVsize: Int,
)

data class SideSwapPegOrder(
    val orderId: String,
    val pegAddress: String,
    val createdAt: Long,
)

data class SideSwapPegTx(
    val txHash: String,
    val amount: Long,
    val payout: Long?,
    val payoutTxid: String?,
    val status: String,
    val txState: String,
    val detectedConfs: Int?,
    val totalConfs: Int?,
)

data class SideSwapPegStatus(
    val orderId: String,
    val pegIn: Boolean,
    val addr: String,
    val addrRecv: String,
    val transactions: List<SideSwapPegTx>,
)

/**
 * Hot wallet balances and dynamic min amounts from SideSwap `subscribe_value` API.
 *
 * Wallet balances determine confirmation requirements:
 * - Peg-in: amount <= pegInWalletBalance → 2 BTC conf (~20 min), else 102 conf (~17 hrs)
 * - Peg-out: amount <= pegOutWalletBalance → 2 Liquid conf (~2 min), else ~20-30 min
 */
data class SideSwapWalletInfo(
    /** Dynamic minimum peg-in amount (sats). May differ from server_status. */
    val pegInMinAmount: Long = 0,
    /** L-BTC available in SideSwap hot wallet (sats). Determines peg-in speed. */
    val pegInWalletBalance: Long = 0,
    /** Dynamic minimum peg-out amount (sats). May differ from server_status. */
    val pegOutMinAmount: Long = 0,
    /** BTC available in SideSwap hot wallet (sats). Determines peg-out speed. */
    val pegOutWalletBalance: Long = 0,
)

// ──────────────────────────────────────────────
// Liquid Electrum server config
// ──────────────────────────────────────────────

data class LiquidElectrumConfig(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String = "",
    val url: String = "",
    val port: Int = 995,
    val useSsl: Boolean = true,
    val useTor: Boolean = false,
) {
    /** Strip protocol prefix for display */
    fun cleanUrl(): String = url
        .removePrefix("ssl://")
        .removePrefix("tcp://")
        .trim()

    fun displayName(): String = name.ifBlank { "${cleanUrl()}:$port" }

    fun isOnionAddress(): Boolean = cleanUrl().endsWith(".onion")
}

/** Lightning invoice generation state */
sealed class LightningInvoiceState {
    data object Idle : LightningInvoiceState()
    data object Generating : LightningInvoiceState()
    data class Ready(
        val invoice: String,
        val swapId: String,
        val amountSats: Long,
    ) : LightningInvoiceState()
    data class Claimed(val txid: String) : LightningInvoiceState()
    data class Failed(val error: String) : LightningInvoiceState()
}

/** State for the Liquid server list */
data class LiquidServersState(
    val servers: List<LiquidElectrumConfig> = emptyList(),
    val activeServerId: String? = null,
)
