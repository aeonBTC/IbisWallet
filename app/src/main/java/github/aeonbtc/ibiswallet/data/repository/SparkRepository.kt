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
import breez_sdk_spark.UpdateUserSettingsRequest
import breez_sdk_spark.connect
import breez_sdk_spark.defaultConfig
import github.aeonbtc.ibiswallet.BuildConfig
import github.aeonbtc.ibiswallet.R
import github.aeonbtc.ibiswallet.data.local.SecureStorage
import github.aeonbtc.ibiswallet.data.model.FeeEstimateSource
import github.aeonbtc.ibiswallet.data.model.FeeEstimates
import github.aeonbtc.ibiswallet.data.model.SeedFormat
import github.aeonbtc.ibiswallet.data.model.SparkOnchainFeeQuote
import github.aeonbtc.ibiswallet.data.model.SparkOnchainFeeSpeed
import github.aeonbtc.ibiswallet.data.model.SparkPayment
import github.aeonbtc.ibiswallet.data.model.SparkReceiveKind
import github.aeonbtc.ibiswallet.data.model.SparkReceiveState
import github.aeonbtc.ibiswallet.data.model.SparkSendState
import github.aeonbtc.ibiswallet.data.model.SparkUnclaimedDeposit
import github.aeonbtc.ibiswallet.data.model.SparkWalletState
import github.aeonbtc.ibiswallet.localization.AppLocale
import github.aeonbtc.ibiswallet.util.isTransactionInsufficientFundsError
import github.aeonbtc.ibiswallet.util.SecureLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.math.BigInteger
import java.util.UUID

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

    private val _sparkTransactionLabels = MutableStateFlow<Map<String, String>>(emptyMap())
    val sparkTransactionLabels: StateFlow<Map<String, String>> = _sparkTransactionLabels.asStateFlow()

    private val _loadedWalletId = MutableStateFlow<String?>(null)
    val loadedWalletId: StateFlow<String?> = _loadedWalletId.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val mutex = Mutex()
    private var sdk: BreezSdk? = null
    private var listenerId: String? = null
    private var preparedSend: PreparedSparkSend? = null
    private var depositsRotatedForAddress: Set<String> = emptySet()
    private var localPendingDeposits: List<SparkUnclaimedDeposit> = emptyList()

    suspend fun loadWallet(walletId: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (_loadedWalletId.value == walletId && sdk != null) {
                refreshStateLocked()
                return@withLock
            }

            unloadLocked()

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

            val storageDir = File(context.filesDir, "spark/$walletId").apply { mkdirs() }
            val connectedSdk = connect(
                ConnectRequest(
                    config = config,
                    seed = Seed.Mnemonic(mnemonic, secureStorage.getPassphrase(walletId)),
                    storageDir = storageDir.absolutePath,
                ),
            )
            sdk = connectedSdk
            _loadedWalletId.value = walletId
            _isConnected.value = true
            _sparkTransactionLabels.value = secureStorage.getAllSparkTransactionLabels(walletId)
            localPendingDeposits = secureStorage.getAllSparkPendingDeposits(walletId)

            runCatching {
                connectedSdk.updateUserSettings(
                    UpdateUserSettingsRequest(sparkPrivateModeEnabled = true),
                )
            }.onFailure {
                SecureLog.w(TAG, "Spark private mode update failed", it, releaseMessage = "Spark privacy setup failed")
            }

            listenerId = connectedSdk.addEventListener(SparkListener { refreshStateLocked() })
            refreshStateLocked()
        }
    }

    suspend fun unloadWallet() = withContext(Dispatchers.IO) {
        mutex.withLock { unloadLocked() }
    }

    fun markLoadFailed(walletId: String, message: String) {
        _loadedWalletId.value = walletId
        _isConnected.value = false
        _sparkState.value = SparkWalletState(
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
    }

    suspend fun deleteWalletData(walletId: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (_loadedWalletId.value == walletId) {
                unloadLocked()
            }
            File(context.filesDir, "spark/$walletId").deleteRecursively()
        }
    }

    suspend fun refreshState() = withContext(Dispatchers.IO) {
        mutex.withLock { refreshStateLocked() }
    }

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
                    if (description.isNotBlank()) {
                        secureStorage.saveSparkAddressLabel(walletId, cachedAddress, description)
                    }
                    _receiveState.value = SparkReceiveState.Ready(
                        kind = kind,
                        paymentRequest = cachedAddress,
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
                if (kind == SparkReceiveKind.BITCOIN_ADDRESS && walletId != null) {
                    secureStorage.setSparkOnchainDepositAddress(walletId, response.paymentRequest)
                }
                if (walletId != null && description.isNotBlank()) {
                    secureStorage.saveSparkAddressLabel(walletId, response.paymentRequest, description)
                }
                _receiveState.value = SparkReceiveState.Ready(
                    kind = kind,
                    paymentRequest = response.paymentRequest,
                    feeSats = response.fee.toLongSafe(),
                )
            } catch (e: Exception) {
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
                    paymentRequest = paymentRequest,
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
            mutex.withLock { refreshStateLocked() }
        } catch (e: Exception) {
            _sendState.value = SparkSendState.Error(e.safeMessage("Spark send failed"))
        }
    }

    suspend fun sendPreparedNow(): String? = withContext(Dispatchers.IO) {
        _sendState.value = SparkSendState.Sending
        try {
            val result = sendPreparedInternal()
            preparedSend = null
            _sendState.value = SparkSendState.Sent(result?.id)
            mutex.withLock { refreshStateLocked() }
            result?.id
        } catch (e: Exception) {
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

    private suspend fun refreshStateLocked() {
        val activeSdk = sdk ?: return
        val walletId = _loadedWalletId.value
        _sparkState.value = _sparkState.value.copy(isSyncing = true, error = null)
        try {
            val info = activeSdk.getInfo(GetInfoRequest(ensureSynced = false))
            if (walletId != null) {
                localPendingDeposits = secureStorage.getAllSparkPendingDeposits(walletId)
            }
            val storedPaymentRecipients = walletId?.let { secureStorage.getAllSparkPaymentRecipients(it) }.orEmpty()
            val storedDepositAddresses = walletId?.let { secureStorage.getAllSparkDepositAddresses(it) }.orEmpty()
            val payments = activeSdk.listPayments(
                ListPaymentsRequest(
                    typeFilter = null,
                    statusFilter = null,
                    assetFilter = AssetFilter.Bitcoin,
                    paymentDetailsFilter = null,
                    fromTimestamp = null,
                    toTimestamp = null,
                    offset = 0u,
                    limit = 50u,
                    sortAscending = false,
                ),
            ).payments.map { it.toSparkPayment(storedPaymentRecipients, storedDepositAddresses) }
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
            localPendingDeposits =
                localPendingDeposits.filter { pending ->
                    unclaimedDeposits.none { it.txid == pending.txid }
                }
            if (walletId != null) {
                val sdkDepositTxids = unclaimedDeposits.map { it.txid }.toSet()
                sdkDepositTxids.forEach { txid ->
                    secureStorage.deleteSparkPendingDeposit(walletId, txid)
                }
            }
            val visibleUnclaimedDeposits = mergeLocalPendingDeposits(unclaimedDeposits)
            val depositKeys = visibleUnclaimedDeposits.map { "${it.txid}:${it.vout}" }.toSet()
            if (walletId != null && depositKeys.any { it !in depositsRotatedForAddress }) {
                secureStorage.clearSparkOnchainDepositAddress(walletId)
            }
            depositsRotatedForAddress = depositKeys
            val lightningAddress = runCatching { activeSdk.getLightningAddress()?.lightningAddress }.getOrNull()
            _sparkState.value = SparkWalletState(
                walletId = walletId,
                isInitialized = true,
                identityPubkey = info.identityPubkey,
                balanceSats = info.balanceSats.toLong(),
                payments = payments,
                unclaimedDeposits = visibleUnclaimedDeposits,
                lightningAddress = lightningAddress,
                isSyncing = false,
                lastSyncTimestamp = System.currentTimeMillis(),
            )
        } catch (e: Exception) {
            _sparkState.value = _sparkState.value.copy(
                isInitialized = true,
                isSyncing = false,
                error = e.safeMessage("Spark refresh failed"),
            )
        }
    }

    private suspend fun unloadLocked() {
        val activeSdk = sdk
        if (activeSdk != null) {
            listenerId?.let { id ->
                runCatching { activeSdk.removeEventListener(id) }
            }
            runCatching { activeSdk.disconnect() }
        }
        sdk = null
        listenerId = null
        preparedSend = null
        depositsRotatedForAddress = emptySet()
        localPendingDeposits = emptyList()
        _loadedWalletId.value = null
        _isConnected.value = false
        _sparkTransactionLabels.value = emptyMap()
        _sparkState.value = SparkWalletState(isInitialized = true)
        _sendState.value = SparkSendState.Idle
        _receiveState.value = SparkReceiveState.Idle
    }

    private fun sdkOrThrow(): BreezSdk =
        sdk ?: throw IllegalStateException(_sparkState.value.error ?: "Spark wallet is not loaded")

    private class SparkListener(
        private val onRefresh: suspend () -> Unit,
    ) : EventListener {
        override suspend fun onEvent(event: SdkEvent) {
            when (event) {
                is SdkEvent.Synced,
                is SdkEvent.PaymentSucceeded,
                is SdkEvent.PaymentPending,
                is SdkEvent.PaymentFailed,
                is SdkEvent.NewDeposits,
                is SdkEvent.ClaimedDeposits,
                is SdkEvent.UnclaimedDeposits,
                is SdkEvent.LightningAddressChanged,
                -> onRefresh()
                is SdkEvent.Optimization -> Unit
            }
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
                            paymentRequest = paymentRequest,
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
            else -> null
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
    }
}
