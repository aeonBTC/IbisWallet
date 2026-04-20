@file:Suppress("AssignedValueIsNeverRead")

package github.aeonbtc.ibiswallet.ui.screens

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import github.aeonbtc.ibiswallet.MainActivity
import github.aeonbtc.ibiswallet.R
import github.aeonbtc.ibiswallet.data.local.SecureStorage
import github.aeonbtc.ibiswallet.data.model.LightningPaymentExecutionPlan
import github.aeonbtc.ibiswallet.data.model.LiquidAsset
import github.aeonbtc.ibiswallet.data.model.LiquidSendKind
import github.aeonbtc.ibiswallet.data.model.LiquidSendPreview
import github.aeonbtc.ibiswallet.data.model.LiquidSendState
import github.aeonbtc.ibiswallet.data.model.LiquidTransaction
import github.aeonbtc.ibiswallet.data.model.LiquidTxSource
import github.aeonbtc.ibiswallet.data.model.LiquidWalletState
import github.aeonbtc.ibiswallet.data.model.PendingLightningPaymentSession
import github.aeonbtc.ibiswallet.data.model.Recipient
import github.aeonbtc.ibiswallet.data.model.UtxoInfo
import github.aeonbtc.ibiswallet.ui.components.AmountLabel
import github.aeonbtc.ibiswallet.ui.components.IbisButton
import github.aeonbtc.ibiswallet.ui.components.NfcStatusIndicator
import github.aeonbtc.ibiswallet.ui.components.QrScannerDialog
import github.aeonbtc.ibiswallet.ui.components.ScrollableDialogSurface
import github.aeonbtc.ibiswallet.ui.components.formatFeeRate
import github.aeonbtc.ibiswallet.ui.components.rememberBringIntoViewRequesterOnExpand
import github.aeonbtc.ibiswallet.ui.theme.AccentRed
import github.aeonbtc.ibiswallet.ui.theme.AccentTeal
import github.aeonbtc.ibiswallet.ui.theme.BitcoinOrange
import github.aeonbtc.ibiswallet.ui.theme.BorderColor
import github.aeonbtc.ibiswallet.ui.theme.DarkBackground
import github.aeonbtc.ibiswallet.ui.theme.DarkCard
import github.aeonbtc.ibiswallet.ui.theme.DarkSurface
import github.aeonbtc.ibiswallet.ui.theme.LightningYellow
import github.aeonbtc.ibiswallet.ui.theme.LiquidTeal
import github.aeonbtc.ibiswallet.ui.theme.SuccessGreen
import github.aeonbtc.ibiswallet.ui.theme.TextPrimary
import github.aeonbtc.ibiswallet.ui.theme.TextSecondary
import github.aeonbtc.ibiswallet.ui.theme.TextTertiary
import github.aeonbtc.ibiswallet.ui.theme.WarningYellow
import github.aeonbtc.ibiswallet.util.LightningKind
import github.aeonbtc.ibiswallet.util.ParsedSendRecipient
import github.aeonbtc.ibiswallet.util.SecureClipboard
import github.aeonbtc.ibiswallet.util.getNfcAvailability
import github.aeonbtc.ibiswallet.util.layer2RecipientValidationError
import github.aeonbtc.ibiswallet.util.parseSendRecipient
import github.aeonbtc.ibiswallet.viewmodel.SendScreenDraft
import github.aeonbtc.ibiswallet.nfc.NfcReaderUiState
import github.aeonbtc.ibiswallet.nfc.NfcRuntimeStatus
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToLong

