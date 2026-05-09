package github.aeonbtc.ibiswallet.data.remote

import github.aeonbtc.ibiswallet.data.boltz.BoltzSwapUpdatesSource
import github.aeonbtc.ibiswallet.data.boltz.BoltzTraceContext
import github.aeonbtc.ibiswallet.data.boltz.BoltzTraceLevel
import github.aeonbtc.ibiswallet.data.boltz.boltzElapsedMs
import github.aeonbtc.ibiswallet.data.boltz.boltzTraceStart
import github.aeonbtc.ibiswallet.data.boltz.logBoltzTrace
import github.aeonbtc.ibiswallet.data.model.BoltzFees
import github.aeonbtc.ibiswallet.data.model.BoltzLimits
import github.aeonbtc.ibiswallet.data.model.BoltzPairInfo
import github.aeonbtc.ibiswallet.data.model.BoltzSubmarineResponse
import github.aeonbtc.ibiswallet.data.model.BoltzSwapUpdate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.pow

data class BoltzFetchedBolt12Invoice(
    val invoice: String,
)

/**
 * Client for the Boltz API v2 endpoints still used by the app.
 *
 * Uses REST endpoints for pair metadata, BOLT12 invoice fetches, and legacy
 * submarine swap creation, plus WebSocket status updates.
 * Tor-aware: routes through SOCKS5 proxy when Tor is active.
 */
