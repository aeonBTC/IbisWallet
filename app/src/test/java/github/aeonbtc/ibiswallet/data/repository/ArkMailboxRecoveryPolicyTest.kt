package github.aeonbtc.ibiswallet.data.repository

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

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
})
