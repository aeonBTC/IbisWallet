package github.aeonbtc.ibiswallet.viewmodel

import android.app.Application
import android.net.Uri
import android.os.SystemClock
import android.util.Base64
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import github.aeonbtc.ibiswallet.BuildConfig
import github.aeonbtc.ibiswallet.data.AppUpdateService
import github.aeonbtc.ibiswallet.data.BtcPriceService
import github.aeonbtc.ibiswallet.data.FeeEstimationService
import github.aeonbtc.ibiswallet.data.local.SecureStorage
import github.aeonbtc.ibiswallet.data.model.DryRunResult
import github.aeonbtc.ibiswallet.data.model.ElectrumConfig
import github.aeonbtc.ibiswallet.data.model.FeeEstimationResult
import github.aeonbtc.ibiswallet.data.model.PsbtDetails
import github.aeonbtc.ibiswallet.data.model.PsbtSessionStatus
import github.aeonbtc.ibiswallet.data.model.Recipient
import github.aeonbtc.ibiswallet.data.model.SeedFormat
import github.aeonbtc.ibiswallet.data.model.StoredWallet
import github.aeonbtc.ibiswallet.data.model.SwapService
import github.aeonbtc.ibiswallet.data.model.TransactionSearchResult
import github.aeonbtc.ibiswallet.data.model.UtxoInfo
import github.aeonbtc.ibiswallet.data.model.WalletAddress
import github.aeonbtc.ibiswallet.data.model.WalletImportConfig
import github.aeonbtc.ibiswallet.data.model.WalletPolicyType
import github.aeonbtc.ibiswallet.data.model.WalletResult
import github.aeonbtc.ibiswallet.data.model.WalletState
import github.aeonbtc.ibiswallet.localization.AppLocale
import github.aeonbtc.ibiswallet.data.repository.WalletRepository
import github.aeonbtc.ibiswallet.service.ConnectivityKeepAlivePolicy
import github.aeonbtc.ibiswallet.tor.TorManager
import github.aeonbtc.ibiswallet.tor.TorState
import github.aeonbtc.ibiswallet.tor.TorStatus
import github.aeonbtc.ibiswallet.util.Bip329LabelNetwork
import github.aeonbtc.ibiswallet.util.Bip329LabelScope
import github.aeonbtc.ibiswallet.util.Bip329Labels
import github.aeonbtc.ibiswallet.util.AppVersion
import github.aeonbtc.ibiswallet.util.BitcoinUtils
import github.aeonbtc.ibiswallet.util.CertificateFirstUseException
import github.aeonbtc.ibiswallet.util.CertificateInfo
import github.aeonbtc.ibiswallet.util.CertificateMismatchException
import github.aeonbtc.ibiswallet.util.CryptoUtils
import github.aeonbtc.ibiswallet.util.InputLimits
import github.aeonbtc.ibiswallet.util.SecureLog
import github.aeonbtc.ibiswallet.util.ServerUrlValidator
import github.aeonbtc.ibiswallet.util.BitcoinSendPreparationCacheKey
import github.aeonbtc.ibiswallet.util.BitcoinSendPreparationState
import github.aeonbtc.ibiswallet.util.BackupJsonAdapters
import github.aeonbtc.ibiswallet.util.SparkBackupMetadata
import github.aeonbtc.ibiswallet.util.buildMultiBitcoinSendPreparationKey
import github.aeonbtc.ibiswallet.util.buildSingleBitcoinSendPreparationKey
import github.aeonbtc.ibiswallet.util.readBytesWithLimit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * ViewModel for managing wallet state across the app
 */
class WalletViewModel(application: Application) : AndroidViewModel(application) {
    private val appContext = getApplication<Application>()
    private val repository = WalletRepository(application)
    private val secureStorage = SecureStorage.getInstance(application)
    private val torManager = TorManager.getInstance(application)
    private val feeEstimationService = FeeEstimationService()
    private val btcPriceService = BtcPriceService()
    private val appUpdateService = AppUpdateService()

    val walletState: StateFlow<WalletState> = repository.walletState
    val torState: StateFlow<TorState> = torManager.torState

    private val _uiState = MutableStateFlow(WalletUiState())
    val uiState: StateFlow<WalletUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<WalletEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<WalletEvent> = _events.asSharedFlow()

    private val _fullBackupResultMessage = MutableStateFlow<String?>(null)
    val fullBackupResultMessage: StateFlow<String?> = _fullBackupResultMessage.asStateFlow()

    private val _settingsRefreshVersion = MutableStateFlow(0L)
    val settingsRefreshVersion: StateFlow<Long> = _settingsRefreshVersion.asStateFlow()

    fun clearFullBackupResult() {
        _fullBackupResultMessage.value = null
    }

    fun setFullBackupResult(message: String) {
        _fullBackupResultMessage.value = message
    }

    // Active connection job (cancellable)
    private var connectionJob: Job? = null

    // Background sync loop job - runs every ~5min while connected
    private var backgroundSyncJob: Job? = null

    // Heartbeat loop job - pings server every ~60s to detect dead connections fast
    private var heartbeatJob: Job? = null

    @Volatile private var isAppInBackground = false
    @Volatile private var isAppSessionUnlocked = false
    private var postUnlockBootstrapJob: Job? = null

    // Foreground-only BTC/fiat price refresh
    private var btcPriceRefreshJob: Job? = null
    private var btcPriceFetchJob: Job? = null
    private var lastBtcPriceFetchElapsedMs = 0L
    private var lastBtcPriceFetchSource = SecureStorage.PRICE_SOURCE_OFF
    private var lastBtcPriceFetchCurrency = SecureStorage.DEFAULT_PRICE_CURRENCY
    private val historicalPriceSeriesCache = mutableMapOf<String, List<BtcPriceService.HistoricalPricePoint>>()
    private var appUpdateCheckJob: Job? = null
    private var lastAppUpdateCheckElapsedMs = 0L

    // Reconnect retry job - exponential backoff reconnection after connection loss
    private var reconnectRetryJob: Job? = null

    private var fullSyncJob: Job? = null
    @Volatile private var fullSyncCancelRequested = false

    private var addressRefreshJob: Job? = null
    private var labelRefreshJob: Job? = null
    private var syncTimesRefreshJob: Job? = null

    // Server state for reactive UI updates
    private val _serversState = MutableStateFlow(ServersState())
    val serversState: StateFlow<ServersState> = _serversState.asStateFlow()

    // Layer 1 denomination state (BTC or SATS)
    private val _denominationState = MutableStateFlow(repository.getLayer1Denomination())
    val denominationState: StateFlow<String> = _denominationState.asStateFlow()

    private val _appLocale = MutableStateFlow(repository.getAppLocale())
    val appLocale: StateFlow<AppLocale> = _appLocale.asStateFlow()

    // Mempool server state
    private val _mempoolServerState = MutableStateFlow(repository.getMempoolServer())
    val mempoolServerState: StateFlow<String> = _mempoolServerState.asStateFlow()

    // Fee estimation state
    private val _feeSourceState = MutableStateFlow(repository.getFeeSource())
    val feeSourceState: StateFlow<String> = _feeSourceState.asStateFlow()

    private val _feeEstimationState = MutableStateFlow<FeeEstimationResult>(FeeEstimationResult.Disabled)
    val feeEstimationState: StateFlow<FeeEstimationResult> = _feeEstimationState.asStateFlow()

    // BTC/USD price source state
    private val _priceSourceState = MutableStateFlow(repository.getPriceSource())
    val priceSourceState: StateFlow<String> = _priceSourceState.asStateFlow()

    private val _priceCurrencyState = MutableStateFlow(repository.getPriceCurrency())
    val priceCurrencyState: StateFlow<String> = _priceCurrencyState.asStateFlow()

    private val _historicalTxFiatEnabledState = MutableStateFlow(repository.isHistoricalTxFiatEnabled())
    val historicalTxFiatEnabledState: StateFlow<Boolean> = _historicalTxFiatEnabledState.asStateFlow()

    // BTC/fiat price state (null if disabled or fetch failed)
    private val _btcPriceState = MutableStateFlow<Double?>(null)
    val btcPriceState: StateFlow<Double?> = _btcPriceState.asStateFlow()

    private val _historicalTxBtcPriceState = MutableStateFlow<Map<String, Double>>(emptyMap())
    val historicalTxBtcPriceState: StateFlow<Map<String, Double>> = _historicalTxBtcPriceState.asStateFlow()
    private val _externalHistoricalTxTimestamps = MutableStateFlow<Map<String, Long?>>(emptyMap())

    private val _appUpdateStatus =
        MutableStateFlow<AppUpdateStatus>(
            if (repository.isAppUpdateCheckEnabled()) {
                AppUpdateStatus.Checking
            } else {
                AppUpdateStatus.Disabled
            },
        )
    val appUpdateStatus: StateFlow<AppUpdateStatus> = _appUpdateStatus.asStateFlow()
    private val _appUpdatePrompt = MutableStateFlow<AppUpdateStatus.UpdateAvailable?>(null)
    val appUpdatePrompt: StateFlow<AppUpdateStatus.UpdateAvailable?> = _appUpdatePrompt.asStateFlow()

    // Minimum fee rate from connected Electrum server
    val minFeeRate: StateFlow<Double> = repository.minFeeRate

    // Privacy mode - hides all amounts when active (persisted across sessions)
    private val _privacyMode = MutableStateFlow(repository.getPrivacyMode())
    val privacyMode: StateFlow<Boolean> = _privacyMode.asStateFlow()

    // Swipe navigation mode
    private val _swipeMode = MutableStateFlow(repository.getSwipeMode())
    val swipeMode: StateFlow<String> = _swipeMode.asStateFlow()

    // QR density for PSBT/PSET animated exports
    private val _psbtQrDensityState = MutableStateFlow(repository.getPsbtQrDensity())
    val psbtQrDensityState: StateFlow<SecureStorage.QrDensity> = _psbtQrDensityState.asStateFlow()

    private val _psbtQrBrightnessState = MutableStateFlow(repository.getPsbtQrBrightness())
    val psbtQrBrightnessState: StateFlow<Float> = _psbtQrBrightnessState.asStateFlow()

    // Pre-selected UTXO for coin control (set from AllUtxosScreen)
    private val _preSelectedUtxo = MutableStateFlow<UtxoInfo?>(null)
    val preSelectedUtxo: StateFlow<UtxoInfo?> = _preSelectedUtxo.asStateFlow()

    private data class HistoricalTxWalletSnapshot(
        val walletId: String?,
        val transactions: List<HistoricalTxSnapshot>,
    )

    private data class HistoricalTxSnapshot(
        val txid: String,
        val timestamp: Long?,
    )

    private data class HistoricalTxPriceRequest(
        val walletId: String?,
        val transactions: List<HistoricalTxSnapshot>,
        val priceSource: String,
        val fiatCurrency: String,
        val enabled: Boolean,
    )

    // All UTXOs — populated asynchronously on IO to avoid BDK JNI calls on the main thread.
    // Refreshed when balance or active wallet changes, and after freeze/unfreeze operations.
    private val _allUtxos = MutableStateFlow<List<UtxoInfo>>(emptyList())
    val allUtxos: StateFlow<List<UtxoInfo>> = _allUtxos.asStateFlow()

    private val _allAddresses =
        MutableStateFlow<Triple<List<WalletAddress>, List<WalletAddress>, List<WalletAddress>>>(
            Triple(emptyList(), emptyList(), emptyList()),
        )
    val allAddresses: StateFlow<Triple<List<WalletAddress>, List<WalletAddress>, List<WalletAddress>>> =
        _allAddresses.asStateFlow()

    private val _addressLabels = MutableStateFlow<Map<String, String>>(emptyMap())
    val addressLabels: StateFlow<Map<String, String>> = _addressLabels.asStateFlow()

    private val _transactionLabels = MutableStateFlow<Map<String, String>>(emptyMap())
    val transactionLabels: StateFlow<Map<String, String>> = _transactionLabels.asStateFlow()

    private val _walletLastFullSyncTimes = MutableStateFlow<Map<String, Long?>>(emptyMap())
    val walletLastFullSyncTimes: StateFlow<Map<String, Long?>> = _walletLastFullSyncTimes.asStateFlow()

    // Track which wallet is currently being synced (for UI progress indicator)
    private val _syncingWalletId = MutableStateFlow<String?>(null)
    val syncingWalletId: StateFlow<String?> = _syncingWalletId.asStateFlow()

    // Send screen draft state (persisted while app is open/minimized)
    private val _sendScreenDraft = MutableStateFlow(SendScreenDraft())
    val sendScreenDraft: StateFlow<SendScreenDraft> = _sendScreenDraft.asStateFlow()

    // Pending send input from external intent or NFC (consumed by IbisWalletApp after unlock)
    private val _pendingSendInput = MutableStateFlow<String?>(null)
    val pendingSendInput: StateFlow<String?> = _pendingSendInput.asStateFlow()

    // PSBT state for watch-only wallet signing flow
    private val _psbtState = MutableStateFlow(PsbtState())
    val psbtState: StateFlow<PsbtState> = _psbtState.asStateFlow()

    // Certificate TOFU state
    private val _certDialogState = MutableStateFlow<CertDialogState?>(null)
    val certDialogState: StateFlow<CertDialogState?> = _certDialogState.asStateFlow()

    // Dry-run fee estimation from BDK TxBuilder
    private val _dryRunResult = MutableStateFlow<DryRunResult?>(null)
    val dryRunResult: StateFlow<DryRunResult?> = _dryRunResult.asStateFlow()
    private val _isDryRunInProgress = MutableStateFlow(false)
    val isDryRunInProgress: StateFlow<Boolean> = _isDryRunInProgress.asStateFlow()
    private var dryRunJob: Job? = null
    private var dryRunRequestToken = 0L
    private var activeDryRunRequestKey: BitcoinSendPreparationCacheKey? = null
    private var completedDryRunRequestKey: BitcoinSendPreparationCacheKey? = null
    private val _sendRecipientIsSelfTransfer = MutableStateFlow(false)
    val sendRecipientIsSelfTransfer: StateFlow<Boolean> = _sendRecipientIsSelfTransfer.asStateFlow()
    private var selfTransferCheckJob: Job? = null
    private var selfTransferRequestToken = 0L

    // Manual broadcast state (standalone tx broadcast, not tied to any wallet)
    private val _manualBroadcastState = MutableStateFlow(ManualBroadcastState())
    val manualBroadcastState: StateFlow<ManualBroadcastState> = _manualBroadcastState.asStateFlow()

    // Auto-switch server on disconnect
    private val _autoSwitchServer = MutableStateFlow(repository.isAutoSwitchServerEnabled())
    val autoSwitchServer: StateFlow<Boolean> = _autoSwitchServer.asStateFlow()

    // Duress mode state (not persisted — resets on app restart which forces re-auth via lock screen)
    private val _isDuressMode = MutableStateFlow(false)
    val isDuressMode: StateFlow<Boolean> = _isDuressMode.asStateFlow()

    // Guards notification firing until the first post-connection sync completes,
    // preventing stale transactions from being treated as new on cold start.
    private val _initialSyncComplete = MutableStateFlow(false)
    val initialSyncComplete: StateFlow<Boolean> = _initialSyncComplete.asStateFlow()

    private val lifecycleCoordinator =
        AppLifecycleCoordinator(
            scope = viewModelScope,
            onBackgrounded = {
                isAppInBackground = true
                ConnectivityKeepAlivePolicy.updateAppForegroundState(
                    context = appContext,
                    isInForeground = false,
                )
                stopBtcPriceRefreshLoop()
                // When the user has enabled the foreground keep-alive service,
                // the process stays alive and the Electrum TCP socket must be
                // kept active via the heartbeat ping loop — otherwise idle
                // servers will close the connection (many have 10–15min
                // inactivity timeouts) and we only notice on resume.
                if (!isBackgroundKeepAliveActive()) {
                    stopHeartbeat()
                }
                _uiState.value.isConnected
            },
            onForegrounded = { wasConnectedBeforeBackground, _ ->
                isAppInBackground = false
                ConnectivityKeepAlivePolicy.updateAppForegroundState(
                    context = appContext,
                    isInForeground = true,
                )
                if (!isAppSessionUnlocked) {
                    return@AppLifecycleCoordinator
                }
                if (repository.getPriceSource() != SecureStorage.PRICE_SOURCE_OFF) {
                    fetchBtcPrice(force = true)
                    startBtcPriceRefreshLoop()
                }

                if (!wasConnectedBeforeBackground) return@AppLifecycleCoordinator
                if (repository.isUserDisconnected()) return@AppLifecycleCoordinator

                if (!_uiState.value.isConnected) {
                    reconnectOnForeground()
                    return@AppLifecycleCoordinator
                }

                verifyConnectionOnForeground()
            },
        )

    /**
     * True when the user has enabled the foreground keep-alive service and the
     * app is expected to keep Electrum sockets alive while backgrounded. When
     * this is true we keep heartbeat, subscription listening and the reconnect
     * retry loop running in background; otherwise we fall back to the
     * "pause-and-resume-on-foreground" policy.
     */
    private fun isBackgroundKeepAliveActive(): Boolean =
        repository.isForegroundConnectivityEnabled()

    fun onAppUnlocked() {
        if (isAppSessionUnlocked && postUnlockBootstrapJob?.isActive == true) return
        if (isAppSessionUnlocked && walletState.value.isInitialized) return

        isAppSessionUnlocked = true
        postUnlockBootstrapJob?.cancel()
        postUnlockBootstrapJob =
            viewModelScope.launch {
                refreshServersState()
                refreshPricePreferences()

                if (repository.isWalletInitialized()) {
                    _uiState.value = _uiState.value.copy(isLoading = true, error = null)
                    when (val result = repository.loadWallet()) {
                        is WalletResult.Success -> {
                            _uiState.value = _uiState.value.copy(isLoading = false)
                        }
                        is WalletResult.Error -> {
                            _uiState.value =
                                _uiState.value.copy(
                                    isLoading = false,
                                    error = result.message,
                                )
                        }
                    }
                }

                if (!repository.isUserDisconnected()) {
                    repository.getElectrumConfig()?.let { config ->
                        connectToElectrum(config)
                    } ?: run {
                        _initialSyncComplete.value = true
                    }
                } else {
                    // No auto-connect — mark initial sync as complete so
                    // persistent notified-txid tracking works for offline mode.
                    _initialSyncComplete.value = true
                }

                fetchFeeEstimates()
                if (repository.isAppUpdateCheckEnabled()) {
                    checkForAppUpdate()
                }
                if (_priceSourceState.value != SecureStorage.PRICE_SOURCE_OFF) {
                    fetchBtcPrice()
                    startBtcPriceRefreshLoop()
                }
            }
    }

    fun onAppLocked() {
        if (!isAppSessionUnlocked && postUnlockBootstrapJob == null) return
        isAppSessionUnlocked = false
        postUnlockBootstrapJob?.cancel()
        postUnlockBootstrapJob = null
        connectionJob?.cancel()
        connectionJob = null
        appUpdateCheckJob?.cancel()
        appUpdateCheckJob = null
        btcPriceFetchJob?.cancel()
        btcPriceFetchJob = null
        stopBtcPriceRefreshLoop()
        stopBackgroundSync()
        stopHeartbeat()
        stopReconnectRetry()
        _initialSyncComplete.value = false
        _uiState.value =
            _uiState.value.copy(
                isLoading = false,
                isConnecting = false,
                isConnected = false,
                serverVersion = null,
            )
        ConnectivityKeepAlivePolicy.updateBitcoinState(
            context = appContext,
            connected = false,
            electrumUsesTor = false,
            externalTorRequired = false,
        )
        viewModelScope.launch(Dispatchers.IO) {
            repository.disconnect()
            repository.unloadWalletFromMemoryForLock()
        }
    }

    init {
        // Initialize servers state
        refreshServersState()
        refreshPricePreferences()

        // Refresh UTXOs asynchronously when wallet sync state changes in a way that can
        // affect confirmation status, balance, or the active wallet.
        // Wait until deferred transaction hydration completes so large-wallet startup
        // does not overlap multiple full BDK scans (tx history + UTXOs + addresses).
        viewModelScope.launch(Dispatchers.IO) {
            walletState
                .map { state ->
                    state.isInitialized to Pair(
                        state.isTransactionHistoryLoading,
                        Triple(
                            state.balanceSats,
                            state.activeWallet?.id,
                            state.lastSyncTimestamp,
                        ),
                    )
                }
                .distinctUntilChanged()
                .collectLatest { (initialized, derivedState) ->
                    val (isTransactionHistoryLoading, _) = derivedState
                    if (!initialized) {
                        clearDerivedWalletSnapshots()
                        return@collectLatest
                    }

                    refreshLabelSnapshots()

                    if (isTransactionHistoryLoading) {
                        clearLargeDerivedWalletSnapshots()
                        return@collectLatest
                    }

                    _allUtxos.value = repository.getAllUtxos()
                    refreshAddressBook()
                }
        }

        viewModelScope.launch {
            walletState
                .map { state -> state.wallets.map { it.id } to state.activeWallet?.id }
                .distinctUntilChanged()
                .collect {
                    refreshWalletLastFullSyncTimes()
                }
        }

        viewModelScope.launch {
            combine(
                _uiState.map { it.isConnected },
                _feeSourceState,
                _priceSourceState,
                _serversState.map { it.activeServerId },
            ) { isConnected, feeSource, priceSource, activeServerId ->
                listOf(isConnected.toString(), feeSource, priceSource, activeServerId.orEmpty())
            }
                .distinctUntilChanged()
                .collect {
                    syncForegroundConnectivityPolicy()
                }
        }

        viewModelScope.launch {
            combine(
                walletState
                    .map { state ->
                        HistoricalTxWalletSnapshot(
                            walletId = state.activeWallet?.id,
                            transactions =
                                state.transactions.map { tx ->
                                    HistoricalTxSnapshot(
                                        txid = tx.txid,
                                        timestamp = tx.timestamp,
                                    )
                                },
                        )
                    }
                    .distinctUntilChanged(),
                _externalHistoricalTxTimestamps,
                _priceSourceState,
                _priceCurrencyState,
                _historicalTxFiatEnabledState,
            ) { snapshot, externalTimestamps, priceSource, fiatCurrency, enabled ->
                HistoricalTxPriceRequest(
                    walletId = snapshot.walletId,
                    transactions =
                        (
                            snapshot.transactions +
                                externalTimestamps.map { (id, timestamp) ->
                                    HistoricalTxSnapshot(
                                        txid = id,
                                        timestamp = normalizeHistoricalPriceTimestamp(timestamp),
                                    )
                                }
                            ).distinctBy { it.txid },
                    priceSource = priceSource,
                    fiatCurrency = fiatCurrency,
                    enabled = enabled,
                )
            }
                .distinctUntilChanged()
                .collectLatest { request ->
                    refreshHistoricalTxBtcPrices(request)
                }
        }

        viewModelScope.launch {
            repository.connectionEvents.collect { event ->
                when (event) {
                    WalletRepository.ConnectionEvent.ConnectionLost -> {
                        if (!_uiState.value.isConnected) return@collect
                        // Only skip background handling when the foreground
                        // keep-alive service is disabled — in that mode we
                        // intentionally wait for resume to re-verify. When
                        // keep-alive is active we must act on the loss now so
                        // the retry loop / auto-switch can self-heal before
                        // the user returns.
                        if (isAppInBackground && !isBackgroundKeepAliveActive()) {
                            if (BuildConfig.DEBUG) {
                                android.util.Log.d(
                                    "WalletViewModel",
                                    "Ignoring background subscription loss; foreground resume will verify the base connection",
                                )
                            }
                            return@collect
                        }

                        // Subscription socket died, but the bridge/direct sockets
                        // may still be alive (and mid-sync). Verify before teardown.
                        val alive =
                            probeServerConnection(
                                timeoutMs = HEARTBEAT_PING_TIMEOUT_MS,
                                socketTimeoutMs = HEARTBEAT_PING_SOCKET_TIMEOUT_MS,
                                lockTimeoutMs = HEARTBEAT_PING_LOCK_TIMEOUT_MS,
                            )

                        if (alive) {
                            if (BuildConfig.DEBUG) {
                                android.util.Log.d(
                                    "WalletViewModel",
                                    "Subscription socket lost but base connection is still alive; restarting subscriptions",
                                )
                            }
                            launchSubscriptions(reason = "recovery")
                        } else {
                            if (BuildConfig.DEBUG) {
                                android.util.Log.w(
                                    "WalletViewModel",
                                    "Subscription loss confirmed a full connection loss; starting reconnect flow",
                                )
                            }
                            handleConnectionLost()
                        }
                    }
                }
            }
        }
    }

