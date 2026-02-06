package github.aeonbtc.ibiswallet.data.repository

import android.content.Context
import android.util.Log
import github.aeonbtc.ibiswallet.data.ElectrumFeatureService
import github.aeonbtc.ibiswallet.data.local.SecureStorage
import github.aeonbtc.ibiswallet.data.model.*
import github.aeonbtc.ibiswallet.tor.TorProxyBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.bitcoindevkit.*
import org.rustbitcoin.bitcoin.Amount
import org.rustbitcoin.bitcoin.FeeRate
import org.rustbitcoin.bitcoin.Network
import java.io.File
import java.util.UUID

/**
 * Repository for managing Bitcoin wallet operations using BDK
 * Supports multiple wallets
 */
class WalletRepository(context: Context) {
    
    companion object {
        private const val TAG = "WalletRepository"
        private const val BDK_DB_DIR = "bdk"
    }
    
    private val secureStorage = SecureStorage(context)
    private val electrumFeatureService = ElectrumFeatureService()
    
    // Directory for BDK wallet databases (persistent cache)
    private val bdkDbDir: File = File(context.filesDir, BDK_DB_DIR).apply { mkdirs() }
    
    /**
     * Get the database file path for a wallet's persistent BDK storage
     */
    private fun getWalletDbPath(walletId: String): String {
        return File(bdkDbDir, "$walletId.db").absolutePath
    }
    
    /**
     * Delete the BDK database files for a wallet
     */
    private fun deleteWalletDatabase(walletId: String) {
        val dbFile = File(bdkDbDir, "$walletId.db")
        val walFile = File(bdkDbDir, "$walletId.db-wal")
        val shmFile = File(bdkDbDir, "$walletId.db-shm")
        
        dbFile.delete()
        walFile.delete()
        shmFile.delete()
        
        Log.d(TAG, "Deleted wallet database for $walletId")
    }
    
    // Current active BDK wallet instance
    private var wallet: Wallet? = null
    private var walletConnection: Connection? = null
    private var electrumClient: ElectrumClient? = null
    
    // Tor proxy bridge for .onion connections
    private var torProxyBridge: TorProxyBridge? = null
    
    // Minimum acceptable fee rate from connected Electrum server (sat/vB)
    private val _minFeeRate = MutableStateFlow(ElectrumFeatureService.DEFAULT_MIN_FEE_RATE)
    val minFeeRate: StateFlow<Double> = _minFeeRate.asStateFlow()
    
    private val _walletState = MutableStateFlow(WalletState())
    val walletState: StateFlow<WalletState> = _walletState.asStateFlow()
    
    /**
     * Check if any wallet has been initialized
     */
    fun isWalletInitialized(): Boolean {
        return secureStorage.getWalletIds().isNotEmpty()
    }
    
    /**
     * Check if a specific wallet has key material
     */
    fun hasKeyMaterial(walletId: String): Boolean {
        return secureStorage.hasMnemonic(walletId) || secureStorage.hasExtendedKey(walletId)
    }
    
    /**
     * Get all stored wallets
     */
    fun getAllWallets(): List<StoredWallet> {
        return secureStorage.getAllWallets()
    }
    
    /**
     * Get the active wallet ID
     */
    fun getActiveWalletId(): String? {
        return secureStorage.getActiveWalletId()
    }
    
    /**
     * Get the last full sync timestamp for a wallet
     * Returns null if never fully synced
     */
    fun getLastFullSyncTime(walletId: String): Long? {
        return secureStorage.getLastFullSyncTime(walletId)
    }
    
    /**
     * Data class for wallet key material
     */
    data class WalletKeyMaterial(
        val mnemonic: String?,
        val extendedPublicKey: String?,
        val isWatchOnly: Boolean
    )
    
    /**
     * Get the key material (mnemonic and/or extended public key) for a wallet
     * For full wallets: returns both mnemonic and derived xpub
     * For watch-only wallets: returns only xpub
     */
    fun getKeyMaterial(walletId: String): WalletKeyMaterial? {
        val storedWallet = secureStorage.getWalletMetadata(walletId) ?: return null
        
        // Check for extended key first (watch-only wallet)
        val extendedKey = secureStorage.getExtendedKey(walletId)
        if (extendedKey != null) {
            return WalletKeyMaterial(
                mnemonic = null,
                extendedPublicKey = extendedKey,
                isWatchOnly = true
            )
        }
        
        // Check for mnemonic (full wallet)
        val mnemonic = secureStorage.getMnemonic(walletId)
        if (mnemonic != null) {
            // Derive the extended public key from the mnemonic
            val xpub = try {
                deriveExtendedPublicKey(
                    mnemonic = mnemonic,
                    passphrase = secureStorage.getPassphrase(walletId),
                    addressType = storedWallet.addressType,
                    network = storedWallet.network
                )
            } catch (e: Exception) {
                null
            }
            
            return WalletKeyMaterial(
                mnemonic = mnemonic,
                extendedPublicKey = xpub,
                isWatchOnly = false
            )
        }
        
        return null
    }
    
    /**
     * Derive the extended public key from a mnemonic
     */
    private fun deriveExtendedPublicKey(
        mnemonic: String,
        passphrase: String?,
        addressType: AddressType,
        network: WalletNetwork
    ): String {
        val bdkNetwork = network.toBdkNetwork()
        val mnemonicObj = Mnemonic.fromString(mnemonic)
        val descriptorSecretKey = DescriptorSecretKey(
            network = bdkNetwork,
            mnemonic = mnemonicObj,
            password = passphrase
        )
        
        // Get the descriptor based on address type
        val descriptor = when (addressType) {
            AddressType.LEGACY -> Descriptor.newBip44(
                secretKey = descriptorSecretKey,
                keychain = KeychainKind.EXTERNAL,
                network = bdkNetwork
            )
            AddressType.SEGWIT -> Descriptor.newBip84(
                secretKey = descriptorSecretKey,
                keychain = KeychainKind.EXTERNAL,
                network = bdkNetwork
            )
            AddressType.TAPROOT -> Descriptor.newBip86(
                secretKey = descriptorSecretKey,
                keychain = KeychainKind.EXTERNAL,
                network = bdkNetwork
            )
        }
        
        // Convert to public descriptor and extract the xpub
        // The descriptor string format is like: wpkh([fingerprint/path]xpub.../0/*)
        val descriptorString = descriptor.toString()
        
        // Extract the xpub/tpub from the descriptor
        // Pattern: extract the key between ] and /
        val xpubPattern = """\]([xtyz]pub[a-zA-Z0-9]+)""".toRegex()
        val match = xpubPattern.find(descriptorString)
        
        return match?.groupValues?.get(1) ?: descriptorString
    }
    
    /**
     * Import a wallet with full configuration
     */
    suspend fun importWallet(
        config: WalletImportConfig
    ): WalletResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val bdkNetwork = config.network.toBdkNetwork()
            val isWatchOnly = config.keyMaterial.let {
                it.startsWith("xpub") || it.startsWith("ypub") || 
                it.startsWith("zpub") || it.startsWith("tpub")
            }
            
            val derivationPath = config.customDerivationPath ?: config.addressType.defaultPath
            val walletId = UUID.randomUUID().toString()
            
            val (externalDescriptor, internalDescriptor) = if (isWatchOnly) {
                // Watch-only wallet from extended public key
                createWatchOnlyDescriptors(config.keyMaterial, config.addressType, bdkNetwork)
            } else {
                // Full wallet from mnemonic
                createDescriptorsFromMnemonic(
                    mnemonic = config.keyMaterial,
                    passphrase = config.passphrase,
                    addressType = config.addressType,
                    network = bdkNetwork
                )
            }
            
            // Create wallet with persistent SQLite storage
            val connection = Connection(getWalletDbPath(walletId))
            wallet = Wallet(
                descriptor = externalDescriptor,
                changeDescriptor = internalDescriptor,
                network = bdkNetwork,
                connection = connection
            )
            walletConnection = connection
            
            // Persist the new wallet to database
            wallet!!.persist(connection)
            
            // Save wallet metadata
            val storedWallet = StoredWallet(
                id = walletId,
                name = config.name,
                addressType = config.addressType,
                derivationPath = derivationPath,
                isWatchOnly = isWatchOnly,
                network = config.network
            )
            secureStorage.saveWalletMetadata(storedWallet)
            
            // Save key material with wallet ID
            if (isWatchOnly) {
                secureStorage.saveExtendedKey(walletId, config.keyMaterial)
            } else {
                secureStorage.saveMnemonic(walletId, config.keyMaterial)
                config.passphrase?.let { secureStorage.savePassphrase(walletId, it) }
            }
            secureStorage.saveNetwork(config.network)
            
            // Mark wallet as needing full sync (address discovery)
            secureStorage.setNeedsFullSync(walletId, true)
            
            // Set as active wallet
            secureStorage.setActiveWalletId(walletId)
            
            // Update state
            updateWalletState()
            
