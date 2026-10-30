package github.aeonbtc.ibiswallet.data.repository

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class ArkSendFailurePolicyTest : FunSpec({

    test("local validation failures are definitive") {
        ArkSendFailurePolicy.isDefinitive("Insufficient funds") shouldBe true
        ArkSendFailurePolicy.isDefinitive("invalid address") shouldBe true
        ArkSendFailurePolicy.isDefinitive("No route to destination") shouldBe true
    }

    test("transport and post-cosign errors are unknown") {
        ArkSendFailurePolicy.isDefinitive("connection reset by peer") shouldBe false
        ArkSendFailurePolicy.isDefinitive("timed out") shouldBe false
        ArkSendFailurePolicy.isDefinitive("is not spendable") shouldBe false
        ArkSendFailurePolicy.isDefinitive("") shouldBe false
    }

    test("stale inputs are never treated as a safe-to-tombstone failure") {
        ArkSendFailurePolicy.classify("vtxo is not spendable") shouldBe
            ArkSendFailurePolicy.Kind.UNKNOWN
    }
})
