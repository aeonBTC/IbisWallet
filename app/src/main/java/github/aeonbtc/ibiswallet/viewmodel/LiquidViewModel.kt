package github.aeonbtc.ibiswallet.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import github.aeonbtc.ibiswallet.BuildConfig
import github.aeonbtc.ibiswallet.R
import github.aeonbtc.ibiswallet.data.boltz.BoltzMaxReviewState
import github.aeonbtc.ibiswallet.data.boltz.BoltzTraceContext
import github.aeonbtc.ibiswallet.data.boltz.BoltzTraceLevel
import github.aeonbtc.ibiswallet.data.boltz.BullBoltzBehavior
import github.aeonbtc.ibiswallet.data.boltz.boltzElapsedMs
import github.aeonbtc.ibiswallet.data.boltz.boltzTraceStart
import github.aeonbtc.ibiswallet.data.boltz.hasBoltzMaxReviewMismatch
import github.aeonbtc.ibiswallet.data.boltz.isBoltzMaxFundingShortfallError
import github.aeonbtc.ibiswallet.data.boltz.logBoltzTrace
import github.aeonbtc.ibiswallet.data.boltz.resolveBoltzMaxReviewMetadata
import github.aeonbtc.ibiswallet.data.boltz.resolveBoltzReviewFundingPreviewIsMaxSend
import github.aeonbtc.ibiswallet.data.boltz.shouldRecreateBoltzMaxOrder
import github.aeonbtc.ibiswallet.data.local.SecureStorage
import github.aeonbtc.ibiswallet.data.model.BoltzChainFundingPreview
import github.aeonbtc.ibiswallet.data.model.BoltzChainSwapDraft
import github.aeonbtc.ibiswallet.data.model.BoltzSwapUpdate
import github.aeonbtc.ibiswallet.data.model.DryRunResult
import github.aeonbtc.ibiswallet.data.model.EstimatedSwapTerms
import github.aeonbtc.ibiswallet.data.model.LightningInvoiceLimits
import github.aeonbtc.ibiswallet.data.model.LightningInvoiceState
import github.aeonbtc.ibiswallet.data.model.LightningPaymentBackend
import github.aeonbtc.ibiswallet.data.model.LightningPaymentExecutionPlan
import github.aeonbtc.ibiswallet.data.model.LiquidAsset
import github.aeonbtc.ibiswallet.data.model.LiquidElectrumConfig
import github.aeonbtc.ibiswallet.data.model.LiquidPsetState
import github.aeonbtc.ibiswallet.data.model.LiquidSendKind
import github.aeonbtc.ibiswallet.data.model.LiquidSendPreview
import github.aeonbtc.ibiswallet.data.model.LiquidSendState
import github.aeonbtc.ibiswallet.data.model.LiquidServersState
import github.aeonbtc.ibiswallet.data.model.LiquidSwapTxRole
import github.aeonbtc.ibiswallet.data.model.LiquidTxSource
import github.aeonbtc.ibiswallet.data.model.LiquidTxType
import github.aeonbtc.ibiswallet.data.model.LiquidWalletState
import github.aeonbtc.ibiswallet.data.model.PendingLightningInvoice
import github.aeonbtc.ibiswallet.data.model.PendingLightningPaymentPhase
import github.aeonbtc.ibiswallet.data.model.PendingLightningPaymentSession
import github.aeonbtc.ibiswallet.data.model.PendingSwapPhase
import github.aeonbtc.ibiswallet.data.model.PendingSwapSession
import github.aeonbtc.ibiswallet.data.model.Recipient
import github.aeonbtc.ibiswallet.data.model.SwapDirection
import github.aeonbtc.ibiswallet.data.model.SwapLimits
import github.aeonbtc.ibiswallet.data.model.SwapQuote
import github.aeonbtc.ibiswallet.data.model.SwapService
import github.aeonbtc.ibiswallet.data.model.SwapState
import github.aeonbtc.ibiswallet.data.model.SyncProgress
import github.aeonbtc.ibiswallet.data.model.TransactionSearchResult
import github.aeonbtc.ibiswallet.data.model.UtxoInfo
import github.aeonbtc.ibiswallet.data.model.WalletAddress
import github.aeonbtc.ibiswallet.data.model.WalletLayer
import github.aeonbtc.ibiswallet.data.repository.LiquidRepository
import github.aeonbtc.ibiswallet.localization.AppLocale
import github.aeonbtc.ibiswallet.service.ConnectivityKeepAlivePolicy
import github.aeonbtc.ibiswallet.data.swap.buildBitcoinSwapFundingRequest
import github.aeonbtc.ibiswallet.data.swap.buildLiquidSwapFundingRequest
import github.aeonbtc.ibiswallet.data.swap.resolveBoltzAddressPlan
import github.aeonbtc.ibiswallet.data.swap.resolveSideSwapRequestedReceiveAddress
import github.aeonbtc.ibiswallet.tor.TorManager
import github.aeonbtc.ibiswallet.tor.TorState
import github.aeonbtc.ibiswallet.tor.TorStatus
import github.aeonbtc.ibiswallet.util.Bip329LabelNetwork
import github.aeonbtc.ibiswallet.util.Bip329LabelScope
import github.aeonbtc.ibiswallet.util.Bip329Labels
import github.aeonbtc.ibiswallet.util.CertificateFirstUseException
import github.aeonbtc.ibiswallet.util.CertificateMismatchException
import github.aeonbtc.ibiswallet.util.isTransactionInsufficientFundsError
import github.aeonbtc.ibiswallet.util.SecureLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * ViewModel for all Layer 2 (Liquid Network) operations.
 * Fully separate from WalletViewModel — manages its own state, lifecycle,
 * and coordinates LiquidRepository for LWK, Boltz, and SideSwap operations.
 */
class LiquidViewModel(application: Application) : AndroidViewModel(application) {
    private val appContext = getApplication<Application>()

    companion object {
        private const val TAG = "LiquidViewModel"
        private const val BOLTZ_LOG_TAG = "BoltzDebug"
        private const val PSET_DISABLED_MESSAGE = "PSET is temporarily unavailable for live use."
        private const val CONNECTION_TIMEOUT_TOR_MS = 30_000L
        private const val CONNECTION_TIMEOUT_CLEARNET_MS = 15_000L
        private const val TOR_BOOTSTRAP_TIMEOUT_MS = 60_000L
        private const val TOR_POST_BOOTSTRAP_DELAY_MS = 2_500L
        private const val BACKGROUND_SYNC_INTERVAL_MS = 300_000L
        private const val BOLTZ_AUTO_PREWARM_STABILIZATION_MS = 12_000L
        private const val BOLTZ_AUTO_PREWARM_FAILURE_COOLDOWN_MS = 30_000L
        private const val REVIEW_ORDER_VALIDITY_MS = 5 * 60_000L
        private const val MIN_LIQUID_SEND_FEE_RATE = 0.1
        private const val ACTIVE_LAYER_PERSIST_DELAY_MS = 250L
        private const val LIGHTNING_PENDING_WARNING_ATTEMPTS = 12
        private const val REFUND_ESCALATION_MS = 5 * 60_000L
        private const val STALE_REFUNDING_SESSION_MS = 24 * 60 * 60_000L
        private const val MONITOR_MAX_RETRIES = 3
        private const val MONITOR_RETRY_BASE_MS = 5_000L
        private const val HEARTBEAT_INTERVAL_MS = 60_000L
        private const val HEARTBEAT_RETRY_INTERVAL_MS = 15_000L
        private const val HEARTBEAT_PING_TIMEOUT_MS = 10_000L
        private const val HEARTBEAT_MAX_FAILURES = 3
        private const val RECONNECT_BASE_DELAY_MS = 5_000L
        private const val RECONNECT_MAX_DELAY_MS = 60_000L
        private const val RECONNECT_MAX_ATTEMPTS = 10
        private const val ADDRESS_BOOK_PREVIEW_SIZE = 20
    }

    private val secureStorage = SecureStorage.getInstance(application)
    private val torManager = TorManager.getInstance(application)
    private val repository = LiquidRepository(application, secureStorage, torManager)

    // ── Observable State ──

    val liquidState: StateFlow<LiquidWalletState> = repository.liquidState
    val loadedWalletId: StateFlow<String?> = repository.loadedWalletId
    val isLiquidConnected: StateFlow<Boolean> = repository.isConnected
    val torState: StateFlow<TorState> = torManager.torState

    private val _activeLayer = MutableStateFlow(WalletLayer.LAYER1)
    val activeLayer: StateFlow<WalletLayer> = _activeLayer

    private val _denominationState = MutableStateFlow(secureStorage.getLayer2Denomination())
    val denominationState: StateFlow<String> = _denominationState

    private val _swapState = MutableStateFlow<SwapState>(SwapState.Idle)
    val swapState: StateFlow<SwapState> = _swapState

    private val _sendState = MutableStateFlow<LiquidSendState>(LiquidSendState.Idle)
    val sendState: StateFlow<LiquidSendState> = _sendState

    private val _sendDraft = MutableStateFlow(emptySendDraft())
    val sendDraft: StateFlow<SendScreenDraft> = _sendDraft

    private val _pendingSwaps = MutableStateFlow<List<PendingSwapSession>>(emptyList())
    val pendingSwaps: StateFlow<List<PendingSwapSession>> = _pendingSwaps

    private val _pendingSubmarineSwap = MutableStateFlow<PendingLightningPaymentSession?>(null)
    val pendingSubmarineSwap: StateFlow<PendingLightningPaymentSession?> = _pendingSubmarineSwap

    private val _boltzRescueMnemonic = MutableStateFlow<String?>(null)
    val boltzRescueMnemonic: StateFlow<String?> = _boltzRescueMnemonic

    private val _allLiquidAddresses =
        MutableStateFlow<Triple<List<WalletAddress>, List<WalletAddress>, List<WalletAddress>>>(
            Triple(emptyList(), emptyList(), emptyList()),
        )
    val allLiquidAddresses: StateFlow<Triple<List<WalletAddress>, List<WalletAddress>, List<WalletAddress>>> =
        _allLiquidAddresses

    private val _allLiquidUtxos = MutableStateFlow<List<UtxoInfo>>(emptyList())
    val allLiquidUtxos: StateFlow<List<UtxoInfo>> = _allLiquidUtxos

    private val _preSelectedLiquidUtxo = MutableStateFlow<UtxoInfo?>(null)
    val preSelectedLiquidUtxo: StateFlow<UtxoInfo?> = _preSelectedLiquidUtxo.asStateFlow()

    private val _liquidTransactionLabels = MutableStateFlow<Map<String, String>>(emptyMap())
    val liquidTransactionLabels: StateFlow<Map<String, String>> = _liquidTransactionLabels

    private val _swapLimits = MutableStateFlow<Map<SwapService, SwapLimits>>(emptyMap())
    val swapLimits: StateFlow<Map<SwapService, SwapLimits>> = _swapLimits

    private val _lightningInvoiceLimits = MutableStateFlow<LightningInvoiceLimits?>(null)
    val lightningInvoiceLimits: StateFlow<LightningInvoiceLimits?> = _lightningInvoiceLimits

    private val _lightningInvoiceState = MutableStateFlow<LightningInvoiceState>(LightningInvoiceState.Idle)
    val lightningInvoiceState: StateFlow<LightningInvoiceState> = _lightningInvoiceState

    private val _pendingLightningInvoices = MutableStateFlow<List<PendingLightningInvoice>>(emptyList())
    val pendingLightningInvoices: StateFlow<List<PendingLightningInvoice>> = _pendingLightningInvoices

    private val _liquidServersState = MutableStateFlow(LiquidServersState())
    val liquidServersState: StateFlow<LiquidServersState> = _liquidServersState

    private val _isLayer2Enabled = MutableStateFlow(false)
    val isLayer2Enabled: StateFlow<Boolean> = _isLayer2Enabled

    // Guards notification firing until the first post-connection sync completes,
    // preventing stale Liquid transactions from being treated as new on cold start.
    private val _initialLiquidSyncComplete = MutableStateFlow(false)
    val initialLiquidSyncComplete: StateFlow<Boolean> = _initialLiquidSyncComplete
    private val _pendingFullSyncProgress = MutableStateFlow<SyncProgress?>(null)
    val pendingFullSyncProgress: StateFlow<SyncProgress?> = _pendingFullSyncProgress.asStateFlow()

    // Per-wallet Liquid toggle — reactive map so UI recomposes on change
    private val _liquidEnabledWallets = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val liquidEnabledWallets: StateFlow<Map<String, Boolean>> = _liquidEnabledWallets

    // Per-wallet Liquid gap limit — reactive map so UI recomposes on change
    private val _liquidGapLimits = MutableStateFlow<Map<String, Int>>(emptyMap())
    val liquidGapLimits: StateFlow<Map<String, Int>> = _liquidGapLimits

    // PSET export/sign/broadcast flow state for watch-only Liquid wallets
    private val _psetState = MutableStateFlow(LiquidPsetState())

    // Connection state (mirrors WalletViewModel pattern)
    private val _isLiquidConnecting = MutableStateFlow(false)
    val isLiquidConnecting: StateFlow<Boolean> = _isLiquidConnecting

    private val _liquidConnectionError = MutableStateFlow<String?>(null)
    val liquidConnectionError: StateFlow<String?> = _liquidConnectionError

    private val _certDialogState = MutableStateFlow<CertDialogState?>(null)
    val certDialogState: StateFlow<CertDialogState?> = _certDialogState

    private val _liquidBlockHeight = MutableStateFlow<UInt?>(null)
    val liquidBlockHeight: StateFlow<UInt?> = _liquidBlockHeight

    // Tor toggle for Liquid
    private val _isLiquidTorEnabled = MutableStateFlow(false)
    val isLiquidTorEnabled: StateFlow<Boolean> = _isLiquidTorEnabled

    private val _boltzApiSource = MutableStateFlow(SecureStorage.BOLTZ_API_DISABLED)
    val boltzApiSource: StateFlow<String> = _boltzApiSource

    private val _liquidExplorer = MutableStateFlow(SecureStorage.LIQUID_EXPLORER_DISABLED)
    val liquidExplorer: StateFlow<String> = _liquidExplorer

    private val _sideSwapApiSource = MutableStateFlow(SecureStorage.SIDESWAP_API_DISABLED)
    val sideSwapApiSource: StateFlow<String> = _sideSwapApiSource

    private val _preferredSwapService = MutableStateFlow(SwapService.BOLTZ)
    val preferredSwapService: StateFlow<SwapService> = _preferredSwapService

    // Auto-switch server on disconnect
    private val _liquidAutoSwitchServer = MutableStateFlow(false)
    val liquidAutoSwitchServer: StateFlow<Boolean> = _liquidAutoSwitchServer

    private val _settingsRefreshVersion = MutableStateFlow(0L)
    val settingsRefreshVersion: StateFlow<Long> = _settingsRefreshVersion

    // Connection job for cancellation
    private var connectionJob: Job? = null
    private var walletLifecycleJob: Job? = null
    private var pendingWalletLoadId: String? = null
    private var sideSwapMonitorJob: Job? = null
    private val swapMonitorJobs = linkedMapOf<String, Job>()
    private var boltzPrewarmJob: Job? = null
    private var boltzAutoPrewarmJob: Job? = null
    private var boltzAppStartWarmupPending = false
    private var lastLiquidConnectionReadyAtMs = 0L
    private var lastAutomaticBoltzPrewarmFailureAtMs = 0L
    private var liquidFullSyncJob: Job? = null
    private var backgroundSyncJob: Job? = null
    private var heartbeatJob: Job? = null
    private var reconnectRetryJob: Job? = null
    private val lightningInvoiceMonitorJobs = linkedMapOf<String, Job>()
    private val lightningInvoiceLastClaimAttempts = mutableMapOf<String, Long>()
    private val lightningInvoiceClaimingSwapIds = mutableSetOf<String>()
    private var lightningPaymentMonitorJob: Job? = null
    private var activeLayerPersistJob: Job? = null
    private var activeLightningInvoiceSwapId: String? = null
    private var liquidAddressRefreshJob: Job? = null
    private var liquidUtxoRefreshJob: Job? = null
    private var liquidLabelRefreshJob: Job? = null
    private var sendPreviewJob: Job? = null
    private var sendPreviewRequestId = 0L
    private val reviewPreparationMutex = Mutex()
    private val swapExecutionMutex = Mutex()
    private val autoSwitchVisited = linkedSetOf<String>()
    private val managedJobs = linkedSetOf<Job>()
    private var liquidContextActive = false
    private var swapScreenActive = false
    private var preparedSwapSession: PendingSwapSession? = null

    private val lifecycleCoordinator =
        AppLifecycleCoordinator(
            scope = viewModelScope,
            onBackgrounded = {
                ConnectivityKeepAlivePolicy.updateAppForegroundState(
                    context = appContext,
                    isInForeground = false,
                )
                val wasConnected = isLiquidConnected.value
                wipePreparedSwapReviewOnBackground()
                wasConnected
            },
            onForegrounded = { wasConnectedBeforeBackground, _ ->
                ConnectivityKeepAlivePolicy.updateAppForegroundState(
                    context = appContext,
                    isInForeground = true,
                )
                if (wasConnectedBeforeBackground && !isLiquidConnected.value) {
                    reconnectOnForeground()
                }
            },
        )

    // One-shot events
    private val _events = MutableSharedFlow<LiquidEvent>()
    val events: SharedFlow<LiquidEvent> = _events

    init {
        // Load persisted state
        _isLayer2Enabled.value = secureStorage.isLayer2Enabled()
        _isLiquidTorEnabled.value = activeLiquidServerRequiresTor()
        _boltzApiSource.value = secureStorage.getBoltzApiSource()
        _liquidExplorer.value = secureStorage.getLiquidExplorer()
        _sideSwapApiSource.value = secureStorage.getSideSwapApiSource()
        _preferredSwapService.value = repository.getPreferredSwapService()
        _liquidAutoSwitchServer.value = secureStorage.isLiquidAutoSwitchEnabled()
        seedDefaultServers()
        migrateLegacyLiquidServerSelection()
        loadLiquidServers()

        // Wallet load handles the initial Liquid connection and SideSwap feed
        // once the active wallet is confirmed to support Liquid.

        viewModelScope.launch {
            loadedWalletId
                .collect { walletId ->
                    if (walletId == null) {
                        clearDerivedLiquidSnapshots()
                    } else {
                        refreshBoltzRescueMnemonic()
                        refreshLiquidAddressBook()
                        refreshLiquidUtxos()
                        refreshLiquidTransactionLabelSnapshots(walletId)
                    }
                }
        }

        viewModelScope.launch {
            combine(
                isLiquidConnected,
                _isLayer2Enabled,
                _boltzApiSource,
                _sideSwapApiSource,
                _liquidServersState.map { it.activeServerId },
            ) { connected, layer2Enabled, boltzSource, sideSwapSource, activeServerId ->
                listOf(
                    connected.toString(),
                    layer2Enabled.toString(),
                    boltzSource,
                    sideSwapSource,
                    activeServerId.orEmpty(),
                )
            }
                .distinctUntilChanged()
                .collect {
                    syncForegroundConnectivityPolicy()
                }
        }

        viewModelScope.launch {
            liquidState
                .map { Triple(it.lastSyncTimestamp, it.currentAddress, it.currentAddressLabel) }
                .distinctUntilChanged()
                .collect {
                    if (loadedWalletId.value != null) {
                        refreshLiquidAddressBook()
                        refreshLiquidUtxos()
                        refreshLiquidTransactionLabelSnapshots()
                        clearSettledLightningInvoiceUiIfNeeded()
                    }
                }
        }

        viewModelScope.launch {
            liquidState
                .map { state -> state.isFullSyncing }
                .distinctUntilChanged()
                .collect { isFullSyncing ->
                    if (isFullSyncing) {
                        clearPendingFullSyncProgress()
                    }
                }
        }

        viewModelScope.launch {
            repository.connectionEvents.collect { event ->
                when (event) {
                    LiquidRepository.ConnectionEvent.ConnectionLost -> {
                        if (BuildConfig.DEBUG) {
                            Log.w(TAG, "Liquid subscription loss detected; checking server before reconnect")
                        }
                        handleLiquidSubscriptionLost()
                    }
                }
            }
        }
    }

    private fun clearDerivedLiquidSnapshots() {
        liquidAddressRefreshJob?.cancel()
        liquidUtxoRefreshJob?.cancel()
        liquidLabelRefreshJob?.cancel()
        _boltzRescueMnemonic.value = null
        _allLiquidAddresses.value = Triple(emptyList(), emptyList(), emptyList())
        _allLiquidUtxos.value = emptyList()
        _liquidTransactionLabels.value = emptyMap()
    }

    private fun showPendingFullSyncProgress(status: String) {
        _pendingFullSyncProgress.value = SyncProgress(status = status)
    }

    private fun clearPendingFullSyncProgress() {
        _pendingFullSyncProgress.value = null
    }

    private fun refreshBoltzRescueMnemonic() {
        launchLiquidJob(Dispatchers.IO) {
            _boltzRescueMnemonic.value = repository.getBoltzRescueMnemonic()
        }
    }

    private fun refreshLiquidAddressBook() {
        liquidAddressRefreshJob?.cancel()
        liquidAddressRefreshJob =
            launchLiquidJob(Dispatchers.IO) {
                if (_allLiquidAddresses.value.first.isEmpty() &&
                    _allLiquidAddresses.value.second.isEmpty() &&
                    _allLiquidAddresses.value.third.isEmpty()
                ) {
                    _allLiquidAddresses.value = repository.getAddressPreview(ADDRESS_BOOK_PREVIEW_SIZE)
                }
                _allLiquidAddresses.value = repository.getAllAddresses()
            }
    }

    private fun refreshLiquidUtxos() {
        liquidUtxoRefreshJob?.cancel()
        liquidUtxoRefreshJob =
            launchLiquidJob(Dispatchers.IO) {
                _allLiquidUtxos.value = repository.getAllUtxos()
            }
    }

    private fun refreshLiquidTransactionLabelSnapshots(walletId: String? = loadedWalletId.value) {
        liquidLabelRefreshJob?.cancel()
        liquidLabelRefreshJob =
            launchLiquidJob(Dispatchers.IO) {
                _liquidTransactionLabels.value =
                    walletId?.let { secureStorage.getAllLiquidTransactionLabels(it) }.orEmpty()
            }
    }

    private fun refreshCurrentLiquidSnapshots() {
        refreshLiquidAddressBook()
        refreshLiquidUtxos()
        refreshLiquidTransactionLabelSnapshots()
    }

    private fun refreshPendingLightningInvoices() {
        val pending = repository.getPendingLightningInvoices()
        val pendingSwapIds = pending.mapTo(mutableSetOf()) { it.swapId }
        lightningInvoiceLastClaimAttempts.keys.retainAll(pendingSwapIds)
        lightningInvoiceClaimingSwapIds.retainAll(pendingSwapIds)
        _pendingLightningInvoices.value = pending.map { invoice ->
            invoice.copy(
                lastClaimAttemptAt = lightningInvoiceLastClaimAttempts[invoice.swapId],
                isClaiming = invoice.swapId in lightningInvoiceClaimingSwapIds,
            )
        }
    }

    private fun markLightningInvoiceClaiming(
        swapId: String,
        claiming: Boolean,
        updateAttempt: Boolean = false,
    ) {
        if (updateAttempt) {
            lightningInvoiceLastClaimAttempts[swapId] = nowMs()
        }
        if (claiming) {
            lightningInvoiceClaimingSwapIds += swapId
        } else {
            lightningInvoiceClaimingSwapIds -= swapId
        }
        refreshPendingLightningInvoices()
    }

    private suspend fun clearSettledLightningInvoiceUiIfNeeded() {
        refreshPendingLightningInvoices()
        val readyState = _lightningInvoiceState.value as? LightningInvoiceState.Ready ?: return
        if (repository.getPendingLightningInvoiceSession(readyState.swapId) != null) return

        if (activeLightningInvoiceSwapId == readyState.swapId) {
            activeLightningInvoiceSwapId = null
        }
        stopLightningInvoiceMonitorAndJoin(readyState.swapId)
        _lightningInvoiceState.value = LightningInvoiceState.Idle
    }

    private fun refreshLiquidSnapshotsIfNeeded(walletId: String) {
        if (walletId == loadedWalletId.value) {
            refreshCurrentLiquidSnapshots()
        }
    }

    override fun onCleared() {
        lifecycleCoordinator.dispose()
        cancelActiveLayerPersistence()
        stopHeartbeat()
        stopReconnectRetry()
        cancelManagedJobs()
        sideSwapMonitorJob?.cancel()
        synchronized(swapMonitorJobs) {
            swapMonitorJobs.values.toList()
        }.forEach { it.cancel() }
        val cleanupScope = MainScope()
        cleanupScope.launch {
            try {
                repository.unloadWallet()
            } catch (_: Exception) { /* best-effort cleanup */ }
            try {
                repository.sideSwapClient.unsubscribeWalletInfo()
            } catch (_: Exception) { /* best-effort cleanup */ }
            try {
                repository.sideSwapClient.disconnect()
            } catch (_: Exception) { /* best-effort cleanup */ }
            repository.close()
            cleanupScope.cancel()
        }
        ConnectivityKeepAlivePolicy.updateLiquidState(
            context = appContext,
            connected = false,
            electrumUsesTor = false,
            externalTorRequired = false,
        )
        super.onCleared()
    }

    private fun syncForegroundConnectivityPolicy() {
        ConnectivityKeepAlivePolicy.updateForegroundConnectivityEnabled(
            context = appContext,
            enabled = secureStorage.isForegroundConnectivityEnabled(),
        )
        ConnectivityKeepAlivePolicy.updateLiquidState(
            context = appContext,
            connected = isLiquidConnected.value,
            electrumUsesTor = isLiquidElectrumTorRequired(),
            externalTorRequired = shouldKeepLayer2ExternalTorRunning(),
        )
    }

    private fun isLiquidElectrumTorRequired(): Boolean {
        return isLiquidConnected.value && activeLiquidServerRequiresTor()
    }

    private fun shouldKeepTorRunningForLiquid(localTorRequirement: Boolean): Boolean {
        syncForegroundConnectivityPolicy()
        return localTorRequirement || ConnectivityKeepAlivePolicy.hasTorRequirementOutsideLiquid()
    }

    private fun launchLiquidJob(
        context: CoroutineContext = EmptyCoroutineContext,
        block: suspend CoroutineScope.() -> Unit,
    ): Job {
        val job = viewModelScope.launch(context = context, block = block)
        synchronized(managedJobs) {
            managedJobs += job
        }
        job.invokeOnCompletion {
            synchronized(managedJobs) {
                managedJobs.remove(job)
            }
        }
        return job
    }

    private fun cancelManagedJobs() {
        val jobs = synchronized(managedJobs) {
            managedJobs.toList()
        }
        jobs.forEach { it.cancel() }
        connectionJob = null
        walletLifecycleJob = null
        pendingWalletLoadId = null
    }

    private fun resetLiquidUiState() {
        cancelActiveSendPreview()
        _swapState.value = SwapState.Idle
        _sendState.value = LiquidSendState.Idle
        _sendDraft.value = emptySendDraft()
        _psetState.value = LiquidPsetState()
        _preSelectedLiquidUtxo.value = null
        preparedSwapSession = null
        _pendingSwaps.value = emptyList()
        _pendingSubmarineSwap.value = null
        _swapLimits.value = emptyMap()
        _pendingLightningInvoices.value = emptyList()
        _lightningInvoiceState.value = LightningInvoiceState.Idle
        activeLightningInvoiceSwapId = null
        stopAllLightningInvoiceMonitors()
        _isLiquidConnecting.value = false
        _liquidConnectionError.value = null
        _liquidBlockHeight.value = null
    }

    private fun cancelActiveLayerPersistence() {
        activeLayerPersistJob?.cancel()
        activeLayerPersistJob = null
    }

    private fun cancelActiveSendPreview() {
        sendPreviewRequestId += 1
        sendPreviewJob?.cancel()
        sendPreviewJob = null
    }

