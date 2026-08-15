package github.aeonbtc.ibiswallet.tor

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import androidx.core.content.ContextCompat
import github.aeonbtc.ibiswallet.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.torproject.jni.TorService
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicInteger

/**
 * Manages the Tor service lifecycle and state.
 *
 * This manager is app-scoped because both Layer 1 and Layer 2 bind the same
 * Tor Android service and should share a single lifecycle + state flow.
 */
class TorManager private constructor(context: Context) {
    companion object {
        private const val TAG = "TorManager"
        private const val DEFAULT_SOCKS_PORT = 9050
        private const val SOCKS_PROBE_RETRIES = 8
        private const val SOCKS_PROBE_TIMEOUT_MS = 500
        private const val SOCKS_PROBE_INTERVAL_MS = 250L
        private const val CONTROL_POLL_INTERVAL_MS = 500L
        private const val TOR_STOP_SETTLE_MS = 2_000L
        private const val STUCK_START_RESET_MS = 90_000L

        @Volatile
        private var instance: TorManager? = null

        /**
         * Last known local Tor SOCKS port. Prefer 9050; falls back to whatever
         * [TorService.getSocksPort] reports when 9050 was unavailable / auto.
         */
        private val socksPortRef = AtomicInteger(DEFAULT_SOCKS_PORT)

        fun socksPort(): Int = socksPortRef.get().coerceAtLeast(1)

        fun getInstance(context: Context): TorManager {
            return instance ?: synchronized(this) {
                instance ?: TorManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val appContextRef = WeakReference(context.applicationContext)
    private val managerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _torState = MutableStateFlow(TorState())
    val torState: StateFlow<TorState> = _torState.asStateFlow()

    private var torService: TorService? = null
    private var isBound = false
    private var isReceiverRegistered = false
    private var stopTransitionJob: Job? = null
    private var stuckStartWatchdogJob: Job? = null
    private var restartAfterStopRequested = false
    private var startGeneration = 0

    private val serviceConnection =
        object : ServiceConnection {
            override fun onServiceConnected(
                name: ComponentName?,
                service: IBinder?,
            ) {
                if (BuildConfig.DEBUG) Log.d(TAG, "Tor service connected")
                val bound = (service as TorService.LocalBinder).service
                torService = bound
                captureSocksPort(bound)
                // Service may already be fully up (reconnect / retained process).
                // Prefer control-port readiness: SOCKS alone can listen before circuits exist.
                if (isNetworkReadyBlocking(bound)) {
                    markConnected("Connected")
                } else if (_torState.value.status != TorStatus.CONNECTED) {
                    _torState.value =
                        _torState.value.copy(
                            status = TorStatus.CONNECTING,
                            statusMessage = "Bootstrapping...",
                        )
                }
                // Status updates will come through the broadcast receiver / awaitReady probes
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                if (BuildConfig.DEBUG) Log.d(TAG, "Tor service disconnected")
                torService = null
                if (_torState.value.status == TorStatus.STOPPING) {
                    return
                }
                _torState.value =
                    _torState.value.copy(
                        status = TorStatus.DISCONNECTED,
                        statusMessage = "Disconnected",
                    )
            }
        }

    private val statusReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(
                context: Context?,
                intent: Intent?,
            ) {
                if (intent?.action == TorService.ACTION_ERROR) {
                    val detail =
                        intent.getStringExtra(Intent.EXTRA_TEXT)?.take(100)
                            ?: "Tor error"
                    if (BuildConfig.DEBUG) Log.e(TAG, "Tor error broadcast: $detail")
                    _torState.value =
                        TorState(
                            status = TorStatus.ERROR,
                            statusMessage = detail,
                        )
                    return
                }

                val status = intent?.getStringExtra(TorService.EXTRA_STATUS) ?: return
                if (BuildConfig.DEBUG) Log.d(TAG, "Tor status update: $status")

                val torStatus =
                    when {
                        // "ON" means Tor is fully connected and ready
                        status.equals("ON", ignoreCase = true) -> TorStatus.CONNECTED
                        status.contains("NOTICE Bootstrapped 100%", ignoreCase = true) -> TorStatus.CONNECTED
                        status.equals("STARTING", ignoreCase = true) -> TorStatus.STARTING
                        status.equals("STOPPING", ignoreCase = true) -> TorStatus.STOPPING
                        status.equals("OFF", ignoreCase = true) -> TorStatus.DISCONNECTED
                        status.contains("Bootstrapped", ignoreCase = true) -> TorStatus.CONNECTING
                        status.contains("WARN", ignoreCase = true) -> TorStatus.CONNECTING
                        status.contains("ERR", ignoreCase = true) -> TorStatus.ERROR
                        else -> _torState.value.status
                    }

                val displayMessage =
                    when (status) {
                        "ON" -> "Connected"
                        "OFF" -> "Disconnected"
                        "STARTING" -> "Starting..."
                        "STOPPING" -> "Stopping..."
                        else -> status.take(100)
                    }

                _torState.value =
                    _torState.value.copy(
                        status = torStatus,
                        statusMessage = displayMessage,
                    )

                if (torStatus == TorStatus.CONNECTED) {
                    captureSocksPort(torService)
                    cancelStuckStartWatchdog()
                }
            }
        }

    /**
     * Start the Tor service. Synchronized to prevent concurrent bindService()
     * calls which cause the native tor_run_main to be invoked twice, crashing
     * with SIGABRT in hs_circuitmap_init.
     */
    @Synchronized
    fun start() {
        val appContext = appContextRef.get() ?: return
        if (_torState.value.status == TorStatus.CONNECTED) {
            if (BuildConfig.DEBUG) Log.d(TAG, "Tor is already connected")
            return
        }
        if (_torState.value.status == TorStatus.CONNECTING ||
            _torState.value.status == TorStatus.STARTING
        ) {
            if (isBound && isReceiverRegistered) {
                if (BuildConfig.DEBUG) Log.d(TAG, "Tor is already running or starting")
                return
            }
            // Recover from a half-started lifecycle (receiver unbound, service retained, etc.)
            if (BuildConfig.DEBUG) Log.w(TAG, "Tor start state stuck without binding; resetting")
            forceResetLocked(appContext)
        }
        if (_torState.value.status == TorStatus.STOPPING) {
            if (BuildConfig.DEBUG) Log.d(TAG, "Tor is stopping; queueing restart")
            restartAfterStopRequested = true
            return
        }

        stopTransitionJob?.cancel()
        stopTransitionJob = null
        restartAfterStopRequested = false
        startGeneration += 1
        val generation = startGeneration

        if (BuildConfig.DEBUG) Log.d(TAG, "Starting Tor service")
        _torState.value =
            _torState.value.copy(
                status = TorStatus.STARTING,
                statusMessage = "Starting Tor...",
            )

        // Register status + error receivers
        if (!isReceiverRegistered) {
            val filter =
                IntentFilter().apply {
                    addAction(TorService.ACTION_STATUS)
                    addAction(TorService.ACTION_ERROR)
                }
            ContextCompat.registerReceiver(
                appContext,
                statusReceiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            isReceiverRegistered = true
        }

        // Bind to Tor service
        if (!isBound) {
            val intent = Intent(appContext, TorService::class.java)
            appContext.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
            isBound = true
        }

        armStuckStartWatchdog(generation)
    }

    /**
     * Stop the Tor service. Synchronized to prevent races with start().
     */
    @Synchronized
    fun stop() {
        val appContext = appContextRef.get()
        if (_torState.value.status == TorStatus.STOPPING) {
            if (BuildConfig.DEBUG) Log.d(TAG, "Tor is already stopping")
            return
        }
        if (BuildConfig.DEBUG) Log.d(TAG, "Stopping Tor service")

        cancelStuckStartWatchdog()
        startGeneration += 1

        _torState.value =
            _torState.value.copy(
                status = TorStatus.STOPPING,
                statusMessage = "Stopping...",
            )

        if (isBound && appContext != null) {
            try {
                appContext.unbindService(serviceConnection)
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.e(TAG, "Error unbinding Tor service", e)
            }
            isBound = false
        }

        if (isReceiverRegistered && appContext != null) {
            try {
                appContext.unregisterReceiver(statusReceiver)
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.e(TAG, "Error unregistering Tor status receiver", e)
            }
            isReceiverRegistered = false
        }

        torService = null
        stopTransitionJob?.cancel()
        stopTransitionJob =
            managerScope.launch {
                delay(TOR_STOP_SETTLE_MS)
                var shouldRestart = false
                synchronized(this@TorManager) {
                    if (_torState.value.status == TorStatus.STOPPING) {
                        _torState.value =
                            TorState(
                                status = TorStatus.DISCONNECTED,
                                statusMessage = "Stopped",
                            )
                    }
                    shouldRestart = restartAfterStopRequested
                    restartAfterStopRequested = false
                    stopTransitionJob = null
                }
                if (shouldRestart) {
                    start()
                }
            }
    }

    /**
     * Check if Tor is ready for use
     */
    fun isReady(): Boolean = _torState.value.status == TorStatus.CONNECTED

    /**
     * Suspend until Tor reaches CONNECTED state, or return false on
     * timeout / ERROR. Uses StateFlow.first() so it reacts immediately
     * to state changes (no polling). After Tor reports CONNECTED, probes
     * the SOCKS proxy port to confirm it is accepting connections — this
     * closes the race window where the control port signals "ready" but
     * the SOCKS listener hasn't started yet.
     *
     * @param timeoutMs Maximum time to wait for bootstrap + proxy readiness.
     * @return true if Tor is ready and the SOCKS proxy is accepting connections.
     */
    suspend fun awaitReady(timeoutMs: Long = 60_000): Boolean {
        // Already bootstrapped — just verify the SOCKS proxy.
        if (isReady()) {
            val ok = probeSocksProxy()
            if (ok) return true
            // Reported ready but proxy gone (killed process); force a restart path.
            if (BuildConfig.DEBUG) Log.w(TAG, "Tor marked connected but SOCKS unreachable; restarting")
            stop()
            start()
        }

        val deadline = System.nanoTime() + timeoutMs * 1_000_000L
        while (System.nanoTime() < deadline) {
            when (_torState.value.status) {
                TorStatus.CONNECTED -> {
                    val ready = probeSocksProxy()
                    if (ready) return true
                    if (BuildConfig.DEBUG) {
                        Log.w(TAG, "Tor connected status but SOCKS not reachable on ${socksPort()}")
                    }
                    return false
                }
                TorStatus.ERROR -> return false
                TorStatus.STARTING, TorStatus.CONNECTING -> {
                    // tor-android 0.4.9.6 may never broadcast STATUS_ON (listens CIRC,
                    // expects STATUS_CLIENT). Recover via control port + SOCKS.
                    if (isNetworkReady()) {
                        markConnected("Connected")
                        return true
                    }
                    // Advance status text from control bootstrap progress when available.
                    updateBootstrapMessageFromControl()
                    val remainingMs = ((deadline - System.nanoTime()) / 1_000_000L).coerceAtLeast(0L)
                    if (remainingMs == 0L) break
                    val waitMs = minOf(CONTROL_POLL_INTERVAL_MS, remainingMs)
                    val baseline = _torState.value
                    withTimeoutOrNull(waitMs) {
                        _torState.first { it != baseline }
                    }
                }
                TorStatus.DISCONNECTED, TorStatus.STOPPING -> {
                    val remainingMs = ((deadline - System.nanoTime()) / 1_000_000L).coerceAtLeast(0L)
                    if (remainingMs == 0L) break
                    val baseline = _torState.value
                    withTimeoutOrNull(remainingMs) {
                        _torState.first { it != baseline }
                    }
                }
            }
        }

        // Timed out while stuck in bootstrap — clear sticky STARTING/CONNECTING so the
        // next start() is not a no-op ("hangs forever on Bootstrapping").
        val stuck =
            _torState.value.status == TorStatus.STARTING ||
                _torState.value.status == TorStatus.CONNECTING
        if (stuck) {
            if (BuildConfig.DEBUG) Log.w(TAG, "Tor bootstrap timed out; resetting")
            stop()
        }
        return false
    }

    /**
     * Try to open + immediately close a TCP connection to the local SOCKS
     * proxy. Retries with short gaps. Runs on [Dispatchers.IO].
     */
    private suspend fun probeSocksProxy(maxAttempts: Int = SOCKS_PROBE_RETRIES): Boolean =
        withContext(Dispatchers.IO) {
            repeat(maxAttempts) { attempt ->
                if (probeSocksPortBlocking(socksPort())) {
                    return@withContext true
                }
                if (BuildConfig.DEBUG && maxAttempts > 1) {
                    Log.d(TAG, "SOCKS probe attempt ${attempt + 1}/$maxAttempts failed (port ${socksPort()})")
                }
                if (attempt < maxAttempts - 1) {
                    delay(SOCKS_PROBE_INTERVAL_MS)
                }
            }
            if (BuildConfig.DEBUG && maxAttempts > 1) {
                Log.w(TAG, "SOCKS proxy not reachable after $maxAttempts probes")
            }
            false
        }

    private fun probeSocksPortBlocking(port: Int): Boolean {
        if (port <= 0) return false
        return try {
            java.net.Socket().use { socket ->
                socket.connect(
                    java.net.InetSocketAddress("127.0.0.1", port),
                    SOCKS_PROBE_TIMEOUT_MS,
                )
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    private suspend fun isNetworkReady(): Boolean =
        withContext(Dispatchers.IO) {
            isNetworkReadyBlocking(torService)
        }

    /**
     * True when SOCKS accepts connections AND Tor reports a finished bootstrap
     * (or an established circuit). SOCKS alone is not enough — it listens early.
     *
     * Needed because tor-android 0.4.9.6 can leave [TorService.STATUS_ON] forever
     * unbroadcast when control-event wiring mismatches the daemon event stream.
     */
    private fun isNetworkReadyBlocking(service: TorService?): Boolean {
        if (service == null) return false
        captureSocksPort(service)
        val socksOk =
            probeSocksPortBlocking(socksPort()) ||
                (service.socksPort > 0 && probeSocksPortBlocking(service.socksPort))
        if (!socksOk) return false

        val control = runCatching { service.torControlConnection }.getOrNull() ?: return false
        return try {
            val circuit = control.getInfo("status/circuit-established")?.trim()
            if (circuit == "1") return true

            val bootstrap = control.getInfo("status/bootstrap-phase").orEmpty()
            bootstrapContainsDone(bootstrap)
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.d(TAG, "Control readiness probe failed: ${e.message}")
            false
        }
    }

    private fun bootstrapContainsDone(bootstrap: String): Boolean {
        if (bootstrap.isBlank()) return false
        // Typical: "NOTICE BOOTSTRAP PROGRESS=100 TAG=done SUMMARY=\"Done\""
        if (bootstrap.contains("PROGRESS=100", ignoreCase = true)) return true
        if (bootstrap.contains("TAG=done", ignoreCase = true)) return true
        if (bootstrap.contains("Bootstrapped 100%", ignoreCase = true)) return true
        return false
    }

    private fun updateBootstrapMessageFromControl() {
        val service = torService ?: return
        val control = runCatching { service.torControlConnection }.getOrNull() ?: return
        val bootstrap =
            runCatching { control.getInfo("status/bootstrap-phase") }
                .getOrNull()
                ?.trim()
                .orEmpty()
        if (bootstrap.isBlank()) return

        val progress =
            Regex("""PROGRESS=(\d+)""", RegexOption.IGNORE_CASE)
                .find(bootstrap)
                ?.groupValues
                ?.getOrNull(1)
        val summary =
            Regex("""SUMMARY="([^"]*)"""", RegexOption.IGNORE_CASE)
                .find(bootstrap)
                ?.groupValues
                ?.getOrNull(1)
                ?.take(60)

        val message =
            when {
                progress != null && summary != null -> "Bootstrapped $progress% — $summary"
                progress != null -> "Bootstrapped $progress%"
                else -> bootstrap.take(100)
            }

        val current = _torState.value
        if (current.status == TorStatus.STARTING || current.status == TorStatus.CONNECTING) {
            if (current.statusMessage != message) {
                _torState.value =
                    current.copy(
                        status = TorStatus.CONNECTING,
                        statusMessage = message,
                    )
            }
        }
    }

    private fun markConnected(message: String) {
        captureSocksPort(torService)
        cancelStuckStartWatchdog()
        if (_torState.value.status != TorStatus.CONNECTED ||
            _torState.value.statusMessage != message
        ) {
            _torState.value =
                TorState(
                    status = TorStatus.CONNECTED,
                    statusMessage = message,
                )
        }
    }

    private fun captureSocksPort(service: TorService?) {
        if (service == null) return
        try {
            val port = service.socksPort
            if (port > 0) {
                socksPortRef.set(port)
                if (BuildConfig.DEBUG && port != DEFAULT_SOCKS_PORT) {
                    Log.d(TAG, "Tor SOCKS listening on non-default port $port")
                }
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Unable to read Tor SOCKS port", e)
        }
    }

    private fun armStuckStartWatchdog(generation: Int) {
        stuckStartWatchdogJob?.cancel()
        stuckStartWatchdogJob =
            managerScope.launch {
                delay(STUCK_START_RESET_MS)
                synchronized(this@TorManager) {
                    if (generation != startGeneration) return@synchronized
                    val status = _torState.value.status
                    if (status == TorStatus.STARTING || status == TorStatus.CONNECTING) {
                        if (BuildConfig.DEBUG) {
                            Log.w(TAG, "Tor still $status after ${STUCK_START_RESET_MS}ms; forcing stop")
                        }
                        // stop() increments generation and clears binding.
                        stop()
                    }
                }
            }
    }

    private fun cancelStuckStartWatchdog() {
        stuckStartWatchdogJob?.cancel()
        stuckStartWatchdogJob = null
    }

    /** Drop binding/receiver without the STOPPING settle delay (internal recovery). */
    private fun forceResetLocked(appContext: Context) {
        cancelStuckStartWatchdog()
        try {
            if (isBound) {
                appContext.unbindService(serviceConnection)
            }
        } catch (_: Exception) {
        }
        isBound = false
        try {
            if (isReceiverRegistered) {
                appContext.unregisterReceiver(statusReceiver)
            }
        } catch (_: Exception) {
        }
        isReceiverRegistered = false
        torService = null
        stopTransitionJob?.cancel()
        stopTransitionJob = null
        restartAfterStopRequested = false
        _torState.value =
            TorState(
                status = TorStatus.DISCONNECTED,
                statusMessage = "Reset",
            )
    }

    /**
     * Wipe all Tor data from disk (relay descriptors, circuit state, cached
     * consensus, keys). Call stop() first to unbind the service, then delete
     * the data directory. Used during auto-wipe to eliminate forensic traces
     * of Tor usage.
     */
    fun wipeTorData() {
        val appContext = appContextRef.get() ?: return
        // Stop Tor if running
        stop()
        // The tor-android library stores data in <filesDir>/app_torservice/
        try {
            val torDataDir = java.io.File(appContext.filesDir, "app_torservice")
            if (torDataDir.exists()) {
                torDataDir.deleteRecursively()
            }
            // Also check the cache directory for any Tor temp files
            val torCacheDir = java.io.File(appContext.cacheDir, "tor")
            if (torCacheDir.exists()) {
                torCacheDir.deleteRecursively()
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Error wiping Tor data: ${e.message}")
        }
    }
}

/**
 * Represents the current state of the Tor service
 */
data class TorState(
    val status: TorStatus = TorStatus.DISCONNECTED,
    val statusMessage: String = "Not started",
)

/**
 * Possible states of the Tor service
 */
enum class TorStatus {
    DISCONNECTED,
    STOPPING,
    STARTING,
    CONNECTING,
    CONNECTED,
    ERROR,
}
