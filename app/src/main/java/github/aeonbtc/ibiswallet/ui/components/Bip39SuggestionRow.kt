package github.aeonbtc.ibiswallet.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import github.aeonbtc.ibiswallet.ui.theme.BitcoinOrange
import github.aeonbtc.ibiswallet.ui.theme.BorderColor
import github.aeonbtc.ibiswallet.ui.theme.DarkSurfaceVariant
import androidx.compose.material3.Text

private const val MAX_SUGGESTIONS = 4

@Composable
fun Bip39SuggestionRow(
    input: String,
    wordlist: List<String>,
    onWordSelected: (completedInput: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val lastWord = remember(input) {
        if (input.isEmpty() || input.last().isWhitespace()) "" else input.substringAfterLast(' ')
    }

    val suggestions = remember(lastWord) {
        if (lastWord.isEmpty() || lastWord in wordlist) {
            emptyList()
        } else {
            wordlist.filter { it.startsWith(lastWord) }.take(MAX_SUGGESTIONS)
        }
    }

    if (suggestions.isNotEmpty()) {
        Row(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            suggestions.forEach { word ->
                Box(
                    modifier =
                        Modifier
                            .border(
                                border = BorderStroke(1.dp, BorderColor),
                                shape = RoundedCornerShape(8.dp),
                            ).background(
                                color = DarkSurfaceVariant,
                                shape = RoundedCornerShape(8.dp),
                            ).clickable {
                        val lastSpace = input.lastIndexOf(' ')
                        val prefix = if (lastSpace >= 0) input.take(lastSpace + 1) else ""
                        onWordSelected("$prefix$word ")
                    },
                ) {
                    Text(
                        text = word,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = BitcoinOrange,
                    )
                }
            }
        }
    }
}
