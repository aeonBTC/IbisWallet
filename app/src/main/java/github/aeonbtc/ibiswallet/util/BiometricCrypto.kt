package github.aeonbtc.ibiswallet.util

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.biometric.BiometricPrompt
import github.aeonbtc.ibiswallet.data.local.SecureStorage
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec

object BiometricCrypto {
    fun createCryptoObject(): BiometricPrompt.CryptoObject =
        BiometricPrompt.CryptoObject(getBiometricCipher())

    fun createCryptoObjectForDecryption(iv: ByteArray): BiometricPrompt.CryptoObject =
        BiometricPrompt.CryptoObject(getBiometricCipher(Cipher.DECRYPT_MODE, iv))

    /**
     * Get or create a KeyStore-backed AES key that requires biometric auth,
     * and return an initialized Cipher for CryptoObject binding.
     */
    fun getBiometricCipher(
        mode: Int = Cipher.ENCRYPT_MODE,
        iv: ByteArray? = null,
    ): Cipher {
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
            if (mode == Cipher.DECRYPT_MODE) {
                require(iv != null) { "IV is required for biometric decryption" }
                init(mode, secretKey, IvParameterSpec(iv))
            } else {
                init(mode, secretKey)
            }
        }
    }
}
