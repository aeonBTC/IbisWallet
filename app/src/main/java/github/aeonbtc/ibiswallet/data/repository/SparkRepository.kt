package github.aeonbtc.ibiswallet.data.repository

import android.content.Context
import breez_sdk_spark.AssetFilter
import breez_sdk_spark.BreezSdk
import breez_sdk_spark.ConnectRequest
import breez_sdk_spark.EventListener
import breez_sdk_spark.FeePolicy
import breez_sdk_spark.GetInfoRequest
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
import breez_sdk_spark.ReceivePaymentMethod
import breez_sdk_spark.ReceivePaymentRequest
import breez_sdk_spark.SdkEvent
import breez_sdk_spark.Seed
import breez_sdk_spark.SendPaymentMethod
import breez_sdk_spark.SendPaymentOptions
import breez_sdk_spark.SendPaymentRequest
import breez_sdk_spark.SendOnchainSpeedFeeQuote
import breez_sdk_spark.SyncWalletRequest
import breez_sdk_spark.UpdateUserSettingsRequest
import breez_sdk_spark.connect
import breez_sdk_spark.defaultConfig
import github.aeonbtc.ibiswallet.BuildConfig
import github.aeonbtc.ibiswallet.R
import github.aeonbtc.ibiswallet.data.local.SecureStorage
import github.aeonbtc.ibiswallet.data.model.FeeEstimateSource
import github.aeonbtc.ibiswallet.data.model.FeeEstimates
import github.aeonbtc.ibiswallet.data.model.SeedFormat
import github.aeonbtc.ibiswallet.data.model.SparkEvent
import github.aeonbtc.ibiswallet.data.model.SparkOnchainFeeQuote
import github.aeonbtc.ibiswallet.data.model.SparkOnchainFeeSpeed
import github.aeonbtc.ibiswallet.data.model.SparkPayment
import github.aeonbtc.ibiswallet.data.model.SparkReceiveKind
import github.aeonbtc.ibiswallet.data.model.SparkReceiveState
import github.aeonbtc.ibiswallet.data.model.SparkSendState
import github.aeonbtc.ibiswallet.data.model.SparkUnclaimedDeposit
import github.aeonbtc.ibiswallet.data.model.SparkWalletState
import github.aeonbtc.ibiswallet.localization.AppLocale
import github.aeonbtc.ibiswallet.util.SecureLog
import github.aeonbtc.ibiswallet.util.isTransactionInsufficientFundsError
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

    private val _events = MutableSharedFlow<SparkEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<SparkEvent> = _events.asSharedFlow()

    private val _sparkTransactionLabels = MutableStateFlow<Map<String, String>>(emptyMap())
    val sparkTransactionLabels: StateFlow<Map<String, String>> = _sparkTransactionLabels.asStateFlow()
    private val _sparkAddressLabels = MutableStateFlow<Map<String, String>>(emptyMap())
    val sparkAddressLabels: StateFlow<Map<String, String>> = _sparkAddressLabels.asStateFlow()

    private val _loadedWalletId = MutableStateFlow<String?>(null)
    val loadedWalletId: StateFlow<String?> = _loadedWalletId.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()
    private val _isConnecting = MutableStateFlow(false)
    val isConnecting: StateFlow<Boolean> = _isConnecting.asStateFlow()

    private val mutex = Mutex()
    private val reconnectMutex = Mutex()
    private var refreshMutex = Mutex()
    private val eventScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var sdk: BreezSdk? = null
    private var listenerId: String? = null
    private var preparedSend: PreparedSparkSend? = null
    private var depositsRotatedForAddress: Set<String> = emptySet()
    private var localPendingDeposits: List<SparkUnclaimedDeposit> = emptyList()
    private var recoverySyncCompletedForWalletId: String? = null
    private var reconnectInProgress = false

    suspend fun loadWallet(walletId: String) = withContext(Dispatchers.IO) {
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
        sdkToDisconnect?.disconnectInBackground()
        val connectedSdk = walletIdToConnect?.let { connectWalletLocked(it) }
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
                val connectedSdk = connectWalletLocked(walletId, showConnectingStatus = false)
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
        _loadedWalletId.value = null
        _isConnected.value = false
        _isConnecting.value = false
        _sparkTransactionLabels.value = emptyMap()
        _sparkAddressLabels.value = emptyMap()
        _sparkState.value = SparkWalletState(isInitialized = true)
        _sendState.value = SparkSendState.Idle
        _receiveState.value = SparkReceiveState.Idle
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
        )
        _loadedWalletId.value?.let { walletId ->
            secureStorage.saveSparkPendingDeposit(walletId, pendingDeposit)
        }
        localPendingDeposits =
            (listOf(pendingDeposit) + localPendingDeposits)
                .distinctBy { it.txid }
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
            File(context.filesDir, "spark/$walletId").deleteRecursively()
        }
        sdkToDisconnect?.disconnectInBackground()
    }

    suspend fun refreshState() = refreshState(SparkSyncPolicy.modeForManualRefresh())

    private suspend fun refreshFromEvent() = refreshState(SparkRefreshMode.ReadCached)

    suspend fun receive(
        kind: SparkReceiveKind,
        amountSats: Long? = null,
        description: String = "",
        forceNew: Boolean = false,
    ) =
        withContext(Dispatchers.IO) {
            val walletId = _loadedWalletId.value
            if (kind == SparkReceiveKind.BITCOIN_ADDRESS && !forceNew && walletId != null) {
                secureStorage.getSparkOnchainDepositAddress(walletId)?.let { cachedAddress ->
                    val normalizedAddress = normalizeSparkAddressLabelRef(cachedAddress)
                    if (description.isNotBlank()) {
                        secureStorage.saveSparkAddressLabel(walletId, normalizedAddress, description)
                    }
                    _receiveState.value = SparkReceiveState.Ready(
                        kind = kind,
                        paymentRequest = normalizedAddress,
                        feeSats = 0,
                    )
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
                if (walletId != null && description.isNotBlank()) {
                    secureStorage.saveSparkAddressLabel(walletId, paymentRequest, description)
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
    ) = withContext(Dispatchers.IO) {
        _sendState.value = SparkSendState.Preparing
        try {
            _sendState.value = prepareSendPreviewInternal(paymentRequest, amountSats, onchainFeeSpeed, useAllFunds)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            _sendState.value = SparkSendState.Error(e.safeMessage("Spark send preview failed"))
        }
    }

    suspend fun prepareSendPreview(
        paymentRequest: String,
        amountSats: Long?,
        onchainFeeSpeed: SparkOnchainFeeSpeed = SparkOnchainFeeSpeed.FAST,
        useAllFunds: Boolean = false,
    ): SparkSendState.Preview =
        withContext(Dispatchers.IO) {
            val preview = prepareSendPreviewInternal(paymentRequest, amountSats, onchainFeeSpeed, useAllFunds)
            _sendState.value = preview
            preview
        }

    suspend fun getOnchainFeeQuotes(
        paymentRequest: String,
        amountSats: Long,
        useAllFunds: Boolean = false,
    ): List<SparkOnchainFeeQuote> =
        withContext(Dispatchers.IO) {
            val response = sdkOrThrow().prepareSendPayment(
                PrepareSendPaymentRequest(
                    paymentRequest = PaymentRequest.Input(paymentRequest),
                    amount = amountSats.toSparkBigInteger(),
                    tokenIdentifier = null,
                    conversionOptions = null,
                    feePolicy = if (useAllFunds) FeePolicy.FEES_INCLUDED else FeePolicy.FEES_EXCLUDED,
                ),
            )
            response.paymentMethod.onchainFeeQuotes()
        }

    suspend fun getRecommendedFeeEstimates(): FeeEstimates =
        withContext(Dispatchers.IO) {
            val fees = sdkOrThrow().recommendedFees()
            FeeEstimates(
                fastestFee = fees.fastestFee.toDouble(),
                halfHourFee = fees.halfHourFee.toDouble(),
                hourFee = fees.hourFee.toDouble(),
                minimumFee = fees.minimumFee.toDouble(),
                source = FeeEstimateSource.MEMPOOL_SPACE,
            )
        }

    suspend fun sendPrepared() = withContext(Dispatchers.IO) {
        _sendState.value = SparkSendState.Sending
        try {
            val result = sendPreparedInternal()
            preparedSend = null
            _sendState.value = SparkSendState.Sent(result?.id)
            refreshFromEvent()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            _sendState.value = SparkSendState.Error(e.safeMessage("Spark send failed"))
        }
    }

    suspend fun sendPreparedNow(): String? = withContext(Dispatchers.IO) {
        _sendState.value = SparkSendState.Sending
        try {
            val result = sendPreparedInternal()
            preparedSend = null
            _sendState.value = SparkSendState.Sent(result?.id)
            refreshFromEvent()
            result?.id
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            _sendState.value = SparkSendState.Error(e.safeMessage("Spark send failed"))
            throw e
        }
    }

    fun resetSendState() {
        preparedSend = null
        _sendState.value = SparkSendState.Idle
    }

    fun resetReceiveState() {
        _receiveState.value = SparkReceiveState.Idle
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
    ) = withContext(Dispatchers.IO) {
        refreshMutex.withLock {
            refreshStateInternal(mode, allowReconnectOnFailure)
        }
    }

    private suspend fun refreshStateInternal(
        mode: SparkRefreshMode,
        allowReconnectOnFailure: Boolean,
    ) {
        val (activeSdk, walletId) =
            mutex.withLock {
                val activeSdk = sdk ?: return
                val walletId = _loadedWalletId.value
                _sparkState.value = _sparkState.value.copy(isSyncing = true, error = null)
                SparkRefreshTarget(activeSdk, walletId)
            }
        try {
            if (mode.syncWallet) {
                withTimeout(SPARK_REFRESH_TIMEOUT_MS) {
                    activeSdk.syncWalletCompat()
                }
            }
            val info = activeSdk.getInfo(GetInfoRequest(ensureSynced = false))
            if (walletId != null) {
                localPendingDeposits = secureStorage.getAllSparkPendingDeposits(walletId)
            }
            val storedPaymentRecipients = walletId?.let { secureStorage.getAllSparkPaymentRecipients(it) }.orEmpty()
            val storedDepositAddresses = walletId?.let { secureStorage.getAllSparkDepositAddresses(it) }.orEmpty()
            val payments = activeSdk
                .listAllBitcoinPayments()
                .map { it.toSparkPayment(storedPaymentRecipients, storedDepositAddresses) }
            if (walletId != null) {
                secureStorage.saveSparkTransactionSources(
                    walletId,
                    payments.associate { payment -> payment.id to payment.method },
                )
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
            localPendingDeposits =
                localPendingDeposits.filter { pending ->
                    unclaimedDeposits.none { sparkDepositKey(it) == sparkDepositKey(pending) } &&
                        sparkDepositKey(pending) !in claimedDepositKeys
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
            if (walletId != null && depositKeys.any { it !in depositsRotatedForAddress }) {
                secureStorage.clearSparkOnchainDepositAddress(walletId)
            }
            val lightningAddress = runCatching { activeSdk.getLightningAddress()?.lightningAddress }.getOrNull()
            val refreshedState = SparkWalletState(
                walletId = walletId,
                isInitialized = true,
                identityPubkey = info.identityPubkey,
                balanceSats = info.balanceSats.toLong(),
                payments = visiblePayments,
                unclaimedDeposits = filteredUnclaimedDeposits,
                lightningAddress = lightningAddress,
                isSyncing = false,
                lastSyncTimestamp = System.currentTimeMillis(),
            )
            mutex.withLock {
                if (sdk !== activeSdk || _loadedWalletId.value != walletId) return
                depositsRotatedForAddress = depositKeys
                _sparkState.value = refreshedState
                walletId?.let { secureStorage.saveSparkWalletStateCache(it, refreshedState) }
                if (mode.syncWallet && walletId != null) {
                    recoverySyncCompletedForWalletId = walletId
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            val failure =
                when (e) {
                    is TimeoutCancellationException -> TimeoutException("Spark sync timed out")
                    else -> e
                }
            mutex.withLock {
                if (sdk !== activeSdk || _loadedWalletId.value != walletId) return
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
        }
    }

    private fun applyLoadingSparkStateLocked(walletId: String) {
        _sparkTransactionLabels.value = secureStorage.getAllSparkTransactionLabels(walletId)
        _sparkAddressLabels.value = secureStorage.getAllSparkAddressLabels(walletId)
        localPendingDeposits = secureStorage.getAllSparkPendingDeposits(walletId)
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

    private suspend fun connectWalletLocked(
        walletId: String,
        showConnectingStatus: Boolean = true,
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
            mutex.withLock {
                sdk = connectedSdk
                _loadedWalletId.value = walletId
                _isConnected.value = true
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

    private fun detachWalletLocked(): SparkSdkHandle? {
        val handle = sdk?.let { activeSdk ->
            SparkSdkHandle(
                sdk = activeSdk,
                listenerId = listenerId,
            )
        }
        sdk = null
        listenerId = null
        refreshMutex = Mutex()
        preparedSend = null
        depositsRotatedForAddress = emptySet()
        localPendingDeposits = emptyList()
        recoverySyncCompletedForWalletId = null
        _loadedWalletId.value = null
        _isConnected.value = false
        _isConnecting.value = false
        _sparkTransactionLabels.value = emptyMap()
        _sparkAddressLabels.value = emptyMap()
        _sparkState.value = SparkWalletState(isInitialized = true)
        _sendState.value = SparkSendState.Idle
        _receiveState.value = SparkReceiveState.Idle
        return handle
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
        val id =
            connectedSdk.addEventListener(
                SparkListener { event ->
                    // Always hop off the callback stack. SDK may re-enter onEvent from the same
                    // calling frame while we refresh/sync, which previously overflowed the stack.
                    eventScope.launch {
                        runCatching {
                            if (event is SdkEvent.PaymentSucceeded) {
                                handlePaymentSucceeded(event.payment)
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

    private fun handlePaymentSucceeded(payment: Payment) {
        if (payment.paymentType != PaymentType.RECEIVE) return
        if (payment.status != PaymentStatus.COMPLETED) return

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
            _receiveState.value =
                SparkReceiveState.Paid(
                    kind = openReady.kind,
                    paymentId = paymentId,
                    amountSats = amountSats,
                    paymentRequest = openReady.paymentRequest,
                )
        }

        _events.tryEmit(
            SparkEvent.PaymentReceived(
                paymentId = paymentId,
                amountSats = amountSats,
                kind = if (matchesOpenInvoice) openReady?.kind else paidKind,
            ),
        )
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

        data class Standard(
            override val paymentRequest: String,
            val response: PrepareSendPaymentResponse,
            val onchainFeeSpeed: SparkOnchainFeeSpeed,
            val feesIncluded: Boolean,
        ) : PreparedSparkSend
        data class Lnurl(
            override val paymentRequest: String,
            val response: PrepareLnurlPayResponse,
            val requestedAmountSats: Long,
            val feesIncluded: Boolean,
        ) : PreparedSparkSend
    }

    private suspend fun prepareSendPreviewInternal(
        paymentRequest: String,
        amountSats: Long?,
        onchainFeeSpeed: SparkOnchainFeeSpeed,
        useAllFunds: Boolean,
    ): SparkSendState.Preview {
        val activeSdk = sdkOrThrow()
        val amount = amountSats?.toSparkBigInteger()
        val parsed = runCatching { activeSdk.parse(paymentRequest) }.getOrNull()
        val prepared =
            when (val lnurlDetails = parsed?.lnurlPayDetails()) {
                null -> {
                    val response = activeSdk.prepareSendPayment(
                        PrepareSendPaymentRequest(
                            paymentRequest = PaymentRequest.Input(paymentRequest),
                            amount = amount,
                            tokenIdentifier = null,
                            conversionOptions = null,
                            feePolicy = if (useAllFunds) FeePolicy.FEES_INCLUDED else FeePolicy.FEES_EXCLUDED,
                        ),
                    )
                    PreparedSparkSend.Standard(paymentRequest, response, onchainFeeSpeed, useAllFunds)
                }
                else -> {
                    val requiredAmount = amount ?: throw IllegalArgumentException("Amount required for LNURL")
                    val response = activeSdk.prepareLnurlPay(
                        PrepareLnurlPayRequest(
                            amount = requiredAmount,
                            payRequest = lnurlDetails,
                            comment = null,
                            validateSuccessActionUrl = true,
                            tokenIdentifier = null,
                            conversionOptions = null,
                            feePolicy = if (useAllFunds) FeePolicy.FEES_INCLUDED else FeePolicy.FEES_EXCLUDED,
                        ),
                    )
                    PreparedSparkSend.Lnurl(paymentRequest, response, amountSats, useAllFunds)
                }
            }
        preparedSend = prepared
        return prepared.toPreview(paymentRequest, amountSats)
    }

    private suspend fun sendPreparedInternal(): Payment {
        val activeSdk = sdkOrThrow()
        val prepared = preparedSend ?: error("No prepared Spark payment")
        val payment =
            when (prepared) {
                is PreparedSparkSend.Standard -> activeSdk.sendPayment(
                    SendPaymentRequest(
                        prepareResponse = prepared.response,
                        options = prepared.response.paymentMethod.defaultOptions(prepared.onchainFeeSpeed),
                        idempotencyKey = UUID.randomUUID().toString(),
                    ),
                ).payment
                is PreparedSparkSend.Lnurl -> activeSdk.lnurlPay(
                    LnurlPayRequest(
                        prepareResponse = prepared.response,
                        idempotencyKey = UUID.randomUUID().toString(),
                    ),
                ).payment
            }
        val walletId = _loadedWalletId.value
        if (walletId != null) {
            secureStorage.saveSparkPaymentRecipient(walletId, payment.id, prepared.paymentRequest)
        }
        return payment
    }

    private fun PreparedSparkSend.toPreview(paymentRequest: String, amountSats: Long?): SparkSendState.Preview =
        when (this) {
            is PreparedSparkSend.Standard -> {
                val feeSats = response.paymentMethod.feeSats(onchainFeeSpeed)
                val grossAmountSats = response.amount.toLongSafe()
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
                SparkSendState.Preview(
                    paymentRequest = paymentRequest,
                    amountSats = if (feesIncluded) (requestedAmountSats - feeSats).coerceAtLeast(0L) else amountSats,
                    feeSats = feeSats,
                    method = "LNURL",
                )
            }
        }

    private fun SendPaymentMethod.feeSats(onchainFeeSpeed: SparkOnchainFeeSpeed): Long? =
        when (this) {
            is SendPaymentMethod.BitcoinAddress -> {
                val quote = when (onchainFeeSpeed) {
                    SparkOnchainFeeSpeed.SLOW -> feeQuote.speedSlow
                    SparkOnchainFeeSpeed.MEDIUM -> feeQuote.speedMedium
                    SparkOnchainFeeSpeed.FAST -> feeQuote.speedFast
                }
                (quote.userFeeSat + quote.l1BroadcastFeeSat).toLong()
            }
            is SendPaymentMethod.Bolt11Invoice -> lightningFeeSats.toLong()
            is SendPaymentMethod.SparkAddress -> fee.toLongSafe()
            is SendPaymentMethod.SparkInvoice -> fee.toLongSafe()
            is SendPaymentMethod.CrossChainAddress -> sourceTransferFeeSats.toLong()
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
        (userFeeSat + l1BroadcastFeeSat).toLong()

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
        val sdkTxids = sdkDeposits.map { it.txid }.toSet()
        return localPendingDeposits.filter { it.txid !in sdkTxids } + sdkDeposits
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
    ): SparkPayment =
        method.toString().let { methodDetails ->
        SparkPayment(
            id = id,
            type = paymentType.name,
            status = status.name,
            amountSats = amount.toLongSafe(),
            feeSats = fees.toLongSafe(),
            timestamp = timestamp.toLong(),
                method = methodDetails.substringAfterLast(".").substringBefore("("),
                recipient =
                    methodDetails.extractSparkRecipient()
                        ?: storedPaymentRecipients[id]
                        ?: storedDepositAddresses.firstNotNullOfOrNull { (txid, address) ->
                            address.takeIf { methodDetails.contains(txid, ignoreCase = true) }
                        },
                methodDetails = methodDetails,
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

    private fun Long.toSparkBigInteger(): BigInteger = BigInteger.valueOf(this)

    private fun Any?.toLongSafe(): Long =
        this?.toString()?.toLongOrNull() ?: 0L

    private fun Exception.safeMessage(fallback: String): String =
        when {
            isTransactionInsufficientFundsError() ->
                AppLocale.createLocalizedContext(context.applicationContext, secureStorage.getAppLocale())
                    .getString(R.string.loc_534e1eb2)
            else -> message?.takeIf { it.isNotBlank() } ?: fallback
        }

    companion object {
        private const val TAG = "SparkRepository"
        internal val SPARK_PAYMENT_HISTORY_PAGE_SIZE = 50u
        internal const val SPARK_PAYMENT_HISTORY_MAX = 1000
        private const val SPARK_REFRESH_TIMEOUT_MS = 120_000L
        private const val SPARK_DISCONNECT_TIMEOUT_MS = 15_000L
    }
}

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
}

internal const val SPARK_NETWORK_RECONNECT_DEBOUNCE_MS = 2_000L
internal const val SPARK_NETWORK_RECONNECT_MIN_INTERVAL_MS = 5_000L

internal object SparkPaymentHistoryPaging {
    fun nextOffset(currentOffset: UInt): UInt = currentOffset + SparkRepository.SPARK_PAYMENT_HISTORY_PAGE_SIZE
}

internal object SparkDepositClaimMatcher {
    fun matches(
        deposit: SparkUnclaimedDeposit,
        payment: SparkPayment,
    ): Boolean =
        payment.type.equals("RECEIVE", ignoreCase = true) &&
            isSettled(payment.status) &&
            payment.amountSats == deposit.amountSats &&
            payment.referencesTxid(deposit.txid)

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
        return listOf(id, method, methodDetails, recipient.orEmpty())
            .any { value -> value.lowercase(Locale.US).contains(normalizedTxid) }
    }
}
