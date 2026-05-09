package github.aeonbtc.ibiswallet.nfc

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe

class Type4NdefApduEmulatorTest : FunSpec({
    context("Type 4 NDEF APDU interop") {
        test("SELECT AID accepts short-Le variants from peer readers") {
            val emulator = Type4NdefApduEmulator { byteArrayOf(0x11, 0x22, 0x33) }

            val response =
                emulator.processCommandApdu(
                    byteArrayOf(
                        0x00,
                        0xA4.toByte(),
                        0x04,
                        0x00,
                        0x07,
                        0xD2.toByte(), 0x76, 0x00, 0x00, 0x85.toByte(), 0x01, 0x01,
                        0x10,
                    ),
                )

            statusWord(response) shouldContainExactly SW_OK.toList()
        }

        test("READ BINARY with Le zero returns the full CC file") {
            val emulator = Type4NdefApduEmulator { byteArrayOf(0x01, 0x02, 0x03, 0x04) }

            emulator.processCommandApdu(selectAid())
            emulator.processCommandApdu(selectFile(0xE1.toByte(), 0x03))
            val response = emulator.processCommandApdu(readBinary(offset = 0, length = 0))

            statusWord(response) shouldContainExactly SW_OK.toList()
            payload(response).take(2) shouldContainExactly listOf(0x00.toByte(), 0x0F)
            payload(response).size shouldBe 15
        }

        test("extended READ BINARY works for NDEF file reads") {
            val emulator = Type4NdefApduEmulator { byteArrayOf(0x01, 0x02, 0x03) }

            emulator.processCommandApdu(selectAid())
            emulator.processCommandApdu(selectFile(0xE1.toByte(), 0x04, trailingLe = byteArrayOf(0x00)))
            val response = emulator.processCommandApdu(readBinaryExtended(offset = 0, length = 2))

            statusWord(response) shouldContainExactly SW_OK.toList()
            payload(response) shouldContainExactly listOf(0x00.toByte(), 0x03)
        }

        test("unknown AID is rejected") {
            val emulator = Type4NdefApduEmulator { byteArrayOf(0x01) }

            val response =
                emulator.processCommandApdu(
                    byteArrayOf(
                        0x00,
                        0xA4.toByte(),
                        0x04,
                        0x00,
                        0x07,
                        0xD2.toByte(), 0x76, 0x00, 0x00, 0x85.toByte(), 0x01, 0x02,
                    ),
                )

            statusWord(response) shouldContainExactly SW_NOT_FOUND.toList()
        }
    }
}) {
    companion object {
        private val SW_OK = byteArrayOf(0x90.toByte(), 0x00)
        private val SW_NOT_FOUND = byteArrayOf(0x6A.toByte(), 0x82.toByte())
    }
}

private fun selectAid(): ByteArray =
    byteArrayOf(
        0x00,
        0xA4.toByte(),
        0x04,
        0x00,
        0x07,
        0xD2.toByte(), 0x76, 0x00, 0x00, 0x85.toByte(), 0x01, 0x01,
    )

private fun selectFile(
    high: Byte,
    low: Byte,
    trailingLe: ByteArray = byteArrayOf(),
): ByteArray =
    byteArrayOf(
        0x00,
        0xA4.toByte(),
        0x00,
        0x0C,
        0x02,
        high,
        low,
    ) + trailingLe

private fun readBinary(offset: Int, length: Int): ByteArray =
    byteArrayOf(
        0x00,
        0xB0.toByte(),
        ((offset shr 8) and 0xFF).toByte(),
        (offset and 0xFF).toByte(),
        (length and 0xFF).toByte(),
    )

private fun readBinaryExtended(offset: Int, length: Int): ByteArray =
    byteArrayOf(
        0x00,
        0xB0.toByte(),
        ((offset shr 8) and 0xFF).toByte(),
        (offset and 0xFF).toByte(),
        0x00,
        ((length shr 8) and 0xFF).toByte(),
        (length and 0xFF).toByte(),
    )

private fun payload(response: ByteArray): List<Byte> = response.dropLast(2)

private fun statusWord(response: ByteArray): List<Byte> = response.takeLast(2)
