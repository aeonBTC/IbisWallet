@file:Suppress("AssignedValueIsNeverRead")

package github.aeonbtc.ibiswallet.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import github.aeonbtc.ibiswallet.MainActivity
import github.aeonbtc.ibiswallet.data.local.SecureStorage
import github.aeonbtc.ibiswallet.data.model.LightningPaymentExecutionPlan
import github.aeonbtc.ibiswallet.data.model.LiquidSendKind
import github.aeonbtc.ibiswallet.data.model.LiquidSendPreview
import github.aeonbtc.ibiswallet.data.model.LiquidSendState
import github.aeonbtc.ibiswallet.data.model.LiquidWalletState
import github.aeonbtc.ibiswallet.data.model.Recipient
import github.aeonbtc.ibiswallet.data.model.UtxoInfo
import github.aeonbtc.ibiswallet.ui.components.IbisButton
import github.aeonbtc.ibiswallet.ui.components.QrScannerDialog
import github.aeonbtc.ibiswallet.ui.components.formatFeeRate
import github.aeonbtc.ibiswallet.ui.theme.AccentRed
import github.aeonbtc.ibiswallet.ui.theme.BorderColor
import github.aeonbtc.ibiswallet.ui.theme.DarkBackground
import github.aeonbtc.ibiswallet.ui.theme.DarkCard
import github.aeonbtc.ibiswallet.ui.theme.DarkSurface
import github.aeonbtc.ibiswallet.ui.theme.LiquidTeal
import github.aeonbtc.ibiswallet.ui.theme.SuccessGreen
import github.aeonbtc.ibiswallet.ui.theme.TextPrimary
import github.aeonbtc.ibiswallet.ui.theme.TextSecondary
import github.aeonbtc.ibiswallet.ui.theme.TextTertiary
import github.aeonbtc.ibiswallet.ui.theme.WarningYellow
import github.aeonbtc.ibiswallet.util.LightningKind
import github.aeonbtc.ibiswallet.util.ParsedSendRecipient
import github.aeonbtc.ibiswallet.util.getNfcAvailability
import github.aeonbtc.ibiswallet.util.layer2RecipientValidationError
import github.aeonbtc.ibiswallet.util.parseSendRecipient
import github.aeonbtc.ibiswallet.viewmodel.SendScreenDraft
import java.util.Locale
import kotlin.math.roundToLong

