package github.aeonbtc.ibiswallet.data.model

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class LiquidPsetDetailsTest : FunSpec({

    test("LiquidPsetDetails holds export fields for UI") {
        val details =
            LiquidPsetDetails(
                psetBase64 = "cHNldP8=",
                recipientAddress = "lq1qqtest",
                recipientAmountSats = 1000L,
                feeSats = 120L,
                changeAmountSats = 50L,
                totalInputSats = 1170L,
                inputCount = 1,
                outputCount = 3,
                assetId = null,
                missingFingerprints = listOf("aabbccdd"),
            )
        details.recipientAmountSats shouldBe 1000L
        details.missingFingerprints shouldBe listOf("aabbccdd")
        details.assetId shouldBe null
    }

    test("LiquidPsetState defaults are idle") {
        val state = LiquidPsetState()
        state.isCreating shouldBe false
        state.isCombining shouldBe false
        state.isBroadcasting shouldBe false
        state.signedData shouldBe null
        state.isReadyToBroadcast shouldBe false
    }
})
