package github.aeonbtc.ibiswallet.data

import android.util.Log
import github.aeonbtc.ibiswallet.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import github.aeonbtc.ibiswallet.util.TofuTrustManager

/**
 * Service for querying Electrum server relay fee via JSON-RPC
 * Used to determine minimum acceptable fee rate for sub-sat precision support
 * 
 * Uses blockchain.relayfee() which returns the minimum fee a low-priority 
 * transaction must pay to be accepted to the daemon's memory pool.
 * The result is in BTC per kilobyte, which we convert to sat/vB.
 */
class ElectrumFeatureService {
    
    companion object {
        private const val TAG = "ElectrumFeatureService"
        private const val TIMEOUT_MS = 15000L
        private const val TOR_TIMEOUT_MS = 30000L
        private const val TOR_PROXY_HOST = "127.0.0.1"
        private const val TOR_PROXY_PORT = 9050
        
        // Default minimum fee rate (1.0 sat/vB) if server doesn't specify or returns error
        const val DEFAULT_MIN_FEE_RATE = 1.0
        
        // Conversion factor: BTC/kB to sat/vB
        // 1 BTC = 100,000,000 sats
        // 1 kB = 1000 vB (approximately, since kB is used for legacy size)
        // So BTC/kB * 100,000,000 / 1000 = sat/vB * 100,000
        // Or simpler: BTC/kB * 100,000 = sat/vB
        private const val BTC_PER_KB_TO_SAT_PER_VB = 100_000.0
        
        /**
         * Create an SSL socket factory that accepts all certificates.
         * Used as fallback for .onion connections where Tor provides transport security.
         * For clearnet servers, prefer using a TofuTrustManager-backed factory.
         */
        internal fun createTrustAllSSLSocketFactory(): SSLSocketFactory {
            val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            })
            
            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(null, trustAllCerts, SecureRandom())
            return sslContext.socketFactory
        }
    }
    
    /**
     * Query the Electrum server for its minimum relay fee using blockchain.relayfee()
     * 
     * @param host Server hostname
     * @param port Server port
     * @param useSSL Whether to use SSL/TLS
     * @param useTorProxy Whether to route through Tor SOCKS proxy
     * @return Minimum acceptable fee rate in sat/vB, or DEFAULT_MIN_FEE_RATE if unavailable
     */
    suspend fun getMinAcceptableFeeRate(
        host: String,
        port: Int,
        useSSL: Boolean,
        useTorProxy: Boolean,
        sslSocketFactory: SSLSocketFactory? = null
    ): Double = withContext(Dispatchers.IO) {
        try {
            val timeoutMs = if (useTorProxy) TOR_TIMEOUT_MS else TIMEOUT_MS
            withTimeout(timeoutMs) {
                queryRelayFee(host, port, useSSL, useTorProxy, timeoutMs, sslSocketFactory)
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Failed to query relay fee: ${e.message}")
            DEFAULT_MIN_FEE_RATE
        }
    }
    
    private fun queryRelayFee(
        host: String,
        port: Int,
        useSSL: Boolean,
        useTorProxy: Boolean,
        timeoutMs: Long,
        sslSocketFactory: SSLSocketFactory? = null
    ): Double {
        var socket: Socket? = null
        var connectedSocket: Socket? = null
        try {
            if (BuildConfig.DEBUG) Log.d(TAG, "Querying relay fee: host=$host, port=$port, ssl=$useSSL, tor=$useTorProxy")

            // Create socket (with Tor proxy if needed)
            socket = if (useTorProxy) {
                val proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress(TOR_PROXY_HOST, TOR_PROXY_PORT))
                Socket(proxy)
            } else {
                Socket()
            }

            // Connect - use createUnresolved for Tor so the proxy handles DNS (required for .onion)
            val address = if (useTorProxy) {
                InetSocketAddress.createUnresolved(host, port)
            } else {
                InetSocketAddress(host, port)
            }
            socket.connect(address, timeoutMs.toInt())
            socket.soTimeout = timeoutMs.toInt()
            if (BuildConfig.DEBUG) Log.d(TAG, "Socket connected to $host:$port")
            
            // Upgrade to SSL if needed
            connectedSocket = if (useSSL) {
                val factory = sslSocketFactory
                    ?: if (host.endsWith(".onion")) createTrustAllSSLSocketFactory()
                    else throw IllegalStateException("SSL socket factory required for clearnet host: $host")
                val sslSocket = factory.createSocket(socket, host, port, true)
                if (BuildConfig.DEBUG) Log.d(TAG, "SSL socket created")
                sslSocket
            } else {
                socket
            }
            
            // Send requests
            val writer = PrintWriter(connectedSocket.getOutputStream(), true)
            val reader = BufferedReader(InputStreamReader(connectedSocket.getInputStream()))
            
            // First, send server.version to establish protocol (required by Electrum)
            val versionRequest = JSONObject().apply {
                put("id", 0)
                put("method", "server.version")
                put("params", org.json.JSONArray().apply {
                    put("IbisWallet")
                    put("1.4")
                })
            }
            
            if (BuildConfig.DEBUG) Log.d(TAG, "Sending version request: ${versionRequest.toString()}")
            writer.println(versionRequest.toString())
            writer.flush()
            
            // Read version response
            val versionResponse = reader.readLine()
            if (BuildConfig.DEBUG) Log.d(TAG, "Version response: $versionResponse")
            
            // Now send blockchain.relayfee request
            // This returns the minimum relay fee in BTC per kilobyte
            val request = JSONObject().apply {
                put("id", 1)
                put("method", "blockchain.relayfee")
                put("params", org.json.JSONArray())
            }
            
            if (BuildConfig.DEBUG) Log.d(TAG, "Sending relayfee request: ${request.toString()}")
            writer.println(request.toString())
            writer.flush()
            
            // Read relayfee response
            val response = reader.readLine()
            if (BuildConfig.DEBUG) Log.d(TAG, "Relayfee response: $response")
            
            if (response != null) {
                val json = JSONObject(response)
                
                // Check for error first
                val error = json.optJSONObject("error")
                if (error != null) {
                    if (BuildConfig.DEBUG) Log.e(TAG, "Server returned error: ${error.toString()}")
                    return DEFAULT_MIN_FEE_RATE
                }
                
                // Result is a floating point number in BTC/kB
                // Example: 0.00001 BTC/kB = 1 sat/vB
                // Example: 0.000001 BTC/kB = 0.1 sat/vB
                val relayFeeBtcPerKb = json.optDouble("result", -1.0)
                
                if (relayFeeBtcPerKb < 0) {
                    // Server returned -1 meaning it doesn't have enough info
                    if (BuildConfig.DEBUG) Log.w(TAG, "Server returned -1 for relayfee, using default")
                    return DEFAULT_MIN_FEE_RATE
                }
                
                // Convert BTC/kB to sat/vB
                val minFeeRate = relayFeeBtcPerKb * BTC_PER_KB_TO_SAT_PER_VB
                
                if (BuildConfig.DEBUG) Log.d(TAG, "Server relay fee: $relayFeeBtcPerKb BTC/kB = $minFeeRate sat/vB")
                
                // Sanity check - if the value is unreasonably low (< 0.01) or high (> 100), use default
                if (minFeeRate < 0.01 || minFeeRate > 100.0) {
                    if (BuildConfig.DEBUG) Log.w(TAG, "Relay fee out of reasonable range ($minFeeRate), using default")
                    return DEFAULT_MIN_FEE_RATE
                }
                
                return minFeeRate
            } else {
                if (BuildConfig.DEBUG) Log.w(TAG, "No response received from server")
            }
            
            if (BuildConfig.DEBUG) Log.d(TAG, "Could not parse relay fee, using default")
            return DEFAULT_MIN_FEE_RATE
            
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Error querying relay fee: ${e.javaClass.simpleName} - ${e.message}", e)
            throw e
        } finally {
            try {
                connectedSocket?.close()
                socket?.close()
            } catch (e: Exception) {
                // Ignore close errors
            }
        }
    }
    
    /**
     * Data class for transaction details fetched from Electrum
     */
    data class ElectrumTxDetails(
        val txid: String,
        val size: Int,      // Raw size in bytes
        val vsize: Int,     // Virtual size in vbytes
        val weight: Int     // Weight units
    )
    
    /**
     * Fetch transaction details (size/vsize) from Electrum server
     * Uses blockchain.transaction.get with verbose=true
     * 
     * @param txid Transaction ID
     * @param host Server hostname
     * @param port Server port
     * @param useSSL Whether to use SSL/TLS
     * @param useTorProxy Whether to route through Tor SOCKS proxy
     * @return ElectrumTxDetails or null if failed
     */
    suspend fun getTransactionDetails(
        txid: String,
        host: String,
        port: Int,
        useSSL: Boolean,
        useTorProxy: Boolean,
        sslSocketFactory: SSLSocketFactory? = null
    ): ElectrumTxDetails? = withContext(Dispatchers.IO) {
        try {
            val timeoutMs = if (useTorProxy) TOR_TIMEOUT_MS else TIMEOUT_MS
            withTimeout(timeoutMs) {
                queryTransactionDetails(txid, host, port, useSSL, useTorProxy, timeoutMs, sslSocketFactory)
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Failed to fetch tx details for $txid: ${e.message}")
            null
        }
    }
    
    private fun queryTransactionDetails(
        txid: String,
        host: String,
        port: Int,
        useSSL: Boolean,
        useTorProxy: Boolean,
        timeoutMs: Long,
        sslSocketFactory: SSLSocketFactory? = null
    ): ElectrumTxDetails? {
        var socket: Socket? = null
        var connectedSocket: Socket? = null
        try {
            if (BuildConfig.DEBUG) Log.d(TAG, "Querying tx details: txid=$txid, host=$host, port=$port")

            // Create socket (with Tor proxy if needed)
            socket = if (useTorProxy) {
                val proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress(TOR_PROXY_HOST, TOR_PROXY_PORT))
                Socket(proxy)
            } else {
                Socket()
            }

            // Connect
            val address = if (useTorProxy) {
                InetSocketAddress.createUnresolved(host, port)
            } else {
                InetSocketAddress(host, port)
            }
            socket.connect(address, timeoutMs.toInt())
            socket.soTimeout = timeoutMs.toInt()
            
            // Upgrade to SSL if needed
            connectedSocket = if (useSSL) {
                val factory = sslSocketFactory
                    ?: if (host.endsWith(".onion")) createTrustAllSSLSocketFactory()
                    else throw IllegalStateException("SSL socket factory required for clearnet host: $host")
                factory.createSocket(socket, host, port, true)
            } else {
                socket
            }
            
            val writer = PrintWriter(connectedSocket.getOutputStream(), true)
            val reader = BufferedReader(InputStreamReader(connectedSocket.getInputStream()))
            
            // Send server.version first (required by Electrum protocol)
            val versionRequest = JSONObject().apply {
                put("id", 0)
                put("method", "server.version")
                put("params", org.json.JSONArray().apply {
                    put("IbisWallet")
                    put("1.4")
                })
            }
            writer.println(versionRequest.toString())
            writer.flush()
            reader.readLine() // Read version response
            
            // Request transaction with verbose=true to get size/vsize
            val request = JSONObject().apply {
                put("id", 1)
                put("method", "blockchain.transaction.get")
                put("params", org.json.JSONArray().apply {
                    put(txid)
                    put(true) // verbose=true
                })
            }
            
            if (BuildConfig.DEBUG) Log.d(TAG, "Sending tx request: ${request.toString()}")
            writer.println(request.toString())
            writer.flush()
            
            val response = reader.readLine()
            if (BuildConfig.DEBUG) Log.d(TAG, "Tx response: ${response?.take(500)}")
            
            if (response != null) {
                val json = JSONObject(response)
                
                val error = json.optJSONObject("error")
                if (error != null) {
                    if (BuildConfig.DEBUG) Log.e(TAG, "Server returned error: ${error.toString()}")
                    return null
                }
                
                val result = json.optJSONObject("result")
                val resultString = if (result == null) json.optString("result", null) else null
                
                if (result != null) {
                    // Log all available keys to see what the server returns
                    if (BuildConfig.DEBUG) Log.d(TAG, "Result keys: ${result.keys().asSequence().toList()}")
                    
                    val size = result.optInt("size", -1)
                    val vsize = result.optInt("vsize", -1)
                    val weight = result.optInt("weight", -1)
                    
                    if (BuildConfig.DEBUG) Log.d(TAG, "Parsed values: size=$size, vsize=$vsize, weight=$weight")
                    
                    // If vsize not provided but weight is, calculate it
                    val calculatedVsize = when {
                        vsize > 0 -> vsize
                        weight > 0 -> (weight + 3) / 4 // ceil(weight/4)
                        size > 0 -> size // Legacy tx, size == vsize
                        else -> -1
                    }
                    
                    if (calculatedVsize > 0) {
                        if (BuildConfig.DEBUG) Log.d(TAG, "Tx $txid: returning vsize=$calculatedVsize")
                        return ElectrumTxDetails(
                            txid = txid,
                            size = if (size > 0) size else calculatedVsize,
                            vsize = calculatedVsize,
                            weight = if (weight > 0) weight else calculatedVsize * 4
                        )
                    } else {
                        if (BuildConfig.DEBUG) Log.w(TAG, "Tx $txid: no size info available in verbose response")
                    }
                } else if (resultString != null && resultString.length > 10) {
                    // Server returned raw hex instead of verbose object
                    // Raw tx size = hex length / 2
                    val rawSize = resultString.length / 2
                    if (BuildConfig.DEBUG) Log.d(TAG, "Tx $txid: server returned raw hex, size=$rawSize bytes")
                    // Note: This is the raw size, not vsize. For segwit txs this is inaccurate.
                    // We can't calculate vsize from raw hex without parsing the tx structure.
                    if (BuildConfig.DEBUG) Log.w(TAG, "Cannot determine vsize from raw hex - server doesn't support verbose mode")
                } else {
                    if (BuildConfig.DEBUG) Log.w(TAG, "Tx $txid: result is null or not an object/string")
                }
            }
            
            if (BuildConfig.DEBUG) Log.w(TAG, "Could not parse tx details")
            return null
            
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Error querying tx details: ${e.javaClass.simpleName} - ${e.message}")
            throw e
        } finally {
            try {
                connectedSocket?.close()
                socket?.close()
            } catch (e: Exception) {
                // Ignore close errors
            }
        }
    }
}
