package github.aeonbtc.ibiswallet.util

import github.aeonbtc.ibiswallet.data.model.WalletLayer
import github.aeonbtc.ibiswallet.data.model.Layer2Provider
import github.aeonbtc.ibiswallet.viewmodel.SendScreenDraft
import java.math.BigDecimal
import java.net.URLDecoder
import java.util.Locale
import lwk.Payment
import lwk.PaymentKind

internal sealed interface ParsedSendRecipient {
    val rawInput: String

    data class Empty(
        override val rawInput: String = "",
    ) : ParsedSendRecipient

    data class Bitcoin(
        override val rawInput: String,
        val address: String,
        val amountSats: Long? = null,
        val label: String? = null,
        val message: String? = null,
    ) : ParsedSendRecipient

    data class Liquid(
        override val rawInput: String,
        val address: String,
        val amountSats: Long? = null,
        val label: String? = null,
        val message: String? = null,
        val assetId: String? = null,
    ) : ParsedSendRecipient

    data class Spark(
        override val rawInput: String,
        val paymentRequest: String,
        val amountSats: Long? = null,
        val label: String? = null,
    ) : ParsedSendRecipient

    data class Lightning(
        override val rawInput: String,
        val paymentInput: String,
        val kind: LightningKind,
        val amountSats: Long? = null,
        val fallbackBitcoin: Bitcoin? = null,
    ) : ParsedSendRecipient

    data class UnsupportedLightning(
        override val rawInput: String,
        val errorMessage: String,
        val fallbackBitcoin: Bitcoin? = null,
    ) : ParsedSendRecipient

    data class Unknown(
        override val rawInput: String,
        val errorMessage: String,
    ) : ParsedSendRecipient
}

internal enum class LightningKind {
    BOLT11,
    BOLT12,
    LNURL,
}

internal data class SendRouteResolution(
    val route: WalletLayer,
    val draft: SendScreenDraft,
    val recipient: ParsedSendRecipient,
)

private const val LAYER2_DEFAULT_FEE_RATE = 0.1

