package github.aeonbtc.ibiswallet.viewmodel

import android.app.Application
import android.net.Uri
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import github.aeonbtc.ibiswallet.R
import github.aeonbtc.ibiswallet.data.local.SecureStorage
import github.aeonbtc.ibiswallet.data.model.ArkAutoDbBackupInfo
import github.aeonbtc.ibiswallet.data.model.ArkEvent
import github.aeonbtc.ibiswallet.data.model.ArkReceiveKind
import github.aeonbtc.ibiswallet.data.model.Layer2Provider
import github.aeonbtc.ibiswallet.data.repository.ArkRepository
import github.aeonbtc.ibiswallet.data.repository.ArkUnilateralExitPolicy
import github.aeonbtc.ibiswallet.service.ConnectivityKeepAlivePolicy
import github.aeonbtc.ibiswallet.util.Bip329LabelNetwork
import github.aeonbtc.ibiswallet.util.Bip329LabelScope
import github.aeonbtc.ibiswallet.util.Bip329Labels
import github.aeonbtc.ibiswallet.util.SecureLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

class ArkViewModel(application: Application) : AndroidViewModel(application) {
    private val appContext = getApplication<Application>()
    private val secureStorage = SecureStorage.getInstance(application)
    private val repository = ArkRepository(application, secureStorage)

    val arkState = repository.arkState
    val sendState = repository.sendState
    val receiveState = repository.receiveState
    val transferState = repository.transferState
    val lifecycleState = repository.lifecycleState
    val events = repository.events
    val arkMovementLabels = repository.arkMovementLabels
    val arkAddressLabels = repository.arkAddressLabels
    val loadedWalletId = repository.loadedWalletId
    val isArkConnected = repository.isConnected
    val isArkConnecting = repository.isConnecting

    private val _isArkLayer2Enabled = MutableStateFlow(effectiveArkLayer2Enabled())
    val isArkLayer2Enabled: StateFlow<Boolean> = _isArkLayer2Enabled.asStateFlow()

    private val _autoDelegatedRefreshEnabled =
        MutableStateFlow(secureStorage.isArkAutoDelegatedRefreshEnabled())
    val autoDelegatedRefreshEnabled: StateFlow<Boolean> = _autoDelegatedRefreshEnabled.asStateFlow()

    private val _autoBoardEnabled =
        MutableStateFlow(secureStorage.isArkAutoBoardEnabled())
    val autoBoardEnabled: StateFlow<Boolean> = _autoBoardEnabled.asStateFlow()

    private val _autoDbBackupEnabled =
        MutableStateFlow(secureStorage.isArkAutoDbBackupEnabled())
    val autoDbBackupEnabled: StateFlow<Boolean> = _autoDbBackupEnabled.asStateFlow()

    private val _autoDbBackupFolderUri =
        MutableStateFlow(secureStorage.getArkAutoDbBackupFolderUri())
    val autoDbBackupFolderUri: StateFlow<String?> = _autoDbBackupFolderUri.asStateFlow()

    private val _autoDbBackupLastMs = MutableStateFlow(0L)
    val autoDbBackupLastMs: StateFlow<Long> = _autoDbBackupLastMs.asStateFlow()

    private val _latestAutoDbBackup = MutableStateFlow<ArkAutoDbBackupInfo?>(null)
    val latestAutoDbBackup: StateFlow<ArkAutoDbBackupInfo?> = _latestAutoDbBackup.asStateFlow()

    /** Bumps when backup protection state may have changed (export / folder / toggle). */
    private val _dbBackupProtectionRevision = MutableStateFlow(0L)
    val dbBackupProtectionRevision: StateFlow<Long> = _dbBackupProtectionRevision.asStateFlow()

    /**
     * Process-lifetime only: wallet IDs for which the Ark DB backup popup was dismissed.
     * Cleared on process death so the alert can show again on next app launch.
     */
    private val _backupAlertDismissedWalletIds = MutableStateFlow<Set<String>>(emptySet())
    val backupAlertDismissedWalletIds: StateFlow<Set<String>> =
        _backupAlertDismissedWalletIds.asStateFlow()

    private val _sendDraft = MutableStateFlow(SendScreenDraft())
    val sendDraft: StateFlow<SendScreenDraft> = _sendDraft.asStateFlow()

    private val _arkEnabledWallets = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val arkEnabledWallets: StateFlow<Map<String, Boolean>> = _arkEnabledWallets

    private val _arkEsploraAddress = MutableStateFlow(secureStorage.getArkEsploraAddress())
    val arkEsploraAddress: StateFlow<String> = _arkEsploraAddress.asStateFlow()

    /** Manual export / import / auto-backup restore in flight (UI busy indicator). */
    private val _dbTransferInProgress = MutableStateFlow<ArkDbTransferProgress?>(null)
    val dbTransferInProgress: StateFlow<ArkDbTransferProgress?> = _dbTransferInProgress.asStateFlow()

