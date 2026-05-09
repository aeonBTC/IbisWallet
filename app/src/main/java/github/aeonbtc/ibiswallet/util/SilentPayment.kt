package github.aeonbtc.ibiswallet.util

import java.math.BigInteger
import java.security.MessageDigest
import java.util.Locale

internal object SilentPayment {
    private const val MAINNET_HRP = "sp"
    private const val BECH32M_CONST = 0x2bc830a3
    private const val CHARSET = "qpzry9x8gf2tvdw0s3jn54khce6mua7l"
    private const val K_MAX = 2323

    private val curveOrder = BigInteger(
        "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEBAAEDCE6AF48A03BBFD25E8CD0364141",
        16,
    )
    private val fieldPrime = BigInteger(
        "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEFFFFFC2F",
        16,
    )
    private val generator = EcPoint(
        x = BigInteger("79BE667EF9DCBBAC55A06295CE870B07029BFCDB2DCE28D959F2815B16F81798", 16),
        y = BigInteger("483ADA7726A3C4655DA4FBFC0E1108A8FD17B448A68554199C47D08FFB10D4B8", 16),
    )

    data class Address(
        val value: String,
        val scanPublicKey: ByteArray,
        val spendPublicKey: ByteArray,
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Address) return false

            return value == other.value &&
                scanPublicKey.contentEquals(other.scanPublicKey) &&
                spendPublicKey.contentEquals(other.spendPublicKey)
        }

        override fun hashCode(): Int {
            var result = value.hashCode()
            result = 31 * result + scanPublicKey.contentHashCode()
            result = 31 * result + spendPublicKey.contentHashCode()
            return result
        }
    }

    data class InputKey(
        val outpoint: String,
        val privateKey: ByteArray,
        val isTaproot: Boolean = false,
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is InputKey) return false

            return outpoint == other.outpoint &&
                privateKey.contentEquals(other.privateKey) &&
                isTaproot == other.isTaproot
        }

        override fun hashCode(): Int {
            var result = outpoint.hashCode()
            result = 31 * result + privateKey.contentHashCode()
            result = 31 * result + isTaproot.hashCode()
            return result
        }
    }

    data class OutputKey(
        val recipientIndex: Int,
        val address: String,
        val xOnlyPublicKey: ByteArray,
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is OutputKey) return false

            return recipientIndex == other.recipientIndex &&
                address == other.address &&
                xOnlyPublicKey.contentEquals(other.xOnlyPublicKey)
        }

        override fun hashCode(): Int {
            var result = recipientIndex
            result = 31 * result + address.hashCode()
            result = 31 * result + xOnlyPublicKey.contentHashCode()
            return result
        }
    }

    fun isSilentPaymentAddress(input: String): Boolean =
        parseAddress(input).isSuccess

    fun parseAddress(input: String): Result<Address> =
        runCatching {
            val trimmed = input.trim()
            val (hrp, data) = decodeBech32m(trimmed)
            require(hrp == MAINNET_HRP) { "Only mainnet silent payment addresses are supported" }
            require(data.isNotEmpty() && data.first() == 0) { "Unsupported silent payment version" }
            val payload = convertBech32Payload(data.drop(1))
            require(payload.size == 66) { "Invalid silent payment payload length" }

            val scan = payload.copyOfRange(0, 33)
            val spend = payload.copyOfRange(33, 66)
            require(decodePublicKey(scan) != null) { "Invalid silent payment scan key" }
            require(decodePublicKey(spend) != null) { "Invalid silent payment spend key" }

            Address(
                value = trimmed.lowercase(Locale.US),
                scanPublicKey = scan,
                spendPublicKey = spend,
            )
        }

    fun placeholderScriptPubKey(): ByteArray =
        taprootScriptPubKey(ByteArray(32) { 1 })

    fun taprootScriptPubKey(xOnlyPublicKey: ByteArray): ByteArray {
        require(xOnlyPublicKey.size == 32) { "Taproot output key must be 32 bytes" }
        return byteArrayOf(0x51, 0x20) + xOnlyPublicKey
    }

    fun createOutputKeys(
        inputKeys: List<InputKey>,
        recipients: List<String>,
    ): List<OutputKey> {
        val parsedRecipients =
            recipients.mapIndexed { index, recipient ->
                index to parseAddress(recipient).getOrThrow()
            }
        val inputScalars =
            inputKeys.mapNotNull { input ->
                val scalar = scalarFromBytes(input.privateKey)
                if (input.isTaproot) {
                    val pubKey = multiplyGenerator(scalar)
                    if (pubKey.y.testBit(0)) curveOrder.subtract(scalar).mod(curveOrder) else scalar
                } else {
                    scalar
                }
            }
        require(inputScalars.isNotEmpty()) { "No eligible inputs for silent payment" }

        val inputScalarSum = inputScalars.fold(BigInteger.ZERO) { acc, scalar ->
            acc.add(scalar).mod(curveOrder)
        }
        require(inputScalarSum != BigInteger.ZERO) { "Invalid silent payment input key sum" }

        val inputPubKeySum = multiplyGenerator(inputScalarSum)
        val inputHash = inputHash(inputKeys.map { it.outpoint }, inputPubKeySum)
        val sharedSecretScalar = inputScalarSum.multiply(inputHash).mod(curveOrder)

        val outputKeys = mutableListOf<OutputKey>()
        parsedRecipients
            .groupBy { it.second.scanPublicKey.toHex() }
            .forEach { (_, group) ->
                require(group.size <= K_MAX) { "Too many silent payment recipients" }
                val scanPoint = decodePublicKey(group.first().second.scanPublicKey)
                    ?: throw IllegalArgumentException("Invalid silent payment scan key")
                val sharedSecret = multiplyPoint(scanPoint, sharedSecretScalar)
                    ?: throw IllegalArgumentException("Invalid silent payment shared secret")

                group.forEachIndexed { groupIndex, indexedRecipient ->
                    val (recipientIndex, recipient) = indexedRecipient
                    val spendPoint = decodePublicKey(recipient.spendPublicKey)
                        ?: throw IllegalArgumentException("Invalid silent payment spend key")
                    val tweak = taggedHashScalar(
                        tag = "BIP0352/SharedSecret",
                        data = serializeCompressed(sharedSecret) + groupIndex.toUInt32Bytes(),
                    )
                    val outputPoint = addPoints(spendPoint, multiplyGenerator(tweak))
                        ?: throw IllegalArgumentException("Invalid silent payment output")
                    outputKeys += OutputKey(
                        recipientIndex = recipientIndex,
                        address = recipient.value,
                        xOnlyPublicKey = outputPoint.x.toByteArray32(),
                    )
                }
            }

        return outputKeys.sortedBy { it.recipientIndex }
    }

    fun deriveTaprootOutputPrivateKey(internalPrivateKey: ByteArray): ByteArray {
        val internalScalar = scalarFromBytes(internalPrivateKey)
        val internalPoint = multiplyGenerator(internalScalar)
        val evenInternalScalar =
            if (internalPoint.y.testBit(0)) curveOrder.subtract(internalScalar).mod(curveOrder) else internalScalar
        val evenInternalPoint = multiplyGenerator(evenInternalScalar)
        val tweak = taggedHashScalar("TapTweak", evenInternalPoint.x.toByteArray32())
        val outputScalar = evenInternalScalar.add(tweak).mod(curveOrder)
        require(outputScalar != BigInteger.ZERO) { "Invalid taproot output key" }
        return outputScalar.toByteArray32()
    }

    fun privateKeyFromWif(wif: String): ByteArray {
        val decoded = BitcoinUtils.Base58.decodeChecked(wif.trim())
        require(decoded.isNotEmpty() && decoded[0].toInt() and 0xFF == 0x80) { "Invalid WIF version" }
        val keyBytes =
            when (decoded.size) {
                33 -> decoded.copyOfRange(1, 33)
                34 -> {
                    require(decoded.last() == 0x01.toByte()) { "Invalid compressed WIF" }
                    decoded.copyOfRange(1, 33)
                }
                else -> throw IllegalArgumentException("Invalid WIF length")
            }
        scalarFromBytes(keyBytes)
        return keyBytes
    }

    fun taggedHash(tag: String, data: ByteArray): ByteArray {
        val tagHash = sha256(tag.toByteArray(Charsets.US_ASCII))
        return sha256(tagHash + tagHash + data)
    }

    private fun inputHash(outpoints: List<String>, inputPubKeySum: EcPoint): BigInteger {
        val lowestOutpoint =
            outpoints.map(::serializeOutpoint)
                .minWithOrNull { a, b -> compareBytesLexicographically(a, b) }
                ?: throw IllegalArgumentException("No silent payment inputs")
        return taggedHashScalar(
            tag = "BIP0352/Inputs",
            data = lowestOutpoint + serializeCompressed(inputPubKeySum),
        )
    }

    private fun taggedHashScalar(tag: String, data: ByteArray): BigInteger {
        val scalar = BigInteger(1, taggedHash(tag, data))
        require(scalar > BigInteger.ZERO && scalar < curveOrder) { "Invalid scalar for $tag" }
        return scalar
    }

    private fun serializeOutpoint(outpoint: String): ByteArray {
        val parts = outpoint.split(":")
        require(parts.size == 2) { "Invalid outpoint" }
        val txid = parts[0].hexToBytes()
        require(txid.size == 32) { "Invalid txid" }
        val vout = parts[1].toLong()
        require(vout in 0..UInt.MAX_VALUE.toLong()) { "Invalid vout" }
        return txid.reversedArray() + byteArrayOf(
            (vout and 0xFF).toByte(),
            ((vout ushr 8) and 0xFF).toByte(),
            ((vout ushr 16) and 0xFF).toByte(),
            ((vout ushr 24) and 0xFF).toByte(),
        )
    }

    private fun decodeBech32m(input: String): Pair<String, List<Int>> {
        require(input.isNotBlank()) { "Empty silent payment address" }
        val lower = input.lowercase(Locale.US)
        require(input == lower || input == input.uppercase(Locale.US)) { "Mixed-case silent payment address" }
        require(lower.length <= 1023) { "Silent payment address too long" }

        val separator = lower.lastIndexOf('1')
        require(separator > 0 && separator + 7 <= lower.length) { "Invalid silent payment address" }

        val hrp = lower.take(separator)
        val dataChars = lower.substring(separator + 1)
        val data = dataChars.map { char ->
            val index = CHARSET.indexOf(char)
            require(index >= 0) { "Invalid silent payment address character" }
            index
        }
        require(polymod(hrpExpand(hrp) + data) == BECH32M_CONST) { "Invalid silent payment checksum" }
        return hrp to data.dropLast(6)
    }

    private fun polymod(values: List<Int>): Int {
        val generators = intArrayOf(0x3b6a57b2, 0x26508e6d, 0x1ea119fa, 0x3d4233dd, 0x2a1462b3)
        var chk = 1
        for (value in values) {
            val top = chk ushr 25
            chk = ((chk and 0x1ffffff) shl 5) xor value
            for (i in 0 until 5) {
                if (((top ushr i) and 1) != 0) {
                    chk = chk xor generators[i]
                }
            }
        }
        return chk
    }

    private fun hrpExpand(hrp: String): List<Int> =
        hrp.map { it.code ushr 5 } + 0 + hrp.map { it.code and 31 }

    private fun convertBech32Payload(data: List<Int>): ByteArray {
        val fromBits = 5
        val toBits = 8
        var acc = 0
        var bits = 0
        val maxv = (1 shl toBits) - 1
        val maxAcc = (1 shl (fromBits + toBits - 1)) - 1
        val result = mutableListOf<Byte>()

        for (value in data) {
            require(value >= 0 && (value shr fromBits) == 0) { "Invalid bech32 data value" }
            acc = ((acc shl fromBits) or value) and maxAcc
            bits += fromBits
            while (bits >= toBits) {
                bits -= toBits
                result.add(((acc shr bits) and maxv).toByte())
            }
        }

        require(bits < fromBits) { "Invalid bech32 padding" }
        require(((acc shl (toBits - bits)) and maxv) == 0) { "Non-zero bech32 padding" }

        return result.toByteArray()
    }

    private data class EcPoint(
        val x: BigInteger,
        val y: BigInteger,
    )

    private fun decodePublicKey(bytes: ByteArray): EcPoint? {
        return when (bytes.size) {
            33
                if (bytes[0] == 0x02.toByte() || bytes[0] == 0x03.toByte()) -> {
                val x = BigInteger(1, bytes.copyOfRange(1, 33))
                if (x >= fieldPrime) return null
                val ySquared = x.modPow(BigInteger.valueOf(3), fieldPrime).add(BigInteger.valueOf(7)).mod(fieldPrime)
                var y = ySquared.modPow(fieldPrime.add(BigInteger.ONE).shiftRight(2), fieldPrime)
                val odd = y.testBit(0)
                val needsOdd = bytes[0] == 0x03.toByte()
                if (odd != needsOdd) {
                    y = fieldPrime.subtract(y).mod(fieldPrime)
                }
                EcPoint(x, y).takeIf(::isOnCurve)
            }
            32 -> {
                val x = BigInteger(1, bytes)
                if (x >= fieldPrime) return null
                val ySquared = x.modPow(BigInteger.valueOf(3), fieldPrime).add(BigInteger.valueOf(7)).mod(fieldPrime)
                var y = ySquared.modPow(fieldPrime.add(BigInteger.ONE).shiftRight(2), fieldPrime)
                if (y.testBit(0)) {
                    y = fieldPrime.subtract(y).mod(fieldPrime)
                }
                EcPoint(x, y).takeIf(::isOnCurve)
            }
            else -> null
        }
    }

    private fun multiplyGenerator(scalar: BigInteger): EcPoint =
        multiplyPoint(generator, scalar) ?: throw IllegalArgumentException("Invalid EC multiplication")

    private fun multiplyPoint(point: EcPoint, scalar: BigInteger): EcPoint? {
        var result: EcPoint? = null
        var addend: EcPoint? = point
        var n = scalar.mod(curveOrder)

        while (n > BigInteger.ZERO) {
            if (n.testBit(0)) {
                result = if (result == null) addend else addPoints(result, addend)
            }
            addend = addend?.let { addPoints(it, it) }
            n = n.shiftRight(1)
        }

        return result
    }

    private fun addPoints(first: EcPoint?, second: EcPoint?): EcPoint? {
        if (first == null) return second
        if (second == null) return first
        if (first.x == second.x && first.y.add(second.y).mod(fieldPrime) == BigInteger.ZERO) {
            return null
        }

        val lambda =
            if (first == second) {
                val numerator = BigInteger.valueOf(3).multiply(first.x).multiply(first.x).mod(fieldPrime)
                val denominator = BigInteger.valueOf(2).multiply(first.y).mod(fieldPrime)
                numerator.multiply(denominator.modInverse(fieldPrime)).mod(fieldPrime)
            } else {
                val numerator = second.y.subtract(first.y).mod(fieldPrime)
                val denominator = second.x.subtract(first.x).mod(fieldPrime)
                numerator.multiply(denominator.modInverse(fieldPrime)).mod(fieldPrime)
            }
        val x = lambda.multiply(lambda).subtract(first.x).subtract(second.x).mod(fieldPrime)
        val y = lambda.multiply(first.x.subtract(x)).subtract(first.y).mod(fieldPrime)
        return EcPoint(x, y)
    }

    private fun isOnCurve(point: EcPoint): Boolean =
        point.y.multiply(point.y).subtract(point.x.modPow(BigInteger.valueOf(3), fieldPrime)).subtract(BigInteger.valueOf(7))
            .mod(fieldPrime) == BigInteger.ZERO

    private fun serializeCompressed(point: EcPoint): ByteArray {
        val prefix = if (point.y.testBit(0)) 0x03.toByte() else 0x02.toByte()
        return byteArrayOf(prefix) + point.x.toByteArray32()
    }

    private fun scalarFromBytes(bytes: ByteArray): BigInteger {
        require(bytes.size == 32) { "Private key must be 32 bytes" }
        val scalar = BigInteger(1, bytes)
        require(scalar > BigInteger.ZERO && scalar < curveOrder) { "Invalid private key" }
        return scalar
    }

    private fun sha256(data: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(data)

    private fun Int.toUInt32Bytes(): ByteArray {
        require(this >= 0) { "Negative uint32" }
        return byteArrayOf(
            ((this ushr 24) and 0xFF).toByte(),
            ((this ushr 16) and 0xFF).toByte(),
            ((this ushr 8) and 0xFF).toByte(),
            (this and 0xFF).toByte(),
        )
    }

    private fun BigInteger.toByteArray32(): ByteArray {
        val bytes = toByteArray()
        val stripped = if (bytes.size > 1 && bytes[0] == 0.toByte()) bytes.copyOfRange(1, bytes.size) else bytes
        require(stripped.size <= 32) { "Integer too large" }
        return ByteArray(32 - stripped.size) + stripped
    }

    private fun String.hexToBytes(): ByteArray {
        require(length % 2 == 0) { "Invalid hex length" }
        return chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }

    private fun ByteArray.toHex(): String =
        joinToString("") { "%02x".format(it) }

    private fun compareBytesLexicographically(a: ByteArray, b: ByteArray): Int {
        val minSize = minOf(a.size, b.size)
        for (i in 0 until minSize) {
            val diff = (a[i].toInt() and 0xFF) - (b[i].toInt() and 0xFF)
            if (diff != 0) return diff
        }
        return a.size - b.size
    }
}
