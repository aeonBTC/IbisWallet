package github.aeonbtc.ibiswallet.data.remote

import github.aeonbtc.ibiswallet.data.boltz.BoltzTraceContext
import github.aeonbtc.ibiswallet.data.boltz.BoltzTraceLevel
import github.aeonbtc.ibiswallet.data.boltz.boltzElapsedMs
import github.aeonbtc.ibiswallet.data.boltz.boltzTraceStart
import github.aeonbtc.ibiswallet.data.boltz.logBoltzTrace
import github.aeonbtc.ibiswallet.util.InputLimits
import github.aeonbtc.ibiswallet.util.stringWithLimit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.IDN
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

data class Bip353Resolution(
    val address: String,
    val bitcoinUri: String,
    val ttlSeconds: Long?,
)

class Bip353Resolver(
    private val baseHttpClient: OkHttpClient,
    private val useTor: () -> Boolean,
    private val torSocksPort: Int = 9050,
) {
    companion object {
        private const val DOH_URL = "https://dns.google/resolve"
        private const val CONNECT_TIMEOUT_SECONDS = 15L
        private const val READ_TIMEOUT_SECONDS = 20L
        private const val TOR_TIMEOUT_SECONDS = 45L
        private val TXT_SEGMENT_REGEX = Regex("\"([^\"]*)\"")
    }

    suspend fun resolve(address: String): Bip353Resolution = withContext(Dispatchers.IO) {
        val trace = BoltzTraceContext(operation = "resolveBip353", viaTor = useTor(), source = "doh")
        val startedAt = boltzTraceStart()
        val normalizedAddress = address.trim().removePrefix("₿")
        val parts = normalizedAddress.split("@", limit = 2)
        require(parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) {
            "Invalid BIP-353 recipient"
        }

        val user = IDN.toASCII(parts[0].trim())
        val domain = IDN.toASCII(parts[1].trim())
        val lookupName = "$user.user._bitcoin-payment.$domain"
        val encodedName = URLEncoder.encode(lookupName, StandardCharsets.UTF_8.name())
        logBoltzTrace(
            "start",
            trace,
            "address" to summarizeValue(normalizedAddress),
            "lookup" to summarizeValue(lookupName),
        )
        val request = Request.Builder()
            .url("$DOH_URL?name=$encodedName&type=TXT&do=1&cd=0")
            .get()
            .header("Accept", "application/dns-json")
            .build()
        try {
            val response = httpClient().newCall(request).execute()

            response.use { httpResponse ->
                if (!httpResponse.isSuccessful) {
                    throw Exception("Failed to resolve BIP-353 recipient")
                }
                val body = httpResponse.body.stringWithLimit(InputLimits.SMALL_JSON_BYTES)
                if (body.isBlank()) {
                    throw Exception("BIP-353 lookup returned an empty response")
                }

                val json = JSONObject(body)
                when (json.optInt("Status", -1)) {
                    0 -> Unit
                    3 -> throw Exception("BIP-353 recipient not found")
                    else -> throw Exception("BIP-353 lookup failed")
                }

                if (!json.optBoolean("AD", false)) {
                    throw Exception("BIP-353 lookup could not be DNSSEC-validated")
                }

                val answers = json.optJSONArray("Answer")
                    ?: throw Exception("BIP-353 recipient has no DNS TXT record")
                val matchingRecords = mutableListOf<Pair<String, Long?>>()
                for (index in 0 until answers.length()) {
                    val answer = answers.optJSONObject(index) ?: continue
                    if (answer.optInt("type") != 16) continue
                    val decoded = decodeTxtData(answer.optString("data"))
                    if (decoded.startsWith("bitcoin:", ignoreCase = true)) {
                        val ttl = answer.optLong("TTL").takeIf { ttlValue -> ttlValue > 0L }
                        matchingRecords += decoded to ttl
                    }
                }

                if (matchingRecords.isEmpty()) {
                    throw Exception("BIP-353 recipient does not publish Bitcoin payment instructions")
                }
                if (matchingRecords.size > 1) {
                    throw Exception("BIP-353 recipient published multiple Bitcoin payment instructions")
                }

                val (bitcoinUri, ttlSeconds) = matchingRecords.single()
                logBoltzTrace(
                    "success",
                    trace,
                    "elapsedMs" to boltzElapsedMs(startedAt),
                    "address" to summarizeValue(normalizedAddress),
                    "dnssecValidated" to json.optBoolean("AD", false),
                    "answerCount" to answers.length(),
                    "ttlSeconds" to ttlSeconds,
                    "resolvedUri" to summarizeValue(bitcoinUri),
                )
                Bip353Resolution(
                    address = normalizedAddress,
                    bitcoinUri = bitcoinUri,
                    ttlSeconds = ttlSeconds,
                )
            }
        } catch (error: Exception) {
            logBoltzTrace(
                "failed",
                trace,
                level = BoltzTraceLevel.WARN,
                throwable = error,
                "elapsedMs" to boltzElapsedMs(startedAt),
                "address" to summarizeValue(normalizedAddress),
                "lookup" to summarizeValue(lookupName),
            )
            throw error
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

    private fun decodeTxtData(data: String): String {
        val trimmed = data.trim()
        val quotedSegments = TXT_SEGMENT_REGEX.findAll(trimmed).map { it.groupValues[1] }.toList()
        return when {
            quotedSegments.isNotEmpty() -> quotedSegments.joinToString("")
            trimmed.startsWith("\"") && trimmed.endsWith("\"") && trimmed.length >= 2 -> trimmed.substring(1, trimmed.length - 1)
            else -> trimmed
        }
    }

    private fun summarizeValue(value: String?): String {
        if (value.isNullOrBlank()) return "null"
        return if (value.length <= 12) value else "${value.take(6)}...${value.takeLast(4)}"
    }

}
