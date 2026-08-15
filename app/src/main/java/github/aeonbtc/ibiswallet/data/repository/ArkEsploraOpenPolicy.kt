package github.aeonbtc.ibiswallet.data.repository

/**
 * Orders Esplora hosts for Bark open. Reachable (latency-sorted) first so a
 * hanging AAAA / slow configured host cannot serialize the whole enable path.
 */
object ArkEsploraOpenPolicy {
    const val MAX_HOSTS = 2

    fun orderForOpen(reachableFastestFirst: List<String>): List<String> =
        reachableFastestFirst
            .map { it.trim().trimEnd('/') }
            .filter { it.isNotBlank() }
            .distinct()
            .take(MAX_HOSTS)
}
