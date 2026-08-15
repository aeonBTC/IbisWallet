package github.aeonbtc.ibiswallet.data.repository

/**
 * Bark datadir lock-manager conflicts are transient when a prior open/close in this
 * process still holds the lock. Retry must **release** the native-handle mutex between
 * attempts so that close can finish; holding the mutex across [delay] deadlocks reopen.
 */
object ArkDatadirLockPolicy {
    const val MAX_ATTEMPTS = 8
    const val MAX_AUTO_REOPENS = 3
    const val BASE_DELAY_MS = 400L
    const val MAX_DELAY_MS = 2_000L

    fun isDatadirLockError(message: String?): Boolean {
        val text = message.orEmpty().lowercase()
        return text.contains("already using datadir") ||
            text.contains("lock manager") ||
            text.contains("instantiate platform default lock")
    }

    fun shouldRetry(
        attemptIndex: Int,
        maxAttempts: Int = MAX_ATTEMPTS,
    ): Boolean = attemptIndex in 0 until (maxAttempts - 1)

    fun retryDelayMs(
        attemptIndex: Int,
        baseDelayMs: Long = BASE_DELAY_MS,
    ): Long {
        val shift = attemptIndex.coerceAtLeast(0).coerceAtMost(4)
        return (baseDelayMs shl shift).coerceAtMost(MAX_DELAY_MS)
    }
}
