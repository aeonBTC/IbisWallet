package github.aeonbtc.ibiswallet.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.ui.unit.dp
import github.aeonbtc.ibiswallet.ui.theme.BitcoinOrange
import github.aeonbtc.ibiswallet.ui.theme.BorderColor
import github.aeonbtc.ibiswallet.ui.theme.DarkSurfaceVariant
import github.aeonbtc.ibiswallet.ui.theme.SuccessGreen
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
    uncheckedColor: Color = DarkSurfaceVariant
) {
    val trackWidth = 44.dp
    val trackHeight = 24.dp
    val thumbSize = 18.dp
    val thumbPadding = 3.dp
    
    // Animate thumb position
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) trackWidth - thumbSize - thumbPadding * 2 else 0.dp,
        animationSpec = tween(durationMillis = 150),
        label = "thumbOffset"
    )
    
    // Animate track color
    val trackColor by animateColorAsState(
        targetValue = when {
            !enabled -> uncheckedColor.copy(alpha = 0.5f)
            checked -> checkedColor.copy(alpha = 0.3f)
            else -> uncheckedColor
        },
        animationSpec = tween(durationMillis = 150),
        label = "trackColor"
    )
    
    // Animate border color
    val borderColor by animateColorAsState(
        targetValue = when {
            !enabled -> BorderColor.copy(alpha = 0.3f)
            checked -> checkedColor
            else -> BorderColor
        },
        animationSpec = tween(durationMillis = 150),
        label = "borderColor"
    )
    
    // Animate thumb color
    val thumbColor by animateColorAsState(
        targetValue = when {
            !enabled -> TextSecondary.copy(alpha = 0.5f)
            checked -> checkedColor
            else -> TextSecondary
        },
        animationSpec = tween(durationMillis = 150),
        label = "thumbColor"
    )
    
    Box(
        modifier = modifier
            .width(trackWidth)
            .height(trackHeight)
            .clip(RoundedCornerShape(4.dp))
            .background(trackColor)
            .border(1.dp, borderColor, RoundedCornerShape(4.dp))
            .clickable(
                enabled = enabled,
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { onCheckedChange(!checked) },
        contentAlignment = Alignment.CenterStart
    ) {
        // Thumb
        Box(
            modifier = Modifier
                .padding(thumbPadding)
                .offset(x = thumbOffset)
                .size(thumbSize)
                .clip(RoundedCornerShape(3.dp))
                .background(thumbColor)
        )
    }
}

/**
 * Rectangle toggle specifically for Tor/success states
 * Uses green when checked
 */
@Composable
fun SquareToggleGreen(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    SquareToggle(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
        checkedColor = SuccessGreen
    )
}
