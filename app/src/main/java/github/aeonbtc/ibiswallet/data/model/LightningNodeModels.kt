package github.aeonbtc.ibiswallet.data.model

/** Connection backend for a Lightning Node Layer 2 wallet. */
enum class LightningNodeConnectionType {
    NONE,
    /** LND HTTP REST (`/v1/...` + macaroon). Stored as LND_REST; legacy LND_GRPC still loads. */
    LND_REST,
    NWC,
    /** Core Lightning clnrest (HTTP/HTTPS + Rune). */
    CLN_REST,
}

/** Parse persisted / backup type names, including legacy LND_GRPC. */
fun parseLightningNodeConnectionType(raw: String?): LightningNodeConnectionType =
    when (raw?.trim()?.uppercase()) {
        null, "", "NONE" -> LightningNodeConnectionType.NONE
        "LND_REST", "LND_GRPC", "LND" -> LightningNodeConnectionType.LND_REST
        "NWC" -> LightningNodeConnectionType.NWC
        "CLN_REST", "CLN" -> LightningNodeConnectionType.CLN_REST
        else ->
            runCatching { LightningNodeConnectionType.valueOf(raw.trim().uppercase()) }
                .getOrDefault(LightningNodeConnectionType.NONE)
    }

enum class LightningNodePaymentDirection {
    INCOMING,
    OUTGOING,
}

enum class LightningNodePaymentStatus {
    PENDING,
    SUCCEEDED,
    FAILED,
    UNKNOWN,
}

