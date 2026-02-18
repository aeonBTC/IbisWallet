package github.aeonbtc.ibiswallet.util

import java.math.BigInteger
import java.security.MessageDigest
import java.text.Normalizer
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Electrum v2+ native seed phrase support.
 *
 * Electrum seeds differ from BIP39 in three ways:
 * 1. **Validation**: HMAC-SHA512("Seed version", normalized_seed) prefix check
 *    instead of wordlist-dependent checksum.
 * 2. **Key stretching**: PBKDF2 with salt "electrum" + passphrase
 *    (BIP39 uses "mnemonic" + passphrase).
 * 3. **Derivation paths**: Standard uses m/ (P2PKH), Segwit uses m/0'/ (P2WPKH).
 *    NOT BIP44/84 paths.
 *
 * Reference: https://electrum.readthedocs.io/en/latest/seedphrase.html
 */
object ElectrumSeedUtil {

    // secp256k1 curve order
    private val CURVE_ORDER = BigInteger(
        "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEBAAEDCE6AF48A03BBFD25E8CD0364141",
        16,
    )

    // secp256k1 prime (field modulus)
    private val FIELD_PRIME = BigInteger(
        "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEFFFFFC2F",
        16,
    )

    // secp256k1 generator point
    private val G_X = BigInteger(
        "79BE667EF9DCBBAC55A06295CE870B07029BFCDB2DCE28D959F2815B16F81798",
        16,
    )
    private val G_Y = BigInteger(
        "483ADA7726A3C4655DA4FBFC0E1108A8FD17B448A68554199C47D08FFB10D4B8",
        16,
    )

    // BIP32 xprv version bytes (mainnet)
    private val XPRV_VERSION = byteArrayOf(0x04, 0x88.toByte(), 0xAD.toByte(), 0xE4.toByte())

    // BIP32 xpub version bytes (mainnet)
    private val XPUB_VERSION = byteArrayOf(0x04, 0x88.toByte(), 0xB2.toByte(), 0x1E)

    // SLIP-132 zpub version bytes (mainnet, signals BIP84/P2WPKH)
    private val ZPUB_VERSION = byteArrayOf(0x04, 0xB2.toByte(), 0x47.toByte(), 0x46)

    // ── Electrum Seed Type Detection ─────────────────────────────────

    /**
     * Electrum seed types with their HMAC prefixes and derivation paths.
     */
    enum class ElectrumSeedType(
        val prefix: String,
        val label: String,
    ) {
        /** P2PKH at m/0 (receive) and m/1 (change) */
        STANDARD("01", "Standard"),
        /** P2WPKH at m/0'/0 (receive) and m/0'/1 (change) */
        SEGWIT("100", "Segwit"),
    }

    /**
     * Normalize text following Electrum's rules:
     * NFKD normalize, lowercase, remove combining (accent) characters,
     * collapse whitespace, remove spaces between CJK characters.
     */
    fun normalizeText(seed: String): String {
        // NFKD normalize
        var s = Normalizer.normalize(seed, Normalizer.Form.NFKD)
        // lowercase
        s = s.lowercase()
        // remove combining characters (accents/diacritics)
        s = s.filter { !Character.getType(it).let { type ->
            type == Character.NON_SPACING_MARK.toInt() ||
                type == Character.COMBINING_SPACING_MARK.toInt() ||
                type == Character.ENCLOSING_MARK.toInt()
        } }
        // collapse whitespace to single spaces
        s = s.trim().replace(Regex("\\s+"), " ")
        // remove spaces between CJK characters
        val sb = StringBuilder()
        for (i in s.indices) {
            val c = s[i]
            if (c == ' ' && i > 0 && i < s.length - 1 && isCJK(s[i - 1]) && isCJK(s[i + 1])) {
                continue
            }
            sb.append(c)
        }
        return sb.toString()
    }

    /**
     * Detect if a seed phrase is a valid Electrum seed.
     * Returns the seed type, or null if not an Electrum seed.
     */
    fun getElectrumSeedType(seed: String): ElectrumSeedType? {
        val normalized = normalizeText(seed)
        if (normalized.isBlank()) return null
        val hash = hmacSha512("Seed version".toByteArray(), normalized.toByteArray(Charsets.UTF_8))
        val hex = hash.toHexString()
        // Check prefixes in order of length (longest first to avoid false matches)
        return when {
            hex.startsWith(ElectrumSeedType.SEGWIT.prefix) -> ElectrumSeedType.SEGWIT
            hex.startsWith(ElectrumSeedType.STANDARD.prefix) -> ElectrumSeedType.STANDARD
            else -> null
        }
    }