    private fun clearDerivedWalletSnapshots() {
        clearLargeDerivedWalletSnapshots()
        _allAddresses.value = Triple(emptyList(), emptyList(), emptyList())
        _addressLabels.value = emptyMap()
        _transactionLabels.value = emptyMap()
        _walletLastFullSyncTimes.value = emptyMap()
        _sendRecipientIsSelfTransfer.value = false
    }

    private fun clearLargeDerivedWalletSnapshots() {
        addressRefreshJob?.cancel()
        _allUtxos.value = emptyList()
        _allAddresses.value = Triple(emptyList(), emptyList(), emptyList())
    }

    private fun refreshAddressBook() {
        addressRefreshJob?.cancel()
        addressRefreshJob =
            viewModelScope.launch(Dispatchers.IO) {
                if (_allAddresses.value.first.isEmpty() && _allAddresses.value.second.isEmpty() && _allAddresses.value.third.isEmpty()) {
                    _allAddresses.value = repository.getAddressPreview(ADDRESS_BOOK_PREVIEW_SIZE)
                }
                val addresses = repository.getAllAddresses()
                _allAddresses.value = addresses
            }
    }

    private fun refreshLabelSnapshots() {
        labelRefreshJob?.cancel()
        labelRefreshJob =
            viewModelScope.launch(Dispatchers.IO) {
                _addressLabels.value = repository.getAllAddressLabels()
                _transactionLabels.value = repository.getAllTransactionLabels()
            }
    }

    private fun refreshWalletLastFullSyncTimes() {
        syncTimesRefreshJob?.cancel()
        syncTimesRefreshJob =
            viewModelScope.launch(Dispatchers.IO) {
                val walletIds = walletState.value.wallets.map { it.id }
                _walletLastFullSyncTimes.value =
                    walletIds.associateWith { walletId ->
                        repository.getLastFullSyncTime(walletId)
                    }
            }
    }

    private suspend fun refreshWalletLastFullSyncTime(walletId: String?) {
        val resolvedWalletId = walletId ?: return
        val lastFullSyncTime =
            withContext(Dispatchers.IO) {
                repository.getLastFullSyncTime(resolvedWalletId)
            }
        _walletLastFullSyncTimes.value =
            _walletLastFullSyncTimes.value.toMutableMap().apply {
                put(resolvedWalletId, lastFullSyncTime)
            }
    }

    private fun refreshCurrentWalletSnapshots() {
        refreshAddressBook()
        refreshLabelSnapshots()
        refreshUtxos()
    }

    private fun refreshActiveWalletSnapshotsIfNeeded(walletId: String) {
        if (walletId == repository.getActiveWalletId()) {
            refreshCurrentWalletSnapshots()
        }
    }

    /**
     * Verify the server is still alive after returning from background while
     * isConnected is still true (heartbeat may not have caught a drop yet).
     * If dead, reconnect immediately. If alive, restart the foreground heartbeat
     * and re-subscribe (the subscription socket likely died in background).
     */
    private suspend fun verifyConnectionOnForeground() {
        if (!isAppSessionUnlocked) return
        if (connectionJob?.isActive == true) return

        val alive =
            probeServerConnection(
                timeoutMs = FOREGROUND_RESUME_PING_TIMEOUT_MS,
                socketTimeoutMs = FOREGROUND_RESUME_PING_SOCKET_TIMEOUT_MS,
                lockTimeoutMs = FOREGROUND_RESUME_PING_LOCK_TIMEOUT_MS,
                allowReconnect = false,
            )

        if (!alive) {
            reconnectOnForeground()
        } else {
            startHeartbeat()
            launchSubscriptions(reason = "foreground_resume")
        }
    }

    private suspend fun probeServerConnection(
        timeoutMs: Long,
        socketTimeoutMs: Int,
        lockTimeoutMs: Long,
        allowReconnect: Boolean = true,
    ): Boolean =
        withTimeoutOrNull(timeoutMs) {
            withContext(Dispatchers.IO) {
                repository.pingServer(
                    socketTimeoutMs = socketTimeoutMs,
                    lockTimeoutMs = lockTimeoutMs,
                    allowReconnect = allowReconnect,
                )
            }
        } ?: false

    /**
     * Silent reconnect after returning from background with a dead connection.
     * Shows "Connecting" status in UI.
     */
    private suspend fun reconnectOnForeground() {
        if (!isAppSessionUnlocked) return
        if (repository.isUserDisconnected()) return
        if (connectionJob?.isActive == true) return
        val config = repository.getElectrumConfig() ?: return

        stopReconnectRetry()
        stopHeartbeat()
        _uiState.value = _uiState.value.copy(isConnecting = true, isConnected = false)

        val needsTor = config.isOnionAddress()

        if (needsTor && !torManager.isReady()) {
            torManager.start()
            if (!torManager.awaitReady(TOR_BOOTSTRAP_TIMEOUT_MS)) {
                _uiState.value = _uiState.value.copy(isConnecting = false, isConnected = false)
                _events.emit(WalletEvent.Error("Connection lost"))
                return
            }
            delay(TOR_POST_BOOTSTRAP_DELAY_MS)
        }

        val timeoutMs = if (needsTor) CONNECTION_TIMEOUT_TOR_MS else CONNECTION_TIMEOUT_CLEARNET_MS
        val result =
            withTimeoutOrNull(timeoutMs) {
                repository.connectToElectrum(config)
            }

        when (result) {
            is WalletResult.Success<*> -> {
                _uiState.value = _uiState.value.copy(isConnecting = false, isConnected = true)
                startHeartbeat()
                launchSubscriptions(reason = "foreground_reconnect")
            }
            else -> {
                _uiState.value = _uiState.value.copy(isConnecting = false, isConnected = false)
                _events.emit(WalletEvent.Error("Connection lost"))
                startReconnectRetry()
            }
        }
    }

    /**
     * Refresh the servers state from storage
     */
    private fun refreshServersState() {
        _serversState.value =
            ServersState(
                servers = repository.getAllElectrumServers(),
                activeServerId = repository.getActiveServerId(),
            )
    }