class BoltzApiClient(
    private val baseHttpClient: OkHttpClient,
    private val useTor: () -> Boolean,
    private val torSocksPort: Int = 9050,
) : BoltzSwapUpdatesSource {
    companion object {
        private const val MAINNET_URL = "https://api.boltz.exchange"
        private const val TOR_URL = "http://boltzzzbnus4m7mta3cxmflnps4fp7dueu2tgurstbvrbt6xswzcocyd.onion/api"
        private const val WS_URL = "wss://api.boltz.exchange/v2/ws"
        private const val TOR_WS_URL = "ws://boltzzzbnus4m7mta3cxmflnps4fp7dueu2tgurstbvrbt6xswzcocyd.onion/api/v2/ws"
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
        private const val RECONNECT_BASE_DELAY_MS = 1_000L
        private const val RECONNECT_MAX_DELAY_MS = 16_000L
        private const val MAX_RECONNECT_ATTEMPTS = 5
        private const val BOLT12_FETCH_TIMEOUT_CLEARNET_SECONDS = 90L
        private const val BOLT12_FETCH_TIMEOUT_TOR_SECONDS = 150L
    }

    private val baseUrl: String get() = if (useTor()) TOR_URL else MAINNET_URL
    private val wsUrl: String get() = if (useTor()) TOR_WS_URL else WS_URL
    private val webSocketScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val webSocketMutex = Mutex()
    private val swapUpdateFlows = java.util.concurrent.ConcurrentHashMap<String, MutableSharedFlow<BoltzSwapUpdate>>()
    private val swapSubscriptionCounts = mutableMapOf<String, Int>()
    private val pendingSubscriptions = linkedSetOf<String>()
    private var sharedWebSocket: WebSocket? = null
    private var sharedWebSocketUrl: String? = null
    private var webSocketReady = false
    private var reconnectAttempts = 0
    private var reconnectJob: Job? = null
    private val requestCounter = AtomicLong(0L)

    private fun httpClient(): OkHttpClient {
        return if (useTor()) {
            baseHttpClient.newBuilder()
                .proxy(Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", torSocksPort)))
                .build()
        } else {
            baseHttpClient
        }
    }

    /**
     * OkHttpClient tuned for long-lived WebSocket connections.
     * pingInterval keeps the connection alive through mobile NATs/carriers
     * that drop idle TCP sockets. readTimeout is extended because swap
     * status updates can be minutes apart.
     */
    private fun wsClient(): OkHttpClient {
        val builder = baseHttpClient.newBuilder()
            .pingInterval(30, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.SECONDS)
        if (useTor()) {
            builder.proxy(Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", torSocksPort)))
        }
        return builder.build()
    }

    // ── Helper: synchronous HTTP ──

    private suspend fun get(path: String): JSONObject = withContext(Dispatchers.IO) {
        val requestId = nextRequestId("GET")
        val trace = BoltzTraceContext(operation = "http_get", viaTor = useTor(), source = "rest")
        val startedAt = boltzTraceStart()
        val request = Request.Builder().url("$baseUrl$path").get().build()
        logBoltzTrace("start", trace, "requestId" to requestId, "path" to path)
        try {
            val response = httpClient().newCall(request).executeAsync()
            val responseBody = response.body.string()
            if (!response.isSuccessful) {
                val errorMsg = try {
                    JSONObject(responseBody).optString("error", responseBody)
                } catch (_: Exception) { responseBody }
                logBoltzTrace(
                    "failed",
                    trace,
                    level = BoltzTraceLevel.WARN,
                    throwable = Exception("Boltz API error ${response.code}: $errorMsg"),
                    "requestId" to requestId,
                    "path" to path,
                    "code" to response.code,
                    "elapsedMs" to boltzElapsedMs(startedAt),
                    "error" to summarizeValue(errorMsg),
                )
                throw Exception("Boltz API error ${response.code}: $errorMsg")
            }
            logBoltzTrace(
                "success",
                trace,
                "requestId" to requestId,
                "path" to path,
                "code" to response.code,
                "elapsedMs" to boltzElapsedMs(startedAt),
            )
            JSONObject(responseBody)
        } catch (error: Exception) {
            if (error.message?.startsWith("Boltz API error") != true) {
                logBoltzTrace(
                    "exception",
                    trace,
                    level = BoltzTraceLevel.WARN,
                    throwable = error,
                    "requestId" to requestId,
                    "path" to path,
                    "elapsedMs" to boltzElapsedMs(startedAt),
                )
            }
            throw error
        }
    }

    private suspend fun post(
        path: String,
        body: JSONObject,
        client: OkHttpClient = httpClient(),
    ): JSONObject = withContext(Dispatchers.IO) {
        val requestId = nextRequestId("POST")
        val trace = BoltzTraceContext(operation = "http_post", viaTor = useTor(), source = "rest")
        val bodySummary = summarizePostBody(path, body)
        val startedAt = boltzTraceStart()
        val request = Request.Builder()
            .url("$baseUrl$path")
            .post(body.toString().toRequestBody(JSON_MEDIA))
            .build()
        logBoltzTrace("start", trace, "requestId" to requestId, "path" to path, "body" to bodySummary)
        try {
            val response = client.newCall(request).executeAsync()
            val responseBody = response.body.string()
            if (!response.isSuccessful) {
                val errorMsg = try {
                    JSONObject(responseBody).optString("error", responseBody)
                } catch (_: Exception) { responseBody }
                logBoltzTrace(
                    "failed",
                    trace,
                    level = BoltzTraceLevel.WARN,
                    throwable = Exception("Boltz API error ${response.code}: $errorMsg"),
                    "requestId" to requestId,
                    "path" to path,
                    "body" to bodySummary,
                    "code" to response.code,
                    "elapsedMs" to boltzElapsedMs(startedAt),
                    "error" to summarizeValue(errorMsg),
                )
                throw Exception("Boltz API error ${response.code}: $errorMsg")
            }
            logBoltzTrace(
                "success",
                trace,
                "requestId" to requestId,
                "path" to path,
                "body" to bodySummary,
                "code" to response.code,
                "elapsedMs" to boltzElapsedMs(startedAt),
            )
            JSONObject(responseBody)
        } catch (error: Exception) {
            if (error.message?.startsWith("Boltz API error") != true) {
                logBoltzTrace(
                    "exception",
                    trace,
                    level = BoltzTraceLevel.WARN,
                    throwable = error,
                    "requestId" to requestId,
                    "path" to path,
                    "body" to bodySummary,
                    "elapsedMs" to boltzElapsedMs(startedAt),
                )
            }
            throw error
        }
    }

    // ── Pairs (fees & limits) ──
    // Endpoints: GET /v2/swap/submarine, GET /v2/swap/reverse, GET /v2/swap/chain
    // Response: { "FROM": { "TO": { hash, rate, limits, fees } } }

    /**
     * Fetch submarine swap pair info.
     * GET /v2/swap/submarine → fees.minerFees is a flat Long.
     */
    suspend fun getSubmarinePairInfo(from: String = "L-BTC", to: String = "BTC"): BoltzPairInfo {
        val json = get("/v2/swap/submarine")
        val pair = json.getJSONObject(from).getJSONObject(to)
        return parseSubmarinePairInfo(pair)
    }

    /**
     * Fetch reverse swap pair info.
     * GET /v2/swap/reverse → fees.minerFees is { lockup, claim }.
     */
    suspend fun getReversePairInfo(from: String = "BTC", to: String = "L-BTC"): BoltzPairInfo {
        val json = get("/v2/swap/reverse")
        val pair = json.getJSONObject(from).getJSONObject(to)
        return parseReversePairInfo(pair)
    }

    /**
     * Fetch chain swap pair info.
     * GET /v2/swap/chain → fees.minerFees is { server, user: { lockup, claim } }.
     */
    suspend fun getChainPairInfo(from: String, to: String): BoltzPairInfo {
        val json = get("/v2/swap/chain")
        val pair = json.getJSONObject(from).getJSONObject(to)
        return parseChainPairInfo(pair)
    }

    private fun parseLimits(j: JSONObject): BoltzLimits {
        val limits = j.getJSONObject("limits")
        return BoltzLimits(
            minimal = limits.getLong("minimal"),
            maximal = limits.getLong("maximal"),
            maximalZeroConf = limits.optLong("maximalZeroConf", 0),
        )
    }

    /** Submarine: minerFees is a flat Long (server claim fee) */
    private fun parseSubmarinePairInfo(j: JSONObject): BoltzPairInfo {
        val fees = j.getJSONObject("fees")
        return BoltzPairInfo(
            hash = j.getString("hash"),
            rate = j.getDouble("rate"),
            limits = parseLimits(j),
            fees = BoltzFees(
                percentage = fees.getDouble("percentage"),
                serverMinerFee = fees.optLong("minerFees", 0),
            ),
        )
    }

    /** Reverse: minerFees is { lockup, claim } — Boltz lockup + user claim */
    private fun parseReversePairInfo(j: JSONObject): BoltzPairInfo {
        val fees = j.getJSONObject("fees")
        val minerFees = fees.getJSONObject("minerFees")
        return BoltzPairInfo(
            hash = j.getString("hash"),
            rate = j.getDouble("rate"),
            limits = parseLimits(j),
            fees = BoltzFees(
                percentage = fees.getDouble("percentage"),
                serverMinerFee = minerFees.optLong("lockup", 0),
                userClaimFee = minerFees.optLong("claim", 0),
            ),
        )
    }

    /** Chain: minerFees is { server, user: { lockup, claim } } */
    private fun parseChainPairInfo(j: JSONObject): BoltzPairInfo {
        val fees = j.getJSONObject("fees")
        val minerFees = fees.getJSONObject("minerFees")
        val userFees = minerFees.getJSONObject("user")
        return BoltzPairInfo(
            hash = j.getString("hash"),
            rate = j.getDouble("rate"),
            limits = parseLimits(j),
            fees = BoltzFees(
                percentage = fees.getDouble("percentage"),
                serverMinerFee = minerFees.optLong("server", 0),
                userLockupFee = userFees.optLong("lockup", 0),
                userClaimFee = userFees.optLong("claim", 0),
            ),
        )
    }

    // ── Submarine Swap: L-BTC → Lightning ──

    /**
     * Create a submarine swap to pay a Lightning invoice using L-BTC.
     * User sends L-BTC to lockup address → Boltz pays the invoice.
     */
    suspend fun createSubmarineSwap(
        invoice: String,
        refundPublicKey: String,
    ): BoltzSubmarineResponse {
        val body = JSONObject().apply {
            put("from", "L-BTC")
            put("to", "BTC")
            put("invoice", invoice)
            put("refundPublicKey", refundPublicKey)
        }
        val j = post("/v2/swap/submarine", body)
        return BoltzSubmarineResponse(
            id = j.getString("id"),
            address = j.getString("address"),
            expectedAmount = j.getLong("expectedAmount"),
            claimPublicKey = j.getString("claimPublicKey"),
            timeoutBlockHeight = j.getInt("timeoutBlockHeight"),
            swapTree = j.optString("swapTree", ""),
            blindingKey = j.optString("blindingKey").takeIf { it.isNotBlank() && !j.isNull("blindingKey") },
        )
    }

    suspend fun fetchBolt12Invoice(
        offer: String,
        amountSats: Long,
        note: String? = null,
    ): BoltzFetchedBolt12Invoice {
        require(amountSats > 0L) { "Amount must be greater than zero" }
        val body = JSONObject().apply {
            put("offer", offer)
            put("amount", amountSats)
            note?.takeIf { it.isNotBlank() }?.let { put("note", it) }
        }
        val timeoutSeconds =
            if (useTor()) {
                BOLT12_FETCH_TIMEOUT_TOR_SECONDS
            } else {
                BOLT12_FETCH_TIMEOUT_CLEARNET_SECONDS
            }
        val client =
            httpClient().newBuilder()
                .readTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .callTimeout(timeoutSeconds + 5L, TimeUnit.SECONDS)
                .build()
        val trace = BoltzTraceContext(operation = "fetchBolt12Invoice", viaTor = useTor(), source = "rest")
        val startedAt = boltzTraceStart()
        logBoltzTrace(
            "start",
            trace,
            "amountSats" to amountSats,
            "timeoutSeconds" to timeoutSeconds,
            "offer" to summarizeValue(offer),
            "note" to note?.takeIf { it.isNotBlank() }?.let(::summarizeValue),
        )
        return try {
            val j =
                try {
                    post("/v2/lightning/BTC/bolt12/fetch", body, client)
                } catch (error: IOException) {
                    logBoltzTrace(
                        "retry",
                        trace.copy(attempt = 2),
                        level = BoltzTraceLevel.WARN,
                        throwable = error,
                        "elapsedMs" to boltzElapsedMs(startedAt),
                        "amountSats" to amountSats,
                        "offer" to summarizeValue(offer),
                    )
                    post("/v2/lightning/BTC/bolt12/fetch", body, client)
                }
            logBoltzTrace(
                "success",
                trace,
                "elapsedMs" to boltzElapsedMs(startedAt),
                "amountSats" to amountSats,
            )
            BoltzFetchedBolt12Invoice(
                invoice = j.getString("invoice"),
            )
        } catch (error: Exception) {
            logBoltzTrace(
                "failed",
                trace,
                level = BoltzTraceLevel.WARN,
                throwable = error,
                "elapsedMs" to boltzElapsedMs(startedAt),
                "amountSats" to amountSats,
                "offer" to summarizeValue(offer),
            )
            throw error
        }
    }

    // ── Swap Status ──

    /** Get current swap status via REST */
    suspend fun getSwapStatus(swapId: String): String {
        val j = get("/v2/swap/$swapId")
        return j.getString("status")
    }

    // ── WebSocket Status Updates ──

    /**
     * Subscribe to real-time swap status updates via WebSocket.
     * Returns a Flow that emits BoltzSwapUpdate for every status change.
     */
    override fun subscribeToSwapUpdates(swapId: String): Flow<BoltzSwapUpdate> = callbackFlow {
        val updates = registerSharedSubscription(swapId)
        val collector = webSocketScope.launch {
            updates.collect { trySend(it).isSuccess }
        }
        awaitClose {
            collector.cancel()
            webSocketScope.launch {
                unregisterSharedSubscription(swapId)
            }
        }
    }

    fun close() {
        logBoltzTrace("close", BoltzTraceContext(operation = "closeClient", viaTor = useTor(), source = "websocket"))
        webSocketScope.cancel()
        sharedWebSocket?.close(1000, "Client closed")
    }

    private suspend fun registerSharedSubscription(swapId: String): MutableSharedFlow<BoltzSwapUpdate> {
        val flow = webSocketMutex.withLock {
            val currentCount = swapSubscriptionCounts[swapId] ?: 0
            swapSubscriptionCounts[swapId] = currentCount + 1
            pendingSubscriptions += swapId
            swapUpdateFlows.getOrPut(swapId) {
                MutableSharedFlow(
                    replay = 0,
                    extraBufferCapacity = 32,
                )
            }
        }
        logBoltzTrace(
            "subscribe",
            BoltzTraceContext(operation = "registerSharedSubscription", swapId = swapId, viaTor = useTor(), source = "websocket"),
            "pendingSubscriptions" to pendingSubscriptions.size,
        )
        ensureSharedWebSocket()
        flushPendingSubscriptions()
        return flow
    }

    private suspend fun unregisterSharedSubscription(swapId: String) {
        val shouldClose = webSocketMutex.withLock {
            val nextCount = (swapSubscriptionCounts[swapId] ?: 1) - 1
            if (nextCount > 0) {
                swapSubscriptionCounts[swapId] = nextCount
                false
            } else {
                swapSubscriptionCounts.remove(swapId)
                pendingSubscriptions.remove(swapId)
                swapUpdateFlows.remove(swapId)
                val socket = sharedWebSocket
                if (webSocketReady && socket != null) {
                    socket.send(
                        JSONObject().apply {
                            put("op", "unsubscribe")
                            put("channel", "swap.update")
                            put("args", JSONArray().put(swapId))
                        }.toString(),
                    )
                }
                swapSubscriptionCounts.isEmpty()
            }
        }
        logBoltzTrace(
            "unsubscribe",
            BoltzTraceContext(operation = "unregisterSharedSubscription", swapId = swapId, viaTor = useTor(), source = "websocket"),
            "closeSocket" to shouldClose,
        )
        if (shouldClose) {
            closeSharedWebSocket()
        }
    }

    private suspend fun ensureSharedWebSocket() {
        val expectedUrl = wsUrl
        val shouldOpen = webSocketMutex.withLock {
            if (sharedWebSocketUrl != null && sharedWebSocketUrl != expectedUrl) {
                closeSharedWebSocketLocked(resetReconnectState = true)
            }
            if (sharedWebSocket != null) {
                false
            } else {
                sharedWebSocketUrl = expectedUrl
                reconnectAttempts = 0
                true
            }
        }
        if (!shouldOpen) {
            logBoltzTrace(
                "reuse",
                BoltzTraceContext(operation = "ensureSharedWebSocket", viaTor = useTor(), source = "websocket"),
                "url" to summarizeValue(expectedUrl),
            )
            return
        }

        logBoltzTrace(
            "open_requested",
            BoltzTraceContext(operation = "ensureSharedWebSocket", viaTor = useTor(), source = "websocket"),
            "url" to summarizeValue(expectedUrl),
        )
        val request = Request.Builder().url(expectedUrl).build()
        val socket = wsClient().newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                logBoltzTrace(
                    "open",
                    BoltzTraceContext(operation = "sharedWebSocket", viaTor = useTor(), source = "websocket"),
                    "code" to response.code,
                )
                webSocketScope.launch {
                    webSocketMutex.withLock {
                        webSocketReady = true
                        reconnectAttempts = 0
                        reconnectJob?.cancel()
                        reconnectJob = null
                    }
                    flushPendingSubscriptions()
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val msg = JSONObject(text)
                    if (msg.optString("event") != "update") return
                    val args = msg.optJSONArray("args") ?: return
                    for (index in 0 until args.length()) {
                        val arg = args.optJSONObject(index) ?: continue
                        val update = BoltzSwapUpdate(
                            id = arg.optString("id"),
                            status = arg.optString("status"),
                            transactionHex = arg.optJSONObject("transaction")?.optString("hex"),
                            transactionId = arg.optJSONObject("transaction")?.optString("id"),
                        )
                        if (update.id.isBlank() || update.status.isBlank()) continue
                        logBoltzTrace(
                            "update",
                            BoltzTraceContext(
                                operation = "sharedWebSocketMessage",
                                swapId = update.id,
                                viaTor = useTor(),
                                source = "websocket",
                            ),
                            "status" to update.status,
                            "txid" to update.transactionId,
                        )
                        swapUpdateFlows[update.id]?.tryEmit(update)
                    }
                } catch (error: Exception) {
                    logBoltzTrace(
                        "malformed_update",
                        BoltzTraceContext(operation = "sharedWebSocketMessage", viaTor = useTor(), source = "websocket"),
                        level = BoltzTraceLevel.WARN,
                        throwable = error,
                        "raw" to summarizeValue(text),
                    )
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                val isTransientEof = t is java.io.EOFException
                logBoltzTrace(
                    "failure",
                    BoltzTraceContext(operation = "sharedWebSocket", viaTor = useTor(), source = "websocket"),
                    level = BoltzTraceLevel.WARN,
                    throwable = if (isTransientEof) null else t,
                    "code" to response?.code,
                    "error" to if (isTransientEof) "EOFException (server closed connection)" else null,
                )
                handleSharedWebSocketDisconnect()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                logBoltzTrace(
                    "closed",
                    BoltzTraceContext(operation = "sharedWebSocket", viaTor = useTor(), source = "websocket"),
                    "code" to code,
                    "reason" to summarizeValue(reason),
                )
                handleSharedWebSocketDisconnect()
            }
        })

        webSocketMutex.withLock {
            sharedWebSocket = socket
        }
    }

    private suspend fun flushPendingSubscriptions() {
        val payload = webSocketMutex.withLock {
            val socket = sharedWebSocket ?: return
            if (!webSocketReady || pendingSubscriptions.isEmpty()) {
                return
            }
            val subscriptions = JSONArray()
            pendingSubscriptions.forEach(subscriptions::put)
            val pendingCount = pendingSubscriptions.size
            pendingSubscriptions.clear()
            Triple(
                socket,
                pendingCount,
                JSONObject().apply {
                put("op", "subscribe")
                put("channel", "swap.update")
                put("args", subscriptions)
                }.toString(),
            )
        }
        logBoltzTrace(
            "flush_subscriptions",
            BoltzTraceContext(operation = "flushPendingSubscriptions", viaTor = useTor(), source = "websocket"),
            "pendingCount" to payload.second,
            "payload" to summarizeValue(payload.third),
        )
        payload.first.send(payload.third)
    }

    private fun handleSharedWebSocketDisconnect() {
        webSocketScope.launch {
            val shouldReconnect = webSocketMutex.withLock {
                closeSharedWebSocketLocked(resetReconnectState = false)
                swapSubscriptionCounts.isNotEmpty()
            }
            logBoltzTrace(
                "disconnect",
                BoltzTraceContext(operation = "handleSharedWebSocketDisconnect", viaTor = useTor(), source = "websocket"),
                "shouldReconnect" to shouldReconnect,
            )
            if (shouldReconnect) {
                scheduleReconnect()
            }
        }
    }

    private suspend fun scheduleReconnect() {
        val attempt = webSocketMutex.withLock {
            if (reconnectJob != null || reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
                logBoltzTrace(
                    "reconnect_skipped",
                    BoltzTraceContext(operation = "scheduleReconnect", viaTor = useTor(), source = "websocket"),
                    "attempt" to reconnectAttempts,
                    "hasPendingJob" to (reconnectJob != null),
                )
                return
            }
            reconnectAttempts += 1
            reconnectAttempts
        }
        val reconnectTask = webSocketScope.launch {
            val delayMs = (RECONNECT_BASE_DELAY_MS * 2.0.pow((attempt - 1).toDouble())).toLong()
                .coerceAtMost(RECONNECT_MAX_DELAY_MS)
            logBoltzTrace(
                "reconnect_scheduled",
                BoltzTraceContext(
                    operation = "scheduleReconnect",
                    viaTor = useTor(),
                    source = "websocket",
                    attempt = attempt,
                ),
                "delayMs" to delayMs,
            )
            kotlinx.coroutines.delay(delayMs)
            webSocketMutex.withLock {
                reconnectJob = null
            }
            ensureSharedWebSocket()
            flushPendingSubscriptions()
        }
        webSocketMutex.withLock {
            reconnectJob = reconnectTask
        }
        reconnectTask.invokeOnCompletion {
            if (attempt >= MAX_RECONNECT_ATTEMPTS) {
                logBoltzTrace(
                    "reconnect_exhausted",
                    BoltzTraceContext(
                        operation = "scheduleReconnect",
                        viaTor = useTor(),
                        source = "websocket",
                        attempt = attempt,
                    ),
                )
                webSocketScope.launch {
                    webSocketMutex.withLock {
                        reconnectJob = null
                    }
                }
            }
        }
    }

    private suspend fun closeSharedWebSocket(resetReconnectState: Boolean = true) {
        webSocketMutex.withLock {
            closeSharedWebSocketLocked(resetReconnectState = resetReconnectState)
        }
    }

    private fun closeSharedWebSocketLocked(resetReconnectState: Boolean) {
        webSocketReady = false
        if (resetReconnectState) {
            reconnectJob?.cancel()
            reconnectJob = null
            reconnectAttempts = 0
        }
        sharedWebSocket?.close(1000, "Done")
        sharedWebSocket = null
        sharedWebSocketUrl = null
    }

    private fun nextRequestId(method: String): String = "$method-${requestCounter.incrementAndGet()}"

    private fun summarizePostBody(path: String, body: JSONObject): String {
        return when (path) {
            "/v2/lightning/BTC/bolt12/fetch" ->
                "amount=${body.optLong("amount")} note=${body.optString("note").takeIf { it.isNotBlank() }?.let(::summarizeValue) ?: "none"} " +
                    "offer=${summarizeValue(body.optString("offer"))}"
            else -> body.keys().asSequence().toList().sorted().joinToString(",")
        }
    }

    private fun summarizeValue(value: String?): String {
        if (value.isNullOrBlank()) return "null"
        return if (value.length <= 12) value else "${value.take(6)}...${value.takeLast(4)}"
    }

}

/** Suspend extension for OkHttp Call */
private suspend fun okhttp3.Call.executeAsync(): Response =
    suspendCancellableCoroutine { cont ->
        cont.invokeOnCancellation { cancel() }
        enqueue(object : Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {
                if (cont.isActive) cont.resumeWithException(e)
            }
            override fun onResponse(call: okhttp3.Call, response: Response) {
                cont.resume(response)
            }
        })
    }
