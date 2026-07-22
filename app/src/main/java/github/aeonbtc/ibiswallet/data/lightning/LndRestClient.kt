package github.aeonbtc.ibiswallet.data.lightning

import android.util.Base64
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
import github.aeonbtc.ibiswallet.util.BitcoinUtils
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
import org.bitcoindevkit.Transaction as BdkTransaction
import org.json.JSONArray
import org.json.JSONObject
import java.io.EOFException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit
import kotlin.math.roundToLong

/**
 * LND node client via HTTP REST API (lnd REST proxy / litd).
 * Auth: macaroon + optional TLS. UI label: LND.
 * REST maps 1:1 to lightning.proto methods and works cleanly over Tor SOCKS5.
 */
class LndRestClient(
    private val torSocksPort: Int = 9050,
) : LightningNodeBackend {
    private var client: OkHttpClient? = null
    private var baseUrl: String = ""
    private var macaroonHex: String = ""
    private var activeConfig: LightningNodeConfig? = null

    /** Actual TLS mode used for the live session (after auto-upgrade if any). */
    val sessionUseTls: Boolean
        get() = activeConfig?.tlsEnabled == true

    val sessionConfig: LightningNodeConfig?
        get() = activeConfig

    override suspend fun connect(config: LightningNodeConfig): LightningNodeInfo =
        withContext(Dispatchers.IO) {
            require(config.host.isNotBlank()) { "Host is required" }
            require(config.port in 1..65535) { "Invalid port" }
            require(config.macaroonHex.isNotBlank()) { "Macaroon is required" }

            macaroonHex = normalizeMacaroon(config.macaroonHex)
            val host =
                config.host
                    .trim()
                    .removePrefix("https://")
                    .removePrefix("http://")
                    .substringBefore('/')
                    .substringBefore(':') // strip accidental :port in host field
                    .trimEnd('/')
            require(host.isNotBlank()) { "Host is required" }
            val needsTor = config.useTor || host.endsWith(".onion", ignoreCase = true)
            if (host.endsWith(".onion", ignoreCase = true) && !needsTor) {
                throw IllegalStateException("Onion hosts require Tor")
            }
            val normalized = config.copy(useTor = needsTor, host = host)

            // Prefer configured TLS. If cleartext fails because the node expects HTTPS
            // (common for LND REST :8080 / Tor), auto-retry once with TLS.
            // Also retry once after Tor stream flakiness (unexpected end of stream).
            connectWithRetries(normalized)
        }

    private fun connectWithRetries(normalized: LightningNodeConfig): LightningNodeInfo {
        var lastError: Exception? = null
        val viaTor =
            normalized.useTor || normalized.host.endsWith(".onion", ignoreCase = true)
        // Scheme order:
        // - TLS on → that scheme only
        // - LAN + TLS off → short HTTP probe, then HTTPS
        // - Tor + TLS off → HTTPS first (LND/CLN REST over .onion is almost always TLS);
        //   cleartext only as a last short probe — never burned minutes on dead HTTP circuits
        // - preferSessionTls → HTTPS first (LAN or Tor)
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
                        // Last-resort cleartext only on clearnet (rare plain-HTTP nodes).
                        // Over Tor a failing HTTPS would just waste another circuit on HTTP.
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
            // Connect: one attempt per scheme over Tor (new circuit is expensive).
            // Clearnet HTTPS keeps a single stream retry; cleartext is always one-shot.
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
                    // Auth / 4xx on the chosen scheme is definitive — don't mask with TLS.
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
    }

    private fun closeClientQuietly() {
        // Evict sockets only; do not shut down the shared dispatcher executor.
        runCatching { client?.connectionPool?.evictAll() }
        client = null
        activeConfig = null
    }

    /** Failures that are about credentials/API, not transport scheme. */
    private fun isDefinitiveHttpFailure(error: Exception): Boolean {
        val message = error.message.orEmpty().lowercase()
        return message.contains("http 401") ||
            message.contains("http 403") ||
            message.contains("permission denied") ||
            message.contains("macaroon") && message.contains("invalid") ||
            message.contains("unknown macaroon")
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
            val channel = getJson("/v1/balance/channels")
            val local = channel.optLong("balance", 0L)
            val remote = channel.optLong("remote_balance", 0L)
            // pending open balance fields may be present; local balance is spendable
            LightningNodeBalance(
                localBalanceSats = local,
                remoteBalanceSats = remote,
            )
        }

    override suspend fun listChannels(): List<LightningNodeChannel> =
        withContext(Dispatchers.IO) {
            val open =
                runCatching { getJson("/v1/channels") }
                    .getOrElse { JSONObject() }
            val pending =
                runCatching { getJson("/v1/channels/pending") }
                    .getOrElse { JSONObject() }
            val channels = mutableListOf<LightningNodeChannel>()
            val seen = mutableSetOf<String>()

            fun addUnique(channel: LightningNodeChannel) {
                if (!seen.add(channel.id)) return
                channels += channel
            }

            val openArr = open.optJSONArray("channels") ?: JSONArray()
            for (i in 0 until openArr.length()) {
                val ch = openArr.optJSONObject(i) ?: continue
                val channelId =
                    ch.optString("chan_id").ifBlank {
                        ch.optString("channel_point")
                    }
                if (channelId.isBlank()) continue
                val (capacity, local, remote) = readLndChannelBalances(ch)
                val active = ch.optBoolean("active", false)
                addUnique(
                    LightningNodeChannel(
                        id = channelId,
                        remotePubkey =
                            ch.optString("remote_pubkey").ifBlank {
                                ch.optString("remote_node_pub")
                            },
                        remoteAlias = ch.optString("peer_alias").ifBlank { null },
                        shortChannelId = ch.optString("chan_id").ifBlank { null },
                        fundingTxid =
                            ch.optString("channel_point")
                                .substringBefore(':')
                                .ifBlank { null },
                        capacitySats = capacity,
                        localBalanceSats = local,
                        remoteBalanceSats = remote,
                        isActive = active,
                        isPrivate = ch.optBoolean("private", false),
                        state = if (active) "ACTIVE" else "INACTIVE",
                    ),
                )
            }

            val tipHeight =
                runCatching {
                    val info = getJson("/v1/getinfo")
                    info.optString("block_height").toIntOrNull()
                        ?: info.optInt("block_height", 0)
                }.getOrDefault(0)

            // Pending open/closing/force-close/waiting — not returned by /v1/channels.
            fun addPendingGroup(
                arrayKey: String,
                stateLabel: String,
            ) {
                val arr = pending.optJSONArray(arrayKey) ?: return
                for (i in 0 until arr.length()) {
                    val wrapper = arr.optJSONObject(i) ?: continue
                    val ch = wrapper.optJSONObject("channel") ?: wrapper
                    val channelPoint =
                        ch.optString("channel_point").ifBlank {
                            wrapper.optString("channel_point")
                        }
                    val channelId =
                        channelPoint.ifBlank {
                            ch.optString("chan_id")
                        }.ifBlank {
                            "${ch.optString("remote_node_pub")}-$stateLabel-$i"
                        }
                    if (channelId.isBlank()) continue
                    // commit_fee lives on the PendingOpenChannel wrapper, not nested channel.
                    val (capacity, local, remote) =
                        readLndChannelBalances(
                            channel = ch,
                            commitFeeFallback = wrapper,
                        )
                    val confsUntilActive =
                        wrapper.optString("confirmations_until_active").toIntOrNull()
                            ?: wrapper.optInt("confirmations_until_active", 0)
                    val confirmationHeight =
                        wrapper.optString("confirmation_height").toIntOrNull()
                            ?: wrapper.optInt("confirmation_height", 0)
                    val fundingConfs =
                        wrapper.optString("num_confirmations").toIntOrNull()
                            ?: wrapper.optInt("num_confirmations", 0)
                            .takeIf { it > 0 }
                            ?: ch.optString("num_confirmations").toIntOrNull()
                            ?: ch.optInt("num_confirmations", 0)
                    val state =
                        if (arrayKey == "pending_open_channels") {
                            formatPendingOpenState(
                                stateLabel = stateLabel,
                                confsUntilActive = confsUntilActive,
                                confirmationHeight = confirmationHeight,
                                fundingConfs = fundingConfs,
                                tipHeight = tipHeight,
                            )
                        } else {
                            stateLabel
                        }
                    addUnique(
                        LightningNodeChannel(
                            id = channelId,
                            remotePubkey =
                                ch.optString("remote_node_pub").ifBlank {
                                    ch.optString("remote_pubkey")
                                },
                            remoteAlias = null,
                            shortChannelId = null,
                            fundingTxid = channelPoint.substringBefore(':').ifBlank { null },
                            capacitySats = capacity,
                            localBalanceSats = local,
                            remoteBalanceSats = remote,
                            isActive = false,
                            isPrivate = ch.optBoolean("private", false),
                            state = state,
                        ),
                    )
                }
            }

            addPendingGroup("pending_open_channels", "AWAITING LOCKIN")
            addPendingGroup("pending_closing_channels", "CLOSING")
            addPendingGroup("pending_force_closing_channels", "FORCE CLOSE")
            addPendingGroup("waiting_close_channels", "WAIT CLOSE")

            channels.sortedWith(
                compareByDescending<LightningNodeChannel> {
                    !it.isActive && (
                        it.state?.contains("PENDING", ignoreCase = true) == true ||
                            it.state?.contains("LOCKIN", ignoreCase = true) == true ||
                            it.state?.contains("OPEN", ignoreCase = true) == true
                        )
                }.thenByDescending { it.isActive }
                    .thenByDescending { it.capacitySats },
            )
        }

    override suspend fun listPayments(limit: Int): List<LightningNodePayment> =
        withContext(Dispatchers.IO) {
            val capped = limit.coerceIn(1, 100)
            // Fetch pays + settled invoices in parallel; sequential doubled Tor/LAN latency.
            val (paymentsJson, invoicesJson) =
                coroutineScope {
                    val paymentsDeferred =
                        async {
                            runCatching {
                                getJson("/v1/payments?include_incomplete=true&max_payments=$capped")
                            }.getOrElse {
                                getJson("/v1/payments?include_incomplete=false&max_payments=$capped")
                            }
                        }
                    val invoicesDeferred =
                        async {
                            getJson("/v1/invoices?num_max_invoices=$capped&reversed=true")
                        }
                    paymentsDeferred.await() to invoicesDeferred.await()
                }
            val payments = mutableListOf<LightningNodePayment>()
            val paymentArr = paymentsJson.optJSONArray("payments") ?: JSONArray()
            for (i in 0 until paymentArr.length()) {
                val p = paymentArr.getJSONObject(i)
                val status = mapPaymentStatus(p.optString("status"))
                val value = p.optString("value", p.optString("value_sat", "0")).toLongOrNull() ?: 0L
                val fee = p.optString("fee", p.optString("fee_sat", "0")).toLongOrNull() ?: 0L
                val creation = p.optLong("creation_date", 0L) * 1000L
                val hash = p.optString("payment_hash").ifBlank { null }
                payments +=
                    LightningNodePayment(
                        id = hash ?: "pay-$i",
                        direction = LightningNodePaymentDirection.OUTGOING,
                        status = status,
                        amountSats = value,
                        feeSats = fee,
                        timestamp = creation,
                        paymentHash = hash,
                        paymentRequest = p.optString("payment_request").ifBlank { null },
                        memo = null,
                        destination = extractLndPaymentDestination(p),
                        failureReason =
                            if (status == LightningNodePaymentStatus.FAILED) {
                                extractLndPaymentFailureReason(p)
                            } else {
                                null
                            },
                    )
            }

            val invArr = invoicesJson.optJSONArray("invoices") ?: JSONArray()
            for (i in 0 until invArr.length()) {
                val inv = invArr.getJSONObject(i)
                val settled = inv.optBoolean("settled", false) || inv.optString("state") == "SETTLED"
                if (!settled) continue
                val value = inv.optString("value", inv.optString("amt_paid_sat", "0")).toLongOrNull() ?: 0L
                val settleDate = inv.optLong("settle_date", inv.optLong("creation_date", 0L)) * 1000L
                val hash = inv.optString("r_hash").ifBlank { inv.optString("payment_addr") }.ifBlank { null }
                val decodedHash =
                    hash?.let { decodePossibleBase64Hash(it) } ?: hash
                payments +=
                    LightningNodePayment(
                        id = decodedHash ?: "inv-$i",
                        direction = LightningNodePaymentDirection.INCOMING,
                        status = LightningNodePaymentStatus.SUCCEEDED,
                        amountSats = value,
                        feeSats = 0,
                        timestamp = settleDate,
                        paymentHash = decodedHash,
                        paymentRequest = inv.optString("payment_request").ifBlank { null },
                        memo = inv.optString("memo").ifBlank { null },
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
            // total_balance includes inputs currently locked by a pending send. Split
            // unlocked spendable UTXOs from locked/leased ones, then subtract residual
            // unconfirmed outs that ListUnspent still reports as free.
            val utxos =
                runCatching { getJson("/v1/utxos?min_confs=0&max_confs=9999999") }
                    .recoverCatching { getJson("/v2/wallet/utxos?min_confs=0&max_confs=9999999") }
                    .getOrNull()
                    ?.optJSONArray("utxos")
            if (utxos != null) {
                var available = 0L
                var locked = 0L
                for (i in 0 until utxos.length()) {
                    val utxo = utxos.optJSONObject(i) ?: continue
                    val amount = readLndAmount(utxo, "amount_sat").coerceAtLeast(0L)
                    if (isLndUtxoUnavailable(utxo)) {
                        locked += amount
                    } else {
                        available += amount
                    }
                }
                val pendingOut = pendingLndOutgoingAmount()
                val spendable = (available - pendingOut).coerceAtLeast(0L)
                val reserved = locked + (available - spendable).coerceAtLeast(0L)
                return@withContext LightningNodeOnchainBalanceDetails(
                    spendableSats = spendable,
                    reservedSats = reserved,
                )
            }
            // Fallback for restricted LND runes that cannot list UTXOs.
            val balance = getJson("/v1/balance/blockchain")
            val confirmed = readLndAmount(balance, "confirmed_balance").coerceAtLeast(0L)
            val pendingOut = pendingLndOutgoingAmount()
            val spendable = (confirmed - pendingOut).coerceAtLeast(0L)
            LightningNodeOnchainBalanceDetails(
                spendableSats = spendable,
                reservedSats = (confirmed - spendable).coerceAtLeast(0L),
            )
        }

    override suspend fun newOnchainAddress(): String =
        withContext(Dispatchers.IO) {
            // LND REST: GET /v1/newaddress?type=… (not POST).
            val response = getJson("/v1/newaddress?type=WITNESS_PUBKEY_HASH")
            response.optString("address").takeIf { it.isNotBlank() }
                ?: throw IllegalStateException("Node did not return an address")
        }

    override suspend fun listOnchainTransactions(limit: Int): List<LightningNodeOnchainTransaction> =
        withContext(Dispatchers.IO) {
            val capped = limit.coerceIn(1, 200)
            // LND REST uses max_transactions (proto field name).
            // Do NOT call listChannels / closed-channels here — that forked 4+ extra RPCs
            // on every balance sync. Channel open/close badges use LND tx labels only.
            val response =
                runCatching { getJson("/v1/transactions?max_transactions=$capped") }
                    .recoverCatching { getJson("/v1/transactions") }
                    .getOrThrow()
            val transactions = response.optJSONArray("transactions") ?: JSONArray()
            buildList {
                for (i in 0 until transactions.length()) {
                    val tx = transactions.optJSONObject(i) ?: continue
                    val amount = readLndAmount(tx, "amount")
                    val fees = readLndAmount(tx, "total_fees")
                    val destinations = tx.optJSONArray("dest_addresses")
                    val conf =
                        tx.optString("num_confirmations").toIntOrNull()
                            ?: tx.optInt("num_confirmations", 0)
                    val stamp =
                        tx.optString("time_stamp").toLongOrNull()
                            ?: tx.optLong("time_stamp", 0L)
                    val txid = tx.optString("tx_hash")
                    val label = tx.optString("label")
                    val outputs =
                        buildList {
                            val outputDetails = tx.optJSONArray("output_details") ?: JSONArray()
                            for (j in 0 until outputDetails.length()) {
                                val detail = outputDetails.optJSONObject(j) ?: continue
                                val outAddress = detail.optString("address").ifBlank { null } ?: continue
                                add(
                                    LndTxOutput(
                                        address = outAddress,
                                        amountSats = readLndAmount(detail, "amount").coerceAtLeast(0L),
                                        isOurs = detail.optBoolean("is_our_address", false),
                                    ),
                                )
                            }
                        }
                    val destFallback = destinations?.optString(0)?.ifBlank { null }
                    val resolved = resolveLndTxAddresses(amount, outputs, destFallback)
                    val isChannelOpen =
                        amount < 0L &&
                            (
                                label.contains("openchannel", ignoreCase = true) ||
                                    label.contains("funding", ignoreCase = true)
                            )
                    val isChannelClose =
                        !isChannelOpen &&
                            (
                                label.contains("closechannel", ignoreCase = true) ||
                                    label.contains("coop close", ignoreCase = true) ||
                                    label.contains("force close", ignoreCase = true) ||
                                    label.contains("channel close", ignoreCase = true)
                            )
                    add(
                        LightningNodeOnchainTransaction(
                            txid = txid,
                            amountSats = amount,
                            feeSats = fees,
                            vsize = transactionVsize(tx.optString("raw_tx_hex")),
                            timestamp = stamp * 1000L,
                            confirmations = conf,
                            address = resolved.address,
                            addressAmountSats = resolved.addressAmountSats,
                            changeAddress = resolved.changeAddress,
                            changeAmountSats = resolved.changeAmountSats,
                            isChannelOpen = isChannelOpen,
                            isChannelClose = isChannelClose,
                        ),
                    )
                }
            }.sortedWith(
                compareByDescending<LightningNodeOnchainTransaction> { it.confirmations <= 0 }
                    .thenByDescending { it.timestamp }
                    .thenByDescending { it.txid },
            )
                .take(capped)
        }

    private fun loadLndChannelClosingTxids(): Set<String> {
        val closing = linkedSetOf<String>()
        fun addTxid(raw: String?) {
            raw
                ?.trim()
                ?.substringBefore(':')
                ?.lowercase()
                ?.takeIf { it.isNotBlank() }
                ?.let { closing += it }
        }
        fun absorbPendingArray(pending: JSONObject, key: String) {
            val arr = pending.optJSONArray(key) ?: return
            for (i in 0 until arr.length()) {
                val wrapper = arr.optJSONObject(i) ?: continue
                addTxid(wrapper.optString("closing_txid"))
                addTxid(wrapper.optString("closing_tx_hash"))
                val commitments = wrapper.optJSONObject("commitments")
                if (commitments != null) {
                    addTxid(commitments.optString("local_txid"))
                    addTxid(commitments.optString("remote_txid"))
                    addTxid(commitments.optString("remote_pending_txid"))
                }
            }
        }
        runCatching {
            val pending = getJson("/v1/channels/pending")
            absorbPendingArray(pending, "pending_closing_channels")
            absorbPendingArray(pending, "pending_force_closing_channels")
            absorbPendingArray(pending, "waiting_close_channels")
        }
        runCatching {
            val closed = getJson("/v1/channels/closed")
            val arr = closed.optJSONArray("channels") ?: JSONArray()
            for (i in 0 until arr.length()) {
                val ch = arr.optJSONObject(i) ?: continue
                addTxid(ch.optString("closing_tx_hash"))
                addTxid(ch.optString("closing_txid"))
            }
        }
        return closing
    }

    override suspend fun listOnchainUtxos(): List<github.aeonbtc.ibiswallet.data.model.UtxoInfo> =
        withContext(Dispatchers.IO) {
            // Prefer lightning ListUnspent; WalletKit endpoint as fallback.
            val response =
                runCatching { getJson("/v1/utxos?min_confs=0&max_confs=9999999") }
                    .recoverCatching { getJson("/v2/wallet/utxos?min_confs=0&max_confs=9999999") }
                    .getOrThrow()
            val utxos = response.optJSONArray("utxos") ?: JSONArray()
            buildList {
                for (i in 0 until utxos.length()) {
                    val u = utxos.optJSONObject(i) ?: continue
                    val amount = readLndAmount(u, "amount_sat").coerceAtLeast(0L).toULong()
                    val address = u.optString("address").orEmpty()
                    val conf =
                        u.optString("confirmations").toLongOrNull()
                            ?: u.optLong("confirmations", 0L)
                    val out = u.optJSONObject("outpoint")
                    val txid =
                        out?.optString("txid_str")?.ifBlank { null }
                            ?: out?.optString("txid")?.ifBlank { null }
                            ?: ""
                    val vout =
                        (
                            out?.optString("output_index")?.toIntOrNull()
                                ?: out?.optInt("output_index", 0)
                                ?: 0
                        ).coerceAtLeast(0).toUInt()
                    if (txid.isBlank() || address.isBlank()) continue
                    // Locked/leased outs stay visible but frozen so Max/coin control
                    // match spendable balance (SendCoins will not spend them).
                    val locked = isLndUtxoUnavailable(u)
                    add(
                        github.aeonbtc.ibiswallet.data.model.UtxoInfo(
                            outpoint = "$txid:$vout",
                            txid = txid,
                            vout = vout,
                            address = address,
                            amountSats = amount,
                            isConfirmed = conf > 0,
                            isFrozen = locked,
                        ),
                    )
                }
            }
        }

    private fun readLndAmount(
        json: JSONObject,
        key: String,
    ): Long =
        json.optString(key).toLongOrNull()
            ?: json.optLong(key, 0L)

    private fun isLndUtxoUnavailable(utxo: JSONObject): Boolean {
        if (utxo.optBoolean("locked", false)) return true
        if (utxo.has("spendable") && !utxo.optBoolean("spendable", true)) return true
        return sequenceOf("lease_expiration", "lease_expiry", "lock_expiration")
            .map { key ->
                utxo.optString(key).toLongOrNull()
                    ?: utxo.optLong(key, 0L)
            }.any { it > 0L }
    }

    /**
     * LND's balance / ListUnspent can retain a confirmed input until its spending tx confirms.
     * Remove unconfirmed outgoing transaction deltas so L1 reflects funds available to send.
     */
    private fun pendingLndOutgoingAmount(): Long =
        runCatching {
            // Only recent unconfirmed outs matter; pulling 200 full txs for balance was
            // the single slowest hop on every on-chain refresh (then txs were fetched again).
            val transactions =
                getJson("/v1/transactions?max_transactions=25")
                    .optJSONArray("transactions")
                    ?: return@runCatching 0L
            var total = 0L
            for (i in 0 until transactions.length()) {
                val tx = transactions.optJSONObject(i) ?: continue
                val confirmations =
                    tx.optString("num_confirmations").toIntOrNull()
                        ?: tx.optInt("num_confirmations", 0)
                if (confirmations > 0) continue
                val amount = readLndAmount(tx, "amount")
                if (amount < 0L) {
                    total += -amount
                }
            }
            total
        }.getOrDefault(0L)

    /**
     * LND reports [local_balance] exclusive of [commit_fee] for the initiator.
     * Zeus / typical UIs treat commit-fee sats as still local (Send / Max Send), so:
     * local + remote + commit_fee (+ anchors) ≈ capacity.
     */
    private fun readLndChannelBalances(
        channel: JSONObject,
        commitFeeFallback: JSONObject? = null,
    ): Triple<Long, Long, Long> {
        val capacity = readLndAmount(channel, "capacity").coerceAtLeast(0L)
        val rawLocal = readLndAmount(channel, "local_balance").coerceAtLeast(0L)
        val rawRemote = readLndAmount(channel, "remote_balance").coerceAtLeast(0L)
        val commitFee =
            readLndAmount(channel, "commit_fee")
                .takeIf { it > 0L }
                ?: commitFeeFallback?.let { readLndAmount(it, "commit_fee") }?.coerceAtLeast(0L)
                ?: 0L
        // Only fold commit fee into Send when it closes the capacity gap (initiator pays fees).
        val residual = (capacity - rawLocal - rawRemote).coerceAtLeast(0L)
        val local =
            if (commitFee > 0L && residual >= commitFee) {
                rawLocal + commitFee
            } else {
                rawLocal
            }
        val remote = rawRemote.coerceAtMost((capacity - local).coerceAtLeast(0L))
        return Triple(capacity, local.coerceAtMost(capacity), remote)
    }

    override suspend fun sendOnchain(
        address: String,
        amountSats: Long?,
        satPerVbyte: Double?,
        sendAll: Boolean,
        label: String?,
        spendUnconfirmed: Boolean,
        selectedOutpoints: List<github.aeonbtc.ibiswallet.data.model.UtxoInfo>?,
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
            // Fractional sat/vB (0.5, 1.25, …): control via sat_per_kw (SendCoins is integer only).
            if (needsFractionalFee(satPerVbyte)) {
                return@withContext sendOnchainFractionalSweep(
                    address = address.trim(),
                    amountSats = amountSats,
                    sendAll = sendAll,
                    satPerVbyte = satPerVbyte!!,
                    label = label,
                    spendUnconfirmed = spendUnconfirmed,
                    selectedOutpoints = selectedOutpoints,
                )
            }
            val body =
                JSONObject().apply {
                    put("addr", address.trim())
                    if (sendAll) {
                        put("send_all", true)
                    } else {
                        put("amount", amountSats!!.toString())
                    }
                    putIntegerSatPerVbyte(this, satPerVbyte)
                    if (!label.isNullOrBlank()) {
                        put("label", label.trim().take(500))
                    }
                    put("spend_unconfirmed", spendUnconfirmed)
                    putOutpoints(this, selectedOutpoints)
                }
            val response = postJson("/v1/transactions", body)
            response.optString("txid").takeIf { it.isNotBlank() }
                ?: throw IllegalStateException("Node did not return a transaction ID")
        }

    override suspend fun sendOnchainMany(
        addrToAmountSats: Map<String, Long>,
        satPerVbyte: Double?,
        label: String?,
        spendUnconfirmed: Boolean,
        selectedOutpoints: List<github.aeonbtc.ibiswallet.data.model.UtxoInfo>?,
    ): String =
        withContext(Dispatchers.IO) {
            require(addrToAmountSats.isNotEmpty()) { "At least one recipient is required" }
            require(addrToAmountSats.all { it.key.isNotBlank() && it.value > 0 }) {
                "Each recipient needs a valid address and positive amount"
            }
            if (needsFractionalFee(satPerVbyte)) {
                return@withContext sendOnchainFractional(
                    addrToAmountSats = addrToAmountSats,
                    satPerVbyte = satPerVbyte!!,
                    label = label,
                    spendUnconfirmed = spendUnconfirmed,
                    selectedOutpoints = selectedOutpoints,
                )
            }
            val amountMap = JSONObject()
            addrToAmountSats.forEach { (addr, amount) ->
                amountMap.put(addr.trim(), amount.toString())
            }
            val body =
                JSONObject().apply {
                    // LND REST uses camel-style map field names for this RPC.
                    put("AddrToAmount", amountMap)
                    putIntegerSatPerVbyte(this, satPerVbyte)
                    if (!label.isNullOrBlank()) {
                        put("label", label.trim().take(500))
                    }
                    put("spend_unconfirmed", spendUnconfirmed)
                    putOutpoints(this, selectedOutpoints)
                }
            val response =
                runCatching { postJson("/v1/transactions/many", body) }
                    .recoverCatching {
                        // Some proxies only accept snake_case map field.
                        body.remove("AddrToAmount")
                        body.put("addr_to_amount", amountMap)
                        postJson("/v1/transactions/many", body)
                    }.getOrThrow()
            response.optString("txid").takeIf { it.isNotBlank() }
                ?: throw IllegalStateException("Node did not return a transaction ID")
        }

    override suspend fun getOnchainMinRelayFeeSatPerVb(): Double? =
        withContext(Dispatchers.IO) {
            // WalletKit EstimateFee returns the bitcoind/btcd min relay fee from the
            // chain backend — same role as Electrum blockchain.relayfee for L1 wallets.
            // conf_target only affects sat_per_kw estimate; min_relay is independent.
            val json =
                runCatching { getJson("/v2/wallet/estimatefee/6") }
                    .recoverCatching { getJson("/v2/wallet/estimatefee/2") }
                    .getOrNull()
                    ?: return@withContext null
            val satPerKw =
                json.optString("min_relay_fee_sat_per_kw")
                    .toLongOrNull()
                    ?: json.optLong("min_relay_fee_sat_per_kw", 0L)
            if (satPerKw <= 0L) return@withContext null
            // 1 sat/vB = 250 sat/kw (kWU).
            val satPerVb = satPerKw.toDouble() / 250.0
            satPerVb.takeIf { it in 0.01..100.0 }
        }

    override suspend fun bumpOnchainFee(
        outpoint: github.aeonbtc.ibiswallet.data.model.UtxoInfo,
        satPerVbyte: Double?,
        immediate: Boolean,
        budgetSats: Long?,
    ): String =
        withContext(Dispatchers.IO) {
            require(outpoint.txid.isNotBlank()) { "Outpoint txid is required" }
            // BumpFee only accepts integer sat/vB — round (keeps UI decimals for send path).
            val integerRate =
                satPerVbyte
                    ?.takeIf { it > 0.0 }
                    ?.let { kotlin.math.ceil(it).toLong().coerceAtLeast(1L) }
            val body =
                JSONObject().apply {
                    put(
                        "outpoint",
                        JSONObject()
                            .put("txid_str", outpoint.txid)
                            .put("output_index", outpoint.vout.toInt()),
                    )
                    if (integerRate != null) {
                        put("sat_per_vbyte", integerRate.toString())
                    }
                    put("immediate", immediate)
                    if (budgetSats != null && budgetSats > 0) {
                        put("budget", budgetSats.toString())
                    }
                }
            val response = postJson("/v2/wallet/bumpfee", body)
            response.optString("status").ifBlank {
                "Fee bump submitted"
            }
        }

    /**
     * WalletKit SendOutputs platform path for fractional sat/vB. For max/send_all the output
     * value is derived from spendable−weight-fee, then reduced on construction errors
     * (reserve / fee shortfall / dust) until the node accepts the package.
     */
    private fun sendOnchainFractionalSweep(
        address: String,
        amountSats: Long?,
        sendAll: Boolean,
        satPerVbyte: Double,
        label: String?,
        spendUnconfirmed: Boolean,
        selectedOutpoints: List<github.aeonbtc.ibiswallet.data.model.UtxoInfo>?,
    ): String {
        if (!selectedOutpoints.isNullOrEmpty()) {
            throw IllegalStateException(
                "Coin control with sub-sat fee rates is not supported on LND — " +
                    "use a whole-number sat/vB or leave coin control off",
            )
        }
        val startAmount =
            if (sendAll) {
                estimateSendAllAmountSats(
                    feeRate = satPerVbyte,
                    selectedOutpoints = null,
                    recipientAddress = address,
                )
            } else {
                amountSats!!
            }
        var attempt = startAmount
        var lastError: Exception? = null
        // Shrink output if WalletKit rejects construction (fee/reserve/shortfall).
        repeat(12) {
            if (attempt <= 0L) return@repeat
            try {
                return sendOnchainFractional(
                    addrToAmountSats = mapOf(address to attempt),
                    satPerVbyte = satPerVbyte,
                    label = label,
                    spendUnconfirmed = spendUnconfirmed,
                    selectedOutpoints = null,
                )
            } catch (e: Exception) {
                lastError = e
                if (!isLndInsufficientFundsError(e) || !sendAll) throw e
                // Drop a few sats and retry — common when weight estimate is low or
                // RequiredReserve slightly exceeds our available−fee headroom.
                attempt = (attempt - 5L).coerceAtLeast(0L)
            }
        }
        throw lastError ?: IllegalStateException("On-chain send failed")
    }

    /**
     * WalletKit SendOutputs (`POST /v2/wallet/send`) with [sat_per_kw] for sub-sat / fractional
     * sat/vB. SendCoins/SendMany only take integer [sat_per_vbyte].
     */
    private fun sendOnchainFractional(
        addrToAmountSats: Map<String, Long>,
        satPerVbyte: Double,
        label: String?,
        spendUnconfirmed: Boolean,
        selectedOutpoints: List<github.aeonbtc.ibiswallet.data.model.UtxoInfo>?,
    ): String {
        if (!selectedOutpoints.isNullOrEmpty()) {
            throw IllegalStateException(
                "Coin control with sub-sat fee rates is not supported on LND — " +
                    "use a whole-number sat/vB or leave coin control off",
            )
        }
        val satPerKw = BitcoinUtils.feeRateToSatPerKwu(satPerVbyte).toLong().coerceAtLeast(1L)
        val outputs = JSONArray()
        addrToAmountSats.forEach { (addr, amount) ->
            val scriptBytes =
                runCatching {
                    Address(addr.trim(), Network.BITCOIN)
                        .scriptPubkey()
                        .toBytes()
                        .toUByteArray()
                        .toByteArray()
                }.getOrElse {
                    throw IllegalArgumentException("Invalid address: $addr")
                }
            outputs.put(
                JSONObject()
                    .put("value", amount.toString())
                    .put("pk_script", Base64.encodeToString(scriptBytes, Base64.NO_WRAP)),
            )
        }
        val body =
            JSONObject().apply {
                put("sat_per_kw", satPerKw.toString())
                put("outputs", outputs)
                if (!label.isNullOrBlank()) {
                    put("label", label.trim().take(500))
                }
                put("spend_unconfirmed", spendUnconfirmed)
                // Explicit min_confs so REST gateways that ignore bool alone still allow 0-conf.
                if (spendUnconfirmed) {
                    put("min_confs", 0)
                }
            }
        val response = postJson("/v2/wallet/send", body)
        val rawTxB64 =
            response.optString("raw_tx").ifBlank {
                throw IllegalStateException("Node did not return a transaction")
            }
        val rawTx =
            runCatching { Base64.decode(rawTxB64, Base64.DEFAULT) }
                .getOrElse { throw IllegalStateException("Invalid raw transaction from node") }
        return runCatching {
            BdkTransaction(rawTx).computeTxid().toString()
        }.getOrElse {
            // Fallback: double-SHA256 of the serialized tx (bitcoin wire id order).
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(digest.digest(rawTx))
            hash.reversedArray().joinToString("") { b -> "%02x".format(b) }
        }
    }

    private fun isLndInsufficientFundsError(error: Exception): Boolean {
        val msg = error.message.orEmpty().lowercase()
        return msg.contains("insufficient") ||
            msg.contains("not enough") ||
            msg.contains("reserve") ||
            msg.contains("unable to create") ||
            msg.contains("construct")
    }

    private fun estimateSendAllAmountSats(
        feeRate: Double,
        selectedOutpoints: List<github.aeonbtc.ibiswallet.data.model.UtxoInfo>?,
        recipientAddress: String,
    ): Long {
        val utxos =
            if (!selectedOutpoints.isNullOrEmpty()) {
                selectedOutpoints
            } else {
                // Prefer concrete utxo list so multi-input sweeps size fee correctly.
                runCatching { listOnchainUtxosBlocking() }.getOrDefault(emptyList())
            }
        val utxoSum = utxos.sumOf { it.amountSats.toLong() }
        // ListUnspent can briefly include coins washed by pending outs —
        // clamp to spendable; also hold back LND anchor required reserve.
        val spendable = getOnchainBalanceDetailsBlocking().spendableSats
        val reserve = runCatching { fetchRequiredReserveSats() }.getOrDefault(0L).coerceAtLeast(0L)
        val available =
            when {
                !selectedOutpoints.isNullOrEmpty() -> utxoSum
                utxoSum > 0L && spendable > 0L -> minOf(utxoSum, spendable)
                utxoSum > 0L -> utxoSum
                else -> spendable
            }.let { bal -> (bal - reserve).coerceAtLeast(0L) }
        val inputAddresses =
            utxos.map { it.address }.ifEmpty {
                // Unknown composition: oversize as several P2WPKH inputs.
                List(3) { "bc1qplaceholder000000000000000000000000000" }
            }
        val fee =
            BitcoinUtils.estimateOnchainSendFeeSats(
                satPerVb = feeRate,
                inputAddresses = inputAddresses,
                outputAddresses = listOf(recipientAddress),
                includeChange = false,
            )
        val amount = available - fee
        require(amount > 0L) { "Insufficient funds for fee at ${formatFeeRateForError(feeRate)} sat/vB" }
        return amount
    }

    /** LND anchor reserve that SendOutputs refuses to spend below. */
    private fun fetchRequiredReserveSats(): Long {
        val json =
            runCatching { getJson("/v2/wallet/requiredreserve") }
                .recoverCatching { getJson("/v2/wallet/requiredreserve?additional_public_channels=0") }
                .getOrNull()
                ?: return 0L
        return readLndAmount(json, "required_reserve").coerceAtLeast(0L)
    }

    private fun listOnchainUtxosBlocking(): List<github.aeonbtc.ibiswallet.data.model.UtxoInfo> {
        val response =
            runCatching { getJson("/v1/utxos?min_confs=0&max_confs=9999999") }
                .recoverCatching { getJson("/v2/wallet/utxos?min_confs=0&max_confs=9999999") }
                .getOrThrow()
        val utxos = response.optJSONArray("utxos") ?: JSONArray()
        return buildList {
            for (i in 0 until utxos.length()) {
                val u = utxos.optJSONObject(i) ?: continue
                if (isLndUtxoUnavailable(u)) continue
                val amount = readLndAmount(u, "amount_sat").coerceAtLeast(0L).toULong()
                val address = u.optString("address").orEmpty()
                val conf =
                    u.optString("confirmations").toLongOrNull()
                        ?: u.optLong("confirmations", 0L)
                val out = u.optJSONObject("outpoint")
                val txid =
                    out?.optString("txid_str")?.ifBlank { null }
                        ?: out?.optString("txid")?.ifBlank { null }
                        ?: ""
                val vout =
                    (
                        out?.optString("output_index")?.toIntOrNull()
                            ?: out?.optInt("output_index", 0)
                            ?: 0
                    ).coerceAtLeast(0).toUInt()
                if (txid.isBlank() || address.isBlank()) continue
                add(
                    github.aeonbtc.ibiswallet.data.model.UtxoInfo(
                        outpoint = "$txid:$vout",
                        txid = txid,
                        vout = vout,
                        address = address,
                        amountSats = amount,
                        isConfirmed = conf > 0,
                        isFrozen = false,
                    ),
                )
            }
        }
    }

    /** Synchronous helper — only called from already-IO backend methods. */
    private fun getOnchainBalanceDetailsBlocking(): github.aeonbtc.ibiswallet.data.model.LightningNodeOnchainBalanceDetails {
        val utxos =
            runCatching { getJson("/v1/utxos?min_confs=0&max_confs=9999999") }
                .recoverCatching { getJson("/v2/wallet/utxos?min_confs=0&max_confs=9999999") }
                .getOrNull()
                ?.optJSONArray("utxos")
        if (utxos != null) {
            var available = 0L
            var locked = 0L
            for (i in 0 until utxos.length()) {
                val utxo = utxos.optJSONObject(i) ?: continue
                val amount = readLndAmount(utxo, "amount_sat").coerceAtLeast(0L)
                if (isLndUtxoUnavailable(utxo)) {
                    locked += amount
                } else {
                    available += amount
                }
            }
            val pendingOut = pendingLndOutgoingAmount()
            val spendable = (available - pendingOut).coerceAtLeast(0L)
            return github.aeonbtc.ibiswallet.data.model.LightningNodeOnchainBalanceDetails(
                spendableSats = spendable,
                reservedSats = locked + (available - spendable).coerceAtLeast(0L),
            )
        }
        val balance = getJson("/v1/balance/blockchain")
        val confirmed = readLndAmount(balance, "confirmed_balance").coerceAtLeast(0L)
        val pendingOut = pendingLndOutgoingAmount()
        val spendable = (confirmed - pendingOut).coerceAtLeast(0L)
        return github.aeonbtc.ibiswallet.data.model.LightningNodeOnchainBalanceDetails(
            spendableSats = spendable,
            reservedSats = (confirmed - spendable).coerceAtLeast(0L),
        )
    }

    private fun needsFractionalFee(satPerVbyte: Double?): Boolean {
        if (satPerVbyte == null || satPerVbyte <= 0.0) return false
        val whole = satPerVbyte.toLong().toDouble()
        return kotlin.math.abs(satPerVbyte - whole) > 1e-9
    }

    private fun putIntegerSatPerVbyte(
        body: JSONObject,
        satPerVbyte: Double?,
    ) {
        if (satPerVbyte == null || satPerVbyte <= 0.0) return
        // Whole sat/vB only on SendCoins/SendMany (proto uint64).
        body.put("sat_per_vbyte", satPerVbyte.roundToLong().coerceAtLeast(1L).toString())
    }

    private fun putOutpoints(
        body: JSONObject,
        selectedOutpoints: List<github.aeonbtc.ibiswallet.data.model.UtxoInfo>?,
    ) {
        if (selectedOutpoints.isNullOrEmpty()) return
        val arr = JSONArray()
        selectedOutpoints.forEach { utxo ->
            arr.put(
                JSONObject()
                    .put("txid_str", utxo.txid)
                    .put("output_index", utxo.vout.toInt()),
            )
        }
        body.put("outpoints", arr)
    }

    private fun formatFeeRateForError(rate: Double): String {
        val formatted = String.format(java.util.Locale.US, "%.4f", rate)
        return formatted.trimEnd('0').trimEnd('.')
    }

    override suspend fun createInvoice(
        amountSats: Long?,
        description: String?,
    ): LightningNodeInvoice =
        withContext(Dispatchers.IO) {
            val body =
                JSONObject().apply {
                    if (amountSats != null && amountSats > 0) {
                        put("value", amountSats.toString())
                    }
                    // Memo only when user explicitly set one — never default.
                    description?.trim()?.takeIf { it.isNotBlank() }?.let { put("memo", it) }
                    put("expiry", "3600")
                }
            val response = postJson("/v1/invoices", body)
            val paymentRequest = response.optString("payment_request")
            require(paymentRequest.isNotBlank()) { "Node did not return a BOLT11 invoice" }
            val rHash = response.optString("r_hash")
            LightningNodeInvoice(
                paymentRequest = paymentRequest,
                paymentHash = decodePossibleBase64Hash(rHash) ?: rHash.ifBlank { null },
                amountSats = amountSats,
                description = description,
                expirySeconds = 3600,
            )
        }

    override suspend fun decodeInvoice(bolt11: String): DecodedLightningNodeInvoice =
        withContext(Dispatchers.IO) {
            val request = bolt11.trim().removePrefix("lightning:").trim()
            val response = getJson("/v1/payreq/${urlEncodePath(request)}")
            val amount = response.optString("num_satoshis", "0").toLongOrNull()?.takeIf { it > 0 }
            DecodedLightningNodeInvoice(
                paymentRequest = request,
                paymentHash = response.optString("payment_hash").ifBlank { null },
                amountSats = amount,
                description = response.optString("description").ifBlank { null },
                destination = response.optString("destination").ifBlank { null },
                expirySeconds = response.optString("expiry").toLongOrNull(),
            )
        }

    override suspend fun payInvoice(
        bolt11: String,
        amountSats: Long?,
        maxFeePercent: Double?,
    ): LightningNodePaymentResult =
        withContext(Dispatchers.IO) {
            val request = bolt11.trim().removePrefix("lightning:").trim()
            // The repository passes amount only for zero-amount invoices.
            val payAmountSats = amountSats?.takeIf { it > 0 }
            // Fee limits need the payment amount as a base. For fixed-amount
            // invoices the amount isn't passed in, so decode it from the node.
            val routingAmountSats =
                payAmountSats
                    ?: runCatching { decodeInvoice(request).amountSats }.getOrNull()
                    ?: 0L
            val feeLimitSat = routerFeeLimitSats(routingAmountSats, maxFeePercent)
            // LND classic REST: SendPaymentSync → /v1/channels/transactions
            // Some proxies / litd reverse-proxies omit that route; fall back to Router.
            val classicBody =
                JSONObject().apply {
                    put("payment_request", request)
                    if (payAmountSats != null) {
                        put("amt", payAmountSats.toString())
                    }
                    put("timeout_seconds", 60)
                    // SendPaymentSync defaults to a fee limit of 100% of the payment
                    // amount when unset — enforce the same capped limit as the router.
                    put("fee_limit", JSONObject().put("fixed", feeLimitSat.toString()))
                }
            val classic =
                runCatching { postJson("/v1/channels/transactions", classicBody) }
                    .getOrElse { classicError ->
                        if (!isNotFoundError(classicError)) throw classicError
                        // Router SendPayment v2 REST: POST /v2/router/send
                        val routerBody =
                            JSONObject().apply {
                                put("payment_request", request)
                                if (payAmountSats != null) {
                                    put("amt", payAmountSats.toString())
                                    put("amt_msat", (payAmountSats * 1000L).toString())
                                }
                                put("timeout_seconds", 60)
                                // Router defaults to a zero-fee budget, which only permits free
                                // routes. Match normal wallet behavior with a 5% capped fee limit.
                                put("fee_limit_sat", feeLimitSat.toString())
                                // Router is streaming. Request only the final update so we do not
                                // mistake its initial IN_FLIGHT/FAILURE_REASON_NONE event for a failure.
                                put("no_inflight_updates", true)
                                // Allow self-payments / local invoice loops for testing.
                                put("allow_self_payment", true)
                            }
                        postJson("/v2/router/send", routerBody)
                    }
            if (classic.has("payment_error") && classic.optString("payment_error").isNotBlank()) {
                throw IllegalStateException(classic.optString("payment_error"))
            }
            // Classic response vs router result payload.
            val result = classic.optJSONObject("result") ?: classic
            if (result.has("failure") && !result.isNull("failure")) {
                val failure = result.optJSONObject("failure")
                val code = failure?.optString("code").orEmpty()
                val failMsg =
                    failure?.optString("failure_source_index").orEmpty().ifBlank {
                        failure?.toString().orEmpty()
                    }
                throw IllegalStateException(
                    listOf(code, failMsg).filter { it.isNotBlank() }.joinToString(": ")
                        .ifBlank { "Payment failed" },
                )
            }
            val failureReason = result.optString("failure_reason").uppercase()
            val hasFailureReason =
                failureReason.isNotBlank() &&
                    failureReason != "NONE" &&
                    failureReason != "FAILURE_REASON_NONE" &&
                    failureReason != "FAILURE_REASON_NONE_" // defensive
            if (hasFailureReason) {
                throw IllegalStateException(failureReason)
            }
            val status = result.optString("status").uppercase()
            when (status) {
                "FAILED" -> throw IllegalStateException("Payment failed")
                "IN_FLIGHT", "INFLIGHT" ->
                    throw IllegalStateException("Payment still in progress")
                // SUCCEEDED or blank (classic SendPaymentSync) — resolved via preimage below.
                else -> Unit
            }
            val hash =
                result.optString("payment_hash").ifBlank {
                    classic.optString("payment_hash")
                }.ifBlank { null }
            val fee =
                result.optJSONObject("payment_route")
                    ?.optString("total_fees", "0")
                    ?.toLongOrNull()
                    ?: result.optString("fee_sat", "0").toLongOrNull()
                    ?: result.optString("fee", "0").toLongOrNull()
                    ?: (result.optString("fee_msat", "0").toLongOrNull()?.div(1000L))
                    ?: 0L
            val preimage =
                result.optString("payment_preimage").ifBlank {
                    result.optString("preimage")
                }.ifBlank {
                    classic.optString("payment_preimage")
                }.ifBlank { null }
                    // Proof of success: never treat empty preimage as paid (IN_FLIGHT edge case).
                    ?: throw IllegalStateException(
                        when {
                            status == "SUCCEEDED" -> "Payment missing preimage"
                            status.isBlank() -> "Payment did not return a preimage"
                            else -> "Payment not completed ($status)"
                        },
                    )
            // Reject all-zero preimage placeholders some stacks return on failure paths.
            if (preimage.matches(Regex("^0+$")) || preimage.matches(Regex("^(00)+$"))) {
                throw IllegalStateException("Payment missing preimage")
            }
            LightningNodePaymentResult(
                paymentId = hash,
                paymentHash = hash,
                feeSats = fee,
                preimage = preimage,
            )
        }

    private fun isNotFoundError(error: Throwable): Boolean {
        val message = error.message.orEmpty().lowercase()
        return message.contains("http 404") ||
            message.contains("not found") ||
            message.contains("unimplemented")
    }

    private fun routerFeeLimitSats(
        amountSats: Long,
        maxFeePercent: Double?,
    ): Long {
        val percent = (maxFeePercent ?: 5.0).coerceIn(0.0, 100.0)
        return kotlin.math.ceil(amountSats.coerceAtLeast(1L) * percent / 100.0)
            .toLong()
            .coerceIn(0L, 100_000L)
    }

    override suspend fun disconnect() {
        closeClientQuietly()
        baseUrl = ""
        macaroonHex = ""
    }

    override suspend fun ping(): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                getJson("/v1/getinfo")
                true
            }.getOrDefault(false)
        }

    private fun getInfo(): LightningNodeInfo {
        val json = getJson("/v1/getinfo")
        return LightningNodeInfo(
            alias = json.optString("alias").ifBlank { null },
            pubkey = json.optString("identity_pubkey").ifBlank { null },
            version = json.optString("version").ifBlank { null },
            network =
                when {
                    json.optBoolean("testnet") -> "testnet"
                    json.optJSONArray("chains")?.let { arr ->
                        (0 until arr.length()).any {
                            arr.optJSONObject(it)?.optString("network") == "testnet"
                        }
                    } == true -> "testnet"
                    else -> "mainnet"
                },
            numActiveChannels = json.optInt("num_active_channels", 0),
            syncedToChain = json.optBoolean("synced_to_chain", true),
        )
    }

    private fun getJson(path: String): JSONObject = executeJson(path, methodGet = true, body = null)

    private fun postJson(
        path: String,
        body: JSONObject,
    ): JSONObject = executeJson(path, methodGet = false, body = body)

    private fun executeJson(
        path: String,
        methodGet: Boolean,
        body: JSONObject?,
    ): JSONObject {
        var lastError: Exception? = null
        repeat(STREAM_RETRY_ATTEMPTS) { attempt ->
            try {
                val http = client ?: throw IllegalStateException("Not connected")
                val builder =
                    Request.Builder()
                        .url("$baseUrl$path")
                        .header("Grpc-Metadata-macaroon", macaroonHex)
                val request =
                    if (methodGet) {
                        builder.get().build()
                    } else {
                        builder
                            .post((body ?: JSONObject()).toString().toRequestBody(JSON_MEDIA))
                            .header("Content-Type", "application/json")
                            .build()
                    }
                http.newCall(request).execute().use { response ->
                    val responseBody = response.body?.stringWithLimit(InputLimits.LARGE_JSON_BYTES).orEmpty()
                    if (!response.isSuccessful) {
                        throw IllegalStateException(extractError(responseBody, response.code, path))
                    }
                    return JSONObject(responseBody.ifBlank { "{}" })
                }
            } catch (e: Exception) {
                lastError = e
                if (!isTransientStreamError(e) || attempt == STREAM_RETRY_ATTEMPTS - 1) {
                    throw e
                }
                // Drop pooled SOCKS/TLS sockets and rebuild from last known config.
                val cfg = activeConfig
                if (cfg != null) {
                    openSession(cfg)
                }
            }
        }
        throw lastError ?: IllegalStateException("Request failed: $path")
    }

    private fun buildHttpClient(
        config: LightningNodeConfig,
        probeTimeouts: Boolean = false,
    ): OkHttpClient {
        val viaTor = config.useTor || config.host.endsWith(".onion", ignoreCase = true)
        // Cleartext probe before HTTPS fallback must fail fast on LAN HTTPS-only nodes
        // (otherwise users wait 30–90s × retries staring at a working HTTPS endpoint).
        val timeoutSeconds =
            when {
                probeTimeouts && viaTor -> CLEARTEXT_PROBE_TOR_SECONDS
                probeTimeouts -> CLEARTEXT_PROBE_SECONDS
                // Initial RPCs over Tor circs: stay responsive (was 90s × retries = multi-minute).
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
            // HTTP/1.1 only — HTTP/2 multiplexing over Tor SOCKS flakes badly.
            // Small keep-alive pool so parallel sync RPCs can reuse circuits instead of
            // opening a brand-new dial for every getinfo/payments/tx call.
            builder.protocols(listOf(Protocol.HTTP_1_1))
            builder.connectionPool(ConnectionPool(2, 45, TimeUnit.SECONDS))
        }

        if (config.tlsEnabled) {
            when {
                // Pin when the user supplied a cert (typical Umbrel/LND tls.cert paste).
                config.tlsCertPem.isNotBlank() ->
                    TlsCertMaterial.applyToOkHttp(builder, config.tlsCertPem)
                // No cert: user is connecting to their own node. Self-signed LND is
                // common; trust-all here. Prefer pasting tls.cert when available.
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
        // Force hostname resolution through SOCKS so Tor handles .onion / remote DNS.
        // Without this, Java tries local DNS first and .onion hosts fail.
        builder.dns { hostname ->
            listOf(InetAddress.getByAddress(hostname, byteArrayOf(0, 0, 0, 0)))
        }
    }

    companion object {
        private const val STREAM_RETRY_ATTEMPTS = 3
        /** Clearnet getinfo stream flakiness only — keep low so LAN does not stall. */
        private const val CONNECT_CLEAR_STREAM_ATTEMPTS = 2
        private const val CLEARTEXT_PROBE_SECONDS = 3L
        private const val CLEARTEXT_PROBE_TOR_SECONDS = 6L
        private const val TOR_RPC_SECONDS = 45L
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

        fun normalizeMacaroon(raw: String): String {
            val trimmed = raw.trim().replace("\\s".toRegex(), "")
            if (trimmed.matches(Regex("^[0-9a-fA-F]+$")) && trimmed.length % 2 == 0) {
                return trimmed.lowercase()
            }
            val decoded =
                runCatching {
                    java.util.Base64.getDecoder().decode(trimmed)
                }.recoverCatching {
                    java.util.Base64.getUrlDecoder().decode(trimmed)
                }.getOrElse {
                    // Fallback for Android-origin decoders in system context.
                    runCatching {
                        Base64.decode(trimmed, Base64.DEFAULT)
                    }.getOrElse {
                        Base64.decode(trimmed, Base64.URL_SAFE or Base64.NO_WRAP)
                    }
                }
            return decoded.joinToString("") { b -> "%02x".format(b) }
        }

        private fun mapPaymentStatus(status: String): LightningNodePaymentStatus =
            when (status.uppercase()) {
                "SUCCEEDED", "COMPLETE" -> LightningNodePaymentStatus.SUCCEEDED
                "FAILED" -> LightningNodePaymentStatus.FAILED
                "IN_FLIGHT", "INFLIGHT" -> LightningNodePaymentStatus.PENDING
                else -> LightningNodePaymentStatus.UNKNOWN
            }

        private fun extractLndPaymentFailureReason(payment: JSONObject): String? {
            val reason = payment.optString("failure_reason").trim()
            if (
                reason.isNotBlank() &&
                !reason.equals("NONE", ignoreCase = true) &&
                !reason.equals("FAILURE_REASON_NONE", ignoreCase = true)
            ) {
                return humanizeLndFailureReason(reason)
            }
            payment.optString("payment_error").ifBlank { null }?.let { return it }
            val htlcs = payment.optJSONArray("htlcs") ?: return null
            for (i in htlcs.length() - 1 downTo 0) {
                val htlc = htlcs.optJSONObject(i) ?: continue
                val fail = htlc.optJSONObject("failure") ?: continue
                val code = fail.optString("code").ifBlank { fail.optString("FailureCode") }
                val msg = fail.optString("message").ifBlank { fail.optString("failure_details") }
                val combined =
                    listOf(code, msg).filter { it.isNotBlank() }.joinToString(": ")
                if (combined.isNotBlank()) return combined
            }
            return null
        }

        private fun humanizeLndFailureReason(raw: String): String {
            val key =
                raw.uppercase()
                    .removePrefix("FAILURE_REASON_")
                    .replace('_', ' ')
                    .trim()
            return when (key) {
                "TIMEOUT" -> "Timed out"
                "NO ROUTE", "NOROUTE" -> "No route"
                "ERROR" -> "Payment error"
                "INCORRECT PAYMENT DETAILS", "INCORRECT_PAYMENT_DETAILS" ->
                    "Incorrect payment details"
                "INSUFFICIENT BALANCE", "INSUFFICIENT_BALANCE" ->
                    "Insufficient balance"
                else -> key.lowercase().replaceFirstChar { it.titlecase(java.util.Locale.US) }
            }
        }

        private fun extractError(
            body: String,
            code: Int,
            path: String = "",
        ): String {
            val message =
                runCatching {
                    JSONObject(body).optString("message").ifBlank {
                        JSONObject(body).optString("error")
                    }
                }.getOrNull()
                    ?.ifBlank { null }
            val detail = message ?: body.take(180).ifBlank { null }
            return buildString {
                append("HTTP $code")
                if (path.isNotBlank()) append(" $path")
                if (detail != null) append(": $detail")
            }
        }

        private fun urlEncodePath(value: String): String =
            java.net.URLEncoder.encode(value, Charsets.UTF_8.name())

        private fun decodePossibleBase64Hash(value: String): String? {
            if (value.isBlank()) return null
            if (value.matches(Regex("^[0-9a-fA-F]{64}$"))) return value.lowercase()
            return runCatching {
                val bytes = Base64.decode(value, Base64.DEFAULT)
                bytes.joinToString("") { "%02x".format(it) }
            }.getOrNull()
        }

        private fun extractLndPaymentDestination(payment: JSONObject): String? {
            payment.optString("destination").ifBlank { null }?.let { return it }
            payment.optString("destination_pubkey").ifBlank { null }?.let { return it }
            val htlcs = payment.optJSONArray("htlcs") ?: return null
            for (i in 0 until htlcs.length()) {
                val htlc = htlcs.optJSONObject(i) ?: continue
                val route = htlc.optJSONObject("route") ?: continue
                val hops = route.optJSONArray("hops") ?: continue
                if (hops.length() == 0) continue
                val last = hops.optJSONObject(hops.length() - 1) ?: continue
                last.optString("pub_key").ifBlank { last.optString("pubkey") }
                    .ifBlank { null }?.let { return it }
            }
            return null
        }

        /**
         * Format pending channel open progress as `AWAITING LOCKIN 0/2`.
         * Prefer explicit conf counts when present; otherwise derive from
         * confirmation height + tip and remaining confs-until-active.
         */
        private fun formatPendingOpenState(
            stateLabel: String,
            confsUntilActive: Int,
            confirmationHeight: Int,
            fundingConfs: Int,
            tipHeight: Int,
        ): String {
            val remaining = confsUntilActive.coerceAtLeast(0)
            val current =
                when {
                    fundingConfs > 0 -> fundingConfs
                    confirmationHeight > 0 && tipHeight >= confirmationHeight ->
                        (tipHeight - confirmationHeight + 1).coerceAtLeast(0)
                    remaining > 0 -> 0
                    else -> return stateLabel
                }
            val total =
                when {
                    remaining > 0 -> (current + remaining).coerceAtLeast(current)
                    current > 0 -> current
                    else -> return stateLabel
                }
            val cappedCurrent = current.coerceAtMost(total)
            return "$stateLabel $cappedCurrent/$total"
        }
    }
}

data class LndTxOutput(
    val address: String,
    val amountSats: Long,
    val isOurs: Boolean,
)

data class LndResolvedTxAddresses(
    val address: String?,
    val addressAmountSats: Long?,
    val changeAddress: String?,
    val changeAmountSats: Long?,
)

/**
 * Map LND [Transaction.output_details] into detail-dialog fields.
 * Incoming txs must use our receive output(s), never an external change UTXO
 * belonging to the sender (common when dest_addresses lists both outs).
 */
fun resolveLndTxAddresses(
    amountSats: Long,
    outputs: List<LndTxOutput>,
    destAddressesFallback: String? = null,
): LndResolvedTxAddresses {
    var recipientAddress: String? = null
    var recipientAmount: Long? = null
    var ourAddress: String? = null
    var ourAmountSats = 0L
    for (output in outputs) {
        if (output.isOurs) {
            if (ourAddress == null) {
                ourAddress = output.address
            }
            ourAmountSats += output.amountSats.coerceAtLeast(0L)
        } else if (recipientAddress == null) {
            recipientAddress = output.address
            recipientAmount = output.amountSats.takeIf { it > 0L }
        }
    }
    val isReceived = amountSats >= 0L
    val address =
        if (isReceived) {
            ourAddress
        } else {
            recipientAddress ?: destAddressesFallback?.ifBlank { null }
        }
    val addressAmountSats =
        if (isReceived) {
            ourAmountSats.takeIf { it > 0L } ?: amountSats.takeIf { it > 0L }
        } else {
            recipientAmount ?: kotlin.math.abs(amountSats).takeIf { address != null }
        }
    val changeAddress = ourAddress?.takeIf { !isReceived && it.isNotBlank() }
    val changeAmountSats = ourAmountSats.takeIf { changeAddress != null && it > 0L }
    return LndResolvedTxAddresses(
        address = address,
        addressAmountSats = addressAmountSats,
        changeAddress = changeAddress,
        changeAmountSats = changeAmountSats,
    )
}
