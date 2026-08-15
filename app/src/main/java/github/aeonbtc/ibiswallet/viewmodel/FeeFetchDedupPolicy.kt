package github.aeonbtc.ibiswallet.viewmodel

internal object FeeFetchDedupPolicy {
    const val DEDUP_WINDOW_MS = 60_000L

    fun shouldSkip(
        force: Boolean,
        inFlight: Boolean,
        lastSource: String,
        currentSource: String,
        lastSuccessElapsedMs: Long,
        nowElapsedMs: Long,
        lastResultWasSuccess: Boolean,
        windowMs: Long = DEDUP_WINDOW_MS,
    ): Boolean {
        if (force) return false
        if (inFlight) return true
        return lastResultWasSuccess &&
            lastSource == currentSource &&
            lastSuccessElapsedMs != 0L &&
            nowElapsedMs - lastSuccessElapsedMs < windowMs
    }
}
