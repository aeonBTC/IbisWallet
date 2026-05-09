package github.aeonbtc.ibiswallet.ui.screens

import github.aeonbtc.ibiswallet.data.model.UtxoInfo

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
