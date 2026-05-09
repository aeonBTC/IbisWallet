package github.aeonbtc.ibiswallet.util

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Minimal Elements PSET signer for wallets with a BIP39 passphrase.
 *
 * LWK's Signer FFI hardcodes an empty passphrase (`mnemonic.to_seed("")`),
 * so when a passphrase is in use we derive keys from the correct seed
 * ourselves, compute the Elements segwit v0 sighash, produce ECDSA
 * signatures with RFC 6979 deterministic nonces, and splice them into the
 * raw PSET bytes before handing back to LWK for finalization.
 */
object ElementsPsetSigner {

    private val CURVE_ORDER = BigInteger(
        "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEBAAEDCE6AF48A03BBFD25E8CD0364141", 16,
    )
    private val HALF_ORDER = CURVE_ORDER.shiftRight(1)
    private val FIELD_PRIME = BigInteger(
        "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEFFFFFC2F", 16,
    )
    private val G_X = BigInteger(
        "79BE667EF9DCBBAC55A06295CE870B07029BFCDB2DCE28D959F2815B16F81798", 16,
    )
    private val G_Y = BigInteger(
        "483ADA7726A3C4655DA4FBFC0E1108A8FD17B448A68554199C47D08FFB10D4B8", 16,
    )

    private const val SIGHASH_ALL = 1

    /**
     * Sign a PSET (base64) using private keys derived from [seed]
     * (the BIP39 seed with the passphrase already baked in).
     *
     * Only segwit-v0 P2WPKH inputs whose BIP32 derivation fingerprint
     * matches the seed's master fingerprint are signed.
     *
     * @return signed PSET as base64
     */
    fun sign(psetBase64: String, seed: ByteArray): String {
        val raw = android.util.Base64.decode(psetBase64, android.util.Base64.NO_WRAP)
        val signed = signRaw(raw, seed)
        return android.util.Base64.encodeToString(signed, android.util.Base64.NO_WRAP)
    }

    // ── Core signing logic ──────────────────────────────────────────

    private fun signRaw(pset: ByteArray, seed: ByteArray): ByteArray {
        val buf = ByteArrayInputStream(pset)

        val magic = ByteArray(5)
        buf.readExactly(magic)
        require(
            magic[0] == 0x70.toByte() && magic[1] == 0x73.toByte() &&
                magic[2] == 0x65.toByte() && magic[3] == 0x74.toByte() &&
                magic[4] == 0xFF.toByte(),
        ) { "Not a valid PSET" }

        val globalMap = readKeyValueMap(buf)
        val unsignedTx = globalMap.firstOrNull { it.keyType == 0x00 }?.value
            ?: error("PSET missing unsigned transaction")

        val txInfo = parseUnsignedTx(unsignedTx)
        val inputCount = txInfo.inputs.size
        val outputCount = txInfo.outputs.size

        val inputMaps = (0 until inputCount).map { readKeyValueMap(buf) }
        val outputMaps = (0 until outputCount).map { readKeyValueMap(buf) }

        val masterFingerprint = ElectrumSeedUtil.computeMasterFingerprint(seed)

        val hashPrevouts = computeHashPrevouts(txInfo.inputs)
        val hashSequences = computeHashSequences(txInfo.inputs)
        val hashIssuances = computeHashIssuances(txInfo.inputs)
        val hashOutputs = computeHashOutputs(txInfo.outputs)

        val newSigs = mutableMapOf<Int, List<Pair<ByteArray, ByteArray>>>()

        for (i in 0 until inputCount) {
            val map = inputMaps[i]
            val witnessUtxo = map.firstOrNull { it.keyType == 0x01 }?.value ?: continue
            val bip32Entries = map.filter { it.keyType == 0x06 }
            val sighashType = map.firstOrNull { it.keyType == 0x03 }
                ?.value?.let { readUint32LE(it, 0).toInt() }
                ?: SIGHASH_ALL

            val witnessValue = parseWitnessUtxoValue(witnessUtxo)

            val sigs = mutableListOf<Pair<ByteArray, ByteArray>>()
            for (entry in bip32Entries) {
                val pubKey = entry.keyData
                val fp = entry.value.copyOfRange(0, 4).toHex()
                if (fp != masterFingerprint) continue

                val path = parseBip32Path(entry.value)
                val (privKey, _) = deriveKeyAtPath(seed, path)

                val derivedPubKey = ElectrumSeedUtil.publicKeyFromPrivate(privKey)
                if (!derivedPubKey.contentEquals(pubKey)) continue

                val pubKeyHash = ElectrumSeedUtil.hash160(derivedPubKey)
                val scriptCode = buildP2wpkhScriptCode(pubKeyHash)

                val sighash = computeSegwitV0Sighash(
                    version = txInfo.version,
                    hashPrevouts = hashPrevouts,
                    hashSequences = hashSequences,
                    outpoint = txInfo.inputs[i].outpoint,
                    scriptCode = scriptCode,
                    value = witnessValue,
                    sequence = txInfo.inputs[i].sequence,
                    hashIssuances = hashIssuances,
                    hashOutputs = hashOutputs,
                    locktime = txInfo.locktime,
                    sighashType = sighashType,
                )

                val sig = ecdsaSign(privKey, sighash)
                val sigWithHashType = sig + byteArrayOf(sighashType.toByte())
                sigs.add(Pair(pubKey, sigWithHashType))
            }
            if (sigs.isNotEmpty()) newSigs[i] = sigs
        }

        return rebuildPset(magic, globalMap, inputMaps, outputMaps, newSigs)
    }