@Composable
fun LiquidSendScreen(
    denomination: String = SecureStorage.DENOMINATION_BTC,
    btcPrice: Double? = null,
    privacyMode: Boolean = false,
    boltzEnabled: Boolean = true,
    isWatchOnly: Boolean = false,
    liquidState: LiquidWalletState = LiquidWalletState(),
    liquidUtxos: List<UtxoInfo> = emptyList(),
    spendUnconfirmed: Boolean = true,
    draft: SendScreenDraft = SendScreenDraft(),
    liquidSendState: LiquidSendState = LiquidSendState.Idle,
    onUpdateDraft: (SendScreenDraft) -> Unit = {},
    onPreviewLiquidSend: (
        address: String,
        amountSats: Long,
        feeRate: Double,
        selectedUtxos: List<UtxoInfo>?,
        isMaxSend: Boolean,
        label: String?,
    ) -> Unit = { _, _, _, _, _, _ -> },
    onPreviewLiquidSendMulti: (
        recipients: List<Recipient>,
        feeRate: Double,
        selectedUtxos: List<UtxoInfo>?,
        label: String?,
    ) -> Unit = { _, _, _, _ -> },
    onPreviewLightningPayment: (
        paymentInput: String,
        kind: LiquidSendKind,
        amountSats: Long?,
        feeRate: Double,
        selectedUtxos: List<UtxoInfo>?,
        label: String?,
    ) -> Unit = { _, _, _, _, _, _ -> },
    onResolveLightningPayment: (
        paymentInput: String,
        kind: LiquidSendKind,
        amountSats: Long?,
        feeRate: Double,
        selectedUtxos: List<UtxoInfo>?,
        label: String?,
    ) -> Unit = { _, _, _, _, _, _ -> },
    onSendLBTC: (
        address: String,
        amountSats: Long,
        feeRate: Double,
        selectedUtxos: List<UtxoInfo>?,
        isMaxSend: Boolean,
        label: String?,
    ) -> Unit = { _, _, _, _, _, _ -> },
    onSendLBTCMulti: (
        recipients: List<Recipient>,
        feeRate: Double,
        selectedUtxos: List<UtxoInfo>?,
        label: String?,
    ) -> Unit = { _, _, _, _ -> },
    onConfirmLightningPayment: (
        selectedUtxos: List<UtxoInfo>?,
        label: String?,
    ) -> Unit = { _, _ -> },
    preSelectedUtxo: UtxoInfo? = null,
    onClearPreSelectedUtxo: () -> Unit = {},
    onClearDraft: () -> Unit = {},
    onResetSend: () -> Unit = {},
) {
    var recipientAddress by remember { mutableStateOf(draft.recipientAddress) }
    var amountInput by remember { mutableStateOf(draft.amountInput) }
    var feeRate by remember { mutableDoubleStateOf(resolveLiquidDraftFeeRate(draft)) }
    var feeRateInput by remember { mutableStateOf(formatFeeRate(resolveLiquidDraftFeeRate(draft))) }
    var isUsdMode by remember { mutableStateOf(false) }
    var showQrScanner by remember { mutableStateOf(false) }
    var showConfirmDialog by remember { mutableStateOf(false) }
    var dismissedSendError by remember { mutableStateOf<String?>(null) }
    var wasSending by remember { mutableStateOf(false) }
    var showCoinControl by remember { mutableStateOf(false) }
    val selectedUtxos = remember { mutableStateListOf<UtxoInfo>() }

    LaunchedEffect(preSelectedUtxo) {
        if (preSelectedUtxo != null) {
            selectedUtxos.clear()
            selectedUtxos.add(preSelectedUtxo)
            onClearPreSelectedUtxo()
        }
    }

    var isMultiMode by remember { mutableStateOf(false) }
    var showMultiDialog by remember { mutableStateOf(false) }
    val multiRecipients = remember { mutableStateListOf<Pair<String, String>>() }
    var isMaxMode by remember { mutableStateOf(draft.isMaxSend) }
    var showLabelField by remember { mutableStateOf(draft.label.isNotEmpty()) }
    var labelText by remember { mutableStateOf(draft.label) }

    fun clearLocalSendForm() {
        recipientAddress = ""
        amountInput = ""
        feeRate = MIN_LIQUID_FEE_RATE
        feeRateInput = formatFeeRate(MIN_LIQUID_FEE_RATE)
        isUsdMode = false
        showQrScanner = false
        dismissedSendError = null
        showCoinControl = false
        selectedUtxos.clear()
        isMultiMode = false
        showMultiDialog = false
        multiRecipients.clear()
        isMaxMode = false
        showLabelField = false
        labelText = ""
    }

    val useSats = denomination == SecureStorage.DENOMINATION_SATS
    val parsedRecipient = remember(recipientAddress) { parseSendRecipient(recipientAddress) }
    val liquidRecipient = parsedRecipient as? ParsedSendRecipient.Liquid
    val lightningRecipient = parsedRecipient as? ParsedSendRecipient.Lightning
    val isLightningPayment = lightningRecipient != null
    val canEditLightningAmount =
        lightningRecipient?.let { it.kind == LightningKind.BOLT12 && it.amountSats == null } == true
    val isMultiAvailable = !isLightningPayment
    val isMaxAvailable = !isLightningPayment || canEditLightningAmount
    val isUsdAmountModeAvailable = !isLightningPayment || canEditLightningAmount
    val recipientModeBadges = remember(parsedRecipient) {
        buildRecipientModeBadges(parsedRecipient)
    }
    val addressValidationError = remember(parsedRecipient, isMultiMode) {
        if (isMultiMode) {
            null
        } else {
            layer2RecipientValidationError(parsedRecipient)
        }
    }
    val isAddressValid = recipientAddress.isNotBlank() && addressValidationError == null
    val spendableUtxos = remember(liquidUtxos) { liquidUtxos.filter { !it.isFrozen } }
    val selectedUtxoSnapshot = selectedUtxos.toList()
    val availableSats =
        remember(selectedUtxoSnapshot, liquidState.balanceSats) {
            if (selectedUtxoSnapshot.isNotEmpty()) {
                selectedUtxoSnapshot.sumOf { it.amountSats.toLong() }
            } else {
                liquidState.balanceSats.coerceAtLeast(0L)
            }
        }
    val amountSats = parseLiquidAmountInputToSats(amountInput, useSats, isUsdMode, btcPrice)
    val lightningRequestedAmountSats =
        lightningRecipient?.amountSats ?: amountSats.takeIf { canEditLightningAmount && it > 0L }
    val multiRecipientList =
        remember(multiRecipients.toList(), useSats, isUsdMode, btcPrice) {
            multiRecipients.mapNotNull { (addressInput, amountInputValue) ->
                val parsed = parseSendRecipient(addressInput.trim())
                if (parsed !is ParsedSendRecipient.Liquid) {
                    return@mapNotNull null
                }
                val sats = parseLiquidAmountInputToSats(amountInputValue, useSats, isUsdMode, btcPrice)
                if (sats <= 0L) {
                    return@mapNotNull null
                }
                Recipient(parsed.address, sats.toULong())
            }
        }
    val multiTotalSats = multiRecipientList.sumOf { it.amountSats.toLong() }
    val txLabel = labelText.trim().takeIf { showLabelField && it.isNotBlank() }
    val preview = currentLiquidSendPreview(liquidSendState)
    val inlineSendError = if (!showConfirmDialog) dismissedSendError else null
    val sendingState = liquidSendState as? LiquidSendState.Sending
    val isSendLocked = sendingState != null
    val canReview =
        liquidState.isInitialized &&
            !isWatchOnly &&
            liquidSendState is LiquidSendState.ReviewReady

    LaunchedEffect(draft.selectedUtxoOutpoints, spendableUtxos) {
        if (draft.selectedUtxoOutpoints.isNotEmpty() && selectedUtxos.isEmpty()) {
            val restored = selectCoinControlUtxos(draft.selectedUtxoOutpoints, spendableUtxos)
            selectedUtxos.addAll(restored)
        }
    }

    LaunchedEffect(spendableUtxos) {
        reconcileCoinControlSelection(selectedUtxos, spendableUtxos)
    }

    LaunchedEffect(draft) {
        val draftWasCleared =
            draft.recipientAddress.isEmpty() &&
                draft.amountInput.isEmpty() &&
                draft.label.isEmpty() &&
                recipientAddress.isNotEmpty()
        if (draftWasCleared) {
            recipientAddress = ""
            amountInput = ""
            feeRate = MIN_LIQUID_FEE_RATE
            feeRateInput = formatFeeRate(MIN_LIQUID_FEE_RATE)
            isUsdMode = false
            isMaxMode = false
            showLabelField = false
            labelText = ""
            selectedUtxos.clear()
        } else if (draft.recipientAddress.isNotEmpty() && draft.recipientAddress != recipientAddress) {
            recipientAddress = draft.recipientAddress
            amountInput = draft.amountInput
            feeRate = resolveLiquidDraftFeeRate(draft)
            feeRateInput = formatFeeRate(feeRate)
            isMaxMode = draft.isMaxSend
            if (draft.label.isNotBlank()) {
                labelText = draft.label
                showLabelField = true
            }
            onUpdateDraft(SendScreenDraft())
        }
    }

    LaunchedEffect(recipientAddress, amountInput, labelText, feeRate, isMaxMode, selectedUtxoSnapshot) {
        onUpdateDraft(
            SendScreenDraft(
                recipientAddress = recipientAddress,
                amountInput = amountInput,
                label = labelText,
                feeRate = feeRate,
                isMaxSend = isMaxMode,
                selectedUtxoOutpoints = selectedUtxoSnapshot.map { it.outpoint },
            ),
        )
    }
    LaunchedEffect(liquidSendState) {
        val isSendingNow = liquidSendState is LiquidSendState.Sending
        if (wasSending && !isSendingNow) {
            when (liquidSendState) {
                is LiquidSendState.Failed -> {
                    dismissedSendError = null
                    showConfirmDialog = true
                }
                is LiquidSendState.Success -> {
                    dismissedSendError = null
                    showConfirmDialog = false
                    clearLocalSendForm()
                    onClearDraft()
                    onResetSend()
                }
                else -> Unit
            }
        }
        if (liquidSendState is LiquidSendState.ReviewReady) {
            dismissedSendError = null
        }
        wasSending = isSendingNow
    }

    LaunchedEffect(liquidSendState, isMaxMode, isMultiMode, isUsdMode, useSats, btcPrice) {
        if (!isMaxMode || isMultiMode) return@LaunchedEffect
        val maxPreview = liquidSendState as? LiquidSendState.ReviewReady ?: return@LaunchedEffect
        val maxAmount = maxPreview.preview.totalRecipientSats ?: return@LaunchedEffect
        amountInput = formatLiquidInputAmount(maxAmount, useSats, isUsdMode, btcPrice)
    }

    LaunchedEffect(
        recipientAddress,
        amountSats,
        feeRate,
        selectedUtxoSnapshot,
        isMaxMode,
        labelText,
        showLabelField,
        isMultiMode,
        liquidState.isInitialized,
    ) {
        if (isMultiMode || isSendLocked || showConfirmDialog) return@LaunchedEffect
        if (!liquidState.isInitialized) {
            onResetSend()
            return@LaunchedEffect
        }
        kotlinx.coroutines.delay(150)
        when (parsedRecipient) {
            is ParsedSendRecipient.Liquid -> {
                if (isAddressValid && (amountSats > 0L || isMaxMode)) {
                    onPreviewLiquidSend(
                        parsedRecipient.address,
                        amountSats,
                        feeRate,
                        selectedUtxoSnapshot.ifEmpty { null },
                        isMaxMode,
                        txLabel,
                    )
                } else {
                    onResetSend()
                }
            }

            is ParsedSendRecipient.Lightning -> {
                if (isAddressValid) {
                    onPreviewLightningPayment(
                        parsedRecipient.paymentInput,
                        parsedRecipient.kind.toLiquidSendKind(),
                        lightningRequestedAmountSats,
                        feeRate,
                        selectedUtxoSnapshot.ifEmpty { null },
                        txLabel,
                    )
                } else {
                    onResetSend()
                }
            }

            else -> onResetSend()
        }
    }

    LaunchedEffect(
        multiRecipientList,
        feeRate,
        selectedUtxoSnapshot,
        labelText,
        showLabelField,
        isMultiMode,
        liquidState.isInitialized,
    ) {
        if (!isMultiMode || isSendLocked) return@LaunchedEffect
        if (!liquidState.isInitialized) {
            onResetSend()
            return@LaunchedEffect
        }
        kotlinx.coroutines.delay(150)
        if (multiRecipientList.size >= 2) {
            onPreviewLiquidSendMulti(
                multiRecipientList,
                feeRate,
                selectedUtxoSnapshot.ifEmpty { null },
                txLabel,
            )
        } else {
            onResetSend()
        }
    }

    val context = LocalContext.current
    val isPreview = LocalInspectionMode.current
    val nfcAvailable = !isPreview && context.getNfcAvailability().canRead
    DisposableEffect(nfcAvailable) {
        if (nfcAvailable) {
            (context as? MainActivity)?.enableNfcReaderMode()
        }
        onDispose {
            (context as? MainActivity)?.disableNfcReaderMode()
        }
    }

    if (showCoinControl) {
        LiquidCoinControlDialog(
            utxos = spendableUtxos,
            selectedUtxos = selectedUtxos,
            useSats = useSats,
            btcPrice = btcPrice,
            privacyMode = privacyMode,
            spendUnconfirmed = spendUnconfirmed,
            onUtxoToggle = { utxo ->
                toggleCoinControlSelection(selectedUtxos, utxo)
            },
            onSelectAll = {
                selectedUtxos.clear()
                selectedUtxos.addAll(
                    if (spendUnconfirmed) {
                        spendableUtxos
                    } else {
                        spendableUtxos.filter { it.isConfirmed }
                    },
                )
            },
            onClearAll = { selectedUtxos.clear() },
            onDismiss = { showCoinControl = false },
        )
    }

    if (showMultiDialog) {
        LiquidMultiRecipientDialog(
            recipients = multiRecipients,
            useSats = useSats,
            isUsdMode = isUsdMode,
            btcPrice = btcPrice,
            privacyMode = privacyMode,
            availableSats = availableSats,
            selectedUtxoCount = selectedUtxoSnapshot.size,
            estimatedFeeSats = preview?.feeSats,
            validRecipientCount = multiRecipientList.size,
            totalSendingSats = multiTotalSats,
            previewError = inlineSendError,
            onDone = { showMultiDialog = false },
            onDismiss = { showMultiDialog = false },
        )
    }

    if (showQrScanner) {
        QrScannerDialog(
            onCodeScanned = { code ->
                when (val parsedScan = parseSendRecipient(code.trim())) {
                    is ParsedSendRecipient.Liquid -> {
                        recipientAddress = parsedScan.address
                        amountInput = parsedScan.amountSats?.let { formatLiquidInputAmount(it, useSats, isUsdMode, btcPrice) }.orEmpty()
                        parsedScan.label?.let {
                            labelText = it
                            showLabelField = true
                        }
                        isMaxMode = false
                    }

                    is ParsedSendRecipient.Lightning -> {
                        val allowManualAmount = parsedScan.kind == LightningKind.BOLT12 && parsedScan.amountSats == null
                        recipientAddress = parsedScan.paymentInput
                        amountInput = parsedScan.amountSats?.let { formatLiquidInputAmount(it, useSats, false, btcPrice) }.orEmpty()
                        if (!allowManualAmount) {
                            isUsdMode = false
                        }
                        isMaxMode = false
                    }

                    else -> {
                        recipientAddress = code.trim()
                    }
                }
                showQrScanner = false
            },
            onDismiss = { showQrScanner = false },
        )
    }

    val shouldShowSendDialog =
        showConfirmDialog &&
            preview != null &&
            when (liquidSendState) {
                is LiquidSendState.ReviewReady,
                is LiquidSendState.Sending,
                is LiquidSendState.Failed,
                -> true
                is LiquidSendState.Success,
                LiquidSendState.Idle,
                -> false
            }

    if (shouldShowSendDialog) {
        LiquidSendConfirmationDialog(
            sendState = liquidSendState,
            useSats = useSats,
            btcPrice = btcPrice,
            privacyMode = privacyMode,
            onConfirm = {
                if (isMultiMode) {
                    onSendLBTCMulti(
                        multiRecipientList,
                        feeRate,
                        selectedUtxoSnapshot.ifEmpty { null },
                        txLabel,
                    )
                } else if (lightningRecipient != null) {
                    onConfirmLightningPayment(
                        selectedUtxoSnapshot.ifEmpty { null },
                        txLabel,
                    )
                } else {
                    onSendLBTC(
                        liquidRecipient?.address ?: recipientAddress.trim(),
                        amountSats,
                        feeRate,
                        selectedUtxoSnapshot.ifEmpty { null },
                        isMaxMode,
                        txLabel,
                    )
                }
            },
            onDismiss = {
                when (liquidSendState) {
                    is LiquidSendState.ReviewReady -> {
                        showConfirmDialog = false
                    }
                    is LiquidSendState.Sending -> {
                        if (liquidSendState.canDismiss) {
                            showConfirmDialog = false
                        }
                    }
                    is LiquidSendState.Success,
                    is LiquidSendState.Failed,
                    -> {
                        showConfirmDialog = false
                        onResetSend()
                    }
                    LiquidSendState.Idle -> {
                        showConfirmDialog = false
                    }
                }
            },
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
                            text = "Send L-BTC",
                            style = MaterialTheme.typography.titleLarge,
                            color = TextPrimary,
                        )
                        if (nfcAvailable) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(top = 2.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Sensors,
                                    contentDescription = "NFC reading",
                                    tint = SuccessGreen,
                                    modifier = Modifier.size(14.dp),
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "NFC",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SuccessGreen,
                                )
                            }
                        }
                    }

                    val hasUtxos = spendableUtxos.isNotEmpty()
                    val coinControlActive = selectedUtxos.isNotEmpty()
                    Card(
                        modifier =
                            Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(enabled = liquidState.isInitialized && hasUtxos) { showCoinControl = true },
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = if (coinControlActive) LiquidTeal.copy(alpha = 0.15f) else DarkSurface),
                        border = BorderStroke(1.dp, if (coinControlActive) LiquidTeal else BorderColor),
                    ) {
                        Text(
                            text = if (coinControlActive) "UTXOs (${selectedUtxos.size})" else "Coin Control",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (coinControlActive) LiquidTeal else TextSecondary,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Recipient",
                        style = MaterialTheme.typography.labelLarge,
                        color = TextSecondary,
                    )
                    Card(
                        modifier =
                            Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(enabled = liquidState.isInitialized && isMultiAvailable) {
                                    if (!isMultiMode) {
                                        isMultiMode = true
                                        isMaxMode = false
                                        multiRecipients.clear()
                                        if (liquidRecipient != null || amountInput.isNotBlank()) {
                                            multiRecipients.add(
                                                Pair(
                                                    liquidRecipient?.address ?: recipientAddress.trim(),
                                                    amountInput,
                                                ),
                                            )
                                        }
                                        if (multiRecipients.isEmpty()) {
                                            multiRecipients.add("" to "")
                                        }
                                        if (multiRecipients.size < 2) {
                                            multiRecipients.add("" to "")
                                        }
                                        showMultiDialog = true
                                    } else {
                                        isMultiMode = false
                                        multiRecipients.clear()
                                        onResetSend()
                                    }
                                },
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor =
                                when {
                                    isMultiMode -> LiquidTeal.copy(alpha = 0.15f)
                                    isMultiAvailable -> DarkSurface
                                    else -> DarkSurface.copy(alpha = 0.6f)
                                },
                        ),
                        border = BorderStroke(
                            1.dp,
                            when {
                                isMultiMode -> LiquidTeal
                                isMultiAvailable -> BorderColor
                                else -> BorderColor.copy(alpha = 0.5f)
                            },
                        ),
                    ) {
                        Text(
                            text = if (isMultiMode) "Multiple (${multiRecipientList.size})" else "Multiple",
                            style = MaterialTheme.typography.labelMedium,
                            color =
                                when {
                                    isMultiMode -> LiquidTeal
                                    isMultiAvailable -> TextSecondary
                                    else -> TextSecondary.copy(alpha = 0.5f)
                                },
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))

                if (isMultiMode) {
                    Card(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { showMultiDialog = true },
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = DarkSurface),
                        border = BorderStroke(1.dp, BorderColor),
                    ) {
                        Column(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                        ) {
                            if (multiRecipientList.isEmpty()) {
                                Text(
                                    text = "Tap to add Liquid recipients",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary.copy(alpha = 0.5f),
                                )
                            } else {
                                multiRecipientList.forEachIndexed { index, recipient ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Text(
                                            text = recipient.address.take(10) + "..." + recipient.address.takeLast(6),
                                            style = MaterialTheme.typography.bodySmall,
                                            fontFamily = FontFamily.Monospace,
                                            color = TextPrimary,
                                            modifier = Modifier.weight(1f),
                                        )
                                        Text(
                                            text = if (privacyMode) LIQUID_HIDDEN_AMOUNT else formatLiquidAmount(recipient.amountSats.toLong(), useSats),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = LiquidTeal,
                                        )
                                    }
                                    if (index < multiRecipientList.lastIndex) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        text = "Total (${multiRecipientList.size} recipients)",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = TextSecondary,
                                    )
                                    Text(
                                        text = if (privacyMode) LIQUID_HIDDEN_AMOUNT else formatLiquidAmount(multiTotalSats, useSats),
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary,
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Tap to edit recipients",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary.copy(alpha = 0.6f),
                    )
                } else {
                    OutlinedTextField(
                        value = recipientAddress,
                        onValueChange = { input ->
                            val normalized = input.trim()
                            when (val parsed = parseSendRecipient(normalized)) {
                                is ParsedSendRecipient.Liquid -> {
                                    recipientAddress = parsed.address
                                    amountInput = parsed.amountSats?.let { formatLiquidInputAmount(it, useSats, isUsdMode, btcPrice) }.orEmpty()
                                    parsed.label?.let {
                                        labelText = it
                                        showLabelField = true
                                    }
                                    isMaxMode = false
                                }

                                is ParsedSendRecipient.Lightning -> {
                                    val allowManualAmount = parsed.kind == LightningKind.BOLT12 && parsed.amountSats == null
                                    recipientAddress = parsed.paymentInput
                                    amountInput =
                                        parsed.amountSats?.let {
                                            formatLiquidInputAmount(it, useSats, false, btcPrice)
                                        }.orEmpty()
                                    if (!allowManualAmount) {
                                        isUsdMode = false
                                    }
                                    isMaxMode = false
                                }

                                else -> {
                                    recipientAddress = normalized
                                    if (normalized.isEmpty()) {
                                        amountInput = ""
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = {
                            Text(
                                text = if (boltzEnabled) "Liquid, LN Invoice or BOLT12" else "Liquid address",
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
                                    tint = LiquidTeal,
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                        },
                        supportingText =
                            if (addressValidationError != null && recipientAddress.isNotBlank()) {
                                {
                                    Text(
                                        text = addressValidationError,
                                        color = AccentRed,
                                    )
                                }
                            } else {
                                null
                            },
                        isError = addressValidationError != null && recipientAddress.isNotBlank(),
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        colors =
                            OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = if (addressValidationError != null && recipientAddress.isNotBlank()) AccentRed else LiquidTeal,
                                unfocusedBorderColor = if (addressValidationError != null && recipientAddress.isNotBlank()) AccentRed else BorderColor,
                                errorBorderColor = AccentRed,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary,
                                cursorColor = LiquidTeal,
                            ),
                    )

                    if (recipientModeBadges.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            recipientModeBadges.forEach { badge ->
                                RecipientModeBadgeChip(badge = badge)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (!isMultiMode) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text =
                                when {
                                    isLightningPayment && lightningRequestedAmountSats != null -> "Amount (${if (useSats) "sats" else "BTC"})"
                                    isLightningPayment -> "Amount"
                                    isUsdMode -> "Amount (USD)"
                                    useSats -> "Amount (sats)"
                                    else -> "Amount (BTC)"
                                },
                            style = MaterialTheme.typography.labelLarge,
                            color = TextSecondary,
                        )
                        if (btcPrice != null && btcPrice > 0) {
                            Card(
                                modifier =
                                    Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable(enabled = isUsdAmountModeAvailable) {
                                            amountInput = formatLiquidInputAmount(amountSats, useSats, !isUsdMode, btcPrice)
                                            isUsdMode = !isUsdMode
                                            isMaxMode = false
                                        },
                                shape = RoundedCornerShape(8.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor =
                                        when {
                                            isUsdMode -> LiquidTeal.copy(alpha = 0.15f)
                                            isUsdAmountModeAvailable -> DarkSurface
                                            else -> DarkSurface.copy(alpha = 0.6f)
                                        },
                                ),
                                border = BorderStroke(
                                    1.dp,
                                    when {
                                        isUsdMode -> LiquidTeal
                                        isUsdAmountModeAvailable -> BorderColor
                                        else -> BorderColor.copy(alpha = 0.5f)
                                    },
                                ),
                            ) {
                                Text(
                                    text = "USD",
                                    style = MaterialTheme.typography.labelMedium,
                                    color =
                                        when {
                                            isUsdMode -> LiquidTeal
                                            isUsdAmountModeAvailable -> TextSecondary
                                            else -> TextSecondary.copy(alpha = 0.5f)
                                        },
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))

                    val conversionText =
                        if (amountInput.isNotEmpty() && amountSats > 0 && btcPrice != null && btcPrice > 0) {
                            if (privacyMode) {
                                "≈ $LIQUID_HIDDEN_AMOUNT"
                            } else if (isUsdMode) {
                                "≈ ${formatLiquidAmount(amountSats, useSats)}"
                            } else {
                                "≈ ${formatLiquidUsd(amountSats, btcPrice)}"
                            }
                        } else {
                            null
                        }

                    OutlinedTextField(
                        value = amountInput,
                        onValueChange = { value ->
                            isMaxMode = false
                            amountInput =
                                filterLiquidAmountInput(
                                    value = value,
                                    useSats = useSats,
                                    isUsdMode = isUsdMode,
                                )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = {
                            Text(
                                text =
                                    when {
                                        canEditLightningAmount -> if (useSats) "Enter sats" else "0.00000000"
                                        isLightningPayment -> "Set by payment"
                                        isUsdMode -> "0.00"
                                        useSats -> "0"
                                        else -> "0.00000000"
                                    },
                                color = TextSecondary.copy(alpha = 0.5f),
                            )
                        },
                        enabled = !isLightningPayment || canEditLightningAmount,
                        leadingIcon =
                            if (isUsdMode && isUsdAmountModeAvailable) {
                                { Text("$", color = TextSecondary) }
                            } else {
                                null
                            },
                        suffix =
                            if (conversionText != null) {
                                {
                                    Text(
                                        text = conversionText,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = LiquidTeal,
                                    )
                                }
                            } else {
                                null
                            },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        colors =
                            OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = if (isMaxMode) LiquidTeal else BorderColor,
                                unfocusedBorderColor = if (isMaxMode) LiquidTeal else BorderColor,
                                disabledBorderColor = BorderColor,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary,
                                disabledTextColor = TextSecondary,
                                cursorColor = LiquidTeal,
                            ),
                    )

                    if (inlineSendError != null && (!isLightningPayment || canEditLightningAmount) && amountInput.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = inlineSendError,
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
                        Text(
                            text = "Available",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text =
                                if (privacyMode) {
                                    LIQUID_HIDDEN_AMOUNT
                                } else {
                                    formatLiquidAmount(availableSats, useSats)
                                },
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                        )
                        if (btcPrice != null && btcPrice > 0 && !privacyMode) {
                            Text(
                                text = " · ${formatLiquidUsd(availableSats, btcPrice)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary.copy(alpha = 0.7f),
                            )
                        }
                        if (selectedUtxos.isNotEmpty()) {
                            Text(
                                text = " (${selectedUtxos.size} UTXO${if (selectedUtxos.size > 1) "s" else ""})",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                            )
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        Card(
                            modifier =
                                Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable(enabled = liquidState.isInitialized && isMaxAvailable) {
                                        if (isMaxMode) {
                                            isMaxMode = false
                                            amountInput = ""
                                        } else {
                                            isMaxMode = true
                                            isUsdMode = false
                                            amountInput =
                                                if (useSats) {
                                                    availableSats.toString()
                                                } else {
                                                    formatLiquidInputAmount(availableSats, useSats = false, isUsdMode = false, btcPrice = btcPrice)
                                                }
                                        }
                                    },
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(
                                containerColor =
                                    when {
                                        isMaxMode -> LiquidTeal.copy(alpha = 0.15f)
                                        isMaxAvailable -> DarkSurface
                                        else -> DarkSurface.copy(alpha = 0.6f)
                                    },
                            ),
                            border = BorderStroke(
                                1.dp,
                                when {
                                    isMaxMode -> LiquidTeal
                                    isMaxAvailable -> BorderColor
                                    else -> BorderColor.copy(alpha = 0.5f)
                                },
                            ),
                        ) {
                            Text(
                                text = "Max",
                                style = MaterialTheme.typography.labelMedium,
                                color =
                                    when {
                                        isMaxMode -> LiquidTeal
                                        isMaxAvailable -> TextSecondary
                                        else -> TextSecondary.copy(alpha = 0.5f)
                                    },
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            )
                        }
                    }
                }

                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp, bottom = 0.dp),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Card(
                        modifier =
                            Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(enabled = liquidState.isInitialized) { showLabelField = !showLabelField },
                        shape = RoundedCornerShape(8.dp),
                        colors =
                            CardDefaults.cardColors(
                                containerColor =
                                    if (showLabelField || labelText.isNotBlank()) {
                                        LiquidTeal.copy(alpha = 0.15f)
                                    } else {
                                        DarkSurface
                                    },
                            ),
                        border = BorderStroke(1.dp, if (showLabelField || labelText.isNotBlank()) LiquidTeal else BorderColor),
                    ) {
                        Text(
                            text = "Label",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (showLabelField || labelText.isNotBlank()) LiquidTeal else TextSecondary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        )
                    }

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
                                    "e.g. Payment to Alice",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary.copy(alpha = 0.5f),
                                )
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp),
                            colors =
                                OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = LiquidTeal,
                                    unfocusedBorderColor = BorderColor,
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary,
                                    cursorColor = LiquidTeal,
                                ),
                            enabled = liquidState.isInitialized,
                            textStyle = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                LiquidFeeRateSection(
                    feeRateInput = feeRateInput,
                    onFeeRateChange = { input ->
                        if (input.isEmpty() || input.matches(Regex("^\\d*\\.?\\d*$"))) {
                            feeRateInput = input
                            input.toDoubleOrNull()?.let {
                                feeRate = it
                            }
                        }
                    },
                    preview = preview,
                    useSats = useSats,
                    btcPrice = btcPrice,
                    privacyMode = privacyMode,
                )

                if (!liquidState.isInitialized) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = liquidState.error ?: "Liquid wallet is not ready yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = WarningYellow,
                    )
                }

                if (isWatchOnly) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Liquid sending is unavailable for watch-only wallets.",
                        style = MaterialTheme.typography.bodySmall,
                        color = AccentRed,
                    )
                }
            }
        }

        if (inlineSendError != null && !showConfirmDialog) {
            Spacer(modifier = Modifier.height(12.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = DarkCard),
                border = BorderStroke(1.dp, WarningYellow.copy(alpha = 0.35f)),
            ) {
                Text(
                    text = inlineSendError,
                    style = MaterialTheme.typography.bodyMedium,
                    color = WarningYellow,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = {
                if (lightningRecipient != null) {
                    showConfirmDialog = true
                    onResolveLightningPayment(
                        lightningRecipient.paymentInput,
                        lightningRecipient.kind.toLiquidSendKind(),
                        lightningRequestedAmountSats,
                        feeRate,
                        selectedUtxoSnapshot.ifEmpty { null },
                        txLabel,
                    )
                } else {
                    showConfirmDialog = true
                }
            },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(48.dp),
            shape = RoundedCornerShape(8.dp),
            enabled = canReview,
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = LiquidTeal,
                    disabledContainerColor = LiquidTeal.copy(alpha = 0.3f),
                ),
        ) {
            Text(
                text = if (isLightningPayment) "Review Payment" else "Review Transaction",
                style = MaterialTheme.typography.titleMedium,
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun LiquidFeeRateSection(
    feeRateInput: String,
    onFeeRateChange: (String) -> Unit,
    preview: LiquidSendPreview?,
    useSats: Boolean,
    btcPrice: Double?,
    privacyMode: Boolean,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Fee Rate",
            style = MaterialTheme.typography.labelLarge,
            color = TextSecondary,
        )
        preview?.feeSats?.let { feeSats ->
            val vBytesDisplay = preview.txVBytes?.let(::formatVBytes)
            val usdFee =
                if (btcPrice != null && btcPrice > 0 && !privacyMode) {
                    " · ${formatLiquidUsd(feeSats, btcPrice)}"
                } else {
                    ""
                }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text =
                    if (privacyMode) {
                        "Est. fee: $LIQUID_HIDDEN_AMOUNT${vBytesDisplay?.let { " ($it vB)" }.orEmpty()}"
                    } else {
                        "Est. fee: ${formatLiquidAmount(feeSats, useSats)}$usdFee${vBytesDisplay?.let { " ($it vB)" }.orEmpty()}"
                    },
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = feeRateInput,
            onValueChange = onFeeRateChange,
            modifier = Modifier.fillMaxWidth(),
            suffix = {
                Text(
                    text = "sat/vB",
                    color = TextSecondary.copy(alpha = 0.7f),
                )
            },
            supportingText = {
                Text(
                    text = "Default: 0.1 sat/vB",
                    color = TextSecondary,
                )
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            shape = RoundedCornerShape(8.dp),
            colors =
                OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = LiquidTeal,
                    unfocusedBorderColor = BorderColor,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    cursorColor = LiquidTeal,
                ),
        )
    }
}

private fun resolveLiquidDraftFeeRate(draft: SendScreenDraft): Double {
    val hasDraftContent =
        draft.recipientAddress.isNotBlank() ||
            draft.amountInput.isNotBlank() ||
            draft.label.isNotBlank() ||
            draft.selectedUtxoOutpoints.isNotEmpty() ||
            draft.isMaxSend
    return if (!hasDraftContent && draft.feeRate == SendScreenDraft().feeRate) {
        MIN_LIQUID_FEE_RATE
    } else {
        draft.feeRate
    }
}

@Composable
private fun LiquidSendConfirmationDialog(
    sendState: LiquidSendState,
    useSats: Boolean,
    btcPrice: Double?,
    privacyMode: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val preview = currentLiquidSendPreview(sendState) ?: return
    val canDismissDialog = (sendState as? LiquidSendState.Sending)?.canDismiss ?: true
    Dialog(
        onDismissRequest = {
            if (canDismissDialog) {
                onDismiss()
            }
        },
        properties =
            DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = canDismissDialog,
                dismissOnClickOutside = canDismissDialog,
            ),
    ) {
        Surface(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            shape = RoundedCornerShape(12.dp),
            color = DarkSurface,
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
            ) {
                Text(
                    text = liquidSendDialogTitle(sendState, preview.kind),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                )

                Spacer(modifier = Modifier.height(16.dp))

                when (sendState) {
                    is LiquidSendState.ReviewReady -> LiquidSendReviewContent(
                        preview = preview,
                        useSats = useSats,
                        btcPrice = btcPrice,
                        privacyMode = privacyMode,
                    )

                    is LiquidSendState.Sending -> LiquidSendReviewContent(
                        preview = preview,
                        useSats = useSats,
                        btcPrice = btcPrice,
                        privacyMode = privacyMode,
                    )

                    is LiquidSendState.Success -> {
                        Text(
                            text = sendState.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                        )
                        sendState.fundingTxid?.let { txid ->
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = txid,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextTertiary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        sendState.settlementTxid?.let { txid ->
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = txid,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextTertiary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }

                    is LiquidSendState.Failed -> {
                        Text(
                            text = sendState.error,
                            style = MaterialTheme.typography.bodyMedium,
                            color = AccentRed,
                        )
                        sendState.refundAddress?.let { refundAddress ->
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Refund address",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = refundAddress,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = TextTertiary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }

                    LiquidSendState.Idle -> Unit
                }

                Spacer(modifier = Modifier.height(24.dp))

                when (sendState) {
                    is LiquidSendState.ReviewReady -> {
                        Button(
                            onClick = onConfirm,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = LiquidTeal, contentColor = DarkBackground),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (preview.kind == LiquidSendKind.LBTC) "Send L-BTC" else "Pay Lightning",
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        IbisButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                        ) {
                            Text("Cancel", style = MaterialTheme.typography.titleMedium)
                        }
                    }

                    is LiquidSendState.Sending -> {
                        val isVerboseButtonText =
                            preview.kind != LiquidSendKind.LBTC && sendState.status.isNotBlank()
                        val buttonText =
                            if (isVerboseButtonText) {
                                sendState.status
                            } else if (preview.kind == LiquidSendKind.LBTC) {
                                "Send L-BTC"
                            } else {
                                "Pay Lightning"
                            }
                        val buttonTextStyle =
                            if (isVerboseButtonText) {
                                MaterialTheme.typography.labelLarge
                            } else {
                                MaterialTheme.typography.titleMedium
                            }
                        val sendingDetail =
                            sendState.detail?.takeIf { it.isNotBlank() }
                                ?: if (sendState.canDismiss) {
                                    "Close anytime. Payment continues in background."
                                } else {
                                    null
                                }
                        Button(
                            onClick = { },
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                            shape = RoundedCornerShape(8.dp),
                            enabled = false,
                            colors = ButtonDefaults.buttonColors(containerColor = LiquidTeal, contentColor = DarkBackground),
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(if (isVerboseButtonText) 18.dp else 20.dp),
                                color = DarkBackground,
                                strokeWidth = 2.dp,
                            )
                            Spacer(modifier = Modifier.width(if (isVerboseButtonText) 6.dp else 8.dp))
                            Text(
                                text = buttonText,
                                modifier = if (isVerboseButtonText) Modifier.weight(1f) else Modifier,
                                style = buttonTextStyle,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                softWrap = false,
                                textAlign = TextAlign.Center,
                            )
                        }
                        if (preview.kind == LiquidSendKind.LBTC && sendState.status.isNotBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = sendState.status,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        sendingDetail?.let { detail ->
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = detail,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextTertiary,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        IbisButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            enabled = sendState.canDismiss,
                        ) {
                            Text(
                                text = if (sendState.canDismiss) "Close" else "Cancel",
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                    }

                    is LiquidSendState.Success,
                    is LiquidSendState.Failed,
                    LiquidSendState.Idle,
                    -> {
                        IbisButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                        ) {
                            Text("Close", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LiquidSendReviewContent(
    preview: LiquidSendPreview,
    useSats: Boolean,
    btcPrice: Double?,
    privacyMode: Boolean,
) {
    if (preview.kind != LiquidSendKind.LBTC) {
        LightningSendReviewContent(
            preview = preview,
            useSats = useSats,
            btcPrice = btcPrice,
            privacyMode = privacyMode,
        )
        return
    }

    val accentColor = LiquidTeal
    val destinationLabel = "Sending To"
    val sendingSubtitle =
        if (preview.recipients.size > 1) {
            "${preview.recipients.size} recipients"
        } else {
            preview.recipientDisplay.let { it.take(8) + "..." + it.takeLast(8) }
        }
    val feeSubtitle =
        preview.txVBytes?.let { "${formatFeeRate(preview.feeRate)} sat/vB • ${formatVBytes(it)} vB" }

    if (preview.recipients.size > 1) {
        Text(
            text = destinationLabel,
            style = MaterialTheme.typography.bodyLarge,
            color = TextSecondary,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "${preview.recipients.size} Liquid recipients",
            style = MaterialTheme.typography.bodyLarge,
            color = TextPrimary,
        )
        Spacer(modifier = Modifier.height(8.dp))
        LazyColumn(
            modifier = Modifier.fillMaxWidth().height(160.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(preview.recipients) { recipient ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = recipient.address.take(12) + "..." + recipient.address.takeLast(8),
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = FontFamily.Monospace,
                            color = TextPrimary,
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text =
                                liquidReviewAmountText(
                                    value = recipient.amountSats.toLong(),
                                    useSats = useSats,
                                    privacyMode = privacyMode,
                                ),
                            style = MaterialTheme.typography.bodySmall,
                            color = accentColor,
                        )
                        liquidReviewUsdSubtext(recipient.amountSats.toLong(), btcPrice, privacyMode)?.let { usdText ->
                            Text(
                                text = usdText,
                                style = MaterialTheme.typography.bodyLarge,
                                color = TextTertiary,
                            )
                        }
                    }
                }
            }
        }
    } else {
        Text(
            text = destinationLabel,
            style = MaterialTheme.typography.bodyLarge,
            color = TextSecondary,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = abbreviateMiddle(preview.recipientDisplay),
            style = MaterialTheme.typography.bodyLarge,
            fontFamily = FontFamily.Monospace,
            color = TextPrimary,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }

    preview.label?.let { label ->
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Label",
            style = MaterialTheme.typography.bodyLarge,
            color = TextSecondary,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = accentColor,
        )
    }

    preview.note?.let { note ->
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = note,
            style = MaterialTheme.typography.bodyLarge,
            color = TextTertiary,
        )
    }

    Spacer(modifier = Modifier.height(16.dp))
    LiquidReviewDivider()
    Spacer(modifier = Modifier.height(16.dp))

    preview.totalRecipientSats?.let { amountSats ->
        LiquidReviewAmountRow(
            label = "Sending:",
            subtitle = sendingSubtitle,
            valueText = liquidReviewAmountText(amountSats, useSats, privacyMode, "-"),
            valueSubtext = liquidReviewUsdSubtext(amountSats, btcPrice, privacyMode),
            color = AccentRed,
        )
        Spacer(modifier = Modifier.height(12.dp))
    }

    preview.changeAddress?.let { changeAddress ->
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
                    text = "Change:",
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextSecondary,
                )
                Text(
                    text = changeAddress.take(8) + "..." + changeAddress.takeLast(8),
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    color = TextSecondary.copy(alpha = 0.7f),
                )
            }
            preview.changeSats?.takeIf { it > 0L }?.let { changeSats ->
                Column(
                    modifier = Modifier.widthIn(min = 96.dp),
                    horizontalAlignment = Alignment.End,
                ) {
                    Text(
                        text = liquidReviewAmountText(changeSats, useSats, privacyMode, "+"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        color = SuccessGreen,
                        textAlign = TextAlign.End,
                        softWrap = false,
                    )
                    liquidReviewUsdSubtext(changeSats, btcPrice, privacyMode)?.let {
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
        Spacer(modifier = Modifier.height(12.dp))
    }

    preview.feeSats?.let { feeSats ->
        LiquidReviewAmountRow(
            label = "Liquid Network Fee:",
            subtitle = feeSubtitle,
            valueText = liquidReviewAmountText(feeSats, useSats, privacyMode, "-"),
            valueSubtext = liquidReviewUsdSubtext(feeSats, btcPrice, privacyMode),
            color = accentColor,
        )
        Spacer(modifier = Modifier.height(12.dp))
    }

    preview.totalSpendSats?.let { total ->
        LiquidReviewDivider()
        Spacer(modifier = Modifier.height(16.dp))
        LiquidReviewAmountRow(
            label = "Total",
            valueText = liquidReviewAmountText(total, useSats, privacyMode, "-"),
            valueSubtext = liquidReviewUsdSubtext(total, btcPrice, privacyMode),
            color = AccentRed,
            bold = true,
        )
        Spacer(modifier = Modifier.height(12.dp))
    }

    preview.selectedUtxoCount.takeIf { it > 0 }?.let { count ->
        Text(
            text = "Spending from $count selected UTXO${if (count > 1) "s" else ""}",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
        )
    }
}

@Composable
private fun LightningSendReviewContent(
    preview: LiquidSendPreview,
    useSats: Boolean,
    btcPrice: Double?,
    privacyMode: Boolean,
) {
    data class LightningFeeLine(
        val label: String,
        val value: Long,
        val subtext: String? = null,
        val valueColor: Color,
    )

    val paymentKindLabel =
        when (preview.kind) {
            LiquidSendKind.LIGHTNING_BOLT12 -> "BOLT12 offer"
            LiquidSendKind.LIGHTNING_BOLT11 -> "Lightning invoice"
            LiquidSendKind.LBTC -> "Liquid payment"
        }
    val boltzFeeSats =
        when (val executionPlan = preview.executionPlan) {
            is LightningPaymentExecutionPlan.BoltzQuote -> executionPlan.swapFeeSats
            is LightningPaymentExecutionPlan.BoltzSwap -> executionPlan.swapFeeSats
            else -> null
        }
    val networkFeeSats =
        preview.feeSats?.let { totalFee ->
            when {
                boltzFeeSats != null -> (totalFee - boltzFeeSats).coerceAtLeast(0L)
                else -> totalFee
            }
        }
    val feeRateInfo =
        preview.txVBytes?.let { "${formatFeeRate(preview.feeRate)} sat/vB • ${formatVBytes(it)} vB" }
    val feeLines =
        buildList {
            boltzFeeSats?.takeIf { it > 0L }?.let { fee ->
                add(
                    LightningFeeLine(
                        label = "Boltz Swap Fee:",
                        value = fee,
                        subtext = "Lightning swap service",
                        valueColor = LiquidTeal,
                    ),
                )
            }
            networkFeeSats?.takeIf { it > 0L }?.let { fee ->
                add(
                    LightningFeeLine(
                        label = "Liquid Network Fee:",
                        value = fee,
                        subtext = feeRateInfo,
                        valueColor = LiquidTeal,
                    ),
                )
            }
        }

    Text(
        text = "Paying To",
            style = MaterialTheme.typography.bodyLarge,
        color = TextSecondary,
    )
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = abbreviateMiddle(preview.recipientDisplay, prefixChars = 16, suffixChars = 16, maxLength = 96),
        style = MaterialTheme.typography.bodyLarge,
        fontFamily = FontFamily.Monospace,
        color = TextPrimary,
        maxLines = 1,
    )

    val refundAddress =
        when (val plan = preview.executionPlan) {
            is LightningPaymentExecutionPlan.BoltzSwap -> plan.refundAddress
            is LightningPaymentExecutionPlan.BoltzQuote -> plan.refundAddress
            else -> null
        }
    if (refundAddress != null) {
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Refund Address",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = abbreviateMiddle(refundAddress, prefixChars = 16, suffixChars = 16, maxLength = 96),
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = TextTertiary,
            maxLines = 1,
        )
    }

    preview.label?.let { label ->
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "Label",
            style = MaterialTheme.typography.bodyLarge,
            color = TextSecondary,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = LiquidTeal,
        )
    }

    Spacer(modifier = Modifier.height(16.dp))
    LiquidReviewDivider()
    Spacer(modifier = Modifier.height(16.dp))

    preview.totalRecipientSats?.let { amountSats ->
        LiquidReviewAmountRow(
            label = "Payment Amount:",
            subtitle = paymentKindLabel,
            valueText = liquidReviewAmountText(amountSats, useSats, privacyMode, "-"),
            valueSubtext = liquidReviewUsdSubtext(amountSats, btcPrice, privacyMode),
            color = AccentRed,
        )
        if (feeLines.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
        }
    }

    feeLines.forEachIndexed { index, line ->
        LiquidReviewFeeRow(
            label = line.label,
            valueText = liquidReviewAmountText(line.value, useSats, privacyMode, "-"),
            subtext = if (privacyMode) null else line.subtext,
            valueSubtext = liquidReviewUsdSubtext(line.value, btcPrice, privacyMode),
            valueColor = line.valueColor,
        )
        if (index < feeLines.lastIndex) {
            Spacer(modifier = Modifier.height(10.dp))
        }
    }
    preview.totalSpendSats?.let { total ->
        Spacer(modifier = Modifier.height(16.dp))
        LiquidReviewDivider()
        Spacer(modifier = Modifier.height(16.dp))
        LiquidReviewAmountRow(
            label = "Total",
            valueText = liquidReviewAmountText(total, useSats, privacyMode, "-"),
            valueSubtext = liquidReviewUsdSubtext(total, btcPrice, privacyMode),
            color = AccentRed,
            bold = true,
        )
        Spacer(modifier = Modifier.height(12.dp))
    }

    preview.selectedUtxoCount.takeIf { it > 0 }?.let { count ->
        Text(
            text = "Funding from $count selected UTXO${if (count > 1) "s" else ""}",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
        )
    }

    preview.note?.let { note ->
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = note,
            style = MaterialTheme.typography.bodyMedium,
            color = TextTertiary,
        )
    }
}

@Composable
private fun LiquidReviewFeeRow(
    label: String,
    valueText: String,
    subtext: String? = null,
    valueSubtext: String? = null,
    emphasize: Boolean = false,
    valueColor: Color = WarningYellow,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = if (emphasize) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
                color = TextSecondary,
                fontWeight = if (emphasize) FontWeight.Medium else FontWeight.Normal,
            )
            subtext?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextTertiary,
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = valueText,
                style = if (emphasize) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleMedium,
                color = if (emphasize) TextPrimary else valueColor,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.End,
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
private fun LiquidReviewAmountRow(
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
            if (subtitle != null) {
                Text(
                    text = subtitle,
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
private fun LiquidReviewDivider() {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(BorderColor),
    )
}

private fun liquidReviewAmountText(
    value: Long,
    useSats: Boolean,
    privacyMode: Boolean,
    prefix: String = "",
): String {
    return if (privacyMode) {
        LIQUID_HIDDEN_AMOUNT
    } else {
        "$prefix${formatLiquidAmount(value, useSats)}"
    }
}

private fun liquidReviewUsdSubtext(
    value: Long,
    btcPrice: Double?,
    privacyMode: Boolean,
): String? {
    if (privacyMode || btcPrice == null || btcPrice <= 0.0) {
        return null
    }
    return formatLiquidUsd(value, btcPrice)
}

private fun abbreviateMiddle(
    value: String,
    prefixChars: Int = 18,
    suffixChars: Int = 14,
    maxLength: Int = 72,
): String {
    if (value.length <= maxLength) return value
    return value.take(prefixChars) + "..." + value.takeLast(suffixChars)
}

@Composable
private fun LiquidCoinControlDialog(
    utxos: List<UtxoInfo>,
    selectedUtxos: List<UtxoInfo>,
    useSats: Boolean,
    btcPrice: Double? = null,
    privacyMode: Boolean,
    spendUnconfirmed: Boolean,
    onUtxoToggle: (UtxoInfo) -> Unit,
    onSelectAll: () -> Unit,
    onClearAll: () -> Unit,
    onDismiss: () -> Unit,
) {
    val totalSelected = selectedUtxos.sumOf { it.amountSats.toLong() }
    val selectedOutpoints =
        remember(selectedUtxos.toList()) {
            selectedUtxos.mapTo(hashSetOf()) { it.outpoint }
        }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            shape = RoundedCornerShape(12.dp),
            color = DarkSurface,
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
            ) {
                Text(
                    text = "Coin Control",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                )

                Spacer(modifier = Modifier.height(8.dp))

                val totalSelectedText =
                    if (selectedUtxos.isNotEmpty()) {
                        if (privacyMode) {
                            "${selectedUtxos.size} selected • $LIQUID_HIDDEN_AMOUNT"
                        } else {
                            val baseText =
                                "${selectedUtxos.size} selected • ${formatLiquidAmount(totalSelected, useSats)}"
                            if (btcPrice != null && btcPrice > 0) {
                                "$baseText · ${formatLiquidUsd(totalSelected, btcPrice)}"
                            } else {
                                baseText
                            }
                        }
                    } else {
                        "Select UTXOs to spend from"
                    }
                Text(
                    text = totalSelectedText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (selectedUtxos.isNotEmpty()) LiquidTeal else TextSecondary,
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextButton(
                        onClick = onSelectAll,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Select All", color = LiquidTeal)
                    }
                    TextButton(
                        onClick = onClearAll,
                        modifier = Modifier.weight(1f),
                        enabled = selectedUtxos.isNotEmpty(),
                    ) {
                        Text(
                            "Clear All",
                            color = if (selectedUtxos.isNotEmpty()) TextSecondary else TextSecondary.copy(alpha = 0.5f),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(350.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(utxos, key = { it.outpoint }) { utxo ->
                        val isSelected = selectedOutpoints.contains(utxo.outpoint)
                        val isDisabled = !utxo.isConfirmed && !spendUnconfirmed

                        Card(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .then(
                                        if (isDisabled) {
                                            Modifier
                                        } else {
                                            Modifier.clickable { onUtxoToggle(utxo) }
                                        },
                                    ),
                            shape = RoundedCornerShape(8.dp),
                            colors =
                                CardDefaults.cardColors(
                                    containerColor =
                                        when {
                                            isDisabled -> DarkCard.copy(alpha = 0.4f)
                                            isSelected -> LiquidTeal.copy(alpha = 0.15f)
                                            else -> DarkCard
                                        },
                                ),
                            border =
                                if (isSelected && !isDisabled) {
                                    BorderStroke(1.dp, LiquidTeal.copy(alpha = 0.5f))
                                } else {
                                    null
                                },
                        ) {
                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector =
                                        if (isSelected && !isDisabled) {
                                            Icons.Default.CheckCircle
                                        } else {
                                            Icons.Default.RadioButtonUnchecked
                                        },
                                    contentDescription = if (isSelected) "Selected" else "Not selected",
                                    tint =
                                        when {
                                            isDisabled -> TextSecondary.copy(alpha = 0.3f)
                                            isSelected -> LiquidTeal
                                            else -> TextSecondary
                                        },
                                    modifier = Modifier.size(24.dp),
                                )

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text =
                                                if (privacyMode) {
                                                    LIQUID_HIDDEN_AMOUNT
                                                } else {
                                                    formatLiquidAmount(utxo.amountSats.toLong(), useSats)
                                                },
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Medium,
                                            color =
                                                if (isDisabled) {
                                                    TextPrimary.copy(alpha = 0.4f)
                                                } else {
                                                    TextPrimary
                                                },
                                        )
                                        if (btcPrice != null && btcPrice > 0 && !privacyMode) {
                                            Text(
                                                text = " · ${formatLiquidUsd(utxo.amountSats.toLong(), btcPrice)}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color =
                                                    if (isDisabled) {
                                                        TextSecondary.copy(alpha = 0.4f)
                                                    } else {
                                                        TextSecondary
                                                    },
                                            )
                                        }
                                    }

                                    Text(
                                        text = "${utxo.address.take(12)}...${utxo.address.takeLast(8)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontFamily = FontFamily.Monospace,
                                        color = if (isDisabled) TextSecondary.copy(alpha = 0.4f) else TextSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )

                                    if (!utxo.label.isNullOrEmpty()) {
                                        Text(
                                            text = utxo.label,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (isDisabled) LiquidTeal.copy(alpha = 0.4f) else LiquidTeal,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }

                                    if (isDisabled) {
                                        Text(
                                            text = "Unconfirmed UTXOs are disabled in settings",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = WarningYellow,
                                        )
                                    }
                                }

                                Box(
                                    modifier =
                                        Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(
                                                if (utxo.isConfirmed) {
                                                    SuccessGreen.copy(alpha = if (isDisabled) 0.1f else 0.2f)
                                                } else {
                                                    LiquidTeal.copy(alpha = if (isDisabled) 0.1f else 0.2f)
                                                },
                                            )
                                            .padding(horizontal = 6.dp, vertical = 2.dp),
                                ) {
                                    Text(
                                        text = if (utxo.isConfirmed) "Confirmed" else "Pending",
                                        style = MaterialTheme.typography.labelSmall,
                                        color =
                                            if (utxo.isConfirmed) {
                                                SuccessGreen.copy(alpha = if (isDisabled) 0.4f else 1f)
                                            } else {
                                                LiquidTeal.copy(alpha = if (isDisabled) 0.4f else 1f)
                                            },
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onDismiss,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = LiquidTeal,
                            contentColor = DarkBackground,
                        ),
                ) {
                    Text(
                        text = "Done",
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun LiquidMultiRecipientDialog(
    recipients: MutableList<Pair<String, String>>,
    useSats: Boolean,
    isUsdMode: Boolean,
    btcPrice: Double?,
    privacyMode: Boolean,
    availableSats: Long,
    selectedUtxoCount: Int,
    estimatedFeeSats: Long?,
    validRecipientCount: Int,
    totalSendingSats: Long,
    previewError: String?,
    onDone: () -> Unit,
    onDismiss: () -> Unit,
) {
    var qrScanRecipientIndex by remember { mutableIntStateOf(-1) }

    if (qrScanRecipientIndex >= 0) {
        QrScannerDialog(
            onCodeScanned = { code ->
                val parsed = parseSendRecipient(code.trim())
                val index = qrScanRecipientIndex
                if (index in recipients.indices && parsed is ParsedSendRecipient.Liquid) {
                    val amount =
                        parsed.amountSats?.let { formatLiquidInputAmount(it, useSats, isUsdMode, btcPrice) }
                            ?: recipients[index].second
                    recipients[index] = parsed.address to amount
                }
                qrScanRecipientIndex = -1
            },
            onDismiss = { qrScanRecipientIndex = -1 },
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            shape = RoundedCornerShape(12.dp),
            color = DarkSurface,
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
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Recipients (${recipients.size})",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                    )
                    TextButton(onClick = onDone) {
                        Text("Done", color = LiquidTeal)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState()),
                ) {
                    recipients.forEachIndexed { index, (address, amount) ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(containerColor = DarkCard),
                        ) {
                            Column(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = "#${index + 1}",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = TextSecondary,
                                    )
                                    if (recipients.size > 2) {
                                        TextButton(
                                            onClick = { recipients.removeAt(index) },
                                            contentPadding = PaddingValues(0.dp),
                                        ) {
                                            Text("Remove", style = MaterialTheme.typography.labelSmall, color = AccentRed)
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                OutlinedTextField(
                                    value = address,
                                    onValueChange = { recipients[index] = it.trim() to amount },
                                    modifier = Modifier.fillMaxWidth(),
                                    placeholder = { Text("Liquid address", color = TextSecondary.copy(alpha = 0.5f)) },
                                    trailingIcon = {
                                        IconButton(onClick = { qrScanRecipientIndex = index }) {
                                            Icon(
                                                imageVector = Icons.Default.QrCodeScanner,
                                                contentDescription = "Scan QR Code",
                                                tint = LiquidTeal,
                                                modifier = Modifier.size(20.dp),
                                            )
                                        }
                                    },
                                    singleLine = true,
                                    shape = RoundedCornerShape(8.dp),
                                    textStyle = MaterialTheme.typography.bodySmall,
                                    colors =
                                        OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = LiquidTeal,
                                            unfocusedBorderColor = BorderColor,
                                            focusedTextColor = TextPrimary,
                                            unfocusedTextColor = TextPrimary,
                                            cursorColor = LiquidTeal,
                                        ),
                                )

                                Spacer(modifier = Modifier.height(6.dp))

                                OutlinedTextField(
                                    value = amount,
                                    onValueChange = { value ->
                                        recipients[index] =
                                            address to filterLiquidAmountInput(
                                                value = value,
                                                useSats = useSats,
                                                isUsdMode = isUsdMode,
                                            )
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    placeholder = {
                                        Text(
                                            text =
                                                when {
                                                    isUsdMode -> "Amount (USD)"
                                                    useSats -> "Amount (sats)"
                                                    else -> "Amount (BTC)"
                                                },
                                            color = TextSecondary.copy(alpha = 0.5f),
                                        )
                                    },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    singleLine = true,
                                    shape = RoundedCornerShape(8.dp),
                                    textStyle = MaterialTheme.typography.bodySmall,
                                    colors =
                                        OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = LiquidTeal,
                                            unfocusedBorderColor = BorderColor,
                                            focusedTextColor = TextPrimary,
                                            unfocusedTextColor = TextPrimary,
                                            cursorColor = LiquidTeal,
                                        ),
                                )

                                val addressError = liquidMultiRecipientError(address)
                                if (addressError != null) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = addressError,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = AccentRed,
                                    )
                                }
                            }
                        }
                        if (index < recipients.lastIndex) {
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedButton(
                    onClick = { recipients.add("" to "") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, BorderColor),
                    contentPadding = PaddingValues(vertical = 10.dp),
                ) {
                    Text(
                        text = "+ Add Recipient",
                        style = MaterialTheme.typography.labelLarge,
                        color = TextSecondary,
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(BorderColor),
                )

                Spacer(modifier = Modifier.height(10.dp))

                previewError?.let {
                    if (validRecipientCount > 0) {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = WarningYellow,
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                }

                val feeSats = estimatedFeeSats ?: 0L
                val totalWithFee = totalSendingSats + feeSats
                val remainingSats = availableSats - totalWithFee
                val overBudget = remainingSats < 0L && validRecipientCount > 0

                if (validRecipientCount > 0) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "Sending ($validRecipientCount recipients)",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                        )
                        Text(
                            text =
                                if (privacyMode) {
                                    LIQUID_HIDDEN_AMOUNT
                                } else {
                                    formatLiquidAmount(totalSendingSats, useSats)
                                },
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = TextPrimary,
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                }

                if (estimatedFeeSats != null && validRecipientCount > 0) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "Est. fee",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                        )
                        Text(
                            text =
                                if (privacyMode) {
                                    LIQUID_HIDDEN_AMOUNT
                                } else {
                                    formatLiquidAmount(estimatedFeeSats, useSats)
                                },
                            style = MaterialTheme.typography.bodySmall,
                            color = LiquidTeal,
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                }

                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "Available",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                    Text(
                        text =
                            if (privacyMode) {
                                LIQUID_HIDDEN_AMOUNT
                            } else {
                                formatLiquidAmount(availableSats, useSats)
                            },
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "Remaining",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary,
                    )
                    Text(
                        text =
                            if (privacyMode) {
                                LIQUID_HIDDEN_AMOUNT
                            } else {
                                formatLiquidAmount(maxOf(0L, remainingSats), useSats)
                            },
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = if (overBudget) AccentRed else SuccessGreen,
                    )
                }

                if (overBudget) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Total exceeds available balance by ${formatLiquidAmount(-remainingSats, useSats)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = AccentRed,
                    )
                }

                if (selectedUtxoCount > 0) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "$selectedUtxoCount selected UTXO${if (selectedUtxoCount > 1) "s" else ""}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                }
            }
        }
    }
}

private fun currentLiquidSendPreview(sendState: LiquidSendState): LiquidSendPreview? {
    return when (sendState) {
        LiquidSendState.Idle -> null
        is LiquidSendState.ReviewReady -> sendState.preview
        is LiquidSendState.Sending -> sendState.preview
        is LiquidSendState.Success -> sendState.preview
        is LiquidSendState.Failed -> sendState.preview
    }
}

private fun liquidSendDialogTitle(
    sendState: LiquidSendState,
    kind: LiquidSendKind,
): String {
    return when (sendState) {
        LiquidSendState.Idle,
        is LiquidSendState.ReviewReady,
        -> when (kind) {
            LiquidSendKind.LBTC -> "Review Transaction"
            LiquidSendKind.LIGHTNING_BOLT12 -> "Pay BOLT12 Offer?"
            LiquidSendKind.LIGHTNING_BOLT11 -> "Pay Lightning Invoice?"
        }

        is LiquidSendState.Sending ->
            if (kind == LiquidSendKind.LBTC) {
                "Sending L-BTC"
            } else {
                "Paying Lightning"
            }

        is LiquidSendState.Success ->
            if (kind == LiquidSendKind.LBTC) {
                "L-BTC Sent"
            } else {
                "Lightning Payment Sent"
            }

        is LiquidSendState.Failed ->
            if (kind == LiquidSendKind.LBTC) {
                "L-BTC Send Failed"
            } else {
                "Lightning Payment Failed"
            }
    }
}

private data class RecipientModeBadge(
    val label: String,
    val tint: Color,
)

private fun buildRecipientModeBadges(
    parsedRecipient: ParsedSendRecipient,
): List<RecipientModeBadge> {
    return when (parsedRecipient) {
        is ParsedSendRecipient.Liquid -> listOf(
            RecipientModeBadge("Liquid", LiquidTeal),
        )
        is ParsedSendRecipient.Lightning ->
            listOf(
                RecipientModeBadge(
                    if (parsedRecipient.kind == LightningKind.BOLT12) "BOLT12" else "BOLT11",
                    WarningYellow,
                ),
            )
        else -> emptyList()
    }
}

@Composable
private fun RecipientModeBadgeChip(badge: RecipientModeBadge) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = badge.tint.copy(alpha = 0.12f),
        contentColor = badge.tint,
    ) {
        Text(
            text = badge.label,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

private fun liquidMultiRecipientError(input: String): String? {
    if (input.isBlank()) return null
    return when (val parsed = parseSendRecipient(input.trim())) {
        is ParsedSendRecipient.Liquid -> null
        is ParsedSendRecipient.Lightning,
        is ParsedSendRecipient.UnsupportedLightning,
        -> "Multiple send only supports Liquid recipients"
        is ParsedSendRecipient.Unknown -> parsed.errorMessage
        else -> "Enter a Liquid address or liquidnetwork URI"
    }
}

private fun parseLiquidAmountInputToSats(
    value: String,
    useSats: Boolean,
    isUsdMode: Boolean,
    btcPrice: Double?,
): Long {
    return try {
        when {
            value.isBlank() -> 0L
            isUsdMode && btcPrice != null && btcPrice > 0 -> ((value.toDoubleOrNull() ?: 0.0) / btcPrice * 100_000_000).roundToLong()
            useSats -> value.replace(",", "").toLongOrNull() ?: 0L
            else -> ((value.toDoubleOrNull() ?: 0.0) * 100_000_000).roundToLong()
        }
    } catch (_: Exception) {
        0L
    }
}

private fun filterLiquidAmountInput(
    value: String,
    useSats: Boolean,
    isUsdMode: Boolean,
): String {
    return when {
        isUsdMode -> if (value.isEmpty() || value.matches(Regex("^\\d*\\.?\\d{0,2}$"))) value else value.dropLast(1)
        useSats -> if (value.isEmpty() || value.matches(Regex("^\\d*$"))) value else value.dropLast(1)
        else -> if (value.isEmpty() || value.matches(Regex("^\\d*\\.?\\d{0,8}$"))) value else value.dropLast(1)
    }
}

private fun formatLiquidInputAmount(
    amountSats: Long,
    useSats: Boolean,
    isUsdMode: Boolean,
    btcPrice: Double?,
): String {
    return when {
        isUsdMode && btcPrice != null && btcPrice > 0 -> {
            val usdValue = (amountSats.toDouble() / 100_000_000.0) * btcPrice
            String.format(Locale.US, "%.2f", usdValue)
        }
        useSats -> amountSats.toString()
        else -> String.format(Locale.US, "%.8f", amountSats / 100_000_000.0).trimEnd('0').trimEnd('.')
    }
}

private fun formatLiquidAmount(amountSats: Long, useSats: Boolean): String {
    return if (useSats) {
        "${"%,d".format(amountSats)} sats"
    } else {
        "${String.format(Locale.US, "%.8f", amountSats / 100_000_000.0).trimEnd('0').trimEnd('.')} BTC"
    }
}

private fun formatLiquidUsd(amountSats: Long, btcPrice: Double): String {
    val usdValue = (amountSats.toDouble() / 100_000_000.0) * btcPrice
    return formatUsd(usdValue)
}

private fun LightningKind.toLiquidSendKind(): LiquidSendKind {
    return when (this) {
        LightningKind.BOLT11 -> LiquidSendKind.LIGHTNING_BOLT11
        LightningKind.BOLT12 -> LiquidSendKind.LIGHTNING_BOLT12
    }
}

private const val MIN_LIQUID_FEE_RATE = 0.1
private const val LIQUID_HIDDEN_AMOUNT = "****"
