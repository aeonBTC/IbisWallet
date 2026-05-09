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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import github.aeonbtc.ibiswallet.data.local.SecureStorage
import github.aeonbtc.ibiswallet.data.model.DryRunResult
import github.aeonbtc.ibiswallet.data.model.FeeEstimates
import github.aeonbtc.ibiswallet.data.model.FeeEstimationResult
import github.aeonbtc.ibiswallet.data.model.SparkOnchainFeeQuote
import github.aeonbtc.ibiswallet.data.model.SparkOnchainFeeSpeed
import github.aeonbtc.ibiswallet.data.model.SparkReceiveKind
import github.aeonbtc.ibiswallet.data.model.SparkReceiveState
import github.aeonbtc.ibiswallet.data.model.SparkSendState
import github.aeonbtc.ibiswallet.data.model.SparkWalletState
import github.aeonbtc.ibiswallet.ui.components.AmountLabel
import github.aeonbtc.ibiswallet.ui.components.FeeRateOption
import github.aeonbtc.ibiswallet.ui.components.FeeRateSection
import github.aeonbtc.ibiswallet.ui.components.IbisButton
import github.aeonbtc.ibiswallet.ui.components.ScrollableDialogSurface
import github.aeonbtc.ibiswallet.ui.theme.BorderColor
import github.aeonbtc.ibiswallet.ui.theme.DarkCard
import github.aeonbtc.ibiswallet.ui.theme.DarkSurface
import github.aeonbtc.ibiswallet.ui.theme.DarkSurfaceVariant
import github.aeonbtc.ibiswallet.ui.theme.ErrorRed
import github.aeonbtc.ibiswallet.ui.theme.SparkPurple
import github.aeonbtc.ibiswallet.ui.theme.TextPrimary
import github.aeonbtc.ibiswallet.ui.theme.TextSecondary
import github.aeonbtc.ibiswallet.ui.theme.TextTertiary
import github.aeonbtc.ibiswallet.util.SecureClipboard
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import androidx.compose.material3.Text
import androidx.compose.ui.res.stringResource
import github.aeonbtc.ibiswallet.R

private val Layer1Accent = Color(0xFFCD7F32)
private const val HIDDEN = "****"

private sealed interface SparkTransferReview {
    val amountSats: Long
    val destinationAddress: String

    data class Deposit(
        override val amountSats: Long,
        override val destinationAddress: String,
        val dryRun: DryRunResult,
        val isMaxSend: Boolean,
        val fundingFeeRate: Double,
    ) : SparkTransferReview

    data class Withdrawal(
        override val amountSats: Long,
        override val destinationAddress: String,
        val preview: SparkSendState.Preview,
    ) : SparkTransferReview
}

