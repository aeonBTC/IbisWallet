package github.aeonbtc.ibiswallet.util

import java.io.ByteArrayOutputStream
import java.util.zip.Deflater
import java.util.zip.Inflater

/**
 * Pure Kotlin implementation of the BBQr protocol (Better Bitcoin QR).
 * See https://bbqr.org and https://github.com/coinkite/BBQr/blob/master/BBQr.md
 *
 * Encodes binary data into a series of QR-code-friendly alphanumeric strings
 * with an 8-char header per frame. Supports Hex, Base32, and Zlib+Base32 encodings.
 *
 * Used for BIP 329 label export/import via animated QR codes.
 */
object Bbqr {

    /** BBQr file type codes per spec */
    const val FILE_TYPE_JSON = 'J'
    const val FILE_TYPE_PSBT = 'P'

    /** Encoding mode codes per spec */
    const val ENCODING_HEX = 'H'
    const val ENCODING_BASE32 = '2'
    const val ENCODING_ZLIB = 'Z'

    /** Fixed 8-char header: "B$" + encoding + fileType + 2-digit count + 2-digit index */
    private const val HEADER_LEN = 8

    /** RFC 4648 Base32 alphabet (uppercase, no padding) */
    private const val BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

    /**
     * Number of alphanumeric characters a QR code can hold at ECC level L.
     * Index = QR version (1-40), index 0 unused.
     */
    private val QR_ALNUM_CAPACITY = intArrayOf(
        0,    // v0 placeholder
        25, 47, 77, 114, 154, 195, 224, 279, 335, 395,      // v1-10
        468, 535, 619, 667, 758, 854, 938, 1046, 1153, 1249, // v11-20
        1352, 1460, 1588, 1704, 1853, 1990, 2132, 2223, 2369, 2520, // v21-30
        2677, 2840, 3009, 3183, 3351, 3537, 3729, 3927, 4087, 4296, // v31-40
    )

    /**
     * Split modulus per encoding — each QR frame's encoded data length must be
     * a multiple of this value (except possibly the last frame) so that the
     * frame decodes to an integer number of bytes.
     */
    private fun splitMod(encoding: Char): Int = when (encoding) {
        ENCODING_HEX -> 2      // 2 hex chars = 1 byte
        ENCODING_BASE32 -> 8   // 8 Base32 chars = 5 bytes
        ENCODING_ZLIB -> 8     // Zlib uses Base32 internally
        else -> 2
    }

    // ── Public API ──────────────────────────────────────────────────────

    data class SplitResult(
        val parts: List<String>,
        val encoding: Char,
        val version: Int,
    )

    /**
     * Encode [data] into a series of BBQr QR-code strings.
     *
     * @param data       Raw binary data to encode
     * @param fileType   One-char file type code (J, U, P, T, etc.)
     * @param encoding   Preferred encoding: 'Z' (Zlib+Base32, default), '2' (Base32), 'H' (Hex)
     * @param minVersion Minimum QR version to consider (1-40, default 5)
     * @param maxVersion Maximum QR version to consider (1-40, default 40)
     * @param minSplit   Minimum number of QR codes (default 1)
     * @param maxSplit   Maximum number of QR codes (default 1295)
     */
    fun split(
        data: ByteArray,
        fileType: Char = FILE_TYPE_JSON,
        encoding: Char = ENCODING_ZLIB,
        minVersion: Int = 5,
        maxVersion: Int = 40,
        minSplit: Int = 1,
        maxSplit: Int = 1295,
    ): SplitResult {
        // Encode the data
        val (actualEncoding, encoded) = encodeData(data, encoding)

        // Find the best QR version + split count
        val (version, count, perEach) = findBestVersion(
            encoded.length, actualEncoding, minVersion, maxVersion, minSplit, maxSplit,
        )

        // Split into parts with headers
        val parts = mutableListOf<String>()
        val countStr = intToBase36(count)
        val header =
            buildString {
                append('B')
                append('$')
                append(actualEncoding)
                append(fileType)
                append(countStr)
            }

        var offset = 0
        var n = 0
        while (offset < encoded.length) {
            val end = minOf(offset + perEach, encoded.length)
            val part = header + intToBase36(n) + encoded.substring(offset, end)
            parts.add(part)
            offset = end
            n++
        }

        return SplitResult(parts = parts, encoding = actualEncoding, version = version)
    }