    // ── Key Derivation ───────────────────────────────────────────────

    /**
     * Derive a 64-byte BIP32 root seed from an Electrum mnemonic.
     * Uses PBKDF2-HMAC-SHA512 with salt = "electrum" + passphrase.
     */
    fun mnemonicToSeed(mnemonic: String, passphrase: String? = null): ByteArray {
        val normalizedMnemonic = normalizeText(mnemonic)
        val normalizedPassphrase = normalizeText(passphrase ?: "")
        val salt = "electrum$normalizedPassphrase"
        val spec = PBEKeySpec(
            normalizedMnemonic.toCharArray(),
            salt.toByteArray(Charsets.UTF_8),
            2048,
            512,
        )
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512")
        return factory.generateSecret(spec).encoded
    }

    /**
     * Derive the master BIP32 key from a root seed.
     * Returns (privateKey: 32 bytes, chainCode: 32 bytes).
     */
    fun masterKeyFromSeed(seed: ByteArray): Pair<ByteArray, ByteArray> {
        val i = hmacSha512("Bitcoin seed".toByteArray(), seed)
        val privateKey = i.copyOfRange(0, 32)
        val chainCode = i.copyOfRange(32, 64)

        val keyInt = BigInteger(1, privateKey)
        require(keyInt > BigInteger.ZERO && keyInt < CURVE_ORDER) {
            "Invalid master key (outside curve order)"
        }
        return Pair(privateKey, chainCode)
    }

    /**
     * Derive a hardened child key at a single index.
     * Only hardened derivation (index >= 0x80000000) is supported.
     */
    fun deriveHardenedChild(
        parentKey: ByteArray,
        parentChainCode: ByteArray,
        index: Long,
    ): Pair<ByteArray, ByteArray> {
        require(index >= 0x80000000L) { "Only hardened derivation is supported" }

        // data = 0x00 || parentKey || index (4 bytes big-endian)
        val data = ByteArray(37)
        data[0] = 0x00
        parentKey.copyInto(data, 1)
        data[33] = ((index shr 24) and 0xFF).toByte()
        data[34] = ((index shr 16) and 0xFF).toByte()
        data[35] = ((index shr 8) and 0xFF).toByte()
        data[36] = (index and 0xFF).toByte()

        val i = hmacSha512(parentChainCode, data)
        val il = i.copyOfRange(0, 32)
        val childChainCode = i.copyOfRange(32, 64)

        val parentInt = BigInteger(1, parentKey)
        val ilInt = BigInteger(1, il)
        val childInt = (parentInt + ilInt).mod(CURVE_ORDER)

        require(childInt > BigInteger.ZERO) { "Invalid child key" }

        val childKey = childInt.toByteArray32()
        return Pair(childKey, childChainCode)
    }

    /**
     * Derive an xprv string at the given derivation path from a root seed.
     * Supports paths like "m", "m/0'", "m/0'/0".
     */
    fun deriveXprv(seed: ByteArray, path: String): String {
        val (masterKey, masterChainCode) = masterKeyFromSeed(seed)

        val segments = parsePath(path)
        if (segments.isEmpty()) {
            // Return master xprv
            return encodeXprv(
                depth = 0,
                parentFingerprint = byteArrayOf(0, 0, 0, 0),
                childNumber = 0L,
                chainCode = masterChainCode,
                privateKey = masterKey,
            )
        }

        var currentKey = masterKey
        var currentChainCode = masterChainCode
        var parentFp = byteArrayOf(0, 0, 0, 0)
        var depth = 0

        for ((i, segment) in segments.withIndex()) {
            val pubKey = publicKeyFromPrivate(currentKey)
            parentFp = hash160(pubKey).copyOfRange(0, 4)
            val (childKey, childChainCode) = if (segment >= 0x80000000L) {
                deriveHardenedChild(currentKey, currentChainCode, segment)
            } else {
                deriveNormalChild(currentKey, currentChainCode, segment)
            }
            currentKey = childKey
            currentChainCode = childChainCode
            depth = i + 1
        }

        return encodeXprv(
            depth = depth,
            parentFingerprint = parentFp,
            childNumber = segments.last(),
            chainCode = currentChainCode,
            privateKey = currentKey,
        )
    }

