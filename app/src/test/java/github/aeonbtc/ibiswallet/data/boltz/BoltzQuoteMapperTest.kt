package github.aeonbtc.ibiswallet.data.boltz

import github.aeonbtc.ibiswallet.data.model.BoltzFees
import github.aeonbtc.ibiswallet.data.model.BoltzLimits
import github.aeonbtc.ibiswallet.data.model.BoltzPairInfo
import github.aeonbtc.ibiswallet.data.model.SwapDirection
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class BoltzQuoteMapperTest : FunSpec({

    test("pair-backed peg-out quote includes provider miner fee and bitcoin payout fee rate") {
        val quote =
            buildBoltzQuoteFromPair(
                direction = SwapDirection.LBTC_TO_BTC,
                amountSats = 30_000L,
                pair = samplePair(serverMinerFee = 325L, userLockupFee = 41L, userClaimFee = 183L),
                estimatedTime = "~20 min",
                typicalBtcTxVsize = 140,
                defaultLiquidSwapFeeRate = 0.1,
            )

        quote.providerMinerFee shouldBe 325L
        quote.btcNetworkFee shouldBe 183L
        quote.liquidNetworkFee shouldBe 41L
        quote.btcFeeRate shouldBe (183.0 / 140.0)
        quote.receiveAmount shouldBe 29_462L
    }

    test("warm-session enrichment preserves the received amount while filling missing fee metadata") {
        val quote =
            buildBoltzQuoteFromPair(
                direction = SwapDirection.LBTC_TO_BTC,
                amountSats = 30_000L,
                pair = samplePair(serverMinerFee = 366L, userLockupFee = 41L, userClaimFee = 183L),
                estimatedTime = "~20 min",
                typicalBtcTxVsize = 140,
                defaultLiquidSwapFeeRate = 0.1,
                receiveAmountOverride = 29_421L,
                serviceFeeOverride = 30L,
            )

        quote.receiveAmount shouldBe 29_421L
        quote.serviceFee shouldBe 30L
        quote.providerMinerFee shouldBe 366L
        quote.btcFeeRate shouldBe (183.0 / 140.0)
    }

    test("reverse invoice amount helper preserves the requested direct-settlement amount") {
        val pair = samplePair(serverMinerFee = 35L, userLockupFee = 0L, userClaimFee = 0L, percentage = 0.5)

        estimateBoltzReverseOnchainAmount(5_000L, pair) shouldBe 4_940L

        val invoiceAmount = estimateBoltzReverseInvoiceAmount(desiredOnchainAmountSats = 5_000L, pair = pair)
        invoiceAmount shouldBe 5_061L
        estimateBoltzReverseOnchainAmount(invoiceAmount, pair) shouldBe 5_000L
    }
})

private fun samplePair(
    serverMinerFee: Long,
    userLockupFee: Long,
    userClaimFee: Long,
    percentage: Double = 0.1,
): BoltzPairInfo {
    return BoltzPairInfo(
        hash = "hash",
        rate = 1.0,
        limits = BoltzLimits(minimal = 1_000L, maximal = 1_000_000L),
        fees = BoltzFees(
            percentage = percentage,
            serverMinerFee = serverMinerFee,
            userLockupFee = userLockupFee,
            userClaimFee = userClaimFee,
        ),
    )
}