    private fun startLatestSendPreview(
        kind: LiquidSendKind,
        block: suspend () -> LiquidSendPreview,
    ) {
        cancelActiveSendPreview()
        val requestId = sendPreviewRequestId
        _sendState.value = LiquidSendState.Estimating(kind = kind)
        val job =
            launchLiquidJob {
                try {
                    val preview = block()
                    if (requestId == sendPreviewRequestId) {
                        _sendState.value = LiquidSendState.ReviewReady(preview)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (requestId == sendPreviewRequestId) {
                        _sendState.value = LiquidSendState.Failed(safeLiquidErrorMessage(e, "Send failed"))
                    }
                } finally {
                    if (sendPreviewJob === currentCoroutineContext()[Job]) {
                        sendPreviewJob = null
                    }
                }
            }
        sendPreviewJob = job
    }

    private fun persistActiveLayer(walletId: String, layer: WalletLayer) {
        activeLayerPersistJob?.cancel()
        activeLayerPersistJob =
            viewModelScope.launch(Dispatchers.IO) {
                delay(ACTIVE_LAYER_PERSIST_DELAY_MS)
                secureStorage.setActiveLayer(walletId, layer.name)
            }
    }

    private suspend fun disconnectSideSwapFeed() {
        try {
            repository.sideSwapClient.unsubscribeWalletInfo()
        } catch (_: Exception) {
            // Best-effort cleanup.
        }
        repository.sideSwapClient.disconnect()
    }

    private fun stopSideSwapMonitor() {
        sideSwapMonitorJob?.cancel()
        sideSwapMonitorJob = null
        viewModelScope.launch {
            disconnectSideSwapFeed()
        }
    }

    private fun stopAllSwapMonitors() {
        val jobs = synchronized(swapMonitorJobs) {
            val allJobs = swapMonitorJobs.values.toList()
            swapMonitorJobs.clear()
            allJobs
        }
        jobs.forEach { it.cancel() }
    }

    private fun stopAllLightningInvoiceMonitors() {
        val jobs = synchronized(lightningInvoiceMonitorJobs) {
            val allJobs = lightningInvoiceMonitorJobs.values.toList()
            lightningInvoiceMonitorJobs.clear()
            allJobs
        }
        jobs.forEach { it.cancel() }
    }

    private suspend fun stopLightningInvoiceMonitorAndJoin(swapId: String) {
        val job = synchronized(lightningInvoiceMonitorJobs) {
            lightningInvoiceMonitorJobs.remove(swapId)
        } ?: return
        if (job == currentCoroutineContext()[Job]) return
        job.cancelAndJoin()
    }

    private fun stopLightningInvoiceMonitor(swapId: String) {
        val job = synchronized(lightningInvoiceMonitorJobs) {
            lightningInvoiceMonitorJobs.remove(swapId)
        } ?: return
        job.cancel()
    }

    private suspend fun stopSwapMonitorAndJoin(swapId: String) {
        val job = synchronized(swapMonitorJobs) {
            swapMonitorJobs.remove(swapId)
        } ?: return
        if (job == currentCoroutineContext()[Job]) return
        job.cancelAndJoin()
    }

    private fun savePreparedSwap(pendingSwap: PendingSwapSession) {
        preparedSwapSession = pendingSwap
        clearPersistedReviewSwaps()
    }

    private fun getPreparedSwap(): PendingSwapSession? {
        return preparedSwapSession
    }

    private fun clearPreparedSwap() {
        preparedSwapSession = null
        clearPersistedReviewSwaps()
    }

    private fun wipePreparedSwapReviewOnBackground() {
        val preparedSwap = preparedSwapSession
        preparedSwapSession = null
        clearPersistedReviewSwaps()
        if (_swapState.value is SwapState.ReviewReady || _swapState.value is SwapState.PreparingReview) {
            _swapState.value = SwapState.Idle
        }
        if (preparedSwap?.phase == PendingSwapPhase.REVIEW && preparedSwap.service == SwapService.BOLTZ) {
            launchLiquidJob {
                runCatching { repository.discardPreparedBoltzChainSwap(preparedSwap.swapId) }
                    .onFailure { error ->
                        logWarn(
                            "Failed to discard prepared swap on background swapId=${preparedSwap.swapId}",
                            Exception(error),
                        )
                    }
            }
        }
    }

    private fun upsertPendingSwap(pendingSwap: PendingSwapSession) {
        _pendingSwaps.value =
            (_pendingSwaps.value.filterNot { it.swapId == pendingSwap.swapId } + pendingSwap)
                .sortedByDescending { it.createdAt }
        repository.savePendingSwapSession(pendingSwap)
        applySwapTransactionLabels(pendingSwap)
    }

    private fun applySwapTransactionLabels(pendingSwap: PendingSwapSession) {
        val walletId = loadedWalletId.value ?: return
        val label = pendingSwap.label?.trim()?.takeIf { it.isNotBlank() } ?: return

        pendingSwap.fundingTxid?.takeIf { it.isNotBlank() }?.let { txid ->
            if (pendingSwap.direction == SwapDirection.BTC_TO_LBTC) {
                secureStorage.saveTransactionLabel(walletId, txid, label)
            } else {
                if (walletId == loadedWalletId.value) {
                    repository.saveLiquidTransactionLabel(txid, label)
                } else {
                    secureStorage.saveLiquidTransactionLabel(walletId, txid, label)
                }
            }
        }

        pendingSwap.settlementTxid?.takeIf { it.isNotBlank() }?.let { txid ->
            if (pendingSwap.direction == SwapDirection.BTC_TO_LBTC) {
                if (walletId == loadedWalletId.value) {
                    repository.saveLiquidTransactionLabel(txid, label)
                } else {
                    secureStorage.saveLiquidTransactionLabel(walletId, txid, label)
                }
            } else {
                secureStorage.saveTransactionLabel(walletId, txid, label)
            }
        }
    }

    private fun getTrackedPendingSwap(swapId: String): PendingSwapSession? {
        return _pendingSwaps.value.firstOrNull { it.swapId == swapId }
    }

    private fun removeTrackedPendingSwap(swapId: String) {
        _pendingSwaps.value = _pendingSwaps.value.filterNot { it.swapId == swapId }
        if (preparedSwapSession?.swapId == swapId) {
            preparedSwapSession = null
        }
        repository.deletePendingSwapSession(swapId)
    }

    private fun clearPersistedReviewSwaps(keepSwapId: String? = null) {
        repository.getPendingSwapSessions()
            .filter { it.phase == PendingSwapPhase.REVIEW && it.swapId != keepSwapId }
            .forEach {
                if (it.service == SwapService.BOLTZ) {
                    launchLiquidJob {
                        repository.discardPreparedBoltzChainSwap(it.swapId)
                    }
                }
                repository.deletePendingSwapSession(it.swapId)
                if (it.service == SwapService.BOLTZ) {
                    repository.deleteBoltzChainSwapDraftBySwapId(it.swapId)
                }
            }
    }

    private fun nowMs(): Long = System.currentTimeMillis()

    private fun normalizeProviderCreatedAt(createdAt: Long): Long {
        if (createdAt <= 0L) return nowMs()
        return if (createdAt < 10_000_000_000L) createdAt * 1000L else createdAt
    }

    private fun buildSwapReviewRequestKey(
        service: SwapService,
        direction: SwapDirection,
        amountSats: Long,
        selectedUtxos: List<UtxoInfo>?,
        bitcoinWalletAddress: String?,
        destinationAddress: String?,
        usesMaxAmount: Boolean,
        fundingFeeRateOverride: Double?,
    ): String {
        return listOf(
            service.name,
            direction.name,
            amountSats.toString(),
            usesMaxAmount.toString(),
            bitcoinWalletAddress.orEmpty(),
            destinationAddress.orEmpty(),
            fundingFeeRateOverride?.toString().orEmpty(),
            selectedUtxos?.joinToString(",") { it.outpoint }.orEmpty(),
        ).joinToString("|")
    }

    private data class BoltzMaxReviewMismatch(
        val pendingAmount: Long?,
        val reviewAmount: Long,
        val orderAmount: Long,
        val verifiedAmount: Long?,
        val orderVerified: Boolean,
    )

    private fun getBoltzMaxReviewMismatch(pendingSwap: PendingSwapSession): BoltzMaxReviewMismatch? {
        if (
            pendingSwap.service != SwapService.BOLTZ ||
            pendingSwap.phase != PendingSwapPhase.REVIEW ||
            !pendingSwap.usesMaxAmount
        ) {
            return null
        }
        val verifiedAmount = pendingSwap.boltzVerifiedRecipientAmountSats?.takeIf { it > 0L }
        val orderAmount = pendingSwap.boltzOrderAmountSats ?: pendingSwap.sendAmount
        val hasMismatch =
            hasBoltzMaxReviewMismatch(
                usesMaxAmount = true,
                state =
                    BoltzMaxReviewState(
                        reviewAmount = pendingSwap.sendAmount,
                        orderAmount = orderAmount,
                        verifiedAmount = verifiedAmount,
                        pendingAmount = pendingSwap.sendAmount,
                        orderVerified = pendingSwap.boltzMaxOrderAmountVerified,
                    ),
            )
        if (!hasMismatch) {
            return null
        }
        return BoltzMaxReviewMismatch(
            pendingAmount = pendingSwap.sendAmount,
            reviewAmount = pendingSwap.sendAmount,
            orderAmount = orderAmount,
            verifiedAmount = verifiedAmount,
            orderVerified = pendingSwap.boltzMaxOrderAmountVerified,
        )
    }

    private fun canReusePreparedSwapReview(
        pendingSwap: PendingSwapSession,
        requestKey: String,
    ): Boolean {
        val sameRequest =
            (pendingSwap.requestKey != null && pendingSwap.requestKey == requestKey) ||
                (pendingSwap.requestKey == null && preparedSwapSession?.swapId == pendingSwap.swapId)
        if (!sameRequest || pendingSwap.phase != PendingSwapPhase.REVIEW) {
            return false
        }
        if (pendingSwap.reviewExpiresAt > 0L && nowMs() > pendingSwap.reviewExpiresAt) {
            return false
        }
        if (pendingSwap.service == SwapService.BOLTZ && getBoltzMaxReviewMismatch(pendingSwap) != null) {
            return false
        }
        return true
    }

    private suspend fun synchronizePendingSwapWithBoltzDraft(
        pendingSwap: PendingSwapSession,
        context: String,
    ): PendingSwapSession {
        if (pendingSwap.service != SwapService.BOLTZ) {
            return pendingSwap
        }
        val draft = repository.getBoltzChainSwapDraftBySwapId(pendingSwap.swapId)
        val synchronized =
            if (draft == null) {
                pendingSwap
            } else {
                val resolvedMaxMetadata =
                    resolveBoltzMaxReviewMetadata(
                        pendingOrderAmount = pendingSwap.boltzOrderAmountSats,
                        pendingVerifiedAmount = pendingSwap.boltzVerifiedRecipientAmountSats,
                        pendingOrderVerified = pendingSwap.boltzMaxOrderAmountVerified,
                        draftOrderAmount = draft.orderAmountSats,
                        draftVerifiedAmount = draft.fundingPreview?.verifiedRecipientAmountSats?.takeIf { it > 0L },
                        draftOrderVerified = draft.maxOrderAmountVerified,
                    )
                pendingSwap.copy(
                    sendAmount = draft.sendAmount,
                    estimatedTerms = draft.estimatedTerms,
                    depositAddress = draft.depositAddress ?: pendingSwap.depositAddress,
                    receiveAddress = draft.receiveAddress ?: pendingSwap.receiveAddress,
                    refundAddress = draft.refundAddress ?: pendingSwap.refundAddress,
                    reviewExpiresAt = draft.reviewExpiresAt.takeIf { it > 0L } ?: pendingSwap.reviewExpiresAt,
                    fundingLiquidFeeRate =
                        draft.fundingLiquidFeeRate.takeIf { it > 0.0 } ?: pendingSwap.fundingLiquidFeeRate,
                    boltzOrderAmountSats = resolvedMaxMetadata.orderAmount,
                    boltzVerifiedRecipientAmountSats = resolvedMaxMetadata.verifiedAmount,
                    boltzMaxOrderAmountVerified = resolvedMaxMetadata.orderVerified,
                    actualFundingFeeSats = pendingSwap.actualFundingFeeSats ?: draft.fundingPreview?.feeSats,
                    actualFundingTxVBytes = pendingSwap.actualFundingTxVBytes ?: draft.fundingPreview?.txVBytes,
                    fundingTxid = draft.fundingTxid ?: pendingSwap.fundingTxid,
                    settlementTxid = draft.settlementTxid ?: pendingSwap.settlementTxid,
                    boltzSnapshot = draft.snapshot ?: pendingSwap.boltzSnapshot,
                )
            }
        if (
            synchronized.phase == PendingSwapPhase.REVIEW &&
            synchronized.usesMaxAmount &&
            synchronized.boltzOrderAmountSats == null
        ) {
            val error = "This max Boltz review is stale. Create a new review before continuing."
            logWarn(
                "$context rejecting max Boltz review without saved order metadata swapId=${pendingSwap.swapId}",
                Exception(error),
            )
            repository.discardPreparedBoltzChainSwap(synchronized.swapId)
            repository.deletePendingSwapSession(synchronized.swapId)
            _pendingSwaps.value = _pendingSwaps.value.filterNot { it.swapId == pendingSwap.swapId }
            if (preparedSwapSession?.swapId == pendingSwap.swapId) {
                preparedSwapSession = null
            }
            throw Exception(error)
        }
        val mismatch = getBoltzMaxReviewMismatch(synchronized) ?: return synchronized
        val error = "This max Boltz review is stale. Create a new review before continuing."
        logWarn(
            "$context rejecting stale max Boltz review swapId=${pendingSwap.swapId} " +
                "pending=${mismatch.pendingAmount} review=${mismatch.reviewAmount} order=${mismatch.orderAmount} " +
                "verified=${mismatch.verifiedAmount} verifiedFlag=${mismatch.orderVerified}",
            Exception(error),
        )
        if (synchronized.phase == PendingSwapPhase.REVIEW && synchronized.fundingTxid.isNullOrBlank()) {
            repository.discardPreparedBoltzChainSwap(synchronized.swapId)
        }
        repository.deletePendingSwapSession(synchronized.swapId)
        _pendingSwaps.value = _pendingSwaps.value.filterNot { it.swapId == pendingSwap.swapId }
        if (preparedSwapSession?.swapId == pendingSwap.swapId) {
            preparedSwapSession = null
        }
        throw Exception(error)
    }

    private fun buildBoltzReviewTerms(
        quote: SwapQuote,
        fundingPreview: BoltzChainFundingPreview? = null,
        fundingBtcFeeRateOverride: Double? = null,
        fundingLiquidFeeRateOverride: Double? = null,
    ): EstimatedSwapTerms =
        when (quote.direction) {
            SwapDirection.BTC_TO_LBTC -> {
                val fundingFee =
                    fundingPreview?.feeSats
                        ?: adjustedFundingNetworkFee(
                            quotedFeeSats = quote.btcNetworkFee,
                            quotedFeeRate = quote.btcFeeRate,
                            feeRateOverride = fundingBtcFeeRateOverride,
                        )
                        ?: quote.btcNetworkFee
                val fundingFeeRate =
                    fundingPreview?.effectiveFeeRate?.takeIf { it > 0.0 }
                        ?: fundingBtcFeeRateOverride
                        ?: quote.btcFeeRate
                EstimatedSwapTerms(
                    receiveAmount = quote.receiveAmount,
                    serviceFee = quote.serviceFee,
                    btcNetworkFee = fundingFee,
                    liquidNetworkFee = quote.liquidNetworkFee,
                    providerMinerFee = quote.providerMinerFee,
                    btcFeeRate = fundingFeeRate,
                    displayLiquidFeeRate = quote.liquidFeeRate,
                    fundingNetworkFee = fundingFee,
                    fundingFeeRate = fundingFeeRate,
                    payoutNetworkFee = quote.liquidNetworkFee,
                    payoutFeeRate = quote.liquidFeeRate,
                    minAmount = quote.minAmount,
                    maxAmount = quote.maxAmount,
                    estimatedTime = quote.estimatedTime,
                )
            }
            SwapDirection.LBTC_TO_BTC -> {
                val fundingFee =
                    fundingPreview?.feeSats
                        ?: adjustedFundingNetworkFee(
                            quotedFeeSats = quote.liquidNetworkFee,
                            quotedFeeRate = quote.liquidFeeRate,
                            feeRateOverride = fundingLiquidFeeRateOverride,
                        )
                        ?: quote.liquidNetworkFee
                val fundingFeeRate =
                    fundingPreview?.effectiveFeeRate?.takeIf { it > 0.0 }
                        ?: fundingLiquidFeeRateOverride
                        ?: quote.liquidFeeRate
                EstimatedSwapTerms(
                    receiveAmount = quote.receiveAmount,
                    serviceFee = quote.serviceFee,
                    btcNetworkFee = quote.btcNetworkFee,
                    liquidNetworkFee = fundingFee,
                    providerMinerFee = quote.providerMinerFee,
                    btcFeeRate = quote.btcFeeRate,
                    displayLiquidFeeRate = fundingFeeRate,
                    fundingNetworkFee = fundingFee,
                    fundingFeeRate = fundingFeeRate,
                    payoutNetworkFee = quote.btcNetworkFee,
                    payoutFeeRate = quote.btcFeeRate,
                    minAmount = quote.minAmount,
                    maxAmount = quote.maxAmount,
                    estimatedTime = quote.estimatedTime,
                )
            }
        }

    private fun adjustedFundingNetworkFee(
        quotedFeeSats: Long,
        quotedFeeRate: Double,
        feeRateOverride: Double?,
    ): Long? {
        val override = feeRateOverride?.takeIf { it > 0.0 } ?: return null
        if (quotedFeeSats <= 0L || quotedFeeRate <= 0.0) return null
        val estimatedVbytes = quotedFeeSats.toDouble() / quotedFeeRate
        if (estimatedVbytes <= 0.0) return null
        return kotlin.math.ceil(estimatedVbytes * override).toLong().coerceAtLeast(1L)
    }

    private suspend fun createBoltzDraftForQuote(
        requestKey: String,
        quote: SwapQuote,
        selectedFundingUtxos: List<UtxoInfo>?,
        btcAddress: String,
        liquidDestinationAddress: String?,
        usesMaxAmount: Boolean,
        fundingBtcFeeRateOverride: Double?,
        fundingLiquidFeeRateOverride: Double?,
    ): BoltzChainSwapDraft {
        return repository.createBoltzChainSwapDraft(
            requestKey = requestKey,
            direction = quote.direction,
            amountSats = quote.sendAmount,
            usesMaxAmount = usesMaxAmount,
            selectedFundingOutpoints = selectedFundingUtxos?.map { it.outpoint }.orEmpty(),
            estimatedTerms =
                buildBoltzReviewTerms(
                    quote = quote,
                    fundingBtcFeeRateOverride = fundingBtcFeeRateOverride,
                    fundingLiquidFeeRateOverride = fundingLiquidFeeRateOverride,
                ),
            fundingLiquidFeeRate = fundingLiquidFeeRateOverride ?: quote.liquidFeeRate,
            bitcoinAddress = btcAddress,
            liquidReceiveAddressOverride = liquidDestinationAddress,
        )
    }

    private fun getRecreatedBoltzMaxAmount(
        quote: SwapQuote,
        fundingPreview: BoltzChainFundingPreview?,
        usesMaxAmount: Boolean,
    ): Long? {
        val verifiedAmount = fundingPreview?.verifiedRecipientAmountSats?.takeIf { it > 0L } ?: return null
        return verifiedAmount.takeIf {
            shouldRecreateBoltzMaxOrder(
                direction = quote.direction,
                usesMaxAmount = usesMaxAmount,
                quotedAmount = quote.sendAmount,
                verifiedAmount = verifiedAmount,
            )
        }
    }

    private fun shouldFallbackToBoltzMaxFundingPreview(
        direction: SwapDirection,
        usesMaxAmount: Boolean,
        error: Throwable,
    ): Boolean {
        return usesMaxAmount && isBoltzMaxFundingShortfallError(direction, error)
    }

    private fun updateTrackedPendingSwapState(
        pendingSwap: PendingSwapSession,
        phase: PendingSwapPhase = pendingSwap.phase,
        status: String = pendingSwap.status,
        fundingTxid: String? = pendingSwap.fundingTxid,
        settlementTxid: String? = pendingSwap.settlementTxid,
        boltzSnapshot: String? = pendingSwap.boltzSnapshot,
    ): PendingSwapSession {
        val updated =
            pendingSwap.copy(
                phase = phase,
                status = status,
                fundingTxid = fundingTxid,
                settlementTxid = settlementTxid,
                boltzSnapshot = boltzSnapshot,
            )
        upsertPendingSwap(updated)
        if (updated.service == SwapService.BOLTZ) {
            repository.syncBoltzChainSwapDraftFromPendingSwap(updated)
        }
        return updated
    }

    private fun setReviewReady(pendingSwap: PendingSwapSession) {
        val reviewSwap = pendingSwap.copy(phase = PendingSwapPhase.REVIEW)
        savePreparedSwap(reviewSwap)
        _swapState.value = SwapState.ReviewReady(reviewSwap)
    }

    private fun setSwapInProgress(
        pendingSwap: PendingSwapSession,
        status: String,
        phase: PendingSwapPhase = pendingSwap.phase,
    ): PendingSwapSession {
        return updateTrackedPendingSwapState(pendingSwap, phase = phase, status = status)
    }

    private fun pendingSwapStatus(pendingSwap: PendingSwapSession): String {
        return when {
            pendingSwap.status.isNotBlank() -> pendingSwap.status
            pendingSwap.phase == PendingSwapPhase.REVIEW -> "Exact order prepared. Review before funding."
            pendingSwap.phase == PendingSwapPhase.FUNDING ->
                "Funding may already be in flight. Verifying current status before allowing any retry."
            pendingSwap.direction == SwapDirection.BTC_TO_LBTC ->
                "Waiting for BTC deposit to ${pendingSwap.depositAddress}"
            pendingSwap.fundingTxid != null ->
                "Funding broadcast. Waiting for ${pendingSwap.service.name} payout..."
            else -> "Waiting for ${pendingSwap.service.name} to continue the swap..."
        }
    }

    private fun saveChainSwapLiquidTxDetails(
        txid: String,
        pendingSwap: PendingSwapSession,
        role: LiquidSwapTxRole,
    ) {
        repository.saveLiquidSwapDetails(
            txid = txid,
            details =
                repository.buildLiquidSwapDetails(
                    service = pendingSwap.service,
                    direction = pendingSwap.direction,
                    swapId = pendingSwap.swapId,
                    role = role,
                    depositAddress = pendingSwap.depositAddress,
                    receiveAddress = pendingSwap.receiveAddress,
                    refundAddress = pendingSwap.refundAddress,
                    sendAmountSats = pendingSwap.sendAmount,
                    expectedReceiveAmountSats = pendingSwap.estimatedTerms.receiveAmount,
                ),
        )
    }

    private fun saveChainSwapBitcoinTxDetails(
        txid: String,
        pendingSwap: PendingSwapSession,
        role: LiquidSwapTxRole,
    ) {
        val walletId = loadedWalletId.value ?: return
        secureStorage.saveTransactionSwapDetails(
            walletId = walletId,
            txid = txid,
            details =
                repository.buildLiquidSwapDetails(
                    service = pendingSwap.service,
                    direction = pendingSwap.direction,
                    swapId = pendingSwap.swapId,
                    role = role,
                    depositAddress = pendingSwap.depositAddress,
                    receiveAddress = pendingSwap.receiveAddress,
                    refundAddress = pendingSwap.refundAddress,
                    sendAmountSats = pendingSwap.sendAmount,
                    expectedReceiveAmountSats = pendingSwap.estimatedTerms.receiveAmount,
                ),
        )
    }

    private fun detectChainSwapSettlementTxid(pendingSwap: PendingSwapSession): String? {
        if (pendingSwap.direction != SwapDirection.BTC_TO_LBTC) return null
        val receiveAddress = pendingSwap.receiveAddress?.takeIf { it.isNotBlank() } ?: return null
        val expectedReceiveAmount = pendingSwap.estimatedTerms.receiveAmount.takeIf { it > 0L }
        val candidateTransactions =
            liquidState.value.transactions.filter { tx ->
                tx.txid != pendingSwap.fundingTxid &&
                    tx.balanceSatoshi > 0L &&
                    tx.walletAddress.equals(receiveAddress, ignoreCase = true)
            }

        return candidateTransactions.firstOrNull { tx ->
            expectedReceiveAmount == null ||
                tx.walletAddressAmountSats == expectedReceiveAmount ||
                tx.balanceSatoshi == expectedReceiveAmount
        }?.txid ?: candidateTransactions.firstOrNull()?.txid
    }

    private suspend fun restoreBoltzSwapIfNeeded(pendingSwap: PendingSwapSession): PendingSwapSession {
        if (pendingSwap.service != SwapService.BOLTZ) return pendingSwap
        val snapshot =
            pendingSwap.boltzSnapshot?.takeIf { it.isNotBlank() }
                ?: repository.getBoltzChainSwapSnapshot(pendingSwap.swapId)?.takeIf { it.isNotBlank() }
                ?: return pendingSwap
        repository.restoreBoltzChainSwap(pendingSwap.swapId, snapshot)
        val latestSnapshot = repository.getBoltzChainSwapSnapshot(pendingSwap.swapId) ?: snapshot
        return if (latestSnapshot == pendingSwap.boltzSnapshot) {
            pendingSwap
        } else {
            pendingSwap.copy(boltzSnapshot = latestSnapshot)
        }
    }

    private suspend fun validatePendingSwapForExecution(
        pendingSwap: PendingSwapSession,
        fromResume: Boolean = false,
    ): PendingSwapSession {
        val synchronizedPendingSwap =
            synchronizePendingSwapWithBoltzDraft(
                pendingSwap = pendingSwap,
                context = if (fromResume) {
                    "validatePendingSwapForExecution(resume)"
                } else {
                    "validatePendingSwapForExecution"
                },
            )
        if (pendingSwap.phase != PendingSwapPhase.REVIEW) {
            return synchronizedPendingSwap
        }
        if (synchronizedPendingSwap.reviewExpiresAt > 0L && nowMs() > synchronizedPendingSwap.reviewExpiresAt) {
            clearPreparedSwap()
            throw Exception("Prepared order expired. Create a new review before funding.")
        }

        return when (synchronizedPendingSwap.service) {
            SwapService.SIDESWAP -> {
                val isPegIn = synchronizedPendingSwap.direction == SwapDirection.BTC_TO_LBTC
                val status = repository.sideSwapClient.getPegStatus(synchronizedPendingSwap.swapId, isPegIn)
                ensureSideSwapStatusMatchesReview(synchronizedPendingSwap, status.addr, status.addrRecv)
                val hasActivity = status.transactions.isNotEmpty()
                if (hasActivity) {
                    val resumedStatus = pendingSwapStatus(synchronizedPendingSwap.copy(phase = PendingSwapPhase.IN_PROGRESS))
                    val resumed =
                        setSwapInProgress(
                            synchronizedPendingSwap,
                            status = resumedStatus,
                            phase = PendingSwapPhase.IN_PROGRESS,
                        )
                    clearPreparedSwap()
                    _swapState.value = SwapState.Idle
                    startSwapMonitor(resumed)
                    throw Exception("This SideSwap order is already active. Monitoring resumed.")
                }
                if (fromResume && nowMs() - synchronizedPendingSwap.createdAt > REVIEW_ORDER_VALIDITY_MS) {
                    clearPreparedSwap()
                    throw Exception("Prepared SideSwap order is stale after restart. Create a new review.")
                }
                val updated = synchronizedPendingSwap.copy(status = "")
                savePreparedSwap(updated)
                updated
            }
            SwapService.BOLTZ -> {
                val restored = restoreBoltzSwapIfNeeded(synchronizedPendingSwap)
                val validated =
                    synchronizePendingSwapWithBoltzDraft(
                        pendingSwap = restored,
                        context = if (fromResume) {
                            "validatePendingSwapForExecution(resume,restored)"
                        } else {
                            "validatePendingSwapForExecution(restored)"
                        },
                    )
                if (fromResume && nowMs() - validated.createdAt > REVIEW_ORDER_VALIDITY_MS) {
                    clearPreparedSwap()
                    throw Exception("Prepared Boltz order is stale after restart. Create a new review.")
                }
                val updated = validated.copy(status = "")
                savePreparedSwap(updated)
                updated
            }
        }
    }

    private fun ensureSideSwapStatusMatchesReview(
        pendingSwap: PendingSwapSession,
        depositAddress: String,
        receiveAddress: String,
    ) {
        val returnedDeposit = depositAddress.takeIf { it.isNotBlank() }
        if (returnedDeposit != null && returnedDeposit != pendingSwap.depositAddress) {
            throw Exception("SideSwap deposit address changed. Create a new review before funding.")
        }

        val reviewedReceive = pendingSwap.receiveAddress.orEmpty()
        val returnedReceive = receiveAddress.takeIf { it.isNotBlank() }
        if (returnedReceive != null && returnedReceive != reviewedReceive) {
            throw Exception("SideSwap receive address changed. Create a new review before funding.")
        }
    }

    private fun inferLightningSendKind(paymentInput: String): LiquidSendKind {
        return when {
            paymentInput.startsWith("lno", ignoreCase = true) -> LiquidSendKind.LIGHTNING_BOLT12
            paymentInput.startsWith("http://", ignoreCase = true) ||
                paymentInput.startsWith("https://", ignoreCase = true) -> LiquidSendKind.LIGHTNING_LNURL
            else -> LiquidSendKind.LIGHTNING_BOLT11
        }
    }

    private fun buildLightningSendPreview(
        paymentInput: String,
        kind: LiquidSendKind,
        amountSats: Long?,
        feeRate: Double,
        selectedUtxos: List<UtxoInfo>?,
        label: String?,
    ): LiquidSendPreview {
        val availableSats =
            selectedUtxos
                ?.takeIf { it.isNotEmpty() }
                ?.sumOf { it.amountSats.toLong() }
                ?: liquidState.value.balanceSats.coerceAtLeast(0L)
        return LiquidSendPreview(
            kind = kind,
            recipientDisplay = paymentInput,
            totalRecipientSats = amountSats,
            feeRate = feeRate,
            availableSats = availableSats,
            label = label?.trim()?.takeIf { it.isNotBlank() },
            selectedUtxoCount = selectedUtxos?.size ?: 0,
        )
    }

    private suspend fun resumePendingLightningWorkIfNeeded() {
        _pendingSubmarineSwap.value = null

        val pendingInvoiceSessions = repository.getPendingLightningInvoiceSessions()
        refreshPendingLightningInvoices()
        pendingInvoiceSessions.forEach { session ->
            if (BullBoltzBehavior.shouldResumePendingLightningInvoice(session)) {
                val restored =
                    runCatching { repository.restorePendingLightningInvoice(session.swapId) }
                        .onFailure {
                            logWarn(
                                "resumePendingLightningWorkIfNeeded failed to restore lightning invoice swapId=${session.swapId}",
                                Exception(it),
                            )
                        }.getOrNull()
                if (restored != null && activeLightningInvoiceSwapId == null) {
                    activeLightningInvoiceSwapId = restored.swapId
                    _lightningInvoiceState.value = LightningInvoiceState.Ready(
                        invoice = restored.invoice,
                        swapId = restored.swapId,
                        amountSats = restored.amountSats,
                    )
                } else if (activeLightningInvoiceSwapId == null) {
                    activeLightningInvoiceSwapId = session.swapId
                    _lightningInvoiceState.value = LightningInvoiceState.Ready(
                        invoice = session.invoice,
                        swapId = session.swapId,
                        amountSats = session.amountSats,
                    )
                }
                monitorLightningInvoice(session.swapId)
            } else {
                logDebug("resumePendingLightningWorkIfNeeded discarding persisted lightning invoice swapId=${session.swapId}")
                runCatching { repository.failLightningInvoice(session.swapId, clearPendingMetadata = true) }
            }
        }
        refreshPendingLightningInvoices()

        repository.getPendingLightningPaymentSession()?.let { session ->
            if (isLightningRefundDetected(session)) {
                failLightningSession(session.swapId)
                _sendState.value =
                    LiquidSendState.Failed(
                        error = "The Lightning payment failed and the refund has already returned to your Liquid wallet.",
                        preview = null,
                        refundAddress = session.refundAddress,
                    )
                return@let
            }
            _pendingSubmarineSwap.value = session
            val preview =
                buildLightningSendPreview(
                    paymentInput = session.paymentInput,
                    kind = inferLightningSendKind(session.paymentInput),
                    amountSats = session.requestedAmountSats,
                    feeRate = MIN_LIQUID_SEND_FEE_RATE,
                    selectedUtxos = null,
                    label = null,
                )
            if (session.phase == PendingLightningPaymentPhase.REFUNDING && session.fundingTxid != null) {
                val staleMs = System.currentTimeMillis() - session.createdAt
                if (staleMs >= STALE_REFUNDING_SESSION_MS) {
                    _sendState.value = LiquidSendState.Failed(
                        error = "A Lightning payment has been awaiting refund for over 24 hours. " +
                            "Use your Boltz rescue key to recover funds manually.",
                        preview = preview,
                        refundAddress = session.refundAddress,
                    )
                } else {
                    _sendState.value = LiquidSendState.Sending(
                        preview = preview,
                        status = "Lightning payment failed. Awaiting refund to your wallet...",
                        refundAddress = session.refundAddress,
                        detail = "Close anytime. The refund will arrive at the address below.",
                        canDismiss = true,
                    )
                    monitorPreparedLightningPayment(
                        backend = session.backend,
                        swapId = session.swapId,
                        paymentReference = session.paymentInput,
                        fundingTxid = session.fundingTxid,
                        preview = preview,
                    )
                }
            } else if (session.fundingTxid != null) {
                _sendState.value = LiquidSendState.Sending(
                    preview = preview,
                    status = "Funding broadcast. Waiting for Boltz payment...",
                    refundAddress = session.refundAddress,
                    detail = "Close anytime. Payment continues in background.",
                    canDismiss = true,
                )
                monitorPreparedLightningPayment(
                    backend = session.backend,
                    swapId = session.swapId,
                    paymentReference = session.paymentInput,
                    fundingTxid = session.fundingTxid,
                    preview = preview,
                )
            } else if (session.phase == PendingLightningPaymentPhase.FUNDING) {
                _sendState.value = LiquidSendState.Failed(
                    error = "A previous Lightning payment stopped during funding. Verify whether any L-BTC left the wallet before retrying.",
                    preview = preview,
                )
            } else if (session.phase == PendingLightningPaymentPhase.PREPARED) {
                runCatching {
                    repository.resolveLightningPaymentPreview(
                        paymentInput = session.paymentInput,
                        requestedAmountSats = session.requestedAmountSats,
                        kind = inferLightningSendKind(session.paymentInput),
                        feeRateSatPerVb = MIN_LIQUID_SEND_FEE_RATE,
                        selectedUtxos = null,
                        label = null,
                    )
                }.onSuccess { restoredPreview ->
                    _sendState.value = LiquidSendState.ReviewReady(restoredPreview)
                }.onFailure {
                    deleteLightningSession()
                }
            }
        }
    }

    private suspend fun resumePendingSwapIfNeeded() {
        val pendingSwaps = repository.getPendingSwapSessions()
        logDebug("resumePendingSwapIfNeeded pendingSessions=${pendingSwaps.size}")
        pendingSwaps
            .filter { BullBoltzBehavior.shouldDiscardPendingSwapOnRestart(it) }
            .forEach { pendingSwap ->
                logDebug("resumePendingSwapIfNeeded deleting uncommitted review swapId=${pendingSwap.swapId}")
                runCatching {
                    if (pendingSwap.service == SwapService.BOLTZ) {
                        repository.discardPreparedBoltzChainSwap(pendingSwap.swapId)
                    }
                }
                repository.deletePendingSwapSession(pendingSwap.swapId)
                if (pendingSwap.service == SwapService.BOLTZ) {
                    repository.deleteBoltzChainSwapDraftBySwapId(pendingSwap.swapId)
                }
            }

        pendingSwaps
            .filter { BullBoltzBehavior.shouldResumePendingSwapOnRestart(it) }
            .forEach { pendingSwap ->
                runCatching {
                    val synchronized =
                        synchronizePendingSwapWithBoltzDraft(
                            pendingSwap = pendingSwap,
                            context = "resumePendingSwapIfNeeded(active)",
                        )
                    logDebug(
                        "resumePendingSwapIfNeeded resuming active pending swap swapId=${synchronized.swapId} phase=${synchronized.phase}",
                    )
                    upsertPendingSwap(synchronized)
                    startSwapMonitor(synchronized)
                }.onFailure {
                    logWarn(
                        "resumePendingSwapIfNeeded active restore failed swapId=${pendingSwap.swapId}",
                        Exception(it),
                    )
                    repository.deletePendingSwapSession(pendingSwap.swapId)
                }
            }
    }

    private fun startBackgroundSync() {
        if (backgroundSyncJob?.isActive == true) return
        backgroundSyncJob?.cancel()
        backgroundSyncJob = launchLiquidJob background@{
            while (isActive) {
                delay(BACKGROUND_SYNC_INTERVAL_MS)
                if (!_isLayer2Enabled.value || !liquidContextActive || !isLiquidConnected.value) {
                    continue
                }
                try {
                    repository.sync()
                    _liquidBlockHeight.value = repository.getServerBlockHeight()
                } catch (e: Exception) {
                    SecureLog.w(TAG, "Background Liquid sync failed: ${e.message}", e)
                }
            }
        }
    }

    private fun stopBackgroundSync() {
        backgroundSyncJob?.cancel()
        backgroundSyncJob = null
    }

    /**
     * Lightweight ping loop against the Liquid Electrum server.
     *
     * Adaptive cadence: 60s while pings succeed, dropping to 15s after the
     * first failed ping so we confirm a drop within ~90s of the first failure
     * instead of ~3min, while still requiring [HEARTBEAT_MAX_FAILURES]
     * consecutive failures to absorb transient Tor/cellular blips.
     */
    private fun startHeartbeat() {
        if (heartbeatJob?.isActive == true) return
        heartbeatJob?.cancel()
        var consecutiveFailures = 0
        heartbeatJob = launchLiquidJob {
            while (isActive) {
                val interval =
                    if (consecutiveFailures == 0) {
                        HEARTBEAT_INTERVAL_MS
                    } else {
                        HEARTBEAT_RETRY_INTERVAL_MS
                    }
                delay(interval)
                if (!_isLayer2Enabled.value || !liquidContextActive || !isLiquidConnected.value) {
                    break
                }

                val alive =
                    withTimeoutOrNull(HEARTBEAT_PING_TIMEOUT_MS) {
                        withContext(Dispatchers.IO) {
                            repository.pingServer()
                        }
                    } ?: false

                if (alive) {
                    consecutiveFailures = 0
                    continue
                }

                consecutiveFailures++
                if (consecutiveFailures < HEARTBEAT_MAX_FAILURES) {
                    continue
                }

                if (liquidState.value.isSyncing || liquidFullSyncJob?.isActive == true) {
                    consecutiveFailures = 0
                    continue
                }

                handleLiquidConnectionLost("Liquid server connection lost")
                break
            }
        }
    }

    private fun stopHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = null
    }

