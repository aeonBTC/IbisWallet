package github.aeonbtc.ibiswallet.util

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import androidx.biometric.BiometricPrompt
import github.aeonbtc.ibiswallet.data.local.SecureStorage
import java.security.KeyStore
import java.security.UnrecoverableKeyException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec

/** The biometric key was permanently invalidated (e.g. a legacy enrollment-change key). */
class BiometricKeyInvalidatedException(cause: Throwable?) :
    IllegalStateException(
        "Biometric key was invalidated; unlock with PIN and re-enable biometric",
        cause,
    )

/** The biometric key is absent or unreadable. */
class BiometricKeyUnavailableException(cause: Throwable?) :
    IllegalStateException(
        "Biometric key is unavailable; unlock with PIN and re-enable biometric",
        cause,
    )

object BiometricCrypto {
    /**
     * Enrollment cipher: may create or (for a legacy invalidated key) replace the
     * key. This is only used from an already-unlocked session (PIN verified), so
     * replacing the key cannot orphan a wrap that the user cannot otherwise
     * recover. Never call this from the unlock/prewarm path.
     */
    fun createCryptoObjectForEnrollment(): BiometricPrompt.CryptoObject =
        BiometricPrompt.CryptoObject(getOrCreateBiometricCipherForEnrollment())

    /**
     * Unlock cipher: uses ONLY an existing key. It never creates or deletes a
     * key, so a missing/invalidated key can never silently destroy the only
     * copy of the biometric wrap.
     */
    fun createCryptoObjectForDecryption(iv: ByteArray): BiometricPrompt.CryptoObject =
        BiometricPrompt.CryptoObject(getExistingBiometricCipherForDecryption(iv))

    private fun getOrCreateBiometricCipherForEnrollment(): Cipher {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val keyAlias = SecureStorage.BIOMETRIC_KEY_ALIAS

        if (!keyStore.containsAlias(keyAlias)) {
            createBiometricKey(keyAlias)
        }

        val cipher = newAesCbcCipher()
        return try {
            cipher.init(Cipher.ENCRYPT_MODE, keyStore.secretKey(keyAlias))
            cipher
        } catch (e: KeyPermanentlyInvalidatedException) {
            // Enrollment is reached only after a successful PIN unlock. The old
            // wrap is already undecryptable, but the live spend master is in the
            // session and will be re-wrapped by enrollBiometricLock.
            keyStore.deleteEntry(keyAlias)
            createBiometricKey(keyAlias)
            cipher.init(Cipher.ENCRYPT_MODE, keyStore.secretKey(keyAlias))
            cipher
        } catch (e: UnrecoverableKeyException) {
            throw BiometricKeyUnavailableException(e)
        }
    }

    private fun getExistingBiometricCipherForDecryption(iv: ByteArray): Cipher {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val keyAlias = SecureStorage.BIOMETRIC_KEY_ALIAS
        if (!keyStore.containsAlias(keyAlias)) {
            throw BiometricKeyUnavailableException(null)
        }

        val cipher = newAesCbcCipher()
        return try {
            cipher.init(
                Cipher.DECRYPT_MODE,
                keyStore.secretKey(keyAlias),
                IvParameterSpec(iv),
            )
            cipher
        } catch (e: KeyPermanentlyInvalidatedException) {
            // Do not delete the key or the wrap. The user can still use PIN and
            // re-enroll; a biometric-only install cannot be recovered without a
            // seed/backup, but deleting the wrap would remove even that evidence.
            throw BiometricKeyInvalidatedException(e)
        } catch (e: UnrecoverableKeyException) {
            throw BiometricKeyUnavailableException(e)
        }
    }

    private fun newAesCbcCipher(): Cipher =
        Cipher.getInstance(
            "${KeyProperties.KEY_ALGORITHM_AES}/${KeyProperties.BLOCK_MODE_CBC}/${KeyProperties.ENCRYPTION_PADDING_PKCS7}",
        )

    private fun KeyStore.secretKey(keyAlias: String): SecretKey =
        getKey(keyAlias, null) as SecretKey

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

    // ==================== Enrollment-change tripwire ====================

    /** Tripwire probe result. UNKNOWN means the Keystore could not be read. */
    enum class TripwireStatus {
        OK,
        CHANGED,
        UNKNOWN,
    }

    /**
     * Canary key with setInvalidatedByBiometricEnrollment(true). It wraps NOTHING,
     * so its invalidation can never destroy wallet data — it only signals that the
     * device's fingerprint enrollment changed since [armTripwire] last ran.
     */
    fun isEnrollmentChanged(): Boolean = tripwireStatus() == TripwireStatus.CHANGED

    fun tripwireStatus(): TripwireStatus =
        try {
            val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            if (!keyStore.containsAlias(SecureStorage.BIOMETRIC_TRIPWIRE_ALIAS)) {
                TripwireStatus.OK
            } else {
                val cipher =
                    Cipher.getInstance(
                        "${KeyProperties.KEY_ALGORITHM_AES}/${KeyProperties.BLOCK_MODE_CBC}/${KeyProperties.ENCRYPTION_PADDING_PKCS7}",
                    )
                cipher.init(
                    Cipher.ENCRYPT_MODE,
                    keyStore.getKey(SecureStorage.BIOMETRIC_TRIPWIRE_ALIAS, null) as SecretKey,
                )
                TripwireStatus.OK
            }
        } catch (e: KeyPermanentlyInvalidatedException) {
            TripwireStatus.CHANGED
        } catch (e: Exception) {
            // Fail closed — callers decide whether to block or retry. Never report
            // a possibly-changed enrollment as OK.
            TripwireStatus.UNKNOWN
        }

    /**
     * (Re)baseline the tripwire to the current fingerprint enrollment state.
     * The tripwire wraps nothing, so deleting/recreating it is always safe.
     */
    fun armTripwire() {
        try {
            val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            runCatching { keyStore.deleteEntry(SecureStorage.BIOMETRIC_TRIPWIRE_ALIAS) }
            val keyGen =
                KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES,
                    "AndroidKeyStore",
                )
            keyGen.init(
                KeyGenParameterSpec.Builder(
                    SecureStorage.BIOMETRIC_TRIPWIRE_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_CBC)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7)
                    .setInvalidatedByBiometricEnrollment(true)
                    .build(),
            )
            keyGen.generateKey()
        } catch (_: Exception) {
        }
    }

    fun disarmTripwire() {
        try {
            val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            keyStore.deleteEntry(SecureStorage.BIOMETRIC_TRIPWIRE_ALIAS)
        } catch (_: Exception) {
        }
    }

}
