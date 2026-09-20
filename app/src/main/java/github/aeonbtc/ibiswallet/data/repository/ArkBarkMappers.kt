package github.aeonbtc.ibiswallet.data.repository

import uniffi.bark.ExitState
import uniffi.bark.ExitTxStatus
import uniffi.bark.VtxoState

/**
 * Maps bark-ffi 0.20+ typed VTXO/exit surfaces onto the stable string labels
 * used by [ArkVtxo]/[ArkExitVtxo]/[ArkExitProgress] and existing UI scrapers.
 */
internal object ArkBarkMappers {

    const val VTXO_SPENDABLE = "Spendable"
    const val VTXO_LOCKED = "Locked"
    const val VTXO_SPENT = "Spent"
    const val VTXO_EXITED = "Exited"

    const val EXIT_START = "Start"
    const val EXIT_PROCESSING = "Processing"
    const val EXIT_AWAITING_DELTA = "AwaitingDelta"
    const val EXIT_CLAIMABLE = "Claimable"
    const val EXIT_CLAIM_IN_PROGRESS = "ClaimInProgress"
    const val EXIT_CLAIMED = "Claimed"
    const val EXIT_VTXO_ALREADY_SPENT = "VtxoAlreadySpent"
    const val EXIT_CANCELED = "Canceled"
    /** Nested under Processing when CPFP child cannot be funded. */
    const val EXIT_AWAITING_CPFP = "AwaitingCpfpBroadcast"

    fun vtxoStateLabel(state: VtxoState): String =
        when (state) {
            is VtxoState.Spendable -> VTXO_SPENDABLE
            is VtxoState.Locked -> VTXO_LOCKED
            is VtxoState.Spent -> VTXO_SPENT
            is VtxoState.Exited -> VTXO_EXITED
        }

    fun isSpendableLabel(state: String): Boolean =
        state.equals(VTXO_SPENDABLE, ignoreCase = true)

    /**
     * Live for balance/drift/scan decisions: Spendable plus Locked (in-round /
     * in-exit). Anything else (Spent, Exited) is mailbox-settled history and must
     * never justify trusting local state over the mailbox.
     */
    fun isLiveVtxoLabel(state: String): Boolean =
        state.equals(VTXO_SPENDABLE, ignoreCase = true) ||
            state.equals(VTXO_LOCKED, ignoreCase = true)

    fun exitStateLabel(state: ExitState): String =
        when (state) {
            is ExitState.Start -> EXIT_START
            is ExitState.Processing ->
                if (processingNeedsCpfp(state)) {
                    "$EXIT_PROCESSING($EXIT_AWAITING_CPFP)"
                } else {
                    EXIT_PROCESSING
                }
            is ExitState.AwaitingDelta -> EXIT_AWAITING_DELTA
            is ExitState.Claimable -> EXIT_CLAIMABLE
            is ExitState.ClaimInProgress -> EXIT_CLAIM_IN_PROGRESS
            is ExitState.Claimed -> EXIT_CLAIMED
            is ExitState.VtxoAlreadySpent -> EXIT_VTXO_ALREADY_SPENT
            is ExitState.Canceled -> EXIT_CANCELED
        }

    fun isClaimed(state: ExitState): Boolean = state is ExitState.Claimed

    fun isClaimedLabel(state: String): Boolean =
        state.equals(EXIT_CLAIMED, ignoreCase = true) ||
            state.contains(EXIT_CLAIMED, ignoreCase = true)

    fun needsCpfpFunding(state: ExitState): Boolean =
        state is ExitState.Processing && processingNeedsCpfp(state)

    fun needsPush(state: ExitState): Boolean =
        when (state) {
            is ExitState.Start -> true
            is ExitState.Processing -> true
            else -> false
        }

    fun needsPushLabel(state: String): Boolean =
        state.contains(EXIT_START, ignoreCase = true) ||
            state.contains("verify", ignoreCase = true) ||
            state.contains(EXIT_AWAITING_CPFP, ignoreCase = true) ||
            state.contains("awaiting-cpfp-broadcast", ignoreCase = true) ||
            state.equals(EXIT_PROCESSING, ignoreCase = true) ||
            state.startsWith("$EXIT_PROCESSING(", ignoreCase = true)

    fun isCanceled(state: ExitState): Boolean = state is ExitState.Canceled

    fun isCanceledLabel(state: String): Boolean =
        state.equals(EXIT_CANCELED, ignoreCase = true) ||
            state.contains(EXIT_CANCELED, ignoreCase = true)

    fun isAlreadySpentLabel(state: String): Boolean =
        state.equals(EXIT_VTXO_ALREADY_SPENT, ignoreCase = true) ||
            state.contains(EXIT_VTXO_ALREADY_SPENT, ignoreCase = true)

    /** Claimed, canceled, or already spent — not an in-flight unilateral exit. */
    fun isTerminalExitLabel(state: String): Boolean =
        isClaimedLabel(state) || isCanceledLabel(state) || isAlreadySpentLabel(state)

    fun canCancelLabel(state: String): Boolean =
        !isCanceledLabel(state) &&
            !isClaimedLabel(state) &&
            !state.contains(EXIT_CLAIMABLE, ignoreCase = true) &&
            !state.contains(EXIT_AWAITING_DELTA, ignoreCase = true) &&
            !state.contains(EXIT_CLAIM_IN_PROGRESS, ignoreCase = true) &&
            (
                state.equals(EXIT_START, ignoreCase = true) ||
                    state.equals(EXIT_PROCESSING, ignoreCase = true) ||
                    state.startsWith("$EXIT_PROCESSING(", ignoreCase = true)
                )

    private fun processingNeedsCpfp(state: ExitState.Processing): Boolean =
        state.transactions.any { it.status is ExitTxStatus.AwaitingCpfpBroadcast }
}
