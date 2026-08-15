package github.aeonbtc.ibiswallet.ui.screens

import github.aeonbtc.ibiswallet.data.model.LiquidAsset
import github.aeonbtc.ibiswallet.data.model.UtxoInfo
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
