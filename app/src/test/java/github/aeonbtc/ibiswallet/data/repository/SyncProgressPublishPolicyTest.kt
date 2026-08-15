package github.aeonbtc.ibiswallet.data.repository

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class SyncProgressPublishPolicyTest : FunSpec({
    test("publishes the first script") {
        SyncProgressPublishPolicy.shouldPublish(
            current = 1UL,
            total = 2000UL,
            nowElapsedMs = 0L,
            lastPublishElapsedMs = 0L,
        ) shouldBe true
    }

    test("suppresses mid-batch scripts inside the throttle window") {
        SyncProgressPublishPolicy.shouldPublish(
            current = 7UL,
            total = 2000UL,
            nowElapsedMs = 40L,
            lastPublishElapsedMs = 0L,
        ) shouldBe false
    }

    test("publishes every throttle-script count") {
        SyncProgressPublishPolicy.shouldPublish(
            current = SyncProgressPublishPolicy.THROTTLE_SCRIPTS.toULong(),
            total = 2000UL,
            nowElapsedMs = 10L,
            lastPublishElapsedMs = 0L,
        ) shouldBe true
    }

    test("publishes after the throttle interval") {
        SyncProgressPublishPolicy.shouldPublish(
            current = 7UL,
            total = 2000UL,
            nowElapsedMs = SyncProgressPublishPolicy.THROTTLE_MS,
            lastPublishElapsedMs = 0L,
        ) shouldBe true
    }

    test("publishes the final script") {
        SyncProgressPublishPolicy.shouldPublish(
            current = 2000UL,
            total = 2000UL,
            nowElapsedMs = 10L,
            lastPublishElapsedMs = 0L,
        ) shouldBe true
    }
})
