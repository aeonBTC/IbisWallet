package github.aeonbtc.ibiswallet.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import github.aeonbtc.ibiswallet.data.model.FeeEstimateSource
import github.aeonbtc.ibiswallet.data.model.FeeEstimationResult
import github.aeonbtc.ibiswallet.ui.theme.BitcoinOrange
import github.aeonbtc.ibiswallet.ui.theme.BorderColor
import github.aeonbtc.ibiswallet.ui.theme.DarkSurface
import github.aeonbtc.ibiswallet.ui.theme.DarkSurfaceVariant
import github.aeonbtc.ibiswallet.ui.theme.TextSecondary
import github.aeonbtc.ibiswallet.ui.theme.WarningYellow
import java.util.Locale
import androidx.compose.material3.Text
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import github.aeonbtc.ibiswallet.R

/** Safety cap for manual fee rate entry (sat/vB). Users can still type higher values
 *  but a warning is shown and the value is clamped when applied. */
const val MAX_FEE_RATE_SAT_VB = 10_000.0

enum class FeeRateOption {
    FASTEST,
    HALF_HOUR,
    HOUR,
    CUSTOM,
}

fun formatFeeRate(rate: Double): String {
    val formatted = String.format(Locale.US, "%.2f", rate)
    return formatted.trimEnd('0').trimEnd('.')
}

// Float overload removed — all fee rates are now Double throughout the pipeline

