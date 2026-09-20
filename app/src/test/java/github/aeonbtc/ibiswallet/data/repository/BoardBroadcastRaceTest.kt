package github.aeonbtc.ibiswallet.data.repository

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class BoardBroadcastRaceTest : FunSpec({

    context("isKnownTxBroadcastRejection") {
        test("bitcoind already-in-chain variants match") {
            WalletRepository.isKnownTxBroadcastRejection(
                "the transaction was rejected by network rules.\n" +
                    "txn-already-known",
            ) shouldBe true
            WalletRepository.isKnownTxBroadcastRejection(
                "Transaction already in block chain",
            ) shouldBe true
            WalletRepository.isKnownTxBroadcastRejection(
                "txn-mempool-conflict, already in mempool",
            ) shouldBe true
        }

        test("duplicate wording matches") {
            WalletRepository.isKnownTxBroadcastRejection(
                "Duplicate transaction",
            ) shouldBe true
        }

        test("null and blank do not match") {
            WalletRepository.isKnownTxBroadcastRejection(null) shouldBe false
            WalletRepository.isKnownTxBroadcastRejection("") shouldBe false
            WalletRepository.isKnownTxBroadcastRejection("   ") shouldBe false
        }

        test("unrelated failures do not match") {
            WalletRepository.isKnownTxBroadcastRejection(
                "Not connected to Electrum server",
            ) shouldBe false
            WalletRepository.isKnownTxBroadcastRejection(
                "Transaction rejected: min relay fee not met",
            ) shouldBe false
            WalletRepository.isKnownTxBroadcastRejection(
                "Insufficient funds",
            ) shouldBe false
        }
    }
})
