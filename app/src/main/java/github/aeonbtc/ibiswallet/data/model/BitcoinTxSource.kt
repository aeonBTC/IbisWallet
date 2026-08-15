package github.aeonbtc.ibiswallet.data.model

/** Persisted L1 transaction source tags (SecureStorage KEY_TX_SOURCE_*). */
object BitcoinTxSource {
    /** Liquid Boltz/SideSwap chain swap (Ibis Swap screen). */
    const val CHAIN_SWAP = "CHAIN_SWAP"

    /** Center Swap control L1 leg (Liquid / Spark / Ark transfer). */
    const val CENTER_SWAP = "CENTER_SWAP"

    fun isSwapHistory(source: String?): Boolean {
        val normalized = source?.trim().orEmpty()
        return normalized.equals(CHAIN_SWAP, ignoreCase = true) ||
            normalized.equals(CENTER_SWAP, ignoreCase = true)
    }
}
