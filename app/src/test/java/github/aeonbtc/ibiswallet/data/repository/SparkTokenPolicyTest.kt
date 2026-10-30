package github.aeonbtc.ibiswallet.data.repository

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * Traces the Spark native-token (BTKN) identifier gate: normalization plus
 * the local plausibility check that runs before the metadata lookup.
 */
class SparkTokenPolicyTest : FunSpec({

    context("normalizeTokenIdentifier") {
        test("trims and lowercases") {
            SparkTokenPolicy.normalizeTokenIdentifier("  BTKN1Qxyz  ") shouldBe "btkn1qxyz"
        }
    }

    context("isPlausibleTokenIdentifier") {
        test("well-formed id passes") {
            SparkTokenPolicy.isPlausibleTokenIdentifier("btkn1qpzry9x8gf2tvdw0s3jn54khce6mua7l") shouldBe true
        }

        test("empty fails") {
            SparkTokenPolicy.isPlausibleTokenIdentifier("   ") shouldBe false
        }

        test("wrong prefix fails") {
            SparkTokenPolicy.isPlausibleTokenIdentifier("spark1qpzry9x8gf2tvdw0s3jn54khce6mua7l") shouldBe false
        }

        test("too short fails") {
            SparkTokenPolicy.isPlausibleTokenIdentifier("btkn1qp") shouldBe false
        }

        test("non-bech32 chars fail") {
            SparkTokenPolicy.isPlausibleTokenIdentifier("btkn1qqqqbiii") shouldBe false
        }
    }
})