    /**
     * Compute the master fingerprint (first 4 bytes of hash160 of the master public key).
     * Returns lowercase hex string (8 chars).
     */
    fun computeMasterFingerprint(seed: ByteArray): String {
        val (masterKey, _) = masterKeyFromSeed(seed)
        val masterPubKey = publicKeyFromPrivate(masterKey)
        return hash160(masterPubKey).copyOfRange(0, 4).toHexString()
    }

    /**
     * Build the external and internal descriptor strings for an Electrum seed.
     *
     * @return Pair(externalDescriptor, internalDescriptor)
     */
    fun buildDescriptorStrings(
        seed: ByteArray,
        seedType: ElectrumSeedType,
    ): Pair<String, String> {
        val fingerprint = computeMasterFingerprint(seed)

        return when (seedType) {
            ElectrumSeedType.STANDARD -> {
                // P2PKH at m/0 (receive) and m/1 (change)
                // Electrum standard: master key directly, children at m/{change}/{index}
                val masterXprv = deriveXprv(seed, "m")
                val external = "pkh([$fingerprint]$masterXprv/0/*)"
                val internal = "pkh([$fingerprint]$masterXprv/1/*)"
                Pair(external, internal)
            }
            ElectrumSeedType.SEGWIT -> {
                // P2WPKH at m/0'/0 (receive) and m/0'/1 (change)
                val childXprv = deriveXprv(seed, "m/0'")
                val external = "wpkh([$fingerprint/0']$childXprv/0/*)"
                val internal = "wpkh([$fingerprint/0']$childXprv/1/*)"
                Pair(external, internal)
            }
        }
    }

    /**
     * Derive the extended public key for an Electrum seed.
     * Returns zpub for Segwit (matching Electrum desktop), xpub for Standard.
     */
    fun deriveExtendedPublicKey(seed: ByteArray, seedType: ElectrumSeedType): String {
        val (masterKey, masterChainCode) = masterKeyFromSeed(seed)

        return when (seedType) {
            ElectrumSeedType.STANDARD -> {
                // Master key at m — encode as xpub
                val pubKey = publicKeyFromPrivate(masterKey)
                encodeXpub(
                    version = XPUB_VERSION,
                    depth = 0,
                    parentFingerprint = byteArrayOf(0, 0, 0, 0),
                    childNumber = 0L,
                    chainCode = masterChainCode,
                    publicKey = pubKey,
                )
            }
            ElectrumSeedType.SEGWIT -> {
                // Child key at m/0' — encode as zpub (SLIP-132, signals P2WPKH)
                val masterPubKey = publicKeyFromPrivate(masterKey)
                val parentFp = hash160(masterPubKey).copyOfRange(0, 4)
                val (childKey, childChainCode) = deriveHardenedChild(
                    masterKey,
                    masterChainCode,
                    0x80000000L,
                )
                val childPubKey = publicKeyFromPrivate(childKey)
                encodeXpub(
                    version = ZPUB_VERSION,
                    depth = 1,
                    parentFingerprint = parentFp,
                    childNumber = 0x80000000L,
                    chainCode = childChainCode,
                    publicKey = childPubKey,
                )
            }
        }
    }

    // ── Normal (non-hardened) Child Derivation ───────────────────────

    /**
     * Derive a normal (non-hardened) child key at a single index.
     */
    private fun deriveNormalChild(
        parentKey: ByteArray,
        parentChainCode: ByteArray,
        index: Long,
    ): Pair<ByteArray, ByteArray> {
        require(index < 0x80000000L) { "Use deriveHardenedChild for hardened indices" }

        val parentPub = publicKeyFromPrivate(parentKey)

        // data = compressed_pubkey || index (4 bytes big-endian)
        val data = ByteArray(37)
        parentPub.copyInto(data, 0)
        data[33] = ((index shr 24) and 0xFF).toByte()
        data[34] = ((index shr 16) and 0xFF).toByte()
        data[35] = ((index shr 8) and 0xFF).toByte()
        data[36] = (index and 0xFF).toByte()

        val i = hmacSha512(parentChainCode, data)
        val il = i.copyOfRange(0, 32)
        val childChainCode = i.copyOfRange(32, 64)

        val parentInt = BigInteger(1, parentKey)
        val ilInt = BigInteger(1, il)
        val childInt = (parentInt + ilInt).mod(CURVE_ORDER)

        require(childInt > BigInteger.ZERO) { "Invalid child key" }

        val childKey = childInt.toByteArray32()
        return Pair(childKey, childChainCode)
    }

