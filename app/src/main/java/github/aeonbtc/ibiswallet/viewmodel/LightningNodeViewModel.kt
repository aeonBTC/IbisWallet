package github.aeonbtc.ibiswallet.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import github.aeonbtc.ibiswallet.data.local.SecureStorage
import github.aeonbtc.ibiswallet.data.model.Layer2Provider
import github.aeonbtc.ibiswallet.data.model.LightningNodeConfig
import github.aeonbtc.ibiswallet.data.model.LightningNodeConnectionTestPhase
import github.aeonbtc.ibiswallet.data.model.LightningNodeConnectionTestResult
import github.aeonbtc.ibiswallet.data.model.LightningNodeEvent
import github.aeonbtc.ibiswallet.data.model.LightningNodeReceiveState
import github.aeonbtc.ibiswallet.data.model.LightningNodeSendState
import github.aeonbtc.ibiswallet.data.model.UtxoInfo
import github.aeonbtc.ibiswallet.data.repository.LightningNodeRepository
import github.aeonbtc.ibiswallet.service.ConnectivityKeepAlivePolicy
import github.aeonbtc.ibiswallet.util.SparkNetworkMonitor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class LightningNodeViewModel(application: Application) : AndroidViewModel(application) {
    private val appContext = getApplication<Application>()
    private val secureStorage = SecureStorage.getInstance(application)
    private val repository = LightningNodeRepository(application, secureStorage)

    val walletState = repository.walletState
    val sendState = repository.sendState
    val onchainSendState = repository.onchainSendState
    val receiveState = repository.receiveState
    val events: SharedFlow<LightningNodeEvent> = repository.events
    val onchainState = repository.onchainState
    val channels = repository.channels
    val channelsLoading = repository.channelsLoading
    val channelsError = repository.channelsError
    val loadedWalletId = repository.loadedWalletId
    val isConnected = repository.isConnected
    val isConnecting = repository.isConnecting
    /** Min relay sat/vB from node bitcoind — use for LN L1 fee floor when connected. */
    val onchainMinFeeRate = repository.onchainMinFeeRate

    private var invoiceWatchJob: Job? = null

    private val _isLightningNodeLayer2Enabled =
        MutableStateFlow(secureStorage.isLightningNodeLayer2Enabled())
    val isLightningNodeLayer2Enabled: StateFlow<Boolean> = _isLightningNodeLayer2Enabled.asStateFlow()

    private val _sendDraft = MutableStateFlow(SendScreenDraft())
    val sendDraft: StateFlow<SendScreenDraft> = _sendDraft.asStateFlow()

    private val _preSelectedOnchainUtxo = MutableStateFlow<UtxoInfo?>(null)
    val preSelectedOnchainUtxo: StateFlow<UtxoInfo?> = _preSelectedOnchainUtxo.asStateFlow()

    private val _lightningEnabledWallets = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val lightningEnabledWallets: StateFlow<Map<String, Boolean>> = _lightningEnabledWallets

    private val _connectionTestResult =
        MutableStateFlow<LightningNodeConnectionTestResult?>(null)
    val connectionTestResult: StateFlow<LightningNodeConnectionTestResult?> =
        _connectionTestResult.asStateFlow()

    private val _isTestingConnection = MutableStateFlow(false)
    val isTestingConnection: StateFlow<Boolean> = _isTestingConnection.asStateFlow()

    private val _connectionTestPhase =
        MutableStateFlow(LightningNodeConnectionTestPhase.IDLE)
    val connectionTestPhase: StateFlow<LightningNodeConnectionTestPhase> =
        _connectionTestPhase.asStateFlow()

    /** Saved public Lightning Address for share/QR on the LN receive tab. */
    private val _lightningAddress = MutableStateFlow("")
    val lightningAddress: StateFlow<String> = _lightningAddress.asStateFlow()

    /**
     * Bumped whenever a non-secret Lightning Node connection config is saved or cleared
     * so wallet list cards recompute Server/Port/Mode lines immediately.
     */
    private val _configRevision = MutableStateFlow(0)
    val configRevision: StateFlow<Int> = _configRevision.asStateFlow()

    private var walletLifecycleJob: Job? = null
    private var pendingWalletLoadId: String? = null
    /** Bumps on every load/unload so superseding switch work never applies stale outcomes. */
    private var lifecycleSerial: Long = 0L
    private var reconnectJob: Job? = null
    private var heartbeatJob: Job? = null
    private var isAppInBackground = false

    private val networkMonitor =
        SparkNetworkMonitor(
            context = getApplication(),
            onNetworkChanged = {
                if (!isAppInBackground || isBackgroundKeepAliveActive()) {
                    scheduleReconnect()
                }
            },
        )

    private val lifecycleCoordinator =
        AppLifecycleCoordinator(
            scope = viewModelScope,
            onBackgrounded = {
                isAppInBackground = true
                ConnectivityKeepAlivePolicy.updateAppForegroundState(
                    context = appContext,
                    isInForeground = false,
                )
                if (!isBackgroundKeepAliveActive()) {
                    stopHeartbeat()
                }
                loadedWalletId.value != null && isConnected.value
            },
            onForegrounded = { wasConnectedBeforeBackground, _ ->
                isAppInBackground = false
                ConnectivityKeepAlivePolicy.updateAppForegroundState(
                    context = appContext,
                    isInForeground = true,
                )
                if (
                    wasConnectedBeforeBackground &&
                    loadedWalletId.value != null &&
                    isLightningNodeLayer2Enabled.value
                ) {
                    if (isConnected.value) {
                        // Immediate pull so txs that arrived while backgrounded show up fast.
                        refresh()
                        startHeartbeat()
                    } else {
                        scheduleReconnect(force = true)
                    }
                }
            },
        )

    init {
        networkMonitor.start()
        reloadRestoredSettings()
        viewModelScope.launch {
            combine(
                isConnected,
                walletState,
                _isLightningNodeLayer2Enabled,
            ) { connected, state, enabled ->
                Triple(connected, state.useTor, enabled)
            }
                .distinctUntilChanged()
                .collect { (connected, _, enabled) ->
                    syncForegroundConnectivityPolicy()
                    if (connected && enabled) {
                        startHeartbeat()
                    } else {
                        stopHeartbeat()
                    }
                }
        }
    }

    private fun isBackgroundKeepAliveActive(): Boolean =
        ConnectivityKeepAlivePolicy.isForegroundConnectivityEnabled()

    private fun syncForegroundConnectivityPolicy() {
        ConnectivityKeepAlivePolicy.updateForegroundConnectivityEnabled(
            context = appContext,
            enabled = secureStorage.isForegroundConnectivityEnabled(),
        )
        ConnectivityKeepAlivePolicy.updateLightningState(
            context = appContext,
            connected = isConnected.value && isLightningNodeLayer2Enabled.value,
            usesTor = repository.currentConfigUsesTor(),
        )
    }

    private fun startHeartbeat() {
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
                    if (!isConnected.value) break
                    if (isAppInBackground && !isBackgroundKeepAliveActive()) break

                    // Full poll: balance + LN payments + L1 (when available). Remote
                    // nodes have no Electrum-style push; this is how incoming txs land in UI.
                    val alive =
                        withTimeoutOrNull(HEARTBEAT_REFRESH_TIMEOUT_MS) {
                            runCatching { repository.refreshState() }.isSuccess &&
                                isConnected.value
                        } ?: runCatching { repository.pingConnection() }.getOrDefault(false)

                    if (alive) {
                        consecutiveFailures = 0
                    } else {
                        consecutiveFailures++
                        if (consecutiveFailures >= HEARTBEAT_MAX_FAILURES) {
                            handleConnectionLost()
                            break
                        }
                    }
                }
            }
    }

    private fun stopHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = null
    }

    private suspend fun handleConnectionLost() {
        if (isAppInBackground && !isBackgroundKeepAliveActive()) return
        if (!isConnected.value) return
        stopHeartbeat()
        scheduleReconnect(force = true)
    }

    fun loadLightningWallet(walletId: String) {
        if (pendingWalletLoadId == walletId && walletLifecycleJob?.isActive == true) return
        // Already fully loaded and live — just refresh (avoid disconnect/reconnect churn).
        if (
            pendingWalletLoadId == null &&
            loadedWalletId.value == walletId &&
            isConnected.value &&
            walletLifecycleJob?.isActive != true
        ) {
            refresh()
            return
        }

        val serial = ++lifecycleSerial
        reconnectJob?.cancel()
        reconnectJob = null
        stopHeartbeat()

        val visibleWalletId = pendingWalletLoadId ?: loadedWalletId.value
        if (visibleWalletId != null && visibleWalletId != walletId) {
            repository.clearWalletDisplayState()
            resetUiState()
        }

        val previousLifecycleJob = walletLifecycleJob
        pendingWalletLoadId = walletId
        _lightningAddress.value = secureStorage.getLightningNodeLightningAddress(walletId)
        // Eager UI so switches into LN don't look idle while the previous job is cancelling.
        repository.beginConnecting(walletId)
        walletLifecycleJob =
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    previousLifecycleJob?.cancelAndJoinWithinSwitchTimeout()
                    if (serial != lifecycleSerial || pendingWalletLoadId != walletId) return@launch
                    runCatching { repository.loadWallet(walletId) }
                        .onFailure { error ->
                            if (error is CancellationException) throw error
                            if (serial != lifecycleSerial || pendingWalletLoadId != walletId) return@launch
                            repository.markLoadFailed(
                                walletId = walletId,
                                message = error.message?.takeIf { it.isNotBlank() }
                                    ?: "Lightning Node load failed",
                            )
                        }
                } finally {
                    if (pendingWalletLoadId == walletId && serial == lifecycleSerial) {
                        pendingWalletLoadId = null
                    }
                    if (walletLifecycleJob?.isActive != true) walletLifecycleJob = null
                }
            }
    }

    fun unloadLightningWallet() {
        val serial = ++lifecycleSerial
        reconnectJob?.cancel()
        reconnectJob = null
        stopHeartbeat()

        val previousLifecycleJob = walletLifecycleJob
        pendingWalletLoadId = null
        repository.clearWalletDisplayState()
        resetUiState()
        _lightningAddress.value = ""
        walletLifecycleJob =
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    previousLifecycleJob?.cancelAndJoinWithinSwitchTimeout()
                    // A newer load superseded this unload — leave that load alone.
                    if (serial != lifecycleSerial) return@launch
                    repository.unloadWallet()
                } finally {
                    if (walletLifecycleJob?.isActive != true) walletLifecycleJob = null
                }
            }
    }

    /**
     * User-facing disconnect / cancel: stop the session but keep saved node config
     * so Connect can re-establish without reconfiguration.
     */
    fun disconnectLightningWallet() {
        val serial = ++lifecycleSerial
        reconnectJob?.cancel()
        reconnectJob = null
        stopHeartbeat()

        val walletId = pendingWalletLoadId ?: loadedWalletId.value
        val previousLifecycleJob = walletLifecycleJob
        pendingWalletLoadId = null
        resetUiState()
        if (walletId != null) {
            // Eager offline identity so Connect stays available while teardown finishes.
            repository.applyDisconnectedConfigState(walletId)
            _lightningAddress.value = secureStorage.getLightningNodeLightningAddress(walletId)
        } else {
            repository.clearWalletDisplayState()
            _lightningAddress.value = ""
        }
        walletLifecycleJob =
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    previousLifecycleJob?.cancelAndJoinWithinSwitchTimeout()
                    if (serial != lifecycleSerial) return@launch
                    repository.disconnectSession(walletId)
                } finally {
                    if (walletLifecycleJob?.isActive != true) walletLifecycleJob = null
                }
            }
    }

    suspend fun deleteWalletData(walletId: String) {
        repository.deleteWalletData(walletId)
        secureStorage.clearLightningNodeLightningAddress(walletId)
        if (loadedWalletId.value == null || loadedWalletId.value == walletId) {
            _lightningAddress.value = ""
        }
        _lightningEnabledWallets.value =
            _lightningEnabledWallets.value.toMutableMap().apply { remove(walletId) }
        notifyConfigChanged()
    }

    fun getLightningAddress(walletId: String): String =
        secureStorage.getLightningNodeLightningAddress(walletId)

    fun setLightningAddress(
        walletId: String,
        address: String,
    ): Boolean {
        val trimmed = address.trim()
        if (trimmed.isBlank()) {
            clearLightningAddress(walletId)
            return true
        }
        val normalized = normalizeLightningAddress(trimmed) ?: return false
        secureStorage.setLightningNodeLightningAddress(walletId, normalized)
        if (loadedWalletId.value == walletId || pendingWalletLoadId == walletId) {
            _lightningAddress.value = normalized
        }
        return true
    }

    fun clearLightningAddress(walletId: String) {
        secureStorage.clearLightningNodeLightningAddress(walletId)
        if (loadedWalletId.value == walletId || pendingWalletLoadId == walletId) {
            _lightningAddress.value = ""
        }
    }

    suspend fun prepareForFullWipe() {
        walletLifecycleJob?.cancel()
        repository.unloadWallet()
    }

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { repository.refreshState() }
                .onFailure { if (it is CancellationException) throw it }
        }
    }

    fun refreshChannels() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { repository.refreshChannels() }
                .onFailure { if (it is CancellationException) throw it }
        }
    }

    fun scheduleReconnect(force: Boolean = false) {
        val walletId = loadedWalletId.value ?: return
        // Don't fight an in-flight wallet switch / load for a different id.
        if (pendingWalletLoadId != null && pendingWalletLoadId != walletId) return
        if (isAppInBackground && !isBackgroundKeepAliveActive() && !force) return
        val serialAtSchedule = lifecycleSerial
        reconnectJob?.cancel()
        reconnectJob =
            viewModelScope.launch(Dispatchers.IO) {
                if (!force) delay(1_500L)
                if (serialAtSchedule != lifecycleSerial) return@launch
                if (loadedWalletId.value != walletId) return@launch
                if (pendingWalletLoadId != null && pendingWalletLoadId != walletId) return@launch
                if (isAppInBackground && !isBackgroundKeepAliveActive()) return@launch
                runCatching { repository.reconnectWallet() }
                    .onFailure { if (it is CancellationException) throw it }
            }
    }

    fun reloadRestoredSettings() {
        val enabled = secureStorage.isLightningNodeLayer2Enabled()
        _isLightningNodeLayer2Enabled.value = enabled
        _lightningEnabledWallets.value =
            secureStorage.getWalletIds().associateWith { walletId ->
                LightningNodeRestoredSettings.isWalletLightningEnabled(
                    storedEnabled = secureStorage.isLightningNodeEnabledForWallet(walletId),
                    provider = secureStorage.getLayer2ProviderForWallet(walletId),
                )
            }
        if (!enabled) {
            unloadLightningWallet()
        }
    }

    fun createInvoice(
        amountSats: Long? = null,
        description: String = "",
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.createInvoice(amountSats, description.ifBlank { null })
            if (receiveState.value is LightningNodeReceiveState.Ready) {
                startOpenInvoiceWatch()
            }
        }
    }

    fun prepareSend(
        paymentRequest: String,
        amountSats: Long? = null,
        maxFeePercent: Double? = null,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.prepareSend(paymentRequest, amountSats, maxFeePercent)
        }
    }

    fun updatePreparedMaxFeePercent(maxFeePercent: Double) {
        repository.updatePreparedMaxFeePercent(maxFeePercent)
    }

    fun sendPrepared() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.sendPrepared()
        }
    }

    fun setSendDraft(draft: SendScreenDraft) {
        _sendDraft.value = draft
    }

    fun resetSendState() {
        repository.resetSendState()
    }

    fun resetOnchainSendState() {
        repository.resetOnchainSendState()
    }

    fun resetReceiveState() {
        stopOpenInvoiceWatch()
        repository.resetReceiveState()
    }

    private fun startOpenInvoiceWatch() {
        if (invoiceWatchJob?.isActive == true) return
        invoiceWatchJob =
            viewModelScope.launch(Dispatchers.IO) {
                while (receiveState.value is LightningNodeReceiveState.Ready) {
                    runCatching { repository.pollOpenInvoicePayment() }
                    if (receiveState.value !is LightningNodeReceiveState.Ready) break
                    delay(OPEN_INVOICE_POLL_INTERVAL_MS)
                }
            }
    }

    private fun stopOpenInvoiceWatch() {
        invoiceWatchJob?.cancel()
        invoiceWatchJob = null
    }

    fun generateOnchainAddress() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.generateOnchainAddress()
        }
    }

    fun sendOnchain(
        address: String,
        amountSats: Long?,
        satPerVbyte: Double?,
        sendAll: Boolean,
        label: String?,
        selectedOutpoints: List<UtxoInfo>?,
        spendUnconfirmed: Boolean = true,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                repository.sendOnchain(
                    address = address,
                    amountSats = amountSats,
                    satPerVbyte = satPerVbyte,
                    sendAll = sendAll,
                    label = label,
                    spendUnconfirmed = spendUnconfirmed,
                    selectedOutpoints = selectedOutpoints,
                )
            }
        }
    }

    fun sendOnchainMany(
        addrToAmountSats: Map<String, Long>,
        satPerVbyte: Double?,
        label: String?,
        selectedOutpoints: List<UtxoInfo>?,
        spendUnconfirmed: Boolean = true,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                repository.sendOnchainMany(
                    addrToAmountSats = addrToAmountSats,
                    satPerVbyte = satPerVbyte,
                    label = label,
                    spendUnconfirmed = spendUnconfirmed,
                    selectedOutpoints = selectedOutpoints,
                )
            }
        }
    }

    fun canBumpOnchainFee(txid: String, confirmations: Int): Boolean =
        repository.canBumpOnchainFee(txid, confirmations)

    fun bumpOnchainFee(
        parentTxid: String,
        satPerVbyte: Double,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                repository.bumpOnchainFee(
                    parentTxid = parentTxid,
                    satPerVbyte = satPerVbyte.takeIf { it > 0.0 },
                )
            }
        }
    }

    fun setPreSelectedOnchainUtxo(utxo: UtxoInfo?) {
        _preSelectedOnchainUtxo.value = utxo
    }

    fun clearPreSelectedOnchainUtxo() {
        _preSelectedOnchainUtxo.value = null
    }

    fun getConfig(walletId: String): LightningNodeConfig = repository.getConfig(walletId)

    fun saveConfig(
        walletId: String,
        config: LightningNodeConfig,
    ) {
        repository.saveConfig(walletId, config)
        notifyConfigChanged()
        if (loadedWalletId.value == walletId) {
            scheduleReconnect(force = true)
        }
    }

    /** Notify UI that non-secret connection endpoint data changed (wallet list cards). */
    fun notifyConfigChanged() {
        _configRevision.value = _configRevision.value + 1
    }

    fun testConnection(config: LightningNodeConfig) {
        viewModelScope.launch(Dispatchers.IO) {
            _isTestingConnection.value = true
            _connectionTestResult.value = null
            _connectionTestPhase.value = LightningNodeConnectionTestPhase.PREPARING
            try {
                _connectionTestResult.value =
                    repository.testConnection(config) { phase ->
                        _connectionTestPhase.value = phase
                    }
            } catch (e: CancellationException) {
                // Don't show "cancelled" when the ViewModel is cleared; surface timeouts
                // as failures instead (aliases of TimeoutCancellationException).
                if (e is kotlinx.coroutines.TimeoutCancellationException) {
                    _connectionTestResult.value =
                        LightningNodeConnectionTestResult.Failure(
                            e.message?.takeIf { it.isNotBlank() } ?: "Connection timed out",
                        )
                } else {
                    throw e
                }
            } catch (e: Exception) {
                _connectionTestResult.value =
                    LightningNodeConnectionTestResult.Failure(
                        e.message?.takeIf { it.isNotBlank() } ?: "Connection failed",
                    )
            } finally {
                _isTestingConnection.value = false
                _connectionTestPhase.value = LightningNodeConnectionTestPhase.IDLE
            }
        }
    }

    fun clearConnectionTestResult() {
        _connectionTestResult.value = null
        _connectionTestPhase.value = LightningNodeConnectionTestPhase.IDLE
    }

    fun isLightningNodeEnabledForWallet(walletId: String): Boolean =
        _lightningEnabledWallets.value[walletId]
            ?: secureStorage.isLightningNodeEnabledForWallet(walletId)

    fun setLightningNodeLayer2Enabled(enabled: Boolean) {
        secureStorage.setLightningNodeLayer2Enabled(enabled)
        _isLightningNodeLayer2Enabled.value = enabled
        if (!enabled) {
            unloadLightningWallet()
        }
    }

    fun setLightningNodeEnabledForWallet(
        walletId: String,
        enabled: Boolean,
    ) {
        if (enabled) {
            secureStorage.setLightningNodeLayer2Enabled(true)
            _isLightningNodeLayer2Enabled.value = true
        }
        secureStorage.setLightningNodeEnabledForWallet(walletId, enabled)
        _lightningEnabledWallets.value =
            _lightningEnabledWallets.value.toMutableMap().apply {
                put(walletId, enabled)
            }
    }

    fun getLayer2ProviderForWallet(walletId: String): Layer2Provider =
        secureStorage.getLayer2ProviderForWallet(walletId)

    fun setLayer2ProviderForWallet(
        walletId: String,
        provider: Layer2Provider,
    ) {
        secureStorage.setLayer2ProviderForWallet(walletId, provider)
        if (provider == Layer2Provider.LIGHTNING) {
            _isLightningNodeLayer2Enabled.value = true
        }
        _lightningEnabledWallets.value =
            _lightningEnabledWallets.value.toMutableMap().apply {
                put(walletId, provider == Layer2Provider.LIGHTNING)
            }
    }

    private fun resetUiState() {
        stopOpenInvoiceWatch()
        repository.resetSendState()
        repository.resetReceiveState()
        _sendDraft.value = SendScreenDraft()
        _connectionTestResult.value = null
    }

    private suspend fun Job.cancelAndJoinWithinSwitchTimeout() {
        cancel()
        // Tor+REST teardown can exceed 2s; allow enough time so the next switch doesn't
        // start while a previous unload still holds the repository mutex.
        withTimeoutOrNull(WALLET_SWITCH_CANCEL_TIMEOUT_MS) { join() }
    }

    override fun onCleared() {
        lifecycleCoordinator.dispose()
        networkMonitor.stop()
        reconnectJob?.cancel()
        stopOpenInvoiceWatch()
        stopHeartbeat()
        ConnectivityKeepAlivePolicy.updateLightningState(
            context = appContext,
            connected = false,
            usesTor = false,
        )
        super.onCleared()
    }

    companion object {
        /** Poll cadence while healthy — pulls balance/history so incoming L1/L2 land in UI. */
        private const val HEARTBEAT_INTERVAL_MS = 30_000L
        private const val HEARTBEAT_RETRY_INTERVAL_MS = 15_000L
        /** Full refresh includes balance + payments + on-chain lists (Tor nodes need headroom). */
        private const val HEARTBEAT_REFRESH_TIMEOUT_MS = 30_000L
        private const val HEARTBEAT_MAX_FAILURES = 3
        private const val WALLET_SWITCH_CANCEL_TIMEOUT_MS = 8_000L
        /** Faster listPayments poll while a receive invoice is open. */
        private const val OPEN_INVOICE_POLL_INTERVAL_MS = 3_000L

        /**
         * Accepts LUD-16 style `user@domain.tld` (optional `lightning:` prefix).
         * Returns normalized form or null if invalid.
         */
        fun normalizeLightningAddress(raw: String): String? {
            val trimmed =
                raw
                    .trim()
                    .removePrefix("lightning:")
                    .removePrefix("LIGHTNING:")
                    .trim()
            if (trimmed.isBlank()) return null
            if (trimmed.contains("://") || trimmed.contains(" ")) return null
            val parts = trimmed.split("@", limit = 3)
            if (parts.size != 2) return null
            val user = parts[0].trim()
            val domain = parts[1].trim().lowercase()
            if (user.isBlank() || domain.isBlank() || !domain.contains('.')) return null
            if (user.any { it.isWhitespace() } || domain.any { it.isWhitespace() }) return null
            return "$user@$domain"
        }
    }
}

internal object LightningNodeRestoredSettings {
    fun isWalletLightningEnabled(
        storedEnabled: Boolean,
        provider: Layer2Provider,
    ): Boolean = storedEnabled || provider == Layer2Provider.LIGHTNING
}
