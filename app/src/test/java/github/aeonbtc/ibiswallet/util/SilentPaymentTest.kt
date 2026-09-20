package github.aeonbtc.ibiswallet.util

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe

class SilentPaymentTest : FunSpec({
    val address = "sp1qqgste7k9hx0qftg6qmwlkqtwuy6cycyavzmzj85c6qdfhjdpdjtdgqjuexzk6murw56suy3e0rd2cgqvycxttddwsvgxe2usfpxumr70xc9pkqwv"

    context("address parsing") {
        test("parses a v0 mainnet silent payment address") {
            val parsed = SilentPayment.parseAddress(address).getOrThrow()

            parsed.scanPublicKey.toHex() shouldBe
                "0220bcfac5b99e04ad1a06ddfb016ee13582609d60b6291e98d01a9bc9a16c96d4"
            parsed.spendPublicKey.toHex() shouldBe
                "025cc9856d6f8375350e123978daac200c260cb5b5ae83106cab90484dcd8fcf36"
        }

        test("rejects an invalid checksum") {
            SilentPayment.parseAddress(address.dropLast(1) + "x").isFailure shouldBe true
        }

        test("encodes back to the same address") {
            val parsed = SilentPayment.parseAddress(address).getOrThrow()
            SilentPayment.encodeAddress(parsed.scanPublicKey, parsed.spendPublicKey) shouldBe address
        }

        test("accepts v1-v30 by reading the first 66 payload bytes") {
            val parsed = SilentPayment.parseAddress(address).getOrThrow()
            val v1 = SilentPayment.encodeAddress(
                parsed.scanPublicKey,
                parsed.spendPublicKey,
                version = 1,
                extraPayload = ByteArray(16) { it.toByte() },
            )
            val reparsed = SilentPayment.parseAddress(v1).getOrThrow()
            reparsed.scanPublicKey.toHex() shouldBe parsed.scanPublicKey.toHex()
            reparsed.spendPublicKey.toHex() shouldBe parsed.spendPublicKey.toHex()
            SilentPayment.isSilentPaymentAddress(v1) shouldBe true
        }

        test("rejects v31 and over-long v0 payloads") {
            val parsed = SilentPayment.parseAddress(address).getOrThrow()
            runCatching {
                SilentPayment.encodeAddress(parsed.scanPublicKey, parsed.spendPublicKey, version = 31)
            }.isFailure shouldBe true
            val longV0 = SilentPayment.encodeAddress(
                parsed.scanPublicKey,
                parsed.spendPublicKey,
                version = 0,
                extraPayload = ByteArray(2),
            )
            SilentPayment.parseAddress(longV0).isFailure shouldBe true
        }
    }

    context("receiver scan") {
        test("finds the output from the simple send vector") {
            val seed = ByteArray(64) { 1 }
            val keys = SilentPayment.deriveReceiverKeys(seed)
            val inputKeys =
                listOf(
                    SilentPayment.InputKey(
                        outpoint = "f4184fc596403b9d638783cf57adfe4c75c605f6356fbc91338530e9831e9e16:0",
                        privateKey = "eadc78165ff1f8ea94ad7cfdc54990738a4c53f6e0507b42154201b8e5dff3b1".hexToBytes(),
                    ),
                    SilentPayment.InputKey(
                        outpoint = "a1075db55d416d3ca199f55b6084e2115b9345e16c5cf302fc80e9d5fbf5d48d:0",
                        privateKey = "93f5ed907ad5b2bdbbdcb5d9116ebc0a4e1f92f910d5260237fa45a9408aad16".hexToBytes(),
                    ),
                )
            val created =
                SilentPayment.createOutputKeys(
                    inputKeys = inputKeys,
                    recipients = listOf(keys.address),
                    allVinOutpoints = inputKeys.map { it.outpoint },
                )
            val tweak = SilentPayment.computeTweakKey(inputKeys, inputKeys.map { it.outpoint })
            val found =
                SilentPayment.scanOutputs(
                    tweakKey = tweak,
                    scanPrivateKey = keys.scanPrivateKey,
                    spendPrivateKey = keys.spendPrivateKey,
                    spendPublicKey = keys.spendPublicKey,
                    outputs =
                        listOf(
                            SilentPayment.TxOutput(
                                scriptPubKey = SilentPayment.taprootScriptPubKey(created.first().xOnlyPublicKey),
                                valueSats = 1000UL,
                            ),
                        ),
                )
            found.size shouldBe 1
            found.first().xOnlyPublicKey.toHex() shouldBe created.first().xOnlyPublicKey.toHex()
            found.first().isChange shouldBe false
        }
    }

    context("bip340 schnorr") {
        test("sign verifies round-trip") {
            val seed = ByteArray(64) { 7 }
            val keys = SilentPayment.deriveReceiverKeys(seed)
            val message = ByteArray(32) { it.toByte() }
            val sig = SilentPayment.schnorrSign(keys.spendPrivateKey, message)
            sig.size shouldBe 64
            SilentPayment.schnorrVerify(keys.spendPublicKey, message, sig) shouldBe true
        }

        test("tampered signature fails verification") {
            val seed = ByteArray(64) { 7 }
            val keys = SilentPayment.deriveReceiverKeys(seed)
            val message = ByteArray(32) { it.toByte() }
            val sig = SilentPayment.schnorrSign(keys.spendPrivateKey, message)
            val tampered = sig.copyOf().also { it[0] = (it[0].toInt() xor 0x01).toByte() }
            SilentPayment.schnorrVerify(keys.spendPublicKey, message, tampered) shouldBe false
        }

        test("wrong message fails verification") {
            val seed = ByteArray(64) { 7 }
            val keys = SilentPayment.deriveReceiverKeys(seed)
            val message = ByteArray(32) { it.toByte() }
            val sig = SilentPayment.schnorrSign(keys.spendPrivateKey, message)
            val other = ByteArray(32) { (it + 1).toByte() }
            SilentPayment.schnorrVerify(keys.spendPublicKey, other, sig) shouldBe false
        }

        test("deterministic with fixed aux") {
            val seed = ByteArray(64) { 7 }
            val keys = SilentPayment.deriveReceiverKeys(seed)
            val message = ByteArray(32) { 1 }
            val aux = ByteArray(32) { 2 }
            val first = SilentPayment.schnorrSign(keys.spendPrivateKey, message, aux)
            val second = SilentPayment.schnorrSign(keys.spendPrivateKey, message, aux)
            first.toList() shouldBe second.toList()
            SilentPayment.schnorrVerify(keys.spendPublicKey, message, first) shouldBe true
        }

        test("taproot key-spend sighash is stable and 32 bytes") {
            val seed = ByteArray(64) { 7 }
            val keys = SilentPayment.deriveReceiverKeys(seed)
            val inputs =
                listOf(
                    SilentPayment.SighashTxIn(
                        txidHex = "00".repeat(32),
                        vout = 0u,
                        sequence = 0xFFFFFFFDu,
                        prevScriptPubKey = ByteArray(34) { 1 },
                        prevValueSats = 9125UL,
                    ),
                )
            val outputs =
                listOf(
                    SilentPayment.SighashTxOut(
                        valueSats = 8000UL,
                        scriptPubKey = ByteArray(34) { 2 },
                    ),
                )
            val first = SilentPayment.taprootKeySpendSighash(2, 0u, inputs, outputs, 0)
            val second = SilentPayment.taprootKeySpendSighash(2, 0u, inputs, outputs, 0)
            first.size shouldBe 32
            first.toList() shouldBe second.toList()
            val sig = SilentPayment.schnorrSign(keys.spendPrivateKey, first)
            SilentPayment.schnorrVerify(keys.spendPublicKey, first, sig) shouldBe true
        }

        test("matches BIP-341 SIGHASH_DEFAULT key-path vector") {
            val raw =
                ("02000000097de20cbff686da83a54981d2b9bab3586f4ca7e48f57f5b55963115f3b334e9c010000000000000000" +
                    "d7b7cab57b1393ace2d064f4d4a2cb8af6def61273e127517d44759b6dafdd990000000000ffffffff" +
                    "f8e1f583384333689228c5d28eac13366be082dc57441760d957275419a418420000000000ffffffff" +
                    "f0689180aa63b30cb162a73c6d2a38b7eeda2a83ece74310fda0843ad604853b0100000000feffffff" +
                    "aa5202bdf6d8ccd2ee0f0202afbbb7461d9264a25e5bfd3c5a52ee1239e0ba6c0000000000feffffff" +
                    "956149bdc66faa968eb2be2d2faa29718acbfe3941215893a2a3446d32acd050000000000000000000" +
                    "e664b9773b88c09c32cb70a2a3e4da0ced63b7ba3b22f848531bbb1d5d5f4c94010000000000000000" +
                    "e9aa6b8e6c9de67619e6a3924ae25696bb7b694bb677a632a74ef7eadfd4eabf0000000000ffffffff" +
                    "a778eb6a263dc090464cd125c466b5a99667720b1c110468831d058aa1b82af10100000000ffffffff" +
                    "0200ca9a3b000000001976a91406afd46bcdfd22ef94ac122aa11f241244a37ecc88ac" +
                    "807840cb0000000020ac9a87f5594be208f8532db38cff670c450ed2fea8fcdefcc9a663f78bab962b" +
                    "0065cd1d").hexToBytes()
            val utxos =
                listOf(
                    "512053a1f6e454df1aa2776a2814a721372d6258050de330b3c6d10ee8f4e0dda343" to 420000000UL,
                    "5120147c9c57132f6e7ecddba9800bb0c4449251c92a1e60371ee77557b6620f3ea3" to 462000000UL,
                    "76a914751e76e8199196d454941c45d1b3a323f1433bd688ac" to 294000000UL,
                    "5120e4d810fd50586274face62b8a807eb9719cef49c04177cc6b76a9a4251d5450e" to 504000000UL,
                    "512091b64d5324723a985170e4dc5a0f84c041804f2cd12660fa5dec09fc21783605" to 630000000UL,
                    "00147dd65592d0ab2fe0d0257d571abf032cd9db93dc" to 378000000UL,
                    "512075169f4001aa68f15bbed28b218df1d0a62cbbcf1188c6665110c293c907b831" to 672000000UL,
                    "5120712447206d7a5238acc7ff53fbe94a3b64539ad291c7cdbc490b7577e4b17df5" to 546000000UL,
                    "512077e30a5522dd9f894c3f8b8bd4c4b2cf82ca7da8a3ea6a239655c39c050ab220" to 588000000UL,
                )
            val parsed = parseUnsignedTx(raw)
            val inputs =
                parsed.inputs.mapIndexed { i, vin ->
                    SilentPayment.SighashTxIn(
                        txidHex = vin.txidHex,
                        vout = vin.vout,
                        sequence = vin.sequence,
                        prevScriptPubKey = utxos[i].first.hexToBytes(),
                        prevValueSats = utxos[i].second,
                    )
                }
            SilentPayment.taprootKeySpendSighash(
                version = parsed.version,
                lockTime = parsed.lockTime,
                inputs = inputs,
                outputs = parsed.outputs,
                inputIndex = 4,
            ).toHex() shouldBe "4f900a0bae3f1446fd48490c2958b5a023228f01661cda3496a11da502a7f7ef"
        }
    }

    context("psbt taproot surgery") {
        fun minimalPsbt(): ByteArray {
            val magic = byteArrayOf(0x70, 0x73, 0x62, 0x74, 0xFF.toByte())
            val global = byteArrayOf(0x00)
            val inputMap = byteArrayOf(0x01, 0x00, 0x20) + ByteArray(32) { 3 } + byteArrayOf(0x00)
            val outputMap = byteArrayOf(0x00)
            return magic + global + inputMap + outputMap
        }

        fun findMapValue(
            bytes: ByteArray,
            mapIndex: Int,
            keyByte: Byte,
        ): ByteArray? {
            var pos = 5
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
            fun skipMap() {
                while (true) {
                    val keyLen = readCompact()
                    if (keyLen == 0L) break
                    pos += keyLen.toInt()
                    pos += readCompact().toInt()
                }
            }
            skipMap()
            repeat(mapIndex + 1) { index ->
                val pairs = mutableListOf<Pair<ByteArray, ByteArray>>()
                while (true) {
                    val keyLen = readCompact()
                    if (keyLen == 0L) break
                    val key = bytes.copyOfRange(pos, pos + keyLen.toInt())
                    pos += keyLen.toInt()
                    val valueLen = readCompact()
                    val value = bytes.copyOfRange(pos, pos + valueLen.toInt())
                    pos += valueLen.toInt()
                    pairs += key to value
                }
                if (index == mapIndex) {
                    return pairs.firstOrNull { it.first.size == 1 && it.first[0] == keyByte }?.second
                }
            }
            return null
        }

        test("injects tap internal key and sig into the right input") {
            val xonly = ByteArray(32) { 4 }
            val sig = ByteArray(64) { 5 }
            val modified = SilentPayment.psbtInjectTapKeySigs(minimalPsbt(), 1, mapOf(0 to (xonly to sig)))
            (modified.size - minimalPsbt().size) shouldBe 171
            findMapValue(modified, 0, 0x17.toByte())?.toList() shouldBe xonly.toList()
            findMapValue(modified, 0, 0x13.toByte())?.toList() shouldBe sig.toList()
            findMapValue(modified, 0, 0x08.toByte())?.toList() shouldBe
                (byteArrayOf(0x01, 0x40) + sig).toList()
        }

        test("verify confirms the injection") {
            val xonly = ByteArray(32) { 4 }
            val sig = ByteArray(64) { 5 }
            SilentPayment.psbtVerifyTapInjection(minimalPsbt(), 1, 0) shouldBe false
            val modified = SilentPayment.psbtInjectTapKeySigs(minimalPsbt(), 1, mapOf(0 to (xonly to sig)))
            SilentPayment.psbtVerifyTapInjection(modified, 1, 0) shouldBe true
        }

        test("re-injection replaces instead of duplicating taproot fields") {
            val xonly = ByteArray(32) { 4 }
            val sig = ByteArray(64) { 5 }
            val once = SilentPayment.psbtInjectTapKeySigs(minimalPsbt(), 1, mapOf(0 to (xonly to sig)))
            val otherSig = ByteArray(64) { 6 }
            val twice = SilentPayment.psbtInjectTapKeySigs(once, 1, mapOf(0 to (xonly to otherSig)))
            // Same size as a single injection: no duplicate map keys appended.
            (twice.size - minimalPsbt().size) shouldBe 171
            findMapValue(twice, 0, 0x13.toByte())?.toList() shouldBe otherSig.toList()
            SilentPayment.psbtVerifyTapInjection(twice, 1, 0) shouldBe true
        }

        test("rejects bad sizes and indexes") {
            val xonly = ByteArray(32) { 4 }
            val sig = ByteArray(64) { 5 }
            runCatching {
                SilentPayment.psbtInjectTapKeySigs(minimalPsbt(), 1, mapOf(0 to (ByteArray(31) to sig)))
            }.isFailure shouldBe true
            runCatching {
                SilentPayment.psbtInjectTapKeySigs(minimalPsbt(), 1, mapOf(1 to (xonly to sig)))
            }.isFailure shouldBe true
        }
    }

    context("input key ownership proof") {
        // Keys and scripts below come from the official BIP-352 vectors, so
        // the expected relationships are externally pinned, not self-derived.
        test("accepts matching P2PKH keys") {
            val priv = "eadc78165ff1f8ea94ad7cfdc54990738a4c53f6e0507b42154201b8e5dff3b1".hexToBytes()
            val script = "76a91419c2f3ae0ca3b642bd3e49598b8da89f50c1416188ac".hexToBytes()
            SilentPayment.inputKeyMatchesScript(priv, script, isTaproot = false) shouldBe true
        }

        test("rejects mismatched P2PKH keys") {
            val priv = "eadc78165ff1f8ea94ad7cfdc54990738a4c53f6e0507b42154201b8e5dff3b1".hexToBytes()
            val other = "76a914d9317c66f54ff0a152ec50b1d19c25be50c8e15988ac".hexToBytes()
            SilentPayment.inputKeyMatchesScript(priv, other, isTaproot = false) shouldBe false
        }

        test("accepts matching P2WPKH keys") {
            val priv = "a6df6a0bb448992a301df4258e06a89fe7cf7146f59ac3bd5ff26083acb22ceb".hexToBytes()
            val script = "00149d9e24f9fab4e35bf1a6df4b46cb533296ac0792".hexToBytes()
            SilentPayment.inputKeyMatchesScript(priv, script, isTaproot = false) shouldBe true
        }

        test("accepts matching taproot output keys") {
            val priv = "eadc78165ff1f8ea94ad7cfdc54990738a4c53f6e0507b42154201b8e5dff3b1".hexToBytes()
            val script = "51205a1e61f898173040e20616d43e9f496fba90338a39faa1ed98fcbaeee4dd9be5".hexToBytes()
            SilentPayment.inputKeyMatchesScript(priv, script, isTaproot = true) shouldBe true
        }

        test("rejects mismatched taproot keys and wrong script kinds") {
            val priv = "eadc78165ff1f8ea94ad7cfdc54990738a4c53f6e0507b42154201b8e5dff3b1".hexToBytes()
            val otherTaproot = "5120782eeb913431ca6e9b8c2fd80a5f72ed2024ef72a3c6fb10263c379937323338".hexToBytes()
            SilentPayment.inputKeyMatchesScript(priv, otherTaproot, isTaproot = true) shouldBe false
            val p2pkh = "76a91419c2f3ae0ca3b642bd3e49598b8da89f50c1416188ac".hexToBytes()
            SilentPayment.inputKeyMatchesScript(priv, p2pkh, isTaproot = true) shouldBe false
            val p2tr = "51205a1e61f898173040e20616d43e9f496fba90338a39faa1ed98fcbaeee4dd9be5".hexToBytes()
            SilentPayment.inputKeyMatchesScript(priv, p2tr, isTaproot = false) shouldBe false
            SilentPayment.inputKeyMatchesScript(ByteArray(31), p2tr, isTaproot = true) shouldBe false
        }
    }

    context("label tweak (BIP-352 change)") {
        test("preimage is the 32-byte scan private key plus big-endian label") {
            val seed = ByteArray(64) { 3 }
            val keys = SilentPayment.deriveReceiverKeys(seed)
            val preimage = SilentPayment.labelPreimage(keys.scanPrivateKey, 0)
            preimage.size shouldBe 36
            preimage.copyOfRange(0, 32).toList() shouldBe keys.scanPrivateKey.toList()
            preimage.copyOfRange(32, 36).toList() shouldBe byteArrayOf(0, 0, 0, 0).toList()
            SilentPayment.labelPreimage(keys.scanPrivateKey, 1).copyOfRange(32, 36).toList() shouldBe
                byteArrayOf(0, 0, 0, 1).toList()
        }

        test("label tweak matches an independently computed BIP0352/Label hash") {
            val seed = ByteArray(64) { 3 }
            val keys = SilentPayment.deriveReceiverKeys(seed)
            val tagHash = sha256("BIP0352/Label".toByteArray(Charsets.US_ASCII))
            val expected = sha256(tagHash + tagHash + keys.scanPrivateKey + byteArrayOf(0, 0, 0, 0))
            SilentPayment.taggedHash(
                "BIP0352/Label",
                SilentPayment.labelPreimage(keys.scanPrivateKey, 0),
            ).toList() shouldBe expected.toList()
        }

        test("preimage must not be the compressed public key") {
            // Regression: the tweak previously hashed the 33-byte compressed
            // scan public key, which no compliant sender ever produces, so
            // genuine label-0 change outputs were never detected.
            val seed = ByteArray(64) { 3 }
            val keys = SilentPayment.deriveReceiverKeys(seed)
            SilentPayment.labelPreimage(keys.scanPrivateKey, 0).size shouldBe 36
            (keys.scanPublicKey + byteArrayOf(0, 0, 0, 0)).size shouldBe 37
        }

        test("rejects bad key sizes and labels") {
            val seed = ByteArray(64) { 3 }
            val keys = SilentPayment.deriveReceiverKeys(seed)
            runCatching { SilentPayment.labelPreimage(ByteArray(33), 0) }.isFailure shouldBe true
            runCatching { SilentPayment.labelPreimage(keys.scanPrivateKey, -1) }.isFailure shouldBe true
        }
    }

    context("sender vectors") {
        test("matches BIP-352 simple send vector with two P2PKH inputs") {
            val outputs =
                SilentPayment.createOutputKeys(
                    inputKeys =
                        listOf(
                            SilentPayment.InputKey(
                                outpoint = "f4184fc596403b9d638783cf57adfe4c75c605f6356fbc91338530e9831e9e16:0",
                                privateKey = "eadc78165ff1f8ea94ad7cfdc54990738a4c53f6e0507b42154201b8e5dff3b1".hexToBytes(),
                            ),
                            SilentPayment.InputKey(
                                outpoint = "a1075db55d416d3ca199f55b6084e2115b9345e16c5cf302fc80e9d5fbf5d48d:0",
                                privateKey = "93f5ed907ad5b2bdbbdcb5d9116ebc0a4e1f92f910d5260237fa45a9408aad16".hexToBytes(),
                            ),
                        ),
                    recipients = listOf(address),
                    allVinOutpoints =
                        listOf(
                            "f4184fc596403b9d638783cf57adfe4c75c605f6356fbc91338530e9831e9e16:0",
                            "a1075db55d416d3ca199f55b6084e2115b9345e16c5cf302fc80e9d5fbf5d48d:0",
                        ),
                )

            outputs.map { it.xOnlyPublicKey.toHex() } shouldContainExactly
                listOf("3e9fce73d4e77a4809908e3c3a2e54ee147b9312dc5044a193d1fc85de46e3c1")
        }

        test("refuses to hash an empty outpoint set instead of burning outputs") {
            val seed = ByteArray(64) { 9 }
            val keys = SilentPayment.deriveReceiverKeys(seed)
            runCatching {
                SilentPayment.createOutputKeys(
                    inputKeys =
                        listOf(
                            SilentPayment.InputKey(
                                outpoint = "00".repeat(32) + ":0",
                                privateKey = keys.spendPrivateKey,
                            ),
                        ),
                    recipients = listOf(keys.address),
                    allVinOutpoints = emptyList(),
                )
            }.isFailure shouldBe true
        }
    }
})

