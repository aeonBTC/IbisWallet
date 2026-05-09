package github.aeonbtc.ibiswallet.util

/**
 * Blech32 encoding for Liquid/Elements confidential addresses.
 *
 * Blech32 extends BIP-173 Bech32 with a 12-character checksum (vs 6)
 * and encodes the blinding public key alongside the witness program.
 */
internal object Blech32Util {

    private const val CHARSET = "qpzry9x8gf2tvdw0s3jn54khce6mua7l"

    private val GENERATORS =
        longArrayOf(
            0x7d52fba40bd886L,
            0x5e8dbf1a03950cL,
            0x1c3a3c74072a18L,
            0x385d72fa0e5139L,
            0x7093e5a608865bL,
        )

    private fun polymod(values: IntArray): Long {
        var chk = 1L
        for (v in values) {
            val top = chk ushr 55
            chk = ((chk and 0x7fffffffffffffL) shl 5) xor v.toLong()
            for (i in 0 until 5) {
                if (((top ushr i) and 1L) == 1L) {
                    chk = chk xor GENERATORS[i]
                }
            }
        }
        return chk
    }

    private fun hrpExpand(hrp: String): IntArray {
        val result = IntArray(hrp.length * 2 + 1)
        for (i in hrp.indices) {
            result[i] = hrp[i].code ushr 5
        }
        for (i in hrp.indices) {
            result[hrp.length + 1 + i] = hrp[i].code and 31
        }
        return result
    }

    private fun createChecksum(hrp: String, data: IntArray): IntArray {
        val expanded = hrpExpand(hrp)
        val values = IntArray(expanded.size + data.size + 12)
        expanded.copyInto(values)
        data.copyInto(values, expanded.size)
        val mod = polymod(values) xor 1L
        return IntArray(12) { p -> ((mod ushr (5 * (11 - p))) and 31L).toInt() }
    }

    private fun convertBits(data: ByteArray): IntArray {
        val result = mutableListOf<Int>()
        var nextByte = 0
        var filledBits = 0
        val fromBits = 8
        val toBits = 5

        for (byte in data) {
            var b = byte.toInt() and 0xFF
            var remFrom = fromBits
            while (remFrom > 0) {
                val remTo = toBits - filledBits
                val extract = minOf(remFrom, remTo)
                nextByte = (nextByte shl extract) or (b ushr (8 - extract))
                b = (b shl extract) and 0xFF
                remFrom -= extract
                filledBits += extract
                if (filledBits == toBits) {
                    result.add(nextByte)
                    filledBits = 0
                    nextByte = 0
                }
            }
        }

        if (filledBits > 0) {
            result.add(nextByte shl (toBits - filledBits))
        }

        return result.toIntArray()
    }

    /**
     * Encode a confidential Liquid address from its components.
     *
     * @param blindingPubKey 33-byte compressed EC public key
     * @param witnessProgram 20 bytes (P2WPKH) or 32 bytes (P2WSH/P2TR)
     * @param witnessVersion segwit witness version (0 for v0, 1 for taproot)
     * @param isMainnet true → HRP "lq", false → HRP "tlq"
     */
    fun encodeConfidentialAddress(
        blindingPubKey: ByteArray,
        witnessProgram: ByteArray,
        witnessVersion: Int,
        isMainnet: Boolean,
    ): String {
        require(blindingPubKey.size == 33) { "Blinding public key must be 33 bytes" }
        require(witnessVersion in 0..16) { "Invalid witness version: $witnessVersion" }

        val hrp = if (isMainnet) "lq" else "tlq"
        val payload = blindingPubKey + witnessProgram
        val data5bit = convertBits(payload)
        val fullData = intArrayOf(witnessVersion) + data5bit
        val checksum = createChecksum(hrp, fullData)
        val combined = fullData + checksum

        return buildString(hrp.length + 1 + combined.size) {
            append(hrp)
            append('1')
            for (value in combined) {
                append(CHARSET[value])
            }
        }
    }
}
