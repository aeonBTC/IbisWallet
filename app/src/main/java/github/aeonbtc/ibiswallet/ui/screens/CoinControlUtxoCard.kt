package github.aeonbtc.ibiswallet.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import github.aeonbtc.ibiswallet.R
import github.aeonbtc.ibiswallet.data.local.SecureStorage
import github.aeonbtc.ibiswallet.data.model.LiquidAsset
import github.aeonbtc.ibiswallet.data.model.UtxoInfo
import github.aeonbtc.ibiswallet.ui.theme.AccentGreen
import github.aeonbtc.ibiswallet.ui.theme.AccentTeal
import github.aeonbtc.ibiswallet.ui.theme.DarkCard
import github.aeonbtc.ibiswallet.ui.theme.TextSecondary
import github.aeonbtc.ibiswallet.ui.theme.WarningYellow

private const val HIDDEN_AMOUNT = "****"

@Composable
internal fun CoinControlUtxoCard(
    utxo: UtxoInfo,
    isSelected: Boolean,
    isDisabled: Boolean,
    useSats: Boolean,
    accentColor: Color,
    onToggle: () -> Unit,
    btcPrice: Double? = null,
    fiatCurrency: String = SecureStorage.DEFAULT_PRICE_CURRENCY,
    privacyMode: Boolean = false,
) {
    val assetId = utxo.assetId
    val isNonLbtcAsset = assetId != null && !LiquidAsset.isPolicyAsset(assetId)
    val resolvedAsset = assetId?.takeUnless { LiquidAsset.isPolicyAsset(it) }?.let { LiquidAsset.resolve(it) }
    val amountColor = if (isDisabled) AccentGreen.copy(alpha = 0.4f) else AccentGreen
    val secondaryColor = if (isDisabled) TextSecondary.copy(alpha = 0.4f) else TextSecondary

    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .then(if (isDisabled) Modifier else Modifier.clickable(onClick = onToggle)),
        shape = RoundedCornerShape(12.dp),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    when {
                        isDisabled -> DarkCard.copy(alpha = 0.4f)
                        isSelected -> accentColor.copy(alpha = 0.15f)
                        else -> DarkCard
                    },
            ),
        border =
            if (isSelected && !isDisabled) {
                BorderStroke(1.dp, accentColor.copy(alpha = 0.5f))
            } else {
                null
            },
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector =
                        if (isSelected && !isDisabled) {
                            Icons.Default.CheckCircle
                        } else {
                            Icons.Default.RadioButtonUnchecked
                        },
                    contentDescription =
                        if (isSelected) {
                            stringResource(R.string.common_selected)
                        } else {
                            stringResource(R.string.loc_e17307d1)
                        },
                    tint =
                        when {
                            isDisabled -> TextSecondary.copy(alpha = 0.3f)
                            isSelected -> accentColor
                            else -> TextSecondary
                        },
                    modifier = Modifier.size(24.dp),
                )
                Spacer(modifier = Modifier.width(10.dp))
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text =
                            when {
                                privacyMode -> HIDDEN_AMOUNT
                                isNonLbtcAsset && resolvedAsset != null ->
                                    formatCoinControlAssetAmount(resolvedAsset, utxo.amountSats.toLong())
                                else -> formatAmount(utxo.amountSats, useSats, includeUnit = true)
                            },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = amountColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!isNonLbtcAsset && btcPrice != null && btcPrice > 0 && !privacyMode) {
                        Text(
                            text = " · ${formatFiat((utxo.amountSats.toDouble() / 100_000_000.0) * btcPrice, fiatCurrency)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = secondaryColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                if ((isNonLbtcAsset && resolvedAsset != null) || !utxo.isConfirmed) {
                    Column(
                        modifier = Modifier.wrapContentWidth(),
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        if (isNonLbtcAsset && resolvedAsset != null) {
                            Box(
                                modifier =
                                    Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(AccentTeal.copy(alpha = 0.16f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp),
                            ) {
                                Text(
                                    text = resolvedAsset.ticker,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = AccentTeal,
                                    maxLines = 1,
                                    softWrap = false,
                                )
                            }
                        }
                        if (!utxo.isConfirmed) {
                            Box(
                                modifier =
                                    Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(WarningYellow.copy(alpha = if (isDisabled) 0.1f else 0.2f))
                                        .padding(horizontal = 8.dp, vertical = 2.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.loc_1b684325),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = WarningYellow.copy(alpha = if (isDisabled) 0.4f else 1f),
                                    maxLines = 1,
                                    softWrap = false,
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                CoinControlInfoField(
                    label = stringResource(R.string.loc_c2f3561d),
                    value = utxo.address,
                    valueColor = secondaryColor,
                    modifier = Modifier.weight(1f),
                )
                CoinControlInfoField(
                    label = stringResource(R.string.loc_cafbbb4a),
                    value = formatCoinControlOutpoint(utxo),
                    valueColor = MaterialTheme.colorScheme.onBackground.copy(alpha = if (isDisabled) 0.4f else 0.7f),
                    modifier = Modifier.weight(1f),
                )
            }

            if (isDisabled) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.loc_bdedd2ce),
                    style = MaterialTheme.typography.bodySmall,
                    color = WarningYellow,
                )
            }
        }
    }
}

@Composable
private fun CoinControlInfoField(
    label: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = "$label:",
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = valueColor,
            maxLines = 1,
            overflow = TextOverflow.MiddleEllipsis,
        )
    }
}
