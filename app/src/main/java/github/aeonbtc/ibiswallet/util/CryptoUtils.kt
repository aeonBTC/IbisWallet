package github.aeonbtc.ibiswallet.util

import java.security.ProviderException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.ShortBufferException
import javax.crypto.AEADBadTagException
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Pure-logic encryption utilities extracted from WalletViewModel
 * for testability. AES-256-GCM + PBKDF2 for backup encryption.
 *
 * No Android dependencies. Pure JDK crypto.
 */
object CryptoUtils {

    const val PBKDF2_ITERATIONS = 600_000

    /**
     * Encrypted payload container.
     */
    data class EncryptedPayload(
        val salt: ByteArray,
        val iv: ByteArray,
        val ciphertext: ByteArray,
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is EncryptedPayload) return false
            return salt.contentEquals(other.salt) &&
                iv.contentEquals(other.iv) &&
                ciphertext.contentEquals(other.ciphertext)
        }

        override fun hashCode(): Int {
            var result = salt.contentHashCode()
            result = 31 * result + iv.contentHashCode()
            result = 31 * result + ciphertext.contentHashCode()
            return result
        }
    }

    /**
     * Encrypt plaintext with AES-256-GCM using PBKDF2-derived key.
     *
     * @param plaintext data to encrypt
     * @param password user password
     * @param iterations PBKDF2 iteration count (default 600,000)
     * @return encrypted payload with salt, IV, and ciphertext
     */
    fun encrypt(
        plaintext: ByteArray,
        password: String,
        iterations: Int = PBKDF2_ITERATIONS,
    ): EncryptedPayload {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }

        val passwordChars = password.toCharArray()
        try {
            val aesKey = deriveKey(passwordChars, salt, iterations)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, aesKey, GCMParameterSpec(128, iv))
            val ciphertext = cipher.doFinal(plaintext)
            return EncryptedPayload(salt, iv, ciphertext)
        } finally {
            passwordChars.fill('\u0000')
        }
    }

    /**
     * Decrypt an encrypted payload with AES-256-GCM using PBKDF2-derived key.
     *
     * @param payload encrypted data with salt, IV, and ciphertext
     * @param password user password
     * @param iterations PBKDF2 iteration count (default 600,000)
     * @return decrypted plaintext
     * @throws javax.crypto.AEADBadTagException if password is wrong or data is corrupted
     */
    fun decrypt(
        payload: EncryptedPayload,
        password: String,
        iterations: Int = PBKDF2_ITERATIONS,
    ): ByteArray {
        val passwordChars = password.toCharArray()
        try {
            val aesKey = deriveKey(passwordChars, payload.salt, iterations)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, aesKey, GCMParameterSpec(128, payload.iv))
            return try {
                cipher.doFinal(payload.ciphertext)
            } catch (e: ProviderException) {
                if (e.cause is ShortBufferException) {
                    throw AEADBadTagException("Ciphertext is truncated or invalid").apply {
                        initCause(e)
                    }
                }
                throw e
            }
        } finally {
            passwordChars.fill('\u0000')
        }
    }

    /**
     * Derive AES-256 key from password using PBKDF2WithHmacSHA256.
     */
    private fun deriveKey(
        passwordChars: CharArray,
        salt: ByteArray,
        iterations: Int,
    ): SecretKeySpec {
        val keySpec = PBEKeySpec(passwordChars, salt, iterations, 256)
        val secretKey = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(keySpec)
        return SecretKeySpec(secretKey.encoded, "AES")
    }
}
