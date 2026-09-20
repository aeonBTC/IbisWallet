package github.aeonbtc.ibiswallet.data.repository

import android.content.Context
import breez_sdk_spark.AssetFilter
import breez_sdk_spark.BreezSdk
import breez_sdk_spark.CheckUnilateralExitRequest
import breez_sdk_spark.ClaimDepositRequest
import breez_sdk_spark.ConnectRequest
import breez_sdk_spark.CpfpFundingKind
import breez_sdk_spark.CpfpInput
import breez_sdk_spark.CpfpSigner
import breez_sdk_spark.EventListener
import breez_sdk_spark.ExitLeafSelection
import breez_sdk_spark.ExitTransactionStatus
import breez_sdk_spark.FeePolicy
import breez_sdk_spark.FetchClaimDepositQuoteRequest
import breez_sdk_spark.GetInfoRequest
import breez_sdk_spark.ImportUnilateralExitStateRequest
import breez_sdk_spark.InputType
import breez_sdk_spark.ListPaymentsRequest
import breez_sdk_spark.ListUnclaimedDepositsRequest
import breez_sdk_spark.LnurlPayRequest
import breez_sdk_spark.LnurlPayRequestDetails
import breez_sdk_spark.MaxFee
import breez_sdk_spark.Network
import breez_sdk_spark.OnchainConfirmationSpeed
import breez_sdk_spark.Payment
import breez_sdk_spark.PaymentDetails
import breez_sdk_spark.PaymentRequest
import breez_sdk_spark.PaymentStatus
import breez_sdk_spark.PaymentType
import breez_sdk_spark.PrepareLnurlPayRequest
import breez_sdk_spark.PrepareLnurlPayResponse
import breez_sdk_spark.PrepareSendPaymentRequest
import breez_sdk_spark.PrepareSendPaymentResponse
import breez_sdk_spark.PrepareUnilateralExitRequest
import breez_sdk_spark.PrepareUnilateralExitResponse
import breez_sdk_spark.ReceivePaymentMethod
import breez_sdk_spark.ReceivePaymentRequest
import breez_sdk_spark.SdkEvent
import breez_sdk_spark.Seed
import breez_sdk_spark.SendOnchainSpeedFeeQuote
import breez_sdk_spark.SendPaymentMethod
import breez_sdk_spark.SendPaymentOptions
import breez_sdk_spark.SendPaymentRequest
import breez_sdk_spark.SyncWalletRequest
import breez_sdk_spark.UnilateralExitLeaf
import breez_sdk_spark.UnilateralExitRequest
import breez_sdk_spark.UnilateralExitResponse
import breez_sdk_spark.UnilateralExitTransaction
import breez_sdk_spark.UnilateralExitTxKind
import breez_sdk_spark.UnilateralExitVerdict
import breez_sdk_spark.UpdateUserSettingsRequest
import breez_sdk_spark.connect
import breez_sdk_spark.defaultConfig
import github.aeonbtc.ibiswallet.BuildConfig
import github.aeonbtc.ibiswallet.R
import github.aeonbtc.ibiswallet.data.local.SecureStorage
import github.aeonbtc.ibiswallet.data.model.BitcoinTxSource
import github.aeonbtc.ibiswallet.data.model.FeeEstimateSource
import github.aeonbtc.ibiswallet.data.model.FeeEstimates
import github.aeonbtc.ibiswallet.data.model.SeedFormat
import github.aeonbtc.ibiswallet.data.model.SparkEvent
import github.aeonbtc.ibiswallet.data.model.SparkExitBranchFunding
import github.aeonbtc.ibiswallet.data.model.SparkExitFlowState
import github.aeonbtc.ibiswallet.data.model.SparkExitFundingUtxo
import github.aeonbtc.ibiswallet.data.model.SparkExitLeafScope
import github.aeonbtc.ibiswallet.data.model.SparkExitLiveUtxo
import github.aeonbtc.ibiswallet.data.model.SparkExitQuote
import github.aeonbtc.ibiswallet.data.model.SparkExitQuotedLeaf
import github.aeonbtc.ibiswallet.data.model.SparkExitRedoReason
import github.aeonbtc.ibiswallet.data.model.SparkExitTx
import github.aeonbtc.ibiswallet.data.model.SparkExitTxKind
import github.aeonbtc.ibiswallet.data.model.SparkExitTxStatus
import github.aeonbtc.ibiswallet.data.model.SparkOnchainFeeQuote
import github.aeonbtc.ibiswallet.data.model.SparkOnchainFeeSpeed
import github.aeonbtc.ibiswallet.data.model.SparkPayment
import github.aeonbtc.ibiswallet.data.model.SparkPendingLnInvoice
import github.aeonbtc.ibiswallet.data.model.SparkReceiveKind
import github.aeonbtc.ibiswallet.data.model.SparkReceiveState
import github.aeonbtc.ibiswallet.data.model.SparkSendState
import github.aeonbtc.ibiswallet.data.model.SparkDepositClaimQuote
import github.aeonbtc.ibiswallet.data.model.SparkUnclaimedDeposit
import github.aeonbtc.ibiswallet.data.model.SparkWalletState
import github.aeonbtc.ibiswallet.localization.AppLocale
import github.aeonbtc.ibiswallet.util.SecureLog
import github.aeonbtc.ibiswallet.util.SparkServiceErrors
import github.aeonbtc.ibiswallet.util.normalizeSparkAddressLabelRef
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.math.BigInteger
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeoutException

