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
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import github.aeonbtc.ibiswallet.MainActivity
import github.aeonbtc.ibiswallet.R
import github.aeonbtc.ibiswallet.data.local.SecureStorage
import github.aeonbtc.ibiswallet.data.model.ArkSendState
import github.aeonbtc.ibiswallet.data.model.Layer2Provider
import github.aeonbtc.ibiswallet.nfc.NfcReaderUiState
import github.aeonbtc.ibiswallet.nfc.NfcRuntimeStatus
import github.aeonbtc.ibiswallet.ui.components.AmountLabel
import github.aeonbtc.ibiswallet.ui.components.AvailableBalanceMaxRow
import github.aeonbtc.ibiswallet.ui.components.IbisButton
import github.aeonbtc.ibiswallet.ui.components.NfcStatusIndicator
import github.aeonbtc.ibiswallet.ui.components.QrScannerDialog
import github.aeonbtc.ibiswallet.ui.components.ScrollableDialogSurface
import github.aeonbtc.ibiswallet.ui.theme.AccentRed
import github.aeonbtc.ibiswallet.ui.theme.ArkRust
import github.aeonbtc.ibiswallet.ui.theme.BitcoinOrange
import github.aeonbtc.ibiswallet.ui.theme.BorderColor
import github.aeonbtc.ibiswallet.ui.theme.DarkBackground
import github.aeonbtc.ibiswallet.ui.theme.DarkCard
import github.aeonbtc.ibiswallet.ui.theme.DarkSurface
import github.aeonbtc.ibiswallet.ui.theme.ErrorRed
import github.aeonbtc.ibiswallet.ui.theme.LightningYellow
import github.aeonbtc.ibiswallet.ui.theme.SuccessGreen
import github.aeonbtc.ibiswallet.ui.theme.TextPrimary
import github.aeonbtc.ibiswallet.ui.theme.TextSecondary
import github.aeonbtc.ibiswallet.ui.theme.TextTertiary
import github.aeonbtc.ibiswallet.ui.theme.WarningYellow
import github.aeonbtc.ibiswallet.util.LightningKind
import github.aeonbtc.ibiswallet.util.ParsedSendRecipient
import github.aeonbtc.ibiswallet.util.SilentPayment
import github.aeonbtc.ibiswallet.util.getNfcAvailability
import github.aeonbtc.ibiswallet.util.isLightningAddressPayment
import github.aeonbtc.ibiswallet.util.layer2RecipientValidationError
import github.aeonbtc.ibiswallet.util.parseSendRecipient
import github.aeonbtc.ibiswallet.viewmodel.SendScreenDraft
import java.util.Locale
import kotlin.math.roundToLong

