package github.aeonbtc.ibiswallet.data.repository

import github.aeonbtc.ibiswallet.data.model.ArkVtxo
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class ArkRefreshPolicyTest : FunSpec({
    fun vtxo(id: String, expiry: Int) =
        ArkVtxo(
            id = id,
            amountSats = 1_000,
            expiryHeight = expiry,
            kind = "round",
            state = "Spendable",
            exitDepth = 1,
            exitTxWeightWu = 400,
        )

    test("scheduled refresh uses a future required height valid for every selected output") {
        ArkRefreshPolicy.scheduledHeight(
            nextRequiredHeight = 900,
            firstExpiryHeight = 1_044,
            chainTipHeight = 850,
            selectedVtxos = listOf(vtxo("a", 1_044), vtxo("b", 1_100)),
        ) shouldBe 900
    }

    test("scheduled refresh rejects a height at an input expiry") {
        ArkRefreshPolicy.scheduledHeight(
            nextRequiredHeight = 900,
            firstExpiryHeight = 1_044,
            chainTipHeight = 850,
            selectedVtxos = listOf(vtxo("a", 144)),
        ) shouldBe null
    }

    test("automatic refresh runs in due and soon windows") {
        ArkRefreshPolicy.shouldRunAutoRefresh(needsRefresh = false, refreshSoon = true) shouldBe true
    }

    test("automatic refresh prefers Bark due selection") {
        val due = vtxo("due", 900)
        ArkRefreshPolicy.autoRefreshTargets(
            dueVtxos = listOf(due),
            allVtxos = listOf(vtxo("soon", 1_000)),
            firstExpiryHeight = 1_000,
        ) shouldBe listOf(due)
    }

    test("automatic refresh schedules outputs with the earliest expiry") {
        val earliest = vtxo("early", 1_000)
        ArkRefreshPolicy.autoRefreshTargets(
            dueVtxos = emptyList(),
            allVtxos = listOf(vtxo("later", 1_100), earliest),
            firstExpiryHeight = 1_000,
        ) shouldBe listOf(earliest)
    }

    test("scheduled fee uses fee tier at scheduled height and rounds once") {
        ArkRefreshPolicy.estimateScheduledFeeSats(
            vtxos = listOf(vtxo("a", 1_010), vtxo("b", 1_020)),
            scheduledHeight = 1_000,
            baseFeeSats = 5,
            tiers =
                listOf(
                    ArkRefreshPolicy.PpmTier(0, 1_000),
                    ArkRefreshPolicy.PpmTier(20, 2_000),
                ),
        ) shouldBe 8L
    }
})
