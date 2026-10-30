package github.aeonbtc.ibiswallet.data.repository

/**
 * Classifies a refresh submission error.
 *
 * A definitive error means Bark/the ASP rejected the supplied inputs before a
 * round existed (spent, invalid, expired, not refreshable). Keeping the local
 * participation in that case blocks every later refresh and even an
 * entire-wallet exit. Transport/unknown errors are ambiguous: the ASP may have
 * accepted the participation, so the reservation must stay until round state or
 * the stale-participation recovery proves otherwise.
 */
object ArkRefreshFailurePolicy {
    fun isDefinitive(message: String): Boolean {
        val text = message.trim().lowercase()
        if (text.isBlank()) return false
        // Transport failures can contain words like "expired" (TLS certificate),
        // "canceled" (request) or "rejected" (connection); never tombstone them.
        if (listOf(
                "certificate",
                "tls",
                "ssl",
                "handshake",
                "connection",
                "connect",
                "timeout",
                "timed out",
                "dns",
                "lookup",
                "network",
                "unreachable",
                "broken pipe",
                "eof",
            ).any { text.contains(it) }
        ) {
            return false
        }
        return listOf(
            "not spendable",
            "already spent",
            "vtxo not found",
            "unknown vtxo",
            "invalid vtxo",
            "not available",
            "not refreshable",
            "no vtxo",
            "no inputs",
            "round participation not found",
            "canceled",
            "cancelled",
            "expired",
            "expiry",
            "scheduled height",
            "failed by server",
            "failed to lock",
            "insufficient",
            "rejected",
        ).any { text.contains(it) }
    }
}