private data class ParsedVin(
    val txidHex: String,
    val vout: UInt,
    val sequence: UInt,
)

private data class ParsedUnsignedTx(
    val version: Int,
    val lockTime: UInt,
    val inputs: List<ParsedVin>,
    val outputs: List<SilentPayment.SighashTxOut>,
)

private fun parseUnsignedTx(raw: ByteArray): ParsedUnsignedTx {
    var pos = 0
    fun u8(): Int = raw[pos++].toInt() and 0xFF
    fun u32(): UInt {
        val v =
            (raw[pos].toInt() and 0xFF) or
                ((raw[pos + 1].toInt() and 0xFF) shl 8) or
                ((raw[pos + 2].toInt() and 0xFF) shl 16) or
                ((raw[pos + 3].toInt() and 0xFF) shl 24)
        pos += 4
        return v.toUInt()
    }
    fun u64(): ULong {
        var v = 0UL
        for (i in 0 until 8) {
            v = v or ((raw[pos++].toULong() and 0xFFu) shl (8 * i))
        }
        return v
    }
    fun compact(): Int {
        return when (val first = u8()) {
            0xFD -> u8() or (u8() shl 8)
            0xFE -> u8() or (u8() shl 8) or (u8() shl 16) or (u8() shl 24)
            else -> first
        }
    }
    fun bytes(n: Int): ByteArray = raw.copyOfRange(pos, pos + n).also { pos += n }
    val version = u32().toInt()
    val vinCount = compact()
    val inputs =
        List(vinCount) {
            val txid = bytes(32).reversedArray().toHex()
            val vout = u32()
            val scriptLen = compact()
            pos += scriptLen
            ParsedVin(txid, vout, u32())
        }
    val voutCount = compact()
    val outputs =
        List(voutCount) {
            val value = u64()
            val script = bytes(compact())
            SilentPayment.SighashTxOut(value, script)
        }
    val lockTime = u32()
    return ParsedUnsignedTx(version, lockTime, inputs, outputs)
}

private fun String.hexToBytes(): ByteArray {
    require(length % 2 == 0)
    return chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}

private fun sha256(data: ByteArray): ByteArray =
    java.security.MessageDigest.getInstance("SHA-256").digest(data)

private fun ByteArray.toHex(): String =
    joinToString("") { "%02x".format(it) }
