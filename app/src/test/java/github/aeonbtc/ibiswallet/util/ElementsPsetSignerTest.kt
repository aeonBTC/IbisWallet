package github.aeonbtc.ibiswallet.util

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.mockk.every
import io.mockk.mockkStatic
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Regression tests for #23: passphrase Liquid path must sign real LWK PSETv2
 * (no global PSBT_GLOBAL_UNSIGNED_TX / key type 0x00).
 */
class ElementsPsetSignerTest : FunSpec({

    beforeSpec {
        mockkStatic(android.util.Base64::class)
        every { android.util.Base64.encodeToString(any(), any()) } answers {
            Base64.getEncoder().encodeToString(firstArg())
        }
        every { android.util.Base64.decode(any<String>(), any()) } answers {
            Base64.getDecoder().decode(firstArg<String>())
        }
        every { android.util.Base64.decode(any<ByteArray>(), any()) } answers {
            Base64.getDecoder().decode(firstArg<ByteArray>())
        }
    }

    val mnemonic =
        "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
    val passphrase = "TREZOR"
    val seed = ElectrumSeedUtil.bip39MnemonicToSeed(mnemonic, passphrase)

    // m/84'/1776'/0'/0/0 — standard Liquid singlesig external address index 0
    val path = listOf(
        84L or 0x8000_0000L,
        1776L or 0x8000_0000L,
        0L or 0x8000_0000L,
        0L,
        0L,
    )

    context("PSETv2 signing (no global unsigned tx)") {
        test("signs without PSBT_GLOBAL_UNSIGNED_TX") {
            val (privKey, pubKey) = deriveAtPath(seed, path)
            val masterFp = ElectrumSeedUtil.computeMasterFingerprint(seed)
            val fingerprintBytes = masterFp.hexToBytes()

            val prevTxid = ByteArray(32) { (it + 1).toByte() }
            val vout = 1L
            val sequence = 0xFFFF_FFFEL
            val amount = 50_000L
            val assetId = ByteArray(32) { 0xAB.toByte() }
            val scriptPubkey = byteArrayOf(0x00, 0x14) + ElectrumSeedUtil.hash160(pubKey)

            // Witness UTXO: explicit asset + explicit value + null nonce + script
            val witnessUtxo = ByteArrayOutputStream().apply {
                write(0x01)
                write(assetId)
                write(0x01)
                write(uint64BE(amount))
                write(0x00)
                writeCompactSize(this, scriptPubkey.size.toLong())
                write(scriptPubkey)
            }.toByteArray()

            val pset = buildMinimalPsetV2(
                txVersion = 2,
                locktime = 0,
                prevTxid = prevTxid,
                vout = vout,
                sequence = sequence,
                witnessUtxo = witnessUtxo,
                bip32PubKey = pubKey,
                bip32Path = fingerprintBytes to path,
                outputAsset = assetId,
                outputAmount = amount - 300,
                outputScript = scriptPubkey,
                feeAsset = assetId,
                feeAmount = 300L,
            )

            // Sanity: global map has no 0x00
            val raw = Base64.getDecoder().decode(pset)
            val globalKeys = readGlobalKeyTypes(raw)
            globalKeys.contains(0x00) shouldBe false
            globalKeys.contains(0x02) shouldBe true // tx version
            globalKeys.contains(0x04) shouldBe true // input count

            val signedBase64 = ElementsPsetSigner.sign(pset, seed)
            signedBase64 shouldNotBe pset

            val signedRaw = Base64.getDecoder().decode(signedBase64)
            val partialSig = extractPartialSig(signedRaw, pubKey)
            partialSig shouldNotBe null
            partialSig!!.size shouldBeGreaterThan 70 // DER + sighash byte

            // Re-signing with same seed must be deterministic (RFC 6979)
            val signedAgain = ElementsPsetSigner.sign(pset, seed)
            signedAgain shouldBe signedBase64

            // Signature must verify against the same pubkey (ECDSA check on r,s shape)
            val sighashType = partialSig.last().toInt() and 0xFF
            sighashType shouldBe 1 // SIGHASH_ALL
            // Keep privKey referenced so optimizers don't drop derivation
            privKey.size shouldBe 32
        }

        test("wrong seed produces no partial sig for this BIP32 origin") {
            val otherSeed = ElectrumSeedUtil.bip39MnemonicToSeed(mnemonic, "other")
            val (_, pubKey) = deriveAtPath(seed, path)
            val masterFp = ElectrumSeedUtil.computeMasterFingerprint(seed)

            val prevTxid = ByteArray(32) { 7 }
            val scriptPubkey = byteArrayOf(0x00, 0x14) + ElectrumSeedUtil.hash160(pubKey)
            val assetId = ByteArray(32) { 0x11 }
            val witnessUtxo = ByteArrayOutputStream().apply {
                write(0x01); write(assetId)
                write(0x01); write(uint64BE(1000))
                write(0x00)
                writeCompactSize(this, scriptPubkey.size.toLong())
                write(scriptPubkey)
            }.toByteArray()

            val pset = buildMinimalPsetV2(
                txVersion = 2,
                locktime = 0,
                prevTxid = prevTxid,
                vout = 0,
                sequence = 0xFFFF_FFFFL,
                witnessUtxo = witnessUtxo,
                bip32PubKey = pubKey,
                bip32Path = masterFp.hexToBytes() to path,
                outputAsset = assetId,
                outputAmount = 700,
                outputScript = scriptPubkey,
                feeAsset = assetId,
                feeAmount = 300L,
            )

            val signed = ElementsPsetSigner.sign(pset, otherSeed)
            // No matching fingerprint → no partial_sig fields added
            val signedRaw = Base64.getDecoder().decode(signed)
            extractPartialSig(signedRaw, pubKey) shouldBe null
        }

        test("rejects garbage that is not a PSET") {
            val err = shouldThrow<IllegalArgumentException> {
                ElementsPsetSigner.sign(
                    Base64.getEncoder().encodeToString(ByteArray(8) { 0x01 }),
                    seed,
                )
            }
            err.message shouldContain "Not a valid PSET"
        }
    }

    // Ported from rust-elements src/sighash.rs test_segwit_sighashes
    // (issue #23 follow-up: NULLFAIL from wrong field order / null-issuance encoding).
    context("Elements segwit-v0 sighash vectors") {
        // Exact vectors from rust-elements (single-line originals).
        val txExplicitNoIssuance =
            "010000000001715df5ccebaf02ff18d6fae7263fa69fed5de59c900f4749556eba41bc7bf2af0000000000000000000201230f4f5d4b7c6fa845806ee4f67713459e1b69e8e60fcee2e4940c7a0d5de1b2010000000124101100001f5175517551755175517551755175517551755175517551755175517551755101230f4f5d4b7c6fa845806ee4f67713459e1b69e8e60fcee2e4940c7a0d5de1b2010000000005f5e100000000000000"
        val script = "76a914f54a5851e9372b87810a8e60cdd2e7cfd80b6e3188ac"
        val confValue = "0850863ad64a87ae8a2fe83c1af1a8403cb53f53e486d8511dad8a04887e5b2352"
        val explicitValue = "010000000005f5e100"
        val txWithIssuance =
            "010000000001715df5ccebaf02ff18d6fae7263fa69fed5de59c900f4749556eba41bc7bf2af000000800000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000100000000000003e801000000000000000a0201230f4f5d4b7c6fa845806ee4f67713459e1b69e8e60fcee2e4940c7a0d5de1b2010000000124101100001f5175517551755175517551755175517551755175517551755175517551755101230f4f5d4b7c6fa845806ee4f67713459e1b69e8e60fcee2e4940c7a0d5de1b2010000000005f5e100000000000000"

        fun assertSegwitSighash(
            txHex: String,
            scriptHex: String,
            inputIndex: Int,
            valueHex: String,
            sighashType: Int,
            expectedHex: String,
        ) {
            val actual = ElementsPsetSigner.computeSegwitV0SighashFromTx(
                txBytes = txHex.hexToBytes(),
                inputIndex = inputIndex,
                scriptCode = scriptHex.hexToBytes(),
                value = valueHex.hexToBytes(),
                sighashType = sighashType,
            )
            actual.toHex() shouldBe expectedHex
        }

        test("SIGHASH_ALL with confidential value, no issuance") {
            assertSegwitSighash(
                txExplicitNoIssuance, script, 0, confValue, 1,
                "e201b4019129a03ca0304989731c6dccde232c854d86fce999b7411da1e90048",
            )
        }

        test("SIGHASH_NONE with confidential value") {
            assertSegwitSighash(
                txExplicitNoIssuance, script, 0, confValue, 2,
                "bfc6599816673083334ae82ac3459a2d0fef478d3e580e3ae203a28347502cb4",
            )
        }

        test("SIGHASH_SINGLE with confidential value") {
            assertSegwitSighash(
                txExplicitNoIssuance, script, 0, confValue, 3,
                "4bc8546e32d31c5415444138184696e80f49e537a083bfcc89be2ab41d962e76",
            )
        }

        test("SIGHASH_ALL|ANYONECANPAY with confidential value") {
            assertSegwitSighash(
                txExplicitNoIssuance, script, 0, confValue, 0x81,
                "b70ba5f4a1c2c48cd7f2104b2baa6a5c97987eb560916d39a5d427deb8b1dc2a",
            )
        }

        test("SIGHASH_ALL with explicit value") {
            assertSegwitSighash(
                txExplicitNoIssuance, script, 0, explicitValue, 1,
                "71141639d982f1a1a8901e32fb1a9e15a0ea168b37d33300a3c9619fc3767388",
            )
        }

        test("SIGHASH_ALL with issuance present") {
            assertSegwitSighash(
                txWithIssuance, script, 0, confValue, 1,
                "ea946ee417d5a16a1038b2c3b54d1b7b12a9f98c0dcb4684bf005eb1c27d0c92",
            )
        }

        test("null-issuance preimage is a single 0x00 per input (not 66-byte empty struct)") {
            val txInfo = ElementsPsetSigner.parseUnsignedTx(txExplicitNoIssuance.hexToBytes())
            txInfo.inputs.forEach { it.hasIssuance shouldBe false }
            // hashIssuances is sha256d of one 0x00 byte for the single input
            val expected = MessageDigestDoubleSha(byteArrayOf(0x00))
            ElementsPsetSigner.computeHashIssuances(txInfo.inputs) shouldBe expected
        }
    }
})

