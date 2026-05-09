package github.aeonbtc.ibiswallet.viewmodel

import android.os.SystemClock
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Shared ProcessLifecycleOwner bridge for viewmodels that need background /
 * foreground callbacks with connection-aware resume behavior.
 */
class AppLifecycleCoordinator(
    private val scope: CoroutineScope,
    private val onBackgrounded: () -> Boolean,
    private val onForegrounded: suspend (wasConnectedBeforeBackground: Boolean, backgroundDurationMs: Long) -> Unit,
) {
    private var wasConnectedBeforeBackground = false
    private var isInBackground = false
    private var backgroundTimestamp = 0L

    private val lifecycleObserver =
        object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                handleForegrounded()
            }

            override fun onStop(owner: LifecycleOwner) {
                handleBackgrounded()
            }
        }

    init {
        ProcessLifecycleOwner.get().lifecycle.addObserver(lifecycleObserver)
    }

    fun dispose() {
        ProcessLifecycleOwner.get().lifecycle.removeObserver(lifecycleObserver)
    }

    private fun handleBackgrounded() {
        if (isInBackground) return
        isInBackground = true
        backgroundTimestamp = SystemClock.elapsedRealtime()
        wasConnectedBeforeBackground = onBackgrounded()
    }

    private fun handleForegrounded() {
        if (!isInBackground) return
        isInBackground = false
        val backgroundDurationMs = SystemClock.elapsedRealtime() - backgroundTimestamp
        scope.launch {
            onForegrounded(wasConnectedBeforeBackground, backgroundDurationMs)
            wasConnectedBeforeBackground = false
        }
    }
}
