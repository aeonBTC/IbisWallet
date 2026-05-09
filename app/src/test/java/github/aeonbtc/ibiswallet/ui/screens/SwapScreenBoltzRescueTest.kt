package github.aeonbtc.ibiswallet.ui.screens

import github.aeonbtc.ibiswallet.data.model.EstimatedSwapTerms
import github.aeonbtc.ibiswallet.data.model.PendingSwapPhase
import github.aeonbtc.ibiswallet.data.model.PendingSwapSession
import github.aeonbtc.ibiswallet.data.model.SwapDirection
import github.aeonbtc.ibiswallet.data.model.SwapService
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class SwapScreenBoltzRescueTest : FunSpec({

    val estimatedTerms =
        EstimatedSwapTerms(
            receiveAmount = 95_000L,
            serviceFee = 500L,
            btcNetworkFee = 250L,
            liquidNetworkFee = 250L,
            estimatedTime = "10 min",
        )

    fun pendingSwap(
        service: SwapService,
        phase: PendingSwapPhase = PendingSwapPhase.IN_PROGRESS,
    ): PendingSwapSession {
        return PendingSwapSession(
            service = service,
            direction = SwapDirection.BTC_TO_LBTC,
            sendAmount = 100_000L,
            estimatedTerms = estimatedTerms,
            swapId = "swap-123",
            depositAddress = "bc1qdepositaddress0000000000000000000000000",
            receiveAddress = "lq1qqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqq",
            refundAddress = "bc1qrefundaddress0000000000000000000000000",
            phase = phase,
        )
    }

    test("shows rescue mnemonic only for in-progress boltz swaps") {
        shouldShowBoltzRescueMnemonic(
            pendingSwap = pendingSwap(SwapService.BOLTZ),
            boltzRescueMnemonic = "abandon ability able about above absent absorb abstract absurd abuse access accident",
        ) shouldBe true

        shouldShowBoltzRescueMnemonic(
            pendingSwap = pendingSwap(SwapService.SIDESWAP),
            boltzRescueMnemonic = "abandon ability able about above absent absorb abstract absurd abuse access accident",
        ) shouldBe false
    }

    test("hides rescue mnemonic for review swaps") {
        shouldShowBoltzRescueMnemonic(
            pendingSwap = pendingSwap(SwapService.BOLTZ, phase = PendingSwapPhase.REVIEW),
            boltzRescueMnemonic = "abandon ability able about above absent absorb abstract absurd abuse access accident",
        ) shouldBe false
    }

    test("hides rescue mnemonic when it is unavailable") {
        shouldShowBoltzRescueMnemonic(
            pendingSwap = pendingSwap(SwapService.BOLTZ),
            boltzRescueMnemonic = null,
        ) shouldBe false

        shouldShowBoltzRescueMnemonic(
            pendingSwap = pendingSwap(SwapService.BOLTZ),
            boltzRescueMnemonic = " ",
        ) shouldBe false
    }
})
