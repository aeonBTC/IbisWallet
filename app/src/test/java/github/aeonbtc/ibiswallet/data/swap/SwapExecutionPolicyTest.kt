package github.aeonbtc.ibiswallet.data.swap

import github.aeonbtc.ibiswallet.data.model.EstimatedSwapTerms
import github.aeonbtc.ibiswallet.data.model.PendingSwapPhase
import github.aeonbtc.ibiswallet.data.model.PendingSwapSession
import github.aeonbtc.ibiswallet.data.model.SwapDirection
import github.aeonbtc.ibiswallet.data.model.SwapService
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class SwapExecutionPolicyTest : FunSpec({

    test("non-max BTC-funded swap executes reviewed amount exactly") {
        val request =
            buildBitcoinSwapFundingRequest(
                pendingSwap = samplePendingSwap(usesMaxAmount = false),
                feeRateSatPerVb = 4.5,
            )

        request.recipientAddress shouldBe "bc1qdeposit"
        request.amountSats shouldBe 24_582L
        request.feeRateSatPerVb shouldBe 4.5
        request.isMaxSend shouldBe false
    }

    test("max BTC-funded swap also executes reviewed amount exactly") {
        val request =
            buildBitcoinSwapFundingRequest(
                pendingSwap = samplePendingSwap(usesMaxAmount = true),
                feeRateSatPerVb = 7.25,
            )

        request.amountSats shouldBe 24_582L
        request.isMaxSend shouldBe false
    }

    test("non-max LBTC-funded swap executes reviewed amount exactly") {
        val request =
            buildLiquidSwapFundingRequest(
                pendingSwap = samplePendingSwap(direction = SwapDirection.LBTC_TO_BTC, usesMaxAmount = false),
                feeRateSatPerVb = 0.4,
            )

        request.recipientAddress shouldBe "lq1deposit"
        request.amountSats shouldBe 24_582L
        request.feeRateSatPerVb shouldBe 0.4
        request.isMaxSend shouldBe false
    }

    test("max LBTC-funded swap executes as a reviewed drain") {
        val request =
            buildLiquidSwapFundingRequest(
                pendingSwap = samplePendingSwap(direction = SwapDirection.LBTC_TO_BTC, usesMaxAmount = true),
                feeRateSatPerVb = 0.8,
            )

        request.recipientAddress shouldBe "lq1deposit"
        request.amountSats shouldBe 24_582L
        request.feeRateSatPerVb shouldBe 0.8
        request.isMaxSend shouldBe true
    }

    test("max Boltz LBTC-funded swap prefers verified recipient amount") {
        val request =
            buildLiquidSwapFundingRequest(
                pendingSwap =
                    samplePendingSwap(
                        service = SwapService.BOLTZ,
                        direction = SwapDirection.LBTC_TO_BTC,
                        usesMaxAmount = true,
                        boltzVerifiedRecipientAmountSats = 24_560L,
                    ),
                feeRateSatPerVb = 0.8,
            )

        request.recipientAddress shouldBe "lq1deposit"
        request.amountSats shouldBe 24_560L
        request.feeRateSatPerVb shouldBe 0.8
        request.isMaxSend shouldBe true
    }

    test("sideswap BTC to LBTC uses custom liquid payout when provided") {
        resolveSideSwapRequestedReceiveAddress(
            direction = SwapDirection.BTC_TO_LBTC,
            destinationAddress = "lq1custom",
            bitcoinWalletAddress = "bc1qwallet",
            generatedLiquidAddress = "lq1generated",
        ) shouldBe "lq1custom"
    }

    test("sideswap BTC to LBTC falls back to generated liquid address") {
        resolveSideSwapRequestedReceiveAddress(
            direction = SwapDirection.BTC_TO_LBTC,
            destinationAddress = null,
            bitcoinWalletAddress = "bc1qwallet",
            generatedLiquidAddress = "lq1generated",
        ) shouldBe "lq1generated"
    }

    test("sideswap LBTC to BTC uses custom bitcoin payout when provided") {
        resolveSideSwapRequestedReceiveAddress(
            direction = SwapDirection.LBTC_TO_BTC,
            destinationAddress = "bc1qcustom",
            bitcoinWalletAddress = "bc1qwallet",
        ) shouldBe "bc1qcustom"
    }

    test("sideswap LBTC to BTC falls back to wallet bitcoin address") {
        resolveSideSwapRequestedReceiveAddress(
            direction = SwapDirection.LBTC_TO_BTC,
            destinationAddress = null,
            bitcoinWalletAddress = "bc1qwallet",
        ) shouldBe "bc1qwallet"
    }

    test("sideswap LBTC to BTC requires a bitcoin payout address") {
        shouldThrow<IllegalArgumentException> {
            resolveSideSwapRequestedReceiveAddress(
                direction = SwapDirection.LBTC_TO_BTC,
                destinationAddress = null,
                bitcoinWalletAddress = null,
            )
        }
    }

    test("boltz BTC to LBTC keeps bitcoin refund separate from liquid payout override") {
        val plan =
            resolveBoltzAddressPlan(
                direction = SwapDirection.BTC_TO_LBTC,
                destinationAddress = "lq1custom",
                bitcoinWalletAddress = "bc1qrefund",
            )

        plan.providerBitcoinAddress shouldBe "bc1qrefund"
        plan.liquidDestinationOverride shouldBe "lq1custom"
    }

    test("boltz LBTC to BTC uses custom bitcoin payout and no liquid override") {
        val plan =
            resolveBoltzAddressPlan(
                direction = SwapDirection.LBTC_TO_BTC,
                destinationAddress = "bc1qcustom",
                bitcoinWalletAddress = "bc1qwallet",
            )

        plan.providerBitcoinAddress shouldBe "bc1qcustom"
        plan.liquidDestinationOverride shouldBe null
    }

    test("boltz LBTC to BTC falls back to wallet bitcoin address") {
        val plan =
            resolveBoltzAddressPlan(
                direction = SwapDirection.LBTC_TO_BTC,
                destinationAddress = null,
                bitcoinWalletAddress = "bc1qwallet",
            )

        plan.providerBitcoinAddress shouldBe "bc1qwallet"
        plan.liquidDestinationOverride shouldBe null
    }
})

private fun samplePendingSwap(
    service: SwapService = SwapService.SIDESWAP,
    direction: SwapDirection = SwapDirection.BTC_TO_LBTC,
    usesMaxAmount: Boolean,
    boltzVerifiedRecipientAmountSats: Long? = null,
): PendingSwapSession {
    val isBtcFunded = direction == SwapDirection.BTC_TO_LBTC
    return PendingSwapSession(
        service = service,
        direction = direction,
        sendAmount = 24_582L,
        usesMaxAmount = usesMaxAmount,
        estimatedTerms =
            EstimatedSwapTerms(
                receiveAmount = 10_703L,
                serviceFee = 100L,
                btcNetworkFee = 220L,
                liquidNetworkFee = 45L,
                estimatedTime = "~20 min",
            ),
        swapId = "swap-id",
        depositAddress = if (isBtcFunded) "bc1qdeposit" else "lq1deposit",
        receiveAddress = if (isBtcFunded) "lq1receive" else "bc1qreceive",
        boltzVerifiedRecipientAmountSats = boltzVerifiedRecipientAmountSats,
        phase = PendingSwapPhase.REVIEW,
        status = "Exact order prepared.",
    )
}
