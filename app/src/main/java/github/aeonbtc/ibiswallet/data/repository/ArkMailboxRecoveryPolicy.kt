package github.aeonbtc.ibiswallet.data.repository

/**
 * Bark only runs the seed-mailbox scan on the [Wallet.open] that **creates** the local DB.
 * A cancelled first open can leave `db.sqlite` without imported VTXOs; reopening that file
 * with `skipRecovery=true` never scans again. ASP hydrate cannot invent those VTXOs.
 *
 * [SCANNED_MARKER_NAME] is written only after a report is present and recovered VTXOs
 * are visible (or the scan recovered none). A reusable DB without a marker and without
 * cached funds is treated as a skeleton.
 */
object ArkMailboxRecoveryPolicy {
    const val SCANNED_MARKER_NAME = "ibis-mailbox-scanned"

    fun canSkipMailboxRecovery(
        hasReusableDb: Boolean,
        hasScannedMarker: Boolean,
        cachedHasFunds: Boolean,
        forceMailbox: Boolean,
    ): Boolean =
        hasReusableDb &&
            !forceMailbox &&
            (hasScannedMarker || cachedHasFunds)

    fun shouldWipeForMailboxRescan(
        hasReusableDb: Boolean,
        hasScannedMarker: Boolean,
        cachedHasFunds: Boolean,
    ): Boolean =
        hasReusableDb && !hasScannedMarker && !cachedHasFunds

    fun isSuccessfulMailboxReport(
        reportPresent: Boolean,
        isComplete: Boolean,
        scanWasExpected: Boolean,
    ): Boolean =
        if (!reportPresent) {
            !scanWasExpected
        } else {
            isComplete
        }

    fun recoveredButNotApplied(
        recoveredCount: Int,
        liveSpendableSats: Long,
        liveVtxoCount: Int,
    ): Boolean = recoveredCount > 0 && liveSpendableSats <= 0L && liveVtxoCount <= 0

    fun shouldMarkMailboxScanned(
        reportPresent: Boolean,
        recoveredCount: Int,
        liveSpendableSats: Long,
        liveVtxoCount: Int,
    ): Boolean {
        if (!reportPresent) return false
        if (recoveredCount <= 0) return true
        return liveSpendableSats > 0L || liveVtxoCount > 0
    }
}