    // ── EC Point Arithmetic (secp256k1) ──────────────────────────────

    /**
     * Derive the compressed public key (33 bytes) from a 32-byte private key.
     */
    private fun publicKeyFromPrivate(privateKey: ByteArray): ByteArray {
        val k = BigInteger(1, privateKey)
        val (x, y) = ecMultiply(k)
        val prefix = if (y.testBit(0)) 0x03.toByte() else 0x02.toByte()
        val xBytes = x.toByteArray32()
        return byteArrayOf(prefix) + xBytes
    }

    /**
     * EC point multiplication: k * G on secp256k1.
     * Uses double-and-add algorithm.
     */
    private fun ecMultiply(k: BigInteger): Pair<BigInteger, BigInteger> {
        var rx = BigInteger.ZERO
        var ry = BigInteger.ZERO
        var isInfinity = true
        var qx = G_X
        var qy = G_Y
        var n = k

        while (n > BigInteger.ZERO) {
            if (n.testBit(0)) {
                if (isInfinity) {
                    rx = qx
                    ry = qy
                    isInfinity = false
                } else {
                    val (sx, sy) = ecAdd(rx, ry, qx, qy)
                    rx = sx
                    ry = sy
                }
            }
            val (dx, dy) = ecDouble(qx, qy)
            qx = dx
            qy = dy
            n = n.shiftRight(1)
        }

        return Pair(rx, ry)
    }

    /**
     * EC point addition on secp256k1.
     */
    private fun ecAdd(
        x1: BigInteger,
        y1: BigInteger,
        x2: BigInteger,
        y2: BigInteger,
    ): Pair<BigInteger, BigInteger> {
        if (x1 == x2 && y1 == y2) return ecDouble(x1, y1)

        val dx = (x2 - x1).mod(FIELD_PRIME)
        val dy = (y2 - y1).mod(FIELD_PRIME)
        val lambda = (dy * dx.modInverse(FIELD_PRIME)).mod(FIELD_PRIME)
        val x3 = (lambda * lambda - x1 - x2).mod(FIELD_PRIME)
        val y3 = (lambda * (x1 - x3) - y1).mod(FIELD_PRIME)
        return Pair(x3, y3)
    }

    /**
     * EC point doubling on secp256k1 (a = 0).
     */
    private fun ecDouble(x: BigInteger, y: BigInteger): Pair<BigInteger, BigInteger> {
        val three = BigInteger.valueOf(3)
        val two = BigInteger.valueOf(2)
        val lambda = (three * x * x * (two * y).modInverse(FIELD_PRIME)).mod(FIELD_PRIME)
        val x3 = (lambda * lambda - two * x).mod(FIELD_PRIME)
        val y3 = (lambda * (x - x3) - y).mod(FIELD_PRIME)
        return Pair(x3, y3)
    }

    // ── Encoding / Hashing ───────────────────────────────────────────

    /**
     * Encode an extended private key as a Base58Check xprv string.
     */
    private fun encodeXprv(
        depth: Int,
        parentFingerprint: ByteArray,
        childNumber: Long,
        chainCode: ByteArray,
        privateKey: ByteArray,
    ): String {
        val payload = ByteArray(78)
        // version (4 bytes)
        XPRV_VERSION.copyInto(payload, 0)
        // depth (1 byte)
        payload[4] = depth.toByte()
        // parent fingerprint (4 bytes)
        parentFingerprint.copyInto(payload, 5)
        // child number (4 bytes big-endian)
        payload[9] = ((childNumber shr 24) and 0xFF).toByte()
        payload[10] = ((childNumber shr 16) and 0xFF).toByte()
        payload[11] = ((childNumber shr 8) and 0xFF).toByte()
        payload[12] = (childNumber and 0xFF).toByte()
        // chain code (32 bytes)
        chainCode.copyInto(payload, 13)
        // 0x00 || private key (33 bytes)
        payload[45] = 0x00
        privateKey.copyInto(payload, 46)

        return base58CheckEncode(payload)
    }

