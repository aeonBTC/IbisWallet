package github.aeonbtc.ibiswallet.util

import github.aeonbtc.ibiswallet.data.model.AddressType
import github.aeonbtc.ibiswallet.data.model.WalletNetwork

/**
 * Pure-logic Bitcoin utility functions extracted from WalletRepository
 * for testability. These functions handle address validation, key format
 * detection, Base58 encoding, and key conversion — all critical paths
 * where a bug could cause fund loss.
 *
 * No Android dependencies. No BDK dependencies. Pure Kotlin + JDK crypto.
 */
object BitcoinUtils {
    const val UNSUPPORTED_NESTED_SEGWIT_MESSAGE =
        "Nested SegWit is not supported. Use Legacy, SegWit, or Taproot."
    const val UNSUPPORTED_NON_MAINNET_MESSAGE =
        "Only Bitcoin mainnet is supported."

    private const val BASE58_ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
    private val supportedDescriptorPrefixes = listOf("pkh(", "wpkh(", "tr(", "wsh(", "sh(wsh(")
    private val liquidDescriptorBranchRegex = Regex("""/([01])/\*(\)+)$""")

    fun parseSupportedAddressType(
        rawAddressType: String?,
        default: AddressType = AddressType.SEGWIT,
    ): AddressType {
        val normalized = rawAddressType?.trim()?.uppercase()
        return when (normalized) {
            null, "" -> default
            AddressType.LEGACY.name -> AddressType.LEGACY
            AddressType.SEGWIT.name -> AddressType.SEGWIT
            AddressType.TAPROOT.name -> AddressType.TAPROOT
            "NESTED_SEGWIT" -> throw IllegalArgumentException(UNSUPPORTED_NESTED_SEGWIT_MESSAGE)
            else -> default
        }
    }

    fun parseSupportedWalletNetwork(
        rawNetwork: String?,
        default: WalletNetwork = WalletNetwork.BITCOIN,
    ): WalletNetwork {
        val normalized = rawNetwork?.trim()?.uppercase()
        return when (normalized) {
            null, "" -> default
            WalletNetwork.BITCOIN.name -> WalletNetwork.BITCOIN
            "TESTNET", "SIGNET", "REGTEST" -> throw IllegalArgumentException(UNSUPPORTED_NON_MAINNET_MESSAGE)
            else -> default
        }
    }

    fun unsupportedNestedSegwitReason(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.isBlank()) return null

