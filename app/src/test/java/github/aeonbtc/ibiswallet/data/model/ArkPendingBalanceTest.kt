package github.aeonbtc.ibiswallet.data.model

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class ArkPendingBalanceTest : FunSpec({

    test("pending board only is labelled boarding") {
        val summary = arkPendingBalanceSummary(ArkWalletState(pendingBoardSats = 57_611L))
        summary.kind shouldBe ArkPendingKind.BOARDING
        summary.totalSats shouldBe 57_611L
    }

    test("needs-refresh only is labelled refresh") {
        arkPendingBalanceSummary(ArkWalletState(needsRefreshSats = 1_000L)).kind shouldBe ArkPendingKind.REFRESH
    }

    test("in-round only is labelled refresh") {
        arkPendingBalanceSummary(ArkWalletState(pendingInRoundSats = 1_000L)).kind shouldBe ArkPendingKind.REFRESH
    }

    test("pending exit only is labelled exit") {
        arkPendingBalanceSummary(ArkWalletState(pendingExitSats = 1_000L)).kind shouldBe ArkPendingKind.EXIT
    }

    test("in-flight arkoor send is labelled send") {
        val summary = arkPendingBalanceSummary(ArkWalletState(pendingArkoorSendSats = 1_000L))
        summary.kind shouldBe ArkPendingKind.SEND
        summary.totalSats shouldBe 1_000L
    }

    test("in-flight offboard is labelled offboard") {
        val summary = arkPendingBalanceSummary(ArkWalletState(pendingOffboardSats = 1_000L))
        summary.kind shouldBe ArkPendingKind.OFFBOARD
        summary.totalSats shouldBe 1_000L
    }

    test("multiple buckets use the neutral label and sum") {
        val summary = arkPendingBalanceSummary(ArkWalletState(pendingBoardSats = 400L, needsRefreshSats = 600L))
        summary.kind shouldBe ArkPendingKind.MIXED
        summary.totalSats shouldBe 1_000L
    }

    test("send and offboard buckets are both part of the pending total") {
        val summary =
            arkPendingBalanceSummary(
                ArkWalletState(pendingArkoorSendSats = 300L, pendingOffboardSats = 700L),
            )
        summary.kind shouldBe ArkPendingKind.MIXED
        summary.totalSats shouldBe 1_000L
    }

    test("nothing pending totals zero") {
        arkPendingBalanceSummary(ArkWalletState()).totalSats shouldBe 0L
    }
})
