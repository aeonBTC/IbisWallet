package github.aeonbtc.ibiswallet.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import github.aeonbtc.ibiswallet.data.local.SecureStorage
import github.aeonbtc.ibiswallet.data.model.Layer2Provider
import github.aeonbtc.ibiswallet.data.model.SparkEvent
import github.aeonbtc.ibiswallet.data.model.SparkOnchainFeeSpeed
import github.aeonbtc.ibiswallet.data.model.SparkReceiveKind
import github.aeonbtc.ibiswallet.data.model.SparkSendState
import github.aeonbtc.ibiswallet.data.repository.SPARK_NETWORK_RECONNECT_DEBOUNCE_MS
import github.aeonbtc.ibiswallet.data.repository.SparkReconnectDebouncer
import github.aeonbtc.ibiswallet.data.repository.SparkRepository
import github.aeonbtc.ibiswallet.service.ConnectivityKeepAlivePolicy
import github.aeonbtc.ibiswallet.util.Bip329LabelNetwork
import github.aeonbtc.ibiswallet.util.Bip329LabelScope
import github.aeonbtc.ibiswallet.util.Bip329Labels
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

class SparkViewModel(application: Application) : AndroidViewModel(application) {
    private val appContext = getApplication<Application>()
    private val secureStorage = SecureStorage.getInstance(application)
    private val repository = SparkRepository(application, secureStorage)

    val sparkState = repository.sparkState
    val sendState = repository.sendState
    val receiveState = repository.receiveState
    val events: SharedFlow<SparkEvent> = repository.events
    val sparkTransactionLabels = repository.sparkTransactionLabels
    val sparkAddressLabels = repository.sparkAddressLabels
    val loadedWalletId = repository.loadedWalletId
    val isSparkConnected = repository.isConnected
    val isSparkConnecting = repository.isConnecting

    private val _isSparkLayer2Enabled = MutableStateFlow(secureStorage.isSparkLayer2Enabled())
    val isSparkLayer2Enabled: StateFlow<Boolean> = _isSparkLayer2Enabled.asStateFlow()

    private val _sendDraft = MutableStateFlow(SendScreenDraft())
    val sendDraft: StateFlow<SendScreenDraft> = _sendDraft.asStateFlow()

    private val _sparkEnabledWallets = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val sparkEnabledWallets: StateFlow<Map<String, Boolean>> = _sparkEnabledWallets