class SparkRepository(
    private val context: Context,
    private val secureStorage: SecureStorage,
) {
    private val _sparkState = MutableStateFlow(SparkWalletState())
    val sparkState: StateFlow<SparkWalletState> = _sparkState.asStateFlow()

    private val _sendState = MutableStateFlow<SparkSendState>(SparkSendState.Idle)
    val sendState: StateFlow<SparkSendState> = _sendState.asStateFlow()

    private val _receiveState = MutableStateFlow<SparkReceiveState>(SparkReceiveState.Idle)
    val receiveState: StateFlow<SparkReceiveState> = _receiveState.asStateFlow()

    private val _pendingLnInvoice = MutableStateFlow<SparkPendingLnInvoice?>(null)
    val pendingLnInvoice: StateFlow<SparkPendingLnInvoice?> = _pendingLnInvoice.asStateFlow()

    private val _events = MutableSharedFlow<SparkEvent>(extraBufferCapacity = 32)
    val events: SharedFlow<SparkEvent> = _events.asSharedFlow()

    private val _sparkTransactionLabels = MutableStateFlow<Map<String, String>>(emptyMap())
    val sparkTransactionLabels: StateFlow<Map<String, String>> = _sparkTransactionLabels.asStateFlow()
    private val _sparkAddressLabels = MutableStateFlow<Map<String, String>>(emptyMap())
    val sparkAddressLabels: StateFlow<Map<String, String>> = _sparkAddressLabels.asStateFlow()
    private val _sparkTransactionSources = MutableStateFlow<Map<String, String>>(emptyMap())
    val sparkTransactionSources: StateFlow<Map<String, String>> = _sparkTransactionSources.asStateFlow()

    private val _loadedWalletId = MutableStateFlow<String?>(null)
    val loadedWalletId: StateFlow<String?> = _loadedWalletId.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()
    private val _isConnecting = MutableStateFlow(false)
    val isConnecting: StateFlow<Boolean> = _isConnecting.asStateFlow()

    private val _exitFlow = MutableStateFlow<SparkExitFlowState>(SparkExitFlowState.Idle)
    val exitFlow: StateFlow<SparkExitFlowState> = _exitFlow.asStateFlow()

    private val _isExitBackupStale = MutableStateFlow(false)
    val isExitBackupStale: StateFlow<Boolean> = _isExitBackupStale.asStateFlow()

    private val mutex = Mutex()
    private val reconnectMutex = Mutex()

    // Never replaced (even on wallet detach): swapping the mutex would let an
    // in-flight refresh for the old wallet run concurrently with the new one.
    private val refreshMutex = Mutex()
    // Serializes prepare→send pairs so two concurrent withdrawals/sends cannot
    // interleave and send each other's prepared payment.
    private val sendMutex = Mutex()
    // Serializes unilateral-exit quote→build pairs; an exit build must always
    // use the quote it was reviewed against.
    private val exitMutex = Mutex()
    private val eventScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var sdk: BreezSdk? = null
    private var listenerId: String? = null
    private var preparedSend: PreparedSparkSend? = null
    private var preparedExitQuote: PrepareUnilateralExitResponse? = null
    // UI copy of the quote the user reviewed. Captured before Building so the
    // built set keeps the reviewed fee rate, destination, and funding splits
    // (reading them back from _exitFlow after it flips to Building loses them).
    private var preparedExitUiQuote: SparkExitQuote? = null
    // Funding weight assumption the prepared quote was priced with (explicit
    // weight or P2WPKH placeholder). A build with heavier funding would
    // under-price the CPFP leg, so it must re-quote instead of signing.
    private var preparedExitFundingWeight: Long? = null
    /**
     * Monotonic generation of [_exitFlow] writes. A slow `check` must never
     * apply (or persist) a stale result over a newer build/rebuild/quote that
     * landed while it was reading the chain — it drops its result instead.
     * Every exit transition (including wallet-switch resets) goes through
     * [setExitFlow] so nothing can bypass the guard.
     */
    private val exitFlowGeneration = java.util.concurrent.atomic.AtomicLong(0L)

    /** All unilateral-exit flow transitions go through here (see [exitFlowGeneration]). */
    private fun setExitFlow(next: SparkExitFlowState) {
        exitFlowGeneration.incrementAndGet()
        _exitFlow.value = next
    }

    private var depositsRotatedForAddress: Set<String> = emptySet()
    private var localPendingDeposits: List<SparkUnclaimedDeposit> = emptyList()
    private var recoverySyncCompletedForWalletId: String? = null
    private var reconnectInProgress = false

    // prepareSendPayment is network-bound; cache + coalesce identical quote requests.
    private val onchainFeeQuoteCacheMutex = Mutex()
    private val onchainFeeQuoteCache =
        LinkedHashMap<OnchainFeeQuoteCacheKey, CachedOnchainFeeQuotes>(16, 0.75f, true)
    private val onchainFeeQuoteInflight =
        HashMap<OnchainFeeQuoteCacheKey, kotlinx.coroutines.CompletableDeferred<List<SparkOnchainFeeQuote>>>()

    /**
     * Monotonic load request counter. A rapid A→B→A switch can leave two
     * `connect()` calls in flight (the switch join times out after 2s and the
     * loser proceeds anyway); the loser must never publish last-writer-wins
     * over the newer request — it disconnects its SDK and yields instead.
     */
    private val loadGeneration = java.util.concurrent.atomic.AtomicLong(0L)

    suspend fun loadWallet(walletId: String) = withContext(Dispatchers.IO) {
        val generation = loadGeneration.incrementAndGet()
        var refreshMode = SparkRefreshMode.ExplicitSdkSync
        var sdkToDisconnect: SparkSdkHandle? = null
        var walletIdToConnect: String? = null
        val existingSdk =
            mutex.withLock {
                if (_loadedWalletId.value == walletId && sdk != null) {
                    refreshMode =
                        if (SparkSyncPolicy.shouldSyncWallet(
                            currentLoadedWalletId = _loadedWalletId.value,
                            requestedWalletId = walletId,
                            recoverySyncCompletedForWalletId = recoverySyncCompletedForWalletId,
                            hasSdk = sdk != null,
                        )) {
                            SparkRefreshMode.ExplicitSdkSync
                        } else {
                            SparkRefreshMode.ReadCached
                        }
                    return@withLock sdk
                }

                sdkToDisconnect = detachWalletLocked()
                applyLoadingSparkStateLocked(walletId)
                walletIdToConnect = walletId
                null
            }
        if (existingSdk != null) {
            refreshState(refreshMode)
            return@withContext
        }
        // Await (bounded 15s) before opening the new wallet's DB: the old SDK
        // may still hold the SQLite lock on filesDir/spark/<id>, and a
        // background fire-and-forget disconnect races the connect.
        sdkToDisconnect?.awaitDisconnect()
        val connectedSdk = walletIdToConnect?.let { connectWalletLocked(it, generation = generation) }
        connectedSdk?.let { registerListenerForSdk(it) }
        refreshState(refreshMode)
    }

    suspend fun reconnectWallet() = withContext(Dispatchers.IO) {
        if (!SparkSyncPolicy.shouldForceReconnectOnNetworkChange(loadedWalletId = _loadedWalletId.value)) {
            return@withContext
        }
        reconnectMutex.withLock {
            if (reconnectInProgress) return@withLock
            reconnectInProgress = true
            var reconnected = false
            try {
                val walletId = mutex.withLock { _loadedWalletId.value } ?: return@withLock
                recoverySyncCompletedForWalletId = null
                mutex.withLock {
                    applyLoadingSparkStateLocked(walletId)
                }
                val handle = mutex.withLock { softDetachSdkLocked(markDisconnected = false) }
                handle?.awaitDisconnect()
                // Same-generation reconnect: not a new load request, so the
                // switch guard must not treat it as stale if a load lands
                // concurrently — the load bumps the generation and wins.
                val generation = loadGeneration.get()
                val connectedSdk = connectWalletLocked(walletId, showConnectingStatus = false, generation = generation)
                reconnected = true
                registerListenerForSdk(connectedSdk)
                refreshState(SparkRefreshMode.ExplicitSdkSync, allowReconnectOnFailure = false)
            } finally {
                if (!reconnected) {
                    mutex.withLock {
                        if (sdk == null) {
                            _isConnected.value = false
                            _isConnecting.value = false
                            _sparkState.value = _sparkState.value.copy(isSyncing = false)
                        }
                    }
                }
                reconnectInProgress = false
            }
        }
    }

    suspend fun unloadWallet() = withContext(Dispatchers.IO) {
        val sdkToDisconnect = mutex.withLock { detachWalletLocked() }
        sdkToDisconnect?.disconnectInBackground()
    }

    fun clearWalletDisplayState() {
        val walletId = _loadedWalletId.value ?: _sparkState.value.walletId
        _loadedWalletId.value = null
        _isConnected.value = false
        _isConnecting.value = false
        _sendState.value = SparkSendState.Idle
        _receiveState.value = SparkReceiveState.Idle
        // Exit state is per-wallet: a stale InProgress from the previous
        // wallet must never paint (or build) on the next one. The persisted
        // tx set survives per wallet id, so restoreSparkExit() repaints it.
        preparedExitQuote = null
        preparedExitUiQuote = null
        preparedExitFundingWeight = null
        setExitFlow(SparkExitFlowState.Idle)
        _isExitBackupStale.value = false
        // In-memory only: the SecureStorage pending invoice survives so the
        // Receive screen can repaint it when this wallet is loaded again.
        _pendingLnInvoice.value = null
        // Keep last-known balance/history offline (cache), same as L1 local DB paint.
        if (walletId.isNullOrBlank()) {
            _sparkTransactionLabels.value = emptyMap()
            _sparkAddressLabels.value = emptyMap()
            _sparkTransactionSources.value = emptyMap()
            _sparkState.value = SparkWalletState(isInitialized = true)
        } else {
            applyDisconnectedSparkStateLocked(walletId)
        }
    }

    fun markLoadFailed(walletId: String, message: String) {
        _loadedWalletId.value = walletId
        _isConnected.value = false
        _isConnecting.value = false
        val currentState = _sparkState.value.takeIf { it.walletId == walletId && it.isInitialized }
        _sparkState.value =
            currentState?.copy(
                isSyncing = false,
                error = message,
            ) ?: SparkWalletState(
                walletId = walletId,
                isInitialized = true,
                error = message,
            )
    }

    fun addLocalPendingDeposit(
        txid: String,
        amountSats: Long,
        address: String,
    ) {
        // Reject corrupt entries before they poison the cache/pending list.
        if (txid.isBlank() || amountSats <= 0L || address.isBlank()) return
        if (txid.trim().length != 64 || txid.trim().any { it !in '0'..'9' && it !in 'a'..'f' && it !in 'A'..'F' }) return
        val walletId = _loadedWalletId.value
        if (walletId != null && sparkDepositHistoryId(txid, 0u) in secureStorage.getHiddenSparkHistoryItemIds(walletId)) {
            return
        }
        _loadedWalletId.value?.let { walletId ->
            secureStorage.saveSparkDepositAddress(walletId, txid, address)
        }
        val pendingDeposit = SparkUnclaimedDeposit(
            txid = txid,
            vout = 0u,
            amountSats = amountSats,
            isMature = false,
            timestamp = System.currentTimeMillis(),
            address = address,
            // Sole caller is the L1→Spark swap execute path: the deposit is a
            // center-Swap peg from birth, before any L1 link exists to prove it.
            isSwapDeposit = true,
        )
        _loadedWalletId.value?.let { walletId ->
            secureStorage.saveSparkPendingDeposit(walletId, pendingDeposit)
        }
        localPendingDeposits =
            (listOf(pendingDeposit) + localPendingDeposits)
                .distinctBy { sparkDepositKey(it) }
        _sparkState.value =
            _sparkState.value.copy(
                unclaimedDeposits = mergeLocalPendingDeposits(_sparkState.value.unclaimedDeposits),
            )
        _loadedWalletId.value?.let { walletId ->
            secureStorage.saveSparkWalletStateCache(walletId, _sparkState.value)
        }
    }

    suspend fun deleteWalletData(walletId: String) = withContext(Dispatchers.IO) {
        var sdkToDisconnect: SparkSdkHandle? = null
        mutex.withLock {
            if (_loadedWalletId.value == walletId) {
                sdkToDisconnect = detachWalletLocked()
            }
            secureStorage.clearSparkWalletStateCache(walletId)
            secureStorage.clearSparkPendingLnInvoice(walletId)
            secureStorage.clearSparkExitTxSet(walletId)
            secureStorage.clearSparkExitFunding(walletId)
            secureStorage.clearSparkExitBackup(walletId)
            if (_loadedWalletId.value == null) {
                _isExitBackupStale.value = false
            }
            File(context.filesDir, "spark/$walletId").deleteRecursively()
        }
        sdkToDisconnect?.disconnectInBackground()
    }

    /**
     * Manual refresh (pull-to-refresh): returns true on success, false when the
     * sync failed. A failed manual refresh attempts one SDK reconnect so a
     * dead socket heals without waiting for 3 heartbeat misses.
     */
    suspend fun refreshState(): Boolean =
        refreshState(SparkSyncPolicy.modeForManualRefresh(), allowReconnectOnFailure = true)

    /**
     * Heartbeat probe: returns true only when the wallet actually synced.
     * Failures return false (never throw) so the ViewModel can count
     * consecutive misses and reconnect. Previously this always returned
     * normally even on error, so dead sockets never healed.
     */
    suspend fun refreshStateForHeartbeat(): Boolean = refreshState(SparkSyncPolicy.modeForHeartbeat())

    private suspend fun refreshFromEvent(): Boolean = refreshState(SparkRefreshMode.ReadCached)

    suspend fun receive(
        kind: SparkReceiveKind,
        amountSats: Long? = null,
        description: String = "",
        forceNew: Boolean = false,
    ) =
        withContext(Dispatchers.IO) {
            // Negative amounts would wrap via toULong()/BigInteger into a huge
            // invoice instead of failing — reject locally before touching SDK.
            if (amountSats != null && amountSats <= 0L) {
                _receiveState.value = SparkReceiveState.Error("Invalid amount")
                return@withContext
            }
            val walletId = _loadedWalletId.value
            if (kind == SparkReceiveKind.BITCOIN_ADDRESS && !forceNew && walletId != null) {
                secureStorage.getSparkOnchainDepositAddress(walletId)?.let { cachedAddress ->
                    val normalizedAddress = normalizeSparkAddressLabelRef(cachedAddress)
                    if (description.isNotBlank()) {
                        saveSparkAddressLabel(walletId, normalizedAddress, description)
                    }
                    _receiveState.value = SparkReceiveState.Ready(
                        kind = kind,
                        paymentRequest = normalizedAddress,
                        feeSats = 0,
                    )
                    return@withContext
                }
            }

            if (kind == SparkReceiveKind.BOLT11_INVOICE && !forceNew && walletId != null) {
                if (paintPendingLnInvoice(walletId)) {
                    return@withContext
                }
            }

            _receiveState.value = SparkReceiveState.Loading
            try {
                val response = sdkOrThrow().receivePayment(
                    ReceivePaymentRequest(
                        paymentMethod = when (kind) {
                            SparkReceiveKind.SPARK_ADDRESS -> ReceivePaymentMethod.SparkAddress
                            SparkReceiveKind.SPARK_INVOICE -> ReceivePaymentMethod.SparkInvoice(
                                amount = amountSats?.toSparkBigInteger(),
                                tokenIdentifier = null,
                                expiryTime = null,
                                description = description.takeIf { it.isNotBlank() },
                                senderPublicKey = null,
                            )
                            SparkReceiveKind.BITCOIN_ADDRESS -> ReceivePaymentMethod.BitcoinAddress(newAddress = true)
                            SparkReceiveKind.BOLT11_INVOICE -> ReceivePaymentMethod.Bolt11Invoice(
                                description = description,
                                amountSats = amountSats?.toULong(),
                                expirySecs = 3600u,
                                paymentHash = null,
                                // External Spark recipient identity; null = this wallet.
                                receiverIdentityPublicKey = null,
                            )
                        },
                    ),
                )
                val paymentRequest =
                    if (kind == SparkReceiveKind.BITCOIN_ADDRESS) {
                        normalizeSparkAddressLabelRef(response.paymentRequest)
                    } else {
                        response.paymentRequest
                    }
                if (kind == SparkReceiveKind.BITCOIN_ADDRESS && walletId != null) {
                    secureStorage.setSparkOnchainDepositAddress(walletId, paymentRequest)
                }
                if (kind == SparkReceiveKind.BOLT11_INVOICE && walletId != null) {
                    val pending =
                        SparkPendingLnInvoice(
                            paymentRequest = paymentRequest,
                            amountSats = amountSats?.takeIf { it > 0L },
                            description = description,
                            createdAtMs = System.currentTimeMillis(),
                        )
                    secureStorage.setSparkPendingLnInvoice(walletId, pending)
                    _pendingLnInvoice.value = pending
                }
                if (walletId != null && description.isNotBlank()) {
                    saveSparkAddressLabel(walletId, paymentRequest, description)
                }
                _receiveState.value = SparkReceiveState.Ready(
                    kind = kind,
                    paymentRequest = paymentRequest,
                    feeSats = response.fee.toLongSafe(),
                )
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _receiveState.value = SparkReceiveState.Error(e.safeMessage("Spark receive failed"))
            }
        }

    suspend fun prepareSend(
        paymentRequest: String,
        amountSats: Long?,
        onchainFeeSpeed: SparkOnchainFeeSpeed = SparkOnchainFeeSpeed.FAST,
        useAllFunds: Boolean = false,
        label: String? = null,
    ) = withContext(Dispatchers.IO) {
        _sendState.value = SparkSendState.Preparing
        try {
            // Serialize with send paths: concurrent prepares must not overwrite
            // each other's prepared payment (A prepares, B overwrites, A sends B's).
            val preview = sendMutex.withLock {
                prepareSendPreviewInternal(paymentRequest, amountSats, onchainFeeSpeed, useAllFunds, label)
            }
            _sendState.value = preview
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            _sendState.value = SparkSendState.Error(e.safeMessage("Spark send preview failed"))
        }
    }

    /**
     * Throwing preview variant for flows that need the [SparkSendState.Preview]
     * value (Transfer dry-run). Unlike [prepareSend] it rethrows so callers
     * get the failure, but it still paints [_sendState] = Error first so the
     * Send screen never sticks on a stale Preparing/Preview.
     */
    suspend fun prepareSendPreview(
        paymentRequest: String,
        amountSats: Long?,
        onchainFeeSpeed: SparkOnchainFeeSpeed = SparkOnchainFeeSpeed.FAST,
        useAllFunds: Boolean = false,
        label: String? = null,
    ): SparkSendState.Preview =
        withContext(Dispatchers.IO) {
            try {
                val preview = sendMutex.withLock {
                    prepareSendPreviewInternal(paymentRequest, amountSats, onchainFeeSpeed, useAllFunds, label)
                }
                _sendState.value = preview
                preview
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _sendState.value = SparkSendState.Error(e.safeMessage("Spark send preview failed"))
                throw e
            }
        }

    suspend fun getOnchainFeeQuotes(
        paymentRequest: String,
        amountSats: Long,
        useAllFunds: Boolean = false,
    ): List<SparkOnchainFeeQuote> =
        withContext(Dispatchers.IO) {
            val normalizedRequest = paymentRequest.trim()
            if (normalizedRequest.isEmpty() || amountSats <= 0L) return@withContext emptyList()
            val cacheKey = OnchainFeeQuoteCacheKey(normalizedRequest, amountSats, useAllFunds)

            data class QuoteLookup(
                val cached: List<SparkOnchainFeeQuote>?,
                val deferred: kotlinx.coroutines.CompletableDeferred<List<SparkOnchainFeeQuote>>?,
                val isOwner: Boolean,
            )

            val lookup =
                onchainFeeQuoteCacheMutex.withLock {
                    val now = System.currentTimeMillis()
                    val cached = onchainFeeQuoteCache[cacheKey]
                    if (cached != null && now - cached.fetchedAtMs <= ONCHAIN_FEE_QUOTE_CACHE_TTL_MS) {
                        return@withLock QuoteLookup(cached.quotes, null, false)
                    }
                    onchainFeeQuoteInflight[cacheKey]?.let {
                        return@withLock QuoteLookup(null, it, false)
                    }
                    val deferred =
                        kotlinx.coroutines.CompletableDeferred<List<SparkOnchainFeeQuote>>()
                    onchainFeeQuoteInflight[cacheKey] = deferred
                    QuoteLookup(null, deferred, true)
                }

            lookup.cached?.let { return@withContext it }
            val deferred = lookup.deferred ?: return@withContext emptyList()
            if (!lookup.isOwner) {
                // Bounded wait: a hung owner (SDK stall) must not park
                // coalesced callers forever — cache TTL doesn't unblock waiters.
                return@withContext withTimeout(SPARK_FEE_QUOTE_TIMEOUT_MS) { deferred.await() }
            }

            try {
                val response =
                    withTimeout(SPARK_FEE_QUOTE_TIMEOUT_MS) {
                        sdkOrThrow().prepareSendPayment(
                            PrepareSendPaymentRequest(
                                paymentRequest = PaymentRequest.Input(normalizedRequest),
                                amount = amountSats.toSparkBigInteger(),
                                tokenIdentifier = null,
                                conversionOptions = null,
                                feePolicy =
                                    if (useAllFunds) {
                                        FeePolicy.FEES_INCLUDED
                                    } else {
                                        FeePolicy.FEES_EXCLUDED
                                    },
                            ),
                        )
                    }
                val quotes = response.paymentMethod.onchainFeeQuotes()
                onchainFeeQuoteCacheMutex.withLock {
                    onchainFeeQuoteCache[cacheKey] =
                        CachedOnchainFeeQuotes(quotes, System.currentTimeMillis())
                    while (onchainFeeQuoteCache.size > ONCHAIN_FEE_QUOTE_CACHE_MAX) {
                        val eldest = onchainFeeQuoteCache.entries.iterator()
                        if (!eldest.hasNext()) break
                        eldest.next()
                        eldest.remove()
                    }
                    onchainFeeQuoteInflight.remove(cacheKey)
                }
                deferred.complete(quotes)
                quotes
            } catch (e: CancellationException) {
                onchainFeeQuoteCacheMutex.withLock {
                    onchainFeeQuoteInflight.remove(cacheKey)
                }
                deferred.cancel(e)
                throw e
            } catch (e: Exception) {
                onchainFeeQuoteCacheMutex.withLock {
                    onchainFeeQuoteInflight.remove(cacheKey)
                }
                deferred.completeExceptionally(e)
                throw e
            }
        }

    suspend fun getRecommendedFeeEstimates(): FeeEstimates =
        withContext(Dispatchers.IO) {
            val fees = sdkOrThrow().recommendedFees()
            FeeEstimates(
                fastestFee = fees.fastestFee.toDouble(),
                halfHourFee = fees.halfHourFee.toDouble(),
                hourFee = fees.hourFee.toDouble(),
                minimumFee = fees.minimumFee.toDouble(),
                // Provenance: these come from the Spark SDK operator feed,
                // not mempool.space (Tor/clearnet routing is the SDK's).
                source = FeeEstimateSource.SPARK_SERVICE,
            )
        }

    // ---- Unilateral exit (emergency on-chain recovery) ----
    //
    // The SDK builds and signs the transaction set; broadcasting stays manual
    // (tree-tx + CPFP pairs need package relay). Funding UTXOs always come
    // from the L1 wallet as Custom inputs (script + upper-bound weight), so
    // no key material is ever extracted — signing runs through the BDK PSBT
    // path via [signPsbt].

    /**
     * Instant-claim quote for a pending deposit (Breez 0.25+): what the
     * deposit would credit right now and the spread fee for skipping the
     * maturity wait. Returns null when the provider offers no early claim
     * (matured, unreachable, or uneconomical) — the deposit then matures
     * normally and the UI simply offers nothing.
     */
    suspend fun fetchDepositClaimQuote(
        txid: String,
        vout: UInt,
    ): SparkDepositClaimQuote? =
        withContext(Dispatchers.IO) {
            val response =
                sdkOrThrow().fetchClaimDepositQuote(FetchClaimDepositQuoteRequest(txid, vout))
            val instant = response.instant ?: return@withContext null
            val amountSats = response.amountSats.toLongSafe()
            val creditSats = instant.creditAmountSats.toLongSafe()
            val feeSats = instant.feeSats.toLongSafe()
            // A corrupt/overflowing SDK quote must never paint: coerce-and-hide
            // beats displaying wrapped values (claim-time ceiling re-checks).
            if (!SparkUnilateralExitPolicy.isPlausibleClaimQuote(amountSats, creditSats, feeSats)) {
                return@withContext null
            }
            SparkDepositClaimQuote(
                txid = txid,
                vout = vout,
                amountSats = amountSats,
                confirmations = response.confirmations.toLongSafe(),
                creditSats = creditSats,
                feeSats = feeSats,
                feeRateSatPerVb = instant.feeRateSatPerVbyte.toLongSafe(),
                isEstimate = instant.isEstimate,
            )
        }

    /**
     * Claims a pending deposit immediately at the reviewed instant quote.
     * [maxFeeSats] must be the accepted quote's fee: the claim fails rather
     * than paying more, and anything at or above the deposit value is refused
     * locally. Refreshes state so the claimed deposit leaves the pending list.
     */
    suspend fun claimDepositNow(
        txid: String,
        vout: UInt,
        amountSats: Long,
        maxFeeSats: Long,
    ) {
        require(amountSats > 0L) { "Invalid deposit amount" }
        require(maxFeeSats > 0L) { "Claim fee ceiling must be positive" }
        SparkUnilateralExitPolicy.validateClaimCeiling(amountSats, maxFeeSats)?.let {
            throw IllegalArgumentException(it)
        }
        withContext(Dispatchers.IO) {
            sdkOrThrow().claimDeposit(ClaimDepositRequest(txid, vout, MaxFee.Fixed(maxFeeSats.toULong())))
            refreshState()
        }
    }

    suspend fun quoteSparkExit(
        feeRateSatPerVb: Long,
        destination: String,
        leafScope: SparkExitLeafScope = SparkExitLeafScope.AUTO,
        leafIds: List<String> = emptyList(),
        fundingScriptHex: String? = null,
        fundingWeight: Long? = null,
    ): SparkExitQuote =
        withContext(Dispatchers.IO) {
            setExitFlow(SparkExitFlowState.Quoting)
            // A wallet switch/unload while the SDK prices must not paint (or
            // return for review) a quote belonging to another wallet.
            val generation = exitFlowGeneration.get()
            try {
                // Sync first: exit-chain data is fetched during syncWallet, and
                // heartbeat/loads are cached reads — quoting without a sync
                // prices stale leaves (e.g. funds received since the last
                // manual refresh silently missing from the quote). Single
                // bounded attempt, no auto-reconnect cascade: a dead socket is
                // the heartbeat's job to heal; the error tells the user to
                // retry once reconnected.
                val synced =
                    refreshState(SparkRefreshMode.ExplicitSdkSync, allowReconnectOnFailure = false)
                if (!synced) {
                    throw IllegalStateException("Spark sync failed — reconnect and retry")
                }
                if (generation != exitFlowGeneration.get()) {
                    throw IllegalStateException("Exit state changed during quote - re-quote if needed")
                }
                val quote =
                    exitMutex.withLock {
                        prepareExitLocked(
                            feeRateSatPerVb = feeRateSatPerVb,
                            destination = destination,
                            leafScope = leafScope,
                            leafIds = leafIds,
                            fundingScriptHex = fundingScriptHex,
                            fundingWeight = fundingWeight,
                        )
                    }
                if (generation != exitFlowGeneration.get()) {
                    throw IllegalStateException("Exit state changed during quote - re-quote if needed")
                }
                setExitFlow(SparkExitFlowState.QuoteReady(quote))
                quote
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                if (generation == exitFlowGeneration.get()) {
                    setExitFlow(SparkExitFlowState.Failed(e.safeMessage("Spark exit quote failed")))
                }
                throw e
            }
        }

    /**
     * SDK prepare step. Assumes [exitMutex] is held so a quote→build pair
     * cannot interleave with another quote. Records both the SDK response
     * and the reviewed UI quote for [buildExitLocked] to consume.
     */
    private suspend fun prepareExitLocked(
        feeRateSatPerVb: Long,
        destination: String,
        leafScope: SparkExitLeafScope,
        leafIds: List<String>,
        fundingScriptHex: String?,
        fundingWeight: Long?,
    ): SparkExitQuote {
        // Fail before the SDK prices anything: a typo'd sweep destination
        // would otherwise burn the quote/build fee or strand funds.
        // Network-exact validation (mainnet vs testnet) runs at the UI
        // boundary against the loaded L1 wallet; this checksum gate catches
        // malformed input even if that check is bypassed.
        require(SparkUnilateralExitPolicy.isPlausibleBitcoinAddress(destination)) {
            "Invalid Spark exit destination address"
        }
        val flooredRate = SparkUnilateralExitPolicy.floorFeeRateSatPerVb(feeRateSatPerVb)
        val quoted =
            sdkOrThrow().prepareUnilateralExit(
                PrepareUnilateralExitRequest(
                    feeRateSatPerVbyte = flooredRate.toULong(),
                    fundingKind = exitFundingKind(fundingScriptHex, fundingWeight),
                    destination = destination,
                    selection =
                        when (leafScope) {
                            SparkExitLeafScope.AUTO -> ExitLeafSelection.Auto
                            SparkExitLeafScope.SPECIFIC ->
                                ExitLeafSelection.Specific(
                                    leafIds,
                                )
                        },
                ),
            )
        preparedExitQuote = quoted
        // Persist the floored rate (what the SDK actually priced), not the
        // raw request, so review → build → bump all agree on the fee.
        val quote = quoted.toSparkExitQuote(destination, flooredRate)
        preparedExitUiQuote = quote
        // Record the funding-weight assumption so the build can refuse a
        // heavier funding set that the placeholder quote under-priced.
        preparedExitFundingWeight =
            fundingWeight?.takeIf { it > 0L }
                ?: fundingScriptHex?.let {
                    SparkUnilateralExitPolicy.classifyFundingScript(it)?.let { cls ->
                        SparkUnilateralExitPolicy.signedInputWeightFor(cls)
                    }
                }
                ?: SparkUnilateralExitPolicy.P2WPKH_SIGNED_INPUT_WEIGHT
        // Note: no flow paint here — callers (quote/rebuild) paint after
        // passing their generation guards, so a wallet switch mid-prepare
        // can never surface another wallet's quote.
        return quote
    }

    /**
     * Builds the signed exit set from the in-memory [quoteSparkExit] result.
     * [reviewedQuote] must be the exact quote the user reviewed: the build
     * refuses to consume a prepared response that no longer matches it (a
     * concurrent re-quote swapping the prepared state fails loudly instead of
     * signing a non-reviewed set). [liveUtxos] is the current L1 wallet view
     * used to reject stale funding (spent/frozen/unconfirmed) before the SDK
     * sees it. Throws when the quote expired (e.g. process death) — callers
     * re-quote from persisted params and retry.
     */
    suspend fun buildSparkExit(
        fundingInputs: List<SparkExitFundingUtxo>,
        reviewedQuote: SparkExitQuote,
        liveUtxos: List<SparkExitLiveUtxo>,
        signPsbt: suspend (ByteArray) -> ByteArray,
    ): SparkExitFlowState.InProgress =
        withContext(Dispatchers.IO) {
            setExitFlow(SparkExitFlowState.Building)
            // A wallet switch/unload mid-build must neither persist for nor
            // paint another wallet: the signer already refuses cross-wallet
            // signatures, and this guard drops the whole result.
            val generation = exitFlowGeneration.get()
            try {
                val walletId = _loadedWalletId.value
                val built =
                    exitMutex.withLock {
                        buildExitLocked(fundingInputs, reviewedQuote, liveUtxos, signPsbt)
                    }
                if (generation != exitFlowGeneration.get() || _loadedWalletId.value != walletId) {
                    throw IllegalStateException("Exit state changed during build - review and retry")
                }
                val txs = built.response.transactions.map { it.toSparkExitTx() }
                if (walletId != null) {
                    secureStorage.saveSparkExitTxSet(walletId, built.uiQuote, txs)
                    secureStorage.saveSparkExitFunding(walletId, built.fundingInputs)
                }
                val inProgress =
                    SparkExitFlowState.InProgress(
                        txs = txs,
                        recoverableValueSats = built.uiQuote.recoverableValueSats,
                        totalFeeSats = built.uiQuote.totalFeeSats,
                        feeRateSatPerVb = built.uiQuote.feeRateSatPerVb,
                        destination = built.uiQuote.destination,
                        leafIds = built.uiQuote.leafIds,
                        cpfpFeeSats = built.uiQuote.cpfpFeeSats,
                        fanoutFeeSats = built.uiQuote.fanoutFeeSats,
                        sweepFeeSats = built.uiQuote.sweepFeeSats,
                    )
                setExitFlow(inProgress)
                inProgress
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                // The prepared quote survives a failed build (it is only
                // consumed on success), so drop back to it instead of losing
                // the reviewed quote — the user can fix funding and retry.
                // Never repaint over a wallet switch that landed meanwhile.
                if (generation == exitFlowGeneration.get()) {
                    val retryQuote = preparedExitUiQuote
                    setExitFlow(
                        if (retryQuote != null) {
                            SparkExitFlowState.QuoteReady(retryQuote)
                        } else {
                            SparkExitFlowState.Failed(e.safeMessage("Spark exit build failed"))
                        },
                    )
                }
                throw e
            }
        }

    private data class BuiltSparkExit(
        val response: UnilateralExitResponse,
        val uiQuote: SparkExitQuote,
        val fundingInputs: List<SparkExitFundingUtxo>,
    )

    /**
     * SDK build step. Assumes [exitMutex] is held and pairs with the
     * [prepareExitLocked] result recorded under the same lock acquisition, so
     * a concurrent quote cannot swap the prepared response mid-build. The
     * reviewed UI quote travels with the build explicitly — it must not be
     * re-read from [_exitFlow] (already [SparkExitFlowState.Building]) — and
     * must match the prepared quote, otherwise the build refuses to sign.
     */
    private suspend fun buildExitLocked(
        fundingInputs: List<SparkExitFundingUtxo>,
        reviewedQuote: SparkExitQuote,
        liveUtxos: List<SparkExitLiveUtxo>,
        signPsbt: suspend (ByteArray) -> ByteArray,
    ): BuiltSparkExit {
        val prepared =
            preparedExitQuote
                ?: throw IllegalStateException("Exit quote expired — re-quote before building")
        val uiQuote =
            preparedExitUiQuote
                ?: throw IllegalStateException("Exit quote expired — re-quote before building")
        // Quote→build binding: a re-quote between review and build swaps the
        // prepared response; building it would sign a non-reviewed set.
        require(SparkUnilateralExitPolicy.quotesMatch(reviewedQuote, uiQuote)) {
            "Exit quote changed since review — re-review before building"
        }
        require(fundingInputs.isNotEmpty()) { "Exit funding UTXO required" }
        // Live-wallet re-validation: the picker snapshot can go stale (funding
        // spent, frozen, or reorged out). Fail here with the conflicting
        // outpoint named instead of inside the SDK signer.
        SparkUnilateralExitPolicy.validateFundingAgainstLive(fundingInputs, liveUtxos)?.let {
            throw IllegalArgumentException(it)
        }
        // Repository build gate (mirrors the quote-card gate): the reviewed
        // quote determines the funding mode, so an underfunded selection can
        // never reach the SDK even if the UI check is bypassed.
        require(
            SparkUnilateralExitPolicy.isExitBuildFundingSufficient(
                quote = uiQuote,
                selectedCount = fundingInputs.size,
                selectedTotalSats = fundingInputs.sumOf { it.valueSats },
            ),
        ) { "Selected funding does not cover the quoted exit fee" }
        // Placeholder-quote guard: the initial quote is priced with a P2WPKH
        // weight when no funding is selected yet. Building it with heavier
        // funding (e.g. P2WSH multisig) would under-price the CPFP leg.
        // Refuse loudly so the caller re-quotes with the exact funding
        // (rebuild path) instead of signing a stale fee.
        val assumedWeight =
            preparedExitFundingWeight
                ?: SparkUnilateralExitPolicy.P2WPKH_SIGNED_INPUT_WEIGHT
        val heaviestFunding =
            fundingInputs.maxOf { input ->
                input.signedInputWeight.takeIf { it > 0L }
                    ?: SparkUnilateralExitPolicy.classifyFundingScript(input.scriptPubkeyHex)?.let { cls ->
                        SparkUnilateralExitPolicy.signedInputWeightFor(cls)
                    }
                    ?: SparkUnilateralExitPolicy.P2WSH_SIGNED_INPUT_WEIGHT
            }
        require(heaviestFunding <= assumedWeight) {
            "Funding exceeds quoted weight — rebuild with selected funding"
        }
        val activeSdk = sdkOrThrow()
        val response =
            activeSdk.unilateralExit(
                UnilateralExitRequest(
                    prepared = prepared,
                    fundingInputs = fundingInputs.map { it.toCpfpInput() },
                ),
                BdkBackedCpfpSigner(signPsbt),
            )
        preparedExitQuote = null
        preparedExitUiQuote = null
        preparedExitFundingWeight = null
        val quote = response.toSparkExitQuote(uiQuote)
        return BuiltSparkExit(response, quote, fundingInputs)
    }

    /**
     * Resume / fee-bump path: re-quotes the same leaves (chain state is
     * re-read, confirmed steps drop out) and rebuilds the unconfirmed
     * remainder. Statuses in the returned set are freshly resolved by the SDK
     * against the chain tip — no manual flags are preserved or needed.
     *
     * The re-quote and rebuild run under a single [exitMutex] acquisition so
     * a concurrent quote cannot swap the prepared response between them.
     * An empty [fundingInputs] falls back to the inputs persisted at build
     * time (covers bump-after-restart, where the UI selection is gone).
     * [liveUtxos] re-validates the effective funding the same way as the
     * initial build; a funding input spent since the last build fails here
     * with the outpoint named instead of inside the SDK signer.
     */
    suspend fun rebuildSparkExit(
        feeRateSatPerVb: Long,
        destination: String,
        leafIds: List<String>,
        fundingInputs: List<SparkExitFundingUtxo>,
        liveUtxos: List<SparkExitLiveUtxo>,
        signPsbt: suspend (ByteArray) -> ByteArray,
    ): SparkExitFlowState.InProgress =
        withContext(Dispatchers.IO) {
            val walletId = _loadedWalletId.value
            setExitFlow(SparkExitFlowState.Building)
            // Same generation guard as the initial build: a wallet switch (or
            // any concurrent exit transition) mid-rebuild must not persist
            // for or paint another wallet.
            val generation = exitFlowGeneration.get()
            try {
                // Sync first (same stale-leaves rationale as the initial
                // quote): the re-quote must see current chain state, not the
                // leaves as of the original build.
                val synced =
                    refreshState(SparkRefreshMode.ExplicitSdkSync, allowReconnectOnFailure = false)
                if (!synced) {
                    throw IllegalStateException("Spark sync failed — reconnect and retry")
                }
                if (generation != exitFlowGeneration.get() || _loadedWalletId.value != walletId) {
                    throw IllegalStateException("Exit state changed during build - review and retry")
                }
                val effectiveFunding =
                    fundingInputs.ifEmpty {
                        walletId?.let { secureStorage.getSparkExitFunding(it) }.orEmpty()
                    }
                SparkUnilateralExitPolicy.validateFundingAgainstLive(effectiveFunding, liveUtxos)?.let {
                    throw IllegalArgumentException(it)
                }
                val merged =
                    exitMutex.withLock {
                        // Re-quote with the exact funding input so the fee the
                        // user reviews matches the built set; first input is
                        // the representative weight for single-UTXO mode.
                        val freshQuote =
                            prepareExitLocked(
                                feeRateSatPerVb = feeRateSatPerVb,
                                destination = destination,
                                leafScope = SparkExitLeafScope.SPECIFIC,
                                leafIds = leafIds,
                                fundingScriptHex = effectiveFunding.firstOrNull()?.scriptPubkeyHex,
                                fundingWeight = effectiveFunding.firstOrNull()?.signedInputWeight,
                            )
                        if (freshQuote.leafIds.isEmpty()) {
                            // Everything resolved on-chain since the last
                            // build: there is nothing to sign. Refresh status
                            // instead of invoking the signer.
                            throw IllegalStateException("Nothing left to exit — check status instead")
                        }
                        val built = buildExitLocked(effectiveFunding, freshQuote, liveUtxos, signPsbt)
                        if (generation != exitFlowGeneration.get() || _loadedWalletId.value != walletId) {
                            throw IllegalStateException("Exit state changed during build - review and retry")
                        }
                        val txs = built.response.transactions.map { it.toSparkExitTx() }
                        if (walletId != null) {
                            secureStorage.saveSparkExitTxSet(walletId, built.uiQuote, txs)
                            secureStorage.saveSparkExitFunding(walletId, built.fundingInputs)
                        }
                        SparkExitFlowState.InProgress(
                            txs = txs,
                            recoverableValueSats = built.uiQuote.recoverableValueSats,
                            totalFeeSats = built.uiQuote.totalFeeSats,
                            feeRateSatPerVb = built.uiQuote.feeRateSatPerVb,
                            destination = built.uiQuote.destination,
                            leafIds = built.uiQuote.leafIds,
                            cpfpFeeSats = built.uiQuote.cpfpFeeSats,
                            fanoutFeeSats = built.uiQuote.fanoutFeeSats,
                            sweepFeeSats = built.uiQuote.sweepFeeSats,
                        )
                    }
                setExitFlow(merged)
                // A rebuild whose set is fully chain-confirmed finishes the
                // exit (e.g. resumed after the sweep confirmed elsewhere).
                refreshExitCompletion()
                merged
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                // A failed fee-bump must not destroy the persisted progress:
                // repaint the stored set (the error still surfaces via the
                // rethrown exception → snackbar) so broadcast tracking survives.
                // Never repaint over a transition that landed meanwhile.
                if (generation == exitFlowGeneration.get()) {
                    val persisted = walletId?.let { secureStorage.getSparkExitTxSet(it) }
                    if (persisted != null) {
                        val (quote, txs) = persisted
                        setExitFlow(
                            SparkExitFlowState.InProgress(
                                txs = txs,
                                recoverableValueSats = quote.recoverableValueSats,
                                totalFeeSats = quote.totalFeeSats,
                                feeRateSatPerVb = quote.feeRateSatPerVb,
                                destination = quote.destination,
                                leafIds = quote.leafIds,
                                cpfpFeeSats = quote.cpfpFeeSats,
                                fanoutFeeSats = quote.fanoutFeeSats,
                                sweepFeeSats = quote.sweepFeeSats,
                            ),
                        )
                    } else {
                        setExitFlow(SparkExitFlowState.Failed(e.safeMessage("Spark exit build failed")))
                    }
                }
                throw e
            }
        }

    /**
     * Follow-up loop (Breez 0.25 `check_unilateral_exit`): reads the persisted
     * exit back against the chain and stores what it returns in its place.
     * Returns the flow state the UI should paint:
     * - `Valid` → refreshed [SparkExitFlowState.InProgress] (broadcast what
     *   is `Ready`, wait on the rest);
     * - `Done` → [SparkExitFlowState.Completed] (persisted set cleared — the
     *   money is at the destination);
     * - `Redo` → [SparkExitFlowState.RedoRequired] (persisted set kept for
     *   reference; rebuild the same leaves to continue — funds are not lost).
     *
     * Needs no wallet leaves, signer, or funding: an exit can be followed from
     * the stored response alone.
     */
    suspend fun checkSparkExit(): SparkExitFlowState =
        withContext(Dispatchers.IO) {
            val walletId =
                _loadedWalletId.value ?: throw IllegalStateException("Spark wallet is not loaded")
            val (quote, txs) =
                secureStorage.getSparkExitTxSet(walletId)
                    ?: throw IllegalStateException("No persisted Spark exit to check")
            if (txs.isEmpty() || quote.leafIds.isEmpty() || quote.recoverableValueSats <= 0L) {
                throw IllegalStateException("Persisted exit is unreadable — rebuild the exit")
            }
            val funding = secureStorage.getSparkExitFunding(walletId)
            val stored =
                runCatching { buildSdkExitForCheck(quote, txs, funding) }.getOrElse {
                    throw IllegalStateException("Persisted exit funding is unreadable — rebuild the exit")
                }
            // Generation guard: a build/rebuild/quote/discard that lands while
            // the chain is being read must win — persisting this stale result
            // over it would destroy the newer recovery set.
            val generation = exitFlowGeneration.get()
            val checked = sdkOrThrow().checkUnilateralExit(CheckUnilateralExitRequest(stored))
            if (generation != exitFlowGeneration.get()) {
                // State moved on (e.g. a fee-bump finished); the newer state
                // already paints — drop this result without persisting.
                return@withContext _exitFlow.value
            }
            val next = applyCheckedExit(walletId, quote, funding, checked.exit, checked.verdict)
            setExitFlow(next)
            next
        }

    /**
     * Rebuilds the SDK-native exit object from the v2 persisted snapshot so
     * `check` can read it against the chain. Our funding list maps 1:1 back
     * to the `Custom` inputs the build was funded with.
     */
    private fun buildSdkExitForCheck(
        quote: SparkExitQuote,
        txs: List<SparkExitTx>,
        funding: List<SparkExitFundingUtxo>,
    ): UnilateralExitResponse =
        UnilateralExitResponse(
            recoverableValueSat = quote.recoverableValueSats.toULong(),
            totalFeeSat = quote.totalFeeSats.toULong(),
            cpfpFeeSat = quote.cpfpFeeSats.toULong(),
            fanoutFeeSat = quote.fanoutFeeSats.toULong(),
            sweepFeeSat = quote.sweepFeeSats.toULong(),
            leaves = quote.leaves.map { UnilateralExitLeaf(it.leafId, it.valueSats.toULong()) },
            transactions = txs.map { it.toSdkExitTransaction() },
            fundingInputs = funding.map { it.toCpfpInput() },
        )

    private fun SparkExitTx.toSdkExitTransaction(): UnilateralExitTransaction =
        UnilateralExitTransaction(
            kind =
                when (kind) {
                    SparkExitTxKind.FAN_OUT -> UnilateralExitTxKind.FAN_OUT
                    SparkExitTxKind.NODE -> UnilateralExitTxKind.NODE
                    SparkExitTxKind.REFUND -> UnilateralExitTxKind.REFUND
                    SparkExitTxKind.SWEEP -> UnilateralExitTxKind.SWEEP
                },
            nodeId = nodeId,
            txid = txid,
            txHex = txHex,
            cpfpTxHex = cpfpTxHex,
            csvTimelockBlocks = csvTimelockBlocks,
            dependsOn = dependsOn,
            status =
                when (status) {
                    SparkExitTxStatus.CONFIRMED ->
                        ExitTransactionStatus.Confirmed(blockHeight)
                    SparkExitTxStatus.READY -> ExitTransactionStatus.Ready
                    SparkExitTxStatus.WAITING_FOR_DEPENDENCIES ->
                        ExitTransactionStatus.WaitingForDependencies
                    SparkExitTxStatus.WAITING_FOR_TIMELOCK ->
                        ExitTransactionStatus.WaitingForTimelock(spendableAtHeight)
                    SparkExitTxStatus.UNVERIFIED -> ExitTransactionStatus.Unverified
                },
        )

    /**
     * Persists the checked exit in place of the stored one and maps the
     * verdict to flow state. [reviewedQuote] carries the presentation context
     * the SDK response lacks (funding splits, fee rate, destination).
     */
    private fun applyCheckedExit(
        walletId: String,
        reviewedQuote: SparkExitQuote,
        funding: List<SparkExitFundingUtxo>,
        checked: UnilateralExitResponse,
        verdict: UnilateralExitVerdict,
    ): SparkExitFlowState {
        // Chain truth from check always wins; keep the reviewed presentation
        // context (funding splits, rate, destination) alongside it.
        val uiQuote = checked.toSparkExitQuote(reviewedQuote)
        val txs = checked.transactions.map { it.toSparkExitTx() }
        return when (verdict) {
            UnilateralExitVerdict.Valid -> {
                secureStorage.saveSparkExitTxSet(walletId, uiQuote, txs)
                secureStorage.saveSparkExitFunding(walletId, funding)
                SparkExitFlowState.InProgress(
                    txs = txs,
                    recoverableValueSats = uiQuote.recoverableValueSats,
                    totalFeeSats = uiQuote.totalFeeSats,
                    feeRateSatPerVb = uiQuote.feeRateSatPerVb,
                    destination = uiQuote.destination,
                    leafIds = uiQuote.leafIds,
                    cpfpFeeSats = uiQuote.cpfpFeeSats,
                    fanoutFeeSats = uiQuote.fanoutFeeSats,
                    sweepFeeSats = uiQuote.sweepFeeSats,
                )
            }
            UnilateralExitVerdict.Done -> {
                // The money is at the destination; the recovery set has served
                // its purpose. The exit-state backup blob is kept (operator-
                // outage recovery is independent of this exit).
                secureStorage.clearSparkExitTxSet(walletId)
                secureStorage.clearSparkExitFunding(walletId)
                val sweepTxid = txs.lastOrNull { it.kind == SparkExitTxKind.SWEEP }?.txid
                SparkExitFlowState.Completed(sweepTxid)
            }
            is UnilateralExitVerdict.Redo -> {
                // The stored exit cannot finish (foreign spend, external fee
                // bump, or funding spent elsewhere). Keep it for reference;
                // rebuilding the same leaves picks up from wherever the money
                // is — funds are not lost. Persist funding too: a rebuild
                // after Redo reuses it when the UI selection is gone, and
                // known-stale funding is caught by live re-validation.
                secureStorage.saveSparkExitTxSet(walletId, uiQuote, txs)
                secureStorage.saveSparkExitFunding(walletId, funding)
                SparkExitFlowState.RedoRequired(
                    reason = SparkExitRedoReason.ON_CHAIN_STATE_DIVERGED,
                    quote = uiQuote,
                )
            }
        }
    }

    /**
     * Chain-truth completion for freshly built sets: clears the persisted
     * recovery set only when every transaction reports chain-confirmed.
     * Steady-state completion flows through [checkSparkExit]'s `Done`
     * verdict; this covers a build that lands fully confirmed (e.g. resumed
     * after the sweep confirmed elsewhere).
     */
    private fun refreshExitCompletion() {
        val current = _exitFlow.value as? SparkExitFlowState.InProgress ?: return
        if (SparkUnilateralExitPolicy.isExitComplete(current.txs)) {
            val sweepTxid = current.txs.lastOrNull { it.kind == SparkExitTxKind.SWEEP }?.txid
            setExitFlow(SparkExitFlowState.Completed(sweepTxid))
            _loadedWalletId.value?.let {
                secureStorage.clearSparkExitTxSet(it)
                secureStorage.clearSparkExitFunding(it)
            }
        }
    }

    /** Persisted funding inputs for signer binding after process death. */
    fun getPersistedExitFunding(): List<SparkExitFundingUtxo> {
        val walletId = _loadedWalletId.value ?: return emptyList()
        return secureStorage.getSparkExitFunding(walletId)
    }

    fun clearSparkExit() {
        preparedExitQuote = null
        preparedExitUiQuote = null
        preparedExitFundingWeight = null
        _loadedWalletId.value?.let {
            secureStorage.clearSparkExitTxSet(it)
            secureStorage.clearSparkExitFunding(it)
            // The backup blob survives a discard (it is operator-outage
            // recovery, independent of the tx set), so re-sync — never reset —
            // the staleness flag here.
            _isExitBackupStale.value = secureStorage.isSparkExitBackupStale(it)
        }
        setExitFlow(SparkExitFlowState.Idle)
    }

    /** Repaints a persisted multi-day exit after wallet load. */
    fun restoreSparkExit() {
        // Never clobber live flow state (e.g. a freshly reviewed quote): the
        // persisted set is only a repaint source when the flow is Idle or a
        // terminal Failed (a prior run's error must not hide persisted progress
        // across restarts — the user can still check/rebuild from it).
        val current = _exitFlow.value
        if (current != SparkExitFlowState.Idle && current !is SparkExitFlowState.Failed) return
        val walletId = _loadedWalletId.value ?: return
        _isExitBackupStale.value = secureStorage.isSparkExitBackupStale(walletId)
        val persisted = secureStorage.getSparkExitTxSet(walletId)
        if (persisted != null) {
            val (quote, txs) = persisted
            setExitFlow(
                SparkExitFlowState.InProgress(
                    txs = txs,
                    recoverableValueSats = quote.recoverableValueSats,
                    totalFeeSats = quote.totalFeeSats,
                    feeRateSatPerVb = quote.feeRateSatPerVb,
                    destination = quote.destination,
                    leafIds = quote.leafIds,
                    cpfpFeeSats = quote.cpfpFeeSats,
                    fanoutFeeSats = quote.fanoutFeeSats,
                    sweepFeeSats = quote.sweepFeeSats,
                ),
            )
            return
        }
        // Legacy (pre-0.25) snapshot: the transactions cannot be checked, but
        // the quote context survives for a rebuild, which resumes from chain
        // state under the 0.25 planner — nothing is lost.
        val legacy = secureStorage.peekSparkExitQuoteAnyVersion(walletId) ?: return
        setExitFlow(
            SparkExitFlowState.RedoRequired(
                reason = SparkExitRedoReason.SCHEMA_UPGRADED,
                quote = legacy,
            ),
        )
    }

    /**
     * Exports the opaque SDK exit-state blob to a private file for
     * operator-outage recovery. Returns bytes written.
     */
    suspend fun exportSparkExitBackup(): Long =
        withContext(Dispatchers.IO) {
            val walletId =
                _loadedWalletId.value ?: throw IllegalStateException("Spark wallet is not loaded")
            val stateHex = exportExitStateHex()
            val dir = File(context.filesDir, "spark-exit-backup").apply { mkdirs() }
            val file = File(dir, "$walletId.exitstate")
            // Atomic write: a crash mid-write must not leave a truncated
            // .exitstate that later imports as corrupt. Write tmp + fsync +
            // rename so readers only ever see the old or the new blob.
            val tmp = File(dir, "$walletId.exitstate.tmp")
            tmp.writeText(stateHex)
            tmp.outputStream().use { it.fd.sync() }
            if (!tmp.renameTo(file)) {
                tmp.copyTo(file, overwrite = true)
                tmp.delete()
            }
            secureStorage.setSparkExitBackupPath(walletId, file.absolutePath)
            secureStorage.setSparkExitBackupStale(walletId, false)
            _isExitBackupStale.value = false
            file.length()
        }

    data class SparkExitImportResult(
        val importedLeaves: Int,
        val skippedForeignLeaves: Int,
        val skippedChains: Int,
        val skippedConflictingLeaves: Int,
    )

    suspend fun importSparkExitBackup(stateHex: String): SparkExitImportResult =
        withContext(Dispatchers.IO) {
            val normalized = SparkUnilateralExitPolicy.normalizeExitStateHex(stateHex)
            // Hold exitMutex while clearing the prepared quote: a concurrent
            // quote/build must not slip a new prepared response between the
            // SDK import and our clear (stale win either way loses money).
            val response = exitMutex.withLock {
                val imported = sdkOrThrow().importUnilateralExitState(ImportUnilateralExitStateRequest(normalized))
                // Imported leaves can obsolete a reviewed-but-unbuilt quote: force
                // a re-quote rather than building from a stale leaf set.
                preparedExitQuote = null
                preparedExitUiQuote = null
                preparedExitFundingWeight = null
                imported
            }
            val current = _exitFlow.value
            if (current is SparkExitFlowState.QuoteReady || current is SparkExitFlowState.Quoting) {
                setExitFlow(SparkExitFlowState.Idle)
            }
            // Freshly imported state supersedes any previously exported blob.
            _loadedWalletId.value?.let { walletId ->
                if (secureStorage.getSparkExitBackupPath(walletId) != null) {
                    secureStorage.setSparkExitBackupStale(walletId, true)
                    _isExitBackupStale.value = true
                }
            }
            SparkExitImportResult(
                importedLeaves = response.importedLeaves.toInt(),
                skippedForeignLeaves = response.skippedForeignLeaves.toInt(),
                skippedChains = response.skippedChains.toInt(),
                skippedConflictingLeaves = response.skippedConflictingLeaves.toInt(),
            )
        }

    /**
     * Raw SDK exit-state hex for the loaded wallet (operator-outage recovery
     * blob). Backs both the private snapshot file and user-chosen copies.
     */
    suspend fun exportExitStateHex(): String =
        withContext(Dispatchers.IO) {
            _loadedWalletId.value ?: throw IllegalStateException("Spark wallet is not loaded")
            sdkOrThrow().exportUnilateralExitState().exitState
        }

    private fun exitFundingKind(
        scriptPubkeyHex: String?,
        weight: Long?,
    ): CpfpFundingKind {
        val scriptClass = scriptPubkeyHex?.let { SparkUnilateralExitPolicy.classifyFundingScript(it) }
        if (scriptClass != null && scriptPubkeyHex != null) {
            val w = weight ?: SparkUnilateralExitPolicy.signedInputWeightFor(scriptClass)
            return CpfpFundingKind.Custom(scriptPubkeyHex.lowercase(), w.toULong())
        }
        // Pre-UTXO informational quote: P2WPKH-class weight placeholder.
        // The binding build always re-quotes with the exact funding input.
        return CpfpFundingKind.P2wpkh
    }

    private fun SparkExitFundingUtxo.toCpfpInput(): CpfpInput {
        require(valueSats > 0L) { "Exit funding value must be positive" }
        val scriptClass =
            SparkUnilateralExitPolicy.classifyFundingScript(scriptPubkeyHex)
                ?: throw IllegalArgumentException("Exit funding UTXO must be native SegWit")
        val weight = signedInputWeight.takeIf { it > 0 }
            ?: SparkUnilateralExitPolicy.signedInputWeightFor(scriptClass)
        return CpfpInput.Custom(
            txid = txid,
            vout = vout,
            value = valueSats.toULong(),
            scriptPubkeyHex = scriptPubkeyHex.lowercase(),
            signedInputWeight = weight.toULong(),
        )
    }

    private fun PrepareUnilateralExitResponse.toSparkExitQuote(
        destination: String,
        feeRateSatPerVb: Long,
    ): SparkExitQuote =
        SparkExitQuote(
            leafIds = leaves.map { it.leafId },
            leaves = leaves.map { SparkExitQuotedLeaf(it.leafId, it.value.toLongExact()) },
            recoverableValueSats = recoverableValueSat.toLongExact(),
            totalFeeSats = totalFeeSat.toLongExact(),
            fanoutFeeSats = fanoutFeeSat.toLongExact(),
            singleUtxoFundingSats = singleUtxoFundingSat.toLongExact(),
            perBranchFunding =
                perBranchFunding.map {
                    SparkExitBranchFunding(it.leafId, it.fundingSat.toLongExact())
                },
            feeRateSatPerVb = feeRateSatPerVb,
            destination = destination,
            cpfpFeeSats = cpfpFeeSat.toLongExact(),
            sweepFeeSats = sweepFeeSat.toLongExact(),
        )

    /**
     * Merges the authoritative SDK build amounts (leaves, recoverable, fees —
     * including the actual CPFP/sweep split) with the reviewed quote's
     * presentation fields (funding splits, fee rate, destination). The SDK
     * build response carries no funding or destination context, so [quoted]
     * must be the [prepareExitLocked] result paired with this build — never
     * re-read from [_exitFlow], which has already flipped to
     * [SparkExitFlowState.Building].
     */
    private fun UnilateralExitResponse.toSparkExitQuote(quoted: SparkExitQuote): SparkExitQuote {
        return SparkExitQuote(
            leafIds = leaves.map { it.leafId },
            leaves = leaves.map { SparkExitQuotedLeaf(it.leafId, it.value.toLongExact()) },
            recoverableValueSats = recoverableValueSat.toLongExact(),
            totalFeeSats = totalFeeSat.toLongExact(),
            fanoutFeeSats = fanoutFeeSat.toLongExact(),
            singleUtxoFundingSats = quoted.singleUtxoFundingSats,
            perBranchFunding = quoted.perBranchFunding,
            feeRateSatPerVb = quoted.feeRateSatPerVb,
            destination = quoted.destination,
            cpfpFeeSats = cpfpFeeSat.toLongExact(),
            sweepFeeSats = sweepFeeSat.toLongExact(),
        )
    }

    private fun breez_sdk_spark.UnilateralExitTransaction.toSparkExitTx(): SparkExitTx {
        val mappedStatus =
            when (val s = status) {
                is ExitTransactionStatus.Confirmed ->
                    SparkExitTxStatus.CONFIRMED to (s.blockHeight to null)
                ExitTransactionStatus.Ready ->
                    SparkExitTxStatus.READY to (null to null)
                ExitTransactionStatus.WaitingForDependencies ->
                    SparkExitTxStatus.WAITING_FOR_DEPENDENCIES to (null to null)
                is ExitTransactionStatus.WaitingForTimelock ->
                    SparkExitTxStatus.WAITING_FOR_TIMELOCK to (null to s.spendableAtHeight)
                ExitTransactionStatus.Unverified ->
                    SparkExitTxStatus.UNVERIFIED to (null to null)
            }
        return SparkExitTx(
            txid = txid,
            txHex = txHex,
            cpfpTxHex = cpfpTxHex,
            kind =
                when (kind) {
                    UnilateralExitTxKind.FAN_OUT -> SparkExitTxKind.FAN_OUT
                    UnilateralExitTxKind.NODE -> SparkExitTxKind.NODE
                    UnilateralExitTxKind.REFUND -> SparkExitTxKind.REFUND
                    UnilateralExitTxKind.SWEEP -> SparkExitTxKind.SWEEP
                },
            nodeId = nodeId,
            dependsOn = dependsOn,
            csvTimelockBlocks = csvTimelockBlocks,
            status = mappedStatus.first,
            blockHeight = mappedStatus.second.first,
            spendableAtHeight = mappedStatus.second.second,
        )
    }

    private class BdkBackedCpfpSigner(
        private val signPsbt: suspend (ByteArray) -> ByteArray,
    ) : CpfpSigner {
        override suspend fun signPsbt(psbtBytes: ByteArray): ByteArray = signPsbt(psbtBytes)
    }

    suspend fun sendPrepared() = withContext(Dispatchers.IO) {
        _sendState.value = SparkSendState.Sending
        try {
            val result =
                sendMutex.withLock {
                    val payment = sendPreparedInternal()
                    preparedSend = null
                    payment
                }
            _sendState.value = SparkSendState.Sent(result?.id)
            refreshFromEvent()
        } catch (e: CancellationException) {
            throw e
        } catch (e: NoPreparedSparkPaymentException) {
            _sendState.value = SparkSendState.Error(sessionExpiredMessage())
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            _sendState.value = SparkSendState.Error(e.safeMessage("Spark send failed"))
        }
    }

    suspend fun sendPreparedNow(): String? = withContext(Dispatchers.IO) {
        _sendState.value = SparkSendState.Sending
        try {
            val result =
                sendMutex.withLock {
                    val payment = sendPreparedInternal()
                    preparedSend = null
                    payment
                }
            val paymentId = result?.id
            val walletId = _loadedWalletId.value
            // Center Swap control (Spark Transfer) only — not Spark Send screen.
            if (walletId != null && !paymentId.isNullOrBlank()) {
                secureStorage.saveSparkTransactionSource(
                    walletId,
                    paymentId,
                    BitcoinTxSource.CENTER_SWAP,
                )
                _sparkTransactionSources.value = secureStorage.getAllSparkTransactionSources(walletId)
            }
            _sendState.value = SparkSendState.Sent(paymentId)
            refreshFromEvent()
            // After refresh, tag the L1 settlement tx when the withdraw onchain txid is known.
            if (walletId != null && !paymentId.isNullOrBlank()) {
                val onchainTxid =
                    _sparkState.value.payments
                        .firstOrNull { it.id == paymentId }
                        ?.onchainTxid
                        ?.trim()
                        ?.takeIf { it.length == 64 }
                if (!onchainTxid.isNullOrBlank()) {
                    secureStorage.saveTransactionSource(
                        walletId,
                        onchainTxid,
                        BitcoinTxSource.CENTER_SWAP,
                    )
                }
                _sparkTransactionSources.value = secureStorage.getAllSparkTransactionSources(walletId)
            }
            paymentId
        } catch (e: CancellationException) {
            throw e
        } catch (e: NoPreparedSparkPaymentException) {
            _sendState.value = SparkSendState.Error(sessionExpiredMessage())
            null
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            _sendState.value = SparkSendState.Error(e.safeMessage("Spark send failed"))
            // Never rethrow: this runs in fire-and-forget scope.launch from the
            // Spark Transfer review flow (including PIN-deferred execution after
            // the dialog already reset prepared state). Rethrowing crashes the app
            // with IllegalStateException instead of showing a user-visible error.
            null
        }
    }

    /**
     * Self-contained Spark → Layer 1 withdrawal for the Transfer (center Swap) flow.
     *
     * Unlike [sendPreparedNow], this carries its own preview inputs so it survives
     * the PIN-deferred auth gap and the review dialog's immediate reset, both of
     * which clear [preparedSend] before a deferred send runs. Reuses the cached
     * [preparedSend] when it still matches, otherwise re-prepares first.
     * Never throws (except [CancellationException]); failures surface via
     * [_sendState] and a `null` return for snackbar handling.
     */
    suspend fun sendSparkWithdrawal(
        destinationAddress: String,
        amountSats: Long?,
        onchainFeeSpeed: SparkOnchainFeeSpeed = SparkOnchainFeeSpeed.FAST,
        useAllFunds: Boolean = false,
    ): String? = withContext(Dispatchers.IO) {
        _sendState.value = SparkSendState.Sending
        try {
            val normalizedRequest = destinationAddress.trim()
            if (normalizedRequest.isEmpty()) {
                _sendState.value = SparkSendState.Error(sessionExpiredMessage())
                return@withContext null
            }
            val result =
                sendMutex.withLock {
                    val current = preparedSend
                    val standard = current as? PreparedSparkSend.Standard
                    val matches =
                        SparkWithdrawalPreparePolicy.shouldReusePrepared(
                            existingRequest = standard?.paymentRequest,
                            existingSpeed = standard?.onchainFeeSpeed,
                            existingFeesIncluded = standard?.feesIncluded,
                            existingAmountSats = standard?.amountSats,
                            isStandard = standard != null,
                            paymentRequest = normalizedRequest,
                            onchainFeeSpeed = onchainFeeSpeed,
                            useAllFunds = useAllFunds,
                            amountSats = amountSats,
                        )
                    if (!matches) {
                        prepareSendPreviewInternal(
                            paymentRequest = normalizedRequest,
                            amountSats = amountSats,
                            onchainFeeSpeed = onchainFeeSpeed,
                            useAllFunds = useAllFunds,
                        )
                    }
                    val payment = sendPreparedInternal()
                    preparedSend = null
                    payment
                }
            val paymentId = result?.id
            val walletId = _loadedWalletId.value
            if (walletId != null && !paymentId.isNullOrBlank()) {
                secureStorage.saveSparkTransactionSource(
                    walletId,
                    paymentId,
                    BitcoinTxSource.CENTER_SWAP,
                )
                _sparkTransactionSources.value = secureStorage.getAllSparkTransactionSources(walletId)
            }
            _sendState.value = SparkSendState.Sent(paymentId)
            refreshFromEvent()
            if (walletId != null && !paymentId.isNullOrBlank()) {
                val onchainTxid =
                    _sparkState.value.payments
                        .firstOrNull { it.id == paymentId }
                        ?.onchainTxid
                        ?.trim()
                        ?.takeIf { it.length == 64 }
                if (!onchainTxid.isNullOrBlank()) {
                    secureStorage.saveTransactionSource(
                        walletId,
                        onchainTxid,
                        BitcoinTxSource.CENTER_SWAP,
                    )
                }
                _sparkTransactionSources.value = secureStorage.getAllSparkTransactionSources(walletId)
            }
            paymentId
        } catch (e: CancellationException) {
            throw e
        } catch (e: NoPreparedSparkPaymentException) {
            _sendState.value = SparkSendState.Error(sessionExpiredMessage())
            null
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            _sendState.value = SparkSendState.Error(e.safeMessage("Spark send failed"))
            null
        }
    }

    private var preparedMultiItems: List<SparkSendState.MultiPreview.MultiItem> = emptyList()
    private var preparedMultiLabel: String? = null

    fun resetSendState() {
        // Best-effort under sendMutex: clearing mid-prepare must not orphan a
        // prepared payment that a concurrent send is about to consume. tryLock
        // keeps this synchronous for UI callers; on contention clear anyway
        // (the in-flight prepare/send will overwrite state consistently).
        if (sendMutex.tryLock()) {
            try {
                preparedSend = null
                preparedMultiItems = emptyList()
                preparedMultiLabel = null
            } finally {
                sendMutex.unlock()
            }
        } else {
            preparedSend = null
            preparedMultiItems = emptyList()
            preparedMultiLabel = null
        }
        _sendState.value = SparkSendState.Idle
    }

    /**
     * Sequential multi-payment prepare for **Spark addresses only** (one SDK payment each).
     * [recipients] is payment request → amount sats; requires ≥2 entries.
     */
    suspend fun prepareSendMany(
        recipients: List<Pair<String, Long>>,
        label: String? = null,
    ) =
        withContext(Dispatchers.IO) {
            if (recipients.size < 2) {
                _sendState.value = SparkSendState.Error(
                    // reuse shared string via app context when available — set from UI otherwise
                    "Add at least two recipients",
                )
                return@withContext
            }
            _sendState.value = SparkSendState.Preparing
            try {
                val items = mutableListOf<SparkSendState.MultiPreview.MultiItem>()
                var totalAmount = 0L
                var totalFee = 0L
                // Hold sendMutex for the whole multi-prepare: each iteration
                // overwrites preparedSend, so a concurrent single-send prepare
                // would corrupt both flows.
                sendMutex.withLock {
                    for ((paymentRequest, amount) in recipients) {
                        if (amount <= 0L) {
                            throw IllegalArgumentException("Amount required")
                        }
                        val preview =
                            prepareSendPreviewInternal(
                                paymentRequest = paymentRequest,
                                amountSats = amount,
                                onchainFeeSpeed = SparkOnchainFeeSpeed.FAST,
                                useAllFunds = false,
                            )
                        // Reject non-Spark-address rails for multi (LN / on-chain cooperative exit).
                        val method = preview.method.lowercase()
                        if (method.contains("lightning") ||
                            method.contains("lnurl") ||
                            method.contains("bolt") ||
                            method.contains("onchain") ||
                            preview.onchainFeeSpeed != null
                        ) {
                            preparedSend = null
                            throw IllegalArgumentException("Multiple mode supports Spark addresses only")
                        }
                        val fee = preview.feeSats ?: 0L
                        totalAmount += amount
                        totalFee += fee
                        items.add(
                            SparkSendState.MultiPreview.MultiItem(
                                paymentRequest = paymentRequest,
                                amountSats = amount,
                                feeSats = fee,
                            ),
                        )
                    }
                    preparedSend = null
                    preparedMultiItems = items
                    preparedMultiLabel = label?.trim()?.takeIf { it.isNotBlank() }
                }
                _sendState.value =
                    SparkSendState.MultiPreview(
                        items = items,
                        totalAmountSats = totalAmount,
                        totalFeeSats = totalFee,
                    )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                preparedSend = null
                preparedMultiItems = emptyList()
                preparedMultiLabel = null
                _sendState.value = SparkSendState.Error(e.safeMessage("Spark multi send preview failed"))
            }
        }

    suspend fun sendPreparedMany() =
        withContext(Dispatchers.IO) {
            var succeeded = 0
            var failed = 0
            var lastError: String? = null
            try {
                // Whole multi-send holds sendMutex from snapshot to clear:
                // each iteration rewrites preparedSend, so a concurrent
                // single send must wait, and the item list cannot change
                // under us between the size check and the loop.
                sendMutex.withLock {
                    val items = preparedMultiItems
                    if (items.size < 2) {
                        throw IllegalStateException("No prepared multi payment")
                    }
                    for ((index, item) in items.withIndex()) {
                        _sendState.value =
                            SparkSendState.MultiSending(
                                completed = index,
                                total = items.size,
                            )
                        try {
                            prepareSendPreviewInternal(
                                paymentRequest = item.paymentRequest,
                                amountSats = item.amountSats,
                                onchainFeeSpeed = SparkOnchainFeeSpeed.FAST,
                                useAllFunds = false,
                                label = preparedMultiLabel,
                            )
                            sendPreparedInternal()
                            succeeded++
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            failed++
                            lastError = e.safeMessage("Spark multi send failed")
                            break
                        }
                    }
                    preparedMultiItems = emptyList()
                    preparedMultiLabel = null
                    preparedSend = null
                }
                refreshFromEvent()
                _sendState.value =
                    SparkSendState.MultiSent(
                        succeeded = succeeded,
                        failed = failed,
                        detail = lastError,
                    )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                preparedMultiItems = emptyList()
                preparedMultiLabel = null
                preparedSend = null
                _sendState.value = SparkSendState.Error(e.safeMessage("Spark multi send failed"))
            }
        }

    fun resetReceiveState() {
        _receiveState.value = SparkReceiveState.Idle
    }

    /**
     * Repaints the cached pending BOLT11 invoice without touching the network.
     * The invoice stays visible until it is paid or the user generates a new one,
     * so entering the Lightning receive tab restores it instead of showing an
     * empty form. Never clobbers [SparkReceiveState.Paid]. Returns true when a
     * pending invoice was painted (or a completed payment for it was found).
     */
    fun primeLnInvoiceFromCache(): Boolean {
        val walletId = _loadedWalletId.value ?: return false
        return paintPendingLnInvoice(walletId)
    }

    /**
     * Cache-only LN invoice paint; safe without locks and without an SDK.
     * If the cached invoice is already settled, the cache is dropped and a
     * [SparkReceiveState.Paid] is emitted instead of a stale invoice.
     */
    private fun paintPendingLnInvoice(walletId: String): Boolean {
        val pending = secureStorage.getSparkPendingLnInvoice(walletId) ?: return false
        if (SparkLnInvoicePaidMatcher.isExpired(pending, nowMs = System.currentTimeMillis())) {
            secureStorage.clearSparkPendingLnInvoice(walletId)
            if (_pendingLnInvoice.value?.paymentRequest == pending.paymentRequest) {
                _pendingLnInvoice.value = null
            }
            return false
        }
        findSettledLnPayment(pending.paymentRequest)?.let { settled ->
            secureStorage.clearSparkPendingLnInvoice(walletId)
            _pendingLnInvoice.value = null
            _receiveState.value =
                SparkReceiveState.Paid(
                    kind = SparkReceiveKind.BOLT11_INVOICE,
                    paymentId = settled.id,
                    amountSats = settled.amountSats,
                    paymentRequest = pending.paymentRequest,
                )
            return true
        }
        val current = _receiveState.value
        // Never hide a payment confirmation; the Paid handler clears the cache,
        // so reaching here with Paid visible means it belongs to another request.
        if (current is SparkReceiveState.Paid) return true
        if (
            current is SparkReceiveState.Ready &&
                current.kind == SparkReceiveKind.BOLT11_INVOICE &&
                current.paymentRequest == pending.paymentRequest
        ) {
            _pendingLnInvoice.value = pending
            return true
        }
        _pendingLnInvoice.value = pending
        _receiveState.value =
            SparkReceiveState.Ready(
                kind = SparkReceiveKind.BOLT11_INVOICE,
                paymentRequest = pending.paymentRequest,
                feeSats = 0,
            )
        return true
    }

    private fun findSettledLnPayment(invoice: String): SparkPayment? =
        _sparkState.value.payments.firstOrNull { payment ->
            SparkLnInvoicePaidMatcher.matches(invoice, payment)
        }

    private fun freshPendingLnInvoice(walletId: String): SparkPendingLnInvoice? {
        val pending = secureStorage.getSparkPendingLnInvoice(walletId) ?: return null
        if (SparkLnInvoicePaidMatcher.isExpired(pending, nowMs = System.currentTimeMillis())) {
            secureStorage.clearSparkPendingLnInvoice(walletId)
            return null
        }
        return pending
    }

    fun getAllSparkAddressLabels(walletId: String): Map<String, String> =
        secureStorage.getAllSparkAddressLabels(walletId)

    fun getAllSparkTransactionLabels(walletId: String): Map<String, String> =
        secureStorage.getAllSparkTransactionLabels(walletId)

    fun saveSparkAddressLabels(
        walletId: String,
        labels: Map<String, String>,
    ) {
        secureStorage.saveSparkAddressLabels(walletId, labels)
        if (_loadedWalletId.value == walletId) {
            _sparkAddressLabels.value = secureStorage.getAllSparkAddressLabels(walletId)
        }
    }

    fun saveSparkAddressLabel(
        walletId: String,
        addressOrRequest: String,
        label: String,
    ) {
        val trimmedLabel = label.trim()
        if (trimmedLabel.isBlank()) {
            return
        }
        secureStorage.saveSparkAddressLabel(walletId, addressOrRequest, trimmedLabel)
        if (_loadedWalletId.value == walletId) {
            _sparkAddressLabels.value = secureStorage.getAllSparkAddressLabels(walletId)
        }
    }

    fun deleteSparkAddressLabel(
        walletId: String,
        addressOrRequest: String,
    ) {
        secureStorage.deleteSparkAddressLabel(walletId, addressOrRequest)
        if (_loadedWalletId.value == walletId) {
            _sparkAddressLabels.value = secureStorage.getAllSparkAddressLabels(walletId)
        }
    }

    fun saveSparkTransactionLabels(
        walletId: String,
        labels: Map<String, String>,
    ) {
        secureStorage.saveSparkTransactionLabels(walletId, labels)
        if (_loadedWalletId.value == walletId) {
            _sparkTransactionLabels.value = secureStorage.getAllSparkTransactionLabels(walletId)
        }
    }

    fun saveSparkTransactionLabel(
        walletId: String,
        paymentId: String,
        label: String,
    ) {
        secureStorage.saveSparkTransactionLabel(walletId, paymentId, label)
        if (_loadedWalletId.value == walletId) {
            _sparkTransactionLabels.value = _sparkTransactionLabels.value.toMutableMap().apply {
                put(paymentId, label)
            }
        }
    }

    fun deleteSparkTransactionLabel(
        walletId: String,
        paymentId: String,
    ) {
        secureStorage.deleteSparkTransactionLabel(walletId, paymentId)
        if (_loadedWalletId.value == walletId) {
            _sparkTransactionLabels.value = _sparkTransactionLabels.value.toMutableMap().apply {
                remove(paymentId)
            }
        }
    }

    fun deleteSparkHistoryItem(
        walletId: String,
        itemId: String,
    ) {
        secureStorage.hideSparkHistoryItem(walletId, itemId)
        secureStorage.purgeHiddenSparkHistoryItemMetadata(walletId, itemId)
        if (_loadedWalletId.value == walletId) {
            localPendingDeposits = filterHiddenSparkDeposits(walletId, localPendingDeposits)
            val updatedState = filterHiddenSparkHistory(walletId, _sparkState.value)
            _sparkState.value = updatedState
            secureStorage.saveSparkWalletStateCache(walletId, updatedState)
        }
    }

    fun deleteAllSparkHistory(walletId: String) {
        val currentState = _sparkState.value
        val itemIds =
            currentState.payments.map { it.id } +
                currentState.unclaimedDeposits.map(::sparkDepositHistoryId)
        val cleanItemIds = itemIds.filter { it.isNotBlank() }.distinct()
        if (cleanItemIds.isEmpty()) return

        cleanItemIds.forEach { itemId ->
            secureStorage.hideSparkHistoryItem(walletId, itemId)
            secureStorage.purgeHiddenSparkHistoryItemMetadata(walletId, itemId)
        }
        if (_loadedWalletId.value == walletId) {
            localPendingDeposits = filterHiddenSparkDeposits(walletId, localPendingDeposits)
            val updatedState = filterHiddenSparkHistory(walletId, currentState)
            _sparkState.value = updatedState
            secureStorage.saveSparkWalletStateCache(walletId, updatedState)
        }
    }

    private suspend fun refreshState(
        mode: SparkRefreshMode,
        allowReconnectOnFailure: Boolean = false,
    ): Boolean = withContext(Dispatchers.IO) {
        refreshMutex.withLock {
            refreshStateInternal(mode, allowReconnectOnFailure)
        }
    }

    private suspend fun refreshStateInternal(
        mode: SparkRefreshMode,
        allowReconnectOnFailure: Boolean,
    ): Boolean {
        val (activeSdk, walletId) =
            mutex.withLock {
                val activeSdk = sdk ?: return false
                val walletId = _loadedWalletId.value
                _sparkState.value = _sparkState.value.copy(isSyncing = true, error = null)
                SparkRefreshTarget(activeSdk, walletId)
            }
        return try {
            if (mode.syncWallet) {
                withTimeout(SPARK_REFRESH_TIMEOUT_MS) {
                    activeSdk.syncWalletCompat()
                }
            }
            val info = activeSdk.getInfo(GetInfoRequest(ensureSynced = false))
            // Guard every write to shared mutable state with an identity check: a
            // wallet switch may have happened while the SDK calls above were in flight.
            if (walletId != null && _loadedWalletId.value == walletId) {
                localPendingDeposits = secureStorage.getAllSparkPendingDeposits(walletId)
            }
            val storedPaymentRecipients = walletId?.let { secureStorage.getAllSparkPaymentRecipients(it) }.orEmpty()
            val storedDepositAddresses = walletId?.let { secureStorage.getAllSparkDepositAddresses(it) }.orEmpty()
            // Bound payment history: an SDK/Tor stall must fail the refresh
            // (heartbeat counts it) rather than hold refreshMutex forever.
            val payments = withTimeout(SPARK_LIST_PAYMENTS_TIMEOUT_MS) {
                activeSdk.listAllBitcoinPayments()
            }.map { it.toSparkPayment(storedPaymentRecipients, storedDepositAddresses) }
            if (walletId != null) {
                val existingSources = secureStorage.getAllSparkTransactionSources(walletId)
                val l1Sources = secureStorage.getAllTransactionSources(walletId)
                val mergedSources =
                    payments.associate { payment ->
                        val existing = existingSources[payment.id]
                        val onchainSource =
                            payment.onchainTxid
                                ?.trim()
                                ?.takeIf { it.isNotBlank() }
                                ?.let { txid ->
                                    l1Sources[txid]
                                        ?: l1Sources.entries
                                            .firstOrNull { it.key.equals(txid, ignoreCase = true) }
                                            ?.value
                                }
                        payment.id to
                            when {
                                BitcoinTxSource.isSwapHistory(existing) -> existing!!
                                BitcoinTxSource.isSwapHistory(onchainSource) -> BitcoinTxSource.CENTER_SWAP
                                else -> payment.method
                            }
                    }
                secureStorage.saveSparkTransactionSources(walletId, mergedSources)
                if (_loadedWalletId.value == walletId) {
                    _sparkTransactionSources.value = secureStorage.getAllSparkTransactionSources(walletId)
                }
            }
            val visiblePayments = filterHiddenSparkPayments(walletId, payments)
            val unclaimedDeposits =
                runCatching {
                    activeSdk.listUnclaimedDeposits(ListUnclaimedDepositsRequest).deposits.map { deposit ->
                        SparkUnclaimedDeposit(
                            txid = deposit.txid,
                            vout = deposit.vout,
                            amountSats = deposit.amountSats.toLongSafe(),
                            isMature = deposit.isMature,
                            timestamp = localPendingDeposits.firstOrNull { it.txid == deposit.txid }?.timestamp,
                            address = storedDepositAddresses[deposit.txid],
                            claimError = deposit.claimError?.toString()?.takeIf { it.isNotBlank() },
                        )
                    }
                }.getOrDefault(emptyList())
            val claimedDepositKeys =
                (unclaimedDeposits + localPendingDeposits)
                    .filter { deposit ->
                        payments.any { payment -> SparkDepositClaimMatcher.matches(deposit, payment) }
                    }
                    .map(::sparkDepositKey)
                    .toSet()
            if (_loadedWalletId.value == walletId) {
                localPendingDeposits =
                    localPendingDeposits.filter { pending ->
                        unclaimedDeposits.none { sparkDepositKey(it) == sparkDepositKey(pending) } &&
                            sparkDepositKey(pending) !in claimedDepositKeys
                    }
            }
            if (walletId != null) {
                val resolvedPendingTxids =
                    (unclaimedDeposits.map { it.txid } + claimedDepositKeys.map { it.substringBefore(':') }).toSet()
                resolvedPendingTxids.forEach { txid ->
                    secureStorage.deleteSparkPendingDeposit(walletId, txid)
                }
            }
            val visibleUnclaimedDeposits = mergeLocalPendingDeposits(unclaimedDeposits)
                .filterNot { sparkDepositKey(it) in claimedDepositKeys }
            val filteredUnclaimedDeposits = filterHiddenSparkDeposits(walletId, visibleUnclaimedDeposits)
            val depositKeys = filteredUnclaimedDeposits.map { "${it.txid}:${it.vout}" }.toSet()
            if (walletId != null && _loadedWalletId.value == walletId && depositKeys.any { it !in depositsRotatedForAddress }) {
                secureStorage.clearSparkOnchainDepositAddress(walletId)
            }
            val lightningAddress = runCatching { activeSdk.getLightningAddress()?.lightningAddress }.getOrNull()
            val refreshedState = SparkWalletState(
                walletId = walletId,
                isInitialized = true,
                identityPubkey = info.identityPubkey,
                balanceSats = info.balanceSats.toLongOrNullSafe()?.coerceAtLeast(0L) ?: 0L,
                payments = visiblePayments,
                unclaimedDeposits = filteredUnclaimedDeposits,
                lightningAddress = lightningAddress,
                isSyncing = false,
                lastSyncTimestamp = System.currentTimeMillis(),
            )
            mutex.withLock {
                // Obsolete refresh (wallet switched mid-sync): not a failure,
                // don't count it against the new wallet's heartbeat.
                if (sdk !== activeSdk || _loadedWalletId.value != walletId) return true
                depositsRotatedForAddress = depositKeys
                _sparkState.value = refreshedState
                walletId?.let { secureStorage.saveSparkWalletStateCache(it, refreshedState) }
                if (mode.syncWallet && walletId != null) {
                    recoverySyncCompletedForWalletId = walletId
                }
            }
            true
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            val failure =
                when (e) {
                    is TimeoutCancellationException -> TimeoutException("Spark sync timed out")
                    else -> e
                }
            mutex.withLock {
                if (sdk !== activeSdk || _loadedWalletId.value != walletId) return true
                _sparkState.value = _sparkState.value.copy(
                    isInitialized = true,
                    isSyncing = false,
                    error = failure.safeMessage("Spark refresh failed"),
                )
            }
            if (allowReconnectOnFailure && !reconnectInProgress && walletId != null) {
                SecureLog.w(
                    TAG,
                    "Spark refresh failed; attempting SDK reconnect",
                    failure,
                    releaseMessage = "Spark refresh failed",
                )
                runCatching { reconnectWallet() }
                    .onFailure { reconnectError ->
                        if (reconnectError is CancellationException) throw reconnectError
                        SecureLog.w(
                            TAG,
                            "Spark reconnect after refresh failure failed",
                            reconnectError,
                            releaseMessage = "Spark reconnect failed",
                        )
                    }
            }
            false
        }
    }

    private fun applyLoadingSparkStateLocked(walletId: String) {
        _sparkTransactionLabels.value = secureStorage.getAllSparkTransactionLabels(walletId)
        _sparkAddressLabels.value = secureStorage.getAllSparkAddressLabels(walletId)
        _sparkTransactionSources.value = secureStorage.getAllSparkTransactionSources(walletId)
        localPendingDeposits = secureStorage.getAllSparkPendingDeposits(walletId)
        _pendingLnInvoice.value = freshPendingLnInvoice(walletId)
        val cachedState = secureStorage.getSparkWalletStateCache(walletId)
        val inMemoryState = _sparkState.value.takeIf { it.walletId == walletId && it.isInitialized }
        _sparkState.value =
            (cachedState ?: inMemoryState)?.let { filterHiddenSparkHistory(walletId, it) }?.copy(
                walletId = walletId,
                isInitialized = true,
                isSyncing = true,
                error = null,
            ) ?: SparkWalletState(
                walletId = walletId,
                isInitialized = true,
                isSyncing = true,
            )
    }

    private fun filterHiddenSparkHistory(
        walletId: String?,
        state: SparkWalletState,
    ): SparkWalletState = state.copy(
        payments = filterHiddenSparkPayments(walletId, state.payments),
        unclaimedDeposits = filterHiddenSparkDeposits(walletId, state.unclaimedDeposits),
    )

    private fun filterHiddenSparkPayments(
        walletId: String?,
        payments: List<SparkPayment>,
    ): List<SparkPayment> {
        val hiddenIds = walletId?.let(secureStorage::getHiddenSparkHistoryItemIds).orEmpty()
        if (hiddenIds.isEmpty()) return payments
        return payments.filterNot { it.id in hiddenIds }
    }

    private fun filterHiddenSparkDeposits(
        walletId: String?,
        deposits: List<SparkUnclaimedDeposit>,
    ): List<SparkUnclaimedDeposit> {
        val hiddenIds = walletId?.let(secureStorage::getHiddenSparkHistoryItemIds).orEmpty()
        if (hiddenIds.isEmpty()) return deposits
        return deposits.filterNot { sparkDepositHistoryId(it) in hiddenIds }
    }

    private fun sparkDepositHistoryId(deposit: SparkUnclaimedDeposit): String = "deposit:${deposit.txid}:${deposit.vout}"

    private fun sparkDepositKey(deposit: SparkUnclaimedDeposit): String = "${deposit.txid}:${deposit.vout}"

    private fun sparkDepositHistoryId(
        txid: String,
        vout: UInt,
    ): String = "deposit:$txid:$vout"

    /**
     * Thrown when a `connect()` that lost a wallet-switch race finishes: the
     * winner already owns the session, so the loser disconnects its SDK and
     * yields. Extends [CancellationException] so ViewModel load paths rethrow
     * without painting a spurious load failure for the superseded wallet.
     */
    private class SupersededSparkLoadException : CancellationException("Superseded by newer load")

    private suspend fun connectWalletLocked(
        walletId: String,
        showConnectingStatus: Boolean = true,
        generation: Long? = null,
    ): BreezSdk {
        val metadata = secureStorage.getWalletMetadata(walletId)
            ?: throw IllegalStateException("Wallet not found")
        if (metadata.seedFormat != SeedFormat.BIP39 || metadata.isWatchOnly) {
            throw IllegalStateException("Spark requires a BIP39 seed wallet")
        }
        val mnemonic = secureStorage.getMnemonic(walletId)
            ?: throw IllegalStateException("No seed available for Spark")
        if (BuildConfig.SPARK_API_KEY.isBlank()) {
            throw IllegalStateException("Spark API key is not configured")
        }

        val config = defaultConfig(Network.MAINNET)
        config.apiKey = BuildConfig.SPARK_API_KEY
        config.privateEnabledDefault = true
        config.maxDepositClaimFee = MaxFee.NetworkRecommended(1u)
        // Unilateral-exit data (each leaf's pre-signed tree transactions) is
        // collected as funds arrive so an exit can be built while the
        // operators are unreachable. It also runs during syncWallet, but
        // pinning this on keeps recovery possible for funds received between
        // syncs — never turn it off to save bandwidth.
        config.exitChainAutoFetchEnabled = true

        if (showConnectingStatus) {
            _isConnecting.value = true
        }
        try {
            val storageDir = File(context.filesDir, "spark/$walletId").apply { mkdirs() }
            val connectedSdk = connect(
                ConnectRequest(
                    config = config,
                    seed = Seed.Mnemonic(mnemonic, secureStorage.getPassphrase(walletId)),
                    storageDir = storageDir.absolutePath,
                ),
            )
            val superseded = mutex.withLock {
                // Lost the switch race: a newer loadWallet call landed while
                // this connect was in flight. Never publish last-writer-wins
                // over it — drop this session and yield to the winner.
                if (generation != null && loadGeneration.get() != generation) {
                    true
                } else {
                    sdk = connectedSdk
                    _loadedWalletId.value = walletId
                    _isConnected.value = true
                    false
                }
            }
            if (superseded) {
                // Don't leak the orphaned native handle (or its SQLite lock).
                SparkSdkHandle(connectedSdk, null).awaitDisconnect()
                throw SupersededSparkLoadException()
            }

            runCatching {
                connectedSdk.updateUserSettings(
                    UpdateUserSettingsRequest(sparkPrivateModeEnabled = true),
                )
            }.onFailure {
                SecureLog.w(TAG, "Spark private mode update failed", it, releaseMessage = "Spark privacy setup failed")
            }
            return connectedSdk
        } finally {
            if (showConnectingStatus) {
                _isConnecting.value = false
            }
        }
    }

    private fun softDetachSdkLocked(markDisconnected: Boolean = true): SparkSdkHandle? {
        val handle =
            sdk?.let { activeSdk ->
                SparkSdkHandle(
                    sdk = activeSdk,
                    listenerId = listenerId,
                )
            }
        sdk = null
        listenerId = null
        preparedSend = null
        // Same-wallet reconnect swaps the SDK session: an unbuilt prepared
        // exit response belongs to the old session and must be re-quoted.
        // Built/persisted progress (_exitFlow InProgress + tx set) is kept.
        preparedExitQuote = null
        preparedExitUiQuote = null
        preparedExitFundingWeight = null
        if (markDisconnected) {
            _isConnected.value = false
            _isConnecting.value = false
        }
        return handle
    }

    private suspend fun SparkSdkHandle.awaitDisconnect() {
        withTimeoutOrNull(SPARK_DISCONNECT_TIMEOUT_MS) {
            listenerId?.let { id ->
                runCatching { sdk.removeEventListener(id) }
            }
            runCatching { sdk.disconnect() }
                .onFailure {
                    SecureLog.w(TAG, "Spark SDK disconnect failed", it, releaseMessage = "Spark disconnect failed")
                }
        } ?: SecureLog.w(
            TAG,
            "Spark SDK disconnect timed out",
            releaseMessage = "Spark disconnect timed out",
        )
    }

    private suspend fun detachWalletLocked(): SparkSdkHandle? {
        val walletId = _loadedWalletId.value
        val liveState = _sparkState.value
        if (
            !walletId.isNullOrBlank() &&
            liveState.walletId == walletId &&
            liveState.isInitialized &&
            liveState.error == null
        ) {
            secureStorage.saveSparkWalletStateCache(walletId, liveState)
        }
        val handle = sdk?.let { activeSdk ->
            SparkSdkHandle(
                sdk = activeSdk,
                listenerId = listenerId,
            )
        }
        sdk = null
        listenerId = null
        preparedSend = null
        // Wallet switch / unload: drop the in-memory exit flow so the next
        // wallet starts Idle. Per-wallet progress survives in SecureStorage
        // (keyed by wallet id) and restoreSparkExit() repaints it. Without
        // this, wallet B would show — and could build from — wallet A's
        // prepared quote.
        preparedExitQuote = null
        preparedExitUiQuote = null
        preparedExitFundingWeight = null
        setExitFlow(SparkExitFlowState.Idle)
        _isExitBackupStale.value = false
        depositsRotatedForAddress = emptySet()
        localPendingDeposits = emptyList()
        recoverySyncCompletedForWalletId = null
        // Synchronous clear under the same mutex acquisition: the old async
        // eventScope.launch could run after the new wallet's first quote,
        // wiping (or cancelling) the new wallet's cache with the old one's.
        onchainFeeQuoteCacheMutex.withLock {
            onchainFeeQuoteCache.clear()
            onchainFeeQuoteInflight.values.forEach { it.cancel() }
            onchainFeeQuoteInflight.clear()
        }
        _loadedWalletId.value = null
        _isConnected.value = false
        _isConnecting.value = false
        _sendState.value = SparkSendState.Idle
        _receiveState.value = SparkReceiveState.Idle
        // In-memory only: the SecureStorage pending invoice survives so the
        // Receive screen can repaint it when this wallet is loaded again.
        _pendingLnInvoice.value = null
        if (walletId.isNullOrBlank()) {
            _sparkTransactionLabels.value = emptyMap()
            _sparkAddressLabels.value = emptyMap()
            _sparkTransactionSources.value = emptyMap()
            _sparkState.value = SparkWalletState(isInitialized = true)
        } else {
            applyDisconnectedSparkStateLocked(walletId)
        }
        return handle
    }

    /** Offline paint after SDK detach: last cache, not empty zeros. */
    private fun applyDisconnectedSparkStateLocked(walletId: String) {
        _sparkTransactionLabels.value = secureStorage.getAllSparkTransactionLabels(walletId)
        _sparkAddressLabels.value = secureStorage.getAllSparkAddressLabels(walletId)
        _sparkTransactionSources.value = secureStorage.getAllSparkTransactionSources(walletId)
        localPendingDeposits = secureStorage.getAllSparkPendingDeposits(walletId)
        val cached = secureStorage.getSparkWalletStateCache(walletId)
        val inMemory = _sparkState.value.takeIf { it.walletId == walletId && it.isInitialized }
        _sparkState.value =
            (cached ?: inMemory)?.let { filterHiddenSparkHistory(walletId, it) }?.copy(
                walletId = walletId,
                isInitialized = true,
                isSyncing = false,
                error = null,
            ) ?: SparkWalletState(
                walletId = walletId,
                isInitialized = true,
                isSyncing = false,
            )
    }

    private fun SparkSdkHandle.disconnectInBackground() {
        eventScope.launch {
            listenerId?.let { id ->
                runCatching { sdk.removeEventListener(id) }
            }
            runCatching { sdk.disconnect() }
                .onFailure {
                    SecureLog.w(TAG, "Spark SDK disconnect failed", it, releaseMessage = "Spark disconnect failed")
                }
        }
    }

    private fun sdkOrThrow(): BreezSdk =
        sdk ?: throw IllegalStateException(_sparkState.value.error ?: "Spark wallet is not loaded")

    private suspend fun registerListenerForSdk(connectedSdk: BreezSdk) {
        // Bind the listener to the wallet it was registered for: a late event
        // from an old SDK session must never paint (or clear invoices for) the
        // wallet that is loaded now.
        val expectedWalletId = mutex.withLock { _loadedWalletId.value }
        val id =
            connectedSdk.addEventListener(
                SparkListener { event ->
                    // Always hop off the callback stack. SDK may re-enter onEvent from the same
                    // calling frame while we refresh/sync, which previously overflowed the stack.
                    eventScope.launch {
                        // Drop stale-session events before touching any shared state.
                        val currentWalletId = _loadedWalletId.value
                        if (expectedWalletId == null || currentWalletId != expectedWalletId) return@launch
                        runCatching {
                            if (event is SdkEvent.PaymentSucceeded) {
                                handlePaymentSucceeded(event.payment, expectedWalletId)
                            }
                            if (event is SdkEvent.UnilateralExitStateChanged) {
                                if (secureStorage.getSparkExitBackupPath(expectedWalletId) != null) {
                                    secureStorage.setSparkExitBackupStale(expectedWalletId, true)
                                    if (_loadedWalletId.value == expectedWalletId) {
                                        _isExitBackupStale.value = true
                                    }
                                }
                            }
                            refreshFromEvent()
                        }.onFailure { err ->
                            if (err is CancellationException) throw err
                            SecureLog.w(
                                TAG,
                                "Spark event handling failed",
                                err,
                                releaseMessage = "Spark event handling failed",
                            )
                        }
                    }
                },
            )
        mutex.withLock {
            if (sdk === connectedSdk) {
                listenerId = id
            } else {
                runCatching { connectedSdk.removeEventListener(id) }
            }
        }
    }

    private fun handlePaymentSucceeded(payment: Payment, expectedWalletId: String) {
        if (payment.paymentType != PaymentType.RECEIVE) return
        if (payment.status != PaymentStatus.COMPLETED) return
        // Second guard (in addition to the listener's early return): the wallet
        // must not have switched between the event hop and this handling.
        if (_loadedWalletId.value != expectedWalletId) return

        val paymentId = payment.id
        val amountSats = payment.amount.toLongSafe()
        val paidKind = payment.receiveKindOrNull() ?: return
        val paidRequest = payment.matchingReceiveRequest()
        val openReady = _receiveState.value as? SparkReceiveState.Ready

        val matchesOpenInvoice =
            openReady != null &&
                openReady.kind.canMatchReceivePayment(paidKind) &&
                (
                    paidRequest.isNullOrBlank() ||
                        openReady.paymentRequest.normalizeSparkPaymentRequest() ==
                        paidRequest.normalizeSparkPaymentRequest()
                )

        if (matchesOpenInvoice && openReady != null) {
            if (openReady.kind == SparkReceiveKind.SPARK_ADDRESS) {
                // Static reusable address: keep it on screen. Unlike invoices
                // it needs no replacement after a payment — the toast +
                // notification + balance refresh below is the confirmation.
                // (Flipping to Paid would null the Ready the QR/NFC payload
                // derives from and strand the screen on "Generating...".)
            } else {
                if (_loadedWalletId.value != expectedWalletId) return
                _receiveState.value =
                    SparkReceiveState.Paid(
                        kind = openReady.kind,
                        paymentId = paymentId,
                        amountSats = amountSats,
                        paymentRequest = openReady.paymentRequest,
                    )
                if (openReady.kind == SparkReceiveKind.BOLT11_INVOICE) {
                    secureStorage.clearSparkPendingLnInvoice(expectedWalletId)
                    _pendingLnInvoice.value = null
                }
            }
        }

        if (paidKind == SparkReceiveKind.BOLT11_INVOICE && !paidRequest.isNullOrBlank()) {
            // Paid while the invoice was not on screen (Idle after navigating away):
            // drop the cache so a settled invoice never resurfaces, and surface the
            // confirmation when nothing else is being shown.
            val cached = secureStorage.getSparkPendingLnInvoice(expectedWalletId)
            if (
                cached != null &&
                    cached.paymentRequest.trim().equals(paidRequest.trim(), ignoreCase = true)
            ) {
                secureStorage.clearSparkPendingLnInvoice(expectedWalletId)
                if (_loadedWalletId.value != expectedWalletId) return
                _pendingLnInvoice.value = null
                val current = _receiveState.value
                if (
                    current is SparkReceiveState.Idle ||
                        (current is SparkReceiveState.Ready && current.kind == SparkReceiveKind.BOLT11_INVOICE)
                ) {
                    _receiveState.value =
                        SparkReceiveState.Paid(
                            kind = SparkReceiveKind.BOLT11_INVOICE,
                            paymentId = paymentId,
                            amountSats = amountSats,
                            paymentRequest = cached.paymentRequest,
                        )
                }
            }
        }

        if (_loadedWalletId.value != expectedWalletId) return
        if (
            !_events.tryEmit(
                SparkEvent.PaymentReceived(
                    paymentId = paymentId,
                    amountSats = amountSats,
                    kind = if (matchesOpenInvoice) openReady?.kind else paidKind,
                ),
            )
        ) {
            SecureLog.w(TAG, "Spark payment event dropped (buffer full)", releaseMessage = "Spark event dropped")
        }
    }

    private fun Payment.matchingReceiveRequest(): String? =
        when (val details = details) {
            is PaymentDetails.Lightning -> details.invoice
            is PaymentDetails.Spark -> details.invoiceDetails?.invoice
            else -> null
        }

    private fun Payment.receiveKindOrNull(): SparkReceiveKind? =
        when (details) {
            is PaymentDetails.Lightning -> SparkReceiveKind.BOLT11_INVOICE
            is PaymentDetails.Spark -> SparkReceiveKind.SPARK_INVOICE
            else -> null
        }

    private fun SparkReceiveKind.canMatchReceivePayment(paidKind: SparkReceiveKind): Boolean =
        when (this) {
            SparkReceiveKind.BOLT11_INVOICE -> paidKind == SparkReceiveKind.BOLT11_INVOICE
            SparkReceiveKind.SPARK_INVOICE,
            SparkReceiveKind.SPARK_ADDRESS,
            -> paidKind == SparkReceiveKind.SPARK_INVOICE
            SparkReceiveKind.BITCOIN_ADDRESS -> false
        }

    private fun String.normalizeSparkPaymentRequest(): String =
        trim()
            .removePrefix("lightning:")
            .removePrefix("LIGHTNING:")
            .lowercase(Locale.US)

    private suspend fun BreezSdk.syncWalletCompat() {
        runCatching { syncWallet(SyncWalletRequest) }
            .onFailure {
                SecureLog.w(TAG, "Spark syncWallet failed", it, releaseMessage = "Spark wallet sync failed")
                throw it
            }
    }

    private suspend fun BreezSdk.listAllBitcoinPayments(): List<Payment> {
        val payments = mutableListOf<Payment>()
        var offset = 0u
        while (payments.size < SPARK_PAYMENT_HISTORY_MAX) {
            val page = listPayments(
                ListPaymentsRequest(
                    typeFilter = null,
                    statusFilter = null,
                    assetFilter = AssetFilter.Bitcoin,
                    paymentDetailsFilter = null,
                    fromTimestamp = null,
                    toTimestamp = null,
                    offset = offset,
                    limit = SPARK_PAYMENT_HISTORY_PAGE_SIZE,
                    sortAscending = false,
                ),
            ).payments
            payments += page
            if (page.size < SPARK_PAYMENT_HISTORY_PAGE_SIZE.toInt()) break
            offset = SparkPaymentHistoryPaging.nextOffset(offset)
        }
        return payments.take(SPARK_PAYMENT_HISTORY_MAX)
    }

    private class SparkListener(
        private val onSdkEvent: (SdkEvent) -> Unit,
    ) : EventListener {
        override suspend fun onEvent(event: SdkEvent) {
            // Keep this method non-branching and non-suspending so UniFFI/native callbacks
            // cannot re-enter through sealed-class dispatch on the same stack frame.
            if (event is SdkEvent.AutoOptimization) return
            onSdkEvent(event)
        }
    }

    private sealed interface PreparedSparkSend {
        val paymentRequest: String
        val label: String?
        val idempotencyKey: String

        data class Standard(
            override val paymentRequest: String,
            val response: PrepareSendPaymentResponse,
            val onchainFeeSpeed: SparkOnchainFeeSpeed,
            val feesIncluded: Boolean,
            val amountSats: Long?,
            override val label: String? = null,
            override val idempotencyKey: String = UUID.randomUUID().toString(),
        ) : PreparedSparkSend
        data class Lnurl(
            override val paymentRequest: String,
            val response: PrepareLnurlPayResponse,
            val requestedAmountSats: Long,
            val feesIncluded: Boolean,
            override val label: String? = null,
            override val idempotencyKey: String = UUID.randomUUID().toString(),
        ) : PreparedSparkSend
    }

    internal object SparkWithdrawalPreparePolicy {
        /**
         * Whether the cached prepared send still matches a withdrawal request
         * and can be reused without re-preparing. Only standard (non-LNURL)
         * prepares match: LNURL prepares bind a different amount/fee context and
         * must re-prepare. Whitespace around the request is ignored; the request
         * itself is compared verbatim (Bitcoin addresses are case-sensitive).
         *
         * Takes decomposed values (instead of the private prepared type) so the
         * policy stays unit-testable without SDK response types.
         */
        fun shouldReusePrepared(
            existingRequest: String?,
            existingSpeed: SparkOnchainFeeSpeed?,
            existingFeesIncluded: Boolean?,
            existingAmountSats: Long?,
            isStandard: Boolean,
            paymentRequest: String,
            onchainFeeSpeed: SparkOnchainFeeSpeed,
            useAllFunds: Boolean,
            amountSats: Long?,
        ): Boolean {
            if (!isStandard) return false
            if (existingRequest == null || existingSpeed == null || existingFeesIncluded == null) return false
            // Drain prepares bind the live balance at prepare time — never reuse
            // across review edits; always re-prepare so the fee quote is fresh.
            if (existingFeesIncluded || useAllFunds) return false
            if (existingAmountSats != amountSats) return false
            return existingRequest.trim() == paymentRequest.trim() &&
                existingSpeed == onchainFeeSpeed &&
                existingFeesIncluded == useAllFunds
        }
    }

    private suspend fun prepareSendPreviewInternal(
        paymentRequest: String,
        amountSats: Long?,
        onchainFeeSpeed: SparkOnchainFeeSpeed,
        useAllFunds: Boolean,
        label: String? = null,
    ): SparkSendState.Preview {
        val activeSdk = sdkOrThrow()
        val parsed = runCatching { activeSdk.parse(paymentRequest) }.getOrNull()
        val prepared =
            when (val lnurlDetails = parsed?.lnurlPayDetails()) {
                null -> {
                    // Max-send ("drain") needs an explicit amount for
                    // address-type recipients: per SDK docs, pass amount =
                    // balance with FEES_INCLUDED and the wallet spends exactly
                    // that. Amount-bearing invoices keep null (SDK uses the
                    // embedded amount). Without this the SDK rejects the
                    // prepare with "Amount is required".
                    val resolvedAmountSats =
                        amountSats ?: if (useAllFunds) {
                            sdkBalanceSats(activeSdk.getInfo(GetInfoRequest(ensureSynced = false)).balanceSats)
                        } else {
                            null
                        }
                    if (resolvedAmountSats != null) {
                        require(resolvedAmountSats > 0L) { "Invalid amount" }
                    }
                    val response = activeSdk.prepareSendPayment(
                        PrepareSendPaymentRequest(
                            paymentRequest = PaymentRequest.Input(paymentRequest),
                            amount = resolvedAmountSats?.toSparkBigInteger(),
                            tokenIdentifier = null,
                            conversionOptions = null,
                            feePolicy = if (useAllFunds) FeePolicy.FEES_INCLUDED else FeePolicy.FEES_EXCLUDED,
                        ),
                    )
                    PreparedSparkSend.Standard(paymentRequest, response, onchainFeeSpeed, useAllFunds, resolvedAmountSats, label)
                }
                else -> {
                    val requiredAmountSats =
                        amountSats
                            ?: if (useAllFunds) {
                                sdkBalanceSats(activeSdk.getInfo(GetInfoRequest(ensureSynced = false)).balanceSats)
                            } else {
                                throw IllegalArgumentException("Amount required for LNURL")
                            }
                    require(requiredAmountSats > 0L) { "Invalid amount" }
                    val response = activeSdk.prepareLnurlPay(
                        PrepareLnurlPayRequest(
                            amount = requiredAmountSats.toSparkBigInteger(),
                            payRequest = lnurlDetails,
                            comment = null,
                            validateSuccessActionUrl = true,
                            tokenIdentifier = null,
                            conversionOptions = null,
                            feePolicy = if (useAllFunds) FeePolicy.FEES_INCLUDED else FeePolicy.FEES_EXCLUDED,
                        ),
                    )
                    PreparedSparkSend.Lnurl(paymentRequest, response, requiredAmountSats, useAllFunds, label)
                }
            }
        preparedSend = prepared
        return prepared.toPreview(paymentRequest, amountSats)
    }

    private suspend fun sendPreparedInternal(): Payment {
        val activeSdk = sdkOrThrow()
        val prepared = preparedSend ?: throw NoPreparedSparkPaymentException()
        // Reuse the prepare-time idempotency key: a retry after an ambiguous
        // timeout must not mint a fresh key (the SDK could not dedupe it and
        // the user could double-pay). A new prepare mints a new key.
        val payment =
            when (prepared) {
                is PreparedSparkSend.Standard -> activeSdk.sendPayment(
                    SendPaymentRequest(
                        prepareResponse = prepared.response,
                        options = prepared.response.paymentMethod.defaultOptions(prepared.onchainFeeSpeed),
                        idempotencyKey = prepared.idempotencyKey,
                    ),
                ).payment
                is PreparedSparkSend.Lnurl -> activeSdk.lnurlPay(
                    LnurlPayRequest(
                        prepareResponse = prepared.response,
                        idempotencyKey = prepared.idempotencyKey,
                    ),
                ).payment
            }
        val walletId = _loadedWalletId.value
        if (walletId != null) {
            secureStorage.saveSparkPaymentRecipient(walletId, payment.id, prepared.paymentRequest)
            prepared.label?.trim()?.takeIf { it.isNotBlank() }?.let { sendLabel ->
                saveSparkAddressLabel(walletId, prepared.paymentRequest, sendLabel)
            }
        }
        return payment
    }

    private fun PreparedSparkSend.toPreview(paymentRequest: String, amountSats: Long?): SparkSendState.Preview =
        when (this) {
            is PreparedSparkSend.Standard -> {
                val feeSats = response.paymentMethod.feeSats(onchainFeeSpeed)
                val grossAmountSats = response.amount.toLongSafe()
                require(grossAmountSats > 0L) { "Invalid SDK amount" }
                if (feesIncluded && feeSats != null && feeSats >= grossAmountSats) {
                    // A drain whose fee meets/exceeds the balance is uneconomical:
                    // fail loudly (maps to insufficient-funds) instead of
                    // previewing a bogus "0 sats" send.
                    throw IllegalArgumentException("Insufficient funds: fee exceeds amount")
                }
                SparkSendState.Preview(
                    paymentRequest = paymentRequest,
                    amountSats = if (feesIncluded && feeSats != null) (grossAmountSats - feeSats).coerceAtLeast(0L) else grossAmountSats,
                    feeSats = feeSats,
                    method = response.paymentMethod::class.java.simpleName.substringAfterLast("$"),
                    onchainFeeSpeed = response.paymentMethod.onchainFeeSpeedOrNull(onchainFeeSpeed),
                    onchainFeeQuotes = response.paymentMethod.onchainFeeQuotes(),
                )
            }
            is PreparedSparkSend.Lnurl -> {
                val feeSats = response.feeSats.toLongSafe()
                if (feesIncluded && feeSats >= requestedAmountSats) {
                    throw IllegalArgumentException("Insufficient funds: fee exceeds amount")
                }
                SparkSendState.Preview(
                    paymentRequest = paymentRequest,
                    amountSats = if (feesIncluded) (requestedAmountSats - feeSats).coerceAtLeast(0L) else amountSats,
                    feeSats = feeSats,
                    method = "LNURL",
                )
            }
        }

    private fun safeFeeSum(a: ULong, b: ULong): Long? {
        val aLong = a.toLongOrNullSafe() ?: return null
        val bLong = b.toLongOrNullSafe() ?: return null
        if (aLong > Long.MAX_VALUE - bLong) return null
        return aLong + bLong
    }

    private fun SendPaymentMethod.feeSats(onchainFeeSpeed: SparkOnchainFeeSpeed): Long? =
        when (this) {
            is SendPaymentMethod.BitcoinAddress -> {
                val quote = when (onchainFeeSpeed) {
                    SparkOnchainFeeSpeed.SLOW -> feeQuote.speedSlow
                    SparkOnchainFeeSpeed.MEDIUM -> feeQuote.speedMedium
                    SparkOnchainFeeSpeed.FAST -> feeQuote.speedFast
                }
                safeFeeSum(quote.userFeeSat, quote.l1BroadcastFeeSat)
            }
            is SendPaymentMethod.Bolt11Invoice -> lightningFeeSats.toLongOrNullSafe()
            is SendPaymentMethod.SparkAddress -> fee.toLongSafe().takeIf { it >= 0L }
            is SendPaymentMethod.SparkInvoice -> fee.toLongSafe().takeIf { it >= 0L }
            is SendPaymentMethod.CrossChainAddress -> sourceTransferFeeSats.toLongOrNullSafe()
        }

    private fun SendPaymentMethod.onchainFeeQuotes(): List<SparkOnchainFeeQuote> =
        when (this) {
            is SendPaymentMethod.BitcoinAddress ->
                listOf(
                    SparkOnchainFeeQuote(SparkOnchainFeeSpeed.SLOW, feeQuote.speedSlow.totalFeeSats()),
                    SparkOnchainFeeQuote(SparkOnchainFeeSpeed.MEDIUM, feeQuote.speedMedium.totalFeeSats()),
                    SparkOnchainFeeQuote(SparkOnchainFeeSpeed.FAST, feeQuote.speedFast.totalFeeSats()),
                )
            else -> emptyList()
        }

    private fun SendOnchainSpeedFeeQuote.totalFeeSats(): Long =
        safeFeeSum(userFeeSat, l1BroadcastFeeSat) ?: 0L

    private fun SendPaymentMethod.defaultOptions(onchainFeeSpeed: SparkOnchainFeeSpeed): SendPaymentOptions? =
        when (this) {
            is SendPaymentMethod.BitcoinAddress ->
                SendPaymentOptions.BitcoinAddress(confirmationSpeed = onchainFeeSpeed.toSdkConfirmationSpeed())
            is SendPaymentMethod.Bolt11Invoice ->
                SendPaymentOptions.Bolt11Invoice(preferSpark = false, completionTimeoutSecs = 10u)
            else -> null
        }

    private fun SendPaymentMethod.onchainFeeSpeedOrNull(speed: SparkOnchainFeeSpeed): SparkOnchainFeeSpeed? =
        if (this is SendPaymentMethod.BitcoinAddress) speed else null

    private fun SparkOnchainFeeSpeed.toSdkConfirmationSpeed(): OnchainConfirmationSpeed =
        when (this) {
            SparkOnchainFeeSpeed.SLOW -> OnchainConfirmationSpeed.SLOW
            SparkOnchainFeeSpeed.MEDIUM -> OnchainConfirmationSpeed.MEDIUM
            SparkOnchainFeeSpeed.FAST -> OnchainConfirmationSpeed.FAST
        }

    private fun mergeLocalPendingDeposits(sdkDeposits: List<SparkUnclaimedDeposit>): List<SparkUnclaimedDeposit> {
        val localByKey = localPendingDeposits.associateBy { "${it.txid}:${it.vout}" }
        val sdkKeys = sdkDeposits.map { "${it.txid}:${it.vout}" }.toSet()
        // Carry the swap marker onto the SDK copy: once the SDK indexes the
        // deposit, the local entry is dropped in favor of it — without this
        // the title flips back to Received until the L1 link arrives.
        val sdkWithFlags = sdkDeposits.map { sdk ->
            val local = localByKey["${sdk.txid}:${sdk.vout}"]
            if (local?.isSwapDeposit == true) sdk.copy(isSwapDeposit = true) else sdk
        }
        return localPendingDeposits.filter { "${it.txid}:${it.vout}" !in sdkKeys } + sdkWithFlags
    }

    private fun InputType.lnurlPayDetails(): LnurlPayRequestDetails? =
        when (this) {
            is InputType.LnurlPay -> v1
            is InputType.LightningAddress -> v1.payRequest
            is InputType.CrossChainAddress,
            is InputType.Bip21,
            is InputType.BitcoinAddress,
            is InputType.Bolt11Invoice,
            is InputType.Bolt12Invoice,
            is InputType.Bolt12InvoiceRequest,
            is InputType.Bolt12Offer,
            is InputType.LnurlAuth,
            is InputType.LnurlWithdraw,
            is InputType.SilentPaymentAddress,
            is InputType.SparkAddress,
            is InputType.SparkInvoice,
            is InputType.Url,
            -> null
        }

    private fun Payment.toSparkPayment(
        storedPaymentRecipients: Map<String, String>,
        storedDepositAddresses: Map<String, String>,
    ): SparkPayment {
        val mapped = SparkPaymentDetailsMapper.map(details)
        val methodName = method.name
        val methodDetails = mapped.methodDetails ?: methodName
        val onchainTxid = mapped.onchainTxid
        val recipient =
            mapped.recipient
                ?: methodDetails.extractSparkRecipient()
                ?: storedPaymentRecipients[id]
                ?: onchainTxid?.let { storedDepositAddresses[it] }
                ?: storedDepositAddresses.firstNotNullOfOrNull { (txid, address) ->
                    address.takeIf { methodDetails.contains(txid, ignoreCase = true) }
                }
        return SparkPayment(
            id = id,
            type = paymentType.name,
            status = status.name,
            amountSats = amount.toLongSafe(),
            feeSats = fees.toLongSafe(),
            timestamp = timestamp.toLong(),
            method = methodName,
            recipient = recipient,
            methodDetails = methodDetails,
            onchainTxid = onchainTxid,
            onchainVout = mapped.onchainVout,
        )
    }

    private fun String.extractSparkRecipient(): String? {
        val keys = listOf(
            "address",
            "bitcoinAddress",
            "sparkAddress",
            "lightningAddress",
            "invoice",
            "bolt11",
            "paymentRequest",
            "paymentInput",
            "recipient",
            "destination",
            "destinationAddress",
            "receiverAddress",
        )
        keys.forEach { key ->
            Regex("""$key=([^,)]+)""", RegexOption.IGNORE_CASE)
                .find(this)
                ?.groupValues
                ?.getOrNull(1)
                ?.let { value ->
                return value.trim().takeIf { it.isNotBlank() && it != "null" }
            }
        }
        return null
    }

    private fun Long.toSparkBigInteger(): BigInteger {
        require(this > 0L) { "Invalid amount" }
        return BigInteger.valueOf(this)
    }

    private fun Any?.toLongSafe(): Long {
        val parsed = this?.toString()?.toLongOrNull()
        if (parsed == null && this != null && this.toString().isNotBlank()) {
            // A corrupt/overflowing SDK numeric must never paint as "0 sats":
            // log in all builds (debug logcat + release SecureLog) so a
            // wrapped fee/amount leaves a trace instead of a bogus zero.
            if (BuildConfig.DEBUG) {
                android.util.Log.w(TAG, "toLongSafe: unparseable numeric value coerced to 0: $this")
            }
            SecureLog.w(TAG, "toLongSafe coerced to 0", releaseMessage = "Spark amount parse failed")
        }
        return parsed ?: 0L
    }

    /**
     * Null-on-overflow ULong conversion for fee paths: a wrapped fee must
     * surface as unknown (null) rather than 0 sats. Callers treat null as
     * "fee unavailable" instead of "free".
     */
    private fun ULong.toLongOrNullSafe(): Long? =
        if (this <= Long.MAX_VALUE.toULong()) this.toLong() else null

    /** Throwing ULong conversion for exit/consensus amounts: never wrap. */
    private fun ULong.toLongExact(): Long {
        if (this > Long.MAX_VALUE.toULong()) {
            throw IllegalStateException("SDK value exceeds maximum")
        }
        return this.toLong()
    }

    private fun sdkBalanceSats(balanceSats: ULong): Long {
        val balance = balanceSats.toLongOrNullSafe()?.coerceAtLeast(0L)
            ?: throw IllegalStateException("Invalid wallet balance")
        require(balance > 0L) { "Insufficient funds" }
        return balance
    }

    private fun Throwable.safeMessage(fallback: String): String =
        SparkServiceErrors.mapFailure(sparkErrorLocalizer, this, fallback)

    private fun sessionExpiredMessage(): String =
        runCatching { sparkErrorLocalizer.get(R.string.spark_send_session_expired) }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: "Spark payment expired. Please review again."

    private class NoPreparedSparkPaymentException : IllegalStateException("No prepared Spark payment")

    private val sparkErrorLocalizer =
        object : SparkServiceErrors.Localizer {
            override fun get(resId: Int): String =
                AppLocale.createLocalizedContext(context.applicationContext, secureStorage.getAppLocale())
                    .getString(resId)
        }

    companion object {
        private const val TAG = "SparkRepository"
        internal val SPARK_PAYMENT_HISTORY_PAGE_SIZE = 50u
        internal const val SPARK_PAYMENT_HISTORY_MAX = 1000
        private const val SPARK_REFRESH_TIMEOUT_MS = 120_000L
        private const val SPARK_DISCONNECT_TIMEOUT_MS = 15_000L
        private const val SPARK_LIST_PAYMENTS_TIMEOUT_MS = 60_000L
        private const val SPARK_FEE_QUOTE_TIMEOUT_MS = 60_000L
        private const val ONCHAIN_FEE_QUOTE_CACHE_TTL_MS = 45_000L
        private const val ONCHAIN_FEE_QUOTE_CACHE_MAX = 24
        /** BOLT11 invoices are minted with a 1h expiry; a stale cache entry must not resurface. */
        internal const val SPARK_LN_INVOICE_TTL_MS = 3_600_000L
    }
}

