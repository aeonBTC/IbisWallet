package github.aeonbtc.ibiswallet

import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.cardemulation.CardEmulation
import android.nfc.tech.IsoDep
import android.nfc.tech.Ndef
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import github.aeonbtc.ibiswallet.data.local.SecureStorage
import github.aeonbtc.ibiswallet.localization.AppLocale
import github.aeonbtc.ibiswallet.localization.LocalAppLocale
import github.aeonbtc.ibiswallet.nfc.NdefHostApduService
import github.aeonbtc.ibiswallet.nfc.NfcPsbtCodec
import github.aeonbtc.ibiswallet.nfc.NfcPsbtExchange
import github.aeonbtc.ibiswallet.nfc.NfcReaderModeRequestRegistry
import github.aeonbtc.ibiswallet.nfc.NfcRuntimeStatus
import github.aeonbtc.ibiswallet.nfc.PsbtNfcEvent
import github.aeonbtc.ibiswallet.nfc.PsbtNfcReadError
import github.aeonbtc.ibiswallet.nfc.PsbtNfcWriteError
import github.aeonbtc.ibiswallet.ui.IbisWalletApp
import github.aeonbtc.ibiswallet.ui.screens.CalculatorScreen
import github.aeonbtc.ibiswallet.ui.screens.LockScreen
import github.aeonbtc.ibiswallet.ui.theme.IbisWalletTheme
import github.aeonbtc.ibiswallet.util.BiometricKeyInvalidatedException
import github.aeonbtc.ibiswallet.util.BiometricKeyUnavailableException
import github.aeonbtc.ibiswallet.util.SecureClipboard
import github.aeonbtc.ibiswallet.util.getNfcAvailability
import github.aeonbtc.ibiswallet.util.isRecognizedSendInput
import github.aeonbtc.ibiswallet.viewmodel.ArkViewModel
import github.aeonbtc.ibiswallet.viewmodel.LiquidViewModel
import github.aeonbtc.ibiswallet.viewmodel.SparkViewModel
import github.aeonbtc.ibiswallet.viewmodel.WalletViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : FragmentActivity() {
    companion object {
        private const val TAG = "MainActivity"

        private val NFC_SW_OK = byteArrayOf(0x90.toByte(), 0x00)
        private val NFC_SELECT_AID = byteArrayOf(
            0x00, 0xA4.toByte(), 0x04, 0x00, 0x07,
            0xD2.toByte(), 0x76, 0x00, 0x00, 0x85.toByte(), 0x01, 0x01,
        )
        private val NFC_SELECT_AID_WITH_LE = NFC_SELECT_AID + byteArrayOf(0x00)
        private val NFC_SELECT_CC_FILE = byteArrayOf(
            0x00, 0xA4.toByte(), 0x00, 0x0C, 0x02,
            0xE1.toByte(), 0x03,
        )
        private val NFC_READ_CC = byteArrayOf(
            0x00, 0xB0.toByte(), 0x00, 0x00, 0x0F,
        )
        private val NFC_SELECT_NDEF_FILE = byteArrayOf(
            0x00, 0xA4.toByte(), 0x00, 0x0C, 0x02,
            0xE1.toByte(), 0x04,
        )
        private val NFC_READ_NDEF_LENGTH = byteArrayOf(
            0x00, 0xB0.toByte(), 0x00, 0x00, 0x02,
        )
        private const val NFC_READ_CHUNK_SIZE = 59
        private const val NFC_MAX_SEND_NDEF_BYTES = 16_384
        private const val ACTIVITY_RESULT_LOCK_SKIP_WINDOW_MS = 30_000L
    }

    private lateinit var secureStorage: SecureStorage
    private lateinit var walletViewModel: WalletViewModel
    private lateinit var liquidViewModel: LiquidViewModel
    private lateinit var sparkViewModel: SparkViewModel
    private lateinit var arkViewModel: ArkViewModel
    private lateinit var lightningNodeViewModel: github.aeonbtc.ibiswallet.viewmodel.LightningNodeViewModel
    private var isUnlocked by mutableStateOf(false)
    private var appUnlockCounter by mutableIntStateOf(0)
    private var cloakBypassed by mutableStateOf(false)
    // Non-null when a PIN hash matched but its spend-master wrap did not
    // (the old non-atomic rotation crash). Holds the current PIN so the
    // previous-PIN recovery screen can re-wrap the master correctly.
    private var previousPinRecoveryCurrentPin by mutableStateOf<String?>(null)
    private var showBiometricEnrollmentWarning by mutableStateOf(false)
    private var biometricPrompt: BiometricPrompt? = null
    // Guards against overlapping BiometricPrompt.authenticate() calls. The app lock
    // screen has two auto-trigger paths that fire together on foreground return
    // (LockScreen LaunchedEffect + onResume), and a second authenticate() while one
    // is already showing cancels the first dialog — the in-flight fingerprint touch
    // is then lost and the prompt appears dead. While true, new requests are ignored;
    // the flag clears on success/error so the fingerprint button stays retryable.
    @Volatile private var isBiometricAuthInProgress = false
    private var wasInBackground = false
    // Set when the activity was stopped while a system picker was open. The
    // picker result may arrive before the skip expires; if it does not, the
    // normal lock timing is enforced on resume instead of being skipped forever.
    private var backgroundedDuringActivityResultSkip = false
    private var skipBackgroundLockUntilElapsedMs = 0L
    private val biometricAutoCancelHandler = Handler(Looper.getMainLooper())
    private var isCloakActive = false
    private var screenOffReceiverRegistered = false
    private val screenOffReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(
                context: android.content.Context?,
                intent: Intent?,
            ) {
                if (intent?.action != Intent.ACTION_SCREEN_OFF) return
                if (!::secureStorage.isInitialized) return
                val securityMethod = secureStorage.getSecurityMethod()
                if (securityMethod == SecureStorage.SecurityMethod.NONE) return
                if (secureStorage.getLockTiming() != SecureStorage.LockTiming.ON_SCREEN_OFF) return
                // Screen-off is a hard lock: do not let an in-flight system
                // picker keep the wallet unlocked indefinitely.
                if (!isUnlocked) return
                lockApp()
            }
        }

    // NFC reader mode — enabled when Send/Balance screen is active.
    // Uses enableReaderMode() instead of enableForegroundDispatch() to suppress
    // NFC peer-to-peer negotiation, which prevents two phones from communicating
    // when one uses HCE (broadcasting) and the other reads.
    private var nfcAdapter: NfcAdapter? = null
    private val nfcReaderRequests = NfcReaderModeRequestRegistry()
    private val nfcHceRequests = NfcReaderModeRequestRegistry()
    var isNfcReaderModeActive by mutableStateOf(false)
        private set
    var isPreferredHceServiceActive by mutableStateOf(false)
        private set

    /**
     * PSBT NFC signing session (PsbtScreen and SignTransactionScreen). While armed, taps are
     * routed to the PSBT read/write path instead of the address handoff.
     * Export holds the unsigned PSBT base64 to push; import reads a signed
     * PSBT (or finalized txn) back. Both cleared on disarm.
     */
    private val _psbtNfcEvents = MutableSharedFlow<PsbtNfcEvent>(extraBufferCapacity = 8)
    val psbtNfcEvents: SharedFlow<PsbtNfcEvent> = _psbtNfcEvents.asSharedFlow()
    // Written on the UI thread (arm/disarm), read on the reader callback
    // thread — volatile for cross-thread visibility.
    @Volatile
    private var psbtNfcExportBase64: String? = null
    @Volatile
    private var psbtNfcImportArmed: Boolean = false

    private val psbtHceShareOwner = Any()

    // Held while a PSBT share screen is visible. Kept across onPause (so
    // onResume can re-present it) but the HCE payload is disarmed in the
    // background; cleared entirely on explicit disarm.
    @Volatile
    private var psbtHceSharePayload: ByteArray? = null

    fun armPsbtNfcExport(psbtBase64: String) {
        psbtNfcExportBase64 = psbtBase64
        psbtNfcImportArmed = false
    }

    fun armPsbtNfcImport() {
        psbtNfcExportBase64 = null
        psbtNfcImportArmed = true
    }

    fun disarmPsbtNfc() {
        psbtNfcExportBase64 = null
        psbtNfcImportArmed = false
    }

    /**
     * Present a PSBT via HCE so another phone can pull it with an NFC tap
     * (phone-to-phone handoff). The importing phone reads it through the
     * normal PSBT import path — no separate protocol. Returns false when NFC
     * cannot broadcast or the payload is missing/oversized.
     *
     * HCE is promiscuous: any nearby reader receives the payload while armed.
     * Callers arm explicitly from a share screen and disarm on dispose,
     * cancel, timeout, or app lock (see [lockApp]).
     */
    fun armPsbtHceShare(psbtBase64: String): Boolean {
        val message = NfcPsbtCodec.buildShareMessage(psbtBase64) ?: return false
        if (!getNfcAvailability(secureStorage.isNfcEnabled()).canBroadcast) return false
        psbtHceSharePayload = message
        NdefHostApduService.armPsbtShare(message)
        requestPreferredHceService(psbtHceShareOwner)
        return true
    }

    /** Stop presenting a shared PSBT via HCE. */
    fun disarmPsbtHceShare() {
        psbtHceSharePayload = null
        NdefHostApduService.disarmPsbtShare()
        releasePreferredHceService(psbtHceShareOwner)
    }

    // HCE can be bound by a nearby reader even while the app is backgrounded,
    // so the PSBT payload is disarmed in onPause but remembered here; onResume
    // re-presents it if the share screen is still visible.
    private fun suspendPsbtHceShare() {
        if (psbtHceSharePayload != null) {
            NdefHostApduService.disarmPsbtShare()
        }
    }

    private fun resumePsbtHceShare() {
        psbtHceSharePayload?.let { NdefHostApduService.armPsbtShare(it) }
    }

    fun isPsbtNfcSessionActive(): Boolean = psbtNfcExportBase64 != null || psbtNfcImportArmed

    /**
     * NFC ReaderCallback — invoked on a background thread when a tag is detected.
     * Reads NDEF message from the tag (or HCE peer), extracts a supported send payload,
     * and posts it to the ViewModel on the main thread.
     */
    private val nfcReaderCallback = NfcAdapter.ReaderCallback { tag: Tag ->
        NfcRuntimeStatus.markReaderTagDetected()
        if (isPsbtNfcSessionActive()) {
            lifecycleScope.launch(Dispatchers.IO) { handlePsbtNfcTag(tag) }
            return@ReaderCallback
        }
        val sendInput = readSendInputFromTag(tag) ?: return@ReaderCallback
        NfcRuntimeStatus.markReaderPayloadReceived()
        runOnUiThread {
            walletViewModel.setPendingSendInput(
                sendInput,
                WalletViewModel.PendingSendOrigin.NFC,
            )
        }
    }

    /**
     * PSBT NFC tap: push the armed unsigned PSBT to the signer, or read a
     * signed PSBT / finalized transaction back. Results go to
     * [psbtNfcEvents]; payload bytes are never logged.
     */
    private suspend fun handlePsbtNfcTag(tag: Tag) {
        val exportBase64 = psbtNfcExportBase64
        if (exportBase64 != null) {
            val psbtBytes =
                runCatching {
                    android.util.Base64.decode(exportBase64, android.util.Base64.DEFAULT)
                }.getOrNull()
            if (psbtBytes == null || psbtBytes.isEmpty()) {
                _psbtNfcEvents.tryEmit(PsbtNfcEvent.WriteFailed(PsbtNfcWriteError.IO))
                return
            }
            val error = NfcPsbtExchange.writePsbt(tag, psbtBytes)
            if (error == null) {
                NfcRuntimeStatus.markReaderPayloadReceived()
                _psbtNfcEvents.tryEmit(PsbtNfcEvent.WriteSucceeded)
            } else {
                _psbtNfcEvents.tryEmit(PsbtNfcEvent.WriteFailed(error))
            }
            return
        }
        if (psbtNfcImportArmed) {
            when (val result = NfcPsbtExchange.readPsbt(tag)) {
                is NfcPsbtExchange.Result.Success -> {
                    NfcRuntimeStatus.markReaderPayloadReceived()
                    _psbtNfcEvents.tryEmit(
                        PsbtNfcEvent.ReadSucceeded(
                            data = result.value.data,
                            isFinalTxn = result.value.isFinalTxn,
                            checksumVerified = result.value.checksumVerified,
                        ),
                    )
                }
                is NfcPsbtExchange.Result.Failure -> {
                    val error =
                        when (result.error) {
                            PsbtNfcReadError.TAG_NOT_SUPPORTED,
                            PsbtNfcReadError.EMPTY,
                            -> PsbtNfcReadError.EMPTY
                            else -> result.error
                        }
                    _psbtNfcEvents.tryEmit(PsbtNfcEvent.ReadFailed(error))
                }
            }
        }
    }

    private fun readSendInputFromTag(tag: Tag): String? {
        return readSendInputFromCachedNdef(tag) ?: readSendInputFromIsoDep(tag)
    }

    /**
     * Fast path for regular NDEF tags where Android already cached the NDEF message.
     */
    private fun readSendInputFromCachedNdef(tag: Tag): String? {
        val ndef = Ndef.get(tag) ?: return null
        val message = ndef.cachedNdefMessage ?: return null
        return extractRecognizedSendInput(message)
    }

    /**
     * Fallback path for HCE peers that expose ISO-DEP but do not surface a cached NDEF tag.
     * Mirrors the NFC Forum Type 4 read sequence used by Phoenix.
     */
    private fun readSendInputFromIsoDep(tag: Tag): String? {
        val isoDep = IsoDep.get(tag) ?: return null

        return try {
            isoDep.timeout = maxOf(isoDep.timeout, 5_000)
            isoDep.connect()

            val selectAidResponse = transceiveSelectAid(isoDep)
            if (!selectAidResponse.contentEquals(NFC_SW_OK)) {
                if (BuildConfig.DEBUG) {
                    Log.w(TAG, "Unexpected SELECT AID response: ${selectAidResponse.toHexString()}")
                }
                return null
            }

            val selectCcResponse = isoDep.transceive(NFC_SELECT_CC_FILE)
            if (!selectCcResponse.contentEquals(NFC_SW_OK)) {
                if (BuildConfig.DEBUG) {
                    Log.w(TAG, "Unexpected SELECT CC response: ${selectCcResponse.toHexString()}")
                }
                return null
            }

            val readCcResponse = isoDep.transceive(NFC_READ_CC)
            if (!hasSuccessStatus(readCcResponse)) {
                if (BuildConfig.DEBUG) {
                    Log.w(TAG, "Unexpected READ CC response: ${readCcResponse.toHexString()}")
                }
                return null
            }

            val selectNdefResponse = isoDep.transceive(NFC_SELECT_NDEF_FILE)
            if (!selectNdefResponse.contentEquals(NFC_SW_OK)) {
                if (BuildConfig.DEBUG) {
                    Log.w(TAG, "Unexpected SELECT NDEF response: ${selectNdefResponse.toHexString()}")
                }
                return null
            }

            val lengthResponse = isoDep.transceive(NFC_READ_NDEF_LENGTH)
            if (!hasSuccessStatus(lengthResponse)) {
                if (BuildConfig.DEBUG) {
                    Log.w(TAG, "Unexpected NDEF length response: ${lengthResponse.toHexString()}")
                }
                return null
            }

            val lengthBytes = responsePayload(lengthResponse)
            if (lengthBytes.size != 2) {
                if (BuildConfig.DEBUG) {
                    Log.w(TAG, "Unexpected NDEF length payload size: ${lengthBytes.size}")
                }
                return null
            }

            val messageLength = ((lengthBytes[0].toInt() and 0xFF) shl 8) or (lengthBytes[1].toInt() and 0xFF)
            if (messageLength <= 0 || messageLength > NFC_MAX_SEND_NDEF_BYTES) return null

            val messageBytes = ByteArray(messageLength)
            var bytesRead = 0
            var offset = 2

            while (bytesRead < messageLength) {
                val chunkSize = minOf(NFC_READ_CHUNK_SIZE, messageLength - bytesRead)
                val response =
                    isoDep.transceive(
                        byteArrayOf(
                            0x00,
                            0xB0.toByte(),
                            ((offset shr 8) and 0xFF).toByte(),
                            (offset and 0xFF).toByte(),
                            chunkSize.toByte(),
                        ),
                    )

                if (!hasSuccessStatus(response)) {
                    if (BuildConfig.DEBUG) {
                        Log.w(TAG, "Unexpected READ NDEF response at offset $offset: ${response.toHexString()}")
                    }
                    return null
                }

                val chunk = responsePayload(response)
                if (chunk.isEmpty()) {
                    if (BuildConfig.DEBUG) {
                        Log.w(TAG, "Empty READ NDEF chunk at offset $offset")
                    }
                    return null
                }

                chunk.copyInto(messageBytes, destinationOffset = bytesRead)
                bytesRead += chunk.size
                offset += chunk.size
            }

            extractRecognizedSendInput(NdefMessage(messageBytes))
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) {
                Log.w(TAG, "Failed to read NFC tag via ISO-DEP", e)
            }
            null
        } finally {
            runCatching { isoDep.close() }
        }
    }

    private fun transceiveSelectAid(isoDep: IsoDep): ByteArray {
        val primaryResponse = isoDep.transceive(NFC_SELECT_AID)
        return if (primaryResponse.contentEquals(NFC_SW_OK)) {
            primaryResponse
        } else {
            isoDep.transceive(NFC_SELECT_AID_WITH_LE)
        }
    }

    private fun extractRecognizedSendInput(message: NdefMessage): String? {
        for (record in message.records) {
            val text = parseNdefRecord(record) ?: continue
            val trimmed = text.trim()
            if (isRecognizedSendInput(trimmed)) {
                return trimmed
            }
        }
        return null
    }

    private fun hasSuccessStatus(response: ByteArray): Boolean {
        return response.size >= 2 && response.takeLast(2).toByteArray().contentEquals(NFC_SW_OK)
    }

    private fun responsePayload(response: ByteArray): ByteArray {
        if (response.size <= 2) return byteArrayOf()
        return response.copyOfRange(0, response.size - 2)
    }

    private fun ByteArray.toHexString(): String = joinToString("") { "%02X".format(it) }

    /**
     * Request NFC reader mode for a visible screen. Reader mode remains enabled until
     * all screen owners release their request, so one screen disposing cannot disable
     * another screen that still needs NFC.
     */
    fun requestNfcReaderMode(owner: Any) {
        nfcReaderRequests.request(owner)
        activateNfcReaderMode()
    }

    /**
     * Release a previously registered NFC reader mode request.
     */
    fun releaseNfcReaderMode(owner: Any) {
        nfcReaderRequests.release(owner)
        if (!nfcReaderRequests.hasActiveRequests()) {
            deactivateNfcReaderMode()
        }
    }

    /**
     * Prefer the Ibis HCE service while a receive screen is visible so Android routes
     * incoming NFC taps to this app instead of any competing HCE service.
     */
    fun requestPreferredHceService(owner: Any) {
        nfcHceRequests.request(owner)
        activatePreferredHceService()
    }

    /**
     * Release a previously registered HCE preference request.
     */
    fun releasePreferredHceService(owner: Any) {
        nfcHceRequests.release(owner)
        if (!nfcHceRequests.hasActiveRequests()) {
            deactivatePreferredHceService()
        }
    }

    /**
     * System pickers briefly stop this activity. Keep the app unlocked so their
     * activity result can resume the in-flight operation.
     */
    fun skipNextBackgroundLockForActivityResult() {
        skipBackgroundLockUntilElapsedMs =
            SystemClock.elapsedRealtime() + ACTIVITY_RESULT_LOCK_SKIP_WINDOW_MS
    }

    private fun isActivityResultLockSkipActive(): Boolean =
        SystemClock.elapsedRealtime() <= skipBackgroundLockUntilElapsedMs

    private fun activateNfcReaderMode() {
        if (isNfcReaderModeActive || !nfcReaderRequests.hasActiveRequests()) return

        val nfcAvailability = getNfcAvailability(secureStorage.isNfcEnabled())
        if (!nfcAvailability.hasHardware || !nfcAvailability.isAppEnabled) {
            isNfcReaderModeActive = false
            NfcRuntimeStatus.setReaderInactive()
            return
        }
        if (!nfcAvailability.isSystemEnabled) {
            isNfcReaderModeActive = false
            NfcRuntimeStatus.setReaderInactive()
            if (BuildConfig.DEBUG) {
                Log.w(TAG, "NFC reader mode unavailable because system NFC is disabled")
            }
            return
        }

        val adapter = nfcAdapter
        if (adapter == null) {
            isNfcReaderModeActive = false
            NfcRuntimeStatus.setReaderInactive()
            if (BuildConfig.DEBUG) {
                Log.w(TAG, "NFC reader mode unavailable because the adapter could not be acquired")
            }
            return
        }

        val flags = NfcAdapter.FLAG_READER_NFC_A or
            NfcAdapter.FLAG_READER_NFC_B or
            NfcAdapter.FLAG_READER_NFC_F or
            NfcAdapter.FLAG_READER_NFC_V or
            NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS
        try {
            adapter.enableReaderMode(this, nfcReaderCallback, flags, null)
            isNfcReaderModeActive = true
            NfcRuntimeStatus.setReaderReady()
        } catch (e: Exception) {
            isNfcReaderModeActive = false
            NfcRuntimeStatus.setReaderInactive()
            if (BuildConfig.DEBUG) {
                Log.w(TAG, "Failed to enable NFC reader mode", e)
            }
        }
    }

    private fun deactivateNfcReaderMode() {
        if (!isNfcReaderModeActive) return
        try {
            nfcAdapter?.disableReaderMode(this)
        } catch (_: Exception) { }
        isNfcReaderModeActive = false
        NfcRuntimeStatus.setReaderInactive()
    }

    private fun activatePreferredHceService() {
        if (isPreferredHceServiceActive || !nfcHceRequests.hasActiveRequests()) return

        val adapter = nfcAdapter
        if (adapter == null) {
            isPreferredHceServiceActive = false
            if (BuildConfig.DEBUG) {
                Log.w(TAG, "HCE preference unavailable because the NFC adapter could not be acquired")
            }
            return
        }

        val cardEmulation =
            runCatching { CardEmulation.getInstance(adapter) }
                .getOrNull()
        if (cardEmulation == null) {
            isPreferredHceServiceActive = false
            if (BuildConfig.DEBUG) {
                Log.w(TAG, "HCE preference unavailable because CardEmulation is not supported")
            }
            return
        }

        val didSetPreferred =
            runCatching {
                cardEmulation.setPreferredService(
                    this,
                    ComponentName(this, NdefHostApduService::class.java),
                )
            }.getOrElse { error ->
                if (BuildConfig.DEBUG) {
                    Log.w(TAG, "Failed to prefer Ibis HCE service", error)
                }
                false
            }

        isPreferredHceServiceActive = didSetPreferred
        if (!didSetPreferred) {
            if (BuildConfig.DEBUG) {
                Log.w(TAG, "Android refused to prefer the Ibis HCE service")
            }
        }
    }

    private fun deactivatePreferredHceService() {
        val adapter = nfcAdapter
        if (adapter != null) {
            runCatching {
                CardEmulation.getInstance(adapter).unsetPreferredService(this)
            }.onFailure { error ->
                if (BuildConfig.DEBUG) {
                    Log.w(TAG, "Failed to clear preferred Ibis HCE service", error)
                }
            }
        }
        isPreferredHceServiceActive = false
    }

    /**
     * Apply any pending launcher icon alias swap. Called early in onCreate
     * before any UI to avoid the Android 10+ process-kill race.
     */
    private fun applyPendingIconSwap() {
        val pending = secureStorage.getPendingIconAlias() ?: return
        val current = secureStorage.getCurrentIconAlias()
        if (pending == current) {
            secureStorage.clearPendingIconAlias()
            return
        }

        val pm = packageManager
        val pkg = packageName
        val allAliases = listOf(SecureStorage.ALIAS_DEFAULT, SecureStorage.ALIAS_CALCULATOR)

        for (alias in allAliases) {
            val state =
                if (alias == pending) {
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                } else {
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                }

            pm.setComponentEnabledSetting(
                ComponentName(pkg, "$pkg$alias"),
                state,
                PackageManager.DONT_KILL_APP,
            )
        }

        secureStorage.setCurrentIconAlias(pending)
        secureStorage.clearPendingIconAlias()
    }

    /**
     * Disguise the app in the recent apps / task switcher when cloak mode is active.
     */
    @Suppress("DEPRECATION")
    private fun updateTaskDescription() {
        if (isCloakActive) {
            val icon =
                android.graphics.BitmapFactory.decodeResource(
                    resources,
                    R.mipmap.ic_launcher_calculator,
                )
            setTaskDescription(
                ActivityManager.TaskDescription(
                    getString(R.string.cloak_calculator_label),
                    icon,
                ),
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

            // Prevent tapjacking/overlay attacks
            window.decorView.filterTouchesWhenObscured = true

            secureStorage =
                try {
                    SecureStorage.getInstance(this)
                } catch (e: SecureStorage.UnavailableException) {
                    Log.e(TAG, "Secure storage unavailable: ${e.message}", e)
                    Toast.makeText(
                        this,
                        getString(R.string.storage_unavailable),
                        Toast.LENGTH_LONG,
                    ).show()
                    finish()
                    return
                }
            walletViewModel = ViewModelProvider(this)[WalletViewModel::class.java]
            liquidViewModel = ViewModelProvider(this)[LiquidViewModel::class.java]
            sparkViewModel = ViewModelProvider(this)[SparkViewModel::class.java]
            arkViewModel = ViewModelProvider(this)[ArkViewModel::class.java]
            lightningNodeViewModel =
                ViewModelProvider(this)[github.aeonbtc.ibiswallet.viewmodel.LightningNodeViewModel::class.java]
            nfcAdapter = NfcAdapter.getDefaultAdapter(this)

            // Apply pending icon swap before any UI
            applyPendingIconSwap()

            // Check cloak mode
            isCloakActive = secureStorage.isCloakModeEnabled() && secureStorage.hasCloakCode()
            updateTaskDescription()

            // Apply screenshot prevention if enabled
            if (secureStorage.getDisableScreenshots()) {
                window.setFlags(
                    WindowManager.LayoutParams.FLAG_SECURE,
                    WindowManager.LayoutParams.FLAG_SECURE,
                )
            }

            registerScreenOffReceiver()

            // Check if security is enabled - locked on fresh start, but preserve the
            // unlocked session across configuration-change recreations (the ViewModel
            // survives them; a process death resets the flag and re-locks normally).
            // When cloak mode is active, stay locked so the calculator screen shows first.
            val securityMethod = secureStorage.getSecurityMethod()
            isUnlocked =
                (securityMethod == SecureStorage.SecurityMethod.NONE && !isCloakActive) ||
                    walletViewModel.isSessionUnlocked()
            cloakBypassed = walletViewModel.isCloakBypassedThisSession()
            if (isUnlocked) {
                walletViewModel.onAppUnlocked()
            }

            // Setup biometric prompt. Never touch the Keystore key here: a
            // startup prewarm used to delete/recreate an invalidated legacy
            // key, destroying the only wrap that could still recover money.
            setupBiometricPrompt()

            // Check for incoming send intent
            handleSendIntent(intent)

        setContent {
            val appLocale by walletViewModel.appLocale.collectAsStateWithLifecycle()
            val themeMode by walletViewModel.themeModeState.collectAsStateWithLifecycle()
            val typeface by walletViewModel.typefaceState.collectAsStateWithLifecycle()
            val localizedContext = remember(appLocale) {
                AppLocale.createLocalizedContext(this, appLocale)
            }

            CompositionLocalProvider(
                LocalActivityResultRegistryOwner provides this,
                LocalAppLocale provides appLocale,
                LocalContext provides this,
                LocalConfiguration provides localizedContext.resources.configuration,
                LocalResources provides localizedContext.resources,
            ) {
                IbisWalletTheme(themeMode = themeMode, typeface = typeface) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background,
                    ) {
                        val recoveryCurrentPin = previousPinRecoveryCurrentPin
                        if (recoveryCurrentPin != null) {
                            LockScreen(
                                securityMethod = SecureStorage.SecurityMethod.PIN,
                                promptMessage = stringResource(R.string.pin_recovery_prompt),
                                randomizePinPad = secureStorage.getRandomizePinPad(),
                                isBiometricAvailable = false,
                                onBiometricRequest = {},
                                onCancel = { previousPinRecoveryCurrentPin = null },
                                onPinEntered = { previousPin ->
                                    val recovered =
                                        withContext(Dispatchers.Default) {
                                            runCatching {
                                                secureStorage.unlockSpendSecretsWithPreviousPin(
                                                    currentPin = recoveryCurrentPin,
                                                    previousPin = previousPin,
                                                )
                                            }.getOrDefault(false)
                                        }
                                    if (recovered) {
                                        previousPinRecoveryCurrentPin = null
                                        unlockApp(incrementCounter = true)
                                        true
                                    } else {
                                        if (secureStorage.shouldAutoWipe()) performFullWipe()
                                        false
                                    }
                                },
                            )
                        } else if (isCloakActive && !cloakBypassed) {
                            // Cloak gate comes before the unlocked session check:
                            // a configuration-change recreation must not skip the
                            // calculator and open the wallet because the process
                            // session is still unlocked.
                            CalculatorScreen(
                                codeMatches = { code -> secureStorage.codeMatchesCloak(code) },
                                onUnlock = {
                                    cloakBypassed = true
                                    walletViewModel.markCloakBypassedThisSession()
                                    val secMethod = secureStorage.getSecurityMethod()
                                    if (secMethod == SecureStorage.SecurityMethod.NONE) {
                                        // No additional auth — go straight to wallet
                                        unlockApp(incrementCounter = false)
                                    }
                                    // Otherwise fall through to LockScreen on next recompose
                                },
                                wipeCodeMatches =
                                    if (secureStorage.isWipePinEnabled()) {
                                        { code -> secureStorage.verifyWipePin(code) }
                                    } else {
                                        null
                                    },
                                onWipe =
                                    if (secureStorage.isWipePinEnabled()) {
                                        { performFullWipe() }
                                    } else {
                                        null
                                    },
                            )
                        } else if (isUnlocked) {
                            IbisWalletApp(
                                onLockApp = { lockApp() },
                                onAutoWipe = { performFullWipe() },
                                appUnlockCounter = appUnlockCounter,
                            )
                        } else {
                            val biometricManager = BiometricManager.from(this)
                            val isBiometricAvailable =
                                biometricManager.canAuthenticate(
                                    BiometricManager.Authenticators.BIOMETRIC_STRONG,
                                ) == BiometricManager.BIOMETRIC_SUCCESS

                            val secMethod = secureStorage.getSecurityMethod()
                            val isDuressEnabled = secureStorage.isDuressEnabled()
                            val isDuressWithBiometric =
                                isDuressEnabled &&
                                    secMethod == SecureStorage.SecurityMethod.BIOMETRIC

                            LockScreen(
                                securityMethod = secMethod,
                                onPinEntered = { pin ->
                                // Route via side-effect-free hash probes so duress/wipe
                                // entries never ratchet the real-PIN failure counter that
                                // auto-wipe depends on. The kept PIN wrap is a documented
                                // fallback: accept the real PIN when biometric is active
                                // (except in duress mode, where the real PIN is hidden and
                                // only the decoy PIN works at the pad).
                                val allowRealPin =
                                    secMethod == SecureStorage.SecurityMethod.PIN ||
                                        (
                                            secMethod == SecureStorage.SecurityMethod.BIOMETRIC &&
                                                !isDuressEnabled
                                        )
                                var needsPreviousPinRecovery = false
                                val (isRealPin, isDuressPin, isWipePin) =
                                    withContext(Dispatchers.Default) {
                                        // All three probes run unconditionally so entry
                                        // type is not leaked via timing.
                                        val matchesReal =
                                            allowRealPin && secureStorage.pinMatchesCurrent(pin)
                                        val matchesDuress =
                                            isDuressEnabled && secureStorage.pinMatchesDuress(pin)
                                        val matchesWipe = secureStorage.pinMatchesWipe(pin)
                                        when {
                                            // Hash matched above; verify performs the unlock
                                            // side effects. No failure was recorded.
                                            matchesReal -> {
                                                val verified = secureStorage.verifyPin(pin)
                                                if (!verified &&
                                                    !secureStorage.isPinLockedOut() &&
                                                    secureStorage.canRecoverSpendMasterWithPreviousPin()
                                                ) {
                                                    // New hash + old wrap from a crash during
                                                    // the old non-atomic rotation. Offer the
                                                    // previous PIN instead of silently failing.
                                                    needsPreviousPinRecovery = true
                                                }
                                                Triple(verified, false, false)
                                            }
                                            matchesDuress ->
                                                Triple(
                                                    false,
                                                    secureStorage.verifyDuressPin(
                                                        pin,
                                                        incrementFailedAttempts = false,
                                                    ),
                                                    false,
                                                )
                                            matchesWipe ->
                                                Triple(false, false, secureStorage.verifyWipePin(pin))
                                            else -> {
                                                // Wrong PIN — record exactly one failed attempt.
                                                if (allowRealPin) {
                                                    Triple(secureStorage.verifyPin(pin), false, false)
                                                } else {
                                                    Triple(
                                                        false,
                                                        isDuressEnabled &&
                                                            secureStorage.verifyDuressPin(
                                                                pin,
                                                                incrementFailedAttempts = true,
                                                            ),
                                                        false,
                                                    )
                                                }
                                            }
                                        }
                                    }

                                if (needsPreviousPinRecovery) {
                                    previousPinRecoveryCurrentPin = pin
                                }
                                when {
                                    isRealPin -> {
                                        // Await wallet switch so the main UI never flashes decoy state.
                                        withContext(Dispatchers.Main) {
                                            walletViewModel.exitDuressMode()
                                        }
                                        unlockApp(incrementCounter = true)
                                        true
                                    }
                                    isDuressPin -> {
                                        // Await decoy load before unlock — never show real balances.
                                        val entered =
                                            withContext(Dispatchers.Main) {
                                                walletViewModel.enterDuressMode()
                                            }
                                        if (entered) {
                                            unlockApp(incrementCounter = true)
                                        }
                                        entered
                                    }
                                    isWipePin || secureStorage.shouldAutoWipe() -> {
                                        performFullWipe()
                                        false
                                    }
                                    else -> false
                                }
                                },
                                onBiometricRequest = {
                                    showBiometricPrompt(isDuressWithBiometric)
                                },
                                isBiometricAvailable = isBiometricAvailable,
                                randomizePinPad = secureStorage.getRandomizePinPad(),
                                isDuressWithBiometric = isDuressWithBiometric,
                            )
                        }

                        if (showBiometricEnrollmentWarning) {
                            androidx.compose.material3.AlertDialog(
                                onDismissRequest = { showBiometricEnrollmentWarning = false },
                                title = {
                                    androidx.compose.material3.Text(
                                        stringResource(R.string.biometric_enrollment_changed_title),
                                    )
                                },
                                text = {
                                    androidx.compose.material3.Text(
                                        stringResource(R.string.biometric_enrollment_changed_message),
                                    )
                                },
                                confirmButton = {
                                    androidx.compose.material3.Button(
                                        onClick = { showBiometricEnrollmentWarning = false },
                                        modifier = Modifier.widthIn(min = 96.dp).heightIn(min = 40.dp),
                                        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                                        colors =
                                            androidx.compose.material3.ButtonDefaults.buttonColors(
                                                containerColor = github.aeonbtc.ibiswallet.ui.theme.BitcoinOrange,
                                            ),
                                    ) {
                                        androidx.compose.material3.Text(
                                            stringResource(R.string.biometric_enrollment_changed_dismiss),
                                        )
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()

        // Re-enable NFC reader mode if a visible screen still owns a request
        // (Android requires disabling in onPause and re-enabling in onResume).
        if (nfcReaderRequests.hasActiveRequests()) {
            activateNfcReaderMode()
        }
        if (nfcHceRequests.hasActiveRequests()) {
            activatePreferredHceService()
            resumePsbtHceShare()
        }
        NdefHostApduService.resumeNdefPayload()

        val securityMethod = secureStorage.getSecurityMethod()
        if (securityMethod == SecureStorage.SecurityMethod.NONE && !isCloakActive) {
            unlockApp(incrementCounter = false)
            return
        }
        // Cloak mode with no PIN/biometric: nothing to re-lock via timing
        if (securityMethod == SecureStorage.SecurityMethod.NONE) {
            return
        }

        // Check if we need to lock based on timing settings
        if (wasInBackground) {
            wasInBackground = false
            val backgroundedByPicker = backgroundedDuringActivityResultSkip
            backgroundedDuringActivityResultSkip = false
            when (val lockTiming = secureStorage.getLockTiming()) {
                SecureStorage.LockTiming.DISABLED -> {
                    // Never auto-lock after initial unlock
                }
                SecureStorage.LockTiming.WHEN_MINIMIZED,
                SecureStorage.LockTiming.ON_SCREEN_OFF,
                -> {
                    // WHEN_MINIMIZED locks in onStop; ON_SCREEN_OFF locks via the
                    // screen-off receiver. If the background happened while a
                    // picker was open, lock now once the picker grace expired.
                    if (backgroundedByPicker && isUnlocked && !isActivityResultLockSkipActive()) {
                        lockApp()
                    }
                }
                else -> {
                    if (secureStorage.shouldLockAfterBackground(lockTiming.timeoutMs)) {
                        lockApp()
                    }
                }
            }
        }

        // Trigger biometric if locked and biometric is enabled.
        // Skip when cloak is active and not yet bypassed — calculator must be entered first.
        // Skip auto-trigger in duress+biometric mode (the C button is the hidden trigger).
        // Posted to the main queue so it runs after the resume transition completes:
        // showBiometricPrompt() requires RESUMED and would otherwise drop this call.
        if (!isUnlocked &&
            securityMethod == SecureStorage.SecurityMethod.BIOMETRIC &&
            !secureStorage.isDuressEnabled() &&
            !(isCloakActive && !cloakBypassed)
        ) {
            biometricAutoCancelHandler.post { showBiometricPrompt() }
        }
    }

    override fun onPause() {
        super.onPause()
        deactivateNfcReaderMode()
        suspendPsbtHceShare()
        NdefHostApduService.suspendNdefPayload()
        deactivatePreferredHceService()
        // Dismiss any in-flight biometric dialog when leaving foreground (screen
        // off, minimize). Otherwise a stale dialog lingers across the lock and the
        // next authenticate() either fails or immediately cancels itself.
        runCatching { biometricPrompt?.cancelAuthentication() }
        biometricAutoCancelHandler.removeCallbacksAndMessages(null)
        isBiometricAuthInProgress = false
    }

    override fun onStop() {
        super.onStop()

        // Only WHEN_MINIMIZED clears on leave-foreground; lock/close use their own paths.
        if (::secureStorage.isInitialized &&
            secureStorage.getClearClipboardMode().clearOnBackground() &&
            !isActivityResultLockSkipActive()
        ) {
            SecureClipboard.clearNow(this)
        }

        val securityMethod =
            if (::secureStorage.isInitialized) {
                secureStorage.getSecurityMethod()
            } else {
                return
            }
        if (securityMethod == SecureStorage.SecurityMethod.NONE) {
            return
        }
        if (isActivityResultLockSkipActive()) {
            // A system picker is open. Do not lock yet so its result can land,
            // but record the background instant and a flag so onResume locks if
            // the skip window has expired. Never leave wasInBackground false:
            // that was the indefinite-bypass bug.
            wasInBackground = true
            backgroundedDuringActivityResultSkip = true
            secureStorage.setLastBackgroundInstant()
            return
        }
        skipBackgroundLockUntilElapsedMs = 0L
        backgroundedDuringActivityResultSkip = false

        wasInBackground = true
        val lockTiming = secureStorage.getLockTiming()

        when (lockTiming) {
            SecureStorage.LockTiming.DISABLED -> {
                // Never lock when going to background
            }
            SecureStorage.LockTiming.WHEN_MINIMIZED -> {
                // Lock immediately according to the user's security timing.
                lockApp()
            }
            SecureStorage.LockTiming.ON_SCREEN_OFF -> {
                // Minimizing alone does not lock; ACTION_SCREEN_OFF does.
            }
            else -> {
                // Record the time we went to background
                secureStorage.setLastBackgroundInstant()
            }
        }
    }

    override fun onDestroy() {
        if (::secureStorage.isInitialized &&
            secureStorage.getClearClipboardMode().clearOnAppClose()
        ) {
            SecureClipboard.clearNow(this)
        }
        unregisterScreenOffReceiver()
        super.onDestroy()
    }

    private fun registerScreenOffReceiver() {
        if (screenOffReceiverRegistered) return
        val filter = IntentFilter(Intent.ACTION_SCREEN_OFF)
        ContextCompat.registerReceiver(
            this,
            screenOffReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        screenOffReceiverRegistered = true
    }

    private fun unregisterScreenOffReceiver() {
        if (!screenOffReceiverRegistered) return
        runCatching { unregisterReceiver(screenOffReceiver) }
        screenOffReceiverRegistered = false
    }

    private fun unlockApp(incrementCounter: Boolean) {
        if (incrementCounter) {
            appUnlockCounter++
        }
        isUnlocked = true
        walletViewModel.onAppUnlocked()
    }

    private fun lockApp() {
        isUnlocked = false
        secureStorage.lockSpendSecretSession()
        if (secureStorage.getClearClipboardMode().clearOnLock()) {
            SecureClipboard.clearNow(this)
        }
        NdefHostApduService.setNdefPayload(null)
        disarmPsbtHceShare()
        deactivateNfcReaderMode()
        deactivatePreferredHceService()
        walletViewModel.onAppLocked()
        liquidViewModel.unloadLiquidWallet()
        sparkViewModel.unloadSparkWallet()
        arkViewModel.unloadArkWallet()
        lightningNodeViewModel.unloadLightningWallet()
    }

    /**
     * Full wipe used by auto-wipe threshold, lock-screen Wipe PIN, and cloak calculator
     * emergency wipe. Always kills the process when finished.
     */
    private fun performFullWipe() {
        lifecycleScope.launch {
            liquidViewModel.prepareForFullWipe()
            sparkViewModel.prepareForFullWipe()
            arkViewModel.prepareForFullWipe()
            lightningNodeViewModel.prepareForFullWipe()
            walletViewModel.wipeAllData { result ->
                // Always kill the process even on partial wipe — leaving a half-functional
                // wallet reachable on the unlock/calculator screen is worse than terminating
                // with logged residue. The repository already logs failed steps via SecureLog.
                if (!result.success && BuildConfig.DEBUG) {
                    Log.w(TAG, "Wipe completed with residue: ${result.failedSteps}")
                }
                android.os.Process.killProcess(android.os.Process.myPid())
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleSendIntent(intent)
    }

    /**
     * Extract a supported send payload from the intent and store it in the ViewModel.
     * Handles ACTION_VIEW intents (browser/app links) for Bitcoin, Lightning, and Liquid.
     * NFC tag reading is handled separately by [nfcReaderCallback].
     */
    private fun handleSendIntent(intent: Intent?) {
        if (intent == null) return
        // Only browser-style VIEW intents are accepted. The manifest filter is
        // VIEW-only, but an explicit intent from another app can carry any
        // action with a data URI.
        if (intent.action != Intent.ACTION_VIEW) return

        intent.data?.let { data ->
            when (data.scheme?.lowercase()) {
                "bitcoin",
                "lightning",
                "liquidnetwork",
                "liquid",
                -> walletViewModel.setPendingSendInput(
                    data.toString(),
                    WalletViewModel.PendingSendOrigin.DEEP_LINK,
                )
            }
        }
    }

    /**
     * Parse an NDEF record into a string, handling both URI and Text record types.
     */
    private fun parseNdefRecord(record: NdefRecord): String? {
        return when (record.tnf) {
            NdefRecord.TNF_WELL_KNOWN -> {
                when {
                    record.type.contentEquals(NdefRecord.RTD_URI) -> {
                        // URI record: first byte is prefix code, rest is URI
                        record.toUri()?.toString()
                    }
                    record.type.contentEquals(NdefRecord.RTD_TEXT) -> {
                        // Text record: first byte = status (bit 7 = encoding, bits 5-0 = lang length)
                        val payload = record.payload
                        if (payload.isEmpty()) return null
                        val status = payload[0].toInt() and 0xFF
                        val langLength = status and 0x3F
                        if (payload.size <= 1 + langLength) return null
                        val encoding = if (status and 0x80 == 0) Charsets.UTF_8 else Charsets.UTF_16
                        String(payload, 1 + langLength, payload.size - 1 - langLength, encoding)
                    }
                    else -> null
                }
            }
            NdefRecord.TNF_ABSOLUTE_URI -> {
                String(record.payload, Charsets.UTF_8)
            }
            else -> null
        }
    }

    private fun setupBiometricPrompt() {
        val executor = ContextCompat.getMainExecutor(this)

        biometricPrompt =
            BiometricPrompt(
                this, executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        super.onAuthenticationSucceeded(result)
                        biometricAutoCancelHandler.removeCallbacksAndMessages(null)
                        isBiometricAuthInProgress = false
                        if (result.cryptoObject == null) {
                            Toast.makeText(
                                this@MainActivity,
                                getString(R.string.loc_0039435a),
                                Toast.LENGTH_SHORT,
                            ).show()
                            return
                        }
                        runCatching {
                            result.cryptoObject?.cipher?.let(secureStorage::unlockSpendSecretsWithBiometric)
                        }.onFailure {
                            val messageRes =
                                if (it is SecureStorage.BiometricEnrollmentChangedException) {
                                    R.string.biometric_enrollment_changed_use_pin
                                } else {
                                    R.string.loc_0039435a
                                }
                            Toast.makeText(
                                this@MainActivity,
                                getString(messageRes),
                                Toast.LENGTH_LONG,
                            ).show()
                            return
                        }
                        // Biometric always opens the real wallet — await switch first.
                        lifecycleScope.launch {
                            walletViewModel.exitDuressMode()
                            unlockApp(incrementCounter = true)
                            if (secureStorage.consumeBiometricEnrollmentWarning()) {
                                showBiometricEnrollmentWarning = true
                            }
                        }
                    }

                    override fun onAuthenticationError(
                        errorCode: Int,
                        errString: CharSequence,
                    ) {
                        super.onAuthenticationError(errorCode, errString)
                        biometricAutoCancelHandler.removeCallbacksAndMessages(null)
                        isBiometricAuthInProgress = false
                        // User can still use PIN as fallback
                    }

                    // onAuthenticationFailed not overridden — biometric failures
                    // do not count toward auto-wipe; the OS already rate-limits
                    // biometric attempts and falls back to PIN entry.
                },
            )
    }

    private fun showBiometricPrompt(autoCancelAfter2s: Boolean = false) {
        // Single-flight: LockScreen auto-trigger and onResume fire together on
        // foreground return — a second authenticate() cancels the first dialog and
        // the in-flight touch is dropped, leaving a prompt that ignores fingerprints.
        if (isBiometricAuthInProgress) return
        // Never start auth while not resumed. The LockScreen recompose triggered by
        // lockApp() in onStop fires while STOPPED (screen off / minimized) — calling
        // authenticate() there shows no dialog and invokes no callback, wedging the
        // gate so the onResume prompt and the fingerprint button both go dead.
        // Dropping the stopped trigger leaves onResume (cold start + foreground
        // return) and the manual button (foreground lock) as the single prompt path.
        if (!lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) return
        isBiometricAuthInProgress = true
        val promptTitle =
            if (isCloakActive) {
                getString(R.string.biometric_prompt_authenticate)
            } else {
                getString(R.string.biometric_prompt_unlock_ibis_wallet)
            }
        val promptSubtitle =
            if (isCloakActive) {
                getString(R.string.biometric_prompt_verify_identity)
            } else {
                getString(R.string.biometric_prompt_access_wallet)
            }
        val promptInfo =
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(promptTitle)
                .setSubtitle(promptSubtitle)
                .setNegativeButtonText(getString(R.string.loc_10459bad))
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                .build()

        // Tie authentication to a KeyStore cryptographic operation so it cannot
        // be bypassed by injecting a success callback on rooted/instrumented devices.
        lifecycleScope.launch {
            val cryptoResult =
                withContext(Dispatchers.Default) {
                    runCatching {
                        secureStorage.createSpendSecretBiometricCryptoObject()
                    }
                }
            val cryptoObject = cryptoResult.getOrNull()

            // Re-check after the background crypto work: the activity may have been
            // backgrounded (screen off) while the cipher was being built. Calling
            // authenticate() while stopped wedges BiometricPrompt with no callback.
            if (!lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) {
                isBiometricAuthInProgress = false
                return@launch
            }
            if (cryptoObject != null) {
                try {
                    biometricPrompt?.authenticate(promptInfo, cryptoObject)
                        ?: run { isBiometricAuthInProgress = false }
                } catch (_: Exception) {
                    // authenticate() throws when the activity is not resumed yet
                    // (foreground return racing onResume). Clear the gate so the
                    // fingerprint button retry works instead of staying blocked.
                    isBiometricAuthInProgress = false
                }
            } else {
                isBiometricAuthInProgress = false
                val messageRes =
                    when (cryptoResult.exceptionOrNull()) {
                        is BiometricKeyInvalidatedException,
                        is BiometricKeyUnavailableException,
                        -> R.string.biometric_enrollment_changed_use_pin
                        else -> R.string.loc_0039435a
                    }
                Toast.makeText(
                    this@MainActivity,
                    getString(messageRes),
                    Toast.LENGTH_SHORT,
                ).show()
                return@launch
            }

            // In duress+biometric mode, auto-cancel the biometric prompt after 2 seconds
            // so an attacker who accidentally hits C sees it disappear quickly
            if (autoCancelAfter2s) {
                biometricAutoCancelHandler.removeCallbacksAndMessages(null)
                biometricAutoCancelHandler.postDelayed({
                    biometricPrompt?.cancelAuthentication()
                }, 2000L)
            }
        }
    }

}
