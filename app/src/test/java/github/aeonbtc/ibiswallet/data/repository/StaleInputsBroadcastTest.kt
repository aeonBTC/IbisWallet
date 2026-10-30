package github.aeonbtc.ibiswallet.data.repository

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class StaleInputsBroadcastTest : FunSpec({

    context("isKnownTxBroadcastRejection") {
        test("recognizes duplicate-transaction phrases") {
            WalletRepository.isKnownTxBroadcastRejection("txn-already-known") shouldBe true
            WalletRepository.isKnownTxBroadcastRejection("already in block chain") shouldBe true
            WalletRepository.isKnownTxBroadcastRejection("transaction already in block chain") shouldBe true
        }

        test("does not treat already-spent as a duplicate broadcast") {
            WalletRepository.isKnownTxBroadcastRejection(
                "bad-txns-inputs-missingorspent, already spent",
            ) shouldBe false
        }

        test("unrelated failure does not match") {
            WalletRepository.isKnownTxBroadcastRejection("Not connected to Electrum server") shouldBe false
        }
    }

    context("isStaleInputsBroadcastFailure") {
        test("screenshot error string matches") {
            WalletRepository.isStaleInputsBroadcastFailure(
                Exception(
                    "errorMessage={'code':-32603,'message':\"sendrawtransaction RPC error: " +
                        "{\\\"code\\\":-25,\\\"message\\\":\\\"bad-txns-inputs-missingorspent\\\"}\"}",
                ),
            ) shouldBe true
        }

        test("bare bitcoind reason matches") {
            WalletRepository.isStaleInputsBroadcastFailure(
                Exception("sendrawtransaction failed: bad-txns-inputs-missingorspent"),
            ) shouldBe true
        }

        test("wrapped cause chain matches") {
            val root = RuntimeException("{\"code\":-25,\"message\":\"bad-txns-inputs-missingorspent\"}")
            val wrapped = RuntimeException("sendrawtransaction RPC error", root)
            WalletRepository.isStaleInputsBroadcastFailure(wrapped) shouldBe true
        }

        test("null and unrelated failures do not match") {
            WalletRepository.isStaleInputsBroadcastFailure(null) shouldBe false
            WalletRepository.isStaleInputsBroadcastFailure(
                Exception("Not connected to Electrum server"),
            ) shouldBe false
            WalletRepository.isStaleInputsBroadcastFailure(
                Exception("Insufficient funds"),
            ) shouldBe false
            WalletRepository.isStaleInputsBroadcastFailure(
                Exception("txn-mempool-conflict, already in mempool"),
            ) shouldBe false
        }
    }
})
