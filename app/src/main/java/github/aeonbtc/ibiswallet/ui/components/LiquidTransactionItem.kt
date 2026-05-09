package github.aeonbtc.ibiswallet.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import github.aeonbtc.ibiswallet.R
import github.aeonbtc.ibiswallet.data.model.LiquidAsset
import github.aeonbtc.ibiswallet.data.model.LiquidTransaction
import github.aeonbtc.ibiswallet.data.model.LiquidSwapTxRole
import github.aeonbtc.ibiswallet.data.model.LiquidTxSource
import github.aeonbtc.ibiswallet.data.model.LiquidTxType
import github.aeonbtc.ibiswallet.ui.screens.formatFiat
import github.aeonbtc.ibiswallet.ui.theme.AccentGreen
import github.aeonbtc.ibiswallet.ui.theme.AccentRed
import github.aeonbtc.ibiswallet.ui.theme.AccentTeal
import github.aeonbtc.ibiswallet.ui.theme.BitcoinOrange
import github.aeonbtc.ibiswallet.ui.theme.DarkCard
import github.aeonbtc.ibiswallet.ui.theme.LightningYellow
import github.aeonbtc.ibiswallet.ui.theme.LiquidTeal
import github.aeonbtc.ibiswallet.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.pow

/**
 * Transaction list item for Liquid L-BTC transactions.
 * Follows the same visual pattern as Bitcoin transaction items
 * but with Liquid-specific source badges and L-BTC denomination.
 */
@Composable
fun LiquidTransactionItem(
    tx: LiquidTransaction,
    denomination: String,
    btcPrice: Double?,
    fiatCurrency: String,
    historicalBtcPrice: Double?,
    privacyMode: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
) {
    val swapDetails = tx.swapDetails?.takeIf { tx.source == LiquidTxSource.CHAIN_SWAP }
    val swapRole = swapDetails?.role
    val isReceive =
        when (swapRole) {
            LiquidSwapTxRole.FUNDING -> false
            LiquidSwapTxRole.SETTLEMENT -> true
            null -> tx.balanceSatoshi >= 0
        }
    val displayAmountSats =
        when (swapRole) {
            LiquidSwapTxRole.FUNDING -> swapDetails.sendAmountSats.takeIf { it > 0L } ?: abs(tx.balanceSatoshi)
            LiquidSwapTxRole.SETTLEMENT ->
                swapDetails.expectedReceiveAmountSats.takeIf { it > 0L } ?: abs(tx.balanceSatoshi)
            null -> abs(tx.balanceSatoshi)
        }
    val displayLabel = label?.takeIf { it.isNotBlank() } ?: tx.memo.takeIf { it.isNotBlank() }
    val icon = if (isReceive) Icons.AutoMirrored.Filled.CallReceived else Icons.AutoMirrored.Filled.CallMade
    val iconTint = if (isReceive) AccentGreen else AccentRed
    val iconBackground = if (isReceive) AccentGreen.copy(alpha = 0.1f) else AccentRed.copy(alpha = 0.1f)
    val amountColor = if (isReceive) AccentGreen else AccentRed
    val networkBadge = networkBadge(tx.source)
    val defaultTitleRes = defaultTransactionTitleRes(tx, isReceive)
    val formattedTimestamp =
        remember(tx.timestamp) {
            tx.timestamp?.let { formatTimestamp(it) }.orEmpty()
        }
    val nonLbtcDelta = tx.assetDeltas.entries.firstOrNull { !LiquidAsset.isPolicyAsset(it.key) && it.value != 0L }
    val nonLbtcAsset = nonLbtcDelta?.let { LiquidAsset.resolve(it.key) }
    val hideNativeLiquidBadgeForUsdt =
        tx.source == LiquidTxSource.NATIVE && nonLbtcAsset?.assetId == LiquidAsset.USDT_ASSET_ID
    val showNetworkBadge = !hideNativeLiquidBadgeForUsdt
    val showAssetBadge = nonLbtcAsset != null

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCard),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(iconBackground),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription =
                        if (isReceive) {
                            stringResource(R.string.loc_301a5b91)
                        } else {
                            stringResource(R.string.loc_1af68597)
                        },
                    tint = iconTint,
                    modifier = Modifier.size(24.dp),
                )
            }


            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(defaultTitleRes),
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp, lineHeight = 25.sp),
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (showNetworkBadge) {
                        Spacer(modifier = Modifier.width(3.dp))
                        Box(
                            modifier =
                                Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(networkBadge.color.copy(alpha = 0.16f))
                                    .padding(horizontal = 5.dp, vertical = 2.dp),
                        ) {
                            Text(
                                text = stringResource(networkBadge.labelRes),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp, lineHeight = 17.sp),
                                color = networkBadge.color,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                            )
                        }
                    }
                    if (showAssetBadge) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Box(
                            modifier =
                                Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(AccentTeal.copy(alpha = 0.16f))
                                    .padding(horizontal = 5.dp, vertical = 2.dp),
                        ) {
                            Text(
                                text = nonLbtcAsset.ticker,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp, lineHeight = 17.sp),
                                color = AccentTeal,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                            )
                        }
                    }
                }
                if (!displayLabel.isNullOrBlank()) {
                    Text(
                        text = displayLabel,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 14.sp, lineHeight = 18.sp),
                        color = AccentTeal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = formattedTimestamp,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 14.sp, lineHeight = 18.sp),
                    color = TextSecondary,
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                if (nonLbtcDelta != null) {
                    val asset = LiquidAsset.resolve(nonLbtcDelta.key)
                    val assetAmount = abs(nonLbtcDelta.value)
                    val divisor = 10.0.pow(asset.precision.toDouble())
                    val displayValue = assetAmount.toDouble() / divisor
                    val assetPrefix = if (nonLbtcDelta.value >= 0) "+" else "-"
                    Text(
                        text = if (privacyMode) "****" else "$assetPrefix${String.format(Locale.US, "%.2f", displayValue)} ${asset.ticker}",
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp, lineHeight = 25.sp),
                        fontWeight = FontWeight.SemiBold,
                        color = if (nonLbtcDelta.value >= 0) AccentGreen else AccentRed,
                        textAlign = TextAlign.End,
                    )
                } else {
                    val amountText = if (privacyMode) {
                        "****"
                    } else {
                        val prefix = if (isReceive) "+" else "-"
                        if (denomination == "SATS") {
                            "$prefix${"%,d".format(displayAmountSats)} sats"
                        } else {
                            "$prefix${"%.8f".format(displayAmountSats / 100_000_000.0)}"
                        }
                    }

                    Text(
                        text = amountText,
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp, lineHeight = 25.sp),
                        fontWeight = FontWeight.SemiBold,
                        color = amountColor,
                        textAlign = TextAlign.End,
                    )
                }

                val fiatText =
                    if (!privacyMode && nonLbtcDelta == null) {
                        val effectiveBtcPrice = historicalBtcPrice ?: btcPrice
                        effectiveBtcPrice
                            ?.takeIf { it > 0.0 }
                            ?.let { price ->
                                formatFiat(displayAmountSats / 100_000_000.0 * price, fiatCurrency)
                            }
                    } else {
                        null
                    }
                fiatText?.let {
                    HistoricalFiatText(
                        text = it,
                        isHistorical = historicalBtcPrice != null && historicalBtcPrice > 0,
                    )
                }
                if (tx.height == null) {
                    StatusBadge(
                        label = stringResource(R.string.loc_1b684325),
                        color = BitcoinOrange,
                        modifier = Modifier.align(Alignment.End),
                    )
                }
            }
        }
    }
}

