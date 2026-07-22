package github.aeonbtc.ibiswallet.data.lightning

import github.aeonbtc.ibiswallet.data.model.DecodedLightningNodeInvoice
import github.aeonbtc.ibiswallet.data.model.LightningNodeBalance
import github.aeonbtc.ibiswallet.data.model.LightningNodeChannel
import github.aeonbtc.ibiswallet.data.model.LightningNodeConfig
import github.aeonbtc.ibiswallet.data.model.LightningNodeInfo
import github.aeonbtc.ibiswallet.data.model.LightningNodeInvoice
import github.aeonbtc.ibiswallet.data.model.LightningNodePayment
import github.aeonbtc.ibiswallet.data.model.LightningNodePaymentResult
import github.aeonbtc.ibiswallet.data.model.LightningNodeOnchainBalanceDetails
import github.aeonbtc.ibiswallet.data.model.LightningNodeOnchainTransaction
import github.aeonbtc.ibiswallet.data.model.UtxoInfo
import kotlin.math.ceil

/**
 * Provider-agnostic Lightning node backend (LND REST, NWC, CLN clnrest).
 * LND: REST + macaroon (UI label "LND").
 * CLN: HTTP/HTTPS clnrest + Rune header.
 * NWC: NIP-47 over Nostr relays (L2 only).
 */
interface LightningNodeBackend {
    suspend fun connect(config: LightningNodeConfig): LightningNodeInfo

    /** Lightweight liveness check used by background keep-alive heartbeats. */
    suspend fun ping(): Boolean

    suspend fun getBalance(): LightningNodeBalance

    suspend fun listPayments(limit: Int = 50): List<LightningNodePayment>

    /** Open channels for the connected node. Default: empty (e.g. NWC has no channels API). */
    suspend fun listChannels(): List<LightningNodeChannel> = emptyList()

    suspend fun getOnchainBalance(): Long

    /**
     * Spendable vs reserved/immature breakdown. Default wraps [getOnchainBalance]
     * so backends without UTXO detail still work.
     */
    suspend fun getOnchainBalanceDetails(): LightningNodeOnchainBalanceDetails =
        LightningNodeOnchainBalanceDetails(spendableSats = getOnchainBalance())

    suspend fun newOnchainAddress(): String

    suspend fun listOnchainTransactions(limit: Int = 50): List<LightningNodeOnchainTransaction>

    suspend fun listOnchainUtxos(): List<UtxoInfo>

    /**
     * Chain backend minimum relay fee in sat/vB (may be fractional).
     * LND: WalletKit EstimateFee → min_relay_fee_sat_per_kw.
     * CLN: feerates perkw floor / min_acceptable.
     * Default null when unsupported (NWC) or unavailable.
     */
    suspend fun getOnchainMinRelayFeeSatPerVb(): Double? = null

    suspend fun sendOnchain(
        address: String,
        amountSats: Long? = null,
        /** sat/vB — may be fractional (e.g. 0.5); backends map to native precision. */
        satPerVbyte: Double? = null,
        sendAll: Boolean = false,
        label: String? = null,
        spendUnconfirmed: Boolean = true,
        selectedOutpoints: List<UtxoInfo>? = null,
    ): String

    /** Multi-output send via LND SendMany (`POST /v1/transactions/many`). */
    suspend fun sendOnchainMany(
        addrToAmountSats: Map<String, Long>,
        satPerVbyte: Double? = null,
        label: String? = null,
        spendUnconfirmed: Boolean = true,
        selectedOutpoints: List<UtxoInfo>? = null,
    ): String

    /**
     * WalletKit BumpFee — RBF or CPFP via LND sweeper (`POST /v2/wallet/bumpfee`).
     * Requires a wallet-controlled [outpoint] of the unconfirmed parent.
     */
    suspend fun bumpOnchainFee(
        outpoint: UtxoInfo,
        satPerVbyte: Double?,
        immediate: Boolean = true,
        budgetSats: Long? = null,
    ): String

    suspend fun createInvoice(
        amountSats: Long?,
        description: String?,
    ): LightningNodeInvoice

    suspend fun decodeInvoice(bolt11: String): DecodedLightningNodeInvoice

    suspend fun payInvoice(
        bolt11: String,
        amountSats: Long?,
        maxFeePercent: Double? = null,
    ): LightningNodePaymentResult

    /**
     * Fetch a BOLT12 invoice from an offer and return it ready for [payInvoice].
     * Default: unsupported (only CLN implements this).
     */
    suspend fun fetchBolt12Invoice(
        offer: String,
        amountSats: Long?,
    ): DecodedLightningNodeInvoice =
        throw UnsupportedOperationException("BOLT12 offers are not supported on this node type")

    suspend fun disconnect()
}

/** Returns exact virtual bytes from a serialized Bitcoin transaction, including witness data. */
internal fun transactionVsize(rawTxHex: String): Double? {
    val hex = rawTxHex.takeIf { it.length >= 20 && it.length % 2 == 0 } ?: return null
    val bytes = ByteArray(hex.length / 2)
    for (index in bytes.indices) {
        bytes[index] = hex.substring(index * 2, index * 2 + 2).toIntOrNull(16)?.toByte() ?: return null
    }
    var position = 4
    if (position >= bytes.size) return null
    val hasWitness = bytes.getOrNull(position) == 0.toByte() && bytes.getOrNull(position + 1) != 0.toByte()
    if (hasWitness) position += 2
    fun readVarInt(): Long? {
        val first = bytes.getOrNull(position++)?.toInt()?.and(0xff) ?: return null
        return when (first) {
            in 0..252 -> first.toLong()
            253 -> {
                if (position + 2 > bytes.size) return null
                val value = (bytes[position].toInt() and 0xff) or ((bytes[position + 1].toInt() and 0xff) shl 8)
                position += 2
                value.toLong()
            }
            254 -> {
                if (position + 4 > bytes.size) return null
                var value = 0L
                repeat(4) { index -> value = value or ((bytes[position + index].toLong() and 0xff) shl (index * 8)) }
                position += 4
                value
            }
            else -> {
                if (position + 8 > bytes.size) return null
                var value = 0L
                repeat(8) { index -> value = value or ((bytes[position + index].toLong() and 0xff) shl (index * 8)) }
                position += 8
                value
            }
        }
    }
    fun skip(count: Long): Boolean {
        if (count < 0 || count > bytes.size - position) return false
        position += count.toInt()
        return true
    }
    val inputCount = readVarInt() ?: return null
    repeat(inputCount.toInt()) {
        if (!skip(36)) return null
        if (!skip(readVarInt() ?: return null)) return null
        if (!skip(4)) return null
    }
    val outputCount = readVarInt() ?: return null
    repeat(outputCount.toInt()) {
        if (!skip(8)) return null
        if (!skip(readVarInt() ?: return null)) return null
    }
    val strippedSizeBeforeWitness = position
    if (hasWitness) {
        repeat(inputCount.toInt()) {
            val stackItems = readVarInt() ?: return null
            repeat(stackItems.toInt()) { if (!skip(readVarInt() ?: return null)) return null }
        }
    }
    if (!skip(4) || position != bytes.size) return null
    val strippedSize = if (hasWitness) strippedSizeBeforeWitness + 4 else bytes.size
    val weight = strippedSize * 4 + (bytes.size - strippedSize)
    return ceil(weight / 4.0)
}
