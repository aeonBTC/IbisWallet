package github.aeonbtc.ibiswallet.data.model

/**
 * Ark (Bark) wallet models. Mainnet-only Layer 2 provider.
 *
 * Balance categories mirror Bark's Balance breakdown. Movement rows are wallet
 * history entries from the Bark DB (not on-chain txs).
 */

object ArkDefaults {
    const val SERVER_ADDRESS = "https://ark.second.tech"
    /** Default Esplora for Ark — same operator as the ASP. */
    const val ESPLORA_ADDRESS = "https://mempool.second.tech/api"
    const val ESPLORA_MEMPOOL_SPACE = "https://mempool.space/api"
    const val ESPLORA_MEMPOOL_ONION =
        "http://mempoolhqx4isw62xs7abwphsq7ldayuidyx2v2oethdhhj6mlo2r6ad.onion/api"
    const val USER_AGENT = "ibis-wallet/5.0"

    /** Clearnet fallbacks only — onion is opt-in (needs Tor). Preferred first. */
    val ESPLORA_FALLBACKS: List<String> =
        listOf(
            ESPLORA_ADDRESS,
            ESPLORA_MEMPOOL_SPACE,
            "https://mempool.emzy.de/api",
        )
}

/** Preset Esplora endpoints shown in the Ark connection picker. */
enum class ArkEsploraPreset(
    val id: String,
    val label: String,
    val url: String,
    val requiresTor: Boolean = false,
) {
    SECOND_TECH(
        id = "second_tech",
        label = "mempool.second.tech",
        url = ArkDefaults.ESPLORA_ADDRESS,
    ),
    MEMPOOL_SPACE(
        id = "mempool_space",
        label = "mempool.space",
        url = ArkDefaults.ESPLORA_MEMPOOL_SPACE,
    ),
    MEMPOOL_ONION(
        id = "mempool_onion",
        label = "mempool.space (Onion)",
        url = ArkDefaults.ESPLORA_MEMPOOL_ONION,
        requiresTor = true,
    ),
    MEMPOOL_EMZY(
        id = "mempool_emzy",
        label = "mempool.emzy.de",
        url = "https://mempool.emzy.de/api",
    ),
    ;

    companion object {
        fun match(url: String): ArkEsploraPreset? {
            val normalized = url.trim().trimEnd('/').lowercase()
            return entries.firstOrNull { it.url.trimEnd('/').lowercase() == normalized }
        }
    }
}

data class ArkServerConfig(
    val serverAddress: String = ArkDefaults.SERVER_ADDRESS,
    val esploraAddress: String = ArkDefaults.ESPLORA_ADDRESS,
)