@Composable
fun LiquidSendScreen(
    denomination: String = SecureStorage.DENOMINATION_BTC,
    btcPrice: Double? = null,
    fiatCurrency: String = SecureStorage.DEFAULT_PRICE_CURRENCY,
    privacyMode: Boolean = false,
    boltzEnabled: Boolean = true,
    isLiquidWatchOnly: Boolean = false,
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
    onCreatePset: (
        address: String,
        amountSats: Long,
        feeRate: Double,
        selectedUtxos: List<UtxoInfo>?,
        isMaxSend: Boolean,
        label: String?,
    ) -> Unit = { _, _, _, _, _, _ -> },
    onPreviewAssetSend: (
        address: String,
        amount: Long,
        assetId: String,
        feeRate: Double,
        selectedUtxos: List<UtxoInfo>?,
        label: String?,
    ) -> Unit = { _, _, _, _, _, _ -> },
    onSendAsset: (
        address: String,
        amount: Long,
        assetId: String,
        feeRate: Double,
        selectedUtxos: List<UtxoInfo>?,
        label: String?,
    ) -> Unit = { _, _, _, _, _, _ -> },
    onCreateAssetPset: (
        address: String,
        amount: Long,
        assetId: String,
        feeRate: Double,
        selectedUtxos: List<UtxoInfo>?,
        label: String?,
    ) -> Unit = { _, _, _, _, _, _ -> },
    pendingSubmarineSwap: PendingLightningPaymentSession? = null,
    boltzRescueMnemonic: String? = null,
    preSelectedUtxo: UtxoInfo? = null,
    onClearPreSelectedUtxo: () -> Unit = {},
    onClearDraft: () -> Unit = {},
    onResetSend: () -> Unit = {},
    onToggleDenomination: () -> Unit = {},
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
    val selectedUtxos = remember {
        mutableStateListOf<UtxoInfo>().also { list ->
            if (preSelectedUtxo != null) list.add(preSelectedUtxo)
        }
    }

    var selectedSendAsset by remember {
        val fromPreSelected = preSelectedUtxo?.assetId
            ?.takeIf { !LiquidAsset.isPolicyAsset(it) }
            ?.let { LiquidAsset.resolve(it) }
        val fromDraft = draft.assetId
            ?.takeIf { !LiquidAsset.isPolicyAsset(it) }
            ?.let { LiquidAsset.resolve(it) }
        mutableStateOf(fromPreSelected ?: fromDraft)
    }

    LaunchedEffect(preSelectedUtxo) {
        if (preSelectedUtxo != null) {
            selectedUtxos.clear()
            selectedUtxos.add(preSelectedUtxo)
            val assetId = preSelectedUtxo.assetId
            selectedSendAsset = if (assetId != null && !LiquidAsset.isPolicyAsset(assetId)) LiquidAsset.resolve(assetId) else null
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
        selectedSendAsset = null
        isMultiMode = false
        showMultiDialog = false
        multiRecipients.clear()
        isMaxMode = false
        showLabelField = false
        labelText = ""
    }

    val useSats = denomination == SecureStorage.DENOMINATION_SATS
    val sendAsset: LiquidAsset? = selectedSendAsset
    val isAssetMode = sendAsset != null
    val parsedRecipient = remember(recipientAddress) { parseSendRecipient(recipientAddress) }
    val liquidRecipient = parsedRecipient as? ParsedSendRecipient.Liquid
    val lightningRecipient = parsedRecipient as? ParsedSendRecipient.Lightning
    val isLightningPayment = lightningRecipient != null
    val canEditLightningAmount =
        lightningRecipient?.let {
            (it.kind == LightningKind.BOLT12 && it.amountSats == null) ||
                (it.kind == LightningKind.LNURL && it.amountSats == null)
        } == true
    val isMultiAvailable = !isLightningPayment && !isLiquidWatchOnly
    val isMaxAvailable = !isLightningPayment || canEditLightningAmount
    val isUsdAmountModeAvailable = (!isLightningPayment || canEditLightningAmount) && !isAssetMode
    val recipientModeBadges = remember(parsedRecipient) {
        buildRecipientModeBadges(parsedRecipient)
    }
    val addressValidationError = remember(parsedRecipient, isMultiMode, isAssetMode) {
        if (isMultiMode) {
            null
        } else if (isAssetMode && parsedRecipient is ParsedSendRecipient.Lightning) {
            "Lightning is not available for ${sendAsset.ticker} sends"
        } else {
            layer2RecipientValidationError(parsedRecipient)
        }
    }
    val isAddressValid = recipientAddress.isNotBlank() && addressValidationError == null
    val allSpendableUtxos = remember(liquidUtxos) { liquidUtxos.filter { !it.isFrozen } }
    val spendableUtxos = remember(allSpendableUtxos, sendAsset) {
        if (sendAsset != null) {
            allSpendableUtxos.filter { it.assetId == sendAsset.assetId }
        } else {
            allSpendableUtxos.filter { utxo ->
                val assetId = utxo.assetId
                assetId == null || LiquidAsset.isPolicyAsset(assetId)
            }
        }
    }
    val selectedUtxoSnapshot = selectedUtxos.toList()
    val availableSats =
        remember(selectedUtxoSnapshot, liquidState.balanceSats, liquidState.assetBalances, sendAsset) {
            if (selectedUtxoSnapshot.isNotEmpty()) {
                selectedUtxoSnapshot.sumOf { it.amountSats.toLong() }
            } else if (sendAsset != null) {
                liquidState.balanceForAsset(sendAsset.assetId)
            } else {
                liquidState.balanceSats.coerceAtLeast(0L)
            }
        }
    val coinControlAsset =
        remember(selectedUtxoSnapshot) {
            if (selectedUtxoSnapshot.isEmpty()) null
            else {
                val nonLbtc = selectedUtxoSnapshot.filter { u ->
                    val assetId = u.assetId
                    assetId != null && !LiquidAsset.isPolicyAsset(assetId)
                }
                nonLbtc.firstOrNull()?.assetId?.let { LiquidAsset.resolve(it) }
            }
        }
    val amountSats = if (isAssetMode) {
        parseAssetAmountToBaseUnits(amountInput, sendAsset)
    } else {
        parseLiquidAmountInputToSats(amountInput, useSats, isUsdMode, btcPrice)
    }
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
            !isLiquidWatchOnly &&
            !isSendLocked &&
            when {
                isMultiMode -> multiRecipientList.size >= 2
                lightningRecipient != null ->
                    isAddressValid &&
                        (
                            lightningRecipient.amountSats != null ||
                                !canEditLightningAmount ||
                                lightningRequestedAmountSats?.let { it > 0L } == true
                        )
                liquidRecipient != null -> isAddressValid && (amountSats > 0L || isMaxMode)
                else -> false
            }

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
                recipientAddress.isNotEmpty() &&
                draft.assetId == null &&
                draft.selectedUtxoOutpoints.isEmpty()
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
        val draftAssetId = draft.assetId
        if (draftAssetId != null && !LiquidAsset.isPolicyAsset(draftAssetId)) {
            selectedSendAsset = LiquidAsset.resolve(draftAssetId)
        }
    }

    LaunchedEffect(recipientAddress, amountInput, labelText, feeRate, isMaxMode, selectedUtxoSnapshot, sendAsset) {
        onUpdateDraft(
            SendScreenDraft(
                recipientAddress = recipientAddress,
                amountInput = amountInput,
                label = labelText,
                feeRate = feeRate,
                isMaxSend = isMaxMode,
                selectedUtxoOutpoints = selectedUtxoSnapshot.map { it.outpoint },
                assetId = sendAsset?.assetId,
            ),
        )
    }
    LaunchedEffect(liquidSendState) {
        val isSendingNow = liquidSendState is LiquidSendState.Sending
        if (wasSending && !isSendingNow) {
            when (liquidSendState) {
                is LiquidSendState.Failed -> {
                    dismissedSendError = null
                    clearLocalSendForm()
                    onClearDraft()
                    showConfirmDialog = true
                }
                is LiquidSendState.Success -> {
                    dismissedSendError = null
                    showConfirmDialog = false
                    clearLocalSendForm()
                    onClearDraft()
                    onResetSend()
                }
                is LiquidSendState.Estimating -> Unit
                else -> Unit
            }
        }
        if (liquidSendState is LiquidSendState.ReviewReady) {
            dismissedSendError = null
        }
        wasSending = isSendingNow
    }

    LaunchedEffect(liquidSendState, isMaxMode, isMultiMode, isUsdMode, useSats, btcPrice, isAssetMode) {
        if (!isMaxMode || isMultiMode) return@LaunchedEffect
        val maxPreview = liquidSendState as? LiquidSendState.ReviewReady ?: return@LaunchedEffect
        val maxAmount = maxPreview.preview.totalRecipientSats ?: return@LaunchedEffect
        amountInput = if (isAssetMode) {
            val divisor = 10.0.pow(sendAsset.precision.toDouble())
            val full = String.format(Locale.US, "%.${sendAsset.precision}f", maxAmount.toDouble() / divisor)
            full.trimEnd('0').trimEnd('.')
        } else {
            formatLiquidInputAmount(maxAmount, useSats, isUsdMode, btcPrice)
        }
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
        delay(150)
        val effectiveAssetId = (parsedRecipient as? ParsedSendRecipient.Liquid)?.assetId ?: draft.assetId
        val isAssetSend = effectiveAssetId != null && !LiquidAsset.isPolicyAsset(effectiveAssetId)
        when (parsedRecipient) {
            is ParsedSendRecipient.Liquid -> {
                if (isAddressValid && (amountSats > 0L || isMaxMode)) {
                    if (isAssetSend) {
                        onPreviewAssetSend(
                            parsedRecipient.address,
                            amountSats,
                            effectiveAssetId,
                            feeRate,
                            selectedUtxoSnapshot.ifEmpty { null },
                            txLabel,
                        )
                    } else {
                        onPreviewLiquidSend(
                            parsedRecipient.address,
                            amountSats,
                            feeRate,
                            selectedUtxoSnapshot.ifEmpty { null },
                            isMaxMode,
                            txLabel,
                        )
                    }
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
        showConfirmDialog,
    ) {
        if (!isMultiMode || isSendLocked || showConfirmDialog) return@LaunchedEffect
        if (!liquidState.isInitialized) {
            onResetSend()
            return@LaunchedEffect
        }
        delay(150)
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
    val mainActivity = context as? MainActivity
    val nfcReaderOwner = remember { Any() }
    val isPreview = LocalInspectionMode.current
    val nfcAvailable = !isPreview && context.getNfcAvailability().canRead
    DisposableEffect(mainActivity, nfcAvailable) {
        if (mainActivity != null && nfcAvailable) {
            mainActivity.requestNfcReaderMode(nfcReaderOwner)
        }
        onDispose {
            mainActivity?.releaseNfcReaderMode(nfcReaderOwner)
        }
    }
    val isNfcReaderActive = nfcAvailable && mainActivity?.isNfcReaderModeActive == true
    val nfcReaderState by NfcRuntimeStatus.readerState.collectAsState()

    if (showCoinControl) {
        LiquidCoinControlDialog(
            utxos = spendableUtxos,
            selectedUtxos = selectedUtxos,
            useSats = useSats,
            btcPrice = btcPrice,
            fiatCurrency = fiatCurrency,
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
            fiatCurrency = fiatCurrency,
            privacyMode = privacyMode,
            availableSats = availableSats,
            coinControlAsset = coinControlAsset,
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
            when (liquidSendState) {
                is LiquidSendState.Estimating,
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
            isLiquidWatchOnly = isLiquidWatchOnly,
            useSats = useSats,
            btcPrice = btcPrice,
            fiatCurrency = fiatCurrency,
            privacyMode = privacyMode,
            onConfirm = {
                val confirmAssetId = (parsedRecipient as? ParsedSendRecipient.Liquid)?.assetId ?: draft.assetId
                val confirmIsAssetSend = confirmAssetId != null && !LiquidAsset.isPolicyAsset(confirmAssetId)
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
                } else if (confirmIsAssetSend) {
                    if (isLiquidWatchOnly) {
                        onCreateAssetPset(
                            liquidRecipient?.address ?: recipientAddress.trim(),
                            amountSats,
                            confirmAssetId,
                            feeRate,
                            selectedUtxoSnapshot.ifEmpty { null },
                            txLabel,
                        )
                    } else {
                        onSendAsset(
                            liquidRecipient?.address ?: recipientAddress.trim(),
                            amountSats,
                            confirmAssetId,
                            feeRate,
                            selectedUtxoSnapshot.ifEmpty { null },
                            txLabel,
                        )
                    }
                } else if (isLiquidWatchOnly) {
                    onCreatePset(
                        liquidRecipient?.address ?: recipientAddress.trim(),
                        amountSats,
                        feeRate,
                        selectedUtxoSnapshot.ifEmpty { null },
                        isMaxMode,
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
                    is LiquidSendState.Estimating,
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

    val pendingLiquidTxs = remember(liquidState.transactions) {
        liquidState.transactions.filter { tx ->
            tx.height == null && tx.balanceSatoshi < 0 && tx.source == LiquidTxSource.NATIVE
        }
    }
    val hasPendingPayments = pendingSubmarineSwap != null || pendingLiquidTxs.isNotEmpty()
    val pendingCount = (if (pendingSubmarineSwap != null) 1 else 0) + pendingLiquidTxs.size
    var pendingCardExpanded by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (hasPendingPayments) {
            PendingPaymentsCard(
                pendingSubmarineSwap = pendingSubmarineSwap,
                pendingLiquidTxs = pendingLiquidTxs,
                pendingCount = pendingCount,
                boltzRescueMnemonic = boltzRescueMnemonic,
                expanded = pendingCardExpanded,
                onExpandedChange = { pendingCardExpanded = it },
                privacyMode = privacyMode,
                useSats = useSats,
            )
            Spacer(modifier = Modifier.height(8.dp))
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top,
                ) {
                    Column {
                        val nonLbtcAssets = liquidState.assetBalances.filter { !it.asset.isPolicyAsset }
                        if (nonLbtcAssets.isNotEmpty()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Send ",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = TextPrimary,
                                )
                                val currentLabel = if (isAssetMode) sendAsset.ticker else "L-BTC"
                                Card(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable {
                                            selectedSendAsset = if (isAssetMode) null else nonLbtcAssets.first().asset
                                            selectedUtxos.clear()
                                            amountInput = ""
                                            isMaxMode = false
                                            isUsdMode = false
                                            isMultiMode = false
                                        },
                                    shape = RoundedCornerShape(6.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = LiquidTeal.copy(alpha = 0.15f),
                                    ),
                                    border = BorderStroke(1.dp, LiquidTeal),
                                ) {
                                    Text(
                                        text = currentLabel,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = LiquidTeal,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
                                    )
                                }
                            }
                        } else {
                            Text(
                                text = "Send L-BTC",
                                style = MaterialTheme.typography.titleLarge,
                                color = TextPrimary,
                            )
                        }
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
                                    BitcoinOrange
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
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
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
                                        if (multiRecipients.isEmpty()) {
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
                                        }
                                        showMultiDialog = true
                                    } else {
                                        isMultiMode = false
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
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
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
                                text = if (isAssetMode) "Liquid address" else if (boltzEnabled) "Liquid, BOLT 11/12, or LN Address" else "Liquid address",
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
                        if (isAssetMode) {
                            Text(
                                text = "Amount (${sendAsset.ticker})",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                            )
                        } else {
                            AmountLabel(
                                useSats = useSats,
                                isUsdMode = isUsdMode && !isLightningPayment,
                                fiatCurrency = fiatCurrency,
                                showDenomination = !isLightningPayment || lightningRequestedAmountSats != null,
                                onToggleDenomination = onToggleDenomination,
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
                                        text = fiatCurrency,
                                        style = MaterialTheme.typography.labelMedium,
                                        color =
                                            when {
                                                isUsdMode -> LiquidTeal
                                                isUsdAmountModeAvailable -> TextSecondary
                                                else -> TextSecondary.copy(alpha = 0.5f)
                                            },
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))

                    val conversionText =
                        if (isAssetMode) {
                            null
                        } else if (amountInput.isNotEmpty() && amountSats > 0 && btcPrice != null && btcPrice > 0) {
                            if (privacyMode) {
                                "≈ $LIQUID_HIDDEN_AMOUNT"
                            } else if (isUsdMode) {
                                "≈ ${formatLiquidAmount(amountSats, useSats)}"
                            } else {
                                "≈ ${formatLiquidUsd(amountSats, btcPrice, fiatCurrency)}"
                            }
                        } else {
                            null
                        }

                    OutlinedTextField(
                        value = amountInput,
                        onValueChange = { value ->
                            isMaxMode = false
                            amountInput =
                                if (isAssetMode) {
                                    filterAssetAmountInput(value, sendAsset.precision)
                                } else {
                                    filterLiquidAmountInput(
                                        value = value,
                                        useSats = useSats,
                                        isUsdMode = isUsdMode,
                                    )
                                }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = {
                            Text(
                                text =
                                    when {
                                        isAssetMode -> "0.00"
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
                                { Text(fiatCurrency, color = TextSecondary) }
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
                        if (isAssetMode && !privacyMode) {
                            Text(
                                text = formatCoinControlAssetAmount(sendAsset, availableSats),
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                            )
                        } else {
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
                                    text = " · ${formatLiquidUsd(availableSats, btcPrice, fiatCurrency)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary.copy(alpha = 0.7f),
                                )
                            }
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
                                                if (isAssetMode) {
                                                    val divisor = 10.0.pow(sendAsset.precision.toDouble())
                                                    val full = String.format(Locale.US, "%.${sendAsset.precision}f", availableSats.toDouble() / divisor)
                                                    full.trimEnd('0').trimEnd('.')
                                                } else if (useSats) {
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
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
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
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
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
                    fiatCurrency = fiatCurrency,
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

                if (isLiquidWatchOnly) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = LIQUID_PSET_DISABLED_MESSAGE,
                        style = MaterialTheme.typography.bodySmall,
                        color = WarningYellow,
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
                } else if (isMultiMode) {
                    showConfirmDialog = true
                    onPreviewLiquidSendMulti(
                        multiRecipientList,
                        feeRate,
                        selectedUtxoSnapshot.ifEmpty { null },
                        txLabel,
                    )
                } else {
                    showConfirmDialog = true
                    val btnAssetId = (parsedRecipient as? ParsedSendRecipient.Liquid)?.assetId ?: draft.assetId
                    val btnIsAssetSend = btnAssetId != null && !LiquidAsset.isPolicyAsset(btnAssetId)
                    if (btnIsAssetSend) {
                        onPreviewAssetSend(
                            liquidRecipient?.address ?: recipientAddress.trim(),
                            amountSats,
                            btnAssetId,
                            feeRate,
                            selectedUtxoSnapshot.ifEmpty { null },
                            txLabel,
                        )
                    } else {
                        onPreviewLiquidSend(
                            liquidRecipient?.address ?: recipientAddress.trim(),
                            amountSats,
                            feeRate,
                            selectedUtxoSnapshot.ifEmpty { null },
                            isMaxMode,
                            txLabel,
                        )
                    }
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
                text =
                    when {
                        isLightningPayment -> "Review Payment"
                        isLiquidWatchOnly -> "PSET Disabled"
                        else -> "Review Transaction"
                    },
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
    fiatCurrency: String,
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
                    " · ${formatLiquidUsd(feeSats, btcPrice, fiatCurrency)}"
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
    return if (draft.feeRate <= MIN_LIQUID_FEE_RATE || draft.feeRate == SendScreenDraft().feeRate) {
        MIN_LIQUID_FEE_RATE
    } else {
        draft.feeRate
    }
}

@Composable
private fun LiquidSendConfirmationDialog(
    sendState: LiquidSendState,
    isLiquidWatchOnly: Boolean,
    useSats: Boolean,
    btcPrice: Double?,
    fiatCurrency: String,
    privacyMode: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val preview = currentLiquidSendPreview(sendState)
    val dialogKind =
        preview?.kind ?: when (sendState) {
            is LiquidSendState.Estimating -> sendState.kind
            else -> null
        }
    val canDismissDialog = (sendState as? LiquidSendState.Sending)?.canDismiss ?: true
    ScrollableDialogSurface(
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
        containerColor = DarkSurface,
        actions = {
            when (sendState) {
                is LiquidSendState.Estimating -> {
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
                            modifier = Modifier.size(20.dp),
                            color = DarkBackground,
                            strokeWidth = 2.dp,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Estimating Fee...",
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    IbisButton(
                        onClick = onDismiss,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                    ) {
                        Text("Cancel", style = MaterialTheme.typography.titleMedium)
                    }
                }
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
                            text =
                                when {
                                    preview?.kind == LiquidSendKind.LIQUID_ASSET ->
                                        "Send ${preview.resolvedAsset.ticker}"
                                    preview?.kind != LiquidSendKind.LBTC -> "Pay Lightning"
                                    isLiquidWatchOnly -> "Create Unsigned PSET"
                                    else -> "Send L-BTC"
                                },
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    IbisButton(
                        onClick = onDismiss,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                    ) {
                        Text("Cancel", style = MaterialTheme.typography.titleMedium)
                    }
                }

                is LiquidSendState.Sending -> {
                    val isVerboseButtonText =
                        preview?.kind != LiquidSendKind.LBTC && preview?.kind != LiquidSendKind.LIQUID_ASSET && sendState.status.isNotBlank()
                    val buttonText =
                        if (isVerboseButtonText) {
                            sendState.status
                        } else if (preview?.kind == LiquidSendKind.LIQUID_ASSET) {
                            "Send ${preview.resolvedAsset.ticker}"
                        } else if (preview?.kind == LiquidSendKind.LBTC) {
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
                    if (preview?.kind == LiquidSendKind.LBTC && sendState.status.isNotBlank()) {
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
                        modifier =
                            Modifier
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
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                    ) {
                        Text("Close", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        },
    ) {
                Text(
                    text = dialogKind?.let { liquidSendDialogTitle(sendState, it, isLiquidWatchOnly) } ?: "Review Transaction",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                )

                Spacer(modifier = Modifier.height(16.dp))

                when (sendState) {
                    is LiquidSendState.Estimating -> {
                        if (preview != null) {
                            LiquidSendReviewContent(
                                preview = preview,
                                useSats = useSats,
                                btcPrice = btcPrice,
                                fiatCurrency = fiatCurrency,
                                privacyMode = privacyMode,
                            )
                        } else {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(28.dp),
                                    color = LiquidTeal,
                                    strokeWidth = 2.dp,
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Building transaction preview...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextPrimary,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }
                    is LiquidSendState.ReviewReady -> LiquidSendReviewContent(
                        preview = preview ?: return@ScrollableDialogSurface,
                        useSats = useSats,
                        btcPrice = btcPrice,
                        fiatCurrency = fiatCurrency,
                        privacyMode = privacyMode,
                    )

                    is LiquidSendState.Sending -> LiquidSendReviewContent(
                        preview = preview ?: return@ScrollableDialogSurface,
                        useSats = useSats,
                        btcPrice = btcPrice,
                        fiatCurrency = fiatCurrency,
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
    }
}

@Composable
private fun LiquidSendReviewContent(
    preview: LiquidSendPreview,
    useSats: Boolean,
    btcPrice: Double?,
    fiatCurrency: String,
    privacyMode: Boolean,
) {
    if (preview.kind == LiquidSendKind.LIQUID_ASSET) {
        AssetSendReviewContent(
            preview = preview,
            useSats = useSats,
            btcPrice = btcPrice,
            fiatCurrency = fiatCurrency,
            privacyMode = privacyMode,
        )
        return
    }
    if (preview.kind != LiquidSendKind.LBTC) {
        LightningSendReviewContent(
            preview = preview,
            useSats = useSats,
            btcPrice = btcPrice,
            fiatCurrency = fiatCurrency,
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
                        liquidReviewUsdSubtext(recipient.amountSats.toLong(), btcPrice, fiatCurrency, privacyMode)?.let { usdText ->
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
            valueSubtext = liquidReviewUsdSubtext(amountSats, btcPrice, fiatCurrency, privacyMode),
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
                    liquidReviewUsdSubtext(changeSats, btcPrice, fiatCurrency, privacyMode)?.let {
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
            valueSubtext = liquidReviewUsdSubtext(feeSats, btcPrice, fiatCurrency, privacyMode),
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
            valueSubtext = liquidReviewUsdSubtext(total, btcPrice, fiatCurrency, privacyMode),
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
private fun AssetSendReviewContent(
    preview: LiquidSendPreview,
    useSats: Boolean,
    btcPrice: Double?,
    fiatCurrency: String,
    privacyMode: Boolean,
) {
    val asset = preview.resolvedAsset
    val ticker = asset.ticker

    fun formatAssetValue(rawAmount: Long): String {
        if (privacyMode) return LIQUID_HIDDEN_AMOUNT
        val divisor = 10.0.pow(asset.precision.toDouble())
        val full = String.format(Locale.US, "%.${asset.precision}f", rawAmount.toDouble() / divisor)
        val trimmed = full.trimEnd('0').let { if (it.endsWith('.')) "${it}00" else it }
        return if (trimmed.contains('.') && trimmed.substringAfter('.').length < 2) {
            trimmed + "0".repeat(2 - trimmed.substringAfter('.').length)
        } else {
            trimmed
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

    preview.totalRecipientSats?.let { amount ->
        LiquidReviewAmountRow(
            label = "Payment Amount:",
            subtitle = "$ticker payment",
            valueText = if (privacyMode) LIQUID_HIDDEN_AMOUNT else "-${formatAssetValue(amount)} $ticker",
            valueSubtext = null,
            color = AccentRed,
        )
        Spacer(modifier = Modifier.height(12.dp))
    }

    preview.feeSats?.let { feeSats ->
        val feeRateInfo = preview.txVBytes?.let {
            "${formatFeeRate(preview.feeRate)} sat/vB • ${formatVBytes(it)} vB"
        }
        LiquidReviewFeeRow(
            label = "Liquid Network Fee:",
            valueText = liquidReviewAmountText(feeSats, useSats, privacyMode, "-"),
            subtext = if (privacyMode) null else feeRateInfo,
            valueSubtext = liquidReviewUsdSubtext(feeSats, btcPrice, fiatCurrency, privacyMode),
            valueColor = WarningYellow,
        )
    }

    Spacer(modifier = Modifier.height(16.dp))
    LiquidReviewDivider()
    Spacer(modifier = Modifier.height(16.dp))

    preview.totalRecipientSats?.let { amount ->
        LiquidReviewAmountRow(
            label = "Total",
            valueText = if (privacyMode) LIQUID_HIDDEN_AMOUNT else "-${formatAssetValue(amount)} $ticker",
            valueSubtext = preview.feeSats?.let { fee ->
                if (privacyMode) null else "+ ${liquidReviewAmountText(fee, useSats, false, "")} fee"
            },
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
}

@Composable
private fun LightningSendReviewContent(
    preview: LiquidSendPreview,
    useSats: Boolean,
    btcPrice: Double?,
    fiatCurrency: String,
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
            LiquidSendKind.LIGHTNING_LNURL -> "Lightning Address"
            LiquidSendKind.LBTC -> "Liquid payment"
            LiquidSendKind.LIQUID_ASSET -> "${preview.resolvedAsset.ticker} payment"
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
            valueSubtext = liquidReviewUsdSubtext(amountSats, btcPrice, fiatCurrency, privacyMode),
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
            valueSubtext = liquidReviewUsdSubtext(line.value, btcPrice, fiatCurrency, privacyMode),
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
            valueSubtext = liquidReviewUsdSubtext(total, btcPrice, fiatCurrency, privacyMode),
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
    fiatCurrency: String,
    privacyMode: Boolean,
): String? {
    if (privacyMode || btcPrice == null || btcPrice <= 0.0) {
        return null
    }
    return formatLiquidUsd(value, btcPrice, fiatCurrency)
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
    fiatCurrency: String = SecureStorage.DEFAULT_PRICE_CURRENCY,
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
                                "$baseText · ${formatLiquidUsd(totalSelected, btcPrice, fiatCurrency)}"
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

                                val utxoAssetId = utxo.assetId
                                val isNonLbtcUtxo = utxoAssetId != null && !LiquidAsset.isPolicyAsset(utxoAssetId)
                                val resolvedUtxoAsset = utxoAssetId?.takeUnless { LiquidAsset.isPolicyAsset(it) }?.let { LiquidAsset.resolve(it) }

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (isNonLbtcUtxo && resolvedUtxoAsset != null) {
                                            Text(
                                                text =
                                                    if (privacyMode) {
                                                        LIQUID_HIDDEN_AMOUNT
                                                    } else {
                                                        formatCoinControlAssetAmount(resolvedUtxoAsset, utxo.amountSats.toLong())
                                                    },
                                                style = MaterialTheme.typography.bodyLarge,
                                                fontWeight = FontWeight.Medium,
                                                color = if (isDisabled) TextPrimary.copy(alpha = 0.4f) else TextPrimary,
                                            )
                                        } else {
                                            Text(
                                                text =
                                                    if (privacyMode) {
                                                        LIQUID_HIDDEN_AMOUNT
                                                    } else {
                                                        formatLiquidAmount(utxo.amountSats.toLong(), useSats)
                                                    },
                                                style = MaterialTheme.typography.bodyLarge,
                                                fontWeight = FontWeight.Medium,
                                                color = if (isDisabled) TextPrimary.copy(alpha = 0.4f) else TextPrimary,
                                            )
                                            if (btcPrice != null && btcPrice > 0 && !privacyMode) {
                                                Text(
                                                    text = " · ${formatLiquidUsd(utxo.amountSats.toLong(), btcPrice, fiatCurrency)}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = if (isDisabled) TextSecondary.copy(alpha = 0.4f) else TextSecondary,
                                                )
                                            }
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

                                Column(
                                    horizontalAlignment = Alignment.End,
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    if (isNonLbtcUtxo && resolvedUtxoAsset != null) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(AccentTeal.copy(alpha = 0.16f))
                                                .padding(horizontal = 6.dp, vertical = 2.dp),
                                        ) {
                                            Text(
                                                text = resolvedUtxoAsset.ticker,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.SemiBold,
                                                color = AccentTeal,
                                            )
                                        }
                                    }
                                    Box(
                                        modifier = Modifier
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
    fiatCurrency: String = SecureStorage.DEFAULT_PRICE_CURRENCY,
    privacyMode: Boolean,
    availableSats: Long,
    coinControlAsset: LiquidAsset? = null,
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
                                                    isUsdMode -> "Amount ($fiatCurrency)"
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
                            } else if (coinControlAsset != null) {
                                formatCoinControlAssetAmount(coinControlAsset, availableSats)
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

            }
        }
    }
}

private fun currentLiquidSendPreview(sendState: LiquidSendState): LiquidSendPreview? {
    return when (sendState) {
        LiquidSendState.Idle -> null
        is LiquidSendState.Estimating -> sendState.preview
        is LiquidSendState.ReviewReady -> sendState.preview
        is LiquidSendState.Sending -> sendState.preview
        is LiquidSendState.Success -> sendState.preview
        is LiquidSendState.Failed -> sendState.preview
    }
}

private fun liquidSendDialogTitle(
    sendState: LiquidSendState,
    kind: LiquidSendKind,
    isLiquidWatchOnly: Boolean,
): String {
    return when (sendState) {
        LiquidSendState.Idle,
        is LiquidSendState.Estimating,
        is LiquidSendState.ReviewReady,
        -> when (kind) {
            LiquidSendKind.LBTC -> if (isLiquidWatchOnly) "Review PSET" else "Review Transaction"
            LiquidSendKind.LIQUID_ASSET -> if (isLiquidWatchOnly) "Review PSET" else "Review Transaction"
            LiquidSendKind.LIGHTNING_BOLT12 -> "Pay BOLT12 Offer?"
            LiquidSendKind.LIGHTNING_BOLT11 -> "Pay Lightning Invoice?"
            LiquidSendKind.LIGHTNING_LNURL -> "Pay Lightning Address?"
        }

        is LiquidSendState.Sending ->
            if (kind == LiquidSendKind.LBTC || kind == LiquidSendKind.LIQUID_ASSET) {
                "Sending..."
            } else {
                "Paying Lightning"
            }

        is LiquidSendState.Success ->
            if (kind == LiquidSendKind.LBTC || kind == LiquidSendKind.LIQUID_ASSET) {
                "Sent"
            } else {
                "Lightning Payment Sent"
            }

        is LiquidSendState.Failed ->
            if (kind == LiquidSendKind.LBTC || kind == LiquidSendKind.LIQUID_ASSET) {
                "Send Failed"
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
                    when (parsedRecipient.kind) {
                        LightningKind.BOLT12 -> "BOLT12"
                        LightningKind.LNURL -> "LNURL"
                        LightningKind.BOLT11 -> "BOLT11"
                    },
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

private fun parseAssetAmountToBaseUnits(value: String, asset: LiquidAsset): Long {
    if (value.isBlank()) return 0L
    val d = value.toDoubleOrNull() ?: return 0L
    return (d * 10.0.pow(asset.precision.toDouble())).roundToLong()
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

private fun filterAssetAmountInput(value: String, precision: Int): String {
    val pattern = Regex("^\\d*\\.?\\d{0,$precision}$")
    return if (value.isEmpty() || value.matches(pattern)) value else value.dropLast(1)
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

private fun formatCoinControlAssetAmount(asset: LiquidAsset, rawAmount: Long): String {
    val divisor = 10.0.pow(asset.precision.toDouble())
    val full = String.format(Locale.US, "%.${asset.precision}f", rawAmount.toDouble() / divisor)
    val trimmed = full.trimEnd('0').let { if (it.endsWith('.')) "${it}00" else it }
    val minDecimals = if (trimmed.contains('.') && trimmed.substringAfter('.').length < 2) {
        trimmed + "0".repeat(2 - trimmed.substringAfter('.').length)
    } else {
        trimmed
    }
    return "$minDecimals ${asset.ticker}"
}

private fun formatLiquidAmount(amountSats: Long, useSats: Boolean): String {
    return if (useSats) {
        "${"%,d".format(amountSats)} sats"
    } else {
        "${String.format(Locale.US, "%.8f", amountSats / 100_000_000.0).trimEnd('0').trimEnd('.')} BTC"
    }
}

private fun formatLiquidUsd(
    amountSats: Long,
    btcPrice: Double,
    fiatCurrency: String,
): String {
    val usdValue = (amountSats.toDouble() / 100_000_000.0) * btcPrice
    return formatFiat(usdValue, fiatCurrency)
}

private fun LightningKind.toLiquidSendKind(): LiquidSendKind {
    return when (this) {
        LightningKind.BOLT11 -> LiquidSendKind.LIGHTNING_BOLT11
        LightningKind.BOLT12 -> LiquidSendKind.LIGHTNING_BOLT12
        LightningKind.LNURL -> LiquidSendKind.LIGHTNING_LNURL
    }
}

// ════════════════════════════════════════════
// Universal Pending Payments Card
// ════════════════════════════════════════════

private const val BOLTZ_RESCUE_URL = "https://boltz.exchange/rescue/external"

@Composable
private fun PendingPaymentsCard(
    pendingSubmarineSwap: PendingLightningPaymentSession?,
    pendingLiquidTxs: List<LiquidTransaction>,
    pendingCount: Int,
    boltzRescueMnemonic: String?,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    privacyMode: Boolean,
    useSats: Boolean,
) {
    val expandedItems = remember { mutableStateMapOf<String, Boolean>() }
    var showRescueKeyDialog by rememberSaveable { mutableStateOf(false) }
    val cardBringIntoViewRequester = rememberBringIntoViewRequesterOnExpand(expanded, "pending_payments_card")

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCard),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onExpandedChange(!expanded) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Pending Payments",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                PendingCountBadge(count = pendingCount)
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = LiquidTeal,
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                Column(
                    modifier = Modifier
                        .padding(top = 12.dp)
                        .bringIntoViewRequester(cardBringIntoViewRequester),
                ) {
                    if (pendingSubmarineSwap != null) {
                        val swapKey = "ln_${pendingSubmarineSwap.swapId}"
                        val swapExpanded = expandedItems[swapKey] ?: (pendingCount == 1)
                        val swapBringIntoViewRequester =
                            rememberBringIntoViewRequesterOnExpand(swapExpanded, "pending_lightning_$swapKey")
                        PendingItemHeader(
                            badgeLabel = "Lightning",
                            badgeColor = LightningYellow,
                            statusLabel = pendingSubmarineSwap.phase.name.lowercase()
                                .replaceFirstChar { it.uppercase() },
                            expanded = swapExpanded,
                            onExpandedChange = { expandedItems[swapKey] = it },
                        )
                        AnimatedVisibility(
                            visible = swapExpanded,
                            enter = expandVertically(),
                            exit = shrinkVertically(),
                        ) {
                            Column(modifier = Modifier.bringIntoViewRequester(swapBringIntoViewRequester)) {
                                PendingLightningPaymentDetails(
                                    session = pendingSubmarineSwap,
                                    boltzRescueMnemonic = boltzRescueMnemonic,
                                    privacyMode = privacyMode,
                                    useSats = useSats,
                                    onShowRescueKey = { showRescueKeyDialog = true },
                                )
                            }
                        }
                        if (pendingLiquidTxs.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            HorizontalDivider(color = BorderColor)
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }

                    pendingLiquidTxs.forEachIndexed { index, tx ->
                        val txKey = "lbtc_${tx.txid}"
                        val txExpanded = expandedItems[txKey] ?: (pendingCount == 1)
                        val txBringIntoViewRequester =
                            rememberBringIntoViewRequesterOnExpand(txExpanded, "pending_liquid_$txKey")
                        PendingItemHeader(
                            badgeLabel = "Liquid",
                            badgeColor = LiquidTeal,
                            statusLabel = "Pending",
                            expanded = txExpanded,
                            onExpandedChange = { expandedItems[txKey] = it },
                        )
                        AnimatedVisibility(
                            visible = txExpanded,
                            enter = expandVertically(),
                            exit = shrinkVertically(),
                        ) {
                            Column(modifier = Modifier.bringIntoViewRequester(txBringIntoViewRequester)) {
                                PendingLiquidTxDetails(
                                    tx = tx,
                                    privacyMode = privacyMode,
                                    useSats = useSats,
                                )
                            }
                        }
                        if (index < pendingLiquidTxs.lastIndex) {
                            Spacer(modifier = Modifier.height(8.dp))
                            HorizontalDivider(color = BorderColor)
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
            }
        }
    }

    if (showRescueKeyDialog && boltzRescueMnemonic != null) {
        RescueMnemonicDialog(
            mnemonic = boltzRescueMnemonic,
            onDismiss = { showRescueKeyDialog = false },
        )
    }
}

@Composable
private fun PendingCountBadge(count: Int) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(LiquidTeal.copy(alpha = 0.14f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "$count",
            style = MaterialTheme.typography.labelSmall,
            color = LiquidTeal,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun PendingItemHeader(
    badgeLabel: String,
    badgeColor: Color,
    statusLabel: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onExpandedChange(!expanded) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(badgeColor.copy(alpha = 0.16f))
                .padding(horizontal = 5.dp, vertical = 2.dp),
        ) {
            Text(
                text = badgeLabel,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp, lineHeight = 17.sp),
                color = badgeColor,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = statusLabel,
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary,
        )
        Spacer(modifier = Modifier.width(4.dp))
        Icon(
            imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
            contentDescription = if (expanded) "Collapse" else "Expand",
            tint = TextSecondary,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun PendingLightningPaymentDetails(
    session: PendingLightningPaymentSession,
    boltzRescueMnemonic: String?,
    privacyMode: Boolean,
    useSats: Boolean,
    onShowRescueKey: () -> Unit,
) {
    Column(modifier = Modifier.padding(start = 4.dp, top = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text("Lockup", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                Text(
                    text = if (privacyMode) LIQUID_HIDDEN_AMOUNT
                    else formatLiquidAmount(session.lockupAmountSats, useSats),
                    style = MaterialTheme.typography.titleMedium,
                    color = BitcoinOrange,
                )
            }
            if (session.paymentAmountSats > 0) {
                Column(horizontalAlignment = Alignment.End) {
                    Text("Payment", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    Text(
                        text = if (privacyMode) LIQUID_HIDDEN_AMOUNT
                        else formatLiquidAmount(session.paymentAmountSats, useSats),
                        style = MaterialTheme.typography.titleMedium,
                        color = LiquidTeal,
                    )
                }
            }
        }

        if (session.status.isNotBlank() || (session.swapFeeSats > 0 && !privacyMode) || boltzRescueMnemonic != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    if (session.status.isNotBlank()) {
                        Text(
                            text = session.status,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                        )
                    }
                    if (session.swapFeeSats > 0 && !privacyMode) {
                        Text(
                            text = "Swap fee: ${formatLiquidAmount(session.swapFeeSats, useSats)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary,
                        )
                    }
                }
                if (boltzRescueMnemonic != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedButton(
                        onClick = onShowRescueKey,
                        modifier = Modifier.height(32.dp),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, BorderColor),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                    ) {
                        Text(
                            text = "Rescue Key",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextPrimary,
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        PendingDetailRow(label = "Boltz Swap ID", value = session.swapId)

        PendingDetailRow(label = "Lockup Address", value = session.lockupAddress)
        PendingDetailRow(label = "Refund Address", value = session.refundAddress)
        session.fundingTxid?.let { PendingDetailRow(label = "Funding Txid", value = it) }
        session.refundPublicKey?.let { PendingDetailRow(label = "Refund Public Key", value = it) }
        session.timeoutBlockHeight?.let {
            PendingDetailRow(label = "Timeout Block Height", value = it.toString())
        }
    }
}

@Composable
private fun PendingLiquidTxDetails(
    tx: LiquidTransaction,
    privacyMode: Boolean,
    useSats: Boolean,
) {
    Column(modifier = Modifier.padding(start = 4.dp, top = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text("Amount", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                Text(
                    text = if (privacyMode) LIQUID_HIDDEN_AMOUNT
                    else formatLiquidAmount(abs(tx.balanceSatoshi), useSats),
                    style = MaterialTheme.typography.bodyMedium,
                    color = AccentRed,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("Fee", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                Text(
                    text = if (privacyMode) LIQUID_HIDDEN_AMOUNT
                    else formatLiquidAmount(tx.fee, useSats),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        PendingDetailRow(label = "Txid", value = tx.txid)
        tx.walletAddress?.let { PendingDetailRow(label = "Recipient", value = it) }
    }
}

@Composable
private fun PendingDetailRow(
    label: String,
    value: String,
) {
    val context = LocalContext.current
    var showCopied by remember { mutableStateOf(false) }

    LaunchedEffect(showCopied, value) {
        if (!showCopied) return@LaunchedEffect
        delay(1_500)
        showCopied = false
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextSecondary,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = truncatePendingValue(value),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary,
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = {
                    SecureClipboard.copyAndScheduleClear(context, label, value)
                    showCopied = true
                },
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "Copy $label",
                    tint = LiquidTeal,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        if (showCopied) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Copied to clipboard!",
                style = MaterialTheme.typography.bodySmall,
                color = LiquidTeal,
            )
        }
    }
}

@Composable
private fun RescueMnemonicDialog(
    mnemonic: String,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var showCopied by remember { mutableStateOf(false) }
    LaunchedEffect(showCopied, mnemonic) {
        if (!showCopied) return@LaunchedEffect
        delay(1_500)
        showCopied = false
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(12.dp),
            color = DarkSurface,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                Text(
                    text = "Emergency Rescue Key",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "If swap fails, open Boltz rescue link and paste this 12-word seed.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(
                                        Intent.ACTION_VIEW,
                                        BOLTZ_RESCUE_URL.toUri(),
                                    ),
                                )
                            }
                        },
                        modifier = Modifier.size(30.dp),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = "Open Boltz rescue page",
                            tint = LiquidTeal,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Boltz Rescue Key",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                ) {
                    Text(
                        text = mnemonic,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            SecureClipboard.copyAndScheduleClear(context, "Boltz Rescue Key", mnemonic)
                            showCopied = true
                        },
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy Boltz rescue key",
                            tint = LiquidTeal,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                if (showCopied) {
                    Text(
                        text = "Copied to clipboard!",
                        style = MaterialTheme.typography.labelSmall,
                        color = LiquidTeal,
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
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

private fun truncatePendingValue(value: String, edgeChars: Int = 10): String =
    if (value.length <= edgeChars * 2 + 3) value
    else "${value.take(edgeChars)}...${value.takeLast(edgeChars)}"

private const val MIN_LIQUID_FEE_RATE = 0.1
private const val LIQUID_HIDDEN_AMOUNT = "****"
private const val LIQUID_PSET_DISABLED_MESSAGE = "PSET is temporarily unavailable for live use."