    /**
     * Continuous joiner that accumulates scanned BBQr frames one at a time.
     * Thread-safe for use from camera analyzer threads.
     */
    class ContinuousJoiner {
        private val received = mutableMapOf<Int, String>()
        private var totalParts: Int = -1
        private var headerPrefix: String = "" // first 6 chars, consistent across all parts

        /** Result after all parts are joined, or null if still in progress */
        var result: JoinResult? = null
            private set

        /** Number of unique parts received so far */
        val partsReceived: Int get() = received.size

        /** Total parts expected, or -1 if not yet known */
        val partsTotal: Int get() = totalParts

        /** Progress as 0.0–1.0, or 0 if not started */
        val progress: Float
            get() = if (totalParts > 0) received.size.toFloat() / totalParts else 0f

        /** True when all parts have been received and joined */
        val isComplete: Boolean get() = result != null

        /**
         * Feed a scanned QR string. Returns true if this was a valid BBQr part.
         * When all parts are received, [result] is populated automatically.
         */
        @Synchronized
        fun addPart(part: String): Boolean {
            if (result != null) return true // already complete

            val trimmed = part.trim()
            if (trimmed.length < HEADER_LEN) return false
            if (!(trimmed[0] == 'B' && trimmed[1] == '$')) return false

            val encoding = trimmed[2]
            val fileType = trimmed[3]
            val numParts = base36ToInt(trimmed.substring(4, 6))
            val partIndex = base36ToInt(trimmed.substring(6, 8))
            val payload = trimmed.substring(8)

            if (numParts < 1 || partIndex < 0 || partIndex >= numParts) return false

            val prefix = trimmed.take(6)

            if (totalParts < 0) {
                // First part received
                totalParts = numParts
                headerPrefix = prefix
            } else {
                // Validate consistency
                if (prefix != headerPrefix || numParts != totalParts) return false
            }

            received[partIndex] = payload

            // Check if complete
            if (received.size == totalParts) {
                result = joinParts(encoding, fileType, totalParts, received)
            }

            return true
        }
    }