data class ArkWalletState(
    val walletId: String? = null,
    val isInitialized: Boolean = false,
    /** Balance sync spinner — mailbox, ASP hydrate, refresh. Not the connection pill. */
    val isSyncing: Boolean = false,
    /** Connection pill — ASP / session liveness only. */
    val isConnected: Boolean = false,
    val isConnecting: Boolean = false,
    val fingerprint: String? = null,
    val spendableSats: Long = 0,
    val pendingInRoundSats: Long = 0,
    val pendingBoardSats: Long = 0,
    val pendingExitSats: Long = 0,
    val pendingLightningSendSats: Long = 0,
    val claimableLightningReceiveSats: Long = 0,
    /** Confirmed BTC sitting in Bark's on-chain wallet waiting to board into VTXOs. */
    val onchainConfirmedSats: Long = 0,
    /** Unconfirmed BTC on Bark's deposit address (L1 funding not yet confirmed/visible). */
    val onchainPendingSats: Long = 0,
    /** Unspent outputs on Bark deposit addresses (Esplora); empty until refreshed. */
    val onchainUtxos: List<ArkOnchainUtxo> = emptyList(),
    val movements: List<ArkMovement> = emptyList(),
    val vtxos: List<ArkVtxo> = emptyList(),
    val vtxosToRefresh: List<ArkVtxo> = emptyList(),
    /** Near-expiry VTXOs (soft warning before required refresh). */
    val expiringSoonVtxos: List<ArkVtxo> = emptyList(),
    val exitVtxos: List<ArkExitVtxo> = emptyList(),
    val claimableExitVtxos: List<ArkExitVtxo> = emptyList(),
    val hasPendingExits: Boolean = false,
    val nextRefreshHeight: Int? = null,
    val firstExpiringHeight: Int? = null,
    /** Optional tip estimate (when known from exit progress / cache); used for distance UI. */
    val chainTipHeight: Int? = null,
    val isAutoRefreshing: Boolean = false,
    /**
     * VTXO ids submitted for a refresh round that has not finished yet.
     * Used for list badges and to block another manual refresh of the same outputs.
     */
    val pendingRefreshVtxoIds: List<String> = emptyList(),
    /** Scheduled height for [pendingRefreshVtxoIds] when using height-priced delegated refresh. */
    val pendingRefreshScheduledHeight: Int? = null,
    val currentAddress: String? = null,
    val serverAddress: String? = null,
    val lastSyncTimestamp: Long = 0,
    val error: String? = null,
    val backupConfigured: Boolean = false,
    /**
     * True after this session successfully hydrated balance/history from the ASP
     * (refreshServer + sync / mailbox). Local Bark DB and SecureStorage cache are
     * paint-only until this is true. External arkdb backups are disaster-only.
     */
    val aspHydrated: Boolean = false,
    /** ASP min board (deposit) amount from [uniffi.bark.ArkInfo.minBoardAmountSats]; null until known. */
    val minBoardAmountSats: Long? = null,
    /**
     * ASP required on-chain confirmations before a deposit can board
     * ([uniffi.bark.ArkInfo.requiredBoardConfirmations]); null until known.
     */
    val requiredBoardConfirmations: Int? = null,
) {
    val onchainTotalSats: Long
        get() = onchainConfirmedSats + onchainPendingSats

    /**
     * Header balance. Spendable + pending-in-round should be disjoint; during a refresh round
     * Bark can list the same VTXO in both — cap that pair by the live VTXO sum.
     */
    val totalSats: Long
        get() {
            val spendable = spendableSats.coerceAtLeast(0L)
            val inRound = pendingInRoundSats.coerceAtLeast(0L)
            val lockedAndSpendable = spendable + inRound
            val vtxoSum = vtxos.sumOf { it.amountSats.coerceAtLeast(0L) }
            val offchainCore =
                if (vtxoSum > 0L && lockedAndSpendable > vtxoSum) {
                    vtxoSum
                } else {
                    lockedAndSpendable
                }
            return offchainCore +
                pendingBoardSats.coerceAtLeast(0L) +
                pendingExitSats.coerceAtLeast(0L) +
                pendingLightningSendSats.coerceAtLeast(0L) +
                claimableLightningReceiveSats.coerceAtLeast(0L) +
                onchainTotalSats
        }

    /** Due for refresh now — Bark [getVtxosToRefresh] (~144 blocks before expiry on mainnet). */
    val needsRefresh: Boolean
        get() = vtxosToRefresh.isNotEmpty()

    /** Recommended soon — not due yet, but nearing the refresh window. */
    val refreshSoon: Boolean
        get() =
            !needsRefresh &&
                (
                    expiringSoonVtxos.isNotEmpty() ||
                        blocksUntilRequiredRefresh?.let { it in 1..REFRESH_SOON_BLOCKS } == true
                )

    val blocksUntilRequiredRefresh: Int?
        get() {
            val next = nextRefreshHeight ?: return null
            val tip = chainTipHeight ?: return null
            return (next - tip).coerceAtLeast(0)
        }

    val hasClaimableExits: Boolean
        get() = claimableExitVtxos.isNotEmpty()

    val hasInboundDeposit: Boolean
        get() = onchainTotalSats > 0L || pendingBoardSats > 0L

    /** Funds, VTXOs, movements, or exits — anything that warrants an Ark DB backup. */
    val hasVtxoActivity: Boolean
        get() =
            totalSats > 0L ||
                vtxos.isNotEmpty() ||
                movements.isNotEmpty() ||
                exitVtxos.isNotEmpty()

    companion object {
        /** Near-expiry scan window (~1 day); aligns with Bark mainnet refresh threshold. */
        const val EXPIRING_SOON_THRESHOLD_BLOCKS: Int = 144

        /** “Recommended” banner: blocks until refresh becomes due. */
        const val REFRESH_SOON_BLOCKS: Int = 72
    }
}

