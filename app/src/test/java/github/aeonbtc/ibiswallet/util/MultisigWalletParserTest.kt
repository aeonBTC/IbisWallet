package github.aeonbtc.ibiswallet.util

import github.aeonbtc.ibiswallet.data.model.MultisigScriptType
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain

class MultisigWalletParserTest : FunSpec({
    val xpub1 = "xpub661MyMwAqRbcF1aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
    val xpub2 = "xpub661MyMwAqRbcF2bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
    val xpub3 = "xpub661MyMwAqRbcF3cccccccccccccccccccccccccccccccccccccccccccccccccccc"

    test("parses native descriptor pair") {
        val config =
            MultisigWalletParser.parse(
                """
                wsh(sortedmulti(2,[aaaaaaaa/48'/0'/0'/2']$xpub1/0/*,[bbbbbbbb/48'/0'/0'/2']$xpub2/0/*,[cccccccc/48'/0'/0'/2']$xpub3/0/*))
                wsh(sortedmulti(2,[aaaaaaaa/48'/0'/0'/2']$xpub1/1/*,[bbbbbbbb/48'/0'/0'/2']$xpub2/1/*,[cccccccc/48'/0'/0'/2']$xpub3/1/*))
                """.trimIndent(),
            )

        config shouldNotBe null
        config!!.threshold shouldBe 2
        config.totalCosigners shouldBe 3
        config.scriptType shouldBe MultisigScriptType.P2WSH
        config.internalDescriptor shouldContain "/1/*"
    }

    test("parses caravan-style json") {
        val config =
            MultisigWalletParser.parse(
                """
                {
                  "name": "Vault",
                  "addressType": "P2WSH",
                  "quorum": { "requiredSigners": 2, "totalSigners": 3 },
                  "extendedPublicKeys": [
                    { "xfp": "aaaaaaaa", "bip32Path": "m/48'/0'/0'/2'", "xpub": "$xpub1" },
                    { "xfp": "bbbbbbbb", "bip32Path": "m/48'/0'/0'/2'", "xpub": "$xpub2" },
                    { "xfp": "cccccccc", "bip32Path": "m/48'/0'/0'/2'", "xpub": "$xpub3" }
                  ]
                }
                """.trimIndent(),
            )

        config shouldNotBe null
        config!!.name shouldBe "Vault"
        config.policyLabel shouldBe "2-of-3 P2WSH"
        config.externalDescriptor shouldContain "sortedmulti(2,"
    }

    test("parses bsms text") {
        val config =
            MultisigWalletParser.parse(
                """
                BSMS 1.0
                Name: Treasury
                Policy: 2 of 3
                Derivation: m/48'/0'/0'/2'
                Format: P2WSH
                aaaaaaaa: $xpub1
                bbbbbbbb: $xpub2
                cccccccc: $xpub3
                """.trimIndent(),
            )

        config shouldNotBe null
        config!!.name shouldBe "Treasury"
        config.cosigners.size shouldBe 3
        config.externalDescriptor shouldContain "[aaaaaaaa/48'/0'/0'/2']"
    }

    test("parseCosignerScanLines extracts bsms key line") {
        val lines =
            MultisigWalletParser.parseCosignerScanLines(
                "aaaaaaaa: $xpub1",
            )
        lines shouldBe listOf("aaaaaaaa: $xpub1")
    }

    test("parseCosignerScanLines extracts all cosigners from bsms") {
        val lines =
            MultisigWalletParser.parseCosignerScanLines(
                """
                BSMS 1.0
                Policy: 2 of 3
                Derivation: m/48'/0'/0'/2'
                aaaaaaaa: $xpub1
                bbbbbbbb: $xpub2
                cccccccc: $xpub3
                """.trimIndent(),
            )
        lines.size shouldBe 3
        lines[0] shouldBe "aaaaaaaa: $xpub1"
    }

    test("parseCosignerScanLines extracts from key origin") {
        val lines =
            MultisigWalletParser.parseCosignerScanLines(
                "[aaaaaaaa/48'/0'/0'/2']$xpub1",
            )
        lines shouldBe listOf("aaaaaaaa: $xpub1")
    }

    test("rejects attacker-controlled change descriptor") {
        val xpubEvil = "xpub661MyMwAqRbcFEeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee"
        val config =
            MultisigWalletParser.parse(
                """
                wsh(sortedmulti(2,[aaaaaaaa/48'/0'/0'/2']$xpub1/0/*,[bbbbbbbb/48'/0'/0'/2']$xpub2/0/*,[cccccccc/48'/0'/0'/2']$xpub3/0/*))
                wsh(sortedmulti(1,[deadbeef/48'/0'/0'/2']$xpubEvil/1/*))
                """.trimIndent(),
            )
        config shouldBe null
    }

    test("rejects threshold mismatch between descriptors") {
        val config =
            MultisigWalletParser.parse(
                """
                wsh(sortedmulti(2,[aaaaaaaa/48'/0'/0'/2']$xpub1/0/*,[bbbbbbbb/48'/0'/0'/2']$xpub2/0/*,[cccccccc/48'/0'/0'/2']$xpub3/0/*))
                wsh(sortedmulti(3,[aaaaaaaa/48'/0'/0'/2']$xpub1/1/*,[bbbbbbbb/48'/0'/0'/2']$xpub2/1/*,[cccccccc/48'/0'/0'/2']$xpub3/1/*))
                """.trimIndent(),
            )
        config shouldBe null
    }

    test("rejects substituted change key set") {
        val xpubEvil = "xpub661MyMwAqRbcFEeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee"
        val config =
            MultisigWalletParser.parse(
                """
                wsh(sortedmulti(2,[aaaaaaaa/48'/0'/0'/2']$xpub1/0/*,[bbbbbbbb/48'/0'/0'/2']$xpub2/0/*,[cccccccc/48'/0'/0'/2']$xpub3/0/*))
                wsh(sortedmulti(2,[aaaaaaaa/48'/0'/0'/2']$xpub1/1/*,[bbbbbbbb/48'/0'/0'/2']$xpub2/1/*,[deadbeef/48'/0'/0'/2']$xpubEvil/1/*))
                """.trimIndent(),
            )
        config shouldBe null
    }

    test("rejects wrapper mismatch between descriptors") {
        val config =
            MultisigWalletParser.parse(
                """
                wsh(sortedmulti(2,[aaaaaaaa/48'/0'/0'/2']$xpub1/0/*,[bbbbbbbb/48'/0'/0'/2']$xpub2/0/*,[cccccccc/48'/0'/0'/2']$xpub3/0/*))
                sh(wsh(sortedmulti(2,[aaaaaaaa/48'/0'/0'/2']$xpub1/1/*,[bbbbbbbb/48'/0'/0'/2']$xpub2/1/*,[cccccccc/48'/0'/0'/2']$xpub3/1/*)))
                """.trimIndent(),
            )
        config shouldBe null
    }

    test("descriptorsMatch accepts mirrored pair") {
        val external =
            "wsh(sortedmulti(2,[aaaaaaaa/48'/0'/0'/2']$xpub1/0/*,[bbbbbbbb/48'/0'/0'/2']$xpub2/0/*,[cccccccc/48'/0'/0'/2']$xpub3/0/*))"
        val internal =
            "wsh(sortedmulti(2,[aaaaaaaa/48'/0'/0'/2']$xpub1/1/*,[bbbbbbbb/48'/0'/0'/2']$xpub2/1/*,[cccccccc/48'/0'/0'/2']$xpub3/1/*))"
        MultisigWalletParser.descriptorsMatch(external, internal) shouldBe true
    }

    test("rejects duplicate cosigner xpub in bsms") {
        val config =
            MultisigWalletParser.parse(
                """
                BSMS 1.0
                Name: Dup
                Policy: 2 of 3
                Derivation: m/48'/0'/0'/2'
                Format: P2WSH
                aaaaaaaa: $xpub1
                bbbbbbbb: $xpub1
                cccccccc: $xpub2
                """.trimIndent(),
            )
        config shouldBe null
    }

    test("rejects duplicate cosigner xpub in descriptor pair") {
        val config =
            MultisigWalletParser.parse(
                """
                wsh(sortedmulti(2,[aaaaaaaa/48'/0'/0'/2']$xpub1/0/*,[bbbbbbbb/48'/0'/0'/2']$xpub1/0/*))
                wsh(sortedmulti(2,[aaaaaaaa/48'/0'/0'/2']$xpub1/1/*,[bbbbbbbb/48'/0'/0'/2']$xpub1/1/*))
                """.trimIndent(),
            )
        config shouldBe null
    }

    test("rejects duplicate cosigner in caravan json") {
        val config =
            MultisigWalletParser.parse(
                """
                {
                  "name": "Dup",
                  "addressType": "P2WSH",
                  "quorum": { "requiredSigners": 2, "totalSigners": 3 },
                  "extendedPublicKeys": [
                    { "xfp": "aaaaaaaa", "bip32Path": "m/48'/0'/0'/2'", "xpub": "$xpub1" },
                    { "xfp": "bbbbbbbb", "bip32Path": "m/48'/0'/0'/2'", "xpub": "$xpub1" },
                    { "xfp": "cccccccc", "bip32Path": "m/48'/0'/0'/2'", "xpub": "$xpub2" }
                  ]
                }
                """.trimIndent(),
            )
        config shouldBe null
    }

    test("rejects legacy sh(multi) descriptors") {
        val config =
            MultisigWalletParser.parse(
                """
                sh(multi(2,[aaaaaaaa/48'/0'/0'/2']$xpub1/0/*,[bbbbbbbb/48'/0'/0'/2']$xpub2/0/*))
                sh(multi(2,[aaaaaaaa/48'/0'/0'/2']$xpub1/1/*,[bbbbbbbb/48'/0'/0'/2']$xpub2/1/*))
                """.trimIndent(),
            )
        config shouldBe null
    }

    test("accepts single bip389 multipath descriptor") {
        val config =
            MultisigWalletParser.parse(
                "wsh(sortedmulti(2,[aaaaaaaa/48'/0'/0'/2']$xpub1/<0;1>/*,[bbbbbbbb/48'/0'/0'/2']$xpub2/<0;1>/*,[cccccccc/48'/0'/0'/2']$xpub3/<0;1>/*))",
            )
        config shouldNotBe null
        config!!.externalDescriptor shouldContain "/0/*"
        config.internalDescriptor shouldContain "/1/*"
    }

    test("accepts two-line multipath descriptor pair") {
        val config =
            MultisigWalletParser.parse(
                """
                wsh(sortedmulti(2,[aaaaaaaa/48'/0'/0'/2']$xpub1/<0;1>/*,[bbbbbbbb/48'/0'/0'/2']$xpub2/<0;1>/*,[cccccccc/48'/0'/0'/2']$xpub3/<0;1>/*))
                wsh(sortedmulti(2,[aaaaaaaa/48'/0'/0'/2']$xpub1/<0;1>/*,[bbbbbbbb/48'/0'/0'/2']$xpub2/<0;1>/*,[cccccccc/48'/0'/0'/2']$xpub3/<0;1>/*))
                """.trimIndent(),
            )
        config shouldNotBe null
        config!!.externalDescriptor shouldContain "/0/*"
        config.internalDescriptor shouldContain "/1/*"
    }

    test("accepts reversed descriptor order") {
        val config =
            MultisigWalletParser.parse(
                """
                wsh(sortedmulti(2,[aaaaaaaa/48'/0'/0'/2']$xpub1/1/*,[bbbbbbbb/48'/0'/0'/2']$xpub2/1/*,[cccccccc/48'/0'/0'/2']$xpub3/1/*))
                wsh(sortedmulti(2,[aaaaaaaa/48'/0'/0'/2']$xpub1/0/*,[bbbbbbbb/48'/0'/0'/2']$xpub2/0/*,[cccccccc/48'/0'/0'/2']$xpub3/0/*))
                """.trimIndent(),
            )
        config shouldNotBe null
        config!!.externalDescriptor shouldContain "/0/*"
        config.internalDescriptor shouldContain "/1/*"
    }

    test("accepts unsorted multi pair and rejects reordered change") {
        val external =
            "wsh(multi(2,[aaaaaaaa/48'/0'/0'/2']$xpub1/0/*,[bbbbbbbb/48'/0'/0'/2']$xpub2/0/*,[cccccccc/48'/0'/0'/2']$xpub3/0/*))"
        val internal =
            "wsh(multi(2,[aaaaaaaa/48'/0'/0'/2']$xpub1/1/*,[bbbbbbbb/48'/0'/0'/2']$xpub2/1/*,[cccccccc/48'/0'/0'/2']$xpub3/1/*))"
        val reordered =
            "wsh(multi(2,[cccccccc/48'/0'/0'/2']$xpub3/1/*,[bbbbbbbb/48'/0'/0'/2']$xpub2/1/*,[aaaaaaaa/48'/0'/0'/2']$xpub1/1/*))"
        MultisigWalletParser.parse("$external\n$internal") shouldNotBe null
        MultisigWalletParser.descriptorsMatch(external, internal) shouldBe true
        MultisigWalletParser.descriptorsMatch(external, reordered) shouldBe false
        MultisigWalletParser.parse("$external\n$reordered") shouldBe null
    }

    context("local signer override binding") {
        val seedA =
            ElectrumSeedUtil.bip39MnemonicToSeed(
                "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about",
            )
        val seedB =
            ElectrumSeedUtil.bip39MnemonicToSeed(
                "legal winner thank year wave sausage worth useful legal winner thank yellow",
            )
        val seedC =
            ElectrumSeedUtil.bip39MnemonicToSeed(
                "letter advice cage absurd amount doctor acoustic avoid letter advice cage above",
            )
        val path = "m/48'/0'/0'/2'"
        val fpA = ElectrumSeedUtil.computeMasterFingerprint(seedA)
        val fpB = ElectrumSeedUtil.computeMasterFingerprint(seedB)
        val fpC = ElectrumSeedUtil.computeMasterFingerprint(seedC)
        val xprvA = ElectrumSeedUtil.deriveXprv(seedA, path)
        val xpubA = ElectrumSeedUtil.xpubFromXprv(xprvA)
        val xpubB = ElectrumSeedUtil.xpubFromXprv(ElectrumSeedUtil.deriveXprv(seedB, path))
        val xpubC = ElectrumSeedUtil.xpubFromXprv(ElectrumSeedUtil.deriveXprv(seedC, path))
        val shortPath = path.removePrefix("m/")

        fun descriptor(fp: String, key: String, branch: Int, others: List<Pair<String, String>>): String {
            val keys =
                listOf("[$fp/$shortPath]$key/$branch/*") +
                    others.map { (ofp, okey) -> "[$ofp/$shortPath]$okey/$branch/*" }
            return "wsh(sortedmulti(2,${keys.joinToString(",")}))"
        }

        val config =
            MultisigWalletParser.parse(
                "${descriptor(fpA, xpubA, 0, listOf(fpB to xpubB, fpC to xpubC))}\n" +
                    descriptor(fpA, xpubA, 1, listOf(fpB to xpubB, fpC to xpubC)),
            )!!

        test("accepts identical pair") {
            MultisigWalletParser.overrideMatchesConfig(
                config.externalDescriptor,
                config.internalDescriptor,
                config,
            ) shouldBe true
        }

        test("rejects substituted key under a known fingerprint") {
            val evilExternal = descriptor(fpA, xpubC, 0, listOf(fpB to xpubB, fpC to xpubC))
            val evilInternal = descriptor(fpA, xpubC, 1, listOf(fpB to xpubB, fpC to xpubC))
            MultisigWalletParser.overrideMatchesConfig(evilExternal, evilInternal, config) shouldBe false
        }

        test("rejects different threshold and script wrapper") {
            val keys =
                listOf("[$fpA/$shortPath]$xpubA", "[$fpB/$shortPath]$xpubB", "[$fpC/$shortPath]$xpubC")
            val otherThresholdExt =
                "wsh(sortedmulti(3,${keys.joinToString(",") { "$it/0/*" }}))"
            val otherThresholdInt =
                "wsh(sortedmulti(3,${keys.joinToString(",") { "$it/1/*" }}))"
            MultisigWalletParser.overrideMatchesConfig(
                otherThresholdExt,
                otherThresholdInt,
                config,
            ) shouldBe false
            val nestedInt = "sh($otherThresholdInt)"
            MultisigWalletParser.overrideMatchesConfig(
                otherThresholdExt,
                nestedInt,
                config,
            ) shouldBe false
        }

        test("normalizePrivateKeysToPublic converts xprv preserving origin and branch") {
            val private = "[$fpA/$shortPath]$xprvA/0/*"
            MultisigWalletParser.normalizePrivateKeysToPublic(private) shouldBe "[$fpA/$shortPath]$xpubA/0/*"
        }

        test("normalizePrivateKeysToPublic passes public text through") {
            MultisigWalletParser.normalizePrivateKeysToPublic(config.externalDescriptor) shouldBe
                config.externalDescriptor
        }

        test("normalizePrivateKeysToPublic returns null for corrupt xprv") {
            MultisigWalletParser.normalizePrivateKeysToPublic("[$fpA/$shortPath]xprv9s21ZrQH143K Sauce/0/*") shouldBe null
        }

        test("accepts private pair for the same quorum after normalization") {
            val privExternal = descriptor(fpA, xprvA, 0, listOf(fpB to xpubB, fpC to xpubC))
            val privInternal = descriptor(fpA, xprvA, 1, listOf(fpB to xpubB, fpC to xpubC))
            val normalized =
                MultisigWalletParser.normalizePrivateKeysToPublic("$privExternal\n$privInternal")!!
            val (normExternal, normInternal) = MultisigWalletParser.normalizeDescriptorPair(normalized)!!
            MultisigWalletParser.overrideMatchesConfig(normExternal, normInternal, config) shouldBe true
        }

        test("rejects private pair with wrong key under a known fingerprint") {            val xprvWrong = ElectrumSeedUtil.deriveXprv(seedC, path)
            val privExternal = descriptor(fpA, xprvWrong, 0, listOf(fpB to xpubB, fpC to xpubC))
            val privInternal = descriptor(fpA, xprvWrong, 1, listOf(fpB to xpubB, fpC to xpubC))
            val normalized =
                MultisigWalletParser.normalizePrivateKeysToPublic("$privExternal\n$privInternal")!!
            val (normExternal, normInternal) = MultisigWalletParser.normalizeDescriptorPair(normalized)!!
            MultisigWalletParser.overrideMatchesConfig(normExternal, normInternal, config) shouldBe false
        }

        test("localXprvMatchesCosigner accepts the true key and rejects a wrong one") {
            MultisigWalletParser.localXprvMatchesCosigner(xprvA, xpubA) shouldBe true
            val xprvWrong = ElectrumSeedUtil.deriveXprv(seedC, path)
            MultisigWalletParser.localXprvMatchesCosigner(xprvWrong, xpubA) shouldBe false
            MultisigWalletParser.localXprvMatchesCosigner("notakey", xpubA) shouldBe false
        }
    }
})
