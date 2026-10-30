package github.aeonbtc.ibiswallet.data.repository

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import uniffi.bark.RecoveryBucket
import uniffi.bark.RecoveryReport

class ArkMailboxRecoveryPolicyTest : FunSpec({

    test("skip recovery when reusable DB has a mailbox scan marker") {
        ArkMailboxRecoveryPolicy.canSkipMailboxRecovery(
            hasReusableDb = true,
            hasScannedMarker = true,
            cachedHasFunds = false,
            forceMailbox = false,
        ) shouldBe true
    }

    test("skip recovery when reusable DB already has cached funds") {
        ArkMailboxRecoveryPolicy.canSkipMailboxRecovery(
            hasReusableDb = true,
            hasScannedMarker = false,
            cachedHasFunds = true,
            forceMailbox = false,
        ) shouldBe true
    }

    test("skeleton DB without marker or funds cannot skip mailbox") {
        ArkMailboxRecoveryPolicy.canSkipMailboxRecovery(
            hasReusableDb = true,
            hasScannedMarker = false,
            cachedHasFunds = false,
            forceMailbox = false,
        ) shouldBe false
    }

    test("force mailbox never skips") {
        ArkMailboxRecoveryPolicy.canSkipMailboxRecovery(
            hasReusableDb = true,
            hasScannedMarker = true,
            cachedHasFunds = false,
            forceMailbox = true,
        ) shouldBe false
    }

    test("unmarked empty reusable DB is wiped for mailbox rescan") {
        ArkMailboxRecoveryPolicy.shouldWipeForMailboxRescan(
            hasReusableDb = true,
            hasScannedMarker = false,
            cachedHasFunds = false,
        ) shouldBe true
    }

    test("funded or marked DB is kept") {
        ArkMailboxRecoveryPolicy.shouldWipeForMailboxRescan(
            hasReusableDb = true,
            hasScannedMarker = false,
            cachedHasFunds = true,
        ) shouldBe false
        ArkMailboxRecoveryPolicy.shouldWipeForMailboxRescan(
            hasReusableDb = true,
            hasScannedMarker = true,
            cachedHasFunds = false,
        ) shouldBe false
    }

    test("null report is success only when a scan was not expected") {
        ArkMailboxRecoveryPolicy.isSuccessfulMailboxReport(
            reportPresent = false,
            isComplete = false,
            scanWasExpected = false,
        ) shouldBe true
    }

    test("null report is failure when a mailbox scan was expected") {
        ArkMailboxRecoveryPolicy.isSuccessfulMailboxReport(
            reportPresent = false,
            isComplete = false,
            scanWasExpected = true,
        ) shouldBe false
    }

    test("incomplete report is not success") {
        ArkMailboxRecoveryPolicy.isSuccessfulMailboxReport(
            reportPresent = true,
            isComplete = false,
            scanWasExpected = true,
        ) shouldBe false
    }

    test("failed scan is never success, even when no scan was expected") {
        ArkMailboxRecoveryPolicy.isSuccessfulMailboxReport(
            reportPresent = false,
            isComplete = false,
            scanWasExpected = false,
            scanFailed = true,
        ) shouldBe false
    }

    test("failed scan with a stale report is failure") {
        ArkMailboxRecoveryPolicy.isSuccessfulMailboxReport(
            reportPresent = true,
            isComplete = true,
            scanWasExpected = true,
            scanFailed = true,
        ) shouldBe false
    }

    test("complete report without scan failure is success") {
        ArkMailboxRecoveryPolicy.isSuccessfulMailboxReport(
            reportPresent = true,
            isComplete = true,
            scanWasExpected = true,
            scanFailed = false,
        ) shouldBe true
    }

    test("cross-device drift rescan fires on unexplained vanish with balance drop") {
        ArkMailboxRecoveryPolicy.shouldRescanForCrossDeviceDrift(
            cachedSpendableIds = listOf("old-1", "old-2"),
            cachedSpendableSats = 40_000L,
            liveSpendableIds = emptyList(),
            liveSpendableSats = 0L,
            activeExitIds = emptyList(),
            claimableIds = emptyList(),
            hasNewOutgoingMovements = false,
            hasInflightActivity = false,
        ) shouldBe true
    }

    test("drift rescan vetoed by in-flight activity") {
        ArkMailboxRecoveryPolicy.shouldRescanForCrossDeviceDrift(
            cachedSpendableIds = listOf("old-1"),
            cachedSpendableSats = 40_000L,
            liveSpendableIds = emptyList(),
            liveSpendableSats = 0L,
            activeExitIds = emptyList(),
            claimableIds = emptyList(),
            hasNewOutgoingMovements = false,
            hasInflightActivity = true,
        ) shouldBe false
    }

    test("drift rescan vetoed by new outgoing movement (same-device drain)") {
        ArkMailboxRecoveryPolicy.shouldRescanForCrossDeviceDrift(
            cachedSpendableIds = listOf("old-1"),
            cachedSpendableSats = 40_000L,
            liveSpendableIds = emptyList(),
            liveSpendableSats = 0L,
            activeExitIds = emptyList(),
            claimableIds = emptyList(),
            hasNewOutgoingMovements = true,
            hasInflightActivity = false,
        ) shouldBe false
    }

    test("drift rescan vetoed when vanish is explained by exits and inputs") {
        ArkMailboxRecoveryPolicy.shouldRescanForCrossDeviceDrift(
            cachedSpendableIds = listOf("old-1", "old-2"),
            cachedSpendableSats = 40_000L,
            liveSpendableIds = listOf("new-1"),
            liveSpendableSats = 39_500L,
            activeExitIds = listOf("old-1"),
            claimableIds = emptyList(),
            locallyConsumedIds = listOf("old-2"),
            hasNewOutgoingMovements = false,
            hasInflightActivity = false,
        ) shouldBe false
    }

    test("drift rescan vetoed by fee-noise drop") {
        ArkMailboxRecoveryPolicy.shouldRescanForCrossDeviceDrift(
            cachedSpendableIds = listOf("old-1"),
            cachedSpendableSats = 40_000L,
            liveSpendableIds = listOf("new-1"),
            liveSpendableSats = 39_800L,
            activeExitIds = emptyList(),
            claimableIds = emptyList(),
            hasNewOutgoingMovements = false,
            hasInflightActivity = false,
        ) shouldBe false
    }

    test("drift rescan needs cached ids") {
        ArkMailboxRecoveryPolicy.shouldRescanForCrossDeviceDrift(
            cachedSpendableIds = emptyList(),
            cachedSpendableSats = 0L,
            liveSpendableIds = listOf("new-1"),
            liveSpendableSats = 10_000L,
            activeExitIds = emptyList(),
            claimableIds = emptyList(),
            hasNewOutgoingMovements = false,
            hasInflightActivity = false,
        ) shouldBe false
    }

    test("auto rescan needs streak, cooldown, and quiet wallet") {
        fun auto(
            streak: Int = 2,
            cooldown: Long = 3_600_000L,
            blocking: Boolean = false,
        ) = ArkMailboxRecoveryPolicy.shouldAutoRescanStaleSession(
            staleStreak = streak,
            cooldownElapsedMs = cooldown,
            hasBlockingActivity = blocking,
        )
        auto() shouldBe true
        auto(streak = 1) shouldBe false
        auto(cooldown = 3_599_999L) shouldBe false
        auto(blocking = true) shouldBe false
    }

    test("recovered ids with empty live state were not applied") {
        ArkMailboxRecoveryPolicy.recoveredButNotApplied(
            recoveredCount = 2,
            liveSpendableSats = 0L,
            liveVtxoCount = 0,
        ) shouldBe true
    }

    test("zero recovered with empty live state is not an apply failure") {
        ArkMailboxRecoveryPolicy.recoveredButNotApplied(
            recoveredCount = 0,
            liveSpendableSats = 0L,
            liveVtxoCount = 0,
        ) shouldBe false
    }

    test("mark after empty recovered report") {
        ArkMailboxRecoveryPolicy.shouldMarkMailboxScanned(
            reportPresent = true,
            recoveredCount = 0,
            liveSpendableSats = 0L,
            liveVtxoCount = 0,
        ) shouldBe true
    }

    test("do not mark recovered ids until they appear live") {
        ArkMailboxRecoveryPolicy.shouldMarkMailboxScanned(
            reportPresent = true,
            recoveredCount = 2,
            liveSpendableSats = 0L,
            liveVtxoCount = 0,
        ) shouldBe false
        ArkMailboxRecoveryPolicy.shouldMarkMailboxScanned(
            reportPresent = true,
            recoveredCount = 2,
            liveSpendableSats = 1_000L,
            liveVtxoCount = 0,
        ) shouldBe true
    }

    test("forced mailbox wipe recreates any reusable DB") {
        ArkMailboxRecoveryPolicy.shouldWipeForForcedMailboxRescan(hasReusableDb = true) shouldBe true
        ArkMailboxRecoveryPolicy.shouldWipeForForcedMailboxRescan(hasReusableDb = false) shouldBe false
    }

    test("movements-only cache is not recoverable funds") {
        ArkMailboxRecoveryPolicy.cachedHasRecoverableFunds(
            spendableSats = 0L,
            pendingInRoundSats = 0L,
            pendingBoardSats = 0L,
            pendingExitSats = 0L,
            claimableLightningReceiveSats = 0L,
            vtxoCount = 0,
        ) shouldBe false
    }

    test("live VTXOs or spendable count as recoverable funds") {
        ArkMailboxRecoveryPolicy.cachedHasRecoverableFunds(
            spendableSats = 1_000L,
            pendingInRoundSats = 0L,
            pendingBoardSats = 0L,
            pendingExitSats = 0L,
            claimableLightningReceiveSats = 0L,
            vtxoCount = 0,
        ) shouldBe true
        ArkMailboxRecoveryPolicy.cachedHasRecoverableFunds(
            spendableSats = 0L,
            pendingInRoundSats = 0L,
            pendingBoardSats = 0L,
            pendingExitSats = 0L,
            claimableLightningReceiveSats = 0L,
            vtxoCount = 1,
        ) shouldBe true
    }

    test("import marks mailbox scanned only when funded") {
        ArkMailboxRecoveryPolicy.shouldMarkImportedMailboxScanned(
            hasReusableDb = true,
            importedSpendableSats = 0L,
            importedHistoryHasFunds = false,
        ) shouldBe false
        ArkMailboxRecoveryPolicy.shouldMarkImportedMailboxScanned(
            hasReusableDb = true,
            importedSpendableSats = 500L,
            importedHistoryHasFunds = false,
        ) shouldBe true
        ArkMailboxRecoveryPolicy.shouldMarkImportedMailboxScanned(
            hasReusableDb = true,
            importedSpendableSats = 0L,
            importedHistoryHasFunds = true,
        ) shouldBe true
        ArkMailboxRecoveryPolicy.shouldMarkImportedMailboxScanned(
            hasReusableDb = false,
            importedSpendableSats = 500L,
            importedHistoryHasFunds = true,
        ) shouldBe false
    }

    test("plaintext legacy imports always force mailbox scan even when bound") {
        ArkMailboxRecoveryPolicy.shouldForceImportMailboxScan(
            manifestBound = true,
            wasEncrypted = false,
        ) shouldBe true
        ArkMailboxRecoveryPolicy.shouldForceImportMailboxScan(
            manifestBound = false,
            wasEncrypted = true,
        ) shouldBe true
        ArkMailboxRecoveryPolicy.shouldForceImportMailboxScan(
            manifestBound = true,
            wasEncrypted = true,
        ) shouldBe false
    }

    test("secure import mark requires encrypted bound funded DB") {
        ArkMailboxRecoveryPolicy.shouldMarkImportedMailboxScannedSecure(
            manifestBound = true,
            wasEncrypted = true,
            hasReusableDb = true,
            importedSpendableSats = 500L,
            importedHistoryHasFunds = false,
        ) shouldBe true
        ArkMailboxRecoveryPolicy.shouldMarkImportedMailboxScannedSecure(
            manifestBound = true,
            wasEncrypted = false,
            hasReusableDb = true,
            importedSpendableSats = 500L,
            importedHistoryHasFunds = false,
        ) shouldBe false
        ArkMailboxRecoveryPolicy.shouldMarkImportedMailboxScannedSecure(
            manifestBound = false,
            wasEncrypted = true,
            hasReusableDb = true,
            importedSpendableSats = 500L,
            importedHistoryHasFunds = false,
        ) shouldBe false
    }

    test("unfunded secure import still forces recovery after install") {
        ArkMailboxRecoveryPolicy.shouldForceImportMailboxScanAfterInstall(
            manifestBound = true,
            wasEncrypted = true,
            secureScanMark = false,
        ) shouldBe true
        ArkMailboxRecoveryPolicy.shouldForceImportMailboxScanAfterInstall(
            manifestBound = true,
            wasEncrypted = true,
            secureScanMark = true,
        ) shouldBe false
    }

    test("retry merge moves failed ids into recovered and preserves exited") {
        val original = recoveryReport(failed = listOf("a"), exited = listOf("x"))
        val retry = recoveryReport(recovered = listOf("a"))

        val merged = ArkMailboxRecoveryPolicy.mergeRecoveryReports(original, retry, listOf("a"))

        merged.recovered.vtxoIds shouldBe listOf("a")
        merged.failed.vtxoIds shouldBe emptyList()
        merged.exited.vtxoIds shouldBe listOf("x")
        merged.isComplete shouldBe true
    }

    test("retry merge keeps a foreign result incomplete") {
        val original = recoveryReport(failed = listOf("a"))
        val retry = recoveryReport(foreign = listOf("a"))

        val merged = ArkMailboxRecoveryPolicy.mergeRecoveryReports(original, retry, listOf("a"))

        merged.failed.vtxoIds shouldBe emptyList()
        merged.foreign.vtxoIds shouldBe listOf("a")
        merged.isComplete shouldBe false
    }

    test("retry merge keeps buckets for ids that were not retried") {
        val original =
            recoveryReport(
                failed = listOf("a", "b"),
                skipped = listOf("s"),
                exited = listOf("x"),
            )
        val retry = recoveryReport(recovered = listOf("a"))

        val merged = ArkMailboxRecoveryPolicy.mergeRecoveryReports(original, retry, listOf("a"))

        merged.recovered.vtxoIds shouldBe listOf("a")
        merged.failed.vtxoIds shouldBe listOf("b")
        merged.skipped.vtxoIds shouldBe listOf("s")
        merged.exited.vtxoIds shouldBe listOf("x")
        merged.isComplete shouldBe false
    }

    test("recovery gap limit covers recorded keys and caps at 1024") {
        ArkMailboxRecoveryPolicy.recoveryKeyGapLimit(-1) shouldBe 1024u
        ArkMailboxRecoveryPolicy.recoveryKeyGapLimit(0) shouldBe 250u
        ArkMailboxRecoveryPolicy.recoveryKeyGapLimit(300) shouldBe 308u
        ArkMailboxRecoveryPolicy.recoveryKeyGapLimit(10_000) shouldBe 1024u
    }

    test("sidecar never marks scanned after a failed scan or import") {
        ArkMailboxRecoveryPolicy.shouldMarkSidecarScanned(
            reportPresent = true,
            isComplete = true,
            scanFailed = true,
            importFailed = false,
            recoveredCount = 0,
            liveSpendableSats = 0L,
            liveVtxoCount = 0,
        ) shouldBe false
        ArkMailboxRecoveryPolicy.shouldMarkSidecarScanned(
            reportPresent = true,
            isComplete = true,
            scanFailed = false,
            importFailed = true,
            recoveredCount = 0,
            liveSpendableSats = 0L,
            liveVtxoCount = 0,
        ) shouldBe false
        ArkMailboxRecoveryPolicy.shouldMarkSidecarScanned(
            reportPresent = true,
            isComplete = false,
            scanFailed = false,
            importFailed = false,
            recoveredCount = 0,
            liveSpendableSats = 0L,
            liveVtxoCount = 0,
        ) shouldBe false
        ArkMailboxRecoveryPolicy.shouldMarkSidecarScanned(
            reportPresent = false,
            isComplete = true,
            scanFailed = false,
            importFailed = false,
            recoveredCount = 0,
            liveSpendableSats = 0L,
            liveVtxoCount = 0,
        ) shouldBe false
    }

    test("sidecar does not mark scanned when recovered funds did not appear") {
        ArkMailboxRecoveryPolicy.shouldMarkSidecarScanned(
            reportPresent = true,
            isComplete = true,
            scanFailed = false,
            importFailed = false,
            recoveredCount = 2,
            liveSpendableSats = 0L,
            liveVtxoCount = 0,
        ) shouldBe false
        ArkMailboxRecoveryPolicy.shouldMarkSidecarScanned(
            reportPresent = true,
            isComplete = true,
            scanFailed = false,
            importFailed = false,
            recoveredCount = 2,
            liveSpendableSats = 500L,
            liveVtxoCount = 1,
        ) shouldBe true
    }

    test("a complete sidecar scan with no recoveries marks the DB scanned") {
        ArkMailboxRecoveryPolicy.shouldMarkSidecarScanned(
            reportPresent = true,
            isComplete = true,
            scanFailed = false,
            importFailed = false,
            recoveredCount = 0,
            liveSpendableSats = 0L,
            liveVtxoCount = 0,
        ) shouldBe true
    }

    test("unmarked reusable DB with cached funds runs the sidecar once") {
        ArkMailboxRecoveryPolicy.shouldSidecarRescanUnmarkedDb(
            hasReusableDb = true,
            hasMarker = false,
            cachedHasFunds = true,
            hasBlockingActivity = false,
            alreadyAttempted = false,
        ) shouldBe true
        ArkMailboxRecoveryPolicy.shouldSidecarRescanUnmarkedDb(
            hasReusableDb = true,
            hasMarker = true,
            cachedHasFunds = true,
            hasBlockingActivity = false,
            alreadyAttempted = false,
        ) shouldBe false
        ArkMailboxRecoveryPolicy.shouldSidecarRescanUnmarkedDb(
            hasReusableDb = false,
            hasMarker = false,
            cachedHasFunds = true,
            hasBlockingActivity = false,
            alreadyAttempted = false,
        ) shouldBe false
        ArkMailboxRecoveryPolicy.shouldSidecarRescanUnmarkedDb(
            hasReusableDb = true,
            hasMarker = false,
            cachedHasFunds = true,
            hasBlockingActivity = true,
            alreadyAttempted = false,
        ) shouldBe false
        ArkMailboxRecoveryPolicy.shouldSidecarRescanUnmarkedDb(
            hasReusableDb = true,
            hasMarker = false,
            cachedHasFunds = true,
            hasBlockingActivity = false,
            alreadyAttempted = true,
        ) shouldBe false
        ArkMailboxRecoveryPolicy.shouldSidecarRescanUnmarkedDb(
            hasReusableDb = true,
            hasMarker = false,
            cachedHasFunds = false,
            hasBlockingActivity = false,
            alreadyAttempted = false,
        ) shouldBe false
    }
})

private fun recoveryReport(
    recovered: List<String> = emptyList(),
    skipped: List<String> = emptyList(),
    foreign: List<String> = emptyList(),
    failed: List<String> = emptyList(),
    exited: List<String> = emptyList(),
): RecoveryReport =
    RecoveryReport(
        recovered = RecoveryBucket(recovered, 0u),
        skipped = RecoveryBucket(skipped, 0u),
        foreign = RecoveryBucket(foreign, 0u),
        failed = RecoveryBucket(failed, 0u),
        exited = RecoveryBucket(exited, 0u),
        isComplete = failed.isEmpty() && foreign.isEmpty(),
    )
