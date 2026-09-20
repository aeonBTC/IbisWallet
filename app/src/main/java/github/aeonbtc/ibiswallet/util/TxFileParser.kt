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

    /** Input is ambiguous (valid as both hex and base64) — caller must reject. */
    AMBIGUOUS,
}

/** Which network a parsed transaction payload belongs to. */
enum class TxFileNetwork {
    BITCOIN,
    LIQUID,
    UNKNOWN,
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
        // Fail closed on ambiguous encodings: a payload that decodes as both
        // plausible hex and plausible base64 must not be guessed — the caller
        // rejects AMBIGUOUS and asks the user to re-export in a single format.
        // This prevents a crafted file/QR from flipping between BTC and Liquid
        // parsing or between PSBT and raw-tx handling.
        if (isPlausibleHex(text) && isPlausibleBase64(text)) {
            return TxFileResult(text, TxFileFormat.AMBIGUOUS)
        }
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

/**
 * Network binding for a parsed payload so L1 PSBT flows never accept a Liquid
 * PSET and vice versa. Binary magics are authoritative; text payloads are
 * classified by distinctive prefixes (psbt/pset base64 magics) and hex length.
 */
fun TxFileResult.detectedNetwork(): TxFileNetwork =
    when (format) {
        TxFileFormat.PSBT_BINARY -> TxFileNetwork.BITCOIN
        TxFileFormat.PSET_BINARY -> TxFileNetwork.LIQUID
        TxFileFormat.RAW_TX_BINARY -> TxFileNetwork.UNKNOWN
        TxFileFormat.AMBIGUOUS -> TxFileNetwork.UNKNOWN
        TxFileFormat.TEXT -> {
            val t = data.trim()
            // Base64 of binary PSBT starts with "cHNidP8=" ("psbt\xff"); PSET with "cHNldP8=".
            when {
                t.startsWith("cHNidP8=") -> TxFileNetwork.BITCOIN
                t.startsWith("cHNldP8=") -> TxFileNetwork.LIQUID
                else -> TxFileNetwork.UNKNOWN
            }
        }
    }

/** True when the payload must be rejected before sign/broadcast. */
fun TxFileResult.requiresRejection(): Boolean = format == TxFileFormat.AMBIGUOUS || data.isBlank()

private fun isPlausibleHex(text: String): Boolean {
    val t = text.trim()
    if (t.length < 16 || t.length % 2 != 0 || t.length > 200_000) return false
    return t.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }
}

private fun isPlausibleBase64(text: String): Boolean {
    val t = text.trim().replace("\\s".toRegex(), "")
    if (t.length < 16 || t.length % 4 == 1 || t.length > 300_000) return false
    if (!t.all { it in 'A'..'Z' || it in 'a'..'z' || it in '0'..'9' || it == '+' || it == '/' || it == '=' }) {
        return false
    }
    return try {
        val decoded = Base64.decode(t, Base64.DEFAULT)
        decoded.size >= 5
    } catch (_: Exception) {
        false
    }
}