    /**
     * Import a wallet with full configuration
     */
    fun importWallet(config: WalletImportConfig) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            when (val result = repository.importWallet(config)) {
                is WalletResult.Success -> {
                    _uiState.value = _uiState.value.copy(isLoading = false)
                    _initialSyncComplete.value = false
                    _events.emit(WalletEvent.WalletImported)

                    if (_uiState.value.isConnected) {
                        if (repository.isWatchAddressWallet()) {
                            when (val syncResult = repository.syncWatchAddress(repository.getActiveWalletId() ?: return@launch)) {
                                is WalletResult.Success -> {
                                    refreshWalletLastFullSyncTime(repository.getActiveWalletId())
                                    _events.emit(WalletEvent.SyncCompleted)
                                }
                                is WalletResult.Error -> _events.emit(WalletEvent.Error(syncResult.message))
                            }
                            _initialSyncComplete.value = true
                        } else {
                            launchSubscriptions(reason = "wallet import")
                        }
                    } else {
                        _initialSyncComplete.value = true
                    }
                }
                is WalletResult.Error -> {
                    _uiState.value =
                        _uiState.value.copy(
                            isLoading = false,
                            error = result.message,
                        )
                    _events.emit(WalletEvent.Error(result.message))
                }
            }
        }
    }

    /**
     * Generate a new wallet with a fresh BIP39 mnemonic.
     * Uses BDK's Mnemonic constructor which sources entropy from the platform's
     * cryptographically secure random number generator (getrandom on Linux/Android).
     */
    fun generateWallet(config: WalletImportConfig) {
        importWallet(config)
    }

    fun importLiquidWatchOnlyWallet(name: String, ctDescriptor: String, gapLimit: Int) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            when (val result = repository.importLiquidWatchOnlyWallet(name, ctDescriptor, gapLimit)) {
                is WalletResult.Success -> {
                    _uiState.value = _uiState.value.copy(isLoading = false)
                    _events.emit(WalletEvent.WalletImported)
                }
                is WalletResult.Error -> {
                    _uiState.value = _uiState.value.copy(isLoading = false, error = result.message)
                    _events.emit(WalletEvent.Error(result.message))
                }
            }
        }
    }

    /**
     * Connect to an Electrum server
     * Automatically enables/disables Tor based on server type
     */
    fun connectToElectrum(config: ElectrumConfig) {
        if (!isAppSessionUnlocked) return
        val previousJob = connectionJob
        stopBackgroundSync()
        stopHeartbeat()
        stopReconnectRetry()
        repository.setUserDisconnected(false)
        connectionJob =
            viewModelScope.launch {
                previousJob?.cancelAndJoin()
                _uiState.value = _uiState.value.copy(isConnecting = true, isConnected = false, error = null, serverVersion = null)

                try {
                    // Auto-enable Tor for Tor-routed servers, auto-disable for clearnet
                    // (but keep Tor alive if fee/price source is .onion)
                    val serverRequiresTor = config.useTor || config.isOnionAddress()
                    val otherNeedsTor = isFeeSourceOnion() || isPriceSourceOnion()
                    val shouldKeepTorRunning = shouldKeepTorRunningForBitcoin(serverRequiresTor || otherNeedsTor)
                    disconnectForServerSwitch(shouldKeepTorRunning)
                    if (serverRequiresTor) {
                        repository.setTorEnabled(true)
                    } else if (repository.isTorEnabled()) {
                        // Switching to clearnet — Electrum won't use Tor proxy
                        repository.setTorEnabled(false)
                        // Only stop the Tor service if nothing else needs it
                        if (!shouldKeepTorRunningForBitcoin(otherNeedsTor)) {
                            torManager.stop()
                        }
                    }
                    if (serverRequiresTor && !torManager.isReady()) {
                        torManager.start()

                        if (!torManager.awaitReady(TOR_BOOTSTRAP_TIMEOUT_MS)) {
                            val msg = if (torState.value.status == TorStatus.ERROR) {
                                "Tor failed to start: ${torState.value.statusMessage}"
                            } else {
                                "Tor connection timed out"
                            }
                            _uiState.value = _uiState.value.copy(isConnecting = false, error = msg)
                            _events.emit(WalletEvent.Error(msg))
                            return@launch
                        }
                        // Brief settle time for the SOCKS proxy after bootstrap
                        delay(TOR_POST_BOOTSTRAP_DELAY_MS)
                    }

                    // Connect with timeout
                    val timeoutMs = if (serverRequiresTor) CONNECTION_TIMEOUT_TOR_MS else CONNECTION_TIMEOUT_CLEARNET_MS
                    val result =
                        withTimeoutOrNull(timeoutMs) {
                            repository.connectToElectrum(config)
                        }

                    when (result) {
                        null -> {
                            // Update UI immediately — don't wait for disconnect IO
                            _uiState.value =
                                _uiState.value.copy(
                                    isConnecting = false,
                                    isConnected = false,
                                    error = "Connection timed out",
                                )
                            _events.emit(WalletEvent.Error("Connection timed out"))
                            launch(Dispatchers.IO) { repository.disconnect() }
                            if (_autoSwitchServer.value) attemptAutoSwitch()
                        }
                        is WalletResult.Success<*> -> {
                            _uiState.value =
                                _uiState.value.copy(
                                    isConnecting = false,
                                    isConnected = true,
                                    serverVersion = null,
                                )
                            refreshServersState()
                            _events.emit(WalletEvent.Connected)
                            val serverVersion =
                                withContext(Dispatchers.IO) {
                                    repository.getServerVersion()
                                }
                            _uiState.value = _uiState.value.copy(serverVersion = serverVersion)
                            // Refresh fee estimates (server may support sub-sat fees)
                            fetchFeeEstimates()
                            startHeartbeat()
                            // Smart sync + real-time subscriptions (Sparrow-style).
                            // Runs in the background — does NOT block the connection
                            // flow. Creates a third upstream socket for push
                            // notifications. Over Tor this takes 3-10s extra.
                            launchSubscriptions(reason = "connect")
                        }
                        is WalletResult.Error -> {
                            _uiState.value = _uiState.value.copy(isConnecting = false, isConnected = false)
                            // Check the full cause chain for TOFU exceptions.
                            // SSLSocket.startHandshake() wraps TrustManager exceptions
                            // in SSLHandshakeException, so we need to unwrap.
                            val firstUse = result.exception?.findCause<CertificateFirstUseException>()
                            val mismatch = result.exception?.findCause<CertificateMismatchException>()
                            when {
                                firstUse != null -> {
                                    _certDialogState.value =
                                        CertDialogState(
                                            certInfo = firstUse.certInfo,
                                            isFirstUse = true,
                                            pendingConfig = config,
                                        )
                                }
                                mismatch != null -> {
                                    _certDialogState.value =
                                        CertDialogState(
                                            certInfo = mismatch.certInfo,
                                            isFirstUse = false,
                                            oldFingerprint = mismatch.storedFingerprint,
                                            pendingConfig = config,
                                        )
                                }
                                else -> {
                                    _uiState.value = _uiState.value.copy(error = result.message)
                                    _events.emit(WalletEvent.Error(result.message))
                                    if (_autoSwitchServer.value) attemptAutoSwitch()
                                }
                            }
                        }
                    }
                } finally {
                    connectionJob = null
                }
            }
    }

    private suspend fun disconnectForServerSwitch(shouldKeepTorRunning: Boolean) {
        withContext(Dispatchers.IO) {
            repository.disconnect()
        }

        val previousServerUsedTor =
            repository.getElectrumConfig()?.isOnionAddress() == true || repository.isTorEnabled()
        if (previousServerUsedTor && !shouldKeepTorRunning) {
            torManager.stop()
        }
    }

    /**
     * Quick sync wallet with the blockchain (revealed addresses only)
     * Used by balance screen refresh
     */
    fun sync() {
        viewModelScope.launch {
            // Watch address wallets use Electrum-only sync
            if (repository.isWatchAddressWallet()) {
                val activeId = repository.getActiveWalletId() ?: return@launch
                when (val result = repository.syncWatchAddress(activeId)) {
                    is WalletResult.Success -> {
                        refreshWalletLastFullSyncTime(activeId)
                        _events.emit(WalletEvent.SyncCompleted)
                    }
                    is WalletResult.Error -> _events.emit(WalletEvent.Error(result.message))
                }
                return@launch
            }
            when (val result = repository.sync()) {
                is WalletResult.Success -> {
                    refreshWalletLastFullSyncTime(repository.getActiveWalletId())
                    _events.emit(WalletEvent.SyncCompleted)
                }
                is WalletResult.Error -> {
                    // Mutex skip is not a real error — another sync is already
                    // running and will deliver the result. Silently ignore it.
                    if (!result.message.contains("already in progress")) {
                        _events.emit(WalletEvent.Error(result.message))
                    }
                }
            }
        }
    }

    /**
     * Attempt to connect to the next available saved server (round-robin).
     * Skips the currently active (failed) server and tries each remaining
     * server once. Stops on first successful connection.
     */
    private fun attemptAutoSwitch() {
        viewModelScope.launch {
            val currentId = _serversState.value.activeServerId
            val servers = _serversState.value.servers
            if (servers.size < 2) return@launch

            // Build candidate list: all servers except the one that just failed
            val candidates = servers.filter { it.id != currentId && it.id != null }
            if (candidates.isEmpty()) return@launch

            for (candidate in candidates) {
                val id = candidate.id ?: continue
                // connectToServer updates UI state, starts background sync, etc.
                connectToServer(id)
                // Wait for connection attempt to finish
                connectionJob?.join()
                if (_uiState.value.isConnected) {
                    _events.emit(WalletEvent.Error("Auto-switched to ${candidate.displayName()}"))
                    return@launch
                }
            }
            // All candidates failed
            _events.emit(WalletEvent.Error("Auto-switch failed: no reachable servers"))
        }
    }

    /**
     * Stop the background sync loop.
     */
    private fun stopBackgroundSync() {
        backgroundSyncJob?.cancel()
        backgroundSyncJob = null
    }

    /**
     * Start a lightweight heartbeat loop that pings the server via the proxy's
     * direct socket. Does NOT hold the sync mutex, so it runs independently of
     * background sync.
     *
     * Adaptive cadence: while pings succeed we sleep [HEARTBEAT_INTERVAL_MS]
     * (60s) — cheap enough to double as a TCP keep-alive against idle-timeout
     * servers. After the first failed ping we drop to
     * [HEARTBEAT_RETRY_INTERVAL_MS] (15s) so we confirm a genuine drop quickly
     * while still requiring [HEARTBEAT_MAX_FAILURES] consecutive failures to
     * avoid false positives from transient Tor/cellular blips. Worst-case
     * detection is ~90s from the first failure instead of ~3min.
     */
    private fun startHeartbeat() {
        // In background, only keep pinging when the foreground keep-alive
        // service is active — otherwise Android will likely suspend the
        // process and the ping is wasted. With keep-alive on, the ping also
        // doubles as a TCP keep-alive to prevent idle-timeout disconnects.
        if (isAppInBackground && !isBackgroundKeepAliveActive()) return
        if (heartbeatJob?.isActive == true) return
        heartbeatJob?.cancel()
        var consecutiveFailures = 0
        heartbeatJob =
            viewModelScope.launch {
                while (true) {
                    val interval =
                        if (consecutiveFailures == 0) {
                            HEARTBEAT_INTERVAL_MS
                        } else {
                            HEARTBEAT_RETRY_INTERVAL_MS
                        }
                    delay(interval)
                    if (!_uiState.value.isConnected) break

                    // Ping with a hard timeout so a blocking socket read
                    // can't stall detection beyond HEARTBEAT_PING_TIMEOUT_MS.
                    val alive =
                        withTimeoutOrNull(HEARTBEAT_PING_TIMEOUT_MS) {
                            withContext(Dispatchers.IO) {
                                repository.pingServer()
                            }
                        } ?: false

                    if (alive) {
                        consecutiveFailures = 0
                    } else {
                        consecutiveFailures++
                        if (consecutiveFailures >= HEARTBEAT_MAX_FAILURES) {
                            if (walletState.value.isFullSyncing) {
                                consecutiveFailures = 0
                                continue
                            }
                            handleConnectionLost()
                            break
                        }
                    }
                }
            }
    }

    /**
     * Stop the heartbeat loop.
     */
    private fun stopHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = null
    }

    private fun stopReconnectRetry() {
        reconnectRetryJob?.cancel()
        reconnectRetryJob = null
    }

    /**
     * React to a confirmed connection loss (from subscription EOF or heartbeat).
     * Tears down the dead connection, attempts auto-switch, and falls back to
     * an exponential-backoff retry loop to the same server.
     */
    private suspend fun handleConnectionLost() {
        if (repository.isUserDisconnected()) return
        // With the foreground keep-alive service on, self-heal in background
        // too; otherwise defer to the next foreground resume so we don't
        // burn battery retrying against a suspended process.
        if (isAppInBackground && !isBackgroundKeepAliveActive()) return
        if (_uiState.value.isConnecting || connectionJob?.isActive == true) return
        if (!_uiState.value.isConnected) return
        if (walletState.value.isSyncing || walletState.value.isFullSyncing || fullSyncJob?.isActive == true) return

        stopHeartbeat()
        stopBackgroundSync()
        stopReconnectRetry()

        withContext(Dispatchers.IO) { repository.disconnect() }
        _uiState.value = _uiState.value.copy(isConnected = false)
        _events.emit(WalletEvent.Error("Server connection lost"))

        val servers = _serversState.value.servers
        if (_autoSwitchServer.value && servers.size >= 2) {
            attemptAutoSwitch()
            connectionJob?.join()
            if (_uiState.value.isConnected) return
        }

        startReconnectRetry()
    }

    /**
     * Exponential-backoff retry loop: reconnects to the last-used server
     * after a connection loss when auto-switch is unavailable or failed.
     * Delays: 5s, 10s, 20s, 40s, 60s, 60s, ... (capped), max 10 attempts.
     */
    private fun startReconnectRetry() {
        if (reconnectRetryJob?.isActive == true) return
        reconnectRetryJob =
            viewModelScope.launch {
                val config = repository.getElectrumConfig() ?: return@launch
                var attempt = 0
                while (attempt < RECONNECT_MAX_ATTEMPTS) {
                    val delayMs = (RECONNECT_BASE_DELAY_MS shl attempt)
                        .coerceAtMost(RECONNECT_MAX_DELAY_MS)
                    delay(delayMs)

                    if (_uiState.value.isConnected) return@launch
                    if (repository.isUserDisconnected()) return@launch

                    attempt++
                    if (BuildConfig.DEBUG) {
                        android.util.Log.d(
                            "WalletViewModel",
                            "Reconnect retry $attempt/$RECONNECT_MAX_ATTEMPTS",
                        )
                    }

                    connectToElectrum(config)
                    connectionJob?.join()

                    if (_uiState.value.isConnected) return@launch
                }
            }
    }

    /**
     * Launch real-time subscriptions in the background (fire-and-forget).
     *
     * Creates a third upstream socket for Electrum push notifications.
     * Over Tor this takes 3-10s to establish the circuit, so it MUST NOT
     * block the connection flow. If subscriptions fail (e.g., Tor circuit
     * doesn't establish), the background sync polling covers us.
     *
     * Also handles the initial sync: if needsFullSync is set (first import),
     * runs full sync first. Otherwise compares subscription statuses to
     * persisted cache and only syncs if changes were detected.
     */
    private fun launchSubscriptions(reason: String = "startup") {
        viewModelScope.launch {
            val subResult =
                withContext(Dispatchers.IO) {
                    repository.startRealTimeSubscriptions()
                }
            when (subResult) {
                WalletRepository.SubscriptionResult.SYNCED,
                WalletRepository.SubscriptionResult.FULL_SYNCED,
                -> {
                    if (BuildConfig.DEBUG) {
                        android.util.Log.d(
                            "WalletViewModel",
                            "Realtime subscriptions active ($reason, result=$subResult)",
                        )
                    }
                    refreshWalletLastFullSyncTime(repository.getActiveWalletId())
                    _events.emit(WalletEvent.SyncCompleted)
                }
                WalletRepository.SubscriptionResult.NO_CHANGES -> {
                    if (BuildConfig.DEBUG) {
                        android.util.Log.d(
                            "WalletViewModel",
                            "Realtime subscriptions active ($reason, no sync needed)",
                        )
                    }
                }
                WalletRepository.SubscriptionResult.FAILED -> {
                    if (BuildConfig.DEBUG) {
                        android.util.Log.w(
                            "WalletViewModel",
                            "Realtime subscriptions failed ($reason); falling back to one-shot sync",
                        )
                    }
                    // Subscription socket failed (common over Tor).
                    // Background sync polling is already running as fallback.
                    // Run a one-time sync so the wallet is up-to-date.
                    when (val syncResult = repository.sync()) {
                        is WalletResult.Success -> {
                            if (BuildConfig.DEBUG) {
                                android.util.Log.d(
                                    "WalletViewModel",
                                    "One-shot sync fallback succeeded after subscription failure ($reason)",
                                )
                            }
                            refreshWalletLastFullSyncTime(repository.getActiveWalletId())
                            _events.emit(WalletEvent.SyncCompleted)
                        }
                        is WalletResult.Error -> {
                            if (!syncResult.message.contains("already in progress")) {
                                _events.emit(WalletEvent.Error(syncResult.message))
                            }
                            if (BuildConfig.DEBUG) {
                                android.util.Log.w(
                                    "WalletViewModel",
                                    "One-shot sync fallback failed after subscription failure ($reason): ${syncResult.message}",
                                )
                            }
                        }
                    }
                }
            }
            _initialSyncComplete.value = true
        }
    }

    /**
     * Full sync for a specific wallet (address discovery scan).
     * Used by Manage Wallets screen for manual full rescan.
     * If the wallet is not active, switches to it first.
     */
    fun fullSync(walletId: String) {
        fullSyncJob?.cancel()
        fullSyncCancelRequested = false
        val newJob =
            viewModelScope.launch {
                _syncingWalletId.value = walletId
                val previousActiveWalletId = repository.getActiveWalletId()
                val targetWallet =
                    repository.getWalletMetadata(walletId)
                        ?: run {
                            _events.emit(WalletEvent.Error("Wallet not found"))
                            return@launch
                        }
                val shouldRestorePreviousWallet =
                    previousActiveWalletId != null && previousActiveWalletId != walletId
                var switchedWalletForSync = false

                try {
                    // Switch to the wallet if it's not already active
                    if (shouldRestorePreviousWallet) {
                        when (val switchResult = repository.switchWallet(walletId)) {
                            is WalletResult.Success -> {
                                switchedWalletForSync = true
                            }
                            is WalletResult.Error -> {
                                _events.emit(WalletEvent.Error(switchResult.message))
                                return@launch
                            }
                        }
                    }

                    // Watch address wallets use Electrum-only sync
                    val result =
                        if (targetWallet.isWatchOnly && targetWallet.derivationPath == "single") {
                            repository.syncWatchAddress(walletId)
                        } else {
                            repository.requestFullSync(walletId)
                        }

                    if (fullSyncCancelRequested) return@launch

                    when (result) {
                        is WalletResult.Success -> {
                            refreshWalletLastFullSyncTimes()
                            _events.emit(WalletEvent.SyncCompleted)
                        }
                        is WalletResult.Error -> {
                            _events.emit(WalletEvent.Error(result.message))
                        }
                    }
                } catch (_: CancellationException) {
                    // User cancelled the manual full sync.
                } finally {
                    if (switchedWalletForSync) {
                        restoreActiveWalletAfterManualFullSync(
                            previousActiveWalletId = previousActiveWalletId,
                            syncedWalletId = walletId,
                        )
                    }
                    _syncingWalletId.value = null
                    if (fullSyncJob === kotlinx.coroutines.currentCoroutineContext()[Job]) {
                        fullSyncJob = null
                    }
                    fullSyncCancelRequested = false
                }
            }
        fullSyncJob = newJob
    }

    private suspend fun restoreActiveWalletAfterManualFullSync(
        previousActiveWalletId: String?,
        syncedWalletId: String,
    ) {
        if (previousActiveWalletId == null || previousActiveWalletId == syncedWalletId) return

        withContext(NonCancellable) {
            if (repository.getActiveWalletId() != syncedWalletId) return@withContext

            when (val restoreResult = repository.switchWallet(previousActiveWalletId)) {
                is WalletResult.Success -> {
                    refreshCurrentWalletSnapshots()
                    refreshWalletLastFullSyncTimes()
                    if (_uiState.value.isConnected) {
                        if (repository.isWatchAddressWallet()) {
                            _initialSyncComplete.value = true
                        } else {
                            launchSubscriptions()
                        }
                    } else {
                        _initialSyncComplete.value = true
                    }
                }
                is WalletResult.Error -> {
                    _events.emit(
                        WalletEvent.Error(
                            "Synced wallet, but couldn't restore the previous selection: ${restoreResult.message}",
                        ),
                    )
                }
            }
        }
    }

    fun cancelFullSync() {
        val serverId = _serversState.value.activeServerId ?: repository.getActiveServerId()
        val jobToCancel = fullSyncJob
        fullSyncCancelRequested = true
        fullSyncJob = null
        _syncingWalletId.value = null
        stopBackgroundSync()
        stopHeartbeat()
        _uiState.value = _uiState.value.copy(isConnecting = false, isConnected = false, serverVersion = null, error = null)
        viewModelScope.launch(Dispatchers.IO) {
            repository.abortActiveFullSync()
            jobToCancel?.cancelAndJoin()
            repository.setUserDisconnected(false)
            if (serverId != null) {
                withContext(Dispatchers.Main) {
                    connectToServer(serverId)
                }
            }
        }
    }

    /**
     * Get a new receiving address
     */
    fun getNewAddress() {
        viewModelScope.launch {
            when (val result = repository.getNewAddress()) {
                is WalletResult.Success -> {
                    refreshAddressBook()
                    _events.emit(WalletEvent.AddressGenerated(result.data))
                }
                is WalletResult.Error -> {
                    _events.emit(WalletEvent.Error(result.message))
                }
            }
        }
    }

    /**
     * Save a label for an address
     */
    fun saveAddressLabel(
        address: String,
        label: String,
    ) {
        repository.saveAddressLabel(address, label)
        patchAddressLabelInPlace(address, label.ifBlank { null })
        refreshLabelSnapshots()
    }

    /**
     * Delete a label for an address
     */
    fun deleteAddressLabel(address: String) {
        repository.deleteAddressLabel(address)
        patchAddressLabelInPlace(address, null)
        refreshLabelSnapshots()
    }

    /**
     * Update the in-memory address book with a changed label without re-deriving every
     * revealed address / re-scanning wallet outputs. Rebuilding the whole triple is expensive
     * on large wallets (5–7s) and the only thing that changed is a single label.
     */
    private fun patchAddressLabelInPlace(address: String, label: String?) {
        val (receive, change, used) = _allAddresses.value

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

        val patched =
            Triple(receive.patched(), change.patched(), used.patched())
        if (patched !== _allAddresses.value) {
            _allAddresses.value = patched
        }
    }

    /**
     * Get all address labels
     */
    fun getAllAddressLabels(): Map<String, String> {
        return _addressLabels.value
    }

    /**
     * Get all transaction labels
     */
    fun getAllTransactionLabels(): Map<String, String> {
        return _transactionLabels.value
    }

    /**
     * Save a label for a transaction
     */
    fun saveTransactionLabel(
        txid: String,
        label: String,
    ) {
        repository.saveTransactionLabel(txid, label)
        refreshLabelSnapshots()
    }

    fun deleteTransactionLabel(txid: String) {
        repository.deleteTransactionLabel(txid)
        refreshLabelSnapshots()
    }

    suspend fun searchTransactions(
        query: String,
        showSwapTransactions: Boolean,
        limit: Int,
    ): TransactionSearchResult =
        withContext(Dispatchers.IO) {
            repository.searchBitcoinTransactionTxids(
                query = query,
                showSwapTransactions = showSwapTransactions,
                limit = limit,
            )
        }

    /**
     * Get all addresses (receive, change, used)
     */
    fun getAllAddresses(): Triple<List<WalletAddress>, List<WalletAddress>, List<WalletAddress>> {
        return _allAddresses.value
    }

    /**
     * Get all UTXOs
     */
    fun getAllUtxos(): List<UtxoInfo> {
        return _allUtxos.value
    }

    /**
     * Refresh the UTXO list asynchronously on IO.
     * Called after freeze/unfreeze since that changes a local flag
     * that doesn't trigger a walletState emission.
     */
    fun refreshUtxos() {
        viewModelScope.launch(Dispatchers.IO) {
            _allUtxos.value = repository.getAllUtxos()
        }
    }

    /**
     * Freeze/unfreeze a UTXO
     */
    fun setUtxoFrozen(
        outpoint: String,
        frozen: Boolean,
    ) {
        repository.setUtxoFrozen(outpoint, frozen)
        refreshUtxos()
    }

    // ==================== BIP 329 Labels ====================

    /**
     * Get label counts (address, transaction) for a wallet.
     */
    fun getLabelCounts(walletId: String): Pair<Int, Int> {
        return repository.getLabelCounts(walletId)
    }

    fun getBitcoinBip329LabelsContent(
        walletId: String,
        includeNetworkTag: Boolean = false,
    ): String {
        if (!includeNetworkTag) {
            return repository.exportBip329Labels(walletId)
        }

        val metadata = secureStorage.getWalletMetadata(walletId)
        val origin = metadata?.let {
            Bip329Labels.buildOrigin(
                addressType = it.addressType,
                fingerprint = it.masterFingerprint,
            )
        }
        return Bip329Labels.export(
            addressLabels = repository.getAllAddressLabelsForWallet(walletId),
            transactionLabels = repository.getAllTransactionLabelsForWallet(walletId),
            origin = origin,
            network = Bip329LabelNetwork.BITCOIN,
        )
    }

    fun importBitcoinBip329LabelsFromContent(
        walletId: String,
        content: String,
        defaultScope: Bip329LabelScope = Bip329LabelScope.BITCOIN,
    ): Int {
        val result = Bip329Labels.import(content, defaultScope)

        repository.saveAddressLabelsForWallet(walletId, result.bitcoinAddressLabels)
        repository.saveTransactionLabelsForWallet(walletId, result.bitcoinTransactionLabels)

        if (result.outputSpendable.isNotEmpty()) {
            val frozenOutpoints = repository.getFrozenUtxosForWallet(walletId).toMutableSet()
            for ((outpoint, spendable) in result.outputSpendable) {
                if (spendable) {
                    frozenOutpoints.remove(outpoint)
                } else {
                    frozenOutpoints.add(outpoint)
                }
            }
            repository.setFrozenUtxosForWallet(walletId, frozenOutpoints)
        }

        refreshActiveWalletSnapshotsIfNeeded(walletId)
        return result.totalBitcoinLabelsImported
    }

    /**
     * Set a pre-selected UTXO for the Send screen (from AllUtxosScreen)
     */
    fun setPreSelectedUtxo(utxo: UtxoInfo) {
        _preSelectedUtxo.value = utxo
    }

    /**
     * Clear the pre-selected UTXO (after SendScreen has consumed it)
     */
    fun clearPreSelectedUtxo() {
        _preSelectedUtxo.value = null
    }

    /**
     * Update send screen draft state (persists while app is open)
     */
    fun updateSendScreenDraft(draft: SendScreenDraft) {
        _sendScreenDraft.value = draft
    }

    fun checkSendRecipientIsSelfTransfer(address: String) {
        selfTransferCheckJob?.cancel()
        val requestToken = ++selfTransferRequestToken
        val trimmed = address.trim()
        if (trimmed.isBlank()) {
            _sendRecipientIsSelfTransfer.value = false
            return
        }

        selfTransferCheckJob =
            viewModelScope.launch {
                val isOwned = repository.isAddressOwnedByActiveWallet(trimmed)
                if (requestToken == selfTransferRequestToken) {
                    _sendRecipientIsSelfTransfer.value = isOwned
                }
            }
    }

    /**
     * Set a pending send input from an external intent or NFC scan.
     * IbisWalletApp will consume it after unlock and navigate to Send.
     */
    fun setPendingSendInput(input: String?) {
        _pendingSendInput.value = input
    }

    /**
     * Consume the pending send input (called after navigation to Send).
     */
    fun consumePendingSendInput() {
        _pendingSendInput.value = null
    }

    /**
     * Clear send screen draft (called on successful send or app close)
     */
    private fun clearSendScreenDraft() {
        _sendScreenDraft.value = SendScreenDraft()
        repository.invalidatePreparedSendCache()
    }

    private fun currentBitcoinSendPreparationState(): BitcoinSendPreparationState {
        val currentWalletState = walletState.value
        return BitcoinSendPreparationState(
            walletId = currentWalletState.activeWallet?.id,
            balanceSats = currentWalletState.balanceSats,
            pendingIncomingSats = currentWalletState.pendingIncomingSats,
            pendingOutgoingSats = currentWalletState.pendingOutgoingSats,
            transactionCount = currentWalletState.transactions.size,
            spendUnconfirmed = repository.getSpendUnconfirmed(),
        )
    }

    /**
     * Send Bitcoin to an address with specified fee rate
     * @param selectedUtxos Optional list of specific UTXOs to spend from (coin control)
     * @param label Optional label for the transaction
     * @param isMaxSend If true, resolves the largest exact send amount with fees on top
     */
    fun sendBitcoin(
        recipientAddress: String,
        amountSats: ULong,
        feeRate: Double = 1.0,
        selectedUtxos: List<UtxoInfo>? = null,
        label: String? = null,
        isMaxSend: Boolean = false,
        precomputedFeeSats: Long? = null,
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSending = true, sendStatus = null, error = null)

            when (
                val result =
                    repository.sendBitcoin(
                        recipientAddress,
                        amountSats,
                        feeRate,
                        selectedUtxos,
                        label,
                        isMaxSend,
                        precomputedFeeSats = precomputedFeeSats?.toULong(),
                        onProgress = { status ->
                            _uiState.value = _uiState.value.copy(sendStatus = status)
                        },
                    )
            ) {
                is WalletResult.Success -> {
                    _uiState.value = _uiState.value.copy(isSending = false, sendStatus = null)
                    // Clear send screen draft on successful transaction
                    clearSendScreenDraft()
                    _events.emit(WalletEvent.TransactionSent(result.data))
                    // Quick sync to update balance and show the new outgoing tx
                    sync()
                }
                is WalletResult.Error -> {
                    _uiState.value =
                        _uiState.value.copy(
                            isSending = false,
                            sendStatus = null,
                            error = result.message,
                        )
                    _events.emit(WalletEvent.Error(result.message))
                }
            }
        }
    }

    suspend fun sendBitcoinForSwap(
        recipientAddress: String,
        amountSats: Long,
        feeRate: Double,
        selectedUtxos: List<UtxoInfo>? = null,
        isMaxSend: Boolean = false,
    ): Pair<String, Long> {
        val recipientAmountSats =
            if (isMaxSend) {
                getMaxBitcoinSpendableForSwap(
                    recipientAddress = recipientAddress,
                    feeRate = feeRate,
                    selectedUtxos = selectedUtxos,
                )
            } else {
                amountSats
            }
        return when (
            val result = repository.sendBitcoin(
                recipientAddress = recipientAddress,
                amountSats = recipientAmountSats.toULong(),
                feeRateSatPerVb = feeRate,
                selectedUtxos = selectedUtxos,
                isMaxSend = false,
                onProgress = {},
            )
        ) {
            is WalletResult.Success -> {
                sync()
                result.data to recipientAmountSats
            }
            is WalletResult.Error -> {
                val message = result.exception?.message?.takeIf { it.isNotBlank() } ?: result.message
                throw Exception(message, result.exception)
            }
        }
    }

    suspend fun getMaxBitcoinSpendableForSwap(
        recipientAddress: String,
        feeRate: Double,
        selectedUtxos: List<UtxoInfo>? = null,
    ): Long {
        return repository.dryRunBuildTx(
            recipientAddress = recipientAddress,
            amountSats = 0u,
            feeRateSatPerVb = feeRate,
            selectedUtxos = selectedUtxos,
            isMaxSend = true,
        )?.recipientAmountSats?.takeIf { it > 0 }
            ?: throw Exception("Unable to compute max spendable Bitcoin amount")
    }

    suspend fun previewBitcoinFundingForSwap(
        recipientAddress: String,
        amountSats: Long,
        feeRate: Double,
        selectedUtxos: List<UtxoInfo>? = null,
        isMaxSend: Boolean = false,
    ): DryRunResult? {
        return repository.dryRunBuildTx(
            recipientAddress = recipientAddress,
            amountSats = amountSats.toULong(),
            feeRateSatPerVb = feeRate,
            selectedUtxos = selectedUtxos,
            isMaxSend = isMaxSend,
        )
    }

    /**
     * Estimate the actual fee using BDK's TxBuilder (dry-run, no network).
     * Call this when the user changes amount, fee rate, or UTXO selection.
     */
    fun estimateFee(
        recipientAddress: String,
        amountSats: ULong,
        feeRate: Double,
        selectedUtxos: List<UtxoInfo>? = null,
        isMaxSend: Boolean = false,
    ) {
        val requestKey =
            buildSingleBitcoinSendPreparationKey(
                state = currentBitcoinSendPreparationState(),
                recipientAddress = recipientAddress,
                amountSats = amountSats,
                feeRateSatPerVb = feeRate,
                selectedOutpoints = selectedUtxos?.map { it.outpoint }.orEmpty(),
                isMaxSend = isMaxSend,
            )
        if (_isDryRunInProgress.value && requestKey == activeDryRunRequestKey) {
            return
        }
        if (!_isDryRunInProgress.value && requestKey == completedDryRunRequestKey && _dryRunResult.value != null) {
            return
        }

        dryRunJob?.cancel()
        val requestToken = ++dryRunRequestToken
        activeDryRunRequestKey = requestKey
        _dryRunResult.value = null
        _isDryRunInProgress.value = true
        dryRunJob =
            viewModelScope.launch {
                try {
                    val result =
                        repository.dryRunBuildTx(
                            recipientAddress = recipientAddress,
                            amountSats = amountSats,
                            feeRateSatPerVb = feeRate,
                            selectedUtxos = selectedUtxos,
                            isMaxSend = isMaxSend,
                            preparePsbtDetails =
                                walletState.value.activeWallet?.let { wallet ->
                                    wallet.isWatchOnly || wallet.policyType == WalletPolicyType.MULTISIG
                                } == true,
                        )
                    if (requestToken == dryRunRequestToken) {
                        _dryRunResult.value = result
                        completedDryRunRequestKey = requestKey
                    }
                } finally {
                    if (requestToken == dryRunRequestToken) {
                        activeDryRunRequestKey = null
                        _isDryRunInProgress.value = false
                    }
                }
            }
    }

    fun clearDryRunResult() {
        dryRunJob?.cancel()
        dryRunRequestToken += 1
        activeDryRunRequestKey = null
        completedDryRunRequestKey = null
        _dryRunResult.value = null
        _isDryRunInProgress.value = false
        repository.invalidatePreparedSendCache()
    }

    /** Estimate fee for a multi-recipient transaction. */
    fun estimateFeeMulti(
        recipients: List<Recipient>,
        feeRate: Double,
        selectedUtxos: List<UtxoInfo>? = null,
    ) {
        val requestKey =
            buildMultiBitcoinSendPreparationKey(
                state = currentBitcoinSendPreparationState(),
                recipients = recipients,
                feeRateSatPerVb = feeRate,
                selectedOutpoints = selectedUtxos?.map { it.outpoint }.orEmpty(),
            )
        if (_isDryRunInProgress.value && requestKey == activeDryRunRequestKey) {
            return
        }
        if (!_isDryRunInProgress.value && requestKey == completedDryRunRequestKey && _dryRunResult.value != null) {
            return
        }

        dryRunJob?.cancel()
        val requestToken = ++dryRunRequestToken
        activeDryRunRequestKey = requestKey
        _dryRunResult.value = null
        _isDryRunInProgress.value = true
        dryRunJob =
            viewModelScope.launch {
                try {
                    val result =
                        repository.dryRunBuildTx(
                            recipients = recipients,
                            feeRateSatPerVb = feeRate,
                            selectedUtxos = selectedUtxos,
                            preparePsbtDetails =
                                walletState.value.activeWallet?.let { wallet ->
                                    wallet.isWatchOnly || wallet.policyType == WalletPolicyType.MULTISIG
                                } == true,
                        )
                    if (requestToken == dryRunRequestToken) {
                        _dryRunResult.value = result
                        completedDryRunRequestKey = requestKey
                    }
                } finally {
                    if (requestToken == dryRunRequestToken) {
                        activeDryRunRequestKey = null
                        _isDryRunInProgress.value = false
                    }
                }
            }
    }

    /** Send to multiple recipients in a single transaction. */
    fun sendBitcoinMulti(
        recipients: List<Recipient>,
        feeRate: Double = 1.0,
        selectedUtxos: List<UtxoInfo>? = null,
        label: String? = null,
        precomputedFeeSats: Long? = null,
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSending = true, sendStatus = null, error = null)
            when (
                val result =
                    repository.sendBitcoin(
                        recipients,
                        feeRate,
                        selectedUtxos,
                        label,
                        precomputedFeeSats = precomputedFeeSats?.toULong(),
                        onProgress = { status ->
                            _uiState.value = _uiState.value.copy(sendStatus = status)
                        },
                    )
            ) {
                is WalletResult.Success -> {
                    _uiState.value = _uiState.value.copy(isSending = false, sendStatus = null)
                    clearSendScreenDraft()
                    _events.emit(WalletEvent.TransactionSent(result.data))
                    // Quick sync to update balance and show the new outgoing tx
                    sync()
                }
                is WalletResult.Error -> {
                    _uiState.value = _uiState.value.copy(isSending = false, sendStatus = null, error = result.message)
                    _events.emit(WalletEvent.Error(result.message))
                }
            }
        }
    }

    /** Create PSBT with multiple recipients for watch-only wallets. */
    fun createPsbtMulti(
        recipients: List<Recipient>,
        feeRate: Double = 1.0,
        selectedUtxos: List<UtxoInfo>? = null,
        label: String? = null,
        precomputedFeeSats: Long? = null,
    ) {
        viewModelScope.launch {
            _psbtState.value = _psbtState.value.copy(isCreating = true, error = null)
            when (
                val result =
                    repository.createPsbt(
                        recipients,
                        feeRate,
                        selectedUtxos,
                        label,
                        precomputedFeeSats = precomputedFeeSats?.toULong(),
                    )
            ) {
                is WalletResult.Success -> {
                    val details = result.data
                    _psbtState.value =
                        _psbtState.value.copy(
                            isCreating = false,
                            psbtId = details.psbtId,
                            unsignedPsbtBase64 = details.psbtBase64,
                            signerExportPsbtBase64 = details.signerExportPsbtBase64,
                            signedData =
                                if (details.requiredSignatures != null &&
                                    details.presentSignatures >= details.requiredSignatures
                                ) {
                                    details.signerExportPsbtBase64
                                } else {
                                    null
                                },
                            pendingLabel = label,
                            presentSignatures = details.presentSignatures,
                            requiredSignatures = details.requiredSignatures,
                            isReadyToBroadcast =
                                details.requiredSignatures != null &&
                                    details.presentSignatures >= details.requiredSignatures,
                            actualFeeSats = details.feeSats,
                            recipientAddress = details.recipientAddress,
                            recipientAmountSats = details.recipientAmountSats,
                            changeAmountSats = details.changeAmountSats,
                            totalInputSats = details.totalInputSats,
                        )
                    clearSendScreenDraft()
                    _events.emit(WalletEvent.PsbtCreated(details.signerExportPsbtBase64))
                }
                is WalletResult.Error -> {
                    _psbtState.value = _psbtState.value.copy(isCreating = false, error = result.message)
                    _events.emit(WalletEvent.Error(result.message))
                }
            }
        }
    }

    /**
     * Create an unsigned PSBT for a watch-only wallet.
     * Does not sign or broadcast - the PSBT is exported for external signing.
     */
    fun createPsbt(
        recipientAddress: String,
        amountSats: ULong,
        feeRate: Double = 1.0,
        selectedUtxos: List<UtxoInfo>? = null,
        label: String? = null,
        isMaxSend: Boolean = false,
        precomputedFeeSats: Long? = null,
    ) {
        viewModelScope.launch {
            _psbtState.value = _psbtState.value.copy(isCreating = true, error = null)

            when (
                val result =
                    repository.createPsbt(
                        recipientAddress,
                        amountSats,
                        feeRate,
                        selectedUtxos,
                        label,
                        isMaxSend,
                        precomputedFeeSats = precomputedFeeSats?.toULong(),
                    )
            ) {
                is WalletResult.Success -> {
                    val details = result.data
                    _psbtState.value =
                        _psbtState.value.copy(
                            isCreating = false,
                            psbtId = details.psbtId,
                            unsignedPsbtBase64 = details.psbtBase64,
                            signerExportPsbtBase64 = details.signerExportPsbtBase64,
                            signedData =
                                if (details.requiredSignatures != null &&
                                    details.presentSignatures >= details.requiredSignatures
                                ) {
                                    details.signerExportPsbtBase64
                                } else {
                                    null
                                },
                            pendingLabel = label,
                            presentSignatures = details.presentSignatures,
                            requiredSignatures = details.requiredSignatures,
                            isReadyToBroadcast =
                                details.requiredSignatures != null &&
                                    details.presentSignatures >= details.requiredSignatures,
                            actualFeeSats = details.feeSats,
                            recipientAddress = details.recipientAddress,
                            recipientAmountSats = details.recipientAmountSats,
                            changeAmountSats = details.changeAmountSats,
                            totalInputSats = details.totalInputSats,
                        )
                    clearSendScreenDraft()
                    _events.emit(WalletEvent.PsbtCreated(details.signerExportPsbtBase64))
                }
                is WalletResult.Error -> {
                    _psbtState.value =
                        _psbtState.value.copy(
                            isCreating = false,
                            error = result.message,
                        )
                    _events.emit(WalletEvent.Error(result.message))
                }
            }
        }
    }

    private suspend fun handlePsbtDetailsResult(
        result: WalletResult<PsbtDetails>,
        label: String? = null,
    ) {
        when (result) {
            is WalletResult.Success -> {
                val details = result.data
                _psbtState.value =
                    _psbtState.value.copy(
                        isCreating = false,
                        psbtId = details.psbtId,
                        unsignedPsbtBase64 = details.psbtBase64,
                        signerExportPsbtBase64 = details.signerExportPsbtBase64,
                        signedData =
                            if (details.requiredSignatures != null &&
                                details.presentSignatures >= details.requiredSignatures
                            ) {
                                details.signerExportPsbtBase64
                            } else {
                                null
                            },
                        pendingLabel = label,
                        presentSignatures = details.presentSignatures,
                        requiredSignatures = details.requiredSignatures,
                        isReadyToBroadcast =
                            details.requiredSignatures != null &&
                                details.presentSignatures >= details.requiredSignatures,
                        actualFeeSats = details.feeSats,
                        recipientAddress = details.recipientAddress,
                        recipientAmountSats = details.recipientAmountSats,
                        changeAmountSats = details.changeAmountSats,
                        totalInputSats = details.totalInputSats,
                        error = null,
                    )
                _uiState.value = _uiState.value.copy(isSending = false)
                _events.emit(WalletEvent.PsbtCreated(details.signerExportPsbtBase64))
            }
            is WalletResult.Error -> {
                _psbtState.value = _psbtState.value.copy(isCreating = false, error = result.message)
                _uiState.value =
                    _uiState.value.copy(
                        isSending = false,
                        error = result.message,
                    )
                _events.emit(WalletEvent.Error(result.message))
            }
        }
    }

    /**
     * Store signed transaction data for user confirmation before broadcasting.
     * Called after scanning the signed PSBT/tx back from the hardware wallet.
     */
    fun setSignedTransactionData(data: String) {
        val sessionId = _psbtState.value.psbtId
        val isHex = data.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }
        if (sessionId == null || (isHex && data.length % 2 == 0 && data.length > 20)) {
            _psbtState.value = _psbtState.value.copy(signedData = data, error = null)
            return
        }

        viewModelScope.launch {
            _psbtState.value = _psbtState.value.copy(isCombining = true, error = null)
            when (val result = repository.combinePsbtPartial(sessionId, data)) {
                is WalletResult.Success -> {
                    val session = result.data
                    _psbtState.value =
                        _psbtState.value.copy(
                            isCombining = false,
                            signerExportPsbtBase64 = session.signerExportPsbtBase64,
                            signedData =
                                if (session.status == PsbtSessionStatus.READY_TO_BROADCAST) {
                                    session.workingPsbtBase64
                                } else {
                                    null
                                },
                            presentSignatures = session.presentSignatures,
                            requiredSignatures = session.requiredSignatures,
                            isReadyToBroadcast = session.status == PsbtSessionStatus.READY_TO_BROADCAST,
                            error = null,
                        )
                }
                is WalletResult.Error -> {
                    _psbtState.value =
                        _psbtState.value.copy(
                            isCombining = false,
                            error = result.message,
                        )
                    _events.emit(WalletEvent.Error(result.message))
                }
            }
        }
    }

    /**
     * Broadcast the signed transaction after user confirmation.
     * Uses the signed data previously stored via setSignedTransactionData().
     */
    fun confirmBroadcast() {
        val data = _psbtState.value.signedData ?: return
        viewModelScope.launch {
            _psbtState.value = _psbtState.value.copy(isBroadcasting = true, broadcastStatus = null, error = null)
            val pendingLabel = _psbtState.value.pendingLabel
            val unsignedPsbt = _psbtState.value.unsignedPsbtBase64

            val onProgress: (String) -> Unit = { status ->
                _psbtState.value = _psbtState.value.copy(broadcastStatus = status)
            }

            // Auto-detect format: base64 PSBT vs raw tx hex
            val isHex = data.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }
            val result =
                if (isHex && data.length % 2 == 0 && data.length > 20) {
                    if (unsignedPsbt == null) {
                        WalletResult.Error("Original PSBT missing. Cannot verify raw transaction before broadcast.")
                    } else {
                        repository.broadcastRawTx(
                            txHex = data,
                            pendingLabel = pendingLabel,
                            onProgress = onProgress,
                            unsignedPsbtBase64 = unsignedPsbt,
                        )
                    }
                } else if (unsignedPsbt == null) {
                    WalletResult.Error("Original PSBT missing. Cannot verify signed PSBT before broadcast.")
                } else {
                    repository.broadcastSignedPsbt(data, unsignedPsbt, pendingLabel, onProgress)
                }

            when (result) {
                is WalletResult.Success -> {
                    _psbtState.value = PsbtState() // Reset PSBT state
                    _uiState.value = _uiState.value.copy(isSending = false)
                    _events.emit(WalletEvent.TransactionSent(result.data))
                    // Quick sync to update balance and show the broadcast tx
                    sync()
                }
                is WalletResult.Error -> {
                    _psbtState.value =
                        _psbtState.value.copy(
                            isBroadcasting = false,
                            broadcastStatus = null,
                            error = result.message,
                        )
                    _events.emit(WalletEvent.Error(result.message))
                }
            }
        }
    }

    /**
     * Cancel the pending broadcast and return to the PSBT QR display.
     * Clears signed data but keeps the unsigned PSBT for re-scanning.
     */
    fun cancelBroadcast() {
        _psbtState.value = _psbtState.value.copy(signedData = null, error = null)
    }

    /**
     * Clear the PSBT state (e.g., when navigating away from PSBT screen)
     */
    fun clearPsbtState() {
        _psbtState.value = PsbtState()
        repository.invalidatePreparedSendCache()
    }

    /**
     * Decode a manual-broadcast payload (raw tx hex or signed PSBT base64) and
     * cache the structured preview in [manualBroadcastState]. The UI requires
     * the user to look at this preview (outputs + ownership) before the
     * Broadcast button actually fires, so a malicious paste cannot push funds
     * out without an on-screen review.
     */
    fun previewManualBroadcast(data: String) {
        viewModelScope.launch {
            val preview = repository.decodeManualBroadcastPreview(data)
            _manualBroadcastState.value =
                _manualBroadcastState.value.copy(
                    preview = preview,
                    previewInput = data.trim(),
                    error = null,
                )
        }
    }

    /**
     * Broadcast a manually provided signed transaction (raw hex or signed PSBT).
     * Standalone — the transaction may not belong to any loaded wallet.
     *
     * Callers must have already populated [ManualBroadcastState.preview] for
     * exactly this input via [previewManualBroadcast]; otherwise the broadcast
     * is refused so the user cannot bypass the on-screen output review.
     */
    fun broadcastManualTransaction(data: String) {
        viewModelScope.launch {
            val current = _manualBroadcastState.value
            val trimmed = data.trim()
            if (current.preview == null || current.previewInput != trimmed) {
                _manualBroadcastState.value =
                    current.copy(
                        error = "Review the decoded outputs before broadcasting.",
                    )
                return@launch
            }
            _manualBroadcastState.value =
                current.copy(isBroadcasting = true, error = null, broadcastStatus = null)
            val result =
                repository.broadcastManualData(data) { status ->
                    _manualBroadcastState.value =
                        _manualBroadcastState.value.copy(
                            broadcastStatus = status,
                        )
                }
            when (result) {
                is WalletResult.Success -> {
                    _manualBroadcastState.value = ManualBroadcastState(txid = result.data)
                }
                is WalletResult.Error -> {
                    _manualBroadcastState.value =
                        _manualBroadcastState.value.copy(
                            isBroadcasting = false,
                            broadcastStatus = null,
                            error = result.message,
                        )
                }
            }
        }
    }

    /**
     * Clear the manual broadcast state (e.g., when navigating away)
     */
    fun clearManualBroadcastState() {
        _manualBroadcastState.value = ManualBroadcastState()
    }

    /**
     * Bump fee of an unconfirmed transaction using RBF
     */
    fun bumpFee(
        txid: String,
        newFeeRate: Double,
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSending = true, error = null)

            if (walletState.value.activeWallet?.let { it.isWatchOnly || it.policyType == WalletPolicyType.MULTISIG } == true) {
                _psbtState.value = _psbtState.value.copy(isCreating = true, error = null)
                handlePsbtDetailsResult(repository.createBumpFeePsbt(txid, newFeeRate))
                return@launch
            }

            when (val result = repository.bumpFee(txid, newFeeRate)) {
                is WalletResult.Success -> {
                    _uiState.value = _uiState.value.copy(isSending = false)
                    _events.emit(WalletEvent.FeeBumped(result.data))
                    // Quick sync to update balance and show the replacement tx
                    sync()
                }
                is WalletResult.Error -> {
                    _uiState.value =
                        _uiState.value.copy(
                            isSending = false,
                            error = result.message,
                        )
                    _events.emit(WalletEvent.Error(result.message))
                }
            }
        }
    }

    /**
     * Speed up an incoming transaction using CPFP
     */
    fun cpfp(
        parentTxid: String,
        feeRate: Double,
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSending = true, error = null)

            if (walletState.value.activeWallet?.let { it.isWatchOnly || it.policyType == WalletPolicyType.MULTISIG } == true) {
                _psbtState.value = _psbtState.value.copy(isCreating = true, error = null)
                handlePsbtDetailsResult(repository.createCpfpPsbt(parentTxid, feeRate))
                return@launch
            }

            when (val result = repository.cpfp(parentTxid, feeRate)) {
                is WalletResult.Success -> {
                    _uiState.value = _uiState.value.copy(isSending = false)
                    _events.emit(WalletEvent.CpfpCreated(result.data))
                    // Quick sync to update balance and show the CPFP tx
                    sync()
                }
                is WalletResult.Error -> {
                    _uiState.value =
                        _uiState.value.copy(
                            isSending = false,
                            error = result.message,
                        )
                    _events.emit(WalletEvent.Error(result.message))
                }
            }
        }
    }

    /**
     * Cancel an unconfirmed sent transaction by redirecting all funds back to the wallet via RBF.
     */
    fun redirectTransaction(
        txid: String,
        newFeeRate: Double,
        destinationAddress: String? = null,
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSending = true, error = null)

            if (walletState.value.activeWallet?.let { it.isWatchOnly || it.policyType == WalletPolicyType.MULTISIG } == true) {
                _psbtState.value = _psbtState.value.copy(isCreating = true, error = null)
                handlePsbtDetailsResult(repository.createRedirectPsbt(txid, newFeeRate, destinationAddress))
                return@launch
            }

            when (val result = repository.redirectTransaction(txid, newFeeRate, destinationAddress)) {
                is WalletResult.Success -> {
                    _uiState.value = _uiState.value.copy(isSending = false)
                    _events.emit(WalletEvent.TransactionRedirected(result.data))
                    sync()
                }
                is WalletResult.Error -> {
                    _uiState.value =
                        _uiState.value.copy(
                            isSending = false,
                            error = result.message,
                        )
                    _events.emit(WalletEvent.Error(result.message))
                }
            }
        }
    }

    /**
     * Check if a transaction can be bumped with RBF
     */
    fun canBumpFee(txid: String): Boolean {
        return repository.canBumpFee(txid)
    }

    /**
     * Check if a transaction can be sped up with CPFP
     */
    fun canCpfp(txid: String): Boolean {
        return repository.canCpfp(txid)
    }

    /**
     * Fetch transaction vsize from Electrum server
     * Returns the fractional vsize (weight / 4.0) from the network
     */
    suspend fun fetchTransactionVsize(txid: String): Double? {
        return repository.fetchTransactionVsizeFromElectrum(txid)
    }

    /**
     * Delete a specific wallet by ID
     */
    suspend fun deleteWallet(walletId: String): WalletResult<Unit> {
        _uiState.value = _uiState.value.copy(isLoading = true)

        return when (val result = repository.deleteWallet(walletId)) {
            is WalletResult.Success -> {
                _uiState.value = _uiState.value.copy(isLoading = false)
                refreshWalletLastFullSyncTimes()
                _events.emit(WalletEvent.WalletDeleted)
                result
            }
            is WalletResult.Error -> {
                _uiState.value =
                    _uiState.value.copy(
                        isLoading = false,
                        error = result.message,
                    )
                _events.emit(WalletEvent.Error(result.message))
                result
            }
        }
    }

    /**
     * Edit wallet metadata (name and optionally fingerprint for watch-only)
     */
    fun editWallet(
        walletId: String,
        newName: String,
        newGapLimit: Int,
        newFingerprint: String? = null,
    ) {
        repository.editWallet(walletId, newName, newGapLimit, newFingerprint)
    }

    /**
     * Set whether a wallet requires app authentication before opening.
     */
    fun setWalletLocked(walletId: String, locked: Boolean) {
        repository.setWalletLocked(walletId, locked)
    }

    /**
     * Reorder wallets to the given ID order.
     * Persists the new order and updates the wallet list for all screens.
     */
    fun reorderWallets(orderedIds: List<String>) {
        repository.reorderWallets(orderedIds)
        refreshWalletLastFullSyncTimes()
    }

    /**
     * Switch to a different wallet
     */
    fun switchWallet(walletId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            _initialSyncComplete.value = false

            when (val result = repository.switchWallet(walletId)) {
                is WalletResult.Success -> {
                    _uiState.value = _uiState.value.copy(isLoading = false)
                    refreshCurrentWalletSnapshots()
                    refreshWalletLastFullSyncTimes()
                    _events.emit(WalletEvent.WalletSwitched)
                    if (_uiState.value.isConnected) {
                        when (val syncResult = repository.sync()) {
                            is WalletResult.Success -> {
                                refreshWalletLastFullSyncTime(walletId)
                                _events.emit(WalletEvent.SyncCompleted)
                            }
                            is WalletResult.Error -> {
                                if (!syncResult.message.contains("already in progress")) {
                                    _events.emit(WalletEvent.Error(syncResult.message))
                                }
                            }
                        }
                        if (repository.isWatchAddressWallet()) {
                            _initialSyncComplete.value = true
                        } else {
                            launchSubscriptions()
                        }
                    } else {
                        _initialSyncComplete.value = true
                    }
                }
                is WalletResult.Error -> {
                    _uiState.value =
                        _uiState.value.copy(
                            isLoading = false,
                            error = result.message,
                        )
                    _events.emit(WalletEvent.Error(result.message))
                }
            }
        }
    }

    /**
     * Get stored Electrum config (active server)
     */
    fun getElectrumConfig(): ElectrumConfig? = repository.getElectrumConfig()

    /**
     * Save a new Electrum server
     */
    fun saveServer(config: ElectrumConfig): ElectrumConfig {
        val savedConfig = repository.saveElectrumServer(config)
        refreshServersState()
        return savedConfig
    }

    /**
     * Delete an Electrum server
     */
    fun deleteServer(serverId: String) {
        val wasActive = repository.getActiveServerId() == serverId
        repository.deleteElectrumServer(serverId)
        if (wasActive) {
            // Stop background sync and cancel any pending connection
            stopBackgroundSync()
            connectionJob?.cancel()
            connectionJob = null
            _uiState.value =
                _uiState.value.copy(
                    isConnecting = false,
                    isConnected = false,
                    serverVersion = null,
                    error = null,
                )
        }
        refreshServersState()
        _events.tryEmit(WalletEvent.ServerDeleted)
    }

    fun reorderServers(orderedIds: List<String>) {
        repository.reorderElectrumServerIds(orderedIds)
        refreshServersState()
    }

    /**
     * Connect to a saved server by ID
     * Automatically enables/disables Tor based on server type
     */
    fun connectToServer(serverId: String) {
        if (!isAppSessionUnlocked) return
        val previousJob = connectionJob
        stopBackgroundSync()
        stopHeartbeat()
        stopReconnectRetry()
        repository.setUserDisconnected(false)
        // Update active server immediately so the UI shows the new server while connecting
        _serversState.value = _serversState.value.copy(activeServerId = serverId)
        connectionJob =
            viewModelScope.launch {
                previousJob?.cancelAndJoin()
                _uiState.value = _uiState.value.copy(isConnecting = true, isConnected = false, error = null, serverVersion = null)

                try {
                    // Get the server config to check if it's an onion address
                    val servers = repository.getAllElectrumServers()
                    val serverConfig = servers.find { it.id == serverId }

                    // Auto-enable Tor for Tor-routed servers, auto-disable for clearnet
                    // (but keep Tor alive if fee/price source is .onion)
                    val serverRequiresTor = serverConfig?.let { it.useTor || it.isOnionAddress() } == true
                    val otherNeedsTor = isFeeSourceOnion() || isPriceSourceOnion()
                    val shouldKeepTorRunning = shouldKeepTorRunningForBitcoin(serverRequiresTor || otherNeedsTor)
                    disconnectForServerSwitch(shouldKeepTorRunning)
                    if (serverRequiresTor) {
                        repository.setTorEnabled(true)
                    } else if (repository.isTorEnabled()) {
                        // Switching to clearnet — Electrum won't use Tor proxy
                        repository.setTorEnabled(false)
                        // Only stop the Tor service if nothing else needs it
                        if (!shouldKeepTorRunningForBitcoin(otherNeedsTor)) {
                            torManager.stop()
                        }
                    }
                    if (serverRequiresTor && !torManager.isReady()) {
                        torManager.start()

                        if (!torManager.awaitReady(TOR_BOOTSTRAP_TIMEOUT_MS)) {
                            val msg = if (torState.value.status == TorStatus.ERROR) {
                                "Tor failed to start: ${torState.value.statusMessage}"
                            } else {
                                "Tor connection timed out"
                            }
                            _uiState.value = _uiState.value.copy(isConnecting = false, error = msg)
                            _events.emit(WalletEvent.Error(msg))
                            return@launch
                        }
                        // Brief settle time for the SOCKS proxy after bootstrap
                        delay(TOR_POST_BOOTSTRAP_DELAY_MS)
                    }

                    // Connect with timeout
                    val timeoutMs = if (serverRequiresTor) CONNECTION_TIMEOUT_TOR_MS else CONNECTION_TIMEOUT_CLEARNET_MS
                    val result =
                        withTimeoutOrNull(timeoutMs) {
                            repository.connectToServer(serverId)
                        }

                    when (result) {
                        null -> {
                            // Update UI immediately — don't wait for disconnect IO
                            _uiState.value =
                                _uiState.value.copy(
                                    isConnecting = false,
                                    isConnected = false,
                                    error = "Connection timed out",
                                )
                            _events.emit(WalletEvent.Error("Connection timed out"))
                            launch(Dispatchers.IO) { repository.disconnect() }
                            if (_autoSwitchServer.value) attemptAutoSwitch()
                        }
                        is WalletResult.Success -> {
                            // Query server software version (e.g. "Fulcrum 1.10.0")
                            val serverVersion =
                                withContext(Dispatchers.IO) {
                                    repository.getServerVersion()
                                }
                            _uiState.value =
                                _uiState.value.copy(
                                    isConnecting = false,
                                    isConnected = true,
                                    serverVersion = serverVersion,
                                )
                            refreshServersState()
                            _events.emit(WalletEvent.Connected)
                            // Refresh fee estimates (server may support sub-sat fees)
                            fetchFeeEstimates()
                            startHeartbeat()
                            launchSubscriptions()
                        }
                        is WalletResult.Error -> {
                            _uiState.value = _uiState.value.copy(isConnecting = false, isConnected = false)
                            val firstUse = result.exception?.findCause<CertificateFirstUseException>()
                            val mismatch = result.exception?.findCause<CertificateMismatchException>()
                            when {
                                firstUse != null -> {
                                    _certDialogState.value =
                                        CertDialogState(
                                            certInfo = firstUse.certInfo,
                                            isFirstUse = true,
                                            pendingServerId = serverId,
                                        )
                                }
                                mismatch != null -> {
                                    _certDialogState.value =
                                        CertDialogState(
                                            certInfo = mismatch.certInfo,
                                            isFirstUse = false,
                                            oldFingerprint = mismatch.storedFingerprint,
                                            pendingServerId = serverId,
                                        )
                                }
                                else -> {
                                    _uiState.value = _uiState.value.copy(error = result.message)
                                    _events.emit(WalletEvent.Error(result.message))
                                    if (_autoSwitchServer.value) attemptAutoSwitch()
                                }
                            }
                        }
                    }
                } finally {
                    connectionJob = null
                }
            }
    }

    /**
     * Get the key material (mnemonic and/or extended public key) for a wallet
     */
    fun getKeyMaterial(walletId: String): WalletRepository.WalletKeyMaterial? = repository.getKeyMaterial(walletId)

    fun getLiquidDescriptor(walletId: String): String? = repository.getLiquidDescriptor(walletId)

    fun signMessage(
        walletId: String,
        address: String,
        message: String,
    ): WalletResult<String> = repository.signMessage(walletId, address, message)

    fun verifyMessage(
        address: String,
        message: String,
        signatureBase64: String,
    ): WalletResult<Boolean> = repository.verifyMessage(address, message, signatureBase64)

    /**
     * Get the last full sync timestamp for a wallet
     */
    fun getLastFullSyncTime(walletId: String): Long? =
        _walletLastFullSyncTimes.value[walletId] ?: repository.getLastFullSyncTime(walletId)

    // ==================== Wallet Export/Import ====================

    // ==================== Full App Backup / Restore ====================

    fun getBackupWalletEntries(): List<github.aeonbtc.ibiswallet.ui.screens.BackupWalletEntry> {
        return repository.getAllWalletIds().mapNotNull { id ->
            val metadata = repository.getWalletMetadata(id) ?: return@mapNotNull null
            github.aeonbtc.ibiswallet.ui.screens.BackupWalletEntry(
                id = id,
                name = metadata.name,
                type =
                    if (metadata.policyType == WalletPolicyType.MULTISIG) {
                        "${metadata.multisigThreshold ?: 0}-of-${metadata.multisigTotalCosigners ?: 0} Multisig"
                    } else {
                        metadata.addressType.displayName
                    },
                isWatchOnly = metadata.isWatchOnly,
            )
        }
    }

    fun exportFullBackup(
        uri: Uri,
        walletIds: List<String>,
        labelWalletIds: List<String>,
        includeServers: Boolean,
        includeAppSettings: Boolean,
        password: String?,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true, error = null)

                val walletsArray = org.json.JSONArray()
                val labelWalletIdSet = labelWalletIds.toSet()
                for (walletId in walletIds) {
                    val metadata = repository.getWalletMetadata(walletId) ?: continue
                    val keyMaterial = repository.getKeyMaterial(walletId) ?: continue

                    val walletEntry = JSONObject().apply {
                        put("wallet", JSONObject().apply {
                            put("name", metadata.name)
                            put("addressType", metadata.addressType.name)
                            put("derivationPath", metadata.derivationPath)
                            put("network", metadata.network.name)
                            put("isWatchOnly", metadata.isWatchOnly)
                            put("isLocked", metadata.isLocked)
                            put("createdAt", metadata.createdAt)
                            put("seedFormat", metadata.seedFormat.name)
                            put("policyType", metadata.policyType.name)
                            if (metadata.gapLimit != StoredWallet.DEFAULT_GAP_LIMIT) {
                                put("gapLimit", metadata.gapLimit)
                            }
                            metadata.masterFingerprint?.let { put("masterFingerprint", it) }
                            metadata.multisigThreshold?.let { put("multisigThreshold", it) }
                            metadata.multisigTotalCosigners?.let { put("multisigTotalCosigners", it) }
                            metadata.multisigScriptType?.let { put("multisigScriptType", it.name) }
                            metadata.localCosignerFingerprint?.let { put("localCosignerFingerprint", it) }
                        })
                        put("keyMaterial", JSONObject().apply {
                            keyMaterial.mnemonic?.let { put("mnemonic", it) }
                            keyMaterial.passphrase?.let { put("passphrase", it) }
                            keyMaterial.extendedPublicKey?.let { put("extendedPublicKey", it) }
                            keyMaterial.watchAddress?.let { put("watchAddress", it) }
                            keyMaterial.privateKey?.let { put("privateKey", it) }
                            keyMaterial.multisigConfig?.let {
                                put("multisigConfig", secureStorage.multisigConfigToJson(it))
                            }
                            keyMaterial.localCosignerKeyMaterial?.let {
                                put("localCosignerKeyMaterial", it)
                            }
                        })

                        put("walletSettings", JSONObject().apply {
                            put("liquidEnabled", repository.isLiquidEnabledForWallet(walletId))
                            put("sparkEnabled", repository.isSparkEnabledForWallet(walletId))
                            put("layer2Provider", repository.getLayer2ProviderForWallet(walletId).name)
                            if (repository.isLiquidWatchOnly(walletId)) {
                                put("liquidWatchOnly", true)
                            }
                            val liquidDescriptor = repository.getLiquidDescriptor(walletId)
                            if (liquidDescriptor != null) {
                                put("liquidDescriptor", liquidDescriptor)
                            }
                            val liquidGap = repository.getLiquidGapLimit(walletId)
                            if (liquidGap != StoredWallet.DEFAULT_GAP_LIMIT) {
                                put("liquidGapLimit", liquidGap)
                            }
                            val frozen = repository.getFrozenUtxosForWallet(walletId)
                            if (frozen.isNotEmpty()) {
                                put("frozenUtxos", org.json.JSONArray(frozen.toList()))
                            }
                        })

                        if (walletId in labelWalletIdSet) {
                            val addrLabels = repository.getAllAddressLabelsForWallet(walletId)
                            val txLabels = repository.getAllTransactionLabelsForWallet(walletId)
                            val liquidMetadata = repository.getLiquidMetadataSnapshotForWallet(walletId)
                            val sparkAddrLabels = repository.getAllSparkAddressLabelsForWallet(walletId)
                            val sparkTxLabels = repository.getAllSparkTransactionLabelsForWallet(walletId)
                            val sparkMetadata =
                                SparkBackupMetadata(
                                    transactionSources = repository.getAllSparkTransactionSourcesForWallet(walletId),
                                    paymentRecipients = repository.getAllSparkPaymentRecipientsForWallet(walletId),
                                    depositAddresses = repository.getAllSparkDepositAddressesForWallet(walletId),
                                    pendingDeposits = repository.getAllSparkPendingDepositsForWallet(walletId),
                                    onchainDepositAddress = repository.getSparkOnchainDepositAddressForWallet(walletId),
                                )
                            put("labels", JSONObject().apply {
                                put("addresses", JSONObject().apply { addrLabels.forEach { (k, v) -> put(k, v) } })
                                put("transactions", JSONObject().apply { txLabels.forEach { (k, v) -> put(k, v) } })
                                put("liquidTransactions", JSONObject().apply {
                                    liquidMetadata.txLabels.forEach { (k, v) -> put(k, v) }
                                })
                                put("sparkAddresses", JSONObject().apply { sparkAddrLabels.forEach { (k, v) -> put(k, v) } })
                                put("sparkTransactions", JSONObject().apply { sparkTxLabels.forEach { (k, v) -> put(k, v) } })
                            })
                            if (!sparkMetadata.isEmpty()) {
                                put("sparkMetadata", BackupJsonAdapters.Spark.metadataToJson(sparkMetadata))
                            }

                            val bitcoinSwapDetails = repository.getAllTransactionSwapDetailsForWallet(walletId)
                            val bitcoinTransactionSources = repository.getAllTransactionSourcesForWallet(walletId)
                            if (liquidMetadata.txSources.isNotEmpty() || liquidMetadata.txSwapDetails.isNotEmpty()) {
                                put(
                                    "liquidMetadata",
                                    BackupJsonAdapters.Liquid.metadataToJson(
                                        transactionSources = liquidMetadata.txSources,
                                        swapDetails = liquidMetadata.txSwapDetails,
                                    ),
                                )
                            }
                            if (bitcoinTransactionSources.isNotEmpty() || bitcoinSwapDetails.isNotEmpty()) {
                                put(
                                    "bitcoinMetadata",
                                    BackupJsonAdapters.Bitcoin.metadataToJson(
                                        transactionSources = bitcoinTransactionSources,
                                        swapDetails = bitcoinSwapDetails,
                                    ),
                                )
                            }
                        }
                    }
                    walletsArray.put(walletEntry)
                }

                val payloadJson = JSONObject().apply {
                    put("ibisFullBackup", true)
                    put("version", 1)
                    put("encrypted", password != null)
                    put("exportedAt", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date()))
                    put("wallets", walletsArray)

                    if (includeServers) {
                        val servers = repository.getAllElectrumServers()
                        val activeId = repository.getActiveServerId()
                        put("electrumServers", org.json.JSONArray().apply {
                            servers.forEach { server ->
                                put(JSONObject().apply {
                                    put("name", server.name ?: "")
                                    put("url", server.url)
                                    put("port", server.port)
                                    put("useSsl", server.useSsl)
                                    put("useTor", server.useTor)
                                    if (server.id == activeId) put("isActive", true)
                                })
                            }
                        })

                        val liquidServers = repository.getAllLiquidServers()
                        val activeLiquidId = repository.getActiveLiquidServerId()
                        put("liquidServers", org.json.JSONArray().apply {
                            liquidServers.forEach { server ->
                                put(JSONObject().apply {
                                    put("name", server.name)
                                    put("url", server.url)
                                    put("port", server.port)
                                    put("useSsl", server.useSsl)
                                    put("useTor", server.useTor)
                                    if (server.id == activeLiquidId) put("isActive", true)
                                })
                            }
                        })
                    }

                    if (includeAppSettings) {
                        put("appSettings", JSONObject().apply {
                            // Intentionally exclude Security screen settings from full app backups.
                            put("layer1Denomination", repository.getLayer1Denomination())
                            put("layer2Denomination", repository.getLayer2Denomination())
                            put("appLocale", repository.getAppLocale().storageValue)
                            put("swipeMode", repository.getSwipeMode())
                            put("autoSwitchServer", repository.isAutoSwitchServerEnabled())
                            put("torEnabled", repository.isTorEnabled())
                            put("spendUnconfirmed", repository.getSpendUnconfirmed())
                            put("psbtQrDensity", repository.getPsbtQrDensity().name)
                            put("psbtQrBrightness", repository.getPsbtQrBrightness().toDouble())
                            put("nfcEnabled", repository.isNfcEnabled())
                            put("privacyMode", repository.getPrivacyMode())
                            put("privacyModeHintSeen", repository.hasSeenPrivacyModeHint())
                            put("walletNotificationsEnabled", repository.isWalletNotificationsEnabled())
                            put("foregroundConnectivityEnabled", repository.isForegroundConnectivityEnabled())
                            put("appUpdateCheckEnabled", repository.isAppUpdateCheckEnabled())
                            repository.getSeenAppUpdateVersion()?.let { put("seenAppUpdateVersion", it) }
                            put("mempoolServer", repository.getMempoolServer())
                            repository.getCustomMempoolUrl()?.let { put("mempoolCustomUrl", it) }
                            put("feeSource", repository.getFeeSource())
                            repository.getCustomFeeSourceUrl()?.let { put("feeSourceCustomUrl", it) }
                            put("priceSource", repository.getPriceSource())
                            put("priceCurrency", repository.getPriceCurrency())
                            put("historicalTxFiatEnabled", repository.isHistoricalTxFiatEnabled())
                            put("layer2Enabled", repository.isLayer2Enabled())
                            put("sparkLayer2Enabled", repository.isSparkLayer2Enabled())
                            put("liquidTorEnabled", repository.isLiquidTorEnabled())
                            put("boltzApiSource", repository.getBoltzApiSource())
                            put("sideSwapApiSource", repository.getSideSwapApiSource())
                            put("preferredSwapService", repository.getPreferredSwapService().name)
                            put("liquidAutoSwitch", repository.isLiquidAutoSwitchEnabled())
                            put("liquidServerSelectedByUser", repository.hasUserSelectedLiquidServer())
                            put("liquidExplorer", repository.getLiquidExplorer())
                            repository.getCustomLiquidExplorerUrl()?.let { put("liquidExplorerCustomUrl", it) }
                        })
                    }
                }

                val outputJson = if (password != null) {
                    val plaintext = payloadJson.toString().toByteArray(Charsets.UTF_8)
                    val encrypted = encryptData(plaintext, password)
                    JSONObject().apply {
                        put("ibisFullBackup", true)
                        put("version", 1)
                        put("encrypted", true)
                        put("salt", Base64.encodeToString(encrypted.salt, Base64.NO_WRAP))
                        put("iv", Base64.encodeToString(encrypted.iv, Base64.NO_WRAP))
                        put("data", Base64.encodeToString(encrypted.ciphertext, Base64.NO_WRAP))
                    }
                } else {
                    payloadJson
                }

                withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openOutputStream(uri)?.use { stream ->
                        stream.write(outputJson.toString(2).toByteArray(Charsets.UTF_8))
                    } ?: throw IllegalStateException("Could not open output stream")
                }

                _uiState.value = _uiState.value.copy(isLoading = false)
                _fullBackupResultMessage.value = "Successfully exported ${walletIds.size} wallet(s)"
                _events.emit(WalletEvent.WalletExported("Full backup (${walletIds.size} wallets)"))
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = "Export failed")
                _fullBackupResultMessage.value = "Export failed"
                _events.emit(WalletEvent.Error("Full backup export failed"))
            }
        }
    }

    suspend fun parseFullBackup(
        uri: Uri,
        password: String?,
    ): JSONObject {
        return withContext(Dispatchers.IO) {
            val rawBytes = getApplication<Application>().contentResolver.openInputStream(uri)?.use {
                it.readBytesWithLimit(InputLimits.BACKUP_FILE_BYTES)
            } ?: throw IllegalStateException("Could not read file")

            val fileJson = JSONObject(String(rawBytes, Charsets.UTF_8))

            if (!fileJson.optBoolean("ibisFullBackup", false)) {
                throw IllegalStateException("Not a full Ibis backup file. Use Manage Wallets to restore single-wallet backups.")
            }

            if (fileJson.optBoolean("encrypted", false)) {
                if (password.isNullOrEmpty()) {
                    throw IllegalStateException("This backup is encrypted. Please enter the password.")
                }
                val salt = Base64.decode(fileJson.getString("salt"), Base64.NO_WRAP)
                val iv = Base64.decode(fileJson.getString("iv"), Base64.NO_WRAP)
                val ciphertext = Base64.decode(fileJson.getString("data"), Base64.NO_WRAP)
                val plaintext = decryptData(EncryptedPayload(salt, iv, ciphertext), password)
                JSONObject(String(plaintext, Charsets.UTF_8))
            } else {
                fileJson
            }
        }
    }

    suspend fun importFullBackup(
        backupJson: JSONObject,
        walletIds: List<String>,
        labelWalletIds: List<String>,
        importServers: Boolean,
        importAppSettings: Boolean,
    ): Boolean {
        return try {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            var walletsImported = 0
            var walletsSkipped = 0
            val selectedWalletIdSet = walletIds.toSet()
            val labelWalletIdSet = labelWalletIds.toSet()

            if (selectedWalletIdSet.isNotEmpty() || labelWalletIdSet.isNotEmpty()) {
                val walletsArray = backupJson.optJSONArray("wallets") ?: org.json.JSONArray()
                val existingWalletIdsByIdentity =
                    repository.getAllWalletIds().mapNotNull { id ->
                        repository.getKeyMaterial(id)?.let { material ->
                            backupIdentityForKeyMaterial(material)?.let { identity -> identity to id }
                        }
                    }.toMap().toMutableMap()

                for (i in 0 until walletsArray.length()) {
                    val walletSelectionId = i.toString()
                    val shouldImportWallet = walletSelectionId in selectedWalletIdSet
                    val shouldImportLabels = walletSelectionId in labelWalletIdSet
                    if (!shouldImportWallet && !shouldImportLabels) continue

                    val entry = walletsArray.getJSONObject(i)
                    val walletObj = entry.getJSONObject("wallet")
                    val keyMaterialObj = entry.getJSONObject("keyMaterial")
                    val parsed =
                        try {
                            BitcoinUtils.parseBackupJson(walletObj, keyMaterialObj)
                        } catch (e: Exception) {
                            if (BuildConfig.DEBUG) {
                                android.util.Log.w("WalletViewModel", "Skip wallet import: ${e.message}")
                            }
                            if (shouldImportWallet) {
                                walletsSkipped++
                            }
                            continue
                        }
                    val backupIdentity = backupIdentityForBackupJson(keyMaterialObj)

                    val existingId = backupIdentity?.let { existingWalletIdsByIdentity[it] }
                    if (existingId != null) {
                        if (shouldImportWallet) {
                            walletsSkipped++
                        }
                        if (shouldImportWallet) {
                            restoreWalletSettings(existingId, entry)
                        }
                        if (shouldImportLabels) {
                            restoreLabelsForWallet(existingId, entry)
                        }
                        continue
                    }

                    if (shouldImportWallet) {
                        try {
                            val network = BitcoinUtils.parseSupportedWalletNetwork(parsed.network)
                            val seedFormat =
                                parsed.seedFormat
                                    .takeIf { it.isNotBlank() }
                                    ?.let { SeedFormat.valueOf(it) }
                                    ?: SeedFormat.BIP39

                            val gapLimit = walletObj.optInt("gapLimit", StoredWallet.DEFAULT_GAP_LIMIT)
                            val fingerprint = walletObj.optString("masterFingerprint", "").ifBlank { null }
                            val multisigConfig =
                                keyMaterialObj.optJSONObject("multisigConfig")?.let {
                                    secureStorage.multisigConfigFromJson(it)
                                }

                            val config = WalletImportConfig(
                                name = parsed.name,
                                keyMaterial = parsed.keyMaterial,
                                addressType = parsed.addressType,
                                passphrase = parsed.passphrase,
                                customDerivationPath = parsed.customDerivationPath,
                                network = network,
                                isWatchOnly = parsed.isWatchOnly,
                                seedFormat = seedFormat,
                                gapLimit = gapLimit,
                                masterFingerprint = fingerprint,
                                multisigConfig = multisigConfig,
                                localCosignerKeyMaterial =
                                    keyMaterialObj.optString("localCosignerKeyMaterial", "").ifBlank { null },
                            )
                            repository.importWallet(config)
                            walletsImported++

                            val newWalletId = repository.getActiveWalletId()
                            if (newWalletId != null) {
                                backupIdentity?.let { existingWalletIdsByIdentity[it] = newWalletId }
                                restoreWalletSettings(newWalletId, entry)
                                if (shouldImportLabels) {
                                    restoreLabelsForWallet(newWalletId, entry)
                                }
                            }
                        } catch (e: Exception) {
                            if (BuildConfig.DEBUG) {
                                android.util.Log.w("WalletViewModel", "Skip wallet import: ${e.message}")
                            }
                            walletsSkipped++
                        }
                    }
                }
            }

            if (importServers) {
                val serverSettingsObj = backupJson.optJSONObject("serverSettings")
                    ?: JSONObject().apply {
                        backupJson.optJSONArray("electrumServers")?.let { put("electrumServers", it) }
                    }
                if (serverSettingsObj.length() > 0) {
                    restoreServerSettings(serverSettingsObj)
                }
                restoreLiquidServers(backupJson.optJSONArray("liquidServers"))
            }

            if (importAppSettings) {
                val settingsObj = backupJson.optJSONObject("appSettings")
                if (settingsObj != null) {
                    restoreAppSettings(settingsObj)
                }
            }

            _uiState.value = _uiState.value.copy(isLoading = false)

            val msg = buildString {
                append("Restored successfully.")
                if (walletsImported > 0) append(" $walletsImported wallet(s) imported.")
                if (walletsSkipped > 0) append(" $walletsSkipped skipped (already exist).")
            }
            _fullBackupResultMessage.value = msg
            _events.emit(WalletEvent.WalletImported)
            true
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            _uiState.value = _uiState.value.copy(isLoading = false, error = "Restore failed")
            _fullBackupResultMessage.value = "Restore failed"
            _events.emit(WalletEvent.Error("Restore failed"))
            false
        }
    }

    private fun backupIdentityForKeyMaterial(material: WalletRepository.WalletKeyMaterial): String? {
        val typeAndValue =
            when {
                material.mnemonic != null -> "mnemonic:${material.mnemonic.trim()}"
                material.privateKey != null -> "privateKey:${material.privateKey.trim()}"
                material.watchAddress != null -> "watchAddress:${material.watchAddress.trim()}"
                material.extendedPublicKey != null -> "extendedPublicKey:${material.extendedPublicKey.trim()}"
                else -> null
            } ?: return null

        return backupIdentity(
            typeAndValue = typeAndValue,
            passphrase = material.passphrase,
            localCosignerKeyMaterial = material.localCosignerKeyMaterial,
        )
    }

    private fun backupIdentityForBackupJson(keyMaterialObj: JSONObject): String? {
        val mnemonic = keyMaterialObj.optBackupString("mnemonic")
        val privateKey = keyMaterialObj.optBackupString("privateKey")
        val watchAddress = keyMaterialObj.optBackupString("watchAddress")
        val extendedPublicKey = keyMaterialObj.optBackupString("extendedPublicKey")
        val typeAndValue =
            when {
                mnemonic != null -> "mnemonic:${mnemonic.trim()}"
                privateKey != null -> "privateKey:${privateKey.trim()}"
                watchAddress != null -> "watchAddress:${watchAddress.trim()}"
                extendedPublicKey != null -> "extendedPublicKey:${extendedPublicKey.trim()}"
                else -> null
            } ?: return null

        return backupIdentity(
            typeAndValue = typeAndValue,
            passphrase = keyMaterialObj.optBackupString("passphrase"),
            localCosignerKeyMaterial = keyMaterialObj.optBackupString("localCosignerKeyMaterial"),
        )
    }

    private fun backupIdentity(
        typeAndValue: String,
        passphrase: String?,
        localCosignerKeyMaterial: String?,
    ): String =
        listOf(
            typeAndValue,
            "passphrase:${passphrase ?: ""}",
            "localCosigner:${localCosignerKeyMaterial?.trim() ?: ""}",
        ).joinToString("|")

    private fun JSONObject.optBackupString(name: String): String? =
        optString(name, null.toString()).let { value ->
            if (value == "null" || value.isBlank()) null else value
        }

    private fun restoreLabelsForWallet(walletId: String, walletEntry: JSONObject) {
        val labelsObj = walletEntry.optJSONObject("labels")
        val addrLabels = labelsObj?.optJSONObject("addresses")
        val txLabels = labelsObj?.optJSONObject("transactions")
        val liquidTxLabels = labelsObj?.optJSONObject("liquidTransactions")
        val sparkAddrLabels = labelsObj?.optJSONObject("sparkAddresses")
        val sparkTxLabels = labelsObj?.optJSONObject("sparkTransactions")

        val restoredAddressLabels = mutableMapOf<String, String>()
        addrLabels?.keys()?.forEach { addr ->
            val label = addrLabels.optString(addr, "")
            if (label.isNotBlank()) {
                restoredAddressLabels[addr] = label
            }
        }
        repository.saveAddressLabelsForWallet(walletId, restoredAddressLabels)

        val restoredTransactionLabels = mutableMapOf<String, String>()
        txLabels?.keys()?.forEach { txid ->
            val label = txLabels.optString(txid, "")
            if (label.isNotBlank()) {
                restoredTransactionLabels[txid] = label
            }
        }
        repository.saveTransactionLabelsForWallet(walletId, restoredTransactionLabels)

        val restoredLiquidTransactionLabels = mutableMapOf<String, String>()
        liquidTxLabels?.keys()?.forEach { txid ->
            val label = liquidTxLabels.optString(txid, "")
            if (label.isNotBlank()) {
                restoredLiquidTransactionLabels[txid] = label
            }
        }
        repository.saveLiquidTransactionLabelsForWallet(walletId, restoredLiquidTransactionLabels)

        val restoredSparkAddressLabels = mutableMapOf<String, String>()
        sparkAddrLabels?.keys()?.forEach { address ->
            val label = sparkAddrLabels.optString(address, "")
            if (label.isNotBlank()) {
                restoredSparkAddressLabels[address] = label
            }
        }
        repository.saveSparkAddressLabelsForWallet(walletId, restoredSparkAddressLabels)

        val restoredSparkTransactionLabels = mutableMapOf<String, String>()
        sparkTxLabels?.keys()?.forEach { paymentId ->
            val label = sparkTxLabels.optString(paymentId, "")
            if (label.isNotBlank()) {
                restoredSparkTransactionLabels[paymentId] = label
            }
        }
        repository.saveSparkTransactionLabelsForWallet(walletId, restoredSparkTransactionLabels)
        restoreSparkMetadataForWallet(walletId, walletEntry.optJSONObject("sparkMetadata"))

        val metadataObj = walletEntry.optJSONObject("liquidMetadata")
        BackupJsonAdapters.Liquid.transactionSourcesFromMetadata(metadataObj).forEach { (txid, source) ->
            repository.saveLiquidTransactionSourceForWallet(walletId, txid, source)
        }
        BackupJsonAdapters.Liquid.swapDetailsFromMetadata(metadataObj).forEach { (txid, details) ->
            repository.saveLiquidSwapDetailsForWallet(walletId, txid, details)
        }
        BackupJsonAdapters.Bitcoin.swapDetailsFromMetadata(walletEntry.optJSONObject("bitcoinMetadata"))
            .forEach { (txid, details) ->
                repository.saveTransactionSwapDetailsForWallet(walletId, txid, details)
            }
        BackupJsonAdapters.Bitcoin.transactionSourcesFromMetadata(walletEntry.optJSONObject("bitcoinMetadata"))
            .forEach { (txid, source) ->
                repository.saveTransactionSourceForWallet(walletId, txid, source)
            }
    }

    private fun restoreLiquidServers(serversArray: org.json.JSONArray?) {
        if (serversArray == null || serversArray.length() == 0) return
        val existing = repository.getAllLiquidServers()
        val existingKeys = existing.map { "${it.cleanUrl()}:${it.port}" }.toSet()
        val activeLiquidId = repository.getActiveLiquidServerId()

        for (i in 0 until serversArray.length()) {
            val obj = serversArray.getJSONObject(i)
            val url = obj.getString("url")
            val port = obj.optInt("port", 995)
            val useSsl = obj.optBoolean("useSsl", true)
            val useTor = obj.optBoolean("useTor", false)

            // Reject malformed hosts/ports — a backup is not a trusted source.
            if (ServerUrlValidator.validateHostAndPort(url, port) != null) {
                SecureLog.w(
                    TAG,
                    "Skipping Liquid server from backup with invalid host/port",
                    releaseMessage = "Backup contained an invalid Liquid server",
                )
                continue
            }
            // Fail closed on plaintext + clearnet servers from backup. A user can
            // re-add a LAN-only plaintext server through the UI if they really
            // need it; we will not silently take that risk on restore.
            if (!useSsl && !useTor) {
                SecureLog.w(
                    TAG,
                    "Skipping Liquid server from backup that uses neither SSL nor Tor",
                    releaseMessage = "Backup contained an untrusted Liquid server",
                )
                continue
            }
            val key = "${github.aeonbtc.ibiswallet.data.model.LiquidElectrumConfig(url = url, port = port).cleanUrl()}:$port"

            if (key in existingKeys) continue

            val config = github.aeonbtc.ibiswallet.data.model.LiquidElectrumConfig(
                name = obj.optString("name", ""),
                url = url,
                port = port,
                useSsl = useSsl,
                useTor = useTor,
            )
            repository.saveLiquidServer(config)
            if (obj.optBoolean("isActive", false) && activeLiquidId == null) {
                repository.setActiveLiquidServerId(config.id)
            }
        }
    }

    private fun restoreAppSettings(settings: JSONObject) {
        // Security screen settings are intentionally not restored from full app backups.
        settings.optString("layer1Denomination", "").takeIf { it.isNotBlank() }
            ?.let { repository.setLayer1Denomination(it) }
        settings.optString("layer2Denomination", "").takeIf { it.isNotBlank() }
            ?.let { repository.setLayer2Denomination(it) }
        settings.optString("appLocale", "").takeIf { it.isNotBlank() }
            ?.let { repository.setAppLocale(AppLocale.fromStorageValue(it)) }
        settings.optString("swipeMode", "").takeIf { it.isNotBlank() }
            ?.let { repository.setSwipeMode(it) }
        if (settings.has("autoSwitchServer")) {
            repository.setAutoSwitchServerEnabled(settings.getBoolean("autoSwitchServer"))
        }
        if (settings.has("torEnabled")) repository.setTorEnabled(settings.getBoolean("torEnabled"))
        if (settings.has("spendUnconfirmed")) repository.setSpendUnconfirmed(settings.getBoolean("spendUnconfirmed"))
        settings.optString("psbtQrDensity", "").takeIf { it.isNotBlank() }?.let { name ->
            try { repository.setPsbtQrDensity(SecureStorage.QrDensity.valueOf(name)) } catch (_: Exception) {}
        }
        if (settings.has("psbtQrBrightness")) {
            repository.setPsbtQrBrightness(settings.getDouble("psbtQrBrightness").toFloat())
        }
        if (settings.has("nfcEnabled")) repository.setNfcEnabled(settings.getBoolean("nfcEnabled"))
        if (settings.has("privacyMode")) repository.setPrivacyMode(settings.getBoolean("privacyMode"))
        if (settings.has("privacyModeHintSeen")) {
            repository.setHasSeenPrivacyModeHint(settings.getBoolean("privacyModeHintSeen"))
        }
        if (settings.has("walletNotificationsEnabled")) repository.setWalletNotificationsEnabled(settings.getBoolean("walletNotificationsEnabled"))
        if (settings.has("foregroundConnectivityEnabled")) {
            repository.setForegroundConnectivityEnabled(settings.getBoolean("foregroundConnectivityEnabled"))
        }
        if (settings.has("appUpdateCheckEnabled")) {
            repository.setAppUpdateCheckEnabled(settings.getBoolean("appUpdateCheckEnabled"))
        }
        if (settings.has("seenAppUpdateVersion")) {
            repository.setSeenAppUpdateVersion(
                settings.optString("seenAppUpdateVersion", "").ifBlank { null },
            )
        }
        settings.optString("mempoolServer", "").takeIf { it.isNotBlank() }
            ?.let { repository.setMempoolServer(it) }
        settings.optString("mempoolCustomUrl", "").takeIf { it.isNotBlank() }
            ?.let { value ->
                if (ServerUrlValidator.validate(value) == null) {
                    repository.setCustomMempoolUrl(value)
                } else {
                    SecureLog.w(
                        TAG,
                        "Skipping mempoolCustomUrl from backup app settings: failed validation",
                        releaseMessage = "Backup contained an invalid mempool URL",
                    )
                }
            }
        settings.optString("feeSource", "").takeIf { it.isNotBlank() }
            ?.let { repository.setFeeSource(it) }
        settings.optString("feeSourceCustomUrl", "").takeIf { it.isNotBlank() }
            ?.let { value ->
                if (ServerUrlValidator.validate(value) == null) {
                    repository.setCustomFeeSourceUrl(value)
                } else {
                    SecureLog.w(
                        TAG,
                        "Skipping feeSourceCustomUrl from backup app settings: failed validation",
                        releaseMessage = "Backup contained an invalid fee source URL",
                    )
                }
            }
        settings.optString("priceSource", "").takeIf { it.isNotBlank() }
            ?.let { repository.setPriceSource(it) }
        settings.optString("priceCurrency", "").takeIf { it.isNotBlank() }
            ?.let { repository.setPriceCurrency(it) }
        if (settings.has("historicalTxFiatEnabled")) {
            repository.setHistoricalTxFiatEnabled(settings.getBoolean("historicalTxFiatEnabled"))
        }
        if (settings.has("layer2Enabled")) repository.setLayer2Enabled(settings.getBoolean("layer2Enabled"))
        if (settings.has("sparkLayer2Enabled")) {
            repository.setSparkLayer2Enabled(settings.getBoolean("sparkLayer2Enabled"))
        }
        if (settings.has("liquidTorEnabled")) {
            repository.setLiquidTorEnabled(settings.getBoolean("liquidTorEnabled"))
        }
        settings.optString("boltzApiSource", "").takeIf { it.isNotBlank() }
            ?.let { repository.setBoltzApiSource(it) }
        settings.optString("sideSwapApiSource", "").takeIf { it.isNotBlank() }
            ?.let { repository.setSideSwapApiSource(it) }
        settings.optString("preferredSwapService", "").takeIf { it.isNotBlank() }?.let { name ->
            try { repository.setPreferredSwapService(SwapService.valueOf(name)) } catch (_: Exception) {}
        }
        if (settings.has("liquidAutoSwitch")) {
            repository.setLiquidAutoSwitchEnabled(settings.getBoolean("liquidAutoSwitch"))
        }
        if (settings.has("liquidServerSelectedByUser")) {
            repository.setUserSelectedLiquidServer(settings.getBoolean("liquidServerSelectedByUser"))
        }
        settings.optString("liquidExplorer", "").takeIf { it.isNotBlank() }
            ?.let { repository.setLiquidExplorer(it) }
        settings.optString("liquidExplorerCustomUrl", "").takeIf { it.isNotBlank() }
            ?.let { repository.setCustomLiquidExplorerUrl(it) }
    }

    private fun restoreWalletSettings(walletId: String, walletEntry: JSONObject) {
        val settingsObj = walletEntry.optJSONObject("walletSettings") ?: return
        restoreWalletSettingsObject(walletId, settingsObj)

        val walletObj = walletEntry.optJSONObject("wallet")
        if (walletObj != null && walletObj.optBoolean("isLocked", false)) {
            repository.setWalletLocked(walletId, true)
        }
    }

    private fun restoreWalletSettingsObject(walletId: String, settingsObj: JSONObject?) {
        settingsObj ?: return
        if (settingsObj.has("liquidEnabled")) {
            repository.setLiquidEnabledForWallet(walletId, settingsObj.getBoolean("liquidEnabled"))
        }
        if (settingsObj.has("sparkEnabled")) {
            repository.setSparkEnabledForWallet(walletId, settingsObj.getBoolean("sparkEnabled"))
        }
        settingsObj.optString("layer2Provider", "").takeIf { it.isNotBlank() }?.let { providerName ->
            runCatching {
                repository.setLayer2ProviderForWallet(
                    walletId,
                    github.aeonbtc.ibiswallet.data.model.Layer2Provider.valueOf(providerName),
                )
            }
        }
        if (settingsObj.has("liquidWatchOnly")) {
            repository.setLiquidWatchOnly(walletId, settingsObj.getBoolean("liquidWatchOnly"))
        }
        if (settingsObj.has("liquidDescriptor")) {
            repository.setLiquidDescriptor(walletId, settingsObj.getString("liquidDescriptor"))
        }
        if (settingsObj.has("liquidGapLimit")) {
            repository.setLiquidGapLimit(walletId, settingsObj.getInt("liquidGapLimit"))
        }
        val frozenArr = settingsObj.optJSONArray("frozenUtxos")
        if (frozenArr != null && frozenArr.length() > 0) {
            val outpoints = (0 until frozenArr.length()).map { frozenArr.getString(it) }.toSet()
            repository.setFrozenUtxosForWallet(walletId, outpoints)
        }
    }

    /**
     * Parse a backup file and return the parsed data for preview/import
     * Returns a Pair of (JSONObject payload, Boolean wasEncrypted)
     */
    suspend fun parseBackupFile(
        uri: Uri,
        password: String?,
    ): JSONObject {
        return withContext(Dispatchers.IO) {
            val rawBytes =
                getApplication<Application>().contentResolver.openInputStream(uri)?.use {
                    it.readBytesWithLimit(InputLimits.BACKUP_FILE_BYTES)
                } ?: throw IllegalStateException("Could not read file")

            val fileJson = JSONObject(String(rawBytes, Charsets.UTF_8))

            if (fileJson.optBoolean("encrypted", false)) {
                if (password.isNullOrEmpty()) {
                    throw IllegalStateException("This backup is encrypted. Please enter the password.")
                }
                val salt = Base64.decode(fileJson.getString("salt"), Base64.NO_WRAP)
                val iv = Base64.decode(fileJson.getString("iv"), Base64.NO_WRAP)
                val ciphertext = Base64.decode(fileJson.getString("data"), Base64.NO_WRAP)

                val plaintext = decryptData(EncryptedPayload(salt, iv, ciphertext), password)
                JSONObject(String(plaintext, Charsets.UTF_8))
            } else {
                fileJson
            }
        }
    }

    /**
     * Import a wallet from a parsed backup JSON object
     */
    fun importFromBackup(
        backupJson: JSONObject,
        importServerSettings: Boolean = true,
    ) {
        val walletObj = backupJson.getJSONObject("wallet")
        val keyMaterialObj = backupJson.getJSONObject("keyMaterial")

        // Delegate JSON field extraction to testable pure function
        val parsed =
            try {
                BitcoinUtils.parseBackupJson(walletObj, keyMaterialObj)
            } catch (_: Exception) {
                val message = "Invalid backup file"
                viewModelScope.launch {
                    _uiState.value = _uiState.value.copy(error = message)
                    _events.emit(WalletEvent.Error(message))
                }
                return
            }

        val network =
            try {
                BitcoinUtils.parseSupportedWalletNetwork(parsed.network)
            } catch (_: Exception) {
                viewModelScope.launch {
                    val message = "Invalid backup file"
                    _uiState.value = _uiState.value.copy(error = message)
                    _events.emit(WalletEvent.Error(message))
                }
                return
            }

        val seedFormat =
            try {
                parsed.seedFormat
                    .takeIf { it.isNotBlank() }
                    ?.let { SeedFormat.valueOf(it) }
                    ?: SeedFormat.BIP39
            } catch (_: Exception) {
                viewModelScope.launch {
                    val message = "Invalid backup file"
                    _uiState.value = _uiState.value.copy(error = message)
                    _events.emit(WalletEvent.Error(message))
                }
                return
            }
        val multisigConfig =
            keyMaterialObj.optJSONObject("multisigConfig")?.let {
                secureStorage.multisigConfigFromJson(it)
            }

        val config =
            WalletImportConfig(
                name = parsed.name,
                keyMaterial = parsed.keyMaterial,
                addressType = parsed.addressType,
                passphrase = parsed.passphrase,
                customDerivationPath = parsed.customDerivationPath,
                network = network,
                isWatchOnly = parsed.isWatchOnly,
                seedFormat = seedFormat,
                multisigConfig = multisigConfig,
                localCosignerKeyMaterial =
                    keyMaterialObj.optString("localCosignerKeyMaterial", "").ifBlank { null },
            )

        // Import the wallet, then restore metadata once import completes
        val labelsObj = backupJson.optJSONObject("labels")
        val bitcoinMetadataObj = backupJson.optJSONObject("bitcoinMetadata")
        val liquidMetadataObj = backupJson.optJSONObject("liquidMetadata")
        val sparkMetadataObj = backupJson.optJSONObject("sparkMetadata")
        val walletSettingsObj = backupJson.optJSONObject("walletSettings")
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            // Restore server settings before import so the imported wallet can use them immediately.
            if (importServerSettings) {
                val serverSettingsObj = backupJson.optJSONObject("serverSettings")
                if (serverSettingsObj != null) {
                    restoreServerSettings(serverSettingsObj)
                } else {
                    restoreLiquidServers(backupJson.optJSONArray("liquidServers"))
                }
            }

            when (val result = repository.importWallet(config)) {
                is WalletResult.Success -> {
                    val restoredWalletId = repository.getActiveWalletId() ?: walletState.value.activeWallet?.id

                    if (restoredWalletId != null) {
                        restoreWalletSettingsObject(restoredWalletId, walletSettingsObj)
                    }

                    if (restoredWalletId != null &&
                        (labelsObj != null || bitcoinMetadataObj != null || liquidMetadataObj != null || sparkMetadataObj != null)
                    ) {
                        restoreBackupMetadata(
                            walletId = restoredWalletId,
                            labelsObj = labelsObj,
                            bitcoinMetadataObj = bitcoinMetadataObj,
                            liquidMetadataObj = liquidMetadataObj,
                            sparkMetadataObj = sparkMetadataObj,
                        )
                    }

                    _uiState.value = _uiState.value.copy(isLoading = false)
                    _events.emit(WalletEvent.WalletImported)

                    if (labelsObj != null || bitcoinMetadataObj != null || liquidMetadataObj != null || sparkMetadataObj != null) {
                        _events.emit(WalletEvent.LabelsRestored)
                    }

                    if (_uiState.value.isConnected) {
                        sync()
                    }
                }
                is WalletResult.Error -> {
                    _uiState.value =
                        _uiState.value.copy(
                            isLoading = false,
                            error = result.message,
                        )
                    _events.emit(WalletEvent.Error(result.message))
                }
            }
        }
    }

    private fun restoreBackupMetadata(
        walletId: String,
        labelsObj: JSONObject?,
        bitcoinMetadataObj: JSONObject?,
        liquidMetadataObj: JSONObject?,
        sparkMetadataObj: JSONObject?,
    ) {
        val addressLabels = labelsObj?.optJSONObject("addresses")
        if (addressLabels != null) {
            val keys = addressLabels.keys()
            while (keys.hasNext()) {
                val addr = keys.next()
                val label = addressLabels.getString(addr)
                repository.saveAddressLabelForWallet(walletId, addr, label)
            }
        }

        val txLabels = labelsObj?.optJSONObject("transactions")
        if (txLabels != null) {
            val keys = txLabels.keys()
            while (keys.hasNext()) {
                val txid = keys.next()
                val label = txLabels.getString(txid)
                repository.saveTransactionLabelForWallet(walletId, txid, label)
            }
        }

        val liquidTxLabels = labelsObj?.optJSONObject("liquidTransactions")
        if (liquidTxLabels != null) {
            val keys = liquidTxLabels.keys()
            while (keys.hasNext()) {
                val txid = keys.next()
                val label = liquidTxLabels.getString(txid)
                repository.saveLiquidTransactionLabelForWallet(walletId, txid, label)
            }
        }

        val sparkAddressLabels = labelsObj?.optJSONObject("sparkAddresses")
        if (sparkAddressLabels != null) {
            val labels = mutableMapOf<String, String>()
            val keys = sparkAddressLabels.keys()
            while (keys.hasNext()) {
                val address = keys.next()
                val label = sparkAddressLabels.getString(address)
                if (label.isNotBlank()) {
                    labels[address] = label
                }
            }
            repository.saveSparkAddressLabelsForWallet(walletId, labels)
        }

        val sparkTxLabels = labelsObj?.optJSONObject("sparkTransactions")
        if (sparkTxLabels != null) {
            val labels = mutableMapOf<String, String>()
            val keys = sparkTxLabels.keys()
            while (keys.hasNext()) {
                val paymentId = keys.next()
                val label = sparkTxLabels.getString(paymentId)
                if (label.isNotBlank()) {
                    labels[paymentId] = label
                }
            }
            repository.saveSparkTransactionLabelsForWallet(walletId, labels)
        }
        restoreSparkMetadataForWallet(walletId, sparkMetadataObj)

        BackupJsonAdapters.Bitcoin.swapDetailsFromMetadata(bitcoinMetadataObj).forEach { (txid, details) ->
            repository.saveTransactionSwapDetailsForWallet(walletId, txid, details)
        }
        BackupJsonAdapters.Bitcoin.transactionSourcesFromMetadata(bitcoinMetadataObj).forEach { (txid, source) ->
            repository.saveTransactionSourceForWallet(walletId, txid, source)
        }
        BackupJsonAdapters.Liquid.transactionSourcesFromMetadata(liquidMetadataObj).forEach { (txid, source) ->
            repository.saveLiquidTransactionSourceForWallet(walletId, txid, source)
        }
        BackupJsonAdapters.Liquid.swapDetailsFromMetadata(liquidMetadataObj).forEach { (txid, details) ->
            repository.saveLiquidSwapDetailsForWallet(walletId, txid, details)
        }
    }

    private fun restoreSparkMetadataForWallet(
        walletId: String,
        sparkMetadataObj: JSONObject?,
    ) {
        val metadata = BackupJsonAdapters.Spark.metadataFromJson(sparkMetadataObj) ?: return
        metadata.transactionSources.forEach { (paymentId, source) ->
            repository.saveSparkTransactionSourceForWallet(walletId, paymentId, source)
        }
        metadata.paymentRecipients.forEach { (paymentId, recipient) ->
            repository.saveSparkPaymentRecipientForWallet(walletId, paymentId, recipient)
        }
        metadata.depositAddresses.forEach { (txid, address) ->
            repository.saveSparkDepositAddressForWallet(walletId, txid, address)
        }
        metadata.pendingDeposits.forEach { deposit ->
            repository.saveSparkPendingDepositForWallet(walletId, deposit)
        }
        metadata.onchainDepositAddress?.let { address ->
            repository.setSparkOnchainDepositAddressForWallet(walletId, address)
        }
    }

    /**
     * Restore server settings from a backup JSON object.
     * Merges Bitcoin and Liquid Electrum servers (skipping duplicates by url+port),
     * then restores explorer and related connectivity settings.
     */
    private fun restoreServerSettings(serverSettingsObj: JSONObject) {
        try {
            // Restore Electrum servers (merge, skip duplicates)
            val serversArray = serverSettingsObj.optJSONArray("electrumServers")
            if (serversArray != null) {
                val existingServers = repository.getAllElectrumServers()
                val existingKeys = existingServers.map { "${it.cleanUrl()}:${it.port}" }.toSet()
                var restoredActiveId: String? = null

                for (i in 0 until serversArray.length()) {
                    val serverObj = serversArray.getJSONObject(i)
                    val url = serverObj.getString("url")
                    val port = serverObj.optInt("port", 50001)
                    val useSsl = serverObj.optBoolean("useSsl", false)
                    val useTor = serverObj.optBoolean("useTor", false)
                    val key = "${ElectrumConfig(url = url).cleanUrl()}:$port"

                    if (key in existingKeys) {
                        // Server already exists; if it was marked active, find the existing one
                        if (serverObj.optBoolean("isActive", false)) {
                            restoredActiveId = existingServers.find {
                                "${it.cleanUrl()}:${it.port}" == key
                            }?.id
                        }
                        continue
                    }

                    // Reject malformed hosts/ports — backups are not trusted input.
                    if (ServerUrlValidator.validateHostAndPort(url, port) != null) {
                        SecureLog.w(
                            TAG,
                            "Skipping Electrum server from backup with invalid host/port",
                            releaseMessage = "Backup contained an invalid Electrum server",
                        )
                        continue
                    }
                    // Refuse to silently add a plaintext + non-Tor server from
                    // backup. Auto-switch could later promote such a server to
                    // active and the user would have no awareness of the trust
                    // downgrade. A determined user can re-add it manually.
                    if (!useSsl && !useTor) {
                        SecureLog.w(
                            TAG,
                            "Skipping Electrum server from backup that uses neither SSL nor Tor",
                            releaseMessage = "Backup contained an untrusted Electrum server",
                        )
                        continue
                    }

                    val newConfig = ElectrumConfig(
                        name = serverObj.optString("name", "").ifBlank { null },
                        url = url,
                        port = port,
                        useSsl = useSsl,
                        useTor = useTor,
                    )
                    val saved = repository.saveElectrumServer(newConfig)
                    if (serverObj.optBoolean("isActive", false)) {
                        restoredActiveId = saved.id
                    }
                }

                // Refresh the servers state flow
                _serversState.value = ServersState(
                    servers = repository.getAllElectrumServers(),
                    activeServerId = restoredActiveId ?: repository.getActiveServerId(),
                )
            }

            restoreLiquidServers(serverSettingsObj.optJSONArray("liquidServers"))
            if (serverSettingsObj.has("liquidTorEnabled")) {
                repository.setLiquidTorEnabled(serverSettingsObj.getBoolean("liquidTorEnabled"))
            }
            if (serverSettingsObj.has("liquidAutoSwitch")) {
                repository.setLiquidAutoSwitchEnabled(serverSettingsObj.getBoolean("liquidAutoSwitch"))
            }
            if (serverSettingsObj.has("liquidServerSelectedByUser")) {
                repository.setUserSelectedLiquidServer(serverSettingsObj.getBoolean("liquidServerSelectedByUser"))
            }
            serverSettingsObj.optString("liquidExplorer", "").takeIf { it.isNotBlank() }
                ?.let { repository.setLiquidExplorer(it) }
            serverSettingsObj.optString("liquidExplorerCustomUrl", "").takeIf { it.isNotBlank() }
                ?.let { repository.setCustomLiquidExplorerUrl(it) }

            // Restore block explorer custom URL only — don't restore the type
            // selection so we never silently enable an external service on import.
            val explorerObj = serverSettingsObj.optJSONObject("blockExplorer")
            if (explorerObj != null) {
                val customUrl = explorerObj.optString("customUrl", "")
                if (customUrl.isNotBlank()) {
                    if (ServerUrlValidator.validate(customUrl) == null) {
                        repository.setCustomMempoolUrl(customUrl)
                    } else {
                        SecureLog.w(
                            TAG,
                            "Skipping custom mempool URL from backup: failed validation",
                            releaseMessage = "Backup contained an invalid block explorer URL",
                        )
                    }
                }
            }

            // Restore fee source custom URL only — same rationale as above.
            val feeObj = serverSettingsObj.optJSONObject("feeSource")
            if (feeObj != null) {
                val customUrl = feeObj.optString("customUrl", "")
                if (customUrl.isNotBlank()) {
                    if (ServerUrlValidator.validate(customUrl) == null) {
                        repository.setCustomFeeSourceUrl(customUrl)
                    } else {
                        SecureLog.w(
                            TAG,
                            "Skipping custom fee source URL from backup: failed validation",
                            releaseMessage = "Backup contained an invalid fee source URL",
                        )
                    }
                }
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) {
                android.util.Log.w("WalletViewModel", "Failed to restore server settings: ${e.message}")
            }
        }
    }

    // ==================== Encryption Helpers ====================
    // Delegates to CryptoUtils for testability.

    private data class EncryptedPayload(
        val salt: ByteArray,
        val iv: ByteArray,
        val ciphertext: ByteArray,
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is EncryptedPayload) return false
            return salt.contentEquals(other.salt) &&
                iv.contentEquals(other.iv) &&
                ciphertext.contentEquals(other.ciphertext)
        }

        override fun hashCode(): Int {
            var result = salt.contentHashCode()
            result = 31 * result + iv.contentHashCode()
            result = 31 * result + ciphertext.contentHashCode()
            return result
        }

        fun toCryptoPayload() = CryptoUtils.EncryptedPayload(salt, iv, ciphertext)
    }

    private fun encryptData(
        plaintext: ByteArray,
        password: String,
    ): EncryptedPayload {
        val result = CryptoUtils.encrypt(plaintext, password, PBKDF2_ITERATIONS)
        return EncryptedPayload(result.salt, result.iv, result.ciphertext)
    }

    private fun decryptData(
        payload: EncryptedPayload,
        password: String,
    ): ByteArray {
        return CryptoUtils.decrypt(payload.toCryptoPayload(), password, PBKDF2_ITERATIONS)
    }

    // ==================== Auto-Switch Server ====================

    /**
     * Set auto-switch server on disconnect
     */
    fun setAutoSwitchServer(enabled: Boolean) {
        repository.setAutoSwitchServerEnabled(enabled)
        _autoSwitchServer.value = enabled
    }

    // ==================== Tor Management ====================

    /**
     * Check if Tor is enabled in settings
     */
    fun isTorEnabled(): Boolean = repository.isTorEnabled()

    /**
     * Set Tor enabled state
     */
    fun setTorEnabled(enabled: Boolean) {
        repository.setTorEnabled(enabled)
        if (enabled) {
            startTor()
        } else {
            // Disabling Tor: disconnect from server since the connection
            // is now broken (Tor proxy is gone). The user can reconnect
            // if the server is reachable without Tor.
            if (_uiState.value.isConnected || _uiState.value.isConnecting) {
                disconnect()
            }
            stopTor()
        }
    }

    /**
     * Start the Tor service
     */
    fun startTor() {
        torManager.start()
    }

    /**
     * Stop the Tor service
     */
    fun stopTor() {
        if (!shouldKeepTorRunning()) {
            torManager.stop()
        }
    }

    /**
     * Check if Tor is ready for use
     */
    fun isTorReady(): Boolean = torManager.isReady()

    /**
     * Accept a server's certificate after user approval (TOFU).
     * Stores the fingerprint and retries the connection.
     */
    fun acceptCertificate() {
        val state = _certDialogState.value ?: return
        val certInfo = state.certInfo

        // Store the approved fingerprint
        repository.acceptServerCertificate(certInfo.host, certInfo.port, certInfo.sha256Fingerprint)

        // Clear dialog
        _certDialogState.value = null

        // Retry the connection
        if (state.pendingConfig != null) {
            connectToElectrum(state.pendingConfig)
        } else if (state.pendingServerId != null) {
            connectToServer(state.pendingServerId)
        }
    }

    /**
     * Reject a server's certificate - cancel the connection attempt.
     */
    fun rejectCertificate() {
        _certDialogState.value = null
        _uiState.value = _uiState.value.copy(isConnecting = false, error = null)
    }

    /**
     * Clean up resources when ViewModel is cleared
     */
    override fun onCleared() {
        super.onCleared()
        lifecycleCoordinator.dispose()
        stopBtcPriceRefreshLoop()
        btcPriceFetchJob?.cancel()
        ConnectivityKeepAlivePolicy.updateBitcoinState(
            context = appContext,
            connected = false,
            electrumUsesTor = false,
            externalTorRequired = false,
        )
        // Run on IO to avoid blocking the main thread with socket closes
        CoroutineScope(Dispatchers.IO).launch {
            repository.disconnect()
            repository.close()
            torManager.stop()
        }
    }

    /**
     * Disconnect from the current Electrum server
     */
    fun disconnect() {
        if (_uiState.value.isConnecting) {
            cancelConnection()
            return
        }
        stopBackgroundSync()
        stopHeartbeat()
        stopReconnectRetry()
        _uiState.value = _uiState.value.copy(isConnected = false, serverVersion = null)
        viewModelScope.launch(Dispatchers.IO) {
            repository.setUserDisconnected(true)
            repository.disconnect()
        }
    }

    /**
     * Cancel an in-progress connection attempt
     */
    fun cancelConnection() {
        stopBackgroundSync()
        stopHeartbeat()
        stopReconnectRetry()
        val jobToCancel = connectionJob
        connectionJob = null
        _uiState.value = _uiState.value.copy(isConnecting = false, isConnected = false, serverVersion = null, error = null)
        viewModelScope.launch(Dispatchers.IO) {
            repository.setUserDisconnected(true)
            jobToCancel?.cancelAndJoin()
            repository.disconnect()
        }
    }

    // ==================== Display Settings ====================

    /**
     * Toggle privacy mode (hides all monetary amounts)
     */
    fun togglePrivacyMode() {
        val newValue = !_privacyMode.value
        _privacyMode.value = newValue
        repository.setPrivacyMode(newValue)
    }

    /**
     * Set the Layer 1 denomination preference
     */
    fun setDenomination(denomination: String) {
        repository.setLayer1Denomination(denomination)
        _denominationState.value = denomination
    }

    fun setAppLocale(locale: AppLocale) {
        repository.setAppLocale(locale)
        _appLocale.value = locale
    }

    fun setSwipeMode(mode: String) {
        repository.setSwipeMode(mode)
        _swipeMode.value = mode
    }

    // ==================== Mempool Server Settings ====================

    /**
     * Get the current mempool server setting
     */
    fun getMempoolServer(): String = repository.getMempoolServer()

    /**
     * Set the mempool server preference
     */
    fun setMempoolServer(server: String) {
        repository.setMempoolServer(server)
        _mempoolServerState.value = server
    }

    /**
     * Get the full mempool URL for block explorer
     */
    fun getMempoolUrl(): String = repository.getMempoolUrl()

    /**
     * Get the custom mempool URL
     */
    fun getCustomMempoolUrl(): String {
        return repository.getCustomMempoolUrl() ?: ""
    }

    /**
     * Set the custom mempool URL
     */
    fun setCustomMempoolUrl(url: String) {
        repository.setCustomMempoolUrl(url)
    }

    /**
     * Get the custom fee source server URL
     */
    fun getCustomFeeSourceUrl(): String {
        return repository.getCustomFeeSourceUrl() ?: ""
    }

    /**
     * Check if the currently active fee source URL is a .onion address.
     * Returns false if fee source is off, Electrum, or a clearnet URL.
     */
    fun isFeeSourceOnion(): Boolean {
        return repository.getFeeSourceUrl()?.let { url ->
            try {
                java.net.URI(url).host?.endsWith(".onion") == true
            } catch (_: Exception) {
                url.endsWith(".onion")
            }
        } == true
    }

    /**
     * Check if the currently active price source is a .onion address.
     */
    fun isPriceSourceOnion(): Boolean {
        return repository.getPriceSource() == SecureStorage.PRICE_SOURCE_MEMPOOL_ONION
    }

    /**
     * Set the custom fee source server URL
     */
    fun setCustomFeeSourceUrl(url: String) {
        repository.setCustomFeeSourceUrl(url)
        syncForegroundConnectivityPolicy()
    }

    // ==================== Spend Unconfirmed ====================

    /**
     * Get whether spending unconfirmed UTXOs is allowed
     */
    fun getSpendUnconfirmed(): Boolean = repository.getSpendUnconfirmed()

    /**
     * Set whether spending unconfirmed UTXOs is allowed
     */
    fun setSpendUnconfirmed(enabled: Boolean) {
        repository.setSpendUnconfirmed(enabled)
    }

    fun getPsbtQrDensity(): SecureStorage.QrDensity = repository.getPsbtQrDensity()

    fun setPsbtQrDensity(density: SecureStorage.QrDensity) {
        repository.setPsbtQrDensity(density)
        _psbtQrDensityState.value = density
    }

    fun getPsbtQrBrightness(): Float = repository.getPsbtQrBrightness()

    fun setPsbtQrBrightness(brightness: Float) {
        val clampedBrightness = brightness.coerceIn(SecureStorage.MIN_PSBT_QR_BRIGHTNESS, 1f)
        repository.setPsbtQrBrightness(clampedBrightness)
        _psbtQrBrightnessState.value = clampedBrightness
    }

    fun isNfcEnabled(): Boolean = repository.isNfcEnabled()

    fun setNfcEnabled(enabled: Boolean) {
        repository.setNfcEnabled(enabled)
    }

    fun isWalletNotificationsEnabled(): Boolean = repository.isWalletNotificationsEnabled()

    fun setWalletNotificationsEnabled(enabled: Boolean) {
        repository.setWalletNotificationsEnabled(enabled)
    }

    fun isForegroundConnectivityEnabled(): Boolean = repository.isForegroundConnectivityEnabled()

    fun setForegroundConnectivityEnabled(enabled: Boolean) {
        repository.setForegroundConnectivityEnabled(enabled)
        syncForegroundConnectivityPolicy()
        if (shouldKeepTorRunning()) {
            torManager.start()
        } else {
            torManager.stop()
        }
        _settingsRefreshVersion.value += 1
    }

    fun isAppUpdateCheckEnabled(): Boolean = repository.isAppUpdateCheckEnabled()

    fun setAppUpdateCheckEnabled(enabled: Boolean) {
        repository.setAppUpdateCheckEnabled(enabled)
        appUpdateCheckJob?.cancel()
        appUpdateCheckJob = null

        if (!enabled) {
            _appUpdateStatus.value = AppUpdateStatus.Disabled
            _appUpdatePrompt.value = null
        } else {
            _appUpdateStatus.value = AppUpdateStatus.Checking
            checkForAppUpdate(force = true)
        }

        _settingsRefreshVersion.value += 1
    }

    // ==================== Fee Estimation Settings ====================

    /**
     * Set the fee source preference
     */
    fun setFeeSource(source: String) {
        repository.setFeeSource(source)
        _feeSourceState.value = source
        syncForegroundConnectivityPolicy()

        if (source == SecureStorage.FEE_SOURCE_OFF) {
            _feeEstimationState.value = FeeEstimationResult.Disabled
        } else {
            // Immediately fetch when enabling a fee source
            fetchFeeEstimates()
        }
    }

    /**
     * Fetch fee estimates from the configured source
     * Call this when opening the Send screen
     * Uses precise endpoint only if the connected Electrum server supports sub-sat fees
     */
    fun fetchFeeEstimates() {
        val feeSource = repository.getFeeSource()

        if (feeSource == SecureStorage.FEE_SOURCE_OFF) {
            _feeEstimationState.value = FeeEstimationResult.Disabled
            return
        }
        if (!isAppSessionUnlocked) return

        viewModelScope.launch {
            _feeEstimationState.value = FeeEstimationResult.Loading

            if (feeSource == SecureStorage.FEE_SOURCE_ELECTRUM) {
                // Fetch from connected Electrum server via BDK's estimateFee()
                val result = repository.fetchElectrumFeeEstimates()
                _feeEstimationState.value =
                    result.fold(
                        onSuccess = { estimates -> FeeEstimationResult.Success(estimates) },
                        onFailure = { error -> FeeEstimationResult.Error(error.message ?: "Not connected to server") },
                    )
                return@launch
            }

            // Fetch from mempool.space HTTP API
            val feeSourceUrl =
                repository.getFeeSourceUrl() ?: run {
                    _feeEstimationState.value = FeeEstimationResult.Disabled
                    return@launch
                }

            val useTorProxy =
                try {
                    java.net.URI(feeSourceUrl).host?.endsWith(".onion") == true
                } catch (_: Exception) {
                    feeSourceUrl.endsWith(".onion")
                }

            // If Tor is required, ensure it's running — fee source manages its own Tor needs
            if (useTorProxy) {
                if (!torManager.isReady()) {
                    torManager.start()
                    if (!torManager.awaitReady(TOR_BOOTSTRAP_TIMEOUT_MS)) {
                        val msg = if (torState.value.status == TorStatus.ERROR) {
                            "Tor failed to start"
                        } else {
                            "Tor connection timed out"
                        }
                        _feeEstimationState.value = FeeEstimationResult.Error(msg)
                        return@launch
                    }
                    // Brief settle time for the SOCKS proxy after bootstrap
                    delay(TOR_POST_BOOTSTRAP_DELAY_MS)
                }
            }

            // HTTP fee providers should decide whether `/precise` works; the service
            // already falls back to `/recommended` when the endpoint is unsupported.
            val usePrecise = true

            val result = feeEstimationService.fetchFeeEstimates(feeSourceUrl, useTorProxy, usePrecise)

            _feeEstimationState.value =
                result.fold(
                    onSuccess = { estimates -> FeeEstimationResult.Success(estimates) },
                    onFailure = { error -> FeeEstimationResult.Error(error.message ?: "Unknown error") },
                )
        }
    }

    // ==================== BTC/Fiat Price ====================

    /**
     * Set the BTC/fiat price source preference
     */
    fun setPriceSource(source: String) {
        repository.setPriceSource(source)
        _priceSourceState.value = source
        syncForegroundConnectivityPolicy()

        val sanitizedCurrency = BtcPriceService.sanitizeFiatCurrency(source, repository.getPriceCurrency())
        repository.setPriceCurrency(sanitizedCurrency)
        _priceCurrencyState.value = sanitizedCurrency

        if (source == SecureStorage.PRICE_SOURCE_OFF) {
            stopBtcPriceRefreshLoop()
            _btcPriceState.value = null
        } else {
            fetchBtcPrice(force = true)
            startBtcPriceRefreshLoop()
        }
    }

    fun setPriceCurrency(currencyCode: String) {
        val source = repository.getPriceSource()
        val sanitizedCurrency = BtcPriceService.sanitizeFiatCurrency(source, currencyCode)
        repository.setPriceCurrency(sanitizedCurrency)
        _priceCurrencyState.value = sanitizedCurrency

        if (source == SecureStorage.PRICE_SOURCE_OFF) {
            _btcPriceState.value = null
        } else {
            fetchBtcPrice(force = true)
            startBtcPriceRefreshLoop()
        }
    }

    fun setHistoricalTxFiatEnabled(enabled: Boolean) {
        repository.setHistoricalTxFiatEnabled(enabled)
        _historicalTxFiatEnabledState.value = enabled
        if (!enabled) {
            _historicalTxBtcPriceState.value = emptyMap()
        }
        _settingsRefreshVersion.value += 1
    }

    fun setExternalHistoricalTxTimestamps(timestampsById: Map<String, Long?>) {
        _externalHistoricalTxTimestamps.value = timestampsById
    }

    fun reloadRestoredAppSettings() {
        _denominationState.value = repository.getLayer1Denomination()
        _appLocale.value = repository.getAppLocale()
        _mempoolServerState.value = repository.getMempoolServer()
        _feeSourceState.value = repository.getFeeSource()
        refreshPricePreferences()
        _privacyMode.value = repository.getPrivacyMode()
        _swipeMode.value = repository.getSwipeMode()
        _psbtQrDensityState.value = repository.getPsbtQrDensity()
        _psbtQrBrightnessState.value = repository.getPsbtQrBrightness()
        _autoSwitchServer.value = repository.isAutoSwitchServerEnabled()

        if (_feeSourceState.value == SecureStorage.FEE_SOURCE_OFF) {
            _feeEstimationState.value = FeeEstimationResult.Disabled
        } else {
            fetchFeeEstimates()
        }

        if (_priceSourceState.value == SecureStorage.PRICE_SOURCE_OFF) {
            stopBtcPriceRefreshLoop()
            _btcPriceState.value = null
        } else {
            fetchBtcPrice(force = true)
            startBtcPriceRefreshLoop()
        }

        if (shouldKeepTorRunning()) {
            startTor()
        } else {
            stopTor()
        }

        syncForegroundConnectivityPolicy()
        _settingsRefreshVersion.value += 1
    }

    /**
     * Fetch BTC/fiat price from the configured source.
     */
    fun fetchBtcPrice(force: Boolean = false) {
        val source = repository.getPriceSource()
        val currencyCode = BtcPriceService.sanitizeFiatCurrency(source, repository.getPriceCurrency())

        if (source == SecureStorage.PRICE_SOURCE_OFF) {
            _btcPriceState.value = null
            return
        }
        if (!isAppSessionUnlocked) return

        val now = SystemClock.elapsedRealtime()
        val recentlyFetchedSameQuote =
            lastBtcPriceFetchSource == source &&
                lastBtcPriceFetchCurrency == currencyCode &&
                now - lastBtcPriceFetchElapsedMs < PRICE_FETCH_DEDUP_WINDOW_MS
        if (!force && (btcPriceFetchJob?.isActive == true || recentlyFetchedSameQuote)) {
            return
        }

        btcPriceFetchJob?.cancel()
        btcPriceFetchJob =
            viewModelScope.launch {
                lastBtcPriceFetchSource = source
                lastBtcPriceFetchCurrency = currencyCode
                lastBtcPriceFetchElapsedMs = SystemClock.elapsedRealtime()

                if (source == SecureStorage.PRICE_SOURCE_MEMPOOL_ONION && !torManager.isReady()) {
                    torManager.start()
                    if (!torManager.awaitReady(TOR_BOOTSTRAP_TIMEOUT_MS)) {
                        if (BuildConfig.DEBUG) {
                            android.util.Log.e("WalletViewModel", "Tor not ready for price fetch")
                        }
                        return@launch
                    }
                    delay(TOR_POST_BOOTSTRAP_DELAY_MS)
                }

                val price =
                    when (source) {
                        SecureStorage.PRICE_SOURCE_MEMPOOL -> btcPriceService.fetchFromMempool(currencyCode)
                        SecureStorage.PRICE_SOURCE_MEMPOOL_ONION -> btcPriceService.fetchFromMempoolOnion(currencyCode)
                        SecureStorage.PRICE_SOURCE_COINGECKO -> btcPriceService.fetchFromCoinGecko(currencyCode)
                        else -> null
                    }

                if (
                    repository.getPriceSource() == source &&
                    repository.getPriceCurrency() == currencyCode
                ) {
                    _btcPriceState.value = price
                }
            }
    }

    fun checkForAppUpdate(force: Boolean = false) {
        if (!repository.isAppUpdateCheckEnabled()) {
            _appUpdateStatus.value = AppUpdateStatus.Disabled
            _appUpdatePrompt.value = null
            return
        }
        if (!isAppSessionUnlocked) return

        val now = SystemClock.elapsedRealtime()
        if (appUpdateCheckJob?.isActive == true) return
        if (!force && lastAppUpdateCheckElapsedMs != 0L && now - lastAppUpdateCheckElapsedMs < APP_UPDATE_CHECK_TTL_MS) {
            return
        }

        appUpdateCheckJob =
            viewModelScope.launch {
                _appUpdateStatus.value = AppUpdateStatus.Checking

                val updateStatus =
                    appUpdateService.fetchLatestRelease().fold(
                        onSuccess = { release ->
                            lastAppUpdateCheckElapsedMs = SystemClock.elapsedRealtime()
                            when {
                                release == null -> AppUpdateStatus.UpToDate
                                release.version > (AppVersion.parse(BuildConfig.VERSION_NAME) ?: release.version) ->
                                    AppUpdateStatus.UpdateAvailable(
                                        latestVersionName = release.versionName,
                                        releaseUrl = release.htmlUrl,
                                    )

                                else -> AppUpdateStatus.UpToDate
                            }
                        },
                        onFailure = {
                            lastAppUpdateCheckElapsedMs = SystemClock.elapsedRealtime()
                            AppUpdateStatus.Error
                        },
                    )

                _appUpdateStatus.value = updateStatus
                updateAppUpdatePrompt(updateStatus)
            }
    }

    fun dismissAppUpdatePrompt(markVersionAsSeen: Boolean = true) {
        val prompt = _appUpdatePrompt.value ?: return
        if (markVersionAsSeen) {
            repository.setSeenAppUpdateVersion(prompt.latestVersionName)
        }
        _appUpdatePrompt.value = null
    }

    private fun updateAppUpdatePrompt(updateStatus: AppUpdateStatus) {
        when (updateStatus) {
            is AppUpdateStatus.UpdateAvailable -> {
                val seenVersion = repository.getSeenAppUpdateVersion()?.trim()
                _appUpdatePrompt.value =
                    if (seenVersion == updateStatus.latestVersionName) {
                        null
                    } else {
                        updateStatus
                    }
            }

            else -> _appUpdatePrompt.value = null
        }
    }

    private fun refreshPricePreferences() {
        val source = repository.getPriceSource()
        _priceSourceState.value = source

        val sanitizedCurrency = BtcPriceService.sanitizeFiatCurrency(source, repository.getPriceCurrency())
        if (sanitizedCurrency != repository.getPriceCurrency()) {
            repository.setPriceCurrency(sanitizedCurrency)
        }
        _priceCurrencyState.value = sanitizedCurrency
        _historicalTxFiatEnabledState.value = repository.isHistoricalTxFiatEnabled()
    }

    private fun normalizeHistoricalPriceTimestamp(timestamp: Long?): Long? {
        if (timestamp == null || timestamp <= 0L) return null
        return if (timestamp > 10_000_000_000L) timestamp / 1000L else timestamp
    }

    private suspend fun refreshHistoricalTxBtcPrices(request: HistoricalTxPriceRequest) {
        if (!isAppSessionUnlocked) {
            _historicalTxBtcPriceState.value = emptyMap()
            return
        }
        val supportsHistoricalPricing =
            request.priceSource == SecureStorage.PRICE_SOURCE_MEMPOOL ||
                request.priceSource == SecureStorage.PRICE_SOURCE_MEMPOOL_ONION
        val timestampedTransactions = request.transactions.filter { it.timestamp != null }

        if (!request.enabled || !supportsHistoricalPricing || timestampedTransactions.isEmpty()) {
            _historicalTxBtcPriceState.value = emptyMap()
            return
        }

        if (request.priceSource == SecureStorage.PRICE_SOURCE_MEMPOOL_ONION && !torManager.isReady()) {
            torManager.start()
            if (!torManager.awaitReady(TOR_BOOTSTRAP_TIMEOUT_MS)) {
                if (BuildConfig.DEBUG) {
                    android.util.Log.e("WalletViewModel", "Tor not ready for historical tx pricing")
                }
                _historicalTxBtcPriceState.value = emptyMap()
                return
            }
            delay(TOR_POST_BOOTSTRAP_DELAY_MS)
        }

        val cacheKey = "${request.priceSource}:${request.fiatCurrency}"
        val series =
            historicalPriceSeriesCache[cacheKey]
                ?: when (request.priceSource) {
                    SecureStorage.PRICE_SOURCE_MEMPOOL ->
                        btcPriceService.fetchHistoricalSeriesFromMempool(request.fiatCurrency)
                    SecureStorage.PRICE_SOURCE_MEMPOOL_ONION ->
                        btcPriceService.fetchHistoricalSeriesFromMempoolOnion(request.fiatCurrency)
                    else -> emptyList()
                }.also { loadedSeries ->
                    if (loadedSeries.isNotEmpty()) {
                        historicalPriceSeriesCache[cacheKey] = loadedSeries
                    }
                }

        if (series.isEmpty()) {
            _historicalTxBtcPriceState.value = emptyMap()
            return
        }

        _historicalTxBtcPriceState.value =
            buildMap {
                timestampedTransactions.forEach { transaction ->
                    val timestamp = transaction.timestamp ?: return@forEach
                    val price = BtcPriceService.resolveHistoricalPrice(series, timestamp) ?: return@forEach
                    put(transaction.txid, price)
                }
            }
    }

    private fun startBtcPriceRefreshLoop() {
        if (!isAppSessionUnlocked) return
        if (repository.getPriceSource() == SecureStorage.PRICE_SOURCE_OFF) return
        if (btcPriceRefreshJob?.isActive == true) return

        btcPriceRefreshJob =
            viewModelScope.launch {
                while (true) {
                    delay(PRICE_REFRESH_INTERVAL_MS)
                    if (repository.getPriceSource() == SecureStorage.PRICE_SOURCE_OFF) {
                        break
                    }
                    fetchBtcPrice()
                }
            }
    }

    private fun stopBtcPriceRefreshLoop() {
        btcPriceRefreshJob?.cancel()
        btcPriceRefreshJob = null
    }

    // ==================== Security Settings ====================

    /**
     * Get the current security method
     */
    fun getSecurityMethod(): SecureStorage.SecurityMethod = repository.getSecurityMethod()

    /**
     * Set the security method
     */
    fun setSecurityMethod(method: SecureStorage.SecurityMethod) {
        repository.setSecurityMethod(method)
    }

    /**
     * Save PIN code
     */
    fun savePin(pin: String) {
        repository.savePin(pin)
    }

    /**
     * Clear PIN code
     */
    fun clearPin() {
        repository.clearPin()
    }

    /**
     * Check if security is enabled
     */
    fun isSecurityEnabled(): Boolean = repository.isSecurityEnabled()

    /**
     * Get lock timing setting
     */
    fun getLockTiming(): SecureStorage.LockTiming = repository.getLockTiming()

    /**
     * Set lock timing setting
     */
    fun setLockTiming(timing: SecureStorage.LockTiming) {
        repository.setLockTiming(timing)
    }

    /**
     * Get whether screenshots are disabled
     */
    fun getDisableScreenshots(): Boolean = repository.getDisableScreenshots()

    /**
     * Set whether screenshots are disabled
     */
    fun setDisableScreenshots(disabled: Boolean) {
        repository.setDisableScreenshots(disabled)
    }

    fun getRandomizePinPad(): Boolean = repository.getRandomizePinPad()

    fun setRandomizePinPad(enabled: Boolean) {
        repository.setRandomizePinPad(enabled)
    }

    // ==================== Duress PIN / Decoy Wallet ====================

    /**
     * Check if duress mode is enabled in settings
     */
    fun isDuressEnabled(): Boolean = repository.isDuressEnabled()

    /**
     * Set up a duress wallet with a PIN and import config.
     * Creates the decoy wallet, saves the duress PIN, and enables duress mode.
     */
    fun setupDuress(
        pin: String,
        config: WalletImportConfig,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {},
    ) {
        viewModelScope.launch {
            when (val result = repository.createDuressWallet(config)) {
                is WalletResult.Success -> {
                    repository.saveDuressPin(pin)
                    repository.setDuressEnabled(true)
                    onSuccess()
                }
                is WalletResult.Error -> {
                    onError(result.message)
                }
            }
        }
    }

    /**
     * Disable duress mode: delete the decoy wallet and clear all duress data
     */
    fun disableDuress(onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            _isDuressMode.value = false
            repository.deleteDuressWallet()
            onComplete()
        }
    }

    /**
     * Enter duress mode: switch to the decoy wallet.
     * Called when the duress PIN is entered on the lock screen.
     */
    fun enterDuressMode() {
        viewModelScope.launch {
            val result = repository.switchToDuressWallet()
            if (result is WalletResult.Success) {
                _isDuressMode.value = true
                if (_uiState.value.isConnected) {
                    launchSubscriptions()
                }
            } else {
                // Duress wallet no longer exists — clean up stale config
                _isDuressMode.value = false
                repository.deleteDuressWallet()
            }
        }
    }

    /**
     * Exit duress mode: switch back to the real wallet.
     * Called when the real PIN or biometric is used on the lock screen.
     */
    fun exitDuressMode() {
        if (!_isDuressMode.value) return
        viewModelScope.launch {
            _isDuressMode.value = false
            repository.switchToRealWallet()
            if (_uiState.value.isConnected) {
                launchSubscriptions()
            }
        }
    }

    /**
     * Get the duress wallet ID (for filtering wallet lists)
     */
    fun getDuressWalletId(): String? = repository.getDuressWalletId()

    // ==================== Auto-Wipe ====================

    /**
     * Get the auto-wipe threshold setting
     */
    fun getAutoWipeThreshold(): SecureStorage.AutoWipeThreshold = repository.getAutoWipeThreshold()

    /**
     * Set the auto-wipe threshold
     */
    fun setAutoWipeThreshold(threshold: SecureStorage.AutoWipeThreshold) {
        repository.setAutoWipeThreshold(threshold)
    }

    // ==================== Cloak Mode ====================

    fun isCloakModeEnabled(): Boolean = repository.isCloakModeEnabled()

    fun enableCloakMode(code: String) {
        repository.setCloakCode(code)
        repository.setCloakModeEnabled(true)
        repository.setPendingIconAlias(SecureStorage.ALIAS_CALCULATOR)
    }

    fun disableCloakMode() {
        repository.clearCloakData()
        // clearCloakData already schedules the alias swap back to default
    }

    /**
     * Wipe all wallet data. Called when auto-wipe threshold is reached.
     * Clears clipboard, stops Tor and deletes its data, and asks the repository
     * to wipe wallet databases, preferences, and the Electrum cache database.
     *
     * Runs the repository wipe twice if the first pass reports residue, then
     * hands the final [WalletRepository.WipeResult] to [onComplete]. The caller
     * is responsible for what to do on partial failure (typically: log and
     * still kill the process so a half-functional wallet is not reachable).
     */
    fun wipeAllData(onComplete: (WalletRepository.WipeResult) -> Unit = {}) {
        viewModelScope.launch {
            val app = getApplication<Application>()

            // Clear clipboard to prevent sensitive data (addresses, PSBTs) from surviving wipe
            try {
                val clipboard =
                    app.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                        as android.content.ClipboardManager
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    clipboard.clearPrimaryClip()
                } else {
                    clipboard.setPrimaryClip(
                        android.content.ClipData.newPlainText("", ""),
                    )
                }
            } catch (_: Exception) {
            }

            // Stop Tor and wipe its data directory (relay descriptors, circuit state, etc.)
            try {
                torManager.wipeTorData()
            } catch (_: Exception) {
            }

            // First pass: best-effort destructive wipe + verification.
            var result = repository.wipeAllData()
            if (!result.success) {
                // Retry once. Some filesystems or Keystore aliases need a second
                // pass after handles are released; this materially reduces the
                // odds of leaving recoverable residue without blocking forever.
                result = repository.wipeAllData()
            }

            onComplete(result)
        }
    }

    // ==================== Sweep Private Key ====================

    private val _sweepState = MutableStateFlow(SweepState())
    val sweepState: StateFlow<SweepState> = _sweepState.asStateFlow()

    fun scanWifBalances(wif: String) {
        viewModelScope.launch {
            _sweepState.value = SweepState(isScanning = true, scanProgress = "Scanning...")
            when (
                val result =
                    repository.scanWifBalances(wif) { progress ->
                        _sweepState.value = _sweepState.value.copy(scanProgress = progress)
                    }
            ) {
                is WalletResult.Success -> {
                    _sweepState.value =
                        _sweepState.value.copy(
                            isScanning = false,
                            scanResults = result.data,
                            scanProgress = null,
                        )
                }
                is WalletResult.Error -> {
                    _sweepState.value =
                        _sweepState.value.copy(
                            isScanning = false,
                            error = result.message,
                            scanProgress = null,
                        )
                }
            }
        }
    }

    fun sweepPrivateKey(
        wif: String,
        destination: String,
        feeRate: Double,
    ) {
        viewModelScope.launch {
            _sweepState.value = _sweepState.value.copy(isSweeping = true, sweepProgress = "Building transactions...")
            when (
                val result =
                    repository.sweepPrivateKey(wif, destination, feeRate) { progress ->
                        _sweepState.value = _sweepState.value.copy(sweepProgress = progress)
                    }
            ) {
                is WalletResult.Success -> {
                    _sweepState.value =
                        _sweepState.value.copy(
                            isSweeping = false,
                            sweepTxids = result.data,
                            sweepProgress = null,
                        )
                    for (txid in result.data) {
                        _events.emit(WalletEvent.TransactionSent(txid))
                    }
                    // Quick sync to update balance with the incoming swept funds
                    sync()
                }
                is WalletResult.Error -> {
                    _sweepState.value =
                        _sweepState.value.copy(
                            isSweeping = false,
                            error = result.message,
                            sweepProgress = null,
                        )
                }
            }
        }
    }

    fun resetSweepState() {
        _sweepState.value = SweepState()
    }

    fun isWifPrivateKey(input: String): Boolean = repository.isWifPrivateKey(input)

    private fun syncForegroundConnectivityPolicy() {
        ConnectivityKeepAlivePolicy.updateForegroundConnectivityEnabled(
            context = appContext,
            enabled = repository.isForegroundConnectivityEnabled(),
        )
        ConnectivityKeepAlivePolicy.updateBitcoinState(
            context = appContext,
            connected = _uiState.value.isConnected,
            electrumUsesTor = isBitcoinElectrumTorRequired(),
            externalTorRequired = isFeeSourceOnion() || isPriceSourceOnion(),
        )
    }

    private fun isBitcoinElectrumTorRequired(): Boolean {
        return _uiState.value.isConnected &&
            (
                repository.getElectrumConfig()?.let { config ->
                    config.useTor || config.isOnionAddress()
                } == true
            )
    }

    private fun shouldKeepTorRunningForBitcoin(localTorRequirement: Boolean): Boolean {
        syncForegroundConnectivityPolicy()
        return localTorRequirement || ConnectivityKeepAlivePolicy.hasTorRequirementOutsideBitcoin()
    }

    fun shouldKeepTorRunning(): Boolean {
        syncForegroundConnectivityPolicy()
        return repository.isTorEnabled() || ConnectivityKeepAlivePolicy.hasAnyTorRequirement()
    }

    companion object {
        private const val TAG = "WalletViewModel"
        private const val CONNECTION_TIMEOUT_CLEARNET_MS = 15_000L
        private const val CONNECTION_TIMEOUT_TOR_MS = 30_000L
        private const val TOR_BOOTSTRAP_TIMEOUT_MS = 60_000L
        private const val TOR_POST_BOOTSTRAP_DELAY_MS = 2_500L
        private const val PBKDF2_ITERATIONS = 600_000
        private const val HEARTBEAT_INTERVAL_MS = 60_000L
        private const val HEARTBEAT_RETRY_INTERVAL_MS = 15_000L
        private const val HEARTBEAT_PING_SOCKET_TIMEOUT_MS = 8_000
        private const val HEARTBEAT_PING_LOCK_TIMEOUT_MS = 3_000L
        private const val HEARTBEAT_PING_TIMEOUT_MS = 10_000L
        private const val HEARTBEAT_MAX_FAILURES = 3
        private const val FOREGROUND_RESUME_PING_SOCKET_TIMEOUT_MS = 2_500
        private const val FOREGROUND_RESUME_PING_LOCK_TIMEOUT_MS = 750L
        private const val FOREGROUND_RESUME_PING_TIMEOUT_MS = 3_500L
        private const val PRICE_REFRESH_INTERVAL_MS = 300_000L
        private const val PRICE_FETCH_DEDUP_WINDOW_MS = 60_000L
        private const val APP_UPDATE_CHECK_TTL_MS = 3_600_000L
        private const val RECONNECT_BASE_DELAY_MS = 5_000L
        private const val RECONNECT_MAX_DELAY_MS = 60_000L
        private const val RECONNECT_MAX_ATTEMPTS = 10
        private const val ADDRESS_BOOK_PREVIEW_SIZE = 20
    }
}

