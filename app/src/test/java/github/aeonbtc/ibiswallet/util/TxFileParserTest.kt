package github.aeonbtc.ibiswallet.util

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockkStatic

class TxFileParserTest : FunSpec({

    beforeSpec {
        // android.util.Base64 throws in JVM tests — mock it
        mockkStatic(android.util.Base64::class)
        every { android.util.Base64.encodeToString(any(), any()) } answers {
            val bytes = firstArg<ByteArray>()
            java.util.Base64.getEncoder().encodeToString(bytes)
        }
    }

    // ── Empty / null input ──

    context("empty input") {
        test("returns null for empty byte array") {
            parseTxFileBytes(ByteArray(0)) shouldBe null
        }
    }

    // ── PSBT detection ──

    context("PSBT binary detection") {
        test("detects PSBT magic bytes and returns base64") {
            // PSBT magic: 0x70 0x73 0x62 0x74 0xFF followed by some data
            val psbtBytes = byteArrayOf(
                0x70, 0x73, 0x62, 0x74, 0xFF.toByte(),
                0x01, 0x02, 0x03,
            )
            val result = parseTxFileBytes(psbtBytes)
            result shouldBe TxFileResult(
                data = java.util.Base64.getEncoder().encodeToString(psbtBytes),
                format = TxFileFormat.PSBT_BINARY,
            )
        }

        test("PSBT requires all 5 magic bytes") {
            // Only 4 bytes of the magic — not a PSBT
            val notPsbt = byteArrayOf(0x70, 0x73, 0x62, 0x74)
            val result = parseTxFileBytes(notPsbt)
            // Should be treated as text (valid UTF-8 "psbt")
            result?.format shouldBe TxFileFormat.TEXT
        }
    }

    // ── Text detection ──

    context("text content") {
        test("returns trimmed text for hex string") {
            val hex = "0200000001abcdef1234567890"
            val result = parseTxFileBytes(hex.toByteArray(Charsets.UTF_8))
            result shouldBe TxFileResult(hex, TxFileFormat.TEXT)
        }

        test("trims whitespace from text") {
            val hex = "  0200000001abcdef  \n"
            val result = parseTxFileBytes(hex.toByteArray(Charsets.UTF_8))
            result?.data shouldBe "0200000001abcdef"
            result?.format shouldBe TxFileFormat.TEXT
        }

        test("returns null for whitespace-only content") {
            val result = parseTxFileBytes("   \n\t  ".toByteArray(Charsets.UTF_8))
            result shouldBe null
        }

        test("returns base64 PSBT text as-is") {
            val base64Psbt = "cHNidP8BAHUCAAAAASaBcTce3/KF6Tta"
            val result = parseTxFileBytes(base64Psbt.toByteArray(Charsets.UTF_8))
            result shouldBe TxFileResult(base64Psbt, TxFileFormat.TEXT)
        }
    }

    // ── Binary raw transaction ──

    context("binary raw transaction") {
        test("returns hex for binary data with control chars") {
            // Binary with control characters that are NOT whitespace (\n \r \t)
            // 0x01 (SOH), 0x03 (ETX), 0x04 (EOT) are control chars that trigger binary detection
            val binary = byteArrayOf(
                0x02, 0x01, 0x03, 0x04, 0x7F, 0x15, 0x06,
            )
            val result = parseTxFileBytes(binary)
            result?.format shouldBe TxFileFormat.RAW_TX_BINARY
            result?.data shouldBe "020103047f1506"
        }
    }
})
