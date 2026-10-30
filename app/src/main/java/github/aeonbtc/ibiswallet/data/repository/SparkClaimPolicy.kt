package github.aeonbtc.ibiswallet.data.repository

import breez_sdk_spark.ClaimDeferredReason
import breez_sdk_spark.ClaimDepositOutcome
import breez_sdk_spark.ClaimDepositQuote
import breez_sdk_spark.InstantClaimStatus
import breez_sdk_spark.MaxFee
import github.aeonbtc.ibiswallet.data.model.SparkClaimDeferredReason
import github.aeonbtc.ibiswallet.data.model.SparkClaimOutcome
import github.aeonbtc.ibiswallet.data.model.SparkClaimStatus
import github.aeonbtc.ibiswallet.data.model.SparkDepositClaimQuote

/**
 * Pure decision/mapping logic for Spark deposit claims (Breez 0.26+).
 *
 * The SDK changed two behaviors under an unchanged method surface:
 * - `claim_deposit` returns a [ClaimDepositOutcome] (Settled / Submitted /
 *   Deferred) instead of failing declined claims — a Deferred outcome is
 *   ordinary, and any requested ceiling stands for later automatic attempts.
 * - Claimed deposits linger in `list_unclaimed_deposits` with an
 *   [InstantClaimStatus] until the provider spends the output — CLAIMED means
 *   credited, never awaiting action.
 */
object SparkClaimPolicy {
    fun claimStatusOf(sdkStatus: InstantClaimStatus?): SparkClaimStatus =
        when (sdkStatus) {
            null -> SparkClaimStatus.NONE
            is InstantClaimStatus.Submitted -> SparkClaimStatus.SUBMITTED
            InstantClaimStatus.Claimed -> SparkClaimStatus.CLAIMED
            is InstantClaimStatus.Declined -> SparkClaimStatus.DECLINED
            else -> SparkClaimStatus.NONE
        }

    /**
     * Per-deposit ceiling as fixed sats, when expressible. Rate and
     * network-recommended ceilings have no fixed amount — null means the
     * configured ceiling applies. Overflow coerces to null (unknown).
     */
    fun maxClaimFeeSatsOf(maxFee: MaxFee?): Long? {
        val fixed = maxFee as? MaxFee.Fixed ?: return null
        return fixed.amount.toLongOrNullSafe()?.takeIf { it > 0L }
    }

    fun deferredReasonOf(reason: ClaimDeferredReason): Pair<SparkClaimDeferredReason, Long?> =
        when (reason) {
            is ClaimDeferredReason.MaxFeeExceeded ->
                SparkClaimDeferredReason.MAX_FEE_EXCEEDED to reason.requiredFeeSats.toLongOrNullSafe()
            ClaimDeferredReason.NoEarlyClaimAvailable ->
                SparkClaimDeferredReason.NO_EARLY_CLAIM to null
            is ClaimDeferredReason.ProviderDeclined ->
                SparkClaimDeferredReason.PROVIDER_DECLINED to null
            else -> SparkClaimDeferredReason.PROVIDER_DECLINED to null
        }

    fun outcomeOf(outcome: ClaimDepositOutcome): SparkClaimOutcome =
        when (outcome) {
            is ClaimDepositOutcome.Settled -> SparkClaimOutcome.Settled(outcome.payment.id)
            ClaimDepositOutcome.Submitted -> SparkClaimOutcome.Submitted
            is ClaimDepositOutcome.Deferred -> {
                val (reason, requiredFeeSats) = deferredReasonOf(outcome.reason)
                SparkClaimOutcome.Deferred(reason, requiredFeeSats)
            }
            else -> SparkClaimOutcome.Deferred(SparkClaimDeferredReason.PROVIDER_DECLINED)
        }

    /**
     * Blocks until a quote depth arrives, from the deposit's current depth.
     * Depths are totals, not waits — clamp at zero, never wrap on UInt math.
     */
    fun blocksToWait(
        required: Long,
        confirmations: Long,
    ): Long = (required - confirmations).coerceAtLeast(0L)

    /**
     * Builds the UI quote from a fetch response. Prefers the early (instant /
     * expedited) offer; falls back to standard-claim terms when the provider
     * offers nothing early (matured, unoffered, unreachable) so the dialog can
     * still arm a ceiling instead of hiding. Null only when neither quote is
     * internally consistent — never paint corrupt numerics.
     */
    fun buildQuote(
        txid: String,
        vout: UInt,
        amountSats: Long,
        confirmations: Long,
        instant: ClaimDepositQuote?,
        mature: ClaimDepositQuote,
    ): SparkDepositClaimQuote? {
        // Overflow anywhere coerces the whole quote to null (hide, never paint
        // wrapped values — the claim-time ceiling re-checks anyway).
        val matureFeeSats = mature.feeSats.toLongOrNullSafe() ?: return null
        val matureConfReq = mature.confirmationsRequired.toLongOrNullSafe() ?: return null
        if (instant != null) {
            val creditSats = instant.creditAmountSats.toLongOrNullSafe() ?: return null
            val feeSats = instant.feeSats.toLongOrNullSafe() ?: return null
            if (!SparkUnilateralExitPolicy.isPlausibleClaimQuote(amountSats, creditSats, feeSats)) {
                return null
            }
            // The reviewed fee becomes the claim ceiling, and ceilings at or
            // above the deposit value are refused at claim time — hiding here
            // beats painting a Claim button that can only error.
            if (feeSats >= amountSats) return null
            return SparkDepositClaimQuote(
                txid = txid,
                vout = vout,
                amountSats = amountSats,
                confirmations = confirmations,
                creditSats = creditSats,
                feeSats = feeSats,
                feeRateSatPerVb = instant.feeRateSatPerVbyte.toLongOrNullSafe() ?: return null,
                isEstimate = instant.isEstimate,
                isEarlyOffer = true,
                instantConfirmationsRequired = instant.confirmationsRequired.toLongOrNullSafe(),
                matureFeeSats = matureFeeSats,
                matureConfirmationsRequired = matureConfReq,
                matureIsEstimate = mature.isEstimate,
            )
        }
        // Standard-claim fallback: the fee comes out of the deposit itself.
        val creditSats = (amountSats - matureFeeSats).coerceAtLeast(0L)
        if (!SparkUnilateralExitPolicy.isPlausibleClaimQuote(amountSats, creditSats, matureFeeSats)) {
            return null
        }
        return SparkDepositClaimQuote(
            txid = txid,
            vout = vout,
            amountSats = amountSats,
            confirmations = confirmations,
            creditSats = creditSats,
            feeSats = matureFeeSats,
            feeRateSatPerVb = mature.feeRateSatPerVbyte.toLongOrNullSafe() ?: return null,
            isEstimate = mature.isEstimate,
            isEarlyOffer = false,
            matureFeeSats = matureFeeSats,
            matureConfirmationsRequired = matureConfReq,
            matureIsEstimate = mature.isEstimate,
        )
    }

    private fun ULong.toLongOrNullSafe(): Long? =
        if (this <= Long.MAX_VALUE.toULong()) this.toLong() else null

    private fun UInt.toLongOrNullSafe(): Long? = this.toLong()
}
