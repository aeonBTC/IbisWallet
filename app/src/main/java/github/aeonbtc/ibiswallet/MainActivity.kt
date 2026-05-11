package github.aeonbtc.ibiswallet

import android.app.ActivityManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.cardemulation.CardEmulation
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.nfc.tech.IsoDep
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.ViewModelProvider
import github.aeonbtc.ibiswallet.data.local.SecureStorage
import github.aeonbtc.ibiswallet.localization.AppLocale
import github.aeonbtc.ibiswallet.localization.LocalAppLocale
import github.aeonbtc.ibiswallet.nfc.NdefHostApduService
import github.aeonbtc.ibiswallet.nfc.NfcReaderModeRequestRegistry
import github.aeonbtc.ibiswallet.nfc.NfcRuntimeStatus
import github.aeonbtc.ibiswallet.ui.IbisWalletApp
import github.aeonbtc.ibiswallet.ui.screens.CalculatorScreen
import github.aeonbtc.ibiswallet.ui.screens.LockScreen
import github.aeonbtc.ibiswallet.ui.theme.DarkBackground
import github.aeonbtc.ibiswallet.ui.theme.IbisWalletTheme
import github.aeonbtc.ibiswallet.util.getNfcAvailability
import github.aeonbtc.ibiswallet.util.isRecognizedSendInput
import github.aeonbtc.ibiswallet.viewmodel.LiquidViewModel
import github.aeonbtc.ibiswallet.viewmodel.SparkViewModel
import github.aeonbtc.ibiswallet.viewmodel.WalletViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

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
    }

    private lateinit var secureStorage: SecureStorage
    private lateinit var walletViewModel: WalletViewModel
    private lateinit var liquidViewModel: LiquidViewModel
    private lateinit var sparkViewModel: SparkViewModel
    private var isUnlocked by mutableStateOf(false)
    private var appUnlockCounter by mutableIntStateOf(0)
    private var cloakBypassed by mutableStateOf(false)
    private var biometricPrompt: BiometricPrompt? = null
    private var wasInBackground = false
    private val biometricAutoCancelHandler = Handler(Looper.getMainLooper())
    private var isCloakActive = false

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
     * NFC ReaderCallback — invoked on a background thread when a tag is detected.
     * Reads NDEF message from the tag (or HCE peer), extracts a supported send payload,
     * and posts it to the ViewModel on the main thread.
     */
    private val nfcReaderCallback = NfcAdapter.ReaderCallback { tag: Tag ->
        NfcRuntimeStatus.markReaderTagDetected()
        val sendInput = readSendInputFromTag(tag) ?: return@ReaderCallback
        NfcRuntimeStatus.markReaderPayloadReceived()
        runOnUiThread {
            walletViewModel.setPendingSendInput(sendInput)
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
            if (messageLength <= 0) return null

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
            setTaskDescription(
                ActivityManager.TaskDescription(
                    getString(R.string.cloak_calculator_label),
                ),
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

            // Prevent tapjacking/overlay attacks
            window.decorView.filterTouchesWhenObscured = true

            secureStorage = SecureStorage.getInstance(this)
            walletViewModel = ViewModelProvider(this)[WalletViewModel::class.java]
            liquidViewModel = ViewModelProvider(this)[LiquidViewModel::class.java]
            sparkViewModel = ViewModelProvider(this)[SparkViewModel::class.java]
            nfcAdapter = NfcAdapter.getDefaultAdapter(this)

            // Apply pending icon swap before any UI
            applyPendingIconSwap()

            // Check cloak mode
            isCloakActive = secureStorage.isCloakModeEnabled() && secureStorage.getCloakCode() != null
            updateTaskDescription()

            // Apply screenshot prevention if enabled
            if (secureStorage.getDisableScreenshots()) {
                window.setFlags(
                    WindowManager.LayoutParams.FLAG_SECURE,
                    WindowManager.LayoutParams.FLAG_SECURE,
                )
            }

            // Check if security is enabled - always locked on fresh start
            // When cloak mode is active, stay locked so the calculator screen shows first
            val securityMethod = secureStorage.getSecurityMethod()
            isUnlocked = securityMethod == SecureStorage.SecurityMethod.NONE && !isCloakActive

            // Setup biometric prompt
            setupBiometricPrompt()
            if (securityMethod == SecureStorage.SecurityMethod.BIOMETRIC) {
                prewarmBiometricCipher()
            }

            // Check for incoming send intent
            handleSendIntent(intent)

        setContent {
            val appLocale by walletViewModel.appLocale.collectAsStateWithLifecycle()
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
                IbisWalletTheme {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = DarkBackground,
                    ) {
                        if (isUnlocked) {
                            IbisWalletApp(
                                onLockApp = { isUnlocked = false },
                                appUnlockCounter = appUnlockCounter,
                            )
                        } else if (isCloakActive && !cloakBypassed) {
                            // Show calculator disguise — entering the secret code bypasses it
                            CalculatorScreen(
                                cloakCode = secureStorage.getCloakCode() ?: "",
                                onUnlock = {
                                    cloakBypassed = true
                                    val secMethod = secureStorage.getSecurityMethod()
                                    if (secMethod == SecureStorage.SecurityMethod.NONE) {
                                        // No additional auth — go straight to wallet
                                        isUnlocked = true
                                    }
                                    // Otherwise fall through to LockScreen on next recompose
                                },
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
                                // For PIN mode: check real PIN first, then duress PIN
                                // For BIOMETRIC mode with duress: only duress PIN works
                                //   (real wallet accessed via biometric through the C button)
                                val isRealPin =
                                    withContext(Dispatchers.Default) {
                                        secMethod == SecureStorage.SecurityMethod.PIN &&
                                            secureStorage.verifyPin(pin)
                                    }
                                // When verifyPin already ran and failed, it incremented
                                // the shared counter — don't double-count in verifyDuressPin
                                val realPinWasTried = secMethod == SecureStorage.SecurityMethod.PIN
                                val isDuressPin =
                                    !isRealPin && isDuressEnabled &&
                                        withContext(Dispatchers.Default) {
                                            secureStorage.verifyDuressPin(
                                                pin,
                                                incrementFailedAttempts = !realPinWasTried,
                                            )
                                        }

                                when {
                                    isRealPin -> {
                                        walletViewModel.exitDuressMode()
                                        appUnlockCounter++
                                        isUnlocked = true
                                        true
                                    }
                                    isDuressPin -> {
                                        walletViewModel.enterDuressMode()
                                        appUnlockCounter++
                                        isUnlocked = true
                                        true
                                    }
                                    else -> {
                                        // Check if failed attempts reached auto-wipe threshold
                                        if (secureStorage.shouldAutoWipe()) {
                                            lifecycleScope.launch {
                                                liquidViewModel.prepareForFullWipe()
                                                sparkViewModel.prepareForFullWipe()
                                                walletViewModel.wipeAllData { result ->
                                                    // Always kill the process even on partial wipe — leaving
                                                    // a half-functional wallet reachable on the unlock screen
                                                    // is worse than terminating with logged residue. The
                                                    // repository already logs failed steps via SecureLog.
                                                    if (!result.success && BuildConfig.DEBUG) {
                                                        android.util.Log.w(
                                                            "MainActivity",
                                                            "Auto-wipe completed with residue: ${result.failedSteps}",
                                                        )
                                                    }
                                                    android.os.Process.killProcess(android.os.Process.myPid())
                                                }
                                            }
                                        }
                                        false
                                    }
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
        }

        val securityMethod = secureStorage.getSecurityMethod()
        if (securityMethod == SecureStorage.SecurityMethod.NONE && !isCloakActive) {
            isUnlocked = true
            return
        }
        // Cloak mode with no PIN/biometric: nothing to re-lock via timing
        if (securityMethod == SecureStorage.SecurityMethod.NONE) {
            return
        }

        // Check if we need to lock based on timing settings
        if (wasInBackground) {
            wasInBackground = false
            when (val lockTiming = secureStorage.getLockTiming()) {
                SecureStorage.LockTiming.DISABLED -> {
                    // Never auto-lock after initial unlock
                }
                SecureStorage.LockTiming.WHEN_MINIMIZED -> {
                    // Already locked in onStop
                }
                else -> {
                    // Check if timeout has elapsed
                    val lastBackgroundTime = secureStorage.getLastBackgroundTime()
                    val elapsedTime = System.currentTimeMillis() - lastBackgroundTime
                    if (elapsedTime >= lockTiming.timeoutMs) {
                        isUnlocked = false
                        if (isCloakActive) cloakBypassed = false
                    }
                }
            }
        }

        // Trigger biometric if locked and biometric is enabled.
        // Skip when cloak is active and not yet bypassed — calculator must be entered first.
        // Skip auto-trigger in duress+biometric mode (the C button is the hidden trigger).
        if (!isUnlocked &&
            securityMethod == SecureStorage.SecurityMethod.BIOMETRIC &&
            !secureStorage.isDuressEnabled() &&
            !(isCloakActive && !cloakBypassed)
        ) {
            prewarmBiometricCipher()
            showBiometricPrompt()
        }
    }

    override fun onPause() {
        super.onPause()
        deactivateNfcReaderMode()
        deactivatePreferredHceService()
    }

    override fun onStop() {
        super.onStop()

        val securityMethod = secureStorage.getSecurityMethod()
        if (securityMethod == SecureStorage.SecurityMethod.NONE && !isCloakActive) {
            return
        }
        // Cloak mode with no PIN: re-engage calculator immediately on background
        if (securityMethod == SecureStorage.SecurityMethod.NONE && isCloakActive) {
            isUnlocked = false
            cloakBypassed = false
            return
        }

        wasInBackground = true
        val lockTiming = secureStorage.getLockTiming()

        when (lockTiming) {
            SecureStorage.LockTiming.DISABLED -> {
                // Never lock when going to background
            }
            SecureStorage.LockTiming.WHEN_MINIMIZED -> {
                // Lock immediately and re-engage cloak
                isUnlocked = false
                if (isCloakActive) cloakBypassed = false
            }
            else -> {
                // Record the time we went to background
                secureStorage.setLastBackgroundTime(System.currentTimeMillis())
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

        intent.data?.let { data ->
            when (data.scheme?.lowercase()) {
                "bitcoin",
                "lightning",
                "liquidnetwork",
                "liquid",
                -> walletViewModel.setPendingSendInput(data.toString())
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
                        if (result.cryptoObject == null) {
                            Toast.makeText(
                                this@MainActivity,
                                getString(R.string.loc_0039435a),
                                Toast.LENGTH_SHORT,
                            ).show()
                            return
                        }
                        // Biometric always opens the real wallet
                        walletViewModel.exitDuressMode()
                        appUnlockCounter++
                        isUnlocked = true
                    }

                    override fun onAuthenticationError(
                        errorCode: Int,
                        errString: CharSequence,
                    ) {
                        super.onAuthenticationError(errorCode, errString)
                        biometricAutoCancelHandler.removeCallbacksAndMessages(null)
                        // User can still use PIN as fallback
                    }

                    // onAuthenticationFailed not overridden — biometric failures
                    // do not count toward auto-wipe; the OS already rate-limits
                    // biometric attempts and falls back to PIN entry.
                },
            )
    }

    private fun showBiometricPrompt(autoCancelAfter2s: Boolean = false) {
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
            val cryptoObject =
                withContext(Dispatchers.Default) {
                    runCatching {
                        BiometricPrompt.CryptoObject(getBiometricCipher())
                    }.getOrNull()
                }

            if (cryptoObject != null) {
                biometricPrompt?.authenticate(promptInfo, cryptoObject)
            } else {
                Toast.makeText(
                    this@MainActivity,
                    getString(R.string.loc_0039435a),
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

    private fun prewarmBiometricCipher() {
        lifecycleScope.launch(Dispatchers.Default) {
            runCatching { getBiometricCipher() }
        }
    }

    /**
     * Get or create a KeyStore-backed AES key that requires biometric auth,
     * and return an initialized Cipher for CryptoObject binding.
     */
    private fun getBiometricCipher(): Cipher {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val keyAlias = SecureStorage.BIOMETRIC_KEY_ALIAS

        if (!keyStore.containsAlias(keyAlias)) {
            val keyGen =
                KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES,
                    "AndroidKeyStore",
                )
            keyGen.init(
                KeyGenParameterSpec.Builder(
                    keyAlias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_CBC)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7)
                    .setUserAuthenticationRequired(true)
                    .setInvalidatedByBiometricEnrollment(true)
                    .build(),
            )
            keyGen.generateKey()
        }

        val secretKey = keyStore.getKey(keyAlias, null) as SecretKey
        return Cipher.getInstance(
            "${KeyProperties.KEY_ALGORITHM_AES}/${KeyProperties.BLOCK_MODE_CBC}/${KeyProperties.ENCRYPTION_PADDING_PKCS7}",
        ).apply {
            init(Cipher.ENCRYPT_MODE, secretKey)
        }
    }
}
