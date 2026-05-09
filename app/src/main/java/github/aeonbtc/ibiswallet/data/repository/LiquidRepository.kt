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
import github.aeonbtc.ibiswallet.data.model.LiquidAsset
import github.aeonbtc.ibiswallet.data.model.LiquidAssetBalance
import github.aeonbtc.ibiswallet.data.model.LiquidSendKind
import github.aeonbtc.ibiswallet.data.model.LiquidSendPreview
import github.aeonbtc.ibiswallet.data.model.LiquidSwapDetails
import github.aeonbtc.ibiswallet.data.model.LiquidSwapTxRole
import github.aeonbtc.ibiswallet.data.model.LiquidTransaction
import github.aeonbtc.ibiswallet.data.model.LiquidTxSource
import github.aeonbtc.ibiswallet.data.model.LiquidTxType
import github.aeonbtc.ibiswallet.data.model.TransactionSearchFilters
import github.aeonbtc.ibiswallet.data.model.TransactionSearchLayer
import github.aeonbtc.ibiswallet.data.model.TransactionSearchResult
import github.aeonbtc.ibiswallet.data.model.LiquidWalletState
import github.aeonbtc.ibiswallet.data.model.PendingLightningInvoice
import github.aeonbtc.ibiswallet.data.model.PendingLightningInvoiceSession
import github.aeonbtc.ibiswallet.data.model.PendingLightningPaymentPhase
import github.aeonbtc.ibiswallet.data.model.PendingLightningPaymentSession
import github.aeonbtc.ibiswallet.data.model.PendingLightningReceive
import github.aeonbtc.ibiswallet.data.model.PendingSwapPhase
import github.aeonbtc.ibiswallet.data.model.PendingSwapSession
import github.aeonbtc.ibiswallet.data.model.Recipient
import github.aeonbtc.ibiswallet.data.model.StoredWallet
import github.aeonbtc.ibiswallet.data.model.SwapDirection
import github.aeonbtc.ibiswallet.data.model.SwapLimits
import github.aeonbtc.ibiswallet.data.model.SwapQuote
import github.aeonbtc.ibiswallet.data.model.SwapService
import github.aeonbtc.ibiswallet.data.model.SyncProgress
import github.aeonbtc.ibiswallet.data.model.UtxoInfo
import github.aeonbtc.ibiswallet.data.model.WalletAddress
import github.aeonbtc.ibiswallet.data.remote.Bip353Resolver
import github.aeonbtc.ibiswallet.data.remote.Bolt12OfferVerifier
import github.aeonbtc.ibiswallet.data.remote.BoltzApiClient
import github.aeonbtc.ibiswallet.data.remote.LnurlPayResolver
import github.aeonbtc.ibiswallet.data.remote.SideSwapApiClient
import github.aeonbtc.ibiswallet.tor.CachingElectrumProxy
import github.aeonbtc.ibiswallet.tor.ElectrumNotification
import github.aeonbtc.ibiswallet.tor.TorManager
import github.aeonbtc.ibiswallet.util.BitcoinUtils
import github.aeonbtc.ibiswallet.util.Blech32Util
import github.aeonbtc.ibiswallet.util.ElectrumSeedUtil
import github.aeonbtc.ibiswallet.util.ElementsPsetSigner
import github.aeonbtc.ibiswallet.util.SecureLog
import github.aeonbtc.ibiswallet.util.TofuTrustManager
import github.aeonbtc.ibiswallet.util.buildLiquidTransactionSearchDocument
import github.aeonbtc.ibiswallet.util.findMaxExactSendAmount
import github.aeonbtc.ibiswallet.util.isTransactionInsufficientFundsError
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
import kotlinx.coroutines.withTimeout
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

