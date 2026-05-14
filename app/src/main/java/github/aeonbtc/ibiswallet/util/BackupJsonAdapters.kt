package github.aeonbtc.ibiswallet.util

import github.aeonbtc.ibiswallet.data.model.LiquidSwapDetails
import github.aeonbtc.ibiswallet.data.model.LiquidSwapTxRole
import github.aeonbtc.ibiswallet.data.model.LiquidTxSource
import github.aeonbtc.ibiswallet.data.model.SparkUnclaimedDeposit
import github.aeonbtc.ibiswallet.data.model.SwapDirection
import github.aeonbtc.ibiswallet.data.model.SwapService
import org.json.JSONArray
import org.json.JSONObject

data class SparkBackupMetadata(
    val transactionSources: Map<String, String> = emptyMap(),
    val paymentRecipients: Map<String, String> = emptyMap(),
    val depositAddresses: Map<String, String> = emptyMap(),
    val pendingDeposits: List<SparkUnclaimedDeposit> = emptyList(),
    val onchainDepositAddress: String? = null,
) {
    fun isEmpty(): Boolean =
        transactionSources.isEmpty() &&
            paymentRecipients.isEmpty() &&
            depositAddresses.isEmpty() &&
            pendingDeposits.isEmpty() &&
            onchainDepositAddress.isNullOrBlank()
}

object BackupJsonAdapters {
    object Bitcoin {
        fun metadataToJson(
            transactionSources: Map<String, String> = emptyMap(),
            swapDetails: Map<String, LiquidSwapDetails>,
        ): JSONObject =
            JSONObject().apply {
                put("transactionSources", transactionSources.toJsonObject())
                put("swapDetails", swapDetailsToJson(swapDetails))
            }

        fun transactionSourcesFromMetadata(json: JSONObject?): Map<String, String> =
            json?.optJSONObject("transactionSources").toStringMap()

        fun swapDetailsFromMetadata(json: JSONObject?): Map<String, LiquidSwapDetails> =
            swapDetailsFromJson(json?.optJSONObject("swapDetails"))
    }

    object Liquid {
        fun metadataToJson(
            transactionSources: Map<String, LiquidTxSource>,
            swapDetails: Map<String, LiquidSwapDetails>,
        ): JSONObject =
            JSONObject().apply {
                put(
                    "transactionSources",
                    JSONObject().apply {
                        transactionSources.forEach { (txid, source) -> put(txid, source.name) }
                    },
                )
                put("swapDetails", swapDetailsToJson(swapDetails))
            }

        fun transactionSourcesFromMetadata(json: JSONObject?): Map<String, LiquidTxSource> {
            val sourcesObj = json?.optJSONObject("transactionSources") ?: return emptyMap()
            val sources = mutableMapOf<String, LiquidTxSource>()
            val keys = sourcesObj.keys()
            while (keys.hasNext()) {
                val txid = keys.next()
                val source =
                    runCatching { LiquidTxSource.valueOf(sourcesObj.getString(txid)) }.getOrNull()
                if (source != null) {
                    sources[txid] = source
                }
            }
            return sources
        }

        fun swapDetailsFromMetadata(json: JSONObject?): Map<String, LiquidSwapDetails> =
            swapDetailsFromJson(json?.optJSONObject("swapDetails"))
    }

    object Spark {
        fun metadataToJson(metadata: SparkBackupMetadata): JSONObject =
            JSONObject().apply {
                put("transactionSources", metadata.transactionSources.toJsonObject())
                put("paymentRecipients", metadata.paymentRecipients.toJsonObject())
                put("depositAddresses", metadata.depositAddresses.toJsonObject())
                put(
                    "pendingDeposits",
                    JSONArray().apply {
                        metadata.pendingDeposits.forEach { deposit ->
                            put(deposit.toJsonObject())
                        }
                    },
                )
                metadata.onchainDepositAddress?.takeIf { it.isNotBlank() }?.let {
                    put("onchainDepositAddress", it)
                }
            }

        fun metadataFromJson(json: JSONObject?): SparkBackupMetadata? {
            json ?: return null
            return SparkBackupMetadata(
                transactionSources = json.optJSONObject("transactionSources").toStringMap(),
                paymentRecipients = json.optJSONObject("paymentRecipients").toStringMap(),
                depositAddresses = json.optJSONObject("depositAddresses").toStringMap(),
                pendingDeposits = json.optJSONArray("pendingDeposits").toPendingDeposits(),
                onchainDepositAddress = json.optString("onchainDepositAddress", "").ifBlank { null },
            )
        }
    }

    private fun Map<String, String>.toJsonObject(): JSONObject =
        JSONObject().apply {
            this@toJsonObject.forEach { (key, value) ->
                if (value.isNotBlank()) {
                    put(key, value)
                }
            }
        }

