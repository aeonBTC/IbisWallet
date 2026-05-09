package github.aeonbtc.ibiswallet.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import github.aeonbtc.ibiswallet.R
import github.aeonbtc.ibiswallet.data.local.SecureStorage
import github.aeonbtc.ibiswallet.data.model.Layer2Provider
import github.aeonbtc.ibiswallet.data.model.SparkOnchainFeeQuote
import github.aeonbtc.ibiswallet.data.model.SparkOnchainFeeSpeed
import github.aeonbtc.ibiswallet.data.model.SparkSendState
import github.aeonbtc.ibiswallet.ui.components.AmountLabel
import github.aeonbtc.ibiswallet.ui.components.IbisButton
import github.aeonbtc.ibiswallet.ui.components.QrScannerDialog
import github.aeonbtc.ibiswallet.ui.components.ScrollableDialogSurface
import github.aeonbtc.ibiswallet.ui.theme.AccentRed
import github.aeonbtc.ibiswallet.ui.theme.BitcoinOrange
import github.aeonbtc.ibiswallet.ui.theme.BorderColor
import github.aeonbtc.ibiswallet.ui.theme.DarkBackground
import github.aeonbtc.ibiswallet.ui.theme.DarkCard
import github.aeonbtc.ibiswallet.ui.theme.DarkSurface
import github.aeonbtc.ibiswallet.ui.theme.ErrorRed
import github.aeonbtc.ibiswallet.ui.theme.LightningYellow
import github.aeonbtc.ibiswallet.ui.theme.SparkPurple
import github.aeonbtc.ibiswallet.ui.theme.TextPrimary
import github.aeonbtc.ibiswallet.ui.theme.TextSecondary
import github.aeonbtc.ibiswallet.ui.theme.TextTertiary
import github.aeonbtc.ibiswallet.ui.theme.WarningYellow
import github.aeonbtc.ibiswallet.util.ParsedSendRecipient
import github.aeonbtc.ibiswallet.util.layer2RecipientValidationError
import github.aeonbtc.ibiswallet.util.parseSendRecipient
import github.aeonbtc.ibiswallet.viewmodel.SendScreenDraft
import java.util.Locale