    private var walletLifecycleJob: Job? = null
    private var pendingWalletLoadId: String? = null
    private var reconnectJob: Job? = null
    private var heartbeatJob: Job? = null
    private var isAppInBackground = false
    private val reconnectDebouncer = SparkReconnectDebouncer()
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
                loadedWalletId.value != null && isSparkConnected.value
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
                    isSparkLayer2Enabled.value
                ) {
                    if (isSparkConnected.value) {
                        startHeartbeat()
                    } else {
                        scheduleReconnect(force = true)
                    }
                }
            },
        )

    init {
        networkMonitor.start()
        viewModelScope.launch {
            combine(
                isSparkConnected,
                _isSparkLayer2Enabled,
            ) { connected, enabled ->
                connected && enabled
            }
                .distinctUntilChanged()
                .collect { keepAliveConnected ->
                    syncForegroundConnectivityPolicy()
                    if (keepAliveConnected) {
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
        ConnectivityKeepAlivePolicy.updateSparkState(
            context = appContext,
            connected = isSparkConnected.value && isSparkLayer2Enabled.value,
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
                    if (!isSparkConnected.value) break
                    if (isAppInBackground && !isBackgroundKeepAliveActive()) break

                    val alive =
                        withTimeoutOrNull(HEARTBEAT_PING_TIMEOUT_MS) {
                            runCatching { repository.refreshState() }.isSuccess &&
                                isSparkConnected.value
                        } ?: false

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

    private fun handleConnectionLost() {
        if (isAppInBackground && !isBackgroundKeepAliveActive()) return
        if (!isSparkConnected.value) return
        stopHeartbeat()
        scheduleReconnect(force = true)
    }

    fun loadSparkWallet(walletId: String) {
        if (pendingWalletLoadId == walletId && walletLifecycleJob?.isActive == true) {
            return
        }

        val visibleWalletId = pendingWalletLoadId ?: loadedWalletId.value
        if (visibleWalletId != null && visibleWalletId != walletId) {
            repository.clearWalletDisplayState()
            resetSparkUiState()
        }

        val previousLifecycleJob = walletLifecycleJob
        pendingWalletLoadId = walletId
        walletLifecycleJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                previousLifecycleJob?.cancelAndJoinWithinSwitchTimeout()
                runCatching { repository.loadWallet(walletId) }
                    .onFailure { error ->
                        if (error is CancellationException) throw error
                        repository.markLoadFailed(
                            walletId = walletId,
                            message = error.message?.takeIf { it.isNotBlank() } ?: "Spark wallet load failed",
                        )
                    }
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

    fun unloadSparkWallet() {
        val previousLifecycleJob = walletLifecycleJob
        pendingWalletLoadId = null
        repository.clearWalletDisplayState()
        resetSparkUiState()
        walletLifecycleJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                previousLifecycleJob?.cancelAndJoinWithinSwitchTimeout()
                repository.unloadWallet()
            } finally {
                if (walletLifecycleJob?.isActive != true) {
                    walletLifecycleJob = null
                }
            }
        }
    }

    suspend fun deleteWalletData(walletId: String) {
        repository.deleteWalletData(walletId)
        _sparkEnabledWallets.value = _sparkEnabledWallets.value.toMutableMap().apply { remove(walletId) }
    }

    suspend fun prepareForFullWipe() {
        walletLifecycleJob?.cancel()
        repository.unloadWallet()
    }

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { repository.refreshState() }
                .onFailure { error ->
                    if (error is CancellationException) throw error
                }
        }
    }

    fun scheduleReconnect(force: Boolean = false) {
        val walletLoaded = loadedWalletId.value != null
        if (!walletLoaded) return
        if (isAppInBackground && !isBackgroundKeepAliveActive() && !force) return
        val now = System.currentTimeMillis()
        if (!force && !reconnectDebouncer.recordScheduleRequest(now)) return
        if (!force) {
            reconnectJob?.cancel()
            reconnectJob =
                viewModelScope.launch(Dispatchers.IO) {
                    delay(SPARK_NETWORK_RECONNECT_DEBOUNCE_MS)
                    if (!reconnectDebouncer.shouldRunReconnect()) return@launch
                    if (isAppInBackground && !isBackgroundKeepAliveActive()) return@launch
                    runReconnectAttempt()
                }
            return
        }
        reconnectJob?.cancel()
        reconnectJob =
            viewModelScope.launch(Dispatchers.IO) {
                runReconnectAttempt()
            }
    }

    private suspend fun runReconnectAttempt() {
        if (loadedWalletId.value == null) return
        if (isAppInBackground && !isBackgroundKeepAliveActive()) return
        runCatching { repository.reconnectWallet() }
            .onFailure { error ->
                if (error is CancellationException) throw error
            }
        reconnectDebouncer.markReconnectExecuted()
    }

    fun syncWallet(walletId: String) {
        val visibleWalletId = pendingWalletLoadId ?: loadedWalletId.value
        if (visibleWalletId != null && visibleWalletId != walletId) {
            repository.clearWalletDisplayState()
            resetSparkUiState()
        }

        val previousLifecycleJob = walletLifecycleJob
        pendingWalletLoadId = walletId
        walletLifecycleJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                previousLifecycleJob?.cancelAndJoinWithinSwitchTimeout()
                runCatching {
                    if (loadedWalletId.value == walletId && isSparkConnected.value) {
                        repository.refreshState()
                    } else {
                        repository.loadWallet(walletId)
                    }
                }.onFailure { error ->
                    if (error is CancellationException) throw error
                    repository.markLoadFailed(
                        walletId = walletId,
                        message = error.message?.takeIf { it.isNotBlank() } ?: "Spark wallet sync failed",
                    )
                }
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

    fun reloadRestoredSettings() {
        val sparkLayer2Enabled = secureStorage.isSparkLayer2Enabled()
        _isSparkLayer2Enabled.value = sparkLayer2Enabled
        _sparkEnabledWallets.value =
            secureStorage.getWalletIds().associateWith { walletId ->
                SparkRestoredSettings.isWalletSparkEnabled(
                    storedSparkEnabled = secureStorage.isSparkEnabledForWallet(walletId),
                    provider = secureStorage.getLayer2ProviderForWallet(walletId),
                )
            }
        if (!sparkLayer2Enabled) {
            unloadSparkWallet()
        }
    }

    fun receive(
        kind: SparkReceiveKind,
        amountSats: Long? = null,
        description: String = "",
        forceNew: Boolean = false,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.receive(kind, amountSats, description, forceNew)
        }
    }

    fun prepareSend(
        paymentRequest: String,
        amountSats: Long?,
        onchainFeeSpeed: SparkOnchainFeeSpeed = SparkOnchainFeeSpeed.FAST,
        useAllFunds: Boolean = false,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.prepareSend(paymentRequest, amountSats, onchainFeeSpeed, useAllFunds)
        }
    }

    suspend fun prepareSendPreview(
        paymentRequest: String,
        amountSats: Long?,
        onchainFeeSpeed: SparkOnchainFeeSpeed = SparkOnchainFeeSpeed.FAST,
        useAllFunds: Boolean = false,
    ): SparkSendState.Preview =
        repository.prepareSendPreview(paymentRequest, amountSats, onchainFeeSpeed, useAllFunds)

    suspend fun getOnchainFeeQuotes(
        paymentRequest: String,
        amountSats: Long,
        useAllFunds: Boolean = false,
    ) = repository.getOnchainFeeQuotes(paymentRequest, amountSats, useAllFunds)

    suspend fun getRecommendedFeeEstimates() = repository.getRecommendedFeeEstimates()

    fun addLocalPendingDeposit(
        txid: String,
        amountSats: Long,
        address: String,
    ) {
        repository.addLocalPendingDeposit(txid, amountSats, address)
    }

    fun setSendDraft(draft: SendScreenDraft) {
        _sendDraft.value = draft
    }

    fun sendPrepared() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.sendPrepared()
        }
    }

    suspend fun sendPreparedNow(): String? = repository.sendPreparedNow()

    fun resetSendState() {
        repository.resetSendState()
    }

    fun resetReceiveState() {
        repository.resetReceiveState()
    }

    private fun resetSparkUiState() {
        repository.resetSendState()
        repository.resetReceiveState()
        _sendDraft.value = SendScreenDraft()
    }

    private suspend fun Job.cancelAndJoinWithinSwitchTimeout() {
        cancel()
        withTimeoutOrNull(SPARK_WALLET_SWITCH_CANCEL_TIMEOUT_MS) {
            join()
        }
    }

    fun getSparkBip329LabelsContent(walletId: String): String =
        Bip329Labels.export(
            addressLabels = repository.getAllSparkAddressLabels(walletId),
            transactionLabels = repository.getAllSparkTransactionLabels(walletId),
            network = Bip329LabelNetwork.SPARK,
        )

    fun importSparkBip329LabelsFromContent(
        walletId: String,
        content: String,
        defaultScope: Bip329LabelScope = Bip329LabelScope.SPARK,
    ): Int {
        val result = Bip329Labels.import(content, defaultScope)
        repository.saveSparkAddressLabels(walletId, result.sparkAddressLabels)
        repository.saveSparkTransactionLabels(walletId, result.sparkTransactionLabels)
        return result.totalSparkLabelsImported
    }

    fun getSparkLabelCounts(walletId: String): Pair<Int, Int> =
        Pair(
            repository.getAllSparkAddressLabels(walletId).size,
            repository.getAllSparkTransactionLabels(walletId).size,
        )

    fun saveSparkTransactionLabel(
        walletId: String,
        paymentId: String,
        label: String,
    ) {
        repository.saveSparkTransactionLabel(walletId, paymentId, label)
    }

    fun saveSparkAddressLabel(
        walletId: String,
        addressOrRequest: String,
        label: String,
    ) {
        repository.saveSparkAddressLabel(walletId, addressOrRequest, label)
    }

    fun deleteSparkAddressLabel(
        walletId: String,
        addressOrRequest: String,
    ) {
        repository.deleteSparkAddressLabel(walletId, addressOrRequest)
    }

    fun deleteSparkTransactionLabel(
        walletId: String,
        paymentId: String,
    ) {
        repository.deleteSparkTransactionLabel(walletId, paymentId)
    }

    fun deleteSparkHistoryItem(
        walletId: String,
        itemId: String,
    ) {
        repository.deleteSparkHistoryItem(walletId, itemId)
    }

    fun deleteAllSparkHistory(walletId: String) {
        repository.deleteAllSparkHistory(walletId)
    }

    fun isSparkEnabledForWallet(walletId: String): Boolean =
        _sparkEnabledWallets.value[walletId] ?: secureStorage.isSparkEnabledForWallet(walletId)

    fun setSparkLayer2Enabled(enabled: Boolean) {
        secureStorage.setSparkLayer2Enabled(enabled)
        _isSparkLayer2Enabled.value = enabled
        if (!enabled) {
            unloadSparkWallet()
        }
    }

    fun setSparkEnabledForWallet(walletId: String, enabled: Boolean) {
        if (enabled) {
            secureStorage.setSparkLayer2Enabled(true)
            _isSparkLayer2Enabled.value = true
        }
        secureStorage.setSparkEnabledForWallet(walletId, enabled)
        _sparkEnabledWallets.value = _sparkEnabledWallets.value.toMutableMap().apply {
            put(walletId, enabled)
        }
    }

    fun getLayer2ProviderForWallet(walletId: String): Layer2Provider =
        secureStorage.getLayer2ProviderForWallet(walletId)

    fun setLayer2ProviderForWallet(walletId: String, provider: Layer2Provider) {
        secureStorage.setLayer2ProviderForWallet(walletId, provider)
        if (provider == Layer2Provider.SPARK) {
            _isSparkLayer2Enabled.value = true
        }
        _sparkEnabledWallets.value = _sparkEnabledWallets.value.toMutableMap().apply {
            put(walletId, provider == Layer2Provider.SPARK)
        }
    }

    override fun onCleared() {
        lifecycleCoordinator.dispose()
        networkMonitor.stop()
        reconnectJob?.cancel()
        stopHeartbeat()
        ConnectivityKeepAlivePolicy.updateSparkState(
            context = appContext,
            connected = false,
        )
        super.onCleared()
    }

    companion object {
        private const val HEARTBEAT_INTERVAL_MS = 60_000L
        private const val HEARTBEAT_RETRY_INTERVAL_MS = 15_000L
        private const val HEARTBEAT_PING_TIMEOUT_MS = 15_000L
        private const val HEARTBEAT_MAX_FAILURES = 3
    }
}

internal object SparkRestoredSettings {
    fun isWalletSparkEnabled(
        storedSparkEnabled: Boolean,
        provider: Layer2Provider,
    ): Boolean = storedSparkEnabled || provider == Layer2Provider.SPARK
}

private const val SPARK_WALLET_SWITCH_CANCEL_TIMEOUT_MS = 2_000L