    private fun JSONObject?.toStringMap(): Map<String, String> {
        if (this == null) return emptyMap()
        val result = mutableMapOf<String, String>()
        val keys = keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val value = optString(key, "")
            if (value.isNotBlank()) {
                result[key] = value
            }
        }
        return result
    }

    private fun SparkUnclaimedDeposit.toJsonObject(): JSONObject =
        JSONObject()
            .put("txid", txid)
            .put("vout", vout.toLong())
            .put("amountSats", amountSats)
            .put("isMature", isMature)
            .put("timestamp", timestamp ?: JSONObject.NULL)
            .put("address", address ?: JSONObject.NULL)
            .put("claimError", claimError ?: JSONObject.NULL)

    private fun JSONArray?.toPendingDeposits(): List<SparkUnclaimedDeposit> {
        if (this == null) return emptyList()
        return List(length()) { index -> optJSONObject(index) }
            .mapNotNull { json ->
                json?.let {
                    val txid = it.optString("txid", "")
                    if (txid.isBlank()) {
                        null
                    } else {
                        SparkUnclaimedDeposit(
                            txid = txid,
                            vout = it.optLong("vout", 0L).toUInt(),
                            amountSats = it.optLong("amountSats", 0L),
                            isMature = it.optBoolean("isMature", false),
                            timestamp =
                                if (it.isNull("timestamp")) {
                                    null
                                } else {
                                    it.optLong("timestamp")
                                },
                            address =
                                if (it.isNull("address")) {
                                    null
                                } else {
                                    it.optString("address")
                                },
                            claimError =
                                if (it.isNull("claimError")) {
                                    null
                                } else {
                                    it.optString("claimError")
                                },
                        )
                    }
                }
            }
    }

    private fun swapDetailsToJson(swapDetails: Map<String, LiquidSwapDetails>): JSONObject =
        JSONObject().apply {
            swapDetails.forEach { (txid, details) ->
                put(
                    txid,
                    JSONObject().apply {
                        put("service", details.service.name)
                        put("direction", details.direction.name)
                        put("swapId", details.swapId)
                        put("role", details.role.name)
                        put("depositAddress", details.depositAddress)
                        put("receiveAddress", details.receiveAddress)
                        put("refundAddress", details.refundAddress)
                        put("sendAmountSats", details.sendAmountSats)
                        put("expectedReceiveAmountSats", details.expectedReceiveAmountSats)
                        put("paymentInput", details.paymentInput)
                        put("resolvedPaymentInput", details.resolvedPaymentInput)
                        put("invoice", details.invoice)
                        put("status", details.status)
                        put("timeoutBlockHeight", details.timeoutBlockHeight)
                        put("refundPublicKey", details.refundPublicKey)
                        put("claimPublicKey", details.claimPublicKey)
                        put("swapTree", details.swapTree)
                        put("blindingKey", details.blindingKey)
                    },
                )
            }
        }

    private fun swapDetailsFromJson(json: JSONObject?): Map<String, LiquidSwapDetails> {
        val keys = json?.keys() ?: return emptyMap()
        val result = mutableMapOf<String, LiquidSwapDetails>()
        while (keys.hasNext()) {
            val txid = keys.next()
            val detailsJson = json.optJSONObject(txid) ?: continue
            val details = parseSwapDetails(detailsJson) ?: continue
            result[txid] = details
        }
        return result
    }

    private fun parseSwapDetails(detailsJson: JSONObject): LiquidSwapDetails? =
        runCatching {
            LiquidSwapDetails(
                service = SwapService.valueOf(detailsJson.getString("service")),
                direction = SwapDirection.valueOf(detailsJson.getString("direction")),
                swapId = detailsJson.getString("swapId"),
                role = LiquidSwapTxRole.valueOf(
                    detailsJson.optString("role", LiquidSwapTxRole.FUNDING.name),
                ),
                depositAddress = detailsJson.getString("depositAddress"),
                receiveAddress = detailsJson.optString("receiveAddress").takeIf { it.isNotBlank() },
                refundAddress = detailsJson.optString("refundAddress").takeIf { it.isNotBlank() },
                sendAmountSats = detailsJson.optLong("sendAmountSats", 0L),
                expectedReceiveAmountSats = detailsJson.optLong("expectedReceiveAmountSats", 0L),
                paymentInput = detailsJson.optString("paymentInput").takeIf { it.isNotBlank() },
                resolvedPaymentInput = detailsJson.optString("resolvedPaymentInput").takeIf { it.isNotBlank() },
                invoice = detailsJson.optString("invoice").takeIf { it.isNotBlank() },
                status = detailsJson.optString("status").takeIf { it.isNotBlank() },
                timeoutBlockHeight =
                    detailsJson.optInt("timeoutBlockHeight").takeIf {
                        !detailsJson.isNull("timeoutBlockHeight")
                    },
                refundPublicKey = detailsJson.optString("refundPublicKey").takeIf { it.isNotBlank() },
                claimPublicKey = detailsJson.optString("claimPublicKey").takeIf { it.isNotBlank() },
                swapTree = detailsJson.optString("swapTree").takeIf { it.isNotBlank() },
                blindingKey = detailsJson.optString("blindingKey").takeIf { it.isNotBlank() },
            )
        }.getOrNull()
}
