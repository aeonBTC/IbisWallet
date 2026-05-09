package github.aeonbtc.ibiswallet.data.boltz

import github.aeonbtc.ibiswallet.data.model.SwapDirection
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class BoltzMaxReviewPolicyTest : FunSpec({

    test("pre-resolves Boltz max for peg-ins once the real lockup exists") {
        shouldPreResolveBoltzMaxAmount(
            direction = SwapDirection.BTC_TO_LBTC,
            usesMaxAmount = true,
        ) shouldBe true

        shouldPreResolveBoltzMaxAmount(
            direction = SwapDirection.LBTC_TO_BTC,
            usesMaxAmount = true,
        ) shouldBe false
    }

    test("recreates max Boltz order when verified peg-out amount changes") {
        shouldRecreateBoltzMaxOrder(
            direction = SwapDirection.LBTC_TO_BTC,
            usesMaxAmount = true,
            quotedAmount = 62_557L,
            verifiedAmount = 62_549L,
        ) shouldBe true

        shouldRecreateBoltzMaxOrder(
            direction = SwapDirection.LBTC_TO_BTC,
            usesMaxAmount = true,
            quotedAmount = 62_549L,
            verifiedAmount = 62_549L,
        ) shouldBe false
    }

    test("recreates max Boltz order when verified peg-in amount changes") {
        shouldRecreateBoltzMaxOrder(
            direction = SwapDirection.BTC_TO_LBTC,
            usesMaxAmount = true,
            quotedAmount = 88_000L,
            verifiedAmount = 87_812L,
        ) shouldBe true
    }

    test("detects Liquid shortfall errors for Boltz max preview fallback") {
        isBoltzMaxFundingShortfallError(
            direction = SwapDirection.LBTC_TO_BTC,
            error =
                IllegalStateException(
                    "Failed",
                    RuntimeException("msg=InsufficientFunds { missing_sats: 22 }"),
                ),
        ) shouldBe true
    }

    test("detects Bitcoin shortfall errors for Boltz max preview fallback") {
        isBoltzMaxFundingShortfallError(
            direction = SwapDirection.BTC_TO_LBTC,
            error = IllegalArgumentException("Insufficient funds in selected coins"),
        ) shouldBe true
    }

    test("uses max-preview semantics when validating max Boltz reviews") {
        resolveBoltzReviewFundingPreviewIsMaxSend(
            usesMaxAmount = true,
        ) shouldBe true

        resolveBoltzReviewFundingPreviewIsMaxSend(
            usesMaxAmount = true,
        ) shouldBe true
    }

    test("prefers fresh draft metadata over stale pending max review values") {
        val resolved =
            resolveBoltzMaxReviewMetadata(
                pendingOrderAmount = 37_523L,
                pendingVerifiedAmount = 37_508L,
                pendingOrderVerified = false,
                draftOrderAmount = 37_508L,
                draftVerifiedAmount = 37_508L,
                draftOrderVerified = true,
            )

        resolved.orderAmount shouldBe 37_508L
        resolved.verifiedAmount shouldBe 37_508L
        resolved.orderVerified shouldBe true
    }

    test("accepts a synchronized max peg-out review") {
        hasBoltzMaxReviewMismatch(
            usesMaxAmount = true,
            state =
                BoltzMaxReviewState(
                    reviewAmount = 62_549L,
                    orderAmount = 62_549L,
                    verifiedAmount = 62_549L,
                    pendingAmount = 62_549L,
                    orderVerified = true,
                ),
        ) shouldBe false
    }

    test("rejects stale max peg-out reviews during resume") {
        hasBoltzMaxReviewMismatch(
            usesMaxAmount = true,
            state =
                BoltzMaxReviewState(
                    reviewAmount = 62_549L,
                    orderAmount = 62_557L,
                    verifiedAmount = 62_549L,
                    pendingAmount = 62_549L,
                    orderVerified = false,
                ),
        ) shouldBe true
    }

    test("rejects stale max peg-in reviews during resume") {
        hasBoltzMaxReviewMismatch(
            usesMaxAmount = true,
            state =
                BoltzMaxReviewState(
                    reviewAmount = 87_812L,
                    orderAmount = 88_000L,
                    verifiedAmount = 87_812L,
                    pendingAmount = 87_812L,
                    orderVerified = false,
                ),
        ) shouldBe true
    }
})
