package github.aeonbtc.ibiswallet.viewmodel

import github.aeonbtc.ibiswallet.data.model.BoltzSwapUpdate
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class LiquidLightningInvoiceStatusTest : FunSpec({

    test("treats paid reverse swap states as claimable") {
        isLiquidLightningInvoiceClaimableStatus("transaction.mempool") shouldBe true
        isLiquidLightningInvoiceClaimableStatus("transaction.confirmed") shouldBe true
        isLiquidLightningInvoiceClaimableStatus("invoice.settled") shouldBe true
        isLiquidLightningInvoiceClaimableStatus("transaction.claim.pending") shouldBe true
    }

    test("does not treat pending invoice states as claimable") {
        isLiquidLightningInvoiceClaimableStatus("swap.created") shouldBe false
        isLiquidLightningInvoiceClaimableStatus("invoice.set") shouldBe false
        isLiquidLightningInvoiceClaimableStatus("invoice.pending") shouldBe false
    }

    test("recognizes terminal failed reverse swap states") {
        isLiquidLightningInvoiceTerminalFailureStatus("invoice.expired") shouldBe true
        isLiquidLightningInvoiceTerminalFailureStatus("transaction.failed") shouldBe true
        isLiquidLightningInvoiceTerminalFailureStatus("transaction.refunded") shouldBe true
        isLiquidLightningInvoiceTerminalFailureStatus("swap.expired") shouldBe true
    }

    test("retries claimable reverse swap status when no new update arrives") {
        val previous = BoltzSwapUpdate(id = "swap-id", status = "transaction.claim.pending")

        selectLiquidLightningInvoiceClaimUpdate(
            latestUpdate = null,
            previousUpdate = previous,
        ) shouldBe previous
    }

    test("does not replay pending invoice status for claim retries") {
        val previous = BoltzSwapUpdate(id = "swap-id", status = "invoice.pending")

        selectLiquidLightningInvoiceClaimUpdate(
            latestUpdate = null,
            previousUpdate = previous,
        ) shouldBe null
    }
})
