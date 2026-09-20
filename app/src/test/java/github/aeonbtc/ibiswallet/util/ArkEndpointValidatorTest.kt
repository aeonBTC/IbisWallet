package github.aeonbtc.ibiswallet.util

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class ArkEndpointValidatorTest : FunSpec({

    context("clearnet") {
        test("accepts https ASP and Esplora") {
            ArkEndpointValidator.isValid("https://ark.second.tech") shouldBe true
            ArkEndpointValidator.isValid("https://mempool.second.tech/api") shouldBe true
        }

        test("rejects plain http clearnet") {
            ArkEndpointValidator.isValid("http://ark.second.tech") shouldBe false
            ArkEndpointValidator.validate("http://example.com/api") shouldNotBe null
        }

        test("rejects credentials and query") {
            ArkEndpointValidator.isValid("https://user:pass@ark.second.tech") shouldBe false
            ArkEndpointValidator.isValid("https://ark.second.tech?x=1") shouldBe false
        }
    }

    context("onion") {
        // v3 onion is 56 base32 chars
        val onionHost = "a".repeat(56) + ".onion"

        test("accepts http onion") {
            ArkEndpointValidator.isValid("http://$onionHost/api") shouldBe true
        }

        test("accepts https onion") {
            ArkEndpointValidator.isValid("https://$onionHost") shouldBe true
        }
    }

    context("normalize") {
        test("trims and strips trailing slash") {
            ArkEndpointValidator.normalize("  https://example.com/api/  ") shouldBe
                "https://example.com/api"
        }
    }

    context("network consistency") {
        test("mainnet pair is consistent") {
            ArkEndpointValidator.isConsistentPair(
                "https://ark.second.tech",
                "https://mempool.second.tech/api",
            ) shouldBe true
        }

        test("testnet ASP with mainnet Esplora is inconsistent") {
            ArkEndpointValidator.isConsistentPair(
                "https://testnet.ark.second.tech",
                "https://mempool.second.tech/api",
            ) shouldBe false
        }

        test("signet Esplora with mainnet ASP is inconsistent") {
            ArkEndpointValidator.isConsistentPair(
                "https://ark.second.tech",
                "https://signet.esplora.example.com/api",
            ) shouldBe false
        }

        test("matching testnet markers are consistent") {
            ArkEndpointValidator.isConsistentPair(
                "https://testnet.ark.example.com",
                "https://testnet.esplora.example.com/api",
            ) shouldBe true
        }

        test("blank side skips the pair check") {
            ArkEndpointValidator.isConsistentPair("", "https://mempool.second.tech/api") shouldBe true
            ArkEndpointValidator.isConsistentPair("https://ark.second.tech", "") shouldBe true
        }

        test("marker needs token boundaries") {
            ArkEndpointValidator.networkMarker("https://latestnet.example.com") shouldBe null
            ArkEndpointValidator.networkMarker("https://testnet.example.com") shouldBe "testnet"
        }
    }
})