@Composable
fun FeeRateSection(
    feeEstimationState: FeeEstimationResult,
    currentFeeRate: Double,
    minFeeRate: Double,
    onFeeRateChange: (Double) -> Unit,
    selectedOption: FeeRateOption? = null,
    onSelectedOptionChange: ((FeeRateOption) -> Unit)? = null,
    customFeeInput: String? = null,
    onCustomFeeInputChange: ((String?) -> Unit)? = null,
    onRawCustomFeeRateChange: ((Double?) -> Unit)? = null,
    onRefreshFees: () -> Unit,
    enabled: Boolean,
    estimatedFeeSats: Long? = null,
    estimatedVBytes: Double? = null,
    useSats: Boolean = true,
    btcPrice: Double? = null,
    privacyMode: Boolean = false,
    formatAmount: ((ULong, Boolean) -> String)? = null,
    formatUsd: ((Double) -> String)? = null,
    formatVBytesDisplay: ((Double) -> String)? = null,
    hiddenAmount: String? = null,
) {
    var resolvedSelectedOption by remember { mutableStateOf(selectedOption ?: FeeRateOption.HALF_HOUR) }
    var resolvedCustomFeeInput by remember { mutableStateOf(customFeeInput) }
    val customFocusRequester = remember { FocusRequester() }

    LaunchedEffect(selectedOption) {
        selectedOption?.let { resolvedSelectedOption = it }
    }

    LaunchedEffect(customFeeInput) {
        if (onCustomFeeInputChange != null) {
            resolvedCustomFeeInput = customFeeInput
        }
    }

    fun updateSelectedOption(option: FeeRateOption) {
        resolvedSelectedOption = option
        onSelectedOptionChange?.invoke(option)
    }

    fun updateCustomFeeInput(input: String?) {
        resolvedCustomFeeInput = input
        onCustomFeeInputChange?.invoke(input)
    }

    val estimates = (feeEstimationState as? FeeEstimationResult.Success)?.estimates

    LaunchedEffect(resolvedSelectedOption) {
        if (resolvedSelectedOption == FeeRateOption.CUSTOM) {
            customFocusRequester.requestFocus()
        }
    }

    LaunchedEffect(estimates, resolvedSelectedOption) {
        if (estimates != null && resolvedSelectedOption != FeeRateOption.CUSTOM) {
            onRawCustomFeeRateChange?.invoke(null)
            val newRate =
                when (resolvedSelectedOption) {
                    FeeRateOption.FASTEST -> estimates.fastestFee
                    FeeRateOption.HALF_HOUR -> estimates.halfHourFee
                    FeeRateOption.HOUR -> estimates.hourFee
                    FeeRateOption.CUSTOM -> return@LaunchedEffect
                }
            onFeeRateChange(newRate.coerceAtLeast(minFeeRate))
        }
    }

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

            if (feeEstimationState !is FeeEstimationResult.Disabled) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier =
                        Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(DarkSurfaceVariant)
                            .clickable(enabled = enabled && feeEstimationState !is FeeEstimationResult.Loading) {
                                onRefreshFees()
                            },
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh fees",
                        tint =
                            if (feeEstimationState is FeeEstimationResult.Loading) {
                                TextSecondary.copy(alpha = 0.5f)
                            } else {
                                BitcoinOrange
                            },
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }

        // Show estimated fee when amount is entered (only if callbacks are provided)
        if (estimatedFeeSats != null && estimatedVBytes != null &&
            formatAmount != null && formatVBytesDisplay != null
        ) {
            val vBytesDisplay = formatVBytesDisplay(estimatedVBytes)
            val usdFee =
                if (btcPrice != null && btcPrice > 0 && !privacyMode && formatUsd != null) {
                    " · ${formatUsd((estimatedFeeSats.toDouble() / 100_000_000.0) * btcPrice)}"
                } else {
                    ""
                }
            Text(
                text =
                    if (privacyMode && hiddenAmount != null) {
                        "Est. fee: $hiddenAmount ($vBytesDisplay vB)"
                    } else {
                        "Est. fee: ${formatAmount(
                            estimatedFeeSats.toULong(),
                            useSats,
                        )} ${if (useSats) "sats" else "BTC"}$usdFee ($vBytesDisplay vB)"
                    },
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        if (feeEstimationState is FeeEstimationResult.Disabled) {
            if (resolvedCustomFeeInput == null) {
                updateCustomFeeInput(formatFeeRate(currentFeeRate))
            }
            ManualFeeInput(
                value = resolvedCustomFeeInput ?: "",
                onValueChange = { input ->
                    updateCustomFeeInput(input)
                    val parsedRate = input.toDoubleOrNull()
                    onRawCustomFeeRateChange?.invoke(parsedRate)
                    parsedRate?.let {
                        onFeeRateChange(it.coerceIn(minFeeRate, MAX_FEE_RATE_SAT_VB))
                    }
                },
                enabled = enabled,
                minFeeRate = minFeeRate,
            )
        } else {
            val isLoading = feeEstimationState is FeeEstimationResult.Loading
            val isElectrum = estimates?.source == FeeEstimateSource.ELECTRUM_SERVER
            val fastCount = if (isElectrum) 2 else 1
            val medCount = if (isElectrum) 6 else 3
            val slowCount = if (isElectrum) 12 else 6
            val fastLabel = pluralStringResource(R.plurals.fee_estimate_approx_blocks, fastCount, fastCount)
            val medLabel = pluralStringResource(R.plurals.fee_estimate_approx_blocks, medCount, medCount)
            val slowLabel = pluralStringResource(R.plurals.fee_estimate_approx_blocks, slowCount, slowCount)

            val errorState = feeEstimationState as? FeeEstimationResult.Error
            if (errorState != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors =
                        CardDefaults.cardColors(
                            containerColor = WarningYellow.copy(alpha = 0.1f),
                        ),
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.loc_82c7cb6e),
                            style = MaterialTheme.typography.bodySmall,
                            color = WarningYellow,
                        )
                        Text(
                            text = errorState.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary.copy(alpha = 0.7f),
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                FeeTargetButton(
                    label = fastLabel,
                    feeRate = estimates?.fastestFee,
                    isSelected = resolvedSelectedOption == FeeRateOption.FASTEST,
                    onClick = {
                        estimates?.let {
                            updateSelectedOption(FeeRateOption.FASTEST)
                            updateCustomFeeInput(null)
                            onRawCustomFeeRateChange?.invoke(null)
                            onFeeRateChange(it.fastestFee.coerceAtLeast(minFeeRate))
                        }
                    },
                    enabled = enabled,
                    isLoading = isLoading,
                    modifier = Modifier.weight(1f),
                )

                FeeTargetButton(
                    label = medLabel,
                    feeRate = estimates?.halfHourFee,
                    isSelected = resolvedSelectedOption == FeeRateOption.HALF_HOUR,
                    onClick = {
                        estimates?.let {
                            updateSelectedOption(FeeRateOption.HALF_HOUR)
                            updateCustomFeeInput(null)
                            onRawCustomFeeRateChange?.invoke(null)
                            onFeeRateChange(it.halfHourFee.coerceAtLeast(minFeeRate))
                        }
                    },
                    enabled = enabled,
                    isLoading = isLoading,
                    modifier = Modifier.weight(1f),
                )

                FeeTargetButton(
                    label = slowLabel,
                    feeRate = estimates?.hourFee,
                    isSelected = resolvedSelectedOption == FeeRateOption.HOUR,
                    onClick = {
                        estimates?.let {
                            updateSelectedOption(FeeRateOption.HOUR)
                            updateCustomFeeInput(null)
                            onRawCustomFeeRateChange?.invoke(null)
                            onFeeRateChange(it.hourFee.coerceAtLeast(minFeeRate))
                        }
                    },
                    enabled = enabled,
                    isLoading = isLoading,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(modifier = Modifier.height(4.dp))

            if (resolvedSelectedOption == FeeRateOption.CUSTOM) {
                if (resolvedCustomFeeInput == null) {
                    updateCustomFeeInput(formatFeeRate(currentFeeRate))
                }
                ManualFeeInput(
                    value = resolvedCustomFeeInput ?: "",
                    onValueChange = { input ->
                        updateCustomFeeInput(input)
                        val parsedRate = input.toDoubleOrNull()
                        onRawCustomFeeRateChange?.invoke(parsedRate)
                        parsedRate?.let {
                            onFeeRateChange(it.coerceIn(minFeeRate, MAX_FEE_RATE_SAT_VB))
                        }
                    },
                    enabled = enabled,
                    minFeeRate = minFeeRate,
                    modifier = Modifier.focusRequester(customFocusRequester),
                )
            } else {
                Card(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(enabled = enabled && !isLoading) {
                                updateSelectedOption(FeeRateOption.CUSTOM)
                                updateCustomFeeInput(formatFeeRate(currentFeeRate))
                                onRawCustomFeeRateChange?.invoke(currentFeeRate)
                            },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    border = BorderStroke(1.dp, BorderColor),
                ) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.loc_f22813ad),
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun FeeTargetButton(
    label: String,
    feeRate: Double?,
    isSelected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
) {
    val backgroundColor = if (isSelected) BitcoinOrange.copy(alpha = 0.15f) else DarkSurface
    val borderColor = if (isSelected) BitcoinOrange else BorderColor
    val textColor = if (isSelected) BitcoinOrange else TextSecondary

    Card(
        modifier =
            modifier
                .clip(RoundedCornerShape(12.dp))
                .clickable(enabled = enabled && !isLoading, onClick = onClick),
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
                    color = BitcoinOrange,
                    strokeWidth = 2.dp,
                )
            } else {
                Text(
                    text = feeRate?.let { formatFeeRate(it) } ?: "—",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = textColor,
                    textAlign = TextAlign.Center,
                )
            }
            Text(
                text = stringResource(R.string.loc_aedd48eb),
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun ManualFeeInput(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    minFeeRate: Double = 1.0,
) {
    val parsedRate = value.toDoubleOrNull()
    val isOverCap = parsedRate != null && parsedRate > MAX_FEE_RATE_SAT_VB
    val isBelowMin = parsedRate != null && parsedRate > 0.0 && parsedRate < minFeeRate

    OutlinedTextField(
        value = value,
        onValueChange = { input ->
            if (input.isEmpty()) {
                onValueChange(input)
                return@OutlinedTextField
            }

            val isValidFormat = input.matches(Regex("^\\d*\\.?\\d{0,2}$"))
            val hasInvalidLeadingZeros =
                input.length > 1 &&
                    input.startsWith("0") &&
                    !input.startsWith("0.")

            if (isValidFormat && !hasInvalidLeadingZeros) {
                onValueChange(input)
            }
        },
        modifier = modifier.fillMaxWidth(),
        suffix = {
            Text(
                text = stringResource(R.string.loc_aedd48eb),
                color = TextSecondary.copy(alpha = 0.7f),
            )
        },
        placeholder = { Text(formatFeeRate(minFeeRate), color = TextSecondary.copy(alpha = 0.5f)) },
        isError = isOverCap || isBelowMin,
        supportingText = {
            if (isOverCap) {
                Text(
                    text = "Maximum: ${formatFeeRate(MAX_FEE_RATE_SAT_VB)} sat/vB",
                    style = MaterialTheme.typography.bodySmall,
                    color = WarningYellow,
                )
            } else if (isBelowMin) {
                Text(
                    text = "Fee rate is below the minimum (${formatFeeRate(minFeeRate)} sat/vB)",
                    style = MaterialTheme.typography.bodySmall,
                    color = WarningYellow,
                )
            } else {
                Text(
                    text = "Minimum: ${formatFeeRate(minFeeRate)} sat/vB",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary.copy(alpha = 0.7f),
                )
            }
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        colors =
            OutlinedTextFieldDefaults.colors(
                focusedBorderColor = if (isOverCap || isBelowMin) WarningYellow else BitcoinOrange,
                unfocusedBorderColor = if (isOverCap || isBelowMin) WarningYellow else BorderColor,
                focusedLabelColor = if (isOverCap || isBelowMin) WarningYellow else BitcoinOrange,
                unfocusedLabelColor = TextSecondary,
                focusedTextColor = MaterialTheme.colorScheme.onBackground,
                unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                cursorColor = BitcoinOrange,
            ),
    )
}
