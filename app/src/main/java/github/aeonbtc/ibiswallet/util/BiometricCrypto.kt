package github.aeonbtc.ibiswallet.util

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
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
     *
     * The key is deliberately created with setInvalidatedByBiometricEnrollment(false):
     * with `true`, adding or removing a fingerprint in system settings permanently
     * destroys the key — and since the spend-secret master key is wrapped only under
     * this key in biometric mode, that silently makes every wallet secret
     * (mnemonics, keys, LN credentials) unrecoverable. Per-use biometric auth is
     * still enforced via setUserAuthenticationRequired(true) with no validity window.
     */
    fun getBiometricCipher(
        mode: Int = Cipher.ENCRYPT_MODE,
        iv: ByteArray? = null,
    ): Cipher {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val keyAlias = SecureStorage.BIOMETRIC_KEY_ALIAS

        if (!keyStore.containsAlias(keyAlias)) {
            createBiometricKey(keyAlias)
        }

        val cipher =
            Cipher.getInstance(
                "${KeyProperties.KEY_ALGORITHM_AES}/${KeyProperties.BLOCK_MODE_CBC}/${KeyProperties.ENCRYPTION_PADDING_PKCS7}",
            )
        return try {
            cipher.initKey(keyStore, keyAlias, mode, iv)
            cipher
        } catch (e: KeyPermanentlyInvalidatedException) {
            // Legacy installs created the key with setInvalidatedByBiometricEnrollment(true),
            // so a fingerprint enrollment change may have killed it while the alias still
            // exists. Delete the stale key and recreate it so biometric unlock can be
            // re-enrolled instead of permanently locking the user out.
            keyStore.deleteEntry(keyAlias)
            createBiometricKey(keyAlias)
            cipher.initKey(keyStore, keyAlias, mode, iv)
            cipher
        }
    }

    private fun createBiometricKey(keyAlias: String) {
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
                .setInvalidatedByBiometricEnrollment(false)
                .build(),
        )
        keyGen.generateKey()
    }

    private fun Cipher.initKey(
        keyStore: KeyStore,
        keyAlias: String,
        mode: Int,
        iv: ByteArray?,
    ) {
        val secretKey = keyStore.getKey(keyAlias, null) as SecretKey
        if (mode == Cipher.DECRYPT_MODE) {
            require(iv != null) { "IV is required for biometric decryption" }
            init(mode, secretKey, IvParameterSpec(iv))
        } else {
            init(mode, secretKey)
        }
    }
}
