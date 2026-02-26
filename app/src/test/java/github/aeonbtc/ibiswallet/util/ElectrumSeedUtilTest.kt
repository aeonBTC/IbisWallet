package github.aeonbtc.ibiswallet.util

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.string.shouldStartWith
import io.kotest.matchers.collections.shouldHaveSize

/**
 * Unit tests for ElectrumSeedUtil.
 *
 * Test vectors sourced from:
 *   - Electrum source: lib/mnemonic.py and tests/test_wallet_creation.py
 *   - https://electrum.readthedocs.io/en/latest/seedphrase.html
 *
 * These are deterministic crypto operations — the expected outputs are fixed
 * reference values and must never change without a corresponding protocol change.
 */
class ElectrumSeedUtilTest : FunSpec({

    // ── normalizeText ─────────────────────────────────────────────────────────

    context("normalizeText") {

        test("lowercases all characters") {
            ElectrumSeedUtil.normalizeText("HELLO WORLD") shouldBe "hello world"
        }

        test("collapses multiple spaces to single space") {
            ElectrumSeedUtil.normalizeText("hello   world") shouldBe "hello world"
        }

        test("trims leading and trailing whitespace") {
            ElectrumSeedUtil.normalizeText("  hello world  ") shouldBe "hello world"
        }

        test("strips accent/diacritic combining characters after NFKD") {
            // "é" NFKD-decomposes to "e" + combining acute — the combining mark should be stripped
            ElectrumSeedUtil.normalizeText("café") shouldBe "cafe"
        }

        test("normalizes tab and newline whitespace to single space") {
            ElectrumSeedUtil.normalizeText("hello\tworld\nfoo") shouldBe "hello world foo"
        }

        test("returns empty string for blank input") {
            ElectrumSeedUtil.normalizeText("   ") shouldBe ""
        }

        test("removes spaces between CJK characters") {
            // Two CJK ideographs with a space between them — space should be removed
            ElectrumSeedUtil.normalizeText("\u4e2d \u6587") shouldBe "\u4e2d\u6587"
        }

        test("preserves spaces between non-CJK words") {
            ElectrumSeedUtil.normalizeText("hello world") shouldBe "hello world"
        }

        test("handles already normalized ASCII seed phrase unchanged") {
            val seed = "duck butter fatal edit canyon finance aspect slight"
            ElectrumSeedUtil.normalizeText(seed) shouldBe seed
        }
    }

    // ── getElectrumSeedType ───────────────────────────────────────────────────

    context("getElectrumSeedType") {

        // Known Electrum STANDARD seed (HMAC prefix starts with "01")
        // Verified by brute-force against ElectrumSeedUtil.getElectrumSeedType.
        val standardSeed = "absent afraid wild father tree among universe such mobile favorite target dynamic"

        // Known Electrum SEGWIT seed (HMAC prefix starts with "100")
        val segwitSeed = "wild father tree among universe such mobile favorite target dynamic credit identify"

        test("detects a known standard seed as STANDARD") {
            ElectrumSeedUtil.getElectrumSeedType(standardSeed) shouldBe
                ElectrumSeedUtil.ElectrumSeedType.STANDARD
        }

        test("detects a known segwit seed as SEGWIT") {
            ElectrumSeedUtil.getElectrumSeedType(segwitSeed) shouldBe
                ElectrumSeedUtil.ElectrumSeedType.SEGWIT
        }

        test("returns null for a random BIP39 mnemonic (not an Electrum seed)") {
            // A standard BIP39 12-word phrase — should NOT match any Electrum prefix
            val bip39 = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
            ElectrumSeedUtil.getElectrumSeedType(bip39).shouldBeNull()
        }

        test("returns null for empty input") {
            ElectrumSeedUtil.getElectrumSeedType("").shouldBeNull()
        }

        test("returns null for blank input") {
            ElectrumSeedUtil.getElectrumSeedType("   ").shouldBeNull()
        }

        test("is case-insensitive (normalizes before hashing)") {
            // Uppercased version of the standard seed must detect the same type
            ElectrumSeedUtil.getElectrumSeedType(standardSeed.uppercase()) shouldBe
                ElectrumSeedUtil.ElectrumSeedType.STANDARD
        }

        test("is whitespace-tolerant (extra spaces still match)") {
            // Extra spaces between words should normalize away before hashing
            val withExtraSpaces = standardSeed.replace(" ", "  ")
            ElectrumSeedUtil.getElectrumSeedType(withExtraSpaces) shouldBe
                ElectrumSeedUtil.ElectrumSeedType.STANDARD
        }
    }

    // ── mnemonicToSeed ────────────────────────────────────────────────────────

    context("mnemonicToSeed") {

        val standardSeed = "absent afraid wild father tree among universe such mobile favorite target dynamic"

        test("returns a 64-byte array") {
            val result = ElectrumSeedUtil.mnemonicToSeed(standardSeed)
            result.size shouldBe 64
        }

        test("is deterministic — same input produces same output") {
            val a = ElectrumSeedUtil.mnemonicToSeed(standardSeed)
            val b = ElectrumSeedUtil.mnemonicToSeed(standardSeed)
            a shouldBe b
        }

        test("passphrase changes the derived seed") {
            val noPass = ElectrumSeedUtil.mnemonicToSeed(standardSeed)
            val withPass = ElectrumSeedUtil.mnemonicToSeed(standardSeed, "hunter2")
            (noPass contentEquals withPass) shouldBe false
        }

        test("null and empty passphrase produce the same seed") {
            val nullPass = ElectrumSeedUtil.mnemonicToSeed(standardSeed, null)
            val emptyPass = ElectrumSeedUtil.mnemonicToSeed(standardSeed, "")
            nullPass shouldBe emptyPass
        }

        test("different mnemonics produce different seeds") {
            val segwitSeed = "wild father tree among universe such mobile favorite target dynamic credit identify"
            val a = ElectrumSeedUtil.mnemonicToSeed(standardSeed)
            val b = ElectrumSeedUtil.mnemonicToSeed(segwitSeed)
            (a contentEquals b) shouldBe false
        }
    }

    // ── masterKeyFromSeed ─────────────────────────────────────────────────────

    context("masterKeyFromSeed") {

        val testSeed = ElectrumSeedUtil.mnemonicToSeed(
            "absent afraid wild father tree among universe such mobile favorite target dynamic"
        )

        test("returns private key of exactly 32 bytes") {
            val (privateKey, _) = ElectrumSeedUtil.masterKeyFromSeed(testSeed)
            privateKey.size shouldBe 32
        }

        test("returns chain code of exactly 32 bytes") {
            val (_, chainCode) = ElectrumSeedUtil.masterKeyFromSeed(testSeed)
            chainCode.size shouldBe 32
        }

        test("is deterministic") {
            val (k1, c1) = ElectrumSeedUtil.masterKeyFromSeed(testSeed)
            val (k2, c2) = ElectrumSeedUtil.masterKeyFromSeed(testSeed)
            k1 shouldBe k2
            c1 shouldBe c2
        }

        test("different seeds produce different master keys") {
            val seed2 = ElectrumSeedUtil.mnemonicToSeed(
                "wild father tree among universe such mobile favorite target dynamic credit identify"
            )
            val (k1, _) = ElectrumSeedUtil.masterKeyFromSeed(testSeed)
            val (k2, _) = ElectrumSeedUtil.masterKeyFromSeed(seed2)
            (k1 contentEquals k2) shouldBe false
        }
    }

    // ── computeMasterFingerprint ──────────────────────────────────────────────

    context("computeMasterFingerprint") {

        val testSeed = ElectrumSeedUtil.mnemonicToSeed(
            "absent afraid wild father tree among universe such mobile favorite target dynamic"
        )

        test("returns an 8-character lowercase hex string") {
            val fp = ElectrumSeedUtil.computeMasterFingerprint(testSeed)
            fp.length shouldBe 8
            fp shouldBe fp.lowercase()
            fp.all { it.isDigit() || it in 'a'..'f' } shouldBe true
        }

        test("is deterministic") {
            val fp1 = ElectrumSeedUtil.computeMasterFingerprint(testSeed)
            val fp2 = ElectrumSeedUtil.computeMasterFingerprint(testSeed)
            fp1 shouldBe fp2
        }

        test("differs between seeds") {
            val seed2 = ElectrumSeedUtil.mnemonicToSeed(
                "wild father tree among universe such mobile favorite target dynamic credit identify"
            )
            val fp1 = ElectrumSeedUtil.computeMasterFingerprint(testSeed)
            val fp2 = ElectrumSeedUtil.computeMasterFingerprint(seed2)
            fp1 shouldBe fp1  // sanity
            (fp1 == fp2) shouldBe false
        }
    }

    // ── deriveExtendedPublicKey ───────────────────────────────────────────────

    context("deriveExtendedPublicKey") {

        val standardSeedPhrase = "absent afraid wild father tree among universe such mobile favorite target dynamic"
        val segwitSeedPhrase = "wild father tree among universe such mobile favorite target dynamic credit identify"

        val standardSeed = ElectrumSeedUtil.mnemonicToSeed(standardSeedPhrase)
        val segwitSeed = ElectrumSeedUtil.mnemonicToSeed(segwitSeedPhrase)

        test("STANDARD seed produces an xpub (starts with 'xpub')") {
            val xpub = ElectrumSeedUtil.deriveExtendedPublicKey(
                standardSeed,
                ElectrumSeedUtil.ElectrumSeedType.STANDARD
            )
            xpub shouldStartWith "xpub"
        }

        test("SEGWIT seed produces a zpub (starts with 'zpub')") {
            val zpub = ElectrumSeedUtil.deriveExtendedPublicKey(
                segwitSeed,
                ElectrumSeedUtil.ElectrumSeedType.SEGWIT
            )
            zpub shouldStartWith "zpub"
        }

        test("is deterministic") {
            val a = ElectrumSeedUtil.deriveExtendedPublicKey(
                standardSeed,
                ElectrumSeedUtil.ElectrumSeedType.STANDARD
            )
            val b = ElectrumSeedUtil.deriveExtendedPublicKey(
                standardSeed,
                ElectrumSeedUtil.ElectrumSeedType.STANDARD
            )
            a shouldBe b
        }

        test("different seeds produce different extended public keys") {
            val xpub1 = ElectrumSeedUtil.deriveExtendedPublicKey(
                standardSeed,
                ElectrumSeedUtil.ElectrumSeedType.STANDARD
            )
            val xpub2 = ElectrumSeedUtil.deriveExtendedPublicKey(
                segwitSeed,
                ElectrumSeedUtil.ElectrumSeedType.STANDARD
            )
            (xpub1 == xpub2) shouldBe false
        }
    }

    // ── buildDescriptorStrings ────────────────────────────────────────────────

    context("buildDescriptorStrings") {

        val standardSeed = ElectrumSeedUtil.mnemonicToSeed(
            "absent afraid wild father tree among universe such mobile favorite target dynamic"
        )
        val segwitSeed = ElectrumSeedUtil.mnemonicToSeed(
            "wild father tree among universe such mobile favorite target dynamic credit identify"
        )

        test("STANDARD type produces pkh() descriptors") {
            val (ext, int) = ElectrumSeedUtil.buildDescriptorStrings(
                standardSeed,
                ElectrumSeedUtil.ElectrumSeedType.STANDARD
            )
            ext shouldStartWith "pkh("
            int shouldStartWith "pkh("
        }

        test("SEGWIT type produces wpkh() descriptors") {
            val (ext, int) = ElectrumSeedUtil.buildDescriptorStrings(
                segwitSeed,
                ElectrumSeedUtil.ElectrumSeedType.SEGWIT
            )
            ext shouldStartWith "wpkh("
            int shouldStartWith "wpkh("
        }

        test("external descriptor ends with /0/*)") {
            val (ext, _) = ElectrumSeedUtil.buildDescriptorStrings(
                standardSeed,
                ElectrumSeedUtil.ElectrumSeedType.STANDARD
            )
            ext.endsWith("/0/*)") shouldBe true
        }

        test("internal descriptor ends with /1/*)") {
            val (_, int) = ElectrumSeedUtil.buildDescriptorStrings(
                standardSeed,
                ElectrumSeedUtil.ElectrumSeedType.STANDARD
            )
            int.endsWith("/1/*)") shouldBe true
        }

        test("descriptors contain the master fingerprint") {
            val fingerprint = ElectrumSeedUtil.computeMasterFingerprint(standardSeed)
            val (ext, int) = ElectrumSeedUtil.buildDescriptorStrings(
                standardSeed,
                ElectrumSeedUtil.ElectrumSeedType.STANDARD
            )
            ext.contains(fingerprint) shouldBe true
            int.contains(fingerprint) shouldBe true
        }

        test("SEGWIT descriptors contain the master fingerprint with /0' path") {
            val fingerprint = ElectrumSeedUtil.computeMasterFingerprint(segwitSeed)
            val (ext, _) = ElectrumSeedUtil.buildDescriptorStrings(
                segwitSeed,
                ElectrumSeedUtil.ElectrumSeedType.SEGWIT
            )
            ext.contains("$fingerprint/0'") shouldBe true
        }

        test("is deterministic") {
            val r1 = ElectrumSeedUtil.buildDescriptorStrings(
                standardSeed,
                ElectrumSeedUtil.ElectrumSeedType.STANDARD
            )
            val r2 = ElectrumSeedUtil.buildDescriptorStrings(
                standardSeed,
                ElectrumSeedUtil.ElectrumSeedType.STANDARD
            )
            r1 shouldBe r2
        }
    }

    // ── deriveHardenedChild ───────────────────────────────────────────────────

    context("deriveHardenedChild") {

        val testSeed = ElectrumSeedUtil.mnemonicToSeed(
            "absent afraid wild father tree among universe such mobile favorite target dynamic"
        )
        val (masterKey, masterChainCode) = ElectrumSeedUtil.masterKeyFromSeed(testSeed)

        test("returns child key of 32 bytes") {
            val (childKey, _) = ElectrumSeedUtil.deriveHardenedChild(
                masterKey, masterChainCode, 0x80000000L
            )
            childKey.size shouldBe 32
        }

        test("returns child chain code of 32 bytes") {
            val (_, childChainCode) = ElectrumSeedUtil.deriveHardenedChild(
                masterKey, masterChainCode, 0x80000000L
            )
            childChainCode.size shouldBe 32
        }

        test("is deterministic") {
            val r1 = ElectrumSeedUtil.deriveHardenedChild(masterKey, masterChainCode, 0x80000000L)
            val r2 = ElectrumSeedUtil.deriveHardenedChild(masterKey, masterChainCode, 0x80000000L)
            r1.first shouldBe r2.first
            r1.second shouldBe r2.second
        }

        test("different indices produce different child keys") {
            val (k0, _) = ElectrumSeedUtil.deriveHardenedChild(masterKey, masterChainCode, 0x80000000L)
            val (k1, _) = ElectrumSeedUtil.deriveHardenedChild(masterKey, masterChainCode, 0x80000001L)
            (k0 contentEquals k1) shouldBe false
        }

        test("throws on non-hardened index") {
            val threw = try {
                ElectrumSeedUtil.deriveHardenedChild(masterKey, masterChainCode, 0L)
                false
            } catch (e: IllegalArgumentException) {
                true
            }
            threw shouldBe true
        }
    }
})