    /**
     * Encode an extended public key as a Base58Check string.
     * Version bytes determine the prefix (xpub, zpub, etc).
     */
    private fun encodeXpub(
        version: ByteArray,
        depth: Int,
        parentFingerprint: ByteArray,
        childNumber: Long,
        chainCode: ByteArray,
        publicKey: ByteArray,
    ): String {
        val payload = ByteArray(78)
        version.copyInto(payload, 0)
        payload[4] = depth.toByte()
        parentFingerprint.copyInto(payload, 5)
        payload[9] = ((childNumber shr 24) and 0xFF).toByte()
        payload[10] = ((childNumber shr 16) and 0xFF).toByte()
        payload[11] = ((childNumber shr 8) and 0xFF).toByte()
        payload[12] = (childNumber and 0xFF).toByte()
        chainCode.copyInto(payload, 13)
        publicKey.copyInto(payload, 45) // 33-byte compressed public key
        return base58CheckEncode(payload)
    }

    /**
     * HMAC-SHA512.
     */
    private fun hmacSha512(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA512")
        mac.init(SecretKeySpec(key, "HmacSHA512"))
        return mac.doFinal(data)
    }

    /**
     * SHA-256 single hash.
     */
    private fun sha256(data: ByteArray): ByteArray {
        return MessageDigest.getInstance("SHA-256").digest(data)
    }

    /**
     * RIPEMD-160(SHA-256(data)) — "hash160".
     */
    private fun hash160(data: ByteArray): ByteArray {
        val sha = sha256(data)
        return ripemd160(sha)
    }

    /**
     * Pure Kotlin RIPEMD-160 implementation.
     *
     * Android removed Bouncy Castle's RIPEMD160 provider in newer API levels,
     * so we implement it directly. Reference: ISO/IEC 10118-3:2004.
     * https://homes.esat.kuleuven.be/~bosMDer/ripemd160/pdf/AB-9601/AB-9601.pdf
     */
    private fun ripemd160(message: ByteArray): ByteArray {
        // Initial hash values
        var h0 = 0x67452301
        var h1 = 0xEFCDAB89.toInt()
        var h2 = 0x98BADCFE.toInt()
        var h3 = 0x10325476
        var h4 = 0xC3D2E1F0.toInt()

        // Pre-processing: add padding
        val bitLength = message.size.toLong() * 8
        // message + 0x80 + zeros + 8-byte length
        val paddedLength = ((message.size + 9 + 63) / 64) * 64
        val padded = ByteArray(paddedLength)
        message.copyInto(padded)
        padded[message.size] = 0x80.toByte()
        // Length in bits as 64-bit little-endian
        for (i in 0..7) {
            padded[paddedLength - 8 + i] = ((bitLength ushr (i * 8)) and 0xFF).toByte()
        }

        // Process each 64-byte block
        val x = IntArray(16)
        for (offset in 0 until paddedLength step 64) {
            for (i in 0..15) {
                val j = offset + i * 4
                x[i] = (padded[j].toInt() and 0xFF) or
                    ((padded[j + 1].toInt() and 0xFF) shl 8) or
                    ((padded[j + 2].toInt() and 0xFF) shl 16) or
                    ((padded[j + 3].toInt() and 0xFF) shl 24)
            }

            var al = h0; var bl = h1; var cl = h2; var dl = h3; var el = h4
            var ar = h0; var br = h1; var cr = h2; var dr = h3; var er = h4

            // 80 rounds, left and right in parallel
            for (j in 0..79) {
                // Left round
                val fl = when (j) {
                    in 0..15 -> bl xor cl xor dl
                    in 16..31 -> (bl and cl) or (bl.inv() and dl)
                    in 32..47 -> (bl or cl.inv()) xor dl
                    in 48..63 -> (bl and dl) or (cl and dl.inv())
                    else -> bl xor (cl or dl.inv())
                }
                val kl = when (j) {
                    in 0..15 -> 0x00000000
                    in 16..31 -> 0x5A827999
                    in 32..47 -> 0x6ED9EBA1
                    in 48..63 -> 0x8F1BBCDC.toInt()
                    else -> 0xA953FD4E.toInt()
                }
                var tl = al + fl + x[RL[j]] + kl
                tl = Integer.rotateLeft(tl, SL[j]) + el
                al = el; el = dl; dl = Integer.rotateLeft(cl, 10); cl = bl; bl = tl

                // Right round
                val fr = when (j) {
                    in 0..15 -> br xor (cr or dr.inv())
                    in 16..31 -> (br and dr) or (cr and dr.inv())
                    in 32..47 -> (br or cr.inv()) xor dr
                    in 48..63 -> (br and cr) or (br.inv() and dr)
                    else -> br xor cr xor dr
                }
                val kr = when (j) {
                    in 0..15 -> 0x50A28BE6
                    in 16..31 -> 0x5C4DD124
                    in 32..47 -> 0x6D703EF3
                    in 48..63 -> 0x7A6D76E9
                    else -> 0x00000000
                }
                var tr = ar + fr + x[RR[j]] + kr
                tr = Integer.rotateLeft(tr, SR[j]) + er
                ar = er; er = dr; dr = Integer.rotateLeft(cr, 10); cr = br; br = tr
            }

            val t = h1 + cl + dr
            h1 = h2 + dl + er
            h2 = h3 + el + ar
            h3 = h4 + al + br
            h4 = h0 + bl + cr
            h0 = t
        }

        // Produce the 20-byte digest (little-endian)
        val digest = ByteArray(20)
        for (i in 0..3) {
            digest[i] = ((h0 ushr (i * 8)) and 0xFF).toByte()
            digest[i + 4] = ((h1 ushr (i * 8)) and 0xFF).toByte()
            digest[i + 8] = ((h2 ushr (i * 8)) and 0xFF).toByte()
            digest[i + 12] = ((h3 ushr (i * 8)) and 0xFF).toByte()
            digest[i + 16] = ((h4 ushr (i * 8)) and 0xFF).toByte()
        }
        return digest
    }

