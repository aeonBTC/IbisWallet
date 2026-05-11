package github.aeonbtc.ibiswallet.data

import android.util.Log
import github.aeonbtc.ibiswallet.BuildConfig
import github.aeonbtc.ibiswallet.data.model.FeeEstimates
import github.aeonbtc.ibiswallet.util.BitcoinUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.InetAddress
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
        private const val TOR_TIMEOUT_SECONDS = 30L

        private val clearnetClient: OkHttpClient by lazy {
            buildClient(useTorProxy = false)
        }

        private val torClient: OkHttpClient by lazy {
            buildClient(useTorProxy = true)
        }

        private fun buildClient(useTorProxy: Boolean): OkHttpClient {
            val builder =
                OkHttpClient.Builder()
                    .connectTimeout(if (useTorProxy) TOR_TIMEOUT_SECONDS else TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .readTimeout(if (useTorProxy) TOR_TIMEOUT_SECONDS else TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .writeTimeout(if (useTorProxy) TOR_TIMEOUT_SECONDS else TIMEOUT_SECONDS, TimeUnit.SECONDS)

            if (useTorProxy) {
                val proxy =
                    Proxy(
                        Proxy.Type.SOCKS,
                        InetSocketAddress(TOR_PROXY_HOST, TOR_PROXY_PORT),
                    )
                builder.proxy(proxy)
                // Prevent local DNS resolution — send hostname through SOCKS5 proxy
                // so Tor resolves it at the exit node, avoiding DNS leaks.
                builder.dns { hostname ->
                    listOf(InetAddress.getByAddress(hostname, byteArrayOf(0, 0, 0, 0)))
                }
            }

            return builder.build()
        }
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
        usePrecise: Boolean = true,
    ): Result<FeeEstimates> =
        withContext(Dispatchers.IO) {
            try {
                val client = if (useTorProxy) torClient else clearnetClient

                if (usePrecise) {
                    val preciseResult = tryFetchFromEndpoint(client, baseUrl, PRECISE_ENDPOINT)
                    if (preciseResult.isSuccess) {
                        return@withContext preciseResult
                    }
                    if (BuildConfig.DEBUG) {
                        Log.w(TAG, "Precise fee endpoint failed, falling back to recommended")
                    }
                }

                tryFetchFromEndpoint(client, baseUrl, RECOMMENDED_ENDPOINT)
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.e(TAG, "Error fetching fee estimates", e)
                Result.failure(e)
            }
        }

    private fun tryFetchFromEndpoint(
        client: OkHttpClient,
        baseUrl: String,
        endpoint: String,
    ): Result<FeeEstimates> {
        return try {
            val url = "${baseUrl.trimEnd('/')}$endpoint"
            if (BuildConfig.DEBUG) Log.d(TAG, "Fetching fees from: $url")

            val request =
                Request.Builder()
                    .url(url)
                    .header("Accept", "application/json")
                    .build()

            val response = client.newCall(request).execute()

            response.use {
                if (!it.isSuccessful) {
                    return Result.failure(Exception("HTTP ${it.code}: ${it.message}"))
                }

                val body = it.body.string()
                if (body.isEmpty()) {
                    return Result.failure(Exception("Empty response body"))
                }

                val estimates =
                    try {
                        parseResponse(body)
                    } catch (e: IllegalArgumentException) {
                        // Reject malformed responses (missing fields, NaN, out-of-range)
                        // rather than presenting defaulted 1.0 sat/vB values that an
                        // attacker-controlled custom endpoint could exploit.
                        return Result.failure(e)
                    }
                Result.success(estimates)
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Error fetching from $endpoint", e)
            Result.failure(e)
        }
    }

    /**
     * Delegates JSON parsing to [BitcoinUtils.parseFeeEstimatesJson],
     * then wraps in the app's [FeeEstimates] model. May throw
     * [IllegalArgumentException] when the response is structurally invalid.
     */
    private fun parseResponse(jsonString: String): FeeEstimates {
        val parsed = BitcoinUtils.parseFeeEstimatesJson(jsonString)
        return FeeEstimates(
            fastestFee = parsed.fastestFee,
            halfHourFee = parsed.halfHourFee,
            hourFee = parsed.hourFee,
            minimumFee = parsed.minimumFee,
        )
    }
}
