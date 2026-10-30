package github.aeonbtc.ibiswallet.data.repository

import github.aeonbtc.ibiswallet.data.model.ArkMovement
import github.aeonbtc.ibiswallet.data.model.TransactionDetails
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class ArkCenterSwapFundingTest : FunSpec({

    val fundingTxid = "a".repeat(64)
    val otherTxid = "b".repeat(64)

    fun pendingDeposit(onchainTxids: List<String>, boardTxid: String? = null) =
        ArkMovement(
            id = -3,
            status = "boarding",
            subsystemName = "Bitcoin",
            subsystemKind = "board",
            intendedBalanceSats = 57_611L,
            effectiveBalanceSats = 57_611L,
            offchainFeeSats = 0L,
            createdAt = "2026-10-27T14:28:00Z",
            updatedAt = "2026-10-27T14:28:00Z",
            onchainTxids = onchainTxids,
            boardTxid = boardTxid,
        )

    fun l1(txid: String, swap: Boolean) =
        TransactionDetails(
            txid = txid,
            amountSats = -57_800L,
            fee = null,
            confirmationTime = null,
            isConfirmed = false,
            timestamp = null,
            isSwapHistory = swap,
        )

    test("funding txid matching a center-swap L1 tx counts as swap") {
        ArkDepositPolicy.isFundedByCenterSwap(
            pendingDeposit(listOf(fundingTxid)),
            listOf(l1(fundingTxid, swap = true)),
        ) shouldBe true
    }

    test("match is case-insensitive") {
        ArkDepositPolicy.isFundedByCenterSwap(
            pendingDeposit(listOf(fundingTxid)),
            listOf(l1(fundingTxid.uppercase(), swap = true)),
        ) shouldBe true
    }

    test("board txid also links the funding L1 tx") {
        ArkDepositPolicy.isFundedByCenterSwap(
            pendingDeposit(emptyList(), boardTxid = fundingTxid),
            listOf(l1(fundingTxid, swap = true)),
        ) shouldBe true
    }

    test("plain L1 send with the same txid is not a swap") {
        ArkDepositPolicy.isFundedByCenterSwap(
            pendingDeposit(listOf(fundingTxid)),
            listOf(l1(fundingTxid, swap = false)),
        ) shouldBe false
    }

    test("swap L1 tx with a different txid is not matched") {
        ArkDepositPolicy.isFundedByCenterSwap(
            pendingDeposit(listOf(fundingTxid)),
            listOf(l1(otherTxid, swap = true)),
        ) shouldBe false
    }

    test("no chain txids never matches") {
        ArkDepositPolicy.isFundedByCenterSwap(
            pendingDeposit(emptyList()),
            listOf(l1(fundingTxid, swap = true)),
        ) shouldBe false
    }
})
