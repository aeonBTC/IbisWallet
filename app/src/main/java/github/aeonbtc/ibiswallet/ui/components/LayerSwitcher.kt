package github.aeonbtc.ibiswallet.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import github.aeonbtc.ibiswallet.data.model.WalletLayer
import github.aeonbtc.ibiswallet.ui.theme.BitcoinOrange
import github.aeonbtc.ibiswallet.ui.theme.AccentBlue
import github.aeonbtc.ibiswallet.ui.theme.BorderColor
import github.aeonbtc.ibiswallet.ui.theme.DarkSurfaceVariant
import github.aeonbtc.ibiswallet.ui.theme.LiquidTeal
import github.aeonbtc.ibiswallet.ui.theme.TextPrimary
import github.aeonbtc.ibiswallet.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.material3.Text
import androidx.compose.ui.res.stringResource
import github.aeonbtc.ibiswallet.R

/**
 * Pill-style layer switcher with Swap button in the center.
 * Layout: [ Layer 1 ] [ Swap ] [ Layer 2 ]
 * Selected tab is highlighted with the layer's accent color.
 */
@Composable
fun LayerSwitcher(
    activeLayer: WalletLayer,
    onLayerSelected: (WalletLayer) -> Unit,
    modifier: Modifier = Modifier,
    isSwapSelected: Boolean = false,
    isSwapEnabled: Boolean = true,
    isLayer1Enabled: Boolean = true,
    layer2Color: Color = LiquidTeal,
    layer2Label: String = stringResource(R.string.loc_2f73501f),
    onSwap: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    var layerSwitchLocked by remember { mutableStateOf(false) }

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(24.dp))
                .background(DarkSurfaceVariant)
                .padding(2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LayerPill(
                label = stringResource(R.string.loc_b67a01a5),
                isSelected = !isSwapSelected && activeLayer == WalletLayer.LAYER1,
                selectedColor = BitcoinOrange,
                enabled = isLayer1Enabled,
                onClick = {
                    if (isLayer1Enabled && !layerSwitchLocked && (isSwapSelected || activeLayer != WalletLayer.LAYER1)) {
                        layerSwitchLocked = true
                        onLayerSelected(WalletLayer.LAYER1)
                        scope.launch {
                            delay(250)
                            layerSwitchLocked = false
                        }
                    }
                },
            )
            Spacer(modifier = Modifier.width(2.dp))
            SwapPill(
                isSelected = isSwapSelected,
                enabled = isSwapEnabled || isSwapSelected,
                layer2Accent = layer2Color,
                onClick = onSwap,
            )
            Spacer(modifier = Modifier.width(2.dp))
            LayerPill(
                label = layer2Label,
                isSelected = !isSwapSelected && activeLayer == WalletLayer.LAYER2,
                selectedColor = layer2Color,
                onClick = {
                    if (!layerSwitchLocked && (isSwapSelected || activeLayer != WalletLayer.LAYER2)) {
                        layerSwitchLocked = true
                        onLayerSelected(WalletLayer.LAYER2)
                        scope.launch {
                            delay(250)
                            layerSwitchLocked = false
                        }
                    }
                },
            )
        }
    }
}

@Composable
private fun SwapPill(
    isSelected: Boolean,
    enabled: Boolean,
    layer2Accent: Color,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    val bgColor by animateColorAsState(
        targetValue =
            when {
                isSelected -> AccentBlue.copy(alpha = 0.28f)
                enabled -> TextSecondary.copy(alpha = 0.10f)
                else -> TextSecondary.copy(alpha = 0.05f)
            },
        label = "swapPillBg",
    )
    val textColor by animateColorAsState(
        targetValue =
            when {
                isSelected -> TextPrimary
                enabled -> TextSecondary
                else -> TextSecondary.copy(alpha = 0.4f)
            },
        label = "swapPillText",
    )
    val borderColor by animateColorAsState(
        targetValue =
            when {
                isSelected -> layer2Accent.copy(alpha = 0.75f)
                enabled -> BorderColor.copy(alpha = 0.45f)
                else -> BorderColor.copy(alpha = 0.10f)
            },
        label = "swapPillBorder",
    )
    Row(
        modifier = Modifier
            .heightIn(min = 32.dp)
            .clip(shape)
            .background(bgColor)
            .border(width = 1.dp, color = borderColor, shape = shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = null,
            tint = textColor,
            modifier = Modifier.size(13.dp),
        )
        Spacer(modifier = Modifier.width(3.dp))
        Text(
            text = stringResource(R.string.loc_85a12a5f),
            color = textColor,
            style = MaterialTheme.typography.labelMedium.copy(lineHeight = 16.sp),
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.width(3.dp))
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = textColor,
            modifier = Modifier.size(13.dp),
        )
    }
}

@Composable
private fun LayerPill(
    label: String,
    isSelected: Boolean,
    selectedColor: Color,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val bgColor by animateColorAsState(
        targetValue = if (isSelected) selectedColor else DarkSurfaceVariant,
        label = "pillBg",
    )
    val textColor by animateColorAsState(
        targetValue =
            when {
                isSelected -> TextPrimary
                enabled -> TextSecondary
                else -> TextSecondary.copy(alpha = 0.4f)
            },
        label = "pillText",
    )

    Box(
        modifier = Modifier
            .heightIn(min = 36.dp)
            .widthIn(min = 92.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(bgColor)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = textColor,
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 14.sp, lineHeight = 16.sp),
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}
