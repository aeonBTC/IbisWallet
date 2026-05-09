package github.aeonbtc.ibiswallet.ui.screens

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import github.aeonbtc.ibiswallet.data.local.SecureStorage
import github.aeonbtc.ibiswallet.data.model.FeeEstimationResult
import github.aeonbtc.ibiswallet.data.model.LiquidAsset
import github.aeonbtc.ibiswallet.data.model.PendingSwapPhase
import github.aeonbtc.ibiswallet.data.model.PendingSwapSession
import github.aeonbtc.ibiswallet.data.model.SwapDirection
import github.aeonbtc.ibiswallet.data.model.SwapLimits
import github.aeonbtc.ibiswallet.data.model.SwapService
import github.aeonbtc.ibiswallet.data.model.SwapState
import github.aeonbtc.ibiswallet.data.model.UtxoInfo
import github.aeonbtc.ibiswallet.ui.components.AmountLabel
import github.aeonbtc.ibiswallet.ui.components.BoltzRescueKeyButton
import github.aeonbtc.ibiswallet.ui.components.BoltzRescueMnemonicDialog
import github.aeonbtc.ibiswallet.ui.components.FeeRateOption
import github.aeonbtc.ibiswallet.ui.components.FeeRateSection
import github.aeonbtc.ibiswallet.ui.components.IbisButton
import github.aeonbtc.ibiswallet.ui.components.QrScannerDialog
import github.aeonbtc.ibiswallet.ui.components.ScrollableDialogSurface
import github.aeonbtc.ibiswallet.ui.components.formatFeeRate
import github.aeonbtc.ibiswallet.ui.components.rememberBringIntoViewRequesterOnExpand
import github.aeonbtc.ibiswallet.ui.components.shouldShowBoltzRescueKey
import github.aeonbtc.ibiswallet.ui.theme.AccentBlue
import github.aeonbtc.ibiswallet.ui.theme.BitcoinOrange
import github.aeonbtc.ibiswallet.ui.theme.BorderColor
import github.aeonbtc.ibiswallet.ui.theme.DarkBackground
import github.aeonbtc.ibiswallet.ui.theme.DarkCard
import github.aeonbtc.ibiswallet.ui.theme.DarkSurface
import github.aeonbtc.ibiswallet.ui.theme.DarkSurfaceVariant
import github.aeonbtc.ibiswallet.ui.theme.ErrorRed
import github.aeonbtc.ibiswallet.ui.theme.LiquidTeal
import github.aeonbtc.ibiswallet.ui.theme.SuccessGreen
import github.aeonbtc.ibiswallet.ui.theme.TextPrimary
import github.aeonbtc.ibiswallet.ui.theme.TextSecondary
import github.aeonbtc.ibiswallet.ui.theme.TextTertiary
import github.aeonbtc.ibiswallet.ui.theme.WarningYellow
import github.aeonbtc.ibiswallet.util.ParsedSendRecipient
import github.aeonbtc.ibiswallet.util.SecureClipboard
import github.aeonbtc.ibiswallet.util.parseSendRecipient
import kotlinx.coroutines.delay
import java.text.NumberFormat
import java.util.Locale
import androidx.compose.ui.res.stringResource
import github.aeonbtc.ibiswallet.R
import androidx.compose.material3.Text

private const val MIN_LIQUID_SWAP_FEE_RATE = 0.1
private const val ESTIMATED_LIQUID_SWAP_TX_VSIZE = 200
/**
 * Swap screen for BTC <-> L-BTC peg-in / peg-out.
 *
 * Uses a compact setup card for direction, amount, limits, and service,
 * followed by a single quote/progress area.
 */
