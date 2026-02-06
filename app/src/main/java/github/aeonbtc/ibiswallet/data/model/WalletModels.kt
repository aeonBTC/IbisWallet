package github.aeonbtc.ibiswallet.data.model

/**
 * Address type for the wallet (determines derivation path and address format)
 */
enum class AddressType(
    val displayName: String,
    val description: String,
    val bip: Int,
    val defaultPath: String
) {
    LEGACY(
        displayName = "Legacy",
        description = "Legacy addresses start with a '1'",
        bip = 44,
        defaultPath = "m/44'/0'/0'/0"
    ),
    SEGWIT(
        displayName = "SegWit",
        description = "SegWit addresses start with 'bc1q'",
        bip = 84,
        defaultPath = "m/84'/0'/0'/0"
    ),
    TAPROOT(
        displayName = "Taproot",
        description = "Taproot addresses start with 'bc1p'",
        bip = 86,
        defaultPath = "m/86'/0'/0'/0"
    )
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
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Address with optional label for display
 */
data class ReceiveAddressInfo(
    val address: String,
    val label: String? = null,
    val isUsed: Boolean = false
)

/**
 * Represents the current state of the wallet
 */
data class WalletState(
    val isInitialized: Boolean = false,
    val wallets: List<StoredWallet> = emptyList(),
    val activeWallet: StoredWallet? = null,
    val balanceSats: ULong = 0UL,
    val pendingBalanceSats: ULong = 0UL,
    val transactions: List<TransactionDetails> = emptyList(),
    val currentAddress: String? = null,
    val currentAddressInfo: ReceiveAddressInfo? = null,
    val isSyncing: Boolean = false,
    val lastSyncTimestamp: Long? = null,
    val error: String? = null
)

/**
 * Transaction details for display
 */
data class TransactionDetails(
    val txid: String,
    val amountSats: Long, // Positive for received, negative for sent
    val fee: ULong?,
    val vsize: ULong? = null, // Virtual size in vBytes for fee rate calculation
    val confirmationTime: ConfirmationTime?,
    val isConfirmed: Boolean,
    val timestamp: Long?,
    val address: String? = null, // Recipient address (for sent) or receiving address (for received)
    val addressAmount: ULong? = null, // Amount sent to/received at the address
    val changeAddress: String? = null, // Change address for sent transactions (if applicable)
    val changeAmount: ULong? = null, // Amount returned as change
    val isSelfTransfer: Boolean = false // True when sending to yourself (all outputs are yours)
) {
    /**
     * Calculate fee rate in sat/vB
     */
    val feeRate: Double?
        get() = if (fee != null && vsize != null && vsize > 0UL) {
            fee.toDouble() / vsize.toDouble()
        } else null
}

/**
 * Confirmation time information
 */
data class ConfirmationTime(
    val height: UInt,
    val timestamp: ULong
)

/**
 * Network type for the wallet
 */
enum class WalletNetwork {
    BITCOIN,
    TESTNET,
    SIGNET,
    REGTEST
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
    val useTor: Boolean = false
) {
    companion object {
        const val TOR_SOCKS_PROXY = "127.0.0.1:9050"
    }
    
    /**
     * Get the clean host URL (strip any protocol prefix)
     */
    private fun cleanUrl(): String {
        return url
            .removePrefix("tcp://")
            .removePrefix("ssl://")
            .removePrefix("http://")
            .removePrefix("https://")
            .trim()
            .trimEnd('/')
    }
    
    /**
     * Generate connection string for BDK ElectrumClient
     * Format: protocol://host:port
     */
    fun toConnectionString(): String {
        val protocol = if (useSsl) "ssl" else "tcp"
        val host = cleanUrl()
        return "$protocol://$host:$port"
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
    
    /**
     * Check if this config requires Tor to work
     * .onion addresses always require Tor
     */
    fun requiresTor(): Boolean {
        return isOnionAddress()
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
    val keyMaterial: String, // Mnemonic or extended public key
    val addressType: AddressType = AddressType.SEGWIT,
    val passphrase: String? = null,
    val customDerivationPath: String? = null,
    val network: WalletNetwork = WalletNetwork.BITCOIN,
    val isWatchOnly: Boolean = false
)

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
    val isUsed: Boolean = false
)

/**
 * Keychain type (receive or change)
 */
enum class KeychainType {
    EXTERNAL,  // Receive addresses
    INTERNAL   // Change addresses
}

/**
 * UTXO (Unspent Transaction Output) details
 */
data class UtxoInfo(
    val outpoint: String,  // txid:vout format
    val txid: String,
    val vout: UInt,
    val address: String,
    val amountSats: ULong,
    val label: String? = null,
    val isConfirmed: Boolean,
    val confirmations: UInt = 0u,
    val isFrozen: Boolean = false
)

/**
 * Fee rate estimates from mempool API
 */
data class FeeEstimates(
    val fastestFee: Double,    // ~1 block target
    val halfHourFee: Double,   // ~3 blocks target
    val hourFee: Double,       // ~6 blocks target
    val minimumFee: Double,    // Minimum relay fee
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Result wrapper for fee estimation
 */
sealed class FeeEstimationResult {
    data class Success(val estimates: FeeEstimates) : FeeEstimationResult()
    data class Error(val message: String) : FeeEstimationResult()
    data object Loading : FeeEstimationResult()
    data object Disabled : FeeEstimationResult()
}