internal fun parseSendRecipient(input: String): ParsedSendRecipient {
    val trimmed = input.trim()
    if (trimmed.isEmpty()) {
        return ParsedSendRecipient.Empty()
    }
    val bip353Address = normalizedBip353Address(trimmed)
    parseSilentPaymentRecipient(trimmed)?.let { return it }

    BitcoinUtils.unsupportedNonMainnetReason(trimmed)?.let {
        return ParsedSendRecipient.Unknown(
            rawInput = trimmed,
            errorMessage = it,
        )
    }

    if (isValidBitcoinAddress(trimmed)) {
        return ParsedSendRecipient.Bitcoin(
            rawInput = trimmed,
            address = trimmed,
        )
    }

    val queryParams =
        try {
            parseUriQueryParameters(trimmed)
        } catch (e: IllegalArgumentException) {
            return ParsedSendRecipient.Unknown(
                rawInput = trimmed,
                errorMessage = e.message ?: "Invalid payment URI",
            )
        }
    parseSilentPaymentUri(trimmed, queryParams)?.let { return it }
    val payment = parsePayment(trimmed)

    if (payment == null) {
        parseOpaqueLiquidRecipient(trimmed, queryParams)?.let { return it }
        parseSparkRecipient(trimmed, queryParams)?.let { return it }
        if (bip353Address != null) {
            return ParsedSendRecipient.Lightning(
                rawInput = trimmed,
                paymentInput = bip353Address,
                kind = LightningKind.BOLT12,
            )
        }
        return ParsedSendRecipient.Unknown(
            rawInput = trimmed,
            errorMessage = "Unsupported payment format",
        )
    }

    return when (payment.kind()) {
        PaymentKind.BITCOIN_ADDRESS -> {
            val address = payment.bitcoinAddress()?.toString().orEmpty()
            BitcoinUtils.unsupportedNonMainnetReason(address)?.let {
                ParsedSendRecipient.Unknown(
                    rawInput = trimmed,
                    errorMessage = it,
                )
            } ?: ParsedSendRecipient.Bitcoin(
                rawInput = trimmed,
                address = address,
            )
        }
        PaymentKind.BIP21 -> {
            val bip21 = payment.bip21()
                ?: return ParsedSendRecipient.Unknown(trimmed, "Invalid Bitcoin address")
            val bip21Address = bip21.address().toString()
            BitcoinUtils.unsupportedNonMainnetReason(bip21Address)?.let {
                return ParsedSendRecipient.Unknown(
                    rawInput = trimmed,
                    errorMessage = it,
                )
            }
            val fallbackBitcoin =
                ParsedSendRecipient.Bitcoin(
                    rawInput = trimmed,
                    address = bip21Address,
                    amountSats = bip21.amount()?.toLong(),
                    label = bip21.label(),
                    message = bip21.message(),
                )

            val bolt12Offer =
                queryParams["lno"]?.takeIf { it.isNotBlank() }
                    ?: queryParams["offer"]?.takeIf { it.isNotBlank() }
                    ?: bip21.offer()?.takeIf { it.isNotBlank() }
            bolt12Offer?.let { offer ->
                ParsedSendRecipient.Lightning(
                    rawInput = trimmed,
                    paymentInput = offer,
                    kind = LightningKind.BOLT12,
                    amountSats = bip21.amount()?.toLong(),
                    fallbackBitcoin = fallbackBitcoin,
                )
            } ?: bip21.lightning()?.let { invoice ->
                ParsedSendRecipient.Lightning(
                    rawInput = trimmed,
                    paymentInput = invoice.toString(),
                    kind = LightningKind.BOLT11,
                    amountSats = invoice.amountMilliSatoshis()?.let(::msatsToRoundedSats),
                    fallbackBitcoin = fallbackBitcoin,
                )
            } ?: fallbackBitcoin
        }
        PaymentKind.LIQUID_ADDRESS -> {
            ParsedSendRecipient.Liquid(
                rawInput = trimmed,
                address = payment.liquidAddress()?.toString().orEmpty(),
            )
        }
        PaymentKind.LIQUID_BIP21 -> {
            val liquidBip21 = payment.liquidBip21()
                ?: return ParsedSendRecipient.Unknown(trimmed, "Invalid Liquid address")
            ParsedSendRecipient.Liquid(
                rawInput = trimmed,
                address = liquidBip21.address.toString(),
                amountSats = liquidBip21.satoshi?.toLong(),
                label = queryParams["label"],
                message = queryParams["message"],
                assetId = queryParams["assetid"],
            )
        }
        PaymentKind.LIGHTNING_INVOICE -> {
            val invoice = payment.lightningInvoice()
                ?: return ParsedSendRecipient.Unknown(trimmed, "Unsupported payment format")
            ParsedSendRecipient.Lightning(
                rawInput = trimmed,
                paymentInput = invoice.toString(),
                kind = LightningKind.BOLT11,
                amountSats = invoice.amountMilliSatoshis()?.let(::msatsToRoundedSats),
            )
        }
        PaymentKind.LIGHTNING_OFFER -> {
            val offer = payment.lightningOffer()
                ?: return ParsedSendRecipient.Unknown(trimmed, "Unsupported payment format")
            ParsedSendRecipient.Lightning(
                rawInput = trimmed,
                paymentInput = offer,
                kind = LightningKind.BOLT12,
            )
        }
        PaymentKind.LN_URL -> {
            val lnurlUrl = payment.lnurl()?.takeIf { it.isNotBlank() }
            if (lnurlUrl != null) {
                ParsedSendRecipient.Lightning(
                    rawInput = trimmed,
                    paymentInput = lnurlUrl,
                    kind = LightningKind.LNURL,
                )
            } else {
                ParsedSendRecipient.UnsupportedLightning(
                    rawInput = trimmed,
                    errorMessage = "Invalid LNURL",
                )
            }
        }
        PaymentKind.BIP353 -> {
            ParsedSendRecipient.Lightning(
                rawInput = trimmed,
                paymentInput = payment.bip353()?.takeIf { it.isNotBlank() } ?: bip353Address ?: trimmed,
                kind = LightningKind.BOLT12,
            )
        }
        PaymentKind.BIP321 -> {
            ParsedSendRecipient.Unknown(
                rawInput = trimmed,
                errorMessage = "Unsupported payment URI",
            )
        }
    }
}