@Composable
fun SwapScreen(
    swapState: SwapState = SwapState.Idle,
    pendingSwaps: List<PendingSwapSession> = emptyList(),
    boltzRescueMnemonic: String? = null,
    swapLimitsByService: Map<SwapService, SwapLimits> = emptyMap(),
    boltzEnabled: Boolean = true,
    sideSwapEnabled: Boolean = true,
    btcBalanceSats: Long = 0,
    lbtcBalanceSats: Long = 0,
    btcUtxos: List<UtxoInfo> = emptyList(),
    liquidUtxos: List<UtxoInfo> = emptyList(),
    spendUnconfirmed: Boolean = false,
    btcPrice: Double? = null,
    fiatCurrency: String = SecureStorage.DEFAULT_PRICE_CURRENCY,
    privacyMode: Boolean = false,
    denomination: String = SecureStorage.DENOMINATION_BTC,
    feeEstimationState: FeeEstimationResult = FeeEstimationResult.Disabled,
    minFeeRate: Double = 1.0,
    preferredService: SwapService = SwapService.BOLTZ,
    onFetchLimits: (SwapDirection, SwapService) -> Unit = { _, _ -> },
    onPreferredServiceChange: (SwapService) -> Unit = {},
    onRefreshBitcoinFees: () -> Unit = {},
    onPrepareSwapReview: (SwapDirection, Long, SwapService, List<UtxoInfo>?, String?, String?, Boolean, Double?) -> Unit = { _, _, _, _, _, _, _, _ -> },
    onExecuteSwap: (PendingSwapSession, List<UtxoInfo>?) -> Unit = { _, _ -> },
    onCancelPreparedReview: () -> Unit = {},
    onResetSwap: () -> Unit = {},
    onDismissFailedSwap: () -> Unit = {},
    onToggleDenomination: () -> Unit = {},
) {
    val useSats = denomination == SecureStorage.DENOMINATION_SATS
    val unit = if (useSats) "sats" else "BTC"

    var direction by remember { mutableStateOf(SwapDirection.BTC_TO_LBTC) }
    var selectedService by remember {
        mutableStateOf(
            resolveInitialSwapService(
                preferredService = preferredService,
                boltzEnabled = boltzEnabled,
                sideSwapEnabled = sideSwapEnabled,
            ),
        )
    }
    var amountInput by rememberSaveable { mutableStateOf("") }
    var isUsdMode by remember { mutableStateOf(false) }
    var isMaxMode by rememberSaveable { mutableStateOf(false) }
    var showLabelField by rememberSaveable { mutableStateOf(false) }
    var labelText by rememberSaveable { mutableStateOf("") }
    var showAdvancedOptions by rememberSaveable { mutableStateOf(false) }
    var showCustomDestinationQrScanner by rememberSaveable { mutableStateOf(false) }
    val customBitcoinDestinationState = rememberSaveable { mutableStateOf("") }
    val customLiquidDestinationState = rememberSaveable { mutableStateOf("") }
    val useCustomBitcoinDestinationState = rememberSaveable { mutableStateOf(false) }
    val useCustomLiquidDestinationState = rememberSaveable { mutableStateOf(false) }
    var customBitcoinFeeRateInput by rememberSaveable { mutableStateOf("") }
    val customLiquidFeeRateInputState = rememberSaveable { mutableStateOf("") }
    var customBitcoinFeeRate by rememberSaveable { mutableDoubleStateOf(minFeeRate) }
    var customBitcoinFeeOptionName by rememberSaveable { mutableStateOf(FeeRateOption.HALF_HOUR.name) }
    val useCustomBitcoinFeeRateState = rememberSaveable { mutableStateOf(false) }
    val useCustomLiquidFeeRateState = rememberSaveable { mutableStateOf(false) }
    var showConfirmation by rememberSaveable { mutableStateOf(false) }
    val showCoinControlState = rememberSaveable { mutableStateOf(false) }
    var executingReviewSwapId by rememberSaveable { mutableStateOf<String?>(null) }
    var broadcastingSwapId by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingSwapsExpanded by rememberSaveable { mutableStateOf(false) }
    var reviewDialogSwap by remember { mutableStateOf<PendingSwapSession?>(null) }
    val selectedBitcoinUtxos = remember { mutableStateListOf<UtxoInfo>() }
    val selectedLiquidUtxos = remember { mutableStateListOf<UtxoInfo>() }
    val customBitcoinDestination = customBitcoinDestinationState.value
    val customLiquidDestination = customLiquidDestinationState.value
    val useCustomBitcoinDestination = useCustomBitcoinDestinationState.value
    val useCustomLiquidDestination = useCustomLiquidDestinationState.value
    val customLiquidFeeRateInput = customLiquidFeeRateInputState.value
    val useCustomBitcoinFeeRate = useCustomBitcoinFeeRateState.value
    val useCustomLiquidFeeRate = useCustomLiquidFeeRateState.value
    val showCoinControl = showCoinControlState.value
    val advancedOptionsBringIntoViewRequester = rememberBringIntoViewRequesterOnExpand(showAdvancedOptions, "swap_advanced")
    val customDestinationBringIntoViewRequester =
        rememberBringIntoViewRequesterOnExpand(
            useCustomBitcoinDestination || useCustomLiquidDestination,
            "swap_destination",
        )
    val customFeeBringIntoViewRequester =
        rememberBringIntoViewRequesterOnExpand(
            useCustomBitcoinFeeRate || useCustomLiquidFeeRate,
            "swap_fees",
        )
    val clearSwapForm = {
        amountInput = ""
        isUsdMode = false
        isMaxMode = false
        showLabelField = false
        labelText = ""
        showAdvancedOptions = false
        customBitcoinDestinationState.value = ""
        customLiquidDestinationState.value = ""
        useCustomBitcoinDestinationState.value = false
        useCustomLiquidDestinationState.value = false
        customBitcoinFeeRateInput = ""
        customLiquidFeeRateInputState.value = ""
        customBitcoinFeeRate = minFeeRate
        customBitcoinFeeOptionName = FeeRateOption.HALF_HOUR.name
        useCustomBitcoinFeeRateState.value = false
        useCustomLiquidFeeRateState.value = false
        showConfirmation = false
        showCoinControlState.value = false
        reviewDialogSwap = null
        selectedBitcoinUtxos.clear()
        selectedLiquidUtxos.clear()
    }
    val clearMaxSelectionForServiceChange: (SwapService) -> Unit = { newService ->
        if (isMaxMode && selectedService != newService) {
            isMaxMode = false
            amountInput = ""
        }
    }
    val preparedSwap = when (swapState) {
        is SwapState.ReviewReady -> swapState.swap
        else -> null
    }
    val txLabel = labelText.trim().takeIf { showLabelField && it.isNotBlank() }
    val isSwapLocked =
        swapState is SwapState.FetchingQuote ||
            swapState is SwapState.PreparingReview ||
            preparedSwap != null

    LaunchedEffect(pendingSwaps.isEmpty()) {
        if (pendingSwaps.isEmpty()) {
            pendingSwapsExpanded = false
        }
    }

    // Fetch limits for both services whenever direction changes.
    LaunchedEffect(direction, boltzEnabled, sideSwapEnabled) {
        if (boltzEnabled) {
            onFetchLimits(direction, SwapService.BOLTZ)
        }
        if (sideSwapEnabled) {
            onFetchLimits(direction, SwapService.SIDESWAP)
        }
    }

    // Parse input → sats regardless of denomination mode
    val amountSats = remember(amountInput, useSats, isUsdMode, btcPrice) {
        when {
            amountInput.isBlank() -> null
            isUsdMode && btcPrice != null && btcPrice > 0 -> {
                amountInput.toDoubleOrNull()?.takeIf { it > 0 }?.let {
                    ((it / btcPrice) * 100_000_000).toLong()
                }
            }
            useSats -> amountInput.toLongOrNull()?.takeIf { it > 0 }
            else -> {
                amountInput.toDoubleOrNull()?.takeIf { it > 0 }?.let {
                    (it * 100_000_000).toLong()
                }
            }
        }
    }

    val isPegIn = direction == SwapDirection.BTC_TO_LBTC
    val layer1Label = stringResource(R.string.loc_b67a01a5)
    val layer2Label = stringResource(R.string.loc_2f73501f)
    val boltzLabel = stringResource(R.string.loc_3d1a0df5)
    val sideSwapLabel = stringResource(R.string.loc_c6f76248)
    val atomicSwapLabel = stringResource(R.string.loc_bebd07bb)
    val trustedPegLabel = stringResource(R.string.loc_e2c90bcd)
    val providerDisabledLabel = stringResource(R.string.loc_cd053ef4)
    val liquidAddressLabel = stringResource(R.string.loc_4622d13c)
    val bitcoinAddressLabel = stringResource(R.string.loc_a18fd453)
    val swapLiquidDestinationHint = stringResource(R.string.loc_6f9cca87)
    val swapBitcoinDestinationHint = stringResource(R.string.loc_955fce50)
    val invalidBitcoinAddressLabel = stringResource(R.string.loc_04536bb4)
    val invalidLiquidAddressLabel = stringResource(R.string.loc_90285d1b)
    val bitcoinFeeRateLabel = stringResource(R.string.loc_a509369c)
    val liquidFeeRateLabel = stringResource(R.string.loc_78e3e750)
    val leaveBlankDefaultEstimateLabel = stringResource(R.string.loc_43525120)
    val enterValidFeeRateLabel = stringResource(R.string.loc_857c8623)
    val enterFeeRateAboveZeroLabel = stringResource(R.string.loc_f2b5f4d4)
    val feeDetailsLoadingLabel = stringResource(R.string.loc_8a098662)
    val feeDetailsUnavailableLabel = stringResource(R.string.loc_e8f8806d)
    val nonCustodialSwapLabel = stringResource(R.string.loc_f572fd2a)
    val custodialSwapLabel = stringResource(R.string.loc_5675a8d7)
    val layer1DefaultFeeLabel = stringResource(
        R.string.common_default_format,
        "${formatFeeRate(MIN_LIQUID_SWAP_FEE_RATE)} sat/vB",
    )
    val fromLabel = if (isPegIn) "BTC" else "L-BTC"
    val toLabel = if (isPegIn) "L-BTC" else "BTC"
    val fromColor = if (isPegIn) BitcoinOrange else LiquidTeal
    val toColor = if (isPegIn) LiquidTeal else BitcoinOrange
    val fromLayerLabel = if (isPegIn) layer1Label else layer2Label
    val toLayerLabel = if (isPegIn) layer2Label else layer1Label
    val spendableBitcoinUtxos = remember(btcUtxos) { btcUtxos.filter { !it.isFrozen } }
    val spendableLiquidUtxos =
        remember(liquidUtxos) {
            liquidUtxos.filter { utxo ->
                !utxo.isFrozen && (utxo.assetId == null || LiquidAsset.isPolicyAsset(utxo.assetId))
            }
        }
    val selectedFundingUtxos = if (isPegIn) selectedBitcoinUtxos else selectedLiquidUtxos
    val selectableFundingUtxos = if (isPegIn) spendableBitcoinUtxos else spendableLiquidUtxos
    val availableBalance =
        if (selectedFundingUtxos.isNotEmpty()) {
            selectedFundingUtxos.sumOf { it.amountSats.toLong() }
        } else if (isPegIn) {
            btcBalanceSats
        } else {
            lbtcBalanceSats
        }
    val swapLimits = swapLimitsByService[selectedService]?.takeIf { it.direction == direction }
    val boltzLimits = swapLimitsByService[SwapService.BOLTZ]?.takeIf { it.direction == direction }
    val sideSwapLimits = swapLimitsByService[SwapService.SIDESWAP]?.takeIf { it.direction == direction }
    val providerOptions = remember(boltzLimits, sideSwapLimits, boltzEnabled, sideSwapEnabled) {
        listOf(
            SwapProviderOption(
                service = SwapService.BOLTZ,
                label = boltzLabel,
                description = atomicSwapLabel,
                feeDetailText = if (boltzEnabled) {
                    providerFeeDetailText(
                        limits = boltzLimits,
                        service = SwapService.BOLTZ,
                        loadingText = feeDetailsLoadingLabel,
                        unavailableText = feeDetailsUnavailableLabel,
                        boltzText = nonCustodialSwapLabel,
                        sideSwapText = custodialSwapLabel,
                    )
                } else {
                    providerDisabledLabel
                },
                accentColor = AccentBlue,
                enabled = boltzEnabled,
            ),
            SwapProviderOption(
                service = SwapService.SIDESWAP,
                label = sideSwapLabel,
                description = trustedPegLabel,
                feeDetailText = if (sideSwapEnabled) {
                    providerFeeDetailText(
                        limits = sideSwapLimits,
                        service = SwapService.SIDESWAP,
                        loadingText = feeDetailsLoadingLabel,
                        unavailableText = feeDetailsUnavailableLabel,
                        boltzText = nonCustodialSwapLabel,
                        sideSwapText = custodialSwapLabel,
                    )
                } else {
                    providerDisabledLabel
                },
                accentColor = LiquidTeal,
                enabled = sideSwapEnabled,
            ),
        )
    }
    val selectedProvider = providerOptions.firstOrNull { it.service == selectedService } ?: providerOptions.first()
    val currentCustomDestination = if (isPegIn) customLiquidDestination else customBitcoinDestination
    val destinationOptionEnabled = if (isPegIn) useCustomLiquidDestination else useCustomBitcoinDestination
    val destinationOptionAccentColor = if (isPegIn) LiquidTeal else BitcoinOrange
    val effectiveCustomDestination = if (destinationOptionEnabled) currentCustomDestination else ""
    val currentFundingFeeRateInput = if (isPegIn) customBitcoinFeeRateInput else customLiquidFeeRateInput
    val fundingFeeOptionEnabled = if (isPegIn) useCustomBitcoinFeeRate else useCustomLiquidFeeRate
    val customBitcoinFeeOption =
        remember(customBitcoinFeeOptionName) {
            runCatching { FeeRateOption.valueOf(customBitcoinFeeOptionName) }.getOrDefault(FeeRateOption.HALF_HOUR)
        }
    val destinationPlaceholder = if (isPegIn) liquidAddressLabel else bitcoinAddressLabel
    val destinationEmptyPrompt = if (isPegIn) swapLiquidDestinationHint else swapBitcoinDestinationHint
    val destinationHelperText =
        if (isPegIn) {
            swapLiquidDestinationHint
        } else {
            swapBitcoinDestinationHint
        }
    val destinationValidationError =
        remember(effectiveCustomDestination, isPegIn) {
            val trimmed = effectiveCustomDestination.trim()
            if (trimmed.isEmpty()) {
                null
            } else {
                when (val parsed = parseSendRecipient(trimmed)) {
                    is ParsedSendRecipient.Liquid ->
                        if (isPegIn) {
                            null
                        } else {
                            invalidBitcoinAddressLabel
                        }
                    is ParsedSendRecipient.Bitcoin ->
                        if (isPegIn) {
                            invalidLiquidAddressLabel
                        } else {
                            null
                        }
                    is ParsedSendRecipient.Unknown -> parsed.errorMessage
                    else -> if (isPegIn) invalidLiquidAddressLabel else invalidBitcoinAddressLabel
                }
            }
        }
    val fundingFeeFieldLabel = if (isPegIn) bitcoinFeeRateLabel else liquidFeeRateLabel
    val fundingFeeAccentColor = if (isPegIn) BitcoinOrange else LiquidTeal
    val fundingFeeDefaultText = if (isPegIn) {
        leaveBlankDefaultEstimateLabel
    } else {
        layer1DefaultFeeLabel
    }
    val fundingFeeRateValidationError =
        remember(currentFundingFeeRateInput, fundingFeeOptionEnabled, isPegIn) {
            if (!fundingFeeOptionEnabled || isPegIn) {
                null
            } else {
                val parsed = currentFundingFeeRateInput.toDoubleOrNull()
                when {
                    currentFundingFeeRateInput.isBlank() -> null
                    parsed == null -> enterValidFeeRateLabel
                    parsed <= 0.0 -> enterFeeRateAboveZeroLabel
                    else -> null
                }
            }
        }
    val fundingFeeRateOverride =
        if (fundingFeeOptionEnabled) {
            if (isPegIn) {
                customBitcoinFeeRate
            } else {
                currentFundingFeeRateInput.toDoubleOrNull()?.takeIf { it > 0.0 }
            }
        } else {
            null
        }
    val effectiveLiquidFundingFeeRate = if (isPegIn) {
        MIN_LIQUID_SWAP_FEE_RATE
    } else {
        fundingFeeRateOverride ?: MIN_LIQUID_SWAP_FEE_RATE
    }
    val estimatedLiquidFundingFee = remember(effectiveLiquidFundingFeeRate) {
        kotlin.math.ceil(effectiveLiquidFundingFeeRate * ESTIMATED_LIQUID_SWAP_TX_VSIZE)
            .toLong()
            .coerceAtLeast(1L)
    }
    val estimatedSpendableBalance = if (isPegIn) {
        availableBalance
    } else {
        maxOf(0L, availableBalance - estimatedLiquidFundingFee)
    }
    val sourceAvailableBalance = if (isPegIn) availableBalance else estimatedSpendableBalance
    val transientExecutingSwap =
        reviewDialogSwap?.takeIf { swap ->
            swap.swapId == executingReviewSwapId || swap.swapId == broadcastingSwapId
        }
    val balanceWarning = remember(
        amountSats,
        estimatedSpendableBalance,
        isPegIn,
        preparedSwap?.swapId,
        transientExecutingSwap?.swapId,
    ) {
        val activeReviewedMaxLiquidSwap =
            sequenceOf(preparedSwap, transientExecutingSwap)
                .filterNotNull()
                .firstOrNull { swap ->
                swap.direction == SwapDirection.LBTC_TO_BTC &&
                    swap.usesMaxAmount &&
                    amountSats != null &&
                    swap.sendAmount == amountSats
                }
        when {
            activeReviewedMaxLiquidSwap != null -> null
            amountSats == null -> null
            amountSats > estimatedSpendableBalance -> if (isPegIn) {
                "Amount exceeds available BTC balance"
            } else {
                "Amount exceeds available L-BTC balance"
            }
            else -> null
        }
    }
    val limitWarning = remember(
        amountSats,
        swapLimits,
        selectedService,
    ) {
        when {
            amountSats == null -> null
            swapLimits != null && !swapLimits.isLoading && swapLimits.error == null &&
                swapLimits.minAmount > 0 && amountSats < swapLimits.minAmount ->
                "Below minimum for ${selectedService.name}"
            swapLimits != null && !swapLimits.isLoading && swapLimits.error == null &&
                swapLimits.maxAmount > 0 && amountSats > swapLimits.maxAmount ->
                "Above maximum for ${selectedService.name}"
            else -> null
        }
    }
    val amountWarnings = listOfNotNull(balanceWarning, limitWarning)
    val canRequestQuote =
        !isSwapLocked &&
            amountSats != null &&
            amountSats > 0 &&
            balanceWarning == null &&
            limitWarning == null &&
            destinationValidationError == null &&
            fundingFeeRateValidationError == null

    LaunchedEffect(preparedSwap?.swapId) {
        if (preparedSwap != null && executingReviewSwapId == preparedSwap.swapId) {
            executingReviewSwapId = null
            reviewDialogSwap = preparedSwap
            showConfirmation = true
        } else if (preparedSwap != null && executingReviewSwapId == null) {
            reviewDialogSwap = preparedSwap
            showConfirmation = true
        } else if (preparedSwap == null && executingReviewSwapId == null && !showConfirmation) {
            reviewDialogSwap = null
        }
    }

    LaunchedEffect(executingReviewSwapId, pendingSwaps) {
        val activeReviewSwapId = executingReviewSwapId ?: return@LaunchedEffect
        if (pendingSwaps.any { it.swapId == activeReviewSwapId }) {
            executingReviewSwapId = null
            broadcastingSwapId = activeReviewSwapId
            clearSwapForm()
            pendingSwapsExpanded = true
        }
    }

    LaunchedEffect(broadcastingSwapId, pendingSwaps, swapState) {
        val activeBroadcastingSwapId = broadcastingSwapId ?: return@LaunchedEffect
        val trackedPendingSwap = pendingSwaps.firstOrNull { it.swapId == activeBroadcastingSwapId }
        when {
            trackedPendingSwap == null -> broadcastingSwapId = null
            trackedPendingSwap.phase != PendingSwapPhase.FUNDING -> broadcastingSwapId = null
            swapState is SwapState.Completed && swapState.swapId == activeBroadcastingSwapId -> broadcastingSwapId = null
            swapState is SwapState.Failed -> {
                val failedSwapId = swapState.swap?.swapId
                if (failedSwapId == null || failedSwapId == activeBroadcastingSwapId) {
                    broadcastingSwapId = null
                }
            }
        }
    }

    LaunchedEffect(swapState, executingReviewSwapId) {
        val activeReviewSwapId = executingReviewSwapId ?: return@LaunchedEffect
        when (swapState) {
            is SwapState.Failed -> {
                val failedSwapId = swapState.swap?.swapId
                if (failedSwapId == null || failedSwapId == activeReviewSwapId) {
                    executingReviewSwapId = null
                    showConfirmation = false
                    reviewDialogSwap = null
                }
            }
            is SwapState.Completed -> {
                if (swapState.swapId == activeReviewSwapId) {
                    executingReviewSwapId = null
                    clearSwapForm()
                }
            }
            else -> Unit
        }
    }

    LaunchedEffect(boltzEnabled, sideSwapEnabled) {
        when (selectedService) {
            SwapService.BOLTZ ->
                if (!boltzEnabled && sideSwapEnabled) {
                    selectedService = SwapService.SIDESWAP
                }
            SwapService.SIDESWAP ->
                if (!sideSwapEnabled && boltzEnabled) {
                    selectedService = SwapService.BOLTZ
                }
        }
    }

    LaunchedEffect(preparedSwap?.swapId, useSats) {
        preparedSwap?.let { activeSwap ->
            direction = activeSwap.direction
            selectedService = activeSwap.service
            isUsdMode = false
            isMaxMode = false
            amountInput = if (useSats) {
                activeSwap.sendAmount.toString()
            } else {
                formatBtcInput(activeSwap.sendAmount)
            }
        }
    }

    LaunchedEffect(preparedSwap?.swapId, preparedSwap?.selectedFundingOutpoints, spendableBitcoinUtxos, spendableLiquidUtxos) {
        val activeSwap = preparedSwap ?: return@LaunchedEffect
        if (activeSwap.direction == SwapDirection.BTC_TO_LBTC) {
            selectedBitcoinUtxos.clear()
            selectedBitcoinUtxos.addAll(
                selectCoinControlUtxos(activeSwap.selectedFundingOutpoints, spendableBitcoinUtxos),
            )
        } else {
            selectedLiquidUtxos.clear()
            selectedLiquidUtxos.addAll(
                selectCoinControlUtxos(activeSwap.selectedFundingOutpoints, spendableLiquidUtxos),
            )
        }
    }

    LaunchedEffect(preparedSwap?.swapId, spendableBitcoinUtxos, spendableLiquidUtxos) {
        if (preparedSwap != null) {
            return@LaunchedEffect
        }
        reconcileCoinControlSelection(selectedBitcoinUtxos, spendableBitcoinUtxos)
        reconcileCoinControlSelection(selectedLiquidUtxos, spendableLiquidUtxos)
    }

    if (showCoinControl) {
        SwapCoinControlDialog(
            title = stringResource(if (isPegIn) R.string.loc_abb2f6d2 else R.string.loc_47914d64),
            accentColor = if (isPegIn) BitcoinOrange else LiquidTeal,
            utxos = selectableFundingUtxos,
            selectedUtxos = selectedFundingUtxos,
            spendUnconfirmed = spendUnconfirmed,
            useSats = useSats,
            privacyMode = privacyMode,
            btcPrice = btcPrice,
            isLiquid = !isPegIn,
            onDismiss = { showCoinControlState.value = false },
            onUtxoToggle = { utxo ->
                toggleCoinControlSelection(selectedFundingUtxos, utxo)
                onResetSwap()
            },
            onSelectAll = {
                selectedFundingUtxos.clear()
                selectedFundingUtxos.addAll(
                    selectableFundingUtxos.filter { spendUnconfirmed || it.isConfirmed },
                )
                onResetSwap()
            },
            onClearAll = {
                selectedFundingUtxos.clear()
                onResetSwap()
            },
        )
    }

    if (showCustomDestinationQrScanner) {
        QrScannerDialog(
            onCodeScanned = { code ->
                showCustomDestinationQrScanner = false
                val normalizedInput =
                    when (val parsed = parseSendRecipient(code.trim())) {
                        is ParsedSendRecipient.Liquid -> if (isPegIn) parsed.address else code.trim()
                        is ParsedSendRecipient.Bitcoin -> if (isPegIn) code.trim() else parsed.address
                        else -> code.trim()
                    }
                if (isPegIn) {
                    customLiquidDestinationState.value = normalizedInput
                } else {
                    customBitcoinDestinationState.value = normalizedInput
                }
                onResetSwap()
            },
            onDismiss = { showCustomDestinationQrScanner = false },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        if (pendingSwaps.isNotEmpty()) {
            PendingSwapsCard(
                pendingSwaps = pendingSwaps,
                boltzRescueMnemonic = boltzRescueMnemonic,
                expanded = pendingSwapsExpanded,
                onExpandedChange = { pendingSwapsExpanded = it },
                useSats = useSats,
                unit = unit,
                privacyMode = privacyMode,
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

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
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.loc_f0100030),
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                    )
                    val coinControlActive = selectedFundingUtxos.isNotEmpty()
                    val hasFundingUtxos = selectableFundingUtxos.isNotEmpty()
                    Card(
                        modifier =
                            Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(enabled = !isSwapLocked && hasFundingUtxos) { showCoinControlState.value = true },
                        shape = RoundedCornerShape(8.dp),
                        colors =
                            CardDefaults.cardColors(
                                containerColor =
                                    if (coinControlActive) {
                                        fromColor.copy(alpha = 0.15f)
                                    } else {
                                        DarkSurface
                                    },
                            ),
                        border = BorderStroke(1.dp, if (coinControlActive) fromColor else BorderColor),
                    ) {
                        Text(
                            text =
                                if (coinControlActive) {
                                    stringResource(
                                        R.string.swap_coin_control_utxo_badge_format,
                                        selectedFundingUtxos.size,
                                    )
                                } else {
                                    stringResource(if (isPegIn) R.string.loc_abb2f6d2 else R.string.loc_47914d64)
                                },
                            style = MaterialTheme.typography.labelMedium,
                            color =
                                if (coinControlActive) {
                                    fromColor
                                } else if (hasFundingUtxos) {
                                    TextSecondary
                                } else {
                                    TextSecondary.copy(alpha = 0.5f)
                                },
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val toggleDirection = {
                        if (!isSwapLocked) {
                            direction = if (isPegIn) SwapDirection.LBTC_TO_BTC else SwapDirection.BTC_TO_LBTC
                            onResetSwap()
                        }
                    }

                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(enabled = !isSwapLocked, onClick = toggleDirection),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = stringResource(R.string.loc_19280e4e),
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextTertiary,
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = fromLayerLabel,
                                style = MaterialTheme.typography.bodyLarge,
                                color = fromColor,
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = fromLabel,
                                style = MaterialTheme.typography.bodySmall,
                                color = fromColor.copy(alpha = 0.7f),
                            )
                        }
                    }

                    IconButton(
                        enabled = !isSwapLocked,
                        onClick = toggleDirection,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(fromColor.copy(alpha = 0.22f)),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = stringResource(R.string.loc_979d5904),
                            tint = toColor,
                            modifier = Modifier.size(24.dp),
                        )
                    }

                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(enabled = !isSwapLocked, onClick = toggleDirection),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = stringResource(R.string.loc_4203f666),
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextTertiary,
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = toLayerLabel,
                                style = MaterialTheme.typography.bodyLarge,
                                color = toColor,
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = toLabel,
                                style = MaterialTheme.typography.bodySmall,
                                color = toColor.copy(alpha = 0.7f),
                            )
                        }
                    }
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
                                .clickable(enabled = !isSwapLocked) {
                                    if (amountInput.isNotEmpty()) {
                                        val currentSats =
                                            when {
                                                isUsdMode -> {
                                                    val usdAmount = amountInput.toDoubleOrNull() ?: 0.0
                                                    (usdAmount / btcPrice * 100_000_000).toLong()
                                                }
                                                useSats -> {
                                                    amountInput.replace(",", "").toLongOrNull() ?: 0L
                                                }
                                                else -> {
                                                    val btcAmount = amountInput.toDoubleOrNull() ?: 0.0
                                                    (btcAmount * 100_000_000).toLong()
                                                }
                                            }
                                        amountInput =
                                            if (!isUsdMode) {
                                                val usdValue = (currentSats / 100_000_000.0) * btcPrice
                                                String.format(Locale.US, "%.2f", usdValue)
                                            } else if (useSats) {
                                                currentSats.toString()
                                            } else {
                                                String.format(Locale.US, "%.8f", currentSats / 100_000_000.0)
                                            }
                                    }
                                    isUsdMode = !isUsdMode
                                    isMaxMode = false
                                    onResetSwap()
                                },
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isUsdMode) LiquidTeal.copy(alpha = 0.15f) else DarkSurface,
                            ),
                            border = BorderStroke(1.dp, if (isUsdMode) LiquidTeal else BorderColor),
                        ) {
                            Text(
                                text = fiatCurrency,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isUsdMode) LiquidTeal else TextSecondary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                val conversionText =
                    if (amountInput.isNotEmpty() && amountSats != null && amountSats > 0 && btcPrice != null && btcPrice > 0) {
                        if (privacyMode) {
                            "≈ ****"
                        } else if (isUsdMode) {
                            "≈ ${formatAmt(amountSats, useSats)} $unit"
                        } else {
                            val usdValue = (amountSats / 100_000_000.0) * btcPrice
                            "≈ ${formatFiat(usdValue, fiatCurrency)}"
                        }
                    } else {
                        null
                    }

                OutlinedTextField(
                    value = amountInput,
                    enabled = !isSwapLocked,
                    onValueChange = { new ->
                        isMaxMode = false
                        when {
                            isUsdMode -> {
                                if (new.isEmpty() || new.matches(Regex("^\\d*\\.?\\d{0,2}$"))) {
                                    amountInput = new
                                }
                            }
                            useSats -> {
                                if (new.isEmpty() || new.matches(Regex("^\\d*$"))) {
                                    amountInput = new
                                }
                            }
                            else -> {
                                if (new.isEmpty() || new.matches(Regex("^\\d*\\.?\\d{0,8}$"))) {
                                    amountInput = new
                                }
                            }
                        }
                        onResetSwap()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(
                            when {
                                isUsdMode -> "0.00"
                                useSats -> "0"
                                else -> "0.00000000"
                            },
                            color = TextTertiary,
                        )
                    },
                    leadingIcon = if (isUsdMode) {
                        { Text(fiatCurrency, color = TextSecondary) }
                    } else {
                        null
                    },
                    suffix = if (conversionText != null) {
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
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (useSats && !isUsdMode) KeyboardType.Number else KeyboardType.Decimal,
                    ),
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = if (isMaxMode) LiquidTeal else BorderColor,
                        unfocusedBorderColor = if (isMaxMode) LiquidTeal else BorderColor,
                        cursorColor = LiquidTeal,
                    ),
                )

                if (amountWarnings.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        amountWarnings.forEach { warning ->
                            Text(
                                text = warning,
                                style = MaterialTheme.typography.bodySmall,
                                color = ErrorRed,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Start,
                ) {
                    Text(
                        text = stringResource(R.string.loc_c624cde4),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (privacyMode) {
                            "****"
                        } else {
                            "${formatAmt(sourceAvailableBalance, useSats)} $unit"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = fromColor,
                    )
                    if (btcPrice != null && btcPrice > 0 && !privacyMode) {
                        Text(
                            text = stringResource(R.string.loc_054cdf00),
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary.copy(alpha = 0.7f),
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    val maxEnabled = sourceAvailableBalance > 0
                    Card(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(enabled = maxEnabled && !isSwapLocked) {
                                if (isMaxMode) {
                                    isMaxMode = false
                                    amountInput = ""
                                } else {
                                    isMaxMode = true
                                    amountInput = when {
                                        isUsdMode && btcPrice != null && btcPrice > 0 -> {
                                            val usdValue = (sourceAvailableBalance.toDouble() / 100_000_000.0) * btcPrice
                                            String.format(Locale.US, "%.2f", usdValue)
                                        }
                                        useSats -> sourceAvailableBalance.toString()
                                        else -> formatBtcInput(sourceAvailableBalance)
                                    }
                                }
                                onResetSwap()
                            },
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isMaxMode) LiquidTeal.copy(alpha = 0.15f) else DarkSurface,
                        ),
                        border = BorderStroke(1.dp, if (isMaxMode) LiquidTeal else BorderColor),
                    ) {
                        Text(
                            text = stringResource(R.string.loc_a53b6469),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isMaxMode) {
                                LiquidTeal
                            } else if (maxEnabled) {
                                TextSecondary
                            } else {
                                TextSecondary.copy(alpha = 0.5f)
                            },
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        )
                    }
                }

                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Card(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(enabled = !isSwapLocked) { showLabelField = !showLabelField },
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (showLabelField || labelText.isNotBlank()) {
                                LiquidTeal.copy(alpha = 0.15f)
                            } else {
                                DarkSurface
                            },
                        ),
                        border = BorderStroke(1.dp, if (showLabelField || labelText.isNotBlank()) LiquidTeal else BorderColor),
                    ) {
                        Text(
                            text = stringResource(R.string.loc_cf667fec),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (showLabelField || labelText.isNotBlank()) LiquidTeal else TextSecondary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        )
                    }

                    if (showLabelField) {
                        OutlinedTextField(
                            value = labelText,
                            onValueChange = { labelText = it },
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 8.dp),
                            placeholder = {
                                Text(
                                    stringResource(R.string.loc_dde5d7d1),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary.copy(alpha = 0.5f),
                                )
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = LiquidTeal,
                                unfocusedBorderColor = BorderColor,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary,
                                cursorColor = LiquidTeal,
                            ),
                            enabled = !isSwapLocked,
                            textStyle = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = stringResource(R.string.loc_0094f526),
                    style = MaterialTheme.typography.labelLarge,
                    color = TextSecondary,
                )

                Spacer(modifier = Modifier.height(8.dp))

                SwapProviderDropdown(
                    options = providerOptions,
                    selectedOption = selectedProvider,
                    enabled = !isSwapLocked,
                    onOptionSelected = { option ->
                        clearMaxSelectionForServiceChange(option.service)
                        selectedService = option.service
                        onPreferredServiceChange(option.service)
                        onResetSwap()
                    },
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text =
                            "Min: ${
                                when {
                                    swapLimits?.error != null -> "Error"
                                    swapLimits == null || swapLimits.isLoading -> "..."
                                    else -> "${formatAmt(swapLimits.minAmount, useSats)} $unit"
                                }
                            }",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (swapLimits?.error != null) ErrorRed else TextSecondary,
                    )
                    Text(
                        text =
                            "Max: ${
                                when {
                                    swapLimits?.error != null -> "Error"
                                    swapLimits == null || swapLimits.isLoading -> "..."
                                    swapLimits.maxAmount > 0 -> "${formatAmt(swapLimits.maxAmount, useSats)} $unit"
                                    else -> "None"
                                }
                            }",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (swapLimits?.error != null) ErrorRed else TextSecondary,
                    )
                }

                if (swapLimits?.error != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.loc_ef217ee7),
                        style = MaterialTheme.typography.bodySmall,
                        color = LiquidTeal,
                        modifier = Modifier.clickable(enabled = !isSwapLocked) {
                            onFetchLimits(direction, selectedService)
                        },
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(enabled = !isSwapLocked) {
                                showAdvancedOptions = !showAdvancedOptions
                            }
                            .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.loc_20a1d916),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                    Icon(
                        imageVector = if (showAdvancedOptions) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription =
                            if (showAdvancedOptions) {
                                stringResource(R.string.loc_2d4a03c5)
                            } else {
                                stringResource(R.string.loc_f5276b4c)
                            },
                        tint = TextSecondary,
                    )
                }

                AnimatedVisibility(
                    visible = showAdvancedOptions,
                    enter = expandVertically(),
                    exit = shrinkVertically(),
                ) {
                    Column(
                        modifier =
                            Modifier
                                .bringIntoViewRequester(advancedOptionsBringIntoViewRequester)
                                .fillMaxWidth()
                                .padding(top = 12.dp),
                    ) {
                        HorizontalDivider(color = BorderColor)
                        Spacer(modifier = Modifier.height(8.dp))
                        SwapAdvancedToggleRow(
                            label = stringResource(R.string.loc_ee7df965),
                            checked = destinationOptionEnabled,
                            enabled = !isSwapLocked,
                            accentColor = destinationOptionAccentColor,
                            onCheckedChange = { enabled ->
                                if (isPegIn) {
                                    useCustomLiquidDestinationState.value = enabled
                                    if (!enabled) {
                                        customLiquidDestinationState.value = ""
                                    }
                                } else {
                                    useCustomBitcoinDestinationState.value = enabled
                                    if (!enabled) {
                                        customBitcoinDestinationState.value = ""
                                    }
                                }
                                onResetSwap()
                            },
                        )
                        AnimatedVisibility(
                            visible = destinationOptionEnabled,
                            enter = expandVertically(),
                            exit = shrinkVertically(),
                        ) {
                            Column(modifier = Modifier.bringIntoViewRequester(customDestinationBringIntoViewRequester)) {
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = currentCustomDestination,
                                    onValueChange = { newValue ->
                                        if (isPegIn) {
                                            customLiquidDestinationState.value = newValue.trim()
                                        } else {
                                            customBitcoinDestinationState.value = newValue.trim()
                                        }
                                        onResetSwap()
                                    },
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(start = 12.dp),
                                    enabled = !isSwapLocked,
                                    placeholder = {
                                        Text(
                                            text = destinationPlaceholder,
                                            color = TextTertiary,
                                        )
                                    },
                                    trailingIcon = {
                                        IconButton(
                                            onClick = { showCustomDestinationQrScanner = true },
                                            enabled = !isSwapLocked,
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.QrCodeScanner,
                                                contentDescription = "Scan QR Code",
                                                tint = destinationOptionAccentColor,
                                                modifier = Modifier.size(20.dp),
                                            )
                                        }
                                    },
                                    isError = destinationValidationError != null,
                                    singleLine = true,
                                    shape = RoundedCornerShape(8.dp),
                                    colors =
                                        OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = if (destinationValidationError != null) ErrorRed else destinationOptionAccentColor,
                                            unfocusedBorderColor = if (destinationValidationError != null) ErrorRed else BorderColor,
                                            cursorColor = destinationOptionAccentColor,
                                        ),
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text =
                                        when {
                                            destinationValidationError != null -> destinationValidationError
                                            currentCustomDestination.isBlank() -> destinationEmptyPrompt
                                            else -> destinationHelperText
                                        },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (destinationValidationError != null) ErrorRed else TextSecondary,
                                    modifier = Modifier.padding(start = 12.dp),
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        SwapAdvancedToggleRow(
                            label = stringResource(R.string.spark_transfer_custom_fee_rate),
                            checked = fundingFeeOptionEnabled,
                            enabled = !isSwapLocked,
                            accentColor = fundingFeeAccentColor,
                            onCheckedChange = { enabled ->
                                if (isPegIn) {
                                    useCustomBitcoinFeeRateState.value = enabled
                                    if (!enabled) {
                                        customBitcoinFeeRate = minFeeRate
                                        customBitcoinFeeOptionName = FeeRateOption.HALF_HOUR.name
                                        customBitcoinFeeRateInput = ""
                                    }
                                } else {
                                    useCustomLiquidFeeRateState.value = enabled
                                    if (!enabled) {
                                        customLiquidFeeRateInputState.value = ""
                                    }
                                }
                                onResetSwap()
                            },
                        )
                        AnimatedVisibility(
                            visible = fundingFeeOptionEnabled,
                            enter = expandVertically(),
                            exit = shrinkVertically(),
                        ) {
                            Column(modifier = Modifier.bringIntoViewRequester(customFeeBringIntoViewRequester)) {
                                Spacer(modifier = Modifier.height(8.dp))
                                if (isPegIn) {
                                    Column(
                                        modifier =
                                            Modifier
                                                .fillMaxWidth()
                                                .padding(start = 12.dp),
                                    ) {
                                        FeeRateSection(
                                            feeEstimationState = feeEstimationState,
                                            currentFeeRate = customBitcoinFeeRate,
                                            minFeeRate = minFeeRate,
                                            onFeeRateChange = {
                                                customBitcoinFeeRate = it
                                                onResetSwap()
                                            },
                                            selectedOption = customBitcoinFeeOption,
                                            onSelectedOptionChange = {
                                                customBitcoinFeeOptionName = it.name
                                            },
                                            customFeeInput = customBitcoinFeeRateInput.takeIf { it.isNotBlank() },
                                            onCustomFeeInputChange = {
                                                customBitcoinFeeRateInput = it.orEmpty()
                                            },
                                            onRefreshFees = onRefreshBitcoinFees,
                                            enabled = !isSwapLocked,
                                        )
                                    }
                                } else {
                                    SwapFundingFeeRateField(
                                        label = fundingFeeFieldLabel,
                                        feeRateInput = currentFundingFeeRateInput,
                                        accentColor = fundingFeeAccentColor,
                                        helperText = fundingFeeDefaultText,
                                        errorText = fundingFeeRateValidationError,
                                        enabled = !isSwapLocked,
                                        modifier = Modifier.padding(start = 12.dp),
                                        onFeeRateChange = { input ->
                                            if (input.isEmpty() || input.matches(Regex("^\\d*\\.?\\d*$"))) {
                                                customLiquidFeeRateInputState.value = input
                                                onResetSwap()
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ── Quote / Action area ──
        when (swapState) {
            is SwapState.Idle -> {
                Button(
                    onClick = {
                        amountSats?.let { sats ->
                            onPrepareSwapReview(
                                direction,
                                sats,
                                selectedService,
                                selectedFundingUtxos.toList().takeIf { it.isNotEmpty() },
                                effectiveCustomDestination.trim().takeIf { it.isNotBlank() },
                                txLabel,
                                isMaxMode,
                                fundingFeeRateOverride,
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    enabled = canRequestQuote && broadcastingSwapId == null,
                    shape = RoundedCornerShape(8.dp),
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = LiquidTeal,
                            disabledContainerColor = LiquidTeal.copy(alpha = 0.3f),
                        ),
                ) {
                    Text(
                        text =
                            if (broadcastingSwapId != null) {
                                stringResource(R.string.loc_52f64443)
                            } else {
                                stringResource(R.string.loc_9a0b9f8e)
                            },
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }

            is SwapState.FetchingQuote,
            is SwapState.PreparingReview,
            -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkCard),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = LiquidTeal,
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text =
                                if (broadcastingSwapId != null) {
                                    stringResource(R.string.loc_52f64443)
                                } else {
                                    stringResource(R.string.loc_9ff7bb31)
                                },
                            color = TextSecondary,
                        )
                    }
                }
            }

            is SwapState.ReviewReady -> {
                Button(
                    onClick = { showConfirmation = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = LiquidTeal),
                ) {
                    Text(stringResource(R.string.loc_1b4f82ec), color = TextPrimary)
                }
            }

            is SwapState.InProgress -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkCard),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(28.dp),
                            strokeWidth = 3.dp,
                            color = LiquidTeal,
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = stringResource(R.string.loc_020518a4),
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = swapState.status,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "ID: ${swapState.swapId.take(16)}...",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextTertiary,
                        )
                    }
                }
            }

            is SwapState.Completed -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkCard),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = stringResource(R.string.loc_d0375699),
                            style = MaterialTheme.typography.titleMedium,
                            color = SuccessGreen,
                            fontWeight = FontWeight.SemiBold,
                        )
                        swapState.settlementTxid?.takeIf { it.isNotBlank() }?.let { settlementTxid ->
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Settlement TX: ${settlementTxid.take(20)}...",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                            )
                        }
                        swapState.fundingTxid?.takeIf { it.isNotBlank() }?.let { fundingTxid ->
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Funding TX: ${fundingTxid.take(20)}...",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextTertiary,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = {
                        onResetSwap()
                        clearSwapForm()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = LiquidTeal),
                ) {
                    Text(stringResource(R.string.loc_f3b88bca), color = TextPrimary)
                }
            }

            is SwapState.Failed -> {
                val failedSwap = swapState.swap
                var showDetails by remember { mutableStateOf(false) }
                val context = LocalContext.current
                val failedDetailsBringIntoViewRequester =
                    rememberBringIntoViewRequesterOnExpand(showDetails, "failed_swap_details")

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkCard),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = stringResource(R.string.loc_9e5eae21),
                            style = MaterialTheme.typography.titleMedium,
                            color = ErrorRed,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = swapState.error,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            textAlign = TextAlign.Center,
                        )

                        if (failedSwap != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = failedSwap.status,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                                textAlign = TextAlign.Center,
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = if (showDetails) "Hide Details" else "Show Details",
                                style = MaterialTheme.typography.labelMedium,
                                color = LiquidTeal,
                                modifier = Modifier.clickable { showDetails = !showDetails },
                            )

                            AnimatedVisibility(visible = showDetails) {
                                Column(
                                    modifier = Modifier
                                        .bringIntoViewRequester(failedDetailsBringIntoViewRequester)
                                        .fillMaxWidth()
                                        .padding(top = 8.dp),
                                ) {
                                    HorizontalDivider(color = BorderColor)
                                    Spacer(modifier = Modifier.height(8.dp))

                                    CopyableDetailRow(stringResource(R.string.loc_3e5cd869), failedSwap.swapId, context)
                                    CopyableDetailRow(stringResource(R.string.loc_3e8c11e4), failedSwap.depositAddress, context)
                                    failedSwap.refundAddress?.let {
                                        CopyableDetailRow(stringResource(R.string.loc_245027bd), it, context)
                                    }
                                    failedSwap.receiveAddress?.let {
                                        CopyableDetailRow(stringResource(R.string.loc_ff3e9c4d), it, context)
                                    }
                                    failedSwap.fundingTxid?.let {
                                        CopyableDetailRow(stringResource(R.string.loc_c52f86d9), it, context)
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (failedSwap != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = { onDismissFailedSwap() },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceVariant),
                        ) {
                            Text(stringResource(R.string.loc_fff59f5b), color = TextSecondary)
                        }
                        Button(
                            onClick = { onResetSwap() },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = LiquidTeal),
                        ) {
                            Text(stringResource(R.string.loc_ef217ee7), color = TextPrimary)
                        }
                    }
                } else {
                    Button(
                        onClick = { onResetSwap() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = LiquidTeal),
                    ) {
                        Text(stringResource(R.string.loc_cddc8112), color = TextPrimary)
                    }
                }
            }
        }

        if (showConfirmation && reviewDialogSwap != null) {
            val activeReviewSwap = reviewDialogSwap!!
            SwapReviewDialog(
                pendingSwap = activeReviewSwap,
                btcPrice = btcPrice,
                fiatCurrency = fiatCurrency,
                privacyMode = privacyMode,
                useSats = useSats,
                unit = unit,
                isExecuting = executingReviewSwapId != null,
                onConfirm = {
                    executingReviewSwapId = activeReviewSwap.swapId
                    broadcastingSwapId = activeReviewSwap.swapId
                    val selectedReviewUtxos =
                        resolveSwapFundingSelection(
                            pendingSwap = activeReviewSwap,
                            btcUtxos = btcUtxos,
                            liquidUtxos = liquidUtxos,
                            selectedBitcoinUtxos = selectedBitcoinUtxos.toList(),
                            selectedLiquidUtxos = selectedLiquidUtxos.toList(),
                        )
                    showConfirmation = false
                    onExecuteSwap(activeReviewSwap, selectedReviewUtxos)
                },
                onDismiss = {
                    if (executingReviewSwapId != null) return@SwapReviewDialog
                    showConfirmation = false
                    reviewDialogSwap = null
                    onCancelPreparedReview()
                },
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

private fun resolveInitialSwapService(
    preferredService: SwapService,
    boltzEnabled: Boolean,
    sideSwapEnabled: Boolean,
): SwapService {
    return when {
        preferredService == SwapService.BOLTZ && boltzEnabled -> SwapService.BOLTZ
        preferredService == SwapService.SIDESWAP && sideSwapEnabled -> SwapService.SIDESWAP
        boltzEnabled -> SwapService.BOLTZ
        sideSwapEnabled -> SwapService.SIDESWAP
        else -> preferredService
    }
}

// ════════════════════════════════════════════
// Private composables
// ════════════════════════════════════════════

@Composable
private fun CopyableDetailRow(
    label: String,
    value: String,
    context: android.content.Context,
) {
    var copied by remember { mutableStateOf(false) }
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TextTertiary,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    SecureClipboard.copyAndScheduleClear(context, value)
                    copied = true
                },
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                modifier = Modifier.weight(1f),
            )
            if (copied) {
                Text(
                    text = stringResource(R.string.loc_5d00536d),
                    style = MaterialTheme.typography.labelSmall,
                    color = LiquidTeal,
                )
            }
        }
    }
}

