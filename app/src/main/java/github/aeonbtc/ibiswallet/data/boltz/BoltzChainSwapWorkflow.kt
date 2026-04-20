package github.aeonbtc.ibiswallet.data.boltz

import android.util.Log
import github.aeonbtc.ibiswallet.BuildConfig
import github.aeonbtc.ibiswallet.data.model.BoltzChainFundingPreview
import github.aeonbtc.ibiswallet.data.model.BoltzChainSwapDraft
import github.aeonbtc.ibiswallet.data.model.BoltzChainSwapDraftState
import github.aeonbtc.ibiswallet.data.model.BoltzChainSwapOrder
import github.aeonbtc.ibiswallet.data.model.PendingSwapPhase
import github.aeonbtc.ibiswallet.data.model.PendingSwapSession
import github.aeonbtc.ibiswallet.data.model.SwapService
import kotlinx.coroutines.TimeoutCancellationException

class BoltzChainSwapWorkflow(
    private val nowMs: () -> Long = System::currentTimeMillis,
) {
    enum class CreateFailureKind {
        APP_TIMEOUT,
        PROVIDER_TIMEOUT,
        OTHER,
    }

    data class CreatedDraftResult(
        val draft: BoltzChainSwapDraft,
        val usedExistingDraft: Boolean,
    )

    fun canReuseDraft(draft: BoltzChainSwapDraft?): Boolean {
        val candidate = draft ?: return false
        val reusableState =
            when (candidate.state) {
                BoltzChainSwapDraftState.CREATED_UNREVIEWED,
                BoltzChainSwapDraftState.FUNDING_BROADCAST,
                BoltzChainSwapDraftState.IN_PROGRESS,
                -> true
                BoltzChainSwapDraftState.REVIEW_READY ->
                    candidate.reviewExpiresAt <= 0L || nowMs() <= candidate.reviewExpiresAt
                else -> false
            }
        return reusableState &&
            !candidate.swapId.isNullOrBlank() &&
            !candidate.depositAddress.isNullOrBlank() &&
            !candidate.snapshot.isNullOrBlank()
    }

    suspend fun createOrRecoverDraft(
        existingDraft: BoltzChainSwapDraft?,
        creatingDraft: BoltzChainSwapDraft,
        createOrder: suspend () -> BoltzChainSwapOrder,
        saveDraft: suspend (BoltzChainSwapDraft) -> Unit,
        resetSession: suspend () -> Unit,
        classifyFailure: (Throwable) -> CreateFailureKind,
    ): CreatedDraftResult {
        logDebug(
            "createOrRecoverDraft start requestKey=${creatingDraft.requestKey} direction=${creatingDraft.direction} " +
                "amount=${creatingDraft.sendAmount} usesMax=${creatingDraft.usesMaxAmount} " +
                "existing=${existingDraft?.state?.name ?: "none"}",
        )
        existingDraft
            ?.takeIf(::canReuseDraft)
            ?.let {
                logDebug(
                    "Reusing existing draft requestKey=${it.requestKey} swapId=${it.swapId} state=${it.state} " +
                        "reviewExpiresAt=${it.reviewExpiresAt}",
                )
                return CreatedDraftResult(
                    draft = it,
                    usedExistingDraft = true,
                )
            }

        var activeDraft = creatingDraft.copy(
            state = BoltzChainSwapDraftState.CREATING,
            updatedAt = nowMs(),
            lastError = null,
        )
        saveDraft(activeDraft)
        logDebug(
            "Saved creating draft requestKey=${activeDraft.requestKey} draftId=${activeDraft.draftId} " +
                "state=${activeDraft.state}",
        )

        var lastError: Throwable? = null
        val attempt = 1
        logDebug(
            "Create attempt=$attempt requestKey=${activeDraft.requestKey} direction=${activeDraft.direction} " +
                "amount=${activeDraft.sendAmount}",
        )
        try {
            val order = createOrder()
            requireValidOrder(order)
            activeDraft =
                activeDraft.copy(
                    swapId = order.swapId,
                    depositAddress = order.lockupAddress,
                    receiveAddress = order.receiveAddress,
                    refundAddress = order.refundAddress,
                    snapshot = order.snapshot,
                    state = BoltzChainSwapDraftState.CREATED_UNREVIEWED,
                    updatedAt = nowMs(),
                    lastError = null,
                )
            saveDraft(activeDraft)
            logDebug(
                "Create succeeded attempt=$attempt requestKey=${activeDraft.requestKey} swapId=${activeDraft.swapId} " +
                    "lockup=${summarizeValue(activeDraft.depositAddress)} state=${activeDraft.state}",
            )
            return CreatedDraftResult(
                draft = activeDraft,
                usedExistingDraft = false,
            )
        } catch (error: Throwable) {
            lastError = error
            val failureKind = classifyFailure(error)
            logWarn(
                "Create failed attempt=$attempt requestKey=${activeDraft.requestKey} failureKind=$failureKind " +
                    "message=${error.message}",
                error,
            )
            if (failureKind == CreateFailureKind.APP_TIMEOUT || failureKind == CreateFailureKind.PROVIDER_TIMEOUT) {
                logDebug(
                    "Resetting Boltz session after timeout requestKey=${activeDraft.requestKey} attempt=$attempt",
                )
                resetSession()
            }
        }

        val failedDraft =
            activeDraft.copy(
                state = BoltzChainSwapDraftState.FAILED,
                updatedAt = nowMs(),
                lastError = lastError?.message,
            )
        saveDraft(failedDraft)
        logWarn(
            "Create failed requestKey=${failedDraft.requestKey} state=${failedDraft.state} " +
                "lastError=${failedDraft.lastError}",
            lastError,
        )
        throw (lastError ?: IllegalStateException("Boltz chain swap creation failed"))
    }

    fun classifyCreateFailure(error: Throwable): CreateFailureKind {
        if (error is TimeoutCancellationException) {
            return CreateFailureKind.APP_TIMEOUT
        }
        val message = error.message.orEmpty()
        return if (message.contains("Timeout(", ignoreCase = true)) {
            CreateFailureKind.PROVIDER_TIMEOUT
        } else {
            CreateFailureKind.OTHER
        }
    }

    fun markReviewReady(
        draft: BoltzChainSwapDraft,
        fundingPreview: BoltzChainFundingPreview?,
        reviewExpiresAt: Long,
    ): BoltzChainSwapDraft {
        logDebug(
            "Mark review ready requestKey=${draft.requestKey} swapId=${draft.swapId} fee=${fundingPreview?.feeSats} " +
                "vbytes=${fundingPreview?.txVBytes} reviewExpiresAt=$reviewExpiresAt",
        )
        return draft.copy(
            state = BoltzChainSwapDraftState.REVIEW_READY,
            fundingPreview = fundingPreview,
            reviewExpiresAt = reviewExpiresAt,
            updatedAt = nowMs(),
            lastError = null,
        )
    }

    fun markFundingBroadcast(
        draft: BoltzChainSwapDraft,
        fundingTxid: String,
    ): BoltzChainSwapDraft {
        logDebug(
            "Mark funding broadcast requestKey=${draft.requestKey} swapId=${draft.swapId} fundingTxid=${summarizeValue(fundingTxid)}",
        )
        return draft.copy(
            state = BoltzChainSwapDraftState.FUNDING_BROADCAST,
            fundingTxid = fundingTxid,
            updatedAt = nowMs(),
            lastError = null,
        )
    }

    fun markInProgress(
        draft: BoltzChainSwapDraft,
        fundingTxid: String? = draft.fundingTxid,
    ): BoltzChainSwapDraft {
        logDebug(
            "Mark in progress requestKey=${draft.requestKey} swapId=${draft.swapId} fundingTxid=${summarizeValue(fundingTxid)}",
        )
        return draft.copy(
            state = BoltzChainSwapDraftState.IN_PROGRESS,
            fundingTxid = fundingTxid,
            updatedAt = nowMs(),
            lastError = null,
        )
    }

    fun markCompleted(
        draft: BoltzChainSwapDraft,
        settlementTxid: String?,
    ): BoltzChainSwapDraft {
        logDebug(
            "Mark completed requestKey=${draft.requestKey} swapId=${draft.swapId} " +
                "settlementTxid=${summarizeValue(settlementTxid)}",
        )
        return draft.copy(
            state = BoltzChainSwapDraftState.COMPLETED,
            settlementTxid = settlementTxid,
            updatedAt = nowMs(),
        )
    }

    fun markFailed(
        draft: BoltzChainSwapDraft,
        error: String?,
        refundable: Boolean = false,
    ): BoltzChainSwapDraft {
        logWarn(
            "Mark failed requestKey=${draft.requestKey} swapId=${draft.swapId} refundable=$refundable error=$error",
            null,
        )
        return draft.copy(
            state = if (refundable) BoltzChainSwapDraftState.REFUNDABLE else BoltzChainSwapDraftState.FAILED,
            updatedAt = nowMs(),
            lastError = error,
        )
    }

    fun toPendingSwapSession(draft: BoltzChainSwapDraft): PendingSwapSession {
        logDebug(
            "Convert draft to pending session requestKey=${draft.requestKey} swapId=${draft.swapId} state=${draft.state}",
        )
        return PendingSwapSession(
            service = SwapService.BOLTZ,
            direction = draft.direction,
            sendAmount = draft.sendAmount,
            requestKey = draft.requestKey,
            usesMaxAmount = draft.usesMaxAmount,
            selectedFundingOutpoints = draft.selectedFundingOutpoints,
            estimatedTerms = draft.estimatedTerms,
            swapId = draft.swapId ?: draft.draftId,
            depositAddress = draft.depositAddress.orEmpty(),
            receiveAddress = draft.receiveAddress,
            refundAddress = draft.refundAddress,
            createdAt = draft.createdAt,
            reviewExpiresAt = draft.reviewExpiresAt,
            phase =
                when (draft.state) {
                    BoltzChainSwapDraftState.REVIEW_READY,
                    BoltzChainSwapDraftState.CREATED_UNREVIEWED,
                    -> PendingSwapPhase.REVIEW
                    BoltzChainSwapDraftState.CREATING,
                    BoltzChainSwapDraftState.FUNDING_BROADCAST,
                    BoltzChainSwapDraftState.IN_PROGRESS,
                    BoltzChainSwapDraftState.COMPLETED,
                    BoltzChainSwapDraftState.REFUNDABLE,
                    -> PendingSwapPhase.IN_PROGRESS
                    BoltzChainSwapDraftState.FAILED -> PendingSwapPhase.FAILED
                },
            status = draft.lastError ?: "",
            fundingLiquidFeeRate = draft.fundingLiquidFeeRate,
            boltzOrderAmountSats = draft.orderAmountSats,
            boltzVerifiedRecipientAmountSats = draft.fundingPreview?.verifiedRecipientAmountSats,
            boltzMaxOrderAmountVerified = draft.maxOrderAmountVerified,
            actualFundingFeeSats = draft.fundingPreview?.feeSats,
            actualFundingTxVBytes = draft.fundingPreview?.txVBytes,
            fundingTxid = draft.fundingTxid,
            settlementTxid = draft.settlementTxid,
            boltzSnapshot = draft.snapshot,
        )
    }

    private fun requireValidOrder(order: BoltzChainSwapOrder) {
        require(!order.swapId.isBlank()) { "Boltz returned an empty swap ID" }
        require(!order.lockupAddress.isBlank()) { "Boltz returned an empty lockup address" }
        require(!order.receiveAddress.isBlank()) { "Boltz returned an empty receive address" }
        require(!order.snapshot.isBlank()) { "Boltz returned an empty swap snapshot" }
    }

    private fun summarizeValue(value: String?): String {
        if (value.isNullOrBlank()) return "null"
        return if (value.length <= 12) value else "${value.take(6)}...${value.takeLast(4)}"
    }

    private fun logDebug(message: String) {
        if (BuildConfig.DEBUG) {
            runCatching { Log.d(TAG, message) }
        }
    }

    private fun logWarn(message: String, error: Throwable?) {
        if (BuildConfig.DEBUG) {
            runCatching { Log.w(TAG, message, error) }
        } else {
            runCatching { Log.w(TAG, message) }
        }
    }

    private companion object {
        private const val TAG = "BoltzDebug"
    }
}
