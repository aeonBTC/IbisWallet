package github.aeonbtc.ibiswallet.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import github.aeonbtc.ibiswallet.ui.theme.BitcoinOrange
import github.aeonbtc.ibiswallet.ui.theme.BorderColor
import github.aeonbtc.ibiswallet.ui.theme.DarkSurfaceVariant
import github.aeonbtc.ibiswallet.ui.theme.TextSecondary

/**
 * Rectangle toggle switch component that replaces the round Material Switch
 * for a more professional, minimal look with square corners
 */
@Composable
fun SquareToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    checkedColor: Color = BitcoinOrange,
    disabledCheckedColor: Color? = null,
    uncheckedColor: Color = DarkSurfaceVariant,
    uncheckedBorderColor: Color = BorderColor,
    uncheckedThumbColor: Color = TextSecondary,
    trackWidth: Dp = 44.dp,
    trackHeight: Dp = 24.dp,
    thumbSize: Dp = 18.dp,
    thumbPadding: Dp = 3.dp,
    trackCornerRadius: Dp = 4.dp,
    thumbCornerRadius: Dp = 3.dp,
) {
    // Animate thumb position
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) trackWidth - thumbSize - thumbPadding * 2 else 0.dp,
        animationSpec = tween(durationMillis = 150),
        label = "thumbOffset",
    )

    // Animate track color
    val trackColor by animateColorAsState(
        targetValue =
            when {
                !enabled && checked && disabledCheckedColor != null -> disabledCheckedColor.copy(alpha = 0.18f)
                !enabled -> uncheckedColor.copy(alpha = 0.5f)
                checked -> checkedColor.copy(alpha = 0.3f)
                else -> uncheckedColor
            },
        animationSpec = tween(durationMillis = 150),
        label = "trackColor",
    )

    // Animate border color
    val borderColor by animateColorAsState(
        targetValue =
            when {
                !enabled && checked && disabledCheckedColor != null -> disabledCheckedColor.copy(alpha = 0.8f)
                !enabled -> uncheckedBorderColor.copy(alpha = 0.3f)
                checked -> checkedColor
                else -> uncheckedBorderColor
            },
        animationSpec = tween(durationMillis = 150),
        label = "borderColor",
    )

    // Animate thumb color
    val thumbColor by animateColorAsState(
        targetValue =
            when {
                !enabled && checked && disabledCheckedColor != null -> disabledCheckedColor
                !enabled -> uncheckedThumbColor.copy(alpha = 0.5f)
                checked -> checkedColor
                else -> uncheckedThumbColor
            },
        animationSpec = tween(durationMillis = 150),
        label = "thumbColor",
    )

    Box(
        modifier =
            modifier
                .width(trackWidth)
                .height(trackHeight)
                .clip(RoundedCornerShape(trackCornerRadius))
                .background(trackColor)
                .border(1.dp, borderColor, RoundedCornerShape(trackCornerRadius))
                .clickable(
                    enabled = enabled,
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                ) { onCheckedChange(!checked) },
        contentAlignment = Alignment.CenterStart,
    ) {
        // Thumb
        Box(
            modifier =
                Modifier
                    .padding(thumbPadding)
                    .offset(x = thumbOffset)
                    .size(thumbSize)
                    .clip(RoundedCornerShape(thumbCornerRadius))
                    .background(thumbColor),
        )
    }
}

