package github.aeonbtc.ibiswallet.data.lightning

import github.aeonbtc.ibiswallet.data.model.DecodedLightningNodeInvoice
import github.aeonbtc.ibiswallet.data.model.LightningNodeBalance
import github.aeonbtc.ibiswallet.data.model.LightningNodeChannel
import github.aeonbtc.ibiswallet.data.model.LightningNodeConfig
import github.aeonbtc.ibiswallet.data.model.LightningNodeInfo
import github.aeonbtc.ibiswallet.data.model.LightningNodeInvoice
import github.aeonbtc.ibiswallet.data.model.LightningNodePayment
import github.aeonbtc.ibiswallet.data.model.LightningNodePaymentDirection
import github.aeonbtc.ibiswallet.data.model.LightningNodePaymentResult
import github.aeonbtc.ibiswallet.data.model.LightningNodePaymentStatus
import github.aeonbtc.ibiswallet.data.model.LightningNodeOnchainBalanceDetails
import github.aeonbtc.ibiswallet.data.model.LightningNodeOnchainTransaction
import github.aeonbtc.ibiswallet.data.model.UtxoInfo
import github.aeonbtc.ibiswallet.util.InputLimits
import github.aeonbtc.ibiswallet.util.stringWithLimit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.ConnectionPool
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.bitcoindevkit.Address
import org.bitcoindevkit.Network
import org.bitcoindevkit.Script
import org.json.JSONArray
import org.json.JSONObject
import java.io.EOFException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Core Lightning node client via built-in clnrest (POST /v1/{rpc} + `Rune` header).
 * Default UI/port is clnrest on 3010 with TLS off until the user enables it;
 * supports Tor SOCKS and optional TLS cert pin.
 */