/**
 * UI state for wallet operations
 */
data class WalletUiState(
    val isLoading: Boolean = false,
    val isConnecting: Boolean = false,
    val isConnected: Boolean = false,
    val isSending: Boolean = false,
    val sendStatus: String? = null,
    val serverVersion: String? = null,
    val error: String? = null,
)

sealed class AppUpdateStatus {
    data object Disabled : AppUpdateStatus()

    data object Checking : AppUpdateStatus()

    data object UpToDate : AppUpdateStatus()

    data object Error : AppUpdateStatus()

    data class UpdateAvailable(
        val latestVersionName: String,
        val releaseUrl: String,
    ) : AppUpdateStatus()
}

/**
 * One-time events from the wallet
 */
sealed class WalletEvent {
    data object WalletImported : WalletEvent()

    data object WalletDeleted : WalletEvent()

    data object WalletSwitched : WalletEvent()

    data object Connected : WalletEvent()

    data object SyncCompleted : WalletEvent()

    data object ServerDeleted : WalletEvent()

    data class AddressGenerated(val address: String) : WalletEvent()

    data class TransactionSent(val txid: String) : WalletEvent()

    data class FeeBumped(val newTxid: String) : WalletEvent()

    data class CpfpCreated(val childTxid: String) : WalletEvent()

