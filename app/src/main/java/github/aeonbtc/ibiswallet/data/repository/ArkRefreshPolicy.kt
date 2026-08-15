package github.aeonbtc.ibiswallet.data.repository

import github.aeonbtc.ibiswallet.data.model.ArkVtxo

object ArkRefreshPolicy {
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
        return runCatching { Math.addExact(baseFeeSats, ppmFee.longValueExact()) }.getOrNull()
    }
}
