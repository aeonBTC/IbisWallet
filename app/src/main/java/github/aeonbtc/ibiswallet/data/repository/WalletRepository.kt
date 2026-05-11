package github.aeonbtc.ibiswallet.data.repository

import android.content.Context
import android.util.Base64
import android.util.Log
import github.aeonbtc.ibiswallet.BuildConfig
import github.aeonbtc.ibiswallet.R
import github.aeonbtc.ibiswallet.data.local.ElectrumCache
import github.aeonbtc.ibiswallet.data.local.SecureStorage
import github.aeonbtc.ibiswallet.data.model.AddressType
import github.aeonbtc.ibiswallet.data.model.ConfirmationTime
import github.aeonbtc.ibiswallet.data.model.DryRunResult
import github.aeonbtc.ibiswallet.data.model.ElectrumConfig
import github.aeonbtc.ibiswallet.data.model.FeeEstimateSource
import github.aeonbtc.ibiswallet.data.model.FeeEstimates
import github.aeonbtc.ibiswallet.data.model.KeychainType
import github.aeonbtc.ibiswallet.data.model.LiquidSwapDetails
import github.aeonbtc.ibiswallet.data.model.LiquidSwapTxRole
import github.aeonbtc.ibiswallet.data.model.LiquidTxSource
import github.aeonbtc.ibiswallet.data.model.Layer2Provider
import github.aeonbtc.ibiswallet.data.model.MultisigWalletConfig
import github.aeonbtc.ibiswallet.data.model.PsbtSessionStatus
import github.aeonbtc.ibiswallet.data.model.PsbtSigningSession
import github.aeonbtc.ibiswallet.data.model.PsbtDetails
import github.aeonbtc.ibiswallet.data.model.ReceiveAddressInfo
import github.aeonbtc.ibiswallet.data.model.Recipient
import github.aeonbtc.ibiswallet.data.model.SeedFormat
import github.aeonbtc.ibiswallet.data.model.StoredWallet
import github.aeonbtc.ibiswallet.data.model.SwapDirection
import github.aeonbtc.ibiswallet.data.model.SwapService
import github.aeonbtc.ibiswallet.data.model.SyncProgress
import github.aeonbtc.ibiswallet.data.model.TransactionDetails
import github.aeonbtc.ibiswallet.data.model.TransactionSearchDocument
import github.aeonbtc.ibiswallet.data.model.TransactionSearchLayer
import github.aeonbtc.ibiswallet.data.model.TransactionSearchFilters
import github.aeonbtc.ibiswallet.data.model.TransactionSearchResult
import github.aeonbtc.ibiswallet.data.model.UtxoInfo
import github.aeonbtc.ibiswallet.data.model.WalletAddress
import github.aeonbtc.ibiswallet.data.model.WalletImportConfig
import github.aeonbtc.ibiswallet.data.model.WalletNetwork
import github.aeonbtc.ibiswallet.data.model.WalletPolicyType
import github.aeonbtc.ibiswallet.data.model.WalletResult
import github.aeonbtc.ibiswallet.data.model.WalletState
import github.aeonbtc.ibiswallet.localization.AppLocale
import github.aeonbtc.ibiswallet.util.BitcoinSendPreparationCacheKey
import github.aeonbtc.ibiswallet.util.BitcoinSendPreparationState
import github.aeonbtc.ibiswallet.util.BitcoinUtils
import github.aeonbtc.ibiswallet.util.Bip137MessageSigner
import github.aeonbtc.ibiswallet.util.ElectrumSeedUtil
import github.aeonbtc.ibiswallet.util.MultisigWalletParser
import github.aeonbtc.ibiswallet.util.PsbtExportOptimizer
import github.aeonbtc.ibiswallet.util.SecureLog
import github.aeonbtc.ibiswallet.util.SilentPayment
import github.aeonbtc.ibiswallet.util.buildMultiBitcoinSendPreparationKey
import github.aeonbtc.ibiswallet.util.buildSingleBitcoinSendPreparationKey
import github.aeonbtc.ibiswallet.util.buildBitcoinTransactionSearchDocument
import github.aeonbtc.ibiswallet.util.findMaxExactSendAmount
import github.aeonbtc.ibiswallet.util.isTransactionInsufficientFundsError
import github.aeonbtc.ibiswallet.util.TofuTrustManager
import github.aeonbtc.ibiswallet.tor.CachingElectrumProxy
import github.aeonbtc.ibiswallet.tor.ElectrumNotification
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.bitcoindevkit.Address
import org.bitcoindevkit.Amount
import org.bitcoindevkit.BumpFeeTxBuilder
import org.bitcoindevkit.ChainPosition
import org.bitcoindevkit.Descriptor
import org.bitcoindevkit.DescriptorSecretKey
import org.bitcoindevkit.ElectrumClient
import org.bitcoindevkit.EvictedTx
import org.bitcoindevkit.FeeRate
import org.bitcoindevkit.FullScanScriptInspector
import org.bitcoindevkit.KeychainKind
import org.bitcoindevkit.Mnemonic
import org.bitcoindevkit.Network
import org.bitcoindevkit.Persister
import org.bitcoindevkit.Psbt
import org.bitcoindevkit.Script
import org.bitcoindevkit.SyncScriptInspector
import org.bitcoindevkit.Transaction
import org.bitcoindevkit.TxBuilder
import org.bitcoindevkit.Txid
import org.bitcoindevkit.Wallet
import org.bitcoindevkit.WalletEvent
import java.io.File
import java.security.MessageDigest
import java.util.UUID

/**
 * Repository for managing Bitcoin wallet operations using BDK
 * Supports multiple wallets
 */
class WalletRepository(context: Context) {
    companion object {
        private const val TAG = "WalletRepository"
        private const val BDK_DB_DIR = "bdk"
        private const val LWK_DB_DIR = "lwk"
        private const val SPARK_DB_DIR = "spark"
        private const val SWEEP_TEMP_DB_DIR = "bdk_sweep_tmp"
        private const val QUICK_SYNC_TIMEOUT_MS = 60_000L
        private const val SYNC_BATCH_SIZE = 500UL
        private const val FULL_SCAN_BATCH_SIZE_TOR = 50UL
        private const val FULL_SCAN_BATCH_SIZE_CLEARNET = 500UL
        private const val MIN_BATCH_SIZE = 25UL
        private const val MESSAGE_SIGNING_MIN_SCAN_LIMIT = 250
        private const val MESSAGE_SIGNING_LOOKAHEAD = 25
        private const val SCRIPT_HASH_SAMPLE_SIZE = 5
        private const val NOTIFICATION_DEBOUNCE_MS = 1000L
        private const val ADDRESS_PEEK_AHEAD = 100
        private const val INCREMENTAL_RECONCILE_INTERVAL = 10
        private const val INCREMENTAL_RECONCILE_MAX_AGE_MS = 30 * 60_000L
        private const val TRANSACTION_HISTORY_INITIAL_CHUNK_SIZE = 25
        private const val TRANSACTION_HISTORY_CHUNK_SIZE = 100

        /** Pattern to extract supported xpub/zpub keys from a descriptor string. */
        private val XPUB_PATTERN = """]([xzXZ]pub[a-zA-Z0-9]+)""".toRegex()
    }

    private data class CachedWalletTransaction(
        val transaction: Transaction,
        val chainPosition: ChainPosition,
    )

    private data class WalletStateChecksum(
        val balanceSats: ULong,
        val pendingIncomingSats: ULong,
        val pendingOutgoingSats: ULong,
        val txCount: Int,
    )

    private fun WalletState.hasPendingBitcoinTransactions(): Boolean =
        pendingIncomingSats > 0UL ||
            pendingOutgoingSats > 0UL ||
            transactions.any { !it.isConfirmed }

    private data class TransactionBuildCandidate(
        val txid: String,
        val cachedTransaction: CachedWalletTransaction,
        val cachedDetails: TransactionDetails?,
        val sortTimestamp: Long,
        val sortHeight: Long,
    )

    private data class PreparedBitcoinSendCacheEntry(
        val key: BitcoinSendPreparationCacheKey,
        val dryRunResult: DryRunResult,
        val psbtDetails: PsbtDetails?,
    )

    private fun buildBitcoinTransactionSearchDocuments(
        walletId: String,
        transactions: Collection<TransactionDetails>,
    ): List<TransactionSearchDocument> {
        val transactionLabels = secureStorage.getAllTransactionLabels(walletId)
        val addressLabels = secureStorage.getAllAddressLabels(walletId)
        return transactions.map { transaction ->
            buildBitcoinTransactionSearchDocument(
                walletId = walletId,
                transaction = transaction,
                transactionLabel = transactionLabels[transaction.txid],
                addressLabel = transaction.address?.let(addressLabels::get),
            )
        }
    }

    private fun scheduleBitcoinTransactionSearchIndexReplace(
        walletId: String?,
        transactions: Collection<TransactionDetails>,
    ) {
        val resolvedWalletId = walletId ?: return
        val transactionSnapshot = transactions.toList()
        bitcoinSearchIndexJob?.cancel()
        bitcoinSearchIndexJob =
            repositoryScope.launch {
                electrumCache.replaceTransactionSearchDocuments(
                    walletId = resolvedWalletId,
                    layer = TransactionSearchLayer.BITCOIN,
                    documents = buildBitcoinTransactionSearchDocuments(resolvedWalletId, transactionSnapshot),
                )
            }
    }

    private fun scheduleBitcoinTransactionSearchIndexUpsert(
        walletId: String?,
        transactions: Collection<TransactionDetails>,
        deletedTxids: Collection<String> = emptySet(),
    ) {
        val resolvedWalletId = walletId ?: return
        val transactionSnapshot = transactions.toList()
        val deletedSnapshot = deletedTxids.filter { it.isNotBlank() }.toSet()
        bitcoinSearchIndexJob?.cancel()
        bitcoinSearchIndexJob =
            repositoryScope.launch {
                if (deletedSnapshot.isNotEmpty()) {
                    electrumCache.deleteTransactionSearchDocuments(
                        walletId = resolvedWalletId,
                        layer = TransactionSearchLayer.BITCOIN,
                        txids = deletedSnapshot,
                    )
                }
                if (transactionSnapshot.isNotEmpty()) {
                    electrumCache.upsertTransactionSearchDocuments(
                        buildBitcoinTransactionSearchDocuments(resolvedWalletId, transactionSnapshot),
                    )
                }
            }
    }

    private fun saveBitcoinTransactionLabelIndexed(
        walletId: String,
        txid: String,
        label: String,
    ) {
        secureStorage.saveTransactionLabel(walletId, txid, label)
        electrumCache.updateTransactionSearchLabel(
            walletId = walletId,
            layer = TransactionSearchLayer.BITCOIN,
            txid = txid,
            label = label,
        )
    }

    private fun saveBitcoinTransactionLabelsIndexed(
        walletId: String,
        labels: Map<String, String>,
    ) {
        val cleanLabels = labels.filterValues { it.isNotBlank() }
        if (cleanLabels.isEmpty()) return
        secureStorage.saveTransactionLabels(walletId, cleanLabels)
        electrumCache.updateTransactionSearchLabels(
            walletId = walletId,
            layer = TransactionSearchLayer.BITCOIN,
            labels = cleanLabels,
        )
    }

    private fun saveLiquidTransactionLabelIndexed(
        walletId: String,
        txid: String,
        label: String,
    ) {
        secureStorage.saveLiquidTransactionLabel(walletId, txid, label)
        electrumCache.updateTransactionSearchLabel(
            walletId = walletId,
            layer = TransactionSearchLayer.LIQUID,
            txid = txid,
            label = label,
        )
    }

    private fun saveLiquidTransactionLabelsIndexed(
        walletId: String,
        labels: Map<String, String>,
    ) {
        val cleanLabels = labels.filterValues { it.isNotBlank() }
        if (cleanLabels.isEmpty()) return
        secureStorage.saveLiquidTransactionLabels(walletId, cleanLabels)
        electrumCache.updateTransactionSearchLabels(
            walletId = walletId,
            layer = TransactionSearchLayer.LIQUID,
            labels = cleanLabels,
        )
    }

    private fun updateBitcoinAddressLabelIndex(
        walletId: String,
        address: String,
        label: String,
    ) {
        electrumCache.updateTransactionSearchAddressLabel(
            walletId = walletId,
            layer = TransactionSearchLayer.BITCOIN,
            address = address,
            label = label,
        )
    }

    private fun saveBitcoinAddressLabelsIndexed(
        walletId: String,
        labels: Map<String, String>,
    ) {
        val cleanLabels = labels.filterValues { it.isNotBlank() }
        if (cleanLabels.isEmpty()) return
        secureStorage.saveAddressLabels(walletId, cleanLabels)
        electrumCache.updateTransactionSearchAddressLabels(
            walletId = walletId,
            layer = TransactionSearchLayer.BITCOIN,
            labels = cleanLabels,
        )
    }

    private val appContext = context.applicationContext
    private val secureStorage = SecureStorage.getInstance(context)

    private fun localizedString(id: Int): String =
        AppLocale.createLocalizedContext(appContext, secureStorage.getAppLocale()).getString(id)
    private val electrumCache = ElectrumCache(context)

    // Directory for BDK wallet databases (persistent cache)
    private val bdkDbDir: File = File(context.filesDir, BDK_DB_DIR).apply { mkdirs() }
    private val lwkDbDir: File = File(context.filesDir, LWK_DB_DIR).apply { mkdirs() }
    private val sparkDbDir: File = File(context.filesDir, SPARK_DB_DIR)
    private val sweepTempDbDir: File = File(context.cacheDir, SWEEP_TEMP_DB_DIR).apply { mkdirs() }

    init {
        if (!cleanupSweepTempDatabases() && BuildConfig.DEBUG) {
            Log.w(TAG, "Failed to clean sweep temp databases on startup")
        }
    }

    /**
     * Get the database file path for a wallet's persistent BDK storage
     */
    private fun getWalletDbPath(walletId: String): String {
        return File(bdkDbDir, "$walletId.db").absolutePath
    }

    private fun getSweepTempDbPath(prefix: String): String {
        return File(sweepTempDbDir, "${prefix}_${UUID.randomUUID()}.db").absolutePath
    }

    /**
     * Delete the BDK database files for a wallet
     */
    private fun deleteWalletDatabase(walletId: String) {
        val deleted = deleteSqliteArtifacts(getWalletDbPath(walletId))
        if (deleted) {
            if (BuildConfig.DEBUG) Log.d(TAG, "Deleted wallet database for $walletId")
        } else {
            SecureLog.e(
                TAG,
                "Failed to delete wallet database",
                releaseMessage = "Wallet database cleanup failed",
            )
        }
    }

    private fun deleteLiquidWalletDatabase(walletId: String) {
        val deleted = deleteSqliteArtifacts(File(lwkDbDir, "$walletId.db").absolutePath)
        if (deleted) {
            if (BuildConfig.DEBUG) Log.d(TAG, "Deleted Liquid wallet database for $walletId")
        } else {
            SecureLog.e(
                TAG,
                "Failed to delete Liquid wallet database",
                releaseMessage = "Liquid wallet database cleanup failed",
            )
        }
    }

    private fun deleteSqliteArtifacts(dbPath: String): Boolean {
        val sqliteFiles = listOf(
            File(dbPath),
            File("$dbPath-wal"),
            File("$dbPath-shm"),
            File("$dbPath-journal"),
        )

        var deletedAllExistingFiles = true
        sqliteFiles.forEach { file ->
            if (file.exists() && !file.delete()) {
                deletedAllExistingFiles = false
            }
        }

        return deletedAllExistingFiles && sqliteFiles.none { it.exists() }
    }

    private fun cleanupSweepTempDatabases(): Boolean {
        val deleted = !sweepTempDbDir.exists() || sweepTempDbDir.deleteRecursively()
        val recreated = sweepTempDbDir.exists() || sweepTempDbDir.mkdirs()
        return deleted && recreated
    }

    private fun clearLoadedWallet() {
        transactionRefreshJob?.cancel()
        transactionRefreshJob = null
        pendingBlockSyncJob?.cancel()
        pendingBlockSyncJob = null
        bitcoinSearchIndexJob?.cancel()
        bitcoinSearchIndexJob = null
        wallet = null
        walletPersister = null
        walletExternalDescriptor = null
        walletInternalDescriptor = null
        walletIsSingleKey = false
        walletTransactionCache.clear()
        peekAddressStringCache.clear()
        incrementalReconcileCounter = 0
        lastIncrementalReconcileAtMs = 0L
    }

    private fun replaceLoadedWallet(
        loadedWallet: Wallet,
        persister: Persister,
        externalDescriptor: Descriptor?,
        internalDescriptor: Descriptor?,
        isSingleKey: Boolean,
    ) {
        transactionRefreshJob?.cancel()
        transactionRefreshJob = null
        pendingBlockSyncJob?.cancel()
        pendingBlockSyncJob = null
        bitcoinSearchIndexJob?.cancel()
        bitcoinSearchIndexJob = null
        wallet = loadedWallet
        walletPersister = persister
        walletExternalDescriptor = externalDescriptor
        walletInternalDescriptor = internalDescriptor
        walletIsSingleKey = isSingleKey
        walletTransactionCache.clear()
        peekAddressStringCache.clear()
        incrementalReconcileCounter = 0
        lastIncrementalReconcileAtMs = 0L
    }

    /**
     * Peek a wallet address by (keychain, index), memoising the derivation. BDK's
     * `peekAddress` is deterministic for a given descriptor so the cached string stays valid
     * for the lifetime of the loaded wallet. Used by hot paths that walk the whole revealed
     * range (address book, script hash sampling, reclaim) on wallets with thousands of addresses.
     */
    private fun peekAddressStringCached(
        currentWallet: Wallet,
        keychain: KeychainKind,
        index: UInt,
    ): String {
        val key = keychain to index
        peekAddressStringCache[key]?.let { return it }
        val derived = currentWallet.peekAddress(keychain, index).address.toString()
        peekAddressStringCache[key] = derived
        return derived
    }

    /**
     * Reload the wallet object from the persisted database without tearing down
     * Electrum connections. Works around a BDK issue where `applyUpdateEvents()`
     * a full scan that reveals new addresses doesn't fully rebuild the internal
     * UTXO/keychain index - `balance()` returns stale data while `transactions()`
     * is correct. A fresh `Wallet.load()` forces BDK to reinitialise from disk.
     *
     * @return true if the wallet was successfully reloaded
     */
    private fun reloadWalletFromDatabase(): Boolean {
        val persister = walletPersister ?: return false
        val extDesc = walletExternalDescriptor ?: return false
        return try {
            wallet = if (walletIsSingleKey) {
                Wallet.loadSingle(extDesc, persister)
            } else {
                val intDesc = walletInternalDescriptor ?: return false
                Wallet.load(extDesc, intDesc, persister)
            }
            walletTransactionCache.clear()
            true
        } catch (e: Exception) {
            val activeWalletId = secureStorage.getActiveWalletId()
            val activeWalletMetadata = activeWalletId?.let { secureStorage.getWalletMetadata(it) }
            val fallback =
                if (activeWalletId != null && activeWalletMetadata != null) {
                    tryLoadWatchOnlyWalletWithCompatibleOrigin(
                        walletId = activeWalletId,
                        storedWallet = activeWalletMetadata,
                        network = activeWalletMetadata.network.toBdkNetwork(),
                        persister = persister,
                        loadError = e,
                    )
                } else {
                    null
                }
            if (fallback != null) {
                val (reloadedWallet, fallbackExternal, fallbackInternal) = fallback
                wallet = reloadedWallet
                walletExternalDescriptor = fallbackExternal
                walletInternalDescriptor = fallbackInternal
                walletIsSingleKey = false
                walletTransactionCache.clear()
                if (BuildConfig.DEBUG) {
                    Log.w(
                        TAG,
                        "Wallet reload fell back to key-only watch-only descriptors for DB compatibility",
                    )
                }
                return true
            }
            if (BuildConfig.DEBUG) Log.w(TAG, "Wallet reload from database failed: ${e.message}")
            false
        }
    }

    private fun isDescriptorMismatch(error: Throwable): Boolean {
        val details =
            generateSequence(error) { it.cause }
                .mapNotNull { throwable -> throwable.message?.trim()?.takeIf { it.isNotEmpty() } }
                .joinToString(separator = " | ")
        return details.contains("Descriptor mismatch", ignoreCase = true)
    }

    private fun canRetryWatchOnlyLoadWithoutMetadataOrigin(extendedKey: String): Boolean {
        val input = extendedKey.trim()
        val descriptorPrefixes = listOf("pkh(", "wpkh(", "tr(", "wsh(", "sh(wsh(")
        if (descriptorPrefixes.any { input.lowercase().startsWith(it) }) {
            return false
        }
        return parseKeyOrigin(input).fingerprint == null
    }

    private fun tryLoadWatchOnlyWalletWithCompatibleOrigin(
        walletId: String,
        storedWallet: StoredWallet,
        network: Network,
        persister: Persister,
        loadError: Throwable,
    ): Triple<Wallet, Descriptor, Descriptor>? {
        if (!storedWallet.isWatchOnly || !isDescriptorMismatch(loadError) || !secureStorage.hasExtendedKey(walletId)) {
            return null
        }

        val extendedKey = secureStorage.getExtendedKey(walletId) ?: return null
        if (!canRetryWatchOnlyLoadWithoutMetadataOrigin(extendedKey)) {
            return null
        }

        val (fallbackExternal, fallbackInternal) =
            createWatchOnlyDescriptors(
                extendedKey = extendedKey,
                addressType = storedWallet.addressType,
                network = network,
                masterFingerprint = null,
            )

        return try {
            val loadedWallet = Wallet.load(fallbackExternal, fallbackInternal, persister)
            Triple(loadedWallet, fallbackExternal, fallbackInternal)
        } catch (fallbackError: Exception) {
            if (BuildConfig.DEBUG) {
                Log.w(
                    TAG,
                    "Watch-only descriptor fallback load failed: ${fallbackError.message}",
                )
            }
            null
        }
    }

    // Current active BDK wallet instance
    private var wallet: Wallet? = null
    private var walletPersister: Persister? = null
    private var walletExternalDescriptor: Descriptor? = null
    private var walletInternalDescriptor: Descriptor? = null
    private var walletIsSingleKey: Boolean = false
    private var electrumClient: ElectrumClient? = null
    private val walletTransactionCache = linkedMapOf<String, CachedWalletTransaction>()
    // Cache of (keychain, index) -> bech32 address string for the active wallet. Peek derivation
    // is deterministic but each call crosses a JNI boundary and is expensive on large wallets
    // (thousands of revealed addresses). Cleared on wallet unload/replace.
    private val peekAddressStringCache = java.util.concurrent.ConcurrentHashMap<Pair<KeychainKind, UInt>, String>()
    private var incrementalReconcileCounter = 0
    private var lastIncrementalReconcileAtMs = 0L

    // Protocol-aware caching proxy — terminates SSL, intercepts blockchain.transaction.get
    // to serve from persistent cache, and consolidates all Electrum connections (BDK traffic,
    // script hash subscriptions, verbose tx queries) through a single upstream socket.
    private var cachingProxy: CachingElectrumProxy? = null
    private val connectionMutex = Mutex()

    // Sync mutex - prevents concurrent sync operations
    private val syncMutex = Mutex()
    @Volatile
    private var abortingActiveFullSync = false

    // Adaptive batch size - halved on timeout, reset on success.
    // Initialized from persisted value (avoids re-learning slow Tor connections).
    private var currentBatchSize =
        secureStorage.getSyncBatchSize()
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
    private var transactionRefreshJob: Job? = null
    private var bitcoinSearchIndexJob: Job? = null
    private var pendingBlockSyncJob: Job? = null
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Tracks which script hashes are subscribed on the subscription socket.
    // Used to detect new addresses after sync (gap limit expansion) and
    // subscribe them without re-subscribing everything.
    private val subscribedScriptHashes = mutableSetOf<String>()