    // ── PSET binary helpers ─────────────────────────────────────────

    private class KvEntry(
        val keyType: Int,
        val keyData: ByteArray,
        val value: ByteArray,
        val rawKey: ByteArray,
    )

    private fun readKeyValueMap(buf: ByteArrayInputStream): MutableList<KvEntry> {
        val entries = mutableListOf<KvEntry>()
        while (true) {
            val keyLen = readCompactSize(buf)
            if (keyLen == 0L) break
            val rawKey = ByteArray(keyLen.toInt())
            buf.readExactly(rawKey)
            val keyType = rawKey[0].toInt() and 0xFF
            val keyData = if (rawKey.size > 1) rawKey.copyOfRange(1, rawKey.size) else ByteArray(0)
            val valueLen = readCompactSize(buf)
            val value = ByteArray(valueLen.toInt())
            buf.readExactly(value)
            entries.add(KvEntry(keyType, keyData, value, rawKey))
        }
        return entries
    }

    private fun rebuildPset(
        magic: ByteArray,
        globalMap: List<KvEntry>,
        inputMaps: List<MutableList<KvEntry>>,
        outputMaps: List<List<KvEntry>>,
        newSigs: Map<Int, List<Pair<ByteArray, ByteArray>>>,
    ): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(magic)
        writeKeyValueMap(out, globalMap)
        for (i in inputMaps.indices) {
            val map = inputMaps[i]
            val sigs = newSigs[i]
            if (sigs != null) {
                for ((pubKey, sig) in sigs) {
                    val rawKey = ByteArray(1 + pubKey.size)
                    rawKey[0] = 0x02
                    pubKey.copyInto(rawKey, 1)
                    map.add(KvEntry(0x02, pubKey, sig, rawKey))
                }
            }
            writeKeyValueMap(out, map)
        }
        for (map in outputMaps) writeKeyValueMap(out, map)
        return out.toByteArray()
    }

    private fun writeKeyValueMap(out: ByteArrayOutputStream, entries: List<KvEntry>) {
        for (entry in entries) {
            writeCompactSize(out, entry.rawKey.size.toLong())
            out.write(entry.rawKey)
            writeCompactSize(out, entry.value.size.toLong())
            out.write(entry.value)
        }
        out.write(0x00)
    }

    // ── Unsigned transaction parser ─────────────────────────────────

    private class TxInput(
        val outpoint: ByteArray,
        val sequence: ByteArray,
        val hasIssuance: Boolean,
        val issuanceData: ByteArray,
    )

    private class TxOutput(val serialized: ByteArray)
    private class TxInfo(
        val version: ByteArray,
        val locktime: ByteArray,
        val inputs: List<TxInput>,
        val outputs: List<TxOutput>,
    )

    private fun parseUnsignedTx(data: ByteArray): TxInfo {
        val buf = ByteArrayInputStream(data)
        val version = ByteArray(4); buf.readExactly(version)

        var peek = buf.read()
        if (peek == 0x00) {
            buf.read() // skip flag byte
            peek = -1
        }

        val inputCount = if (peek >= 0) {
            readCompactSizeFromFirstByte(peek, buf)
        } else {
            readCompactSize(buf)
        }

        val inputs = mutableListOf<TxInput>()
        repeat(inputCount.toInt()) {
            val txid = ByteArray(32); buf.readExactly(txid)
            val voutBytes = ByteArray(4); buf.readExactly(voutBytes)
            val vout = readUint32LE(voutBytes, 0)
            val outpoint = txid + voutBytes

            val scriptSigLen = readCompactSize(buf)
            if (scriptSigLen > 0) buf.skip(scriptSigLen)

            val seq = ByteArray(4); buf.readExactly(seq)

            val hasIssuance = (vout and 0x80000000u.toLong()) != 0L
            val issuanceData = if (hasIssuance) {
                readIssuanceData(buf)
            } else {
                ByteArray(0)
            }

            inputs.add(TxInput(outpoint, seq, hasIssuance, issuanceData))
        }

        val outputCount = readCompactSize(buf)
        val outputs = mutableListOf<TxOutput>()
        repeat(outputCount.toInt()) {
            outputs.add(TxOutput(readTxOutput(buf)))
        }

        val locktime = ByteArray(4); buf.readExactly(locktime)
        return TxInfo(version, locktime, inputs, outputs)
    }

    private fun readIssuanceData(buf: ByteArrayInputStream): ByteArray {
        val out = ByteArrayOutputStream()
        val nonce = ByteArray(32); buf.readExactly(nonce); out.write(nonce)
        val entropy = ByteArray(32); buf.readExactly(entropy); out.write(entropy)
        readConfValue(buf, out)
        readConfValue(buf, out)
        return out.toByteArray()
    }

    private fun readConfValue(buf: ByteArrayInputStream, out: ByteArrayOutputStream) {
        val prefix = buf.read(); out.write(prefix)
        val len = when (prefix) { 0x00 -> 0; 0x01 -> 8; else -> 32 }
        if (len > 0) { val d = ByteArray(len); buf.readExactly(d); out.write(d) }
    }

    private fun readTxOutput(buf: ByteArrayInputStream): ByteArray {
        val out = ByteArrayOutputStream()
        readConfAsset(buf, out)
        readConfValue(buf, out)
        readConfNonce(buf, out)
        val scriptLen = readCompactSize(buf)
        writeCompactSize(out, scriptLen)
        if (scriptLen > 0) { val s = ByteArray(scriptLen.toInt()); buf.readExactly(s); out.write(s) }
        return out.toByteArray()
    }

    private fun readConfAsset(buf: ByteArrayInputStream, out: ByteArrayOutputStream) {
        val prefix = buf.read(); out.write(prefix)
        val len = when (prefix) { 0x00 -> 0; else -> 32 }
        if (len > 0) { val d = ByteArray(len); buf.readExactly(d); out.write(d) }
    }

    private fun readConfNonce(buf: ByteArrayInputStream, out: ByteArrayOutputStream) {
        val prefix = buf.read(); out.write(prefix)
        val len = when (prefix) { 0x00 -> 0; else -> 32 }
        if (len > 0) { val d = ByteArray(len); buf.readExactly(d); out.write(d) }
    }

    // ── Witness UTXO parsing ────────────────────────────────────────

    private fun parseWitnessUtxoValue(witnessUtxo: ByteArray): ByteArray {
        val buf = ByteArrayInputStream(witnessUtxo)
        // Skip asset (prefix + data)
        val assetPrefix = buf.read()
        buf.skip(if (assetPrefix == 0x00) 0 else 32)
        // Read value (prefix + data)
        val valuePrefix = buf.read()
        val valueDataLen = when (valuePrefix) { 0x00 -> 0; 0x01 -> 8; else -> 32 }
        val result = ByteArray(1 + valueDataLen)
        result[0] = valuePrefix.toByte()
        if (valueDataLen > 0) buf.readExactly(result, 1, valueDataLen)
        return result
    }

    // ── Elements segwit v0 sighash ──────────────────────────────────

    private fun computeHashPrevouts(inputs: List<TxInput>): ByteArray {
        val out = ByteArrayOutputStream()
        for (inp in inputs) out.write(inp.outpoint)
        return sha256d(out.toByteArray())
    }

    private fun computeHashSequences(inputs: List<TxInput>): ByteArray {
        val out = ByteArrayOutputStream()
        for (inp in inputs) out.write(inp.sequence)
        return sha256d(out.toByteArray())
    }

    private fun computeHashIssuances(inputs: List<TxInput>): ByteArray {
        val out = ByteArrayOutputStream()
        for (inp in inputs) {
            if (inp.hasIssuance) {
                out.write(inp.issuanceData)
            } else {
                out.write(ByteArray(64))
                out.write(0x00)
                out.write(0x00)
            }
        }
        return sha256d(out.toByteArray())
    }

    private fun computeHashOutputs(outputs: List<TxOutput>): ByteArray {
        val out = ByteArrayOutputStream()
        for (o in outputs) out.write(o.serialized)
        return sha256d(out.toByteArray())
    }

    private fun computeSegwitV0Sighash(
        version: ByteArray,
        hashPrevouts: ByteArray,
        hashSequences: ByteArray,
        outpoint: ByteArray,
        scriptCode: ByteArray,
        value: ByteArray,
        sequence: ByteArray,
        hashIssuances: ByteArray,
        hashOutputs: ByteArray,
        locktime: ByteArray,
        sighashType: Int,
    ): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(version)              // 4
        out.write(hashPrevouts)         // 32
        out.write(hashSequences)        // 32
        out.write(outpoint)             // 36
        writeCompactSize(out, scriptCode.size.toLong())
        out.write(scriptCode)           // 25 for P2WPKH
        out.write(value)                // 9 or 33
        out.write(sequence)             // 4
        out.write(hashIssuances)        // 32
        out.write(hashOutputs)          // 32
        out.write(locktime)             // 4
        out.write(uint32LE(sighashType.toLong()))  // 4
        return sha256d(out.toByteArray())
    }

    private fun buildP2wpkhScriptCode(pubKeyHash: ByteArray): ByteArray =
        byteArrayOf(
            0x76.toByte(), 0xA9.toByte(), 0x14.toByte(),
        ) + pubKeyHash + byteArrayOf(0x88.toByte(), 0xAC.toByte())

    // ── ECDSA signing with RFC 6979 ─────────────────────────────────

    private fun ecdsaSign(privKey: ByteArray, hash: ByteArray): ByteArray {
        val d = BigInteger(1, privKey)
        val z = BigInteger(1, hash)
        val k = rfc6979(privKey, hash)
        val rPoint = ecMultiplyG(k)
        val r = rPoint.first.mod(CURVE_ORDER)
        require(r > BigInteger.ZERO)
        var s = k.modInverse(CURVE_ORDER).multiply(z.add(r.multiply(d))).mod(CURVE_ORDER)
        if (s > HALF_ORDER) s = CURVE_ORDER.subtract(s)
        return encodeDer(r, s)
    }

    private fun rfc6979(privKey: ByteArray, hash: ByteArray): BigInteger {
        var v = ByteArray(32) { 0x01 }
        var kk = ByteArray(32)
        kk = hmacSha256(kk, v + byteArrayOf(0x00) + privKey + hash)
        v = hmacSha256(kk, v)
        kk = hmacSha256(kk, v + byteArrayOf(0x01) + privKey + hash)
        v = hmacSha256(kk, v)
        while (true) {
            v = hmacSha256(kk, v)
            val candidate = BigInteger(1, v)
            if (candidate >= BigInteger.ONE && candidate < CURVE_ORDER) return candidate
            kk = hmacSha256(kk, v + byteArrayOf(0x00))
            v = hmacSha256(kk, v)
        }
    }

    private fun ecMultiplyG(k: BigInteger): Pair<BigInteger, BigInteger> {
        var rx = BigInteger.ZERO
        var ry = BigInteger.ZERO
        var qx = G_X
        var qy = G_Y
        var scalar = k
        while (scalar > BigInteger.ZERO) {
            if (scalar.testBit(0)) {
                if (rx == BigInteger.ZERO && ry == BigInteger.ZERO) {
                    rx = qx; ry = qy
                } else {
                    val (nx, ny) = ecAdd(rx, ry, qx, qy)
                    rx = nx; ry = ny
                }
            }
            val (nx, ny) = ecDouble(qx, qy)
            qx = nx; qy = ny
            scalar = scalar.shiftRight(1)
        }
        return Pair(rx, ry)
    }

    private fun ecAdd(
        x1: BigInteger, y1: BigInteger,
        x2: BigInteger, y2: BigInteger,
    ): Pair<BigInteger, BigInteger> {
        if (x1 == x2 && y1 == y2) return ecDouble(x1, y1)
        val slope = (y2 - y1) * (x2 - x1).modInverse(FIELD_PRIME) % FIELD_PRIME
        val x3 = (slope * slope - x1 - x2).mod(FIELD_PRIME)
        val y3 = (slope * (x1 - x3) - y1).mod(FIELD_PRIME)
        return Pair(x3, y3)
    }

    private fun ecDouble(x: BigInteger, y: BigInteger): Pair<BigInteger, BigInteger> {
        val three = BigInteger.valueOf(3)
        val two = BigInteger.valueOf(2)
        val slope = (three * x * x) * (two * y).modInverse(FIELD_PRIME) % FIELD_PRIME
        val x3 = (slope * slope - two * x).mod(FIELD_PRIME)
        val y3 = (slope * (x - x3) - y).mod(FIELD_PRIME)
        return Pair(x3, y3)
    }

    private fun encodeDer(r: BigInteger, s: BigInteger): ByteArray {
        val rb = r.toByteArray()
        val sb = s.toByteArray()
        val totalLen = 2 + rb.size + 2 + sb.size
        val out = ByteArrayOutputStream()
        out.write(0x30)
        out.write(totalLen)
        out.write(0x02); out.write(rb.size); out.write(rb)
        out.write(0x02); out.write(sb.size); out.write(sb)
        return out.toByteArray()
    }

    // ── Key derivation ──────────────────────────────────────────────

    private fun deriveKeyAtPath(seed: ByteArray, path: List<Long>): Pair<ByteArray, ByteArray> {
        val (masterKey, masterChainCode) = ElectrumSeedUtil.masterKeyFromSeed(seed)
        var currentKey = masterKey
        var currentChainCode = masterChainCode
        for (segment in path) {
            val (childKey, childChainCode) = if (segment >= 0x80000000L) {
                ElectrumSeedUtil.deriveHardenedChild(currentKey, currentChainCode, segment)
            } else {
                deriveNormalChild(currentKey, currentChainCode, segment)
            }
            currentKey = childKey
            currentChainCode = childChainCode
        }
        return Pair(currentKey, currentChainCode)
    }

    private fun deriveNormalChild(
        parentKey: ByteArray,
        parentChainCode: ByteArray,
        index: Long,
    ): Pair<ByteArray, ByteArray> {
        val parentPub = ElectrumSeedUtil.publicKeyFromPrivate(parentKey)
        val data = ByteArray(37)
        parentPub.copyInto(data, 0)
        data[33] = ((index shr 24) and 0xFF).toByte()
        data[34] = ((index shr 16) and 0xFF).toByte()
        data[35] = ((index shr 8) and 0xFF).toByte()
        data[36] = (index and 0xFF).toByte()

        val i = ElectrumSeedUtil.hmacSha512(parentChainCode, data)
        val il = i.copyOfRange(0, 32)
        val childChainCode = i.copyOfRange(32, 64)
        val parentInt = BigInteger(1, parentKey)
        val ilInt = BigInteger(1, il)
        val childInt = (parentInt + ilInt).mod(CURVE_ORDER)
        val childKey = childInt.toByteArray().let { bytes ->
            when {
                bytes.size == 32 -> bytes
                bytes.size > 32 -> bytes.copyOfRange(bytes.size - 32, bytes.size)
                else -> ByteArray(32 - bytes.size) + bytes
            }
        }
        return Pair(childKey, childChainCode)
    }

    private fun parseBip32Path(data: ByteArray): List<Long> {
        val path = mutableListOf<Long>()
        var pos = 4
        while (pos + 4 <= data.size) {
            path.add(readUint32LE(data, pos))
            pos += 4
        }
        return path
    }

    // ── Compact size encoding ───────────────────────────────────────

    private fun readCompactSize(buf: ByteArrayInputStream): Long {
        val first = buf.read()
        if (first < 0) error("Unexpected end of stream")
        return readCompactSizeFromFirstByte(first, buf)
    }

    private fun readCompactSizeFromFirstByte(first: Int, buf: ByteArrayInputStream): Long =
        when {
            first < 0xFD -> first.toLong()
            first == 0xFD -> {
                val b = ByteArray(2); buf.readExactly(b)
                (b[0].toInt() and 0xFF).toLong() or ((b[1].toInt() and 0xFF).toLong() shl 8)
            }
            first == 0xFE -> {
                val b = ByteArray(4); buf.readExactly(b)
                readUint32LE(b, 0)
            }
            else -> {
                val b = ByteArray(8); buf.readExactly(b)
                readUint64LE(b)
            }
        }

    private fun writeCompactSize(out: ByteArrayOutputStream, value: Long) {
        when {
            value < 0xFD -> out.write(value.toInt())
            value <= 0xFFFF -> {
                out.write(0xFD)
                out.write((value and 0xFF).toInt())
                out.write(((value shr 8) and 0xFF).toInt())
            }
            value <= 0xFFFFFFFFL -> {
                out.write(0xFE)
                out.write(uint32LE(value))
            }
            else -> {
                out.write(0xFF)
                out.write(uint64LE(value))
            }
        }
    }

    // ── Byte helpers ────────────────────────────────────────────────

    private fun readUint32LE(b: ByteArray, off: Int): Long =
        (b[off].toLong() and 0xFF) or
            ((b[off + 1].toLong() and 0xFF) shl 8) or
            ((b[off + 2].toLong() and 0xFF) shl 16) or
            ((b[off + 3].toLong() and 0xFF) shl 24)

    private fun readUint64LE(b: ByteArray): Long =
        readUint32LE(b, 0) or (readUint32LE(b, 4) shl 32)

    private fun uint32LE(v: Long): ByteArray = byteArrayOf(
        (v and 0xFF).toByte(),
        ((v shr 8) and 0xFF).toByte(),
        ((v shr 16) and 0xFF).toByte(),
        ((v shr 24) and 0xFF).toByte(),
    )

    private fun uint64LE(v: Long): ByteArray =
        uint32LE(v and 0xFFFFFFFFL) + uint32LE((v ushr 32) and 0xFFFFFFFFL)

    private fun ByteArrayInputStream.readExactly(b: ByteArray, off: Int = 0, len: Int = b.size) {
        var pos = off
        while (pos < off + len) {
            val n = read(b, pos, off + len - pos)
            if (n < 0) error("Unexpected end of PSET stream")
            pos += n
        }
    }

    private fun sha256(data: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(data)

    private fun sha256d(data: ByteArray): ByteArray = sha256(sha256(data))

    private fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }

    private fun ByteArray.toHex(): String =
        joinToString("") { "%02x".format(it) }
}
