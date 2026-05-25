package github.aeonbtc.ibiswallet.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Handler
import android.os.Looper
import github.aeonbtc.ibiswallet.data.repository.SPARK_NETWORK_RECONNECT_DEBOUNCE_MS

/**
 * Observes default-network changes (including VPN IP switches) and notifies
 * after a short debounce so Spark can recycle its SDK session.
 */
class SparkNetworkMonitor(
    context: Context,
    private val onNetworkChanged: () -> Unit,
    debounceMs: Long = SPARK_NETWORK_RECONNECT_DEBOUNCE_MS,
    handler: Handler = Handler(Looper.getMainLooper()),
) {
    private val debounceMs: Long = debounceMs
    private val handler: Handler = handler
    private val appContext = context.applicationContext
    private val connectivityManager =
        appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private var activeValidatedNetwork: Network? = null
    private var pendingRunnable: Runnable? = null
    private var isRegistered = false

    private val callback =
        object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                scheduleIfChanged(network)
            }

            override fun onLost(network: Network) {
                if (activeValidatedNetwork == network) {
                    activeValidatedNetwork = null
                }
                scheduleDebounced()
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities,
            ) {
                if (!networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return
                if (!networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) return
                scheduleIfChanged(network)
            }
        }

    fun start() {
        if (isRegistered) return
        isRegistered = true
        activeValidatedNetwork = currentValidatedNetwork()
        connectivityManager.registerNetworkCallback(
            NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build(),
            callback,
        )
    }

    fun stop() {
        if (!isRegistered) return
        isRegistered = false
        pendingRunnable?.let { handler.removeCallbacks(it) }
        pendingRunnable = null
        runCatching { connectivityManager.unregisterNetworkCallback(callback) }
    }

    private fun scheduleIfChanged(network: Network) {
        if (activeValidatedNetwork == network) return
        activeValidatedNetwork = network
        scheduleDebounced()
    }

    private fun scheduleDebounced() {
        pendingRunnable?.let { handler.removeCallbacks(it) }
        val runnable =
            Runnable {
                pendingRunnable = null
                onNetworkChanged()
            }
        pendingRunnable = runnable
        handler.postDelayed(runnable, debounceMs)
    }

    private fun currentValidatedNetwork(): Network? =
        connectivityManager.allNetworks.firstOrNull { network ->
            connectivityManager.getNetworkCapabilities(network)?.let { capabilities ->
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            } == true
        }
}
