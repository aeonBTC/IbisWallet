package github.aeonbtc.ibiswallet.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import github.aeonbtc.ibiswallet.data.local.SecureStorage
import github.aeonbtc.ibiswallet.data.model.Layer2Provider
import github.aeonbtc.ibiswallet.data.model.SparkOnchainFeeSpeed
import github.aeonbtc.ibiswallet.data.model.SparkReceiveKind
import github.aeonbtc.ibiswallet.data.model.SparkSendState
import github.aeonbtc.ibiswallet.data.repository.SparkRepository
import github.aeonbtc.ibiswallet.util.Bip329LabelNetwork
import github.aeonbtc.ibiswallet.util.Bip329LabelScope
import github.aeonbtc.ibiswallet.util.Bip329Labels
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SparkViewModel(application: Application) : AndroidViewModel(application) {
    private val secureStorage = SecureStorage.getInstance(application)
    private val repository = SparkRepository(application, secureStorage)

    val sparkState = repository.sparkState
    val sendState = repository.sendState
    val receiveState = repository.receiveState
    val sparkTransactionLabels = repository.sparkTransactionLabels
    val loadedWalletId = repository.loadedWalletId
    val isSparkConnected = repository.isConnected

    private val _isSparkLayer2Enabled = MutableStateFlow(secureStorage.isSparkLayer2Enabled())
    val isSparkLayer2Enabled: StateFlow<Boolean> = _isSparkLayer2Enabled.asStateFlow()

    private val _sendDraft = MutableStateFlow(SendScreenDraft())
    val sendDraft: StateFlow<SendScreenDraft> = _sendDraft.asStateFlow()

    private val _sparkEnabledWallets = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val sparkEnabledWallets: StateFlow<Map<String, Boolean>> = _sparkEnabledWallets

    fun loadSparkWallet(walletId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { repository.loadWallet(walletId) }
                .onFailure { error ->
                    repository.markLoadFailed(
                        walletId = walletId,
                        message = error.message?.takeIf { it.isNotBlank() } ?: "Spark wallet load failed",
                    )
                }
        }
    }

    fun unloadSparkWallet() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.unloadWallet()
        }
    }

    suspend fun deleteWalletData(walletId: String) {
        repository.deleteWalletData(walletId)
        _sparkEnabledWallets.value = _sparkEnabledWallets.value.toMutableMap().apply { remove(walletId) }
    }

    suspend fun prepareForFullWipe() {
        repository.unloadWallet()
    }

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.refreshState()
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
        repository.saveSparkAddressLabels(walletId, mapOf(addressOrRequest to label))
    }

    fun deleteSparkTransactionLabel(
        walletId: String,
        paymentId: String,
    ) {
        repository.deleteSparkTransactionLabel(walletId, paymentId)
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
        _sparkEnabledWallets.value = _sparkEnabledWallets.value.toMutableMap().apply {
            put(walletId, provider == Layer2Provider.SPARK)
        }
    }
}