private data class OnchainFeeQuoteCacheKey(
    val paymentRequest: String,
    val amountSats: Long,
    val useAllFunds: Boolean,
)

private data class CachedOnchainFeeQuotes(
    val quotes: List<SparkOnchainFeeQuote>,
    val fetchedAtMs: Long,
)

private data class SparkRefreshTarget(
    val sdk: BreezSdk,
    val walletId: String?,
)

private data class SparkSdkHandle(
    val sdk: BreezSdk,
    val listenerId: String?,
)

internal enum class SparkRefreshMode(
    val syncWallet: Boolean,
) {
    ExplicitSdkSync(syncWallet = true),
    ReadCached(syncWallet = false),
}

internal object SparkSyncPolicy {
    fun shouldSyncWallet(
        currentLoadedWalletId: String?,
        requestedWalletId: String,
        recoverySyncCompletedForWalletId: String?,
        hasSdk: Boolean,
    ): Boolean =
        !hasSdk ||
            currentLoadedWalletId != requestedWalletId ||
            recoverySyncCompletedForWalletId != requestedWalletId

    fun modeForManualRefresh(): SparkRefreshMode = SparkRefreshMode.ExplicitSdkSync

    fun modeForHeartbeat(): SparkRefreshMode = SparkRefreshMode.ReadCached

