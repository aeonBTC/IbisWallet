package github.aeonbtc.ibiswallet.data.repository

import android.app.Application
import github.aeonbtc.ibiswallet.data.lightning.ClnRestClient
import github.aeonbtc.ibiswallet.data.lightning.LightningNodeBackend
import github.aeonbtc.ibiswallet.data.lightning.LndRestClient
import github.aeonbtc.ibiswallet.data.lightning.NwcClient
import github.aeonbtc.ibiswallet.data.local.SecureStorage
import github.aeonbtc.ibiswallet.data.model.DecodedLightningNodeInvoice
import github.aeonbtc.ibiswallet.data.model.KeychainType
import github.aeonbtc.ibiswallet.data.model.LightningNodeChannel
import github.aeonbtc.ibiswallet.data.model.LightningNodeConfig
import github.aeonbtc.ibiswallet.data.model.LightningNodeConnectionTestPhase
import github.aeonbtc.ibiswallet.data.model.LightningNodeConnectionTestResult
import github.aeonbtc.ibiswallet.data.model.LightningNodeConnectionType
import github.aeonbtc.ibiswallet.data.model.LightningNodeEvent
import github.aeonbtc.ibiswallet.data.model.LightningNodeOnchainSendState
import github.aeonbtc.ibiswallet.data.model.LightningNodeOnchainState
import github.aeonbtc.ibiswallet.data.model.LightningNodeOnchainTransaction
import github.aeonbtc.ibiswallet.data.model.LightningNodePayment
import github.aeonbtc.ibiswallet.data.model.LightningNodePaymentDirection
import github.aeonbtc.ibiswallet.data.model.LightningNodePaymentStatus
import github.aeonbtc.ibiswallet.data.model.LightningNodeReceiveState
import github.aeonbtc.ibiswallet.data.model.LightningNodeSendState
import github.aeonbtc.ibiswallet.data.model.LightningNodeWalletState
import github.aeonbtc.ibiswallet.data.model.UtxoInfo
import github.aeonbtc.ibiswallet.data.model.WalletAddress
import github.aeonbtc.ibiswallet.data.remote.LnurlPayMetadata
import github.aeonbtc.ibiswallet.data.remote.LnurlPayResolver
import github.aeonbtc.ibiswallet.tor.TorManager
import github.aeonbtc.ibiswallet.util.LightningKind
import github.aeonbtc.ibiswallet.util.ParsedSendRecipient
import github.aeonbtc.ibiswallet.util.isLightningAddressPayment
import github.aeonbtc.ibiswallet.util.parseSendRecipient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.util.Locale

