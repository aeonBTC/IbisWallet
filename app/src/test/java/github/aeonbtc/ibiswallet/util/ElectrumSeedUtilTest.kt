package github.aeonbtc.ibiswallet.util

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldStartWith

class ElectrumSeedUtilTest : FunSpec({

    // ── normalizeText ──

    context("normalizeText") {
        test("lowercases input") {
            ElectrumSeedUtil.normalizeText("HELLO WORLD") shouldBe "hello world"
        }

        test("collapses multiple spaces") {
            ElectrumSeedUtil.normalizeText("hello   world") shouldBe "hello world"
        }

        test("trims leading and trailing whitespace") {
            ElectrumSeedUtil.normalizeText("  hello world  ") shouldBe "hello world"
        }

        test("removes accent marks via NFKD") {
            // e with acute accent should become plain e
            ElectrumSeedUtil.normalizeText("caf\u00e9") shouldBe "cafe"
        }

        test("removes spaces between CJK characters") {
            // Two CJK chars with a space between them
            ElectrumSeedUtil.normalizeText("\u4e00 \u4e8c") shouldBe "\u4e00\u4e8c"
        }

        test("preserves spaces between Latin words") {
            ElectrumSeedUtil.normalizeText("abandon ability able") shouldBe "abandon ability able"
        }

        test("normalizes full-width spaces") {
            // NFKD converts ideographic space (\u3000) to regular space
            ElectrumSeedUtil.normalizeText("hello\u3000world") shouldBe "hello world"
        }

        test("handles empty string") {
            ElectrumSeedUtil.normalizeText("") shouldBe ""
        }
    }

    // ── getElectrumSeedType ──

    context("getElectrumSeedType") {
        // Known valid Electrum segwit seed (from official Electrum test vectors)
        val segwitSeed =
            "wild father tree among universe such mobile favorite " +
                "target dynamic credit identify"

        // Known valid Electrum standard seed (from official Electrum test vectors)
        val standardSeed =
            "cram swing cover prefer miss modify ritual silly " +
                "deliver chunk behind inform able"

        test("detects segwit seed") {
            ElectrumSeedUtil.getElectrumSeedType(segwitSeed) shouldBe
                ElectrumSeedUtil.ElectrumSeedType.SEGWIT
        }

        test("detects standard seed") {
            ElectrumSeedUtil.getElectrumSeedType(standardSeed) shouldBe
                ElectrumSeedUtil.ElectrumSeedType.STANDARD
        }

        test("returns null for BIP39 seed") {
            val bip39Seed =
                "abandon abandon abandon abandon abandon abandon " +
                    "abandon abandon abandon abandon abandon about"
            ElectrumSeedUtil.getElectrumSeedType(bip39Seed) shouldBe null
        }

        test("returns null for random words") {
            ElectrumSeedUtil.getElectrumSeedType("hello world foo bar") shouldBe null
        }

        test("returns null for blank input") {
            ElectrumSeedUtil.getElectrumSeedType("") shouldBe null
            ElectrumSeedUtil.getElectrumSeedType("   ") shouldBe null
        }

        test("is case-insensitive") {
            ElectrumSeedUtil.getElectrumSeedType(segwitSeed.uppercase()) shouldBe
                ElectrumSeedUtil.ElectrumSeedType.SEGWIT
        }
    }

    // ── mnemonicToSeed ──

    context("mnemonicToSeed") {
        test("produces 64-byte seed") {
            val seed = ElectrumSeedUtil.mnemonicToSeed("wild father tree among universe such mobile favorite target dynamic credit identify")
            seed.size shouldBe 64
        }

        test("same mnemonic produces same seed") {
            val mnemonic = "wild father tree among universe such mobile favorite target dynamic credit identify"
            val seed1 = ElectrumSeedUtil.mnemonicToSeed(mnemonic)
            val seed2 = ElectrumSeedUtil.mnemonicToSeed(mnemonic)
            seed1.toList() shouldBe seed2.toList()
        }

        test("different passphrase produces different seed") {
            val mnemonic = "wild father tree among universe such mobile favorite target dynamic credit identify"
            val seed1 = ElectrumSeedUtil.mnemonicToSeed(mnemonic, null)
            val seed2 = ElectrumSeedUtil.mnemonicToSeed(mnemonic, "mypassphrase")
            seed1.toList() shouldNotBe seed2.toList()
        }
    }

    context("bip39MnemonicToSeed") {
        test("matches the official BIP39 vector") {
            val seed =
                ElectrumSeedUtil.bip39MnemonicToSeed(
                    mnemonic = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about",
                    passphrase = "TREZOR",
                )

            seed.joinToString("") { "%02x".format(it) } shouldBe
                "c55257c360c07c72029aebc1b53c05ed0362ada38ead3e3e9efa3708e5349553" +
                "1f09a6987599d18264c1e1c92f2cf141630c7a3c4ab7c81b2f001698e7463b04"
        }
    }

    // ── masterKeyFromSeed ──

    context("masterKeyFromSeed") {
        test("produces 32-byte key and 32-byte chain code") {
            val seed = ElectrumSeedUtil.mnemonicToSeed("wild father tree among universe such mobile favorite target dynamic credit identify")
            val (key, chainCode) = ElectrumSeedUtil.masterKeyFromSeed(seed)
            key.size shouldBe 32
            chainCode.size shouldBe 32
        }

        test("same seed produces same master key") {
            val seed = ElectrumSeedUtil.mnemonicToSeed("wild father tree among universe such mobile favorite target dynamic credit identify")
            val (key1, cc1) = ElectrumSeedUtil.masterKeyFromSeed(seed)
            val (key2, cc2) = ElectrumSeedUtil.masterKeyFromSeed(seed)
            key1.toList() shouldBe key2.toList()
            cc1.toList() shouldBe cc2.toList()
        }
    }

    // ── deriveXprv ──

    context("deriveXprv") {
        test("master path produces xprv starting with xprv") {
            val seed = ElectrumSeedUtil.mnemonicToSeed("wild father tree among universe such mobile favorite target dynamic credit identify")
            val xprv = ElectrumSeedUtil.deriveXprv(seed, "m")
            xprv shouldStartWith "xprv"
        }

        test("derived path produces different xprv than master") {
            val seed = ElectrumSeedUtil.mnemonicToSeed("wild father tree among universe such mobile favorite target dynamic credit identify")
            val master = ElectrumSeedUtil.deriveXprv(seed, "m")
            val child = ElectrumSeedUtil.deriveXprv(seed, "m/0'")
            master shouldNotBe child
        }

        test("xprv is valid Base58Check (111 chars for standard BIP32)") {
            val seed = ElectrumSeedUtil.mnemonicToSeed("wild father tree among universe such mobile favorite target dynamic credit identify")
            val xprv = ElectrumSeedUtil.deriveXprv(seed, "m")
            // Standard xprv is 111 characters
            xprv.length shouldBe 111
        }
    }

    // ── computeMasterFingerprint ──

    context("computeMasterFingerprint") {
        test("produces 8-char hex string") {
            val seed = ElectrumSeedUtil.mnemonicToSeed("wild father tree among universe such mobile favorite target dynamic credit identify")
            val fp = ElectrumSeedUtil.computeMasterFingerprint(seed)
            fp.length shouldBe 8
            fp.all { it in '0'..'9' || it in 'a'..'f' } shouldBe true
        }

        test("same seed produces same fingerprint") {
            val seed = ElectrumSeedUtil.mnemonicToSeed("wild father tree among universe such mobile favorite target dynamic credit identify")
            val fp1 = ElectrumSeedUtil.computeMasterFingerprint(seed)
            val fp2 = ElectrumSeedUtil.computeMasterFingerprint(seed)
            fp1 shouldBe fp2
        }

        test("different seeds produce different fingerprints") {
            val seed1 = ElectrumSeedUtil.mnemonicToSeed("wild father tree among universe such mobile favorite target dynamic credit identify")
            val seed2 = ElectrumSeedUtil.mnemonicToSeed("cram swing cover prefer miss modify ritual silly deliver chunk behind inform able")
            val fp1 = ElectrumSeedUtil.computeMasterFingerprint(seed1)
            val fp2 = ElectrumSeedUtil.computeMasterFingerprint(seed2)
            fp1 shouldNotBe fp2
        }
    }

    // ── buildDescriptorStrings ──

    context("buildDescriptorStrings") {
        val segwitSeed = "wild father tree among universe such mobile favorite target dynamic credit identify"

        test("SEGWIT produces wpkh descriptors") {
            val seed = ElectrumSeedUtil.mnemonicToSeed(segwitSeed)
            val (external, internal) = ElectrumSeedUtil.buildDescriptorStrings(
                seed, ElectrumSeedUtil.ElectrumSeedType.SEGWIT,
            )
            external shouldStartWith "wpkh(["
            external.contains("/0/*)") shouldBe true
            internal shouldStartWith "wpkh(["
            internal.contains("/1/*)") shouldBe true
        }

        test("STANDARD produces pkh descriptors") {
            val standardSeed = "cram swing cover prefer miss modify ritual silly deliver chunk behind inform able"
            val seed = ElectrumSeedUtil.mnemonicToSeed(standardSeed)
            val (external, internal) = ElectrumSeedUtil.buildDescriptorStrings(
                seed, ElectrumSeedUtil.ElectrumSeedType.STANDARD,
            )
            external shouldStartWith "pkh(["
            external.contains("/0/*)") shouldBe true
            internal shouldStartWith "pkh(["
            internal.contains("/1/*)") shouldBe true
        }

        test("descriptors contain master fingerprint") {
            val seed = ElectrumSeedUtil.mnemonicToSeed(segwitSeed)
            val fp = ElectrumSeedUtil.computeMasterFingerprint(seed)
            val (external, _) = ElectrumSeedUtil.buildDescriptorStrings(
                seed, ElectrumSeedUtil.ElectrumSeedType.SEGWIT,
            )
            external.contains(fp) shouldBe true
        }
    }

    // ── deriveExtendedPublicKey ──

    context("deriveExtendedPublicKey") {
        test("STANDARD produces xpub") {
            val standardSeed = "cram swing cover prefer miss modify ritual silly deliver chunk behind inform able"
            val seed = ElectrumSeedUtil.mnemonicToSeed(standardSeed)
            val xpub = ElectrumSeedUtil.deriveExtendedPublicKey(
                seed, ElectrumSeedUtil.ElectrumSeedType.STANDARD,
            )
            xpub shouldStartWith "xpub"
        }

        test("SEGWIT produces zpub") {
            val segwitSeed = "wild father tree among universe such mobile favorite target dynamic credit identify"
            val seed = ElectrumSeedUtil.mnemonicToSeed(segwitSeed)
            val zpub = ElectrumSeedUtil.deriveExtendedPublicKey(
                seed, ElectrumSeedUtil.ElectrumSeedType.SEGWIT,
            )
            zpub shouldStartWith "zpub"
        }

        test("extended public key is 111 characters") {
            val segwitSeed = "wild father tree among universe such mobile favorite target dynamic credit identify"
            val seed = ElectrumSeedUtil.mnemonicToSeed(segwitSeed)
            val zpub = ElectrumSeedUtil.deriveExtendedPublicKey(
                seed, ElectrumSeedUtil.ElectrumSeedType.SEGWIT,
            )
            zpub.length shouldBe 111
        }
    }

    context("deriveCompressedPublicKey") {
        test("returns a compressed public key for a Liquid receive path") {
            val seed =
                ElectrumSeedUtil.bip39MnemonicToSeed(
                    "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about",
                )

            val pubkey = ElectrumSeedUtil.deriveCompressedPublicKey(seed, "m/84'/1776'/0'/0/0")

            pubkey.size shouldBe 33
            (pubkey.first().toInt() == 0x02 || pubkey.first().toInt() == 0x03) shouldBe true
        }

        test("different Liquid receive indices derive different refund keys") {
            val seed =
                ElectrumSeedUtil.bip39MnemonicToSeed(
                    "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about",
                )

            val first = ElectrumSeedUtil.deriveCompressedPublicKey(seed, "m/84'/1776'/0'/0/0")
            val second = ElectrumSeedUtil.deriveCompressedPublicKey(seed, "m/84'/1776'/0'/0/1")

            first.toList() shouldNotBe second.toList()
        }
    }

    // ── deriveHardenedChild ──

    context("deriveHardenedChild") {
        test("produces 32-byte child key and chain code") {
            val seed = ElectrumSeedUtil.mnemonicToSeed("wild father tree among universe such mobile favorite target dynamic credit identify")
            val (masterKey, masterChainCode) = ElectrumSeedUtil.masterKeyFromSeed(seed)
            val (childKey, childChainCode) = ElectrumSeedUtil.deriveHardenedChild(
                masterKey, masterChainCode, 0x80000000L,
            )
            childKey.size shouldBe 32
            childChainCode.size shouldBe 32
        }

        test("different indices produce different keys") {
            val seed = ElectrumSeedUtil.mnemonicToSeed("wild father tree among universe such mobile favorite target dynamic credit identify")
            val (masterKey, masterChainCode) = ElectrumSeedUtil.masterKeyFromSeed(seed)
            val (child0, _) = ElectrumSeedUtil.deriveHardenedChild(masterKey, masterChainCode, 0x80000000L)
            val (child1, _) = ElectrumSeedUtil.deriveHardenedChild(masterKey, masterChainCode, 0x80000001L)
            child0.toList() shouldNotBe child1.toList()
        }
    }

    // ── ElectrumSeedType enum ──

    context("ElectrumSeedType") {
        test("STANDARD has prefix 01") {
            ElectrumSeedUtil.ElectrumSeedType.STANDARD.prefix shouldBe "01"
        }

        test("SEGWIT has prefix 100") {
            ElectrumSeedUtil.ElectrumSeedType.SEGWIT.prefix shouldBe "100"
        }
    }
})