    /**
     * Subscribe to block headers on the connected Electrum server.
     * Used during the initial connect as a real upstream health check:
     * if this fails, the connection attempt should fail too.
     */
    private fun subscribeBlockHeaders(client: ElectrumClient) {
        val headerNotification = client.blockHeadersSubscribe()
        lastKnownBlockHeight = headerNotification.height
        // Propagate block height to wallet state immediately so the UI
        // can display it right after connecting (before sync completes).
        val height = headerNotification.height.toUInt()
        val current = _walletState.value
        if (current.blockHeight != height) {
            _walletState.value = current.copy(blockHeight = height)
        }
        if (BuildConfig.DEBUG) {
            Log.d(
                TAG,
                "Subscribed to block headers, tip at height ${headerNotification.height}",
            )
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

    private val _connectionEvents = MutableSharedFlow<ConnectionEvent>(extraBufferCapacity = 1)
    val connectionEvents: SharedFlow<ConnectionEvent> = _connectionEvents.asSharedFlow()

    /**
     * Convert a sat/vB fee rate (potentially fractional, e.g. 0.5) to a BDK FeeRate
     * using sat/kWU for sub-sat/vB precision.
     * 1 sat/vB = 250 sat/kWU, so 0.8 sat/vB = 200 sat/kWU.
     */
    /**
     * Delegates to [BitcoinUtils.feeRateToSatPerKwu].
     */
    private fun feeRateFromSatPerVb(satPerVb: Double): FeeRate {
        return FeeRate.fromSatPerKwu(BitcoinUtils.feeRateToSatPerKwu(satPerVb))
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
        useTor: Boolean,
    ): java.net.Socket {
        val storedFingerprint = secureStorage.getServerCertFingerprint(host, port)
        // For .onion+SSL, override isOnionHost=false so the trust manager
        // performs real TOFU verification instead of trust-all.
        val trustManager = TofuTrustManager(host, port, storedFingerprint, isOnionHost = false)
        val sslFactory = TofuTrustManager.createSSLSocketFactory(trustManager)

        var rawSocket: java.net.Socket? = null
        var sslSocket: java.net.Socket? = null
        var success = false
        try {
            rawSocket =
                if (useTor) {
                    val proxy =
                        java.net.Proxy(
                            java.net.Proxy.Type.SOCKS,
                            java.net.InetSocketAddress("127.0.0.1", 9050),
                        )
                    java.net.Socket(proxy).also {
                        it.soTimeout = 30_000
                        it.connect(
                            java.net.InetSocketAddress.createUnresolved(host, port),
                            30_000,
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
                try {
                    sslSocket?.close()
                } catch (_: Exception) {
                }
                try {
                    rawSocket?.close()
                } catch (_: Exception) {
                }
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
        unsignedTx: Transaction,
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
            } catch (_: Exception) {
                // fall through
            }
        } catch (_: Exception) {
            // sign failed — fall through to estimation
        }

        // Watch-only or finalization failed: estimate from reference sizes.
        // Descriptor.maxWeightToSatisfy() is only the satisfaction delta, not the
        // full serialized input weight, so derive the supported address type and
        // use the complete Optech reference weights instead.
        val walletAddrType =
            try {
                val pubDescStr = wallet.publicDescriptor(KeychainKind.EXTERNAL)
                BitcoinUtils.detectDescriptorAddressType(pubDescStr)
            } catch (_: Exception) {
                null
            } ?: try {
                val addr = wallet.revealNextAddress(KeychainKind.EXTERNAL)
                BitcoinUtils.detectAddressType(addr.address.toString())
            } catch (_: Exception) {
                null
            } ?: AddressType.SEGWIT
        val inputWU = BitcoinUtils.inputWeightWU(walletAddrType)
        val hasWitness = walletAddrType != AddressType.LEGACY

        val numInputs = unsignedTx.input().size
        val outputScriptLengths = unsignedTx.output().map { it.scriptPubkey.toBytes().size }

        return BitcoinUtils.estimateVsizeFromComponents(
            numInputs = numInputs,
            inputWeightWU = inputWU,
            outputScriptLengths = outputScriptLengths,
            hasWitness = hasWitness,
        )
    }

    /**
     * Result of the two-pass fee correction: the exact fee AND the ceiled vsize
     * from signing the pass-1 PSBT. The vsize here is the authoritative value —
     * callers should use it for display rather than re-signing pass-2 (which would
     * produce a different DSPACE signature and potentially differ by ±1 WU).
     */
    private data class ExactFeeResult(val feeSats: ULong, val vsize: Double)

    /**
     * Two-pass fee correction: sign the pass-1 PSBT to measure ceiled vsize
     * (matching Bitcoin Core / mempool.space), then compute exact fee =
     * round(targetRate * ceiledVsize).
     *
     * Returns null if the PSBT can't be signed (watch-only wallet).
     */
    /**
     * Two-pass fee correction: sign the pass-1 PSBT to measure ceiled vsize,
     * then delegates arithmetic to [BitcoinUtils.computeExactFeeSats].
     */
    private fun computeExactFee(
        psbt: Psbt,
        wallet: Wallet,
        unsignedTx: Transaction,
        targetSatPerVb: Double,
    ): ExactFeeResult? {
        val vsize = estimateSignedVBytes(psbt, wallet, unsignedTx)
        val feeSats = BitcoinUtils.computeExactFeeSats(targetSatPerVb, vsize) ?: return null
        return ExactFeeResult(feeSats, vsize)
    }

    /**
     * Result of [signWithFeeCorrection]: the broadcast-ready [Transaction]
     * whose effective fee rate matches the target as closely as possible.
     */
    private data class SignedTxResult(val tx: Transaction, val feeSats: ULong, val vsize: Double)

    private data class PreparedPsbtBuild(
        val psbt: Psbt,
        val finalTx: Transaction,
        val feeSats: ULong,
        val txVBytes: Double,
    )

    private data class SingleRecipientOutputSummary(
        val recipientAmountSats: ULong,
        val changeAmountSats: ULong?,
        val changeAddress: String?,
        val hasChange: Boolean,
    )

    private data class MultiRecipientOutputSummary(
        val totalRecipientAmountSats: ULong,
        val changeAmountSats: ULong?,
        val changeAddress: String?,
        val hasChange: Boolean,
    )

    private data class SendRecipientScript(
        val recipient: Recipient,
        val script: Script,
        val isSilentPayment: Boolean,
    )

    /**
     * Sign a PSBT and iteratively correct the fee so the effective rate
     * (fee / ceil(weight/4)) matches [targetSatPerVb] after ECDSA signature
     * non-determinism is accounted for.
     *
     * Algorithm:
     *  1. Sign [initialPsbt], measure actual weight -> vsize.
     *  2. Compute targetFee = round(rate * vsize).
     *  3. If targetFee == baked-in fee, return (done).
     *  4. Otherwise rebuild an unsigned PSBT via [rebuildWithFee] using
     *     targetFee, sign it, measure again.
     *  5. If the second sign's vsize yields the same targetFee, return.
     *  6. On oscillation (two different vsizes -> two different targetFees),
     *     pick the candidate whose effective rate is closer to the target.
     *
     * @param initialPsbt  The unsigned PSBT from the two-pass pre-correction.
     * @param wallet       The wallet used for signing.
     * @param targetSatPerVb  The user-chosen fee rate.
     * @param rebuildWithFee  Lambda that builds a NEW unsigned PSBT with the
     *                        given absolute fee baked in. Returns null if the
     *                        rebuild fails (e.g. insufficient funds).
     */
    private fun signWithFeeCorrection(
        initialPsbt: Psbt,
        wallet: Wallet,
        targetSatPerVb: Double,
        rebuildWithFee: (ULong) -> Psbt?,
    ): SignedTxResult {
        // --- attempt 1: sign the initial PSBT ---
        wallet.sign(initialPsbt)
        val tx = initialPsbt.extractTx()
        val bakedFee = try { initialPsbt.fee() } catch (_: Exception) { 0UL }
        val weight = tx.weight()
        val vsize = kotlin.math.ceil(weight.toDouble() / 4.0)
        val targetFee = BitcoinUtils.computeExactFeeSats(targetSatPerVb, vsize)

        if (targetFee == null || targetFee == bakedFee) {
            return SignedTxResult(tx, bakedFee, vsize)
        }

        // --- attempt 2: rebuild with corrected fee, re-sign ---
        val correctedPsbt = try {
            rebuildWithFee(targetFee)
        } catch (_: Exception) {
            null
        }
        if (correctedPsbt == null) {
            return SignedTxResult(tx, bakedFee, vsize)
        }

        wallet.sign(correctedPsbt)
        val tx2 = correctedPsbt.extractTx()
        val bakedFee2 = try { correctedPsbt.fee() } catch (_: Exception) { 0UL }
        val weight2 = tx2.weight()
        val vsize2 = kotlin.math.ceil(weight2.toDouble() / 4.0)
        val targetFee2 = BitcoinUtils.computeExactFeeSats(targetSatPerVb, vsize2)

        // If the corrected fee still matches the baked fee for this vsize, we're done
        if (targetFee2 == null || targetFee2 == bakedFee2) {
            return SignedTxResult(tx2, bakedFee2, vsize2)
        }

        // --- attempt 3: one more rebuild to try to converge ---
        val correctedPsbt3 = try {
            rebuildWithFee(targetFee2)
        } catch (_: Exception) {
            null
        }
        if (correctedPsbt3 != null) {
            try {
                wallet.sign(correctedPsbt3)
                val tx3 = correctedPsbt3.extractTx()
                val bakedFee3 = try { correctedPsbt3.fee() } catch (_: Exception) { 0UL }
                val weight3 = tx3.weight()
                val vsize3 = kotlin.math.ceil(weight3.toDouble() / 4.0)
                val targetFee3 = BitcoinUtils.computeExactFeeSats(targetSatPerVb, vsize3)

                if (targetFee3 == null || targetFee3 == bakedFee3) {
                    return SignedTxResult(tx3, bakedFee3, vsize3)
                }

                // Still oscillating — pick the best among all three candidates
                val candidates = listOf(
                    SignedTxResult(tx, bakedFee, vsize),
                    SignedTxResult(tx2, bakedFee2, vsize2),
                    SignedTxResult(tx3, bakedFee3, vsize3),
                )
                return candidates.minByOrNull {
                    kotlin.math.abs(it.feeSats.toDouble() / it.vsize - targetSatPerVb)
                } ?: SignedTxResult(tx2, bakedFee2, vsize2)
            } catch (_: Exception) {
                // Fall through to pick best of first two
            }
        }

        // Oscillation between attempt 1 and 2 — pick the one closer to target
        val err1 = kotlin.math.abs(bakedFee.toDouble() / vsize - targetSatPerVb)
        val err2 = kotlin.math.abs(bakedFee2.toDouble() / vsize2 - targetSatPerVb)
        return if (err2 <= err1) {
            SignedTxResult(tx2, bakedFee2, vsize2)
        } else {
            SignedTxResult(tx, bakedFee, vsize)
        }
    }

    private val _walletState = MutableStateFlow(WalletState())
    val walletState: StateFlow<WalletState> = _walletState.asStateFlow()
    private val preparedBitcoinSendCacheMutex = Mutex()
    private var preparedBitcoinSendCache: PreparedBitcoinSendCacheEntry? = null

    /**
     * Check if any wallet has been initialized
     */
    fun isWalletInitialized(): Boolean {
        return secureStorage.getWalletIds().isNotEmpty()
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
        val passphrase: String? = null,
        val privateKey: String? = null, // WIF private key (for single-key wallets)
        val watchAddress: String? = null, // Single watched address
        val multisigConfig: MultisigWalletConfig? = null,
        val localCosignerKeyMaterial: String? = null,
    ) {
        /** Redact sensitive fields to prevent accidental logging of key material. */
        override fun toString(): String =
            "WalletKeyMaterial(hasMnemonic=${mnemonic != null}, hasXpub=${extendedPublicKey != null}, " +
                "isWatchOnly=$isWatchOnly, hasPassphrase=${passphrase != null}, hasPrivateKey=${privateKey != null}, " +
                    "hasWatchAddress=${watchAddress != null}, hasMultisig=${multisigConfig != null}, " +
                        "hasLocalCosigner=${localCosignerKeyMaterial != null})"
    }

    /**
     * Get the key material (mnemonic and/or extended public key) for a wallet
     * For full wallets: returns both mnemonic and derived xpub
     * For watch-only wallets: returns only xpub
     * For WIF wallets: returns the private key
     * For watch address wallets: returns the watched address
     */
    fun getKeyMaterial(walletId: String): WalletKeyMaterial? {
        val storedWallet = secureStorage.getWalletMetadata(walletId) ?: return null

        if (storedWallet.policyType == WalletPolicyType.MULTISIG) {
            val multisigConfig = secureStorage.getMultisigWalletConfig(walletId)
            return WalletKeyMaterial(
                mnemonic = null,
                extendedPublicKey = multisigConfig?.externalDescriptor,
                isWatchOnly = storedWallet.isWatchOnly,
                multisigConfig = multisigConfig,
                localCosignerKeyMaterial = secureStorage.getLocalCosignerKeyMaterial(walletId),
            )
        }

        // Check for watch address (single address watch-only)
        val watchAddress = secureStorage.getWatchAddress(walletId)
        if (watchAddress != null) {
            return WalletKeyMaterial(
                mnemonic = null,
                extendedPublicKey = null,
                isWatchOnly = true,
                watchAddress = watchAddress,
            )
        }

        // Check for WIF private key (single-key wallet)
        val privateKey = secureStorage.getPrivateKey(walletId)
        if (privateKey != null) {
            return WalletKeyMaterial(
                mnemonic = null,
                extendedPublicKey = null,
                isWatchOnly = false,
                privateKey = privateKey,
            )
        }

        // Check for extended key (watch-only wallet)
        val extendedKey = secureStorage.getExtendedKey(walletId)
        if (extendedKey != null) {
            return WalletKeyMaterial(
                mnemonic = null,
                extendedPublicKey = extendedKey,
                isWatchOnly = true,
            )
        }

        // Check for mnemonic (full wallet)
        val mnemonic = secureStorage.getMnemonic(walletId)
        if (mnemonic != null) {
            // Derive the extended public key from the mnemonic
            val xpub =
                try {
                    when (storedWallet.seedFormat) {
                        SeedFormat.ELECTRUM_STANDARD -> {
                            val seed = ElectrumSeedUtil.mnemonicToSeed(mnemonic, secureStorage.getPassphrase(walletId))
                            ElectrumSeedUtil.deriveExtendedPublicKey(seed, ElectrumSeedUtil.ElectrumSeedType.STANDARD)
                        }
                        SeedFormat.ELECTRUM_SEGWIT -> {
                            val seed = ElectrumSeedUtil.mnemonicToSeed(mnemonic, secureStorage.getPassphrase(walletId))
                            ElectrumSeedUtil.deriveExtendedPublicKey(seed, ElectrumSeedUtil.ElectrumSeedType.SEGWIT)
                        }
                        else -> deriveExtendedPublicKey(
                            walletId = walletId,
                            mnemonic = mnemonic,
                            passphrase = secureStorage.getPassphrase(walletId),
                            addressType = storedWallet.addressType,
                            network = storedWallet.network,
                        )
                    }
                } catch (_: Exception) {
                    null
                }

            return WalletKeyMaterial(
                mnemonic = mnemonic,
                extendedPublicKey = xpub,
                isWatchOnly = false,
                passphrase = secureStorage.getPassphrase(walletId),
            )
        }

        return null
    }

    fun signMessage(
        walletId: String,
        address: String,
        message: String,
    ): WalletResult<String> {
        val cleanAddress = address.trim()
        if (cleanAddress.isBlank()) return WalletResult.Error("Enter an address")
        if (message.isBlank()) return WalletResult.Error("Enter a message")

        val storedWallet = secureStorage.getWalletMetadata(walletId)
            ?: return WalletResult.Error("Wallet not found")
        if (storedWallet.policyType != WalletPolicyType.SINGLE_SIG) {
            return WalletResult.Error("Message signing is only available for single-sig wallets")
        }

        val addressType = BitcoinUtils.detectAddressType(cleanAddress)
            ?: return WalletResult.Error("Unsupported address")
        if (addressType == AddressType.TAPROOT) {
            return WalletResult.Error("BIP137 does not support Taproot addresses")
        }

        val wif = secureStorage.getPrivateKey(walletId)
        if (wif != null) {
            return signWifMessage(wif, cleanAddress, addressType, message)
        }

        if (storedWallet.isWatchOnly) {
            return WalletResult.Error("This wallet is watch-only")
        }
        if (storedWallet.addressType == AddressType.TAPROOT) {
            return WalletResult.Error("BIP137 does not support Taproot wallets")
        }
        if (storedWallet.addressType != addressType) {
            return WalletResult.Error("Address type does not match this wallet")
        }

        val mnemonic = secureStorage.getMnemonic(walletId)
            ?: return WalletResult.Error("No signing key available")
        val passphrase = secureStorage.getPassphrase(walletId)
        val seed = when (storedWallet.seedFormat) {
            SeedFormat.BIP39 -> ElectrumSeedUtil.bip39MnemonicToSeed(mnemonic, passphrase)
            SeedFormat.ELECTRUM_STANDARD, SeedFormat.ELECTRUM_SEGWIT ->
                ElectrumSeedUtil.mnemonicToSeed(mnemonic, passphrase)
        }

        val activeWalletLoaded = secureStorage.getActiveWalletId() == walletId
        val currentWallet = if (activeWalletLoaded) wallet else null
        val externalLimit = messageSigningScanLimit(storedWallet, currentWallet, KeychainKind.EXTERNAL)
        val internalLimit = messageSigningScanLimit(storedWallet, currentWallet, KeychainKind.INTERNAL)

        listOf(
            KeychainKind.EXTERNAL to externalLimit,
            KeychainKind.INTERNAL to internalLimit,
        ).forEach { (keychain, limit) ->
            for (index in 0..limit) {
                val path = messageSigningPath(storedWallet, keychain, index)
                val privateKey = ElectrumSeedUtil.derivePrivateKey(seed, path)
                val derivedAddress = Bip137MessageSigner.addressForPrivateKey(privateKey, addressType)
                if (derivedAddress == cleanAddress) {
                    return WalletResult.Success(Bip137MessageSigner.sign(privateKey, addressType, message))
                }
            }
        }

        return WalletResult.Error("Address not found in this wallet")
    }

    fun verifyMessage(
        address: String,
        message: String,
        signatureBase64: String,
    ): WalletResult<Boolean> {
        if (address.isBlank()) return WalletResult.Error("Enter an address")
        if (message.isBlank()) return WalletResult.Error("Enter a message")
        if (signatureBase64.isBlank()) return WalletResult.Error("Enter a signature")
        if (BitcoinUtils.detectAddressType(address.trim()) == AddressType.TAPROOT) {
            return WalletResult.Error("BIP137 does not support Taproot addresses")
        }
        return WalletResult.Success(Bip137MessageSigner.verify(address, message, signatureBase64))
    }

    private fun signWifMessage(
        wif: String,
        address: String,
        addressType: AddressType,
        message: String,
    ): WalletResult<String> =
        try {
            val decoded = Bip137MessageSigner.decodeWif(wif)
            val derivedAddress = Bip137MessageSigner.addressForPrivateKey(
                privateKeyBytes = decoded.privateKeyBytes,
                addressType = addressType,
                compressed = decoded.compressed,
            )
            if (derivedAddress != address) {
                WalletResult.Error("Address not found in this wallet")
            } else {
                WalletResult.Success(
                    Bip137MessageSigner.sign(
                        privateKeyBytes = decoded.privateKeyBytes,
                        addressType = addressType,
                        message = message,
                        compressed = decoded.compressed,
                    ),
                )
            }
        } catch (e: Exception) {
            WalletResult.Error("Invalid private key", e)
        }

    private fun messageSigningScanLimit(
        storedWallet: StoredWallet,
        currentWallet: Wallet?,
        keychain: KeychainKind,
    ): Int {
        val revealed = try {
            currentWallet?.derivationIndex(keychain)?.toInt()
        } catch (_: Exception) {
            null
        }
        return maxOf(revealed ?: 0, storedWallet.gapLimit, MESSAGE_SIGNING_MIN_SCAN_LIMIT) +
            MESSAGE_SIGNING_LOOKAHEAD
    }

    private fun messageSigningPath(
        storedWallet: StoredWallet,
        keychain: KeychainKind,
        index: Int,
    ): String {
        val branch = when (keychain) {
            KeychainKind.EXTERNAL -> 0
            KeychainKind.INTERNAL -> 1
        }
        return when (storedWallet.seedFormat) {
            SeedFormat.ELECTRUM_STANDARD -> "m/$branch/$index"
            SeedFormat.ELECTRUM_SEGWIT -> "m/0'/$branch/$index"
            SeedFormat.BIP39 -> {
                val base = storedWallet.derivationPath.trim().ifBlank { storedWallet.addressType.defaultPath }
                val branchPath =
                    if (base.endsWith("/0") || base.endsWith("/1")) {
                        base.dropLast(2) + "/$branch"
                    } else {
                        "$base/$branch"
                    }
                "$branchPath/$index"
            }
        }
    }

    /**
     * Derive the extended public key from a mnemonic.
     * Prefers wallet.publicDescriptor() when the wallet is loaded (guaranteed public-key-only),
     * falls back to creating a Descriptor from the mnemonic and extracting the xpub.
     */
    private fun deriveExtendedPublicKey(
        walletId: String,
        mnemonic: String,
        passphrase: String?,
        addressType: AddressType,
        network: WalletNetwork,
    ): String {
        // Only trust the loaded BDK wallet when deriving the active wallet's xpub.
        // Otherwise revealing another wallet can accidentally reuse the active wallet's descriptor.
        val currentWallet = wallet
        if (currentWallet != null && secureStorage.getActiveWalletId() == walletId) {
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
        val descriptorSecretKey =
            DescriptorSecretKey(
                network = bdkNetwork,
                mnemonic = mnemonicObj,
                password = passphrase,
            )

        // Get the descriptor based on address type
        val descriptor =
            when (addressType) {
                AddressType.LEGACY ->
                    Descriptor.newBip44(
                        secretKey = descriptorSecretKey,
                        keychainKind = KeychainKind.EXTERNAL,
                        network = bdkNetwork,
                    )
                AddressType.SEGWIT ->
                    Descriptor.newBip84(
                        secretKey = descriptorSecretKey,
                        keychainKind = KeychainKind.EXTERNAL,
                        network = bdkNetwork,
                    )
                AddressType.TAPROOT ->
                    Descriptor.newBip86(
                        secretKey = descriptorSecretKey,
                        keychainKind = KeychainKind.EXTERNAL,
                        network = bdkNetwork,
                    )
            }

        // Extract the supported xpub from the descriptor string
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
        network: WalletNetwork,
    ): String? {
        return try {
            val bdkNetwork = network.toBdkNetwork()
            val mnemonicObj = Mnemonic.fromString(mnemonic)
            val descriptorSecretKey =
                DescriptorSecretKey(
                    network = bdkNetwork,
                    mnemonic = mnemonicObj,
                    password = passphrase,
                )
            val descriptor =
                when (addressType) {
                    AddressType.LEGACY -> Descriptor.newBip44(descriptorSecretKey, KeychainKind.EXTERNAL, bdkNetwork)
                    AddressType.SEGWIT -> Descriptor.newBip84(descriptorSecretKey, KeychainKind.EXTERNAL, bdkNetwork)
                    AddressType.TAPROOT -> Descriptor.newBip86(descriptorSecretKey, KeychainKind.EXTERNAL, bdkNetwork)
                }
            // Descriptor string: wpkh([73c5da0a/84'/0'/0']xpub.../0/*)
            extractFingerprint(descriptor.toString())
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Import a wallet with full configuration
     */
    suspend fun importWallet(config: WalletImportConfig): WalletResult<Unit> =
        withContext(Dispatchers.IO) {
            try {
                if (config.network != WalletNetwork.BITCOIN) {
                    return@withContext WalletResult.Error(BitcoinUtils.UNSUPPORTED_NON_MAINNET_MESSAGE)
                }
                val bdkNetwork = config.network.toBdkNetwork()
                val trimmedKey = config.keyMaterial.trim()
                BitcoinUtils.unsupportedNonMainnetReason(trimmedKey)?.let {
                    return@withContext WalletResult.Error(it)
                }
                BitcoinUtils.unsupportedNestedSegwitReason(trimmedKey)?.let {
                    return@withContext WalletResult.Error(it)
                }
                val multisigConfig =
                    config.multisigConfig ?: if (MultisigWalletParser.looksLikeMultisig(trimmedKey)) {
                        MultisigWalletParser.parse(trimmedKey)
                    } else {
                        null
                    }
                val isWatchOnly = isWatchOnlyInput(trimmedKey)
                val isWif = isWifPrivateKey(trimmedKey)
                val isSingleAddress = isBitcoinAddress(trimmedKey)

                // Detect Electrum native seed format
                val isElectrumSeed = config.seedFormat != SeedFormat.BIP39
                val electrumSeedType = if (isElectrumSeed) {
                    when (config.seedFormat) {
                        SeedFormat.ELECTRUM_STANDARD -> ElectrumSeedUtil.ElectrumSeedType.STANDARD
                        SeedFormat.ELECTRUM_SEGWIT -> ElectrumSeedUtil.ElectrumSeedType.SEGWIT
                        else -> null
                    }
                } else {
                    null
                }

                // Extract fingerprint from key origin if present, fall back to user-provided
                val fingerprint = extractFingerprint(trimmedKey) ?: config.masterFingerprint

                val derivationPath = when {
                    config.customDerivationPath != null -> config.customDerivationPath
                    isWif || isSingleAddress -> "single"
                    electrumSeedType == ElectrumSeedUtil.ElectrumSeedType.STANDARD -> "m"
                    electrumSeedType == ElectrumSeedUtil.ElectrumSeedType.SEGWIT -> "m/0'"
                    else -> config.addressType.defaultPath
                }
                val walletId = UUID.randomUUID().toString()

                if (multisigConfig != null) {
                    return@withContext importMultisigWallet(
                        walletId = walletId,
                        importConfig = config,
                        multisigConfig = multisigConfig,
                        network = bdkNetwork,
                    )
                }

                // For full wallets, derive the master fingerprint from the mnemonic
                val resolvedFingerprint =
                    when {
                        isWif || isSingleAddress -> null
                        isWatchOnly -> fingerprint
                        isElectrumSeed ->
                            deriveElectrumFingerprint(
                                config.keyMaterial,
                                config.passphrase,
                            )
                        else ->
                            deriveMasterFingerprint(
                                config.keyMaterial,
                                config.passphrase,
                                config.addressType,
                                config.network,
                            )
                    }

                // Single address watch: use Electrum-only tracking (no BDK wallet)
                if (isSingleAddress) {
                    val detectedType = detectAddressType(trimmedKey) ?: config.addressType
                    val storedWallet =
                        StoredWallet(
                            id = walletId,
                            name = config.name,
                            addressType = detectedType,
                            derivationPath = "single",
                            isWatchOnly = true,
                            network = config.network,
                            masterFingerprint = null,
                            gapLimit = config.gapLimit,
                        )
                    secureStorage.saveWalletMetadata(storedWallet)
                    secureStorage.saveWatchAddress(walletId, trimmedKey)
                    secureStorage.saveNetwork(config.network)
                    secureStorage.setNeedsFullSync(walletId, false)
                    clearLoadedWallet()
                    secureStorage.setActiveWalletId(walletId)
                    updateWalletState()
                    return@withContext WalletResult.Success(Unit)
                }

                // Create wallet with persistent SQLite storage
                val persister = Persister.newSqlite(getWalletDbPath(walletId))

                if (isWif) {
                    // Single-key WIF wallet — uses Wallet.createSingle (no change descriptor)
                    val descriptor = createDescriptorFromWif(trimmedKey, config.addressType, bdkNetwork)
                    val importedWallet =
                        Wallet.createSingle(
                            descriptor = descriptor,
                            network = bdkNetwork,
                            persister = persister,
                        )
                    replaceLoadedWallet(
                        loadedWallet = importedWallet,
                        persister = persister,
                        externalDescriptor = descriptor,
                        internalDescriptor = null,
                        isSingleKey = true,
                    )
                } else {
                    val (externalDescriptor, internalDescriptor) =
                        when {
                            isWatchOnly ->
                                createWatchOnlyDescriptors(
                                    trimmedKey,
                                    config.addressType,
                                    bdkNetwork,
                                    resolvedFingerprint,
                                )
                            electrumSeedType != null ->
                                createDescriptorsFromElectrumSeed(
                                    mnemonic = config.keyMaterial,
                                    passphrase = config.passphrase,
                                    seedType = electrumSeedType,
                                    network = bdkNetwork,
                                )
                            else ->
                                createDescriptorsFromMnemonic(
                                    mnemonic = config.keyMaterial,
                                    passphrase = config.passphrase,
                                    addressType = config.addressType,
                                    network = bdkNetwork,
                                )
                        }

                    // Use BDK's native multipath constructor when input is a BIP 389 descriptor.
                    // This avoids manual splitting and lets BDK handle the <0;1> derivation internally.
                    val isMultipathInput =
                        isWatchOnly &&
                            (trimmedKey.contains("<0;1>") || trimmedKey.contains("<1;0>"))

                    val importedWallet =
                        if (isMultipathInput) {
                            try {
                                val stripped = trimmedKey.substringBefore('#').trim()
                                val multipathDescriptor = Descriptor(stripped, bdkNetwork)
                                Wallet.createFromTwoPathDescriptor(
                                    twoPathDescriptor = multipathDescriptor,
                                    network = bdkNetwork,
                                    persister = persister,
                                )
                            } catch (e: Exception) {
                                if (BuildConfig.DEBUG) Log.w(TAG, "createFromTwoPathDescriptor failed, falling back to split descriptors: ${e.message}")
                                Wallet(
                                    descriptor = externalDescriptor,
                                    changeDescriptor = internalDescriptor,
                                    network = bdkNetwork,
                                    persister = persister,
                                )
                            }
                        } else {
                            Wallet(
                                descriptor = externalDescriptor,
                                changeDescriptor = internalDescriptor,
                                network = bdkNetwork,
                                persister = persister,
                            )
                        }
                    replaceLoadedWallet(
                        loadedWallet = importedWallet,
                        persister = persister,
                        externalDescriptor = externalDescriptor,
                        internalDescriptor = internalDescriptor,
                        isSingleKey = false,
                    )
                }

                // Persist the new wallet to database
                wallet!!.persist(persister)

                // Save wallet metadata
                val storedWallet =
                    StoredWallet(
                        id = walletId,
                        name = config.name,
                        addressType = config.addressType,
                        derivationPath = derivationPath,
                        isWatchOnly = isWatchOnly,
                        network = config.network,
                        masterFingerprint = resolvedFingerprint,
                        seedFormat = config.seedFormat,
                        gapLimit = config.gapLimit,
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
                SecureLog.e(
                    TAG,
                    "Failed to import wallet",
                    e,
                    releaseMessage = "Wallet import failed",
                )
                WalletResult.Error("Failed to import wallet: ${e.message ?: e.javaClass.simpleName}", e)
            }
        }

    private fun importMultisigWallet(
        walletId: String,
        importConfig: WalletImportConfig,
        multisigConfig: MultisigWalletConfig,
        network: Network,
    ): WalletResult<Unit> {
        val localCosignerMaterial = importConfig.localCosignerKeyMaterial?.trim()?.takeIf { it.isNotBlank() }
        val hasPrivateDescriptor =
            localCosignerMaterial?.contains("prv", ignoreCase = true) == true ||
                multisigConfig.externalDescriptor.contains("prv", ignoreCase = true)
        val (externalDescriptor, internalDescriptor) =
            createMultisigDescriptors(multisigConfig, localCosignerMaterial, network)
        val persister = Persister.newSqlite(getWalletDbPath(walletId))
        val importedWallet =
            Wallet(
                descriptor = externalDescriptor,
                changeDescriptor = internalDescriptor,
                network = network,
                persister = persister,
            )
        replaceLoadedWallet(
            loadedWallet = importedWallet,
            persister = persister,
            externalDescriptor = externalDescriptor,
            internalDescriptor = internalDescriptor,
            isSingleKey = false,
        )
        wallet!!.persist(persister)

        val storedWallet =
            StoredWallet(
                id = walletId,
                name = importConfig.name.ifBlank { multisigConfig.name ?: "Multisig" },
                addressType = AddressType.SEGWIT,
                derivationPath = "multisig",
                isWatchOnly = !hasPrivateDescriptor,
                network = importConfig.network,
                masterFingerprint = multisigConfig.cosigners.firstOrNull()?.fingerprint,
                gapLimit = importConfig.gapLimit,
                policyType = WalletPolicyType.MULTISIG,
                multisigThreshold = multisigConfig.threshold,
                multisigTotalCosigners = multisigConfig.totalCosigners,
                multisigScriptType = multisigConfig.scriptType,
                localCosignerFingerprint =
                    multisigConfig.cosigners.firstOrNull { cosigner ->
                        localCosignerMaterial?.contains(cosigner.fingerprint, ignoreCase = true) == true
                    }?.fingerprint,
            )
        secureStorage.saveWalletMetadata(storedWallet)
        secureStorage.saveMultisigWalletConfig(walletId, multisigConfig)
        localCosignerMaterial?.let { secureStorage.saveLocalCosignerKeyMaterial(walletId, it) }
        secureStorage.saveNetwork(importConfig.network)
        secureStorage.setNeedsFullSync(walletId, true)
        secureStorage.setActiveWalletId(walletId)
        updateWalletState()
        return WalletResult.Success(Unit)
    }

    private fun createMultisigDescriptors(
        config: MultisigWalletConfig,
        localCosignerMaterial: String?,
        network: Network,
    ): Pair<Descriptor, Descriptor> {
        val descriptorPair =
            localCosignerMaterial
                ?.takeIf { MultisigWalletParser.looksLikeMultisig(it) }
                ?.let { MultisigWalletParser.normalizeDescriptorPair(it) }
                ?: (config.externalDescriptor to config.internalDescriptor)
        return Descriptor(descriptorPair.first, network) to Descriptor(descriptorPair.second, network)
    }

    /**
     * Import a Liquid-only watch-only wallet from a CT descriptor.
     * Creates a StoredWallet record without initializing a BDK wallet.
     */
    suspend fun importLiquidWatchOnlyWallet(
        name: String,
        ctDescriptor: String,
        gapLimit: Int,
    ): WalletResult<String> = withContext(Dispatchers.IO) {
        try {
            val walletId = UUID.randomUUID().toString()
            val storedWallet = StoredWallet(
                id = walletId,
                name = name,
                addressType = AddressType.SEGWIT,
                derivationPath = "liquid_ct",
                isWatchOnly = true,
                network = WalletNetwork.BITCOIN,
            )
            secureStorage.saveWalletMetadata(storedWallet)
            secureStorage.setLiquidDescriptor(walletId, ctDescriptor)
            secureStorage.setLiquidWatchOnly(walletId, true)
            secureStorage.setLayer2Enabled(true)
            secureStorage.setLiquidEnabledForWallet(walletId, true)
            secureStorage.setLiquidGapLimit(walletId, gapLimit)
            secureStorage.setNeedsFullSync(walletId, false)
            secureStorage.setNeedsLiquidFullSync(walletId, true)
            clearLoadedWallet()
            secureStorage.setActiveWalletId(walletId)
            updateWalletState()
            WalletResult.Success(walletId)
        } catch (e: Exception) {
            SecureLog.e(
                TAG,
                "Failed to import Liquid watch-only wallet",
                e,
                releaseMessage = "Liquid wallet import failed",
            )
            WalletResult.Error("Failed to import: ${e.message ?: e.javaClass.simpleName}", e)
        }
    }

    /**
     * Switch to a different wallet
     */
    suspend fun switchWallet(walletId: String): WalletResult<Unit> =
        withContext(Dispatchers.IO) {
            try {
                secureStorage.getWalletMetadata(walletId)
                    ?: return@withContext WalletResult.Error("Wallet not found")

                // Set as active
                secureStorage.setActiveWalletId(walletId)

                // Clear script hash cache - different wallet has different addresses
                clearScriptHashCache()

                // Load the wallet
                when (val loadResult = loadWalletById(walletId)) {
                    is WalletResult.Success -> WalletResult.Success(Unit)
                    is WalletResult.Error -> return@withContext loadResult
                }

                WalletResult.Success(Unit)
            } catch (e: Exception) {
                WalletResult.Error(e.message ?: "Failed to switch wallet", e)
            }
        }

    /**
     * Load a specific wallet by ID
     */
    private suspend fun loadWalletById(walletId: String): WalletResult<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val storedWallet =
                    secureStorage.getWalletMetadata(walletId)
                        ?: return@withContext WalletResult.Error("Wallet not found")

                val bdkNetwork = storedWallet.network.toBdkNetwork()
                clearLoadedWallet()

                // Watch address wallets have no BDK wallet — tracked via Electrum only
                if (secureStorage.hasWatchAddress(walletId)) {
                    updateWalletState()
                    return@withContext WalletResult.Success(Unit)
                }

                // Liquid-only watch-only wallets have no BDK wallet
                if (storedWallet.derivationPath == "liquid_ct") {
                    updateWalletState()
                    return@withContext WalletResult.Success(Unit)
                }

                if (storedWallet.policyType == WalletPolicyType.MULTISIG) {
                    val multisigConfig =
                        secureStorage.getMultisigWalletConfig(walletId)
                            ?: return@withContext WalletResult.Error("No multisig config found")
                    val localCosignerMaterial = secureStorage.getLocalCosignerKeyMaterial(walletId)
                    val (externalDescriptor, internalDescriptor) =
                        createMultisigDescriptors(multisigConfig, localCosignerMaterial, bdkNetwork)
                    val persister = Persister.newSqlite(getWalletDbPath(walletId))
                    walletPersister = persister
                    wallet =
                        try {
                            Wallet.load(externalDescriptor, internalDescriptor, persister)
                        } catch (e: Exception) {
                            if (isDescriptorMismatch(e)) {
                                throw e
                            }
                            val newWallet =
                                Wallet(
                                    descriptor = externalDescriptor,
                                    changeDescriptor = internalDescriptor,
                                    network = bdkNetwork,
                                    persister = persister,
                                )
                            newWallet.persist(persister)
                            newWallet
                        }
                    walletExternalDescriptor = externalDescriptor
                    walletInternalDescriptor = internalDescriptor
                    walletIsSingleKey = false
                    updateWalletStateLightweight()
                    wallet?.let { scheduleDetailedTransactionRefresh(walletId, it) }
                    return@withContext WalletResult.Success(Unit)
                }

                val isWifWallet = secureStorage.hasPrivateKey(walletId)

                // Load wallet with persistent SQLite storage
                val persister = Persister.newSqlite(getWalletDbPath(walletId))
                walletPersister = persister

                if (isWifWallet) {
                    // Single-key WIF wallet — uses Wallet.createSingle (no change descriptor)
                    val wif =
                        secureStorage.getPrivateKey(walletId)
                            ?: return@withContext WalletResult.Error("No private key found")
                    val descriptor = createDescriptorFromWif(wif, storedWallet.addressType, bdkNetwork)

                    wallet =
                        try {
                            Wallet.loadSingle(descriptor, persister)
                        } catch (_: Exception) {
                            if (BuildConfig.DEBUG) Log.d(TAG, "No existing wallet DB found, creating new single-key wallet for $walletId")
                            val newWallet =
                                Wallet.createSingle(
                                    descriptor = descriptor,
                                    network = bdkNetwork,
                                    persister = persister,
                                )
                            newWallet.persist(persister)
                            newWallet
                        }
                    walletExternalDescriptor = descriptor
                    walletInternalDescriptor = null
                    walletIsSingleKey = true
                } else {
                    var (externalDescriptor, internalDescriptor) =
                        when {
                            secureStorage.hasExtendedKey(walletId) -> {
                                // Watch-only wallet - include master fingerprint for PSBT signing
                                val extendedKey =
                                    secureStorage.getExtendedKey(walletId)
                                        ?: return@withContext WalletResult.Error("No extended key found")
                                createWatchOnlyDescriptors(
                                    extendedKey,
                                    storedWallet.addressType,
                                    bdkNetwork,
                                    storedWallet.masterFingerprint,
                                )
                            }
                            else -> {
                                // Full wallet from mnemonic
                                val mnemonic =
                                    secureStorage.getMnemonic(walletId)
                                        ?: return@withContext WalletResult.Error("No mnemonic found")
                                val passphrase = secureStorage.getPassphrase(walletId)

                                // Route Electrum seeds through their own derivation
                                when (storedWallet.seedFormat) {
                                    SeedFormat.ELECTRUM_STANDARD ->
                                        createDescriptorsFromElectrumSeed(
                                            mnemonic,
                                            passphrase,
                                            ElectrumSeedUtil.ElectrumSeedType.STANDARD,
                                            bdkNetwork,
                                        )
                                    SeedFormat.ELECTRUM_SEGWIT ->
                                        createDescriptorsFromElectrumSeed(
                                            mnemonic,
                                            passphrase,
                                            ElectrumSeedUtil.ElectrumSeedType.SEGWIT,
                                            bdkNetwork,
                                        )
                                    else ->
                                        createDescriptorsFromMnemonic(
                                            mnemonic,
                                            passphrase,
                                            storedWallet.addressType,
                                            bdkNetwork,
                                        )
                                }
                            }
                        }

                    // Try to load existing wallet from database, fall back to creating new if not found
                    wallet =
                        try {
                            Wallet.load(externalDescriptor, internalDescriptor, persister)
                        } catch (e: Exception) {
                            tryLoadWatchOnlyWalletWithCompatibleOrigin(
                                walletId = walletId,
                                storedWallet = storedWallet,
                                network = bdkNetwork,
                                persister = persister,
                                loadError = e,
                            )?.let { (loadedWallet, fallbackExternal, fallbackInternal) ->
                                externalDescriptor = fallbackExternal
                                internalDescriptor = fallbackInternal
                                loadedWallet
                            } ?: run {
                                if (isDescriptorMismatch(e)) {
                                    throw e
                                }
                                if (BuildConfig.DEBUG) {
                                    Log.d(TAG, "Wallet DB load failed, creating new wallet for $walletId: ${e.message}")
                                }
                                val newWallet =
                                    Wallet(
                                        descriptor = externalDescriptor,
                                        changeDescriptor = internalDescriptor,
                                        network = bdkNetwork,
                                        persister = persister,
                                    )
                                newWallet.persist(persister)
                                newWallet
                            }
                        }
                    walletExternalDescriptor = externalDescriptor
                    walletInternalDescriptor = internalDescriptor
                    walletIsSingleKey = false
                }

                val loadedWallet = wallet
                if (loadedWallet != null && hasWarmTransactionHistoryCache(loadedWallet, walletId)) {
                    updateWalletState()
                } else {
                    updateWalletStateLightweight()
                    loadedWallet?.let { hydratedWallet ->
                        scheduleDetailedTransactionRefresh(walletId, hydratedWallet)
                    }
                }

                WalletResult.Success(Unit)
            } catch (e: Exception) {
                WalletResult.Error(e.message ?: "Failed to load wallet", e)
            }
        }

    /**
     * Create descriptors from mnemonic based on address type
     */
    private fun createDescriptorsFromMnemonic(
        mnemonic: String,
        passphrase: String?,
        addressType: AddressType,
        network: Network,
    ): Pair<Descriptor, Descriptor> {
        val mnemonicObj = Mnemonic.fromString(mnemonic)
        val descriptorSecretKey =
            DescriptorSecretKey(
                network = network,
                mnemonic = mnemonicObj,
                password = passphrase,
            )

        return when (addressType) {
            AddressType.LEGACY -> {
                // BIP44 for Legacy P2PKH
                val external =
                    Descriptor.newBip44(
                        secretKey = descriptorSecretKey,
                        keychainKind = KeychainKind.EXTERNAL,
                        network = network,
                    )
                val internal =
                    Descriptor.newBip44(
                        secretKey = descriptorSecretKey,
                        keychainKind = KeychainKind.INTERNAL,
                        network = network,
                    )
                Pair(external, internal)
            }
            AddressType.SEGWIT -> {
                // BIP84 for Native SegWit P2WPKH
                val external =
                    Descriptor.newBip84(
                        secretKey = descriptorSecretKey,
                        keychainKind = KeychainKind.EXTERNAL,
                        network = network,
                    )
                val internal =
                    Descriptor.newBip84(
                        secretKey = descriptorSecretKey,
                        keychainKind = KeychainKind.INTERNAL,
                        network = network,
                    )
                Pair(external, internal)
            }
            AddressType.TAPROOT -> {
                // BIP86 for Taproot P2TR
                val external =
                    Descriptor.newBip86(
                        secretKey = descriptorSecretKey,
                        keychainKind = KeychainKind.EXTERNAL,
                        network = network,
                    )
                val internal =
                    Descriptor.newBip86(
                        secretKey = descriptorSecretKey,
                        keychainKind = KeychainKind.INTERNAL,
                        network = network,
                    )
                Pair(external, internal)
            }
        }
    }

    /**
     * Create descriptors from an Electrum native seed phrase.
     *
     * Electrum seeds use different key stretching (PBKDF2 with "electrum" salt)
     * and different derivation paths (m/ for Standard, m/0'/ for Segwit)
     * compared to BIP39 seeds. We derive the xprv ourselves and build raw
     * descriptor strings that BDK can parse.
     */
    private fun createDescriptorsFromElectrumSeed(
        mnemonic: String,
        passphrase: String?,
        seedType: ElectrumSeedUtil.ElectrumSeedType,
        network: Network,
    ): Pair<Descriptor, Descriptor> {
        val seed = ElectrumSeedUtil.mnemonicToSeed(mnemonic, passphrase)
        val (externalStr, internalStr) = ElectrumSeedUtil.buildDescriptorStrings(seed, seedType)
        return Pair(Descriptor(externalStr, network), Descriptor(internalStr, network))
    }

    /**
     * Derive the master fingerprint from an Electrum seed.
     */
    private fun deriveElectrumFingerprint(
        mnemonic: String,
        passphrase: String?,
    ): String? {
        return try {
            val seed = ElectrumSeedUtil.mnemonicToSeed(mnemonic, passphrase)
            ElectrumSeedUtil.computeMasterFingerprint(seed)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Convert extended public keys (zpub) to xpub format.
     *
     * Version bytes:
     * - xpub: 0x0488B21E (mainnet)
     * - zpub: 0x04B24746 (mainnet BIP84)
     */
    /**
     * Convert zpub to xpub.
     * Delegates to [BitcoinUtils.convertToXpub].
     */
    private fun convertToXpub(extendedKey: String): String =
        BitcoinUtils.convertToXpub(extendedKey)

    /**
     * Create watch-only descriptors from extended public key.
     *
     * Includes key origin info [fingerprint/derivation] when available, which is
     * required for hardware wallets (SeedSigner, Cold card, etc.) to:
     * - Match PSBT inputs to their seed for signing
     * - Verify input amounts
     * - Recognize change addresses
     *
     * Supported input formats:
     * - Bare xpub/zpub: "zpub6rFR7..."
     * - Origin-prefixed: "[73c5da0a/84'/0'/0']zpub6rFR7..."
     * - Full output descriptor: wpkh([73c5da0a/84'/0'/0']xpub6.../0/wildcard)
     * - BIP 389 multipath: `wpkh([73c5da0a/84'/0'/0']xpub6.../<0;1>/{wildcard})`
     */
    private fun createWatchOnlyDescriptors(
        extendedKey: String,
        addressType: AddressType,
        network: Network,
        masterFingerprint: String? = null,
    ): Pair<Descriptor, Descriptor> {
        val input = extendedKey.trim()

        // Check if input is already a full output descriptor (e.g., "wpkh([fp/path]xpub/0/*)")
        val descriptorPrefixes = listOf("pkh(", "wpkh(", "tr(")
        val isFullDescriptor = descriptorPrefixes.any { input.lowercase().startsWith(it) }

        if (isFullDescriptor) {
            return parseFullDescriptor(input, network)
        }

        // Parse origin info from "[fingerprint/path]xpub..." format
        val parsed = parseKeyOrigin(input)
        val bareKey = parsed.bareKey
        val fingerprint = parsed.fingerprint ?: masterFingerprint
        val originPath = parsed.derivationPath

        // Convert to xpub format for BDK compatibility
        val xpubKey = convertToXpub(bareKey)

        // Build the key expression with origin info (delegates to BitcoinUtils).
        // Uses 00000000 as fallback fingerprint when none is provided.
        // This is critical for hardware wallet compatibility:
        // - SeedSigner uses the fingerprint in BIP32 derivations to verify change outputs
        // - Without origin info, BDK uses the xpub's own fingerprint which won't match
        //   the master seed fingerprint, causing SeedSigner to reject the PSBT
        // - 00000000 triggers SeedSigner's missing-fingerprint fallback which derives
        //   keys and compares pubkeys directly (see _fill_missing_fingerprints)
        val keyWithOrigin = BitcoinUtils.buildKeyWithOrigin(xpubKey, fingerprint, originPath, addressType)
        val (externalStr, internalStr) = BitcoinUtils.buildDescriptorStrings(keyWithOrigin, addressType)

        val external = Descriptor(externalStr, network)
        val internal = Descriptor(internalStr, network)
        return Pair(external, internal)
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
        network: Network,
    ): Pair<Descriptor, Descriptor> {
        val trimmed = BitcoinUtils.stripDescriptorChecksum(descriptor)

        // BIP 389 multipath descriptor: contains <0;1> syntax for combined receive/change paths
        // e.g. "wpkh([73c5da0a/84'/0'/0']xpub6.../<0;1>/*)"
        if (BitcoinUtils.isBip389Multipath(trimmed)) {
            val multipathDescriptor = Descriptor(trimmed, network)
            if (multipathDescriptor.isMultipath()) {
                val singles = multipathDescriptor.toSingleDescriptors()
                if (singles.size == 2) {
                    val isReversed = BitcoinUtils.isBip389Reversed(trimmed)
                    val external = if (isReversed) singles[1] else singles[0]
                    val internal = if (isReversed) singles[0] else singles[1]
                    return Pair(external, internal)
                }
            }
            // Fall through if multipath parsing fails — treat as regular descriptor
            if (BuildConfig.DEBUG) {
                Log.w(
                    TAG,
                    "Multipath descriptor detected but toSingleDescriptors() failed, falling back to manual parsing",
                )
            }
        }

        // Delegate string derivation to BitcoinUtils
        val (externalStr, internalStr) = BitcoinUtils.deriveDescriptorPair(descriptor)
        return Pair(Descriptor(externalStr, network), Descriptor(internalStr, network))
    }

    /**
     * Parsed key origin info from an extended key string.
     */
    private data class KeyOriginInfo(
        val bareKey: String, // The bare xpub/zpub without origin prefix
        val fingerprint: String?, // Master key fingerprint (8 hex chars), if present
        val derivationPath: String?, // Derivation path without m/ prefix (e.g., "84'/0'/0'"), if present
    )

    /**
     * Parse key origin info from "[fingerprint/path]xpub..." format.
     * Delegates to [BitcoinUtils.parseKeyOrigin].
     */
    private fun parseKeyOrigin(input: String): KeyOriginInfo {
        val result = BitcoinUtils.parseKeyOrigin(input)
        return KeyOriginInfo(result.bareKey, result.fingerprint, result.derivationPath)
    }

    /**
     * Extract master fingerprint from key material string.
     * Delegates to [BitcoinUtils.extractFingerprint].
     */
    fun extractFingerprint(keyMaterial: String): String? =
        BitcoinUtils.extractFingerprint(keyMaterial)

    /**
     * Check if input string represents a watch-only key material.
     * Delegates to [BitcoinUtils.isWatchOnlyInput].
     */
    fun isWatchOnlyInput(input: String): Boolean =
        BitcoinUtils.isWatchOnlyInput(input)

    /**
     * Check if input string is a WIF (Wallet Import Format) private key.
     * Delegates to [BitcoinUtils.isWifPrivateKey].
     */
    fun isWifPrivateKey(input: String): Boolean =
        BitcoinUtils.isWifPrivateKey(input)

    /**
     * Check if a WIF key is compressed (K/L prefix, 52 chars).
     * Delegates to [BitcoinUtils.isWifCompressed].
     */
    private fun isWifCompressed(wif: String): Boolean =
        BitcoinUtils.isWifCompressed(wif)

    /**
     * Check if input string is a valid Bitcoin address.
     * Supports Bitcoin mainnet only.
     */
    fun isBitcoinAddress(input: String): Boolean {
        val trimmed = input.trim()
        if (trimmed.isBlank()) return false
        return try {
            Address(trimmed, Network.BITCOIN)
            true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun isAddressOwnedByActiveWallet(address: String): Boolean =
        withContext(Dispatchers.IO) {
            val trimmed = address.trim()
            if (trimmed.isBlank()) return@withContext false

            val activeWalletId = secureStorage.getActiveWalletId() ?: return@withContext false
            val currentWallet = wallet

            if (currentWallet == null && secureStorage.hasWatchAddress(activeWalletId)) {
                val watchAddress = secureStorage.getWatchAddress(activeWalletId) ?: return@withContext false
                return@withContext try {
                    val inputScript = Address(trimmed, Network.BITCOIN).scriptPubkey()
                    val watchScript = Address(watchAddress, Network.BITCOIN).scriptPubkey()
                    inputScript.toBytes().contentEquals(watchScript.toBytes())
                } catch (_: Exception) {
                    false
                }
            }

            if (currentWallet == null) return@withContext false

            return@withContext try {
                currentWallet.isMine(Address(trimmed, currentWallet.network()).scriptPubkey())
            } catch (_: Exception) {
                false
            }
        }

    /**
     * Detect the address type from a Bitcoin address string.
     * Delegates to [BitcoinUtils.detectAddressType].
     */
    fun detectAddressType(address: String): AddressType? =
        BitcoinUtils.detectAddressType(address)

    /**
     * Create a single descriptor from a WIF private key.
     * Returns a non-ranged descriptor (single address, no wildcard).
     * Single-key wallets must use Wallet.createSingle() since BDK rejects
     * identical external and internal descriptors.
     */
    private fun createDescriptorFromWif(
        wif: String,
        addressType: AddressType,
        network: Network,
    ): Descriptor {
        val descriptorStr =
            when (addressType) {
                AddressType.LEGACY -> "pkh($wif)"
                AddressType.SEGWIT -> "wpkh($wif)"
                AddressType.TAPROOT -> "tr($wif)"
            }
        return Descriptor(descriptorStr, network)
    }

    /**
     * Load the active wallet from secure storage
     */
    suspend fun loadWallet(): WalletResult<Unit> =
        withContext(Dispatchers.IO) {
            val activeWalletId =
                secureStorage.getActiveWalletId()
                    ?: return@withContext WalletResult.Error("No active wallet")

            loadWalletById(activeWalletId)
        }

    /**
     * Connect to an Electrum server and save it
     */
    suspend fun connectToElectrum(config: ElectrumConfig): WalletResult<Unit> =
        withContext(Dispatchers.IO) {
            connectionMutex.withLock {
                var newProxy: CachingElectrumProxy? = null
                var newClient: ElectrumClient? = null
                try {
                    stopNotificationCollector()
                    electrumClient = null
                    cachingProxy?.stop()
                    cachingProxy = null

                    val useTor = isTorEnabled() || config.isOnionAddress()
                    val cleanHost =
                        config.url
                            .removePrefix("tcp://").removePrefix("ssl://")
                            .removePrefix("http://").removePrefix("https://")
                            .trim().trimEnd('/')

                    val client: ElectrumClient

                    if (config.useSsl) {
                        // SSL connections: use caching proxy with Android-native TLS
                        // BDK's rustls doesn't work on Android, so we terminate SSL in Kotlin
                        val verifiedSocket = verifyCertificateProbe(cleanHost, config.port, useTor)
                        val stored = secureStorage.getServerCertFingerprint(cleanHost, config.port)
                        val bridgeTrustManager = TofuTrustManager(cleanHost, config.port, stored, isOnionHost = false)
                        newProxy =
                            CachingElectrumProxy(
                                targetHost = cleanHost,
                                targetPort = config.port,
                                proxyOwner = "bitcoin-bdk",
                                useSsl = true,
                                useTorProxy = useTor,
                                connectionTimeoutMs = if (useTor) 30000 else 15000,
                                soTimeoutMs = if (useTor) 30000 else 15000,
                                sslTrustManager = bridgeTrustManager,
                                cache = electrumCache,
                            )
                        newProxy.setPreConnectedSocket(verifiedSocket)
                        val localPort = newProxy.start()

                        if (BuildConfig.DEBUG) {
                            Log.d(
                                TAG,
                                    "Bitcoin proxy started on port $localPort -> $cleanHost:${config.port} (tor=$useTor, ssl=true)",
                            )
                        }
                        client = ElectrumClient("tcp://127.0.0.1:$localPort")
                    } else if (useTor) {
                        newProxy =
                            CachingElectrumProxy(
                                targetHost = cleanHost,
                                targetPort = config.port,
                                proxyOwner = "bitcoin-bdk",
                                useSsl = false,
                                useTorProxy = true,
                                connectionTimeoutMs = 30000,
                                soTimeoutMs = 30000,
                                cache = electrumCache,
                            )
                        val localPort = newProxy.start()

                        if (BuildConfig.DEBUG) {
                            Log.d(
                                TAG,
                                    "Bitcoin proxy started on port $localPort -> $cleanHost:${config.port} (tor=true, ssl=false)",
                            )
                        }
                        client = ElectrumClient("tcp://127.0.0.1:$localPort")
                    } else {
                        newProxy =
                            CachingElectrumProxy(
                                targetHost = cleanHost,
                                targetPort = config.port,
                                proxyOwner = "bitcoin-bdk",
                                useSsl = false,
                                useTorProxy = false,
                                connectionTimeoutMs = 15000,
                                soTimeoutMs = 15000,
                                cache = electrumCache,
                            )
                        val localPort = newProxy.start()

                        if (BuildConfig.DEBUG) {
                            Log.d(
                                TAG,
                                    "Bitcoin proxy started on port $localPort -> $cleanHost:${config.port} (tor=false, ssl=false)",
                            )
                        }
                        client = ElectrumClient("tcp://127.0.0.1:$localPort")
                    }

                    newClient = client
                    if (BuildConfig.DEBUG) Log.d(TAG, "ElectrumClient created successfully")

                    // Force the local proxy to be consumed before we publish it.
                    subscribeBlockHeaders(client)
                    currentCoroutineContext().ensureActive()

                    cachingProxy = newProxy
                    electrumClient = client

                    val savedConfig = saveElectrumServer(config)
                    secureStorage.setActiveServerId(savedConfig.id)

                    repositoryScope.launch {
                        queryServerMinFeeRate()
                    }
                    repositoryScope.launch {
                        electrumCache.pruneStaleUnconfirmed()
                    }

                    WalletResult.Success(Unit)
                } catch (e: CancellationException) {
                    if (BuildConfig.DEBUG) Log.d(TAG, "Electrum connection cancelled")
                    if (electrumClient === newClient) electrumClient = null
                    if (cachingProxy === newProxy) cachingProxy = null
                    newProxy?.stop()
                    throw e
                } catch (e: Exception) {
                    if (BuildConfig.DEBUG) {
                        Log.e(
                            TAG,
                            "Failed to connect to Electrum: ${e.javaClass.simpleName} - ${e.message}",
                            e,
                        )
                    }
                    if (electrumClient === newClient) electrumClient = null
                    if (cachingProxy === newProxy) cachingProxy = null
                    newProxy?.stop()
                    WalletResult.Error("Failed to connect to server", e)
                }
            }
        }

    /**
     * Connect to an existing saved server by ID
     */
    suspend fun connectToServer(serverId: String): WalletResult<Unit> =
        withContext(Dispatchers.IO) {
            val config =
                secureStorage.getElectrumServer(serverId)
                    ?: return@withContext WalletResult.Error("Server not found")

            connectionMutex.withLock {
                var newProxy: CachingElectrumProxy? = null
                var newClient: ElectrumClient? = null
                try {
                    stopNotificationCollector()
                    electrumClient = null
                    cachingProxy?.stop()
                    cachingProxy = null

                    val useTor = isTorEnabled() || config.isOnionAddress()
                    val cleanHost =
                        config.url
                            .removePrefix("tcp://").removePrefix("ssl://")
                            .removePrefix("http://").removePrefix("https://")
                            .trim().trimEnd('/')

                    val client: ElectrumClient

                    if (config.useSsl) {
                        val verifiedSocket = verifyCertificateProbe(cleanHost, config.port, useTor)
                        val stored = secureStorage.getServerCertFingerprint(cleanHost, config.port)
                        val bridgeTrustManager = TofuTrustManager(cleanHost, config.port, stored, isOnionHost = false)
                        newProxy =
                            CachingElectrumProxy(
                                targetHost = cleanHost,
                                targetPort = config.port,
                                proxyOwner = "bitcoin-bdk",
                                useSsl = true,
                                useTorProxy = useTor,
                                connectionTimeoutMs = if (useTor) 30000 else 15000,
                                soTimeoutMs = if (useTor) 30000 else 15000,
                                sslTrustManager = bridgeTrustManager,
                                cache = electrumCache,
                            )
                        newProxy.setPreConnectedSocket(verifiedSocket)
                        val localPort = newProxy.start()

                        if (BuildConfig.DEBUG) {
                            Log.d(
                                TAG,
                                    "Bitcoin proxy started on port $localPort -> $cleanHost:${config.port} (tor=$useTor, ssl=true)",
                            )
                        }
                        client = ElectrumClient("tcp://127.0.0.1:$localPort")
                    } else if (useTor) {
                        newProxy =
                            CachingElectrumProxy(
                                targetHost = cleanHost,
                                targetPort = config.port,
                                proxyOwner = "bitcoin-bdk",
                                useSsl = false,
                                useTorProxy = true,
                                connectionTimeoutMs = 30000,
                                soTimeoutMs = 30000,
                                cache = electrumCache,
                            )
                        val localPort = newProxy.start()

                        if (BuildConfig.DEBUG) {
                            Log.d(
                                TAG,
                                    "Bitcoin proxy started on port $localPort -> $cleanHost:${config.port} (tor=true, ssl=false)",
                            )
                        }
                        client = ElectrumClient("tcp://127.0.0.1:$localPort")
                    } else {
                        newProxy =
                            CachingElectrumProxy(
                                targetHost = cleanHost,
                                targetPort = config.port,
                                proxyOwner = "bitcoin-bdk",
                                useSsl = false,
                                useTorProxy = false,
                                connectionTimeoutMs = 15000,
                                soTimeoutMs = 15000,
                                cache = electrumCache,
                            )
                        val localPort = newProxy.start()

                        if (BuildConfig.DEBUG) {
                            Log.d(
                                TAG,
                                    "Bitcoin proxy started on port $localPort -> $cleanHost:${config.port} (tor=false, ssl=false)",
                            )
                        }
                        client = ElectrumClient("tcp://127.0.0.1:$localPort")
                    }

                    newClient = client
                    if (BuildConfig.DEBUG) Log.d(TAG, "ElectrumClient created successfully")

                    subscribeBlockHeaders(client)
                    currentCoroutineContext().ensureActive()

                    cachingProxy = newProxy
                    electrumClient = client
                    secureStorage.setActiveServerId(serverId)

                    repositoryScope.launch {
                        queryServerMinFeeRate()
                    }
                    repositoryScope.launch {
                        electrumCache.pruneStaleUnconfirmed()
                    }

                    WalletResult.Success(Unit)
                } catch (e: CancellationException) {
                    if (BuildConfig.DEBUG) Log.d(TAG, "Electrum connection cancelled")
                    if (electrumClient === newClient) electrumClient = null
                    if (cachingProxy === newProxy) cachingProxy = null
                    newProxy?.stop()
                    throw e
                } catch (e: Exception) {
                    if (BuildConfig.DEBUG) {
                        Log.e(
                            TAG,
                            "Failed to connect to Electrum: ${e.javaClass.simpleName} - ${e.message}",
                            e,
                        )
                    }
                    if (electrumClient === newClient) electrumClient = null
                    if (cachingProxy === newProxy) cachingProxy = null
                    newProxy?.stop()
                    WalletResult.Error("Failed to connect to server", e)
                }
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
    private fun isTransientElectrumSyncError(error: Throwable): Boolean {
        val details =
            generateSequence(error) { it.cause }
                .mapNotNull { throwable -> throwable.message?.trim()?.takeIf { it.isNotEmpty() } }
                .joinToString(separator = " | ")

        if (details.isBlank()) return false

        return details.contains("AllAttemptsErrored", ignoreCase = true) ||
            details.contains("timed out", ignoreCase = true) ||
            details.contains("Timeout(", ignoreCase = true) ||
            details.contains("Connection reset", ignoreCase = true) ||
            details.contains("Broken pipe", ignoreCase = true) ||
            details.contains("EOF", ignoreCase = true)
    }

    private fun cancelTransactionRefreshForSync() {
        val activeRefresh = transactionRefreshJob?.takeIf { it.isActive } ?: return
        if (BuildConfig.DEBUG) Log.d(TAG, "Cancelling background transaction refresh before sync")
        activeRefresh.cancel()
        transactionRefreshJob = null
        _walletState.value = _walletState.value.copy(isTransactionHistoryLoading = false)
    }

    suspend fun sync(force: Boolean = false): WalletResult<Unit> =
        withContext(Dispatchers.IO) {
            val activeWalletId =
                secureStorage.getActiveWalletId()
                    ?: return@withContext WalletResult.Error("No active wallet")

            // Watch address wallets have no BDK wallet — route to Electrum-only sync
            if (wallet == null && secureStorage.hasWatchAddress(activeWalletId)) {
                return@withContext syncWatchAddress(activeWalletId)
            }

            // Liquid-only wallets have no BDK wallet — sync is handled by LiquidRepository
            if (wallet == null) {
                val meta = secureStorage.getWalletMetadata(activeWalletId)
                if (meta?.derivationPath == "liquid_ct") {
                    return@withContext WalletResult.Success(Unit)
                }
            }

            val currentWallet = wallet ?: return@withContext WalletResult.Error("Wallet not initialized")
            val client = electrumClient ?: return@withContext WalletResult.Error("Not connected to Electrum server")
            cancelTransactionRefreshForSync()

            // If wallet needs full sync (first time or manually requested), do that instead.
            // force = false: if another fullSync() completed while we wait on the mutex,
            // the re-check inside fullSync() will skip the redundant scan.
            if (secureStorage.needsFullSync(activeWalletId)) {
                if (BuildConfig.DEBUG) Log.d(TAG, "Wallet needs full sync, redirecting to fullSync()")
                return@withContext fullSync(force = false)
            }

            // Mutex: skip if another sync is already running.
            // Return Error (not Success) so callers like the background sync loop
            // don't reset their failure counters on a no-op skip.
            if (!syncMutex.tryLock()) {
                if (BuildConfig.DEBUG) Log.d(TAG, "Sync already in progress, skipping")
                return@withContext WalletResult.Error("Sync skipped (already in progress)")
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
                if (!force && !newBlockDetected && scriptHashStatusCache.isNotEmpty()) {
                    val hasChanges =
                        cachingProxy
                            ?.checkForScriptHashChanges(scriptHashStatusCache)
                            ?: true // No proxy — sync to be safe
                    if (!hasChanges) {
                        secureStorage.saveLastSyncTime(activeWalletId, System.currentTimeMillis())
                        _walletState.value = _walletState.value.copy(isSyncing = false)
                        if (BuildConfig.DEBUG) {
                            Log.d(
                                TAG,
                                "Pre-check: no new block and no script hash changes, skipping sync",
                            )
                        }
                        return@withContext WalletResult.Success(Unit)
                    }
                } else if (force && BuildConfig.DEBUG) {
                    Log.d(TAG, "Forced quick sync: bypassing pre-check")
                }

                if (BuildConfig.DEBUG) Log.d(TAG, "Starting quick sync (batch=$currentBatchSize)")
                val hadPendingTransactions = _walletState.value.hasPendingBitcoinTransactions()

                val syncProgress = java.util.concurrent.atomic.AtomicLong(0)
                val syncRequest =
                    currentWallet.startSyncWithRevealedSpks()
                        .inspectSpks(
                            object : SyncScriptInspector {
                                override fun inspect(
                                    script: Script,
                                    total: ULong,
                                ) {
                                    val current = syncProgress.incrementAndGet().toULong()
                                    _walletState.value =
                                        _walletState.value.copy(
                                            syncProgress = SyncProgress(current = current, total = total),
                                        )
                                }
                            },
                        )
                        .build()
                val batchSize = currentBatchSize
                val update =
                    try {
                        withTimeoutOrNull(QUICK_SYNC_TIMEOUT_MS) {
                            client.sync(
                                request = syncRequest,
                                batchSize = batchSize,
                                fetchPrevTxouts = false,
                            )
                        }
                    } catch (e: Exception) {
                        if (!isTransientElectrumSyncError(e)) throw e

                        if (BuildConfig.DEBUG) {
                            Log.w(TAG, "Quick sync transient failure, retrying once: ${e.message}")
                        }

                        delay(1_000)
                        withTimeoutOrNull(QUICK_SYNC_TIMEOUT_MS) {
                            client.sync(
                                request = syncRequest,
                                batchSize = batchSize,
                                fetchPrevTxouts = false,
                            )
                        }
                    }

                if (update == null) {
                    // Timeout: halve batch size for next attempt (adaptive)
                    currentBatchSize = maxOf(MIN_BATCH_SIZE, currentBatchSize / 2UL)
                    secureStorage.saveSyncBatchSize(currentBatchSize.toLong())
                    _walletState.value = _walletState.value.copy(isSyncing = false, syncProgress = null)
                    if (BuildConfig.DEBUG) Log.w(TAG, "Quick sync timed out, reducing batch to $currentBatchSize")
                    return@withContext WalletResult.Error(
                        "Sync timed out - check your server connection",
                    )
                }

                // Success: reset batch size to default
                currentBatchSize = SYNC_BATCH_SIZE
                secureStorage.saveSyncBatchSize(currentBatchSize.toLong())

                // Apply update and get typed events (TxConfirmed, TxReplaced, etc.)
                val events = currentWallet.applyUpdateEvents(update)
                val hasTransactionEvents =
                    events.any { event ->
                        event is WalletEvent.TxConfirmed ||
                            event is WalletEvent.TxUnconfirmed ||
                            event is WalletEvent.TxReplaced ||
                            event is WalletEvent.TxDropped
                    }
                val shouldVerifyPendingConfirmations = hadPendingTransactions && (force || newBlockDetected)

                // Always persist — chain tip updates even when no tx events
                walletPersister?.let { currentWallet.persist(it) }
                var postSyncWallet = currentWallet
                if (shouldVerifyPendingConfirmations) {
                    if (reloadWalletFromDatabase()) {
                        postSyncWallet = wallet ?: currentWallet
                    } else {
                        wallet = currentWallet
                    }
                }

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
                    if (shouldVerifyPendingConfirmations && !hasTransactionEvents) {
                        if (BuildConfig.DEBUG) {
                            Log.d(TAG, "Pending txs + new block produced no tx events; rebuilding wallet state")
                        }
                        updateWalletState()
                    } else {
                        updateWalletStateIncremental(events)
                    }
                    refreshScriptHashCache(postSyncWallet)
                } else {
                    if (shouldVerifyPendingConfirmations) {
                        if (BuildConfig.DEBUG) {
                            Log.d(TAG, "Pending txs + new block produced no events; rebuilding wallet state")
                        }
                        updateWalletState()
                        refreshScriptHashCache(postSyncWallet)
                    } else if (BuildConfig.DEBUG) {
                        Log.d(TAG, "Quick sync: no changes detected, skipping state rebuild")
                    }
                }

                secureStorage.saveLastSyncTime(activeWalletId, System.currentTimeMillis())
                if (BuildConfig.DEBUG) Log.d(TAG, "Quick sync completed successfully")
                WalletResult.Success(Unit)
            } catch (e: Exception) {
                _walletState.value =
                    _walletState.value.copy(
                        isSyncing = false,
                        syncProgress = null,
                        error = "Sync failed - check your connection",
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
    suspend fun fullSync(showProgress: Boolean = true, force: Boolean = true): WalletResult<Unit> =
        withContext(Dispatchers.IO) {
            val currentWallet = wallet ?: return@withContext WalletResult.Error("Wallet not initialized")
            val client = electrumClient ?: return@withContext WalletResult.Error("Not connected to Electrum server")
            val activeWalletId =
                secureStorage.getActiveWalletId()
                    ?: return@withContext WalletResult.Error("No active wallet")
            cancelTransactionRefreshForSync()

            // Mutex: wait for any running sync to finish before starting full scan
            syncMutex.withLock {
                if (!force && !secureStorage.needsFullSync(activeWalletId)) {
                    if (BuildConfig.DEBUG) Log.d(TAG, "Full sync already completed by prior call, skipping")
                    _walletState.value = _walletState.value.copy(isSyncing = false, isFullSyncing = false, syncProgress = null)
                    return@withContext WalletResult.Success(Unit)
                }

                try {
                    abortingActiveFullSync = false
                    val fullScanBatchSize = getActiveFullScanBatchSize()
                    _walletState.value = _walletState.value.copy(isSyncing = true, isFullSyncing = showProgress, error = null)
                    if (BuildConfig.DEBUG) Log.d(TAG, "Starting full sync (address discovery, batch=$fullScanBatchSize)")

                    val fullScanProgress = java.util.concurrent.atomic.AtomicLong(0)
                    fun buildFullScanRequest() =
                        currentWallet.startFullScan()
                            .inspectSpksForAllKeychains(
                                object : FullScanScriptInspector {
                                    override fun inspect(
                                        keychain: KeychainKind,
                                        index: UInt,
                                        script: Script,
                                    ) {
                                        val current = fullScanProgress.incrementAndGet().toULong()
                                        val keychainName = if (keychain == KeychainKind.EXTERNAL) "receive" else "change"
                                        _walletState.value =
                                            _walletState.value.copy(
                                                syncProgress =
                                                    SyncProgress(
                                                        current = current,
                                                        total = 0UL,
                                                        keychain = "$keychainName #$index",
                                                        status = "Scanned $current addresses...",
                                                    ),
                                            )
                                    }
                                },
                            )
                            .build()

                    val proxy = cachingProxy
                    // Full scans can legitimately sit on one large batch for several
                    // minutes. Keep the bridge socket in blocking mode here so the
                    // proxy does not mistake a slow server response for a disconnect.
                    proxy?.setBridgeReadTimeout(0)
                    val update =
                        try {
                            _walletState.value =
                                _walletState.value.copy(
                                    syncProgress = SyncProgress(status = "Preparing sync..."),
                                )

                            val allScriptHashes = getAllRevealedScriptHashes(currentWallet)
                            if (proxy != null && allScriptHashes.isNotEmpty()) {
                                val currentStatuses = proxy.subscribeScriptHashes(allScriptHashes)
                                proxy.setValidStatuses(currentStatuses)

                                if (BuildConfig.DEBUG) {
                                    val persistedStatuses = electrumCache.loadScriptHashStatuses()
                                    val unchangedCount =
                                        currentStatuses.count { (scriptHash, status) ->
                                            status != null && persistedStatuses[scriptHash] == status
                                        }
                                    val changedCount = currentStatuses.size - unchangedCount
                                    Log.d(
                                        TAG,
                                        "Prepared full sync history cache for ${currentStatuses.size} revealed scripts " +
                                            "($unchangedCount unchanged, $changedCount changed)",
                                    )
                                }
                            }

                            // No timeout on full scan - large wallets with extensive tx history
                            // can legitimately take minutes. TCP-level timeouts handle dead connections.
                            // Do not fetch prev txouts during full scan (same as Sparrow): only txs
                            // from scripthash history are needed; chasing input prevouts multiplies
                            // transaction.get traffic with little benefit for balance/UTXO correctness.
                            val needsPrevTxouts = false
                            val walletGapLimit =
                                (secureStorage.getWalletMetadata(activeWalletId)?.gapLimit
                                    ?: StoredWallet.DEFAULT_GAP_LIMIT).toULong()
                            try {
                                client.fullScan(
                                    request = buildFullScanRequest(),
                                    stopGap = walletGapLimit,
                                    batchSize = fullScanBatchSize,
                                    fetchPrevTxouts = needsPrevTxouts,
                                )
                            } catch (e: Exception) {
                                if (abortingActiveFullSync) {
                                    throw CancellationException("Full sync cancelled")
                                }
                                if (!isTransientElectrumSyncError(e)) throw e
                                if (BuildConfig.DEBUG) {
                                    Log.w(TAG, "Full scan transient failure, retrying once: ${e.message}")
                                }
                                delay(2_000)
                                if (abortingActiveFullSync) {
                                    throw CancellationException("Full sync cancelled")
                                }
                                client.fullScan(
                                    request = buildFullScanRequest(),
                                    stopGap = walletGapLimit,
                                    batchSize = fullScanBatchSize,
                                    fetchPrevTxouts = needsPrevTxouts,
                                )
                            }
                        } finally {
                            proxy?.clearValidStatuses()
                            proxy?.setBridgeReadTimeout(null)
                        }

                    // Success: reset batch size
                    currentBatchSize = SYNC_BATCH_SIZE
                    secureStorage.saveSyncBatchSize(currentBatchSize.toLong())

                    // Apply update with events
                    _walletState.value =
                        _walletState.value.copy(
                            syncProgress = SyncProgress(status = "Applying updates..."),
                        )
                    val events = currentWallet.applyUpdateEvents(update)
                    if (BuildConfig.DEBUG) Log.d(TAG, "Full scan events: ${events.size} changes detected")

                    // Persist changes to database
                    _walletState.value =
                        _walletState.value.copy(
                            syncProgress = SyncProgress(status = "Saving to database..."),
                        )
                    walletPersister?.let { currentWallet.persist(it) }
                    var postSyncWallet = currentWallet

                    // BDK's in-memory UTXO/keychain index can be stale after
                    // applyUpdateEvents when the full scan revealed new addresses.
                    // Reloading from the persisted database forces a full rebuild
                    // so that balance() matches the actual transaction set.
                    if (events.isNotEmpty()) {
                        refreshWalletTransactionCache(currentWallet)
                        val preReloadBalance = try { amountToSats(currentWallet.balance().total) } catch (_: Exception) { null }
                        if (reloadWalletFromDatabase()) {
                            postSyncWallet = wallet ?: currentWallet
                            refreshWalletTransactionCache(postSyncWallet)
                            if (BuildConfig.DEBUG) {
                                val postBalance = try { amountToSats(postSyncWallet.balance().total) } catch (_: Exception) { null }
                                if (preReloadBalance != postBalance) {
                                    Log.w(TAG, "Full scan reload corrected balance: $preReloadBalance -> $postBalance")
                                }
                            }
                        } else {
                            wallet = currentWallet
                        }
                    }

                    // Mark full sync as complete - future syncs will be quick
                    secureStorage.setNeedsFullSync(activeWalletId, false)

                    // Save the full sync timestamp
                    val now = System.currentTimeMillis()
                    secureStorage.saveLastFullSyncTime(activeWalletId, now)
                    secureStorage.saveLastSyncTime(activeWalletId, now)

                    _walletState.value =
                        _walletState.value.copy(
                            syncProgress = SyncProgress(status = "Updating wallet..."),
                        )
                    updateWalletStateLightweight()

                    // Reclaim revealed-but-unused addresses that have no label
                    try {
                        reclaimUnusedAddresses()
                    } catch (e: Exception) {
                        if (BuildConfig.DEBUG) Log.w(TAG, "Address reclaim failed: ${e.message}")
                    }

                    // Refresh script hash cache AFTER updateWalletState so that
                    // the current receive address is included in the cache.
                    _walletState.value =
                        _walletState.value.copy(
                            syncProgress = SyncProgress(status = "Refreshing address cache..."),
                        )
                    refreshScriptHashCache(postSyncWallet)
                    _walletState.value = _walletState.value.copy(isSyncing = false, isFullSyncing = false, syncProgress = null)
                    scheduleDetailedTransactionRefresh(activeWalletId, postSyncWallet)

                    if (BuildConfig.DEBUG) Log.d(TAG, "Full sync completed successfully")
                    abortingActiveFullSync = false
                    WalletResult.Success(Unit)
                } catch (e: CancellationException) {
                    abortingActiveFullSync = false
                    _walletState.value =
                        _walletState.value.copy(
                            isSyncing = false,
                            isFullSyncing = false,
                            syncProgress = null,
                            error = null,
                        )
                    if (BuildConfig.DEBUG) Log.d(TAG, "Full sync cancelled")
                    throw e
                } catch (e: Exception) {
                    val cancelledByAbort = abortingActiveFullSync
                    abortingActiveFullSync = false
                    if (cancelledByAbort) {
                        _walletState.value =
                            _walletState.value.copy(
                                isSyncing = false,
                                isFullSyncing = false,
                                syncProgress = null,
                                error = null,
                            )
                        if (BuildConfig.DEBUG) Log.d(TAG, "Full sync aborted by user")
                        throw CancellationException("Full sync cancelled").apply { initCause(e) }
                    }
                    _walletState.value =
                        _walletState.value.copy(
                            isSyncing = false,
                            isFullSyncing = false,
                            syncProgress = null,
                            error = "Full sync failed - check your connection",
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
    /**
     * Delegates to [BitcoinUtils.computeScriptHash].
     */
    private fun computeScriptHash(script: Script): String {
        @OptIn(ExperimentalUnsignedTypes::class)
        val scriptBytes = script.toBytes().toUByteArray().toByteArray()
        return BitcoinUtils.computeScriptHash(scriptBytes)
    }

    /**
     * Compute the Electrum script hash from a Bitcoin address string.
     */
    fun computeScriptHashForAddress(address: String): String? {
        return try {
            val addr = Address(address, Network.BITCOIN)
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
        allWallets: List<StoredWallet>,
    ): WalletState {
        val address =
            secureStorage.getWatchAddress(walletId) ?: return WalletState(
                isInitialized = allWallets.isNotEmpty(),
                wallets = allWallets,
                activeWallet = activeWallet,
            )

        val scriptHash = computeScriptHashForAddress(address)
        val proxy = cachingProxy

        if (scriptHash == null || proxy == null) {
            return WalletState(
                isInitialized = true,
                wallets = allWallets,
                activeWallet = activeWallet,
                currentAddress = address,
                currentAddressInfo = buildCurrentReceiveAddressInfo(walletId, address),
            )
        }

        // Query balance from Electrum
        val balancePair =
            try {
                proxy.getScriptHashBalance(scriptHash)
            } catch (_: Exception) {
                null
            }
        val confirmed = balancePair?.first?.toULong() ?: 0UL
        val unconfirmed = balancePair?.second ?: 0L
        val transactionSwapDetails = secureStorage.getAllTransactionSwapDetails(walletId)
        val liquidSwapDetails = secureStorage.getAllLiquidSwapDetails(walletId)

        // Query history from Electrum and fetch amounts/timestamps for each tx
        val history =
            try {
                proxy.getScriptHashHistory(scriptHash)
            } catch (_: Exception) {
                null
            }
        val transactions =
            history?.map { (txid, height) ->
                // Fetch the net amount, timestamp, and counterparty from verbose tx data
                val txInfo =
                    try {
                        proxy.getAddressTxInfo(txid, address)
                    } catch (_: Exception) {
                        null
                    }
                val isReceive = (txInfo?.netAmountSats ?: 0L) > 0L
                TransactionDetails(
                    txid = txid,
                    amountSats = txInfo?.netAmountSats ?: 0L,
                    fee = txInfo?.feeSats?.toULong(),
                    confirmationTime =
                        if (height > 0) {
                            ConfirmationTime(
                                height = height.toUInt(),
                                timestamp = (txInfo?.timestamp ?: 0L).toULong(),
                            )
                        } else {
                            null
                        },
                    isConfirmed = height > 0,
                    timestamp = txInfo?.timestamp,
                    // For receives: show the watched address ("received at")
                    // For sends: show the recipient address
                    address = if (isReceive) address else (txInfo?.counterpartyAddress ?: address),
                    swapDetails = transactionSwapDetails[txid],
                ).let { details ->
                    details.copy(
                        swapDetails =
                            details.swapDetails
                                ?: inferBitcoinChainSwapSettlementDetails(details, liquidSwapDetails),
                    )
                }
            }?.sortedWith(
                compareBy<TransactionDetails> { it.isConfirmed } // unconfirmed first
                    .thenByDescending { it.confirmationTime?.height ?: 0U },
            ) // then by height descending
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
            currentAddressInfo = buildCurrentReceiveAddressInfo(walletId, address),
        )
    }

    private fun buildCurrentReceiveAddressInfo(
        walletId: String?,
        address: String?,
        fallback: ReceiveAddressInfo? = null,
    ): ReceiveAddressInfo? {
        val resolvedAddress = address ?: return fallback
        val label = walletId?.let { secureStorage.getAddressLabel(it, resolvedAddress) }
        return ReceiveAddressInfo(
            address = resolvedAddress,
            label = label,
            isUsed = fallback?.isUsed ?: false,
        )
    }

    /**
     * Sync a watch address wallet by querying Electrum for balance/history updates.
     * Returns true if state was updated.
     */
    suspend fun syncWatchAddress(walletId: String): WalletResult<Unit> =
        withContext(Dispatchers.IO) {
            try {
                secureStorage.getWatchAddress(walletId)
                    ?: return@withContext WalletResult.Error("No watch address found")

                _walletState.value = _walletState.value.copy(isSyncing = true)

                // Just update the wallet state — it queries Electrum inside getWatchAddressState
                updateWalletState()

                val now = System.currentTimeMillis()
                _walletState.value =
                    _walletState.value.copy(
                        isSyncing = false,
                        lastSyncTimestamp = now,
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
        try {
            if (subscribedScriptHashes.isNotEmpty()) {
                val proxy = cachingProxy ?: return
                val statuses = proxy.subscribeScriptHashes(subscribedScriptHashes.toList())
                if (statuses.isNotEmpty()) {
                    scriptHashStatusCache.clear()
                    scriptHashStatusCache.putAll(statuses)
                    if (BuildConfig.DEBUG) {
                        Log.d(TAG, "Refreshed full script hash cache with ${statuses.size} entries")
                    }
                }
                return
            }

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
                val start =
                    if (lastExternal >= SCRIPT_HASH_SAMPLE_SIZE.toUInt()) {
                        lastExternal - SCRIPT_HASH_SAMPLE_SIZE.toUInt() + 1u
                    } else {
                        0u
                    }
                for (i in start..lastExternal) {
                    val addr = currentWallet.peekAddress(KeychainKind.EXTERNAL, i)
                    hashSet.add(computeScriptHash(addr.address.scriptPubkey()))
                }
            }
            // Sample from internal (change) keychain
            val lastInternal = currentWallet.derivationIndex(KeychainKind.INTERNAL)
            if (lastInternal != null) {
                val start =
                    if (lastInternal >= (SCRIPT_HASH_SAMPLE_SIZE / 2).toUInt()) {
                        lastInternal - (SCRIPT_HASH_SAMPLE_SIZE / 2).toUInt() + 1u
                    } else {
                        0u
                    }
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
        invalidatePreparedSendCache()
    }

    fun invalidatePreparedSendCache() {
        preparedBitcoinSendCache = null
    }

    private fun currentBitcoinSendPreparationState(): BitcoinSendPreparationState {
        val currentState = _walletState.value
        return BitcoinSendPreparationState(
            walletId = secureStorage.getActiveWalletId(),
            balanceSats = currentState.balanceSats,
            pendingIncomingSats = currentState.pendingIncomingSats,
            pendingOutgoingSats = currentState.pendingOutgoingSats,
            transactionCount = currentState.transactions.size,
            spendUnconfirmed = secureStorage.getSpendUnconfirmed(),
        )
    }

    private suspend fun getPreparedBitcoinSendCacheEntry(
        key: BitcoinSendPreparationCacheKey,
        requiresPsbtDetails: Boolean,
    ): PreparedBitcoinSendCacheEntry? =
        preparedBitcoinSendCacheMutex.withLock {
            preparedBitcoinSendCache?.takeIf { cached ->
                cached.key == key && (!requiresPsbtDetails || cached.psbtDetails != null)
            }
        }

    private suspend fun cachePreparedBitcoinSendEntry(entry: PreparedBitcoinSendCacheEntry) {
        preparedBitcoinSendCacheMutex.withLock {
            preparedBitcoinSendCache = entry
        }
    }

    private fun buildManualSelectionApplier(
        currentWallet: Wallet,
        selectedUtxos: List<UtxoInfo>?,
    ): (TxBuilder) -> TxBuilder {
        if (selectedUtxos.isNullOrEmpty()) {
            return { it }
        }

        val selectedOutpoints = selectedUtxos.map { it.outpoint }.toSet()
        val selectedWalletUtxos =
            currentWallet.listUnspent().filter { utxo ->
                val outpoint = utxo.outpoint
                selectedOutpoints.contains("${outpoint.txid}:${outpoint.vout}")
            }

        return { builder ->
            var configuredBuilder = builder
            selectedWalletUtxos.forEach { utxo ->
                configuredBuilder = configuredBuilder.addUtxo(utxo.outpoint)
            }
            configuredBuilder.manuallySelectedOnly()
        }
    }

    private fun isSilentPaymentAddress(address: String): Boolean =
        SilentPayment.isSilentPaymentAddress(address)

    private fun scriptFromBytes(bytes: ByteArray): Script =
        Script(bytes)

    private fun buildSendRecipientScripts(
        recipients: List<Recipient>,
        currentWallet: Wallet,
        silentOutputKeys: List<SilentPayment.OutputKey>? = null,
    ): List<SendRecipientScript> {
        val silentKeysByIndex = silentOutputKeys?.associateBy { it.recipientIndex }.orEmpty()
        return recipients.mapIndexed { index, recipient ->
            if (isSilentPaymentAddress(recipient.address)) {
                val outputKey = silentKeysByIndex[index]?.xOnlyPublicKey
                val scriptBytes =
                    if (outputKey != null) {
                        SilentPayment.taprootScriptPubKey(outputKey)
                    } else {
                        SilentPayment.placeholderScriptPubKey()
                    }
                SendRecipientScript(
                    recipient = recipient,
                    script = scriptFromBytes(scriptBytes),
                    isSilentPayment = true,
                )
            } else {
                SendRecipientScript(
                    recipient = recipient,
                    script = Address(recipient.address, currentWallet.network()).scriptPubkey(),
                    isSilentPayment = false,
                )
            }
        }
    }

    private fun addRecipientScripts(
        builder: TxBuilder,
        scripts: List<SendRecipientScript>,
    ): TxBuilder {
        var configuredBuilder = builder
        scripts.forEach { recipientScript ->
            configuredBuilder =
                configuredBuilder.addRecipient(
                    recipientScript.script,
                    Amount.fromSat(recipientScript.recipient.amountSats),
                )
        }
        return configuredBuilder
    }

    private fun hasSilentPaymentRecipient(recipients: List<Recipient>): Boolean =
        recipients.any { isSilentPaymentAddress(it.address) }

    private fun rebuildWithSilentPaymentOutputs(
        placeholderPsbt: Psbt,
        currentWallet: Wallet,
        storedWallet: StoredWallet?,
        recipients: List<Recipient>,
        feeSats: ULong,
    ): Psbt {
        val finalTx = placeholderPsbt.extractTx()
        val inputOutpoints = finalTx.input().map { it.previousOutput }
        val inputKeys =
            deriveSilentPaymentInputKeys(
                currentWallet = currentWallet,
                storedWallet = storedWallet,
                inputOutpoints = inputOutpoints.map { "${it.txid}:${it.vout}" },
            )
        val silentRecipientIndexes =
            recipients.mapIndexedNotNull { index, recipient ->
                index.takeIf { isSilentPaymentAddress(recipient.address) }
            }
        val silentOutputKeys =
            SilentPayment.createOutputKeys(
                inputKeys = inputKeys,
                recipients = silentRecipientIndexes.map { recipients[it].address },
            ).map { outputKey ->
                outputKey.copy(recipientIndex = silentRecipientIndexes[outputKey.recipientIndex])
            }
        val recipientScripts = buildSendRecipientScripts(recipients, currentWallet, silentOutputKeys)

        var builder = TxBuilder().feeAbsolute(Amount.fromSat(feeSats))
        inputOutpoints.forEach { outpoint ->
            builder = builder.addUtxo(outpoint)
        }
        builder = addRecipientScripts(builder.manuallySelectedOnly(), recipientScripts)
        return builder.finish(currentWallet)
    }

    private fun buildSilentPaymentRedirectScript(
        placeholderPsbt: Psbt,
        currentWallet: Wallet,
        storedWallet: StoredWallet?,
        destinationAddress: String,
    ): Script {
        val finalTx = placeholderPsbt.extractTx()
        val inputOutpoints = finalTx.input().map { it.previousOutput }
        val inputKeys =
            deriveSilentPaymentInputKeys(
                currentWallet = currentWallet,
                storedWallet = storedWallet,
                inputOutpoints = inputOutpoints.map { "${it.txid}:${it.vout}" },
            )
        val outputKey =
            SilentPayment.createOutputKeys(
                inputKeys = inputKeys,
                recipients = listOf(destinationAddress),
            ).single()
        return scriptFromBytes(SilentPayment.taprootScriptPubKey(outputKey.xOnlyPublicKey))
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    private fun deriveSilentPaymentInputKeys(
        currentWallet: Wallet,
        storedWallet: StoredWallet?,
        inputOutpoints: List<String>,
    ): List<SilentPayment.InputKey> {
        val walletOutputsByOutpoint =
            currentWallet.listUnspent().associateBy { localOutput ->
                "${localOutput.outpoint.txid}:${localOutput.outpoint.vout}"
            }
        val keys =
            inputOutpoints.mapNotNull { outpoint ->
                val localOutput = walletOutputsByOutpoint[outpoint] ?: return@mapNotNull null
                val scriptBytes = localOutput.txout.scriptPubkey.toBytes().toUByteArray().toByteArray()
                val isTaproot = isP2trScript(scriptBytes)
                if (!isEligibleSilentPaymentInputScript(scriptBytes)) {
                    return@mapNotNull null
                }
                val privateKey = deriveWalletInputPrivateKey(storedWallet, localOutput.keychain, localOutput.derivationIndex, isTaproot)
                SilentPayment.InputKey(
                    outpoint = outpoint,
                    privateKey = privateKey,
                    isTaproot = isTaproot,
                )
            }
        require(keys.isNotEmpty()) { "Silent payments require an eligible input" }
        return keys
    }

    private fun deriveWalletInputPrivateKey(
        storedWallet: StoredWallet?,
        keychain: KeychainKind,
        derivationIndex: UInt,
        isTaproot: Boolean,
    ): ByteArray {
        val activeWalletId = secureStorage.getActiveWalletId()
            ?: throw IllegalStateException("Wallet not initialized")
        secureStorage.getPrivateKey(activeWalletId)?.let { wif ->
            val key = SilentPayment.privateKeyFromWif(wif)
            return if (isTaproot) SilentPayment.deriveTaprootOutputPrivateKey(key) else key
        }
        if (storedWallet?.isWatchOnly == true) {
            throw IllegalStateException("Silent payments require a hot wallet")
        }
        val mnemonic = secureStorage.getMnemonic(activeWalletId)
            ?: throw IllegalStateException("Silent payments require a seed wallet")
        val passphrase = secureStorage.getPassphrase(activeWalletId)
        val branch =
            when (keychain) {
                KeychainKind.EXTERNAL -> 0L
                KeychainKind.INTERNAL -> 1L
            }
        val index = derivationIndex.toLong()
        val path =
            when (storedWallet?.seedFormat ?: SeedFormat.BIP39) {
                SeedFormat.BIP39 ->
                    when (storedWallet?.addressType ?: AddressType.SEGWIT) {
                        AddressType.LEGACY -> "m/44'/0'/0'/$branch/$index"
                        AddressType.SEGWIT -> "m/84'/0'/0'/$branch/$index"
                        AddressType.TAPROOT -> "m/86'/0'/0'/$branch/$index"
                    }
                SeedFormat.ELECTRUM_STANDARD -> "m/$branch/$index"
                SeedFormat.ELECTRUM_SEGWIT -> "m/0'/$branch/$index"
            }
        val seed =
            when (storedWallet?.seedFormat ?: SeedFormat.BIP39) {
                SeedFormat.BIP39 -> ElectrumSeedUtil.bip39MnemonicToSeed(mnemonic, passphrase)
                SeedFormat.ELECTRUM_STANDARD,
                SeedFormat.ELECTRUM_SEGWIT,
                -> ElectrumSeedUtil.mnemonicToSeed(mnemonic, passphrase)
            }
        val key = ElectrumSeedUtil.derivePrivateKey(seed, path)
        return if (isTaproot) SilentPayment.deriveTaprootOutputPrivateKey(key) else key
    }

    private fun isEligibleSilentPaymentInputScript(scriptBytes: ByteArray): Boolean =
        isP2pkhScript(scriptBytes) || isP2wpkhScript(scriptBytes) || isP2trScript(scriptBytes)

    private fun isP2pkhScript(scriptBytes: ByteArray): Boolean =
        scriptBytes.size == 25 &&
            scriptBytes[0] == 0x76.toByte() &&
            scriptBytes[1] == 0xA9.toByte() &&
            scriptBytes[2] == 0x14.toByte() &&
            scriptBytes[23] == 0x88.toByte() &&
            scriptBytes[24] == 0xAC.toByte()

    private fun isP2wpkhScript(scriptBytes: ByteArray): Boolean =
        scriptBytes.size == 22 &&
            scriptBytes[0] == 0x00.toByte() &&
            scriptBytes[1] == 0x14.toByte()

    private fun isP2trScript(scriptBytes: ByteArray): Boolean =
        scriptBytes.size == 34 &&
            scriptBytes[0] == 0x51.toByte() &&
            scriptBytes[1] == 0x20.toByte()

    private fun buildPreparedPsbt(
        currentWallet: Wallet,
        feeRateSatPerVb: Double,
        precomputedFeeSats: ULong?,
        configureBuilder: (TxBuilder) -> TxBuilder,
    ): PreparedPsbtBuild {
        val feeRate = feeRateFromSatPerVb(feeRateSatPerVb)

        fun buildPass1Psbt(): Pair<Psbt, Transaction> {
            val pass1Psbt =
                configureBuilder(
                    TxBuilder().applyConfirmedOnlyFilter().feeRate(feeRate),
                ).finish(currentWallet)
            return pass1Psbt to pass1Psbt.extractTx()
        }

        var referencePsbt: Psbt
        var referenceUnsignedTx: Transaction
        var measuredVBytes: Double?

        val psbt =
            if (precomputedFeeSats != null) {
                try {
                    val absolutePsbt =
                        configureBuilder(
                            TxBuilder().applyConfirmedOnlyFilter().feeAbsolute(Amount.fromSat(precomputedFeeSats)),
                        ).finish(currentWallet)
                    val absoluteUnsignedTx = absolutePsbt.extractTx()
                    referencePsbt = absolutePsbt
                    referenceUnsignedTx = absoluteUnsignedTx
                    measuredVBytes = estimateSignedVBytes(absolutePsbt, currentWallet, absoluteUnsignedTx)
                    absolutePsbt
                } catch (_: Exception) {
                    val (pass1Psbt, unsignedTx) = buildPass1Psbt()
                    referencePsbt = pass1Psbt
                    referenceUnsignedTx = unsignedTx
                    val exactFeeResult = computeExactFee(pass1Psbt, currentWallet, unsignedTx, feeRateSatPerVb)
                    measuredVBytes = exactFeeResult?.vsize
                    if (exactFeeResult != null) {
                        try {
                            configureBuilder(
                                TxBuilder().applyConfirmedOnlyFilter().feeAbsolute(
                                    Amount.fromSat(exactFeeResult.feeSats),
                                ),
                            ).finish(currentWallet)
                        } catch (_: Exception) {
                            pass1Psbt
                        }
                    } else {
                        pass1Psbt
                    }
                }
            } else {
                val (pass1Psbt, unsignedTx) = buildPass1Psbt()
                referencePsbt = pass1Psbt
                referenceUnsignedTx = unsignedTx
                val exactFeeResult = computeExactFee(pass1Psbt, currentWallet, unsignedTx, feeRateSatPerVb)
                measuredVBytes = exactFeeResult?.vsize
                if (exactFeeResult != null) {
                    try {
                        configureBuilder(
                            TxBuilder().applyConfirmedOnlyFilter().feeAbsolute(
                                Amount.fromSat(exactFeeResult.feeSats),
                            ),
                        ).finish(currentWallet)
                    } catch (_: Exception) {
                        pass1Psbt
                    }
                } else {
                    pass1Psbt
                }
            }

        val feeSats =
            try {
                psbt.fee()
            } catch (_: Exception) {
                0UL
            }
        val referenceUnsigned = referenceUnsignedTx
        val finalTx = if (psbt !== referencePsbt) psbt.extractTx() else referenceUnsigned
        val txVBytes =
            measuredVBytes
                ?: estimateSignedVBytes(referencePsbt, currentWallet, referenceUnsigned)

        return PreparedPsbtBuild(
            psbt = psbt,
            finalTx = finalTx,
            feeSats = feeSats,
            txVBytes = txVBytes,
        )
    }

    private fun summarizeSingleRecipientOutputs(
        tx: Transaction,
        recipientScript: Script,
        currentWallet: Wallet,
        fallbackRecipientAmount: ULong,
    ): SingleRecipientOutputSummary {
        var recipientAmount = fallbackRecipientAmount
        var changeAmount: ULong? = null
        var changeAddress: String? = null
        var hasChange = false

        for (output in tx.output()) {
            if (output.scriptPubkey.toBytes().contentEquals(recipientScript.toBytes())) {
                recipientAmount = output.value.toSat()
            } else {
                changeAmount = output.value.toSat()
                changeAddress =
                    try {
                        Address.fromScript(output.scriptPubkey, currentWallet.network()).toString()
                    } catch (_: Exception) {
                        null
                    }
                hasChange = true
            }
        }

        return SingleRecipientOutputSummary(
            recipientAmountSats = recipientAmount,
            changeAmountSats = changeAmount,
            changeAddress = changeAddress,
            hasChange = hasChange,
        )
    }

    private fun summarizeMultiRecipientOutputs(
        tx: Transaction,
        sendRecipientScripts: List<SendRecipientScript>,
        currentWallet: Wallet,
    ): MultiRecipientOutputSummary {
        val recipientScripts =
            sendRecipientScripts.map {
                it.script.toBytes()
            }.toSet()
        var totalRecipientAmount = 0UL
        var changeAmount: ULong? = null
        var changeAddress: String? = null
        var hasChange = false

        for (output in tx.output()) {
            val scriptBytes = output.scriptPubkey.toBytes()
            if (recipientScripts.any { it.contentEquals(scriptBytes) }) {
                totalRecipientAmount += output.value.toSat()
            } else {
                changeAmount = output.value.toSat()
                changeAddress =
                    try {
                        Address.fromScript(output.scriptPubkey, currentWallet.network()).toString()
                    } catch (_: Exception) {
                        null
                    }
                hasChange = true
            }
        }

        return MultiRecipientOutputSummary(
            totalRecipientAmountSats = totalRecipientAmount,
            changeAmountSats = changeAmount,
            changeAddress = changeAddress,
            hasChange = hasChange,
        )
    }

    private fun buildSingleRecipientPsbtDetails(
        psbt: Psbt,
        feeSats: ULong,
        recipientAddress: String,
        summary: SingleRecipientOutputSummary,
    ): PsbtDetails {
        val psbtBase64 = psbt.serialize()
        val signerExportPsbtBase64 = PsbtExportOptimizer.trimForSignerExport(psbtBase64)
        val totalInputSats = summary.recipientAmountSats + (summary.changeAmountSats ?: 0UL) + feeSats
        return PsbtDetails(
            psbtBase64 = psbtBase64,
            signerExportPsbtBase64 = signerExportPsbtBase64,
            feeSats = feeSats,
            recipientAddress = recipientAddress,
            recipientAmountSats = summary.recipientAmountSats,
            changeAmountSats = summary.changeAmountSats,
            totalInputSats = totalInputSats,
        )
    }

    private fun buildMultiRecipientPsbtDetails(
        psbt: Psbt,
        feeSats: ULong,
        recipients: List<Recipient>,
        summary: MultiRecipientOutputSummary,
    ): PsbtDetails {
        val psbtBase64 = psbt.serialize()
        val signerExportPsbtBase64 = PsbtExportOptimizer.trimForSignerExport(psbtBase64)
        val totalInputSats = summary.totalRecipientAmountSats + (summary.changeAmountSats ?: 0UL) + feeSats
        return PsbtDetails(
            psbtBase64 = psbtBase64,
            signerExportPsbtBase64 = signerExportPsbtBase64,
            feeSats = feeSats,
            recipientAddress = recipients.joinToString(", ") { it.address.take(12) + "..." },
            recipientAmountSats = summary.totalRecipientAmountSats,
            changeAmountSats = summary.changeAmountSats,
            totalInputSats = totalInputSats,
        )
    }

    private fun buildGenericPsbtDetails(
        psbt: Psbt,
        displayLabel: String,
    ): PsbtDetails {
        val psbtBase64 = psbt.serialize()
        val feeSats = try { psbt.fee() } catch (_: Exception) { 0UL }
        val totalOutputSats =
            try {
                psbt.extractTx().output().sumOf { it.value.toSat().toLong() }.toULong()
            } catch (_: Exception) {
                0UL
            }
        return PsbtDetails(
            psbtBase64 = psbtBase64,
            signerExportPsbtBase64 = PsbtExportOptimizer.trimForSignerExport(psbtBase64),
            feeSats = feeSats,
            recipientAddress = displayLabel,
            recipientAmountSats = totalOutputSats,
            changeAmountSats = null,
            totalInputSats = totalOutputSats + feeSats,
        )
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
        FAILED,
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
    suspend fun startRealTimeSubscriptions(): SubscriptionResult =
        withContext(Dispatchers.IO) {
            val currentWallet = wallet ?: return@withContext SubscriptionResult.FAILED
            val proxy = cachingProxy ?: return@withContext SubscriptionResult.FAILED
            val activeWalletId =
                secureStorage.getActiveWalletId()
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
            val needsSync =
                if (persistedStatuses.isEmpty()) {
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
                result =
                    if (syncResult is WalletResult.Success) {
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
        notificationCollectorJob =
            repositoryScope.launch {
                if (BuildConfig.DEBUG) Log.d(TAG, "Notification collector started")

                // Debounce buffer: accumulate notifications for 1 second of quiet
                val pendingNotifications = mutableListOf<ElectrumNotification>()
                var lastNotificationTime: Long

                proxy.notifications.collect { notification ->
                    if (!isActive) return@collect

                    if (notification is ElectrumNotification.ConnectionLost) {
                        SecureLog.w(TAG, "Subscription socket reported connection lost")
                        _connectionEvents.tryEmit(ConnectionEvent.ConnectionLost)
                        return@collect
                    }

                    if (notification is ElectrumNotification.NewBlockHeader) {
                        lastKnownBlockHeight = notification.height.toULong()
                        val current = _walletState.value
                        if (current.blockHeight != notification.height.toUInt()) {
                            _walletState.value = current.copy(blockHeight = notification.height.toUInt())
                        }
                        if (current.hasPendingBitcoinTransactions()) {
                            pendingBlockSyncJob?.cancel()
                            pendingBlockSyncJob = launch {
                                delay(NOTIFICATION_DEBOUNCE_MS)
                                if (!_walletState.value.hasPendingBitcoinTransactions()) {
                                    return@launch
                                }
                                if (BuildConfig.DEBUG) {
                                    Log.d(TAG, "New block while txs are pending — forcing debounced quick sync")
                                }
                                scriptHashStatusCache.clear()
                                val result = sync(force = true)
                                if (result is WalletResult.Success) {
                                    subscribeNewlyRevealedAddresses()
                                    electrumCache.saveScriptHashStatuses(scriptHashStatusCache.toMap())
                                }
                            }
                        }
                        return@collect
                    }

                    // Script hash changes trigger targeted syncs; new blocks also force
                    // one while there are pending txs so confirmations are not missed.
                    pendingNotifications.add(notification)
                    lastNotificationTime = System.currentTimeMillis()

                    launch debounceSync@{
                        delay(NOTIFICATION_DEBOUNCE_MS)

                        if (System.currentTimeMillis() - lastNotificationTime < NOTIFICATION_DEBOUNCE_MS) {
                            return@debounceSync
                        }

                        val batch = pendingNotifications.toList()
                        pendingNotifications.clear()

                        if (batch.isEmpty()) return@debounceSync

                        if (BuildConfig.DEBUG) {
                            Log.d(TAG, "Debounced sync trigger: ${batch.size} script hash changes")
                        }

                        scriptHashStatusCache.clear()
                        val result = sync(force = true)

                        if (result is WalletResult.Success) {
                            subscribeNewlyRevealedAddresses()
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
     * Reveal a fresh receiving address.
     *
     * BDK already tracks used/revealed keychain indices from sync state. Avoid
     * walking every transaction here; that makes the receive button noticeably
     * slow on wallets with large histories.
     */
    suspend fun getNewAddress(): WalletResult<String> =
        withContext(Dispatchers.IO) {
            val currentWallet = wallet ?: return@withContext WalletResult.Error("Wallet not initialized")
            val activeWalletId =
                secureStorage.getActiveWalletId()
                    ?: return@withContext WalletResult.Error("No active wallet")

            try {
                val currentAddress = _walletState.value.currentAddress
                if (currentAddress != null) {
                    findExternalAddressIndex(currentWallet, currentAddress)?.let { currentIndex ->
                        currentWallet.markUsed(KeychainKind.EXTERNAL, currentIndex)
                    }
                }
                val addressInfo = currentWallet.nextUnusedAddress(KeychainKind.EXTERNAL)
                val address = addressInfo.address.toString()

                // Get label if exists
                val label = secureStorage.getAddressLabel(activeWalletId, address)

                val addrInfo =
                    ReceiveAddressInfo(
                        address = address,
                        label = label,
                        isUsed = false, // We only return unused addresses now
                    )

                _walletState.value =
                    _walletState.value.copy(
                        currentAddress = address,
                        currentAddressInfo = addrInfo,
                    )

                // Persist revealed addresses to database
                walletPersister?.let { currentWallet.persist(it) }

                WalletResult.Success(address)
            } catch (e: Exception) {
                WalletResult.Error("Failed to generate address", e)
            }
        }

    private fun findExternalAddressIndex(
        currentWallet: Wallet,
        address: String,
    ): UInt? {
        val externalTip = currentWallet.derivationIndex(KeychainKind.EXTERNAL) ?: return null
        var index = 0u
        while (index <= externalTip) {
            val candidate = currentWallet.peekAddress(KeychainKind.EXTERNAL, index).address.toString()
            if (candidate == address) return index
            index = index.inc()
        }
        return null
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
                } catch (_: Exception) {
                    // Skip outputs that can't be converted to addresses
                }
            }
        }
        return false
    }

    /**
     * Save a label for an address
     */
    fun saveAddressLabel(
        address: String,
        label: String,
    ) {
        val activeWalletId = secureStorage.getActiveWalletId() ?: return
        secureStorage.saveAddressLabel(activeWalletId, address, label)
        updateBitcoinAddressLabelIndex(activeWalletId, address, label)

        // Update state if this is the current address
        val currentState = _walletState.value
        if (currentState.currentAddress == address) {
            _walletState.value =
                currentState.copy(
                    currentAddressInfo = currentState.currentAddressInfo?.copy(label = label),
                )
        }
    }

    /**
     * Delete a label for an address
     */
    fun deleteAddressLabel(address: String) {
        val activeWalletId = secureStorage.getActiveWalletId() ?: return
        secureStorage.deleteAddressLabel(activeWalletId, address)
        updateBitcoinAddressLabelIndex(activeWalletId, address, "")

        // Update state if this is the current address
        val currentState = _walletState.value
        if (currentState.currentAddress == address) {
            _walletState.value =
                currentState.copy(
                    currentAddressInfo = currentState.currentAddressInfo?.copy(label = null),
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

    fun searchBitcoinTransactionTxids(
        query: String,
        showSwapTransactions: Boolean,
        limit: Int,
    ): TransactionSearchResult {
        val activeWalletId = secureStorage.getActiveWalletId() ?: return TransactionSearchResult(emptyList(), 0)
        return electrumCache.searchTransactionTxids(
            walletId = activeWalletId,
            layer = TransactionSearchLayer.BITCOIN,
            query = query,
            limit = limit,
            filters = TransactionSearchFilters(swapOnly = showSwapTransactions),
        )
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
    fun saveTransactionLabel(
        txid: String,
        label: String,
    ) {
        val activeWalletId = secureStorage.getActiveWalletId() ?: return
        saveBitcoinTransactionLabelIndexed(activeWalletId, txid, label)
    }

    fun deleteTransactionLabel(txid: String) {
        val activeWalletId = secureStorage.getActiveWalletId() ?: return
        secureStorage.deleteTransactionLabel(activeWalletId, txid)
        electrumCache.updateTransactionSearchLabel(
            walletId = activeWalletId,
            layer = TransactionSearchLayer.BITCOIN,
            txid = txid,
            label = "",
        )
    }

    /**
     * Get wallet metadata for a specific wallet by ID
     */
    fun getWalletMetadata(walletId: String): StoredWallet? {
        return secureStorage.getWalletMetadata(walletId)
    }

    fun getMultisigWalletConfig(walletId: String): MultisigWalletConfig? =
        secureStorage.getMultisigWalletConfig(walletId)

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

    fun getAllLiquidTransactionLabelsForWallet(walletId: String): Map<String, String> {
        return secureStorage.getAllLiquidTransactionLabels(walletId)
    }

    fun getLiquidMetadataSnapshotForWallet(walletId: String): SecureStorage.LiquidMetadataSnapshot {
        return secureStorage.getLiquidMetadataSnapshot(walletId)
    }

    fun getAllSparkAddressLabelsForWallet(walletId: String): Map<String, String> {
        return secureStorage.getAllSparkAddressLabels(walletId)
    }

    fun getAllSparkTransactionLabelsForWallet(walletId: String): Map<String, String> {
        return secureStorage.getAllSparkTransactionLabels(walletId)
    }

    fun getAllLiquidTransactionSourcesForWallet(walletId: String): Map<String, LiquidTxSource> {
        return secureStorage.getAllLiquidTransactionSources(walletId)
    }

    fun getAllLiquidSwapDetailsForWallet(walletId: String): Map<String, LiquidSwapDetails> {
        return secureStorage.getAllLiquidSwapDetails(walletId)
    }

    fun getAllTransactionSwapDetailsForWallet(walletId: String): Map<String, LiquidSwapDetails> {
        return secureStorage.getAllTransactionSwapDetails(walletId)
    }

    private fun inferBitcoinChainSwapSettlementDetails(
        transaction: TransactionDetails,
        liquidSwapDetails: Map<String, LiquidSwapDetails>,
    ): LiquidSwapDetails? {
        val address = transaction.address?.takeIf { it.isNotBlank() } ?: return null
        val amountSats =
            transaction.addressAmount?.toLong()?.takeIf { it > 0L }
                ?: kotlin.math.abs(transaction.amountSats).takeIf { it > 0L }
                ?: return null

        val exactMatch =
            liquidSwapDetails.values.firstOrNull { details ->
                details.direction == SwapDirection.LBTC_TO_BTC &&
                    details.role == LiquidSwapTxRole.FUNDING &&
                    details.receiveAddress.equals(address, ignoreCase = true) &&
                    details.expectedReceiveAmountSats == amountSats
            }
        if (exactMatch != null) {
            return exactMatch.copy(role = LiquidSwapTxRole.SETTLEMENT)
        }

        val singleAddressMatch =
            liquidSwapDetails.values
                .singleOrNull { details ->
                    details.direction == SwapDirection.LBTC_TO_BTC &&
                        details.role == LiquidSwapTxRole.FUNDING &&
                        details.receiveAddress.equals(address, ignoreCase = true)
                }

        return singleAddressMatch?.copy(role = LiquidSwapTxRole.SETTLEMENT)
    }

    /**
     * Save an address label for a specific wallet
     */
    fun saveAddressLabelForWallet(
        walletId: String,
        address: String,
        label: String,
    ) {
        secureStorage.saveAddressLabel(walletId, address, label)
        updateBitcoinAddressLabelIndex(walletId, address, label)
    }

    fun saveAddressLabelsForWallet(
        walletId: String,
        labels: Map<String, String>,
    ) {
        saveBitcoinAddressLabelsIndexed(walletId, labels)
    }

    /**
     * Save a transaction label for a specific wallet
     */
    fun saveTransactionLabelForWallet(
        walletId: String,
        txid: String,
        label: String,
    ) {
        saveBitcoinTransactionLabelIndexed(walletId, txid, label)
    }

    fun saveTransactionLabelsForWallet(
        walletId: String,
        labels: Map<String, String>,
    ) {
        saveBitcoinTransactionLabelsIndexed(walletId, labels)
    }

    fun saveLiquidTransactionLabelForWallet(
        walletId: String,
        txid: String,
        label: String,
    ) {
        saveLiquidTransactionLabelIndexed(walletId, txid, label)
    }

    fun saveLiquidTransactionLabelsForWallet(
        walletId: String,
        labels: Map<String, String>,
    ) {
        saveLiquidTransactionLabelsIndexed(walletId, labels)
    }

    fun saveSparkAddressLabelsForWallet(
        walletId: String,
        labels: Map<String, String>,
    ) {
        secureStorage.saveSparkAddressLabels(walletId, labels)
    }

    fun saveSparkTransactionLabelsForWallet(
        walletId: String,
        labels: Map<String, String>,
    ) {
        secureStorage.saveSparkTransactionLabels(walletId, labels)
    }

    fun saveLiquidTransactionSourceForWallet(
        walletId: String,
        txid: String,
        source: LiquidTxSource,
    ) {
        secureStorage.saveLiquidTransactionSource(walletId, txid, source)
    }

    fun saveLiquidSwapDetailsForWallet(
        walletId: String,
        txid: String,
        details: LiquidSwapDetails,
    ) {
        secureStorage.saveLiquidSwapDetails(walletId, txid, details)
    }

    fun saveTransactionSwapDetailsForWallet(
        walletId: String,
        txid: String,
        details: LiquidSwapDetails,
    ) {
        secureStorage.saveTransactionSwapDetails(walletId, txid, details)
    }

    // ==================== BIP 329 Labels ====================

    /**
     * Export all labels for a wallet in BIP 329 JSONL format.
     */
    fun exportBip329Labels(walletId: String): String {
        val addressLabels = secureStorage.getAllAddressLabels(walletId)
        val txLabels = secureStorage.getAllTransactionLabels(walletId)
        val metadata = secureStorage.getWalletMetadata(walletId)
        val origin = if (metadata != null) {
            github.aeonbtc.ibiswallet.util.Bip329Labels.buildOrigin(
                metadata.addressType,
                metadata.masterFingerprint,
            )
        } else {
            null
        }
        return github.aeonbtc.ibiswallet.util.Bip329Labels.export(addressLabels, txLabels, origin)
    }

    /**
     * Get label counts for a wallet (address labels, transaction labels).
     */
    fun getLabelCounts(walletId: String): Pair<Int, Int> {
        val addressLabels = secureStorage.getAllAddressLabels(walletId)
        val txLabels = secureStorage.getAllTransactionLabels(walletId)
        return Pair(addressLabels.size, txLabels.size)
    }

    fun getAddressPreview(limitPerSection: Int): Triple<List<WalletAddress>, List<WalletAddress>, List<WalletAddress>> {
        val currentWallet = wallet ?: return Triple(emptyList(), emptyList(), emptyList())
        val activeWalletId = secureStorage.getActiveWalletId() ?: return Triple(emptyList(), emptyList(), emptyList())
        val labels = secureStorage.getAllAddressLabels(activeWalletId)
        val balanceByIndex = HashMap<Pair<KeychainKind, UInt>, ULong>()
        val outputCountByIndex = HashMap<Pair<KeychainKind, UInt>, Int>()

        try {
            for (output in currentWallet.listOutput()) {
                val key = output.keychain to output.derivationIndex
                outputCountByIndex[key] = (outputCountByIndex[key] ?: 0) + 1
                if (!output.isSpent) {
                    balanceByIndex[key] = (balanceByIndex[key] ?: 0UL) + output.txout.value.toSat()
                }
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Error listing wallet outputs for address preview: ${e.message}")
        }

        val usedAddresses = mutableListOf<WalletAddress>()

        fun buildPreviewForKeychain(
            keychain: KeychainKind,
            keychainType: KeychainType,
        ): List<WalletAddress> {
            val usedIndices =
                outputCountByIndex.keys
                    .asSequence()
                    .filter { it.first == keychain }
                    .map { it.second }
                    .sorted()
                    .toList()
            val usedIndexSet = usedIndices.toSet()
            usedIndices.take(limitPerSection).forEach { index ->
                val address = peekAddressStringCached(currentWallet, keychain, index)
                val key = keychain to index
                usedAddresses.add(
                    WalletAddress(
                        address = address,
                        index = index,
                        keychain = keychainType,
                        label = labels[address],
                        balanceSats = balanceByIndex[key] ?: 0UL,
                        transactionCount = outputCountByIndex[key] ?: 0,
                        isUsed = true,
                    ),
                )
            }

            val unused = mutableListOf<WalletAddress>()
            var index = 0u
            while (unused.size < limitPerSection) {
                if (index !in usedIndexSet) {
                    val address = peekAddressStringCached(currentWallet, keychain, index)
                    unused.add(
                        WalletAddress(
                            address = address,
                            index = index,
                            keychain = keychainType,
                            label = labels[address],
                            balanceSats = 0UL,
                            transactionCount = 0,
                            isUsed = false,
                        ),
                    )
                }
                index++
            }
            return unused
        }

        val receiveAddresses = buildPreviewForKeychain(KeychainKind.EXTERNAL, KeychainType.EXTERNAL)
        val changeAddresses = buildPreviewForKeychain(KeychainKind.INTERNAL, KeychainType.INTERNAL)
        return Triple(receiveAddresses, changeAddresses, usedAddresses)
    }

    /**
     * Get all addresses for the wallet (receive, change, used)
     */
    fun getAllAddresses(): Triple<List<WalletAddress>, List<WalletAddress>, List<WalletAddress>> {
        val currentWallet = wallet ?: return Triple(emptyList(), emptyList(), emptyList())
        val activeWalletId = secureStorage.getActiveWalletId() ?: return Triple(emptyList(), emptyList(), emptyList())
        val labels = secureStorage.getAllAddressLabels(activeWalletId)

        // Aggregate balances and output counts per (keychain, derivation index). `listOutput()`
        // returns only wallet-owned outputs with pre-resolved keychain/index metadata — avoids
        // iterating every input/output of every transaction and running `Address.fromScript` for
        // each one, which dominates CPU on wallets with thousands of txs.
        val balanceByIndex = HashMap<Pair<KeychainKind, UInt>, ULong>()
        val outputCountByIndex = HashMap<Pair<KeychainKind, UInt>, Int>()
        try {
            for (output in currentWallet.listOutput()) {
                val key = output.keychain to output.derivationIndex
                outputCountByIndex[key] = (outputCountByIndex[key] ?: 0) + 1
                if (!output.isSpent) {
                    balanceByIndex[key] = (balanceByIndex[key] ?: 0UL) + output.txout.value.toSat()
                }
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Error listing wallet outputs: ${e.message}")
        }

        val receiveAddresses = mutableListOf<WalletAddress>()
        val changeAddresses = mutableListOf<WalletAddress>()
        val usedAddresses = mutableListOf<WalletAddress>()

        fun collectKeychain(
            keychain: KeychainKind,
            keychainType: KeychainType,
            targetUnused: MutableList<WalletAddress>,
        ) {
            val lastRevealed =
                try {
                    currentWallet.derivationIndex(keychain)
                } catch (_: Exception) {
                    null
                }

            if (lastRevealed != null) {
                var i = 0u
                while (i <= lastRevealed) {
                    val addr = peekAddressStringCached(currentWallet, keychain, i)
                    val key = keychain to i
                    val count = outputCountByIndex[key] ?: 0
                    val balance = balanceByIndex[key] ?: 0UL
                    val entry =
                        WalletAddress(
                            address = addr,
                            index = i,
                            keychain = keychainType,
                            label = labels[addr],
                            balanceSats = balance,
                            transactionCount = count,
                            isUsed = count > 0,
                        )
                    if (count > 0) usedAddresses.add(entry) else targetUnused.add(entry)
                    i = i.inc()
                }
            }

            val startIndex = (lastRevealed?.plus(1u)) ?: 0u
            val peekCount = maxOf(1, ADDRESS_PEEK_AHEAD - targetUnused.size)
            for (offset in 0u until peekCount.toUInt()) {
                val index = startIndex + offset
                val addr = peekAddressStringCached(currentWallet, keychain, index)
                targetUnused.add(
                    WalletAddress(
                        address = addr,
                        index = index,
                        keychain = keychainType,
                        label = labels[addr],
                        balanceSats = 0UL,
                        transactionCount = 0,
                        isUsed = false,
                    ),
                )
            }
        }

        try {
            collectKeychain(KeychainKind.EXTERNAL, KeychainType.EXTERNAL, receiveAddresses)
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Error getting receive addresses: ${e.message}")
        }

        try {
            collectKeychain(KeychainKind.INTERNAL, KeychainType.INTERNAL, changeAddresses)
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
            val txPositions =
                currentWallet.transactions().associateBy(
                    { it.transaction.computeTxid().toString() },
                    { it.chainPosition },
                )
            val utxos = currentWallet.listUnspent()
            utxos.mapNotNull { utxo ->
                try {
                    val addr = Address.fromScript(utxo.txout.scriptPubkey, network).toString()
                    val outpoint = "${utxo.outpoint.txid}:${utxo.outpoint.vout}"

                    // Use the wallet transaction list's chain position so confirmation updates
                    // stay consistent with the rest of the BTC UI.
                    val isConfirmed =
                        txPositions[utxo.outpoint.txid.toString()] is ChainPosition.Confirmed

                    UtxoInfo(
                        outpoint = outpoint,
                        txid = utxo.outpoint.txid.toString(),
                        vout = utxo.outpoint.vout,
                        address = addr,
                        amountSats = utxo.txout.value.toSat(),
                        label = labels[addr],
                        isConfirmed = isConfirmed,
                        isFrozen = frozenUtxos.contains(outpoint),
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
    fun setUtxoFrozen(
        outpoint: String,
        frozen: Boolean,
    ) {
        val activeWalletId = secureStorage.getActiveWalletId() ?: return
        if (frozen) {
            secureStorage.freezeUtxo(activeWalletId, outpoint)
        } else {
            secureStorage.unfreezeUtxo(activeWalletId, outpoint)
        }
    }

    private suspend fun resolveMaxExactBitcoinAmount(
        recipientAddress: String,
        feeRateSatPerVb: Double,
        selectedUtxos: List<UtxoInfo>? = null,
        applyManualSelection: ((TxBuilder) -> TxBuilder)? = null,
    ): ULong {
        val currentWallet = wallet ?: return 0UL
        val recipientScript =
            buildSendRecipientScripts(
                recipients = listOf(Recipient(recipientAddress, 0UL)),
                currentWallet = currentWallet,
            ).first().script
        val manualSelection = applyManualSelection ?: buildManualSelectionApplier(currentWallet, selectedUtxos)
        val feeRate = feeRateFromSatPerVb(feeRateSatPerVb)

        val availableSats =
            selectedUtxos
                ?.takeIf { it.isNotEmpty() }
                ?.sumOf { it.amountSats.toLong() }
                ?: currentWallet.balance().total.toSat().toLong()
        if (availableSats <= 0L) return 0UL

        val maxAmountSats =
            findMaxExactSendAmount(availableSats) { candidate ->
                try {
                    manualSelection(
                        TxBuilder().applyConfirmedOnlyFilter()
                            .feeRate(feeRate)
                            .addRecipient(recipientScript, Amount.fromSat(candidate.toULong())),
                    ).finish(currentWallet)
                    true
                } catch (_: Exception) {
                    false
                }
            }
        return maxAmountSats.toULong()
    }

    private suspend fun prepareSingleRecipientTransaction(
        recipientAddress: String,
        amountSats: ULong,
        feeRateSatPerVb: Double,
        selectedUtxos: List<UtxoInfo>? = null,
        isMaxSend: Boolean = false,
        precomputedFeeSats: ULong? = null,
        includePsbtDetails: Boolean = false,
    ): PreparedBitcoinSendCacheEntry? {
        val currentWallet = wallet ?: return null
        val cacheKey =
            buildSingleBitcoinSendPreparationKey(
                state = currentBitcoinSendPreparationState(),
                recipientAddress = recipientAddress,
                amountSats = amountSats,
                feeRateSatPerVb = feeRateSatPerVb,
                selectedOutpoints = selectedUtxos?.map { it.outpoint }.orEmpty(),
                isMaxSend = isMaxSend,
            )
        getPreparedBitcoinSendCacheEntry(
            key = cacheKey,
            requiresPsbtDetails = includePsbtDetails,
        )?.let { return it }

        val recipients = listOf(Recipient(recipientAddress, amountSats))
        val recipientScripts = buildSendRecipientScripts(recipients, currentWallet)
        val applyManualSelection = buildManualSelectionApplier(currentWallet, selectedUtxos)
        val resolvedAmountSats =
            if (isMaxSend) {
                resolveMaxExactBitcoinAmount(
                    recipientAddress = recipientAddress,
                    feeRateSatPerVb = feeRateSatPerVb,
                    selectedUtxos = selectedUtxos,
                    applyManualSelection = applyManualSelection,
                )
            } else {
                amountSats
            }
        if (resolvedAmountSats == 0UL) return null

        val preparedPsbt =
            buildPreparedPsbt(
                currentWallet = currentWallet,
                feeRateSatPerVb = feeRateSatPerVb,
                precomputedFeeSats = precomputedFeeSats,
            ) { builder ->
                applyManualSelection(
                    addRecipientScripts(
                        builder,
                        recipientScripts.map { it.copy(recipient = it.recipient.copy(amountSats = resolvedAmountSats)) },
                    ),
                )
            }
        val summary =
            summarizeSingleRecipientOutputs(
                tx = preparedPsbt.finalTx,
                recipientScript = recipientScripts.first().script,
                currentWallet = currentWallet,
                fallbackRecipientAmount = resolvedAmountSats,
            )
        val dryRunResult =
            DryRunResult(
                feeSats = preparedPsbt.feeSats.toLong(),
                changeSats = summary.changeAmountSats?.toLong() ?: 0L,
                changeAddress = summary.changeAddress,
                hasChange = summary.hasChange,
                numInputs = preparedPsbt.finalTx.input().size,
                txVBytes = preparedPsbt.txVBytes,
                effectiveFeeRate =
                    if (preparedPsbt.txVBytes > 0.0) {
                        preparedPsbt.feeSats.toDouble() / preparedPsbt.txVBytes
                    } else {
                        feeRateSatPerVb
                    },
                recipientAmountSats = summary.recipientAmountSats.toLong(),
            )
        val psbtDetails =
            if (includePsbtDetails) {
                buildSingleRecipientPsbtDetails(
                    psbt = preparedPsbt.psbt,
                    feeSats = preparedPsbt.feeSats,
                    recipientAddress = recipientAddress,
                    summary = summary,
                )
            } else {
                null
            }
        val entry =
            PreparedBitcoinSendCacheEntry(
                key = cacheKey,
                dryRunResult = dryRunResult,
                psbtDetails = psbtDetails,
            )
        cachePreparedBitcoinSendEntry(entry)
        return entry
    }

    private suspend fun prepareMultiRecipientTransaction(
        recipients: List<Recipient>,
        feeRateSatPerVb: Double,
        selectedUtxos: List<UtxoInfo>? = null,
        precomputedFeeSats: ULong? = null,
        includePsbtDetails: Boolean = false,
    ): PreparedBitcoinSendCacheEntry? {
        val currentWallet = wallet ?: return null
        val cacheKey =
            buildMultiBitcoinSendPreparationKey(
                state = currentBitcoinSendPreparationState(),
                recipients = recipients,
                feeRateSatPerVb = feeRateSatPerVb,
                selectedOutpoints = selectedUtxos?.map { it.outpoint }.orEmpty(),
            )
        getPreparedBitcoinSendCacheEntry(
            key = cacheKey,
            requiresPsbtDetails = includePsbtDetails,
        )?.let { return it }

        val applyManualSelection = buildManualSelectionApplier(currentWallet, selectedUtxos)
        val recipientScripts = buildSendRecipientScripts(recipients, currentWallet)
        val preparedPsbt =
            buildPreparedPsbt(
                currentWallet = currentWallet,
                feeRateSatPerVb = feeRateSatPerVb,
                precomputedFeeSats = precomputedFeeSats,
            ) { builder ->
                applyManualSelection(addRecipientScripts(builder, recipientScripts))
            }
        val summary =
            summarizeMultiRecipientOutputs(
                tx = preparedPsbt.finalTx,
                sendRecipientScripts = recipientScripts,
                currentWallet = currentWallet,
            )
        val dryRunResult =
            DryRunResult(
                feeSats = preparedPsbt.feeSats.toLong(),
                changeSats = summary.changeAmountSats?.toLong() ?: 0L,
                changeAddress = summary.changeAddress,
                hasChange = summary.hasChange,
                numInputs = preparedPsbt.finalTx.input().size,
                txVBytes = preparedPsbt.txVBytes,
                effectiveFeeRate =
                    if (preparedPsbt.txVBytes > 0.0) {
                        preparedPsbt.feeSats.toDouble() / preparedPsbt.txVBytes
                    } else {
                        feeRateSatPerVb
                    },
                recipientAmountSats = summary.totalRecipientAmountSats.toLong(),
            )
        val psbtDetails =
            if (includePsbtDetails) {
                buildMultiRecipientPsbtDetails(
                    psbt = preparedPsbt.psbt,
                    feeSats = preparedPsbt.feeSats,
                    recipients = recipients,
                    summary = summary,
                )
            } else {
                null
            }
        val entry =
            PreparedBitcoinSendCacheEntry(
                key = cacheKey,
                dryRunResult = dryRunResult,
                psbtDetails = psbtDetails,
            )
        cachePreparedBitcoinSendEntry(entry)
        return entry
    }

    /**
     * Create and broadcast a transaction
     * @param selectedUtxos Optional list of specific UTXOs to spend from (coin control)
     * @param label Optional label for the transaction
     * @param isMaxSend If true, precomputes the largest exact send amount with fees on top
     */
    suspend fun sendBitcoin(
        recipientAddress: String,
        amountSats: ULong,
        feeRateSatPerVb: Double = 1.0,
        selectedUtxos: List<UtxoInfo>? = null,
        label: String? = null,
        isMaxSend: Boolean = false,
        precomputedFeeSats: ULong? = null,
        onProgress: (String) -> Unit = {},
    ): WalletResult<String> =
        withContext(Dispatchers.IO) {
            val currentWallet = wallet ?: return@withContext WalletResult.Error("Wallet not initialized")
            val client = electrumClient ?: return@withContext WalletResult.Error("Not connected to Electrum server")

            // Check if watch-only
            val activeWalletId = secureStorage.getActiveWalletId()
            val storedWallet = activeWalletId?.let { secureStorage.getWalletMetadata(it) }
            if (storedWallet?.policyType == WalletPolicyType.MULTISIG) {
                return@withContext WalletResult.Error("Use PSBT signing for multisig wallets")
            }
            if (storedWallet?.isWatchOnly == true) {
                return@withContext WalletResult.Error("Cannot send from watch-only wallet")
            }

            try {
                onProgress("Building transaction...")
                val applyManualSelection = buildManualSelectionApplier(currentWallet, selectedUtxos)
                val resolvedAmountSats =
                    if (isMaxSend) {
                        resolveMaxExactBitcoinAmount(
                            recipientAddress = recipientAddress,
                            feeRateSatPerVb = feeRateSatPerVb,
                            selectedUtxos = selectedUtxos,
                            applyManualSelection = applyManualSelection,
                        )
                    } else {
                        amountSats
                    }
                if (resolvedAmountSats == 0UL) {
                    return@withContext WalletResult.Error("No spendable Bitcoin available")
                }
                val sendRecipients = listOf(Recipient(recipientAddress, resolvedAmountSats))
                val usesSilentPayment = hasSilentPaymentRecipient(sendRecipients)
                val recipientScripts = buildSendRecipientScripts(sendRecipients, currentWallet)
                val feeRate = feeRateFromSatPerVb(feeRateSatPerVb)

                // Helper to configure the TxBuilder with recipients, UTXOs, etc.
                fun buildTx(builder: TxBuilder): TxBuilder {
                    return applyManualSelection(
                        addRecipientScripts(builder, recipientScripts),
                    )
                }

                // When the dry-run already computed the exact fee, reuse it directly
                // via feeAbsolute to guarantee the broadcast fee matches the estimate
                // the user approved. Otherwise fall back to the two-pass correction.
                val psbt =
                    if (precomputedFeeSats != null) {
                        try {
                            buildTx(
                                TxBuilder().applyConfirmedOnlyFilter().feeAbsolute(Amount.fromSat(precomputedFeeSats)),
                            ).finish(currentWallet)
                        } catch (_: Exception) {
                            // Fallback: two-pass if feeAbsolute with precomputed fee fails
                            // (e.g. UTXO set changed between dry-run and send)
                            val pass1Psbt =
                                buildTx(
                                    TxBuilder().applyConfirmedOnlyFilter().feeRate(feeRate),
                                ).finish(currentWallet)
                            val exactFeeResult =
                                computeExactFee(pass1Psbt, currentWallet, pass1Psbt.extractTx(), feeRateSatPerVb)
                            if (exactFeeResult != null) {
                                try {
                                    buildTx(
                                        TxBuilder().applyConfirmedOnlyFilter().feeAbsolute(
                                            Amount.fromSat(exactFeeResult.feeSats),
                                        ),
                                    ).finish(currentWallet)
                                } catch (_: Exception) {
                                    pass1Psbt
                                }
                            } else {
                                pass1Psbt
                            }
                        }
                    } else {
                        val pass1Psbt =
                            buildTx(
                                TxBuilder().applyConfirmedOnlyFilter().feeRate(feeRate),
                            ).finish(currentWallet)
                        onProgress("Computing fee...")
                        val exactFeeResult =
                            computeExactFee(pass1Psbt, currentWallet, pass1Psbt.extractTx(), feeRateSatPerVb)
                        if (exactFeeResult != null) {
                            try {
                                buildTx(
                                    TxBuilder().applyConfirmedOnlyFilter().feeAbsolute(
                                        Amount.fromSat(exactFeeResult.feeSats),
                                    ),
                                ).finish(currentWallet)
                            } catch (_: Exception) {
                                pass1Psbt
                            }
                        } else {
                            pass1Psbt
                        }
                    }

                val psbtForSigning =
                    if (usesSilentPayment) {
                        rebuildWithSilentPaymentOutputs(
                            placeholderPsbt = psbt,
                            currentWallet = currentWallet,
                            storedWallet = storedWallet,
                            recipients = sendRecipients,
                            feeSats = psbt.fee(),
                        )
                    } else {
                        psbt
                    }

                onProgress("Signing transaction...")
                val tx =
                    signWithFeeCorrection(
                        initialPsbt = psbtForSigning,
                        wallet = currentWallet,
                        targetSatPerVb = feeRateSatPerVb,
                        rebuildWithFee = { fee ->
                            if (usesSilentPayment) {
                                try {
                                    rebuildWithSilentPaymentOutputs(
                                        placeholderPsbt = psbt,
                                        currentWallet = currentWallet,
                                        storedWallet = storedWallet,
                                        recipients = sendRecipients,
                                        feeSats = fee,
                                    )
                                } catch (_: Exception) {
                                    null
                                }
                            } else {
                                try {
                                    buildTx(
                                        TxBuilder().applyConfirmedOnlyFilter()
                                            .feeAbsolute(Amount.fromSat(fee)),
                                    ).finish(currentWallet)
                                } catch (_: Exception) {
                                    null
                                }
                            }
                        },
                    ).tx

                onProgress("Broadcasting to network...")
                client.transactionBroadcast(tx)

                val txid = tx.computeTxid().toString()

                // Save transaction label if provided
                if (!label.isNullOrBlank() && activeWalletId != null) {
                    saveBitcoinTransactionLabelIndexed(activeWalletId, txid, label)
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
        feeRateSatPerVb: Double = 1.0,
        selectedUtxos: List<UtxoInfo>? = null,
        isMaxSend: Boolean = false,
        preparePsbtDetails: Boolean = false,
    ): DryRunResult =
        withContext(Dispatchers.IO) {
            try {
                prepareSingleRecipientTransaction(
                    recipientAddress = recipientAddress,
                    amountSats = amountSats,
                    feeRateSatPerVb = feeRateSatPerVb,
                    selectedUtxos = selectedUtxos,
                    isMaxSend = isMaxSend,
                    includePsbtDetails = preparePsbtDetails,
                )?.dryRunResult ?: DryRunResult.error(localizedString(R.string.loc_534e1eb2))
            } catch (e: Exception) {
                val msg = e.message ?: "Transaction build failed"
                val userMessage =
                    when {
                        e.isTransactionInsufficientFundsError() ->
                            localizedString(R.string.loc_534e1eb2)
                        msg.contains("OutputBelowDustLimit", ignoreCase = true) ||
                            msg.contains("index=0", ignoreCase = true) -> "Amount below dust limit"
                        else -> "Could not build transaction"
                    }
                DryRunResult.error(userMessage)
            }
        }

    /**
     * Dry-run transaction build with multiple recipients.
     */
    suspend fun dryRunBuildTx(
        recipients: List<Recipient>,
        feeRateSatPerVb: Double = 1.0,
        selectedUtxos: List<UtxoInfo>? = null,
        preparePsbtDetails: Boolean = false,
    ): DryRunResult? =
        withContext(Dispatchers.IO) {
            try {
                prepareMultiRecipientTransaction(
                    recipients = recipients,
                    feeRateSatPerVb = feeRateSatPerVb,
                    selectedUtxos = selectedUtxos,
                    includePsbtDetails = preparePsbtDetails,
                )?.dryRunResult
            } catch (e: Exception) {
                val msg = e.message ?: "Transaction build failed"
                val userMessage =
                    when {
                        e.isTransactionInsufficientFundsError() ->
                            localizedString(R.string.loc_534e1eb2)
                        msg.contains("OutputBelowDustLimit", ignoreCase = true) ||
                            msg.contains("index=0", ignoreCase = true) -> "Amount below dust limit"
                        else -> "Could not build transaction"
                    }
                DryRunResult.error(userMessage)
            }
        }

    /**
     * Send Bitcoin to multiple recipients in a single transaction.
     */
    suspend fun sendBitcoin(
        recipients: List<Recipient>,
        feeRateSatPerVb: Double = 1.0,
        selectedUtxos: List<UtxoInfo>? = null,
        label: String? = null,
        precomputedFeeSats: ULong? = null,
        onProgress: (String) -> Unit = {},
    ): WalletResult<String> =
        withContext(Dispatchers.IO) {
            val currentWallet = wallet ?: return@withContext WalletResult.Error("Wallet not initialized")
            val client = electrumClient ?: return@withContext WalletResult.Error("Not connected to Electrum server")

            val activeWalletId = secureStorage.getActiveWalletId()
            val storedWallet = activeWalletId?.let { secureStorage.getWalletMetadata(it) }
            if (storedWallet?.policyType == WalletPolicyType.MULTISIG) {
                return@withContext WalletResult.Error("Use PSBT signing for multisig wallets")
            }
            if (storedWallet?.isWatchOnly == true) {
                return@withContext WalletResult.Error("Cannot send from watch-only wallet")
            }

            try {
                onProgress("Building transaction...")
                val feeRate = feeRateFromSatPerVb(feeRateSatPerVb)
                val applyManualSelection = buildManualSelectionApplier(currentWallet, selectedUtxos)
                val usesSilentPayment = hasSilentPaymentRecipient(recipients)
                val recipientScripts = buildSendRecipientScripts(recipients, currentWallet)

                fun buildTx(builder: TxBuilder): TxBuilder {
                    return applyManualSelection(addRecipientScripts(builder, recipientScripts))
                }

                val psbt =
                    if (precomputedFeeSats != null) {
                        try {
                            buildTx(
                                TxBuilder().applyConfirmedOnlyFilter().feeAbsolute(Amount.fromSat(precomputedFeeSats)),
                            ).finish(currentWallet)
                        } catch (_: Exception) {
                            val pass1Psbt =
                                buildTx(
                                    TxBuilder().applyConfirmedOnlyFilter().feeRate(feeRate),
                                ).finish(currentWallet)
                            val exactFeeResult =
                                computeExactFee(pass1Psbt, currentWallet, pass1Psbt.extractTx(), feeRateSatPerVb)
                            if (exactFeeResult != null) {
                                try {
                                    buildTx(
                                        TxBuilder().applyConfirmedOnlyFilter().feeAbsolute(
                                            Amount.fromSat(exactFeeResult.feeSats),
                                        ),
                                    ).finish(currentWallet)
                                } catch (_: Exception) {
                                    pass1Psbt
                                }
                            } else {
                                pass1Psbt
                            }
                        }
                    } else {
                        val pass1Psbt =
                            buildTx(
                                TxBuilder().applyConfirmedOnlyFilter().feeRate(feeRate),
                            ).finish(currentWallet)
                        onProgress("Computing fee...")
                        val exactFeeResult =
                            computeExactFee(pass1Psbt, currentWallet, pass1Psbt.extractTx(), feeRateSatPerVb)
                        if (exactFeeResult != null) {
                            try {
                                buildTx(
                                    TxBuilder().applyConfirmedOnlyFilter().feeAbsolute(
                                        Amount.fromSat(exactFeeResult.feeSats),
                                    ),
                                ).finish(currentWallet)
                            } catch (_: Exception) {
                                pass1Psbt
                            }
                        } else {
                            pass1Psbt
                        }
                    }

                val psbtForSigning =
                    if (usesSilentPayment) {
                        rebuildWithSilentPaymentOutputs(
                            placeholderPsbt = psbt,
                            currentWallet = currentWallet,
                            storedWallet = storedWallet,
                            recipients = recipients,
                            feeSats = psbt.fee(),
                        )
                    } else {
                        psbt
                    }

                onProgress("Signing transaction...")
                val tx = signWithFeeCorrection(
                    initialPsbt = psbtForSigning,
                    wallet = currentWallet,
                    targetSatPerVb = feeRateSatPerVb,
                    rebuildWithFee = { fee ->
                        if (usesSilentPayment) {
                            try {
                                rebuildWithSilentPaymentOutputs(
                                    placeholderPsbt = psbt,
                                    currentWallet = currentWallet,
                                    storedWallet = storedWallet,
                                    recipients = recipients,
                                    feeSats = fee,
                                )
                            } catch (_: Exception) {
                                null
                            }
                        } else {
                            try {
                                buildTx(
                                    TxBuilder().applyConfirmedOnlyFilter()
                                        .feeAbsolute(Amount.fromSat(fee)),
                                ).finish(currentWallet)
                            } catch (_: Exception) {
                                null
                            }
                        }
                    },
                ).tx

                onProgress("Broadcasting to network...")
                client.transactionBroadcast(tx)

                val txid = tx.computeTxid().toString()

                if (!label.isNullOrBlank() && activeWalletId != null) {
                    saveBitcoinTransactionLabelIndexed(activeWalletId, txid, label)
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
        feeRateSatPerVb: Double = 1.0,
        selectedUtxos: List<UtxoInfo>? = null,
        label: String? = null,
        precomputedFeeSats: ULong? = null,
    ): WalletResult<PsbtDetails> =
        withContext(Dispatchers.IO) {
            try {
                if (hasSilentPaymentRecipient(recipients)) {
                    return@withContext WalletResult.Error("Silent payments require a hot wallet")
                }
                val details =
                    prepareMultiRecipientTransaction(
                        recipients = recipients,
                        feeRateSatPerVb = feeRateSatPerVb,
                        selectedUtxos = selectedUtxos,
                        precomputedFeeSats = precomputedFeeSats,
                        includePsbtDetails = true,
                    )?.psbtDetails ?: return@withContext WalletResult.Error("Failed to create PSBT")
                val sessionDetails = maybeCreatePsbtSigningSession(details, label)
                if (!label.isNullOrBlank()) {
                    val activeWalletId = secureStorage.getActiveWalletId()
                    if (activeWalletId != null) {
                        secureStorage.savePendingPsbtLabel(activeWalletId, psbtStableId(sessionDetails.psbtBase64), label)
                    }
                }
                WalletResult.Success(sessionDetails)
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
        feeRateSatPerVb: Double = 1.0,
        selectedUtxos: List<UtxoInfo>? = null,
        label: String? = null,
        isMaxSend: Boolean = false,
        precomputedFeeSats: ULong? = null,
    ): WalletResult<PsbtDetails> =
        withContext(Dispatchers.IO) {
            try {
                if (isSilentPaymentAddress(recipientAddress)) {
                    return@withContext WalletResult.Error("Silent payments require a hot wallet")
                }
                val details =
                    prepareSingleRecipientTransaction(
                        recipientAddress = recipientAddress,
                        amountSats = amountSats,
                        feeRateSatPerVb = feeRateSatPerVb,
                        selectedUtxos = selectedUtxos,
                        isMaxSend = isMaxSend,
                        precomputedFeeSats = precomputedFeeSats,
                        includePsbtDetails = true,
                    )?.psbtDetails ?: return@withContext WalletResult.Error("No spendable Bitcoin available")
                val sessionDetails = maybeCreatePsbtSigningSession(details, label)
                if (!label.isNullOrBlank()) {
                    val activeWalletId = secureStorage.getActiveWalletId()
                    if (activeWalletId != null) {
                        secureStorage.savePendingPsbtLabel(activeWalletId, psbtStableId(sessionDetails.psbtBase64), label)
                    }
                }
                WalletResult.Success(sessionDetails)
            } catch (e: Exception) {
                WalletResult.Error("Failed to create PSBT", e)
            }
        }

    private fun maybeCreatePsbtSigningSession(
        details: PsbtDetails,
        label: String?,
    ): PsbtDetails {
        val activeWalletId = secureStorage.getActiveWalletId() ?: return details
        val metadata = secureStorage.getWalletMetadata(activeWalletId) ?: return details
        if (metadata.policyType != WalletPolicyType.MULTISIG) return details

        val required = metadata.multisigThreshold ?: 1
        val psbt = Psbt(details.psbtBase64)
        var presentSignatures = 0
        var status = PsbtSessionStatus.IN_PROGRESS

        if (!metadata.isWatchOnly && wallet != null) {
            try {
                val finalizedByLocalSigner = wallet!!.sign(psbt)
                presentSignatures = 1
                if (finalizedByLocalSigner || canFinalize(psbt)) {
                    presentSignatures = required
                    status = PsbtSessionStatus.READY_TO_BROADCAST
                }
            } catch (_: Exception) {
                // Keep the coordinator session usable even if local signing fails.
            }
        }

        val workingPsbt = psbt.serialize()
        val sessionId = psbtStableId(details.psbtBase64)
        val now = System.currentTimeMillis()
        val session =
            PsbtSigningSession(
                id = sessionId,
                walletId = activeWalletId,
                originalPsbtBase64 = details.psbtBase64,
                workingPsbtBase64 = workingPsbt,
                signerExportPsbtBase64 = PsbtExportOptimizer.trimForSignerExport(workingPsbt),
                requiredSignatures = required,
                presentSignatures = presentSignatures,
                pendingLabel = label,
                createdAt = now,
                updatedAt = now,
                status = status,
            )
        secureStorage.savePsbtSigningSession(session)
        return details.copy(
            signerExportPsbtBase64 = session.signerExportPsbtBase64,
            psbtId = sessionId,
            presentSignatures = presentSignatures,
            requiredSignatures = required,
        )
    }

    fun getPsbtSigningSession(
        walletId: String,
        sessionId: String,
    ): PsbtSigningSession? = secureStorage.getPsbtSigningSession(walletId, sessionId)

    fun getPsbtSigningSessions(walletId: String): List<PsbtSigningSession> =
        secureStorage.getPsbtSigningSessions(walletId)

    suspend fun combinePsbtPartial(
        sessionId: String,
        partialPsbtBase64: String,
    ): WalletResult<PsbtSigningSession> =
        withContext(Dispatchers.IO) {
            try {
                val walletId = secureStorage.getActiveWalletId()
                    ?: return@withContext WalletResult.Error("No active wallet")
                val session = secureStorage.getPsbtSigningSession(walletId, sessionId)
                    ?: return@withContext WalletResult.Error("PSBT session not found")
                val partialId = psbtStableId(partialPsbtBase64)

                val working = Psbt(session.workingPsbtBase64)
                val combined =
                    try {
                        working.combine(Psbt(partialPsbtBase64))
                    } catch (_: Exception) {
                        val original = Psbt(session.originalPsbtBase64)
                        original.combine(Psbt(partialPsbtBase64))
                    }

                // Verify the combined PSBT's unsigned transaction still
                // matches the session's original outputs/inputs. A
                // BIP 174 combine should never change the unsigned tx, but
                // a tampered partial that smuggles in a different global
                // tx (or one assembled offline against a forged "original")
                // must be rejected before it overwrites the session state.
                val combinedTx =
                    try {
                        combined.extractTx()
                    } catch (e: Exception) {
                        return@withContext WalletResult.Error(
                            "Combined PSBT could not be decoded for verification",
                            e,
                        )
                    }
                verifyBroadcastTransactionMatchesOriginalPsbt(combinedTx, session.originalPsbtBase64)
                    ?.let { return@withContext it }

                val combinedBase64 = combined.serialize()
                val alreadyImported = partialId in session.importedPartials
                val updatedImported =
                    if (alreadyImported) session.importedPartials else session.importedPartials + partialId
                val canFinalize = canFinalize(combined)
                val updatedPresentSignatures =
                    if (canFinalize) {
                        session.requiredSignatures
                    } else if (alreadyImported) {
                        session.presentSignatures
                    } else {
                        (session.presentSignatures + 1).coerceAtMost(session.requiredSignatures)
                    }
                val updated =
                    session.copy(
                        workingPsbtBase64 = combinedBase64,
                        signerExportPsbtBase64 = PsbtExportOptimizer.trimForSignerExport(combinedBase64),
                        presentSignatures = updatedPresentSignatures,
                        importedPartials = updatedImported,
                        updatedAt = System.currentTimeMillis(),
                        status = if (canFinalize) PsbtSessionStatus.READY_TO_BROADCAST else PsbtSessionStatus.IN_PROGRESS,
                    )
                secureStorage.savePsbtSigningSession(updated)
                WalletResult.Success(updated)
            } catch (e: Exception) {
                WalletResult.Error("Failed to import partial signature", e)
            }
        }

    private fun canFinalize(psbt: Psbt): Boolean =
        try {
            psbt.finalize().couldFinalize
        } catch (_: Exception) {
            false
        }

    private fun psbtStableId(psbtBase64: String): String {
        val bytes =
            try {
                Base64.decode(psbtBase64, Base64.DEFAULT)
            } catch (_: Exception) {
                psbtBase64.toByteArray(Charsets.UTF_8)
            }
        return MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }
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
        onProgress: (String) -> Unit = {},
    ): WalletResult<String> =
        withContext(Dispatchers.IO) {
            val client = electrumClient ?: return@withContext WalletResult.Error("Not connected to Electrum server")

            try {
                onProgress("Finalizing PSBT...")
                val signedPsbt = Psbt(psbtBase64)

                // Combine with original unsigned PSBT to restore UTXO data that
                // hardware wallets strip to reduce QR code size.
                // BIP 174 combine merges: original provides witness_utxo/non_witness_utxo,
                // signed provides partial_sigs/final_scriptwitness.
                //
                // We treat a combine failure as a hard error: combining a
                // returned signed PSBT with the reviewed unsigned PSBT is the
                // canonical defence against a co-signer or transport layer
                // substituting outputs. Silently falling back to the signed
                // payload would put the broadcast-time verifier on its own,
                // narrowing the defensive surface to a single check. If combine
                // legitimately fails (e.g. the user pasted an unrelated PSBT),
                // we want the user to know rather than proceeding.
                val psbtToFinalize =
                    if (unsignedPsbtBase64 != null) {
                        try {
                            val originalPsbt = Psbt(unsignedPsbtBase64)
                            originalPsbt.combine(signedPsbt)
                        } catch (e: Exception) {
                            SecureLog.w(
                                TAG,
                                "PSBT combine with original unsigned PSBT failed: ${e.message}",
                                e,
                                releaseMessage = "Could not combine signed PSBT with the reviewed unsigned PSBT",
                            )
                            return@withContext WalletResult.Error(
                                "Signed PSBT does not match the reviewed unsigned PSBT. Re-import the signed PSBT or re-create the transaction.",
                                e,
                            )
                        }
                    } else {
                        signedPsbt
                    }

                // Finalize the PSBT — assembles partial signatures into final
                // scriptSig/witness fields. Required before extractTx().
                val finalizeResult = psbtToFinalize.finalize()

                if (!finalizeResult.couldFinalize) {
                    val errorDetails =
                        finalizeResult.errors
                            ?.joinToString("; ") { it.message ?: "unknown" }
                            ?: "unknown reason"
                    return@withContext WalletResult.Error(
                        "PSBT finalization failed: $errorDetails",
                    )
                }

                val finalizedPsbt = finalizeResult.psbt
                val tx = finalizedPsbt.extractTx()

                unsignedPsbtBase64?.let { originalPsbt ->
                    verifyBroadcastTransactionMatchesOriginalPsbt(tx, originalPsbt)?.let { return@withContext it }
                }

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
                                            prevOutputs[vout],
                                        )
                                    }
                                } catch (_: Exception) {
                                    // non-fatal
                                }
                            }
                        }
                        walletPersister?.let { currentWallet.persist(it) }
                    } catch (_: Exception) {
                        // non-fatal
                    }
                }

                onProgress("Broadcasting to network...")
                client.transactionBroadcast(tx)

                val txid = tx.computeTxid().toString()

                // Apply pending label if provided
                if (!pendingLabel.isNullOrBlank()) {
                    val activeWalletId = secureStorage.getActiveWalletId()
                    if (activeWalletId != null) {
                        saveBitcoinTransactionLabelIndexed(activeWalletId, txid, pendingLabel)
                    }
                }

                // Invalidate pre-check cache so next background sync picks up the change
                clearScriptHashCache()

                WalletResult.Success(txid)
            } catch (e: Exception) {
                val errorMsg =
                    when {
                        e.message?.contains("non-final") == true -> "PSBT is not fully signed"
                        e.message?.contains("InputException") == true -> "PSBT signing incomplete"
                        else -> "Broadcast failed"
                    }
                WalletResult.Error(errorMsg, e)
            }
        }

    private fun verifyBroadcastTransactionMatchesOriginalPsbt(
        signedTx: Transaction,
        unsignedPsbtBase64: String,
    ): WalletResult.Error? {
        val originalTx =
            try {
                Psbt(unsignedPsbtBase64).extractTx()
            } catch (e: Exception) {
                return WalletResult.Error("Could not verify signed transaction against original PSBT", e)
            }

        val signedInputs = signedTx.input()
        val originalInputs = originalTx.input()
        if (signedInputs.size != originalInputs.size) {
            return WalletResult.Error(
                "Signed transaction has ${signedInputs.size} inputs, expected ${originalInputs.size}. " +
                    "The signed data may have been tampered with.",
            )
        }
        for (i in signedInputs.indices) {
            if (signedInputs[i].previousOutput != originalInputs[i].previousOutput) {
                return WalletResult.Error(
                    "Signed transaction input $i does not match the original PSBT. " +
                        "The signed data may have been tampered with.",
                )
            }
        }

        val signedOutputs = signedTx.output()
        val originalOutputs = originalTx.output()
        if (signedOutputs.size != originalOutputs.size) {
            return WalletResult.Error(
                "Signed transaction has ${signedOutputs.size} outputs, expected ${originalOutputs.size}. " +
                    "The signed data may have been tampered with.",
            )
        }
        for (i in signedOutputs.indices) {
            val signedOut = signedOutputs[i]
            val originalOut = originalOutputs[i]
            val scriptMatch =
                signedOut.scriptPubkey.toBytes()
                    .contentEquals(originalOut.scriptPubkey.toBytes())
            if (signedOut.value.toSat() != originalOut.value.toSat() || !scriptMatch) {
                return WalletResult.Error(
                    "Signed transaction output $i does not match the original PSBT. " +
                        "The signed data may have been tampered with.",
                )
            }
        }

        return null
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
        onProgress: (String) -> Unit = {},
        unsignedPsbtBase64: String? = null,
    ): WalletResult<String> =
        withContext(Dispatchers.IO) {
            val client = electrumClient ?: return@withContext WalletResult.Error("Not connected to Electrum server")

            try {
                onProgress("Verifying transaction...")
                val txBytes = txHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
                val tx = Transaction(txBytes)
                unsignedPsbtBase64?.let { originalPsbt ->
                    verifyBroadcastTransactionMatchesOriginalPsbt(tx, originalPsbt)?.let { return@withContext it }
                }

                onProgress("Broadcasting to network...")
                client.transactionBroadcast(tx)

                val txid = tx.computeTxid().toString()

                // Apply pending label if provided
                if (!pendingLabel.isNullOrBlank()) {
                    val activeWalletId = secureStorage.getActiveWalletId()
                    if (activeWalletId != null) {
                        saveBitcoinTransactionLabelIndexed(activeWalletId, txid, pendingLabel)
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
        onProgress: (String) -> Unit = {},
    ): WalletResult<String> =
        withContext(Dispatchers.IO) {
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
                        val errorDetails =
                            finalizeResult.errors
                                ?.joinToString("; ") { it.message ?: "unknown" }
                                ?: "unknown reason"
                        return@withContext WalletResult.Error(
                            "PSBT finalization failed: $errorDetails",
                        )
                    }

                    onProgress("Broadcasting to network...")
                    val tx = finalizeResult.psbt.extractTx()
                    client.transactionBroadcast(tx)
                    val txid = tx.computeTxid().toString()
                    WalletResult.Success(txid)
                }
            } catch (e: Exception) {
                val errorMsg =
                    when {
                        e.message?.contains("non-final") == true -> "PSBT is not fully signed"
                        e.message?.contains("InputException") == true -> "PSBT signing incomplete"
                        e.message?.contains("base64") == true -> "Invalid PSBT format"
                        else -> "Broadcast failed: ${e.message ?: "unknown error"}"
                    }
                WalletResult.Error(errorMsg, e)
            }
        }

    /**
     * Decoded output for a manual-broadcast preview: amount, destination
     * address (if derivable from the scriptPubKey on this network), and a flag
     * indicating whether the output script is owned by the currently loaded
     * wallet. UI surfaces this so the user can sanity-check a pasted hex or
     * PSBT before committing it to the chain.
     */
    data class ManualBroadcastOutput(
        val amountSats: Long,
        val address: String?,
        val ownedByLoadedWallet: Boolean,
    )

    /**
     * Structured preview of a manual-broadcast payload (raw tx hex or signed
     * PSBT base64). Returns null if the payload cannot be parsed at all so the
     * caller can render the existing "unrecognized format" feedback.
     */
    data class ManualBroadcastPreview(
        val txid: String,
        val outputs: List<ManualBroadcastOutput>,
        val anyOutputUnowned: Boolean,
        val isFromLoadedWallet: Boolean,
    )

    fun decodeManualBroadcastPreview(data: String): ManualBroadcastPreview? {
        val trimmed = data.trim()
        val isHex = trimmed.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }

        val tx: Transaction =
            try {
                if (isHex && trimmed.length % 2 == 0 && trimmed.length > 20) {
                    val txBytes = trimmed.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
                    Transaction(txBytes)
                } else {
                    val psbt = Psbt(trimmed)
                    val finalizeResult = psbt.finalize()
                    if (finalizeResult.couldFinalize) {
                        finalizeResult.psbt.extractTx()
                    } else {
                        // Unfinalized PSBT — we can still surface the global
                        // unsigned transaction outputs to the user.
                        psbt.extractTx()
                    }
                }
            } catch (_: Exception) {
                return null
            }

        val loadedWallet = wallet
        val network = loadedWallet?.network() ?: Network.BITCOIN

        val decodedOutputs = tx.output().map { output ->
            val owned =
                loadedWallet?.let {
                    runCatching { it.isMine(output.scriptPubkey) }.getOrDefault(false)
                } ?: false
            val address =
                runCatching {
                    Address.fromScript(output.scriptPubkey, network).toString()
                }.getOrNull()
            ManualBroadcastOutput(
                amountSats = output.value.toSat().toLong(),
                address = address,
                ownedByLoadedWallet = owned,
            )
        }

        val isFromLoadedWallet =
            loadedWallet?.let { w ->
                runCatching {
                    tx.input().all { input ->
                        w.getUtxo(input.previousOutput) != null
                    }
                }.getOrDefault(false)
            } ?: false

        return ManualBroadcastPreview(
            txid = tx.computeTxid().toString(),
            outputs = decodedOutputs,
            anyOutputUnowned = decodedOutputs.any { !it.ownedByLoadedWallet },
            isFromLoadedWallet = isFromLoadedWallet,
        )
    }

    /**
     * Result of scanning a WIF key for balances across all address types.
     */
    data class SweepScanResult(
        val addressType: AddressType,
        val address: String,
        val balanceSats: ULong,
        val utxoCount: Int,
    )

    /**
     * Scan a WIF private key for balances across all relevant address types.
     * Creates ephemeral BDK wallets, syncs each against Electrum, and returns balances.
     */
    suspend fun scanWifBalances(
        wif: String,
        onProgress: (String) -> Unit = {},
    ): WalletResult<List<SweepScanResult>> =
        withContext(Dispatchers.IO) {
            val client = electrumClient ?: return@withContext WalletResult.Error("Not connected to Electrum server")
            val activeId = secureStorage.getActiveWalletId()
            val storedWallet = activeId?.let { secureStorage.getWalletMetadata(it) }
            val network = (storedWallet?.network ?: WalletNetwork.BITCOIN).toBdkNetwork()

            // Determine which address types to scan based on key compression
            val compressed = isWifCompressed(wif)
            val addressTypes =
                if (compressed) {
                    // Compressed keys: Legacy, SegWit, Taproot
                    listOf(AddressType.LEGACY, AddressType.SEGWIT, AddressType.TAPROOT)
                } else {
                    // Uncompressed keys can only do Legacy
                    listOf(AddressType.LEGACY)
                }

            val results = mutableListOf<SweepScanResult>()

            try {
                if (!cleanupSweepTempDatabases() && BuildConfig.DEBUG) {
                    Log.w(TAG, "Failed to clear sweep temp databases before scan")
                }

                for (addrType in addressTypes) {
                    onProgress("Scanning ${addrType.displayName}...")

                    val tempDbPath = getSweepTempDbPath("scan")

                    try {
                        val descriptor = createDescriptorFromWif(wif, addrType, network)
                        val persister = Persister.newSqlite(tempDbPath)
                        val tempWallet =
                            Wallet.createSingle(
                                descriptor = descriptor,
                                network = network,
                                persister = persister,
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
                            results.add(
                                SweepScanResult(
                                    addressType = addrType,
                                    address = address,
                                    balanceSats = totalSats,
                                    utxoCount = utxos.size,
                                ),
                            )
                        }
                    } finally {
                        if (!deleteSqliteArtifacts(tempDbPath) && BuildConfig.DEBUG) {
                            Log.w(TAG, "Failed to delete temporary sweep-scan database")
                        }
                    }
                }

                WalletResult.Success(results)
            } catch (e: Exception) {
                WalletResult.Error("Scan failed: ${e.message}", e)
            } finally {
                if (!cleanupSweepTempDatabases() && BuildConfig.DEBUG) {
                    Log.w(TAG, "Failed to clean sweep temp databases after scan")
                }
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
        feeRateSatPerVb: Double,
        onProgress: (String) -> Unit = {},
    ): WalletResult<List<String>> =
        withContext(Dispatchers.IO) {
            val client = electrumClient ?: return@withContext WalletResult.Error("Not connected to Electrum server")
            val activeId = secureStorage.getActiveWalletId()
            val storedWallet = activeId?.let { secureStorage.getWalletMetadata(it) }
            val network = (storedWallet?.network ?: WalletNetwork.BITCOIN).toBdkNetwork()

            // Validate destination address
            val destAddr =
                try {
                    Address(destinationAddress, network)
                } catch (_: Exception) {
                    return@withContext WalletResult.Error("Invalid destination address")
                }

            val feeRate = feeRateFromSatPerVb(feeRateSatPerVb)
            val compressed = isWifCompressed(wif)
            val addressTypes =
                if (compressed) {
                    listOf(AddressType.LEGACY, AddressType.SEGWIT, AddressType.TAPROOT)
                } else {
                    listOf(AddressType.LEGACY)
                }

            val txids = mutableListOf<String>()

            try {
                if (!cleanupSweepTempDatabases() && BuildConfig.DEBUG) {
                    Log.w(TAG, "Failed to clear sweep temp databases before sweep")
                }

                for (addrType in addressTypes) {
                    onProgress("Checking ${addrType.displayName}...")

                    val tempDbPath = getSweepTempDbPath("sweep")

                    try {
                        val descriptor = createDescriptorFromWif(wif, addrType, network)
                        val persister = Persister.newSqlite(tempDbPath)
                        val tempWallet =
                            Wallet.createSingle(
                                descriptor = descriptor,
                                network = network,
                                persister = persister,
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
                        val psbt =
                            TxBuilder()
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
                        if (!deleteSqliteArtifacts(tempDbPath) && BuildConfig.DEBUG) {
                            Log.w(TAG, "Failed to delete temporary sweep database")
                        }
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
            } finally {
                if (!cleanupSweepTempDatabases() && BuildConfig.DEBUG) {
                    Log.w(TAG, "Failed to clean sweep temp databases after sweep")
                }
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
        newFeeRate: Double,
    ): WalletResult<String> =
        withContext(Dispatchers.IO) {
            val currentWallet = wallet ?: return@withContext WalletResult.Error("Wallet not initialized")
            val client = electrumClient ?: return@withContext WalletResult.Error("Not connected to Electrum server")

            // Check if watch-only
            val activeWalletId = secureStorage.getActiveWalletId()
            val storedWallet = activeWalletId?.let { secureStorage.getWalletMetadata(it) }
            if (storedWallet?.policyType == WalletPolicyType.MULTISIG) {
                return@withContext WalletResult.Error("Use PSBT signing for multisig wallets")
            }
            if (storedWallet?.isWatchOnly == true) {
                return@withContext WalletResult.Error("Cannot bump fee from watch-only wallet")
            }

            try {
                // Round up fee rate to ensure we meet the target
                val feeRate = feeRateFromSatPerVb(newFeeRate)

                // Use BDK's bump fee builder — requires Txid type in BDK 2.x
                val bumpFeeTxBuilder = BumpFeeTxBuilder(Txid.fromString(txid), feeRate)
                val psbt = bumpFeeTxBuilder.finish(currentWallet)

                // BumpFeeTxBuilder only accepts FeeRate (no feeAbsolute),
                // so we apply post-sign correction by re-invoking the builder.
                // The ±1 WU ECDSA drift is small relative to typical RBF bumps,
                // but we still correct for consistency.
                val result = signWithFeeCorrection(
                    initialPsbt = psbt,
                    wallet = currentWallet,
                    targetSatPerVb = newFeeRate,
                    rebuildWithFee = { fee ->
                        // Rebuild: extract the same inputs from the initial PSBT,
                        // reconstruct with feeAbsolute via a regular TxBuilder.
                        try {
                            val unsignedTx = psbt.extractTx()
                            val inputs = unsignedTx.input()
                            val outputs = unsignedTx.output()

                            // Identify change vs recipient outputs using isMine().
                            // Recipient outputs get fixed amounts via addRecipient;
                            // change output absorbs the remainder via drainTo.
                            var b = TxBuilder()
                                .feeAbsolute(Amount.fromSat(fee))

                            for (inp in inputs) {
                                b = b.addUtxo(inp.previousOutput)
                            }
                            b = b.manuallySelectedOnly()

                            for (output in outputs) {
                                val isChange = try {
                                    currentWallet.isMine(output.scriptPubkey)
                                } catch (_: Exception) { false }

                                b = if (isChange) {
                                    b.drainTo(output.scriptPubkey)
                                } else {
                                    b.addRecipient(
                                        output.scriptPubkey,
                                        Amount.fromSat(output.value.toSat()),
                                    )
                                }
                            }

                            b.finish(currentWallet)
                        } catch (_: Exception) {
                            null
                        }
                    },
                )
                val tx = result.tx

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
                        saveBitcoinTransactionLabelIndexed(activeWalletId, newTxid, originalLabel)
                    }
                    secureStorage.savePendingReplacementTransaction(activeWalletId, txid, newTxid)
                }

                // Invalidate pre-check cache so next background sync picks up the change
                clearScriptHashCache()

                WalletResult.Success(newTxid)
            } catch (e: Exception) {
                val errorMsg =
                    when {
                        e.message?.contains("not found") == true -> "Transaction not found in wallet"
                        e.message?.contains("already confirmed") == true -> "Transaction is already confirmed"
                        e.message?.contains("rbf") == true || e.message?.contains("RBF") == true ->
                            "Transaction is not RBF-enabled"
                        e.isTransactionInsufficientFundsError() ->
                            "Not enough change or wallet balance to pay the higher fee"
                        e.message?.contains("fee") == true -> "New fee rate must be higher than current"
                        else -> "Failed to bump fee"
                    }
                WalletResult.Error(errorMsg, e)
            }
        }

    suspend fun createBumpFeePsbt(
        txid: String,
        newFeeRate: Double,
    ): WalletResult<PsbtDetails> =
        withContext(Dispatchers.IO) {
            val currentWallet = wallet ?: return@withContext WalletResult.Error("Wallet not initialized")
            try {
                val feeRate = feeRateFromSatPerVb(newFeeRate)
                val psbt = BumpFeeTxBuilder(Txid.fromString(txid), feeRate).finish(currentWallet)
                WalletResult.Success(
                    maybeCreatePsbtSigningSession(
                        buildGenericPsbtDetails(
                            psbt = psbt,
                            displayLabel = "Replacement transaction",
                        ),
                        label = null,
                    ),
                )
            } catch (e: Exception) {
                val errorMsg =
                    when {
                        e.message?.contains("not found") == true -> "Transaction not found in wallet"
                        e.message?.contains("already confirmed") == true -> "Transaction is already confirmed"
                        e.message?.contains("rbf") == true || e.message?.contains("RBF") == true ->
                            "Transaction is not RBF-enabled"
                        e.isTransactionInsufficientFundsError() ->
                            "Not enough change or wallet balance to pay the higher fee"
                        e.message?.contains("fee") == true -> "New fee rate must be higher than current"
                        else -> "Failed to create replacement PSBT"
                    }
                WalletResult.Error(errorMsg, e)
            }
        }

    /**
     * Cancel an unconfirmed sent transaction by redirecting all funds back to the wallet.
     * Evicts the original tx to free its inputs back into the UTXO set, builds a
     * replacement that drains those inputs to an internal address, then broadcasts.
     * Wallet state is only persisted after successful broadcast.
     */
    suspend fun redirectTransaction(
        txid: String,
        newFeeRate: Double,
        destinationAddress: String? = null,
    ): WalletResult<String> =
        withContext(Dispatchers.IO) {
            val currentWallet = wallet ?: return@withContext WalletResult.Error("Wallet not initialized")
            val client = electrumClient ?: return@withContext WalletResult.Error("Not connected to Electrum server")

            val activeWalletId = secureStorage.getActiveWalletId()
            val storedWallet = activeWalletId?.let { secureStorage.getWalletMetadata(it) }
            if (storedWallet?.policyType == WalletPolicyType.MULTISIG) {
                return@withContext WalletResult.Error("Use PSBT signing for multisig wallets")
            }
            if (storedWallet?.isWatchOnly == true) {
                return@withContext WalletResult.Error("Cannot redirect from watch-only wallet")
            }

            try {
                val transactions = currentWallet.transactions()
                val originalCanonicalTx = transactions.find {
                    it.transaction.computeTxid().toString() == txid
                } ?: return@withContext WalletResult.Error("Transaction not found")

                if (originalCanonicalTx.chainPosition is ChainPosition.Confirmed) {
                    return@withContext WalletResult.Error("Transaction is already confirmed")
                }

                val originalInputs = originalCanonicalTx.transaction.input()
                if (!originalInputs.any { it.sequence < 0xfffffffeu }) {
                    return@withContext WalletResult.Error("Transaction is not RBF-enabled")
                }

                // Evict the original tx so BDK removes it from the canonical set
                // and its inputs become available UTXOs again. We do NOT persist yet —
                // if the replacement build or broadcast fails, a sync will restore
                // the correct state from the network.
                val evictedAt = System.currentTimeMillis() / 1000
                currentWallet.applyEvictedTxs(
                    listOf(EvictedTx(Txid.fromString(txid), evictedAt.toULong())),
                )

                val trimmedDestinationAddress = destinationAddress?.trim()?.takeIf { it.isNotBlank() }
                val isSilentPaymentRedirect =
                    trimmedDestinationAddress?.let(::isSilentPaymentAddress) == true
                val redirectScript =
                    if (trimmedDestinationAddress == null) {
                        // Pick the lowest-index unused internal (change) address instead
                        // of always advancing the derivation index.
                        val redirectAddress = run {
                            val lastRevealed = currentWallet.derivationIndex(KeychainKind.INTERNAL)
                            if (lastRevealed != null) {
                                for (i in 0u..lastRevealed) {
                                    val addr = currentWallet.peekAddress(KeychainKind.INTERNAL, i)
                                    if (!isAddressUsed(addr.address.toString())) {
                                        return@run addr
                                    }
                                }
                            }
                            currentWallet.revealNextAddress(KeychainKind.INTERNAL)
                        }
                        redirectAddress.address.scriptPubkey()
                    } else if (isSilentPaymentRedirect) {
                        scriptFromBytes(SilentPayment.placeholderScriptPubKey())
                    } else {
                        try {
                            Address(trimmedDestinationAddress, currentWallet.network()).scriptPubkey()
                        } catch (_: Exception) {
                            return@withContext WalletResult.Error("Invalid destination address")
                        }
                    }
                val bdkFeeRate = feeRateFromSatPerVb(newFeeRate)

                fun buildRedirectTx(
                    builder: TxBuilder,
                    destinationScript: Script,
                ): TxBuilder {
                    var b = builder
                    for (inp in originalInputs) {
                        b = b.addUtxo(inp.previousOutput)
                    }
                    return b.manuallySelectedOnly().drainTo(destinationScript)
                }

                fun rebuildRedirectPsbtWithFee(
                    fee: ULong,
                    placeholderPsbt: Psbt,
                ): Psbt {
                    val finalRedirectScript =
                        if (isSilentPaymentRedirect) {
                            buildSilentPaymentRedirectScript(
                                placeholderPsbt = placeholderPsbt,
                                currentWallet = currentWallet,
                                storedWallet = storedWallet,
                                destinationAddress = trimmedDestinationAddress,
                            )
                        } else {
                            redirectScript
                        }
                    return buildRedirectTx(
                        TxBuilder().feeAbsolute(Amount.fromSat(fee)),
                        finalRedirectScript,
                    ).finish(currentWallet)
                }

                val pass1Psbt = buildRedirectTx(TxBuilder().feeRate(bdkFeeRate), redirectScript).finish(currentWallet)
                val psbtForSigning =
                    if (isSilentPaymentRedirect) {
                        rebuildRedirectPsbtWithFee(pass1Psbt.fee(), pass1Psbt)
                    } else {
                        pass1Psbt
                    }

                val result = signWithFeeCorrection(
                    initialPsbt = psbtForSigning,
                    wallet = currentWallet,
                    targetSatPerVb = newFeeRate,
                    rebuildWithFee = { fee ->
                        try {
                            rebuildRedirectPsbtWithFee(fee, pass1Psbt)
                        } catch (_: Exception) { null }
                    },
                )
                val tx = result.tx

                client.transactionBroadcast(tx)

                val newTxid = tx.computeTxid().toString()

                // Persist after successful broadcast so the eviction is durable
                walletPersister?.let { currentWallet.persist(it) }

                if (activeWalletId != null) {
                    val originalLabel = secureStorage.getTransactionLabel(activeWalletId, txid)
                    if (!originalLabel.isNullOrBlank()) {
                        saveBitcoinTransactionLabelIndexed(activeWalletId, newTxid, originalLabel)
                    }
                    secureStorage.savePendingReplacementTransaction(activeWalletId, txid, newTxid)
                }

                clearScriptHashCache()

                WalletResult.Success(newTxid)
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.e(TAG, "redirectTransaction failed: ${e.message}", e)
                val errorMsg = when {
                    e.message?.contains("not found") == true -> "Transaction not found in wallet"
                    e.message?.contains("already confirmed") == true -> "Transaction is already confirmed"
                    e.message?.contains("rbf") == true || e.message?.contains("RBF") == true ->
                        "Transaction is not RBF-enabled"
                    e.message?.contains("fee") == true -> "Fee rate must be higher than current"
                    e.message?.contains("Insufficient") == true -> "Insufficient funds for redirect fee"
                    e.message?.contains("BelowDustLimit") == true -> "Output too small after fee — try a lower fee rate"
                    else -> "Failed to redirect transaction: ${e.message}"
                }
                WalletResult.Error(errorMsg, e)
            }
        }

    suspend fun createRedirectPsbt(
        txid: String,
        newFeeRate: Double,
        destinationAddress: String? = null,
    ): WalletResult<PsbtDetails> =
        withContext(Dispatchers.IO) {
            val currentWallet = wallet ?: return@withContext WalletResult.Error("Wallet not initialized")
            try {
                val transactions = currentWallet.transactions()
                val originalCanonicalTx = transactions.find {
                    it.transaction.computeTxid().toString() == txid
                } ?: return@withContext WalletResult.Error("Transaction not found")
                if (originalCanonicalTx.chainPosition is ChainPosition.Confirmed) {
                    return@withContext WalletResult.Error("Transaction is already confirmed")
                }
                val originalInputs = originalCanonicalTx.transaction.input()
                if (!originalInputs.any { it.sequence < 0xfffffffeu }) {
                    return@withContext WalletResult.Error("Transaction is not RBF-enabled")
                }

                // Evict the original tx in-memory so its inputs become spendable
                // for the replacement build. We deliberately do NOT call
                // `walletPersister.persist(...)`: if the user abandons the PSBT
                // before broadcast, the next sync from the network restores the
                // canonical view. The broadcast path (`redirectTransaction`)
                // persists only after a successful broadcast and we mirror that
                // contract here.
                currentWallet.applyEvictedTxs(
                    listOf(EvictedTx(Txid.fromString(txid), (System.currentTimeMillis() / 1000).toULong())),
                )

                val trimmedDestinationAddress = destinationAddress?.trim()?.takeIf { it.isNotBlank() }
                val redirectScript =
                    if (trimmedDestinationAddress == null) {
                        // Mirror `redirectTransaction`: prefer the lowest-index
                        // unused internal (change) address instead of advancing
                        // the derivation index. Otherwise abandoning the PSBT
                        // flow would silently burn a derivation slot.
                        val redirectAddress = run {
                            val lastRevealed = currentWallet.derivationIndex(KeychainKind.INTERNAL)
                            if (lastRevealed != null) {
                                for (i in 0u..lastRevealed) {
                                    val addr = currentWallet.peekAddress(KeychainKind.INTERNAL, i)
                                    if (!isAddressUsed(addr.address.toString())) {
                                        return@run addr
                                    }
                                }
                            }
                            currentWallet.revealNextAddress(KeychainKind.INTERNAL)
                        }
                        redirectAddress.address.scriptPubkey()
                    } else {
                        try {
                            Address(trimmedDestinationAddress, currentWallet.network()).scriptPubkey()
                        } catch (_: Exception) {
                            return@withContext WalletResult.Error("Invalid destination address")
                        }
                    }
                var builder = TxBuilder().feeRate(feeRateFromSatPerVb(newFeeRate))
                for (input in originalInputs) {
                    builder = builder.addUtxo(input.previousOutput)
                }
                val psbt = builder.manuallySelectedOnly().drainTo(redirectScript).finish(currentWallet)
                WalletResult.Success(
                    maybeCreatePsbtSigningSession(
                        buildGenericPsbtDetails(
                            psbt = psbt,
                            displayLabel = "Cancel transaction",
                        ),
                        label = null,
                    ),
                )
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.e(TAG, "createRedirectPsbt failed: ${e.message}", e)
                val errorMsg = when {
                    e.message?.contains("not found") == true -> "Transaction not found in wallet"
                    e.message?.contains("already confirmed") == true -> "Transaction is already confirmed"
                    e.message?.contains("rbf") == true || e.message?.contains("RBF") == true ->
                        "Transaction is not RBF-enabled"
                    e.message?.contains("fee") == true -> "Fee rate must be higher than current"
                    e.message?.contains("Insufficient") == true -> "Insufficient funds for redirect fee"
                    e.message?.contains("BelowDustLimit") == true -> "Output too small after fee — try a lower fee rate"
                    else -> "Failed to create cancel PSBT"
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
        feeRate: Double,
    ): WalletResult<String> =
        withContext(Dispatchers.IO) {
            val currentWallet = wallet ?: return@withContext WalletResult.Error("Wallet not initialized")
            val client = electrumClient ?: return@withContext WalletResult.Error("Not connected to Electrum server")

            // Check if watch-only
            val activeWalletId = secureStorage.getActiveWalletId()
            val storedWallet = activeWalletId?.let { secureStorage.getWalletMetadata(it) }
            if (storedWallet?.policyType == WalletPolicyType.MULTISIG) {
                return@withContext WalletResult.Error("Use PSBT signing for multisig wallets")
            }
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
                val effectiveFeeRateCeil = kotlin.math.ceil(feeRate).toULong()
                val estimatedFee = effectiveFeeRateCeil * estimatedVsize.toULong()

                // Check if parent output can cover fee AND leave dust-safe amount
                val canCoverFeeWithDust = totalValue > estimatedFee + dustLimit

                if (BuildConfig.DEBUG) {
                    Log.d(
                        TAG,
                        "CPFP: parentUtxos=${parentUtxos.size}, totalValue=$totalValue, feeRate=$feeRate, estimatedFee=$estimatedFee, canCoverFeeWithDust=$canCoverFeeWithDust",
                    )
                }

                // Local helper to build the CPFP transaction with given fee config
                fun buildCpfpTx(builder: TxBuilder): TxBuilder {
                    var b = builder
                    for (utxo in parentUtxos) {
                        if (BuildConfig.DEBUG) {
                            Log.d(
                                TAG,
                                "CPFP: Adding required UTXO: ${utxo.outpoint.txid}:${utxo.outpoint.vout}, value=${utxo.txout.value.toSat()}",
                            )
                        }
                        b = b.addUtxo(utxo.outpoint)
                    }
                    b = if (canCoverFeeWithDust) {
                        b.drainTo(changeAddress.address.scriptPubkey())
                    } else {
                        b.drainWallet()
                            .drainTo(changeAddress.address.scriptPubkey())
                    }
                    return b
                }

                // Two-pass fee correction: pass-1 with feeRate for coin selection,
                // then sign to measure actual vsize, rebuild with feeAbsolute
                val pass1Psbt = buildCpfpTx(TxBuilder().feeRate(bdkFeeRate)).finish(currentWallet)
                val exactFeeResult = computeExactFee(
                    pass1Psbt, currentWallet, pass1Psbt.extractTx(), feeRate,
                )
                val psbt = if (exactFeeResult != null) {
                    try {
                        buildCpfpTx(
                            TxBuilder().feeAbsolute(Amount.fromSat(exactFeeResult.feeSats)),
                        ).finish(currentWallet)
                    } catch (_: Exception) {
                        pass1Psbt
                    }
                } else {
                    pass1Psbt
                }

                // Post-sign fee correction for ECDSA signature non-determinism
                val result = signWithFeeCorrection(
                    initialPsbt = psbt,
                    wallet = currentWallet,
                    targetSatPerVb = feeRate,
                    rebuildWithFee = { fee ->
                        try {
                            buildCpfpTx(
                                TxBuilder().feeAbsolute(Amount.fromSat(fee)),
                            ).finish(currentWallet)
                        } catch (_: Exception) {
                            null
                        }
                    },
                )
                val tx = result.tx

                client.transactionBroadcast(tx)

                val childTxid = tx.computeTxid().toString()

                // Invalidate pre-check cache so next background sync picks up the change
                clearScriptHashCache()

                WalletResult.Success(childTxid)
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.e(TAG, "CPFP failed: ${e.message}", e)
                val errorMsg =
                    when {
                        e.message?.contains("not found") == true -> "Parent transaction not found"
                        e.message?.contains("confirmed") == true -> "Parent transaction is already confirmed"
                        e.message?.contains("Insufficient") == true -> {
                            // Parse the amounts from error message if possible
                            val match =
                                Regex(
                                    "(\\d+\\.\\d+) BTC available.*(\\d+\\.\\d+) BTC needed",
                                ).find(e.message ?: "")
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

    suspend fun createCpfpPsbt(
        parentTxid: String,
        feeRate: Double,
    ): WalletResult<PsbtDetails> =
        withContext(Dispatchers.IO) {
            val currentWallet = wallet ?: return@withContext WalletResult.Error("Wallet not initialized")
            try {
                val parentUtxos = currentWallet.listUnspent().filter { it.outpoint.txid.toString() == parentTxid }
                if (parentUtxos.isEmpty()) {
                    return@withContext WalletResult.Error("No spendable outputs found from parent transaction")
                }

                val changeAddress = currentWallet.revealNextAddress(KeychainKind.INTERNAL)
                val totalValue = parentUtxos.sumOf { it.txout.value.toSat().toLong() }.toULong()
                val estimatedVsize = 150L + (parentUtxos.size - 1) * 68L
                val estimatedFee = kotlin.math.ceil(feeRate).toULong() * estimatedVsize.toULong()
                val canCoverFeeWithDust = totalValue > estimatedFee + 546UL

                var builder = TxBuilder().feeRate(feeRateFromSatPerVb(feeRate))
                for (utxo in parentUtxos) {
                    builder = builder.addUtxo(utxo.outpoint)
                }
                builder =
                    if (canCoverFeeWithDust) {
                        builder.drainTo(changeAddress.address.scriptPubkey())
                    } else {
                        builder.drainWallet().drainTo(changeAddress.address.scriptPubkey())
                    }
                val psbt = builder.finish(currentWallet)
                WalletResult.Success(
                    maybeCreatePsbtSigningSession(
                        buildGenericPsbtDetails(
                            psbt = psbt,
                            displayLabel = "CPFP transaction",
                        ),
                        label = null,
                    ),
                )
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.e(TAG, "createCpfpPsbt failed: ${e.message}", e)
                val errorMsg =
                    when {
                        e.message?.contains("not found") == true -> "Parent transaction not found"
                        e.message?.contains("confirmed") == true -> "Parent transaction is already confirmed"
                        e.message?.contains("Insufficient") == true -> "Insufficient funds for CPFP"
                        e.message?.contains("BelowDustLimit") == true -> "Output too small — try a lower fee rate"
                        else -> "Failed to create CPFP PSBT"
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
            val tx =
                transactions.find { it.transaction.computeTxid().toString() == txid }
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
            val tx =
                transactions.find { it.transaction.computeTxid().toString() == txid }
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
    fun editWallet(
        walletId: String,
        newName: String,
        newGapLimit: Int,
        newFingerprint: String? = null,
    ) {
        if (secureStorage.editWallet(walletId, newName, newGapLimit, newFingerprint)) {
            updateWalletState()
        }
    }

    /**
     * Set whether a wallet requires app authentication before opening.
     */
    fun setWalletLocked(walletId: String, locked: Boolean) {
        if (secureStorage.setWalletLocked(walletId, locked)) {
            updateWalletState()
        }
    }

    /**
     * Reorder wallets to the given ID order.
     * Updates persistent storage and refreshes wallet state so both
     * ManageWallets and WalletSelectorPanel reflect the new order.
     */
    fun reorderWallets(orderedIds: List<String>) {
        secureStorage.reorderWalletIds(orderedIds)
        updateWalletState()
    }

    /**
     * Delete a specific wallet
     */
    suspend fun deleteWallet(walletId: String): WalletResult<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val wasActive = secureStorage.getActiveWalletId() == walletId

                // If deleting the duress wallet, clean up duress configuration
                if (secureStorage.getDuressWalletId() == walletId) {
                    secureStorage.clearDuressData()
                }

                if (wasActive) {
                    clearLoadedWallet()
                    clearScriptHashCache()
                }

                // Delete wallet data from secure storage
                secureStorage.deleteWallet(walletId)
                electrumCache.clearAllWalletActivityData()

                // Delete BDK database files
                deleteWalletDatabase(walletId)
                deleteLiquidWalletDatabase(walletId)

                // If this was the active wallet, clear current BDK wallet
                if (wasActive) {
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
     * Lightweight server health check via the proxy's direct socket.
     * Returns true if the server responded to a ping, false if dead.
     * Does NOT hold the sync mutex — safe to call from a heartbeat loop.
     */
    fun pingServer(
        socketTimeoutMs: Int = 8_000,
        lockTimeoutMs: Long = 3_000L,
        allowReconnect: Boolean = true,
    ): Boolean {
        return cachingProxy?.ping(
            socketTimeoutMs = socketTimeoutMs,
            lockTimeoutMs = lockTimeoutMs,
            allowReconnect = allowReconnect,
        ) ?: false
    }

    /**
     * Disconnect from Electrum server and clean up resources
     */
    suspend fun disconnect() =
        withContext(Dispatchers.IO) {
            connectionMutex.withLock {
                if (BuildConfig.DEBUG) Log.d(TAG, "Disconnecting from Electrum")
                stopNotificationCollector()
                electrumClient = null
                cachingProxy?.stop()
                cachingProxy = null
                _minFeeRate.value = CachingElectrumProxy.DEFAULT_MIN_FEE_RATE
                electrumCache.clearAllHistory()
                clearScriptHashCache()
            }
        }

    suspend fun abortActiveFullSync() =
        withContext(Dispatchers.IO) {
            if (BuildConfig.DEBUG) Log.d(TAG, "Aborting active full sync")
            abortingActiveFullSync = true

            // Intentionally bypass the normal disconnect mutex path so a blocking
            // native fullScan() call loses its underlying Electrum transport ASAP.
            stopNotificationCollector()
            electrumClient = null
            cachingProxy?.stop()
            cachingProxy = null
            _minFeeRate.value = CachingElectrumProxy.DEFAULT_MIN_FEE_RATE
            electrumCache.clearAllHistory()
            clearScriptHashCache()
            _walletState.value =
                _walletState.value.copy(
                    isSyncing = false,
                    isFullSyncing = false,
                    syncProgress = null,
                    error = null,
                )
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
     * Returns e.g. "Fulcrum 1.10.0", "elects/0.10.5", "ElectrumX 1.16.0", or null on failure.
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
    suspend fun fetchElectrumFeeEstimates(): Result<FeeEstimates> =
        withContext(Dispatchers.IO) {
            val client =
                electrumClient
                    ?: return@withContext Result.failure(Exception("Not connected to server"))

            try {
                // BDK's estimateFee(n) returns BTC/kB; convert to sat/vB:
                // 1 BTC = 100,000,000 sat, 1 kB = 1000 bytes → BTC/kB * 100,000 = sat/vB
                fun btcPerKbToSatPerVb(btcPerKb: Double): Double = btcPerKb * 100_000.0

                // Query multiple targets from low to high priority.
                // Wider spacing gives Bitcoin Core's estimator room to differentiate.
                val targets = listOf(1UL, 2UL, 3UL, 6UL, 12UL, 25UL, 36UL, 144UL)
                val rawResults =
                    targets.map { target ->
                        try {
                            client.estimateFee(target)
                        } catch (e: Exception) {
                            if (BuildConfig.DEBUG) Log.w(TAG, "estimateFee($target) threw: ${e.message}")
                            -1.0
                        }
                    }

                val estimates =
                    rawResults.map { raw ->
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
                        Exception("Server returned no fee estimates — it may not support fee estimation"),
                    )
                }

                // Ensure monotonic ordering: fastest >= halfHour >= hour >= economy >= minRate
                val economyFinal = (economy ?: minRate).coerceAtLeast(minRate)
                val hourFinal = (hour ?: economyFinal).coerceAtLeast(economyFinal)
                val halfHourFinal = (halfHour ?: hourFinal).coerceAtLeast(hourFinal)
                val fastestFinal = (fastest ?: halfHourFinal).coerceAtLeast(halfHourFinal)

                if (BuildConfig.DEBUG) {
                    Log.d(
                        TAG,
                        "Electrum fee estimates (sat/vB): fast=$fastestFinal half=$halfHourFinal hour=$hourFinal econ=$economyFinal",
                    )
                }

                Result.success(
                    FeeEstimates(
                        fastestFee = fastestFinal,
                        halfHourFee = halfHourFinal,
                        hourFee = hourFinal,
                        minimumFee = economyFinal,
                        timestamp = System.currentTimeMillis(),
                        source = FeeEstimateSource.ELECTRUM_SERVER,
                    ),
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

    private fun getActiveFullScanBatchSize(): ULong {
        val config = getElectrumConfig()
        val useSlowPath = isTorEnabled() || config?.useTor == true || config?.isOnionAddress() == true
        return if (useSlowPath) FULL_SCAN_BATCH_SIZE_TOR else FULL_SCAN_BATCH_SIZE_CLEARNET
    }

    private fun getWalletTransactionDescriptorCacheKey(activeWalletId: String?): String? {
        if (activeWalletId.isNullOrBlank()) return null
        val externalDescriptor = walletExternalDescriptor?.toString() ?: return null
        val internalDescriptor = walletInternalDescriptor?.toString().orEmpty()
        return if (walletIsSingleKey) {
            "single|$externalDescriptor"
        } else {
            "paired|$externalDescriptor|$internalDescriptor"
        }
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
        val defaults =
            listOf(
                ElectrumConfig(
                    name = "SethForPrivacy (Tor)",
                    url = "iuo6acfdicxhrovyqrekefh4rg2b7vgmzeeohc5cbwegawwhqpdxkgad.onion",
                    port = 50002,
                    useSsl = true,
                ),
                ElectrumConfig(
                    name = "Mullvad",
                    url = "bitcoin.mullvad.net",
                    port = 5010,
                    useSsl = true,
                ),
                ElectrumConfig(
                    name = "Bull Bitcoin",
                    url = "electrum.bullbitcoin.com",
                    port = 50002,
                    useSsl = true,
                ),
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

    fun reorderElectrumServerIds(orderedIds: List<String>) {
        secureStorage.reorderElectrumServerIds(orderedIds)
    }

    /**
     * Delete an Electrum server
     */
    fun deleteElectrumServer(serverId: String) {
        val wasActive = secureStorage.getActiveServerId() == serverId
        secureStorage.deleteElectrumServer(serverId)
        if (wasActive) {
            repositoryScope.launch {
                disconnect()
            }
        }
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
    fun acceptServerCertificate(
        host: String,
        port: Int,
        fingerprint: String,
    ) {
        secureStorage.saveServerCertFingerprint(host, port, fingerprint)
    }

    /**
     * Fetch transaction vsize from Electrum server.
     * Returns ceiled vsize (ceil(weight / 4)) matching Bitcoin Core / mempool.space convention.
     */
    suspend fun fetchTransactionVsizeFromElectrum(txid: String): Double? =
        withContext(Dispatchers.IO) {
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

    // ==================== Auto-Switch Server ====================

    /**
     * Check if auto-switch to another saved server on disconnect is enabled
     */
    fun isAutoSwitchServerEnabled(): Boolean {
        return secureStorage.isAutoSwitchServerEnabled()
    }

    /**
     * Set auto-switch server on disconnect state
     */
    fun setAutoSwitchServerEnabled(enabled: Boolean) {
        secureStorage.setAutoSwitchServerEnabled(enabled)
    }

    // ==================== User Disconnect Intent ====================

    fun isUserDisconnected(): Boolean = secureStorage.isUserDisconnected()

    fun setUserDisconnected(disconnected: Boolean) = secureStorage.setUserDisconnected(disconnected)

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
     * Get the preferred Layer 1 denomination.
     */
    fun getLayer1Denomination(): String {
        return secureStorage.getLayer1Denomination()
    }

    /**
     * Set the preferred Layer 1 denomination.
     */
    fun setLayer1Denomination(denomination: String) {
        secureStorage.setLayer1Denomination(denomination)
    }

    fun getAppLocale(): AppLocale {
        return secureStorage.getAppLocale()
    }

    fun setAppLocale(locale: AppLocale) {
        secureStorage.setAppLocale(locale)
    }

    fun getSwipeMode(): String {
        return secureStorage.getSwipeMode()
    }

    fun setSwipeMode(mode: String) {
        secureStorage.setSwipeMode(mode)
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

    fun hasSeenPrivacyModeHint(): Boolean {
        return secureStorage.hasSeenPrivacyModeHint()
    }

    fun setHasSeenPrivacyModeHint(seen: Boolean) {
        secureStorage.setHasSeenPrivacyModeHint(seen)
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

    fun getPsbtQrDensity(): SecureStorage.QrDensity {
        return secureStorage.getPsbtQrDensity()
    }

    fun setPsbtQrDensity(density: SecureStorage.QrDensity) {
        secureStorage.setPsbtQrDensity(density)
    }

    fun getPsbtQrBrightness(): Float {
        return secureStorage.getPsbtQrBrightness()
    }

    fun setPsbtQrBrightness(brightness: Float) {
        secureStorage.setPsbtQrBrightness(brightness)
    }

    fun isNfcEnabled(): Boolean {
        return secureStorage.isNfcEnabled()
    }

    fun setNfcEnabled(enabled: Boolean) {
        secureStorage.setNfcEnabled(enabled)
    }

    fun isWalletNotificationsEnabled(): Boolean {
        return secureStorage.isWalletNotificationsEnabled()
    }

    fun setWalletNotificationsEnabled(enabled: Boolean) {
        secureStorage.setWalletNotificationsEnabled(enabled)
    }

    fun isForegroundConnectivityEnabled(): Boolean {
        return secureStorage.isForegroundConnectivityEnabled()
    }

    fun setForegroundConnectivityEnabled(enabled: Boolean) {
        secureStorage.setForegroundConnectivityEnabled(enabled)
    }

    fun isAppUpdateCheckEnabled(): Boolean {
        return secureStorage.isAppUpdateCheckEnabled()
    }

    fun setAppUpdateCheckEnabled(enabled: Boolean) {
        secureStorage.setAppUpdateCheckEnabled(enabled)
    }

    fun getSeenAppUpdateVersion(): String? {
        return secureStorage.getSeenAppUpdateVersion()
    }

    fun setSeenAppUpdateVersion(versionName: String?) {
        secureStorage.setSeenAppUpdateVersion(versionName)
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

    fun getPriceCurrency(): String = secureStorage.getPriceCurrency()

    fun setPriceCurrency(currencyCode: String) = secureStorage.setPriceCurrency(currencyCode)

    fun isHistoricalTxFiatEnabled(): Boolean = secureStorage.isHistoricalTxFiatEnabled()

    fun setHistoricalTxFiatEnabled(enabled: Boolean) = secureStorage.setHistoricalTxFiatEnabled(enabled)

    fun getAllWalletIds(): List<String> = secureStorage.getWalletIds()

    fun getLayer2Denomination(): String = secureStorage.getLayer2Denomination()

    fun setLayer2Denomination(denomination: String) = secureStorage.setLayer2Denomination(denomination)

    fun isLayer2Enabled(): Boolean = secureStorage.isLayer2Enabled()

    fun setLayer2Enabled(enabled: Boolean) = secureStorage.setLayer2Enabled(enabled)

    fun isSparkLayer2Enabled(): Boolean = secureStorage.isSparkLayer2Enabled()

    fun setSparkLayer2Enabled(enabled: Boolean) = secureStorage.setSparkLayer2Enabled(enabled)

    fun getBoltzApiSource(): String = secureStorage.getBoltzApiSource()

    fun setBoltzApiSource(source: String) = secureStorage.setBoltzApiSource(source)

    fun getSideSwapApiSource(): String = secureStorage.getSideSwapApiSource()

    fun setSideSwapApiSource(source: String) = secureStorage.setSideSwapApiSource(source)

    fun getPreferredSwapService(): SwapService = secureStorage.getPreferredSwapService()

    fun setPreferredSwapService(service: SwapService) = secureStorage.setPreferredSwapService(service)

    fun getLiquidExplorer(): String = secureStorage.getLiquidExplorer()

    fun setLiquidExplorer(explorer: String) = secureStorage.setLiquidExplorer(explorer)

    fun getCustomLiquidExplorerUrl(): String? = secureStorage.getCustomLiquidExplorerUrl()

    fun setCustomLiquidExplorerUrl(url: String) = secureStorage.setCustomLiquidExplorerUrl(url)

    fun isLiquidTorEnabled(): Boolean = secureStorage.isLiquidTorEnabled()

    fun setLiquidTorEnabled(enabled: Boolean) = secureStorage.setLiquidTorEnabled(enabled)

    fun isLiquidAutoSwitchEnabled(): Boolean = secureStorage.isLiquidAutoSwitchEnabled()

    fun setLiquidAutoSwitchEnabled(enabled: Boolean) = secureStorage.setLiquidAutoSwitchEnabled(enabled)

    fun hasUserSelectedLiquidServer(): Boolean = secureStorage.hasUserSelectedLiquidServer()

    fun setUserSelectedLiquidServer(selected: Boolean) = secureStorage.setUserSelectedLiquidServer(selected)

    fun getAllLiquidServers(): List<github.aeonbtc.ibiswallet.data.model.LiquidElectrumConfig> =
        secureStorage.getAllLiquidServers()

    fun getActiveLiquidServerId(): String? = secureStorage.getActiveLiquidServerId()

    fun setActiveLiquidServerId(id: String?) = secureStorage.setActiveLiquidServerId(id)

    fun saveLiquidServer(config: github.aeonbtc.ibiswallet.data.model.LiquidElectrumConfig) =
        secureStorage.saveLiquidServer(config)

    fun isLiquidEnabledForWallet(walletId: String): Boolean =
        secureStorage.isLiquidEnabledForWallet(walletId)

    fun setLiquidEnabledForWallet(walletId: String, enabled: Boolean) =
        secureStorage.setLiquidEnabledForWallet(walletId, enabled)

    fun isSparkEnabledForWallet(walletId: String): Boolean =
        secureStorage.isSparkEnabledForWallet(walletId)

    fun setSparkEnabledForWallet(walletId: String, enabled: Boolean) =
        secureStorage.setSparkEnabledForWallet(walletId, enabled)

    fun getLayer2ProviderForWallet(walletId: String): Layer2Provider =
        secureStorage.getLayer2ProviderForWallet(walletId)

    fun setLayer2ProviderForWallet(walletId: String, provider: Layer2Provider) =
        secureStorage.setLayer2ProviderForWallet(walletId, provider)

    fun getLiquidGapLimit(walletId: String): Int =
        secureStorage.getLiquidGapLimit(walletId)

    fun setLiquidGapLimit(walletId: String, gapLimit: Int) =
        secureStorage.setLiquidGapLimit(walletId, gapLimit)

    fun isLiquidWatchOnly(walletId: String): Boolean =
        secureStorage.isLiquidWatchOnly(walletId)

    fun setLiquidWatchOnly(walletId: String, watchOnly: Boolean) =
        secureStorage.setLiquidWatchOnly(walletId, watchOnly)

    fun getLiquidDescriptor(walletId: String): String? =
        secureStorage.getLiquidDescriptor(walletId)

    fun setLiquidDescriptor(walletId: String, descriptor: String) =
        secureStorage.setLiquidDescriptor(walletId, descriptor)

    fun getFrozenUtxosForWallet(walletId: String): Set<String> =
        secureStorage.getFrozenUtxos(walletId)

    fun setFrozenUtxosForWallet(walletId: String, outpoints: Set<String>) =
        secureStorage.setFrozenUtxosForWallet(walletId, outpoints)

    /**
     * Update the wallet state with current data
     */
    private fun filterPendingReplacementTransactions(
        walletId: String?,
        transactions: List<TransactionDetails>,
    ): List<TransactionDetails> {
        if (walletId == null || transactions.isEmpty()) return transactions

        val pendingReplacements = secureStorage.getPendingReplacementTransactions(walletId)
        if (pendingReplacements.isEmpty()) return transactions

        val txById = transactions.associateBy { it.txid }
        val hiddenTxids = mutableSetOf<String>()

        for ((originalTxid, replacementTxid) in pendingReplacements) {
            val originalTx = txById[originalTxid]
            val replacementTx = txById[replacementTxid]

            when {
                replacementTx != null -> {
                    hiddenTxids.add(originalTxid)
                    if (originalTx == null || replacementTx.isConfirmed) {
                        secureStorage.removePendingReplacementTransaction(walletId, originalTxid)
                    }
                }
                originalTx == null -> {
                    secureStorage.removePendingReplacementTransaction(walletId, originalTxid)
                }
            }
        }

        return transactions.filterNot { it.txid in hiddenTxids }
    }

    private fun refreshWalletTransactionCache(currentWallet: Wallet) {
        walletTransactionCache.clear()
        currentWallet.transactions().forEach { canonicalTx ->
            val tx = canonicalTx.transaction
            val txid = tx.computeTxid().toString()
            walletTransactionCache[txid] =
                CachedWalletTransaction(
                    transaction = tx,
                    chainPosition = canonicalTx.chainPosition,
                )
        }
    }

    private fun recomputeNetAmount(
        currentWallet: Wallet,
        tx: Transaction,
    ): Long? =
        try {
            val sentAndReceived = currentWallet.sentAndReceived(tx)
            amountToSats(sentAndReceived.received).toLong() - amountToSats(sentAndReceived.sent).toLong()
        } catch (_: Exception) {
            null
        }

    private fun buildTransactionDetails(
        currentWallet: Wallet,
        activeWalletId: String?,
        txid: String,
        cachedTransaction: CachedWalletTransaction,
        network: Network,
    ): TransactionDetails? {
        return try {
            val tx = cachedTransaction.transaction
            val txidObj = tx.computeTxid()
            val details = currentWallet.txDetails(txidObj)

            val fee: ULong?
            val txWeight: ULong?
            val netAmount: Long
            val chainPos: ChainPosition

            if (details != null) {
                fee = details.fee?.let { amountToSats(it) }
                txWeight =
                    try {
                        tx.weight()
                    } catch (_: Exception) {
                        null
                    }
                val detailsNetAmount = details.balanceDelta
                val recomputedNetAmount =
                    if (detailsNetAmount == 0L) {
                        recomputeNetAmount(currentWallet, tx)
                    } else {
                        null
                    }
                netAmount = recomputedNetAmount?.takeIf { it != 0L } ?: detailsNetAmount
                chainPos = details.chainPosition
            } else {
                fee =
                    try {
                        amountToSats(currentWallet.calculateFee(tx))
                    } catch (_: Exception) {
                        null
                    }
                txWeight =
                    try {
                        tx.weight()
                    } catch (_: Exception) {
                        null
                    }
                netAmount = recomputeNetAmount(currentWallet, tx) ?: 0L
                chainPos = cachedTransaction.chainPosition
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
            } catch (_: Exception) {
            }

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
                } catch (_: Exception) {
                }
            }

            val isConfirmed = chainPos is ChainPosition.Confirmed
            val confirmationInfo =
                when (chainPos) {
                    is ChainPosition.Confirmed ->
                        ConfirmationTime(
                            height = chainPos.confirmationBlockTime.blockId.height,
                            timestamp = chainPos.confirmationBlockTime.confirmationTime,
                        )
                    is ChainPosition.Unconfirmed -> null
                }

            val txTimestamp =
                if (isConfirmed) {
                    confirmationInfo?.timestamp?.toLong().also {
                        activeWalletId?.let { wid -> secureStorage.removeTxFirstSeen(wid, txid) }
                    }
                } else {
                    val bdkLastSeen = (chainPos as? ChainPosition.Unconfirmed)?.timestamp?.toLong()
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
                isSelfTransfer = isSelfTransfer,
            )
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.w(TAG, "Failed to process tx $txid: ${e.message}")
            null
        }
    }

    private fun decorateTransactionDetails(
        details: TransactionDetails,
        transactionSwapDetails: Map<String, LiquidSwapDetails>,
        liquidSwapDetails: Map<String, LiquidSwapDetails>,
    ): TransactionDetails =
        details.copy(
            swapDetails =
                transactionSwapDetails[details.txid]
                    ?: inferBitcoinChainSwapSettlementDetails(details, liquidSwapDetails),
        )

    private fun getTransactionBuildCandidate(
        txid: String,
        cachedTransaction: CachedWalletTransaction,
        cachedDetails: TransactionDetails?,
        activeWalletId: String?,
    ): TransactionBuildCandidate {
        val chainPos = cachedTransaction.chainPosition
        val sortTimestamp =
            cachedDetails?.timestamp
                ?: when (chainPos) {
                    is ChainPosition.Confirmed -> chainPos.confirmationBlockTime.confirmationTime.toLong()
                    is ChainPosition.Unconfirmed -> {
                        activeWalletId?.let { secureStorage.getTxFirstSeen(it, txid) }
                            ?: chainPos.timestamp?.toLong()?.takeIf { it > 0L }
                            ?: (System.currentTimeMillis() / 1000L)
                    }
                }
        val sortHeight =
            cachedDetails?.confirmationTime?.height?.toLong()
                ?: when (chainPos) {
                    is ChainPosition.Confirmed -> chainPos.confirmationBlockTime.blockId.height.toLong()
                    is ChainPosition.Unconfirmed -> Long.MAX_VALUE
                }
        return TransactionBuildCandidate(
            txid = txid,
            cachedTransaction = cachedTransaction,
            cachedDetails = cachedDetails,
            sortTimestamp = sortTimestamp,
            sortHeight = sortHeight,
        )
    }

    private fun buildTransactionDetailsList(
        currentWallet: Wallet,
        activeWalletId: String?,
        onChunkLoaded: ((List<TransactionDetails>) -> Unit)? = null,
    ): List<TransactionDetails> {
        if (walletTransactionCache.isEmpty()) {
            refreshWalletTransactionCache(currentWallet)
        }
        val network = currentWallet.network()
        val transactionSwapDetails = activeWalletId?.let(secureStorage::getAllTransactionSwapDetails).orEmpty()
        val liquidSwapDetails = activeWalletId?.let(secureStorage::getAllLiquidSwapDetails).orEmpty()
        val descriptorCacheKey = getWalletTransactionDescriptorCacheKey(activeWalletId)
        val cachedConfirmedDetails =
            if (activeWalletId != null && descriptorCacheKey != null) {
                val confirmedTxids =
                    walletTransactionCache
                        .filterValues { it.chainPosition is ChainPosition.Confirmed }
                        .keys
                        .toList()
                electrumCache.loadConfirmedTransactionDetails(activeWalletId, descriptorCacheKey, confirmedTxids)
            } else {
                emptyMap()
            }
        val newlyBuiltConfirmedDetails = mutableListOf<TransactionDetails>()
        val orderedCandidates =
            walletTransactionCache
                .map { (txid, cachedTransaction) ->
                    val cachedDetails =
                        if (cachedTransaction.chainPosition is ChainPosition.Confirmed) {
                            cachedConfirmedDetails[txid].also { details ->
                                if (details == null) {
                                    activeWalletId?.let { secureStorage.removeTxFirstSeen(it, txid) }
                                }
                            }
                        } else {
                            null
                        }
                    getTransactionBuildCandidate(
                        txid = txid,
                        cachedTransaction = cachedTransaction,
                        cachedDetails = cachedDetails,
                        activeWalletId = activeWalletId,
                    )
                }.sortedWith(
                    compareByDescending<TransactionBuildCandidate> { it.sortTimestamp }
                        .thenByDescending { it.sortHeight },
                )
        val transactions = mutableListOf<TransactionDetails>()
        var lastPublishedCount = 0

        orderedCandidates.forEachIndexed { index, candidate ->
            val details =
                candidate.cachedDetails
                    ?: buildTransactionDetails(
                        currentWallet = currentWallet,
                        activeWalletId = activeWalletId,
                        txid = candidate.txid,
                        cachedTransaction = candidate.cachedTransaction,
                        network = network,
                    )?.also { builtDetails ->
                        if (builtDetails.isConfirmed) {
                            newlyBuiltConfirmedDetails += builtDetails
                        }
                    }

            details?.let { builtDetails ->
                transactions += decorateTransactionDetails(
                    details = builtDetails,
                    transactionSwapDetails = transactionSwapDetails,
                    liquidSwapDetails = liquidSwapDetails,
                )
                val builtCount = transactions.size
                val shouldPublishChunk =
                    onChunkLoaded != null &&
                        (
                            builtCount == TRANSACTION_HISTORY_INITIAL_CHUNK_SIZE ||
                                (
                                    builtCount > TRANSACTION_HISTORY_INITIAL_CHUNK_SIZE &&
                                        builtCount - lastPublishedCount >= TRANSACTION_HISTORY_CHUNK_SIZE
                                ) ||
                                index == orderedCandidates.lastIndex
                        )
                if (shouldPublishChunk) {
                    lastPublishedCount = builtCount
                    onChunkLoaded(
                        transactions.sortedByDescending { it.timestamp ?: Long.MAX_VALUE },
                    )
                }
            }
        }

        if (activeWalletId != null && descriptorCacheKey != null && newlyBuiltConfirmedDetails.isNotEmpty()) {
            electrumCache.putConfirmedTransactionDetails(activeWalletId, descriptorCacheKey, newlyBuiltConfirmedDetails)
        }

        return transactions.sortedByDescending { it.timestamp ?: Long.MAX_VALUE }
    }

    private fun hasWarmTransactionHistoryCache(
        currentWallet: Wallet,
        activeWalletId: String?,
    ): Boolean {
        if (activeWalletId.isNullOrBlank()) return false
        if (walletTransactionCache.isEmpty()) {
            refreshWalletTransactionCache(currentWallet)
        }
        val descriptorCacheKey = getWalletTransactionDescriptorCacheKey(activeWalletId) ?: return false
        val confirmedTxids =
            walletTransactionCache
                .filterValues { it.chainPosition is ChainPosition.Confirmed }
                .keys
                .toList()
        if (confirmedTxids.isEmpty()) return true
        val cachedConfirmedDetails =
            electrumCache.loadConfirmedTransactionDetails(activeWalletId, descriptorCacheKey, confirmedTxids)
        return cachedConfirmedDetails.size == confirmedTxids.size
    }

    private fun buildWalletStateChecksum(
        balanceSats: ULong,
        transactions: List<TransactionDetails>,
    ): WalletStateChecksum {
        val pendingIncomingSats =
            transactions
                .filter { !it.isConfirmed && it.amountSats > 0 }
                .sumOf { it.amountSats.toULong() }
        val pendingOutgoingSats =
            transactions
                .filter { !it.isConfirmed && it.amountSats < 0 }
                .sumOf { (-it.amountSats).toULong() }
        return WalletStateChecksum(
            balanceSats = balanceSats,
            pendingIncomingSats = pendingIncomingSats,
            pendingOutgoingSats = pendingOutgoingSats,
            txCount = transactions.size,
        )
    }

    private fun shouldRunIncrementalReconcile(): Boolean {
        val now = System.currentTimeMillis()
        return incrementalReconcileCounter >= INCREMENTAL_RECONCILE_INTERVAL ||
            now - lastIncrementalReconcileAtMs >= INCREMENTAL_RECONCILE_MAX_AGE_MS
    }

    private fun markIncrementalReconcileCompleted() {
        incrementalReconcileCounter = 0
        lastIncrementalReconcileAtMs = System.currentTimeMillis()
    }

    private fun recordIncrementalUpdateApplied() {
        incrementalReconcileCounter += 1
    }

    private fun updateWalletStateLightweight() {
        val currentWallet = wallet ?: run {
            updateWalletState()
            return
        }
        val previousState = _walletState.value
        val activeWalletId = secureStorage.getActiveWalletId()
        val shouldPreserveDerivedState = previousState.activeWallet?.id == activeWalletId
        val activeWallet: StoredWallet?
        val allWallets: List<StoredWallet>

        try {
            activeWallet = activeWalletId?.let { secureStorage.getWalletMetadata(it) }
            allWallets = secureStorage.getAllWallets()
        } catch (_: IllegalArgumentException) {
            _walletState.value =
                WalletState(
                    isInitialized = secureStorage.getWalletIds().isNotEmpty(),
                    wallets = emptyList(),
                    activeWallet = null,
                    error = "Wallet metadata is invalid",
                )
            return
        }

        try {
            val balance = currentWallet.balance()
            val lastAddress =
                try {
                    currentWallet.nextUnusedAddress(KeychainKind.EXTERNAL).address.toString()
                } catch (_: Exception) {
                    previousState.currentAddress
                }
            val lastSyncTime = activeWalletId?.let { secureStorage.getLastSyncTime(it) }
            val latestBlockHeight =
                try {
                    currentWallet.latestCheckpoint().height
                } catch (_: Exception) {
                    previousState.blockHeight
                }
            val currentAddressInfo =
                buildCurrentReceiveAddressInfo(
                    walletId = activeWalletId,
                    address = lastAddress,
                    fallback = previousState.currentAddressInfo,
                )

            _walletState.value =
                previousState.copy(
                    isInitialized = true,
                    wallets = allWallets,
                    activeWallet = activeWallet,
                    balanceSats = amountToSats(balance.total),
                    pendingIncomingSats = if (shouldPreserveDerivedState) previousState.pendingIncomingSats else 0UL,
                    pendingOutgoingSats = if (shouldPreserveDerivedState) previousState.pendingOutgoingSats else 0UL,
                    isTransactionHistoryLoading = true,
                    transactions = if (shouldPreserveDerivedState) previousState.transactions else emptyList(),
                    currentAddress = lastAddress,
                    currentAddressInfo = currentAddressInfo,
                    lastSyncTimestamp = lastSyncTime,
                    blockHeight = latestBlockHeight,
                    error = null,
                )
        } catch (_: Exception) {
            _walletState.value =
                previousState.copy(
                    wallets = allWallets,
                    activeWallet = activeWallet,
                    error = "Failed to update wallet state",
                )
        }
    }

    private fun scheduleDetailedTransactionRefresh(
        walletId: String,
        walletSnapshot: Wallet,
    ) {
        transactionRefreshJob?.cancel()
        transactionRefreshJob =
            repositoryScope.launch {
                try {
                    syncMutex.withLock {
                        if (!isActive || secureStorage.getActiveWalletId() != walletId || wallet !== walletSnapshot) {
                            return@withLock
                        }

                        val transactions =
                            buildTransactionDetailsList(walletSnapshot, walletId) { partialTransactions ->
                                if (!isActive || secureStorage.getActiveWalletId() != walletId || wallet !== walletSnapshot) {
                                    return@buildTransactionDetailsList
                                }
                                val visiblePartialTransactions =
                                    filterPendingReplacementTransactions(walletId, partialTransactions)
                                val partialState = _walletState.value
                                _walletState.value =
                                    partialState.copy(
                                        isTransactionHistoryLoading = true,
                                        transactions = visiblePartialTransactions,
                                        error = null,
                                    )
                            }
                        val visibleTransactions = filterPendingReplacementTransactions(walletId, transactions)
                        val currentState = _walletState.value
                        val checksum = buildWalletStateChecksum(currentState.balanceSats, visibleTransactions)

                        if (!isActive || secureStorage.getActiveWalletId() != walletId || wallet !== walletSnapshot) {
                            return@withLock
                        }

                        scheduleBitcoinTransactionSearchIndexReplace(walletId, visibleTransactions)

                        _walletState.value =
                            currentState.copy(
                                pendingIncomingSats = checksum.pendingIncomingSats,
                                pendingOutgoingSats = checksum.pendingOutgoingSats,
                                isTransactionHistoryLoading = false,
                                transactions = visibleTransactions,
                                error = null,
                            )
                        markIncrementalReconcileCompleted()
                        if (BuildConfig.DEBUG) {
                            Log.d(TAG, "Background transaction refresh completed with ${visibleTransactions.size} transactions")
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (BuildConfig.DEBUG) {
                        Log.w(TAG, "Background transaction refresh failed: ${e.message}")
                    }
                    if (secureStorage.getActiveWalletId() == walletId && wallet === walletSnapshot) {
                        _walletState.value = _walletState.value.copy(isTransactionHistoryLoading = false)
                    }
                }
            }
    }

    private fun updateWalletState() {
        val currentWallet = wallet
        val previousState = _walletState.value
        val activeWalletId = secureStorage.getActiveWalletId()
        val activeWallet: StoredWallet?
        val allWallets: List<StoredWallet>

        try {
            activeWallet = activeWalletId?.let { secureStorage.getWalletMetadata(it) }
            allWallets = secureStorage.getAllWallets()
        } catch (_: IllegalArgumentException) {
            _walletState.value =
                WalletState(
                    isInitialized = secureStorage.getWalletIds().isNotEmpty(),
                    wallets = emptyList(),
                    activeWallet = null,
                    error = "Wallet metadata is invalid",
                )
            return
        }

        if (currentWallet == null) {
            // Check if this is a watch address wallet (Electrum-only tracking)
            if (activeWalletId != null && secureStorage.hasWatchAddress(activeWalletId)) {
                val watchState = getWatchAddressState(activeWalletId, activeWallet, allWallets)
                scheduleBitcoinTransactionSearchIndexReplace(activeWalletId, watchState.transactions)
                _walletState.value =
                    watchState.copy(
                        isTransactionHistoryLoading = false,
                        isSyncing = previousState.isSyncing,
                        isFullSyncing = previousState.isFullSyncing,
                        syncProgress = previousState.syncProgress,
                    )
            } else {
                _walletState.value =
                    previousState.copy(
                        isInitialized = allWallets.isNotEmpty(),
                        wallets = allWallets,
                        activeWallet = activeWallet,
                        balanceSats = 0UL,
                        pendingIncomingSats = 0UL,
                        pendingOutgoingSats = 0UL,
                        isTransactionHistoryLoading = false,
                        transactions = emptyList(),
                        currentAddress = null,
                        currentAddressInfo = null,
                        lastSyncTimestamp = null,
                        blockHeight = null,
                        error = null,
                    )
            }
            return
        }

        try {
            val balance = currentWallet.balance()
            val transactions =
                buildTransactionDetailsList(
                    currentWallet = currentWallet,
                    activeWalletId = activeWalletId,
                )
            val visibleTransactions = filterPendingReplacementTransactions(activeWalletId, transactions)
            val stateChecksum = buildWalletStateChecksum(amountToSats(balance.total), visibleTransactions)
            if (BuildConfig.DEBUG) {
                Log.d(
                    TAG,
                    "Pending: incoming=${stateChecksum.pendingIncomingSats}, outgoing=${stateChecksum.pendingOutgoingSats}",
                )
            }

            // Get the next unused address (only reveals new if all revealed addresses are used)
            val lastAddress =
                try {
                    currentWallet.nextUnusedAddress(KeychainKind.EXTERNAL).address.toString()
                } catch (_: Exception) {
                    null
                }

            val lastSyncTime = activeWalletId?.let { secureStorage.getLastSyncTime(it) }
            val currentAddressInfo =
                buildCurrentReceiveAddressInfo(
                    walletId = activeWalletId,
                    address = lastAddress,
                )

            // Get the latest synced block height from BDK's local chain
            val latestBlockHeight =
                try {
                    currentWallet.latestCheckpoint().height
                } catch (_: Exception) {
                    null
                }

            scheduleBitcoinTransactionSearchIndexReplace(activeWalletId, visibleTransactions)

            _walletState.value =
                previousState.copy(
                    isInitialized = true,
                    wallets = allWallets,
                    activeWallet = activeWallet,
                    balanceSats = stateChecksum.balanceSats,
                    pendingIncomingSats = stateChecksum.pendingIncomingSats,
                    pendingOutgoingSats = stateChecksum.pendingOutgoingSats,
                    isTransactionHistoryLoading = false,
                    transactions = visibleTransactions,
                    currentAddress = lastAddress,
                    currentAddressInfo = currentAddressInfo,
                    lastSyncTimestamp = lastSyncTime,
                    blockHeight = latestBlockHeight,
                    error = null,
                )
            markIncrementalReconcileCompleted()
        } catch (_: Exception) {
            _walletState.value =
                previousState.copy(
                    wallets = allWallets,
                    activeWallet = activeWallet,
                    error = "Failed to update wallet state",
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

        if (existingState.transactions.isEmpty() && existingState.balanceSats == 0UL) {
            updateWalletState()
            return
        }

        try {
            val balance = currentWallet.balance()
            val activeWalletId = secureStorage.getActiveWalletId()

                val affectedTxids = mutableSetOf<String>()
                val droppedTxids = mutableSetOf<String>()
                for (event in events) {
                    when (event) {
                        is WalletEvent.TxConfirmed -> affectedTxids.add(event.txid.toString())
                        is WalletEvent.TxUnconfirmed -> affectedTxids.add(event.txid.toString())
                        is WalletEvent.TxReplaced -> {
                            event.conflicts.forEach { conflict ->
                                droppedTxids.add(conflict.txid.toString())
                                walletTransactionCache.remove(conflict.txid.toString())
                            }
                            affectedTxids.add(event.txid.toString())
                        }
                        is WalletEvent.TxDropped -> {
                            val txid = event.txid.toString()
                            droppedTxids.add(txid)
                            walletTransactionCache.remove(txid)
                        }
                        is WalletEvent.ChainTipChanged -> Unit
                    }
                }

            if (affectedTxids.size > 20) {
                updateWalletState()
                return
            }

            if (walletTransactionCache.isEmpty()) {
                refreshWalletTransactionCache(currentWallet)
            }

            if (affectedTxids.any { it !in walletTransactionCache }) {
                refreshWalletTransactionCache(currentWallet)
            }

            val network = currentWallet.network()
            val transactionSwapDetails = activeWalletId?.let(secureStorage::getAllTransactionSwapDetails).orEmpty()
            val liquidSwapDetails = activeWalletId?.let(secureStorage::getAllLiquidSwapDetails).orEmpty()
            val updatedTxDetails = mutableMapOf<String, TransactionDetails>()
            for (txid in affectedTxids) {
                val cachedTransaction = walletTransactionCache[txid] ?: continue
                buildTransactionDetails(
                    currentWallet = currentWallet,
                    activeWalletId = activeWalletId,
                    txid = txid,
                    cachedTransaction = cachedTransaction,
                    network = network,
                )?.let { details ->
                    updatedTxDetails[txid] =
                        decorateTransactionDetails(
                            details = details,
                            transactionSwapDetails = transactionSwapDetails,
                            liquidSwapDetails = liquidSwapDetails,
                        )
                }
            }

            if (affectedTxids.isNotEmpty() && updatedTxDetails.isEmpty()) {
                updateWalletState()
                return
            }

            val mergedTransactions =
                existingState.transactions
                    .filter { it.txid !in droppedTxids }
                    .map { existing ->
                        updatedTxDetails.remove(existing.txid) ?: existing
                    }
                    .toMutableList()
            mergedTransactions.addAll(updatedTxDetails.values)

            val sortedTransactions =
                filterPendingReplacementTransactions(
                    activeWalletId,
                    mergedTransactions.sortedByDescending { it.timestamp ?: Long.MAX_VALUE },
                )
            val hiddenTxids =
                mergedTransactions
                    .map { it.txid }
                    .toSet() - sortedTransactions.map { it.txid }.toSet()
            val checksum = buildWalletStateChecksum(amountToSats(balance.total), sortedTransactions)

            if (shouldRunIncrementalReconcile()) {
                val fullTransactions =
                    filterPendingReplacementTransactions(
                        activeWalletId,
                        buildTransactionDetailsList(
                            currentWallet = currentWallet,
                            activeWalletId = activeWalletId,
                        ),
                    )
                val fullChecksum = buildWalletStateChecksum(amountToSats(balance.total), fullTransactions)
                if (fullChecksum != checksum) {
                    SecureLog.w(TAG, "Incremental checksum mismatch. Falling back to full rebuild.")
                    updateWalletState()
                    return
                }
                markIncrementalReconcileCompleted()
            } else {
                recordIncrementalUpdateApplied()
            }

            val lastAddress =
                try {
                    currentWallet.nextUnusedAddress(KeychainKind.EXTERNAL).address.toString()
                } catch (_: Exception) {
                    existingState.currentAddress
                }
            val lastSyncTime = activeWalletId?.let { secureStorage.getLastSyncTime(it) }
            val latestBlockHeight =
                try {
                    currentWallet.latestCheckpoint().height
                } catch (_: Exception) {
                    existingState.blockHeight
                }
            val currentAddressInfo =
                buildCurrentReceiveAddressInfo(
                    walletId = activeWalletId,
                    address = lastAddress,
                    fallback = existingState.currentAddressInfo,
                )

            scheduleBitcoinTransactionSearchIndexUpsert(
                walletId = activeWalletId,
                transactions = sortedTransactions.filter { it.txid in affectedTxids },
                deletedTxids = droppedTxids + hiddenTxids,
            )

            _walletState.value =
                existingState.copy(
                    balanceSats = checksum.balanceSats,
                    pendingIncomingSats = checksum.pendingIncomingSats,
                    pendingOutgoingSats = checksum.pendingOutgoingSats,
                    isTransactionHistoryLoading = false,
                    transactions = sortedTransactions,
                    currentAddress = lastAddress,
                    currentAddressInfo = currentAddressInfo,
                    lastSyncTimestamp = lastSyncTime,
                    blockHeight = latestBlockHeight,
                )

            if (BuildConfig.DEBUG) {
                Log.d(
                    TAG,
                    "Incremental state update: ${affectedTxids.size} affected, ${droppedTxids.size} dropped",
                )
            }
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
        if (method == SecureStorage.SecurityMethod.NONE && secureStorage.clearWalletLocks()) {
            updateWalletState()
        }
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

    fun getRandomizePinPad(): Boolean {
        return secureStorage.getRandomizePinPad()
    }

    fun setRandomizePinPad(enabled: Boolean) {
        secureStorage.setRandomizePinPad(enabled)
    }

    // ==================== Duress PIN / Decoy Wallet ====================

    /**
     * Create a duress (decoy) wallet from a full import config.
     * Saves the wallet ID as the duress wallet and records the current active wallet as the real wallet.
     * Switches back to the real wallet after creation.
     */
    suspend fun createDuressWallet(
        config: WalletImportConfig,
    ): WalletResult<String> =
        withContext(Dispatchers.IO) {
            try {
                // A real wallet must exist before setting up duress — otherwise the
                // decoy wallet becomes the only (and visible) wallet in the main app.
                val currentActiveId =
                    secureStorage.getActiveWalletId()
                        ?: return@withContext WalletResult.Error("Add a wallet before setting up duress")

                // Record the current active wallet as the real wallet
                secureStorage.setRealWalletId(currentActiveId)

                // Import the wallet (this sets it as active)
                when (val result = importWallet(config.copy(network = WalletNetwork.BITCOIN))) {
                    is WalletResult.Error -> return@withContext WalletResult.Error(result.message)
                    is WalletResult.Success -> { /* continue */ }
                }

                // The new wallet is now active — get its ID
                val duressWalletId =
                    secureStorage.getActiveWalletId()
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
    suspend fun deleteDuressWallet(): WalletResult<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val duressWalletId = secureStorage.getDuressWalletId()
                if (duressWalletId != null) {
                    // Delete the wallet data and BDK database
                    secureStorage.deleteWallet(duressWalletId)
                    electrumCache.clearAllWalletActivityData()
                    deleteWalletDatabase(duressWalletId)
                    deleteLiquidWalletDatabase(duressWalletId)
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
    suspend fun switchToDuressWallet(): WalletResult<Unit> =
        withContext(Dispatchers.IO) {
            val duressWalletId =
                secureStorage.getDuressWalletId()
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
    suspend fun switchToRealWallet(): WalletResult<Unit> =
        withContext(Dispatchers.IO) {
            val realWalletId =
                secureStorage.getRealWalletId()
                    ?: return@withContext WalletResult.Error("No real wallet ID saved")

            switchWallet(realWalletId)
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

    fun clearCloakData() {
        secureStorage.clearCloakData()
    }

    fun setPendingIconAlias(alias: String) {
        secureStorage.setPendingIconAlias(alias)
    }

    /**
     * Result of [wipeAllData]: which discrete steps failed.
     *
     * The caller may use this to decide whether to retry, log, or warn the user
     * before continuing with process termination. Even on partial failure the
     * caller should still consider killing the process — leaving a half-wiped
     * app reachable is worse than a clean shutdown with logged residue.
     */
    data class WipeResult(
        val failedSteps: List<String>,
    ) {
        val success: Boolean
            get() = failedSteps.isEmpty()
    }

    /**
     * Wipe all wallet data: delete every wallet's BDK database, recursively
     * delete BDK/LWK/Spark on-disk state, clear the Electrum cache, wipe both
     * SharedPreferences (secure + regular), reset in-memory state, and verify
     * that the on-disk artifacts are actually gone.
     *
     * Each step runs in its own try/catch so a single failure cannot mask
     * subsequent steps. After the destructive phase a verification phase
     * confirms the secure prefs file, encrypted master key, and the BDK/LWK/
     * Spark directories are empty. The result lists every step that failed so
     * the caller can decide how to react before killing the process.
     */
    suspend fun wipeAllData(): WipeResult =
        withContext(Dispatchers.IO) {
            val failures = mutableListOf<String>()

            fun step(name: String, block: () -> Unit) {
                try {
                    block()
                } catch (e: Exception) {
                    failures += name
                    SecureLog.e(
                        TAG,
                        "Wipe step '$name' failed: ${e.message}",
                        e,
                        releaseMessage = "Secure wipe step failed",
                    )
                }
            }

            // Capture wallet ids before any prefs are cleared.
            val walletIds: List<String> =
                try {
                    secureStorage.getWalletIds()
                } catch (e: Exception) {
                    failures += "list-wallet-ids"
                    SecureLog.e(
                        TAG,
                        "Wipe step 'list-wallet-ids' failed: ${e.message}",
                        e,
                        releaseMessage = "Secure wipe step failed",
                    )
                    emptyList()
                }

            try {
                disconnect()
            } catch (e: Exception) {
                failures += "disconnect"
                SecureLog.e(
                    TAG,
                    "Wipe step 'disconnect' failed: ${e.message}",
                    e,
                    releaseMessage = "Secure wipe step failed",
                )
            }
            step("clear-loaded-wallet") { clearLoadedWallet() }

            step("delete-bdk-wallet-databases") {
                for (id in walletIds) {
                    deleteWalletDatabase(id)
                }
            }

            step("delete-bdk-dir") {
                if (bdkDbDir.exists() && !bdkDbDir.deleteRecursively()) {
                    error("bdkDbDir.deleteRecursively returned false")
                }
                bdkDbDir.mkdirs()
            }

            step("delete-lwk-dir") {
                if (lwkDbDir.exists() && !lwkDbDir.deleteRecursively()) {
                    error("lwkDbDir.deleteRecursively returned false")
                }
                lwkDbDir.mkdirs()
            }

            step("delete-spark-dir") {
                // Spark SDK keeps per-wallet state under filesDir/spark/<walletId>.
                // Without this the "full wipe" leaves rich Layer-2 metadata behind.
                if (sparkDbDir.exists() && !sparkDbDir.deleteRecursively()) {
                    error("sparkDbDir.deleteRecursively returned false")
                }
            }

            step("delete-sweep-temp-dir") {
                if (!cleanupSweepTempDatabases()) {
                    error("cleanupSweepTempDatabases returned false")
                }
            }

            step("delete-electrum-cache") {
                if (!electrumCache.deleteDatabaseFile()) {
                    error("ElectrumCache.deleteDatabaseFile returned false")
                }
            }

            step("wipe-secure-storage") { secureStorage.wipeAllData() }

            step("reset-in-memory-state") { _walletState.value = WalletState() }

            // Verification pass: confirm the destructive steps actually landed.
            // Failures here are reported even when the corresponding step
            // appeared to succeed, because file-system or Keystore races can
            // leave residue that the destructive APIs do not surface.
            verifyWipeResidue(walletIds).forEach { residue ->
                if (!failures.contains(residue)) failures += residue
            }

            val result = WipeResult(failures)
            if (!result.success) {
                SecureLog.e(
                    TAG,
                    "Secure wipe finished with residue: ${result.failedSteps}",
                    releaseMessage = "Secure wipe incomplete",
                )
            }
            result
        }

    private fun verifyWipeResidue(walletIds: List<String>): List<String> {
        val residue = mutableListOf<String>()
        fun check(name: String, predicate: () -> Boolean) {
            try {
                if (predicate()) residue += name
            } catch (e: Exception) {
                residue += name
                SecureLog.e(
                    TAG,
                    "Wipe verification '$name' failed: ${e.message}",
                    e,
                    releaseMessage = "Secure wipe verification failed",
                )
            }
        }

        val secureFile =
            File(appContext.applicationInfo.dataDir, "shared_prefs/ibis_secure_prefs.xml")
        check("residue-secure-prefs-file") { secureFile.exists() }

        val regularFile =
            File(appContext.applicationInfo.dataDir, "shared_prefs/ibis_prefs.xml")
        check("residue-regular-prefs-file") { regularFile.exists() }

        check("residue-master-key") {
            val keyStore = java.security.KeyStore.getInstance("AndroidKeyStore")
            keyStore.load(null)
            keyStore.containsAlias("_androidx_security_master_key_")
        }

        check("residue-biometric-key") {
            val keyStore = java.security.KeyStore.getInstance("AndroidKeyStore")
            keyStore.load(null)
            keyStore.containsAlias(SecureStorage.BIOMETRIC_KEY_ALIAS)
        }

        check("residue-bdk-dir") { bdkDirHasFiles() }
        check("residue-lwk-dir") { lwkDirHasFiles() }
        check("residue-spark-dir") { sparkDbDir.exists() && sparkDbDir.walk().any { it.isFile } }

        // Wallet ids should be unreachable after secure prefs are cleared.
        check("residue-wallet-ids") {
            try {
                secureStorage.getWalletIds().any { it in walletIds }
            } catch (_: Exception) {
                false
            }
        }
        return residue
    }

    private fun bdkDirHasFiles(): Boolean =
        bdkDbDir.exists() && bdkDbDir.walk().any { it.isFile }

    private fun lwkDirHasFiles(): Boolean =
        lwkDbDir.exists() && lwkDbDir.walk().any { it.isFile }

    fun close() {
        stopNotificationCollector()
        repositoryScope.cancel()
        clearLoadedWallet()
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
        }
    }

    sealed interface ConnectionEvent {
        data object ConnectionLost : ConnectionEvent
    }
}
