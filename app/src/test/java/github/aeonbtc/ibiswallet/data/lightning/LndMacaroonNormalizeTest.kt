package github.aeonbtc.ibiswallet.data.lightning

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class LndMacaroonNormalizeTest : StringSpec({
    "keeps hex macaroon lowercase" {
        val hex = "a1b2c3d4"
        LndRestClient.normalizeMacaroon(hex) shouldBe "a1b2c3d4"
    }

    "converts base64 macaroon to hex" {
        // "test" in base64 is dGVzdA==
        val result = LndRestClient.normalizeMacaroon("dGVzdA==")
        result shouldBe "74657374"
    }

    "strips whitespace from hex" {
        LndRestClient.normalizeMacaroon("aa bb") shouldBe "aabb"
    }
})
