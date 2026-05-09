package github.aeonbtc.ibiswallet.util

/**
 * Finds the largest exact recipient amount that still fits within the wallet's
 * spendable budget once fees are paid on top.
 */
suspend fun findMaxExactSendAmount(
    upperBoundSats: Long,
    canSendAmount: suspend (Long) -> Boolean,
): Long {
    if (upperBoundSats <= 0L) return 0L

    var low = 1L
    var high = upperBoundSats
    var best = 0L

    while (low <= high) {
        val mid = low + ((high - low) / 2)
        if (canSendAmount(mid)) {
            best = mid
            low = mid + 1
        } else {
            high = mid - 1
        }
    }

    return best
}

/**
 * Finds the smallest funding amount whose net delivered amount meets the target.
 *
 * Useful for provider flows where service or payout fees are deducted from the
 * funded amount, but the app must still enforce a minimum delivered amount.
 */
fun findMinimumFundingAmount(
    targetNetAmountSats: Long,
    startingFundingAmountSats: Long,
    maxFundingAmountSats: Long = 0L,
    netAmountAtFunding: (Long) -> Long,
): Long {
    if (targetNetAmountSats <= 0L) return 0L

    val start = maxOf(1L, startingFundingAmountSats)
    if (netAmountAtFunding(start) >= targetNetAmountSats) {
        return start
    }

    val hardMax = if (maxFundingAmountSats > 0L) maxFundingAmountSats else Long.MAX_VALUE
    var low = start + 1L
    var high = start

    while (high < hardMax && netAmountAtFunding(high) < targetNetAmountSats) {
        high =
            if (high > hardMax / 2L) {
                hardMax
            } else {
                (high * 2L).coerceAtMost(hardMax)
            }
    }

    if (netAmountAtFunding(high) < targetNetAmountSats) {
        return high
    }

    var best = high
    while (low <= high) {
        val mid = low + ((high - low) / 2L)
        if (netAmountAtFunding(mid) >= targetNetAmountSats) {
            best = mid
            high = mid - 1L
        } else {
            low = mid + 1L
        }
    }

    return best
}
