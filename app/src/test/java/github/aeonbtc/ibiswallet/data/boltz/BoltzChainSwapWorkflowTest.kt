package github.aeonbtc.ibiswallet.data.boltz

import github.aeonbtc.ibiswallet.data.model.BoltzChainSwapDraft
import github.aeonbtc.ibiswallet.data.model.BoltzChainSwapDraftState
import github.aeonbtc.ibiswallet.data.model.BoltzChainSwapOrder
import github.aeonbtc.ibiswallet.data.model.EstimatedSwapTerms
import github.aeonbtc.ibiswallet.data.model.SwapDirection
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class BoltzChainSwapWorkflowTest : FunSpec({

    test("reuses an unexpired review-ready draft before creating a new order") {
        val workflow = BoltzChainSwapWorkflow(nowMs = { 1_000L })
        val existingDraft = sampleDraft(
            state = BoltzChainSwapDraftState.REVIEW_READY,
            reviewExpiresAt = 5_000L,
        )
        var createCalls = 0

        val result =
            workflow.createOrRecoverDraft(
                existingDraft = existingDraft,
                creatingDraft = existingDraft.copy(state = BoltzChainSwapDraftState.CREATING),
                createOrder = {
                    createCalls += 1
                    sampleOrder()
                },
                saveDraft = {},
                resetSession = {},
                classifyFailure = workflow::classifyCreateFailure,
            )

        result.usedExistingDraft shouldBe true
        result.draft shouldBe existingDraft
        createCalls shouldBe 0
    }

    test("does not retry a timeout automatically") {
        val workflow = BoltzChainSwapWorkflow()
        var createCalls = 0
        var resetCalls = 0

        shouldThrow<Exception> {
            workflow.createOrRecoverDraft(
                existingDraft = null,
                creatingDraft = sampleDraft(),
                createOrder = {
                    createCalls += 1
                    throw Exception("msg=Timeout(\"first\")")
                },
                saveDraft = {},
                resetSession = { resetCalls += 1 },
                classifyFailure = workflow::classifyCreateFailure,
            )
        }

        createCalls shouldBe 1
        resetCalls shouldBe 1
    }

    test("does not reset session for non-timeout failures") {
        val workflow = BoltzChainSwapWorkflow()
        var createCalls = 0
        var resetCalls = 0

        shouldThrow<Exception> {
            workflow.createOrRecoverDraft(
                existingDraft = null,
                creatingDraft = sampleDraft(),
                createOrder = {
                    createCalls += 1
                    throw Exception("provider exploded")
                },
                saveDraft = {},
                resetSession = { resetCalls += 1 },
                classifyFailure = workflow::classifyCreateFailure,
            )
        }

        createCalls shouldBe 1
        resetCalls shouldBe 0
    }
})

private fun sampleDraft(
    state: BoltzChainSwapDraftState = BoltzChainSwapDraftState.CREATING,
    reviewExpiresAt: Long = 0L,
): BoltzChainSwapDraft {
    return BoltzChainSwapDraft(
        draftId = "draft",
        requestKey = "request",
        direction = SwapDirection.LBTC_TO_BTC,
        sendAmount = 30_000L,
        estimatedTerms = sampleTerms(),
        fundingLiquidFeeRate = 0.1,
        bitcoinAddress = "bc1qexample",
        swapId = if (state == BoltzChainSwapDraftState.CREATING) null else "swap-id",
        depositAddress = if (state == BoltzChainSwapDraftState.CREATING) null else "lq1lockup",
        receiveAddress = "bc1qdest",
        refundAddress = "lq1refund",
        state = state,
        reviewExpiresAt = reviewExpiresAt,
        snapshot = if (state == BoltzChainSwapDraftState.CREATING) null else "snapshot",
    )
}

private fun sampleOrder(swapId: String = "swap-id"): BoltzChainSwapOrder {
    return BoltzChainSwapOrder(
        swapId = swapId,
        lockupAddress = "lq1lockup",
        receiveAddress = "bc1qdest",
        refundAddress = "lq1refund",
        snapshot = "snapshot",
    )
}

private fun sampleTerms(): EstimatedSwapTerms {
    return EstimatedSwapTerms(
        receiveAmount = 29_000L,
        serviceFee = 100L,
        btcNetworkFee = 200L,
        liquidNetworkFee = 300L,
        estimatedTime = "~20 min",
    )
}
