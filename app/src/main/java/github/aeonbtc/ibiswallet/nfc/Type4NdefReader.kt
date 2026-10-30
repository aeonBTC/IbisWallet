package github.aeonbtc.ibiswallet.nfc

import android.nfc.Tag
import android.nfc.tech.IsoDep

/**
 * Reads a raw NDEF message from an ISO-DEP (Type 4) tag that does not surface
 * the platform `Ndef` technology — i.e. an HCE peer such as another phone
 * sharing a PSBT via [NdefHostApduService].
 *
 * Mirrors the NFC Forum Type 4 read sequence already used for the address
 * handoff (SELECT AID → CC → NDEF length → chunked READ BINARY), but returns
 * the raw message bytes for [NfcPsbtCodec] instead of extracting a send
 * payload. The advertised length is capped so a malicious peer cannot force
 * a large allocation.
 */
internal object Type4NdefReader {
    private val SW_OK = byteArrayOf(0x90.toByte(), 0x00)

    private val SELECT_AID =
        byteArrayOf(
            0x00, 0xA4.toByte(), 0x04, 0x00, 0x07,
            0xD2.toByte(), 0x76, 0x00, 0x00, 0x85.toByte(), 0x01, 0x01,
        )
    private val SELECT_AID_WITH_LE = SELECT_AID + byteArrayOf(0x00)
    private val SELECT_CC_FILE =
        byteArrayOf(
            0x00, 0xA4.toByte(), 0x00, 0x0C, 0x02, 0xE1.toByte(), 0x03,
        )
    private val READ_CC = byteArrayOf(0x00, 0xB0.toByte(), 0x00, 0x00, 0x0F)
    private val SELECT_NDEF_FILE =
        byteArrayOf(
            0x00, 0xA4.toByte(), 0x00, 0x0C, 0x02, 0xE1.toByte(), 0x04,
        )
    private val READ_NDEF_LENGTH = byteArrayOf(0x00, 0xB0.toByte(), 0x00, 0x00, 0x02)

    private const val READ_CHUNK_SIZE = 59

    /**
     * Upper bound on the NDEF message read from a peer. PSBT shares are
     * capped at [NfcPsbtCodec.MAX_PSBT_BYTES] before framing; this headroom
     * covers framing overhead while bounding malicious length prefixes.
     */
    const val MAX_NDEF_BYTES = 16_384

    fun readNdefMessage(tag: Tag): ByteArray? {
        val isoDep = IsoDep.get(tag) ?: return null
        return try {
            isoDep.timeout = maxOf(isoDep.timeout, 5_000)
            isoDep.connect()

            if (!isoDep.transceive(SELECT_AID).isSuccess() &&
                !isoDep.transceive(SELECT_AID_WITH_LE).isSuccess()
            ) {
                return null
            }
            if (!isoDep.transceive(SELECT_CC_FILE).isSuccess()) return null
            if (!isoDep.transceive(READ_CC).isSuccess()) return null
            if (!isoDep.transceive(SELECT_NDEF_FILE).isSuccess()) return null

            val lengthResponse = isoDep.transceive(READ_NDEF_LENGTH)
            if (!lengthResponse.isSuccess()) return null
            val lengthBytes = lengthResponse.dropLast(2).toByteArray()
            if (lengthBytes.size != 2) return null
            val messageLength = ((lengthBytes[0].toInt() and 0xFF) shl 8) or
                (lengthBytes[1].toInt() and 0xFF)
            if (messageLength <= 0 || messageLength > MAX_NDEF_BYTES) return null

            val messageBytes = ByteArray(messageLength)
            var bytesRead = 0
            var offset = 2
            while (bytesRead < messageLength) {
                val chunkSize = minOf(READ_CHUNK_SIZE, messageLength - bytesRead)
                val response =
                    isoDep.transceive(
                        byteArrayOf(
                            0x00,
                            0xB0.toByte(),
                            ((offset shr 8) and 0xFF).toByte(),
                            (offset and 0xFF).toByte(),
                            chunkSize.toByte(),
                        ),
                    )
                if (!response.isSuccess()) return null
                val chunk = response.dropLast(2).toByteArray()
                if (chunk.isEmpty()) return null
                chunk.copyInto(messageBytes, destinationOffset = bytesRead)
                bytesRead += chunk.size
                offset += chunk.size
            }
            messageBytes
        } catch (_: Exception) {
            null
        } finally {
            runCatching { isoDep.close() }
        }
    }

    private fun ByteArray.isSuccess(): Boolean =
        size >= 2 && takeLast(2).toByteArray().contentEquals(SW_OK)
}
