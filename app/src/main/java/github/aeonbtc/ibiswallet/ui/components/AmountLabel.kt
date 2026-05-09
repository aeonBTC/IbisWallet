package github.aeonbtc.ibiswallet.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.text.TextStyle
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
