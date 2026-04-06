package github.aeonbtc.ibiswallet.data.boltz

import github.aeonbtc.ibiswallet.data.model.SwapDirection

data class BoltzMaxReviewState(
    val reviewAmount: Long,
    val orderAmount: Long,
    val verifiedAmount: Long?,
    val pendingAmount: Long? = null,
    val orderVerified: Boolean,
)

data class ResolvedBoltzMaxReviewMetadata(
    val orderAmount: Long?,
    val verifiedAmount: Long?,
    val orderVerified: Boolean,
)

fun shouldPreResolveBoltzMaxAmount(
    direction: SwapDirection,
    usesMaxAmount: Boolean,
): Boolean = BullBoltzBehavior.shouldPreResolveMaxAmount(direction, usesMaxAmount)

fun resolveBoltzReviewFundingPreviewIsMaxSend(
    usesMaxAmount: Boolean,
): Boolean = usesMaxAmount

fun resolveBoltzMaxReviewMetadata(
    pendingOrderAmount: Long?,
    pendingVerifiedAmount: Long?,
    pendingOrderVerified: Boolean,
    draftOrderAmount: Long?,
    draftVerifiedAmount: Long?,
    draftOrderVerified: Boolean,
): ResolvedBoltzMaxReviewMetadata {
    return if (
        draftOrderAmount != null ||
        draftVerifiedAmount != null ||
        draftOrderVerified
    ) {
        ResolvedBoltzMaxReviewMetadata(
            orderAmount = draftOrderAmount,
            verifiedAmount = draftVerifiedAmount,
            orderVerified = draftOrderVerified,
        )
    } else {
        ResolvedBoltzMaxReviewMetadata(
            orderAmount = pendingOrderAmount,
            verifiedAmount = pendingVerifiedAmount,
            orderVerified = pendingOrderVerified,
        )
    }
}

fun shouldRecreateBoltzMaxOrder(
    direction: SwapDirection,
    usesMaxAmount: Boolean,
    quotedAmount: Long,
    verifiedAmount: Long?,
): Boolean {
    return usesMaxAmount &&
        (direction == SwapDirection.LBTC_TO_BTC || direction == SwapDirection.BTC_TO_LBTC) &&
        verifiedAmount != null &&
        verifiedAmount > 0L &&
        verifiedAmount != quotedAmount
}

fun isBoltzMaxFundingShortfallError(
    direction: SwapDirection,
    error: Throwable,
): Boolean {
    val texts =
        generateSequence(error) { current -> current.cause }
            .flatMap { throwable ->
                sequenceOf(
                    throwable.message,
                    throwable.toString(),
                )
            }
            .filterNotNull()
            .toList()
    if (texts.isEmpty()) {
        return false
    }

    fun matches(vararg needles: String): Boolean {
        return texts.any { text ->
            needles.any { needle ->
                text.contains(needle, ignoreCase = true)
            }
        }
    }

    return when (direction) {
        SwapDirection.BTC_TO_LBTC -> matches("Insufficient funds", "missing_sats")
        SwapDirection.LBTC_TO_BTC -> matches("InsufficientFunds", "missing_sats", "Insufficient L-BTC")
    }
}

fun hasBoltzMaxReviewMismatch(
    usesMaxAmount: Boolean,
    state: BoltzMaxReviewState,
): Boolean {
    if (!usesMaxAmount) {
        return false
    }
    return !state.orderVerified ||
        state.orderAmount != state.reviewAmount ||
        (state.verifiedAmount != null && state.verifiedAmount != state.reviewAmount) ||
        (state.pendingAmount != null && state.pendingAmount != state.reviewAmount)
}
