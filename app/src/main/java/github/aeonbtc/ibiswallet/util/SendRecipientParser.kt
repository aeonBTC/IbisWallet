package github.aeonbtc.ibiswallet.util

import github.aeonbtc.ibiswallet.data.model.WalletLayer
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

    val queryParams = parseUriQueryParameters(trimmed)
    val payment = parsePayment(trimmed)

    if (payment == null) {
        parseOpaqueLiquidRecipient(trimmed, queryParams)?.let { return it }
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
        is ParsedSendRecipient.Lightning,
        is ParsedSendRecipient.UnsupportedLightning,
        -> true
        else -> false
    }
}

internal fun resolveLayer2SendDraft(
    input: String,
    layer2UseSats: Boolean,
): SendScreenDraft {
    return when (val parsed = parseSendRecipient(input)) {
        is ParsedSendRecipient.Liquid -> parsed.toLayer2Draft(layer2UseSats)
        is ParsedSendRecipient.Lightning -> parsed.toLayer2Draft(layer2UseSats)
        is ParsedSendRecipient.UnsupportedLightning ->
            SendScreenDraft(
                recipientAddress = parsed.rawInput,
                feeRate = LAYER2_DEFAULT_FEE_RATE,
            )
        is ParsedSendRecipient.Empty -> SendScreenDraft(feeRate = LAYER2_DEFAULT_FEE_RATE)
        else ->
            SendScreenDraft(
                recipientAddress = parsed.rawInput,
                feeRate = LAYER2_DEFAULT_FEE_RATE,
            )
    }
}

internal fun layer2RecipientValidationError(parsed: ParsedSendRecipient): String? {
    return when (parsed) {
        is ParsedSendRecipient.Empty -> null
        is ParsedSendRecipient.Liquid -> null
        is ParsedSendRecipient.Lightning ->
            when {
                parsed.kind == LightningKind.BOLT11 && parsed.amountSats == null ->
                    "Amountless Lightning invoices are not supported"
                else -> null
            }
        is ParsedSendRecipient.UnsupportedLightning -> parsed.errorMessage
        else -> "Enter a Liquid address, LN invoice, BOLT12 offer, or Lightning Address"
    }
}

private fun ParsedSendRecipient.Bitcoin.toLayer1Draft(useSats: Boolean): SendScreenDraft {
    return SendScreenDraft(
        recipientAddress = address,
        amountInput = amountSats?.let { formatSatsForDraft(it, useSats) }.orEmpty(),
        label = label.orEmpty(),
    )
}

private fun ParsedSendRecipient.Liquid.toLayer2Draft(useSats: Boolean): SendScreenDraft {
    return SendScreenDraft(
        recipientAddress = address,
        amountInput = amountSats?.let { formatSatsForDraft(it, useSats) }.orEmpty(),
        label = label.orEmpty(),
        feeRate = LAYER2_DEFAULT_FEE_RATE,
        assetId = assetId,
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

private fun isValidBitcoinAddress(input: String): Boolean {
    val trimmed = input.trim()
    return when {
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

    return input.substring(queryIndex + 1)
        .split("&")
        .mapNotNull { entry ->
            if (entry.isBlank()) {
                return@mapNotNull null
            }
            val keyValue = entry.split("=", limit = 2)
            val key = keyValue[0].lowercase(Locale.US)
            val value = keyValue.getOrElse(1) { "" }
            key to URLDecoder.decode(value, "UTF-8")
        }
        .toMap()
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

private fun parseLiquidAmountToSats(amountText: String): Long? {
    return runCatching {
        BigDecimal(amountText)
            .movePointRight(8)
            .longValueExact()
    }.getOrNull()
}

private fun msatsToRoundedSats(amountMsats: ULong): Long {
    val value = amountMsats.toLong()
    return (value + 999L) / 1000L
}
