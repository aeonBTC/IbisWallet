package github.aeonbtc.ibiswallet.ui.screens

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import github.aeonbtc.ibiswallet.R
import github.aeonbtc.ibiswallet.data.model.AddressType
import github.aeonbtc.ibiswallet.ui.components.IbisButton
import github.aeonbtc.ibiswallet.ui.theme.DarkCard
import github.aeonbtc.ibiswallet.ui.theme.ErrorRed
import github.aeonbtc.ibiswallet.util.BitcoinUtils
import github.aeonbtc.ibiswallet.util.LightningKind
import github.aeonbtc.ibiswallet.util.ParsedSendRecipient
import github.aeonbtc.ibiswallet.util.parseSendRecipient
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private sealed interface DecoderResult {
    data class Rows(
        val title: String,
        val rows: List<Pair<String, String>>,
    ) : DecoderResult

    data class Error(
        val message: String,
    ) : DecoderResult
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DecoderScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var input by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<DecoderResult?>(null) }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.loc_cdfc6e09),
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.tools_decoder_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = DarkCard),
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
            ) {
                ToolField(
                    value = input,
                    onValueChange = {
                        input = it
                        result = null
                    },
                    title = stringResource(R.string.tools_decoder_input_label),
                    placeholder = stringResource(R.string.tools_decoder_input_hint),
                    monospace = true,
                    singleLine = false,
                    minHeight = 120,
                )

                Spacer(modifier = Modifier.height(12.dp))

                IbisButton(
                    onClick = { result = decodePayload(context, input) },
                    enabled = input.isNotBlank(),
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                ) {
                    Text(
                        text = stringResource(R.string.tools_decode_button),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        when (val current = result) {
            is DecoderResult.Rows -> {
                ToolDetailCard(title = current.title, rows = current.rows)
            }
            is DecoderResult.Error -> {
                ToolResultBanner(text = current.message, color = ErrorRed)
            }
            null -> Unit
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

private fun decodePayload(
    context: Context,
    raw: String,
): DecoderResult {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) {
        return DecoderResult.Error(context.getString(R.string.tools_error_unrecognized))
    }

    return when (val parsed = parseSendRecipient(trimmed, context)) {
        is ParsedSendRecipient.Bitcoin -> decodeBitcoin(context, parsed, isBip21 = trimmed.startsWith("bitcoin:", ignoreCase = true))
        is ParsedSendRecipient.Liquid -> decodeLiquid(context, parsed)
        is ParsedSendRecipient.Ark -> decodeArk(context, parsed)
        is ParsedSendRecipient.Spark -> decodeSpark(context, parsed)
        is ParsedSendRecipient.Lightning ->
            when (parsed.kind) {
                LightningKind.BOLT11 -> decodeBolt11(context, parsed)
                LightningKind.BOLT12 -> decodeBolt12(context, parsed)
                LightningKind.LNURL -> decodeLnurl(context, parsed)
            }
        is ParsedSendRecipient.UnsupportedLightning ->
            DecoderResult.Error(parsed.errorMessage.ifBlank { context.getString(R.string.tools_error_unrecognized) })
        is ParsedSendRecipient.Unknown ->
            DecoderResult.Error(parsed.errorMessage.ifBlank { context.getString(R.string.tools_error_unrecognized) })
        is ParsedSendRecipient.Empty ->
            DecoderResult.Error(context.getString(R.string.tools_error_unrecognized))
    }
}

private fun amountRow(
    context: Context,
    amountSats: Long,
): Pair<String, String> =
    context.getString(R.string.tools_result_amount) to
        context.getString(R.string.tools_result_amount_sats, amountSats.toString())

private fun decodeBitcoin(
    context: Context,
    parsed: ParsedSendRecipient.Bitcoin,
    isBip21: Boolean,
): DecoderResult {
    val addressType = BitcoinUtils.detectAddressType(parsed.address)
    val rows = mutableListOf<Pair<String, String>>()
    rows += context.getString(R.string.tools_result_network) to "Bitcoin"
    rows += context.getString(R.string.tools_result_type) to
        (addressType?.let { context.getString(it.labelRes()) }
            ?: context.getString(R.string.tools_result_unknown))
    rows += context.getString(R.string.tools_result_address) to parsed.address
    parsed.amountSats?.let { rows += amountRow(context, it) }
    parsed.label?.takeIf { it.isNotBlank() }?.let {
        rows += context.getString(R.string.tools_result_label) to it
    }
    parsed.message?.takeIf { it.isNotBlank() }?.let {
        rows += context.getString(R.string.tools_result_message) to it
    }
    return DecoderResult.Rows(
        title = context.getString(if (isBip21) R.string.tools_type_bip21 else R.string.tools_type_bitcoin_address),
        rows = rows,
    )
}

private fun decodeLiquid(
    context: Context,
    parsed: ParsedSendRecipient.Liquid,
): DecoderResult {
    val rows = mutableListOf<Pair<String, String>>()
    rows += context.getString(R.string.tools_result_network) to "Liquid"
    rows += context.getString(R.string.tools_result_address) to parsed.address
    parsed.amountSats?.let { rows += amountRow(context, it) }
    parsed.label?.takeIf { it.isNotBlank() }?.let {
        rows += context.getString(R.string.tools_result_label) to it
    }
    parsed.message?.takeIf { it.isNotBlank() }?.let {
        rows += context.getString(R.string.tools_result_message) to it
    }
    parsed.assetId?.takeIf { it.isNotBlank() }?.let {
        rows += context.getString(R.string.tools_result_asset) to it
    }
    return DecoderResult.Rows(
        title = context.getString(R.string.tools_type_liquid_address),
        rows = rows,
    )
}

private fun decodeArk(
    context: Context,
    parsed: ParsedSendRecipient.Ark,
): DecoderResult {
    val rows = mutableListOf<Pair<String, String>>()
    rows += context.getString(R.string.tools_result_network) to "Ark"
    rows += context.getString(R.string.tools_result_address) to parsed.address
    parsed.amountSats?.let { rows += amountRow(context, it) }
    parsed.label?.takeIf { it.isNotBlank() }?.let {
        rows += context.getString(R.string.tools_result_label) to it
    }
    return DecoderResult.Rows(
        title = context.getString(R.string.tools_type_ark_address),
        rows = rows,
    )
}

private fun decodeSpark(
    context: Context,
    parsed: ParsedSendRecipient.Spark,
): DecoderResult =
    DecoderResult.Rows(
        title = context.getString(R.string.tools_type_spark_request),
        rows =
            listOf(
                context.getString(R.string.tools_result_network) to "Spark",
                context.getString(R.string.tools_result_invoice) to parsed.paymentRequest,
            ),
    )

private fun decodeBolt11(
    context: Context,
    parsed: ParsedSendRecipient.Lightning,
): DecoderResult {
    val rows = mutableListOf<Pair<String, String>>()
    rows += context.getString(R.string.tools_result_type) to "BOLT11"

    val invoice = parsed.paymentInput.trim().removePrefix("lightning:").trim()
    val details =
        runCatching {
            val bolt11 = lwk.Bolt11Invoice(invoice)
            try {
                Bolt11Details(
                    paymentHash = bolt11.paymentHash().lowercase().ifBlank { null },
                    amountSats =
                        bolt11.amountMilliSatoshis()
                            ?.takeIf { it > 0UL }
                            ?.let { ((it + 999UL) / 1000UL).toLong() },
                    description = bolt11.invoiceDescription().ifBlank { null },
                    expiresAtEpochSeconds =
                        bolt11.timestamp().toLong()
                            .takeIf { it > 0L }
                            ?.let { it + bolt11.expiryTime().toLong() },
                )
            } finally {
                runCatching { bolt11.destroy() }
            }
        }.getOrNull()

    val amountSats = details?.amountSats ?: parsed.amountSats
    amountSats?.let { rows += amountRow(context, it) }
    details?.description?.takeIf { it.isNotBlank() }?.let {
        rows += context.getString(R.string.tools_result_description) to it
    }
    details?.paymentHash?.takeIf { it.isNotBlank() }?.let {
        rows += context.getString(R.string.tools_result_payment_hash) to it
    }
    details?.expiresAtEpochSeconds?.let { epochSeconds ->
        runCatching {
            Instant
                .ofEpochSecond(epochSeconds)
                .atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofLocalizedDateTime(java.time.format.FormatStyle.MEDIUM))
        }.getOrNull()?.let {
            rows += context.getString(R.string.tools_result_expiry) to it
        }
    }
    rows += context.getString(R.string.tools_result_invoice) to invoice

    return DecoderResult.Rows(
        title = context.getString(R.string.tools_type_bolt11),
        rows = rows,
    )
}

private fun decodeBolt12(
    context: Context,
    parsed: ParsedSendRecipient.Lightning,
): DecoderResult {
    val rows = mutableListOf<Pair<String, String>>()
    rows += context.getString(R.string.tools_result_type) to "BOLT12"
    parsed.amountSats?.let { rows += amountRow(context, it) }
    rows += context.getString(R.string.tools_result_offer) to parsed.paymentInput
    return DecoderResult.Rows(
        title = context.getString(R.string.tools_type_bolt12),
        rows = rows,
    )
}

private fun decodeLnurl(
    context: Context,
    parsed: ParsedSendRecipient.Lightning,
): DecoderResult =
    DecoderResult.Rows(
        title = context.getString(R.string.tools_type_lnurl),
        rows =
            listOf(
                context.getString(R.string.tools_result_type) to "LNURL",
                context.getString(R.string.tools_result_url) to parsed.paymentInput,
            ),
    )

private data class Bolt11Details(
    val paymentHash: String?,
    val amountSats: Long?,
    val description: String?,
    val expiresAtEpochSeconds: Long?,
)

internal fun AddressType.labelRes(): Int =
    when (this) {
        AddressType.LEGACY -> R.string.tools_addr_legacy
        AddressType.SEGWIT -> R.string.tools_addr_segwit
        AddressType.TAPROOT -> R.string.tools_addr_taproot
    }
