package github.aeonbtc.ibiswallet.data.repository

import github.aeonbtc.ibiswallet.data.model.SparkOnchainFeeSpeed
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class SparkWithdrawalPreparePolicyTest : FunSpec({

    fun reuse(
        existingRequest: String? = "bc1qexample",
        existingSpeed: SparkOnchainFeeSpeed? = SparkOnchainFeeSpeed.FAST,
        existingFeesIncluded: Boolean? = false,
        existingAmountSats: Long? = 1000L,
        isStandard: Boolean = true,
        paymentRequest: String = "bc1qexample",
        onchainFeeSpeed: SparkOnchainFeeSpeed = SparkOnchainFeeSpeed.FAST,
        useAllFunds: Boolean = false,
        amountSats: Long? = 1000L,
    ) = SparkRepository.SparkWithdrawalPreparePolicy.shouldReusePrepared(
        existingRequest = existingRequest,
        existingSpeed = existingSpeed,
        existingFeesIncluded = existingFeesIncluded,
        existingAmountSats = existingAmountSats,
        isStandard = isStandard,
        paymentRequest = paymentRequest,
        onchainFeeSpeed = onchainFeeSpeed,
        useAllFunds = useAllFunds,
        amountSats = amountSats,
    )

    test("reuses matching standard prepared withdrawal") {
        reuse() shouldBe true
    }

    test("null existing never matches (cleared by reset, detach, or reconnect)") {
        reuse(
            existingRequest = null,
            existingSpeed = null,
            existingFeesIncluded = null,
        ) shouldBe false
    }

    test("non-standard (LNURL) prepares never match a withdrawal") {
        reuse(isStandard = false) shouldBe false
    }

    test("different destination address forces re-prepare") {
        reuse(paymentRequest = "bc1qother") shouldBe false
    }

    test("different fee speed forces re-prepare") {
        reuse(onchainFeeSpeed = SparkOnchainFeeSpeed.SLOW) shouldBe false
    }

    test("max vs non-max mismatch forces re-prepare") {
        reuse(useAllFunds = true) shouldBe false
    }

    test("surrounding whitespace is ignored") {
        reuse(paymentRequest = "  bc1qexample  ") shouldBe true
    }

    test("different amount forces re-prepare") {
        reuse(amountSats = 2000L) shouldBe false
    }

    test("null vs non-null amount forces re-prepare") {
        reuse(existingAmountSats = null) shouldBe false
    }

    test("drain prepares never reuse") {
        reuse(
            existingFeesIncluded = true,
            existingAmountSats = null,
            useAllFunds = true,
            amountSats = null,
        ) shouldBe false
    }
})