@Composable
private fun PendingSwapsCard(
    pendingSwaps: List<PendingSwapSession>,
    boltzRescueMnemonic: String?,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    useSats: Boolean,
    unit: String,
    privacyMode: Boolean,
) {
    val expandedSwapIds = remember { mutableStateMapOf<String, Boolean>() }
    val cardBringIntoViewRequester = rememberBringIntoViewRequesterOnExpand(expanded, "pending_swaps_card")

    LaunchedEffect(pendingSwaps) {
        val activeIds = pendingSwaps.map { it.swapId }.toSet()
        val staleIds = expandedSwapIds.keys.filterNot { it in activeIds }
        staleIds.forEach(expandedSwapIds::remove)
    }

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
                    text = stringResource(R.string.loc_36c054ad),
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = stringResource(R.string.loc_2abb5095),
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription =
                        if (expanded) {
                            stringResource(R.string.loc_6e01dd8a)
                        } else {
                            stringResource(R.string.loc_0a0d0a98)
                        },
                    tint = LiquidTeal,
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .padding(top = 12.dp)
                        .bringIntoViewRequester(cardBringIntoViewRequester),
                ) {
                    pendingSwaps.forEachIndexed { index, pendingSwap ->
                        if (index > 0) {
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        PendingSwapRow(
                            pendingSwap = pendingSwap,
                            boltzRescueMnemonic = boltzRescueMnemonic,
                            useSats = useSats,
                            unit = unit,
                            privacyMode = privacyMode,
                            expanded = expandedSwapIds[pendingSwap.swapId] ?: false,
                            onExpandedChange = { isExpanded ->
                                expandedSwapIds[pendingSwap.swapId] = isExpanded
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PendingSwapRow(
    pendingSwap: PendingSwapSession,
    boltzRescueMnemonic: String?,
    useSats: Boolean,
    unit: String,
    privacyMode: Boolean,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
) {
    val providerLabel = pendingSwapProviderLabel(pendingSwap)
    val providerAccent = if (pendingSwap.service == SwapService.BOLTZ) AccentBlue else LiquidTeal
    val isPegIn = pendingSwap.direction == SwapDirection.BTC_TO_LBTC
    val sendAsset = if (pendingSwap.direction == SwapDirection.BTC_TO_LBTC) "BTC" else "L-BTC"
    val receiveAsset = if (pendingSwap.direction == SwapDirection.BTC_TO_LBTC) "L-BTC" else "BTC"
    val sendAccent = if (isPegIn) BitcoinOrange else LiquidTeal
    val receiveAccent = if (isPegIn) LiquidTeal else BitcoinOrange
    val idLabel =
        if (pendingSwap.service == SwapService.BOLTZ) {
            stringResource(R.string.loc_12c49c0c)
        } else {
            stringResource(R.string.loc_1598922a)
        }
    val sendAmountText = pendingSwapDisplayAmount(pendingSwap.sendAmount, useSats, unit, privacyMode)
    val showRescueKeyDialogState = rememberSaveable(pendingSwap.swapId) { mutableStateOf(false) }
    val showRescueKeyDialog = showRescueKeyDialogState.value
    val rowBringIntoViewRequester = rememberBringIntoViewRequesterOnExpand(expanded, "pending_swap_${pendingSwap.swapId}")

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = BorderStroke(1.dp, BorderColor),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onExpandedChange(!expanded) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PendingSwapDirectionBadge(
                        fromLabel = if (isPegIn) "L1" else "L2",
                        toLabel = if (isPegIn) "L2" else "L1",
                        fromColor = sendAccent,
                        toColor = receiveAccent,
                    )
                    Text(
                        text = sendAmountText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    RouteBadge(
                        text = providerLabel,
                        accentColor = providerAccent,
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription =
                            if (expanded) {
                                stringResource(R.string.loc_65b11dc2)
                            } else {
                                stringResource(R.string.loc_02cecfdb)
                            },
                        tint = TextSecondary,
                    )
                }
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                Column(modifier = Modifier.bringIntoViewRequester(rowBringIntoViewRequester)) {
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        PendingSwapAmountColumn(
                            label = "Sent",
                            value = sendAmountText,
                            asset = sendAsset,
                            accentColor = sendAccent,
                            modifier = Modifier.weight(1f),
                        )
                        PendingSwapAmountColumn(
                            label = "Est. receive",
                            value = pendingSwapDisplayAmount(
                                pendingSwap.estimatedTerms.receiveAmount,
                                useSats,
                                unit,
                                privacyMode,
                            ),
                            asset = receiveAsset,
                            accentColor = receiveAccent,
                            modifier = Modifier.weight(1f),
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        Text(
                            text = stringResource(R.string.loc_20fccd71),
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                            modifier = Modifier.weight(1f),
                        )
                        if (shouldShowBoltzRescueMnemonic(pendingSwap, boltzRescueMnemonic)) {
                            Spacer(modifier = Modifier.width(8.dp))
                            BoltzRescueKeyButton(
                                onClick = { showRescueKeyDialogState.value = true },
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = BorderColor)
                    Spacer(modifier = Modifier.height(8.dp))

                    PendingSwapDetailRow(idLabel, pendingSwap.swapId)
                    pendingSwap.label?.takeIf { it.isNotBlank() }?.let {
                        PendingSwapDetailRow(stringResource(R.string.loc_cf667fec), it)
                    }
                    PendingSwapDetailRow("Deposit Address", pendingSwap.depositAddress)
                    pendingSwap.receiveAddress?.let { PendingSwapDetailRow("Destination Address", it) }
                    pendingSwap.refundAddress?.let { PendingSwapDetailRow("Refund Address", it) }
                    pendingSwap.fundingTxid?.let { PendingSwapDetailRow("Funding Txid", it) }
                    pendingSwap.settlementTxid?.let { PendingSwapDetailRow("Settlement Txid", it) }
                }
            }
        }
    }

    if (showRescueKeyDialog && shouldShowBoltzRescueMnemonic(pendingSwap, boltzRescueMnemonic)) {
        BoltzRescueMnemonicDialog(
            mnemonic = boltzRescueMnemonic.orEmpty(),
            accentColor = LiquidTeal,
            onDismiss = { showRescueKeyDialogState.value = false },
        )
    }
}

private fun pendingSwapProgressValue(pendingSwap: PendingSwapSession): String {
    return pendingSwap.status.ifBlank { fallbackPendingSwapStatus(pendingSwap) }
}

private fun pendingSwapProviderLabel(pendingSwap: PendingSwapSession): String {
    return if (pendingSwap.service == SwapService.BOLTZ) "Boltz" else "SideSwap"
}

private fun fallbackPendingSwapStatus(pendingSwap: PendingSwapSession): String {
    val providerLabel = pendingSwapProviderLabel(pendingSwap)
    return when {
        pendingSwap.phase == PendingSwapPhase.REVIEW -> "Exact $providerLabel order prepared. Funding not started yet."
        pendingSwap.phase == PendingSwapPhase.FUNDING ->
            "Funding may already be in flight. Verifying $providerLabel status."
        pendingSwap.fundingTxid != null -> "Funding broadcast. Waiting for $providerLabel payout..."
        pendingSwap.direction == SwapDirection.BTC_TO_LBTC ->
            "Waiting for BTC deposit to ${pendingSwap.depositAddress}"
        else -> "Waiting for L-BTC deposit to ${pendingSwap.depositAddress}"
    }
}

@Composable
private fun PendingSwapAmountColumn(
    label: String,
    value: String,
    asset: String,
    accentColor: Color,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TextTertiary,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = accentColor,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = asset,
            style = MaterialTheme.typography.labelSmall,
            color = accentColor,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun PendingSwapDirectionBadge(
    fromLabel: String,
    toLabel: String,
    fromColor: Color,
    toColor: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(DarkCard)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = fromLabel,
            style = MaterialTheme.typography.labelMedium,
            color = fromColor,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = stringResource(R.string.loc_6b8bdd37),
            style = MaterialTheme.typography.labelMedium,
            color = TextSecondary,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = toLabel,
            style = MaterialTheme.typography.labelMedium,
            color = toColor,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun PendingSwapDetailRow(
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
                    text = truncateAddress(value),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary,
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = {
                    SecureClipboard.copyAndScheduleClear(context, value)
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
                text = stringResource(R.string.loc_e287255d),
                style = MaterialTheme.typography.bodySmall,
                color = LiquidTeal,
            )
        }
    }
}

private fun pendingSwapDisplayAmount(
    sats: Long,
    useSats: Boolean,
    unit: String,
    privacyMode: Boolean,
): String {
    return if (privacyMode) {
        "****"
    } else {
        "${formatAmt(sats, useSats)} $unit"
    }
}

@Composable
private fun RouteBadge(
    text: String,
    accentColor: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(accentColor.copy(alpha = 0.14f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = accentColor,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

private data class SwapProviderOption(
    val service: SwapService,
    val label: String,
    val description: String,
    val feeDetailText: String,
    val accentColor: Color,
    val enabled: Boolean,
)

@Composable
private fun SwapProviderDropdown(
    options: List<SwapProviderOption>,
    selectedOption: SwapProviderOption,
    enabled: Boolean,
    onOptionSelected: (SwapProviderOption) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    var fieldWidthPx by remember { mutableIntStateOf(0) }
    val borderColor = if (expanded) selectedOption.accentColor else BorderColor
    val density = LocalDensity.current
    val menuModifier = if (fieldWidthPx > 0) {
        Modifier.width(with(density) { fieldWidthPx.toDp() })
    } else {
        Modifier
    }

    Box(modifier = modifier.fillMaxWidth()) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned { coordinates ->
                    val width = coordinates.size.width
                    if (fieldWidthPx != width) {
                        fieldWidthPx = width
                    }
                }
                .clickable(enabled = enabled) { expanded = true },
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
            border = BorderStroke(1.dp, borderColor),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = selectedOption.label,
                    color = if (enabled) selectedOption.accentColor else TextSecondary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = selectedOption.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    textAlign = TextAlign.End,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = stringResource(R.string.loc_4818a44f),
                    tint = if (enabled) TextSecondary else TextSecondary.copy(alpha = 0.5f),
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = menuModifier
                .background(DarkSurface),
        ) {
            options.forEach { option ->
                val isSelected = option.service == selectedOption.service
                val accent = option.accentColor
                DropdownMenuItem(
                    enabled = option.enabled,
                    modifier = Modifier
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (isSelected) accent.copy(alpha = 0.16f) else DarkSurface,
                        ),
                    text = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = option.label,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = if (option.enabled) {
                                        accent
                                    } else {
                                        accent.copy(alpha = 0.5f)
                                    },
                                    fontWeight = if (isSelected) {
                                        FontWeight.SemiBold
                                    } else {
                                        FontWeight.Medium
                                    },
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = option.feeDetailText,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (option.enabled) {
                                        TextSecondary
                                    } else {
                                        TextSecondary.copy(alpha = 0.6f)
                                    },
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = option.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (option.enabled) {
                                    if (isSelected) accent.copy(alpha = 0.82f) else TextSecondary
                                } else {
                                    TextSecondary.copy(alpha = 0.6f)
                                },
                                textAlign = TextAlign.End,
                            )
                        }
                    },
                    onClick = {
                        onOptionSelected(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

private fun resolveSwapFundingSelection(
    pendingSwap: PendingSwapSession,
    btcUtxos: List<UtxoInfo>,
    liquidUtxos: List<UtxoInfo>,
    selectedBitcoinUtxos: List<UtxoInfo>,
    selectedLiquidUtxos: List<UtxoInfo>,
): List<UtxoInfo>? {
    val currentSelection =
        if (pendingSwap.direction == SwapDirection.BTC_TO_LBTC) {
            selectedBitcoinUtxos
        } else {
            selectedLiquidUtxos
        }
    val candidateUtxos =
        if (pendingSwap.direction == SwapDirection.BTC_TO_LBTC) {
            btcUtxos
        } else {
            liquidUtxos.filter { utxo ->
                utxo.assetId == null || LiquidAsset.isPolicyAsset(utxo.assetId)
            }
        }
    if (currentSelection.isNotEmpty()) {
        return selectCoinControlUtxos(
            outpoints = currentSelection.map { it.outpoint },
            availableUtxos = candidateUtxos,
        ).takeIf { it.isNotEmpty() }
    }
    if (pendingSwap.selectedFundingOutpoints.isEmpty()) {
        return null
    }
    return selectCoinControlUtxos(
        outpoints = pendingSwap.selectedFundingOutpoints,
        availableUtxos = candidateUtxos,
    ).takeIf { it.isNotEmpty() }
}

@Composable
private fun SwapCoinControlDialog(
    title: String,
    accentColor: Color,
    utxos: List<UtxoInfo>,
    selectedUtxos: List<UtxoInfo>,
    spendUnconfirmed: Boolean,
    useSats: Boolean,
    privacyMode: Boolean,
    btcPrice: Double?,
    isLiquid: Boolean,
    onDismiss: () -> Unit,
    onUtxoToggle: (UtxoInfo) -> Unit,
    onSelectAll: () -> Unit,
    onClearAll: () -> Unit,
) {
    val totalSelected = selectedUtxos.sumOf { it.amountSats.toLong() }
    val hiddenAmount = "****"
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
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text =
                        if (selectedUtxos.isNotEmpty()) {
                            val amountPart =
                                if (privacyMode) {
                                    hiddenAmount
                                } else {
                                    formatAmount(totalSelected.toULong(), useSats, includeUnit = true)
                                }
                            stringResource(
                                R.string.coin_control_selected_summary_format,
                                selectedUtxos.size,
                                amountPart,
                            )
                        } else {
                            stringResource(R.string.loc_cfe01156)
                        },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (selectedUtxos.isNotEmpty()) accentColor else TextSecondary,
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
                        Text(stringResource(R.string.loc_27833d8a), color = accentColor)
                    }
                    TextButton(
                        onClick = onClearAll,
                        modifier = Modifier.weight(1f),
                        enabled = selectedUtxos.isNotEmpty(),
                    ) {
                        Text(
                            text = stringResource(R.string.loc_4340d39b),
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
                                            isSelected -> accentColor.copy(alpha = 0.15f)
                                            else -> DarkCard
                                        },
                                ),
                            border =
                                if (isSelected && !isDisabled) {
                                    BorderStroke(1.dp, accentColor.copy(alpha = 0.5f))
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
                                    contentDescription =
                                        if (isSelected) {
                                            stringResource(R.string.common_selected)
                                        } else {
                                            stringResource(R.string.loc_e17307d1)
                                        },
                                    tint =
                                        when {
                                            isDisabled -> TextSecondary.copy(alpha = 0.3f)
                                            isSelected -> accentColor
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
                                                    hiddenAmount
                                                } else {
                                                    formatAmount(utxo.amountSats, useSats, includeUnit = true)
                                                },
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Medium,
                                            color = if (isDisabled) TextPrimary.copy(alpha = 0.4f) else TextPrimary,
                                        )
                                        if (btcPrice != null && btcPrice > 0 && !privacyMode) {
                                            val usdValue = utxo.amountSats.toLong() * btcPrice / 100_000_000.0
                                            Text(
                                                text = " · $${String.format(Locale.US, "%,.2f", usdValue)}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = if (isDisabled) TextSecondary.copy(alpha = 0.4f) else TextSecondary,
                                            )
                                        }
                                    }

                                    Text(
                                        text = utxo.address,
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
                                            color = if (isDisabled) accentColor.copy(alpha = 0.4f) else accentColor,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }

                                    if (isDisabled) {
                                        Text(
                                            text = stringResource(R.string.loc_bdedd2ce),
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
                                                    accentColor.copy(alpha = if (isDisabled) 0.1f else 0.2f)
                                                },
                                            )
                                            .padding(horizontal = 6.dp, vertical = 2.dp),
                                ) {
                                    Text(
                                        text =
                                            if (utxo.isConfirmed) {
                                                stringResource(R.string.loc_4ab75d7f)
                                            } else {
                                                stringResource(R.string.loc_1b684325)
                                            },
                                        style = MaterialTheme.typography.labelSmall,
                                        color =
                                            if (utxo.isConfirmed) {
                                                SuccessGreen.copy(alpha = if (isDisabled) 0.4f else 1f)
                                            } else {
                                                accentColor.copy(alpha = if (isDisabled) 0.4f else 1f)
                                            },
                                    )
                                }
                            }
                        }
                    }
                }

                if (selectedUtxos.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text =
                            if (isLiquid) {
                                stringResource(R.string.loc_6ff3adfb)
                            } else {
                                stringResource(R.string.loc_cce909f4)
                            },
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
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
                            containerColor = accentColor,
                            contentColor = DarkBackground,
                        ),
                ) {
                    Text(
                        text = stringResource(R.string.loc_b01f4f95),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }
    }
}

