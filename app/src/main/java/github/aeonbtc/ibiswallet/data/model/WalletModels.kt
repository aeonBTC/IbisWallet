package github.aeonbtc.ibiswallet.data.model

/**
 * Address type for the wallet (determines derivation path and address format)
 */
enum class AddressType(
    val displayName: String,
    val description: String,
    val defaultPath: String,
    val accountPath: String, // Account-level derivation path without m/ prefix (for descriptor origin)
) {
    LEGACY(
        displayName = "Legacy",
        description = "P2PKH - Legacy addresses start with '1'",
        defaultPath = "m/44'/0'/0'/0",
        accountPath = "44'/0'/0'",
    ),
    NESTED_SEGWIT(
        displayName = "Wrapped",
        description = "P2SH-P2WPKH - Wrapped SegWit addresses start with '3'",
        defaultPath = "m/49'/0'/0'/0",
        accountPath = "49'/0'/0'",
    ),
    SEGWIT(
        displayName = "SegWit",
        description = "P2WPKH - Native SegWit addresses start with 'bc1q'",
        defaultPath = "m/84'/0'/0'/0",
        accountPath = "84'/0'/0'",
    ),
    TAPROOT(
        displayName = "Taproot",
        description = "P2TR - Taproot addresses start with 'bc1p'",
        defaultPath = "m/86'/0'/0'/0",
        accountPath = "86'/0'/0'",
    ),
}

/**
 * Stored wallet metadata
 */
data class StoredWallet(
    val id: String,
    val name: String,
    val addressType: AddressType,
    val derivationPath: String,
    val isWatchOnly: Boolean = false,
    val network: WalletNetwork = WalletNetwork.BITCOIN,
    val createdAt: Long = System.currentTimeMillis(),
    val masterFingerprint: String? = null, // Master key fingerprint (8 hex chars) for watch-only wallets
)

/**
 * Address with optional label for display
 */
data class ReceiveAddressInfo(
    val address: String,
    val label: String? = null,
    val isUsed: Boolean = false,
)

/**
 * Represents the current state of the wallet
 */
data class WalletState(
    val isInitialized: Boolean = false,
    val wallets: List<StoredWallet> = emptyList(),
    val activeWallet: StoredWallet? = null,
    val balanceSats: ULong = 0UL,
    val pendingIncomingSats: ULong = 0UL,
    val pendingOutgoingSats: ULong = 0UL,
    val transactions: List<TransactionDetails> = emptyList(),
    val currentAddress: String? = null,
    val currentAddressInfo: ReceiveAddressInfo? = null,
    val isSyncing: Boolean = false,
    val isFullSyncing: Boolean = false,
    val syncProgress: SyncProgress? = null,
    val lastSyncTimestamp: Long? = null,
    val blockHeight: UInt? = null,
    val error: String? = null,
)

/**
 * Granular sync progress across the entire sync pipeline.
 *
 * Phases (with approximate overall progress weights):
 *   0%–50%  Scanning addresses (BDK inspectSpks callback)
 *  50%–60%  Applying updates
 *  60%–70%  Saving to database
 *  70%–90%  Processing transactions (expensive for large wallets)
 *  90%–100% Refreshing address cache
 */
data class SyncProgress(
    val current: ULong = 0UL,
    val total: ULong = 0UL,
    val keychain: String? = null,
    /** Human-readable label for the current pipeline step */
    val status: String? = null,
)

/**
 * Transaction details for display
 */
data class TransactionDetails(
    val txid: String,
    val amountSats: Long, // Positive for received, negative for sent
    val fee: ULong?,
    val weight: ULong? = null, // Transaction weight in WU for fee rate calculation
    val confirmationTime: ConfirmationTime?,
    val isConfirmed: Boolean,
    val timestamp: Long?,
    val address: String? = null, // Recipient address (for sent) or receiving address (for received)
    val addressAmount: ULong? = null, // Amount sent to/received at the address
    val changeAddress: String? = null, // Change address for sent transactions (if applicable)
    val changeAmount: ULong? = null, // Amount returned as change
    val isSelfTransfer: Boolean = false, // True when sending to yourself (all outputs are yours)
) {
    /** Ceiled vsize = ceil(weight / 4) matching Bitcoin Core / mempool.space */
    val vsize: Double?
        get() = if (weight != null && weight > 0UL) kotlin.math.ceil(weight.toDouble() / 4.0) else null

    /** Fee rate in sat/vB using ceiled vsize */
    val feeRate: Double?
        get() {
            val vs = vsize
            return if (fee != null && vs != null && vs > 0.0) fee.toDouble() / vs else null
        }
}

/**
 * Confirmation time information
 */
data class ConfirmationTime(
    val height: UInt,
    val timestamp: ULong,
)

/**
 * Network type for the wallet
 */
enum class WalletNetwork {
    BITCOIN,
    TESTNET,
    SIGNET,
    REGTEST,
}

/**
 * Electrum server configuration
 */
