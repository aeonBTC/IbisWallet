package github.aeonbtc.ibiswallet.data.repository

import java.util.concurrent.atomic.AtomicInteger

/**
 * Nested ownership for the Ark balance-card sync spinner.
 * Background paints (notifications, heartbeat) must not begin/end this.
 */
class ArkSyncSpinner {
    private val depth = AtomicInteger(0)

    fun begin(): Boolean = depth.incrementAndGet() == 1

    fun end(): Boolean = depth.updateAndGet { current -> (current - 1).coerceAtLeast(0) } == 0

    fun reset() {
        depth.set(0)
    }

    fun isHeld(): Boolean = depth.get() > 0
}
