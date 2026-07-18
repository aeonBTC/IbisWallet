package github.aeonbtc.ibiswallet.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import github.aeonbtc.ibiswallet.data.repository.SPARK_NETWORK_RECONNECT_DEBOUNCE_MS

/**
 * Observes validated default-network changes and notifies after a short debounce
 * so Spark can recycle its SDK session only when Android actually changes routes.
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
                scheduleIfValidatedChanged(network)
            }

            override fun onLost(network: Network) {
                if (activeValidatedNetwork != network) return
                activeValidatedNetwork = currentValidatedNetwork()
                if (activeValidatedNetwork != null) scheduleDebounced()
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities,
            ) {
                if (networkCapabilities.isValidatedInternet()) {
                    scheduleIfChanged(network)
                } else if (activeValidatedNetwork == network) {
                    activeValidatedNetwork = null
                }
            }
        }

    fun start() {
        if (isRegistered) return
        isRegistered = true
        activeValidatedNetwork = currentValidatedNetwork()
        connectivityManager.registerDefaultNetworkCallback(callback)
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

    private fun scheduleIfValidatedChanged(network: Network) {
        if (connectivityManager.getNetworkCapabilities(network)?.isValidatedInternet() != true) return
        scheduleIfChanged(network)
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
        connectivityManager.activeNetwork?.takeIf { network ->
            connectivityManager.getNetworkCapabilities(network)?.isValidatedInternet() == true
        }

    private fun NetworkCapabilities.isValidatedInternet(): Boolean =
        hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}
