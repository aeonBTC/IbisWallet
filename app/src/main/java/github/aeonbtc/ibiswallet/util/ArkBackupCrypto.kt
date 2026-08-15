package github.aeonbtc.ibiswallet.util

import github.aeonbtc.ibiswallet.util.ArkBackupCrypto.encrypt
import github.aeonbtc.ibiswallet.util.ArkBackupCrypto.isEncrypted
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Seed-derived AES-256-GCM encryption for Ark (Bark) DB backups.
 *
 * Format: MAGIC(8) || VERSION(1) || NONCE(12) || CIPHERTEXT+TAG
 * Key: HKDF-SHA256(bip39Seed, info="ibis-ark-backup-v1") → 32-byte AES key.
 *
 * Only the wallet's seed can decrypt. Wrong seed → [WrongWalletException].
 * Legacy plaintext zip backups remain supported by callers that check [isEncrypted].
 */
object ArkBackupCrypto {
    const val MAGIC = "IBARKENC"
    const val VERSION: Byte = 1
    const val NONCE_SIZE = 12
    const val TAG_BITS = 128
    const val KEY_SIZE = 32
    const val HEADER_SIZE = 8 + 1 + NONCE_SIZE
    const val MIN_ENCRYPTED_SIZE = HEADER_SIZE + 16

    private val MAGIC_BYTES = MAGIC.toByteArray(Charsets.US_ASCII)
    private val HKDF_SALT = "ibis-ark-backup".toByteArray(Charsets.US_ASCII)
    private val HKDF_INFO = "ibis-ark-backup-v1".toByteArray(Charsets.US_ASCII)

    class WrongWalletException(
        message: String = "Ark backup does not belong to this wallet",
    ) : Exception(message)

    class InvalidPayloadException(
        message: String = "Invalid Ark backup payload",
    ) : Exception(message)

    fun isEncrypted(bytes: ByteArray): Boolean {
        if (bytes.size < HEADER_SIZE) return false
        for (i in MAGIC_BYTES.indices) {
            if (bytes[i] != MAGIC_BYTES[i]) return false
        }
        return true
    }

    /**
     * Encrypt [plaintext] (typically a zip of the Bark data dir) with a key derived from [bip39Seed].
     */
    fun encrypt(
        plaintext: ByteArray,
        bip39Seed: ByteArray,
    ): ByteArray {
        require(bip39Seed.isNotEmpty()) { "Empty seed" }
        val keyBytes = deriveKey(bip39Seed)
        try {
            val nonce = ByteArray(NONCE_SIZE).also { SecureRandom().nextBytes(it) }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(keyBytes, "AES"), GCMParameterSpec(TAG_BITS, nonce))
            val ciphertext = cipher.doFinal(plaintext)
            val out = ByteArray(HEADER_SIZE + ciphertext.size)
            System.arraycopy(MAGIC_BYTES, 0, out, 0, 8)
            out[8] = VERSION
            System.arraycopy(nonce, 0, out, 9, NONCE_SIZE)
            System.arraycopy(ciphertext, 0, out, HEADER_SIZE, ciphertext.size)
            return out
        } finally {
            keyBytes.fill(0)
        }
    }

    /**
     * Decrypt a payload produced by [encrypt].
     * @throws WrongWalletException if the seed does not match or the payload is corrupted
     * @throws InvalidPayloadException if the header is malformed
     */
    fun decrypt(
        payload: ByteArray,
        bip39Seed: ByteArray,
    ): ByteArray {
        if (!isEncrypted(payload)) {
            throw InvalidPayloadException("Not an encrypted Ark backup")
        }
        if (payload.size < MIN_ENCRYPTED_SIZE) {
            throw InvalidPayloadException("Encrypted Ark backup is truncated")
        }
        val version = payload[8]
        if (version != VERSION) {
            throw InvalidPayloadException("Unsupported Ark backup version: $version")
        }
        require(bip39Seed.isNotEmpty()) { "Empty seed" }
        val nonce = payload.copyOfRange(9, 9 + NONCE_SIZE)
        val ciphertext = payload.copyOfRange(HEADER_SIZE, payload.size)
        val keyBytes = deriveKey(bip39Seed)
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(keyBytes, "AES"), GCMParameterSpec(TAG_BITS, nonce))
            return cipher.doFinal(ciphertext)
        } catch (_: AEADBadTagException) {
            throw WrongWalletException()
        } catch (e: Exception) {
            if (e is WrongWalletException || e is InvalidPayloadException) throw e
            throw WrongWalletException()
        } finally {
            keyBytes.fill(0)
        }
    }

    /**
     * If [payload] is encrypted, decrypt with [bip39Seed]; otherwise return it unchanged
     * (legacy plaintext zip).
     */
    fun unwrapIfEncrypted(
        payload: ByteArray,
        bip39Seed: ByteArray,
    ): ByteArray =
        if (isEncrypted(payload)) {
            decrypt(payload, bip39Seed)
        } else {
            payload
        }

    /** Stable short fingerprint of a seed for manifest binding (not secret). */
    fun seedFingerprint(bip39Seed: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bip39Seed)
        return digest.take(8).joinToString("") { "%02x".format(it) }
    }

    private fun deriveKey(bip39Seed: ByteArray): ByteArray {
        // HKDF-Extract
        val prk = hmacSha256(HKDF_SALT, bip39Seed)
        // HKDF-Expand to 32 bytes (one block)
        val okm = hmacSha256(prk, HKDF_INFO + byteArrayOf(0x01))
        prk.fill(0)
        return okm
    }

    private fun hmacSha256(
        key: ByteArray,
        data: ByteArray,
    ): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }
}