@Composable
fun SparkTransferScreen(
    sparkState: SparkWalletState,
    receiveState: SparkReceiveState,
    layer1Address: String?,
    denomination: String,
    btcPrice: Double?,
    fiatCurrency: String,
    privacyMode: Boolean,
    layer1BalanceSats: Long,
    feeEstimationState: FeeEstimationResult,
    minFeeRate: Double,
    onRefreshBitcoinFees: () -> Unit,
    onLoadSparkRecommendedFeeEstimates: suspend () -> FeeEstimates?,
    onGenerateSparkDeposit: () -> Unit,
    onGenerateLayer1Address: () -> Unit,
    onPreviewLayer1ToSpark: suspend (String, Long, Double, Boolean) -> DryRunResult?,
    onExecuteLayer1ToSpark: suspend (String, Long, Double, Boolean) -> Unit,
    onPreviewSparkToLayer1: suspend (String, Long, SparkOnchainFeeSpeed, Boolean) -> SparkSendState.Preview,
    onLoadSparkWithdrawalFeeQuotes: suspend (String, Long, Boolean) -> List<SparkOnchainFeeQuote>,
    onExecuteSparkToLayer1: suspend () -> Unit,
    onResetSparkSend: () -> Unit,
    onToggleDenomination: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val useSats = denomination == SecureStorage.DENOMINATION_SATS
    var selectedDirection by remember { mutableIntStateOf(0) }
    var amountInput by remember { mutableStateOf("") }
    var isUsdMode by remember { mutableStateOf(false) }
    var isMaxMode by remember { mutableStateOf(false) }
    var isPreparingReview by remember { mutableStateOf(false) }
    var isExecutingReview by remember { mutableStateOf(false) }
    var reviewError by remember { mutableStateOf<String?>(null) }
    var reviewState by remember { mutableStateOf<SparkTransferReview?>(null) }
    var showAdvancedOptions by rememberSaveable { mutableStateOf(false) }
    var useCustomDestination by rememberSaveable { mutableStateOf(false) }
    var customDestination by rememberSaveable { mutableStateOf("") }
    var useCustomFundingFeeRate by rememberSaveable { mutableStateOf(false) }
    var customFundingFeeRateInput by rememberSaveable { mutableStateOf("") }
    var customFundingFeeRate by rememberSaveable { mutableDoubleStateOf(minFeeRate) }
    var customFundingFeeOptionName by rememberSaveable { mutableStateOf(FeeRateOption.HALF_HOUR.name) }
    var sparkRecommendedFeeEstimates by remember { mutableStateOf<FeeEstimates?>(null) }
    var useCustomWithdrawalFeeSpeed by rememberSaveable { mutableStateOf(false) }
    var withdrawalFeeSpeed by rememberSaveable { mutableStateOf(SparkOnchainFeeSpeed.FAST) }
    var withdrawalFeeQuotes by remember { mutableStateOf<List<SparkOnchainFeeQuote>>(emptyList()) }
    var withdrawalFeeQuotesLoading by remember { mutableStateOf(false) }
    val advancedOptionsBringIntoViewRequester = remember { BringIntoViewRequester() }
    val customDestinationBringIntoViewRequester = remember { BringIntoViewRequester() }
    val customFeeBringIntoViewRequester = remember { BringIntoViewRequester() }

    val amountSats = remember(amountInput, useSats, isUsdMode, btcPrice) {
        when {
            amountInput.isBlank() -> null
            isUsdMode && btcPrice != null && btcPrice > 0 ->
                amountInput.toDoubleOrNull()?.takeIf { it > 0 }?.let {
                    ((it / btcPrice) * 100_000_000).toLong()
                }
            useSats -> amountInput.replace(",", "").toLongOrNull()?.takeIf { it > 0 }
            else -> amountInput.toDoubleOrNull()?.takeIf { it > 0 }?.let { (it * 100_000_000).toLong() }
        }
    }

    val isLayer1ToSpark = selectedDirection == 0
    val layer1Label = stringResource(R.string.loc_b67a01a5)
    val layer2Label = stringResource(R.string.loc_2f73501f)
    val fromLayerLabel = if (isLayer1ToSpark) layer1Label else layer2Label
    val toLayerLabel = if (isLayer1ToSpark) layer2Label else layer1Label
    val sparkDepositAddressLabel = stringResource(R.string.spark_transfer_spark_deposit_address)
    val destinationOverrideHint = stringResource(R.string.spark_transfer_destination_override_hint)
    val enterDestinationAddressLabel = stringResource(R.string.spark_transfer_enter_destination_address)
    val customFeeRateLabel = stringResource(R.string.spark_transfer_custom_fee_rate)
    val enterFeeRateLabel = stringResource(R.string.spark_transfer_enter_fee_rate)
    val enterValidFeeRateLabel = stringResource(R.string.loc_857c8623)
    val enterFeeRateAboveZeroLabel = stringResource(R.string.loc_f2b5f4d4)
    val unableToPrepareSwapLabel = stringResource(R.string.swap_unable_to_prepare)
    val fromLabel = if (isLayer1ToSpark) "BTC" else "Spark"
    val toLabel = if (isLayer1ToSpark) "Spark" else "BTC"
    val fromColor = if (isLayer1ToSpark) Layer1Accent else SparkPurple
    val toColor = if (isLayer1ToSpark) SparkPurple else Layer1Accent
    val availableBalance = if (isLayer1ToSpark) layer1BalanceSats else sparkState.balanceSats
    val sparkDepositAddress =
        (receiveState as? SparkReceiveState.Ready)
            ?.takeIf { it.kind == SparkReceiveKind.BITCOIN_ADDRESS }
            ?.paymentRequest
    val destinationAddress =
        customDestination.trim()
            .takeIf { useCustomDestination && it.isNotBlank() }
            ?: if (isLayer1ToSpark) sparkDepositAddress else layer1Address
    val destinationValidationError =
        if (useCustomDestination && customDestination.isBlank()) {
            enterDestinationAddressLabel
        } else {
            null
        }
    val customFundingFeeOption =
        remember(customFundingFeeOptionName) {
            runCatching { FeeRateOption.valueOf(customFundingFeeOptionName) }.getOrDefault(FeeRateOption.HALF_HOUR)
        }
    val defaultFundingFeeRate =
        ((feeEstimationState as? FeeEstimationResult.Success)?.estimates?.halfHourFee
            ?: sparkRecommendedFeeEstimates?.halfHourFee
            ?: minFeeRate)
            .coerceAtLeast(minFeeRate)
    val effectiveFeeEstimationState =
        sparkRecommendedFeeEstimates?.let { FeeEstimationResult.Success(it) } ?: feeEstimationState
    val fundingFeeRateValidationError =
        when {
            !useCustomFundingFeeRate || !isLayer1ToSpark -> null
            customFundingFeeOption != FeeRateOption.CUSTOM -> null
            customFundingFeeRateInput.isBlank() -> enterFeeRateLabel
            customFundingFeeRateInput.toDoubleOrNull() == null -> enterValidFeeRateLabel
            customFundingFeeRateInput.toDoubleOrNull()!! <= 0.0 -> enterFeeRateAboveZeroLabel
            else -> null
        }
    val fundingFeeRate =
        if (isLayer1ToSpark && useCustomFundingFeeRate) {
            customFundingFeeRate
        } else {
            defaultFundingFeeRate
        }
    val canPrepareReview =
        !isPreparingReview &&
            !isExecutingReview &&
            destinationValidationError == null &&
            fundingFeeRateValidationError == null

    LaunchedEffect(showAdvancedOptions) {
        if (showAdvancedOptions) {
            delay(100)
            advancedOptionsBringIntoViewRequester.bringIntoView()
        }
    }

    LaunchedEffect(useCustomDestination) {
        if (useCustomDestination) {
            delay(100)
            customDestinationBringIntoViewRequester.bringIntoView()
        }
    }

    LaunchedEffect(useCustomFundingFeeRate) {
        if (useCustomFundingFeeRate) {
            delay(100)
            customFeeBringIntoViewRequester.bringIntoView()
        }
    }

    LaunchedEffect(isLayer1ToSpark, destinationAddress, amountSats, isMaxMode, useCustomWithdrawalFeeSpeed) {
        if (
            !isLayer1ToSpark &&
            useCustomWithdrawalFeeSpeed &&
            !destinationAddress.isNullOrBlank() &&
            amountSats != null &&
            amountSats > 0L
        ) {
            withdrawalFeeQuotesLoading = true
            withdrawalFeeQuotes =
                runCatching {
                    onLoadSparkWithdrawalFeeQuotes(destinationAddress, amountSats, isMaxMode)
                }.getOrDefault(emptyList())
            withdrawalFeeQuotesLoading = false
        } else if (isLayer1ToSpark || !useCustomWithdrawalFeeSpeed) {
            withdrawalFeeQuotesLoading = false
            withdrawalFeeQuotes = emptyList()
        }
    }

    LaunchedEffect(feeEstimationState, isLayer1ToSpark) {
        if (isLayer1ToSpark && feeEstimationState !is FeeEstimationResult.Success) {
            sparkRecommendedFeeEstimates =
                runCatching {
                    onLoadSparkRecommendedFeeEstimates()
                }.getOrNull()
        }
    }

    LaunchedEffect(defaultFundingFeeRate, useCustomFundingFeeRate) {
        if (!useCustomFundingFeeRate) {
            customFundingFeeRate = defaultFundingFeeRate
        }
    }

    LaunchedEffect(sparkDepositAddress, receiveState) {
        if (sparkDepositAddress == null && receiveState !is SparkReceiveState.Loading) {
            onGenerateSparkDeposit()
        }
    }

    LaunchedEffect(layer1Address) {
        if (layer1Address.isNullOrBlank()) {
            onGenerateLayer1Address()
        }
    }

    reviewState?.let { review ->
        SparkTransferReviewDialog(
            review = review,
            useSats = useSats,
            btcPrice = btcPrice,
            fiatCurrency = fiatCurrency,
            privacyMode = privacyMode,
            isExecuting = isExecutingReview,
            onConfirm = {
                scope.launch {
                    isExecutingReview = true
                    reviewError = null
                    try {
                        when (review) {
                            is SparkTransferReview.Deposit ->
                                onExecuteLayer1ToSpark(
                                    review.destinationAddress,
                                    review.amountSats,
                                    review.fundingFeeRate,
                                    review.isMaxSend,
                                )
                            is SparkTransferReview.Withdrawal ->
                                onExecuteSparkToLayer1()
                        }
                        reviewState = null
                        amountInput = ""
                        isMaxMode = false
                        isUsdMode = false
                        onResetSparkSend()
                    } catch (e: Exception) {
                        reviewError = e.message ?: "Transfer failed"
                    } finally {
                        isExecutingReview = false
                    }
                }
            },
            onDismiss = {
                if (!isExecutingReview) {
                    reviewState = null
                    reviewError = null
                    onResetSparkSend()
                }
            },
            errorMessage = reviewError,
        )
    }

    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(modifier = Modifier.height(8.dp))
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
                Text(
                    text = stringResource(R.string.loc_f0100030),
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary,
                )
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LayerDirectionCard(
                        label = stringResource(R.string.loc_19280e4e),
                        layer = fromLayerLabel,
                        asset = fromLabel,
                        accent = fromColor,
                        modifier = Modifier.weight(1f),
                    ) {
                        selectedDirection = 1 - selectedDirection
                        isMaxMode = false
                        customDestination = ""
                        useCustomDestination = false
                        reviewError = null
                    }
                    IconButton(
                        onClick = {
                            selectedDirection = 1 - selectedDirection
                            isMaxMode = false
                            customDestination = ""
                            useCustomDestination = false
                            reviewError = null
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(SparkPurple.copy(alpha = 0.22f)),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = stringResource(R.string.loc_979d5904),
                            tint = toColor,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                    LayerDirectionCard(
                        label = stringResource(R.string.loc_4203f666),
                        layer = toLayerLabel,
                        asset = toLabel,
                        accent = toColor,
                        modifier = Modifier.weight(1f),
                    ) {
                        selectedDirection = 1 - selectedDirection
                        isMaxMode = false
                        reviewError = null
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
                                .clickable {
                                    if (amountInput.isNotEmpty() && amountSats != null) {
                                        amountInput = if (!isUsdMode) {
                                            val usdValue = (amountSats / 100_000_000.0) * btcPrice
                                            String.format(Locale.US, "%.2f", usdValue)
                                        } else if (useSats) {
                                            amountSats.toString()
                                        } else {
                                            formatBtcInput(amountSats)
                                        }
                                    }
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

                Spacer(modifier = Modifier.height(8.dp))

                val conversionText =
                    if (amountInput.isNotEmpty() && amountSats != null && amountSats > 0 && btcPrice != null && btcPrice > 0) {
                        if (privacyMode) {
                            "≈ $HIDDEN"
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
                    enabled = !isPreparingReview && !isExecutingReview,
                    onValueChange = { new ->
                        isMaxMode = false
                        reviewError = null
                        amountInput =
                            when {
                                isUsdMode -> new.takeIf { it.isEmpty() || it.matches(Regex("^\\d*\\.?\\d{0,2}$")) } ?: amountInput
                                useSats -> new.filter { it.isDigit() }
                                else -> new.takeIf { it.isEmpty() || it.matches(Regex("^\\d*\\.?\\d{0,8}$")) } ?: amountInput
                            }
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
                    suffix = if (conversionText != null) {
                        {
                            Text(
                                text = conversionText,
                                style = MaterialTheme.typography.bodyLarge,
                                color = SparkPurple,
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
                        focusedBorderColor = if (isMaxMode) SparkPurple else BorderColor,
                        unfocusedBorderColor = if (isMaxMode) SparkPurple else BorderColor,
                        cursorColor = SparkPurple,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                    ),
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.loc_c624cde4),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text =
                            if (privacyMode) {
                                HIDDEN
                            } else {
                                formatAmount(availableBalance.toULong(), useSats, includeUnit = true)
                            },
                        style = MaterialTheme.typography.bodySmall,
                        color = fromColor,
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Card(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(enabled = availableBalance > 0 && !isPreparingReview && !isExecutingReview) {
                                isMaxMode = !isMaxMode
                                amountInput =
                                    if (isMaxMode) {
                                        if (useSats) availableBalance.toString() else formatBtcInput(availableBalance)
                                    } else {
                                        ""
                                    }
                            },
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isMaxMode) SparkPurple.copy(alpha = 0.15f) else DarkSurface,
                        ),
                        border = BorderStroke(1.dp, if (isMaxMode) SparkPurple else BorderColor),
                    ) {
                        Text(
                            text = stringResource(R.string.loc_a53b6469),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isMaxMode) SparkPurple else TextSecondary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        )
                    }
                }

                if (reviewError != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = reviewError ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = ErrorRed,
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(enabled = !isPreparingReview && !isExecutingReview) {
                                showAdvancedOptions = !showAdvancedOptions
                            }
                            .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.loc_20a1d916),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Icon(
                        imageVector = if (showAdvancedOptions) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (showAdvancedOptions) "Collapse advanced options" else "Expand advanced options",
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
                        SparkSwapAdvancedToggleRow(
                            label = stringResource(R.string.loc_ee7df965),
                            checked = useCustomDestination,
                            enabled = !isPreparingReview && !isExecutingReview,
                            accentColor = toColor,
                            onCheckedChange = { enabled ->
                                useCustomDestination = enabled
                                if (!enabled) {
                                    customDestination = ""
                                }
                                reviewError = null
                            },
                        )
                        AnimatedVisibility(
                            visible = useCustomDestination,
                            enter = expandVertically(),
                            exit = shrinkVertically(),
                        ) {
                            Column(modifier = Modifier.bringIntoViewRequester(customDestinationBringIntoViewRequester)) {
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = customDestination,
                                    onValueChange = {
                                        customDestination = it.trim()
                                        reviewError = null
                                    },
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(start = 12.dp),
                                    enabled = !isPreparingReview && !isExecutingReview,
                                    placeholder = {
                                        Text(
                                            text = if (isLayer1ToSpark) sparkDepositAddressLabel else stringResource(R.string.loc_a18fd453),
                                            color = TextTertiary,
                                        )
                                    },
                                    isError = destinationValidationError != null,
                                    singleLine = true,
                                    shape = RoundedCornerShape(8.dp),
                                    colors =
                                        OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = if (destinationValidationError != null) ErrorRed else toColor,
                                            unfocusedBorderColor = if (destinationValidationError != null) ErrorRed else BorderColor,
                                            cursorColor = toColor,
                                            focusedTextColor = TextPrimary,
                                            unfocusedTextColor = TextPrimary,
                                        ),
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = destinationValidationError ?: destinationOverrideHint,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (destinationValidationError != null) ErrorRed else TextSecondary,
                                    modifier = Modifier.padding(start = 12.dp),
                                )
                            }
                        }

                        if (isLayer1ToSpark) {
                            Spacer(modifier = Modifier.height(8.dp))
                            SparkSwapAdvancedToggleRow(
                                label = customFeeRateLabel,
                                checked = useCustomFundingFeeRate,
                                enabled = !isPreparingReview && !isExecutingReview,
                                accentColor = Layer1Accent,
                                onCheckedChange = { enabled ->
                                    useCustomFundingFeeRate = enabled
                                    if (!enabled) {
                                        customFundingFeeRateInput = ""
                                        customFundingFeeRate = defaultFundingFeeRate
                                        customFundingFeeOptionName = FeeRateOption.HALF_HOUR.name
                                    }
                                    reviewError = null
                                },
                            )
                            AnimatedVisibility(
                                visible = useCustomFundingFeeRate,
                                enter = expandVertically(),
                                exit = shrinkVertically(),
                            ) {
                                Column(
                                    modifier =
                                        Modifier
                                            .bringIntoViewRequester(customFeeBringIntoViewRequester)
                                            .fillMaxWidth()
                                            .padding(start = 12.dp, top = 8.dp),
                                ) {
                                    FeeRateSection(
                                        feeEstimationState = effectiveFeeEstimationState,
                                        currentFeeRate = customFundingFeeRate,
                                        minFeeRate = minFeeRate,
                                        onFeeRateChange = {
                                            customFundingFeeRate = it
                                            reviewError = null
                                        },
                                        selectedOption = customFundingFeeOption,
                                        onSelectedOptionChange = {
                                            customFundingFeeOptionName = it.name
                                        },
                                        customFeeInput = customFundingFeeRateInput.takeIf { it.isNotBlank() },
                                        onCustomFeeInputChange = {
                                            customFundingFeeRateInput = it.orEmpty()
                                        },
                                        onRefreshFees = onRefreshBitcoinFees,
                                        enabled = !isPreparingReview && !isExecutingReview,
                                    )
                                    fundingFeeRateValidationError?.let { error ->
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = error,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = ErrorRed,
                                        )
                                    }
                                }
                            }
                        } else {
                            Spacer(modifier = Modifier.height(8.dp))
                            SparkSwapAdvancedToggleRow(
                                label = customFeeRateLabel,
                                checked = useCustomWithdrawalFeeSpeed,
                                enabled = !isPreparingReview && !isExecutingReview,
                                accentColor = SparkPurple,
                                onCheckedChange = { enabled ->
                                    useCustomWithdrawalFeeSpeed = enabled
                                    if (!enabled) {
                                        withdrawalFeeSpeed = SparkOnchainFeeSpeed.FAST
                                        withdrawalFeeQuotes = emptyList()
                                    }
                                    reviewError = null
                                },
                            )
                            AnimatedVisibility(
                                visible = useCustomWithdrawalFeeSpeed,
                                enter = expandVertically(),
                                exit = shrinkVertically(),
                            ) {
                                SparkTransferOnchainFeeSpeedSection(
                                    selectedSpeed = withdrawalFeeSpeed,
                                    feeQuotes = withdrawalFeeQuotes,
                                    isLoading = withdrawalFeeQuotesLoading,
                                    privacyMode = privacyMode,
                                    onSpeedSelected = { withdrawalFeeSpeed = it },
                                    enabled = !isPreparingReview && !isExecutingReview,
                                    modifier =
                                        Modifier
                                            .bringIntoViewRequester(customFeeBringIntoViewRequester)
                                            .padding(start = 12.dp, top = 8.dp),
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        if (amountSats == null || amountSats <= 0L) {
                            reviewError = "Enter an amount"
                            return@Button
                        }
                        if (destinationValidationError != null) {
                            reviewError = destinationValidationError
                            return@Button
                        }
                        if (fundingFeeRateValidationError != null) {
                            reviewError = fundingFeeRateValidationError
                            return@Button
                        }
                        scope.launch {
                            isPreparingReview = true
                            reviewError = null
                            try {
                                if (isLayer1ToSpark) {
                                    val address = destinationAddress ?: throw IllegalStateException("Spark deposit address not ready")
                                    val dryRun =
                                        onPreviewLayer1ToSpark(
                                            address,
                                            amountSats,
                                            fundingFeeRate,
                                            isMaxMode,
                                        ) ?: throw IllegalStateException("Unable to prepare Layer 1 funding")
                                    if (dryRun.isError) {
                                        throw IllegalStateException(dryRun.error ?: "Unable to prepare Layer 1 funding")
                                    }
                                    reviewState = SparkTransferReview.Deposit(
                                        amountSats = dryRun.recipientAmountSats,
                                        destinationAddress = address,
                                        dryRun = dryRun,
                                        isMaxSend = isMaxMode,
                                        fundingFeeRate = fundingFeeRate,
                                    )
                                } else {
                                    val address = destinationAddress ?: throw IllegalStateException("Layer 1 address not ready")
                                    val preview = onPreviewSparkToLayer1(address, amountSats, withdrawalFeeSpeed, isMaxMode)
                                    reviewState = SparkTransferReview.Withdrawal(
                                        amountSats = preview.amountSats ?: amountSats,
                                        destinationAddress = address,
                                        preview = preview,
                                    )
                                }
                            } catch (e: Exception) {
                                reviewError = e.message ?: unableToPrepareSwapLabel
                            } finally {
                                isPreparingReview = false
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    enabled = canPrepareReview,
                    shape = RoundedCornerShape(8.dp),
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = SparkPurple,
                            disabledContainerColor = SparkPurple.copy(alpha = 0.3f),
                        ),
                ) {
                    if (isPreparingReview) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = TextPrimary,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text(stringResource(R.string.loc_9a0b9f8e), style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun SparkSwapAdvancedToggleRow(
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

@Composable
private fun SparkTransferOnchainFeeSpeedSection(
    selectedSpeed: SparkOnchainFeeSpeed,
    feeQuotes: List<SparkOnchainFeeQuote>,
    isLoading: Boolean,
    privacyMode: Boolean,
    onSpeedSelected: (SparkOnchainFeeSpeed) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.loc_943c89b7),
            style = MaterialTheme.typography.labelLarge,
            color = TextSecondary,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            SparkTransferFeeTargetButton(
                label = "~1 block",
                feeSats = feeQuotes.firstOrNull { it.speed == SparkOnchainFeeSpeed.FAST }?.feeSats,
                isSelected = selectedSpeed == SparkOnchainFeeSpeed.FAST,
                onClick = { onSpeedSelected(SparkOnchainFeeSpeed.FAST) },
                enabled = enabled,
                isLoading = isLoading,
                privacyMode = privacyMode,
                modifier = Modifier.weight(1f),
            )
            SparkTransferFeeTargetButton(
                label = "~3 blocks",
                feeSats = feeQuotes.firstOrNull { it.speed == SparkOnchainFeeSpeed.MEDIUM }?.feeSats,
                isSelected = selectedSpeed == SparkOnchainFeeSpeed.MEDIUM,
                onClick = { onSpeedSelected(SparkOnchainFeeSpeed.MEDIUM) },
                enabled = enabled,
                isLoading = isLoading,
                privacyMode = privacyMode,
                modifier = Modifier.weight(1f),
            )
            SparkTransferFeeTargetButton(
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
private fun SparkTransferFeeTargetButton(
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
                        if (privacyMode) HIDDEN else String.format(Locale.US, "%,d", it)
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

private fun formatBtcInput(amountSats: Long): String =
    String.format(Locale.US, "%.8f", amountSats / 100_000_000.0)
        .trimEnd('0')
        .trimEnd('.')

@Composable
private fun LayerDirectionCard(
    label: String,
    layer: String,
    asset: String,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Card(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text = label, style = MaterialTheme.typography.bodyMedium, color = TextTertiary)
            Spacer(modifier = Modifier.height(6.dp))
            Text(text = layer, style = MaterialTheme.typography.bodyLarge, color = accent)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = asset, style = MaterialTheme.typography.bodySmall, color = accent.copy(alpha = 0.7f))
        }
    }
}

@Composable
private fun SparkTransferReviewDialog(
    review: SparkTransferReview,
    useSats: Boolean,
    btcPrice: Double?,
    fiatCurrency: String,
    privacyMode: Boolean,
    isExecuting: Boolean,
    errorMessage: String?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val isDeposit = review is SparkTransferReview.Deposit
    val hidden = HIDDEN
    val sendAccent = if (isDeposit) Layer1Accent else SparkPurple
    val receiveAccent = if (isDeposit) SparkPurple else Layer1Accent
    val sendLayerLabel = if (isDeposit) stringResource(R.string.loc_b67a01a5) else stringResource(R.string.loc_2f73501f)
    val receiveLayerLabel = if (isDeposit) stringResource(R.string.loc_2f73501f) else stringResource(R.string.loc_b67a01a5)
    val sparkDepositAddressLabel = stringResource(R.string.spark_transfer_spark_deposit_address)
    val destinationAddressLabel = stringResource(R.string.loc_3083a5d1)
    val sendingSwapLabel = stringResource(R.string.loc_94bea00f)
    val confirmSwapLabel = stringResource(R.string.loc_7a4eb12d)
    val collapseFeesLabel = stringResource(R.string.loc_e607c9c3)
    val expandFeesLabel = stringResource(R.string.loc_cd1ee95d)
    val bitcoinFundingFeeLabel = stringResource(R.string.loc_85a9e0cb)
    val bitcoinWithdrawalFeeLabel = stringResource(R.string.spark_transfer_bitcoin_withdrawal_fee)
    val changeLabel = stringResource(R.string.loc_47fbfb16)
    val totalFeesEstimateLabel = stringResource(R.string.loc_7d9effcf)
    val orderId = review.destinationAddress
    val totalFeeSats =
        when (review) {
            is SparkTransferReview.Deposit -> review.dryRun.feeSats
            is SparkTransferReview.Withdrawal -> review.preview.feeSats ?: 0L
        }
    var addressesExpanded by rememberSaveable(orderId) { mutableStateOf(false) }
    var feesExpanded by rememberSaveable(orderId) { mutableStateOf(false) }
    val addressesBringIntoViewRequester = remember(orderId) { BringIntoViewRequester() }
    val feesBringIntoViewRequester = remember(orderId) { BringIntoViewRequester() }
    val context = LocalContext.current

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
        containerColor = DarkCard,
        contentPadding = PaddingValues(20.dp),
        actions = {
            Button(
                onClick = onConfirm,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                enabled = !isExecuting,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SparkPurple),
            ) {
                if (isExecuting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = TextPrimary,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(if (isExecuting) sendingSwapLabel else confirmSwapLabel, color = TextPrimary)
            }
            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = ErrorRed,
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
                modifier = Modifier
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
            RouteBadgeSpark(text = "SPARK", accentColor = SparkPurple)
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
                    text = if (privacyMode) hidden else formatAmount(review.amountSats.toULong(), useSats, includeUnit = true),
                    style = MaterialTheme.typography.headlineSmall,
                    color = sendAccent,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
                reviewUsdText(review.amountSats, btcPrice, fiatCurrency, privacyMode)?.let { usdText ->
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
                        text = stringResource(R.string.loc_4374aaee),
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
                    text = if (privacyMode) hidden else formatAmount(review.amountSats.toULong(), useSats, includeUnit = true),
                    style = MaterialTheme.typography.headlineSmall,
                    color = receiveAccent,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
                reviewUsdText(review.amountSats, btcPrice, fiatCurrency, privacyMode)?.let { usdText ->
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = usdText,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextTertiary,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
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
                        tint = SparkPurple,
                    )
                }
                AnimatedVisibility(addressesExpanded) {
                    Column(
                        modifier = Modifier
                            .bringIntoViewRequester(addressesBringIntoViewRequester)
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                    ) {
                        AddressRowSpark(
                            label = if (isDeposit) sparkDepositAddressLabel else destinationAddressLabel,
                            value = review.destinationAddress,
                            context = context,
                        )
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
                        text = stringResource(R.string.loc_00a16e52),
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
                                    text = if (privacyMode) hidden else formatAmount(totalFeeSats.toULong(), useSats, includeUnit = true),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Medium,
                                )
                                reviewUsdText(totalFeeSats, btcPrice, fiatCurrency, privacyMode)?.let {
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
                            contentDescription = if (feesExpanded) collapseFeesLabel else expandFeesLabel,
                            tint = SparkPurple,
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
                        when (review) {
                            is SparkTransferReview.Deposit -> {
                                FeeRowSpark(
                                    label = bitcoinFundingFeeLabel,
                                    value = if (privacyMode) hidden else formatAmount(review.dryRun.feeSats.toULong(), useSats, includeUnit = true),
                                    subtext = "≈ ${String.format(Locale.US, "%.1f", review.dryRun.effectiveFeeRate)} sat/vB",
                                    valueSubtext = reviewUsdText(review.dryRun.feeSats, btcPrice, fiatCurrency, privacyMode),
                                )
                                if (review.dryRun.hasChange) {
                                    FeeRowSpark(
                                        label = changeLabel,
                                        value = if (privacyMode) hidden else formatAmount(review.dryRun.changeSats.toULong(), useSats, includeUnit = true),
                                        valueSubtext = reviewUsdText(review.dryRun.changeSats, btcPrice, fiatCurrency, privacyMode),
                                    )
                                }
                            }
                            is SparkTransferReview.Withdrawal -> {
                                FeeRowSpark(
                                    label = bitcoinWithdrawalFeeLabel,
                                    value = if (privacyMode) hidden else formatAmount((review.preview.feeSats ?: 0L).toULong(), useSats, includeUnit = true),
                                    subtext = sparkWithdrawalMethodText(review.preview.method),
                                    valueSubtext = reviewUsdText(review.preview.feeSats ?: 0L, btcPrice, fiatCurrency, privacyMode),
                                )
                            }
                        }
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 8.dp),
                            color = BorderColor.copy(alpha = 0.3f),
                        )
                        ReviewRowSpark(
                            label = totalFeesEstimateLabel,
                            value = if (privacyMode) hidden else formatAmount(totalFeeSats.toULong(), useSats, includeUnit = true),
                            valueSubtext = reviewUsdText(totalFeeSats, btcPrice, fiatCurrency, privacyMode),
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
    }
}

@Composable
private fun ReviewRowSpark(
    label: String,
    value: String,
    valueSubtext: String? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Text(label, color = TextSecondary, modifier = Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = value,
                color = TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
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

@Composable
private fun RouteBadgeSpark(
    text: String,
    accentColor: Color,
) {
    Box(
        modifier = Modifier
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

@Composable
private fun AddressRowSpark(
    label: String,
    value: String,
    context: android.content.Context,
) {
    val copyLabelContentDescription = stringResource(R.string.common_copy_with_label, label)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = TextSecondary,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary,
            )
        }
        Spacer(modifier = Modifier.width(6.dp))
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(DarkSurfaceVariant)
                .clickable {
                    SecureClipboard.copyAndScheduleClear(
                        context = context,
                        text = value,
                    )
                },
        ) {
            Icon(
                imageVector = Icons.Default.ContentCopy,
                contentDescription = copyLabelContentDescription,
                tint = TextSecondary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun FeeRowSpark(
    label: String,
    value: String,
    subtext: String? = null,
    valueSubtext: String? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = TextSecondary,
            )
            subtext?.let {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextTertiary,
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                color = TextPrimary,
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

private fun reviewUsdText(
    amountSats: Long,
    btcPrice: Double?,
    fiatCurrency: String,
    privacyMode: Boolean,
): String? {
    if (privacyMode || btcPrice == null || btcPrice <= 0.0) return null
    return formatFiat((amountSats / 100_000_000.0) * btcPrice, fiatCurrency)
}

private fun sparkWithdrawalMethodText(method: String): String =
    when {
        method.contains("Bitcoin", ignoreCase = true) -> "On-chain Bitcoin payout"
        method.contains("Bolt", ignoreCase = true) || method.contains("Lightning", ignoreCase = true) -> "Lightning payout"
        method.contains("Spark", ignoreCase = true) -> "Spark transfer"
        else -> "Spark withdrawal"
    }
