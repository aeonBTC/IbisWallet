package github.aeonbtc.ibiswallet.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import github.aeonbtc.ibiswallet.ui.theme.BitcoinOrange
import github.aeonbtc.ibiswallet.ui.theme.BorderColor
import github.aeonbtc.ibiswallet.ui.theme.TextSecondary

/**
 * Compact read-only dropdown field with proper text alignment (no clipping).
 */
@Composable
fun CompactDropdownField(
    value: String,
    expanded: Boolean,
    modifier: Modifier = Modifier,
    dense: Boolean = false,
) {
    val horizontalPadding = if (dense) 10.dp else 12.dp
    val verticalPadding = if (dense) 7.dp else 10.dp
    val textSize = if (dense) 13.5.sp else 14.5.sp
    val iconSize = if (dense) 18.dp else 20.dp

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .border(1.dp, if (expanded) BitcoinOrange else BorderColor, RoundedCornerShape(8.dp))
                .padding(horizontal = horizontalPadding, vertical = verticalPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = value,
            style = TextStyle(fontSize = textSize),
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = Icons.Default.ArrowDropDown,
            contentDescription = null,
            tint = TextSecondary,
            modifier = Modifier.size(iconSize),
        )
    }
}

@Composable
fun DropdownOptionText(
    title: String,
    subtitle: String,
    selected: Boolean,
) {
    Column {
        Text(
            text = title,
            style = TextStyle(fontSize = 14.5.sp),
            color = if (selected) BitcoinOrange else MaterialTheme.colorScheme.onBackground,
        )
        if (subtitle.isNotBlank()) {
            Text(
                text = subtitle,
                style = TextStyle(fontSize = 12.5.sp),
                color = TextSecondary,
            )
        }
    }
}