    fun modeForSdkEvent(): SparkRefreshMode = SparkRefreshMode.ReadCached

    fun shouldForceReconnectOnNetworkChange(loadedWalletId: String?): Boolean = loadedWalletId != null
}

internal class SparkReconnectDebouncer(
    private val debounceMs: Long = SPARK_NETWORK_RECONNECT_DEBOUNCE_MS,
    private val minIntervalMs: Long = SPARK_NETWORK_RECONNECT_MIN_INTERVAL_MS,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    private var lastReconnectRequestMs = 0L
    private var lastReconnectExecutedMs: Long? = null

    fun recordScheduleRequest(now: Long = clock()): Boolean {
        val lastExecuted = lastReconnectExecutedMs
        if (lastExecuted != null && now - lastExecuted < minIntervalMs) return false
        lastReconnectRequestMs = now
        return true
    }

    fun shouldRunReconnect(now: Long = clock()): Boolean {
        val lastExecuted = lastReconnectExecutedMs
        if (lastExecuted != null && now - lastExecuted < minIntervalMs) return false
        if (now - lastReconnectRequestMs < debounceMs) return false
        return true
    }

    fun markReconnectExecuted(now: Long = clock()) {
        lastReconnectExecutedMs = now
        lastReconnectRequestMs = now
    }

    /**
     * Force-path throttle: explicit reconnects (connection-lost handler,
     * foreground return, connect button) bypass the debounce tail but must
     * still respect the min interval so concurrent triggers cannot stack a
     * reconnect storm. Returns false when a reconnect ran too recently.
     */
    fun tryRecordForceReconnect(now: Long = clock()): Boolean {
        val lastExecuted = lastReconnectExecutedMs
        if (lastExecuted != null && now - lastExecuted < minIntervalMs) return false
        lastReconnectExecutedMs = now
        lastReconnectRequestMs = now
        return true
    }
}