class ClnRestClient(
    private val torSocksPort: Int = 9050,
) : LightningNodeBackend {
    private var client: OkHttpClient? = null
    private var baseUrl: String = ""
    private var rune: String = ""
    private var activeConfig: LightningNodeConfig? = null
    private var fundsCache: CachedClnFunds? = null
    private val knownOwnedOutpoints = LinkedHashMap<String, ClnOwnedOutpoint>()

    val sessionUseTls: Boolean
        get() = activeConfig?.tlsEnabled == true

    val sessionConfig: LightningNodeConfig?
        get() = activeConfig

    override suspend fun connect(config: LightningNodeConfig): LightningNodeInfo =
        withContext(Dispatchers.IO) {
            require(config.host.isNotBlank()) { "Host is required" }
            require(config.port in 1..65535) { "Invalid port" }
            require(config.clnRune.isNotBlank()) { "Rune is required" }

            rune = config.clnRune.trim()
            val host =
                config.host
                    .trim()
                    .removePrefix("https://")
                    .removePrefix("http://")
                    .substringBefore('/')
                    .substringBefore(':')
                    .trimEnd('/')
            require(host.isNotBlank()) { "Host is required" }
            val needsTor = config.useTor || host.endsWith(".onion", ignoreCase = true)
            if (host.endsWith(".onion", ignoreCase = true) && !needsTor) {
                throw IllegalStateException("Onion hosts require Tor")
            }
            val normalized = config.copy(useTor = needsTor, host = host)
            connectWithRetries(normalized)
        }

    private fun connectWithRetries(normalized: LightningNodeConfig): LightningNodeInfo {
        var lastError: Exception? = null
        val viaTor =
            normalized.useTor || normalized.host.endsWith(".onion", ignoreCase = true)
        // See LndRestClient: Tor + TLS off → HTTPS first; never burn slow HTTP circuits.
        val httpsFallback =
            if (!normalized.tlsEnabled) {
                normalized.copy(
                    useTls = true,
                    allowInsecureTls = true,
                    tlsCertPem = normalized.tlsCertPem,
                )
            } else {
                null
            }
        val candidates =
            buildList {
                when {
                    normalized.tlsEnabled -> add(normalized)
                    httpsFallback == null -> add(normalized)
                    viaTor || normalized.preferSessionTls -> {
                        add(httpsFallback)
                        if (!viaTor) add(normalized)
                    }
                    else -> {
                        add(normalized)
                        add(httpsFallback)
                    }
                }
            }
        for ((candidateIndex, candidate) in candidates.withIndex()) {
            val isCleartextProbe = !candidate.tlsEnabled && !normalized.tlsEnabled
            val maxAttempts =
                when {
                    isCleartextProbe -> 1
                    viaTor -> 1
                    else -> CONNECT_CLEAR_STREAM_ATTEMPTS
                }
            for (attempt in 0 until maxAttempts) {
                try {
                    openSession(candidate, probeTimeouts = isCleartextProbe)
                    return getInfo()
                } catch (e: Exception) {
                    lastError = e
                    val streamRetry =
                        !isCleartextProbe &&
                            isTransientStreamError(e) &&
                            attempt < maxAttempts - 1
                    if (streamRetry) {
                        closeClientQuietly()
                        continue
                    }
                    if (isDefinitiveHttpFailure(e)) throw e
                    if (candidateIndex == candidates.lastIndex) throw e
                    closeClientQuietly()
                    break
                }
            }
        }
        throw lastError ?: IllegalStateException("Connection failed")
    }

    private fun openSession(
        config: LightningNodeConfig,
        probeTimeouts: Boolean = false,
    ) {
        closeClientQuietly()
        val scheme = if (config.tlsEnabled) "https" else "http"
        baseUrl = "$scheme://${config.host}:${config.port}"
        client = buildHttpClient(config, probeTimeouts = probeTimeouts)
        activeConfig = config
        fundsCache = null
        knownOwnedOutpoints.clear()
    }

    private fun closeClientQuietly() {
        runCatching { client?.connectionPool?.evictAll() }
        client = null
        activeConfig = null
        fundsCache = null
        knownOwnedOutpoints.clear()
    }

    /** Failures that are about credentials/API, not transport scheme. */
    private fun isDefinitiveHttpFailure(error: Exception): Boolean {
        val message = error.message.orEmpty().lowercase()
        return message.contains("http 401") ||
            message.contains("http 403") ||
            message.contains("permission denied") ||
            message.contains("rune") &&
            (
                message.contains("invalid") ||
                    message.contains("unauthorized") ||
                    message.contains("denied")
            )
    }

    private fun isTransientStreamError(error: Throwable): Boolean {
        var current: Throwable? = error
        while (current != null) {
            if (current is EOFException) return true
            val message = current.message.orEmpty().lowercase()
            if (
                message.contains("unexpected end of stream") ||
                message.contains("connection reset") ||
                message.contains("software caused connection abort") ||
                message.contains("stream was reset") ||
                message.contains("connection closed") ||
                message.contains("socket closed")
            ) {
                return true
            }
            current = current.cause
        }
        return false
    }

    override suspend fun getBalance(): LightningNodeBalance =
        withContext(Dispatchers.IO) {
            val funds = listFundsCached()
            val channels = funds.optJSONArray("channels") ?: JSONArray()
            var local = 0L
            var remote = 0L
            for (i in 0 until channels.length()) {
                val ch = channels.optJSONObject(i) ?: continue
                val state = ch.optString("state").uppercase()
                if (state.isNotBlank() && state != "CHANNELD_NORMAL") continue
                local += msatToSats(ch, "our_amount_msat", "our_amount")
                val total = msatToSats(ch, "amount_msat", "amount")
                if (total > local) {
                    remote += (total - msatToSats(ch, "our_amount_msat", "our_amount"))
                }
            }
            LightningNodeBalance(
                localBalanceSats = local,
                remoteBalanceSats = remote.coerceAtLeast(0L),
            )
        }

    override suspend fun listChannels(): List<LightningNodeChannel> =
        withContext(Dispatchers.IO) {
            val peerNames =
                runCatching {
                    val peers = rpc("listpeers")
                    val arr = peers.optJSONArray("peers") ?: JSONArray()
                    buildMap {
                        for (i in 0 until arr.length()) {
                            val p = arr.optJSONObject(i) ?: continue
                            val id = p.optString("id")
                            if (id.isBlank()) continue
                            val alias =
                                p.optString("alias").ifBlank {
                                    p.optJSONObject("features")?.optString("alias").orEmpty()
                                }.ifBlank { null }
                            put(id, alias)
                        }
                    }
                }.getOrDefault(emptyMap())
            // Prefer listpeerchannels (includes pending open / closing states).
            // Fall back to listfunds.channels for older CLN.
            val channels =
                runCatching {
                    val peerChannels = rpc("listpeerchannels")
                    peerChannels.optJSONArray("channels")
                }.getOrNull()
                    ?: runCatching { listFundsCached() }.getOrElse { JSONObject() }
                        .optJSONArray("channels")
                    ?: JSONArray()
            buildList {
                for (i in 0 until channels.length()) {
                    val ch = channels.optJSONObject(i) ?: continue
                    val peerId =
                        ch.optString("peer_id").ifBlank {
                            ch.optString("peerid")
                        }.ifBlank {
                            ch.optString("id")
                        }
                    val shortId =
                        ch.optString("short_channel_id").ifBlank {
                            ch.optString("alias")
                        }.ifBlank {
                            ch.optString("channel_id")
                        }
                    val channelId =
                        shortId.ifBlank {
                            ch.optString("channel_id")
                        }.ifBlank {
                            ch.optString("funding_txid")
                        }.ifBlank {
                            peerId
                        }.ifBlank { "channel-$i" }
                    if (channelId.isBlank()) continue
                    val state = ch.optString("state").ifBlank { null }
                    val total =
                        msatToSats(ch, "total_msat", "amount_msat", "amount")
                            .takeIf { it > 0L }
                            ?: msatToSats(ch, "funding_msat", "amount_msat")
                    val our =
                        msatToSats(ch, "to_us_msat", "our_amount_msat", "our_amount")
                    val their =
                        msatToSats(ch, "to_them_msat")
                            .takeIf { it > 0L }
                            ?: (total - our).coerceAtLeast(0L)
                    val active = state.equals("CHANNELD_NORMAL", ignoreCase = true)
                    val fundingTxid =
                        ch.optString("funding_txid").ifBlank {
                            ch.optJSONObject("funding")?.optString("txid").orEmpty()
                        }.ifBlank {
                            ch.optString("channel_id")
                                .takeIf { it.length == 64 }
                                .orEmpty()
                        }.ifBlank { null }
                    add(
                        LightningNodeChannel(
                            id = channelId,
                            remotePubkey = peerId,
                            remoteAlias = peerNames[peerId],
                            shortChannelId = shortId.takeIf { it.isNotBlank() && it != channelId },
                            fundingTxid = fundingTxid,
                            capacitySats = total,
                            localBalanceSats = our,
                            remoteBalanceSats = their,
                            isActive = active,
                            isPrivate = ch.optBoolean("private", false),
                            state = state,
                        ),
                    )
                }
            }.sortedWith(
                compareByDescending<LightningNodeChannel> {
                    !it.isActive && (
                        it.state?.contains("AWAIT", ignoreCase = true) == true ||
                            it.state?.contains("LOCKIN", ignoreCase = true) == true ||
                            it.state?.contains("OPEN", ignoreCase = true) == true ||
                            it.state?.contains("DUALOPEND", ignoreCase = true) == true
                        )
                }.thenByDescending { it.isActive }
                    .thenByDescending { it.capacitySats },
            )
        }

    override suspend fun listPayments(limit: Int): List<LightningNodePayment> =
        withContext(Dispatchers.IO) {
            val capped = limit.coerceIn(1, 100)
            val payments = mutableListOf<LightningNodePayment>()
            val (pays, invoices) =
                coroutineScope {
                    val paysDeferred = async { runCatching { rpc("listpays") }.getOrElse { JSONObject() } }
                    val invDeferred =
                        async { runCatching { rpc("listinvoices") }.getOrElse { JSONObject() } }
                    paysDeferred.await() to invDeferred.await()
                }
            val payArr = pays.optJSONArray("pays") ?: JSONArray()
            for (i in 0 until payArr.length()) {
                val p = payArr.optJSONObject(i) ?: continue
                val status = mapPayStatus(p.optString("status"))
                if (status == LightningNodePaymentStatus.UNKNOWN) continue
                val amount =
                    msatToSats(p, "amount_msat", "amount_sent_msat")
                        .takeIf { it > 0 }
                        ?: msatToSats(p, "amount_sent_msat")
                val fee =
                    (
                        msatToSats(p, "amount_sent_msat") -
                            msatToSats(p, "amount_msat")
                    ).coerceAtLeast(0L)
                val created = parseClnTimestamp(p.optString("created_at", p.optString("completed_at")))
                val hash = p.optString("payment_hash").ifBlank { null }
                payments +=
                    LightningNodePayment(
                        id = hash ?: p.optString("bolt11").ifBlank { "pay-$i" },
                        direction = LightningNodePaymentDirection.OUTGOING,
                        status = status,
                        amountSats = amount,
                        feeSats = fee,
                        timestamp = created,
                        paymentHash = hash,
                        paymentRequest =
                            p.optString("bolt11").ifBlank {
                                p.optString("bolt12").ifBlank {
                                    p.optString("invstring").ifBlank { null }
                                }
                            },
                        // Never fall back to internal labels (e.g. ibis-uuid).
                        memo = p.optString("description").ifBlank { null },
                        destination = p.optString("destination").ifBlank { null },
                        destinationAlias =
                            p.optString("destination_alias").ifBlank {
                                p.optString("alias")
                                    .takeIf { alias ->
                                        alias.isNotBlank() &&
                                            !alias.startsWith("ibis-", ignoreCase = true)
                                    }
                            },
                        failureReason =
                            if (status == LightningNodePaymentStatus.FAILED) {
                                extractClnPaymentFailureReason(p)
                            } else {
                                null
                            },
                    )
            }

            val invArr = invoices.optJSONArray("invoices") ?: JSONArray()
            for (i in 0 until invArr.length()) {
                val inv = invArr.optJSONObject(i) ?: continue
                val status = inv.optString("status").lowercase()
                if (status != "paid") continue
                val amount = msatToSats(inv, "amount_received_msat", "amount_msat", "msatoshi_received")
                val paidAt = parseClnTimestamp(inv.optString("paid_at", inv.optString("expires_at")))
                val hash = inv.optString("payment_hash").ifBlank { null }
                payments +=
                    LightningNodePayment(
                        id = hash ?: inv.optString("label").ifBlank { "inv-$i" },
                        direction = LightningNodePaymentDirection.INCOMING,
                        status = LightningNodePaymentStatus.SUCCEEDED,
                        amountSats = amount,
                        feeSats = 0,
                        timestamp = paidAt,
                        paymentHash = hash,
                        paymentRequest =
                            inv.optString("bolt11").ifBlank {
                                inv.optString("bolt12").ifBlank { null }
                            },
                        // description only — do not use invoice label (ibis-…) as memo.
                        memo = inv.optString("description").ifBlank { null },
                        destination = null,
                    )
            }

            payments
                .sortedByDescending { it.timestamp }
                .take(capped)
        }

    override suspend fun getOnchainBalance(): Long =
        getOnchainBalanceDetails().spendableSats

    override suspend fun getOnchainBalanceDetails(): LightningNodeOnchainBalanceDetails =
        withContext(Dispatchers.IO) {
            val funds = listFundsCached()
            val outputs = funds.optJSONArray("outputs") ?: JSONArray()
            var spendable = 0L
            var reserved = 0L
            var immature = 0L
            for (i in 0 until outputs.length()) {
                val out = outputs.optJSONObject(i) ?: continue
                val status = out.optString("status").lowercase()
                if (status == "spent" || status.contains("offer")) continue
                val amount = amountToSats(out, "amount_msat", "value").coerceAtLeast(0L)
                when {
                    status == "immature" -> immature += amount
                    isClnOutputReserved(out, status) -> reserved += amount
                    isClnOutputSpendable(out) -> spendable += amount
                }
            }
            LightningNodeOnchainBalanceDetails(
                spendableSats = spendable,
                reservedSats = reserved,
                immatureSats = immature,
            )
        }

    override suspend fun newOnchainAddress(): String =
        withContext(Dispatchers.IO) {
            val response =
                runCatching { rpc("newaddr", JSONObject().put("addresstype", "bech32")) }
                    .recoverCatching { rpc("newaddr") }
                    .getOrThrow()
            response.optString("bech32").ifBlank {
                response.optString("p2tr").ifBlank {
                    response.optString("p2sh-segwit").ifBlank {
                        response.optString("address")
                    }
                }
            }.takeIf { it.isNotBlank() }
                ?: throw IllegalStateException("Node did not return an address")
        }

    override suspend fun listOnchainTransactions(limit: Int): List<LightningNodeOnchainTransaction> =
        withContext(Dispatchers.IO) {
            val capped = limit.coerceIn(1, 200)
            val nowMs = System.currentTimeMillis()
            // Parallelize the RPC fan-out. Sequential listtransactions + getinfo +
            // listfunds(spent) + listpeerchannels + listclosedchannels was multi-minute on Tor.
            val (response, tipHeight, ownedOutpoints, channelFundingTxids, channelClosingTxids) =
                coroutineScope {
                    val txsDeferred =
                        async { runCatching { rpc("listtransactions") }.getOrElse { JSONObject() } }
                    val tipDeferred =
                        async {
                            runCatching { rpc("getinfo").optInt("blockheight", 0) }
                                .getOrDefault(0)
                                .coerceAtLeast(0)
                        }
                    val ownedDeferred = async { loadClnWalletOutpoints() }
                    val fundingDeferred =
                        async {
                            runCatching {
                                listChannels()
                                    .mapNotNull {
                                        it.fundingTxid?.lowercase()?.takeIf(String::isNotBlank)
                                    }
                                    .toSet()
                            }.getOrDefault(emptySet())
                        }
                    val closingDeferred = async { loadClnChannelClosingTxids() }
                    Quintuple(
                        txsDeferred.await(),
                        tipDeferred.await(),
                        ownedDeferred.await(),
                        fundingDeferred.await(),
                        closingDeferred.await(),
                    )
                }
            val txs = response.optJSONArray("transactions") ?: JSONArray()
            buildList {
                for (i in 0 until txs.length()) {
                    val tx = txs.optJSONObject(i) ?: continue
                    val txid = tx.optString("hash").ifBlank { tx.optString("txid") }
                    if (txid.isBlank()) continue

                    var receivedSats = 0L
                    var receivingAddress: String? = null
                    var changeAddress: String? = null
                    var changeSats = 0L
                    var recipientAddress: String? = null
                    var recipientSats = 0L
                    var allOutputSats = 0L
                    val outputs = tx.optJSONArray("outputs") ?: JSONArray()
                    for (j in 0 until outputs.length()) {
                        val o = outputs.optJSONObject(j) ?: continue
                        val outputSats = amountToSats(o, "amount_msat", "msat", "satoshi")
                        allOutputSats += outputSats
                        val vout =
                            when {
                                o.has("index") -> o.optInt("index", -1)
                                o.has("output") -> o.optInt("output", -1)
                                else -> j
                            }
                        if (vout < 0) continue
                        val key = outpointKey(txid, vout)
                        val owned = ownedOutpoints[key]
                        if (owned != null) {
                            val outAmt = outputSats.takeIf { it > 0L } ?: owned.amountSats
                            receivedSats += outAmt
                            if (receivingAddress.isNullOrBlank()) {
                                receivingAddress = owned.address ?: o.optString("address").ifBlank { null }
                            }
                            if (changeAddress.isNullOrBlank()) {
                                changeAddress = owned.address ?: o.optString("address").ifBlank { null }
                                changeSats = outAmt
                            }
                        } else if (recipientAddress.isNullOrBlank()) {
                            outputAddress(o)?.let { address ->
                                recipientAddress = address
                                recipientSats = outputSats
                            }
                        }
                    }

                    var spentSats = 0L
                    val inputs = tx.optJSONArray("inputs") ?: JSONArray()
                    for (j in 0 until inputs.length()) {
                        val inp = inputs.optJSONObject(j) ?: continue
                        val prevTxid =
                            inp.optString("txid").ifBlank {
                                inp.optString("tx_hash")
                            }
                        if (prevTxid.isBlank()) continue
                        val prevIndex =
                            when {
                                inp.has("index") -> inp.optInt("index", -1)
                                inp.has("vout") -> inp.optInt("vout", -1)
                                inp.has("output") -> inp.optInt("output", -1)
                                else -> -1
                            }
                        if (prevIndex < 0) continue
                        val owned = ownedOutpoints[outpointKey(prevTxid, prevIndex)] ?: continue
                        spentSats += owned.amountSats
                    }

                    // Net wallet delta: credits − draws. Zero-net txs (e.g. pure watches) skipped.
                    val amountSats = receivedSats - spentSats
                    if (amountSats == 0L && receivedSats == 0L && spentSats == 0L) {
                        // No ownership signal — skip rather than mis-label counterparty amounts.
                        continue
                    }

                    val blockHeight = tx.optInt("blockheight", 0).coerceAtLeast(0)
                    val conf =
                        when {
                            blockHeight <= 0 -> 0
                            tipHeight > 0 && tipHeight >= blockHeight ->
                                (tipHeight - blockHeight + 1).coerceAtLeast(1)
                            else -> 1
                        }
                    val explicitTime =
                        sequenceOf(
                            tx.opt("created_at"),
                            tx.opt("timestamp"),
                            tx.opt("blocktime"),
                            tx.opt("time"),
                        ).mapNotNull { raw ->
                            val parsed =
                                when (raw) {
                                    null, JSONObject.NULL -> 0L
                                    is Number -> parseClnTimestamp(raw.toString())
                                    is String -> parseClnTimestamp(raw)
                                    else -> 0L
                                }
                            parsed.takeIf { it > 0L }
                        }.firstOrNull()
                    // listtransactions often lacks wall time; ~10min/block from tip for historical fiat.
                    val estimatedTimeMs =
                        if (
                            explicitTime == null &&
                            blockHeight > 0 &&
                            tipHeight > 0 &&
                            tipHeight >= blockHeight
                        ) {
                            val blocksAgo = (tipHeight - blockHeight).toLong().coerceAtLeast(0L)
                            (nowMs - blocksAgo * 600_000L).coerceAtLeast(0L)
                        } else {
                            0L
                        }
                    // Fee estimate when we spent more than we kept on-chain (send/channel fund).
                    val feeSats =
                        amountToSats(tx, "fee_msat", "fee")
                            .coerceAtLeast(0L)
                            .takeIf { it > 0L }
                            ?: if (spentSats > 0L) {
                                (spentSats - allOutputSats).coerceAtLeast(0L)
                            } else {
                                0L
                            }
                    val isReceived = amountSats >= 0L
                    val address = if (isReceived) receivingAddress else recipientAddress
                    val addressAmount =
                        if (isReceived) receivedSats.takeIf { it > 0L } else recipientSats.takeIf { it > 0L }
                    val outgoingChangeAddress = changeAddress?.takeIf { !isReceived && it.isNotBlank() }
                    val outgoingChangeSats = changeSats.takeIf { outgoingChangeAddress != null && it > 0L }
                    val timestamp =
                        explicitTime
                            ?: estimatedTimeMs.takeIf { it > 0L }
                            ?: if (blockHeight <= 0) nowMs else 0L
                    add(
                        LightningNodeOnchainTransaction(
                            txid = txid,
                            amountSats = amountSats,
                            feeSats = feeSats,
                            vsize = transactionVsize(tx.optString("rawtx")),
                            timestamp = timestamp,
                            confirmations = conf,
                            address = address,
                            addressAmountSats = addressAmount,
                            changeAddress = outgoingChangeAddress,
                            changeAmountSats = outgoingChangeSats,
                            isChannelOpen =
                                amountSats < 0L && txid.lowercase() in channelFundingTxids,
                            isChannelClose =
                                amountSats >= 0L && txid.lowercase() in channelClosingTxids,
                        ),
                    )
                }
            }
                .sortedWith(
                    compareByDescending<LightningNodeOnchainTransaction> { it.confirmations <= 0 }
                        .thenByDescending { it.timestamp }
                        .thenByDescending { it.txid },
                )
                .take(capped)
        }

    private data class Quintuple<A, B, C, D, E>(
        val first: A,
        val second: B,
        val third: C,
        val fourth: D,
        val fifth: E,
    )

    private fun loadClnChannelClosingTxids(): Set<String> {
        val closing = linkedSetOf<String>()
        fun addTxid(raw: String?) {
            raw
                ?.trim()
                ?.substringBefore(':')
                ?.lowercase()
                ?.takeIf { it.isNotBlank() }
                ?.let { closing += it }
        }
        runCatching {
            val peerChannels =
                runCatching { rpc("listpeerchannels") }
                    .getOrElse { JSONObject() }
                    .optJSONArray("channels")
                    ?: JSONArray()
            for (i in 0 until peerChannels.length()) {
                val ch = peerChannels.optJSONObject(i) ?: continue
                val state = ch.optString("state").uppercase()
                if (
                    state.contains("CLOSE") ||
                    state.contains("SHUTDOWN") ||
                    state.contains("CLOSINGD") ||
                    state.contains("ONCHAIN")
                ) {
                    addTxid(ch.optString("scratch_txid"))
                    addTxid(ch.optString("last_tx"))
                    addTxid(ch.optString("close_txid"))
                    addTxid(ch.optJSONObject("close")?.optString("txid"))
                    addTxid(ch.optJSONObject("funding")?.optString("txid"))
                    // Some CLN versions expose close outpoint via status arrays.
                    val status = ch.optJSONArray("status")
                    if (status != null) {
                        for (j in 0 until status.length()) {
                            val line = status.optString(j)
                            Regex("""\b([0-9a-fA-F]{64})\b""").findAll(line).forEach { match ->
                                addTxid(match.groupValues[1])
                            }
                        }
                    }
                }
            }
        }
        runCatching {
            val closed =
                runCatching { rpc("listclosedchannels") }
                    .getOrElse { JSONObject() }
                    .optJSONArray("closedchannels")
                    ?: JSONObject().optJSONArray("channels")
                    ?: JSONArray()
            for (i in 0 until closed.length()) {
                val ch = closed.optJSONObject(i) ?: continue
                addTxid(ch.optString("last_tx"))
                addTxid(ch.optString("close_txid"))
                addTxid(ch.optString("closing_txid"))
            }
        }
        return closing
    }

    /**
     * Outpoints ever held by the wallet (confirmed / unconfirmed / spent) from listfunds.
     * CLN excludes spent outputs by default, so request them explicitly to attribute both
     * receives that were later spent and the inputs of outgoing transactions.
     */
    private fun loadClnWalletOutpoints(): Map<String, ClnOwnedOutpoint> {
        val fullSpentFunds =
            runCatching { rpc("listfunds", JSONObject().put("spent", true)) }.getOrNull()
        val funds = fullSpentFunds ?: runCatching { listFundsCached() }.getOrNull()
        if (funds == null) {
            return LinkedHashMap(knownOwnedOutpoints)
        }
        val outputs = funds.optJSONArray("outputs") ?: JSONArray()
        val map = LinkedHashMap<String, ClnOwnedOutpoint>()
        for (i in 0 until outputs.length()) {
            val out = outputs.optJSONObject(i) ?: continue
            val txid = out.optString("txid").ifBlank { continue }
            val vout = out.optInt("output", out.optInt("vout", -1))
            if (vout < 0) continue
            val amount = amountToSats(out, "amount_msat", "value").coerceAtLeast(0L)
            if (amount <= 0L) continue
            map[outpointKey(txid, vout)] =
                ClnOwnedOutpoint(
                    amountSats = amount,
                    address = out.optString("address").ifBlank { null },
                )
        }
        if (fullSpentFunds != null) {
            // Authoritative full ownership map: replace sticky memory so spent outs
            // don't create permanent double-count risk after confirmations settle.
            knownOwnedOutpoints.clear()
            knownOwnedOutpoints.putAll(map)
            return map
        }
        knownOwnedOutpoints.putAll(map)
        return LinkedHashMap(knownOwnedOutpoints)
    }

    private fun listFundsCached(): JSONObject {
        val now = System.currentTimeMillis()
        fundsCache
            ?.takeIf { now - it.createdAtMs <= FUNDS_CACHE_TTL_MS }
            ?.let { return it.funds }
        val funds = rpc("listfunds")
        fundsCache = CachedClnFunds(funds = funds, createdAtMs = now)
        return funds
    }

    /** Available for L1 spend: not spent/immature, and not reserved by an in-flight withdraw. */
    private fun isClnOutputSpendable(out: JSONObject): Boolean {
        val status = out.optString("status").lowercase()
        if (
            status == "spent" ||
            status == "immature" ||
            status.contains("reserv") ||
            status.contains("offer")
        ) {
            return false
        }
        // CLN keeps reserved=true on the source UTXO until the pending spend confirms.
        if (out.optBoolean("reserved", false)) return false
        return true
    }

    private fun isClnOutputReserved(
        out: JSONObject,
        status: String,
    ): Boolean =
        status.contains("reserv") || out.optBoolean("reserved", false)

    private fun outpointKey(
        txid: String,
        vout: Int,
    ): String = "${txid.lowercase()}:$vout"

    private fun outputAddress(output: JSONObject): String? {
        output.optString("address").ifBlank { null }?.let { return it }
        val scriptHex = output.optString("scriptPubKey").ifBlank { output.optString("scriptpubkey") }
        if (scriptHex.length % 2 != 0) return null
        val scriptBytes = ByteArray(scriptHex.length / 2)
        for (index in scriptBytes.indices) {
            scriptBytes[index] =
                scriptHex.substring(index * 2, index * 2 + 2).toIntOrNull(16)?.toByte() ?: return null
        }
        return runCatching { Address.fromScript(Script(scriptBytes), Network.BITCOIN).toString() }.getOrNull()
    }

    private data class CachedClnFunds(
        val funds: JSONObject,
        val createdAtMs: Long,
    )

    private data class ClnOwnedOutpoint(
        val amountSats: Long,
        val address: String?,
    )

    override suspend fun listOnchainUtxos(): List<UtxoInfo> =
        withContext(Dispatchers.IO) {
            val funds = listFundsCached()
            val outputs = funds.optJSONArray("outputs") ?: JSONArray()
            buildList {
                for (i in 0 until outputs.length()) {
                    val out = outputs.optJSONObject(i) ?: continue
                    val status = out.optString("status").lowercase()
                    if (status == "spent") continue
                    // Still show reserved outputs (pending spends) frozen so coin control
                    // cannot re-select them, but they are not spendable balance.
                    val reserved = out.optBoolean("reserved", false)
                    val txid = out.optString("txid").ifBlank { continue }
                    val vout = out.optInt("output", out.optInt("vout", 0)).coerceAtLeast(0).toUInt()
                    val address = out.optString("address")
                    if (address.isBlank()) continue
                    val amount = amountToSats(out, "amount_msat", "value").coerceAtLeast(0L).toULong()
                    val confirmed = status == "confirmed" || out.optInt("blockheight", 0) > 0
                    add(
                        UtxoInfo(
                            outpoint = "$txid:$vout",
                            txid = txid,
                            vout = vout,
                            address = address,
                            amountSats = amount,
                            isConfirmed = confirmed,
                            isFrozen = status == "immature" || reserved,
                        ),
                    )
                }
            }
        }

    override suspend fun sendOnchain(
        address: String,
        amountSats: Long?,
        satPerVbyte: Double?,
        sendAll: Boolean,
        label: String?,
        spendUnconfirmed: Boolean,
        selectedOutpoints: List<UtxoInfo>?,
    ): String =
        withContext(Dispatchers.IO) {
            require(address.isNotBlank()) { "Address is required" }
            if (sendAll) {
                require(amountSats == null || amountSats <= 0) {
                    "Amount must be omitted for send_all"
                }
            } else {
                require(amountSats != null && amountSats > 0) { "Amount must be positive" }
            }
            if (!selectedOutpoints.isNullOrEmpty()) {
                throw IllegalStateException("CLN withdraw does not support coin control outpoints")
            }
            val body =
                JSONObject().apply {
                    put("destination", address.trim())
                    if (sendAll) {
                        put("satoshi", "all")
                    } else {
                        put("satoshi", amountSats!!.toString())
                    }
                    putClnFeerate(this, satPerVbyte)
                }
            val response = rpc("withdraw", body)
            fundsCache = null
            response.optString("txid").takeIf { it.isNotBlank() }
                ?: throw IllegalStateException("Node did not return a transaction ID")
        }

    override suspend fun sendOnchainMany(
        addrToAmountSats: Map<String, Long>,
        satPerVbyte: Double?,
        label: String?,
        spendUnconfirmed: Boolean,
        selectedOutpoints: List<UtxoInfo>?,
    ): String =
        withContext(Dispatchers.IO) {
            require(addrToAmountSats.isNotEmpty()) { "At least one recipient is required" }
            require(addrToAmountSats.all { it.key.isNotBlank() && it.value > 0 }) {
                "Each recipient needs a valid address and positive amount"
            }
            if (!selectedOutpoints.isNullOrEmpty()) {
                throw IllegalStateException("CLN multiwithdraw does not support coin control outpoints")
            }
            val outputs = JSONObject()
            addrToAmountSats.forEach { (addr, amount) ->
                outputs.put(addr.trim(), amount.toString())
            }
            val body =
                JSONObject().apply {
                    put("outputs", outputs)
                    putClnFeerate(this, satPerVbyte)
                }
            val response =
                runCatching { rpc("multiwithdraw", body) }
                    .getOrElse { e ->
                        throw IllegalStateException(
                            e.message?.takeIf { it.isNotBlank() }
                                ?: "CLN multiwithdraw failed",
                        )
                    }
            fundsCache = null
            // multiwithdraw returns tx or txid depending on version
            response.optString("txid").ifBlank {
                response.optJSONArray("txids")?.optString(0).orEmpty()
            }.takeIf { it.isNotBlank() }
                ?: throw IllegalStateException("Node did not return a transaction ID")
        }

    override suspend fun bumpOnchainFee(
        outpoint: UtxoInfo,
        satPerVbyte: Double?,
        immediate: Boolean,
        budgetSats: Long?,
    ): String =
        withContext(Dispatchers.IO) {
            throw IllegalStateException("Fee bump is not supported for CLN")
        }

    override suspend fun getOnchainMinRelayFeeSatPerVb(): Double? =
        withContext(Dispatchers.IO) {
            // feerates perkw.floor = backend minrelayfee/mempoolminfee (sat per 1000 weight).
            val json =
                runCatching {
                    rpc("feerates", JSONObject().put("style", "perkw"))
                }.getOrNull()
                    ?: return@withContext null
            val perkw = json.optJSONObject("perkw") ?: return@withContext null
            val satPerKw =
                sequenceOf("floor", "min_acceptable")
                    .mapNotNull { key ->
                        perkw.optString(key).toLongOrNull()
                            ?: perkw.optLong(key, 0L).takeIf { it > 0L }
                    }
                    .firstOrNull { it > 0L }
                    ?: return@withContext null
            val satPerVb = satPerKw.toDouble() / 250.0
            satPerVb.takeIf { it in 0.01..100.0 }
        }

    /**
     * CLN `feerate`: `Nperkb` where N = sat/kB ≈ sat/vB * 1000.
     * Fractional sat/vB becomes a non-integer perkb only after rounding — use
     * milli-kw (`Nperkw`) when available so 0.5 sat/vB → 125perkw.
     */
    private fun putClnFeerate(
        body: JSONObject,
        satPerVbyte: Double?,
    ) {
        if (satPerVbyte == null || satPerVbyte <= 0.0) return
        val satPerKw =
            github.aeonbtc.ibiswallet.util.BitcoinUtils
                .feeRateToSatPerKwu(satPerVbyte)
                .toLong()
                .coerceAtLeast(1L)
        // Prefer perkw (sat/kWU) — exact match for our kwu conversion (1 sat/vB = 250).
        body.put("feerate", "${satPerKw}perkw")
    }

    override suspend fun createInvoice(
        amountSats: Long?,
        description: String?,
    ): LightningNodeInvoice =
        withContext(Dispatchers.IO) {
            val label = "ibis-${UUID.randomUUID()}"
            val body =
                JSONObject().apply {
                    if (amountSats != null && amountSats > 0) {
                        put("amount_msat", amountSats * 1000L)
                    } else {
                        put("amount_msat", "any")
                    }
                    put("label", label)
                    // CLN requires a description field; only put user memo when explicitly set.
                    // Never invent a default ("Ibis Wallet") — empty string = no memo to payee.
                    val memo = description?.trim()?.takeIf { it.isNotBlank() }
                    put("description", memo.orEmpty())
                    put("expiry", 3600)
                }
            val response = rpc("invoice", body)
            val bolt11 = response.optString("bolt11")
            require(bolt11.isNotBlank()) { "Node did not return a BOLT11 invoice" }
            LightningNodeInvoice(
                paymentRequest = bolt11,
                paymentHash = response.optString("payment_hash").ifBlank { null },
                amountSats = amountSats,
                // App memo is only the user-supplied text — not CLN label (ibis-…).
                description = description?.trim()?.takeIf { it.isNotBlank() },
                expirySeconds = 3600,
            )
        }

    override suspend fun decodeInvoice(bolt11: String): DecodedLightningNodeInvoice =
        withContext(Dispatchers.IO) {
            val request = bolt11.trim().removePrefix("lightning:").trim()
            val response =
                runCatching { rpc("decode", JSONObject().put("string", request)) }
                    .recoverCatching { rpc("decodepay", JSONObject().put("bolt11", request)) }
                    .getOrThrow()
            val amount =
                msatToSats(response, "amount_msat", "msatoshi").takeIf { it > 0 }
            DecodedLightningNodeInvoice(
                paymentRequest = request,
                paymentHash = response.optString("payment_hash").ifBlank { null },
                amountSats = amount,
                description = response.optString("description").ifBlank { null },
                destination = response.optString("payee").ifBlank {
                    response.optString("destination").ifBlank { null }
                },
                expirySeconds = response.optLong("expiry", 0L).takeIf { it > 0 },
            )
        }

    override suspend fun payInvoice(
        bolt11: String,
        amountSats: Long?,
        maxFeePercent: Double?,
    ): LightningNodePaymentResult =
        withContext(Dispatchers.IO) {
            val request =
                bolt11
                    .trim()
                    .removePrefix("lightning:")
                    .removePrefix("LIGHTNING:")
                    .trim()
            // The repository passes amount only for zero-amount invoices/offers.
            val payAmountSats = amountSats?.takeIf { it > 0L }
            // Fee budget for fixed invoices: resolve amount when the caller omitted it.
            val routingAmountSats =
                payAmountSats
                    ?: runCatching { decodeInvoice(request).amountSats }.getOrNull()
                    ?: 0L
            val maxFeeMsat = paymentFeeLimitMsat(routingAmountSats, maxFeePercent).toString()
            val isBolt12 =
                request.startsWith("lno", ignoreCase = true) ||
                    request.startsWith("lni", ignoreCase = true)
            // BOLT12 offers/invoices: xpay fetches offer invoices and pays. pay() is bolt11-only.
            if (isBolt12) {
                val xpayBody =
                    JSONObject().apply {
                        put("invstring", request)
                        if (payAmountSats != null) {
                            put("amount_msat", payAmountSats * 1000L)
                        }
                        put("maxfee", maxFeeMsat)
                        put("retry_for", 60)
                    }
                val response = rpc("xpay", xpayBody)
                return@withContext parsePaymentResponse(response)
            }
            // BOLT11: pay uses bolt11; xpay uses invstring (rejects bolt11).
            val payBody =
                JSONObject().apply {
                    put("bolt11", request)
                    if (payAmountSats != null) {
                        put("amount_msat", payAmountSats * 1000L)
                    }
                    put("maxfee", maxFeeMsat)
                    put("retry_for", 60)
                }
            val xpayBody =
                JSONObject().apply {
                    put("invstring", request)
                    if (payAmountSats != null) {
                        put("amount_msat", payAmountSats * 1000L)
                    }
                    put("maxfee", maxFeeMsat)
                    put("retry_for", 60)
                }
            val response =
                runCatching { rpc("pay", payBody) }
                    .recoverCatching { payError ->
                        // Only fall back when pay is unavailable / wrong shape — not on
                        // real routing failures (those should surface once, cleanly).
                        if (!isMissingRpcMethodError(payError, "pay")) {
                            throw payError
                        }
                        rpc("xpay", xpayBody)
                    }
                    .getOrThrow()
            parsePaymentResponse(response)
        }

    private fun parsePaymentResponse(response: JSONObject): LightningNodePaymentResult {
        val status = response.optString("status").lowercase()
        // Explicit failure states (and anything other than complete/paid/blank on plugins without status).
        when (status) {
            "", "complete", "paid", "succeeded", "success" -> Unit
            "pending", "inflight", "in_flight" ->
                throw IllegalStateException("Payment still in progress")
            else -> {
                val err = response.optString("error").ifBlank { response.optString("message") }
                throw IllegalStateException(err.ifBlank { "Payment failed: $status" })
            }
        }
        // Proof of payment required — without preimage a failed/partial pay can look like success.
        val preimage =
            response.optString("payment_preimage").ifBlank {
                response.optString("preimage")
            }.ifBlank { null }
                ?: throw IllegalStateException("Payment did not return a preimage")
        val hash = response.optString("payment_hash").ifBlank { null }
        val amountSent = msatToSats(response, "amount_sent_msat")
        val amount = msatToSats(response, "amount_msat")
        val fee = (amountSent - amount).coerceAtLeast(0L)
        return LightningNodePaymentResult(
            paymentId = hash,
            paymentHash = hash,
            feeSats = fee,
            preimage = preimage,
        )
    }

    private fun paymentFeeLimitMsat(
        amountSats: Long,
        maxFeePercent: Double?,
    ): Long {
        val percent = (maxFeePercent ?: 5.0).coerceIn(0.0, 100.0)
        return kotlin.math.ceil(amountSats.coerceAtLeast(1L) * percent / 100.0 * 1000.0)
            .toLong()
            .coerceIn(0L, 100_000_000L)
    }

    override suspend fun fetchBolt12Invoice(
        offer: String,
        amountSats: Long?,
    ): DecodedLightningNodeInvoice =
        withContext(Dispatchers.IO) {
            val request =
                offer
                    .trim()
                    .removePrefix("lightning:")
                    .removePrefix("LIGHTNING:")
                    .trim()
            require(request.isNotBlank()) { "BOLT12 offer is required" }
            // Prefer leave-as-offer: prepare only needs decode. Pay uses xpay(invstring=offer).
            // fetchinvoice is optional and hits the remote over onion messages.
            val decodedOffer =
                runCatching { decodeInvoice(request) }
                    .getOrElse {
                        DecodedLightningNodeInvoice(paymentRequest = request)
                    }
            val offerAmount = decodedOffer.amountSats?.takeIf { it > 0L }
            // amountSats on decode = offer-fixed only; amountless keeps null so pay still
            // passes the user amount. Repository combines with the user field for preview.
            if (offerAmount == null && (amountSats == null || amountSats <= 0L)) {
                throw IllegalArgumentException(
                    "This BOLT12 offer requires an amount. Enter the amount, then review again.",
                )
            }
            decodedOffer.copy(
                paymentRequest = request,
                amountSats = offerAmount,
            )
        }

    /**
     * Optional explicit invoice fetch for offers. Prefer [payInvoice] with the raw offer —
     * modern CLN xpay fetches gracefully. amount_msat only when the offer itself is amountless.
     */
    suspend fun fetchBolt12InvoiceRemote(
        offer: String,
        amountSats: Long?,
    ): DecodedLightningNodeInvoice =
        withContext(Dispatchers.IO) {
            val request =
                offer
                    .trim()
                    .removePrefix("lightning:")
                    .removePrefix("LIGHTNING:")
                    .trim()
            require(request.isNotBlank()) { "BOLT12 offer is required" }
            val offerAmount =
                runCatching { decodeInvoice(request).amountSats }.getOrNull()?.takeIf { it > 0L }
            val finalAmount = offerAmount ?: amountSats?.takeIf { it > 0L }
            if (finalAmount == null || finalAmount <= 0L) {
                throw IllegalArgumentException(
                    "This BOLT12 offer requires an amount. Enter the amount, then review again.",
                )
            }
            val body =
                JSONObject().apply {
                    put("offer", request)
                    // amount_msat only for amountless offers — remotes often fail when
                    // amount is re-supplied on a fixed-amount offer.
                    if (offerAmount == null) {
                        put("amount_msat", finalAmount * 1000L)
                    }
                    put("timeout", 90)
                }
            val response =
                runCatching { rpc("fetchinvoice", body) }
                    .getOrElse { e ->
                        throw IllegalStateException(mapBolt12FetchError(e.message), e)
                    }
            val bolt12Invoice =
                response.optString("invoice").ifBlank {
                    response.optString("bolt12").ifBlank { null }
                } ?: throw IllegalStateException("Node did not return a BOLT12 invoice")
            runCatching { decodeInvoice(bolt12Invoice) }
                .getOrElse {
                    DecodedLightningNodeInvoice(
                        paymentRequest = bolt12Invoice,
                        paymentHash = response.optString("payment_hash").ifBlank { null },
                        amountSats = finalAmount,
                        description = response.optString("description").ifBlank { null },
                        destination = null,
                        expirySeconds = null,
                    )
                }.let { decoded ->
                    decoded.copy(amountSats = decoded.amountSats ?: finalAmount)
                }
        }

    override suspend fun disconnect() {
        closeClientQuietly()
        baseUrl = ""
        rune = ""
    }

    override suspend fun ping(): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                rpc("getinfo")
                true
            }.getOrDefault(false)
        }

    private fun getInfo(): LightningNodeInfo {
        val json = rpc("getinfo")
        val network =
            json.optString("network").ifBlank {
                when {
                    json.optBoolean("testnet") -> "testnet"
                    else -> "mainnet"
                }
            }
        return LightningNodeInfo(
            alias = json.optString("alias").ifBlank { null },
            pubkey = json.optString("id").ifBlank { null },
            version = json.optString("version").ifBlank { null },
            network = network,
            numActiveChannels = json.optInt("num_active_channels", 0),
            syncedToChain =
                when {
                    json.has("warning_bitcoind_sync") -> false
                    json.has("warning_lightningd_sync") -> false
                    else -> true
                },
        )
    }

    private fun rpc(
        method: String,
        params: JSONObject = JSONObject(),
    ): JSONObject {
        var lastError: Exception? = null
        repeat(STREAM_RETRY_ATTEMPTS) { attempt ->
            try {
                val http = client ?: throw IllegalStateException("Not connected")
                val request =
                    Request.Builder()
                        .url("$baseUrl/v1/$method")
                        .header("Rune", rune)
                        .header("Content-Type", "application/json")
                        .post(params.toString().toRequestBody(JSON_MEDIA))
                        .build()
                http.newCall(request).execute().use { response ->
                    val responseBody = response.body?.stringWithLimit(InputLimits.LARGE_JSON_BYTES).orEmpty()
                    if (!response.isSuccessful) {
                        throw IllegalStateException(
                            extractError(responseBody, response.code, "/v1/$method"),
                        )
                    }
                    return parseRpcBody(responseBody)
                }
            } catch (e: Exception) {
                lastError = e
                if (!isTransientStreamError(e) || attempt == STREAM_RETRY_ATTEMPTS - 1) {
                    throw e
                }
                val cfg = activeConfig
                if (cfg != null) {
                    openSession(cfg)
                }
            }
        }
        throw lastError ?: IllegalStateException("Request failed: /v1/$method")
    }

    private fun parseRpcBody(body: String): JSONObject {
        val json = JSONObject(body.ifBlank { "{}" })
        // Some gateways wrap as { result: {...}, error: ... }
        if (json.has("error") && !json.isNull("error")) {
            val err = json.opt("error")
            val message =
                when (err) {
                    is JSONObject ->
                        err.optString("message").ifBlank { err.optString("code") }
                    else -> err?.toString().orEmpty()
                }
            throw IllegalStateException(message.ifBlank { "RPC error" })
        }
        return if (json.has("result") && json.opt("result") is JSONObject) {
            json.getJSONObject("result")
        } else {
            json
        }
    }

    private fun buildHttpClient(
        config: LightningNodeConfig,
        probeTimeouts: Boolean = false,
    ): OkHttpClient {
        val viaTor = config.useTor || config.host.endsWith(".onion", ignoreCase = true)
        val timeoutSeconds =
            when {
                probeTimeouts && viaTor -> CLEARTEXT_PROBE_TOR_SECONDS
                probeTimeouts -> CLEARTEXT_PROBE_SECONDS
                viaTor -> TOR_RPC_SECONDS
                else -> 30L
            }
        val callTimeoutSeconds =
            when {
                probeTimeouts -> timeoutSeconds + 1L
                viaTor -> timeoutSeconds + 15L
                else -> timeoutSeconds + 30L
            }
        val builder =
            OkHttpClient.Builder()
                .connectTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .readTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .writeTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .callTimeout(callTimeoutSeconds, TimeUnit.SECONDS)
                .retryOnConnectionFailure(!probeTimeouts)

        if (viaTor) {
            applyTorProxy(builder, torSocksPort)
            builder.protocols(listOf(Protocol.HTTP_1_1))
            // Small pool so parallel payment/on-chain RPCs reuse Tor circuits.
            builder.connectionPool(ConnectionPool(2, 45, TimeUnit.SECONDS))
        }

        if (config.tlsEnabled) {
            when {
                // Zeus exports base64(client_key+client_cert+ca_cert). Pin to the bundle's
                // certs and install the mTLS client identity when present.
                config.tlsCertPem.isNotBlank() ->
                    TlsCertMaterial.applyToOkHttp(builder, config.tlsCertPem)
                // No cert: user is connecting to their own node. Self-signed CLN is
                // common; trust-all here. Prefer pasting the CA/server cert when available.
                else -> TlsCertMaterial.applyInsecureTrust(builder)
            }
        }

        return builder.build()
    }

    private fun applyTorProxy(
        builder: OkHttpClient.Builder,
        socksPort: Int,
    ) {
        builder.proxy(Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", socksPort)))
        builder.dns { hostname ->
            listOf(InetAddress.getByAddress(hostname, byteArrayOf(0, 0, 0, 0)))
        }
    }

    companion object {
        private const val STREAM_RETRY_ATTEMPTS = 3
        private const val CONNECT_CLEAR_STREAM_ATTEMPTS = 2
        private const val CLEARTEXT_PROBE_SECONDS = 3L
        private const val CLEARTEXT_PROBE_TOR_SECONDS = 6L
        private const val TOR_RPC_SECONDS = 45L
        private const val FUNDS_CACHE_TTL_MS = 15_000L
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

        private fun mapPayStatus(status: String): LightningNodePaymentStatus =
            when (status.lowercase()) {
                "complete", "paid", "succeeded" -> LightningNodePaymentStatus.SUCCEEDED
                "failed" -> LightningNodePaymentStatus.FAILED
                "pending", "inflight" -> LightningNodePaymentStatus.PENDING
                else -> LightningNodePaymentStatus.UNKNOWN
            }

        private fun extractClnPaymentFailureReason(pay: JSONObject): String? {
            sequenceOf(
                pay.optString("error"),
                pay.optString("message"),
                pay.optString("failcode"),
                pay.optString("failcodename"),
            ).map { it.trim() }
                .firstOrNull { it.isNotBlank() }
                ?.let { return cleanPaymentError(it) }

            val err = pay.optJSONObject("error")
            if (err != null) {
                err.optString("message").ifBlank { err.optString("code") }
                    .takeIf { it.isNotBlank() }
                    ?.let { return cleanPaymentError(it) }
            }
            // Some CLN builds attach last hop / part failure detail under "attempts".
            val attempts = pay.optJSONArray("attempts")
            if (attempts != null) {
                for (i in attempts.length() - 1 downTo 0) {
                    val attempt = attempts.optJSONObject(i) ?: continue
                    val fail =
                        attempt.optString("failure_message").ifBlank {
                            attempt.optString("failreason").ifBlank {
                                attempt.optString("error").ifBlank {
                                    attempt.optJSONObject("failure")?.optString("message").orEmpty()
                                }
                            }
                        }
                    if (fail.isNotBlank()) return cleanPaymentError(fail)
                }
            }
            return null
        }

        private fun extractError(
            body: String,
            code: Int,
            path: String = "",
        ): String {
            val message =
                runCatching {
                    val json = JSONObject(body)
                    when {
                        json.optJSONObject("error") != null -> {
                            val err = json.getJSONObject("error")
                            err.optString("message").ifBlank { err.optString("code") }
                        }
                        else ->
                            json.optString("message").ifBlank {
                                json.optString("error")
                            }
                    }
                }.getOrNull()
                    ?.ifBlank { null }
            val detail = message ?: body.take(180).ifBlank { null }
            // Surface CLN payment/routing text without HTTP/path noise when we have it.
            if (!detail.isNullOrBlank() && isUserFacingPaymentError(detail)) {
                return cleanPaymentError(detail)
            }
            return buildString {
                append("HTTP $code")
                if (path.isNotBlank()) append(" $path")
                if (detail != null) append(": $detail")
            }.let { cleanPaymentError(it) }
        }

        private fun isMissingRpcMethodError(
            error: Throwable,
            method: String,
        ): Boolean {
            val msg = error.message.orEmpty().lowercase()
            return msg.contains("unknown method") ||
                msg.contains("not found") ||
                msg.contains("unknown parameter") ||
                msg.contains("method not found") ||
                (msg.contains(method) && msg.contains("not available"))
        }

        private fun isUserFacingPaymentError(detail: String): Boolean {
            val lower = detail.lowercase()
            return lower.contains("no connection") ||
                lower.contains("destination") ||
                lower.contains("route") ||
                lower.contains("invoice") ||
                lower.contains("payment") ||
                lower.contains("temporary channel failure") ||
                lower.contains("insufficient") ||
                lower.contains("fee") ||
                lower.contains("ran out of routes") ||
                lower.contains("could not find a route") ||
                lower.contains("remote node") ||
                lower.contains("offer") ||
                lower.contains("onion")
        }

        private fun cleanPaymentError(raw: String): String {
            var msg =
                raw
                    .removePrefix("Failed:")
                    .removePrefix("failed:")
                    .trim()
            // Strip clnrest wrapping like "HTTP 500 /v1/xpay: Failed: …"
            val colonIdx = msg.indexOf(": ")
            if (
                colonIdx > 0 &&
                (
                    msg.startsWith("HTTP ", ignoreCase = true) ||
                        msg.contains("/v1/", ignoreCase = true)
                    )
            ) {
                val tail = msg.substring(colonIdx + 2).trim()
                if (tail.isNotBlank()) msg = tail.removePrefix("Failed:").trim()
            }
            return mapBolt12FetchError(msg.ifBlank { raw })
        }

        private fun mapBolt12FetchError(raw: String?): String {
            val msg = raw?.trim().orEmpty()
            if (msg.isBlank()) return "BOLT12 offer fetch failed"
            val lower = msg.lowercase()
            return when {
                lower.contains("remote node sent failure") ||
                    lower.contains("returned an error message") ->
                    "Offer issuer rejected the invoice request"
                lower.contains("cannot find a route") ||
                    lower.contains("no connection") && lower.contains("destination") ->
                    "No route to offer issuer (onion messages)"
                lower.contains("timed out") || lower.contains("timeout") ->
                    "Offer issuer did not respond in time"
                lower.contains("offer has expired") || lower.contains("expired") ->
                    "This BOLT12 offer has expired"
                else -> msg
            }
        }

        /** CLN often returns msat as number, string, or "12345msat". */
        private fun msatToSats(
            json: JSONObject,
            vararg keys: String,
        ): Long {
            for (key in keys) {
                if (!json.has(key) || json.isNull(key)) continue
                val raw = json.opt(key) ?: continue
                val msat = parseMsat(raw) ?: continue
                return msat / 1000L
            }
            return 0L
        }

        private fun amountToSats(
            json: JSONObject,
            vararg keys: String,
        ): Long {
            for (key in keys) {
                if (!json.has(key) || json.isNull(key)) continue
                val raw = json.opt(key) ?: continue
                if (key.contains("msat", ignoreCase = true)) {
                    val msat = parseMsat(raw) ?: continue
                    return msat / 1000L
                }
                when (raw) {
                    is Number -> return raw.toLong()
                    is String -> {
                        val cleaned = raw.trim().removeSuffix("msat").removeSuffix("sat")
                        cleaned.toLongOrNull()?.let { return it }
                    }
                }
            }
            return 0L
        }

        private fun parseMsat(raw: Any): Long? =
            when (raw) {
                is Number -> raw.toLong()
                is String -> {
                    val cleaned = raw.trim().removeSuffix("msat").trim()
                    cleaned.toLongOrNull()
                }
                else -> null
            }

        private fun parseClnTimestamp(raw: String): Long {
            val trimmed = raw.trim()
            if (trimmed.isBlank()) return 0L
            val asLong =
                trimmed.toLongOrNull()
                    ?: trimmed.substringBefore('.').toLongOrNull()
                    ?: return 0L
            // Seconds since epoch vs already-ms
            return if (asLong < 10_000_000_000L) asLong * 1000L else asLong
        }
    }
}
