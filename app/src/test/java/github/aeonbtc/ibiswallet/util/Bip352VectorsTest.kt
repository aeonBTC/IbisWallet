package github.aeonbtc.ibiswallet.util

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.math.BigInteger
import java.security.MessageDigest
import kotlin.time.Duration.Companion.seconds
import org.json.JSONArray
import org.json.JSONObject

/**
 * Official BIP-352 send/receive vectors
 * (`bip-0352/send_and_receive_test_vectors.json`) plus the BIP-340 signature
 * vectors (`bip-0340/test-vectors.csv`).
 *
 * Input classification mirrors the reference implementation
 * (`get_pubkey_from_input`): P2PKH via sliding-window hash160 match (handles
 * malleated scriptSigs), P2WPKH via witness pubkey, P2TR key-path via the
 * output x-only key, script-path NUMS inputs skipped, P2SH skipped (our
 * wallets never hold P2SH inputs), uncompressed keys skipped. `input_hash`
 * always covers ALL outpoints, eligible or not.
 */
class Bip352VectorsTest : FunSpec({

    fun resourceText(path: String): String =
        requireNotNull(javaClass.getResourceAsStream(path)) { "Missing test resource $path" }
            .bufferedReader().use { it.readText() }

    // Lazily parsed once; individual tests only do EC work.
    val cases by lazy {
        val arr = JSONArray(resourceText("/bip352/send_and_receive_test_vectors.json"))
        List(arr.length()) { arr.getJSONObject(it) }
    }

    context("BIP-352 official sending vectors") {
        cases.forEach { case ->
            val comment = case.getString("comment")
            val sending = case.optJSONArray("sending") ?: JSONArray()
            for (i in 0 until sending.length()) {
                val entry = sending.getJSONObject(i)
                test("$comment [sending #$i]").config(timeout = 300.seconds) {
                    runSendingVector(entry)
                }
            }
        }

        test("K_max group limit enforced (2324 recipients)").config(timeout = 300.seconds) {
            val arr = JSONArray(resourceText("/bip352/send_and_receive_test_vectors.json"))
            val kmax = List(arr.length()) { arr.getJSONObject(it) }
                .first { it.getString("comment").contains("K_max") }
            val recipient = kmax.getJSONArray("sending").getJSONObject(0)
                .getJSONObject("given").getJSONArray("recipients").getJSONObject(0)
            val address = recipient.getString("address")
            val count = recipient.optInt("count", 1)
            (count > 2323) shouldBe true
            val vin = kmax.getJSONArray("sending").getJSONObject(0).getJSONObject("given")
                .getJSONArray("vin").getJSONObject(0)
            val input = SilentPayment.InputKey(
                outpoint = "${vin.getString("txid")}:${vin.getInt("vout")}",
                privateKey = vin.getString("private_key").hexToBytes(),
                isTaproot = true,
            )
            runCatching {
                SilentPayment.createOutputKeys(
                    inputKeys = listOf(input),
                    recipients = List(count) { address },
                    allVinOutpoints = listOf(input.outpoint),
                )
            }.isFailure shouldBe true
        }
    }

    context("BIP-352 official receiving vectors") {
        cases.forEach { case ->
            val comment = case.getString("comment")
            val receiving = case.optJSONArray("receiving") ?: JSONArray()
            for (i in 0 until receiving.length()) {
                val entry = receiving.getJSONObject(i)
                test("$comment [receiving #$i]").config(timeout = 600.seconds) {
                    runReceivingVector(entry)
                }
            }
        }
    }

    context("BIP-340 official signature vectors") {
        val rows by lazy {
            resourceText("/bip340/test-vectors.csv").lines()
                .drop(1).filter { it.isNotBlank() }.map { it.split(",") }
        }
        test("deterministic signing matches indices 0-3").config(timeout = 120.seconds) {
            rows.filter { it[0].toInt() in 0..3 }.forEach { row ->
                val sig = SilentPayment.schnorrSign(
                    row[1].hexToBytes(),
                    row[4].hexToBytes(),
                    row[3].hexToBytes(),
                )
                sig.toHex() shouldBe row[5].lowercase()
                SilentPayment.schnorrVerify(row[2].hexToBytes(), row[4].hexToBytes(), sig) shouldBe true
            }
        }
        test("verification accepts index 4").config(timeout = 60.seconds) {
            val row = rows.first { it[0].toInt() == 4 }
            SilentPayment.schnorrVerify(row[2].hexToBytes(), row[4].hexToBytes(), row[5].hexToBytes()) shouldBe true
        }
        test("verification rejects indices 5-14").config(timeout = 120.seconds) {
            rows.filter { it[0].toInt() in 5..14 }.forEach { row ->
                SilentPayment.schnorrVerify(row[2].hexToBytes(), row[4].hexToBytes(), row[5].hexToBytes()) shouldBe false
            }
        }
        // Indices 15-18 use 0/1/17/100-byte messages, outside this API's
        // contract (all production messages are 32-byte sighashes) and are
        // intentionally not covered here.
    }
})