data class ElectrumConfig(
    val id: String? = null,
    val name: String? = null,
    val url: String,
    val port: Int = 50001,
    val useSsl: Boolean = false,
    val useTor: Boolean = false,
) {
    /**
     * Get the clean host URL (strip any protocol prefix)
     */
    fun cleanUrl(): String {
        return url
            .removePrefix("tcp://")
            .removePrefix("ssl://")
            .removePrefix("http://")
            .removePrefix("https://")
            .trim()
            .trimEnd('/')
    }

    /**
     * Generate a display name if none provided
     */
    fun displayName(): String {
        return if (!name.isNullOrBlank()) name else "${cleanUrl()}:$port"
    }

    /**
     * Check if this is an onion address
     */
    fun isOnionAddress(): Boolean {
        return cleanUrl().endsWith(".onion")
    }
}

/**
 * Result wrapper for wallet operations
 */
sealed class WalletResult<out T> {
    data class Success<T>(val data: T) : WalletResult<T>()

    data class Error(val message: String, val exception: Throwable? = null) : WalletResult<Nothing>()
}

/**
 * Import wallet configuration
 */
data class WalletImportConfig(
    val name: String,
    val keyMaterial: String, // Mnemonic, extended public key, WIF private key, or Bitcoin address
    val addressType: AddressType = AddressType.SEGWIT,
    val passphrase: String? = null,
    val customDerivationPath: String? = null,
    val network: WalletNetwork = WalletNetwork.BITCOIN,
    val isWatchOnly: Boolean = false,
    val masterFingerprint: String? = null, // Master key fingerprint (8 hex chars) for hardware wallet PSBT signing
) {
    /** Redact sensitive fields to prevent accidental logging of key material. */
    override fun toString(): String =
        "WalletImportConfig(name=$name, addressType=$addressType, network=$network, " +
            "isWatchOnly=$isWatchOnly, hasPassphrase=${passphrase != null}, " +
            "keyMaterial=[REDACTED ${keyMaterial.length} chars])"
}

/**
 * Wallet address with details for display
 */
data class WalletAddress(
    val address: String,
    val index: UInt,
    val keychain: KeychainType,
    val label: String? = null,
    val balanceSats: ULong = 0UL,
    val transactionCount: Int = 0,
    val isUsed: Boolean = false,
)

/**
 * Keychain type (receive or change)
 */
enum class KeychainType {
    EXTERNAL, // Receive addresses
    INTERNAL, // Change addresses
}

/**
 * UTXO (Unspent Transaction Output) details
 */
data class UtxoInfo(
    val outpoint: String, // txid:vout format
    val txid: String,
    val vout: UInt,
    val address: String,
    val amountSats: ULong,
    val label: String? = null,
    val isConfirmed: Boolean,
    val isFrozen: Boolean = false,
)

/**
 * Fee rate estimates from mempool API
 */
data class FeeEstimates(
    val fastestFee: Double, // High priority target
    val halfHourFee: Double, // Medium priority target
    val hourFee: Double, // Low priority target
    val minimumFee: Double, // Minimum/economy fee
    val timestamp: Long = System.currentTimeMillis(),
    val source: FeeEstimateSource = FeeEstimateSource.MEMPOOL_SPACE,
) {
    /** True when all priority levels report the same rate (common in low-fee mempools via Electrum) */
    val isUniform: Boolean
        get() = fastestFee == halfHourFee && halfHourFee == hourFee
}

/**
 * Where the fee estimates came from — affects UI labels
 */
enum class FeeEstimateSource {
    MEMPOOL_SPACE,
    ELECTRUM_SERVER,
}

/**
 * Result wrapper for fee estimation
 */
sealed class FeeEstimationResult {
    data class Success(val estimates: FeeEstimates) : FeeEstimationResult()

    data class Error(val message: String) : FeeEstimationResult()

    data object Loading : FeeEstimationResult()

    data object Disabled : FeeEstimationResult()
}

/**
 * A single recipient in a transaction (address + amount).
 * Used for both single and multi-recipient sends.
 */
data class Recipient(
    val address: String,
    val amountSats: ULong,
)

/**
 * Details extracted from a created PSBT for display purposes.
 * Contains the actual fee/amounts computed by BDK's TxBuilder,
 * which may differ from the client-side estimates shown on the send screen.
 */
data class PsbtDetails(
    val psbtBase64: String,
    val feeSats: ULong,
    val recipientAddress: String,
    val recipientAmountSats: ULong,
    val changeAmountSats: ULong?,
    val totalInputSats: ULong,
)

/**
 * Result of a dry-run transaction build for accurate fee estimation.
 * Built by BDK's TxBuilder without signing or broadcasting.
 */
data class DryRunResult(
    val feeSats: Long,
    val changeSats: Long,
    val hasChange: Boolean,
    val numInputs: Int,
    val txVBytes: Double,
    val effectiveFeeRate: Double,
    val recipientAmountSats: Long,
    val error: String? = null, // Non-null when the dry-run failed (e.g. insufficient funds)
) {
    val isError: Boolean get() = error != null

    companion object {
        /** Create an error-only result when TxBuilder.finish() fails */
        fun error(message: String) =
            DryRunResult(
                feeSats = 0L,
                changeSats = 0L,
                hasChange = false,
                numInputs = 0,
                txVBytes = 0.0,
                effectiveFeeRate = 0.0,
                recipientAmountSats = 0L,
                error = message,
            )
    }
}