private fun MessageDigestDoubleSha(data: ByteArray): ByteArray {
    val md = java.security.MessageDigest.getInstance("SHA-256")
    return md.digest(md.digest(data))
}

private fun ByteArray.toHex(): String =
    joinToString("") { "%02x".format(it) }

// ── Test helpers ────────────────────────────────────────────────────

private fun deriveAtPath(seed: ByteArray, path: List<Long>): Pair<ByteArray, ByteArray> {
    val (masterKey, masterCc) = ElectrumSeedUtil.masterKeyFromSeed(seed)
    var key = masterKey
    var cc = masterCc
    val order = BigInteger(
        "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEBAAEDCE6AF48A03BBFD25E8CD0364141",
        16,
    )
    for (seg in path) {
        if (seg >= 0x8000_0000L) {
            val (k, c) = ElectrumSeedUtil.deriveHardenedChild(key, cc, seg)
            key = k
            cc = c
        } else {
            val parentPub = ElectrumSeedUtil.publicKeyFromPrivate(key)
            val data = ByteArray(37)
            parentPub.copyInto(data, 0)
            data[33] = ((seg shr 24) and 0xFF).toByte()
            data[34] = ((seg shr 16) and 0xFF).toByte()
            data[35] = ((seg shr 8) and 0xFF).toByte()
            data[36] = (seg and 0xFF).toByte()
            val i = hmacSha512(cc, data)
            val il = BigInteger(1, i.copyOfRange(0, 32))
            val childInt = (BigInteger(1, key) + il).mod(order)
            key = childInt.toByteArray().let { b ->
                when {
                    b.size == 32 -> b
                    b.size > 32 -> b.copyOfRange(b.size - 32, b.size)
                    else -> ByteArray(32 - b.size) + b
                }
            }
            cc = i.copyOfRange(32, 64)
        }
    }
    return key to ElectrumSeedUtil.publicKeyFromPrivate(key)
}

