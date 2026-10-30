package github.aeonbtc.ibiswallet.data.model

import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode

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
 * Spark native token (BTKN) registry — mirrors [LiquidAsset] on Liquid.
 *
 * The canonical `btkn1…` identifier for USDT is resolved at runtime from
 * SDK `tokenBalances[].tokenMetadata` / `getTokensMetadata()` and is never
 * hardcoded: Breez publishes no constant for it. USDT detection keys off the
 * metadata ticker (`USDT`, case-insensitive) so the UX follows the token
 * wherever its identifier rotates.
 */
data class SparkToken(
    val tokenIdentifier: String,
    val ticker: String,
    val name: String,
    val decimals: Int,
) {
    val isUsdt: Boolean get() = ticker.equals(USDT_TICKER, ignoreCase = true)

    companion object {
        const val USDT_TICKER = "USDT"

        fun fromMetadata(
            tokenIdentifier: String,
            ticker: String,
            name: String,
            decimals: Int,
        ): SparkToken =
            SparkToken(
                tokenIdentifier = tokenIdentifier,
                ticker = ticker.ifBlank { tokenIdentifier.take(8) + "…" },
                name = name.ifBlank { "Unknown Token" },
                decimals = decimals.coerceIn(0, 18),
            )

        fun unknown(tokenIdentifier: String): SparkToken =
            SparkToken(
                tokenIdentifier = tokenIdentifier,
                ticker = tokenIdentifier.take(8) + "…",
                name = "Unknown Token",
                decimals = 6,
            )
    }
}

data class SparkTokenBalance(
    val token: SparkToken,
    val amount: BigInteger,
)

/**
 * Parses a user-typed token amount ("12.50") into base units for [decimals].
 * Returns null on invalid input. Never throws.
 */
fun parseSparkTokenAmountToBaseUnits(input: String, decimals: Int): BigInteger? {
    val trimmed = input.trim().replace(',', '.')
    if (trimmed.isEmpty()) return null
    if (!trimmed.matches(Regex("\\d+(\\.\\d*)?"))) return null
    return runCatching {
        val scaled = BigDecimal(trimmed).movePointRight(decimals).setScale(0, RoundingMode.DOWN)
        if (scaled.signum() <= 0) return null
        scaled.toBigIntegerExact()
    }.getOrNull()
}

/**
 * Formats token base units for display, Liquid-USDt style: always two
 * decimals plus ticker ("12.50 USDT"). No fiat conversion.
 */
fun formatSparkTokenAmount(amount: BigInteger, token: SparkToken): String {
    val divisor = BigDecimal.TEN.pow(token.decimals.coerceIn(0, 18))
    val value = BigDecimal(amount).divide(divisor, 2, RoundingMode.DOWN)
    return String.format(java.util.Locale.US, "%.2f %s", value.toDouble(), token.ticker)
}

/**
 * Input filter for the token amount field: digits with at most one dot and
 * at most [decimals] fractional digits.
 */
fun filterSparkTokenAmountInput(value: String, decimals: Int): String {
    val normalized = value.replace(',', '.')
    if (normalized.isEmpty()) return ""
    if (normalized.count { it == '.' } > 1) return value.dropLast(1)
    val parts = normalized.split('.')
    if (parts[0].length > 21 || parts[0].any { !it.isDigit() }) return value.dropLast(1)
    if (parts.size == 2) {
        if (parts[1].any { !it.isDigit() }) return value.dropLast(1)
        if (parts[1].length > decimals.coerceIn(0, 18)) return value.dropLast(1)
    }
    return normalized
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
    val tokenBalances: List<SparkTokenBalance> = emptyList(),
    val payments: List<SparkPayment> = emptyList(),
    val unclaimedDeposits: List<SparkUnclaimedDeposit> = emptyList(),
    val lightningAddress: String? = null,
    val isSyncing: Boolean = false,
    val lastSyncTimestamp: Long = 0,
    val error: String? = null,
) {
    fun balanceForToken(tokenIdentifier: String): BigInteger =
        tokenBalances.firstOrNull { it.token.tokenIdentifier == tokenIdentifier }?.amount ?: BigInteger.ZERO

    fun tokenForId(tokenIdentifier: String?): SparkToken? =
        tokenIdentifier?.let { id -> tokenBalances.firstOrNull { it.token.tokenIdentifier == id }?.token }

    val hasNonBtcTokens: Boolean get() = tokenBalances.any { it.amount.signum() > 0 }

    val usdtToken: SparkTokenBalance? get() = tokenBalances.firstOrNull { it.token.isUsdt }

    val hasUsdtTransactions: Boolean get() = payments.any { it.isToken && it.tokenTicker?.equals(SparkToken.USDT_TICKER, ignoreCase = true) == true }
}

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
    /** BTKN token identifier when this is a token payment; null for BTC. */
    val tokenIdentifier: String? = null,
    /** Token ticker from SDK metadata (e.g. "USDT"); null for BTC. */
    val tokenTicker: String? = null,
    /** Token decimals from SDK metadata; null for BTC. */
    val tokenDecimals: Int? = null,
    /** Authoritative token amount in base units; null for BTC. */
    val tokenAmount: String? = null,
    /** Authoritative token fee in base units; null for BTC. */
    val tokenFee: String? = null,
) {
    val isToken: Boolean get() = tokenIdentifier != null

    fun resolvedToken(): SparkToken? =
        tokenIdentifier?.let { id ->
            SparkToken.fromMetadata(
                tokenIdentifier = id,
                ticker = tokenTicker ?: "",
                name = tokenTicker ?: "",
                decimals = tokenDecimals ?: 6,
            )
        }

    fun tokenAmountBigInteger(): BigInteger? = tokenAmount?.let { runCatching { BigInteger(it) }.getOrNull() }

    fun tokenFeeBigInteger(): BigInteger? = tokenFee?.let { runCatching { BigInteger(it) }.getOrNull() }
}

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
    /**
     * Early-claim lifecycle from the SDK (Breez 0.26+). Claimed deposits stay
     * listed until the provider spends the output — CLAIMED means credited,
     * not awaiting action. Local pending deposits are always NONE.
     */
    val claimStatus: SparkClaimStatus = SparkClaimStatus.NONE,
    /** Per-deposit fee ceiling standing on the deposit, if expressed as a fixed amount. */
    val maxClaimFeeSats: Long? = null,
)