data class ArkMovement(
    val id: Int,
    val status: String,
    val subsystemName: String,
    val subsystemKind: String,
    val intendedBalanceSats: Long,
    val effectiveBalanceSats: Long,
    val offchainFeeSats: Long,
    val sentToAddresses: List<String> = emptyList(),
    val receivedOnAddresses: List<String> = emptyList(),
    val createdAt: String,
    val updatedAt: String,
    val completedAt: String? = null,
    val paymentHash: String? = null,
    val lightningInvoice: String? = null,
    val lightningOffer: String? = null,
    val label: String? = null,
    /** Raw Bark movement metadata JSON (txid / vtxo ids / fees when present). */
    val metadataJson: String? = null,
    /** On-chain funding or exit txids parsed from metadata / Ibis L1 funding handoff. */
    val onchainTxids: List<String> = emptyList(),
    val inputVtxoIds: List<String> = emptyList(),
    val outputVtxoIds: List<String> = emptyList(),
    /** Optional on-chain fee sats when disclosed in metadata (else may be root offchainFeeSats). */
    val onchainFeeSats: Long? = null,
    /** Bark-created board transaction, distinct from the external funding transaction. */
    val boardTxid: String? = null,
    /** Confirmations of the external transaction paying the Bark deposit address. */
    val fundingConfirmations: Int? = null,
    /** Actual confirmations of [boardTxid], fetched from the active Ark Esplora. */
    val boardConfirmations: Int? = null,
    /** Confirmation target advertised by the connected ASP. */
    val requiredBoardConfirmations: Int? = null,
) {
    fun displayBalanceSats(): Long =
        when {
            effectiveBalanceSats != 0L -> effectiveBalanceSats
            intendedBalanceSats != 0L -> intendedBalanceSats
            else -> 0L
        }
}

/** Final on-chain claim broadcast created after a unilateral exit becomes claimable. */
data class ArkExitClaimHistory(
    val txid: String,
    val destinationAddress: String,
    val amountSats: Long,
    val feeSats: Long,
    val vtxoIds: List<String>,
    val createdAt: String,
)

/** Unspent output on a Bark on-chain deposit address (Esplora-sourced). */
data class ArkOnchainUtxo(
    val txid: String,
    val vout: Int,
    val amountSats: Long,
    val confirmations: Int,
    val address: String,
    val isConfirmed: Boolean,
) {
    val outpoint: String
        get() = "$txid:$vout"
}

/**
  * Below-min Bark on-chain deposit that was swept back to Layer 1 (not boarded).
  * Kept so history shows the outcome after the synthetic pending row is gone.
  */
data class ArkRecoveredOnchainDeposit(
    val fundingTxid: String,
    val amountSats: Long,
    val destinationAddress: String,
    val recoverTxid: String,
    val depositAddress: String? = null,
    val createdAt: String,
    val recoveredAt: String,
)

data class ArkVtxo(
    val id: String,
    val amountSats: Long,
    val expiryHeight: Int,
    val kind: String,
    val state: String,
    val exitDepth: Int,
    val exitTxWeightWu: Long,
    /** True once Bark has backed up this VTXO id and signed chain with the ASP. */
    val registered: Boolean = false,
)

data class ArkExitVtxo(
    val vtxoId: String,
    val amountSats: Long,
    val state: String,
    val isClaimable: Boolean,
)

data class ArkExitProgress(
    val vtxoId: String,
    val state: String,
    val error: String? = null,
)

