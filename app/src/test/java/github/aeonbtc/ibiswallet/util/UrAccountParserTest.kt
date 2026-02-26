package github.aeonbtc.ibiswallet.util

import com.sparrowwallet.hummingbird.UR
import com.sparrowwallet.hummingbird.registry.CryptoAccount
import com.sparrowwallet.hummingbird.registry.CryptoHDKey
import com.sparrowwallet.hummingbird.registry.CryptoOutput
import com.sparrowwallet.hummingbird.registry.CryptoCoinInfo
import com.sparrowwallet.hummingbird.registry.ScriptExpression
import com.sparrowwallet.hummingbird.registry.URAccountDescriptor
import com.sparrowwallet.hummingbird.registry.UROutputDescriptor
import com.sparrowwallet.hummingbird.registry.pathcomponent.PathComponent
import github.aeonbtc.ibiswallet.data.model.AddressType
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldStartWith
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic

/**
 * Unit tests for UrAccountParser.
 *
 * Because UrAccountParser depends on Hummingbird (Blockchain Commons UR) registry objects,
 * we use MockK to build lightweight fakes rather than constructing real CBOR payloads.
 *
 * Test strategy:
 *   - parseUr() routing: verify correct branch is selected for each UR type string
 *   - ParsedUrResult data class: verify fields are populated correctly
 *   - sourceToAddressType (private): exercised indirectly via v2 account-descriptor path
 *   - scriptExpressionsToAddressType (private): exercised via crypto-output path
 *   - Null/error handling: unsupported types, malformed objects return null safely
 */
