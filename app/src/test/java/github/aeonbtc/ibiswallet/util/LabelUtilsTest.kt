package github.aeonbtc.ibiswallet.util

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class LabelUtilsTest : FunSpec({
    context("normalize") {
        test("trims whitespace") {
            LabelUtils.normalize("  Savings  ") shouldBe "Savings"
        }

        test("treats blank as no label") {
            LabelUtils.normalize("   ") shouldBe null
            LabelUtils.normalize(null) shouldBe null
        }
    }

    context("resolve") {
        test("prefers the explicit transaction label") {
            LabelUtils.resolve("Payment", "Recipient") shouldBe "Payment"
        }

        test("falls back to the address label") {
            LabelUtils.resolve(null, "Recipient") shouldBe "Recipient"
        }

        test("ignores blank transaction labels") {
            LabelUtils.resolve("   ", "Recipient") shouldBe "Recipient"
        }

        test("returns null when neither side has a label") {
            LabelUtils.resolve(null, null) shouldBe null
        }
    }
})
