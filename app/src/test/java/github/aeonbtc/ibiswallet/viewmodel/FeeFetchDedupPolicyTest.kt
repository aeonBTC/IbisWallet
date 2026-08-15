package github.aeonbtc.ibiswallet.viewmodel

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class FeeFetchDedupPolicyTest : FunSpec({
    test("skips a fresh successful fetch of the same source") {
        FeeFetchDedupPolicy.shouldSkip(
            force = false,
            inFlight = false,
            lastSource = "mempool",
            currentSource = "mempool",
            lastSuccessElapsedMs = 1_000L,
            nowElapsedMs = 2_000L,
            lastResultWasSuccess = true,
        ) shouldBe true
    }

    test("does not skip after the dedup window") {
        FeeFetchDedupPolicy.shouldSkip(
            force = false,
            inFlight = false,
            lastSource = "mempool",
            currentSource = "mempool",
            lastSuccessElapsedMs = 1_000L,
            nowElapsedMs = 1_000L + FeeFetchDedupPolicy.DEDUP_WINDOW_MS,
            lastResultWasSuccess = true,
        ) shouldBe false
    }

    test("does not skip a failed previous fetch") {
        FeeFetchDedupPolicy.shouldSkip(
            force = false,
            inFlight = false,
            lastSource = "electrum",
            currentSource = "electrum",
            lastSuccessElapsedMs = 0L,
            nowElapsedMs = 2_000L,
            lastResultWasSuccess = false,
        ) shouldBe false
    }

    test("force bypasses an in-flight fetch") {
        FeeFetchDedupPolicy.shouldSkip(
            force = true,
            inFlight = true,
            lastSource = "mempool",
            currentSource = "mempool",
            lastSuccessElapsedMs = 1_000L,
            nowElapsedMs = 1_100L,
            lastResultWasSuccess = true,
        ) shouldBe false
    }
})
