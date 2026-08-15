package github.aeonbtc.ibiswallet.data.repository

internal object SyncProgressPublishPolicy {
    const val THROTTLE_MS = 150L
    const val THROTTLE_SCRIPTS = 50L

    fun shouldPublish(
        current: ULong,
        total: ULong,
        nowElapsedMs: Long,
        lastPublishElapsedMs: Long,
        throttleMs: Long = THROTTLE_MS,
        throttleScripts: Long = THROTTLE_SCRIPTS,
    ): Boolean {
        val isBoundary = current <= 1UL || (total > 0UL && current >= total)
        val isCountTick = current > 0UL && current % throttleScripts.toULong() == 0UL
        val isTimeTick = nowElapsedMs - lastPublishElapsedMs >= throttleMs
        return isBoundary || isCountTick || isTimeTick
    }
}
