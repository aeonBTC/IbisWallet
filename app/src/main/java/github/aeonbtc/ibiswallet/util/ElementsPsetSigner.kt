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

    // BIP 174 / BIP 370 global types
    private const val PSBT_GLOBAL_UNSIGNED_TX = 0x00
    private const val PSBT_GLOBAL_TX_VERSION = 0x02
    private const val PSBT_GLOBAL_FALLBACK_LOCKTIME = 0x03
    private const val PSBT_GLOBAL_INPUT_COUNT = 0x04
    private const val PSBT_GLOBAL_OUTPUT_COUNT = 0x05

    // BIP 174 / BIP 370 input types
    private const val PSBT_IN_WITNESS_UTXO = 0x01
    private const val PSBT_IN_PARTIAL_SIG = 0x02
    private const val PSBT_IN_SIGHASH = 0x03
    private const val PSBT_IN_BIP32_DERIVATION = 0x06
    private const val PSBT_IN_PREVIOUS_TXID = 0x0e
    private const val PSBT_IN_OUTPUT_INDEX = 0x0f
    private const val PSBT_IN_SEQUENCE = 0x10
    private const val PSBT_IN_REQUIRED_TIME_LOCKTIME = 0x11
    private const val PSBT_IN_REQUIRED_HEIGHT_LOCKTIME = 0x12
    private const val PSBT_IN_PROPRIETARY = 0xFC

    // BIP 174 / BIP 370 output types
    private const val PSBT_OUT_AMOUNT = 0x03
    private const val PSBT_OUT_SCRIPT = 0x04
    private const val PSBT_OUT_PROPRIETARY = 0xFC

    // Elements proprietary input subtypes (prefix "pset")
    private const val PSBT_ELEMENTS_IN_ISSUANCE_VALUE = 0x00
    private const val PSBT_ELEMENTS_IN_ISSUANCE_VALUE_COMMITMENT = 0x01
    private const val PSBT_ELEMENTS_IN_ISSUANCE_INFLATION_KEYS = 0x0a
    private const val PSBT_ELEMENTS_IN_ISSUANCE_INFLATION_KEYS_COMMITMENT = 0x0b
    private const val PSBT_ELEMENTS_IN_ISSUANCE_BLINDING_NONCE = 0x0c
    private const val PSBT_ELEMENTS_IN_ISSUANCE_ASSET_ENTROPY = 0x0d

    // Elements proprietary output subtypes (prefix "pset")
    private const val PSBT_ELEMENTS_OUT_VALUE_COMMITMENT = 0x01
    private const val PSBT_ELEMENTS_OUT_ASSET = 0x02
    private const val PSBT_ELEMENTS_OUT_ASSET_COMMITMENT = 0x03
    private const val PSBT_ELEMENTS_OUT_ECDH_PUBKEY = 0x07

    private val PSET_PROP_PREFIX = byteArrayOf(0x70, 0x73, 0x65, 0x74) // "pset"

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

        // PSBTv0 embeds the full unsigned tx at global key 0x00. Elements PSET
        // (PSBTv2) removes that field — reconstruct tx fields from global + per-
        // input/output maps (BIP 370 / Elements proprietary keys).
        val unsignedTx = globalMap.firstOrNull { it.keyType == PSBT_GLOBAL_UNSIGNED_TX }?.value
        val inputCountFromGlobal = globalMap
            .firstOrNull { it.keyType == PSBT_GLOBAL_INPUT_COUNT }
            ?.value
            ?.let { decodeVarIntValue(it) }
        val outputCountFromGlobal = globalMap
            .firstOrNull { it.keyType == PSBT_GLOBAL_OUTPUT_COUNT }
            ?.value
            ?.let { decodeVarIntValue(it) }

        val (provisionalInputCount, provisionalOutputCount) = when {
            unsignedTx != null -> {
                val parsed = parseUnsignedTx(unsignedTx)
                parsed.inputs.size to parsed.outputs.size
            }
            inputCountFromGlobal != null && outputCountFromGlobal != null -> {
                inputCountFromGlobal.toInt() to outputCountFromGlobal.toInt()
            }
            else -> error("PSET missing unsigned transaction and PSETv2 counts")
        }

        val inputMaps = (0 until provisionalInputCount).map { readKeyValueMap(buf) }
        val outputMaps = (0 until provisionalOutputCount).map { readKeyValueMap(buf) }

        val txInfo = if (unsignedTx != null) {
            parseUnsignedTx(unsignedTx)
        } else {
            reconstructTxInfoFromPsetV2(globalMap, inputMaps, outputMaps)
        }
        val inputCount = txInfo.inputs.size
        val outputCount = txInfo.outputs.size
        require(inputCount == provisionalInputCount && outputCount == provisionalOutputCount) {
            "PSET input/output map count mismatch"
        }

        val masterFingerprint = ElectrumSeedUtil.computeMasterFingerprint(seed)

        val hashPrevouts = computeHashPrevouts(txInfo.inputs)
        val hashSequences = computeHashSequences(txInfo.inputs)
        val hashIssuances = computeHashIssuances(txInfo.inputs)
        val hashOutputs = computeHashOutputs(txInfo.outputs)

        val newSigs = mutableMapOf<Int, List<Pair<ByteArray, ByteArray>>>()

        for (i in 0 until inputCount) {
            val map = inputMaps[i]
            val witnessUtxo = map.firstOrNull { it.keyType == PSBT_IN_WITNESS_UTXO }?.value ?: continue
            val bip32Entries = map.filter { it.keyType == PSBT_IN_BIP32_DERIVATION }
            val sighashType = map.firstOrNull { it.keyType == PSBT_IN_SIGHASH }
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

                val input = txInfo.inputs[i]
                val baseType = sighashType and 0x1f
                val selectedHashOutputs = when {
                    baseType == SIGHASH_NONE -> ByteArray(32)
                    baseType == SIGHASH_SINGLE -> {
                        if (i < txInfo.outputs.size) {
                            sha256d(txInfo.outputs[i].serialized)
                        } else {
                            ByteArray(32)
                        }
                    }
                    else -> hashOutputs
                }
                val sighash = computeSegwitV0Sighash(
                    version = txInfo.version,
                    hashPrevouts = hashPrevouts,
                    hashSequences = hashSequences,
                    hashIssuances = hashIssuances,
                    outpoint = input.outpoint,
                    scriptCode = scriptCode,
                    value = witnessValue,
                    sequence = input.sequence,
                    inputIssuance = if (input.hasIssuance) input.issuanceData else null,
                    hashOutputs = selectedHashOutputs,
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
                    rawKey[0] = PSBT_IN_PARTIAL_SIG.toByte()
                    pubKey.copyInto(rawKey, 1)
                    map.add(KvEntry(PSBT_IN_PARTIAL_SIG, pubKey, sig, rawKey))
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

    internal class TxInput(
        val outpoint: ByteArray,
        val sequence: ByteArray,
        val hasIssuance: Boolean,
        val issuanceData: ByteArray,
    )

    internal class TxOutput(val serialized: ByteArray)
    internal class TxInfo(
        val version: ByteArray,
        val locktime: ByteArray,
        val inputs: List<TxInput>,
        val outputs: List<TxOutput>,
    )

    /**
     * Rebuild the unsigned-tx fields needed for Elements segwit-v0 sighash from a
     * PSETv2 (no global PSBT_GLOBAL_UNSIGNED_TX).
     */
    private fun reconstructTxInfoFromPsetV2(
        globalMap: List<KvEntry>,
        inputMaps: List<List<KvEntry>>,
        outputMaps: List<List<KvEntry>>,
    ): TxInfo {
        val versionBytes = globalMap.firstOrNull { it.keyType == PSBT_GLOBAL_TX_VERSION }?.value
            ?: error("PSETv2 missing global tx version")
        require(versionBytes.size == 4) { "PSETv2 global tx version must be 4 bytes" }

        val fallbackLocktime = globalMap
            .firstOrNull { it.keyType == PSBT_GLOBAL_FALLBACK_LOCKTIME }
            ?.value
            ?.also { require(it.size == 4) { "PSETv2 fallback locktime must be 4 bytes" } }

        val locktime = computePsetV2Locktime(inputMaps, fallbackLocktime)

        val inputs = inputMaps.map { map -> reconstructInput(map) }
        val outputs = outputMaps.map { map -> TxOutput(reconstructOutputSerialized(map)) }

        return TxInfo(versionBytes, locktime, inputs, outputs)
    }

    /**
     * BIP 370 locktime resolution (same rules as rust-elements `PSET::locktime`):
     * unconstrained/unconstrained → fallback (or 0);
     * time Minimum (any height) → max time;
     * height Minimum → max height;
     * both Disallowed → conflict.
     */
    private fun computePsetV2Locktime(
        inputMaps: List<List<KvEntry>>,
        fallbackLocktime: ByteArray?,
    ): ByteArray {
        // null=Unconstrained, -1=Disallowed, else Minimum(value)
        var timeState: Long? = null
        var heightState: Long? = null
        var timeDisallowed = false
        var heightDisallowed = false

        for (map in inputMaps) {
            val timeLt = map.firstOrNull { it.keyType == PSBT_IN_REQUIRED_TIME_LOCKTIME }
                ?.value?.let {
                    require(it.size == 4) { "time locktime must be 4 bytes" }
                    readUint32LE(it, 0)
                }
            val heightLt = map.firstOrNull { it.keyType == PSBT_IN_REQUIRED_HEIGHT_LOCKTIME }
                ?.value?.let {
                    require(it.size == 4) { "height locktime must be 4 bytes" }
                    readUint32LE(it, 0)
                }

            when {
                timeLt != null && heightLt != null -> {
                    timeState = maxOf(timeState ?: 0L, timeLt)
                    heightState = maxOf(heightState ?: 0L, heightLt)
                }
                timeLt != null -> {
                    timeState = maxOf(timeState ?: 0L, timeLt)
                    heightDisallowed = true
                }
                heightLt != null -> {
                    heightState = maxOf(heightState ?: 0L, heightLt)
                    timeDisallowed = true
                }
            }
        }

        return when {
            !timeDisallowed && timeState != null -> uint32LE(timeState)
            !heightDisallowed && heightState != null -> uint32LE(heightState)
            timeDisallowed && heightDisallowed -> error("PSETv2 locktime conflict")
            else -> fallbackLocktime ?: uint32LE(0)
        }
    }

    private fun reconstructInput(map: List<KvEntry>): TxInput {
        val txid = map.firstOrNull { it.keyType == PSBT_IN_PREVIOUS_TXID }?.value
            ?: error("PSETv2 input missing previous txid")
        require(txid.size == 32) { "previous txid must be 32 bytes" }

        val voutBytes = map.firstOrNull { it.keyType == PSBT_IN_OUTPUT_INDEX }?.value
            ?: error("PSETv2 input missing output index")
        require(voutBytes.size == 4) { "output index must be 4 bytes" }
        val rawVout = readUint32LE(voutBytes, 0)

        // Outpoint used in hashPrevouts / sighash uses flags-stripped vout (bits 30/31 cleared),
        // except the full coinbase marker 0xffffffff. Matches extract_tx in rust-elements.
        val outpointVout = if (rawVout == 0xFFFF_FFFFL) {
            rawVout
        } else {
            rawVout and 0x3FFF_FFFFL
        }
        val outpoint = txid + uint32LE(outpointVout)

        val sequence = map.firstOrNull { it.keyType == PSBT_IN_SEQUENCE }?.value
            ?: uint32LE(0xFFFF_FFFFL)
        require(sequence.size == 4) { "sequence must be 4 bytes" }

        val flaggedIssuance = (rawVout != 0xFFFF_FFFFL) && (rawVout and 0x8000_0000L) != 0L
        val issuanceData = if (flaggedIssuance) {
            reconstructIssuanceData(map)
        } else {
            ByteArray(0)
        }
        val hasIssuance = flaggedIssuance && !isNullIssuance(issuanceData)

        return TxInput(
            outpoint,
            sequence,
            hasIssuance,
            if (hasIssuance) issuanceData else ByteArray(0),
        )
    }

    private fun reconstructIssuanceData(map: List<KvEntry>): ByteArray {
        val out = ByteArrayOutputStream()
        val nonce = findElementsProp(map, PSBT_IN_PROPRIETARY, PSBT_ELEMENTS_IN_ISSUANCE_BLINDING_NONCE)
            ?: ByteArray(32)
        require(nonce.size == 32) { "issuance blinding nonce must be 32 bytes" }
        out.write(nonce)

        val entropy = findElementsProp(map, PSBT_IN_PROPRIETARY, PSBT_ELEMENTS_IN_ISSUANCE_ASSET_ENTROPY)
            ?: ByteArray(32)
        require(entropy.size == 32) { "issuance asset entropy must be 32 bytes" }
        out.write(entropy)

        writeConfidentialValueFromPset(
            out = out,
            explicit = findElementsProp(map, PSBT_IN_PROPRIETARY, PSBT_ELEMENTS_IN_ISSUANCE_VALUE),
            commitment = findElementsProp(
                map,
                PSBT_IN_PROPRIETARY,
                PSBT_ELEMENTS_IN_ISSUANCE_VALUE_COMMITMENT,
            ),
        )
        writeConfidentialValueFromPset(
            out = out,
            explicit = findElementsProp(map, PSBT_IN_PROPRIETARY, PSBT_ELEMENTS_IN_ISSUANCE_INFLATION_KEYS),
            commitment = findElementsProp(
                map,
                PSBT_IN_PROPRIETARY,
                PSBT_ELEMENTS_IN_ISSUANCE_INFLATION_KEYS_COMMITMENT,
            ),
        )
        return out.toByteArray()
    }

    private fun reconstructOutputSerialized(map: List<KvEntry>): ByteArray {
        val out = ByteArrayOutputStream()

        val assetCommitment = findElementsProp(map, PSBT_OUT_PROPRIETARY, PSBT_ELEMENTS_OUT_ASSET_COMMITMENT)
        val assetExplicit = findElementsProp(map, PSBT_OUT_PROPRIETARY, PSBT_ELEMENTS_OUT_ASSET)
        when {
            assetCommitment != null -> {
                require(assetCommitment.size == 33) { "asset commitment must be 33 bytes" }
                out.write(assetCommitment)
            }
            assetExplicit != null -> {
                require(assetExplicit.size == 32) { "explicit asset must be 32 bytes" }
                out.write(0x01)
                out.write(assetExplicit)
            }
            else -> error("PSETv2 output missing asset")
        }

        val valueCommitment = findElementsProp(map, PSBT_OUT_PROPRIETARY, PSBT_ELEMENTS_OUT_VALUE_COMMITMENT)
        val amountExplicit = map.firstOrNull { it.keyType == PSBT_OUT_AMOUNT }?.value
        when {
            valueCommitment != null -> {
                require(valueCommitment.size == 33) { "value commitment must be 33 bytes" }
                out.write(valueCommitment)
            }
            amountExplicit != null -> {
                require(amountExplicit.size == 8) { "explicit amount must be 8 bytes" }
                out.write(0x01)
                // Confidential explicit values are big-endian u64; PSET stores little-endian.
                out.write(amountExplicit.reversedArray())
            }
            else -> error("PSETv2 output missing amount")
        }

        val ecdh = findElementsProp(map, PSBT_OUT_PROPRIETARY, PSBT_ELEMENTS_OUT_ECDH_PUBKEY)
        if (ecdh != null) {
            require(ecdh.size == 33) { "ECDH pubkey must be 33 bytes" }
            out.write(ecdh)
        } else {
            out.write(0x00)
        }

        val script = map.firstOrNull { it.keyType == PSBT_OUT_SCRIPT }?.value
            ?: error("PSETv2 output missing script")
        writeCompactSize(out, script.size.toLong())
        out.write(script)

        return out.toByteArray()
    }

    private fun writeConfidentialValueFromPset(
        out: ByteArrayOutputStream,
        explicit: ByteArray?,
        commitment: ByteArray?,
    ) {
        when {
            commitment != null -> {
                require(commitment.size == 33) { "value commitment must be 33 bytes" }
                out.write(commitment)
            }
            explicit != null -> {
                require(explicit.size == 8) { "explicit value must be 8 bytes" }
                out.write(0x01)
                out.write(explicit.reversedArray())
            }
            else -> out.write(0x00)
        }
    }

    /**
     * Elements proprietary keys: type 0xFC, key = compactSize("pset") || subtype || rest.
     */
    private fun findElementsProp(map: List<KvEntry>, type: Int, subtype: Int): ByteArray? {
        for (entry in map) {
            if (entry.keyType != type) continue
            val keyData = entry.keyData
            if (keyData.size < 6) continue
            // compactSize-prefixed "pset" (length 4)
            if (keyData[0].toInt() and 0xFF != 4) continue
            if (!keyData.copyOfRange(1, 5).contentEquals(PSET_PROP_PREFIX)) continue
            if (keyData[5].toInt() and 0xFF != subtype) continue
            return entry.value
        }
        return null
    }

    private fun decodeVarIntValue(value: ByteArray): Long {
        val buf = ByteArrayInputStream(value)
        val n = readCompactSize(buf)
        require(buf.available() == 0) { "trailing bytes in compact size value" }
        return n
    }

    /**
     * Parse an Elements transaction serialization for sighash purposes.
     *
     * Elements wire format (unlike Bitcoin): always `version || wit_flag(0|1) ||
     * inputs || outputs || locktime [|| witnesses]`. There is no separate 0x00
     * marker before the flag. Vout high bits encode pegin (bit 30) / issuance
     * (bit 31); the outpoint used in BIP143 hashes drops those bits.
     */
    internal fun parseUnsignedTx(data: ByteArray): TxInfo {
        val buf = ByteArrayInputStream(data)
        val version = ByteArray(4); buf.readExactly(version)

        val witFlag = buf.read()
        require(witFlag == 0 || witFlag == 1) {
            "Invalid Elements witness flag: $witFlag"
        }

        val inputCount = readCompactSize(buf)
        val inputs = mutableListOf<TxInput>()
        repeat(inputCount.toInt()) {
            val txid = ByteArray(32); buf.readExactly(txid)
            val voutBytes = ByteArray(4); buf.readExactly(voutBytes)
            val rawVout = readUint32LE(voutBytes, 0)

            // Coinbase (0xffffffff) keeps full value; otherwise strip pegin/issuance flag bits.
            val cleanVout = if (rawVout == 0xFFFF_FFFFL) {
                rawVout
            } else {
                rawVout and 0x3FFF_FFFFL
            }
            val outpoint = txid + uint32LE(cleanVout)

            val scriptSigLen = readCompactSize(buf)
            if (scriptSigLen > 0) buf.skip(scriptSigLen)

            val seq = ByteArray(4); buf.readExactly(seq)

            val flaggedIssuance = (rawVout != 0xFFFF_FFFFL) && (rawVout and 0x8000_0000L) != 0L
            val issuanceData = if (flaggedIssuance) {
                readIssuanceData(buf)
            } else {
                ByteArray(0)
            }
            // rust-elements: has_issuance() is !issuance.is_null() (null amount AND null inflation keys).
            val hasIssuance = flaggedIssuance && !isNullIssuance(issuanceData)

            inputs.add(TxInput(outpoint, seq, hasIssuance, if (hasIssuance) issuanceData else ByteArray(0)))
        }

        val outputCount = readCompactSize(buf)
        val outputs = mutableListOf<TxOutput>()
        repeat(outputCount.toInt()) {
            outputs.add(TxOutput(readTxOutput(buf)))
        }

        val locktime = ByteArray(4); buf.readExactly(locktime)
        // Witnesses ignored for sighash (fields already collected).
        return TxInfo(version, locktime, inputs, outputs)
    }

    /** Null AssetIssuance: both amount and inflation_keys are Null (prefix 0x00). */
    private fun isNullIssuance(issuanceData: ByteArray): Boolean {
        if (issuanceData.size < 66) return true
        // nonce(32) + entropy(32) + amount_prefix + inflation_prefix
        return issuanceData[64] == 0.toByte() && issuanceData[65] == 0.toByte()
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

    internal fun computeHashPrevouts(inputs: List<TxInput>): ByteArray {
        val out = ByteArrayOutputStream()
        for (inp in inputs) out.write(inp.outpoint)
        return sha256d(out.toByteArray())
    }

    internal fun computeHashSequences(inputs: List<TxInput>): ByteArray {
        val out = ByteArrayOutputStream()
        for (inp in inputs) out.write(inp.sequence)
        return sha256d(out.toByteArray())
    }

    /**
     * Elements BIP143 issuances prefimage: each input contributes either its
     * full `AssetIssuance` serialization, or a single 0x00 when absent
     * (rust-elements `common_cache.issuances`, not a null issuance struct).
     */
    internal fun computeHashIssuances(inputs: List<TxInput>): ByteArray {
        val out = ByteArrayOutputStream()
        for (inp in inputs) {
            if (inp.hasIssuance) {
                out.write(inp.issuanceData)
            } else {
                out.write(0x00)
            }
        }
        return sha256d(out.toByteArray())
    }

    internal fun computeHashOutputs(outputs: List<TxOutput>): ByteArray {
        val out = ByteArrayOutputStream()
        for (o in outputs) out.write(o.serialized)
        return sha256d(out.toByteArray())
    }

    private const val SIGHASH_NONE = 2
    private const val SIGHASH_SINGLE = 3
    private const val SIGHASH_ANYONECANPAY = 0x80

    /**
     * Elements BIP143 / segwit-v0 signing preimage
     * (`encode_segwitv0_signing_data_to` in rust-elements).
     */
    internal fun computeSegwitV0Sighash(
        version: ByteArray,
        hashPrevouts: ByteArray,
        hashSequences: ByteArray,
        hashIssuances: ByteArray,
        outpoint: ByteArray,
        scriptCode: ByteArray,
        value: ByteArray,
        sequence: ByteArray,
        inputIssuance: ByteArray?,
        hashOutputs: ByteArray,
        locktime: ByteArray,
        sighashType: Int,
    ): ByteArray {
        val baseType = sighashType and 0x1f
        val anyoneCanPay = (sighashType and SIGHASH_ANYONECANPAY) != 0
        val zeroHash = ByteArray(32)

        val out = ByteArrayOutputStream()
        out.write(version)

        // hashPrevouts — zero if ANYONECANPAY
        out.write(if (anyoneCanPay) zeroHash else hashPrevouts)

        // hashSequences — zero if ANYONECANPAY or SINGLE or NONE
        val includeSequences = !anyoneCanPay && baseType != SIGHASH_SINGLE && baseType != SIGHASH_NONE
        out.write(if (includeSequences) hashSequences else zeroHash)

        // hashIssuances (Elements) — zero if ANYONECANPAY
        out.write(if (anyoneCanPay) zeroHash else hashIssuances)

        // input-specific
        out.write(outpoint)
        writeCompactSize(out, scriptCode.size.toLong())
        out.write(scriptCode)
        out.write(value)
        out.write(sequence)
        if (inputIssuance != null) {
            out.write(inputIssuance)
        }

        // hashOutputs — for SINGLE/NONE the caller supplies the preimage hash
        // (single-output hash or zero). For ALL it is hash of all outputs.
        out.write(hashOutputs)

        out.write(locktime)
        out.write(uint32LE(sighashType.toLong()))
        return sha256d(out.toByteArray())
    }

    /**
     * Compute the Elements segwit-v0 sighash for a fully-serialized unsigned
     * Elements transaction (no flag/witness). Used by regression tests that
     * port rust-elements `test_segwit_sighash` vectors.
     */
    internal fun computeSegwitV0SighashFromTx(
        txBytes: ByteArray,
        inputIndex: Int,
        scriptCode: ByteArray,
        value: ByteArray,
        sighashType: Int,
    ): ByteArray {
        val txInfo = parseUnsignedTx(txBytes)
        require(inputIndex in txInfo.inputs.indices) { "input index out of range" }
        val baseType = sighashType and 0x1f
        val hashPrevouts = computeHashPrevouts(txInfo.inputs)
        val hashSequences = computeHashSequences(txInfo.inputs)
        val hashIssuances = computeHashIssuances(txInfo.inputs)
        val hashOutputs = when {
            baseType == SIGHASH_NONE -> ByteArray(32)
            baseType == SIGHASH_SINGLE -> {
                if (inputIndex < txInfo.outputs.size) {
                    sha256d(txInfo.outputs[inputIndex].serialized)
                } else {
                    ByteArray(32)
                }
            }
            else -> computeHashOutputs(txInfo.outputs)
        }
        val input = txInfo.inputs[inputIndex]
        return computeSegwitV0Sighash(
            version = txInfo.version,
            hashPrevouts = hashPrevouts,
            hashSequences = hashSequences,
            hashIssuances = hashIssuances,
            outpoint = input.outpoint,
            scriptCode = scriptCode,
            value = value,
            sequence = input.sequence,
            inputIssuance = if (input.hasIssuance) input.issuanceData else null,
            hashOutputs = hashOutputs,
            locktime = txInfo.locktime,
            sighashType = sighashType,
        )
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
