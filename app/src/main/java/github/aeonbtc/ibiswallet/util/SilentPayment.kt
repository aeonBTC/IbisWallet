package github.aeonbtc.ibiswallet.util

import java.math.BigInteger
import java.security.MessageDigest
import java.util.Locale

internal object SilentPayment {
    private const val MAINNET_HRP = "sp"
    private const val BECH32M_CONST = 0x2bc830a3
    private const val CHARSET = "qpzry9x8gf2tvdw0s3jn54khce6mua7l"
    // BIP-352 recipient-group cap: at most one output per tweak index k per
    // scan key, and receivers scan k = 0..K_MAX. Kept in sync with
    // RECEIVE_K_MAX below — a send with more recipients per scan key would
    // produce outputs the receiver never scans for.
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

    const val SCAN_PATH = "m/352'/0'/0'/1'/0"
    const val SPEND_PATH = "m/352'/0'/0'/0'/0"
    private const val RECEIVE_K_MAX = 2323

    data class ReceiverKeys(
        val scanPrivateKey: ByteArray,
        val scanPublicKey: ByteArray,
        val spendPrivateKey: ByteArray,
        val spendPublicKey: ByteArray,
        val address: String,
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is ReceiverKeys) return false
            return scanPrivateKey.contentEquals(other.scanPrivateKey) &&
                scanPublicKey.contentEquals(other.scanPublicKey) &&
                spendPrivateKey.contentEquals(other.spendPrivateKey) &&
                spendPublicKey.contentEquals(other.spendPublicKey) &&
                address == other.address
        }

        override fun hashCode(): Int {
            var result = scanPrivateKey.contentHashCode()
            result = 31 * result + scanPublicKey.contentHashCode()
            result = 31 * result + spendPrivateKey.contentHashCode()
            result = 31 * result + spendPublicKey.contentHashCode()
            result = 31 * result + address.hashCode()
            return result
        }
    }

    data class FoundOutput(
        val vout: Int,
        val valueSats: ULong,
        val scriptPubKey: ByteArray,
        val xOnlyPublicKey: ByteArray,
        val tweakIndex: Int,
        val isChange: Boolean,
        val spendPrivateKey: ByteArray,
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is FoundOutput) return false
            return vout == other.vout &&
                valueSats == other.valueSats &&
                scriptPubKey.contentEquals(other.scriptPubKey) &&
                xOnlyPublicKey.contentEquals(other.xOnlyPublicKey) &&
                tweakIndex == other.tweakIndex &&
                isChange == other.isChange &&
                spendPrivateKey.contentEquals(other.spendPrivateKey)
        }

        override fun hashCode(): Int {
            var result = vout
            result = 31 * result + valueSats.hashCode()
            result = 31 * result + scriptPubKey.contentHashCode()
            result = 31 * result + xOnlyPublicKey.contentHashCode()
            result = 31 * result + tweakIndex
            result = 31 * result + isChange.hashCode()
            result = 31 * result + spendPrivateKey.contentHashCode()
            return result
        }
    }

    data class TxOutput(
        val scriptPubKey: ByteArray,
        val valueSats: ULong,
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is TxOutput) return false
            return scriptPubKey.contentEquals(other.scriptPubKey) && valueSats == other.valueSats
        }

        override fun hashCode(): Int =
            31 * scriptPubKey.contentHashCode() + valueSats.hashCode()
    }

    fun isSilentPaymentAddress(input: String): Boolean =
        parseAddress(input).isSuccess

    fun parseAddress(input: String): Result<Address> =
        runCatching {
            val trimmed = input.trim()
            val (hrp, data) = decodeBech32m(trimmed)
            require(hrp == MAINNET_HRP) { "Only mainnet silent payment addresses are supported" }
            require(data.isNotEmpty()) { "Unsupported silent payment version" }
            // BIP-352 forward compatibility: v0 requires exactly 66 bytes;
            // v1-v30 readers use the first 66 bytes and ignore the rest; v31
            // is reserved for backwards-incompatible changes and must fail.
            val version = data.first()
            require(version in 0..30) { "Unsupported silent payment version" }
            val payload = convertBech32Payload(data.drop(1))
            if (version == 0) {
                require(payload.size == 66) { "Invalid silent payment payload length" }
            } else {
                require(payload.size >= 66) { "Invalid silent payment payload length" }
            }

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

    fun encodeAddress(
        scanPublicKey: ByteArray,
        spendPublicKey: ByteArray,
    ): String = encodeAddress(scanPublicKey, spendPublicKey, version = 0)

    /**
     * Versioned encoder (BIP-352 v1-v30 carry extra payload after the 66 key
     * bytes). Production receive addresses are always v0; higher versions
     * exist so forward-compat parsing stays tested.
     */
    fun encodeAddress(
        scanPublicKey: ByteArray,
        spendPublicKey: ByteArray,
        version: Int,
        extraPayload: ByteArray = ByteArray(0),
    ): String {
        require(version in 0..30) { "Unsupported silent payment version" }
        require(scanPublicKey.size == 33) { "Scan public key must be compressed" }
        require(spendPublicKey.size == 33) { "Spend public key must be compressed" }
        require(decodePublicKey(scanPublicKey) != null) { "Invalid scan public key" }
        require(decodePublicKey(spendPublicKey) != null) { "Invalid spend public key" }
        val data = listOf(version) + convertBytesToBech32(scanPublicKey + spendPublicKey + extraPayload)
        return encodeBech32m(MAINNET_HRP, data)
    }

    fun deriveReceiverKeys(seed: ByteArray): ReceiverKeys {
        val scanPriv = ElectrumSeedUtil.derivePrivateKey(seed, SCAN_PATH)
        val spendPriv = ElectrumSeedUtil.derivePrivateKey(seed, SPEND_PATH)
        val scanPub = compressedPublicKey(scanPriv)
        val spendPub = compressedPublicKey(spendPriv)
        return ReceiverKeys(
            scanPrivateKey = scanPriv,
            scanPublicKey = scanPub,
            spendPrivateKey = spendPriv,
            spendPublicKey = spendPub,
            address = encodeAddress(scanPub, spendPub),
        )
    }

    fun compressedPublicKey(privateKey: ByteArray): ByteArray =
        serializeCompressed(multiplyGenerator(scalarFromBytes(privateKey)))

    /**
     * BIP-352 label preimage: `ser256(b_scan) || ser32(m)` — the 32-byte scan
     * *private* key concatenated with the big-endian label index. Exposed so
     * a regression test pins the format: hashing the compressed *public* key
     * here instead scans for a tweak no compliant sender ever produces, which
     * silently hides genuine change outputs.
     */
    fun labelPreimage(
        scanPrivateKey: ByteArray,
        label: Int = 0,
    ): ByteArray {
        require(scanPrivateKey.size == 32) { "Scan private key must be 32 bytes" }
        require(label >= 0) { "Negative label" }
        return scanPrivateKey + label.toUInt32Bytes()
    }

    /**
     * Scan transaction outputs for payments to our silent payment address.
     *
     * Matches plain outputs (key `m = 0`) and change outputs (label `m = 0`
     * tweak on the scan key). Custom BIP-352 labels (`m = 1, 2, ...`) are NOT
     * scanned — a sender paying a labeled address derived from our keys will
     * not be detected. This is a documented limitation, not a silent bug:
     * our displayed receive address never carries labels.
     */
    fun scanOutputs(
        tweakKey: ByteArray,
        scanPrivateKey: ByteArray,
        spendPrivateKey: ByteArray,
        spendPublicKey: ByteArray,
        outputs: List<TxOutput>,
        maxK: Int = RECEIVE_K_MAX,
    ): List<FoundOutput> {
        val tweakPoint =
            decodePublicKey(tweakKey) ?: throw IllegalArgumentException("Invalid tweak key")
        val sharedSecret =
            multiplyPoint(tweakPoint, scalarFromBytes(scanPrivateKey))
                ?: throw IllegalArgumentException("Invalid shared secret")
        val spendPoint =
            decodePublicKey(spendPublicKey)
                ?: throw IllegalArgumentException("Invalid spend public key")
        // BIP-352 labels tweak with ser256(b_scan) — the 32-byte scan
        // *private* key — concatenated with the label index. Using the
        // compressed public key here instead scans for a tweak no compliant
        // sender ever produces, so genuine label-0 change outputs (e.g. from
        // another wallet recovering the same seed) would be missed.
        require(scanPrivateKey.size == 32) { "Scan private key must be 32 bytes" }
        val changeTweak =
            taggedHashScalar(
                tag = "BIP0352/Label",
                data = labelPreimage(scanPrivateKey, 0),
            )
        val changeSpendPoint =
            addPoints(spendPoint, multiplyGenerator(changeTweak))
                ?: throw IllegalArgumentException("Invalid change spend key")
        val spendScalar = scalarFromBytes(spendPrivateKey)
        val changeSpendScalar = spendScalar.add(changeTweak).mod(curveOrder)

        val taprootOutputs =
            outputs.mapIndexedNotNull { index, output ->
                xOnlyFromTaprootScript(output.scriptPubKey)?.let { xOnly ->
                    Triple(index, output, xOnly.toHex())
                }
            }
        if (taprootOutputs.isEmpty()) return emptyList()

        val found = mutableListOf<FoundOutput>()
        val remaining = taprootOutputs.toMutableList()

        fun matchAgainst(
            baseSpend: EcPoint,
            baseScalar: BigInteger,
            isChange: Boolean,
        ) {
            var k = 0
            while (k < maxK && remaining.isNotEmpty()) {
                val tk =
                    taggedHashScalar(
                        tag = "BIP0352/SharedSecret",
                        data = serializeCompressed(sharedSecret) + k.toUInt32Bytes(),
                    )
                val outputPoint =
                    addPoints(baseSpend, multiplyGenerator(tk))
                        ?: throw IllegalArgumentException("Invalid silent payment output")
                val xOnly = outputPoint.x.toByteArray32()
                val hex = xOnly.toHex()
                val matchIndex = remaining.indexOfFirst { it.third == hex }
                if (matchIndex >= 0) {
                    val (vout, output, _) = remaining.removeAt(matchIndex)
                    val outputScalar = baseScalar.add(tk).mod(curveOrder)
                    found +=
                        FoundOutput(
                            vout = vout,
                            valueSats = output.valueSats,
                            scriptPubKey = output.scriptPubKey,
                            xOnlyPublicKey = xOnly,
                            tweakIndex = k,
                            isChange = isChange,
                            spendPrivateKey = outputScalar.toByteArray32(),
                        )
                    k += 1
                } else {
                    break
                }
            }
        }

        matchAgainst(spendPoint, spendScalar, isChange = false)
        matchAgainst(changeSpendPoint, changeSpendScalar, isChange = true)
        return found.sortedBy { it.vout }
    }

    fun xOnlyFromTaprootScript(scriptPubKey: ByteArray): ByteArray? {
        if (scriptPubKey.size != 34) return null
        if (scriptPubKey[0] != 0x51.toByte() || scriptPubKey[1] != 0x20.toByte()) return null
        return scriptPubKey.copyOfRange(2, 34)
    }

    fun placeholderScriptPubKey(): ByteArray =
        taprootScriptPubKey(ByteArray(32) { 1 })

    fun taprootScriptPubKey(xOnlyPublicKey: ByteArray): ByteArray {
        require(xOnlyPublicKey.size == 32) { "Taproot output key must be 32 bytes" }
        return byteArrayOf(0x51, 0x20) + xOnlyPublicKey
    }

    // BIP-352: `allVinOutpoints` must be EVERY input of the transaction, not just
    // the eligible subset. It is a required parameter (no default) so future
    // callers cannot silently hash only eligible outpoints and burn outputs.
    fun createOutputKeys(
        inputKeys: List<InputKey>,
        recipients: List<String>,
        allVinOutpoints: List<String>,
    ): List<OutputKey> {
        val parsedRecipients =
            recipients.mapIndexed { index, recipient ->
                index to parseAddress(recipient).getOrThrow()
            }
        val inputScalars =
            inputKeys.mapNotNull { input ->
                val scalar = scalarFromBytes(input.privateKey)
                if (input.isTaproot) toEvenYScalar(scalar) else scalar
            }
        require(inputScalars.isNotEmpty()) { "No eligible inputs for silent payment" }

        val inputScalarSum = inputScalars.fold(BigInteger.ZERO) { acc, scalar ->
            acc.add(scalar).mod(curveOrder)
        }
        require(inputScalarSum != BigInteger.ZERO) { "Invalid silent payment input key sum" }

        val inputPubKeySum = multiplyGenerator(inputScalarSum)
        // BIP-352 hashes the lexicographically smallest outpoint of EVERY
        // transaction input (eligible or not). Never fall back to the eligible
        // subset: hashing the wrong outpoint set produces outputs the
        // receiver can never find, burning funds.
        require(allVinOutpoints.isNotEmpty()) { "Silent payments require all transaction outpoints" }
        val inputHash = inputHash(allVinOutpoints, inputPubKeySum)
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

    fun computeTweakKey(
        inputKeys: List<InputKey>,
        allVinOutpoints: List<String>,
    ): ByteArray {
        val inputScalars =
            inputKeys.mapNotNull { input ->
                val scalar = scalarFromBytes(input.privateKey)
                if (input.isTaproot) toEvenYScalar(scalar) else scalar
            }
        require(inputScalars.isNotEmpty()) { "No eligible inputs for silent payment" }
        val inputScalarSum = inputScalars.fold(BigInteger.ZERO) { acc, scalar ->
            acc.add(scalar).mod(curveOrder)
        }
        require(inputScalarSum != BigInteger.ZERO) { "Invalid silent payment input key sum" }
        val inputPubKeySum = multiplyGenerator(inputScalarSum)
        require(allVinOutpoints.isNotEmpty()) { "Silent payments require all transaction outpoints" }
        val inputHash = inputHash(allVinOutpoints, inputPubKeySum)
        return serializeCompressed(
            multiplyPoint(inputPubKeySum, inputHash)
                ?: throw IllegalArgumentException("Invalid tweak key"),
        )
    }

    fun evenYPrivateKey(privateKey: ByteArray): ByteArray {
        val scalar = scalarFromBytes(privateKey)
        return toEvenYScalar(scalar).toByteArray32()
    }

    /**
     * Fail-closed check that a derived BIP-352 input private key actually owns
     * the prevout it is about to be mixed into the shared-secret sum.
     *
     * A derivation mismatch (wrong seed, stale custom path, cross-wallet
     * race) would otherwise produce outputs the receiver can never find while
     * the transaction itself broadcasts fine — silent fund burning. Callers
     * must refuse to build the transaction when this returns false.
     *
     * [privateKey] is the key as used in the scalar sum: the raw key for
     * P2PKH/P2WPKH, the tweaked output key for P2TR (even-Y normalized here).
     */
    fun inputKeyMatchesScript(
        privateKey: ByteArray,
        scriptPubKey: ByteArray,
        isTaproot: Boolean,
    ): Boolean =
        runCatching {
            require(privateKey.size == 32) { "Private key must be 32 bytes" }
            when {
                isTaproot &&
                    scriptPubKey.size == 34 &&
                    scriptPubKey[0] == 0x51.toByte() &&
                    scriptPubKey[1] == 0x20.toByte() -> {
                    val xOnly =
                        compressedPublicKey(evenYPrivateKey(privateKey)).copyOfRange(1, 33)
                    xOnly.contentEquals(scriptPubKey.copyOfRange(2, 34))
                }
                !isTaproot && isP2pkhScript(scriptPubKey) -> {
                    ElectrumSeedUtil.hash160(compressedPublicKey(privateKey))
                        .contentEquals(scriptPubKey.copyOfRange(3, 23))
                }
                !isTaproot && isP2wpkhScript(scriptPubKey) -> {
                    ElectrumSeedUtil.hash160(compressedPublicKey(privateKey))
                        .contentEquals(scriptPubKey.copyOfRange(2, 22))
                }
                else -> false
            }
        }.getOrDefault(false)

    private fun isP2pkhScript(scriptPubKey: ByteArray): Boolean =
        scriptPubKey.size == 25 &&
            scriptPubKey[0] == 0x76.toByte() &&
            scriptPubKey[1] == 0xA9.toByte() &&
            scriptPubKey[2] == 0x14.toByte() &&
            scriptPubKey[23] == 0x88.toByte() &&
            scriptPubKey[24] == 0xAC.toByte()

    private fun isP2wpkhScript(scriptPubKey: ByteArray): Boolean =
        scriptPubKey.size == 22 &&
            scriptPubKey[0] == 0x00.toByte() &&
            scriptPubKey[1] == 0x14.toByte()

    /**
     * Single canonical even-Y negation for BIP-352/BIP-341. Taproot input
     * scalars must use the even-Y variant; all call sites go through here so
     * the negation can never be applied twice or skipped on one path.
     */
    private fun toEvenYScalar(scalar: BigInteger): BigInteger {
        val point = multiplyGenerator(scalar)
        return if (point.y.testBit(0)) curveOrder.subtract(scalar).mod(curveOrder) else scalar
    }

    fun toCompressedWif(privateKey: ByteArray): String {
        val even = evenYPrivateKey(privateKey)
        return BitcoinUtils.Base58.encodeChecked(byteArrayOf(0x80.toByte()) + even + byteArrayOf(0x01))
    }

    fun deriveTaprootOutputPrivateKey(internalPrivateKey: ByteArray): ByteArray {
        val internalScalar = scalarFromBytes(internalPrivateKey)
        val evenInternalScalar = toEvenYScalar(internalScalar)
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

    /**
     * True only when the WIF encodes a compressed key (33-byte payload with
     * 0x01 suffix). Decodes Base58Check instead of guessing from the prefix
     * character so uncompressed `5...` keys can never slip into the BIP-352
     * scalar sum and burn the output.
     */
    fun isCompressedWif(wif: String): Boolean =
        runCatching {
            val decoded = BitcoinUtils.Base58.decodeChecked(wif.trim())
            decoded.size == 34 &&
                decoded[0].toInt() and 0xFF == 0x80 &&
                decoded.last() == 0x01.toByte()
        }.getOrDefault(false)

    fun taggedHash(tag: String, data: ByteArray): ByteArray {
        val tagHash = sha256(tag.toByteArray(Charsets.US_ASCII))
        return sha256(tagHash + tagHash + data)
    }

    // ==================== BIP-340 Schnorr ====================
    // Hand-rolled because BDK's miniscript cannot parse rawtr() descriptors,
    // the only descriptor form matching a BIP-352 output key. Every signature
    // produced here is re-verified (self-check + miniscript interpreter in
    // finalize()), so a bug fails the send — it can never move funds wrongly.

    private fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val mac = javax.crypto.Mac.getInstance("HmacSHA256")
        mac.init(javax.crypto.spec.SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }

    private fun hasEvenY(point: EcPoint): Boolean = !point.y.testBit(0)

    private fun negatePoint(point: EcPoint): EcPoint =
        if (point.y == BigInteger.ZERO) point else EcPoint(point.x, fieldPrime.subtract(point.y))

    private fun liftX(xBytes: ByteArray): EcPoint {
        require(xBytes.size == 32) { "x-only key must be 32 bytes" }
        val x = BigInteger(1, xBytes)
        require(x < fieldPrime) { "x-only key out of range" }
        val ySquared = x.modPow(BigInteger.valueOf(3), fieldPrime).add(BigInteger.valueOf(7)).mod(fieldPrime)
        var y = ySquared.modPow(fieldPrime.add(BigInteger.ONE).shiftRight(2), fieldPrime)
        require(y.multiply(y).mod(fieldPrime) == ySquared) { "Invalid x-only key" }
        if (y.testBit(0)) {
            y = fieldPrime.subtract(y)
        }
        return EcPoint(x, y)
    }

    fun schnorrSign(
        privateKey: ByteArray,
        message: ByteArray,
        auxRand: ByteArray? = null,
    ): ByteArray {
        require(message.size == 32) { "BIP-340 message must be 32 bytes" }
        var dPrime = scalarFromBytes(privateKey)
        var point = multiplyGenerator(dPrime)
        var d = if (hasEvenY(point)) dPrime else curveOrder.subtract(dPrime)
        val random = java.security.SecureRandom()
        while (true) {
            val aux = auxRand ?: ByteArray(32).also { random.nextBytes(it) }
            require(aux.size == 32) { "BIP-340 aux must be 32 bytes" }
            val t = d.toByteArray32().xorBytes(taggedHash("BIP0340/aux", aux))
            val rand = taggedHash("BIP0340/nonce", t + multiplyGenerator(d).x.toByteArray32() + message)
            val kPrime = BigInteger(1, rand).mod(curveOrder)
            if (kPrime == BigInteger.ZERO) {
                if (auxRand != null) throw IllegalArgumentException("Invalid Schnorr nonce")
                continue
            }
            val rPoint = multiplyGenerator(kPrime)
            val k = if (hasEvenY(rPoint)) kPrime else curveOrder.subtract(kPrime)
            val e =
                BigInteger(
                    1,
                    taggedHash(
                        "BIP0340/challenge",
                        rPoint.x.toByteArray32() + multiplyGenerator(d).x.toByteArray32() + message,
                    ),
                ).mod(curveOrder)
            val s = k.add(e.multiply(d)).mod(curveOrder)
            return rPoint.x.toByteArray32() + s.toByteArray32()
        }
    }

    fun schnorrVerify(
        publicKey: ByteArray,
        message: ByteArray,
        signature: ByteArray,
    ): Boolean {
        return runCatching {
            // Accept x-only (32) or compressed (33) keys; Schnorr verifies
            // against the x-only form either way.
            val publicKeyXOnly =
                when (publicKey.size) {
                    32 -> publicKey
                    33 -> {
                        require(publicKey[0] == 0x02.toByte() || publicKey[0] == 0x03.toByte()) {
                            "Invalid compressed key prefix"
                        }
                        publicKey.copyOfRange(1, 33)
                    }
                    else -> throw IllegalArgumentException("Public key must be 32 or 33 bytes")
                }
            require(message.size == 32 && signature.size == 64)
            val r = BigInteger(1, signature.copyOfRange(0, 32))
            val s = BigInteger(1, signature.copyOfRange(32, 64))
            require(r < fieldPrime && s < curveOrder)
            val point = liftX(publicKeyXOnly)
            val e =
                BigInteger(
                    1,
                    taggedHash("BIP0340/challenge", signature.copyOfRange(0, 32) + publicKeyXOnly + message),
                ).mod(curveOrder)
            val rPoint = addPoints(multiplyGenerator(s), negatePoint(multiplyPoint(point, e)!!))
            rPoint != null && hasEvenY(rPoint) && rPoint.x == r
        }.getOrDefault(false)
    }

    private fun ByteArray.xorBytes(other: ByteArray): ByteArray {
        require(size == other.size) { "XOR length mismatch" }
        return ByteArray(size) { i -> (this[i].toInt() xor other[i].toInt()).toByte() }
    }

    // ==================== BIP-341 key-path sighash (SIGHASH_DEFAULT) ====================

    data class SighashTxIn(
        val txidHex: String,
        val vout: UInt,
        val sequence: UInt,
        val prevScriptPubKey: ByteArray,
        val prevValueSats: ULong,
    )

    data class SighashTxOut(
        val valueSats: ULong,
        val scriptPubKey: ByteArray,
    )

    fun taprootKeySpendSighash(
        version: Int,
        lockTime: UInt,
        inputs: List<SighashTxIn>,
        outputs: List<SighashTxOut>,
        inputIndex: Int,
    ): ByteArray {
        require(inputIndex in inputs.indices) { "Sighash input index out of range" }
        val shaPrevouts = MessageDigest.getInstance("SHA-256")
        val shaAmounts = MessageDigest.getInstance("SHA-256")
        val shaScripts = MessageDigest.getInstance("SHA-256")
        val shaSequences = MessageDigest.getInstance("SHA-256")
        for (input in inputs) {
            shaPrevouts.update(serializeOutpoint("${input.txidHex}:${input.vout}"))
            shaAmounts.update(input.prevValueSats.toLe64())
            shaScripts.update(compactSize(input.prevScriptPubKey.size) + input.prevScriptPubKey)
            shaSequences.update(input.sequence.toLe32())
        }
        val shaOutputs = MessageDigest.getInstance("SHA-256")
        for (output in outputs) {
            shaOutputs.update(output.valueSats.toLe64())
            shaOutputs.update(compactSize(output.scriptPubKey.size) + output.scriptPubKey)
        }
        val sigMsg =
            byteArrayOf(0x00, 0x00) +
                version.toLe32() +
                lockTime.toLe32() +
                shaPrevouts.digest() +
                shaAmounts.digest() +
                shaScripts.digest() +
                shaSequences.digest() +
                shaOutputs.digest() +
                byteArrayOf(0x00) +
                inputIndex.toLe32()
        return taggedHash("TapSighash", sigMsg)
    }

    private fun UInt.toLe32(): ByteArray =
        byteArrayOf(
            (this and 0xFFu).toByte(),
            ((this shr 8) and 0xFFu).toByte(),
            ((this shr 16) and 0xFFu).toByte(),
            ((this shr 24) and 0xFFu).toByte(),
        )

    private fun Int.toLe32(): ByteArray =
        byteArrayOf(
            (this and 0xFF).toByte(),
            ((this ushr 8) and 0xFF).toByte(),
            ((this ushr 16) and 0xFF).toByte(),
            ((this ushr 24) and 0xFF).toByte(),
        )

    private fun ULong.toLe64(): ByteArray =
        ByteArray(8) { i -> ((this shr (8 * i)) and 0xFFu).toByte() }

    private fun compactSize(value: Int): ByteArray {
        require(value >= 0) { "Negative compact size" }
        return when {
            value < 0xFD -> byteArrayOf(value.toByte())
            value <= 0xFFFF ->
                byteArrayOf(
                    0xFD.toByte(),
                    (value and 0xFF).toByte(),
                    ((value ushr 8) and 0xFF).toByte(),
                )
            else ->
                byteArrayOf(
                    0xFE.toByte(),
                    (value and 0xFF).toByte(),
                    ((value ushr 8) and 0xFF).toByte(),
                    ((value ushr 16) and 0xFF).toByte(),
                    ((value ushr 24) and 0xFF).toByte(),
                )
        }
    }

    // ==================== PSBT surgery ====================
    // BDK exposes no API to set per-input fields, so taproot key-path data
    // is injected at the byte level. Miniscript finalize requires
    // tap_key_origins to build a descriptor for foreign P2TR, which BIP-352
    // outputs never have — so we also write final_script_witness (0x08) and
    // skip the interpreter for those inputs.

    private const val PSBT_IN_FINAL_SCRIPTWITNESS = 0x08
    private const val PSBT_IN_TAP_KEY_SIG = 0x13
    private const val PSBT_IN_TAP_INTERNAL_KEY = 0x17

    private fun taprootKeyPathWitness(signature: ByteArray): ByteArray {
        require(signature.size == 64) { "Taproot signature must be 64 bytes" }
        return writeCompactSize(1) + writeCompactSize(64) + signature
    }

    private class PsbtReader(val bytes: ByteArray) {
        var pos = 0

        fun readCompactSize(): Long {
            require(pos < bytes.size) { "Truncated PSBT" }
            return when (val first = bytes[pos++].toInt() and 0xFF) {
                0xFF -> {
                    require(pos + 8 <= bytes.size) { "Truncated PSBT" }
                    var value = 0L
                    for (i in 0 until 8) {
                        value = value or ((bytes[pos++].toLong() and 0xFF) shl (8 * i))
                    }
                    value
                }
                0xFE -> {
                    require(pos + 4 <= bytes.size) { "Truncated PSBT" }
                    var value = 0L
                    for (i in 0 until 4) {
                        value = value or ((bytes[pos++].toLong() and 0xFF) shl (8 * i))
                    }
                    value
                }
                0xFD -> {
                    require(pos + 2 <= bytes.size) { "Truncated PSBT" }
                    ((bytes[pos++].toInt() and 0xFF) or ((bytes[pos++].toInt() and 0xFF) shl 8)).toLong()
                }
                else -> first.toLong()
            }
        }

        fun readBytes(length: Long): ByteArray {
            require(length <= Int.MAX_VALUE && pos + length <= bytes.size) { "Truncated PSBT" }
            return bytes.copyOfRange(pos, (pos + length).toInt()).also { pos += length.toInt() }
        }
    }

    private fun writeCompactSize(value: Long): ByteArray {
        return when {
            value < 0xFD -> byteArrayOf(value.toByte())
            value <= 0xFFFF ->
                byteArrayOf(0xFD.toByte(), (value and 0xFF).toByte(), ((value ushr 8) and 0xFF).toByte())
            value <= 0xFFFFFFFFL ->
                byteArrayOf(
                    0xFE.toByte(),
                    (value and 0xFF).toByte(),
                    ((value ushr 8) and 0xFF).toByte(),
                    ((value ushr 16) and 0xFF).toByte(),
                    ((value ushr 24) and 0xFF).toByte(),
                )
            else -> {
                val out = ByteArray(9)
                out[0] = 0xFF.toByte()
                for (i in 0 until 8) {
                    out[1 + i] = ((value ushr (8 * i)) and 0xFF).toByte()
                }
                out
            }
        }
    }

    private fun readPsbtMap(reader: PsbtReader): MutableList<Pair<ByteArray, ByteArray>> {
        val pairs = mutableListOf<Pair<ByteArray, ByteArray>>()
        while (true) {
            val keyLen = reader.readCompactSize()
            if (keyLen == 0L) break
            val key = reader.readBytes(keyLen)
            val valueLen = reader.readCompactSize()
            pairs += key to reader.readBytes(valueLen)
        }
        return pairs
    }

    private fun writePsbtMap(pairs: List<Pair<ByteArray, ByteArray>>): ByteArray {
        var out = byteArrayOf()
        for ((key, value) in pairs) {
            out += writeCompactSize(key.size.toLong()) + key +
                writeCompactSize(value.size.toLong()) + value
        }
        return out + byteArrayOf(0x00)
    }

    /** Re-walk serialized PSBT bytes and confirm the taproot fields landed. */
    fun psbtVerifyTapInjection(
        psbtBytes: ByteArray,
        inputCount: Int,
        index: Int,
    ): Boolean {
        return runCatching {
            val reader = PsbtReader(psbtBytes)
            val magic = reader.readBytes(5)
            require(magic.contentEquals(byteArrayOf(0x70, 0x73, 0x62, 0x74, 0xFF.toByte())))
            readPsbtMap(reader)
            repeat(inputCount) { i ->
                val pairs = readPsbtMap(reader)
                if (i == index) {
                    val internal = pairs.firstOrNull { it.first.contentEquals(byteArrayOf(0x17)) }
                    val sig = pairs.firstOrNull { it.first.contentEquals(byteArrayOf(0x13)) }
                    val witness = pairs.firstOrNull {
                        it.first.contentEquals(byteArrayOf(PSBT_IN_FINAL_SCRIPTWITNESS.toByte()))
                    }
                    return internal != null && internal.second.size == 32 &&
                        sig != null && sig.second.size == 64 &&
                        witness != null && witness.second.contentEquals(taprootKeyPathWitness(sig.second))
                }
            }
            false
        }.getOrDefault(false)
    }

    fun psbtInjectTapKeySigs(
        psbtBytes: ByteArray,
        inputCount: Int,
        entries: Map<Int, Pair<ByteArray, ByteArray>>,
    ): ByteArray {
        require(entries.isNotEmpty()) { "No taproot signatures to inject" }
        for ((index, entry) in entries) {
            require(index in 0 until inputCount) { "Taproot sig input index out of range" }
            require(entry.first.size == 32) { "Taproot internal key must be 32 bytes" }
            require(entry.second.size == 64) { "Taproot signature must be 64 bytes" }
        }
        val reader = PsbtReader(psbtBytes)
        val magic = reader.readBytes(5)
        require(magic.contentEquals(byteArrayOf(0x70, 0x73, 0x62, 0x74, 0xFF.toByte()))) {
            "Invalid PSBT magic"
        }
        var out = magic
        out += writePsbtMap(readPsbtMap(reader))
        repeat(inputCount) { index ->
            val pairs = readPsbtMap(reader)
            val extra = entries[index]
            val withSigs =
                if (extra != null) {
                    // Replace any pre-existing taproot fields instead of
                    // appending duplicates: duplicate map keys are invalid
                    // BIP174 for strict parsers (e.g. re-injection into an
                    // already-signed input, or a BDK-signed P2TR input).
                    val base =
                        pairs.filterNot { (key, _) ->
                            key.size == 1 &&
                                (key[0] == PSBT_IN_TAP_INTERNAL_KEY.toByte() ||
                                    key[0] == PSBT_IN_TAP_KEY_SIG.toByte() ||
                                    key[0] == PSBT_IN_FINAL_SCRIPTWITNESS.toByte())
                        }
                    base +
                        (byteArrayOf(PSBT_IN_TAP_INTERNAL_KEY.toByte()) to extra.first) +
                        (byteArrayOf(PSBT_IN_TAP_KEY_SIG.toByte()) to extra.second) +
                        (byteArrayOf(PSBT_IN_FINAL_SCRIPTWITNESS.toByte()) to taprootKeyPathWitness(extra.second))
                } else {
                    pairs
                }
            out += writePsbtMap(withSigs)
        }
        out += psbtBytes.copyOfRange(reader.pos, psbtBytes.size)
        return out
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

    private fun encodeBech32m(hrp: String, data: List<Int>): String {
        val checksum = createChecksum(hrp, data)
        return hrp + "1" + (data + checksum).joinToString("") { CHARSET[it].toString() }
    }

    private fun createChecksum(hrp: String, data: List<Int>): List<Int> {
        val values = hrpExpand(hrp) + data + List(6) { 0 }
        val polymod = polymod(values) xor BECH32M_CONST
        return (0 until 6).map { i -> (polymod shr (5 * (5 - i))) and 31 }
    }

    private fun convertBytesToBech32(data: ByteArray): List<Int> {
        val fromBits = 8
        val toBits = 5
        var acc = 0
        var bits = 0
        val maxv = (1 shl toBits) - 1
        val maxAcc = (1 shl (fromBits + toBits - 1)) - 1
        val result = mutableListOf<Int>()
        for (byte in data) {
            val value = byte.toInt() and 0xFF
            acc = ((acc shl fromBits) or value) and maxAcc
            bits += fromBits
            while (bits >= toBits) {
                bits -= toBits
                result.add((acc shr bits) and maxv)
            }
        }
        if (bits > 0) {
            result.add((acc shl (toBits - bits)) and maxv)
        }
        return result
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
