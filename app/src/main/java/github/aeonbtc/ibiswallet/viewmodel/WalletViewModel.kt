package github.aeonbtc.ibiswallet.viewmodel

import android.app.Application
import android.net.Uri
import android.util.Base64
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import github.aeonbtc.ibiswallet.data.BtcPriceService
import github.aeonbtc.ibiswallet.data.FeeEstimationService
import github.aeonbtc.ibiswallet.data.local.SecureStorage
import github.aeonbtc.ibiswallet.data.model.*
import github.aeonbtc.ibiswallet.data.repository.WalletRepository
import github.aeonbtc.ibiswallet.tor.TorManager
import github.aeonbtc.ibiswallet.tor.TorState
import github.aeonbtc.ibiswallet.tor.TorStatus
import github.aeonbtc.ibiswallet.util.CertificateFirstUseException
import github.aeonbtc.ibiswallet.util.CertificateInfo
import github.aeonbtc.ibiswallet.util.CertificateMismatchException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * ViewModel for managing wallet state across the app
 */
class WalletViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = WalletRepository(application)
    private val torManager = TorManager(application)
    private val feeEstimationService = FeeEstimationService()
    private val btcPriceService = BtcPriceService()

    val walletState: StateFlow<WalletState> = repository.walletState
    val torState: StateFlow<TorState> = torManager.torState

    private val _uiState = MutableStateFlow(WalletUiState())
    val uiState: StateFlow<WalletUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<WalletEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<WalletEvent> = _events.asSharedFlow()

    // Active connection job (cancellable)
    private var connectionJob: Job? = null

    // Background sync loop job - runs every ~45s while connected
    private var backgroundSyncJob: Job? = null

    // Server state for reactive UI updates
    private val _serversState = MutableStateFlow(ServersState())
    val serversState: StateFlow<ServersState> = _serversState.asStateFlow()

    // Denomination state (BTC or SATS)
    private val _denominationState = MutableStateFlow(repository.getDenomination())
    val denominationState: StateFlow<String> = _denominationState.asStateFlow()

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

    // BTC/USD price state (null if disabled or fetch failed)
    private val _btcPriceState = MutableStateFlow<Double?>(null)
    val btcPriceState: StateFlow<Double?> = _btcPriceState.asStateFlow()

    // Minimum fee rate from connected Electrum server
    val minFeeRate: StateFlow<Double> = repository.minFeeRate

    // Privacy mode - hides all amounts when active (persisted across sessions)
    private val _privacyMode = MutableStateFlow(repository.getPrivacyMode())
    val privacyMode: StateFlow<Boolean> = _privacyMode.asStateFlow()

    // Pre-selected UTXO for coin control (set from AllUtxosScreen)
    private val _preSelectedUtxo = MutableStateFlow<github.aeonbtc.ibiswallet.data.model.UtxoInfo?>(null)
    val preSelectedUtxo: StateFlow<github.aeonbtc.ibiswallet.data.model.UtxoInfo?> = _preSelectedUtxo.asStateFlow()

    // Track which wallet is currently being synced (for UI progress indicator)
    private val _syncingWalletId = MutableStateFlow<String?>(null)
    val syncingWalletId: StateFlow<String?> = _syncingWalletId.asStateFlow()

    // Send screen draft state (persisted while app is open/minimized)
    private val _sendScreenDraft = MutableStateFlow(SendScreenDraft())
    val sendScreenDraft: StateFlow<SendScreenDraft> = _sendScreenDraft.asStateFlow()

    // PSBT state for watch-only wallet signing flow
    private val _psbtState = MutableStateFlow(PsbtState())
    val psbtState: StateFlow<PsbtState> = _psbtState.asStateFlow()

    // Certificate TOFU state
    private val _certDialogState = MutableStateFlow<CertDialogState?>(null)
    val certDialogState: StateFlow<CertDialogState?> = _certDialogState.asStateFlow()

    // Dry-run fee estimation from BDK TxBuilder
    private val _dryRunResult = MutableStateFlow<github.aeonbtc.ibiswallet.data.model.DryRunResult?>(null)
    val dryRunResult: StateFlow<github.aeonbtc.ibiswallet.data.model.DryRunResult?> = _dryRunResult.asStateFlow()
    private var dryRunJob: kotlinx.coroutines.Job? = null

    // Manual broadcast state (standalone tx broadcast, not tied to any wallet)
    private val _manualBroadcastState = MutableStateFlow(ManualBroadcastState())
    val manualBroadcastState: StateFlow<ManualBroadcastState> = _manualBroadcastState.asStateFlow()

    // Duress mode state (not persisted — resets on app restart which forces re-auth via lock screen)
    private val _isDuressMode = MutableStateFlow(false)
    val isDuressMode: StateFlow<Boolean> = _isDuressMode.asStateFlow()

    init {
        // Initialize servers state
        refreshServersState()

        // Fetch fee estimates on startup (independent of Electrum)
        fetchFeeEstimates()

        // Load existing wallet and auto-connect to Electrum on startup
        viewModelScope.launch {
            // Load wallet if one exists
            if (repository.isWalletInitialized()) {
                _uiState.value = _uiState.value.copy(isLoading = true)
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

            // Auto-connect to saved Electrum server if available,
            // regardless of whether a wallet is imported
            repository.getElectrumConfig()?.let { config ->
                connectToElectrum(config)
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
                    _events.emit(WalletEvent.WalletImported)
                    // Auto-trigger full sync for the newly imported wallet
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

    /**
     * Generate a new wallet with a fresh BIP39 mnemonic.
     * Uses BDK's Mnemonic constructor which sources entropy from the platform's
     * cryptographically secure random number generator (getrandom on Linux/Android).
     */
    fun generateWallet(config: WalletImportConfig) {
        importWallet(config)
    }

    /**
     * Connect to an Electrum server
     * Automatically enables/disables Tor based on server type
     */
    fun connectToElectrum(config: ElectrumConfig) {
        connectionJob?.cancel()
        connectionJob =
            viewModelScope.launch {
                _uiState.value = _uiState.value.copy(isConnecting = true, error = null)

                try {
                    // Auto-enable Tor for .onion addresses, auto-disable for clearnet
                    // (but keep Tor alive if the active fee source is .onion)
                    val feeSourceNeedsTor = isFeeSourceOnion()
                    if (config.isOnionAddress()) {
                        repository.setTorEnabled(true)
                    } else if (repository.isTorEnabled()) {
                        // Switching to clearnet — Electrum won't use Tor proxy
                        repository.setTorEnabled(false)
                        // Only stop the Tor service if nothing else needs it
                        if (!feeSourceNeedsTor) {
                            torManager.stop()
                        }
                    }
                    val needsTor = config.isOnionAddress()

                    if (needsTor && !torManager.isReady()) {
                        torManager.start()

                        var attempts = 0
                        while (!torManager.isReady() && attempts < 120) {
                            delay(500)
                            attempts++

                            if (torState.value.status == TorStatus.ERROR) {
                                _uiState.value =
                                    _uiState.value.copy(
                                        isConnecting = false,
                                        error = "Tor failed to start: ${torState.value.statusMessage}",
                                    )
                                _events.emit(WalletEvent.Error("Tor failed to start"))
                                return@launch
                            }
                        }

                        if (!torManager.isReady()) {
                            _uiState.value =
                                _uiState.value.copy(
                                    isConnecting = false,
                                    error = "Tor connection timed out",
                                )
                            _events.emit(WalletEvent.Error("Tor connection timed out"))
                            return@launch
                        }
                    }

                    // Connect with timeout
                    val result =
                        withTimeoutOrNull(CONNECTION_TIMEOUT_MS) {
                            repository.connectToElectrum(config)
                        }

                    when {
                        result == null -> {
                            repository.disconnect()
                            _uiState.value =
                                _uiState.value.copy(
                                    isConnecting = false,
                                    isConnected = false,
                                    error = "Connection timed out",
                                )
                            _events.emit(WalletEvent.Error("Connection timed out"))
                        }
                        result is WalletResult.Success -> {
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
                            // Background sync as safety net / keep-alive.
                            // Start BEFORE subscriptions so it runs even if the
                            // subscription socket fails (critical for Tor where
                            // the third circuit may not establish).
                            startBackgroundSync()
                            // Smart sync + real-time subscriptions (Sparrow-style).
                            // Runs in the background — does NOT block the connection
                            // flow. Creates a third upstream socket for push
                            // notifications. Over Tor this takes 3-10s extra.
                            // If it fails, background sync polling covers us.
                            launchSubscriptions()
                        }
                        result is WalletResult.Error -> {
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
     * Quick sync wallet with the blockchain (revealed addresses only)
     * Used by balance screen refresh
     */
    fun sync() {
        viewModelScope.launch {
            // Watch address wallets use Electrum-only sync
            if (repository.isWatchAddressWallet()) {
                val activeId = repository.getActiveWalletId() ?: return@launch
                when (val result = repository.syncWatchAddress(activeId)) {
                    is WalletResult.Success -> _events.emit(WalletEvent.SyncCompleted)
                    is WalletResult.Error -> _events.emit(WalletEvent.Error(result.message))
                }
                return@launch
            }
            when (val result = repository.sync()) {
                is WalletResult.Success -> {
                    _events.emit(WalletEvent.SyncCompleted)
                }
                is WalletResult.Error -> {
                    _events.emit(WalletEvent.Error(result.message))
                }
            }
        }
    }

    /**
     * Start background sync loop. Runs every ~45s while connected.
     * Uses the script hash pre-check so it's nearly free when nothing changed.
     * Also acts as a keep-alive: if the server connection is dead, marks
     * isConnected=false so the UI reflects reality without waiting for user action.
     */
    private fun startBackgroundSync() {
        backgroundSyncJob?.cancel()
        var consecutiveFailures = 0
        backgroundSyncJob =
            viewModelScope.launch {
                while (true) {
                    delay(BACKGROUND_SYNC_INTERVAL_MS)
                    if (!_uiState.value.isConnected) break
                    when (repository.sync()) {
                        is WalletResult.Success -> consecutiveFailures = 0
                        is WalletResult.Error -> {
                            consecutiveFailures++
                            // 2 consecutive failures = connection is dead, update UI
                            if (consecutiveFailures >= 2) {
                                _uiState.value = _uiState.value.copy(isConnected = false)
                                _events.emit(WalletEvent.Error("Server connection lost"))
                                break
                            }
                        }
                    }
                }
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
    private fun launchSubscriptions() {
        viewModelScope.launch {
            val subResult =
                withContext(Dispatchers.IO) {
                    repository.startRealTimeSubscriptions()
                }
            when (subResult) {
                WalletRepository.SubscriptionResult.SYNCED,
                WalletRepository.SubscriptionResult.FULL_SYNCED,
                ->
                    _events.emit(WalletEvent.SyncCompleted)
                WalletRepository.SubscriptionResult.NO_CHANGES -> { }
                WalletRepository.SubscriptionResult.FAILED -> {
                    // Subscription socket failed (common over Tor).
                    // Background sync polling is already running as fallback.
                    // Run a one-time sync so the wallet is up-to-date.
                    when (val syncResult = repository.sync()) {
                        is WalletResult.Success ->
                            _events.emit(WalletEvent.SyncCompleted)
                        is WalletResult.Error ->
                            _events.emit(WalletEvent.Error(syncResult.message))
                    }
                }
            }
        }
    }

    /**
     * Full sync for a specific wallet (address discovery scan).
     * Used by Manage Wallets screen for manual full rescan.
     * If the wallet is not active, switches to it first.
     */
    fun fullSync(walletId: String) {
        viewModelScope.launch {
            _syncingWalletId.value = walletId

            // Switch to the wallet if it's not already active
            val activeId = repository.getActiveWalletId()
            if (walletId != activeId) {
                when (val switchResult = repository.switchWallet(walletId)) {
                    is WalletResult.Success -> {
                        _events.emit(WalletEvent.WalletSwitched)
                    }
                    is WalletResult.Error -> {
                        _events.emit(WalletEvent.Error(switchResult.message))
                        _syncingWalletId.value = null
                        return@launch
                    }
                }
            }

            // Watch address wallets use Electrum-only sync
            val result =
                if (repository.isWatchAddressWallet()) {
                    repository.syncWatchAddress(walletId)
                } else {
                    repository.requestFullSync(walletId)
                }

            when (result) {
                is WalletResult.Success -> {
                    _events.emit(WalletEvent.SyncCompleted)
                }
                is WalletResult.Error -> {
                    _events.emit(WalletEvent.Error(result.message))
                }
            }
            _syncingWalletId.value = null
        }
    }

    /**
     * Get a new receiving address
     */
    fun getNewAddress() {
        viewModelScope.launch {
            when (val result = repository.getNewAddress()) {
                is WalletResult.Success -> {
                    _events.emit(WalletEvent.AddressGenerated(result.data))
                }
                is WalletResult.Error -> {
                    _events.emit(WalletEvent.Error(result.message))
                }
            }
        }
    }

    /**
     * Get a new receiving address (suspend version for direct await)
     */
    suspend fun getNewAddressSuspend(): String? {
        return when (val result = repository.getNewAddress()) {
            is WalletResult.Success -> result.data
            is WalletResult.Error -> null
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
    }

    /**
     * Delete a label for an address
     */
    fun deleteAddressLabel(address: String) {
        repository.deleteAddressLabel(address)
    }

    /**
     * Get all address labels
     */
    fun getAllAddressLabels(): Map<String, String> {
        return repository.getAllAddressLabels()
    }

    /**
     * Get all transaction labels
     */
    fun getAllTransactionLabels(): Map<String, String> {
        return repository.getAllTransactionLabels()
    }

    /**
     * Save a label for a transaction
     */
    fun saveTransactionLabel(
        txid: String,
        label: String,
    ) {
        repository.saveTransactionLabel(txid, label)
    }

    /**
     * Get all addresses (receive, change, used)
     */
    fun getAllAddresses(): Triple<List<github.aeonbtc.ibiswallet.data.model.WalletAddress>, List<github.aeonbtc.ibiswallet.data.model.WalletAddress>, List<github.aeonbtc.ibiswallet.data.model.WalletAddress>> {
        return repository.getAllAddresses()
    }

    /**
     * Get all UTXOs
     */
    fun getAllUtxos(): List<github.aeonbtc.ibiswallet.data.model.UtxoInfo> {
        return repository.getAllUtxos()
    }

    /**
     * Freeze/unfreeze a UTXO
     */
    fun setUtxoFrozen(
        outpoint: String,
        frozen: Boolean,
    ) {
        repository.setUtxoFrozen(outpoint, frozen)
    }

    /**
     * Set a pre-selected UTXO for the Send screen (from AllUtxosScreen)
     */
    fun setPreSelectedUtxo(utxo: github.aeonbtc.ibiswallet.data.model.UtxoInfo) {
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

    /**
     * Clear send screen draft (called on successful send or app close)
     */
    private fun clearSendScreenDraft() {
        _sendScreenDraft.value = SendScreenDraft()
    }

    /**
     * Send Bitcoin to an address with specified fee rate
     * @param selectedUtxos Optional list of specific UTXOs to spend from (coin control)
     * @param label Optional label for the transaction
     * @param isMaxSend If true, sends entire balance minus fees
     */
    fun sendBitcoin(
        recipientAddress: String,
        amountSats: ULong,
        feeRate: Float = 1.0f,
        selectedUtxos: List<github.aeonbtc.ibiswallet.data.model.UtxoInfo>? = null,
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

    /**
     * Estimate the actual fee using BDK's TxBuilder (dry-run, no network).
     * Call this when the user changes amount, fee rate, or UTXO selection.
     */
    fun estimateFee(
        recipientAddress: String,
        amountSats: ULong,
        feeRate: Float,
        selectedUtxos: List<github.aeonbtc.ibiswallet.data.model.UtxoInfo>? = null,
        isMaxSend: Boolean = false,
    ) {
        dryRunJob?.cancel()
        dryRunJob =
            viewModelScope.launch {
                val result =
                    repository.dryRunBuildTx(
                        recipientAddress = recipientAddress,
                        amountSats = amountSats,
                        feeRateSatPerVb = feeRate,
                        selectedUtxos = selectedUtxos,
                        isMaxSend = isMaxSend,
                    )
                _dryRunResult.value = result
            }
    }

    fun clearDryRunResult() {
        dryRunJob?.cancel()
        _dryRunResult.value = null
    }

    /** Estimate fee for a multi-recipient transaction. */
    fun estimateFeeMulti(
        recipients: List<github.aeonbtc.ibiswallet.data.model.Recipient>,
        feeRate: Float,
        selectedUtxos: List<github.aeonbtc.ibiswallet.data.model.UtxoInfo>? = null,
    ) {
        dryRunJob?.cancel()
        dryRunJob =
            viewModelScope.launch {
                _dryRunResult.value = repository.dryRunBuildTx(recipients, feeRate, selectedUtxos)
            }
    }

    /** Send to multiple recipients in a single transaction. */
    fun sendBitcoinMulti(
        recipients: List<github.aeonbtc.ibiswallet.data.model.Recipient>,
        feeRate: Float = 1.0f,
        selectedUtxos: List<github.aeonbtc.ibiswallet.data.model.UtxoInfo>? = null,
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
        recipients: List<github.aeonbtc.ibiswallet.data.model.Recipient>,
        feeRate: Float = 1.0f,
        selectedUtxos: List<github.aeonbtc.ibiswallet.data.model.UtxoInfo>? = null,
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
                            unsignedPsbtBase64 = details.psbtBase64,
                            pendingLabel = label,
                            actualFeeSats = details.feeSats,
                            recipientAddress = details.recipientAddress,
                            recipientAmountSats = details.recipientAmountSats,
                            changeAmountSats = details.changeAmountSats,
                            totalInputSats = details.totalInputSats,
                        )
                    clearSendScreenDraft()
                    _events.emit(WalletEvent.PsbtCreated(details.psbtBase64))
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
        feeRate: Float = 1.0f,
        selectedUtxos: List<github.aeonbtc.ibiswallet.data.model.UtxoInfo>? = null,
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
                            unsignedPsbtBase64 = details.psbtBase64,
                            pendingLabel = label,
                            actualFeeSats = details.feeSats,
                            recipientAddress = details.recipientAddress,
                            recipientAmountSats = details.recipientAmountSats,
                            changeAmountSats = details.changeAmountSats,
                            totalInputSats = details.totalInputSats,
                        )
                    clearSendScreenDraft()
                    _events.emit(WalletEvent.PsbtCreated(details.psbtBase64))
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

    /**
     * Store signed transaction data for user confirmation before broadcasting.
     * Called after scanning the signed PSBT/tx back from the hardware wallet.
     */
    fun setSignedTransactionData(data: String) {
        _psbtState.value = _psbtState.value.copy(signedData = data, error = null)
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
                    repository.broadcastRawTx(data, pendingLabel, onProgress)
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
    }

    /**
     * Broadcast a manually provided signed transaction (raw hex or signed PSBT).
     * Standalone — the transaction may not belong to any loaded wallet.
     */
    fun broadcastManualTransaction(data: String) {
        viewModelScope.launch {
            _manualBroadcastState.value = ManualBroadcastState(isBroadcasting = true)
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
                    _manualBroadcastState.value = ManualBroadcastState(error = result.message)
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
        newFeeRate: Float,
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSending = true, error = null)

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
        feeRate: Float,
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSending = true, error = null)

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
    fun deleteWallet(walletId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            when (val result = repository.deleteWallet(walletId)) {
                is WalletResult.Success -> {
                    _uiState.value = _uiState.value.copy(isLoading = false)
                    _events.emit(WalletEvent.WalletDeleted)
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
     * Edit wallet metadata (name and optionally fingerprint for watch-only)
     */
    fun editWallet(
        walletId: String,
        newName: String,
        newFingerprint: String? = null,
    ) {
        repository.editWallet(walletId, newName, newFingerprint)
    }

    /**
     * Switch to a different wallet
     */
    fun switchWallet(walletId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            when (val result = repository.switchWallet(walletId)) {
                is WalletResult.Success -> {
                    _uiState.value = _uiState.value.copy(isLoading = false)
                    _events.emit(WalletEvent.WalletSwitched)
                    // Re-subscribe new wallet's addresses and sync if needed
                    if (_uiState.value.isConnected) {
                        launchSubscriptions()
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

    /**
     * Connect to a saved server by ID
     * Automatically enables/disables Tor based on server type
     */
    fun connectToServer(serverId: String) {
        connectionJob?.cancel()
        connectionJob =
            viewModelScope.launch {
                _uiState.value = _uiState.value.copy(isConnecting = true, error = null)

                try {
                    // Get the server config to check if it's an onion address
                    val servers = repository.getAllElectrumServers()
                    val serverConfig = servers.find { it.id == serverId }

                    // Auto-enable Tor for .onion addresses, auto-disable for clearnet
                    // (but keep Tor alive if the active fee source is .onion)
                    val isOnion = serverConfig?.isOnionAddress() == true
                    val feeSourceNeedsTor = isFeeSourceOnion()
                    if (isOnion) {
                        repository.setTorEnabled(true)
                    } else if (repository.isTorEnabled()) {
                        // Switching to clearnet — Electrum won't use Tor proxy
                        repository.setTorEnabled(false)
                        // Only stop the Tor service if nothing else needs it
                        if (!feeSourceNeedsTor) {
                            torManager.stop()
                        }
                    }
                    val needsTor = isOnion

                    if (needsTor && !torManager.isReady()) {
                        torManager.start()

                        var attempts = 0
                        while (!torManager.isReady() && attempts < 120) {
                            delay(500)
                            attempts++

                            if (torState.value.status == TorStatus.ERROR) {
                                _uiState.value =
                                    _uiState.value.copy(
                                        isConnecting = false,
                                        error = "Tor failed to start: ${torState.value.statusMessage}",
                                    )
                                _events.emit(WalletEvent.Error("Tor failed to start"))
                                return@launch
                            }
                        }

                        if (!torManager.isReady()) {
                            _uiState.value =
                                _uiState.value.copy(
                                    isConnecting = false,
                                    error = "Tor connection timed out",
                                )
                            _events.emit(WalletEvent.Error("Tor connection timed out"))
                            return@launch
                        }
                    }

                    // Connect with timeout
                    val result =
                        withTimeoutOrNull(CONNECTION_TIMEOUT_MS) {
                            repository.connectToServer(serverId)
                        }

                    when {
                        result == null -> {
                            repository.disconnect()
                            _uiState.value =
                                _uiState.value.copy(
                                    isConnecting = false,
                                    isConnected = false,
                                    error = "Connection timed out",
                                )
                            _events.emit(WalletEvent.Error("Connection timed out"))
                        }
                        result is WalletResult.Success -> {
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
                            startBackgroundSync()
                            launchSubscriptions()
                        }
                        result is WalletResult.Error -> {
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

    /**
     * Get the last full sync timestamp for a wallet
     */
    fun getLastFullSyncTime(walletId: String): Long? = repository.getLastFullSyncTime(walletId)

    // ==================== Wallet Export/Import ====================

    /**
     * Export a wallet to a JSON backup file, optionally encrypted with AES-256-GCM
     */
    fun exportWallet(
        walletId: String,
        uri: Uri,
        includeLabels: Boolean,
        password: String?,
    ) {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true, error = null)

                val walletMetadata =
                    repository.getWalletMetadata(walletId)
                        ?: throw IllegalStateException("Wallet not found")
                val keyMaterial =
                    repository.getKeyMaterial(walletId)
                        ?: throw IllegalStateException("Key material not found")

                // Build the wallet payload JSON
                val payloadJson =
                    JSONObject().apply {
                        put("version", 1)
                        put("encrypted", password != null)
                        put("exportedAt", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date()))

                        put(
                            "wallet",
                            JSONObject().apply {
                                put("name", walletMetadata.name)
                                put("addressType", walletMetadata.addressType.name)
                                put("derivationPath", walletMetadata.derivationPath)
                                put("network", walletMetadata.network.name)
                                put("isWatchOnly", walletMetadata.isWatchOnly)
                                put("createdAt", walletMetadata.createdAt)
                            },
                        )

                        put(
                            "keyMaterial",
                            JSONObject().apply {
                                if (keyMaterial.mnemonic != null) put("mnemonic", keyMaterial.mnemonic)
                                if (keyMaterial.extendedPublicKey != null) {
                                    put(
                                        "extendedPublicKey",
                                        keyMaterial.extendedPublicKey,
                                    )
                                }
                            },
                        )

                        if (includeLabels) {
                            val addressLabels = repository.getAllAddressLabelsForWallet(walletId)
                            val txLabels = repository.getAllTransactionLabelsForWallet(walletId)

                            put(
                                "labels",
                                JSONObject().apply {
                                    put(
                                        "addresses",
                                        JSONObject().apply {
                                            addressLabels.forEach { (addr, label) -> put(addr, label) }
                                        },
                                    )
                                    put(
                                        "transactions",
                                        JSONObject().apply {
                                            txLabels.forEach { (txid, label) -> put(txid, label) }
                                        },
                                    )
                                },
                            )
                        }
                    }

                val outputJson =
                    if (password != null) {
                        // Encrypt the payload
                        val plaintext = payloadJson.toString().toByteArray(Charsets.UTF_8)
                        val encrypted = encryptData(plaintext, password)

                        JSONObject().apply {
                            put("version", 1)
                            put("encrypted", true)
                            put("salt", Base64.encodeToString(encrypted.salt, Base64.NO_WRAP))
                            put("iv", Base64.encodeToString(encrypted.iv, Base64.NO_WRAP))
                            put("data", Base64.encodeToString(encrypted.ciphertext, Base64.NO_WRAP))
                        }
                    } else {
                        payloadJson
                    }

                // Write to the chosen URI
                withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openOutputStream(uri)?.use { stream ->
                        stream.write(outputJson.toString(2).toByteArray(Charsets.UTF_8))
                    } ?: throw IllegalStateException("Could not open output stream")
                }

                _uiState.value = _uiState.value.copy(isLoading = false)
                _events.emit(WalletEvent.WalletExported(walletMetadata.name))
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
                _events.emit(WalletEvent.Error("Export failed: ${e.message}"))
            }
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
                    it.readBytes()
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
    fun importFromBackup(backupJson: JSONObject) {
        val walletObj = backupJson.getJSONObject("wallet")
        val keyMaterialObj = backupJson.getJSONObject("keyMaterial")

        val addressType =
            try {
                AddressType.valueOf(walletObj.getString("addressType"))
            } catch (_: Exception) {
                AddressType.SEGWIT
            }

        val network =
            try {
                WalletNetwork.valueOf(walletObj.optString("network", "BITCOIN"))
            } catch (_: Exception) {
                WalletNetwork.BITCOIN
            }

        val mnemonic =
            keyMaterialObj.optString("mnemonic", null.toString()).let {
                if (it == "null" || it.isBlank()) null else it
            }
        val xpub =
            keyMaterialObj.optString("extendedPublicKey", null.toString()).let {
                if (it == "null" || it.isBlank()) null else it
            }

        val keyMaterial =
            mnemonic ?: xpub
                ?: throw IllegalStateException("No key material found in backup")

        val isWatchOnly = mnemonic == null

        val config =
            WalletImportConfig(
                name = walletObj.optString("name", "Restored Wallet"),
                keyMaterial = keyMaterial,
                addressType = addressType,
                customDerivationPath =
                    walletObj.optString("derivationPath", null.toString()).let {
                        if (it == "null" || it.isBlank()) null else it
                    },
                network = network,
                isWatchOnly = isWatchOnly,
            )

        // Import the wallet, then restore labels once import completes
        val labelsObj = backupJson.optJSONObject("labels")
        if (labelsObj != null) {
            viewModelScope.launch {
                // Wait for import to complete by watching for the wallet to appear
                var activeWalletId: String? = null
                for (attempt in 0 until 20) {
                    delay(250)
                    activeWalletId = walletState.value.activeWallet?.id
                    if (activeWalletId != null && !_uiState.value.isLoading) break
                }
                if (activeWalletId == null) return@launch

                val addressLabels = labelsObj.optJSONObject("addresses")
                if (addressLabels != null) {
                    val keys = addressLabels.keys()
                    while (keys.hasNext()) {
                        val addr = keys.next()
                        val label = addressLabels.getString(addr)
                        repository.saveAddressLabelForWallet(activeWalletId, addr, label)
                    }
                }

                val txLabels = labelsObj.optJSONObject("transactions")
                if (txLabels != null) {
                    val keys = txLabels.keys()
                    while (keys.hasNext()) {
                        val txid = keys.next()
                        val label = txLabels.getString(txid)
                        repository.saveTransactionLabelForWallet(activeWalletId, txid, label)
                    }
                }

                // Notify UI that labels have been restored
                _events.emit(WalletEvent.LabelsRestored)
            }
        }

        importWallet(config)
    }

    // ==================== Encryption Helpers ====================

    private data class EncryptedPayload(
        val salt: ByteArray,
        val iv: ByteArray,
        val ciphertext: ByteArray,
    )

    private fun encryptData(
        plaintext: ByteArray,
        password: String,
    ): EncryptedPayload {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }

        val passwordChars = password.toCharArray()
        try {
            val keySpec = PBEKeySpec(passwordChars, salt, PBKDF2_ITERATIONS, 256)
            val secretKey =
                SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(keySpec)
            val keyBytes = secretKey.encoded
            val aesKey = SecretKeySpec(keyBytes, "AES")

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, aesKey, GCMParameterSpec(128, iv))
            val ciphertext = cipher.doFinal(plaintext)

            return EncryptedPayload(salt, iv, ciphertext)
        } finally {
            // Zero out sensitive data
            passwordChars.fill('\u0000')
        }
    }

    private fun decryptData(
        payload: EncryptedPayload,
        password: String,
    ): ByteArray {
        val passwordChars = password.toCharArray()
        try {
            val keySpec = PBEKeySpec(passwordChars, payload.salt, PBKDF2_ITERATIONS, 256)
            val secretKey =
                SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(keySpec)
            val keyBytes = secretKey.encoded
            val aesKey = SecretKeySpec(keyBytes, "AES")

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, aesKey, GCMParameterSpec(128, payload.iv))
            return cipher.doFinal(payload.ciphertext)
        } finally {
            // Zero out sensitive data
            passwordChars.fill('\u0000')
        }
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
        torManager.stop()
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
        repository.disconnect()
        torManager.stop()
    }

    /**
     * Disconnect from the current Electrum server
     */
    fun disconnect() {
        stopBackgroundSync()
        repository.disconnect()
        _uiState.value = _uiState.value.copy(isConnected = false, serverVersion = null)
    }

    /**
     * Cancel an in-progress connection attempt
     */
    fun cancelConnection() {
        stopBackgroundSync()
        connectionJob?.cancel()
        connectionJob = null
        repository.disconnect()
        _uiState.value = _uiState.value.copy(isConnecting = false, isConnected = false, serverVersion = null, error = null)
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
     * Set the denomination preference
     */
    fun setDenomination(denomination: String) {
        repository.setDenomination(denomination)
        _denominationState.value = denomination
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
     * Set the custom fee source server URL
     */
    fun setCustomFeeSourceUrl(url: String) {
        repository.setCustomFeeSourceUrl(url)
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

    // ==================== Fee Estimation Settings ====================

    /**
     * Set the fee source preference
     */
    fun setFeeSource(source: String) {
        repository.setFeeSource(source)
        _feeSourceState.value = source

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
                    var torAttempts = 0
                    while (!torManager.isReady() && torAttempts < 120) {
                        delay(500)
                        torAttempts++
                        if (torState.value.status == TorStatus.ERROR) {
                            _feeEstimationState.value = FeeEstimationResult.Error("Tor failed to start")
                            return@launch
                        }
                    }
                    if (!torManager.isReady()) {
                        _feeEstimationState.value = FeeEstimationResult.Error("Tor connection timed out")
                        return@launch
                    }
                }
                // Wait for the SOCKS proxy to be ready to accept connections
                delay(500)
            }

            // Only use precise fees if the connected server supports sub-sat fee rates
            val usePrecise = repository.supportsSubSatFees()

            val result = feeEstimationService.fetchFeeEstimates(feeSourceUrl, useTorProxy, usePrecise)

            _feeEstimationState.value =
                result.fold(
                    onSuccess = { estimates -> FeeEstimationResult.Success(estimates) },
                    onFailure = { error -> FeeEstimationResult.Error(error.message ?: "Unknown error") },
                )
        }
    }

    // ==================== BTC/USD Price ====================

    /**
     * Set the BTC/USD price source preference
     */
    fun setPriceSource(source: String) {
        repository.setPriceSource(source)
        _priceSourceState.value = source

        // Clear price when disabled, fetch when enabled
        if (source == SecureStorage.PRICE_SOURCE_OFF) {
            _btcPriceState.value = null
        } else {
            fetchBtcPrice()
        }
    }

    /**
     * Fetch BTC/USD price from the configured source
     */
    fun fetchBtcPrice() {
        val source = repository.getPriceSource()

        if (source == SecureStorage.PRICE_SOURCE_OFF) {
            _btcPriceState.value = null
            return
        }

        viewModelScope.launch {
            val price =
                when (source) {
                    SecureStorage.PRICE_SOURCE_MEMPOOL -> btcPriceService.fetchFromMempool()
                    SecureStorage.PRICE_SOURCE_COINGECKO -> btcPriceService.fetchFromCoinGecko()
                    else -> null
                }
            _btcPriceState.value = price
        }
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

    // ==================== Duress PIN / Decoy Wallet ====================

    /**
     * Check if duress mode is enabled in settings
     */
    fun isDuressEnabled(): Boolean = repository.isDuressEnabled()

    /**
     * Set up a duress wallet with a PIN and mnemonic.
     * Creates the decoy wallet, saves the duress PIN, and enables duress mode.
     */
    fun setupDuress(
        pin: String,
        mnemonic: String,
        passphrase: String?,
        customDerivationPath: String? = null,
        addressType: AddressType = AddressType.SEGWIT,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {},
    ) {
        viewModelScope.launch {
            when (val result = repository.createDuressWallet(mnemonic, passphrase, customDerivationPath, addressType)) {
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
     * Clears clipboard, stops Tor and deletes its data, wipes all wallet
     * databases and preferences, and deletes the Electrum cache DB file.
     */
    fun wipeAllData(onComplete: () -> Unit = {}) {
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

            // Wipe all wallet data (BDK databases, preferences, Electrum cache tables, in-memory state)
            repository.wipeAllData()

            // Delete the Electrum cache database file (not just cleared rows)
            // to prevent forensic recovery from SQLite free pages
            try {
                app.deleteDatabase("electrum_cache.db")
            } catch (_: Exception) {
            }

            onComplete()
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
        feeRate: Float,
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

    companion object {
        private const val CONNECTION_TIMEOUT_MS = 30_000L
        private const val PBKDF2_ITERATIONS = 600_000
        private const val BACKGROUND_SYNC_INTERVAL_MS = 300_000L
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

    data class PsbtCreated(val psbtBase64: String) : WalletEvent()

    data class WalletExported(val walletName: String) : WalletEvent()

    data object LabelsRestored : WalletEvent()

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
    val feeRate: Float = 1.0f,
    val isMaxSend: Boolean = false,
    val selectedUtxoOutpoints: List<String> = emptyList(), // Store outpoints to restore selection
)

/**
 * State for PSBT creation and signing flow (watch-only wallets)
 */
data class PsbtState(
    val isCreating: Boolean = false,
    val isBroadcasting: Boolean = false,
    val broadcastStatus: String? = null,
    val unsignedPsbtBase64: String? = null,
    val signedData: String? = null, // Signed PSBT/tx data awaiting broadcast confirmation
    val pendingLabel: String? = null,
    val error: String? = null,
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