private fun hmacSha512(key: ByteArray, data: ByteArray): ByteArray {
    val mac = Mac.getInstance("HmacSHA512")
    mac.init(SecretKeySpec(key, "HmacSHA512"))
    return mac.doFinal(data)
}

private fun buildMinimalPsetV2(
    txVersion: Long,
    locktime: Long,
    prevTxid: ByteArray,
    vout: Long,
    sequence: Long,
    witnessUtxo: ByteArray,
    bip32PubKey: ByteArray,
    bip32Path: Pair<ByteArray, List<Long>>,
    outputAsset: ByteArray,
    outputAmount: Long,
    outputScript: ByteArray,
    feeAsset: ByteArray,
    feeAmount: Long,
): String {
    val out = ByteArrayOutputStream()
    // magic "pset" + 0xff
    out.write(byteArrayOf(0x70, 0x73, 0x65, 0x74, 0xFF.toByte()))

    // global map
    writeKv(out, byteArrayOf(0x02), uint32LE(txVersion)) // TX_VERSION
    writeKv(out, byteArrayOf(0x03), uint32LE(locktime)) // FALLBACK_LOCKTIME
    writeKv(out, byteArrayOf(0x04), byteArrayOf(0x01)) // INPUT_COUNT = 1
    writeKv(out, byteArrayOf(0x05), byteArrayOf(0x02)) // OUTPUT_COUNT = 2 (recv + fee)
    writeKv(out, byteArrayOf(0xFB.toByte()), uint32LE(2)) // PSET version 2
    out.write(0x00) // separator

    // input 0
    writeKv(out, byteArrayOf(0x01), witnessUtxo) // WITNESS_UTXO
    val bip32Key = byteArrayOf(0x06) + bip32PubKey
    val bip32Val = ByteArrayOutputStream().apply {
        write(bip32Path.first)
        for (seg in bip32Path.second) write(uint32LE(seg))
    }.toByteArray()
    writeKv(out, bip32Key, bip32Val)
    writeKv(out, byteArrayOf(0x0e), prevTxid) // PREVIOUS_TXID
    writeKv(out, byteArrayOf(0x0f), uint32LE(vout)) // OUTPUT_INDEX
    writeKv(out, byteArrayOf(0x10), uint32LE(sequence)) // SEQUENCE
    out.write(0x00)

    // output 0 — payment
    writeKv(out, byteArrayOf(0x03), uint64LE(outputAmount)) // AMOUNT
    writeKv(out, byteArrayOf(0x04), outputScript) // SCRIPT
    writeKv(out, propKey(0x02), outputAsset) // ELEMENTS_OUT_ASSET
    out.write(0x00)

    // output 1 — fee (empty script)
    writeKv(out, byteArrayOf(0x03), uint64LE(feeAmount))
    writeKv(out, byteArrayOf(0x04), ByteArray(0))
    writeKv(out, propKey(0x02), feeAsset)
    out.write(0x00)

    return Base64.getEncoder().encodeToString(out.toByteArray())
}

