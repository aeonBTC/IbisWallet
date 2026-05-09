package github.aeonbtc.ibiswallet.data.model

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class LiquidModelsTest : FunSpec({

    context("LiquidSendPreview.totalSpendSats") {
        test("adds fees on top of the payment amount") {
            val preview =
                LiquidSendPreview(
                    kind = LiquidSendKind.LIGHTNING_BOLT11,
                    recipientDisplay = "lnbc...",
                    totalRecipientSats = 40_000L,
                    feeSats = 520L,
                )

            preview.totalSpendSats shouldBe 40_520L
        }
    }

    context("SwapQuote total fee helpers") {
        test("preserves swap fee aggregation semantics") {
            val quote =
                SwapQuote(
                    service = SwapService.BOLTZ,
                    direction = SwapDirection.BTC_TO_LBTC,
                    sendAmount = 100_000L,
                    receiveAmount = 98_500L,
                    serviceFee = 1_000L,
                    btcNetworkFee = 200L,
                    liquidNetworkFee = 250L,
                    providerMinerFee = 50L,
                    estimatedTime = "~20 min",
                )

            quote.totalNetworkFee shouldBe 500L
            quote.totalFee shouldBe 1_500L
        }
    }

    context("EstimatedSwapTerms Boltz fee breakdown helpers") {
        test("keeps funding fee separate from payout deductions for peg-in reviews") {
            val terms =
                EstimatedSwapTerms(
                    receiveAmount = 29_541L,
                    serviceFee = 30L,
                    btcNetworkFee = 153L,
                    liquidNetworkFee = 143L,
                    providerMinerFee = 67L,
                    btcFeeRate = 1.0,
                    displayLiquidFeeRate = 0.1,
                    fundingNetworkFee = 153L,
                    fundingFeeRate = 1.0,
                    payoutNetworkFee = 143L,
                    payoutFeeRate = 0.1,
                    estimatedTime = "~20 min",
                )

            terms.fundingNetworkFeeFor(SwapDirection.BTC_TO_LBTC) shouldBe 153L
            terms.fundingFeeRateFor(SwapDirection.BTC_TO_LBTC) shouldBe 1.0
            terms.payoutNetworkFeeFor(SwapDirection.BTC_TO_LBTC) shouldBe 143L
            terms.payoutFeeRateFor(SwapDirection.BTC_TO_LBTC) shouldBe 0.1
            terms.otherDeductionsFor(SwapDirection.BTC_TO_LBTC) shouldBe 240L
            terms.totalSwapDeductionsFor(SwapDirection.BTC_TO_LBTC) shouldBe 393L
        }

        test("falls back to legacy directional fields for persisted peg-out reviews") {
            val terms =
                EstimatedSwapTerms(
                    receiveAmount = 29_541L,
                    serviceFee = 30L,
                    btcNetworkFee = 220L,
                    liquidNetworkFee = 153L,
                    providerMinerFee = 10L,
                    btcFeeRate = 1.0,
                    displayLiquidFeeRate = 0.1,
                    estimatedTime = "~20 min",
                )

            terms.fundingNetworkFeeFor(SwapDirection.LBTC_TO_BTC) shouldBe 153L
            terms.fundingFeeRateFor(SwapDirection.LBTC_TO_BTC) shouldBe 0.1
            terms.payoutNetworkFeeFor(SwapDirection.LBTC_TO_BTC) shouldBe 220L
            terms.payoutFeeRateFor(SwapDirection.LBTC_TO_BTC) shouldBe 1.0
            terms.otherDeductionsFor(SwapDirection.LBTC_TO_BTC) shouldBe 260L
            terms.totalSwapDeductionsFor(SwapDirection.LBTC_TO_BTC) shouldBe 413L
        }
    }
})