private val curveOrder = BigInteger(
    "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEBAAEDCE6AF48A03BBFD25E8CD0364141",
    16,
)
private val numsInternalKeyHex = "50929b74c1a04954b78b4b6035e97a5e078a5a0f28ec96d547bfee9ace803ac0"

private data class ClassifiedInput(
    val outpoint: String,
    val privateKey: ByteArray,
    val isTaproot: Boolean,
)

private fun runSendingVector(entry: JSONObject) {
    val given = entry.getJSONObject("given")
    val expected = entry.getJSONObject("expected")
    val vins = given.getJSONArray("vin")
    val allOutpoints = mutableListOf<String>()
    val eligible = mutableListOf<ClassifiedInput>()
    for (i in 0 until vins.length()) {
        val vin = vins.getJSONObject(i)
        val outpoint = "${vin.getString("txid")}:${vin.getInt("vout")}"
        allOutpoints += outpoint
        classifyVectorInput(vin)?.let { eligible += it.copy(outpoint = outpoint) }
    }
    val recipients = mutableListOf<String>()
    val recipientArr = given.getJSONArray("recipients")
    for (i in 0 until recipientArr.length()) {
        val recipient = recipientArr.getJSONObject(i)
        repeat(recipient.optInt("count", 1)) { recipients += recipient.getString("address") }
    }
    val expectedSets = mutableListOf<List<String>>()
    val outputsArr = expected.optJSONArray("outputs") ?: JSONArray()
    for (i in 0 until outputsArr.length()) {
        val set = outputsArr.optJSONArray(i) ?: continue
        expectedSets += List(set.length()) { set.getString(it).lowercase() }
    }
    if (eligible.isEmpty() || expectedSets.all { it.isEmpty() }) {
        // Reference returns no outputs (no valid inputs, zero key sum, or
        // K_max exceeded); this implementation fails closed instead.
        runCatching {
            SilentPayment.createOutputKeys(
                inputKeys = eligible.map {
                    SilentPayment.InputKey(it.outpoint, it.privateKey, it.isTaproot)
                },
                recipients = recipients,
                allVinOutpoints = allOutpoints,
            )
        }.isFailure shouldBe true
        return
    }
    val created = SilentPayment.createOutputKeys(
        inputKeys = eligible.map {
            SilentPayment.InputKey(it.outpoint, it.privateKey, it.isTaproot)
        },
        recipients = recipients,
        allVinOutpoints = allOutpoints,
    )
    val ours = created.sortedBy { it.recipientIndex }.map { it.xOnlyPublicKey.toHex() }
    // Reference returns list(set(outputs)): sets, in arbitrary order. Compare
    // as sets (k-order is pinned by the receiving vectors instead).
    expectedSets.any { it.size == ours.size && it.toSet() == ours.toSet() } shouldBe true
}