data class LightningNodeConfig(
    val type: LightningNodeConnectionType = LightningNodeConnectionType.NONE,
    val host: String = "",
    val port: Int = DEFAULT_LND_REST_PORT,
    val useTor: Boolean = false,
    val macaroonHex: String = "",
    val tlsCertPem: String = "",
    /**
     * When true, connect over HTTPS and optionally pin [tlsCertPem].
     * When false, prefer cleartext HTTP (may still auto-try HTTPS for the session).
     * [allowInsecureTls] is retained for backup compatibility; prefer [useTls].
     */
    val useTls: Boolean = false,
    /** Legacy flag: true means HTTP/cleartext (TLS disabled). */
    val allowInsecureTls: Boolean = true,
    /**
     * Last transport that successfully opened getinfo for this host/port.
     * Used only to skip a slow cleartext probe when the node is HTTPS-only.
     * Does not affect the TLS toggle / [useTls] preference shown in UI.
     */
    val preferSessionTls: Boolean = false,
    val nwcUri: String = "",
    /** Core Lightning clnrest authorization rune (createrune / showrunes). */
    val clnRune: String = "",
) {
    val isConfigured: Boolean
        get() =
            when (type) {
                LightningNodeConnectionType.NONE -> false
                LightningNodeConnectionType.LND_REST ->
                    host.isNotBlank() && port in 1..65535 && macaroonHex.isNotBlank()
                LightningNodeConnectionType.NWC -> nwcUri.isNotBlank()
                LightningNodeConnectionType.CLN_REST ->
                    host.isNotBlank() && port in 1..65535 && clnRune.isNotBlank()
            }

    /**
     * Effective TLS follows the user's [useTls] preference.
     * A pasted cert is only a pin when TLS is on — it must not force TLS=
     * true when the user explicitly chose plain HTTP.
     */
    val tlsEnabled: Boolean
        get() = useTls

    /** Short type label for wallet lists (no secrets). Protocol tokens stay English. */
    fun listTypeLabel(lightningFallback: String = "Lightning"): String =
        when (type) {
            LightningNodeConnectionType.LND_REST -> "LND"
            LightningNodeConnectionType.NWC -> "NWC"
            LightningNodeConnectionType.CLN_REST -> "CLN"
            LightningNodeConnectionType.NONE -> lightningFallback
        }

    /**
     * Non-secret connection detail for wallet lists (labeled like seed meta lines).
     * LND/CLN → Server: host (port is [listPortLine])
     * NWC → Relay: first relay (+N)
     * Long hostnames (especially .onion) are shortened to fit compact cards.
     *
     * Pass localized [copy] from UI so labels follow the active locale.
     */
    fun listDetailLine(copy: LightningNodeListCopy = LightningNodeListCopy.English): String =
        when (type) {
            LightningNodeConnectionType.LND_REST,
            LightningNodeConnectionType.CLN_REST,
            -> {
                val shortHost = shortenHostForList(host)
                if (shortHost.isNullOrBlank()) {
                    copy.serverNotConfigured
                } else {
                    String.format(copy.serverFormat, shortHost)
                }
            }
            LightningNodeConnectionType.NWC -> {
                val relays =
                    runCatching {
                        github.aeonbtc.ibiswallet.data.lightning.NwcUriParser
                            .parse(nwcUri)
                            .relays
                    }.getOrDefault(emptyList())
                val hosts =
                    relays
                        .map {
                            it
                                .removePrefix("wss://")
                                .removePrefix("ws://")
                                .substringBefore('/')
                                .substringBefore('?')
                        }.filter { it.isNotBlank() }
                        .map { shortenHostForList(it) ?: it }
                        .distinct()
                val relayLabel =
                    when {
                        hosts.isEmpty() ->
                            if (nwcUri.isNotBlank()) {
                                copy.configured
                            } else {
                                copy.notConfigured
                            }
                        hosts.size == 1 -> hosts.first()
                        else -> "${hosts.first()} +${hosts.size - 1}"
                    }
                String.format(copy.relayFormat, relayLabel)
            }
            LightningNodeConnectionType.NONE -> copy.serverNotConfigured
        }

    /** LND/CLN: Port: N on its own list line. */
    fun listPortLine(copy: LightningNodeListCopy = LightningNodeListCopy.English): String? =
        when (type) {
            LightningNodeConnectionType.LND_REST,
            LightningNodeConnectionType.CLN_REST,
            ->
                if (host.isNotBlank() && port in 1..65535) {
                    String.format(copy.portFormat, port)
                } else {
                    null
                }
            else -> null
        }

    /** Shorten long hosts (v3 onion ≈56+ chars) for compact wallet cards. */
    private fun shortenHostForList(raw: String?): String? {
        val host = raw?.trim()?.ifBlank { null } ?: return null
        val isOnion = host.endsWith(".onion", ignoreCase = true)
        val maxLen = if (isOnion) 22 else 36
        if (host.length <= maxLen) return host
        return if (isOnion) {
            // abcdef12…xyz.onion
            val withoutSuffix = host.removeSuffix(".onion").removeSuffix(".ONION")
            val head = withoutSuffix.take(8)
            val tail = withoutSuffix.takeLast(4)
            "$head…$tail.onion"
        } else {
            "${host.take(14)}…${host.takeLast(10)}"
        }
    }

    /**
     * Extra list meta (like fingerprint on seed wallets). No secrets.
     * NWC → Pubkey: truncated key (+ Address: lud16 if present).
     * LND/CLN → Mode: HTTP / SSL / SSL pin (separate from Server).
     */
    fun listMetaLine(copy: LightningNodeListCopy = LightningNodeListCopy.English): String? =
        when (type) {
            LightningNodeConnectionType.NWC -> {
                val parsed =
                    runCatching {
                        github.aeonbtc.ibiswallet.data.lightning.NwcUriParser.parse(nwcUri)
                    }.getOrNull() ?: return null
                val pk = parsed.walletPubkey
                val shortPk =
                    if (pk.length >= 16) {
                        "${pk.take(8)}…${pk.takeLast(8)}"
                    } else {
                        pk
                    }
                val parts =
                    buildList {
                        add(String.format(copy.pubkeyFormat, shortPk))
                        parsed.lud16?.takeIf { it.isNotBlank() }?.let {
                            add(String.format(copy.addressFormat, it))
                        }
                    }
                parts.joinToString(" · ").ifBlank { null }
            }
            LightningNodeConnectionType.LND_REST,
            LightningNodeConnectionType.CLN_REST,
            ->
                when {
                    tlsCertPem.isNotBlank() -> copy.modeSslPin
                    tlsEnabled -> copy.modeSsl
                    host.isNotBlank() -> copy.modeHttp
                    else -> null
                }
            LightningNodeConnectionType.NONE -> null
        }

    companion object {
        /** Default LND REST port (native gRPC is often 10009 and is not used by this app). */
        const val DEFAULT_LND_REST_PORT = 8080
        const val DEFAULT_CLN_REST_PORT = 3010
    }
}

/** Localized snippets for Lightning Node wallet list rows. Protocol tokens (HTTP/SSL/Pubkey) stay English. */
data class LightningNodeListCopy(
    val serverFormat: String,
    val serverNotConfigured: String,
    val portFormat: String,
    val relayFormat: String,
    val configured: String,
    val notConfigured: String,
    val modeHttp: String,
    val modeSsl: String,
    val modeSslPin: String,
    val pubkeyFormat: String,
    val addressFormat: String,
) {
    companion object {
        val English =
            LightningNodeListCopy(
                serverFormat = "Server: %1\$s",
                serverNotConfigured = "Server: Not configured",
                portFormat = "Port: %1\$d",
                relayFormat = "Relay: %1\$s",
                configured = "Configured",
                notConfigured = "Not configured",
                modeHttp = "Mode: HTTP",
                modeSsl = "Mode: SSL",
                modeSslPin = "Mode: SSL pin",
                pubkeyFormat = "Pubkey: %1\$s",
                addressFormat = "Address: %1\$s",
            )
    }
}