    data class TransactionRedirected(val newTxid: String) : WalletEvent()

    data class PsbtCreated(val psbtBase64: String) : WalletEvent()

    data class WalletExported(val walletName: String) : WalletEvent()

    data object LabelsRestored : WalletEvent()

    data class Bip329LabelsExported(val count: Int) : WalletEvent()

    data class Bip329LabelsImported(val count: Int) : WalletEvent()

    data class Error(val message: String) : WalletEvent()
}

/**
 * State for sweep private key operations
 */
data class SweepState(
    val isScanning: Boolean = false,
    val isSweeping: Boolean = false,
    val scanResults: List<WalletRepository.SweepScanResult> = emptyList(),
    val sweepTxids: List<String> = emptyList(),
    val scanProgress: String? = null,
    val sweepProgress: String? = null,
    val error: String? = null,
) {
    val totalBalanceSats: ULong get() = scanResults.sumOf { it.balanceSats.toLong() }.toULong()
    val hasBalance: Boolean get() = scanResults.isNotEmpty()
    val isComplete: Boolean get() = sweepTxids.isNotEmpty()
}

/**
 * State for Electrum servers
 */
data class ServersState(
    val servers: List<ElectrumConfig> = emptyList(),
    val activeServerId: String? = null,
)

/**
 * Draft state for Send screen (persisted while app is open/minimized)
 */
