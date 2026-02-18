package github.aeonbtc.ibiswallet.data

import android.util.Log
import github.aeonbtc.ibiswallet.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit

/**
 * Service for fetching BTC/USD price from various sources
 */
class BtcPriceService {
    companion object {
        private const val TAG = "BtcPriceService"
        private const val MEMPOOL_PRICE_URL = "https://mempool.space/api/v1/prices"
        private const val MEMPOOL_ONION_PRICE_URL =
            "http://mempoolhqx4isw62xs7abwphsq7ldayuidyx2v2oethdhhj6mlo2r6ad.onion/api/v1/prices"
        private const val COINGECKO_PRICE_URL = "https://api.coingecko.com/api/v3/simple/price?ids=bitcoin&vs_currencies=usd"
        private const val TIMEOUT_SECONDS = 15L
        private const val TOR_TIMEOUT_SECONDS = 30L
        private const val TOR_PROXY_HOST = "127.0.0.1"
        private const val TOR_PROXY_PORT = 9050
    }

    private val client =
        OkHttpClient.Builder()
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()

    private val torClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(TOR_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TOR_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(TOR_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .proxy(Proxy(Proxy.Type.SOCKS, InetSocketAddress(TOR_PROXY_HOST, TOR_PROXY_PORT)))
            .dns(
                object : Dns {
                    override fun lookup(hostname: String): List<InetAddress> {
                        // Send hostname through SOCKS5 proxy for Tor resolution, avoiding DNS leaks
                        return listOf(InetAddress.getByAddress(hostname, byteArrayOf(0, 0, 0, 0)))
                    }
                },
            )
            .build()
    }

    /**
     * Fetch BTC/USD price from mempool.space
     * @return Price in USD or null on failure
     */
    suspend fun fetchFromMempool(): Double? =
        withContext(Dispatchers.IO) {
            try {
                val request =
                    Request.Builder()
                        .url(MEMPOOL_PRICE_URL)
                        .header("Accept", "application/json")
                        .build()

                val response = client.newCall(request).execute()

                if (!response.isSuccessful) {
                    if (BuildConfig.DEBUG) Log.e(TAG, "Mempool price fetch failed: ${response.code}")
                    return@withContext null
                }

                val body = response.body?.string()
                if (body.isNullOrEmpty()) {
                    if (BuildConfig.DEBUG) Log.e(TAG, "Mempool price response empty")
                    return@withContext null
                }

                val json = JSONObject(body)
                val price = json.optDouble("USD", -1.0)

                if (price > 0) {
                    if (BuildConfig.DEBUG) Log.d(TAG, "Mempool price: $price USD")
                    price
                } else {
                    if (BuildConfig.DEBUG) Log.e(TAG, "Invalid price from Mempool")
                    null
                }
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.e(TAG, "Error fetching from Mempool", e)
                null
            }
        }

    /**
     * Fetch BTC/USD price from mempool.space via Tor (.onion)
     * @return Price in USD or null on failure
     */
    suspend fun fetchFromMempoolOnion(): Double? =
        withContext(Dispatchers.IO) {
            try {
                val request =
                    Request.Builder()
                        .url(MEMPOOL_ONION_PRICE_URL)
                        .header("Accept", "application/json")
                        .build()

                val response = torClient.newCall(request).execute()

                if (!response.isSuccessful) {
                    if (BuildConfig.DEBUG) Log.e(TAG, "Mempool onion price fetch failed: ${response.code}")
                    return@withContext null
                }

                val body = response.body?.string()
                if (body.isNullOrEmpty()) {
                    if (BuildConfig.DEBUG) Log.e(TAG, "Mempool onion price response empty")
                    return@withContext null
                }

                val json = JSONObject(body)
                val price = json.optDouble("USD", -1.0)

                if (price > 0) {
                    if (BuildConfig.DEBUG) Log.d(TAG, "Mempool onion price: $price USD")
                    price
                } else {
                    if (BuildConfig.DEBUG) Log.e(TAG, "Invalid price from Mempool onion")
                    null
                }
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.e(TAG, "Error fetching from Mempool onion", e)
                null
            }
        }

    /**
     * Fetch BTC/USD price from CoinGecko
     * @return Price in USD or null on failure
     */
    suspend fun fetchFromCoinGecko(): Double? =
        withContext(Dispatchers.IO) {
            try {
                val request =
                    Request.Builder()
                        .url(COINGECKO_PRICE_URL)
                        .header("Accept", "application/json")
                        .build()

                val response = client.newCall(request).execute()

                if (!response.isSuccessful) {
                    if (BuildConfig.DEBUG) Log.e(TAG, "CoinGecko price fetch failed: ${response.code}")
                    return@withContext null
                }

                val body = response.body?.string()
                if (body.isNullOrEmpty()) {
                    if (BuildConfig.DEBUG) Log.e(TAG, "CoinGecko price response empty")
                    return@withContext null
                }

                // Response format: {"bitcoin":{"usd":12345.67}}
                val json = JSONObject(body)
                val bitcoin = json.optJSONObject("bitcoin")
                val price = bitcoin?.optDouble("usd", -1.0) ?: -1.0

                if (price > 0) {
                    if (BuildConfig.DEBUG) Log.d(TAG, "CoinGecko price: $price USD")
                    price
                } else {
                    if (BuildConfig.DEBUG) Log.e(TAG, "Invalid price from CoinGecko")
                    null
                }
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.e(TAG, "Error fetching from CoinGecko", e)
                null
            }
        }
}
