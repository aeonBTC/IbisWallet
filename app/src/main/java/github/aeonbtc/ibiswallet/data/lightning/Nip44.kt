package github.aeonbtc.ibiswallet.data.lightning

import android.util.Base64
import org.bitcoinj.crypto.ECKey
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.floor
import kotlin.math.ln

/**
 * NIP-44 v2 (secp256k1 ECDH + HKDF + ChaCha20 + HMAC-SHA256).
 * Used by modern NWC wallet services (Alby, etc.).
 */
object Nip44 {
    private const val VERSION: Byte = 0x02
    private const val MIN_PLAINTEXT = 1L
    private const val MAX_PLAINTEXT = 0xFFFF_FFFFL // 2^32 - 1
    private const val EXTENDED_PREFIX_THRESHOLD = 65_536L
    private val SALT = "nip44-v2".toByteArray(StandardCharsets.UTF_8)

    fun encrypt(
        plaintext: String,
        privateKey: ECKey,
        recipientXOnlyPubkeyHex: String,
    ): String {
        val conversationKey = conversationKey(privateKey, recipientXOnlyPubkeyHex)
        val nonce = ByteArray(32).also { SecureRandom().nextBytes(it) }
        return encryptWithNonce(plaintext, conversationKey, nonce)
    }

    fun decrypt(
        payload: String,
        privateKey: ECKey,
        senderXOnlyPubkeyHex: String,
    ): String {
        require(payload.isNotEmpty()) { "Empty NIP-44 payload" }
        require(!payload.startsWith("#")) { "Unsupported non-base64 NIP-44 version flag" }
        val conversationKey = conversationKey(privateKey, senderXOnlyPubkeyHex)
        return decryptWithConversationKey(payload, conversationKey)
    }

    internal fun encryptWithNonce(
        plaintext: String,
        conversationKey: ByteArray,
        nonce: ByteArray,
    ): String {
        val (chachaKey, chachaNonce, hmacKey) = messageKeys(conversationKey, nonce)
        val padded = pad(plaintext.toByteArray(StandardCharsets.UTF_8))
        val ciphertext = chacha20(chachaKey, chachaNonce, padded)
        val mac = hmacAad(hmacKey, ciphertext, nonce)
        val raw = ByteArray(1 + nonce.size + ciphertext.size + mac.size)
        raw[0] = VERSION
        System.arraycopy(nonce, 0, raw, 1, nonce.size)
        System.arraycopy(ciphertext, 0, raw, 1 + nonce.size, ciphertext.size)
        System.arraycopy(mac, 0, raw, 1 + nonce.size + ciphertext.size, mac.size)
        return Base64.encodeToString(raw, Base64.NO_WRAP)
    }

    internal fun decryptWithConversationKey(
        payload: String,
        conversationKey: ByteArray,
    ): String {
        require(payload.length >= 132) { "Invalid NIP-44 payload size" }
        val data =
            try {
                Base64.decode(payload, Base64.DEFAULT)
            } catch (e: IllegalArgumentException) {
                throw IllegalArgumentException("Invalid NIP-44 base64", e)
            }
        require(data.size >= 99) { "Invalid NIP-44 data size" }
        require(data[0] == VERSION) { "Unsupported NIP-44 version ${data[0].toInt() and 0xff}" }
        val nonce = data.copyOfRange(1, 33)
        val mac = data.copyOfRange(data.size - 32, data.size)
        val ciphertext = data.copyOfRange(33, data.size - 32)
        val (chachaKey, chachaNonce, hmacKey) = messageKeys(conversationKey, nonce)
        val calculated = hmacAad(hmacKey, ciphertext, nonce)
        require(constantTimeEquals(calculated, mac)) { "Invalid NIP-44 MAC" }
        val padded = chacha20(chachaKey, chachaNonce, ciphertext)
        return String(unpad(padded), StandardCharsets.UTF_8)
    }

    internal fun conversationKey(
        privateKey: ECKey,
        remoteXOnlyPubkeyHex: String,
    ): ByteArray {
        val sharedX = ecdhSharedX(privateKey, remoteXOnlyPubkeyHex)
        return hkdfExtract(sharedX, SALT)
    }

