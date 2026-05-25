package github.aeonbtc.ibiswallet.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import github.aeonbtc.ibiswallet.ui.theme.BorderColor
import github.aeonbtc.ibiswallet.ui.theme.DarkSurface
import github.aeonbtc.ibiswallet.ui.theme.TextSecondary
import androidx.compose.material3.Text
import androidx.compose.ui.res.stringResource
import github.aeonbtc.ibiswallet.R

@Composable
fun AmountLabel(
    useSats: Boolean,
    isUsdMode: Boolean = false,
    fiatCurrency: String = "",
    showDenomination: Boolean = true,
    onToggleDenomination: () -> Unit = {},
    style: TextStyle = MaterialTheme.typography.labelLarge,
    color: Color = TextSecondary,
) {
    if (isUsdMode || !showDenomination) {
        Text(
            text =
                if (isUsdMode) {
                    "${stringResource(R.string.loc_890d7574)} ($fiatCurrency)"
                } else {
                    stringResource(R.string.loc_890d7574)
                },
            style = style,
            color = color,
        )
    } else {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = stringResource(R.string.loc_890d7574), style = style, color = color)
            Spacer(modifier = Modifier.width(4.dp))
            Card(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onToggleDenomination),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                border = BorderStroke(1.dp, BorderColor),
            ) {
                Text(
                    text = if (useSats) "Sats" else "BTC",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                )
            }
        }
    }
}

@Composable
fun AvailableBalanceMaxRow(
    amountText: String,
    accentColor: Color,
    isMaxMode: Boolean,
    maxEnabled: Boolean,
    onMaxClick: () -> Unit,
    modifier: Modifier = Modifier,
    fiatText: String? = null,
    fadeWhenDisabled: Boolean = false,
) {
    val availableLabel = stringResource(R.string.loc_277e2626)
    val maxLabel = stringResource(R.string.loc_a53b6469)
    val textStyle = MaterialTheme.typography.bodySmall
    val maxTextStyle = MaterialTheme.typography.labelMedium
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val labelWidthPx = textMeasurer.measure(availableLabel, style = textStyle).size.width
        val labelSpacerPx = with(density) { 8.dp.roundToPx() }
        val amountWidthPx = textMeasurer.measure(amountText, style = textStyle).size.width
        val fiatWidthPx = fiatText?.let { textMeasurer.measure(it, style = textStyle).size.width } ?: 0
        val maxButtonWidthPx =
            textMeasurer.measure(maxLabel, style = maxTextStyle).size.width +
                with(density) { 16.dp.roundToPx() }
        val maxGapPx = with(density) { 8.dp.roundToPx() }
        val balanceRowWidthPx = labelWidthPx + labelSpacerPx + amountWidthPx + fiatWidthPx
        val maxWidthPx = with(density) { maxWidth.roundToPx() }
        val stackFiat = fiatText != null && balanceRowWidthPx > maxWidthPx - maxButtonWidthPx - maxGapPx
        val fiatIndent = with(density) { labelWidthPx.toDp() } + 8.dp
        val maxContainerColor =
            when {
                isMaxMode -> accentColor.copy(alpha = 0.15f)
                maxEnabled -> DarkSurface
                fadeWhenDisabled -> DarkSurface.copy(alpha = 0.6f)
                else -> DarkSurface
            }
        val maxBorderColor =
            when {
                isMaxMode -> accentColor
                maxEnabled -> BorderColor
                fadeWhenDisabled -> BorderColor.copy(alpha = 0.5f)
                else -> BorderColor
            }
        val maxTextColor =
            when {
                isMaxMode -> accentColor
                maxEnabled -> TextSecondary
                else -> TextSecondary.copy(alpha = 0.5f)
            }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = if (stackFiat) Alignment.Top else Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = availableLabel,
                        style = textStyle,
                        color = TextSecondary,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = amountText,
                        style = textStyle,
                        color = TextSecondary,
                    )
                    if (fiatText != null && !stackFiat) {
                        Text(
                            text = fiatText,
                            style = textStyle,
                            color = TextSecondary.copy(alpha = 0.7f),
                        )
                    }
                }
                if (fiatText != null && stackFiat) {
                    Text(
                        text = fiatText,
                        style = textStyle,
                        color = TextSecondary.copy(alpha = 0.7f),
                        modifier = Modifier.padding(start = fiatIndent),
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Card(
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(enabled = maxEnabled, onClick = onMaxClick),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = maxContainerColor),
                border = BorderStroke(1.dp, maxBorderColor),
            ) {
                Text(
                    text = maxLabel,
                    style = maxTextStyle,
                    color = maxTextColor,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                )
            }
        }
    }
}
