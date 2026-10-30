package github.aeonbtc.ibiswallet.data.repository

/**
 * Pure safety gate for discarding an unaccepted claim journal.
 *
 * The journal is written before broadcast. Discarding it is safe only when the
 * transaction is provably not on-chain and every journaled exit output is still
 * claimable. Any uncertainty keeps the journal: re-signing over a transaction
 * that may already have been accepted can strand the claim.
 */
object ArkClaimRecoveryPolicy {
    enum class Verdict {
        SAFE_TO_DISCARD,
        /** The journal is already marked accepted; never discard it silently. */
        BROADCAST_ACCEPTED,
        /** Signed bytes exist but no txid could be derived, so chain absence is unproven. */
        UNVERIFIABLE_SIGNED_TX,
        /** The txid is known to be on-chain. */
        ON_CHAIN,
        /** One or more journaled VTXOs are no longer claimable. */
        NOT_CLAIMABLE,
        /** No journal at all. */
        NONE,
    }

    fun verdict(
        busy: Boolean,
        broadcastAccepted: Boolean,
        signedHexPresent: Boolean,
        txidPresent: Boolean,
        txOnChain: Boolean?,
        vtxoIds: Collection<String>,
        claimableIds: Collection<String>,
    ): Verdict {
        if (txOnChain == true) return Verdict.ON_CHAIN
        if (broadcastAccepted) return Verdict.BROADCAST_ACCEPTED
        if (signedHexPresent && !txidPresent) return Verdict.UNVERIFIABLE_SIGNED_TX
        val ids = vtxoIds.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (ids.isEmpty()) return Verdict.NONE
        if (busy) return Verdict.NOT_CLAIMABLE
        val claimable = claimableIds.map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        if (ids.any { it !in claimable }) return Verdict.NOT_CLAIMABLE
        return Verdict.SAFE_TO_DISCARD
    }
}
