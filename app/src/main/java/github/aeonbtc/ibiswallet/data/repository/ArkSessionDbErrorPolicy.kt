package github.aeonbtc.ibiswallet.data.repository

/**
 * Bark session SQLite lives under cacheDir and can vanish while the native handle
 * is still open (emulator cache eviction, OS trim). Those errors are local — not
 * ASP/Esplora — and must not be classified as transient network failures.
 */
object ArkSessionDbErrorPolicy {
    const val MAX_AUTO_REOPENS = 3
    const val BASE_DELAY_MS = 400L
    const val MAX_DELAY_MS = 2_000L

    fun isSessionDbError(error: Throwable?): Boolean {
        if (error == null) return false
        var current: Throwable? = error
        var depth = 0
        while (current != null && depth < 8) {
            if (isSessionDbError(current.message)) return true
            current = current.cause
            depth++
        }
        return false
    }

    fun isSessionDbError(message: String?): Boolean {
        val text = message.orEmpty().lowercase()
        if (text.isBlank()) return false
        return text.contains("error connecting to database") ||
            text.contains("unable to open database") ||
            text.contains("unable to open db") ||
            text.contains("disk i/o error") ||
            text.contains("disk image is malformed") ||
            text.contains("file is not a database") ||
            text.contains("database disk image") ||
            (text.contains("db.sqlite") && text.contains("open")) ||
            (text.contains("ark-session") && text.contains("database"))
    }

    fun isDnsOrNetworkError(message: String?): Boolean {
        if (isSessionDbError(message)) return false
        val text = message.orEmpty().lowercase()
        return text.contains("dns") ||
            text.contains("lookup") ||
            text.contains("connect") ||
            text.contains("network") ||
            text.contains("timed out") ||
            text.contains("timeout") ||
            text.contains("unreachable") ||
            text.contains("no route") ||
            text.contains("broken pipe") ||
            text.contains("eof") ||
            text.contains("ssl") ||
            text.contains("handshake")
    }

    fun retryDelayMs(
        attemptIndex: Int,
        baseDelayMs: Long = BASE_DELAY_MS,
    ): Long {
        val shift = attemptIndex.coerceAtLeast(0).coerceAtMost(4)
        return (baseDelayMs shl shift).coerceAtMost(MAX_DELAY_MS)
    }
}
