package github.aeonbtc.ibiswallet.data.repository

import android.content.Context
import android.util.Log
import fr.acinq.lightning.wire.OfferTypes
import github.aeonbtc.ibiswallet.BuildConfig
import github.aeonbtc.ibiswallet.data.boltz.BoltzActivityMode
import github.aeonbtc.ibiswallet.data.boltz.BoltzChainSwapWorkflow
import github.aeonbtc.ibiswallet.data.boltz.BoltzProviderPort
import github.aeonbtc.ibiswallet.data.boltz.BoltzRuntime
import github.aeonbtc.ibiswallet.data.boltz.BoltzSwapStatusService
import github.aeonbtc.ibiswallet.data.boltz.BoltzTraceContext
import github.aeonbtc.ibiswallet.data.boltz.BoltzTraceLevel
import github.aeonbtc.ibiswallet.data.boltz.BullBoltzBehavior
import github.aeonbtc.ibiswallet.data.boltz.HybridBoltzProvider
import github.aeonbtc.ibiswallet.data.boltz.boltzElapsedMs
import github.aeonbtc.ibiswallet.data.boltz.boltzTraceStart
import github.aeonbtc.ibiswallet.data.boltz.buildBoltzQuoteFromPair
import github.aeonbtc.ibiswallet.data.boltz.logBoltzTrace
import github.aeonbtc.ibiswallet.data.local.ElectrumCache
import github.aeonbtc.ibiswallet.data.local.SecureStorage
import github.aeonbtc.ibiswallet.data.model.BoltzChainFundingPreview
import github.aeonbtc.ibiswallet.data.model.BoltzChainSwapDraft
import github.aeonbtc.ibiswallet.data.model.BoltzChainSwapDraftState
import github.aeonbtc.ibiswallet.data.model.BoltzChainSwapOrder
import github.aeonbtc.ibiswallet.data.model.BoltzPairInfo
import github.aeonbtc.ibiswallet.data.model.BoltzSwapUpdate
import github.aeonbtc.ibiswallet.data.model.DirectLightningPayment
import github.aeonbtc.ibiswallet.data.model.EstimatedSwapTerms
import github.aeonbtc.ibiswallet.data.model.KeychainType
import github.aeonbtc.ibiswallet.data.model.LightningInvoiceCreation
import github.aeonbtc.ibiswallet.data.model.LightningInvoiceLimits
import github.aeonbtc.ibiswallet.data.model.LightningPaymentBackend
import github.aeonbtc.ibiswallet.data.model.LightningPaymentExecutionPlan
import github.aeonbtc.ibiswallet.data.model.LiquidElectrumConfig
import github.aeonbtc.ibiswallet.data.model.LiquidSendKind
import github.aeonbtc.ibiswallet.data.model.LiquidSendPreview
import github.aeonbtc.ibiswallet.data.model.LiquidSwapDetails
import github.aeonbtc.ibiswallet.data.model.LiquidSwapTxRole
import github.aeonbtc.ibiswallet.data.model.LiquidTransaction
import github.aeonbtc.ibiswallet.data.model.LiquidTxSource
import github.aeonbtc.ibiswallet.data.model.LiquidTxType
import github.aeonbtc.ibiswallet.data.model.LiquidWalletState
import github.aeonbtc.ibiswallet.data.model.PendingLightningInvoiceSession
import github.aeonbtc.ibiswallet.data.model.PendingLightningPaymentPhase
import github.aeonbtc.ibiswallet.data.model.PendingLightningPaymentSession
import github.aeonbtc.ibiswallet.data.model.PendingLightningReceive
import github.aeonbtc.ibiswallet.data.model.PendingSwapPhase
import github.aeonbtc.ibiswallet.data.model.PendingSwapSession
import github.aeonbtc.ibiswallet.data.model.Recipient
import github.aeonbtc.ibiswallet.data.model.SwapDirection
import github.aeonbtc.ibiswallet.data.model.SwapLimits
import github.aeonbtc.ibiswallet.data.model.SwapQuote
import github.aeonbtc.ibiswallet.data.model.SwapService
import github.aeonbtc.ibiswallet.data.model.UtxoInfo
import github.aeonbtc.ibiswallet.data.model.WalletAddress
import github.aeonbtc.ibiswallet.data.remote.Bip353Resolver
import github.aeonbtc.ibiswallet.data.remote.Bolt12OfferVerifier
import github.aeonbtc.ibiswallet.data.remote.BoltzApiClient
import github.aeonbtc.ibiswallet.data.remote.SideSwapApiClient
import github.aeonbtc.ibiswallet.tor.CachingElectrumProxy
import github.aeonbtc.ibiswallet.tor.ElectrumNotification
import github.aeonbtc.ibiswallet.tor.TorManager
import github.aeonbtc.ibiswallet.util.BitcoinUtils
import github.aeonbtc.ibiswallet.util.Blech32Util
import github.aeonbtc.ibiswallet.util.ElectrumSeedUtil
import github.aeonbtc.ibiswallet.util.TofuTrustManager
import github.aeonbtc.ibiswallet.util.findMaxExactSendAmount
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import lwk.Address
import lwk.AnyClient
import lwk.BitcoinAddress
import lwk.BoltzSession
import lwk.BoltzSessionBuilder
import lwk.Chain
import lwk.ElectrumClient
import lwk.InvoiceResponse
import lwk.LightningPayment
import lwk.LockupResponse
import lwk.LwkException
import lwk.Mnemonic
import lwk.Network
import lwk.Payment
import lwk.PaymentKind
import lwk.PaymentState
import lwk.PreparePayResponse
import lwk.Pset
import lwk.Signer
import lwk.SwapAsset
import lwk.Update
import lwk.Wollet
import lwk.WolletDescriptor
import okhttp3.OkHttpClient
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.math.BigDecimal
import java.net.SocketTimeoutException
import java.net.URLDecoder
import java.security.MessageDigest
import java.util.Locale

/**
 * Repository for all Liquid Network wallet operations.
 *
 * Manages:
 * - LWK Wollet (watch-only wallet) lifecycle: init, load, sync, unload
 * - LWK Signer for transaction signing
 * - LWK ElectrumClient for blockchain data
 * - LWK BoltzSession for built-in Lightning swaps
 * - Boltz API integration for Lightning swaps (fallback)
 * - SideSwap API integration for BTC<>L-BTC pegs
 *
 * Architecture follows LWK's Signer/Wollet/Client pattern:
 * - Signer: holds mnemonic, signs PSETs (in-memory only, never persisted)
 * - Wollet: watch-only wallet from CT descriptor, tracks balance/txs
 * - Client: Electrum connection for blockchain sync
 *
 * The CT descriptor uses SLIP-77 blinding + wpkh (Liquid native segwit)
 * with coin type 1776 (Liquid mainnet per SLIP-0044).
 */