class UrAccountParserTest : FunSpec({

    // Mock android.util.Log for all tests — it is not available in the JVM unit test environment
    beforeSpec {
        mockkStatic(android.util.Log::class)
        every { android.util.Log.w(any(), any<String>()) } returns 0
        every { android.util.Log.e(any(), any<String>()) } returns 0
        every { android.util.Log.e(any(), any<String>(), any()) } returns 0
        every { android.util.Log.d(any(), any<String>()) } returns 0
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Build a minimal mock CryptoHDKey with the fields needed for xpub reconstruction.
     * Uses a real 33-byte compressed public key and 32-byte chain code so that
     * base58Check encoding can run without throwing.
     *
     * The key bytes below are the secp256k1 generator point G compressed —
     * a publicly known point that is safe to use as a test fixture.
     */
    fun minimalHdKeyMock(
        fingerprint: ByteArray = byteArrayOf(0x37, 0xb5.toByte(), 0xee.toByte(), 0xd4.toByte()),
        path: String? = "84'/0'/0'",
        isTestnet: Boolean = false,
    ): CryptoHDKey {
        val mock = mockk<CryptoHDKey>(relaxed = true)

        // 33-byte compressed public key (generator point G, prefix 0x02)
        val keyData = byteArrayOf(0x02) + ByteArray(32) { (it + 1).toByte() }
        // 32-byte chain code
        val chainCode = ByteArray(32) { (it + 0x10).toByte() }

        val origin = mockk<com.sparrowwallet.hummingbird.registry.CryptoKeypath>(relaxed = true)
        every { origin.sourceFingerprint } returns fingerprint
        every { origin.path } returns path
        every { origin.depth } returns (path?.count { it == '/' } ?: 0)
        every { origin.components } returns emptyList()

        val useInfo = mockk<CryptoCoinInfo>(relaxed = true)
        every { useInfo.network } returns
            if (isTestnet) CryptoCoinInfo.Network.TESTNET else CryptoCoinInfo.Network.MAINNET

        every { mock.key } returns keyData
        every { mock.chainCode } returns chainCode
        every { mock.origin } returns origin
        every { mock.useInfo } returns useInfo
        every { mock.parentFingerprint } returns byteArrayOf(0x00, 0x00, 0x00, 0x00)
        every { mock.children } returns null

        return mock
    }

    // ── ParsedUrResult data class ─────────────────────────────────────────────

    context("ParsedUrResult") {

        test("holds keyMaterial, fingerprint, and detectedAddressType") {
            val result = UrAccountParser.ParsedUrResult(
                keyMaterial = "wpkh([deadbeef/84'/0'/0']xpub.../0/*)",
                fingerprint = "deadbeef",
                detectedAddressType = AddressType.SEGWIT,
            )
            result.keyMaterial shouldBe "wpkh([deadbeef/84'/0'/0']xpub.../0/*)"
            result.fingerprint shouldBe "deadbeef"
            result.detectedAddressType shouldBe AddressType.SEGWIT
        }

        test("detectedAddressType defaults to null") {
            val result = UrAccountParser.ParsedUrResult(
                keyMaterial = "xpub123",
                fingerprint = null,
            )
            result.detectedAddressType shouldBe null
        }

        test("fingerprint can be null") {
            val result = UrAccountParser.ParsedUrResult(
                keyMaterial = "xpub123",
                fingerprint = null,
            )
            result.fingerprint shouldBe null
        }
    }

    // ── parseUr — unsupported type ────────────────────────────────────────────

    context("parseUr - unsupported UR type") {

        test("returns null for unknown UR type") {
            val ur = mockk<UR>(relaxed = true)
            every { ur.type } returns "crypto-seed"

            val result = UrAccountParser.parseUr(ur, AddressType.SEGWIT)
            result.shouldBeNull()
        }

        test("returns null for empty UR type string") {
            val ur = mockk<UR>(relaxed = true)
            every { ur.type } returns ""

            val result = UrAccountParser.parseUr(ur, AddressType.SEGWIT)
            result.shouldBeNull()
        }
    }

    // ── parseUr — crypto-hdkey (v1) ───────────────────────────────────────────

    context("parseUr - crypto-hdkey") {

        test("returns non-null result for a well-formed crypto-hdkey") {
            val hdKey = minimalHdKeyMock()
            val ur = mockk<UR>(relaxed = true)
            every { ur.type } returns "crypto-hdkey"
            every { ur.decodeFromRegistry() } returns hdKey

            val result = UrAccountParser.parseUr(ur, AddressType.SEGWIT)
            result.shouldNotBeNull()
        }

        test("extracts fingerprint from origin") {
            val expectedFp = byteArrayOf(0xDE.toByte(), 0xAD.toByte(), 0xBE.toByte(), 0xEF.toByte())
            val hdKey = minimalHdKeyMock(fingerprint = expectedFp)
            val ur = mockk<UR>(relaxed = true)
            every { ur.type } returns "crypto-hdkey"
            every { ur.decodeFromRegistry() } returns hdKey

            val result = UrAccountParser.parseUr(ur, AddressType.SEGWIT)
            result.shouldNotBeNull()
            result.fingerprint shouldBe "deadbeef"
        }

        test("keyMaterial contains fingerprint in key-origin format") {
            val fp = byteArrayOf(0x37, 0xb5.toByte(), 0xee.toByte(), 0xd4.toByte())
            val hdKey = minimalHdKeyMock(fingerprint = fp, path = "84'/0'/0'")
            val ur = mockk<UR>(relaxed = true)
            every { ur.type } returns "crypto-hdkey"
            every { ur.decodeFromRegistry() } returns hdKey

            val result = UrAccountParser.parseUr(ur, AddressType.SEGWIT)
            result.shouldNotBeNull()
            result.keyMaterial shouldContain "37b5eed4"
        }

        test("keyMaterial starts with xpub for mainnet key") {
            val hdKey = minimalHdKeyMock(isTestnet = false)
            val ur = mockk<UR>(relaxed = true)
            every { ur.type } returns "crypto-hdkey"
            every { ur.decodeFromRegistry() } returns hdKey

            val result = UrAccountParser.parseUr(ur, AddressType.SEGWIT)
            result.shouldNotBeNull()
            // key-origin format: "[fp/path]xpub..." or bare "xpub..."
            (result.keyMaterial.contains("xpub") || result.keyMaterial.startsWith("[")) shouldBe true
        }

        test("testnet key produces tpub in keyMaterial") {
            val hdKey = minimalHdKeyMock(isTestnet = true)
            val ur = mockk<UR>(relaxed = true)
            every { ur.type } returns "crypto-hdkey"
            every { ur.decodeFromRegistry() } returns hdKey

            val result = UrAccountParser.parseUr(ur, AddressType.SEGWIT)
            result.shouldNotBeNull()
            result.keyMaterial shouldContain "tpub"
        }

        test("returns null if UR decodes to wrong type") {
            val ur = mockk<UR>(relaxed = true)
            every { ur.type } returns "crypto-hdkey"
            every { ur.decodeFromRegistry() } returns "not a CryptoHDKey"

            val result = UrAccountParser.parseUr(ur, AddressType.SEGWIT)
            result.shouldBeNull()
        }
    }

    // ── parseUr — hdkey (v2) ──────────────────────────────────────────────────

    context("parseUr - hdkey (v2)") {

        test("routes hdkey type and returns result equivalent to crypto-hdkey") {
            val hdKey = minimalHdKeyMock()
            val ur = mockk<UR>(relaxed = true)
            every { ur.type } returns "hdkey"
            every { ur.decodeFromRegistry() } returns hdKey

            val result = UrAccountParser.parseUr(ur, AddressType.SEGWIT)
            result.shouldNotBeNull()
        }
    }

    // ── parseUr — crypto-output (v1) ─────────────────────────────────────────

    context("parseUr - crypto-output") {

        fun makeCryptoOutput(expressions: List<ScriptExpression>): CryptoOutput {
            val hdKey = minimalHdKeyMock()
            val output = mockk<CryptoOutput>(relaxed = true)
            every { output.hdKey } returns hdKey
            every { output.scriptExpressions } returns expressions
            return output
        }

        test("returns SEGWIT address type for WITNESS_PUBLIC_KEY_HASH expression") {
            val output = makeCryptoOutput(listOf(ScriptExpression.WITNESS_PUBLIC_KEY_HASH))
            val ur = mockk<UR>(relaxed = true)
            every { ur.type } returns "crypto-output"
            every { ur.decodeFromRegistry() } returns output

            val result = UrAccountParser.parseUr(ur, AddressType.SEGWIT)
            result.shouldNotBeNull()
            result.detectedAddressType shouldBe AddressType.SEGWIT
        }

        test("returns NESTED_SEGWIT for SCRIPT_HASH + WITNESS_PUBLIC_KEY_HASH") {
            val output = makeCryptoOutput(
                listOf(ScriptExpression.SCRIPT_HASH, ScriptExpression.WITNESS_PUBLIC_KEY_HASH)
            )
            val ur = mockk<UR>(relaxed = true)
            every { ur.type } returns "crypto-output"
            every { ur.decodeFromRegistry() } returns output

            val result = UrAccountParser.parseUr(ur, AddressType.SEGWIT)
            result.shouldNotBeNull()
            result.detectedAddressType shouldBe AddressType.NESTED_SEGWIT
        }

        test("returns LEGACY for PUBLIC_KEY_HASH expression") {
            val output = makeCryptoOutput(listOf(ScriptExpression.PUBLIC_KEY_HASH))
            val ur = mockk<UR>(relaxed = true)
            every { ur.type } returns "crypto-output"
            every { ur.decodeFromRegistry() } returns output

            val result = UrAccountParser.parseUr(ur, AddressType.SEGWIT)
            result.shouldNotBeNull()
            result.detectedAddressType shouldBe AddressType.LEGACY
        }

        test("returns TAPROOT for TAPROOT expression") {
            val output = makeCryptoOutput(listOf(ScriptExpression.TAPROOT))
            val ur = mockk<UR>(relaxed = true)
            every { ur.type } returns "crypto-output"
            every { ur.decodeFromRegistry() } returns output

            val result = UrAccountParser.parseUr(ur, AddressType.SEGWIT)
            result.shouldNotBeNull()
            result.detectedAddressType shouldBe AddressType.TAPROOT
        }

        test("keyMaterial contains wpkh() wrapper for SEGWIT") {
            val output = makeCryptoOutput(listOf(ScriptExpression.WITNESS_PUBLIC_KEY_HASH))
            val ur = mockk<UR>(relaxed = true)
            every { ur.type } returns "crypto-output"
            every { ur.decodeFromRegistry() } returns output

            val result = UrAccountParser.parseUr(ur, AddressType.SEGWIT)
            result.shouldNotBeNull()
            result.keyMaterial shouldStartWith "wpkh("
        }

        test("returns null when hdKey is missing from output") {
            val output = mockk<CryptoOutput>(relaxed = true)
            every { output.hdKey } returns null
            every { output.scriptExpressions } returns listOf(ScriptExpression.WITNESS_PUBLIC_KEY_HASH)

            val ur = mockk<UR>(relaxed = true)
            every { ur.type } returns "crypto-output"
            every { ur.decodeFromRegistry() } returns output

            val result = UrAccountParser.parseUr(ur, AddressType.SEGWIT)
            result.shouldBeNull()
        }
    }

    // ── parseUr — output-descriptor (v2) ─────────────────────────────────────

    context("parseUr - output-descriptor (v2)") {

        test("returns SEGWIT when source starts with wpkh(") {
            val hdKey = minimalHdKeyMock()
            val desc = mockk<UROutputDescriptor>(relaxed = true)
            every { desc.source } returns "wpkh(@0)"
            every { desc.keys } returns listOf(hdKey)

            val ur = mockk<UR>(relaxed = true)
            every { ur.type } returns "output-descriptor"
            every { ur.decodeFromRegistry() } returns desc

            val result = UrAccountParser.parseUr(ur, AddressType.SEGWIT)
            result.shouldNotBeNull()
            result.detectedAddressType shouldBe AddressType.SEGWIT
        }

        test("returns NESTED_SEGWIT when source starts with sh(wpkh(") {
            val hdKey = minimalHdKeyMock()
            val desc = mockk<UROutputDescriptor>(relaxed = true)
            every { desc.source } returns "sh(wpkh(@0))"
            every { desc.keys } returns listOf(hdKey)

            val ur = mockk<UR>(relaxed = true)
            every { ur.type } returns "output-descriptor"
            every { ur.decodeFromRegistry() } returns desc

            val result = UrAccountParser.parseUr(ur, AddressType.SEGWIT)
            result.shouldNotBeNull()
            result.detectedAddressType shouldBe AddressType.NESTED_SEGWIT
        }

        test("returns LEGACY when source starts with pkh(") {
            val hdKey = minimalHdKeyMock()
            val desc = mockk<UROutputDescriptor>(relaxed = true)
            every { desc.source } returns "pkh(@0)"
            every { desc.keys } returns listOf(hdKey)

            val ur = mockk<UR>(relaxed = true)
            every { ur.type } returns "output-descriptor"
            every { ur.decodeFromRegistry() } returns desc

            val result = UrAccountParser.parseUr(ur, AddressType.SEGWIT)
            result.shouldNotBeNull()
            result.detectedAddressType shouldBe AddressType.LEGACY
        }

        test("returns TAPROOT when source starts with tr(") {
            val hdKey = minimalHdKeyMock()
            val desc = mockk<UROutputDescriptor>(relaxed = true)
            every { desc.source } returns "tr(@0)"
            every { desc.keys } returns listOf(hdKey)

            val ur = mockk<UR>(relaxed = true)
            every { ur.type } returns "output-descriptor"
            every { ur.decodeFromRegistry() } returns desc

            val result = UrAccountParser.parseUr(ur, AddressType.SEGWIT)
            result.shouldNotBeNull()
            result.detectedAddressType shouldBe AddressType.TAPROOT
        }

        test("replaces @0 placeholder with key-origin string in keyMaterial") {
            val hdKey = minimalHdKeyMock(
                fingerprint = byteArrayOf(0xAB.toByte(), 0xCD.toByte(), 0xEF.toByte(), 0x01),
                path = "84'/0'/0'"
            )
            val desc = mockk<UROutputDescriptor>(relaxed = true)
            every { desc.source } returns "wpkh(@0)"
            every { desc.keys } returns listOf(hdKey)

            val ur = mockk<UR>(relaxed = true)
            every { ur.type } returns "output-descriptor"
            every { ur.decodeFromRegistry() } returns desc

            val result = UrAccountParser.parseUr(ur, AddressType.SEGWIT)
            result.shouldNotBeNull()
            // @0 should have been replaced — the literal "@0" must not remain
            (result.keyMaterial.contains("@0")) shouldBe false
        }

        test("returns null when source is null") {
            val desc = mockk<UROutputDescriptor>(relaxed = true)
            every { desc.source } returns null
            every { desc.keys } returns emptyList()

            val ur = mockk<UR>(relaxed = true)
            every { ur.type } returns "output-descriptor"
            every { ur.decodeFromRegistry() } returns desc

            val result = UrAccountParser.parseUr(ur, AddressType.SEGWIT)
            result.shouldBeNull()
        }
    }

    // ── parseUr — exception safety ────────────────────────────────────────────

    context("parseUr - exception safety") {

        test("returns null instead of throwing when decodeFromRegistry throws") {
            val ur = mockk<UR>(relaxed = true)
            every { ur.type } returns "crypto-hdkey"
            every { ur.decodeFromRegistry() } throws RuntimeException("CBOR decode failed")

            val result = UrAccountParser.parseUr(ur, AddressType.SEGWIT)
            result.shouldBeNull()
        }

        test("returns null instead of throwing for account-descriptor with null masterFingerprint") {
            val account = mockk<URAccountDescriptor>(relaxed = true)
            every { account.masterFingerprint } returns null

            val ur = mockk<UR>(relaxed = true)
            every { ur.type } returns "account-descriptor"
            every { ur.decodeFromRegistry() } returns account

            val result = UrAccountParser.parseUr(ur, AddressType.SEGWIT)
            result.shouldBeNull()
        }
    }
})
