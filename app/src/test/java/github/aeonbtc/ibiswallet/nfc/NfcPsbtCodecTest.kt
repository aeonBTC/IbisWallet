package github.aeonbtc.ibiswallet.nfc

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.security.MessageDigest

class NfcPsbtCodecTest : FunSpec({
    fun fakePsbt(size: Int = 200): ByteArray =
        byteArrayOf(0x70, 0x73, 0x62, 0x74, 0xFF.toByte()) + ByteArray(size) { it.toByte() }

    fun encodeRecord(
        tnf: Int,
        type: String,
        payload: ByteArray,
        first: Boolean,
        last: Boolean,
    ): ByteArray {
        val typeBytes = type.toByteArray(Charsets.US_ASCII)
        val short = payload.size < 256
        var header = tnf and 0x07
        if (first) header = header or 0x80
        if (last) header = header or 0x40
        if (short) header = header or 0x10
        val out = mutableListOf<Byte>()
        out += header.toByte()
        out += typeBytes.size.toByte()
        if (short) {
            out += payload.size.toByte()
        } else {
            val size = payload.size
            out += ((size shr 24) and 0xFF).toByte()
            out += ((size shr 16) and 0xFF).toByte()
            out += ((size shr 8) and 0xFF).toByte()
            out += (size and 0xFF).toByte()
        }
        typeBytes.forEach { out += it }
        payload.forEach { out += it }
        return out.toByteArray()
    }

    fun textRecordPayload(text: String): ByteArray {
        val lang = "en".toByteArray(Charsets.US_ASCII)
        val textBytes = text.toByteArray(Charsets.UTF_8)
        return byteArrayOf(lang.size.toByte()) + lang + textBytes
    }

    context("Coldcard PSBT round-trip") {
        test("build then parse returns identical bytes with verified checksum") {
            val psbt = fakePsbt()
            val message = NfcPsbtCodec.buildPsbtMessage(psbt)
            val parsed = NfcPsbtCodec.parseMessage(message)
            parsed.shouldBeInstanceOf<NfcPsbtCodec.ParseResult.Psbt>()
            parsed.bytes shouldBe psbt
            parsed.checksumVerified shouldBe true
        }

        test("toSignedData returns base64 decoding back to the PSBT") {
            val psbt = fakePsbt()
            val parsed = NfcPsbtCodec.parseMessage(NfcPsbtCodec.buildPsbtMessage(psbt))
            val data = NfcPsbtCodec.toSignedData(parsed)
            java.util.Base64.getDecoder().decode(data) shouldBe psbt
        }

        test("tampered payload fails checksum") {
            val psbt = fakePsbt()
            val message = NfcPsbtCodec.buildPsbtMessage(psbt)
            message[message.size - 1] = (message[message.size - 1] + 1).toByte()
            val parsed = NfcPsbtCodec.parseMessage(message)
            parsed shouldBe
                NfcPsbtCodec.ParseResult.Failure(NfcPsbtCodec.ParseFailure.CHECKSUM_MISMATCH)
        }
    }

    context("size guard") {
        test("7500 bytes is exportable, 7501 is not") {
            NfcPsbtCodec.isExportableSize(ByteArray(7_500)) shouldBe true
            NfcPsbtCodec.isExportableSize(ByteArray(7_501)) shouldBe false
            NfcPsbtCodec.isExportableSize(byteArrayOf()) shouldBe false
        }

        test("oversized payload throws on build") {
            shouldThrow<IllegalArgumentException> {
                NfcPsbtCodec.buildPsbtMessage(ByteArray(7_501))
            }
        }
    }

    context("finalized transaction records") {
        test("txn record with checksum parses to hex") {
            val rawTx = byteArrayOf(0x02, 0x00, 0x00, 0x00, 0x01, 0x02, 0x03)
            val sha = MessageDigest.getInstance("SHA-256").digest(rawTx)
            val message =
                encodeRecord(0x01, "T", textRecordPayload("Signed Transaction"), true, false) +
                    encodeRecord(0x04, "bitcoin.org:sha256", sha, false, false) +
                    encodeRecord(0x04, "bitcoin.org:txn", rawTx, false, true)
            val parsed = NfcPsbtCodec.parseMessage(message)
            parsed.shouldBeInstanceOf<NfcPsbtCodec.ParseResult.Txn>()
            parsed.rawHex shouldBe "02000000010203"
            parsed.checksumVerified shouldBe true
            NfcPsbtCodec.toSignedData(parsed) shouldBe "02000000010203"
        }

        test("psbt is preferred over txn when both present") {
            val psbt = fakePsbt(64)
            val rawTx = byteArrayOf(0x02, 0x00, 0x00, 0x00)
            // Rebuild manually: label + psbt (no checksum) + txn
            val message =
                encodeRecord(0x01, "T", textRecordPayload("Partly signed PSBT"), true, false) +
                    encodeRecord(0x04, "bitcoin.org:psbt", psbt, false, false) +
                    encodeRecord(0x04, "bitcoin.org:txn", rawTx, false, true)
            val parsed = NfcPsbtCodec.parseMessage(message)
            parsed.shouldBeInstanceOf<NfcPsbtCodec.ParseResult.Psbt>()
            parsed.bytes shouldBe psbt
            parsed.checksumVerified shouldBe false
        }
    }

    context("fallback and failure paths") {
        test("text record alone returns trimmed text payload") {
            val message =
                encodeRecord(0x01, "T", textRecordPayload("  cHNidP8BAH0=  "), true, true)
            val parsed = NfcPsbtCodec.parseMessage(message)
            parsed shouldBe NfcPsbtCodec.ParseResult.TextPayload("cHNidP8BAH0=")
            NfcPsbtCodec.toSignedData(parsed) shouldBe "cHNidP8BAH0="
        }

        test("empty message fails") {
            NfcPsbtCodec.parseMessage(byteArrayOf()) shouldBe
                NfcPsbtCodec.ParseResult.Failure(NfcPsbtCodec.ParseFailure.MALFORMED)
        }

        test("garbage bytes fail as malformed") {
            NfcPsbtCodec.parseMessage(byteArrayOf(0x01, 0x02)) shouldBe
                NfcPsbtCodec.ParseResult.Failure(NfcPsbtCodec.ParseFailure.MALFORMED)
        }

        test("reserved TNF values are rejected") {
            // MB+ME, TNF=6 (reserved), empty type/payload.
            NfcPsbtCodec.parseMessage(byteArrayOf(0xC6.toByte(), 0x00, 0x00)) shouldBe
                NfcPsbtCodec.ParseResult.Failure(NfcPsbtCodec.ParseFailure.MALFORMED)
        }

        test("label-only message has no usable record") {
            val message =
                encodeRecord(0x01, "T", textRecordPayload("   "), true, true)
            // Blank text is dropped, so nothing usable remains.
            NfcPsbtCodec.parseMessage(message) shouldBe
                NfcPsbtCodec.ParseResult.Failure(NfcPsbtCodec.ParseFailure.NO_USABLE_RECORD)
        }

        test("failures map to null signed data") {
            NfcPsbtCodec.toSignedData(
                NfcPsbtCodec.ParseResult.Failure(NfcPsbtCodec.ParseFailure.CHECKSUM_MISMATCH),
            ) shouldBe null
        }
    }

    context("HCE share message") {
        test("share message round-trips through parseMessage with verified checksum") {
            val psbt = fakePsbt()
            val base64 = java.util.Base64.getEncoder().encodeToString(psbt)
            val message = NfcPsbtCodec.buildShareMessage(base64)
            (message != null) shouldBe true
            val parsed = NfcPsbtCodec.parseMessage(message!!)
            parsed.shouldBeInstanceOf<NfcPsbtCodec.ParseResult.Psbt>()
            parsed.bytes shouldBe psbt
            parsed.checksumVerified shouldBe true
        }

        test("blank or non-base64 input returns null") {
            NfcPsbtCodec.buildShareMessage("   ") shouldBe null
            NfcPsbtCodec.buildShareMessage("not valid base64!!!") shouldBe null
        }

        test("oversized PSBT returns null") {
            val oversized = ByteArray(NfcPsbtCodec.MAX_PSBT_BYTES + 1) { it.toByte() }
            val base64 = java.util.Base64.getEncoder().encodeToString(oversized)
            NfcPsbtCodec.buildShareMessage(base64) shouldBe null
        }

        test("max-size PSBT is accepted") {
            val maxed = ByteArray(NfcPsbtCodec.MAX_PSBT_BYTES) { it.toByte() }
            val base64 = java.util.Base64.getEncoder().encodeToString(maxed)
            val message = NfcPsbtCodec.buildShareMessage(base64)
            (message != null) shouldBe true
        }
    }
})