class LiquidRepository(
    private val context: Context,
    private val secureStorage: SecureStorage,
    private val torManager: TorManager,
) {
    companion object {
        private const val TAG = "LiquidRepository"
        private const val BOLTZ_LOG_TAG = "BoltzDebug"
        private const val LWK_DB_DIR = "lwk"
        private const val ADDRESS_PEEK_AHEAD = 60
        private const val ADDRESS_INTERNAL_PEEK_AHEAD = 60
        private const val NOTIFICATION_DEBOUNCE_MS = 1_000L

        // Typical BTC tx vsize for a simple 1-in-1-out + change (~140 vB)
        private const val TYPICAL_BTC_TX_VSIZE = 140
        private const val TYPICAL_LIQUID_TX_VSIZE = 200
        private const val SIDESWAP_PEG_IN_LIQUID_TX_VSIZE = 2860
        private const val DEFAULT_LIQUID_SWAP_FEE_RATE = 0.1
        private const val MAX_LBTC_MAX_FEE_ABSORB_SATS = 3L
        private const val BOLTZ_METADATA_CACHE_MS = 30_000L
        private const val BOLTZ_LIGHTNING_RESOLUTION_CACHE_MS = 120_000L
        private const val BOLTZ_OPERATION_LOCK_LOG_THRESHOLD_MS = 250L
        private const val BOLTZ_SESSION_TIMEOUT_CLEARNET_SECS = 5u
        private const val BOLTZ_SESSION_TIMEOUT_TOR_SECS = 12u
        private const val BOLTZ_RESCUE_MNEMONIC_WORD_COUNT = 12u
        private const val BOLTZ_RESCUE_MNEMONIC_BIP85_INDEX = 26_589u

        // Default Liquid Electrum servers
        val DEFAULT_SERVERS = listOf(
            LiquidElectrumConfig(
                id = "blockstream_liquid",
                name = "Blockstream Liquid",
                url = "blockstream.info",
                port = 995,
                useSsl = true,
                useTor = false,
            ),
            LiquidElectrumConfig(
                id = "bullbitcoin_liquid",
                name = "Bull Bitcoin Liquid",
                url = "les.bullbitcoin.com",
                port = 995,
                useSsl = true,
                useTor = false,
            ),
        )
    }

    // ── LWK typed instances ──
    private var lwkNetwork: Network? = null
    private var lwkSigner: Signer? = null
    private var lwkWollet: Wollet? = null
    private var lwkClient: ElectrumClient? = null
    private var lwkBoltzSession: BoltzSession? = null
    private var lwkBoltzAnyClient: AnyClient? = null
    private var liquidElectrumProxy: CachingElectrumProxy? = null
    private val invoiceResponses = mutableMapOf<String, InvoiceResponse>()
    private val preparePayResponses = mutableMapOf<String, PreparePayResponse>()
    private val lockupResponses = mutableMapOf<String, LockupResponse>()
    private val boltzChainSwapDrafts = mutableMapOf<String, BoltzChainSwapDraft>()

    private val _liquidState = MutableStateFlow(LiquidWalletState())
    val liquidState: StateFlow<LiquidWalletState> = _liquidState

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected

    private val _loadedWalletId = MutableStateFlow<String?>(null)
    val loadedWalletId: StateFlow<String?> = _loadedWalletId

    private val _connectionEvents = MutableSharedFlow<ConnectionEvent>(extraBufferCapacity = 1)
    val connectionEvents: SharedFlow<ConnectionEvent> = _connectionEvents.asSharedFlow()

    private val connectionMutex = Mutex()
    private val syncMutex = Mutex()
    private val boltzSessionMutex = Mutex()
    private val boltzOperationMutex = Mutex()
    private val electrumCache = ElectrumCache(context)
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var boltzSessionKeepAliveUntilMs = 0L
    private var currentWalletId: String? = null
    private var notificationCollectorJob: Job? = null
    private val subscribedScriptHashes = mutableSetOf<String>()
    private val scriptHashStatusCache = mutableMapOf<String, String?>()
    private val lightningResolutionCache = mutableMapOf<String, CachedLightningResolution>()

    // API clients — initialized lazily (fallback for custom Boltz REST + SideSwap)
    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .build()
    }

    val boltzClient: BoltzApiClient by lazy {
        BoltzApiClient(
            baseHttpClient = okHttpClient,
            useTor = { secureStorage.getBoltzApiSource() == SecureStorage.BOLTZ_API_TOR && torManager.isReady() },
        )
    }
    private val bip353Resolver: Bip353Resolver by lazy {
        Bip353Resolver(
            baseHttpClient = okHttpClient,
            useTor = { secureStorage.getBoltzApiSource() == SecureStorage.BOLTZ_API_TOR && torManager.isReady() },
        )
    }
    private val bolt12OfferVerifier = Bolt12OfferVerifier()

    private val boltzRuntime: BoltzRuntime by lazy {
        BoltzRuntime(
            metadataTtlMs = BOLTZ_METADATA_CACHE_MS,
            ensureTorReady = { ensureBoltzTorReadyIfNeeded() },
            ensureSession = { ensureBoltzSession() },
            fetchReversePairInfo = { boltzClient.getReversePairInfo("BTC", "L-BTC") },
            fetchSubmarinePairInfo = { boltzClient.getSubmarinePairInfo("L-BTC", "BTC") },
            fetchChainPairInfo = { direction ->
                when (direction) {
                    SwapDirection.BTC_TO_LBTC -> boltzClient.getChainPairInfo("BTC", "L-BTC")
                    SwapDirection.LBTC_TO_BTC -> boltzClient.getChainPairInfo("L-BTC", "BTC")
                }
            },
        )
    }

    private val boltzStatusService: BoltzSwapStatusService by lazy {
        BoltzSwapStatusService(boltzClient)
    }
    private val boltzProvider: BoltzProviderPort by lazy {
        HybridBoltzProvider(
            runtime = boltzRuntime,
            client = boltzClient,
            statusService = boltzStatusService,
        )
    }

    private val boltzChainWorkflow = BoltzChainSwapWorkflow()

    val sideSwapClient: SideSwapApiClient by lazy {
        SideSwapApiClient(
            httpClient = okHttpClient,
            useTor = { secureStorage.getSideSwapApiSource() == SecureStorage.SIDESWAP_API_TOR && torManager.isReady() },
        )
    }

    fun getBoltzApiSource(): String = secureStorage.getBoltzApiSource()

    fun setBoltzApiSource(source: String) {
        secureStorage.setBoltzApiSource(source)
        repositoryScope.launch {
            boltzRuntime.invalidate()
        }
    }

    fun isBoltzEnabled(): Boolean = secureStorage.getBoltzApiSource() != SecureStorage.BOLTZ_API_DISABLED

    fun getLiquidExplorer(): String = secureStorage.getLiquidExplorer()

    fun setLiquidExplorer(explorer: String) {
        secureStorage.setLiquidExplorer(explorer)
    }

    fun getCustomLiquidExplorerUrl(): String = secureStorage.getCustomLiquidExplorerUrl().orEmpty()

    fun setCustomLiquidExplorerUrl(url: String) {
        secureStorage.setCustomLiquidExplorerUrl(url)
    }

    fun getLiquidExplorerUrl(): String = secureStorage.getLiquidExplorerUrl()

    fun getSideSwapApiSource(): String = secureStorage.getSideSwapApiSource()

    fun setSideSwapApiSource(source: String) {
        secureStorage.setSideSwapApiSource(source)
    }

    fun getPreferredSwapService(): SwapService = secureStorage.getPreferredSwapService()

    fun setPreferredSwapService(service: SwapService) {
        secureStorage.setPreferredSwapService(service)
    }

    fun getBoltzRescueMnemonic(): String? {
        return runCatching {
            val signer = ensureSigner()
            val rescueMnemonic = deriveBoltzRescueMnemonic(signer).toString()
            val sessionRescueMnemonic =
                runCatching { lwkBoltzSession?.rescueFile() }
                    .getOrNull()
                    ?.let(::extractBoltzRescueMnemonic)
            sessionRescueMnemonic ?: rescueMnemonic
        }.getOrNull()
    }

    fun isSideSwapEnabled(): Boolean = secureStorage.getSideSwapApiSource() != SecureStorage.SIDESWAP_API_DISABLED

    fun canPrewarmBoltzSession(): Boolean = getBitcoinElectrumUrl() != null

    // ════════════════════════════════════════════
    // Wallet Lifecycle
    // ════════════════════════════════════════════

    /**
     * Initialize a Liquid wallet from an existing BIP39 mnemonic.
     * Creates the LWK Signer, derives the CT descriptor, creates the Wollet.
     */
    suspend fun initializeWallet(walletId: String, mnemonic: String) = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Initializing Liquid wallet for $walletId")
            _liquidState.value = LiquidWalletState()

            val network = Network.mainnet()
            lwkNetwork = network

            val lwkMnemonic = Mnemonic(mnemonic)
            val signer = Signer(lwkMnemonic, network)
            lwkSigner = signer

            // Derive the WPKH + SLIP-77 CT descriptor
            val descriptor: WolletDescriptor = signer.wpkhSlip77Descriptor()
            val descriptorStr = descriptor.toString()

            // Persist the CT descriptor (encrypted) for reload without mnemonic
            secureStorage.setLiquidDescriptor(walletId, descriptorStr)

            // Create the watch-only wallet with on-disk persistence
            val dbPath = getLwkDbPath(walletId)
            val wollet = Wollet(network, descriptor, dbPath)
            lwkWollet = wollet
            clearBoltzChainSwapDraftCache()
            currentWalletId = walletId
            _loadedWalletId.value = walletId

            refreshLiquidWalletState(
                wollet = wollet,
                updateLastSyncTimestamp = false,
            )

            Log.d(TAG, "Liquid wallet initialized successfully")
        } catch (e: LwkException) {
            logError("LWK error initializing Liquid wallet", e)
            _liquidState.value = _liquidState.value.copy(error = "Failed to init Liquid wallet: ${e.message}")
        } catch (e: Exception) {
            logError("Failed to initialize Liquid wallet", e)
            _liquidState.value = _liquidState.value.copy(error = "Failed to init Liquid wallet: ${e.message}")
        }
    }

    /**
     * Load an existing Liquid wallet from persisted CT descriptor.
     * Used on app restart when the wallet was previously initialized.
     */
    suspend fun loadWallet(walletId: String, mnemonic: String? = null) = withContext(Dispatchers.IO) {
        try {
            _liquidState.value = LiquidWalletState()

            val descriptorStr = secureStorage.getLiquidDescriptor(walletId)
            if (descriptorStr == null) {
                val resolvedMnemonic = mnemonic ?: secureStorage.getMnemonic(walletId)
                if (resolvedMnemonic != null) {
                    initializeWallet(walletId, resolvedMnemonic)
                    return@withContext
                }
                _liquidState.value = _liquidState.value.copy(error = "No Liquid descriptor found")
                return@withContext
            }

            val network = Network.mainnet()
            lwkNetwork = network

            val descriptor = WolletDescriptor(descriptorStr)
            val dbPath = getLwkDbPath(walletId)
            lwkWollet = Wollet(network, descriptor, dbPath)

            clearBoltzChainSwapDraftCache()
            currentWalletId = walletId
            _loadedWalletId.value = walletId
            refreshLiquidWalletState(
                wollet = lwkWollet ?: return@withContext,
                updateLastSyncTimestamp = false,
            )

            Log.d(TAG, "Liquid wallet loaded from descriptor")
        } catch (e: LwkException) {
            logError("LWK error loading Liquid wallet", e)
            _liquidState.value = _liquidState.value.copy(error = "Failed to load: ${e.message}")
        } catch (e: Exception) {
            logError("Failed to load Liquid wallet", e)
            _liquidState.value = _liquidState.value.copy(error = "Failed to load: ${e.message}")
        }
    }

    /** Unload the current Liquid wallet, optionally preserving the Electrum connection. */
    suspend fun unloadWallet(preserveConnection: Boolean = false) =
        withContext(Dispatchers.IO) {
            connectionMutex.withLock {
                stopNotificationCollector()
                subscribedScriptHashes.clear()
                scriptHashStatusCache.clear()
                withBoltzOperationLock(operation = "unloadWallet") {
                    resetBoltzSessionStateLocked()
                }
                try {
                    lwkWollet?.destroy()
                } catch (_: Exception) {}
                try {
                    lwkSigner?.destroy()
                } catch (_: Exception) {}
                try {
                    lwkNetwork?.destroy()
                } catch (_: Exception) {}

                lwkBoltzSession = null
                clearBoltzChainSwapDraftCache()
                lwkWollet = null
                lwkSigner = null
                lwkNetwork = null
                currentWalletId = null
                _loadedWalletId.value = null
                _liquidState.value = LiquidWalletState()
                if (!preserveConnection) {
                    liquidElectrumProxy?.stop()
                    liquidElectrumProxy = null
                    try {
                        lwkClient?.destroy()
                    } catch (_: Exception) {}
                    lwkClient = null
                    _isConnected.value = false
                }
                Log.d(TAG, "Liquid wallet unloaded (preserveConnection=$preserveConnection)")
            }
        }

    suspend fun deleteWalletDatabase(walletId: String) =
        withContext(Dispatchers.IO) {
            if (currentWalletId == walletId) {
                unloadWallet()
            }
            if (!deleteWalletDatabaseFiles(walletId)) {
                Log.e(TAG, "Failed to delete Liquid wallet database for $walletId")
            }
        }

    fun isWalletLoaded(walletId: String): Boolean = currentWalletId == walletId && lwkWollet != null

    fun getPendingSwapSessions(): List<PendingSwapSession> {
        val walletId = currentWalletId ?: return emptyList()
        return secureStorage.getPendingSwaps(walletId)
    }

    fun getPendingSwapSession(swapId: String): PendingSwapSession? {
        val walletId = currentWalletId ?: return null
        return secureStorage.getPendingSwap(walletId, swapId)
    }

    fun savePendingSwapSession(session: PendingSwapSession) {
        val walletId = currentWalletId ?: return
        secureStorage.savePendingSwap(walletId, session)
    }

    fun deletePendingSwapSession(swapId: String? = null) {
        val walletId = currentWalletId ?: return
        secureStorage.deletePendingSwap(walletId, swapId)
    }

    fun getBoltzChainSwapDraftByRequestKey(requestKey: String): BoltzChainSwapDraft? {
        return synchronized(boltzChainSwapDrafts) {
            boltzChainSwapDrafts.values.firstOrNull { it.requestKey == requestKey }
        }
    }

    fun getBoltzChainSwapDraftBySwapId(swapId: String): BoltzChainSwapDraft? {
        return synchronized(boltzChainSwapDrafts) {
            boltzChainSwapDrafts.values.firstOrNull { it.swapId == swapId }
        }
    }

    fun saveBoltzChainSwapDraft(draft: BoltzChainSwapDraft) {
        synchronized(boltzChainSwapDrafts) {
            boltzChainSwapDrafts[draft.draftId] = draft
        }
    }

    fun deleteBoltzChainSwapDraft(draftId: String) {
        synchronized(boltzChainSwapDrafts) {
            boltzChainSwapDrafts.remove(draftId)
        }
    }

    fun deleteBoltzChainSwapDraftBySwapId(swapId: String?) {
        if (swapId.isNullOrBlank()) return
        getBoltzChainSwapDraftBySwapId(swapId)?.let { deleteBoltzChainSwapDraft(it.draftId) }
    }

    private fun clearBoltzChainSwapDraftCache() {
        synchronized(boltzChainSwapDrafts) {
            boltzChainSwapDrafts.clear()
        }
    }

    fun getPendingLightningInvoiceSession(): PendingLightningInvoiceSession? {
        val walletId = currentWalletId ?: return null
        return secureStorage.getPendingLightningInvoiceSession(walletId)
    }

    suspend fun restorePendingLightningInvoice(): LightningInvoiceCreation? = withContext(Dispatchers.IO) {
        val walletId = currentWalletId ?: return@withContext null
        val session = getPendingLightningInvoiceSession() ?: return@withContext null
        val pendingReceive = secureStorage.getPendingLightningReceive(walletId, session.swapId) ?: return@withContext null
        val response = ensureLightningInvoiceResponse(session.swapId)
        LightningInvoiceCreation(
            swapId = session.swapId,
            invoice = response.bolt11Invoice().toString(),
            claimAddress = pendingReceive.claimAddress,
            amountSats = session.amountSats,
        )
    }

    fun getPendingLightningPaymentSession(): PendingLightningPaymentSession? {
        val walletId = currentWalletId ?: return null
        return secureStorage.getPendingLightningPaymentSession(walletId)
    }

    fun savePendingLightningPaymentSession(session: PendingLightningPaymentSession) {
        val walletId = currentWalletId ?: return
        secureStorage.savePendingLightningPaymentSession(walletId, session)
    }

    fun deletePendingLightningPaymentSession() {
        val walletId = currentWalletId ?: return
        secureStorage.deletePendingLightningPaymentSession(walletId)
    }

    fun isUserDisconnected(): Boolean = secureStorage.isLiquidUserDisconnected()

    fun setUserDisconnected(disconnected: Boolean) = secureStorage.setLiquidUserDisconnected(disconnected)

    private fun ensureSigner(): Signer {
        lwkSigner?.let { return it }

        val walletId = currentWalletId ?: throw Exception("Wallet not loaded")
        val network = lwkNetwork ?: throw Exception("Liquid network not initialized")
        val mnemonic = secureStorage.getMnemonic(walletId)
            ?: throw Exception("Signer not available (read-only mode)")

        return try {
            Signer(Mnemonic(mnemonic), network).also { signer ->
                lwkSigner = signer
            }
        } catch (e: Exception) {
            throw Exception("Signer not available (read-only mode)", e)
        }
    }

    // ════════════════════════════════════════════
    // Electrum Connection
    // ════════════════════════════════════════════

    /**
     * Connect to a Liquid Electrum server.
     * If the server is .onion, starts Tor first.
     */
    suspend fun connectToElectrum(config: LiquidElectrumConfig) = withContext(Dispatchers.IO) {
        connectionMutex.withLock {
            try {
                Log.d(TAG, "Connecting to Liquid Electrum: ${config.displayName()}")
                stopNotificationCollector()
                subscribedScriptHashes.clear()
                scriptHashStatusCache.clear()

                val useTor = shouldUseLiquidTor() || config.isOnionAddress() || config.useTor
                if (useTor && !torManager.isReady()) {
                    torManager.start()
                    torManager.awaitReady(60_000)
                    delay(500)
                }

                val maxAttempts = if (useTor) 2 else 1
                var lastError: Exception? = null
                val cleanHost = config.cleanUrl()

                repeat(maxAttempts) { attempt ->
                    var newProxy: CachingElectrumProxy? = null
                    var newClient: ElectrumClient? = null
                    try {
                        try {
                            lwkClient?.destroy()
                        } catch (_: Exception) {}
                        lwkClient = null
                        liquidElectrumProxy?.stop()
                        liquidElectrumProxy = null
                        _isConnected.value = false

                        newProxy = if (config.useSsl) {
                            val preConnectedSocket = verifyCertificateProbe(cleanHost, config.port, useTor)
                            val stored = secureStorage.getServerCertFingerprint(cleanHost, config.port)
                            val bridgeTrustManager = TofuTrustManager(cleanHost, config.port, stored, isOnionHost = false)
                            CachingElectrumProxy(
                                targetHost = cleanHost,
                                targetPort = config.port,
                                useSsl = true,
                                useTorProxy = useTor,
                                connectionTimeoutMs = if (useTor) 30_000 else 15_000,
                                soTimeoutMs = if (useTor) 30_000 else 15_000,
                                sslTrustManager = bridgeTrustManager,
                                cache = electrumCache,
                            ).also {
                                it.setPreConnectedSocket(preConnectedSocket)
                            }
                        } else {
                            CachingElectrumProxy(
                                targetHost = cleanHost,
                                targetPort = config.port,
                                useSsl = false,
                                useTorProxy = useTor,
                                connectionTimeoutMs = if (useTor) 30_000 else 15_000,
                                soTimeoutMs = if (useTor) 30_000 else 15_000,
                                cache = electrumCache,
                            )
                        }
                        val localPort = newProxy.start()
                        val client = ElectrumClient.fromUrl("tcp://127.0.0.1:$localPort")
                        newClient = client

                        // Prove the LWK client actually consumed the local proxy before
                        // we publish the connection as active.
                        client.tip()
                        currentCoroutineContext().ensureActive()

                        liquidElectrumProxy = newProxy
                        lwkClient = client
                        _isConnected.value = true

                        Log.d(TAG, "Connected to Liquid Electrum server")
                        repositoryScope.launch {
                            electrumCache.pruneStaleUnconfirmed()
                        }
                        return@withContext
                    } catch (e: CancellationException) {
                        if (lwkClient === newClient) {
                            try {
                                lwkClient?.destroy()
                            } catch (_: Exception) {}
                            lwkClient = null
                        }
                        if (liquidElectrumProxy === newProxy) liquidElectrumProxy = null
                        newProxy?.stop()
                        _isConnected.value = false
                        throw e
                    } catch (e: Exception) {
                        lastError = e
                        if (lwkClient === newClient) {
                            try {
                                lwkClient?.destroy()
                            } catch (_: Exception) {}
                            lwkClient = null
                        }
                        if (liquidElectrumProxy === newProxy) liquidElectrumProxy = null
                        newProxy?.stop()
                        _isConnected.value = false
                        if (useTor && attempt < maxAttempts - 1) {
                            delay(1_500)
                        }
                    }
                }

                throw lastError ?: Exception("Liquid Electrum connection failed")
            } catch (e: LwkException) {
                logError("LWK error connecting to Liquid Electrum", e)
                _isConnected.value = false
                throw Exception("Connection failed: ${e.message}")
            } catch (e: Exception) {
                logError("Failed to connect to Liquid Electrum", e)
                _isConnected.value = false
                throw e
            }
        }
    }

    private fun verifyCertificateProbe(
        host: String,
        port: Int,
        useTor: Boolean,
    ): java.net.Socket {
        val storedFingerprint = secureStorage.getServerCertFingerprint(host, port)
        val trustManager = TofuTrustManager(host, port, storedFingerprint, isOnionHost = false)
        val sslFactory = TofuTrustManager.createSSLSocketFactory(trustManager)

        var rawSocket: java.net.Socket? = null
        var sslSocket: java.net.Socket? = null
        var success = false
        try {
            rawSocket = if (useTor) {
                val proxy = java.net.Proxy(
                    java.net.Proxy.Type.SOCKS,
                    java.net.InetSocketAddress("127.0.0.1", 9050),
                )
                java.net.Socket(proxy).also {
                    it.soTimeout = 30_000
                    it.connect(
                        java.net.InetSocketAddress.createUnresolved(host, port),
                        30_000,
                    )
                }
            } else {
                java.net.Socket().also {
                    it.soTimeout = 15_000
                    it.connect(java.net.InetSocketAddress(host, port), 15_000)
                }
            }

            sslSocket = sslFactory.createSocket(rawSocket, host, port, true)
            (sslSocket as javax.net.ssl.SSLSocket).startHandshake()
            if (BuildConfig.DEBUG) Log.d(TAG, "SSL probe OK for $host:$port")
            success = true
            return sslSocket
        } finally {
            if (!success) {
                try {
                    sslSocket?.close()
                } catch (_: Exception) {}
                try {
                    rawSocket?.close()
                } catch (_: Exception) {}
            }
        }
    }

    fun acceptServerCertificate(
        host: String,
        port: Int,
        fingerprint: String,
    ) {
        secureStorage.saveServerCertFingerprint(host, port, fingerprint)
    }

    suspend fun disconnect() =
        withContext(Dispatchers.IO) {
            connectionMutex.withLock {
                stopNotificationCollector()
                subscribedScriptHashes.clear()
                scriptHashStatusCache.clear()
                liquidElectrumProxy?.stop()
                liquidElectrumProxy = null
                withBoltzOperationLock(operation = "disconnect") {
                    resetBoltzSessionStateLocked()
                }
                try {
                    lwkClient?.destroy()
                } catch (_: Exception) {}
                lwkBoltzSession = null
                lwkClient = null
                _isConnected.value = false
            }
        }

    fun pingServer(): Boolean {
        return liquidElectrumProxy?.ping() ?: false
    }

    fun getServerBlockHeight(): UInt? {
        val client = lwkClient ?: return null
        return try {
            client.tip().height()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to query Liquid tip height: ${e.message}")
            null
        }
    }

    private fun refreshLiquidWalletState(
        wollet: Wollet,
        updateLastSyncTimestamp: Boolean,
    ) {
        data class LiquidTxAddressSummary(
            var walletAddress: String? = null,
            var walletAddressAmountSats: Long = 0,
            var changeAddress: String? = null,
            var changeAmountSats: Long = 0,
        )

        val network = lwkNetwork ?: Network.mainnet()
        val policyAsset = network.policyAsset()
        val walletId = currentWalletId
        val metadata = walletId?.let(secureStorage::getLiquidMetadataSnapshot)
        val txLabels = metadata?.txLabels?.toMutableMap() ?: mutableMapOf()
        val txSources = metadata?.txSources?.toMutableMap() ?: mutableMapOf()
        val txSwapDetails = metadata?.txSwapDetails?.toMutableMap() ?: mutableMapOf()
        val pendingLightningReceivesByAddress =
            metadata?.pendingLightningReceives?.associateBy(PendingLightningReceive::claimAddress)?.toMutableMap()
                ?: mutableMapOf()

        val balanceMap: Map<String, ULong> = wollet.balance()
        val lbtcBalance = balanceMap[policyAsset]?.toLong() ?: 0L
        val txAddressSummaries = mutableMapOf<String, LiquidTxAddressSummary>()

        try {
            wollet.txos().forEach { txo ->
                val txid = txo.outpoint().txid().toString()
                val address = txo.address().toString()
                val value = txo.unblinded().value().toLong()
                val summary = txAddressSummaries.getOrPut(txid) { LiquidTxAddressSummary() }

                if (txo.extInt() == Chain.INTERNAL) {
                    if (summary.changeAddress == null) {
                        summary.changeAddress = address
                    }
                    summary.changeAmountSats += value
                } else {
                    if (summary.walletAddress == null) {
                        summary.walletAddress = address
                    }
                    summary.walletAddressAmountSats += value
                }
            }
        } catch (e: Exception) {
            logWarn("Failed to derive Liquid transaction address summaries", e)
        }

        val transactions = wollet.transactions()
            .mapNotNull { walletTx ->
                try {
                    val txid = walletTx.txid().toString()
                    val balMap: Map<String, Long> = walletTx.balance()
                    val lbtcDelta = balMap[policyAsset] ?: 0L
                    val fee = walletTx.fee().toLong()
                    val height = walletTx.height()?.toInt()
                    val timestamp = if (height != null) {
                        walletTx.timestamp()?.toLong().also {
                            walletId?.let { wid -> secureStorage.removeTxFirstSeen(wid, txid) }
                        }
                    } else {
                        walletId?.let { wid ->
                            val firstSeen = secureStorage.getTxFirstSeen(wid, txid)
                            if (firstSeen != null) {
                                firstSeen
                            } else {
                                val ts = walletTx.timestamp()?.toLong() ?: (System.currentTimeMillis() / 1000)
                                secureStorage.setTxFirstSeenIfAbsent(wid, txid, ts)
                                ts
                            }
                        } ?: walletTx.timestamp()?.toLong()
                    }
                    val addressSummary = txAddressSummaries[txid]
                    val existingSource = txSources[txid]
                    val pendingLightningReceive = addressSummary?.walletAddress?.let { pendingLightningReceivesByAddress[it] }

                    if (walletId != null && existingSource == null && pendingLightningReceive != null) {
                        secureStorage.saveLiquidTransactionSource(walletId, txid, LiquidTxSource.LIGHTNING_RECEIVE_SWAP)
                        txSources[txid] = LiquidTxSource.LIGHTNING_RECEIVE_SWAP
                        pendingLightningReceive.label
                            .takeIf { it.isNotBlank() }
                            ?.let { label ->
                                secureStorage.saveLiquidAddressLabel(
                                    walletId,
                                    pendingLightningReceive.claimAddress,
                                    label,
                                )
                                secureStorage.saveLiquidTransactionLabel(walletId, txid, label)
                                txLabels[txid] = label
                            }
                        secureStorage.deletePendingLightningReceivesByClaimAddress(walletId, pendingLightningReceive.claimAddress)
                        pendingLightningReceivesByAddress.remove(pendingLightningReceive.claimAddress)
                    }

                    val txType = walletTx.type()
                    val source = txSources[txid] ?: LiquidTxSource.NATIVE

                    LiquidTransaction(
                        txid = txid,
                        balanceSatoshi = lbtcDelta,
                        fee = fee,
                        height = height,
                        timestamp = timestamp,
                        walletAddress = addressSummary?.walletAddress,
                        walletAddressAmountSats =
                            addressSummary?.walletAddressAmountSats?.takeIf { it > 0L },
                        changeAddress = addressSummary?.changeAddress,
                        changeAmountSats =
                            addressSummary?.changeAmountSats?.takeIf { it > 0L },
                        memo = txLabels[txid].orEmpty(),
                        source = source,
                        swapDetails = txSwapDetails[txid],
                        type = when {
                            source == LiquidTxSource.CHAIN_SWAP -> LiquidTxType.SWAP
                            txType == "incoming" -> LiquidTxType.RECEIVE
                            txType == "outgoing" -> LiquidTxType.SEND
                            lbtcDelta >= 0 -> LiquidTxType.RECEIVE
                            else -> LiquidTxType.SEND
                        },
                    )
                } catch (e: Exception) {
                    logWarn("Failed to parse LWK transaction", e)
                    null
                }
            }.sortedWith(
                compareByDescending<LiquidTransaction> { it.timestamp ?: Long.MIN_VALUE }
                    .thenByDescending { it.height ?: -1 }
            )

        val existingState = _liquidState.value
        val derivedCurrentAddress = try {
            val addrResult = wollet.address(null)
            addrResult.index() to addrResult.address().toString()
        } catch (_: Exception) {
            null
        }
        val currentAddr = existingState.currentAddress ?: derivedCurrentAddress?.second
        val currentAddressIndex = existingState.currentAddressIndex ?: derivedCurrentAddress?.first
        val currentAddressLabel = if (walletId != null && currentAddr != null) {
            secureStorage.getLiquidAddressLabel(walletId, currentAddr)
        } else {
            null
        }

        _liquidState.value = _liquidState.value.copy(
            isInitialized = true,
            balanceSats = lbtcBalance,
            transactions = transactions,
            currentAddress = currentAddr,
            currentAddressIndex = currentAddressIndex,
            currentAddressLabel = currentAddressLabel,
            isSyncing = false,
            lastSyncTimestamp = if (updateLastSyncTimestamp) {
                System.currentTimeMillis()
            } else {
                _liquidState.value.lastSyncTimestamp
            },
            error = null,
        )
    }

    fun saveLiquidTransactionSource(
        txid: String,
        source: LiquidTxSource,
    ) {
        val walletId = currentWalletId ?: return
        secureStorage.saveLiquidTransactionSource(walletId, txid, source)
        refreshCachedWalletState()
    }

    fun saveLiquidTransactionLabel(
        txid: String,
        label: String,
    ) {
        val walletId = currentWalletId ?: return
        secureStorage.saveLiquidTransactionLabel(walletId, txid, label)
    }

    fun saveLiquidSwapDetails(
        txid: String,
        details: LiquidSwapDetails,
    ) {
        val walletId = currentWalletId ?: return
        secureStorage.saveLiquidSwapDetails(walletId, txid, details)
        refreshCachedWalletState()
    }

    fun buildLiquidSwapDetails(
        service: SwapService,
        direction: SwapDirection,
        swapId: String,
        role: LiquidSwapTxRole,
        depositAddress: String,
        receiveAddress: String? = null,
        refundAddress: String? = null,
        sendAmountSats: Long = 0L,
        expectedReceiveAmountSats: Long = 0L,
    ): LiquidSwapDetails {
        return LiquidSwapDetails(
            service = service,
            direction = direction,
            swapId = swapId,
            role = role,
            depositAddress = depositAddress,
            receiveAddress = receiveAddress,
            refundAddress = refundAddress,
            sendAmountSats = sendAmountSats,
            expectedReceiveAmountSats = expectedReceiveAmountSats,
        )
    }

    fun finalizeLightningReceiveTransaction(
        swapId: String,
        txid: String,
    ) {
        val walletId = currentWalletId ?: return
        val pendingReceive = secureStorage.getPendingLightningReceive(walletId, swapId)
        secureStorage.saveLiquidTransactionSource(walletId, txid, LiquidTxSource.LIGHTNING_RECEIVE_SWAP)
        pendingReceive?.label
            ?.takeIf { it.isNotBlank() }
            ?.let {
                secureStorage.saveLiquidAddressLabel(walletId, pendingReceive.claimAddress, it)
                secureStorage.saveLiquidTransactionLabel(walletId, txid, it)
            }
        secureStorage.deletePendingLightningReceive(walletId, swapId)
    }

    // ════════════════════════════════════════════
    // BoltzSession (LWK built-in swap support)
    // ════════════════════════════════════════════

    /**
     * Initialize the LWK BoltzSession for built-in Boltz swap operations.
     * Requires: network, client, and optionally mnemonic (for signing swaps).
     */
    private suspend fun ensureBoltzSession(): BoltzSession = boltzSessionMutex.withLock {
        ensureBoltzEnabled()
        val startedAt = System.currentTimeMillis()
        val traceStartedAt = boltzTraceStart()
        val boltzSource = secureStorage.getBoltzApiSource()
        val usesTor = boltzSource == SecureStorage.BOLTZ_API_TOR
        val sessionTimeoutSecs = if (usesTor) BOLTZ_SESSION_TIMEOUT_TOR_SECS else BOLTZ_SESSION_TIMEOUT_CLEARNET_SECS
        val walletId = currentWalletId ?: throw Exception("Wallet not loaded")
        val cachedNextIndex = secureStorage.getBoltzSessionNextIndex(walletId)?.toUInt()
        val trace =
            BoltzTraceContext(
                operation = "ensureBoltzSession",
                viaTor = usesTor,
                session = "shared",
            )
        lwkBoltzSession?.let {
            logBoltzTrace(
                "reuse",
                trace.copy(session = "warm"),
                "elapsedMs" to boltzElapsedMs(traceStartedAt),
                "timeoutSeconds" to sessionTimeoutSecs,
            )
            logDebug("ensureBoltzSession reuse existing elapsedMs=${System.currentTimeMillis() - startedAt}")
            return@withLock it
        }

        val network = lwkNetwork ?: throw Exception("Liquid network not initialized")
        val client = lwkClient ?: throw Exception("Liquid server not connected")
        val signer = ensureSigner()
        val boltzMnemonic = deriveBoltzRescueMnemonic(signer)
        logBoltzTrace(
            "start",
            trace.copy(session = "cold"),
            "timeoutSeconds" to sessionTimeoutSecs,
            "cachedNextIndex" to cachedNextIndex,
        )

        try {
            if (usesTor) {
                ensureBoltzTorReadyIfNeeded()
            }
            lwkBoltzAnyClient?.let { staleAnyClient ->
                runCatching { staleAnyClient.destroy() }
                lwkBoltzAnyClient = null
            }
            val anyClient = AnyClient.fromElectrum(client)
            lwkBoltzAnyClient = anyClient
            val builder = BoltzSessionBuilder(
                network = network,
                client = anyClient,
                mnemonic = boltzMnemonic,
                nextIndexToUse = cachedNextIndex,
                timeout = sessionTimeoutSecs.toULong(),
                polling = true,
                bitcoinElectrumClientUrl = getBitcoinElectrumUrl(),
            )

            BoltzSession.fromBuilder(builder).also {
                lwkBoltzSession = it
                persistBoltzSessionNextIndex(walletId, it)
                logBoltzTrace(
                    "success",
                    trace.copy(session = "cold"),
                    "elapsedMs" to boltzElapsedMs(traceStartedAt),
                    "timeoutSeconds" to sessionTimeoutSecs,
                    "nextIndex" to secureStorage.getBoltzSessionNextIndex(walletId),
                )
                Log.d(
                    TAG,
                    "BoltzSession initialized timeoutSeconds=$sessionTimeoutSecs usesTor=$usesTor " +
                        "elapsedMs=${System.currentTimeMillis() - startedAt}",
                )
            }
        } catch (e: LwkException) {
            lwkBoltzSession = null
            lwkBoltzAnyClient?.let { anyClient ->
                runCatching { anyClient.destroy() }
            }
            lwkBoltzAnyClient = null
            val sessionError = normalizeBoltzSessionInitFailure(e)
            logBoltzTrace(
                "failed",
                trace.copy(session = "cold"),
                level = BoltzTraceLevel.WARN,
                throwable = sessionError,
                "elapsedMs" to boltzElapsedMs(traceStartedAt),
                "timeoutSeconds" to sessionTimeoutSecs,
                "cachedNextIndex" to cachedNextIndex,
                "errorCategory" to boltzSessionInitFailureCategory(sessionError),
                "transient" to sessionError.isTransient,
            )
            throw sessionError
        } catch (e: Exception) {
            lwkBoltzSession = null
            lwkBoltzAnyClient?.let { anyClient ->
                runCatching { anyClient.destroy() }
            }
            lwkBoltzAnyClient = null
            val sessionError = normalizeBoltzSessionInitFailure(e)
            logBoltzTrace(
                "failed",
                trace.copy(session = "cold"),
                level = BoltzTraceLevel.WARN,
                throwable = sessionError,
                "elapsedMs" to boltzElapsedMs(traceStartedAt),
                "timeoutSeconds" to sessionTimeoutSecs,
                "cachedNextIndex" to cachedNextIndex,
                "errorCategory" to boltzSessionInitFailureCategory(sessionError),
                "transient" to sessionError.isTransient,
            )
            throw sessionError
        }
    }

    private class BoltzSessionInitializationException(
        message: String,
        cause: Throwable,
        val isTransient: Boolean,
    ) : Exception(message, cause)

    private fun normalizeBoltzSessionInitFailure(error: Throwable): BoltzSessionInitializationException {
        val detail =
            generateSequence(error) { it.cause }
                .mapNotNull { throwable -> throwable.message?.trim()?.takeIf { it.isNotEmpty() } }
                .firstOrNull()
                .orEmpty()
        val isConnectionReset = detail.contains("Connection reset by peer", ignoreCase = true)
        val allAttemptsErrored = detail.contains("AllAttemptsErrored", ignoreCase = true)
        val timedOut =
            detail.contains("timed out", ignoreCase = true) ||
                detail.contains("Timeout(", ignoreCase = true)
        val isTransient = isConnectionReset || allAttemptsErrored || timedOut
        val normalizedMessage =
            when {
                isConnectionReset -> "Boltz session initialization failed due to a transient network reset"
                timedOut -> "Boltz session initialization timed out"
                allAttemptsErrored -> "Boltz session initialization failed after all connection attempts"
                detail.isNotBlank() -> "Boltz session initialization failed: $detail"
                else -> "Boltz session initialization failed"
            }
        return BoltzSessionInitializationException(
            message = normalizedMessage,
            cause = error,
            isTransient = isTransient,
        )
    }

    private fun boltzSessionInitFailureCategory(error: BoltzSessionInitializationException): String {
        val message = error.message.orEmpty()
        return when {
            message.contains("network reset", ignoreCase = true) -> "network_reset"
            message.contains("timed out", ignoreCase = true) -> "timeout"
            message.contains("all connection attempts", ignoreCase = true) -> "all_attempts_failed"
            else -> "generic"
        }
    }

    private suspend fun ensureBoltzTorReadyIfNeeded() {
        if (secureStorage.getBoltzApiSource() != SecureStorage.BOLTZ_API_TOR) return
        val trace = BoltzTraceContext(operation = "ensureBoltzTorReady", viaTor = true, source = "tor")
        val startedAt = boltzTraceStart()
        if (torManager.isReady()) {
            logBoltzTrace("already_ready", trace, "elapsedMs" to boltzElapsedMs(startedAt))
            return
        }
        logBoltzTrace("start", trace, "timeoutMs" to 60_000)
        torManager.start()
        if (!torManager.awaitReady(60_000)) {
            logBoltzTrace(
                "failed",
                trace,
                level = BoltzTraceLevel.WARN,
                "elapsedMs" to boltzElapsedMs(startedAt),
            )
            throw Exception("Tor failed to start for Boltz")
        }
        logBoltzTrace("success", trace, "elapsedMs" to boltzElapsedMs(startedAt))
    }

    // ════════════════════════════════════════════
    // Sync
    // ════════════════════════════════════════════

    /**
     * Sync the Liquid wallet with the Electrum server.
     * Updates balance and transaction history.
     */
    suspend fun sync(): Boolean = syncMutex.withLock {
        withContext(Dispatchers.IO) {
            val wollet = lwkWollet ?: run {
                Log.w(TAG, "Cannot sync: wallet not loaded")
                return@withContext false
            }
            val client = lwkClient ?: run {
                Log.w(TAG, "Cannot sync: not connected")
                return@withContext false
            }

            try {
                _liquidState.value = _liquidState.value.copy(isSyncing = true)

                if (scriptHashStatusCache.isNotEmpty()) {
                    val hasChanges = liquidElectrumProxy?.checkForScriptHashChanges(scriptHashStatusCache) ?: true
                    if (!hasChanges) {
                        _liquidState.value = _liquidState.value.copy(
                            isSyncing = false,
                            lastSyncTimestamp = System.currentTimeMillis(),
                            error = null,
                        )
                        return@withContext true
                    }
                }

                prewarmLiquidTransactionHistoryCache(wollet)

                // Full scan: fetches all transactions for the wallet's addresses
                val update: Update? = client.fullScan(wollet)
                if (update != null) {
                    wollet.applyUpdate(update)
                }

                refreshLiquidWalletState(
                    wollet = wollet,
                    updateLastSyncTimestamp = true,
                )
                refreshScriptHashStatusCache(wollet)
                subscribeNewlyRevealedAddresses()

                Log.d(
                    TAG,
                    "Liquid sync complete: balance=${_liquidState.value.balanceSats} sats, ${_liquidState.value.transactions.size} txs",
                )
                true
            } catch (e: LwkException) {
                logError("LWK sync failed", e)
                _liquidState.value = _liquidState.value.copy(
                    isSyncing = false,
                    error = "Sync failed: ${e.message}",
                )
                false
            } catch (e: Exception) {
                logError("Liquid sync failed", e)
                _liquidState.value = _liquidState.value.copy(
                    isSyncing = false,
                    error = "Sync failed: ${e.message}",
                )
                false
            }
        }
    }

    fun refreshCachedWalletState() {
        val wollet = lwkWollet ?: return
        refreshLiquidWalletState(
            wollet = wollet,
            updateLastSyncTimestamp = false,
        )
    }

    private fun prewarmLiquidTransactionHistoryCache(wollet: Wollet) {
        try {
            val knownTxids = wollet.transactions().mapTo(linkedSetOf()) { it.txid().toString() }.toList()
            if (knownTxids.isEmpty()) return

            val proxy = liquidElectrumProxy ?: return
            val uncached = electrumCache.findMissingRawTxids(knownTxids)
            if (uncached.isEmpty()) {
                if (BuildConfig.DEBUG) Log.d(TAG, "All ${knownTxids.size} Liquid txs already in persistent cache")
                return
            }

            val warmed = proxy.pipelineFetchTransactions(uncached)
            if (BuildConfig.DEBUG) {
                Log.d(
                    TAG,
                    "Liquid prewarmed $warmed/${uncached.size} uncached txs (${knownTxids.size - uncached.size} already cached)",
                )
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.w(TAG, "Liquid tx history warmup failed: ${e.message}")
        }
    }

    suspend fun startRealTimeSubscriptions(): SubscriptionResult =
        withContext(Dispatchers.IO) {
            val walletId = currentWalletId ?: return@withContext SubscriptionResult.FAILED
            val wollet = lwkWollet ?: return@withContext SubscriptionResult.FAILED
            val proxy = liquidElectrumProxy ?: return@withContext SubscriptionResult.FAILED

            stopNotificationCollector()

            val allScriptHashes = getAllRevealedLiquidScriptHashes(wollet)
            if (allScriptHashes.isEmpty()) {
                logWarn("Failed to start Liquid subscriptions: no revealed script hashes", Exception("No addresses"))
                return@withContext SubscriptionResult.FAILED
            }

            val currentStatuses = proxy.startSubscriptions(allScriptHashes)
            if (currentStatuses.isEmpty()) {
                Log.w(TAG, "Failed to start Liquid subscriptions: proxy returned empty status set")
                return@withContext SubscriptionResult.FAILED
            }

            subscribedScriptHashes.clear()
            subscribedScriptHashes.addAll(currentStatuses.keys)

            val persistedStatuses = secureStorage.getLiquidScriptHashStatuses(walletId)
            val needsSync =
                persistedStatuses.isEmpty() ||
                    currentStatuses.any { (scriptHash, currentStatus) ->
                        !persistedStatuses.containsKey(scriptHash) || persistedStatuses[scriptHash] != currentStatus
                    }

            val result =
                if (needsSync) {
                    scriptHashStatusCache.clear()
                    if (sync()) {
                        if (scriptHashStatusCache.isEmpty()) {
                            scriptHashStatusCache.putAll(currentStatuses)
                        }
                        subscribeNewlyRevealedAddresses()
                        SubscriptionResult.SYNCED
                    } else {
                        SubscriptionResult.FAILED
                    }
                } else {
                    scriptHashStatusCache.clear()
                    scriptHashStatusCache.putAll(currentStatuses)
                    SubscriptionResult.NO_CHANGES
                }

            if (scriptHashStatusCache.isNotEmpty()) {
                secureStorage.saveLiquidScriptHashStatuses(walletId, scriptHashStatusCache.toMap())
            }
            startNotificationCollector(proxy)
            result
        }

    private fun startNotificationCollector(proxy: CachingElectrumProxy) {
        notificationCollectorJob?.cancel()
        notificationCollectorJob =
            repositoryScope.launch {
                val pendingNotifications = mutableListOf<ElectrumNotification>()
                var lastNotificationTime: Long

                proxy.notifications.collect { notification ->
                    if (!isActive) return@collect
                    if (notification is ElectrumNotification.ConnectionLost) {
                        Log.w(TAG, "Liquid subscription socket reported connection lost")
                        _connectionEvents.tryEmit(ConnectionEvent.ConnectionLost)
                        return@collect
                    }

                    pendingNotifications.add(notification)
                    lastNotificationTime = System.currentTimeMillis()

                    launch debounceSync@{
                        delay(NOTIFICATION_DEBOUNCE_MS)
                        if (System.currentTimeMillis() - lastNotificationTime < NOTIFICATION_DEBOUNCE_MS) {
                            return@debounceSync
                        }

                        val batch = pendingNotifications.toList()
                        pendingNotifications.clear()
                        if (batch.isEmpty()) return@debounceSync

                        scriptHashStatusCache.clear()
                        if (sync()) {
                            subscribeNewlyRevealedAddresses()
                            currentWalletId?.let { walletId ->
                                if (scriptHashStatusCache.isNotEmpty()) {
                                    secureStorage.saveLiquidScriptHashStatuses(walletId, scriptHashStatusCache.toMap())
                                }
                            }
                        }
                    }
                }
            }
    }

    private fun stopNotificationCollector() {
        notificationCollectorJob?.cancel()
        notificationCollectorJob = null
    }

    private fun refreshScriptHashStatusCache(wollet: Wollet) {
        val walletId = currentWalletId ?: return
        val proxy = liquidElectrumProxy ?: return
        val allScriptHashes = getAllRevealedLiquidScriptHashes(wollet)
        if (allScriptHashes.isEmpty()) return

        val statuses = proxy.subscribeScriptHashes(allScriptHashes)
        if (statuses.isEmpty()) return

        scriptHashStatusCache.clear()
        scriptHashStatusCache.putAll(statuses)
        secureStorage.saveLiquidScriptHashStatuses(walletId, statuses)
    }

    private fun subscribeNewlyRevealedAddresses() {
        val wollet = lwkWollet ?: return
        val proxy = liquidElectrumProxy ?: return
        val allScriptHashes = getAllRevealedLiquidScriptHashes(wollet)
        val newHashes = allScriptHashes.filter { it !in subscribedScriptHashes }
        if (newHashes.isEmpty()) return

        val newStatuses = proxy.subscribeAdditionalScriptHashes(newHashes)
        subscribedScriptHashes.addAll(newStatuses.keys)
        scriptHashStatusCache.putAll(newStatuses)
    }

    private fun getAllRevealedLiquidScriptHashes(wollet: Wollet): List<String> {
        val scriptHashes = linkedSetOf<String>()

        runCatching {
            val currentReceive = wollet.address(null)
            for (i in 0u..currentReceive.index()) {
                val address = wollet.address(i).address().toString()
                computeLiquidScriptHashForAddress(address)?.let(scriptHashes::add)
            }
        }

        runCatching {
            wollet.txos().forEach { txo ->
                if (txo.extInt() == Chain.INTERNAL) {
                    computeLiquidScriptHashForAddress(txo.address().toString())?.let(scriptHashes::add)
                }
            }
        }

        return scriptHashes.toList()
    }

    private fun computeLiquidScriptHashForAddress(address: String): String? {
        return try {
            val scriptBytes = Address(address).scriptPubkey().bytes()
            BitcoinUtils.computeScriptHash(scriptBytes)
        } catch (e: Exception) {
            logWarn("Failed to derive Liquid script hash", e)
            null
        }
    }

    // ════════════════════════════════════════════
    // Address Generation
    // ════════════════════════════════════════════

    /** Get the next unused receive address (confidential Liquid address) */
    fun getNewAddress(): String? {
        val wollet = lwkWollet ?: return null
        return try {
            val addressResult = wollet.address(null) // null = next unused
            val address = addressResult.address().toString()
            val addressIndex = addressResult.index()
            val walletId = currentWalletId
            val label = if (walletId != null) secureStorage.getLiquidAddressLabel(walletId, address) else null
            _liquidState.value = _liquidState.value.copy(
                currentAddress = address,
                currentAddressIndex = addressIndex,
                currentAddressLabel = label,
            )
            address
        } catch (e: LwkException) {
            logError("LWK error getting address", e)
            null
        } catch (e: Exception) {
            logError("Failed to get Liquid address", e)
            null
        }
    }

    /** Force a fresh receive address even if the current unused one has not been used yet. */
    fun generateFreshAddress(): String? {
        val wollet = lwkWollet ?: return null
        return try {
            val currentIndex = _liquidState.value.currentAddressIndex ?: wollet.address(null).index()
            val nextIndex = currentIndex + 1u
            val address = wollet.address(nextIndex).address().toString()
            val walletId = currentWalletId
            val label = if (walletId != null) secureStorage.getLiquidAddressLabel(walletId, address) else null
            _liquidState.value = _liquidState.value.copy(
                currentAddress = address,
                currentAddressIndex = nextIndex,
                currentAddressLabel = label,
            )
            address
        } catch (e: LwkException) {
            logError("LWK error generating fresh Liquid address", e)
            null
        } catch (e: Exception) {
            logError("Failed to generate fresh Liquid address", e)
            null
        }
    }

    fun getAllAddresses(): Triple<List<WalletAddress>, List<WalletAddress>, List<WalletAddress>> {
        val wollet = lwkWollet ?: return Triple(emptyList(), emptyList(), emptyList())
        val walletId = currentWalletId ?: return Triple(emptyList(), emptyList(), emptyList())
        val network = lwkNetwork ?: return Triple(emptyList(), emptyList(), emptyList())
        val labels = secureStorage.getAllLiquidAddressLabels(walletId)

        val addressBalances = mutableMapOf<String, ULong>()
        val addressTxCounts = mutableMapOf<String, Int>()
        val internalByIndex = linkedMapOf<UInt, WalletAddress>()
        var maxInternalIndex = -1

        try {
            wollet.utxos().forEach { utxo ->
                val address = utxo.address().toString()
                val value = utxo.unblinded().value()
                addressBalances[address] = (addressBalances[address] ?: 0UL) + value
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Error listing Liquid UTXOs for address balances", e)
        }

        try {
            wollet.txos().forEach { txo ->
                val address = txo.address().toString()
                addressTxCounts[address] = (addressTxCounts[address] ?: 0) + 1

                if (txo.extInt() == Chain.INTERNAL) {
                    val idx = txo.wildcardIndex()
                    if (idx.toInt() > maxInternalIndex) maxInternalIndex = idx.toInt()
                    internalByIndex[idx] =
                        WalletAddress(
                            address = address,
                            index = idx,
                            keychain = KeychainType.INTERNAL,
                            label = labels[address],
                            balanceSats = addressBalances[address] ?: 0UL,
                            transactionCount = addressTxCounts[address] ?: 0,
                            isUsed = true,
                        )
                }
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Error listing Liquid TXOs", e)
        }

        try {
            val descriptor = wollet.descriptor()
            val isMainnet = network.isMainnet()
            val peekEnd = maxInternalIndex + ADDRESS_INTERNAL_PEEK_AHEAD
            for (i in 0..peekEnd) {
                val idx = i.toUInt()
                if (internalByIndex.containsKey(idx)) continue

                val script = descriptor.scriptPubkey(Chain.INTERNAL, idx)
                val scriptBytes = script.bytes()
                if (scriptBytes.size < 2) continue

                val blindingKey = descriptor.deriveBlindingKey(script) ?: continue
                val blindingPubKey =
                    ElectrumSeedUtil.publicKeyFromPrivate(blindingKey.bytes())
                val witnessVersion =
                    if (scriptBytes[0].toInt() == 0x00) 0
                    else (scriptBytes[0].toInt() - 0x50)
                val programLen = scriptBytes[1].toInt() and 0xFF
                val witnessProgram = scriptBytes.copyOfRange(2, 2 + programLen)

                val address =
                    Blech32Util.encodeConfidentialAddress(
                        blindingPubKey = blindingPubKey,
                        witnessProgram = witnessProgram,
                        witnessVersion = witnessVersion,
                        isMainnet = isMainnet,
                    )

                internalByIndex[idx] =
                    WalletAddress(
                        address = address,
                        index = idx,
                        keychain = KeychainType.INTERNAL,
                        label = labels[address],
                        balanceSats = addressBalances[address] ?: 0UL,
                        transactionCount = addressTxCounts[address] ?: 0,
                        isUsed = (addressTxCounts[address] ?: 0) > 0,
                    )
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Error deriving Liquid change addresses", e)
        }

        val receiveAddresses = mutableListOf<WalletAddress>()
        val changeAddresses = mutableListOf<WalletAddress>()
        val usedAddresses = mutableListOf<WalletAddress>()

        try {
            val currentReceive = wollet.address(null)
            val lastExternalIndex = maxOf(
                currentReceive.index(),
                _liquidState.value.currentAddressIndex ?: 0u,
            )

            for (i in 0u..lastExternalIndex) {
                val address = wollet.address(i).address().toString()
                val isUsed = (addressTxCounts[address] ?: 0) > 0
                val balance = addressBalances[address] ?: 0UL

                val walletAddress =
                    WalletAddress(
                        address = address,
                        index = i,
                        keychain = KeychainType.EXTERNAL,
                        label = labels[address],
                        balanceSats = balance,
                        transactionCount = addressTxCounts[address] ?: 0,
                        isUsed = isUsed,
                    )

                if (isUsed) {
                    usedAddresses.add(walletAddress)
                } else {
                    receiveAddresses.add(walletAddress)
                }
            }

            val nextIndex = lastExternalIndex + 1u
            val extraCount = maxOf(1, ADDRESS_PEEK_AHEAD - receiveAddresses.size)
            for (offset in 0 until extraCount) {
                val index = nextIndex + offset.toUInt()
                val address = wollet.address(index).address().toString()
                receiveAddresses.add(
                    WalletAddress(
                        address = address,
                        index = index,
                        keychain = KeychainType.EXTERNAL,
                        label = labels[address],
                        balanceSats = 0UL,
                        transactionCount = 0,
                        isUsed = false,
                    ),
                )
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Error deriving Liquid receive addresses", e)
        }

        val sortedChangeAddresses = internalByIndex.entries.sortedBy { it.key }.map { it.value }
        changeAddresses.addAll(sortedChangeAddresses.filter { !it.isUsed })
        usedAddresses.addAll(sortedChangeAddresses.filter { it.isUsed })
        return Triple(receiveAddresses, changeAddresses, usedAddresses)
    }

    fun getAllUtxos(): List<UtxoInfo> {
        val wollet = lwkWollet ?: return emptyList()
        val walletId = currentWalletId ?: return emptyList()
        val labels = secureStorage.getAllLiquidAddressLabels(walletId)
        val frozenUtxos = secureStorage.getFrozenUtxos(walletId)

        return try {
            wollet.utxos().mapNotNull { utxo ->
                try {
                    val address = utxo.address().toString()
                    val outpointObj = utxo.outpoint()
                    val txid = outpointObj.txid().toString()
                    val vout = outpointObj.vout()
                    val outpoint = "$txid:$vout"

                    UtxoInfo(
                        outpoint = outpoint,
                        txid = txid,
                        vout = vout,
                        address = address,
                        amountSats = utxo.unblinded().value(),
                        label = labels[address],
                        isConfirmed = utxo.height() != null,
                        isFrozen = frozenUtxos.contains(outpoint),
                    )
                } catch (e: Exception) {
                    if (BuildConfig.DEBUG) Log.e(TAG, "Error parsing Liquid UTXO", e)
                    null
                }
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Error listing Liquid UTXOs", e)
            emptyList()
        }
    }

    fun setUtxoFrozen(
        outpoint: String,
        frozen: Boolean,
    ) {
        val walletId = currentWalletId ?: return
        if (frozen) {
            secureStorage.freezeUtxo(walletId, outpoint)
        } else {
            secureStorage.unfreezeUtxo(walletId, outpoint)
        }
    }

    // ════════════════════════════════════════════
    // Send L-BTC
    // ════════════════════════════════════════════

    /**
     * Send L-BTC to a Liquid address.
     * Uses LWK's TxBuilder -> Signer -> Wollet.finalize -> Client.broadcast flow.
     *
     * @return Transaction ID on success
     */
    private data class LbtcMaxSendPlan(
        val amountSats: Long,
        val feeRateSatPerVb: Double,
    )

    private suspend fun resolveLbtcMaxSendPlan(
        address: String,
        feeRateSatPerVb: Double,
        selectedUtxos: List<UtxoInfo>?,
    ): LbtcMaxSendPlan = withContext(Dispatchers.IO) {
        val maxAmount = getMaxSpendableLbtc(address, feeRateSatPerVb, selectedUtxos)
        if (maxAmount <= 0L) {
            return@withContext LbtcMaxSendPlan(
                amountSats = 0L,
                feeRateSatPerVb = feeRateSatPerVb,
            )
        }

        val recipients = listOf(Recipient(address, maxAmount.toULong()))
        val basePreview =
            previewLbtcTransfer(
                recipients = recipients,
                feeRateSatPerVb = feeRateSatPerVb,
                selectedUtxos = selectedUtxos,
                label = null,
                isMaxSend = true,
            )
        val residue = basePreview.remainingSats ?: 0L
        val txVBytes = basePreview.txVBytes?.takeIf { it > 0.0 }
        val feeSats = basePreview.feeSats ?: 0L
        if (residue !in 1L..MAX_LBTC_MAX_FEE_ABSORB_SATS || txVBytes == null || feeSats <= 0L) {
            return@withContext LbtcMaxSendPlan(
                amountSats = maxAmount,
                feeRateSatPerVb = feeRateSatPerVb,
            )
        }

        for (extraFeeSats in residue..MAX_LBTC_MAX_FEE_ABSORB_SATS) {
            val adjustedFeeRate = (feeSats + extraFeeSats).toDouble() / txVBytes
            val adjustedPreview =
                runCatching {
                    previewLbtcTransfer(
                        recipients = recipients,
                        feeRateSatPerVb = adjustedFeeRate,
                        selectedUtxos = selectedUtxos,
                        label = null,
                        isMaxSend = true,
                    )
                }.getOrNull() ?: continue
            if ((adjustedPreview.remainingSats ?: 0L) == 0L) {
                return@withContext LbtcMaxSendPlan(
                    amountSats = maxAmount,
                    feeRateSatPerVb = adjustedFeeRate,
                )
            }
        }

        LbtcMaxSendPlan(
            amountSats = maxAmount,
            feeRateSatPerVb = feeRateSatPerVb,
        )
    }

    suspend fun previewLbtcSend(
        address: String,
        amountSats: Long,
        feeRateSatPerVb: Double = 0.1,
        selectedUtxos: List<UtxoInfo>? = null,
        isMaxSend: Boolean = false,
        label: String? = null,
    ): LiquidSendPreview = withContext(Dispatchers.IO) {
        val maxPlan =
            if (isMaxSend) {
                resolveLbtcMaxSendPlan(address, feeRateSatPerVb, selectedUtxos)
            } else {
                null
            }
        val normalizedAmount = maxPlan?.amountSats ?: amountSats
        if (normalizedAmount <= 0L) {
            throw Exception("No spendable L-BTC available")
        }
        previewLbtcTransfer(
            recipients = listOf(Recipient(address, normalizedAmount.toULong())),
            feeRateSatPerVb = maxPlan?.feeRateSatPerVb ?: feeRateSatPerVb,
            selectedUtxos = selectedUtxos,
            label = label,
            isMaxSend = isMaxSend,
        )
    }

    suspend fun previewLbtcSendMulti(
        recipients: List<Recipient>,
        feeRateSatPerVb: Double = 0.1,
        selectedUtxos: List<UtxoInfo>? = null,
        label: String? = null,
    ): LiquidSendPreview = withContext(Dispatchers.IO) {
        previewLbtcTransfer(
            recipients = recipients,
            feeRateSatPerVb = feeRateSatPerVb,
            selectedUtxos = selectedUtxos,
            label = label,
        )
    }

    suspend fun sendLBTC(
        address: String,
        amountSats: Long,
        feeRateSatPerVb: Double = 0.1,
        selectedUtxos: List<UtxoInfo>? = null,
        isMaxSend: Boolean = false,
        label: String? = null,
        saveRecipientLabel: Boolean = true,
    ): String = withContext(Dispatchers.IO) {
        val maxPlan =
            if (isMaxSend) {
                resolveLbtcMaxSendPlan(address, feeRateSatPerVb, selectedUtxos)
            } else {
                null
            }
        val actualAmount = maxPlan?.amountSats ?: amountSats
        if (actualAmount <= 0L) {
            throw Exception("No spendable L-BTC available")
        }
        sendLbtcTransfer(
            recipients = listOf(Recipient(address, actualAmount.toULong())),
            feeRateSatPerVb = maxPlan?.feeRateSatPerVb ?: feeRateSatPerVb,
            selectedUtxos = selectedUtxos,
            label = label,
            saveRecipientLabel = saveRecipientLabel,
        )
    }

    suspend fun sendLBTCMulti(
        recipients: List<Recipient>,
        feeRateSatPerVb: Double = 0.1,
        selectedUtxos: List<UtxoInfo>? = null,
        label: String? = null,
    ): String = withContext(Dispatchers.IO) {
        sendLbtcTransfer(
            recipients = recipients,
            feeRateSatPerVb = feeRateSatPerVb,
            selectedUtxos = selectedUtxos,
            label = label,
            saveRecipientLabel = false,
        )
    }

    private suspend fun sendLbtcTransfer(
        recipients: List<Recipient>,
        feeRateSatPerVb: Double,
        selectedUtxos: List<UtxoInfo>?,
        label: String?,
        saveRecipientLabel: Boolean,
    ): String = withContext(Dispatchers.IO) {
        val wollet = lwkWollet ?: throw Exception("Wallet not loaded")
        val signer = ensureSigner()
        val client = lwkClient ?: throw Exception("Not connected to Electrum")
        val network = lwkNetwork ?: throw Exception("Network not initialized")
        require(recipients.isNotEmpty()) { "At least one Liquid recipient is required" }

        try {
            val pset = buildLbtcSendPset(
                wollet = wollet,
                network = network,
                recipients = recipients,
                feeRateSatPerVb = feeRateSatPerVb,
                selectedUtxos = selectedUtxos,
            )

            // 2. Sign the PSET
            val signedPset: Pset = signer.sign(pset)

            // 3. Finalize: Wollet.finalize returns a finalized Pset
            val finalizedPset: Pset = wollet.finalize(signedPset)

            // 4. Extract the transaction from the finalized PSET
            val tx = finalizedPset.extractTx()

            // 5. Broadcast
            val txid = client.broadcast(tx)
            val txidStr = txid.toString()

            if (BuildConfig.DEBUG) Log.d(TAG, "L-BTC sent successfully: $txidStr")

            persistLiquidSendLabel(
                txid = txidStr,
                label = label,
                recipientAddress = recipients.singleOrNull()?.address.takeIf { saveRecipientLabel },
            )

            // Keep the send successful even if the post-broadcast sync fails.
            runCatching { sync() }
                .onFailure { error ->
                    val syncError = (error as? Exception) ?: Exception(error.message ?: "Post-send sync failed")
                    logWarn("Post-send sync failed after L-BTC broadcast", syncError)
                }

            txidStr
        } catch (e: LwkException) {
            logError("LWK error sending L-BTC", e)
            val shortfall = extractMissingSats(e.message)
            if (shortfall != null) {
                throw Exception(
                    "Insufficient L-BTC for the send amount plus Liquid fee. Reduce the amount by at least $shortfall sats.",
                )
            }
            throw Exception("Send failed: ${e.message}")
        } catch (e: Exception) {
            logError("Failed to send L-BTC", e)
            throw Exception("Send failed: ${e.message}")
        }
    }

    private suspend fun previewLbtcTransfer(
        recipients: List<Recipient>,
        feeRateSatPerVb: Double,
        selectedUtxos: List<UtxoInfo>?,
        label: String?,
        isMaxSend: Boolean = false,
    ): LiquidSendPreview = withContext(Dispatchers.IO) {
        val wollet = lwkWollet ?: throw Exception("Wallet not loaded")
        val network = lwkNetwork ?: throw Exception("Network not initialized")
        require(recipients.isNotEmpty()) { "At least one Liquid recipient is required" }

        val pset = buildLbtcSendPset(
            wollet = wollet,
            network = network,
            recipients = recipients,
            feeRateSatPerVb = feeRateSatPerVb,
            selectedUtxos = selectedUtxos,
        )
        val details = wollet.psetDetails(pset)
        val balance = details.balance()
        try {
            val feeSats = balance.fee().toLong()
            val changeOutput = extractLiquidPreviewChangeOutput(pset, recipients, network, wollet)
            val totalRecipientSats = recipients.sumOf { it.amountSats.toLong() }
            val availableSats =
                selectedUtxos
                    ?.takeIf { it.isNotEmpty() }
                    ?.sumOf { it.amountSats.toLong() }
                    ?: _liquidState.value.balanceSats.coerceAtLeast(0L)
            val txVBytes = if (feeRateSatPerVb > 0.0) feeSats.toDouble() / feeRateSatPerVb else null
            val effectiveFeeRate = if (txVBytes != null && txVBytes > 0.0) feeSats.toDouble() / txVBytes else feeRateSatPerVb
            LiquidSendPreview(
                kind = LiquidSendKind.LBTC,
                recipientDisplay =
                    if (recipients.size == 1) {
                        recipients.first().address
                    } else {
                        "${recipients.size} Liquid recipients"
                    },
                recipients = recipients,
                totalRecipientSats = totalRecipientSats,
                feeSats = feeSats,
                changeSats = changeOutput?.amountSats,
                changeAddress = changeOutput?.address,
                feeRate = effectiveFeeRate,
                availableSats = availableSats,
                remainingSats = (availableSats - totalRecipientSats - feeSats).coerceAtLeast(0L),
                label = label?.trim()?.takeIf { it.isNotBlank() },
                isMaxSend = isMaxSend,
                selectedUtxoCount = selectedUtxos?.size ?: 0,
                txVBytes = txVBytes,
            )
        } finally {
            balance.destroy()
            details.destroy()
        }
    }

    // ════════════════════════════════════════════
    // Swap Limits & Quotes
    // ════════════════════════════════════════════

    /**
     * Fetch min/max swap limits for a service + direction.
     * Lightweight call — suitable for firing on tab selection.
     */
    suspend fun getSwapLimits(
        direction: SwapDirection,
        service: SwapService,
    ): SwapLimits = withContext(Dispatchers.IO) {
        when (service) {
            SwapService.BOLTZ -> {
                val pair = getBoltzChainPairInfo(direction)
                SwapLimits(
                    service = service,
                    direction = direction,
                    minAmount = pair.limits.minimal,
                    maxAmount = pair.limits.maximal,
                    serviceFeePercent = pair.fees.percentage,
                )
            }
            SwapService.SIDESWAP -> {
                check(isSideSwapEnabled()) { "SideSwap is disabled in settings" }
                ensureSideSwapTorReadyIfNeeded()
                val status = sideSwapClient.getServerStatus()
                val walletInfo = sideSwapClient.walletInfo.value
                val isPegIn = direction == SwapDirection.BTC_TO_LBTC

                // Prefer dynamic min amounts from subscribe_value over server_status
                val minAmount = if (isPegIn) {
                    walletInfo.pegInMinAmount.takeIf { it > 0 } ?: status.minPegInAmount
                } else {
                    walletInfo.pegOutMinAmount.takeIf { it > 0 } ?: status.minPegOutAmount
                }

                SwapLimits(
                    service = service,
                    direction = direction,
                    minAmount = minAmount,
                    maxAmount = 0, // SideSwap doesn't publish a max
                    serviceFeePercent = if (isPegIn) status.serverFeePercentPegIn else status.serverFeePercentPegOut,
                )
            }
        }
    }

    suspend fun prewarmBoltzChainSwapContext(direction: SwapDirection) = withContext(Dispatchers.IO) {
        boltzRuntime.prewarmChainContext(direction)
    }

    suspend fun prewarmBoltzLightningContext() = withContext(Dispatchers.IO) {
        boltzRuntime.prewarmLightningContext()
    }

    suspend fun getLightningInvoiceLimits(): LightningInvoiceLimits = withContext(Dispatchers.IO) {
        ensureBoltzEnabled()
        val trace = BoltzTraceContext(operation = "getLightningInvoiceLimits", viaTor = secureStorage.getBoltzApiSource() == SecureStorage.BOLTZ_API_TOR)
        val traceStartedAt = boltzTraceStart()
        logBoltzTrace("start", trace)
        try {
            val limits = boltzRuntime.getLightningInvoiceLimits()
            logBoltzTrace(
                "success",
                trace,
                    "elapsedMs" to boltzElapsedMs(traceStartedAt),
                    "minimumSats" to limits.minimal,
                    "maximumSats" to limits.maximal,
            )
            LightningInvoiceLimits(
                minimumSats = limits.minimal,
                maximumSats = limits.maximal,
            )
        } catch (error: Exception) {
            logBoltzTrace(
                "failed",
                trace,
                level = BoltzTraceLevel.WARN,
                throwable = error,
                "elapsedMs" to boltzElapsedMs(traceStartedAt),
            )
            throw error
        }
    }

    /**
     * Fetch a swap quote from the specified service.
     * Tries LWK BoltzSession first for Boltz quotes, falls back to REST API.
     */
    suspend fun getSwapQuote(
        direction: SwapDirection,
        amountSats: Long,
        service: SwapService,
    ): SwapQuote = withContext(Dispatchers.IO) {
        val startedAt = System.currentTimeMillis()
        try {
            when (service) {
                SwapService.BOLTZ -> getBoltzQuote(direction, amountSats)
                SwapService.SIDESWAP -> getSideSwapQuote(direction, amountSats)
            }
        } finally {
            logTiming("getSwapQuote[$service/$direction]", startedAt)
        }
    }

    /**
     * Try to get a quote using LWK's built-in BoltzSession.
     * Returns null if BoltzSession is not available.
     */
    private suspend fun tryLwkBoltzQuote(direction: SwapDirection, amountSats: Long): SwapQuote? {
        val session = lwkBoltzSession ?: return null
        return try {
            val (sendAsset, receiveAsset) = when (direction) {
                SwapDirection.BTC_TO_LBTC -> SwapAsset.ONCHAIN to SwapAsset.LIQUID
                SwapDirection.LBTC_TO_BTC -> SwapAsset.LIQUID to SwapAsset.ONCHAIN
            }

            val quoteBuilder = session.quote(amountSats.toULong())
            quoteBuilder.send(sendAsset)
            quoteBuilder.receive(receiveAsset)
            val quote = quoteBuilder.build()

            val estimatedTime = when (direction) {
                SwapDirection.BTC_TO_LBTC -> "~20 min (2 BTC confirmations)"
                SwapDirection.LBTC_TO_BTC -> "~20 min (2 BTC confirmations)"
            }

            // LWK BoltzSession gives a single networkFee; split by direction
            val totalNetFee = quote.networkFee.toLong()
            val (btcFee, liqFee) = when (direction) {
                SwapDirection.BTC_TO_LBTC -> totalNetFee * 2 / 3 to totalNetFee / 3
                SwapDirection.LBTC_TO_BTC -> totalNetFee / 3 to totalNetFee * 2 / 3
            }
            val pair = runCatching { getBoltzChainPairInfo(direction) }.getOrNull()
            pair?.let {
                buildBoltzQuoteFromPair(
                    direction = direction,
                    amountSats = quote.sendAmount.toLong(),
                    pair = it,
                    estimatedTime = estimatedTime,
                    typicalBtcTxVsize = TYPICAL_BTC_TX_VSIZE,
                    defaultLiquidSwapFeeRate = DEFAULT_LIQUID_SWAP_FEE_RATE,
                    receiveAmountOverride = quote.receiveAmount.toLong(),
                    serviceFeeOverride = quote.boltzFee.toLong(),
                )
            } ?: SwapQuote(
                service = SwapService.BOLTZ,
                direction = direction,
                sendAmount = quote.sendAmount.toLong(),
                receiveAmount = quote.receiveAmount.toLong(),
                serviceFee = quote.boltzFee.toLong(),
                btcNetworkFee = btcFee,
                liquidNetworkFee = liqFee,
                providerMinerFee = 0L,
                estimatedTime = estimatedTime,
            )
        } catch (e: Exception) {
            Log.w(TAG, "LWK BoltzSession quote failed, falling back to REST: ${e.message}")
            null
        }
    }

    private suspend fun getBoltzQuote(
        direction: SwapDirection,
        amountSats: Long,
    ): SwapQuote {
        val startedAt = System.currentTimeMillis()
        val traceStartedAt = boltzTraceStart()
        val trace =
            BoltzTraceContext(
                operation = "getBoltzQuote",
                viaTor = secureStorage.getBoltzApiSource() == SecureStorage.BOLTZ_API_TOR,
            )
        tryLwkBoltzQuote(direction, amountSats)?.let { quote ->
            logBoltzTrace(
                "warm_session",
                trace.copy(session = "warm", source = "lwk"),
                "elapsedMs" to boltzElapsedMs(traceStartedAt),
                "direction" to direction,
                "amountSats" to amountSats,
                "receiveSats" to quote.receiveAmount,
                "feeSats" to quote.serviceFee + quote.totalNetworkFee,
            )
            logDebug(
                "getBoltzQuote using warm session direction=$direction amount=$amountSats " +
                    "receive=${quote.receiveAmount} fee=${quote.serviceFee + quote.totalNetworkFee}",
            )
            logTiming("getBoltzQuote[lwk/$direction]", startedAt)
            return quote
        }
        val pair = getBoltzChainPairInfo(direction)
        // Chain swap fee structure from GET /v2/swap/chain:
        //   serverMinerFee = Boltz's lockup+claim cost (deducted from amount)
        //   userLockupFee  = estimate for YOUR tx on sending chain
        //   userClaimFee   = estimate for YOUR tx on receiving chain
        //
        // Per docs: percentageFee = ceil(userLockAmount * percentage)
        //           serverLockAmount = floor(userLockAmount - percentageFee - serverMinerFee)
        val quote =
            buildBoltzQuoteFromPair(
                direction = direction,
                amountSats = amountSats,
                pair = pair,
                estimatedTime = "~20 min (2 BTC confirmations)",
                typicalBtcTxVsize = TYPICAL_BTC_TX_VSIZE,
                defaultLiquidSwapFeeRate = DEFAULT_LIQUID_SWAP_FEE_RATE,
            )
        logDebug(
            "getBoltzQuote using REST pair info direction=$direction amount=$amountSats " +
                "receive=${quote.receiveAmount} fee=${quote.serviceFee + quote.totalNetworkFee}",
        )
        logBoltzTrace(
            "rest_fallback",
            trace.copy(session = "cold", source = "rest"),
            "elapsedMs" to boltzElapsedMs(traceStartedAt),
            "direction" to direction,
            "amountSats" to amountSats,
            "receiveSats" to quote.receiveAmount,
            "feeSats" to quote.serviceFee + quote.totalNetworkFee,
        )
        logTiming("getBoltzQuote[rest/$direction]", startedAt)
        return quote
    }

    private suspend fun getSideSwapQuote(
        direction: SwapDirection,
        amountSats: Long,
    ): SwapQuote {
        check(isSideSwapEnabled()) { "SideSwap is disabled in settings" }
        ensureSideSwapTorReadyIfNeeded()
        val isPegIn = direction == SwapDirection.BTC_TO_LBTC

        val status = sideSwapClient.getServerStatus()
        val walletInfo = sideSwapClient.walletInfo.value
        val feePercent = if (isPegIn) status.serverFeePercentPegIn else status.serverFeePercentPegOut
        val serviceFee = (amountSats * feePercent / 100).toLong()
        val btcFeeRate = status.bitcoinFeeRate
        val liquidFeeRate = normalizeSideSwapLiquidFeeRate(status.elementsFeeRate)

        // Estimated time based on hot wallet balance from subscribe_value API:
        // Peg-in:  amount <= pegInWalletBalance  → 2 BTC conf (~20 min)
        //          amount >  pegInWalletBalance  → 102 BTC conf (~17 hrs)
        // Peg-out: amount <= pegOutWalletBalance → 2 Liquid conf (~2 min)
        //          amount >  pegOutWalletBalance → ~20-30 min
        val estimatedTime = if (isPegIn) {
            if (walletInfo.pegInWalletBalance > 0 && amountSats <= walletInfo.pegInWalletBalance) {
                "~20 min (2 BTC confirmations)"
            } else if (walletInfo.pegInWalletBalance > 0) {
                "~17 hrs (102 BTC confirmations)"
            } else {
                "~20 min (2 BTC confirmations)" // No wallet info yet — show optimistic
            }
        } else {
            if (walletInfo.pegOutWalletBalance > 0 && amountSats <= walletInfo.pegOutWalletBalance) {
                "~20 min (2 BTC confirmations)"
            } else if (walletInfo.pegOutWalletBalance > 0) {
                "~20-30 min"
            } else {
                "~2-30 min" // No wallet info yet
            }
        }

        // Network fees:
        // - Sending-chain fee is paid separately by the user.
        // - Receive-side payout fee is deducted from the amount received.
        val btcNetworkFee: Long
        val liquidNetworkFee: Long
        val recvAmount: Long
        if (isPegIn) {
            btcNetworkFee = estimateNetworkFeeSats(TYPICAL_BTC_TX_VSIZE, btcFeeRate)
            liquidNetworkFee = estimateNetworkFeeSats(SIDESWAP_PEG_IN_LIQUID_TX_VSIZE, liquidFeeRate)
            recvAmount = (amountSats - serviceFee - liquidNetworkFee).coerceAtLeast(0)
        } else {
            liquidNetworkFee = estimateNetworkFeeSats(TYPICAL_LIQUID_TX_VSIZE, DEFAULT_LIQUID_SWAP_FEE_RATE)
            btcNetworkFee = estimateNetworkFeeSats(status.pegOutBitcoinTxVsize, btcFeeRate)
            recvAmount = (amountSats - serviceFee - btcNetworkFee).coerceAtLeast(0)
        }

        // Prefer dynamic min amounts from subscribe_value over server_status
        val minAmount = if (isPegIn) {
            walletInfo.pegInMinAmount.takeIf { it > 0 } ?: status.minPegInAmount
        } else {
            walletInfo.pegOutMinAmount.takeIf { it > 0 } ?: status.minPegOutAmount
        }

        return SwapQuote(
            service = SwapService.SIDESWAP,
            direction = direction,
            sendAmount = amountSats,
            receiveAmount = recvAmount,
            serviceFee = serviceFee,
            btcNetworkFee = btcNetworkFee,
            liquidNetworkFee = liquidNetworkFee,
            providerMinerFee = 0L,
            btcFeeRate = btcFeeRate,
            liquidFeeRate = liquidFeeRate,
            minAmount = minAmount,
            maxAmount = 0, // SideSwap doesn't publish a max
            estimatedTime = estimatedTime,
        )
    }

    private fun normalizeSideSwapLiquidFeeRate(rawElementsFeeRate: Double): Double {
        if (rawElementsFeeRate <= 0.0) return DEFAULT_LIQUID_SWAP_FEE_RATE
        return if (rawElementsFeeRate < 0.001) {
            rawElementsFeeRate * 100_000.0
        } else {
            rawElementsFeeRate
        }
    }

    // ════════════════════════════════════════════
    // Preimage / Key Utilities (for Boltz swaps)
    // ════════════════════════════════════════════

    /** SHA-256 hash of a preimage */
    fun sha256(data: ByteArray): ByteArray {
        return MessageDigest.getInstance("SHA-256").digest(data)
    }

    // ── Helpers ──

    private fun formatSatsForDisplay(amountSats: Long): String = "%,d sats".format(Locale.US, amountSats)

    private data class LiquidPreviewChangeOutput(
        val address: String? = null,
        val amountSats: Long? = null,
    )

    private data class LightningPaymentReviewContext(
        val requestKey: String,
        val normalizedPaymentInput: String,
        val resolvedPaymentInput: String,
        val fetchedInvoice: String?,
        val requestedAmountSats: Long?,
        val paymentAmountSats: Long,
        val previewFundingAddress: String,
        val estimatedSwapFeeSats: Long,
        val estimatedLockupAmountSats: Long,
    )

    private data class ResolvedLightningPaymentInput(
        val paymentInput: String,
        val requestedAmountSats: Long?,
        val fetchedInvoice: String? = null,
    )

    private data class CachedLightningResolution(
        val cachedAt: Long,
        val resolution: ResolvedLightningPaymentInput,
    )

    private data class BoltzSubmarineRefundDetails(
        val refundAddress: String,
        val refundPublicKey: String,
    )

    private fun extractLiquidPreviewChangeOutput(
        pset: Pset,
        recipients: List<Recipient>,
        network: Network,
        wollet: Wollet,
    ): LiquidPreviewChangeOutput? {
        return try {
            val recipientScripts =
                recipients.mapNotNull { recipient ->
                    runCatching { Address(recipient.address).scriptPubkey().toString() }.getOrNull()
                }.toSet()
            val descriptor = wollet.descriptor()
            val isMainnet = network.isMainnet()

            pset.outputs().firstNotNullOfOrNull { output ->
                val script = output.scriptPubkey()
                val scriptStr = script.toString()
                val scriptBytes = script.bytes()

                if (scriptStr in recipientScripts || scriptBytes.size < 2) {
                    return@firstNotNullOfOrNull null
                }

                val blindingKey = descriptor.deriveBlindingKey(script)
                    ?: return@firstNotNullOfOrNull null

                val blindingPubKey =
                    ElectrumSeedUtil.publicKeyFromPrivate(blindingKey.bytes())
                val witnessVersion =
                    if (scriptBytes[0].toInt() == 0x00) 0
                    else (scriptBytes[0].toInt() - 0x50)
                val programLen = scriptBytes[1].toInt() and 0xFF
                val witnessProgram = scriptBytes.copyOfRange(2, 2 + programLen)

                LiquidPreviewChangeOutput(
                    address =
                        Blech32Util.encodeConfidentialAddress(
                            blindingPubKey = blindingPubKey,
                            witnessProgram = witnessProgram,
                            witnessVersion = witnessVersion,
                            isMainnet = isMainnet,
                        ),
                    amountSats = output.amount()?.toLong(),
                )
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun buildLightningPaymentRequestKey(
        paymentInput: String,
        requestedAmountSats: Long?,
    ): String {
        val digest =
            MessageDigest.getInstance("SHA-256")
                .digest("$paymentInput|${requestedAmountSats ?: -1L}".toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun lightningResolutionCacheKey(paymentInput: String, requestedAmountSats: Long?): String {
        return buildLightningPaymentRequestKey(normalizeLightningPaymentInput(paymentInput), requestedAmountSats)
    }

    private fun getCachedLightningPaymentResolution(
        paymentInput: String,
        requestedAmountSats: Long?,
    ): ResolvedLightningPaymentInput? {
        val key = lightningResolutionCacheKey(paymentInput, requestedAmountSats)
        val cached = lightningResolutionCache[key] ?: return null
        if (System.currentTimeMillis() - cached.cachedAt > BOLTZ_LIGHTNING_RESOLUTION_CACHE_MS) {
            lightningResolutionCache.remove(key)
            return null
        }
        return cached.resolution
    }

    private fun cacheLightningPaymentResolution(
        paymentInput: String,
        requestedAmountSats: Long?,
        resolution: ResolvedLightningPaymentInput,
    ) {
        val normalizedPaymentInput = normalizeLightningPaymentInput(paymentInput)
        val cached = CachedLightningResolution(System.currentTimeMillis(), resolution)
        linkedSetOf(
            lightningResolutionCacheKey(normalizedPaymentInput, requestedAmountSats),
            lightningResolutionCacheKey(normalizedPaymentInput, resolution.requestedAmountSats),
        ).forEach { key ->
            lightningResolutionCache[key] = cached
        }
    }

    private fun getLightningPreviewFundingAddress(): String {
        _liquidState.value.currentAddress?.takeIf { it.isNotBlank() }?.let { return it }
        return getNewAddress() ?: throw Exception("No Liquid address available for Lightning preview")
    }

    private fun estimateBoltzSubmarineSwapFee(
        paymentAmountSats: Long,
        pair: BoltzPairInfo,
    ): Long {
        val percentageFee = kotlin.math.ceil(paymentAmountSats * pair.fees.percentage / 100.0).toLong()
        return safeAddSats(percentageFee, pair.fees.serverMinerFee)
    }

    private suspend fun buildLightningPaymentReviewContext(
        paymentInput: String,
        requestedAmountSats: Long?,
    ): LightningPaymentReviewContext {
        val startedAt = System.currentTimeMillis()
        try {
            val normalizedPaymentInput = normalizeLightningPaymentInput(paymentInput)
            val resolvedPayment =
                try {
                    resolveBoltzLightningPaymentInput(normalizedPaymentInput, requestedAmountSats)
                } catch (e: Exception) {
                    throw normalizeLightningPaymentError(e, normalizedPaymentInput, requestedAmountSats)
                }
            val effectiveRequestedAmountSats = resolvedPayment.requestedAmountSats
            val paymentAmountSats = effectiveRequestedAmountSats ?: extractLightningPaymentAmountSats(resolvedPayment.paymentInput)
                ?: throw normalizeLightningPaymentError(
                    Exception("Amount missing"),
                    normalizedPaymentInput,
                    requestedAmountSats,
                )
            val submarinePair = getBoltzSubmarinePairInfo()
            validateBoltzLightningAmountLimits(paymentAmountSats, submarinePair.limits)
            val estimatedSwapFeeSats = estimateBoltzSubmarineSwapFee(paymentAmountSats, submarinePair)
            return LightningPaymentReviewContext(
                requestKey = buildLightningPaymentRequestKey(normalizedPaymentInput, effectiveRequestedAmountSats),
                normalizedPaymentInput = normalizedPaymentInput,
                resolvedPaymentInput = resolvedPayment.paymentInput,
                fetchedInvoice = resolvedPayment.fetchedInvoice,
                requestedAmountSats = effectiveRequestedAmountSats,
                paymentAmountSats = paymentAmountSats,
                previewFundingAddress = getLightningPreviewFundingAddress(),
                estimatedSwapFeeSats = estimatedSwapFeeSats,
                estimatedLockupAmountSats = safeAddSats(paymentAmountSats, estimatedSwapFeeSats),
            )
        } finally {
            logTiming("buildLightningPaymentReviewContext", startedAt)
        }
    }

    private fun normalizeLightningInvoiceError(error: Exception): Exception {
        val message = error.message ?: return error
        val maxMatch = Regex("""(\d+)\s+exceeds maximal of\s+(\d+)""").find(message)
        if (maxMatch != null) {
            val maxAmount = maxMatch.groupValues[2].toLongOrNull()
            if (maxAmount != null) {
                return Exception("Amount exceeds Boltz maximum of ${formatSatsForDisplay(maxAmount)}.")
            }
        }
        return error
    }

    private fun isNoBoltzUpdate(error: LwkException): Boolean {
        return error.javaClass.simpleName == "NoBoltzUpdate" ||
            error.javaClass.name == "lwk.LwkException" + "$" + "NoBoltzUpdate"
    }

    private fun isBoltzTransientProgressError(error: LwkException): Boolean {
        if (isNoBoltzUpdate(error)) {
            return true
        }
        return error.message?.contains("RetryBroadcastFailed", ignoreCase = true) == true
    }

    private suspend fun destroyBoltzSession(persistNextIndex: Boolean = true) = boltzSessionMutex.withLock {
        if (persistNextIndex) {
            currentWalletId?.let { walletId ->
                lwkBoltzSession?.let { session ->
                    persistBoltzSessionNextIndex(walletId, session)
                }
            }
        }
        try {
            lwkBoltzSession?.destroy()
        } catch (_: Exception) {}
        lwkBoltzSession = null
        try {
            lwkBoltzAnyClient?.destroy()
        } catch (_: Exception) {}
        lwkBoltzAnyClient = null
        boltzSessionKeepAliveUntilMs = 0L
    }

    suspend fun invalidateBoltzSessionState() = withContext(Dispatchers.IO) {
        withBoltzOperationLock(operation = "invalidateBoltzSessionState") {
            resetBoltzSessionStateLocked()
        }
    }

    suspend fun discardPreparedBoltzChainSwap(swapId: String?) = withContext(Dispatchers.IO) {
        swapId?.let {
            cleanupLockupResponse(it)
            getBoltzChainSwapDraftBySwapId(it)?.let { draft ->
                deleteBoltzChainSwapDraft(draft.draftId)
            }
            destroyBoltzSessionIfIdle("chain_review_discarded")
        }
    }

    private suspend fun getBoltzChainPairInfo(direction: SwapDirection): BoltzPairInfo {
        ensureBoltzEnabled()
        return boltzProvider.getChainPairInfo(direction)
    }

    private suspend fun getBoltzSubmarinePairInfo(): BoltzPairInfo {
        ensureBoltzEnabled()
        return boltzProvider.getSubmarinePairInfo()
    }

    private suspend fun ensureSideSwapTorReadyIfNeeded() {
        if (secureStorage.getSideSwapApiSource() != SecureStorage.SIDESWAP_API_TOR) return
        if (!torManager.isReady()) {
            torManager.start()
            if (!torManager.awaitReady(30_000L)) {
                throw Exception("Tor connection timed out")
            }
            delay(500)
        }
    }

    private fun ensureBoltzEnabled() {
        if (!isBoltzEnabled()) {
            throw Exception("Boltz API is disabled. Lightning swaps are unavailable.")
        }
    }

    private fun shouldRetryLightningInvoiceCreation(error: Exception): Boolean {
        val message = error.message ?: return false
        return error is LwkException &&
            (
                message.contains("Invoice failed: Timeout(") ||
                    isBoltzNextIndexCollision(error)
            )
    }

    private fun shouldRetryBoltzChainSwapCreation(error: Exception): Boolean {
        val message = error.message ?: return false
        return error is LwkException &&
            (
                message.contains("Timeout(", ignoreCase = true) ||
                    isBoltzNextIndexCollision(error)
            )
    }

    private fun isBoltzNextIndexCollision(error: Exception): Boolean {
        val message = error.message ?: return false
        return error is LwkException &&
            message.contains("preimage hash exists already", ignoreCase = true)
    }

    private fun clearBoltzSessionNextIndexCache() {
        currentWalletId?.let { walletId ->
            secureStorage.setBoltzSessionNextIndex(walletId, null)
        }
    }

    private fun hasActiveBoltzResponses(): Boolean {
        return invoiceResponses.isNotEmpty() || preparePayResponses.isNotEmpty() || lockupResponses.isNotEmpty()
    }

    private fun advanceBoltzPaymentState(
        operation: String,
        advance: () -> PaymentState,
    ): PaymentState {
        return try {
            advance()
        } catch (error: LwkException) {
            if (isBoltzTransientProgressError(error)) {
                if (BuildConfig.DEBUG) {
                    val suffix =
                        if (isNoBoltzUpdate(error)) {
                            "no Boltz update yet"
                        } else {
                            "transient progress error: ${error.message}"
                        }
                    Log.d(TAG, "$operation: $suffix")
                }
                PaymentState.CONTINUE
            } else {
                throw error
            }
        }
    }

    private suspend fun resetBoltzSessionStateLocked() {
        invoiceResponses.keys.toList().forEach(::cleanupInvoiceResponse)
        preparePayResponses.keys.toList().forEach(::cleanupPreparePayResponse)
        lockupResponses.keys.toList().forEach(::cleanupLockupResponse)
        lightningResolutionCache.clear()
        boltzRuntime.invalidate()
        destroyBoltzSession()
    }

    private fun persistBoltzSessionNextIndex(walletId: String, session: BoltzSession) {
        val nextIndex = runCatching { session.nextIndexToUse().toInt() }.getOrNull()
        secureStorage.setBoltzSessionNextIndex(walletId, nextIndex)
    }

    private fun deriveBoltzRescueMnemonic(signer: Signer): Mnemonic {
        return signer.deriveBip85Mnemonic(
            index = BOLTZ_RESCUE_MNEMONIC_BIP85_INDEX,
            wordCount = BOLTZ_RESCUE_MNEMONIC_WORD_COUNT,
        )
    }

    private suspend fun <T> withBoltzOperationLock(
        operation: String,
        swapId: String? = null,
        block: suspend () -> T,
    ): T {
        val startedAt = boltzTraceStart()
        return boltzOperationMutex.withLock {
            val waitedMs = boltzElapsedMs(startedAt)
            if (waitedMs >= BOLTZ_OPERATION_LOCK_LOG_THRESHOLD_MS) {
                logBoltzTrace(
                    "lock_wait",
                    BoltzTraceContext(
                        operation = operation,
                        swapId = swapId,
                        viaTor = secureStorage.getBoltzApiSource() == SecureStorage.BOLTZ_API_TOR,
                        source = "lwk",
                    ),
                    "waitedMs" to waitedMs,
                )
            }
            block()
        }
    }

    private fun persistLightningInvoiceSession(session: PendingLightningInvoiceSession) {
        val walletId = currentWalletId ?: return
        secureStorage.savePendingLightningInvoiceSession(walletId, session)
    }

    private fun clearPendingLightningInvoiceSession() {
        val walletId = currentWalletId ?: return
        secureStorage.deletePendingLightningInvoiceSession(walletId)
    }

    private suspend fun ensureLightningInvoiceResponse(swapId: String): InvoiceResponse {
        invoiceResponses[swapId]?.let { return it }
        val session = getPendingLightningInvoiceSession()
            ?.takeIf { it.swapId == swapId }
            ?: throw Exception("Invoice swap not found")
        return ensureBoltzSession().restoreInvoice(session.snapshot).also {
            invoiceResponses[swapId] = it
        }
    }

    private fun persistPreparedLightningPaymentSession(session: PendingLightningPaymentSession) {
        val walletId = currentWalletId ?: return
        secureStorage.savePendingLightningPaymentSession(walletId, session)
    }

    private fun clearPendingPreparedLightningPaymentSession() {
        val walletId = currentWalletId ?: return
        secureStorage.deletePendingLightningPaymentSession(walletId)
    }

    private suspend fun ensurePreparedLightningPaymentResponse(swapId: String): PreparePayResponse {
        preparePayResponses[swapId]?.let { return it }
        val session = getPendingLightningPaymentSession()
            ?.takeIf { it.swapId == swapId }
            ?: throw Exception("Lightning payment swap not found")
        val snapshot = session.snapshot
            ?: throw Exception("Prepared Lightning payment snapshot is unavailable for this swap backend")
        return ensureBoltzSession().restorePreparePay(snapshot).also {
            preparePayResponses[swapId] = it
        }
    }

    suspend fun createLightningInvoice(
        amountSats: Long,
        label: String?,
    ): LightningInvoiceCreation = withContext(Dispatchers.IO) {
        require(amountSats > 0) { "Amount must be greater than zero" }
        val walletId = currentWalletId ?: throw Exception("Wallet not loaded")
        val startedAt = System.currentTimeMillis()
        val traceStartedAt = boltzTraceStart()
        val trace =
            BoltzTraceContext(
                operation = "createLightningInvoice",
                viaTor = secureStorage.getBoltzApiSource() == SecureStorage.BOLTZ_API_TOR,
                source = "lwk",
            )

        val limits = boltzRuntime.getLightningInvoiceLimits()
        validateBoltzLightningAmountLimits(amountSats, limits)
        logBoltzTrace("start", trace, "amountSats" to amountSats)

        try {
            val claimAddress = generateFreshAddress() ?: throw Exception("No Liquid address available")
            val trimmedLabel = label?.trim().orEmpty()
            val response = createBoltzLightningInvoiceResponse(amountSats, claimAddress)
            val swapId = response.swapId()
            val snapshot = response.serialize()
            invoiceResponses[swapId] = response
            secureStorage.savePendingLightningReceive(
                walletId = walletId,
                swapId = swapId,
                claimAddress = claimAddress,
                label = trimmedLabel,
            )
            persistLightningInvoiceSession(
                PendingLightningInvoiceSession(
                    swapId = swapId,
                    snapshot = snapshot,
                    amountSats = amountSats,
                ),
            )
            LightningInvoiceCreation(
                swapId = swapId,
                invoice = response.bolt11Invoice().toString(),
                claimAddress = claimAddress,
                amountSats = amountSats,
            )
        } catch (e: Exception) {
            logBoltzTrace(
                "failed",
                trace,
                level = BoltzTraceLevel.WARN,
                throwable = e,
                "elapsedMs" to boltzElapsedMs(traceStartedAt),
                "amountSats" to amountSats,
            )
            throw normalizeLightningInvoiceError(e)
        } finally {
            logBoltzTrace("finished", trace, "elapsedMs" to boltzElapsedMs(traceStartedAt), "amountSats" to amountSats)
            logTiming("createLightningInvoice", startedAt)
        }
    }

    private suspend fun createBoltzLightningInvoiceResponse(
        amountSats: Long,
        claimAddress: String,
    ): InvoiceResponse {
        val startedAt = System.currentTimeMillis()
        val traceStartedAt = boltzTraceStart()
        val address = Address(claimAddress)
        val trace =
            BoltzTraceContext(
                operation = "createBoltzLightningInvoiceResponse",
                viaTor = secureStorage.getBoltzApiSource() == SecureStorage.BOLTZ_API_TOR,
                source = "lwk",
            )
        logBoltzTrace("start", trace.copy(attempt = 1), "amountSats" to amountSats, "claimAddress" to summarizeValue(claimAddress))
        return withBoltzOperationLock(operation = "createBoltzLightningInvoiceResponse") {
            val walletId = currentWalletId ?: throw Exception("Wallet not loaded")
            fun createInvoiceWithSession(session: BoltzSession, attempt: Int): InvoiceResponse {
                return session.invoice(
                    amountSats.toULong(),
                    // Keep labels local so they can be attached to the final L-BTC tx
                    // without depending on Boltz's optional description handling.
                    null,
                    address,
                    null,
                ).also {
                    persistBoltzSessionNextIndex(walletId, session)
                    logBoltzTrace(
                        "success",
                        trace.copy(attempt = attempt),
                        "elapsedMs" to boltzElapsedMs(traceStartedAt),
                        "amountSats" to amountSats,
                        "swapId" to it.swapId(),
                    )
                }
            }
            try {
                createInvoiceWithSession(ensureBoltzSession(), attempt = 1)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (!shouldRetryLightningInvoiceCreation(e)) {
                    logBoltzTrace(
                        "failed",
                        trace.copy(attempt = 1),
                        level = BoltzTraceLevel.WARN,
                        throwable = e,
                        "elapsedMs" to boltzElapsedMs(traceStartedAt),
                        "amountSats" to amountSats,
                    )
                    throw e
                }
                val retryReason =
                    if (isBoltzNextIndexCollision(e)) {
                        "next_index_collision"
                    } else {
                        "timeout"
                    }
                logBoltzTrace(
                    "retrying",
                    trace.copy(attempt = 2),
                    level = BoltzTraceLevel.WARN,
                    throwable = e,
                    "elapsedMs" to boltzElapsedMs(traceStartedAt),
                    "amountSats" to amountSats,
                    "reason" to retryReason,
                )
                if (isBoltzNextIndexCollision(e)) {
                    clearBoltzSessionNextIndexCache()
                }
                if (hasActiveBoltzResponses()) {
                    logBoltzTrace(
                        "retry_skipped",
                        trace.copy(attempt = 2),
                        level = BoltzTraceLevel.WARN,
                        throwable = e,
                        "elapsedMs" to boltzElapsedMs(traceStartedAt),
                        "amountSats" to amountSats,
                        "reason" to "$retryReason.active_responses",
                    )
                    throw e
                }
                logWarn(
                    if (isBoltzNextIndexCollision(e)) {
                        "Boltz invoice creation hit a stale next-index collision, retrying with uncached session state"
                    } else {
                        "Boltz invoice creation timed out, retrying with fresh session"
                    },
                    e,
                )
                destroyBoltzSession(persistNextIndex = !isBoltzNextIndexCollision(e))
                createInvoiceWithSession(ensureBoltzSession(), attempt = 2)
            } finally {
                logTiming("createBoltzLightningInvoiceResponse", startedAt)
            }
        }
    }

    suspend fun advanceLightningInvoice(swapId: String): PaymentState = withContext(Dispatchers.IO) {
        val (state, snapshot) =
            withBoltzOperationLock(operation = "advanceLightningInvoice", swapId = swapId) {
                val response = ensureLightningInvoiceResponse(swapId)
                advanceBoltzPaymentState("advanceLightningInvoice") {
                    response.advance()
                } to response.serialize()
            }
        getPendingLightningInvoiceSession()
            ?.takeIf { it.swapId == swapId }
            ?.let { persistLightningInvoiceSession(it.copy(snapshot = snapshot)) }
        state
    }

    suspend fun finishLightningInvoice(swapId: String): String? = withContext(Dispatchers.IO) {
        val result =
            withBoltzOperationLock(operation = "finishLightningInvoice", swapId = swapId) {
                val response = ensureLightningInvoiceResponse(swapId)
                val snapshot = response.serialize()
                try {
                    response.completePay()
                    response.claimTxid().orEmpty()
                } catch (error: LwkException) {
                    if (!isBoltzTransientProgressError(error)) {
                        throw error
                    }
                    if (BuildConfig.DEBUG) Log.d(TAG, "finishLightningInvoice: waiting for more Boltz activity")
                    runCatching { response.destroy() }
                    invoiceResponses[swapId] = ensureBoltzSession().restoreInvoice(snapshot)
                    null
                }
            }
        if (result == null) {
            return@withContext null
        }
        cleanupInvoiceResponse(swapId)
        clearPendingLightningInvoiceSession()
        destroyBoltzSessionIfIdle("lightning_invoice_completed")
        result
    }

    suspend fun failLightningInvoice(
        swapId: String,
        clearPendingMetadata: Boolean = true,
    ) = withContext(Dispatchers.IO) {
        cleanupInvoiceResponse(swapId)
        if (clearPendingMetadata) {
            clearPendingLightningInvoiceSession()
            currentWalletId?.let { walletId ->
                secureStorage.deletePendingLightningReceive(walletId, swapId)
            }
        }
        destroyBoltzSessionIfIdle(
            if (clearPendingMetadata) {
                "lightning_invoice_cleared"
            } else {
                "lightning_invoice_failed"
            },
        )
    }

    suspend fun awaitBoltzSwapActivity(
        swapId: String,
        timeoutMs: Long = 5_000L,
        previousUpdate: BoltzSwapUpdate? = null,
    ): BoltzSwapUpdate? = withContext(Dispatchers.IO) {
        ensureBoltzEnabled()
        ensureBoltzTorReadyIfNeeded()
        boltzProvider.awaitSwapActivity(
            swapId = swapId,
            timeoutMs = timeoutMs,
            mode = BoltzActivityMode.FALLBACK_STATUS,
            previousUpdate = previousUpdate,
        ) {
            boltzClient.getSwapStatus(swapId)
        }
    }

    suspend fun prepareLightningPaymentForConfirmation(
        executionPlan: LightningPaymentExecutionPlan,
    ): LightningPaymentExecutionPlan = withContext(Dispatchers.IO) {
        val startedAt = System.currentTimeMillis()
        try {
            when (executionPlan) {
                is LightningPaymentExecutionPlan.BoltzQuote -> prepareLightningPaymentExecutionPlan(executionPlan)
                else -> executionPlan
            }
        } finally {
            logTiming("prepareLightningPaymentForConfirmation", startedAt)
        }
    }

    private suspend fun prepareLightningPaymentExecutionPlan(
        executionPlan: LightningPaymentExecutionPlan.BoltzQuote,
    ): LightningPaymentExecutionPlan = withContext(Dispatchers.IO) {
        val startedAt = System.currentTimeMillis()
        val traceStartedAt = boltzTraceStart()
        val requestKey = buildLightningPaymentRequestKey(executionPlan.paymentInput, executionPlan.requestedAmountSats)
        val trace =
            BoltzTraceContext(
                operation = "prepareLightningPaymentExecutionPlan",
                requestKey = requestKey,
                viaTor = secureStorage.getBoltzApiSource() == SecureStorage.BOLTZ_API_TOR,
            )
        logBoltzTrace(
            "start",
            trace,
            "amountSats" to executionPlan.paymentAmountSats,
            "requestedAmountSats" to executionPlan.requestedAmountSats,
        )
        findReusablePreparedLightningPaymentSession(requestKey)?.let {
            if (it.backend == LightningPaymentBackend.LWK_PREPARE_PAY) {
                withBoltzOperationLock(operation = "prepareLightningPaymentExecutionPlan", swapId = it.swapId) {
                    ensurePreparedLightningPaymentResponse(it.swapId)
                }
            }
            logBoltzTrace(
                "reuse",
                trace.copy(swapId = it.swapId, backend = it.backend.name, session = "warm"),
                "elapsedMs" to boltzElapsedMs(traceStartedAt),
            )
            logDebug(
                "prepareLightningPaymentExecutionPlan reuse prepared session requestKey=$requestKey swapId=${it.swapId}",
            )
            logTiming("prepareLightningPaymentExecutionPlan[reused]", startedAt)
            return@withContext buildPreparedLightningExecutionPlan(it)
        }
        discardPreparedLightningPaymentReview()
        val resolvedPayment =
            getCachedLightningPaymentResolution(executionPlan.paymentInput, executionPlan.requestedAmountSats)
                ?: resolveBoltzLightningPaymentInput(executionPlan.paymentInput, executionPlan.requestedAmountSats)
        try {
            val lwkPaymentInput = resolvedPayment.fetchedInvoice ?: executionPlan.resolvedPaymentInput
            val refundAddress = getNewAddress() ?: throw Exception("No Liquid refund address available")
            val payment = createLightningPayment(lwkPaymentInput)
            logBoltzTrace(
                "prepare_lwk",
                trace.copy(backend = LightningPaymentBackend.LWK_PREPARE_PAY.name, source = "lwk"),
                "refundAddress" to summarizeValue(refundAddress),
                "payment" to summarizeValue(lwkPaymentInput),
                "hasFetchedInvoice" to (resolvedPayment.fetchedInvoice != null),
            )
            return@withContext withBoltzOperationLock(operation = "prepareLightningPaymentExecutionPlan") {
                val walletId = currentWalletId ?: throw Exception("Wallet not loaded")
                val boltzSession = ensureBoltzSession()
                val response = boltzSession.preparePay(payment, Address(refundAddress), null)
                persistBoltzSessionNextIndex(walletId, boltzSession)
                val swapId = response.swapId()
                val snapshot = response.serialize()
                val quotedLockupAmountSats = response.uriAmount().toLong()
                val swapFeeSats = response.fee()?.toLong() ?: executionPlan.swapFeeSats
                val inferredPaymentAmountSats = (quotedLockupAmountSats - swapFeeSats).coerceAtLeast(0L)
                val paymentAmountSats = executionPlan.paymentAmountSats.takeIf { it > 0L } ?: inferredPaymentAmountSats
                val requiredLockupAmountSats = safeAddSats(paymentAmountSats, swapFeeSats)
                val lockupAmountSats = maxOf(quotedLockupAmountSats, requiredLockupAmountSats)
                val effectiveSwapFeeSats = (lockupAmountSats - paymentAmountSats).coerceAtLeast(0L)
                preparePayResponses[swapId] = response
                val session =
                    PendingLightningPaymentSession(
                        swapId = swapId,
                        backend = LightningPaymentBackend.LWK_PREPARE_PAY,
                        requestKey = requestKey,
                        paymentInput = executionPlan.paymentInput,
                        lockupAddress = response.uriAddress().toString(),
                        lockupAmountSats = lockupAmountSats,
                        refundAddress = refundAddress,
                        snapshot = snapshot,
                        requestedAmountSats = executionPlan.requestedAmountSats,
                        resolvedPaymentInput = executionPlan.resolvedPaymentInput,
                        fetchedInvoice = resolvedPayment.fetchedInvoice,
                        paymentAmountSats = paymentAmountSats,
                        swapFeeSats = effectiveSwapFeeSats,
                        phase = PendingLightningPaymentPhase.PREPARED,
                        status = "Prepared Lightning payment. Awaiting funding.",
                    )
                persistPreparedLightningPaymentSession(session)
                logBoltzTrace(
                    "prepared",
                    trace.copy(
                        swapId = swapId,
                        backend = LightningPaymentBackend.LWK_PREPARE_PAY.name,
                        source = "lwk",
                    ),
                    "elapsedMs" to boltzElapsedMs(traceStartedAt),
                    "lockupAmountSats" to lockupAmountSats,
                    "paymentAmountSats" to paymentAmountSats,
                    "swapFeeSats" to effectiveSwapFeeSats,
                    "hasFetchedInvoice" to (resolvedPayment.fetchedInvoice != null),
                )
                LightningPaymentExecutionPlan.BoltzSwap(
                    paymentInput = executionPlan.paymentInput,
                    backend = LightningPaymentBackend.LWK_PREPARE_PAY,
                    requestKey = requestKey,
                    resolvedPaymentInput = executionPlan.resolvedPaymentInput,
                    requestedAmountSats = executionPlan.requestedAmountSats,
                    swapId = swapId,
                    lockupAddress = response.uriAddress().toString(),
                    lockupAmountSats = lockupAmountSats,
                    refundAddress = refundAddress,
                    fetchedInvoice = resolvedPayment.fetchedInvoice,
                    refundPublicKey = null,
                    paymentAmountSats = paymentAmountSats,
                    swapFeeSats = effectiveSwapFeeSats,
                )
            }
        } catch (e: LwkException.MagicRoutingHint) {
            logBoltzTrace(
                "direct_liquid_fallback",
                trace.copy(source = "magic_routing_hint"),
                level = BoltzTraceLevel.INFO,
                throwable = e,
                "elapsedMs" to boltzElapsedMs(traceStartedAt),
            )
            val direct = parseMagicRoutingHintPayment(e)
            LightningPaymentExecutionPlan.DirectLiquid(
                paymentInput = executionPlan.paymentInput,
                address = direct.address,
                amountSats = direct.amountSats,
            )
        } catch (e: Exception) {
            if (resolvedPayment.fetchedInvoice != null && e !is LwkException.MagicRoutingHint) {
                logBoltzTrace(
                    "lwk_failed_falling_back_to_rest",
                    trace,
                    level = BoltzTraceLevel.WARN,
                    throwable = e,
                    "elapsedMs" to boltzElapsedMs(traceStartedAt),
                )
                return@withContext prepareRestSubmarineSwapFallback(
                    executionPlan = executionPlan,
                    resolvedPayment = resolvedPayment,
                    requestKey = requestKey,
                    trace = trace,
                    traceStartedAt = traceStartedAt,
                )
            }
            logBoltzTrace(
                "failed",
                trace,
                level = BoltzTraceLevel.WARN,
                throwable = e,
                "elapsedMs" to boltzElapsedMs(traceStartedAt),
            )
            throw normalizeLightningPaymentError(e, executionPlan.paymentInput, executionPlan.requestedAmountSats)
        } finally {
            logTiming("prepareLightningPaymentExecutionPlan[fromPreview]", startedAt)
        }
    }

    private suspend fun prepareRestSubmarineSwapFallback(
        executionPlan: LightningPaymentExecutionPlan.BoltzQuote,
        resolvedPayment: ResolvedLightningPaymentInput,
        requestKey: String,
        trace: BoltzTraceContext,
        traceStartedAt: Long,
    ): LightningPaymentExecutionPlan.BoltzSwap {
        val fetchedInvoice = resolvedPayment.fetchedInvoice
            ?: throw Exception("REST fallback requires a fetched invoice")
        val refundDetails = allocateBoltzSubmarineRefundDetails()
        logBoltzTrace(
            "prepare_rest_submarine_fallback",
            trace.copy(backend = LightningPaymentBackend.BOLTZ_REST_SUBMARINE.name, source = "rest"),
            "invoice" to summarizeValue(fetchedInvoice),
            "refundAddress" to summarizeValue(refundDetails.refundAddress),
        )
        val response =
            boltzProvider.createLegacySubmarineSwap(
                invoice = fetchedInvoice,
                refundPublicKey = refundDetails.refundPublicKey,
            )
        val paymentAmountSats =
            executionPlan.requestedAmountSats.takeIf { it != null && it > 0L }
                ?: executionPlan.paymentAmountSats
        val lockupAmountSats = maxOf(response.expectedAmount, safeAddSats(paymentAmountSats, 0L))
        val swapFeeSats = (lockupAmountSats - paymentAmountSats).coerceAtLeast(0L)
        val session =
            PendingLightningPaymentSession(
                swapId = response.id,
                backend = LightningPaymentBackend.BOLTZ_REST_SUBMARINE,
                requestKey = requestKey,
                paymentInput = executionPlan.paymentInput,
                lockupAddress = response.address,
                lockupAmountSats = lockupAmountSats,
                refundAddress = refundDetails.refundAddress,
                requestedAmountSats = executionPlan.requestedAmountSats,
                resolvedPaymentInput = resolvedPayment.paymentInput,
                fetchedInvoice = fetchedInvoice,
                refundPublicKey = refundDetails.refundPublicKey,
                paymentAmountSats = paymentAmountSats,
                swapFeeSats = swapFeeSats,
                phase = PendingLightningPaymentPhase.PREPARED,
                status = "Prepared Lightning payment. Awaiting funding.",
                boltzClaimPublicKey = response.claimPublicKey,
                timeoutBlockHeight = response.timeoutBlockHeight,
                swapTree = response.swapTree,
                blindingKey = response.blindingKey,
            )
        persistPreparedLightningPaymentSession(session)
        logBoltzTrace(
            "prepared",
            trace.copy(
                swapId = response.id,
                backend = LightningPaymentBackend.BOLTZ_REST_SUBMARINE.name,
                source = "rest",
            ),
            "elapsedMs" to boltzElapsedMs(traceStartedAt),
            "lockupAmountSats" to lockupAmountSats,
            "paymentAmountSats" to paymentAmountSats,
            "swapFeeSats" to swapFeeSats,
            "timeoutBlockHeight" to response.timeoutBlockHeight,
        )
        return LightningPaymentExecutionPlan.BoltzSwap(
            paymentInput = executionPlan.paymentInput,
            backend = LightningPaymentBackend.BOLTZ_REST_SUBMARINE,
            requestKey = requestKey,
            resolvedPaymentInput = resolvedPayment.paymentInput,
            requestedAmountSats = executionPlan.requestedAmountSats,
            swapId = response.id,
            lockupAddress = response.address,
            lockupAmountSats = lockupAmountSats,
            refundAddress = refundDetails.refundAddress,
            fetchedInvoice = fetchedInvoice,
            refundPublicKey = refundDetails.refundPublicKey,
            paymentAmountSats = paymentAmountSats,
            swapFeeSats = swapFeeSats,
        )
    }

    suspend fun resolveLightningPaymentPreview(
        paymentInput: String,
        requestedAmountSats: Long?,
        kind: LiquidSendKind,
        feeRateSatPerVb: Double,
        selectedUtxos: List<UtxoInfo>?,
        label: String?,
    ): LiquidSendPreview = withContext(Dispatchers.IO) {
        val startedAt = System.currentTimeMillis()
        val context = buildLightningPaymentReviewContext(paymentInput, requestedAmountSats)
        val reusableSession = findReusablePreparedLightningPaymentSession(context.requestKey)
        val executionPlan =
            reusableSession?.let(::buildPreparedLightningExecutionPlan)
                ?: LightningPaymentExecutionPlan.BoltzQuote(
                    paymentInput = context.normalizedPaymentInput,
                    resolvedPaymentInput = context.resolvedPaymentInput,
                    requestedAmountSats = context.requestedAmountSats,
                    previewFundingAddress = context.previewFundingAddress,
                    estimatedLockupAmountSats = context.estimatedLockupAmountSats,
                    paymentAmountSats = context.paymentAmountSats,
                    swapFeeSats = context.estimatedSwapFeeSats,
                    refundAddress = _liquidState.value.currentAddress,
                )
        val preview =
            buildLightningPreviewFromExecutionPlan(
                recipientDisplay = context.normalizedPaymentInput,
                executionPlan = executionPlan,
                kind = kind,
                feeRateSatPerVb = feeRateSatPerVb,
                selectedUtxos = selectedUtxos,
                label = label,
            )
        logTiming("resolveLightningPaymentPreview", startedAt)
        preview
    }

    suspend fun resolveLightningPaymentPreview(
        executionPlan: LightningPaymentExecutionPlan,
        kind: LiquidSendKind,
        feeRateSatPerVb: Double,
        selectedUtxos: List<UtxoInfo>?,
        label: String?,
    ): LiquidSendPreview = withContext(Dispatchers.IO) {
        val startedAt = System.currentTimeMillis()
        try {
            buildLightningPreviewFromExecutionPlan(
                recipientDisplay = executionPlan.paymentInput,
                executionPlan = executionPlan,
                kind = kind,
                feeRateSatPerVb = feeRateSatPerVb,
                selectedUtxos = selectedUtxos,
                label = label,
            )
        } finally {
            logTiming("resolveLightningPaymentPreview[fromPlan]", startedAt)
        }
    }

    private suspend fun buildLightningPreviewFromExecutionPlan(
        recipientDisplay: String,
        executionPlan: LightningPaymentExecutionPlan,
        kind: LiquidSendKind,
        feeRateSatPerVb: Double,
        selectedUtxos: List<UtxoInfo>?,
        label: String?,
    ): LiquidSendPreview {
        return if (executionPlan is LightningPaymentExecutionPlan.DirectLiquid) {
            val fundingPreview =
                previewLbtcSend(
                    address = executionPlan.address,
                    amountSats = executionPlan.amountSats,
                    feeRateSatPerVb = feeRateSatPerVb,
                    selectedUtxos = selectedUtxos,
                    label = label,
                )
            LiquidSendPreview(
                kind = kind,
                recipientDisplay = recipientDisplay,
                totalRecipientSats = executionPlan.amountSats,
                feeSats = fundingPreview.feeSats,
                feeRate = feeRateSatPerVb,
                availableSats = fundingPreview.availableSats,
                remainingSats = fundingPreview.remainingSats,
                label = label?.trim()?.takeIf { it.isNotBlank() },
                note = "This invoice can be completed without a swap. Ibis will pay it by sending L-BTC directly to the recipient.",
                selectedUtxoCount = fundingPreview.selectedUtxoCount,
                txVBytes = fundingPreview.txVBytes,
                executionPlan = executionPlan,
            )
        } else {
            var fundingAddress: String
            var fundingAmount: Long
            var paymentAmountSats: Long
            var swapFeeSats: Long
            var adjustedPlan: LightningPaymentExecutionPlan = executionPlan
            when (executionPlan) {
                is LightningPaymentExecutionPlan.BoltzQuote -> {
                    fundingAddress = executionPlan.previewFundingAddress
                    fundingAmount = executionPlan.estimatedLockupAmountSats
                    paymentAmountSats = executionPlan.paymentAmountSats
                    swapFeeSats = executionPlan.swapFeeSats
                }
                is LightningPaymentExecutionPlan.BoltzSwap -> {
                    fundingAddress = executionPlan.lockupAddress
                    fundingAmount = executionPlan.lockupAmountSats
                    paymentAmountSats = executionPlan.paymentAmountSats
                    swapFeeSats = executionPlan.swapFeeSats
                }
                is LightningPaymentExecutionPlan.DirectLiquid -> {
                    throw IllegalStateException("Direct Liquid execution plan already handled")
                }
            }

            val availableBalance =
                selectedUtxos
                    ?.takeIf { it.isNotEmpty() }
                    ?.sumOf { it.amountSats.toLong() }
                    ?: _liquidState.value.balanceSats.coerceAtLeast(0L)
            if (fundingAmount > availableBalance && availableBalance > 0L) {
                val maxLockup = getMaxSpendableLbtc(fundingAddress, feeRateSatPerVb, selectedUtxos)
                if (maxLockup > 0L) {
                    val pair = getBoltzSubmarinePairInfo()
                    val minerFee = pair.fees.serverMinerFee
                    val pct = pair.fees.percentage
                    val adjusted =
                        kotlin.math.floor((maxLockup - minerFee) * 100.0 / (100.0 + pct))
                            .toLong()
                            .coerceAtLeast(0L)
                    val verifyFee = estimateBoltzSubmarineSwapFee(adjusted, pair)
                    val verifyLockup = safeAddSats(adjusted, verifyFee)
                    paymentAmountSats = if (verifyLockup > maxLockup) (adjusted - 1).coerceAtLeast(0L) else adjusted
                    swapFeeSats = estimateBoltzSubmarineSwapFee(paymentAmountSats, pair)
                    fundingAmount = safeAddSats(paymentAmountSats, swapFeeSats)
                    if (executionPlan is LightningPaymentExecutionPlan.BoltzQuote) {
                        adjustedPlan = executionPlan.copy(
                            paymentAmountSats = paymentAmountSats,
                            swapFeeSats = swapFeeSats,
                            estimatedLockupAmountSats = fundingAmount,
                        )
                    }
                }
            }

            val fundingPreview =
                previewLbtcSend(
                    address = fundingAddress,
                    amountSats = fundingAmount,
                    feeRateSatPerVb = feeRateSatPerVb,
                    selectedUtxos = selectedUtxos,
                    label = label,
                )
            val fundingFeeSats = fundingPreview.feeSats ?: 0L
            val totalFeeSats = swapFeeSats + fundingFeeSats
            LiquidSendPreview(
                kind = kind,
                recipientDisplay = recipientDisplay,
                totalRecipientSats = paymentAmountSats,
                feeSats = totalFeeSats,
                feeRate = feeRateSatPerVb,
                availableSats = fundingPreview.availableSats,
                remainingSats = fundingPreview.remainingSats,
                label = label?.trim()?.takeIf { it.isNotBlank() },
                note =
                    if (adjustedPlan is LightningPaymentExecutionPlan.BoltzQuote) {
                        "Boltz will pay this lightning invoice using L-BTC->LN swap service."
                    } else {
                        "Boltz will pay this using L-BTC."
                    },
                selectedUtxoCount = fundingPreview.selectedUtxoCount,
                txVBytes = fundingPreview.txVBytes,
                executionPlan = adjustedPlan,
            )
        }
    }

    suspend fun discardPreparedLightningPaymentReview() = withContext(Dispatchers.IO) {
        val session = getPendingLightningPaymentSession() ?: return@withContext
        if (session.phase == PendingLightningPaymentPhase.PREPARED) {
            failPreparedLightningPayment(session.swapId)
        }
    }

    private suspend fun findReusablePreparedLightningPaymentSession(
        requestKey: String,
    ): PendingLightningPaymentSession? {
        val session = getPendingLightningPaymentSession() ?: return null
        if (BullBoltzBehavior.shouldReusePreparedLightningPayment(session, requestKey)) {
            return session
        }
        if (BullBoltzBehavior.shouldDiscardPreparedLightningPayment(session, requestKey)) {
            failPreparedLightningPayment(session.swapId)
        }
        return null
    }

    private fun buildPreparedLightningExecutionPlan(
        session: PendingLightningPaymentSession,
    ): LightningPaymentExecutionPlan.BoltzSwap {
        return LightningPaymentExecutionPlan.BoltzSwap(
            paymentInput = session.paymentInput,
            backend = session.backend,
            requestKey = session.requestKey,
            resolvedPaymentInput = session.resolvedPaymentInput ?: session.paymentInput,
            requestedAmountSats = session.requestedAmountSats,
            swapId = session.swapId,
            lockupAddress = session.lockupAddress,
            lockupAmountSats = session.lockupAmountSats,
            refundAddress = session.refundAddress,
            fetchedInvoice = session.fetchedInvoice,
            refundPublicKey = session.refundPublicKey,
            paymentAmountSats = session.paymentAmountSats,
            swapFeeSats = session.swapFeeSats,
        )
    }

    private fun validateBoltzLightningAmountLimits(
        amountSats: Long,
        limits: github.aeonbtc.ibiswallet.data.model.BoltzLimits,
    ) {
        if (amountSats < limits.minimal) {
            throw Exception("Amount is below Boltz minimum of ${formatSatsForDisplay(limits.minimal)}.")
        }
        if (limits.maximal > 0 && amountSats !in limits.minimal..limits.maximal) {
            throw Exception("Amount exceeds Boltz maximum of ${formatSatsForDisplay(limits.maximal)}.")
        }
    }

    private suspend fun resolveBoltzLightningPaymentInput(
        paymentInput: String,
        requestedAmountSats: Long?,
    ): ResolvedLightningPaymentInput {
        val startedAt = System.currentTimeMillis()
        val traceStartedAt = boltzTraceStart()
        val trace =
            BoltzTraceContext(
                operation = "resolveBoltzLightningPaymentInput",
                viaTor = secureStorage.getBoltzApiSource() == SecureStorage.BOLTZ_API_TOR,
            )
        logBoltzTrace(
            "start",
            trace,
            "input" to summarizeValue(paymentInput),
            "requestedAmountSats" to requestedAmountSats,
        )
        getCachedLightningPaymentResolution(paymentInput, requestedAmountSats)?.let { cached ->
            logBoltzTrace(
                "cache_hit",
                trace.copy(cache = "hit"),
                "elapsedMs" to boltzElapsedMs(traceStartedAt),
                "input" to summarizeValue(paymentInput),
                "requestedAmountSats" to requestedAmountSats,
            )
            logDebug(
                "resolveBoltzLightningPaymentInput cache hit amount=${requestedAmountSats ?: -1L} " +
                    "input=${summarizeValue(paymentInput)}",
            )
            logTiming("resolveBoltzLightningPaymentInput[cached]", startedAt)
            return cached
        }
        val parsedPayment = runCatching { Payment(paymentInput) }.getOrNull()
        val bip353Address = parsedPayment?.bip353()?.takeIf { it.isNotBlank() } ?: normalizedBip353Address(paymentInput)
        val lightningInput =
            when {
                parsedPayment?.kind() == PaymentKind.BIP353 || bip353Address != null -> {
                    ensureBoltzTorReadyIfNeeded()
                    val bip353StartedAt = System.currentTimeMillis()
                    val bip353TraceStartedAt = boltzTraceStart()
                    logBoltzTrace(
                        "resolve_bip353_start",
                        trace.copy(source = "bip353"),
                        "address" to summarizeValue(bip353Address ?: paymentInput),
                    )
                    val resolution = bip353Resolver.resolve(bip353Address ?: paymentInput)
                    logBoltzTrace(
                        "resolve_bip353_success",
                        trace.copy(source = "bip353"),
                        "elapsedMs" to boltzElapsedMs(bip353TraceStartedAt),
                        "resolvedUri" to summarizeValue(resolution.bitcoinUri),
                    )
                    logTiming("resolveBoltzLightningPaymentInput[bip353]", bip353StartedAt)
                    resolveBip353LightningPaymentInput(
                        bitcoinUri = resolution.bitcoinUri,
                        requestedAmountSats = requestedAmountSats,
                    )
                }
                else -> ResolvedLightningPaymentInput(paymentInput = paymentInput, requestedAmountSats = requestedAmountSats)
            }
        val resolvedPayment = runCatching { Payment(lightningInput.paymentInput) }.getOrNull()
        val isBolt12Offer =
            resolvedPayment?.kind() == PaymentKind.LIGHTNING_OFFER ||
                lightningInput.paymentInput.startsWith("lno", ignoreCase = true)
        if (!isBolt12Offer || lightningInput.requestedAmountSats == null) {
            cacheLightningPaymentResolution(paymentInput, requestedAmountSats, lightningInput)
            logBoltzTrace(
                "resolved_without_fetch",
                trace,
                "elapsedMs" to boltzElapsedMs(traceStartedAt),
                "source" to if (bip353Address != null) "bip353" else "direct",
                "resolvedInput" to summarizeValue(lightningInput.paymentInput),
                "requestedAmountSats" to lightningInput.requestedAmountSats,
            )
            logTiming("resolveBoltzLightningPaymentInput", startedAt)
            return lightningInput
        }
        ensureBoltzTorReadyIfNeeded()
        logBoltzTrace(
            "fetch_bolt12_start",
            trace.copy(source = if (bip353Address != null) "bip353" else "offer"),
            "amountSats" to lightningInput.requestedAmountSats,
            "input" to summarizeValue(lightningInput.paymentInput),
        )
        try {
            val fetchedInvoice =
                boltzProvider.fetchBolt12Invoice(
                    offer = lightningInput.paymentInput,
                    amountSats = lightningInput.requestedAmountSats,
                )
            bolt12OfferVerifier.verifyFetchedInvoice(
                offerString = lightningInput.paymentInput,
                invoiceString = fetchedInvoice,
            )
            logBoltzTrace(
                "fetch_bolt12_success",
                trace.copy(source = if (bip353Address != null) "bip353" else "offer"),
                "elapsedMs" to boltzElapsedMs(traceStartedAt),
                "invoice" to summarizeValue(fetchedInvoice),
            )
            val resolvedInput = lightningInput.copy(
                requestedAmountSats = lightningInput.requestedAmountSats,
                fetchedInvoice = fetchedInvoice,
            )
            cacheLightningPaymentResolution(paymentInput, requestedAmountSats, resolvedInput)
            logTiming("resolveBoltzLightningPaymentInput", startedAt)
            return resolvedInput
        } catch (e: Exception) {
            logBoltzTrace(
                "fetch_bolt12_failed",
                trace.copy(source = if (bip353Address != null) "bip353" else "offer"),
                level = BoltzTraceLevel.WARN,
                throwable = e,
                "elapsedMs" to boltzElapsedMs(traceStartedAt),
                "requestedAmountSats" to lightningInput.requestedAmountSats,
            )
            logWarn(
                "resolveBoltzLightningPaymentInput failed elapsedMs=${System.currentTimeMillis() - startedAt} " +
                    "source=${if (bip353Address != null) "BIP353" else "offer"} amount=${lightningInput.requestedAmountSats}",
                e,
            )
            throw e
        }
    }

    private fun resolveBip353LightningPaymentInput(
        bitcoinUri: String,
        requestedAmountSats: Long?,
    ): ResolvedLightningPaymentInput {
        val parsedPayment = runCatching { Payment(bitcoinUri) }.getOrNull()
        val queryParams = parseOpaqueUriQueryParameters(bitcoinUri)
        val resolvedAmountSats =
            requestedAmountSats
                ?: parsedPayment?.bip21()?.amount()?.toLong()
                ?: parseBitcoinAmountSats(queryParams["amount"])
        val offer =
            queryParams["lno"]?.takeIf { it.isNotBlank() }
                ?: queryParams["offer"]?.takeIf { it.isNotBlank() }
                ?: parsedPayment?.bip21()?.offer()?.takeIf { it.isNotBlank() }
                ?: parsedPayment?.lightningOffer()?.takeIf { it.isNotBlank() }
        if (offer != null) {
            return ResolvedLightningPaymentInput(
                paymentInput = offer,
                requestedAmountSats = resolvedAmountSats ?: extractBolt12OfferAmountSats(offer),
            )
        }

        val invoice =
            parsedPayment?.bip21()?.lightning()?.toString()
                ?: parsedPayment?.lightningInvoice()?.toString()
        if (invoice != null) {
            return ResolvedLightningPaymentInput(
                paymentInput = invoice,
                requestedAmountSats = resolvedAmountSats ?: extractLightningPaymentAmountSats(invoice),
            )
        }

        throw Exception("BIP-353 recipient did not resolve to a Lightning payment")
    }

    private fun createLightningPayment(paymentInput: String): LightningPayment {
        return runCatching { LightningPayment(paymentInput) }
            .recoverCatching {
                Payment(paymentInput).lightningPayment()
                    ?: throw Exception("Unsupported Lightning payment")
            }
            .getOrThrow()
    }

    private fun extractLightningPaymentAmountSats(paymentInput: String): Long? {
        val payment = runCatching { Payment(paymentInput) }.getOrNull() ?: return null
        return payment.lightningInvoice()?.amountMilliSatoshis()?.let { msats ->
            (msats.toLong() + 999L) / 1000L
        }
    }

    private fun extractBolt12OfferAmountSats(offer: String): Long? {
        return runCatching {
            OfferTypes.Offer.decode(offer).get().amount?.let { msats ->
                (msats.toLong() + 999L) / 1000L
            }
        }.getOrNull()
    }

    private fun normalizeLightningPaymentInput(input: String): String {
        return input
            .trim()
            .removePrefix("lightning:")
            .removePrefix("LIGHTNING:")
    }

    private fun normalizeLightningPaymentError(
        error: Exception,
        paymentInput: String,
        requestedAmountSats: Long?,
    ): Exception {
        val message = error.message.orEmpty()
        val parsedPayment = runCatching { Payment(paymentInput) }.getOrNull()
        val isBip353 = parsedPayment?.kind() == PaymentKind.BIP353 || normalizedBip353Address(paymentInput) != null
        val isBolt12Offer =
            parsedPayment?.kind() == PaymentKind.LIGHTNING_OFFER ||
                paymentInput.startsWith("lno", ignoreCase = true)
        val isBolt12Like = isBolt12Offer || isBip353
        if (isBolt12Like && requestedAmountSats == null && message.contains("amount", ignoreCase = true)) {
            return Exception("This BOLT12 offer requires an amount. Enter the amount, then review again.")
        }
        if (
            isBolt12Like &&
            (
                error.hasCause<SocketTimeoutException>() ||
                    message.contains("Boltz API error 500: Timeout waiting for response", ignoreCase = true)
            )
        ) {
            return Exception(
                "Boltz did not receive an invoice response from this BOLT12 offer in time. " +
                    "The offer may be stale, the recipient may be offline, or the upstream onion-message fetch failed.",
                error,
            )
        }
        if (
            isBolt12Like &&
            (
                message.contains("does not belong to the requested offer", ignoreCase = true) ||
                    message.contains("invalid signature", ignoreCase = true)
            )
        ) {
            return Exception("Boltz returned a BOLT12 invoice that does not match the requested offer.", error)
        }
        if (isBolt12Like && message.contains("Bolt12Unsupported", ignoreCase = true)) {
            return Exception(
                "BOLT12 execution is not supported by the current Liquid swap backend on this build. " +
                    "The offer can be fetched and previewed, but the actual Boltz payment step is rejected upstream.",
                error,
            )
        }
        return if (message.isNotBlank()) {
            Exception(message, error)
        } else {
            Exception("Failed to prepare Lightning payment", error)
        }
    }

    private inline fun <reified T : Throwable> Throwable.hasCause(): Boolean {
        var current: Throwable? = this
        val visited = mutableSetOf<Throwable>()
        while (current != null && visited.add(current)) {
            if (current is T) return true
            current = current.cause
        }
        return false
    }

    private fun parseBitcoinAmountSats(amount: String?): Long? {
        if (amount.isNullOrBlank()) return null
        return runCatching {
            BigDecimal(amount.trim())
                .multiply(BigDecimal(100_000_000L))
                .setScale(0, java.math.RoundingMode.DOWN)
                .longValueExact()
        }.getOrNull()
    }

    private fun normalizedBip353Address(input: String): String? {
        val trimmed = input.trim().removePrefix("₿")
        if (trimmed.isBlank() || trimmed.contains("://") || trimmed.contains(":")) {
            return null
        }
        val parts = trimmed.split("@", limit = 3)
        return if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) {
            trimmed
        } else {
            null
        }
    }

    private fun parseMagicRoutingHintPayment(error: LwkException.MagicRoutingHint): DirectLightningPayment {
        val message = error.message
        val uri = message.substringAfter("uri=", "").trim()
        val hintedAddress = message.substringAfter("address=", "").substringBefore(",").trim()
        val hintedAmountSats = message.substringAfter("amount=", "").substringBefore(",").trim().toLongOrNull()
        if (uri.isBlank()) {
            throw Exception("Boltz returned a magic routing hint without a Liquid URI")
        }
        if (!uri.startsWith("liquidnetwork:", ignoreCase = true) && !uri.startsWith("liquid:", ignoreCase = true)) {
            throw Exception("Boltz returned an unsupported magic routing hint URI")
        }
        val address = uri.substringAfter(':').substringBefore('?').trim()
        if (address.isBlank()) {
            throw Exception("Boltz returned a magic routing hint without a Liquid address")
        }
        if (hintedAddress.isNotBlank() && !hintedAddress.equals(address, ignoreCase = true)) {
            throw Exception("Boltz returned inconsistent magic routing hint address data")
        }
        val queryParams = parseOpaqueUriQueryParameters(uri)
        val amountText = queryParams["amount"]
            ?: throw Exception("Boltz returned a magic routing hint without an amount")
        val amountSats = parseLiquidAmountToSats(amountText)
        if (hintedAmountSats != null && hintedAmountSats != amountSats) {
            throw Exception("Boltz returned inconsistent magic routing hint amount data")
        }
        val expectedAssetId = (lwkNetwork ?: Network.mainnet()).policyAsset()
        val assetId = queryParams["assetid"]
            ?: throw Exception("Boltz returned a magic routing hint without an asset id")
        if (!assetId.equals(expectedAssetId, ignoreCase = true)) {
            throw Exception("Boltz returned a magic routing hint for a different Liquid asset")
        }
        return DirectLightningPayment(
            address = address,
            amountSats = amountSats,
            uri = uri,
        )
    }

    private fun parseLiquidAmountToSats(amountText: String): Long {
        return BigDecimal(amountText)
            .movePointRight(8)
            .longValueExact()
    }

    private fun allocateBoltzSubmarineRefundDetails(): BoltzSubmarineRefundDetails {
        val walletId = currentWalletId ?: throw Exception("Wallet not loaded")
        val wollet = lwkWollet ?: throw Exception("Liquid wallet not loaded")
        val mnemonic = secureStorage.getMnemonic(walletId)
            ?: throw Exception("Liquid signer is unavailable for refund key derivation")
        val passphrase = secureStorage.getPassphrase(walletId)
        val refundAddressResult = wollet.address(null)
        val refundAddress = refundAddressResult.address().toString()
        val refundIndex = refundAddressResult.index().toLong()
        val seed = ElectrumSeedUtil.bip39MnemonicToSeed(mnemonic, passphrase)
        val refundPublicKey =
            ElectrumSeedUtil.deriveCompressedPublicKeyHex(
                seed = seed,
                path = "m/84'/1776'/0'/0/$refundIndex",
            )
        return BoltzSubmarineRefundDetails(
            refundAddress = refundAddress,
            refundPublicKey = refundPublicKey,
        )
    }

    private fun safeAddSats(left: Long, right: Long): Long {
        if (left <= 0L) return right.coerceAtLeast(0L)
        if (right <= 0L) return left.coerceAtLeast(0L)
        return if (Long.MAX_VALUE - left < right) Long.MAX_VALUE else left + right
    }

    private fun parseOpaqueUriQueryParameters(input: String): Map<String, String> {
        val queryIndex = input.indexOf('?')
        if (queryIndex == -1 || queryIndex == input.lastIndex) {
            return emptyMap()
        }

        return input.substring(queryIndex + 1)
            .split("&")
            .mapNotNull { entry ->
                if (entry.isBlank()) {
                    return@mapNotNull null
                }
                val keyValue = entry.split("=", limit = 2)
                val key = keyValue[0].lowercase(Locale.US)
                val value = keyValue.getOrElse(1) { "" }
                key to URLDecoder.decode(value, "UTF-8")
            }
            .toMap()
    }

    suspend fun advancePreparedLightningPayment(swapId: String): PaymentState = withContext(Dispatchers.IO) {
        getPendingLightningPaymentSession()
            ?.takeIf { it.swapId == swapId && it.backend != LightningPaymentBackend.LWK_PREPARE_PAY }
            ?.let { throw Exception("This prepared Lightning payment is monitored directly via Boltz status") }
        val (state, snapshot) =
            withBoltzOperationLock(operation = "advancePreparedLightningPayment", swapId = swapId) {
                val response = ensurePreparedLightningPaymentResponse(swapId)
                advanceBoltzPaymentState("advancePreparedLightningPayment") {
                    response.advance()
                } to response.serialize()
            }
        getPendingLightningPaymentSession()
            ?.takeIf { it.swapId == swapId }
            ?.let { persistPreparedLightningPaymentSession(it.copy(snapshot = snapshot)) }
        state
    }

    suspend fun finishPreparedLightningPayment(swapId: String): Boolean? = withContext(Dispatchers.IO) {
        getPendingLightningPaymentSession()
            ?.takeIf { it.swapId == swapId && it.backend != LightningPaymentBackend.LWK_PREPARE_PAY }
            ?.let { throw Exception("This prepared Lightning payment does not expose an LWK completion snapshot") }
        val completed =
            withBoltzOperationLock(operation = "finishPreparedLightningPayment", swapId = swapId) {
                val response = ensurePreparedLightningPaymentResponse(swapId)
                val snapshot = response.serialize()
                try {
                    response.completePay()
                } catch (error: LwkException) {
                    if (!isBoltzTransientProgressError(error)) {
                        throw error
                    }
                    if (BuildConfig.DEBUG) Log.d(TAG, "finishPreparedLightningPayment: waiting for more Boltz activity")
                    runCatching { response.destroy() }
                    preparePayResponses[swapId] = ensureBoltzSession().restorePreparePay(snapshot)
                    null
                }
            }
        if (completed == null) {
            return@withContext null
        }
        cleanupPreparePayResponse(swapId)
        clearPendingPreparedLightningPaymentSession()
        destroyBoltzSessionIfIdle("lightning_payment_completed")
        completed
    }

    suspend fun failPreparedLightningPayment(swapId: String) = withContext(Dispatchers.IO) {
        cleanupPreparePayResponse(swapId)
        clearPendingPreparedLightningPaymentSession()
        destroyBoltzSessionIfIdle("lightning_payment_failed")
    }

    suspend fun clearPreparedLightningPaymentSession(swapId: String) = withContext(Dispatchers.IO) {
        cleanupPreparePayResponse(swapId)
        clearPendingPreparedLightningPaymentSession()
        destroyBoltzSessionIfIdle("lightning_payment_cleared")
    }

    suspend fun createBoltzChainSwapDraft(
        requestKey: String,
        direction: SwapDirection,
        amountSats: Long,
        usesMaxAmount: Boolean,
        selectedFundingOutpoints: List<String>,
        estimatedTerms: EstimatedSwapTerms,
        fundingLiquidFeeRate: Double,
        bitcoinAddress: String,
        liquidReceiveAddressOverride: String? = null,
    ): BoltzChainSwapDraft = withContext(Dispatchers.IO) {
        ensureBoltzEnabled()
        val existingDraft = getBoltzChainSwapDraftByRequestKey(requestKey)
        existingDraft?.let {
            logDebug(
                "createBoltzChainSwapDraft discarding stale runtime draft requestKey=$requestKey " +
                    "swapId=${it.swapId} state=${it.state}",
            )
            deleteBoltzChainSwapDraft(it.draftId)
        }
        ensureBoltzTorReadyIfNeeded()
        logDebug(
            "createBoltzChainSwapDraft start requestKey=$requestKey direction=$direction amount=$amountSats usesMax=$usesMaxAmount " +
                "selectedOutpoints=${selectedFundingOutpoints.size} existing=${existingDraft?.state?.name ?: "none"} " +
                "existingSwapId=${existingDraft?.swapId}",
        )
        val creatingDraft =
            existingDraft?.copy(
                sendAmount = amountSats,
                orderAmountSats = amountSats,
                maxOrderAmountVerified = false,
                usesMaxAmount = usesMaxAmount,
                selectedFundingOutpoints = selectedFundingOutpoints,
                estimatedTerms = estimatedTerms,
                fundingLiquidFeeRate = fundingLiquidFeeRate,
                bitcoinAddress = bitcoinAddress,
                liquidAddress = liquidReceiveAddressOverride,
                swapId = null,
                depositAddress = null,
                receiveAddress = null,
                refundAddress = null,
                state = BoltzChainSwapDraftState.CREATING,
                fundingPreview = null,
                fundingTxid = null,
                settlementTxid = null,
                reviewExpiresAt = 0L,
                snapshot = null,
                updatedAt = System.currentTimeMillis(),
                lastError = null,
            ) ?: BoltzChainSwapDraft(
                draftId = requestKey,
                requestKey = requestKey,
                direction = direction,
                sendAmount = amountSats,
                orderAmountSats = amountSats,
                usesMaxAmount = usesMaxAmount,
                selectedFundingOutpoints = selectedFundingOutpoints,
                estimatedTerms = estimatedTerms,
                fundingLiquidFeeRate = fundingLiquidFeeRate,
                bitcoinAddress = bitcoinAddress,
                liquidAddress = liquidReceiveAddressOverride,
            )

        try {
            val result =
                boltzChainWorkflow.createOrRecoverDraft(
                existingDraft = existingDraft,
                creatingDraft = creatingDraft,
                createOrder = {
                    createBoltzChainSwapOrder(
                        direction = direction,
                        amountSats = amountSats,
                        bitcoinReceiveAddress = bitcoinAddress,
                        liquidReceiveAddressOverride = liquidReceiveAddressOverride,
                    )
                },
                saveDraft = { saveBoltzChainSwapDraft(it) },
                resetSession = { invalidateBoltzSessionState() },
                classifyFailure = { boltzChainWorkflow.classifyCreateFailure(it) },
            )
            logDebug(
                "createBoltzChainSwapDraft complete requestKey=$requestKey usedExisting=${result.usedExistingDraft} " +
                    "swapId=${result.draft.swapId} state=${result.draft.state} " +
                    "lockup=${summarizeValue(result.draft.depositAddress)}",
            )
            result.draft
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val timeoutKind = boltzChainWorkflow.classifyCreateFailure(e)
            if (timeoutKind != BoltzChainSwapWorkflow.CreateFailureKind.OTHER) {
                logWarn(
                    "Boltz chain swap creation timed out requestKey=$requestKey direction=$direction amount=$amountSats " +
                        "timeoutKind=$timeoutKind",
                    Exception("Timeout preparing chain swap", e),
                )
                throw Exception(
                    "Boltz took too long to prepare the swap review. Ibis retried automatically once. Retry again if needed.",
                )
            }
            logWarn(
                "Boltz chain swap creation failed requestKey=$requestKey direction=$direction amount=$amountSats",
                Exception("Boltz create order failed", e),
            )
            throw Exception(e.message ?: "Boltz failed to prepare the swap review", e)
        }
    }

    private suspend fun createBoltzChainSwapOrder(
        direction: SwapDirection,
        amountSats: Long,
        bitcoinReceiveAddress: String,
        liquidReceiveAddressOverride: String? = null,
    ): BoltzChainSwapOrder = withContext(Dispatchers.IO) {
        val startedAt = System.currentTimeMillis()
        logDebug(
            "createBoltzChainSwapOrder start direction=$direction amount=$amountSats " +
                "btcAddress=${summarizeValue(bitcoinReceiveAddress)} liquidOverride=${summarizeValue(liquidReceiveAddressOverride)}",
        )
        fun createOrder(session: BoltzSession): Triple<LockupResponse, String, String> {
            return when (direction) {
                SwapDirection.BTC_TO_LBTC -> {
                    val claimAddress =
                        liquidReceiveAddressOverride?.takeIf { it.isNotBlank() }
                            ?: getNewAddress()
                            ?: throw Exception("No Liquid claim address available")
                    Triple(
                        session.btcToLbtc(
                            amountSats.toULong(),
                            BitcoinAddress(bitcoinReceiveAddress),
                            Address(claimAddress),
                            null,
                        ),
                        claimAddress,
                        bitcoinReceiveAddress,
                    )
                }
                SwapDirection.LBTC_TO_BTC -> {
                    val liquidRefundAddress = getNewAddress() ?: throw Exception("No Liquid refund address available")
                    Triple(
                        session.lbtcToBtc(
                            amountSats.toULong(),
                            Address(liquidRefundAddress),
                            BitcoinAddress(bitcoinReceiveAddress),
                            null,
                        ),
                        bitcoinReceiveAddress,
                        liquidRefundAddress,
                    )
                }
            }
        }

        withBoltzOperationLock(operation = "createBoltzChainSwapOrder") {
            val traceStartedAt = boltzTraceStart()
            val trace =
                BoltzTraceContext(
                    operation = "createBoltzChainSwapOrder",
                    viaTor = secureStorage.getBoltzApiSource() == SecureStorage.BOLTZ_API_TOR,
                    source = "lwk",
                )
            val hadWarmSession =
                boltzSessionMutex.withLock {
                    lwkBoltzSession != null
                }
            if (direction == SwapDirection.LBTC_TO_BTC && hadWarmSession) {
                logBoltzTrace(
                    "reset_before_create",
                    BoltzTraceContext(
                        operation = "createBoltzChainSwapOrder",
                        viaTor = secureStorage.getBoltzApiSource() == SecureStorage.BOLTZ_API_TOR,
                        source = "lwk",
                    ),
                    "direction" to direction.name,
                    "reason" to "warm_session",
                )
                resetBoltzSessionStateLocked()
            }
            val walletId = currentWalletId ?: throw Exception("Wallet not loaded")
            if (getBitcoinElectrumUrl() == null) {
                throw Exception("Chain swap requires a configured Bitcoin Electrum server. Set one in Settings before swapping.")
            }

            fun createOrderWithSession(
                session: BoltzSession,
                sessionElapsedMs: Long,
                attempt: Int,
            ): Triple<LockupResponse, String, String> {
                logDebug(
                    "createBoltzChainSwapOrder session ready direction=$direction amount=$amountSats " +
                        "elapsedMs=$sessionElapsedMs attempt=$attempt",
                )
                val orderStartedAt = System.currentTimeMillis()
                return try {
                    createOrder(session).also {
                        persistBoltzSessionNextIndex(walletId, session)
                        logBoltzTrace(
                            "success",
                            trace.copy(attempt = attempt),
                            "elapsedMs" to boltzElapsedMs(traceStartedAt),
                            "direction" to direction.name,
                            "amountSats" to amountSats,
                        )
                    }
                } catch (e: Exception) {
                    logWarn(
                        "createBoltzChainSwapOrder failed direction=$direction amount=$amountSats " +
                            "sessionElapsedMs=$sessionElapsedMs providerElapsedMs=${System.currentTimeMillis() - orderStartedAt} " +
                            "attempt=$attempt",
                        e,
                    )
                    throw e
                }
            }

            val sessionStartedAt = System.currentTimeMillis()
            val session = ensureBoltzSession()
            val sessionElapsedMs = System.currentTimeMillis() - sessionStartedAt
            val (response, receiveAddress, refundAddress) =
                try {
                    createOrderWithSession(session, sessionElapsedMs, attempt = 1)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (!shouldRetryBoltzChainSwapCreation(e)) {
                        throw e
                    }
                    val retryReason =
                        if (isBoltzNextIndexCollision(e)) {
                            "next_index_collision"
                        } else {
                            "timeout"
                        }
                    logBoltzTrace(
                        "retrying",
                        trace.copy(attempt = 2),
                        level = BoltzTraceLevel.WARN,
                        throwable = e,
                        "elapsedMs" to boltzElapsedMs(traceStartedAt),
                        "direction" to direction.name,
                        "amountSats" to amountSats,
                        "reason" to retryReason,
                    )
                    if (isBoltzNextIndexCollision(e)) {
                        clearBoltzSessionNextIndexCache()
                    }
                    if (hasActiveBoltzResponses()) {
                        logBoltzTrace(
                            "retry_skipped",
                            trace.copy(attempt = 2),
                            level = BoltzTraceLevel.WARN,
                            throwable = e,
                            "elapsedMs" to boltzElapsedMs(traceStartedAt),
                            "direction" to direction.name,
                            "amountSats" to amountSats,
                            "reason" to "$retryReason.active_responses",
                        )
                        throw e
                    }
                    logWarn(
                        if (isBoltzNextIndexCollision(e)) {
                            "Boltz chain swap creation hit a stale next-index collision, retrying with uncached session state"
                        } else {
                            "Boltz chain swap creation timed out, retrying with fresh session"
                        },
                        e,
                    )
                    destroyBoltzSession(persistNextIndex = !isBoltzNextIndexCollision(e))
                    val retrySessionStartedAt = System.currentTimeMillis()
                    val retrySession = ensureBoltzSession()
                    val retrySessionElapsedMs = System.currentTimeMillis() - retrySessionStartedAt
                    createOrderWithSession(retrySession, retrySessionElapsedMs, attempt = 2)
                }

            val swapId = response.swapId()
            logDebug(
                "createBoltzChainSwapOrder success direction=$direction swapId=$swapId " +
                    "lockup=${summarizeValue(response.lockupAddress())} receive=${summarizeValue(receiveAddress)} " +
                    "refund=${summarizeValue(refundAddress)}",
            )
            lockupResponses[swapId] = response
            val snapshot = response.serialize()
            BoltzChainSwapOrder(
                swapId = swapId,
                lockupAddress = response.lockupAddress(),
                receiveAddress = receiveAddress,
                refundAddress = refundAddress,
                snapshot = snapshot,
            )
                .also { logTiming("createBoltzChainSwap", startedAt) }
        }
    }

    fun finalizeBoltzChainSwapReviewDraft(
        draft: BoltzChainSwapDraft,
        fundingPreview: BoltzChainFundingPreview?,
        reviewExpiresAt: Long,
    ): BoltzChainSwapDraft {
        logDebug(
            "finalizeBoltzChainSwapReviewDraft requestKey=${draft.requestKey} swapId=${draft.swapId} " +
                "fundingFee=${fundingPreview?.feeSats} fundingVBytes=${fundingPreview?.txVBytes} reviewExpiresAt=$reviewExpiresAt",
        )
        return boltzChainWorkflow.markReviewReady(draft, fundingPreview, reviewExpiresAt)
    }

    suspend fun releasePreparedBoltzChainSwapRuntime(
        swapId: String?,
        reason: String,
    ) = withContext(Dispatchers.IO) {
        if (swapId.isNullOrBlank()) return@withContext
        logDebug("releasePreparedBoltzChainSwapRuntime swapId=$swapId reason=$reason")
        cleanupLockupResponse(swapId)
        boltzSessionKeepAliveUntilMs = 0L
        destroyBoltzSessionIfIdle(reason)
    }

    fun failBoltzChainSwapDraft(
        swapId: String,
        error: String?,
        refundable: Boolean = false,
    ): BoltzChainSwapDraft? {
        val draft = getBoltzChainSwapDraftBySwapId(swapId) ?: return null
        logWarn(
            "failBoltzChainSwapDraft swapId=$swapId refundable=$refundable error=$error",
            Exception(error ?: "Boltz chain swap failed"),
        )
        val updated = boltzChainWorkflow.markFailed(draft, error, refundable)
        saveBoltzChainSwapDraft(updated)
        return updated
    }

    fun boltzChainSwapDraftToPendingSession(draft: BoltzChainSwapDraft): PendingSwapSession {
        return boltzChainWorkflow.toPendingSwapSession(draft)
    }

    fun syncBoltzChainSwapDraftFromPendingSwap(
        pendingSwap: PendingSwapSession,
        state: BoltzChainSwapDraftState? = null,
        error: String? = null,
        refundable: Boolean = false,
    ): BoltzChainSwapDraft {
        val draft = getBoltzChainSwapDraftBySwapId(pendingSwap.swapId) ?: pendingSwapToBoltzChainDraft(pendingSwap)
        logDebug(
            "syncBoltzChainSwapDraftFromPendingSwap swapId=${pendingSwap.swapId} " +
                "phase=${pendingSwap.phase} requestedState=${state?.name ?: "none"} fundingTxid=${summarizeValue(pendingSwap.fundingTxid)} " +
                "settlementTxid=${summarizeValue(pendingSwap.settlementTxid)}",
        )
        val synced =
            draft.copy(
                sendAmount = pendingSwap.sendAmount,
                estimatedTerms = pendingSwap.estimatedTerms,
                fundingLiquidFeeRate = pendingSwap.fundingLiquidFeeRate,
                receiveAddress = pendingSwap.receiveAddress,
                refundAddress = pendingSwap.refundAddress,
                fundingTxid = pendingSwap.fundingTxid,
                settlementTxid = pendingSwap.settlementTxid,
                reviewExpiresAt = pendingSwap.reviewExpiresAt,
                snapshot = pendingSwap.boltzSnapshot ?: draft.snapshot,
                fundingPreview =
                    draft.fundingPreview?.copy(
                        feeSats = pendingSwap.actualFundingFeeSats ?: draft.fundingPreview.feeSats,
                        txVBytes = pendingSwap.actualFundingTxVBytes ?: draft.fundingPreview.txVBytes,
                    ),
                updatedAt = System.currentTimeMillis(),
            )
        val updated =
            when (state) {
                null -> synced
                BoltzChainSwapDraftState.FUNDING_BROADCAST ->
                    boltzChainWorkflow.markFundingBroadcast(synced, pendingSwap.fundingTxid.orEmpty())
                BoltzChainSwapDraftState.IN_PROGRESS -> boltzChainWorkflow.markInProgress(synced, pendingSwap.fundingTxid)
                BoltzChainSwapDraftState.COMPLETED ->
                    boltzChainWorkflow.markCompleted(synced, pendingSwap.settlementTxid)
                BoltzChainSwapDraftState.REFUNDABLE ->
                    boltzChainWorkflow.markFailed(synced, error, refundable = true)
                BoltzChainSwapDraftState.FAILED ->
                    boltzChainWorkflow.markFailed(synced, error, refundable = refundable)
                else -> synced.copy(state = state)
            }
        saveBoltzChainSwapDraft(updated)
        return updated
    }

    private fun pendingSwapToBoltzChainDraft(pendingSwap: PendingSwapSession): BoltzChainSwapDraft {
        val bitcoinAddress =
            when (pendingSwap.direction) {
                SwapDirection.BTC_TO_LBTC -> pendingSwap.refundAddress.orEmpty()
                SwapDirection.LBTC_TO_BTC -> pendingSwap.receiveAddress.orEmpty()
            }
        val liquidAddress =
            when (pendingSwap.direction) {
                SwapDirection.BTC_TO_LBTC -> pendingSwap.receiveAddress
                SwapDirection.LBTC_TO_BTC -> pendingSwap.refundAddress
            }
        return BoltzChainSwapDraft(
            draftId = pendingSwap.requestKey ?: pendingSwap.swapId,
            requestKey = pendingSwap.requestKey ?: pendingSwap.swapId,
            direction = pendingSwap.direction,
            sendAmount = pendingSwap.sendAmount,
            orderAmountSats = pendingSwap.boltzOrderAmountSats ?: pendingSwap.sendAmount,
            maxOrderAmountVerified = pendingSwap.boltzMaxOrderAmountVerified,
            usesMaxAmount = pendingSwap.usesMaxAmount,
            selectedFundingOutpoints = pendingSwap.selectedFundingOutpoints,
            estimatedTerms = pendingSwap.estimatedTerms,
            fundingLiquidFeeRate = pendingSwap.fundingLiquidFeeRate,
            bitcoinAddress = bitcoinAddress,
            liquidAddress = liquidAddress,
            swapId = pendingSwap.swapId,
            depositAddress = pendingSwap.depositAddress,
            receiveAddress = pendingSwap.receiveAddress,
            refundAddress = pendingSwap.refundAddress,
            state =
                when (pendingSwap.phase) {
                    PendingSwapPhase.REVIEW -> BoltzChainSwapDraftState.REVIEW_READY
                    PendingSwapPhase.FUNDING -> BoltzChainSwapDraftState.FUNDING_BROADCAST
                    PendingSwapPhase.IN_PROGRESS -> BoltzChainSwapDraftState.IN_PROGRESS
                    PendingSwapPhase.FAILED -> BoltzChainSwapDraftState.FAILED
                },
            fundingPreview =
                pendingSwap.actualFundingFeeSats?.let { feeSats ->
                    BoltzChainFundingPreview(
                        feeSats = feeSats,
                        txVBytes = pendingSwap.actualFundingTxVBytes ?: 0.0,
                        effectiveFeeRate = pendingSwap.fundingLiquidFeeRate,
                        verifiedRecipientAmountSats =
                            pendingSwap.boltzVerifiedRecipientAmountSats ?: pendingSwap.sendAmount,
                    )
                },
            fundingTxid = pendingSwap.fundingTxid,
            settlementTxid = pendingSwap.settlementTxid,
            createdAt = pendingSwap.createdAt,
            updatedAt = System.currentTimeMillis(),
            reviewExpiresAt = pendingSwap.reviewExpiresAt,
            snapshot = pendingSwap.boltzSnapshot,
        )
    }

    suspend fun restoreBoltzChainSwap(
        swapId: String,
        snapshot: String,
    ) = withContext(Dispatchers.IO) {
        if (lockupResponses.containsKey(swapId)) {
            logDebug("restoreBoltzChainSwap skipped swapId=$swapId alreadyLoaded=true")
            return@withContext
        }
        logDebug("restoreBoltzChainSwap swapId=$swapId snapshotLength=${snapshot.length}")
        withBoltzOperationLock(operation = "restoreBoltzChainSwap", swapId = swapId) {
            lockupResponses[swapId] = ensureBoltzSession().restoreLockup(snapshot)
        }
    }

    suspend fun getBoltzChainSwapSnapshot(swapId: String): String? = withContext(Dispatchers.IO) {
        runCatching { lockupResponses[swapId]?.serialize() }.getOrNull()
            ?: getPendingSwapSession(swapId)?.boltzSnapshot
            ?: getBoltzChainSwapDraftBySwapId(swapId)?.snapshot
    }

    suspend fun advanceBoltzChainSwap(swapId: String): PaymentState = withContext(Dispatchers.IO) {
        withBoltzOperationLock(operation = "advanceBoltzChainSwap", swapId = swapId) {
            val response = ensureLockupResponseForChainSwap(swapId)
            try {
                advanceBoltzPaymentState("advanceBoltzChainSwap") {
                    response.advance()
                }
            } catch (error: Exception) {
                if (!isBoltzObjectConsumed(error)) throw error
                logDebug("advanceBoltzChainSwap restoring consumed response swapId=$swapId")
                val restored = restoreConsumedLockupResponse(swapId)
                advanceBoltzPaymentState("advanceBoltzChainSwapRestored") {
                    restored.advance()
                }
            }
        }
    }

    suspend fun finishBoltzChainSwap(swapId: String): Boolean? = withContext(Dispatchers.IO) {
        val completed =
            withBoltzOperationLock(operation = "finishBoltzChainSwap", swapId = swapId) {
                var response = ensureLockupResponseForChainSwap(swapId)
                val snapshot =
                    runCatching { response.serialize() }.getOrElse { error ->
                        if (!isBoltzObjectConsumed(error)) throw error
                        logDebug("finishBoltzChainSwap restoring consumed response before serialize swapId=$swapId")
                        response = restoreConsumedLockupResponse(swapId)
                        response.serialize()
                    }
                try {
                    response.complete()
                } catch (error: Exception) {
                    if (isBoltzObjectConsumed(error)) {
                        logDebug("finishBoltzChainSwap restoring consumed response before complete swapId=$swapId")
                        response = restoreConsumedLockupResponse(swapId)
                        return@withBoltzOperationLock runCatching { response.complete() }
                            .getOrElse { retryError ->
                                if (retryError is LwkException && isBoltzTransientProgressError(retryError)) {
                                    if (BuildConfig.DEBUG) {
                                        Log.d(TAG, "finishBoltzChainSwap: waiting for more Boltz activity after restore")
                                    }
                                    runCatching { response.destroy() }
                                    lockupResponses[swapId] = ensureBoltzSession().restoreLockup(snapshot)
                                    return@withBoltzOperationLock null
                                }
                                throw retryError
                            }
                    }
                    if (error !is LwkException) throw error
                    if (!isBoltzTransientProgressError(error)) {
                        throw error
                    }
                    if (BuildConfig.DEBUG) Log.d(TAG, "finishBoltzChainSwap: waiting for more Boltz activity")
                    runCatching { response.destroy() }
                    lockupResponses[swapId] = ensureBoltzSession().restoreLockup(snapshot)
                    null
                }
            }
        if (completed == null) {
            return@withContext null
        }
        cleanupLockupResponse(swapId)
        completed
    }

    suspend fun failBoltzChainSwap(swapId: String) = withContext(Dispatchers.IO) {
        cleanupLockupResponse(swapId)
    }

    suspend fun clearBoltzChainSwapRuntime(
        swapId: String,
        reason: String,
    ) = withContext(Dispatchers.IO) {
        logDebug("clearBoltzChainSwapRuntime swapId=$swapId reason=$reason")
        cleanupLockupResponse(swapId)
        destroyBoltzSessionIfIdle(reason)
    }

    suspend fun estimateLbtcSendFee(
        address: String,
        amountSats: Long,
        feeRateSatPerVb: Double = 0.1,
        selectedUtxos: List<UtxoInfo>? = null,
    ): Long = withContext(Dispatchers.IO) {
        val wollet = lwkWollet ?: throw Exception("Wallet not loaded")
        val network = lwkNetwork ?: throw Exception("Network not initialized")
        val pset = buildLbtcSendPset(
            wollet = wollet,
            network = network,
            recipients = listOf(Recipient(address, amountSats.toULong())),
            feeRateSatPerVb = feeRateSatPerVb,
            selectedUtxos = selectedUtxos,
        )
        val details = wollet.psetDetails(pset)
        try {
            details.balance().fee().toLong()
        } finally {
            details.destroy()
        }
    }

    suspend fun getMaxSpendableLbtc(
        address: String,
        feeRateSatPerVb: Double = 0.1,
        selectedUtxos: List<UtxoInfo>? = null,
    ): Long = withContext(Dispatchers.IO) {
        val wollet = lwkWollet ?: throw Exception("Wallet not loaded")
        val network = lwkNetwork ?: throw Exception("Network not initialized")
        val balance =
            selectedUtxos
                ?.takeIf { it.isNotEmpty() }
                ?.sumOf { it.amountSats.toLong() }
                ?: _liquidState.value.balanceSats.coerceAtLeast(0L)
        if (balance <= 0L) return@withContext 0L

        findMaxExactSendAmount(balance) { candidate ->
            try {
                buildLbtcSendPset(
                    wollet = wollet,
                    network = network,
                    recipients = listOf(Recipient(address, candidate.toULong())),
                    feeRateSatPerVb = feeRateSatPerVb,
                    selectedUtxos = selectedUtxos,
                )
                true
            } catch (e: LwkException) {
                if (isInsufficientFundsError(e.message)) {
                    false
                } else {
                    throw e
                }
            }
        }
    }

    private fun buildLbtcSendPset(
        wollet: Wollet,
        network: Network,
        recipients: List<Recipient>,
        feeRateSatPerVb: Double,
        selectedUtxos: List<UtxoInfo>? = null,
    ): Pset {
        require(recipients.isNotEmpty()) { "At least one recipient is required" }
        require(recipients.all { it.amountSats > 0UL }) { "Recipient amounts must be positive" }

        val txBuilder = network.txBuilder()
        selectedUtxos
            ?.takeIf { it.isNotEmpty() }
            ?.let { requestedUtxos ->
                val selectedOutpoints = requestedUtxos.map { it.outpoint }.toSet()
                val walletUtxos =
                    wollet.utxos().filter { utxo ->
                        val outpoint = utxo.outpoint()
                        "${outpoint.txid()}:${outpoint.vout()}" in selectedOutpoints
                    }
                if (walletUtxos.size != selectedOutpoints.size) {
                    throw Exception("Some selected Liquid UTXOs are no longer available")
                }
                txBuilder.setWalletUtxos(walletUtxos.map { it.outpoint() })
            }
        recipients.forEach { recipient ->
            txBuilder.addLbtcRecipient(Address(recipient.address), recipient.amountSats)
        }
        txBuilder.feeRate((feeRateSatPerVb * 1000.0).toFloat())
        return txBuilder.finish(wollet)
    }

    private fun persistLiquidSendLabel(
        txid: String,
        label: String?,
        recipientAddress: String? = null,
    ) {
        val walletId = currentWalletId ?: return
        val trimmedLabel = label?.trim().orEmpty()
        if (trimmedLabel.isBlank()) {
            return
        }
        secureStorage.saveLiquidTransactionLabel(walletId, txid, trimmedLabel)
        recipientAddress
            ?.takeIf { it.isNotBlank() }
            ?.let { secureStorage.saveLiquidAddressLabel(walletId, it, trimmedLabel) }
    }

    private fun estimateNetworkFeeSats(vsize: Int, feeRateSatPerVb: Double): Long {
        return kotlin.math.ceil(vsize * feeRateSatPerVb).toLong().coerceAtLeast(1L)
    }

    private fun extractMissingSats(message: String?): Long? {
        if (message.isNullOrBlank()) return null
        val match = Regex("""missing_sats:\s*(\d+)""").find(message) ?: return null
        return match.groupValues.getOrNull(1)?.toLongOrNull()
    }

    private fun isInsufficientFundsError(message: String?): Boolean {
        return message?.contains("InsufficientFunds") == true
    }

    private fun cleanupInvoiceResponse(swapId: String) {
        val response = invoiceResponses.remove(swapId) ?: return
        try {
            response.destroy()
        } catch (_: Exception) {}
    }

    private fun cleanupPreparePayResponse(swapId: String) {
        val response = preparePayResponses.remove(swapId) ?: return
        try {
            response.destroy()
        } catch (_: Exception) {}
    }

    private fun cleanupLockupResponse(swapId: String) {
        val response = lockupResponses.remove(swapId) ?: return
        try {
            response.destroy()
        } catch (_: Exception) {}
    }

    private suspend fun ensureLockupResponseForChainSwap(swapId: String): LockupResponse {
        lockupResponses[swapId]?.let { return it }
        val snapshot =
            getPendingSwapSession(swapId)?.boltzSnapshot
                ?: getBoltzChainSwapDraftBySwapId(swapId)?.snapshot
                ?: throw Exception("Boltz chain swap not found")
        return ensureBoltzSession().restoreLockup(snapshot).also {
            lockupResponses[swapId] = it
        }
    }

    private suspend fun restoreConsumedLockupResponse(swapId: String): LockupResponse {
        cleanupLockupResponse(swapId)
        val snapshot =
            getPendingSwapSession(swapId)?.boltzSnapshot
                ?: getBoltzChainSwapDraftBySwapId(swapId)?.snapshot
                ?: throw Exception("Boltz chain swap snapshot unavailable")
        return ensureBoltzSession().restoreLockup(snapshot).also {
            lockupResponses[swapId] = it
        }
    }

    private fun isBoltzObjectConsumed(error: Throwable): Boolean {
        return error.javaClass.simpleName == "ObjectConsumed" ||
            error.message?.contains("ObjectConsumed", ignoreCase = true) == true
    }

    private suspend fun destroyBoltzSessionIfIdle(reason: String) {
        if (invoiceResponses.isNotEmpty() || preparePayResponses.isNotEmpty() || lockupResponses.isNotEmpty()) {
            logDebug(
                "destroyBoltzSessionIfIdle skipped reason=$reason invoices=${invoiceResponses.size} " +
                    "preparePays=${preparePayResponses.size} lockups=${lockupResponses.size}",
            )
            return
        }
        val now = System.currentTimeMillis()
        if (lwkBoltzSession != null && now < boltzSessionKeepAliveUntilMs) {
            logDebug(
                "destroyBoltzSessionIfIdle keeping warm session reason=$reason keepAliveMs=${boltzSessionKeepAliveUntilMs - now}",
            )
            return
        }
        logDebug("destroyBoltzSessionIfIdle destroying shared session reason=$reason")
        destroyBoltzSession()
    }

    private fun getBitcoinElectrumUrl(): String? {
        val config = secureStorage.getActiveElectrumServer() ?: return null
        val protocol = if (config.useSsl) "ssl" else "tcp"
        return "$protocol://${config.cleanUrl()}:${config.port}"
    }

    private fun shouldUseLiquidTor(): Boolean {
        if (secureStorage.isLiquidTorEnabled()) return true
        val activeId = secureStorage.getActiveLiquidServerId() ?: return false
        val config = secureStorage.getLiquidServer(activeId) ?: return false
        return config.useTor || config.isOnionAddress()
    }

    private fun getLwkDbPath(walletId: String): String {
        val dir = File(context.filesDir, LWK_DB_DIR)
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "$walletId.db").absolutePath
    }

    private fun deleteWalletDatabaseFiles(walletId: String): Boolean {
        return deleteSqliteArtifacts(getLwkDbPath(walletId))
    }

    private fun deleteSqliteArtifacts(dbPath: String): Boolean {
        val sqliteFiles = listOf(
            File(dbPath),
            File("$dbPath-wal"),
            File("$dbPath-shm"),
            File("$dbPath-journal"),
        )

        var deletedAllExistingFiles = true
        sqliteFiles.forEach { file ->
            if (file.exists() && !file.delete()) {
                deletedAllExistingFiles = false
            }
        }

        return deletedAllExistingFiles && sqliteFiles.none { it.exists() }
    }

    fun close() {
        stopNotificationCollector()
        runCatching { boltzProvider.close() }
        repositoryScope.cancel()
    }

    private fun logError(message: String, error: Exception) {
        if (BuildConfig.DEBUG) {
            Log.e(BOLTZ_LOG_TAG, message, error)
        } else {
            Log.e(BOLTZ_LOG_TAG, message)
        }
    }

    private fun logWarn(message: String, error: Exception) {
        if (BuildConfig.DEBUG) {
            Log.w(BOLTZ_LOG_TAG, message, error)
        } else {
            Log.w(BOLTZ_LOG_TAG, message)
        }
    }

    private fun logDebug(message: String) {
        if (BuildConfig.DEBUG) {
            Log.d(BOLTZ_LOG_TAG, message)
        }
    }

    private fun logTiming(operation: String, startedAt: Long) {
        if (!BuildConfig.DEBUG) return
        Log.d(TAG, "$operation took ${System.currentTimeMillis() - startedAt}ms")
    }

    private fun summarizeValue(value: String?): String {
        if (value.isNullOrBlank()) return "null"
        return if (value.length <= 12) value else "${value.take(6)}...${value.takeLast(4)}"
    }

    enum class SubscriptionResult {
        NO_CHANGES,
        SYNCED,
        FAILED,
    }

    sealed interface ConnectionEvent {
        data object ConnectionLost : ConnectionEvent
    }
}

internal fun extractBoltzRescueMnemonic(rescueFileJson: String): String? {
    val trimmed = rescueFileJson.trim()
    if (looksLikeBoltzRescueMnemonic(trimmed)) {
        return normalizeBoltzMnemonic(trimmed)
    }
    val root = runCatching { JSONObject(trimmed) }.getOrNull() ?: return null
    return findBoltzMnemonicValue(root)
}

private fun findBoltzMnemonicValue(node: Any?): String? {
    return when (node) {
        is JSONObject -> {
            val keys = node.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val mnemonic = findBoltzMnemonicValue(node.opt(key))
                if (mnemonic != null) {
                    return mnemonic
                }
            }
            null
        }

        is JSONArray -> {
            for (index in 0 until node.length()) {
                val mnemonic = findBoltzMnemonicValue(node.opt(index))
                if (mnemonic != null) {
                    return mnemonic
                }
            }
            null
        }

        is String -> normalizeBoltzMnemonic(node).takeIf(::looksLikeBoltzRescueMnemonic)
        else -> null
    }
}

internal fun looksLikeBoltzRescueMnemonic(value: String): Boolean {
    val normalized = normalizeBoltzMnemonic(value)
    val words = normalized.split(' ')
    if (words.size != 12 && words.size != 24) {
        return false
    }
    return words.all { word -> word.matches(Regex("[a-z]+")) }
}

private fun normalizeBoltzMnemonic(value: String): String = value.trim().replace(Regex("\\s+"), " ")
