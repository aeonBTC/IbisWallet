package github.aeonbtc.ibiswallet.ui.screens

import github.aeonbtc.ibiswallet.data.model.UtxoInfo
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe

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

    test("format keeps short outpoints intact") {
        formatCoinControlOutpoint(utxo(outpoint = "abcd1234:1")) shouldBe "abcd1234:1"
    }

    test("format abbreviates a full txid and keeps the vout") {
        val txid = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
        formatCoinControlOutpoint(utxo(outpoint = "$txid:3")) shouldBe "0123456789abcdef...89abcdef:3"
    }

    test("filter returns all utxos on blank query") {
        val first = utxo(outpoint = "txa:0")
        val second = utxo(outpoint = "txb:1")
        filterCoinControlUtxos(listOf(first, second), "   ").shouldContainExactly(first, second)
    }

    test("filter matches address, label, txid and outpoint") {
        val labeled = utxo(outpoint = "abc123:0", label = "Savings")
        val other = utxo(outpoint = "def456:1")
        filterCoinControlUtxos(listOf(labeled, other), "savings").shouldContainExactly(labeled)
        filterCoinControlUtxos(listOf(labeled, other), "abc123").shouldContainExactly(labeled)
        filterCoinControlUtxos(listOf(labeled, other), "def456:1").shouldContainExactly(other)
    }

    test("filter matches amount and confirmation status") {
        val confirmed = utxo(outpoint = "txc:0", amountSats = 50_000UL, isConfirmed = true)
        val pending = utxo(outpoint = "txd:0", amountSats = 21_000UL, isConfirmed = false)
        filterCoinControlUtxos(listOf(confirmed, pending), "50000").shouldContainExactly(confirmed)
        filterCoinControlUtxos(listOf(confirmed, pending), "pending").shouldContainExactly(pending)
        filterCoinControlUtxos(listOf(confirmed, pending), "zzz-no-match").shouldContainExactly(emptyList())
    }
})
