package github.aeonbtc.ibiswallet.util

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class SpScanStartPolicyTest : FunSpec({
    val min = 709632
    val tip = 900000

    context("parseCustomHeight") {
        test("accepts a height inside bounds") {
            SpScanStartPolicy.parseCustomHeight("800000", min, tip) shouldBe 800000
        }

        test("accepts the activation and tip edges") {
            SpScanStartPolicy.parseCustomHeight("709632", min, tip) shouldBe 709632
            SpScanStartPolicy.parseCustomHeight("900000", min, tip) shouldBe 900000
        }

        test("rejects below activation, above tip, blank and non-numeric") {
            SpScanStartPolicy.parseCustomHeight("709631", min, tip) shouldBe null
            SpScanStartPolicy.parseCustomHeight("900001", min, tip) shouldBe null
            SpScanStartPolicy.parseCustomHeight("", min, tip) shouldBe null
            SpScanStartPolicy.parseCustomHeight("abc", min, tip) shouldBe null
        }

        test("accepts above-tip input when the tip is unknown") {
            SpScanStartPolicy.parseCustomHeight("950000", min, null) shouldBe 950000
        }
    }

    context("resolveSelection") {
        test("tip selection short-circuits to Tip") {
            SpScanStartPolicy.resolveSelection(true, "", min, tip) shouldBe
                SpScanStartPolicy.Selection.Tip
        }

        test("custom selection maps valid input to Height") {
            SpScanStartPolicy.resolveSelection(false, "800000", min, tip) shouldBe
                SpScanStartPolicy.Selection.Height(800000)
        }

        test("custom selection maps invalid input to Invalid") {
            SpScanStartPolicy.resolveSelection(false, "12", min, tip) shouldBe
                SpScanStartPolicy.Selection.Invalid
        }
    }
})
