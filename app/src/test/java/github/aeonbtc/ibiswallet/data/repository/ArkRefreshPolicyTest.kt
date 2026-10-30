package github.aeonbtc.ibiswallet.data.repository

import github.aeonbtc.ibiswallet.data.model.ArkRefreshParticipation
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

    test("unknown fee cannot bypass the automatic fee cap even when due") {
        ArkRefreshPolicy.shouldSkipAutoRefreshForFee(
            feeSats = null,
            needsRefresh = true,
            maxFeeSats = 5_000L,
            dailyRemainingSats = 25_000L,
        ) shouldBe true
    }

    test("null fee skip stays in the soon window") {
        ArkRefreshPolicy.shouldSkipAutoRefreshForFee(
            feeSats = null,
            needsRefresh = false,
            maxFeeSats = 5_000L,
            dailyRemainingSats = 25_000L,
        ) shouldBe true
    }

    test("over-cap fee still skips when due") {
        ArkRefreshPolicy.shouldSkipAutoRefreshForFee(
            feeSats = 6_000L,
            needsRefresh = true,
            maxFeeSats = 5_000L,
            dailyRemainingSats = 25_000L,
        ) shouldBe true
    }

    fun participation(createdAtMs: Long, roundId: UInt? = null) =
        ArkRefreshParticipation(
            operationId = "op",
            vtxoIds = listOf("a"),
            roundId = roundId,
            createdAtMs = createdAtMs,
        )

    test("stale refresh is abandoned only when no round owns it and inputs stay spendable") {
        val now = 10_000_000L
        val old = now - ArkRefreshPolicy.STALE_PARTICIPATION_AGE_MS - 1
        ArkRefreshPolicy.shouldAbandonParticipation(
            participation = participation(old),
            liveRoundIds = emptySet(),
            pendingRoundInputIds = emptySet(),
            nowMs = now,
        ) shouldBe true
    }

    test("stale refresh is kept while a live round owns its inputs") {
        val now = 10_000_000L
        val old = now - ArkRefreshPolicy.STALE_PARTICIPATION_AGE_MS - 1
        ArkRefreshPolicy.shouldAbandonParticipation(
            participation = participation(old, roundId = 7u),
            liveRoundIds = setOf(7u),
            pendingRoundInputIds = emptySet(),
            nowMs = now,
        ) shouldBe false
        ArkRefreshPolicy.shouldAbandonParticipation(
            participation = participation(old),
            liveRoundIds = emptySet(),
            pendingRoundInputIds = setOf("a"),
            nowMs = now,
        ) shouldBe false
    }

    test("restored scheduled round without input ids clears once its round is gone") {
        val now = 10_000_000L
        val empty =
            ArkRefreshParticipation(
                operationId = "scheduled",
                vtxoIds = emptyList(),
                roundId = 9u,
                createdAtMs = now - ArkRefreshPolicy.STALE_PARTICIPATION_AGE_MS - 1,
            )
        ArkRefreshPolicy.shouldAbandonParticipation(
            participation = empty,
            liveRoundIds = setOf(9u),
            pendingRoundInputIds = emptySet(),
            nowMs = now,
        ) shouldBe false
        ArkRefreshPolicy.shouldAbandonParticipation(
            participation = empty,
            liveRoundIds = emptySet(),
            pendingRoundInputIds = emptySet(),
            nowMs = now,
        ) shouldBe true
    }

    test("stale refresh is abandoned after its inputs were spent elsewhere") {
        val now = 10_000_000L
        ArkRefreshPolicy.shouldAbandonParticipation(
            participation = participation(now - ArkRefreshPolicy.STALE_PARTICIPATION_AGE_MS - 1),
            liveRoundIds = emptySet(),
            pendingRoundInputIds = emptySet(),
            nowMs = now,
        ) shouldBe true
    }

    test("automatic cleanup waits out the registration grace period") {
        val now = 10_000_000L
        ArkRefreshPolicy.shouldAbandonParticipation(
            participation = participation(now - 1_000L),
            liveRoundIds = emptySet(),
            pendingRoundInputIds = emptySet(),
            nowMs = now,
        ) shouldBe false
        ArkRefreshPolicy.shouldAbandonParticipation(
            participation = participation(now - 1_000L),
            liveRoundIds = emptySet(),
            pendingRoundInputIds = emptySet(),
            nowMs = now,
            requireAge = false,
        ) shouldBe true
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
