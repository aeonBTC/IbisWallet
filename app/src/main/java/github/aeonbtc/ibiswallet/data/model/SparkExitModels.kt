package github.aeonbtc.ibiswallet.data.model

/**
 * Unilateral exit (emergency recovery) models for Spark.
 *
 * A unilateral exit moves the Spark balance on-chain without operator
 * cooperation. The SDK only builds and signs the transaction set — the user
 * broadcasts each step manually (package pairs need a package-relay node).
 */
enum class SparkExitLeafScope {
    AUTO,
    SPECIFIC,
}

data class SparkExitBranchFunding(
    val leafId: String,
    val fundingSats: Long,
)

data class SparkExitQuotedLeaf(
    val leafId: String,
    val valueSats: Long,
)

data class SparkExitQuote(
    val leafIds: List<String>,
    val leaves: List<SparkExitQuotedLeaf>,
    val recoverableValueSats: Long,
    val totalFeeSats: Long,
    val fanoutFeeSats: Long,
    val singleUtxoFundingSats: Long,
    val perBranchFunding: List<SparkExitBranchFunding>,
    val feeRateSatPerVb: Long,
    val destination: String,
    /**
     * Fee split (Breez 0.25+): the CPFP part is paid from funding UTXOs and
     * does not reduce recovery; the sweep part is taken from the recovered
     * value, so `recoverableValueSats - sweepFeeSats` is what reaches
     * [destination] before unspent funding change is added back.
     */
    val cpfpFeeSats: Long = 0L,
    val sweepFeeSats: Long = 0L,
)

data class SparkExitFundingUtxo(
    val txid: String,
    val vout: UInt,
    val valueSats: Long,
    val scriptPubkeyHex: String,
    val signedInputWeight: Long,
)

/**
 * Live L1 wallet view of a UTXO, used to re-validate exit funding at build
 * time. The funding picker runs on a snapshot that can go stale (spent,
 * frozen, or reorged out from under the quote); the repository rejects builds
 * whose inputs are no longer usable instead of failing inside the SDK.
 */
data class SparkExitLiveUtxo(
    val outpoint: String,
    val isConfirmed: Boolean,
    val isFrozen: Boolean,
)

enum class SparkExitTxKind {
    FAN_OUT,
    NODE,
    REFUND,
    SWEEP,
}

enum class SparkExitTxStatus {
    CONFIRMED,
    READY,
    WAITING_FOR_DEPENDENCIES,
    WAITING_FOR_TIMELOCK,
    UNVERIFIED,
}

data class SparkExitTx(
    val txid: String,
    val txHex: String,
    val cpfpTxHex: String? = null,
    val kind: SparkExitTxKind,
    val nodeId: String? = null,
    val dependsOn: List<String> = emptyList(),
    val csvTimelockBlocks: UInt? = null,
    val status: SparkExitTxStatus = SparkExitTxStatus.READY,
    /** Chain height the tx confirmed at (CONFIRMED only, when reported). */
    val blockHeight: UInt? = null,
    /**
     * First block that can include the tx (WAITING_FOR_TIMELOCK only, when
     * the chain service could resolve it).
     */
    val spendableAtHeight: UInt? = null,
)

sealed interface SparkExitFlowState {
    data object Idle : SparkExitFlowState

    data object Quoting : SparkExitFlowState

    data class QuoteReady(
        val quote: SparkExitQuote,
    ) : SparkExitFlowState

    data object Building : SparkExitFlowState

    /**
     * Signed set ready. Statuses come from the SDK (`check_unilateral_exit`
     * refreshes them against the chain); there are no manual broadcast flags
     * — resending an already-sent step is harmless, so progress is chain
     * truth, never self-attested clicks.
     */
    data class InProgress(
        val txs: List<SparkExitTx>,
        val recoverableValueSats: Long,
        val totalFeeSats: Long,
        val feeRateSatPerVb: Long,
        val destination: String,
        val leafIds: List<String>,
        /** Fee split for the breakdown display (see [SparkExitQuote]). */
        val cpfpFeeSats: Long = 0L,
        val fanoutFeeSats: Long = 0L,
        val sweepFeeSats: Long = 0L,
    ) : SparkExitFlowState

    /**
     * The stored exit cannot finish as it stands (chain diverged: a foreign
     * spend, an external fee bump, or funding spent elsewhere). Funds are not
     * lost — rebuild the same leaves and the SDK picks up from wherever the
     * money is. Persisted progress is kept until a rebuild replaces it.
     * [quote] is the last checked context (leaves, destination, fee rate,
     * funding splits, amounts) the rebuild reuses.
     */
    data class RedoRequired(
        val reason: SparkExitRedoReason,
        val quote: SparkExitQuote,
    ) : SparkExitFlowState

    data class Completed(
        val sweepTxid: String?,
    ) : SparkExitFlowState

    data class Failed(
        val message: String,
    ) : SparkExitFlowState
}

enum class SparkExitRedoReason {
    /** Chain no longer matches the stored exit — rebuild to continue. */
    ON_CHAIN_STATE_DIVERGED,

    /** Pre-0.25 snapshot: transactions cannot be checked, rebuild instead. */
    SCHEMA_UPGRADED,
}