data class LightningNodeInfo(
    val alias: String? = null,
    val pubkey: String? = null,
    val version: String? = null,
    val network: String? = null,
    val numActiveChannels: Int? = null,
    val syncedToChain: Boolean? = null,
)

data class LightningNodeBalance(
    val localBalanceSats: Long = 0,
    val remoteBalanceSats: Long = 0,
) {
    val totalSats: Long get() = localBalanceSats
}

/** Open / pending channel on a remote Lightning node (LND or CLN). */
data class LightningNodeChannel(
    val id: String,
    val remotePubkey: String,
    val remoteAlias: String? = null,
    val shortChannelId: String? = null,
    /** Funding outpoint txid when known (channel open / pending open). */
    val fundingTxid: String? = null,
    val capacitySats: Long = 0,
    val localBalanceSats: Long = 0,
    val remoteBalanceSats: Long = 0,
    val isActive: Boolean = false,
    val isPrivate: Boolean = false,
    val state: String? = null,
)

data class LightningNodeInvoice(
    val paymentRequest: String,
    val paymentHash: String? = null,
    val amountSats: Long? = null,
    val description: String? = null,
    val expirySeconds: Long? = null,
)

data class DecodedLightningNodeInvoice(
    val paymentRequest: String,
    val paymentHash: String? = null,
    val amountSats: Long? = null,
    val description: String? = null,
    val destination: String? = null,
    val expirySeconds: Long? = null,
)

data class LightningNodePayment(
    val id: String,
    val direction: LightningNodePaymentDirection,
    val status: LightningNodePaymentStatus,
    val amountSats: Long,
    val feeSats: Long = 0,
    val timestamp: Long = 0,
    val paymentHash: String? = null,
    /** BOLT11 / BOLT12 invoice or offer / LN address string when known. */
    val paymentRequest: String? = null,
    val memo: String? = null,
    /** Counterparty node pubkey when known. */
    val destination: String? = null,
    /** Counterparty node alias/name when known. */
    val destinationAlias: String? = null,
    /** Human-readable failure detail when [status] is FAILED (send path / history). */
    val failureReason: String? = null,
)

data class LightningNodePaymentResult(
    val paymentId: String?,
    val paymentHash: String?,
    val feeSats: Long = 0,
    val preimage: String? = null,
)

data class LightningNodeOnchainTransaction(
    val txid: String,
    val amountSats: Long,
    val feeSats: Long = 0,
    val vsize: Double? = null,
    val timestamp: Long = 0,
    val confirmations: Int = 0,
    val address: String? = null,
    val addressAmountSats: Long? = null,
    val changeAddress: String? = null,
    val changeAmountSats: Long? = null,
    /** True when this on-chain tx funds a channel open (pending or established). */
    val isChannelOpen: Boolean = false,
    /** True when this on-chain tx closes a channel (cooperative or force). */
    val isChannelClose: Boolean = false,
)

/**
 * On-chain wallet funds split for Lightning Node L1.
 * [spendableSats] is what Send / Max use; [reservedSats] is locked by pending
 * withdraws or leases; [immatureSats] is coinbase not yet mature.
 */
data class LightningNodeOnchainBalanceDetails(
    val spendableSats: Long = 0,
    val reservedSats: Long = 0,
    val immatureSats: Long = 0,
)

data class LightningNodeOnchainState(
    val isAvailable: Boolean = false,
    /** Spendable on-chain balance (Send / Max). */
    val balanceSats: Long = 0,
    /** UTXOs reserved by in-flight spends or locked by the node. */
    val reservedSats: Long = 0,
    /** Immature coinbase outputs (not spendable yet). */
    val immatureSats: Long = 0,
    val currentAddress: String? = null,
    /** Addresses generated via NewAddress this session (Receive tab). */
    val revealedReceiveAddresses: List<String> = emptyList(),
    val transactions: List<LightningNodeOnchainTransaction> = emptyList(),
    val utxos: List<github.aeonbtc.ibiswallet.data.model.UtxoInfo> = emptyList(),
    val receiveAddresses: List<github.aeonbtc.ibiswallet.data.model.WalletAddress> = emptyList(),
    val changeAddresses: List<github.aeonbtc.ibiswallet.data.model.WalletAddress> = emptyList(),
    val usedAddresses: List<github.aeonbtc.ibiswallet.data.model.WalletAddress> = emptyList(),
    val isSyncing: Boolean = false,
    val error: String? = null,
)

