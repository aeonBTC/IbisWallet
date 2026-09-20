package github.aeonbtc.ibiswallet.data.repository

import github.aeonbtc.ibiswallet.data.model.SparkPayment
import github.aeonbtc.ibiswallet.data.model.SparkPendingLnInvoice
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class SparkLnInvoicePaidMatcherTest : FunSpec({

    val invoice = "lnbc100n1psjntredpp5fakelninvoiceformatching"

    fun lnPayment(
        status: String = "COMPLETED",
        type: String = "RECEIVE",
        recipient: String? = invoice,
        methodDetails: String = "Lightning(invoice=$invoice)",
    ) = SparkPayment(
        id = "payment-id",
        type = type,
        status = status,
        amountSats = 10_000L,
        feeSats = 0L,
        timestamp = 0L,
        method = "Lightning",
        recipient = recipient,
        methodDetails = methodDetails,
    )

    test("matches settled receive payment carrying the invoice") {
        SparkLnInvoicePaidMatcher.matches(invoice, lnPayment()) shouldBe true
    }

    test("matches case-insensitively") {
        SparkLnInvoicePaidMatcher.matches(
            invoice.uppercase(),
            lnPayment(),
        ) shouldBe true
    }

    test("matches via methodDetails fallback when recipient is absent") {
        SparkLnInvoicePaidMatcher.matches(
            invoice,
            lnPayment(recipient = null),
        ) shouldBe true
    }

    test("rejects pending payment") {
        SparkLnInvoicePaidMatcher.matches(
            invoice,
            lnPayment(status = "PENDING"),
        ) shouldBe false
    }

    test("rejects send payment carrying the invoice") {
        SparkLnInvoicePaidMatcher.matches(
            invoice,
            lnPayment(type = "SEND"),
        ) shouldBe false
    }

    test("rejects different invoice") {
        SparkLnInvoicePaidMatcher.matches(
            "lnbc200n1psdifferentinvoice",
            lnPayment(),
        ) shouldBe false
    }

    test("rejects blank invoice") {
        SparkLnInvoicePaidMatcher.matches("   ", lnPayment()) shouldBe false
    }

    test("fresh invoice is not expired") {
        SparkLnInvoicePaidMatcher.isExpired(
            SparkPendingLnInvoice(invoice, 10_000L, "", createdAtMs = 1_000L),
            nowMs = 1_000L + SparkRepository.SPARK_LN_INVOICE_TTL_MS - 1L,
        ) shouldBe false
    }

    test("invoice past TTL is expired") {
        SparkLnInvoicePaidMatcher.isExpired(
            SparkPendingLnInvoice(invoice, 10_000L, "", createdAtMs = 1_000L),
            nowMs = 1_000L + SparkRepository.SPARK_LN_INVOICE_TTL_MS,
        ) shouldBe true
    }

    test("invoice without timestamp never expires") {
        SparkLnInvoicePaidMatcher.isExpired(
            SparkPendingLnInvoice(invoice, 10_000L, "", createdAtMs = 0L),
            nowMs = Long.MAX_VALUE,
        ) shouldBe false
    }
})