internal fun resolveSendRoute(
    input: String,
    layer1UseSats: Boolean,
    layer2UseSats: Boolean,
    isLiquidAvailable: Boolean,
): SendRouteResolution {
    return when (val parsed = parseSendRecipient(input)) {
        is ParsedSendRecipient.Bitcoin -> {
            SendRouteResolution(
                route = WalletLayer.LAYER1,
                draft = parsed.toLayer1Draft(layer1UseSats),
                recipient = parsed,
            )
        }
        is ParsedSendRecipient.Lightning -> {
            if (isLiquidAvailable) {
                SendRouteResolution(
                    route = WalletLayer.LAYER2,
                    draft = parsed.toLayer2Draft(layer2UseSats),
                    recipient = parsed,
                )
            } else {
                val fallback = parsed.fallbackBitcoin
                SendRouteResolution(
                    route = WalletLayer.LAYER1,
                    draft = fallback?.toLayer1Draft(layer1UseSats) ?: SendScreenDraft(recipientAddress = parsed.rawInput),
                    recipient = fallback ?: parsed,
                )
            }
        }
        is ParsedSendRecipient.Liquid -> {
            val route = if (isLiquidAvailable) WalletLayer.LAYER2 else WalletLayer.LAYER1
            SendRouteResolution(
                route = route,
                draft = if (route == WalletLayer.LAYER2) parsed.toLayer2Draft(layer2UseSats) else SendScreenDraft(recipientAddress = parsed.rawInput),
                recipient = parsed,
            )
        }
        is ParsedSendRecipient.Spark -> {
            val route = if (isLiquidAvailable) WalletLayer.LAYER2 else WalletLayer.LAYER1
            SendRouteResolution(
                route = route,
                draft = if (route == WalletLayer.LAYER2) parsed.toLayer2Draft(layer2UseSats) else SendScreenDraft(recipientAddress = parsed.rawInput),
                recipient = parsed,
            )
        }
        is ParsedSendRecipient.UnsupportedLightning -> {
            if (isLiquidAvailable) {
                SendRouteResolution(
                    route = WalletLayer.LAYER2,
                    draft = SendScreenDraft(recipientAddress = parsed.rawInput, feeRate = LAYER2_DEFAULT_FEE_RATE),
                    recipient = parsed,
                )
            } else {
                val fallback = parsed.fallbackBitcoin
                SendRouteResolution(
                    route = WalletLayer.LAYER1,
                    draft = fallback?.toLayer1Draft(layer1UseSats) ?: SendScreenDraft(recipientAddress = parsed.rawInput),
                    recipient = fallback ?: parsed,
                )
            }
        }
        is ParsedSendRecipient.Empty -> {
            SendRouteResolution(
                route = WalletLayer.LAYER1,
                draft = SendScreenDraft(),
                recipient = parsed,
            )
        }
        is ParsedSendRecipient.Unknown -> {
            SendRouteResolution(
                route = WalletLayer.LAYER1,
                draft = SendScreenDraft(recipientAddress = parsed.rawInput),
                recipient = parsed,
            )
        }
    }
}

internal fun isRecognizedSendInput(input: String): Boolean {
    return when (parseSendRecipient(input)) {
        is ParsedSendRecipient.Bitcoin,
        is ParsedSendRecipient.Liquid,
        is ParsedSendRecipient.Spark,
        is ParsedSendRecipient.Lightning,
        is ParsedSendRecipient.UnsupportedLightning,
        -> true
        else -> false
    }
}

internal fun resolveLayer2SendDraft(
    input: String,
    layer2UseSats: Boolean,
    provider: Layer2Provider? = null,
): SendScreenDraft {
    return when (val parsed = parseSendRecipient(input)) {
        is ParsedSendRecipient.Bitcoin ->
            if (provider == Layer2Provider.SPARK) {
                parsed.toSparkDraft(layer2UseSats)
            } else {
                unsupportedLayer2Draft(parsed.rawInput)
            }
        is ParsedSendRecipient.Liquid ->
            if (provider == null || provider == Layer2Provider.LIQUID) {
                parsed.toLayer2Draft(layer2UseSats)
            } else {
                unsupportedLayer2Draft(parsed.rawInput)
            }
        is ParsedSendRecipient.Spark ->
            if (provider == null || provider == Layer2Provider.SPARK) {
                parsed.toLayer2Draft(layer2UseSats)
            } else {
                unsupportedLayer2Draft(parsed.rawInput)
            }
        is ParsedSendRecipient.Lightning -> parsed.toLayer2Draft(layer2UseSats)
        is ParsedSendRecipient.UnsupportedLightning ->
            SendScreenDraft(
                recipientAddress = parsed.rawInput,
                feeRate = LAYER2_DEFAULT_FEE_RATE,
            )
        is ParsedSendRecipient.Empty -> SendScreenDraft(feeRate = LAYER2_DEFAULT_FEE_RATE)
        else ->
            unsupportedLayer2Draft(parsed.rawInput)
    }
}

