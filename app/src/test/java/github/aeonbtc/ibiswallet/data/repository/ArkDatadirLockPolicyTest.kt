package github.aeonbtc.ibiswallet.data.repository

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class ArkDatadirLockPolicyTest : FunSpec({

    test("detects bark datadir lock manager errors") {
        ArkDatadirLockPolicy.isDatadirLockError(
            "Already using datadir /data/user/0/.../ark-session",
        ) shouldBe true
        ArkDatadirLockPolicy.isDatadirLockError(
            "Failed to instantiate platform default lock manager",
        ) shouldBe true
        ArkDatadirLockPolicy.isDatadirLockError("dns error") shouldBe false
        ArkDatadirLockPolicy.isDatadirLockError(null) shouldBe false
    }

    test("retries until the last attempt") {
        ArkDatadirLockPolicy.shouldRetry(0) shouldBe true
        ArkDatadirLockPolicy.shouldRetry(6) shouldBe true
        ArkDatadirLockPolicy.shouldRetry(7) shouldBe false
        ArkDatadirLockPolicy.MAX_AUTO_REOPENS shouldBe 3
    }

    test("backs off retry delay with a cap") {
        ArkDatadirLockPolicy.retryDelayMs(0) shouldBe 400L
        ArkDatadirLockPolicy.retryDelayMs(1) shouldBe 800L
        ArkDatadirLockPolicy.retryDelayMs(2) shouldBe 1_600L
        ArkDatadirLockPolicy.retryDelayMs(3) shouldBe 2_000L
        ArkDatadirLockPolicy.retryDelayMs(7) shouldBe 2_000L
    }
})