internal const val SPARK_NETWORK_RECONNECT_DEBOUNCE_MS = 2_000L
internal const val SPARK_NETWORK_RECONNECT_MIN_INTERVAL_MS = 5_000L

internal object SparkPaymentHistoryPaging {
    fun nextOffset(currentOffset: UInt): UInt = currentOffset + SparkRepository.SPARK_PAYMENT_HISTORY_PAGE_SIZE
}

internal data class SparkMappedPaymentDetails(
    val methodDetails: String? = null,
    val recipient: String? = null,
    val onchainTxid: String? = null,
    val onchainVout: UInt? = null,
)

/**
 * Maps breez_sdk_spark [PaymentDetails] into fields we can match and display.
 * Deposit/Withdraw identity lives in details (txId), not PaymentMethod alone.
 */
internal object SparkPaymentDetailsMapper {
    fun map(details: PaymentDetails?): SparkMappedPaymentDetails =
        when (details) {
            is PaymentDetails.Deposit ->
                SparkMappedPaymentDetails(
                    methodDetails = "Deposit(txId=${details.txId}, vout=${details.vout})",
                    onchainTxid = details.txId,
                    onchainVout = details.vout,
                )
            is PaymentDetails.Withdraw ->
                SparkMappedPaymentDetails(
                    methodDetails = "Withdraw(txId=${details.txId})",
                    onchainTxid = details.txId,
                )
            is PaymentDetails.Lightning ->
                SparkMappedPaymentDetails(
                    methodDetails = details.toString(),
                    recipient =
                        details.lnurlPayInfo?.lnAddress?.takeIf { it.isNotBlank() }
                            ?: details.invoice.takeIf { it.isNotBlank() },
                )
            is PaymentDetails.Spark ->
                SparkMappedPaymentDetails(
                    methodDetails = details.toString(),
                    recipient = details.invoiceDetails?.invoice?.takeIf { it.isNotBlank() },
                )
            is PaymentDetails.Token ->
                SparkMappedPaymentDetails(methodDetails = details.toString())
            null -> SparkMappedPaymentDetails()
        }
}

