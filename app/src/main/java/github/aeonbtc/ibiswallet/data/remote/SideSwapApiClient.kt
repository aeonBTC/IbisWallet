package github.aeonbtc.ibiswallet.data.remote

import android.util.Log
import github.aeonbtc.ibiswallet.BuildConfig
import github.aeonbtc.ibiswallet.data.model.SideSwapPegOrder
import github.aeonbtc.ibiswallet.data.model.SideSwapPegStatus
import github.aeonbtc.ibiswallet.data.model.SideSwapPegTx
import github.aeonbtc.ibiswallet.data.model.SideSwapServerStatus
import github.aeonbtc.ibiswallet.data.model.SideSwapWalletInfo
import github.aeonbtc.ibiswallet.util.SecureLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Client for SideSwap API over WebSocket.
 *
 * Wire format: standard JSON-RPC 2.0 with snake_case method names.
 *   Request:  {"id": N, "method": "method_name", "params": <params_or_null>}
 *   Response: {"id": N, "result": {...}} or {"id": N, "error": {...}}
 *   Push:     {"method": "method_name", "params": {...}} (no id)
 *
 * Endpoint: wss://api.sideswap.io/json-rpc-ws
 * Fee: ~0.1% service fee for peg-in/peg-out.
 *
 * Available methods (per https://sideswap.io/docs/):
 *   server_status (params: null) — min amounts & fee percentages
 *   peg (params: {peg_in, recv_addr}) — create peg-in/peg-out order
 *   peg_status (params: {peg_in, order_id}) — query peg status
 */
class SideSwapApiClient(
    private val httpClient: OkHttpClient,
    private val useTor: () -> Boolean = { false },
    private val torSocksPort: Int = 9050,
) {
    private var webSocket: WebSocket? = null
    private val requestId = AtomicInteger(1)
    private val pendingRequests = ConcurrentHashMap<Int, (Result<JSONObject>) -> Unit>()
    private val notificationListeners = ConcurrentHashMap<String, (JSONObject) -> Unit>()

    /** Reactive hot wallet balances & dynamic min amounts from subscribe_value API */
    private val _walletInfo = MutableStateFlow(SideSwapWalletInfo())
    val walletInfo: StateFlow<SideSwapWalletInfo> = _walletInfo
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected

    // OkHttpClient with 30s WebSocket ping to prevent idle disconnect
    // (SideSwap server drops connections after ~2 min of inactivity)
    private fun wsClient(): OkHttpClient {
        val builder = httpClient.newBuilder()
            .pingInterval(30, TimeUnit.SECONDS)
        if (useTor()) {
            builder.proxy(Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", torSocksPort)))
            // Force hostname resolution through SOCKS5 so Tor handles DNS remotely.
            builder.dns { hostname ->
                listOf(InetAddress.getByAddress(hostname, byteArrayOf(0, 0, 0, 0)))
            }
        }
        return builder.build()
    }

    // ── WebSocket lifecycle ──

    fun connect() {
        if (webSocket != null) return

        // Register subscribed_value notification handler for hot wallet balances
        notificationListeners["subscribed_value"] = { params ->
            handleSubscribedValue(params)
        }

        val request = Request.Builder().url(WS_URL).build()
        webSocket = wsClient().newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                _isConnected.value = true
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleMessage(text)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                SecureLog.e(TAG, "WebSocket failure", t, releaseMessage = "SideSwap WebSocket failure")
                pendingRequests.values.forEach { it(Result.failure(t)) }
                pendingRequests.clear()
                this@SideSwapApiClient.webSocket = null
                _isConnected.value = false
                _walletInfo.value = SideSwapWalletInfo()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                this@SideSwapApiClient.webSocket = null
                _isConnected.value = false
                _walletInfo.value = SideSwapWalletInfo()
            }
        })
    }

    fun disconnect() {
        webSocket?.close(1000, "Done")
        webSocket = null
        pendingRequests.clear()
        _isConnected.value = false
        _walletInfo.value = SideSwapWalletInfo()
    }

    private fun handleMessage(text: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, "Received: $text")
        try {
            val json = JSONObject(text)

            // JSON-RPC 2.0: {"id": N, "result": {...}} or {"id": N, "error": {...}}
            // Also handles notifications: {"method": "...", "params": {...}} (no id)
            if (json.has("method") && !json.has("id")) {
                // Server-push notification
                val method = json.getString("method")
                val params = json.optJSONObject("params") ?: JSONObject()
                notificationListeners[method]?.invoke(params)
                return
            }

            // Response with id
            val id = if (json.isNull("id")) -1 else json.optInt("id", -1)
            if (id > 0 && pendingRequests.containsKey(id)) {
                val callback = pendingRequests.remove(id) ?: return
                val error = json.optJSONObject("error")
                if (error != null) {
                    val message = error.optString("message", "Unknown SideSwap error")
                    SecureLog.w(TAG, "SideSwap request failed: $message")
                    callback(Result.failure(Exception(SIDESWAP_REQUEST_FAILED_MESSAGE)))
                } else {
                    callback(Result.success(json.optJSONObject("result") ?: JSONObject()))
                }
                return
            }

            // id=null error (e.g. malformed request) — fail any single pending request
            if (json.has("error") && json.isNull("id")) {
                val error = json.getJSONObject("error")
                val message = error.optString("message", "SideSwap error")
                SecureLog.e(
                    TAG,
                    "SideSwap error (no id): $message",
                    releaseMessage = "SideSwap server returned an error",
                )
                failAllPending(Exception(SIDESWAP_REQUEST_FAILED_MESSAGE))
                return
            }

            if (BuildConfig.DEBUG) Log.w(TAG, "Unhandled SideSwap message: ${text.take(200)}")
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "Failed to parse SideSwap message: ${e.message}\nRaw: ${text.take(300)}")
            }
            failAllPending(Exception(SIDESWAP_RESPONSE_FAILED_MESSAGE, e))
        }
    }

    /** Fail all pending requests with the given error */
    private fun failAllPending(e: Exception) {
        val entries = pendingRequests.entries.toList()
        for (entry in entries) {
            pendingRequests.remove(entry.key)
            entry.value(Result.failure(e))
        }
    }

    /**
     * Build and send a JSON-RPC 2.0 request with a 15s timeout.
     * Format: {"id": N, "method": "method_name", "params": <params>}
     *
     * Method names use snake_case per SideSwap API docs:
     * server_status, peg, peg_status, etc.
     */
    private suspend fun rpcCall(method: String, params: Any?): JSONObject =
        withTimeout(15_000L) {
            suspendCancellableCoroutine { cont ->
                val id = requestId.getAndIncrement()

                val message = JSONObject().apply {
                    put("id", id)
                    put("method", method)
                    put("params", params ?: JSONObject.NULL)
                }

                pendingRequests[id] = { result ->
                    result.fold(
                        onSuccess = { if (cont.isActive) cont.resume(it) },
                        onFailure = { if (cont.isActive) cont.resumeWithException(it) },
                    )
                }

                cont.invokeOnCancellation { pendingRequests.remove(id) }

                val msgStr = message.toString()
                if (BuildConfig.DEBUG) Log.d(TAG, "Sending: $msgStr")
                val sent = webSocket?.send(msgStr) ?: false
                if (!sent) {
                    pendingRequests.remove(id)
                    if (cont.isActive) cont.resumeWithException(
                        Exception(SIDESWAP_WEBSOCKET_NOT_CONNECTED_MESSAGE),
                    )
                }
            }
        }

    // ── Server Status ──

    /** Fetch server status including min amounts, fee percentages, and fee rates */
    suspend fun getServerStatus(): SideSwapServerStatus {
        ensureConnected()
        // server_status takes null params (NOT empty object — that causes an error)
        val result = rpcCall("server_status", null)

        // Parse fastest BTC fee rate (blocks=2) from bitcoin_fee_rates array
        val feeRates = result.optJSONArray("bitcoin_fee_rates")
        val btcFeeRate = if (feeRates != null && feeRates.length() > 0) {
            feeRates.getJSONObject(0).optDouble("value", 1.0)
        } else {
            1.0 // conservative fallback
        }

        return SideSwapServerStatus(
            minPegInAmount = result.optLong("min_peg_in_amount", 0),
            minPegOutAmount = result.optLong("min_peg_out_amount", 0),
            serverFeePercentPegIn = result.optDouble("server_fee_percent_peg_in", 0.1),
            serverFeePercentPegOut = result.optDouble("server_fee_percent_peg_out", 0.1),
            bitcoinFeeRate = btcFeeRate,
            elementsFeeRate = result.optDouble("elements_fee_rate", 0.1),
            pegOutBitcoinTxVsize = result.optInt("peg_out_bitcoin_tx_vsize", 141),
        )
    }

    // ── Peg-In: BTC → L-BTC ──

    /**
     * Create a peg-in order. Returns a BTC address to send funds to.
     * @param recvAddr Liquid address to receive L-BTC
     */
    suspend fun createPegIn(recvAddr: String): SideSwapPegOrder {
        ensureConnected()
        val params = JSONObject().apply {
            put("peg_in", true)
            put("recv_addr", recvAddr)
        }
        val result = rpcCall("peg", params)
        return SideSwapPegOrder(
            orderId = result.getString("order_id"),
            pegAddress = result.getString("peg_addr"),
            createdAt = result.optLong("created_at", System.currentTimeMillis()),
        )
    }

    // ── Peg-Out: L-BTC → BTC ──

    /**
     * Create a peg-out order. Returns a Liquid address to send L-BTC to.
     * @param recvAddr Bitcoin address to receive BTC
     */
    suspend fun createPegOut(recvAddr: String): SideSwapPegOrder {
        ensureConnected()
        val params = JSONObject().apply {
            put("peg_in", false)
            put("recv_addr", recvAddr)
        }
        val result = rpcCall("peg", params)
        return SideSwapPegOrder(
            orderId = result.getString("order_id"),
            pegAddress = result.getString("peg_addr"),
            createdAt = result.optLong("created_at", System.currentTimeMillis()),
        )
    }

    // ── Peg Status Monitoring ──

    /** Get current peg status */
    suspend fun getPegStatus(orderId: String, pegIn: Boolean): SideSwapPegStatus {
        ensureConnected()
        val params = JSONObject().apply {
            put("order_id", orderId)
            put("peg_in", pegIn)
        }
        val result = rpcCall("peg_status", params)
        return parsePegStatus(result)
    }

    private fun parsePegStatus(j: JSONObject): SideSwapPegStatus {
        val list = j.optJSONArray("list") ?: org.json.JSONArray()
        val transactions = (0 until list.length()).map { i ->
            val tx = list.getJSONObject(i)
            SideSwapPegTx(
                txHash = tx.getString("tx_hash"),
                amount = tx.getLong("amount"),
                payout = if (tx.isNull("payout")) null else tx.optLong("payout"),
                payoutTxid = if (tx.isNull("payout_txid")) null else tx.optString("payout_txid"),
                status = tx.optString("status", ""),
                txState = tx.optString("tx_state", ""),
                detectedConfs = if (tx.isNull("detected_confs")) null else tx.optInt("detected_confs"),
                totalConfs = if (tx.isNull("total_confs")) null else tx.optInt("total_confs"),
            )
        }
        return SideSwapPegStatus(
            orderId = j.optString("order_id", ""),
            pegIn = j.optBoolean("peg_in", true),
            addr = j.optString("addr", ""),
            addrRecv = j.optString("addr_recv", ""),
            transactions = transactions,
        )
    }

    // ── Hot Wallet Balance & Dynamic Min Amounts ──

    /**
     * Subscribe to all four wallet info values from SideSwap.
     * Each triggers a `subscribed_value` notification immediately with the current value,
     * then pushes updates whenever the value changes.
     *
     * Values:
     *   PegInMinAmount     → dynamic minimum peg-in amount
     *   PegInWalletBalance → L-BTC hot wallet; amount <= this → 2 conf, else 102 conf
     *   PegOutMinAmount    → dynamic minimum peg-out amount
     *   PegOutWalletBalance→ BTC hot wallet; amount <= this → ~2 min, else ~20-30 min
     */
    suspend fun subscribeWalletInfo() {
        ensureConnected()
        for (value in SUBSCRIBE_VALUES) {
            try {
                val params = JSONObject().apply { put("value", value) }
                rpcCall("subscribe_value", params)
            } catch (e: Exception) {
                SecureLog.e(TAG, "Failed to subscribe to $value: ${e.message}", e)
            }
        }
    }

    /** Unsubscribe from all wallet info values */
    suspend fun unsubscribeWalletInfo() {
        if (!_isConnected.value) return

        for (value in SUBSCRIBE_VALUES) {
            try {
                val params = JSONObject().apply { put("value", value) }
                rpcCall("unsubscribe_value", params)
            } catch (e: Exception) {
                if (e.message == SIDESWAP_WEBSOCKET_NOT_CONNECTED_MESSAGE) return
                SecureLog.w(TAG, "Failed to unsubscribe from $value: ${e.message}", e)
            }
        }
    }

    /**
     * Parse `subscribed_value` notification.
     * Format: {"value": {"PegInWalletBalance": {"available": 184900000}}}
     *    or:  {"value": {"PegInMinAmount": {"min_amount": 1286}}}
     */
    private fun handleSubscribedValue(params: JSONObject) {
        try {
            val valueObj = params.optJSONObject("value") ?: return
            val current = _walletInfo.value

            val updated = current.copy(
                pegInMinAmount = valueObj.optJSONObject("PegInMinAmount")
                    ?.optLong("min_amount", current.pegInMinAmount)
                    ?: current.pegInMinAmount,
                pegInWalletBalance = valueObj.optJSONObject("PegInWalletBalance")
                    ?.optLong("available", current.pegInWalletBalance)
                    ?: current.pegInWalletBalance,
                pegOutMinAmount = valueObj.optJSONObject("PegOutMinAmount")
                    ?.optLong("min_amount", current.pegOutMinAmount)
                    ?: current.pegOutMinAmount,
                pegOutWalletBalance = valueObj.optJSONObject("PegOutWalletBalance")
                    ?.optLong("available", current.pegOutWalletBalance)
                    ?: current.pegOutWalletBalance,
            )

            if (updated != current) {
                if (BuildConfig.DEBUG) Log.d(TAG, "Wallet info updated: $updated")
                _walletInfo.value = updated
            }
        } catch (e: Exception) {
            SecureLog.e(TAG, "Failed to parse subscribed_value: ${e.message}", e)
        }
    }

    private fun ensureConnected() {
        if (webSocket == null) connect()
    }

    companion object {
        private const val TAG = "SideSwapApiClient"
        private const val WS_URL = "wss://api.sideswap.io/json-rpc-ws"
        private const val SIDESWAP_REQUEST_FAILED_MESSAGE = "SideSwap request failed"
        private const val SIDESWAP_RESPONSE_FAILED_MESSAGE = "SideSwap response could not be processed"
        private const val SIDESWAP_WEBSOCKET_NOT_CONNECTED_MESSAGE = "SideSwap WebSocket not connected"

        private val SUBSCRIBE_VALUES = listOf(
            "PegInMinAmount",
            "PegInWalletBalance",
            "PegOutMinAmount",
            "PegOutWalletBalance",
        )
    }
}
