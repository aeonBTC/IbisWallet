package github.aeonbtc.ibiswallet.data

import android.util.Log
import github.aeonbtc.ibiswallet.data.model.FeeEstimates
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit

/**
 * Service for fetching fee rate estimates from mempool.space API
 */
class FeeEstimationService {
    
    companion object {
        private const val TAG = "FeeEstimationService"
        private const val PRECISE_ENDPOINT = "/api/v1/fees/precise"
        private const val RECOMMENDED_ENDPOINT = "/api/v1/fees/recommended"
        private const val TOR_PROXY_HOST = "127.0.0.1"
        private const val TOR_PROXY_PORT = 9050
        private const val TIMEOUT_SECONDS = 30L
        private const val TOR_TIMEOUT_SECONDS = 60L
    }
    
    /**
     * Fetch fee estimates from the specified mempool API
     * 
     * @param baseUrl The base URL of the mempool server (e.g., "https://mempool.space")
     * @param useTorProxy Whether to route requests through Tor SOCKS proxy
     * @param usePrecise Whether to use the precise endpoint (for sub-sat fees). If true, tries
     *                   precise first then falls back to recommended. If false, only uses recommended.
     * @return FeeEstimates on success, null on failure
     */
    suspend fun fetchFeeEstimates(
        baseUrl: String,
        useTorProxy: Boolean,
        usePrecise: Boolean = true
    ): Result<FeeEstimates> = withContext(Dispatchers.IO) {
        try {
            val client = buildClient(useTorProxy)
            
            if (usePrecise) {
                // Try precise endpoint first for sub-sat precision
                val preciseResult = tryFetchFromEndpoint(client, baseUrl, PRECISE_ENDPOINT)
                if (preciseResult.isSuccess) {
                    return@withContext preciseResult
                }
                Log.d(TAG, "Precise endpoint failed, trying recommended endpoint")
            }
            
            // Use recommended endpoint (either as fallback or primary)
            val recommendedResult = tryFetchFromEndpoint(client, baseUrl, RECOMMENDED_ENDPOINT)
            if (recommendedResult.isSuccess) {
                return@withContext recommendedResult
            }
            
            // Failed
            val error = recommendedResult.exceptionOrNull()?.message ?: "Unknown error"
            Result.failure(Exception("Failed to fetch fee estimates: $error"))
            
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching fee estimates", e)
            Result.failure(e)
        }
    }
    
    private fun buildClient(useTorProxy: Boolean): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(if (useTorProxy) TOR_TIMEOUT_SECONDS else TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(if (useTorProxy) TOR_TIMEOUT_SECONDS else TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(if (useTorProxy) TOR_TIMEOUT_SECONDS else TIMEOUT_SECONDS, TimeUnit.SECONDS)
        
        if (useTorProxy) {
            val proxy = Proxy(
                Proxy.Type.SOCKS,
                InetSocketAddress(TOR_PROXY_HOST, TOR_PROXY_PORT)
            )
            builder.proxy(proxy)
        }
        
        return builder.build()
    }
    
    private fun tryFetchFromEndpoint(
        client: OkHttpClient,
        baseUrl: String,
        endpoint: String
    ): Result<FeeEstimates> {
        return try {
            val url = "${baseUrl.trimEnd('/')}$endpoint"
            Log.d(TAG, "Fetching fees from: $url")
            
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .build()
            
            val response = client.newCall(request).execute()
            
            if (!response.isSuccessful) {
                return Result.failure(Exception("HTTP ${response.code}: ${response.message}"))
            }
            
            val body = response.body?.string()
            if (body.isNullOrEmpty()) {
                return Result.failure(Exception("Empty response body"))
            }
            
            val estimates = parseResponse(body)
            Result.success(estimates)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching from $endpoint", e)
            Result.failure(e)
        }
    }
    
    private fun parseResponse(jsonString: String): FeeEstimates {
        val json = JSONObject(jsonString)
        
        // Both endpoints return the same field names
        // Precise returns decimals, recommended returns integers
        val fastestFee = json.optDouble("fastestFee", 1.0)
        val halfHourFee = json.optDouble("halfHourFee", 1.0)
        val hourFee = json.optDouble("hourFee", 1.0)
        val minimumFee = json.optDouble("minimumFee", 1.0)
        
        return FeeEstimates(
            fastestFee = fastestFee,
            halfHourFee = halfHourFee,
            hourFee = hourFee,
            minimumFee = minimumFee
        )
    }
}