data class SendScreenDraft(
    val recipientAddress: String = "",
    val amountInput: String = "",
    val label: String = "",
    val feeRate: Double = 1.0,
    val isMaxSend: Boolean = false,
    val selectedUtxoOutpoints: List<String> = emptyList(),
    val assetId: String? = null,
)

/**
 * State for PSBT creation and signing flow (watch-only wallets)
 */
data class PsbtState(
    val isCreating: Boolean = false,
    val isCombining: Boolean = false,
    val isBroadcasting: Boolean = false,
    val broadcastStatus: String? = null,
    val psbtId: String? = null,
    val unsignedPsbtBase64: String? = null,
    val signerExportPsbtBase64: String? = null,
    val signedData: String? = null, // Signed PSBT/tx data awaiting broadcast confirmation
    val pendingLabel: String? = null,
    val error: String? = null,
    val presentSignatures: Int = 0,
    val requiredSignatures: Int? = null,
    val isReadyToBroadcast: Boolean = false,
    // Actual transaction details from BDK (not client-side estimates)
    val actualFeeSats: ULong = 0UL,
    val recipientAddress: String? = null,
    val recipientAmountSats: ULong = 0UL,
    val changeAmountSats: ULong? = null,
    val totalInputSats: ULong = 0UL,
)

/**
 * State for manual transaction broadcast (standalone, not tied to any wallet).
 */
data class ManualBroadcastState(
    val isBroadcasting: Boolean = false,
    val broadcastStatus: String? = null,
    val txid: String? = null,
    val error: String? = null,
    val preview: WalletRepository.ManualBroadcastPreview? = null,
    val previewInput: String? = null,
)

/**
 * State for the certificate approval/warning dialog (TOFU)
 */
data class CertDialogState(
    val certInfo: CertificateInfo,
    val isFirstUse: Boolean, // true = first connection, false = cert changed
    val oldFingerprint: String? = null, // non-null when cert changed
    val pendingConfig: ElectrumConfig? = null, // config to retry after approval
    val pendingServerId: String? = null, // server ID to retry after approval
)

/**
 * Walk an exception's cause chain to find a specific exception type.
 * SSLSocket.startHandshake() wraps TrustManager exceptions in SSLHandshakeException,
 * so we need to unwrap to find our TOFU exceptions (CertificateFirstUseException, etc.).
 */
private inline fun <reified T : Throwable> Throwable.findCause(): T? {
    var current: Throwable? = this
    val visited = mutableSetOf<Throwable>()
    while (current != null && visited.add(current)) {
        if (current is T) return current
        current = current.cause
    }
    return null
}
