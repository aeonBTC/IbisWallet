package github.aeonbtc.ibiswallet.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import github.aeonbtc.ibiswallet.ui.theme.TextSecondary

/**
 * Standard primary action button for the app.
 *
 * - **Active** (`enabled = true`): TextSecondary content + light border
 * - **Inactive** (`enabled = false`): faded content + faded border
 *
 * Optionally override [activeColor] for special cases (e.g. SuccessGreen).
 */
@Composable
fun IbisButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    activeColor: Color = TextSecondary,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = RoundedCornerShape(8.dp),
        border =
            BorderStroke(
                1.dp,
                if (enabled) Color(0xFF9BA3AC) else Color(0xFF9BA3AC).copy(alpha = 0.25f),
            ),
        colors =
            ButtonDefaults.outlinedButtonColors(
                contentColor = activeColor,
                disabledContentColor = TextSecondary.copy(alpha = 0.3f),
            ),
        contentPadding = contentPadding,
        content = content,
    )
}
