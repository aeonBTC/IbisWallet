package github.aeonbtc.ibiswallet.ui.screens

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import github.aeonbtc.ibiswallet.R
import github.aeonbtc.ibiswallet.data.model.LiquidAsset
import github.aeonbtc.ibiswallet.data.model.UtxoInfo
import github.aeonbtc.ibiswallet.ui.theme.DarkCard
import github.aeonbtc.ibiswallet.ui.theme.TextSecondary
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.pow

internal fun toggleCoinControlSelection(
    selectedUtxos: MutableList<UtxoInfo>,
    utxo: UtxoInfo,
) {
    val removed = selectedUtxos.removeAll { it.outpoint == utxo.outpoint }
    if (!removed) {
        selectedUtxos.add(utxo)
    }
}

internal fun reconcileCoinControlSelection(
    selectedUtxos: MutableList<UtxoInfo>,
    availableUtxos: List<UtxoInfo>,
) {
    val refreshedSelection = selectCoinControlUtxos(
        outpoints = selectedUtxos.map { it.outpoint },
        availableUtxos = availableUtxos,
    )
    if (refreshedSelection == selectedUtxos) {
        return
    }
    selectedUtxos.clear()
    selectedUtxos.addAll(refreshedSelection)
}

internal fun formatCoinControlOutpoint(utxo: UtxoInfo): String {
    val txid = utxo.txid.ifBlank { utxo.outpoint.substringBefore(':') }
    val vout = utxo.vout
    if (txid.length <= 24) {
        return "$txid:$vout"
    }
    return "${txid.take(16)}...${txid.takeLast(8)}:$vout"
}

internal fun formatCoinControlAssetAmount(
    asset: LiquidAsset,
    rawAmount: Long,
): String {
    val divisor = 10.0.pow(asset.precision.toDouble())
    val full = String.format(Locale.US, "%.${asset.precision}f", rawAmount.toDouble() / divisor)
    val trimmed = full.trimEnd('0').let { if (it.endsWith('.')) "${it}00" else it }
    val minDecimals =
        if (trimmed.contains('.') && trimmed.substringAfter('.').length < 2) {
            trimmed + "0".repeat(2 - trimmed.substringAfter('.').length)
        } else {
            trimmed
        }
    return "$minDecimals ${asset.ticker}"
}

internal fun selectCoinControlUtxos(
    outpoints: Collection<String>,
    availableUtxos: List<UtxoInfo>,
): List<UtxoInfo> {
    if (outpoints.isEmpty()) {
        return emptyList()
    }
    val availableByOutpoint = availableUtxos.associateBy { it.outpoint }
    return outpoints.mapNotNull(availableByOutpoint::get)
}

internal fun filterCoinControlUtxos(
    utxos: List<UtxoInfo>,
    query: String,
): List<UtxoInfo> {
    val trimmed = query.trim()
    if (trimmed.isBlank()) {
        return utxos
    }
    return utxos.filter { matchesCoinControlSearch(it, trimmed) }
}

internal fun matchesCoinControlSearch(
    utxo: UtxoInfo,
    query: String,
): Boolean {
    val normalizedQuery = query.trim().lowercase()
    if (normalizedQuery.isBlank()) return true
    val normalizedAmountQuery = normalizeCoinControlSearchValue(query)
    val assetId = utxo.assetId
    val isNonLbtc = assetId != null && !LiquidAsset.isPolicyAsset(assetId)
    val amountTerms = buildList {
        add(utxo.amountSats.toString())
        add(NumberFormat.getNumberInstance(Locale.US).format(utxo.amountSats.toLong()))
        if (isNonLbtc) {
            val asset = LiquidAsset.resolve(assetId)
            add(formatCoinControlAssetAmount(asset, utxo.amountSats.toLong()))
            add(asset.ticker)
        } else {
            add("${utxo.amountSats} sats")
            add("${NumberFormat.getNumberInstance(Locale.US).format(utxo.amountSats.toLong())} sats")
            add(String.format(Locale.US, "%.8f", utxo.amountSats.toDouble() / 100_000_000.0))
            add("${String.format(Locale.US, "%.8f", utxo.amountSats.toDouble() / 100_000_000.0)} btc")
        }
    }

    return utxo.address.lowercase().contains(normalizedQuery) ||
        utxo.label?.lowercase()?.contains(normalizedQuery) == true ||
        utxo.txid.lowercase().contains(normalizedQuery) ||
        utxo.outpoint.lowercase().contains(normalizedQuery) ||
        utxo.vout.toString().contains(normalizedQuery) ||
        amountTerms.any { normalizeCoinControlSearchValue(it).contains(normalizedAmountQuery) } ||
        (if (utxo.isConfirmed) "confirmed" else "pending").contains(normalizedQuery) ||
        (if (utxo.isFrozen) "frozen" else "spendable").contains(normalizedQuery) ||
        (if (utxo.isSilentPayment && (normalizedQuery == "sp" || normalizedQuery.contains("silent"))) "sp silent" else "").contains(normalizedQuery)
}

private fun normalizeCoinControlSearchValue(value: String): String =
    value
        .trim()
        .lowercase()
        .replace(",", "")
        .replace(" ", "")

@Composable
internal fun CoinControlSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    accentColor: Color,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = {
            Text(
                text = stringResource(R.string.loc_ff1e339d),
                maxLines = 1,
            )
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = TextSecondary,
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = stringResource(R.string.loc_2470de02),
                        tint = TextSecondary,
                    )
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        colors =
            OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = DarkCard,
                focusedContainerColor = DarkCard,
                unfocusedBorderColor = DarkCard,
                focusedBorderColor = accentColor,
            ),
        modifier =
            modifier
                .fillMaxWidth()
                .height(52.dp),
    )
}