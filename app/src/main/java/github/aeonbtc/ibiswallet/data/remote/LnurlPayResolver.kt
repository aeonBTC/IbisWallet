package github.aeonbtc.ibiswallet.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit

data class LnurlPayMetadata(
    val callback: String,
    val minSendableMsats: Long,
    val maxSendableMsats: Long,
    val metadata: String,
    val isFixedAmount: Boolean,
) {
    val minSendableSats: Long get() = (minSendableMsats + 999L) / 1000L
    val maxSendableSats: Long get() = maxSendableMsats / 1000L
}

data class LnurlPayInvoice(
    val bolt11: String,
)

class LnurlPayResolver(
    private val baseHttpClient: OkHttpClient,
    private val useTor: () -> Boolean,
    private val torSocksPort: Int = 9050,
) {
    companion object {
        private const val CONNECT_TIMEOUT_SECONDS = 15L
        private const val READ_TIMEOUT_SECONDS = 20L
        private const val TOR_TIMEOUT_SECONDS = 45L
    }

    suspend fun resolveAddress(address: String): LnurlPayMetadata = withContext(Dispatchers.IO) {
        val parts = address.trim().split("@", limit = 2)
        require(parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) {
            "Invalid Lightning Address"
        }
        val user = parts[0].trim().lowercase()
        val domain = parts[1].trim()
        val url = "https://$domain/.well-known/lnurlp/$user"
        fetchPayMetadata(url)
    }

    suspend fun resolveUrl(lnurlUrl: String): LnurlPayMetadata = withContext(Dispatchers.IO) {
        fetchPayMetadata(lnurlUrl)
    }

    suspend fun fetchInvoice(
        metadata: LnurlPayMetadata,
        amountMsats: Long,
    ): LnurlPayInvoice = withContext(Dispatchers.IO) {
        if (amountMsats < metadata.minSendableMsats) {
            throw Exception(
                "Amount is below the minimum of ${formatSatsDisplay(metadata.minSendableSats)}.",
            )
        }
        if (amountMsats > metadata.maxSendableMsats) {
            throw Exception(
                "Amount exceeds the maximum of ${formatSatsDisplay(metadata.maxSendableSats)}.",
            )
        }
        val separator = if (metadata.callback.contains("?")) "&" else "?"
        val url = "${metadata.callback}${separator}amount=$amountMsats"
        val request = Request.Builder()
            .url(url)
            .get()
            .header("Accept", "application/json")
            .build()
        val response = httpClient().newCall(request).execute()
        response.use { httpResponse ->
            if (!httpResponse.isSuccessful) {
                val body = runCatching { httpResponse.body.string() }.getOrNull()
                val reason = extractErrorReason(body)
                throw Exception(reason ?: "Failed to fetch Lightning invoice (HTTP ${httpResponse.code})")
            }
            val body = httpResponse.body.string()
            val json = JSONObject(body)
            if (json.has("status") && json.optString("status") == "ERROR") {
                throw Exception(json.optString("reason", "LNURL service returned an error"))
            }
            val bolt11 = json.optString("pr", "").takeIf { it.isNotBlank() }
                ?: throw Exception("LNURL service did not return a Lightning invoice")
            LnurlPayInvoice(bolt11 = bolt11)
        }
    }

    private fun fetchPayMetadata(url: String): LnurlPayMetadata {
        val request = Request.Builder()
            .url(url)
            .get()
            .header("Accept", "application/json")
            .build()
        val response = httpClient().newCall(request).execute()
        return response.use { httpResponse ->
            if (!httpResponse.isSuccessful) {
                val body = runCatching { httpResponse.body.string() }.getOrNull()
                val reason = extractErrorReason(body)
                throw Exception(reason ?: "Failed to resolve LNURL (HTTP ${httpResponse.code})")
            }
            val body = httpResponse.body.string()
            val json = JSONObject(body)
            if (json.has("status") && json.optString("status") == "ERROR") {
                throw Exception(json.optString("reason", "LNURL service returned an error"))
            }
            val tag = json.optString("tag", "")
            if (tag != "payRequest") {
                throw Exception("Unsupported LNURL type: $tag")
            }
            val callback = json.optString("callback", "").takeIf { it.isNotBlank() }
                ?: throw Exception("LNURL response missing callback URL")
            val minSendable = json.optLong("minSendable", 0L)
            val maxSendable = json.optLong("maxSendable", 0L)
            if (minSendable <= 0L || maxSendable <= 0L) {
                throw Exception("LNURL response has invalid amount bounds")
            }
            val metadata = json.optString("metadata", "")
            LnurlPayMetadata(
                callback = callback,
                minSendableMsats = minSendable,
                maxSendableMsats = maxSendable,
                metadata = metadata,
                isFixedAmount = minSendable == maxSendable,
            )
        }
    }

    private fun httpClient(): OkHttpClient {
        val timeoutSeconds = if (useTor()) TOR_TIMEOUT_SECONDS else READ_TIMEOUT_SECONDS
        val builder = baseHttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(timeoutSeconds, TimeUnit.SECONDS)
            .writeTimeout(timeoutSeconds, TimeUnit.SECONDS)
            .callTimeout(timeoutSeconds + 5L, TimeUnit.SECONDS)

        if (useTor()) {
            builder.proxy(Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", torSocksPort)))
            builder.dns { hostname ->
                listOf(InetAddress.getByAddress(hostname, byteArrayOf(0, 0, 0, 0)))
            }
        }

        return builder.build()
    }

    private fun extractErrorReason(body: String?): String? {
        if (body.isNullOrBlank()) return null
        return runCatching {
            JSONObject(body).optString("reason").takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    private fun formatSatsDisplay(sats: Long): String {
        return "%,d sats".format(java.util.Locale.US, sats)
    }
}
