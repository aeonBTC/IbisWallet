package github.aeonbtc.ibiswallet.data.repository

import github.aeonbtc.ibiswallet.data.model.ArkRefreshParticipation
import github.aeonbtc.ibiswallet.data.model.ArkVtxo

object ArkRefreshPolicy {
    /** A stale participation must be this old before automatic cleanup may drop it. */
    const val STALE_PARTICIPATION_AGE_MS: Long = 30L * 60L * 1000L

    /**
     * A participation is provably idle when Bark knows no live round tied to it
     * and none of its inputs is still held by a pending round. In that state no
     * round can still be waiting on this local row, so dropping it cannot hide
     * an in-flight refresh. The inputs may be spendable (the submission never
     * happened) or already spent (the round finished elsewhere); either way the
     * local reservation is stale.
     *
     * [requireAge] is false for the explicit user action (after live checks) and
     * true for automatic reconciliation, which waits out a possible ASP round
     * registration window.
     */
    fun shouldAbandonParticipation(
        participation: ArkRefreshParticipation,
        liveRoundIds: Set<UInt>,
        pendingRoundInputIds: Set<String>,
        nowMs: Long = System.currentTimeMillis(),
        minAgeMs: Long = STALE_PARTICIPATION_AGE_MS,
        requireAge: Boolean = true,
    ): Boolean {
        val ids = participation.vtxoIds.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (ids.isEmpty()) {
            // A restored scheduled round may have no input ids because Bark does
            // not lock them until the round starts. Keep it while its round is
            // live; once the round disappears there is nothing left to reserve.
            if (participation.roundId != null && participation.roundId in liveRoundIds) return false
            if (!requireAge) return true
            val createdAt = participation.createdAtMs.coerceAtLeast(0L)
            return nowMs - createdAt >= minAgeMs
        }
        if (participation.roundId != null && participation.roundId in liveRoundIds) return false
        if (ids.any { it in pendingRoundInputIds }) return false
        if (!requireAge) return true
        val createdAt = participation.createdAtMs.coerceAtLeast(0L)
        return nowMs - createdAt >= minAgeMs
    }

    data class PpmTier(
        val expiryBlocksThreshold: Int,
        val ppm: Long,
    )

    fun scheduledHeight(
        nextRequiredHeight: Int?,
        firstExpiryHeight: Int?,
        chainTipHeight: Int?,
        selectedVtxos: Collection<ArkVtxo>,
    ): Int? {
        val next = nextRequiredHeight?.takeIf { it > 0 } ?: return null
        val firstExpiry = firstExpiryHeight?.takeIf { it > next } ?: return null
        val tip = chainTipHeight ?: return null
        val selectedExpiry = selectedVtxos.minOfOrNull { it.expiryHeight } ?: return null
        val threshold = firstExpiry - next
        val height = selectedExpiry - threshold
        if (height <= tip || selectedVtxos.isEmpty()) return null
        return height.takeIf { scheduled -> selectedVtxos.all { it.expiryHeight > scheduled } }
    }

    fun shouldRunAutoRefresh(
        needsRefresh: Boolean,
        refreshSoon: Boolean,
    ): Boolean = needsRefresh || refreshSoon

    /**
     * Unknown fees require manual review. Automatic submissions must never bypass
     * the attempt/daily caps, including when inputs are already due.
     */
    fun shouldSkipAutoRefreshForFee(
        feeSats: Long?,
        needsRefresh: Boolean,
        maxFeeSats: Long,
        dailyRemainingSats: Long,
    ): Boolean {
        if (dailyRemainingSats <= 0L) return true
        if (feeSats == null || feeSats < 0L) return true
        return feeSats > maxFeeSats || feeSats > dailyRemainingSats
    }

    fun autoRefreshTargets(
        dueVtxos: Collection<ArkVtxo>,
        allVtxos: Collection<ArkVtxo>,
        firstExpiryHeight: Int?,
    ): List<ArkVtxo> =
        if (dueVtxos.isNotEmpty()) {
            dueVtxos.toList()
        } else {
            val firstExpiry = firstExpiryHeight ?: return emptyList()
            allVtxos.filter { it.expiryHeight == firstExpiry }
        }

    fun estimateScheduledFeeSats(
        vtxos: Collection<ArkVtxo>,
        scheduledHeight: Int,
        baseFeeSats: Long,
        tiers: Collection<PpmTier>,
    ): Long? {
        if (vtxos.isEmpty() || scheduledHeight <= 0 || baseFeeSats < 0L) return null
        val sorted = tiers.sortedBy { it.expiryBlocksThreshold }
        var numerator = java.math.BigInteger.ZERO
        for (vtxo in vtxos) {
            if (vtxo.amountSats < 0L) return null
            val expiryBlocks = (vtxo.expiryHeight - scheduledHeight).coerceAtLeast(0)
            val ppm =
                sorted.lastOrNull { expiryBlocks >= it.expiryBlocksThreshold }?.ppm
                    ?.takeIf { it >= 0L }
                    ?: 0L
            numerator +=
                java.math.BigInteger.valueOf(vtxo.amountSats)
                    .multiply(java.math.BigInteger.valueOf(ppm))
        }
        val million = java.math.BigInteger.valueOf(1_000_000L)
        val ppmFee = numerator.add(million - java.math.BigInteger.ONE).divide(million)
        if (ppmFee.bitLength() > 63) return null
        return runCatching { Math.addExact(baseFeeSats, ppmFee.toLong()) }.getOrNull()
    }
}
