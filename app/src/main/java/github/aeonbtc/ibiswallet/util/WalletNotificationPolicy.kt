package github.aeonbtc.ibiswallet.util

data class WalletNotificationTrackingUpdate(
    val trackedTxids: Set<String>,
    val notifyTxids: Set<String>,
    val baselineEstablished: Boolean,
)

enum class WalletNotificationDeliveryState {
    APP_DISABLED,
    READY,
    PERMISSION_REQUIRED,
    SYSTEM_DISABLED,
}

object WalletNotificationPolicy {
    fun updateTrackedTransactions(
        currentTxids: Set<String>,
        trackedTxids: Set<String>,
        baselineEstablished: Boolean,
    ): WalletNotificationTrackingUpdate {
        val mergedTxids = trackedTxids + currentTxids
        if (!baselineEstablished) {
            return WalletNotificationTrackingUpdate(
                trackedTxids = mergedTxids,
                notifyTxids = emptySet(),
                baselineEstablished = true,
            )
        }

        return WalletNotificationTrackingUpdate(
            trackedTxids = mergedTxids,
            notifyTxids = currentTxids - trackedTxids,
            baselineEstablished = true,
        )
    }

    fun resolveDeliveryState(
        appEnabled: Boolean,
        permissionGranted: Boolean,
        systemNotificationsEnabled: Boolean,
    ): WalletNotificationDeliveryState {
        if (!appEnabled) return WalletNotificationDeliveryState.APP_DISABLED
        if (!permissionGranted) return WalletNotificationDeliveryState.PERMISSION_REQUIRED
        if (!systemNotificationsEnabled) return WalletNotificationDeliveryState.SYSTEM_DISABLED
        return WalletNotificationDeliveryState.READY
    }
}
