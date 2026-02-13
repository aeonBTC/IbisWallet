package github.aeonbtc.ibiswallet.tor

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.os.Build
import android.os.IBinder
import android.util.Log
import github.aeonbtc.ibiswallet.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.torproject.jni.TorService

/**
 * Manages the Tor service lifecycle and state
 */
class TorManager(private val context: Context) {
    
    companion object {
        private const val TAG = "TorManager"
        const val SOCKS_PORT = 9050
        const val SOCKS_HOST = "127.0.0.1"
    }
    
    private val _torState = MutableStateFlow(TorState())
    val torState: StateFlow<TorState> = _torState.asStateFlow()
    
    private var torService: TorService? = null
    private var isBound = false
    private var isReceiverRegistered = false
    
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            if (BuildConfig.DEBUG) Log.d(TAG, "Tor service connected")
            torService = (service as TorService.LocalBinder).service
            _torState.value = _torState.value.copy(
                status = TorStatus.CONNECTING,
                statusMessage = "Bootstrapping..."
            )
            // Status updates will come through the broadcast receiver
        }
        
        override fun onServiceDisconnected(name: ComponentName?) {
            if (BuildConfig.DEBUG) Log.d(TAG, "Tor service disconnected")
            torService = null
            _torState.value = _torState.value.copy(
                status = TorStatus.DISCONNECTED,
                statusMessage = "Disconnected"
            )
        }
    }
    
    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val status = intent?.getStringExtra(TorService.EXTRA_STATUS) ?: return
            if (BuildConfig.DEBUG) Log.d(TAG, "Tor status update: $status")
            
            val torStatus = when {
                // "ON" means Tor is fully connected and ready
                status.equals("ON", ignoreCase = true) -> TorStatus.CONNECTED
                status.contains("NOTICE Bootstrapped 100%", ignoreCase = true) -> TorStatus.CONNECTED
                status.equals("STARTING", ignoreCase = true) -> TorStatus.STARTING
                status.equals("STOPPING", ignoreCase = true) -> TorStatus.DISCONNECTED
                status.equals("OFF", ignoreCase = true) -> TorStatus.DISCONNECTED
                status.contains("Bootstrapped", ignoreCase = true) -> TorStatus.CONNECTING
                status.contains("WARN", ignoreCase = true) -> TorStatus.CONNECTING
                status.contains("ERR", ignoreCase = true) -> TorStatus.ERROR
                else -> _torState.value.status
            }
            
            val displayMessage = when (status) {
                "ON" -> "Connected"
                "OFF" -> "Disconnected"
                "STARTING" -> "Starting..."
                "STOPPING" -> "Stopping..."
                else -> status.take(100)
            }
            
            _torState.value = _torState.value.copy(
                status = torStatus,
                statusMessage = displayMessage
            )
        }
    }
    
    /**
     * Start the Tor service
     */
    fun start() {
        if (_torState.value.status == TorStatus.CONNECTED || 
            _torState.value.status == TorStatus.CONNECTING ||
            _torState.value.status == TorStatus.STARTING) {
            if (BuildConfig.DEBUG) Log.d(TAG, "Tor is already running or starting")
            return
        }
        
        if (BuildConfig.DEBUG) Log.d(TAG, "Starting Tor service")
        _torState.value = _torState.value.copy(
            status = TorStatus.STARTING,
            statusMessage = "Starting Tor..."
        )
        
        // Register status receiver
        val filter = IntentFilter(TorService.ACTION_STATUS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(statusReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(statusReceiver, filter)
        }
        isReceiverRegistered = true
        
        // Bind to Tor service
        val intent = Intent(context, TorService::class.java)
        context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        isBound = true
    }
    
    /**
     * Stop the Tor service
     */
    fun stop() {
        if (BuildConfig.DEBUG) Log.d(TAG, "Stopping Tor service")
        
        if (isBound) {
            try {
                context.unbindService(serviceConnection)
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.e(TAG, "Error unbinding Tor service", e)
            }
            isBound = false
        }
        
        if (isReceiverRegistered) {
            try {
                context.unregisterReceiver(statusReceiver)
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.e(TAG, "Error unregistering Tor status receiver", e)
            }
            isReceiverRegistered = false
        }
        
        torService = null
        _torState.value = TorState(
            status = TorStatus.DISCONNECTED,
            statusMessage = "Stopped"
        )
    }
    
    /**
     * Check if Tor is ready for use
     */
    fun isReady(): Boolean = _torState.value.status == TorStatus.CONNECTED
    
    /**
     * Wipe all Tor data from disk (relay descriptors, circuit state, cached
     * consensus, keys). Call stop() first to unbind the service, then delete
     * the data directory. Used during auto-wipe to eliminate forensic traces
     * of Tor usage.
     */
    fun wipeTorData() {
        // Stop Tor if running
        stop()
        // The tor-android library stores data in <filesDir>/app_torservice/
        try {
            val torDataDir = java.io.File(context.filesDir, "app_torservice")
            if (torDataDir.exists()) {
                torDataDir.deleteRecursively()
            }
            // Also check the cache directory for any Tor temp files
            val torCacheDir = java.io.File(context.cacheDir, "tor")
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
    val statusMessage: String = "Not started"
)

/**
 * Possible states of the Tor service
 */
enum class TorStatus {
    DISCONNECTED,
    STARTING,
    CONNECTING,
    CONNECTED,
    ERROR
}