class LightningNodeRepository(
    private val application: Application,
    private val secureStorage: SecureStorage,
) {
    companion object {
        const val DEFAULT_MAX_FEE_PERCENT = 5.0
        /**
         * History depth for LN payments and on-chain txs.
         * Enough for normal node history; initial load stays fast via parallel RPCs.
         * (Not infinite — LND honours max_payments; CLN trims after fetch.)
         */
        private const val HISTORY_LIMIT = 200
    }

    private val mutex = Mutex()
    private var backend: LightningNodeBackend? = null
    private var loadedConfig: LightningNodeConfig? = null

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder().build()
    }

    private val lnurlPayResolver: LnurlPayResolver by lazy {
        LnurlPayResolver(
            baseHttpClient = okHttpClient,
            useTor = { secureStorage.isTorEnabled() },
        )
    }

    private val _walletState = MutableStateFlow(LightningNodeWalletState(isInitialized = true))
    val walletState: StateFlow<LightningNodeWalletState> = _walletState.asStateFlow()

    private val _sendState = MutableStateFlow<LightningNodeSendState>(LightningNodeSendState.Idle)
    val sendState: StateFlow<LightningNodeSendState> = _sendState.asStateFlow()

    private val _onchainSendState =
        MutableStateFlow<LightningNodeOnchainSendState>(LightningNodeOnchainSendState.Idle)
    val onchainSendState: StateFlow<LightningNodeOnchainSendState> = _onchainSendState.asStateFlow()

    private val _receiveState = MutableStateFlow<LightningNodeReceiveState>(LightningNodeReceiveState.Idle)
    val receiveState: StateFlow<LightningNodeReceiveState> = _receiveState.asStateFlow()

    private val _events = MutableSharedFlow<LightningNodeEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<LightningNodeEvent> = _events.asSharedFlow()

    private val _onchainState = MutableStateFlow(LightningNodeOnchainState())
    val onchainState: StateFlow<LightningNodeOnchainState> = _onchainState.asStateFlow()

    private val _channels = MutableStateFlow<List<LightningNodeChannel>>(emptyList())
    val channels: StateFlow<List<LightningNodeChannel>> = _channels.asStateFlow()

    private val _channelsLoading = MutableStateFlow(false)
    val channelsLoading: StateFlow<Boolean> = _channelsLoading.asStateFlow()

    private val _channelsError = MutableStateFlow<String?>(null)
    val channelsError: StateFlow<String?> = _channelsError.asStateFlow()

    private val _loadedWalletId = MutableStateFlow<String?>(null)
    val loadedWalletId: StateFlow<String?> = _loadedWalletId.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _isConnecting = MutableStateFlow(false)
    val isConnecting: StateFlow<Boolean> = _isConnecting.asStateFlow()

    /**
     * Min relay fee (sat/vB) from the connected node's chain backend.
     * Null when disconnected / NWC / query failed — UI falls back to Electrum rate.
     */
    private val _onchainMinFeeRate = MutableStateFlow<Double?>(null)
    val onchainMinFeeRate: StateFlow<Double?> = _onchainMinFeeRate.asStateFlow()

    fun clearWalletDisplayState() {
        _onchainState.value = LightningNodeOnchainState()
        _channels.value = emptyList()
        _channelsLoading.value = false
        _channelsError.value = null
        _onchainMinFeeRate.value = null
        _walletState.value =
            LightningNodeWalletState(
                walletId = _loadedWalletId.value,
                isInitialized = true,
            )
        _isConnected.value = false
        _isConnecting.value = false
        resetSendState()
        resetOnchainSendState()
        resetReceiveState()
    }

    /**
     * Immediately mark [walletId] as the loaded target and connecting so wallet switches
     * show progress before [loadWallet] acquires the mutex / completes TLS/Tor setup.
     */
    fun beginConnecting(walletId: String) {
        val config = secureStorage.getLightningNodeConfig(walletId)
        _loadedWalletId.value = walletId
        _isConnected.value = false
        _isConnecting.value = true
        _walletState.value =
            LightningNodeWalletState(
                walletId = walletId,
                isInitialized = true,
                isConnecting = true,
                connectionType = config.type,
                host = config.host.ifBlank { null },
                port = config.port.takeIf { it > 0 },
                useTor = config.useTor,
                useTls = config.tlsEnabled,
            )
        resetSendState()
        resetOnchainSendState()
        resetReceiveState()
    }

    fun markLoadFailed(
        walletId: String,
        message: String,
    ) {
        val config = secureStorage.getLightningNodeConfig(walletId)
        _walletState.value =
            LightningNodeWalletState(
                walletId = walletId,
                isInitialized = true,
                connectionType = config.type,
                host = config.host.ifBlank { null },
                port = config.port.takeIf { it > 0 },
                useTor = config.useTor,
                useTls = config.tlsEnabled,
                error = message,
            )
        _isConnected.value = false
        _isConnecting.value = false
        _loadedWalletId.value = walletId
    }

    suspend fun loadWallet(walletId: String) =
        mutex.withLock {
            withContext(Dispatchers.IO) {
                // Still the requested wallet and already live — refresh only.
                if (_loadedWalletId.value == walletId && backend != null && _isConnected.value) {
                    refreshStateUnlocked()
                    return@withContext
                }
                // Another switch already changed the target while we waited on the mutex.
                if (_loadedWalletId.value != null && _loadedWalletId.value != walletId) {
                    return@withContext
                }
                disconnectUnlocked()
                ensureActive()
                // Drop work if a newer beginConnecting / unload changed the target mid-dial.
                if (_loadedWalletId.value != null && _loadedWalletId.value != walletId) {
                    return@withContext
                }
                _loadedWalletId.value = walletId
                _isConnecting.value = true
                val config = secureStorage.getLightningNodeConfig(walletId)
                _walletState.value =
                    LightningNodeWalletState(
                        walletId = walletId,
                        isInitialized = true,
                        isConnecting = true,
                        connectionType = config.type,
                        host = config.host.ifBlank { null },
                        port = config.port.takeIf { it > 0 },
                        useTor = config.useTor,
                        useTls = config.tlsEnabled,
                    )

                if (!config.isConfigured) {
                    _isConnecting.value = false
                    _walletState.value =
                        LightningNodeWalletState(
                            walletId = walletId,
                            isInitialized = true,
                            error = "Not connected — set up Lightning Node in Layer 2",
                            connectionType = config.type,
                            host = config.host.ifBlank { null },
                            port = config.port.takeIf { it > 0 },
                            useTor = config.useTor,
                            useTls = config.tlsEnabled,
                        )
                    return@withContext
                }

                try {
                    val connectConfig = withTorIfNeeded(config)
                    ensureTorIfNeeded(connectConfig)
                    ensureActive()
                    if (_loadedWalletId.value != walletId) {
                        return@withContext
                    }
                    val client = createBackend(connectConfig)
                    val info = client.connect(connectConfig)
                    ensureActive()
                    if (_loadedWalletId.value != walletId) {
                        runCatching { client.disconnect() }
                        return@withContext
                    }
                    backend = client
                    val sessionConfig =
                        (client as? LndRestClient)?.sessionConfig
                            ?: (client as? ClnRestClient)?.sessionConfig
                            ?: connectConfig
                    // Tor auto-detect (.onion) + preferSessionTls (skip slow cleartext next
                    // time). Never flip the user's TLS toggle from a session upgrade.
                    val preferSessionTls = !config.useTls && sessionConfig.tlsEnabled
                    val persistedHintDiffers =
                        sessionConfig.useTor != config.useTor ||
                            preferSessionTls != config.preferSessionTls
                    if (persistedHintDiffers) {
                        secureStorage.setLightningNodeConfig(
                            walletId,
                            config.copy(
                                useTor = sessionConfig.useTor,
                                preferSessionTls = preferSessionTls,
                            ),
                        )
                    }
                    // Live session keeps the transport that worked; UI prefs stay on config.
                    loadedConfig =
                        sessionConfig.copy(
                            useTls = config.useTls,
                            allowInsecureTls = config.allowInsecureTls,
                            preferSessionTls = preferSessionTls,
                            macaroonHex = config.macaroonHex,
                            tlsCertPem = config.tlsCertPem,
                            nwcUri = config.nwcUri,
                            clnRune = config.clnRune,
                        )
                    // Mark connected after getinfo + balance. Payment history and on-chain
                    // lists each open their own Tor circuits and used to block the spinner
                    // for minutes even when the node was already reachable.
                    val balance = client.getBalance()
                    ensureActive()
                    if (_loadedWalletId.value != walletId) {
                        disconnectUnlocked()
                        return@withContext
                    }
                    _isConnected.value = true
                    _isConnecting.value = false
                    _walletState.value =
                        LightningNodeWalletState(
                            walletId = walletId,
                            isInitialized = true,
                            isConnected = true,
                            isConnecting = false,
                            connectionType = config.type,
                            nodeAlias = info.alias,
                            nodePubkey = info.pubkey,
                            nodeVersion = info.version,
                            nodeNetwork = info.network,
                            numActiveChannels = info.numActiveChannels,
                            syncedToChain = info.syncedToChain,
                            host = sessionConfig.host.ifBlank { null },
                            port = sessionConfig.port.takeIf { it > 0 },
                            useTor = sessionConfig.useTor,
                            // User preference (settings/edit cog), not session auto-upgrade.
                            useTls = config.useTls,
                            balanceSats = balance.totalSats,
                            remoteBalanceSats = balance.remoteBalanceSats,
                            payments = emptyList(),
                            isSyncing = true,
                            lastSyncTimestamp = System.currentTimeMillis(),
                        )
                    // Best-effort enrichment — LN history, on-chain, min-relay in parallel.
                    try {
                        val payments =
                            coroutineScope {
                                val paysDeferred =
                                    async {
                                        runCatching { client.listPayments(HISTORY_LIMIT) }
                                            .getOrDefault(emptyList())
                                            .sortedByDescending { it.timestamp }
                                    }
                                val onchainDeferred =
                                    async {
                                        runCatching {
                                            refreshOnchainStateUnlocked(
                                                client,
                                                transactionLimit = HISTORY_LIMIT,
                                            )
                                        }
                                    }
                                val minRelayDeferred =
                                    async { runCatching { refreshOnchainMinFeeRateUnlocked(client) } }
                                onchainDeferred.await()
                                minRelayDeferred.await()
                                paysDeferred.await()
                            }
                        if (_loadedWalletId.value == walletId && backend === client) {
                            _walletState.value =
                                _walletState.value.copy(
                                    payments = payments,
                                    isSyncing = false,
                                    lastSyncTimestamp = System.currentTimeMillis(),
                                )
                        }
                    } catch (_: Exception) {
                        if (_loadedWalletId.value == walletId && backend === client) {
                            _walletState.value = _walletState.value.copy(isSyncing = false)
                        }
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) {
                        disconnectUnlocked()
                        throw e
                    }
                    if (_loadedWalletId.value != walletId) {
                        disconnectUnlocked()
                        return@withContext
                    }
                    disconnectUnlocked()
                    _isConnecting.value = false
                    _isConnected.value = false
                    _walletState.value =
                        LightningNodeWalletState(
                            walletId = walletId,
                            isInitialized = true,
                            connectionType = config.type,
                            host = config.host.ifBlank { null },
                            port = config.port.takeIf { it > 0 },
                            useTor = config.useTor,
                            useTls = config.tlsEnabled,
                            error = e.message?.takeIf { it.isNotBlank() } ?: "Connection failed",
                        )
                    throw e
                }
            }
        }

    suspend fun unloadWallet() =
        mutex.withLock {
            withContext(Dispatchers.IO) {
                disconnectUnlocked()
                _loadedWalletId.value = null
                _walletState.value = LightningNodeWalletState(isInitialized = true)
                _onchainState.value = LightningNodeOnchainState()
                _channels.value = emptyList()
                _channelsLoading.value = false
                _channelsError.value = null
                _isConnected.value = false
                _isConnecting.value = false
                resetSendState()
                resetOnchainSendState()
                resetReceiveState()
            }
        }

    /**
     * Ends the live node session but keeps the wallet selected and non-secret endpoint
     * fields so Connect can re-dial without reconfiguration.
     */
    suspend fun disconnectSession(walletId: String? = null) =
        mutex.withLock {
            withContext(Dispatchers.IO) {
                val targetId = walletId ?: _loadedWalletId.value
                disconnectUnlocked()
                _isConnected.value = false
                _isConnecting.value = false
                resetSendState()
                resetOnchainSendState()
                resetReceiveState()
                _onchainState.value = LightningNodeOnchainState()
                _channels.value = emptyList()
                _channelsLoading.value = false
                _channelsError.value = null
                if (targetId.isNullOrBlank()) {
                    _loadedWalletId.value = null
                    _walletState.value = LightningNodeWalletState(isInitialized = true)
                    return@withContext
                }
                // A superseding load/switch already pointed elsewhere — leave that alone.
                val currentId = _loadedWalletId.value
                if (currentId != null && currentId != targetId) {
                    return@withContext
                }
                applyDisconnectedConfigState(targetId)
            }
        }

    /** Restore offline endpoint identity for [walletId] without dialing. */
    fun applyDisconnectedConfigState(walletId: String) {
        val config = secureStorage.getLightningNodeConfig(walletId)
        _loadedWalletId.value = walletId
        _isConnected.value = false
        _isConnecting.value = false
        _channels.value = emptyList()
        _channelsLoading.value = false
        _channelsError.value = null
        _walletState.value =
            LightningNodeWalletState(
                walletId = walletId,
                isInitialized = true,
                isConnected = false,
                isConnecting = false,
                connectionType = config.type,
                host = config.host.ifBlank { null },
                port = config.port.takeIf { it > 0 },
                useTor = config.useTor,
                useTls = config.tlsEnabled,
            )
    }

    suspend fun reconnectWallet() {
        val walletId = _loadedWalletId.value ?: return
        loadWallet(walletId)
    }

    /**
     * Lightweight liveness check for background keep-alive heartbeats.
     * Does not hold the main mutex so it can run next to sync/pay work.
     */
    suspend fun pingConnection(): Boolean =
        withContext(Dispatchers.IO) {
            val client = backend ?: return@withContext false
            runCatching { client.ping() }.getOrDefault(false)
        }

    fun currentConfigUsesTor(): Boolean {
        val config = loadedConfig ?: return _walletState.value.useTor
        return config.useTor || config.host.endsWith(".onion", ignoreCase = true)
    }

    suspend fun refreshState() =
        mutex.withLock {
            withContext(Dispatchers.IO) {
                refreshStateUnlocked()
            }
        }

    private suspend fun refreshStateUnlocked() {
        val client = backend ?: return
        val walletId = _loadedWalletId.value ?: return
        _walletState.value = _walletState.value.copy(isSyncing = true, error = null)
        try {
            // Fan out balance / payments / channels / on-chain so wall time ≈ slowest RPC.
            val (balance, payments, channelList) =
                coroutineScope {
                    val balanceDeferred = async { client.getBalance() }
                    val paymentsDeferred =
                        async {
                            runCatching { client.listPayments(HISTORY_LIMIT) }
                                .getOrNull()
                                ?.let {
                                    mergeStablePaymentTimestamps(_walletState.value.payments, it)
                                }
                                ?: _walletState.value.payments
                        }
                    val channelsDeferred =
                        async {
                            runCatching { client.listChannels() }
                                .onSuccess {
                                    _channelsError.value = null
                                }
                                .onFailure { err ->
                                    _channelsError.value =
                                        err.message?.takeIf { it.isNotBlank() }
                                            ?: "Failed to load channels"
                                }
                                .getOrDefault(_channels.value)
                        }
                    val onchainDeferred =
                        async {
                            runCatching {
                                refreshOnchainStateUnlocked(
                                    client,
                                    transactionLimit = HISTORY_LIMIT,
                                )
                            }
                        }
                    val minRelayDeferred =
                        async { runCatching { refreshOnchainMinFeeRateUnlocked(client) } }
                    onchainDeferred.await()
                    minRelayDeferred.await()
                    Triple(balanceDeferred.await(), paymentsDeferred.await(), channelsDeferred.await())
                }
            _channels.value = channelList
            _walletState.value =
                _walletState.value.copy(
                    isSyncing = false,
                    isConnected = true,
                    balanceSats = balance.totalSats,
                    remoteBalanceSats = balance.remoteBalanceSats,
                    payments = payments,
                    lastSyncTimestamp = System.currentTimeMillis(),
                    error = null,
                )
            _isConnected.value = true
            checkOpenInvoicePaid(payments)
        } catch (e: Exception) {
            _walletState.value =
                _walletState.value.copy(
                    isSyncing = false,
                    walletId = walletId,
                    error = e.message?.takeIf { it.isNotBlank() } ?: "Refresh failed",
                )
        }
    }

    /**
     * Lightweight poll used while an invoice is open on the receive screen.
     * Avoids full refresh overhead (channels / on-chain) for faster paid detection.
     */
    suspend fun pollOpenInvoicePayment() =
        withContext(Dispatchers.IO) {
            if (_receiveState.value !is LightningNodeReceiveState.Ready) return@withContext
            val client = backend ?: return@withContext
            val payments =
                runCatching { client.listPayments(HISTORY_LIMIT) }
                    .getOrNull()
                    ?: return@withContext
            val merged = mergeStablePaymentTimestamps(_walletState.value.payments, payments)
            if (merged !== _walletState.value.payments) {
                _walletState.value =
                    _walletState.value.copy(
                        payments = merged,
                        lastSyncTimestamp = System.currentTimeMillis(),
                    )
            }
            checkOpenInvoicePaid(merged)
        }

    private fun checkOpenInvoicePaid(payments: List<LightningNodePayment>) {
        val ready = _receiveState.value as? LightningNodeReceiveState.Ready ?: return
        val readyHash = ready.paymentHash.normalizePaymentHash()
        val readyRequest = ready.paymentRequest.normalizeBolt11()
        val match =
            payments.firstOrNull { payment ->
                payment.direction == LightningNodePaymentDirection.INCOMING &&
                    payment.status == LightningNodePaymentStatus.SUCCEEDED &&
                    (
                        (
                            !readyHash.isNullOrBlank() &&
                                payment.paymentHash.normalizePaymentHash() == readyHash
                        ) ||
                            (
                                !readyRequest.isNullOrBlank() &&
                                    payment.paymentRequest.normalizeBolt11() == readyRequest
                            )
                    )
            } ?: return

        val amountSats =
            match.amountSats.takeIf { it > 0 }
                ?: ready.amountSats
                ?: 0L
        _receiveState.value =
            LightningNodeReceiveState.Paid(
                paymentHash = match.paymentHash ?: ready.paymentHash,
                amountSats = amountSats,
                paymentRequest = ready.paymentRequest,
                description = ready.description,
            )
        _events.tryEmit(
            LightningNodeEvent.PaymentReceived(
                paymentId = match.id.ifBlank { match.paymentHash ?: ready.paymentRequest },
                amountSats = amountSats,
                paymentHash = match.paymentHash ?: ready.paymentHash,
            ),
        )
    }

    private fun String?.normalizePaymentHash(): String? =
        this
            ?.trim()
            ?.lowercase(Locale.US)
            ?.removePrefix("0x")
            ?.takeIf { it.isNotBlank() }

    private fun String?.normalizeBolt11(): String? =
        this
            ?.trim()
            ?.removePrefix("lightning:")
            ?.removePrefix("LIGHTNING:")
            ?.lowercase(Locale.US)
            ?.takeIf { it.isNotBlank() }

    suspend fun refreshChannels() {
        mutex.withLock {
            withContext(Dispatchers.IO) {
                val client = backend
                if (client == null) {
                    _channelsError.value = "Not connected"
                    return@withContext
                }
                _channelsLoading.value = true
                try {
                    val list = client.listChannels()
                    _channels.value = list
                    _channelsError.value = null
                } catch (e: Exception) {
                    _channelsError.value =
                        e.message?.takeIf { it.isNotBlank() } ?: "Failed to load channels"
                } finally {
                    _channelsLoading.value = false
                }
            }
        }
    }

    suspend fun testConnection(
        config: LightningNodeConfig,
        onPhase: (LightningNodeConnectionTestPhase) -> Unit = {},
    ): LightningNodeConnectionTestResult =
        withContext(Dispatchers.IO) {
            if (!config.isConfigured) {
                return@withContext LightningNodeConnectionTestResult.Failure("Incomplete connection settings")
            }
            try {
                onPhase(LightningNodeConnectionTestPhase.PREPARING)
                val connectConfig = withTorIfNeeded(config)
                ensureTorIfNeeded(connectConfig, onPhase)
                onPhase(LightningNodeConnectionTestPhase.CONNECTING)
                val client = createBackend(connectConfig)
                val info = client.connect(connectConfig)
                onPhase(LightningNodeConnectionTestPhase.FETCHING_BALANCE)
                val balance = client.getBalance()
                val sessionConfig =
                    (client as? LndRestClient)?.sessionConfig
                        ?: (client as? ClnRestClient)?.sessionConfig
                        ?: connectConfig
                client.disconnect()
                // Hint for Save without flipping the TLS toggle.
                val preferSessionTls = !config.useTls && sessionConfig.tlsEnabled
                LightningNodeConnectionTestResult.Success(
                    info = info,
                    balance = balance,
                    preferSessionTls = preferSessionTls,
                )
            } catch (e: Exception) {
                // TimeoutCancellationException is a CancellationException; convert before it
                // escapes and wipes the test UI without a Failure payload.
                if (
                    e is kotlinx.coroutines.CancellationException &&
                    e !is kotlinx.coroutines.TimeoutCancellationException
                ) {
                    throw e
                }
                val raw = e.message?.takeIf { it.isNotBlank() } ?: "Connection failed"
                val hint =
                    when {
                        raw.contains("HTTP request to an HTTPS server", ignoreCase = true) ||
                            raw.contains("http request to an https", ignoreCase = true) ->
                            "$raw — enable TLS (LND REST / CLN clnrest usually use HTTPS)"
                        raw.contains("timed out", ignoreCase = true) ||
                            raw.contains("timeout", ignoreCase = true) ->
                            "$raw. For NWC: confirm the URI is active, relays are reachable, and Tor is off for clearnet Alby relays."
                        else -> raw
                    }
                LightningNodeConnectionTestResult.Failure(hint)
            }
        }

    fun getConfig(walletId: String): LightningNodeConfig = secureStorage.getLightningNodeConfig(walletId)

    fun saveConfig(
        walletId: String,
        config: LightningNodeConfig,
    ) {
        val existing = secureStorage.getLightningNodeConfig(walletId)
        val sameEndpoint =
            existing.type == config.type &&
                existing.host.equals(config.host.trim(), ignoreCase = true) &&
                existing.port == config.port &&
                existing.useTls == config.useTls
        // Keep the LAN speed hint across edit/save; drop it when host/port/TLS change.
        val toSave =
            config.copy(
                preferSessionTls =
                    if (sameEndpoint) {
                        existing.preferSessionTls || config.preferSessionTls
                    } else {
                        config.preferSessionTls
                    },
            )
        secureStorage.setLightningNodeConfig(walletId, toSave)
    }

    suspend fun createInvoice(
        amountSats: Long?,
        description: String?,
    ) {
        val client = backend
        if (client == null) {
            _receiveState.value = LightningNodeReceiveState.Error("Not connected")
            return
        }
        _receiveState.value = LightningNodeReceiveState.Generating
        try {
            // Memo only when user explicitly provided one (blank → null for all backends).
            val memo = description?.trim()?.takeIf { it.isNotBlank() }
            val invoice = client.createInvoice(amountSats, memo)
            _receiveState.value =
                LightningNodeReceiveState.Ready(
                    paymentRequest = invoice.paymentRequest,
                    amountSats = invoice.amountSats,
                    description = memo,
                    paymentHash = invoice.paymentHash,
                )
        } catch (e: Exception) {
            _receiveState.value =
                LightningNodeReceiveState.Error(
                    e.message?.takeIf { it.isNotBlank() } ?: "Invoice creation failed",
                )
        }
    }

    suspend fun prepareSend(
        paymentRequest: String,
        amountSats: Long?,
        maxFeePercent: Double? = null,
    ) {
        val client = backend
        if (client == null) {
            _sendState.value = LightningNodeSendState.Error("Not connected")
            return
        }
        _sendState.value = LightningNodeSendState.Decoding
        try {
            val decoded = resolvePaymentForSend(client, paymentRequest, amountSats)
            val finalAmount = decoded.amountSats ?: amountSats
            _sendState.value =
                LightningNodeSendState.Preview(
                    paymentRequest = decoded.paymentRequest,
                    amountSats = finalAmount,
                    isFixedAmount = decoded.amountSats != null,
                    description = decoded.description,
                    destination = decoded.destination,
                    maxFeePercent = maxFeePercent ?: DEFAULT_MAX_FEE_PERCENT,
                )
        } catch (e: Exception) {
            _sendState.value =
                LightningNodeSendState.Error(
                    e.message?.takeIf { it.isNotBlank() } ?: "Decode failed",
                )
        }
    }

    fun updatePreparedMaxFeePercent(maxFeePercent: Double) {
        val preview = _sendState.value as? LightningNodeSendState.Preview ?: return
        _sendState.value =
            preview.copy(
                maxFeePercent = maxFeePercent.coerceIn(0.0, 100.0),
            )
    }

    private suspend fun resolvePaymentForSend(
        client: LightningNodeBackend,
        paymentRequest: String,
        amountSats: Long?,
    ): DecodedLightningNodeInvoice {
        val parsed = parseSendRecipient(paymentRequest.trim())
        return when (parsed) {
            is ParsedSendRecipient.Lightning ->
                when {
                    parsed.kind == LightningKind.LNURL ->
                        resolveLnurlToInvoice(parsed.paymentInput, amountSats)
                    parsed.kind == LightningKind.BOLT12 && isLightningAddressPayment(parsed) ->
                        resolveLightningAddressToInvoice(parsed.paymentInput, amountSats)
                    parsed.kind == LightningKind.BOLT12 -> {
                        require(client is ClnRestClient) {
                            "BOLT12 offers require a CLN connection"
                        }
                        client.fetchBolt12Invoice(parsed.paymentInput, amountSats)
                    }
                    else -> client.decodeInvoice(parsed.paymentInput)
                }
            else -> client.decodeInvoice(paymentRequest)
        }
    }

    private suspend fun resolveLightningAddressToInvoice(
        address: String,
        amountSats: Long?,
    ): DecodedLightningNodeInvoice {
        val metadata = lnurlPayResolver.resolveAddress(address)
        return fetchLnurlInvoice(metadata, amountSats)
    }

    private suspend fun resolveLnurlToInvoice(
        lnurlUrl: String,
        amountSats: Long?,
    ): DecodedLightningNodeInvoice {
        val metadata = lnurlPayResolver.resolveUrl(lnurlUrl)
        return fetchLnurlInvoice(metadata, amountSats)
    }

    private suspend fun fetchLnurlInvoice(
        metadata: LnurlPayMetadata,
        amountSats: Long?,
    ): DecodedLightningNodeInvoice {
        val resolvedAmount = resolveAndValidateLnurlAmount(metadata, amountSats)
        val invoice = lnurlPayResolver.fetchInvoice(metadata, resolvedAmount * 1000L)
        return DecodedLightningNodeInvoice(
            paymentRequest = invoice.bolt11,
            paymentHash = null,
            amountSats = resolvedAmount,
            description = null,
            destination = null,
            expirySeconds = null,
        )
    }

    private fun resolveAndValidateLnurlAmount(
        metadata: LnurlPayMetadata,
        requestedAmountSats: Long?,
    ): Long {
        if (metadata.isFixedAmount) {
            return metadata.minSendableSats
        }
        val amount =
            requestedAmountSats
                ?: throw IllegalArgumentException(
                    "Enter an amount between ${formatSatsDisplay(metadata.minSendableSats)} " +
                        "and ${formatSatsDisplay(metadata.maxSendableSats)}.",
                )
        if (amount < metadata.minSendableSats) {
            throw IllegalArgumentException(
                "Amount is below the minimum of ${formatSatsDisplay(metadata.minSendableSats)}.",
            )
        }
        if (amount > metadata.maxSendableSats) {
            throw IllegalArgumentException(
                "Amount exceeds the maximum of ${formatSatsDisplay(metadata.maxSendableSats)}.",
            )
        }
        return amount
    }

    private fun formatSatsDisplay(sats: Long): String =
        "%,d sats".format(java.util.Locale.US, sats)

    suspend fun sendPrepared() {
        val preview = _sendState.value as? LightningNodeSendState.Preview
        if (preview == null) {
            _sendState.value = LightningNodeSendState.Error("Nothing to send")
            return
        }
        val client = backend
        if (client == null) {
            _sendState.value = LightningNodeSendState.Error("Not connected")
            return
        }
        _sendState.value =
            LightningNodeSendState.Paying(
                paymentRequest = preview.paymentRequest,
                amountSats = preview.amountSats,
                destination = preview.destination,
            )
        try {
            val result = client.payInvoice(
                preview.paymentRequest,
                preview.amountSats.takeUnless { preview.isFixedAmount },
                preview.maxFeePercent,
            )
            // Belt-and-suspenders: backends must return a real preimage for success.
            val preimage = result.preimage?.trim().orEmpty()
            if (
                preimage.isBlank() ||
                preimage.matches(Regex("^0+$")) ||
                preimage.matches(Regex("^(00)+$"))
            ) {
                throw IllegalStateException("Payment did not complete")
            }
            _sendState.value =
                LightningNodeSendState.Paid(
                    paymentId = result.paymentId,
                    paymentHash = result.paymentHash,
                    amountSats = preview.amountSats ?: 0L,
                    feeSats = result.feeSats,
                )
            runCatching { refreshStateUnlocked() }
        } catch (e: Exception) {
            _sendState.value =
                LightningNodeSendState.Error(
                    e.message?.takeIf { it.isNotBlank() } ?: "Payment failed",
                )
        }
    }

    fun resetSendState() {
        _sendState.value = LightningNodeSendState.Idle
    }

    fun resetOnchainSendState() {
        _onchainSendState.value = LightningNodeOnchainSendState.Idle
    }

    fun resetReceiveState() {
        _receiveState.value = LightningNodeReceiveState.Idle
    }

    suspend fun generateOnchainAddress() = mutex.withLock {
        withContext(Dispatchers.IO) {
            val client = backend ?: return@withContext
            _onchainState.value = _onchainState.value.copy(isSyncing = true, error = null)
            try {
                val address = client.newOnchainAddress()
                val prev = _onchainState.value
                val revealed =
                    (listOf(address) + prev.revealedReceiveAddresses)
                        .distinct()
                val catalogs =
                    buildOnchainAddressCatalogs(
                        revealedReceive = revealed,
                        currentAddress = address,
                        transactions = prev.transactions,
                        utxos = prev.utxos,
                    )
                _onchainState.value =
                    prev.copy(
                        isAvailable = true,
                        currentAddress = address,
                        revealedReceiveAddresses = revealed,
                        receiveAddresses = catalogs.first,
                        changeAddresses = catalogs.second,
                        usedAddresses = catalogs.third,
                        isSyncing = false,
                        error = null,
                    )
            } catch (e: Exception) {
                // Keep prior balance/history available; only surface address error.
                _onchainState.value =
                    _onchainState.value.copy(
                        isSyncing = false,
                        error = e.message ?: "Failed to generate address",
                    )
            }
        }
    }

    suspend fun sendOnchain(
        address: String,
        amountSats: Long?,
        satPerVbyte: Double?,
        sendAll: Boolean,
        label: String?,
        spendUnconfirmed: Boolean,
        selectedOutpoints: List<UtxoInfo>?,
    ): String =
        mutex.withLock {
            withContext(Dispatchers.IO) {
                _onchainSendState.value = LightningNodeOnchainSendState.Sending
                try {
                    val client = backend ?: throw IllegalStateException("Not connected")
                    val txid =
                        client.sendOnchain(
                            address = address,
                            amountSats = amountSats,
                            satPerVbyte = satPerVbyte,
                            sendAll = sendAll,
                            label = label,
                            spendUnconfirmed = spendUnconfirmed,
                            selectedOutpoints = selectedOutpoints,
                        )
                    runCatching { refreshOnchainStateUnlocked(client) }
                    _onchainSendState.value = LightningNodeOnchainSendState.Success(txid)
                    txid
                } catch (e: Exception) {
                    _onchainSendState.value =
                        LightningNodeOnchainSendState.Error(
                            e.message?.takeIf { it.isNotBlank() } ?: "On-chain send failed",
                        )
                    throw e
                }
            }
        }

    suspend fun sendOnchainMany(
        addrToAmountSats: Map<String, Long>,
        satPerVbyte: Double?,
        label: String?,
        spendUnconfirmed: Boolean,
        selectedOutpoints: List<UtxoInfo>?,
    ): String =
        mutex.withLock {
            withContext(Dispatchers.IO) {
                _onchainSendState.value = LightningNodeOnchainSendState.Sending
                try {
                    val client = backend ?: throw IllegalStateException("Not connected")
                    val txid =
                        client.sendOnchainMany(
                            addrToAmountSats = addrToAmountSats,
                            satPerVbyte = satPerVbyte,
                            label = label,
                            spendUnconfirmed = spendUnconfirmed,
                            selectedOutpoints = selectedOutpoints,
                        )
                    runCatching { refreshOnchainStateUnlocked(client) }
                    _onchainSendState.value = LightningNodeOnchainSendState.Success(txid)
                    txid
                } catch (e: Exception) {
                    _onchainSendState.value =
                        LightningNodeOnchainSendState.Error(
                            e.message?.takeIf { it.isNotBlank() } ?: "On-chain multi-send failed",
                        )
                    throw e
                }
            }
        }

    /**
     * RBF/CPFP via LND WalletKit BumpFee for an unconfirmed parent [txid].
     * Picks the largest wallet-owned outpoint from that parent (change or receive UTXO).
     */
    suspend fun bumpOnchainFee(
        parentTxid: String,
        satPerVbyte: Double?,
    ): String =
        mutex.withLock {
            withContext(Dispatchers.IO) {
                val client = backend ?: throw IllegalStateException("Not connected")
                val outpoint =
                    resolveBumpOutpoint(parentTxid)
                        ?: throw IllegalStateException(
                            "No wallet UTXO found for this transaction — cannot fee-bump",
                        )
                val budget =
                    outpoint.amountSats.toLong().let { value ->
                        // Prefer parent output value as budget upper bound (LND CPFP may add wallet UTXOs).
                        (value / 2).coerceAtLeast(1L)
                    }
                val status =
                    client.bumpOnchainFee(
                        outpoint = outpoint,
                        satPerVbyte = satPerVbyte,
                        immediate = true,
                        budgetSats = budget,
                    )
                runCatching { refreshOnchainStateUnlocked(client) }
                status
            }
        }

    fun resolveBumpOutpoint(parentTxid: String): UtxoInfo? {
        val normalized = parentTxid.trim()
        if (normalized.isBlank()) return null
        return _onchainState.value.utxos
            .filter { it.txid.equals(normalized, ignoreCase = true) }
            .maxByOrNull { it.amountSats }
    }

    fun canBumpOnchainFee(parentTxid: String, confirmations: Int): Boolean {
        if (loadedConfig?.type == LightningNodeConnectionType.CLN_REST ||
            loadedConfig?.type == LightningNodeConnectionType.NWC
        ) {
            return false
        }
        return confirmations <= 0 && resolveBumpOutpoint(parentTxid) != null
    }

    suspend fun deleteWalletData(walletId: String) {
        if (_loadedWalletId.value == walletId) {
            unloadWallet()
        }
        secureStorage.clearLightningNodeConfig(walletId)
        secureStorage.setLightningNodeEnabledForWallet(walletId, false)
    }

    private fun createBackend(config: LightningNodeConfig): LightningNodeBackend =
        when (config.type) {
            LightningNodeConnectionType.LND_REST -> LndRestClient()
            LightningNodeConnectionType.NWC -> NwcClient()
            LightningNodeConnectionType.CLN_REST -> ClnRestClient()
            LightningNodeConnectionType.NONE ->
                throw IllegalStateException("No connection type configured")
        }

    private fun configNeedsTor(config: LightningNodeConfig): Boolean {
        val host = config.host.trim().lowercase()
        val onionHost = host.endsWith(".onion") || host.contains(".onion:")
        val onionNwc =
            config.type == LightningNodeConnectionType.NWC &&
                config.nwcUri.contains(".onion", ignoreCase = true)
        return config.useTor || onionHost || onionNwc
    }

    private suspend fun ensureTorIfNeeded(
        config: LightningNodeConfig,
        onPhase: (LightningNodeConnectionTestPhase) -> Unit = {},
    ) {
        if (!configNeedsTor(config)) return
        val tor = TorManager.getInstance(application)
        if (tor.isReady()) return
        onPhase(LightningNodeConnectionTestPhase.STARTING_TOR)
        tor.start()
        onPhase(LightningNodeConnectionTestPhase.WAITING_FOR_TOR)
        if (!tor.awaitReady(timeoutMs = 90_000L)) {
            throw IllegalStateException("Tor is not ready")
        }
    }

    /** Normalize onion → useTor before handoff to backends. */
    private fun withTorIfNeeded(config: LightningNodeConfig): LightningNodeConfig =
        if (configNeedsTor(config) && !config.useTor) {
            config.copy(useTor = true)
        } else {
            config
        }

    /** Query node chain-backend min relay (sat/vB) for LN L1 fee floor. */
    private suspend fun refreshOnchainMinFeeRateUnlocked(client: LightningNodeBackend) {
        val rate =
            runCatching { client.getOnchainMinRelayFeeSatPerVb() }
                .getOrNull()
                ?.takeIf { it in 0.01..100.0 }
        if (rate != null) {
            _onchainMinFeeRate.value = rate
        }
        // Keep prior value on transient failure so the fee picker does not jump to Electrum default.
    }

    private suspend fun refreshOnchainStateUnlocked(
        client: LightningNodeBackend,
        transactionLimit: Int = HISTORY_LIMIT,
    ) {
        val previous = _onchainState.value
        _onchainState.value = previous.copy(isSyncing = true, error = null)
        // Balance alone is enough to mark L1 available; history/utxos are best-effort so a
        // permissions/list/network failure does not blank the entire on-chain layer.
        // Fetch in parallel — sequential UTXO + history was the dominant LAN/Tor lag.
        val (balanceResult, txsResult, utxosResult) =
            coroutineScope {
                val balanceDeferred = async { runCatching { client.getOnchainBalanceDetails() } }
                val txsDeferred =
                    async {
                        runCatching { client.listOnchainTransactions(transactionLimit) }
                    }
                val utxosDeferred = async { runCatching { client.listOnchainUtxos() } }
                Triple(balanceDeferred.await(), txsDeferred.await(), utxosDeferred.await())
            }
        when {
            balanceResult.isSuccess -> {
                val details = balanceResult.getOrThrow()
                val transactions =
                    txsResult
                        .getOrNull()
                        ?.let { mergeStableOnchainTimestamps(previous.transactions, it) }
                        ?: previous.transactions
                val utxos = utxosResult.getOrDefault(previous.utxos)
                val revealed = previous.revealedReceiveAddresses
                val catalogs =
                    buildOnchainAddressCatalogs(
                        revealedReceive = revealed,
                        currentAddress = previous.currentAddress,
                        transactions = transactions,
                        utxos = utxos,
                    )
                val secondaryError =
                    listOfNotNull(
                        txsResult.exceptionOrNull()?.message,
                        utxosResult.exceptionOrNull()?.message,
                    ).firstOrNull()
                _onchainState.value =
                    previous.copy(
                        isAvailable = true,
                        balanceSats = details.spendableSats,
                        reservedSats = details.reservedSats,
                        immatureSats = details.immatureSats,
                        transactions = transactions,
                        utxos = utxos,
                        receiveAddresses = catalogs.first,
                        changeAddresses = catalogs.second,
                        usedAddresses = catalogs.third,
                        isSyncing = false,
                        error = secondaryError,
                    )
            }
            else -> {
                _onchainState.value =
                    previous.copy(
                        isAvailable = false,
                        isSyncing = false,
                        error =
                            balanceResult.exceptionOrNull()?.message
                                ?: "On-chain wallet unavailable",
                    )
            }
        }
    }

    private fun mergeStableOnchainTimestamps(
        previous: List<LightningNodeOnchainTransaction>,
        refreshed: List<LightningNodeOnchainTransaction>,
    ): List<LightningNodeOnchainTransaction> {
        val previousByTxid = previous.associateBy { it.txid }
        val now = System.currentTimeMillis()
        return refreshed
            .map { tx ->
                val old = previousByTxid[tx.txid]
                when {
                    old != null && old.confirmations == 0 && tx.confirmations == 0 && old.timestamp > 0L ->
                        tx.copy(timestamp = old.timestamp)
                    tx.confirmations == 0 && tx.timestamp <= 0L ->
                        tx.copy(timestamp = now)
                    else -> tx
                }
            }.sortedWith(
                compareByDescending<LightningNodeOnchainTransaction> { it.confirmations <= 0 }
                    .thenByDescending { it.timestamp }
                    .thenByDescending { it.txid },
            )
    }

    private fun mergeStablePaymentTimestamps(
        previous: List<github.aeonbtc.ibiswallet.data.model.LightningNodePayment>,
        refreshed: List<github.aeonbtc.ibiswallet.data.model.LightningNodePayment>,
    ): List<github.aeonbtc.ibiswallet.data.model.LightningNodePayment> {
        val previousById = previous.associateBy { it.id }
        return refreshed
            .map { payment ->
                val old = previousById[payment.id]
                if (payment.timestamp <= 0L && old != null && old.timestamp > 0L) {
                    payment.copy(timestamp = old.timestamp)
                } else {
                    payment
                }
            }.sortedByDescending { it.timestamp }
    }

    /**
     * LND has no full BDK-style address index. Build All Addresses tabs from:
     * - Receive: addresses generated via NewAddress in-session (minus those already used)
     * - Change: empty (not exposed remotely)
     * - Used: addresses seen on UTXOs / on-chain tx destinations
     */
    private fun buildOnchainAddressCatalogs(
        revealedReceive: List<String>,
        currentAddress: String?,
        transactions: List<LightningNodeOnchainTransaction>,
        utxos: List<UtxoInfo>,
    ): Triple<List<WalletAddress>, List<WalletAddress>, List<WalletAddress>> {
        val balancesByAddress =
            utxos
                .groupBy { it.address }
                .mapValues { (_, coins) -> coins.sumOf { it.amountSats.toLong() }.toULong() }
        val txCountByAddress = mutableMapOf<String, Int>()
        transactions.forEach { tx ->
            tx.address?.takeIf { it.isNotBlank() }?.let { addr ->
                txCountByAddress[addr] = (txCountByAddress[addr] ?: 0) + 1
            }
        }
        val usedAddressSet =
            buildSet {
                addAll(utxos.map { it.address }.filter { it.isNotBlank() })
                addAll(transactions.mapNotNull { it.address?.takeIf { a -> a.isNotBlank() } })
            }

        val receiveSource =
            (listOfNotNull(currentAddress) + revealedReceive)
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinct()
                .filterNot { it in usedAddressSet }

        val receive =
            receiveSource.mapIndexed { index, address ->
                WalletAddress(
                    address = address,
                    index = index.toUInt(),
                    keychain = KeychainType.EXTERNAL,
                    balanceSats = balancesByAddress[address] ?: 0UL,
                    transactionCount = txCountByAddress[address] ?: 0,
                    isUsed = false,
                )
            }

        val used =
            usedAddressSet
                .sorted()
                .mapIndexed { index, address ->
                    WalletAddress(
                        address = address,
                        index = index.toUInt(),
                        keychain = KeychainType.EXTERNAL,
                        balanceSats = balancesByAddress[address] ?: 0UL,
                        transactionCount = txCountByAddress[address] ?: 0,
                        isUsed = true,
                    )
                }

        return Triple(receive, emptyList(), used)
    }

    private suspend fun disconnectUnlocked() {
        runCatching { backend?.disconnect() }
        backend = null
        loadedConfig = null
        _onchainMinFeeRate.value = null
    }
}
