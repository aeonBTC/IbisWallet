package github.aeonbtc.ibiswallet.ui.screens

import github.aeonbtc.ibiswallet.data.model.UtxoInfo
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly

class CoinControlSelectionTest : FunSpec({

    fun utxo(
        outpoint: String,
        amountSats: ULong = 50_000UL,
        label: String? = null,
        isConfirmed: Boolean = true,
        isFrozen: Boolean = false,
    ): UtxoInfo {
        val txid = outpoint.substringBefore(':')
        val vout = outpoint.substringAfter(':').toUInt()
        return UtxoInfo(
            outpoint = outpoint,
            txid = txid,
            vout = vout,
            address = "bc1qexampleaddress0000000000000000000000000000000000000",
            amountSats = amountSats,
            label = label,
            isConfirmed = isConfirmed,
            isFrozen = isFrozen,
        )
    }

    test("toggle removes a stale selected utxo by outpoint") {
        val originallySelected = utxo(outpoint = "tx1:0", label = null, isConfirmed = false)
        val refreshedRow = originallySelected.copy(label = "updated", isConfirmed = true)
        val selected = mutableListOf(originallySelected)

        toggleCoinControlSelection(selected, refreshedRow)

        selected.shouldContainExactly(emptyList())
    }

    test("toggle adds a utxo when it is not already selected") {
        val selectable = utxo(outpoint = "tx2:1")
        val selected = mutableListOf<UtxoInfo>()

        toggleCoinControlSelection(selected, selectable)

        selected.shouldContainExactly(selectable)
    }

    test("reconcile refreshes selected entries from the latest utxo snapshot") {
        val stale = utxo(outpoint = "tx3:2", label = null, isConfirmed = false)
        val refreshed = stale.copy(label = "fresh", isConfirmed = true)
        val selected = mutableListOf(stale)

        reconcileCoinControlSelection(selected, availableUtxos = listOf(refreshed))

        selected.shouldContainExactly(refreshed)
    }

    test("swap restore maps saved outpoints onto the latest utxos in saved order") {
        val first = utxo(outpoint = "tx4:0")
        val second = utxo(outpoint = "tx5:1")
        val restoredSelection = selectCoinControlUtxos(
            outpoints = listOf(second.outpoint, "missing:9", first.outpoint),
            availableUtxos = listOf(first, second),
        )

        restoredSelection.shouldContainExactly(second, first)
    }
})