        val lowered = trimmed.lowercase()
        return when {
            lowered.contains("\"p2sh_p2wpkh\"") -> UNSUPPORTED_NESTED_SEGWIT_MESSAGE
            trimmed.startsWith("ypub") || trimmed.startsWith("upub") ->
                UNSUPPORTED_NESTED_SEGWIT_MESSAGE
            trimmed.startsWith("yprv") || trimmed.startsWith("uprv") ->
                UNSUPPORTED_NESTED_SEGWIT_MESSAGE
            trimmed.startsWith("[") &&
                trimmed.contains("]") &&
                trimmed.substringAfter("]").let { it.startsWith("ypub") || it.startsWith("upub") } ->
                UNSUPPORTED_NESTED_SEGWIT_MESSAGE
            trimmed.startsWith("[") &&
                trimmed.contains("]") &&
                trimmed.substringAfter("]").let { it.startsWith("yprv") || it.startsWith("uprv") } ->
                UNSUPPORTED_NESTED_SEGWIT_MESSAGE
            lowered.startsWith("sh(wpkh(") -> UNSUPPORTED_NESTED_SEGWIT_MESSAGE
            trimmed.startsWith("3") || trimmed.startsWith("2") -> UNSUPPORTED_NESTED_SEGWIT_MESSAGE
            else -> null
        }
    }

    fun unsupportedNonMainnetReason(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.isBlank()) return null

        val lowered = trimmed.lowercase()
        return when {
            lowered.contains("\"network\":\"testnet\"") ||
                lowered.contains("\"network\": \"testnet\"") ||
                lowered.contains("\"network\":\"signet\"") ||
                lowered.contains("\"network\": \"signet\"") ||
                lowered.contains("\"network\":\"regtest\"") ||
                lowered.contains("\"network\": \"regtest\"") ->
                UNSUPPORTED_NON_MAINNET_MESSAGE
            trimmed.startsWith("tpub") || trimmed.startsWith("vpub") ->
                UNSUPPORTED_NON_MAINNET_MESSAGE
            trimmed.startsWith("tprv") || trimmed.startsWith("uprv") ->
                UNSUPPORTED_NON_MAINNET_MESSAGE
            trimmed.startsWith("[") &&
                trimmed.contains("]") &&
                trimmed.substringAfter("]").let { it.startsWith("tpub") || it.startsWith("vpub") } ->
                UNSUPPORTED_NON_MAINNET_MESSAGE
            trimmed.startsWith("[") &&
                trimmed.contains("]") &&
                trimmed.substringAfter("]").let { it.startsWith("tprv") || it.startsWith("uprv") } ->
                UNSUPPORTED_NON_MAINNET_MESSAGE
            lowered.startsWith("bitcoin:tb1") ||
                lowered.startsWith("bitcoin:m") ||
                lowered.startsWith("bitcoin:n") ||
                lowered.startsWith("bitcoin:2") ->
                UNSUPPORTED_NON_MAINNET_MESSAGE
            trimmed.startsWith("tb1q") || trimmed.startsWith("tb1p") ->
                UNSUPPORTED_NON_MAINNET_MESSAGE
            isLikelyTestnetBase58Address(trimmed) ->
                UNSUPPORTED_NON_MAINNET_MESSAGE
            isLikelyTestnetWif(trimmed) ->
                UNSUPPORTED_NON_MAINNET_MESSAGE
            else -> null
        }
    }

    private fun isLikelyTestnetBase58Address(input: String): Boolean {
        if (!input.startsWith("m") && !input.startsWith("n") && !input.startsWith("2")) {
            return false
        }
        return input.length in 26..35 && input.all { it in BASE58_ALPHABET }
    }

    private fun isLikelyTestnetWif(input: String): Boolean {
        if (!input.startsWith("c") && !input.startsWith("9")) {
            return false
        }
        return input.length in 51..52 && input.all { it in BASE58_ALPHABET }
    }


    // ── Address Type Detection ───────────────────────────────────────

    /**
     * Detect the address type from a Bitcoin mainnet address string.
     */
    fun detectAddressType(address: String): AddressType? {
        val trimmed = address.trim()
        return when {
            trimmed.startsWith("1") -> AddressType.LEGACY
            trimmed.startsWith("bc1q") -> AddressType.SEGWIT
            trimmed.startsWith("bc1p") -> AddressType.TAPROOT
            else -> null
        }
    }

    /**
     * Detect the address type from a supported output descriptor string.
     */
    fun detectDescriptorAddressType(descriptor: String): AddressType? {
        val trimmed = stripDescriptorChecksum(descriptor).trim().lowercase()
        return when {
            trimmed.startsWith("pkh(") -> AddressType.LEGACY
            trimmed.startsWith("wpkh(") -> AddressType.SEGWIT
            trimmed.startsWith("wsh(") || trimmed.startsWith("sh(wsh(") -> AddressType.SEGWIT
            trimmed.startsWith("tr(") -> AddressType.TAPROOT
            else -> null
        }
    }

    // ── Watch-Only Detection ─────────────────────────────────────────

    /**
     * Check if input string represents a watch-only key material.
     * Supports:
     * - Bare xpub/zpub
     * - Origin-prefixed: "[fingerprint/path]xpub..."
     * - Full output descriptors: wpkh([fingerprint/path]xpub.../0/wildcard)
     */
    fun isWatchOnlyInput(input: String): Boolean {
        val trimmed = input.trim()

        if (unsupportedNonMainnetReason(trimmed) != null) {
            return false
        }
        if (unsupportedNestedSegwitReason(trimmed) != null) {
            return false
        }

        // Bare xpub/zpub
        if (trimmed.startsWith("xpub") ||
            trimmed.startsWith("zpub")
        ) {
            return true
        }

        // Origin-prefixed: [fingerprint/path]xpub
        if (trimmed.startsWith("[") && trimmed.contains("]")) {
            val afterBracket = trimmed.substringAfter("]")
            if (afterBracket.startsWith("xpub") ||
                afterBracket.startsWith("zpub")
            ) {
                return true
            }
        }

        // Full output descriptor with public key
        if (supportedDescriptorPrefixes.any { trimmed.lowercase().startsWith(it) }) {
            val hasPublicKey =
                trimmed.contains("xpub") || trimmed.contains("zpub")
            val hasPrivateKey = trimmed.contains("xprv") || trimmed.contains("zprv")
            return hasPublicKey && !hasPrivateKey
        }

        return false
    }

    // ── WIF Private Key Validation ───────────────────────────────────

    /**
     * Check if input string is a WIF (Wallet Import Format) private key.
     * Mainnet: starts with 'K' or 'L' (compressed, 52 chars) or '5' (uncompressed, 51 chars)
     * Validates via Base58Check decode: version byte 0x80 (mainnet).
     */
    fun isWifPrivateKey(input: String): Boolean {
        val trimmed = input.trim()
        val couldBeWif =
            (trimmed.length == 52 && (trimmed[0] == 'K' || trimmed[0] == 'L')) ||
                (trimmed.length == 51 && trimmed[0] == '5')
        if (!couldBeWif) return false

        return try {
            val decoded = Base58.decodeChecked(trimmed)
            val version = decoded[0].toInt() and 0xFF
            val validVersion = version == 0x80
            val validLength = decoded.size == 34 || decoded.size == 33
            validVersion && validLength
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Check if a WIF key is compressed (K/L prefix, 52 chars).
     */
    fun isWifCompressed(wif: String): Boolean {
        val trimmed = wif.trim()
        return trimmed.length == 52 && (trimmed[0] == 'K' || trimmed[0] == 'L')
    }

    // ── Fingerprint Extraction ───────────────────────────────────────

    /**
     * Extract master fingerprint from key material string.
     * Parses "[fingerprint/...]xpub" and "wpkh([fingerprint/...]xpub/0/wildcard)" formats.
     * Returns null if no fingerprint found.
     */
    fun extractFingerprint(keyMaterial: String): String? {
        val pattern = """\[([a-fA-F0-9]{8})/""".toRegex()
        return pattern.find(keyMaterial.trim())?.groupValues?.get(1)?.lowercase()
    }

    // ── Key Origin Parsing ───────────────────────────────────────────

    /**
     * Parsed key origin info from an extended key string.
     */
    data class KeyOriginInfo(
        val bareKey: String,
        val fingerprint: String?,
        val derivationPath: String?,
    )

    /**
     * Parse key origin info from "[fingerprint/path]xpub..." format.
     * Returns the bare key and any origin info found.
     * Handles both ' and h notation for hardened derivation.
     */
    fun parseKeyOrigin(input: String): KeyOriginInfo {
        val originPattern = """\[([a-fA-F0-9]{8})/([^]]+)](.+)""".toRegex()
        val match = originPattern.find(input.trim())

        return if (match != null) {
            val fingerprint = match.groupValues[1].lowercase()
            val path =
                match.groupValues[2]
                    .replace("H", "'")
                    .replace("h", "'")
            val bareKey = match.groupValues[3]
            KeyOriginInfo(bareKey, fingerprint, path)
        } else {
            KeyOriginInfo(input.trim(), null, null)
        }
    }

    // ── Extended Key Conversion ──────────────────────────────────────

    /**
     * Convert zpub to xpub via Base58Check re-encode.
     * xpub passes through unchanged.
     *
     * Version byte mappings:
     * - xpub: 0x0488B21E (mainnet)
     * - zpub: 0x04B24746 (mainnet BIP84)
     */
    fun convertToXpub(extendedKey: String): String {
        unsupportedNonMainnetReason(extendedKey)?.let { throw IllegalArgumentException(it) }
        unsupportedNestedSegwitReason(extendedKey)?.let { throw IllegalArgumentException(it) }

        if (extendedKey.startsWith("xpub")) {
            return extendedKey
        }

        val decoded = Base58.decodeChecked(extendedKey)

        val targetVersion = byteArrayOf(0x04, 0x88.toByte(), 0xB2.toByte(), 0x1E) // xpub

        val newData = targetVersion + decoded.sliceArray(4 until decoded.size)
        return Base58.encodeChecked(newData)
    }

    // ── Base58 Encoding/Decoding ─────────────────────────────────────

    /**
     * Base58 encoding/decoding with checksum support.
     * Used for Bitcoin address and extended key serialization.
     */
    object Base58 {
        private const val ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
        private val INDEXES =
            IntArray(128) { -1 }.also { arr ->
                ALPHABET.forEachIndexed { i, c -> arr[c.code] = i }
            }

        /**
         * Decode a Base58Check string, verifying the 4-byte checksum.
         * Returns the payload (without checksum).
         * @throws IllegalArgumentException if checksum is invalid
         */
        fun decodeChecked(input: String): ByteArray {
            val decoded = decode(input)
            val data = decoded.sliceArray(0 until decoded.size - 4)
            val checksum = decoded.sliceArray(decoded.size - 4 until decoded.size)
            val hash = sha256(sha256(data))
            val expectedChecksum = hash.sliceArray(0 until 4)
            require(checksum.contentEquals(expectedChecksum)) { "Invalid checksum" }
            return data
        }

        /**
         * Encode data with a 4-byte double-SHA256 checksum appended.
         */
        fun encodeChecked(data: ByteArray): String {
            val hash = sha256(sha256(data))
            val checksum = hash.sliceArray(0 until 4)
            return encode(data + checksum)
        }

        /**
         * Raw Base58 decode (no checksum verification).
         */
        fun decode(input: String): ByteArray {
            if (input.isEmpty()) return ByteArray(0)

            var bi = java.math.BigInteger.ZERO
            for (c in input) {
                val digit = INDEXES[c.code]
                require(digit >= 0) { "Invalid Base58 character: $c" }
                bi = bi.multiply(java.math.BigInteger.valueOf(58))
                    .add(java.math.BigInteger.valueOf(digit.toLong()))
            }

            val bytes = bi.toByteArray()
            val stripSign = bytes.isNotEmpty() && bytes[0] == 0.toByte() && bytes.size > 1
            val stripped = if (stripSign) bytes.sliceArray(1 until bytes.size) else bytes

            var leadingZeros = 0
            for (c in input) {
                if (c == '1') leadingZeros++ else break
            }

            return ByteArray(leadingZeros) + stripped
        }

        /**
         * Raw Base58 encode (no checksum).
         */
        fun encode(input: ByteArray): String {
            if (input.isEmpty()) return ""

            var leadingZeros = 0
            for (b in input) {
                if (b == 0.toByte()) leadingZeros++ else break
            }

            var bi = java.math.BigInteger(1, input)
            val sb = StringBuilder()

            while (bi > java.math.BigInteger.ZERO) {
                val (quotient, remainder) = bi.divideAndRemainder(java.math.BigInteger.valueOf(58))
                sb.append(ALPHABET[remainder.toInt()])
                bi = quotient
            }

            repeat(leadingZeros) { sb.append('1') }

            return sb.reverse().toString()
        }

        fun sha256(data: ByteArray): ByteArray {
            return java.security.MessageDigest.getInstance("SHA-256").digest(data)
        }
    }

    // ── Fee Rate Conversion ──────────────────────────────────────────

    /**
     * Convert a sat/vB fee rate (potentially fractional, e.g. 0.5) to sat/kWU.
     * 1 sat/vB = 250 sat/kWU, so 0.8 sat/vB = 200 sat/kWU.
     * Result is clamped to at least 1 (minimum relay fee).
     */
    fun feeRateToSatPerKwu(satPerVb: Double): ULong {
        return kotlin.math.round(satPerVb * 250.0)
            .toLong()
            .coerceAtLeast(1L)
            .toULong()
    }

    /**
     * Compute exact fee in sats from a target rate (sat/vB) and measured vsize.
     * Uses round() so the effective rate (fee/vsize) is centered on the target
     * rather than always above it. Max error is +/-0.5/vsize sat/vB.
     * Returns null if vsize <= 0 or resulting fee <= 0.
     */
    fun computeExactFeeSats(targetSatPerVb: Double, vsize: Double): ULong? {
        if (vsize <= 0.0) return null
        val exactFee = kotlin.math.round(targetSatPerVb * vsize).toLong()
        return if (exactFee > 0) exactFee.toULong() else null
    }

    // ── Script Hash ──────────────────────────────────────────────────

    /**
     * Compute an Electrum-style script hash from raw scriptPubKey bytes.
     * Electrum uses reversed SHA-256 of the scriptPubKey as the "script hash".
     */
    fun computeScriptHash(scriptBytes: ByteArray): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(scriptBytes)
        return hash.reversedArray().joinToString("") { "%02x".format(it) }
    }

    // ── Descriptor String Building ───────────────────────────────────

    /**
     * Build a descriptor key expression with origin info.
     * Produces: [fingerprint/path]xpub
     * Uses "00000000" as fallback fingerprint when none is provided
     * (triggers SeedSigner's missing-fingerprint fallback).
     */
    fun buildKeyWithOrigin(
        xpubKey: String,
        fingerprint: String?,
        derivationPath: String?,
        addressType: AddressType,
    ): String {
        val effectiveFingerprint = fingerprint ?: "00000000"
        val path = derivationPath ?: addressType.accountPath
        return "[$effectiveFingerprint/$path]$xpubKey"
    }

    /**
     * Wrap a key expression in the appropriate descriptor function for the given address type.
     * Returns a pair of (external descriptor string, internal descriptor string).
     */
    fun buildDescriptorStrings(
        keyWithOrigin: String,
        addressType: AddressType,
    ): Pair<String, String> {
        return when (addressType) {
            AddressType.LEGACY -> Pair(
                "pkh($keyWithOrigin/0/*)",
                "pkh($keyWithOrigin/1/*)",
            )
            AddressType.SEGWIT -> Pair(
                "wpkh($keyWithOrigin/0/*)",
                "wpkh($keyWithOrigin/1/*)",
            )
            AddressType.TAPROOT -> Pair(
                "tr($keyWithOrigin/0/*)",
                "tr($keyWithOrigin/1/*)",
            )
        }
    }

    // ── Descriptor Parsing ───────────────────────────────────────────

    /**
     * Strip checksum suffix from a descriptor string.
     * e.g. "wpkh(...)#abcdef12" -> "wpkh(...)"
     */
    fun stripDescriptorChecksum(descriptor: String): String {
        val trimmed = descriptor.trim()
        val checksumSuffix = trimmed.substringAfterLast('#', "")
        return trimmed.removeSuffix("#$checksumSuffix").trim()
    }

    /**
     * Normalize Liquid watch-only descriptor input into a single descriptor string.
     *
     * Supports:
     * - A single combined `ct(...)` descriptor, with or without checksum
     * - A two-line external/internal pair such as Green exports
     *
     * Returns a checksum-free descriptor string, canonicalized to `<0;1>` for pairs.
     */
    fun normalizeLiquidDescriptorInput(input: String): String? {
        val descriptors =
            input.lineSequence()
                .map(::stripDescriptorChecksum)
                .map(String::trim)
                .filter(String::isNotBlank)
                .toList()

        if (descriptors.isEmpty()) return null
        if (descriptors.size == 1) {
            val descriptor = descriptors.single()
            return descriptor.takeIf(::isLiquidCtDescriptor)
        }
        if (descriptors.size != 2) return null

        return combineLiquidDescriptorPair(descriptors[0], descriptors[1])
    }

    /**
     * Combine a Green-style external/change Liquid descriptor pair into a single
     * BIP389-style multipath descriptor.
     */
    fun combineLiquidDescriptorPair(firstDescriptor: String, secondDescriptor: String): String? {
        val first = stripDescriptorChecksum(firstDescriptor)
        val second = stripDescriptorChecksum(secondDescriptor)
        if (!isLiquidCtDescriptor(first) || !isLiquidCtDescriptor(second)) return null

        val firstMatch = liquidDescriptorBranchRegex.find(first) ?: return null
        val secondMatch = liquidDescriptorBranchRegex.find(second) ?: return null
        val firstBranch = firstMatch.groupValues[1]
        val secondBranch = secondMatch.groupValues[1]
        if (firstBranch == secondBranch) return null

        val firstShape = first.replaceRange(firstMatch.range, "/<?>/*${firstMatch.groupValues[2]}")
        val secondShape = second.replaceRange(secondMatch.range, "/<?>/*${secondMatch.groupValues[2]}")
        if (firstShape != secondShape) return null

        val externalDescriptor = if (firstBranch == "0") first else second
        val externalMatch = if (firstBranch == "0") firstMatch else secondMatch
        return externalDescriptor.replaceRange(externalMatch.range, "/<0;1>/*${externalMatch.groupValues[2]}")
    }

    /**
     * Expand a stored Liquid CT descriptor into the dual external/internal descriptor format
     * used by wallets like Blockstream Green for display/export purposes.
     *
     * If the descriptor is already a single-branch CT descriptor, it is returned unchanged.
     * Multipath `<0;1>` and `<1;0>` descriptors are expanded into two lines.
     */
    fun formatLiquidDescriptorForDisplay(descriptor: String): String {
        val trimmed = stripDescriptorChecksum(descriptor)
        if (!isLiquidCtDescriptor(trimmed)) return descriptor.trim()

        return when {
            trimmed.contains("<0;1>") ->
                listOf(
                    trimmed.replace("<0;1>", "0"),
                    trimmed.replace("<0;1>", "1"),
                ).joinToString("\n")
            trimmed.contains("<1;0>") ->
                listOf(
                    trimmed.replace("<1;0>", "0"),
                    trimmed.replace("<1;0>", "1"),
                ).joinToString("\n")
            else -> trimmed
        }
    }

    private fun isLiquidCtDescriptor(descriptor: String): Boolean {
        val trimmed = descriptor.trim()
        return trimmed.lowercase().startsWith("ct(") && trimmed.endsWith(")")
    }

    /**
     * Check if a descriptor string is a full output descriptor
     * (starts with pkh(, wpkh(, or tr().
     */
    fun isFullDescriptor(input: String): Boolean {
        return supportedDescriptorPrefixes.any { input.trim().lowercase().startsWith(it) }
    }

    /**
     * Check if a descriptor contains BIP 389 multipath syntax (<0;1> or <1;0>).
     */
    fun isBip389Multipath(descriptor: String): Boolean {
        return descriptor.contains("<0;1>") || descriptor.contains("<1;0>")
    }

    /**
     * Determine if a BIP 389 multipath descriptor has reversed ordering (<1;0>).
     */
    fun isBip389Reversed(descriptor: String): Boolean {
        return descriptor.contains("<1;0>")
    }

    /**
     * Derive external/internal descriptor strings from a single full descriptor.
     * Handles:
     * - Descriptors with /0/wildcard) -> external; /1/wildcard) for internal
     * - Descriptors with /1/wildcard) -> internal; /0/wildcard) for external
     * - Descriptors with no child path -> appends /0/wildcard and /1/wildcard
     *
     * Returns pair of (external string, internal string).
     */
    fun deriveDescriptorPair(descriptor: String): Pair<String, String> {
        val trimmed = stripDescriptorChecksum(descriptor)

        return if (trimmed.contains("/0/*)")) {
            Pair(trimmed, trimmed.replace("/0/*)", "/1/*)"))
        } else if (trimmed.contains("/1/*)")) {
            Pair(trimmed.replace("/1/*)", "/0/*)"), trimmed)
        } else {
            // No child path specified - add /0/* and /1/*
            val base = trimmed.trimEnd(')')
            val closingParens = ")".repeat(trimmed.length - trimmed.trimEnd(')').length)
            Pair("$base/0/*$closingParens", "$base/1/*$closingParens")
        }
    }

    // ── Weight Estimation (Fallback) ─────────────────────────────────

    /**
     * Input weight in weight units for each address type.
     * These are reference values from bitcoinops.org/en/tools/calc-size/
     */
    fun inputWeightWU(addressType: AddressType): Long {
        return when (addressType) {
            AddressType.LEGACY -> 592L
            AddressType.SEGWIT -> 272L
            AddressType.TAPROOT -> 230L
        }
    }

    private fun compactSizeLength(value: Int): Int {
        require(value >= 0) { "CompactSize value must be non-negative" }
        return when {
            value <= 252 -> 1
            value <= 0xFFFF -> 3
            else -> 5
        }
    }

    /**
     * Estimate signed transaction vsize from known components when
     * actual signing is not possible (watch-only wallets).
     *
     * @param numInputs number of transaction inputs
     * @param inputWeightWU weight units per input (from inputWeightWU() or BDK maxWeightToSatisfy)
     * @param outputScriptLengths list of scriptPubKey byte lengths for each output
     * @param hasWitness true when the transaction contains at least one segwit/taproot input
     * @return estimated vsize (ceil of weight/4)
     */
    fun estimateVsizeFromComponents(
        numInputs: Int,
        inputWeightWU: Long,
        outputScriptLengths: List<Int>,
        hasWitness: Boolean,
    ): Double {
        val overheadBytes =
            4 + compactSizeLength(numInputs) + compactSizeLength(outputScriptLengths.size) + 4
        val overheadWU = (overheadBytes * 4L) + if (hasWitness) 2L else 0L

        var totalOutputWU = 0L
        for (scriptLen in outputScriptLengths) {
            totalOutputWU += (8L + compactSizeLength(scriptLen) + scriptLen) * 4L
        }

        val totalWU = overheadWU + (numInputs.toLong() * inputWeightWU) + totalOutputWU
        return kotlin.math.ceil(totalWU.toDouble() / 4.0)
    }

    // ── Backup JSON Parsing ──────────────────────────────────────────

    /**
     * Parsed wallet backup data — pure data extracted from JSON,
     * before any BDK/Android operations.
     */
    data class BackupWalletData(
        val name: String,
        val addressType: AddressType,
        val network: String, // "BITCOIN"
        val seedFormat: String, // "BIP39" etc.
        val keyMaterial: String,
        val isWatchOnly: Boolean,
        val customDerivationPath: String?,
        val passphrase: String? = null,
    )

    /**
     * Parse a backup JSON object into a BackupWalletData.
     * Extracts and validates fields with sensible defaults.
     * Throws if required fields (wallet, keyMaterial with mnemonic or xpub) are missing.
     *
     * @param walletJson the "wallet" sub-object
     * @param keyMaterialJson the "keyMaterial" sub-object
     */
    fun parseBackupJson(
        walletJson: org.json.JSONObject,
        keyMaterialJson: org.json.JSONObject,
    ): BackupWalletData {
        val addressType =
            parseSupportedAddressType(
                rawAddressType = walletJson.optString("addressType", AddressType.SEGWIT.name),
                default = AddressType.SEGWIT,
            )

        val network =
            parseSupportedWalletNetwork(
                rawNetwork = walletJson.optString("network", WalletNetwork.BITCOIN.name),
                default = WalletNetwork.BITCOIN,
            ).name

        val seedFormat = try {
            walletJson.optString("seedFormat", "BIP39").ifBlank { "BIP39" }
        } catch (_: Exception) {
            "BIP39"
        }

        val mnemonic = keyMaterialJson.optNonBlankString("mnemonic")
        val privateKey = keyMaterialJson.optNonBlankString("privateKey")
        val watchAddress = keyMaterialJson.optNonBlankString("watchAddress")
        val xpub = keyMaterialJson.optNonBlankString("extendedPublicKey")
        val passphrase = keyMaterialJson.optNonBlankString("passphrase")

        val keyMaterial = mnemonic ?: privateKey ?: watchAddress ?: xpub
            ?: throw IllegalStateException("No key material found in backup")

        val isWatchOnly = mnemonic == null && privateKey == null

        val customDerivationPath = walletJson.optNonBlankString("derivationPath")

        return BackupWalletData(
            name = walletJson.optString("name", "Restored Wallet"),
            addressType = addressType,
            network = network,
            seedFormat = seedFormat,
            keyMaterial = keyMaterial,
            isWatchOnly = isWatchOnly,
            customDerivationPath = customDerivationPath,
            passphrase = passphrase,
        )
    }

    private fun org.json.JSONObject.optNonBlankString(name: String): String? =
        optString(name, null.toString()).let { value ->
            if (value == "null" || value.isBlank()) null else value
        }

    // ── Fee Estimation JSON Parsing ──────────────────────────────────

    /**
     * Parsed fee estimates — pure data from mempool.space JSON.
     */
    data class ParsedFeeEstimates(
        val fastestFee: Double,
        val halfHourFee: Double,
        val hourFee: Double,
        val minimumFee: Double,
    )

    /**
     * Parse a mempool.space-compatible fee estimation JSON string.
     *
     * Both `/api/v1/fees/precise` and `/api/v1/fees/recommended` use the
     * same field names. Throws [IllegalArgumentException] when the response
     * is structurally invalid (missing required buckets, non-numeric values,
     * non-finite numbers, or implausible rates) so the caller can fall back
     * instead of presenting attacker-controlled fees as legitimate quick-pick
     * values.
     *
     * Buckets are coerced to a monotonically non-increasing series
     * (fastestFee >= halfHourFee >= hourFee >= minimumFee) and clamped to a
     * sanity ceiling.
     */
    fun parseFeeEstimatesJson(jsonString: String): ParsedFeeEstimates {
        val json =
            try {
                org.json.JSONObject(jsonString)
            } catch (e: Exception) {
                throw IllegalArgumentException("Fee response is not valid JSON", e)
            }

        fun required(name: String): Double {
            if (!json.has(name)) {
                throw IllegalArgumentException("Fee response missing required field: $name")
            }
            val value = json.optDouble(name, Double.NaN)
            if (value.isNaN() || value.isInfinite()) {
                throw IllegalArgumentException("Fee response has non-numeric $name")
            }
            if (value <= 0.0 || value > MAX_FEE_RATE_SAT_VB) {
                throw IllegalArgumentException("Fee response has out-of-range $name: $value")
            }
            return value
        }

        val fastest = required("fastestFee")
        val halfHour = required("halfHourFee")
        val hour = required("hourFee")
        val minimum = required("minimumFee")

        // Coerce to a non-increasing series. Some misbehaving providers return
        // hourFee > fastestFee; the wallet UI cannot make sense of that and the
        // safe interpretation is "use the higher rate" rather than letting the
        // user pick a 'cheap' bucket that is actually higher than fastest.
        val normalizedHalfHour = halfHour.coerceAtMost(fastest)
        val normalizedHour = hour.coerceAtMost(normalizedHalfHour)
        val normalizedMinimum = minimum.coerceAtMost(normalizedHour)

        return ParsedFeeEstimates(
            fastestFee = fastest,
            halfHourFee = normalizedHalfHour,
            hourFee = normalizedHour,
            minimumFee = normalizedMinimum,
        )
    }

    private const val MAX_FEE_RATE_SAT_VB = 10_000.0
}