            WalletResult.Success(Unit)
        } catch (e: Exception) {
            WalletResult.Error("Failed to import wallet: ${e.message}", e)
        }
    }
    
    /**
     * Simple import (backward compatible)
     */
    suspend fun importWallet(
        mnemonic: String,
        passphrase: String? = null,
        network: WalletNetwork = WalletNetwork.BITCOIN
    ): WalletResult<Unit> {
        return importWallet(
            WalletImportConfig(
                name = "Wallet ${getAllWallets().size + 1}",
                keyMaterial = mnemonic,
                addressType = AddressType.SEGWIT,
                passphrase = passphrase,
                network = network
            )
        )
    }
    
    /**
     * Switch to a different wallet
     */
    suspend fun switchWallet(walletId: String): WalletResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val storedWallet = secureStorage.getWalletMetadata(walletId)
                ?: return@withContext WalletResult.Error("Wallet not found")
            
            // Set as active
            secureStorage.setActiveWalletId(walletId)
            
            // Load the wallet
            loadWalletById(walletId)
            
            WalletResult.Success(Unit)
        } catch (e: Exception) {
            WalletResult.Error("Failed to switch wallet: ${e.message}", e)
        }
    }
    
    /**
     * Load a specific wallet by ID
     */
    private suspend fun loadWalletById(walletId: String): WalletResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val storedWallet = secureStorage.getWalletMetadata(walletId)
                ?: return@withContext WalletResult.Error("Wallet not found")
            
            val bdkNetwork = storedWallet.network.toBdkNetwork()
            
            val (externalDescriptor, internalDescriptor) = if (secureStorage.hasExtendedKey(walletId)) {
                // Watch-only wallet
                val extendedKey = secureStorage.getExtendedKey(walletId)
                    ?: return@withContext WalletResult.Error("No extended key found")
                createWatchOnlyDescriptors(extendedKey, storedWallet.addressType, bdkNetwork)
            } else {
                // Full wallet from mnemonic
                val mnemonic = secureStorage.getMnemonic(walletId)
                    ?: return@withContext WalletResult.Error("No mnemonic found")
                val passphrase = secureStorage.getPassphrase(walletId)
                
                createDescriptorsFromMnemonic(mnemonic, passphrase, storedWallet.addressType, bdkNetwork)
            }
            
            // Load wallet with persistent SQLite storage
            val connection = Connection(getWalletDbPath(walletId))
            walletConnection = connection
            
            // Try to load existing wallet from database, fall back to creating new if not found
            wallet = try {
                Wallet.load(externalDescriptor, internalDescriptor, connection)
            } catch (e: Exception) {
                Log.d(TAG, "No existing wallet DB found, creating new wallet for $walletId")
                val newWallet = Wallet(
                    descriptor = externalDescriptor,
                    changeDescriptor = internalDescriptor,
                    network = bdkNetwork,
                    connection = connection
                )
                newWallet.persist(connection)
                newWallet
            }
            
            updateWalletState()
            
            WalletResult.Success(Unit)
        } catch (e: Exception) {
            WalletResult.Error("Failed to load wallet: ${e.message}", e)
        }
    }
    
    /**
     * Create descriptors from mnemonic based on address type
     */
    private fun createDescriptorsFromMnemonic(
        mnemonic: String,
        passphrase: String?,
        addressType: AddressType,
        network: Network
    ): Pair<Descriptor, Descriptor> {
        val mnemonicObj = Mnemonic.fromString(mnemonic)
        val descriptorSecretKey = DescriptorSecretKey(
            network = network,
            mnemonic = mnemonicObj,
            password = passphrase
        )
        
        return when (addressType) {
            AddressType.LEGACY -> {
                // BIP44 for Legacy P2PKH
                val external = Descriptor.newBip44(
                    secretKey = descriptorSecretKey,
                    keychain = KeychainKind.EXTERNAL,
                    network = network
                )
                val internal = Descriptor.newBip44(
                    secretKey = descriptorSecretKey,
                    keychain = KeychainKind.INTERNAL,
                    network = network
                )
                Pair(external, internal)
            }
            AddressType.SEGWIT -> {
                // BIP84 for Native SegWit P2WPKH
                val external = Descriptor.newBip84(
                    secretKey = descriptorSecretKey,
                    keychain = KeychainKind.EXTERNAL,
                    network = network
                )
                val internal = Descriptor.newBip84(
                    secretKey = descriptorSecretKey,
                    keychain = KeychainKind.INTERNAL,
                    network = network
                )
                Pair(external, internal)
            }
            AddressType.TAPROOT -> {
                // BIP86 for Taproot P2TR
                val external = Descriptor.newBip86(
                    secretKey = descriptorSecretKey,
                    keychain = KeychainKind.EXTERNAL,
                    network = network
                )
                val internal = Descriptor.newBip86(
                    secretKey = descriptorSecretKey,
                    keychain = KeychainKind.INTERNAL,
                    network = network
                )
                Pair(external, internal)
            }
        }
    }
    
    /**
     * Convert extended public keys (zpub, ypub, vpub, upub) to xpub/tpub format
     * BDK only accepts xpub (mainnet) and tpub (testnet) formats
     * 
     * Version bytes:
     * - xpub: 0x0488B21E (mainnet)
     * - tpub: 0x043587CF (testnet)
     * - ypub: 0x049D7CB2 (mainnet BIP49)
     * - upub: 0x044A5262 (testnet BIP49)
     * - zpub: 0x04B24746 (mainnet BIP84)
     * - vpub: 0x045F1CF6 (testnet BIP84)
     */
    private fun convertToXpub(extendedKey: String): String {
        // If already xpub/tpub, return as-is
        if (extendedKey.startsWith("xpub") || extendedKey.startsWith("tpub")) {
            return extendedKey
        }
        
        // Decode Base58Check
        val decoded = Base58.decodeChecked(extendedKey)
        
        // Determine target version bytes based on network (mainnet vs testnet)
        val isTestnet = extendedKey.startsWith("vpub") || extendedKey.startsWith("upub")
        val targetVersion = if (isTestnet) {
            byteArrayOf(0x04, 0x35, 0x87.toByte(), 0xCF.toByte()) // tpub
        } else {
            byteArrayOf(0x04, 0x88.toByte(), 0xB2.toByte(), 0x1E) // xpub
        }
        
        // Replace version bytes (first 4 bytes) and re-encode
        val newData = targetVersion + decoded.sliceArray(4 until decoded.size)
        return Base58.encodeChecked(newData)
    }
    
    /**
     * Create watch-only descriptors from extended public key
     * Uses the user's selected address type to build the correct descriptor.
     * Extended key prefixes (xpub/zpub/ypub) are all treated as interchangeable
     * since wallets like Sparrow export xpub for all derivation paths.
     */
    private fun createWatchOnlyDescriptors(
        extendedKey: String,
        addressType: AddressType,
        network: Network
    ): Pair<Descriptor, Descriptor> {
        // Convert to xpub/tpub format for BDK compatibility
        val xpubKey = convertToXpub(extendedKey)
        
        // Build descriptor based on the user's selected address type
        return when (addressType) {
            AddressType.LEGACY -> {
                val external = Descriptor("pkh($xpubKey/0/*)", network)
                val internal = Descriptor("pkh($xpubKey/1/*)", network)
                Pair(external, internal)
            }
            AddressType.SEGWIT -> {
                val external = Descriptor("wpkh($xpubKey/0/*)", network)
                val internal = Descriptor("wpkh($xpubKey/1/*)", network)
                Pair(external, internal)
            }
            AddressType.TAPROOT -> {
                val external = Descriptor("tr($xpubKey/0/*)", network)
                val internal = Descriptor("tr($xpubKey/1/*)", network)
                Pair(external, internal)
            }
        }
    }
    
    /**
     * Base58 encoding/decoding utilities for extended key conversion
     */
    private object Base58 {
        private const val ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
        private val INDEXES = IntArray(128) { -1 }.also { arr ->
            ALPHABET.forEachIndexed { i, c -> arr[c.code] = i }
        }
        
        fun decodeChecked(input: String): ByteArray {
            val decoded = decode(input)
            // Verify checksum (last 4 bytes)
            val data = decoded.sliceArray(0 until decoded.size - 4)
            val checksum = decoded.sliceArray(decoded.size - 4 until decoded.size)
            val hash = sha256(sha256(data))
            val expectedChecksum = hash.sliceArray(0 until 4)
            require(checksum.contentEquals(expectedChecksum)) { "Invalid checksum" }
            return data
        }
        
        fun encodeChecked(data: ByteArray): String {
            val hash = sha256(sha256(data))
            val checksum = hash.sliceArray(0 until 4)
            return encode(data + checksum)
        }
        
        private fun decode(input: String): ByteArray {
            if (input.isEmpty()) return ByteArray(0)
            
            // Convert base58 string to big integer
            var bi = java.math.BigInteger.ZERO
            for (c in input) {
                val digit = INDEXES[c.code]
                require(digit >= 0) { "Invalid Base58 character: $c" }
                bi = bi.multiply(java.math.BigInteger.valueOf(58)).add(java.math.BigInteger.valueOf(digit.toLong()))
            }
            
            // Convert to bytes
            val bytes = bi.toByteArray()
            // Remove leading zero if present (BigInteger sign byte)
            val stripSign = bytes.isNotEmpty() && bytes[0] == 0.toByte() && bytes.size > 1
            val stripped = if (stripSign) bytes.sliceArray(1 until bytes.size) else bytes
            
            // Count leading zeros in input
            var leadingZeros = 0
            for (c in input) {
                if (c == '1') leadingZeros++ else break
            }
            
            // Add leading zero bytes
            return ByteArray(leadingZeros) + stripped
        }
        
        private fun encode(input: ByteArray): String {
            if (input.isEmpty()) return ""
            
            // Count leading zeros
            var leadingZeros = 0
            for (b in input) {
                if (b == 0.toByte()) leadingZeros++ else break
            }
            
            // Convert to big integer
            var bi = java.math.BigInteger(1, input)
            val sb = StringBuilder()
            
            while (bi > java.math.BigInteger.ZERO) {
                val (quotient, remainder) = bi.divideAndRemainder(java.math.BigInteger.valueOf(58))
                sb.append(ALPHABET[remainder.toInt()])
                bi = quotient
            }
            
            // Add leading '1's for zero bytes
            repeat(leadingZeros) { sb.append('1') }
            
            return sb.reverse().toString()
        }
        
        private fun sha256(data: ByteArray): ByteArray {
            return java.security.MessageDigest.getInstance("SHA-256").digest(data)
        }
    }
    
    /**
     * Load the active wallet from secure storage
     */
    suspend fun loadWallet(): WalletResult<Unit> = withContext(Dispatchers.IO) {
        val activeWalletId = secureStorage.getActiveWalletId()
            ?: return@withContext WalletResult.Error("No active wallet")
        
        loadWalletById(activeWalletId)
    }
    
    /**
     * Connect to an Electrum server and save it
     */
    suspend fun connectToElectrum(config: ElectrumConfig): WalletResult<Unit> = withContext(Dispatchers.IO) {
        try {
            // Stop any existing proxy bridge
            torProxyBridge?.stop()
            torProxyBridge = null
            
            val connectionString: String
            
            // Check if we need to use Tor proxy bridge (global Tor setting or .onion address)
            if (isTorEnabled() || config.isOnionAddress()) {
                Log.d(TAG, "Using Tor proxy bridge for ${config.url}:${config.port}")
                
                // Create and start the proxy bridge
                val bridge = TorProxyBridge(
                    targetHost = config.url,
                    targetPort = config.port,
                    useSsl = config.useSsl,
                    connectionTimeoutMs = 60000,
                    soTimeoutMs = 60000
                )
                
                val localPort = bridge.start()
                torProxyBridge = bridge
                
                // BDK connects to our local bridge (always TCP, SSL is handled by bridge)
                connectionString = "tcp://127.0.0.1:$localPort"
                Log.d(TAG, "Tor bridge started on port $localPort")
            } else {
                // Direct connection without Tor
                connectionString = config.toConnectionString()
                Log.d(TAG, "Direct connection to $connectionString (SSL: ${config.useSsl})")
            }
            
            Log.d(TAG, "Creating ElectrumClient with: $connectionString")
            val client = ElectrumClient(connectionString)
            electrumClient = client
            Log.d(TAG, "ElectrumClient created successfully")
            
            // Query server features to get minimum acceptable fee rate
            queryServerMinFeeRate(config.url, config.port, config.useSsl, isTorEnabled() || config.isOnionAddress())
            
            // Save the server config with proper ID
            val savedConfig = saveElectrumServer(config)
            secureStorage.setActiveServerId(savedConfig.id)
            
            WalletResult.Success(Unit)
        } catch (e: Exception) {
            // Clean up bridge on error
            torProxyBridge?.stop()
            torProxyBridge = null
            Log.e(TAG, "Failed to connect to Electrum: ${e.javaClass.simpleName} - ${e.message}", e)
            WalletResult.Error("Failed to connect to Electrum: ${e.message}", e)
        }
    }
    
    /**
     * Connect to an existing saved server by ID
     */
    suspend fun connectToServer(serverId: String): WalletResult<Unit> = withContext(Dispatchers.IO) {
        val config = secureStorage.getElectrumServer(serverId)
            ?: return@withContext WalletResult.Error("Server not found")
        
        try {
            // Stop any existing proxy bridge
            torProxyBridge?.stop()
            torProxyBridge = null
            
            val connectionString: String
            
            // Check if we need to use Tor proxy bridge (global Tor setting or .onion address)
            if (isTorEnabled() || config.isOnionAddress()) {
                Log.d(TAG, "Using Tor proxy bridge for ${config.url}:${config.port}")
                
                // Create and start the proxy bridge
                val bridge = TorProxyBridge(
                    targetHost = config.url,
                    targetPort = config.port,
                    useSsl = config.useSsl,
                    connectionTimeoutMs = 60000,
                    soTimeoutMs = 60000
                )
                
                val localPort = bridge.start()
                torProxyBridge = bridge
                
                // BDK connects to our local bridge (always TCP, SSL is handled by bridge)
                connectionString = "tcp://127.0.0.1:$localPort"
                Log.d(TAG, "Tor bridge started on port $localPort")
            } else {
                // Direct connection without Tor
                connectionString = config.toConnectionString()
                Log.d(TAG, "Direct connection to $connectionString (SSL: ${config.useSsl})")
            }
            
            Log.d(TAG, "Creating ElectrumClient with: $connectionString")
            val client = ElectrumClient(connectionString)
            electrumClient = client
            Log.d(TAG, "ElectrumClient created successfully")
            
            // Query server features to get minimum acceptable fee rate
            queryServerMinFeeRate(config.url, config.port, config.useSsl, isTorEnabled() || config.isOnionAddress())
            
            secureStorage.setActiveServerId(serverId)
            
            WalletResult.Success(Unit)
        } catch (e: Exception) {
            // Clean up bridge on error
            torProxyBridge?.stop()
            torProxyBridge = null
            Log.e(TAG, "Failed to connect to Electrum: ${e.javaClass.simpleName} - ${e.message}", e)
            WalletResult.Error("Failed to connect to Electrum: ${e.message}", e)
        }
    }
    
    /**
     * Quick sync - only syncs already-revealed addresses
     * Use for regular balance refresh (fast)
     */
    suspend fun sync(): WalletResult<Unit> = withContext(Dispatchers.IO) {
        val currentWallet = wallet ?: return@withContext WalletResult.Error("Wallet not initialized")
        val client = electrumClient ?: return@withContext WalletResult.Error("Not connected to Electrum server")
        val activeWalletId = secureStorage.getActiveWalletId()
            ?: return@withContext WalletResult.Error("No active wallet")
        
        // If wallet needs full sync (first time or manually requested), do that instead
        if (secureStorage.needsFullSync(activeWalletId)) {
            Log.d(TAG, "Wallet needs full sync, redirecting to fullSync()")
            return@withContext fullSync()
        }
        
        try {
            _walletState.value = _walletState.value.copy(isSyncing = true, error = null)
            Log.d(TAG, "Starting quick sync (revealed addresses only)")
            
            val syncRequest = currentWallet.startSyncWithRevealedSpks().build()
            val update = client.sync(
                syncRequest = syncRequest,
                batchSize = 20UL,
                fetchPrevTxouts = true
            )
            
            currentWallet.applyUpdate(update)
            
            // Persist changes to database
            walletConnection?.let { currentWallet.persist(it) }
            
            updateWalletState()
            _walletState.value = _walletState.value.copy(isSyncing = false)
            
            Log.d(TAG, "Quick sync completed successfully")
            WalletResult.Success(Unit)
        } catch (e: Exception) {
            _walletState.value = _walletState.value.copy(
                isSyncing = false,
                error = "Sync failed: ${e.message}"
            )
            Log.e(TAG, "Quick sync failed: ${e.message}", e)
            WalletResult.Error("Sync failed: ${e.message}", e)
        }
    }
    
    /**
     * Full sync - scans all addresses up to the gap limit for address discovery
     * Use for first import or manual full rescan (slow)
     */
    suspend fun fullSync(): WalletResult<Unit> = withContext(Dispatchers.IO) {
        val currentWallet = wallet ?: return@withContext WalletResult.Error("Wallet not initialized")
        val client = electrumClient ?: return@withContext WalletResult.Error("Not connected to Electrum server")
        val activeWalletId = secureStorage.getActiveWalletId()
            ?: return@withContext WalletResult.Error("No active wallet")
        
        try {
            _walletState.value = _walletState.value.copy(isSyncing = true, error = null)
            Log.d(TAG, "Starting full sync (address discovery scan)")
            
            val fullScanRequestBuilder = currentWallet.startFullScan()
            val fullScanRequest = fullScanRequestBuilder.build()
            val update = client.fullScan(
                fullScanRequest = fullScanRequest,
                stopGap = 20UL,
                batchSize = 20UL,
                fetchPrevTxouts = true
            )
            
            currentWallet.applyUpdate(update)
            
            // Persist changes to database
            walletConnection?.let { currentWallet.persist(it) }
            
            // Mark full sync as complete - future syncs will be quick
            secureStorage.setNeedsFullSync(activeWalletId, false)
            
            // Save the full sync timestamp
            secureStorage.saveLastFullSyncTime(activeWalletId, System.currentTimeMillis())
            
            updateWalletState()
            _walletState.value = _walletState.value.copy(isSyncing = false)
            
            Log.d(TAG, "Full sync completed successfully")
            WalletResult.Success(Unit)
        } catch (e: Exception) {
            _walletState.value = _walletState.value.copy(
                isSyncing = false,
                error = "Full sync failed: ${e.message}"
            )
            Log.e(TAG, "Full sync failed: ${e.message}", e)
            WalletResult.Error("Full sync failed: ${e.message}", e)
        }
    }
    
    /**
     * Request a full sync for a specific wallet
     * If the wallet is active, performs full sync immediately
     * If not active, marks it for full sync on next load
     */
    suspend fun requestFullSync(walletId: String): WalletResult<Unit> {
        val activeWalletId = secureStorage.getActiveWalletId()
        
        return if (walletId == activeWalletId) {
            // Active wallet - perform full sync now
            fullSync()
        } else {
            // Not active - mark for full sync when loaded
            secureStorage.setNeedsFullSync(walletId, true)
            WalletResult.Success(Unit)
        }
    }
    
    /**
     * Get the earliest unused receiving address
     * First checks already-revealed addresses, only generates new if all are used
     */
    suspend fun getNewAddress(): WalletResult<String> = withContext(Dispatchers.IO) {
        val currentWallet = wallet ?: return@withContext WalletResult.Error("Wallet not initialized")
        val activeWalletId = secureStorage.getActiveWalletId()
            ?: return@withContext WalletResult.Error("No active wallet")
        
        try {
            var address: String
            var attempts = 0
            val maxAttempts = 100 // Safety limit
            
            // Keep generating addresses until we find an unused one
            do {
                val addressInfo = currentWallet.revealNextAddress(KeychainKind.EXTERNAL)
                address = addressInfo.address.toString()
                attempts++
            } while (isAddressUsed(address) && attempts < maxAttempts)
            
            // Get label if exists
            val label = secureStorage.getAddressLabel(activeWalletId, address)
            
            val addrInfo = ReceiveAddressInfo(
                address = address,
                label = label,
                isUsed = false // We only return unused addresses now
            )
            
            _walletState.value = _walletState.value.copy(
                currentAddress = address,
                currentAddressInfo = addrInfo
            )
            
            // Persist revealed addresses to database
            walletConnection?.let { currentWallet.persist(it) }
            
            WalletResult.Success(address)
        } catch (e: Exception) {
            WalletResult.Error("Failed to get address: ${e.message}", e)
        }
    }
    
    /**
     * Get a new unused change address
     */
    suspend fun getNewChangeAddress(): WalletResult<String> = withContext(Dispatchers.IO) {
        val currentWallet = wallet ?: return@withContext WalletResult.Error("Wallet not initialized")
        
        try {
            var address: String
            var attempts = 0
            val maxAttempts = 100
            
            do {
                val addressInfo = currentWallet.revealNextAddress(KeychainKind.INTERNAL)
                address = addressInfo.address.toString()
                attempts++
            } while (isAddressUsed(address) && attempts < maxAttempts)
            
            // Persist revealed addresses to database
            walletConnection?.let { currentWallet.persist(it) }
            
            WalletResult.Success(address)
        } catch (e: Exception) {
            WalletResult.Error("Failed to get change address: ${e.message}", e)
        }
    }
    
    /**
     * Check if an address has been used (received any transactions)
     */
    private fun isAddressUsed(address: String): Boolean {
        val currentWallet = wallet ?: return false
        
        // Check all transactions for this address
        val transactions = currentWallet.transactions()
        for (canonicalTx in transactions) {
            val tx = canonicalTx.transaction
            val outputs = tx.output()
            val network = currentWallet.network()
            
            for (output in outputs) {
                try {
                    val outputAddress = Address.fromScript(output.scriptPubkey, network).toString()
                    if (outputAddress == address) {
                        return true
                    }
                } catch (e: Exception) {
                    // Skip outputs that can't be converted to addresses
                }
            }
        }
        return false
    }
    
    /**
     * Save a label for an address
     */
    fun saveAddressLabel(address: String, label: String) {
        val activeWalletId = secureStorage.getActiveWalletId() ?: return
        secureStorage.saveAddressLabel(activeWalletId, address, label)
        
        // Update state if this is the current address
        val currentState = _walletState.value
        if (currentState.currentAddress == address) {
            _walletState.value = currentState.copy(
                currentAddressInfo = currentState.currentAddressInfo?.copy(label = label)
            )
        }
    }
    
    /**
     * Get label for an address
     */
    fun getAddressLabel(address: String): String? {
        val activeWalletId = secureStorage.getActiveWalletId() ?: return null
        return secureStorage.getAddressLabel(activeWalletId, address)
    }
    
    /**
     * Get all address labels for current wallet
     */
    fun getAllAddressLabels(): Map<String, String> {
        val activeWalletId = secureStorage.getActiveWalletId() ?: return emptyMap()
        return secureStorage.getAllAddressLabels(activeWalletId)
    }
    
    /**
     * Get all transaction labels for current wallet
     */
    fun getAllTransactionLabels(): Map<String, String> {
        val activeWalletId = secureStorage.getActiveWalletId() ?: return emptyMap()
        return secureStorage.getAllTransactionLabels(activeWalletId)
    }
    
    /**
     * Save a label for a transaction
     */
    fun saveTransactionLabel(txid: String, label: String) {
        val activeWalletId = secureStorage.getActiveWalletId() ?: return
        secureStorage.saveTransactionLabel(activeWalletId, txid, label)
    }
    
    /**
     * Get wallet metadata for a specific wallet by ID
     */
    fun getWalletMetadata(walletId: String): StoredWallet? {
        return secureStorage.getWalletMetadata(walletId)
    }
    
    /**
     * Get all address labels for a specific wallet (not just the active one)
     */
    fun getAllAddressLabelsForWallet(walletId: String): Map<String, String> {
        return secureStorage.getAllAddressLabels(walletId)
    }
    
    /**
     * Get all transaction labels for a specific wallet (not just the active one)
     */
    fun getAllTransactionLabelsForWallet(walletId: String): Map<String, String> {
        return secureStorage.getAllTransactionLabels(walletId)
    }
    
    /**
     * Save an address label for a specific wallet
     */
    fun saveAddressLabelForWallet(walletId: String, address: String, label: String) {
        secureStorage.saveAddressLabel(walletId, address, label)
    }
    
    /**
     * Save a transaction label for a specific wallet
     */
    fun saveTransactionLabelForWallet(walletId: String, txid: String, label: String) {
        secureStorage.saveTransactionLabel(walletId, txid, label)
    }
    
    /**
     * Get all addresses for the wallet (receive, change, used)
     */
    fun getAllAddresses(): Triple<List<WalletAddress>, List<WalletAddress>, List<WalletAddress>> {
        val currentWallet = wallet ?: return Triple(emptyList(), emptyList(), emptyList())
        val activeWalletId = secureStorage.getActiveWalletId() ?: return Triple(emptyList(), emptyList(), emptyList())
        val labels = secureStorage.getAllAddressLabels(activeWalletId)
        val network = currentWallet.network()
        
        // Get address balances from UTXOs
        val addressBalances = mutableMapOf<String, ULong>()
        val addressTxCounts = mutableMapOf<String, Int>()
        
        try {
            val utxos = currentWallet.listUnspent()
            for (utxo in utxos) {
                val addr = try {
                    Address.fromScript(utxo.txout.scriptPubkey, network).toString()
                } catch (e: Exception) { continue }
                addressBalances[addr] = (addressBalances[addr] ?: 0UL) + utxo.txout.value
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error listing UTXOs: ${e.message}")
        }
        
        // Count transactions per address
        try {
            val transactions = currentWallet.transactions()
            for (canonicalTx in transactions) {
                val tx = canonicalTx.transaction
                val outputs = tx.output()
                for (output in outputs) {
                    val addr = try {
                        Address.fromScript(output.scriptPubkey, network).toString()
                    } catch (e: Exception) { continue }
                    addressTxCounts[addr] = (addressTxCounts[addr] ?: 0) + 1
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error counting transactions: ${e.message}")
        }
        
        val receiveAddresses = mutableListOf<WalletAddress>()
        val changeAddresses = mutableListOf<WalletAddress>()
        val usedAddresses = mutableListOf<WalletAddress>()
        
        // Get receive addresses (external keychain)
        try {
            val lastRevealedExternal = currentWallet.derivationIndex(KeychainKind.EXTERNAL)
            
            // Add revealed addresses - used ones to usedAddresses, unused to receiveAddresses
            if (lastRevealedExternal != null) {
                for (i in 0u..lastRevealedExternal) {
                    val addrInfo = currentWallet.peekAddress(KeychainKind.EXTERNAL, i)
                    val addr = addrInfo.address.toString()
                    val isUsed = (addressTxCounts[addr] ?: 0) > 0
                    val balance = addressBalances[addr] ?: 0UL
                    
                    if (isUsed) {
                        usedAddresses.add(WalletAddress(
                            address = addr,
                            index = i,
                            keychain = KeychainType.EXTERNAL,
                            label = labels[addr],
                            balanceSats = balance,
                            transactionCount = addressTxCounts[addr] ?: 0,
                            isUsed = true
                        ))
                    } else {
                        receiveAddresses.add(WalletAddress(
                            address = addr,
                            index = i,
                            keychain = KeychainType.EXTERNAL,
                            label = labels[addr],
                            balanceSats = balance,
                            transactionCount = 0,
                            isUsed = false
                        ))
                    }
                }
            }
            
            // Show at least 1 peeked address, but ensure minimum 6 total
            val startIndex = (lastRevealedExternal?.plus(1u)) ?: 0u
            val peekCount = maxOf(1, 6 - receiveAddresses.size)
            for (i in 0u until peekCount.toUInt()) {
                val index = startIndex + i
                val addrInfo = currentWallet.peekAddress(KeychainKind.EXTERNAL, index)
                val addr = addrInfo.address.toString()
                
                receiveAddresses.add(WalletAddress(
                    address = addr,
                    index = index,
                    keychain = KeychainType.EXTERNAL,
                    label = labels[addr],
                    balanceSats = 0UL,
                    transactionCount = 0,
                    isUsed = false
                ))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting receive addresses: ${e.message}")
        }
        
        // Get change addresses (internal keychain)
        try {
            val lastRevealedInternal = currentWallet.derivationIndex(KeychainKind.INTERNAL)
            
            // Add revealed addresses - used ones to usedAddresses, unused to changeAddresses
            if (lastRevealedInternal != null) {
                for (i in 0u..lastRevealedInternal) {
                    val addrInfo = currentWallet.peekAddress(KeychainKind.INTERNAL, i)
                    val addr = addrInfo.address.toString()
                    val isUsed = (addressTxCounts[addr] ?: 0) > 0
                    val balance = addressBalances[addr] ?: 0UL
                    
                    if (isUsed) {
                        usedAddresses.add(WalletAddress(
                            address = addr,
                            index = i,
                            keychain = KeychainType.INTERNAL,
                            label = labels[addr],
                            balanceSats = balance,
                            transactionCount = addressTxCounts[addr] ?: 0,
                            isUsed = true
                        ))
                    } else {
                        changeAddresses.add(WalletAddress(
                            address = addr,
                            index = i,
                            keychain = KeychainType.INTERNAL,
                            label = labels[addr],
                            balanceSats = balance,
                            transactionCount = 0,
                            isUsed = false
                        ))
                    }
                }
            }
            
            // Show at least 1 peeked address, but ensure minimum 6 total
            val startIndex = (lastRevealedInternal?.plus(1u)) ?: 0u
            val peekCount = maxOf(1, 6 - changeAddresses.size)
            for (i in 0u until peekCount.toUInt()) {
                val index = startIndex + i
                val addrInfo = currentWallet.peekAddress(KeychainKind.INTERNAL, index)
                val addr = addrInfo.address.toString()
                
                changeAddresses.add(WalletAddress(
                    address = addr,
                    index = index,
                    keychain = KeychainType.INTERNAL,
                    label = labels[addr],
                    balanceSats = 0UL,
                    transactionCount = 0,
                    isUsed = false
                ))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting change addresses: ${e.message}")
        }
        
        return Triple(receiveAddresses, changeAddresses, usedAddresses)
    }
    
    /**
     * Get all UTXOs for the wallet
     */
    fun getAllUtxos(): List<UtxoInfo> {
        val currentWallet = wallet ?: return emptyList()
        val activeWalletId = secureStorage.getActiveWalletId() ?: return emptyList()
        val labels = secureStorage.getAllAddressLabels(activeWalletId)
        val frozenUtxos = secureStorage.getFrozenUtxos(activeWalletId)
        val network = currentWallet.network()
        
        return try {
            val utxos = currentWallet.listUnspent()
            utxos.mapNotNull { utxo ->
                try {
                    val addr = Address.fromScript(utxo.txout.scriptPubkey, network).toString()
                    val outpoint = "${utxo.outpoint.txid}:${utxo.outpoint.vout}"
                    
                    // Check confirmation status by looking up the transaction
                    val isConfirmed = try {
                        val canonicalTx = currentWallet.getTx(utxo.outpoint.txid)
                        canonicalTx?.chainPosition is ChainPosition.Confirmed
                    } catch (e: Exception) {
                        false
                    }
                    
                    UtxoInfo(
                        outpoint = outpoint,
                        txid = utxo.outpoint.txid,
                        vout = utxo.outpoint.vout,
                        address = addr,
                        amountSats = utxo.txout.value,
                        label = labels[addr],
                        isConfirmed = isConfirmed,
                        isFrozen = frozenUtxos.contains(outpoint)
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing UTXO: ${e.message}")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error listing UTXOs: ${e.message}")
            emptyList()
        }
    }
    
    /**
     * Freeze/unfreeze a UTXO
     */
    fun setUtxoFrozen(outpoint: String, frozen: Boolean) {
        val activeWalletId = secureStorage.getActiveWalletId() ?: return
        if (frozen) {
            secureStorage.freezeUtxo(activeWalletId, outpoint)
        } else {
            secureStorage.unfreezeUtxo(activeWalletId, outpoint)
        }
    }
    
    /**
     * Check if a UTXO is frozen
     */
    fun isUtxoFrozen(outpoint: String): Boolean {
        val activeWalletId = secureStorage.getActiveWalletId() ?: return false
        return secureStorage.getFrozenUtxos(activeWalletId).contains(outpoint)
    }
    
    /**
     * Create and broadcast a transaction
     * @param selectedUtxos Optional list of specific UTXOs to spend from (coin control)
     * @param label Optional label for the transaction
     * @param isMaxSend If true, sends entire balance minus fees (drain wallet)
     */
    suspend fun sendBitcoin(
        recipientAddress: String,
        amountSats: ULong,
        feeRateSatPerVb: Float = 1.0f,
        selectedUtxos: List<UtxoInfo>? = null,
        label: String? = null,
        isMaxSend: Boolean = false
    ): WalletResult<String> = withContext(Dispatchers.IO) {
        val currentWallet = wallet ?: return@withContext WalletResult.Error("Wallet not initialized")
        val client = electrumClient ?: return@withContext WalletResult.Error("Not connected to Electrum server")
        
        // Check if watch-only
        val activeWalletId = secureStorage.getActiveWalletId()
        val storedWallet = activeWalletId?.let { secureStorage.getWalletMetadata(it) }
        if (storedWallet?.isWatchOnly == true) {
            return@withContext WalletResult.Error("Cannot send from watch-only wallet")
        }
        
        try {
            val address = Address(recipientAddress, currentWallet.network())
            // Round up fee rate to ensure we meet the target
            val feeRate = FeeRate.fromSatPerVb(kotlin.math.ceil(feeRateSatPerVb.toDouble()).toULong())
            
            var txBuilder = TxBuilder()
                .feeRate(feeRate)
            
            // For max send, use drainWallet to send entire balance minus fees
            if (isMaxSend) {
                txBuilder = txBuilder
                    .drainWallet()
                    .drainTo(address.scriptPubkey())
            } else {
                val amount = Amount.fromSat(amountSats)
                txBuilder = txBuilder.addRecipient(address.scriptPubkey(), amount)
            }
            
            // If specific UTXOs are selected, use only those (coin control)
            if (!selectedUtxos.isNullOrEmpty()) {
                // Get actual UTXOs from wallet and match by outpoint string
                val walletUtxos = currentWallet.listUnspent()
                val selectedOutpoints = selectedUtxos.map { it.outpoint }.toSet()
                
                for (utxo in walletUtxos) {
                    val outpointStr = "${utxo.outpoint.txid}:${utxo.outpoint.vout}"
                    if (selectedOutpoints.contains(outpointStr)) {
                        txBuilder = txBuilder.addUtxo(utxo.outpoint)
                    }
                }
                // Only spend from the selected UTXOs
                txBuilder = txBuilder.manuallySelectedOnly()
            }
            
            val psbt = txBuilder.finish(currentWallet)
            
            currentWallet.sign(psbt)
            
            val tx = psbt.extractTx()
            client.broadcast(tx)
            
            val txid = tx.computeTxid()
            
            // Save transaction label if provided
            if (!label.isNullOrBlank() && activeWalletId != null) {
                secureStorage.saveTransactionLabel(activeWalletId, txid, label)
            }
            
            sync()
            
            WalletResult.Success(txid)
        } catch (e: Exception) {
            WalletResult.Error("Transaction failed: ${e.message}", e)
        }
    }
    
    /**
     * Bump the fee of an unconfirmed transaction using RBF (Replace-By-Fee)
     * @param txid The transaction ID to bump
     * @param newFeeRate The new fee rate in sat/vB
     * @return The new transaction ID
     */
    suspend fun bumpFee(
        txid: String,
        newFeeRate: Float
    ): WalletResult<String> = withContext(Dispatchers.IO) {
        val currentWallet = wallet ?: return@withContext WalletResult.Error("Wallet not initialized")
        val client = electrumClient ?: return@withContext WalletResult.Error("Not connected to Electrum server")
        
        // Check if watch-only
        val activeWalletId = secureStorage.getActiveWalletId()
        val storedWallet = activeWalletId?.let { secureStorage.getWalletMetadata(it) }
        if (storedWallet?.isWatchOnly == true) {
            return@withContext WalletResult.Error("Cannot bump fee from watch-only wallet")
        }
        
        try {
            // Round up fee rate to ensure we meet the target
            val feeRate = FeeRate.fromSatPerVb(kotlin.math.ceil(newFeeRate.toDouble()).toULong())
            
            // Use BDK's bump fee builder
            val bumpFeeTxBuilder = BumpFeeTxBuilder(txid, feeRate)
            val psbt = bumpFeeTxBuilder.finish(currentWallet)
            
            currentWallet.sign(psbt)
            
            val tx = psbt.extractTx()
            client.broadcast(tx)
            
            val newTxid = tx.computeTxid()
            
            // Copy label from original transaction if exists
            if (activeWalletId != null) {
                val originalLabel = secureStorage.getTransactionLabel(activeWalletId, txid)
                if (!originalLabel.isNullOrBlank()) {
                    secureStorage.saveTransactionLabel(activeWalletId, newTxid, originalLabel)
                }
            }
            
            sync()
            
            WalletResult.Success(newTxid)
        } catch (e: Exception) {
            val errorMsg = when {
                e.message?.contains("not found") == true -> "Transaction not found in wallet"
                e.message?.contains("already confirmed") == true -> "Transaction is already confirmed"
                e.message?.contains("rbf") == true || e.message?.contains("RBF") == true -> 
                    "Transaction is not RBF-enabled"
                e.message?.contains("fee") == true -> "New fee rate must be higher than current"
                else -> "Failed to bump fee: ${e.message}"
            }
            WalletResult.Error(errorMsg, e)
        }
    }
    
    /**
     * Speed up an incoming unconfirmed transaction using CPFP (Child-Pays-For-Parent)
     * Creates a new transaction spending an output from the parent with a high fee
     * @param parentTxid The parent transaction ID to speed up
     * @param feeRate The fee rate for the child transaction in sat/vB
     * @return The child transaction ID
     */
    suspend fun cpfp(
        parentTxid: String,
        feeRate: Float
    ): WalletResult<String> = withContext(Dispatchers.IO) {
        val currentWallet = wallet ?: return@withContext WalletResult.Error("Wallet not initialized")
        val client = electrumClient ?: return@withContext WalletResult.Error("Not connected to Electrum server")
        
        // Check if watch-only
        val activeWalletId = secureStorage.getActiveWalletId()
        val storedWallet = activeWalletId?.let { secureStorage.getWalletMetadata(it) }
        if (storedWallet?.isWatchOnly == true) {
            return@withContext WalletResult.Error("Cannot create CPFP from watch-only wallet")
        }
        
        try {
            // Find UTXOs from the parent transaction
            val utxos = currentWallet.listUnspent()
            val parentUtxos = utxos.filter { it.outpoint.txid == parentTxid }
            
            if (parentUtxos.isEmpty()) {
                return@withContext WalletResult.Error("No spendable outputs found from parent transaction")
            }
            
            // Get a change address to send to (we're just consolidating to ourselves)
            val changeAddress = currentWallet.revealNextAddress(KeychainKind.INTERNAL)
            
            // Calculate total value of parent UTXOs
            val totalValue = parentUtxos.sumOf { it.txout.value.toLong() }.toULong()
            
            // Build transaction spending the parent's UTXOs
            // Round up fee rate to ensure we meet the target
            val effectiveFeeRate = kotlin.math.ceil(feeRate.toDouble()).toULong()
            
            // Dust limit - use 546 sats (Bitcoin Core default relay dust, safe for all output types)
            // P2TR dust is ~387 sats, P2WPKH is ~294 sats, so 546 covers all
            val dustLimit = 546UL
            
            // Estimate child tx vsize (~150 vB for 1-in-1-out P2WPKH/P2TR)
            // Add buffer for potential additional inputs
            val estimatedVsize = 150L + (parentUtxos.size - 1) * 68L // ~68 vB per additional input
            val estimatedFee = effectiveFeeRate * estimatedVsize.toULong()
            
            // Check if parent output can cover fee AND leave dust-safe amount
            val canCoverFeeWithDust = totalValue > estimatedFee + dustLimit
            
            Log.d(TAG, "CPFP: parentUtxos=${parentUtxos.size}, totalValue=$totalValue, feeRate=$effectiveFeeRate, estimatedFee=$estimatedFee, canCoverFeeWithDust=$canCoverFeeWithDust")
            
            var txBuilder = TxBuilder()
                .feeRate(FeeRate.fromSatPerVb(effectiveFeeRate))
            
            // Add all UTXOs from parent transaction as REQUIRED inputs
            // This ensures these unconfirmed outputs are spent, pulling the parent tx
            for (utxo in parentUtxos) {
                Log.d(TAG, "CPFP: Adding required UTXO: ${utxo.outpoint.txid}:${utxo.outpoint.vout}, value=${utxo.txout.value}")
                txBuilder = txBuilder.addUtxo(utxo.outpoint)
            }
            
            if (canCoverFeeWithDust) {
                // Parent output is enough - just drain it to change address
                // BDK will calculate the exact fee and output amount
                txBuilder = txBuilder.drainTo(changeAddress.address.scriptPubkey())
            } else {
                // Parent output is too small - need additional UTXOs
                // Use drainWallet to include all available UTXOs
                txBuilder = txBuilder
                    .drainWallet()
                    .drainTo(changeAddress.address.scriptPubkey())
            }
            
            val psbt = txBuilder.finish(currentWallet)
            
            currentWallet.sign(psbt)
            
            val tx = psbt.extractTx()
            client.broadcast(tx)
            
            val childTxid = tx.computeTxid()
            
            sync()
            
            WalletResult.Success(childTxid)
        } catch (e: Exception) {
            Log.e(TAG, "CPFP failed: ${e.message}", e)
            val errorMsg = when {
                e.message?.contains("not found") == true -> "Parent transaction not found"
                e.message?.contains("confirmed") == true -> "Parent transaction is already confirmed"
                e.message?.contains("Insufficient") == true -> {
                    // Parse the amounts from error message if possible
                    val match = Regex("(\\d+\\.\\d+) BTC available.*(\\d+\\.\\d+) BTC needed").find(e.message ?: "")
                    if (match != null) {
                        "Insufficient funds: ${match.groupValues[1]} BTC available, ${match.groupValues[2]} BTC needed"
                    } else {
                        "Insufficient funds for CPFP - wallet balance too low"
                    }
                }
                e.message?.contains("BelowDustLimit") == true -> "Output too small (below dust limit) - try a lower fee rate"
                else -> "Failed to create CPFP: ${e.message}"
            }
            WalletResult.Error(errorMsg, e)
        }
    }
    
    /**
     * Check if a transaction can be bumped with RBF
     * @param txid The transaction ID to check
     * @return True if the transaction is unconfirmed and RBF-enabled
     */
    fun canBumpFee(txid: String): Boolean {
        val currentWallet = wallet ?: return false
        
        try {
            // Check if transaction exists and is unconfirmed
            val transactions = currentWallet.transactions()
            val tx = transactions.find { it.transaction.computeTxid() == txid }
                ?: return false
            
            // Check if confirmed
            if (tx.chainPosition is ChainPosition.Confirmed) {
                return false
            }
            
            // Check if any input has RBF signaling (sequence < 0xfffffffe)
            val inputs = tx.transaction.input()
            return inputs.any { it.sequence < 0xfffffffeu }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking RBF eligibility: ${e.message}")
            return false
        }
    }
    
    /**
     * Check if a transaction can be sped up with CPFP
     * @param txid The transaction ID to check
     * @return True if the transaction has unspent outputs that we control
     */
    fun canCpfp(txid: String): Boolean {
        val currentWallet = wallet ?: return false
        
        try {
            // Check if transaction is unconfirmed
            val transactions = currentWallet.transactions()
            val tx = transactions.find { it.transaction.computeTxid() == txid }
                ?: return false
            
            // Check if confirmed
            if (tx.chainPosition is ChainPosition.Confirmed) {
                return false
            }
            
            // Check if we have unspent outputs from this transaction
            val utxos = currentWallet.listUnspent()
            return utxos.any { it.outpoint.txid == txid }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking CPFP eligibility: ${e.message}")
            return false
        }
    }
    
    /**
     * Delete a specific wallet
     */
    suspend fun deleteWallet(walletId: String): WalletResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val wasActive = secureStorage.getActiveWalletId() == walletId
            
            // Delete wallet data from secure storage
            secureStorage.deleteWallet(walletId)
            
            // Delete BDK database files
            deleteWalletDatabase(walletId)
            
            // If this was the active wallet, clear current BDK wallet
            if (wasActive) {
                wallet = null
                
                // Try to load another wallet if available
                val remainingWallets = secureStorage.getAllWallets()
                if (remainingWallets.isNotEmpty()) {
                    val newActiveId = remainingWallets.first().id
                    secureStorage.setActiveWalletId(newActiveId)
                    loadWalletById(newActiveId)
                } else {
                    // No more wallets
                    _walletState.value = WalletState()
                }
            } else {
                // Just update the wallets list in state
                updateWalletState()
            }
            
            WalletResult.Success(Unit)
        } catch (e: Exception) {
            WalletResult.Error("Failed to delete wallet: ${e.message}", e)
        }
    }
    
    /**
     * Delete the currently active wallet (legacy method)
     */
    suspend fun deleteWallet(): WalletResult<Unit> {
        val activeWalletId = secureStorage.getActiveWalletId()
            ?: return WalletResult.Error("No active wallet")
        return deleteWallet(activeWalletId)
    }
    
    /**
     * Disconnect from Electrum server and clean up resources
     */
    fun disconnect() {
        Log.d(TAG, "Disconnecting from Electrum")
        electrumClient = null
        torProxyBridge?.stop()
        torProxyBridge = null
        // Reset min fee rate to default when disconnected
        _minFeeRate.value = ElectrumFeatureService.DEFAULT_MIN_FEE_RATE
    }
    
    /**
     * Query the Electrum server's minimum acceptable fee rate
     * This runs asynchronously and updates _minFeeRate
     */
    private suspend fun queryServerMinFeeRate(
        host: String,
        port: Int,
        useSSL: Boolean,
        useTorProxy: Boolean
    ) {
        Log.d(TAG, "Querying server min fee rate: host=$host, port=$port, ssl=$useSSL, tor=$useTorProxy")
        try {
            val minRate = electrumFeatureService.getMinAcceptableFeeRate(host, port, useSSL, useTorProxy)
            _minFeeRate.value = minRate
            Log.d(TAG, "Server minimum fee rate set to: $minRate sat/vB (supports sub-sat: ${minRate < 1.0})")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to query server min fee rate: ${e.javaClass.simpleName} - ${e.message}", e)
            _minFeeRate.value = ElectrumFeatureService.DEFAULT_MIN_FEE_RATE
            Log.d(TAG, "Using default min fee rate: ${ElectrumFeatureService.DEFAULT_MIN_FEE_RATE} sat/vB")
        }
    }
    
    /**
     * Check if the connected server supports sub-sat fee rates
     */
    fun supportsSubSatFees(): Boolean {
        return _minFeeRate.value < 1.0
    }
    
    /**
     * Get the minimum acceptable fee rate for the connected server
     */
    fun getMinFeeRate(): Double {
        return _minFeeRate.value
    }
    
    /**
     * Check if currently using Tor proxy bridge
     */
    fun isUsingTorBridge(): Boolean = torProxyBridge?.isRunning() == true
    
    /**
     * Get the active Electrum server configuration
     */
    fun getElectrumConfig(): ElectrumConfig? {
        return secureStorage.getActiveElectrumServer()
    }
    
    /**
     * Get all saved Electrum servers
     */
    fun getAllElectrumServers(): List<ElectrumConfig> {
        return secureStorage.getAllElectrumServers()
    }
    
    /**
     * Save an Electrum server (add or update)
     */
    fun saveElectrumServer(config: ElectrumConfig): ElectrumConfig {
        return secureStorage.saveElectrumServer(config)
    }
    
    /**
     * Delete an Electrum server
     */
    fun deleteElectrumServer(serverId: String) {
        secureStorage.deleteElectrumServer(serverId)
        // Disconnect if this was the active server
        if (secureStorage.getActiveServerId() == null) {
            electrumClient = null
        }
    }
    
    /**
     * Set the active Electrum server and connect to it
     */
    suspend fun setActiveServer(serverId: String): WalletResult<Unit> = withContext(Dispatchers.IO) {
        val config = secureStorage.getElectrumServer(serverId)
            ?: return@withContext WalletResult.Error("Server not found")
        
        secureStorage.setActiveServerId(serverId)
        connectToElectrum(config)
    }
    
    /**
     * Get the active server ID
     */
    fun getActiveServerId(): String? {
        return secureStorage.getActiveServerId()
    }
    
    /**
     * Fetch transaction vsize from Electrum server
     * Returns the actual vsize from the network instead of BDK's calculated value
     */
    suspend fun fetchTransactionVsizeFromElectrum(txid: String): Int? = withContext(Dispatchers.IO) {
        try {
            val config = getElectrumConfig() ?: return@withContext null
            val useTor = isTorEnabled() || config.useTor || config.url.contains(".onion")
            
            val details = electrumFeatureService.getTransactionDetails(
                txid = txid,
                host = config.url.removePrefix("tcp://").removePrefix("ssl://"),
                port = config.port,
                useSSL = config.useSsl,
                useTorProxy = useTor
            )
            
            if (details?.vsize != null && details.vsize > 0) {
                Log.d(TAG, "Got vsize from Electrum: ${details.vsize}")
                return@withContext details.vsize
            }
            
            Log.w(TAG, "Electrum returned no vsize for tx $txid")
            null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch tx vsize from Electrum: ${e.message}")
            null
        }
    }
    
    // ==================== Tor Settings ====================
    
    /**
     * Check if Tor is enabled
     */
    fun isTorEnabled(): Boolean {
        return secureStorage.isTorEnabled()
    }
    
    /**
     * Set Tor enabled state
     */
    fun setTorEnabled(enabled: Boolean) {
        secureStorage.setTorEnabled(enabled)
    }
    
    // ==================== Display Settings ====================
    
    /**
     * Get the preferred denomination
     */
    fun getDenomination(): String {
        return secureStorage.getDenomination()
    }
    
    /**
     * Set the preferred denomination
     */
    fun setDenomination(denomination: String) {
        secureStorage.setDenomination(denomination)
    }
    
    /**
     * Check if using sats denomination
     */
    fun isUsingSats(): Boolean {
        return secureStorage.isUsingSats()
    }
    
    // ==================== Mempool Server Settings ====================
    
    /**
     * Get the selected mempool server option
     */
    fun getMempoolServer(): String {
        return secureStorage.getMempoolServer()
    }
    
    /**
     * Set the mempool server option
     */
    fun setMempoolServer(server: String) {
        secureStorage.setMempoolServer(server)
    }
    
    /**
     * Get the full mempool URL for block explorer links
     */
    fun getMempoolUrl(): String {
        return secureStorage.getMempoolUrl()
    }
    
    /**
     * Check if the current mempool URL is an onion address
     */
    fun isMempoolOnionAddress(): Boolean {
        val url = getMempoolUrl()
        return url.contains(".onion")
    }
    
    /**
     * Get the custom mempool URL
     */
    fun getCustomMempoolUrl(): String? {
        return secureStorage.getCustomMempoolUrl()
    }
    
    /**
     * Set the custom mempool URL
     */
    fun setCustomMempoolUrl(url: String) {
        secureStorage.setCustomMempoolUrl(url)
    }
    
    // ==================== Fee Estimation Settings ====================
    
    /**
     * Get the selected fee estimation source
     */
    fun getFeeSource(): String {
        return secureStorage.getFeeSource()
    }
    
    /**
     * Set the fee estimation source
     */
    fun setFeeSource(source: String) {
        secureStorage.setFeeSource(source)
    }
    
    /**
     * Get the full fee source URL based on selected option
     * Returns null if fee estimation is disabled
     */
    fun getFeeSourceUrl(): String? {
        return secureStorage.getFeeSourceUrl()
    }
    
    // Fee source constants (re-exported from SecureStorage)
    val FEE_SOURCE_OFF = SecureStorage.FEE_SOURCE_OFF
    val FEE_SOURCE_MEMPOOL = SecureStorage.FEE_SOURCE_MEMPOOL
    
    /**
     * Get the BTC/USD price source
     */
    fun getPriceSource(): String {
        return secureStorage.getPriceSource()
    }
    
    /**
     * Set the BTC/USD price source
     */
    fun setPriceSource(source: String) {
        secureStorage.setPriceSource(source)
    }
    
    /**
     * Update the wallet state with current data
     */
    private fun updateWalletState() {
        val currentWallet = wallet
        val activeWalletId = secureStorage.getActiveWalletId()
        val activeWallet = activeWalletId?.let { secureStorage.getWalletMetadata(it) }
        val allWallets = secureStorage.getAllWallets()
        
        if (currentWallet == null) {
            _walletState.value = WalletState(
                isInitialized = allWallets.isNotEmpty(),
                wallets = allWallets,
                activeWallet = activeWallet
            )
            return
        }
        
        try {
            val balance = currentWallet.balance()
            
            val transactions = currentWallet.transactions().mapNotNull { canonicalTx ->
                try {
                    val tx = canonicalTx.transaction
                    val txid = tx.computeTxid()
                    
                    // Use BDK's sentAndReceived to properly calculate amounts
                    // Returns SentAndReceivedValues with sent and received Amount properties
                    // - sent = sum of inputs that belong to this wallet (what we spent)
                    // - received = sum of outputs that belong to this wallet (what we got back)
                    val sentAndReceived = currentWallet.sentAndReceived(tx)
                    val sent = amountToSats(sentAndReceived.sent)
                    val received = amountToSats(sentAndReceived.received)
                    
                    Log.d(TAG, "TX $txid: sent=$sent, received=$received")
                    
                    // Net amount: positive = received, negative = sent
                    // If we received 100 and sent 0: net = +100 (incoming)
                    // If we sent 100 and received 20 (change): net = 20 - 100 = -80 (outgoing, excluding change)
                    val netAmount = received.toLong() - sent.toLong()
                    val isSentTx = netAmount < 0
                    
                    // Extract addresses from transaction outputs
                    val outputs = tx.output()
                    val network = currentWallet.network()
                    
                    // Categorize outputs
                    val ourOutputs = outputs.filter { currentWallet.isMine(it.scriptPubkey) }
                    val externalOutputs = outputs.filter { !currentWallet.isMine(it.scriptPubkey) }
                    
                    // Check if this is a self-transfer (all outputs belong to us)
                    val isSelfTransfer = isSentTx && externalOutputs.isEmpty() && ourOutputs.isNotEmpty()
                    
                    // Extract address and amount (recipient for sent, receiving for received)
                    var address: String? = null
                    var addressAmount: ULong? = null
                    try {
                        if (isSentTx) {
                            if (isSelfTransfer) {
                                // Self-transfer: show the first output address (destination)
                                ourOutputs.firstOrNull()?.let { output ->
                                    address = Address.fromScript(output.scriptPubkey, network).toString()
                                    addressAmount = output.value
                                }
                            } else {
                                // Normal send: find the first output that is NOT ours (the recipient)
                                externalOutputs.firstOrNull()?.let { output ->
                                    address = Address.fromScript(output.scriptPubkey, network).toString()
                                    addressAmount = output.value
                                }
                            }
                        } else {
                            // Received: find the first output that IS ours
                            ourOutputs.firstOrNull()?.let { output ->
                                address = Address.fromScript(output.scriptPubkey, network).toString()
                                addressAmount = output.value
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Could not extract address from tx $txid: ${e.message}")
                    }
                    
                    // Extract change address and amount for sent transactions
                    var changeAddress: String? = null
                    var changeAmount: ULong? = null
                    if (isSentTx && ourOutputs.isNotEmpty()) {
                        try {
                            if (isSelfTransfer) {
                                // For self-transfers, find the output that is NOT the recipient (that's the change)
                                ourOutputs.find { output ->
                                    val outputAddr = Address.fromScript(output.scriptPubkey, network).toString()
                                    outputAddr != address
                                }?.let { output ->
                                    changeAddress = Address.fromScript(output.scriptPubkey, network).toString()
                                    changeAmount = output.value
                                }
                            } else {
                                // For normal sends, the change is the output that belongs to us
                                ourOutputs.firstOrNull()?.let { output ->
                                    changeAddress = Address.fromScript(output.scriptPubkey, network).toString()
                                    changeAmount = output.value
                                }
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Could not extract change address from tx $txid: ${e.message}")
                        }
                    }
                    
                    // Calculate fee if possible
                    val fee = try {
                        val feeAmount = currentWallet.calculateFee(tx)
                        amountToSats(feeAmount)
                    } catch (e: Exception) {
                        null
                    }
                    
                    // Get transaction vsize for fee rate calculation
                    val vsize = try {
                        tx.vsize()
                    } catch (e: Exception) {
                        null
                    }
                    
                    val confirmationInfo = when (val position = canonicalTx.chainPosition) {
                        is ChainPosition.Confirmed -> ConfirmationTime(
                            height = position.confirmationBlockTime.blockId.height,
                            timestamp = position.confirmationBlockTime.confirmationTime
                        )
                        is ChainPosition.Unconfirmed -> null
                    }
                    
                    // Get timestamp: confirmation time for confirmed, null for pending
                    val txTimestamp = confirmationInfo?.timestamp?.toLong()
                    
                    TransactionDetails(
                        txid = txid,
                        amountSats = netAmount,
                        fee = fee,
                        vsize = vsize,
                        confirmationTime = confirmationInfo,
                        isConfirmed = canonicalTx.chainPosition is ChainPosition.Confirmed,
                        timestamp = txTimestamp,
                        address = address,
                        addressAmount = addressAmount,
                        changeAddress = changeAddress,
                        changeAmount = changeAmount,
                        isSelfTransfer = isSelfTransfer
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing transaction: ${e.message}", e)
                    null
                }
            }.sortedByDescending { it.timestamp ?: Long.MAX_VALUE }
            
            // Get the next unused address (only reveals new if all revealed addresses are used)
            val lastAddress = try {
                currentWallet.nextUnusedAddress(KeychainKind.EXTERNAL).address.toString()
            } catch (e: Exception) {
                null
            }
            
            val lastSyncTime = activeWalletId?.let { secureStorage.getLastSyncTime(it) }
            
            _walletState.value = WalletState(
                isInitialized = true,
                wallets = allWallets,
                activeWallet = activeWallet,
                balanceSats = amountToSats(balance.total),
                pendingBalanceSats = amountToSats(balance.trustedPending) + amountToSats(balance.untrustedPending),
                transactions = transactions,
                currentAddress = lastAddress,
                lastSyncTimestamp = lastSyncTime
            )
        } catch (e: Exception) {
            _walletState.value = _walletState.value.copy(
                wallets = allWallets,
                activeWallet = activeWallet,
                error = "Failed to update state: ${e.message}"
            )
        }
    }
    
    // ==================== Security Settings ====================
    
    /**
     * Get the current security method
     */
    fun getSecurityMethod(): SecureStorage.SecurityMethod {
        return secureStorage.getSecurityMethod()
    }
    
    /**
     * Set the security method
     */
    fun setSecurityMethod(method: SecureStorage.SecurityMethod) {
        secureStorage.setSecurityMethod(method)
    }
    
    /**
     * Save PIN code
     */
    fun savePin(pin: String) {
        secureStorage.savePin(pin)
    }
    
    /**
     * Verify PIN code
     */
    fun verifyPin(pin: String): Boolean {
        return secureStorage.verifyPin(pin)
    }
    
    /**
     * Check if PIN is set
     */
    fun hasPin(): Boolean {
        return secureStorage.hasPin()
    }
    
    /**
     * Clear PIN code
     */
    fun clearPin() {
        secureStorage.clearPin()
    }
    
    /**
     * Check if security is enabled
     */
    fun isSecurityEnabled(): Boolean {
        return secureStorage.isSecurityEnabled()
    }
    
    /**
     * Check if biometric is enabled
     */
    fun isBiometricEnabled(): Boolean {
        return secureStorage.isBiometricEnabled()
    }
    
    /**
     * Get lock timing setting
     */
    fun getLockTiming(): SecureStorage.LockTiming {
        return secureStorage.getLockTiming()
    }
    
    /**
     * Set lock timing setting
     */
    fun setLockTiming(timing: SecureStorage.LockTiming) {
        secureStorage.setLockTiming(timing)
    }
    
    /**
     * Get last background time
     */
    fun getLastBackgroundTime(): Long {
        return secureStorage.getLastBackgroundTime()
    }
    
    /**
     * Set last background time
     */
    fun setLastBackgroundTime(time: Long) {
        secureStorage.setLastBackgroundTime(time)
    }
    
    /**
     * Convert Amount to satoshis
     * BDK 1.0 uses Amount.toSat() method
     */
    private fun amountToSats(amount: Amount): ULong {
        return try {
            amount.toSat()
        } catch (e: Exception) {
            Log.w(TAG, "Error converting Amount to sats: ${e.message}")
            0UL
        }
    }
    
    private fun WalletNetwork.toBdkNetwork(): Network {
        return when (this) {
            WalletNetwork.BITCOIN -> Network.BITCOIN
            WalletNetwork.TESTNET -> Network.TESTNET
            WalletNetwork.SIGNET -> Network.SIGNET
            WalletNetwork.REGTEST -> Network.REGTEST
        }
    }
}