    // RIPEMD-160 message word selection (left rounds)
    private val RL = intArrayOf(
        0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15,
        7, 4, 13, 1, 10, 6, 15, 3, 12, 0, 9, 5, 2, 14, 11, 8,
        3, 10, 14, 4, 9, 15, 8, 1, 2, 7, 0, 6, 13, 11, 5, 12,
        1, 9, 11, 10, 0, 8, 12, 4, 13, 3, 7, 15, 14, 5, 6, 2,
        4, 0, 5, 9, 7, 12, 2, 10, 14, 1, 3, 8, 11, 6, 15, 13,
    )

    // RIPEMD-160 message word selection (right rounds)
    private val RR = intArrayOf(
        5, 14, 7, 0, 9, 2, 11, 4, 13, 6, 15, 8, 1, 10, 3, 12,
        6, 11, 3, 7, 0, 13, 5, 10, 14, 15, 8, 12, 4, 9, 1, 2,
        15, 5, 1, 3, 7, 14, 6, 9, 11, 8, 12, 2, 10, 0, 4, 13,
        8, 6, 4, 1, 3, 11, 15, 0, 5, 12, 2, 13, 9, 7, 10, 14,
        12, 15, 10, 4, 1, 5, 8, 7, 6, 2, 13, 14, 0, 3, 9, 11,
    )

    // RIPEMD-160 left rotation amounts (left rounds)
    private val SL = intArrayOf(
        11, 14, 15, 12, 5, 8, 7, 9, 11, 13, 14, 15, 6, 7, 9, 8,
        7, 6, 8, 13, 11, 9, 7, 15, 7, 12, 15, 9, 11, 7, 13, 12,
        11, 13, 6, 7, 14, 9, 13, 15, 14, 8, 13, 6, 5, 12, 7, 5,
        11, 12, 14, 15, 14, 15, 9, 8, 9, 14, 5, 6, 8, 6, 5, 12,
        9, 15, 5, 11, 6, 8, 13, 12, 5, 12, 13, 14, 11, 8, 5, 6,
    )