data class LightningNodeWalletState(
    val walletId: String? = null,
    val isInitialized: Boolean = false,
    val isConnected: Boolean = false,
    val isConnecting: Boolean = false,
    val connectionType: LightningNodeConnectionType = LightningNodeConnectionType.NONE,
    val nodeAlias: String? = null,
    val nodePubkey: String? = null,
    val nodeVersion: String? = null,
    val nodeNetwork: String? = null,
    val numActiveChannels: Int? = null,
    val syncedToChain: Boolean? = null,
    /** Config host used for current session (no secrets). */
    val host: String? = null,
    val port: Int? = null,
    val useTor: Boolean = false,
    val useTls: Boolean = false,
    val balanceSats: Long = 0,
    val remoteBalanceSats: Long = 0,
    val payments: List<LightningNodePayment> = emptyList(),
    val isSyncing: Boolean = false,
    val lastSyncTimestamp: Long = 0,
    val error: String? = null,
) {
    /**
     * Human-readable node target for status copy: alias, host, truncated pubkey, or type label.
     */
    fun displayTarget(): String {
        nodeAlias?.trim()?.takeIf { it.isNotBlank() }?.let { return it }
        host?.trim()?.takeIf { it.isNotBlank() }?.let { rawHost ->
            val shortHost =
                if (rawHost.length > 28) {
                    if (rawHost.endsWith(".onion", ignoreCase = true)) {
                        val base = rawHost.removeSuffix(".onion").removeSuffix(".ONION")
                        "${base.take(8)}…${base.takeLast(4)}.onion"
                    } else {
                        "${rawHost.take(12)}…${rawHost.takeLast(8)}"
                    }
                } else {
                    rawHost
                }
            return if (port != null && port > 0) "$shortHost:$port" else shortHost
        }
        nodePubkey?.trim()?.takeIf { it.isNotBlank() }?.let { pk ->
            return if (pk.length > 16) "${pk.take(8)}…${pk.takeLast(6)}" else pk
        }
        return when (connectionType) {
            LightningNodeConnectionType.LND_REST -> "LND"
            LightningNodeConnectionType.CLN_REST -> "CLN"
            LightningNodeConnectionType.NWC -> "NWC"
            LightningNodeConnectionType.NONE -> "node"
        }
    }
}

sealed interface LightningNodeSendState {
    data object Idle : LightningNodeSendState

    data object Decoding : LightningNodeSendState

    data class Preview(
        val paymentRequest: String,
        val amountSats: Long?,
        /** True when the invoice itself specifies the amount; do not submit it again. */
        val isFixedAmount: Boolean,
        val description: String?,
        val destination: String?,
        val maxFeePercent: Double? = null,
        val feeSats: Long? = null,
    ) : LightningNodeSendState

    data class Paying(
        val paymentRequest: String,
        val amountSats: Long?,
        val destination: String?,
    ) : LightningNodeSendState

    data class Paid(
        val paymentId: String?,
        val paymentHash: String?,
        val amountSats: Long,
        val feeSats: Long,
    ) : LightningNodeSendState

    data class Error(val message: String) : LightningNodeSendState
}

/** Layer 1 (node on-chain) send progress for Lightning Node wallets. */
sealed interface LightningNodeOnchainSendState {
    data object Idle : LightningNodeOnchainSendState

    data object Sending : LightningNodeOnchainSendState

    data class Success(val txid: String) : LightningNodeOnchainSendState

    data class Error(val message: String) : LightningNodeOnchainSendState
}

sealed interface LightningNodeReceiveState {
    data object Idle : LightningNodeReceiveState

    data object Generating : LightningNodeReceiveState

    data class Ready(
        val paymentRequest: String,
        val amountSats: Long?,
        val description: String?,
        val paymentHash: String?,
    ) : LightningNodeReceiveState

    data class Paid(
        val paymentHash: String?,
        val amountSats: Long,
        val paymentRequest: String? = null,
        val description: String? = null,
    ) : LightningNodeReceiveState

    data class Error(val message: String) : LightningNodeReceiveState
}

sealed class LightningNodeEvent {
    data class PaymentReceived(
        val paymentId: String,
        val amountSats: Long,
        val paymentHash: String? = null,
    ) : LightningNodeEvent()
}

sealed interface LightningNodeConnectionTestResult {
    data class Success(
        val info: LightningNodeInfo,
        val balance: LightningNodeBalance,
        /** True when connect used HTTPS while the user left TLS off — save as a speed hint. */
        val preferSessionTls: Boolean = false,
    ) : LightningNodeConnectionTestResult

    data class Failure(val message: String) : LightningNodeConnectionTestResult
}

/** Progress steps while the setup screen tests node connectivity. */
enum class LightningNodeConnectionTestPhase {
    IDLE,
    PREPARING,
    STARTING_TOR,
    WAITING_FOR_TOR,
    CONNECTING,
    FETCHING_BALANCE,
}
