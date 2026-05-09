package github.aeonbtc.ibiswallet.data.boltz

import github.aeonbtc.ibiswallet.data.model.EstimatedSwapTerms
import github.aeonbtc.ibiswallet.data.model.PendingLightningInvoiceSession
import github.aeonbtc.ibiswallet.data.model.PendingLightningPaymentPhase
import github.aeonbtc.ibiswallet.data.model.PendingLightningPaymentSession
import github.aeonbtc.ibiswallet.data.model.PendingSwapPhase
import github.aeonbtc.ibiswallet.data.model.PendingSwapSession
import github.aeonbtc.ibiswallet.data.model.SwapDirection
import github.aeonbtc.ibiswallet.data.model.SwapService
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class BoltzBehaviorPortTest : FunSpec({

    test("resumes only durable chain swap phases on restart") {
        BullBoltzBehavior.shouldResumePendingSwapOnRestart(samplePendingSwap(PendingSwapPhase.REVIEW)) shouldBe false
        BullBoltzBehavior.shouldDiscardPendingSwapOnRestart(samplePendingSwap(PendingSwapPhase.REVIEW)) shouldBe true

        BullBoltzBehavior.shouldResumePendingSwapOnRestart(samplePendingSwap(PendingSwapPhase.FUNDING)) shouldBe true
        BullBoltzBehavior.shouldDiscardPendingSwapOnRestart(samplePendingSwap(PendingSwapPhase.FUNDING)) shouldBe false

        BullBoltzBehavior.shouldResumePendingSwapOnRestart(samplePendingSwap(PendingSwapPhase.IN_PROGRESS)) shouldBe true
        BullBoltzBehavior.shouldDiscardPendingSwapOnRestart(samplePendingSwap(PendingSwapPhase.FAILED)) shouldBe true
    }

    test("always resumes persisted lightning invoices") {
        BullBoltzBehavior.shouldResumePendingLightningInvoice(
            PendingLightningInvoiceSession(
                swapId = "swap-id",
                snapshot = "snapshot",
                invoice = "lnbc1example",
                amountSats = 25_000L,
            ),
        ) shouldBe true
    }

    test("reuses prepared lightning payment only for the same request key") {
        val session = sampleLightningPaymentSession(requestKey = "same")
        val fundedSession = sampleLightningPaymentSession(
            requestKey = "same",
            phase = PendingLightningPaymentPhase.IN_PROGRESS,
            fundingTxid = "funding-txid",
        )
        val ambiguousFundingSession = sampleLightningPaymentSession(
            requestKey = "same",
            phase = PendingLightningPaymentPhase.FUNDING,
            fundingTxid = null,
        )

        BullBoltzBehavior.shouldReusePreparedLightningPayment(session, requestKey = "same") shouldBe true
        BullBoltzBehavior.shouldReusePreparedLightningPayment(fundedSession, requestKey = "same") shouldBe true
        BullBoltzBehavior.shouldReusePreparedLightningPayment(ambiguousFundingSession, requestKey = "same") shouldBe false
        BullBoltzBehavior.shouldReusePreparedLightningPayment(session, requestKey = "other") shouldBe false
    }

    test("discards only stale unfunded prepared lightning payments") {
        val prepared = sampleLightningPaymentSession(
            requestKey = "prepared",
            phase = PendingLightningPaymentPhase.PREPARED,
            fundingTxid = null,
        )
        val active = sampleLightningPaymentSession(
            requestKey = "active",
            phase = PendingLightningPaymentPhase.IN_PROGRESS,
            fundingTxid = "funding-txid",
        )

        BullBoltzBehavior.shouldDiscardPreparedLightningPayment(prepared, requestKey = "other") shouldBe true
        BullBoltzBehavior.shouldDiscardPreparedLightningPayment(active, requestKey = "other") shouldBe false
    }
})

private fun samplePendingSwap(phase: PendingSwapPhase): PendingSwapSession {
    return PendingSwapSession(
        service = SwapService.BOLTZ,
        direction = SwapDirection.LBTC_TO_BTC,
        sendAmount = 50_000L,
        estimatedTerms = sampleBoltzTerms(),
        swapId = "swap-id",
        depositAddress = "lq1lockup",
        phase = phase,
    )
}

private fun sampleLightningPaymentSession(
    requestKey: String,
    phase: PendingLightningPaymentPhase = PendingLightningPaymentPhase.PREPARED,
    fundingTxid: String? = null,
): PendingLightningPaymentSession {
    return PendingLightningPaymentSession(
        swapId = "swap-id",
        requestKey = requestKey,
        paymentInput = "lnbc1example",
        lockupAddress = "lq1lockup",
        lockupAmountSats = 30_000L,
        refundAddress = "lq1refund",
        paymentAmountSats = 29_000L,
        swapFeeSats = 1_000L,
        phase = phase,
        fundingTxid = fundingTxid,
    )
}

private fun sampleBoltzTerms(): EstimatedSwapTerms {
    return EstimatedSwapTerms(
        receiveAmount = 49_000L,
        serviceFee = 200L,
        btcNetworkFee = 300L,
        liquidNetworkFee = 400L,
        estimatedTime = "~20 min",
    )
}