    // RIPEMD-160 left rotation amounts (right rounds)
    private val SR = intArrayOf(
        8, 9, 9, 11, 13, 15, 15, 5, 7, 7, 8, 11, 14, 14, 12, 6,
        9, 13, 15, 7, 12, 8, 9, 11, 7, 7, 12, 7, 6, 15, 13, 11,
        9, 7, 15, 11, 8, 6, 6, 14, 12, 13, 5, 14, 13, 13, 7, 5,
        15, 5, 8, 11, 14, 14, 6, 14, 6, 9, 12, 9, 12, 5, 15, 8,
        8, 5, 12, 9, 12, 5, 14, 6, 8, 13, 6, 5, 15, 13, 11, 11,
    )

    /**
     * Base58Check encoding (with 4-byte checksum).
     */
    private fun base58CheckEncode(payload: ByteArray): String {
        val checksum = sha256(sha256(payload)).copyOfRange(0, 4)
        val data = payload + checksum
        return base58Encode(data)
    }

    /**
     * Raw Base58 encoding.
     */
    private fun base58Encode(data: ByteArray): String {
        val alphabet = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
        var num = BigInteger(1, data)
        val sb = StringBuilder()
        val base = BigInteger.valueOf(58)
        while (num > BigInteger.ZERO) {
            val (quotient, remainder) = num.divideAndRemainder(base)
            sb.append(alphabet[remainder.toInt()])
            num = quotient
        }
        // Preserve leading zeros
        for (b in data) {
            if (b == 0.toByte()) {
                sb.append('1')
            } else {
                break
            }
        }
        return sb.reverse().toString()
    }

    /**
     * Parse a BIP32 derivation path string into a list of child indices.
     * e.g. "m/0'" -> [0x80000000], "m/0'/0" -> [0x80000000, 0]
     */
    private fun parsePath(path: String): List<Long> {
        val stripped = path.removePrefix("m").removePrefix("/")
        if (stripped.isBlank()) return emptyList()
        return stripped.split("/").map { segment ->
            val hardened = segment.endsWith("'") || segment.endsWith("h") || segment.endsWith("H")
            val index = segment.trimEnd('\'', 'h', 'H').toLong()
            if (hardened) index + 0x80000000L else index
        }
    }

    /**
     * Check if a character is CJK (Chinese/Japanese/Korean).
     */
    private fun isCJK(c: Char): Boolean {
        val block = Character.UnicodeBlock.of(c)
        return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS ||
            block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A ||
            block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B ||
            block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS ||
            block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS_SUPPLEMENT ||
            block == Character.UnicodeBlock.KANGXI_RADICALS ||
            block == Character.UnicodeBlock.CJK_RADICALS_SUPPLEMENT ||
            block == Character.UnicodeBlock.CJK_STROKES ||
            block == Character.UnicodeBlock.IDEOGRAPHIC_DESCRIPTION_CHARACTERS ||
            block == Character.UnicodeBlock.BOPOMOFO ||
            block == Character.UnicodeBlock.BOPOMOFO_EXTENDED ||
            block == Character.UnicodeBlock.HALFWIDTH_AND_FULLWIDTH_FORMS ||
            block == Character.UnicodeBlock.HIRAGANA ||
            block == Character.UnicodeBlock.KATAKANA ||
            block == Character.UnicodeBlock.KATAKANA_PHONETIC_EXTENSIONS ||
            block == Character.UnicodeBlock.HANGUL_SYLLABLES ||
            block == Character.UnicodeBlock.HANGUL_JAMO ||
            block == Character.UnicodeBlock.HANGUL_JAMO_EXTENDED_A ||
            block == Character.UnicodeBlock.HANGUL_JAMO_EXTENDED_B ||
            block == Character.UnicodeBlock.HANGUL_COMPATIBILITY_JAMO
    }

    // ── Extension Helpers ────────────────────────────────────────────

    /**
     * Convert BigInteger to exactly 32 bytes, zero-padded on the left.
     */
    private fun BigInteger.toByteArray32(): ByteArray {
        val bytes = this.toByteArray()
        return when {
            bytes.size == 32 -> bytes
            bytes.size > 32 -> bytes.copyOfRange(bytes.size - 32, bytes.size)
            else -> ByteArray(32 - bytes.size) + bytes
        }
    }

    /**
     * Convert ByteArray to lowercase hex string.
     */
    private fun ByteArray.toHexString(): String =
        joinToString("") { "%02x".format(it) }
}
