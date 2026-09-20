package github.aeonbtc.ibiswallet.data.repository

import android.content.Context
import android.os.SystemClock
import android.util.Base64
import android.util.Log
import github.aeonbtc.ibiswallet.BuildConfig
import github.aeonbtc.ibiswallet.R
import github.aeonbtc.ibiswallet.data.local.ElectrumCache
import github.aeonbtc.ibiswallet.data.local.SecureStorage
import github.aeonbtc.ibiswallet.data.model.AddressType
import github.aeonbtc.ibiswallet.data.model.BitcoinTxSource
import github.aeonbtc.ibiswallet.data.model.ConfirmationTime
import github.aeonbtc.ibiswallet.data.model.DryRunResult
import github.aeonbtc.ibiswallet.data.model.ElectrumConfig
import github.aeonbtc.ibiswallet.data.model.FeeEstimateSource
import github.aeonbtc.ibiswallet.data.model.FeeEstimates
import github.aeonbtc.ibiswallet.data.model.KeychainType
import github.aeonbtc.ibiswallet.data.model.Layer2Provider
import github.aeonbtc.ibiswallet.data.model.LightningNodeConfig
import github.aeonbtc.ibiswallet.data.model.LiquidSwapDetails
import github.aeonbtc.ibiswallet.data.model.LiquidSwapTxRole
import github.aeonbtc.ibiswallet.data.model.LiquidTxSource
import github.aeonbtc.ibiswallet.data.model.MultisigWalletConfig
import github.aeonbtc.ibiswallet.data.model.PsbtDetails
import github.aeonbtc.ibiswallet.data.model.PsbtSessionStatus
import github.aeonbtc.ibiswallet.data.model.PsbtSigningSession
import github.aeonbtc.ibiswallet.data.model.ReceiveAddressInfo
import github.aeonbtc.ibiswallet.data.model.Recipient
import github.aeonbtc.ibiswallet.data.model.SeedFormat
import github.aeonbtc.ibiswallet.data.model.SignedBoardFunding
import github.aeonbtc.ibiswallet.data.model.SilentPaymentPendingItem
import github.aeonbtc.ibiswallet.data.model.SilentPaymentUtxo
import github.aeonbtc.ibiswallet.data.model.SparkUnclaimedDeposit
import github.aeonbtc.ibiswallet.data.model.StoredWallet
import github.aeonbtc.ibiswallet.data.model.SwapDirection
import github.aeonbtc.ibiswallet.data.model.SwapService
import github.aeonbtc.ibiswallet.data.model.SyncProgress
import github.aeonbtc.ibiswallet.data.model.TransactionDetails
import github.aeonbtc.ibiswallet.data.model.TransactionSearchDocument
import github.aeonbtc.ibiswallet.data.model.TransactionSearchFilters
import github.aeonbtc.ibiswallet.data.model.TransactionSearchLayer
import github.aeonbtc.ibiswallet.data.model.TransactionSearchResult
import github.aeonbtc.ibiswallet.data.model.UtxoInfo
import github.aeonbtc.ibiswallet.data.model.WalletAddress
import github.aeonbtc.ibiswallet.data.model.WalletImportConfig
import github.aeonbtc.ibiswallet.data.model.WalletKind
import github.aeonbtc.ibiswallet.data.model.WalletLayer
import github.aeonbtc.ibiswallet.data.model.WalletNetwork
import github.aeonbtc.ibiswallet.data.model.WalletPolicyType
import github.aeonbtc.ibiswallet.data.model.WalletResult
import github.aeonbtc.ibiswallet.data.model.WalletState
import github.aeonbtc.ibiswallet.localization.AppLocale
import github.aeonbtc.ibiswallet.tor.CachingElectrumProxy
import github.aeonbtc.ibiswallet.tor.ElectrumNotification
import github.aeonbtc.ibiswallet.tor.SilentPaymentHistoryItem
import github.aeonbtc.ibiswallet.util.ArkBackupCrypto
import github.aeonbtc.ibiswallet.util.ArkWalletDataPack
import github.aeonbtc.ibiswallet.util.Bip137MessageSigner
import github.aeonbtc.ibiswallet.util.BitcoinSendPreparationCacheKey
import github.aeonbtc.ibiswallet.util.BitcoinSendPreparationState
import github.aeonbtc.ibiswallet.util.BitcoinUtils
import github.aeonbtc.ibiswallet.util.ElectrumSeedUtil
import github.aeonbtc.ibiswallet.util.MultisigWalletParser
import github.aeonbtc.ibiswallet.util.PsbtExportOptimizer
import github.aeonbtc.ibiswallet.util.QrFormatParser
import github.aeonbtc.ibiswallet.util.SecureLog
import github.aeonbtc.ibiswallet.util.SilentPayment
import github.aeonbtc.ibiswallet.util.SpeedUpTransactionErrors
import github.aeonbtc.ibiswallet.util.TofuTrustManager
import github.aeonbtc.ibiswallet.util.buildBitcoinTransactionSearchDocument
import github.aeonbtc.ibiswallet.util.buildMultiBitcoinSendPreparationKey
import github.aeonbtc.ibiswallet.util.buildSingleBitcoinSendPreparationKey
import github.aeonbtc.ibiswallet.util.findMaxExactSendAmount
import github.aeonbtc.ibiswallet.util.isTransactionInsufficientFundsError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
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
import org.bitcoindevkit.ChangeSpendPolicy
import org.bitcoindevkit.DerivationPath
import org.bitcoindevkit.Descriptor
import org.bitcoindevkit.DescriptorSecretKey
import org.bitcoindevkit.ElectrumClient
import org.bitcoindevkit.EvictedTx
import org.bitcoindevkit.FeeRate
import org.bitcoindevkit.FullScanScriptInspector
import org.bitcoindevkit.Input
import org.bitcoindevkit.KeychainKind
import org.bitcoindevkit.Mnemonic
import org.bitcoindevkit.Network
import org.bitcoindevkit.NetworkKind
import org.bitcoindevkit.OutPoint
import org.bitcoindevkit.Persister
import org.bitcoindevkit.Psbt
import org.bitcoindevkit.Script
import org.bitcoindevkit.SyncScriptInspector
import org.bitcoindevkit.Transaction
import org.bitcoindevkit.TxBuilder
import org.bitcoindevkit.TxOut
import org.bitcoindevkit.Txid
import org.bitcoindevkit.Wallet
import org.bitcoindevkit.WalletEvent
import org.json.JSONObject
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
        private const val SPARK_EXIT_BACKUP_DIR = "spark-exit-backup"
        private const val ARK_DB_DIR = "ark"
        private const val ARK_AUTO_BACKUP_DIR = "ark_auto_backup"
        private const val ARK_SESSION_DIR = "ark-session"
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
        private const val TAPROOT_ACTIVATION_HEIGHT = 709632
        // Frigate re-runs the whole historical scan on every (re)subscribe and
        // only reports mempool at progress=1.0, so never restart a live scan
        // from refresh paths — only when no push arrives within this window.
        private const val SP_SCAN_STALL_MS = 180_000L
        private const val SP_REFRESH_TIMEOUT_MS = 90_000L
        private const val SP_PENDING_ITEM_TTL_MS = 7 * 24 * 60 * 60_000L
        // Scan-logic generation: bump when scan semantics change so one
        // unskipped backfill re-examines history (missed outputs from older
        // logic must not be skipped as "already scanned"). Generation 2 =
        // the BIP0352/Label tweak fix (was: compressed pubkey preimage).
        private const val SP_SCAN_GENERATION = 2
        private const val SP_PENDING_RETRY_BASE_MS = 30_000L
        private const val SP_PENDING_RETRY_MAX_MS = 15 * 60_000L
        // Reorg window for SP receive/spend re-verification: matches the
        // -100 block scan overlap. Confirmed UTXOs/spends deeper than this
        // are considered stable and skipped to bound Electrum I/O.
        private const val SP_REORG_WINDOW_BLOCKS = 100L
        private const val MAX_RECEIVE_ADDRESS_SCAN_ATTEMPTS = 10_000
        private const val INCREMENTAL_RECONCILE_INTERVAL = 10
        private const val INCREMENTAL_RECONCILE_MAX_AGE_MS = 30 * 60_000L
        private const val TRANSACTION_HISTORY_INITIAL_CHUNK_SIZE = 25
        private const val TRANSACTION_HISTORY_CHUNK_SIZE = 100
        private const val BITCOIN_SOURCE_CHAIN_SWAP = BitcoinTxSource.CHAIN_SWAP
        /** BIP125 opt-in RBF signaling sequence. */
        private const val RBF_SIGNAL_SEQUENCE: UInt = 0xFFFFFFFDu
        /**
         * Board funding broadcast-race verification: presence polls after a
         * broadcast failure (Bark Esplora → our Electrum propagation can lag).
         */
        private const val BOARD_BROADCAST_VERIFY_ATTEMPTS = 3
        private const val BOARD_BROADCAST_VERIFY_RETRY_MS = 4_000L

        /**
         * True when a broadcast rejection means the server already holds the tx
         * (bitcoind/ElectrumX "already in block chain", "txn-already-known", …),
         * i.e. a lost broadcast race rather than a real failure.
         */
        internal fun isKnownTxBroadcastRejection(message: String?): Boolean {
            val msg = message.orEmpty()
            return msg.contains("already", ignoreCase = true) ||
                msg.contains("duplicate", ignoreCase = true)
        }

        /** Pattern to extract supported xpub/zpub keys from a descriptor string. */
        private val XPUB_PATTERN = """]([xzXZ]pub[a-zA-Z0-9]+)""".toRegex()

        const val SETH_TOR_HOST = "iuo6acfdicxhrovyqrekefh4rg2b7vgmzeeohc5cbwegawwhqpdxkgad.onion"
        const val BULL_BITCOIN_HOST = "electrum.bullbitcoin.com"
        const val FRIGATE_HOST = "frigate.2140.dev"
        private const val DEFAULT_ELECTRUM_PORT = 50002

        val DEFAULT_ELECTRUM_SERVERS =
            listOf(
                ElectrumConfig(
                    name = "SethForPrivacy (Tor)",
                    url = SETH_TOR_HOST,
                    port = DEFAULT_ELECTRUM_PORT,
                    useSsl = true,
                    useTor = true,
                ),
                ElectrumConfig(
                    name = "Bull Bitcoin",
                    url = BULL_BITCOIN_HOST,
                    port = DEFAULT_ELECTRUM_PORT,
                    useSsl = true,
                ),
                ElectrumConfig(
                    name = "Frigate (Silent Payments)",
                    url = FRIGATE_HOST,
                    port = DEFAULT_ELECTRUM_PORT,
                    useSsl = true,
                ),
            )
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

    private fun getWalletGapLimit(walletId: String?): Int =
        walletId
            ?.let(secureStorage::getWalletMetadata)
            ?.gapLimit
            ?: StoredWallet.DEFAULT_GAP_LIMIT

    private fun revealConfiguredGapLimit(
        currentWallet: Wallet,
        walletId: String?,
    ): Boolean {
        val targetIndex = (getWalletGapLimit(walletId).coerceAtLeast(1) - 1).toUInt()
        var revealed = false
        for (keychain in listOf(KeychainKind.EXTERNAL, KeychainKind.INTERNAL)) {
            try {
                if (currentWallet.revealAddressesTo(keychain, targetIndex).isNotEmpty()) {
                    revealed = true
                }
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.d(TAG, "Could not reveal $keychain gap limit: ${e.message}")
            }
        }
        if (revealed) {
            walletPersister?.let { currentWallet.persist(it) }
        }
        return revealed
    }

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
        val hiddenTxids = secureStorage.getHiddenBitcoinTransactionIds(walletId)
        return transactions.filterNot { it.txid in hiddenTxids }.map { transaction ->
            buildBitcoinTransactionSearchDocument(
                walletId = walletId,
                transaction = transaction,
                transactionLabel = transactionLabels[transaction.txid],
                addressLabel = transaction.address?.let(addressLabels::get),
            )
        }
    }

    private fun filterHiddenBitcoinTransactions(
        walletId: String?,
        transactions: List<TransactionDetails>,
    ): List<TransactionDetails> {
        val hiddenTxids = walletId?.let(secureStorage::getHiddenBitcoinTransactionIds).orEmpty()
        if (hiddenTxids.isEmpty()) return transactions
        return transactions.filterNot { it.txid in hiddenTxids }
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

    private fun localizedString(
        id: Int,
        vararg formatArgs: Any,
    ): String =
        AppLocale.createLocalizedContext(appContext, secureStorage.getAppLocale()).getString(id, formatArgs)

    private val speedUpErrorLocalizer =
        object : SpeedUpTransactionErrors.Localizer {
            override fun get(resId: Int): String = localizedString(resId)

            override fun get(
                resId: Int,
                vararg formatArgs: Any,
            ): String = localizedString(resId, *formatArgs)
        }

    private fun mapRbfError(e: Exception): String =
        SpeedUpTransactionErrors.mapRbfFailure(speedUpErrorLocalizer, e)

    private fun mapCpfpError(e: Exception): String =
        SpeedUpTransactionErrors.mapCpfpFailure(speedUpErrorLocalizer, e)

    private val electrumCache = ElectrumCache(context)

    // Directory for BDK wallet databases (persistent cache)
    private val bdkDbDir: File = File(context.filesDir, BDK_DB_DIR).apply { mkdirs() }
    private val lwkDbDir: File = File(context.filesDir, LWK_DB_DIR).apply { mkdirs() }
    private val sparkDbDir: File = File(context.filesDir, SPARK_DB_DIR)
    private val sparkExitBackupDir: File = File(context.filesDir, SPARK_EXIT_BACKUP_DIR)
    private val arkDbDir: File = File(context.filesDir, ARK_DB_DIR)
    private val arkAutoBackupDir: File = File(context.filesDir, ARK_AUTO_BACKUP_DIR)
    private val arkSessionDir: File = File(context.cacheDir, ARK_SESSION_DIR)
    private val sweepTempDbDir: File = File(context.cacheDir, SWEEP_TEMP_DB_DIR).apply { mkdirs() }

    init {
        if (!cleanupSweepTempDatabases() && BuildConfig.DEBUG) {
            Log.w(TAG, "Failed to clean sweep temp databases on startup")
        }
        ensureFrigateServerPresent()
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

    /**
     * Best-effort Spark SDK data removal (session DB + exit-state backup).
     * SecureStorage Spark keys are already purged by [SecureStorage.deleteWallet]
     * above; the SDK owns no keys outside its filesDir trees. Never throws —
     * wallet deletion must not fail on a locked/stale SDK dir.
     */
    private fun deleteSparkWalletData(walletId: String) {
        runCatching { File(sparkDbDir, walletId).deleteRecursively() }
            .onFailure {
                SecureLog.w(TAG, "Failed to delete Spark wallet data", it, releaseMessage = "Spark cleanup failed")
            }
        runCatching { File(sparkExitBackupDir, "$walletId.exitstate").delete() }
        runCatching { File(sparkExitBackupDir, "$walletId.exitstate.tmp").delete() }
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
        loadedWalletId = null
        walletPersister = null
        walletExternalDescriptor = null
        walletInternalDescriptor = null
        walletIsSingleKey = false
        stopSilentPaymentScan()
        spPendingRetryJob?.cancel()
        spPendingRetryJob = null
        silentPaymentKeys = null
        walletTransactionCache.clear()
        peekAddressStringCache.clear()
        scriptHashByKeychainIndex.clear()
        lastSubscribedScriptHashFingerprint = null
        incrementalReconcileCounter = 0
        lastIncrementalReconcileAtMs = 0L
    }

    suspend fun unloadWalletFromMemoryForLock() {
        walletLoadMutex.withLock {
            val previous = _walletState.value
            val walletId = previous.activeWallet?.id
            val cachedAddress =
                previous.currentAddress?.takeIf { it.isNotBlank() }
                    ?: walletId?.let { secureStorage.getL1ReceiveAddress(it) }
            if (walletId != null && !cachedAddress.isNullOrBlank()) {
                persistL1ReceiveAddress(walletId, cachedAddress)
            }
            clearLoadedWallet()
            // Keep last receive address so Receive paints instantly after unlock.
            _walletState.value =
                WalletState(
                    isInitialized = previous.isInitialized || cachedAddress != null,
                    wallets = previous.wallets,
                    activeWallet = previous.activeWallet,
                    currentAddress = cachedAddress,
                    currentAddressInfo =
                        buildCurrentReceiveAddressInfo(
                            walletId = walletId,
                            address = cachedAddress,
                            fallback = previous.currentAddressInfo,
                        ),
                )
        }
    }

    private fun replaceLoadedWallet(
        walletId: String,
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
        loadedWalletId = walletId
        walletPersister = persister
        walletExternalDescriptor = externalDescriptor
        walletInternalDescriptor = internalDescriptor
        walletIsSingleKey = isSingleKey
        walletTransactionCache.clear()
        peekAddressStringCache.clear()
        scriptHashByKeychainIndex.clear()
        lastSubscribedScriptHashFingerprint = null
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
                networkKind = network.toNetworkKind(),
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

    // Which wallet ID the loaded BDK wallet belongs to (null while unloaded).
    // State publishers use it to avoid emitting wallet A's data under wallet B's
    // metadata during a wallet switch.
    private var loadedWalletId: String? = null
    private var walletPersister: Persister? = null
    private var walletExternalDescriptor: Descriptor? = null
    private var walletInternalDescriptor: Descriptor? = null
    private var walletIsSingleKey: Boolean = false
    private var silentPaymentKeys: SilentPayment.ReceiverKeys? = null
    @Volatile private var silentPaymentsSupported: Boolean? = null
    private var subscribedSilentPaymentScanKeyHex: String? = null
    private var subscribedSilentPaymentSpendKeyHex: String? = null
    private var spSubscribedProxy: CachingElectrumProxy? = null
    private var spSubscribedAtMs: Long = 0L
    private var spScanProgress: Double = 0.0
    private var spLastPushMs: Long = 0L
    // Serializes every read-modify-write of the stored SP UTXO list. Push
    // handling, refresh, and local spends all run on IO threads and each does
    // slow network I/O mid-sequence — without this, concurrent saves silently
    // drop freshly discovered receives.
    private val spUtxoMutex = Mutex()
    private val silentPaymentBlockTimes = java.util.concurrent.ConcurrentHashMap<Int, Long>()
    private val coinControlOutpoints = ThreadLocal<Set<String>?>()
    private var electrumClient: ElectrumClient? = null
    private val walletTransactionCache = linkedMapOf<String, CachedWalletTransaction>()
    // Cache of (keychain, index) -> bech32 address string for the active wallet. Peek derivation
    // is deterministic but each call crosses a JNI boundary and is expensive on large wallets
    // (thousands of revealed addresses). Cleared on wallet unload/replace.
    private val peekAddressStringCache = java.util.concurrent.ConcurrentHashMap<Pair<KeychainKind, UInt>, String>()
    private val scriptHashByKeychainIndex = java.util.concurrent.ConcurrentHashMap<Pair<KeychainKind, UInt>, String>()
    private var lastSubscribedScriptHashFingerprint: String? = null
    private var incrementalReconcileCounter = 0
    private var lastIncrementalReconcileAtMs = 0L

    // Protocol-aware caching proxy — terminates SSL, intercepts blockchain.transaction.get
    // to serve from persistent cache, and consolidates all Electrum connections (BDK traffic,
    // script hash subscriptions, verbose tx queries) through a single upstream socket.
    private var cachingProxy: CachingElectrumProxy? = null
    private val connectionMutex = Mutex()

    // Wallet-load mutex — serializes load/switch/unload so the wallet, persister, and
    // descriptor fields can never be crossed between two wallets by concurrent loads
    // (e.g. post-unlock bootstrap racing a duress-mode wallet switch).
    private val walletLoadMutex = Mutex()

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
    private var notificationDebounceJob: Job? = null
    private var notificationSyncJob: Job? = null
    private var transactionRefreshJob: Job? = null
    private var bitcoinSearchIndexJob: Job? = null
    private var pendingBlockSyncJob: Job? = null
    private val lastSyncProgressPublishElapsedMs = java.util.concurrent.atomic.AtomicLong(0L)
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Tracks which script hashes are subscribed on the subscription socket.
    // Used to detect new addresses after sync (gap limit expansion) and
    // subscribe them without re-subscribing everything.
    private val subscribedScriptHashes = mutableSetOf<String>()
    private val silentPaymentScriptHashes = mutableSetOf<String>()

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
     * BDK Electrum client over the local CachingElectrumProxy.
     * Sets timeout/retry (new in bdk-android 3.0) to match Tor vs clearnet proxy sockets.
     */
    private fun createLocalElectrumClient(
        localPort: Int,
        useTor: Boolean,
    ): ElectrumClient {
        val timeoutSecs = if (useTor) 30u else 15u
        return ElectrumClient(
            url = "tcp://127.0.0.1:$localPort",
            socks5 = null,
            timeout = timeoutSecs.toUByte(),
            retry = 2u.toUByte(),
            validateDomain = false,
        )
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
                            java.net.InetSocketAddress(
                                "127.0.0.1",
                                github.aeonbtc.ibiswallet.tor.TorManager.socksPort(),
                            ),
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

    /** BIP125 opt-in RBF (nSequence = 0xFFFFFFFD) for ordinary sends when enabled. */
    private fun TxBuilder.applyOptInRbfIfEnabled(): TxBuilder =
        if (secureStorage.getRbfEnabled()) setExactSequence(RBF_SIGNAL_SEQUENCE) else this

    /** Force BIP125 opt-in RBF for replacements / CPFP / cancel (always on). */
    private fun TxBuilder.applyOptInRbf(): TxBuilder = setExactSequence(RBF_SIGNAL_SEQUENCE)

    /**
     * Exclude frozen UTXOs from automatic coin selection. Frozen status is an
     * app-level do-not-spend flag — without this filter, default coin selection
     * would silently spend frozen coins on any ordinary send.
     */
    private fun TxBuilder.applyFrozenUtxoFilter(): TxBuilder {
        val frozenOutpoints = frozenOutpointsForActiveWallet()
        return if (frozenOutpoints.isEmpty()) this else unspendable(frozenOutpoints)
    }

    private fun frozenOutpointsForActiveWallet(): List<OutPoint> {
        val walletId = secureStorage.getActiveWalletId() ?: return emptyList()
        val frozen = secureStorage.getFrozenUtxos(walletId)
        if (frozen.isEmpty()) return emptyList()
        return frozen.mapNotNull { ref ->
            val txid = ref.substringBefore(':', "")
            val vout = ref.substringAfter(':', "").toUIntOrNull() ?: return@mapNotNull null
            if (txid.isBlank()) return@mapNotNull null
            runCatching { OutPoint(Txid.fromString(txid), vout) }.getOrNull()
        }
    }

    private fun OutPoint.toFrozenRef(): String = "${txid}:$vout"

    private fun isFrozenOutpoint(
        outpoint: OutPoint,
        frozenRefs: Set<String>,
    ): Boolean = outpoint.toFrozenRef() in frozenRefs

    private fun frozenRefsForActiveWallet(): Set<String> {
        val walletId = secureStorage.getActiveWalletId() ?: return emptySet()
        return secureStorage.getFrozenUtxos(walletId)
    }

    /**
     * Apply default TxBuilder policies for ordinary sends.
     * @param preferChangeOnly when true, forces ONLY_CHANGE regardless of the setting
     *   (used by consolidate-fallback callers that already decided the policy).
     *   when null, follows the user's Consolidate Change toggle.
     */
    private fun TxBuilder.applySendDefaults(preferChangeOnly: Boolean? = null): TxBuilder {
        var builder =
            applyConfirmedOnlyFilter()
                .applyOptInRbfIfEnabled()
                .applyFrozenUtxoFilter()
        val useChangeOnly = preferChangeOnly ?: secureStorage.getConsolidateChange()
        if (useChangeOnly) {
            builder = builder.changePolicy(ChangeSpendPolicy.ONLY_CHANGE)
        }
        return applySilentPaymentUtxos(builder)
    }

    /**
     * Prefer spending change first; if that can't fund the send, fall back to normal
     * selection so the toggle never hard-blocks payments.
     */
    private fun <T> withConsolidateFallback(build: (preferChangeOnly: Boolean?) -> T): T {
        if (!secureStorage.getConsolidateChange()) {
            return build(null)
        }
        return try {
            build(true)
        } catch (e: Exception) {
            if (!e.isTransactionInsufficientFundsError()) {
                throw e
            }
            build(false)
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
            val spPsbt = signSilentPaymentInputs(psbt)
            if (isSigned) {
                // sign() auto-finalized; extract the signed tx with real witness data
                val signedTx = spPsbt.extractTx()
                val weight = signedTx.weight()
                return kotlin.math.ceil(weight.toDouble() / 4.0)
            }
            // Partially signed — try explicit finalize as backup
            try {
                val finalizeResult = spPsbt.finalize()
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
    private data class SignedTxResult(
        val tx: Transaction,
        val feeSats: ULong,
        val vsize: Double,
        val signedPsbt: Psbt,
    )

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
        val changeIsMine: Boolean = true,
    )

    private data class MultiRecipientOutputSummary(
        val totalRecipientAmountSats: ULong,
        val changeAmountSats: ULong?,
        val changeAddress: String?,
        val hasChange: Boolean,
        val changeIsMine: Boolean = true,
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
        feeOffsetSats: Long = 0,
        spWalletId: String? = null,
    ): SignedTxResult {
        // spWalletId pins silent-payment signing/records to the send flow's
        // wallet. Null reads the current wallet: only for flows that cannot
        // involve silent inputs (or dry-runs that never broadcast).
        // Package-aware fee target: rate * vsize + offset (offset accounts for a
        // CPFP parent's fee/vsize). Floored at ~1 sat/vB relay minimum.
        fun targetFeeFor(vsize: Double): ULong? {
            val base = BitcoinUtils.computeExactFeeSats(targetSatPerVb, vsize) ?: return null
            val adjusted = base.toLong() + feeOffsetSats
            val relayFloor = kotlin.math.ceil(1.0 * vsize).toLong()
            return adjusted.coerceAtLeast(relayFloor).toULong()
        }

        // Score against the package absolute fee target, not bare fee/vsize rate.
        // With feeOffsetSats > 0 the true child rate is above targetSatPerVb; scoring
        // |fee/vsize - rate| systematically prefers under-fee candidates.
        fun packageError(candidate: SignedTxResult): Double {
            val target = targetFeeFor(candidate.vsize)?.toDouble() ?: return Double.MAX_VALUE
            return kotlin.math.abs(candidate.feeSats.toDouble() - target)
        }

        fun bestCandidate(candidates: List<SignedTxResult>): SignedTxResult {
            // Prefer candidates that meet/exceed the package fee target; among those
            // (or among all if none meet) pick the closest absolute fee.
            val meeting =
                candidates.filter { c ->
                    val t = targetFeeFor(c.vsize)
                    t != null && c.feeSats >= t
                }
            val pool = meeting.ifEmpty { candidates }
            return pool.minByOrNull(::packageError) ?: pool.last()
        }

        // --- attempt 1: sign the initial PSBT ---
        wallet.sign(initialPsbt)
        val spSigned = signSilentPaymentInputs(initialPsbt, spWalletId)
        val tx = spSigned.extractTx()
        val bakedFee = try { spSigned.fee() } catch (_: Exception) { 0UL }
        val weight = tx.weight()
        val vsize = kotlin.math.ceil(weight.toDouble() / 4.0)
        val targetFee = targetFeeFor(vsize)
        val candidate1 = SignedTxResult(tx, bakedFee, vsize, spSigned)

        if (targetFee == null || targetFee == bakedFee) {
            return candidate1
        }

        // --- attempt 2: rebuild with corrected fee, re-sign ---
        val correctedPsbt = try {
            rebuildWithFee(targetFee)
        } catch (_: Exception) {
            null
        }
        if (correctedPsbt == null) {
            // Prefer the higher absolute fee among signed candidates when package-aware
            // correction failed — never lock in under-package pass-1 if we can avoid it.
            return candidate1
        }

        wallet.sign(correctedPsbt)
        val spSigned2 = signSilentPaymentInputs(correctedPsbt, spWalletId)
        val tx2 = spSigned2.extractTx()
        val bakedFee2 = try { spSigned2.fee() } catch (_: Exception) { 0UL }
        val weight2 = tx2.weight()
        val vsize2 = kotlin.math.ceil(weight2.toDouble() / 4.0)
        val targetFee2 = targetFeeFor(vsize2)
        val candidate2 = SignedTxResult(tx2, bakedFee2, vsize2, spSigned2)

        // If the corrected fee still matches the baked fee for this vsize, we're done
        if (targetFee2 == null || targetFee2 == bakedFee2) {
            return candidate2
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
                val spSigned3 = signSilentPaymentInputs(correctedPsbt3, spWalletId)
                val tx3 = spSigned3.extractTx()
                val bakedFee3 = try { spSigned3.fee() } catch (_: Exception) { 0UL }
                val weight3 = tx3.weight()
                val vsize3 = kotlin.math.ceil(weight3.toDouble() / 4.0)
                val targetFee3 = targetFeeFor(vsize3)
                val candidate3 = SignedTxResult(tx3, bakedFee3, vsize3, spSigned3)

                if (targetFee3 == null || targetFee3 == bakedFee3) {
                    return candidate3
                }

                // Still oscillating — pick the closest to the package fee target
                return bestCandidate(listOf(candidate1, candidate2, candidate3))
            } catch (_: Exception) {
                // Fall through to pick best of first two
            }
        }

        // Oscillation between attempt 1 and 2 — pick closer to package fee target
        return bestCandidate(listOf(candidate1, candidate2))
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
        val extendedPrivateKey: String? = null, // xprv/zprv import (account-level)
        val watchAddress: String? = null, // Single watched address
        val multisigConfig: MultisigWalletConfig? = null,
        val localCosignerKeyMaterial: String? = null,
    ) {
        /** Redact sensitive fields to prevent accidental logging of key material. */
        override fun toString(): String =
            "WalletKeyMaterial(hasMnemonic=${mnemonic != null}, hasXpub=${extendedPublicKey != null}, " +
                "isWatchOnly=$isWatchOnly, hasPassphrase=${passphrase != null}, hasPrivateKey=${privateKey != null}, " +
                "hasExtendedPrivateKey=${extendedPrivateKey != null}, " +
                "hasWatchAddress=${watchAddress != null}, hasMultisig=${multisigConfig != null}, " +
                "hasLocalCosigner=${localCosignerKeyMaterial != null})"
    }

    /**
     * Get the key material (mnemonic and/or extended public key) for a wallet
     * For full wallets: returns both mnemonic and derived xpub
     * For watch-only wallets: returns only xpub
     * For WIF wallets: returns the private key
     * For xprv/zprv imports: returns extended private key (and derived xpub when possible)
     * For watch address wallets: returns the watched address
     */
    fun getKeyMaterial(walletId: String): WalletKeyMaterial? {
        val duressWalletId = secureStorage.getDuressWalletId()
        val inDuress = duressWalletId != null && secureStorage.getActiveWalletId() == duressWalletId
        if (inDuress && walletId != duressWalletId) return null
        if (!inDuress && duressWalletId != null && walletId == duressWalletId) return null
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

        // Extended key: public (watch-only) or private (xprv/zprv import)
        val extendedKey = secureStorage.getExtendedKey(walletId)
        if (extendedKey != null) {
            if (isExtendedPrivateKeyInput(extendedKey)) {
                return WalletKeyMaterial(
                    mnemonic = null,
                    extendedPublicKey = null,
                    isWatchOnly = false,
                    extendedPrivateKey = extendedKey,
                )
            }
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
            SeedFormat.BIP39 -> bip39SeedCanonical(mnemonic, passphrase)
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
    ): String =
        storedWallet.inputDerivationPath(
            change = keychain == KeychainKind.INTERNAL,
            index = index.toLong(),
        )

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

        val bdkNetworkKind = network.toBdkNetworkKind()
        val mnemonicObj = Mnemonic.fromString(mnemonic)
        val descriptorSecretKey =
            DescriptorSecretKey(
                networkKind = bdkNetworkKind,
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
                        networkKind = bdkNetworkKind,
                    )
                AddressType.SEGWIT ->
                    Descriptor.newBip84(
                        secretKey = descriptorSecretKey,
                        keychainKind = KeychainKind.EXTERNAL,
                        networkKind = bdkNetworkKind,
                    )
                AddressType.TAPROOT ->
                    Descriptor.newBip86(
                        secretKey = descriptorSecretKey,
                        keychainKind = KeychainKind.EXTERNAL,
                        networkKind = bdkNetworkKind,
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
            val bdkNetworkKind = network.toBdkNetworkKind()
            val mnemonicObj = Mnemonic.fromString(mnemonic)
            val descriptorSecretKey =
                DescriptorSecretKey(
                    networkKind = bdkNetworkKind,
                    mnemonic = mnemonicObj,
                    password = passphrase,
                )
            val descriptor =
                when (addressType) {
                    AddressType.LEGACY -> Descriptor.newBip44(descriptorSecretKey, KeychainKind.EXTERNAL, bdkNetworkKind)
                    AddressType.SEGWIT -> Descriptor.newBip84(descriptorSecretKey, KeychainKind.EXTERNAL, bdkNetworkKind)
                    AddressType.TAPROOT -> Descriptor.newBip86(descriptorSecretKey, KeychainKind.EXTERNAL, bdkNetworkKind)
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
            walletLoadMutex.withLock {
            try {
                if (config.network != WalletNetwork.BITCOIN) {
                    return@withContext WalletResult.Error(BitcoinUtils.UNSUPPORTED_NON_MAINNET_MESSAGE)
                }
                val bdkNetwork = config.network.toBdkNetwork()
                var trimmedKey = config.keyMaterial.trim()
                BitcoinUtils.unsupportedNonMainnetReason(trimmedKey)?.let {
                    return@withContext WalletResult.Error(it)
                }
                BitcoinUtils.unsupportedNestedSegwitReason(trimmedKey)?.let {
                    return@withContext WalletResult.Error(it)
                }
                // ColdCard / Specter / generic wallet JSON pasted into the text field:
                // run the same conversion as the QR import path to extract key material.
                if (trimmedKey.startsWith("{") && !MultisigWalletParser.looksLikeMultisig(trimmedKey)) {
                    trimmedKey =
                        try {
                            QrFormatParser.parseWalletQr(appContext, trimmedKey, config.addressType)
                        } catch (e: Exception) {
                            return@withContext WalletResult.Error(e.message ?: "Unsupported wallet JSON")
                        }
                }
                val multisigConfig =
                    config.multisigConfig ?: if (MultisigWalletParser.looksLikeMultisig(trimmedKey)) {
                        MultisigWalletParser.parse(trimmedKey)
                    } else {
                        null
                    }
                val isWatchOnly = isWatchOnlyInput(trimmedKey)
                val isXprv = !isWatchOnly && isExtendedPrivateKeyInput(trimmedKey)
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
                        isWatchOnly || isXprv -> fingerprint
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
                    loadedWalletId = walletId
                    secureStorage.setActiveWalletId(walletId)
                    updateWalletState()
                    return@withContext WalletResult.Success(Unit)
                }

                // Create wallet with persistent SQLite storage
                val persister = Persister.newSqlite(getWalletDbPath(walletId))

                val bdkNetworkKind = bdkNetwork.toNetworkKind()
                if (isWif) {
                    // Single-key WIF wallet — uses Wallet.createSingle (no change descriptor)
                    val descriptor = createDescriptorFromWif(trimmedKey, config.addressType, bdkNetworkKind)
                    val importedWallet =
                        Wallet.createSingle(
                            descriptor = descriptor,
                            network = bdkNetwork,
                            persister = persister,
                        )
                    replaceLoadedWallet(
                        walletId = walletId,
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
                                    bdkNetworkKind,
                                    resolvedFingerprint,
                                    config.customDerivationPath,
                                )
                            isXprv ->
                                createExtendedPrivateKeyDescriptors(
                                    trimmedKey,
                                    config.addressType,
                                    bdkNetworkKind,
                                    resolvedFingerprint,
                                    config.customDerivationPath,
                                )
                            electrumSeedType != null ->
                                createDescriptorsFromElectrumSeed(
                                    mnemonic = config.keyMaterial,
                                    passphrase = config.passphrase,
                                    seedType = electrumSeedType,
                                    networkKind = bdkNetworkKind,
                                    customPath = config.customDerivationPath,
                                    addressType = config.addressType,
                                )
                            else ->
                                createDescriptorsFromMnemonic(
                                    mnemonic = config.keyMaterial,
                                    passphrase = config.passphrase,
                                    addressType = config.addressType,
                                    networkKind = bdkNetworkKind,
                                    customPath = config.customDerivationPath,
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
                                val multipathDescriptor = Descriptor(stripped, bdkNetworkKind)
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
                        walletId = walletId,
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

                revealConfiguredGapLimit(wallet!!, walletId)

                // Save key material with wallet ID
                if (isWif) {
                    secureStorage.savePrivateKey(walletId, config.keyMaterial.trim())
                } else if (isWatchOnly || isXprv) {
                    secureStorage.saveExtendedKey(walletId, trimmedKey)
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
            } // walletLoadMutex
        }

    private fun importMultisigWallet(
        walletId: String,
        importConfig: WalletImportConfig,
        multisigConfig: MultisigWalletConfig,
        network: Network,
    ): WalletResult<Unit> {
        val localCosignerMaterial = importConfig.localCosignerKeyMaterial?.trim()?.takeIf { it.isNotBlank() }
        val hasPrivateDescriptor =
            BitcoinUtils.isExtendedPrivateKeyMaterial(localCosignerMaterial ?: "") ||
                BitcoinUtils.isExtendedPrivateKeyMaterial(multisigConfig.externalDescriptor)
        val (externalDescriptor, internalDescriptor) =
            createMultisigDescriptors(multisigConfig, localCosignerMaterial, network.toNetworkKind(), strictOverride = true)
        val persister = Persister.newSqlite(getWalletDbPath(walletId))
        val importedWallet =
            Wallet(
                descriptor = externalDescriptor,
                changeDescriptor = internalDescriptor,
                network = network,
                persister = persister,
            )
        replaceLoadedWallet(
            walletId = walletId,
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
                    resolveLocalCosignerFingerprint(multisigConfig, localCosignerMaterial),
            )
        secureStorage.saveWalletMetadata(storedWallet)
        revealConfiguredGapLimit(wallet!!, walletId)
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
        networkKind: NetworkKind,
        strictOverride: Boolean = false,
    ): Pair<Descriptor, Descriptor> {
        val overridePair =
            localCosignerMaterial
                ?.takeIf { MultisigWalletParser.looksLikeMultisig(it) }
                ?.let { MultisigWalletParser.normalizeDescriptorPair(it) }
                ?.takeIf { (rawExternal, rawInternal) ->
                    // The override may embed xprv keys (signing-capable). Bind it
                    // to the quorum by comparing its public-normalized form, but
                    // build BDK with the raw pair so private keys are retained.
                    val normalizedPublic =
                        MultisigWalletParser.normalizePrivateKeysToPublic("$rawExternal\n$rawInternal")
                            ?.let { MultisigWalletParser.normalizeDescriptorPair(it) }
                            ?: return@takeIf false
                    MultisigWalletParser.overrideMatchesConfig(
                        normalizedPublic.first,
                        normalizedPublic.second,
                        config,
                    )
                }
        if (overridePair == null && strictOverride && !localCosignerMaterial.isNullOrBlank()) {
            throw IllegalArgumentException("Local signer does not match this multisig quorum")
        }
        // Defense in depth: config pairs are validated at parse time, but a
        // mismatched persisted config must never silently route change away.
        val descriptorPair =
            overridePair
                ?: (config.externalDescriptor to config.internalDescriptor)
                    .takeIf { MultisigWalletParser.descriptorsMatch(it.first, it.second) }
                ?: throw IllegalArgumentException("Multisig change descriptor does not match receive descriptor")
        return Descriptor(descriptorPair.first, networkKind) to Descriptor(descriptorPair.second, networkKind)
    }

    /**
     * Identify which quorum cosigner holds the attached local signer key.
     * Only fingerprints whose key material in [localCosignerMaterial] is
     * actually private ([xprv/zprv]) and listed in [config] qualify — plain
     * substring matching could misfire on base58 text. Returns null for
     * watch-only wallets or ambiguous material. Never throws.
     */
    private fun resolveLocalCosignerFingerprint(
        config: MultisigWalletConfig,
        localCosignerMaterial: String?,
    ): String? {
        if (localCosignerMaterial.isNullOrBlank()) return null
        return runCatching {
            val privateFingerprints =
                MultisigWalletParser.privateKeyFingerprints(localCosignerMaterial)
            val configFingerprints = config.cosigners.map { it.fingerprint.lowercase() }.toSet()
            privateFingerprints.intersect(configFingerprints).singleOrNull()
        }.getOrNull()
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
        walletLoadMutex.withLock {
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
                loadedWalletId = walletId
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
    }

    /**
     * Create a standalone remote Lightning wallet. It has no local seed or BDK state;
     * its spending authority is the configured LND macaroon or NWC capability.
     */
    suspend fun createLightningNodeWallet(
        name: String,
        config: LightningNodeConfig,
    ): WalletResult<String> = withContext(Dispatchers.IO) {
        walletLoadMutex.withLock {
            try {
                require(name.isNotBlank()) { "Wallet name is required" }
                require(config.isConfigured) { "Incomplete Lightning Node connection" }
                val walletId = UUID.randomUUID().toString()
                val storedWallet = StoredWallet(
                    id = walletId,
                    name = name.trim(),
                    addressType = AddressType.SEGWIT,
                    derivationPath = "lightning_node",
                    isWatchOnly = true,
                    network = WalletNetwork.BITCOIN,
                    walletKind = WalletKind.LIGHTNING_NODE,
                )
                secureStorage.saveWalletMetadata(storedWallet)
                secureStorage.setLightningNodeConfig(walletId, config)
                secureStorage.setLayer2ProviderForWallet(walletId, Layer2Provider.LIGHTNING)
                secureStorage.setActiveLayer(walletId, WalletLayer.LAYER2.name)
                secureStorage.setNeedsFullSync(walletId, false)
                clearLoadedWallet()
                loadedWalletId = walletId
                secureStorage.setActiveWalletId(walletId)
                updateWalletState()
                WalletResult.Success(walletId)
            } catch (e: Exception) {
                WalletResult.Error("Lightning Node wallet creation failed: ${e.message ?: e.javaClass.simpleName}", e)
            }
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

                walletLoadMutex.withLock {
                    val previousActiveId = secureStorage.getActiveWalletId()
                    // Set as active
                    secureStorage.setActiveWalletId(walletId)

                    // Clear script hash cache - different wallet has different addresses
                    clearScriptHashCache()

                    // Load the wallet
                    when (val loadResult = loadWalletByIdLocked(walletId)) {
                        is WalletResult.Success -> WalletResult.Success(Unit)
                        is WalletResult.Error -> {
                            // Roll back active id and try to restore the previous wallet
                            // so a failed switch doesn't leave a half-loaded target active.
                            if (previousActiveId != null && previousActiveId != walletId) {
                                secureStorage.setActiveWalletId(previousActiveId)
                                clearScriptHashCache()
                                loadWalletByIdLocked(previousActiveId)
                            } else {
                                clearLoadedWallet()
                                updateWalletState()
                            }
                            return@withContext loadResult
                        }
                    }
                }

                WalletResult.Success(Unit)
            } catch (e: Exception) {
                WalletResult.Error(e.message ?: "Failed to switch wallet", e)
            }
        }

    /**
     * Load a specific wallet by ID.
     * Caller MUST hold [walletLoadMutex] — see [loadWallet] / [switchWallet].
     */
    private suspend fun loadWalletByIdLocked(walletId: String): WalletResult<Unit> =
        withContext(Dispatchers.IO) {
            fun failLoad(message: String): WalletResult.Error {
                clearLoadedWallet()
                updateWalletState()
                return WalletResult.Error(message)
            }
            try {
                val storedWallet =
                    secureStorage.getWalletMetadata(walletId)
                        ?: return@withContext failLoad("Wallet not found")

                val bdkNetwork = storedWallet.network.toBdkNetwork()
                val bdkNetworkKind = bdkNetwork.toNetworkKind()
                clearLoadedWallet()
                loadedWalletId = walletId
                // Paint last receive address immediately while BDK opens (confirmed after load).
                paintCachedReceiveAddress(walletId, storedWallet)

                // Watch address wallets have no BDK wallet — tracked via Electrum only
                if (secureStorage.hasWatchAddress(walletId)) {
                    updateWalletState()
                    return@withContext WalletResult.Success(Unit)
                }

                // Remote-provider-only wallets have no BDK wallet.
                if (
                    storedWallet.walletKind == WalletKind.LIGHTNING_NODE ||
                    storedWallet.derivationPath == "liquid_ct"
                ) {
                    updateWalletState()
                    return@withContext WalletResult.Success(Unit)
                }

                if (storedWallet.policyType == WalletPolicyType.MULTISIG) {
                    val multisigConfig =
                        secureStorage.getMultisigWalletConfig(walletId)
                            ?: return@withContext failLoad("No multisig config found")
                    val localCosignerMaterial = secureStorage.getLocalCosignerKeyMaterial(walletId)
                    val (externalDescriptor, internalDescriptor) =
                        createMultisigDescriptors(multisigConfig, localCosignerMaterial, bdkNetworkKind)
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
                    refreshSilentPaymentKeys(storedWallet)
                    wallet?.let { revealConfiguredGapLimit(it, walletId) }
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
                            ?: return@withContext failLoad("No private key found")
                    val descriptor = createDescriptorFromWif(wif, storedWallet.addressType, bdkNetworkKind)

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
                    // For BIP39 wallets with a custom derivation path: credentials kept
                    // in scope so a descriptor mismatch can fall back to the standard
                    // path (see below).
                    var bip39FallbackCredentials: Pair<String, String?>? = null
                    val storedCustomPath = customDerivationPath(storedWallet)
                    var (externalDescriptor, internalDescriptor) =
                        when {
                            secureStorage.hasExtendedKey(walletId) -> {
                                val extendedKey =
                                    secureStorage.getExtendedKey(walletId)
                                        ?: return@withContext failLoad("No extended key found")
                                if (isExtendedPrivateKeyInput(extendedKey)) {
                                    createExtendedPrivateKeyDescriptors(
                                        extendedKey,
                                        storedWallet.addressType,
                                        bdkNetworkKind,
                                        storedWallet.masterFingerprint,
                                        storedCustomPath,
                                    )
                                } else {
                                    createWatchOnlyDescriptors(
                                        extendedKey,
                                        storedWallet.addressType,
                                        bdkNetworkKind,
                                        storedWallet.masterFingerprint,
                                        storedCustomPath,
                                    )
                                }
                            }
                            else -> {
                                val mnemonic =
                                    secureStorage.getMnemonic(walletId)
                                        ?: return@withContext failLoad("No mnemonic found")
                                val passphrase = secureStorage.getPassphrase(walletId)

                                when (storedWallet.seedFormat) {
                                    SeedFormat.ELECTRUM_STANDARD ->
                                        createDescriptorsFromElectrumSeed(
                                            mnemonic,
                                            passphrase,
                                            ElectrumSeedUtil.ElectrumSeedType.STANDARD,
                                            bdkNetworkKind,
                                            storedCustomPath,
                                            storedWallet.addressType,
                                        )
                                    SeedFormat.ELECTRUM_SEGWIT ->
                                        createDescriptorsFromElectrumSeed(
                                            mnemonic,
                                            passphrase,
                                            ElectrumSeedUtil.ElectrumSeedType.SEGWIT,
                                            bdkNetworkKind,
                                            storedCustomPath,
                                            storedWallet.addressType,
                                        )
                                    else -> {
                                        if (storedCustomPath != null) {
                                            bip39FallbackCredentials = mnemonic to passphrase
                                        }
                                        createDescriptorsFromMnemonic(
                                            mnemonic,
                                            passphrase,
                                            storedWallet.addressType,
                                            bdkNetworkKind,
                                            customPath = storedCustomPath,
                                        )
                                    }
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
                                    val fallbackCredentials = bip39FallbackCredentials
                                    if (fallbackCredentials != null) {
                                        // Wallets imported before custom derivation paths were
                                        // honored were actually derived at the standard path.
                                        // Retry with standard descriptors and repair the stored
                                        // path on success.
                                        val (stdExternal, stdInternal) =
                                            createDescriptorsFromMnemonic(
                                                fallbackCredentials.first,
                                                fallbackCredentials.second,
                                                storedWallet.addressType,
                                                bdkNetworkKind,
                                            )
                                        val legacyWallet =
                                            try {
                                                Wallet.load(stdExternal, stdInternal, persister)
                                            } catch (_: Exception) {
                                                throw e
                                            }
                                        externalDescriptor = stdExternal
                                        internalDescriptor = stdInternal
                                        secureStorage.saveWalletMetadata(
                                            storedWallet.copy(
                                                derivationPath = storedWallet.addressType.defaultPath,
                                            ),
                                        )
                                        return@run legacyWallet
                                    }
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

                refreshSilentPaymentKeys(storedWallet)

                val loadedWallet = wallet
                loadedWallet?.let { revealConfiguredGapLimit(it, walletId) }
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
                // Leave memory unloaded so state publishers don't publish half-built
                // descriptors under a wallet id that never finished loading.
                clearLoadedWallet()
                updateWalletState()
                WalletResult.Error(e.message ?: "Failed to load wallet", e)
            }
        }

    /**
     * Create descriptors from mnemonic based on address type.
     * When [customPath] is provided, the account key is derived at that path
     * instead of the standard BIP44/84/86 path.
     */
    private fun createDescriptorsFromMnemonic(
        mnemonic: String,
        passphrase: String?,
        addressType: AddressType,
        networkKind: NetworkKind,
        customPath: String? = null,
    ): Pair<Descriptor, Descriptor> {
        val mnemonicObj = Mnemonic.fromString(mnemonic)
        val descriptorSecretKey =
            DescriptorSecretKey(
                networkKind = networkKind,
                mnemonic = mnemonicObj,
                password = passphrase,
            )

        if (!customPath.isNullOrBlank()) {
            return createCustomPathDescriptors(
                descriptorSecretKey = descriptorSecretKey,
                customPath = customPath,
                addressType = addressType,
                networkKind = networkKind,
            )
        }

        return when (addressType) {
            AddressType.LEGACY -> {
                // BIP44 for Legacy P2PKH
                val external =
                    Descriptor.newBip44(
                        secretKey = descriptorSecretKey,
                        keychainKind = KeychainKind.EXTERNAL,
                        networkKind = networkKind,
                    )
                val internal =
                    Descriptor.newBip44(
                        secretKey = descriptorSecretKey,
                        keychainKind = KeychainKind.INTERNAL,
                        networkKind = networkKind,
                    )
                Pair(external, internal)
            }
            AddressType.SEGWIT -> {
                // BIP84 for Native SegWit P2WPKH
                val external =
                    Descriptor.newBip84(
                        secretKey = descriptorSecretKey,
                        keychainKind = KeychainKind.EXTERNAL,
                        networkKind = networkKind,
                    )
                val internal =
                    Descriptor.newBip84(
                        secretKey = descriptorSecretKey,
                        keychainKind = KeychainKind.INTERNAL,
                        networkKind = networkKind,
                    )
                Pair(external, internal)
            }
            AddressType.TAPROOT -> {
                // BIP86 for Taproot P2TR
                val external =
                    Descriptor.newBip86(
                        secretKey = descriptorSecretKey,
                        keychainKind = KeychainKind.EXTERNAL,
                        networkKind = networkKind,
                    )
                val internal =
                    Descriptor.newBip86(
                        secretKey = descriptorSecretKey,
                        keychainKind = KeychainKind.INTERNAL,
                        networkKind = networkKind,
                    )
                Pair(external, internal)
            }
        }
    }

    /**
     * Create descriptors for a BIP39 mnemonic at a user-supplied account derivation
     * path. The account key is derived at [customPath] (minus any trailing
     * non-hardened change segment), then extended with wildcard change branches
     * (0 for external, 1 for internal) — the same convention as the standard
     * BIP44/84/86 builders.
     */
    private fun createCustomPathDescriptors(
        descriptorSecretKey: DescriptorSecretKey,
        customPath: String,
        addressType: AddressType,
        networkKind: NetworkKind,
    ): Pair<Descriptor, Descriptor> {
        val accountPath = customAccountPath(customPath)
        val accountXprv = descriptorSecretKey.derive(DerivationPath(accountPath)).toString()
        val function =
            when (addressType) {
                AddressType.LEGACY -> "pkh"
                AddressType.SEGWIT -> "wpkh"
                AddressType.TAPROOT -> "tr"
            }
        return Pair(
            Descriptor("$function($accountXprv/0/*)", networkKind),
            Descriptor("$function($accountXprv/1/*)", networkKind),
        )
    }

    /**
     * Normalize a user-entered derivation path to the account-level path.
     * A trailing non-hardened `0`/`1` segment is treated as the change branch
     * (matching the default paths, e.g. m/84'/0'/0'/0) and stripped.
     * Throws IllegalArgumentException on malformed paths so bad input fails
     * loudly instead of silently deriving the wrong wallet.
     */
    private fun customAccountPath(rawPath: String): String =
        BitcoinUtils.bip39AccountDerivationPath(rawPath)

    /**
     * Returns the custom derivation path stored for this wallet, or null when
     * the stored path is a sentinel/default that uses a dedicated descriptor builder.
     */
    private fun customDerivationPath(storedWallet: StoredWallet): String? {
        val path = storedWallet.derivationPath.trim()
        if (path.isBlank()) return null
        if (path == "single" || path == "liquid_ct" || path == "lightning_node" || path == "multisig") {
            return null
        }
        val defaultPath = storedWallet.defaultDerivationPath()
        return if (
            BitcoinUtils.bip39AccountDerivationPath(path) ==
            BitcoinUtils.bip39AccountDerivationPath(defaultPath)
        ) {
            null
        } else {
            path
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
        networkKind: NetworkKind,
        customPath: String? = null,
        addressType: AddressType? = null,
    ): Pair<Descriptor, Descriptor> {
        val seed = ElectrumSeedUtil.mnemonicToSeed(mnemonic, passphrase)
        val (externalStr, internalStr) =
            ElectrumSeedUtil.buildDescriptorStrings(
                seed = seed,
                seedType = seedType,
                customPath = customPath,
                addressType = addressType,
            )
        return Pair(Descriptor(externalStr, networkKind), Descriptor(internalStr, networkKind))
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
     * True when the input is a bare or origin-prefixed extended PRIVATE key
     * (xprv/zprv). Full descriptors are handled by the descriptor parser instead.
     */
    private fun isExtendedPrivateKeyInput(input: String): Boolean {
        val bare = parseKeyOrigin(input.trim()).bareKey
        return bare.startsWith("xprv") || bare.startsWith("zprv")
    }

    /**
     * Create spend-capable descriptors from an extended private key (xprv/zprv).
     * Mirrors [createWatchOnlyDescriptors] but embeds the private key so the
     * resulting wallet can sign. zprv is re-encoded to xprv version bytes.
     */
    private fun createExtendedPrivateKeyDescriptors(
        extendedKey: String,
        addressType: AddressType,
        networkKind: NetworkKind,
        masterFingerprint: String? = null,
        customPath: String? = null,
    ): Pair<Descriptor, Descriptor> {
        val parsed = parseKeyOrigin(extendedKey.trim())
        val fingerprint = parsed.fingerprint ?: masterFingerprint
        val originPath = overrideOriginPath(parsed.derivationPath, customPath)
        validateKeyScriptTypeConsistency(parsed.bareKey, originPath, addressType)
        val xprvKey = BitcoinUtils.convertToXprv(parsed.bareKey)
        val keyWithOrigin = BitcoinUtils.buildKeyWithOrigin(xprvKey, fingerprint, originPath, addressType)
        val (externalStr, internalStr) = BitcoinUtils.buildDescriptorStrings(keyWithOrigin, addressType)
        return Pair(Descriptor(externalStr, networkKind), Descriptor(internalStr, networkKind))
    }

    /**
     * Reject key/origin combinations that would silently derive a valid-but-wrong
     * wallet: SLIP-132 versions imply a script type, and a BIP purpose (44'/84'/86')
     * in the origin path must match the selected address type.
     */
    private fun validateKeyScriptTypeConsistency(
        bareKey: String,
        originPath: String?,
        addressType: AddressType,
    ) {
        if ((bareKey.startsWith("zpub") || bareKey.startsWith("zprv")) && addressType != AddressType.SEGWIT) {
            throw IllegalArgumentException(
                "${bareKey.take(4)} is a Native SegWit key — select the SegWit address type",
            )
        }
        val purpose =
            originPath
                ?.split('/')
                ?.firstOrNull()
                ?.removeSuffix("'")
                ?.toIntOrNull()
        if (purpose == 44 || purpose == 84 || purpose == 86) {
            val expected =
                when (addressType) {
                    AddressType.LEGACY -> 44
                    AddressType.SEGWIT -> 84
                    AddressType.TAPROOT -> 86
                }
            if (purpose != expected) {
                throw IllegalArgumentException(
                    "Origin path m/$purpose'/... does not match the selected address type (expected m/$expected'/...)",
                )
            }
        }
    }

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
    private fun overrideOriginPath(
        parsedOriginPath: String?,
        customPath: String?,
    ): String? {
        if (customPath.isNullOrBlank()) return parsedOriginPath
        return BitcoinUtils.originPathFromDerivationPath(customPath)
    }

    private fun createWatchOnlyDescriptors(
        extendedKey: String,
        addressType: AddressType,
        networkKind: NetworkKind,
        masterFingerprint: String? = null,
        customPath: String? = null,
    ): Pair<Descriptor, Descriptor> {
        val input = extendedKey.trim()

        val descriptorPrefixes = listOf("pkh(", "wpkh(", "tr(")
        val isFullDescriptor = descriptorPrefixes.any { input.lowercase().startsWith(it) }

        if (isFullDescriptor) {
            return parseFullDescriptor(input, networkKind)
        }

        val parsed = parseKeyOrigin(input)
        val bareKey = parsed.bareKey
        val fingerprint = parsed.fingerprint ?: masterFingerprint
        val originPath = overrideOriginPath(parsed.derivationPath, customPath)

        validateKeyScriptTypeConsistency(bareKey, originPath, addressType)

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

        val external = Descriptor(externalStr, networkKind)
        val internal = Descriptor(internalStr, networkKind)
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
        networkKind: NetworkKind,
    ): Pair<Descriptor, Descriptor> {
        val trimmed = BitcoinUtils.stripDescriptorChecksum(descriptor)

        // BIP 389 multipath descriptor: contains <0;1> syntax for combined receive/change paths
        // e.g. "wpkh([73c5da0a/84'/0'/0']xpub6.../<0;1>/*)"
        if (BitcoinUtils.isBip389Multipath(trimmed)) {
            val multipathDescriptor = Descriptor(trimmed, networkKind)
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
        return Pair(Descriptor(externalStr, networkKind), Descriptor(internalStr, networkKind))
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
        networkKind: NetworkKind,
    ): Descriptor {
        // Uncompressed-pubkey WIF keys ("5...") are only standard for Legacy P2PKH.
        // wpkh()/tr() with an uncompressed pubkey produces a non-standard script
        // that no other wallet would derive — reject instead of silently creating it.
        if (!isWifCompressed(wif) && addressType != AddressType.LEGACY) {
            throw IllegalArgumentException(
                "Uncompressed WIF keys can only be used with the Legacy address type",
            )
        }
        val descriptorStr =
            when (addressType) {
                AddressType.LEGACY -> "pkh($wif)"
                AddressType.SEGWIT -> "wpkh($wif)"
                AddressType.TAPROOT -> "tr($wif)"
            }
        return Descriptor(descriptorStr, networkKind)
    }

    private inline fun <T> withSelectedUtxos(
        selectedUtxos: List<UtxoInfo>?,
        block: () -> T,
    ): T {
        val previous = coinControlOutpoints.get()
        coinControlOutpoints.set(selectedUtxos?.map { it.outpoint }?.toSet())
        try {
            return block()
        } finally {
            if (previous == null) {
                coinControlOutpoints.remove()
            } else {
                coinControlOutpoints.set(previous)
            }
        }
    }

    /**
     * Load the active wallet from secure storage
     */
    suspend fun loadWallet(): WalletResult<Unit> =
        withContext(Dispatchers.IO) {
            walletLoadMutex.withLock {
                // Read the active wallet ID under the lock so a concurrent wallet
                // switch can't leave us loading a wallet that is no longer active.
                val activeWalletId =
                    secureStorage.getActiveWalletId()
                        ?: return@withContext WalletResult.Error("No active wallet")

                loadWalletByIdLocked(activeWalletId)
            }
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
                    resetSilentPaymentsCapability()
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
                        client = createLocalElectrumClient(localPort, useTor)
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
                        client = createLocalElectrumClient(localPort, useTor)
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
                        client = createLocalElectrumClient(localPort, useTor)
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
                    resetSilentPaymentsCapability()
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
                        client = createLocalElectrumClient(localPort, useTor)
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
                        client = createLocalElectrumClient(localPort, useTor)
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
                        client = createLocalElectrumClient(localPort, useTor)
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
        if (_walletState.value.transactions.isEmpty()) {
            _walletState.value = _walletState.value.copy(isTransactionHistoryLoading = true)
        }
    }

    /**
     * Rebuild or resume transaction history when balance is known but the list is still empty.
     * Common after sync cancels in-flight hydration on flaky Electrum connections.
     */
    private fun ensureTransactionHistoryHydrated() {
        val currentWallet = wallet ?: return
        val walletId = secureStorage.getActiveWalletId() ?: return
        val state = _walletState.value
        if (state.activeWallet?.id != walletId || state.transactions.isNotEmpty()) return
        if (transactionRefreshJob?.isActive == true) return

        if (walletTransactionCache.isEmpty()) {
            try {
                refreshWalletTransactionCache(currentWallet)
            } catch (_: Exception) {
                return
            }
        }
        if (walletTransactionCache.isEmpty()) {
            _walletState.value = state.copy(isTransactionHistoryLoading = false)
            return
        }

        if (hasWarmTransactionHistoryCache(currentWallet, walletId)) {
            repositoryScope.launch {
                try {
                    syncMutex.withLock {
                        if (secureStorage.getActiveWalletId() != walletId || wallet !== currentWallet) return@withLock
                        updateWalletState()
                    }
                } catch (e: Exception) {
                    if (BuildConfig.DEBUG) {
                        Log.w(TAG, "Warm-cache transaction hydration failed: ${e.message}")
                    }
                    if (secureStorage.getActiveWalletId() == walletId && wallet === currentWallet) {
                        scheduleDetailedTransactionRefresh(walletId, currentWallet)
                    }
                }
            }
            return
        }

        scheduleDetailedTransactionRefresh(walletId, currentWallet)
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

                // Provider-only wallets have no BDK wallet — their own repository owns sync.
                if (wallet == null) {
                    val meta = secureStorage.getWalletMetadata(activeWalletId)
                    if (
                        meta?.walletKind == WalletKind.LIGHTNING_NODE ||
                        meta?.derivationPath == "liquid_ct"
                    ) {
                        return@withContext WalletResult.Success(Unit)
                }
            }

            val currentWallet = wallet ?: return@withContext WalletResult.Error("Wallet not initialized")
            val client = electrumClient ?: return@withContext WalletResult.Error("Not connected to Electrum server")
            // Snapshot ownership so a concurrent switch/import cannot persist this
            // wallet into another wallet's SQLite or publish its balance under the new name.
            val syncWalletId = loadedWalletId
            val syncWallet = currentWallet
            if (syncWalletId == null || syncWalletId != activeWalletId) {
                return@withContext WalletResult.Error("Wallet changed during sync")
            }

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
                ensureTransactionHistoryHydrated()
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

                val revealedGapLimitAddresses = revealConfiguredGapLimit(currentWallet, activeWalletId)

                // Pre-check: skip sync if no new block AND no script hash changes.
                // blockHeadersPop() is free (local queue), script hash check is one
                // round-trip per sampled address. Together they make background syncs
                // nearly free when nothing has changed.
                val newBlockDetected = hasNewBlock(client)
                if (!force &&
                    !revealedGapLimitAddresses &&
                    !newBlockDetected &&
                    scriptHashStatusCache.isNotEmpty()
                ) {
                    val hasChanges =
                        cachingProxy
                            ?.checkForScriptHashChanges(scriptHashStatusCache)
                            ?: true // No proxy — sync to be safe
                    if (!hasChanges) {
                        secureStorage.saveLastSyncTime(activeWalletId, System.currentTimeMillis())
                        refreshSilentPaymentWalletState()
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

                cancelTransactionRefreshForSync()

                if (BuildConfig.DEBUG) Log.d(TAG, "Starting quick sync (batch=$currentBatchSize)")
                val hadPendingTransactions = _walletState.value.hasPendingBitcoinTransactions()

                lastSyncProgressPublishElapsedMs.set(0L)
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
                                    publishThrottledSyncProgress(
                                        SyncProgress(current = current, total = total),
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

                // Never write or publish if ownership changed mid-sync
                if (loadedWalletId != syncWalletId ||
                    wallet !== syncWallet ||
                    secureStorage.getActiveWalletId() != syncWalletId
                ) {
                    if (BuildConfig.DEBUG) Log.d(TAG, "Abandoning sync apply — wallet ownership changed")
                    return@withContext WalletResult.Error("Wallet changed during sync")
                }

                // Always persist — chain tip updates even when no tx events
                walletPersister?.let { syncWallet.persist(it) }
                var postSyncWallet = syncWallet
                if (shouldVerifyPendingConfirmations) {
                    if (reloadWalletFromDatabase()) {
                        postSyncWallet = wallet ?: syncWallet
                    } else if (loadedWalletId == syncWalletId && wallet === syncWallet) {
                        wallet = syncWallet
                    }
                }

                // Clear syncing state so the UI dialog dismisses promptly.
                // The post-processing (updateWalletState, cache refresh) runs after
                // and the balance/transactions update seamlessly in the background.
                if (loadedWalletId == syncWalletId) {
                    _walletState.value = _walletState.value.copy(isSyncing = false, syncProgress = null)
                }

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
                    } else {
                        refreshSilentPaymentWalletState()
                        if (BuildConfig.DEBUG) {
                            Log.d(TAG, "Quick sync: no BDK changes, refreshed silent payment state")
                        }
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
                ensureTransactionHistoryHydrated()
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
            val fullSyncWalletId = loadedWalletId
            val fullSyncWallet = currentWallet
            if (fullSyncWalletId == null || fullSyncWalletId != activeWalletId) {
                return@withContext WalletResult.Error("Wallet changed during sync")
            }
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

                    lastSyncProgressPublishElapsedMs.set(0L)
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
                                        publishThrottledSyncProgress(
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
                                // No persisted baseline (fresh wallet / derivation change):
                                // every status counts as changed, so subscribing first
                                // is pure overhead — the full scan fetches everything.
                                // Some servers (Frigate forwards each subscribe to its
                                // backend) are very slow here; never stall the scan.
                                val persistedBaseline =
                                    runCatching { electrumCache.loadScriptHashStatuses(scriptHashCacheWalletId()) }
                                        .getOrDefault(emptyMap())
                                val currentStatuses =
                                    if (persistedBaseline.isEmpty()) {
                                        if (BuildConfig.DEBUG) {
                                            Log.d(
                                                TAG,
                                                "No persisted script-hash baseline — skipping pre-subscribe for ${allScriptHashes.size} scripts",
                                            )
                                        }
                                        emptyMap()
                                    } else {
                                        withTimeoutOrNull(120_000) {
                                            proxy.subscribeScriptHashes(allScriptHashes)
                                        } ?: run {
                                            if (BuildConfig.DEBUG) {
                                                Log.w(TAG, "Pre-sync subscribe timed out — scanning without cache assist")
                                            }
                                            emptyMap()
                                        }
                                    }
                                proxy.setValidStatuses(currentStatuses)

                                if (BuildConfig.DEBUG) {
                                    val persistedStatuses = electrumCache.loadScriptHashStatuses(scriptHashCacheWalletId())
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
                            val walletGapLimit = getWalletGapLimit(activeWalletId).toULong()
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

                    // Never apply/persist if ownership changed mid-scan
                    if (loadedWalletId != fullSyncWalletId ||
                        wallet !== fullSyncWallet ||
                        secureStorage.getActiveWalletId() != fullSyncWalletId
                    ) {
                        if (BuildConfig.DEBUG) Log.d(TAG, "Abandoning full sync apply — wallet ownership changed")
                        return@withContext WalletResult.Error("Wallet changed during sync")
                    }

                    // Apply update with events
                    _walletState.value =
                        _walletState.value.copy(
                            syncProgress = SyncProgress(status = "Applying updates..."),
                        )
                    val events = fullSyncWallet.applyUpdateEvents(update)
                    if (BuildConfig.DEBUG) Log.d(TAG, "Full scan events: ${events.size} changes detected")

                    // Persist changes to database
                    _walletState.value =
                        _walletState.value.copy(
                            syncProgress = SyncProgress(status = "Saving to database..."),
                        )
                    walletPersister?.let { fullSyncWallet.persist(it) }
                    var postSyncWallet = fullSyncWallet

                    // BDK's in-memory UTXO/keychain index can be stale after
                    // applyUpdateEvents when the full scan revealed new addresses.
                    // Reloading from the persisted database forces a full rebuild
                    // so that balance() matches the actual transaction set.
                    if (events.isNotEmpty()) {
                        refreshWalletTransactionCache(fullSyncWallet)
                        val preReloadBalance = try { amountToSats(fullSyncWallet.balance().total) } catch (_: Exception) { null }
                        if (reloadWalletFromDatabase()) {
                            postSyncWallet = wallet ?: fullSyncWallet
                            refreshWalletTransactionCache(postSyncWallet)
                            if (BuildConfig.DEBUG) {
                                val postBalance = try { amountToSats(postSyncWallet.balance().total) } catch (_: Exception) { null }
                                if (preReloadBalance != postBalance) {
                                    Log.w(TAG, "Full scan reload corrected balance: $preReloadBalance -> $postBalance")
                                }
                            }
                        } else if (loadedWalletId == fullSyncWalletId && wallet === fullSyncWallet) {
                            wallet = fullSyncWallet
                        }
                    }

                    // Mark full sync as complete - future syncs will be quick
                    secureStorage.setNeedsFullSync(activeWalletId, false)
                    revealConfiguredGapLimit(postSyncWallet, activeWalletId)

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
                } finally {
                    ensureTransactionHistoryHydrated()
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
        val transactionSources = secureStorage.getAllTransactionSources(walletId)

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
                    val swapDetails =
                        details.swapDetails
                            ?: inferBitcoinChainSwapSettlementDetails(details, liquidSwapDetails)
                    val source =
                        transactionSources[txid]
                            ?: transactionSources.entries
                                .firstOrNull { it.key.equals(txid, ignoreCase = true) }
                                ?.value
                    details.copy(
                        swapDetails = swapDetails,
                        isSwapHistory = swapDetails != null || isBitcoinCenterSwapSource(source),
                    )
                }
            }?.sortedWith(
                compareBy<TransactionDetails> { it.isConfirmed } // unconfirmed first
                    .thenByDescending { it.confirmationTime?.height ?: 0U },
            ) // then by height descending
                ?: emptyList()
        val visibleTransactions = filterHiddenBitcoinTransactions(walletId, transactions)

        return WalletState(
            isInitialized = true,
            wallets = allWallets,
            activeWallet = activeWallet,
            balanceSats = confirmed,
            pendingIncomingSats = if (unconfirmed > 0) unconfirmed.toULong() else 0UL,
            pendingOutgoingSats = if (unconfirmed < 0) (-unconfirmed).toULong() else 0UL,
            transactions = visibleTransactions,
            currentAddress = address,
            currentAddressInfo = buildCurrentReceiveAddressInfo(walletId, address),
            silentPaymentAddress = silentPaymentKeys?.address,
            silentPaymentsSupported = silentPaymentsSupported,
            canReceiveSilentPayments = canReceiveSilentPayments(activeWallet),
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

    /** Show last persisted receive address before BDK finishes opening. */
    private fun paintCachedReceiveAddress(
        walletId: String,
        storedWallet: StoredWallet,
    ) {
        val cached =
            if (secureStorage.hasWatchAddress(walletId)) {
                secureStorage.getWatchAddress(walletId)
            } else {
                secureStorage.getL1ReceiveAddress(walletId)
            }?.takeIf { it.isNotBlank() } ?: return
        val previous = _walletState.value
        val sameWallet = previous.activeWallet?.id == walletId
        val allWallets =
            try {
                secureStorage.getAllWallets()
            } catch (_: Exception) {
                previous.wallets
            }
        _walletState.value =
            previous.copy(
                isInitialized = true,
                wallets = allWallets,
                activeWallet = storedWallet,
                // Never carry another wallet's balances/history onto the cached paint.
                balanceSats = if (sameWallet) previous.balanceSats else 0UL,
                pendingIncomingSats = if (sameWallet) previous.pendingIncomingSats else 0UL,
                pendingOutgoingSats = if (sameWallet) previous.pendingOutgoingSats else 0UL,
                transactions = if (sameWallet) previous.transactions else emptyList(),
                isTransactionHistoryLoading = !sameWallet || previous.isTransactionHistoryLoading,
                currentAddress = cached,
                currentAddressInfo =
                    buildCurrentReceiveAddressInfo(
                        walletId = walletId,
                        address = cached,
                        fallback =
                            previous.currentAddressInfo?.takeIf {
                                sameWallet && it.address == cached
                            },
                    ),
                silentPaymentAddress =
                    if (sameWallet) previous.silentPaymentAddress else silentPaymentKeys?.address,
                silentPaymentsSupported = silentPaymentsSupported,
                canReceiveSilentPayments = canReceiveSilentPayments(storedWallet),
                lastSyncTimestamp =
                    if (sameWallet) {
                        previous.lastSyncTimestamp
                    } else {
                        secureStorage.getLastSyncTime(walletId)
                    },
                blockHeight = if (sameWallet) previous.blockHeight else null,
                error = null,
            )
    }

    private fun persistL1ReceiveAddress(
        walletId: String?,
        address: String?,
    ) {
        if (walletId.isNullOrBlank() || address.isNullOrBlank()) return
        secureStorage.setL1ReceiveAddress(walletId, address)
    }

    /**
     * Next external receive address that is not reserved by a user label.
     * Skipped labeled indices are marked used in BDK so they stay reserved.
     */
    private fun resolveCurrentReceiveAddress(
        currentWallet: Wallet,
        walletId: String,
    ): String? {
        val labels = secureStorage.getAllAddressLabels(walletId)
        var markedReserved = false
        repeat(MAX_RECEIVE_ADDRESS_SCAN_ATTEMPTS) {
            val address =
                try {
                    currentWallet.nextUnusedAddress(KeychainKind.EXTERNAL).address.toString()
                } catch (_: Exception) {
                    return null
                }
            if (!labels.containsKey(address)) {
                if (markedReserved) {
                    walletPersister?.let { currentWallet.persist(it) }
                }
                return address
            }
            findExternalAddressIndex(currentWallet, address)?.let { index ->
                if (currentWallet.markUsed(KeychainKind.EXTERNAL, index)) {
                    markedReserved = true
                }
            }
        }
        if (markedReserved) {
            walletPersister?.let { currentWallet.persist(it) }
        }
        return null
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
                        Log.d(TAG, "Refreshed full script hash cache with ${scriptHashStatusCache.size} non-empty entries")
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
                if (BuildConfig.DEBUG) Log.d(TAG, "Refreshed script hash cache with ${scriptHashStatusCache.size} non-empty entries")
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.w(TAG, "Failed to refresh script hash cache: ${e.message}")
        }
    }

    private fun hasScriptHashStatusChanges(
        currentStatuses: Map<String, String?>,
        persistedStatuses: Map<String, String?>,
    ): Boolean {
        if (persistedStatuses.isEmpty()) {
            return currentStatuses.values.any { !it.isNullOrBlank() }
        }
        persistedStatuses.forEach { (scriptHash, persistedStatus) ->
            if (currentStatuses[scriptHash] != persistedStatus) return true
        }
        return currentStatuses.any { (scriptHash, currentStatus) ->
            !currentStatus.isNullOrBlank() && persistedStatuses[scriptHash] != currentStatus
        }
    }

    /**
     * Get a sample of script hashes from revealed addresses.
     * Includes:
     * - The current receive address — most likely to receive funds
     * - The last few revealed addresses from both keychains (high-index tail)
     * This ensures the pre-check detects incoming txs on the address shown to the user,
     * even if it's an earlier index not in the tail sample window.
     */
    private fun getSampleScriptHashes(currentWallet: Wallet): List<String> {
        val hashSet = linkedSetOf<String>() // preserve insertion order, deduplicate
        try {
            // Always include the current receive address — this is the address shown
            // on the receive screen and the most likely to get incoming payments.
            secureStorage.getActiveWalletId()?.let { walletId ->
                resolveCurrentReceiveAddress(currentWallet, walletId)?.let { address ->
                    try {
                        hashSet.add(
                            computeScriptHash(
                                Address(address, currentWallet.network()).scriptPubkey(),
                            ),
                        )
                    } catch (e: Exception) {
                        if (BuildConfig.DEBUG) {
                            Log.w(TAG, "Could not derive script hash for receive address: ${e.message}")
                        }
                    }
                }
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
                    hashSet.add(scriptHashForRevealedAddress(currentWallet, KeychainKind.EXTERNAL, i))
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
                    hashSet.add(scriptHashForRevealedAddress(currentWallet, KeychainKind.INTERNAL, i))
                }
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.w(TAG, "Error getting sample script hashes: ${e.message}")
        }
        return hashSet.toList()
    }

    private fun publishThrottledSyncProgress(progress: SyncProgress) {
        val now = SystemClock.elapsedRealtime()
        val last = lastSyncProgressPublishElapsedMs.get()
        if (
            !SyncProgressPublishPolicy.shouldPublish(
                current = progress.current,
                total = progress.total,
                nowElapsedMs = now,
                lastPublishElapsedMs = last,
            )
        ) {
            return
        }
        lastSyncProgressPublishElapsedMs.set(now)
        _walletState.value = _walletState.value.copy(syncProgress = progress)
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
            secureStorage.getActiveWalletId()?.let { walletId ->
                resolveCurrentReceiveAddress(currentWallet, walletId)?.let { address ->
                    try {
                        hashSet.add(
                            computeScriptHash(
                                Address(address, currentWallet.network()).scriptPubkey(),
                            ),
                        )
                    } catch (e: Exception) {
                        if (BuildConfig.DEBUG) {
                            Log.w(TAG, "Could not derive script hash for receive address: ${e.message}")
                        }
                    }
                }
            }

            // All revealed external (receive) addresses
            val lastExternal = currentWallet.derivationIndex(KeychainKind.EXTERNAL)
            if (lastExternal != null) {
                for (i in 0u..lastExternal) {
                    hashSet.add(scriptHashForRevealedAddress(currentWallet, KeychainKind.EXTERNAL, i))
                }
            }
            val lastInternal = currentWallet.derivationIndex(KeychainKind.INTERNAL)
            if (lastInternal != null) {
                for (i in 0u..lastInternal) {
                    hashSet.add(scriptHashForRevealedAddress(currentWallet, KeychainKind.INTERNAL, i))
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
        silentPaymentScriptHashes.clear()
        lastSubscribedScriptHashFingerprint = null
        // Scope clearing to the active wallet so one wallet's switch/disconnect
        // cannot wipe another wallet's sync baseline (linkability + resync DoS).
        val activeId = secureStorage.getActiveWalletId().orEmpty()
        if (activeId.isNotBlank()) {
            electrumCache.clearScriptHashStatuses(activeId)
        } else {
            electrumCache.clearScriptHashStatuses()
        }
        invalidatePreparedSendCache()
    }

    private fun scriptHashCacheWalletId(): String = secureStorage.getActiveWalletId().orEmpty()

    private fun scriptHashForRevealedAddress(
        currentWallet: Wallet,
        keychain: KeychainKind,
        index: UInt,
    ): String {
        val key = keychain to index
        scriptHashByKeychainIndex[key]?.let { return it }
        val address = peekAddressStringCached(currentWallet, keychain, index)
        val hash =
            computeScriptHash(
                Address(address, currentWallet.network()).scriptPubkey(),
            )
        scriptHashByKeychainIndex[key] = hash
        return hash
    }

    private fun scriptHashSetFingerprint(scriptHashes: Collection<String>): String =
        scriptHashes.sorted().joinToString(separator = "\n")

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
            rbfEnabled = secureStorage.getRbfEnabled(),
            consolidateChange = secureStorage.getConsolidateChange(),
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

        // Fail closed: `unspendable` does not evict explicitly `addUtxo`'d inputs,
        // so check the requested selection against frozen refs up front.
        val frozenRefs = frozenRefsForActiveWallet()
        if (frozenRefs.isNotEmpty()) {
            val frozenSelected = selectedUtxos.map { it.outpoint }.filter { it in frozenRefs }
            require(frozenSelected.isEmpty()) {
                "Cannot send using frozen UTXOs — unfreeze coins or change selection"
            }
        }

        val selectedOutpoints = selectedUtxos.map { it.outpoint }.toSet()
        val selectedWalletUtxos =
            currentWallet.listUnspent().filter { utxo ->
                val outpoint = utxo.outpoint
                selectedOutpoints.contains("${outpoint.txid}:${outpoint.vout}")
            }

        return { builder ->
            // Re-check inside the applier: freeze may have landed after the
            // applier was built but before the TxBuilder runs.
            val liveFrozen = frozenRefsForActiveWallet()
            if (liveFrozen.isNotEmpty()) {
                val pinned =
                    selectedWalletUtxos.map { "${it.outpoint.txid}:${it.outpoint.vout}" }
                        .filter { it in liveFrozen }
                require(pinned.isEmpty()) {
                    "Cannot send using frozen UTXOs — unfreeze coins or change selection"
                }
            }
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

    private fun originalTxHasExternalTaprootOutput(
        currentWallet: Wallet,
        originalTx: Transaction,
    ): Boolean =
        originalTx.output().any { output ->
            val scriptBytes = output.scriptPubkey.toBytes()
            isP2trScript(scriptBytes) &&
                runCatching { !currentWallet.isMine(output.scriptPubkey) }.getOrDefault(true)
        }

    private fun persistSilentPaymentRecipients(
        walletId: String,
        txid: String,
        recipients: List<Recipient>,
    ) {
        if (hasSilentPaymentRecipient(recipients)) {
            secureStorage.saveSilentPaymentRecipients(walletId, txid, recipients)
        } else {
            // Record known non-SP sends so a later RBF bump can tell them apart from
            // unknown/legacy transactions when a fee top-up changes the input set.
            secureStorage.saveNoSilentPaymentMarker(walletId, txid)
        }
    }

    private fun rebuildWithSilentPaymentOutputs(
        placeholderPsbt: Psbt,
        currentWallet: Wallet,
        storedWallet: StoredWallet?,
        recipients: List<Recipient>,
        feeSats: ULong,
        walletId: String,
        forceRbf: Boolean = false,
    ): Psbt {
        val finalTx = placeholderPsbt.extractTx()
        val inputOutpoints = finalTx.input().map { it.previousOutput }
        // `unspendable` does not evict explicitly `addUtxo`'d inputs below,
        // so a frozen coin in the placeholder set would otherwise be pinned
        // straight into the replacement. Fail closed here (the placeholder
        // was built with the frozen filter, so this only fires on a
        // freeze-while-building race or cross-wallet misuse).
        val frozenRefs = secureStorage.getFrozenUtxos(walletId)
        if (frozenRefs.isNotEmpty()) {
            val frozenPinned =
                inputOutpoints.map { "${it.txid}:${it.vout}" }.filter { it in frozenRefs }
            require(frozenPinned.isEmpty()) {
                "Cannot send using frozen UTXOs — unfreeze coins or change selection"
            }
        }
        val inputKeys =
            deriveSilentPaymentInputKeys(
                currentWallet = currentWallet,
                storedWallet = storedWallet,
                inputOutpoints = inputOutpoints.map { "${it.txid}:${it.vout}" },
                walletId = walletId,
            )
        val silentRecipientIndexes =
            recipients.mapIndexedNotNull { index, recipient ->
                index.takeIf { isSilentPaymentAddress(recipient.address) }
            }
        val silentOutputKeys =
            SilentPayment.createOutputKeys(
                inputKeys = inputKeys,
                recipients = silentRecipientIndexes.map { recipients[it].address },
                allVinOutpoints = inputOutpoints.map { "${it.txid}:${it.vout}" },
            ).map { outputKey ->
                outputKey.copy(recipientIndex = silentRecipientIndexes[outputKey.recipientIndex])
            }
        val recipientScripts = buildSendRecipientScripts(recipients, currentWallet, silentOutputKeys)

        var builder = TxBuilder().feeAbsolute(Amount.fromSat(feeSats))
        builder = if (forceRbf) builder.applyOptInRbf() else builder.applyOptInRbfIfEnabled()
        builder = builder.applyFrozenUtxoFilter()
        val silentByOutpoint =
            secureStorage.getSilentPaymentUtxos(walletId)
                .associateBy { it.outpoint.lowercase() }
                .orEmpty()
        inputOutpoints.forEach { outpoint ->
            val key = "${outpoint.txid}:${outpoint.vout}".lowercase()
            val silent = silentByOutpoint[key]
            builder =
                if (silent != null) {
                    addSilentPaymentForeignUtxo(builder, silent)
                } else {
                    builder.addUtxo(outpoint)
                }
        }
        builder = addRecipientScripts(builder.manuallySelectedOnly(), recipientScripts)
        return builder.finish(currentWallet)
    }

    private fun buildSilentPaymentRedirectScript(
        placeholderPsbt: Psbt,
        currentWallet: Wallet,
        storedWallet: StoredWallet?,
        destinationAddress: String,
        walletId: String,
    ): Script {
        val finalTx = placeholderPsbt.extractTx()
        val inputOutpoints = finalTx.input().map { it.previousOutput }
        val inputKeys =
            deriveSilentPaymentInputKeys(
                currentWallet = currentWallet,
                storedWallet = storedWallet,
                inputOutpoints = inputOutpoints.map { "${it.txid}:${it.vout}" },
                walletId = walletId,
            )
        val outputKey =
            SilentPayment.createOutputKeys(
                inputKeys = inputKeys,
                recipients = listOf(destinationAddress),
                allVinOutpoints = inputOutpoints.map { "${it.txid}:${it.vout}" },
            ).single()
        return scriptFromBytes(SilentPayment.taprootScriptPubKey(outputKey.xOnlyPublicKey))
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    private fun deriveSilentPaymentInputKeys(
        currentWallet: Wallet,
        storedWallet: StoredWallet?,
        inputOutpoints: List<String>,
        walletId: String,
    ): List<SilentPayment.InputKey> {
        val walletOutputsByOutpoint =
            currentWallet.listOutput().associateBy { localOutput ->
                "${localOutput.outpoint.txid}:${localOutput.outpoint.vout}"
            }
        val silentUtxosByOutpoint =
            secureStorage.getSilentPaymentUtxos(walletId)
                .associateBy { it.outpoint.lowercase() }
        val missing =
            inputOutpoints.filter {
                it !in walletOutputsByOutpoint && it.lowercase() !in silentUtxosByOutpoint
            }
        require(missing.isEmpty()) {
            "Silent payments cannot recover input keys for unknown outpoints"
        }
        val keys =
            inputOutpoints.mapNotNull { outpoint ->
                silentUtxosByOutpoint[outpoint.lowercase()]?.let { utxo ->
                    return@mapNotNull silentPaymentInputKey(utxo)
                }
                val localOutput = walletOutputsByOutpoint.getValue(outpoint)
                val scriptBytes = localOutput.txout.scriptPubkey.toBytes().toUByteArray().toByteArray()
                val isTaproot = isP2trScript(scriptBytes)
                if (!isEligibleSilentPaymentInputScript(scriptBytes)) {
                    return@mapNotNull null
                }
                val wif = secureStorage.getPrivateKey(walletId)
                // Single-key (WIF) wallets share one key across all inputs. An
                // uncompressed key cannot contribute to the BIP-352 scalar sum —
                // decode-check (not prefix-guess) so `5...` keys fail closed
                // here instead of burning the SP output.
                if (wif != null && !SilentPayment.isCompressedWif(wif)) {
                    return@mapNotNull null
                }
                val privateKey =
                    deriveWalletInputPrivateKey(
                        storedWallet,
                        localOutput.keychain,
                        localOutput.derivationIndex,
                        isTaproot,
                        walletId,
                    )
                // Fail closed before the key enters the shared-secret sum: a
                // derivation mismatch (wrong seed, stale custom path,
                // cross-wallet race) would otherwise burn the SP outputs while
                // the transaction itself broadcasts fine.
                require(
                    SilentPayment.inputKeyMatchesScript(privateKey, scriptBytes, isTaproot),
                ) { "Silent payment input key does not match $outpoint" }
                SilentPayment.InputKey(
                    outpoint = outpoint,
                    privateKey = privateKey,
                    isTaproot = isTaproot,
                )
            }
        require(keys.isNotEmpty()) { "Silent payments require an eligible compressed input" }
        return keys
    }

    private fun silentPaymentInputKey(utxo: SilentPaymentUtxo): SilentPayment.InputKey {
        val keys =
            silentPaymentKeys
                ?: throw IllegalStateException("Silent payment keys unavailable")
        val found =
            SilentPayment.scanOutputs(
                tweakKey = utxo.tweakKeyHex.hexToByteArray(),
                scanPrivateKey = keys.scanPrivateKey,
                spendPrivateKey = keys.spendPrivateKey,
                spendPublicKey = keys.spendPublicKey,
                outputs =
                    listOf(
                        SilentPayment.TxOutput(
                            scriptPubKey = utxo.scriptPubKeyHex.hexToByteArray(),
                            valueSats = utxo.valueSats,
                        ),
                    ),
            ).firstOrNull()
                ?: throw IllegalStateException("Could not derive silent payment input key")
        val normalized = SilentPayment.evenYPrivateKey(found.spendPrivateKey)
        // Same fail-closed ownership check as hot-wallet inputs: a corrupted
        // SP UTXO record (wrong tweak/script) must abort the send instead of
        // entering the shared-secret sum and burning the outputs.
        require(
            SilentPayment.inputKeyMatchesScript(
                normalized,
                utxo.scriptPubKeyHex.hexToByteArray(),
                true,
            ),
        ) { "Silent payment input key does not match ${utxo.outpoint}" }
        return SilentPayment.InputKey(
            outpoint = utxo.outpoint,
            privateKey = normalized,
            isTaproot = true,
        )
    }

    private fun deriveWalletInputPrivateKey(
        storedWallet: StoredWallet?,
        keychain: KeychainKind,
        derivationIndex: UInt,
        isTaproot: Boolean,
        walletId: String,
    ): ByteArray {
        secureStorage.getPrivateKey(walletId)?.let { wif ->
            val key = SilentPayment.privateKeyFromWif(wif)
            return if (isTaproot) SilentPayment.deriveTaprootOutputPrivateKey(key) else key
        }
        if (storedWallet?.isWatchOnly == true) {
            throw IllegalStateException("Silent payments require a hot wallet")
        }
        val mnemonic = secureStorage.getMnemonic(walletId)
            ?: throw IllegalStateException("Silent payments require a seed wallet")
        val passphrase = secureStorage.getPassphrase(walletId)
        val wallet =
            storedWallet ?: throw IllegalStateException("Silent payments require a seed wallet")
        val path =
            wallet.inputDerivationPath(
                change = keychain == KeychainKind.INTERNAL,
                index = derivationIndex.toLong(),
            )
        val seed =
            when (wallet.seedFormat) {
                SeedFormat.BIP39 -> bip39SeedCanonical(mnemonic, passphrase)
                SeedFormat.ELECTRUM_STANDARD,
                SeedFormat.ELECTRUM_SEGWIT,
                -> ElectrumSeedUtil.mnemonicToSeed(mnemonic, passphrase)
            }
        val key = ElectrumSeedUtil.derivePrivateKey(seed, path)
        return if (isTaproot) SilentPayment.deriveTaprootOutputPrivateKey(key) else key
    }

    // BIP-352 eligible inputs with keys we can derive: P2PKH, P2WPKH, P2TR.
    // NOTE: P2SH-P2WPKH is deliberately NOT eligible: we only have the
    // scriptPubKey here, not the redeemScript, so we cannot prove the inner
    // script is P2WPKH nor extract the right key. Including outer-template
    // matches in `a` would diverge from compliant receivers and burn outputs.
    private fun isEligibleSilentPaymentInputScript(scriptBytes: ByteArray): Boolean =
        isP2pkhScript(scriptBytes) ||
            isP2wpkhScript(scriptBytes) ||
            isP2trScript(scriptBytes)

    @Suppress("unused")
    private fun isP2shP2wpkhScript(scriptBytes: ByteArray): Boolean =
        scriptBytes.size == 23 &&
            scriptBytes[0] == 0xA9.toByte() &&
            scriptBytes[1] == 0x14.toByte() &&
            scriptBytes[22] == 0x87.toByte()

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
    ): PreparedPsbtBuild =
        withConsolidateFallback { preferChangeOnly ->
            buildPreparedPsbtOnce(
                currentWallet = currentWallet,
                feeRateSatPerVb = feeRateSatPerVb,
                precomputedFeeSats = precomputedFeeSats,
                configureBuilder = configureBuilder,
                preferChangeOnly = preferChangeOnly,
            )
        }

    private fun buildPreparedPsbtOnce(
        currentWallet: Wallet,
        feeRateSatPerVb: Double,
        precomputedFeeSats: ULong?,
        configureBuilder: (TxBuilder) -> TxBuilder,
        preferChangeOnly: Boolean? = null,
    ): PreparedPsbtBuild {
        val feeRate = feeRateFromSatPerVb(feeRateSatPerVb)

        fun buildPass1Psbt(): Pair<Psbt, Transaction> {
            val pass1Psbt =
                configureBuilder(
                    TxBuilder().applySendDefaults(preferChangeOnly).feeRate(feeRate),
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
                            TxBuilder().applySendDefaults(preferChangeOnly).feeAbsolute(Amount.fromSat(precomputedFeeSats)),
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
                                TxBuilder().applySendDefaults(preferChangeOnly).feeAbsolute(
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
                            TxBuilder().applySendDefaults(preferChangeOnly).feeAbsolute(
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
        var changeIsMine = true

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
                // Never label a foreign output as change: a mismatched change
                // descriptor routes funds away, and by-exclusion labeling hides it.
                changeIsMine = runCatching { currentWallet.isMine(output.scriptPubkey) }.getOrDefault(false)
                hasChange = true
            }
        }

        return SingleRecipientOutputSummary(
            recipientAmountSats = recipientAmount,
            changeAmountSats = changeAmount,
            changeAddress = changeAddress,
            hasChange = hasChange,
            changeIsMine = changeIsMine,
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
        var changeIsMine = true

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
                changeIsMine = runCatching { currentWallet.isMine(output.scriptPubkey) }.getOrDefault(false)
                hasChange = true
            }
        }

        return MultiRecipientOutputSummary(
            totalRecipientAmountSats = totalRecipientAmount,
            changeAmountSats = changeAmount,
            changeAddress = changeAddress,
            hasChange = hasChange,
            changeIsMine = changeIsMine,
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
            changeAddress = summary.changeAddress,
            changeIsMine = summary.changeIsMine,
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
            changeAddress = summary.changeAddress,
            changeIsMine = summary.changeIsMine,
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

            if (secureStorage.needsFullSync(activeWalletId)) {
                if (BuildConfig.DEBUG) Log.d(TAG, "Wallet needs full sync — running before subscriptions")
                val syncResult = sync()
                if (syncResult is WalletResult.Error) {
                    return@withContext SubscriptionResult.FAILED
                }
            }

            val revealedGapLimitAddresses = revealConfiguredGapLimit(currentWallet, activeWalletId)
            val allScriptHashes = getAllRevealedScriptHashes(currentWallet)
            if (allScriptHashes.isEmpty()) return@withContext SubscriptionResult.FAILED

            val fingerprint = scriptHashSetFingerprint(allScriptHashes)
            val collectorAlive = notificationCollectorJob?.isActive == true
            if (
                !revealedGapLimitAddresses &&
                    collectorAlive &&
                    proxy.isSubscriptionAlive() &&
                    fingerprint == lastSubscribedScriptHashFingerprint &&
                    subscribedScriptHashes.containsAll(allScriptHashes)
            ) {
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "Subscription set unchanged — skipping re-subscribe (${allScriptHashes.size})")
                }
                startSilentPaymentScan(proxy, activeWalletId)
                return@withContext SubscriptionResult.NO_CHANGES
            }

            stopNotificationCollector()

            if (BuildConfig.DEBUG) Log.d(TAG, "Subscribing ${allScriptHashes.size} addresses for real-time updates")

            val currentStatuses = proxy.startSubscriptions(allScriptHashes)

            if (currentStatuses.isEmpty()) {
                if (BuildConfig.DEBUG) Log.w(TAG, "Failed to start subscriptions — proxy returned empty")
                return@withContext SubscriptionResult.FAILED
            }

            // Track which script hashes are subscribed
            subscribedScriptHashes.clear()
            subscribedScriptHashes.addAll(currentStatuses.keys)
            lastSubscribedScriptHashFingerprint = fingerprint

            // Do NOT populate scriptHashStatusCache yet — if we populate it now with
            // the server's current statuses, sync()'s pre-check will compare the cache
            // against the same server and see "no changes", skipping BDK sync entirely.
            // The cache must only reflect what BDK has already processed.
            scriptHashStatusCache.clear()

            // Compare against persisted statuses from previous session
            val persistedStatuses = electrumCache.loadScriptHashStatuses(scriptHashCacheWalletId())
            val needsSync =
                if (revealedGapLimitAddresses) {
                    if (BuildConfig.DEBUG) Log.d(TAG, "New gap-limit addresses revealed — sync needed")
                    true
                } else {
                    hasScriptHashStatusChanges(currentStatuses, persistedStatuses)
                }

            val result: SubscriptionResult

            if (needsSync) {
                if (BuildConfig.DEBUG) Log.d(TAG, "Changes detected — running sync")
                // Cache is empty → sync()'s pre-check is bypassed → BDK sync runs
                val syncResult = sync()
                result =
                    if (syncResult is WalletResult.Success) {
                        // NOW populate the cache — BDK has processed all changes.
                        // These statuses represent the current server state that
                        // BDK is in sync with.
                        scriptHashStatusCache.putAll(currentStatuses)
                        // Persist current statuses for next app launch
                        electrumCache.saveScriptHashStatuses(scriptHashStatusCache.toMap(), scriptHashCacheWalletId())
                        SubscriptionResult.SYNCED
                    } else {
                        // Sync failed — do NOT record the server's statuses in memory
                        // or on disk. Keeping the stale persisted snapshot forces
                        // change detection (and a sync) on the next launch; leaving
                        // the in-memory cache empty bypasses the quick-sync pre-check.
                        SubscriptionResult.FAILED
                    }
            } else {
                if (BuildConfig.DEBUG) Log.d(TAG, "No changes since last session — skipping sync")
                scriptHashStatusCache.putAll(currentStatuses)
                electrumCache.saveScriptHashStatuses(scriptHashStatusCache.toMap(), scriptHashCacheWalletId())
                result = SubscriptionResult.NO_CHANGES
            }

            if (BuildConfig.DEBUG) Log.d(TAG, "Starting notification collector (result=$result)")

            startNotificationCollector(proxy)
            startSilentPaymentScan(proxy, activeWalletId)

            result
        }

    /**
     * Collect push notifications from the proxy and trigger targeted sync.
     * Uses a 1-second debounce window (like Sparrow) to coalesce rapid
     * notifications (e.g., a new block confirming multiple wallet txs).
     */
    private fun startNotificationCollector(proxy: CachingElectrumProxy) {
        notificationCollectorJob?.cancel()
        notificationDebounceJob?.cancel()
        notificationDebounceJob = null
        notificationSyncJob?.cancel()
        notificationSyncJob = null
        notificationCollectorJob =
            repositoryScope.launch {
                if (BuildConfig.DEBUG) Log.d(TAG, "Notification collector started")

                val pendingNotifications = mutableListOf<ElectrumNotification>()

                fun launchDebouncedSync() {
                    notificationDebounceJob?.cancel()
                    notificationDebounceJob =
                        launch {
                            delay(NOTIFICATION_DEBOUNCE_MS)
                            notificationSyncJob?.join()
                            val batch = pendingNotifications.toList()
                            pendingNotifications.clear()
                            if (batch.isEmpty()) return@launch

                            if (BuildConfig.DEBUG) {
                                Log.d(TAG, "Debounced sync trigger: ${batch.size} script hash changes")
                            }

                            notificationSyncJob =
                                repositoryScope.launch {
                                    scriptHashStatusCache.clear()
                                    val result = sync(force = true)
                                    if (result is WalletResult.Success) {
                                        subscribeNewlyRevealedAddresses()
                                        electrumCache.saveScriptHashStatuses(scriptHashStatusCache.toMap(), scriptHashCacheWalletId())
                                    }
                                }
                        }
                }

                proxy.notifications.collect { notification ->
                    if (!isActive) return@collect

                    if (notification is ElectrumNotification.ConnectionLost) {
                        SecureLog.w(TAG, "Subscription socket reported connection lost")
                        _connectionEvents.tryEmit(ConnectionEvent.ConnectionLost)
                        return@collect
                    }

                    if (notification is ElectrumNotification.SilentPaymentsUpdate) {
                        try {
                            handleSilentPaymentUpdate(notification)
                        } catch (e: Exception) {
                            if (BuildConfig.DEBUG) {
                                Log.e(TAG, "Silent payments update failed: ${e.message}", e)
                            }
                        }
                        return@collect
                    }

                    if (
                        notification is ElectrumNotification.ScriptHashChanged &&
                        notification.scriptHash in silentPaymentScriptHashes
                    ) {
                        refreshSilentPaymentWalletState()
                        if (notification.scriptHash in scriptHashStatusCache) {
                            pendingNotifications.add(notification)
                            launchDebouncedSync()
                        }
                        return@collect
                    }

                    if (notification is ElectrumNotification.NewBlockHeader) {
                        lastKnownBlockHeight = notification.height.toULong()
                        secureStorage.getActiveWalletId()?.let { ensureSpPendingRetryLoop(it) }
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
                                    electrumCache.saveScriptHashStatuses(scriptHashStatusCache.toMap(), scriptHashCacheWalletId())
                                }
                            }
                        }
                        return@collect
                    }

                    pendingNotifications.add(notification)
                    launchDebouncedSync()
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
        subscribedScriptHashes.addAll(newHashes)
        scriptHashStatusCache.putAll(newStatuses)
        lastSubscribedScriptHashFingerprint = scriptHashSetFingerprint(subscribedScriptHashes)
    }

    /**
     * Stop the notification collector coroutine.
     */
    private fun stopNotificationCollector() {
        notificationDebounceJob?.cancel()
        notificationDebounceJob = null
        notificationSyncJob?.cancel()
        notificationSyncJob = null
        notificationCollectorJob?.cancel()
        notificationCollectorJob = null
    }

    /**
     * BIP39 seed for silent payments / message signing, canonicalized through
     * the same BDK `Mnemonic` parser used for descriptors. BDK normalizes
     * whitespace/case per rust-bip39; feeding the raw stored string into PBKDF2
     * instead would derive a different seed and burn SP outputs. Falls back to
     * the stored string only if BDK parsing fails (caller then fails closed
     * via inputKeyMatchesScript / no-match signing).
     */
    private fun bip39SeedCanonical(mnemonic: String, passphrase: String?): ByteArray {
        val canonical =
            runCatching { Mnemonic.fromString(mnemonic).use { it.toString() } }
                .getOrNull()?.takeIf { it.isNotBlank() } ?: mnemonic
        return ElectrumSeedUtil.bip39MnemonicToSeed(canonical, passphrase)
    }

    private fun seedForSilentPayments(storedWallet: StoredWallet): ByteArray? {
        val mnemonic = secureStorage.getMnemonic(storedWallet.id) ?: return null
        val passphrase = secureStorage.getPassphrase(storedWallet.id)
        return when (storedWallet.seedFormat) {
            SeedFormat.BIP39 -> bip39SeedCanonical(mnemonic, passphrase)
            SeedFormat.ELECTRUM_STANDARD,
            SeedFormat.ELECTRUM_SEGWIT,
            -> ElectrumSeedUtil.mnemonicToSeed(mnemonic, passphrase)
        }
    }

    private fun refreshSilentPaymentKeys(storedWallet: StoredWallet?) {
        silentPaymentKeys =
            if (canReceiveSilentPayments(storedWallet) && storedWallet != null) {
                seedForSilentPayments(storedWallet)?.let { SilentPayment.deriveReceiverKeys(it) }
            } else {
                null
            }
    }

    private fun startSilentPaymentScan(
        proxy: CachingElectrumProxy,
        walletId: String,
        force: Boolean = false,
    ) {
        val keys = silentPaymentKeys
        if (keys == null) {
            stopSilentPaymentScan()
            if (_walletState.value.silentPaymentsSupported != silentPaymentsSupported) {
                updateWalletState()
            }
            return
        }
        when (proxy.supportsSilentPayments()) {
            true -> silentPaymentsSupported = true
            false -> silentPaymentsSupported = false
            null -> Unit
        }
        if (silentPaymentsSupported != _walletState.value.silentPaymentsSupported) {
            updateWalletState()
        }
        if (silentPaymentsSupported == false) return
        val scanHex = keys.scanPrivateKey.joinToString("") { "%02x".format(it) }
        val spendHex = keys.spendPublicKey.joinToString("") { "%02x".format(it) }
        if (
            !force &&
            spSubscribedProxy === proxy &&
            subscribedSilentPaymentScanKeyHex == scanHex &&
            subscribedSilentPaymentSpendKeyHex == spendHex
        ) {
            return
        }
        stopSilentPaymentScan()
        val lastHeight = secureStorage.getSilentPaymentScanHeight(walletId)
        val hasUtxos = secureStorage.getSilentPaymentUtxos(walletId).isNotEmpty()
        val fullStart =
            when {
                !hasUtxos -> TAPROOT_ACTIVATION_HEIGHT
                lastHeight > TAPROOT_ACTIVATION_HEIGHT + 100 -> lastHeight - 100
                else -> TAPROOT_ACTIVATION_HEIGHT
            }
        // A fresh wallet would otherwise scan the whole chain before learning
        // anything — including mempool, which Frigate only reports at
        // progress=1.0. Prime live state with a cheap recent-window scan
        // first; the full backfill follows on the same subscription. The merge
        // is idempotent, so nothing can be missed or doubled.
        val tip =
            runCatching { wallet?.latestCheckpoint()?.height?.toInt() }.getOrNull()
                ?: lastKnownBlockHeight?.toInt()
        val quickStart = tip?.let { maxOf(TAPROOT_ACTIVATION_HEIGHT, it - 100) }
        if (quickStart != null && quickStart > fullStart) {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "SP scan: priming live state from $quickStart before full backfill from $fullStart")
            }
            proxy.subscribeSilentPayments(scanHex, spendHex, quickStart)
        }
        if (!proxy.subscribeSilentPayments(scanHex, spendHex, fullStart)) return
        subscribedSilentPaymentScanKeyHex = scanHex
        subscribedSilentPaymentSpendKeyHex = spendHex
        spSubscribedProxy = proxy
        spSubscribedAtMs = System.currentTimeMillis()
        spScanProgress = 0.0
        spLastPushMs = 0L
        subscribeSilentPaymentScriptHashes(proxy, walletId)
    }

    private fun stopSilentPaymentScan() {
        val scanHex = subscribedSilentPaymentScanKeyHex
        val spendHex = subscribedSilentPaymentSpendKeyHex
        if (scanHex != null && spendHex != null) {
            cachingProxy?.unsubscribeSilentPayments(scanHex, spendHex)
        }
        subscribedSilentPaymentScanKeyHex = null
        subscribedSilentPaymentSpendKeyHex = null
        spSubscribedProxy = null
        spSubscribedAtMs = 0L
        spScanProgress = 0.0
        spLastPushMs = 0L
    }

    private fun resetSilentPaymentsCapability() {
        stopSilentPaymentScan()
        silentPaymentsSupported = null
    }

    private fun subscribeSilentPaymentScriptHashes(
        proxy: CachingElectrumProxy,
        walletId: String,
    ) {
        val hashes =
            secureStorage.getSilentPaymentUtxos(walletId)
                .filterNot { it.spent }
                .mapNotNull { utxo ->
                    runCatching {
                        BitcoinUtils.computeScriptHash(utxo.scriptPubKeyHex.hexToByteArray())
                    }.getOrNull()
                }
        if (hashes.isEmpty()) return
        silentPaymentScriptHashes.addAll(hashes)
        proxy.subscribeAdditionalScriptHashes(hashes)
        subscribedScriptHashes.addAll(hashes)
    }

    private suspend fun refreshSilentPaymentWalletState() {
        val walletId = secureStorage.getActiveWalletId() ?: return
        val proxy = cachingProxy
        if (proxy != null) {
            // Bound the whole refresh: every step inside is serial Tor I/O
            // with no per-call timeout. Without this cap a hung socket
            // stalls the sync pre-check path indefinitely and the next
            // refresh forces a resubscribe → backfill → stall livelock.
            // Mutations persist only on the success path, so a timeout
            // leaves stored state untouched for the next attempt.
            withTimeoutOrNull(SP_REFRESH_TIMEOUT_MS) {
                spUtxoMutex.withLock {
                // Never restart a live Frigate scan here: every resubscribe
                // re-runs the historical scan and mempool is only reported
                // at progress=1.0. (Re)subscribe only when never subscribed
                // on this connection, when the scan stalled with no push
                // inside the stall window, or when scan semantics changed
                // (generation bump forces one unskipped backfill so older
                // logic's misses are re-examined).
                val generationChanged =
                    secureStorage.getSilentPaymentScanGeneration(walletId) != SP_SCAN_GENERATION
                startSilentPaymentScan(proxy, walletId, force = spScanStalled(proxy) || generationChanged)
                val keys = silentPaymentKeys
                val utxos = secureStorage.getSilentPaymentUtxos(walletId).toMutableList()
                var dirty = false
                if (keys != null && retrySilentPaymentPendingItems(proxy, keys, walletId, utxos)) {
                    dirty = true
                }
                if (utxos.isNotEmpty() && refreshSilentPaymentSpentStatus(walletId, proxy, utxos)) {
                    dirty = true
                }
                if (repairSilentPaymentUtxoAmountsInList(proxy, utxos)) {
                    dirty = true
                }
                if (fillSilentPaymentTimestampsInList(utxos)) {
                    dirty = true
                }
                if (dirty) {
                    secureStorage.saveSilentPaymentUtxos(walletId, utxos)
                    subscribeSilentPaymentScriptHashes(proxy, walletId)
                }
                }
            }
        }
        if (secureStorage.getSilentPaymentPendingItems(walletId).isNotEmpty()) {
            ensureSpPendingRetryLoop(walletId)
        }
        updateWalletState()
    }

    private fun spScanStalled(proxy: CachingElectrumProxy): Boolean {
        if (spSubscribedProxy !== proxy) return false
        if (subscribedSilentPaymentScanKeyHex == null) return false
        if (spScanProgress >= 1.0) return false
        val lastActivity = maxOf(spLastPushMs, spSubscribedAtMs)
        if (lastActivity <= 0L) return false
        return System.currentTimeMillis() - lastActivity > SP_SCAN_STALL_MS
    }

    private suspend fun handleSilentPaymentUpdate(update: ElectrumNotification.SilentPaymentsUpdate) {
        val keys = silentPaymentKeys ?: return
        val walletId = secureStorage.getActiveWalletId() ?: return
        if (!update.address.equals(keys.address, ignoreCase = true)) return
        spScanProgress = update.progress
        spLastPushMs = System.currentTimeMillis()
        if (BuildConfig.DEBUG) {
            val preview = update.history.take(5).joinToString(",") { "${it.txHash.take(8)}@${it.height}" }
            Log.d(TAG, "Silent payments push: progress=${update.progress} history=${update.history.size} startHeight=${update.startHeight} [$preview]")
        }
        if (silentPaymentsSupported != true) {
            silentPaymentsSupported = true
        }
        val proxy = cachingProxy ?: return
        // Skip re-fetching transactions this generation already scanned: a
        // fully-scanned tx is immutable, so its result cannot change. The
        // skip is disabled until the stored generation catches up, forcing
        // one thorough pass after every scan-logic upgrade.
        val skipScannedTx = secureStorage.getSilentPaymentScanGeneration(walletId) == SP_SCAN_GENERATION
        spUtxoMutex.withLock {
            val existing = secureStorage.getSilentPaymentUtxos(walletId).toMutableList()
            val scannedTxids =
                if (skipScannedTx) {
                    existing.map { it.txid.lowercase() }.toSet()
                } else {
                    emptySet()
                }
            var added = retrySilentPaymentPendingItems(proxy, keys, walletId, existing)
            touchSilentPaymentPendingAdvertised(walletId, update.history)
            update.history.forEach { item ->
                if (item.txHash.lowercase() in scannedTxids) {
                    removeSilentPaymentPendingItem(walletId, item.txHash)
                    return@forEach
                }
                try {
                    val found = scanSilentPaymentHistoryItem(proxy, keys, item)
                    if (found == null) {
                        // Transient fetch failure — queue for retry instead of
                        // dropping the receive forever.
                        queueSilentPaymentPendingItem(walletId, item)
                        if (BuildConfig.DEBUG) {
                            Log.d(TAG, "SP scan: tx fetch failed for ${item.txHash}, queued for retry")
                        }
                        return@forEach
                    }
                    removeSilentPaymentPendingItem(walletId, item.txHash)
                    found.forEach { utxo ->
                        added = mergeSilentPaymentUtxo(existing, utxo) || added
                    }
                } catch (e: Exception) {
                    if (BuildConfig.DEBUG) {
                        Log.e(TAG, "SP scan failed for ${item.txHash}: ${e.message}", e)
                    }
                    queueSilentPaymentPendingItem(walletId, item)
                }
            }
            if (update.progress >= 1.0) {
                val scannedTo =
                    update.history
                        .map { it.height }
                        .filter { it > 0 }
                        .maxOrNull()
                val previous = secureStorage.getSilentPaymentScanHeight(walletId)
                if (scannedTo != null) {
                    if (scannedTo > previous) {
                        secureStorage.setSilentPaymentScanHeight(walletId, scannedTo)
                    }
                } else {
                    // A complete scan with no confirmed history still proves
                    // the chain was scanned: persist the tip so the next
                    // launch resumes with the usual overlap instead of
                    // re-backfilling from genesis every time. Forward-only.
                    val tip =
                        runCatching { wallet?.latestCheckpoint()?.height?.toInt() }.getOrNull()
                            ?: lastKnownBlockHeight?.toInt()
                    if (tip != null && tip > previous) {
                        secureStorage.setSilentPaymentScanHeight(walletId, tip)
                    }
                }
                if (!skipScannedTx) {
                    secureStorage.setSilentPaymentScanGeneration(walletId, SP_SCAN_GENERATION)
                }
            }
            val spentChanged = refreshSilentPaymentSpentStatus(walletId, proxy, existing)
            val repaired = repairSilentPaymentUtxoAmountsInList(proxy, existing)
            if (added || spentChanged || repaired) {
                secureStorage.saveSilentPaymentUtxos(walletId, existing)
                subscribeSilentPaymentScriptHashes(proxy, walletId)
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "SP scan: stored ${existing.size} utxo(s), added=$added spentChanged=$spentChanged")
                }
            }
        }
        if (secureStorage.getSilentPaymentPendingItems(walletId).isNotEmpty()) {
            ensureSpPendingRetryLoop(walletId)
        }
        updateWalletState()
    }

    private fun mergeSilentPaymentUtxo(
        existing: MutableList<SilentPaymentUtxo>,
        utxo: SilentPaymentUtxo,
    ): Boolean {
        val existingIndex = existing.indexOfFirst { it.outpoint == utxo.outpoint }
        if (existingIndex < 0) {
            existing += utxo
            return true
        }
        val current = existing[existingIndex]
        val merged =
            current.copy(
                height = maxOf(current.height, utxo.height),
                timestamp = utxo.timestamp ?: current.timestamp,
            )
        if (merged != current) {
            existing[existingIndex] = merged
            return true
        }
        return false
    }

    private fun queueSilentPaymentPendingItem(
        walletId: String,
        item: SilentPaymentHistoryItem,
    ) {
        val now = System.currentTimeMillis()
        val pending = secureStorage.getSilentPaymentPendingItems(walletId).toMutableList()
        val index = pending.indexOfFirst { it.txHash.equals(item.txHash, ignoreCase = true) }
        if (index < 0) {
            pending +=
                SilentPaymentPendingItem(
                    txHash = item.txHash,
                    tweakKey = item.tweakKey,
                    height = item.height,
                    firstSeenMs = now,
                    lastSeenMs = now,
                )
        } else {
            pending[index] = pending[index].copy(
                height = maxOf(pending[index].height, item.height),
                lastSeenMs = now,
            )
        }
        secureStorage.saveSilentPaymentPendingItems(walletId, pending)
    }

    /**
     * Record that these txs are still advertised server-side, so expiry is
     * measured from the last sighting. A continuously advertised receive is
     * retried indefinitely instead of aging out of the queue.
     */
    private fun touchSilentPaymentPendingAdvertised(
        walletId: String,
        history: List<SilentPaymentHistoryItem>,
    ) {
        if (history.isEmpty()) return
        val advertised = history.map { it.txHash.lowercase() }.toSet()
        val pending = secureStorage.getSilentPaymentPendingItems(walletId)
        if (pending.none { it.txHash.lowercase() in advertised }) return
        val now = System.currentTimeMillis()
        secureStorage.saveSilentPaymentPendingItems(
            walletId,
            pending.map { item ->
                if (item.txHash.lowercase() in advertised) item.copy(lastSeenMs = now) else item
            },
        )
    }

    private fun removeSilentPaymentPendingItem(
        walletId: String,
        txHash: String,
    ) {
        val pending = secureStorage.getSilentPaymentPendingItems(walletId)
        if (pending.none { it.txHash.equals(txHash, ignoreCase = true) }) return
        secureStorage.saveSilentPaymentPendingItems(
            walletId,
            pending.filterNot { it.txHash.equals(txHash, ignoreCase = true) },
        )
    }

    private var spPendingRetryJob: Job? = null

    // A queued mempool receive must not wait for user action to retry: while
    // the queue is non-empty, retry with exponential backoff (30s base,
    // 15min cap; reset on progress) plus on every push, refresh and block,
    // until each item scans or expires. Self-terminates on empty queue,
    // wallet switch, or missing keys.
    private fun ensureSpPendingRetryLoop(walletId: String) {
        if (spPendingRetryJob?.isActive == true) return
        if (secureStorage.getSilentPaymentPendingItems(walletId).isEmpty()) return
        spPendingRetryJob =
            repositoryScope.launch {
                var attempts = 0
                while (isActive) {
                    val delayMs = (SP_PENDING_RETRY_BASE_MS shl attempts.coerceAtMost(5))
                        .coerceAtMost(SP_PENDING_RETRY_MAX_MS)
                    delay(delayMs)
                    val activeId = secureStorage.getActiveWalletId()
                    if (activeId != walletId) break
                    val keys = silentPaymentKeys ?: break
                    val proxy = cachingProxy ?: continue
                    var dirty = false
                    spUtxoMutex.withLock {
                        val existing = secureStorage.getSilentPaymentUtxos(activeId).toMutableList()
                        if (retrySilentPaymentPendingItems(proxy, keys, activeId, existing)) {
                            secureStorage.saveSilentPaymentUtxos(activeId, existing)
                            subscribeSilentPaymentScriptHashes(proxy, activeId)
                            dirty = true
                        }
                    }
                    if (dirty) {
                        attempts = 0
                        updateWalletState()
                    } else {
                        attempts++
                    }
                    if (secureStorage.getSilentPaymentPendingItems(activeId).isEmpty()) break
                }
            }
    }

    private fun retrySilentPaymentPendingItems(
        proxy: CachingElectrumProxy,
        keys: SilentPayment.ReceiverKeys,
        walletId: String,
        existing: MutableList<SilentPaymentUtxo>,
    ): Boolean {
        val pending = secureStorage.getSilentPaymentPendingItems(walletId)
        if (pending.isEmpty()) return false
        var added = false
        val now = System.currentTimeMillis()
        pending.forEach { item ->
            if (now - item.lastAdvertisedMs > SP_PENDING_ITEM_TTL_MS) {
                // The server stopped advertising this candidate over a week
                // ago: likely a reorged mempool phantom. Scan one final time
                // before dropping — a confirmed-but-unadvertised receive must
                // not be discarded without ever being examined. A later
                // backfill re-queues it via history if it reappears.
                val lastChance =
                    runCatching {
                        scanSilentPaymentHistoryItem(
                            proxy,
                            keys,
                            SilentPaymentHistoryItem(height = item.height, txHash = item.txHash, tweakKey = item.tweakKey),
                        )
                    }.getOrNull()
                if (lastChance != null) {
                    removeSilentPaymentPendingItem(walletId, item.txHash)
                    lastChance.forEach { utxo ->
                        added = mergeSilentPaymentUtxo(existing, utxo) || added
                    }
                } else {
                    Log.w(TAG, "SP scan: pending ${item.txHash} unadvertised for TTL without scanning; dropping from retry queue")
                    removeSilentPaymentPendingItem(walletId, item.txHash)
                }
                return@forEach
            }
            val found =
                scanSilentPaymentHistoryItem(
                    proxy,
                    keys,
                    SilentPaymentHistoryItem(height = item.height, txHash = item.txHash, tweakKey = item.tweakKey),
                ) ?: return@forEach
            removeSilentPaymentPendingItem(walletId, item.txHash)
            found.forEach { utxo ->
                added = mergeSilentPaymentUtxo(existing, utxo) || added
            }
        }
        return added
    }

    private fun refreshSilentPaymentSpentStatus(
        walletId: String,
        proxy: CachingElectrumProxy,
        utxos: MutableList<SilentPaymentUtxo>,
    ): Boolean {
        var changed = false
        val phantomTxids = mutableListOf<String>()
        val tipHeight =
            lastKnownBlockHeight?.toLong()
                ?: runCatching { wallet?.latestCheckpoint()?.height?.toLong() }.getOrNull()
        utxos.forEachIndexed { index, utxo ->
            // Frigate never re-notifies when a mempool receive confirms — the
            // scripthash history is the canonical source for confirmation
            // height, so unconfirmed UTXOs must always be re-checked.
            // Frigate never re-notifies on confirmation; the scripthash
            // history is the only source. Always re-check unconfirmed UTXOs
            // and UTXOs whose spend hasn't confirmed yet.
            val needsConfirmCheck = utxo.height <= 0
            val needsSpendCheck = !utxo.spent || utxo.spendTxid.isNullOrBlank()
            val needsSpendConfirmCheck = utxo.spent && !utxo.spendTxid.isNullOrBlank() && utxo.spendHeight <= 0
            // Confirmed spends are normally stable, but a reorg can evict the
            // spending tx: without a re-check the UTXO stays spent=true
            // forever and the funds become invisible/unspendable. Re-verify
            // spends within the reorg window of the tip.
            val needsSpendReorgCheck =
                utxo.spent && !utxo.spendTxid.isNullOrBlank() && utxo.spendHeight > 0 &&
                    tipHeight != null && (tipHeight - utxo.spendHeight) < SP_REORG_WINDOW_BLOCKS
            // A confirmed receive whose tx vanished from scripthash history
            // was reorged/double-spent away: keeping it overstates the balance
            // and every spend attempt fails. Recheck confirmed receives within
            // the window too so phantoms are pruned below.
            val needsReceiveReorgCheck =
                !utxo.spent && utxo.height > 0 &&
                    tipHeight != null && (tipHeight - utxo.height) < SP_REORG_WINDOW_BLOCKS
            val needsTimestamp = (utxo.timestamp ?: 0L) <= 0L
            val needsSpendTimestamp = utxo.spent && (utxo.spendTimestamp ?: 0L) <= 0L
            if (!needsConfirmCheck && !needsSpendCheck && !needsSpendConfirmCheck && !needsSpendReorgCheck && !needsReceiveReorgCheck && !needsTimestamp && !needsSpendTimestamp) {
                return@forEachIndexed
            }
            val scriptHash =
                runCatching { BitcoinUtils.computeScriptHash(utxo.scriptPubKeyHex.hexToByteArray()) }
                    .getOrNull() ?: return@forEachIndexed
            val history = proxy.getScriptHashHistory(scriptHash) ?: return@forEachIndexed
            val confirmedHeight =
                history.firstOrNull { (txid, _) -> txid.equals(utxo.txid, ignoreCase = true) }
                    ?.second?.takeIf { it > 0 }
            val newHeight = maxOf(utxo.height, confirmedHeight ?: 0)
            val spendEntry =
                history.firstOrNull { (txid, _) -> !txid.equals(utxo.txid, ignoreCase = true) }
            val receiveEntry =
                history.firstOrNull { (txid, _) -> txid.equals(utxo.txid, ignoreCase = true) }
            // Prune reorged-out receives: history fetch succeeded but the
            // receive tx is entirely absent. Fail closed on two axes: only
            // prune recent receives (deep disappearance more likely indicates
            // a faulty server than a reorg, and a false prune outside the
            // backfill window would hide funds until manual rescan), and only
            // when the BDK wallet doesn't know the tx either (our own sends
            // are always known even if Electrum lags).
            val inPruneWindow =
                tipHeight != null && (tipHeight - utxo.height) < SP_REORG_WINDOW_BLOCKS
            if (receiveEntry == null && utxo.height > 0 && inPruneWindow) {
                val receiveStillKnown =
                    runCatching {
                        val id = org.bitcoindevkit.Txid.fromString(utxo.txid)
                        wallet?.getTx(id) != null
                    }.getOrDefault(true)
                if (!receiveStillKnown) {
                    phantomTxids += utxo.txid
                    changed = true
                    return@forEachIndexed
                }
            }
            val spendTxid = spendEntry?.first
            // Reconcile optimistic spends: markSilentPaymentUtxosSpent flips
            // spent=true at broadcast with spendHeight=0. If the spend never
            // confirms and vanishes from both Electrum history and the BDK
            // wallet (evicted/abandoned broadcast), un-spend so the UTXO
            // becomes spendable again instead of stuck forever. The same
            // applies to confirmed spends evicted by a reorg: history is only
            // fetched for in-window spends (see needsSpendReorgCheck), so
            // reaching here with no spend in history means the recorded spend
            // is gone and the wallet doesn't know it either.
            if (spendTxid == null && utxo.spent && !utxo.spendTxid.isNullOrBlank()) {
                val spendStillKnown =
                    runCatching {
                        val id = org.bitcoindevkit.Txid.fromString(utxo.spendTxid)
                        wallet?.getTx(id) != null
                    }.getOrDefault(true)
                if (!spendStillKnown) {
                    utxos[index] =
                        utxo.copy(
                            spent = false,
                            spendTxid = null,
                            spendFeeSats = null,
                            spendAddress = null,
                            spendTimestamp = null,
                            spendHeight = 0,
                        )
                    changed = true
                    return@forEachIndexed
                }
            }
            val spendHeight = maxOf(utxo.spendHeight, spendEntry?.second?.takeIf { it > 0 } ?: 0)
            val receiveJson = if ((utxo.timestamp ?: 0L) <= 0L) proxy.getVerboseTransaction(utxo.txid) else null
            val receiveTs =
                receiveJson?.let(::verboseTxTimestamp)
                    ?: resolveSilentPaymentTimestamp(utxo.timestamp, newHeight, unconfirmedFallback = true)
            val spendMeta =
                if (spendTxid != null) {
                    hydrateSilentPaymentSpend(proxy, utxos, spendTxid)
                } else {
                    null
                }
            if (spendTxid == null && receiveTs == null && newHeight <= utxo.height && spendHeight <= utxo.spendHeight) {
                return@forEachIndexed
            }
            // Prefer the block timestamp once confirmed; the mempool first-seen
            // value stays only while unconfirmed.
            val confirmedTs =
                if (newHeight > 0 && newHeight != utxo.height) {
                    silentPaymentTimestampForHeight(newHeight)
                } else {
                    null
                }
            // Electrum-detected spends have no local broadcast time; stamp
            // newly discovered spends with now (mempool) or block time
            // (confirmed) so history never shows a blank date.
            var spendTs = spendMeta?.third ?: utxo.spendTimestamp
            if ((spendTs ?: 0L) <= 0L && spendTxid != null) {
                spendTs =
                    if (spendHeight > 0) {
                        silentPaymentTimestampForHeight(spendHeight)
                    } else {
                        System.currentTimeMillis() / 1000L
                    }
            }
            utxos[index] =
                utxo.copy(
                    height = newHeight,
                    spent = utxo.spent || spendTxid != null,
                    timestamp = confirmedTs ?: receiveTs ?: utxo.timestamp,
                    spendTxid = spendTxid ?: utxo.spendTxid,
                    spendFeeSats = spendMeta?.first ?: utxo.spendFeeSats,
                    spendAddress = spendMeta?.second ?: utxo.spendAddress,
                    spendTimestamp = spendTs ?: utxo.spendTimestamp,
                    spendHeight = spendHeight,
                )
            changed = true
        }
        if (phantomTxids.isNotEmpty()) {
            val phantom = phantomTxids.map { it.lowercase() }.toSet()
            utxos.removeAll { it.txid.lowercase() in phantom }
        }
        return changed
    }

    private fun hydrateSilentPaymentSpend(
        proxy: CachingElectrumProxy,
        utxos: List<SilentPaymentUtxo>,
        spendTxid: String,
    ): Triple<ULong?, String?, Long?>? {
        val txJson = proxy.getVerboseTransaction(spendTxid) ?: return null
        val timestamp = verboseTxTimestamp(txJson)
        val vins = txJson.optJSONArray("vin")
        val ourOutpoints = utxos.map { it.outpoint.lowercase() }.toSet()
        val ourByOutpoint = utxos.associateBy { it.outpoint.lowercase() }
        var ourInputSum = 0UL
        var vinCount = 0
        var ourVinCount = 0
        if (vins != null) {
            for (i in 0 until vins.length()) {
                val vin = vins.optJSONObject(i) ?: continue
                vinCount += 1
                val prev = "${vin.optString("txid", "")}:${vin.optInt("vout", -1)}"
                val ours = ourByOutpoint[prev.lowercase()] ?: continue
                ourVinCount += 1
                ourInputSum += ours.valueSats
            }
        }
        val vouts = txJson.optJSONArray("vout")
        var outputSum = 0UL
        var firstExternal: String? = null
        val network = wallet?.network()
        if (vouts != null) {
            for (i in 0 until vouts.length()) {
                val vout = vouts.optJSONObject(i) ?: continue
                outputSum += bitcoinJsonValueToSats(vout)
                if (firstExternal == null && network != null) {
                    val hex = vout.optJSONObject("scriptPubKey")?.optString("hex", "").orEmpty()
                    if (hex.isNotBlank() && utxos.none { it.scriptPubKeyHex.equals(hex, ignoreCase = true) }) {
                        firstExternal =
                            runCatching {
                                Address.fromScript(Script(hex.hexToByteArray()), network).toString()
                            }.getOrNull()
                    }
                }
            }
        }
        val fee =
            if (vinCount > 0 && vinCount == ourVinCount && ourInputSum > outputSum) {
                ourInputSum - outputSum
            } else {
                null
            }
        return Triple(fee, firstExternal, timestamp)
    }

    private fun scanSilentPaymentHistoryItem(
        proxy: CachingElectrumProxy,
        keys: SilentPayment.ReceiverKeys,
        item: SilentPaymentHistoryItem,
    ): List<SilentPaymentUtxo>? {
        // Sparrow fetches raw (non-verbose) tx and re-scans client-side.
        // Verbose JSON is unreliable for mempool txs on some Electrum servers.
        val rawHex = proxy.getRawTransactionHex(item.txHash)
        if (rawHex == null && BuildConfig.DEBUG) {
            Log.d(TAG, "SP scan: raw tx fetch failed for ${item.txHash}")
        }
        val txJson = if (rawHex == null) proxy.getVerboseTransaction(item.txHash) else null
        val outputs = silentPaymentOutputsFromTx(proxy, item.txHash, txJson, rawHex)
        if (outputs == null) {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "SP scan: could not parse outputs for ${item.txHash} (raw=${rawHex != null}, verbose=${txJson != null})")
            }
            return null
        }
        if (outputs.isEmpty()) {
            // A real transaction always has outputs — empty means the fetch
            // or parse came back hollow. Return null so the item is queued
            // for retry instead of being accepted as "no match" forever.
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "SP scan: no outputs parsed for ${item.txHash} (retrying)")
            }
            return null
        }
        val tweak =
            try {
                item.tweakKey.hexToByteArray()
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) {
                    Log.e(TAG, "SP scan: invalid tweak key for ${item.txHash}: ${item.tweakKey.take(20)}...", e)
                }
                return null
            }
        if (tweak.size != 33) {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "SP scan: tweak key wrong size ${tweak.size} for ${item.txHash}")
            }
            return null
        }
        val found =
            try {
                SilentPayment.scanOutputs(
                    tweakKey = tweak,
                    scanPrivateKey = keys.scanPrivateKey,
                    spendPrivateKey = keys.spendPrivateKey,
                    spendPublicKey = keys.spendPublicKey,
                    outputs = outputs,
                )
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) {
                    Log.e(TAG, "SP scan: scanOutputs failed for ${item.txHash}: ${e.message}", e)
                }
                return null
            }
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "SP scan: ${item.txHash} outputs=${outputs.size} p2tr=${outputs.count { it.scriptPubKey.size == 34 }} found=${found.size}")
        }
        return found.map { found ->
            SilentPaymentUtxo(
                txid = item.txHash,
                vout = found.vout,
                valueSats = found.valueSats,
                scriptPubKeyHex = found.scriptPubKey.joinToString("") { "%02x".format(it) },
                tweakKeyHex = item.tweakKey,
                tweakIndex = found.tweakIndex,
                isChange = found.isChange,
                height = item.height,
                timestamp =
                    resolveSilentPaymentTimestamp(
                        stored = txJson?.let(::verboseTxTimestamp),
                        height = item.height,
                        unconfirmedFallback = true,
                    ),
            )
        }
    }

    private fun silentPaymentOutputsFromTx(
        proxy: CachingElectrumProxy,
        txid: String,
        txJson: JSONObject?,
        rawHex: String? = null,
    ): List<SilentPayment.TxOutput>? {
        if (txJson != null) {
            val vouts = txJson.optJSONArray("vout") ?: return null
            val outputs = mutableListOf<SilentPayment.TxOutput>()
            for (i in 0 until vouts.length()) {
                val vout = vouts.optJSONObject(i) ?: continue
                val hex = vout.optJSONObject("scriptPubKey")?.optString("hex", "").orEmpty()
                if (hex.isBlank()) continue
                outputs +=
                    SilentPayment.TxOutput(
                        scriptPubKey = hex.hexToByteArray(),
                        valueSats = bitcoinJsonValueToSats(vout),
                    )
            }
            return outputs
        }
        // Reuse the caller's raw fetch — refetching here doubled Tor cost on
        // every cache miss during backfill scans.
        val hex = rawHex ?: proxy.getRawTransactionHex(txid) ?: return null
        val tx =
            runCatching { Transaction(hex.hexToByteArray()) }.getOrNull() ?: return null
        return tx.output().map { out ->
            SilentPayment.TxOutput(
                scriptPubKey = out.scriptPubkey.toBytes(),
                valueSats = out.value.toSat(),
            )
        }
    }

    private fun verboseTxTimestamp(txJson: JSONObject): Long? {
        val blocktime = txJson.optLong("blocktime", 0L)
        if (blocktime > 0L) return blocktime
        val time = txJson.optLong("time", 0L)
        if (time > 0L) return time
        val nested = txJson.optLong("block_time", 0L)
        return nested.takeIf { it > 0L }
    }

    private fun silentPaymentTimestampForHeight(height: Int): Long? {
        if (height <= 0) return null
        silentPaymentBlockTimes[height]?.let { return it }
        val fromClient =
            runCatching { electrumClient?.blockHeader(height.toULong())?.time?.toLong() }
                .getOrNull()
                ?.takeIf { it > 0L }
        val ts = fromClient ?: cachingProxy?.getBlockTimestamp(height) ?: return null
        silentPaymentBlockTimes[height] = ts
        return ts
    }

    private fun resolveSilentPaymentTimestamp(
        stored: Long?,
        height: Int,
        unconfirmedFallback: Boolean,
    ): Long? {
        stored?.takeIf { it > 0L }?.let { return it }
        silentPaymentTimestampForHeight(height)?.let { return it }
        if (unconfirmedFallback && height <= 0) {
            return System.currentTimeMillis() / 1000L
        }
        return null
    }

    private fun String.hexToByteArray(): ByteArray {
        require(length % 2 == 0)
        return chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }

    private fun bitcoinJsonValueToSats(vout: JSONObject): ULong {
        if (vout.has("valueSat") && !vout.isNull("valueSat")) {
            return vout.optLong("valueSat", 0L).coerceAtLeast(0L).toULong()
        }
        val raw = vout.opt("value") ?: return 0UL
        val text =
            when (raw) {
                is Number -> java.math.BigDecimal(raw.toString())
                is String -> raw.trim().toBigDecimalOrNull()
                else -> null
            } ?: return 0UL
        return text
            .movePointRight(8)
            .setScale(0, java.math.RoundingMode.HALF_UP)
            .toLong()
            .coerceAtLeast(0L)
            .toULong()
    }

    // In-list variants below never touch storage themselves: callers must hold
    // [spUtxoMutex] and persist the list once after all mutations.
    private fun repairSilentPaymentUtxoAmountsInList(
        proxy: CachingElectrumProxy,
        utxos: MutableList<SilentPaymentUtxo>,
    ): Boolean {
        if (utxos.none { it.valueSats >= 100_000_000UL }) return false
        var changed = false
        utxos.forEachIndexed { index, utxo ->
            if (utxo.valueSats < 100_000_000UL) return@forEachIndexed
            val txJson = proxy.getVerboseTransaction(utxo.txid) ?: return@forEachIndexed
            val vouts = txJson.optJSONArray("vout") ?: return@forEachIndexed
            val vout = vouts.optJSONObject(utxo.vout) ?: return@forEachIndexed
            val corrected = bitcoinJsonValueToSats(vout)
            if (corrected > 0UL && corrected != utxo.valueSats) {
                utxos[index] = utxo.copy(valueSats = corrected)
                changed = true
            }
        }
        return changed
    }

    private fun fillSilentPaymentTimestampsInList(utxos: MutableList<SilentPaymentUtxo>): Boolean {
        var changed = false
        utxos.forEachIndexed { index, utxo ->
            if ((utxo.timestamp ?: 0L) > 0L) return@forEachIndexed
            val ts =
                resolveSilentPaymentTimestamp(
                    stored = null,
                    height = utxo.height,
                    unconfirmedFallback = true,
                ) ?: return@forEachIndexed
            utxos[index] = utxo.copy(timestamp = ts)
            changed = true
        }
        return changed
    }

    private fun applySilentPaymentUtxos(builder: TxBuilder): TxBuilder {
        val walletId = secureStorage.getActiveWalletId() ?: return builder
        val keys = silentPaymentKeys ?: return builder
        val frozen = frozenRefsForActiveWallet()
        val selected = coinControlOutpoints.get()
        val unspent =
            secureStorage.getSilentPaymentUtxos(walletId).filter { utxo ->
                !utxo.spent &&
                    utxo.outpoint !in frozen &&
                    (selected == null || utxo.outpoint in selected)
            }
        if (unspent.isEmpty()) return builder
        var next = builder
        unspent.forEach { utxo ->
            next = addSilentPaymentForeignUtxo(next, utxo)
        }
        return next
    }

    private fun addSilentPaymentForeignUtxo(
        builder: TxBuilder,
        utxo: SilentPaymentUtxo,
    ): TxBuilder {
        val script = Script(utxo.scriptPubKeyHex.hexToByteArray())
        val txout = TxOut(Amount.fromSat(utxo.valueSats), script)
        val outpoint = OutPoint(Txid.fromString(utxo.txid), utxo.vout.toUInt())
        wallet?.insertTxout(outpoint, txout)
        // BDK's signer demands the full previous transaction
        // (MissingNonWitnessUtxo otherwise), even for taproot inputs it
        // cannot sign itself. Reuse the cached verbose fetch from scanning,
        // falling back to a raw-tx fetch: most Electrum servers
        // (ElectrumX/Fulcrum/Electrs) omit "hex" from verbose responses.
        val nonWitnessUtxo = fetchSilentPaymentPrevTx(utxo.txid)
        if (nonWitnessUtxo == null && BuildConfig.DEBUG) {
            Log.w(TAG, "SP foreign UTXO ${utxo.outpoint} without prev tx — signer may reject")
        }
                val psbtInput =
                    Input(
                        nonWitnessUtxo = nonWitnessUtxo,
                        witnessUtxo = txout,
                partialSigs = emptyMap(),
                sighashType = null,
                redeemScript = null,
                witnessScript = null,
                bip32Derivation = emptyMap(),
                finalScriptSig = null,
                finalScriptWitness = null,
                ripemd160Preimages = emptyMap(),
                sha256Preimages = emptyMap(),
                hash160Preimages = emptyMap(),
                hash256Preimages = emptyMap(),
                tapKeySig = null,
                tapScriptSigs = emptyMap(),
                tapScripts = emptyMap(),
                tapKeyOrigins = emptyMap(),
                tapInternalKey = null,
                tapMerkleRoot = null,
                proprietary = emptyMap(),
                unknown = emptyMap(),
            )
        return builder.addForeignUtxo(outpoint, psbtInput, 230UL)
    }

    /**
     * Fetch the full previous transaction for a silent-payment UTXO so BDK
     * can sign the foreign input. Tries the cached verbose fetch first, then
     * falls back to a raw-tx fetch (verbose responses usually lack "hex").
     * Returns null when neither source yields a txid-matching transaction.
     */
    private fun fetchSilentPaymentPrevTx(txid: String): Transaction? {
        val proxy = cachingProxy
        val verboseHex =
            runCatching {
                proxy?.getVerboseTransaction(txid)?.optString("hex", "").orEmpty()
            }.getOrNull().orEmpty()
        val rawHex =
            if (verboseHex.length >= 20) {
                verboseHex
            } else {
                runCatching { proxy?.getRawTransactionHex(txid).orEmpty() }.getOrNull().orEmpty()
            }
        if (rawHex.length < 20) return null
        return runCatching {
            Transaction(rawHex.hexToByteArray()).takeIf {
                it.computeTxid().toString().equals(txid, ignoreCase = true)
            }
        }.getOrNull()
    }

    /**
     * Sign every silent-payment input in [psbt] with a hand-rolled BIP-340
     * signature over the BIP-341 key-path sighash, then inject
     * `tap_internal_key`/`tap_key_sig`/`final_script_witness` via byte-level
     * PSBT surgery. Miniscript cannot build a descriptor for foreign BIP-352
     * P2TR (empty `tap_key_origins`), so the witness is attached directly and
     * `finalize()` only covers remaining wallet-owned inputs.
     *
     * Returns a (possibly new) PSBT; callers must use the returned object.
     */
    @OptIn(ExperimentalUnsignedTypes::class)
    private fun signSilentPaymentInputs(
        psbt: Psbt,
        walletId: String? = null,
    ): Psbt {
        // walletId pins the send flow's wallet so a mid-send wallet switch
        // cannot mix another wallet's keys/records in. Null reads the current
        // wallet and is only for fee-estimate dry-runs that never broadcast.
        val resolvedWalletId = walletId ?: secureStorage.getActiveWalletId() ?: return psbt
        val unsigned = runCatching { psbt.extractTx() }.getOrNull() ?: return psbt
        // Lowercased: BDK renders txids lowercase while server-advertised
        // hashes may differ in case; an exact-match miss here would leave a
        // silent input unsigned and fail the send (fail closed, but noisy).
        val spentOutpoints = unsigned.input().map { "${it.previousOutput.txid}:${it.previousOutput.vout}".lowercase() }.toSet()
        // No `!it.spent` filter: an RBF replacement re-spends the same SP
        // UTXOs the original already marked spent. Filtering them out here
        // would leave the input unsigned and fail the bump at broadcast.
        // Scope is already restricted to this transaction's inputs above.
        val matching =
            secureStorage.getSilentPaymentUtxos(resolvedWalletId).filter { it.outpoint.lowercase() in spentOutpoints }
        if (matching.isEmpty()) return psbt
        // SP inputs ARE being spent: missing keys/wallet must throw loudly
        // instead of returning the PSBT unsigned (an unsigned SP input fails
        // consensus at broadcast, but an explicit error names the cause).
        val keys = silentPaymentKeys ?: throw IllegalStateException("Silent payment keys unavailable for signing")
        val currentWallet = wallet ?: throw IllegalStateException("Wallet unavailable for SP signing")
        val walletOutputsByOutpoint =
            runCatching { currentWallet.listOutput() }.getOrNull()
                ?.associateBy { "${it.outpoint.txid}:${it.outpoint.vout}" }
                .orEmpty()
        val spByOutpoint =
            secureStorage.getSilentPaymentUtxos(resolvedWalletId).associateBy { it.outpoint.lowercase() }
        val sighashInputs =
            unsigned.input().map { txIn ->
                val key = "${txIn.previousOutput.txid}:${txIn.previousOutput.vout}".lowercase()
                val sp = spByOutpoint[key]
                if (sp != null) {
                    SilentPayment.SighashTxIn(
                        txidHex = txIn.previousOutput.txid.toString(),
                        vout = txIn.previousOutput.vout,
                        sequence = txIn.sequence,
                        prevScriptPubKey = sp.scriptPubKeyHex.hexToByteArray(),
                        prevValueSats = sp.valueSats,
                    )
                } else {
                    val local =
                        walletOutputsByOutpoint[key]
                            ?: throw IllegalStateException("Missing prevout data for $key")
                    SilentPayment.SighashTxIn(
                        txidHex = txIn.previousOutput.txid.toString(),
                        vout = txIn.previousOutput.vout,
                        sequence = txIn.sequence,
                        prevScriptPubKey = local.txout.scriptPubkey.toBytes(),
                        prevValueSats = local.txout.value.toSat(),
                    )
                }
            }
        val sighashOutputs =
            unsigned.output().map { txOut ->
                SilentPayment.SighashTxOut(
                    valueSats = txOut.value.toSat(),
                    scriptPubKey = txOut.scriptPubkey.toBytes(),
                )
            }
        val injections = mutableMapOf<Int, Pair<ByteArray, ByteArray>>()
        matching.forEach { utxo ->
            val found =
                SilentPayment.scanOutputs(
                    tweakKey = utxo.tweakKeyHex.hexToByteArray(),
                    scanPrivateKey = keys.scanPrivateKey,
                    spendPrivateKey = keys.spendPrivateKey,
                    spendPublicKey = keys.spendPublicKey,
                    outputs =
                        listOf(
                            SilentPayment.TxOutput(
                                scriptPubKey = utxo.scriptPubKeyHex.hexToByteArray(),
                                valueSats = utxo.valueSats,
                            ),
                        ),
                ).firstOrNull()
                    ?: throw IllegalStateException("Could not rederive silent payment key for ${utxo.outpoint}")
            val evenKey = SilentPayment.evenYPrivateKey(found.spendPrivateKey)
            val inputIndex =
                unsigned.input().indexOfFirst {
                    "${it.previousOutput.txid}:${it.previousOutput.vout}".lowercase() == utxo.outpoint.lowercase()
                }
            if (inputIndex < 0) {
                throw IllegalStateException("Silent payment input ${utxo.outpoint} missing from transaction")
            }
            val sighash =
                SilentPayment.taprootKeySpendSighash(
                    version = unsigned.version(),
                    lockTime = unsigned.lockTime(),
                    inputs = sighashInputs,
                    outputs = sighashOutputs,
                    inputIndex = inputIndex,
                )
            val sig = SilentPayment.schnorrSign(evenKey, sighash)
            if (!SilentPayment.schnorrVerify(found.xOnlyPublicKey, sighash, sig)) {
                throw IllegalStateException("Silent payment signature self-check failed for ${utxo.outpoint}")
            }
            injections[inputIndex] = found.xOnlyPublicKey to sig
        }
        val raw =
            try {
                android.util.Base64.decode(psbt.serialize(), android.util.Base64.DEFAULT)
            } catch (e: Exception) {
                throw IllegalStateException("Could not serialize PSBT for silent payment signing", e)
            }
        val modified =
            try {
                SilentPayment.psbtInjectTapKeySigs(raw, unsigned.input().size, injections)
            } catch (e: Exception) {
                throw IllegalStateException(
                    "Silent payment PSBT injection failed: ${e.message?.ifBlank { null } ?: e::class.simpleName}",
                    e,
                )
            }
        val inputCount = unsigned.input().size
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "SP sign: injecting ${injections.size} tap sig(s) into $inputCount input(s)")
        }
        for ((index, _) in injections) {
            if (!SilentPayment.psbtVerifyTapInjection(modified, inputCount, index)) {
                throw IllegalStateException("Silent payment PSBT injection missing at input $index")
            }
        }
        val rebuilt =
            try {
                Psbt(android.util.Base64.encodeToString(modified, android.util.Base64.NO_WRAP))
            } catch (e: Exception) {
                throw IllegalStateException("Silent payment PSBT rebuild failed", e)
            }
        if (BuildConfig.DEBUG) {
            runCatching {
                val dumped = rebuilt.jsonSerialize()
                val forLog = dumped.replace(Regex("""("tap_key_sig":\s*")[^"]{8}[^"]*(")"""), "$1<sig>$2")
                Log.d(TAG, "SP sign: rebuilt inputs=${rebuilt.input().size} tapFieldsPresent=${dumped.contains("tap_key_sig")}")
                Log.d(TAG, "SP sign: PSBT JSON: $forLog")
            }
        }
        if (injections.size == unsigned.input().size) {
            return rebuilt
        }
        val finalized =
            try {
                rebuilt.finalize()
            } catch (e: Exception) {
                throw IllegalStateException(
                    "Silent payment finalize failed: ${e.message?.ifBlank { null } ?: e::class.simpleName}",
                    e,
                )
            }
        if (!finalized.couldFinalize) {
            runCatching { rebuilt.extractTx() }.getOrNull()
                ?: throw IllegalStateException("Could not finalize silent payment inputs")
            return rebuilt
        }
        return finalized.psbt
    }

    private suspend fun markSilentPaymentUtxosSpent(
        tx: Transaction,
        walletId: String? = null,
    ) {
        val resolvedWalletId = walletId ?: secureStorage.getActiveWalletId() ?: return
        val spentOutpoints = tx.input().map { "${it.previousOutput.txid}:${it.previousOutput.vout}".lowercase() }.toSet()
        spUtxoMutex.withLock {
            val existing = secureStorage.getSilentPaymentUtxos(resolvedWalletId)
            if (existing.none { it.outpoint.lowercase() in spentOutpoints && !it.spent }) return@withLock
            val spendTxid = runCatching { tx.computeTxid().toString() }.getOrNull()
            // Fee spans ALL inputs (silent + wallet-owned), not just the
            // silent ones: under-reporting here corrupts spend metadata.
            // Unknown prevout values fail the fee to null, never to a wrong
            // number.
            val inputValues =
                spentOutpoints.map { outpoint ->
                    existing.firstOrNull { it.outpoint.equals(outpoint, ignoreCase = true) }?.valueSats
                        ?: runCatching {
                            val parts = outpoint.split(":")
                            val txid = org.bitcoindevkit.Txid.fromString(parts[0])
                            val vout = parts[1].toUInt()
                            wallet?.getUtxo(OutPoint(txid, vout))?.txout?.value?.toSat()
                        }.getOrNull()
                }
            val outputSum = tx.output().fold(0UL) { acc, out -> acc + out.value.toSat() }
            val spendFee =
                if (inputValues.all { it != null }) {
                    inputValues.filterNotNull().fold(0UL) { acc, value -> acc + value }
                        .takeIf { it > outputSum }?.minus(outputSum)
                } else {
                    null
                }
            val network = wallet?.network()
            val spendAddress =
                if (network != null) {
                    tx.output().firstOrNull()?.let { out ->
                        runCatching { Address.fromScript(out.scriptPubkey, network).toString() }.getOrNull()
                    }
                } else {
                    null
                }
            val now = System.currentTimeMillis() / 1000L
            secureStorage.saveSilentPaymentUtxos(
                resolvedWalletId,
                existing.map { utxo ->
                    if (utxo.outpoint.lowercase() in spentOutpoints) {
                        utxo.copy(
                            spent = true,
                            spendTxid = spendTxid ?: utxo.spendTxid,
                            spendFeeSats = spendFee ?: utxo.spendFeeSats,
                            spendAddress = spendAddress ?: utxo.spendAddress,
                            spendTimestamp = now,
                            spendHeight = 0,
                        )
                    } else {
                        utxo
                    }
                },
            )
        }
        updateWalletState()
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
                val address =
                    resolveCurrentReceiveAddress(currentWallet, activeWalletId)
                        ?: return@withContext WalletResult.Error("Failed to generate address")

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
                persistL1ReceiveAddress(activeWalletId, address)

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
     * Save a label for an address.
     * Non-blank labels reserve the address for future receive selection (skipped by
     * resolveCurrentReceiveAddress / reclaim) but do not mark it Used in the address
     * book and do not auto-advance the currently shown receive address.
     */
    fun saveAddressLabel(
        address: String,
        label: String,
    ) {
        val activeWalletId = secureStorage.getActiveWalletId() ?: return
        secureStorage.saveAddressLabel(activeWalletId, address, label)
        updateBitcoinAddressLabelIndex(activeWalletId, address, label)

        // Update state if this is the current address (stay on it; do not advance).
        val currentState = _walletState.value
        if (currentState.currentAddress == address) {
            _walletState.value =
                currentState.copy(
                    currentAddressInfo = currentState.currentAddressInfo?.copy(label = label.ifBlank { null }),
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
        limit: Int,
    ): TransactionSearchResult {
        val activeWalletId = secureStorage.getActiveWalletId() ?: return TransactionSearchResult(emptyList(), 0)
        val hiddenTxids = secureStorage.getHiddenBitcoinTransactionIds(activeWalletId)
        val result = electrumCache.searchTransactionTxids(
            walletId = activeWalletId,
            layer = TransactionSearchLayer.BITCOIN,
            query = query,
            limit = limit,
            filters = TransactionSearchFilters(),
        )
        if (hiddenTxids.isEmpty()) return result
        val visibleTxids = result.txids.filterNot { it in hiddenTxids }
        return result.copy(
            txids = visibleTxids,
            totalCount = (result.totalCount - (result.txids.size - visibleTxids.size)).coerceAtLeast(0),
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

    fun deleteTransactionFromHistory(txid: String) {
        val activeWalletId = secureStorage.getActiveWalletId() ?: return
        secureStorage.hideBitcoinTransaction(activeWalletId, txid)
        secureStorage.purgeHiddenBitcoinTransactionMetadata(activeWalletId, txid)
        electrumCache.deleteTransactionCache(txid, activeWalletId)
        electrumCache.deleteTransactionSearchDocuments(
            walletId = activeWalletId,
            layer = TransactionSearchLayer.BITCOIN,
            txids = listOf(txid),
        )
        val currentState = _walletState.value
        val visibleTransactions = currentState.transactions.filterNot { it.txid == txid }
        if (visibleTransactions.size != currentState.transactions.size) {
            val checksum = buildWalletStateChecksum(currentState.balanceSats, visibleTransactions)
            _walletState.value = currentState.copy(
                pendingIncomingSats = checksum.pendingIncomingSats,
                pendingOutgoingSats = checksum.pendingOutgoingSats,
                transactions = visibleTransactions,
            )
        }
    }

    fun deleteAllTransactionsFromHistory() {
        val activeWalletId = secureStorage.getActiveWalletId() ?: return
        val currentState = _walletState.value
        val txids = currentState.transactions.map { it.txid }.filter { it.isNotBlank() }.distinct()
        if (txids.isEmpty()) return

        txids.forEach { txid ->
            secureStorage.hideBitcoinTransaction(activeWalletId, txid)
            secureStorage.purgeHiddenBitcoinTransactionMetadata(activeWalletId, txid)
            electrumCache.deleteTransactionCache(txid, activeWalletId)
        }
        electrumCache.deleteTransactionSearchDocuments(
            walletId = activeWalletId,
            layer = TransactionSearchLayer.BITCOIN,
            txids = txids,
        )
        val checksum = buildWalletStateChecksum(currentState.balanceSats, emptyList())
        _walletState.value = currentState.copy(
            pendingIncomingSats = checksum.pendingIncomingSats,
            pendingOutgoingSats = checksum.pendingOutgoingSats,
            transactions = emptyList(),
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

    fun getLiquidMetadataSnapshotForWallet(walletId: String): SecureStorage.LiquidMetadataSnapshot {
        return secureStorage.getLiquidMetadataSnapshot(walletId)
    }

    fun getAllSparkAddressLabelsForWallet(walletId: String): Map<String, String> {
        return secureStorage.getAllSparkAddressLabels(walletId)
    }

    fun getAllSparkTransactionLabelsForWallet(walletId: String): Map<String, String> {
        return secureStorage.getAllSparkTransactionLabels(walletId)
    }

    fun getAllSparkPaymentRecipientsForWallet(walletId: String): Map<String, String> {
        return secureStorage.getAllSparkPaymentRecipients(walletId)
    }

    fun getAllSparkDepositAddressesForWallet(walletId: String): Map<String, String> {
        return secureStorage.getAllSparkDepositAddresses(walletId)
    }

    fun getAllSparkPendingDepositsForWallet(walletId: String): List<SparkUnclaimedDeposit> {
        return secureStorage.getAllSparkPendingDeposits(walletId)
    }

    fun getSparkOnchainDepositAddressForWallet(walletId: String): String? {
        return secureStorage.getSparkOnchainDepositAddress(walletId)
    }

    fun getAllSparkTransactionSourcesForWallet(walletId: String): Map<String, String> {
        return secureStorage.getAllSparkTransactionSources(walletId)
    }

    fun getAllArkMovementLabelsForWallet(walletId: String): Map<String, String> {
        return secureStorage.getAllArkMovementLabels(walletId)
    }

    fun getAllArkAddressLabelsForWallet(walletId: String): Map<String, String> {
        return secureStorage.getAllArkAddressLabels(walletId)
    }

    fun getArkFundingTxidsForWallet(walletId: String): List<String> =
        secureStorage.getArkFundingTxids(walletId)

    fun setArkFundingTxidsForWallet(
        walletId: String,
        txids: List<String>,
    ) {
        secureStorage.setArkFundingTxids(walletId, txids)
    }

    fun getAllTransactionSwapDetailsForWallet(walletId: String): Map<String, LiquidSwapDetails> {
        return secureStorage.getAllTransactionSwapDetails(walletId)
    }

    fun getAllSilentPaymentRecipientsForWallet(
        walletId: String,
    ): Map<String, List<Recipient>> = secureStorage.getAllSilentPaymentRecipients(walletId)

    fun saveSilentPaymentRecipientsForWallet(
        walletId: String,
        destinations: Map<String, List<Recipient>>,
    ) {
        destinations.forEach { (txid, recipients) ->
            persistSilentPaymentRecipients(walletId, txid, recipients)
        }
    }

    fun getSilentPaymentUtxosForWallet(walletId: String): List<SilentPaymentUtxo> =
        secureStorage.getSilentPaymentUtxos(walletId)

    fun saveSilentPaymentUtxosForWallet(
        walletId: String,
        utxos: List<SilentPaymentUtxo>,
    ) {
        secureStorage.saveSilentPaymentUtxos(walletId, utxos)
    }

    fun getSilentPaymentPendingForWallet(walletId: String): List<SilentPaymentPendingItem> =
        secureStorage.getSilentPaymentPendingItems(walletId)

    fun saveSilentPaymentPendingForWallet(
        walletId: String,
        items: List<SilentPaymentPendingItem>,
    ) {
        secureStorage.saveSilentPaymentPendingItems(walletId, items)
    }

    fun getSilentPaymentScanHeightForWallet(walletId: String): Int =
        secureStorage.getSilentPaymentScanHeight(walletId)

    fun setSilentPaymentScanHeightForWallet(
        walletId: String,
        height: Int,
    ) {
        secureStorage.setSilentPaymentScanHeight(walletId, height)
    }

    /** Txids with a known-non-SP (`[]`) marker — exported so restores keep the RBF distinction. */
    fun getKnownNonSilentPaymentTxidsForWallet(walletId: String): List<String> =
        secureStorage.getKnownNonSilentPaymentTxids(walletId)

    fun saveKnownNonSilentPaymentMarkerForWallet(
        walletId: String,
        txid: String,
    ) {
        // Never overwrite a destinations record restored from the same
        // backup (import restores destinations first, markers second).
        if (!secureStorage.hasSilentPaymentRecord(walletId, txid)) {
            secureStorage.saveNoSilentPaymentMarker(walletId, txid)
        }
    }

    fun canReceiveSilentPayments(storedWallet: StoredWallet?): Boolean {
        if (storedWallet == null) return false
        if (storedWallet.isWatchOnly) return false
        if (storedWallet.walletKind != WalletKind.BITCOIN) return false
        if (storedWallet.policyType != WalletPolicyType.SINGLE_SIG) return false
        if (storedWallet.derivationPath == "single") return false
        val walletId = storedWallet.id
        return secureStorage.getMnemonic(walletId) != null &&
            !secureStorage.hasPrivateKey(walletId) &&
            !secureStorage.hasExtendedKey(walletId)
    }

    fun silentPaymentAddressForActiveWallet(): String? = silentPaymentKeys?.address

    fun isSilentPaymentsServerSupported(): Boolean? = silentPaymentsSupported

    fun getSilentPaymentReceiveMode(): Boolean {
        val walletId = secureStorage.getActiveWalletId() ?: return false
        return secureStorage.getSilentPaymentReceiveMode(walletId)
    }

    fun setSilentPaymentReceiveMode(enabled: Boolean) {
        val walletId = secureStorage.getActiveWalletId() ?: return
        secureStorage.setSilentPaymentReceiveMode(walletId, enabled)
    }

    fun hasAcknowledgedSilentPaymentScanDisclosure(): Boolean {
        if (secureStorage.hasAcknowledgedSilentPaymentScanDisclosureGlobal()) return true
        // Legacy per-wallet flags (pre-global): an upgrader who already saw
        // the popup must not be re-prompted.
        val walletId = secureStorage.getActiveWalletId() ?: return true
        return secureStorage.hasAcknowledgedSilentPaymentScanDisclosure(walletId)
    }

    fun acknowledgeSilentPaymentScanDisclosure() {
        secureStorage.setSilentPaymentScanDisclosureAcknowledgedGlobal()
    }

    private fun silentPaymentUnspentSats(walletId: String?): ULong {
        if (walletId.isNullOrBlank()) return 0UL
        return secureStorage.getSilentPaymentUtxos(walletId)
            .filter { !it.spent }
            .fold(0UL) { acc, utxo -> acc + utxo.valueSats }
    }

    fun getAllTransactionSourcesForWallet(walletId: String): Map<String, String> {
        val storedSources = secureStorage.getAllTransactionSources(walletId)
        val swapSources =
            secureStorage.getAllTransactionSwapDetails(walletId).keys.associateWith {
                BITCOIN_SOURCE_CHAIN_SWAP
            }
        return storedSources + swapSources
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

    fun saveTransactionSourceForWallet(
        walletId: String,
        txid: String,
        source: String,
    ) {
        secureStorage.saveTransactionSource(walletId, txid, source)
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

    fun saveArkMovementLabelsForWallet(
        walletId: String,
        labels: Map<String, String>,
    ) {
        secureStorage.saveArkMovementLabels(walletId, labels)
    }

    fun saveArkAddressLabelsForWallet(
        walletId: String,
        labels: Map<String, String>,
    ) {
        secureStorage.saveArkAddressLabels(walletId, labels)
    }

    /**
     * Full-app backup no longer embeds a durable local Ark DB (mailbox + external SAF only).
     * Always null — Ark offline copies live on the Backup Ark DB / auto-backup SAF folder.
     */
    fun exportArkWalletDataBase64(walletId: String): String? = null

    /**
     * Full-app restore ignores embedded Ark DB blobs. Use the Ark Backup tab / external SAF
     * for disaster restore. Keeps suppress flag so a first load does not spam auto-backup.
     */
    fun importArkWalletDataBase64(
        walletId: String,
        base64: String,
    ) {
        if (base64.isBlank()) return
        // Legacy backups may still carry an arkDb payload — discard without writing filesDir.
        secureStorage.markArkAutoDbBackupSuppressOnce(walletId)
        secureStorage.clearArkWalletStateCache(walletId)
    }

    private fun resolveBip39SeedForArkBackup(walletId: String): ByteArray? {
        val raw = secureStorage.getMnemonic(walletId) ?: return null
        if (raw.isBlank()) return null
        return bip39SeedCanonical(raw, secureStorage.getPassphrase(walletId))
    }

    fun saveSparkTransactionSourceForWallet(
        walletId: String,
        paymentId: String,
        source: String,
    ) {
        secureStorage.saveSparkTransactionSource(walletId, paymentId, source)
    }

    fun saveSparkPaymentRecipientForWallet(
        walletId: String,
        paymentId: String,
        recipient: String,
    ) {
        secureStorage.saveSparkPaymentRecipient(walletId, paymentId, recipient)
    }

    fun saveSparkDepositAddressForWallet(
        walletId: String,
        txid: String,
        address: String,
    ) {
        secureStorage.saveSparkDepositAddress(walletId, txid, address)
    }

    fun saveSparkPendingDepositForWallet(
        walletId: String,
        deposit: SparkUnclaimedDeposit,
    ) {
        secureStorage.saveSparkPendingDeposit(walletId, deposit)
    }

    fun setSparkOnchainDepositAddressForWallet(
        walletId: String,
        address: String,
    ) {
        secureStorage.setSparkOnchainDepositAddress(walletId, address)
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
        secureStorage.saveTransactionSource(walletId, txid, BITCOIN_SOURCE_CHAIN_SWAP)
    }

    /** Mark an L1 tx as initiated from the center Swap control (Liquid / Spark / Ark). */
    fun markBitcoinCenterSwapTx(
        walletId: String,
        txid: String,
    ) {
        val normalized = txid.trim()
        if (normalized.isBlank()) return
        secureStorage.saveTransactionSource(walletId, normalized, BitcoinTxSource.CENTER_SWAP)
    }

    fun isBitcoinCenterSwapSource(source: String?): Boolean = BitcoinTxSource.isSwapHistory(source)

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
                    val label = labels[address]
                    val entry = WalletAddress(
                        address = address,
                        index = index,
                        keychain = keychainType,
                        label = label,
                        balanceSats = 0UL,
                        transactionCount = 0,
                        isUsed = false,
                    )
                    unused.add(entry)
                }
                index++
            }
            return unused
        }

        val receiveAddresses = buildPreviewForKeychain(KeychainKind.EXTERNAL, KeychainType.EXTERNAL)
        val changeAddresses = buildPreviewForKeychain(KeychainKind.INTERNAL, KeychainType.INTERNAL)
        // Used tab is display-only privacy: hide empty historical addresses, keep funded.
        // Do not reclassify empty used into Receive/Change (still chain-used for gap/nextUnused).
        return Triple(receiveAddresses, changeAddresses, usedAddresses.filter { it.balanceSats > 0UL })
    }

    /**
     * Get all addresses for the wallet (receive, change, used)
     */
    fun getAllAddresses(): Triple<List<WalletAddress>, List<WalletAddress>, List<WalletAddress>> {
        val currentWallet = wallet ?: return Triple(emptyList(), emptyList(), emptyList())
        val activeWalletId = secureStorage.getActiveWalletId() ?: return Triple(emptyList(), emptyList(), emptyList())
        val labels = secureStorage.getAllAddressLabels(activeWalletId)
        // Same gap for receive and change: show at least the configured unused window on both.
        val gapLimit = getWalletGapLimit(activeWalletId).coerceAtLeast(1)
        val targetUnusedCount = maxOf(gapLimit, ADDRESS_PEEK_AHEAD)

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
            val highestUsedIndex =
                outputCountByIndex.keys
                    .asSequence()
                    .filter { it.first == keychain }
                    .maxOfOrNull { it.second.toInt() }
                    ?: -1
            // Cover gap past highest used even when BDK has not revealed that far yet.
            val gapCoverageIndex = (highestUsedIndex + gapLimit).coerceAtLeast(gapLimit - 1)
            val lastRevealedInt = lastRevealed?.toInt() ?: -1
            val endIndex = maxOf(lastRevealedInt, gapCoverageIndex)

            if (endIndex >= 0) {
                var i = 0u
                while (i.toInt() <= endIndex) {
                    val addr = peekAddressStringCached(currentWallet, keychain, i)
                    val key = keychain to i
                    val count = outputCountByIndex[key] ?: 0
                    val balance = balanceByIndex[key] ?: 0UL
                    val label = labels[addr]
                    // Used = on-chain activity only. Labels reserve receive selection, not Used tab.
                    val isUsed = count > 0
                    val entry =
                        WalletAddress(
                            address = addr,
                            index = i,
                            keychain = keychainType,
                            label = label,
                            balanceSats = balance,
                            transactionCount = count,
                            isUsed = isUsed,
                        )
                    if (isUsed) usedAddresses.add(entry) else targetUnused.add(entry)
                    i = i.inc()
                }
            }

            val startIndex = (endIndex + 1).coerceAtLeast(0).toUInt()
            val peekCount = maxOf(0, targetUnusedCount - targetUnused.size)
            for (offset in 0u until peekCount.toUInt()) {
                val index = startIndex + offset
                val addr = peekAddressStringCached(currentWallet, keychain, index)
                val label = labels[addr]
                val entry = WalletAddress(
                    address = addr,
                    index = index,
                    keychain = keychainType,
                    label = label,
                    balanceSats = 0UL,
                    transactionCount = 0,
                    isUsed = false,
                )
                targetUnused.add(entry)
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

        // Used tab is display-only privacy: hide empty historical addresses, keep funded.
        // Do not reclassify empty used into Receive/Change (still chain-used for gap/nextUnused).
        return Triple(receiveAddresses, changeAddresses, usedAddresses.filter { it.balanceSats > 0UL })
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
            val walletUtxos =
            utxos.mapNotNull { utxo ->
                try {
                    val txid = utxo.outpoint.txid.toString()
                    val addr = Address.fromScript(utxo.txout.scriptPubkey, network).toString()
                    val outpoint = "${utxo.outpoint.txid}:${utxo.outpoint.vout}"

                    // Use the wallet transaction list's chain position so confirmation updates
                    // stay consistent with the rest of the BTC UI.
                    val chainPos = txPositions[txid]
                    val isConfirmed = chainPos is ChainPosition.Confirmed
                    val timestamp =
                        when (chainPos) {
                            is ChainPosition.Confirmed ->
                                chainPos.confirmationBlockTime.confirmationTime.toLong().takeIf { it > 0L }
                            is ChainPosition.Unconfirmed -> {
                                val bdkLastSeen = chainPos.timestamp?.toLong()?.takeIf { it > 0L }
                                val firstSeen = secureStorage.getTxFirstSeen(activeWalletId, txid)
                                firstSeen ?: bdkLastSeen
                            }
                            null -> secureStorage.getTxFirstSeen(activeWalletId, txid)
                        }

                    UtxoInfo(
                        outpoint = outpoint,
                        txid = txid,
                        vout = utxo.outpoint.vout,
                        address = addr,
                        amountSats = utxo.txout.value.toSat(),
                        label = labels[addr],
                        isConfirmed = isConfirmed,
                        isFrozen = frozenUtxos.contains(outpoint),
                        timestamp = timestamp,
                    )
                } catch (e: Exception) {
                    if (BuildConfig.DEBUG) Log.e(TAG, "Error parsing UTXO: ${e.message}")
                    null
                }
            }
            val knownOutpoints = walletUtxos.map { it.outpoint }.toSet()
            val silentUtxos =
                secureStorage.getSilentPaymentUtxos(activeWalletId).mapNotNull { utxo ->
                    if (utxo.spent || utxo.outpoint in knownOutpoints) return@mapNotNull null
                    val address =
                        runCatching {
                            Address.fromScript(
                                Script(utxo.scriptPubKeyHex.hexToByteArray()),
                                network,
                            ).toString()
                        }.getOrNull() ?: return@mapNotNull null
                    UtxoInfo(
                        outpoint = utxo.outpoint,
                        txid = utxo.txid,
                        vout = utxo.vout.toUInt(),
                        address = address,
                        amountSats = utxo.valueSats,
                        label = labels[address],
                        isConfirmed = utxo.height > 0,
                        isFrozen = frozenUtxos.contains(utxo.outpoint),
                        isSilentPayment = true,
                    )
                }
            walletUtxos + silentUtxos
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
        return withSelectedUtxos(selectedUtxos) {
        val currentWallet = wallet ?: return@withSelectedUtxos 0UL
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
                ?: (
                    currentWallet.balance().total.toSat().toLong() +
                        silentPaymentUnspentSats(secureStorage.getActiveWalletId()).toLong()
                    )
        if (availableSats <= 0L) return 0UL

        // Candidate must clear the same two-pass feeAbsolute path used by dry-run/send.
        // feeRate-only search overstates max by a few sats when exact fee rounds up.
        fun canSendExact(candidate: Long, preferChangeOnly: Boolean?): Boolean {
            try {
                val configure: (TxBuilder) -> TxBuilder = { builder ->
                    manualSelection(
                        builder.addRecipient(recipientScript, Amount.fromSat(candidate.toULong())),
                    )
                }
                val pass1Psbt =
                    configure(
                        TxBuilder().applySendDefaults(preferChangeOnly).feeRate(feeRate),
                    ).finish(currentWallet)
                val exactFeeResult =
                    computeExactFee(pass1Psbt, currentWallet, pass1Psbt.extractTx(), feeRateSatPerVb)
                        ?: return true
                configure(
                    TxBuilder().applySendDefaults(preferChangeOnly).feeAbsolute(
                        Amount.fromSat(exactFeeResult.feeSats),
                    ),
                ).finish(currentWallet)
                return true
            } catch (_: Exception) {
                return false
            }
        }

        suspend fun maxUnderPolicy(preferChangeOnly: Boolean?): Long =
            findMaxExactSendAmount(availableSats) { candidate ->
                canSendExact(candidate, preferChangeOnly)
            }
        val maxAmountSats =
            if (secureStorage.getConsolidateChange()) {
                val changeOnlyMax = maxUnderPolicy(preferChangeOnly = true)
                if (changeOnlyMax > 0L) changeOnlyMax else maxUnderPolicy(preferChangeOnly = false)
            } else {
                maxUnderPolicy(preferChangeOnly = null)
            }
        return@withSelectedUtxos maxAmountSats.toULong()
        }
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
        return withSelectedUtxos(selectedUtxos) {
        val currentWallet = wallet ?: return@withSelectedUtxos null
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
        return@withSelectedUtxos entry
        }
    }

    private suspend fun prepareMultiRecipientTransaction(
        recipients: List<Recipient>,
        feeRateSatPerVb: Double,
        selectedUtxos: List<UtxoInfo>? = null,
        precomputedFeeSats: ULong? = null,
        includePsbtDetails: Boolean = false,
    ): PreparedBitcoinSendCacheEntry? {
        return withSelectedUtxos(selectedUtxos) {
        val currentWallet = wallet ?: return@withSelectedUtxos null
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
        return@withSelectedUtxos entry
        }
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
            withSelectedUtxos(selectedUtxos) {
            val currentWallet = wallet ?: return@withSelectedUtxos WalletResult.Error("Wallet not initialized")
            val client = electrumClient ?: return@withSelectedUtxos WalletResult.Error("Not connected to Electrum server")

            // Check if watch-only
            val activeWalletId = secureStorage.getActiveWalletId()
            val storedWallet = activeWalletId?.let { secureStorage.getWalletMetadata(it) }
            if (storedWallet?.policyType == WalletPolicyType.MULTISIG) {
                return@withContext WalletResult.Error("Use PSBT signing for multisig wallets")
            }
            if (storedWallet?.isWatchOnly == true) {
                return@withContext WalletResult.Error("Cannot send from watch-only wallet")
            }
            // Pin the wallet for the whole flow: re-reading the active id
            // later could mix another wallet's keys/records in after a
            // mid-send wallet switch.
            val sendWalletId = storedWallet?.id ?: activeWalletId
                ?: return@withSelectedUtxos WalletResult.Error("Wallet not initialized")

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
                val (psbt, usedPreferChangeOnly) =
                    withConsolidateFallback { preferChangeOnly ->
                        val built =
                            if (precomputedFeeSats != null) {
                                try {
                                    buildTx(
                                        TxBuilder().applySendDefaults(preferChangeOnly)
                                            .feeAbsolute(Amount.fromSat(precomputedFeeSats)),
                                    ).finish(currentWallet)
                                } catch (_: Exception) {
                                    // Fallback: two-pass if feeAbsolute with precomputed fee fails
                                    // (e.g. UTXO set changed between dry-run and send)
                                    val pass1Psbt =
                                        buildTx(
                                            TxBuilder().applySendDefaults(preferChangeOnly).feeRate(feeRate),
                                        ).finish(currentWallet)
                                    val exactFeeResult =
                                        computeExactFee(pass1Psbt, currentWallet, pass1Psbt.extractTx(), feeRateSatPerVb)
                                    if (exactFeeResult != null) {
                                        try {
                                            buildTx(
                                                TxBuilder().applySendDefaults(preferChangeOnly).feeAbsolute(
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
                                        TxBuilder().applySendDefaults(preferChangeOnly).feeRate(feeRate),
                                    ).finish(currentWallet)
                                onProgress("Computing fee...")
                                val exactFeeResult =
                                    computeExactFee(pass1Psbt, currentWallet, pass1Psbt.extractTx(), feeRateSatPerVb)
                                if (exactFeeResult != null) {
                                    try {
                                        buildTx(
                                            TxBuilder().applySendDefaults(preferChangeOnly).feeAbsolute(
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
                        built to preferChangeOnly
                    }

                val psbtForSigning =
                    if (usesSilentPayment) {
                        rebuildWithSilentPaymentOutputs(
                            placeholderPsbt = psbt,
                            currentWallet = currentWallet,
                            storedWallet = storedWallet,
                            recipients = sendRecipients,
                            feeSats = psbt.fee(),
                            walletId = sendWalletId,
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
                                        walletId = sendWalletId,
                                    )
                                } catch (_: Exception) {
                                    null
                                }
                            } else {
                                try {
                                    buildTx(
                                        TxBuilder().applySendDefaults(usedPreferChangeOnly)
                                            .feeAbsolute(Amount.fromSat(fee)),
                                    ).finish(currentWallet)
                                } catch (_: Exception) {
                                    null
                                }
                            }
                        },
                        spWalletId = sendWalletId,
                    ).tx

                onProgress("Broadcasting to network...")
                // Journal the SP record BEFORE broadcast: a kill between
                // broadcast and persist used to leave an on-chain SP tx with
                // no record, permanently blocking RBF. A record for a txid
                // that never broadcasts is harmless (bump fails at lookup).
                val txid = tx.computeTxid().toString()
                persistSilentPaymentRecipients(sendWalletId, txid, sendRecipients)
                client.transactionBroadcast(tx)
                markSilentPaymentUtxosSpent(tx, sendWalletId)

                // Save transaction label if provided
                if (!label.isNullOrBlank()) {
                    saveBitcoinTransactionLabelIndexed(sendWalletId, txid, label)
                }

                // Invalidate pre-check cache so next background sync picks up the change
                clearScriptHashCache()

                WalletResult.Success(txid)
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.e(TAG, "sendBitcoin failed: ${e.message}", e)
                WalletResult.Error(e.message?.ifBlank { null } ?: "Transaction failed", e)
            }
            }
        }

    /**
     * Build and sign an Ark board funding transaction WITHOUT broadcasting it.
     * Single-tx self-board path: the signed PSBT is handed to Bark `boardPsbt`
     * first (Bark commits to this exact txid), then broadcast separately via
     * [broadcastSignedBoardFundingTx]. Build/sign mirrors [sendBitcoin]
     * (two-pass fee correction, manual coin selection, frozen-UTXO filter).
     *
     * Silent-payment destinations are rejected — boarding cannot rebuild SP outputs.
     */
    suspend fun buildSignedBoardFundingTx(
        recipientAddress: String,
        amountSats: ULong,
        feeRateSatPerVb: Double = 1.0,
        selectedUtxos: List<UtxoInfo>? = null,
        isMaxSend: Boolean = false,
        precomputedFeeSats: ULong? = null,
        onProgress: (String) -> Unit = {},
    ): WalletResult<SignedBoardFunding> =
        withContext(Dispatchers.IO) {
            withSelectedUtxos(selectedUtxos) {
            val currentWallet = wallet ?: return@withSelectedUtxos WalletResult.Error("Wallet not initialized")
            val activeWalletId = secureStorage.getActiveWalletId()
            val storedWallet = activeWalletId?.let { secureStorage.getWalletMetadata(it) }
            if (storedWallet?.policyType == WalletPolicyType.MULTISIG) {
                return@withContext WalletResult.Error("Use PSBT signing for multisig wallets")
            }
            if (storedWallet?.isWatchOnly == true) {
                return@withContext WalletResult.Error("Cannot send from watch-only wallet")
            }
            // Pin the wallet for the whole flow: re-reading the active id
            // later could mix another wallet's keys/records in after a
            // mid-send wallet switch.
            val sendWalletId = storedWallet?.id ?: activeWalletId
                ?: return@withSelectedUtxos WalletResult.Error("Wallet not initialized")

            try {
                onProgress("Building transaction...")
                if (isSilentPaymentAddress(recipientAddress)) {
                    return@withContext WalletResult.Error("Silent payments are not supported for boarding")
                }
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
                val recipientScripts = buildSendRecipientScripts(sendRecipients, currentWallet)
                val feeRate = feeRateFromSatPerVb(feeRateSatPerVb)

                // Helper to configure the TxBuilder with recipients, UTXOs, etc.
                fun buildTx(builder: TxBuilder): TxBuilder {
                    return applyManualSelection(
                        addRecipientScripts(builder, recipientScripts),
                    )
                }

                // When the dry-run already computed the exact fee, reuse it directly
                // via feeAbsolute to guarantee the boarded txid matches the reviewed
                // estimate. Otherwise fall back to the two-pass correction.
                val (psbt, usedPreferChangeOnly) =
                    withConsolidateFallback { preferChangeOnly ->
                        val built =
                            if (precomputedFeeSats != null) {
                                try {
                                    buildTx(
                                        TxBuilder().applySendDefaults(preferChangeOnly)
                                            .feeAbsolute(Amount.fromSat(precomputedFeeSats)),
                                    ).finish(currentWallet)
                                } catch (_: Exception) {
                                    // Fallback: two-pass if feeAbsolute with precomputed fee fails
                                    // (e.g. UTXO set changed between dry-run and send)
                                    val pass1Psbt =
                                        buildTx(
                                            TxBuilder().applySendDefaults(preferChangeOnly).feeRate(feeRate),
                                        ).finish(currentWallet)
                                    val exactFeeResult =
                                        computeExactFee(pass1Psbt, currentWallet, pass1Psbt.extractTx(), feeRateSatPerVb)
                                    if (exactFeeResult != null) {
                                        try {
                                            buildTx(
                                                TxBuilder().applySendDefaults(preferChangeOnly).feeAbsolute(
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
                                        TxBuilder().applySendDefaults(preferChangeOnly).feeRate(feeRate),
                                    ).finish(currentWallet)
                                onProgress("Computing fee...")
                                val exactFeeResult =
                                    computeExactFee(pass1Psbt, currentWallet, pass1Psbt.extractTx(), feeRateSatPerVb)
                                if (exactFeeResult != null) {
                                    try {
                                        buildTx(
                                            TxBuilder().applySendDefaults(preferChangeOnly).feeAbsolute(
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
                        built to preferChangeOnly
                    }

                onProgress("Signing transaction...")
                val signed =
                    signWithFeeCorrection(
                        initialPsbt = psbt,
                        wallet = currentWallet,
                        targetSatPerVb = feeRateSatPerVb,
                        rebuildWithFee = { fee ->
                            try {
                                buildTx(
                                    TxBuilder().applySendDefaults(usedPreferChangeOnly)
                                        .feeAbsolute(Amount.fromSat(fee)),
                                ).finish(currentWallet)
                            } catch (_: Exception) {
                                null
                            }
                        },
                        spWalletId = sendWalletId,
                    )

                // Serialize AFTER signing: boardPsbt commits to this exact txid, so the
                // PSBT handed to Bark must already carry final signatures (legacy
                // scriptSig inputs change the txid when finalized).
                val signedPsbtBase64 = signed.signedPsbt.serialize()
                val txid = signed.tx.computeTxid().toString()
                WalletResult.Success(
                    SignedBoardFunding(
                        signedPsbtBase64 = signedPsbtBase64,
                        txid = txid,
                        feeSats = signed.feeSats.toLong(),
                        recipientAmountSats = resolvedAmountSats.toLong(),
                    ),
                )
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.e(TAG, "buildSignedBoardFundingTx failed: ${e.message}", e)
                WalletResult.Error(e.message?.ifBlank { null } ?: "Transaction failed", e)
            }
            }
        }

    /**
     * Broadcast a previously built board funding transaction (see
     * [buildSignedBoardFundingTx]). Re-derives the transaction from the signed
     * PSBT so the broadcast txid is guaranteed to match the boarded one.
     *
     * Broadcast-race tolerant: Bark may broadcast the finalized PSBT itself via
     * Esplora before we get here. A broadcast failure is verified against the
     * network ([confirmBoardFundingBroadcast]) before it is reported — if our
     * exact txid is now known, the race was won and this returns success.
     */
    suspend fun broadcastSignedBoardFundingTx(
        signedPsbtBase64: String,
        onProgress: (String) -> Unit = {},
    ): WalletResult<String> =
        withContext(Dispatchers.IO) {
            val client = electrumClient ?: return@withContext WalletResult.Error("Not connected to Electrum server")
            try {
                onProgress("Broadcasting to network...")
                val tx = Psbt(signedPsbtBase64).extractTx()
                require(tx.input().isNotEmpty() && tx.output().isNotEmpty()) {
                    "Invalid funding transaction"
                }
                val txid = tx.computeTxid().toString()
                try {
                    client.transactionBroadcast(tx)
                } catch (broadcastErr: Exception) {
                    if (!confirmBoardFundingBroadcast(txid, broadcastErr)) throw broadcastErr
                }
                // Mirror sendBitcoin bookkeeping: SP inputs (if any) must be marked
                // spent. SP *recipients* need no journaling — boarding destinations
                // are rejected in buildSignedBoardFundingTx.
                val activeWalletId = secureStorage.getActiveWalletId()
                markSilentPaymentUtxosSpent(tx, activeWalletId)
                clearScriptHashCache()
                WalletResult.Success(txid)
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.e(TAG, "broadcastSignedBoardFundingTx failed: ${e.message}", e)
                WalletResult.Error(e.message?.ifBlank { null } ?: "Broadcast failed", e)
            }
        }

    /**
     * Confirm a board funding tx reached the network after a broadcast failure.
     *
     * Returns true when [txid] is now known to our Electrum server (the broadcast
     * raced with Bark's own Esplora broadcast and succeeded), or when our server
     * explicitly rejected the broadcast as a duplicate (it holds the tx even if the
     * follow-up lookup missed on a transient socket failure). Cross-backend
     * propagation can lag, so presence is polled before giving up.
     */
    private suspend fun confirmBoardFundingBroadcast(txid: String, broadcastErr: Exception): Boolean {
        val claimedDuplicate = isKnownTxBroadcastRejection(broadcastErr.message)
        repeat(BOARD_BROADCAST_VERIFY_ATTEMPTS) { attempt ->
            if (attempt > 0) delay(BOARD_BROADCAST_VERIFY_RETRY_MS)
            val hex = runCatching { cachingProxy?.getRawTransactionHex(txid) }.getOrNull()
            if (!hex.isNullOrBlank()) {
                if (BuildConfig.DEBUG) {
                    Log.i(TAG, "Board funding $txid confirmed on network after broadcast race")
                }
                return true
            }
        }
        if (claimedDuplicate) {
            if (BuildConfig.DEBUG) {
                Log.i(TAG, "Board funding $txid rejected as duplicate; treating broadcast as won race")
            }
            return true
        }
        return false
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
            withSelectedUtxos(selectedUtxos) {
            val currentWallet = wallet ?: return@withSelectedUtxos WalletResult.Error("Wallet not initialized")
            val client = electrumClient ?: return@withSelectedUtxos WalletResult.Error("Not connected to Electrum server")

            val activeWalletId = secureStorage.getActiveWalletId()
            val storedWallet = activeWalletId?.let { secureStorage.getWalletMetadata(it) }
            if (storedWallet?.policyType == WalletPolicyType.MULTISIG) {
                return@withSelectedUtxos WalletResult.Error("Use PSBT signing for multisig wallets")
            }
            if (storedWallet?.isWatchOnly == true) {
                return@withSelectedUtxos WalletResult.Error("Cannot send from watch-only wallet")
            }
            // Pin the wallet for the whole flow (see single-send).
            val sendWalletId = storedWallet?.id ?: activeWalletId
                ?: return@withSelectedUtxos WalletResult.Error("Wallet not initialized")

            try {
                onProgress("Building transaction...")
                val feeRate = feeRateFromSatPerVb(feeRateSatPerVb)
                val applyManualSelection = buildManualSelectionApplier(currentWallet, selectedUtxos)
                val usesSilentPayment = hasSilentPaymentRecipient(recipients)
                val recipientScripts = buildSendRecipientScripts(recipients, currentWallet)

                fun buildTx(builder: TxBuilder): TxBuilder {
                    return applyManualSelection(addRecipientScripts(builder, recipientScripts))
                }

                val (psbt, usedPreferChangeOnly) =
                    withConsolidateFallback { preferChangeOnly ->
                        val built =
                            if (precomputedFeeSats != null) {
                                try {
                                    buildTx(
                                        TxBuilder().applySendDefaults(preferChangeOnly)
                                            .feeAbsolute(Amount.fromSat(precomputedFeeSats)),
                                    ).finish(currentWallet)
                                } catch (_: Exception) {
                                    val pass1Psbt =
                                        buildTx(
                                            TxBuilder().applySendDefaults(preferChangeOnly).feeRate(feeRate),
                                        ).finish(currentWallet)
                                    val exactFeeResult =
                                        computeExactFee(pass1Psbt, currentWallet, pass1Psbt.extractTx(), feeRateSatPerVb)
                                    if (exactFeeResult != null) {
                                        try {
                                            buildTx(
                                                TxBuilder().applySendDefaults(preferChangeOnly).feeAbsolute(
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
                                        TxBuilder().applySendDefaults(preferChangeOnly).feeRate(feeRate),
                                    ).finish(currentWallet)
                                onProgress("Computing fee...")
                                val exactFeeResult =
                                    computeExactFee(pass1Psbt, currentWallet, pass1Psbt.extractTx(), feeRateSatPerVb)
                                if (exactFeeResult != null) {
                                    try {
                                        buildTx(
                                            TxBuilder().applySendDefaults(preferChangeOnly).feeAbsolute(
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
                        built to preferChangeOnly
                    }

                val psbtForSigning =
                    if (usesSilentPayment) {
                        rebuildWithSilentPaymentOutputs(
                            placeholderPsbt = psbt,
                            currentWallet = currentWallet,
                            storedWallet = storedWallet,
                            recipients = recipients,
                            feeSats = psbt.fee(),
                            walletId = sendWalletId,
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
                                    walletId = sendWalletId,
                                )
                            } catch (_: Exception) {
                                null
                            }
                        } else {
                            try {
                                buildTx(
                                    TxBuilder().applySendDefaults(usedPreferChangeOnly)
                                        .feeAbsolute(Amount.fromSat(fee)),
                                ).finish(currentWallet)
                            } catch (_: Exception) {
                                null
                            }
                        }
                    },
                    spWalletId = sendWalletId,
                ).tx

                onProgress("Broadcasting to network...")
                // Journal the SP record BEFORE broadcast (see single-send).
                val txid = tx.computeTxid().toString()
                persistSilentPaymentRecipients(sendWalletId, txid, recipients)
                client.transactionBroadcast(tx)
                markSilentPaymentUtxosSpent(tx, sendWalletId)

                if (!label.isNullOrBlank()) {
                    saveBitcoinTransactionLabelIndexed(sendWalletId, txid, label)
                }

                // Invalidate pre-check cache so next background sync picks up the change
                clearScriptHashCache()

                WalletResult.Success(txid)
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.e(TAG, "sendBitcoin multi failed: ${e.message}", e)
                WalletResult.Error(e.message?.ifBlank { null } ?: "Transaction failed", e)
            }
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
    /**
     * Signs a PSBT built externally (Spark unilateral-exit CPFP set) with the
     * active hot L1 wallet. Keys never leave the wallet: the SDK hands us the
     * unsigned PSBT bytes and gets back signed PSBT bytes.
     *
     * Fund-safety verification before signing:
     * - the wallet being signed with must be [expectedWalletId] (binds the
     *   signature to the wallet the user reviewed — a wallet switch between
     *   quote and sign fails loudly instead of signing with the wrong key);
     * - every PSBT input that spends one of OUR wallet's UTXOs must be an
     *   expected funding outpoint, or pay to an expected funding script
     *   (covers SDK-derived reuse such as CPFP change back to the funding
     *   address on resume builds). Tree/foreign inputs the wallet does not own
     *   are left to the SDK. Without this, a buggy native lib could slip an
     *   arbitrary L1 UTXO into the set and spend it with no change check.
     *
     * @throws IllegalStateException when no spend-capable wallet is loaded.
     * @throws IllegalArgumentException when the PSBT touches unexpected funds
     * or the active wallet does not match [expectedWalletId].
     */
    suspend fun signExitCpfpPsbt(
        psbtBytes: ByteArray,
        expectedOutpoints: Set<String>,
        expectedScripts: Set<String>,
        expectedWalletId: String,
    ): ByteArray =
        withContext(Dispatchers.IO) {
            val activeWalletId = secureStorage.getActiveWalletId()
                ?: throw IllegalStateException("No active wallet")
            if (activeWalletId != expectedWalletId) {
                throw IllegalArgumentException("Active wallet changed — re-quote the exit before signing")
            }
            val metadata = secureStorage.getWalletMetadata(activeWalletId)
                ?: throw IllegalStateException("Wallet not found")
            if (metadata.isWatchOnly) throw IllegalStateException("Cannot sign from watch-only wallet")
            val hotWallet = wallet ?: throw IllegalStateException("L1 wallet is not loaded")
            val normalizedExpected = expectedOutpoints.map { it.lowercase() }.toSet()
            val normalizedScripts = expectedScripts.map { it.lowercase() }.toSet()
            val base64 = android.util.Base64.encodeToString(psbtBytes, android.util.Base64.NO_WRAP)
            val psbt = Psbt(base64)
            val unsignedTx =
                runCatching { psbt.extractTx() }.getOrElse {
                    throw IllegalArgumentException("Exit CPFP PSBT is not parseable", it)
                }
            var walletOwnedInputs = 0
            for (input in unsignedTx.input()) {
                val prevOut = input.previousOutput
                val known = runCatching { hotWallet.getUtxo(prevOut) }.getOrNull() ?: continue
                walletOwnedInputs++
                val outpoint = "${prevOut.txid}:${prevOut.vout}".lowercase()
                if (outpoint in normalizedExpected) continue
                val scriptHex =
                    runCatching {
                        val script = known.txout?.scriptPubkey ?: return@runCatching ""
                        script.toBytes().joinToString("") { "%02x".format(it) }.lowercase()
                    }.getOrDefault("")
                if (scriptHex.isNotEmpty() && scriptHex in normalizedScripts) continue
                throw IllegalArgumentException(
                    "Exit CPFP PSBT spends unexpected wallet funds ($outpoint) — refusing to sign",
                )
            }
            if (walletOwnedInputs == 0 && normalizedExpected.isNotEmpty()) {
                throw IllegalArgumentException("Exit CPFP PSBT spends none of the selected funding UTXOs — refusing to sign")
            }
            hotWallet.sign(psbt)
            android.util.Base64.decode(psbt.serialize(), android.util.Base64.DEFAULT)
        }

    /**
     * Exact-match destination check for Spark exits: the address must parse
     * for the loaded L1 wallet's network. Complements the checksum-aware
     * plausibility gate in [SparkUnilateralExitPolicy] with a network binding,
     * so a testnet address can never back a mainnet sweep.
     */
    fun isValidAddressForWallet(address: String): Boolean {
        val hotWallet = wallet ?: return false
        val trimmed = address.trim()
        if (trimmed.isEmpty()) return false
        return runCatching { Address(trimmed, hotWallet.network()); true }.getOrDefault(false)
    }

    /**
     * Raw scriptPubkey hex for one of our L1 addresses, used to describe
     * Spark exit funding inputs (native SegWit only). Null when unknown.
     */
    fun fundingScriptHexForAddress(address: String): String? {
        val hotWallet = wallet ?: return null
        return runCatching {
            val parsed = Address(address, hotWallet.network())
            parsed.scriptPubkey().toBytes().joinToString("") { "%02x".format(it) }
        }.getOrNull()
    }

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

    private fun isAmbiguousHexBase64(trimmed: String): Boolean {
        if (trimmed.length < 16 || trimmed.length > 300_000) return false
        val noWs = trimmed.replace("\\s".toRegex(), "")
        if (noWs.length % 2 != 0) return false
        if (!noWs.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) return false
        if (noWs.length % 4 == 1) return false
        if (!noWs.all { it in 'A'..'Z' || it in 'a'..'z' || it in '0'..'9' || it == '+' || it == '/' || it == '=' }) {
            return false
        }
        return try {
            android.util.Base64.decode(noWs, android.util.Base64.DEFAULT).size >= 5
        } catch (_: Exception) {
            false
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
                val trimmedHex = txHex.trim()
                require(trimmedHex.length % 2 == 0 && trimmedHex.length > 20) {
                    "Invalid transaction hex"
                }
                require(trimmedHex.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) {
                    "Invalid transaction hex"
                }
                val txBytes = trimmedHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
                val tx = Transaction(txBytes)
                // Standalone manual broadcast has no originating PSBT by design
                // (may not belong to any loaded wallet), so substitution binding
                // applies only when the caller supplies one. Still fail closed
                // on structurally invalid transactions.
                require(tx.input().isNotEmpty() && tx.output().isNotEmpty()) {
                    "Invalid transaction: missing inputs or outputs"
                }
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
                if (trimmed.isBlank()) {
                    return@withContext WalletResult.Error("Empty transaction data")
                }
                // Fail closed on ambiguous encodings (same policy as TxFileParser):
                // a payload valid as both hex and base64 must not be guessed.
                if (isAmbiguousHexBase64(trimmed)) {
                    return@withContext WalletResult.Error(
                        "Ambiguous transaction data: valid as both hex and base64. Re-export in a single format.",
                    )
                }
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
                        val descriptor = createDescriptorFromWif(wif, addrType, network.toNetworkKind())
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
                        val descriptor = createDescriptorFromWif(wif, addrType, network.toNetworkKind())
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
                                .applyOptInRbf()
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
                if (txids.isNotEmpty()) {
                    return@withContext WalletResult.Error(
                        "Sweep partially succeeded. Broadcast txids: ${txids.joinToString(", ")}. " +
                            "Later sweep step failed: ${e.message}",
                        e,
                    )
                }
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
            val currentWallet =
                wallet
                    ?: return@withContext WalletResult.Error(localizedString(R.string.speed_up_error_wallet_not_ready))
            val client =
                electrumClient
                    ?: return@withContext WalletResult.Error(localizedString(R.string.speed_up_error_not_connected))

            // Check if watch-only
            val activeWalletId = secureStorage.getActiveWalletId()
            val storedWallet = activeWalletId?.let { secureStorage.getWalletMetadata(it) }
            if (storedWallet?.policyType == WalletPolicyType.MULTISIG) {
                return@withContext WalletResult.Error("Use PSBT signing for multisig wallets")
            }
            if (storedWallet?.isWatchOnly == true) {
                return@withContext WalletResult.Error("Cannot bump fee from watch-only wallet")
            }
            // Pin the wallet for the whole flow (see sendBitcoin).
            val sendWalletId = storedWallet?.id ?: activeWalletId
                ?: return@withContext WalletResult.Error(localizedString(R.string.speed_up_error_wallet_not_ready))

            try {
                // Round up fee rate to ensure we meet the target
                val feeRate = feeRateFromSatPerVb(newFeeRate)
                val frozenRefs = frozenRefsForActiveWallet()
                val originalTx =
                    currentWallet.getTx(Txid.fromString(txid))?.transaction
                        ?: return@withContext WalletResult.Error("Original transaction not found")
                val originalOutpoints =
                    originalTx.input().map { it.previousOutput.toFrozenRef() }.toSet()
                val originalRecipients =
                    secureStorage.getSilentPaymentRecipients(sendWalletId, txid)
                val usesSilentPayment = hasSilentPaymentRecipient(originalRecipients)
                // Fail closed: a corrupt SP record (present but unparseable, not the
                // known non-SP "[]" marker) must block the RBF. Without this, a
                // corrupt record parses to empty, looks like non-SP, and the
                // replacement would pay the stale output key (permanent loss).
                if (!usesSilentPayment &&
                    secureStorage.isSilentPaymentRecordCorrupt(sendWalletId, txid) &&
                    originalTxHasExternalTaprootOutput(currentWallet, originalTx)
                ) {
                    return@withContext WalletResult.Error(
                        "Cannot RBF — Silent Payment destinations unreadable",
                    )
                }

                // Use BDK's bump fee builder — requires Txid type in BDK 2.x
                val bumpFeeTxBuilder = BumpFeeTxBuilder(Txid.fromString(txid), feeRate)
                val psbt = bumpFeeTxBuilder.finish(currentWallet)
                val addedInputs =
                    psbt.extractTx().input().any { inp ->
                        inp.previousOutput.toFrozenRef() !in originalOutpoints
                    }
                if (addedInputs &&
                    !usesSilentPayment &&
                    // Known non-SP send (recorded at broadcast) — top-up inputs are
                    // safe; only unknown/legacy txs with an external P2TR output must
                    // stay conservative.
                    secureStorage.hasSilentPaymentRecord(sendWalletId, txid) != true &&
                    originalTxHasExternalTaprootOutput(currentWallet, originalTx)
                ) {
                    return@withContext WalletResult.Error(
                        "Cannot RBF — Silent Payment destinations missing",
                    )
                }

                // BumpFeeTxBuilder has no freeze API — reject if it pulled any new
                // (fee-top-up) input that the user marked frozen.
                val bumpInputs = psbt.extractTx().input()
                val frozenFeeTopUp =
                    bumpInputs.any { inp ->
                        val ref = inp.previousOutput.toFrozenRef()
                        ref !in originalOutpoints && ref in frozenRefs
                    }
                if (frozenFeeTopUp) {
                    return@withContext WalletResult.Error(
                        "Cannot RBF using frozen UTXOs for the fee top-up — unfreeze coins or lower the fee",
                    )
                }

                // BumpFeeTxBuilder guarantees the replacement satisfies BIP125
                // (original fee + incremental relay fee). The post-sign correction
                // below may compute a slightly lower fee when the user picks a rate
                // barely above the original — clamp to BDK's minimum so the
                // replacement isn't rejected by the mempool.
                val minReplacementFee = try { psbt.fee() } catch (_: Exception) { 0UL }
                val psbtForSigning =
                    if (usesSilentPayment && addedInputs) {
                        rebuildWithSilentPaymentOutputs(
                            placeholderPsbt = psbt,
                            currentWallet = currentWallet,
                            storedWallet = storedWallet,
                            recipients = originalRecipients,
                            feeSats = minReplacementFee,
                            walletId = sendWalletId,
                            forceRbf = true,
                        )
                    } else {
                        psbt
                    }

                val result = signWithFeeCorrection(
                    initialPsbt = psbtForSigning,
                    wallet = currentWallet,
                    targetSatPerVb = newFeeRate,
                    rebuildWithFee = { fee ->
                        val clampedFee = if (fee < minReplacementFee) minReplacementFee else fee
                        try {
                            if (usesSilentPayment) {
                                rebuildWithSilentPaymentOutputs(
                                    placeholderPsbt = psbt,
                                    currentWallet = currentWallet,
                                    storedWallet = storedWallet,
                                    recipients = originalRecipients,
                                    feeSats = clampedFee,
                                    walletId = sendWalletId,
                                    forceRbf = true,
                                )
                            } else {
                                val unsignedTx = psbt.extractTx()
                                val inputs = unsignedTx.input()
                                val outputs = unsignedTx.output()
                                var b =
                                    TxBuilder()
                                        .applyOptInRbf()
                                        .applyFrozenUtxoFilter()
                                        .feeAbsolute(Amount.fromSat(clampedFee))
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
                            }
                        } catch (_: Exception) {
                            null
                        }
                    },
                    spWalletId = sendWalletId,
                )
                val tx = result.tx

                // Journal the replacement SP record BEFORE broadcast so a kill
                // cannot leave the replacement on-chain without destinations.
                val newTxid = tx.computeTxid().toString()
                persistSilentPaymentRecipients(sendWalletId, newTxid, originalRecipients)

                client.transactionBroadcast(tx)
                markSilentPaymentUtxosSpent(tx, sendWalletId)

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
                val originalLabel = secureStorage.getTransactionLabel(sendWalletId, txid)
                if (!originalLabel.isNullOrBlank()) {
                    saveBitcoinTransactionLabelIndexed(sendWalletId, newTxid, originalLabel)
                }
                secureStorage.savePendingReplacementTransaction(sendWalletId, txid, newTxid)
                // The replacement carries its own copy of the destinations
                // (journaled pre-broadcast above); drop the evicted txid's
                // entry so RBF chains don't accumulate dead records.
                if (newTxid != txid) {
                    secureStorage.deleteSilentPaymentRecord(sendWalletId, txid)
                    persistSilentPaymentRecipients(sendWalletId, newTxid, originalRecipients)
                }

                // Invalidate pre-check cache so next background sync picks up the change
                clearScriptHashCache()

                WalletResult.Success(newTxid)
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.e(TAG, "bumpFee failed: ${e.message}", e)
                WalletResult.Error(mapRbfError(e), e)
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
                val activeWalletId = secureStorage.getActiveWalletId()
                val storedWallet = activeWalletId?.let { secureStorage.getWalletMetadata(it) }
                // Pin the wallet for the whole flow (see sendBitcoin).
                val sendWalletId = storedWallet?.id ?: activeWalletId
                    ?: return@withContext WalletResult.Error("Wallet not initialized")
                val silentPaymentRecipients =
                    secureStorage.getSilentPaymentRecipients(sendWalletId, txid)
                        .takeIf { hasSilentPaymentRecipient(it) }
                        .orEmpty()
                val originalOutpoints =
                    currentWallet.getTx(Txid.fromString(txid))?.transaction
                        ?.input()
                        ?.map { it.previousOutput.toFrozenRef() }
                        .orEmpty()
                        .toSet()
                val psbt = BumpFeeTxBuilder(Txid.fromString(txid), feeRate).finish(currentWallet)
                val addedInputs =
                    psbt.extractTx().input().any { inp ->
                        inp.previousOutput.toFrozenRef() !in originalOutpoints
                    }
                if (addedInputs &&
                    silentPaymentRecipients.isEmpty() &&
                    (secureStorage.hasSilentPaymentRecord(sendWalletId, txid) != true ||
                        secureStorage.isSilentPaymentRecordCorrupt(sendWalletId, txid)) &&
                    currentWallet.getTx(Txid.fromString(txid))?.transaction?.let {
                        originalTxHasExternalTaprootOutput(currentWallet, it)
                    } == true
                ) {
                    return@withContext WalletResult.Error(
                        "Cannot RBF — Silent Payment destinations missing",
                    )
                }
                // Mirror bumpFee: BumpFeeTxBuilder has no freeze API — refuse
                // to hand out a PSBT that spends user-frozen coins.
                val bumpInputs = psbt.extractTx().input()
                val frozenFeeTopUp =
                    bumpInputs.any { inp ->
                        val ref = inp.previousOutput.toFrozenRef()
                        ref !in originalOutpoints && ref in frozenRefsForActiveWallet()
                    }
                if (frozenFeeTopUp) {
                    return@withContext WalletResult.Error(
                        "Cannot RBF using frozen UTXOs for the fee top-up — unfreeze coins or lower the fee",
                    )
                }
                val finalPsbt =
                    if (silentPaymentRecipients.isNotEmpty() && addedInputs) {
                        rebuildWithSilentPaymentOutputs(
                            placeholderPsbt = psbt,
                            currentWallet = currentWallet,
                            storedWallet = storedWallet,
                            recipients = silentPaymentRecipients,
                            feeSats = psbt.fee(),
                            walletId = sendWalletId,
                            forceRbf = true,
                        )
                    } else {
                        psbt
                    }
                WalletResult.Success(
                    maybeCreatePsbtSigningSession(
                        buildGenericPsbtDetails(
                            psbt = finalPsbt,
                            displayLabel = "Replacement transaction",
                        ),
                        label = null,
                    ),
                )
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.e(TAG, "createBumpFeePsbt failed: ${e.message}", e)
                WalletResult.Error(mapRbfError(e), e)
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
            // Pin the wallet for the whole flow (see sendBitcoin).
            val sendWalletId = storedWallet?.id ?: activeWalletId
                ?: return@withContext WalletResult.Error("Wallet not initialized")

            try {
                val transactions = currentWallet.transactions()
                val originalCanonicalTx = transactions.find {
                    it.transaction.computeTxid().toString() == txid
                } ?: return@withContext WalletResult.Error(localizedString(R.string.speed_up_error_rbf_tx_not_found))

                if (originalCanonicalTx.chainPosition is ChainPosition.Confirmed) {
                    return@withContext WalletResult.Error(localizedString(R.string.speed_up_error_rbf_confirmed))
                }

                val originalInputs = originalCanonicalTx.transaction.input()
                if (!originalInputs.any { it.sequence < 0xfffffffeu }) {
                    return@withContext WalletResult.Error(localizedString(R.string.speed_up_error_rbf_not_replaceable))
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
                    // Fail closed: a freeze-while-pending must block Cancel —
                    // `manuallySelectedOnly` pins these inputs even if frozen.
                    val liveFrozen = frozenRefsForActiveWallet()
                    if (liveFrozen.isNotEmpty()) {
                        val frozenPinned =
                            originalInputs.map { "${it.previousOutput.txid}:${it.previousOutput.vout}" }
                                .filter { it in liveFrozen }
                        require(frozenPinned.isEmpty()) {
                            "Cannot cancel using frozen UTXOs — unfreeze coins or wait for confirmation"
                        }
                    }
                    var b = builder.applyOptInRbf()
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
                                walletId = sendWalletId,
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
                    spWalletId = sendWalletId,
                )
                val tx = result.tx

                val newTxid = tx.computeTxid().toString()

                // Journal the cancel record BEFORE broadcast (same kill
                // window as sends). Persist the actual drained value, not
                // 0, so a later fee-bump of the cancel rebuilds against a
                // sane amount and backup import validation accepts it.
                val spDest = trimmedDestinationAddress?.takeIf(::isSilentPaymentAddress)
                if (spDest != null) {
                    val drainedValue = tx.output().firstOrNull()?.value?.toSat() ?: 0UL
                    persistSilentPaymentRecipients(
                        sendWalletId,
                        newTxid,
                        listOf(Recipient(spDest, drainedValue)),
                    )
                }

                client.transactionBroadcast(tx)
                markSilentPaymentUtxosSpent(tx, sendWalletId)

                // Persist after successful broadcast so the eviction is durable
                walletPersister?.let { currentWallet.persist(it) }

                val originalLabel = secureStorage.getTransactionLabel(sendWalletId, txid)
                if (!originalLabel.isNullOrBlank()) {
                    saveBitcoinTransactionLabelIndexed(sendWalletId, newTxid, originalLabel)
                }
                secureStorage.savePendingReplacementTransaction(sendWalletId, txid, newTxid)

                clearScriptHashCache()

                WalletResult.Success(newTxid)
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.e(TAG, "redirectTransaction failed: ${e.message}", e)
                WalletResult.Error(mapRbfError(e), e)
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
                } ?: return@withContext WalletResult.Error(localizedString(R.string.speed_up_error_rbf_tx_not_found))
                if (originalCanonicalTx.chainPosition is ChainPosition.Confirmed) {
                    return@withContext WalletResult.Error(localizedString(R.string.speed_up_error_rbf_confirmed))
                }
                val originalInputs = originalCanonicalTx.transaction.input()
                if (!originalInputs.any { it.sequence < 0xfffffffeu }) {
                    return@withContext WalletResult.Error(localizedString(R.string.speed_up_error_rbf_not_replaceable))
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
                val isSilentPaymentRedirect =
                    trimmedDestinationAddress?.let(::isSilentPaymentAddress) == true
                // Non-null SP destination for the re-derive branch below.
                val spRedirectDest = trimmedDestinationAddress?.takeIf { isSilentPaymentRedirect }
                // SP outputs cannot be parsed by BDK's Address — start from a
                // same-size placeholder and re-derive the tweak below, exactly
                // like `redirectTransaction`.
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
                    } else if (isSilentPaymentRedirect) {
                        scriptFromBytes(SilentPayment.placeholderScriptPubKey())
                    } else {
                        try {
                            Address(trimmedDestinationAddress, currentWallet.network()).scriptPubkey()
                        } catch (_: Exception) {
                            return@withContext WalletResult.Error("Invalid destination address")
                        }
                    }
                var builder =
                    TxBuilder()
                        .applyOptInRbf()
                        .feeRate(feeRateFromSatPerVb(newFeeRate))
                for (input in originalInputs) {
                    builder = builder.addUtxo(input.previousOutput)
                }
                val pass1Psbt = builder.manuallySelectedOnly().drainTo(redirectScript).finish(currentWallet)
                // Re-derive the real SP output from the placeholder's input
                // set. Needs hot input keys — watch-only fails closed here
                // with "Silent payments require a hot wallet".
                val psbt =
                    if (spRedirectDest != null) {
                        val activeId = secureStorage.getActiveWalletId()
                        val stored = activeId?.let { secureStorage.getWalletMetadata(it) }
                        // Pin the wallet for key derivation (see sendBitcoin).
                        val redirectWalletId = stored?.id ?: activeId
                            ?: return@withContext WalletResult.Error("Wallet not initialized")
                        val finalScript =
                            buildSilentPaymentRedirectScript(
                                placeholderPsbt = pass1Psbt,
                                currentWallet = currentWallet,
                                storedWallet = stored,
                                destinationAddress = spRedirectDest,
                                walletId = redirectWalletId,
                            )
                        var rebuilt =
                            TxBuilder()
                                .applyOptInRbf()
                                .feeRate(feeRateFromSatPerVb(newFeeRate))
                        for (input in originalInputs) {
                            rebuilt = rebuilt.addUtxo(input.previousOutput)
                        }
                        rebuilt.manuallySelectedOnly().drainTo(finalScript).finish(currentWallet)
                    } else {
                        pass1Psbt
                    }
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
                    e.message?.contains("Silent payment") == true ->
                        e.message?.take(200) ?: "Silent payment signing failed"
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
            val currentWallet =
                wallet
                    ?: return@withContext WalletResult.Error(localizedString(R.string.speed_up_error_wallet_not_ready))
            val client =
                electrumClient
                    ?: return@withContext WalletResult.Error(localizedString(R.string.speed_up_error_not_connected))

            // Check if watch-only
            val activeWalletId = secureStorage.getActiveWalletId()
            val storedWallet = activeWalletId?.let { secureStorage.getWalletMetadata(it) }
            if (storedWallet?.policyType == WalletPolicyType.MULTISIG) {
                return@withContext WalletResult.Error("Use PSBT signing for multisig wallets")
            }
            if (storedWallet?.isWatchOnly == true) {
                return@withContext WalletResult.Error("Cannot create CPFP from watch-only wallet")
            }
            // Pin the wallet for the whole flow (see sendBitcoin).
            val sendWalletId = storedWallet?.id ?: activeWalletId
                ?: return@withContext WalletResult.Error(localizedString(R.string.speed_up_error_wallet_not_ready))

            try {
                // Find UTXOs from the parent transaction: BDK-owned outputs
                // plus unspent silent-payment receives, so incoming SP funds
                // stuck unconfirmed can be sped up too.
                val utxos = currentWallet.listUnspent()
                val parentUtxos = utxos.filter { it.outpoint.txid.toString().equals(parentTxid, ignoreCase = true) }
                val spParentUtxos = secureStorage.getSilentPaymentUtxos(sendWalletId)
                    .filter { !it.spent && it.txid.equals(parentTxid, ignoreCase = true) }

                if (parentUtxos.isEmpty() && spParentUtxos.isEmpty()) {
                    return@withContext WalletResult.Error(localizedString(R.string.speed_up_error_cpfp_no_outputs))
                }

                val frozenRefs = frozenRefsForActiveWallet()
                val frozenLower = frozenRefs.map { it.lowercase() }.toSet()
                if (parentUtxos.any { isFrozenOutpoint(it.outpoint, frozenRefs) } ||
                    spParentUtxos.any { it.outpoint.lowercase() in frozenLower }
                ) {
                    return@withContext WalletResult.Error(
                        "Cannot CPFP with frozen parent outputs — unfreeze them first",
                    )
                }

                // Get a change address to send to (we're just consolidating to ourselves)
                val changeAddress = currentWallet.revealNextAddress(KeychainKind.INTERNAL)

                // Calculate total value of parent UTXOs
                val totalValue = parentUtxos.sumOf { it.txout.value.toSat().toLong() }.toULong() +
                    spParentUtxos.fold(0UL) { acc, utxo -> acc + utxo.valueSats }

                // Package-aware fee accounting: miners evaluate parent+child TOGETHER,
                // so the child must pay targetRate * (parentVsize + childVsize) minus
                // whatever the parent already pays — not just targetRate * childVsize.
                // For silent-payment parents the BDK graph has no fee data, so the
                // parent contribution is approximated as zero (safe direction: the
                // child alone still meets the target rate on its own vsize).
                val parentTx =
                    currentWallet.getTx(Txid.fromString(parentTxid))?.transaction
                val parentFeeSats =
                    parentTx?.let { runCatching { currentWallet.calculateFee(it).toSat().toLong() }.getOrDefault(0L) }
                        ?: 0L
                val parentVsize = parentTx?.let { it.weight().toDouble() / 4.0 } ?: 0.0
                val feeOffsetSats = kotlin.math.round(feeRate * parentVsize).toLong() - parentFeeSats

                // Build transaction spending the parent's UTXOs
                val bdkFeeRate = feeRateFromSatPerVb(feeRate)

                // Dust limit - use 546 sats (Bitcoin Core default relay dust, safe for all output types)
                // P2TR dust is ~387 sats, P2WPKH is ~294 sats, so 546 covers all
                val dustLimit = 546UL

                // Estimate child tx vsize (~150 vB for 1-in-1-out P2WPKH/P2TR)
                // Add buffer for potential additional inputs
                val parentCount = parentUtxos.size + spParentUtxos.size
                val estimatedVsize = 150L + (parentCount - 1) * 68L // ~68 vB per additional input
                val estimatedFee =
                    (kotlin.math.ceil(feeRate * (parentVsize + estimatedVsize)).toLong() - parentFeeSats)
                        .coerceAtLeast(estimatedVsize) // >= ~1 sat/vB relay floor
                        .toULong()

                // Check if parent output can cover fee AND leave dust-safe amount
                val canCoverFeeWithDust = totalValue > estimatedFee + dustLimit

                if (BuildConfig.DEBUG) {
                    Log.d(
                        TAG,
                        "CPFP: parentUtxos=${parentUtxos.size}+${spParentUtxos.size}sp, totalValue=$totalValue, feeRate=$feeRate, estimatedFee=$estimatedFee, canCoverFeeWithDust=$canCoverFeeWithDust",
                    )
                }

                // Local helper to build the CPFP transaction with given fee config.
                // applyFrozenUtxoFilter keeps drainWallet() from pulling frozen coins
                // for fee; parent outs are still force-added via addUtxo.
                fun buildCpfpTx(builder: TxBuilder): TxBuilder {
                    var b = builder.applyOptInRbf().applyFrozenUtxoFilter()
                    for (utxo in parentUtxos) {
                        if (BuildConfig.DEBUG) {
                            Log.d(
                                TAG,
                                "CPFP: Adding required UTXO: ${utxo.outpoint.txid}:${utxo.outpoint.vout}, value=${utxo.txout.value.toSat()}",
                            )
                        }
                        b = b.addUtxo(utxo.outpoint)
                    }
                    for (utxo in spParentUtxos) {
                        if (BuildConfig.DEBUG) {
                            Log.d(TAG, "CPFP: Adding required silent UTXO: ${utxo.outpoint}, value=${utxo.valueSats}")
                        }
                        b = addSilentPaymentForeignUtxo(b, utxo)
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
                // then sign to measure actual vsize, rebuild with feeAbsolute.
                // The fee offset makes both passes package-aware (parent + child).
                val pass1Psbt = buildCpfpTx(TxBuilder().feeRate(bdkFeeRate)).finish(currentWallet)
                val exactFeeResult = computeExactFee(
                    pass1Psbt, currentWallet, pass1Psbt.extractTx(), feeRate,
                )
                val psbt = if (exactFeeResult != null) {
                    try {
                        val packageAwareFee =
                            (exactFeeResult.feeSats.toLong() + feeOffsetSats)
                                .coerceAtLeast(kotlin.math.ceil(exactFeeResult.vsize).toLong())
                                .toULong()
                        buildCpfpTx(
                            TxBuilder().feeAbsolute(Amount.fromSat(packageAwareFee)),
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
                    feeOffsetSats = feeOffsetSats,
                    rebuildWithFee = { fee ->
                        try {
                            buildCpfpTx(
                                TxBuilder().feeAbsolute(Amount.fromSat(fee)),
                            ).finish(currentWallet)
                        } catch (_: Exception) {
                            null
                        }
                    },
                    spWalletId = sendWalletId,
                )
                val tx = result.tx

                client.transactionBroadcast(tx)
                markSilentPaymentUtxosSpent(tx, sendWalletId)

                val childTxid = tx.computeTxid().toString()

                // Invalidate pre-check cache so next background sync picks up the change
                clearScriptHashCache()

                WalletResult.Success(childTxid)
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.e(TAG, "CPFP failed: ${e.message}", e)
                WalletResult.Error(mapCpfpError(e), e)
            }
        }

    suspend fun createCpfpPsbt(
        parentTxid: String,
        feeRate: Double,
    ): WalletResult<PsbtDetails> =
        withContext(Dispatchers.IO) {
            val currentWallet = wallet ?: return@withContext WalletResult.Error("Wallet not initialized")
            try {
                val activeWalletId = secureStorage.getActiveWalletId()
                val parentUtxos = currentWallet.listUnspent().filter {
                    it.outpoint.txid.toString().equals(parentTxid, ignoreCase = true)
                }
                val spParentCount = activeWalletId
                    ?.let { secureStorage.getSilentPaymentUtxos(it) }
                    .orEmpty()
                    .count { !it.spent && it.txid.equals(parentTxid, ignoreCase = true) }
                if (parentUtxos.isEmpty() && spParentCount == 0) {
                    return@withContext WalletResult.Error(localizedString(R.string.speed_up_error_cpfp_no_outputs))
                }
                if (spParentCount > 0) {
                    // External signers cannot produce the BIP-340 signatures
                    // silent inputs need (see signSilentPaymentInputs); the
                    // hot-wallet CPFP flow signs them in software instead.
                    return@withContext WalletResult.Error(
                        "CPFP of silent-payment receives needs the hot-wallet flow, not a PSBT",
                    )
                }
                val frozenRefs = frozenRefsForActiveWallet()
                if (parentUtxos.any { isFrozenOutpoint(it.outpoint, frozenRefs) }) {
                    return@withContext WalletResult.Error(
                        "Cannot CPFP with frozen parent outputs — unfreeze them first",
                    )
                }

                val changeAddress = currentWallet.revealNextAddress(KeychainKind.INTERNAL)
                val totalValue = parentUtxos.sumOf { it.txout.value.toSat().toLong() }.toULong()
                val estimatedVsize = 150L + (parentUtxos.size - 1) * 68L

                // Package-aware: child must pay targetRate * (parent + child vsize)
                // minus the parent's existing fee (see cpfp()).
                val parentTx = currentWallet.getTx(Txid.fromString(parentTxid))?.transaction
                val parentFeeSats =
                    parentTx?.let { runCatching { currentWallet.calculateFee(it).toSat().toLong() }.getOrDefault(0L) }
                        ?: 0L
                val parentVsize = parentTx?.let { it.weight().toDouble() / 4.0 } ?: 0.0
                val estimatedFee =
                    (kotlin.math.ceil(feeRate * (parentVsize + estimatedVsize)).toLong() - parentFeeSats)
                        .coerceAtLeast(estimatedVsize)
                        .toULong()
                val canCoverFeeWithDust = totalValue > estimatedFee + 546UL

                var builder =
                    TxBuilder()
                        .applyOptInRbf()
                        .applyFrozenUtxoFilter()
                        .feeAbsolute(Amount.fromSat(estimatedFee))
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
                WalletResult.Error(mapCpfpError(e), e)
            }
        }

    /**
     * Check if a transaction can be bumped with RBF
     * @param txid The transaction ID to check
     * @return True if the transaction is unconfirmed and RBF-enabled
     */
    fun canBumpFee(txid: String): Boolean {
        val currentWallet = wallet ?: return false
        return withWalletReadLock(lockedOutValue = false) {
            try {
                val canonical =
                    currentWallet.transactions().find {
                        it.transaction.computeTxid().toString() == txid
                    } ?: return@withWalletReadLock false
                val isOutgoing = isOutgoingWalletTransaction(currentWallet, canonical.transaction)
                computeCanRbf(canonical.chainPosition, isOutgoing, canonical.transaction)
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.e(TAG, "Error checking RBF eligibility: ${e.message}")
                false
            }
        }
    }

    /**
     * Check if a transaction can be sped up with CPFP
     * @param txid The transaction ID to check
     * @return True if the transaction has unspent outputs that we control
     */
    fun canCpfp(txid: String): Boolean {
        val currentWallet = wallet ?: return false
        return withWalletReadLock(lockedOutValue = false) {
            try {
                val canonical =
                    currentWallet.transactions().find {
                        it.transaction.computeTxid().toString() == txid
                    }
                if (canonical != null) {
                    val isOutgoing = isOutgoingWalletTransaction(currentWallet, canonical.transaction)
                    computeCanCpfp(
                        chainPos = canonical.chainPosition,
                        isSentTx = isOutgoing,
                        txid = txid,
                        cpfpParentTxids = cpfpEligibleParentTxids(currentWallet),
                    )
                } else {
                    // Silent-payment receives live outside the BDK graph: an
                    // unconfirmed, unspent SP output can still be CPFP'd.
                    val walletId = secureStorage.getActiveWalletId() ?: return@withWalletReadLock false
                    secureStorage.getSilentPaymentUtxos(walletId).any {
                        !it.spent && it.txid.equals(txid, ignoreCase = true) && it.height <= 0
                    }
                }
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.e(TAG, "Error checking CPFP eligibility: ${e.message}")
                false
            }
        }
    }

    /**
     * Run [block] holding the sync mutex so BDK wallet reads can't race a sync's
     * apply/persist. When a sync is in progress the mutex can't be acquired —
     * return [lockedOutValue] rather than reading unlocked (fail-safe: callers
     * pass `false`, hiding RBF/CPFP actions until the sync completes).
     */
    private inline fun <T> withWalletReadLock(
        lockedOutValue: T,
        block: () -> T,
    ): T {
        if (!syncMutex.tryLock()) return lockedOutValue
        return try {
            block()
        } finally {
            syncMutex.unlock()
        }
    }

    private fun cpfpEligibleParentTxids(currentWallet: Wallet): Set<String> =
        try {
            val bdk = currentWallet.listUnspent().map { it.outpoint.txid.toString() }.toSet()
            val sp = secureStorage.getActiveWalletId()
                ?.let { secureStorage.getSilentPaymentUtxos(it) }
                .orEmpty()
                .filter { !it.spent }
                .map { it.txid }
                .toSet()
            bdk + sp
        } catch (_: Exception) {
            emptySet()
        }

    private fun isOutgoingWalletTransaction(
        currentWallet: Wallet,
        tx: Transaction,
    ): Boolean =
        try {
            val sentAndReceived = currentWallet.sentAndReceived(tx)
            amountToSats(sentAndReceived.sent) > amountToSats(sentAndReceived.received)
        } catch (_: Exception) {
            false
        }

    private fun computeCanRbf(
        chainPos: ChainPosition,
        isSentTx: Boolean,
        tx: Transaction,
    ): Boolean {
        if (chainPos is ChainPosition.Confirmed || !isSentTx) return false
        val inputs = tx.input()
        if (inputs.any { it.sequence < 0xfffffffeu }) return true
        // BDK can still build fee bumps for wallet-owned unconfirmed sends even when
        // sequences look final (e.g. after a prior fee-correction rebuild).
        return true
    }

    private fun computeCanCpfp(
        chainPos: ChainPosition,
        isSentTx: Boolean,
        txid: String,
        cpfpParentTxids: Set<String>,
    ): Boolean {
        if (chainPos is ChainPosition.Confirmed || isSentTx) return false
        return txid in cpfpParentTxids
    }

    fun canEditDerivationPath(walletId: String): Boolean {
        val storedWallet = secureStorage.getWalletMetadata(walletId) ?: return false
        if (!storedWallet.canEditDerivationPath()) return false
        if (secureStorage.hasWatchAddress(walletId) || secureStorage.hasPrivateKey(walletId)) {
            return false
        }
        if (secureStorage.getMnemonic(walletId) != null) return true
        if (!secureStorage.hasExtendedKey(walletId)) return false
        val extendedKey = secureStorage.getExtendedKey(walletId) ?: return false
        val input = extendedKey.trim().lowercase()
        val descriptorPrefixes = listOf("pkh(", "wpkh(", "tr(", "wsh(", "sh(wsh(")
        return descriptorPrefixes.none { input.startsWith(it) }
    }

    /**
     * Edit wallet metadata (name and optionally fingerprint for watch-only).
     * Changing a BIP39 derivation path deletes the BDK DB and rebuilds descriptors.
     */
    suspend fun editWallet(
        walletId: String,
        newName: String,
        newGapLimit: Int,
        newFingerprint: String? = null,
        newDerivationPath: String? = null,
        newAddressType: AddressType? = null,
    ): WalletResult<Boolean> =
        withContext(Dispatchers.IO) {
            val storedWallet =
                secureStorage.getWalletMetadata(walletId)
                    ?: return@withContext WalletResult.Error("Wallet not found")
            val canEditPath = canEditDerivationPath(walletId)
            val persistablePath =
                if (newDerivationPath != null && canEditPath) {
                    try {
                        BitcoinUtils.persistableDerivationPath(
                            newDerivationPath,
                            storedWallet.defaultDerivationPath(),
                            storedWallet.derivationPath,
                        )
                    } catch (e: Exception) {
                        return@withContext WalletResult.Error(
                            e.message ?: "Invalid derivation path",
                            e,
                        )
                    }
                } else {
                    null
                }
            val persistableAddressType = if (canEditPath) newAddressType else null
            val pathChanged =
                persistablePath != null && persistablePath != storedWallet.derivationPath
            val addressTypeChanged =
                persistableAddressType != null && persistableAddressType != storedWallet.addressType
            val descriptorsChanged = pathChanged || addressTypeChanged
            walletLoadMutex.withLock {
                val wasActive = walletId == secureStorage.getActiveWalletId()
                if (descriptorsChanged) {
                    if (wasActive) {
                        clearLoadedWallet()
                        clearScriptHashCache()
                    }
                    if (!deleteSqliteArtifacts(getWalletDbPath(walletId))) {
                        if (wasActive) {
                            loadWalletByIdLocked(walletId)
                        }
                        return@withLock WalletResult.Error("Failed to rebuild wallet database")
                    }
                    secureStorage.clearL1ReceiveAddress(walletId)
                    secureStorage.setNeedsFullSync(walletId, true)
                }
                if (!secureStorage.editWallet(
                        walletId,
                        newName,
                        newGapLimit,
                        newFingerprint,
                        persistablePath,
                        persistableAddressType,
                    )
                ) {
                    return@withLock WalletResult.Error("Wallet not found")
                }
                if (descriptorsChanged && wasActive) {
                    when (val loadResult = loadWalletByIdLocked(walletId)) {
                        is WalletResult.Error -> return@withLock loadResult
                        is WalletResult.Success -> Unit
                    }
                } else if (wasActive) {
                    wallet?.let { currentWallet ->
                        if (revealConfiguredGapLimit(currentWallet, walletId)) {
                            subscribeNewlyRevealedAddresses()
                        }
                    }
                }
                refreshWalletListMetadata()
                WalletResult.Success(descriptorsChanged)
            }
        }

    /**
     * Set whether a wallet requires app authentication before opening.
     */
    fun setWalletLocked(walletId: String, locked: Boolean) {
        if (secureStorage.setWalletLocked(walletId, locked)) {
            refreshWalletListMetadata()
        }
    }

    /**
     * Reorder wallets to the given ID order.
     * Updates persistent storage and refreshes wallet state so both
     * ManageWallets and WalletSelectorPanel reflect the new order.
     */
    fun reorderWallets(orderedIds: List<String>) {
        secureStorage.reorderWalletIds(orderedIds)
        refreshWalletListMetadata()
    }

    /**
     * Delete a specific wallet
     */
    suspend fun deleteWallet(walletId: String): WalletResult<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val wasActive = secureStorage.getActiveWalletId() == walletId
                var nextActiveWalletId: String? = null

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
                nextActiveWalletId = secureStorage.getActiveWalletId()

                // Update the list immediately so Manage Wallets reflects the deletion
                // before cache/db cleanup and the next wallet load finish.
                if (wasActive && nextActiveWalletId == null) {
                    _walletState.value = WalletState()
                } else {
                    updateWalletState()
                }

                electrumCache.clearAllWalletActivityData()

                // Delete BDK database files
                deleteWalletDatabase(walletId)
                deleteLiquidWalletDatabase(walletId)
                // Defensive L2 cleanup: the UI normally fans out to each L2
                // ViewModel first, but direct repository callers must not
                // orphan per-wallet SDK state (stale Spark sessions reuse the
                // same filesDir/spark/<walletId> path on re-import).
                deleteSparkWalletData(walletId)

                if (wasActive && nextActiveWalletId != null) {
                    walletLoadMutex.withLock {
                        loadWalletByIdLocked(nextActiveWalletId)
                    }
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
                abortElectrumTransportLocked()
            }
        }

    /**
     * Tear down the active Electrum transport without acquiring [connectionMutex].
     * Used when cancelling an in-flight connect so a server switch is not blocked
     * behind a Tor SSL probe or BDK handshake.
     */
    suspend fun abortActiveElectrumConnection() =
        withContext(Dispatchers.IO) {
            if (BuildConfig.DEBUG) Log.d(TAG, "Aborting active Electrum connection transport")
            abortElectrumTransportLocked()
        }

    private fun abortElectrumTransportLocked() {
        stopNotificationCollector()
        resetSilentPaymentsCapability()
        electrumClient = null
        cachingProxy?.stop()
        cachingProxy = null
        _minFeeRate.value = CachingElectrumProxy.DEFAULT_MIN_FEE_RATE
        electrumCache.clearAllHistory()
        clearScriptHashCache()
    }

    suspend fun abortActiveFullSync() =
        withContext(Dispatchers.IO) {
            if (BuildConfig.DEBUG) Log.d(TAG, "Aborting active full sync")
            abortingActiveFullSync = true

            // Intentionally bypass the normal disconnect mutex path so a blocking
            // native fullScan() call loses its underlying Electrum transport ASAP.
            abortElectrumTransportLocked()
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
                val targets = listOf(2UL, 6UL, 12UL, 144UL)
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
                // estimates[0]=2, [1]=6, [2]=12, [3]=144

                val minRate = _minFeeRate.value

                val fastest = estimates[0]
                val halfHour = estimates[1] ?: estimates[0]
                val hour = estimates[2] ?: estimates[1]
                val economy = estimates[3] ?: estimates[2]

                // If no target returned a valid estimate, the server doesn't support fee estimation
                if (fastest == null && halfHour == null && hour == null && economy == null) {
                    return@withContext Result.failure(
                        Exception("Server returned no fee estimates — it may not support fee estimation"),
                    )
                }

                // Ensure monotonic ordering: fastest >= halfHour >= hour >= economy >= minRate
                val maxRate = BitcoinUtils.MAX_FEE_RATE_SAT_VB
                val economyFinal = (economy ?: minRate).coerceIn(minRate, maxRate)
                val hourFinal = (hour ?: economyFinal).coerceIn(economyFinal, maxRate)
                val halfHourFinal = (halfHour ?: hourFinal).coerceIn(hourFinal, maxRate)
                val fastestFinal = (fastest ?: halfHourFinal).coerceIn(halfHourFinal, maxRate)

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
        ensureFrigateServerPresent()
        return secureStorage.getAllElectrumServers()
    }

    private fun ensureFrigateServerPresent() {
        ensureDefaultServersPresent()
    }

    /**
     * One-time migration ensuring the three bundled defaults (SethForPrivacy Tor,
     * Bull Bitcoin, Frigate) exist for installs seeded before they were all bundled.
     * Runs once; afterwards user deletions are respected.
     */
    private fun ensureDefaultServersPresent() {
        if (secureStorage.hasDefaultElectrumV2Migrated() && secureStorage.hasFrigateMigrated()) return
        val servers = secureStorage.getAllElectrumServers()
        val missing =
            DEFAULT_ELECTRUM_SERVERS.filter { default ->
                servers.none {
                    it.cleanUrl().equals(default.url, ignoreCase = true) && it.port == default.port
                }
            }
        // Backfill useTor on the Seth onion entry for installs seeded before it was set.
        servers
            .filter {
                it.cleanUrl().equals(SETH_TOR_HOST, ignoreCase = true) && !it.useTor
            }.forEach { existing ->
                secureStorage.saveElectrumServer(existing.copy(useTor = true))
            }
        for (config in missing) {
            secureStorage.saveElectrumServer(config)
        }
        secureStorage.setFrigateMigrated(true)
        secureStorage.setDefaultElectrumV2Migrated(true)
        if (BuildConfig.DEBUG && missing.isNotEmpty()) {
            Log.d(TAG, "Backfilled missing default servers: ${missing.map { it.name }}")
        }
    }

    /**
     * Seed default Electrum servers for first-time users.
     * Sets the seeded flag so defaults won't be re-added if user deletes them.
     */
    private fun seedDefaultServers() {
        for (config in DEFAULT_ELECTRUM_SERVERS) {
            secureStorage.saveElectrumServer(config)
        }
        secureStorage.setDefaultServersSeeded(true)
        secureStorage.setFrigateMigrated(true)
        secureStorage.setDefaultElectrumV2Migrated(true)
        if (BuildConfig.DEBUG) Log.d(TAG, "Seeded ${DEFAULT_ELECTRUM_SERVERS.size} default servers")
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

    fun getBalanceDateFormat(): String {
        return secureStorage.getBalanceDateFormat()
    }

    fun setBalanceDateFormat(format: String) {
        secureStorage.setBalanceDateFormat(format)
    }

    fun getThemeMode(): String {
        return secureStorage.getThemeMode()
    }

    fun setThemeMode(themeMode: String) {
        secureStorage.setThemeMode(themeMode)
    }

    fun getTypeface(): String {
        return secureStorage.getTypeface()
    }

    fun setTypeface(typeface: String) {
        secureStorage.setTypeface(typeface)
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

    fun getRbfEnabled(): Boolean = secureStorage.getRbfEnabled()

    fun setRbfEnabled(enabled: Boolean) {
        secureStorage.setRbfEnabled(enabled)
        invalidatePreparedSendCache()
    }

    fun getRequireCoinControl(): Boolean = secureStorage.getRequireCoinControl()

    fun setRequireCoinControl(enabled: Boolean) {
        secureStorage.setRequireCoinControl(enabled)
    }

    fun getConsolidateChange(): Boolean = secureStorage.getConsolidateChange()

    fun setConsolidateChange(enabled: Boolean) {
        secureStorage.setConsolidateChange(enabled)
        invalidatePreparedSendCache()
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

    fun isArkLayer2Enabled(): Boolean = secureStorage.isArkLayer2Enabled()

    fun setArkLayer2Enabled(enabled: Boolean) = secureStorage.setArkLayer2Enabled(enabled)

    fun isArkAutoDelegatedRefreshEnabled(): Boolean = secureStorage.isArkAutoDelegatedRefreshEnabled()

    fun setArkAutoDelegatedRefreshEnabled(enabled: Boolean) =
        secureStorage.setArkAutoDelegatedRefreshEnabled(enabled)

    fun isArkAutoDbBackupEnabled(): Boolean = secureStorage.isArkAutoDbBackupEnabled()

    fun setArkAutoDbBackupEnabled(enabled: Boolean) = secureStorage.setArkAutoDbBackupEnabled(enabled)

    fun getArkAutoDbBackupFolderUri(): String? = secureStorage.getArkAutoDbBackupFolderUri()

    fun setArkAutoDbBackupFolderUri(uri: String?) = secureStorage.setArkAutoDbBackupFolderUri(uri)

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

    fun hasUserSelectedElectrumServer(): Boolean = secureStorage.hasUserSelectedElectrumServer()

    fun setUserSelectedElectrumServer(selected: Boolean) = secureStorage.setUserSelectedElectrumServer(selected)

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

    fun isArkEnabledForWallet(walletId: String): Boolean =
        secureStorage.isArkEnabledForWallet(walletId)

    fun setArkEnabledForWallet(walletId: String, enabled: Boolean) =
        secureStorage.setArkEnabledForWallet(walletId, enabled)

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
        cpfpParentTxids: Set<String>,
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

            val canRbf = computeCanRbf(chainPos, isSentTx, tx)
            val canCpfp =
                computeCanCpfp(
                    chainPos = chainPos,
                    isSentTx = isSentTx,
                    txid = txid,
                    cpfpParentTxids = cpfpParentTxids,
                )

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
                canRbf = canRbf,
                canCpfp = canCpfp,
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
        transactionSources: Map<String, String> = emptyMap(),
        pendingReplacements: Map<String, String> = emptyMap(),
    ): TransactionDetails {
        val replacesTxid = pendingReplacements.entries.find { it.value == details.txid }?.key
        val swapDetails =
            transactionSwapDetails[details.txid]
                ?: inferBitcoinChainSwapSettlementDetails(details, liquidSwapDetails)
        val source =
            transactionSources[details.txid]
                ?: transactionSources.entries
                    .firstOrNull { it.key.equals(details.txid, ignoreCase = true) }
                    ?.value
        return details.copy(
            replacesTxid = replacesTxid,
            swapDetails = swapDetails,
            isSwapHistory = swapDetails != null || isBitcoinCenterSwapSource(source),
        )
    }

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
        val transactionSources = activeWalletId?.let(secureStorage::getAllTransactionSources).orEmpty()
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
        val cpfpParentTxids = cpfpEligibleParentTxids(currentWallet)
        val pendingReplacements =
            activeWalletId?.let { secureStorage.getPendingReplacementTransactions(it) }.orEmpty()
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
                        cpfpParentTxids = cpfpParentTxids,
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
                    transactionSources = transactionSources,
                    pendingReplacements = pendingReplacements,
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

        return mergeSilentPaymentHistory(transactions, currentWallet, activeWalletId)
            .sortedByDescending { it.timestamp ?: Long.MAX_VALUE }
    }

    private fun mergeSilentPaymentHistory(
        transactions: List<TransactionDetails>,
        currentWallet: Wallet,
        walletId: String?,
    ): List<TransactionDetails> {
        if (walletId.isNullOrBlank()) return transactions
        // Pure read: all persistence happens in mutex-held refresh paths, so a
        // state rebuild can never clobber a concurrent SP save.
        val utxos = secureStorage.getSilentPaymentUtxos(walletId)
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "SP merge: wallet=${walletId.take(8)} utxos=${utxos.size}")
        }
        if (utxos.isEmpty()) return transactions
        val network = currentWallet.network()
        val byTxid = utxos.groupBy { it.txid.lowercase() }
        val patched =
            transactions.map { tx ->
                val group = byTxid[tx.txid.lowercase()] ?: return@map tx
                val received = group.sumOf { it.valueSats.toLong() }
                if (received <= 0L) return@map tx
                val address =
                    group.firstOrNull()?.let { utxo ->
                        runCatching {
                            Address.fromScript(
                                Script(utxo.scriptPubKeyHex.hexToByteArray()),
                                network,
                            ).toString()
                        }.getOrNull()
                    }
                tx.copy(
                    amountSats = tx.amountSats + received,
                    isSelfTransfer = true,
                    address = address ?: tx.address,
                    addressAmount = received.toULong(),
                    timestamp = tx.timestamp ?: group.maxOfOrNull { it.timestamp ?: 0L }?.takeIf { it > 0L },
                )
            }
        val knownTxids = patched.map { it.txid.lowercase() }.toSet()
        val receiveExtras =
            byTxid
                .filterKeys { it !in knownTxids }
                .map { (_, group) ->
                    val received = group.sumOf { it.valueSats.toLong() }
                    val height = group.maxOf { it.height }
                    val address =
                        group.firstOrNull()?.let { utxo ->
                            runCatching {
                                Address.fromScript(
                                    Script(utxo.scriptPubKeyHex.hexToByteArray()),
                                    network,
                                ).toString()
                            }.getOrNull()
                        }
                    val timestamp =
                        resolveSilentPaymentTimestamp(
                            stored = group.maxOfOrNull { it.timestamp ?: 0L }?.takeIf { it > 0L },
                            height = height,
                            unconfirmedFallback = true,
                        )
                    TransactionDetails(
                        txid = group.first().txid,
                        amountSats = received,
                        fee = null,
                        confirmationTime =
                            if (height > 0) {
                                ConfirmationTime(height = height.toUInt(), timestamp = (timestamp ?: 0L).toULong())
                            } else {
                                null
                            },
                        isConfirmed = height > 0,
                        timestamp = timestamp,
                        address = address,
                        addressAmount = received.toULong(),
                    )
                }
        val spendExtras =
            utxos
                .filter { it.spent && !it.spendTxid.isNullOrBlank() }
                .groupBy { it.spendTxid!!.lowercase() }
                .filterKeys { it !in knownTxids }
                .map { (_, group) ->
                    val spent = group.sumOf { it.valueSats.toLong() }
                    val fee = group.mapNotNull { it.spendFeeSats }.maxOrNull()
                    val recipient = (spent - (fee?.toLong() ?: 0L)).coerceAtLeast(0L)
                    val timestamp = group.maxOfOrNull { it.spendTimestamp ?: 0L }?.takeIf { it > 0L }
                    val spendHeight = group.maxOf { it.spendHeight }
                    TransactionDetails(
                        txid = group.first().spendTxid!!,
                        amountSats = -spent,
                        fee = fee,
                        confirmationTime =
                            if (spendHeight > 0) {
                                ConfirmationTime(height = spendHeight.toUInt(), timestamp = (timestamp ?: 0L).toULong())
                            } else {
                                null
                            },
                        isConfirmed = spendHeight > 0,
                        timestamp = timestamp,
                        address = group.firstNotNullOfOrNull { it.spendAddress },
                        addressAmount = recipient.toULong(),
                    )
                }
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "SP merge: receiveExtras=${receiveExtras.size} spendExtras=${spendExtras.size}")
        }
        return patched + receiveExtras + spendExtras
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
        // Identity guard: a wallet switch is in flight and the loaded wallet does
        // not belong to the active wallet ID — skip rather than publish wallet A's
        // balance under wallet B's name.
        if (loadedWalletId != activeWalletId) return
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
                activeWalletId?.let { walletId ->
                    resolveCurrentReceiveAddress(currentWallet, walletId)
                } ?: previousState.currentAddress
            persistL1ReceiveAddress(activeWalletId, lastAddress)
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
                    balanceSats = amountToSats(balance.total) + silentPaymentUnspentSats(activeWalletId),
                    pendingIncomingSats = if (shouldPreserveDerivedState) previousState.pendingIncomingSats else 0UL,
                    pendingOutgoingSats = if (shouldPreserveDerivedState) previousState.pendingOutgoingSats else 0UL,
                    isTransactionHistoryLoading = true,
                    transactions = if (shouldPreserveDerivedState) previousState.transactions else emptyList(),
                    currentAddress = lastAddress,
                    currentAddressInfo = currentAddressInfo,
                    silentPaymentAddress = silentPaymentKeys?.address,
                    silentPaymentsSupported = silentPaymentsSupported,
                    canReceiveSilentPayments = canReceiveSilentPayments(activeWallet),
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
                                    filterHiddenBitcoinTransactions(
                                        walletId,
                                        filterPendingReplacementTransactions(walletId, partialTransactions),
                                    )
                                val partialState = _walletState.value
                                _walletState.value =
                                    partialState.copy(
                                        isTransactionHistoryLoading = true,
                                        transactions = visiblePartialTransactions,
                                        error = null,
                                    )
                            }
                        val visibleTransactions =
                            filterHiddenBitcoinTransactions(
                                walletId,
                                filterPendingReplacementTransactions(walletId, transactions),
                            )
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
                        ensureTransactionHistoryHydrated()
                    }
                }
            }
    }

    private fun refreshWalletListMetadata() {
        val previousState = _walletState.value
        val activeWalletId = secureStorage.getActiveWalletId()
        val allWallets: List<StoredWallet>
        val activeWallet: StoredWallet?
        try {
            allWallets = secureStorage.getAllWallets()
            activeWallet = activeWalletId?.let { secureStorage.getWalletMetadata(it) }
        } catch (_: IllegalArgumentException) {
            return
        }
        _walletState.value =
            WalletListMetadataPolicy.apply(
                previous = previousState,
                allWallets = allWallets,
                activeWallet = activeWallet,
                loadedWalletId = loadedWalletId,
                activeWalletId = activeWalletId,
            )
    }

    private fun updateWalletState() {
        val currentWallet = wallet
        val previousState = _walletState.value
        val activeWalletId = secureStorage.getActiveWalletId()
        // Identity guard: don't publish a loaded wallet's data under a different
        // active wallet's metadata while a wallet switch is in flight.
        // Also skip when wallet is null but loadedWalletId disagrees with active
        // (half-failed load / switch in flight), so we don't flash empty state.
        if (loadedWalletId != null && loadedWalletId != activeWalletId) return
        if (currentWallet != null && loadedWalletId != activeWalletId) return
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
                val cachedAddress =
                    activeWalletId
                        ?.let { secureStorage.getL1ReceiveAddress(it) }
                        ?.takeIf { it.isNotBlank() }
                _walletState.value =
                    previousState.copy(
                        isInitialized = allWallets.isNotEmpty() || cachedAddress != null,
                        wallets = allWallets,
                        activeWallet = activeWallet,
                        balanceSats = 0UL,
                        pendingIncomingSats = 0UL,
                        pendingOutgoingSats = 0UL,
                        isTransactionHistoryLoading = false,
                        transactions = emptyList(),
                        currentAddress = cachedAddress,
                        currentAddressInfo =
                            buildCurrentReceiveAddressInfo(
                                walletId = activeWalletId,
                                address = cachedAddress,
                            ),
                        lastSyncTimestamp = activeWalletId?.let { secureStorage.getLastSyncTime(it) },
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
            val visibleTransactions =
                filterHiddenBitcoinTransactions(
                    activeWalletId,
                    filterPendingReplacementTransactions(activeWalletId, transactions),
                )
            val stateChecksum =
                buildWalletStateChecksum(
                    amountToSats(balance.total) + silentPaymentUnspentSats(activeWalletId),
                    visibleTransactions,
                )
            if (BuildConfig.DEBUG) {
                Log.d(
                    TAG,
                    "Pending: incoming=${stateChecksum.pendingIncomingSats}, outgoing=${stateChecksum.pendingOutgoingSats}",
                )
            }

            val lastAddress =
                activeWalletId?.let { walletId ->
                    resolveCurrentReceiveAddress(currentWallet, walletId)
                }
            persistL1ReceiveAddress(activeWalletId, lastAddress)

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
                    silentPaymentAddress = silentPaymentKeys?.address,
                    silentPaymentsSupported = silentPaymentsSupported,
                    canReceiveSilentPayments = canReceiveSilentPayments(activeWallet),
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
        val activeWalletId = secureStorage.getActiveWalletId()
        // Identity guard: never merge A’s events into B’s transaction history.
        if (loadedWalletId != activeWalletId) return
        if (existingState.activeWallet?.id != null && existingState.activeWallet?.id != activeWalletId) {
            updateWalletState()
            return
        }

        if (existingState.transactions.isEmpty()) {
            updateWalletState()
            return
        }

        try {
            val balance = currentWallet.balance()

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
            val transactionSources = activeWalletId?.let(secureStorage::getAllTransactionSources).orEmpty()
            val cpfpParentTxids = cpfpEligibleParentTxids(currentWallet)
            val pendingReplacements =
                activeWalletId?.let { secureStorage.getPendingReplacementTransactions(it) }.orEmpty()
            val updatedTxDetails = mutableMapOf<String, TransactionDetails>()
            for (txid in affectedTxids) {
                val cachedTransaction = walletTransactionCache[txid] ?: continue
                buildTransactionDetails(
                    currentWallet = currentWallet,
                    activeWalletId = activeWalletId,
                    txid = txid,
                    cachedTransaction = cachedTransaction,
                    network = network,
                    cpfpParentTxids = cpfpParentTxids,
                )?.let { details ->
                    updatedTxDetails[txid] =
                        decorateTransactionDetails(
                            details = details,
                            transactionSwapDetails = transactionSwapDetails,
                            liquidSwapDetails = liquidSwapDetails,
                            transactionSources = transactionSources,
                            pendingReplacements = pendingReplacements,
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
                filterHiddenBitcoinTransactions(
                    activeWalletId,
                    filterPendingReplacementTransactions(
                        activeWalletId,
                        mergedTransactions.sortedByDescending { it.timestamp ?: Long.MAX_VALUE },
                    ),
                )
            val hiddenTxids =
                mergedTransactions
                    .map { it.txid }
                    .toSet() - sortedTransactions.map { it.txid }.toSet()
            val checksum = buildWalletStateChecksum(amountToSats(balance.total), sortedTransactions)

            if (shouldRunIncrementalReconcile()) {
                val fullTransactions =
                    filterHiddenBitcoinTransactions(
                        activeWalletId,
                        filterPendingReplacementTransactions(
                            activeWalletId,
                            buildTransactionDetailsList(
                                currentWallet = currentWallet,
                                activeWalletId = activeWalletId,
                            ),
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
                activeWalletId?.let { walletId ->
                    resolveCurrentReceiveAddress(currentWallet, walletId)
                } ?: existingState.currentAddress
            persistL1ReceiveAddress(activeWalletId, lastAddress)
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
                    balanceSats = checksum.balanceSats + silentPaymentUnspentSats(activeWalletId),
                    pendingIncomingSats = checksum.pendingIncomingSats,
                    pendingOutgoingSats = checksum.pendingOutgoingSats,
                    isTransactionHistoryLoading = false,
                    transactions = sortedTransactions,
                    currentAddress = lastAddress,
                    currentAddressInfo = currentAddressInfo,
                    silentPaymentAddress = silentPaymentKeys?.address,
                    silentPaymentsSupported = silentPaymentsSupported,
                    canReceiveSilentPayments = canReceiveSilentPayments(existingState.activeWallet),
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
    fun setSecurityMethod(
        method: SecureStorage.SecurityMethod,
        acknowledgedDowngradeRisk: Boolean = false,
    ) {
        secureStorage.setSecurityMethod(method, acknowledgedDowngradeRisk)
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
     * Wrap spend secrets for biometric unlock. PIN wrap is kept as recovery.
     */
    fun enrollBiometricLock(cipher: javax.crypto.Cipher) {
        secureStorage.enrollBiometricLock(cipher)
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

    fun getClearClipboardMode(): SecureStorage.ClearClipboardMode =
        secureStorage.getClearClipboardMode()

    fun setClearClipboardMode(mode: SecureStorage.ClearClipboardMode) {
        secureStorage.setClearClipboardMode(mode)
    }

    fun getClearClipboard(): Boolean = secureStorage.getClearClipboard()

    fun setClearClipboard(enabled: Boolean) {
        secureStorage.setClearClipboard(enabled)
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
                    // Decoy Spark unilateral-exit residue (tx hex, destination,
                    // funding, backup blob) must not survive even when the
                    // L2-data callback was not wired by the caller.
                    runCatching { secureStorage.clearSparkExitTxSet(duressWalletId) }
                    runCatching { secureStorage.clearSparkExitFunding(duressWalletId) }
                    runCatching { secureStorage.clearSparkExitBackup(duressWalletId) }
                    runCatching { java.io.File(sparkDbDir, duressWalletId).deleteRecursively() }
                    // Decoy Ark state must not survive "disable duress" (plausible
                    // deniability): mirror ArkRepository.deleteWalletData scrub —
                    // session DB, orphan/import-tmp dirs, safety-copy backups,
                    // labels/addresses/funding/backup markers, and caches.
                    // (External SAF auto-backup folder is user-owned and stays.)
                    runCatching {
                        java.io.File(java.io.File(appContext.cacheDir, "ark-session"), duressWalletId)
                            .takeIf { it.exists() }?.deleteRecursively()
                    }
                    runCatching {
                        java.io.File(appContext.cacheDir, "ark-session")
                            .listFiles()
                            ?.filter { it.isDirectory && it.name.startsWith("$duressWalletId-") }
                            ?.forEach { it.deleteRecursively() }
                    }
                    runCatching {
                        java.io.File(appContext.filesDir, "ark")
                            .let { java.io.File(it, duressWalletId) }
                            .takeIf { it.exists() }?.deleteRecursively()
                    }
                    runCatching {
                        java.io.File(appContext.cacheDir, "ark-session-backups")
                            .listFiles()
                            ?.filter { it.isDirectory && it.name.startsWith("$duressWalletId-") }
                            ?.forEach { it.deleteRecursively() }
                    }
                    runCatching { secureStorage.clearArkWalletStateCache(duressWalletId) }
                    runCatching { secureStorage.clearArkMovementJournal(duressWalletId) }
                    runCatching { secureStorage.clearArkExitClaimHistory(duressWalletId) }
                    runCatching { secureStorage.clearArkPendingClaim(duressWalletId) }
                    runCatching { secureStorage.clearArkMovementDestinations(duressWalletId) }
                    runCatching { secureStorage.setArkFundingTxids(duressWalletId, emptyList()) }
                    runCatching { secureStorage.clearArkAutoDbBackupLastInfo(duressWalletId) }
                    runCatching { secureStorage.clearAllArkWalletData(duressWalletId) }
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

    fun isWipePinEnabled(): Boolean = secureStorage.isWipePinEnabled()

    fun saveWipePin(pin: String) {
        secureStorage.saveWipePin(pin)
    }

    fun clearWipePin() {
        secureStorage.clearWipePin()
    }

    fun isSpendPinEnabled(): Boolean = secureStorage.isSpendPinEnabled()

    fun setSpendPinEnabled(enabled: Boolean) {
        secureStorage.setSpendPinEnabled(enabled)
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
     * Best-effort secure delete: overwrite file contents before unlinking so a
     * simple forensic carve of freed blocks does not recover wallet DBs.
     * NAND wear-leveling means this cannot guarantee erasure — Keystore-backed
     * secrets and the verification pass remain the real assurance.
     */
    private fun secureDeleteRecursively(root: java.io.File): Boolean {
        try {
            if (root.isFile) {
                overwriteFileContents(root)
            } else if (root.isDirectory) {
                root.walkTopDown().filter { it.isFile }.forEach { overwriteFileContents(it) }
            }
        } catch (_: Exception) {
            // Overwrite is best-effort; fall through to delete.
        }
        return root.deleteRecursively()
    }

    private fun overwriteFileContents(file: java.io.File) {
        try {
            val length = file.length()
            if (length <= 0) return
            // Single zero pass, capped to avoid stalling on huge files.
            val capped = minOf(length, 8L * 1024L * 1024L)
            java.io.RandomAccessFile(file, "rw").use { raf ->
                val zeros = ByteArray(4096)
                var remaining = capped
                while (remaining > 0) {
                    val chunk = minOf(remaining, zeros.size.toLong()).toInt()
                    raf.write(zeros, 0, chunk)
                    remaining -= chunk
                }
                raf.fd.sync()
            }
        } catch (_: Exception) {
        }
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
                if (bdkDbDir.exists() && !secureDeleteRecursively(bdkDbDir)) {
                    error("bdkDbDir.secureDeleteRecursively returned false")
                }
                bdkDbDir.mkdirs()
            }

            step("delete-lwk-dir") {
                if (lwkDbDir.exists() && !secureDeleteRecursively(lwkDbDir)) {
                    error("lwkDbDir.secureDeleteRecursively returned false")
                }
                lwkDbDir.mkdirs()
            }

            step("delete-spark-dir") {
                // Spark SDK keeps per-wallet state under filesDir/spark/<walletId>.
                // Without this the "full wipe" leaves rich Layer-2 metadata behind.
                if (sparkDbDir.exists() && !secureDeleteRecursively(sparkDbDir)) {
                    error("sparkDbDir.secureDeleteRecursively returned false")
                }
            }

            step("delete-spark-exit-backup-dir") {
                // Opaque exit-state blobs disclose balance and history.
                if (sparkExitBackupDir.exists() && !secureDeleteRecursively(sparkExitBackupDir)) {
                    error("sparkExitBackupDir.secureDeleteRecursively returned false")
                }
            }

            step("delete-ark-dir") {
                // Legacy durable Bark path (pre session-only); scrub residue if present.
                if (arkDbDir.exists() && !secureDeleteRecursively(arkDbDir)) {
                    error("arkDbDir.secureDeleteRecursively returned false")
                }
            }

            step("delete-ark-session-dir") {
                // Per-wallet Bark session dirs under cacheDir/ark-session.
                if (arkSessionDir.exists() && !secureDeleteRecursively(arkSessionDir)) {
                    error("arkSessionDir.secureDeleteRecursively returned false")
                }
            }

            step("delete-ark-auto-backup-dir") {
                // Legacy in-app auto-backup only; external SAF folder is user-owned
                // and intentionally survives (user-owned durable copy).
                if (arkAutoBackupDir.exists() && !secureDeleteRecursively(arkAutoBackupDir)) {
                    error("arkAutoBackupDir.secureDeleteRecursively returned false")
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
        check("residue-spark-exit-backup-dir") {
            sparkExitBackupDir.exists() && sparkExitBackupDir.walk().any { it.isFile }
        }
        check("residue-ark-dir") { arkDbDir.exists() && arkDbDir.walk().any { it.isFile } }
        check("residue-ark-auto-backup-dir") {
            arkAutoBackupDir.exists() && arkAutoBackupDir.walk().any { it.isFile }
        }
        check("residue-ark-session-dir") {
            val dir = java.io.File(appContext.cacheDir, "ark-session")
            dir.exists() && dir.walk().any { it.isFile }
        }
        check("residue-ark-session-backup-dir") {
            val dir = java.io.File(appContext.cacheDir, "ark-session-backups")
            dir.exists() && dir.walk().any { it.isFile }
        }

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

    private fun WalletNetwork.toBdkNetworkKind(): NetworkKind {
        return when (this) {
            WalletNetwork.BITCOIN -> NetworkKind.MAIN
        }
    }

    private fun Network.toNetworkKind(): NetworkKind {
        return when (this) {
            Network.BITCOIN -> NetworkKind.MAIN
            else -> NetworkKind.TEST
        }
    }

    sealed interface ConnectionEvent {
        data object ConnectionLost : ConnectionEvent
    }
}
