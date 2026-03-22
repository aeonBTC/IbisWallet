package github.aeonbtc.ibiswallet.data.boltz

import github.aeonbtc.ibiswallet.data.model.PendingLightningInvoiceSession
import github.aeonbtc.ibiswallet.data.model.PendingLightningPaymentPhase
import github.aeonbtc.ibiswallet.data.model.PendingLightningPaymentSession
import github.aeonbtc.ibiswallet.data.model.PendingSwapPhase
import github.aeonbtc.ibiswallet.data.model.PendingSwapSession
import github.aeonbtc.ibiswallet.data.model.SwapDirection

internal interface BoltzBehaviorPort {
    fun shouldPreResolveMaxAmount(
        direction: SwapDirection,
        usesMaxAmount: Boolean,
    ): Boolean

    fun shouldUseMaxFundingPreview(
        direction: SwapDirection,
        usesMaxAmount: Boolean,
    ): Boolean

    fun shouldDiscardPendingSwapOnRestart(pendingSwap: PendingSwapSession): Boolean

    fun shouldResumePendingSwapOnRestart(pendingSwap: PendingSwapSession): Boolean

    fun shouldResumePendingLightningInvoice(session: PendingLightningInvoiceSession): Boolean

    fun shouldReusePreparedLightningPayment(
        session: PendingLightningPaymentSession,
        requestKey: String,
    ): Boolean

    fun shouldDiscardPreparedLightningPayment(
        session: PendingLightningPaymentSession,
        requestKey: String,
    ): Boolean
}

internal object BullBoltzBehavior : BoltzBehaviorPort {
    override fun shouldPreResolveMaxAmount(
        direction: SwapDirection,
        usesMaxAmount: Boolean,
    ): Boolean {
        return usesMaxAmount && direction == SwapDirection.BTC_TO_LBTC
    }

    override fun shouldUseMaxFundingPreview(
        direction: SwapDirection,
        usesMaxAmount: Boolean,
    ): Boolean {
        return usesMaxAmount &&
            (direction == SwapDirection.BTC_TO_LBTC || direction == SwapDirection.LBTC_TO_BTC)
    }

    override fun shouldDiscardPendingSwapOnRestart(pendingSwap: PendingSwapSession): Boolean {
        return when (pendingSwap.phase) {
            PendingSwapPhase.REVIEW,
            PendingSwapPhase.FAILED,
            -> true
            PendingSwapPhase.FUNDING,
            PendingSwapPhase.IN_PROGRESS,
            -> false
        }
    }

    override fun shouldResumePendingSwapOnRestart(pendingSwap: PendingSwapSession): Boolean {
        return when (pendingSwap.phase) {
            PendingSwapPhase.FUNDING,
            PendingSwapPhase.IN_PROGRESS,
            -> true
            PendingSwapPhase.REVIEW,
            PendingSwapPhase.FAILED,
            -> false
        }
    }

    override fun shouldResumePendingLightningInvoice(session: PendingLightningInvoiceSession): Boolean = true

    override fun shouldReusePreparedLightningPayment(
        session: PendingLightningPaymentSession,
        requestKey: String,
    ): Boolean {
        if (session.requestKey != requestKey) {
            return false
        }
        return when (session.phase) {
            PendingLightningPaymentPhase.PREPARED -> true
            PendingLightningPaymentPhase.FUNDING,
            PendingLightningPaymentPhase.IN_PROGRESS,
            -> !session.fundingTxid.isNullOrBlank()
            PendingLightningPaymentPhase.REFUNDING,
            PendingLightningPaymentPhase.FAILED,
            -> false
        }
    }

    override fun shouldDiscardPreparedLightningPayment(
        session: PendingLightningPaymentSession,
        requestKey: String,
    ): Boolean {
        if (session.requestKey == requestKey) {
            return false
        }
        return session.phase == PendingLightningPaymentPhase.PREPARED &&
            session.fundingTxid.isNullOrBlank()
    }
}
