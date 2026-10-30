package github.aeonbtc.ibiswallet.data.repository

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class ArkRefreshFailurePolicyTest : FunSpec({

    test("definitive refresh rejections are identified") {
        ArkRefreshFailurePolicy.isDefinitive("VTXO is not spendable") shouldBe true
        ArkRefreshFailurePolicy.isDefinitive("unknown VTXO") shouldBe true
        ArkRefreshFailurePolicy.isDefinitive("round participation not found") shouldBe true
        ArkRefreshFailurePolicy.isDefinitive("already spent") shouldBe true
    }

    test("transport and timeout errors remain ambiguous") {
        ArkRefreshFailurePolicy.isDefinitive("connection reset") shouldBe false
        ArkRefreshFailurePolicy.isDefinitive("request timed out") shouldBe false
        ArkRefreshFailurePolicy.isDefinitive("DNS lookup failed") shouldBe false
        ArkRefreshFailurePolicy.isDefinitive("TLS certificate expired") shouldBe false
        ArkRefreshFailurePolicy.isDefinitive("") shouldBe false
    }
})