@Composable
fun ArkSendScreen(
    draft: SendScreenDraft,
    sendState: ArkSendState,
    denomination: String,
    btcPrice: Double?,
    fiatCurrency: String,
    privacyMode: Boolean,
    availableSats: Long,
    onUpdateDraft: (SendScreenDraft) -> Unit,
    onPrepareSend: (String, Long?, Boolean, String?) -> Unit,
    onPrepareSendMany: (List<Pair<String, Long>>, String?) -> Unit = { _, _ -> },
    onSendPrepared: () -> Unit,
    onSendPreparedMany: () -> Unit = {},
    onResetSend: () -> Unit,
    onToggleDenomination: () -> Unit,
) {
    val context = LocalContext.current
    val mainActivity = context as? MainActivity
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
    var isMaxMode by remember { mutableStateOf(draft.isMaxSend) }
    var isMultiMode by remember { mutableStateOf(false) }
    var showMultiDialog by remember { mutableStateOf(false) }
    val multiRecipients = remember { mutableStateListOf<Pair<String, String>>() }

    val nfcReaderOwner = remember { Any() }
    val nfcAvailable = context.getNfcAvailability().canRead
    DisposableEffect(mainActivity) {
        if (mainActivity != null && nfcAvailable) {
            mainActivity.requestNfcReaderMode(nfcReaderOwner)
        }
        onDispose {
            mainActivity?.releaseNfcReaderMode(nfcReaderOwner)
        }
    }
    val isNfcReaderActive = nfcAvailable && mainActivity?.isNfcReaderModeActive == true
    val nfcReaderState by NfcRuntimeStatus.readerState.collectAsState()

    LaunchedEffect(draft) {
        if (showConfirmDialog) return@LaunchedEffect
        if (draft.recipientAddress.isNotBlank()) paymentRequest = draft.recipientAddress
        if (draft.amountInput.isNotBlank()) amountInput = draft.amountInput
        if (draft.label.isNotBlank()) {
            labelText = draft.label
            showLabelField = true
        }
        isMaxMode = draft.isMaxSend
    }

    LaunchedEffect(paymentRequest, amountInput, labelText, isMaxMode) {
        val updatedDraft =
            SendScreenDraft(
                recipientAddress = paymentRequest,
                amountInput = amountInput,
                label = labelText,
                isMaxSend = isMaxMode,
            )
        if (updatedDraft != draft) {
            onUpdateDraft(updatedDraft)
        }
    }

    val amountSats =
        remember(amountInput, denomination, isUsdMode, btcPrice) {
            parseArkSendAmount(amountInput, useSats, isUsdMode, btcPrice)
        }
    val parsedRecipient = parseSendRecipient(paymentRequest.trim(), context)
    val recipientValidationError =
        remember(parsedRecipient, paymentRequest, context) {
            if (paymentRequest.isBlank()) {
                null
            } else {
                layer2RecipientValidationError(parsedRecipient, Layer2Provider.ARK, context = context)
            }
        }
    val recipientBadges = remember(parsedRecipient) { arkRecipientModeBadges(parsedRecipient) }
    val busy =
        sendState is ArkSendState.Preparing ||
            sendState is ArkSendState.Sending ||
            sendState is ArkSendState.MultiSending
    val amountLockedByInvoice =
        when (val p = parsedRecipient) {
            is ParsedSendRecipient.Lightning ->
                p.kind == LightningKind.BOLT11 && p.amountSats != null && p.amountSats > 0L
            else -> false
        }

    fun parseMultiAmountToSats(raw: String): Long? {
        val t = raw.trim()
        if (t.isEmpty()) return null
        return when {
            isUsdMode -> {
                val usd = t.toDoubleOrNull() ?: return null
                if (btcPrice == null || btcPrice <= 0) return null
                ((usd / btcPrice) * 100_000_000.0).roundToLong().takeIf { it > 0L }
            }
            useSats -> t.toLongOrNull()?.takeIf { it > 0L }
            else -> {
                val btc = t.toDoubleOrNull() ?: return null
                (btc * 100_000_000.0).roundToLong().takeIf { it > 0L }
            }
        }
    }

    val multiRecipientPairs =
        remember(multiRecipients.toList(), useSats, isUsdMode, btcPrice) {
            multiRecipients.mapNotNull { (addr, amt) ->
                val a = addr.trim()
                val sats = parseMultiAmountToSats(amt) ?: return@mapNotNull null
                if (a.isEmpty() || parseSendRecipient(a, context) !is ParsedSendRecipient.Ark) {
                    return@mapNotNull null
                }
                a to sats
            }
        }
    val multiTotalSats = multiRecipientPairs.sumOf { it.second }

    LaunchedEffect(amountInput, paymentRequest, isMaxMode, parsedRecipient, isMultiMode) {
        prepareError = null
    }

    LaunchedEffect(sendState) {
        when (sendState) {
            is ArkSendState.Preparing -> prepareError = null
            is ArkSendState.Error -> {
                // Keep confirm dialog open so failure is visible (Liquid-style).
                // Also mirror onto the form if the user already closed the dialog.
                if (!showConfirmDialog) {
                    prepareError = sendState.message
                }
            }
            is ArkSendState.Preview,
            is ArkSendState.MultiPreview,
            -> prepareError = null
            else -> Unit
        }
    }

    // Autofill fixed amounts from recipient (invoice/BIP21) when field empty.
    LaunchedEffect(parsedRecipient) {
        val fixed =
            when (parsedRecipient) {
                is ParsedSendRecipient.Bitcoin -> parsedRecipient.amountSats
                is ParsedSendRecipient.Ark -> parsedRecipient.amountSats
                is ParsedSendRecipient.Lightning -> parsedRecipient.amountSats
                else -> null
            }
        if (fixed != null && fixed > 0L && amountInput.isBlank() && !isMaxMode) {
            amountInput = formatArkSendAmountInput(fixed, useSats)
            isUsdMode = false
        }
        val draftLabel =
            when (parsedRecipient) {
                is ParsedSendRecipient.Bitcoin -> parsedRecipient.label
                is ParsedSendRecipient.Ark -> parsedRecipient.label
                else -> null
            }
        if (!draftLabel.isNullOrBlank() && labelText.isBlank()) {
            labelText = draftLabel
            showLabelField = true
        }
    }

    val recipientFixedAmountSats: Long? =
        when (parsedRecipient) {
            is ParsedSendRecipient.Bitcoin -> parsedRecipient.amountSats
            is ParsedSendRecipient.Ark -> parsedRecipient.amountSats
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
    val arkClientOverBalance =
        !isMaxMode &&
            amountForSpendCheck != null &&
            amountForSpendCheck > 0L &&
            amountForSpendCheck > availableSats
    val arkAmountFieldError =
        prepareError?.takeIf { it.isNotBlank() }
            ?: if (arkClientOverBalance) {
                stringResource(
                    R.string.balance_insufficient_funds_available_format,
                    formatAmount(availableSats.toULong(), useSats, includeUnit = false),
                    if (useSats) "sats" else "BTC",
                )
            } else {
                null
            }
    val canArkReview =
        if (isMultiMode) {
            multiRecipientPairs.size >= 2 &&
                multiTotalSats > 0L &&
                multiTotalSats <= availableSats &&
                !busy &&
                prepareError == null
        } else {
            paymentRequest.isNotBlank() &&
                recipientValidationError == null &&
                !busy &&
                prepareError == null &&
                !arkClientOverBalance &&
                hasUsableSendAmount
        }

    fun applyParsedRecipient(parsed: ParsedSendRecipient) {
        when (parsed) {
            is ParsedSendRecipient.Bitcoin -> {
                paymentRequest = parsed.address
                // Only fill amount when URI supplies one and the field is blank — never wipe user input.
                if (parsed.amountSats != null && amountInput.isBlank()) {
                    amountInput = formatArkSendAmountInput(parsed.amountSats, useSats)
                    isUsdMode = false
                }
                parsed.label?.takeIf { it.isNotBlank() }?.let {
                    labelText = it
                    showLabelField = true
                }
                isMaxMode = false
            }
            is ParsedSendRecipient.Ark -> {
                paymentRequest = parsed.address
                if (parsed.amountSats != null && amountInput.isBlank()) {
                    amountInput = formatArkSendAmountInput(parsed.amountSats, useSats)
                    isUsdMode = false
                }
                parsed.label?.takeIf { it.isNotBlank() }?.let {
                    labelText = it
                    showLabelField = true
                }
                isMaxMode = false
            }
            is ParsedSendRecipient.Lightning -> {
                paymentRequest = parsed.paymentInput
                if (parsed.amountSats != null) {
                    // Invoice-locked amount always wins over a user draft.
                    amountInput = formatArkSendAmountInput(parsed.amountSats, useSats)
                    isUsdMode = false
                }
                isMaxMode = false
            }
            else -> Unit
        }
    }

    fun clearSuccessfulSendDraft() {
        paymentRequest = ""
        amountInput = ""
        labelText = ""
        showLabelField = false
        isMaxMode = false
        isUsdMode = false
        isMultiMode = false
        multiRecipients.clear()
        onUpdateDraft(SendScreenDraft())
        onResetSend()
    }

    if (showMultiDialog) {
        val arkOnlyError = stringResource(R.string.send_multi_ark_only)
        MultiRecipientDialog(
            recipients = multiRecipients,
            useSats = useSats,
            isUsdMode = isUsdMode,
            btcPrice = btcPrice,
            fiatCurrency = fiatCurrency,
            privacyMode = privacyMode,
            availableSats = availableSats,
            estimatedFeeSats = null,
            dryRunError = prepareError,
            validRecipientCount = multiRecipientPairs.size,
            totalSendingSats = multiTotalSats,
            accentColor = ArkRust,
            addressValidator = { addr ->
                val p = parseSendRecipient(addr.trim(), context)
                when {
                    addr.isBlank() -> null
                    p is ParsedSendRecipient.Ark -> null
                    else -> arkOnlyError
                }
            },
            parseScannedCode = { code ->
                when (val p = parseSendRecipient(code, context)) {
                    is ParsedSendRecipient.Ark -> p.address to p.amountSats
                    else -> code to null
                }
            },
            sequentialNote = stringResource(R.string.send_multi_sequential_note),
            addressPlaceholder = stringResource(R.string.send_multi_ark_address_hint),
            onDone = { showMultiDialog = false },
            onDismiss = { showMultiDialog = false },
        )
    }

    if (showConfirmDialog) {
        ArkSendConfirmationDialog(
            sendState = sendState,
            useSats = useSats,
            btcPrice = btcPrice,
            fiatCurrency = fiatCurrency,
            privacyMode = privacyMode,
            onConfirm = {
                when (sendState) {
                    is ArkSendState.MultiPreview -> onSendPreparedMany()
                    else -> onSendPrepared()
                }
            },
            onDismiss = {
                showConfirmDialog = false
                when (sendState) {
                    is ArkSendState.Sent,
                    is ArkSendState.MultiSent,
                    -> clearSuccessfulSendDraft()
                    is ArkSendState.Error -> {
                        prepareError = sendState.message
                        onResetSend()
                    }
                    is ArkSendState.Sending,
                    is ArkSendState.MultiSending,
                    -> {
                        // Allow closing while pay is in flight; result lands in history.
                    }
                    else -> onResetSend()
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
                val parsed = parseSendRecipient(code.trim(), context)
                val error = layer2RecipientValidationError(parsed, Layer2Provider.ARK, context = context)
                if (error != null) {
                    scanError = error
                } else {
                    scanError = null
                    onResetSend()
                    prepareError = null
                    applyParsedRecipient(parsed)
                }
            },
            onDismiss = { showQrScanner = false },
        )
    }

    Column(
        modifier =
            Modifier
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
                        if (isNfcReaderActive) {
                            val nfcStatusLabel =
                                when (nfcReaderState) {
                                    NfcReaderUiState.Inactive,
                                    NfcReaderUiState.Ready,
                                        -> stringResource(R.string.nfc_status_receive_ready)
                                    NfcReaderUiState.Detecting -> stringResource(R.string.nfc_status_detecting)
                                    NfcReaderUiState.Received -> stringResource(R.string.nfc_status_received)
                                }
                            val nfcStatusColor =
                                if (nfcReaderState == NfcReaderUiState.Detecting) {
                                    ArkRust
                                } else {
                                    SuccessGreen
                                }
                            NfcStatusIndicator(
                                label = nfcStatusLabel,
                                contentDescription = nfcStatusLabel,
                                modifier = Modifier.padding(top = 2.dp),
                                color = nfcStatusColor,
                            )
                        }
                    }
                    ArkChipButton(
                        text = stringResource(R.string.loc_002b1ce2),
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
                    Text(
                        stringResource(R.string.loc_eaf579ea),
                        style = MaterialTheme.typography.labelLarge,
                        color = TextSecondary,
                    )
                    ArkChipButton(
                        text =
                            if (isMultiMode) {
                                "${stringResource(R.string.loc_fcc11f52)} (${multiRecipientPairs.size})"
                            } else {
                                stringResource(R.string.loc_fcc11f52)
                            },
                        selected = isMultiMode,
                        enabled = !busy,
                        onClick = {
                            if (!isMultiMode) {
                                isMultiMode = true
                                isMaxMode = false
                                multiRecipients.clear()
                                if (paymentRequest.isNotBlank() && amountInput.isNotBlank()) {
                                    multiRecipients.add(paymentRequest to amountInput)
                                }
                                multiRecipients.add("" to "")
                                if (multiRecipients.size < 2) multiRecipients.add("" to "")
                                showMultiDialog = true
                            } else {
                                isMultiMode = false
                                multiRecipients.clear()
                            }
                        },
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                if (isMultiMode) {
                    Card(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !busy) { showMultiDialog = true },
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = DarkSurface),
                        border = BorderStroke(1.dp, ArkRust),
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = stringResource(R.string.send_recipients_title_format, multiRecipientPairs.size),
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextPrimary,
                                fontWeight = FontWeight.Medium,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = stringResource(R.string.send_multi_sequential_note),
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                            )
                            if (multiTotalSats > 0L) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text =
                                        if (privacyMode) {
                                            ARK_HIDDEN_AMOUNT
                                        } else {
                                            formatAmount(multiTotalSats.toULong(), useSats, includeUnit = true)
                                        },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = ArkRust,
                                )
                            }
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = paymentRequest,
                        onValueChange = { input ->
                            val normalized = input.trim()
                            when (val parsed = parseSendRecipient(normalized, context)) {
                                is ParsedSendRecipient.Bitcoin,
                                is ParsedSendRecipient.Ark,
                                is ParsedSendRecipient.Lightning,
                                -> {
                                    scanError = null
                                    applyParsedRecipient(parsed)
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
                                text = stringResource(R.string.ark_send_destination_hint),
                                color = TextSecondary.copy(alpha = 0.5f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        trailingIcon = {
                            IconButton(onClick = { showQrScanner = true }) {
                                Icon(
                                    imageVector = Icons.Default.QrCodeScanner,
                                    contentDescription = stringResource(R.string.loc_59b2cdc5),
                                    tint = ArkRust,
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
                        colors = arkSendTextFieldColors(),
                    )
                    if (recipientBadges.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            recipientBadges.forEach { badge ->
                                ArkRecipientModeBadgeChip(label = badge.first, color = badge.second)
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
                }

                if (!isMultiMode) {
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
                            modifier =
                                Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable(enabled = !amountLockedByInvoice) {
                                        amountInput =
                                            convertArkAmountInput(amountSats, useSats, !isUsdMode, btcPrice)
                                        isUsdMode = !isUsdMode
                                        isMaxMode = false
                                    },
                            shape = RoundedCornerShape(8.dp),
                            colors =
                                CardDefaults.cardColors(
                                    containerColor =
                                        if (isUsdMode) ArkRust.copy(alpha = 0.15f) else DarkSurface,
                                ),
                            border = BorderStroke(1.dp, if (isUsdMode) ArkRust else BorderColor),
                        ) {
                            Text(
                                text = fiatCurrency,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isUsdMode) ArkRust else TextSecondary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))

                val conversionText =
                    if (amountInput.isNotBlank() && amountSats != null && amountSats > 0 && btcPrice != null && btcPrice > 0) {
                        if (privacyMode) {
                            "≈ $ARK_HIDDEN_AMOUNT"
                        } else if (isUsdMode) {
                            "≈ ${formatAmount(amountSats.toULong(), useSats, includeUnit = true)}"
                        } else {
                            "≈ ${formatFiat((amountSats / 100_000_000.0) * btcPrice, fiatCurrency)}"
                        }
                    } else {
                        null
                    }

                OutlinedTextField(
                    value =
                        if (isMaxMode && !privacyMode && amountSats == null) {
                            formatArkSendAmountInput(availableSats, useSats)
                        } else if (isMaxMode && privacyMode) {
                            ARK_HIDDEN_AMOUNT
                        } else {
                            amountInput
                        },
                    onValueChange = { value ->
                        if (amountLockedByInvoice) return@OutlinedTextField
                        amountInput = filterArkAmountInput(value, useSats, isUsdMode)
                        isMaxMode = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !amountLockedByInvoice && !(isMaxMode && privacyMode),
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
                                    color = ArkRust,
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                            }
                        },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp),
                    colors = arkSendTextFieldColors(),
                    isError = arkAmountFieldError != null,
                )

                if (arkAmountFieldError != null && (amountInput.isNotBlank() || isMaxMode || prepareError != null)) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = arkAmountFieldError,
                        style = MaterialTheme.typography.bodySmall,
                        color = WarningYellow,
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                AvailableBalanceMaxRow(
                    amountText =
                        if (privacyMode) {
                            ARK_HIDDEN_AMOUNT
                        } else {
                            formatAmount(availableSats.toULong(), useSats, includeUnit = true)
                        },
                    fiatText =
                        if (btcPrice != null && btcPrice > 0 && !privacyMode) {
                            val usdValue = (availableSats.toDouble() / 100_000_000.0) * btcPrice
                            " · ${formatFiat(usdValue, fiatCurrency)}"
                        } else {
                            null
                        },
                    accentColor = ArkRust,
                    isMaxMode = isMaxMode,
                    maxEnabled = availableSats > 0 && !busy && !amountLockedByInvoice,
                    fadeWhenDisabled = true,
                    onMaxClick = {
                        amountInput = formatArkSendAmountInput(availableSats, useSats)
                        isUsdMode = false
                        isMaxMode = true
                    },
                )
                } // end !isMultiMode amount section

                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ArkChipButton(
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
                            colors = arkSendTextFieldColors(),
                            textStyle = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }

                if (!isMultiMode &&
                    parsedRecipient is ParsedSendRecipient.Bitcoin &&
                    !SilentPayment.isSilentPaymentAddress(parsedRecipient.address)
                ) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.ark_send_onchain_fee_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextTertiary,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        Button(
            onClick = {
                val label = labelText.trim().takeIf { it.isNotBlank() }
                showConfirmDialog = true
                if (isMultiMode) {
                    onPrepareSendMany(multiRecipientPairs, label)
                } else {
                    val amountForPrepare: Long? =
                        when {
                            isMaxMode -> null
                            amountSats != null && amountSats > 0L -> amountSats
                            else -> recipientFixedAmountSats?.takeIf { it > 0L }
                        }
                    onPrepareSend(
                        paymentRequest.trim(),
                        amountForPrepare,
                        isMaxMode,
                        label,
                    )
                }
            },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(48.dp),
            shape = RoundedCornerShape(8.dp),
            enabled = canArkReview,
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = ArkRust,
                    disabledContainerColor = ArkRust.copy(alpha = 0.3f),
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
private fun arkSendTextFieldColors() =
    OutlinedTextFieldDefaults.colors(
        focusedBorderColor = ArkRust,
        unfocusedBorderColor = BorderColor,
        focusedTextColor = TextPrimary,
        unfocusedTextColor = TextPrimary,
        cursorColor = ArkRust,
        disabledBorderColor = BorderColor,
        disabledTextColor = TextPrimary,
    )

@Composable
private fun ArkChipButton(
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
                        selected -> ArkRust.copy(alpha = 0.15f)
                        enabled -> DarkSurface
                        else -> DarkSurface.copy(alpha = 0.6f)
                    },
            ),
        border = BorderStroke(1.dp, if (selected) ArkRust else BorderColor.copy(alpha = if (enabled) 1f else 0.5f)),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color =
                when {
                    selected -> ArkRust
                    enabled -> TextSecondary
                    else -> TextSecondary.copy(alpha = 0.5f)
                },
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun ArkRecipientModeBadgeChip(
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

private fun arkRecipientModeBadges(parsed: ParsedSendRecipient): List<Pair<String, Color>> =
    when (parsed) {
        is ParsedSendRecipient.Bitcoin -> listOf("Bitcoin" to BitcoinOrange)
        is ParsedSendRecipient.Ark -> listOf("Ark" to ArkRust)
        is ParsedSendRecipient.Lightning ->
            when {
                isLightningAddressPayment(parsed) -> listOf("LN Address" to LightningYellow)
                parsed.kind == LightningKind.LNURL -> listOf("LNURL" to LightningYellow)
                parsed.kind == LightningKind.BOLT12 -> listOf("BOLT12" to LightningYellow)
                else -> listOf("Lightning" to LightningYellow)
            }
        else -> emptyList()
    }

@Composable
private fun ArkSendConfirmationDialog(
    sendState: ArkSendState,
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
                ArkSendState.Idle,
                ArkSendState.Preparing,
                    -> {
                    Button(
                        onClick = { },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                        shape = RoundedCornerShape(8.dp),
                        enabled = false,
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = ArkRust,
                                contentColor = DarkBackground,
                            ),
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
                is ArkSendState.Preview,
                is ArkSendState.MultiPreview,
                    -> {
                    Button(
                        onClick = onConfirm,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = ArkRust,
                                contentColor = DarkBackground,
                            ),
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
                ArkSendState.Sending,
                is ArkSendState.MultiSending,
                    -> {
                    Button(
                        onClick = { },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                        shape = RoundedCornerShape(8.dp),
                        enabled = false,
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = ArkRust,
                                contentColor = DarkBackground,
                            ),
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = DarkBackground,
                            strokeWidth = 2.dp,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text =
                                if (sendState is ArkSendState.MultiSending) {
                                    stringResource(
                                        R.string.send_multi_progress_format,
                                        sendState.completed + 1,
                                        sendState.total,
                                    )
                                } else {
                                    stringResource(R.string.ark_send_status_submitting)
                                },
                            style = MaterialTheme.typography.titleMedium,
                        )
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
                        Text(stringResource(R.string.loc_d2c0aec0), style = MaterialTheme.typography.titleMedium)
                    }
                }
                is ArkSendState.Error,
                is ArkSendState.Sent,
                is ArkSendState.MultiSent,
                    -> {
                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = BorderColor)
                    Spacer(modifier = Modifier.height(8.dp))
                    IbisButton(
                        onClick =
                            if (sendState is ArkSendState.Sent || sendState is ArkSendState.MultiSent) {
                                onDone
                            } else {
                                onDismiss
                            },
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
            text = stringResource(arkSendDialogTitle(sendState)),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
        )
        Spacer(modifier = Modifier.height(16.dp))
        when (sendState) {
            ArkSendState.Idle,
            ArkSendState.Preparing,
                ->
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(28.dp),
                        color = ArkRust,
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
            is ArkSendState.Preview ->
                ArkSendReviewContent(
                    preview = sendState,
                    useSats = useSats,
                    btcPrice = btcPrice,
                    fiatCurrency = fiatCurrency,
                )
            is ArkSendState.MultiPreview -> {
                Text(
                    text = stringResource(R.string.send_multi_sequential_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
                Spacer(modifier = Modifier.height(12.dp))
                sendState.items.forEachIndexed { index, item ->
                    Text(
                        text = stringResource(R.string.send_recipient_number_format, index + 1),
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary,
                    )
                    Text(
                        text = abbreviateArkReviewDestination(item.destination, method = "Ark"),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = TextPrimary,
                    )
                    Text(
                        text = formatAmount(item.amountSats.toULong(), useSats, includeUnit = true),
                        style = MaterialTheme.typography.bodyMedium,
                        color = ArkRust,
                    )
                    if (index < sendState.items.lastIndex) {
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text =
                        stringResource(
                            R.string.send_sending_recipients_format,
                            sendState.items.size,
                        ) +
                            " · " +
                            formatAmount(sendState.totalAmountSats.toULong(), useSats, includeUnit = true),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary,
                )
            }
            ArkSendState.Sending -> ArkSendProgressContent()
            is ArkSendState.MultiSending -> {
                Text(
                    text =
                        stringResource(
                            R.string.send_multi_progress_format,
                            sendState.completed + 1,
                            sendState.total,
                        ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary,
                )
                Spacer(modifier = Modifier.height(12.dp))
                CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    color = ArkRust,
                    strokeWidth = 2.dp,
                )
            }
            is ArkSendState.Sent -> {
                Text(
                    text = stringResource(R.string.ark_send_success),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                )
                sendState.detail?.takeIf { it.isNotBlank() }?.let {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = it,
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            is ArkSendState.MultiSent -> {
                Text(
                    text =
                        if (sendState.failed == 0) {
                            stringResource(
                                R.string.send_multi_done_format,
                                sendState.succeeded,
                                sendState.succeeded,
                            )
                        } else {
                            stringResource(
                                R.string.send_multi_partial_format,
                                sendState.succeeded,
                                sendState.succeeded + sendState.failed,
                                sendState.failed,
                            )
                        },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (sendState.failed == 0) SuccessGreen else WarningYellow,
                )
                sendState.detail?.takeIf { it.isNotBlank() }?.let {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = it,
                        color = ErrorRed,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            is ArkSendState.Error -> {
                Text(
                    text = stringResource(R.string.ln_node_status_failed),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = ErrorRed,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.ln_node_failure_reason),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text =
                        sendState.message.ifBlank {
                            stringResource(R.string.ln_node_status_failed)
                        },
                    style = MaterialTheme.typography.bodyMedium,
                    color = ErrorRed,
                )
            }
        }
    }
}

private fun arkSendDialogTitle(sendState: ArkSendState): Int =
    when (sendState) {
        ArkSendState.Sending,
        is ArkSendState.MultiSending,
        -> R.string.ark_send_status_title
        is ArkSendState.Sent,
        is ArkSendState.MultiSent,
        -> R.string.ark_send_status_sent_title
        is ArkSendState.Error -> R.string.ark_send_status_failed_title
        else -> R.string.loc_81f5c0cf
    }

@Composable
private fun ArkSendProgressContent() {
    Text(
        text = stringResource(R.string.ark_send_status_message),
        style = MaterialTheme.typography.bodyMedium,
        color = TextPrimary,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(modifier = Modifier.height(16.dp))
    ArkSendProgressStep(
        title = stringResource(R.string.ark_send_status_step_review_title),
        detail = stringResource(R.string.ark_send_status_step_review_detail),
        complete = true,
    )
    ArkSendProgressStep(
        title = stringResource(R.string.ark_send_status_step_submit_title),
        detail = stringResource(R.string.ark_send_status_step_submit_detail),
        active = true,
    )
    ArkSendProgressStep(
        title = stringResource(R.string.ark_send_status_step_settle_title),
        detail = stringResource(R.string.ark_send_status_step_settle_detail),
    )
}

@Composable
private fun ArkSendProgressStep(
    title: String,
    detail: String,
    complete: Boolean = false,
    active: Boolean = false,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier =
                Modifier
                    .padding(top = 3.dp)
                    .size(18.dp),
            contentAlignment = Alignment.Center,
        ) {
            when {
                active ->
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = ArkRust,
                        strokeWidth = 2.dp,
                    )
                complete ->
                    Box(
                        modifier =
                            Modifier
                                .size(10.dp)
                                .background(ArkRust, RoundedCornerShape(2.dp)),
                    )
                else ->
                    Box(
                        modifier =
                            Modifier
                                .size(10.dp)
                                .background(BorderColor, RoundedCornerShape(2.dp)),
                    )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = if (complete || active) TextPrimary else TextSecondary,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )
        }
    }
}

@Composable
private fun ArkSendReviewContent(
    preview: ArkSendState.Preview,
    useSats: Boolean,
    btcPrice: Double?,
    fiatCurrency: String,
) {
    // Confirmation dialogs always show real amounts/destination (privacy mode is ambient-only).
    Text(
        text = stringResource(R.string.loc_895ab1d4),
        style = MaterialTheme.typography.bodyLarge,
        color = TextSecondary,
    )
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = abbreviateArkReviewDestination(preview.destination, preview.method),
        style = MaterialTheme.typography.bodyLarge,
        fontFamily = FontFamily.Monospace,
        color = TextPrimary,
    )
    preview.label?.takeIf { it.isNotBlank() }?.let { label ->
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
        )
    }

    Spacer(modifier = Modifier.height(12.dp))
    Text(
        text = preview.method,
        style = MaterialTheme.typography.labelLarge,
        color = arkReviewMethodColor(preview.method),
        fontWeight = FontWeight.SemiBold,
    )

    Spacer(modifier = Modifier.height(16.dp))
    ArkReviewDivider()
    Spacer(modifier = Modifier.height(16.dp))

    val amountSats = preview.amountSats ?: 0L
    val feeSats = preview.feeSats ?: 0L
    if (preview.amountSats != null) {
        ArkReviewAmountRow(
            label = stringResource(R.string.loc_d19e8dd8),
            valueText = arkReviewAmountText(amountSats, useSats),
            valueSubtext = arkReviewUsdSubtext(amountSats, btcPrice, fiatCurrency),
            color = AccentRed,
        )
        Spacer(modifier = Modifier.height(12.dp))
    }

    if (preview.feeSats != null) {
        ArkReviewAmountRow(
            label = arkReviewFeeLabel(preview.method),
            valueText = arkReviewAmountText(feeSats, useSats),
            valueSubtext = arkReviewUsdSubtext(feeSats, btcPrice, fiatCurrency),
            color = arkReviewMethodColor(preview.method),
        )
        Spacer(modifier = Modifier.height(12.dp))
    }

    if (preview.amountSats != null || preview.feeSats != null) {
        ArkReviewDivider()
        Spacer(modifier = Modifier.height(16.dp))
        // For rails where fee is separate from recipient amount, total = amount + fee.
        // Bark estimates already expose net/gross; when fee exists treat like Spark annex.
        val total = amountSats + feeSats
        ArkReviewAmountRow(
            label = stringResource(R.string.loc_03eece5a),
            valueText = arkReviewAmountText(total, useSats),
            valueSubtext = arkReviewUsdSubtext(total, btcPrice, fiatCurrency),
            color = AccentRed,
            bold = true,
        )
    }
}

@Composable
private fun ArkReviewAmountRow(
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
private fun ArkReviewDivider() {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(BorderColor),
    )
}

private fun arkReviewAmountText(
    value: Long,
    useSats: Boolean,
): String = "-${formatAmount(value.toULong(), useSats, includeUnit = true)}"

private fun arkReviewUsdSubtext(
    value: Long,
    btcPrice: Double?,
    fiatCurrency: String,
): String? {
    if (btcPrice == null || btcPrice <= 0.0) return null
    return formatFiat((value.toDouble() / 100_000_000.0) * btcPrice, fiatCurrency)
}

@Composable
private fun arkReviewFeeLabel(method: String): String =
    when {
        method.contains("chain", ignoreCase = true) || method.contains("Bitcoin", ignoreCase = true) ->
            stringResource(R.string.ark_review_fee_bitcoin)
        method.contains("Lightning", ignoreCase = true) ||
            method.contains("BOLT", ignoreCase = true) ||
            method.contains("LNURL", ignoreCase = true) ||
            method.contains("LN Address", ignoreCase = true) ->
            stringResource(R.string.ark_review_fee_lightning)
        else -> stringResource(R.string.ark_review_fee_ark)
    }

/** Head…tail truncation for long destinations (Ark, on-chain, LN) on review. */
private fun abbreviateArkReviewDestination(
    destination: String,
    method: String,
    prefix: Int = 12,
    suffix: Int = 8,
): String {
    val value = destination.trim()
    // Short LN addresses (user@domain) stay fully visible.
    val isShortLnAddress =
        method.contains("LN Address", ignoreCase = true) ||
            (value.contains('@') && value.length <= 48 && !value.startsWith("ln", ignoreCase = true))
    if (isShortLnAddress) return value
    if (value.length <= prefix + suffix + 3) return value
    return value.take(prefix) + "..." + value.takeLast(suffix)
}

private fun arkReviewMethodColor(method: String): Color =
    when {
        method.contains("chain", ignoreCase = true) || method.contains("Bitcoin", ignoreCase = true) ->
            BitcoinOrange
        method.contains("Lightning", ignoreCase = true) ||
            method.contains("BOLT", ignoreCase = true) ||
            method.contains("LNURL", ignoreCase = true) ||
            method.contains("LN Address", ignoreCase = true) ->
            LightningYellow
        else -> ArkRust
    }



private fun filterArkAmountInput(
    value: String,
    useSats: Boolean,
    isUsdMode: Boolean,
): String {
    var v = value
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

private fun parseArkSendAmount(
    input: String,
    useSats: Boolean,
    isUsdMode: Boolean,
    btcPrice: Double?,
): Long? {
    val trimmed = input.trim()
    if (trimmed.isBlank()) return null
    return when {
        isUsdMode && btcPrice != null && btcPrice > 0 ->
            trimmed.toDoubleOrNull()?.let { ((it / btcPrice) * 100_000_000).roundToLong() }
        useSats -> trimmed.toLongOrNull()
        else -> trimmed.toDoubleOrNull()?.let { (it * 100_000_000).roundToLong() }
    }?.takeIf { it > 0 }
}

private fun convertArkAmountInput(
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

private fun formatArkSendAmountInput(
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
