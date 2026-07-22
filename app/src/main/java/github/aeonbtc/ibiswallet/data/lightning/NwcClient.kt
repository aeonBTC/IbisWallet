package github.aeonbtc.ibiswallet.data.lightning

import github.aeonbtc.ibiswallet.data.model.DecodedLightningNodeInvoice
import github.aeonbtc.ibiswallet.data.model.LightningNodeBalance
import github.aeonbtc.ibiswallet.data.model.LightningNodeConfig
import github.aeonbtc.ibiswallet.data.model.LightningNodeConnectionType
import github.aeonbtc.ibiswallet.data.model.LightningNodeInfo
import github.aeonbtc.ibiswallet.data.model.LightningNodeInvoice
import github.aeonbtc.ibiswallet.data.model.LightningNodePayment
import github.aeonbtc.ibiswallet.data.model.LightningNodePaymentDirection
import github.aeonbtc.ibiswallet.data.model.LightningNodePaymentResult
import github.aeonbtc.ibiswallet.data.model.LightningNodePaymentStatus
import github.aeonbtc.ibiswallet.data.model.LightningNodeOnchainTransaction
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.bitcoinj.crypto.ECKey
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.math.BigInteger
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Nostr Wallet Connect (NIP-47) backend.
 * Communicates over WebSocket relays; supports Tor SOCKS for wss relays.
 */
