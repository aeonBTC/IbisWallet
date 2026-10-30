package github.aeonbtc.ibiswallet.data.repository

import github.aeonbtc.ibiswallet.data.model.ArkMovement
import github.aeonbtc.ibiswallet.data.model.ArkRefreshParticipation

internal object ArkRefreshOutcomePolicy {
    enum class Outcome { PENDING, CONFIRMED, FAILED }

    fun resolve(
        participation: ArkRefreshParticipation,
        movements: List<ArkMovement>,
        liveInputIds: Set<String>,
    ): Outcome {
        if (participation.vtxoIds.isEmpty()) return Outcome.PENDING
        val matching = movements.filter {
            participation.vtxoIds.all { id -> id in it.inputVtxoIds } &&
                (it.subsystemKind.contains("refresh", true) || it.subsystemName.contains("round", true))
        }
        // A retry leaves multiple movements for the same inputs. Bark movement ids
        // are monotonic, so only the newest one describes the current attempt. An
        // older failed attempt must not fail a retry that is still pending.
        val latest = matching.maxByOrNull { it.id } ?: return Outcome.PENDING
        val complete =
            !latest.completedAt.isNullOrBlank() && latest.outputVtxoIds.isNotEmpty() &&
                (latest.status.equals("Successful", true) || latest.status.equals("Completed", true))
        // Settlement with the inputs gone wins over any stale failure.
        if (complete && participation.vtxoIds.none { it in liveInputIds }) return Outcome.CONFIRMED
        if (latest.status.equals("Failed", true) || latest.status.equals("Canceled", true)) {
            return Outcome.FAILED
        }
        return Outcome.PENDING
    }
}
