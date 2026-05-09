package github.aeonbtc.ibiswallet.nfc

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class NfcReaderModeRequestRegistryTest : FunSpec({
    context("screen-scoped NFC reader requests") {
        test("reader mode stays requested until the last owner releases") {
            val registry = NfcReaderModeRequestRegistry()
            val ownerA = Any()
            val ownerB = Any()

            registry.request(ownerA)
            registry.request(ownerB)
            registry.hasActiveRequests() shouldBe true

            registry.release(ownerA)
            registry.hasActiveRequests() shouldBe true

            registry.release(ownerB)
            registry.hasActiveRequests() shouldBe false
        }

        test("the same owner can request multiple times without needing multiple releases") {
            val registry = NfcReaderModeRequestRegistry()
            val owner = Any()

            registry.request(owner)
            registry.request(owner)
            registry.hasActiveRequests() shouldBe true

            registry.release(owner)
            registry.hasActiveRequests() shouldBe false
        }
    }
})
