package github.aeonbtc.ibiswallet

import android.os.Bundle
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
import github.aeonbtc.ibiswallet.data.local.SecureStorage
import github.aeonbtc.ibiswallet.ui.IbisWalletApp
import github.aeonbtc.ibiswallet.ui.screens.LockScreen
import github.aeonbtc.ibiswallet.ui.theme.DarkBackground
import github.aeonbtc.ibiswallet.ui.theme.IbisWalletTheme

class MainActivity : FragmentActivity() {
    
    private lateinit var secureStorage: SecureStorage
    private var isUnlocked by mutableStateOf(false)
    private var biometricPrompt: BiometricPrompt? = null
    private var wasInBackground = false
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Prevent tapjacking/overlay attacks
        window.decorView.filterTouchesWhenObscured = true
        
        secureStorage = SecureStorage(this)
        
        // Apply screenshot prevention if enabled
        if (secureStorage.getDisableScreenshots()) {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
            )
        }
        
        // Check if security is enabled - always locked on fresh start
        val securityMethod = secureStorage.getSecurityMethod()
        isUnlocked = securityMethod == SecureStorage.SecurityMethod.NONE
        
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
                    } else {
                        val biometricManager = BiometricManager.from(this)
                        val isBiometricAvailable = biometricManager.canAuthenticate(
                            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                            BiometricManager.Authenticators.BIOMETRIC_WEAK
                        ) == BiometricManager.BIOMETRIC_SUCCESS
                        
                        LockScreen(
                            securityMethod = secureStorage.getSecurityMethod(),
                            onPinEntered = { pin ->
                                val success = secureStorage.verifyPin(pin)
                                if (success) {
                                    isUnlocked = true
                                }
                                success
                            },
                            onBiometricRequest = {
                                showBiometricPrompt()
                            },
                            isBiometricAvailable = isBiometricAvailable,
                            storedPinLength = secureStorage.getStoredPinLength()
                        )
                    }
                }
            }
        }
    }
    
    override fun onResume() {
        super.onResume()
        
        val securityMethod = secureStorage.getSecurityMethod()
        if (securityMethod == SecureStorage.SecurityMethod.NONE) {
            isUnlocked = true
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
                    }
                }
            }
        }
        
        // Trigger biometric if locked and biometric is enabled
        if (!isUnlocked && securityMethod == SecureStorage.SecurityMethod.BIOMETRIC) {
            showBiometricPrompt()
        }
    }
    
    override fun onStop() {
        super.onStop()
        
        val securityMethod = secureStorage.getSecurityMethod()
        if (securityMethod == SecureStorage.SecurityMethod.NONE) {
            return
        }
        
        wasInBackground = true
        val lockTiming = secureStorage.getLockTiming()
        
        when (lockTiming) {
            SecureStorage.LockTiming.DISABLED -> {
                // Never lock when going to background
            }
            SecureStorage.LockTiming.WHEN_MINIMIZED -> {
                // Lock immediately
                isUnlocked = false
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
                    isUnlocked = true
                }
                
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    // User can still use PIN as fallback
                }
                
                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    // Authentication failed, user can retry or use PIN
                }
            })
    }
    
    private fun showBiometricPrompt() {
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock Ibis Wallet")
            .setSubtitle("Authenticate to access your wallet")
            .setNegativeButtonText("Use PIN")
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.BIOMETRIC_WEAK
            )
            .build()
        
        biometricPrompt?.authenticate(promptInfo)
    }
}
