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
    
    // Privacy mode - hides all amounts when active (resets each session)
    private val _privacyMode = MutableStateFlow(false)
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
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = result.message
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
        _serversState.value = ServersState(
            servers = repository.getAllElectrumServers(),
            activeServerId = repository.getActiveServerId()
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
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = result.message
                    )
                    _events.emit(WalletEvent.Error(result.message))
                }
            }
        }
    }
    
    /**
     * Import a wallet from mnemonic (simple version for backward compatibility)
     */
    fun importWallet(
        mnemonic: String,
        passphrase: String? = null,
        network: WalletNetwork = WalletNetwork.BITCOIN
    ) {
        val config = WalletImportConfig(
            name = "Main Wallet",
            keyMaterial = mnemonic,
            passphrase = passphrase,
            network = network
        )
        importWallet(config)
    }
    
    /**
     * Connect to an Electrum server
     * Automatically enables/disables Tor based on server type
     */
    fun connectToElectrum(config: ElectrumConfig) {
        connectionJob?.cancel()
        connectionJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isConnecting = true, error = null)
            
            try {
                // Auto-toggle Tor based on server type
                if (config.isOnionAddress()) {
                    // Enable Tor for .onion addresses
                    repository.setTorEnabled(true)
                    
                    // Start Tor if not already running
                    if (!torManager.isReady()) {
                        torManager.start()
                        
                        // Wait for Tor to be ready
                        var attempts = 0
                        while (!torManager.isReady() && attempts < 120) {
                            delay(500)
                            attempts++
                            
                            if (torState.value.status == TorStatus.ERROR) {
                                _uiState.value = _uiState.value.copy(
                                    isConnecting = false,
                                    error = "Tor failed to start: ${torState.value.statusMessage}"
                                )
                                _events.emit(WalletEvent.Error("Tor failed to start"))
                                return@launch
                            }
                        }
                        
                        if (!torManager.isReady()) {
                            _uiState.value = _uiState.value.copy(
                                isConnecting = false,
                                error = "Tor connection timed out"
                            )
                            _events.emit(WalletEvent.Error("Tor connection timed out"))
                            return@launch
                        }
                    }
                } else {
                    // Disable Tor for clearnet addresses
                    if (repository.isTorEnabled()) {
                        repository.setTorEnabled(false)
                        torManager.stop()
                    }
                }
                
                // Connect with timeout
                val result = withTimeoutOrNull(CONNECTION_TIMEOUT_MS) {
                    repository.connectToElectrum(config)
                }
                
                when {
                    result == null -> {
                        repository.disconnect()
                        _uiState.value = _uiState.value.copy(
                            isConnecting = false,
                            isConnected = false,
                            error = "Connection timed out"
                        )
                        _events.emit(WalletEvent.Error("Connection timed out"))
                    }
                    result is WalletResult.Success -> {
                        _uiState.value = _uiState.value.copy(
                            isConnecting = false,
                            isConnected = true
                        )
                        refreshServersState()
                        _events.emit(WalletEvent.Connected)
                        // Refresh fee estimates (server may support sub-sat fees)
                        fetchFeeEstimates()
                        // Auto-sync after connecting
                        sync()
                    }
                    result is WalletResult.Error -> {
                        _uiState.value = _uiState.value.copy(
                            isConnecting = false,
                            isConnected = false,
                            error = result.message
                        )
                        _events.emit(WalletEvent.Error(result.message))
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
     * Full sync for a specific wallet (address discovery scan)
     * Used by Manage Wallets screen for manual full rescan
     */
    fun fullSync(walletId: String) {
        viewModelScope.launch {
            _syncingWalletId.value = walletId
            when (val result = repository.requestFullSync(walletId)) {
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
     * Get a new change address
     */
    fun getNewChangeAddress() {
        viewModelScope.launch {
            when (val result = repository.getNewChangeAddress()) {
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
     * Get a new change address (suspend version for direct await)
     */
    suspend fun getNewChangeAddressSuspend(): String? {
        return when (val result = repository.getNewChangeAddress()) {
            is WalletResult.Success -> result.data
            is WalletResult.Error -> null
        }
    }
    
    /**
     * Save a label for an address
     */
    fun saveAddressLabel(address: String, label: String) {
        repository.saveAddressLabel(address, label)
    }
    
    /**
     * Get label for an address
     */
    fun getAddressLabel(address: String): String? {
        return repository.getAddressLabel(address)
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
    fun saveTransactionLabel(txid: String, label: String) {
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
    fun setUtxoFrozen(outpoint: String, frozen: Boolean) {
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
    fun clearSendScreenDraft() {
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
        isMaxSend: Boolean = false
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSending = true, error = null)
            
            when (val result = repository.sendBitcoin(recipientAddress, amountSats, feeRate, selectedUtxos, label, isMaxSend)) {
                is WalletResult.Success -> {
                    _uiState.value = _uiState.value.copy(isSending = false)
                    // Clear send screen draft on successful transaction
                    clearSendScreenDraft()
                    _events.emit(WalletEvent.TransactionSent(result.data))
                }
                is WalletResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isSending = false,
                        error = result.message
                    )
                    _events.emit(WalletEvent.Error(result.message))
                }
            }
        }
    }
    
    /**
     * Bump fee of an unconfirmed transaction using RBF
     */
    fun bumpFee(txid: String, newFeeRate: Float) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSending = true, error = null)
            
            when (val result = repository.bumpFee(txid, newFeeRate)) {
                is WalletResult.Success -> {
                    _uiState.value = _uiState.value.copy(isSending = false)
                    _events.emit(WalletEvent.FeeBumped(result.data))
                }
                is WalletResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isSending = false,
                        error = result.message
                    )
                    _events.emit(WalletEvent.Error(result.message))
                }
            }
        }
    }
    
    /**
     * Speed up an incoming transaction using CPFP
     */
    fun cpfp(parentTxid: String, feeRate: Float) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSending = true, error = null)
            
            when (val result = repository.cpfp(parentTxid, feeRate)) {
                is WalletResult.Success -> {
                    _uiState.value = _uiState.value.copy(isSending = false)
                    _events.emit(WalletEvent.CpfpCreated(result.data))
                }
                is WalletResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isSending = false,
                        error = result.message
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
     * Returns the actual vsize from the network
     */
    suspend fun fetchTransactionVsize(txid: String): Int? {
        return repository.fetchTransactionVsizeFromElectrum(txid)
    }
    
    /**
     * Delete the currently active wallet
     */
    fun deleteWallet() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            
            when (val result = repository.deleteWallet()) {
                is WalletResult.Success -> {
                    _uiState.value = _uiState.value.copy(isLoading = false)
                    _events.emit(WalletEvent.WalletDeleted)
                }
                is WalletResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = result.message
                    )
                    _events.emit(WalletEvent.Error(result.message))
                }
            }
        }
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
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = result.message
                    )
                    _events.emit(WalletEvent.Error(result.message))
                }
            }
        }
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
                    // Sync the newly active wallet if connected
                    if (_uiState.value.isConnected) {
                        sync()
                    }
                }
                is WalletResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = result.message
                    )
                    _events.emit(WalletEvent.Error(result.message))
                }
            }
        }
    }
    
    /**
     * Clear error state
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
    
    /**
     * Check if wallet is initialized
     */
    fun isWalletInitialized(): Boolean = repository.isWalletInitialized()
    
    /**
     * Get stored Electrum config (active server)
     */
    fun getElectrumConfig(): ElectrumConfig? = repository.getElectrumConfig()
    
    /**
     * Get all saved Electrum servers
     */
    fun getAllServers(): List<ElectrumConfig> = repository.getAllElectrumServers()
    
    /**
     * Get the active server ID
     */
    fun getActiveServerId(): String? = repository.getActiveServerId()
    
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
        repository.deleteElectrumServer(serverId)
        // Update connected state if this was the active server
        if (repository.getActiveServerId() == null) {
            _uiState.value = _uiState.value.copy(isConnected = false)
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
        connectionJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isConnecting = true, error = null)
            
            try {
                // Get the server config to check if it's an onion address
                val servers = repository.getAllElectrumServers()
                val serverConfig = servers.find { it.id == serverId }
                
                if (serverConfig != null) {
                    // Auto-toggle Tor based on server type
                    if (serverConfig.isOnionAddress()) {
                        // Enable Tor for .onion addresses
                        repository.setTorEnabled(true)
                        
                        // Start Tor if not already running
                        if (!torManager.isReady()) {
                            torManager.start()
                            
                            // Wait for Tor to be ready
                            var attempts = 0
                            while (!torManager.isReady() && attempts < 120) {
                                delay(500)
                                attempts++
                                
                                if (torState.value.status == TorStatus.ERROR) {
                                    _uiState.value = _uiState.value.copy(
                                        isConnecting = false,
                                        error = "Tor failed to start: ${torState.value.statusMessage}"
                                    )
                                    _events.emit(WalletEvent.Error("Tor failed to start"))
                                    return@launch
                                }
                            }
                            
                            if (!torManager.isReady()) {
                                _uiState.value = _uiState.value.copy(
                                    isConnecting = false,
                                    error = "Tor connection timed out"
                                )
                                _events.emit(WalletEvent.Error("Tor connection timed out"))
                                return@launch
                            }
                        }
                    } else {
                        // Disable Tor for clearnet addresses
                        if (repository.isTorEnabled()) {
                            repository.setTorEnabled(false)
                            torManager.stop()
                        }
                    }
                }
                
                // Connect with timeout
                val result = withTimeoutOrNull(CONNECTION_TIMEOUT_MS) {
                    repository.connectToServer(serverId)
                }
                
                when {
                    result == null -> {
                        repository.disconnect()
                        _uiState.value = _uiState.value.copy(
                            isConnecting = false,
                            isConnected = false,
                            error = "Connection timed out"
                        )
                        _events.emit(WalletEvent.Error("Connection timed out"))
                    }
                    result is WalletResult.Success -> {
                        _uiState.value = _uiState.value.copy(
                            isConnecting = false,
                            isConnected = true
                        )
                        refreshServersState()
                        _events.emit(WalletEvent.Connected)
                        // Refresh fee estimates (server may support sub-sat fees)
                        fetchFeeEstimates()
                        // Auto-sync after connecting
                        sync()
                    }
                    result is WalletResult.Error -> {
                        _uiState.value = _uiState.value.copy(
                            isConnecting = false,
                            isConnected = false,
                            error = result.message
                        )
                        _events.emit(WalletEvent.Error(result.message))
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
        password: String?
    ) {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true, error = null)
                
                val walletMetadata = repository.getWalletMetadata(walletId)
                    ?: throw IllegalStateException("Wallet not found")
                val keyMaterial = repository.getKeyMaterial(walletId)
                    ?: throw IllegalStateException("Key material not found")
                
                // Build the wallet payload JSON
                val payloadJson = JSONObject().apply {
                    put("version", 1)
                    put("encrypted", password != null)
                    put("exportedAt", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date()))
                    
                    put("wallet", JSONObject().apply {
                        put("name", walletMetadata.name)
                        put("addressType", walletMetadata.addressType.name)
                        put("derivationPath", walletMetadata.derivationPath)
                        put("network", walletMetadata.network.name)
                        put("isWatchOnly", walletMetadata.isWatchOnly)
                        put("createdAt", walletMetadata.createdAt)
                    })
                    
                    put("keyMaterial", JSONObject().apply {
                        if (keyMaterial.mnemonic != null) put("mnemonic", keyMaterial.mnemonic)
                        if (keyMaterial.extendedPublicKey != null) put("extendedPublicKey", keyMaterial.extendedPublicKey)
                    })
                    
                    if (includeLabels) {
                        val addressLabels = repository.getAllAddressLabelsForWallet(walletId)
                        val txLabels = repository.getAllTransactionLabelsForWallet(walletId)
                        
                        put("labels", JSONObject().apply {
                            put("addresses", JSONObject().apply {
                                addressLabels.forEach { (addr, label) -> put(addr, label) }
                            })
                            put("transactions", JSONObject().apply {
                                txLabels.forEach { (txid, label) -> put(txid, label) }
                            })
                        })
                    }
                }
                
                val outputJson = if (password != null) {
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
    suspend fun parseBackupFile(uri: Uri, password: String?): JSONObject {
        return withContext(Dispatchers.IO) {
            val rawBytes = getApplication<Application>().contentResolver.openInputStream(uri)?.use {
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
        
        val addressType = try {
            AddressType.valueOf(walletObj.getString("addressType"))
        } catch (_: Exception) {
            AddressType.SEGWIT
        }
        
        val network = try {
            WalletNetwork.valueOf(walletObj.optString("network", "BITCOIN"))
        } catch (_: Exception) {
            WalletNetwork.BITCOIN
        }
        
        val mnemonic = keyMaterialObj.optString("mnemonic", null.toString()).let {
            if (it == "null" || it.isBlank()) null else it
        }
        val xpub = keyMaterialObj.optString("extendedPublicKey", null.toString()).let {
            if (it == "null" || it.isBlank()) null else it
        }
        
        val keyMaterial = mnemonic ?: xpub
            ?: throw IllegalStateException("No key material found in backup")
        
        val isWatchOnly = mnemonic == null
        
        val config = WalletImportConfig(
            name = walletObj.optString("name", "Restored Wallet"),
            keyMaterial = keyMaterial,
            addressType = addressType,
            customDerivationPath = walletObj.optString("derivationPath", null.toString()).let {
                if (it == "null" || it.isBlank()) null else it
            },
            network = network,
            isWatchOnly = isWatchOnly
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
        val ciphertext: ByteArray
    )
    
    private fun encryptData(plaintext: ByteArray, password: String): EncryptedPayload {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        
        val keySpec = PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, 256)
        val secretKey = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(keySpec)
        val aesKey = SecretKeySpec(secretKey.encoded, "AES")
        
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, aesKey, GCMParameterSpec(128, iv))
        val ciphertext = cipher.doFinal(plaintext)
        
        return EncryptedPayload(salt, iv, ciphertext)
    }
    
    private fun decryptData(payload: EncryptedPayload, password: String): ByteArray {
        val keySpec = PBEKeySpec(password.toCharArray(), payload.salt, PBKDF2_ITERATIONS, 256)
        val secretKey = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(keySpec)
        val aesKey = SecretKeySpec(secretKey.encoded, "AES")
        
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, aesKey, GCMParameterSpec(128, payload.iv))
        return cipher.doFinal(payload.ciphertext)
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
     * Clean up resources when ViewModel is cleared
     */
    override fun onCleared() {
        super.onCleared()
        repository.disconnect()
        torManager.stop()
    }
    
    /**
     * Check if currently connected via Tor bridge
     */
    fun isUsingTorBridge(): Boolean = repository.isUsingTorBridge()
    
    /**
     * Disconnect from the current Electrum server
     */
    fun disconnect() {
        repository.disconnect()
        _uiState.value = _uiState.value.copy(isConnected = false)
    }
    
    /**
     * Cancel an in-progress connection attempt
     */
    fun cancelConnection() {
        connectionJob?.cancel()
        connectionJob = null
        repository.disconnect()
        _uiState.value = _uiState.value.copy(isConnecting = false, isConnected = false, error = null)
    }
    
    // ==================== Display Settings ====================
    
    /**
     * Toggle privacy mode (hides all monetary amounts)
     */
    fun togglePrivacyMode() {
        _privacyMode.value = !_privacyMode.value
    }
    
    /**
     * Get the current denomination setting
     */
    fun getDenomination(): String = repository.getDenomination()
    
    /**
     * Set the denomination preference
     */
    fun setDenomination(denomination: String) {
        repository.setDenomination(denomination)
        _denominationState.value = denomination
    }
    
    /**
     * Check if using sats denomination
     */
    fun isUsingSats(): Boolean = repository.isUsingSats()
    
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
     * Check if the current mempool URL is an onion address
     */
    fun isMempoolOnionAddress(): Boolean = repository.isMempoolOnionAddress()
    
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
    
    // ==================== Fee Estimation Settings ====================
    
    /**
     * Get the current fee source setting
     */
    fun getFeeSource(): String = repository.getFeeSource()
    
    /**
     * Set the fee source preference
     */
    fun setFeeSource(source: String) {
        repository.setFeeSource(source)
        _feeSourceState.value = source
        
        if (source == repository.FEE_SOURCE_OFF) {
            _feeEstimationState.value = FeeEstimationResult.Disabled
        } else {
            // Immediately fetch when enabling a fee source
            fetchFeeEstimates()
        }
    }
    
    /**
     * Get the full fee source URL
     */
    fun getFeeSourceUrl(): String? = repository.getFeeSourceUrl()
    
    /**
     * Fetch fee estimates from the configured source
     * Call this when opening the Send screen
     * Uses precise endpoint only if the connected Electrum server supports sub-sat fees
     */
    fun fetchFeeEstimates() {
        val feeSourceUrl = repository.getFeeSourceUrl()
        
        if (feeSourceUrl == null) {
            _feeEstimationState.value = FeeEstimationResult.Disabled
            return
        }
        
        viewModelScope.launch {
            _feeEstimationState.value = FeeEstimationResult.Loading
            
            val useTorProxy = feeSourceUrl.contains(".onion")
            
            // If Tor is required but not ready, show error
            if (useTorProxy && !torManager.isReady()) {
                _feeEstimationState.value = FeeEstimationResult.Error("Tor is required but not connected")
                return@launch
            }
            
            // Only use precise fees if the connected server supports sub-sat fee rates
            val usePrecise = repository.supportsSubSatFees()
            
            val result = feeEstimationService.fetchFeeEstimates(feeSourceUrl, useTorProxy, usePrecise)
            
            _feeEstimationState.value = result.fold(
                onSuccess = { estimates -> FeeEstimationResult.Success(estimates) },
                onFailure = { error -> FeeEstimationResult.Error(error.message ?: "Unknown error") }
            )
        }
    }
    
    /**
     * Clear fee estimation state (e.g., when leaving Send screen)
     */
    fun clearFeeEstimates() {
        val feeSource = repository.getFeeSource()
        _feeEstimationState.value = if (feeSource == repository.FEE_SOURCE_OFF) {
            FeeEstimationResult.Disabled
        } else {
            FeeEstimationResult.Loading
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
            val price = when (source) {
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
     * Verify PIN code
     */
    fun verifyPin(pin: String): Boolean = repository.verifyPin(pin)
    
    /**
     * Check if PIN is set
     */
    fun hasPin(): Boolean = repository.hasPin()
    
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
     * Check if biometric is enabled
     */
    fun isBiometricEnabled(): Boolean = repository.isBiometricEnabled()
    
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
     * Get last background time
     */
    fun getLastBackgroundTime(): Long = repository.getLastBackgroundTime()
    
    /**
     * Set last background time
     */
    fun setLastBackgroundTime(time: Long) {
        repository.setLastBackgroundTime(time)
    }
    
    companion object {
        private const val CONNECTION_TIMEOUT_MS = 30_000L
        private const val PBKDF2_ITERATIONS = 210_000
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
    val error: String? = null
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
    data class WalletExported(val walletName: String) : WalletEvent()
    data object LabelsRestored : WalletEvent()
    data class Error(val message: String) : WalletEvent()
}

/**
 * State for Electrum servers
 */
data class ServersState(
    val servers: List<ElectrumConfig> = emptyList(),
    val activeServerId: String? = null
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
    val selectedUtxoOutpoints: List<String> = emptyList() // Store outpoints to restore selection
)