/**
 * Early-claim lifecycle of a deposit, mirroring the SDK `InstantClaimStatus`.
 */
enum class SparkClaimStatus {
    NONE,
    SUBMITTED,
    CLAIMED,
    DECLINED,
}

/**
 * Outcome of a `claim_deposit` call (Breez 0.26+). A declined claim is an
 * ordinary [Deferred] outcome — not an error — and any requested ceiling
 * stands on the deposit for later automatic attempts.
 */
sealed interface SparkClaimOutcome {
    /** Claimed at maturity and settled. */
    data class Settled(val paymentId: String?) : SparkClaimOutcome

    /** Claimed early and submitted; settles asynchronously, no payment yet. */
    data object Submitted : SparkClaimOutcome

    /** Nothing claimed yet; the SDK keeps trying on its own. */
    data class Deferred(
        val reason: SparkClaimDeferredReason,
        /** What the early claim would have cost (MAX_FEE_EXCEEDED only). */
        val requiredFeeSats: Long? = null,
    ) : SparkClaimOutcome
}

enum class SparkClaimDeferredReason {
    MAX_FEE_EXCEEDED,
    NO_EARLY_CLAIM,
    PROVIDER_DECLINED,
    IN_PROGRESS,
}

/**
 * Claim quote for a pending deposit. [isEarlyOffer] distinguishes the instant
 * (or expedited) spread quote from the [matureFeeSats] standard-claim fallback:
 * claiming under the mature terms on an immature deposit records the ceiling
 * and settles at maturity. Null when the provider quoted nothing usable.
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
    /** False when priced from the standard (maturity) quote, not an early offer. */
    val isEarlyOffer: Boolean = true,
    /** Depth the early offer becomes claimable at (early offers only). */
    val instantConfirmationsRequired: Long? = null,
    /** Standard-claim fee at maturity, when quoted. */
    val matureFeeSats: Long? = null,
    /** Depth the standard claim becomes available at, when quoted. */
    val matureConfirmationsRequired: Long? = null,
    /** True when the standard-claim fee is estimated, not quoted. */
    val matureIsEstimate: Boolean = false,
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
        /** BTKN token identifier when previewing a token send; null for BTC. */
        val tokenIdentifier: String? = null,
        val tokenTicker: String? = null,
        val tokenDecimals: Int? = null,
        /** Authoritative token amount/fee in base units; null for BTC. */
        val tokenAmount: String? = null,
        val tokenFee: String? = null,
    ) : SparkSendState {
        val isToken: Boolean get() = tokenIdentifier != null
    }

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
        /** BTKN token identifier when the invoice is token-locked; null for BTC. */
        val tokenIdentifier: String? = null,
    ) : SparkReceiveState
    data class Paid(
        val kind: SparkReceiveKind,
        val paymentId: String,
        val amountSats: Long,
        val paymentRequest: String? = null,
        /** BTKN token identifier when the settled payment is a token; null for BTC. */
        val tokenIdentifier: String? = null,
    ) : SparkReceiveState
    data class Error(val message: String) : SparkReceiveState
}

sealed class SparkEvent {
    data class PaymentReceived(
        val paymentId: String,
        val amountSats: Long,
        val kind: SparkReceiveKind?,
        val tokenIdentifier: String? = null,
    ) : SparkEvent()
}
