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

/**
 * Manages the Tor service lifecycle and state.
 *
 * This manager is app-scoped because both Layer 1 and Layer 2 bind the same
 * Tor Android service and should share a single lifecycle + state flow.
 */
class TorManager private constructor(context: Context) {
    companion object {
        private const val TAG = "TorManager"
        private const val SOCKS_PORT = 9050
        private const val SOCKS_PROBE_RETRIES = 4
        private const val SOCKS_PROBE_TIMEOUT_MS = 500
        private const val SOCKS_PROBE_INTERVAL_MS = 250L
        private const val TOR_STOP_SETTLE_MS = 2_000L

        @Volatile
        private var instance: TorManager? = null

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
    private var restartAfterStopRequested = false

    private val serviceConnection =
        object : ServiceConnection {
            override fun onServiceConnected(
                name: ComponentName?,
                service: IBinder?,
            ) {
                if (BuildConfig.DEBUG) Log.d(TAG, "Tor service connected")
                torService = (service as TorService.LocalBinder).service
                _torState.value =
                    _torState.value.copy(
                        status = TorStatus.CONNECTING,
                        statusMessage = "Bootstrapping...",
                    )
                // Status updates will come through the broadcast receiver
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
        if (_torState.value.status == TorStatus.CONNECTED ||
            _torState.value.status == TorStatus.CONNECTING ||
            _torState.value.status == TorStatus.STARTING
        ) {
            if (BuildConfig.DEBUG) Log.d(TAG, "Tor is already running or starting")
            return
        }
        if (_torState.value.status == TorStatus.STOPPING) {
            if (BuildConfig.DEBUG) Log.d(TAG, "Tor is stopping; queueing restart")
            restartAfterStopRequested = true
            return
        }

        stopTransitionJob?.cancel()
        stopTransitionJob = null
        restartAfterStopRequested = false

        if (BuildConfig.DEBUG) Log.d(TAG, "Starting Tor service")
        _torState.value =
            _torState.value.copy(
                status = TorStatus.STARTING,
                statusMessage = "Starting Tor...",
            )

        // Register status receiver
        val filter = IntentFilter(TorService.ACTION_STATUS)
        ContextCompat.registerReceiver(
            appContext,
            statusReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        isReceiverRegistered = true

        // Bind to Tor service
        val intent = Intent(appContext, TorService::class.java)
        appContext.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        isBound = true
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
        if (isReady()) return probeSocksProxy()

        val result = withTimeoutOrNull(timeoutMs) {
            _torState.first { state ->
                state.status == TorStatus.CONNECTED || state.status == TorStatus.ERROR
            }
        }

        if (result == null || result.status != TorStatus.CONNECTED) return false

        // SOCKS proxy may take a moment to start listening after the
        // control port reports "Bootstrapped 100%". Probe with retries.
        return probeSocksProxy()
    }

    /**
     * Try to open + immediately close a TCP connection to the local SOCKS
     * proxy. Retries up to 4 times with 250 ms between attempts (≤ 1 s).
     * Runs on [Dispatchers.IO] to avoid blocking the main thread.
     */
    private suspend fun probeSocksProxy(): Boolean = withContext(Dispatchers.IO) {
        repeat(SOCKS_PROBE_RETRIES) { attempt ->
            try {
                java.net.Socket().use { socket ->
                    socket.connect(
                        java.net.InetSocketAddress("127.0.0.1", SOCKS_PORT),
                        SOCKS_PROBE_TIMEOUT_MS,
                    )
                }
                return@withContext true
            } catch (_: Exception) {
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "SOCKS probe attempt ${attempt + 1}/$SOCKS_PROBE_RETRIES failed")
                }
                if (attempt < SOCKS_PROBE_RETRIES - 1) {
                    Thread.sleep(SOCKS_PROBE_INTERVAL_MS)
                }
            }
        }
        if (BuildConfig.DEBUG) Log.w(TAG, "SOCKS proxy not reachable after $SOCKS_PROBE_RETRIES probes")
        false
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
