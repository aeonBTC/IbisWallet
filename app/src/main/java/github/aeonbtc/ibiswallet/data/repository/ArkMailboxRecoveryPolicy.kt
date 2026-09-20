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

    /**
     * Minimum unexplained spendable-balance drop that justifies a cross-device
     * drift rescan. Well above refresh/board fee noise; a same-device drain
     * always leaves a negative movement, which vetoes instead.
     */
    const val CROSS_DEVICE_DRIFT_MIN_DROP_SATS = 5_000L

    /** Consecutive stale-input send failures that prove persistent divergence. */
    const val STALE_INPUT_RESCAN_STREAK = 2

    /** Minimum gap between automatic mailbox rescans (monotonic ms). */
    const val FORCED_RESCAN_COOLDOWN_MS = 3_600_000L

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

    /** Wallet Management / recovered-but-not-applied: always recreate the session DB. */
    fun shouldWipeForForcedMailboxRescan(hasReusableDb: Boolean): Boolean = hasReusableDb

    /**
     * Live VTXOs / balances only. Movement history lives in SecureStorage independently
     * of `db.sqlite` and must not prove the session already imported mailbox funds.
     */
    fun cachedHasRecoverableFunds(
        spendableSats: Long,
        pendingInRoundSats: Long,
        pendingBoardSats: Long,
        pendingExitSats: Long,
        claimableLightningReceiveSats: Long,
        vtxoCount: Int,
    ): Boolean =
        spendableSats > 0L ||
            pendingInRoundSats > 0L ||
            pendingBoardSats > 0L ||
            pendingExitSats > 0L ||
            claimableLightningReceiveSats > 0L ||
            vtxoCount > 0

    /**
     * Import may skip mailbox only when the zip has a real Bark DB and evidence of
     * funds (manifest spendable, or sidecar history with a positive net). Empty /
     * unfunded zips must open with a mailbox scan.
     */
    fun shouldMarkImportedMailboxScanned(
        hasReusableDb: Boolean,
        importedSpendableSats: Long,
        importedHistoryHasFunds: Boolean,
    ): Boolean =
        hasReusableDb && (importedSpendableSats > 0L || importedHistoryHasFunds)

    /**
     * Plaintext legacy zips always rescan even when fingerprint-bound: the
     * manifest fingerprint is a hint, not authentication. Only encrypted (GCM
     * auth passed) + bound imports may skip the scan. Unbound imports always
     * rescan.
     */
    fun shouldForceImportMailboxScan(
        manifestBound: Boolean,
        wasEncrypted: Boolean,
    ): Boolean = !manifestBound || !wasEncrypted

    fun shouldMarkImportedMailboxScannedSecure(
        manifestBound: Boolean,
        wasEncrypted: Boolean,
        hasReusableDb: Boolean,
        importedSpendableSats: Long,
        importedHistoryHasFunds: Boolean,
    ): Boolean =
        manifestBound &&
            wasEncrypted &&
            shouldMarkImportedMailboxScanned(
                hasReusableDb = hasReusableDb,
                importedSpendableSats = importedSpendableSats,
                importedHistoryHasFunds = importedHistoryHasFunds,
            )

    /**
     * A failed scan ([scanFailed]) is never success — Bark 0.7.0 reports it via
     * `Wallet.recoveryStatus()` separately from a scan that never ran, so funds
     * may be missing until a retry succeeds.
     */
    fun isSuccessfulMailboxReport(
        reportPresent: Boolean,
        isComplete: Boolean,
        scanWasExpected: Boolean,
        scanFailed: Boolean = false,
    ): Boolean {
        if (scanFailed) return false
        if (!reportPresent) {
            return !scanWasExpected
        } else {
            return isComplete
        }
    }

    fun recoveredButNotApplied(
        recoveredCount: Int,
        liveSpendableSats: Long,
        liveVtxoCount: Int,
        livePendingSats: Long = 0L,
        livePendingCount: Int = 0,
        liveClaimableCount: Int = 0,
    ): Boolean =
        recoveredCount > 0 &&
            liveSpendableSats <= 0L &&
            liveVtxoCount <= 0 &&
            livePendingSats <= 0L &&
            livePendingCount <= 0 &&
            liveClaimableCount <= 0

    fun shouldMarkMailboxScanned(
        reportPresent: Boolean,
        recoveredCount: Int,
        liveSpendableSats: Long,
        liveVtxoCount: Int,
        isComplete: Boolean = true,
    ): Boolean {
        if (!reportPresent) return false
        // An incomplete/failed scan must never mark the DB scanned, even when
        // it recovered nothing — otherwise future opens skip the rescan.
        if (!isComplete) return false
        if (recoveredCount <= 0) return true
        return liveSpendableSats > 0L || liveVtxoCount > 0
    }

    /**
     * True when previously-known spendable VTXOs vanished without any local
     * trace after a successful sync — the signature of another device spending
     * (e.g. refreshing) this seed's funds. The local DB can neither mark the
     * old outputs spent-with-replacement nor discover the new outputs, so the
     * caller should rescan the seed mailbox (forceMailbox reopen).
     *
     * Vetoes: in-flight activity (pending exits/claims/boards/refreshes/sends),
     * a new outgoing movement since the cache snapshot — a same-device drain
     * always leaves one — and drops within fee noise.
     */
    fun shouldRescanForCrossDeviceDrift(
        cachedSpendableIds: Collection<String>,
        cachedSpendableSats: Long,
        liveSpendableIds: Collection<String>,
        liveSpendableSats: Long,
        activeExitIds: Collection<String>,
        claimableIds: Collection<String>,
        locallyConsumedIds: Collection<String> = emptyList(),
        hasNewOutgoingMovements: Boolean = false,
        hasInflightActivity: Boolean = false,
        minDropSats: Long = CROSS_DEVICE_DRIFT_MIN_DROP_SATS,
    ): Boolean {
        if (hasInflightActivity) return false
        val cached =
            cachedSpendableIds.map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        if (cached.isEmpty()) return false
        val accounted =
            liveSpendableIds.map { it.trim() }.filter { it.isNotEmpty() }.toSet() +
                activeExitIds.map { it.trim() }.filter { it.isNotEmpty() } +
                claimableIds.map { it.trim() }.filter { it.isNotEmpty() } +
                locallyConsumedIds.map { it.trim() }.filter { it.isNotEmpty() }
        if ((cached - accounted).isEmpty()) return false
        if (cachedSpendableSats - liveSpendableSats <= minDropSats) return false
        if (hasNewOutgoingMovements) return false
        return true
    }

    /**
     * True when a mailbox rescan may run automatically: proof of persistent
     * divergence (repeated stale-input send failures the server keeps
     * rejecting), no cooldown active, and nothing in flight that a session
     * wipe could strand (pending exits/claims/boards/refreshes/sends).
     */
    fun shouldAutoRescanStaleSession(
        staleStreak: Int,
        cooldownElapsedMs: Long,
        hasBlockingActivity: Boolean,
        streakThreshold: Int = STALE_INPUT_RESCAN_STREAK,
        cooldownMs: Long = FORCED_RESCAN_COOLDOWN_MS,
    ): Boolean {
        if (hasBlockingActivity) return false
        if (staleStreak < streakThreshold) return false
        if (cooldownElapsedMs < cooldownMs) return false
        return true
    }
}
