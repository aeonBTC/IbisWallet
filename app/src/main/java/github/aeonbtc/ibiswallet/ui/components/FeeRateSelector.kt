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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
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

/** Safety cap for manual fee rate entry (sat/vB). Users can still type higher values
 *  but a warning is shown and the value is clamped when applied. */
const val MAX_FEE_RATE_SAT_VB = 10_000f

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

fun formatFeeRate(rate: Float): String = formatFeeRate(rate.toDouble())

@Composable
fun FeeRateSection(
    feeEstimationState: FeeEstimationResult,
    currentFeeRate: Float,
    minFeeRate: Double,
    onFeeRateChange: (Float) -> Unit,
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
    var selectedOption by remember { mutableStateOf(FeeRateOption.HALF_HOUR) }
    var customFeeInput by remember { mutableStateOf<String?>(null) }

    val minFeeFloat = minFeeRate.toFloat()

    val estimates = (feeEstimationState as? FeeEstimationResult.Success)?.estimates

    LaunchedEffect(estimates) {
        if (estimates != null && selectedOption != FeeRateOption.CUSTOM) {
            val newRate =
                when (selectedOption) {
                    FeeRateOption.FASTEST -> estimates.fastestFee.toFloat()
                    FeeRateOption.HALF_HOUR -> estimates.halfHourFee.toFloat()
                    FeeRateOption.HOUR -> estimates.hourFee.toFloat()
                    FeeRateOption.CUSTOM -> currentFeeRate
                }
            onFeeRateChange(newRate.coerceAtLeast(minFeeFloat))
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Fee Rate",
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

        Spacer(modifier = Modifier.height(8.dp))

        if (feeEstimationState is FeeEstimationResult.Disabled) {
            if (customFeeInput == null) {
                customFeeInput = formatFeeRate(currentFeeRate)
            }
            ManualFeeInput(
                value = customFeeInput ?: "",
                onValueChange = { input ->
                    customFeeInput = input
                    input.toFloatOrNull()?.let {
                        onFeeRateChange(it.coerceIn(minFeeFloat, MAX_FEE_RATE_SAT_VB))
                    }
                },
                enabled = enabled,
                minFeeRate = minFeeRate,
            )
        } else {
            val isLoading = feeEstimationState is FeeEstimationResult.Loading
            val isError = feeEstimationState is FeeEstimationResult.Error
            val isElectrum = estimates?.source == FeeEstimateSource.ELECTRUM_SERVER
            val fastLabel = if (isElectrum) "~2 blocks" else "~1 block"
            val medLabel = if (isElectrum) "~6 blocks" else "~3 blocks"
            val slowLabel = if (isElectrum) "~12 blocks" else "~6 blocks"

            if (isError) {
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
                            text = "Failed to load fee estimates",
                            style = MaterialTheme.typography.bodySmall,
                            color = WarningYellow,
                        )
                        Text(
                            text = (feeEstimationState as FeeEstimationResult.Error).message,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary.copy(alpha = 0.7f),
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FeeTargetButton(
                    label = fastLabel,
                    feeRate = estimates?.fastestFee,
                    isSelected = selectedOption == FeeRateOption.FASTEST,
                    onClick = {
                        estimates?.let {
                            selectedOption = FeeRateOption.FASTEST
                            customFeeInput = null
                            onFeeRateChange(it.fastestFee.toFloat().coerceAtLeast(minFeeFloat))
                        }
                    },
                    enabled = enabled,
                    isLoading = isLoading,
                    modifier = Modifier.weight(1f),
                )

                FeeTargetButton(
                    label = medLabel,
                    feeRate = estimates?.halfHourFee,
                    isSelected = selectedOption == FeeRateOption.HALF_HOUR,
                    onClick = {
                        estimates?.let {
                            selectedOption = FeeRateOption.HALF_HOUR
                            customFeeInput = null
                            onFeeRateChange(it.halfHourFee.toFloat().coerceAtLeast(minFeeFloat))
                        }
                    },
                    enabled = enabled,
                    isLoading = isLoading,
                    modifier = Modifier.weight(1f),
                )

                FeeTargetButton(
                    label = slowLabel,
                    feeRate = estimates?.hourFee,
                    isSelected = selectedOption == FeeRateOption.HOUR,
                    onClick = {
                        estimates?.let {
                            selectedOption = FeeRateOption.HOUR
                            customFeeInput = null
                            onFeeRateChange(it.hourFee.toFloat().coerceAtLeast(minFeeFloat))
                        }
                    },
                    enabled = enabled,
                    isLoading = isLoading,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(modifier = Modifier.height(6.dp))

            if (selectedOption == FeeRateOption.CUSTOM) {
                if (customFeeInput == null) {
                    customFeeInput = formatFeeRate(currentFeeRate)
                }
                ManualFeeInput(
                    value = customFeeInput ?: "",
                    onValueChange = { input ->
                        customFeeInput = input
                        input.toFloatOrNull()?.let {
                            onFeeRateChange(it.coerceIn(minFeeFloat, MAX_FEE_RATE_SAT_VB))
                        }
                    },
                    enabled = enabled,
                    minFeeRate = minFeeRate,
                )
            } else {
                Card(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(enabled = enabled && !isLoading) {
                                selectedOption = FeeRateOption.CUSTOM
                                customFeeInput = formatFeeRate(currentFeeRate)
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
                            text = "Custom",
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
    isLoading: Boolean = false,
    modifier: Modifier = Modifier,
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
                    .padding(vertical = 8.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = if (isSelected) MaterialTheme.colorScheme.onBackground else TextSecondary,
            )
            Spacer(modifier = Modifier.height(1.dp))
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
                )
            }
            Text(
                text = "sat/vB",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
            )
        }
    }
}

@Composable
private fun ManualFeeInput(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    minFeeRate: Double = 1.0,
) {
    val parsedRate = value.toFloatOrNull()
    val isOverCap = parsedRate != null && parsedRate > MAX_FEE_RATE_SAT_VB

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
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Fee rate (sat/vB)") },
        placeholder = { Text(formatFeeRate(minFeeRate), color = TextSecondary.copy(alpha = 0.5f)) },
        isError = isOverCap,
        supportingText = {
            if (isOverCap) {
                Text(
                    text = "Exceeds ${formatFeeRate(MAX_FEE_RATE_SAT_VB)} sat/vB safety cap — will be clamped",
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
                focusedBorderColor = if (isOverCap) WarningYellow else BitcoinOrange,
                unfocusedBorderColor = if (isOverCap) WarningYellow else BorderColor,
                focusedLabelColor = if (isOverCap) WarningYellow else BitcoinOrange,
                unfocusedLabelColor = TextSecondary,
                focusedTextColor = MaterialTheme.colorScheme.onBackground,
                unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                cursorColor = BitcoinOrange,
            ),
    )
}