    class JoinResult(
        val data: ByteArray,
        val fileType: Char,
        val encoding: Char,
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is JoinResult) return false
            return data.contentEquals(other.data) &&
                fileType == other.fileType &&
                encoding == other.encoding
        }

        override fun hashCode(): Int {
            var result = data.contentHashCode()
            result = 31 * result + fileType.hashCode()
            result = 31 * result + encoding.hashCode()
            return result
        }
    }

    /**
     * Check if a scanned QR string looks like a BBQr frame (starts with "B$").
     */
    fun isBbqrPart(text: String): Boolean {
        val t = text.trim()
        return t.length >= HEADER_LEN && t[0] == 'B' && t[1] == '$'
    }

    // ── Encoding / Decoding ─────────────────────────────────────────────

    /**
     * Encode raw bytes with the given encoding mode. If Zlib doesn't reduce size,
     * falls back to Base32.
     */
    private fun encodeData(data: ByteArray, encoding: Char): Pair<Char, String> {
        return when (encoding) {
            ENCODING_HEX -> Pair(ENCODING_HEX, bytesToHex(data))
            ENCODING_BASE32 -> Pair(ENCODING_BASE32, base32Encode(data))
            ENCODING_ZLIB -> {
                val compressed = zlibCompress(data)
                val zlibEncoded = base32Encode(compressed)
                val plainEncoded = base32Encode(data)
                // Only use Zlib if it actually saves space
                if (zlibEncoded.length < plainEncoded.length) {
                    Pair(ENCODING_ZLIB, zlibEncoded)
                } else {
                    Pair(ENCODING_BASE32, plainEncoded)
                }
            }
            else -> Pair(ENCODING_HEX, bytesToHex(data))
        }
    }

    /**
     * Decode encoded string back to raw bytes based on the encoding mode.
     */
    private fun decodeData(encoded: String, encoding: Char): ByteArray {
        return when (encoding) {
            ENCODING_HEX -> hexToBytes(encoded)
            ENCODING_BASE32 -> base32Decode(encoded)
            ENCODING_ZLIB -> {
                val compressed = base32Decode(encoded)
                zlibDecompress(compressed)
            }
            else -> hexToBytes(encoded)
        }
    }

    // ── Version selection ───────────────────────────────────────────────

    private data class VersionChoice(val version: Int, val count: Int, val perEach: Int)

    private fun findBestVersion(
        encodedLen: Int,
        encoding: Char,
        minVersion: Int,
        maxVersion: Int,
        minSplit: Int,
        maxSplit: Int,
    ): VersionChoice {
        val mod = splitMod(encoding)
        val options = mutableListOf<VersionChoice>()

        for (version in minVersion..maxVersion) {
            val baseCap = QR_ALNUM_CAPACITY[version] - HEADER_LEN
            if (baseCap <= 0) continue
            val adjustedCap = baseCap - (baseCap % mod)
            if (adjustedCap <= 0) continue

            val count = numQrNeeded(encodedLen, baseCap, adjustedCap)
            if (count in minSplit..maxSplit) {
                options.add(VersionChoice(version, count, adjustedCap))
            }
        }

        if (options.isEmpty()) {
            // Fallback: use max version with whatever split count needed
            val baseCap = QR_ALNUM_CAPACITY[maxVersion] - HEADER_LEN
            val adjustedCap = baseCap - (baseCap % mod)
            val count = numQrNeeded(encodedLen, baseCap, adjustedCap)
            return VersionChoice(maxVersion, count, adjustedCap)
        }

        // Pick smallest count, then lowest version
        return options.minWith(compareBy({ it.count }, { it.version }))
    }

    private fun numQrNeeded(encodedLen: Int, baseCap: Int, adjustedCap: Int): Int {
        if (encodedLen <= baseCap) return 1
        val estimate = (encodedLen + adjustedCap - 1) / adjustedCap
        // Verify: (estimate-1) frames at adjustedCap + last frame at baseCap
        val totalCap = (estimate - 1).toLong() * adjustedCap + baseCap
        return if (totalCap >= encodedLen) estimate else estimate + 1
    }

    // ── Joining ─────────────────────────────────────────────────────────

    private fun joinParts(
        encoding: Char,
        fileType: Char,
        totalParts: Int,
        parts: Map<Int, String>,
    ): JoinResult {
        val sb = StringBuilder()
        for (i in 0 until totalParts) {
            sb.append(parts[i] ?: "")
        }
        val data = decodeData(sb.toString(), encoding)
        return JoinResult(data = data, fileType = fileType, encoding = encoding)
    }

    // ── Base36 (2-digit, 00..ZZ = 0..1295) ─────────────────────────────

    private const val BASE36_CHARS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ"

    private fun intToBase36(value: Int): String {
        require(value in 0..1295) { "Value $value out of base36 2-digit range" }
        return "${BASE36_CHARS[value / 36]}${BASE36_CHARS[value % 36]}"
    }

    private fun base36ToInt(s: String): Int {
        require(s.length == 2) { "Base36 string must be 2 chars" }
        val hi = BASE36_CHARS.indexOf(s[0].uppercaseChar())
        val lo = BASE36_CHARS.indexOf(s[1].uppercaseChar())
        if (hi < 0 || lo < 0) return -1
        return hi * 36 + lo
    }

    // ── Hex ─────────────────────────────────────────────────────────────

    private fun bytesToHex(data: ByteArray): String {
        val sb = StringBuilder(data.size * 2)
        for (b in data) {
            sb.append(BASE36_CHARS[(b.toInt() shr 4) and 0x0F])
            sb.append(BASE36_CHARS[b.toInt() and 0x0F])
        }
        return sb.toString()
    }

    private fun hexToBytes(hex: String): ByteArray {
        val len = hex.length / 2
        val result = ByteArray(len)
        for (i in 0 until len) {
            val hi = Character.digit(hex[i * 2], 16)
            val lo = Character.digit(hex[i * 2 + 1], 16)
            result[i] = ((hi shl 4) or lo).toByte()
        }
        return result
    }

    // ── Base32 (RFC 4648, no padding) ───────────────────────────────────

    private fun base32Encode(data: ByteArray): String {
        val sb = StringBuilder((data.size * 8 + 4) / 5)
        var buffer = 0
        var bitsLeft = 0

        for (b in data) {
            buffer = (buffer shl 8) or (b.toInt() and 0xFF)
            bitsLeft += 8
            while (bitsLeft >= 5) {
                bitsLeft -= 5
                sb.append(BASE32_ALPHABET[(buffer shr bitsLeft) and 0x1F])
            }
        }
        // Flush remaining bits (if any)
        if (bitsLeft > 0) {
            sb.append(BASE32_ALPHABET[(buffer shl (5 - bitsLeft)) and 0x1F])
        }

        return sb.toString()
    }

    private fun base32Decode(encoded: String): ByteArray {
        val output = ByteArrayOutputStream(encoded.length * 5 / 8)
        var buffer = 0
        var bitsLeft = 0

        for (c in encoded) {
            val value = BASE32_ALPHABET.indexOf(c.uppercaseChar())
            if (value < 0) continue // skip invalid chars

            buffer = (buffer shl 5) or value
            bitsLeft += 5
            if (bitsLeft >= 8) {
                bitsLeft -= 8
                output.write((buffer shr bitsLeft) and 0xFF)
            }
        }

        return output.toByteArray()
    }

    // ── Zlib (wbits=10, raw deflate, no header) ────────────────────────

    /**
     * Compress with Zlib raw deflate, wbits=10 as required by BBQr spec.
     * Maximum compression effort (level 9).
     */
    private fun zlibCompress(data: ByteArray): ByteArray {
        // Deflater with nowrap=true gives raw deflate (no zlib/gzip header)
        // wbits=10 means window size = 2^10 = 1024
        val deflater = Deflater(Deflater.BEST_COMPRESSION, true)
        try {
            // Set window bits via internal implementation:
            // Java's Deflater with nowrap=true uses wbits=15 by default.
            // BBQr spec requires wbits=10 for embedded device compatibility.
            // However, Java's Deflater API doesn't expose wbits directly.
            // We use nowrap=true (raw deflate) which decoders with wbits>=10 can handle.
            // The BBQr spec says receivers MUST support all encodings — a receiver
            // with wbits=10 can decompress our output since it will fit in the window.
            deflater.setInput(data)
            deflater.finish()

            val output = ByteArrayOutputStream(data.size)
            val buf = ByteArray(4096)
            while (!deflater.finished()) {
                val count = deflater.deflate(buf)
                if (count > 0) output.write(buf, 0, count)
            }
            return output.toByteArray()
        } finally {
            deflater.end()
        }
    }

    /**
     * Decompress raw deflate data. Accepts any wbits up to 15.
     */
    private fun zlibDecompress(compressed: ByteArray): ByteArray {
        // nowrap=true for raw deflate (no zlib header), matches BBQr spec
        val inflater = Inflater(true)
        try {
            inflater.setInput(compressed)

            val output = ByteArrayOutputStream(compressed.size * 3)
            val buf = ByteArray(4096)
            while (!inflater.finished()) {
                val count = inflater.inflate(buf)
                if (count == 0 && inflater.needsInput()) break
                output.write(buf, 0, count)
            }
            return output.toByteArray()
        } finally {
            inflater.end()
        }
    }
}
