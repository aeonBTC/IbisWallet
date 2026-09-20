package github.aeonbtc.ibiswallet.data.model

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class ArkBoardFeePolicyTest : FunSpec({

    context("hasBoardFeeBreakdown") {
        test("null means legacy no-breakdown") {
            ArkBoardFeePolicy.hasBoardFeeBreakdown(null) shouldBe false
        }

        test("zero and negative mean no-breakdown") {
            ArkBoardFeePolicy.hasBoardFeeBreakdown(0L) shouldBe false
            ArkBoardFeePolicy.hasBoardFeeBreakdown(-5L) shouldBe false
        }

        test("positive means breakdown") {
            ArkBoardFeePolicy.hasBoardFeeBreakdown(1L) shouldBe true
        }
    }

    context("combinedTotalFeeSats") {
        test("legacy null board fee keeps L1 fee") {
            ArkBoardFeePolicy.combinedTotalFeeSats(500L, null) shouldBe 500L
        }

        test("zero board fee keeps L1 fee") {
            ArkBoardFeePolicy.combinedTotalFeeSats(500L, 0L) shouldBe 500L
        }

        test("single-tx sums both components") {
            ArkBoardFeePolicy.combinedTotalFeeSats(500L, 200L) shouldBe 700L
        }
    }

    context("BoardPreview single-tx fields") {
        test("defaults to legacy 2-tx (no funding info)") {
            val preview =
                ArkTransferState.BoardPreview(
                    amountSats = 100_000L,
                    feeSats = 500L,
                    netAmountSats = 99_500L,
                    boardAll = false,
                )
            preview.boardFunding shouldBe null
            preview.boardFeeSats shouldBe null
        }

        test("carries funding scalars for boardPsbt echo-back") {
            val funding =
                ArkTransferState.BoardFundingInfo(
                    address = "bc1qtest",
                    keypairIndex = 7u,
                    expiryHeight = 900_000u,
                )
            val preview =
                ArkTransferState.BoardPreview(
                    amountSats = 100_000L,
                    feeSats = 500L,
                    netAmountSats = 99_500L,
                    boardAll = false,
                    bitcoinDepositAddress = funding.address,
                    boardFunding = funding,
                    boardFeeSats = 200L,
                )
            preview.boardFunding shouldBe funding
            preview.boardFunding?.keypairIndex shouldBe 7u
            preview.boardFunding?.expiryHeight shouldBe 900_000u
            ArkBoardFeePolicy.combinedTotalFeeSats(
                preview.feeSats ?: 0L,
                preview.boardFeeSats,
            ) shouldBe 700L
        }
    }
})