    private fun stopReconnectRetry() {
        reconnectRetryJob?.cancel()
        reconnectRetryJob = null
    }

    private fun startReconnectRetry() {
        if (reconnectRetryJob?.isActive == true) return
        if (repository.isUserDisconnected()) return
        if (!_isLayer2Enabled.value || !liquidContextActive) return
        if (!secureStorage.hasUserSelectedLiquidServer()) return

        reconnectRetryJob =
            launchLiquidJob retry@{
                var attempt = 0
                while (isActive && attempt < RECONNECT_MAX_ATTEMPTS) {
                    val delayMs =
                        (RECONNECT_BASE_DELAY_MS shl attempt)
                            .coerceAtMost(RECONNECT_MAX_DELAY_MS)
                    delay(delayMs)

                    if (isLiquidConnected.value) return@retry
                    if (repository.isUserDisconnected()) return@retry
                    if (!_isLayer2Enabled.value || !liquidContextActive) return@retry
                    if (_isLiquidConnecting.value || connectionJob?.isActive == true) continue

                    attempt++
                    if (BuildConfig.DEBUG) {
                        Log.d(TAG, "Liquid reconnect retry $attempt/$RECONNECT_MAX_ATTEMPTS")
                    }

                    runCatching {
                        connectToActiveServer()
                    }.onFailure { error ->
                        SecureLog.w(TAG, "Liquid reconnect retry $attempt failed: ${error.message}", error)
                    }

                    if (isLiquidConnected.value) return@retry
                }
            }
    }

    /**
     * Silent reconnect after returning from background with a dead Liquid connection.
     */
    private suspend fun reconnectOnForeground() {
        if (!_isLayer2Enabled.value || !liquidContextActive) return
        if (connectionJob?.isActive == true) return
        stopReconnectRetry()
        try {
            connectToActiveServer()
        } catch (e: Exception) {
            SecureLog.w(TAG, "Liquid reconnect on foreground failed: ${e.message}", e)
        }
    }

    private fun launchRealtimeSubscriptions(reason: String = "startup") {
        launchLiquidJob {
            try {
                when (repository.startRealTimeSubscriptions()) {
                    LiquidRepository.SubscriptionResult.SYNCED,
                    LiquidRepository.SubscriptionResult.NO_CHANGES,
                    -> {
                        if (BuildConfig.DEBUG) {
                            Log.d(TAG, "Liquid realtime subscriptions active ($reason)")
                        }
                        _liquidBlockHeight.value = repository.getServerBlockHeight()
                    }
                    LiquidRepository.SubscriptionResult.FAILED -> {
                        if (BuildConfig.DEBUG) {
                            Log.w(TAG, "Liquid realtime subscriptions failed ($reason); falling back to one-shot sync")
                        }
                        repository.sync()
                        if (BuildConfig.DEBUG) {
                            Log.d(TAG, "Liquid one-shot sync fallback completed after subscription failure ($reason)")
                        }
                        _liquidBlockHeight.value = repository.getServerBlockHeight()
                    }
                }
            } catch (e: Exception) {
                SecureLog.w(TAG, "Liquid real-time subscription startup failed: ${e.message}", e)
                runCatching {
                    repository.sync()
                    _liquidBlockHeight.value = repository.getServerBlockHeight()
                }
            } finally {
                _initialLiquidSyncComplete.value = true
            }
        }
    }