    private var walletLifecycleJob: Job? = null
    private var pendingWalletLoadId: String? = null
    private var heartbeatJob: Job? = null
    private var maintenanceJob: Job? = null
    private var isAppInBackground = false

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
                loadedWalletId.value != null && isArkConnected.value
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
                    isArkLayer2Enabled.value
                ) {
                    if (isArkConnected.value) {
                        startHeartbeat()
                        requestMaintenance()
                    } else {
                        loadedWalletId.value?.let { loadArkWallet(it) }
                    }
                }
            },
        )

    init {
        viewModelScope.launch {
            combine(isArkConnected, _isArkLayer2Enabled) { connected, enabled ->
                connected && enabled
            }
                .distinctUntilChanged()
                .collect { keepAliveConnected ->
                    syncForegroundConnectivityPolicy()
                    if (keepAliveConnected) {
                        startHeartbeat()
                        startMaintenanceLoop()
                    } else {
                        stopHeartbeat()
                        stopMaintenanceLoop()
                    }
                }
        }
        viewModelScope.launch {
            loadedWalletId.collect { id ->
                refreshAutoDbBackupMeta(id)
            }
        }
        viewModelScope.launch {
            events.collect { event ->
                when (event) {
                    is ArkEvent.ArkDbAutoBackedUp -> {
                        _latestAutoDbBackup.value = event.info
                        _autoDbBackupLastMs.value = event.info.timestampMs
                        bumpDbBackupProtectionRevision()
                    }
                    is ArkEvent.ArkDbExported -> bumpDbBackupProtectionRevision()
                    else -> Unit
                }
            }
        }
    }

    private fun bumpDbBackupProtectionRevision() {
        _dbBackupProtectionRevision.value = _dbBackupProtectionRevision.value + 1L
    }

    fun isDbBackupProtected(walletId: String?): Boolean {
        if (walletId.isNullOrBlank()) return false
        return secureStorage.isArkDbBackupProtected(walletId)
    }

    /** True when the backup-required popup was dismissed this process for [walletId]. */
    fun isBackupAlertDismissed(walletId: String?): Boolean {
        if (walletId.isNullOrBlank()) return false
        return walletId in _backupAlertDismissedWalletIds.value
    }

    fun dismissBackupAlert(walletId: String?) {
        if (walletId.isNullOrBlank()) return
        _backupAlertDismissedWalletIds.value = _backupAlertDismissedWalletIds.value + walletId
    }

    private fun refreshAutoDbBackupMeta(walletId: String?) {
        if (walletId.isNullOrBlank()) {
            _autoDbBackupLastMs.value = 0L
            _latestAutoDbBackup.value = null
            return
        }
        val info = repository.getLatestAutoDbBackupInfo(walletId)
        _latestAutoDbBackup.value = info
        val stored = secureStorage.getArkAutoDbBackupLastMs(walletId)
        _autoDbBackupLastMs.value = maxOf(info?.timestampMs ?: 0L, stored)
    }

    private fun isBackgroundKeepAliveActive(): Boolean =
        ConnectivityKeepAlivePolicy.isForegroundConnectivityEnabled()

    private fun syncForegroundConnectivityPolicy() {
        ConnectivityKeepAlivePolicy.updateForegroundConnectivityEnabled(
            context = appContext,
            enabled = secureStorage.isForegroundConnectivityEnabled(),
        )
        ConnectivityKeepAlivePolicy.updateArkState(
            context = appContext,
            connected = isArkConnected.value && isArkLayer2Enabled.value,
            usesTor = arkEsploraUsesTor(),
        )
    }

    private fun arkEsploraUsesTor(): Boolean {
        val url = _arkEsploraAddress.value.trim()
        if (url.isBlank()) return false
        return runCatching {
            java.net.URI(url).host?.endsWith(".onion", ignoreCase = true) == true
        }.getOrDefault(url.contains(".onion", ignoreCase = true))
    }

    private fun startHeartbeat() {
        if (isAppInBackground && !isBackgroundKeepAliveActive()) return
        if (heartbeatJob?.isActive == true) return
        heartbeatJob?.cancel()
        heartbeatJob =
            viewModelScope.launch {
                while (true) {
                    delay(HEARTBEAT_INTERVAL_MS)
                    if (!isArkConnected.value) break
                    if (isAppInBackground && !isBackgroundKeepAliveActive()) break
                    runCatching { repository.refreshState() }
                        .onFailure { error ->
                            if (error is CancellationException) throw error
                        }
                }
            }
    }

    private fun stopHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = null
    }

    private fun startMaintenanceLoop() {
        if (maintenanceJob?.isActive == true) return
        maintenanceJob?.cancel()
        maintenanceJob =
            viewModelScope.launch {
                while (true) {
                    delay(MAINTENANCE_INTERVAL_MS)
                    if (!isArkConnected.value) break
                    if (isAppInBackground && !isBackgroundKeepAliveActive()) break
                    runCatching { repository.maintenance() }
                        .onFailure { error ->
                            if (error is CancellationException) throw error
                            SecureLog.w(TAG, "Ark maintenance tick failed")
                        }
                }
            }
    }

    private fun stopMaintenanceLoop() {
        maintenanceJob?.cancel()
        maintenanceJob = null
    }

    private fun requestMaintenance() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { repository.maintenance() }
        }
    }

    fun loadArkWallet(walletId: String) {
        if (pendingWalletLoadId == walletId && walletLifecycleJob?.isActive == true) {
            return
        }
        // Already live for this wallet — avoid disconnect/reconnect churn.
        if (
            pendingWalletLoadId == null &&
            loadedWalletId.value == walletId &&
            isArkConnected.value &&
            walletLifecycleJob?.isActive != true
        ) {
            return
        }
        val visibleWalletId = pendingWalletLoadId ?: loadedWalletId.value ?: arkState.value.walletId
        if (visibleWalletId != walletId) {
            // Immediate retarget: never leave previous wallet balances on screen.
            repository.beginConnecting(walletId)
            resetArkUiState()
        }
        val previous = walletLifecycleJob
        val previousWalletId = pendingWalletLoadId ?: loadedWalletId.value
        pendingWalletLoadId = walletId
        walletLifecycleJob =
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    previous?.cancelAndJoinForWalletSwitch(previousWalletId, walletId)
                    if (pendingWalletLoadId != walletId) {
                        // Stale job after switch: do not load a superseded walletId.
                        return@launch
                    }
                    runCatching { repository.loadWallet(walletId) }
                        .onFailure { error ->
                            if (error is CancellationException) throw error
                            if (pendingWalletLoadId == walletId) {
                                repository.markLoadFailed(
                                    walletId = walletId,
                                    message = error.message?.takeIf { it.isNotBlank() } ?: "Ark wallet load failed",
                                )
                            }
                        }
                } finally {
                    if (walletLifecycleJob === coroutineContext[Job]) {
                        if (pendingWalletLoadId == walletId) pendingWalletLoadId = null
                        walletLifecycleJob = null
                    }
                }
            }
    }

    fun unloadArkWallet() {
        val previous = walletLifecycleJob
        pendingWalletLoadId = null
        repository.clearWalletDisplayState()
        resetArkUiState()
        walletLifecycleJob =
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    previous?.cancelAndJoinWithinSwitchTimeout()
                    // unload keeps SecureStorage snapshot on Balance (offline paint).
                    repository.unloadWallet()
                } finally {
                    if (walletLifecycleJob?.isActive != true) walletLifecycleJob = null
                }
            }
    }

    /** Blocks until Bark handles are released so full backup can zip a consistent DB. */
    suspend fun unloadArkWalletAndAwait() {
        val previous = walletLifecycleJob
        walletLifecycleJob = null
        pendingWalletLoadId = null
        repository.clearWalletDisplayState()
        resetArkUiState()
        withContext(Dispatchers.IO) {
            previous?.cancelAndJoinWithinSwitchTimeout()
            // unload keeps SecureStorage snapshot on Balance (offline paint).
            repository.unloadWallet()
        }
    }

    suspend fun deleteWalletData(walletId: String) {
        repository.deleteWalletData(walletId)
        _arkEnabledWallets.value =
            _arkEnabledWallets.value.toMutableMap().apply { remove(walletId) }
    }

    suspend fun prepareForFullWipe() {
        walletLifecycleJob?.cancel()
        walletLifecycleJob = null
        pendingWalletLoadId = null
        // Repository bounds the mutex wait and force-deletes files on timeout.
        repository.prepareForFullWipe()
    }

    /** Ark fund-risk snapshot for wallet-delete confirmation UI. */
    fun assessDeleteRisk(walletId: String): ArkRepository.ArkDeleteRisk =
        repository.assessDeleteRisk(walletId)

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            // Opt-in auto-board only (default off).
            if (secureStorage.isArkAutoBoardEnabled()) {
                repository.scheduleDeferredBoardAttempts()
            }
            val walletId = loadedWalletId.value ?: arkState.value.walletId
            if (!isArkConnected.value && !walletId.isNullOrBlank()) {
                loadArkWallet(walletId)
                return@launch
            }
            runCatching { repository.refreshState() }
                .onFailure { error ->
                    if (error is CancellationException) throw error
                }
        }
    }

    /** Wallet Management full sync, including Bark's seed-mailbox recovery report. */
    fun fullSyncMailboxRecovery(walletId: String) {
        val previous = walletLifecycleJob
        val previousWalletId = pendingWalletLoadId ?: loadedWalletId.value
        pendingWalletLoadId = walletId
        // Switching wallets mid-recovery must not keep the prior wallet's draft/send UI.
        if (loadedWalletId.value != null && loadedWalletId.value != walletId) {
            resetArkUiState()
        }
        walletLifecycleJob =
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    previous?.cancelAndJoinForWalletSwitch(previousWalletId, walletId)
                    runCatching { repository.loadWallet(walletId) }
                        .onFailure { error ->
                            if (error is CancellationException) throw error
                            // Only mark failed if this load is still the intended one.
                            if (pendingWalletLoadId == walletId) {
                                repository.markLoadFailed(
                                    walletId = walletId,
                                    message = error.message?.takeIf { it.isNotBlank() } ?: "Ark wallet load failed",
                                )
                            }
                            return@launch
                        }
                    if (pendingWalletLoadId != walletId) return@launch
                    runCatching { repository.runMailboxRecoveryFullSync() }
                        .onFailure { error ->
                            if (error is CancellationException) throw error
                            SecureLog.w(TAG, "Ark mailbox recovery full sync failed: ${error.message}")
                        }
                } finally {
                    if (walletLifecycleJob === coroutineContext[Job]) {
                        if (pendingWalletLoadId == walletId) pendingWalletLoadId = null
                        walletLifecycleJob = null
                    }
                }
            }
    }

    fun receive(
        kind: ArkReceiveKind,
        amountSats: Long? = null,
        description: String? = null,
        forceNew: Boolean = false,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.receive(kind, amountSats, description, forceNew)
        }
    }

    /** Instant Receive paint from prefs before Bark finishes opening. */
    fun primeReceiveFromCache(walletId: String?) {
        repository.primeReceiveFromCache(walletId)
    }

    /** Instant paint for a specific Receive tab (Ark / BTC) from prefs. */
    fun primeReceiveKindFromCache(
        walletId: String?,
        kind: ArkReceiveKind,
    ) {
        repository.primeReceiveKindFromCache(walletId, kind)
    }

    fun prepareSend(
        destination: String,
        amountSats: Long?,
        useAllFunds: Boolean = false,
        label: String? = null,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.prepareSend(destination, amountSats, useAllFunds, label)
        }
    }

    fun prepareSendMany(
        recipients: List<Pair<String, Long>>,
        label: String? = null,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.prepareSendMany(recipients, label)
        }
    }

    fun sendPrepared() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.sendPrepared()
        }
    }

    fun sendPreparedMany() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.sendPreparedMany()
        }
    }

    fun prepareBoard(amountSats: Long, boardAll: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.prepareBoard(amountSats, boardAll)
        }
    }

    fun markBoardFundingInProgress() {
        repository.markBoardFundingInProgress()
    }

    fun markBoardFundingFailed(message: String) {
        repository.markBoardFundingFailed(message)
    }

    /** L1 funding broadcast finished — complete transfer UI and defer Bark board. */
    fun completeLayer1Funding(fundingTxid: String) {
        repository.completeLayer1Funding(fundingTxid)
    }

    /** Retry boarding any BTC still on Bark's on-chain deposit wallet (auto-board path). */
    fun retryBoardPendingDeposits() {
        if (secureStorage.isArkAutoBoardEnabled()) {
            repository.scheduleDeferredBoardAttempts()
        }
    }

    fun setArkAutoBoardEnabled(enabled: Boolean) {
        secureStorage.setArkAutoBoardEnabled(enabled)
        _autoBoardEnabled.value = enabled
        if (enabled && isArkConnected.value) {
            viewModelScope.launch(Dispatchers.IO) {
                repository.scheduleDeferredBoardAttempts()
                runCatching { repository.refreshState() }
            }
        }
    }

    fun boardOnchainAll(onResult: (Result<Long>) -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = repository.boardOnchainAll()
            withContext(Dispatchers.Main) { onResult(result) }
        }
    }

    fun boardOnchainAmount(
        amountSats: Long,
        onResult: (Result<Long>) -> Unit = {},
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = repository.boardOnchainAmount(amountSats)
            withContext(Dispatchers.Main) { onResult(result) }
        }
    }

    /**
     * Sweep stuck Bark on-chain deposit (below ASP min board) to a Layer 1 Bitcoin address.
     */
    fun recoverOnchainDepositToLayer1(
        destinationAddress: String,
        feeRateSatPerVb: Long = 2L,
        onResult: (Result<String>) -> Unit = {},
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val result =
                runCatching {
                    repository.recoverOnchainDepositToLayer1(
                        destinationAddress = destinationAddress,
                        feeRateSatPerVb = feeRateSatPerVb,
                    )
                }.fold(
                    onSuccess = { it },
                    onFailure = { Result.failure(it) },
                )
            // Always deliver on Main so UI busy flags clear even if recover threw.
            withContext(Dispatchers.Main) {
                onResult(result)
            }
        }
    }

    fun prepareOffboard(
        destinationAddress: String,
        amountSats: Long?,
        offboardAll: Boolean,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.prepareOffboard(destinationAddress, amountSats, offboardAll)
        }
    }

    fun executeOffboard() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.executeOffboard()
        }
    }

    fun prepareRefresh(vtxoIds: List<String> = emptyList()) {
        // Sync paint of RefreshPreview (selected ids) — do not hop to IO first.
        repository.prepareRefresh(vtxoIds)
    }

    /** Lifecycle confirm — prefers ASP-delegated (same path as auto opt-in). */
    fun executeRefresh(delegated: Boolean = true) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.executeRefresh(delegated = delegated)
        }
    }

    /**
     * Balance one-tap: ASP-delegated refresh of due VTXOs (shared helper with auto refresh).
     * Non-delegated only as fallback inside the repository if delegated fails.
     */
    fun quickRefreshVtxos() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { repository.quickRefreshVtxos() }
                .onFailure { error ->
                    if (error is CancellationException) throw error
                }
        }
    }

    fun startUnilateralExit(
        vtxoIds: List<String> = emptyList(),
        entireWallet: Boolean = false,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.startUnilateralExit(vtxoIds = vtxoIds, entireWallet = entireWallet)
        }
    }

    fun progressUnilateralExits(
        feeRateSatPerVb: Long = ArkUnilateralExitPolicy.DEFAULT_EXIT_FEE_RATE_SAT_VB,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.progressUnilateralExits(feeRateSatPerVb)
        }
    }

    fun prepareClaimExits(
        destinationAddress: String,
        vtxoIds: List<String> = emptyList(),
        feeRateSatPerVb: Long = ArkUnilateralExitPolicy.DEFAULT_EXIT_FEE_RATE_SAT_VB,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.prepareClaimExits(
                destinationAddress = destinationAddress,
                vtxoIds = vtxoIds,
                feeRateSatPerVb = feeRateSatPerVb,
            )
        }
    }

    fun executeClaimExits(expectedPsbtBase64: String? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.executeClaimExits(expectedPsbtBase64 = expectedPsbtBase64)
        }
    }

    fun resetSendState() {
        repository.resetSendState()
    }

    fun resetReceiveState() {
        repository.resetReceiveState()
    }

    fun resetTransferState() {
        repository.resetTransferState()
    }

    fun resetLifecycleState() {
        repository.resetLifecycleState()
    }

    /** Export live Bark session zip to external [uri] (only durable offline copy path). */
    fun exportArkDbToUri(uri: Uri) {
        if (_dbTransferInProgress.value != null) return
        viewModelScope.launch(Dispatchers.IO) {
            val id = loadedWalletId.value ?: secureStorage.getActiveWalletId()
            if (id.isNullOrBlank()) {
                repository.emitArkEvent(
                    ArkEvent.ArkDbTransferFailed(appContext.getString(R.string.ark_error_wallet_not_loaded)),
                )
                return@launch
            }
            _dbTransferInProgress.value = ArkDbTransferProgress.EXPORTING
            try {
                // Zip while loaded — session dir is deleted on unload.
                val bytes = repository.exportWalletDataZipBytes(id)
                    ?: throw Exception(appContext.getString(R.string.ark_db_export_empty))
                appContext.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(bytes)
                    out.flush()
                } ?: throw Exception(appContext.getString(R.string.ark_db_export_failed))
                secureStorage.setArkManualDbBackupLastMs(id)
                repository.emitArkEvent(ArkEvent.ArkDbExported)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                SecureLog.w(TAG, "Ark DB export failed: ${e.message}")
                repository.emitArkEvent(
                    ArkEvent.ArkDbTransferFailed(
                        e.message?.takeIf { it.isNotBlank() }
                            ?: appContext.getString(R.string.ark_db_export_failed),
                    ),
                )
            } finally {
                _dbTransferInProgress.value = null
            }
        }
    }

    /** Import Bark DB zip from [uri] into the active wallet, then reload Ark. */
    fun importArkDbFromUri(uri: Uri) {
        if (_dbTransferInProgress.value != null) return
        viewModelScope.launch(Dispatchers.IO) {
            val id = loadedWalletId.value ?: secureStorage.getActiveWalletId()
            if (id.isNullOrBlank()) {
                repository.emitArkEvent(
                    ArkEvent.ArkDbTransferFailed(appContext.getString(R.string.ark_error_wallet_not_loaded)),
                )
                return@launch
            }
            _dbTransferInProgress.value = ArkDbTransferProgress.IMPORTING
            try {
                val previous = walletLifecycleJob
                pendingWalletLoadId = null
                previous?.cancelAndJoinWithinSwitchTimeout()
                walletLifecycleJob = null
                val bytes =
                    appContext.contentResolver.openInputStream(uri)?.use { input ->
                        input.readBytes()
                    } ?: throw Exception(appContext.getString(R.string.ark_db_import_failed))
                if (bytes.isEmpty()) {
                    throw Exception(appContext.getString(R.string.ark_db_import_empty))
                }
                repository.importWalletDataZipBytes(id, bytes)
                // File install is done — clear spinner before Bark reopen/ASP sync.
                _dbTransferInProgress.value = null
                repository.emitArkEvent(ArkEvent.ArkDbImported)
                loadArkWallet(id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                SecureLog.w(TAG, "Ark DB import failed: ${e.message}")
                repository.emitArkEvent(
                    ArkEvent.ArkDbTransferFailed(
                        e.message?.takeIf { it.isNotBlank() }
                            ?: appContext.getString(R.string.ark_db_import_failed),
                    ),
                )
            } finally {
                _dbTransferInProgress.value = null
            }
        }
    }

    fun setSendDraft(draft: SendScreenDraft) {
        _sendDraft.value = draft
    }

    private fun resetArkUiState() {
        repository.resetSendState()
        repository.resetReceiveState()
        repository.resetTransferState()
        repository.resetLifecycleState()
        _sendDraft.value = SendScreenDraft()
    }

    fun saveMovementLabel(
        walletId: String,
        movementId: Int,
        label: String,
    ) {
        repository.saveMovementLabel(walletId, movementId, label)
    }

    fun deleteArkMovementFromHistory(
        walletId: String,
        movementId: Int,
    ) {
        repository.deleteArkMovementFromHistory(walletId, movementId)
    }

    fun deleteAllArkMovementsFromHistory(walletId: String) {
        repository.deleteAllArkMovementsFromHistory(walletId)
    }

    fun saveAddressLabel(
        walletId: String,
        address: String,
        label: String,
    ) {
        repository.saveAddressLabel(walletId, address, label)
    }

    fun deleteAddressLabel(
        walletId: String,
        address: String,
    ) {
        repository.deleteAddressLabel(walletId, address)
    }

    fun getArkBip329LabelsContent(walletId: String): String =
        Bip329Labels.export(
            addressLabels = repository.getAllArkAddressLabels(walletId),
            // Ark has no chain txids for ARKOOR; BIP 329 `tx` refs are movement ids.
            transactionLabels = repository.getAllArkMovementLabels(walletId),
            network = Bip329LabelNetwork.ARK,
        )

    fun importArkBip329LabelsFromContent(
        walletId: String,
        content: String,
        defaultScope: Bip329LabelScope = Bip329LabelScope.ARK,
    ): Int {
        val result = Bip329Labels.import(content, defaultScope)
        repository.saveArkAddressLabels(walletId, result.arkAddressLabels)
        repository.saveArkMovementLabels(walletId, result.arkTransactionLabels)
        return result.totalArkLabelsImported
    }

    fun getArkLabelCounts(walletId: String): Pair<Int, Int> =
        Pair(
            repository.getAllArkAddressLabels(walletId).size,
            repository.getAllArkMovementLabels(walletId).size,
        )

    fun isArkEnabledForWallet(walletId: String): Boolean {
        if (!ARK_LAYER2_TOGGLE_ENABLED) return false
        // Prefer live storage (other L2s may have cleared ARK) over a stale map hit.
        val stored = secureStorage.isArkEnabledForWallet(walletId)
        val mapped = _arkEnabledWallets.value[walletId]
        return if (mapped != null && mapped != stored) {
            _arkEnabledWallets.value =
                _arkEnabledWallets.value.toMutableMap().apply { put(walletId, stored) }
            stored
        } else {
            mapped ?: stored
        }
    }

    fun setArkLayer2Enabled(enabled: Boolean) {
        if (enabled && !ARK_LAYER2_TOGGLE_ENABLED) return
        secureStorage.setArkLayer2Enabled(enabled)
        _isArkLayer2Enabled.value = enabled && ARK_LAYER2_TOGGLE_ENABLED
        if (!_isArkLayer2Enabled.value) {
            unloadArkWallet()
        }
    }

    fun setArkAutoDelegatedRefreshEnabled(enabled: Boolean) {
        secureStorage.setArkAutoDelegatedRefreshEnabled(enabled)
        _autoDelegatedRefreshEnabled.value = enabled
        if (enabled && isArkConnected.value) {
            // Retroactively pick up due VTXOs when user opts in.
            viewModelScope.launch(Dispatchers.IO) {
                runCatching { repository.refreshState() }
            }
        }
    }

    fun setArkAutoDbBackupEnabled(enabled: Boolean) {
        secureStorage.setArkAutoDbBackupEnabled(enabled)
        _autoDbBackupEnabled.value = enabled
        bumpDbBackupProtectionRevision()
        if (enabled && isArkConnected.value) {
            viewModelScope.launch(Dispatchers.IO) {
                runCatching { repository.refreshState() }
            }
        }
    }

    fun setArkAutoDbBackupFolderUri(uri: Uri?) {
        val previous = secureStorage.getArkAutoDbBackupFolderUri()
        if (!previous.isNullOrBlank() && previous != uri?.toString()) {
            runCatching {
                appContext.contentResolver.releasePersistableUriPermission(
                    previous.toUri(),
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
        }
        if (uri != null) {
            runCatching {
                appContext.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
            secureStorage.setArkAutoDbBackupFolderUri(uri.toString())
            _autoDbBackupFolderUri.value = uri.toString()
            if (isArkConnected.value) {
                viewModelScope.launch(Dispatchers.IO) {
                    runCatching { repository.refreshState() }
                }
            }
        } else {
            secureStorage.setArkAutoDbBackupFolderUri(null)
            _autoDbBackupFolderUri.value = null
        }
        refreshAutoDbBackupMeta(loadedWalletId.value ?: secureStorage.getActiveWalletId())
        bumpDbBackupProtectionRevision()
    }

    fun clearArkAutoDbBackupFolderUri() {
        setArkAutoDbBackupFolderUri(null)
    }

    fun restoreLatestAutoDbBackup() {
        if (_dbTransferInProgress.value != null) return
        viewModelScope.launch(Dispatchers.IO) {
            val id = loadedWalletId.value ?: secureStorage.getActiveWalletId()
            if (id.isNullOrBlank()) {
                repository.emitArkEvent(
                    ArkEvent.ArkDbTransferFailed(appContext.getString(R.string.ark_error_wallet_not_loaded)),
                )
                return@launch
            }
            _dbTransferInProgress.value = ArkDbTransferProgress.RESTORING
            try {
                val previous = walletLifecycleJob
                pendingWalletLoadId = null
                previous?.cancelAndJoinWithinSwitchTimeout()
                walletLifecycleJob = null
                repository.restoreLatestAutoDbBackup(id)
                _dbTransferInProgress.value = null
                repository.emitArkEvent(ArkEvent.ArkDbImported)
                loadArkWallet(id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                SecureLog.w(TAG, "Ark auto DB restore failed: ${e.message}")
                repository.emitArkEvent(
                    ArkEvent.ArkDbTransferFailed(
                        e.message?.takeIf { it.isNotBlank() }
                            ?: appContext.getString(R.string.ark_db_import_failed),
                    ),
                )
            } finally {
                _dbTransferInProgress.value = null
            }
        }
    }

    fun setArkEnabledForWallet(walletId: String, enabled: Boolean) {
        if (enabled && !ARK_LAYER2_TOGGLE_ENABLED) return
        if (enabled) {
            secureStorage.setArkLayer2Enabled(true)
            _isArkLayer2Enabled.value = true
            // Keep provider in sync so Layer2 routing loads Ark immediately.
            secureStorage.setLayer2ProviderForWallet(walletId, Layer2Provider.ARK)
        }
        secureStorage.setArkEnabledForWallet(walletId, enabled)
        _arkEnabledWallets.value =
            _arkEnabledWallets.value.toMutableMap().apply {
                put(walletId, enabled)
            }
        if (!enabled && loadedWalletId.value == walletId) {
            unloadArkWallet()
        } else if (enabled) {
            // Force full reopen path (cancel any half-started job) so empty Bark
            // dirs after seed restore always run ASP recover.
            loadArkWallet(walletId)
        }
    }

    fun getLayer2ProviderForWallet(walletId: String): Layer2Provider =
        secureStorage.getLayer2ProviderForWallet(walletId)

    fun setLayer2ProviderForWallet(walletId: String, provider: Layer2Provider) {
        if (provider == Layer2Provider.ARK && !ARK_LAYER2_TOGGLE_ENABLED) return
        secureStorage.setLayer2ProviderForWallet(walletId, provider)
        if (provider == Layer2Provider.ARK) {
            _isArkLayer2Enabled.value = true
            secureStorage.setArkLayer2Enabled(true)
            secureStorage.setArkEnabledForWallet(walletId, true)
        }
        _arkEnabledWallets.value =
            _arkEnabledWallets.value.toMutableMap().apply {
                put(walletId, provider == Layer2Provider.ARK)
            }
        if (provider == Layer2Provider.ARK) {
            loadArkWallet(walletId)
        }
    }

    fun getArkServerAddress(): String = secureStorage.getArkServerAddress()

    fun setArkServerAddress(address: String) {
        val normalized = github.aeonbtc.ibiswallet.util.ArkEndpointValidator.normalize(address)
        if (!github.aeonbtc.ibiswallet.util.ArkEndpointValidator.isValid(normalized)) return
        secureStorage.setArkServerAddress(normalized)
    }

    fun getArkEsploraAddress(): String = _arkEsploraAddress.value

    fun setArkEsploraAddress(address: String) {
        val normalized = github.aeonbtc.ibiswallet.util.ArkEndpointValidator.normalize(address)
        if (!github.aeonbtc.ibiswallet.util.ArkEndpointValidator.isValid(normalized)) return
        secureStorage.setArkEsploraAddress(normalized)
        _arkEsploraAddress.value = secureStorage.getArkEsploraAddress()
        syncForegroundConnectivityPolicy()
    }

    /** Persist Esplora URL and reload the active Ark wallet so Bark picks it up. */
    fun setArkEsploraAddressAndReload(address: String) {
        val normalized = github.aeonbtc.ibiswallet.util.ArkEndpointValidator.normalize(address)
        if (!github.aeonbtc.ibiswallet.util.ArkEndpointValidator.isValid(normalized)) return
        secureStorage.setArkEsploraAddress(normalized)
        _arkEsploraAddress.value = secureStorage.getArkEsploraAddress()
        syncForegroundConnectivityPolicy()
        val walletId = loadedWalletId.value ?: pendingWalletLoadId
        if (walletId != null && isArkLayer2Enabled.value) {
            loadArkWallet(walletId)
        }
    }

    /** True when the wallet has a BIP39 passphrase (Bark on-chain boarding unavailable). */
    fun hasBip39Passphrase(walletId: String?): Boolean {
        if (walletId.isNullOrBlank()) return false
        return !secureStorage.getPassphrase(walletId).isNullOrEmpty()
    }

    fun activeEsploraUrl(): String = repository.activeEsploraUrl()

    fun isEligible(walletId: String): Boolean = repository.isEligible(walletId)

    private fun effectiveArkLayer2Enabled(): Boolean =
        ARK_LAYER2_TOGGLE_ENABLED && secureStorage.isArkLayer2Enabled()

    fun reloadRestoredSettings() {
        val arkLayer2Enabled = effectiveArkLayer2Enabled()
        _isArkLayer2Enabled.value = arkLayer2Enabled
        _autoDelegatedRefreshEnabled.value = secureStorage.isArkAutoDelegatedRefreshEnabled()
        _autoBoardEnabled.value = secureStorage.isArkAutoBoardEnabled()
        _autoDbBackupEnabled.value = secureStorage.isArkAutoDbBackupEnabled()
        _autoDbBackupFolderUri.value = secureStorage.getArkAutoDbBackupFolderUri()
        refreshAutoDbBackupMeta(loadedWalletId.value ?: secureStorage.getActiveWalletId())
        _arkEsploraAddress.value = secureStorage.getArkEsploraAddress()
        _arkEnabledWallets.value =
            if (!ARK_LAYER2_TOGGLE_ENABLED) {
                emptyMap()
            } else {
                secureStorage.getWalletIds().associateWith { walletId ->
                    ArkRestoredSettings.isWalletArkEnabled(
                        storedArkEnabled = secureStorage.isArkEnabledForWallet(walletId),
                        provider = secureStorage.getLayer2ProviderForWallet(walletId),
                    )
                }
            }
        syncForegroundConnectivityPolicy()
        if (!arkLayer2Enabled) {
            unloadArkWallet()
        }
    }

    private suspend fun Job.cancelAndJoinWithinSwitchTimeout() {
        cancel()
        withTimeoutOrNull(ARK_WALLET_SWITCH_CANCEL_TIMEOUT_MS) {
            join()
        }
    }

    /**
     * Same-wallet reload/full-sync must wait for an in-flight [Wallet.open] so a 2s
     * timeout cannot leave a skeleton DB that later opens with skipRecovery.
     */
    private suspend fun Job.cancelAndJoinForWalletSwitch(
        previousWalletId: String?,
        nextWalletId: String,
    ) {
        if (previousWalletId == nextWalletId) {
            join()
        } else {
            cancelAndJoinWithinSwitchTimeout()
        }
    }

    override fun onCleared() {
        lifecycleCoordinator.dispose()
        stopHeartbeat()
        stopMaintenanceLoop()
        ConnectivityKeepAlivePolicy.updateArkState(
            context = appContext,
            connected = false,
            usesTor = false,
        )
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { repository.unloadWallet() }
        }
        super.onCleared()
    }

    companion object {
        private const val TAG = "ArkViewModel"
        private const val HEARTBEAT_INTERVAL_MS = 60_000L
        private const val MAINTENANCE_INTERVAL_MS = 5 * 60_000L
        private const val ARK_WALLET_SWITCH_CANCEL_TIMEOUT_MS = 2_000L

        const val ARK_LAYER2_TOGGLE_ENABLED = false
    }
}

enum class ArkDbTransferProgress {
    EXPORTING,
    IMPORTING,
    RESTORING,
}

internal object ArkRestoredSettings {
    fun isWalletArkEnabled(
        storedArkEnabled: Boolean,
        provider: Layer2Provider,
    ): Boolean = storedArkEnabled || provider == Layer2Provider.ARK
}
