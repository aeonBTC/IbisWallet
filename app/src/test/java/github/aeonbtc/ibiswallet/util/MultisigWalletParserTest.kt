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
})