internal fun findEarliestUnusedLiquidExternalIndex(occupiedIndices: Set<UInt>): UInt {
    var index = 0u
    while (index in occupiedIndices) {
        index++
    }
    return index
}

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
    private fun buildLiquidTransactionSearchDocuments(
        walletId: String,
        transactions: Collection<LiquidTransaction>,
    ) = run {
        val labels = secureStorage.getAllLiquidTransactionLabels(walletId)
        transactions.map { transaction ->
            buildLiquidTransactionSearchDocument(
                walletId = walletId,
                transaction = transaction,
                transactionLabel = labels[transaction.txid],
            )
        }
    }

    private fun scheduleLiquidTransactionSearchIndexRefresh(
        walletId: String?,
        transactions: Collection<LiquidTransaction>,
    ) {
        val resolvedWalletId = walletId ?: return
        val transactionSnapshot = transactions.toList()
        liquidSearchIndexJob?.cancel()
        liquidSearchIndexJob =
            repositoryScope.launch {
                electrumCache.replaceTransactionSearchDocuments(
                    walletId = resolvedWalletId,
                    layer = TransactionSearchLayer.LIQUID,
                    documents = buildLiquidTransactionSearchDocuments(resolvedWalletId, transactionSnapshot),
                )
            }
    }

    private fun upsertLiquidTransactionSearchDocument(
        walletId: String?,
        transaction: LiquidTransaction,
    ) {
        val resolvedWalletId = walletId ?: return
        electrumCache.upsertTransactionSearchDocuments(
            listOf(
                buildLiquidTransactionSearchDocument(
                    walletId = resolvedWalletId,
                    transaction = transaction,
                    transactionLabel = secureStorage.getAllLiquidTransactionLabels(resolvedWalletId)[transaction.txid],
                ),
            ),
        )
    }

    companion object {
        private const val TAG = "LiquidRepository"
        private const val BOLTZ_LOG_TAG = "BoltzDebug"
        private const val LWK_DB_DIR = "lwk"
        private const val ADDRESS_PEEK_AHEAD = 60
        private const val ADDRESS_INTERNAL_PEEK_AHEAD = 60
        private const val MAX_LIQUID_ADDRESS_SCAN_ATTEMPTS = 10_000
        private const val NOTIFICATION_DEBOUNCE_MS = 1_000L
        private const val LIQUID_SYNC_TIMEOUT_MS = 90_000L

        // Typical BTC tx vsize for a simple 1-in-1-out + change (~140 vB)
        private const val TYPICAL_BTC_TX_VSIZE = 140
        private const val TYPICAL_LIQUID_TX_VSIZE = 200
        private const val SIDESWAP_PEG_IN_LIQUID_TX_VSIZE = 2860
        private const val DEFAULT_LIQUID_SWAP_FEE_RATE = 0.1
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
    private var transactionRefreshJob: Job? = null
    private var liquidSearchIndexJob: Job? = null
    private val subscribedScriptHashes = mutableSetOf<String>()
    private val scriptHashStatusCache = mutableMapOf<String, String?>()
    private val lightningResolutionCache = mutableMapOf<String, CachedLightningResolution>()

    // Cached address strings keyed by derivation index, per keychain. LWK derives addresses
    // through a JNI call (external) or manual blech32/SLIP-77 derivation (internal) that adds
    // up on wallets with thousands of revealed addresses. Cleared on wallet switch/unload.
    private val liquidExternalAddressCache = java.util.concurrent.ConcurrentHashMap<UInt, String>()
    private val liquidInternalAddressCache = java.util.concurrent.ConcurrentHashMap<UInt, String>()

    @Volatile
    private var walletSwitchGeneration: Long = 0

    @Volatile
    private var lastKnownBlockHeight: UInt? = null

    @Volatile
    private var pendingSyncRequested = false

    @Volatile
    private var pendingForceFullSyncRequested = false

    @Volatile
    private var pendingFullSyncProgressRequested = false

    private var cachedMetadataSnapshot: SecureStorage.LiquidMetadataSnapshot? = null
    private var metadataSnapshotWalletId: String? = null

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
    private val lnurlPayResolver: LnurlPayResolver by lazy {
        LnurlPayResolver(
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

    private fun getLiquidMetadata(walletId: String): SecureStorage.LiquidMetadataSnapshot {
        if (metadataSnapshotWalletId == walletId && cachedMetadataSnapshot != null) {
            return cachedMetadataSnapshot!!
        }
        val snapshot = secureStorage.getLiquidMetadataSnapshot(walletId)
        cachedMetadataSnapshot = snapshot
        metadataSnapshotWalletId = walletId
        return snapshot
    }

    fun invalidateMetadataCache() {
        cachedMetadataSnapshot = null
    }

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
     * Load an existing Liquid wallet from persisted CT descriptor.
     * Used on app restart when the wallet was previously initialized.
     */
    suspend fun loadWallet(walletId: String, mnemonic: String? = null) =
        withContext(Dispatchers.IO) {
            connectionMutex.withLock {
                loadWalletLocked(walletId, mnemonic)
            }
        }

    /**
     * Atomically switch the loaded Liquid wallet without leaving the old Wollet active
     * between visible-state clear, teardown, and new-wallet load.
     *
     * @param preloaded when `true`, the caller already called [preloadCachedWalletState]
     *   which published a lightweight snapshot and incremented the generation counter.
     *   [beginWalletSwitchLocked] is skipped to avoid clearing that preloaded state;
     *   [unloadWalletLocked] still performs its own generation increment and teardown.
     */
    suspend fun switchWallet(
        walletId: String,
        mnemonic: String? = null,
        preserveConnection: Boolean = false,
        preloaded: Boolean = false,
    ) = withContext(Dispatchers.IO) {
        connectionMutex.withLock {
            if (!preloaded) {
                beginWalletSwitchLocked()
            }
            unloadWalletLocked(
                preserveConnection = preserveConnection,
                visibleStateAlreadyCleared = preloaded,
            )
            loadWalletLocked(
                walletId = walletId,
                mnemonic = mnemonic,
                preserveVisibleState = preloaded,
            )
        }
    }

    /** Unload the current Liquid wallet, optionally preserving the Electrum connection. */
    suspend fun unloadWallet(preserveConnection: Boolean = false) =
        withContext(Dispatchers.IO) {
            connectionMutex.withLock {
                unloadWalletLocked(preserveConnection)
            }
        }

    private fun initializeWalletLocked(
        walletId: String,
        mnemonic: String,
        preserveVisibleState: Boolean = false,
    ) {
        try {
            SecureLog.d(TAG, "Initializing Liquid wallet for $walletId")
            if (!preserveVisibleState) {
                _liquidState.value = LiquidWalletState(walletId = walletId)
            }

            val network = Network.mainnet()
            lwkNetwork = network

            val passphrase = secureStorage.getPassphrase(walletId)
            val descriptorStr: String

            if (passphrase.isNullOrEmpty()) {
                val lwkMnemonic = Mnemonic(mnemonic)
                val signer = Signer(lwkMnemonic, network)
                lwkSigner = signer
                val descriptor: WolletDescriptor = signer.wpkhSlip77Descriptor()
                descriptorStr = descriptor.toString()
            } else {
                lwkSigner = null
                val seed = ElectrumSeedUtil.bip39MnemonicToSeed(mnemonic, passphrase)
                descriptorStr = ElectrumSeedUtil.buildLiquidCtDescriptor(seed)
            }

            secureStorage.setLiquidDescriptor(walletId, descriptorStr)

            val descriptor = WolletDescriptor(descriptorStr)
            val dbPath = getLwkDbPath(walletId)
            val wollet = Wollet(network, descriptor, dbPath)
            lwkWollet = wollet
            clearBoltzChainSwapDraftCache()
            invalidateMetadataCache()
            currentWalletId = walletId
            _loadedWalletId.value = walletId

            if (preserveVisibleState) {
                scheduleDetailedLiquidTransactionRefresh(
                    wollet = wollet,
                    switchGeneration = walletSwitchGeneration,
                )
            } else {
                refreshLiquidWalletState(
                    wollet = wollet,
                    updateLastSyncTimestamp = false,
                )
            }

            SecureLog.d(TAG, "Liquid wallet initialized successfully")
        } catch (e: LwkException) {
            logError("LWK error initializing Liquid wallet", e)
            _liquidState.value = _liquidState.value.copy(error = "Failed to init Liquid wallet: ${e.message}")
        } catch (e: Exception) {
            logError("Failed to initialize Liquid wallet", e)
            _liquidState.value = _liquidState.value.copy(error = "Failed to init Liquid wallet: ${e.message}")
        }
    }

    private fun loadWalletLocked(
        walletId: String,
        mnemonic: String? = null,
        preserveVisibleState: Boolean = false,
    ) {
        try {
            if (!preserveVisibleState) {
                _liquidState.value = LiquidWalletState(walletId = walletId)
            }

            var descriptorStr = secureStorage.getLiquidDescriptor(walletId)

            val passphrase = secureStorage.getPassphrase(walletId)
            if (!passphrase.isNullOrEmpty() && descriptorStr != null) {
                val resolvedMnemonic = mnemonic ?: secureStorage.getMnemonic(walletId)
                if (resolvedMnemonic != null) {
                    val seed = ElectrumSeedUtil.bip39MnemonicToSeed(resolvedMnemonic, passphrase)
                    val correctDescriptor = ElectrumSeedUtil.buildLiquidCtDescriptor(seed)
                    val storedCore = descriptorStr.substringBefore("#")
                    if (storedCore != correctDescriptor) {
                        SecureLog.i(TAG, "Liquid descriptor mismatch (passphrase) - re-deriving")
                        deleteWalletDatabaseFiles(walletId)
                        secureStorage.removeLiquidDescriptor(walletId)
                        descriptorStr = null
                    }
                }
            }

            if (descriptorStr == null) {
                val resolvedMnemonic = mnemonic ?: secureStorage.getMnemonic(walletId)
                if (resolvedMnemonic != null) {
                    initializeWalletLocked(
                        walletId = walletId,
                        mnemonic = resolvedMnemonic,
                        preserveVisibleState = preserveVisibleState,
                    )
                    return
                }
                _liquidState.value = _liquidState.value.copy(error = "No Liquid descriptor found")
                return
            }

            val network = Network.mainnet()
            lwkNetwork = network

            val descriptor = WolletDescriptor(descriptorStr)
            val dbPath = getLwkDbPath(walletId)
            val wollet = Wollet(network, descriptor, dbPath)
            lwkWollet = wollet

            clearBoltzChainSwapDraftCache()
            invalidateMetadataCache()
            currentWalletId = walletId
            _loadedWalletId.value = walletId
            if (preserveVisibleState) {
                scheduleDetailedLiquidTransactionRefresh(
                    wollet = wollet,
                    switchGeneration = walletSwitchGeneration,
                )
            } else {
                refreshLiquidWalletState(
                    wollet = wollet,
                    updateLastSyncTimestamp = false,
                )
            }

            SecureLog.d(TAG, "Liquid wallet loaded from descriptor")
        } catch (e: LwkException) {
            logError("LWK error loading Liquid wallet", e)
            _liquidState.value = _liquidState.value.copy(error = "Failed to load: ${e.message}")
        } catch (e: Exception) {
            logError("Failed to load Liquid wallet", e)
            _liquidState.value = _liquidState.value.copy(error = "Failed to load: ${e.message}")
        }
    }

    private fun beginWalletSwitchLocked() {
        walletSwitchGeneration++
        stopNotificationCollector()
        transactionRefreshJob?.cancel()
        liquidSearchIndexJob?.cancel()
        subscribedScriptHashes.clear()
        scriptHashStatusCache.clear()
        liquidExternalAddressCache.clear()
        liquidInternalAddressCache.clear()
        liquidUsedIndexSummaryCache = LiquidUsedIndexSummary()
        _loadedWalletId.value = null
        _liquidState.value = LiquidWalletState()
    }

    private suspend fun unloadWalletLocked(
        preserveConnection: Boolean,
        visibleStateAlreadyCleared: Boolean = false,
    ) {
        walletSwitchGeneration++
        stopNotificationCollector()
        transactionRefreshJob?.cancel()
        liquidSearchIndexJob?.cancel()
        subscribedScriptHashes.clear()
        scriptHashStatusCache.clear()
        liquidExternalAddressCache.clear()
        liquidInternalAddressCache.clear()
        liquidUsedIndexSummaryCache = LiquidUsedIndexSummary()
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
        if (!visibleStateAlreadyCleared) {
            _liquidState.value = LiquidWalletState()
        }
        if (!preserveConnection) {
            liquidElectrumProxy?.stop()
            liquidElectrumProxy = null
            try {
                lwkClient?.destroy()
            } catch (_: Exception) {}
            lwkClient = null
            _isConnected.value = false
        }
        SecureLog.d(TAG, "Liquid wallet unloaded (preserveConnection=$preserveConnection)")
    }

    suspend fun deleteWalletDatabase(walletId: String) =
        withContext(Dispatchers.IO) {
            if (currentWalletId == walletId) {
                unloadWallet()
            }
            electrumCache.clearTransactionSearchDocuments(walletId)
            if (!deleteWalletDatabaseFiles(walletId)) {
                SecureLog.e(
                    TAG,
                    "Failed to delete Liquid wallet database for $walletId",
                    releaseMessage = "Liquid wallet database cleanup failed",
                )
            }
        }

    fun isWalletLoaded(walletId: String): Boolean = currentWalletId == walletId && lwkWollet != null

    /**
     * Immediately clears visible wallet display state so the UI never renders
     * stale data from the previous wallet. Called synchronously on the main
     * thread before launching the async lifecycle job.
     */
    fun clearWalletDisplayState() {
        _loadedWalletId.value = null
        _liquidState.value = LiquidWalletState(isInitialized = true)
    }

    /**
     * Reads the new wallet's cached balance, address, and transactions from its
     * separate LWK database file and publishes them immediately. This does NOT
     * touch [connectionMutex] or any shared wallet/connection state, so it can
     * run while a previous connection or sync is still finishing.
     *
     * The state published here is a lightweight snapshot — no labels, sources,
     * swap details, or unblinded URLs. [switchWallet] fills those in once it
     * acquires the mutex and calls [refreshLiquidWalletState].
     */
    fun preloadCachedWalletState(walletId: String): Boolean {
        val descriptorStr = secureStorage.getLiquidDescriptor(walletId) ?: return false
        var tempNetwork: Network? = null
        var tempWollet: Wollet? = null
        try {
            val network = Network.mainnet()
            tempNetwork = network
            val descriptor = WolletDescriptor(descriptorStr)
            val dbPath = getLwkDbPath(walletId)
            val wollet = Wollet(network, descriptor, dbPath)
            tempWollet = wollet

            val policyAsset = network.policyAsset()
            val lbtcBalance = wollet.balance()[policyAsset]?.toLong() ?: 0L

            val addr = try {
                resolveEarliestUnusedLiquidExternalAddress(
                    wollet = wollet,
                    walletId = walletId,
                )
            } catch (_: Exception) {
                null
            }

            val transactions = wollet.transactions().mapNotNull { walletTx ->
                try {
                    val txid = walletTx.txid().toString()
                    val lbtcDelta = walletTx.balance()[policyAsset] ?: 0L
                    val txType = walletTx.type()
                    LiquidTransaction(
                        txid = txid,
                        balanceSatoshi = lbtcDelta,
                        fee = walletTx.fee().toLong(),
                        height = walletTx.height()?.toInt(),
                        timestamp = walletTx.timestamp()?.toLong(),
                        type = when {
                            txType == "incoming" -> LiquidTxType.RECEIVE
                            txType == "outgoing" -> LiquidTxType.SEND
                            lbtcDelta >= 0 -> LiquidTxType.RECEIVE
                            else -> LiquidTxType.SEND
                        },
                    )
                } catch (_: Exception) {
                    null
                }
            }.sortedByDescending { it.timestamp ?: Long.MIN_VALUE }

            walletSwitchGeneration++
            stopNotificationCollector()
            subscribedScriptHashes.clear()
            scriptHashStatusCache.clear()

            _liquidState.value = LiquidWalletState(
                walletId = walletId,
                isInitialized = true,
                balanceSats = lbtcBalance,
                transactions = transactions,
                currentAddress = addr?.address,
                currentAddressIndex = addr?.index,
            )
            return true
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.w(TAG, "Preload cached Liquid state failed: ${e.message}")
            return false
        } finally {
            try { tempWollet?.destroy() } catch (_: Exception) {}
            try { tempNetwork?.destroy() } catch (_: Exception) {}
        }
    }

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

    fun getPendingLightningInvoiceSession(swapId: String): PendingLightningInvoiceSession? {
        val walletId = currentWalletId ?: return null
        return secureStorage.getPendingLightningInvoiceSession(walletId, swapId)
    }

    fun getPendingLightningInvoiceSessions(): List<PendingLightningInvoiceSession> {
        val walletId = currentWalletId ?: return emptyList()
        return secureStorage.getPendingLightningInvoiceSessions(walletId)
    }

    fun getPendingLightningInvoices(): List<PendingLightningInvoice> {
        val walletId = currentWalletId ?: return emptyList()
        return secureStorage.getPendingLightningInvoiceSessions(walletId).map { session ->
            val pendingReceive = secureStorage.getPendingLightningReceive(walletId, session.swapId)
            PendingLightningInvoice(
                swapId = session.swapId,
                invoice = session.invoice,
                amountSats = session.amountSats,
                createdAt = session.createdAt,
                claimAddress = pendingReceive?.claimAddress.orEmpty(),
                label = pendingReceive?.label.orEmpty(),
            )
        }
    }

    suspend fun restorePendingLightningInvoice(swapId: String? = null): LightningInvoiceCreation? = withContext(Dispatchers.IO) {
        val walletId = currentWalletId ?: return@withContext null
        val session =
            if (swapId != null) {
                secureStorage.getPendingLightningInvoiceSession(walletId, swapId)
            } else {
                getPendingLightningInvoiceSession()
            } ?: return@withContext null
        val pendingReceive = secureStorage.getPendingLightningReceive(walletId, session.swapId)
        val response = ensureLightningInvoiceResponse(session.swapId)
        LightningInvoiceCreation(
            swapId = session.swapId,
            invoice = session.invoice.ifBlank { response.bolt11Invoice().toString() },
            claimAddress = pendingReceive?.claimAddress.orEmpty(),
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

    private fun signPset(pset: Pset): Pset {
        val walletId = currentWalletId ?: throw Exception("Wallet not loaded")
        val passphrase = secureStorage.getPassphrase(walletId)

        if (passphrase.isNullOrEmpty()) {
            val signer = ensureSigner()
            return signer.sign(pset)
        }

        val mnemonic = secureStorage.getMnemonic(walletId)
            ?: throw Exception("Signer not available (read-only mode)")
        val seed = ElectrumSeedUtil.bip39MnemonicToSeed(mnemonic, passphrase)
        val psetBase64 = pset.toString()
        val signedBase64 = ElementsPsetSigner.sign(psetBase64, seed)
        return Pset(signedBase64)
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
                SecureLog.d(TAG, "Connecting to Liquid Electrum: ${config.displayName()}")
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
                                proxyOwner = "liquid-lwk",
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
                                proxyOwner = "liquid-lwk",
                                useSsl = false,
                                useTorProxy = useTor,
                                connectionTimeoutMs = if (useTor) 30_000 else 15_000,
                                soTimeoutMs = if (useTor) 30_000 else 15_000,
                                cache = electrumCache,
                            )
                        }
                        val localPort = newProxy.start()
                        if (BuildConfig.DEBUG) {
                            Log.d(
                                TAG,
                                "Liquid proxy started on port $localPort -> $cleanHost:${config.port} (tor=$useTor, ssl=${config.useSsl})",
                            )
                        }
                        val client = ElectrumClient.fromUrl("tcp://127.0.0.1:$localPort")
                        newClient = client

                        // Prove the LWK client actually consumed the local proxy before
                        // we publish the connection as active.
                        client.tip()
                        currentCoroutineContext().ensureActive()

                        liquidElectrumProxy = newProxy
                        lwkClient = client
                        _isConnected.value = true

                        SecureLog.d(TAG, "Connected to Liquid Electrum server")
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
                throw Exception("Connection failed", e)
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
                lastKnownBlockHeight = null
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

    suspend fun getServerBlockHeight(): UInt? =
        withContext(Dispatchers.IO) {
            val client = lwkClient ?: return@withContext null
            try {
                client.tip().height()
            } catch (e: Exception) {
                SecureLog.w(TAG, "Failed to query Liquid tip height: ${e.message}", e)
                null
            }
        }

    private fun refreshLiquidWalletState(
        wollet: Wollet,
        updateLastSyncTimestamp: Boolean,
        switchGeneration: Long = walletSwitchGeneration,
    ) {
        val network = lwkNetwork ?: Network.mainnet()
        val policyAsset = network.policyAsset()
        val walletId = currentWalletId

        val balanceMap: Map<String, ULong> = wollet.balance()
        val lbtcBalance = balanceMap[policyAsset]?.toLong() ?: 0L
        val assetBalances = balanceMap.map { (assetId, amount) ->
            LiquidAssetBalance(
                asset = LiquidAsset.resolve(assetId),
                amount = amount.toLong(),
            )
        }.sortedByDescending { it.asset.isPolicyAsset }

        val transactions = buildLiquidTransactionList(wollet, switchGeneration) ?: return

        val existingState = _liquidState.value
        val derivedCurrentAddress = try {
            val addrResult = resolveEarliestUnusedLiquidExternalAddress(
                wollet = wollet,
                walletId = walletId,
            )
            addrResult.index to addrResult.address
        } catch (_: Exception) {
            null
        }
        val currentAddr = derivedCurrentAddress?.second ?: existingState.currentAddress
        val currentAddressIndex = derivedCurrentAddress?.first ?: existingState.currentAddressIndex
        val currentAddressLabel = if (walletId != null && currentAddr != null) {
            secureStorage.getLiquidAddressLabel(walletId, currentAddr)
        } else {
            null
        }

        if (switchGeneration != walletSwitchGeneration) return

        _liquidState.value = _liquidState.value.copy(
            walletId = walletId,
            isInitialized = true,
            balanceSats = lbtcBalance,
            assetBalances = assetBalances,
            transactions = transactions,
            currentAddress = currentAddr,
            currentAddressIndex = currentAddressIndex,
            currentAddressLabel = currentAddressLabel,
            isSyncing = false,
            isTransactionHistoryLoading = false,
            lastSyncTimestamp = if (updateLastSyncTimestamp) {
                System.currentTimeMillis()
            } else {
                _liquidState.value.lastSyncTimestamp
            },
            error = null,
        )
        scheduleLiquidTransactionSearchIndexRefresh(walletId, transactions)
    }

    private fun refreshLiquidWalletStateLightweight(
        wollet: Wollet,
        switchGeneration: Long = walletSwitchGeneration,
        keepSyncing: Boolean = false,
    ) {
        val network = lwkNetwork ?: Network.mainnet()
        val policyAsset = network.policyAsset()
        val walletId = currentWalletId

        val balanceMap: Map<String, ULong> = wollet.balance()
        val lbtcBalance = balanceMap[policyAsset]?.toLong() ?: 0L
        val assetBalances = balanceMap.map { (assetId, amount) ->
            LiquidAssetBalance(
                asset = LiquidAsset.resolve(assetId),
                amount = amount.toLong(),
            )
        }.sortedByDescending { it.asset.isPolicyAsset }

        val existingState = _liquidState.value
        val shouldPreserveDerivedState = existingState.walletId == walletId
        val derivedCurrentAddress = try {
            val addrResult = resolveEarliestUnusedLiquidExternalAddress(
                wollet = wollet,
                walletId = walletId,
            )
            addrResult.index to addrResult.address
        } catch (_: Exception) {
            null
        }
        val currentAddr = derivedCurrentAddress?.second ?: existingState.currentAddress
        val currentAddressIndex = derivedCurrentAddress?.first ?: existingState.currentAddressIndex
        val currentAddressLabel = if (walletId != null && currentAddr != null) {
            secureStorage.getLiquidAddressLabel(walletId, currentAddr)
        } else {
            null
        }

        if (switchGeneration != walletSwitchGeneration) return

        _liquidState.value = existingState.copy(
            walletId = walletId,
            isInitialized = true,
            balanceSats = lbtcBalance,
            assetBalances = assetBalances,
            isTransactionHistoryLoading = true,
            transactions = if (shouldPreserveDerivedState) existingState.transactions else emptyList(),
            currentAddress = currentAddr,
            currentAddressIndex = currentAddressIndex,
            currentAddressLabel = currentAddressLabel,
            isSyncing = keepSyncing,
            lastSyncTimestamp = System.currentTimeMillis(),
            error = null,
        )
    }

    private fun scheduleDetailedLiquidTransactionRefresh(
        wollet: Wollet,
        switchGeneration: Long,
    ): Job {
        transactionRefreshJob?.cancel()
        transactionRefreshJob = repositoryScope.launch {
            try {
                val builtTransactions = buildLiquidTransactionList(wollet, switchGeneration)
                    ?: return@launch
                if (switchGeneration != walletSwitchGeneration) return@launch
                _liquidState.value = _liquidState.value.copy(
                    transactions = builtTransactions,
                    isTransactionHistoryLoading = false,
                )
                scheduleLiquidTransactionSearchIndexRefresh(currentWalletId, builtTransactions)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logWarn("Background Liquid transaction refresh failed", e)
                if (switchGeneration == walletSwitchGeneration) {
                    _liquidState.value = _liquidState.value.copy(
                        isTransactionHistoryLoading = false,
                    )
                }
            }
        }
        return transactionRefreshJob!!
    }

    private fun buildLiquidTransactionList(
        wollet: Wollet,
        switchGeneration: Long,
    ): List<LiquidTransaction>? {
        data class LiquidTxAddressSummary(
            var walletAddress: String? = null,
            var walletAddressAmountSats: Long = 0,
            var changeAddress: String? = null,
            var changeAmountSats: Long = 0,
        )

        val network = lwkNetwork ?: Network.mainnet()
        val policyAsset = network.policyAsset()
        val walletId = currentWalletId
        val metadata = walletId?.let(::getLiquidMetadata)
        val txLabels = metadata?.txLabels?.toMutableMap() ?: mutableMapOf()
        val txSources = metadata?.txSources?.toMutableMap() ?: mutableMapOf()
        val txSwapDetails = metadata?.txSwapDetails?.toMutableMap() ?: mutableMapOf()
        val txRecipients = metadata?.txRecipients ?: emptyMap()
        val txFeeDetails = metadata?.txFeeDetails ?: emptyMap()
        val pendingLightningReceivesByAddress =
            metadata?.pendingLightningReceives?.associateBy(PendingLightningReceive::claimAddress)?.toMutableMap()
                ?: mutableMapOf()

        val txAddressSummaries = mutableMapOf<String, LiquidTxAddressSummary>()
        try {
            wollet.txos().forEach { txo ->
                val txid = txo.outpoint().txid().toString()
                val address = txo.address().toString()
                val value = txo.unblinded().value().toLong()
                val summary = txAddressSummaries.getOrPut(txid) { LiquidTxAddressSummary() }
                if (txo.extInt() == Chain.INTERNAL) {
                    if (summary.changeAddress == null) summary.changeAddress = address
                    summary.changeAmountSats += value
                } else {
                    if (summary.walletAddress == null) summary.walletAddress = address
                    summary.walletAddressAmountSats += value
                }
            }
        } catch (e: Exception) {
            logWarn("Failed to derive Liquid transaction address summaries", e)
        }

        if (switchGeneration != walletSwitchGeneration) return null

        val wolletTransactions = wollet.transactions()
        prewarmTxidList(wolletTransactions.map { it.txid().toString() })

        val liquidExplorerUrl = secureStorage.getLiquidExplorerUrl().takeIf { it.isNotBlank() }
        return wolletTransactions
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

                    if (walletId != null && pendingLightningReceive != null) {
                        if (existingSource == null) {
                            secureStorage.saveLiquidTransactionSource(walletId, txid, LiquidTxSource.LIGHTNING_RECEIVE_SWAP)
                            txSources[txid] = LiquidTxSource.LIGHTNING_RECEIVE_SWAP
                        }
                        secureStorage.getPendingLightningInvoiceSession(walletId, pendingLightningReceive.swapId)
                            ?.let { secureStorage.deletePendingLightningInvoiceSession(walletId, pendingLightningReceive.swapId) }
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
                    val unblindedUrl =
                        liquidExplorerUrl?.let { explorerBaseUrl ->
                            try {
                                walletTx.unblindedUrl(explorerBaseUrl).takeIf { it.isNotBlank() }
                            } catch (e: Exception) {
                                logWarn("Failed to build Liquid unblinded explorer URL for $txid", e)
                                null
                            }
                        }

                    LiquidTransaction(
                        txid = txid,
                        balanceSatoshi = lbtcDelta,
                        fee = fee,
                        feeRate = txFeeDetails[txid]?.feeRate,
                        vsize = txFeeDetails[txid]?.vsize,
                        assetDeltas = balMap,
                        height = height,
                        timestamp = timestamp,
                        unblindedUrl = unblindedUrl,
                        walletAddress = addressSummary?.walletAddress,
                        walletAddressAmountSats =
                            addressSummary?.walletAddressAmountSats?.takeIf { it > 0L },
                        changeAddress = addressSummary?.changeAddress,
                        changeAmountSats =
                            addressSummary?.changeAmountSats?.takeIf { it > 0L },
                        recipientAddress = txRecipients[txid],
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
    }

    fun saveLiquidTransactionSource(
        txid: String,
        source: LiquidTxSource,
    ) {
        val walletId = currentWalletId ?: return
        secureStorage.saveLiquidTransactionSource(walletId, txid, source)
        invalidateMetadataCache()
        updateTransactionInPlace(txid) { it.copy(source = source) }
    }

    fun saveLiquidTransactionLabel(
        txid: String,
        label: String,
    ) {
        val walletId = currentWalletId ?: return
        secureStorage.saveLiquidTransactionLabel(walletId, txid, label)
        electrumCache.updateTransactionSearchLabel(
            walletId = walletId,
            layer = TransactionSearchLayer.LIQUID,
            txid = txid,
            label = label,
        )
        invalidateMetadataCache()
        updateTransactionInPlace(txid) { it.copy(memo = label) }
    }

    fun deleteLiquidTransactionLabel(txid: String) {
        val walletId = currentWalletId ?: return
        secureStorage.deleteLiquidTransactionLabel(walletId, txid)
        electrumCache.updateTransactionSearchLabel(
            walletId = walletId,
            layer = TransactionSearchLayer.LIQUID,
            txid = txid,
            label = "",
        )
        invalidateMetadataCache()
        updateTransactionInPlace(txid) { it.copy(memo = "") }
    }

    fun searchLiquidTransactionTxids(
        query: String,
        includeSwap: Boolean,
        includeLightning: Boolean,
        includeNative: Boolean,
        includeUsdt: Boolean,
        limit: Int,
    ): TransactionSearchResult {
        val walletId = currentWalletId ?: return TransactionSearchResult(emptyList(), 0)
        return electrumCache.searchTransactionTxids(
            walletId = walletId,
            layer = TransactionSearchLayer.LIQUID,
            query = query,
            limit = limit,
            filters =
                TransactionSearchFilters(
                    includeSwap = includeSwap,
                    includeLightning = includeLightning,
                    includeNative = includeNative,
                    includeUsdt = includeUsdt,
                ),
        )
    }

    fun saveLiquidSwapDetails(
        txid: String,
        details: LiquidSwapDetails,
    ) {
        val walletId = currentWalletId ?: return
        secureStorage.saveLiquidSwapDetails(walletId, txid, details)
        invalidateMetadataCache()
        updateTransactionInPlace(txid) { it.copy(swapDetails = details) }
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
        paymentInput: String? = null,
        resolvedPaymentInput: String? = null,
        invoice: String? = null,
        status: String? = null,
        timeoutBlockHeight: Int? = null,
        refundPublicKey: String? = null,
        claimPublicKey: String? = null,
        swapTree: String? = null,
        blindingKey: String? = null,
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
            paymentInput = paymentInput,
            resolvedPaymentInput = resolvedPaymentInput,
            invoice = invoice,
            status = status,
            timeoutBlockHeight = timeoutBlockHeight,
            refundPublicKey = refundPublicKey,
            claimPublicKey = claimPublicKey,
            swapTree = swapTree,
            blindingKey = blindingKey,
        )
    }

    fun finalizeLightningReceiveTransaction(
        swapId: String,
        txid: String,
    ) {
        val walletId = currentWalletId ?: return
        val pendingReceive = secureStorage.getPendingLightningReceive(walletId, swapId)
        val invoiceSession = secureStorage.getPendingLightningInvoiceSession(walletId, swapId)
        secureStorage.saveLiquidTransactionSource(walletId, txid, LiquidTxSource.LIGHTNING_RECEIVE_SWAP)
        pendingReceive?.let { receive ->
            saveLiquidSwapDetails(
                txid = txid,
                details = buildLightningReceiveSwapDetails(receive, invoiceSession),
            )
        }
        pendingReceive?.label
            ?.takeIf { it.isNotBlank() }
            ?.let {
                secureStorage.saveLiquidAddressLabel(walletId, pendingReceive.claimAddress, it)
                secureStorage.saveLiquidTransactionLabel(walletId, txid, it)
                electrumCache.updateTransactionSearchLabel(
                    walletId = walletId,
                    layer = TransactionSearchLayer.LIQUID,
                    txid = txid,
                    label = it,
                )
            }
        secureStorage.deletePendingLightningReceive(walletId, swapId)
        if (invoiceSession != null) {
            secureStorage.deletePendingLightningInvoiceSession(walletId, swapId)
        }
        invalidateMetadataCache()
    }

    fun persistLightningSendSwapDetails(
        txid: String,
        swapId: String,
    ) {
        if (txid.isBlank()) return
        val session = getPendingLightningPaymentSession()?.takeIf { it.swapId == swapId } ?: return
        saveLiquidSwapDetails(
            txid = txid,
            details = buildLightningSendSwapDetails(session),
        )
    }

    private fun buildLightningSendSwapDetails(session: PendingLightningPaymentSession): LiquidSwapDetails {
        return buildLiquidSwapDetails(
            service = SwapService.BOLTZ,
            direction = SwapDirection.LBTC_TO_BTC,
            swapId = session.swapId,
            role = LiquidSwapTxRole.FUNDING,
            depositAddress = session.lockupAddress,
            refundAddress = session.refundAddress,
            sendAmountSats = session.lockupAmountSats,
            expectedReceiveAmountSats = session.paymentAmountSats,
            paymentInput = session.paymentInput,
            resolvedPaymentInput = session.resolvedPaymentInput,
            invoice = session.fetchedInvoice,
            status = session.status,
            timeoutBlockHeight = session.timeoutBlockHeight,
            refundPublicKey = session.refundPublicKey,
            claimPublicKey = session.boltzClaimPublicKey,
            swapTree = session.swapTree,
            blindingKey = session.blindingKey,
        )
    }

    private fun buildLightningReceiveSwapDetails(
        pendingReceive: PendingLightningReceive,
        invoiceSession: PendingLightningInvoiceSession?,
    ): LiquidSwapDetails {
        return buildLiquidSwapDetails(
            service = SwapService.BOLTZ,
            direction = SwapDirection.BTC_TO_LBTC,
            swapId = pendingReceive.swapId,
            role = LiquidSwapTxRole.SETTLEMENT,
            depositAddress = pendingReceive.claimAddress,
            receiveAddress = pendingReceive.claimAddress,
            expectedReceiveAmountSats = invoiceSession?.amountSats ?: 0L,
            paymentInput = invoiceSession?.invoice,
            invoice = invoiceSession?.invoice,
            status = "Invoice paid",
        )
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
                SecureLog.d(
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

    private fun isTransientLwkSyncError(error: Throwable): Boolean {
        val details = generateSequence(error) { it.cause }
            .mapNotNull { throwable -> throwable.message?.trim()?.takeIf { it.isNotEmpty() } }
            .joinToString(separator = " | ")
        if (details.isBlank()) return false
        return details.contains("AllAttemptsErrored", ignoreCase = true) ||
            details.contains("timed out", ignoreCase = true) ||
            details.contains("Timeout(", ignoreCase = true) ||
            details.contains("Connection reset", ignoreCase = true) ||
            details.contains("Broken pipe", ignoreCase = true) ||
            details.contains("EOF", ignoreCase = true)
    }

    private data class LiquidUsedIndexSummary(
        val externalUsedIndices: Set<UInt> = emptySet(),
        val highestExternalUsedIndex: Int = -1,
        val highestInternalUsedIndex: Int = -1,
    ) {
        val highestUsedIndex: Int get() = maxOf(highestExternalUsedIndex, highestInternalUsedIndex)
    }

    private data class LiquidExternalAddressSelection(
        val index: UInt,
        val address: String,
    )

    private var liquidUsedIndexSummaryCache = LiquidUsedIndexSummary()

    private fun collectLiquidUsedIndices(wollet: Wollet): LiquidUsedIndexSummary {
        val externalUsedIndices = mutableSetOf<UInt>()
        var highestExternalUsedIndex = -1
        var highestInternalUsedIndex = -1

        runCatching {
            wollet.txos().forEach { txo ->
                val index = txo.wildcardIndex().toInt()
                if (txo.extInt() == Chain.INTERNAL) {
                    highestInternalUsedIndex = maxOf(highestInternalUsedIndex, index)
                } else {
                    externalUsedIndices.add(txo.wildcardIndex())
                    highestExternalUsedIndex = maxOf(highestExternalUsedIndex, index)
                }
            }
        }.onFailure { error ->
            val exception = error as? Exception ?: Exception(error.message ?: "Unknown Liquid scan error", error)
            logWarn("Failed to inspect Liquid used indices during full scan", exception)
        }

        return LiquidUsedIndexSummary(
            externalUsedIndices = externalUsedIndices,
            highestExternalUsedIndex = highestExternalUsedIndex,
            highestInternalUsedIndex = highestInternalUsedIndex,
        ).also { liquidUsedIndexSummaryCache = it }
    }

    private fun collectReservedLiquidExternalAddresses(walletId: String?): Set<String> {
        if (walletId == null) return emptySet()

        val reservedAddresses = linkedSetOf<String>()

        secureStorage.getAllPendingLightningReceives(walletId).forEach { pendingReceive ->
            reservedAddresses.add(pendingReceive.claimAddress)
        }

        secureStorage.getPendingLightningPaymentSession(walletId)
            ?.refundAddress
            ?.takeIf { it.isNotBlank() }
            ?.let(reservedAddresses::add)

        secureStorage.getPendingSwaps(walletId).forEach { pendingSwap ->
            pendingSwap.receiveAddress
                ?.takeIf { it.isNotBlank() }
                ?.let(reservedAddresses::add)
            pendingSwap.refundAddress
                ?.takeIf { it.isNotBlank() }
                ?.let(reservedAddresses::add)
        }

        synchronized(boltzChainSwapDrafts) {
            boltzChainSwapDrafts.values.forEach { draft ->
                draft.liquidAddress
                    ?.takeIf { it.isNotBlank() }
                    ?.let(reservedAddresses::add)
                draft.receiveAddress
                    ?.takeIf { it.isNotBlank() }
                    ?.let(reservedAddresses::add)
                draft.refundAddress
                    ?.takeIf { it.isNotBlank() }
                    ?.let(reservedAddresses::add)
            }
        }

        return reservedAddresses
    }

    private fun resolveEarliestUnusedLiquidExternalAddress(
        wollet: Wollet,
        walletId: String? = currentWalletId,
    ): LiquidExternalAddressSelection {
        val occupiedIndices = collectLiquidUsedIndices(wollet).externalUsedIndices.toMutableSet()
        val reservedAddresses = collectReservedLiquidExternalAddresses(walletId)

        repeat(MAX_LIQUID_ADDRESS_SCAN_ATTEMPTS) {
            val candidateIndex = findEarliestUnusedLiquidExternalIndex(occupiedIndices)
            val candidateAddress = wollet.address(candidateIndex).address().toString()
            if (candidateAddress !in reservedAddresses) {
                return LiquidExternalAddressSelection(
                    index = candidateIndex,
                    address = candidateAddress,
                )
            }
            occupiedIndices.add(candidateIndex)
        }

        throw IllegalStateException("Failed to derive earliest unused Liquid address")
    }

    private fun resolveFreshLiquidExternalAddress(
        wollet: Wollet,
        walletId: String? = currentWalletId,
        currentIndex: UInt? = _liquidState.value.currentAddressIndex,
    ): LiquidExternalAddressSelection {
        if (currentIndex == null) {
            return resolveEarliestUnusedLiquidExternalAddress(
                wollet = wollet,
                walletId = walletId,
            )
        }

        val occupiedIndices = liquidUsedIndexSummaryCache.externalUsedIndices
        val reservedAddresses = collectReservedLiquidExternalAddresses(walletId)
        var candidateIndex = currentIndex + 1u

        repeat(MAX_LIQUID_ADDRESS_SCAN_ATTEMPTS) {
            val candidateAddress = liquidExternalAddressAt(wollet, candidateIndex)
            if (candidateIndex !in occupiedIndices && candidateAddress !in reservedAddresses) {
                return LiquidExternalAddressSelection(
                    index = candidateIndex,
                    address = candidateAddress,
                )
            }
            candidateIndex++
        }

        throw IllegalStateException("Failed to derive fresh Liquid address")
    }

    private suspend fun runFullScanWithTimeout(
        client: ElectrumClient,
        wollet: Wollet,
        liquidGapLimit: Int,
        showFullSyncProgress: Boolean,
    ): Boolean = withTimeout(LIQUID_SYNC_TIMEOUT_MS) {
        val stopGap = liquidGapLimit.coerceAtLeast(1)
        var targetIndex = stopGap - 1
        var hadChanges = false

        while (currentCoroutineContext().isActive) {
            val scannedAddressCount = (targetIndex + 1).toULong()
            updateLiquidFullSyncProgress(
                showFullSyncProgress = showFullSyncProgress,
                status = "Scanned $scannedAddressCount addresses...",
                current = scannedAddressCount,
                total = 0UL,
            )

            val update = client.fullScanToIndex(wollet, targetIndex.toUInt())
            if (update != null) {
                wollet.applyUpdate(update)
                hadChanges = true
            }

            val usedIndices = collectLiquidUsedIndices(wollet)
            val requiredTargetIndex = usedIndices.highestUsedIndex + stopGap
            if (requiredTargetIndex <= targetIndex) {
                break
            }

            targetIndex = requiredTargetIndex
        }

        hadChanges
    }

    /**
     * Sync the Liquid wallet with the Electrum server.
     * Updates balance and transaction history.
     */
    private fun updateLiquidFullSyncProgress(
        showFullSyncProgress: Boolean,
        status: String,
        current: ULong? = null,
        total: ULong? = null,
    ) {
        if (!showFullSyncProgress) return
        val existingProgress = _liquidState.value.syncProgress
        _liquidState.value = _liquidState.value.copy(
            isFullSyncing = true,
            syncProgress = SyncProgress(
                current = current ?: existingProgress?.current ?: 0UL,
                total = total ?: existingProgress?.total ?: 0UL,
                status = status,
            ),
        )
    }

    private fun clearLiquidFullSyncProgress(showFullSyncProgress: Boolean) {
        if (!showFullSyncProgress) return
        _liquidState.value = _liquidState.value.copy(
            isFullSyncing = false,
            syncProgress = null,
            isSyncing = false,
            isTransactionHistoryLoading = false,
        )
    }

    suspend fun sync(
        forceFullScan: Boolean = false,
        showFullSyncProgress: Boolean = false,
    ): Boolean {
        if (!syncMutex.tryLock()) {
            if (BuildConfig.DEBUG) Log.d(TAG, "Liquid sync already in progress, queuing re-sync")
            pendingSyncRequested = true
            pendingForceFullSyncRequested = pendingForceFullSyncRequested || forceFullScan
            pendingFullSyncProgressRequested = pendingFullSyncProgressRequested || showFullSyncProgress
            return false
        }
        try {
            return withContext(Dispatchers.IO) {
                syncInternal(
                    forceFullScan = forceFullScan,
                    showFullSyncProgress = showFullSyncProgress,
                )
            }
        } finally {
            syncMutex.unlock()
            if (pendingSyncRequested) {
                val queuedForceFullSync = pendingForceFullSyncRequested
                val queuedShowFullSyncProgress = pendingFullSyncProgressRequested
                pendingSyncRequested = false
                pendingForceFullSyncRequested = false
                pendingFullSyncProgressRequested = false
                repositoryScope.launch {
                    sync(
                        forceFullScan = queuedForceFullSync,
                        showFullSyncProgress = queuedShowFullSyncProgress,
                    )
                }
            }
        }
    }

    private suspend fun syncInternal(
        forceFullScan: Boolean,
        showFullSyncProgress: Boolean,
    ): Boolean {
        val syncGeneration = walletSwitchGeneration
        val wollet = lwkWollet ?: run {
            SecureLog.w(TAG, "Cannot sync: wallet not loaded")
            return false
        }
        val client = lwkClient ?: run {
            SecureLog.w(TAG, "Cannot sync: not connected")
            return false
        }

        return try {
            if (syncGeneration != walletSwitchGeneration) return false
            pendingSyncRequested = false
            pendingForceFullSyncRequested = false
            pendingFullSyncProgressRequested = false
            _liquidState.value = _liquidState.value.copy(isSyncing = true)
            val currentTipHeight =
                runCatching { client.tip().height() }
                    .getOrNull()
            val previousTipHeight = lastKnownBlockHeight
            val newBlockDetected =
                currentTipHeight != null &&
                    previousTipHeight != null &&
                    currentTipHeight > previousTipHeight
            updateLiquidFullSyncProgress(
                showFullSyncProgress = showFullSyncProgress,
                status = "Preparing sync...",
                current = 0UL,
                total = 0UL,
            )

            if (!forceFullScan && !newBlockDetected && scriptHashStatusCache.isNotEmpty()) {
                val hasChanges = liquidElectrumProxy?.checkForScriptHashChanges(scriptHashStatusCache) ?: true
                if (!hasChanges) {
                    if (syncGeneration != walletSwitchGeneration) return false
                    if (currentTipHeight != null) {
                        lastKnownBlockHeight = currentTipHeight
                    }
                    scheduleLiquidTransactionSearchIndexRefresh(currentWalletId, _liquidState.value.transactions)
                    _liquidState.value = _liquidState.value.copy(
                        isSyncing = false,
                        lastSyncTimestamp = System.currentTimeMillis(),
                        error = null,
                    )
                    return true
                }
            }

            prewarmLiquidTransactionHistoryCache(wollet)

            val walletId = currentWalletId
            val liquidGapLimit = if (walletId != null) {
                secureStorage.getLiquidGapLimit(walletId)
            } else {
                StoredWallet.DEFAULT_GAP_LIMIT
            }
            val hadChanges = try {
                runFullScanWithTimeout(
                    client = client,
                    wollet = wollet,
                    liquidGapLimit = liquidGapLimit,
                    showFullSyncProgress = showFullSyncProgress,
                )
            } catch (e: Exception) {
                if (isTransientLwkSyncError(e)) {
                    if (BuildConfig.DEBUG) Log.w(TAG, "Transient Liquid sync error, retrying: ${e.message}")
                    delay(1_000)
                    runFullScanWithTimeout(
                        client = client,
                        wollet = wollet,
                        liquidGapLimit = liquidGapLimit,
                        showFullSyncProgress = showFullSyncProgress,
                    )
                } else throw e
            }
            if (syncGeneration != walletSwitchGeneration) return false
            if (hadChanges) {
                updateLiquidFullSyncProgress(
                    showFullSyncProgress = showFullSyncProgress,
                    status = "Applying updates...",
                    current = 0UL,
                    total = 0UL,
                )
                refreshLiquidWalletStateLightweight(
                    wollet = wollet,
                    switchGeneration = syncGeneration,
                    keepSyncing = showFullSyncProgress,
                )
                updateLiquidFullSyncProgress(
                    showFullSyncProgress = showFullSyncProgress,
                    status = "Refreshing address cache...",
                    current = 0UL,
                    total = 0UL,
                )
                val detailedRefreshJob = scheduleDetailedLiquidTransactionRefresh(wollet, syncGeneration)
                if (showFullSyncProgress) {
                    detailedRefreshJob.join()
                }
                refreshScriptHashStatusCache(wollet)
                subscribeNewlyRevealedAddresses()
                lastKnownBlockHeight =
                    runCatching { client.tip().height() }
                        .getOrNull()
                        ?: currentTipHeight
                        ?: lastKnownBlockHeight
            } else {
                if (syncGeneration == walletSwitchGeneration) {
                    if (currentTipHeight != null) {
                        lastKnownBlockHeight = currentTipHeight
                    }
                    _liquidState.value = _liquidState.value.copy(
                        isSyncing = false,
                        lastSyncTimestamp = System.currentTimeMillis(),
                        error = null,
                    )
                }
            }

            SecureLog.d(
                TAG,
                "Liquid sync complete: balance=${_liquidState.value.balanceSats} sats, ${_liquidState.value.transactions.size} txs",
            )
            true
        } catch (e: LwkException) {
            logError("LWK sync failed", e)
            if (syncGeneration == walletSwitchGeneration) {
                _liquidState.value = _liquidState.value.copy(
                    isSyncing = false,
                    error = "Sync failed: ${e.message}",
                )
            }
            false
        } catch (e: CancellationException) {
            if (syncGeneration == walletSwitchGeneration) {
                _liquidState.value = _liquidState.value.copy(
                    isSyncing = false,
                    error = null,
                )
            }
            throw e
        } catch (e: Exception) {
            logError("Liquid sync failed", e)
            if (syncGeneration == walletSwitchGeneration) {
                _liquidState.value = _liquidState.value.copy(
                    isSyncing = false,
                    error = "Sync failed: ${e.message}",
                )
            }
            false
        } finally {
            if (syncGeneration == walletSwitchGeneration) {
                clearLiquidFullSyncProgress(showFullSyncProgress)
            }
        }
    }

    fun refreshCachedWalletState() {
        val wollet = lwkWollet ?: return
        invalidateMetadataCache()
        refreshLiquidWalletState(
            wollet = wollet,
            updateLastSyncTimestamp = false,
        )
    }

    fun patchCurrentAddressLabelIfCurrent(
        address: String,
        label: String?,
    ) {
        val currentState = _liquidState.value
        if (currentState.currentAddress != address || currentState.currentAddressLabel == label) return
        _liquidState.value = currentState.copy(currentAddressLabel = label)
    }

    private fun updateTransactionInPlace(
        txid: String,
        transform: (LiquidTransaction) -> LiquidTransaction,
    ) {
        val currentState = _liquidState.value
        val existingTx = currentState.transactions.firstOrNull { it.txid == txid } ?: return
        val updatedTx = transform(existingTx)
        if (updatedTx == existingTx) return
        _liquidState.value = currentState.copy(
            transactions = currentState.transactions.map { if (it.txid == txid) updatedTx else it },
        )
        upsertLiquidTransactionSearchDocument(currentWalletId, updatedTx)
    }

    private fun prewarmLiquidTransactionHistoryCache(wollet: Wollet) {
        prewarmTxidList(wollet.transactions().mapTo(linkedSetOf()) { it.txid().toString() }.toList())
    }

    private fun prewarmTxidList(knownTxids: List<String>) {
        try {
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
                SecureLog.w(TAG, "Failed to start Liquid subscriptions: proxy returned empty status set")
                return@withContext SubscriptionResult.FAILED
            }

            lastKnownBlockHeight =
                runCatching { lwkClient?.tip()?.height() }
                    .getOrNull()

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
        val collectorGeneration = walletSwitchGeneration
        notificationCollectorJob =
            repositoryScope.launch {
                val pendingNotifications = mutableListOf<ElectrumNotification>()
                var lastNotificationTime: Long

                proxy.notifications.collect { notification ->
                    if (!isActive || collectorGeneration != walletSwitchGeneration) return@collect
                    if (notification is ElectrumNotification.ConnectionLost) {
                        SecureLog.w(TAG, "Liquid subscription socket reported connection lost")
                        _connectionEvents.tryEmit(ConnectionEvent.ConnectionLost)
                        return@collect
                    }

                    if (notification is ElectrumNotification.NewBlockHeader) {
                        lastKnownBlockHeight = notification.height.toUInt()
                    }

                    pendingNotifications.add(notification)
                    lastNotificationTime = System.currentTimeMillis()

                    launch debounceSync@{
                        delay(NOTIFICATION_DEBOUNCE_MS)
                        if (collectorGeneration != walletSwitchGeneration) return@debounceSync
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
        val usedIndices = collectLiquidUsedIndices(wollet)
        val reservedAddresses = collectReservedLiquidExternalAddresses(currentWalletId)
        val currentReceive =
            runCatching {
                resolveEarliestUnusedLiquidExternalAddress(wollet)
            }.getOrNull()

        runCatching {
            val coverageIndex = maxOf(currentReceive?.index?.toInt() ?: -1, usedIndices.highestExternalUsedIndex)
            if (coverageIndex < 0) return@runCatching
            for (i in 0..coverageIndex) {
                val address = wollet.address(i.toUInt()).address().toString()
                computeLiquidScriptHashForAddress(address)?.let(scriptHashes::add)
            }
        }

        reservedAddresses.forEach { address ->
            computeLiquidScriptHashForAddress(address)?.let(scriptHashes::add)
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

    /** Get the earliest unused receive address (confidential Liquid address). */
    fun getNewAddress(): String? {
        val wollet = lwkWollet ?: return null
        return try {
            val addressResult = resolveEarliestUnusedLiquidExternalAddress(
                wollet = wollet,
                walletId = currentWalletId,
            )
            val address = addressResult.address
            val addressIndex = addressResult.index
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

    /** Explicit "New" requests should advance past the currently shown receive address. */
    fun generateFreshAddress(): String? {
        val wollet = lwkWollet ?: return null
        return try {
            val addressResult = resolveFreshLiquidExternalAddress(
                wollet = wollet,
                walletId = currentWalletId,
            )
            val address = addressResult.address
            val addressIndex = addressResult.index
            val walletId = currentWalletId
            val label = if (walletId != null) secureStorage.getLiquidAddressLabel(walletId, address) else null
            _liquidState.value = _liquidState.value.copy(
                currentAddress = address,
                currentAddressIndex = addressIndex,
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

    /**
     * Memoised external-address derivation. `wollet.address(i)` crosses a JNI boundary and
     * is called hundreds–thousands of times on large wallets when rebuilding the address book.
     */
    private fun liquidExternalAddressAt(wollet: Wollet, index: UInt): String {
        liquidExternalAddressCache[index]?.let { return it }
        val address = wollet.address(index).address().toString()
        liquidExternalAddressCache[index] = address
        return address
    }

    /**
     * Memoised internal (change) address derivation. Each derivation runs SLIP-77 blinding
     * key derivation + blech32 confidential encoding in Kotlin — cheap per call, but the
     * peek-ahead range can produce dozens of derivations per [getAllAddresses] invocation.
     */
    private fun liquidInternalAddressAt(
        descriptor: WolletDescriptor,
        isMainnet: Boolean,
        index: UInt,
    ): String? {
        liquidInternalAddressCache[index]?.let { return it }
        val script = descriptor.scriptPubkey(Chain.INTERNAL, index)
        val scriptBytes = script.bytes()
        if (scriptBytes.size < 2) return null
        val blindingKey = descriptor.deriveBlindingKey(script) ?: return null
        val blindingPubKey = ElectrumSeedUtil.publicKeyFromPrivate(blindingKey.bytes())
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
        liquidInternalAddressCache[index] = address
        return address
    }

    fun getAddressPreview(limitPerSection: Int): Triple<List<WalletAddress>, List<WalletAddress>, List<WalletAddress>> {
        val wollet = lwkWollet ?: return Triple(emptyList(), emptyList(), emptyList())
        val walletId = currentWalletId ?: return Triple(emptyList(), emptyList(), emptyList())
        val network = lwkNetwork ?: return Triple(emptyList(), emptyList(), emptyList())
        val labels = secureStorage.getAllLiquidAddressLabels(walletId)
        val balanceByAddress = HashMap<String, ULong>()
        val addressesWithUtxos = HashSet<String>()
        val externalTxCountByIndex = HashMap<UInt, Int>()
        val internalTxCountByIndex = HashMap<UInt, Int>()

        try {
            val policyAsset = network.policyAsset()
            wollet.utxos().forEach { utxo ->
                val address = utxo.address().toString()
                addressesWithUtxos.add(address)
                val secrets = utxo.unblinded()
                if (secrets.asset() == policyAsset) {
                    balanceByAddress[address] = (balanceByAddress[address] ?: 0UL) + secrets.value()
                }
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Error listing Liquid UTXOs for address preview", e)
        }

        val usedSummary = collectLiquidUsedIndices(wollet)
        try {
            wollet.txos().forEach { txo ->
                val idx = txo.wildcardIndex()
                when (txo.extInt()) {
                    Chain.EXTERNAL -> externalTxCountByIndex[idx] = (externalTxCountByIndex[idx] ?: 0) + 1
                    Chain.INTERNAL -> internalTxCountByIndex[idx] = (internalTxCountByIndex[idx] ?: 0) + 1
                }
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Error listing Liquid TXOs for address preview", e)
        }

        val usedAddresses = mutableListOf<WalletAddress>()

        usedSummary.externalUsedIndices.sorted().take(limitPerSection).forEach { index ->
            val address = liquidExternalAddressAt(wollet, index)
            usedAddresses.add(
                WalletAddress(
                    address = address,
                    index = index,
                    keychain = KeychainType.EXTERNAL,
                    label = labels[address],
                    balanceSats = balanceByAddress[address] ?: 0UL,
                    transactionCount = externalTxCountByIndex[index] ?: 0,
                    isUsed = true,
                ),
            )
        }

        val receiveAddresses = mutableListOf<WalletAddress>()
        var externalIndex = 0u
        while (receiveAddresses.size < limitPerSection) {
            if (externalIndex !in usedSummary.externalUsedIndices) {
                val address = liquidExternalAddressAt(wollet, externalIndex)
                receiveAddresses.add(
                    WalletAddress(
                        address = address,
                        index = externalIndex,
                        keychain = KeychainType.EXTERNAL,
                        label = labels[address],
                        balanceSats = 0UL,
                        transactionCount = 0,
                        isUsed = false,
                    ),
                )
            }
            externalIndex++
        }

        val changeAddresses = mutableListOf<WalletAddress>()
        try {
            val descriptor = wollet.descriptor()
            val isMainnet = network.isMainnet()
            internalTxCountByIndex.keys.sorted().take(limitPerSection).forEach { index ->
                val address = liquidInternalAddressAt(descriptor, isMainnet, index) ?: return@forEach
                usedAddresses.add(
                    WalletAddress(
                        address = address,
                        index = index,
                        keychain = KeychainType.INTERNAL,
                        label = labels[address],
                        balanceSats = balanceByAddress[address] ?: 0UL,
                        transactionCount = internalTxCountByIndex[index] ?: 0,
                        isUsed = true,
                    ),
                )
            }

            var internalIndex = 0u
            while (changeAddresses.size < limitPerSection) {
                if (internalIndex !in internalTxCountByIndex) {
                    val address = liquidInternalAddressAt(descriptor, isMainnet, internalIndex)
                    if (address != null) {
                        changeAddresses.add(
                            WalletAddress(
                                address = address,
                                index = internalIndex,
                                keychain = KeychainType.INTERNAL,
                                label = labels[address],
                                balanceSats = 0UL,
                                transactionCount = 0,
                                isUsed = false,
                            ),
                        )
                    }
                }
                internalIndex++
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Error deriving Liquid address preview", e)
        }

        return Triple(receiveAddresses, changeAddresses, usedAddresses)
    }

    fun getAllAddresses(): Triple<List<WalletAddress>, List<WalletAddress>, List<WalletAddress>> {
        val wollet = lwkWollet ?: return Triple(emptyList(), emptyList(), emptyList())
        val walletId = currentWalletId ?: return Triple(emptyList(), emptyList(), emptyList())
        val network = lwkNetwork ?: return Triple(emptyList(), emptyList(), emptyList())
        val labels = secureStorage.getAllLiquidAddressLabels(walletId)

        // Bucket balances/counts by (chain, index) from wallet-owned utxos/txos. For UTXOs
        // we still need the address string (policy-asset only balances), but for txos we can
        // aggregate per-index and derive the address lazily through the peek cache, avoiding
        // thousands of `txo.address()` JNI calls on wallets with deep history.
        val balanceByAddress = HashMap<String, ULong>()
        val addressesWithUtxos = HashSet<String>()
        val externalTxCountByIndex = HashMap<UInt, Int>()
        val internalTxCountByIndex = HashMap<UInt, Int>()
        var maxInternalIndex = -1
        var maxExternalIndexFromTxos = -1

        try {
            val policyAsset = network.policyAsset()
            wollet.utxos().forEach { utxo ->
                val address = utxo.address().toString()
                addressesWithUtxos.add(address)
                val secrets = utxo.unblinded()
                if (secrets.asset() == policyAsset) {
                    balanceByAddress[address] = (balanceByAddress[address] ?: 0UL) + secrets.value()
                }
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Error listing Liquid UTXOs for address balances", e)
        }

        try {
            wollet.txos().forEach { txo ->
                val idx = txo.wildcardIndex()
                when (txo.extInt()) {
                    Chain.EXTERNAL -> {
                        externalTxCountByIndex[idx] = (externalTxCountByIndex[idx] ?: 0) + 1
                        if (idx.toInt() > maxExternalIndexFromTxos) maxExternalIndexFromTxos = idx.toInt()
                    }
                    Chain.INTERNAL -> {
                        internalTxCountByIndex[idx] = (internalTxCountByIndex[idx] ?: 0) + 1
                        if (idx.toInt() > maxInternalIndex) maxInternalIndex = idx.toInt()
                    }
                }
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Error listing Liquid TXOs", e)
        }

        val receiveAddresses = mutableListOf<WalletAddress>()
        val changeAddresses = mutableListOf<WalletAddress>()
        val usedAddresses = mutableListOf<WalletAddress>()

        // ── Change (internal) keychain ─────────────────────────────────
        val internalByIndex = linkedMapOf<UInt, WalletAddress>()
        try {
            val descriptor = wollet.descriptor()
            val isMainnet = network.isMainnet()
            val peekEnd = maxInternalIndex + ADDRESS_INTERNAL_PEEK_AHEAD
            for (i in 0..peekEnd) {
                val idx = i.toUInt()
                val txCount = internalTxCountByIndex[idx] ?: 0
                val address = liquidInternalAddressAt(descriptor, isMainnet, idx) ?: continue
                val balance = balanceByAddress[address] ?: 0UL
                val isUsed = txCount > 0 || address in addressesWithUtxos
                internalByIndex[idx] =
                    WalletAddress(
                        address = address,
                        index = idx,
                        keychain = KeychainType.INTERNAL,
                        label = labels[address],
                        balanceSats = balance,
                        transactionCount = txCount,
                        isUsed = isUsed,
                    )
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Error deriving Liquid change addresses", e)
        }

        // ── Receive (external) keychain ────────────────────────────────
        val usedSummary = collectLiquidUsedIndices(wollet)
        val currentReceive =
            runCatching {
                resolveEarliestUnusedLiquidExternalAddress(
                    wollet = wollet,
                    walletId = walletId,
                )
            }.getOrNull()

        try {
            val lastExternalIndex = maxOf(
                currentReceive?.index?.toInt() ?: -1,
                usedSummary.highestExternalUsedIndex,
                maxExternalIndexFromTxos,
            )

            if (lastExternalIndex >= 0) {
                for (i in 0..lastExternalIndex) {
                    val index = i.toUInt()
                    val address = liquidExternalAddressAt(wollet, index)
                    val txCount = externalTxCountByIndex[index] ?: 0
                    val isUsed = txCount > 0 || address in addressesWithUtxos
                    val balance = balanceByAddress[address] ?: 0UL
                    val entry =
                        WalletAddress(
                            address = address,
                            index = index,
                            keychain = KeychainType.EXTERNAL,
                            label = labels[address],
                            balanceSats = balance,
                            transactionCount = txCount,
                            isUsed = isUsed,
                        )
                    if (isUsed) usedAddresses.add(entry) else receiveAddresses.add(entry)
                }
            }

            val nextIndex = (lastExternalIndex + 1).coerceAtLeast(0).toUInt()
            val extraCount = maxOf(1, ADDRESS_PEEK_AHEAD - receiveAddresses.size)
            for (offset in 0 until extraCount) {
                val index = nextIndex + offset.toUInt()
                val address = liquidExternalAddressAt(wollet, index)
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
                    val secrets = utxo.unblinded()

                    UtxoInfo(
                        outpoint = outpoint,
                        txid = txid,
                        vout = vout,
                        address = address,
                        amountSats = secrets.value(),
                        label = labels[address],
                        isConfirmed = utxo.height() != null,
                        isFrozen = frozenUtxos.contains(outpoint),
                        assetId = secrets.asset(),
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
    suspend fun previewLbtcSend(
        address: String,
        amountSats: Long,
        feeRateSatPerVb: Double = 0.1,
        selectedUtxos: List<UtxoInfo>? = null,
        isMaxSend: Boolean = false,
        label: String? = null,
    ): LiquidSendPreview = withContext(Dispatchers.IO) {
        if (isMaxSend) {
            return@withContext previewDrainTransfer(
                address = address,
                feeRateSatPerVb = feeRateSatPerVb,
                selectedUtxos = selectedUtxos,
                label = label,
            )
        }
        if (amountSats <= 0L) {
            throw Exception("No spendable L-BTC available")
        }
        previewLbtcTransfer(
            recipients = listOf(Recipient(address, amountSats.toULong())),
            feeRateSatPerVb = feeRateSatPerVb,
            selectedUtxos = selectedUtxos,
            label = label,
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
        if (isMaxSend) {
            return@withContext sendDrainTransfer(
                address = address,
                feeRateSatPerVb = feeRateSatPerVb,
                selectedUtxos = selectedUtxos,
                label = label,
                saveRecipientLabel = saveRecipientLabel,
            )
        }
        if (amountSats <= 0L) {
            throw Exception("No spendable L-BTC available")
        }
        sendLbtcTransfer(
            recipients = listOf(Recipient(address, amountSats.toULong())),
            feeRateSatPerVb = feeRateSatPerVb,
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
            val feeDetails = extractLiquidTxFeeDetails(wollet, pset, feeRateSatPerVb)

            val signedPset: Pset = signPset(pset)

            val finalizedPset: Pset = wollet.finalize(signedPset)

            // 4. Extract the transaction from the finalized PSET
            val tx = finalizedPset.extractTx()

            // 5. Broadcast
            val txid = client.broadcast(tx)
            val txidStr = txid.toString()

            if (BuildConfig.DEBUG) Log.d(TAG, "L-BTC sent successfully: $txidStr")

            persistLiquidSendLabel(
                txid = txidStr,
                label = label.takeIf { saveRecipientLabel },
                recipientAddress = recipients.singleOrNull()?.address,
                feeDetails = feeDetails,
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

    /**
     * Build a drain PSET and return a preview. One PSET build — no binary search.
     * The drain recipient amount is read back from the PSET outputs.
     */
    private suspend fun previewDrainTransfer(
        address: String,
        feeRateSatPerVb: Double,
        selectedUtxos: List<UtxoInfo>?,
        label: String?,
    ): LiquidSendPreview = withContext(Dispatchers.IO) {
        val wollet = lwkWollet ?: throw Exception("Wallet not loaded")
        val network = lwkNetwork ?: throw Exception("Network not initialized")

        val pset = buildDrainPset(
            wollet = wollet,
            network = network,
            address = address,
            feeRateSatPerVb = feeRateSatPerVb,
            selectedUtxos = selectedUtxos,
        )
        val details = wollet.psetDetails(pset)
        val balance = details.balance()
        try {
            val feeSats = balance.fee().toLong()
            val availableSats =
                selectedUtxos
                    ?.takeIf { it.isNotEmpty() }
                    ?.sumOf { it.amountSats.toLong() }
                    ?: _liquidState.value.balanceSats.coerceAtLeast(0L)
            val totalRecipientSats =
                extractLiquidPreviewRecipientAmount(
                    pset = pset,
                    recipientAddress = address,
                ) ?: (availableSats - feeSats).coerceAtLeast(0L)
            val txVBytes = if (feeRateSatPerVb > 0.0) feeSats.toDouble() / feeRateSatPerVb else null
            val effectiveFeeRate = if (txVBytes != null && txVBytes > 0.0) feeSats.toDouble() / txVBytes else feeRateSatPerVb
            LiquidSendPreview(
                kind = LiquidSendKind.LBTC,
                recipientDisplay = address,
                recipients = listOf(Recipient(address, totalRecipientSats.toULong())),
                totalRecipientSats = totalRecipientSats,
                feeSats = feeSats,
                changeSats = null,
                changeAddress = null,
                feeRate = effectiveFeeRate,
                availableSats = availableSats,
                remainingSats = 0L,
                label = label?.trim()?.takeIf { it.isNotBlank() },
                isMaxSend = true,
                selectedUtxoCount = selectedUtxos?.size ?: 0,
                txVBytes = txVBytes,
            )
        } finally {
            balance.destroy()
            details.destroy()
        }
    }

    /**
     * Sign, finalize, and broadcast a drain PSET (max send).
     */
    private suspend fun sendDrainTransfer(
        address: String,
        feeRateSatPerVb: Double,
        selectedUtxos: List<UtxoInfo>?,
        label: String?,
        saveRecipientLabel: Boolean,
    ): String = withContext(Dispatchers.IO) {
        val wollet = lwkWollet ?: throw Exception("Wallet not loaded")
        val client = lwkClient ?: throw Exception("Not connected to Electrum")
        val network = lwkNetwork ?: throw Exception("Network not initialized")

        try {
            val pset = buildDrainPset(
                wollet = wollet,
                network = network,
                address = address,
                feeRateSatPerVb = feeRateSatPerVb,
                selectedUtxos = selectedUtxos,
            )
            val feeDetails = extractLiquidTxFeeDetails(wollet, pset, feeRateSatPerVb)
            val signedPset: Pset = signPset(pset)
            val finalizedPset: Pset = wollet.finalize(signedPset)
            val tx = finalizedPset.extractTx()
            val txid = client.broadcast(tx)
            val txidStr = txid.toString()

            if (BuildConfig.DEBUG) Log.d(TAG, "L-BTC drain sent successfully: $txidStr")

            persistLiquidSendLabel(
                txid = txidStr,
                label = label.takeIf { saveRecipientLabel },
                recipientAddress = address,
                feeDetails = feeDetails,
            )

            runCatching { sync() }
                .onFailure { error ->
                    val syncError = (error as? Exception) ?: Exception(error.message ?: "Post-send sync failed")
                    logWarn("Post-send sync failed after L-BTC drain broadcast", syncError)
                }

            txidStr
        } catch (e: LwkException) {
            logError("LWK error draining L-BTC", e)
            val shortfall = extractMissingSats(e.message)
            if (shortfall != null) {
                throw Exception(
                    "Insufficient L-BTC for the send amount plus Liquid fee. Reduce the amount by at least $shortfall sats.",
                )
            }
            throw Exception("Send failed: ${e.message}")
        } catch (e: Exception) {
            logError("Failed to drain L-BTC", e)
            throw Exception("Send failed: ${e.message}")
        }
    }

    // ════════════════════════════════════════════
    // Send Liquid asset (non-L-BTC, e.g. USDT)
    // ════════════════════════════════════════════

    suspend fun previewAssetSend(
        address: String,
        amount: Long,
        assetId: String,
        feeRateSatPerVb: Double = 0.1,
        selectedUtxos: List<UtxoInfo>? = null,
        label: String? = null,
    ): LiquidSendPreview = withContext(Dispatchers.IO) {
        require(amount > 0L) { "Amount must be positive" }
        val wollet = lwkWollet ?: throw Exception("Wallet not loaded")
        val network = lwkNetwork ?: throw Exception("Network not initialized")

        val recipient = Recipient(address, amount.toULong(), assetId)
        val pset = buildLbtcSendPset(
            wollet = wollet,
            network = network,
            recipients = listOf(recipient),
            feeRateSatPerVb = feeRateSatPerVb,
            selectedUtxos = selectedUtxos,
        )
        val details = wollet.psetDetails(pset)
        val balance = details.balance()
        try {
            val feeSats = balance.fee().toLong()
            val availableAsset = _liquidState.value.balanceForAsset(assetId).coerceAtLeast(0L)
            val txVBytes = if (feeRateSatPerVb > 0.0) feeSats.toDouble() / feeRateSatPerVb else null
            val effectiveFeeRate = if (txVBytes != null && txVBytes > 0.0) feeSats.toDouble() / txVBytes else feeRateSatPerVb
            LiquidSendPreview(
                kind = LiquidSendKind.LIQUID_ASSET,
                assetId = assetId,
                recipientDisplay = address,
                recipients = listOf(recipient),
                totalRecipientSats = amount,
                feeSats = feeSats,
                changeSats = null,
                changeAddress = null,
                feeRate = effectiveFeeRate,
                availableSats = availableAsset,
                remainingSats = (availableAsset - amount).coerceAtLeast(0L),
                label = label?.trim()?.takeIf { it.isNotBlank() },
                selectedUtxoCount = selectedUtxos?.size ?: 0,
                txVBytes = txVBytes,
            )
        } finally {
            balance.destroy()
            details.destroy()
        }
    }

    suspend fun sendAsset(
        address: String,
        amount: Long,
        assetId: String,
        feeRateSatPerVb: Double = 0.1,
        selectedUtxos: List<UtxoInfo>? = null,
        label: String? = null,
        saveRecipientLabel: Boolean = true,
    ): String = withContext(Dispatchers.IO) {
        require(amount > 0L) { "Amount must be positive" }
        val wollet = lwkWollet ?: throw Exception("Wallet not loaded")
        val client = lwkClient ?: throw Exception("Not connected to Electrum")
        val network = lwkNetwork ?: throw Exception("Network not initialized")
        val ticker = LiquidAsset.resolve(assetId).ticker

        try {
            val recipient = Recipient(address, amount.toULong(), assetId)
            val pset = buildLbtcSendPset(
                wollet = wollet,
                network = network,
                recipients = listOf(recipient),
                feeRateSatPerVb = feeRateSatPerVb,
                selectedUtxos = selectedUtxos,
            )
            val feeDetails = extractLiquidTxFeeDetails(wollet, pset, feeRateSatPerVb)
            val signedPset: Pset = signPset(pset)
            val finalizedPset: Pset = wollet.finalize(signedPset)
            val tx = finalizedPset.extractTx()
            val txid = client.broadcast(tx)
            val txidStr = txid.toString()

            if (BuildConfig.DEBUG) Log.d(TAG, "$ticker sent successfully: $txidStr")

            persistLiquidSendLabel(
                txid = txidStr,
                label = label.takeIf { saveRecipientLabel },
                recipientAddress = address,
                feeDetails = feeDetails,
            )

            runCatching { sync() }
                .onFailure { error ->
                    val syncError = (error as? Exception) ?: Exception(error.message ?: "Post-send sync failed")
                    logWarn("Post-send sync failed after $ticker broadcast", syncError)
                }

            txidStr
        } catch (e: LwkException) {
            logError("LWK error sending $ticker", e)
            val shortfall = extractMissingSats(e.message)
            if (shortfall != null) {
                throw Exception(
                    "Insufficient L-BTC for the Liquid network fee. You need at least $shortfall more sats of L-BTC.",
                )
            }
            throw Exception("Send failed: ${e.message}")
        } catch (e: Exception) {
            logError("Failed to send $ticker", e)
            throw Exception("Send failed: ${e.message}")
        }
    }

    suspend fun createUnsignedAssetPset(
        address: String,
        amount: Long,
        assetId: String,
        feeRateSatPerVb: Double = 0.1,
        selectedUtxos: List<UtxoInfo>? = null,
    ): String = withContext(Dispatchers.IO) {
        require(amount > 0L) { "Amount must be positive" }
        val wollet = lwkWollet ?: throw Exception("Wallet not loaded")
        val network = lwkNetwork ?: throw Exception("Network not initialized")

        val pset = buildLbtcSendPset(
            wollet = wollet,
            network = network,
            recipients = listOf(Recipient(address, amount.toULong(), assetId)),
            feeRateSatPerVb = feeRateSatPerVb,
            selectedUtxos = selectedUtxos,
        )
        pset.toString()
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
            val pair = getBoltzReversePairInfo()
            logBoltzTrace(
                "success",
                trace,
                    "elapsedMs" to boltzElapsedMs(traceStartedAt),
                    "minimumSats" to pair.limits.minimal,
                    "maximumSats" to pair.limits.maximal,
            )
            LightningInvoiceLimits(
                minimumSats = pair.limits.minimal,
                maximumSats = pair.limits.maximal,
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
            SecureLog.w(TAG, "LWK BoltzSession quote failed, falling back to REST: ${e.message}", e)
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

    private fun extractLiquidPreviewRecipientAmount(
        pset: Pset,
        recipientAddress: String,
    ): Long? {
        return try {
            val recipientScript = Address(recipientAddress).scriptPubkey().toString()
            pset.outputs()
                .firstOrNull { output -> output.scriptPubkey().toString() == recipientScript }
                ?.amount()
                ?.toLong()
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

    private suspend fun getBoltzReversePairInfo(): BoltzPairInfo {
        ensureBoltzEnabled()
        return boltzRuntime.getReversePairInfo()
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
                if (BuildConfig.DEBUG && !isNoBoltzUpdate(error)) {
                    Log.d(TAG, "$operation: transient progress error: ${error.message}")
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

    private fun clearPendingLightningInvoiceSession(swapId: String? = null) {
        val walletId = currentWalletId ?: return
        secureStorage.deletePendingLightningInvoiceSession(walletId, swapId)
    }

    private suspend fun ensureLightningInvoiceResponse(swapId: String): InvoiceResponse {
        invoiceResponses[swapId]?.let { return it }
        val session = getPendingLightningInvoiceSession(swapId)
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
        localLabel: String?,
        embeddedLabel: String?,
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

        logBoltzTrace("start", trace, "amountSats" to amountSats)

        try {
            val claimAddress = generateFreshAddress() ?: throw Exception("No Liquid address available")
            val trimmedLocalLabel = localLabel?.trim().orEmpty()
            val trimmedEmbeddedLabel = embeddedLabel?.trim()?.takeIf { it.isNotEmpty() }
            val response = createBoltzLightningInvoiceResponse(amountSats, claimAddress, trimmedEmbeddedLabel)
            val swapId = response.swapId()
            val snapshot = response.serialize()
            invoiceResponses[swapId] = response
            secureStorage.savePendingLightningReceive(
                walletId = walletId,
                swapId = swapId,
                claimAddress = claimAddress,
                label = trimmedLocalLabel,
            )
            persistLightningInvoiceSession(
                PendingLightningInvoiceSession(
                    swapId = swapId,
                    snapshot = snapshot,
                    invoice = response.bolt11Invoice().toString(),
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
        invoiceAmountSats: Long,
        claimAddress: String,
        embeddedLabel: String?,
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
        logBoltzTrace("start", trace.copy(attempt = 1), "invoiceAmountSats" to invoiceAmountSats, "claimAddress" to summarizeValue(claimAddress))
        return withBoltzOperationLock(operation = "createBoltzLightningInvoiceResponse") {
            val walletId = currentWalletId ?: throw Exception("Wallet not loaded")
            fun createInvoiceWithSession(session: BoltzSession, attempt: Int): InvoiceResponse {
                return session.invoice(
                    invoiceAmountSats.toULong(),
                    embeddedLabel,
                    address,
                    null,
                ).also {
                    persistBoltzSessionNextIndex(walletId, session)
                    logBoltzTrace(
                        "success",
                        trace.copy(attempt = attempt),
                        "elapsedMs" to boltzElapsedMs(traceStartedAt),
                        "invoiceAmountSats" to invoiceAmountSats,
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
                        "invoiceAmountSats" to invoiceAmountSats,
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
                    "invoiceAmountSats" to invoiceAmountSats,
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
                        "invoiceAmountSats" to invoiceAmountSats,
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
                var response = ensureLightningInvoiceResponse(swapId)
                val state =
                    try {
                        advanceBoltzPaymentState("advanceLightningInvoice") {
                            response.advance()
                        }
                    } catch (error: Exception) {
                        if (!isBoltzObjectConsumed(error)) throw error
                        logDebug("advanceLightningInvoice restoring consumed response swapId=$swapId")
                        response = restoreConsumedInvoiceResponse(swapId)
                        advanceBoltzPaymentState("advanceLightningInvoiceRestored") {
                            response.advance()
                        }
                    }
                state to response.serialize()
            }
        getPendingLightningInvoiceSession(swapId)
            ?.let { persistLightningInvoiceSession(it.copy(snapshot = snapshot)) }
        state
    }

    suspend fun finishLightningInvoice(swapId: String): String? = withContext(Dispatchers.IO) {
        val result =
            withBoltzOperationLock(operation = "finishLightningInvoice", swapId = swapId) {
                var response = ensureLightningInvoiceResponse(swapId)
                val snapshot =
                    runCatching { response.serialize() }.getOrElse { error ->
                        if (!isBoltzObjectConsumed(error)) throw error
                        logDebug("finishLightningInvoice restoring consumed response before serialize swapId=$swapId")
                        response = restoreConsumedInvoiceResponse(swapId)
                        response.serialize()
                    }
                try {
                    completeLightningInvoiceResponse(swapId, response, snapshot)
                } catch (error: Exception) {
                    if (isBoltzObjectConsumed(error)) {
                        logDebug("finishLightningInvoice restoring consumed response before complete swapId=$swapId")
                        response = restoreConsumedInvoiceResponse(swapId)
                        return@withBoltzOperationLock runCatching {
                            completeLightningInvoiceResponse(swapId, response, snapshot)
                        }.getOrElse { retryError ->
                            if (isBoltzObjectConsumed(retryError)) {
                                logDebug("finishLightningInvoice restored response was consumed again swapId=$swapId")
                                cleanupInvoiceResponse(swapId)
                                invoiceResponses[swapId] = ensureBoltzSession().restoreInvoice(snapshot)
                                return@withBoltzOperationLock null
                            }
                            if (retryError is LwkException && isBoltzTransientProgressError(retryError)) {
                                if (BuildConfig.DEBUG) {
                                    Log.d(TAG, "finishLightningInvoice: waiting for more Boltz activity after restore")
                                }
                                val latestSnapshot = runCatching { response.serialize() }.getOrDefault(snapshot)
                                getPendingLightningInvoiceSession(swapId)
                                    ?.let { persistLightningInvoiceSession(it.copy(snapshot = latestSnapshot)) }
                                runCatching { response.destroy() }
                                invoiceResponses[swapId] = ensureBoltzSession().restoreInvoice(latestSnapshot)
                                return@withBoltzOperationLock null
                            }
                            throw retryError
                        }
                    }
                    if (error !is LwkException) throw error
                    if (!isBoltzTransientProgressError(error)) {
                        throw error
                    }
                    if (BuildConfig.DEBUG) Log.d(TAG, "finishLightningInvoice: waiting for more Boltz activity")
                    val latestSnapshot = runCatching { response.serialize() }.getOrDefault(snapshot)
                    getPendingLightningInvoiceSession(swapId)
                        ?.let { persistLightningInvoiceSession(it.copy(snapshot = latestSnapshot)) }
                    runCatching { response.destroy() }
                    invoiceResponses[swapId] = ensureBoltzSession().restoreInvoice(latestSnapshot)
                    null
                }
            }
        if (result == null) {
            return@withContext null
        }
        finalizeLightningReceiveTransaction(swapId, result)
        cleanupInvoiceResponse(swapId)
        clearPendingLightningInvoiceSession(swapId)
        destroyBoltzSessionIfIdle("lightning_invoice_completed")
        result
    }

    private fun completeLightningInvoiceResponse(
        swapId: String,
        response: InvoiceResponse,
        fallbackSnapshot: String,
    ): String? {
        val completed = response.completePay()
        if (!completed) {
            persistUpdatedLightningInvoiceSnapshot(swapId, response, fallbackSnapshot)
            return null
        }
        return response.claimTxid()
            .orEmpty()
            .takeIf { it.isNotBlank() }
            ?: run {
                persistUpdatedLightningInvoiceSnapshot(swapId, response, fallbackSnapshot)
                null
            }
    }

    private fun persistUpdatedLightningInvoiceSnapshot(
        swapId: String,
        response: InvoiceResponse,
        fallbackSnapshot: String,
    ) {
        getPendingLightningInvoiceSession(swapId)
            ?.let {
                val latestSnapshot = runCatching { response.serialize() }.getOrDefault(fallbackSnapshot)
                persistLightningInvoiceSession(it.copy(snapshot = latestSnapshot))
            }
    }

    suspend fun finalizeLightningInvoiceClaimed(
        swapId: String,
        txid: String? = null,
    ) = withContext(Dispatchers.IO) {
        cleanupInvoiceResponse(swapId)
        txid
            ?.takeIf { it.isNotBlank() }
            ?.let { finalizeLightningReceiveTransaction(swapId, it) }
        destroyBoltzSessionIfIdle("lightning_invoice_claimed_shortcut")
    }

    suspend fun failLightningInvoice(
        swapId: String,
        clearPendingMetadata: Boolean = true,
    ) = withContext(Dispatchers.IO) {
        cleanupInvoiceResponse(swapId)
        if (clearPendingMetadata) {
            clearPendingLightningInvoiceSession(swapId)
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

    suspend fun deletePendingLightningInvoice(swapId: String) = withContext(Dispatchers.IO) {
        cleanupInvoiceResponse(swapId)
        clearPendingLightningInvoiceSession(swapId)
        currentWalletId?.let { walletId ->
            secureStorage.deletePendingLightningReceive(walletId, swapId)
        }
        invalidateMetadataCache()
        destroyBoltzSessionIfIdle("lightning_invoice_deleted")
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
            val direct = parseMagicRoutingHintPayment(e)
            val invoiceAmountSats = executionPlan.paymentAmountSats
            logBoltzTrace(
                "direct_liquid_fallback",
                trace.copy(source = "magic_routing_hint"),
                level = BoltzTraceLevel.INFO,
                throwable = e,
                "elapsedMs" to boltzElapsedMs(traceStartedAt),
                "mrhHintedAmountSats" to direct.amountSats,
                "invoiceAmountSats" to invoiceAmountSats,
            )
            LightningPaymentExecutionPlan.DirectLiquid(
                paymentInput = executionPlan.paymentInput,
                address = direct.address,
                amountSats = invoiceAmountSats,
            )
        } catch (e: Exception) {
            if (canUseRestSubmarineFallback(resolvedPayment)) {
                logBoltzTrace(
                    "lwk_failed_falling_back_to_rest",
                    trace,
                    level = BoltzTraceLevel.WARN,
                    throwable = e,
                    "elapsedMs" to boltzElapsedMs(traceStartedAt),
                    "reason" to lightningPaymentFallbackReason(e),
                )
                destroyBoltzSessionAfterPreparePayFailure()
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

    private fun canUseRestSubmarineFallback(resolvedPayment: ResolvedLightningPaymentInput): Boolean {
        val invoice = resolvedPayment.fetchedInvoice ?: resolvedPayment.paymentInput
        val payment = runCatching { Payment(invoice) }.getOrNull()
        return payment?.kind() == PaymentKind.LIGHTNING_INVOICE
    }

    private fun lightningPaymentFallbackReason(error: Exception): String {
        val message = error.message.orEmpty()
        return when {
            message.contains("BoltzApi(HTTP", ignoreCase = true) -> "lwk_http"
            message.contains("timeout", ignoreCase = true) -> "timeout"
            message.contains("connection", ignoreCase = true) -> "connection"
            else -> "lwk_prepare_pay"
        }
    }

    private suspend fun destroyBoltzSessionAfterPreparePayFailure() {
        if (hasActiveBoltzResponses()) {
            logDebug("preparePay failed; keeping shared Boltz session because active responses exist")
            return
        }
        destroyBoltzSession(persistNextIndex = true)
    }

    private suspend fun prepareRestSubmarineSwapFallback(
        executionPlan: LightningPaymentExecutionPlan.BoltzQuote,
        resolvedPayment: ResolvedLightningPaymentInput,
        requestKey: String,
        trace: BoltzTraceContext,
        traceStartedAt: Long,
    ): LightningPaymentExecutionPlan.BoltzSwap {
        val fetchedInvoice = resolvedPayment.fetchedInvoice ?: resolvedPayment.paymentInput
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
            if (availableBalance in 1L until fundingAmount) {
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
        val isLnurlUrl = paymentInput.startsWith("http://", ignoreCase = true) ||
            paymentInput.startsWith("https://", ignoreCase = true)
        val parsedPayment = runCatching { Payment(paymentInput) }.getOrNull()
        val bip353Address = parsedPayment?.bip353()?.takeIf { it.isNotBlank() } ?: normalizedBip353Address(paymentInput)
        val lightningInput =
            when {
                isLnurlUrl -> {
                    resolveLnurlPayInput(
                        lnurlUrl = paymentInput,
                        requestedAmountSats = requestedAmountSats,
                        trace = trace,
                    )
                }
                parsedPayment?.kind() == PaymentKind.BIP353 || bip353Address != null -> {
                    val address = bip353Address ?: paymentInput
                    ensureBoltzTorReadyIfNeeded()
                    val bip353StartedAt = System.currentTimeMillis()
                    val bip353TraceStartedAt = boltzTraceStart()
                    logBoltzTrace(
                        "resolve_bip353_start",
                        trace.copy(source = "bip353"),
                        "address" to summarizeValue(address),
                    )
                    try {
                        val resolution = bip353Resolver.resolve(address)
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
                    } catch (bip353Error: Exception) {
                        if (isLightningAddress(address)) {
                            logBoltzTrace(
                                "bip353_failed_fallback_lnurl",
                                trace.copy(source = "lnurl"),
                                "bip353Error" to (bip353Error.message ?: "unknown"),
                                "address" to summarizeValue(address),
                            )
                            resolveLightningAddressInput(
                                address = address,
                                requestedAmountSats = requestedAmountSats,
                                trace = trace,
                            )
                        } else {
                            throw bip353Error
                        }
                    }
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

    private suspend fun resolveLnurlPayInput(
        lnurlUrl: String,
        requestedAmountSats: Long?,
        trace: BoltzTraceContext,
    ): ResolvedLightningPaymentInput {
        val startedAt = System.currentTimeMillis()
        val traceStartedAt = boltzTraceStart()
        ensureBoltzTorReadyIfNeeded()
        logBoltzTrace(
            "resolve_lnurl_start",
            trace.copy(source = "lnurl"),
            "url" to summarizeValue(lnurlUrl),
            "requestedAmountSats" to requestedAmountSats,
        )
        val metadata = lnurlPayResolver.resolveUrl(lnurlUrl)
        val amountSats = resolveAndValidateLnurlAmount(metadata, requestedAmountSats)
        val invoice = lnurlPayResolver.fetchInvoice(metadata, amountSats * 1000L)
        logBoltzTrace(
            "resolve_lnurl_success",
            trace.copy(source = "lnurl"),
            "elapsedMs" to boltzElapsedMs(traceStartedAt),
            "amountSats" to amountSats,
            "invoice" to summarizeValue(invoice.bolt11),
        )
        logTiming("resolveLnurlPayInput", startedAt)
        return ResolvedLightningPaymentInput(
            paymentInput = invoice.bolt11,
            requestedAmountSats = amountSats,
        )
    }

    private suspend fun resolveLightningAddressInput(
        address: String,
        requestedAmountSats: Long?,
        trace: BoltzTraceContext,
    ): ResolvedLightningPaymentInput {
        val startedAt = System.currentTimeMillis()
        val traceStartedAt = boltzTraceStart()
        ensureBoltzTorReadyIfNeeded()
        logBoltzTrace(
            "resolve_lnaddr_start",
            trace.copy(source = "lnaddr"),
            "address" to summarizeValue(address),
            "requestedAmountSats" to requestedAmountSats,
        )
        val metadata = lnurlPayResolver.resolveAddress(address)
        val amountSats = resolveAndValidateLnurlAmount(metadata, requestedAmountSats)
        val invoice = lnurlPayResolver.fetchInvoice(metadata, amountSats * 1000L)
        logBoltzTrace(
            "resolve_lnaddr_success",
            trace.copy(source = "lnaddr"),
            "elapsedMs" to boltzElapsedMs(traceStartedAt),
            "amountSats" to amountSats,
            "invoice" to summarizeValue(invoice.bolt11),
        )
        logTiming("resolveLightningAddressInput", startedAt)
        return ResolvedLightningPaymentInput(
            paymentInput = invoice.bolt11,
            requestedAmountSats = amountSats,
        )
    }

    private fun resolveAndValidateLnurlAmount(
        metadata: github.aeonbtc.ibiswallet.data.remote.LnurlPayMetadata,
        requestedAmountSats: Long?,
    ): Long {
        if (metadata.isFixedAmount) {
            return metadata.minSendableSats
        }
        val amountSats = requestedAmountSats
            ?: throw Exception("Enter an amount between ${formatSatsForDisplay(metadata.minSendableSats)} and ${formatSatsForDisplay(metadata.maxSendableSats)}.")
        if (amountSats < metadata.minSendableSats) {
            throw Exception("Amount is below the minimum of ${formatSatsForDisplay(metadata.minSendableSats)}.")
        }
        if (amountSats > metadata.maxSendableSats) {
            throw Exception("Amount exceeds the maximum of ${formatSatsForDisplay(metadata.maxSendableSats)}.")
        }
        return amountSats
    }

    private fun isLightningAddress(input: String): Boolean {
        val trimmed = input.trim()
        if (trimmed.contains("://") || trimmed.contains(":")) return false
        val parts = trimmed.split("@", limit = 3)
        return parts.size == 2 && parts[0].isNotBlank() && parts[1].contains(".")
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
        val refundAddressResult = resolveEarliestUnusedLiquidExternalAddress(
            wollet = wollet,
            walletId = walletId,
        )
        val refundAddress = refundAddressResult.address
        val refundIndex = refundAddressResult.index.toLong()
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
        val session = getPendingLightningPaymentSession()?.takeIf { it.swapId == swapId }
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
        session?.fundingTxid
            ?.takeIf { it.isNotBlank() }
            ?.let { persistLightningSendSwapDetails(it, swapId) }
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
                    "Boltz took too long to prepare the swap review. Retry when you're ready.",
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
                    if (shouldRetryBoltzChainSwapCreation(e)) {
                        val resetReason =
                            if (isBoltzNextIndexCollision(e)) {
                                "next_index_collision"
                            } else {
                                "timeout"
                            }
                        logBoltzTrace(
                            "reset_after_failure",
                            trace.copy(attempt = 1),
                            level = BoltzTraceLevel.WARN,
                            throwable = e,
                            "elapsedMs" to boltzElapsedMs(traceStartedAt),
                            "direction" to direction.name,
                            "amountSats" to amountSats,
                            "reason" to resetReason,
                        )
                        if (isBoltzNextIndexCollision(e)) {
                            clearBoltzSessionNextIndexCache()
                        }
                        logWarn(
                            if (isBoltzNextIndexCollision(e)) {
                                "Boltz chain swap creation hit a stale next-index collision; waiting for user retry on a fresh session"
                            } else {
                                "Boltz chain swap creation timed out; waiting for user retry on a fresh session"
                            },
                            e,
                        )
                        destroyBoltzSession(persistNextIndex = !isBoltzNextIndexCollision(e))
                    }
                    throw e
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
                if (e.isTransactionInsufficientFundsError()) {
                    false
                } else {
                    throw e
                }
            }
        }
    }

    fun buildLbtcSendPset(
        wollet: Wollet,
        network: Network,
        recipients: List<Recipient>,
        feeRateSatPerVb: Double,
        selectedUtxos: List<UtxoInfo>? = null,
    ): Pset {
        require(recipients.isNotEmpty()) { "At least one recipient is required" }
        require(recipients.all { it.amountSats > 0UL }) { "Recipient amounts must be positive" }

        val hasNonLbtcRecipient = recipients.any { recipient ->
            val assetId = recipient.assetId
            assetId != null && !LiquidAsset.isPolicyAsset(assetId)
        }
        val effectiveUtxos = if (hasNonLbtcRecipient && selectedUtxos != null) {
            augmentWithLbtcUtxos(wollet, selectedUtxos)
        } else {
            selectedUtxos
        }

        val txBuilder = network.txBuilder()
        applyUtxoSelection(txBuilder, wollet, effectiveUtxos)
        recipients.forEach { recipient ->
            val assetId = recipient.assetId
            if (assetId != null && !LiquidAsset.isPolicyAsset(assetId)) {
                txBuilder.addRecipient(Address(recipient.address), recipient.amountSats, assetId)
            } else {
                txBuilder.addLbtcRecipient(Address(recipient.address), recipient.amountSats)
            }
        }
        txBuilder.feeRate((feeRateSatPerVb * 1000.0).toFloat())
        return txBuilder.finish(wollet)
    }

    /**
     * Build a PSET that drains the entire L-BTC balance to a single address.
     * Uses LWK's native `drainLbtcWallet()` + `drainLbtcTo()` so the builder
     * handles coin selection and fee deduction in one pass -- no binary search.
     */
    fun buildDrainPset(
        wollet: Wollet,
        network: Network,
        address: String,
        feeRateSatPerVb: Double,
        selectedUtxos: List<UtxoInfo>? = null,
    ): Pset {
        val txBuilder = network.txBuilder()
        applyUtxoSelection(txBuilder, wollet, selectedUtxos)
        txBuilder.drainLbtcWallet()
        txBuilder.drainLbtcTo(Address(address))
        txBuilder.feeRate((feeRateSatPerVb * 1000.0).toFloat())
        return txBuilder.finish(wollet)
    }

    private fun applyUtxoSelection(
        txBuilder: lwk.TxBuilder,
        wollet: Wollet,
        selectedUtxos: List<UtxoInfo>?,
    ) {
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
    }

    /**
     * When coin control selects only non-L-BTC UTXOs, the tx builder still needs
     * L-BTC inputs to cover the fee. This adds all unfrozen L-BTC UTXOs to the
     * selection so the builder can pick from them for fee coverage.
     */
    private fun augmentWithLbtcUtxos(
        wollet: Wollet,
        selectedUtxos: List<UtxoInfo>,
    ): List<UtxoInfo> {
        val selectedOutpoints = selectedUtxos.map { it.outpoint }.toSet()
        val hasLbtcSelected = selectedUtxos.any { utxo ->
            val assetId = utxo.assetId
            assetId == null || LiquidAsset.isPolicyAsset(assetId)
        }
        if (hasLbtcSelected) return selectedUtxos

        val policyAssetId = lwkNetwork?.policyAsset()
        val lbtcUtxos = wollet.utxos()
            .filter { utxo ->
                val outpoint = utxo.outpoint()
                val op = "${outpoint.txid()}:${outpoint.vout()}"
                op !in selectedOutpoints && utxo.unblinded().asset() == policyAssetId
            }
            .map { utxo ->
                val outpoint = utxo.outpoint()
                val txid = outpoint.txid().toString()
                val vout = outpoint.vout()
                val secrets = utxo.unblinded()
                UtxoInfo(
                    outpoint = "$txid:$vout",
                    txid = txid,
                    vout = vout,
                    amountSats = secrets.value(),
                    address = "",
                    isConfirmed = utxo.height() != null,
                    isFrozen = false,
                    assetId = secrets.asset(),
                )
            }
        return selectedUtxos + lbtcUtxos
    }

    /**
     * Create an unsigned PSET for watch-only wallets.
     * Returns the PSET serialized as a base64 string.
     */
    suspend fun createUnsignedPset(
        address: String,
        amountSats: Long,
        feeRateSatPerVb: Double = 0.1,
        selectedUtxos: List<UtxoInfo>? = null,
        isMaxSend: Boolean = false,
    ): String = withContext(Dispatchers.IO) {
        val wollet = lwkWollet ?: throw Exception("Wallet not loaded")
        val network = lwkNetwork ?: throw Exception("Network not initialized")

        val pset = if (isMaxSend) {
            buildDrainPset(
                wollet = wollet,
                network = network,
                address = address,
                feeRateSatPerVb = feeRateSatPerVb,
                selectedUtxos = selectedUtxos,
            )
        } else {
            if (amountSats <= 0L) throw Exception("No spendable L-BTC available")
            buildLbtcSendPset(
                wollet = wollet,
                network = network,
                recipients = listOf(Recipient(address, amountSats.toULong())),
                feeRateSatPerVb = feeRateSatPerVb,
                selectedUtxos = selectedUtxos,
            )
        }
        pset.toString()
    }


    private fun persistLiquidSendLabel(
        txid: String,
        label: String?,
        recipientAddress: String? = null,
        feeDetails: SecureStorage.LiquidTxFeeDetails? = null,
    ) {
        val walletId = currentWalletId ?: return
        recipientAddress
            ?.takeIf { it.isNotBlank() }
            ?.let { addr ->
                secureStorage.saveLiquidTransactionRecipient(walletId, txid, addr)
            }
        feeDetails?.let { details ->
            secureStorage.saveLiquidTransactionFeeDetails(
                walletId = walletId,
                txid = txid,
                feeRate = details.feeRate,
                vsize = details.vsize,
            )
        }
        invalidateMetadataCache()
        updateTransactionInPlace(txid) {
            it.copy(
                recipientAddress = recipientAddress ?: it.recipientAddress,
                feeRate = feeDetails?.feeRate ?: it.feeRate,
                vsize = feeDetails?.vsize ?: it.vsize,
            )
        }
        val trimmedLabel = label?.trim().orEmpty()
        if (trimmedLabel.isBlank()) {
            return
        }
        secureStorage.saveLiquidTransactionLabel(walletId, txid, trimmedLabel)
        electrumCache.updateTransactionSearchLabel(
            walletId = walletId,
            layer = TransactionSearchLayer.LIQUID,
            txid = txid,
            label = trimmedLabel,
        )
        recipientAddress
            ?.takeIf { it.isNotBlank() }
            ?.let { secureStorage.saveLiquidAddressLabel(walletId, it, trimmedLabel) }
        updateTransactionInPlace(txid) { it.copy(memo = trimmedLabel) }
    }

    private fun extractLiquidTxFeeDetails(
        wollet: Wollet,
        pset: Pset,
        fallbackFeeRateSatPerVb: Double,
    ): SecureStorage.LiquidTxFeeDetails? {
        val details = wollet.psetDetails(pset)
        val balance = details.balance()
        return try {
            val feeSats = balance.fee().toLong()
            val txVBytes = if (fallbackFeeRateSatPerVb > 0.0) feeSats.toDouble() / fallbackFeeRateSatPerVb else null
            val effectiveFeeRate = if (txVBytes != null && txVBytes > 0.0) feeSats.toDouble() / txVBytes else fallbackFeeRateSatPerVb
            if (effectiveFeeRate > 0.0 && txVBytes != null && txVBytes > 0.0) {
                SecureStorage.LiquidTxFeeDetails(
                    feeRate = effectiveFeeRate,
                    vsize = txVBytes,
                )
            } else {
                null
            }
        } finally {
            balance.destroy()
            details.destroy()
        }
    }

    private fun estimateNetworkFeeSats(vsize: Int, feeRateSatPerVb: Double): Long {
        return kotlin.math.ceil(vsize * feeRateSatPerVb).toLong().coerceAtLeast(1L)
    }

    private fun extractMissingSats(message: String?): Long? {
        if (message.isNullOrBlank()) return null
        val match = Regex("""missing_sats:\s*(\d+)""").find(message) ?: return null
        return match.groupValues.getOrNull(1)?.toLongOrNull()
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

    private suspend fun restoreConsumedInvoiceResponse(swapId: String): InvoiceResponse {
        cleanupInvoiceResponse(swapId)
        val snapshot =
            getPendingLightningInvoiceSession(swapId)
                ?.snapshot
                ?: throw Exception("Lightning invoice snapshot unavailable")
        return ensureBoltzSession().restoreInvoice(snapshot).also {
            invoiceResponses[swapId] = it
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
        SecureLog.e(BOLTZ_LOG_TAG, message, error)
    }

    private fun logWarn(message: String, error: Exception) {
        SecureLog.w(BOLTZ_LOG_TAG, message, error)
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