    private fun messageKeys(
        conversationKey: ByteArray,
        nonce: ByteArray,
    ): Triple<ByteArray, ByteArray, ByteArray> {
        require(conversationKey.size == 32)
        require(nonce.size == 32)
        val keys = hkdfExpand(conversationKey, nonce, 76)
        return Triple(
            keys.copyOfRange(0, 32),
            keys.copyOfRange(32, 44),
            keys.copyOfRange(44, 76),
        )
    }

    private fun ecdhSharedX(
        privateKey: ECKey,
        remoteXOnlyPubkeyHex: String,
    ): ByteArray {
        val clean = remoteXOnlyPubkeyHex.removePrefix("0x").lowercase()
        val remoteBytes =
            when (clean.length) {
                64 -> hexToBytes("02$clean")
                66 -> hexToBytes(clean)
                else -> throw IllegalArgumentException("Invalid remote pubkey")
            }
        val remote = ECKey.fromPublicOnly(remoteBytes)
        val sharedPoint = remote.pubKeyPoint.multiply(privateKey.privKey).normalize()
        return pad32(sharedPoint.xCoord.encoded)
    }

    private fun pad(unpadded: ByteArray): ByteArray {
        val unpaddedLen = unpadded.size.toLong()
        require(unpaddedLen in MIN_PLAINTEXT..MAX_PLAINTEXT) { "Invalid plaintext length" }
        val prefix =
            if (unpaddedLen >= EXTENDED_PREFIX_THRESHOLD) {
                ByteBuffer.allocate(6).order(ByteOrder.BIG_ENDIAN)
                    .putShort(0)
                    .putInt(unpaddedLen.toInt())
                    .array()
            } else {
                ByteBuffer.allocate(2).order(ByteOrder.BIG_ENDIAN)
                    .putShort(unpaddedLen.toShort())
                    .array()
            }
        val paddedLen = calcPaddedLen(unpaddedLen)
        val suffix = ByteArray((paddedLen - unpaddedLen).toInt())
        return prefix + unpadded + suffix
    }

    private fun unpad(padded: ByteArray): ByteArray {
        require(padded.size >= 2) { "Invalid padding" }
        val firstTwo =
            ((padded[0].toInt() and 0xff) shl 8) or (padded[1].toInt() and 0xff)
        val (unpaddedLen, prefixLen) =
            if (firstTwo == 0) {
                require(padded.size >= 6) { "Invalid padding" }
                val len =
                    ByteBuffer.wrap(padded, 2, 4).order(ByteOrder.BIG_ENDIAN).int.toLong() and
                        0xFFFF_FFFFL
                require(len >= EXTENDED_PREFIX_THRESHOLD) { "Invalid padding" }
                len to 6
            } else {
                firstTwo.toLong() to 2
            }
        require(unpaddedLen > 0) { "Invalid padding" }
        require(prefixLen + unpaddedLen <= padded.size) { "Invalid padding" }
        val unpadded = padded.copyOfRange(prefixLen, prefixLen + unpaddedLen.toInt())
        require(unpadded.size.toLong() == unpaddedLen) { "Invalid padding" }
        require(padded.size.toLong() == prefixLen + calcPaddedLen(unpaddedLen)) { "Invalid padding" }
        return unpadded
    }

    private fun calcPaddedLen(unpaddedLen: Long): Long {
        if (unpaddedLen <= 32) return 32
        val nextPower = 1L shl (floor(ln((unpaddedLen - 1).toDouble()) / ln(2.0)).toInt() + 1)
        val chunk =
            if (nextPower <= 256) {
                32L
            } else {
                nextPower / 8
            }
        return chunk * (floor((unpaddedLen - 1).toDouble() / chunk).toLong() + 1)
    }

    private fun hmacAad(
        key: ByteArray,
        message: ByteArray,
        aad: ByteArray,
    ): ByteArray {
        require(aad.size == 32) { "AAD must be 32 bytes" }
        return hmacSha256(key, aad + message)
    }

    private fun hkdfExtract(
        ikm: ByteArray,
        salt: ByteArray,
    ): ByteArray = hmacSha256(salt, ikm)

