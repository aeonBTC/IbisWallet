package github.aeonbtc.ibiswallet.data.repository

/**
 * Pure transport-liveness decisions for Ark.
 *
 * Bark exposes no connection-state callback and every repository call site
 * swallows transport errors, so liveness is inferred from consecutive
 * sync/hydrate outcomes: [nextFailureStreak] tracks them, [shouldMarkDisconnected]
 * flips the pill only after a sustained outage (single Tor blips recover), and
 * [shouldAutoReconnect] gates exactly one silent reload per outage episode.
 */
object ArkConnectionPolicy {
    /** Consecutive failed syncs/hydrates before the session counts as dead. */
    const val FAILURE_STREAK_LIMIT = 3

    /** Minimum gap between silent auto-reconnect attempts (monotonic ms). */
    const val AUTO_RECONNECT_COOLDOWN_MS = 60_000L

    fun nextFailureStreak(ok: Boolean, streak: Int): Int =
        if (ok) 0 else (streak + 1).coerceAtLeast(0)

    fun shouldMarkDisconnected(
        streak: Int,
        limit: Int = FAILURE_STREAK_LIMIT,
        connected: Boolean,
        walletLoaded: Boolean,
    ): Boolean = connected && walletLoaded && streak >= limit

    fun shouldAutoReconnect(
        connected: Boolean,
        connecting: Boolean,
        walletPresent: Boolean,
        arkEnabled: Boolean,
        foregrounded: Boolean,
        cooldownElapsedMs: Long,
        cooldownMs: Long = AUTO_RECONNECT_COOLDOWN_MS,
    ): Boolean =
        !connected &&
            !connecting &&
            walletPresent &&
            arkEnabled &&
            foregrounded &&
            cooldownElapsedMs >= cooldownMs
}
