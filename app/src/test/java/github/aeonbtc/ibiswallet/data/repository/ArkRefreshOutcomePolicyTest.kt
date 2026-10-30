package github.aeonbtc.ibiswallet.data.repository

import github.aeonbtc.ibiswallet.data.model.ArkMovement
import github.aeonbtc.ibiswallet.data.model.ArkRefreshParticipation
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class ArkRefreshOutcomePolicyTest : FunSpec({
    val participation = ArkRefreshParticipation("reviewed", listOf("input-a", "input-b"), roundId = 7u)
    fun movement(
        status: String,
        inputs: List<String>,
        outputs: List<String> = listOf("replacement"),
        id: Int = 7,
    ) = ArkMovement(id, status, "round", "refresh", 0, -100, 100,
            createdAt = "2026-10-19T00:00:00Z", updatedAt = "2026-10-19T00:10:00Z",
            completedAt = "2026-10-19T00:10:00Z", inputVtxoIds = inputs, outputVtxoIds = outputs)

    test("vanished inputs alone do not prove a refresh settled") {
        ArkRefreshOutcomePolicy.resolve(participation, emptyList(), emptySet()) shouldBe
            ArkRefreshOutcomePolicy.Outcome.PENDING
    }
    test("an unrelated completed round cannot complete the reviewed participation") {
        ArkRefreshOutcomePolicy.resolve(participation,
            listOf(movement("Successful", listOf("input-c"))), emptySet()) shouldBe
            ArkRefreshOutcomePolicy.Outcome.PENDING
    }
    test("a matching failed movement resolves failure even after its round row disappears") {
        ArkRefreshOutcomePolicy.resolve(participation,
            listOf(movement("Failed", participation.vtxoIds, emptyList())), participation.vtxoIds.toSet()) shouldBe
            ArkRefreshOutcomePolicy.Outcome.FAILED
    }
    test("a completed replacement with all reviewed inputs resolves settlement") {
        ArkRefreshOutcomePolicy.resolve(participation,
            listOf(movement("Successful", participation.vtxoIds)), emptySet()) shouldBe
            ArkRefreshOutcomePolicy.Outcome.CONFIRMED
    }

    test("a later successful retry beats an older failed movement for the same inputs") {
        ArkRefreshOutcomePolicy.resolve(
            participation,
            listOf(
                movement("Failed", participation.vtxoIds, emptyList(), id = 1),
                movement("Successful", participation.vtxoIds, id = 2),
            ),
            emptySet(),
        ) shouldBe ArkRefreshOutcomePolicy.Outcome.CONFIRMED
    }

    test("an older failed attempt cannot fail a newer pending retry") {
        ArkRefreshOutcomePolicy.resolve(
            participation,
            listOf(
                movement("Failed", participation.vtxoIds, emptyList(), id = 1),
                movement("Pending", participation.vtxoIds, emptyList(), id = 2),
            ),
            participation.vtxoIds.toSet(),
        ) shouldBe ArkRefreshOutcomePolicy.Outcome.PENDING
    }

    test("a newer failed retry overrides an older successful movement while inputs stay live") {
        ArkRefreshOutcomePolicy.resolve(
            participation,
            listOf(
                movement("Successful", participation.vtxoIds, id = 1),
                movement("Failed", participation.vtxoIds, emptyList(), id = 2),
            ),
            participation.vtxoIds.toSet(),
        ) shouldBe ArkRefreshOutcomePolicy.Outcome.FAILED
    }
})
