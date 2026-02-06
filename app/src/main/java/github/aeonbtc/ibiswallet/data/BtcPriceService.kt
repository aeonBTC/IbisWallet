package github.aeonbtc.ibiswallet.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Service for fetching BTC/USD price from various sources
 */
class BtcPriceService {
    
    companion object {
        private const val TAG = "BtcPriceService"
        private const val MEMPOOL_PRICE_URL = "https://mempool.space/api/v1/prices"
        private const val COINGECKO_PRICE_URL = "https://api.coingecko.com/api/v3/simple/price?ids=bitcoin&vs_currencies=usd"
        private const val TIMEOUT_SECONDS = 15L
    }
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()
    
    /**
     * Fetch BTC/USD price from mempool.space
     * @return Price in USD or null on failure
     */
    suspend fun fetchFromMempool(): Double? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(MEMPOOL_PRICE_URL)
                .header("Accept", "application/json")
                .build()
            
            val response = client.newCall(request).execute()
            
            if (!response.isSuccessful) {
                Log.e(TAG, "Mempool price fetch failed: ${response.code}")
                return@withContext null
            }
            
            val body = response.body?.string()
            if (body.isNullOrEmpty()) {
                Log.e(TAG, "Mempool price response empty")
                return@withContext null
            }
            
            val json = JSONObject(body)
            val price = json.optDouble("USD", -1.0)
            
            if (price > 0) {
                Log.d(TAG, "Mempool price: $price USD")
                price
            } else {
                Log.e(TAG, "Invalid price from Mempool")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching from Mempool", e)
            null
        }
    }
    
    /**
     * Fetch BTC/USD price from CoinGecko
     * @return Price in USD or null on failure
     */
    suspend fun fetchFromCoinGecko(): Double? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(COINGECKO_PRICE_URL)
                .header("Accept", "application/json")
                .build()
            
            val response = client.newCall(request).execute()
            
            if (!response.isSuccessful) {
                Log.e(TAG, "CoinGecko price fetch failed: ${response.code}")
                return@withContext null
            }
            
            val body = response.body?.string()
            if (body.isNullOrEmpty()) {
                Log.e(TAG, "CoinGecko price response empty")
                return@withContext null
            }
            
            // Response format: {"bitcoin":{"usd":12345.67}}
            val json = JSONObject(body)
            val bitcoin = json.optJSONObject("bitcoin")
            val price = bitcoin?.optDouble("usd", -1.0) ?: -1.0
            
            if (price > 0) {
                Log.d(TAG, "CoinGecko price: $price USD")
                price
            } else {
                Log.e(TAG, "Invalid price from CoinGecko")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching from CoinGecko", e)
            null
        }
    }
}
