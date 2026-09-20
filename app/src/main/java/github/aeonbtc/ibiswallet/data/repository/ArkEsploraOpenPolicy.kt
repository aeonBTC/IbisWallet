package github.aeonbtc.ibiswallet.data.repository

/**
 * Orders Esplora hosts for Bark open. The configured host stays first when
 * reachable; remaining slots are fastest fallbacks so a hanging AAAA / dead
 * configured host cannot serialize the whole enable path.
 *
 * Invariant: [orderForOpen] input must already be preflight-reachable,
 * fastest-first ([ArkRepository.probeArkOpenEndpoints]). Truncation to
 * [MAX_HOSTS] therefore only drops slower reachable hosts — never an
 * unprobed one. Callers passing unprobed hosts must bump the cap or filter
 * first, or preferred+lastGood could crowd out defaults.
 */
object ArkEsploraOpenPolicy {
    const val MAX_HOSTS = 2

    fun orderForOpen(
        reachableFastestFirst: List<String>,
        preferred: String? = null,
    ): List<String> {
        val normalized =
            reachableFastestFirst
                .map { it.trim().trimEnd('/') }
                .filter { it.isNotBlank() }
                .distinct()
        val pref = preferred?.trim()?.trimEnd('/')?.takeIf { it.isNotBlank() }
        val preferredReachable =
            pref?.let { wanted ->
                normalized.firstOrNull { it.equals(wanted, ignoreCase = true) }
            }
        val rest = normalized.filterNot { it.equals(preferredReachable, ignoreCase = true) }
        return (listOfNotNull(preferredReachable) + rest).take(MAX_HOSTS)
    }

    fun shouldNotifyFallback(
        preferred: String?,
        active: String?,
    ): Boolean {
        val pref = preferred?.trim()?.trimEnd('/')
        val act = active?.trim()?.trimEnd('/')
        if (pref.isNullOrBlank() || act.isNullOrBlank()) return false
        return !pref.equals(act, ignoreCase = true)
    }
}
