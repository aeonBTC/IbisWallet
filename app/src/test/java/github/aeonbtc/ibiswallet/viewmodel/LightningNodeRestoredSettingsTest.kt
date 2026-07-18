package github.aeonbtc.ibiswallet.viewmodel

import github.aeonbtc.ibiswallet.data.model.Layer2Provider
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class LightningNodeRestoredSettingsTest : StringSpec({
    "enabled when stored flag is true" {
        LightningNodeRestoredSettings.isWalletLightningEnabled(
            storedEnabled = true,
            provider = Layer2Provider.NONE,
        ) shouldBe true
    }

    "enabled when provider is LIGHTNING" {
        LightningNodeRestoredSettings.isWalletLightningEnabled(
            storedEnabled = false,
            provider = Layer2Provider.LIGHTNING,
        ) shouldBe true
    }

    "disabled when neither stored nor provider" {
        LightningNodeRestoredSettings.isWalletLightningEnabled(
            storedEnabled = false,
            provider = Layer2Provider.SPARK,
        ) shouldBe false
    }
})
