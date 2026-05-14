package github.aeonbtc.ibiswallet.viewmodel

import github.aeonbtc.ibiswallet.data.model.Layer2Provider
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class SparkRestoredSettingsTest : FunSpec({

    test("restored Spark provider enables wallet even without legacy Spark flag") {
        SparkRestoredSettings.isWalletSparkEnabled(
            storedSparkEnabled = false,
            provider = Layer2Provider.SPARK,
        ) shouldBe true
    }

    test("restored non Spark provider does not enable wallet without Spark flag") {
        SparkRestoredSettings.isWalletSparkEnabled(
            storedSparkEnabled = false,
            provider = Layer2Provider.LIQUID,
        ) shouldBe false
    }

    test("restored Spark flag enables wallet when provider is missing") {
        SparkRestoredSettings.isWalletSparkEnabled(
            storedSparkEnabled = true,
            provider = Layer2Provider.NONE,
        ) shouldBe true
    }
})
