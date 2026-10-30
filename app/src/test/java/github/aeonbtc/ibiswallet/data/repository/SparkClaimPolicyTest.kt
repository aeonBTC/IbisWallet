package github.aeonbtc.ibiswallet.data.repository

import breez_sdk_spark.ClaimDeferredReason
import breez_sdk_spark.ClaimDepositOutcome
import breez_sdk_spark.ClaimDepositQuote
import breez_sdk_spark.InstantClaimStatus
import breez_sdk_spark.MaxFee
import breez_sdk_spark.Payment
import breez_sdk_spark.PaymentMethod
import breez_sdk_spark.PaymentStatus
import breez_sdk_spark.PaymentType
import github.aeonbtc.ibiswallet.data.model.SparkClaimDeferredReason
import github.aeonbtc.ibiswallet.data.model.SparkClaimOutcome
import github.aeonbtc.ibiswallet.data.model.SparkClaimStatus
import github.aeonbtc.ibiswallet.data.model.SparkDepositClaimQuote
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

/**
 * Traces Spark deposit-claim mapping for Breez 0.26+: early-claim lifecycle,
 * per-deposit ceilings, deferred outcomes, and quote building with a
 * standard-claim fallback.
 */
class SparkClaimPolicyTest : FunSpec({

    context("claimStatusOf") {
        test("null maps to NONE") {
            SparkClaimPolicy.claimStatusOf(null) shouldBe SparkClaimStatus.NONE
        }

        test("Submitted maps to SUBMITTED") {
            SparkClaimPolicy.claimStatusOf(InstantClaimStatus.Submitted("claim-1")) shouldBe
                SparkClaimStatus.SUBMITTED
        }

        test("Claimed maps to CLAIMED") {
            SparkClaimPolicy.claimStatusOf(InstantClaimStatus.Claimed) shouldBe SparkClaimStatus.CLAIMED
        }

        test("Declined maps to DECLINED") {
            SparkClaimPolicy.claimStatusOf(InstantClaimStatus.Declined(null, 0u)) shouldBe
                SparkClaimStatus.DECLINED
        }
    }

    context("maxClaimFeeSatsOf") {
        test("null means configured ceiling applies") {
            SparkClaimPolicy.maxClaimFeeSatsOf(null).shouldBeNull()
        }

        test("Fixed maps to its amount") {
            SparkClaimPolicy.maxClaimFeeSatsOf(MaxFee.Fixed(100u)) shouldBe 100L
        }

        test("zero Fixed is unknown") {
            SparkClaimPolicy.maxClaimFeeSatsOf(MaxFee.Fixed(0u)).shouldBeNull()
        }

        test("Rate has no fixed amount") {
            SparkClaimPolicy.maxClaimFeeSatsOf(MaxFee.Rate(5u)).shouldBeNull()
        }

        test("NetworkRecommended has no fixed amount") {
            SparkClaimPolicy.maxClaimFeeSatsOf(MaxFee.NetworkRecommended(1u)).shouldBeNull()
        }
    }

    context("deferredReasonOf") {
        test("MaxFeeExceeded keeps the required fee") {
            SparkClaimPolicy.deferredReasonOf(
                ClaimDeferredReason.MaxFeeExceeded(requiredFeeSats = 500u, maxFeeSats = 100u),
            ) shouldBe (SparkClaimDeferredReason.MAX_FEE_EXCEEDED to 500L)
        }

        test("NoEarlyClaimAvailable carries no fee") {
            SparkClaimPolicy.deferredReasonOf(
                ClaimDeferredReason.NoEarlyClaimAvailable,
            ) shouldBe (SparkClaimDeferredReason.NO_EARLY_CLAIM to null)
        }

        test("ProviderDeclined carries no fee") {
            SparkClaimPolicy.deferredReasonOf(
                ClaimDeferredReason.ProviderDeclined("refused"),
            ) shouldBe (SparkClaimDeferredReason.PROVIDER_DECLINED to null)
        }
    }

    context("outcomeOf") {
        test("Submitted passes through") {
            SparkClaimPolicy.outcomeOf(ClaimDepositOutcome.Submitted) shouldBe SparkClaimOutcome.Submitted
        }

        test("Deferred maps its reason") {
            SparkClaimPolicy.outcomeOf(
                ClaimDepositOutcome.Deferred(ClaimDeferredReason.NoEarlyClaimAvailable),
            ) shouldBe SparkClaimOutcome.Deferred(SparkClaimDeferredReason.NO_EARLY_CLAIM)
        }

        test("Settled keeps the payment id") {
            val payment =
                Payment(
                    id = "pay-1",
                    paymentType = PaymentType.RECEIVE,
                    status = PaymentStatus.COMPLETED,
                    amount = java.math.BigInteger.valueOf(9_800L),
                    fees = java.math.BigInteger.valueOf(150L),
                    timestamp = 0u,
                    method = PaymentMethod.DEPOSIT,
                    details = null,
                    conversionDetails = null,
                )
            SparkClaimPolicy.outcomeOf(
                ClaimDepositOutcome.Settled(payment),
            ) shouldBe SparkClaimOutcome.Settled("pay-1")
        }
    }

    context("blocksToWait") {
        test("depth below required waits the gap") {
            SparkClaimPolicy.blocksToWait(required = 3L, confirmations = 1L) shouldBe 2L
        }

        test("depth reached waits nothing") {
            SparkClaimPolicy.blocksToWait(required = 1L, confirmations = 1L) shouldBe 0L
        }

        test("depth past required clamps at zero") {
            SparkClaimPolicy.blocksToWait(required = 0L, confirmations = 5L) shouldBe 0L
        }
    }

    context("buildQuote") {
        val mature =
            ClaimDepositQuote(
                confirmationsRequired = 3u,
                creditAmountSats = 9_900u,
                feeSats = 100u,
                feeRateSatPerVbyte = 2u,
                isEstimate = true,
            )

        test("early offer maps credit and fee") {
            SparkClaimPolicy.buildQuote(
                txid = "tx",
                vout = 0u,
                amountSats = 10_000L,
                confirmations = 0L,
                instant =
                    ClaimDepositQuote(
                        confirmationsRequired = 0u,
                        creditAmountSats = 9_800u,
                        feeSats = 150u,
                        feeRateSatPerVbyte = 3u,
                        isEstimate = false,
                    ),
                mature = mature,
            ) shouldBe
                SparkDepositClaimQuote(
                    txid = "tx",
                    vout = 0u,
                    amountSats = 10_000L,
                    confirmations = 0L,
                    creditSats = 9_800L,
                    feeSats = 150L,
                    feeRateSatPerVb = 3L,
                    isEstimate = false,
                    isEarlyOffer = true,
                    instantConfirmationsRequired = 0L,
                    matureFeeSats = 100L,
                    matureConfirmationsRequired = 3L,
                    matureIsEstimate = true,
                )
        }

        test("no early offer falls back to standard-claim terms") {
            SparkClaimPolicy.buildQuote(
                txid = "tx",
                vout = 0u,
                amountSats = 10_000L,
                confirmations = 1L,
                instant = null,
                mature = mature,
            ) shouldBe
                SparkDepositClaimQuote(
                    txid = "tx",
                    vout = 0u,
                    amountSats = 10_000L,
                    confirmations = 1L,
                    creditSats = 9_900L,
                    feeSats = 100L,
                    feeRateSatPerVb = 2L,
                    isEstimate = true,
                    isEarlyOffer = false,
                    matureFeeSats = 100L,
                    matureConfirmationsRequired = 3L,
                    matureIsEstimate = true,
                )
        }

        test("implausible early quote hides") {
            SparkClaimPolicy.buildQuote(
                txid = "tx",
                vout = 0u,
                amountSats = 10_000L,
                confirmations = 0L,
                instant =
                    ClaimDepositQuote(
                        confirmationsRequired = 0u,
                        creditAmountSats = 100u,
                        feeSats = 20_000u,
                        feeRateSatPerVbyte = 3u,
                        isEstimate = false,
                    ),
                mature = mature,
            ).shouldBeNull()
        }

        test("early fee at the deposit value hides (unclaimable ceiling)") {
            SparkClaimPolicy.buildQuote(
                txid = "tx",
                vout = 0u,
                amountSats = 10_000L,
                confirmations = 0L,
                instant =
                    ClaimDepositQuote(
                        confirmationsRequired = 0u,
                        creditAmountSats = 100u,
                        feeSats = 10_000u,
                        feeRateSatPerVbyte = 3u,
                        isEstimate = false,
                    ),
                mature = mature,
            ).shouldBeNull()
        }

        test("overflowing quote hides") {
            SparkClaimPolicy.buildQuote(
                txid = "tx",
                vout = 0u,
                amountSats = 10_000L,
                confirmations = 0L,
                instant =
                    ClaimDepositQuote(
                        confirmationsRequired = 0u,
                        creditAmountSats = ULong.MAX_VALUE,
                        feeSats = 150u,
                        feeRateSatPerVbyte = 3u,
                        isEstimate = false,
                    ),
                mature = mature,
            ).shouldBeNull()
        }
    }
})
