package github.aeonbtc.ibiswallet

import android.app.ActivityManager
import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import github.aeonbtc.ibiswallet.data.local.SecureStorage
import github.aeonbtc.ibiswallet.ui.IbisWalletApp
import github.aeonbtc.ibiswallet.ui.screens.CalculatorScreen
import github.aeonbtc.ibiswallet.ui.screens.LockScreen
import github.aeonbtc.ibiswallet.ui.theme.DarkBackground
import github.aeonbtc.ibiswallet.ui.theme.IbisWalletTheme
import github.aeonbtc.ibiswallet.viewmodel.WalletViewModel

class MainActivity : FragmentActivity() {
    
    private lateinit var secureStorage: SecureStorage
    private lateinit var walletViewModel: WalletViewModel
    private var isUnlocked by mutableStateOf(false)
    private var cloakBypassed by mutableStateOf(false)
    private var biometricPrompt: BiometricPrompt? = null
    private var wasInBackground = false
    private val biometricAutoCancelHandler = Handler(Looper.getMainLooper())
    private var isCloakActive = false
    
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
            val state = if (alias == pending)
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            else
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            
            pm.setComponentEnabledSetting(
                ComponentName(pkg, "$pkg$alias"),
                state,
                PackageManager.DONT_KILL_APP
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
                    getString(R.string.cloak_calculator_label)
                )
            )
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Prevent tapjacking/overlay attacks
        window.decorView.filterTouchesWhenObscured = true
        
        secureStorage = SecureStorage(this)
        walletViewModel = ViewModelProvider(this)[WalletViewModel::class.java]
        
        // Apply pending icon swap before any UI
        applyPendingIconSwap()
        
        // Check cloak mode
        isCloakActive = secureStorage.isCloakModeEnabled() && secureStorage.getCloakCode() != null
        updateTaskDescription()
        
        // Apply screenshot prevention if enabled
        if (secureStorage.getDisableScreenshots()) {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
            )
        }
        
        // Check if security is enabled - always locked on fresh start
        // When cloak mode is active, stay locked so the calculator screen shows first
        val securityMethod = secureStorage.getSecurityMethod()
        isUnlocked = securityMethod == SecureStorage.SecurityMethod.NONE && !isCloakActive
        
        // Setup biometric prompt
        setupBiometricPrompt()
        
        setContent {
            IbisWalletTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = DarkBackground
                ) {
                    if (isUnlocked) {
                        IbisWalletApp(
                            onLockApp = { isUnlocked = false }
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
                            }
                        )
                    } else {
                        val biometricManager = BiometricManager.from(this)
                        val isBiometricAvailable = biometricManager.canAuthenticate(
                            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                            BiometricManager.Authenticators.BIOMETRIC_WEAK
                        ) == BiometricManager.BIOMETRIC_SUCCESS
                        
                        val secMethod = secureStorage.getSecurityMethod()
                        val isDuressEnabled = secureStorage.isDuressEnabled()
                        val isDuressWithBiometric = isDuressEnabled &&
                            secMethod == SecureStorage.SecurityMethod.BIOMETRIC
                        
                        LockScreen(
                            securityMethod = secMethod,
                            onPinEntered = { pin ->
                                // For PIN mode: check real PIN first, then duress PIN
                                // For BIOMETRIC mode with duress: only duress PIN works
                                //   (real wallet accessed via biometric through the C button)
                                val isRealPin = secMethod == SecureStorage.SecurityMethod.PIN &&
                                    secureStorage.verifyPin(pin)
                                // When verifyPin already ran and failed, it incremented
                                // the shared counter — don't double-count in verifyDuressPin
                                val realPinWasTried = secMethod == SecureStorage.SecurityMethod.PIN
                                val isDuressPin = !isRealPin && isDuressEnabled &&
                                    secureStorage.verifyDuressPin(pin, incrementFailedAttempts = !realPinWasTried)
                                
                                when {
                                    isRealPin -> {
                                        walletViewModel.exitDuressMode()
                                        isUnlocked = true
                                        true
                                    }
                                    isDuressPin -> {
                                        walletViewModel.enterDuressMode()
                                        isUnlocked = true
                                        true
                                    }
                                    else -> {
                                        // Check if failed attempts reached auto-wipe threshold
                                        if (secureStorage.shouldAutoWipe()) {
                                            walletViewModel.wipeAllData {
                                                // Kill the process to simulate a crash — no restart,
                                                // no fresh state visible. The app simply vanishes.
                                                android.os.Process.killProcess(android.os.Process.myPid())
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
                            storedPinLength = if (isDuressWithBiometric) {
                                secureStorage.getDuressPinLength()
                            } else {
                                secureStorage.getStoredPinLength()
                            },
                            isDuressWithBiometric = isDuressWithBiometric
                        )
                    }
                }
            }
        }
    }
    
    override fun onResume() {
        super.onResume()
        
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
            val lockTiming = secureStorage.getLockTiming()
            
            when (lockTiming) {
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
        
        // Trigger biometric if locked and biometric is enabled
        // Skip auto-trigger in duress+biometric mode (the C button is the hidden trigger)
        if (!isUnlocked && securityMethod == SecureStorage.SecurityMethod.BIOMETRIC && !secureStorage.isDuressEnabled()) {
            showBiometricPrompt()
        }
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
    
    private fun setupBiometricPrompt() {
        val executor = ContextCompat.getMainExecutor(this)
        
        biometricPrompt = BiometricPrompt(this, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    biometricAutoCancelHandler.removeCallbacksAndMessages(null)
                    // Biometric always opens the real wallet
                    walletViewModel.exitDuressMode()
                    isUnlocked = true
                }
                
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    biometricAutoCancelHandler.removeCallbacksAndMessages(null)
                    // User can still use PIN as fallback
                }
                
                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    // Increment shared failed attempt counter (same as PIN failures)
                    secureStorage.incrementFailedAttempts()
                    if (secureStorage.shouldAutoWipe()) {
                        walletViewModel.wipeAllData {
                            // Kill the process to simulate a crash — no restart,
                            // no fresh state visible. The app simply vanishes.
                            android.os.Process.killProcess(android.os.Process.myPid())
                        }
                    }
                }
            })
    }
    
    private fun showBiometricPrompt(autoCancelAfter2s: Boolean = false) {
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(if (isCloakActive) "Authenticate" else "Unlock Ibis Wallet")
            .setSubtitle(if (isCloakActive) "Verify your identity" else "Authenticate to access your wallet")
            .setNegativeButtonText("Use PIN")
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.BIOMETRIC_WEAK
            )
            .build()
        
        biometricPrompt?.authenticate(promptInfo)
        
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