    private suspend fun handleLiquidSubscriptionLost() {
        if (repository.isUserDisconnected()) return
        if (_isLiquidConnecting.value || connectionJob?.isActive == true) return
        if (!isLiquidConnected.value) return
        if (liquidState.value.isSyncing || liquidFullSyncJob?.isActive == true) return

        val alive =
            withTimeoutOrNull(HEARTBEAT_PING_TIMEOUT_MS) {
                withContext(Dispatchers.IO) {
                    repository.pingServer()
                }
            } ?: false

        if (alive) {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "Liquid server is alive after subscription loss; restarting subscriptions only")
            }
            startBackgroundSync()
            startHeartbeat()
            launchRealtimeSubscriptions(reason = "subscription_recovery")
            return
        }

        handleLiquidConnectionLost("Liquid server connection lost")
    }

    private suspend fun handleLiquidConnectionLost(message: String) {
        if (repository.isUserDisconnected()) return
        if (_isLiquidConnecting.value || connectionJob?.isActive == true) return
        if (!isLiquidConnected.value) return
        if (liquidState.value.isSyncing || liquidFullSyncJob?.isActive == true) return

        val failedServerId = secureStorage.getActiveLiquidServerId()
        if (BuildConfig.DEBUG) {
            Log.w(TAG, "Disconnecting Liquid after confirmed connection loss; auto-switch will be attempted if available")
        }
        stopHeartbeat()
        stopBackgroundSync()
        stopReconnectRetry()
        _initialLiquidSyncComplete.value = false
        _liquidBlockHeight.value = null
        repository.disconnect()
        _liquidConnectionError.value = message
        _events.emit(LiquidEvent.Error(message))
        failedServerId?.let(::tryAutoSwitchFrom)
        connectionJob?.join()
        if (isLiquidConnected.value) return
        startReconnectRetry()
    }

    private fun requireLiquidAvailable() {
        check(_isLayer2Enabled.value && liquidContextActive) {
            "Liquid is disabled for the active wallet"
        }
    }

    // ════════════════════════════════════════════
    // Layer 2 Toggle
    // ════════════════════════════════════════════

    fun setLayer2Enabled(enabled: Boolean) {
        secureStorage.setLayer2Enabled(enabled)
        _isLayer2Enabled.value = enabled
        if (!enabled) {
            cancelAutomaticBoltzPrewarm()
            cancelActiveLayerPersistence()
            _activeLayer.value = WalletLayer.LAYER1
            cancelManagedJobs()
            viewModelScope.launch(Dispatchers.IO) {
                repository.unloadWallet()
            }
            resetLiquidUiState()
            stopHeartbeat()
            stopBackgroundSync()
            stopSideSwapMonitor()
        } else if (liquidContextActive) {
            // The app-level wallet lifecycle effect will load/connect Liquid
            // for the active wallet once Layer 2 is enabled.
            refreshSideSwapWalletInfoMonitoring()
        }
    }

    fun setDenomination(denomination: String) {
        secureStorage.setLayer2Denomination(denomination)
        _denominationState.value = denomination
    }

    fun setLiquidContextActive(active: Boolean) {
        liquidContextActive = active
        if (active) {
            if (_isLayer2Enabled.value) {
                refreshSideSwapWalletInfoMonitoring()
                if (isLiquidConnected.value) {
                    startBackgroundSync()
                    startHeartbeat()
                    launchRealtimeSubscriptions(reason = "context_resume")
                    if (isBoltzEnabled()) {
                        scheduleAutomaticBoltzPrewarm("context_active")
                    }
                } else if (!repository.isUserDisconnected() && !_isLiquidConnecting.value) {
                    launchLiquidJob {
                        reconnectOnForeground()
                    }
                }
            }
            return
        }

        cancelAutomaticBoltzPrewarm()
        cancelActiveLayerPersistence()
        _activeLayer.value = WalletLayer.LAYER1
        cancelManagedJobs()
        resetLiquidUiState()
        stopHeartbeat()
        stopReconnectRetry()
        stopBackgroundSync()
        stopSideSwapMonitor()
        stopAllSwapMonitors()
    }

    /**
     * Subscribe to SideSwap hot wallet balances and dynamic min amounts.
     * Connects the SideSwap WebSocket and subscribes to all four values:
     * PegInMinAmount, PegInWalletBalance, PegOutMinAmount, PegOutWalletBalance.
     */
    private fun subscribeSideSwapWalletInfo() {
        sideSwapMonitorJob?.cancel()
        sideSwapMonitorJob = launchLiquidJob {
            while (isActive && shouldMonitorSideSwapWalletInfo()) {
                if (!isSideSwapEnabled()) {
                    repository.sideSwapClient.disconnect()
                    delay(10_000)
                    continue
                }
                try {
                    if (isSideSwapTor() && !torManager.isReady()) {
                        torManager.start()
                        if (!torManager.awaitReady(TOR_BOOTSTRAP_TIMEOUT_MS)) {
                            throw Exception("Tor connection timed out")
                        }
                        delay(TOR_POST_BOOTSTRAP_DELAY_MS)
                    }
                    if (!repository.sideSwapClient.isConnected.value) {
                        repository.sideSwapClient.connect()
                        delay(1_000)
                        repository.sideSwapClient.subscribeWalletInfo()
                    }
                    delay(10_000)
                } catch (e: Exception) {
                    SecureLog.w(TAG, "Failed to subscribe to SideSwap wallet info: ${e.message}", e)
                    delay(5_000)
                }
            }
        }
    }

    private fun shouldMonitorSideSwapWalletInfo(): Boolean =
        _isLayer2Enabled.value &&
            liquidContextActive &&
            swapScreenActive &&
            _preferredSwapService.value == SwapService.SIDESWAP &&
            isSideSwapEnabled()

    private fun refreshSideSwapWalletInfoMonitoring() {
        if (shouldMonitorSideSwapWalletInfo()) {
            subscribeSideSwapWalletInfo()
        } else {
            stopSideSwapMonitor()
        }
    }

    // ════════════════════════════════════════════
    // Layer Switching
    // ════════════════════════════════════════════

    fun setActiveLayer(layer: WalletLayer, walletId: String? = null) {
        if (_activeLayer.value == layer) return
        _activeLayer.value = layer
        if (walletId != null) {
            persistActiveLayer(walletId, layer)
        } else {
            cancelActiveLayerPersistence()
        }
    }

    fun loadActiveLayer(walletId: String) {
        cancelActiveLayerPersistence()
        val saved = secureStorage.getActiveLayer(walletId)
        _activeLayer.value = try {
            WalletLayer.valueOf(saved)
        } catch (_: Exception) {
            WalletLayer.LAYER1
        }
    }

    // ════════════════════════════════════════════
    // Wallet Lifecycle
    // ════════════════════════════════════════════

    fun loadLiquidWallet(walletId: String, mnemonic: String? = null) {
        loadActiveLayer(walletId)
        _pendingSubmarineSwap.value = secureStorage.getPendingLightningPaymentSession(walletId)
        if (secureStorage.needsLiquidFullSync(walletId)) {
            showPendingFullSyncProgress("Loading Liquid wallet...")
        } else {
            clearPendingFullSyncProgress()
        }
        if (pendingWalletLoadId == walletId && walletLifecycleJob?.isActive == true) {
            return
        }

        val visibleWalletId = pendingWalletLoadId ?: loadedWalletId.value
        val shouldPreloadCachedState = visibleWalletId != null && visibleWalletId != walletId
        if (shouldPreloadCachedState) {
            resetLiquidUiState()
            clearDerivedLiquidSnapshots()
            repository.clearWalletDisplayState()
        }

        _initialLiquidSyncComplete.value = false

        val previousLifecycleJob = walletLifecycleJob
        pendingWalletLoadId = walletId
        walletLifecycleJob = launchLiquidJob {
            previousLifecycleJob?.cancel()
            try {
                if (previousLifecycleJob == null && repository.isWalletLoaded(walletId)) {
                    handleLoadedWalletResume(walletId)
                } else {
                    handleWalletSwitch(
                        walletId = walletId,
                        mnemonic = mnemonic,
                        shouldPreloadCachedState = shouldPreloadCachedState,
                    )
                }
            } catch (e: Exception) {
                clearPendingFullSyncProgress()
                logError("Failed to load Liquid wallet", e)
            } finally {
                if (pendingWalletLoadId == walletId) {
                    pendingWalletLoadId = null
                }
                if (walletLifecycleJob?.isActive != true) {
                    walletLifecycleJob = null
                }
            }
        }
    }

    private suspend fun handleLoadedWalletResume(walletId: String) {
        if (repository.isUserDisconnected()) {
            clearPendingFullSyncProgress()
            _initialLiquidSyncComplete.value = true
        } else if (!isLiquidConnected.value && !isLiquidConnecting.value) {
            if (secureStorage.needsLiquidFullSync(walletId)) {
                showPendingFullSyncProgress("Connecting to Liquid server...")
            }
            connectToActiveServer()
        } else {
            startBackgroundSync()
            startHeartbeat()
            runCatching {
                repository.sync()
                _liquidBlockHeight.value = repository.getServerBlockHeight()
            }.onFailure { error ->
                SecureLog.w(TAG, "Liquid sync after wallet reload failed: ${error.message}", error)
            }
            launchRealtimeSubscriptions(reason = "wallet_reload")
        }
    }

    private suspend fun handleWalletSwitch(
        walletId: String,
        mnemonic: String?,
        shouldPreloadCachedState: Boolean,
    ) {
        cancelAutomaticBoltzPrewarm()
        lastLiquidConnectionReadyAtMs = nowMs()
        val keepServerConnection =
            !repository.isUserDisconnected() &&
                isLiquidConnected.value &&
                !isLiquidConnecting.value
        connectionJob?.cancel()
        connectionJob = null
        stopBackgroundSync()
        stopHeartbeat()
        clearPreparedSwap()
        _pendingSwaps.value = emptyList()
        stopAllSwapMonitors()

        val preloadStartedAt = System.currentTimeMillis()
        val preloaded =
            if (shouldPreloadCachedState) {
                withContext(Dispatchers.IO) {
                    repository.preloadCachedWalletState(walletId)
                }
            } else {
                false
            }
        if (BuildConfig.DEBUG) {
            Log.d(
                TAG,
                "Liquid startup profile: preloadCachedState enabled=$shouldPreloadCachedState " +
                    "result=$preloaded elapsedMs=${System.currentTimeMillis() - preloadStartedAt}",
            )
        }

        val switchStartedAt = System.currentTimeMillis()
        repository.switchWallet(
            walletId = walletId,
            mnemonic = mnemonic,
            preserveConnection = keepServerConnection,
            preloaded = preloaded,
        )
        if (BuildConfig.DEBUG) {
            Log.d(
                TAG,
                "Liquid startup profile: switchWallet preserveConnection=$keepServerConnection " +
                    "preloaded=$preloaded elapsedMs=${System.currentTimeMillis() - switchStartedAt}",
            )
        }
        if (keepServerConnection) {
            loadedWalletId.value?.let { loadedId ->
                runRequestedLiquidFullSyncIfNeeded(loadedId)
            }
            runCatching {
                repository.sync()
                _liquidBlockHeight.value = repository.getServerBlockHeight()
            }.onFailure { e ->
                SecureLog.w(TAG, "Liquid sync after wallet switch failed: ${e.message}", e)
            }
            startBackgroundSync()
            startHeartbeat()
            launchRealtimeSubscriptions(reason = "wallet_switch")
            preparedSwapSession = null
            resumePendingSwapIfNeeded()
            resumePendingLightningWorkIfNeeded()
            lastLiquidConnectionReadyAtMs = nowMs()
        } else if (!repository.isUserDisconnected()) {
            connectToActiveServer()
        } else {
            _initialLiquidSyncComplete.value = true
        }
    }

    fun unloadLiquidWallet() {
        cancelAutomaticBoltzPrewarm()
        cancelActiveLayerPersistence()
        clearPreparedSwap()
        _pendingSwaps.value = emptyList()
        clearPendingFullSyncProgress()
        cancelManagedJobs()
        stopBackgroundSync()
        stopSideSwapMonitor()
        stopAllSwapMonitors()
        resetLiquidUiState()
        _initialLiquidSyncComplete.value = false
        _activeLayer.value = WalletLayer.LAYER1
        viewModelScope.launch(Dispatchers.IO) {
            repository.unloadWallet()
        }
    }

    suspend fun deleteWalletData(walletId: String) {
        cancelActiveLayerPersistence()
        clearPreparedSwap()
        _pendingSwaps.value = emptyList()
        cancelManagedJobs()
        stopBackgroundSync()
        stopSideSwapMonitor()
        stopAllSwapMonitors()

        val isLoadedWallet = loadedWalletId.value == walletId
        if (isLoadedWallet) {
            resetLiquidUiState()
            _activeLayer.value = WalletLayer.LAYER1
            withContext(Dispatchers.IO) {
                try {
                    repository.unloadWallet()
                } catch (_: Exception) { /* best-effort cleanup */ }
                try {
                    repository.sideSwapClient.unsubscribeWalletInfo()
                } catch (_: Exception) { /* best-effort cleanup */ }
                try {
                    repository.sideSwapClient.disconnect()
                } catch (_: Exception) { /* best-effort cleanup */ }
            }
        }

        repository.deleteWalletDatabase(walletId)
        _liquidEnabledWallets.value = _liquidEnabledWallets.value.toMutableMap().apply { remove(walletId) }
    }

    suspend fun prepareForFullWipe() {
        cancelAutomaticBoltzPrewarm()
        cancelActiveLayerPersistence()
        clearPreparedSwap()
        _pendingSwaps.value = emptyList()
        cancelManagedJobs()
        stopBackgroundSync()
        stopSideSwapMonitor()
        stopAllSwapMonitors()
        resetLiquidUiState()
        _activeLayer.value = WalletLayer.LAYER1
        withContext(Dispatchers.IO) {
            try {
                repository.unloadWallet()
            } catch (_: Exception) { /* best-effort cleanup */ }
            try {
                repository.sideSwapClient.unsubscribeWalletInfo()
            } catch (_: Exception) { /* best-effort cleanup */ }
            try {
                repository.sideSwapClient.disconnect()
            } catch (_: Exception) { /* best-effort cleanup */ }
        }
    }

    fun isLiquidEnabledForWallet(walletId: String): Boolean {
        // Read from reactive map if populated, otherwise fall back to storage
        return _liquidEnabledWallets.value[walletId]
            ?: secureStorage.isLiquidEnabledForWallet(walletId)
    }

    fun setLiquidEnabledForWallet(walletId: String, enabled: Boolean) {
        secureStorage.setLiquidEnabledForWallet(walletId, enabled)
        _liquidEnabledWallets.value = _liquidEnabledWallets.value.toMutableMap().apply {
            put(walletId, enabled)
        }
    }

    fun getLiquidGapLimit(walletId: String): Int =
        _liquidGapLimits.value[walletId]
            ?: secureStorage.getLiquidGapLimit(walletId)

    fun setLiquidGapLimit(walletId: String, gapLimit: Int) {
        secureStorage.setLiquidGapLimit(walletId, gapLimit)
        _liquidGapLimits.value = _liquidGapLimits.value.toMutableMap().apply {
            put(walletId, gapLimit)
        }
    }

    fun isLiquidWatchOnly(walletId: String): Boolean =
        secureStorage.isLiquidWatchOnly(walletId)

    fun setLiquidWatchOnly(walletId: String, watchOnly: Boolean) {
        secureStorage.setLiquidWatchOnly(walletId, watchOnly)
    }

    // ════════════════════════════════════════════
    // Server Connection (mirrors WalletViewModel pattern)
    // ════════════════════════════════════════════

    /**
     * Connect to a specific Liquid Electrum server by ID.
     * Cancels any existing connection, sets the server as active, and connects.
     */
    fun connectToLiquidServer(serverId: String) {
        repository.setUserDisconnected(false)
        connectToLiquidServerInternal(serverId, isAutoSwitch = false)
    }

    private fun connectToLiquidServerInternal(serverId: String, isAutoSwitch: Boolean) {
        val previousJob = connectionJob
        if (!isAutoSwitch) autoSwitchVisited.clear()
        stopBackgroundSync()
        stopHeartbeat()
        stopReconnectRetry()
        _isLiquidConnecting.value = true
        _liquidConnectionError.value = null
        val config =
            secureStorage.getLiquidServer(serverId)
                ?: run {
                    _isLiquidConnecting.value = false
                    _liquidConnectionError.value = "Server not found"
                    return
                }
        val serverRequiresTor = config.useTor || config.isOnionAddress()
        val previousServerUsedTor = shouldUseLiquidTor()
        val otherNeedsTor = shouldKeepLayer2ExternalTorRunning()
        val shouldKeepTorRunning = shouldKeepTorRunningForLiquid(serverRequiresTor || otherNeedsTor)
        connectionJob = launchLiquidJob {
            previousJob?.cancelAndJoin()
            try {
                disconnectForLiquidServerSwitch(
                    previousServerUsedTor = previousServerUsedTor,
                    shouldKeepTorRunning = shouldKeepTorRunning,
                )
                _isLiquidTorEnabled.value = serverRequiresTor
                if (!isAutoSwitch) {
                    secureStorage.setUserSelectedLiquidServer(true)
                }
                secureStorage.setActiveLiquidServerId(serverId)
                loadLiquidServers()
                connectConfig(config)
                autoSwitchVisited.clear()
            } catch (e: Exception) {
                if (handleCertificateException(e, serverId)) {
                    return@launchLiquidJob
                }
                logError("Failed to connect to Liquid server: $serverId", e)
                _isLiquidConnecting.value = false
                _liquidConnectionError.value = safeLiquidErrorMessage(e, "Connection failed")
                tryAutoSwitchFrom(serverId)
            }
        }
    }

    private suspend fun disconnectForLiquidServerSwitch(
        previousServerUsedTor: Boolean,
        shouldKeepTorRunning: Boolean,
    ) {
        withContext(Dispatchers.IO) {
            repository.disconnect()
        }

        if (previousServerUsedTor && !shouldKeepTorRunning) {
            torManager.stop()
        }
    }

    /** Disconnect from the current Liquid Electrum server */
    fun disconnectLiquidServer() {
        if (_isLiquidConnecting.value) {
            cancelLiquidConnection()
            return
        }
        stopHeartbeat()
        stopReconnectRetry()
        stopBackgroundSync()
        _liquidConnectionError.value = null
        _liquidBlockHeight.value = null
        viewModelScope.launch(Dispatchers.IO) {
            repository.setUserDisconnected(true)
            repository.disconnect()
        }
    }

    /** Cancel an in-progress connection attempt */
    fun cancelLiquidConnection() {
        stopHeartbeat()
        stopReconnectRetry()
        stopBackgroundSync()
        val jobToCancel = connectionJob
        connectionJob = null
        _isLiquidConnecting.value = false
        _liquidConnectionError.value = null
        _liquidBlockHeight.value = null
        viewModelScope.launch(Dispatchers.IO) {
            repository.setUserDisconnected(true)
            jobToCancel?.cancelAndJoin()
            repository.disconnect()
        }
    }

    // ════════════════════════════════════════════
    // Server Management
    // ════════════════════════════════════════════

    private fun seedDefaultServers() {
        if (secureStorage.isLiquidDefaultServersSeeded()) return
        LiquidRepository.DEFAULT_SERVERS.forEach { server ->
            secureStorage.saveLiquidServer(server)
        }
        secureStorage.setLiquidDefaultServersSeeded()
    }

    private fun migrateLegacyLiquidServerSelection() {
        if (secureStorage.hasUserSelectedLiquidServer()) return

        val activeId = secureStorage.getActiveLiquidServerId() ?: return
        val shouldClearImplicitDefault = activeId == LiquidRepository.DEFAULT_SERVERS.firstOrNull()?.id

        if (shouldClearImplicitDefault) {
            secureStorage.setActiveLiquidServerId(null)
        } else {
            secureStorage.setUserSelectedLiquidServer(true)
        }
    }

    private fun loadLiquidServers() {
        _liquidServersState.value = LiquidServersState(
            servers = secureStorage.getAllLiquidServers(),
            activeServerId = secureStorage.getActiveLiquidServerId(),
        )
        _isLiquidTorEnabled.value = activeLiquidServerRequiresTor()
    }

    /**
     * Save a server (add or update). Returns the config with its ID.
     * Mirrors ElectrumConfigScreen's onSaveServer pattern.
     */
    fun saveLiquidServer(config: LiquidElectrumConfig): LiquidElectrumConfig {
        secureStorage.saveLiquidServer(config)
        loadLiquidServers()
        return config
    }

    fun removeLiquidServer(id: String) {
        secureStorage.removeLiquidServer(id)
        loadLiquidServers()
    }

    fun reorderLiquidServers(orderedIds: List<String>) {
        secureStorage.reorderLiquidServerIds(orderedIds)
        loadLiquidServers()
    }

    // ════════════════════════════════════════════
    // Tor
    // ════════════════════════════════════════════

    fun setBoltzApiSource(source: String) {
        repository.setBoltzApiSource(source)
        _boltzApiSource.value = source
        if (source == SecureStorage.BOLTZ_API_DISABLED) {
            _lightningInvoiceLimits.value = null
            _swapLimits.value =
                _swapLimits.value.toMutableMap().apply {
                    remove(SwapService.BOLTZ)
                }
        }
        syncForegroundConnectivityPolicy()
        if (shouldKeepLayer2TorRunning()) {
            torManager.start()
        } else {
            torManager.stop()
        }
        launchLiquidJob {
            runCatching { repository.invalidateBoltzSessionState() }
            if (_isLayer2Enabled.value && liquidContextActive && isBoltzEnabled()) {
                scheduleAutomaticBoltzPrewarm("source_changed")
            }
        }
    }

    fun isBoltzEnabled(): Boolean = _boltzApiSource.value != SecureStorage.BOLTZ_API_DISABLED

    fun setLiquidExplorer(explorer: String) {
        repository.setLiquidExplorer(explorer)
        _liquidExplorer.value = explorer
        repository.refreshCachedWalletState()
    }

    fun getCustomLiquidExplorerUrl(): String = repository.getCustomLiquidExplorerUrl()

    fun setCustomLiquidExplorerUrl(url: String) {
        repository.setCustomLiquidExplorerUrl(url)
        if (_liquidExplorer.value == SecureStorage.LIQUID_EXPLORER_CUSTOM) {
            repository.refreshCachedWalletState()
        }
    }

    fun getLiquidExplorerUrl(): String = repository.getLiquidExplorerUrl()

    fun setSideSwapApiSource(source: String) {
        repository.setSideSwapApiSource(source)
        _sideSwapApiSource.value = source
        if (source == SecureStorage.SIDESWAP_API_DISABLED) {
            repository.sideSwapClient.disconnect()
            _swapLimits.value =
                _swapLimits.value.toMutableMap().apply {
                    remove(SwapService.SIDESWAP)
                }
        } else if (_isLayer2Enabled.value && liquidContextActive) {
            repository.sideSwapClient.disconnect()
        }
        syncForegroundConnectivityPolicy()
        if (shouldKeepLayer2TorRunning()) {
            torManager.start()
        } else {
            torManager.stop()
        }
        refreshSideSwapWalletInfoMonitoring()
    }

    fun isSideSwapEnabled(): Boolean = _sideSwapApiSource.value != SecureStorage.SIDESWAP_API_DISABLED

    fun setPreferredSwapService(service: SwapService) {
        repository.setPreferredSwapService(service)
        _preferredSwapService.value = service
        refreshSideSwapWalletInfoMonitoring()
    }

    fun setSwapScreenActive(active: Boolean) {
        if (swapScreenActive == active) return
        swapScreenActive = active
        if (active) {
            refreshBoltzRescueMnemonic()
        }
        refreshSideSwapWalletInfoMonitoring()
    }

    private fun shouldKeepLayer2TorRunning(): Boolean {
        return shouldKeepTorRunningForLiquid(shouldUseLiquidTor() || shouldKeepLayer2ExternalTorRunning())
    }

    private fun shouldKeepLayer2ExternalTorRunning(): Boolean {
        return _isLayer2Enabled.value &&
            (
                _boltzApiSource.value == SecureStorage.BOLTZ_API_TOR ||
                    isSideSwapTor()
            )
    }

    private fun isSideSwapTor(): Boolean = _sideSwapApiSource.value == SecureStorage.SIDESWAP_API_TOR

    fun setLiquidAutoSwitchServer(enabled: Boolean) {
        secureStorage.setLiquidAutoSwitchEnabled(enabled)
        _liquidAutoSwitchServer.value = enabled
    }

    fun getPendingLightningPaymentSessionForTxid(txid: String): PendingLightningPaymentSession? {
        return repository.getPendingLightningPaymentSession()
            ?.takeIf { session ->
                session.fundingTxid.equals(txid, ignoreCase = true) &&
                    session.swapId.isNotBlank() &&
                    session.paymentInput.isNotBlank()
            }
    }

    fun reloadRestoredSettings() {
        val layer2Enabled = secureStorage.isLayer2Enabled()
        val denomination = secureStorage.getLayer2Denomination()
        val boltzSource = secureStorage.getBoltzApiSource()
        val sideSwapSource = secureStorage.getSideSwapApiSource()
        val preferredService = repository.getPreferredSwapService()
        val autoSwitchEnabled = secureStorage.isLiquidAutoSwitchEnabled()
        val explorer = secureStorage.getLiquidExplorer()

        _denominationState.value = denomination

        if (_isLayer2Enabled.value != layer2Enabled) {
            setLayer2Enabled(layer2Enabled)
        } else {
            _isLayer2Enabled.value = layer2Enabled
        }

        _isLiquidTorEnabled.value = activeLiquidServerRequiresTor()

        if (_boltzApiSource.value != boltzSource) {
            setBoltzApiSource(boltzSource)
        } else {
            _boltzApiSource.value = boltzSource
        }

        if (_sideSwapApiSource.value != sideSwapSource) {
            setSideSwapApiSource(sideSwapSource)
        } else {
            _sideSwapApiSource.value = sideSwapSource
        }

        if (_preferredSwapService.value != preferredService) {
            setPreferredSwapService(preferredService)
        } else {
            _preferredSwapService.value = preferredService
        }

        if (_liquidAutoSwitchServer.value != autoSwitchEnabled) {
            setLiquidAutoSwitchServer(autoSwitchEnabled)
        } else {
            _liquidAutoSwitchServer.value = autoSwitchEnabled
        }

        _liquidExplorer.value = explorer
        loadLiquidServers()

        syncForegroundConnectivityPolicy()
        if (shouldKeepLayer2TorRunning()) {
            torManager.start()
        } else {
            torManager.stop()
        }

        _settingsRefreshVersion.value += 1
    }

    private fun activeLiquidServerRequiresTor(): Boolean {
        val activeId = secureStorage.getActiveLiquidServerId() ?: return false
        val config = secureStorage.getLiquidServer(activeId) ?: return false
        return config.useTor || config.isOnionAddress()
    }

    private fun shouldUseLiquidTor(): Boolean = activeLiquidServerRequiresTor()

    /** Private connect helper used by wallet init/load (no connection job management) */
    private suspend fun connectToActiveServer() {
        connectionJob?.cancelAndJoin()
        connectionJob = null
        stopBackgroundSync()
        stopHeartbeat()
        val walletId = loadedWalletId.value
        if (walletId != null && secureStorage.needsLiquidFullSync(walletId)) {
            showPendingFullSyncProgress("Connecting to Liquid server...")
        }
        if (!secureStorage.hasUserSelectedLiquidServer()) {
            clearPendingFullSyncProgress()
            return
        }
        val activeId = secureStorage.getActiveLiquidServerId() ?: run {
            clearPendingFullSyncProgress()
            return
        }
        val config = secureStorage.getLiquidServer(activeId) ?: run {
            clearPendingFullSyncProgress()
            return
        }
        _isLiquidTorEnabled.value = config.useTor || config.isOnionAddress()
        _isLiquidConnecting.value = true
        _liquidConnectionError.value = null
        try {
            connectConfig(config)
            autoSwitchVisited.clear()
        } catch (e: Exception) {
            if (handleCertificateException(e, activeId)) {
                return
            }
            clearPendingFullSyncProgress()
            logError("Failed to connect to Liquid server", e)
            _isLiquidConnecting.value = false
            _liquidConnectionError.value = safeLiquidErrorMessage(e, "Connection failed")
            _events.emit(LiquidEvent.Error("Connection failed"))
            tryAutoSwitchFrom(activeId)
        }
    }

    private fun handleCertificateException(exception: Exception, serverId: String): Boolean {
        val firstUse = exception.findCause<CertificateFirstUseException>()
        val mismatch = exception.findCause<CertificateMismatchException>()
        when {
            firstUse != null -> {
                _isLiquidConnecting.value = false
                _liquidConnectionError.value = null
                _certDialogState.value =
                    CertDialogState(
                        certInfo = firstUse.certInfo,
                        isFirstUse = true,
                        pendingServerId = serverId,
                    )
                return true
            }

            mismatch != null -> {
                _isLiquidConnecting.value = false
                _liquidConnectionError.value = null
                _certDialogState.value =
                    CertDialogState(
                        certInfo = mismatch.certInfo,
                        isFirstUse = false,
                        oldFingerprint = mismatch.storedFingerprint,
                        pendingServerId = serverId,
                    )
                return true
            }
        }
        return false
    }

    fun acceptCertificate() {
        val state = _certDialogState.value ?: return
        val certInfo = state.certInfo
        repository.acceptServerCertificate(certInfo.host, certInfo.port, certInfo.sha256Fingerprint)
        _certDialogState.value = null
        state.pendingServerId?.let(::connectToLiquidServer)
    }

    fun rejectCertificate() {
        _certDialogState.value = null
        _isLiquidConnecting.value = false
        _liquidConnectionError.value = null
    }

    private suspend fun connectConfig(config: LiquidElectrumConfig) {
        val needsTor = shouldUseLiquidTor() || config.isOnionAddress() || config.useTor

        if (needsTor && !torManager.isReady()) {
            torManager.start()
            if (!torManager.awaitReady(TOR_BOOTSTRAP_TIMEOUT_MS)) {
                val msg = if (torState.value.status == TorStatus.ERROR) {
                    "Tor failed to start: ${torState.value.statusMessage}"
                } else {
                    "Tor connection timed out"
                }
                throw Exception(msg)
            }
            delay(TOR_POST_BOOTSTRAP_DELAY_MS)
        }

        val timeoutMs = if (needsTor) CONNECTION_TIMEOUT_TOR_MS else CONNECTION_TIMEOUT_CLEARNET_MS
        val result = withTimeoutOrNull(timeoutMs) {
            repository.connectToElectrum(config)
        }

        if (result == null) {
            launchDisconnect()
            throw Exception("Connection timed out")
        }

        _isLiquidConnecting.value = false
        lastLiquidConnectionReadyAtMs = nowMs()
        loadedWalletId.value?.let { walletId ->
            runRequestedLiquidFullSyncIfNeeded(walletId)
        }
        if (isBoltzEnabled() && _isLayer2Enabled.value) {
            val trace =
                BoltzTraceContext(
                    operation = "requestBoltzWarmupAtAppStart",
                    viaTor = boltzApiSource.value == SecureStorage.BOLTZ_API_TOR,
                    source = "viewmodel",
                )
            logBoltzTrace(
                if (boltzAppStartWarmupPending) "launch_from_connect" else "launch_after_connect",
                trace,
            )
            boltzAppStartWarmupPending = false
            scheduleAutomaticBoltzPrewarm("after_connect")
        }
        startBackgroundSync()
        startHeartbeat()
        _liquidBlockHeight.value = repository.getServerBlockHeight()
        launchRealtimeSubscriptions(reason = "connect")
        preparedSwapSession = null
        resumePendingSwapIfNeeded()
        resumePendingLightningWorkIfNeeded()
    }

    private fun launchDisconnect() {
        launchLiquidJob(Dispatchers.IO) {
            repository.disconnect()
        }
    }

    private fun tryAutoSwitchFrom(failedServerId: String) {
        if (!_liquidAutoSwitchServer.value) return
        autoSwitchVisited += failedServerId
        val nextServer = _liquidServersState.value.servers.firstOrNull { it.id !in autoSwitchVisited } ?: run {
            autoSwitchVisited.clear()
            return
        }
        launchLiquidJob {
            _events.emit(LiquidEvent.Error("Switching Liquid server to ${nextServer.displayName()}"))
        }
        connectToLiquidServerInternal(nextServer.id, isAutoSwitch = true)
    }

    // ════════════════════════════════════════════
    // Sync
    // ════════════════════════════════════════════

    fun requestFullSync(walletId: String) {
        if (!isLiquidEnabledForWallet(walletId)) return
        secureStorage.setNeedsLiquidFullSync(walletId, true)
        if (loadedWalletId.value == walletId && isLiquidConnected.value) {
            launchRequestedLiquidFullSync(walletId)
        }
    }

    private fun launchRequestedLiquidFullSync(walletId: String) {
        if (liquidFullSyncJob?.isActive == true) return
        val job =
            launchLiquidJob {
                try {
                    runRequestedLiquidFullSyncIfNeeded(walletId)
                } finally {
                    if (liquidFullSyncJob == currentCoroutineContext()[Job]) {
                        liquidFullSyncJob = null
                    }
                }
            }
        liquidFullSyncJob = job
    }

    private suspend fun runRequestedLiquidFullSyncIfNeeded(walletId: String) {
        if (loadedWalletId.value != walletId) return
        if (!isLiquidConnected.value) return
        if (!secureStorage.needsLiquidFullSync(walletId)) return
        try {
            val didSync =
                repository.sync(
                forceFullScan = true,
                showFullSyncProgress = true,
                )
            if (!didSync) return
            _liquidBlockHeight.value = repository.getServerBlockHeight()
            refreshLiquidSnapshotsIfNeeded(walletId)
            secureStorage.setNeedsLiquidFullSync(walletId, false)
        } catch (_: CancellationException) {
            throw CancellationException()
        } catch (e: Exception) {
            logError("Liquid full sync failed", e)
        }
    }

    fun cancelFullSync() {
        val walletId = loadedWalletId.value ?: pendingWalletLoadId
        val jobToCancel = liquidFullSyncJob
        liquidFullSyncJob = null
        clearPendingFullSyncProgress()
        walletId?.let { secureStorage.setNeedsLiquidFullSync(it, false) }
        viewModelScope.launch {
            jobToCancel?.cancelAndJoin()
        }
    }

    fun syncLiquidWallet() {
        requireLiquidAvailable()
        launchLiquidJob {
            try {
                repository.sync()
                _liquidBlockHeight.value = repository.getServerBlockHeight()
            } catch (e: Exception) {
                logError("Liquid sync failed", e)
            }
        }
    }

    fun refreshCachedWalletState() {
        repository.refreshCachedWalletState()
        refreshCurrentLiquidSnapshots()
    }

    // ════════════════════════════════════════════
    // Receive (Address + Lightning Invoice)
    // ════════════════════════════════════════════

    fun ensureLiquidAddress() {
        launchLiquidJob(Dispatchers.IO) {
            repository.getNewAddress()
            refreshLiquidAddressBook()
        }
    }

    fun generateFreshLiquidAddress() {
        launchLiquidJob(Dispatchers.IO) {
            repository.generateFreshAddress()
            refreshLiquidAddressBook()
        }
    }

    fun createLightningInvoice(
        amountSats: Long,
        localLabel: String? = null,
        embedLabelInInvoice: Boolean = false,
    ) {
        requireLiquidAvailable()
        if (!isBoltzEnabled()) {
            viewModelScope.launch {
                _events.emit(LiquidEvent.Error("Boltz API is disabled. Lightning swap functionality is unavailable."))
            }
            return
        }
        launchLiquidJob {
            val traceStartedAt = boltzTraceStart()
            val trace =
                BoltzTraceContext(
                    operation = "createLightningInvoiceUi",
                    viaTor = boltzApiSource.value == SecureStorage.BOLTZ_API_TOR,
                    source = "viewmodel",
                )
            _lightningInvoiceState.value = LightningInvoiceState.Generating
            logBoltzTrace("start", trace, "amountSats" to amountSats)
            try {
                val created =
                    repository.createLightningInvoice(
                        amountSats = amountSats,
                        localLabel = localLabel,
                        embeddedLabel = localLabel?.takeIf { embedLabelInInvoice },
                    )
                activeLightningInvoiceSwapId = created.swapId
                logBoltzTrace(
                    "ready",
                    trace.copy(swapId = created.swapId),
                    "elapsedMs" to boltzElapsedMs(traceStartedAt),
                    "amountSats" to amountSats,
                )
                _lightningInvoiceState.value = LightningInvoiceState.Ready(
                    invoice = created.invoice,
                    swapId = created.swapId,
                    amountSats = amountSats,
                )
                refreshPendingLightningInvoices()
                monitorLightningInvoice(created.swapId)
            } catch (e: Exception) {
                logBoltzTrace(
                    "failed",
                    trace,
                    level = BoltzTraceLevel.WARN,
                    throwable = e,
                    "elapsedMs" to boltzElapsedMs(traceStartedAt),
                    "amountSats" to amountSats,
                )
                logError("Failed to create Lightning invoice", e)
                _lightningInvoiceState.value = LightningInvoiceState.Failed(
                    error = safeLiquidErrorMessage(e, "Failed to create invoice"),
                )
            }
        }
    }

    private fun monitorLightningInvoice(swapId: String): Job {
        synchronized(lightningInvoiceMonitorJobs) {
            lightningInvoiceMonitorJobs[swapId]
                ?.takeIf { it.isActive }
                ?.let { return it }
        }
        val job = launchLiquidJob monitor@{
            val traceStartedAt = boltzTraceStart()
            val trace =
                BoltzTraceContext(
                    operation = "monitorLightningInvoice",
                    swapId = swapId,
                    viaTor = boltzApiSource.value == SecureStorage.BOLTZ_API_TOR,
                    source = "viewmodel",
                )
            var lastBoltzUpdate: BoltzSwapUpdate? = null
            logBoltzTrace("start", trace)
            var retryCount = 0
            retryLoop@ while (true) {
                try {
                    while (true) {
                        val paymentState = repository.advanceLightningInvoice(swapId)
                        retryCount = 0
                        when (paymentState) {
                            lwk.PaymentState.CONTINUE -> {
                                logBoltzTrace("continue", trace, "elapsedMs" to boltzElapsedMs(traceStartedAt))
                                val boltzUpdate =
                                    repository.awaitBoltzSwapActivity(
                                        swapId = swapId,
                                        previousUpdate = lastBoltzUpdate,
                                    )
                                if (boltzUpdate != null) {
                                    lastBoltzUpdate = boltzUpdate
                                    logBoltzTrace(
                                        "status",
                                        trace,
                                        "elapsedMs" to boltzElapsedMs(traceStartedAt),
                                        "status" to boltzUpdate.status,
                                        "txid" to boltzUpdate.transactionId,
                                    )
                                }
                                val claimUpdate = selectLiquidLightningInvoiceClaimUpdate(boltzUpdate, lastBoltzUpdate)
                                if (handleLightningInvoiceBoltzStatus(
                                        swapId = swapId,
                                        boltzUpdate = claimUpdate,
                                        trace = trace,
                                        traceStartedAt = traceStartedAt,
                                        source = "continue_status",
                                    )
                                ) {
                                    return@monitor
                                }
                                if (claimUpdate == null) {
                                    markLightningInvoiceClaiming(swapId, claiming = false)
                                }
                            }
                            lwk.PaymentState.SUCCESS -> {
                                val claimCompleted = completeLightningInvoiceClaim(
                                    swapId = swapId,
                                    trace = trace,
                                    traceStartedAt = traceStartedAt,
                                    source = "success_complete",
                                )
                                if (!claimCompleted) {
                                    logBoltzTrace("finish_waiting", trace, "elapsedMs" to boltzElapsedMs(traceStartedAt))
                                    val boltzUpdate =
                                        repository.awaitBoltzSwapActivity(
                                            swapId = swapId,
                                            previousUpdate = lastBoltzUpdate,
                                        )
                                    if (boltzUpdate != null) {
                                        lastBoltzUpdate = boltzUpdate
                                        logBoltzTrace(
                                            "status",
                                            trace,
                                            "elapsedMs" to boltzElapsedMs(traceStartedAt),
                                            "status" to boltzUpdate.status,
                                            "txid" to boltzUpdate.transactionId,
                                        )
                                    }
                                    val claimUpdate = selectLiquidLightningInvoiceClaimUpdate(boltzUpdate, lastBoltzUpdate)
                                    if (handleLightningInvoiceBoltzStatus(
                                            swapId = swapId,
                                            boltzUpdate = claimUpdate,
                                            trace = trace,
                                            traceStartedAt = traceStartedAt,
                                            source = "success_status",
                                        )
                                    ) {
                                        return@monitor
                                    }
                                    continue
                                }
                                return@monitor
                            }
                            lwk.PaymentState.FAILED -> {
                                val boltzUpdate =
                                    repository.awaitBoltzSwapActivity(
                                        swapId = swapId,
                                        previousUpdate = lastBoltzUpdate,
                                    )
                                if (boltzUpdate != null) {
                                    lastBoltzUpdate = boltzUpdate
                                    logBoltzTrace(
                                        "status_after_failed_state",
                                        trace,
                                        level = BoltzTraceLevel.WARN,
                                        "elapsedMs" to boltzElapsedMs(traceStartedAt),
                                        "status" to boltzUpdate.status,
                                        "txid" to boltzUpdate.transactionId,
                                    )
                                }
                                val claimUpdate = selectLiquidLightningInvoiceClaimUpdate(boltzUpdate, lastBoltzUpdate)
                                if (handleLightningInvoiceBoltzStatus(
                                        swapId = swapId,
                                        boltzUpdate = claimUpdate,
                                        trace = trace,
                                        traceStartedAt = traceStartedAt,
                                        source = "failed_state_status",
                                    )
                                ) {
                                    return@monitor
                                }
                                if (isLiquidLightningInvoiceTerminalFailureStatus(boltzUpdate?.status)) {
                                    if (repository.getPendingLightningInvoiceSession(swapId) == null) {
                                        if (activeLightningInvoiceSwapId == swapId) {
                                            activeLightningInvoiceSwapId = null
                                            _lightningInvoiceState.value = LightningInvoiceState.Idle
                                        }
                                        refreshPendingLightningInvoices()
                                        logBoltzTrace(
                                            "settled_cleanup",
                                            trace,
                                            "elapsedMs" to boltzElapsedMs(traceStartedAt),
                                            "status" to boltzUpdate?.status,
                                        )
                                        return@monitor
                                    }
                                    repository.failLightningInvoice(swapId)
                                    if (activeLightningInvoiceSwapId == swapId) {
                                        activeLightningInvoiceSwapId = null
                                        _lightningInvoiceState.value = LightningInvoiceState.Failed(
                                            error = "Lightning receive failed",
                                        )
                                    }
                                    refreshPendingLightningInvoices()
                                    logBoltzTrace("failed", trace, "elapsedMs" to boltzElapsedMs(traceStartedAt))
                                    return@monitor
                                }
                                logBoltzTrace(
                                    "failed_state_retrying",
                                    trace,
                                    level = BoltzTraceLevel.WARN,
                                    "elapsedMs" to boltzElapsedMs(traceStartedAt),
                                    "status" to boltzUpdate?.status,
                                )
                                delay(MONITOR_RETRY_BASE_MS)
                            }
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (settleMissingPendingLightningInvoice(swapId)) {
                        return@monitor
                    }
                    retryCount++
                    logBoltzTrace(
                        "failed",
                        trace,
                        level = BoltzTraceLevel.WARN,
                        throwable = e,
                        "elapsedMs" to boltzElapsedMs(traceStartedAt),
                        "retry" to retryCount,
                    )
                    logError("Failed to monitor Lightning invoice", e)
                    val stillPending = repository.getPendingLightningInvoiceSession(swapId) != null
                    runCatching { repository.failLightningInvoice(swapId, clearPendingMetadata = false) }
                    markLightningInvoiceClaiming(swapId, claiming = false)
                    if (stillPending) {
                        val delayMs =
                            (MONITOR_RETRY_BASE_MS * (1L shl (retryCount - 1).coerceAtMost(4)))
                                .coerceAtMost(RECONNECT_MAX_DELAY_MS)
                        delay(delayMs)
                        continue@retryLoop
                    } else {
                        if (activeLightningInvoiceSwapId == swapId) {
                            activeLightningInvoiceSwapId = null
                            _lightningInvoiceState.value = LightningInvoiceState.Idle
                        }
                        refreshPendingLightningInvoices()
                        return@monitor
                    }
                }
            }
        }
        synchronized(lightningInvoiceMonitorJobs) {
            lightningInvoiceMonitorJobs[swapId] = job
        }
        job.invokeOnCompletion {
            synchronized(lightningInvoiceMonitorJobs) {
                if (lightningInvoiceMonitorJobs[swapId] === job) {
                    lightningInvoiceMonitorJobs.remove(swapId)
                }
            }
        }
        return job
    }

    private suspend fun handleLightningInvoiceBoltzStatus(
        swapId: String,
        boltzUpdate: BoltzSwapUpdate?,
        trace: BoltzTraceContext,
        traceStartedAt: Long,
        source: String,
    ): Boolean {
        val status = boltzUpdate?.status ?: return false
        if (isLiquidLightningInvoiceClaimedStatus(status)) {
            val txid = boltzUpdate.transactionId
            if (txid.isNullOrBlank()) {
                logBoltzTrace("claimed_without_txid", trace, level = BoltzTraceLevel.WARN, "source" to source)
                return completeLightningInvoiceClaim(swapId, trace, traceStartedAt, "${source}_claimed_no_txid")
            }
            SecureLog.w(TAG, "Boltz reported $status before LWK completed the lightning invoice; forcing success")
            repository.finalizeLightningInvoiceClaimed(swapId = swapId, txid = txid)
            publishLightningInvoiceClaimed(swapId = swapId, txid = txid)
            logBoltzTrace("claimed_shortcut", trace, "elapsedMs" to boltzElapsedMs(traceStartedAt), "txid" to txid)
            return true
        }
        if (!isLiquidLightningInvoiceClaimableStatus(status)) {
            markLightningInvoiceClaiming(swapId, claiming = false)
            return false
        }
        logBoltzTrace("claim_ready", trace, "elapsedMs" to boltzElapsedMs(traceStartedAt), "status" to status)
        return completeLightningInvoiceClaim(swapId, trace, traceStartedAt, "${source}_claim_ready")
    }

    private fun settleMissingPendingLightningInvoice(swapId: String): Boolean {
        if (repository.getPendingLightningInvoiceSession(swapId) != null) return false
        markLightningInvoiceClaiming(swapId, claiming = false)
        if (activeLightningInvoiceSwapId == swapId) {
            activeLightningInvoiceSwapId = null
            _lightningInvoiceState.value = LightningInvoiceState.Idle
        }
        refreshPendingLightningInvoices()
        return true
    }

    private suspend fun completeLightningInvoiceClaim(
        swapId: String,
        trace: BoltzTraceContext,
        traceStartedAt: Long,
        source: String,
    ): Boolean {
        val txid =
            try {
                repository.finishLightningInvoice(swapId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (settleMissingPendingLightningInvoice(swapId)) {
                    return true
                }
                logBoltzTrace(
                    "claim_retry",
                    trace,
                    level = BoltzTraceLevel.WARN,
                    throwable = e,
                    "elapsedMs" to boltzElapsedMs(traceStartedAt),
                    "source" to source,
                )
                null
            }
        if (txid.isNullOrBlank()) {
            markLightningInvoiceClaiming(swapId, claiming = false)
            return false
        }
        logBoltzTrace("claimed", trace, "elapsedMs" to boltzElapsedMs(traceStartedAt), "txid" to txid, "source" to source)
        publishLightningInvoiceClaimed(swapId = swapId, txid = txid)
        return true
    }

    private suspend fun publishLightningInvoiceClaimed(
        swapId: String,
        txid: String,
    ) {
        markLightningInvoiceClaiming(swapId, claiming = false)
        if (activeLightningInvoiceSwapId == swapId) {
            activeLightningInvoiceSwapId = null
            _lightningInvoiceState.value = LightningInvoiceState.Claimed(txid = txid)
        }
        _events.emit(LiquidEvent.LightningReceived(txid))
        repository.sync()
        refreshPendingLightningInvoices()
    }

    fun resetLightningInvoice() {
        launchLiquidJob {
            activeLightningInvoiceSwapId = null
            _lightningInvoiceState.value = LightningInvoiceState.Idle
            refreshPendingLightningInvoices()
        }
    }

    fun claimPendingLightningInvoice(swapId: String) {
        launchLiquidJob {
            val session = repository.getPendingLightningInvoiceSession(swapId) ?: return@launchLiquidJob
            markLightningInvoiceClaiming(swapId, claiming = true, updateAttempt = true)
            activeLightningInvoiceSwapId = session.swapId
            _lightningInvoiceState.value = LightningInvoiceState.Ready(
                invoice = session.invoice,
                swapId = session.swapId,
                amountSats = session.amountSats,
            )
            monitorLightningInvoice(session.swapId)
            refreshPendingLightningInvoices()
        }
    }

    fun deletePendingLightningInvoice(swapId: String) {
        launchLiquidJob {
            stopLightningInvoiceMonitor(swapId)
            lightningInvoiceClaimingSwapIds -= swapId
            lightningInvoiceLastClaimAttempts.remove(swapId)
            repository.deletePendingLightningInvoice(swapId)
            if (activeLightningInvoiceSwapId == swapId ||
                (_lightningInvoiceState.value as? LightningInvoiceState.Ready)?.swapId == swapId
            ) {
                activeLightningInvoiceSwapId = null
                _lightningInvoiceState.value = LightningInvoiceState.Idle
            }
            refreshPendingLightningInvoices()
        }
    }

    fun getAllLiquidTransactionLabels(walletId: String): Map<String, String> {
        return if (walletId == loadedWalletId.value) {
            _liquidTransactionLabels.value
        } else {
            secureStorage.getAllLiquidTransactionLabels(walletId)
        }
    }

    fun getAllLiquidAddressLabels(walletId: String): Map<String, String> {
        return secureStorage.getAllLiquidAddressLabels(walletId)
    }

    fun getAllLiquidAddresses(): Triple<List<WalletAddress>, List<WalletAddress>, List<WalletAddress>> {
        return _allLiquidAddresses.value
    }

    fun getAllLiquidUtxos(): List<UtxoInfo> {
        return _allLiquidUtxos.value
    }

    fun getLiquidLabelCounts(walletId: String): Pair<Int, Int> {
        return Pair(
            secureStorage.getAllLiquidAddressLabels(walletId).size,
            secureStorage.getAllLiquidTransactionLabels(walletId).size,
        )
    }

    fun getLiquidBip329LabelsContent(walletId: String): String {
        val metadata = secureStorage.getWalletMetadata(walletId)
        val origin = metadata?.let {
            Bip329Labels.buildOrigin(
                addressType = it.addressType,
                fingerprint = it.masterFingerprint,
            )
        }
        return Bip329Labels.export(
            addressLabels = secureStorage.getAllLiquidAddressLabels(walletId),
            transactionLabels = secureStorage.getAllLiquidTransactionLabels(walletId),
            origin = origin,
            network = Bip329LabelNetwork.LIQUID,
        )
    }

    fun importLiquidBip329LabelsFromContent(
        walletId: String,
        content: String,
        defaultScope: Bip329LabelScope = Bip329LabelScope.LIQUID,
    ): Int {
        val result = Bip329Labels.import(content, defaultScope)

        secureStorage.saveLiquidAddressLabels(walletId, result.liquidAddressLabels)
        secureStorage.saveLiquidTransactionLabels(walletId, result.liquidTransactionLabels)

        repository.refreshCachedWalletState()
        refreshLiquidSnapshotsIfNeeded(walletId)
        return result.totalLiquidLabelsImported
    }

    fun saveLiquidAddressLabel(
        walletId: String,
        address: String,
        label: String,
    ) {
        secureStorage.saveLiquidAddressLabel(walletId, address, label)
        val normalizedLabel = label.ifBlank { null }
        patchCurrentLiquidAddressLabelIfActive(walletId, address, normalizedLabel)
        patchLiquidAddressLabelInPlaceIfActive(walletId, address, normalizedLabel)
        if (walletId == loadedWalletId.value) {
            refreshLiquidUtxos()
            refreshLiquidTransactionLabelSnapshots(walletId)
        }
    }

    fun deleteLiquidAddressLabel(
        walletId: String,
        address: String,
    ) {
        secureStorage.deleteLiquidAddressLabel(walletId, address)
        patchCurrentLiquidAddressLabelIfActive(walletId, address, null)
        patchLiquidAddressLabelInPlaceIfActive(walletId, address, null)
        if (walletId == loadedWalletId.value) {
            refreshLiquidUtxos()
            refreshLiquidTransactionLabelSnapshots(walletId)
        }
    }

    private fun patchCurrentLiquidAddressLabelIfActive(
        walletId: String,
        address: String,
        label: String?,
    ) {
        if (walletId != loadedWalletId.value) return
        repository.patchCurrentAddressLabelIfCurrent(address, label)
    }

    /**
     * Patch the in-memory Liquid address book with a changed label without re-deriving
     * every revealed change address (blech32 + SLIP-77 per index is expensive on big wallets).
     */
    private fun patchLiquidAddressLabelInPlaceIfActive(
        walletId: String,
        address: String,
        label: String?,
    ) {
        if (walletId != loadedWalletId.value) return
        val (receive, change, used) = _allLiquidAddresses.value

        fun List<WalletAddress>.patched(): List<WalletAddress> {
            var changed = false
            val result =
                map { entry ->
                    if (entry.address == address && entry.label != label) {
                        changed = true
                        entry.copy(label = label)
                    } else {
                        entry
                    }
                }
            return if (changed) result else this
        }

        val patched = Triple(receive.patched(), change.patched(), used.patched())
        if (patched !== _allLiquidAddresses.value) {
            _allLiquidAddresses.value = patched
        }
    }

    fun saveLiquidTransactionLabel(
        walletId: String,
        txid: String,
        label: String,
    ) {
        if (walletId == loadedWalletId.value) {
            repository.saveLiquidTransactionLabel(txid, label)
        } else {
            secureStorage.saveLiquidTransactionLabel(walletId, txid, label)
        }
        repository.refreshCachedWalletState()
        refreshLiquidSnapshotsIfNeeded(walletId)
    }

    fun deleteLiquidTransactionLabel(
        walletId: String,
        txid: String,
    ) {
        if (walletId == loadedWalletId.value) {
            repository.deleteLiquidTransactionLabel(txid)
        } else {
            secureStorage.deleteLiquidTransactionLabel(walletId, txid)
        }
        repository.refreshCachedWalletState()
        refreshLiquidSnapshotsIfNeeded(walletId)
    }

    suspend fun searchTransactions(
        query: String,
        includeSwap: Boolean,
        includeLightning: Boolean,
        includeNative: Boolean,
        includeUsdt: Boolean,
        limit: Int,
    ): TransactionSearchResult =
        withContext(Dispatchers.IO) {
            repository.searchLiquidTransactionTxids(
                query = query,
                includeSwap = includeSwap,
                includeLightning = includeLightning,
                includeNative = includeNative,
                includeUsdt = includeUsdt,
                limit = limit,
            )
        }

    fun setLiquidUtxoFrozen(
        outpoint: String,
        frozen: Boolean,
    ) {
        repository.setUtxoFrozen(outpoint, frozen)
        refreshLiquidUtxos()
    }

    // ════════════════════════════════════════════
    // Send (L-BTC + Lightning via Boltz)
    // ════════════════════════════════════════════

    fun previewLiquidSend(
        address: String,
        amountSats: Long,
        feeRate: Double = MIN_LIQUID_SEND_FEE_RATE,
        selectedUtxos: List<UtxoInfo>? = null,
        isMaxSend: Boolean = false,
        label: String? = null,
    ) {
        requireLiquidAvailable()
        startLatestSendPreview(kind = LiquidSendKind.LBTC) {
            repository.previewLbtcSend(
                address = address,
                amountSats = amountSats,
                feeRateSatPerVb = feeRate,
                selectedUtxos = selectedUtxos,
                isMaxSend = isMaxSend,
                label = label,
            )
        }
    }

    fun previewLiquidSendMulti(
        recipients: List<Recipient>,
        feeRate: Double = MIN_LIQUID_SEND_FEE_RATE,
        selectedUtxos: List<UtxoInfo>? = null,
        label: String? = null,
    ) {
        requireLiquidAvailable()
        startLatestSendPreview(kind = LiquidSendKind.LBTC) {
            repository.previewLbtcSendMulti(
                recipients = recipients,
                feeRateSatPerVb = feeRate,
                selectedUtxos = selectedUtxos,
                label = label,
            )
        }
    }

    fun previewLightningPayment(
        paymentInput: String,
        kind: LiquidSendKind,
        amountSats: Long?,
        feeRate: Double = MIN_LIQUID_SEND_FEE_RATE,
        selectedUtxos: List<UtxoInfo>? = null,
        label: String? = null,
    ) {
        requireLiquidAvailable()
        if (!isBoltzEnabled()) {
            _sendState.value = LiquidSendState.Failed("Boltz API is disabled. Lightning swap functionality is unavailable.")
            return
        }
        startLatestSendPreview(kind = kind) {
            buildLightningSendPreview(
                paymentInput = paymentInput,
                kind = kind,
                amountSats = amountSats,
                feeRate = feeRate,
                selectedUtxos = selectedUtxos,
                label = label,
            )
        }
    }

    fun resolveLightningPaymentReview(
        paymentInput: String,
        kind: LiquidSendKind,
        amountSats: Long?,
        feeRate: Double = MIN_LIQUID_SEND_FEE_RATE,
        selectedUtxos: List<UtxoInfo>? = null,
        label: String? = null,
    ) {
        requireLiquidAvailable()
        if (!isBoltzEnabled()) {
            _sendState.value = LiquidSendState.Failed("Boltz API is disabled. Lightning swap functionality is unavailable.")
            return
        }
        launchLiquidJob {
            val traceStartedAt = boltzTraceStart()
            val trace =
                BoltzTraceContext(
                    operation = "resolveLightningPaymentReview",
                    viaTor = boltzApiSource.value == SecureStorage.BOLTZ_API_TOR,
                    source = "viewmodel",
                )
            val basicPreview =
                buildLightningSendPreview(
                    paymentInput = paymentInput.trim().removePrefix("lightning:").removePrefix("LIGHTNING:"),
                    kind = kind,
                    amountSats = amountSats,
                    feeRate = feeRate,
                    selectedUtxos = selectedUtxos,
                    label = label,
                )
            _sendState.value = LiquidSendState.Sending(
                preview = basicPreview,
                status = "Resolving Lightning payment...",
                canDismiss = false,
            )
            logBoltzTrace(
                "start",
                trace,
                "input" to summarizeValue(paymentInput),
                "requestedAmountSats" to amountSats,
            )
            try {
                val resolvedPreview =
                    repository.resolveLightningPaymentPreview(
                        paymentInput = paymentInput,
                        requestedAmountSats = amountSats,
                        kind = kind,
                        feeRateSatPerVb = feeRate,
                        selectedUtxos = selectedUtxos,
                        label = label,
                    )
                logBoltzTrace(
                    "success",
                    trace,
                    "elapsedMs" to boltzElapsedMs(traceStartedAt),
                    "totalRecipientSats" to resolvedPreview.totalRecipientSats,
                    "feeSats" to resolvedPreview.feeSats,
                )
                _sendState.value = LiquidSendState.ReviewReady(resolvedPreview)
            } catch (e: Exception) {
                logBoltzTrace(
                    "failed",
                    trace,
                    level = BoltzTraceLevel.WARN,
                    throwable = e,
                    "elapsedMs" to boltzElapsedMs(traceStartedAt),
                    "input" to summarizeValue(paymentInput),
                )
                logError("Failed to resolve Lightning payment", e)
                _sendState.value = LiquidSendState.Failed(safeLiquidErrorMessage(e, "Payment resolution failed"), basicPreview)
            }
        }
    }

    fun sendLBTC(
        address: String,
        amountSats: Long,
        feeRate: Double = MIN_LIQUID_SEND_FEE_RATE,
        selectedUtxos: List<UtxoInfo>? = null,
        isMaxSend: Boolean = false,
        label: String? = null,
    ) {
        requireLiquidAvailable()
        cancelActiveSendPreview()
        launchLiquidJob {
            val preview =
                when (val current = _sendState.value) {
                    is LiquidSendState.ReviewReady -> current.preview
                    is LiquidSendState.Failed -> current.preview ?: repository.previewLbtcSend(
                        address = address,
                        amountSats = amountSats,
                        feeRateSatPerVb = feeRate,
                        selectedUtxos = selectedUtxos,
                        isMaxSend = isMaxSend,
                        label = label,
                    )
                    else -> repository.previewLbtcSend(
                        address = address,
                        amountSats = amountSats,
                        feeRateSatPerVb = feeRate,
                        selectedUtxos = selectedUtxos,
                        isMaxSend = isMaxSend,
                        label = label,
                    )
                }
            _sendState.value = LiquidSendState.Sending(preview = preview, status = "Sending L-BTC...")
            try {
                val txid = repository.sendLBTC(
                    address = address,
                    amountSats = amountSats,
                    feeRateSatPerVb = feeRate,
                    selectedUtxos = selectedUtxos,
                    isMaxSend = isMaxSend,
                    label = label,
                )
                _sendState.value = LiquidSendState.Success(
                    preview = preview,
                    message = "The L-BTC transaction was sent successfully.",
                    fundingTxid = txid,
                )
                _events.emit(LiquidEvent.TransactionSent(txid))
            } catch (e: Exception) {
                _sendState.value = LiquidSendState.Failed(safeLiquidErrorMessage(e, "Send failed"), preview)
            }
        }
    }

    fun sendLBTCMulti(
        recipients: List<Recipient>,
        feeRate: Double = MIN_LIQUID_SEND_FEE_RATE,
        selectedUtxos: List<UtxoInfo>? = null,
        label: String? = null,
    ) {
        requireLiquidAvailable()
        cancelActiveSendPreview()
        launchLiquidJob {
            val preview =
                when (val current = _sendState.value) {
                    is LiquidSendState.ReviewReady -> current.preview
                    is LiquidSendState.Failed -> current.preview ?: repository.previewLbtcSendMulti(
                        recipients = recipients,
                        feeRateSatPerVb = feeRate,
                        selectedUtxos = selectedUtxos,
                        label = label,
                    )
                    else -> repository.previewLbtcSendMulti(
                        recipients = recipients,
                        feeRateSatPerVb = feeRate,
                        selectedUtxos = selectedUtxos,
                        label = label,
                    )
                }
            _sendState.value = LiquidSendState.Sending(preview = preview, status = "Sending L-BTC...")
            try {
                val txid = repository.sendLBTCMulti(
                    recipients = recipients,
                    feeRateSatPerVb = feeRate,
                    selectedUtxos = selectedUtxos,
                    label = label,
                )
                _sendState.value = LiquidSendState.Success(
                    preview = preview,
                    message = "The L-BTC transaction was sent successfully.",
                    fundingTxid = txid,
                )
                _events.emit(LiquidEvent.TransactionSent(txid))
            } catch (e: Exception) {
                _sendState.value = LiquidSendState.Failed(safeLiquidErrorMessage(e, "Send failed"), preview)
            }
        }
    }

    // ════════════════════════════════════════════
    // Send Liquid asset (non-L-BTC, e.g. USDT)
    // ════════════════════════════════════════════

    fun previewAssetSend(
        address: String,
        amount: Long,
        assetId: String,
        feeRate: Double = MIN_LIQUID_SEND_FEE_RATE,
        selectedUtxos: List<UtxoInfo>? = null,
        label: String? = null,
    ) {
        requireLiquidAvailable()
        startLatestSendPreview(kind = LiquidSendKind.LIQUID_ASSET) {
            repository.previewAssetSend(
                address = address,
                amount = amount,
                assetId = assetId,
                feeRateSatPerVb = feeRate,
                selectedUtxos = selectedUtxos,
                label = label,
            )
        }
    }

    fun sendAsset(
        address: String,
        amount: Long,
        assetId: String,
        feeRate: Double = MIN_LIQUID_SEND_FEE_RATE,
        selectedUtxos: List<UtxoInfo>? = null,
        label: String? = null,
    ) {
        requireLiquidAvailable()
        cancelActiveSendPreview()
        val ticker = LiquidAsset.resolve(assetId).ticker
        launchLiquidJob {
            val preview =
                when (val current = _sendState.value) {
                    is LiquidSendState.ReviewReady -> current.preview
                    is LiquidSendState.Failed -> current.preview ?: repository.previewAssetSend(
                        address = address,
                        amount = amount,
                        assetId = assetId,
                        feeRateSatPerVb = feeRate,
                        selectedUtxos = selectedUtxos,
                        label = label,
                    )
                    else -> repository.previewAssetSend(
                        address = address,
                        amount = amount,
                        assetId = assetId,
                        feeRateSatPerVb = feeRate,
                        selectedUtxos = selectedUtxos,
                        label = label,
                    )
                }
            _sendState.value = LiquidSendState.Sending(preview = preview, status = "Sending $ticker...")
            try {
                val txid = repository.sendAsset(
                    address = address,
                    amount = amount,
                    assetId = assetId,
                    feeRateSatPerVb = feeRate,
                    selectedUtxos = selectedUtxos,
                    label = label,
                )
                _sendState.value = LiquidSendState.Success(
                    preview = preview,
                    message = "$ticker sent successfully.",
                    fundingTxid = txid,
                )
                _events.emit(LiquidEvent.TransactionSent(txid))
            } catch (e: Exception) {
                _sendState.value = LiquidSendState.Failed(safeLiquidErrorMessage(e, "Send failed"), preview)
            }
        }
    }

    private fun disablePsetFlow(assetId: String? = null) {
        _psetState.value = LiquidPsetState(error = PSET_DISABLED_MESSAGE, assetId = assetId)
        viewModelScope.launch {
            _events.emit(LiquidEvent.Error(PSET_DISABLED_MESSAGE))
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun createUnsignedAssetPset(
        address: String,
        amount: Long,
        assetId: String,
        feeRate: Double = MIN_LIQUID_SEND_FEE_RATE,
        selectedUtxos: List<UtxoInfo>? = null,
        label: String? = null,
    ) {
        requireLiquidAvailable()
        disablePsetFlow(assetId)
    }

    // ════════════════════════════════════════════
    // PSET flow (watch-only Liquid wallets)
    // ════════════════════════════════════════════

    @Suppress("UNUSED_PARAMETER")
    fun createUnsignedPset(
        address: String,
        amountSats: Long,
        feeRateSatPerVb: Double = 0.1,
        selectedUtxos: List<UtxoInfo>? = null,
        isMaxSend: Boolean = false,
        label: String? = null,
    ) {
        disablePsetFlow()
    }

    fun cancelPsetFlow() {
        _psetState.value = LiquidPsetState()
    }

    fun confirmLightningPayment(
        selectedUtxos: List<UtxoInfo>? = null,
        label: String? = null,
    ) {
        requireLiquidAvailable()
        if (!isBoltzEnabled()) {
            _sendState.value = LiquidSendState.Failed("Boltz API is disabled. Lightning swap functionality is unavailable.")
            return
        }
        launchLiquidJob {
            val preview = (_sendState.value as? LiquidSendState.ReviewReady)?.preview
            val executionPlan = preview?.executionPlan
            if (preview == null || executionPlan == null || preview.kind == LiquidSendKind.LBTC) {
                _sendState.value = LiquidSendState.Failed("Review the Lightning payment before confirming.", preview)
                return@launchLiquidJob
            }
            try {
                if (executionPlan is LightningPaymentExecutionPlan.BoltzQuote) {
                    _sendState.value = LiquidSendState.Sending(
                        preview = preview,
                        status = "Preparing Boltz lockup details...",
                        canDismiss = false,
                    )
                    val preparedPlan = repository.prepareLightningPaymentForConfirmation(executionPlan)
                    val preparedPreview = repository.resolveLightningPaymentPreview(
                        executionPlan = preparedPlan,
                        kind = preview.kind,
                        feeRateSatPerVb = preview.feeRate,
                        selectedUtxos = selectedUtxos,
                        label = label,
                    )
                    _sendState.value = LiquidSendState.ReviewReady(preparedPreview)
                    _events.emit(LiquidEvent.Error("Lightning payment details prepared. Review again before funding."))
                    return@launchLiquidJob
                }
                when (executionPlan) {
                    is LightningPaymentExecutionPlan.BoltzSwap -> {
                        repository.getPendingLightningPaymentSession()?.let { session ->
                            saveLightningSession(
                                session.copy(
                                    phase = PendingLightningPaymentPhase.FUNDING,
                                    status = "Sending L-BTC to Boltz lockup address...",
                                ),
                            )
                        }
                        _sendState.value = LiquidSendState.Sending(
                            preview = preview,
                            status = "Sending L-BTC to Boltz lockup address...",
                            refundAddress = executionPlan.refundAddress,
                            detail = "Close anytime. Payment continues in background.",
                            canDismiss = true,
                        )
                        val fundingTxid = repository.sendLBTC(
                            address = executionPlan.lockupAddress,
                            amountSats = executionPlan.lockupAmountSats,
                            feeRateSatPerVb = preview.feeRate,
                            selectedUtxos = selectedUtxos,
                            label = label,
                            saveRecipientLabel = false,
                        )
                        repository.saveLiquidTransactionSource(
                            txid = fundingTxid,
                            source = LiquidTxSource.LIGHTNING_SEND_SWAP,
                        )
                        repository.getPendingLightningPaymentSession()?.let { session ->
                            saveLightningSession(
                                session.copy(
                                    phase = PendingLightningPaymentPhase.IN_PROGRESS,
                                    status = "Funding broadcast. Waiting for Boltz payment...",
                                    fundingTxid = fundingTxid,
                                ),
                            )
                        }
                        repository.persistLightningSendSwapDetails(
                            txid = fundingTxid,
                            swapId = executionPlan.swapId,
                        )
                        monitorPreparedLightningPayment(
                            backend = executionPlan.backend,
                            swapId = executionPlan.swapId,
                            paymentReference = executionPlan.paymentInput,
                            fundingTxid = fundingTxid,
                            preview = preview,
                        )
                    }
                    is LightningPaymentExecutionPlan.DirectLiquid -> {
                        _sendState.value = LiquidSendState.Sending(
                            preview = preview,
                            status = "Completing this payment with a direct L-BTC transfer...",
                            detail = "Close anytime. Payment continues in background.",
                            canDismiss = true,
                        )
                        val fundingTxid = repository.sendLBTC(
                            address = executionPlan.address,
                            amountSats = executionPlan.amountSats,
                            feeRateSatPerVb = preview.feeRate,
                            selectedUtxos = selectedUtxos,
                            label = label,
                            saveRecipientLabel = false,
                        )
                        repository.saveLiquidTransactionSource(
                            txid = fundingTxid,
                            source = LiquidTxSource.LIGHTNING_SEND_SWAP,
                        )
                        _sendState.value = LiquidSendState.Success(
                            preview = preview,
                            message = "The Lightning payment was sent successfully.",
                            fundingTxid = fundingTxid,
                        )
                        _events.emit(LiquidEvent.LightningSent(executionPlan.paymentInput))
                        repository.sync()
                    }
                    is LightningPaymentExecutionPlan.BoltzQuote -> {
                        throw Exception("Lightning payment was not prepared")
                    }
                }
            } catch (e: Exception) {
                logError("Failed to confirm Lightning payment", e)
                deleteLightningSession()
                presentLightningPaymentFailure(
                    error = safeLiquidErrorMessage(e, "Payment failed"),
                    preview = preview,
                )
            }
        }
    }

    private fun monitorPreparedLightningPayment(
        backend: LightningPaymentBackend,
        swapId: String,
        paymentReference: String,
        fundingTxid: String,
        preview: LiquidSendPreview,
    ) {
        when (backend) {
            LightningPaymentBackend.LWK_PREPARE_PAY ->
                monitorPreparedLightningPaymentWithLwk(
                    swapId = swapId,
                    paymentReference = paymentReference,
                    fundingTxid = fundingTxid,
                    preview = preview,
                )
            LightningPaymentBackend.BOLTZ_REST_SUBMARINE ->
                monitorPreparedLightningPaymentViaBoltzRest(
                    swapId = swapId,
                    paymentReference = paymentReference,
                    fundingTxid = fundingTxid,
                    preview = preview,
                )
        }
    }

    private fun monitorPreparedLightningPaymentWithLwk(
        swapId: String,
        paymentReference: String,
        fundingTxid: String,
        preview: LiquidSendPreview,
    ) {
        val job = launchLiquidJob monitor@{
            val traceStartedAt = boltzTraceStart()
            val trace =
                BoltzTraceContext(
                    operation = "monitorPreparedLightningPaymentWithLwk",
                    swapId = swapId,
                    backend = LightningPaymentBackend.LWK_PREPARE_PAY.name,
                    viaTor = boltzApiSource.value == SecureStorage.BOLTZ_API_TOR,
                    source = "viewmodel",
                )
            var lastBoltzUpdate: BoltzSwapUpdate? = null
            var refundingSince: Long? = null
            logBoltzTrace("start", trace, "fundingTxid" to summarizeValue(fundingTxid))
            var retryCount = 0
            retryLoop@ while (true) {
            try {
                var continueCount = 0
                while (true) {
                    when (repository.advancePreparedLightningPayment(swapId)) {
                        lwk.PaymentState.CONTINUE -> {
                            continueCount++
                            retryCount = 0
                            val session = repository.getPendingLightningPaymentSession()
                            if (session != null && isLightningRefundDetected(session)) {
                                failLightningSession(swapId)
                                presentLightningPaymentFailure(
                                    error = "The Lightning payment failed and the refund has already returned to your Liquid wallet.",
                                    preview = preview,
                                    refundAddress = session.refundAddress,
                                )
                                return@monitor
                            }
                            val refundEscalated = refundingSince?.let {
                                System.currentTimeMillis() - it >= REFUND_ESCALATION_MS
                            } == true
                            _sendState.value = LiquidSendState.Sending(
                                preview = preview,
                                status = when {
                                    refundEscalated ->
                                        "Refund is taking longer than expected. Use your Boltz rescue key if needed."
                                    continueCount >= LIGHTNING_PENDING_WARNING_ATTEMPTS ->
                                        "Lightning payment is still pending."
                                    else ->
                                        "Waiting for Boltz to settle the Lightning payment..."
                                },
                                refundAddress = session?.refundAddress,
                                detail = when {
                                    refundEscalated ->
                                        "Check the pending payment card for your rescue key."
                                    continueCount >= LIGHTNING_PENDING_WARNING_ATTEMPTS ->
                                        "Still pending. Close anytime; monitoring continues."
                                    else ->
                                        "Close anytime. Payment continues in background."
                                },
                                canDismiss = true,
                            )
                            val boltzUpdate =
                                repository.awaitBoltzSwapActivity(
                                    swapId = swapId,
                                    previousUpdate = lastBoltzUpdate,
                                )
                            if (boltzUpdate != null) {
                                lastBoltzUpdate = boltzUpdate
                                logBoltzTrace(
                                    "status",
                                    trace,
                                    "elapsedMs" to boltzElapsedMs(traceStartedAt),
                                    "status" to boltzUpdate.status,
                                    "txid" to boltzUpdate.transactionId,
                                    "continueCount" to continueCount,
                                )
                            }
                            when (boltzUpdate?.status) {
                                "transaction.claim.pending" -> {
                                    val completed = repository.finishPreparedLightningPayment(swapId)
                                    if (completed != null) {
                                        logBoltzTrace(
                                            "completed",
                                            trace,
                                            "elapsedMs" to boltzElapsedMs(traceStartedAt),
                                            "txid" to summarizeValue(fundingTxid),
                                        )
                                        finalizePreparedLightningPaymentSuccess(
                                            fundingTxid = fundingTxid,
                                            paymentReference = paymentReference,
                                            preview = preview,
                                        )
                                        return@monitor
                                    }
                                }
                                "transaction.claimed" -> {
                                    logBoltzTrace(
                                        "claimed_shortcut",
                                        trace,
                                        "elapsedMs" to boltzElapsedMs(traceStartedAt),
                                        "txid" to boltzUpdate.transactionId,
                                    )
                                    SecureLog.w(
                                        TAG,
                                        "Boltz reported transaction.claimed before LWK completed the prepared payment; forcing success",
                                    )
                                    clearLightningSession(swapId, fundingTxid)
                                    finalizePreparedLightningPaymentSuccess(
                                        fundingTxid = fundingTxid,
                                        paymentReference = paymentReference,
                                        preview = preview,
                                    )
                                    return@monitor
                                }
                                "invoice.failedToPay", "swap.expired", "transaction.lockupFailed" -> {
                                    if (refundingSince == null) refundingSince = System.currentTimeMillis()
                                    val failedSession = repository.getPendingLightningPaymentSession()
                                    logBoltzTrace(
                                        "failed_status_awaiting_lwk_refund",
                                        trace,
                                        level = BoltzTraceLevel.WARN,
                                        "elapsedMs" to boltzElapsedMs(traceStartedAt),
                                        "status" to boltzUpdate.status,
                                    )
                                    _sendState.value = LiquidSendState.Sending(
                                        preview = preview,
                                        status = "Lightning payment failed. LWK is processing the refund...",
                                        refundAddress = failedSession?.refundAddress,
                                        detail = "Close anytime. Refund continues in background.",
                                        canDismiss = true,
                                    )
                                    failedSession?.let { s ->
                                        saveLightningSession(
                                            s.copy(
                                                phase = PendingLightningPaymentPhase.REFUNDING,
                                                status = "Lightning payment failed. Awaiting refund via LWK.",
                                            ),
                                        )
                                    }
                                }
                            }
                        }
                        lwk.PaymentState.SUCCESS -> {
                            val completed = repository.finishPreparedLightningPayment(swapId)
                            if (completed == null) {
                                val boltzUpdate =
                                    repository.awaitBoltzSwapActivity(
                                        swapId = swapId,
                                        previousUpdate = lastBoltzUpdate,
                                    )
                                if (boltzUpdate != null) {
                                    lastBoltzUpdate = boltzUpdate
                                    logBoltzTrace(
                                        "status",
                                        trace,
                                        "elapsedMs" to boltzElapsedMs(traceStartedAt),
                                        "status" to boltzUpdate.status,
                                        "txid" to boltzUpdate.transactionId,
                                    )
                                }
                                val boltzStatus = boltzUpdate?.status
                                if (boltzStatus == "transaction.claimed") {
                                    logBoltzTrace(
                                        "claimed_after_success",
                                        trace,
                                        "elapsedMs" to boltzElapsedMs(traceStartedAt),
                                        "txid" to boltzUpdate.transactionId,
                                    )
                                    SecureLog.w(
                                        TAG,
                                        "Boltz reported transaction.claimed after LWK success path stalled; forcing success",
                                    )
                                    clearLightningSession(swapId, fundingTxid)
                                    finalizePreparedLightningPaymentSuccess(
                                        fundingTxid = fundingTxid,
                                        paymentReference = paymentReference,
                                        preview = preview,
                                    )
                                    return@monitor
                                }
                                continue
                            }
                            logBoltzTrace(
                                "completed",
                                trace,
                                "elapsedMs" to boltzElapsedMs(traceStartedAt),
                                "txid" to summarizeValue(fundingTxid),
                            )
                            finalizePreparedLightningPaymentSuccess(
                                fundingTxid = fundingTxid,
                                paymentReference = paymentReference,
                                preview = preview,
                            )
                            return@monitor
                        }
                        lwk.PaymentState.FAILED -> {
                            val session = repository.getPendingLightningPaymentSession()
                            logBoltzTrace(
                                "failed_state",
                                trace,
                                level = BoltzTraceLevel.WARN,
                                "elapsedMs" to boltzElapsedMs(traceStartedAt),
                            )
                            if (session != null && isLightningRefundDetected(session)) {
                                failLightningSession(swapId)
                                presentLightningPaymentFailure(
                                    error = "The Lightning payment failed and the refund has already returned to your Liquid wallet.",
                                    preview = preview,
                                    refundAddress = session.refundAddress,
                                )
                                return@monitor
                            }
                            failLightningSession(swapId)
                            presentLightningPaymentFailure(
                                error =
                                    "Boltz could not complete the Lightning payment. " +
                                        "The receiver may have rejected it or the amount may have been too small. " +
                                        "If the payment is refunded, it will return to the refund address below.",
                                preview = preview,
                                refundAddress = session?.refundAddress,
                            )
                            return@monitor
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                    if (settleMissingPendingLightningInvoice(swapId)) {
                        return@monitor
                    }
                retryCount++
                logBoltzTrace(
                    "failed",
                    trace,
                    level = BoltzTraceLevel.WARN,
                    throwable = e,
                    "elapsedMs" to boltzElapsedMs(traceStartedAt),
                    "retry" to retryCount,
                )
                logError("Failed to monitor Lightning payment (attempt $retryCount/$MONITOR_MAX_RETRIES)", e)
                if (retryCount < MONITOR_MAX_RETRIES) {
                    delay(MONITOR_RETRY_BASE_MS * (1L shl (retryCount - 1)))
                    continue@retryLoop
                }
                repository.getPendingLightningPaymentSession()?.let { session ->
                    persistLightningPaymentProgress(
                        session = session,
                        status = "Funding may already be onchain. Monitoring interrupted; will resume automatically.",
                        fundingTxid = fundingTxid,
                    )
                    _sendState.value = LiquidSendState.Sending(
                        preview = preview,
                        status = "Funding may already be onchain. Monitoring interrupted; will resume automatically.",
                        refundAddress = session.refundAddress,
                        detail = "Close anytime. Monitoring resumes automatically.",
                        canDismiss = true,
                    )
                } ?: run {
                    presentLightningPaymentFailure(
                        error = safeLiquidErrorMessage(e, "Lightning payment failed"),
                        preview = preview,
                    )
                }
            }
            break@retryLoop
            }
        }
        lightningPaymentMonitorJob = job
        job.invokeOnCompletion {
            if (lightningPaymentMonitorJob === job) {
                lightningPaymentMonitorJob = null
            }
        }
    }

    private fun monitorPreparedLightningPaymentViaBoltzRest(
        swapId: String,
        paymentReference: String,
        fundingTxid: String,
        preview: LiquidSendPreview,
    ) {
        val job = launchLiquidJob monitor@{
            val traceStartedAt = boltzTraceStart()
            val trace =
                BoltzTraceContext(
                    operation = "monitorPreparedLightningPaymentViaBoltzRest",
                    swapId = swapId,
                    backend = LightningPaymentBackend.BOLTZ_REST_SUBMARINE.name,
                    viaTor = boltzApiSource.value == SecureStorage.BOLTZ_API_TOR,
                    source = "viewmodel",
                )
            var lastBoltzUpdate: BoltzSwapUpdate? = null
            var refundingSince: Long? = null
            logBoltzTrace("start", trace, "fundingTxid" to summarizeValue(fundingTxid))
            var retryCount = 0
            retryLoop@ while (true) {
            try {
                var continueCount = 0
                while (true) {
                    val session = repository.getPendingLightningPaymentSession()
                    if (session == null || session.swapId != swapId) {
                        return@monitor
                    }
                    if (isLightningRefundDetected(session)) {
                        failLightningSession(swapId)
                        presentLightningPaymentFailure(
                            error = "The Lightning payment failed and the refund has already returned to your Liquid wallet.",
                            preview = preview,
                            refundAddress = session.refundAddress,
                        )
                        return@monitor
                    }

                    continueCount++
                    retryCount = 0
                    val refundEscalated = refundingSince?.let {
                        System.currentTimeMillis() - it >= REFUND_ESCALATION_MS
                    } == true
                    val boltzUpdate =
                        repository.awaitBoltzSwapActivity(
                            swapId = swapId,
                            previousUpdate = lastBoltzUpdate,
                        )
                    if (boltzUpdate != null) {
                        lastBoltzUpdate = boltzUpdate
                        logBoltzTrace(
                            "status",
                            trace,
                            "elapsedMs" to boltzElapsedMs(traceStartedAt),
                            "status" to boltzUpdate.status,
                            "txid" to boltzUpdate.transactionId,
                            "continueCount" to continueCount,
                        )
                    }
                    val boltzStatus = boltzUpdate?.status
                    when {
                        isRestLightningPaymentPendingStatus(boltzStatus) -> {
                            val waitingStatus = when {
                                refundEscalated ->
                                    "Refund is taking longer than expected. Use your Boltz rescue key if needed."
                                continueCount >= LIGHTNING_PENDING_WARNING_ATTEMPTS ->
                                    "Lightning payment is still pending."
                                else ->
                                    "Waiting for Boltz to settle the Lightning payment..."
                            }
                            persistLightningPaymentProgress(
                                session = session,
                                status = waitingStatus,
                                fundingTxid = fundingTxid,
                            )
                            _sendState.value = LiquidSendState.Sending(
                                preview = preview,
                                status = waitingStatus,
                                refundAddress = session.refundAddress,
                                detail = when {
                                    refundEscalated ->
                                        "Check the pending payment card for your rescue key."
                                    continueCount >= LIGHTNING_PENDING_WARNING_ATTEMPTS ->
                                        "Still pending. Close anytime; monitoring continues."
                                    else ->
                                        "Close anytime. Payment continues in background."
                                },
                                canDismiss = true,
                            )
                        }
                        isRestLightningPaymentSuccessStatus(boltzStatus) -> {
                            // For the REST-backed BOLT12 path we do not yet submit the optional
                            // cooperative claim signature, so provider-side onchain claiming may
                            // continue after the recipient has already been paid. The user-facing
                            // payment should complete as soon as Boltz confirms the invoice was paid.
                            logBoltzTrace(
                                "completed",
                                trace,
                                "elapsedMs" to boltzElapsedMs(traceStartedAt),
                                "status" to boltzStatus,
                                "txid" to boltzUpdate?.transactionId,
                            )
                            clearLightningSession(swapId, fundingTxid)
                            finalizePreparedLightningPaymentSuccess(
                                fundingTxid = fundingTxid,
                                paymentReference = paymentReference,
                                preview = preview,
                            )
                            return@monitor
                        }
                        boltzStatus in setOf("invoice.failedToPay", "swap.expired", "transaction.lockupFailed") -> {
                            if (refundingSince == null) refundingSince = System.currentTimeMillis()
                            logBoltzTrace(
                                "failed_status_awaiting_refund",
                                trace,
                                level = BoltzTraceLevel.WARN,
                                "elapsedMs" to boltzElapsedMs(traceStartedAt),
                                "status" to boltzStatus,
                            )
                            saveLightningSession(
                                session.copy(
                                    phase = PendingLightningPaymentPhase.REFUNDING,
                                    status = "Lightning payment failed ($boltzStatus). Awaiting refund.",
                                ),
                            )
                            val refundStatus = if (refundEscalated) {
                                "Refund is taking longer than expected. Use your Boltz rescue key if needed."
                            } else {
                                "Lightning payment failed. Awaiting refund to your wallet..."
                            }
                            val refundDetail = if (refundEscalated) {
                                "Check the pending payment card for your rescue key."
                            } else {
                                "Close anytime. The refund will arrive at the address below."
                            }
                            _sendState.value = LiquidSendState.Sending(
                                preview = preview,
                                status = refundStatus,
                                refundAddress = session.refundAddress,
                                detail = refundDetail,
                                canDismiss = true,
                            )
                        }
                        else -> {
                            logBoltzTrace(
                                "unexpected_status",
                                trace,
                                level = BoltzTraceLevel.WARN,
                                "elapsedMs" to boltzElapsedMs(traceStartedAt),
                                "status" to boltzStatus,
                            )
                            persistLightningPaymentProgress(
                                session = session,
                                status = "Boltz status: $boltzStatus",
                                fundingTxid = fundingTxid,
                            )
                            _sendState.value = LiquidSendState.Sending(
                                preview = preview,
                                status = "Boltz status: $boltzStatus",
                                refundAddress = session.refundAddress,
                                detail = "Close anytime. Payment continues in background.",
                                canDismiss = true,
                            )
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                retryCount++
                logBoltzTrace(
                    "failed",
                    trace,
                    level = BoltzTraceLevel.WARN,
                    throwable = e,
                    "elapsedMs" to boltzElapsedMs(traceStartedAt),
                    "retry" to retryCount,
                )
                logError("Failed to monitor Lightning payment (attempt $retryCount/$MONITOR_MAX_RETRIES)", e)
                if (retryCount < MONITOR_MAX_RETRIES) {
                    delay(MONITOR_RETRY_BASE_MS * (1L shl (retryCount - 1)))
                    continue@retryLoop
                }
                repository.getPendingLightningPaymentSession()?.let { session ->
                    persistLightningPaymentProgress(
                        session = session,
                        status = "Funding may already be onchain. Monitoring interrupted; will resume automatically.",
                        fundingTxid = fundingTxid,
                    )
                    _sendState.value = LiquidSendState.Sending(
                        preview = preview,
                        status = "Funding may already be onchain. Monitoring interrupted; will resume automatically.",
                        refundAddress = session.refundAddress,
                        detail = "Close anytime. Monitoring resumes automatically.",
                        canDismiss = true,
                    )
                } ?: run {
                    presentLightningPaymentFailure(
                        error = safeLiquidErrorMessage(e, "Lightning payment failed"),
                        preview = preview,
                    )
                }
            }
            break@retryLoop
            }
        }
        lightningPaymentMonitorJob = job
        job.invokeOnCompletion {
            if (lightningPaymentMonitorJob === job) {
                lightningPaymentMonitorJob = null
            }
        }
    }

    private fun persistLightningPaymentProgress(
        session: PendingLightningPaymentSession,
        status: String,
        fundingTxid: String,
    ) {
        val updated = session.copy(
            phase = PendingLightningPaymentPhase.IN_PROGRESS,
            status = status,
            fundingTxid = fundingTxid,
        )
        repository.savePendingLightningPaymentSession(updated)
        _pendingSubmarineSwap.value = updated
    }

    private fun saveLightningSession(session: PendingLightningPaymentSession) {
        repository.savePendingLightningPaymentSession(session)
        _pendingSubmarineSwap.value = session
    }

    private suspend fun clearLightningSession(
        swapId: String,
        fundingTxid: String? = null,
    ) {
        fundingTxid
            ?.takeIf { it.isNotBlank() }
            ?.let { repository.persistLightningSendSwapDetails(it, swapId) }
        repository.clearPreparedLightningPaymentSession(swapId)
        _pendingSubmarineSwap.value = null
    }

    private fun deleteLightningSession() {
        repository.deletePendingLightningPaymentSession()
        _pendingSubmarineSwap.value = null
    }

    private suspend fun failLightningSession(swapId: String) {
        repository.failPreparedLightningPayment(swapId)
        _pendingSubmarineSwap.value = null
    }

    private suspend fun finalizePreparedLightningPaymentSuccess(
        fundingTxid: String,
        paymentReference: String,
        preview: LiquidSendPreview,
    ) {
        _pendingSubmarineSwap.value = null
        if (fundingTxid.isNotBlank()) {
            repository.saveLiquidTransactionSource(
                txid = fundingTxid,
                source = LiquidTxSource.LIGHTNING_SEND_SWAP,
            )
        }
        _sendState.value = LiquidSendState.Success(
            preview = preview,
            message = "The Lightning payment was sent successfully.",
            fundingTxid = fundingTxid.ifBlank { null },
        )
        _events.emit(LiquidEvent.LightningSent(paymentReference))
        repository.sync()
    }

    private suspend fun presentLightningPaymentFailure(
        error: String,
        preview: LiquidSendPreview,
        refundAddress: String? = null,
    ) {
        _sendState.value = LiquidSendState.Failed(
            error = error,
            preview = preview,
            refundAddress = refundAddress,
        )
        _events.emit(LiquidEvent.Error(error))
    }

    private fun isLightningRefundDetected(session: PendingLightningPaymentSession): Boolean {
        return liquidState.value.transactions.any { tx ->
            tx.type == LiquidTxType.RECEIVE &&
                tx.walletAddress.equals(session.refundAddress, ignoreCase = true) &&
                tx.balanceSatoshi > 0L &&
                tx.txid != session.fundingTxid
        }
    }

    // ════════════════════════════════════════════
    // Swaps (BTC ↔ L-BTC)
    // ════════════════════════════════════════════

    /** Fetch min/max limits for the selected service + direction (fire on tab/direction change) */
    fun fetchSwapLimits(direction: SwapDirection, service: SwapService) {
        requireLiquidAvailable()
        launchLiquidJob {
            if (service == SwapService.BOLTZ && !isBoltzEnabled()) {
                _swapLimits.value =
                    _swapLimits.value.toMutableMap().apply {
                        this[service] = SwapLimits(
                            service = service,
                            direction = direction,
                            minAmount = 0,
                            maxAmount = 0,
                            error = "Boltz API is disabled in settings",
                        )
                    }
                return@launchLiquidJob
            }
            if (service == SwapService.SIDESWAP && !isSideSwapEnabled()) {
                _swapLimits.value =
                    _swapLimits.value.toMutableMap().apply {
                        this[service] = SwapLimits(
                            service = service,
                            direction = direction,
                            minAmount = 0,
                            maxAmount = 0,
                            error = "SideSwap is disabled in settings",
                        )
                    }
                return@launchLiquidJob
            }
            _swapLimits.value =
                _swapLimits.value.toMutableMap().apply {
                    this[service] = SwapLimits(
                        service = service,
                        direction = direction,
                        minAmount = 0,
                        maxAmount = 0,
                        isLoading = true,
                    )
                }
            if (service == SwapService.BOLTZ && _isLayer2Enabled.value && isLiquidConnected.value) {
                prewarmBoltzSwapContext(direction)
            }
            try {
                val limits = repository.getSwapLimits(direction, service)
                _swapLimits.value =
                    _swapLimits.value.toMutableMap().apply {
                        this[service] = limits
                    }
            } catch (e: Exception) {
                SecureLog.e(TAG, "Failed to fetch swap limits: ${e.message}", e)
                _swapLimits.value =
                    _swapLimits.value.toMutableMap().apply {
                        this[service] = SwapLimits(
                            service = service,
                            direction = direction,
                            minAmount = 0,
                            maxAmount = 0,
                            error = "Failed to fetch swap limits",
                        )
                    }
            }
        }
    }

    fun requestBoltzWarmupAtAppStart() {
        val trace =
            BoltzTraceContext(
                operation = "requestBoltzWarmupAtAppStart",
                viaTor = boltzApiSource.value == SecureStorage.BOLTZ_API_TOR,
                source = "viewmodel",
            )
        if (!isBoltzEnabled() || !_isLayer2Enabled.value) {
            boltzAppStartWarmupPending = false
            logBoltzTrace(
                "skipped",
                trace,
                "connected" to isLiquidConnected.value,
                "layer2Enabled" to _isLayer2Enabled.value,
                "liquidContextActive" to liquidContextActive,
            )
            return
        }
        if (isLiquidConnected.value) {
            boltzAppStartWarmupPending = false
            logBoltzTrace("launch_now", trace, "connected" to true)
            scheduleAutomaticBoltzPrewarm("app_start_connected")
            return
        }
        boltzAppStartWarmupPending = true
        logBoltzTrace(
            "queued",
            trace,
            "connected" to false,
            "liquidContextActive" to liquidContextActive,
        )
    }

    fun requestBoltzLightningWarmup() {
        val trace =
            BoltzTraceContext(
                operation = "requestBoltzLightningWarmup",
                viaTor = boltzApiSource.value == SecureStorage.BOLTZ_API_TOR,
                source = "viewmodel",
            )
        if (!isBoltzEnabled() || !_isLayer2Enabled.value) {
            logBoltzTrace(
                "skipped",
                trace,
                "connected" to isLiquidConnected.value,
                "layer2Enabled" to _isLayer2Enabled.value,
                "liquidContextActive" to liquidContextActive,
            )
            return
        }
        if (!isLiquidConnected.value) {
            logBoltzTrace(
                "skipped",
                trace,
                "connected" to false,
                "layer2Enabled" to _isLayer2Enabled.value,
                "liquidContextActive" to liquidContextActive,
            )
            return
        }
        logBoltzTrace("launch_now", trace, "connected" to true)
        prewarmBoltzLightningContext()
    }

    private fun cancelAutomaticBoltzPrewarm() {
        boltzAppStartWarmupPending = false
        boltzAutoPrewarmJob?.cancel()
        boltzAutoPrewarmJob = null
    }

    private fun canRunAutomaticBoltzPrewarm(): Boolean =
        isBoltzEnabled() &&
            _isLayer2Enabled.value &&
            liquidContextActive &&
            isLiquidConnected.value &&
            repository.canPrewarmBoltzSession()

    private fun scheduleAutomaticBoltzPrewarm(reason: String) {
        val trace =
            BoltzTraceContext(
                operation = "scheduleAutomaticBoltzPrewarm",
                viaTor = boltzApiSource.value == SecureStorage.BOLTZ_API_TOR,
                source = "viewmodel",
            )
        if (!canRunAutomaticBoltzPrewarm()) {
            logBoltzTrace(
                "skipped",
                trace,
                "reason" to reason,
                "connected" to isLiquidConnected.value,
                "layer2Enabled" to _isLayer2Enabled.value,
                "liquidContextActive" to liquidContextActive,
                "hasBitcoinElectrumUrl" to repository.canPrewarmBoltzSession(),
            )
            return
        }

        val now = nowMs()
        val stabilizationRemainingMs =
            (BOLTZ_AUTO_PREWARM_STABILIZATION_MS - (now - lastLiquidConnectionReadyAtMs)).coerceAtLeast(0L)
        val cooldownRemainingMs =
            (BOLTZ_AUTO_PREWARM_FAILURE_COOLDOWN_MS - (now - lastAutomaticBoltzPrewarmFailureAtMs)).coerceAtLeast(0L)
        val delayMs = maxOf(stabilizationRemainingMs, cooldownRemainingMs)

        boltzAutoPrewarmJob?.cancel()
        boltzAutoPrewarmJob =
            launchLiquidJob {
                logBoltzTrace(
                    "scheduled",
                    trace,
                    "reason" to reason,
                    "delayMs" to delayMs,
                )
                if (delayMs > 0) {
                    delay(delayMs)
                }
                if (!canRunAutomaticBoltzPrewarm()) {
                    logBoltzTrace(
                        "cancelled",
                        trace,
                        "reason" to reason,
                    )
                    return@launchLiquidJob
                }
                if (boltzPrewarmJob?.isActive == true) {
                    logBoltzTrace(
                        "skipped",
                        trace,
                        "reason" to reason,
                        "alreadyRunning" to true,
                    )
                    return@launchLiquidJob
                }
                prewarmBoltzSwapContext(automatic = true)
            }
    }

    private fun prewarmBoltzSwapContext(direction: SwapDirection? = null, automatic: Boolean = false) {
        launchBoltzPrewarm(direction = direction, lightningOnly = false, automatic = automatic)
    }

    private fun prewarmBoltzLightningContext(automatic: Boolean = false) {
        launchBoltzPrewarm(lightningOnly = true, automatic = automatic)
    }

    private fun launchBoltzPrewarm(
        direction: SwapDirection? = null,
        lightningOnly: Boolean = false,
        automatic: Boolean = false,
    ) {
        val previousJob = boltzPrewarmJob
        val job = launchLiquidJob(Dispatchers.IO) {
            val traceStartedAt = boltzTraceStart()
            val trace =
                BoltzTraceContext(
                    operation = if (lightningOnly) "prewarmBoltzLightningContext" else "prewarmBoltzSwapContext",
                    viaTor = boltzApiSource.value == SecureStorage.BOLTZ_API_TOR,
                    source = "viewmodel",
                )
            if (previousJob != null && previousJob != currentCoroutineContext()[Job]) {
                previousJob.cancelAndJoin()
            }
            if (!isBoltzEnabled() || !_isLayer2Enabled.value || !isLiquidConnected.value) {
                logBoltzTrace(
                    "skipped",
                    trace,
                    "direction" to direction?.name,
                    "lightningOnly" to lightningOnly,
                    "automatic" to automatic,
                    "connected" to isLiquidConnected.value,
                    "layer2Enabled" to _isLayer2Enabled.value,
                    "liquidContextActive" to liquidContextActive,
                )
                if (boltzPrewarmJob == currentCoroutineContext()[Job]) {
                    boltzPrewarmJob = null
                }
                return@launchLiquidJob
            }
            try {
                logBoltzTrace(
                    "start",
                    trace,
                    "direction" to direction?.name,
                    "lightningOnly" to lightningOnly,
                    "automatic" to automatic,
                )
                if (lightningOnly) {
                    repository.prewarmBoltzLightningContext()
                } else if (direction != null) {
                    repository.prewarmBoltzChainSwapContext(direction)
                } else {
                    // Keep startup warmup sequential and best-effort.
                    // Parallel async children can surface failures before the parent
                    // handles them, which is too risky for non-critical prewarm work.
                    repository.prewarmBoltzLightningContext()
                    repository.prewarmBoltzChainSwapContext(SwapDirection.BTC_TO_LBTC)
                    repository.prewarmBoltzChainSwapContext(SwapDirection.LBTC_TO_BTC)
                }
            } catch (_: CancellationException) {
                logBoltzTrace(
                    "cancelled",
                    trace,
                    "elapsedMs" to boltzElapsedMs(traceStartedAt),
                    "direction" to direction?.name,
                    "lightningOnly" to lightningOnly,
                    "automatic" to automatic,
                )
            } catch (e: Exception) {
                if (automatic) {
                    lastAutomaticBoltzPrewarmFailureAtMs = nowMs()
                }
                logBoltzTrace(
                    "failed",
                    trace,
                    level = BoltzTraceLevel.WARN,
                    throwable = e,
                    "elapsedMs" to boltzElapsedMs(traceStartedAt),
                    "direction" to direction?.name,
                    "lightningOnly" to lightningOnly,
                    "automatic" to automatic,
                )
                logError("Failed to prewarm Boltz session", e)
            } finally {
                if (currentCoroutineContext().isActive) {
                    logBoltzTrace(
                        "finished",
                        trace,
                        "elapsedMs" to boltzElapsedMs(traceStartedAt),
                        "direction" to direction?.name,
                        "lightningOnly" to lightningOnly,
                        "automatic" to automatic,
                    )
                }
                if (boltzPrewarmJob == currentCoroutineContext()[Job]) {
                    boltzPrewarmJob = null
                }
            }
        }
        boltzPrewarmJob = job
    }

    fun fetchLightningInvoiceLimits() {
        requireLiquidAvailable()
        if (!isBoltzEnabled()) return
        if (_lightningInvoiceLimits.value != null) return
        launchLiquidJob {
            val traceStartedAt = boltzTraceStart()
            val trace =
                BoltzTraceContext(
                    operation = "fetchLightningInvoiceLimitsUi",
                    viaTor = boltzApiSource.value == SecureStorage.BOLTZ_API_TOR,
                    source = "viewmodel",
                )
            logBoltzTrace("start", trace)
            try {
                val limits = repository.getLightningInvoiceLimits()
                _lightningInvoiceLimits.value = limits
                logBoltzTrace(
                    "success",
                    trace,
                    "elapsedMs" to boltzElapsedMs(traceStartedAt),
                    "minimumSats" to limits.minimumSats,
                    "maximumSats" to limits.maximumSats,
                )
            } catch (e: Exception) {
                logBoltzTrace(
                    "failed",
                    trace,
                    level = BoltzTraceLevel.WARN,
                    throwable = e,
                    "elapsedMs" to boltzElapsedMs(traceStartedAt),
                )
                SecureLog.e(TAG, "Failed to fetch Lightning invoice limits: ${e.message}", e)
            }
        }
    }

    fun prepareSwapReview(
        direction: SwapDirection,
        amountSats: Long,
        service: SwapService,
        selectedUtxos: List<UtxoInfo>? = null,
        bitcoinWalletAddress: String? = null,
        destinationAddress: String? = null,
        label: String? = null,
        usesMaxAmount: Boolean = false,
        fundingFeeRateOverride: Double? = null,
        resolveBitcoinMaxSend: suspend (address: String, feeRateSatPerVb: Double, selectedUtxos: List<UtxoInfo>?) -> Long = { _, _, _ ->
            throw Exception("Bitcoin wallet funding is unavailable for max swap calculation")
        },
        previewBitcoinFunding: suspend (address: String, amountSats: Long, feeRateSatPerVb: Double, selectedUtxos: List<UtxoInfo>?, isMaxSend: Boolean) -> DryRunResult? = { _, _, _, _, _ ->
            null
        },
    ) {
        requireLiquidAvailable()
        if (service == SwapService.BOLTZ && !isBoltzEnabled()) {
            _swapState.value = SwapState.Failed("Boltz API is disabled. Lightning swap functionality is unavailable.")
            return
        }
        if (service == SwapService.SIDESWAP && !isSideSwapEnabled()) {
            _swapState.value = SwapState.Failed("SideSwap is disabled in settings")
            return
        }
        _swapState.value = SwapState.PreparingReview
        launchLiquidJob(Dispatchers.IO) {
            reviewPreparationMutex.withLock {
                val selectedOutpoints = selectedUtxos?.map { it.outpoint }.orEmpty()
                val requestKey =
                    buildSwapReviewRequestKey(
                        service = service,
                        direction = direction,
                        amountSats = amountSats,
                        selectedUtxos = selectedUtxos,
                        bitcoinWalletAddress = bitcoinWalletAddress,
                        destinationAddress = destinationAddress,
                        usesMaxAmount = usesMaxAmount,
                        fundingFeeRateOverride = fundingFeeRateOverride,
                    )
                val traceStartedAt = boltzTraceStart()
                val trace =
                    BoltzTraceContext(
                        operation = "prepareSwapReview",
                        requestKey = requestKey,
                        backend = service.name,
                        viaTor = boltzApiSource.value == SecureStorage.BOLTZ_API_TOR,
                        source = "viewmodel",
                    )
                logDebug(
                    "prepareSwapReview start service=$service direction=$direction amount=$amountSats usesMax=$usesMaxAmount " +
                        "requestKey=$requestKey selectedOutpoints=${selectedOutpoints.size}",
                )
                logBoltzTrace(
                    "start",
                    trace,
                    "direction" to direction,
                    "amountSats" to amountSats,
                    "usesMaxAmount" to usesMaxAmount,
                    "selectedOutpoints" to selectedOutpoints.size,
                )
                getPreparedSwap()?.let { existing ->
                    val sameRequest =
                        (existing.requestKey != null && existing.requestKey == requestKey) ||
                            (existing.requestKey == null &&
                                existing.service == service &&
                                existing.direction == direction &&
                                existing.sendAmount == amountSats &&
                                existing.selectedFundingOutpoints == selectedOutpoints)
                    if (sameRequest && canReusePreparedSwapReview(existing, requestKey)) {
                        val relabeled = existing.copy(label = label)
                        logDebug(
                            "prepareSwapReview reusing prepared review swapId=${existing.swapId} requestKey=${existing.requestKey}",
                        )
                        logBoltzTrace(
                            "reuse",
                            trace.copy(swapId = existing.swapId, session = "warm"),
                            "elapsedMs" to boltzElapsedMs(traceStartedAt),
                        )
                        setReviewReady(relabeled)
                        return@withLock
                    }
                    logDebug(
                        if (sameRequest) {
                            "prepareSwapReview clearing stale prepared review swapId=${existing.swapId} requestKey=${existing.requestKey}"
                        } else {
                            "prepareSwapReview clearing existing prepared review swapId=${existing.swapId} requestKey=${existing.requestKey}"
                        },
                    )
                    clearPreparedSwap()
                }
                try {
                    val quote = repository.getSwapQuote(
                        direction = direction,
                        amountSats = amountSats,
                        service = service,
                    )
                    val pendingSwap =
                        when (service) {
                            SwapService.SIDESWAP ->
                                createSideSwapReview(
                                    requestKey = requestKey,
                                    quote = quote,
                                    label = label,
                                    selectedFundingUtxos = selectedUtxos,
                                    bitcoinWalletAddress = bitcoinWalletAddress,
                                    destinationAddress = destinationAddress,
                                    usesMaxAmount = usesMaxAmount,
                                    fundingFeeRateOverride = fundingFeeRateOverride,
                                    resolveBitcoinMaxSend = resolveBitcoinMaxSend,
                                )
                            SwapService.BOLTZ ->
                                createBoltzReview(
                                    requestKey = requestKey,
                                    quote = quote,
                                    label = label,
                                    selectedFundingUtxos = selectedUtxos,
                                    bitcoinWalletAddress = bitcoinWalletAddress,
                                    destinationAddress = destinationAddress,
                                    usesMaxAmount = usesMaxAmount,
                                    fundingFeeRateOverride = fundingFeeRateOverride,
                                    resolveBitcoinMaxSend = resolveBitcoinMaxSend,
                                    previewBitcoinFunding = previewBitcoinFunding,
                                )
                        }
                    logDebug(
                        "prepareSwapReview success service=$service direction=$direction amount=$amountSats " +
                            "swapId=${pendingSwap.swapId} requestKey=${pendingSwap.requestKey}",
                    )
                    logBoltzTrace(
                        "success",
                        trace.copy(swapId = pendingSwap.swapId),
                        "elapsedMs" to boltzElapsedMs(traceStartedAt),
                        "direction" to direction,
                        "amountSats" to amountSats,
                    )
                    setReviewReady(pendingSwap)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logBoltzTrace(
                        "failed",
                        trace,
                        level = BoltzTraceLevel.WARN,
                        throwable = e,
                        "elapsedMs" to boltzElapsedMs(traceStartedAt),
                        "direction" to direction,
                        "amountSats" to amountSats,
                    )
                    logError(
                        "Failed to prepare swap review service=$service direction=$direction amount=$amountSats",
                        e,
                    )
                    val errorMessage = e.message?.takeIf { it.isNotBlank() }
                        ?: "${e.javaClass.simpleName} while preparing swap review"
                    _swapState.value = SwapState.Failed(errorMessage)
                }
            }
        }
    }

    private suspend fun createSideSwapReview(
        requestKey: String,
        quote: SwapQuote,
        label: String?,
        selectedFundingUtxos: List<UtxoInfo>?,
        bitcoinWalletAddress: String?,
        destinationAddress: String?,
        usesMaxAmount: Boolean,
        fundingFeeRateOverride: Double?,
        resolveBitcoinMaxSend: suspend (address: String, feeRateSatPerVb: Double, selectedUtxos: List<UtxoInfo>?) -> Long,
    ): PendingSwapSession {
        val isPegIn = quote.direction == SwapDirection.BTC_TO_LBTC
        val fundingBtcFeeRateOverride = fundingFeeRateOverride.takeIf { isPegIn }
        val fundingLiquidFeeRateOverride = fundingFeeRateOverride.takeIf { !isPegIn }
        val requestedReceiveAddress =
            resolveSideSwapRequestedReceiveAddress(
                direction = quote.direction,
                destinationAddress = destinationAddress,
                bitcoinWalletAddress = bitcoinWalletAddress,
                generatedLiquidAddress = if (isPegIn) repository.getNewAddress() else null,
            )
        val order = if (isPegIn) {
            repository.sideSwapClient.createPegIn(requestedReceiveAddress)
        } else {
            repository.sideSwapClient.createPegOut(requestedReceiveAddress)
        }
        val normalizedCreatedAt = normalizeProviderCreatedAt(order.createdAt)
        val effectiveSendAmount =
            if (quote.direction == SwapDirection.BTC_TO_LBTC) {
                resolveEffectiveSwapSendAmount(
                    quote = quote,
                    depositAddress = order.pegAddress,
                    usesMaxAmount = usesMaxAmount,
                    selectedFundingUtxos = selectedFundingUtxos,
                    fundingBitcoinFeeRateOverride = fundingBtcFeeRateOverride,
                    resolveBitcoinMaxSend = resolveBitcoinMaxSend,
                )
            } else {
                resolveEffectiveLiquidSwapSendAmount(
                    quote = quote,
                    depositAddress = order.pegAddress,
                    usesMaxAmount = usesMaxAmount,
                    selectedFundingUtxos = selectedFundingUtxos,
                    fundingLiquidFeeRateOverride = fundingLiquidFeeRateOverride,
                )
            }
        val effectiveQuote =
            if (effectiveSendAmount != quote.sendAmount) {
                repository.getSwapQuote(
                    direction = quote.direction,
                    amountSats = effectiveSendAmount,
                    service = quote.service,
                )
            } else {
                quote
            }
        return PendingSwapSession(
            service = effectiveQuote.service,
            direction = effectiveQuote.direction,
            sendAmount = effectiveQuote.sendAmount,
            requestKey = requestKey,
            label = label,
            usesMaxAmount = usesMaxAmount,
            selectedFundingOutpoints = selectedFundingUtxos?.map { it.outpoint }.orEmpty(),
            estimatedTerms =
                run {
                    val fundingFee =
                        when (effectiveQuote.direction) {
                            SwapDirection.BTC_TO_LBTC ->
                                adjustedFundingNetworkFee(
                                    quotedFeeSats = effectiveQuote.btcNetworkFee,
                                    quotedFeeRate = effectiveQuote.btcFeeRate,
                                    feeRateOverride = fundingBtcFeeRateOverride,
                                ) ?: effectiveQuote.btcNetworkFee
                            SwapDirection.LBTC_TO_BTC ->
                                adjustedFundingNetworkFee(
                                    quotedFeeSats = effectiveQuote.liquidNetworkFee,
                                    quotedFeeRate = effectiveQuote.liquidFeeRate,
                                    feeRateOverride = fundingLiquidFeeRateOverride,
                                ) ?: effectiveQuote.liquidNetworkFee
                        }
                    val fundingFeeRate =
                        when (effectiveQuote.direction) {
                            SwapDirection.BTC_TO_LBTC -> fundingBtcFeeRateOverride ?: effectiveQuote.btcFeeRate
                            SwapDirection.LBTC_TO_BTC -> fundingLiquidFeeRateOverride ?: effectiveQuote.liquidFeeRate
                        }
                    val payoutNetworkFee =
                        when (effectiveQuote.direction) {
                            SwapDirection.BTC_TO_LBTC -> effectiveQuote.liquidNetworkFee
                            SwapDirection.LBTC_TO_BTC -> effectiveQuote.btcNetworkFee
                        }
                    val payoutFeeRate =
                        when (effectiveQuote.direction) {
                            SwapDirection.BTC_TO_LBTC -> effectiveQuote.liquidFeeRate
                            SwapDirection.LBTC_TO_BTC -> effectiveQuote.btcFeeRate
                        }
                    EstimatedSwapTerms(
                        receiveAmount = effectiveQuote.receiveAmount,
                        serviceFee = effectiveQuote.serviceFee,
                        btcNetworkFee =
                            adjustedFundingNetworkFee(
                                quotedFeeSats = effectiveQuote.btcNetworkFee,
                                quotedFeeRate = effectiveQuote.btcFeeRate,
                                feeRateOverride = fundingBtcFeeRateOverride,
                            ) ?: effectiveQuote.btcNetworkFee,
                        liquidNetworkFee =
                            adjustedFundingNetworkFee(
                                quotedFeeSats = effectiveQuote.liquidNetworkFee,
                                quotedFeeRate = effectiveQuote.liquidFeeRate,
                                feeRateOverride = fundingLiquidFeeRateOverride,
                            ) ?: effectiveQuote.liquidNetworkFee,
                        providerMinerFee = effectiveQuote.providerMinerFee,
                        btcFeeRate = fundingBtcFeeRateOverride ?: effectiveQuote.btcFeeRate,
                        displayLiquidFeeRate = fundingLiquidFeeRateOverride ?: effectiveQuote.liquidFeeRate,
                        fundingNetworkFee = fundingFee,
                        fundingFeeRate = fundingFeeRate,
                        payoutNetworkFee = payoutNetworkFee,
                        payoutFeeRate = payoutFeeRate,
                        minAmount = effectiveQuote.minAmount,
                        maxAmount = effectiveQuote.maxAmount,
                        estimatedTime = effectiveQuote.estimatedTime,
                    )
                },
            swapId = order.orderId,
            depositAddress = order.pegAddress,
            receiveAddress = requestedReceiveAddress,
            createdAt = normalizedCreatedAt,
            reviewExpiresAt = normalizedCreatedAt + REVIEW_ORDER_VALIDITY_MS,
            phase = PendingSwapPhase.REVIEW,
            status = "Exact SideSwap order prepared. Funding not started yet.",
            fundingLiquidFeeRate = fundingLiquidFeeRateOverride ?: effectiveQuote.liquidFeeRate,
        )
    }

    private suspend fun createBoltzReview(
        requestKey: String,
        quote: SwapQuote,
        label: String?,
        selectedFundingUtxos: List<UtxoInfo>?,
        bitcoinWalletAddress: String?,
        destinationAddress: String?,
        usesMaxAmount: Boolean,
        fundingFeeRateOverride: Double?,
        resolveBitcoinMaxSend: suspend (address: String, feeRateSatPerVb: Double, selectedUtxos: List<UtxoInfo>?) -> Long,
        previewBitcoinFunding: suspend (address: String, amountSats: Long, feeRateSatPerVb: Double, selectedUtxos: List<UtxoInfo>?, isMaxSend: Boolean) -> DryRunResult?,
    ): PendingSwapSession {
        val reviewStartedAt = nowMs()
        val fundingBtcFeeRateOverride = fundingFeeRateOverride.takeIf { quote.direction == SwapDirection.BTC_TO_LBTC }
        val fundingLiquidFeeRateOverride = fundingFeeRateOverride.takeIf { quote.direction == SwapDirection.LBTC_TO_BTC }
        logDebug(
            "createBoltzReview start requestKey=$requestKey direction=${quote.direction} " +
                "quotedSend=${quote.sendAmount} usesMax=$usesMaxAmount selectedOutpoints=${selectedFundingUtxos?.size ?: 0}",
        )
        var createdSwapId: String? = null
        var releaseReason = "review_not_created"
        try {
            val addressPlan =
                resolveBoltzAddressPlan(
                    direction = quote.direction,
                    destinationAddress = destinationAddress,
                    bitcoinWalletAddress = bitcoinWalletAddress,
                )
            val btcAddress = addressPlan.providerBitcoinAddress
            val liquidDestinationAddress = addressPlan.liquidDestinationOverride
            val effectiveSendAmount =
                resolveEffectiveSwapSendAmount(
                    quote = quote,
                    depositAddress = btcAddress,
                    usesMaxAmount = usesMaxAmount,
                    selectedFundingUtxos = selectedFundingUtxos,
                fundingBitcoinFeeRateOverride = fundingBtcFeeRateOverride,
                    resolveBitcoinMaxSend = resolveBitcoinMaxSend,
                )
            var reviewQuote =
                if (effectiveSendAmount != quote.sendAmount) {
                    logDebug(
                        "createBoltzReview requoting requestKey=$requestKey oldSend=${quote.sendAmount} newSend=$effectiveSendAmount",
                    )
                    repository.getSwapQuote(
                        direction = quote.direction,
                        amountSats = effectiveSendAmount,
                        service = quote.service,
                    )
                } else {
                    quote
                }
            logDebug(
                "createBoltzReview effective quote requestKey=$requestKey send=${reviewQuote.sendAmount} " +
                    "receive=${reviewQuote.receiveAmount} btcFee=${reviewQuote.btcNetworkFee} liquidFee=${reviewQuote.liquidNetworkFee}",
            )
            logDebug("createBoltzReview quote stage requestKey=$requestKey elapsedMs=${nowMs() - reviewStartedAt}")

            var reviewDraft: BoltzChainSwapDraft? = null
            var fundingPreview: BoltzChainFundingPreview? = null
            for (attempt in 0 until 2) {
                val createStartedAt = nowMs()
                val createdDraft =
                    createBoltzDraftForQuote(
                        requestKey = requestKey,
                        quote = reviewQuote,
                        selectedFundingUtxos = selectedFundingUtxos,
                        btcAddress = btcAddress,
                        liquidDestinationAddress = liquidDestinationAddress,
                        usesMaxAmount = usesMaxAmount,
                        fundingBtcFeeRateOverride = fundingBtcFeeRateOverride,
                        fundingLiquidFeeRateOverride = fundingLiquidFeeRateOverride,
                    )
                reviewDraft = createdDraft
                createdSwapId = createdDraft.swapId
                logDebug(
                    "createBoltzReview draft created requestKey=$requestKey swapId=${createdDraft.swapId} " +
                        "state=${createdDraft.state} lockup=${summarizeValue(createdDraft.depositAddress)}",
                )
                logDebug(
                    "createBoltzReview create stage requestKey=$requestKey swapId=${createdDraft.swapId} " +
                        "elapsedMs=${nowMs() - createStartedAt}",
                )
                val lockupNormalizedAmount =
                    resolveEffectiveLiquidSwapSendAmount(
                        quote = reviewQuote,
                        depositAddress = createdDraft.depositAddress ?: throw Exception("Boltz returned an empty lockup address"),
                        usesMaxAmount = usesMaxAmount,
                        selectedFundingUtxos = selectedFundingUtxos,
                        fundingLiquidFeeRateOverride = fundingLiquidFeeRateOverride,
                    )
                if (lockupNormalizedAmount != reviewQuote.sendAmount) {
                    logWarn(
                        "createBoltzReview max normalized from Liquid lockup requestKey=$requestKey swapId=${createdDraft.swapId} " +
                            "quoted=${reviewQuote.sendAmount} verified=$lockupNormalizedAmount",
                        Exception("Boltz max amount changed after Liquid lockup resolution"),
                    )
                    if (attempt == 1) {
                        throw Exception("Unable to lock the max Boltz review to a stable exact amount. Review again.")
                    }
                    repository.discardPreparedBoltzChainSwap(createdDraft.swapId)
                    repository.releasePreparedBoltzChainSwapRuntime(createdDraft.swapId, "max_lockup_recreate")
                    createdSwapId = null
                    reviewQuote =
                        repository.getSwapQuote(
                            direction = reviewQuote.direction,
                            amountSats = lockupNormalizedAmount,
                            service = reviewQuote.service,
                        )
                    logDebug(
                        "createBoltzReview recreated lockup quote requestKey=$requestKey send=${reviewQuote.sendAmount} " +
                            "receive=${reviewQuote.receiveAmount} btcFee=${reviewQuote.btcNetworkFee} liquidFee=${reviewQuote.liquidNetworkFee}",
                    )
                    continue
                }
                val fundingPreviewStartedAt = nowMs()
                val currentFundingPreview =
                    buildBoltzChainFundingPreview(
                        direction = reviewQuote.direction,
                        pendingDraft = createdDraft,
                        selectedFundingUtxos = selectedFundingUtxos,
                        previewBitcoinFunding = previewBitcoinFunding,
                    )
                fundingPreview = currentFundingPreview
                logDebug(
                    "createBoltzReview funding preview requestKey=$requestKey swapId=${createdDraft.swapId} " +
                        "fee=${currentFundingPreview?.feeSats} vbytes=${currentFundingPreview?.txVBytes} " +
                        "effectiveFeeRate=${currentFundingPreview?.effectiveFeeRate} " +
                        "verifiedAmount=${currentFundingPreview?.verifiedRecipientAmountSats}",
                )
                logDebug(
                    "createBoltzReview funding preview stage requestKey=$requestKey swapId=${createdDraft.swapId} " +
                        "elapsedMs=${nowMs() - fundingPreviewStartedAt}",
                )
                val recreatedAmount =
                    getRecreatedBoltzMaxAmount(
                        quote = reviewQuote,
                        fundingPreview = currentFundingPreview,
                        usesMaxAmount = usesMaxAmount,
                    )
                if (recreatedAmount == null) {
                    break
                }
                logWarn(
                    "createBoltzReview max normalized requestKey=$requestKey swapId=${createdDraft.swapId} " +
                        "quoted=${reviewQuote.sendAmount} verified=$recreatedAmount",
                    Exception("Boltz max amount changed after funding preview"),
                )
                if (attempt == 1) {
                    throw Exception("Unable to lock the max Boltz review to a stable exact amount. Review again.")
                }
                repository.discardPreparedBoltzChainSwap(createdDraft.swapId)
                repository.releasePreparedBoltzChainSwapRuntime(createdDraft.swapId, "max_normalized_recreate")
                createdSwapId = null
                reviewQuote =
                    repository.getSwapQuote(
                        direction = reviewQuote.direction,
                        amountSats = recreatedAmount,
                        service = reviewQuote.service,
                    )
                logDebug(
                    "createBoltzReview recreated quote requestKey=$requestKey send=${reviewQuote.sendAmount} " +
                        "receive=${reviewQuote.receiveAmount} btcFee=${reviewQuote.btcNetworkFee} liquidFee=${reviewQuote.liquidNetworkFee}",
                )
            }

            val finalizedDraft = reviewDraft ?: throw Exception("Boltz swap review was not created")
            val finalFundingPreview = fundingPreview
            val maxOrderAmountVerified =
                if (usesMaxAmount) {
                    val verifiedAmount = finalFundingPreview?.verifiedRecipientAmountSats?.takeIf { it > 0L }
                    finalizedDraft.orderAmountSats == reviewQuote.sendAmount &&
                        (verifiedAmount == null || verifiedAmount == reviewQuote.sendAmount)
                } else {
                    false
                }
            val reviewReadyDraft =
                repository.finalizeBoltzChainSwapReviewDraft(
                    draft =
                        finalizedDraft.copy(
                            sendAmount = reviewQuote.sendAmount,
                            maxOrderAmountVerified = maxOrderAmountVerified,
                            estimatedTerms =
                                buildBoltzReviewTerms(
                                    quote = reviewQuote,
                                    fundingPreview = finalFundingPreview,
                                    fundingBtcFeeRateOverride = fundingBtcFeeRateOverride,
                                    fundingLiquidFeeRateOverride = fundingLiquidFeeRateOverride,
                                ),
                            fundingLiquidFeeRate =
                                if (reviewQuote.direction == SwapDirection.LBTC_TO_BTC) {
                                    finalFundingPreview?.effectiveFeeRate?.takeIf { it > 0.0 }
                                        ?: fundingLiquidFeeRateOverride
                                        ?: reviewQuote.liquidFeeRate
                                } else {
                                    reviewQuote.liquidFeeRate
                                },
                        ),
                    fundingPreview = finalFundingPreview,
                    reviewExpiresAt = nowMs() + REVIEW_ORDER_VALIDITY_MS,
                )
            repository.saveBoltzChainSwapDraft(reviewReadyDraft)
            val reviewSession =
                repository.boltzChainSwapDraftToPendingSession(reviewReadyDraft).copy(
                    label = label,
                    status = "Exact Boltz order prepared. Funding not started yet.",
                    actualFundingFeeSats = finalFundingPreview?.feeSats,
                    actualFundingTxVBytes = finalFundingPreview?.txVBytes,
                )
            if (getBoltzMaxReviewMismatch(reviewSession) != null) {
                repository.discardPreparedBoltzChainSwap(reviewReadyDraft.swapId)
                throw Exception("Unable to prepare a consistent max Boltz review. Review again.")
            }
            logDebug(
                "createBoltzReview review ready requestKey=$requestKey swapId=${reviewReadyDraft.swapId} " +
                    "reviewFee=${finalFundingPreview?.feeSats} reviewVbytes=${finalFundingPreview?.txVBytes}",
            )
            releaseReason = "review_ready"
            logDebug(
                "createBoltzReview total requestKey=$requestKey swapId=${reviewReadyDraft.swapId} " +
                    "totalElapsedMs=${nowMs() - reviewStartedAt}",
            )
            return reviewSession
        } finally {
            if (!createdSwapId.isNullOrBlank()) {
                repository.releasePreparedBoltzChainSwapRuntime(createdSwapId, releaseReason)
            }
        }
    }

    private suspend fun buildBoltzChainFundingPreview(
        direction: SwapDirection,
        pendingDraft: BoltzChainSwapDraft,
        selectedFundingUtxos: List<UtxoInfo>?,
        previewBitcoinFunding: suspend (address: String, amountSats: Long, feeRateSatPerVb: Double, selectedUtxos: List<UtxoInfo>?, isMaxSend: Boolean) -> DryRunResult?,
    ): BoltzChainFundingPreview? {
        val lockupAddress = pendingDraft.depositAddress ?: return null
        logDebug(
            "buildBoltzChainFundingPreview start swapId=${pendingDraft.swapId} direction=$direction " +
                "amount=${pendingDraft.sendAmount} usesMax=${pendingDraft.usesMaxAmount} lockup=${summarizeValue(lockupAddress)}",
        )
        return when (direction) {
            SwapDirection.BTC_TO_LBTC -> {
                val feeRate = pendingDraft.estimatedTerms.btcFeeRate.takeIf { it > 0.0 } ?: 1.0
                val preview =
                    (
                        previewBitcoinFunding(
                            lockupAddress,
                            pendingDraft.sendAmount,
                            feeRate,
                            selectedFundingUtxos,
                            resolveBoltzReviewFundingPreviewIsMaxSend(pendingDraft.usesMaxAmount),
                        ) ?: return null
                        ).let { exactPreview ->
                        if (!exactPreview.isError) {
                            exactPreview
                        } else if (!shouldFallbackToBoltzMaxFundingPreview(direction, pendingDraft.usesMaxAmount, Exception(exactPreview.error))) {
                            exactPreview
                        } else {
                            logWarn(
                                "buildBoltzChainFundingPreview BTC exact preview shortfall swapId=${pendingDraft.swapId} " +
                                    "amount=${pendingDraft.sendAmount} feeRate=$feeRate",
                                Exception(exactPreview.error ?: "BTC funding preview failed"),
                            )
                            previewBitcoinFunding(
                                lockupAddress,
                                pendingDraft.sendAmount,
                                feeRate,
                                selectedFundingUtxos,
                                true,
                            ) ?: exactPreview
                        }
                    }
                if (preview.isError) {
                    logWarn(
                        "buildBoltzChainFundingPreview BTC preview error swapId=${pendingDraft.swapId} error=${preview.error}",
                        Exception(preview.error ?: "BTC funding preview failed"),
                    )
                    throw Exception(preview.error ?: "Unable to build the exact BTC funding transaction for this swap.")
                }
                logDebug(
                    "buildBoltzChainFundingPreview BTC preview success swapId=${pendingDraft.swapId} " +
                        "fee=${preview.feeSats} vbytes=${preview.txVBytes} recipient=${preview.recipientAmountSats}",
                )
                BoltzChainFundingPreview(
                    feeSats = preview.feeSats,
                    txVBytes = preview.txVBytes,
                    effectiveFeeRate = preview.effectiveFeeRate,
                    verifiedRecipientAmountSats = preview.recipientAmountSats,
                    error = preview.error,
                )
            }
            SwapDirection.LBTC_TO_BTC -> {
                val feePreview =
                    try {
                        repository.previewLbtcSend(
                            address = lockupAddress,
                            amountSats = pendingDraft.sendAmount,
                            feeRateSatPerVb = pendingDraft.fundingLiquidFeeRate.takeIf { it > 0.0 } ?: 0.1,
                            selectedUtxos = selectedFundingUtxos,
                            isMaxSend = resolveBoltzReviewFundingPreviewIsMaxSend(pendingDraft.usesMaxAmount),
                        )
                    } catch (error: Exception) {
                        if (!shouldFallbackToBoltzMaxFundingPreview(direction, pendingDraft.usesMaxAmount, error)) {
                            throw error
                        }
                        logWarn(
                            "buildBoltzChainFundingPreview L-BTC exact preview shortfall swapId=${pendingDraft.swapId} " +
                                "amount=${pendingDraft.sendAmount} feeRate=${pendingDraft.fundingLiquidFeeRate}",
                            error,
                        )
                        repository.previewLbtcSend(
                            address = lockupAddress,
                            amountSats = pendingDraft.sendAmount,
                            feeRateSatPerVb = pendingDraft.fundingLiquidFeeRate.takeIf { it > 0.0 } ?: 0.1,
                            selectedUtxos = selectedFundingUtxos,
                            isMaxSend = true,
                        )
                    }
                logDebug(
                    "buildBoltzChainFundingPreview L-BTC preview success swapId=${pendingDraft.swapId} " +
                        "fee=${feePreview.feeSats} vbytes=${feePreview.txVBytes} recipient=${feePreview.totalRecipientSats}",
                )
                BoltzChainFundingPreview(
                    feeSats = feePreview.feeSats ?: 0L,
                    txVBytes = feePreview.txVBytes ?: 0.0,
                    effectiveFeeRate = feePreview.feeRate,
                    verifiedRecipientAmountSats = feePreview.totalRecipientSats ?: pendingDraft.sendAmount,
                )
            }
        }
    }

    private suspend fun resolveEffectiveSwapSendAmount(
        quote: SwapQuote,
        depositAddress: String,
        usesMaxAmount: Boolean,
        selectedFundingUtxos: List<UtxoInfo>?,
        fundingBitcoinFeeRateOverride: Double?,
        resolveBitcoinMaxSend: suspend (address: String, feeRateSatPerVb: Double, selectedUtxos: List<UtxoInfo>?) -> Long,
    ): Long {
        if (!usesMaxAmount) return quote.sendAmount
        if (quote.direction != SwapDirection.BTC_TO_LBTC) {
            return quote.sendAmount
        }

        val feeRate = fundingBitcoinFeeRateOverride ?: quote.btcFeeRate.takeIf { it > 0.0 } ?: 1.0
        val effectiveSendAmount = resolveBitcoinMaxSend(depositAddress, feeRate, selectedFundingUtxos)

        if (effectiveSendAmount <= 0L) {
            throw Exception("Unable to compute the max spendable amount for this swap.")
        }

        return effectiveSendAmount
    }

    private suspend fun resolveEffectiveLiquidSwapSendAmount(
        quote: SwapQuote,
        depositAddress: String,
        usesMaxAmount: Boolean,
        selectedFundingUtxos: List<UtxoInfo>?,
        fundingLiquidFeeRateOverride: Double?,
    ): Long {
        if (!usesMaxAmount) return quote.sendAmount
        if (quote.direction != SwapDirection.LBTC_TO_BTC) {
            return quote.sendAmount
        }

        val feeRate = fundingLiquidFeeRateOverride ?: quote.liquidFeeRate.takeIf { it > 0.0 } ?: 0.1
        val effectiveSendAmount =
            repository.previewLbtcSend(
                address = depositAddress,
                amountSats = quote.sendAmount,
                feeRateSatPerVb = feeRate,
                selectedUtxos = selectedFundingUtxos,
                isMaxSend = true,
            ).totalRecipientSats ?: 0L

        if (effectiveSendAmount <= 0L) {
            throw Exception("Unable to compute the max spendable amount for this swap.")
        }

        return effectiveSendAmount
    }

    fun executeSwap(
        pendingSwap: PendingSwapSession,
        selectedUtxos: List<UtxoInfo>? = null,
        sendBitcoinFunding: suspend (address: String, amountSats: Long, feeRateSatPerVb: Double, selectedUtxos: List<UtxoInfo>?, isMaxSend: Boolean) -> Pair<String, Long>,
    ) {
        requireLiquidAvailable()
        launchLiquidJob {
            swapExecutionMutex.withLock {
                try {
                    val persisted = getPreparedSwap()
                        ?.takeIf { it.swapId == pendingSwap.swapId }
                        ?: return@withLock
                    _swapState.value = SwapState.PreparingReview
                    val validated = validatePendingSwapForExecution(persisted)
                    val reviewedSelectedUtxos = ensureSwapFundingSelectionMatchesReview(validated, selectedUtxos)
                    val active =
                        when (validated.service) {
                            SwapService.SIDESWAP -> executeSideSwapPeg(
                                validated,
                                reviewedSelectedUtxos,
                                sendBitcoinFunding,
                            )
                            SwapService.BOLTZ -> executeBoltzChainSwap(
                                validated,
                                reviewedSelectedUtxos,
                                sendBitcoinFunding,
                            )
                        }
                    clearPreparedSwap()
                    upsertPendingSwap(active)
                    _swapState.value = SwapState.Idle
                    startSwapMonitor(active)
                } catch (e: Exception) {
                    logError("Failed to execute swap", e)
                    val prepared = getPreparedSwap()
                    if (prepared != null && prepared.swapId == pendingSwap.swapId) {
                        _swapState.value = SwapState.ReviewReady(prepared)
                        _events.emit(LiquidEvent.Error(e.message ?: "Swap execution blocked"))
                    } else {
                        val tracked = getTrackedPendingSwap(pendingSwap.swapId)
                        if (tracked != null) {
                            val status = pendingSwapStatus(tracked)
                            startSwapMonitor(setSwapInProgress(tracked, status, phase = tracked.phase))
                        } else {
                            _swapState.value = SwapState.Failed(e.message ?: "Swap failed")
                        }
                    }
                }
            }
        }
    }

    private fun ensureSwapFundingSelectionMatchesReview(
        pendingSwap: PendingSwapSession,
        selectedUtxos: List<UtxoInfo>?,
    ): List<UtxoInfo>? {
        val reviewedOutpoints = pendingSwap.selectedFundingOutpoints
        val currentOutpoints = selectedUtxos?.map { it.outpoint }.orEmpty()
        if (reviewedOutpoints != currentOutpoints) {
            throw Exception("Funding selection changed. Create a new review before funding.")
        }
        return selectedUtxos?.takeIf { it.isNotEmpty() }
    }

    private suspend fun executeSideSwapPeg(
        pendingSwap: PendingSwapSession,
        selectedUtxos: List<UtxoInfo>?,
        sendBitcoinFunding: suspend (address: String, amountSats: Long, feeRateSatPerVb: Double, selectedUtxos: List<UtxoInfo>?, isMaxSend: Boolean) -> Pair<String, Long>,
    ): PendingSwapSession {
        val isPegIn = pendingSwap.direction == SwapDirection.BTC_TO_LBTC
        val btcFeeRate = pendingSwap.estimatedTerms.btcFeeRate.takeIf { it > 0.0 } ?: 1.0
        val liquidFeeRate = pendingSwap.fundingLiquidFeeRate.takeIf { it > 0.0 } ?: 0.1
        return if (isPegIn) {
            val fundingState =
                pendingSwap.copy(
                    phase = PendingSwapPhase.FUNDING,
                    status = "Sending BTC to: ${pendingSwap.depositAddress}",
                )
            val fundingRequest = buildBitcoinSwapFundingRequest(fundingState, btcFeeRate)
            val (fundingTxid, actualSendAmount) = sendBitcoinFunding(
                fundingRequest.recipientAddress,
                fundingRequest.amountSats,
                fundingRequest.feeRateSatPerVb,
                selectedUtxos,
                fundingRequest.isMaxSend,
            )
            val active =
                fundingState.copy(
                    fundingTxid = fundingTxid,
                    sendAmount = actualSendAmount,
                    phase = PendingSwapPhase.IN_PROGRESS,
                    status = "Broadcasted peg-in funding transaction, waiting for SideSwap payout...",
                )
            saveChainSwapBitcoinTxDetails(
                txid = fundingTxid,
                pendingSwap = active,
                role = LiquidSwapTxRole.FUNDING,
            )
            active.also(::applySwapTransactionLabels)
        } else {
            val fundingState =
                pendingSwap.copy(
                    phase = PendingSwapPhase.FUNDING,
                    status = "Sending L-BTC to: ${pendingSwap.depositAddress}",
                )
            val fundingRequest = buildLiquidSwapFundingRequest(fundingState, liquidFeeRate)
            if (fundingRequest.isMaxSend) {
                ensureLbtcMaxSwapFundingMatchesReview(
                    fundingRequest.recipientAddress,
                    fundingRequest.amountSats,
                    fundingRequest.feeRateSatPerVb,
                    selectedUtxos,
                )
            } else {
                ensureLbtcSwapFundingFits(
                    fundingRequest.recipientAddress,
                    fundingRequest.amountSats,
                    fundingRequest.feeRateSatPerVb,
                    selectedUtxos,
                )
            }
            val fundingTxid = repository.sendLBTC(
                address = fundingRequest.recipientAddress,
                amountSats = fundingRequest.amountSats,
                feeRateSatPerVb = fundingRequest.feeRateSatPerVb,
                selectedUtxos = selectedUtxos,
                isMaxSend = fundingRequest.isMaxSend,
            )
            repository.saveLiquidTransactionSource(
                txid = fundingTxid,
                source = LiquidTxSource.CHAIN_SWAP,
            )
            saveChainSwapLiquidTxDetails(
                txid = fundingTxid,
                pendingSwap = fundingState,
                role = LiquidSwapTxRole.FUNDING,
            )
            fundingState.copy(
                fundingTxid = fundingTxid,
                phase = PendingSwapPhase.IN_PROGRESS,
                status = "Broadcasted peg-out funding transaction, waiting for SideSwap payout...",
            ).also(::applySwapTransactionLabels)
        }
    }

    private suspend fun executeBoltzChainSwap(
        pendingSwap: PendingSwapSession,
        selectedUtxos: List<UtxoInfo>?,
        sendBitcoinFunding: suspend (address: String, amountSats: Long, feeRateSatPerVb: Double, selectedUtxos: List<UtxoInfo>?, isMaxSend: Boolean) -> Pair<String, Long>,
    ): PendingSwapSession {
        val isBtcToLbtc = pendingSwap.direction == SwapDirection.BTC_TO_LBTC
        val btcFeeRate = pendingSwap.estimatedTerms.btcFeeRate.takeIf { it > 0.0 } ?: 1.0
        val liquidFeeRate = pendingSwap.fundingLiquidFeeRate.takeIf { it > 0.0 } ?: 0.1
        logDebug(
            "executeBoltzChainSwap start swapId=${pendingSwap.swapId} direction=${pendingSwap.direction} " +
                "sendAmount=${pendingSwap.sendAmount} usesMax=${pendingSwap.usesMaxAmount} selectedUtxos=${selectedUtxos?.size ?: 0}",
        )
        return if (isBtcToLbtc) {
            val fundingState =
                pendingSwap.copy(
                    phase = PendingSwapPhase.FUNDING,
                    status = "Sending BTC to Boltz lockup address...",
                )
            val fundingRequest = buildBitcoinSwapFundingRequest(fundingState, btcFeeRate)
            val (fundingTxid, actualSendAmount) = sendBitcoinFunding(
                fundingRequest.recipientAddress,
                fundingRequest.amountSats,
                fundingRequest.feeRateSatPerVb,
                selectedUtxos,
                fundingRequest.isMaxSend,
            )
            val active =
                fundingState.copy(
                    fundingTxid = fundingTxid,
                    sendAmount = actualSendAmount,
                    phase = PendingSwapPhase.IN_PROGRESS,
                    status = "Broadcasted chain swap funding transaction, waiting for payout...",
                )
            saveChainSwapBitcoinTxDetails(
                txid = fundingTxid,
                pendingSwap = active,
                role = LiquidSwapTxRole.FUNDING,
            )
            applySwapTransactionLabels(active)
            logDebug(
                "executeBoltzChainSwap BTC funding broadcast swapId=${active.swapId} fundingTxid=${summarizeValue(fundingTxid)} " +
                    "actualSend=$actualSendAmount",
            )
            repository.syncBoltzChainSwapDraftFromPendingSwap(
                active,
                state = github.aeonbtc.ibiswallet.data.model.BoltzChainSwapDraftState.FUNDING_BROADCAST,
            )
            active
        } else {
            val fundingState =
                pendingSwap.copy(
                    phase = PendingSwapPhase.FUNDING,
                    status = "Sending L-BTC to Boltz lockup address...",
                )
            val fundingRequest = buildLiquidSwapFundingRequest(fundingState, liquidFeeRate)
            if (fundingRequest.isMaxSend) {
                ensureLbtcMaxSwapFundingMatchesReview(
                    fundingRequest.recipientAddress,
                    fundingRequest.amountSats,
                    fundingRequest.feeRateSatPerVb,
                    selectedUtxos,
                )
            } else {
                ensureLbtcSwapFundingFits(
                    fundingRequest.recipientAddress,
                    fundingRequest.amountSats,
                    fundingRequest.feeRateSatPerVb,
                    selectedUtxos,
                )
            }
            val fundingTxid = repository.sendLBTC(
                address = fundingRequest.recipientAddress,
                amountSats = fundingRequest.amountSats,
                feeRateSatPerVb = fundingRequest.feeRateSatPerVb,
                selectedUtxos = selectedUtxos,
                isMaxSend = fundingRequest.isMaxSend,
            )
            repository.saveLiquidTransactionSource(
                txid = fundingTxid,
                source = LiquidTxSource.CHAIN_SWAP,
            )
            saveChainSwapLiquidTxDetails(
                txid = fundingTxid,
                pendingSwap = fundingState,
                role = LiquidSwapTxRole.FUNDING,
            )
            val active =
                fundingState.copy(
                    fundingTxid = fundingTxid,
                    phase = PendingSwapPhase.IN_PROGRESS,
                    status = "Broadcasted chain swap funding transaction, waiting for payout...",
                )
            applySwapTransactionLabels(active)
            logDebug(
                "executeBoltzChainSwap L-BTC funding broadcast swapId=${active.swapId} fundingTxid=${summarizeValue(fundingTxid)}",
            )
            repository.syncBoltzChainSwapDraftFromPendingSwap(
                active,
                state = github.aeonbtc.ibiswallet.data.model.BoltzChainSwapDraftState.FUNDING_BROADCAST,
            )
            active
        }
    }

    private suspend fun ensureLbtcSwapFundingFits(
        address: String,
        sendAmountSats: Long,
        feeRateSatPerVb: Double,
        selectedUtxos: List<UtxoInfo>? = null,
    ) {
        val maxSpendable = repository.getMaxSpendableLbtc(address, feeRateSatPerVb, selectedUtxos)
        if (sendAmountSats <= maxSpendable) return

        val exactFee = if (maxSpendable > 0) {
            repository.estimateLbtcSendFee(address, maxSpendable, feeRateSatPerVb, selectedUtxos)
        } else {
            0L
        }
        throw Exception(
            "Insufficient L-BTC. At the current Liquid fee rate, max deposit is $maxSpendable sats and exact fee is $exactFee sats.",
        )
    }

    private suspend fun ensureLbtcMaxSwapFundingMatchesReview(
        address: String,
        reviewedAmountSats: Long,
        feeRateSatPerVb: Double,
        selectedUtxos: List<UtxoInfo>? = null,
    ) {
        val currentMaxPreview =
            repository.previewLbtcSend(
                address = address,
                amountSats = reviewedAmountSats,
                feeRateSatPerVb = feeRateSatPerVb,
                selectedUtxos = selectedUtxos,
                isMaxSend = true,
            )
        val currentMaxAmount = currentMaxPreview.totalRecipientSats ?: 0L
        if (currentMaxAmount == reviewedAmountSats) return
        throw Exception(
            "This max Liquid swap review is stale. Current max deposit is $currentMaxAmount sats at the selected fee rate. Review again.",
        )
    }

    private suspend fun startSwapMonitor(pendingSwap: PendingSwapSession) {
        stopSwapMonitorAndJoin(pendingSwap.swapId)
        val job =
            launchLiquidJob monitor@{
                when (pendingSwap.service) {
                    SwapService.SIDESWAP -> monitorSideSwap(pendingSwap)
                    SwapService.BOLTZ -> monitorBoltzChainSwap(pendingSwap)
                }
            }
        synchronized(swapMonitorJobs) {
            swapMonitorJobs[pendingSwap.swapId] = job
        }
        job.invokeOnCompletion {
            synchronized(swapMonitorJobs) {
                if (swapMonitorJobs[pendingSwap.swapId] === job) {
                    swapMonitorJobs.remove(pendingSwap.swapId)
                }
            }
        }
    }

    private suspend fun monitorSideSwap(pendingSwap: PendingSwapSession) {
        var active = pendingSwap
        val isPegIn = active.direction == SwapDirection.BTC_TO_LBTC
        while (true) {
            try {
                val status = repository.sideSwapClient.getPegStatus(active.swapId, isPegIn)
                val completedTx =
                    status.transactions.firstOrNull {
                        it.txState.equals("Done", ignoreCase = true)
                    }
                if (completedTx != null) {
                    val settlementTxid = completedTx.payoutTxid?.takeIf { it.isNotBlank() }
                    applySwapTransactionLabels(active.copy(settlementTxid = settlementTxid))
                    removeTrackedPendingSwap(active.swapId)
                    if (settlementTxid != null) {
                        if (active.direction == SwapDirection.BTC_TO_LBTC) {
                            repository.saveLiquidTransactionSource(
                                txid = settlementTxid,
                                source = LiquidTxSource.CHAIN_SWAP,
                            )
                            saveChainSwapLiquidTxDetails(
                                txid = settlementTxid,
                                pendingSwap = active,
                                role = LiquidSwapTxRole.SETTLEMENT,
                            )
                        } else {
                            saveChainSwapBitcoinTxDetails(
                                txid = settlementTxid,
                                pendingSwap = active,
                                role = LiquidSwapTxRole.SETTLEMENT,
                            )
                        }
                    }
                    _events.emit(
                        LiquidEvent.SwapCompleted(
                            swapId = active.swapId,
                            settlementTxid = settlementTxid,
                            fundingTxid = active.fundingTxid,
                        ),
                    )
                    runCatching { repository.sync() }
                    return
                }

                val failedTx =
                    status.transactions.lastOrNull {
                        it.txState.equals("Failed", ignoreCase = true) ||
                            it.status.contains("failed", ignoreCase = true)
                    }
                if (failedTx != null) {
                    removeTrackedPendingSwap(active.swapId)
                    _events.emit(
                        LiquidEvent.Error(
                            failedTx.status.ifBlank {
                                "SideSwap swap failed. Keep the order details for support or refund tracking."
                            },
                        ),
                    )
                    return
                }

                val latest = status.transactions.lastOrNull()
                val refreshed =
                    active.copy(
                        depositAddress = status.addr.takeIf { it.isNotBlank() } ?: active.depositAddress,
                        receiveAddress = status.addrRecv.takeIf { it.isNotBlank() } ?: active.receiveAddress,
                    )
                val nextPhase = if (active.phase == PendingSwapPhase.FUNDING) PendingSwapPhase.IN_PROGRESS else active.phase
                val nextStatus = latest?.status?.takeIf { it.isNotBlank() } ?: pendingSwapStatus(refreshed.copy(phase = nextPhase))
                active = setSwapInProgress(refreshed, nextStatus, phase = nextPhase)
                delay(5_000)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logError("Failed to monitor SideSwap swap", e)
                val retryStatus = if (active.phase == PendingSwapPhase.FUNDING || active.fundingTxid != null) {
                    "Funding may already be onchain. Monitoring interrupted; retrying..."
                } else {
                    "Waiting for SideSwap status update..."
                }
                active = setSwapInProgress(active, retryStatus, phase = active.phase)
                delay(5_000)
            }
        }
    }

    private suspend fun monitorBoltzChainSwap(pendingSwap: PendingSwapSession) {
        var active = restoreBoltzSwapIfNeeded(pendingSwap)
        val traceStartedAt = boltzTraceStart()
        val trace =
            BoltzTraceContext(
                operation = "monitorBoltzChainSwap",
                swapId = active.swapId,
                backend = SwapService.BOLTZ.name,
                viaTor = boltzApiSource.value == SecureStorage.BOLTZ_API_TOR,
                source = "viewmodel",
            )
        var lastBoltzUpdate: BoltzSwapUpdate? = null
        logDebug(
            "monitorBoltzChainSwap start swapId=${active.swapId} phase=${active.phase} " +
                "fundingTxid=${summarizeValue(active.fundingTxid)} settlementTxid=${summarizeValue(active.settlementTxid)}",
        )
        logBoltzTrace(
            "start",
            trace,
            "phase" to active.phase,
            "fundingTxid" to summarizeValue(active.fundingTxid),
            "settlementTxid" to summarizeValue(active.settlementTxid),
        )
        upsertPendingSwap(active)
        while (true) {
            try {
                when (repository.advanceBoltzChainSwap(active.swapId)) {
                    lwk.PaymentState.CONTINUE -> {
                        logDebug("monitorBoltzChainSwap continue swapId=${active.swapId} phase=${active.phase}")
                        val latestSnapshot = repository.getBoltzChainSwapSnapshot(active.swapId) ?: active.boltzSnapshot
                        active =
                            setSwapInProgress(
                                active.copy(boltzSnapshot = latestSnapshot),
                                status = "Waiting for Boltz to complete the chain swap...",
                                phase = if (active.phase == PendingSwapPhase.REVIEW) {
                                    PendingSwapPhase.IN_PROGRESS
                                } else {
                                    active.phase
                                },
                            )
                        repository.syncBoltzChainSwapDraftFromPendingSwap(
                            active,
                            state = github.aeonbtc.ibiswallet.data.model.BoltzChainSwapDraftState.IN_PROGRESS,
                        )
                        val boltzUpdate =
                            repository.awaitBoltzSwapActivity(
                                swapId = active.swapId,
                                previousUpdate = lastBoltzUpdate,
                            )
                        if (boltzUpdate != null) {
                            lastBoltzUpdate = boltzUpdate
                            logBoltzTrace(
                                "status",
                                trace,
                                "elapsedMs" to boltzElapsedMs(traceStartedAt),
                                "phase" to active.phase,
                                "status" to boltzUpdate.status,
                                "txid" to boltzUpdate.transactionId,
                            )
                        }
                        when (boltzUpdate?.status) {
                            "transaction.claim.pending" -> {
                                logWarn(
                                    "monitorBoltzChainSwap claim pending swapId=${active.swapId} txid=${summarizeValue(boltzUpdate.transactionId)}",
                                    Exception("Boltz chain swap requires claim completion"),
                                )
                                val completed = repository.finishBoltzChainSwap(active.swapId)
                                if (completed == true) {
                                    finalizeBoltzChainSwapSuccess(
                                        pendingSwap = active,
                                        settlementTxid = boltzUpdate.transactionId ?: active.settlementTxid,
                                        source = "claim_pending_finish",
                                    )
                                    return
                                }
                                logWarn(
                                    "monitorBoltzChainSwap claim pending but finish incomplete swapId=${active.swapId} completed=$completed",
                                    Exception("Boltz chain swap claim still incomplete"),
                                )
                            }
                            "transaction.claimed" -> {
                                finalizeBoltzChainSwapSuccess(
                                    pendingSwap = active,
                                    settlementTxid = boltzUpdate.transactionId ?: active.settlementTxid,
                                    source = "claimed_status_shortcut",
                                )
                                return
                            }
                        }
                    }
                    lwk.PaymentState.SUCCESS -> {
                        logDebug("monitorBoltzChainSwap success swapId=${active.swapId}")
                        val completed = repository.finishBoltzChainSwap(active.swapId)
                        if (completed != true) {
                            logDebug(
                                "monitorBoltzChainSwap awaiting more updates after success swapId=${active.swapId} completed=$completed",
                            )
                            val latestSnapshot =
                                repository.getBoltzChainSwapSnapshot(active.swapId) ?: active.boltzSnapshot
                            active = updateTrackedPendingSwapState(active, boltzSnapshot = latestSnapshot)
                            repository.syncBoltzChainSwapDraftFromPendingSwap(active)
                            val boltzUpdate =
                                repository.awaitBoltzSwapActivity(
                                    swapId = active.swapId,
                                    previousUpdate = lastBoltzUpdate,
                                )
                            if (boltzUpdate != null) {
                                lastBoltzUpdate = boltzUpdate
                            }
                            if (boltzUpdate?.status == "transaction.claimed") {
                                finalizeBoltzChainSwapSuccess(
                                    pendingSwap = active,
                                    settlementTxid = boltzUpdate.transactionId ?: active.settlementTxid,
                                    source = "success_claimed_status",
                                )
                                return
                            }
                            continue
                        }
                        logBoltzTrace("completed", trace, "elapsedMs" to boltzElapsedMs(traceStartedAt))
                        finalizeBoltzChainSwapSuccess(
                            pendingSwap = active,
                            settlementTxid = active.settlementTxid,
                            source = "success_complete",
                        )
                        return
                    }
                    lwk.PaymentState.FAILED -> {
                        logBoltzTrace(
                            "failed_state",
                            trace,
                            level = BoltzTraceLevel.WARN,
                            "elapsedMs" to boltzElapsedMs(traceStartedAt),
                        )
                        logWarn("monitorBoltzChainSwap failed swapId=${active.swapId}", Exception("Boltz payment state failed"))
                        repository.failBoltzChainSwap(active.swapId)
                        repository.failBoltzChainSwapDraft(
                            active.swapId,
                            error = "Boltz chain swap failed. Boltz will refund after the timelock expires.",
                            refundable = true,
                        )
                        removeTrackedPendingSwap(active.swapId)
                        _events.emit(
                            LiquidEvent.Error(
                                "Boltz chain swap failed. Boltz will refund to your refund address after the timelock expires.",
                            ),
                        )
                        return
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: lwk.LwkException.SwapExpired) {
                logBoltzTrace(
                    "expired",
                    trace,
                    level = BoltzTraceLevel.WARN,
                    throwable = e,
                    "elapsedMs" to boltzElapsedMs(traceStartedAt),
                )
                logWarn("monitorBoltzChainSwap expired swapId=${active.swapId}", e)
                repository.failBoltzChainSwap(active.swapId)
                repository.failBoltzChainSwapDraft(
                    active.swapId,
                    error = "Swap expired (${e.swapId}). Boltz will refund after the timelock.",
                    refundable = true,
                )
                removeTrackedPendingSwap(active.swapId)
                _events.emit(
                    LiquidEvent.Error(
                        "Swap expired (${e.swapId}). Boltz will refund to your refund address after the timelock.",
                    ),
                )
                return
            } catch (e: Exception) {
                logBoltzTrace(
                    "failed",
                    trace,
                    level = BoltzTraceLevel.WARN,
                    throwable = e,
                    "elapsedMs" to boltzElapsedMs(traceStartedAt),
                )
                logError("Failed to monitor Boltz chain swap swapId=${active.swapId}", e)
                val latestSnapshot = runCatching {
                    repository.getBoltzChainSwapSnapshot(active.swapId)
                }.getOrNull() ?: active.boltzSnapshot
                val retryStatus = if (active.phase == PendingSwapPhase.FUNDING || active.fundingTxid != null) {
                    "Funding may already be onchain. Monitoring interrupted; retrying..."
                } else {
                    "Waiting for Boltz to continue the chain swap..."
                }
                active =
                    setSwapInProgress(
                        active.copy(boltzSnapshot = latestSnapshot),
                        retryStatus,
                        phase = active.phase,
                    )
                repository.syncBoltzChainSwapDraftFromPendingSwap(active)
                val boltzUpdate =
                    repository.awaitBoltzSwapActivity(
                        swapId = active.swapId,
                        previousUpdate = lastBoltzUpdate,
                    )
                if (boltzUpdate != null) {
                    lastBoltzUpdate = boltzUpdate
                }
            }
        }
    }

    private suspend fun finalizeBoltzChainSwapSuccess(
        pendingSwap: PendingSwapSession,
        settlementTxid: String?,
        source: String,
    ) {
        var resolvedSettlementTxid =
            settlementTxid?.takeIf { it.isNotBlank() }
                ?: pendingSwap.settlementTxid?.takeIf { it.isNotBlank() }
                ?: detectChainSwapSettlementTxid(pendingSwap)
        val completedSwap =
            updateTrackedPendingSwapState(
                pendingSwap,
                settlementTxid = resolvedSettlementTxid,
            )
        applySwapTransactionLabels(completedSwap)
        if (source.contains("shortcut")) {
            repository.clearBoltzChainSwapRuntime(completedSwap.swapId, source)
        }
        repository.deleteBoltzChainSwapDraftBySwapId(completedSwap.swapId)
        logDebug(
            "finalizeBoltzChainSwapSuccess source=$source swapId=${completedSwap.swapId} " +
                "settlementTxid=${summarizeValue(completedSwap.settlementTxid)}",
        )
        removeTrackedPendingSwap(completedSwap.swapId)
        completedSwap.settlementTxid?.takeIf { it.isNotBlank() }?.let { txid ->
            if (completedSwap.direction == SwapDirection.BTC_TO_LBTC) {
                repository.saveLiquidTransactionSource(
                    txid = txid,
                    source = LiquidTxSource.CHAIN_SWAP,
                )
                saveChainSwapLiquidTxDetails(
                    txid = txid,
                    pendingSwap = completedSwap,
                    role = LiquidSwapTxRole.SETTLEMENT,
                )
            } else {
                saveChainSwapBitcoinTxDetails(
                    txid = txid,
                    pendingSwap = completedSwap,
                    role = LiquidSwapTxRole.SETTLEMENT,
                )
            }
        }
        runCatching { repository.sync() }
        if (completedSwap.direction == SwapDirection.BTC_TO_LBTC && resolvedSettlementTxid.isNullOrBlank()) {
            detectChainSwapSettlementTxid(completedSwap)?.let { txid ->
                resolvedSettlementTxid = txid
                applySwapTransactionLabels(completedSwap.copy(settlementTxid = txid))
                repository.saveLiquidTransactionSource(
                    txid = txid,
                    source = LiquidTxSource.CHAIN_SWAP,
                )
                saveChainSwapLiquidTxDetails(
                    txid = txid,
                    pendingSwap = completedSwap,
                    role = LiquidSwapTxRole.SETTLEMENT,
                )
            }
        }
        _events.emit(
            LiquidEvent.SwapCompleted(
                swapId = completedSwap.swapId,
                settlementTxid = resolvedSettlementTxid,
                fundingTxid = completedSwap.fundingTxid,
            ),
        )
    }

    fun resetSwapState() {
        _swapState.value = SwapState.Idle
    }

    fun resetSendState() {
        cancelActiveSendPreview()
        lightningPaymentMonitorJob?.cancel()
        lightningPaymentMonitorJob = null
        _sendState.value = LiquidSendState.Idle
        launchLiquidJob {
            runCatching { repository.discardPreparedLightningPaymentReview() }
        }
    }

    fun setPreSelectedLiquidUtxo(utxo: UtxoInfo) {
        _preSelectedLiquidUtxo.value = utxo
    }

    fun clearPreSelectedLiquidUtxo() {
        _preSelectedLiquidUtxo.value = null
    }

    fun updateSendDraft(draft: SendScreenDraft) {
        _sendDraft.value = draft
    }

    fun clearSendDraft() {
        _sendDraft.value = emptySendDraft()
    }

    fun discardPreparedSwapReview() {
        val pendingSwap = getPreparedSwap()
        if (pendingSwap?.phase == PendingSwapPhase.REVIEW) {
            _swapState.value = SwapState.PreparingReview
            launchLiquidJob {
                reviewPreparationMutex.withLock {
                    try {
                        if (pendingSwap.service == SwapService.BOLTZ) {
                            repository.discardPreparedBoltzChainSwap(pendingSwap.swapId)
                        }
                        clearPreparedSwap()
                    } catch (e: Exception) {
                        logError("Failed to discard prepared swap ${pendingSwap.swapId}", e)
                    }
                }
                _swapState.value = SwapState.Idle
            }
            return
        }
        if (_swapState.value is SwapState.ReviewReady) {
            _swapState.value = SwapState.Idle
        }
    }

    /** Permanently dismiss a failed swap, clearing all persisted data. */
    fun dismissFailedSwap() {
        clearPreparedSwap()
        _swapState.value = SwapState.Idle
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

    private fun safeLiquidErrorMessage(error: Throwable?, fallback: String): String {
        if (error?.isTransactionInsufficientFundsError() == true) {
            return AppLocale.createLocalizedContext(appContext, secureStorage.getAppLocale())
                .getString(R.string.loc_534e1eb2)
        }
        val message = error?.message.orEmpty()
        return when {
            message.contains("dust", ignoreCase = true) -> "Amount below dust limit"
            message.contains("timed out", ignoreCase = true) ||
                message.contains("timeout", ignoreCase = true) -> "Connection timed out"
            message.contains("network", ignoreCase = true) ||
                message.contains("connection", ignoreCase = true) ||
                message.contains("socket", ignoreCase = true) -> "Network error"
            message.contains("invalid address", ignoreCase = true) -> "Invalid address"
            message.contains("exceeds maximal", ignoreCase = true) ||
                message.contains("minimum", ignoreCase = true) -> "Amount outside supported limits"
            else -> fallback
        }
    }

    private fun summarizeValue(value: String?): String {
        if (value.isNullOrBlank()) return "null"
        return if (value.length <= 12) value else "${value.take(6)}...${value.takeLast(4)}"
    }

    private fun emptySendDraft(): SendScreenDraft = SendScreenDraft(feeRate = MIN_LIQUID_SEND_FEE_RATE)
}

internal fun isRestLightningPaymentPendingStatus(status: String?): Boolean {
    return when (status) {
        null, "", "invoice.set", "transaction.mempool", "transaction.confirmed", "invoice.pending" -> true
        else -> false
    }
}

internal fun isRestLightningPaymentSuccessStatus(status: String?): Boolean {
    return when (status) {
        "invoice.paid", "transaction.claim.pending", "transaction.claimed" -> true
        else -> false
    }
}

internal fun isLiquidLightningInvoiceClaimableStatus(status: String?): Boolean {
    return when (status) {
        "transaction.mempool",
        "transaction.confirmed",
        "invoice.settled",
        "invoice.paid",
        "transaction.claim.pending",
        -> true
        else -> false
    }
}

internal fun selectLiquidLightningInvoiceClaimUpdate(
    latestUpdate: BoltzSwapUpdate?,
    previousUpdate: BoltzSwapUpdate?,
): BoltzSwapUpdate? {
    if (latestUpdate != null) return latestUpdate
    return previousUpdate?.takeIf {
        isLiquidLightningInvoiceClaimableStatus(it.status) ||
            isLiquidLightningInvoiceClaimedStatus(it.status)
    }
}

internal fun isLiquidLightningInvoiceClaimedStatus(status: String?): Boolean = status == "transaction.claimed"

internal fun isLiquidLightningInvoiceTerminalFailureStatus(status: String?): Boolean {
    return when (status) {
        "invoice.expired",
        "swap.expired",
        "transaction.failed",
        "transaction.refunded",
        "transaction.lockupFailed",
        "invoice.failedToPay",
        -> true
        else -> false
    }
}

/** One-shot events from LiquidViewModel */
sealed class LiquidEvent {
    data class TransactionSent(val txid: String) : LiquidEvent()
    data class LightningReceived(val txid: String) : LiquidEvent()
    data class LightningSent(val invoice: String) : LiquidEvent()
    data class SwapCompleted(
        val swapId: String,
        val settlementTxid: String? = null,
        val fundingTxid: String? = null,
    ) : LiquidEvent()
    data class Error(val message: String) : LiquidEvent()
}

private inline fun <reified T : Throwable> Throwable.findCause(): T? {
    var current: Throwable? = this
    val visited = mutableSetOf<Throwable>()
    while (current != null && visited.add(current)) {
        if (current is T) return current
        current = current.cause
    }
    return null
}
