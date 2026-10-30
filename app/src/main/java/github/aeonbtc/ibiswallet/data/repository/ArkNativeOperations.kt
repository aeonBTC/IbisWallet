package github.aeonbtc.ibiswallet.data.repository

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

/** Native calls outlive a cancelled UniFFI await. Keep ownership until they actually return. */
internal class ArkNativeOperations {
    private class Lease(val owner: Any) : AbstractCoroutineContextElement(Key) {
        @Volatile var active = true
        companion object Key : CoroutineContext.Key<Lease>
    }
    private val mutex = Mutex()
    private val counts = mutableMapOf<Any, Int>()
    private val retiring = mutableSetOf<Any>()
    private val paused = mutableSetOf<Any>()

    suspend fun <T> run(owner: Any, block: suspend () -> T): T {
        val inherited = coroutineContext[Lease]
        mutex.withLock {
            val accepted = inherited?.owner === owner && inherited.active
            check(accepted || (owner !in retiring && owner !in paused)) {
                "Ark session is retiring or taking a snapshot"
            }
            counts[owner] = (counts[owner] ?: 0) + 1
        }
        val lease = Lease(owner)
        return withContext(NonCancellable + lease) {
            try {
                block()
            } finally {
                lease.active = false
                mutex.withLock {
                    val remaining = (counts[owner] ?: 1) - 1
                    if (remaining == 0) counts.remove(owner) else counts[owner] = remaining
                }
            }
        }
    }

    suspend fun retire(owner: Any) = mutex.withLock { retiring.add(owner) }

    suspend fun resume(owner: Any) = mutex.withLock { retiring.remove(owner) }

    suspend fun <T> snapshot(owner: Any, block: suspend () -> T): T {
        val currentLease = coroutineContext[Lease]?.takeIf { it.owner === owner && it.active }
        val retained = if (currentLease == null) 0 else 1
        mutex.withLock {
            check(owner !in retiring && owner !in paused)
            paused.add(owner)
        }
        try {
            if (retained > 0) {
                check(mutex.withLock { (counts[owner] ?: 0) == retained }) {
                    "Other Ark native operations are still running"
                }
            }
            while (mutex.withLock { (counts[owner] ?: 0) > retained }) delay(25)
            return withContext(NonCancellable) { block() }
        } finally {
            withContext(NonCancellable) { mutex.withLock { paused.remove(owner) } }
        }
    }

    suspend fun isBusy(owner: Any): Boolean = mutex.withLock { (counts[owner] ?: 0) > 0 || owner in paused }

    suspend fun <T> exclusiveCheckpoint(owner: Any, block: suspend () -> T): T {
        val lease = coroutineContext[Lease]
        check(lease?.owner === owner && lease.active)
        mutex.withLock {
            check(owner !in retiring && owner !in paused && counts[owner] == 1)
            paused.add(owner)
        }
        try {
            return withContext(NonCancellable) { block() }
        } finally {
            withContext(NonCancellable) { mutex.withLock { paused.remove(owner) } }
        }
    }

    suspend fun forget(owner: Any) = mutex.withLock {
        check((counts[owner] ?: 0) == 0)
        retiring.remove(owner)
    }
}