private fun providerFeeDetailText(
    limits: SwapLimits?,
    service: SwapService,
    loadingText: String,
    unavailableText: String,
    boltzText: String,
    sideSwapText: String,
): String {
    if (limits == null || limits.isLoading) {
        return loadingText
    }
    if (limits.error != null) {
        return unavailableText
    }

    return when (service) {
        SwapService.BOLTZ -> boltzText
        SwapService.SIDESWAP -> sideSwapText
    }
}


@Composable
private fun FeeRow(
    label: String,
    value: String,
    subtext: String? = null,
    valueSubtext: String? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Column(
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = TextSecondary,
            )
            subtext?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextTertiary,
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                color = TextPrimary,
                fontWeight = FontWeight.Medium,
            )
            valueSubtext?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextTertiary,
                    textAlign = TextAlign.End,
                )
            }
        }
    }
}

private fun truncateAddress(address: String, edgeChars: Int = 10): String =
    if (address.length <= edgeChars * 2 + 3) address
    else "${address.take(edgeChars)}...${address.takeLast(edgeChars)}"

@Composable
private fun AddressRow(
    label: String,
    value: String,
    context: android.content.Context,
) {
    val displayValue = truncateAddress(value)
    var showCopied by remember { mutableStateOf(false) }
    val copyLabelContentDescription = stringResource(R.string.common_copy_with_label, label)

    LaunchedEffect(showCopied, value) {
        if (!showCopied) return@LaunchedEffect
        delay(1_500)
        showCopied = false
    }

    Column(modifier = Modifier.fillMaxWidth()) {
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
                    text = displayValue,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary,
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = {
                    SecureClipboard.copyAndScheduleClear(
                        context = context,
                        text = value,
                    )
                    showCopied = true
                },
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = copyLabelContentDescription,
                    tint = LiquidTeal,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        if (showCopied) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.loc_e287255d),
                style = MaterialTheme.typography.bodySmall,
                color = LiquidTeal,
            )
        }
    }
}