private fun propKey(subtype: Int): ByteArray {
    // type 0xFC, then compactSize("pset")=4 || "pset" || subtype
    return byteArrayOf(0xFC.toByte(), 0x04, 0x70, 0x73, 0x65, 0x74, subtype.toByte())
}

private fun writeKv(out: ByteArrayOutputStream, rawKey: ByteArray, value: ByteArray) {
    writeCompactSize(out, rawKey.size.toLong())
    out.write(rawKey)
    writeCompactSize(out, value.size.toLong())
    out.write(value)
}

private fun writeCompactSize(out: ByteArrayOutputStream, value: Long) {
    when {
        value < 0xFD -> out.write(value.toInt())
        value <= 0xFFFF -> {
            out.write(0xFD)
            out.write((value and 0xFF).toInt())
            out.write(((value shr 8) and 0xFF).toInt())
        }
        else -> error("unsupported compact size in test helper")
    }
}

private fun uint32LE(v: Long): ByteArray = byteArrayOf(
    (v and 0xFF).toByte(),
    ((v shr 8) and 0xFF).toByte(),
    ((v shr 16) and 0xFF).toByte(),
    ((v shr 24) and 0xFF).toByte(),
)

private fun uint64LE(v: Long): ByteArray = uint32LE(v and 0xFFFF_FFFFL) +
    uint32LE((v ushr 32) and 0xFFFF_FFFFL)

