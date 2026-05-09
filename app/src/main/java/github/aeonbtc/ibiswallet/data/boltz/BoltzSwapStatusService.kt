package github.aeonbtc.ibiswallet.data.boltz

import github.aeonbtc.ibiswallet.data.model.BoltzSwapUpdate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Shared Boltz swap status fan-out with polling fallback.
 */
interface BoltzSwapUpdatesSource {
    fun subscribeToSwapUpdates(swapId: String): Flow<BoltzSwapUpdate>
}

class BoltzSwapStatusService(
    private val client: BoltzSwapUpdatesSource,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val updateFlows = mutableMapOf<String, MutableSharedFlow<BoltzSwapUpdate>>()
    private val subscriptionCounts = mutableMapOf<String, Int>()
    private val monitorJobs = mutableMapOf<String, Job>()
    private val idleCleanupJobs = mutableMapOf<String, Job>()
    private val lastFingerprints = mutableMapOf<String, String>()

    suspend fun awaitSwapActivity(
        swapId: String,
        timeoutMs: Long,
        previousUpdate: BoltzSwapUpdate? = null,
        pollStatus: (suspend () -> String?)? = null,
    ): BoltzSwapUpdate? {
        val trace = BoltzTraceContext(operation = "awaitSwapActivity", swapId = swapId)
        val startedAt = boltzTraceStart()
        val previousFingerprint = previousUpdate?.fingerprint()
        retainSwap(swapId)
        return try {
            val updates = flowForSwap(swapId)
            updates.replayCache.lastOrNull()
                ?.takeIf { it.fingerprint() != previousFingerprint }
                ?.let { replayed ->
                    logBoltzTrace(
                        "replay_hit",
                        trace,
                        "source" to "replay",
                        "elapsedMs" to boltzElapsedMs(startedAt),
                        "status" to replayed.status,
                        "txid" to replayed.transactionId,
                    )
                    return replayed
                }

            withTimeoutOrNull(timeoutMs) {
                updates.first { it.fingerprint() != previousFingerprint }
            }?.let { update ->
                logBoltzTrace(
                    "update",
                    trace,
                    "source" to "websocket",
                    "elapsedMs" to boltzElapsedMs(startedAt),
                    "status" to update.status,
                    "txid" to update.transactionId,
                )
                return update
            }

            pollStatus?.let { statusProvider ->
                val status =
                    runCatching { statusProvider() }
                        .onFailure { error ->
                            logBoltzTrace(
                                "poll_failed",
                                trace,
                                level = BoltzTraceLevel.WARN,
                                throwable = error,
                                "source" to "poll",
                                "elapsedMs" to boltzElapsedMs(startedAt),
                            )
                        }.getOrNull() ?: run {
                        logBoltzTrace(
                            "timeout",
                            trace,
                            "source" to "poll",
                            "elapsedMs" to boltzElapsedMs(startedAt),
                        )
                        return@let null
                    }
                val update = BoltzSwapUpdate(id = swapId, status = status)
                if (update.fingerprint() == previousFingerprint) {
                    logBoltzTrace(
                        "poll_unchanged",
                        trace,
                        "source" to "poll",
                        "elapsedMs" to boltzElapsedMs(startedAt),
                        "status" to status,
                    )
                    return@let null
                }
                emitIfChanged(update)
                logBoltzTrace(
                    "update",
                    trace,
                    "source" to "poll",
                    "elapsedMs" to boltzElapsedMs(startedAt),
                    "status" to status,
                )
                return update
            }

            logBoltzTrace(
                "timeout",
                trace,
                "source" to "websocket",
                "elapsedMs" to boltzElapsedMs(startedAt),
            )
            null
        } finally {
            releaseSwap(swapId)
        }
    }

    fun close() {
        scope.cancel()
    }

    private suspend fun retainSwap(swapId: String) = mutex.withLock {
        val resumedWarmMonitor =
            idleCleanupJobs.remove(swapId)
                ?.also { it.cancel() } != null
        subscriptionCounts[swapId] = (subscriptionCounts[swapId] ?: 0) + 1
        if (resumedWarmMonitor) {
            logBoltzTrace(
                "monitor_resume",
                BoltzTraceContext(operation = "retainSwap", swapId = swapId),
                "activeSubscribers" to subscriptionCounts[swapId],
            )
        }
        if (monitorJobs[swapId]?.isActive != true) {
            monitorJobs[swapId]?.cancel()
            logBoltzTrace(
                "monitor_start",
                BoltzTraceContext(operation = "retainSwap", swapId = swapId),
                "activeSubscribers" to subscriptionCounts[swapId],
            )
            monitorJobs[swapId] = scope.launch {
                client.subscribeToSwapUpdates(swapId)
                    .catch { error ->
                        logBoltzTrace(
                            "monitor_failed",
                            BoltzTraceContext(operation = "retainSwap", swapId = swapId),
                            level = BoltzTraceLevel.WARN,
                            throwable = error,
                            "retryDelayMs" to RETRY_DELAY_MS,
                        )
                        delay(RETRY_DELAY_MS)
                    }
                    .collect { emitIfChanged(it) }
            }
        }
    }

    private suspend fun releaseSwap(swapId: String) = mutex.withLock {
        val nextCount = (subscriptionCounts[swapId] ?: 1) - 1
        if (nextCount > 0) {
            subscriptionCounts[swapId] = nextCount
            return@withLock
        }
        subscriptionCounts.remove(swapId)
        idleCleanupJobs.remove(swapId)?.cancel()
        idleCleanupJobs[swapId] =
            scope.launch {
                delay(IDLE_MONITOR_GRACE_MS)
                mutex.withLock {
                    if ((subscriptionCounts[swapId] ?: 0) > 0) {
                        idleCleanupJobs.remove(swapId)
                        return@withLock
                    }
                    logBoltzTrace(
                        "monitor_stop",
                        BoltzTraceContext(operation = "releaseSwap", swapId = swapId),
                    )
                    idleCleanupJobs.remove(swapId)
                    monitorJobs.remove(swapId)?.cancel()
                    updateFlows.remove(swapId)
                    lastFingerprints.remove(swapId)
                }
            }
    }

    private suspend fun emitIfChanged(update: BoltzSwapUpdate) {
        val fingerprint = update.fingerprint()
        val flow = mutex.withLock {
            val last = lastFingerprints[update.id]
            if (last == fingerprint) {
                return
            }
            lastFingerprints[update.id] = fingerprint
            updateFlows.getOrPut(update.id) {
                MutableSharedFlow(
                    replay = 1,
                    extraBufferCapacity = 16,
                )
            }
        }
        flow.emit(update)
    }

    private suspend fun flowForSwap(swapId: String): MutableSharedFlow<BoltzSwapUpdate> = mutex.withLock {
        updateFlows.getOrPut(swapId) {
            MutableSharedFlow(
                replay = 1,
                extraBufferCapacity = 16,
            )
        }
    }

    private companion object {
        private const val RETRY_DELAY_MS = 1_500L
        private const val IDLE_MONITOR_GRACE_MS = 20_000L
    }
}

private fun BoltzSwapUpdate.fingerprint(): String {
    return "${status}|${transactionId.orEmpty()}|${transactionHex.orEmpty()}"
}