@Composable
fun SparkSendScreen(
    draft: SendScreenDraft,
    sendState: SparkSendState,
    denomination: String,
    btcPrice: Double?,
    fiatCurrency: String,
    privacyMode: Boolean,
    availableSats: Long,
    onUpdateDraft: (SendScreenDraft) -> Unit,
    onLoadOnchainFeeQuotes: suspend (String, Long, Boolean) -> List<SparkOnchainFeeQuote>,
    onPrepareSend: (String, Long?, SparkOnchainFeeSpeed, Boolean) -> Unit,
    onSendPrepared: () -> Unit,
    onResetSend: () -> Unit,
    onToggleDenomination: () -> Unit,
) {
    val useSats = denomination == SecureStorage.DENOMINATION_SATS
    var paymentRequest by remember { mutableStateOf(draft.recipientAddress) }
    var amountInput by remember { mutableStateOf(draft.amountInput) }
    var isUsdMode by remember { mutableStateOf(false) }
    var showQrScanner by remember { mutableStateOf(false) }
    var scanError by remember { mutableStateOf<String?>(null) }
    var showLabelField by remember { mutableStateOf(draft.label.isNotBlank()) }
    var labelText by remember { mutableStateOf(draft.label) }
    var showConfirmDialog by remember { mutableStateOf(false) }
    var prepareError by remember { mutableStateOf<String?>(null) }
    var onchainFeeSpeed by remember { mutableStateOf(SparkOnchainFeeSpeed.FAST) }
    var isMaxMode by remember { mutableStateOf(draft.isMaxSend) }
    var onchainFeeQuotes by remember { mutableStateOf<List<SparkOnchainFeeQuote>>(emptyList()) }
    var onchainFeeQuotesLoading by remember { mutableStateOf(false) }

    LaunchedEffect(draft) {
        if (draft.recipientAddress.isNotBlank()) paymentRequest = draft.recipientAddress
        if (draft.amountInput.isNotBlank()) amountInput = draft.amountInput
        if (draft.label.isNotBlank()) {
            labelText = draft.label
            showLabelField = true
        }
        isMaxMode = draft.isMaxSend
    }

    LaunchedEffect(paymentRequest, amountInput, labelText, isMaxMode) {
        val updatedDraft = SendScreenDraft(
            recipientAddress = paymentRequest,
            amountInput = amountInput,
            label = labelText,
            isMaxSend = isMaxMode,
        )
        if (updatedDraft != draft) {
            onUpdateDraft(updatedDraft)
        }
    }

    val amountSats = remember(amountInput, denomination, isUsdMode, btcPrice) {
        parseSparkSendAmount(amountInput, useSats, isUsdMode, btcPrice)
    }
    val parsedRecipient = parseSendRecipient(paymentRequest.trim())
    val recipientValidationError = remember(parsedRecipient, paymentRequest) {
        if (paymentRequest.isBlank()) null else layer2RecipientValidationError(parsedRecipient, Layer2Provider.SPARK)
    }
    val recipientBadges = remember(parsedRecipient) { sparkRecipientModeBadges(parsedRecipient) }
    val busy = sendState is SparkSendState.Preparing || sendState is SparkSendState.Sending

    LaunchedEffect(amountInput, paymentRequest, isMaxMode, parsedRecipient) {
        prepareError = null
    }

    LaunchedEffect(sendState) {
        when (sendState) {
            is SparkSendState.Preparing -> prepareError = null
            is SparkSendState.Error -> {
                showConfirmDialog = false
                prepareError = sendState.message
            }
            is SparkSendState.Preview -> prepareError = null
            else -> Unit
        }
    }

    val recipientFixedAmountSats: Long? =
        when (parsedRecipient) {
            is ParsedSendRecipient.Bitcoin -> parsedRecipient.amountSats
            is ParsedSendRecipient.Spark -> parsedRecipient.amountSats
            is ParsedSendRecipient.Lightning -> parsedRecipient.amountSats
            else -> null
        }
    val hasUsableSendAmount =
        isMaxMode ||
            (amountSats != null && amountSats > 0L) ||
            (recipientFixedAmountSats != null && recipientFixedAmountSats > 0L)
    val amountForSpendCheck: Long? =
        when {
            isMaxMode -> null
            amountSats != null && amountSats > 0L -> amountSats
            recipientFixedAmountSats != null && recipientFixedAmountSats > 0L -> recipientFixedAmountSats
            else -> null
        }
    val selectedOnchainFeeSats =
        if (parsedRecipient is ParsedSendRecipient.Bitcoin) {
            onchainFeeQuotes.firstOrNull { it.speed == onchainFeeSpeed }?.feeSats ?: 0L
        } else {
            0L
        }
    val totalOutSats =
        when {
            isMaxMode -> availableSats
            amountForSpendCheck == null -> 0L
            parsedRecipient is ParsedSendRecipient.Bitcoin -> amountForSpendCheck + selectedOnchainFeeSats
            else -> amountForSpendCheck
        }
    val sparkClientOverBalance =
        !isMaxMode &&
            amountForSpendCheck != null &&
            amountForSpendCheck > 0L &&
            totalOutSats > availableSats
    val sparkAmountFieldError =
        prepareError?.takeIf { it.isNotBlank() }
            ?: if (sparkClientOverBalance) {
                stringResource(
                    R.string.balance_insufficient_funds_available_format,
                    formatAmount(availableSats.toULong(), useSats, includeUnit = false),
                    if (useSats) "sats" else "BTC",
                )
            } else {
                null
            }
    val canSparkReview =
        paymentRequest.isNotBlank() &&
            recipientValidationError == null &&
            !busy &&
            prepareError == null &&
            !sparkClientOverBalance &&
            hasUsableSendAmount

    LaunchedEffect(parsedRecipient, paymentRequest, availableSats) {
        val quoteAmount = amountSats?.takeIf { it > 0L } ?: availableSats.takeIf { it > 0L }
        if (parsedRecipient is ParsedSendRecipient.Bitcoin && quoteAmount != null && recipientValidationError == null) {
            onchainFeeQuotesLoading = true
            onchainFeeQuotes =
                runCatching {
                    onLoadOnchainFeeQuotes(paymentRequest.trim(), quoteAmount, false)
                }.getOrDefault(emptyList())
            onchainFeeQuotesLoading = false
        } else {
            onchainFeeQuotesLoading = false
            onchainFeeQuotes = emptyList()
        }
    }

    fun clearSuccessfulSendDraft() {
        paymentRequest = ""
        amountInput = ""
        labelText = ""
        showLabelField = false
        isMaxMode = false
        onUpdateDraft(SendScreenDraft())
        onResetSend()
    }

    if (showConfirmDialog) {
        SparkSendConfirmationDialog(
            sendState = sendState,
            useSats = useSats,
            btcPrice = btcPrice,
            fiatCurrency = fiatCurrency,
            privacyMode = privacyMode,
            onConfirm = onSendPrepared,
            onDismiss = {
                showConfirmDialog = false
                if (sendState is SparkSendState.Sent) {
                    clearSuccessfulSendDraft()
                }
            },
            onDone = {
                showConfirmDialog = false
                clearSuccessfulSendDraft()
            },
        )
    }

    if (showQrScanner) {
        QrScannerDialog(
            onCodeScanned = { code ->
                showQrScanner = false
                val parsed = parseSendRecipient(code.trim())
                val error = layer2RecipientValidationError(parsed, Layer2Provider.SPARK)
                if (error != null) {
                    scanError = error
                } else {
                    scanError = null
                    when (parsed) {
                        is ParsedSendRecipient.Bitcoin -> {
                            paymentRequest = parsed.address
                            amountInput = parsed.amountSats?.let { formatSparkSendAmountInput(it, useSats) }.orEmpty()
                            isMaxMode = false
                        }
                        is ParsedSendRecipient.Spark -> {
                            paymentRequest = parsed.paymentRequest
                            amountInput = parsed.amountSats?.let { formatSparkSendAmountInput(it, useSats) }.orEmpty()
                            isMaxMode = false
                        }
                        is ParsedSendRecipient.Lightning -> {
                            paymentRequest = parsed.paymentInput
                            amountInput = parsed.amountSats?.let { formatSparkSendAmountInput(it, useSats) }.orEmpty()
                            isMaxMode = false
                        }
                        else -> Unit
                    }
                }
            },
            onDismiss = { showQrScanner = false },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top,
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.loc_a274c658),
                            style = MaterialTheme.typography.titleLarge,
                            color = TextPrimary,
                        )
                    }
                    SparkChipButton(
                        text = "Coin Control",
                        selected = false,
                        enabled = false,
                        onClick = {},
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.loc_eaf579ea), style = MaterialTheme.typography.labelLarge, color = TextSecondary)
                    SparkChipButton(
                        text = stringResource(R.string.loc_fcc11f52),
                        selected = false,
                        enabled = false,
                        onClick = {},
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = paymentRequest,
                    onValueChange = { input ->
                        val normalized = input.trim()
                        when (val parsed = parseSendRecipient(normalized)) {
                            is ParsedSendRecipient.Bitcoin -> {
                                paymentRequest = parsed.address
                                amountInput = parsed.amountSats?.let { formatSparkSendAmountInput(it, useSats) }.orEmpty()
                                isMaxMode = false
                            }
                            is ParsedSendRecipient.Spark -> {
                                paymentRequest = parsed.paymentRequest
                                amountInput = parsed.amountSats?.let { formatSparkSendAmountInput(it, useSats) }.orEmpty()
                                isMaxMode = false
                            }
                            is ParsedSendRecipient.Lightning -> {
                                paymentRequest = parsed.paymentInput
                                amountInput = parsed.amountSats?.let { formatSparkSendAmountInput(it, useSats) }.orEmpty()
                                isMaxMode = false
                            }
                            else -> {
                                paymentRequest = normalized
                                if (normalized.isEmpty()) amountInput = ""
                                isMaxMode = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(
                            text = stringResource(R.string.loc_ea3c0acf),
                            color = TextSecondary.copy(alpha = 0.5f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    trailingIcon = {
                        IconButton(onClick = { showQrScanner = true }) {
                            Icon(
                                imageVector = Icons.Default.QrCodeScanner,
                                contentDescription = "Scan QR Code",
                                tint = SparkPurple,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    },
                    supportingText =
                        recipientValidationError?.let {
                            {
                                Text(
                                    text = it,
                                    color = ErrorRed,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        },
                    isError = recipientValidationError != null,
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp),
                    colors = sparkTextFieldColors(),
                )
                if (recipientBadges.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        recipientBadges.forEach { badge ->
                            SparkRecipientModeBadgeChip(label = badge.first, color = badge.second)
                        }
                    }
                }
                scanError?.let {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = ErrorRed,
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AmountLabel(
                        useSats = useSats,
                        isUsdMode = isUsdMode,
                        fiatCurrency = fiatCurrency,
                        onToggleDenomination = onToggleDenomination,
                    )
                    if (btcPrice != null && btcPrice > 0) {
                        Card(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    amountInput = convertSparkAmountInput(amountSats, useSats, !isUsdMode, btcPrice)
                                    isUsdMode = !isUsdMode
                                    isMaxMode = false
                                },
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isUsdMode) SparkPurple.copy(alpha = 0.15f) else DarkSurface,
                            ),
                            border = BorderStroke(1.dp, if (isUsdMode) SparkPurple else BorderColor),
                        ) {
                            Text(
                                text = fiatCurrency,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isUsdMode) SparkPurple else TextSecondary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))

                val conversionText =
                    if (amountInput.isNotBlank() && amountSats != null && amountSats > 0 && btcPrice != null && btcPrice > 0) {
                        if (privacyMode) {
                            "≈ $SPARK_SEND_HIDDEN"
                        } else if (isUsdMode) {
                            "≈ ${formatAmount(amountSats.toULong(), useSats, includeUnit = true)}"
                        } else {
                            "≈ ${formatFiat((amountSats / 100_000_000.0) * btcPrice, fiatCurrency)}"
                        }
                    } else {
                        null
                    }

                OutlinedTextField(
                    value = amountInput,
                    onValueChange = { value ->
                        amountInput = filterSparkAmountInput(value, useSats, isUsdMode)
                        isMaxMode = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(
                            text = if (isUsdMode) "0.00" else "0",
                            color = TextSecondary.copy(alpha = 0.5f),
                        )
                    },
                    leadingIcon =
                        if (isUsdMode) {
                            { Text(fiatCurrency, color = TextSecondary) }
                        } else {
                            null
                        },
                    suffix =
                        conversionText?.let {
                            {
                                Text(
                                    text = it,
                                    color = SparkPurple,
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                            }
                        },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp),
                    colors = sparkTextFieldColors(),
                    isError = sparkAmountFieldError != null,
                )

                if (sparkAmountFieldError != null && amountInput.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = sparkAmountFieldError,
                        style = MaterialTheme.typography.bodySmall,
                        color = WarningYellow,
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Start,
                ) {
                    Text(stringResource(R.string.loc_277e2626), style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(
                        text =
                            if (privacyMode) {
                                SPARK_SEND_HIDDEN
                            } else {
                                formatAmount(availableSats.toULong(), useSats, includeUnit = true)
                            },
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                    if (btcPrice != null && btcPrice > 0 && !privacyMode) {
                        Text(
                            text =
                                " · ${
                                    formatFiat(
                                        (availableSats.toDouble() / 100_000_000.0) * btcPrice,
                                        fiatCurrency,
                                    )
                                }",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary.copy(alpha = 0.7f),
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    SparkChipButton(
                        text = "Max",
                        selected = isMaxMode,
                        enabled = availableSats > 0 && !busy,
                        onClick = {
                            amountInput = formatSparkSendAmountInput(availableSats, useSats)
                            isUsdMode = false
                            isMaxMode = true
                        },
                    )
                }

                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SparkChipButton(
                        text = stringResource(R.string.loc_cf667fec),
                        selected = showLabelField || labelText.isNotBlank(),
                        enabled = !busy,
                        onClick = { showLabelField = !showLabelField },
                    )
                    if (showLabelField) {
                        OutlinedTextField(
                            value = labelText,
                            onValueChange = { labelText = it },
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .padding(start = 8.dp),
                            placeholder = {
                                Text(
                                    stringResource(R.string.loc_642fdbfc),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary.copy(alpha = 0.5f),
                                )
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp),
                            colors = sparkTextFieldColors(),
                            textStyle = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }

                if (parsedRecipient is ParsedSendRecipient.Bitcoin) {
                    Spacer(modifier = Modifier.height(16.dp))
                    SparkOnchainFeeSpeedSection(
                        selectedSpeed = onchainFeeSpeed,
                        feeQuotes = onchainFeeQuotes,
                        isLoading = onchainFeeQuotesLoading,
                        privacyMode = privacyMode,
                        onSpeedSelected = { onchainFeeSpeed = it },
                        enabled = !busy,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        Button(
            onClick = {
                val amountForPrepare: Long? =
                    when {
                        isMaxMode -> null
                        amountSats != null && amountSats > 0L -> amountSats
                        else -> recipientFixedAmountSats?.takeIf { it > 0L }
                    }
                showConfirmDialog = true
                onPrepareSend(paymentRequest.trim(), amountForPrepare, onchainFeeSpeed, isMaxMode)
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(8.dp),
            enabled = canSparkReview,
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = SparkPurple,
                    disabledContainerColor = SparkPurple.copy(alpha = 0.3f),
                ),
        ) {
            Text(
                text = stringResource(R.string.loc_81f5c0cf),
                style = MaterialTheme.typography.titleMedium,
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun SparkChipButton(
    text: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier =
            Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    when {
                        selected -> SparkPurple.copy(alpha = 0.15f)
                        enabled -> DarkSurface
                        else -> DarkSurface.copy(alpha = 0.6f)
                    },
            ),
        border = BorderStroke(1.dp, if (selected) SparkPurple else BorderColor.copy(alpha = if (enabled) 1f else 0.5f)),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color =
                when {
                    selected -> SparkPurple
                    enabled -> TextSecondary
                    else -> TextSecondary.copy(alpha = 0.5f)
                },
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun SparkRecipientModeBadgeChip(
    label: String,
    color: Color,
) {
    Box(
        modifier =
            Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(color.copy(alpha = 0.14f))
                .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
        )
    }
}

@Composable
private fun SparkOnchainFeeSpeedSection(
    selectedSpeed: SparkOnchainFeeSpeed,
    feeQuotes: List<SparkOnchainFeeQuote>,
    isLoading: Boolean,
    privacyMode: Boolean,
    onSpeedSelected: (SparkOnchainFeeSpeed) -> Unit,
    enabled: Boolean,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.loc_943c89b7),
                style = MaterialTheme.typography.labelLarge,
                color = TextSecondary,
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            SparkFeeTargetButton(
                label = "~1 block",
                feeSats = feeQuotes.firstOrNull { it.speed == SparkOnchainFeeSpeed.FAST }?.feeSats,
                isSelected = selectedSpeed == SparkOnchainFeeSpeed.FAST,
                onClick = { onSpeedSelected(SparkOnchainFeeSpeed.FAST) },
                enabled = enabled,
                isLoading = isLoading,
                privacyMode = privacyMode,
                modifier = Modifier.weight(1f),
            )
            SparkFeeTargetButton(
                label = "~3 blocks",
                feeSats = feeQuotes.firstOrNull { it.speed == SparkOnchainFeeSpeed.MEDIUM }?.feeSats,
                isSelected = selectedSpeed == SparkOnchainFeeSpeed.MEDIUM,
                onClick = { onSpeedSelected(SparkOnchainFeeSpeed.MEDIUM) },
                enabled = enabled,
                isLoading = isLoading,
                privacyMode = privacyMode,
                modifier = Modifier.weight(1f),
            )
            SparkFeeTargetButton(
                label = "~6 blocks",
                feeSats = feeQuotes.firstOrNull { it.speed == SparkOnchainFeeSpeed.SLOW }?.feeSats,
                isSelected = selectedSpeed == SparkOnchainFeeSpeed.SLOW,
                onClick = { onSpeedSelected(SparkOnchainFeeSpeed.SLOW) },
                enabled = enabled,
                isLoading = isLoading,
                privacyMode = privacyMode,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun SparkFeeTargetButton(
    label: String,
    feeSats: Long?,
    isSelected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean,
    isLoading: Boolean,
    privacyMode: Boolean,
    modifier: Modifier = Modifier,
) {
    val backgroundColor = if (isSelected) SparkPurple.copy(alpha = 0.15f) else DarkSurface
    val borderColor = if (isSelected) SparkPurple else BorderColor
    val textColor = if (isSelected) SparkPurple else TextSecondary

    Card(
        modifier =
            modifier
                .clip(RoundedCornerShape(12.dp))
                .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        border = BorderStroke(1.dp, borderColor),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp, horizontal = 0.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = if (isSelected) MaterialTheme.colorScheme.onBackground else TextSecondary,
                textAlign = TextAlign.Center,
            )
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = SparkPurple,
                    strokeWidth = 2.dp,
                )
            } else {
                Text(
                    text = feeSats?.let {
                        if (privacyMode) SPARK_SEND_HIDDEN else String.format(Locale.US, "%,d", it)
                    } ?: "—",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = textColor,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(R.string.loc_9384ed0d),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

private fun sparkRecipientModeBadges(parsed: ParsedSendRecipient): List<Pair<String, Color>> =
    when (parsed) {
        is ParsedSendRecipient.Bitcoin -> listOf("Bitcoin" to BitcoinOrange)
        is ParsedSendRecipient.Lightning -> listOf("Lightning" to LightningYellow)
        is ParsedSendRecipient.Spark -> listOf("Spark" to SparkPurple)
        else -> emptyList()
    }

@Composable
private fun SparkSendConfirmationDialog(
    sendState: SparkSendState,
    useSats: Boolean,
    btcPrice: Double?,
    fiatCurrency: String,
    privacyMode: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    onDone: () -> Unit,
) {
    ScrollableDialogSurface(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        containerColor = DarkSurface,
        actions = {
            when (sendState) {
                SparkSendState.Idle,
                SparkSendState.Preparing,
                -> {
                    Button(
                        onClick = { },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                        shape = RoundedCornerShape(8.dp),
                        enabled = false,
                        colors = ButtonDefaults.buttonColors(containerColor = SparkPurple, contentColor = DarkBackground),
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = DarkBackground,
                            strokeWidth = 2.dp,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.loc_68504b99), style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = BorderColor)
                    Spacer(modifier = Modifier.height(8.dp))
                    IbisButton(
                        onClick = onDismiss,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                    ) {
                        Text(stringResource(R.string.loc_51bac044), style = MaterialTheme.typography.titleMedium)
                    }
                }
                is SparkSendState.Preview -> {
                    Button(
                        onClick = onConfirm,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SparkPurple, contentColor = DarkBackground),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.loc_a274c658), style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = BorderColor)
                    Spacer(modifier = Modifier.height(8.dp))
                    IbisButton(
                        onClick = onDismiss,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                    ) {
                        Text(stringResource(R.string.loc_51bac044), style = MaterialTheme.typography.titleMedium)
                    }
                }
                SparkSendState.Sending -> {
                    Button(
                        onClick = { },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                        shape = RoundedCornerShape(8.dp),
                        enabled = false,
                        colors = ButtonDefaults.buttonColors(containerColor = SparkPurple, contentColor = DarkBackground),
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = DarkBackground,
                            strokeWidth = 2.dp,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.loc_57cf7ca4), style = MaterialTheme.typography.titleMedium)
                    }
                }
                is SparkSendState.Error,
                is SparkSendState.Sent,
                -> {
                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = BorderColor)
                    Spacer(modifier = Modifier.height(8.dp))
                    IbisButton(
                        onClick = if (sendState is SparkSendState.Sent) onDone else onDismiss,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                    ) {
                        Text(stringResource(R.string.loc_d2c0aec0), style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        },
    ) {
        Text(
            text = stringResource(R.string.loc_81f5c0cf),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
        )
        Spacer(modifier = Modifier.height(16.dp))
        when (sendState) {
            SparkSendState.Idle,
            SparkSendState.Preparing,
            -> Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    color = SparkPurple,
                    strokeWidth = 2.dp,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.loc_b86dbd12),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary,
                    textAlign = TextAlign.Center,
                )
            }
            is SparkSendState.Preview -> SparkSendReviewContent(
                preview = sendState,
                useSats = useSats,
                btcPrice = btcPrice,
                fiatCurrency = fiatCurrency,
                privacyMode = privacyMode,
            )
            SparkSendState.Sending -> SparkSendReviewContent(
                preview = null,
                useSats = useSats,
                btcPrice = btcPrice,
                fiatCurrency = fiatCurrency,
                privacyMode = privacyMode,
            )
            is SparkSendState.Sent -> {
                Text(
                    text = stringResource(R.string.loc_d90f9485),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                )
                sendState.paymentId?.let {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = it,
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            is SparkSendState.Error -> Text(
                text = sendState.message,
                style = MaterialTheme.typography.bodyMedium,
                color = ErrorRed,
            )
        }
    }
}

@Composable
private fun SparkSendReviewContent(
    preview: SparkSendState.Preview?,
    useSats: Boolean,
    btcPrice: Double?,
    fiatCurrency: String,
    privacyMode: Boolean,
) {
    if (preview == null) {
        Text(
            text = stringResource(R.string.loc_d91923f1),
            style = MaterialTheme.typography.bodyMedium,
            color = TextPrimary,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
        return
    }

    Text(
        text = stringResource(R.string.loc_895ab1d4),
        style = MaterialTheme.typography.bodyLarge,
        color = TextSecondary,
    )
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = abbreviateSparkReviewText(preview.paymentRequest),
        style = MaterialTheme.typography.bodyLarge,
        fontFamily = FontFamily.Monospace,
        color = TextPrimary,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
    )

    Spacer(modifier = Modifier.height(16.dp))
    SparkReviewDivider()
    Spacer(modifier = Modifier.height(16.dp))

    val amountSats = preview.amountSats ?: 0L
    val feeSats = preview.feeSats ?: 0L
    if (preview.amountSats != null) {
        SparkReviewAmountRow(
            label = "Sending:",
            subtitle = abbreviateSparkReviewText(preview.paymentRequest, prefix = 8, suffix = 8),
            valueText = sparkReviewAmountText(amountSats, useSats, privacyMode),
            valueSubtext = sparkReviewUsdSubtext(amountSats, btcPrice, fiatCurrency, privacyMode),
            color = AccentRed,
        )
        Spacer(modifier = Modifier.height(12.dp))
    }

    if (preview.feeSats != null) {
        SparkReviewAmountRow(
            label = sparkReviewFeeLabel(preview.method),
            subtitle = preview.onchainFeeSpeed?.displayName(),
            valueText = sparkReviewAmountText(feeSats, useSats, privacyMode),
            valueSubtext = sparkReviewUsdSubtext(feeSats, btcPrice, fiatCurrency, privacyMode),
            color = sparkReviewFeeColor(preview.method),
        )
        Spacer(modifier = Modifier.height(12.dp))
    }

    if (preview.amountSats != null || preview.feeSats != null) {
        SparkReviewDivider()
        Spacer(modifier = Modifier.height(16.dp))
        SparkReviewAmountRow(
            label = "Total",
            valueText = sparkReviewAmountText(amountSats + feeSats, useSats, privacyMode),
            valueSubtext = sparkReviewUsdSubtext(amountSats + feeSats, btcPrice, fiatCurrency, privacyMode),
            color = AccentRed,
            bold = true,
        )
    }
}

@Composable
private fun SparkReviewAmountRow(
    label: String,
    valueText: String,
    color: Color,
    subtitle: String? = null,
    valueSubtext: String? = null,
    bold: Boolean = false,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .padding(end = 16.dp),
        ) {
            Text(
                text = label,
                style = if (bold) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyLarge,
                fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
                color = TextSecondary,
            )
            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextSecondary.copy(alpha = 0.7f),
                )
            }
        }
        Column(
            modifier = Modifier.widthIn(min = 96.dp),
            horizontalAlignment = Alignment.End,
        ) {
            Text(
                text = valueText,
                style = if (bold) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
                fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium,
                color = color,
                textAlign = TextAlign.End,
                softWrap = false,
            )
            valueSubtext?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextTertiary,
                    textAlign = TextAlign.End,
                )
            }
        }
    }
}

@Composable
private fun SparkReviewDivider() {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(BorderColor),
    )
}

private fun sparkReviewAmountText(
    value: Long,
    useSats: Boolean,
    privacyMode: Boolean,
): String =
    if (privacyMode) {
        SPARK_SEND_HIDDEN
    } else {
        "-${formatAmount(value.toULong(), useSats, includeUnit = true)}"
    }

private fun sparkReviewUsdSubtext(
    value: Long,
    btcPrice: Double?,
    fiatCurrency: String,
    privacyMode: Boolean,
): String? {
    if (privacyMode || btcPrice == null || btcPrice <= 0.0) return null
    return formatFiat((value.toDouble() / 100_000_000.0) * btcPrice, fiatCurrency)
}

private fun sparkReviewFeeLabel(method: String): String =
    when {
        method.contains("Bitcoin", ignoreCase = true) -> "Bitcoin Network Fee:"
        method.contains("Bolt", ignoreCase = true) || method.contains("Lightning", ignoreCase = true) -> "Lightning Network Fee:"
        else -> "Spark Network Fee:"
    }

private fun sparkReviewFeeColor(method: String): Color =
    when {
        method.contains("Bitcoin", ignoreCase = true) -> BitcoinOrange
        method.contains("Bolt", ignoreCase = true) || method.contains("Lightning", ignoreCase = true) -> LightningYellow
        else -> SparkPurple
    }

private fun SparkOnchainFeeSpeed.displayName(): String =
    name.lowercase(Locale.US).replaceFirstChar { it.titlecase(Locale.US) }

private fun abbreviateSparkReviewText(
    value: String,
    prefix: Int = 12,
    suffix: Int = 8,
): String =
    if (value.length <= prefix + suffix + 3) {
        value
    } else {
        value.take(prefix) + "..." + value.takeLast(suffix)
    }

@Composable
private fun ReviewRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = TextSecondary, modifier = Modifier.weight(1f))
        Text(value, color = TextPrimary)
    }
}

private fun filterSparkAmountInput(
    value: String,
    useSats: Boolean,
    isUsdMode: Boolean,
): String {
    var v = value.replace(",", "")
    val pattern =
        when {
            isUsdMode -> Regex("^\\d*\\.?\\d{0,2}$")
            useSats -> Regex("^\\d*$")
            else -> Regex("^\\d*\\.?\\d{0,8}$")
        }
    while (v.isNotEmpty() && !v.matches(pattern)) {
        v = v.dropLast(1)
    }
    return v
}

private fun parseSparkSendAmount(
    input: String,
    useSats: Boolean,
    isUsdMode: Boolean,
    btcPrice: Double?,
): Long? {
    val trimmed = input.trim().replace(",", "")
    if (trimmed.isBlank()) return null
    return when {
        isUsdMode && btcPrice != null && btcPrice > 0 ->
            trimmed.toDoubleOrNull()?.let { ((it / btcPrice) * 100_000_000).toLong() }
        useSats -> trimmed.toLongOrNull()
        else -> trimmed.toDoubleOrNull()?.let { (it * 100_000_000).toLong() }
    }?.takeIf { it > 0 }
}

private fun convertSparkAmountInput(
    amountSats: Long?,
    useSats: Boolean,
    toUsdMode: Boolean,
    btcPrice: Double,
): String {
    val sats = amountSats ?: return ""
    return if (toUsdMode) {
        String.format(Locale.US, "%.2f", (sats / 100_000_000.0) * btcPrice)
    } else if (useSats) {
        sats.toString()
    } else {
        String.format(Locale.US, "%.8f", sats / 100_000_000.0)
            .trimEnd('0')
            .trimEnd('.')
    }
}

private fun formatSparkSendAmountInput(
    amountSats: Long,
    useSats: Boolean,
): String =
    if (useSats) {
        amountSats.toString()
    } else {
        String.format(Locale.US, "%.8f", amountSats / 100_000_000.0)
            .trimEnd('0')
            .trimEnd('.')
    }

private const val SPARK_SEND_HIDDEN = "****"