internal object SparkDepositClaimMatcher {
    fun matches(
        deposit: SparkUnclaimedDeposit,
        payment: SparkPayment,
    ): Boolean {
        if (!payment.type.equals("RECEIVE", ignoreCase = true)) return false
        if (!isSettled(payment.status)) return false
        if (!payment.referencesTxid(deposit.txid)) return false
        // Structured on-chain txid is authoritative; amount equality remains a fallback
        // for legacy cached payments that only buried the txid in methodDetails.
        val exactOnchain =
            payment.onchainTxid?.equals(deposit.txid, ignoreCase = true) == true
        return exactOnchain || payment.amountSats == deposit.amountSats
    }

    private fun isSettled(status: String): Boolean {
        val normalized = status.trim().lowercase(Locale.US)
        return normalized == "complete" ||
            normalized == "completed" ||
            normalized == "confirmed" ||
            normalized == "succeeded" ||
            normalized == "success"
    }

    private fun SparkPayment.referencesTxid(txid: String): Boolean {
        val normalizedTxid = txid.lowercase(Locale.US)
        if (onchainTxid?.lowercase(Locale.US) == normalizedTxid) return true
        return listOf(id, method, methodDetails, recipient.orEmpty())
            .any { value -> value.lowercase(Locale.US).contains(normalizedTxid) }
    }
}