@Composable
private fun HistoricalFiatText(
    text: String,
    isHistorical: Boolean,
    large: Boolean = false,
) {
    val style =
        if (large) {
            MaterialTheme.typography.titleMedium.copy(
                fontSize = 18.sp,
                lineHeight = 26.sp,
                fontWeight = FontWeight.Normal,
            )
        } else {
            MaterialTheme.typography.bodyMedium.copy(fontSize = 16.sp, lineHeight = 22.sp)
        }
    val iconSize = if (large) 15.dp else 13.dp
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        if (isHistorical) {
            Icon(
                imageVector = Icons.Default.Schedule,
                contentDescription = null,
                tint = BitcoinOrange,
                modifier = Modifier.size(iconSize),
            )
        }
        Text(
            text = text,
            color = TextSecondary,
            style = style,
            textAlign = TextAlign.End,
        )
    }
}

private fun defaultTransactionTitleRes(
    tx: LiquidTransaction,
    isReceive: Boolean,
): Int =
    when {
        tx.source == LiquidTxSource.CHAIN_SWAP -> if (isReceive) R.string.loc_301a5b91 else R.string.loc_1af68597
        tx.balanceSatoshi >= 0 -> R.string.loc_301a5b91
        tx.type == LiquidTxType.SEND -> R.string.loc_1af68597
        else -> if (isReceive) R.string.loc_301a5b91 else R.string.loc_1af68597
    }

private data class TransactionNetworkBadge(
    val labelRes: Int,
    val color: Color,
)

private fun networkBadge(source: LiquidTxSource): TransactionNetworkBadge =
    when (source) {
        LiquidTxSource.CHAIN_SWAP -> TransactionNetworkBadge(
            labelRes = R.string.loc_85a12a5f,
            color = BitcoinOrange,
        )
        LiquidTxSource.LIGHTNING_RECEIVE_SWAP,
        LiquidTxSource.LIGHTNING_SEND_SWAP,
        -> TransactionNetworkBadge(
            labelRes = R.string.loc_03b82433,
            color = LightningYellow,
        )
        LiquidTxSource.NATIVE -> TransactionNetworkBadge(
            labelRes = R.string.loc_22236665,
            color = LiquidTeal,
        )
    }

private fun formatTimestamp(epochSeconds: Long): String {
    return try {
        liquidDateTimeFormatter.get()?.format(Date(epochSeconds * 1000)).orEmpty()
    } catch (_: Exception) { "" }
}

private val liquidDateTimeFormatter: ThreadLocal<SimpleDateFormat> =
    ThreadLocal.withInitial {
        SimpleDateFormat("MMM d, yyyy · HH:mm", Locale.getDefault())
    }
