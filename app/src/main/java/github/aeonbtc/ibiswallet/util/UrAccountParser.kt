package github.aeonbtc.ibiswallet.util

import android.util.Log
import com.sparrowwallet.hummingbird.UR
import com.sparrowwallet.hummingbird.registry.CryptoAccount
import com.sparrowwallet.hummingbird.registry.CryptoCoinInfo
import com.sparrowwallet.hummingbird.registry.CryptoHDKey
import com.sparrowwallet.hummingbird.registry.CryptoOutput
import com.sparrowwallet.hummingbird.registry.ScriptExpression
import com.sparrowwallet.hummingbird.registry.URAccountDescriptor
import com.sparrowwallet.hummingbird.registry.UROutputDescriptor
import com.sparrowwallet.hummingbird.registry.pathcomponent.IndexPathComponent
import github.aeonbtc.ibiswallet.BuildConfig
import github.aeonbtc.ibiswallet.data.model.AddressType
import java.math.BigInteger
import java.security.MessageDigest

/**
 * Parses Blockchain Commons UR types (crypto-account, crypto-hdkey, crypto-output)
 * into text-form descriptors/key-origin strings that the existing import pipeline handles.
 *
 * Supports both v1 (crypto-account/crypto-hdkey/crypto-output) and v2
 * (account-descriptor/hdkey/output-descriptor) UR types from Hummingbird.
 *
 * Used by hardware wallets: Keystone, Foundation Passport, SeedSigner, Jade.
 */
object UrAccountParser {
    private const val TAG = "UrAccountParser"

    /**
     * Result of parsing a UR into text-form key material.
     *
     * @param keyMaterial The text-form descriptor or key-origin string
     *   (e.g., "[fp/path]xpub..." or "wpkh([fp/path]xpub/0/star)")
     * @param fingerprint Master key fingerprint (8 hex chars), if found
     * @param detectedAddressType The address type detected from the UR script expressions, if any
     */
    data class ParsedUrResult(
        val keyMaterial: String,
        val fingerprint: String?,
        val detectedAddressType: AddressType? = null,
    )

