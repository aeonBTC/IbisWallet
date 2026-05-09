package github.aeonbtc.ibiswallet.data.boltz

import github.aeonbtc.ibiswallet.data.model.BoltzPairInfo
import github.aeonbtc.ibiswallet.data.model.BoltzSubmarineResponse
import github.aeonbtc.ibiswallet.data.model.BoltzSwapUpdate
import github.aeonbtc.ibiswallet.data.model.LightningPaymentBackend
import github.aeonbtc.ibiswallet.data.model.SwapDirection
import github.aeonbtc.ibiswallet.data.remote.BoltzApiClient
import kotlinx.coroutines.delay

internal enum class BoltzFallbackFeature {
    PAIR_METADATA,
    BOLT12_INVOICE_FETCH,
    LEGACY_STATUS_UPDATES,
    LEGACY_SUBMARINE_SWAP,
}

internal data class BoltzProviderCapabilities(
    val usesLwkQuotes: Boolean = true,
    val usesLwkLightningInvoices: Boolean = true,
    val usesLwkChainSwaps: Boolean = true,
    val usesLwkPreparePay: Boolean = true,
    val supportsNativeBolt12Offers: Boolean = true,
    val supportsSnapshotStore: Boolean = true,
    val supportsTorTransportSelection: Boolean = true,
    val supportsCustomEndpointSelection: Boolean = false,
    val fallbackFeatures: Set<BoltzFallbackFeature> = setOf(
        BoltzFallbackFeature.PAIR_METADATA,
        BoltzFallbackFeature.BOLT12_INVOICE_FETCH,
        BoltzFallbackFeature.LEGACY_STATUS_UPDATES,
        BoltzFallbackFeature.LEGACY_SUBMARINE_SWAP,
    ),
) {
    val usesFallbackTransport: Boolean
        get() = fallbackFeatures.isNotEmpty()

    val transportSettingsAffectFallbackOnly: Boolean
        get() = usesLwkQuotes &&
            usesLwkLightningInvoices &&
            usesLwkChainSwaps &&
            usesLwkPreparePay &&
            usesFallbackTransport

    fun requires(feature: BoltzFallbackFeature): Boolean = feature in fallbackFeatures
}

internal enum class BoltzActivityMode {
    LWK_POLLING,
    FALLBACK_STATUS,
}

private val LightningPaymentBackend.requiresFallbackTransport: Boolean
    get() = this == LightningPaymentBackend.BOLTZ_REST_SUBMARINE

internal fun resolveBoltzActivityMode(
    backend: LightningPaymentBackend?,
    preferTransportFallback: Boolean,
): BoltzActivityMode {
    return if (preferTransportFallback || backend?.requiresFallbackTransport == true) {
        BoltzActivityMode.FALLBACK_STATUS
    } else {
        BoltzActivityMode.LWK_POLLING
    }
}

internal interface BoltzProviderPort {
    val capabilities: BoltzProviderCapabilities

    suspend fun getChainPairInfo(direction: SwapDirection): BoltzPairInfo

    suspend fun getSubmarinePairInfo(): BoltzPairInfo

    suspend fun getReversePairInfo(): BoltzPairInfo

    suspend fun fetchBolt12Invoice(offer: String, amountSats: Long): String

    suspend fun createLegacySubmarineSwap(
        invoice: String,
        refundPublicKey: String,
    ): BoltzSubmarineResponse

    suspend fun awaitSwapActivity(
        swapId: String,
        timeoutMs: Long,
        mode: BoltzActivityMode,
        previousUpdate: BoltzSwapUpdate? = null,
        pollStatus: (suspend () -> String?)? = null,
    ): BoltzSwapUpdate?

    fun close()
}

internal class HybridBoltzProvider(
    private val runtime: BoltzRuntime,
    private val client: BoltzApiClient,
    private val statusService: BoltzSwapStatusService,
    private val waitForLwkProgress: suspend (Long) -> Unit = { delay(it) },
) : BoltzProviderPort {
    override val capabilities: BoltzProviderCapabilities = BoltzProviderCapabilities()

    override suspend fun getChainPairInfo(direction: SwapDirection): BoltzPairInfo = runtime.getChainPairInfo(direction)

    override suspend fun getSubmarinePairInfo(): BoltzPairInfo = runtime.getSubmarinePairInfo()

    override suspend fun getReversePairInfo(): BoltzPairInfo = runtime.getReversePairInfo()

    override suspend fun fetchBolt12Invoice(offer: String, amountSats: Long): String {
        return client.fetchBolt12Invoice(
            offer = offer,
            amountSats = amountSats,
        ).invoice
    }

    override suspend fun createLegacySubmarineSwap(
        invoice: String,
        refundPublicKey: String,
    ): BoltzSubmarineResponse {
        return client.createSubmarineSwap(
            invoice = invoice,
            refundPublicKey = refundPublicKey,
        )
    }

    override suspend fun awaitSwapActivity(
        swapId: String,
        timeoutMs: Long,
        mode: BoltzActivityMode,
        previousUpdate: BoltzSwapUpdate?,
        pollStatus: (suspend () -> String?)?,
    ): BoltzSwapUpdate? {
        return when (mode) {
            BoltzActivityMode.LWK_POLLING -> {
                waitForLwkProgress(timeoutMs)
                null
            }
            BoltzActivityMode.FALLBACK_STATUS ->
                statusService.awaitSwapActivity(
                    swapId = swapId,
                    timeoutMs = timeoutMs,
                    previousUpdate = previousUpdate,
                    pollStatus = pollStatus,
                )
        }
    }

    override fun close() {
        runCatching { statusService.close() }
        runCatching { client.close() }
    }
}
