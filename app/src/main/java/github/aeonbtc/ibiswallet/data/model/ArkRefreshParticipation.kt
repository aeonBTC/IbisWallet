package github.aeonbtc.ibiswallet.data.model

/** Persisted before submission; null round ID means the submission outcome is unresolved. */
data class ArkRefreshParticipation(
    val operationId: String,
    val vtxoIds: List<String>,
    val roundId: UInt? = null,
    val scheduledHeight: Int? = null,
    val automatic: Boolean = false,
    /** Wall-clock creation time. Legacy rows load with 0 so stale cleanup can consider them immediately. */
    val createdAtMs: Long = 0L,
)
