package github.aeonbtc.ibiswallet.util

import github.aeonbtc.ibiswallet.R
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

class SpeedUpTransactionErrorsTest : FunSpec({
    val localizer =
        object : SpeedUpTransactionErrors.Localizer {
            override fun get(resId: Int): String = "res:$resId"

            override fun get(
                resId: Int,
                vararg formatArgs: Any,
            ): String = "res:$resId:${formatArgs.joinToString()}"
        }

    test("RBF insufficient funds maps to dedicated message") {
        val message =
            SpeedUpTransactionErrors.mapRbfFailure(
                localizer,
                RuntimeException("Insufficient funds for fee bump"),
            )
        message shouldBe "res:${R.string.speed_up_error_rbf_insufficient}"
    }

    test("RBF confirmed maps to dedicated message") {
        val message =
            SpeedUpTransactionErrors.mapRbfFailure(
                localizer,
                RuntimeException("Transaction already confirmed in chain"),
            )
        message shouldBe "res:${R.string.speed_up_error_rbf_confirmed}"
    }

    test("CPFP broadcast rejection maps to dedicated message") {
        val message =
            SpeedUpTransactionErrors.mapCpfpFailure(
                localizer,
                RuntimeException("electrum broadcast failed: min relay fee not met"),
            )
        message shouldBe "res:${R.string.speed_up_error_cpfp_broadcast_rejected}"
    }

    test("unknown RBF error includes sanitized detail") {
        val message =
            SpeedUpTransactionErrors.mapRbfFailure(
                localizer,
                RuntimeException("custom upstream failure"),
            )
        message shouldContain "res:${R.string.speed_up_error_rbf_generic}"
        message shouldContain "custom upstream failure"
    }
})
