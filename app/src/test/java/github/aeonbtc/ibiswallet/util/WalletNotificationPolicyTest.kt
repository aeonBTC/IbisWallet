package github.aeonbtc.ibiswallet.util

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe

class WalletNotificationPolicyTest : FunSpec({

    test("first synced snapshot seeds the notification baseline without notifying") {
        val update =
            WalletNotificationPolicy.updateTrackedTransactions(
                currentTxids = setOf("old-1", "old-2"),
                trackedTxids = emptySet(),
                baselineEstablished = false,
            )

        update.trackedTxids shouldContainExactlyInAnyOrder setOf("old-1", "old-2")
        update.notifyTxids.shouldBeEmpty()
        update.baselineEstablished shouldBe true
    }

    test("empty first snapshot still establishes a baseline") {
        val seeded =
            WalletNotificationPolicy.updateTrackedTransactions(
                currentTxids = emptySet(),
                trackedTxids = emptySet(),
                baselineEstablished = false,
            )

        seeded.trackedTxids.shouldBeEmpty()
        seeded.notifyTxids.shouldBeEmpty()
        seeded.baselineEstablished shouldBe true

        val laterReceive =
            WalletNotificationPolicy.updateTrackedTransactions(
                currentTxids = setOf("new-1"),
                trackedTxids = seeded.trackedTxids,
                baselineEstablished = seeded.baselineEstablished,
            )

        laterReceive.notifyTxids shouldContainExactlyInAnyOrder setOf("new-1")
    }

    test("only unseen txids notify after the baseline exists") {
        val update =
            WalletNotificationPolicy.updateTrackedTransactions(
                currentTxids = setOf("old-1", "new-1", "new-2"),
                trackedTxids = setOf("old-1"),
                baselineEstablished = true,
            )

        update.trackedTxids shouldContainExactlyInAnyOrder setOf("old-1", "new-1", "new-2")
        update.notifyTxids shouldContainExactlyInAnyOrder setOf("new-1", "new-2")
        update.baselineEstablished shouldBe true
    }

    test("app disabled takes precedence over Android notification state") {
        WalletNotificationPolicy.resolveDeliveryState(
            appEnabled = false,
            permissionGranted = false,
            systemNotificationsEnabled = false,
        ) shouldBe WalletNotificationDeliveryState.APP_DISABLED
    }

    test("permission required is reported before system-disabled state") {
        WalletNotificationPolicy.resolveDeliveryState(
            appEnabled = true,
            permissionGranted = false,
            systemNotificationsEnabled = false,
        ) shouldBe WalletNotificationDeliveryState.PERMISSION_REQUIRED
    }

    test("system-disabled state is reported when app is enabled and permission is granted") {
        WalletNotificationPolicy.resolveDeliveryState(
            appEnabled = true,
            permissionGranted = true,
            systemNotificationsEnabled = false,
        ) shouldBe WalletNotificationDeliveryState.SYSTEM_DISABLED
    }

    test("ready state is reported when app and Android both allow notifications") {
        WalletNotificationPolicy.resolveDeliveryState(
            appEnabled = true,
            permissionGranted = true,
            systemNotificationsEnabled = true,
        ) shouldBe WalletNotificationDeliveryState.READY
    }
})
