package github.aeonbtc.ibiswallet.util

import android.content.Context
import android.util.Log
import github.aeonbtc.ibiswallet.data.model.AddressType
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.security.MessageDigest

/**
 * Parses QR code content into structured wallet import or server config data.
 *
 * Supported seed/key formats:
 * - Standard SeedQR: 48 or 96 digits (BIP39 word indices, 4 digits each, zero-padded)
 * - CompactSeedQR: 16 or 32 raw bytes (BIP39 entropy as binary QR)
 * - Plain BIP39 mnemonic: space-separated words
 * - Extended public keys: xpub/ypub/zpub/tpub/vpub/upub (bare or with origin info)
 * - Output descriptors: wpkh(...), tr(...), etc.
 * - ColdCard JSON: {"xfp": "...", "p2wpkh": "zpub...", ...}
 * - Specter JSON: {"MasterFingerprint": "...", "ExtPubKey": "xpub...", ...}
 *
 * Supported server formats:
 * - Plain: host:port
 * - Protocol-prefixed: ssl://host:port, tcp://host:port
 */
object QrFormatParser {
    private const val TAG = "QrFormatParser"
    private var cachedWordlist: List<String>? = null

    /**
     * Load the BIP39 English wordlist from assets. Cached after first load.
     */
    fun getWordlist(context: Context): List<String> {
        cachedWordlist?.let { return it }
        val words =
            context.assets.open("bip39_english.txt").use { stream ->
                BufferedReader(InputStreamReader(stream)).readLines()
            }
        cachedWordlist = words
        return words
    }

    /**
     * Parse QR content intended for wallet import (seed phrase or key material).
     * Returns the normalized string to populate the key material field.
     *
     * @param context Android context for loading BIP39 wordlist
     * @param raw The raw scanned QR text
     * @param addressType The address type selected on the import screen,
     *   used for ColdCard JSON to select the matching key
     */
    fun parseWalletQr(
        context: Context,
        raw: String,
        addressType: AddressType = AddressType.SEGWIT,
    ): String {
        val trimmed = raw.trim()

        // 1. Standard SeedQR: all digits, length 48 (12 words) or 96 (24 words)
        if (trimmed.all { it.isDigit() } && trimmed.length in listOf(48, 96)) {
            val mnemonic = decodeSeedQr(context, trimmed)
            if (mnemonic != null) return mnemonic
        }

        // 2. CompactSeedQR: raw binary, 16 bytes (12 words) or 32 bytes (24 words)
        //    ZXing may deliver this as a string with raw byte values.
        //    Check for non-printable chars as a heuristic.
        if (trimmed.length in listOf(16, 32) && trimmed.any { it.code !in 32..126 }) {
            val mnemonic = decodeCompactSeedQr(context, trimmed.toByteArray(Charsets.ISO_8859_1))
            if (mnemonic != null) return mnemonic
        }

        // 3. ColdCard / Specter / generic JSON format
        if (trimmed.startsWith("{")) {
            val parsed = parseColdCardJson(trimmed, addressType)
            if (parsed != null) return parsed
        }

        // 4. Already a plain mnemonic, xpub, descriptor, or origin-prefixed key — pass through
        return trimmed
    }

