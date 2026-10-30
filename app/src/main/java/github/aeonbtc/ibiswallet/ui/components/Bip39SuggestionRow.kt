package github.aeonbtc.ibiswallet.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import github.aeonbtc.ibiswallet.ui.theme.BitcoinOrange
import github.aeonbtc.ibiswallet.ui.theme.BorderColor
import github.aeonbtc.ibiswallet.ui.theme.DarkSurfaceVariant
import github.aeonbtc.ibiswallet.util.QrFormatParser

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

    val suggestions = remember(lastWord, wordlist) {
        QrFormatParser.suggestBip39Words(wordlist, lastWord, MAX_SUGGESTIONS)
    }

    if (suggestions.isNotEmpty()) {
        @OptIn(ExperimentalLayoutApi::class)
        FlowRow(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            suggestions.forEach { word ->
                Box(
                    contentAlignment = Alignment.Center,
                    modifier =
                        Modifier
                            .heightIn(min = 32.dp)
                            .border(
                                border = BorderStroke(1.dp, BorderColor),
                                shape = RoundedCornerShape(8.dp),
                            )
                            .background(
                                color = DarkSurfaceVariant,
                                shape = RoundedCornerShape(8.dp),
                            )
                            .clickable {
                                onWordSelected(QrFormatParser.completeSeedWord(input, word))
                            }
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = word,
                        style = MaterialTheme.typography.bodySmall,
                        color = BitcoinOrange,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        softWrap = false,
                    )
                }
            }
        }
    }
}
