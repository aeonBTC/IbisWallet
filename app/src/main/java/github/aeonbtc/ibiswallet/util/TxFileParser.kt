package github.aeonbtc.ibiswallet.util

import android.util.Base64

/**
 * Result of parsing a transaction file.
 * [data] is always a string ready for broadcast:
 *   - hex string for raw transactions
 *   - base64 string for PSBTs
 */
data class TxFileResult(
    val data: String,
    val format: TxFileFormat
)

enum class TxFileFormat {
    /** Binary PSBT detected (magic bytes 0x70736274ff), returned as base64 */
    PSBT_BINARY,
    /** Raw binary transaction (.txn), returned as hex */
    RAW_TX_BINARY,
    /** Text content (base64 PSBT, hex tx, or plain text), returned as-is trimmed */
    TEXT
}

/**
 * Parse raw file bytes into a broadcast-ready string.
 *
 * Handles:
 * - **Binary PSBT** (.psbt) — detected by magic bytes `psbt\xff` (0x70736274FF).
 *   Returned as base64 string.
 * - **Binary raw transaction** (.txn) — detected when content is not valid UTF-8 text
 *   or when valid UTF-8 but not plausible hex/base64 (contains control chars, etc.).
 *   Returned as hex string.
 * - **Text files** (.txt, or any text-encoded .psbt/.txn) — base64 PSBT, hex raw tx,
 *   or other text. Returned as-is (trimmed).
 */
fun parseTxFileBytes(bytes: ByteArray): TxFileResult? {
    if (bytes.isEmpty()) return null
    
    // Check for PSBT magic: "psbt" + 0xFF separator (5 bytes: 70 73 62 74 FF)
    if (bytes.size >= 5 &&
        bytes[0] == 0x70.toByte() && bytes[1] == 0x73.toByte() &&
        bytes[2] == 0x62.toByte() && bytes[3] == 0x74.toByte() &&
        bytes[4] == 0xFF.toByte()
    ) {
        val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
        return TxFileResult(base64, TxFileFormat.PSBT_BINARY)
    }
    
    // Try interpreting as UTF-8 text
    val text = try {
        val decoded = String(bytes, Charsets.UTF_8)
        // Check if it's plausible text: no control chars other than whitespace/newline
        val trimmed = decoded.trim()
        if (trimmed.isEmpty()) return null
        val hasControlChars = trimmed.any { ch ->
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