class NwcClient(
    private val torSocksPort: Int = 9050,
) : LightningNodeBackend {
    private var parsed: ParsedNwcUri? = null
    private var clientSecretKey: ECKey? = null
    private var clientPubkeyHex: String? = null
    private var useTor: Boolean = false
    private val baseHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(45, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.SECONDS)
            .writeTimeout(45, TimeUnit.SECONDS)
            .pingInterval(20, TimeUnit.SECONDS)
            .build()

    override suspend fun connect(config: LightningNodeConfig): LightningNodeInfo =
        withContext(Dispatchers.IO) {
            require(config.type == LightningNodeConnectionType.NWC) { "Expected NWC connection type" }
            val uri = NwcUriParser.parse(config.nwcUri)
            parsed = uri
            useTor =
                config.useTor ||
                uri.relays.any { it.contains(".onion", ignoreCase = true) }
            if (uri.relays.any { it.contains(".onion", ignoreCase = true) } && !useTor) {
                throw IllegalStateException("Onion NWC relays require Tor")
            }
            val secretBytes = hexToBytes(uri.secret)
            val key = ECKey.fromPrivate(BigInteger(1, secretBytes), true)
            clientSecretKey = key
            // NIP-01 / BIP-340 x-only pubkey (32-byte, lowercase hex)
            clientPubkeyHex = xOnlyPubkeyHex(key)
            // get_info validates the connection end-to-end
            val infoResult = request("get_info", JSONObject())
            LightningNodeInfo(
                alias = infoResult.optString("alias").ifBlank { "NWC" },
                pubkey = infoResult.optString("pubkey").ifBlank { uri.walletPubkey },
                version = infoResult.optString("version").ifBlank { null },
                network = infoResult.optString("network").ifBlank { null },
                numActiveChannels = null,
                syncedToChain = true,
            )
        }

    override suspend fun ping(): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                request("get_info", JSONObject())
                true
            }.getOrDefault(false)
        }

    override suspend fun getBalance(): LightningNodeBalance =
        withContext(Dispatchers.IO) {
            val result = request("get_balance", JSONObject())
            val balanceMsats = result.optLong("balance", 0L)
            LightningNodeBalance(localBalanceSats = balanceMsats / 1000L, remoteBalanceSats = 0)
        }

    override suspend fun listPayments(limit: Int): List<LightningNodePayment> =
        withContext(Dispatchers.IO) {
            val params =
                JSONObject().apply {
                    put("limit", limit)
                }
            val result =
                runCatching { request("list_transactions", params) }
                    .getOrElse { return@withContext emptyList() }
            val arr = result.optJSONArray("transactions") ?: JSONArray()
            buildList {
                for (i in 0 until arr.length()) {
                    val tx = arr.getJSONObject(i)
                    val type = tx.optString("type", "outgoing")
                    val amountMsats = tx.optLong("amount", tx.optLong("amount_msat", 0L))
                    val feeMsats = tx.optLong("fees_paid", tx.optLong("fee_msat", 0L))
                    val created = tx.optLong("created_at", 0L) * 1000L
                    val settled = tx.optLong("settled_at", 0L).takeIf { it > 0 }?.times(1000L) ?: created
                    val direction =
                        if (type.equals("incoming", ignoreCase = true)) {
                            LightningNodePaymentDirection.INCOMING
                        } else {
                            LightningNodePaymentDirection.OUTGOING
                        }
                    val state = tx.optString("state", "settled")
                    val status =
                        when {
                            state.equals("pending", true) -> LightningNodePaymentStatus.PENDING
                            state.equals("failed", true) -> LightningNodePaymentStatus.FAILED
                            else -> LightningNodePaymentStatus.SUCCEEDED
                        }
                    add(
                        LightningNodePayment(
                            id = tx.optString("payment_hash").ifBlank { "nwc-$i" },
                            direction = direction,
                            status = status,
                            amountSats = amountMsats / 1000L,
                            feeSats = feeMsats / 1000L,
                            timestamp = settled,
                            paymentHash = tx.optString("payment_hash").ifBlank { null },
                            // Prefer invoice / offer / LN address-style string when wallet provides it.
                            paymentRequest =
                                tx.optString("invoice").ifBlank {
                                    tx.optString("bolt11").ifBlank {
                                        tx.optString("bolt12").ifBlank {
                                            tx.optString("payment_request").ifBlank {
                                                tx.optString("offer").ifBlank { null }
                                            }
                                        }
                                    }
                                },
                            memo = tx.optString("description").ifBlank { null },
                            destination =
                                tx.optString("payee_pubkey").ifBlank {
                                    tx.optString("destination").ifBlank {
                                        tx.optString("pubkey").ifBlank { null }
                                    }
                                },
                            destinationAlias =
                                tx.optString("payee_alias").ifBlank {
                                    tx.optString("alias").ifBlank {
                                        tx.optString("name").ifBlank { null }
                                    }
                                },
                            failureReason =
                                if (status == LightningNodePaymentStatus.FAILED) {
                                    tx.optString("error").ifBlank {
                                        tx.optString("failure_reason").ifBlank {
                                            tx.optString("message").ifBlank { null }
                                        }
                                    }
                                } else {
                                    null
                                },
                        ),
                    )
                }
            }.sortedByDescending { it.timestamp }
        }

    override suspend fun getOnchainBalance(): Long =
        throw UnsupportedOperationException("NWC does not expose the node's on-chain wallet")

    override suspend fun newOnchainAddress(): String =
        throw UnsupportedOperationException("NWC does not expose the node's on-chain wallet")

    override suspend fun listOnchainTransactions(limit: Int): List<LightningNodeOnchainTransaction> =
        throw UnsupportedOperationException("NWC does not expose the node's on-chain wallet")

    override suspend fun listOnchainUtxos(): List<github.aeonbtc.ibiswallet.data.model.UtxoInfo> =
        throw UnsupportedOperationException("NWC does not expose the node's on-chain wallet")

    override suspend fun sendOnchain(
        address: String,
        amountSats: Long?,
        satPerVbyte: Double?,
        sendAll: Boolean,
        label: String?,
        spendUnconfirmed: Boolean,
        selectedOutpoints: List<github.aeonbtc.ibiswallet.data.model.UtxoInfo>?,
    ): String = throw UnsupportedOperationException("NWC does not expose the node's on-chain wallet")

    override suspend fun sendOnchainMany(
        addrToAmountSats: Map<String, Long>,
        satPerVbyte: Double?,
        label: String?,
        spendUnconfirmed: Boolean,
        selectedOutpoints: List<github.aeonbtc.ibiswallet.data.model.UtxoInfo>?,
    ): String = throw UnsupportedOperationException("NWC does not expose the node's on-chain wallet")

    override suspend fun bumpOnchainFee(
        outpoint: github.aeonbtc.ibiswallet.data.model.UtxoInfo,
        satPerVbyte: Double?,
        immediate: Boolean,
        budgetSats: Long?,
    ): String = throw UnsupportedOperationException("NWC does not expose the node's on-chain wallet")

    override suspend fun createInvoice(
        amountSats: Long?,
        description: String?,
    ): LightningNodeInvoice =
        withContext(Dispatchers.IO) {
            val params =
                JSONObject().apply {
                    if (amountSats != null && amountSats > 0) {
                        put("amount", amountSats * 1000L)
                    }
                    // Memo only when user explicitly set one — never default.
                    description?.trim()?.takeIf { it.isNotBlank() }?.let { put("description", it) }
                    put("expiry", 3600)
                }
            val result = request("make_invoice", params)
            val invoice = result.optString("invoice")
            require(invoice.isNotBlank()) { "NWC did not return an invoice" }
            LightningNodeInvoice(
                paymentRequest = invoice,
                paymentHash = result.optString("payment_hash").ifBlank { null },
                amountSats = amountSats,
                description = description,
                expirySeconds = 3600,
            )
        }

    override suspend fun decodeInvoice(bolt11: String): DecodedLightningNodeInvoice =
        withContext(Dispatchers.IO) {
            val requestStr = bolt11.trim().removePrefix("lightning:").trim()
            val params = JSONObject().put("invoice", requestStr)
            val result =
                runCatching { request("lookup_invoice", params) }
                    .getOrElse {
                        // Fallback: basic emptiness check; amount filled by user if unknown
                        return@withContext DecodedLightningNodeInvoice(paymentRequest = requestStr)
                    }
            DecodedLightningNodeInvoice(
                paymentRequest = requestStr,
                paymentHash = result.optString("payment_hash").ifBlank { null },
                amountSats = result.optLong("amount", 0L).takeIf { it > 0 }?.div(1000L),
                description = result.optString("description").ifBlank { null },
                destination = null,
                expirySeconds = result.optLong("expires_at", 0L).takeIf { it > 0 },
            )
        }

    override suspend fun payInvoice(
        bolt11: String,
        amountSats: Long?,
        maxFeePercent: Double?,
    ): LightningNodePaymentResult =
        withContext(Dispatchers.IO) {
            val requestStr = bolt11.trim().removePrefix("lightning:").trim()
            val amountMsats =
                if (amountSats != null && amountSats > 0) {
                    amountSats * 1000L
                } else {
                    null
                }

            fun buildParams(includeAmountMsats: Long?): JSONObject =
                JSONObject().apply {
                    put("invoice", requestStr)
                    if (includeAmountMsats != null && includeAmountMsats > 0) {
                        put("amount", includeAmountMsats)
                    }
                }

            val result =
                runCatching { request("pay_invoice", buildParams(amountMsats)) }
                    .getOrElse { firstError ->
                        // If wallet still rejects amount on a fixed invoice (decode failed earlier),
                        // retry once without amount.
                        val msg = firstError.message.orEmpty()
                        val isFixedAmountReject =
                            amountMsats != null &&
                                (
                                    msg.contains("must not be specified", ignoreCase = true) ||
                                        msg.contains("amount must not", ignoreCase = true) ||
                                        msg.contains("non-zero amount invoice", ignoreCase = true)
                                    )
                        if (!isFixedAmountReject) throw firstError
                        request("pay_invoice", buildParams(null))
                    }
            if (result.has("error") && !result.isNull("error")) {
                val err = result.opt("error")
                val msg =
                    when (err) {
                        is JSONObject ->
                            err.optString("message").ifBlank { err.optString("code") }
                        is String -> err
                        else -> err?.toString().orEmpty()
                    }
                throw IllegalStateException(msg.ifBlank { "Payment failed" })
            }
            // NIP-47: preimage is proof of payment — required before we show success.
            val preimage =
                result.optString("preimage").ifBlank {
                    result.optString("payment_preimage")
                }.ifBlank { null }
                    ?: throw IllegalStateException("Payment did not return a preimage")
            if (preimage.matches(Regex("^0+$")) || preimage.matches(Regex("^(00)+$"))) {
                throw IllegalStateException("Payment did not return a preimage")
            }
            val fees = result.optLong("fees_paid", result.optLong("fee_msat", 0L)) / 1000L
            LightningNodePaymentResult(
                paymentId = result.optString("payment_hash").ifBlank { null },
                paymentHash = result.optString("payment_hash").ifBlank { null },
                feeSats = fees,
                preimage = preimage,
            )
        }

    override suspend fun disconnect() {
        parsed = null
        clientSecretKey = null
        clientPubkeyHex = null
    }

    private suspend fun request(
        method: String,
        params: JSONObject,
    ): JSONObject {
        val uri = parsed ?: throw IllegalStateException("Not connected")
        val clientKey = clientSecretKey ?: throw IllegalStateException("Not connected")
        val clientPub = clientPubkeyHex ?: throw IllegalStateException("Not connected")

        val payload =
            JSONObject()
                .put("method", method)
                .put("params", params)
        // Prefer NIP-44 (current Alby / NIP-47 default); fall back not offered until
        // a wallet's info event says it only supports nip04.
        val encrypted = Nip44.encrypt(payload.toString(), clientKey, uri.walletPubkey)
        val createdAt = System.currentTimeMillis() / 1000L
        val tags =
            JSONArray()
                .put(JSONArray().put("p").put(uri.walletPubkey))
                .put(JSONArray().put("encryption").put("nip44_v2"))
        val eventId =
            computeEventId(
                pubkey = clientPub,
                createdAt = createdAt,
                kind = 23194,
                tags = tags,
                content = encrypted,
            )
        val signature = signEventId(eventId, clientKey)
        val event =
            JSONObject()
                .put("id", eventId)
                .put("pubkey", clientPub)
                .put("created_at", createdAt)
                .put("kind", 23194)
                .put("tags", tags)
                .put("content", encrypted)
                .put("sig", signature)

        var lastError: Exception? = null
        for (relay in uri.relays) {
            try {
                // Prefer withTimeoutOrNull over withTimeout: TimeoutCancellationException
                // is a CancellationException and was escaping the test path without a UI error.
                val result =
                    withTimeoutOrNull(REQUEST_TIMEOUT_MS) {
                        sendAndAwaitResponse(
                            relayUrl = relay,
                            event = event,
                            eventId = eventId,
                            subscriptionId = "ibis-${UUID.randomUUID().toString().take(8)}",
                            walletPubkey = uri.walletPubkey,
                            clientKey = clientKey,
                            clientPub = clientPub,
                        )
                    }
                if (result != null) return result
                lastError =
                    IllegalStateException(
                        "Timed out waiting for NWC wallet on ${shortRelay(relay)}",
                    )
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException &&
                    e !is kotlinx.coroutines.TimeoutCancellationException
                ) {
                    throw e
                }
                lastError =
                    IllegalStateException(
                        "${shortRelay(relay)}: ${e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName}",
                        e,
                    )
            }
        }
        throw lastError
            ?: IllegalStateException("All NWC relays failed — check URI, relay reachability, and Tor if enabled")
    }

    private suspend fun sendAndAwaitResponse(
        relayUrl: String,
        event: JSONObject,
        eventId: String,
        subscriptionId: String,
        walletPubkey: String,
        clientKey: ECKey,
        clientPub: String,
    ): JSONObject {
        val deferred = CompletableDeferred<JSONObject>()
        val client = httpClient()
        val request = Request.Builder().url(relayUrl).build()
        val filter =
            JSONObject()
                .put("kinds", JSONArray().put(23195))
                .put("authors", JSONArray().put(walletPubkey))
                .put("#p", JSONArray().put(clientPub))
                .put("since", (System.currentTimeMillis() / 1000L) - 30)

        val listener =
            object : WebSocketListener() {
                override fun onOpen(
                    webSocket: WebSocket,
                    response: Response,
                ) {
                    // Subscribe first so we don't miss a fast response, then publish the request.
                    webSocket.send(JSONArray().put("REQ").put(subscriptionId).put(filter).toString())
                    webSocket.send(JSONArray().put("EVENT").put(event).toString())
                }

                override fun onMessage(
                    webSocket: WebSocket,
                    text: String,
                ) {
                    if (deferred.isCompleted) return
                    runCatching {
                        val arr = JSONArray(text)
                        if (arr.length() < 2) return
                        when (arr.optString(0)) {
                            "OK" -> {
                                // ["OK", <event_id>, <true|false>, <message>]
                                val okId = arr.optString(1)
                                val accepted = arr.optBoolean(2, true)
                                val msg = arr.optString(3, "")
                                if (okId.equals(eventId, ignoreCase = true) && !accepted) {
                                    deferred.completeExceptionally(
                                        IllegalStateException(
                                            "Relay rejected NWC request: ${msg.ifBlank { "invalid event" }}",
                                        ),
                                    )
                                    webSocket.close(1000, null)
                                }
                            }
                            "NOTICE" -> {
                                val notice = arr.optString(1)
                                if (notice.isNotBlank() &&
                                    (
                                        notice.contains("blocked", ignoreCase = true) ||
                                            notice.contains("auth", ignoreCase = true) ||
                                            notice.contains("error", ignoreCase = true)
                                    )
                                ) {
                                    // Soft signal only for hard-looking notices; continue waiting otherwise.
                                    if (!deferred.isCompleted && notice.contains("auth-required", ignoreCase = true)) {
                                        deferred.completeExceptionally(
                                            IllegalStateException("Relay requires auth: $notice"),
                                        )
                                        webSocket.close(1000, null)
                                    }
                                }
                            }
                            "EVENT" -> {
                                if (arr.length() < 3) return
                                val eventObj = arr.optJSONObject(2) ?: return
                                if (eventObj.optInt("kind") != 23195) return
                                // Prefer responses that reference our request event id (NIP-47 `e` tag).
                                val tags = eventObj.optJSONArray("tags")
                                val referencedRequest =
                                    tags?.let { tagArr ->
                                        (0 until tagArr.length()).any { i ->
                                            val t = tagArr.optJSONArray(i) ?: return@any false
                                            t.optString(0) == "e" &&
                                                t.optString(1).equals(eventId, ignoreCase = true)
                                        }
                                    } ?: true
                                if (!referencedRequest) return
                                val content = eventObj.optString("content")
                                if (content.isBlank()) return
                                val plaintext =
                                    runCatching {
                                        decryptNwcContent(content, clientKey, walletPubkey)
                                    }.getOrElse { decryptError ->
                                        deferred.completeExceptionally(
                                            IllegalStateException(
                                                "Failed to decrypt NWC response: ${decryptError.message}",
                                                decryptError,
                                            ),
                                        )
                                        webSocket.close(1000, null)
                                        return
                                    }
                                val responseJson = JSONObject(plaintext)
                                if (responseJson.has("error") && !responseJson.isNull("error")) {
                                    val err = responseJson.optJSONObject("error")
                                    val code = err?.optString("code").orEmpty()
                                    val message =
                                        err?.optString("message")?.ifBlank { null }
                                            ?: "NWC error"
                                    deferred.completeExceptionally(
                                        IllegalStateException(
                                            if (code.isNotBlank()) "$code: $message" else message,
                                        ),
                                    )
                                    webSocket.close(1000, null)
                                    return
                                }
                                // NIP-47 success payload may be { result_type, result } or bare result.
                                val result =
                                    responseJson.optJSONObject("result")
                                        ?: if (responseJson.has("result_type")) {
                                            responseJson.optJSONObject("result")
                                                ?: JSONObject()
                                        } else {
                                            responseJson
                                        }
                                deferred.complete(result)
                                webSocket.close(1000, null)
                            }
                            "CLOSED" -> {
                                val reason = arr.optString(2)
                                if (!deferred.isCompleted && reason.isNotBlank()) {
                                    deferred.completeExceptionally(
                                        IllegalStateException("Subscription closed: $reason"),
                                    )
                                }
                            }
                        }
                    }.onFailure { parseError ->
                        if (!deferred.isCompleted) {
                            deferred.completeExceptionally(
                                IllegalStateException(
                                    "Bad relay message: ${parseError.message}",
                                    parseError,
                                ),
                            )
                        }
                    }
                }

                override fun onFailure(
                    webSocket: WebSocket,
                    t: Throwable,
                    response: Response?,
                ) {
                    if (!deferred.isCompleted) {
                        val code = response?.code
                        val detail =
                            buildString {
                                append(t.message?.ifBlank { null } ?: t.javaClass.simpleName)
                                if (code != null) append(" (HTTP $code)")
                            }
                        deferred.completeExceptionally(IOException("WebSocket failed: $detail", t))
                    }
                }
            }

        val ws = client.newWebSocket(request, listener)
        try {
            return deferred.await()
        } finally {
            runCatching { ws.close(1000, null) }
            ws.cancel()
        }
    }

    private fun shortRelay(relayUrl: String): String =
        relayUrl
            .removePrefix("wss://")
            .removePrefix("ws://")
            .substringBefore('/')
            .ifBlank { relayUrl }

    private fun httpClient(): OkHttpClient {
        val builder =
            baseHttpClient
                .newBuilder()
                .connectTimeout(if (useTor) 90 else 45, TimeUnit.SECONDS)
                .writeTimeout(if (useTor) 90 else 45, TimeUnit.SECONDS)
        if (useTor) {
            builder.proxy(Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", torSocksPort)))
            // Force hostname resolution through SOCKS so Tor handles .onion / remote DNS.
            builder.dns { hostname ->
                listOf(InetAddress.getByAddress(hostname, byteArrayOf(0, 0, 0, 0)))
            }
        }
        return builder.build()
    }

    companion object {
        private const val REQUEST_TIMEOUT_MS = 45_000L

        /**
         * Prefer NIP-44 (modern wallets). Fall back to legacy NIP-04 payloads of the form
         * `base64?iv=base64` when the cipher text is not a valid NIP-44 v2 blob.
         */
        private fun decryptNwcContent(
            payload: String,
            privateKey: ECKey,
            senderPubkeyHex: String,
        ): String {
            val nip44Error =
                runCatching {
                    return Nip44.decrypt(payload, privateKey, senderPubkeyHex)
                }.exceptionOrNull()
            return runCatching {
                nip04Decrypt(payload, privateKey, senderPubkeyHex)
            }.getOrElse { nip04Error ->
                throw IllegalStateException(
                    "NIP-44: ${nip44Error?.message ?: "failed"}; NIP-04: ${nip04Error.message}",
                    nip04Error,
                )
            }
        }

        private fun nip04Decrypt(
            payload: String,
            privateKey: ECKey,
            senderPubkeyHex: String,
        ): String {
            val parts = payload.split("?iv=")
            require(parts.size == 2) { "Invalid NIP-04 payload" }
            val encrypted = android.util.Base64.decode(parts[0], android.util.Base64.DEFAULT)
            val iv = android.util.Base64.decode(parts[1], android.util.Base64.DEFAULT)
            val shared = ecdh(privateKey, senderPubkeyHex)
            val key = sha256(shared)
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
            return String(cipher.doFinal(encrypted), StandardCharsets.UTF_8)
        }

        private fun ecdh(
            privateKey: ECKey,
            remotePubkeyHex: String,
        ): ByteArray {
            val remoteBytes =
                when (remotePubkeyHex.length) {
                    64 -> hexToBytes("02$remotePubkeyHex")
                    66 -> hexToBytes(remotePubkeyHex)
                    else -> throw IllegalArgumentException("Invalid remote pubkey")
                }
            val remote = ECKey.fromPublicOnly(remoteBytes)
            val sharedPoint = remote.pubKeyPoint.multiply(privateKey.privKey)
            return pad32(sharedPoint.normalize().xCoord.encoded)
        }

        private fun computeEventId(
            pubkey: String,
            createdAt: Long,
            kind: Int,
            tags: JSONArray,
            content: String,
        ): String {
            // NIP-01: id = sha256 of UTF-8 of the JSON array
            // [0, pubkey, created_at, kind, tags, content]
            // Must match standard JSON (NO org.json slash-escaping: "/" -> "\/"), or relays
            // recompute a different id and reject with "bad event id".
            val serialized =
                buildString {
                    append('[')
                    append('0')
                    append(',')
                    appendJsonString(pubkey)
                    append(',')
                    append(createdAt)
                    append(',')
                    append(kind)
                    append(',')
                    appendJsonArray(tags)
                    append(',')
                    appendJsonString(content)
                    append(']')
                }
            return sha256Hex(serialized.toByteArray(StandardCharsets.UTF_8))
        }

        /** JSON string encoding without optional solidus escaping (RFC 8259). */
        private fun StringBuilder.appendJsonString(value: String): StringBuilder {
            append('"')
            for (ch in value) {
                when (ch) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\b' -> append("\\b")
                    '\u000c' -> append("\\f")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> {
                        if (ch.code < 0x20) {
                            append("\\u")
                            append("%04x".format(ch.code))
                        } else {
                            append(ch)
                        }
                    }
                }
            }
            append('"')
            return this
        }

        private fun StringBuilder.appendJsonArray(arr: JSONArray): StringBuilder {
            append('[')
            for (i in 0 until arr.length()) {
                if (i > 0) append(',')
                when (val v = arr.opt(i)) {
                    null, JSONObject.NULL -> append("null")
                    is JSONArray -> appendJsonArray(v)
                    is Number -> append(v.toLong())
                    is Boolean -> append(v)
                    else -> appendJsonString(v.toString())
                }
            }
            append(']')
            return this
        }

        private fun signEventId(
            eventIdHex: String,
            privateKey: ECKey,
        ): String {
            // NIP-01 uses BIP-340 Schnorr. If bitcoinj cannot schnorr-sign, fall back is not accepted by relays.
            // Use deterministic ECDSA as last resort is incorrect for Nostr — implement schnorr via tagged hash.
            return schnorrSign(hexToBytes(eventIdHex), privateKey)
        }

        /**
         * BIP-340 Schnorr signature over secp256k1 message (32-byte).
         * Minimal implementation enough for NWC event auth.
         */
        private fun schnorrSign(
            message32: ByteArray,
            privateKey: ECKey,
        ): String {
            require(message32.size == 32)
            val curve = ECKey.ecDomainParameters()
            val d = privateKey.privKey
            val p = privateKey.pubKeyPoint.normalize()
            var dAdj = d
            if (p.yCoord.toBigInteger().testBit(0)) {
                dAdj = curve.n.subtract(d)
            }
            val px = pad32(p.xCoord.encoded)

            val aux = ByteArray(32).also { SecureRandom().nextBytes(it) }
            val t = xor(pad32(dAdj.toByteArrayFixed(32)), taggedHash("BIP0340/aux", aux))
            val rand = taggedHash("BIP0340/nonce", t + px + message32)
            var k = BigInteger(1, rand).mod(curve.n)
            if (k == BigInteger.ZERO) error("Invalid nonce")
            val R = curve.g.multiply(k).normalize()
            if (R.yCoord.toBigInteger().testBit(0)) {
                k = curve.n.subtract(k)
            }
            val rx = pad32(R.xCoord.encoded)
            val eHASH = taggedHash("BIP0340/challenge", rx + px + message32)
            val e = BigInteger(1, eHASH).mod(curve.n)
            val s = k.add(e.multiply(dAdj)).mod(curve.n)
            val sig = rx + pad32(s.toByteArrayFixed(32))
            return bytesToHex(sig)
        }

        private fun taggedHash(
            tag: String,
            msg: ByteArray,
        ): ByteArray {
            val tagHash = sha256(tag.toByteArray(StandardCharsets.UTF_8))
            return sha256(tagHash + tagHash + msg)
        }

        private fun xor(
            a: ByteArray,
            b: ByteArray,
        ): ByteArray {
            require(a.size == b.size)
            return ByteArray(a.size) { i -> (a[i].toInt() xor b[i].toInt()).toByte() }
        }

        private fun pad32(bytes: ByteArray): ByteArray =
            when {
                bytes.size == 32 -> bytes
                bytes.size > 32 -> bytes.copyOfRange(bytes.size - 32, bytes.size)
                else -> ByteArray(32 - bytes.size) + bytes
            }

        private fun xOnlyPubkeyHex(privateKey: ECKey): String =
            bytesToHex(pad32(privateKey.pubKeyPoint.normalize().xCoord.encoded))

        private fun BigInteger.toByteArrayFixed(size: Int): ByteArray {
            val raw = toByteArray()
            return pad32(
                if (raw.size > size && raw[0] == 0.toByte()) {
                    raw.copyOfRange(1, raw.size)
                } else {
                    raw
                },
            ).let {
                if (it.size == size) it else pad32(it)
            }
        }

        private fun sha256(data: ByteArray): ByteArray =
            MessageDigest.getInstance("SHA-256").digest(data)

        private fun sha256Hex(data: ByteArray): String = bytesToHex(sha256(data))

        private fun hexToBytes(hex: String): ByteArray {
            val clean = hex.removePrefix("0x")
            require(clean.length % 2 == 0)
            return ByteArray(clean.length / 2) { i ->
                clean.substring(i * 2, i * 2 + 2).toInt(16).toByte()
            }
        }

        private fun bytesToHex(bytes: ByteArray): String =
            bytes.joinToString("") { "%02x".format(it) }
    }
}
