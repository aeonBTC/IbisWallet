package github.aeonbtc.ibiswallet.util

import android.util.Base64

/**
 * Result of parsing a transaction file.
 * [data] is always a string ready for broadcast/import:
 *   - hex string for raw transactions
 *   - base64 string for PSBTs / PSETs
 */
data class TxFileResult(
    val data: String,
    val format: TxFileFormat,
)

enum class TxFileFormat {
    /** Binary PSBT detected (magic bytes 0x70736274ff), returned as base64 */
    PSBT_BINARY,

    /** Binary Elements PSET detected (magic bytes 0x70736574ff), returned as base64 */
    PSET_BINARY,

    /** Raw binary transaction (.txn), returned as hex */
    RAW_TX_BINARY,

    /** Text content (base64 PSBT/PSET, hex tx, or plain text), returned as-is trimmed */
    TEXT,
}

/**
 * Parse raw file bytes into a broadcast/import-ready string.
 *
 * Handles:
 * - **Binary PSBT** (.psbt) — detected by magic bytes `psbt\xff` (0x70736274FF).
 *   Returned as base64 string.
 * - **Binary PSET** (.pset) — detected by magic bytes `pset\xff` (0x70736574FF).
 *   Returned as base64 string (Liquid / Elements).
 * - **Binary raw transaction** (.txn) — detected when content is not valid UTF-8 text
 *   or when valid UTF-8 but not plausible hex/base64 (contains control chars, etc.).
 *   Returned as hex string.
 * - **Text files** (.txt, or any text-encoded .psbt/.pset/.txn) — base64 PSBT/PSET, hex raw tx,
 *   or other text. Returned as-is (trimmed).
 */
fun parseTxFileBytes(bytes: ByteArray): TxFileResult? {
    if (bytes.isEmpty()) return null

    // Bitcoin PSBT magic: "psbt" + 0xFF (70 73 62 74 FF)
    if (bytes.hasMagicPrefix(0x70, 0x73, 0x62, 0x74, 0xFF)) {
        val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
        return TxFileResult(base64, TxFileFormat.PSBT_BINARY)
    }

    // Elements PSET magic: "pset" + 0xFF (70 73 65 74 FF)
    if (bytes.hasMagicPrefix(0x70, 0x73, 0x65, 0x74, 0xFF)) {
        val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
        return TxFileResult(base64, TxFileFormat.PSET_BINARY)
    }

    // Try interpreting as UTF-8 text
    val text =
        try {
            val decoded = String(bytes, Charsets.UTF_8)
            // Check if it's plausible text: no control chars other than whitespace/newline
            val trimmed = decoded.trim()
            if (trimmed.isEmpty()) return null
            val hasControlChars =
                trimmed.any { ch ->
                    ch.code < 0x20 && ch != '\n' && ch != '\r' && ch != '\t'
                }
            if (hasControlChars) null else trimmed
        } catch (_: Exception) {
            null
        }

    if (text != null) {
        return TxFileResult(text, TxFileFormat.TEXT)
    }

    // Not valid text — treat as binary raw transaction, return as hex
    val hex = bytes.joinToString("") { "%02x".format(it) }
    return TxFileResult(hex, TxFileFormat.RAW_TX_BINARY)
}

private fun ByteArray.hasMagicPrefix(vararg magic: Int): Boolean {
    if (size < magic.size) return false
    for (i in magic.indices) {
        if (this[i] != magic[i].toByte()) return false
    }
    return true
}