enum class ArkReceiveKind {
    ARK_ADDRESS,
    BOLT11_INVOICE,
    BITCOIN_ADDRESS,
}

sealed interface ArkSendState {
    data object Idle : ArkSendState

    data object Preparing : ArkSendState

    data class Preview(
        val destination: String,
        val amountSats: Long?,
        val feeSats: Long?,
        val method: String,
        val netAmountSats: Long? = null,
        val useAllFunds: Boolean = false,
        val label: String? = null,
    ) : ArkSendState

    /** Sequential multi-payment preview (Arkoor addresses only). */
    data class MultiPreview(
        val items: List<MultiItem>,
        val totalAmountSats: Long,
        val totalFeeSats: Long,
        val label: String? = null,
    ) : ArkSendState {
        data class MultiItem(
            val destination: String,
            val amountSats: Long,
            val feeSats: Long,
        )
    }

    data object Sending : ArkSendState

    data class MultiSending(
        val completed: Int,
        val total: Int,
    ) : ArkSendState

    data class Sent(
        val detail: String? = null,
    ) : ArkSendState

    data class MultiSent(
        val succeeded: Int,
        val failed: Int,
        val detail: String? = null,
    ) : ArkSendState

    data class Error(
        val message: String,
    ) : ArkSendState
}

sealed interface ArkReceiveState {
    data object Idle : ArkReceiveState

    data object Loading : ArkReceiveState

    data class Ready(
        val kind: ArkReceiveKind,
        val paymentRequest: String,
        val amountSats: Long? = null,
        val feeSats: Long = 0,
        val paymentHash: String? = null,
    ) : ArkReceiveState

    data class Paid(
        val kind: ArkReceiveKind,
        val amountSats: Long,
        val paymentRequest: String? = null,
    ) : ArkReceiveState

    data class Error(
        val message: String,
    ) : ArkReceiveState
}

sealed interface ArkTransferState {
    data object Idle : ArkTransferState

    data object Preparing : ArkTransferState

    data class BoardPreview(
        val amountSats: Long,
        val feeSats: Long?,
        val netAmountSats: Long?,
        val boardAll: Boolean,
        /** Bark on-chain Bitcoin address used as the BTC→Ark deposit target. */
        val bitcoinDepositAddress: String? = null,
        /** sat/vB inferred from fee when weight is known; otherwise null. */
        val feeRateSatPerVb: Double? = null,
        val grossAmountSats: Long? = null,
    ) : ArkTransferState

    data class OffboardPreview(
        val destinationAddress: String,
        val amountSats: Long?,
        val feeSats: Long?,
        val netAmountSats: Long?,
        val offboardAll: Boolean,
        val feeRateSatPerVb: Double? = null,
        val grossAmountSats: Long? = null,
    ) : ArkTransferState

    data object InProgress : ArkTransferState

    data class Completed(
        val detail: String? = null,
    ) : ArkTransferState

    data class Error(
        val message: String,
    ) : ArkTransferState
}

sealed interface ArkLifecycleState {
    data object Idle : ArkLifecycleState

    data object Loading : ArkLifecycleState

    data class RefreshPreview(
        val vtxoIds: List<String>,
        val feeSats: Long?,
        val netAmountSats: Long?,
        val refreshAll: Boolean,
        /** When set, confirm uses Bark scheduled delegated refresh at this height. */
        val scheduledHeight: Int? = null,
    ) : ArkLifecycleState

    data class ExitStarted(
        val vtxoIds: List<String>,
        val entireWallet: Boolean,
    ) : ArkLifecycleState

    data class ExitProgressing(
        val statuses: List<ArkExitProgress>,
    ) : ArkLifecycleState

    data class ClaimPreview(
        val vtxoIds: List<String>,
        val destinationAddress: String,
        val feeSats: Long,
        val feeRateSatPerVb: Long,
        val psbtBase64: String,
    ) : ArkLifecycleState

    data object InProgress : ArkLifecycleState