internal fun layer2RecipientValidationError(
    parsed: ParsedSendRecipient,
    provider: Layer2Provider? = null,
): String? {
    return when (parsed) {
        is ParsedSendRecipient.Empty -> null
        is ParsedSendRecipient.Bitcoin ->
            if (provider == Layer2Provider.SPARK) {
                null
            } else {
                "Bitcoin addresses are not supported on Liquid send"
            }
        is ParsedSendRecipient.Liquid ->
            if (provider == null || provider == Layer2Provider.LIQUID) {
                null
            } else {
                "Liquid requests are not supported on Spark send"
            }
        is ParsedSendRecipient.Spark ->
            if (provider == null || provider == Layer2Provider.SPARK) {
                null
            } else {
                "Spark requests are not supported on Liquid send"
            }
        is ParsedSendRecipient.Lightning ->
            when {
                provider != Layer2Provider.SPARK && parsed.kind == LightningKind.BOLT11 && parsed.amountSats == null ->
                    "Amountless Lightning invoices are not supported"
                else -> null
            }
        is ParsedSendRecipient.UnsupportedLightning -> parsed.errorMessage
        else ->
            when (provider) {
                Layer2Provider.LIQUID -> "Enter a Liquid, BOLT 11/12, or LN Address"
                Layer2Provider.SPARK -> "Enter a Spark, BOLT 11, LNURL, or Bitcoin address"
                else -> "Enter a Liquid, Spark, BOLT 11/12, or LN Address"
            }
    }
}

private fun ParsedSendRecipient.Bitcoin.toLayer1Draft(useSats: Boolean): SendScreenDraft {
    return SendScreenDraft(
        recipientAddress = address,
        amountInput = amountSats?.let { formatSatsForDraft(it, useSats) }.orEmpty(),
        label = label.orEmpty(),
    )
}

private fun ParsedSendRecipient.Bitcoin.toSparkDraft(useSats: Boolean): SendScreenDraft {
    return SendScreenDraft(
        recipientAddress = address,
        amountInput = amountSats?.let { formatSatsForDraft(it, useSats) }.orEmpty(),
        label = label.orEmpty(),
        feeRate = LAYER2_DEFAULT_FEE_RATE,
    )
}

private fun unsupportedLayer2Draft(rawInput: String): SendScreenDraft =
    SendScreenDraft(
        recipientAddress = rawInput,
        feeRate = LAYER2_DEFAULT_FEE_RATE,
    )

private fun ParsedSendRecipient.Liquid.toLayer2Draft(useSats: Boolean): SendScreenDraft {
    return SendScreenDraft(
        recipientAddress = address,
        amountInput = amountSats?.let { formatSatsForDraft(it, useSats) }.orEmpty(),
        label = label.orEmpty(),
        feeRate = LAYER2_DEFAULT_FEE_RATE,
        assetId = assetId,
    )
}

private fun ParsedSendRecipient.Spark.toLayer2Draft(useSats: Boolean): SendScreenDraft {
    return SendScreenDraft(
        recipientAddress = paymentRequest,
        amountInput = amountSats?.let { formatSatsForDraft(it, useSats) }.orEmpty(),
        label = label.orEmpty(),
        feeRate = LAYER2_DEFAULT_FEE_RATE,
    )
}

private fun ParsedSendRecipient.Lightning.toLayer2Draft(useSats: Boolean): SendScreenDraft {
    return SendScreenDraft(
        recipientAddress = paymentInput,
        amountInput = amountSats?.let { formatSatsForDraft(it, useSats) }.orEmpty(),
        feeRate = LAYER2_DEFAULT_FEE_RATE,
    )
}

private fun formatSatsForDraft(amountSats: Long, useSats: Boolean): String {
    return if (useSats) {
        amountSats.toString()
    } else {
        String.format(Locale.US, "%.8f", amountSats / 100_000_000.0)
            .trimEnd('0')
            .trimEnd('.')
    }
}

private fun parsePayment(input: String): Payment? {
    runCatching { Payment(input) }
        .getOrNull()
        ?.let { return it }

    normalizedLightningPayload(input)?.let { normalized ->
        runCatching { Payment(normalized) }
            .getOrNull()
            ?.let { return it }
    }

    return null
}

private fun parseSilentPaymentRecipient(input: String): ParsedSendRecipient.Bitcoin? {
    if (!input.lowercase(Locale.US).startsWith("sp1")) {
        return null
    }
    return if (SilentPayment.isSilentPaymentAddress(input)) {
        ParsedSendRecipient.Bitcoin(
            rawInput = input,
            address = input.trim(),
        )
    } else {
        null
    }
}