    /**
     * Parse QR content intended for Electrum server configuration.
     * Strips protocol prefixes and extracts host, port, and SSL flag.
     */
    fun parseServerQr(raw: String): ServerConfig {
        var trimmed = raw.trim()

        // Strip protocol prefix and detect SSL
        var ssl: Boolean? = null
        when {
            trimmed.lowercase().startsWith("ssl://") -> {
                trimmed = trimmed.substring(6)
                ssl = true
            }
            trimmed.lowercase().startsWith("tcp://") -> {
                trimmed = trimmed.substring(6)
                ssl = false
            }
            trimmed.lowercase().startsWith("http://") -> {
                trimmed = trimmed.substring(7)
                ssl = false
            }
            trimmed.lowercase().startsWith("https://") -> {
                trimmed = trimmed.substring(8)
                ssl = true
            }
            trimmed.lowercase().startsWith("electrum://") -> {
                trimmed = trimmed.substring(11)
            }
        }

        // Strip trailing path/slash
        trimmed = trimmed.trimEnd('/')

        // Handle Electrum-style host:port:t/s suffix (t=TCP, s=SSL)
        val electrumSuffix = """:([ts])$""".toRegex(RegexOption.IGNORE_CASE)
        electrumSuffix.find(trimmed)?.let { match ->
            val flag = match.groupValues[1].lowercase()
            if (ssl == null) {
                ssl = (flag == "s")
            }
            trimmed = trimmed.take(match.range.first)
        }

        // Extract host and port
        val lastColon = trimmed.lastIndexOf(':')
        if (lastColon > 0) {
            val potentialPort = trimmed.substring(lastColon + 1)
            val port = potentialPort.toIntOrNull()
            if (port != null && port in 1..65535) {
                val host = trimmed.take(lastColon)
                val useSsl = ssl ?: (port == 50002 || port == 443)
                return ServerConfig(host, port, useSsl)
            }
        }

        // No valid port — return host only with best-guess SSL
        return ServerConfig(trimmed, null, ssl)
    }

    /**
     * Decode a Standard SeedQR digit string into a BIP39 mnemonic.
     * Each word is represented as its 4-digit zero-padded index (0000-2047).
     * 12-word = 48 digits, 24-word = 96 digits.
     */
    private fun decodeSeedQr(
        context: Context,
        digits: String,
    ): String? {
        val wordlist = getWordlist(context)
        if (wordlist.size != 2048) return null

        val wordCount = digits.length / 4
        if (wordCount !in listOf(12, 24)) return null

        val words = mutableListOf<String>()
        for (i in 0 until wordCount) {
            val indexStr = digits.substring(i * 4, i * 4 + 4)
            val index = indexStr.toIntOrNull() ?: return null
            if (index !in 0 until 2048) return null
            words.add(wordlist[index])
        }

        return words.joinToString(" ")
    }

    /**
     * Decode a CompactSeedQR byte array into a BIP39 mnemonic.
     * 16 bytes = 128 bits entropy = 12 words, 32 bytes = 256 bits entropy = 24 words.
     * The checksum is recomputed from the entropy (not stored in the QR).
     */
    private fun decodeCompactSeedQr(
        context: Context,
        bytes: ByteArray,
    ): String? {
        val wordlist = getWordlist(context)
        if (wordlist.size != 2048) return null

        val entropyBits = bytes.size * 8 // 128 or 256
        val checksumBits = entropyBits / 32 // 4 or 8
        val totalBits = entropyBits + checksumBits // 132 or 264
        val wordCount = totalBits / 11 // 12 or 24

        if (wordCount !in listOf(12, 24)) return null

        // Compute SHA-256 checksum of entropy
        val sha256 = MessageDigest.getInstance("SHA-256").digest(bytes)
        val checksumByte = sha256[0].toInt() and 0xFF

        // Build full bitstream: entropy bits + checksum bits
        val bits = BooleanArray(totalBits)

        // Entropy bits
        for (i in 0 until entropyBits) {
            val byteIndex = i / 8
            val bitIndex = 7 - (i % 8)
            bits[i] = ((bytes[byteIndex].toInt() shr bitIndex) and 1) == 1
        }

        // Checksum bits (top N bits of SHA-256 hash)
        for (i in 0 until checksumBits) {
            val bitIndex = 7 - i
            bits[entropyBits + i] = ((checksumByte shr bitIndex) and 1) == 1
        }

        // Extract 11-bit word indices
        val words = mutableListOf<String>()
        for (w in 0 until wordCount) {
            var index = 0
            for (b in 0 until 11) {
                if (bits[w * 11 + b]) {
                    index = index or (1 shl (10 - b))
                }
            }
            if (index !in 0 until 2048) return null
            words.add(wordlist[index])
        }

        return words.joinToString(" ")
    }

