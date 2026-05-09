package github.aeonbtc.ibiswallet.data.boltz

import github.aeonbtc.ibiswallet.data.model.LightningPaymentBackend
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class BoltzProviderPortTest : FunSpec({

    test("transport settings are scoped to fallback paths in the hybrid provider") {
        val capabilities = BoltzProviderCapabilities()

        capabilities.usesFallbackTransport shouldBe true
        capabilities.transportSettingsAffectFallbackOnly shouldBe true
        capabilities.requires(BoltzFallbackFeature.BOLT12_INVOICE_FETCH) shouldBe true
    }

    test("LWK-backed swaps stay on native polling") {
        resolveBoltzActivityMode(
            backend = LightningPaymentBackend.LWK_PREPARE_PAY,
            preferTransportFallback = false,
        ) shouldBe BoltzActivityMode.LWK_POLLING
    }

    test("legacy fallback swaps use transport status updates") {
        resolveBoltzActivityMode(
            backend = LightningPaymentBackend.BOLTZ_REST_SUBMARINE,
            preferTransportFallback = false,
        ) shouldBe BoltzActivityMode.FALLBACK_STATUS
    }

    test("explicit transport preference overrides backend defaults") {
        resolveBoltzActivityMode(
            backend = LightningPaymentBackend.LWK_PREPARE_PAY,
            preferTransportFallback = true,
        ) shouldBe BoltzActivityMode.FALLBACK_STATUS
    }
})
