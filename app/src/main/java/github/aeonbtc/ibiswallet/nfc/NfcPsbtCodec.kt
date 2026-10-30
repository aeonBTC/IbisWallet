package github.aeonbtc.ibiswallet.nfc

import java.security.MessageDigest

/**
 * Coldcard-compatible NFC record codec for PSBT signing.
 *
 * Coldcard (Mk4/Q) exposes an NFC-V Type 5 tag whose NDEF message uses
 * NFC Forum external-type records (`urn:nfc:ext:bitcoin.org:*`):
 * a text label, an optional SHA256 checksum, then the binary PSBT (or
 * finalized transaction). See Coldcard firmware `docs/nfc-coldcard.md`.
 *
 * This codec is pure JVM (no `android.nfc` dependency) so it is unit
 * tested on the local JVM. It encodes/decodes raw NDEF message bytes;
 * the Android layer ([NfcPsbtExchange]) moves those bytes to/from tags.
 * Payload bytes are never logged.
 */
object NfcPsbtCodec {
    const val EXT_TYPE_PSBT = "bitcoin.org:psbt"
    const val EXT_TYPE_TXN = "bitcoin.org:txn"
    const val EXT_TYPE_SHA256 = "bitcoin.org:sha256"
    const val EXT_TYPE_TXID = "bitcoin.org:txid"

    const val EXPORT_LABEL = "Partly signed PSBT"

    /**
     * Coldcard tag memory is up to 8 KB. Cap the PSBT payload so the full
     * NDEF message (records + headers) stays transferable. Larger PSBTs
     * must use animated QR or file export.
     */
    const val MAX_PSBT_BYTES = 7_500

    private const val TNF_WELL_KNOWN = 0x01
    private const val TNF_EXTERNAL = 0x04

    fun isExportableSize(psbtBytes: ByteArray): Boolean =
        psbtBytes.isNotEmpty() && psbtBytes.size <= MAX_PSBT_BYTES

    /**
     * Build an NDEF message: [text label, sha256, psbt].
     * @throws IllegalArgumentException when the payload is empty or oversized.
     */
    fun buildPsbtMessage(psbtBytes: ByteArray): ByteArray {
        require(psbtBytes.isNotEmpty()) { "PSBT is empty" }
        require(isExportableSize(psbtBytes)) { "PSBT too large for NFC" }
        val digest = MessageDigest.getInstance("SHA-256").digest(psbtBytes)
        val records =
            listOf(
                NdefRecordSpec(
                    tnf = TNF_WELL_KNOWN,
                    type = "T".toByteArray(Charsets.US_ASCII),
                    payload = textPayload(EXPORT_LABEL),
                ),
                NdefRecordSpec(
                    tnf = TNF_EXTERNAL,
                    type = EXT_TYPE_SHA256.toByteArray(Charsets.US_ASCII),
                    payload = digest,
                ),
                NdefRecordSpec(
                    tnf = TNF_EXTERNAL,
                    type = EXT_TYPE_PSBT.toByteArray(Charsets.US_ASCII),
                    payload = psbtBytes,
                ),
            )
        return encodeMessage(records)
    }