    private fun hkdfExpand(
        prk: ByteArray,
        info: ByteArray,
        length: Int,
    ): ByteArray {
        val result = ByteArray(length)
        var t = ByteArray(0)
        var offset = 0
        var counter = 1
        while (offset < length) {
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(prk, "HmacSHA256"))
            mac.update(t)
            mac.update(info)
            mac.update(counter.toByte())
            t = mac.doFinal()
            val copy = minOf(t.size, length - offset)
            System.arraycopy(t, 0, result, offset, copy)
            offset += copy
            counter++
        }
        return result
    }

    private fun hmacSha256(
        key: ByteArray,
        message: ByteArray,
    ): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(message)
    }

    private fun constantTimeEquals(
        a: ByteArray,
        b: ByteArray,
    ): Boolean {
        if (a.size != b.size) return false
        var diff = 0
        for (i in a.indices) {
            diff = diff or (a[i].toInt() xor b[i].toInt())
        }
        return diff == 0
    }

    private fun pad32(bytes: ByteArray): ByteArray =
        when {
            bytes.size == 32 -> bytes
            bytes.size > 32 -> bytes.copyOfRange(bytes.size - 32, bytes.size)
            else -> ByteArray(32 - bytes.size) + bytes
        }

    private fun hexToBytes(hex: String): ByteArray {
        val clean = hex.removePrefix("0x")
        require(clean.length % 2 == 0)
        return ByteArray(clean.length / 2) { i ->
            clean.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }

    /** ChaCha20 (RFC 8439) stream cipher, counter starts at 0. */
    private fun chacha20(
        key: ByteArray,
        nonce: ByteArray,
        data: ByteArray,
    ): ByteArray {
        require(key.size == 32)
        require(nonce.size == 12)
        val out = ByteArray(data.size)
        val state = IntArray(16)
        // constants "expand 32-byte k"
        state[0] = 0x61707865
        state[1] = 0x3320646e
        state[2] = 0x79622d32
        state[3] = 0x6b206574
        for (i in 0 until 8) {
            state[4 + i] = leInt(key, i * 4)
        }
        state[12] = 0 // counter
        state[13] = leInt(nonce, 0)
        state[14] = leInt(nonce, 4)
        state[15] = leInt(nonce, 8)

        var offset = 0
        val block = ByteArray(64)
        val working = IntArray(16)
        while (offset < data.size) {
            System.arraycopy(state, 0, working, 0, 16)
            // 20 rounds = 10 double rounds (column + diagonal)
            for (i in 0 until 10) {
                quarter(working, 0, 4, 8, 12)
                quarter(working, 1, 5, 9, 13)
                quarter(working, 2, 6, 10, 14)
                quarter(working, 3, 7, 11, 15)
                quarter(working, 0, 5, 10, 15)
                quarter(working, 1, 6, 11, 12)
                quarter(working, 2, 7, 8, 13)
                quarter(working, 3, 4, 9, 14)
            }
            for (i in 0 until 16) {
                val v = working[i] + state[i]
                block[i * 4] = v.toByte()
                block[i * 4 + 1] = (v ushr 8).toByte()
                block[i * 4 + 2] = (v ushr 16).toByte()
                block[i * 4 + 3] = (v ushr 24).toByte()
            }
            val n = minOf(64, data.size - offset)
            for (i in 0 until n) {
                out[offset + i] = (data[offset + i].toInt() xor block[i].toInt()).toByte()
            }
            offset += n
            // increment 32-bit little-endian counter
            state[12] = state[12] + 1
        }
        return out
    }

    private fun quarter(
        s: IntArray,
        a: Int,
        b: Int,
        c: Int,
        d: Int,
    ) {
        s[a] = s[a] + s[b]
        s[d] = rotl(s[d] xor s[a], 16)
        s[c] = s[c] + s[d]
        s[b] = rotl(s[b] xor s[c], 12)
        s[a] = s[a] + s[b]
        s[d] = rotl(s[d] xor s[a], 8)
        s[c] = s[c] + s[d]
        s[b] = rotl(s[b] xor s[c], 7)
    }

    private fun rotl(
        v: Int,
        n: Int,
    ): Int = (v shl n) or (v ushr (32 - n))

    private fun leInt(
        bytes: ByteArray,
        offset: Int,
    ): Int =
        (bytes[offset].toInt() and 0xff) or
            ((bytes[offset + 1].toInt() and 0xff) shl 8) or
            ((bytes[offset + 2].toInt() and 0xff) shl 16) or
            ((bytes[offset + 3].toInt() and 0xff) shl 24)
}
