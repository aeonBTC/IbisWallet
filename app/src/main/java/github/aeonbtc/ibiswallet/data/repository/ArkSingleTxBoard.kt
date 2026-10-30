package github.aeonbtc.ibiswallet.data.repository

import github.aeonbtc.ibiswallet.data.model.ArkTransferState
import github.aeonbtc.ibiswallet.data.model.SignedBoardFunding
import kotlin.coroutines.cancellation.CancellationException

/** Result of the build + register phases of a single-tx Ark self-board. */
sealed interface SingleTxBoardOutcome {
    /** Bark committed to [signed]; broadcast exactly that tx next. No fallback past this point. */
    data class Registered(val signed: SignedBoardFunding) : SingleTxBoardOutcome

    /**
     * Nothing was registered with Bark: use the legacy fund-then-board path.
     * [cause] is the build/register error to surface if no fallback is available.
     */
    data class Fallback(val cause: Throwable?) : SingleTxBoardOutcome

    /**
     * Bark registered a board for a different txid than the one we signed.
     * Fail closed: neither broadcast nor fall back to a second funding tx.
     */
    data object TxidMismatch : SingleTxBoardOutcome
}

/**
 * Build + sign the L1 funding tx and register it with Bark (`boardPsbt`).
 *
 * Ordering matters: Bark must commit to the exact signed txid before anything is
 * broadcast. Cancellation is always rethrown (never turned into a fallback): a
 * cancelled register call may still have succeeded server-side, and falling back
 * would broadcast a second, different funding tx.
 */
suspend fun planSingleTxBoard(
    funding: ArkTransferState.BoardFundingInfo?,
    build: suspend () -> SignedBoardFunding,
    register: suspend (signedPsbtBase64: String, funding: ArkTransferState.BoardFundingInfo) -> Result<String>,
): SingleTxBoardOutcome {
    if (funding == null) return SingleTxBoardOutcome.Fallback(null)
    val signed =
        try {
            build()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return SingleTxBoardOutcome.Fallback(e)
        }
    val boardTxid =
        try {
            register(signed.signedPsbtBase64, funding).getOrThrow()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return SingleTxBoardOutcome.Fallback(e)
        }
    return if (boardTxid.equals(signed.txid, ignoreCase = true)) {
        SingleTxBoardOutcome.Registered(signed)
    } else {
        SingleTxBoardOutcome.TxidMismatch
    }
}
