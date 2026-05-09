package github.aeonbtc.ibiswallet.data.repository

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class LiquidRepositoryAddressSelectionTest : FunSpec({

    test("returns index zero when no addresses are occupied") {
        findEarliestUnusedLiquidExternalIndex(emptySet()) shouldBe 0u
    }

    test("returns the next index after a contiguous occupied range") {
        findEarliestUnusedLiquidExternalIndex(setOf(0u, 1u, 2u, 3u)) shouldBe 4u
    }

    test("returns the earliest gap when lower unused addresses exist") {
        findEarliestUnusedLiquidExternalIndex(setOf(0u, 2u, 3u, 6u)) shouldBe 1u
    }

    test("ignores sparse higher occupied indices once an earlier gap exists") {
        findEarliestUnusedLiquidExternalIndex(setOf(0u, 1u, 2u, 8u, 25u)) shouldBe 3u
    }
})
