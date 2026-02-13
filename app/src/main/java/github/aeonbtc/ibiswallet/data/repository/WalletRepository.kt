package github.aeonbtc.ibiswallet.data.repository

import android.content.Context
import android.util.Log
import github.aeonbtc.ibiswallet.BuildConfig
import github.aeonbtc.ibiswallet.data.local.ElectrumCache
import github.aeonbtc.ibiswallet.data.local.SecureStorage
import github.aeonbtc.ibiswallet.data.model.*
import github.aeonbtc.ibiswallet.tor.CachingElectrumProxy
import github.aeonbtc.ibiswallet.tor.ElectrumNotification
import github.aeonbtc.ibiswallet.util.TofuTrustManager
import github.aeonbtc.ibiswallet.util.CertificateFirstUseException
import github.aeonbtc.ibiswallet.util.CertificateMismatchException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.bitcoindevkit.*
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
        private const val QUICK_SYNC_TIMEOUT_MS = 60_000L
        private const val SYNC_BATCH_SIZE = 500UL
        private const val FULL_SCAN_BATCH_SIZE = 25UL
        private const val MIN_BATCH_SIZE = 25UL
        private const val SCRIPT_HASH_SAMPLE_SIZE = 5
        private const val NOTIFICATION_DEBOUNCE_MS = 1000L
        /** Pattern to extract xpub/tpub/ypub/zpub/vpub/upub from a descriptor string: key between ] and / */
        private val XPUB_PATTERN = """\]([xtyzvuXTYZVU]pub[a-zA-Z0-9]+)""".toRegex()
    }
    
    private val secureStorage = SecureStorage(context)
    private val electrumCache = ElectrumCache(context)
    
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
        
        if (BuildConfig.DEBUG) Log.d(TAG, "Deleted wallet database for $walletId")
    }
    
    // Current active BDK wallet instance
    private var wallet: Wallet? = null
    private var walletPersister: Persister? = null
    private var electrumClient: ElectrumClient? = null
    
    // Protocol-aware caching proxy — terminates SSL, intercepts blockchain.transaction.get
    // to serve from persistent cache, and consolidates all Electrum connections (BDK traffic,
    // script hash subscriptions, verbose tx queries) through a single upstream socket.
    private var cachingProxy: CachingElectrumProxy? = null
    
    // Sync mutex - prevents concurrent sync operations
    private val syncMutex = Mutex()
    
    // Adaptive batch size - halved on timeout, reset on success.
    // Initialized from persisted value (avoids re-learning slow Tor connections).
    private var currentBatchSize = secureStorage.getSyncBatchSize()
        ?.toULong()?.coerceIn(MIN_BATCH_SIZE, SYNC_BATCH_SIZE) ?: SYNC_BATCH_SIZE
    
    // Script hash status cache for change-detection (address scriptHash -> status)
    // Used to skip sync when nothing has changed on the server
    private val scriptHashStatusCache = mutableMapOf<String, String?>()
    
    // Last known block height from Electrum blockHeadersSubscribe.
    // Used to skip sync when no new block has arrived AND no script hash changes.
    private var lastKnownBlockHeight: ULong? = null

    // Real-time notification collector — listens for server push notifications
    // (script hash changes, new blocks) and triggers targeted sync.
    private var notificationCollectorJob: Job? = null
    private val repositoryScope = CoroutineScope(Dispatchers.IO)

    // Tracks which script hashes are subscribed on the subscription socket.
    // Used to detect new addresses after sync (gap limit expansion) and
    // subscribe them without re-subscribing everything.
    private val subscribedScriptHashes = mutableSetOf<String>()
    
    /**
     * Subscribe to block headers on the connected Electrum server.
     * Updates lastKnownBlockHeight so sync() can detect new blocks.
     */
    private fun subscribeBlockHeaders(client: ElectrumClient) {
        try {
            val headerNotification = client.blockHeadersSubscribe()
            lastKnownBlockHeight = headerNotification.height
            if (BuildConfig.DEBUG) Log.d(TAG, "Subscribed to block headers, tip at height ${headerNotification.height}")
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.w(TAG, "Could not subscribe to block headers: ${e.message}")
        }
    }
    
    /**
     * Check if a new block has arrived since the last check.
     * Uses blockHeadersPop() to drain queued notifications.
     * Returns true if a new block was detected (sync should proceed).
     */
    private fun hasNewBlock(client: ElectrumClient): Boolean {
        var newBlock = false
        try {
            // Drain all queued header notifications
            while (true) {
                val notification = client.blockHeadersPop() ?: break
                lastKnownBlockHeight = notification.height
                newBlock = true
                if (BuildConfig.DEBUG) Log.d(TAG, "New block detected at height ${notification.height}")
            }
        } catch (e: Exception) {
            // If pop fails, assume we should sync (conservative)
            if (BuildConfig.DEBUG) Log.w(TAG, "blockHeadersPop failed: ${e.message}")
            return true
        }
        return newBlock
    }
    
    // Minimum acceptable fee rate from connected Electrum server (sat/vB)
    private val _minFeeRate = MutableStateFlow(CachingElectrumProxy.DEFAULT_MIN_FEE_RATE)
    val minFeeRate: StateFlow<Double> = _minFeeRate.asStateFlow()
    
    /**
     * Convert a sat/vB fee rate (potentially fractional, e.g. 0.5) to a BDK FeeRate
     * using sat/kWU for sub-sat/vB precision.
     * 1 sat/vB = 250 sat/kWU, so 0.8 sat/vB = 200 sat/kWU.
     */
    private fun feeRateFromSatPerVb(satPerVb: Float): FeeRate {
        val satPerKwu = kotlin.math.round(satPerVb.toDouble() * 250.0).toLong().coerceAtLeast(1L).toULong()
        return FeeRate.fromSatPerKwu(satPerKwu)
    }
    
    /**
     * Eagerly verify a server's SSL certificate via a probe connection.
     *
     * The CachingElectrumProxy performs the actual SSL handshake asynchronously (when BDK
     * connects through the local port), so TOFU exceptions thrown inside the bridge
     * are caught internally and never reach the caller. This probe makes a direct
     * SSL connection to trigger the TofuTrustManager BEFORE the bridge is created,
     * allowing CertificateFirstUseException / CertificateMismatchException to
     * propagate synchronously to connectToElectrum / connectToServer -> ViewModel.
     *
     * Returns the verified SSL socket so the bridge can reuse it for the first
     * BDK connection, eliminating the redundant SOCKS5+SSL handshake (saves 3-10s
     * over Tor). The caller must either pass the socket to the bridge or close it.
     *
     * For .onion hosts this returns null (Tor provides transport authentication).
     */
    private fun verifyCertificateProbe(
        host: String,
        port: Int,
        isOnion: Boolean,
        useTor: Boolean,
        useSsl: Boolean = true
    ): java.net.Socket? {
        // Skip probe only for .onion addresses that don't use SSL.
        // .onion + SSL is a rare edge case — still verify the certificate
        // so the user sees the TOFU approval dialog.
        if (isOnion && !useSsl) return null

        val storedFingerprint = secureStorage.getServerCertFingerprint(host, port)
        // For .onion+SSL, override isOnionHost=false so the trust manager
        // performs real TOFU verification instead of trust-all.
        val trustManager = TofuTrustManager(host, port, storedFingerprint, isOnionHost = false)
        val sslFactory = TofuTrustManager.createSSLSocketFactory(trustManager)

        var rawSocket: java.net.Socket? = null
        var sslSocket: java.net.Socket? = null
        var success = false
        try {
            rawSocket = if (useTor) {
                val proxy = java.net.Proxy(
                    java.net.Proxy.Type.SOCKS,
                    java.net.InetSocketAddress("127.0.0.1", 9050)
                )
                java.net.Socket(proxy).also {
                    it.soTimeout = 30_000
                    it.connect(
                        java.net.InetSocketAddress.createUnresolved(host, port),
                        30_000
                    )
                }
            } else {
                java.net.Socket().also {
                    it.soTimeout = 15_000
                    it.connect(java.net.InetSocketAddress(host, port), 15_000)
                }
            }

            // This triggers TofuTrustManager.checkServerTrusted() which throws
            // CertificateFirstUseException or CertificateMismatchException
            sslSocket = sslFactory.createSocket(rawSocket, host, port, true)
            (sslSocket as javax.net.ssl.SSLSocket).startHandshake()

            if (BuildConfig.DEBUG) Log.d(TAG, "Certificate probe OK for $host:$port")
            success = true
            // Return the verified socket for reuse by the bridge
            return sslSocket
        } finally {
            if (!success) {
                // Only close on failure/exception — on success the caller owns the socket
                try { sslSocket?.close() } catch (_: Exception) {}
                try { rawSocket?.close() } catch (_: Exception) {}
            }
        }
    }
    
    /**
     * Apply the "spend unconfirmed" setting to a TxBuilder.
     * When spending unconfirmed is disabled, excludes unconfirmed UTXOs from coin selection.
     */
    private fun TxBuilder.applyConfirmedOnlyFilter(): TxBuilder {
        return if (!secureStorage.getSpendUnconfirmed()) {
            this.excludeUnconfirmed()
        } else {
            this
        }
    }
    
    /**
     * Compute ceiled vsize (ceil(weight/4)) of a signed tx from a PSBT.
     * Returns integer vsize matching Bitcoin Core / mempool.space convention.
     *
     * Hot wallets: signs + finalizes the PSBT (dry-run only, never broadcast)
     * so real witness data is present, then measures the exact weight.
     *
     * Watch-only wallets: sign() is a no-op and finalize() fails, so we
     * estimate the signed weight from:
     *   Overhead: nVersion(4) + inCount(1) + outCount(1) + nLockTime(4) = 10 vB
     *            + segwit marker & flag (0.5 vB) for segwit/taproot = 10.5 vB
     *   Inputs (determined by wallet's address type — all inputs are ours):
     *     P2PKH:  outpoint(36) + scriptSigLen(1) + scriptSig(107) + nSeq(4) = 148 vB
     *     P2WPKH: 41 non-witness + 108 witness = 272 WU = 68 vB
     *     P2TR:   41 non-witness + 66 witness  = 230 WU = 57.5 vB
     *   Outputs (determined by each output's actual scriptPubKey — handles mixed types):
     *     nValue(8) + scriptLen(1) + scriptPubKey(N) = (9+N) vB per output
     *     P2PKH(N=25)=34 vB, P2WPKH(N=22)=31 vB, P2TR(N=34)=43 vB
     *   Reference: bitcoinops.org/en/tools/calc-size/
     */
    private fun estimateSignedVBytes(
        psbt: Psbt,
        wallet: Wallet,
        unsignedTx: Transaction
    ): Double {
        // Try signing — wallet.sign() both signs AND finalizes each input,
        // so we can extract the signed tx directly without calling finalize().
        try {
            val isSigned = wallet.sign(psbt)
            if (isSigned) {
                // sign() auto-finalized; extract the signed tx with real witness data
                val signedTx = psbt.extractTx()
                val weight = signedTx.weight()
                return kotlin.math.ceil(weight.toDouble() / 4.0)
            }
            // Partially signed — try explicit finalize as backup
            try {
                val finalizeResult = psbt.finalize()
                if (finalizeResult.couldFinalize) {
                    val signedTx = finalizeResult.psbt.extractTx()
                    val weight = signedTx.weight()
                    return kotlin.math.ceil(weight.toDouble() / 4.0)
                }
            } catch (_: Exception) { /* fall through */ }
        } catch (_: Exception) {
            // sign failed — fall through to estimation
        }
        
        // Watch-only or finalization failed: estimate from reference sizes.
        // Prefer Descriptor.maxWeightToSatisfy() for accurate, descriptor-derived
        // input weight (handles multisig, complex scripts, etc.)
        val inputWU: Long = try {
            val pubDescStr = wallet.publicDescriptor(KeychainKind.EXTERNAL)
            val desc = Descriptor(pubDescStr, wallet.network())
            desc.maxWeightToSatisfy().toLong()
        } catch (_: Exception) {
            // Fallback: detect address type from the wallet's receive address
            val walletAddrType = try {
                val addr = wallet.revealNextAddress(KeychainKind.EXTERNAL)
                val addrStr = addr.address.toString()
                when {
                    addrStr.startsWith("bc1p") || addrStr.startsWith("tb1p") -> AddressType.TAPROOT
                    addrStr.startsWith("bc1q") || addrStr.startsWith("tb1q") -> AddressType.SEGWIT
                    addrStr.startsWith("3") || addrStr.startsWith("2") -> AddressType.NESTED_SEGWIT
                    else -> AddressType.LEGACY
                }
            } catch (_: Exception) {
                AddressType.SEGWIT
            }
            when (walletAddrType) {
                AddressType.LEGACY -> 592L
                AddressType.NESTED_SEGWIT -> 364L // P2SH-P2WPKH input weight
                AddressType.SEGWIT -> 272L
                AddressType.TAPROOT -> 230L
            }
        }
        
        // Detect if segwit from input weight (segwit/taproot have witness discount)
        val isSegwit = inputWU < 500L // Legacy P2PKH is ~592 WU, segwit types are <300
        val overheadWU = if (isSegwit) 42L else 40L
        
        val numInputs = unsignedTx.input().size
        
        var totalOutputWU = 0L
        for (output in unsignedTx.output()) {
            val scriptLen = output.scriptPubkey.toBytes().size
            totalOutputWU += (9L + scriptLen) * 4L
        }
        
        val totalWU = overheadWU + (numInputs.toLong() * inputWU) + totalOutputWU
        return kotlin.math.ceil(totalWU.toDouble() / 4.0)
    }
    
    /**
     * Result of the two-pass fee correction: the exact fee AND the ceiled vsize
     * from signing the pass-1 PSBT. The vsize here is the authoritative value —
     * callers should use it for display rather than re-signing pass-2 (which would
     * produce a different ECDSA signature and potentially differ by ±1 WU).
     */
    private data class ExactFeeResult(val feeSats: ULong, val vsize: Double)

    /**
     * Two-pass fee correction: sign the pass-1 PSBT to measure ceiled vsize
     * (matching Bitcoin Core / mempool.space), then compute exact fee =
     * round(targetRate * ceiledVsize).
     *
     * Returns null if the PSBT can't be signed (watch-only wallet).
     */
    private fun computeExactFee(
        psbt: Psbt,
        wallet: Wallet,
        unsignedTx: Transaction,
        targetSatPerVb: Float
    ): ExactFeeResult? {
        val vsize = estimateSignedVBytes(psbt, wallet, unsignedTx)
        if (vsize <= 0.0) return null
        // round(rate * vsize) so the effective rate (fee/vsize) is centered on the target
        // rather than always above it. Max error is ±0.5/vsize sat/vB (< 0.005 sat/vB).
        val exactFee = kotlin.math.round(targetSatPerVb.toDouble() * vsize).toLong()
        return if (exactFee > 0) ExactFeeResult(exactFee.toULong(), vsize) else null
    }
    
    private val _walletState = MutableStateFlow(WalletState())
    val walletState: StateFlow<WalletState> = _walletState.asStateFlow()
    
    /**
     * Check if any wallet has been initialized
     */
    fun isWalletInitialized(): Boolean {
        return secureStorage.getWalletIds().isNotEmpty()
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
        val isWatchOnly: Boolean,
        val privateKey: String? = null,      // WIF private key (for single-key wallets)
        val watchAddress: String? = null      // Single watched address
    )
    
    /**
     * Get the key material (mnemonic and/or extended public key) for a wallet
     * For full wallets: returns both mnemonic and derived xpub
     * For watch-only wallets: returns only xpub
     * For WIF wallets: returns the private key
     * For watch address wallets: returns the watched address
     */
    fun getKeyMaterial(walletId: String): WalletKeyMaterial? {
        val storedWallet = secureStorage.getWalletMetadata(walletId) ?: return null
        
        // Check for watch address (single address watch-only)
        val watchAddress = secureStorage.getWatchAddress(walletId)
        if (watchAddress != null) {
            return WalletKeyMaterial(
                mnemonic = null,
                extendedPublicKey = null,
                isWatchOnly = true,
                watchAddress = watchAddress
            )
        }
        
        // Check for WIF private key (single-key wallet)
        val privateKey = secureStorage.getPrivateKey(walletId)
        if (privateKey != null) {
            return WalletKeyMaterial(
                mnemonic = null,
                extendedPublicKey = null,
                isWatchOnly = false,
                privateKey = privateKey
            )
        }
        
        // Check for extended key (watch-only wallet)
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
     * Derive the extended public key from a mnemonic.
     * Prefers wallet.publicDescriptor() when the wallet is loaded (guaranteed public-key-only),
     * falls back to creating a Descriptor from the mnemonic and extracting the xpub.
     */
    private fun deriveExtendedPublicKey(
        mnemonic: String,
        passphrase: String?,
        addressType: AddressType,
        network: WalletNetwork
    ): String {
        // If the wallet is already loaded, use publicDescriptor() for a reliable public key
        val currentWallet = wallet
        if (currentWallet != null) {
            try {
                val pubDescStr = currentWallet.publicDescriptor(KeychainKind.EXTERNAL)
                val xpubMatch = XPUB_PATTERN.find(pubDescStr)
                if (xpubMatch != null) {
                    return xpubMatch.groupValues[1]
                }
            } catch (_: Exception) {
                // Fall through to mnemonic-based derivation
            }
        }
        
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
                keychainKind = KeychainKind.EXTERNAL,
                network = bdkNetwork
            )
            AddressType.NESTED_SEGWIT -> Descriptor.newBip49(
                secretKey = descriptorSecretKey,
                keychainKind = KeychainKind.EXTERNAL,
                network = bdkNetwork
            )
            AddressType.SEGWIT -> Descriptor.newBip84(
                secretKey = descriptorSecretKey,
                keychainKind = KeychainKind.EXTERNAL,
                network = bdkNetwork
            )
            AddressType.TAPROOT -> Descriptor.newBip86(
                secretKey = descriptorSecretKey,
                keychainKind = KeychainKind.EXTERNAL,
                network = bdkNetwork
            )
        }
        
        // Extract the xpub/tpub from the descriptor string
        val descriptorString = descriptor.toString()
        val match = XPUB_PATTERN.find(descriptorString)
        
        return match?.groupValues?.get(1) ?: descriptorString
    }
    
    /**
     * Derive the master key fingerprint from a mnemonic.
     * Prefers wallet.publicDescriptor() when the wallet is loaded,
     * falls back to creating a Descriptor from the mnemonic and extracting the fingerprint.
     */
    private fun deriveMasterFingerprint(
        mnemonic: String,
        passphrase: String?,
        addressType: AddressType,
        network: WalletNetwork
    ): String? {
        return try {
            // If the wallet is loaded, use publicDescriptor() for reliable extraction
            val currentWallet = wallet
            if (currentWallet != null) {
                try {
                    val pubDescStr = currentWallet.publicDescriptor(KeychainKind.EXTERNAL)
                    val fp = extractFingerprint(pubDescStr)
                    if (fp != null) return fp
                } catch (_: Exception) {
                    // Fall through
                }
            }
            
            val bdkNetwork = network.toBdkNetwork()
            val mnemonicObj = Mnemonic.fromString(mnemonic)
            val descriptorSecretKey = DescriptorSecretKey(
                network = bdkNetwork,
                mnemonic = mnemonicObj,
                password = passphrase
            )
            val descriptor = when (addressType) {
                AddressType.LEGACY -> Descriptor.newBip44(descriptorSecretKey, KeychainKind.EXTERNAL, bdkNetwork)
                AddressType.NESTED_SEGWIT -> Descriptor.newBip49(descriptorSecretKey, KeychainKind.EXTERNAL, bdkNetwork)
                AddressType.SEGWIT -> Descriptor.newBip84(descriptorSecretKey, KeychainKind.EXTERNAL, bdkNetwork)
                AddressType.TAPROOT -> Descriptor.newBip86(descriptorSecretKey, KeychainKind.EXTERNAL, bdkNetwork)
            }
            // Descriptor string: wpkh([73c5da0a/84'/0'/0']xpub.../0/*)
            extractFingerprint(descriptor.toString())
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Import a wallet with full configuration
     */
    suspend fun importWallet(
        config: WalletImportConfig
    ): WalletResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val bdkNetwork = config.network.toBdkNetwork()
            val trimmedKey = config.keyMaterial.trim()
            val isWatchOnly = isWatchOnlyInput(trimmedKey)
            val isWif = isWifPrivateKey(trimmedKey)
            val isSingleAddress = isBitcoinAddress(trimmedKey)
            
            // Extract fingerprint from key origin if present, fall back to user-provided
            val fingerprint = extractFingerprint(trimmedKey) ?: config.masterFingerprint
            
            val derivationPath = config.customDerivationPath ?: if (isWif || isSingleAddress) "single" else config.addressType.defaultPath
            val walletId = UUID.randomUUID().toString()
            
            // For full wallets, derive the master fingerprint from the mnemonic
            val resolvedFingerprint = when {
                isWif || isSingleAddress -> null
                isWatchOnly -> fingerprint
                else -> deriveMasterFingerprint(
                    config.keyMaterial, config.passphrase, config.addressType, config.network
                )
            }
            
            // Single address watch: use Electrum-only tracking (no BDK wallet)
            if (isSingleAddress) {
                val detectedType = detectAddressType(trimmedKey) ?: config.addressType
                val storedWallet = StoredWallet(
                    id = walletId,
                    name = config.name,
                    addressType = detectedType,
                    derivationPath = "single",
                    isWatchOnly = true,
                    network = config.network,
                    masterFingerprint = null
                )
                secureStorage.saveWalletMetadata(storedWallet)
                secureStorage.saveWatchAddress(walletId, trimmedKey)
                secureStorage.saveNetwork(config.network)
                secureStorage.setNeedsFullSync(walletId, false)
                secureStorage.setActiveWalletId(walletId)
                updateWalletState()
                return@withContext WalletResult.Success(Unit)
            }
            
            // Create wallet with persistent SQLite storage
            val persister = Persister.newSqlite(getWalletDbPath(walletId))
            
            if (isWif) {
                // Single-key WIF wallet — uses Wallet.createSingle (no change descriptor)
                val descriptor = createDescriptorFromWif(trimmedKey, config.addressType, bdkNetwork)
                wallet = Wallet.createSingle(
                    descriptor = descriptor,
                    network = bdkNetwork,
                    persister = persister
                )
            } else {
                val (externalDescriptor, internalDescriptor) = when {
                    isWatchOnly -> createWatchOnlyDescriptors(trimmedKey, config.addressType, bdkNetwork, resolvedFingerprint)
                    else -> createDescriptorsFromMnemonic(
                        mnemonic = config.keyMaterial,
                        passphrase = config.passphrase,
                        addressType = config.addressType,
                        network = bdkNetwork
                    )
                }
                
                // Use BDK's native multipath constructor when input is a BIP 389 descriptor.
                // This avoids manual splitting and lets BDK handle the <0;1> derivation internally.
                val isMultipathInput = isWatchOnly &&
                    (trimmedKey.contains("<0;1>") || trimmedKey.contains("<1;0>"))
                
                wallet = if (isMultipathInput) {
                    try {
                        val stripped = trimmedKey.substringBefore('#').trim()
                        val multipathDescriptor = Descriptor(stripped, bdkNetwork)
                        Wallet.createFromTwoPathDescriptor(
                            twoPathDescriptor = multipathDescriptor,
                            network = bdkNetwork,
                            persister = persister
                        )
                    } catch (e: Exception) {
                        if (BuildConfig.DEBUG) Log.w(TAG, "createFromTwoPathDescriptor failed, falling back to split descriptors: ${e.message}")
                        Wallet(
                            descriptor = externalDescriptor,
                            changeDescriptor = internalDescriptor,
                            network = bdkNetwork,
                            persister = persister
                        )
                    }
                } else {
                    Wallet(
                        descriptor = externalDescriptor,
                        changeDescriptor = internalDescriptor,
                        network = bdkNetwork,
                        persister = persister
                    )
                }
            }
            walletPersister = persister
            
            // Persist the new wallet to database
            wallet!!.persist(persister)
            
            // Save wallet metadata
            val storedWallet = StoredWallet(
                id = walletId,
                name = config.name,
                addressType = config.addressType,
                derivationPath = derivationPath,
                isWatchOnly = isWatchOnly,
                network = config.network,
                masterFingerprint = resolvedFingerprint
            )
            secureStorage.saveWalletMetadata(storedWallet)
            
            // Save key material with wallet ID
            if (isWif) {
                secureStorage.savePrivateKey(walletId, config.keyMaterial.trim())
            } else if (isWatchOnly) {
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
            WalletResult.Error("Failed to import wallet", e)
        }
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
            
            // Clear script hash cache - different wallet has different addresses
            clearScriptHashCache()
            
            // Load the wallet
            loadWalletById(walletId)
            
            WalletResult.Success(Unit)
        } catch (e: Exception) {
            WalletResult.Error("Failed to switch wallet", e)
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
            
            // Watch address wallets have no BDK wallet — tracked via Electrum only
            if (secureStorage.hasWatchAddress(walletId)) {
                wallet = null
                walletPersister = null
                updateWalletState()
                return@withContext WalletResult.Success(Unit)
            }
            
            val isWifWallet = secureStorage.hasPrivateKey(walletId)
            
            // Load wallet with persistent SQLite storage
            val persister = Persister.newSqlite(getWalletDbPath(walletId))
            walletPersister = persister
            
            if (isWifWallet) {
                // Single-key WIF wallet — uses Wallet.createSingle (no change descriptor)
                val wif = secureStorage.getPrivateKey(walletId)
                    ?: return@withContext WalletResult.Error("No private key found")
                val descriptor = createDescriptorFromWif(wif, storedWallet.addressType, bdkNetwork)
                
                wallet = try {
                    Wallet.loadSingle(descriptor, persister)
                } catch (e: Exception) {
                    if (BuildConfig.DEBUG) Log.d(TAG, "No existing wallet DB found, creating new single-key wallet for $walletId")
                    val newWallet = Wallet.createSingle(
                        descriptor = descriptor,
                        network = bdkNetwork,
                        persister = persister
                    )
                    newWallet.persist(persister)
                    newWallet
                }
            } else {
                val (externalDescriptor, internalDescriptor) = when {
                    secureStorage.hasExtendedKey(walletId) -> {
                        // Watch-only wallet - include master fingerprint for PSBT signing
                        val extendedKey = secureStorage.getExtendedKey(walletId)
                            ?: return@withContext WalletResult.Error("No extended key found")
                        createWatchOnlyDescriptors(
                            extendedKey,
                            storedWallet.addressType,
                            bdkNetwork,
                            storedWallet.masterFingerprint
                        )
                    }
                    else -> {
                        // Full wallet from mnemonic
                        val mnemonic = secureStorage.getMnemonic(walletId)
                            ?: return@withContext WalletResult.Error("No mnemonic found")
                        val passphrase = secureStorage.getPassphrase(walletId)
                        createDescriptorsFromMnemonic(mnemonic, passphrase, storedWallet.addressType, bdkNetwork)
                    }
                }
                
                // Try to load existing wallet from database, fall back to creating new if not found
                wallet = try {
                    Wallet.load(externalDescriptor, internalDescriptor, persister)
                } catch (e: Exception) {
                    if (BuildConfig.DEBUG) Log.d(TAG, "No existing wallet DB found, creating new wallet for $walletId")
                    val newWallet = Wallet(
                        descriptor = externalDescriptor,
                        changeDescriptor = internalDescriptor,
                        network = bdkNetwork,
                        persister = persister
                    )
                    newWallet.persist(persister)
                    newWallet
                }
            }
            
            updateWalletState()
            
            WalletResult.Success(Unit)
        } catch (e: Exception) {
            WalletResult.Error("Failed to load wallet", e)
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
                    keychainKind = KeychainKind.EXTERNAL,
                    network = network
                )
                val internal = Descriptor.newBip44(
                    secretKey = descriptorSecretKey,
                    keychainKind = KeychainKind.INTERNAL,
                    network = network
                )
                Pair(external, internal)
            }
            AddressType.NESTED_SEGWIT -> {
                // BIP49 for Nested SegWit P2SH-P2WPKH
                val external = Descriptor.newBip49(
                    secretKey = descriptorSecretKey,
                    keychainKind = KeychainKind.EXTERNAL,
                    network = network
                )
                val internal = Descriptor.newBip49(
                    secretKey = descriptorSecretKey,
                    keychainKind = KeychainKind.INTERNAL,
                    network = network
                )
                Pair(external, internal)
            }
            AddressType.SEGWIT -> {
                // BIP84 for Native SegWit P2WPKH
                val external = Descriptor.newBip84(
                    secretKey = descriptorSecretKey,
                    keychainKind = KeychainKind.EXTERNAL,
                    network = network
                )
                val internal = Descriptor.newBip84(
                    secretKey = descriptorSecretKey,
                    keychainKind = KeychainKind.INTERNAL,
                    network = network
                )
                Pair(external, internal)
            }
            AddressType.TAPROOT -> {
                // BIP86 for Taproot P2TR
                val external = Descriptor.newBip86(
                    secretKey = descriptorSecretKey,
                    keychainKind = KeychainKind.EXTERNAL,
                    network = network
                )
                val internal = Descriptor.newBip86(
                    secretKey = descriptorSecretKey,
                    keychainKind = KeychainKind.INTERNAL,
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
     * Create watch-only descriptors from extended public key.
     * 
     * Includes key origin info [fingerprint/derivation] when available, which is
     * required for hardware wallets (SeedSigner, Coldcard, etc.) to:
     * - Match PSBT inputs to their seed for signing
     * - Verify input amounts
     * - Recognize change addresses
     *
     * Supported input formats:
     * - Bare xpub/zpub/ypub: "zpub6rFR7..."
     * - Origin-prefixed: "[73c5da0a/84'/0'/0']zpub6rFR7..."
     * - Full output descriptor: wpkh([73c5da0a/84'/0'/0']xpub6.../0/wildcard)
     * - BIP 389 multipath: `wpkh([73c5da0a/84'/0'/0']xpub6.../<0;1>/{wildcard})`
     */
    private fun createWatchOnlyDescriptors(
        extendedKey: String,
        addressType: AddressType,
        network: Network,
        masterFingerprint: String? = null
    ): Pair<Descriptor, Descriptor> {
        val input = extendedKey.trim()
        
        // Check if input is already a full output descriptor (e.g., "wpkh([fp/path]xpub/0/*)")
        val descriptorPrefixes = listOf("pkh(", "wpkh(", "tr(", "sh(wpkh(", "sh(")
        val isFullDescriptor = descriptorPrefixes.any { input.lowercase().startsWith(it) }
        
        if (isFullDescriptor) {
            return parseFullDescriptor(input, addressType, network)
        }
        
        // Parse origin info from "[fingerprint/path]xpub..." format
        val parsed = parseKeyOrigin(input)
        val bareKey = parsed.bareKey
        val fingerprint = parsed.fingerprint ?: masterFingerprint
        val originPath = parsed.derivationPath
        
        // Convert to xpub/tpub format for BDK compatibility
        val xpubKey = convertToXpub(bareKey)
        
        // Build the key expression with origin info.
        // Always include origin: [fingerprint/path]xpub/0/*
        // Uses 00000000 as fallback fingerprint when none is provided.
        // This is critical for hardware wallet compatibility:
        // - SeedSigner uses the fingerprint in BIP32 derivations to verify change outputs
        // - Without origin info, BDK uses the xpub's own fingerprint which won't match
        //   the master seed fingerprint, causing SeedSigner to reject the PSBT
        // - 00000000 triggers SeedSigner's missing-fingerprint fallback which derives
        //   keys and compares pubkeys directly (see _fill_missing_fingerprints)
        val effectiveFingerprint = fingerprint ?: "00000000"
        val path = originPath ?: addressType.accountPath
        val keyWithOrigin = "[$effectiveFingerprint/$path]$xpubKey"
        
        // Build descriptor based on the user's selected address type
        return when (addressType) {
            AddressType.LEGACY -> {
                val external = Descriptor("pkh($keyWithOrigin/0/*)", network)
                val internal = Descriptor("pkh($keyWithOrigin/1/*)", network)
                Pair(external, internal)
            }
            AddressType.NESTED_SEGWIT -> {
                val external = Descriptor("sh(wpkh($keyWithOrigin/0/*))", network)
                val internal = Descriptor("sh(wpkh($keyWithOrigin/1/*))", network)
                Pair(external, internal)
            }
            AddressType.SEGWIT -> {
                val external = Descriptor("wpkh($keyWithOrigin/0/*)", network)
                val internal = Descriptor("wpkh($keyWithOrigin/1/*)", network)
                Pair(external, internal)
            }
            AddressType.TAPROOT -> {
                val external = Descriptor("tr($keyWithOrigin/0/*)", network)
                val internal = Descriptor("tr($keyWithOrigin/1/*)", network)
                Pair(external, internal)
            }
        }
    }
    
    /**
     * Parse a full output descriptor string into external and internal descriptor pairs.
     * Handles formats like: wpkh([73c5da0a/84'/0'/0']xpub6.../0/wildcard)
     * Derives the internal (change) descriptor by replacing /0/wildcard with /1/wildcard.
     * Also supports BIP 389 multipath descriptors with <0;1> syntax — uses
     * Descriptor.toSingleDescriptors() to split into receive/change paths.
     */
    private fun parseFullDescriptor(
        descriptor: String,
        addressType: AddressType,
        network: Network
    ): Pair<Descriptor, Descriptor> {
        // Strip descriptor checksum suffix if present (e.g., "#abcdef12")
        val checksumSuffix = descriptor.substringAfterLast('#', "")
        val trimmed = descriptor.trim()
            .removeSuffix("#$checksumSuffix")
            .trim()
        
        // BIP 389 multipath descriptor: contains <0;1> syntax for combined receive/change paths
        // e.g. "wpkh([73c5da0a/84'/0'/0']xpub6.../<0;1>/*)"
        if (trimmed.contains("<0;1>") || trimmed.contains("<1;0>")) {
            val multipathDescriptor = Descriptor(trimmed, network)
            if (multipathDescriptor.isMultipath()) {
                val singles = multipathDescriptor.toSingleDescriptors()
                if (singles.size == 2) {
                    // BIP 389 defines <0;1> as receive;change ordering
                    val isReversed = trimmed.contains("<1;0>")
                    val external = if (isReversed) singles[1] else singles[0]
                    val internal = if (isReversed) singles[0] else singles[1]
                    return Pair(external, internal)
                }
            }
            // Fall through if multipath parsing fails — treat as regular descriptor
            if (BuildConfig.DEBUG) Log.w(TAG, "Multipath descriptor detected but toSingleDescriptors() failed, falling back to manual parsing")
        }
        
        // If descriptor contains /0/* it's the external; derive internal by replacing with /1/*
        val external: Descriptor
        val internal: Descriptor
        
        if (trimmed.contains("/0/*)")) {
            external = Descriptor(trimmed, network)
            internal = Descriptor(trimmed.replace("/0/*)", "/1/*)"), network)
        } else if (trimmed.contains("/1/*)")) {
            // User pasted the internal descriptor - derive external
            internal = Descriptor(trimmed, network)
            external = Descriptor(trimmed.replace("/1/*)", "/0/*)"), network)
        } else {
            // No child path specified - add /0/* and /1/*
            // Remove trailing ) and add child paths
            val base = trimmed.trimEnd(')')
            val closingParens = ")".repeat(trimmed.length - trimmed.trimEnd(')').length)
            external = Descriptor("$base/0/*$closingParens", network)
            internal = Descriptor("$base/1/*$closingParens", network)
        }
        
        return Pair(external, internal)
    }
    
    /**
     * Parsed key origin info from an extended key string.
     */
    private data class KeyOriginInfo(
        val bareKey: String,        // The bare xpub/zpub without origin prefix
        val fingerprint: String?,   // Master key fingerprint (8 hex chars), if present
        val derivationPath: String? // Derivation path without m/ prefix (e.g., "84'/0'/0'"), if present
    )
    
    /**
     * Parse key origin info from "[fingerprint/path]xpub..." format.
     * Returns the bare key and any origin info found.
     * 
     * Handles both ' and h notation for hardened derivation (84' or 84h).
     */
    private fun parseKeyOrigin(input: String): KeyOriginInfo {
        // Pattern: [fingerprint/derivation/path]xpub...
        val originPattern = """\[([a-fA-F0-9]{8})/([^\]]+)\](.+)""".toRegex()
        val match = originPattern.find(input.trim())
        
        return if (match != null) {
            val fingerprint = match.groupValues[1].lowercase()
            val path = match.groupValues[2]
                .replace("H", "'") // Normalize uppercase hardened notation (some wallets use H)
                .replace("h", "'") // Normalize lowercase hardened notation
            val bareKey = match.groupValues[3]
            KeyOriginInfo(bareKey, fingerprint, path)
        } else {
            KeyOriginInfo(input.trim(), null, null)
        }
    }
    
    /**
     * Extract master fingerprint from key material string.
     * Parses "[fingerprint/...]xpub" and "wpkh([fingerprint/...]xpub/0/wildcard)" formats.
     * Returns null if no fingerprint found.
     */
    fun extractFingerprint(keyMaterial: String): String? {
        val pattern = """\[([a-fA-F0-9]{8})/""".toRegex()
        return pattern.find(keyMaterial.trim())?.groupValues?.get(1)?.lowercase()
    }
    
    /**
     * Check if input string represents a watch-only key material.
     * Supports:
     * - Bare xpub/zpub/ypub/tpub: "zpub6rFR7..."
     * - Origin-prefixed: "[73c5da0a/84'/0'/0']zpub6rFR7..."
     * - Full output descriptors: wpkh([73c5da0a/84'/0'/0']xpub6.../0/wildcard)
     */
    fun isWatchOnlyInput(input: String): Boolean {
        val trimmed = input.trim()
        
        // Bare xpub/zpub/ypub/tpub/vpub/upub
        if (trimmed.startsWith("xpub") || trimmed.startsWith("ypub") ||
            trimmed.startsWith("zpub") || trimmed.startsWith("tpub") ||
            trimmed.startsWith("vpub") || trimmed.startsWith("upub")) {
            return true
        }
        
        // Origin-prefixed: [fingerprint/path]xpub
        if (trimmed.startsWith("[") && trimmed.contains("]")) {
            val afterBracket = trimmed.substringAfter("]")
            if (afterBracket.startsWith("xpub") || afterBracket.startsWith("ypub") ||
                afterBracket.startsWith("zpub") || afterBracket.startsWith("tpub") ||
                afterBracket.startsWith("vpub") || afterBracket.startsWith("upub")) {
                return true
            }
        }
        
        // Full output descriptor with public key
        val descriptorPrefixes = listOf("pkh(", "wpkh(", "tr(", "sh(wpkh(", "sh(")
        if (descriptorPrefixes.any { trimmed.lowercase().startsWith(it) }) {
            // Make sure it contains a public key (xpub/tpub/ypub/zpub/vpub/upub), not a private key
            val hasPublicKey = trimmed.contains("xpub") || trimmed.contains("tpub") ||
                trimmed.contains("ypub") || trimmed.contains("zpub") ||
                trimmed.contains("vpub") || trimmed.contains("upub")
            val hasPrivateKey = trimmed.contains("xprv") || trimmed.contains("tprv")
            return hasPublicKey && !hasPrivateKey
        }
        
        return false
    }
    
    /**
     * Check if input string is a WIF (Wallet Import Format) private key.
     * Mainnet: starts with 'K' or 'L' (compressed, 52 chars) or '5' (uncompressed, 51 chars)
     * Testnet: starts with 'c' (compressed) or '9' (uncompressed)
     * Validates via Base58Check decode: version byte 0x80 (mainnet) or 0xEF (testnet).
     */
    fun isWifPrivateKey(input: String): Boolean {
        val trimmed = input.trim()
        // Quick prefix check before expensive Base58 decode
        val couldBeWif = (trimmed.length == 52 && (trimmed[0] == 'K' || trimmed[0] == 'L' || trimmed[0] == 'c')) ||
            (trimmed.length == 51 && (trimmed[0] == '5' || trimmed[0] == '9'))
        if (!couldBeWif) return false
        
        return try {
            val decoded = Base58.decodeChecked(trimmed)
            val version = decoded[0].toInt() and 0xFF
            // 0x80 = mainnet, 0xEF = testnet
            val validVersion = version == 0x80 || version == 0xEF
            // Compressed: 34 bytes (1 version + 32 key + 1 compression flag)
            // Uncompressed: 33 bytes (1 version + 32 key)
            val validLength = decoded.size == 34 || decoded.size == 33
            validVersion && validLength
        } catch (_: Exception) {
            false
        }
    }
    
    /**
     * Check if a WIF key is compressed (K/L/c prefix, 52 chars).
     */
    private fun isWifCompressed(wif: String): Boolean {
        val trimmed = wif.trim()
        return trimmed.length == 52 && (trimmed[0] == 'K' || trimmed[0] == 'L' || trimmed[0] == 'c')
    }
    
    /**
     * Check if input string is a valid Bitcoin address.
     * Supports: P2PKH (1...), P2SH (3...), P2WPKH (bc1q...), P2TR (bc1p...), and testnet variants.
     */
    fun isBitcoinAddress(input: String): Boolean {
        val trimmed = input.trim()
        if (trimmed.isBlank()) return false
        return try {
            Address(trimmed, Network.BITCOIN)
            true
        } catch (_: Exception) {
            try {
                Address(trimmed, Network.TESTNET)
                true
            } catch (_: Exception) {
                false
            }
        }
    }
    
    /**
     * Detect the address type from a Bitcoin address string.
     */
    fun detectAddressType(address: String): AddressType? {
        val trimmed = address.trim()
        return when {
            trimmed.startsWith("1") -> AddressType.LEGACY
            trimmed.startsWith("3") -> AddressType.NESTED_SEGWIT
            trimmed.startsWith("bc1q") || trimmed.startsWith("tb1q") -> AddressType.SEGWIT
            trimmed.startsWith("bc1p") || trimmed.startsWith("tb1p") -> AddressType.TAPROOT
            trimmed.startsWith("m") || trimmed.startsWith("n") -> AddressType.LEGACY // testnet P2PKH
            trimmed.startsWith("2") -> AddressType.NESTED_SEGWIT // testnet P2SH
            else -> null
        }
    }
    
    /**
     * Create a single descriptor from a WIF private key.
     * Returns a non-ranged descriptor (single address, no wildcard).
     * Single-key wallets must use Wallet.createSingle() since BDK rejects
     * identical external and internal descriptors.
     */
    private fun createDescriptorFromWif(
        wif: String,
        addressType: AddressType,
        network: Network
    ): Descriptor {
        val descriptorStr = when (addressType) {
            AddressType.LEGACY -> "pkh($wif)"
            AddressType.NESTED_SEGWIT -> "sh(wpkh($wif))"
            AddressType.SEGWIT -> "wpkh($wif)"
            AddressType.TAPROOT -> "tr($wif)"
        }
        return Descriptor(descriptorStr, network)
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
            // Stop any existing proxy
            cachingProxy?.stop()
            cachingProxy = null
            
            val useTor = isTorEnabled() || config.isOnionAddress()
            val cleanHost = config.url
                .removePrefix("tcp://").removePrefix("ssl://")
                .removePrefix("http://").removePrefix("https://")
                .trim().trimEnd('/')
            
            val client: ElectrumClient
            
            if (config.useSsl) {
                // SSL connections: use caching proxy with Android-native TLS
                // BDK's rustls doesn't work on Android, so we terminate SSL in Kotlin
                val isOnion = config.isOnionAddress()

                // Eagerly verify the certificate BEFORE creating the proxy.
                // The proxy performs SSL handshakes asynchronously so TOFU
                // exceptions would be swallowed. This probe connection triggers
                // CertificateFirstUseException / CertificateMismatchException
                // synchronously so they propagate to the ViewModel for user approval.
                // Returns the verified socket so the proxy can reuse it (saves 3-10s over Tor).
                val verifiedSocket = verifyCertificateProbe(cleanHost, config.port, isOnion, useTor, useSsl = true)

                // Certificate is trusted (either stored or just approved) — create proxy.
                // Re-read the stored fingerprint (now guaranteed to exist after probe + approval)
                // so the proxy also verifies on subsequent BDK reconnections.
                // For .onion+SSL, use TOFU (not trust-all) so reconnections are also verified.
                val stored = secureStorage.getServerCertFingerprint(cleanHost, config.port)
                val bridgeTrustManager = TofuTrustManager(cleanHost, config.port, stored, isOnionHost = false)
                val proxy = CachingElectrumProxy(
                    targetHost = cleanHost,
                    targetPort = config.port,
                    useSsl = true,
                    useTorProxy = useTor,
                    connectionTimeoutMs = if (useTor) 90000 else 60000,
                    soTimeoutMs = if (useTor) 90000 else 60000,
                    sslTrustManager = bridgeTrustManager,
                    cache = electrumCache
                )
                // Pass the verified socket so the proxy reuses it for the first
                // BDK connection instead of opening a redundant SOCKS5+SSL connection.
                verifiedSocket?.let { proxy.setPreConnectedSocket(it) }
                val localPort = proxy.start()
                cachingProxy = proxy
                
                if (BuildConfig.DEBUG) Log.d(TAG, "Caching proxy started on port $localPort -> $cleanHost:${config.port} (tor=$useTor)")
                // BDK connects to local plaintext port; proxy handles SSL + caching
                client = ElectrumClient("tcp://127.0.0.1:$localPort")
            } else if (useTor) {
                // TCP + Tor: BDK handles SOCKS5 natively
                // Still create a proxy for caching + direct query consolidation
                val proxy = CachingElectrumProxy(
                    targetHost = cleanHost,
                    targetPort = config.port,
                    useSsl = false,
                    useTorProxy = true,
                    connectionTimeoutMs = 90000,
                    soTimeoutMs = 90000,
                    cache = electrumCache
                )
                val localPort = proxy.start()
                cachingProxy = proxy
                
                if (BuildConfig.DEBUG) Log.d(TAG, "Caching proxy started on port $localPort -> $cleanHost:${config.port} (tor=true, ssl=false)")
                client = ElectrumClient("tcp://127.0.0.1:$localPort")
            } else {
                // TCP + clearnet: use proxy for caching
                val proxy = CachingElectrumProxy(
                    targetHost = cleanHost,
                    targetPort = config.port,
                    useSsl = false,
                    useTorProxy = false,
                    connectionTimeoutMs = 60000,
                    soTimeoutMs = 60000,
                    cache = electrumCache
                )
                val localPort = proxy.start()
                cachingProxy = proxy
                
                if (BuildConfig.DEBUG) Log.d(TAG, "Caching proxy started on port $localPort -> $cleanHost:${config.port} (tor=false, ssl=false)")
                client = ElectrumClient("tcp://127.0.0.1:$localPort")
            }
            
            electrumClient = client
            if (BuildConfig.DEBUG) Log.d(TAG, "ElectrumClient created successfully")
            
            // Subscribe to block headers for new-block detection
            subscribeBlockHeaders(client)
            
            // Save the server config with proper ID
            val savedConfig = saveElectrumServer(config)
            secureStorage.setActiveServerId(savedConfig.id)
            
            // Query server relay fee via the proxy's shared upstream socket
            kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                queryServerMinFeeRate()
            }

            // Prune stale unconfirmed verbose tx cache entries
            kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                electrumCache.pruneStaleUnconfirmed()
            }
            
            WalletResult.Success(Unit)
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Failed to connect to Electrum: ${e.javaClass.simpleName} - ${e.message}", e)
            // Clean up proxy on failure
            cachingProxy?.stop()
            cachingProxy = null
            WalletResult.Error("Failed to connect to server", e)
        }
    }
    
    /**
     * Connect to an existing saved server by ID
     */
    suspend fun connectToServer(serverId: String): WalletResult<Unit> = withContext(Dispatchers.IO) {
        val config = secureStorage.getElectrumServer(serverId)
            ?: return@withContext WalletResult.Error("Server not found")
        
        try {
            // Stop any existing proxy
            cachingProxy?.stop()
            cachingProxy = null
            
            val useTor = isTorEnabled() || config.isOnionAddress()
            val cleanHost = config.url
                .removePrefix("tcp://").removePrefix("ssl://")
                .removePrefix("http://").removePrefix("https://")
                .trim().trimEnd('/')
            
            val client: ElectrumClient
            
            if (config.useSsl) {
                // SSL connections: use caching proxy with Android-native TLS
                val isOnion = config.isOnionAddress()

                // Eagerly verify the certificate BEFORE creating the proxy.
                // Returns the verified socket for proxy reuse (saves 3-10s over Tor).
                val verifiedSocket = verifyCertificateProbe(cleanHost, config.port, isOnion, useTor, useSsl = true)

                // Certificate is trusted — create proxy with stored fingerprint
                // for ongoing verification during BDK reconnections.
                // For .onion+SSL, use TOFU (not trust-all) so reconnections are also verified.
                val stored = secureStorage.getServerCertFingerprint(cleanHost, config.port)
                val bridgeTrustManager = TofuTrustManager(cleanHost, config.port, stored, isOnionHost = false)
                val proxy = CachingElectrumProxy(
                    targetHost = cleanHost,
                    targetPort = config.port,
                    useSsl = true,
                    useTorProxy = useTor,
                    connectionTimeoutMs = if (useTor) 90000 else 60000,
                    soTimeoutMs = if (useTor) 90000 else 60000,
                    sslTrustManager = bridgeTrustManager,
                    cache = electrumCache
                )
                verifiedSocket?.let { proxy.setPreConnectedSocket(it) }
                val localPort = proxy.start()
                cachingProxy = proxy

                if (BuildConfig.DEBUG) Log.d(TAG, "Caching proxy started on port $localPort -> $cleanHost:${config.port} (tor=$useTor)")
                client = ElectrumClient("tcp://127.0.0.1:$localPort")
            } else if (useTor) {
                // TCP + Tor: route through caching proxy for consolidation
                val proxy = CachingElectrumProxy(
                    targetHost = cleanHost,
                    targetPort = config.port,
                    useSsl = false,
                    useTorProxy = true,
                    connectionTimeoutMs = 90000,
                    soTimeoutMs = 90000,
                    cache = electrumCache
                )
                val localPort = proxy.start()
                cachingProxy = proxy

                if (BuildConfig.DEBUG) Log.d(TAG, "Caching proxy started on port $localPort -> $cleanHost:${config.port} (tor=true, ssl=false)")
                client = ElectrumClient("tcp://127.0.0.1:$localPort")
            } else {
                // TCP + clearnet: use proxy for caching
                val proxy = CachingElectrumProxy(
                    targetHost = cleanHost,
                    targetPort = config.port,
                    useSsl = false,
                    useTorProxy = false,
                    connectionTimeoutMs = 60000,
                    soTimeoutMs = 60000,
                    cache = electrumCache
                )
                val localPort = proxy.start()
                cachingProxy = proxy

                if (BuildConfig.DEBUG) Log.d(TAG, "Caching proxy started on port $localPort -> $cleanHost:${config.port} (tor=false, ssl=false)")
                client = ElectrumClient("tcp://127.0.0.1:$localPort")
            }

            electrumClient = client
            if (BuildConfig.DEBUG) Log.d(TAG, "ElectrumClient created successfully")

            // Subscribe to block headers for new-block detection
            subscribeBlockHeaders(client)

            secureStorage.setActiveServerId(serverId)

            // Query server relay fee via the proxy's shared upstream socket
            kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                queryServerMinFeeRate()
            }

            // Prune stale unconfirmed verbose tx cache entries
            kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                electrumCache.pruneStaleUnconfirmed()
            }

            WalletResult.Success(Unit)
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Failed to connect to Electrum: ${e.javaClass.simpleName} - ${e.message}", e)
            cachingProxy?.stop()
            cachingProxy = null
            WalletResult.Error("Failed to connect to server", e)
        }
    }
    
    /**
     * Quick sync - only syncs already-revealed addresses
     * Use for regular balance refresh (fast)
     *
     * Optimizations:
     * - Mutex prevents concurrent sync operations
     * - Script hash pre-check skips BDK sync when nothing changed on server
     * - Adaptive batch sizing (halves on timeout, resets on success)
     */
    suspend fun sync(): WalletResult<Unit> = withContext(Dispatchers.IO) {
        val activeWalletId = secureStorage.getActiveWalletId()
            ?: return@withContext WalletResult.Error("No active wallet")
        
        // Watch address wallets have no BDK wallet — route to Electrum-only sync
        if (wallet == null && secureStorage.hasWatchAddress(activeWalletId)) {
            return@withContext syncWatchAddress(activeWalletId)
        }
        
        val currentWallet = wallet ?: return@withContext WalletResult.Error("Wallet not initialized")
        val client = electrumClient ?: return@withContext WalletResult.Error("Not connected to Electrum server")
        
        // If wallet needs full sync (first time or manually requested), do that instead
        if (secureStorage.needsFullSync(activeWalletId)) {
            if (BuildConfig.DEBUG) Log.d(TAG, "Wallet needs full sync, redirecting to fullSync()")
            return@withContext fullSync()
        }
        
        // Mutex: skip if another sync is already running
        if (!syncMutex.tryLock()) {
            if (BuildConfig.DEBUG) Log.d(TAG, "Sync already in progress, skipping")
            return@withContext WalletResult.Success(Unit)
        }
        
        try {
            _walletState.value = _walletState.value.copy(isSyncing = true, error = null)
            
            // Health check: ping the server to verify connection is alive
            try {
                client.ping()
            } catch (e: Exception) {
                _walletState.value = _walletState.value.copy(isSyncing = false)
                if (BuildConfig.DEBUG) Log.w(TAG, "Server ping failed, connection may be dead: ${e.message}")
                return@withContext WalletResult.Error("Server connection lost - try reconnecting")
            }
            
            // Pre-check: skip sync if no new block AND no script hash changes.
            // blockHeadersPop() is free (local queue), script hash check is one
            // round-trip per sampled address. Together they make background syncs
            // nearly free when nothing has changed.
            val newBlockDetected = hasNewBlock(client)
            if (!newBlockDetected && scriptHashStatusCache.isNotEmpty()) {
                val proxy = cachingProxy
                val hasChanges = if (proxy != null) {
                    proxy.checkForScriptHashChanges(scriptHashStatusCache)
                } else {
                    true // No proxy — sync to be safe
                }
                if (!hasChanges) {
                    _walletState.value = _walletState.value.copy(isSyncing = false)
                    if (BuildConfig.DEBUG) Log.d(TAG, "Pre-check: no new block and no script hash changes, skipping sync")
                    return@withContext WalletResult.Success(Unit)
                }
            }
            
            if (BuildConfig.DEBUG) Log.d(TAG, "Starting quick sync (batch=$currentBatchSize)")
            
            val syncProgress = java.util.concurrent.atomic.AtomicLong(0)
            val syncRequest = currentWallet.startSyncWithRevealedSpks()
                .inspectSpks(object : SyncScriptInspector {
                    override fun inspect(script: Script, total: ULong) {
                        val current = syncProgress.incrementAndGet().toULong()
                        _walletState.value = _walletState.value.copy(
                            syncProgress = SyncProgress(current = current, total = total)
                        )
                    }
                })
                .build()
            val batchSize = currentBatchSize
            val update = withTimeoutOrNull(QUICK_SYNC_TIMEOUT_MS) {
                client.sync(
                    request = syncRequest,
                    batchSize = batchSize,
                    fetchPrevTxouts = false
                )
            }
            
            if (update == null) {
                // Timeout: halve batch size for next attempt (adaptive)
                currentBatchSize = maxOf(MIN_BATCH_SIZE, currentBatchSize / 2UL)
                secureStorage.saveSyncBatchSize(currentBatchSize.toLong())
                _walletState.value = _walletState.value.copy(isSyncing = false, syncProgress = null)
                if (BuildConfig.DEBUG) Log.w(TAG, "Quick sync timed out, reducing batch to $currentBatchSize")
                return@withContext WalletResult.Error(
                    "Sync timed out - check your server connection"
                )
            }
            
            // Success: reset batch size to default
            currentBatchSize = SYNC_BATCH_SIZE
            secureStorage.saveSyncBatchSize(currentBatchSize.toLong())
            
            // Apply update and get typed events (TxConfirmed, TxReplaced, etc.)
            val events = currentWallet.applyUpdateEvents(update)
            
            // Always persist — chain tip updates even when no tx events
            walletPersister?.let { currentWallet.persist(it) }
            
            // Clear syncing state so the UI dialog dismisses promptly.
            // The post-processing (updateWalletState, cache refresh) runs after
            // and the balance/transactions update seamlessly in the background.
            _walletState.value = _walletState.value.copy(isSyncing = false, syncProgress = null)
            
            if (events.isNotEmpty()) {
                if (BuildConfig.DEBUG) Log.d(TAG, "Sync events: ${events.size} changes detected")
                for (event in events) {
                    if (BuildConfig.DEBUG) Log.d(TAG, "  Event: $event")
                }
                // Use incremental state update for quick sync — only reprocess
                // transactions affected by events instead of full O(n) rebuild.
                updateWalletStateIncremental(events)
                refreshScriptHashCache(currentWallet)
            } else {
                if (BuildConfig.DEBUG) Log.d(TAG, "Quick sync: no changes detected, skipping state rebuild")
            }
            
            if (BuildConfig.DEBUG) Log.d(TAG, "Quick sync completed successfully")
            WalletResult.Success(Unit)
        } catch (e: Exception) {
            _walletState.value = _walletState.value.copy(
                isSyncing = false,
                syncProgress = null,
                error = "Sync failed - check your connection"
            )
            if (BuildConfig.DEBUG) Log.e(TAG, "Quick sync failed: ${e.message}", e)
            WalletResult.Error("Sync failed - check your connection", e)
        } finally {
            syncMutex.unlock()
        }
    }
    
    /**
     * Full sync - scans all addresses up to the gap limit for address discovery
     * Use for first import or manual full rescan (slow)
     */
    suspend fun fullSync(showProgress: Boolean = true): WalletResult<Unit> = withContext(Dispatchers.IO) {
        val currentWallet = wallet ?: return@withContext WalletResult.Error("Wallet not initialized")
        val client = electrumClient ?: return@withContext WalletResult.Error("Not connected to Electrum server")
        val activeWalletId = secureStorage.getActiveWalletId()
            ?: return@withContext WalletResult.Error("No active wallet")
        
        // Mutex: wait for any running sync to finish before starting full scan
        syncMutex.withLock {
            try {
                _walletState.value = _walletState.value.copy(isSyncing = true, isFullSyncing = showProgress, error = null)
                if (BuildConfig.DEBUG) Log.d(TAG, "Starting full sync (address discovery, batch=$currentBatchSize)")
                
                val fullScanProgress = java.util.concurrent.atomic.AtomicLong(0)
                val fullScanRequest = currentWallet.startFullScan()
                    .inspectSpksForAllKeychains(object : FullScanScriptInspector {
                        override fun inspect(keychain: KeychainKind, index: UInt, script: Script) {
                            val current = fullScanProgress.incrementAndGet().toULong()
                            val keychainName = if (keychain == KeychainKind.EXTERNAL) "receive" else "change"
                            _walletState.value = _walletState.value.copy(
                                syncProgress = SyncProgress(
                                    current = current,
                                    total = 0UL,
                                    keychain = "$keychainName #$index",
                                    status = "Scanned $current addresses..."
                                )
                            )
                        }
                    })
                    .build()

                // Pre-warm BDK's internal tx_cache to avoid cold-start penalty.
                //
                // The CachingElectrumProxy intercepts blockchain.transaction.get requests
                // from BDK and serves cached tx hex from SQLite instantly. For txids not
                // yet in the persistent cache, we pipeline-fetch them via the proxy's
                // shared upstream socket to warm both the server-side cache and our local
                // SQLite cache. BDK's serial fetchTx() calls then hit the proxy cache.
                //
                // Net effect: fullScan's internal fetch_tx() calls are near-instant for
                // all known txids (proxy serves from SQLite without network round-trips).
                _walletState.value = _walletState.value.copy(
                    syncProgress = SyncProgress(status = "Preparing sync...")
                )
                val knownTxids = currentWallet.transactions().map { it.transaction.computeTxid() }
                if (knownTxids.isNotEmpty()) {
                    // Pipeline-fetch any txids not yet in our persistent cache.
                    // The proxy caches responses in SQLite, so future fetchTx() calls
                    // from BDK will be served from cache without network round-trips.
                    val proxy = cachingProxy
                    if (proxy != null) {
                        val txidStrings = knownTxids.map { it.toString() }
                        val uncached = txidStrings.filter { electrumCache.getRawTx(it) == null }
                        if (uncached.isNotEmpty()) {
                            proxy.pipelineFetchTransactions(uncached)
                            if (BuildConfig.DEBUG) Log.d(TAG, "Pipeline-fetched ${uncached.size} uncached txs (${txidStrings.size - uncached.size} already cached)")
                        } else {
                            if (BuildConfig.DEBUG) Log.d(TAG, "All ${txidStrings.size} txs already in persistent cache")
                        }
                    }
                    // BDK serial fetchTx — hits proxy cache for known txids
                    for (txid in knownTxids) {
                        try {
                            client.fetchTx(txid)
                        } catch (e: Exception) {
                            if (BuildConfig.DEBUG) Log.w(TAG, "tx_cache warm failed for $txid: ${e.message}")
                        }
                    }
                    if (BuildConfig.DEBUG) Log.d(TAG, "Pre-warmed tx_cache with ${knownTxids.size} transactions")
                }

                // No timeout on full scan - large wallets with extensive tx history
                // can legitimately take minutes. TCP-level timeouts handle dead connections.
                // Only fetch prev txouts on the very first sync (needsFullSync=true).
                // After that they're persisted in the database; re-fetching them on every
                // app-launch full scan adds extra cold Electrum queries for no benefit.
                val needsPrevTxouts = secureStorage.needsFullSync(activeWalletId)
                val update = client.fullScan(
                    request = fullScanRequest,
                    stopGap = 20UL,
                    batchSize = FULL_SCAN_BATCH_SIZE,
                    fetchPrevTxouts = needsPrevTxouts
                )
                
                // Success: reset batch size
                currentBatchSize = SYNC_BATCH_SIZE
                secureStorage.saveSyncBatchSize(currentBatchSize.toLong())
                
                // Apply update with events
                _walletState.value = _walletState.value.copy(
                    syncProgress = SyncProgress(status = "Applying updates...")
                )
                val events = currentWallet.applyUpdateEvents(update)
                if (BuildConfig.DEBUG) Log.d(TAG, "Full scan events: ${events.size} changes detected")
                
                // Persist changes to database
                _walletState.value = _walletState.value.copy(
                    syncProgress = SyncProgress(status = "Saving to database...")
                )
                walletPersister?.let { currentWallet.persist(it) }
                
                // Mark full sync as complete - future syncs will be quick
                secureStorage.setNeedsFullSync(activeWalletId, false)
                
                // Save the full sync timestamp
                secureStorage.saveLastFullSyncTime(activeWalletId, System.currentTimeMillis())
                
                _walletState.value = _walletState.value.copy(
                    syncProgress = SyncProgress(status = "Processing transactions...")
                )
                updateWalletState()
                
                // Reclaim revealed-but-unused addresses that have no label
                try { reclaimUnusedAddresses() } catch (e: Exception) {
                    if (BuildConfig.DEBUG) Log.w(TAG, "Address reclaim failed: ${e.message}")
                }
                
                // Refresh script hash cache AFTER updateWalletState so that
                // the current receive address is included in the cache.
                _walletState.value = _walletState.value.copy(
                    syncProgress = SyncProgress(status = "Refreshing address cache...")
                )
                refreshScriptHashCache(currentWallet)
                _walletState.value = _walletState.value.copy(isSyncing = false, isFullSyncing = false, syncProgress = null)
                
                if (BuildConfig.DEBUG) Log.d(TAG, "Full sync completed successfully")
                WalletResult.Success(Unit)
            } catch (e: Exception) {
                _walletState.value = _walletState.value.copy(
                    isSyncing = false,
                    isFullSyncing = false,
                    syncProgress = null,
                    error = "Full sync failed - check your connection"
                )
                if (BuildConfig.DEBUG) Log.e(TAG, "Full sync failed: ${e.message}", e)
                WalletResult.Error("Full sync failed - check your connection", e)
            }
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
     * Compute the Electrum script hash from a BDK Script object.
     * Electrum uses reversed SHA256 of the scriptPubKey bytes as the "script hash".
     */
    private fun computeScriptHash(script: Script): String {
        // BDK's Script.toBytes() returns List<UByte>, convert to ByteArray for SHA-256
        val scriptBytes = script.toBytes().map { it.toByte() }.toByteArray()
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(scriptBytes)
        return hash.reversedArray().joinToString("") { "%02x".format(it) }
    }
    
    /**
     * Compute the Electrum script hash from a Bitcoin address string.
     */
    fun computeScriptHashForAddress(address: String): String? {
        return try {
            val addr = try {
                Address(address, Network.BITCOIN)
            } catch (_: Exception) {
                Address(address, Network.TESTNET)
            }
            computeScriptHash(addr.scriptPubkey())
        } catch (_: Exception) {
            null
        }
    }
    
    /**
     * Build wallet state for a watch address wallet using Electrum queries.
     */
    private fun getWatchAddressState(
        walletId: String,
        activeWallet: StoredWallet?,
        allWallets: List<StoredWallet>
    ): WalletState {
        val address = secureStorage.getWatchAddress(walletId) ?: return WalletState(
            isInitialized = allWallets.isNotEmpty(),
            wallets = allWallets,
            activeWallet = activeWallet
        )
        
        val scriptHash = computeScriptHashForAddress(address)
        val proxy = cachingProxy
        
        if (scriptHash == null || proxy == null) {
            return WalletState(
                isInitialized = true,
                wallets = allWallets,
                activeWallet = activeWallet,
                currentAddress = address,
                currentAddressInfo = ReceiveAddressInfo(address = address)
            )
        }
        
        // Query balance from Electrum
        val balancePair = try { proxy.getScriptHashBalance(scriptHash) } catch (_: Exception) { null }
        val confirmed = balancePair?.first?.toULong() ?: 0UL
        val unconfirmed = balancePair?.second ?: 0L
        
        // Query history from Electrum and fetch amounts/timestamps for each tx
        val history = try { proxy.getScriptHashHistory(scriptHash) } catch (_: Exception) { null }
        val transactions = history?.map { (txid, height) ->
            // Fetch the net amount, timestamp, and counterparty from verbose tx data
            val txInfo = try { proxy.getAddressTxInfo(txid, address) } catch (_: Exception) { null }
            val isReceive = (txInfo?.netAmountSats ?: 0L) > 0L
            TransactionDetails(
                txid = txid,
                amountSats = txInfo?.netAmountSats ?: 0L,
                fee = txInfo?.feeSats?.toULong(),
                confirmationTime = if (height > 0) ConfirmationTime(
                    height = height.toUInt(),
                    timestamp = (txInfo?.timestamp ?: 0L).toULong()
                ) else null,
                isConfirmed = height > 0,
                timestamp = txInfo?.timestamp,
                // For receives: show the watched address ("received at")
                // For sends: show the recipient address
                address = if (isReceive) address else (txInfo?.counterpartyAddress ?: address)
            )
        }?.sortedWith(compareBy<TransactionDetails> { it.isConfirmed } // unconfirmed first
            .thenByDescending { it.confirmationTime?.height ?: 0U }) // then by height descending
            ?: emptyList()
        
        return WalletState(
            isInitialized = true,
            wallets = allWallets,
            activeWallet = activeWallet,
            balanceSats = confirmed,
            pendingIncomingSats = if (unconfirmed > 0) unconfirmed.toULong() else 0UL,
            pendingOutgoingSats = if (unconfirmed < 0) (-unconfirmed).toULong() else 0UL,
            transactions = transactions,
            currentAddress = address,
            currentAddressInfo = ReceiveAddressInfo(address = address)
        )
    }
    
    /**
     * Sync a watch address wallet by querying Electrum for balance/history updates.
     * Returns true if state was updated.
     */
    suspend fun syncWatchAddress(walletId: String): WalletResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val address = secureStorage.getWatchAddress(walletId)
                ?: return@withContext WalletResult.Error("No watch address found")
            
            _walletState.value = _walletState.value.copy(isSyncing = true)
            
            // Just update the wallet state — it queries Electrum inside getWatchAddressState
            updateWalletState()
            
            val now = System.currentTimeMillis()
            _walletState.value = _walletState.value.copy(
                isSyncing = false,
                lastSyncTimestamp = now
            )
            
            // Single-address wallets have no address discovery, so any sync is a full sync
            secureStorage.saveLastFullSyncTime(walletId, now)
            
            WalletResult.Success(Unit)
        } catch (e: Exception) {
            _walletState.value = _walletState.value.copy(isSyncing = false)
            WalletResult.Error("Watch address sync failed", e)
        }
    }
    
    /**
     * Check if the currently active wallet is a watch address wallet.
     */
    fun isWatchAddressWallet(): Boolean {
        val activeId = secureStorage.getActiveWalletId() ?: return false
        return secureStorage.hasWatchAddress(activeId)
    }
    
    /**
     * Refresh the script hash status cache via the caching proxy.
     * Called after a successful sync that detected changes, to establish
     * a baseline for the next pre-check cycle.
     */
    private fun refreshScriptHashCache(currentWallet: Wallet) {
        // If the subscription socket has full-coverage cache (from
        // startRealTimeSubscriptions), don't overwrite with a 5-address sample.
        // The full cache is maintained by the subscription listener + sync cycle.
        if (subscribedScriptHashes.isNotEmpty()) {
            if (BuildConfig.DEBUG) Log.d(TAG, "Skipping sample refresh — subscription cache has ${subscribedScriptHashes.size} entries")
            return
        }

        try {
            val sampleHashes = getSampleScriptHashes(currentWallet)
            if (sampleHashes.isEmpty()) return
            val proxy = cachingProxy ?: return
            
            val statuses = proxy.subscribeScriptHashes(sampleHashes)
            if (statuses.isNotEmpty()) {
                scriptHashStatusCache.clear()
                scriptHashStatusCache.putAll(statuses)
                if (BuildConfig.DEBUG) Log.d(TAG, "Refreshed script hash cache with ${statuses.size} entries")
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.w(TAG, "Failed to refresh script hash cache: ${e.message}")
        }
    }
    
    /**
     * Get a sample of script hashes from revealed addresses.
     * Includes:
     * - The current receive address (nextUnusedAddress) — most likely to receive funds
     * - The last few revealed addresses from both keychains (high-index tail)
     * This ensures the pre-check detects incoming txs on the address shown to the user,
     * even if it's an earlier index not in the tail sample window.
     */
    private fun getSampleScriptHashes(currentWallet: Wallet): List<String> {
        val hashSet = linkedSetOf<String>() // preserve insertion order, deduplicate
        try {
            // Always include the current receive address — this is the address shown
            // on the receive screen and the most likely to get incoming payments.
            try {
                val currentReceiveAddr = currentWallet.nextUnusedAddress(KeychainKind.EXTERNAL)
                hashSet.add(computeScriptHash(currentReceiveAddr.address.scriptPubkey()))
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.w(TAG, "Could not get nextUnusedAddress for pre-check: ${e.message}")
            }

            // Sample from external (receive) keychain — last N revealed addresses
            val lastExternal = currentWallet.derivationIndex(KeychainKind.EXTERNAL)
            if (lastExternal != null) {
                val start = if (lastExternal >= SCRIPT_HASH_SAMPLE_SIZE.toUInt())
                    lastExternal - SCRIPT_HASH_SAMPLE_SIZE.toUInt() + 1u else 0u
                for (i in start..lastExternal) {
                    val addr = currentWallet.peekAddress(KeychainKind.EXTERNAL, i)
                    hashSet.add(computeScriptHash(addr.address.scriptPubkey()))
                }
            }
            // Sample from internal (change) keychain
            val lastInternal = currentWallet.derivationIndex(KeychainKind.INTERNAL)
            if (lastInternal != null) {
                val start = if (lastInternal >= (SCRIPT_HASH_SAMPLE_SIZE / 2).toUInt())
                    lastInternal - (SCRIPT_HASH_SAMPLE_SIZE / 2).toUInt() + 1u else 0u
                for (i in start..lastInternal) {
                    val addr = currentWallet.peekAddress(KeychainKind.INTERNAL, i)
                    hashSet.add(computeScriptHash(addr.address.scriptPubkey()))
                }
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.w(TAG, "Error getting sample script hashes: ${e.message}")
        }
        return hashSet.toList()
    }
    
    /**
     * Get ALL revealed script hashes for both keychains + the current receive address.
     * Used for subscribing to real-time push notifications on the subscription socket.
     * Unlike [getSampleScriptHashes] which only returns a small tail sample, this
     * covers every address the wallet has ever revealed.
     */
    private fun getAllRevealedScriptHashes(currentWallet: Wallet): List<String> {
        val hashSet = linkedSetOf<String>()
        try {
            // Current receive address — most likely to receive funds
            try {
                val currentReceiveAddr = currentWallet.nextUnusedAddress(KeychainKind.EXTERNAL)
                hashSet.add(computeScriptHash(currentReceiveAddr.address.scriptPubkey()))
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.w(TAG, "Could not get nextUnusedAddress: ${e.message}")
            }

            // All revealed external (receive) addresses
            val lastExternal = currentWallet.derivationIndex(KeychainKind.EXTERNAL)
            if (lastExternal != null) {
                for (i in 0u..lastExternal) {
                    val addr = currentWallet.peekAddress(KeychainKind.EXTERNAL, i)
                    hashSet.add(computeScriptHash(addr.address.scriptPubkey()))
                }
            }
            // All revealed internal (change) addresses
            val lastInternal = currentWallet.derivationIndex(KeychainKind.INTERNAL)
            if (lastInternal != null) {
                for (i in 0u..lastInternal) {
                    val addr = currentWallet.peekAddress(KeychainKind.INTERNAL, i)
                    hashSet.add(computeScriptHash(addr.address.scriptPubkey()))
                }
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.w(TAG, "Error getting all revealed script hashes: ${e.message}")
        }
        return hashSet.toList()
    }

    /**
     * Clear the script hash status cache and subscription tracking.
     * Called on wallet switch, disconnect, and after transactions that change the UTXO set.
     */
    fun clearScriptHashCache() {
        scriptHashStatusCache.clear()
        subscribedScriptHashes.clear()
        electrumCache.clearScriptHashStatuses()
    }

    // ==================== Real-Time Subscriptions ====================

    /**
     * Result of the subscription + smart sync startup.
     */
    enum class SubscriptionResult {
        /** No changes detected — skipped sync entirely. */
        NO_CHANGES,
        /** Changes detected — sync completed successfully. */
        SYNCED,
        /** First-ever import — full sync completed. */
        FULL_SYNCED,
        /** Subscription or sync failed. */
        FAILED
    }

    /**
     * Subscribe ALL revealed addresses + block headers on the proxy's dedicated
     * subscription socket, compare statuses to the persisted cache, sync only if
     * changes detected, then start collecting push notifications.
     *
     * This replaces the old initialSync() + startRealTimeSubscriptions() two-step.
     * On app relaunch when nothing changed while the app was closed, this skips
     * BDK sync entirely — the wallet state from the database is already current.
     *
     * Flow:
     * 1. Subscribe all addresses → get current statuses from server
     * 2. Load persisted statuses from ElectrumCache
     * 3. Compare: if identical → skip sync (nothing changed while app was closed)
     * 4. If different or no persisted cache → run sync()
     * 5. Persist new statuses for next app launch
     * 6. Start notification listener for real-time updates
     *
     * Safe to call multiple times — stops the previous collector first.
     */
    suspend fun startRealTimeSubscriptions(): SubscriptionResult = withContext(Dispatchers.IO) {
        val currentWallet = wallet ?: return@withContext SubscriptionResult.FAILED
        val proxy = cachingProxy ?: return@withContext SubscriptionResult.FAILED
        val activeWalletId = secureStorage.getActiveWalletId()
            ?: return@withContext SubscriptionResult.FAILED

        // Stop any existing collector
        stopNotificationCollector()

        // Check if wallet needs full sync first (first-ever import)
        if (secureStorage.needsFullSync(activeWalletId)) {
            if (BuildConfig.DEBUG) Log.d(TAG, "Wallet needs full sync — running before subscriptions")
            val syncResult = sync() // Redirects to fullSync internally
            if (syncResult is WalletResult.Error) {
                return@withContext SubscriptionResult.FAILED
            }
            // After full sync, re-read the wallet (addresses are now revealed)
        }

        // Gather ALL revealed script hashes
        val allScriptHashes = getAllRevealedScriptHashes(currentWallet)
        if (allScriptHashes.isEmpty()) return@withContext SubscriptionResult.FAILED

        if (BuildConfig.DEBUG) Log.d(TAG, "Subscribing ${allScriptHashes.size} addresses for real-time updates")

        // Subscribe on the proxy's dedicated subscription socket.
        // Returns current statuses AND starts the notification listener.
        val currentStatuses = proxy.startSubscriptions(allScriptHashes)

        if (currentStatuses.isEmpty()) {
            if (BuildConfig.DEBUG) Log.w(TAG, "Failed to start subscriptions — proxy returned empty")
            return@withContext SubscriptionResult.FAILED
        }

        // Track which script hashes are subscribed
        subscribedScriptHashes.clear()
        subscribedScriptHashes.addAll(currentStatuses.keys)

        // Do NOT populate scriptHashStatusCache yet — if we populate it now with
        // the server's current statuses, sync()'s pre-check will compare the cache
        // against the same server and see "no changes", skipping BDK sync entirely.
        // The cache must only reflect what BDK has already processed.
        scriptHashStatusCache.clear()

        // Compare against persisted statuses from previous session
        val persistedStatuses = electrumCache.loadScriptHashStatuses()
        val needsSync = if (persistedStatuses.isEmpty()) {
            if (BuildConfig.DEBUG) Log.d(TAG, "No persisted statuses — sync needed")
            true
        } else {
            // Check if any status changed or if new addresses were added
            var changed = false
            for ((scriptHash, currentStatus) in currentStatuses) {
                val persistedStatus = persistedStatuses[scriptHash]
                // If the script hash is new (not in persisted), or status differs
                if (!persistedStatuses.containsKey(scriptHash) || persistedStatus != currentStatus) {
                    if (BuildConfig.DEBUG) Log.d(TAG, "Status change detected for $scriptHash")
                    changed = true
                    break
                }
            }
            changed
        }

        val result: SubscriptionResult

        if (needsSync) {
            if (BuildConfig.DEBUG) Log.d(TAG, "Changes detected — running sync")
            // Cache is empty → sync()'s pre-check is bypassed → BDK sync runs
            val syncResult = sync()
            result = if (syncResult is WalletResult.Success) {
                SubscriptionResult.SYNCED
            } else {
                SubscriptionResult.FAILED
            }
        } else {
            if (BuildConfig.DEBUG) Log.d(TAG, "No changes since last session — skipping sync")
            result = SubscriptionResult.NO_CHANGES
        }

        // NOW populate the cache — BDK has processed all changes (or confirmed
        // nothing changed). These statuses represent the current server state
        // that BDK is in sync with.
        scriptHashStatusCache.putAll(currentStatuses)

        // Persist current statuses for next app launch
        electrumCache.saveScriptHashStatuses(currentStatuses)

        if (BuildConfig.DEBUG) Log.d(TAG, "Starting notification collector (result=$result)")

        // Start collecting push notifications for real-time updates
        startNotificationCollector(proxy)

        result
    }

    /**
     * Collect push notifications from the proxy and trigger targeted sync.
     * Uses a 1-second debounce window (like Sparrow) to coalesce rapid
     * notifications (e.g., a new block confirming multiple wallet txs).
     */
    private fun startNotificationCollector(proxy: CachingElectrumProxy) {
        notificationCollectorJob?.cancel()
        notificationCollectorJob = repositoryScope.launch {
            if (BuildConfig.DEBUG) Log.d(TAG, "Notification collector started")

            // Debounce buffer: accumulate notifications for 1 second of quiet
            var pendingNotifications = mutableListOf<ElectrumNotification>()
            var lastNotificationTime = 0L

            proxy.notifications.collect { notification ->
                if (!isActive) return@collect

                pendingNotifications.add(notification)
                lastNotificationTime = System.currentTimeMillis()

                // Handle immediate block height update (for confirmation counts)
                if (notification is ElectrumNotification.NewBlockHeader) {
                    lastKnownBlockHeight = notification.height.toULong()
                    // Update block height in wallet state immediately for UI
                    val current = _walletState.value
                    if (current.blockHeight != notification.height.toUInt()) {
                        _walletState.value = current.copy(blockHeight = notification.height.toUInt())
                    }
                }

                // Launch a debounced sync: wait 1 second after the last notification,
                // then trigger sync for all accumulated changes.
                // This is effectively a trailing-edge debounce.
                launch debounceSync@{
                    delay(NOTIFICATION_DEBOUNCE_MS)

                    // Check if more notifications arrived during the delay
                    if (System.currentTimeMillis() - lastNotificationTime < NOTIFICATION_DEBOUNCE_MS) {
                        return@debounceSync // Another coroutine will handle it
                    }

                    // Drain the pending buffer
                    val batch = pendingNotifications.toList()
                    pendingNotifications = mutableListOf()

                    if (batch.isEmpty()) return@debounceSync

                    val scriptHashChanges = batch.count { it is ElectrumNotification.ScriptHashChanged }
                    val blockChanges = batch.count { it is ElectrumNotification.NewBlockHeader }
                    if (BuildConfig.DEBUG) Log.d(TAG, "Debounced sync trigger: $scriptHashChanges address changes, $blockChanges new blocks")

                    // Trigger a quick sync — the script hash pre-check inside sync()
                    // will see the changes and do a targeted BDK sync.
                    // Clear the status cache so sync() doesn't skip via pre-check
                    // (the statuses have changed, but our cache is stale).
                    scriptHashStatusCache.clear()
                    val result = sync()

                    if (result is WalletResult.Success) {
                        // After sync, check if new addresses were revealed
                        // (gap limit expansion) and subscribe them.
                        subscribeNewlyRevealedAddresses()
                        // Persist updated statuses for next app launch
                        electrumCache.saveScriptHashStatuses(scriptHashStatusCache.toMap())
                    }
                }
            }
        }
    }

    /**
     * After a sync that may have revealed new addresses (e.g., incoming tx at
     * a gap limit boundary), subscribe any addresses not yet monitored.
     */
    private fun subscribeNewlyRevealedAddresses() {
        val currentWallet = wallet ?: return
        val proxy = cachingProxy ?: return

        val allScriptHashes = getAllRevealedScriptHashes(currentWallet)
        val newHashes = allScriptHashes.filter { it !in subscribedScriptHashes }

        if (newHashes.isEmpty()) return

        if (BuildConfig.DEBUG) Log.d(TAG, "Subscribing ${newHashes.size} newly revealed addresses")
        val newStatuses = proxy.subscribeAdditionalScriptHashes(newHashes)
        subscribedScriptHashes.addAll(newStatuses.keys)
        scriptHashStatusCache.putAll(newStatuses)
    }

    /**
     * Stop the notification collector coroutine.
     */
    private fun stopNotificationCollector() {
        notificationCollectorJob?.cancel()
        notificationCollectorJob = null
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
            walletPersister?.let { currentWallet.persist(it) }
            
            WalletResult.Success(address)
        } catch (e: Exception) {
            WalletResult.Error("Failed to generate address", e)
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
            walletPersister?.let { currentWallet.persist(it) }
            
            WalletResult.Success(address)
        } catch (e: Exception) {
            WalletResult.Error("Failed to generate change address", e)
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
     * Delete a label for an address
     */
    fun deleteAddressLabel(address: String) {
        val activeWalletId = secureStorage.getActiveWalletId() ?: return
        secureStorage.deleteAddressLabel(activeWalletId, address)

        // Update state if this is the current address
        val currentState = _walletState.value
        if (currentState.currentAddress == address) {
            _walletState.value = currentState.copy(
                currentAddressInfo = currentState.currentAddressInfo?.copy(label = null)
            )
        }
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
     * Automatically reclaim revealed-but-unused receive addresses that have no label.
     * Called after sync — BDK has fresh on-chain data so unmarkUsed() correctly
     * identifies addresses that never received funds.
     *
     * Only reclaims addresses with index < current tip (never the most recently
     * revealed address). Labeled addresses are always preserved — the label is the
     * user's signal that the address was generated intentionally.
     *
     * BDK's own safety: unmarkUsed() is a no-op for addresses with actual tx history.
     */
    private fun reclaimUnusedAddresses() {
        val currentWallet = wallet ?: return
        val activeWalletId = secureStorage.getActiveWalletId() ?: return
        
        val labels = secureStorage.getAllAddressLabels(activeWalletId)
        var reclaimed = 0
        
        // Reclaim unused EXTERNAL (receive) addresses — skip labeled ones
        val externalTip = currentWallet.derivationIndex(KeychainKind.EXTERNAL)
        if (externalTip != null && externalTip > 0u) {
            for (i in 0u until externalTip) {
                val addr = currentWallet.peekAddress(KeychainKind.EXTERNAL, i)
                val addrStr = addr.address.toString()
                
                // Skip labeled addresses — user generated them intentionally
                if (labels.containsKey(addrStr)) continue
                
                if (currentWallet.unmarkUsed(KeychainKind.EXTERNAL, i)) {
                    reclaimed++
                }
            }
        }
        
        // Reclaim unused INTERNAL (change) addresses — skip labeled ones
        val internalTip = currentWallet.derivationIndex(KeychainKind.INTERNAL)
        if (internalTip != null && internalTip > 0u) {
            for (i in 0u until internalTip) {
                val addr = currentWallet.peekAddress(KeychainKind.INTERNAL, i)
                val addrStr = addr.address.toString()
                
                if (labels.containsKey(addrStr)) continue
                
                if (currentWallet.unmarkUsed(KeychainKind.INTERNAL, i)) {
                    reclaimed++
                }
            }
        }
        
        if (reclaimed > 0) {
            if (BuildConfig.DEBUG) Log.d(TAG, "Reclaimed $reclaimed unused unlabeled address(es)")
            walletPersister?.let { currentWallet.persist(it) }
        }
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
                addressBalances[addr] = (addressBalances[addr] ?: 0UL) + utxo.txout.value.toSat()
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Error listing UTXOs: ${e.message}")
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
            if (BuildConfig.DEBUG) Log.e(TAG, "Error counting transactions: ${e.message}")
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
            
            // Peek ahead to ensure at least 20 addresses are visible
            val startIndex = (lastRevealedExternal?.plus(1u)) ?: 0u
            val peekCount = maxOf(1, 20 - receiveAddresses.size)
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
            if (BuildConfig.DEBUG) Log.e(TAG, "Error getting receive addresses: ${e.message}")
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
            
            // Peek ahead to ensure at least 20 addresses are visible
            val startIndex = (lastRevealedInternal?.plus(1u)) ?: 0u
            val peekCount = maxOf(1, 20 - changeAddresses.size)
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
            if (BuildConfig.DEBUG) Log.e(TAG, "Error getting change addresses: ${e.message}")
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
                        txid = utxo.outpoint.txid.toString(),
                        vout = utxo.outpoint.vout,
                        address = addr,
                        amountSats = utxo.txout.value.toSat(),
                        label = labels[addr],
                        isConfirmed = isConfirmed,
                        isFrozen = frozenUtxos.contains(outpoint)
                    )
                } catch (e: Exception) {
                    if (BuildConfig.DEBUG) Log.e(TAG, "Error parsing UTXO: ${e.message}")
                    null
                }
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Error listing UTXOs: ${e.message}")
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
        isMaxSend: Boolean = false,
        precomputedFeeSats: ULong? = null,
        onProgress: (String) -> Unit = {}
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
            onProgress("Building transaction...")
            val address = Address(recipientAddress, currentWallet.network())
            val feeRate = feeRateFromSatPerVb(feeRateSatPerVb)
            
            // Helper to configure the TxBuilder with recipients, UTXOs, etc.
            fun buildTx(builder: TxBuilder): TxBuilder {
                var b = builder
                if (isMaxSend) {
                    b = b.drainWallet().drainTo(address.scriptPubkey())
                } else {
                    b = b.addRecipient(address.scriptPubkey(), Amount.fromSat(amountSats))
                }
                if (!selectedUtxos.isNullOrEmpty()) {
                    val walletUtxos = currentWallet.listUnspent()
                    val selectedOutpoints = selectedUtxos.map { it.outpoint }.toSet()
                    for (utxo in walletUtxos) {
                        val outpointStr = "${utxo.outpoint.txid}:${utxo.outpoint.vout}"
                        if (selectedOutpoints.contains(outpointStr)) {
                            b = b.addUtxo(utxo.outpoint)
                        }
                    }
                    b = b.manuallySelectedOnly()
                }
                return b
            }
            
            // When the dry-run already computed the exact fee, reuse it directly
            // via feeAbsolute to guarantee the broadcast fee matches the estimate
            // the user approved. Otherwise fall back to the two-pass correction.
            val psbt = if (precomputedFeeSats != null && !isMaxSend) {
                try {
                    buildTx(TxBuilder().applyConfirmedOnlyFilter().feeAbsolute(Amount.fromSat(precomputedFeeSats))).finish(currentWallet)
                } catch (_: Exception) {
                    // Fallback: two-pass if feeAbsolute with precomputed fee fails
                    // (e.g. UTXO set changed between dry-run and send)
                    val pass1Psbt = buildTx(TxBuilder().applyConfirmedOnlyFilter().feeRate(feeRate)).finish(currentWallet)
                    val exactFeeResult = computeExactFee(pass1Psbt, currentWallet, pass1Psbt.extractTx(), feeRateSatPerVb)
                    if (exactFeeResult != null) {
                        try {
                            buildTx(TxBuilder().applyConfirmedOnlyFilter().feeAbsolute(Amount.fromSat(exactFeeResult.feeSats))).finish(currentWallet)
                        } catch (_: Exception) { pass1Psbt }
                    } else pass1Psbt
                }
            } else {
                val pass1Psbt = buildTx(TxBuilder().applyConfirmedOnlyFilter().feeRate(feeRate)).finish(currentWallet)
                onProgress("Computing fee...")
                val exactFeeResult = computeExactFee(pass1Psbt, currentWallet, pass1Psbt.extractTx(), feeRateSatPerVb)
                if (exactFeeResult != null && !isMaxSend) {
                    try {
                        buildTx(TxBuilder().applyConfirmedOnlyFilter().feeAbsolute(Amount.fromSat(exactFeeResult.feeSats))).finish(currentWallet)
                    } catch (_: Exception) { pass1Psbt }
                } else pass1Psbt
            }
            
            onProgress("Signing transaction...")
            currentWallet.sign(psbt)
            val tx = psbt.extractTx()
            
            onProgress("Broadcasting to network...")
            client.transactionBroadcast(tx)
            
            val txid = tx.computeTxid().toString()
            
            // Save transaction label if provided
            if (!label.isNullOrBlank() && activeWalletId != null) {
                secureStorage.saveTransactionLabel(activeWalletId, txid, label)
            }
            
            // Invalidate pre-check cache so next background sync picks up the change
            clearScriptHashCache()
            
            WalletResult.Success(txid)
        } catch (e: Exception) {
            WalletResult.Error("Transaction failed", e)
        }
    }
    
    /**
      * Dry-run transaction build for accurate fee estimation.
     * Uses BDK's TxBuilder to perform real coin selection and fee calculation
     * without signing or broadcasting. Fast (no network calls).
     *
     * Note: TxBuilder.finish() advances the wallet's internal change address
     * index. This is acceptable because createPsbt also calls finish(), and
     * the change index is only persisted on sync.
     *
     * Returns null if building fails (e.g., insufficient funds, invalid address).
     */
    suspend fun dryRunBuildTx(
        recipientAddress: String,
        amountSats: ULong,
        feeRateSatPerVb: Float = 1.0f,
        selectedUtxos: List<UtxoInfo>? = null,
        isMaxSend: Boolean = false
    ): DryRunResult? = withContext(Dispatchers.IO) {
        val currentWallet = wallet ?: return@withContext null
        
        try {
            val address = Address(recipientAddress, currentWallet.network())
            val feeRate = feeRateFromSatPerVb(feeRateSatPerVb)
            
            fun buildTx(builder: TxBuilder): TxBuilder {
                var b = builder
                if (isMaxSend) {
                    b = b.drainWallet().drainTo(address.scriptPubkey())
                } else {
                    b = b.addRecipient(address.scriptPubkey(), Amount.fromSat(amountSats))
                }
                if (!selectedUtxos.isNullOrEmpty()) {
                    val walletUtxos = currentWallet.listUnspent()
                    val selectedOutpoints = selectedUtxos.map { it.outpoint }.toSet()
                    for (utxo in walletUtxos) {
                        val outpointStr = "${utxo.outpoint.txid}:${utxo.outpoint.vout}"
                        if (selectedOutpoints.contains(outpointStr)) {
                            b = b.addUtxo(utxo.outpoint)
                        }
                    }
                    b = b.manuallySelectedOnly()
                }
                return b
            }
            
            // Pass 1: build with feeRate to get coin selection + structure
            val pass1Psbt = buildTx(TxBuilder().applyConfirmedOnlyFilter().feeRate(feeRate)).finish(currentWallet)
            val unsignedTx = pass1Psbt.extractTx()
            
            // Pass 2: sign pass-1 to measure actual vsize, compute exact fee, rebuild
            // The vBytes measured here is authoritative — we do NOT re-sign pass-2
            // because ECDSA non-determinism can shift the weight by ±1 WU per input.
            val exactFeeResult = computeExactFee(pass1Psbt, currentWallet, unsignedTx, feeRateSatPerVb)
            val measuredVBytes = exactFeeResult?.vsize
            val psbt = if (exactFeeResult != null && !isMaxSend) {
                try {
                    buildTx(TxBuilder().applyConfirmedOnlyFilter().feeAbsolute(Amount.fromSat(exactFeeResult.feeSats))).finish(currentWallet)
                } catch (_: Exception) {
                    pass1Psbt
                }
            } else {
                pass1Psbt
            }
            
            val feeSats = try { psbt.fee().toLong() } catch (_: Exception) { 0L }
            val finalTx = if (psbt !== pass1Psbt) psbt.extractTx() else unsignedTx
            
            val recipientScript = address.scriptPubkey()
            var recipientAmount = 0L
            var changeSats = 0L
            var hasChange = false
            
            for (output in finalTx.output()) {
                if (output.scriptPubkey.toBytes().contentEquals(recipientScript.toBytes())) {
                    recipientAmount = output.value.toSat().toLong()
                } else {
                    changeSats = output.value.toSat().toLong()
                    hasChange = true
                }
            }
            
            val numInputs = finalTx.input().size
            // Use the vBytes from pass-1 signing (which determined the fee)
            val txVBytes = measuredVBytes
                ?: estimateSignedVBytes(pass1Psbt, currentWallet, unsignedTx)
            val effectiveFeeRate = if (txVBytes > 0.0) {
                feeSats.toDouble() / txVBytes
            } else {
                feeRateSatPerVb.toDouble()
            }
            
            DryRunResult(
                feeSats = feeSats,
                changeSats = changeSats,
                hasChange = hasChange,
                numInputs = numInputs,
                txVBytes = txVBytes,
                effectiveFeeRate = effectiveFeeRate,
                recipientAmountSats = recipientAmount
            )
        } catch (e: Exception) {
            val msg = e.message ?: "Transaction build failed"
            val userMessage = when {
                msg.contains("InsufficientFunds", ignoreCase = true) -> "Insufficient funds"
                msg.contains("OutputBelowDustLimit", ignoreCase = true) ||
                    msg.contains("index=0", ignoreCase = true) -> "Amount below dust limit"
                else -> msg.substringAfter("CreateTxException.").substringBefore("(").ifEmpty { msg }
            }
            DryRunResult.error(userMessage)
        }
    }

    /**
     * Dry-run transaction build with multiple recipients.
     */
    suspend fun dryRunBuildTx(
        recipients: List<Recipient>,
        feeRateSatPerVb: Float = 1.0f,
        selectedUtxos: List<UtxoInfo>? = null
    ): DryRunResult? = withContext(Dispatchers.IO) {
        val currentWallet = wallet ?: return@withContext null
        
        try {
            val feeRate = feeRateFromSatPerVb(feeRateSatPerVb)
            
            fun buildTx(builder: TxBuilder): TxBuilder {
                var b = builder
                for (r in recipients) {
                    val addr = Address(r.address, currentWallet.network())
                    b = b.addRecipient(addr.scriptPubkey(), Amount.fromSat(r.amountSats))
                }
                if (!selectedUtxos.isNullOrEmpty()) {
                    val walletUtxos = currentWallet.listUnspent()
                    val selectedOutpoints = selectedUtxos.map { it.outpoint }.toSet()
                    for (utxo in walletUtxos) {
                        val outpointStr = "${utxo.outpoint.txid}:${utxo.outpoint.vout}"
                        if (selectedOutpoints.contains(outpointStr)) {
                            b = b.addUtxo(utxo.outpoint)
                        }
                    }
                    b = b.manuallySelectedOnly()
                }
                return b
            }
            
            // Pass 1: build with feeRate to get coin selection + structure
            val pass1Psbt = buildTx(TxBuilder().applyConfirmedOnlyFilter().feeRate(feeRate)).finish(currentWallet)
            val unsignedTx = pass1Psbt.extractTx()
            
            // Pass 2: sign pass-1 to measure actual vsize, compute exact fee, rebuild
            val exactFeeResult = computeExactFee(pass1Psbt, currentWallet, unsignedTx, feeRateSatPerVb)
            val measuredVBytes = exactFeeResult?.vsize
            val psbt = if (exactFeeResult != null) {
                try {
                    buildTx(TxBuilder().applyConfirmedOnlyFilter().feeAbsolute(Amount.fromSat(exactFeeResult.feeSats))).finish(currentWallet)
                } catch (_: Exception) {
                    pass1Psbt
                }
            } else {
                pass1Psbt
            }
            
            val feeSats = try { psbt.fee().toLong() } catch (_: Exception) { 0L }
            val finalTx = if (psbt !== pass1Psbt) psbt.extractTx() else unsignedTx
            
            val recipientScripts = recipients.map {
                Address(it.address, currentWallet.network()).scriptPubkey().toBytes()
            }.toSet()
            
            var totalRecipientAmount = 0L
            var changeSats = 0L
            var hasChange = false
            
            for (output in finalTx.output()) {
                val scriptBytes = output.scriptPubkey.toBytes()
                if (recipientScripts.any { it.contentEquals(scriptBytes) }) {
                    totalRecipientAmount += output.value.toSat().toLong()
                } else {
                    changeSats = output.value.toSat().toLong()
                    hasChange = true
                }
            }
            
            val numInputs = finalTx.input().size
            val txVBytes = measuredVBytes
                ?: estimateSignedVBytes(pass1Psbt, currentWallet, unsignedTx)
            val effectiveFeeRate = if (txVBytes > 0.0) {
                feeSats.toDouble() / txVBytes
            } else {
                feeRateSatPerVb.toDouble()
            }
            
            DryRunResult(
                feeSats = feeSats,
                changeSats = changeSats,
                hasChange = hasChange,
                numInputs = numInputs,
                txVBytes = txVBytes,
                effectiveFeeRate = effectiveFeeRate,
                recipientAmountSats = totalRecipientAmount
            )
        } catch (e: Exception) {
            val msg = e.message ?: "Transaction build failed"
            val userMessage = when {
                msg.contains("InsufficientFunds", ignoreCase = true) -> "Insufficient funds"
                msg.contains("OutputBelowDustLimit", ignoreCase = true) ||
                    msg.contains("index=0", ignoreCase = true) -> "Amount below dust limit"
                else -> msg.substringAfter("CreateTxException.").substringBefore("(").ifEmpty { msg }
            }
            DryRunResult.error(userMessage)
        }
    }

    /**
     * Send Bitcoin to multiple recipients in a single transaction.
     */
    suspend fun sendBitcoin(
        recipients: List<Recipient>,
        feeRateSatPerVb: Float = 1.0f,
        selectedUtxos: List<UtxoInfo>? = null,
        label: String? = null,
        precomputedFeeSats: ULong? = null,
        onProgress: (String) -> Unit = {}
    ): WalletResult<String> = withContext(Dispatchers.IO) {
        val currentWallet = wallet ?: return@withContext WalletResult.Error("Wallet not initialized")
        val client = electrumClient ?: return@withContext WalletResult.Error("Not connected to Electrum server")
        
        val activeWalletId = secureStorage.getActiveWalletId()
        val storedWallet = activeWalletId?.let { secureStorage.getWalletMetadata(it) }
        if (storedWallet?.isWatchOnly == true) {
            return@withContext WalletResult.Error("Cannot send from watch-only wallet")
        }
        
        try {
            onProgress("Building transaction...")
            val feeRate = feeRateFromSatPerVb(feeRateSatPerVb)
            
            fun buildTx(builder: TxBuilder): TxBuilder {
                var b = builder
                for (r in recipients) {
                    val addr = Address(r.address, currentWallet.network())
                    b = b.addRecipient(addr.scriptPubkey(), Amount.fromSat(r.amountSats))
                }
                if (!selectedUtxos.isNullOrEmpty()) {
                    val walletUtxos = currentWallet.listUnspent()
                    val selectedOutpoints = selectedUtxos.map { it.outpoint }.toSet()
                    for (utxo in walletUtxos) {
                        val outpointStr = "${utxo.outpoint.txid}:${utxo.outpoint.vout}"
                        if (selectedOutpoints.contains(outpointStr)) {
                            b = b.addUtxo(utxo.outpoint)
                        }
                    }
                    b = b.manuallySelectedOnly()
                }
                return b
            }
            
            val psbt = if (precomputedFeeSats != null) {
                try {
                    buildTx(TxBuilder().applyConfirmedOnlyFilter().feeAbsolute(Amount.fromSat(precomputedFeeSats))).finish(currentWallet)
                } catch (_: Exception) {
                    val pass1Psbt = buildTx(TxBuilder().applyConfirmedOnlyFilter().feeRate(feeRate)).finish(currentWallet)
                    val exactFeeResult = computeExactFee(pass1Psbt, currentWallet, pass1Psbt.extractTx(), feeRateSatPerVb)
                    if (exactFeeResult != null) {
                        try {
                            buildTx(TxBuilder().applyConfirmedOnlyFilter().feeAbsolute(Amount.fromSat(exactFeeResult.feeSats))).finish(currentWallet)
                        } catch (_: Exception) { pass1Psbt }
                    } else pass1Psbt
                }
            } else {
                val pass1Psbt = buildTx(TxBuilder().applyConfirmedOnlyFilter().feeRate(feeRate)).finish(currentWallet)
                onProgress("Computing fee...")
                val exactFeeResult = computeExactFee(pass1Psbt, currentWallet, pass1Psbt.extractTx(), feeRateSatPerVb)
                if (exactFeeResult != null) {
                    try {
                        buildTx(TxBuilder().applyConfirmedOnlyFilter().feeAbsolute(Amount.fromSat(exactFeeResult.feeSats))).finish(currentWallet)
                    } catch (_: Exception) { pass1Psbt }
                } else pass1Psbt
            }
            
            onProgress("Signing transaction...")
            currentWallet.sign(psbt)
            val tx = psbt.extractTx()
            
            onProgress("Broadcasting to network...")
            client.transactionBroadcast(tx)
            
            val txid = tx.computeTxid().toString()
            
            if (!label.isNullOrBlank() && activeWalletId != null) {
                secureStorage.saveTransactionLabel(activeWalletId, txid, label)
            }
            
            // Invalidate pre-check cache so next background sync picks up the change
            clearScriptHashCache()
            
            WalletResult.Success(txid)
        } catch (e: Exception) {
            WalletResult.Error("Transaction failed", e)
        }
    }
    
    /**
      * Create an unsigned PSBT with multiple recipients for watch-only wallets.
     */
    suspend fun createPsbt(
        recipients: List<Recipient>,
        feeRateSatPerVb: Float = 1.0f,
        selectedUtxos: List<UtxoInfo>? = null,
        label: String? = null,
        precomputedFeeSats: ULong? = null
    ): WalletResult<PsbtDetails> = withContext(Dispatchers.IO) {
        val currentWallet = wallet ?: return@withContext WalletResult.Error("Wallet not initialized")
        
        try {
            val feeRate = feeRateFromSatPerVb(feeRateSatPerVb)
            
            fun buildTx(builder: TxBuilder): TxBuilder {
                var b = builder
                for (r in recipients) {
                    val addr = Address(r.address, currentWallet.network())
                    b = b.addRecipient(addr.scriptPubkey(), Amount.fromSat(r.amountSats))
                }
                if (!selectedUtxos.isNullOrEmpty()) {
                    val walletUtxos = currentWallet.listUnspent()
                    val selectedOutpoints = selectedUtxos.map { it.outpoint }.toSet()
                    for (utxo in walletUtxos) {
                        val outpointStr = "${utxo.outpoint.txid}:${utxo.outpoint.vout}"
                        if (selectedOutpoints.contains(outpointStr)) {
                            b = b.addUtxo(utxo.outpoint)
                        }
                    }
                    b = b.manuallySelectedOnly()
                }
                return b
            }
            
            val psbt = if (precomputedFeeSats != null) {
                try {
                    buildTx(TxBuilder().applyConfirmedOnlyFilter().feeAbsolute(Amount.fromSat(precomputedFeeSats))).finish(currentWallet)
                } catch (_: Exception) {
                    val pass1Psbt = buildTx(TxBuilder().applyConfirmedOnlyFilter().feeRate(feeRate)).finish(currentWallet)
                    val exactFeeResult = computeExactFee(pass1Psbt, currentWallet, pass1Psbt.extractTx(), feeRateSatPerVb)
                    if (exactFeeResult != null) {
                        try {
                            buildTx(TxBuilder().applyConfirmedOnlyFilter().feeAbsolute(Amount.fromSat(exactFeeResult.feeSats))).finish(currentWallet)
                        } catch (_: Exception) { pass1Psbt }
                    } else pass1Psbt
                }
            } else {
                val pass1Psbt = buildTx(TxBuilder().applyConfirmedOnlyFilter().feeRate(feeRate)).finish(currentWallet)
                val exactFeeResult = computeExactFee(pass1Psbt, currentWallet, pass1Psbt.extractTx(), feeRateSatPerVb)
                if (exactFeeResult != null) {
                    try {
                        buildTx(TxBuilder().applyConfirmedOnlyFilter().feeAbsolute(Amount.fromSat(exactFeeResult.feeSats))).finish(currentWallet)
                    } catch (_: Exception) { pass1Psbt }
                } else pass1Psbt
            }
            
            val psbtBase64 = psbt.serialize()
            val actualFeeSats = try { psbt.fee() } catch (_: Exception) { 0UL }
            
            val tx = psbt.extractTx()
            val recipientScripts = recipients.map {
                Address(it.address, currentWallet.network()).scriptPubkey().toBytes()
            }.toSet()
            
            var totalRecipientAmount = 0UL
            var changeAmount: ULong? = null
            
            for (output in tx.output()) {
                val scriptBytes = output.scriptPubkey.toBytes()
                if (recipientScripts.any { it.contentEquals(scriptBytes) }) {
                    totalRecipientAmount += output.value.toSat()
                } else {
                    changeAmount = output.value.toSat()
                }
            }
            
            val totalInputSats = totalRecipientAmount + (changeAmount ?: 0UL) + actualFeeSats
            
            if (!label.isNullOrBlank()) {
                val activeWalletId = secureStorage.getActiveWalletId()
                if (activeWalletId != null) {
                    secureStorage.savePendingPsbtLabel(activeWalletId, psbtBase64.hashCode().toString(), label)
                }
            }
            
            WalletResult.Success(
                PsbtDetails(
                    psbtBase64 = psbtBase64,
                    feeSats = actualFeeSats,
                    recipientAddress = recipients.joinToString(", ") { it.address.take(12) + "..." },
                    recipientAmountSats = totalRecipientAmount,
                    changeAmountSats = changeAmount,
                    totalInputSats = totalInputSats
                )
            )
        } catch (e: Exception) {
            WalletResult.Error("Failed to create PSBT", e)
        }
    }
    
    /**
     * Create an unsigned PSBT for watch-only wallets.
     * Builds the transaction but does NOT sign or broadcast it.
     * Returns PsbtDetails containing the base64 PSBT and actual fee/amount
     * information computed by BDK (which may differ from client-side estimates).
     */
    suspend fun createPsbt(
        recipientAddress: String,
        amountSats: ULong,
        feeRateSatPerVb: Float = 1.0f,
        selectedUtxos: List<UtxoInfo>? = null,
        label: String? = null,
        isMaxSend: Boolean = false,
        precomputedFeeSats: ULong? = null
    ): WalletResult<PsbtDetails> = withContext(Dispatchers.IO) {
        val currentWallet = wallet ?: return@withContext WalletResult.Error("Wallet not initialized")
        
        try {
            val address = Address(recipientAddress, currentWallet.network())
            val feeRate = feeRateFromSatPerVb(feeRateSatPerVb)
            
            fun buildTx(builder: TxBuilder): TxBuilder {
                var b = builder
                if (isMaxSend) {
                    b = b.drainWallet().drainTo(address.scriptPubkey())
                } else {
                    b = b.addRecipient(address.scriptPubkey(), Amount.fromSat(amountSats))
                }
                if (!selectedUtxos.isNullOrEmpty()) {
                    val walletUtxos = currentWallet.listUnspent()
                    val selectedOutpoints = selectedUtxos.map { it.outpoint }.toSet()
                    for (utxo in walletUtxos) {
                        val outpointStr = "${utxo.outpoint.txid}:${utxo.outpoint.vout}"
                        if (selectedOutpoints.contains(outpointStr)) {
                            b = b.addUtxo(utxo.outpoint)
                        }
                    }
                    b = b.manuallySelectedOnly()
                }
                return b
            }
            
            val psbt = if (precomputedFeeSats != null && !isMaxSend) {
                try {
                    buildTx(TxBuilder().applyConfirmedOnlyFilter().feeAbsolute(Amount.fromSat(precomputedFeeSats))).finish(currentWallet)
                } catch (_: Exception) {
                    val pass1Psbt = buildTx(TxBuilder().applyConfirmedOnlyFilter().feeRate(feeRate)).finish(currentWallet)
                    val exactFeeResult = computeExactFee(pass1Psbt, currentWallet, pass1Psbt.extractTx(), feeRateSatPerVb)
                    if (exactFeeResult != null) {
                        try {
                            buildTx(TxBuilder().applyConfirmedOnlyFilter().feeAbsolute(Amount.fromSat(exactFeeResult.feeSats))).finish(currentWallet)
                        } catch (_: Exception) { pass1Psbt }
                    } else pass1Psbt
                }
            } else {
                val pass1Psbt = buildTx(TxBuilder().applyConfirmedOnlyFilter().feeRate(feeRate)).finish(currentWallet)
                val exactFeeResult = if (!isMaxSend) {
                    computeExactFee(pass1Psbt, currentWallet, pass1Psbt.extractTx(), feeRateSatPerVb)
                } else null
                if (exactFeeResult != null) {
                    try {
                        buildTx(TxBuilder().applyConfirmedOnlyFilter().feeAbsolute(Amount.fromSat(exactFeeResult.feeSats))).finish(currentWallet)
                    } catch (_: Exception) { pass1Psbt }
                } else pass1Psbt
            }
            
            val psbtBase64 = psbt.serialize()
            val actualFeeSats = try { psbt.fee() } catch (_: Exception) { 0UL }
            
            var recipientAmount = amountSats
            var changeAmount: ULong? = null
            
            try {
                val txOutputs = psbt.extractTx().output()
                val recipientScript = address.scriptPubkey()
                
                for (output in txOutputs) {
                    if (output.scriptPubkey.toBytes().contentEquals(recipientScript.toBytes())) {
                        recipientAmount = output.value.toSat()
                    } else {
                        changeAmount = output.value.toSat()
                    }
                }
            } catch (_: Exception) {
                // extractTx may fail on unsigned PSBT in some cases
            }
            
            val totalInputSats = recipientAmount + (changeAmount ?: 0UL) + actualFeeSats
            
            if (!label.isNullOrBlank()) {
                val activeWalletId = secureStorage.getActiveWalletId()
                if (activeWalletId != null) {
                    secureStorage.savePendingPsbtLabel(activeWalletId, psbtBase64.hashCode().toString(), label)
                }
            }
            
            WalletResult.Success(
                PsbtDetails(
                    psbtBase64 = psbtBase64,
                    feeSats = actualFeeSats,
                    recipientAddress = recipientAddress,
                    recipientAmountSats = recipientAmount,
                    changeAmountSats = changeAmount,
                    totalInputSats = totalInputSats
                )
            )
        } catch (e: Exception) {
            WalletResult.Error("Failed to create PSBT", e)
        }
    }
    
    /**
     * Broadcast a signed PSBT received from an external signer (hardware wallet).
     *
     * Hardware wallets like SeedSigner trim the signed PSBT to reduce QR size,
     * stripping witness_utxo/non_witness_utxo fields and keeping only signatures.
     * BDK's finalize() needs UTXO data, so we combine the signed PSBT with the
     * original unsigned PSBT (which has all UTXO data) before finalizing.
     *
     * @param psbtBase64 Base64-encoded signed PSBT (may be trimmed)
     * @param unsignedPsbtBase64 Base64-encoded original unsigned PSBT with UTXO data
     * @param pendingLabel Optional label to apply to the transaction
     * @return Transaction ID
     */
    suspend fun broadcastSignedPsbt(
        psbtBase64: String,
        unsignedPsbtBase64: String? = null,
        pendingLabel: String? = null,
        onProgress: (String) -> Unit = {}
    ): WalletResult<String> = withContext(Dispatchers.IO) {
        val client = electrumClient ?: return@withContext WalletResult.Error("Not connected to Electrum server")
        
        try {
            onProgress("Finalizing PSBT...")
            val signedPsbt = Psbt(psbtBase64)
            
            // Combine with original unsigned PSBT to restore UTXO data that
            // hardware wallets strip to reduce QR code size.
            // BIP 174 combine merges: original provides witness_utxo/non_witness_utxo,
            // signed provides partial_sigs/final_scriptwitness.
            val psbtToFinalize = if (unsignedPsbtBase64 != null) {
                try {
                    val originalPsbt = Psbt(unsignedPsbtBase64)
                    originalPsbt.combine(signedPsbt)
                } catch (e: Exception) {
                    // If combine fails, fall back to using signed PSBT directly
                    signedPsbt
                }
            } else {
                signedPsbt
            }
            
            // Finalize the PSBT — assembles partial signatures into final
            // scriptSig/witness fields. Required before extractTx().
            val finalizeResult = psbtToFinalize.finalize()
            
            if (!finalizeResult.couldFinalize) {
                val errorDetails = finalizeResult.errors
                    ?.joinToString("; ") { it.message ?: "unknown" }
                    ?: "unknown reason"
                return@withContext WalletResult.Error(
                    "PSBT finalization failed: $errorDetails"
                )
            }
            
            val finalizedPsbt = finalizeResult.psbt
            val tx = finalizedPsbt.extractTx()
            
            // Insert any foreign TxOuts into the wallet's tx graph so that
            // calculateFee() works for transactions with external inputs.
            val currentWallet = wallet
            if (currentWallet != null) {
                try {
                    for (input in tx.input()) {
                        if (currentWallet.getUtxo(input.previousOutput) == null) {
                            try {
                                val prevTx = client.fetchTx(input.previousOutput.txid)
                                val prevOutputs = prevTx.output()
                                val vout = input.previousOutput.vout.toInt()
                                if (vout < prevOutputs.size) {
                                    currentWallet.insertTxout(
                                        input.previousOutput,
                                        prevOutputs[vout]
                                    )
                                }
                            } catch (_: Exception) { /* non-fatal */ }
                        }
                    }
                    walletPersister?.let { currentWallet.persist(it) }
                } catch (_: Exception) { /* non-fatal */ }
            }
            
            onProgress("Broadcasting to network...")
            client.transactionBroadcast(tx)
            
            val txid = tx.computeTxid().toString()
            
            // Apply pending label if provided
            if (!pendingLabel.isNullOrBlank()) {
                val activeWalletId = secureStorage.getActiveWalletId()
                if (activeWalletId != null) {
                    secureStorage.saveTransactionLabel(activeWalletId, txid, pendingLabel)
                }
            }
            
            // Invalidate pre-check cache so next background sync picks up the change
            clearScriptHashCache()
            
            WalletResult.Success(txid)
        } catch (e: Exception) {
            val errorMsg = when {
                e.message?.contains("non-final") == true -> "PSBT is not fully signed"
                e.message?.contains("InputException") == true -> "PSBT signing incomplete"
                else -> "Broadcast failed"
            }
            WalletResult.Error(errorMsg, e)
        }
    }
    
    /**
     * Broadcast a raw signed transaction hex received from an external signer.
     * @param txHex Hex-encoded signed transaction
     * @param pendingLabel Optional label to apply to the transaction
     * @return Transaction ID
     */
    suspend fun broadcastRawTx(
        txHex: String,
        pendingLabel: String? = null,
        onProgress: (String) -> Unit = {}
    ): WalletResult<String> = withContext(Dispatchers.IO) {
        val client = electrumClient ?: return@withContext WalletResult.Error("Not connected to Electrum server")
        
        try {
            onProgress("Broadcasting to network...")
            val txBytes = txHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            val tx = Transaction(txBytes)
            client.transactionBroadcast(tx)
            
            val txid = tx.computeTxid().toString()
            
            // Apply pending label if provided
            if (!pendingLabel.isNullOrBlank()) {
                val activeWalletId = secureStorage.getActiveWalletId()
                if (activeWalletId != null) {
                    secureStorage.saveTransactionLabel(activeWalletId, txid, pendingLabel)
                }
            }
            
            // Invalidate pre-check cache so next background sync picks up the change
            clearScriptHashCache()
            
            WalletResult.Success(txid)
        } catch (e: Exception) {
            WalletResult.Error("Broadcast failed", e)
        }
    }
    
    /**
     * Broadcast a manually provided signed transaction (raw hex or signed PSBT base64).
     * Standalone — no wallet-specific side effects (no labels, no insertTxout, no cache clearing).
     * The transaction may not belong to any wallet loaded in the app.
     */
    suspend fun broadcastManualData(
        data: String,
        onProgress: (String) -> Unit = {}
    ): WalletResult<String> = withContext(Dispatchers.IO) {
        val client = electrumClient ?: return@withContext WalletResult.Error("Not connected to Electrum server")
        
        try {
            val trimmed = data.trim()
            val isHex = trimmed.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }
            
            if (isHex && trimmed.length % 2 == 0 && trimmed.length > 20) {
                // Raw transaction hex
                onProgress("Broadcasting raw transaction...")
                val txBytes = trimmed.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
                val tx = Transaction(txBytes)
                client.transactionBroadcast(tx)
                val txid = tx.computeTxid().toString()
                WalletResult.Success(txid)
            } else {
                // Assume signed PSBT base64
                onProgress("Finalizing PSBT...")
                val psbt = Psbt(trimmed)
                val finalizeResult = psbt.finalize()
                
                if (!finalizeResult.couldFinalize) {
                    val errorDetails = finalizeResult.errors
                        ?.joinToString("; ") { it.message ?: "unknown" }
                        ?: "unknown reason"
                    return@withContext WalletResult.Error(
                        "PSBT finalization failed: $errorDetails"
                    )
                }
                
                onProgress("Broadcasting to network...")
                val tx = finalizeResult.psbt.extractTx()
                client.transactionBroadcast(tx)
                val txid = tx.computeTxid().toString()
                WalletResult.Success(txid)
            }
        } catch (e: Exception) {
            val errorMsg = when {
                e.message?.contains("non-final") == true -> "PSBT is not fully signed"
                e.message?.contains("InputException") == true -> "PSBT signing incomplete"
                e.message?.contains("base64") == true -> "Invalid PSBT format"
                else -> "Broadcast failed: ${e.message ?: "unknown error"}"
            }
            WalletResult.Error(errorMsg, e)
        }
    }
    
    /**
     * Result of scanning a WIF key for balances across all address types.
     */
    data class SweepScanResult(
        val addressType: AddressType,
        val address: String,
        val balanceSats: ULong,
        val utxoCount: Int
    )
    
    /**
     * Scan a WIF private key for balances across all relevant address types.
     * Creates ephemeral BDK wallets, syncs each against Electrum, and returns balances.
     */
    suspend fun scanWifBalances(
        wif: String,
        onProgress: (String) -> Unit = {}
    ): WalletResult<List<SweepScanResult>> = withContext(Dispatchers.IO) {
        val client = electrumClient ?: return@withContext WalletResult.Error("Not connected to Electrum server")
        val activeId = secureStorage.getActiveWalletId()
        val storedWallet = activeId?.let { secureStorage.getWalletMetadata(it) }
        val network = (storedWallet?.network ?: WalletNetwork.BITCOIN).toBdkNetwork()
        
        // Determine which address types to scan based on key compression
        val compressed = isWifCompressed(wif)
        val addressTypes = if (compressed) {
            // Compressed keys: Legacy, SegWit, Taproot
            listOf(AddressType.LEGACY, AddressType.SEGWIT, AddressType.TAPROOT)
        } else {
            // Uncompressed keys can only do Legacy
            listOf(AddressType.LEGACY)
        }
        
        val results = mutableListOf<SweepScanResult>()
        
        try {
            for (addrType in addressTypes) {
                onProgress("Scanning ${addrType.displayName}...")
                
                val tempId = "sweep_temp_${UUID.randomUUID()}"
                val tempDbPath = getWalletDbPath(tempId)
                
                try {
                    val descriptor = createDescriptorFromWif(wif, addrType, network)
                    val persister = Persister.newSqlite(tempDbPath)
                    val tempWallet = Wallet.createSingle(
                        descriptor = descriptor,
                        network = network,
                        persister = persister
                    )
                    
                    // Reveal the address so startSyncWithRevealedSpks() includes it
                    val addressInfo = tempWallet.revealNextAddress(KeychainKind.EXTERNAL)
                    val address = addressInfo.address.toString()
                    tempWallet.persist(persister)
                    
                    // Sync this wallet (single-address, quick)
                    val syncRequest = tempWallet.startSyncWithRevealedSpks().build()
                    val update = client.sync(syncRequest, 10UL, false)
                    tempWallet.applyUpdateEvents(update)
                    tempWallet.persist(persister)
                    
                    val balance = tempWallet.balance()
                    val totalSats = amountToSats(balance.total)
                    val utxos = tempWallet.listUnspent()
                    
                    if (totalSats > 0UL) {
                        results.add(SweepScanResult(
                            addressType = addrType,
                            address = address,
                            balanceSats = totalSats,
                            utxoCount = utxos.size
                        ))
                    }
                } finally {
                    // Clean up temp DB files
                    try {
                        File(tempDbPath).delete()
                        File("$tempDbPath-wal").delete()
                        File("$tempDbPath-shm").delete()
                    } catch (_: Exception) {}
                }
            }
            
            WalletResult.Success(results)
        } catch (e: Exception) {
            WalletResult.Error("Scan failed: ${e.message}", e)
        }
    }
    
    /**
     * Sweep all funds from a WIF private key to a destination address.
     * Creates ephemeral BDK wallets for each address type with balance,
     * builds sweep transactions, signs, and broadcasts.
     * Returns a list of broadcast transaction IDs.
     */
    suspend fun sweepPrivateKey(
        wif: String,
        destinationAddress: String,
        feeRateSatPerVb: Float,
        onProgress: (String) -> Unit = {}
    ): WalletResult<List<String>> = withContext(Dispatchers.IO) {
        val client = electrumClient ?: return@withContext WalletResult.Error("Not connected to Electrum server")
        val activeId = secureStorage.getActiveWalletId()
        val storedWallet = activeId?.let { secureStorage.getWalletMetadata(it) }
        val network = (storedWallet?.network ?: WalletNetwork.BITCOIN).toBdkNetwork()
        
        // Validate destination address
        val destAddr = try {
            Address(destinationAddress, network)
        } catch (e: Exception) {
            return@withContext WalletResult.Error("Invalid destination address")
        }
        
        val feeRate = feeRateFromSatPerVb(feeRateSatPerVb)
        val compressed = isWifCompressed(wif)
        val addressTypes = if (compressed) {
            listOf(AddressType.LEGACY, AddressType.SEGWIT, AddressType.TAPROOT)
        } else {
            listOf(AddressType.LEGACY)
        }
        
        val txids = mutableListOf<String>()
        
        try {
            for (addrType in addressTypes) {
                onProgress("Checking ${addrType.displayName}...")
                
                val tempId = "sweep_${UUID.randomUUID()}"
                val tempDbPath = getWalletDbPath(tempId)
                
                try {
                    val descriptor = createDescriptorFromWif(wif, addrType, network)
                    val persister = Persister.newSqlite(tempDbPath)
                    val tempWallet = Wallet.createSingle(
                        descriptor = descriptor,
                        network = network,
                        persister = persister
                    )
                    
                    // Reveal the address so startSyncWithRevealedSpks() includes it
                    tempWallet.revealNextAddress(KeychainKind.EXTERNAL)
                    tempWallet.persist(persister)
                    
                    // Sync
                    val syncRequest = tempWallet.startSyncWithRevealedSpks().build()
                    val update = client.sync(syncRequest, 10UL, false)
                    tempWallet.applyUpdateEvents(update)
                    tempWallet.persist(persister)
                    
                    val balance = tempWallet.balance()
                    val totalSats = amountToSats(balance.total)
                    
                    if (totalSats == 0UL) continue
                    
                    onProgress("Sweeping ${addrType.displayName} ($totalSats sats)...")
                    
                    // Build sweep transaction
                    val psbt = TxBuilder()
                        .drainWallet()
                        .drainTo(destAddr.scriptPubkey())
                        .feeRate(feeRate)
                        .finish(tempWallet)
                    
                    // Sign
                    tempWallet.sign(psbt)
                    val tx = psbt.extractTx()
                    
                    // Broadcast
                    onProgress("Broadcasting ${addrType.displayName}...")
                    client.transactionBroadcast(tx)
                    
                    val txid = tx.computeTxid().toString()
                    txids.add(txid)
                } finally {
                    try {
                        File(tempDbPath).delete()
                        File("$tempDbPath-wal").delete()
                        File("$tempDbPath-shm").delete()
                    } catch (_: Exception) {}
                }
            }
            
            if (txids.isEmpty()) {
                return@withContext WalletResult.Error("No funds found on this private key")
            }
            
            // Invalidate script hash cache
            clearScriptHashCache()
            
            WalletResult.Success(txids)
        } catch (e: Exception) {
            WalletResult.Error("Sweep failed: ${e.message}", e)
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
            val feeRate = feeRateFromSatPerVb(newFeeRate)
            
            // Use BDK's bump fee builder — requires Txid type in BDK 2.x
            val bumpFeeTxBuilder = BumpFeeTxBuilder(Txid.fromString(txid), feeRate)
            val psbt = bumpFeeTxBuilder.finish(currentWallet)
            
            currentWallet.sign(psbt)
            
            val tx = psbt.extractTx()
            client.transactionBroadcast(tx)
            
            val newTxid = tx.computeTxid().toString()
            
            // Mark the original transaction as evicted (replaced by RBF)
            // so BDK removes it from the canonical tx set
            try {
                val evictedAt = System.currentTimeMillis() / 1000
                currentWallet.applyEvictedTxs(listOf(EvictedTx(Txid.fromString(txid), evictedAt.toULong())))
                walletPersister?.let { currentWallet.persist(it) }
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.w(TAG, "Could not evict original tx $txid: ${e.message}")
            }
            
            // Copy label from original transaction if exists
            if (activeWalletId != null) {
                val originalLabel = secureStorage.getTransactionLabel(activeWalletId, txid)
                if (!originalLabel.isNullOrBlank()) {
                    secureStorage.saveTransactionLabel(activeWalletId, newTxid, originalLabel)
                }
            }
            
            // Invalidate pre-check cache so next background sync picks up the change
            clearScriptHashCache()
            
            WalletResult.Success(newTxid)
        } catch (e: Exception) {
            val errorMsg = when {
                e.message?.contains("not found") == true -> "Transaction not found in wallet"
                e.message?.contains("already confirmed") == true -> "Transaction is already confirmed"
                e.message?.contains("rbf") == true || e.message?.contains("RBF") == true -> 
                    "Transaction is not RBF-enabled"
                e.message?.contains("fee") == true -> "New fee rate must be higher than current"
                else -> "Failed to bump fee"
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
            val parentUtxos = utxos.filter { it.outpoint.txid.toString() == parentTxid }
            
            if (parentUtxos.isEmpty()) {
                return@withContext WalletResult.Error("No spendable outputs found from parent transaction")
            }
            
            // Get a change address to send to (we're just consolidating to ourselves)
            val changeAddress = currentWallet.revealNextAddress(KeychainKind.INTERNAL)
            
            // Calculate total value of parent UTXOs
            val totalValue = parentUtxos.sumOf { it.txout.value.toSat().toLong() }.toULong()
            
            // Build transaction spending the parent's UTXOs
            val bdkFeeRate = feeRateFromSatPerVb(feeRate)
            
            // Dust limit - use 546 sats (Bitcoin Core default relay dust, safe for all output types)
            // P2TR dust is ~387 sats, P2WPKH is ~294 sats, so 546 covers all
            val dustLimit = 546UL
            
            // Estimate child tx vsize (~150 vB for 1-in-1-out P2WPKH/P2TR)
            // Add buffer for potential additional inputs
            val estimatedVsize = 150L + (parentUtxos.size - 1) * 68L // ~68 vB per additional input
            val effectiveFeeRateCeil = kotlin.math.ceil(feeRate.toDouble()).toULong()
            val estimatedFee = effectiveFeeRateCeil * estimatedVsize.toULong()
            
            // Check if parent output can cover fee AND leave dust-safe amount
            val canCoverFeeWithDust = totalValue > estimatedFee + dustLimit
            
            if (BuildConfig.DEBUG) Log.d(TAG, "CPFP: parentUtxos=${parentUtxos.size}, totalValue=$totalValue, feeRate=$feeRate, estimatedFee=$estimatedFee, canCoverFeeWithDust=$canCoverFeeWithDust")
            
            var txBuilder = TxBuilder()
                .feeRate(bdkFeeRate)
            
            // Add all UTXOs from parent transaction as REQUIRED inputs
            // This ensures these unconfirmed outputs are spent, pulling the parent tx
            for (utxo in parentUtxos) {
                if (BuildConfig.DEBUG) Log.d(TAG, "CPFP: Adding required UTXO: ${utxo.outpoint.txid}:${utxo.outpoint.vout}, value=${utxo.txout.value.toSat()}")
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
            client.transactionBroadcast(tx)
            
            val childTxid = tx.computeTxid().toString()
            
            // Invalidate pre-check cache so next background sync picks up the change
            clearScriptHashCache()
            
            WalletResult.Success(childTxid)
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "CPFP failed: ${e.message}", e)
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
                else -> "Failed to create CPFP"
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
            val tx = transactions.find { it.transaction.computeTxid().toString() == txid }
                ?: return false
            
            // Check if confirmed
            if (tx.chainPosition is ChainPosition.Confirmed) {
                return false
            }
            
            // Check if any input has RBF signaling (sequence < 0xfffffffe)
            val inputs = tx.transaction.input()
            return inputs.any { it.sequence < 0xfffffffeu }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Error checking RBF eligibility: ${e.message}")
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
            val tx = transactions.find { it.transaction.computeTxid().toString() == txid }
                ?: return false
            
            // Check if confirmed
            if (tx.chainPosition is ChainPosition.Confirmed) {
                return false
            }
            
            // Check if we have unspent outputs from this transaction
            val utxos = currentWallet.listUnspent()
            return utxos.any { it.outpoint.txid.toString() == txid }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Error checking CPFP eligibility: ${e.message}")
            return false
        }
    }
    
    /**
     * Edit wallet metadata (name and optionally fingerprint for watch-only)
     */
    fun editWallet(walletId: String, newName: String, newFingerprint: String? = null) {
        if (secureStorage.editWallet(walletId, newName, newFingerprint)) {
            updateWalletState()
        }
    }
    
    /**
     * Delete a specific wallet
     */
    suspend fun deleteWallet(walletId: String): WalletResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val wasActive = secureStorage.getActiveWalletId() == walletId
            
            // If deleting the duress wallet, clean up duress configuration
            if (secureStorage.getDuressWalletId() == walletId) {
                secureStorage.clearDuressData()
            }
            
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
            WalletResult.Error("Failed to delete wallet", e)
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
        if (BuildConfig.DEBUG) Log.d(TAG, "Disconnecting from Electrum")
        // Stop notification collector first (it references the proxy)
        stopNotificationCollector()
        electrumClient = null
        // Stop caching proxy if running (also stops subscription listener)
        cachingProxy?.stop()
        cachingProxy = null
        // Reset min fee rate to default when disconnected
        _minFeeRate.value = CachingElectrumProxy.DEFAULT_MIN_FEE_RATE
        // Clear cached statuses since we're disconnecting
        clearScriptHashCache()
    }
    
    /**
     * Query the Electrum server's minimum acceptable fee rate via the caching proxy.
     * Uses the proxy's shared upstream socket instead of opening a throwaway connection.
     */
    private fun queryServerMinFeeRate() {
        try {
            val proxy = cachingProxy
            if (proxy != null) {
                val feeRate = proxy.getMinAcceptableFeeRate()
                _minFeeRate.value = feeRate
                if (BuildConfig.DEBUG) Log.d(TAG, "Server relay fee: $feeRate sat/vB (sub-sat: ${feeRate < 1.0})")
            } else {
                if (BuildConfig.DEBUG) Log.w(TAG, "No proxy available for relay fee query")
                _minFeeRate.value = CachingElectrumProxy.DEFAULT_MIN_FEE_RATE
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Failed to query relay fee: ${e.javaClass.simpleName} - ${e.message}", e)
            _minFeeRate.value = CachingElectrumProxy.DEFAULT_MIN_FEE_RATE
        }
    }
    
    /**
     * Query the connected Electrum server's software version string.
     * Returns e.g. "Fulcrum 1.10.0", "electrs/0.10.5", "ElectrumX 1.16.0", or null on failure.
     */
    fun getServerVersion(): String? {
        val client = electrumClient ?: return null
        return try {
            val features = client.serverFeatures()
            features.serverVersion.also {
                if (BuildConfig.DEBUG) Log.d(TAG, "Server version: $it")
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Failed to query server features: ${e.message}")
            null
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
     * Fetch fee estimates from the connected Electrum server using blockchain.estimatefee.
     * Returns BDK's ElectrumClient.estimateFee() results converted from BTC/kB to sat/vB.
     *
     * Uses widely-spaced block targets (2, 6, 12, 144) because Bitcoin Core's estimatesmartfee
     * (which Electrum servers relay) has limited granularity:
     * - Target 1 is usually unsupported (returns -1)
     * - Close targets (1/3/6) often return identical values
     * - Wider spacing gives meaningful differentiation between priority levels
     *
     * When a target returns -1 (can't estimate), falls back to the next lower-priority
     * estimate rather than the relay fee, so results reflect actual mempool conditions.
     */
    suspend fun fetchElectrumFeeEstimates(): Result<FeeEstimates> = withContext(Dispatchers.IO) {
        val client = electrumClient
            ?: return@withContext Result.failure(Exception("Not connected to server"))
        
        try {
            // BDK's estimateFee(n) returns BTC/kB; convert to sat/vB:
            // 1 BTC = 100,000,000 sat, 1 kB = 1000 bytes → BTC/kB * 100,000 = sat/vB
            fun btcPerKbToSatPerVb(btcPerKb: Double): Double = btcPerKb * 100_000.0
            
            // Query multiple targets from low to high priority.
            // Wider spacing gives Bitcoin Core's estimator room to differentiate.
            val targets = listOf(1UL, 2UL, 3UL, 6UL, 12UL, 25UL, 36UL, 144UL)
            val rawResults = targets.map { target ->
                try {
                    client.estimateFee(target)
                } catch (e: Exception) {
                    if (BuildConfig.DEBUG) Log.w(TAG, "estimateFee($target) threw: ${e.message}")
                    -1.0
                }
            }
            
            val estimates = rawResults.map { raw ->
                if (raw < 0.0) null else btcPerKbToSatPerVb(raw)
            }
            // estimates[0]=1, [1]=2, [2]=3, [3]=6, [4]=12, [5]=25, [6]=36, [7]=144
            
            val minRate = _minFeeRate.value
            
            // Pick the best available estimate for each priority bucket.
            // Fall through from tightest target to looser targets when a target returns -1.
            // "fastest" = target 1 or 2 or 3
            // "halfHour" = target 3 or 6
            // "hour" = target 6 or 12
            // "economy" = target 144 or 36 or 25
            val fastest = (estimates[0] ?: estimates[1] ?: estimates[2])
            val halfHour = (estimates[2] ?: estimates[3])
            val hour = (estimates[3] ?: estimates[4])
            val economy = (estimates[7] ?: estimates[6] ?: estimates[5])
            
            // If no target returned a valid estimate, the server doesn't support fee estimation
            if (fastest == null && halfHour == null && hour == null && economy == null) {
                return@withContext Result.failure(
                    Exception("Server returned no fee estimates — it may not support fee estimation")
                )
            }
            
            // Ensure monotonic ordering: fastest >= halfHour >= hour >= economy >= minRate
            val economyFinal = (economy ?: minRate).coerceAtLeast(minRate)
            val hourFinal = (hour ?: economyFinal).coerceAtLeast(economyFinal)
            val halfHourFinal = (halfHour ?: hourFinal).coerceAtLeast(hourFinal)
            val fastestFinal = (fastest ?: halfHourFinal).coerceAtLeast(halfHourFinal)
            
            if (BuildConfig.DEBUG) Log.d(TAG, "Electrum fee estimates (sat/vB): fast=$fastestFinal half=$halfHourFinal hour=$hourFinal econ=$economyFinal")
            
            Result.success(
                FeeEstimates(
                    fastestFee = fastestFinal,
                    halfHourFee = halfHourFinal,
                    hourFee = hourFinal,
                    minimumFee = economyFinal,
                    timestamp = System.currentTimeMillis(),
                    source = FeeEstimateSource.ELECTRUM_SERVER
                )
            )
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Electrum fee estimation failed: ${e.message}")
            Result.failure(e)
        }
    }
    
    /**
     * Get the active Electrum server configuration
     */
    fun getElectrumConfig(): ElectrumConfig? {
        return secureStorage.getActiveElectrumServer()
    }
    
    /**
     * Get all saved Electrum servers, seeding defaults on first launch only.
     * If user deletes all servers, they stay deleted.
     */
    fun getAllElectrumServers(): List<ElectrumConfig> {
        val servers = secureStorage.getAllElectrumServers()
        if (servers.isEmpty() && !secureStorage.hasDefaultServersSeeded()) {
            seedDefaultServers()
            return secureStorage.getAllElectrumServers()
        }
        return servers
    }
    
    /**
     * Seed default Electrum servers for first-time users.
     * Sets the seeded flag so defaults won't be re-added if user deletes them.
     */
    private fun seedDefaultServers() {
        val defaults = listOf(
            ElectrumConfig(
                name = "SethForPrivacy (Tor)",
                url = "iuo6acfdicxhrovyqrekefh4rg2b7vgmzeeohc5cbwegawwhqpdxkgad.onion",
                port = 50002,
                useSsl = true
            ),
            ElectrumConfig(
                name = "Mullvad",
                url = "bitcoin.mullvad.net",
                port = 5010,
                useSsl = true
            ),
            ElectrumConfig(
                name = "Bull Bitcoin",
                url = "fulcrum.bullbitcoin.com",
                port = 50002,
                useSsl = true
            )

        )
        for (config in defaults) {
            secureStorage.saveElectrumServer(config)
        }
        secureStorage.setDefaultServersSeeded(true)
        if (BuildConfig.DEBUG) Log.d(TAG, "Seeded ${defaults.size} default servers")
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
        val wasActive = secureStorage.getActiveServerId() == serverId
        secureStorage.deleteElectrumServer(serverId)
        if (wasActive) {
            disconnect()
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
     * Store an approved certificate fingerprint for a server (TOFU).
     * Called after the user explicitly trusts a new or changed certificate.
     */
    fun acceptServerCertificate(host: String, port: Int, fingerprint: String) {
        secureStorage.saveServerCertFingerprint(host, port, fingerprint)
    }
    
    /**
     * Fetch transaction vsize from Electrum server.
     * Returns ceiled vsize (ceil(weight / 4)) matching Bitcoin Core / mempool.space convention.
     */
    suspend fun fetchTransactionVsizeFromElectrum(txid: String): Double? = withContext(Dispatchers.IO) {
        try {
            val proxy = cachingProxy
            if (proxy == null) {
                if (BuildConfig.DEBUG) Log.w(TAG, "No proxy available for verbose tx query")
                return@withContext null
            }
            
            val details = proxy.getTransactionDetails(txid)
            
            if (details != null && details.weight > 0) {
                val vsize = kotlin.math.ceil(details.weight.toDouble() / 4.0)
                if (BuildConfig.DEBUG) Log.d(TAG, "Got vsize from proxy: $vsize (weight=${details.weight})")
                return@withContext vsize
            }
            
            // Fallback: use vsize if weight not available (legacy servers)
            if (details != null && details.vsize > 0) {
                if (BuildConfig.DEBUG) Log.d(TAG, "Got ceiled vsize from proxy (no weight): ${details.vsize}")
                return@withContext details.vsize.toDouble()
            }
            
            if (BuildConfig.DEBUG) Log.w(TAG, "Proxy returned no vsize for tx $txid")
            null
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Failed to fetch tx vsize: ${e.message}")
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
     * Get persisted privacy mode state
     */
    fun getPrivacyMode(): Boolean {
        return secureStorage.getPrivacyMode()
    }
    
    /**
     * Set privacy mode state
     */
    fun setPrivacyMode(enabled: Boolean) {
        secureStorage.setPrivacyMode(enabled)
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
    
    /**
     * Get the custom fee source server URL
     */
    fun getCustomFeeSourceUrl(): String? {
        return secureStorage.getCustomFeeSourceUrl()
    }
    
    /**
     * Set the custom fee source server URL
     */
    fun setCustomFeeSourceUrl(url: String) {
        secureStorage.setCustomFeeSourceUrl(url)
    }
    
    // ==================== Spend Only Confirmed ====================
    
    /**
     * Get whether spending unconfirmed UTXOs is allowed
     */
    fun getSpendUnconfirmed(): Boolean {
        return secureStorage.getSpendUnconfirmed()
    }
    
    /**
     * Set whether spending unconfirmed UTXOs is allowed
     */
    fun setSpendUnconfirmed(enabled: Boolean) {
        secureStorage.setSpendUnconfirmed(enabled)
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
    val FEE_SOURCE_ELECTRUM = SecureStorage.FEE_SOURCE_ELECTRUM
    val FEE_SOURCE_CUSTOM = SecureStorage.FEE_SOURCE_CUSTOM
    
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
            // Check if this is a watch address wallet (Electrum-only tracking)
            if (activeWalletId != null && secureStorage.hasWatchAddress(activeWalletId)) {
                val watchState = getWatchAddressState(activeWalletId, activeWallet, allWallets)
                _walletState.value = watchState
            } else {
                _walletState.value = WalletState(
                    isInitialized = allWallets.isNotEmpty(),
                    wallets = allWallets,
                    activeWallet = activeWallet
                )
            }
            return
        }
        
        try {
            val balance = currentWallet.balance()
            
            val transactions = currentWallet.transactions().mapNotNull { canonicalTx ->
                try {
                    val tx = canonicalTx.transaction
                    val txidObj = tx.computeTxid()
                    val txid = txidObj.toString()
                    
                    // Use BDK's txDetails() for efficient single-call retrieval of
                    // sent, received, fee, fee_rate, balance_delta, and chain_position
                    val details = currentWallet.txDetails(txidObj)
                    
                    val sent: ULong
                    val received: ULong
                    val fee: ULong?
                    val txWeight: ULong?
                    val netAmount: Long
                    val chainPos: ChainPosition
                    
                    if (details != null) {
                        sent = amountToSats(details.sent)
                        received = amountToSats(details.received)
                        fee = details.fee?.let { amountToSats(it) }
                        txWeight = try { tx.weight() } catch (_: Exception) { null }
                        netAmount = details.balanceDelta
                        chainPos = details.chainPosition
                    } else {
                        // Fallback for edge cases where txDetails returns null
                        val sentAndReceived = currentWallet.sentAndReceived(tx)
                        sent = amountToSats(sentAndReceived.sent)
                        received = amountToSats(sentAndReceived.received)
                        fee = try { amountToSats(currentWallet.calculateFee(tx)) } catch (_: Exception) { null }
                        txWeight = try { tx.weight() } catch (_: Exception) { null }
                        netAmount = received.toLong() - sent.toLong()
                        chainPos = canonicalTx.chainPosition
                    }
                    
                    if (BuildConfig.DEBUG) Log.d(TAG, "TX $txid: sent=$sent, received=$received")
                    
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
                                    addressAmount = output.value.toSat()
                                }
                            } else {
                                // Normal send: find the first output that is NOT ours (the recipient)
                                externalOutputs.firstOrNull()?.let { output ->
                                    address = Address.fromScript(output.scriptPubkey, network).toString()
                                    addressAmount = output.value.toSat()
                                }
                            }
                        } else {
                            // Received: find the first output that IS ours
                            ourOutputs.firstOrNull()?.let { output ->
                                address = Address.fromScript(output.scriptPubkey, network).toString()
                                addressAmount = output.value.toSat()
                            }
                        }
                    } catch (e: Exception) {
                        if (BuildConfig.DEBUG) Log.w(TAG, "Could not extract address from tx $txid: ${e.message}")
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
                                    changeAmount = output.value.toSat()
                                }
                            } else {
                                // For normal sends, the change is the output that belongs to us
                                ourOutputs.firstOrNull()?.let { output ->
                                    changeAddress = Address.fromScript(output.scriptPubkey, network).toString()
                                    changeAmount = output.value.toSat()
                                }
                            }
                        } catch (e: Exception) {
                            if (BuildConfig.DEBUG) Log.w(TAG, "Could not extract change address from tx $txid: ${e.message}")
                        }
                    }
                    
                    val isConfirmed = chainPos is ChainPosition.Confirmed
                    val confirmationInfo = when (chainPos) {
                        is ChainPosition.Confirmed -> ConfirmationTime(
                            height = chainPos.confirmationBlockTime.blockId.height,
                            timestamp = chainPos.confirmationBlockTime.confirmationTime
                        )
                        is ChainPosition.Unconfirmed -> null
                    }
                    
                    // Get timestamp: block time for confirmed, persisted first-seen for pending
                    val txTimestamp = if (isConfirmed) {
                        confirmationInfo?.timestamp?.toLong().also {
                            // Clean up first-seen entry now that the tx is confirmed
                            activeWalletId?.let { wid ->
                                secureStorage.removeTxFirstSeen(wid, txid)
                            }
                        }
                    } else {
                        // Pending: use stored first-seen time, or persist BDK's timestamp now
                        val bdkLastSeen = (chainPos as? ChainPosition.Unconfirmed)
                            ?.timestamp?.toLong()
                        activeWalletId?.let { wid ->
                            val firstSeen = secureStorage.getTxFirstSeen(wid, txid)
                            if (firstSeen != null) {
                                firstSeen
                            } else {
                                val ts = bdkLastSeen ?: (System.currentTimeMillis() / 1000)
                                secureStorage.setTxFirstSeenIfAbsent(wid, txid, ts)
                                ts
                            }
                        } ?: bdkLastSeen
                    }
                    
                    TransactionDetails(
                        txid = txid,
                        amountSats = netAmount,
                        fee = fee,
                        weight = txWeight,
                        confirmationTime = confirmationInfo,
                        isConfirmed = isConfirmed,
                        timestamp = txTimestamp,
                        address = address,
                        addressAmount = addressAmount,
                        changeAddress = changeAddress,
                        changeAmount = changeAmount,
                        isSelfTransfer = isSelfTransfer
                    )
                } catch (e: Exception) {
                    if (BuildConfig.DEBUG) Log.e(TAG, "Error parsing transaction: ${e.message}", e)
                    null
                }
            }.sortedByDescending { it.timestamp ?: Long.MAX_VALUE }

            // Pending incoming: unconfirmed received transactions (positive balance delta)
            val pendingIncomingSats = transactions
                .filter { !it.isConfirmed && it.amountSats > 0L }
                .sumOf { it.amountSats.toULong() }
            // Pending outgoing: unconfirmed sent transactions (negative balance delta, show as positive)
            val pendingOutgoingSats = transactions
                .filter { !it.isConfirmed && it.amountSats < 0L }
                .sumOf { (-it.amountSats).toULong() }
            if (BuildConfig.DEBUG) Log.d(TAG, "Pending: incoming=$pendingIncomingSats, outgoing=$pendingOutgoingSats")

            // Get the next unused address (only reveals new if all revealed addresses are used)
            val lastAddress = try {
                currentWallet.nextUnusedAddress(KeychainKind.EXTERNAL).address.toString()
            } catch (e: Exception) {
                null
            }
            
            val lastSyncTime = activeWalletId?.let { secureStorage.getLastSyncTime(it) }
            
            // Get the latest synced block height from BDK's local chain
            val latestBlockHeight = try {
                currentWallet.latestCheckpoint().height
            } catch (_: Exception) {
                null
            }
            
            _walletState.value = WalletState(
                isInitialized = true,
                wallets = allWallets,
                activeWallet = activeWallet,
                balanceSats = amountToSats(balance.total),
                pendingIncomingSats = pendingIncomingSats,
                pendingOutgoingSats = pendingOutgoingSats,
                transactions = transactions,
                currentAddress = lastAddress,
                lastSyncTimestamp = lastSyncTime,
                blockHeight = latestBlockHeight
            )
        } catch (e: Exception) {
            _walletState.value = _walletState.value.copy(
                wallets = allWallets,
                activeWallet = activeWallet,
                error = "Failed to update wallet state"
            )
        }
    }
    
    /**
     * Incremental wallet state update: only reprocesses transactions affected by
     * the given BDK WalletEvents instead of rebuilding the entire state from scratch.
     *
     * For the common case (1 new tx confirmed, 1 incoming tx) this reduces work from
     * O(all_transactions) to O(affected_transactions), which is significant for
     * wallets with long tx histories.
     *
     * Falls back to full updateWalletState() if incremental update is not feasible.
     */
    private fun updateWalletStateIncremental(events: List<WalletEvent>) {
        val currentWallet = wallet ?: return
        val existingState = _walletState.value
        
        // Must have existing transaction data to merge into
        if (existingState.transactions.isEmpty() && existingState.balanceSats == 0UL) {
            updateWalletState()
            return
        }
        
        try {
            // Balance is always fast (BDK in-memory) — get the authoritative value
            val balance = currentWallet.balance()
            val activeWalletId = secureStorage.getActiveWalletId()
            
            // Collect txids affected by events
            val affectedTxids = mutableSetOf<String>()
            val droppedTxids = mutableSetOf<String>()
            for (event in events) {
                when (event) {
                    is WalletEvent.TxConfirmed -> affectedTxids.add(event.txid.toString())
                    is WalletEvent.TxUnconfirmed -> affectedTxids.add(event.txid.toString())
                    is WalletEvent.TxReplaced -> {
                        // conflicts contains the old txids that were replaced
                        for (conflict in event.conflicts) {
                            droppedTxids.add(conflict.txid.toString())
                        }
                        affectedTxids.add(event.txid.toString())
                    }
                    is WalletEvent.TxDropped -> droppedTxids.add(event.txid.toString())
                    is WalletEvent.ChainTipChanged -> {
                        // Chain tip change without tx events — just update balance + height
                    }
                }
            }
            
            // If too many txids are affected, fall back to full rebuild
            // (rare case of large reorg or initial load)
            if (affectedTxids.size > 20) {
                updateWalletState()
                return
            }
            
            val network = currentWallet.network()
            
            // Build TransactionDetails for each affected txid
            val updatedTxDetails = mutableMapOf<String, TransactionDetails>()
            for (canonicalTx in currentWallet.transactions()) {
                val tx = canonicalTx.transaction
                val txid = tx.computeTxid().toString()
                if (txid !in affectedTxids) continue
                
                try {
                    val txidObj = tx.computeTxid()
                    val details = currentWallet.txDetails(txidObj)
                    
                    val sent: ULong
                    val received: ULong
                    val fee: ULong?
                    val txWeight: ULong?
                    val netAmount: Long
                    val chainPos: ChainPosition
                    
                    if (details != null) {
                        sent = amountToSats(details.sent)
                        received = amountToSats(details.received)
                        fee = details.fee?.let { amountToSats(it) }
                        txWeight = try { tx.weight() } catch (_: Exception) { null }
                        netAmount = details.balanceDelta
                        chainPos = details.chainPosition
                    } else {
                        val sentAndReceived = currentWallet.sentAndReceived(tx)
                        sent = amountToSats(sentAndReceived.sent)
                        received = amountToSats(sentAndReceived.received)
                        fee = try { amountToSats(currentWallet.calculateFee(tx)) } catch (_: Exception) { null }
                        txWeight = try { tx.weight() } catch (_: Exception) { null }
                        netAmount = received.toLong() - sent.toLong()
                        chainPos = canonicalTx.chainPosition
                    }
                    
                    val isSentTx = netAmount < 0
                    val outputs = tx.output()
                    val ourOutputs = outputs.filter { currentWallet.isMine(it.scriptPubkey) }
                    val externalOutputs = outputs.filter { !currentWallet.isMine(it.scriptPubkey) }
                    val isSelfTransfer = isSentTx && externalOutputs.isEmpty() && ourOutputs.isNotEmpty()
                    
                    var address: String? = null
                    var addressAmount: ULong? = null
                    try {
                        if (isSentTx) {
                            if (isSelfTransfer) {
                                ourOutputs.firstOrNull()?.let { output ->
                                    address = Address.fromScript(output.scriptPubkey, network).toString()
                                    addressAmount = output.value.toSat()
                                }
                            } else {
                                externalOutputs.firstOrNull()?.let { output ->
                                    address = Address.fromScript(output.scriptPubkey, network).toString()
                                    addressAmount = output.value.toSat()
                                }
                            }
                        } else {
                            ourOutputs.firstOrNull()?.let { output ->
                                address = Address.fromScript(output.scriptPubkey, network).toString()
                                addressAmount = output.value.toSat()
                            }
                        }
                    } catch (_: Exception) {}
                    
                    var changeAddress: String? = null
                    var changeAmount: ULong? = null
                    if (isSentTx && ourOutputs.isNotEmpty()) {
                        try {
                            if (isSelfTransfer) {
                                ourOutputs.find { output ->
                                    Address.fromScript(output.scriptPubkey, network).toString() != address
                                }?.let { output ->
                                    changeAddress = Address.fromScript(output.scriptPubkey, network).toString()
                                    changeAmount = output.value.toSat()
                                }
                            } else {
                                ourOutputs.firstOrNull()?.let { output ->
                                    changeAddress = Address.fromScript(output.scriptPubkey, network).toString()
                                    changeAmount = output.value.toSat()
                                }
                            }
                        } catch (_: Exception) {}
                    }
                    
                    val isConfirmed = chainPos is ChainPosition.Confirmed
                    val confirmationInfo = when (chainPos) {
                        is ChainPosition.Confirmed -> ConfirmationTime(
                            height = chainPos.confirmationBlockTime.blockId.height,
                            timestamp = chainPos.confirmationBlockTime.confirmationTime
                        )
                        is ChainPosition.Unconfirmed -> null
                    }
                    
                    val txTimestamp = if (isConfirmed) {
                        confirmationInfo?.timestamp?.toLong().also {
                            activeWalletId?.let { wid -> secureStorage.removeTxFirstSeen(wid, txid) }
                        }
                    } else {
                        val bdkLastSeen = (chainPos as? ChainPosition.Unconfirmed)?.timestamp?.toLong()
                        activeWalletId?.let { wid ->
                            val firstSeen = secureStorage.getTxFirstSeen(wid, txid)
                            if (firstSeen != null) firstSeen
                            else {
                                val ts = bdkLastSeen ?: (System.currentTimeMillis() / 1000)
                                secureStorage.setTxFirstSeenIfAbsent(wid, txid, ts)
                                ts
                            }
                        } ?: bdkLastSeen
                    }
                    
                    updatedTxDetails[txid] = TransactionDetails(
                        txid = txid,
                        amountSats = netAmount,
                        fee = fee,
                        weight = txWeight,
                        confirmationTime = confirmationInfo,
                        isConfirmed = isConfirmed,
                        timestamp = txTimestamp,
                        address = address,
                        addressAmount = addressAmount,
                        changeAddress = changeAddress,
                        changeAmount = changeAmount,
                        isSelfTransfer = isSelfTransfer
                    )
                } catch (e: Exception) {
                    if (BuildConfig.DEBUG) Log.w(TAG, "Incremental: failed to process tx $txid: ${e.message}")
                }
            }
            
            // Merge: start with existing list, remove dropped, replace/add affected
            val mergedTransactions = existingState.transactions
                .filter { it.txid !in droppedTxids }
                .map { existing ->
                    updatedTxDetails.remove(existing.txid) ?: existing
                }
                .toMutableList()
            // Add any new transactions not in the existing list
            mergedTransactions.addAll(updatedTxDetails.values)
            val sortedTransactions = mergedTransactions.sortedByDescending { it.timestamp ?: Long.MAX_VALUE }
            
            // Recalculate pending sats from merged list
            val pendingIncomingSats = sortedTransactions
                .filter { !it.isConfirmed && it.amountSats > 0L }
                .sumOf { it.amountSats.toULong() }
            val pendingOutgoingSats = sortedTransactions
                .filter { !it.isConfirmed && it.amountSats < 0L }
                .sumOf { (-it.amountSats).toULong() }
            
            val lastAddress = try {
                currentWallet.nextUnusedAddress(KeychainKind.EXTERNAL).address.toString()
            } catch (_: Exception) { existingState.currentAddress }
            
            val lastSyncTime = activeWalletId?.let { secureStorage.getLastSyncTime(it) }
            val latestBlockHeight = try {
                currentWallet.latestCheckpoint().height
            } catch (_: Exception) { existingState.blockHeight }
            
            _walletState.value = existingState.copy(
                balanceSats = amountToSats(balance.total),
                pendingIncomingSats = pendingIncomingSats,
                pendingOutgoingSats = pendingOutgoingSats,
                transactions = sortedTransactions,
                currentAddress = lastAddress,
                lastSyncTimestamp = lastSyncTime,
                blockHeight = latestBlockHeight
            )
            
            if (BuildConfig.DEBUG) Log.d(TAG, "Incremental state update: ${affectedTxids.size} affected, ${droppedTxids.size} dropped")
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.w(TAG, "Incremental update failed, falling back to full: ${e.message}")
            updateWalletState()
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
     * Get whether screenshots are disabled
     */
    fun getDisableScreenshots(): Boolean {
        return secureStorage.getDisableScreenshots()
    }
    
    /**
     * Set whether screenshots are disabled
     */
    fun setDisableScreenshots(disabled: Boolean) {
        secureStorage.setDisableScreenshots(disabled)
    }
    
    // ==================== Duress PIN / Decoy Wallet ====================
    
    /**
     * Create a duress (decoy) wallet from a mnemonic.
     * Imports it as a standard SegWit wallet with a generic name.
     * Saves the wallet ID as the duress wallet and records the current active wallet as the real wallet.
     * Switches back to the real wallet after creation.
     */
    suspend fun createDuressWallet(
        mnemonic: String,
        passphrase: String? = null,
        customDerivationPath: String? = null,
        addressType: AddressType = AddressType.SEGWIT
    ): WalletResult<String> = withContext(Dispatchers.IO) {
        try {
            // A real wallet must exist before setting up duress — otherwise the
            // decoy wallet becomes the only (and visible) wallet in the main app.
            val currentActiveId = secureStorage.getActiveWalletId()
                ?: return@withContext WalletResult.Error("Import a wallet before setting up duress")
            
            // Record the current active wallet as the real wallet
            secureStorage.setRealWalletId(currentActiveId)
            
            val config = WalletImportConfig(
                name = "Wallet",
                keyMaterial = mnemonic,
                addressType = addressType,
                passphrase = passphrase,
                customDerivationPath = customDerivationPath,
                network = WalletNetwork.BITCOIN
            )
            
            // Import the wallet (this sets it as active)
            when (val result = importWallet(config)) {
                is WalletResult.Error -> return@withContext WalletResult.Error(result.message)
                is WalletResult.Success -> { /* continue */ }
            }
            
            // The new wallet is now active — get its ID
            val duressWalletId = secureStorage.getActiveWalletId()
                ?: return@withContext WalletResult.Error("Failed to get duress wallet ID")
            
            secureStorage.setDuressWalletId(duressWalletId)
            
            // Switch back to the real wallet
            switchWallet(currentActiveId)
            
            WalletResult.Success(duressWalletId)
        } catch (e: Exception) {
            WalletResult.Error("Failed to create duress wallet", e)
        }
    }
    
    /**
     * Delete the duress wallet and clear all duress-related data
     */
    suspend fun deleteDuressWallet(): WalletResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val duressWalletId = secureStorage.getDuressWalletId()
            if (duressWalletId != null) {
                // Delete the wallet data and BDK database
                secureStorage.deleteWallet(duressWalletId)
                deleteWalletDatabase(duressWalletId)
            }
            secureStorage.clearDuressData()
            updateWalletState()
            WalletResult.Success(Unit)
        } catch (e: Exception) {
            WalletResult.Error("Failed to delete duress wallet", e)
        }
    }
    
    /**
     * Switch to the duress (decoy) wallet.
     * Saves the current active wallet as real_wallet_id if not already set.
     */
    suspend fun switchToDuressWallet(): WalletResult<Unit> = withContext(Dispatchers.IO) {
        val duressWalletId = secureStorage.getDuressWalletId()
            ?: return@withContext WalletResult.Error("No duress wallet configured")
        
        // Preserve the real wallet ID
        val currentActiveId = secureStorage.getActiveWalletId()
        if (currentActiveId != null && currentActiveId != duressWalletId) {
            secureStorage.setRealWalletId(currentActiveId)
        }
        
        switchWallet(duressWalletId)
    }
    
    /**
     * Switch back to the real wallet from duress mode
     */
    suspend fun switchToRealWallet(): WalletResult<Unit> = withContext(Dispatchers.IO) {
        val realWalletId = secureStorage.getRealWalletId()
            ?: return@withContext WalletResult.Error("No real wallet ID saved")
        
        switchWallet(realWalletId)
    }
    
    /**
     * Check if a given wallet ID is the duress wallet
     */
    fun isDuressWallet(walletId: String): Boolean {
        return secureStorage.getDuressWalletId() == walletId
    }
    
    /**
     * Check if duress mode is enabled
     */
    fun isDuressEnabled(): Boolean {
        return secureStorage.isDuressEnabled()
    }
    
    /**
     * Get the duress wallet ID
     */
    fun getDuressWalletId(): String? {
        return secureStorage.getDuressWalletId()
    }
    
    /**
     * Save the duress PIN
     */
    fun saveDuressPin(pin: String) {
        secureStorage.saveDuressPin(pin)
    }
    
    /**
     * Set whether duress mode is enabled
     */
    fun setDuressEnabled(enabled: Boolean) {
        secureStorage.setDuressEnabled(enabled)
    }
    
    // ==================== Auto-Wipe ====================
    
    /**
     * Get the auto-wipe threshold setting
     */
    fun getAutoWipeThreshold(): SecureStorage.AutoWipeThreshold {
        return secureStorage.getAutoWipeThreshold()
    }
    
    /**
     * Set the auto-wipe threshold
     */
    fun setAutoWipeThreshold(threshold: SecureStorage.AutoWipeThreshold) {
        secureStorage.setAutoWipeThreshold(threshold)
    }
    
    // ==================== Cloak Mode ====================
    
    fun isCloakModeEnabled(): Boolean {
        return secureStorage.isCloakModeEnabled()
    }
    
    fun setCloakModeEnabled(enabled: Boolean) {
        secureStorage.setCloakModeEnabled(enabled)
    }
    
    fun setCloakCode(code: String) {
        secureStorage.setCloakCode(code)
    }
    
    fun getCloakCode(): String? {
        return secureStorage.getCloakCode()
    }
    
    fun clearCloakData() {
        secureStorage.clearCloakData()
    }
    
    fun setPendingIconAlias(alias: String) {
        secureStorage.setPendingIconAlias(alias)
    }
    
    /**
     * Wipe all wallet data: delete every wallet's BDK database, clear all
     * SharedPreferences (secure + regular), reset in-memory state, and
     * clear the Electrum cache.
     */
    suspend fun wipeAllData() = withContext(Dispatchers.IO) {
        try {
            // Disconnect from server
            disconnect()
            
            // Delete all BDK database files
            val walletIds = secureStorage.getWalletIds()
            for (id in walletIds) {
                deleteWalletDatabase(id)
            }
            // Recursively nuke the entire bdk directory (including subdirectories)
            bdkDbDir.deleteRecursively()
            bdkDbDir.mkdirs() // Recreate empty dir for potential future use
            
            // Clear Electrum cache
            electrumCache.clearAll()
            
            // Wipe all preferences (secure + regular)
            secureStorage.wipeAllData()
            
            // Reset in-memory state
            wallet = null
            walletPersister = null
            _walletState.value = WalletState()
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Error during wipe: ${e.message}")
        }
    }
    
    /**
     * Convert Amount to satoshis
     * BDK 1.0 uses Amount.toSat() method
     */
    private fun amountToSats(amount: Amount): ULong {
        return try {
            amount.toSat()
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.w(TAG, "Error converting Amount to sats: ${e.message}")
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