    /**
     * Build the exact NDEF message bytes for HCE phone-to-phone PSBT sharing
     * from a base64 PSBT string. Same Coldcard-compatible framing as
     * [buildPsbtMessage], so the importing phone parses it with [parseMessage]
     * unchanged. Returns null when the input is blank, not valid base64, or
     * oversized for NFC — callers must refuse to arm HCE sharing then.
     */
    fun buildShareMessage(psbtBase64: String): ByteArray? {
        val trimmed = psbtBase64.trim()
        if (trimmed.isEmpty()) return null
        val psbtBytes =
            try {
                java.util.Base64.getDecoder().decode(trimmed)
            } catch (_: Exception) {
                return null
            }
        if (!isExportableSize(psbtBytes)) return null
        return try {
            buildPsbtMessage(psbtBytes)
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    sealed interface ParseResult {
        data class Psbt(
            val bytes: ByteArray,
            val checksumVerified: Boolean,
        ) : ParseResult

        data class Txn(
            val rawHex: String,
            val checksumVerified: Boolean,
        ) : ParseResult

        /** Plain-text fallback (generic tags sharing base64/hex as text). */
        data class TextPayload(val text: String) : ParseResult

        data class Failure(val reason: ParseFailure) : ParseResult
    }

    enum class ParseFailure {
        MALFORMED,
        EMPTY,
        NO_USABLE_RECORD,
        CHECKSUM_MISMATCH,
    }

    /**
     * Parse raw NDEF message bytes. Prefers `bitcoin.org:psbt`, then
     * `bitcoin.org:txn`, then a text payload. A SHA256 record associated
     * with a binary payload (the record Coldcard places directly before
     * it) is verified; mismatch fails closed (likely NFC corruption).
     */
    fun parseMessage(messageBytes: ByteArray): ParseResult {
        val records =
            try {
                decodeMessage(messageBytes)
            } catch (_: Exception) {
                return ParseResult.Failure(ParseFailure.MALFORMED)
            }
        if (records.isEmpty()) return ParseResult.Failure(ParseFailure.EMPTY)

        var pendingSha256: ByteArray? = null
        var psbt: ByteArray? = null
        var psbtChecksum: ByteArray? = null
        var txn: ByteArray? = null
        var txnChecksum: ByteArray? = null
        var textFallback: String? = null

        for (record in records) {
            when {
                record.tnf == TNF_EXTERNAL && record.type.contentEquals(EXT_TYPE_SHA256_BYTES) -> {
                    if (record.payload.size == 32) pendingSha256 = record.payload
                }
                record.tnf == TNF_EXTERNAL && record.type.contentEquals(EXT_TYPE_PSBT_BYTES) -> {
                    if (psbt == null) {
                        psbt = record.payload
                        psbtChecksum = pendingSha256
                    }
                    pendingSha256 = null
                }
                record.tnf == TNF_EXTERNAL && record.type.contentEquals(EXT_TYPE_TXN_BYTES) -> {
                    if (txn == null) {
                        txn = record.payload
                        txnChecksum = pendingSha256
                    }
                    pendingSha256 = null
                }
                record.tnf == TNF_EXTERNAL && record.type.contentEquals(EXT_TYPE_TXID_BYTES) -> {
                    pendingSha256 = null
                }
                record.tnf == TNF_WELL_KNOWN && record.type.contentEquals(RTD_TEXT_BYTES) -> {
                    if (textFallback == null) {
                        decodeTextPayload(record.payload)?.takeIf { it.isNotBlank() }?.let {
                            textFallback = it
                        }
                    }
                    // A text record is not binary data; it does not consume a pending checksum.
                }
                else -> {
                    pendingSha256 = null
                }
            }
        }

        psbt?.takeIf { it.isNotEmpty() }?.let { bytes ->
            val checksum = psbtChecksum
            if (checksum != null) {
                val actual = MessageDigest.getInstance("SHA-256").digest(bytes)
                if (!actual.contentEquals(checksum)) {
                    return ParseResult.Failure(ParseFailure.CHECKSUM_MISMATCH)
                }
                return ParseResult.Psbt(bytes, checksumVerified = true)
            }
            return ParseResult.Psbt(bytes, checksumVerified = false)
        }
        txn?.takeIf { it.isNotEmpty() }?.let { bytes ->
            val checksum = txnChecksum
            if (checksum != null) {
                val actual = MessageDigest.getInstance("SHA-256").digest(bytes)
                if (!actual.contentEquals(checksum)) {
                    return ParseResult.Failure(ParseFailure.CHECKSUM_MISMATCH)
                }
                return ParseResult.Txn(bytes.toHexString(), checksumVerified = true)
            }
            return ParseResult.Txn(bytes.toHexString(), checksumVerified = false)
        }
        textFallback?.let { return ParseResult.TextPayload(it.trim()) }
        return ParseResult.Failure(ParseFailure.NO_USABLE_RECORD)
    }

    /**
     * Map a parse result to the string form accepted by
     * `WalletViewModel.setSignedTransactionData` (base64 PSBT or raw hex).
     * Returns null for failures.
     */
    fun toSignedData(result: ParseResult): String? =
        when (result) {
            is ParseResult.Psbt ->
                java.util.Base64.getEncoder().encodeToString(result.bytes)
            is ParseResult.Txn -> result.rawHex
            is ParseResult.TextPayload -> result.text.takeIf { it.isNotBlank() }
            is ParseResult.Failure -> null
        }

    private val EXT_TYPE_SHA256_BYTES = EXT_TYPE_SHA256.toByteArray(Charsets.US_ASCII)
    private val EXT_TYPE_PSBT_BYTES = EXT_TYPE_PSBT.toByteArray(Charsets.US_ASCII)
    private val EXT_TYPE_TXN_BYTES = EXT_TYPE_TXN.toByteArray(Charsets.US_ASCII)
    private val EXT_TYPE_TXID_BYTES = EXT_TYPE_TXID.toByteArray(Charsets.US_ASCII)
    private val RTD_TEXT_BYTES = "T".toByteArray(Charsets.US_ASCII)

    private data class NdefRecordSpec(
        val tnf: Int,
        val type: ByteArray,
        val payload: ByteArray,
    )

    private fun textPayload(text: String): ByteArray {
        val lang = "en".toByteArray(Charsets.US_ASCII)
        val textBytes = text.toByteArray(Charsets.UTF_8)
        val payload = ByteArray(1 + lang.size + textBytes.size)
        payload[0] = lang.size.toByte()
        System.arraycopy(lang, 0, payload, 1, lang.size)
        System.arraycopy(textBytes, 0, payload, 1 + lang.size, textBytes.size)
        return payload
    }

    private fun decodeTextPayload(payload: ByteArray): String? {
        if (payload.isEmpty()) return null
        val status = payload[0].toInt() and 0xFF
        val langLength = status and 0x3F
        if (payload.size <= 1 + langLength) return null
        val encoding = if (status and 0x80 == 0) Charsets.UTF_8 else Charsets.UTF_16
        return String(payload, 1 + langLength, payload.size - 1 - langLength, encoding)
    }

    private fun encodeMessage(records: List<NdefRecordSpec>): ByteArray {
        val out = mutableListOf<Byte>()
        records.forEachIndexed { index, record ->
            val shortRecord = record.payload.size < 256
            var header = record.tnf and 0x07
            if (index == 0) header = header or 0x80
            if (index == records.lastIndex) header = header or 0x40
            if (shortRecord) header = header or 0x10
            out += header.toByte()
            out += record.type.size.toByte()
            if (shortRecord) {
                out += record.payload.size.toByte()
            } else {
                val size = record.payload.size
                out += ((size shr 24) and 0xFF).toByte()
                out += ((size shr 16) and 0xFF).toByte()
                out += ((size shr 8) and 0xFF).toByte()
                out += (size and 0xFF).toByte()
            }
            record.type.forEach { out += it }
            record.payload.forEach { out += it }
        }
        return out.toByteArray()
    }

    private fun decodeMessage(bytes: ByteArray): List<NdefRecordSpec> {
        val records = mutableListOf<NdefRecordSpec>()
        var offset = 0
        var first = true
        while (true) {
            if (offset >= bytes.size) {
                if (first) throw IllegalArgumentException("Empty NDEF message")
                break
            }
            val header = bytes[offset++].toInt() and 0xFF
            val mb = header and 0x80 != 0
            val me = header and 0x40 != 0
            val chunked = header and 0x20 != 0
            val shortRecord = header and 0x10 != 0
            val idPresent = header and 0x08 != 0
            val tnf = header and 0x07
            if (chunked) throw IllegalArgumentException("Chunked records unsupported")
            // TNF 6/7 are reserved; TNF 5/6 never appear outside chunked sequences.
            if (tnf == 0x06 || tnf == 0x07) throw IllegalArgumentException("Reserved TNF")
            if (first && !mb) throw IllegalArgumentException("Missing message-begin flag")
            if (offset >= bytes.size) throw IllegalArgumentException("Truncated record header")
            val typeLength = bytes[offset++].toInt() and 0xFF
            val payloadLength =
                if (shortRecord) {
                    if (offset >= bytes.size) throw IllegalArgumentException("Truncated length")
                    bytes[offset++].toInt() and 0xFF
                } else {
                    if (offset + 4 > bytes.size) throw IllegalArgumentException("Truncated length")
                    val length =
                        ((bytes[offset].toInt() and 0xFF) shl 24) or
                            ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
                            ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
                            (bytes[offset + 3].toInt() and 0xFF)
                    offset += 4
                    if (length < 0) throw IllegalArgumentException("Negative payload length")
                    length
                }
            val idLength =
                if (idPresent) {
                    if (offset >= bytes.size) throw IllegalArgumentException("Truncated ID length")
                    bytes[offset++].toInt() and 0xFF
                } else {
                    0
                }
            if (offset + typeLength + idLength + payloadLength > bytes.size) {
                throw IllegalArgumentException("Record overruns message")
            }
            val type = bytes.copyOfRange(offset, offset + typeLength)
            offset += typeLength
            offset += idLength
            val payload = bytes.copyOfRange(offset, offset + payloadLength)
            offset += payloadLength
            records += NdefRecordSpec(tnf, type, payload)
            first = false
            if (me) break
        }
        return records
    }

    private fun ByteArray.toHexString(): String = joinToString("") { "%02x".format(it) }
}