    /** Bark accepted the delegated refresh; completion is tracked by normal wallet sync. */
    data class RefreshPending(
        val scheduledHeight: Int? = null,
        val vtxoIds: List<String> = emptyList(),
        /** True while the native round wait is still in flight (submitted, not finished). */
        val inFlight: Boolean = true,
    ) : ArkLifecycleState

    data class Completed(
        val detail: String? = null,
    ) : ArkLifecycleState

    data class Error(
        val message: String,
    ) : ArkLifecycleState
}

sealed class ArkEvent {
    data class PaymentReceived(
        val amountSats: Long,
        val movementId: Int? = null,
    ) : ArkEvent()

    /** VTXOs are due for refresh ([getVtxosToRefresh] non-empty). */
    data class NeedsRefresh(
        val vtxoCount: Int,
        val nextRefreshHeight: Int? = null,
        val autoStarted: Boolean = false,
    ) : ArkEvent()

    /** Refresh recommended: approaching the due window, not expired yet. */
    data class RefreshSoon(
        val vtxoCount: Int,
        val blocksRemaining: Int? = null,
    ) : ArkEvent()

    data class RefreshCompleted(
        val detail: String? = null,
        val automatic: Boolean = false,
        /** True when ASP-delegated path was preferred (auto refresh always). */
        val delegated: Boolean = true,
    ) : ArkEvent()

    data class RefreshSubmitted(
        val automatic: Boolean = false,
        val scheduledHeight: Int? = null,
        val vtxoCount: Int = 0,
    ) : ArkEvent()

    data class RefreshFailed(
        val message: String,
        val automatic: Boolean = false,
        val delegated: Boolean = true,
    ) : ArkEvent()

    data class ExitProgressUpdate(
        val statuses: List<ArkExitProgress>,
    ) : ArkEvent()

    /** Mailbox recovery / Ark full sync finished. */
    data class MailboxRecoveryCompleted(
        val supported: Boolean,
        val detail: String? = null,
    ) : ArkEvent()

    data class MailboxRecoveryFailed(
        val message: String,
        val supported: Boolean,
    ) : ArkEvent()

    data object ArkDbExported : ArkEvent()

    data object ArkDbImported : ArkEvent()

    data class ArkDbTransferFailed(
        val message: String,
    ) : ArkEvent()

    /** Silent-friendly: auto snapshot written after VTXO activity. */
    data class ArkDbAutoBackedUp(
        val info: ArkAutoDbBackupInfo,
    ) : ArkEvent()

    data class ArkDbAutoBackupFailed(
        val message: String,
    ) : ArkEvent()

    /**
     * Clearnet Esplora fell back from the configured URL to another host.
     * Onion is never included in silent fallback.
     */
    data class EsploraFallbackUsed(
        val configuredUrl: String,
        val activeUrl: String,
    ) : ArkEvent()

    /**
     * Seed wallet opened off-chain-only (Bark could not init its on-chain wallet on any
     * Esplora host). BTC→Ark boarding is disabled until a re-open succeeds.
     */
    data class OnchainUnavailable(
        val message: String,
    ) : ArkEvent()

    /**
     * Confirmed Bark on-chain deposit is below ASP min board amount.
     * [shortfallSats] is how much more is needed to meet the min (null if unknown).
     */
    data class BoardBelowMinimum(
        val onchainConfirmedSats: Long,
        val minBoardAmountSats: Long,
        val shortfallSats: Long?,
        val message: String,
    ) : ArkEvent()

    /** User or auto-board submitted a boardAll / boardAmount successfully. */
    data class BoardSucceeded(
        val amountSats: Long,
        val boardTxid: String? = null,
    ) : ArkEvent()

    data class BoardFailed(
        val message: String,
    ) : ArkEvent()
}

/** Latest external Ark DB auto-backup snapshot metadata for UI. */
data class ArkAutoDbBackupInfo(
    val fileName: String,
    val sizeBytes: Long,
    val timestampMs: Long,
    val snapshotCount: Int,
    /** True when ibis-ark-db-backup.zip is present alongside latest. */
    val hasBackupCopy: Boolean = snapshotCount >= 2,
)