@Composable
private fun SwapReviewDialog(
    pendingSwap: PendingSwapSession,
    btcPrice: Double?,
    fiatCurrency: String,
    privacyMode: Boolean,
    useSats: Boolean = true,
    unit: String = "sats",
    isExecuting: Boolean = false,
    executionStatus: String? = null,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val estimated = pendingSwap.estimatedTerms
    val isPegIn = pendingSwap.direction == SwapDirection.BTC_TO_LBTC
    val hidden = "****"
    val context = LocalContext.current
    fun fmt(sats: Long) = "${formatAmt(sats, useSats)} $unit"
    val serviceAccent = if (pendingSwap.service == SwapService.BOLTZ) AccentBlue else LiquidTeal
    val sendAccent = if (isPegIn) BitcoinOrange else LiquidTeal
    val receiveAccent = if (isPegIn) LiquidTeal else BitcoinOrange
    val sendLayerLabel = if (isPegIn) stringResource(R.string.loc_b67a01a5) else stringResource(R.string.loc_2f73501f)
    val receiveLayerLabel = if (isPegIn) stringResource(R.string.loc_2f73501f) else stringResource(R.string.loc_b67a01a5)
    val copySwapIdLabel = stringResource(R.string.loc_c0b66d5a)
    val orderId = pendingSwap.swapId
    val fundingNetworkFee = estimated.fundingNetworkFeeFor(pendingSwap.direction)
    val payoutNetworkFee = estimated.payoutNetworkFeeFor(pendingSwap.direction)
    val satVbUnit = stringResource(R.string.loc_aedd48eb)
    val fundingRate = estimated.fundingFeeRateFor(pendingSwap.direction)
    val fundingFeeRateText =
        if (fundingRate > 0.0) {
            stringResource(
                R.string.swap_review_approx_fee_rate,
                String.format(Locale.US, "%.1f", fundingRate),
                satVbUnit,
            )
        } else {
            null
        }
    val payoutRate = estimated.payoutFeeRateFor(pendingSwap.direction)
    val payoutFeeRateText =
        if (payoutRate > 0.0) {
            stringResource(
                R.string.swap_review_approx_fee_rate,
                String.format(Locale.US, "%.1f", payoutRate),
                satVbUnit,
            )
        } else {
            null
        }
    val serviceFeePercentText =
        pendingSwap.sendAmount.takeIf { it > 0 }?.let {
            "${String.format(Locale.US, "%.2f", estimated.serviceFee * 100.0 / it).trimEnd('0').trimEnd('.') }%"
        }
    val fundingFeeLabel =
        if (isPegIn) stringResource(R.string.loc_85a9e0cb) else stringResource(R.string.loc_c5e0700a)
    val payoutFeeLabel =
        if (isPegIn) stringResource(R.string.loc_d3b3a18f) else stringResource(R.string.loc_41bcc5c5)
    data class ReviewFeeLine(
        val label: String,
        val value: Long,
        val subtext: String? = null,
    )
    val serviceLabel =
        when (pendingSwap.service) {
            SwapService.BOLTZ -> stringResource(R.string.loc_776ee9d4)
            SwapService.SIDESWAP -> stringResource(R.string.loc_88bc08a6)
        }
    val providerTransferFeesLabel = stringResource(R.string.loc_6203b65d)
    val estimatedByBoltzText = stringResource(R.string.loc_9283713e)
    val estimatedBySideSwapText = stringResource(R.string.loc_5154065c)
    val feeLines = buildList {
        if (fundingNetworkFee > 0L) {
            add(ReviewFeeLine(fundingFeeLabel, fundingNetworkFee, fundingFeeRateText))
        }
        add(ReviewFeeLine(serviceLabel, estimated.serviceFee, serviceFeePercentText))
        if (estimated.providerMinerFee > 0L) {
            val providerSubtext =
                when (pendingSwap.service) {
                    SwapService.BOLTZ -> estimatedByBoltzText
                    SwapService.SIDESWAP -> estimatedBySideSwapText
                }
            add(ReviewFeeLine(providerTransferFeesLabel, estimated.providerMinerFee, providerSubtext))
        }
        if (payoutNetworkFee > 0L) {
            add(ReviewFeeLine(payoutFeeLabel, payoutNetworkFee, payoutFeeRateText))
        }
    }
    val feesCardTitle = stringResource(R.string.loc_00a16e52)
    val totalFeeLabel = stringResource(R.string.loc_7d9effcf)
    val totalFeeSats =
        if (pendingSwap.service == SwapService.BOLTZ) {
            estimated.totalSwapDeductionsFor(pendingSwap.direction)
        } else {
            estimated.totalFee
        }
    val collapsedFeeText = if (privacyMode) hidden else fmt(totalFeeSats)
    val collapsedFeeUsdText = swapReviewUsdText(totalFeeSats, btcPrice, fiatCurrency, privacyMode)
    val totalFeeText = if (privacyMode) hidden else fmt(totalFeeSats)
    val totalFeeUsdText = swapReviewUsdText(totalFeeSats, btcPrice, fiatCurrency, privacyMode)
    var showSwapIdCopied by remember { mutableStateOf(false) }
    var addressesExpanded by rememberSaveable(orderId) { mutableStateOf(false) }
    var feesExpanded by rememberSaveable(orderId) { mutableStateOf(false) }
    val addressesBringIntoViewRequester = remember(orderId) { BringIntoViewRequester() }
    val feesBringIntoViewRequester = remember(orderId) { BringIntoViewRequester() }
    val label = pendingSwap.label?.takeIf { it.isNotBlank() }

    LaunchedEffect(showSwapIdCopied, orderId) {
        if (!showSwapIdCopied) return@LaunchedEffect
        delay(1_500)
        showSwapIdCopied = false
    }

    LaunchedEffect(addressesExpanded, orderId) {
        if (!addressesExpanded) return@LaunchedEffect
        delay(180)
        addressesBringIntoViewRequester.bringIntoView()
    }

    LaunchedEffect(feesExpanded, orderId) {
        if (!feesExpanded) return@LaunchedEffect
        delay(180)
        feesBringIntoViewRequester.bringIntoView()
    }

    ScrollableDialogSurface(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        containerColor = DarkCard,
        contentPadding = PaddingValues(20.dp),
        actions = {
            Button(
                onClick = {
                    onConfirm()
                },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                enabled = !isExecuting,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = LiquidTeal),
            ) {
                if (isExecuting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = TextPrimary,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    if (isExecuting) stringResource(R.string.loc_94bea00f) else stringResource(R.string.loc_7a4eb12d),
                    color = TextPrimary,
                )
            }

            if (isExecuting && !executionStatus.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = executionStatus,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = BorderColor)
            Spacer(modifier = Modifier.height(8.dp))

            IbisButton(
                onClick = onDismiss,
                enabled = !isExecuting,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(48.dp),
            ) {
                Text(stringResource(R.string.loc_51bac044), style = MaterialTheme.typography.titleMedium)
            }
        },
    ) {
                Text(
                    text = stringResource(R.string.loc_75935701),
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RouteBadge(
                        text = pendingSwap.service.name,
                        accentColor = serviceAccent,
                    )
                    Text(
                        text = estimated.estimatedTime,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextTertiary,
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.loc_a9af1b38),
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (privacyMode) hidden else fmt(pendingSwap.sendAmount),
                            style = MaterialTheme.typography.headlineSmall,
                            color = sendAccent,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                        )
                        swapReviewUsdText(pendingSwap.sendAmount, btcPrice, fiatCurrency, privacyMode)?.let { usdText ->
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = usdText,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextTertiary,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center,
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = sendLayerLabel,
                            style = MaterialTheme.typography.labelLarge,
                            color = sendAccent,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center,
                        ) {
                            HorizontalDivider(
                                modifier = Modifier.fillMaxWidth(),
                                color = BorderColor.copy(alpha = 0.3f),
                            )
                            Text(
                                text = stringResource(R.string.loc_54da2c24),
                                style = MaterialTheme.typography.labelMedium,
                                color = TextTertiary,
                                modifier = Modifier
                                    .background(DarkSurface)
                                    .padding(horizontal = 10.dp),
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = receiveLayerLabel,
                            style = MaterialTheme.typography.labelLarge,
                            color = receiveAccent,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (privacyMode) hidden else fmt(estimated.receiveAmount),
                            style = MaterialTheme.typography.headlineSmall,
                            color = receiveAccent,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                        )
                        swapReviewUsdText(estimated.receiveAmount, btcPrice, fiatCurrency, privacyMode)?.let { usdText ->
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = usdText,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextTertiary,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center,
                            )
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        HorizontalDivider(color = BorderColor.copy(alpha = 0.3f))
                        Spacer(modifier = Modifier.height(14.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text =
                                        if (pendingSwap.service == SwapService.SIDESWAP) {
                                            stringResource(R.string.loc_1598922a)
                                        } else {
                                            stringResource(R.string.loc_12c49c0c)
                                        },
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = TextSecondary,
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = orderId,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextPrimary,
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier =
                                    Modifier
                                        .size(32.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(DarkSurfaceVariant)
                                        .clickable {
                                            SecureClipboard.copyAndScheduleClear(
                                                context = context,
                                                text = orderId,
                                            )
                                            showSwapIdCopied = true
                                        },
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = copySwapIdLabel,
                                    tint = if (showSwapIdCopied) LiquidTeal else TextSecondary,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                        if (showSwapIdCopied) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = stringResource(R.string.loc_e287255d),
                                style = MaterialTheme.typography.bodySmall,
                                color = LiquidTeal,
                            )
                        }

                        label?.let {
                            Spacer(modifier = Modifier.height(14.dp))
                            HorizontalDivider(color = BorderColor.copy(alpha = 0.3f))
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = stringResource(R.string.loc_cf667fec),
                                style = MaterialTheme.typography.bodyLarge,
                                color = TextSecondary,
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextPrimary,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { addressesExpanded = !addressesExpanded },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(R.string.loc_ed3bf7b5),
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Icon(
                                imageVector = if (addressesExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription =
                                    if (addressesExpanded) {
                                        stringResource(R.string.loc_0430ad11)
                                    } else {
                                        stringResource(R.string.loc_afb3fec8)
                                    },
                                tint = LiquidTeal,
                            )
                        }
                        AnimatedVisibility(addressesExpanded) {
                            Column(
                                modifier = Modifier
                                    .bringIntoViewRequester(addressesBringIntoViewRequester)
                                    .fillMaxWidth()
                                    .padding(top = 12.dp),
                            ) {
                                AddressRow(
                                    label = stringResource(R.string.loc_40fb77d1),
                                    value = pendingSwap.depositAddress,
                                    context = context,
                                )
                                pendingSwap.receiveAddress?.takeIf { it.isNotBlank() }?.let { receiveAddress ->
                                    Spacer(modifier = Modifier.height(10.dp))
                                    AddressRow(
                                        label = stringResource(R.string.loc_3083a5d1),
                                        value = receiveAddress,
                                        context = context,
                                    )
                                }
                                pendingSwap.refundAddress?.takeIf { it.isNotBlank() }?.let { refundAddress ->
                                    Spacer(modifier = Modifier.height(10.dp))
                                    AddressRow(
                                        label = stringResource(R.string.loc_245027bd),
                                        value = refundAddress,
                                        context = context,
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { feesExpanded = !feesExpanded },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = feesCardTitle,
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                if (!feesExpanded) {
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = collapsedFeeText,
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = TextPrimary,
                                            fontWeight = FontWeight.Medium,
                                        )
                                        collapsedFeeUsdText?.let {
                                            Text(
                                                text = it,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = TextTertiary,
                                                textAlign = TextAlign.End,
                                            )
                                        }
                                    }
                                }
                                Icon(
                                    imageVector = if (feesExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                    contentDescription =
                                        if (feesExpanded) {
                                            stringResource(R.string.loc_e607c9c3)
                                        } else {
                                            stringResource(R.string.loc_cd1ee95d)
                                        },
                                    tint = LiquidTeal,
                                )
                            }
                        }
                        AnimatedVisibility(feesExpanded) {
                            Column(
                                modifier = Modifier
                                    .bringIntoViewRequester(feesBringIntoViewRequester)
                                    .fillMaxWidth()
                                    .padding(top = 12.dp),
                            ) {
                                feeLines.forEach { line ->
                                    FeeRow(
                                        line.label,
                                        if (privacyMode) hidden else fmt(line.value),
                                        subtext = if (privacyMode) null else line.subtext,
                                        valueSubtext = swapReviewUsdText(line.value, btcPrice, fiatCurrency, privacyMode),
                                    )
                                }
                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    color = BorderColor.copy(alpha = 0.3f),
                                )
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.Top,
                                ) {
                                    Text(
                                        text = totalFeeLabel,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = TextSecondary,
                                    )
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = totalFeeText,
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = TextPrimary,
                                            fontWeight = FontWeight.Medium,
                                        )
                                        totalFeeUsdText?.let {
                                            Text(
                                                text = it,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = TextTertiary,
                                                textAlign = TextAlign.End,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
    }
}

internal fun shouldShowBoltzRescueMnemonic(
    pendingSwap: PendingSwapSession,
    boltzRescueMnemonic: String?,
): Boolean {
    return pendingSwap.phase == PendingSwapPhase.IN_PROGRESS &&
        shouldShowBoltzRescueKey(pendingSwap.service, boltzRescueMnemonic)
}

@Composable
private fun SwapFundingFeeRateField(
    label: String,
    feeRateInput: String,
    accentColor: Color,
    helperText: String,
    errorText: String?,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onFeeRateChange: (String) -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = TextSecondary,
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = feeRateInput,
            onValueChange = onFeeRateChange,
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            suffix = {
                Text(
                    text = stringResource(R.string.loc_aedd48eb),
                    color = TextSecondary.copy(alpha = 0.7f),
                )
            },
            supportingText = {
                Text(
                    text = errorText ?: helperText,
                    color = if (errorText != null) ErrorRed else TextSecondary,
                )
            },
            isError = errorText != null,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            shape = RoundedCornerShape(8.dp),
            colors =
                OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = if (errorText != null) ErrorRed else accentColor,
                    unfocusedBorderColor = if (errorText != null) ErrorRed else BorderColor,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    cursorColor = accentColor,
                ),
        )
    }
}

@Composable
private fun SwapAdvancedToggleRow(
    label: String,
    checked: Boolean,
    enabled: Boolean,
    accentColor: Color,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable(enabled = enabled) { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors =
                CheckboxDefaults.colors(
                    checkedColor = accentColor,
                    uncheckedColor = TextSecondary,
                ),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (enabled) TextPrimary else TextSecondary.copy(alpha = 0.5f),
        )
    }
}

private fun formatAmt(sats: Long, useSats: Boolean): String {
    return if (useSats) {
        NumberFormat.getNumberInstance(Locale.US).format(sats)
    } else {
        String.format(Locale.US, "%.8f", sats.toDouble() / 100_000_000.0)
    }
}

private fun swapReviewUsdText(
    sats: Long,
    btcPrice: Double?,
    fiatCurrency: String,
    privacyMode: Boolean,
): String? {
    if (privacyMode || btcPrice == null || btcPrice <= 0.0) {
        return null
    }
    return formatFiat(sats.toDouble() * btcPrice / 100_000_000.0, fiatCurrency)
}

private fun formatBtcInput(sats: Long): String {
    return String.format(Locale.US, "%.8f", sats.toDouble() / 100_000_000.0)
}
