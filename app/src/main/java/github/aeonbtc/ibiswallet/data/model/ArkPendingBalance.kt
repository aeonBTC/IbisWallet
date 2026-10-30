package github.aeonbtc.ibiswallet.data.model

/** What the Ark balance card's "pending" line is waiting on. */
enum class ArkPendingKind {
    REFRESH,
    BOARDING,
    EXIT,

    /** Sats held by an in-flight arkoor send (bark 0.8.0 `pendingArkoorSendSats`). */
    SEND,

    /** Sats held by an in-flight offboard before its tx is on the network. */
    OFFBOARD,

    /** More than one bucket is non-zero: show a neutral label. */
    MIXED,
}

data class ArkPendingBalance(
    val totalSats: Long,
    val kind: ArkPendingKind,
)

/**
 * Owned-but-not-spendable sats shown under the Ark balance. Refresh (VTXOs needing
 * refresh or locked in a round), boarding, exit, in-flight arkoor send and
 * in-flight offboard are labelled separately so one flow never reads as another.
 */
fun arkPendingBalanceSummary(state: ArkWalletState): ArkPendingBalance {
    val refresh = state.needsRefreshSats.coerceAtLeast(0L) + state.pendingInRoundSats.coerceAtLeast(0L)
    val boarding = state.pendingBoardSats.coerceAtLeast(0L)
    val exit = state.pendingExitSats.coerceAtLeast(0L)
    val send = state.pendingArkoorSendSats.coerceAtLeast(0L)
    val offboard = state.pendingOffboardSats.coerceAtLeast(0L)
    val kind =
        when {
            refresh > 0L && boarding == 0L && exit == 0L && send == 0L && offboard == 0L ->
                ArkPendingKind.REFRESH
            boarding > 0L && refresh == 0L && exit == 0L && send == 0L && offboard == 0L ->
                ArkPendingKind.BOARDING
            exit > 0L && refresh == 0L && boarding == 0L && send == 0L && offboard == 0L ->
                ArkPendingKind.EXIT
            send > 0L && refresh == 0L && boarding == 0L && exit == 0L && offboard == 0L ->
                ArkPendingKind.SEND
            offboard > 0L && refresh == 0L && boarding == 0L && exit == 0L && send == 0L ->
                ArkPendingKind.OFFBOARD
            else -> ArkPendingKind.MIXED
        }
    return ArkPendingBalance(totalSats = refresh + boarding + exit + send + offboard, kind = kind)
}