    /**
     * Parse a decoded UR based on its type. Routes to the appropriate parser.
     *
     * @param ur The decoded UR object from Hummingbird's URDecoder
     * @param preferredAddressType The address type selected on the import screen,
     *   used to pick the matching descriptor from crypto-account bundles
     * @return ParsedUrResult with text-form key material, or null if unsupported type
     */
    fun parseUr(
        ur: UR,
        preferredAddressType: AddressType,
    ): ParsedUrResult? {
        return try {
            when (ur.type) {
                "crypto-account" -> parseCryptoAccountV1(ur, preferredAddressType)
                "account-descriptor" -> parseAccountDescriptorV2(ur, preferredAddressType)
                "crypto-hdkey" -> parseCryptoHdKeyV1(ur)
                "hdkey" -> parseHdKeyV2(ur)
                "crypto-output" -> parseCryptoOutputV1(ur)
                "output-descriptor" -> parseOutputDescriptorV2(ur)
                else -> {
                    if (BuildConfig.DEBUG) Log.w(TAG, "Unsupported UR type: ${ur.type}")
                    null
                }
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Failed to parse UR type ${ur.type}", e)
            null
        }
    }

    // ── v1 parsers (crypto-account, crypto-hdkey, crypto-output) ──

    /**
     * Parse ur:crypto-account (v1, BCR-2020-015).
     * Contains master fingerprint + array of CryptoOutput descriptors.
     * Selects the descriptor matching [preferredAddressType].
     */
    private fun parseCryptoAccountV1(
        ur: UR,
        preferredAddressType: AddressType,
    ): ParsedUrResult? {
        val account = ur.decodeFromRegistry() as? CryptoAccount ?: return null
        val masterFp = account.masterFingerprint?.toHexString() ?: return null
        val outputs = account.outputDescriptors ?: return null

        if (outputs.isEmpty()) {
            if (BuildConfig.DEBUG) Log.w(TAG, "crypto-account has no output descriptors")
            return null
        }

        // Find the output descriptor matching the preferred address type
        val matched =
            outputs.firstOrNull { output ->
                scriptExpressionsToAddressType(output.scriptExpressions) == preferredAddressType
            }

        // Fall back to first available singlesig descriptor
        val selectedOutput =
            matched ?: outputs.firstOrNull { output ->
                val expressions = output.scriptExpressions
                expressions.none {
                    it == ScriptExpression.MULTISIG ||
                        it == ScriptExpression.SORTED_MULTISIG ||
                        it == ScriptExpression.COSIGNER
                } && output.hdKey != null
            } ?: outputs.firstOrNull { it.hdKey != null }

        if (selectedOutput == null) {
            if (BuildConfig.DEBUG) Log.w(TAG, "No suitable output descriptor found in crypto-account")
            return null
        }

        val detectedType = scriptExpressionsToAddressType(selectedOutput.scriptExpressions)
        val descriptorStr =
            cryptoOutputToDescriptorString(selectedOutput, masterFp)
                ?: return null

        return ParsedUrResult(
            keyMaterial = descriptorStr,
            fingerprint = masterFp,
            detectedAddressType = detectedType,
        )
    }

    /**
     * Parse ur:crypto-hdkey (v1, BCR-2020-007).
     * Contains a single HD key with optional origin info.
     */
    private fun parseCryptoHdKeyV1(ur: UR): ParsedUrResult? {
        val hdKey = ur.decodeFromRegistry() as? CryptoHDKey ?: return null
        return hdKeyToResult(hdKey)
    }

    /**
     * Parse ur:crypto-output (v1, BCR-2020-010).
     * Contains a single output descriptor (script type + key).
     */
    private fun parseCryptoOutputV1(ur: UR): ParsedUrResult? {
        val output = ur.decodeFromRegistry() as? CryptoOutput ?: return null
        val detectedType = scriptExpressionsToAddressType(output.scriptExpressions)
        val fingerprint = output.hdKey?.origin?.sourceFingerprint?.toHexString()
        val descriptorStr =
            cryptoOutputToDescriptorString(output, fingerprint)
                ?: return null

        return ParsedUrResult(
            keyMaterial = descriptorStr,
            fingerprint = fingerprint,
            detectedAddressType = detectedType,
        )
    }

    // ── v2 parsers (account-descriptor, hdkey, output-descriptor) ──

    /**
     * Parse ur:account-descriptor (v2, BCR-2023-019).
     * Contains master fingerprint + array of UROutputDescriptor.
     * v2 output descriptors have a text-form `source` field (e.g., "wpkh(@0)")
     * and a separate `keys` list, making reconstruction straightforward.
     */
    private fun parseAccountDescriptorV2(
        ur: UR,
        preferredAddressType: AddressType,
    ): ParsedUrResult? {
        val account = ur.decodeFromRegistry() as? URAccountDescriptor ?: return null
        val masterFp = account.masterFingerprint?.toHexString() ?: return null
        val descriptors = account.outputDescriptors ?: return null

        if (descriptors.isEmpty()) {
            if (BuildConfig.DEBUG) Log.w(TAG, "account-descriptor has no output descriptors")
            return null
        }

        // Try to match preferred address type from the source string
        val matched =
            descriptors.firstOrNull { desc ->
                val source = desc.source?.lowercase() ?: return@firstOrNull false
                sourceToAddressType(source) == preferredAddressType
            }

        // Fall back to first singlesig descriptor
        val selected =
            matched ?: descriptors.firstOrNull { desc ->
                val source = desc.source?.lowercase() ?: return@firstOrNull false
                !source.contains("multi") && !source.contains("cosigner")
            } ?: descriptors.firstOrNull()

        if (selected == null) return null

        val detectedType = selected.source?.let { sourceToAddressType(it.lowercase()) }
        val descriptorStr = urOutputDescriptorToString(selected, masterFp) ?: return null

        return ParsedUrResult(
            keyMaterial = descriptorStr,
            fingerprint = masterFp,
            detectedAddressType = detectedType,
        )
    }

    /**
     * Parse ur:hdkey (v2, BCR-2020-007 updated).
     * Extends CryptoHDKey -- same structure, different UR type string.
     */
    private fun parseHdKeyV2(ur: UR): ParsedUrResult? {
        val hdKey = ur.decodeFromRegistry() as? CryptoHDKey ?: return null
        return hdKeyToResult(hdKey)
    }

    /**
     * Parse ur:output-descriptor (v2, BCR-2023-010).
     * Has a text `source` field and separate keys list.
     */
    private fun parseOutputDescriptorV2(ur: UR): ParsedUrResult? {
        val desc = ur.decodeFromRegistry() as? UROutputDescriptor ?: return null
        val fingerprint =
            desc.keys?.filterIsInstance<CryptoHDKey>()?.firstOrNull()
                ?.origin?.sourceFingerprint?.toHexString()
        val detectedType = desc.source?.let { sourceToAddressType(it.lowercase()) }
        val descriptorStr = urOutputDescriptorToString(desc, fingerprint) ?: return null

        return ParsedUrResult(
            keyMaterial = descriptorStr,
            fingerprint = fingerprint,
            detectedAddressType = detectedType,
        )
    }

    // ── Shared helpers ──

    /**
     * Convert a CryptoHDKey (v1 or v2) to a ParsedUrResult.
     */
    private fun hdKeyToResult(hdKey: CryptoHDKey): ParsedUrResult? {
        val xpub = hdKeyToXpub(hdKey) ?: return null
        val fingerprint = hdKey.origin?.sourceFingerprint?.toHexString()
        val keyStr = hdKeyToKeyOriginString(hdKey, xpub, null)

        return ParsedUrResult(
            keyMaterial = keyStr,
            fingerprint = fingerprint,
        )
    }

    /**
     * Reconstruct a BIP32 Base58Check xpub/tpub from CryptoHDKey fields.
     *
     * BIP32 serialization (78 bytes):
     *   4 bytes version | 1 byte depth | 4 bytes parent fingerprint |
     *   4 bytes child number | 32 bytes chain code | 33 bytes key data
     */
    private fun hdKeyToXpub(hdKey: CryptoHDKey): String? {
        val keyData = hdKey.key ?: return null
        val chainCode = hdKey.chainCode ?: return null

        if (keyData.size != 33 || chainCode.size != 32) return null

        // Determine version bytes (mainnet vs testnet)
        val isTestnet = hdKey.useInfo?.network == CryptoCoinInfo.Network.TESTNET
        val version =
            if (isTestnet) {
                byteArrayOf(0x04, 0x35, 0x87.toByte(), 0xCF.toByte()) // tpub
            } else {
                byteArrayOf(0x04, 0x88.toByte(), 0xB2.toByte(), 0x1E) // xpub
            }

        // Depth: number of derivation steps in origin path
        val origin = hdKey.origin
        val depth =
            origin?.depth
                ?: origin?.components?.count { it is IndexPathComponent }
                ?: 0
        val depthByte = byteArrayOf(depth.toByte())

        // Parent fingerprint (4 bytes)
        val parentFp =
            hdKey.parentFingerprint
                ?: byteArrayOf(0x00, 0x00, 0x00, 0x00)

        // Child number: last IndexPathComponent of the derivation path
        val lastComponent =
            origin?.components?.lastOrNull { it is IndexPathComponent }
                as? IndexPathComponent
        val childNumber =
            if (lastComponent != null) {
                val childIdx =
                    if (lastComponent.isHardened) {
                        (lastComponent.index or 0x80000000.toInt())
                    } else {
                        lastComponent.index
                    }
                ByteArray(4).also {
                    it[0] = (childIdx shr 24 and 0xFF).toByte()
                    it[1] = (childIdx shr 16 and 0xFF).toByte()
                    it[2] = (childIdx shr 8 and 0xFF).toByte()
                    it[3] = (childIdx and 0xFF).toByte()
                }
            } else {
                byteArrayOf(0x00, 0x00, 0x00, 0x00)
            }

        // Assemble the 78-byte payload
        val payload = version + depthByte + parentFp + childNumber + chainCode + keyData

        return base58CheckEncode(payload)
    }

    /**
     * Build a key-origin string: [fingerprint/derivation]xpub
     * Falls back to bare xpub if no origin info available.
     */
    private fun hdKeyToKeyOriginString(
        hdKey: CryptoHDKey,
        xpub: String,
        fallbackFingerprint: String?,
    ): String {
        val origin = hdKey.origin
        val fingerprint =
            origin?.sourceFingerprint?.toHexString()
                ?: fallbackFingerprint

        if (fingerprint != null && origin != null) {
            val path = origin.path
            if (path != null) {
                return "[$fingerprint/$path]$xpub"
            }
        }

        if (fingerprint != null) {
            return "[$fingerprint]$xpub"
        }

        return xpub
    }

    /**
     * Convert a v1 CryptoOutput to a text-form output descriptor string.
     * e.g., wpkh([37b5eed4/84'/0'/0']xpub6BkU.../0/wildcard)
     */
    private fun cryptoOutputToDescriptorString(
        output: CryptoOutput,
        fallbackFingerprint: String?,
    ): String? {
        val hdKey = output.hdKey ?: return null
        val xpub = hdKeyToXpub(hdKey) ?: return null
        val keyOrigin = hdKeyToKeyOriginString(hdKey, xpub, fallbackFingerprint)

        // Build child path suffix from CryptoHDKey.children if present
        val childrenPath = hdKey.children?.path
        val keyWithChildren =
            if (childrenPath != null) {
                "$keyOrigin/$childrenPath"
            } else {
                // Default: add /0/* for receive derivation
                "$keyOrigin/0/*"
            }

        // Build the script expression wrappers
        val expressions = output.scriptExpressions
        if (expressions.isNullOrEmpty()) {
            return keyOrigin
        }

        // Skip cosigner expressions (used for multisig placeholders in crypto-account)
        val scriptExpressions = expressions.filter { it != ScriptExpression.COSIGNER }
        if (scriptExpressions.isEmpty()) {
            return keyOrigin
        }

        // Build nested descriptor: expressions list is [outermost, ..., innermost]
        // Wrap from innermost outward
        var result = keyWithChildren
        for (expr in scriptExpressions.reversed()) {
            result = "${expr.expression}($result)"
        }

        return result
    }

    /**
     * Convert a v2 UROutputDescriptor to a text-form descriptor string.
     * v2 descriptors have a `source` field like "wpkh(@0)" where @0 is a placeholder
     * for the first key, and a `keys` list with the actual HD keys.
     */
    private fun urOutputDescriptorToString(
        desc: UROutputDescriptor,
        fallbackFingerprint: String?,
    ): String? {
        val source = desc.source ?: return null
        val keys = desc.keys

        if (keys.isNullOrEmpty()) {
            // No separate keys -- source might be a complete descriptor already
            return source
        }

        // Replace @0, @1, etc. with the actual key-origin strings
        var result = source
        keys.forEachIndexed { index, registryItem ->
            val hdKey = registryItem as? CryptoHDKey ?: return@forEachIndexed
            val xpub = hdKeyToXpub(hdKey) ?: return@forEachIndexed
            val fp = hdKey.origin?.sourceFingerprint?.toHexString() ?: fallbackFingerprint
            val keyOrigin = hdKeyToKeyOriginString(hdKey, xpub, fp)

            // Build the key expression with children path
            val childrenPath = hdKey.children?.path
            val fullKey =
                if (childrenPath != null) {
                    "$keyOrigin/$childrenPath"
                } else {
                    "$keyOrigin/0/*"
                }

            result = result.replace("@$index", fullKey)
        }

        return result
    }

    /**
     * Map v1 CryptoOutput script expressions to an AddressType.
     */
    private fun scriptExpressionsToAddressType(expressions: List<ScriptExpression>?): AddressType? {
        if (expressions.isNullOrEmpty()) return null

        // Filter out cosigner wrapper
        return when (expressions.filter { it != ScriptExpression.COSIGNER }) {
            listOf(ScriptExpression.WITNESS_PUBLIC_KEY_HASH) ->
                AddressType.SEGWIT
            listOf(
                ScriptExpression.SCRIPT_HASH,
                ScriptExpression.WITNESS_PUBLIC_KEY_HASH,
            ) ->
                AddressType.NESTED_SEGWIT
            listOf(ScriptExpression.PUBLIC_KEY_HASH) ->
                AddressType.LEGACY
            listOf(ScriptExpression.TAPROOT) ->
                AddressType.TAPROOT
            else -> null
        }
    }

    /**
     * Map v2 source string (e.g., "wpkh(@0)") to an AddressType.
     */
    private fun sourceToAddressType(source: String): AddressType? {
        return when {
            source.startsWith("wpkh(") -> AddressType.SEGWIT
            source.startsWith("sh(wpkh(") -> AddressType.NESTED_SEGWIT
            source.startsWith("pkh(") -> AddressType.LEGACY
            source.startsWith("tr(") -> AddressType.TAPROOT
            else -> null
        }
    }

    // ── Base58Check encoding ──

    private const val BASE58_ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"

    private fun base58CheckEncode(payload: ByteArray): String {
        val checksum = sha256(sha256(payload)).sliceArray(0 until 4)
        val data = payload + checksum
        return base58Encode(data)
    }

    private fun base58Encode(data: ByteArray): String {
        var num = BigInteger(1, data)
        val sb = StringBuilder()
        val fiftyEight = BigInteger.valueOf(58)

        while (num > BigInteger.ZERO) {
            val (quotient, remainder) = num.divideAndRemainder(fiftyEight)
            sb.append(BASE58_ALPHABET[remainder.toInt()])
            num = quotient
        }

        // Preserve leading zeros
        for (b in data) {
            if (b.toInt() == 0) {
                sb.append(BASE58_ALPHABET[0])
            } else {
                break
            }
        }

        return sb.reverse().toString()
    }

    private fun sha256(data: ByteArray): ByteArray {
        return MessageDigest.getInstance("SHA-256").digest(data)
    }

    private fun ByteArray.toHexString(): String {
        return joinToString("") { "%02x".format(it) }
    }
}