private fun parseSilentPaymentUri(
    input: String,
    queryParams: Map<String, String>,
): ParsedSendRecipient.Bitcoin? {
    if (!input.startsWith("bitcoin:", ignoreCase = true)) {
        return null
    }
    val address = input.substringAfter(':').substringBefore('?').trim()
    if (!address.lowercase(Locale.US).startsWith("sp1") || !SilentPayment.isSilentPaymentAddress(address)) {
        return null
    }
    return ParsedSendRecipient.Bitcoin(
        rawInput = input,
        address = address,
        amountSats = queryParams["amount"]?.let(::parseBitcoinAmountToSats),
        label = queryParams["label"]?.takeIf { it.isNotBlank() },
        message = queryParams["message"]?.takeIf { it.isNotBlank() },
    )
}

private fun isValidBitcoinAddress(input: String): Boolean {
    val trimmed = input.trim()
    return when {
        SilentPayment.isSilentPaymentAddress(trimmed) -> true
        trimmed.startsWith("1") ||
            trimmed.startsWith("3") -> isValidBase58Address(trimmed)
        else -> false
    }
}

private fun isValidBase58Address(address: String): Boolean {
    if (address.length !in 25..35) return false
    return runCatching { BitcoinUtils.Base58.decodeChecked(address) }.isSuccess
}

private fun normalizedLightningPayload(input: String): String? {
    val prefix = "lightning:"
    return if (input.length > prefix.length && input.take(prefix.length).equals(prefix, ignoreCase = true)) {
        input.substring(prefix.length).trim()
    } else {
        null
    }
}

private fun normalizedBip353Address(input: String): String? {
    val trimmed = input.trim().removePrefix("₿")
    if (trimmed.isBlank() || trimmed.contains("://") || trimmed.contains(":")) {
        return null
    }
    val parts = trimmed.split("@", limit = 3)
    return if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) {
        trimmed
    } else {
        null
    }
}

private fun parseUriQueryParameters(input: String): Map<String, String> {
    val queryIndex = input.indexOf('?')
    if (queryIndex == -1 || queryIndex == input.lastIndex) {
        return emptyMap()
    }

    val params = mutableMapOf<String, String>()
    input.substring(queryIndex + 1)
        .split("&")
        .forEach { entry ->
            if (entry.isBlank()) return@forEach
            val keyValue = entry.split("=", limit = 2)
            val key = keyValue[0].lowercase(Locale.US)
            require(key.isNotBlank()) { "Payment URI has a blank query key" }
            require(!params.containsKey(key)) { "Payment URI contains duplicate '$key' parameter" }
            val value = keyValue.getOrElse(1) { "" }
            params[key] =
                try {
                    URLDecoder.decode(value, "UTF-8")
                } catch (e: IllegalArgumentException) {
                    throw IllegalArgumentException("Payment URI contains invalid percent encoding", e)
                }
        }
    return params
}

private fun parseOpaqueLiquidRecipient(
    input: String,
    queryParams: Map<String, String>,
): ParsedSendRecipient.Liquid? {
    if (!input.startsWith("liquid:", ignoreCase = true) &&
        !input.startsWith("liquidnetwork:", ignoreCase = true)
    ) {
        return null
    }

    val address = input.substringAfter(':').substringBefore('?').trim()
    if (address.isBlank()) {
        return null
    }

    return ParsedSendRecipient.Liquid(
        rawInput = input,
        address = address,
        amountSats = queryParams["amount"]?.let(::parseLiquidAmountToSats),
        label = queryParams["label"]?.takeIf { it.isNotBlank() },
        message = queryParams["message"]?.takeIf { it.isNotBlank() },
        assetId = queryParams["assetid"]?.takeIf { it.isNotBlank() },
    )
}

private fun parseSparkRecipient(
    input: String,
    queryParams: Map<String, String>,
): ParsedSendRecipient.Spark? {
    val lower = input.lowercase(Locale.US)
    val looksLikeSpark =
        lower.startsWith("spark1") ||
            lower.startsWith("spark:") ||
            lower.startsWith("sparkinvoice")
    if (!looksLikeSpark) return null

    return ParsedSendRecipient.Spark(
        rawInput = input,
        paymentRequest = input,
        amountSats = queryParams["amount"]?.let(::parseBitcoinAmountToSats),
        label = queryParams["label"]?.takeIf { it.isNotBlank() },
    )
}

private fun parseLiquidAmountToSats(amountText: String): Long? {
    return runCatching {
        BigDecimal(amountText)
            .movePointRight(8)
            .longValueExact()
    }.getOrNull()
}

private fun parseBitcoinAmountToSats(amountText: String): Long? =
    parseLiquidAmountToSats(amountText)

private fun msatsToRoundedSats(amountMsats: ULong): Long {
    val value = amountMsats.toLong()
    return (value + 999L) / 1000L
}
