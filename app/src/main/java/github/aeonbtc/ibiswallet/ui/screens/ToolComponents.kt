package github.aeonbtc.ibiswallet.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import github.aeonbtc.ibiswallet.ui.theme.BitcoinOrange
import github.aeonbtc.ibiswallet.ui.theme.BorderColor
import github.aeonbtc.ibiswallet.ui.theme.DarkCard
import github.aeonbtc.ibiswallet.ui.theme.TextSecondary

@Composable
internal fun ToolField(
    value: String,
    onValueChange: (String) -> Unit,
    title: String? = null,
    modifier: Modifier = Modifier,
    monospace: Boolean = false,
    singleLine: Boolean = true,
    minHeight: Int = 0,
    placeholder: String? = null,
) {
    val fieldModifier =
        Modifier
            .fillMaxWidth()
            .then(if (minHeight > 0) Modifier.heightIn(min = minHeight.dp) else Modifier)

    Column(modifier = modifier.fillMaxWidth()) {
        if (title != null) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = TextSecondary,
            )
            Spacer(modifier = Modifier.height(6.dp))
        }
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = fieldModifier,
            singleLine = singleLine,
            textStyle =
                if (monospace) {
                    MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                } else {
                    MaterialTheme.typography.bodyMedium
                },
            placeholder =
                placeholder?.let { hint ->
                    {
                        Text(
                            text = hint,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary.copy(alpha = 0.5f),
                        )
                    }
                },
            shape = RoundedCornerShape(8.dp),
            colors =
                OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = BitcoinOrange,
                    unfocusedBorderColor = BorderColor,
                    cursorColor = BitcoinOrange,
                ),
        )
    }
}

@Composable
internal fun ToolResultBanner(
    text: String,
    color: Color,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCard),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color = color,
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Composable
internal fun ToolDetailRow(
    label: String,
    value: String,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            modifier = Modifier.width(96.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
internal fun ToolDetailCard(
    title: String,
    rows: List<Pair<String, String>>,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCard),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(modifier = Modifier.height(8.dp))
            rows.forEach { (label, value) ->
                ToolDetailRow(label = label, value = value)
            }
        }
    }
}
