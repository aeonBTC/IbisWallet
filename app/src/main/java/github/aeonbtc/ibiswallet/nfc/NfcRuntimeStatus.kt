package github.aeonbtc.ibiswallet.nfc

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface NfcReaderUiState {
    data object Inactive : NfcReaderUiState

    data object Ready : NfcReaderUiState

    data object Detecting : NfcReaderUiState

    data object Received : NfcReaderUiState
}

sealed interface NfcShareUiState {
    data object Inactive : NfcShareUiState

    data object Ready : NfcShareUiState

    data object Sharing : NfcShareUiState
}

object NfcRuntimeStatus {
    private const val TAP_STATUS_MS = 1_200L
    private const val SUCCESS_STATUS_MS = 2_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _readerState = MutableStateFlow<NfcReaderUiState>(NfcReaderUiState.Inactive)
    val readerState: StateFlow<NfcReaderUiState> = _readerState.asStateFlow()

    private val _shareState = MutableStateFlow<NfcShareUiState>(NfcShareUiState.Inactive)
    val shareState: StateFlow<NfcShareUiState> = _shareState.asStateFlow()

    private var readerResetJob: Job? = null
    private var shareResetJob: Job? = null

    fun setReaderInactive() {
        readerResetJob?.cancel()
        _readerState.value = NfcReaderUiState.Inactive
    }

    fun setReaderReady() {
        readerResetJob?.cancel()
        _readerState.value = NfcReaderUiState.Ready
    }

    fun markReaderTagDetected() {
        if (_readerState.value == NfcReaderUiState.Inactive) return
        readerResetJob?.cancel()
        _readerState.value = NfcReaderUiState.Detecting
        scheduleReaderReset(TAP_STATUS_MS)
    }

    fun markReaderPayloadReceived() {
        if (_readerState.value == NfcReaderUiState.Inactive) return
        readerResetJob?.cancel()
        _readerState.value = NfcReaderUiState.Received
        scheduleReaderReset(SUCCESS_STATUS_MS)
    }

    fun setShareInactive() {
        shareResetJob?.cancel()
        _shareState.value = NfcShareUiState.Inactive
    }

    fun setShareReady() {
        shareResetJob?.cancel()
        _shareState.value = NfcShareUiState.Ready
    }

    fun markShareActivity() {
        if (_shareState.value == NfcShareUiState.Inactive) return
        shareResetJob?.cancel()
        _shareState.value = NfcShareUiState.Sharing
        scheduleShareReset()
    }

    fun restoreShareReadyIfPayloadPresent(hasPayload: Boolean) {
        if (hasPayload) {
            setShareReady()
        } else {
            setShareInactive()
        }
    }

    private fun scheduleReaderReset(delayMs: Long) {
        readerResetJob =
            scope.launch {
                delay(delayMs)
                if (_readerState.value != NfcReaderUiState.Inactive) {
                    _readerState.value = NfcReaderUiState.Ready
                }
            }
    }

    private fun scheduleShareReset() {
        shareResetJob =
            scope.launch {
                delay(TAP_STATUS_MS)
                if (_shareState.value != NfcShareUiState.Inactive) {
                    _shareState.value = NfcShareUiState.Ready
                }
            }
    }
}