    /**
     * Parse ColdCard, Specter, or generic JSON wallet export format.
     *
     * ColdCard format:
     * {"xfp": "0F056943", "p2wpkh": "zpub...", "p2wpkh_deriv": "m/84'/0'/0'", ...}
     *
     * Specter format:
     * {"MasterFingerprint": "37b5eed4", "ExtPubKey": "xpub...", "AccountKeyPath": "84'/0'/0'"}
     *
     * Returns a key-origin string like "[fingerprint/path]xpub..." or null if not valid JSON.
     */
    private fun parseColdCardJson(
        json: String,
        addressType: AddressType,
    ): String? {
        return try {
            val obj = JSONObject(json)

            // Try ColdCard format first
            val coldCardResult = parseColdCardFormat(obj, addressType)
            if (coldCardResult != null) return coldCardResult

            // Try Specter/generic format
            val specterResult = parseSpecterFormat(obj)
            if (specterResult != null) return specterResult

            null
        } catch (e: Exception) {
            Log.d(TAG, "Failed to parse JSON wallet export: ${e.message}")
            null
        }
    }

    /**
     * Parse ColdCard JSON export format.
     * Keys are named by script type: p2wpkh, p2sh_p2wpkh, p2pkh, p2tr
     * Derivation paths are in matching *_deriv fields.
     */
    private fun parseColdCardFormat(
        obj: JSONObject,
        addressType: AddressType,
    ): String? {
        val xfp = obj.optString("xfp", "").takeIf { it.isNotBlank() } ?: return null

        // Map address type to ColdCard JSON field names
        val (keyField, derivField) =
            when (addressType) {
                AddressType.SEGWIT -> "p2wpkh" to "p2wpkh_deriv"
                AddressType.NESTED_SEGWIT -> "p2sh_p2wpkh" to "p2sh_p2wpkh_deriv"
                AddressType.LEGACY -> "p2pkh" to "p2pkh_deriv"
                AddressType.TAPROOT -> "p2tr" to "p2tr_deriv"
            }

        // Try the preferred type first, then fall back to any available key
        var key = obj.optString(keyField, "").takeIf { it.isNotBlank() }
        var deriv = obj.optString(derivField, "").takeIf { it.isNotBlank() }

        if (key == null) {
            // Fall back to first available key in priority order
            val fallbacks =
                listOf(
                    "p2wpkh" to "p2wpkh_deriv",
                    "p2tr" to "p2tr_deriv",
                    "p2sh_p2wpkh" to "p2sh_p2wpkh_deriv",
                    "p2pkh" to "p2pkh_deriv",
                )
            for ((kf, df) in fallbacks) {
                val k = obj.optString(kf, "").takeIf { it.isNotBlank() }
                if (k != null) {
                    key = k
                    deriv = obj.optString(df, "").takeIf { it.isNotBlank() }
                    break
                }
            }
        }

        if (key == null) return null

        // Build key-origin string: [fingerprint/path]key
        val fingerprint = xfp.lowercase()
        val path = deriv?.removePrefix("m/") ?: addressType.accountPath

        return "[$fingerprint/$path]$key"
    }

    /**
     * Parse Specter DIY / generic JSON export format.
     * Uses "MasterFingerprint", "ExtPubKey", and "AccountKeyPath" fields.
     */
    private fun parseSpecterFormat(obj: JSONObject): String? {
        val fingerprint =
            obj.optString("MasterFingerprint", "").takeIf { it.isNotBlank() }
                ?: return null
        val extPubKey =
            obj.optString("ExtPubKey", "").takeIf { it.isNotBlank() }
                ?: return null
        val path = obj.optString("AccountKeyPath", "").takeIf { it.isNotBlank() }

        return if (path != null) {
            "[${fingerprint.lowercase()}/${path.removePrefix("m/")}]$extPubKey"
        } else {
            "[${fingerprint.lowercase()}]$extPubKey"
        }
    }

    data class ServerConfig(
        val host: String,
        val port: Int?,
        val ssl: Boolean?,
    )
}