private fun runReceivingVector(entry: JSONObject) {
    val given = entry.getJSONObject("given")
    val expected = entry.getJSONObject("expected")
    val expectedOutputs = expected.optJSONArray("outputs")
    val tweakHex = expected.optString("tweak", "")
    if ((expectedOutputs == null || expectedOutputs.length() == 0) && tweakHex.isBlank()) {
        // Reference skips the transaction (no valid inputs or infinite key
        // sum); sender-side coverage asserts the failure. Nothing to scan.
        return
    }
    val keyMaterial = given.getJSONObject("key_material")
    val scanPriv = keyMaterial.getString("scan_priv_key").hexToBytes()
    val spendPriv = keyMaterial.getString("spend_priv_key").hexToBytes()
    val spendPub = SilentPayment.compressedPublicKey(spendPriv)
    val outputsArr = given.getJSONArray("outputs")
    val outputs = List(outputsArr.length()) { i ->
        val xonly = outputsArr.getString(i).hexToBytes()
        SilentPayment.TxOutput(
            scriptPubKey = byteArrayOf(0x51, 0x20) + xonly,
            valueSats = 0UL,
        )
    }
    val found = SilentPayment.scanOutputs(
        tweakKey = tweakHex.hexToBytes(),
        scanPrivateKey = scanPriv,
        spendPrivateKey = spendPriv,
        spendPublicKey = spendPub,
        outputs = outputs,
    )
    val message = MessageDigest.getInstance("SHA-256").digest("message".toByteArray())
    val expectedByPub = mutableMapOf<String, JSONObject>()
    if (expectedOutputs != null) {
        for (i in 0 until expectedOutputs.length()) {
            val output = expectedOutputs.getJSONObject(i)
            expectedByPub[output.getString("pub_key").lowercase()] = output
            // Reference signatures are BIP-340 sigs over SHA256("message") with
            // the derived (even-Y) spend key: independent verify coverage.
            SilentPayment.schnorrVerify(
                output.getString("pub_key").hexToBytes(),
                message,
                output.getString("signature").hexToBytes(),
            ) shouldBe true
        }
    }
    // K_max schema reports a count instead of per-output tweaks.
    if (expected.has("n_outputs")) {
        found.size shouldBe expected.getInt("n_outputs")
        return
    }
    // Every found output must match a reference entry exactly, including the
    // derived spend key (spend_priv + priv_key_tweak mod n).
    found.forEach { match ->
        val output = expectedByPub[match.xOnlyPublicKey.toHex()]
            ?: throw AssertionError("Found output ${match.xOnlyPublicKey.toHex()} not in reference set")
        val fullPriv = BigInteger(1, spendPriv)
            .add(BigInteger(1, output.getString("priv_key_tweak").hexToBytes()))
            .mod(curveOrder)
        match.spendPrivateKey.toHex() shouldBe fullPriv.toByteArray32().toHex()
    }
    val labels = given.optJSONArray("labels")
    val labelList = if (labels == null) emptyList() else List(labels.length()) { labels.getInt(it) }
    val expectedCount = expectedOutputs?.length() ?: 0
    if (labelList.isEmpty()) {
        found.size shouldBe expectedCount
        found.forEach { it.isChange shouldBe false }
    } else if (labelList.size == 1 && labelList[0] == 0) {
        // Sender-change recovery: every output uses the label-0 tweak.
        found.size shouldBe expectedCount
        found.forEach { it.isChange shouldBe true }
    }
    // Custom labels (m >= 1) are a documented non-goal: senders cannot create
    // them for our keys (we never publish labeled addresses), and the BIP-352
    // stop rule means a label-unaware scan may legitimately stop early. Only
    // the no-false-positive + spend-key proofs above apply.
}