internal object SparkLnInvoicePaidMatcher {
    /**
     * Whether a cached pending BOLT11 invoice counts as settled by [payment].
     * For Lightning receives the SDK-mapped `recipient` carries the invoice, so
     * matching is exact on the normalized invoice (case-insensitive: invoices
     * are bech32) plus a settled status. `methodDetails` is a fallback for
     * legacy cached payments that only bury the invoice in the debug dump.
     */
    fun matches(
        invoice: String,
        payment: SparkPayment,
    ): Boolean {
        if (!payment.type.equals("RECEIVE", ignoreCase = true)) return false
        if (!isSettled(payment.status)) return false
        val normalized = invoice.trim().lowercase(Locale.US)
        if (normalized.isEmpty()) return false
        if (payment.recipient?.trim()?.lowercase(Locale.US) == normalized) return true
        return payment.methodDetails.lowercase(Locale.US).contains(normalized)
    }

    fun isExpired(
        pending: SparkPendingLnInvoice,
        nowMs: Long,
    ): Boolean {
        if (pending.createdAtMs <= 0L) return false
        return nowMs - pending.createdAtMs >= SparkRepository.SPARK_LN_INVOICE_TTL_MS
    }

    private fun isSettled(status: String): Boolean {
        val normalized = status.trim().lowercase(Locale.US)
        return normalized == "complete" ||
            normalized == "completed" ||
            normalized == "confirmed" ||
            normalized == "succeeded" ||
            normalized == "success"
    }
}
