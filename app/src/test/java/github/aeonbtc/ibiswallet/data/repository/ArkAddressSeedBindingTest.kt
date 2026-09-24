package github.aeonbtc.ibiswallet.data.repository

import github.aeonbtc.ibiswallet.data.repository.ArkAddressSeedBinding
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class ArkAddressSeedBindingTest : FunSpec({

    test("matching fingerprints verify") {
        ArkRepository.arkAddressSeedBindingVerdict(
            boundFp = "a1b2c3d4",
            liveFp = "a1b2c3d4",
        ) shouldBe ArkAddressSeedBinding.MATCH
    }

    test("comparison ignores case and whitespace") {
        ArkRepository.arkAddressSeedBindingVerdict(
            boundFp = "  A1B2C3D4 ",
            liveFp = "a1b2c3d4",
        ) shouldBe ArkAddressSeedBinding.MATCH
    }

    test("different fingerprints mismatch") {
        ArkRepository.arkAddressSeedBindingVerdict(
            boundFp = "a1b2c3d4",
            liveFp = "e5f60718",
        ) shouldBe ArkAddressSeedBinding.MISMATCH
    }

    test("missing bound fingerprint adopts") {
        ArkRepository.arkAddressSeedBindingVerdict(
            boundFp = null,
            liveFp = "a1b2c3d4",
        ) shouldBe ArkAddressSeedBinding.ADOPT
        ArkRepository.arkAddressSeedBindingVerdict(
            boundFp = "   ",
            liveFp = "a1b2c3d4",
        ) shouldBe ArkAddressSeedBinding.ADOPT
    }

    test("missing live fingerprint is unverified") {
        ArkRepository.arkAddressSeedBindingVerdict(
            boundFp = "a1b2c3d4",
            liveFp = null,
        ) shouldBe ArkAddressSeedBinding.UNVERIFIED
        ArkRepository.arkAddressSeedBindingVerdict(
            boundFp = null,
            liveFp = null,
        ) shouldBe ArkAddressSeedBinding.UNVERIFIED
    }
})