private fun classifyVectorInput(vin: JSONObject): ClassifiedInput? {
    val privHex = vin.optString("private_key", "")
    if (privHex.isBlank()) return null
    val privateKey = privHex.hexToBytes()
    val script = vin.getJSONObject("prevout").getJSONObject("scriptPubKey").getString("hex").hexToBytes()
    val scriptSig = vin.optString("scriptSig", "").ifBlank { null }?.hexToBytes() ?: ByteArray(0)
    val witnessHex = vin.optString("txinwitness", "")
    val witness = if (witnessHex.isBlank()) emptyList() else parseWitnessStack(witnessHex.hexToBytes())
    return when {
        isP2pkh(script) -> {
            val spkHash = script.copyOfRange(3, 23)
            var matched = false
            var i = scriptSig.size
            while (i - 33 >= 0) {
                val window = scriptSig.copyOfRange(i - 33, i)
                if ((window[0] == 0x02.toByte() || window[0] == 0x03.toByte()) &&
                    ElectrumSeedUtil.hash160(window).contentEquals(spkHash)
                ) {
                    matched = true
                    break
                }
                i--
            }
            if (matched) ClassifiedInput("", privateKey, false) else null
        }
        isP2wpkh(script) -> {
            val pub = witness.lastOrNull()
            if (pub != null && pub.size == 33 && (pub[0] == 0x02.toByte() || pub[0] == 0x03.toByte())) {
                ClassifiedInput("", privateKey, false)
            } else {
                null
            }
        }
        isP2sh(script) -> null
        isP2tr(script) -> {
            val stack = witness.toMutableList()
            if (stack.size > 1 && stack.last().isNotEmpty() && stack.last()[0] == 0x50.toByte()) {
                stack.removeAt(stack.size - 1)
            }
            if (stack.size > 1) {
                val control = stack.last()
                if (control.size >= 33 && control.copyOfRange(1, 33).toHex() == numsInternalKeyHex) {
                    return null
                }
            }
            ClassifiedInput("", privateKey, true)
        }
        else -> null
    }
}

private fun isP2pkh(script: ByteArray): Boolean =
    script.size == 25 && script[0] == 0x76.toByte() && script[1] == 0xA9.toByte() &&
        script[2] == 0x14.toByte() && script[23] == 0x88.toByte() && script[24] == 0xAC.toByte()

private fun isP2wpkh(script: ByteArray): Boolean =
    script.size == 22 && script[0] == 0x00.toByte() && script[1] == 0x14.toByte()

private fun isP2sh(script: ByteArray): Boolean =
    script.size == 23 && script[0] == 0xA9.toByte() && script[1] == 0x14.toByte() && script[22] == 0x87.toByte()

private fun isP2tr(script: ByteArray): Boolean =
    script.size == 34 && script[0] == 0x51.toByte() && script[1] == 0x20.toByte()

private fun parseWitnessStack(bytes: ByteArray): List<ByteArray> {
    var pos = 0
    fun readCompact(): Long {
        val first = bytes[pos++].toInt() and 0xFF
        return when (first) {
            0xFD -> ((bytes[pos++].toInt() and 0xFF) or ((bytes[pos++].toInt() and 0xFF) shl 8)).toLong()
            0xFE -> {
                var value = 0L
                for (i in 0 until 4) value = value or ((bytes[pos++].toLong() and 0xFF) shl (8 * i))
                value
            }
            0xFF -> {
                var value = 0L
                for (i in 0 until 8) value = value or ((bytes[pos++].toLong() and 0xFF) shl (8 * i))
                value
            }
            else -> first.toLong()
        }
    }
    val count = readCompact().toInt()
    return List(count) {
        val length = readCompact().toInt()
        bytes.copyOfRange(pos, pos + length).also { pos += length }
    }
}

private fun BigInteger.toByteArray32(): ByteArray {
    val bytes = toByteArray()
    val stripped = if (bytes.size > 1 && bytes[0] == 0.toByte()) bytes.copyOfRange(1, bytes.size) else bytes
    require(stripped.size <= 32) { "Integer too large" }
    return ByteArray(32 - stripped.size) + stripped
}

private fun String.hexToBytes(): ByteArray {
    require(length % 2 == 0) { "Invalid hex length" }
    return chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}

private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
