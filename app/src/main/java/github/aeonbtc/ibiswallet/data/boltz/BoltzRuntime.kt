package github.aeonbtc.ibiswallet.data.boltz

import github.aeonbtc.ibiswallet.data.model.BoltzPairInfo
import github.aeonbtc.ibiswallet.data.model.SwapDirection
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import lwk.BoltzSession

/**
 * Keeps reusable Boltz metadata and session warmup off the user-action hot path.
 */
class BoltzRuntime(
    private val metadataTtlMs: Long,
    private val ensureTorReady: suspend () -> Unit,
    private val ensureSession: suspend () -> BoltzSession,
    private val fetchReversePairInfo: suspend () -> BoltzPairInfo,
    private val fetchSubmarinePairInfo: suspend () -> BoltzPairInfo,
    private val fetchChainPairInfo: suspend (SwapDirection) -> BoltzPairInfo,
) {
    private data class CachedPair(
        val fetchedAt: Long,
        val pair: BoltzPairInfo,
    )

    private val mutex = Mutex()
    private var reversePairCache: CachedPair? = null
    private var submarinePairCache: CachedPair? = null
    private val chainPairCache = mutableMapOf<SwapDirection, CachedPair>()

    suspend fun invalidate() = mutex.withLock {
        reversePairCache = null
        submarinePairCache = null
        chainPairCache.clear()
    }

    suspend fun prewarmChainContext(direction: SwapDirection) {
        val trace = BoltzTraceContext(operation = "prewarmChainContext")
        val startedAt = boltzTraceStart()
        logBoltzTrace("start", trace, "direction" to direction)
        try {
            getChainPairInfo(direction)
            ensureSession()
            logBoltzTrace(
                "success",
                trace,
                "elapsedMs" to boltzElapsedMs(startedAt),
                "direction" to direction,
            )
        } catch (error: Exception) {
            logBoltzTrace(
                "failed",
                trace,
                level = BoltzTraceLevel.WARN,
                throwable = error,
                "elapsedMs" to boltzElapsedMs(startedAt),
                "direction" to direction,
            )
        }
    }

    suspend fun prewarmLightningContext() {
        val trace = BoltzTraceContext(operation = "prewarmLightningContext")
        val startedAt = boltzTraceStart()
        logBoltzTrace("start", trace)
        try {
            getReversePairInfo()
            getSubmarinePairInfo()
            ensureSession()
            logBoltzTrace("success", trace, "elapsedMs" to boltzElapsedMs(startedAt))
        } catch (error: Exception) {
            logBoltzTrace(
                "failed",
                trace,
                level = BoltzTraceLevel.WARN,
                throwable = error,
                "elapsedMs" to boltzElapsedMs(startedAt),
            )
        }
    }

    suspend fun getReversePairInfo(): BoltzPairInfo {
        return getCachedPair(
            operation = "getReversePairInfo",
            current = { reversePairCache },
            update = { reversePairCache = it },
            fetch = fetchReversePairInfo,
        )
    }

    suspend fun getSubmarinePairInfo(): BoltzPairInfo {
        return getCachedPair(
            operation = "getSubmarinePairInfo",
            current = { submarinePairCache },
            update = { submarinePairCache = it },
            fetch = fetchSubmarinePairInfo,
        )
    }

    suspend fun getChainPairInfo(direction: SwapDirection): BoltzPairInfo {
        return getCachedPair(
            operation = "getChainPairInfo",
            current = { chainPairCache[direction] },
            update = { chainPairCache[direction] = it },
            fetch = { fetchChainPairInfo(direction) },
        )
    }

    private suspend fun getCachedPair(
        operation: String,
        current: () -> CachedPair?,
        update: (CachedPair) -> Unit,
        fetch: suspend () -> BoltzPairInfo,
    ): BoltzPairInfo {
        val trace = BoltzTraceContext(operation = operation)
        val now = System.currentTimeMillis()
        mutex.withLock {
            current()?.takeIf { now - it.fetchedAt <= metadataTtlMs }?.let {
                logBoltzTrace(
                    "cache_hit",
                    trace,
                    "cache" to "hit",
                    "ageMs" to (now - it.fetchedAt),
                )
                return it.pair
            }
        }

        val startedAt = boltzTraceStart()
        logBoltzTrace("cache_miss", trace, "cache" to "miss")
        ensureTorReady()
        val pair =
            try {
                fetch()
            } catch (error: Exception) {
                logBoltzTrace(
                    "fetch_failed",
                    trace,
                    level = BoltzTraceLevel.WARN,
                    throwable = error,
                    "cache" to "miss",
                    "elapsedMs" to boltzElapsedMs(startedAt),
                )
                throw error
            }

        mutex.withLock {
            update(CachedPair(System.currentTimeMillis(), pair))
        }
        logBoltzTrace(
            "fetch_success",
            trace,
            "cache" to "miss",
            "elapsedMs" to boltzElapsedMs(startedAt),
        )
        return pair
    }
}
