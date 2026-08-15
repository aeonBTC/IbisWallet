package github.aeonbtc.ibiswallet.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Main balance amount on Layer cards.
 *
 * ₿ is drawn in its own fixed slot (uneven glyph metrics) so toggling BTC/sats
 * does not vertically nudge the numeric amount.
 */
@Composable
fun BalanceAmountText(
    amountText: String,
    showBtcSymbol: Boolean,
    showSatsUnit: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onBackground,
) {
    val base = MaterialTheme.typography.displaySmall
    val density = LocalDensity.current
    val lineHeightDp =
        with(density) {
            (base.lineHeight.takeIf { it.isSp } ?: base.fontSize * 1.2f).toDp()
        }
    val slotHeight = maxOf(lineHeightDp, 40.dp)
    val amountStyle =
        base.merge(
            TextStyle(
                fontWeight = FontWeight.Bold,
                platformStyle = PlatformTextStyle(includeFontPadding = false),
                lineHeightStyle =
                    LineHeightStyle(
                        alignment = LineHeightStyle.Alignment.Center,
                        trim = LineHeightStyle.Trim.Both,
                    ),
            ),
        )
    // Match amount size but isolate ₿ metrics so digits stay put.
    val symbolStyle =
        amountStyle.merge(
            TextStyle(
                fontSize = base.fontSize,
                lineHeight = base.fontSize,
                textAlign = TextAlign.Center,
            ),
        )

    Box(
        contentAlignment = Alignment.Center,
        modifier =
            modifier
                .fillMaxWidth()
                .height(slotHeight)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (showBtcSymbol) {
                // Fixed slot + slight optical nudge; glyph alone used to shift the whole line.
                Box(
                    contentAlignment = Alignment.Center,
                    modifier =
                        Modifier
                            .height(slotHeight)
                            .widthIn(min = with(density) { (base.fontSize * 0.85f).toDp() })
                            .padding(end = 4.dp),
                ) {
                    Text(
                        text = "\u20BF",
                        style = symbolStyle,
                        color = color,
                        maxLines = 1,
                        softWrap = false,
                        // Slight lift — glyph center sits low vs bold digits in app fonts.
                        modifier =
                            Modifier
                                .wrapContentHeight(align = Alignment.CenterVertically)
                                .offset(y = (-2.4).dp),
                    )
                }
            }
            Text(
                text = amountText,
                style = amountStyle,
                color = color,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
            if (showSatsUnit) {
                Text(
                    text = " sats",
                    style = amountStyle,
                    color = color,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}