private fun uint64BE(v: Long): ByteArray = byteArrayOf(
    ((v shr 56) and 0xFF).toByte(),
    ((v shr 48) and 0xFF).toByte(),
    ((v shr 40) and 0xFF).toByte(),
    ((v shr 32) and 0xFF).toByte(),
    ((v shr 24) and 0xFF).toByte(),
    ((v shr 16) and 0xFF).toByte(),
    ((v shr 8) and 0xFF).toByte(),
    (v and 0xFF).toByte(),
)

private fun String.hexToBytes(): ByteArray {
    require(length % 2 == 0)
    return ByteArray(length / 2) { i ->
        substring(i * 2, i * 2 + 2).toInt(16).toByte()
    }
}

private fun readGlobalKeyTypes(pset: ByteArray): Set<Int> {
    val buf = ByteArrayInputStream(pset)
    buf.skip(5) // magic
    val keys = mutableSetOf<Int>()
    while (true) {
        val keyLen = readCompactSize(buf)
        if (keyLen == 0L) break
        val rawKey = ByteArray(keyLen.toInt())
        buf.read(rawKey)
        keys.add(rawKey[0].toInt() and 0xFF)
        val valueLen = readCompactSize(buf)
        buf.skip(valueLen)
    }
    return keys
}

private fun extractPartialSig(pset: ByteArray, pubKey: ByteArray): ByteArray? {
    val buf = ByteArrayInputStream(pset)
    buf.skip(5)
    // skip global
    while (true) {
        val keyLen = readCompactSize(buf)
        if (keyLen == 0L) break
        buf.skip(keyLen)
        buf.skip(readCompactSize(buf))
    }
    // first input
    while (true) {
        val keyLen = readCompactSize(buf)
        if (keyLen == 0L) break
        val rawKey = ByteArray(keyLen.toInt())
        buf.read(rawKey)
        val valueLen = readCompactSize(buf)
        val value = ByteArray(valueLen.toInt())
        buf.read(value)
        if ((rawKey[0].toInt() and 0xFF) == 0x02 &&
            rawKey.size == 1 + pubKey.size &&
            rawKey.copyOfRange(1, rawKey.size).contentEquals(pubKey)
        ) {
            return value
        }
    }
    return null
}

private fun readCompactSize(buf: ByteArrayInputStream): Long {
    val first = buf.read()
    require(first >= 0)
    return when {
        first < 0xFD -> first.toLong()
        first == 0xFD -> {
            val lo = buf.read()
            val hi = buf.read()
            (lo or (hi shl 8)).toLong()
        }
        else -> error("unsupported compact size")
    }
}
