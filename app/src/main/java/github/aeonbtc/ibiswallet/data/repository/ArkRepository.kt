package github.aeonbtc.ibiswallet.data.repository

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.net.toUri
import github.aeonbtc.ibiswallet.R
import github.aeonbtc.ibiswallet.data.local.SecureStorage
import github.aeonbtc.ibiswallet.data.model.ArkAutoDbBackupInfo
import github.aeonbtc.ibiswallet.data.model.ArkDefaults
import github.aeonbtc.ibiswallet.data.model.ArkEvent
import github.aeonbtc.ibiswallet.data.model.ArkExitClaimHistory
import github.aeonbtc.ibiswallet.data.model.ArkExitProgress
import github.aeonbtc.ibiswallet.data.model.ArkExitVtxo
import github.aeonbtc.ibiswallet.data.model.ArkLifecycleState
import github.aeonbtc.ibiswallet.data.model.ArkMovement
import github.aeonbtc.ibiswallet.data.model.ArkOnchainUtxo
import github.aeonbtc.ibiswallet.data.model.ArkReceiveKind
import github.aeonbtc.ibiswallet.data.model.ArkReceiveState
import github.aeonbtc.ibiswallet.data.model.ArkRecoveredOnchainDeposit
import github.aeonbtc.ibiswallet.data.model.ArkSendState
import github.aeonbtc.ibiswallet.data.model.ArkServerConfig
import github.aeonbtc.ibiswallet.data.model.ArkTransferState
import github.aeonbtc.ibiswallet.data.model.ArkVtxo
import github.aeonbtc.ibiswallet.data.model.ArkWalletState
import github.aeonbtc.ibiswallet.data.model.SeedFormat
import github.aeonbtc.ibiswallet.data.model.WalletPolicyType
import github.aeonbtc.ibiswallet.data.repository.ArkRepository.Companion.AUTO_BACKUP_BACKUP_NAME
import github.aeonbtc.ibiswallet.data.repository.ArkRepository.Companion.AUTO_REFRESH_FAILURE_RETRY_MS
import github.aeonbtc.ibiswallet.data.repository.ArkRepository.Companion.AUTO_REFRESH_MAX_FEE_SATS
import github.aeonbtc.ibiswallet.localization.AppLocale
import github.aeonbtc.ibiswallet.tor.EsploraClearnetRelay
import github.aeonbtc.ibiswallet.tor.EsploraTorRelay
import github.aeonbtc.ibiswallet.tor.TorManager
import github.aeonbtc.ibiswallet.util.ArkBackupCrypto
import github.aeonbtc.ibiswallet.util.ArkWalletDataPack
import github.aeonbtc.ibiswallet.util.ElectrumSeedUtil
import github.aeonbtc.ibiswallet.util.LightningKind
import github.aeonbtc.ibiswallet.util.ParsedSendRecipient
import github.aeonbtc.ibiswallet.util.PreferIpv4Dns
import github.aeonbtc.ibiswallet.util.SecureLog
import github.aeonbtc.ibiswallet.util.SilentPayment
import github.aeonbtc.ibiswallet.util.isLightningAddressPayment
import github.aeonbtc.ibiswallet.util.parseSendRecipient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import tech.second.bark.notificationsFlow
import uniffi.bark.Config
import uniffi.bark.FeeEstimate
import uniffi.bark.LightningSendStatus
import uniffi.bark.Movement
import uniffi.bark.Network
import uniffi.bark.OnchainWallet
import uniffi.bark.RecoveryReport
import uniffi.bark.Vtxo
import uniffi.bark.VtxoState
import uniffi.bark.Wallet
import uniffi.bark.WalletNotification
import uniffi.bark.WalletOpenArgs
import uniffi.bark.validateArkAddress
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import uniffi.bark.Exception as BarkException

/**
 * Ark (Bark) L2 repository — mainnet only.
 *
 * Owns one Bark [Wallet] for the active Ibis wallet. Seed material stays in
 * memory only while the wallet is loaded; smoke credentials are never logged.
 */
class ArkRepository(
    private val context: Context,
    private val secureStorage: SecureStorage,
) {
    private val mutex = Mutex()
    /** Serializes user/maintenance exit progression without blocking unrelated wallet UI. */
    private val exitOperationMutex = Mutex()

    /**
     * Serializes native Bark open/close so a new `Wallet.open` can never race an
     * in-flight `close()` from a timed-out prior load (loadGeneration allows overlap).
     * Two live native wallets on the same datadir abort the process (SIGABRT in
     * libbark_ffi_kotlin.so). Same guard pattern as TorManager's @Synchronized start/stop.
     */
    private val nativeHandleMutex = Mutex()
    private val torManager = TorManager.getInstance(context)

    private fun localizedString(id: Int): String =
        AppLocale.createLocalizedContext(context.applicationContext, secureStorage.getAppLocale())
            .getString(id)

    private fun localizedString(
        id: Int,
        vararg formatArgs: Any,
    ): String =
        AppLocale.createLocalizedContext(context.applicationContext, secureStorage.getAppLocale())
            .getString(id, *formatArgs)
    private val eventScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    /** Active loopback→Tor relay when Esplora is .onion (env proxies don't reach Bark FFI). */
    private var esploraTorRelay: EsploraTorRelay? = null
    /** Active loopback→IPv4 HTTPS relay so Bark never dials broken AAAA records. */
    private var esploraClearnetRelay: EsploraClearnetRelay? = null
    /** User-facing Esplora URL (may be .onion); Bark may get a 127.0.0.1 rewrite. */
    private var configuredEsploraUrl: String = secureStorage.getArkEsploraAddress()

    private val _arkState = MutableStateFlow(ArkWalletState(isInitialized = true))
    val arkState: StateFlow<ArkWalletState> = _arkState.asStateFlow()

    private val _sendState = MutableStateFlow<ArkSendState>(ArkSendState.Idle)
    val sendState: StateFlow<ArkSendState> = _sendState.asStateFlow()

    private val _receiveState = MutableStateFlow<ArkReceiveState>(ArkReceiveState.Idle)
    val receiveState: StateFlow<ArkReceiveState> = _receiveState.asStateFlow()

    private val _transferState = MutableStateFlow<ArkTransferState>(ArkTransferState.Idle)
    val transferState: StateFlow<ArkTransferState> = _transferState.asStateFlow()

    private val _lifecycleState = MutableStateFlow<ArkLifecycleState>(ArkLifecycleState.Idle)
    val lifecycleState: StateFlow<ArkLifecycleState> = _lifecycleState.asStateFlow()

    private val _events = MutableSharedFlow<ArkEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<ArkEvent> = _events.asSharedFlow()

    fun emitArkEvent(event: ArkEvent) {
        _events.tryEmit(event)
    }

    private val _arkMovementLabels = MutableStateFlow<Map<String, String>>(emptyMap())
    val arkMovementLabels: StateFlow<Map<String, String>> = _arkMovementLabels.asStateFlow()

    private val _arkAddressLabels = MutableStateFlow<Map<String, String>>(emptyMap())
    val arkAddressLabels: StateFlow<Map<String, String>> = _arkAddressLabels.asStateFlow()

    private val _loadedWalletId = MutableStateFlow<String?>(null)
    val loadedWalletId: StateFlow<String?> = _loadedWalletId.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _isConnecting = MutableStateFlow(false)
    val isConnecting: StateFlow<Boolean> = _isConnecting.asStateFlow()

    private var wallet: Wallet? = null
    /** Bark bundled on-chain wallet used for BTC→Ark boarding (Bitcoin deposit address). */
    private var onchainWallet: OnchainWallet? = null
    private var notificationJob: Job? = null
    /** Bark wallet [notificationJob] collects on — used to join that job before disposing its handle. */
    private var notificationWallet: Wallet? = null
    /** Retroactive / deferred board attempts after L1→Ark funding. */
    private var deferredBoardJob: Job? = null
    private var autoRefreshJob: Job? = null
    private var trackedRefreshRoundId: UInt? = null
    private var trackedRefreshScheduledHeight: Int? = null
    private var trackedRefreshAutomatic: Boolean = false
    private var trackedRefreshVtxoIds: List<String> = emptyList()
    private var autoDbBackupJob: Job? = null
    /** Post-open ASP/chain sync — cancelled+joined on unload so DB import is not blocked. */
    private var postOpenSyncJob: Job? = null
    private var manualRefreshJob: Job? = null
    private val autoDbBackupRunning = AtomicBoolean(false)
    /** Nested user-visible hydrates (load / pull-to-refresh). Background paints must not touch this. */
    private val syncSpinner = ArkSyncSpinner()
    private var syncSpinnerTimeoutJob: Job? = null
    private var lastAutoDbBackupFingerprint: String? = null
    private var preparedDestination: String? = null
    private var preparedAmountSats: Long? = null
    private var preparedMethod: String? = null
    private var preparedPayKind: ArkPayKind? = null
    private var preparedUseAllFunds: Boolean = false
    private var preparedLabel: String? = null
    /**
     * After a successful send, hold destination until it is attached to a movement id.
     * Bark history is often oldest-first and omits peer addresses; notifications can race
     * a full refresh before the stamp sticks.
     */
    private var pendingSendDestination: String? = null
    private var pendingSendAmountSats: Long? = null
    private var pendingSendKind: ArkPayKind? = null
    /** Baseline [ArkWalletState.claimableLightningReceiveSats] when a BOLT11 invoice was last created; -1L means no active tracking. */
    private var receiveLightningBaselineSats: Long = -1L
    /** Polls open BOLT11 receive until paid or cancelled. */
    private var lightningReceiveWatchJob: Job? = null
    /** Prevents spamming NeedsRefresh / RefreshSoon in-app banners across ticks. */
    private var lastNeedsRefreshEmitMs: Long = 0L
    private var lastRefreshSoonEmitMs: Long = 0L
    private var lastAutoRefreshAttemptMs: Long = 0L
    /** When true, next auto-refresh wait uses [AUTO_REFRESH_FAILURE_RETRY_MS] instead of full cooldown. */
    private var lastAutoRefreshFailed: Boolean = false
    private var lastKnownChainTipHeight: Int? = null
    private var lastTipFetchMs: Long = 0L
    /** Monotonic generation so a timed-out prior load cannot overwrite a newer wallet. */
    private val loadGeneration = AtomicLong(0L)
    /** True when the last [Wallet.open] was expected to run a seed-mailbox scan. */
    private var lastOpenExpectedMailbox: Boolean = false
    /**
     * Wallet id that completed ASP/mailbox hydrate this process. SecureStorage paint
     * cache is paint-only until this matches the loaded wallet. External SAF arkdb
     * backups are the only durable offline DB (disaster when ASP is gone).
     */
    private var aspHydratedWalletId: String? = null
    /**
     * Per-wallet Bark working dir under `cacheDir/ark-session/<walletId>`.
     * Reused across process death when the OS keeps cache; wiped on wallet delete / auto-wipe.
     * Never under filesDir — external SAF arkdb backups remain the only user-owned durable copy.
     */
    private var sessionDataDir: File? = null
    private var sessionWalletId: String? = null
    /**
     * Native handles from a [Wallet.open] that has not been attached (or was superseded).
     * The next open/dispose must close these first — otherwise the datadir stays locked
     * until process death.
     */
    private var unattachedBarkHandles: OpenedArkWallet? = null
    /** Last leftover wallet already closed under [nativeHandleMutex]; skip a second close. */
    private var closedUnattachedWallet: Wallet? = null
    /** Delayed reopen after a datadir-lock failure so the UI recovers without an app restart. */
    private var datadirLockReopenJob: Job? = null
    private var datadirLockReopenAttempts: Int = 0
    /**
     * Disaster import: pre-installed session dir consumed by the next [loadWallet].
     * Not durable across process death beyond the cache dir itself.
     */
    private var pendingImportSessionDir: File? = null
    private var pendingImportWalletId: String? = null
    /**
     * Session dirs queued for delete after native dispose (failed opens / superseded loads).
     * Normal unload keeps the wallet dir for fast reopen.
     */
    private val pendingSessionDirsToDelete = mutableListOf<File?>()
    private var purgedLegacyDurableArkDirs: Boolean = false
    /** Debounce for forced re-open when a seed wallet is running off-chain-only. */
    private var lastOnchainReopenAttemptMs: Long = 0L
    /** Cooldown for the on-chain-unavailable snackbar. */
    private var lastOnchainUnavailableEmitMs: Long = 0L
    /** Cooldown for below-min board snackbar (stuck on-chain deposit). */
    private var lastBoardBelowMinEmitMs: Long = 0L
    /**
     * After L1 recover of a stuck below-min deposit, do not re-paint prior on-chain
     * totals from cache/Esplora bridge while Bark briefly reports 0.
     */
    private var suppressStaleOnchainPaint: Boolean = false
    /**
     * When the on-chain DB is new/empty (first open or after wipe), Bark has no revealed
     * addresses, so sync cannot see deposits made to addresses Ibis already cached.
     * Catch-up re-reveals them once per attached session before boarding.
     */
    private var onchainRevealCatchUpDone: Boolean = false
    /** Consecutive board attempts where Bark is blind to a deposit Ibis already painted. */
    private var blindBoardAttempts: Int = 0
    /** Rolling window of auto-refresh fees spent this UTC day (sats). */
    private var autoRefreshFeeSpentTodaySats: Long = 0L
    private var autoRefreshFeeDayEpoch: Long = -1L
    /** Shared preflight client — IPv4-first, tight timeouts for ASP + Esplora probes. */
    private val preflightHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .dns(PreferIpv4Dns)
            .connectTimeout(ESPLORA_PREFLIGHT_CONNECT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(ESPLORA_PREFLIGHT_READ_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(ESPLORA_PREFLIGHT_READ_SECONDS, TimeUnit.SECONDS)
            .callTimeout(ESPLORA_PREFLIGHT_CALL_SECONDS, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    /** Shared Esplora HTTP client — connection reuse across tip/utxo/tx lookups. */
    private val esploraHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .dns(PreferIpv4Dns)
            .connectTimeout(ESPLORA_DETAIL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(ESPLORA_DETAIL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(ESPLORA_DETAIL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .callTimeout(ESPLORA_DETAIL_TIMEOUT_SECONDS + 2L, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }
    /** Short-lived /utxo cache so one refresh does not re-hit the same address. */
    private val esploraUtxoCache = mutableMapOf<String, CachedEsploraUtxos>()

    private data class CachedEsploraUtxos(
        val fetchedAtMs: Long,
        val utxos: List<org.json.JSONObject>,
    )

    /** Root for Bark session dirs (OS may clear cache; never backed up by Android backup). */
    private fun arkSessionRootDir(): File =
        File(context.cacheDir, ARK_SESSION_ROOT).also { it.mkdirs() }

    /** Legacy durable path — only deleted on migrate/wipe, never opened. */
    private fun legacyDurableArkRoot(): File = File(context.filesDir, "ark")

    /** Stable per-wallet Bark datadir: `cacheDir/ark-session/<walletId>`. */
    private fun sessionDataDirForWallet(walletId: String): File =
        File(arkSessionRootDir(), walletId)

    /**
     * True when [dir] already has a Bark SQLite DB we can reopen without mailbox recovery.
     * Fresh/empty dirs return false so the first open still runs seed-mailbox recovery.
     */
    private fun hasReusableBarkDb(dir: File): Boolean {
        val db = File(dir, ArkWalletDataPack.DB_FILE_NAME)
        if (!db.isFile || db.length() < MIN_REUSABLE_BARK_DB_BYTES) return false
        return runCatching {
            db.inputStream().use { input ->
                val header = ByteArray(16)
                val read = input.read(header)
                read >= 15 && header.decodeToString(0, 15) == "SQLite format 3"
            }
        }.getOrDefault(false)
    }

    private fun hasMailboxScannedMarker(dir: File): Boolean =
        File(dir, ArkMailboxRecoveryPolicy.SCANNED_MARKER_NAME).isFile

    private fun markMailboxScanned(dir: File?) {
        if (dir == null || !dir.isDirectory) return
        runCatching {
            File(dir, ArkMailboxRecoveryPolicy.SCANNED_MARKER_NAME).writeText("1")
        }
    }

    private fun cachedArkHasFunds(walletId: String): Boolean {
        val cache = secureStorage.getArkWalletStateCache(walletId) ?: return false
        return cache.spendableSats > 0L ||
            cache.pendingInRoundSats > 0L ||
            cache.pendingBoardSats > 0L ||
            cache.pendingExitSats > 0L ||
            cache.claimableLightningReceiveSats > 0L ||
            cache.vtxos.isNotEmpty() ||
            cache.movements.isNotEmpty()
    }

    private fun prepareSessionDirForOpen(
        walletId: String,
        generation: Long,
    ): Pair<File, Boolean> {
        var sessionDir = takeSessionDataDirForOpen(walletId, generation)
        val reusable = hasReusableBarkDb(sessionDir)
        val marker = hasMailboxScannedMarker(sessionDir)
        val funds = cachedArkHasFunds(walletId)
        if (
            ArkMailboxRecoveryPolicy.shouldWipeForMailboxRescan(
                hasReusableDb = reusable,
                hasScannedMarker = marker,
                cachedHasFunds = funds,
            )
        ) {
            SecureLog.w(TAG, "Ark wiping unmarked/empty session for mailbox recovery")
            sessionDir = recreateEmptySessionDataDir(walletId)
            sessionDataDir = sessionDir
            sessionWalletId = walletId
        }
        val skipRecovery =
            ArkMailboxRecoveryPolicy.canSkipMailboxRecovery(
                hasReusableDb = hasReusableBarkDb(sessionDir),
                hasScannedMarker = hasMailboxScannedMarker(sessionDir),
                cachedHasFunds = funds,
                forceMailbox = false,
            )
        return sessionDir to skipRecovery
    }

    /** Ensure stable wallet dir exists; scrub legacy `walletId-*` ephemeral dirs. */
    private fun ensureSessionDataDir(walletId: String): File {
        val root = arkSessionRootDir()
        // Pre-reuse layout used unique dirs per open; remove orphans for this wallet.
        runCatching {
            root.listFiles()
                ?.filter { it.isDirectory && it.name.startsWith("$walletId-") }
                ?.forEach { deleteSessionDataDir(it) }
        }
        val dir = sessionDataDirForWallet(walletId)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /** Wipe and recreate the stable session dir (corrupt open / forced fresh recovery). */
    private fun recreateEmptySessionDataDir(walletId: String): File {
        val dir = sessionDataDirForWallet(walletId)
        deleteSessionDataDir(dir)
        dir.mkdirs()
        return dir
    }

    @Suppress("UNUSED_PARAMETER")
    private fun createEmptySessionDataDir(walletId: String, generation: Long): File {
        // [generation] kept for call-site compatibility; path is stable per wallet.
        return recreateEmptySessionDataDir(walletId)
    }

    private fun deleteSessionDataDir(dir: File?) {
        if (dir == null) return
        runCatching {
            if (dir.exists()) dir.deleteRecursively()
        }.onFailure {
            SecureLog.w(TAG, "Ark session dir delete failed: ${publicError(it)}")
        }
    }

    private fun flushPendingSessionDirDeletes() {
        if (pendingSessionDirsToDelete.isEmpty()) return
        val pending = pendingSessionDirsToDelete.toList()
        pendingSessionDirsToDelete.clear()
        pending.forEach { deleteSessionDataDir(it) }
    }

    /** Detach live session pointer without deleting the cache dir (kept for fast reopen). */
    private fun detachSessionDataDirLocked() {
        sessionDataDir = null
        sessionWalletId = null
    }

    private fun clearSessionDataDirLocked() {
        val dir = sessionDataDir
        detachSessionDataDirLocked()
        deleteSessionDataDir(dir)
    }

    private fun purgeLegacyDurableArkDirsOnce() {
        if (purgedLegacyDurableArkDirs) return
        purgedLegacyDurableArkDirs = true
        runCatching {
            legacyDurableArkRoot().takeIf { it.exists() }?.deleteRecursively()
        }
        runCatching {
            File(context.filesDir, LEGACY_AUTO_BACKUP_DIR).takeIf { it.exists() }?.deleteRecursively()
        }
    }

    /**
     * Resolve the working Bark datadir for this open.
     * Reuses `cacheDir/ark-session/<walletId>` when present so cold start skips mailbox recovery.
     * Post-import: consume [pendingImportSessionDir] once.
     */
    private fun takeSessionDataDirForOpen(walletId: String, generation: Long): File {
        purgeLegacyDurableArkDirsOnce()
        val pending = pendingImportSessionDir
        val pendingId = pendingImportWalletId
        if (pending != null && pendingId == walletId && pending.isDirectory) {
            pendingImportSessionDir = null
            pendingImportWalletId = null
            // Normalize import into the stable wallet path when the install used a temp dir.
            val stable = sessionDataDirForWallet(walletId)
            val dir =
                if (pending.absolutePath == stable.absolutePath) {
                    pending
                } else {
                    deleteSessionDataDir(stable)
                    if (pending.renameTo(stable)) {
                        stable
                    } else {
                        // Fallback: copy tree then drop temp.
                        runCatching {
                            pending.copyRecursively(stable, overwrite = true)
                            deleteSessionDataDir(pending)
                            stable
                        }.getOrElse {
                            pending
                        }
                    }
                }
            sessionDataDir = dir
            sessionWalletId = walletId
            return dir
        }
        // Drop a stale pending import for another wallet / failed prior open.
        if (pending != null) {
            pendingImportSessionDir = null
            pendingImportWalletId = null
            // Only delete if it is not another wallet's stable cache dir.
            if (pendingId.isNullOrBlank() || pending != sessionDataDirForWallet(pendingId)) {
                deleteSessionDataDir(pending)
            }
        }
        val dir = ensureSessionDataDir(walletId)
        sessionDataDir = dir
        sessionWalletId = walletId
        return dir
    }

    /** Active session dir for [walletId], if any (export/auto-backup while loaded). */
    private fun activeSessionDataDir(walletId: String): File? =
        sessionDataDir?.takeIf { sessionWalletId == walletId && it.isDirectory }

    fun serverConfig(): ArkServerConfig =
        ArkServerConfig(
            serverAddress = secureStorage.getArkServerAddress(),
            esploraAddress = secureStorage.getArkEsploraAddress(),
        )

    fun isEligible(walletId: String): Boolean {
        val meta = secureStorage.getWalletMetadata(walletId) ?: return false
        return !meta.isWatchOnly &&
            meta.seedFormat == SeedFormat.BIP39 &&
            meta.policyType == WalletPolicyType.SINGLE_SIG &&
            secureStorage.getMnemonic(walletId) != null
    }

    suspend fun loadWallet(
        walletId: String,
        forceMailbox: Boolean = false,
    ) =
        withContext(Dispatchers.IO) {
            // Capture generation before any suspend so a timed-out prior load is discarded.
            val generation = loadGeneration.incrementAndGet()

            // Fast path: already live — always re-hydrate from ASP (not local-DB-only).
            // Force-mailbox still reopens when the session DB looks like an unscanned skeleton.
            val needsMailboxReopen =
                forceMailbox &&
                    ArkMailboxRecoveryPolicy.shouldWipeForMailboxRescan(
                        hasReusableDb = hasReusableBarkDb(sessionDataDirForWallet(walletId)),
                        hasScannedMarker = hasMailboxScannedMarker(sessionDataDirForWallet(walletId)),
                        cachedHasFunds = cachedArkHasFunds(walletId),
                    )
            val alreadyLive =
                !needsMailboxReopen &&
                    mutex.withLock {
                        if (generation != loadGeneration.get()) return@withLock true
                        _loadedWalletId.value == walletId && wallet != null && _isConnected.value
                    }
            if (alreadyLive && !shouldRetryOnchainReopen(walletId)) {
                val snap =
                    mutex.withLock {
                        if (generation != loadGeneration.get()) return@withContext
                        wallet to onchainWallet
                    }
                val live = snap.first ?: return@withContext
                hydrateFromAspOutsideLock(
                    reason = "reload",
                    expectedWallet = live,
                    onchain = snap.second,
                    generation = generation,
                    walletId = walletId,
                    attemptBoard = false,
                    indicateSync = true,
                )
                return@withContext
            }
            if (alreadyLive) {
                // Off-chain-only session with a BIP39 wallet: full re-open to retry on-chain.
                SecureLog.w(TAG, "Ark reload: off-chain-only session — retrying on-chain open")
            }

            if (!isEligible(walletId)) {
                mutex.withLock {
                    if (generation != loadGeneration.get()) return@withLock
                    _arkState.value =
                        ArkWalletState(
                            walletId = walletId,
                            isInitialized = true,
                            error = localizedString(R.string.ark_error_requires_bip39_wallet),
                        )
                }
                return@withContext
            }

            // Unload previous wallet; dispose native handles outside the mutex so the next
            // open is not blocked on slow Bark close.
            val staleHandles =
                mutex.withLock {
                    if (generation != loadGeneration.get()) return@withLock null
                    unloadLocked(disposeHandles = false)
                }
            if (staleHandles != null) {
                disposeBarkHandles(staleHandles.first, staleHandles.second)
                stopEsploraRelays()
            }
            if (generation != loadGeneration.get()) return@withContext

            // Esplora preflight is pure network — run outside the mutex (parallel probes).
            _isConnecting.value = true
            mutex.withLock {
                if (generation == loadGeneration.get()) {
                    applyLoadingArkStateLocked(walletId)
                    // Paint last receive addresses immediately so Receive is not blocked on Bark open.
                    primeCachedReceiveStateLocked(walletId)
                }
            }
            beginSyncSpinner()
            try {
            val credentials =
                try {
                    resolveBarkCredentials(walletId)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (generation != loadGeneration.get()) return@withContext
                    markLoadFailed(walletId, publicError(e))
                    return@withContext
                }
            // Stable session dir (or post-import). Reused across cold starts when cache survives.
            val (sessionDir, reuseLocalDb) =
                prepareSessionDirForOpen(walletId, generation)
            lastOpenExpectedMailbox = !reuseLocalDb
            val datadir = sessionDir.absolutePath
            val openEndpoints = probeArkOpenEndpoints()
            if (generation != loadGeneration.get()) return@withContext
            if (!openEndpoints.aspReachable) {
                markLoadFailed(walletId, localizedString(R.string.ark_error_asp_unreachable))
                return@withContext
            }
            val esploraOrder = openEndpoints.esploraOrder
            if (esploraOrder.isEmpty()) {
                markLoadFailed(walletId, localizedString(R.string.ark_error_chain_source))
                return@withContext
            }
            // ASP is live — pill goes Connected. Sync spinner stays until attach + local paint.
            mutex.withLock {
                if (generation == loadGeneration.get()) {
                    markArkServerLiveLocked(walletId)
                }
            }

            var openedHandles: OpenedArkWallet? = null
            try {
                val opened =
                    try {
                        openBarkWalletWithFallbacks(
                            credentials = credentials,
                            datadir = datadir,
                            esploraOrder = esploraOrder,
                            skipRecovery = reuseLocalDb,
                        )
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // Reused DB path only: never wipe on ASP/Esplora/network failures.
                        if (!reuseLocalDb || isTransientArkOpenError(e)) throw e
                        SecureLog.w(
                            TAG,
                            "Ark reopen (skipRecovery) failed; retrying with recovery: ${publicError(e)}",
                        )
                        try {
                            openBarkWalletWithFallbacks(
                                credentials = credentials,
                                datadir = datadir,
                                esploraOrder = esploraOrder,
                                skipRecovery = false,
                            )
                        } catch (e2: CancellationException) {
                            throw e2
                        } catch (e2: Exception) {
                            if (isTransientArkOpenError(e2) || !isLocalBarkDbOpenError(e2)) throw e2
                            SecureLog.w(
                                TAG,
                                "Ark local DB unusable; recreating session: ${publicError(e2)}",
                            )
                            val fresh = recreateEmptySessionDataDir(walletId)
                            sessionDataDir = fresh
                            sessionWalletId = walletId
                            openBarkWalletWithFallbacks(
                                credentials = credentials,
                                datadir = fresh.absolutePath,
                                esploraOrder = esploraOrder,
                                skipRecovery = false,
                            )
                        }
                    }
                openedHandles = opened
                if (generation != loadGeneration.get()) {
                    disposeBarkHandles(opened.wallet, opened.onchain)
                    openedHandles = null
                    // Keep stable cache dir for the next open of this wallet.
                    if (sessionDataDir == sessionDir) detachSessionDataDirLocked()
                    return@withContext
                }
                // Attach + mint receive addresses under a short lock. Heavy local refresh runs
                // after releasing [mutex] so Receive/send are not blocked on history/Esplora.
                val attached =
                    mutex.withLock {
                        if (generation != loadGeneration.get()) {
                            false
                        } else {
                            val claimed =
                                nativeHandleMutex.withLock {
                                    if (opened.wallet === closedUnattachedWallet) {
                                        false
                                    } else {
                                        if (unattachedBarkHandles?.wallet === opened.wallet) {
                                            unattachedBarkHandles = null
                                        }
                                        wallet = opened.wallet
                                        onchainWallet = opened.onchain
                                        true
                                    }
                                }
                            if (!claimed) return@withLock false
                            _loadedWalletId.value = walletId
                            // Prefer live pointer (may be a recreated stable dir after corrupt reopen).
                            sessionDataDir =
                                sessionDataDir?.takeIf { it.isDirectory }
                                    ?: sessionDir
                            sessionWalletId = walletId
                            aspHydratedWalletId = null
                            loadLabelsLocked(walletId)
                            startNotificationsLocked(opened.wallet)
                            _isConnected.value = true
                            _isConnecting.value = false
                            runCatching { opened.wallet.runDaemon() }
                            publishReceiveAddressesFastLocked(walletId)
                            _arkState.value =
                                _arkState.value.copy(
                                    aspHydrated = false,
                                )
                            true
                        }
                    }
                if (!attached || generation != loadGeneration.get()) {
                    val drop =
                        mutex.withLock {
                            if (wallet === opened.wallet) {
                                wallet = null
                                onchainWallet = null
                            }
                            val notif =
                                notificationJob.takeIf { notificationWallet === opened.wallet }
                            if (notif != null) {
                                notificationJob = null
                                notificationWallet = null
                            }
                            Triple(opened.wallet, opened.onchain, notif)
                        }
                    drop.third?.let { job ->
                        job.cancel()
                        withTimeoutOrNull(ARK_JOB_JOIN_TIMEOUT_MS) { job.join() }
                    }
                    disposeBarkHandles(drop.first, drop.second)
                    openedHandles = null
                    if (sessionDataDir == sessionDir) detachSessionDataDirLocked()
                    return@withContext
                }
                openedHandles = null
                datadirLockReopenAttempts = 0
                cancelDatadirLockReopen()
                // Session Bark paint only — ASP/mailbox hydrate runs in post-open.
                mutex.withLock {
                    if (generation != loadGeneration.get() || wallet !== opened.wallet) return@withLock
                    refreshStateLocked(sync = false, attemptBoard = false)
                    _arkState.value =
                        _arkState.value.copy(
                            aspHydrated = false,
                        )
                }
                schedulePostOpenSync(
                    generation = generation,
                    walletId = walletId,
                    openedWallet = opened.wallet,
                )
            } catch (e: CancellationException) {
                openedHandles?.let { disposeBarkHandles(it.wallet, it.onchain) }
                if (sessionDataDir == sessionDir) detachSessionDataDirLocked()
                throw e
            } catch (e: Exception) {
                openedHandles?.let { disposeBarkHandles(it.wallet, it.onchain) }
                if (sessionDataDir == sessionDir) detachSessionDataDirLocked()
                if (generation != loadGeneration.get()) return@withContext
                val msg = publicError(e)
                SecureLog.w(TAG, "Ark load failed for wallet: $msg")
                markLoadFailed(walletId, msg)
                if (isDatadirLockError(e) && generation == loadGeneration.get()) {
                    scheduleDatadirLockReopen(walletId)
                }
            }
            } finally {
                endSyncSpinner()
            }
        }

    suspend fun unloadWallet() =
        withContext(Dispatchers.IO) {
            mutex.withLock { unloadLocked() }
        }

    suspend fun refreshState(indicateSync: Boolean = true) =
        withContext(Dispatchers.IO) {
            // Always hydrate from ASP outside [mutex] so send/review is not stuck behind
            // heartbeat / maintenance, and local Bark DB is never treated as source of truth.
            data class RefreshSnap(
                val wallet: Wallet,
                val onchain: OnchainWallet?,
                val walletId: String,
                val generation: Long,
            )
            val snapshot =
                mutex.withLock {
                    val w = wallet
                    val id = _loadedWalletId.value.orEmpty().ifBlank { _arkState.value.walletId.orEmpty() }
                    if (w == null) {
                        return@withLock null
                    }
                    RefreshSnap(
                        wallet = w,
                        onchain = onchainWallet,
                        walletId = id,
                        generation = loadGeneration.get(),
                    )
                }
            if (snapshot == null) {
                val retryId =
                    _loadedWalletId.value.orEmpty().ifBlank { _arkState.value.walletId.orEmpty() }
                if (
                    retryId.isNotBlank() &&
                    isEligible(retryId) &&
                    !_isConnected.value &&
                    !_isConnecting.value
                ) {
                    SecureLog.w(TAG, "Ark refresh: no live session — reopening")
                    loadWallet(retryId)
                }
                return@withContext
            }
            if (snapshot.walletId.isBlank()) return@withContext
            if (shouldRetryOnchainReopen(snapshot.walletId)) {
                // Off-chain-only session: boarding no-ops until the on-chain wallet opens.
                SecureLog.w(TAG, "Ark refresh: off-chain-only session — retrying on-chain open")
                loadWallet(snapshot.walletId)
                return@withContext
            }
            if (indicateSync) beginSyncSpinner()
            try {
                mutex.withLock {
                    if (wallet === snapshot.wallet) {
                        refreshStateLocked(sync = false, attemptBoard = false)
                    }
                }
            } finally {
                if (indicateSync) endSyncSpinner()
            }
            hydrateFromAspOutsideLock(
                reason = "refresh",
                expectedWallet = snapshot.wallet,
                onchain = snapshot.onchain,
                generation = snapshot.generation,
                walletId = snapshot.walletId,
                attemptBoard = false,
                indicateSync = false,
            )
            // Opt-in auto-board only (default off).
            if (secureStorage.isArkAutoBoardEnabled()) {
                scheduleDeferredBoardAttempts()
            }
        }

    private fun isArkStateVisiblyEmptyLocked(): Boolean =
        _arkState.value.spendableSats == 0L &&
            _arkState.value.pendingInRoundSats == 0L &&
            _arkState.value.pendingBoardSats == 0L &&
            _arkState.value.pendingExitSats == 0L &&
            _arkState.value.pendingLightningSendSats == 0L &&
            _arkState.value.claimableLightningReceiveSats == 0L &&
            _arkState.value.movements.isEmpty() &&
            _arkState.value.vtxos.isEmpty() &&
            !_arkState.value.hasInboundDeposit

    /**
     * Manual Wallet Management full sync: open-time mailbox report (if any) + ASP hydrate.
     * Normal open already hydrates from ASP; this path also surfaces RecoveryReport details.
     * A missing report after a scan was expected (skeleton DB / cancelled first open) forces
     * a wipe + reopen so Bark actually runs mailbox recovery.
     */
    suspend fun runMailboxRecoveryFullSync() =
        withContext(Dispatchers.IO) {
            data class Snap(
                val wallet: Wallet,
                val onchain: OnchainWallet?,
                val walletId: String,
                val generation: Long,
            )
            suspend fun currentSnapOrNull(): Snap? =
                mutex.withLock {
                    val w = wallet ?: return@withLock null
                    val id = _loadedWalletId.value.orEmpty()
                    if (id.isBlank()) return@withLock null
                    Snap(
                        wallet = w,
                        onchain = onchainWallet,
                        walletId = id,
                        generation = loadGeneration.get(),
                    )
                }
            var snapshot = currentSnapOrNull()
            if (snapshot == null) {
                _lifecycleState.value =
                    ArkLifecycleState.Error(localizedString(R.string.ark_error_wallet_not_loaded))
                _events.tryEmit(
                    ArkEvent.MailboxRecoveryFailed(
                        message = localizedString(R.string.ark_error_wallet_not_loaded),
                        supported = false,
                    ),
                )
                return@withContext
            }
            _lifecycleState.value = ArkLifecycleState.InProgress
            try {
                var recovery =
                    runMailboxRecoverySteps(
                        w = snapshot.wallet,
                        onchain = snapshot.onchain,
                    )
                val needsForcedMailbox =
                    recovery == null &&
                        (
                            lastOpenExpectedMailbox ||
                                ArkMailboxRecoveryPolicy.shouldWipeForMailboxRescan(
                                    hasReusableDb =
                                        hasReusableBarkDb(sessionDataDirForWallet(snapshot.walletId)),
                                    hasScannedMarker =
                                        hasMailboxScannedMarker(sessionDataDirForWallet(snapshot.walletId)),
                                    cachedHasFunds = cachedArkHasFunds(snapshot.walletId),
                                )
                        )
                if (needsForcedMailbox) {
                    SecureLog.w(TAG, "Ark mailbox report missing — reopening with mailbox scan")
                    loadWallet(snapshot.walletId, forceMailbox = true)
                    snapshot = currentSnapOrNull()
                    if (snapshot == null) {
                        throw Exception(localizedString(R.string.ark_error_wallet_not_loaded))
                    }
                    recovery =
                        runMailboxRecoverySteps(
                            w = snapshot.wallet,
                            onchain = snapshot.onchain,
                        )
                }
                if (snapshot.generation != loadGeneration.get()) return@withContext
                hydrateFromAspOutsideLock(
                    reason = "mailbox-full-sync",
                    expectedWallet = snapshot.wallet,
                    onchain = snapshot.onchain,
                    generation = snapshot.generation,
                    walletId = snapshot.walletId,
                    attemptBoard = false,
                    indicateSync = true,
                )
                maybeMarkMailboxScanned(recovery)
                val recoveredCount = recovery?.recovered?.vtxoIds?.size ?: 0
                val notApplied =
                    ArkMailboxRecoveryPolicy.recoveredButNotApplied(
                        recoveredCount = recoveredCount,
                        liveSpendableSats = _arkState.value.spendableSats,
                        liveVtxoCount = _arkState.value.vtxos.size,
                    )
                val success =
                    !notApplied &&
                        ArkMailboxRecoveryPolicy.isSuccessfulMailboxReport(
                            reportPresent = recovery != null,
                            isComplete = recovery?.isComplete == true,
                            scanWasExpected = lastOpenExpectedMailbox || needsForcedMailbox,
                        )
                val detail =
                    when {
                        notApplied ->
                            localizedString(R.string.ark_mailbox_recovery_not_applied)
                        recovery != null -> recovery.toLocalizedSummary()
                        !success -> localizedString(R.string.ark_mailbox_recovery_no_report)
                        else -> localizedString(R.string.ark_mailbox_recovery_completed)
                    }
                if (!success) {
                    _lifecycleState.value = ArkLifecycleState.Error(detail)
                    _events.tryEmit(
                        ArkEvent.MailboxRecoveryFailed(
                            message = detail,
                            supported = true,
                        ),
                    )
                } else {
                    _lifecycleState.value = ArkLifecycleState.Completed(detail = detail)
                    _events.tryEmit(
                        ArkEvent.MailboxRecoveryCompleted(
                            supported = true,
                            detail = detail,
                        ),
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val msg = publicError(e)
                _lifecycleState.value = ArkLifecycleState.Error(msg)
                _events.tryEmit(
                    ArkEvent.MailboxRecoveryFailed(
                        message = msg,
                        supported = true,
                    ),
                )
            }
        }

    fun isMailboxRecoverySupported(): Boolean = wallet != null

    private fun RecoveryReport.toLocalizedSummary(): String =
        localizedString(
            R.string.ark_mailbox_recovery_report_format,
            recovered.vtxoIds.size,
            skipped.vtxoIds.size,
            failed.vtxoIds.size,
            foreign.vtxoIds.size,
            exited.vtxoIds.size,
        )

    /**
     * Open-time seed-mailbox report + failed-id retry. Bark only produces a report on the
     * open that creates the local DB; subsequent opens return null — ASP hydrate still runs.
     */
    private suspend fun runMailboxRecoverySteps(
        w: Wallet,
        onchain: OnchainWallet?,
    ): RecoveryReport? {
        runCatching { w.runDaemon() }
        var recovery = runCatching { w.recoveryReport() }.getOrNull()
        val failedIds =
            recovery
                ?.failed
                ?.vtxoIds
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                .orEmpty()
        if (failedIds.isNotEmpty()) {
            runCatching { w.recoverVtxos(failedIds) }
                .onSuccess { recovery = it }
                .onFailure {
                    SecureLog.w(TAG, "Ark recoverVtxos retry failed: ${publicError(it)}")
                }
        }
        // Keep onchain reference warm; full ASP path continues in hydrateFromAspOutsideLock.
        runCatching { onchain?.sync() }
        return recovery
    }

    private fun maybeMarkMailboxScanned(recovery: RecoveryReport?) {
        val mark =
            ArkMailboxRecoveryPolicy.shouldMarkMailboxScanned(
                reportPresent = recovery != null,
                recoveredCount = recovery?.recovered?.vtxoIds?.size ?: 0,
                liveSpendableSats = _arkState.value.spendableSats,
                liveVtxoCount = _arkState.value.vtxos.size,
            )
        if (mark) markMailboxScanned(sessionDataDir)
    }

    /**
     * Full ASP/mailbox hydrate after attach. Always runs — local Bark DB and SecureStorage
     * cache are paint-only. External arkdb backups are disaster restore only and never skip
     * this path. Network work is outside [mutex]; publish takes the lock briefly.
     */
    private fun schedulePostOpenSync(
        generation: Long,
        walletId: String,
        openedWallet: Wallet,
    ) {
        postOpenSyncJob?.cancel()
        val onchainSnapshot = onchainWallet
        postOpenSyncJob =
            eventScope.launch {
                val self = coroutineContext[Job]
                try {
                    if (generation != loadGeneration.get()) return@launch
                    val recovery = runMailboxRecoverySteps(openedWallet, onchainSnapshot)
                    if (generation != loadGeneration.get()) return@launch
                    hydrateFromAspOutsideLock(
                        reason = "post-open",
                        expectedWallet = openedWallet,
                        onchain = onchainSnapshot,
                        generation = generation,
                        walletId = walletId,
                        attemptBoard = false,
                        indicateSync = false,
                    )
                    maybeMarkMailboxScanned(recovery)
                    // Opt-in auto-board only (default off).
                    if (
                        onchainSnapshot != null &&
                            generation == loadGeneration.get() &&
                            secureStorage.isArkAutoBoardEnabled()
                    ) {
                        scheduleDeferredBoardAttempts()
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    SecureLog.w(TAG, "Ark post-open ASP hydrate failed: ${publicError(e)}")
                } finally {
                    if (postOpenSyncJob === self) {
                        postOpenSyncJob = null
                    }
                }
            }
    }

    /**
     * Source of truth for normal operation: ASP via refreshServer + sync (+ boards/exits/LN).
     * Local SQLite is updated as a working cache; SecureStorage snapshot is paint-only.
     * Bails when [generation] is superseded (import/unload).
     */
    private suspend fun hydrateFromAspOutsideLock(
        reason: String,
        expectedWallet: Wallet,
        onchain: OnchainWallet?,
        generation: Long,
        walletId: String,
        attemptBoard: Boolean,
        indicateSync: Boolean = true,
    ) {
        if (generation != loadGeneration.get()) return
        SecureLog.w(TAG, "Ark ASP hydrate ($reason) for $walletId")
        if (indicateSync) beginSyncSpinner()
        try {
            runCatching { expectedWallet.runDaemon() }
            var lastError: String? = null
            var networkOk = false
        // Fresh enable is usually empty; one refreshServer+sync is enough. Retry only
        // when the first pass failed or a known-nonempty wallet still looks empty.
        val maxAttempts =
            if (
                reason == "post-open" &&
                    !lastOpenExpectedMailbox &&
                    isArkStateVisiblyEmptyForWallet(walletId)
            ) {
                1
            } else {
                2
            }
        repeat(maxAttempts) { attempt ->
            if (generation != loadGeneration.get()) return
            val refreshOk =
                runCatching { expectedWallet.refreshServer() }
                    .onFailure {
                        lastError = publicError(it)
                        SecureLog.w(TAG, "Ark hydrate refreshServer[$attempt]: $lastError")
                    }
                    .isSuccess
            if (generation != loadGeneration.get()) return
            val syncOk =
                runCatching { expectedWallet.sync() }
                    .onFailure {
                        lastError = publicError(it)
                        SecureLog.w(TAG, "Ark hydrate sync[$attempt]: $lastError")
                    }
                    .isSuccess
            if (refreshOk || syncOk) networkOk = true
            if (generation != loadGeneration.get()) return
            runCatching { onchain?.sync() }
            runCatching { expectedWallet.progressPendingRounds() }
            runCatching { expectedWallet.tryClaimAllLightningReceives(wait = false) }
            runCatching { expectedWallet.syncPendingBoards() }
            runCatching { expectedWallet.syncExits() }
            runCatching { expectedWallet.syncForceExitedVtxos() }
            mutex.withLock {
                if (generation != loadGeneration.get() || wallet !== expectedWallet) return@withLock
                if (attemptBoard && _isConnected.value && secureStorage.isArkAutoBoardEnabled()) {
                    runCatching { boardPendingOnchainFundsLocked(force = false) }
                }
                refreshStateLocked(sync = false, attemptBoard = false)
                if (networkOk) {
                    markAspHydratedLocked(walletId)
                    SecureLog.w(
                        TAG,
                        "Ark hydrate ok ($reason#$attempt) " +
                            "spendable=${_arkState.value.spendableSats} " +
                            "vtxos=${_arkState.value.vtxos.size} " +
                            "movements=${_arkState.value.movements.size} " +
                            "mailbox=${runCatching { expectedWallet.mailboxIdentifier() }.getOrNull()}",
                    )
                }
            }
            if (networkOk && !isArkStateVisiblyEmptyForWallet(walletId)) return
            if (attempt == 0 && maxAttempts > 1) delay(750L)
        }
        if (generation != loadGeneration.get()) return
        // Final publish even if still empty (ASP may legitimately have zero balance).
        mutex.withLock {
            if (generation != loadGeneration.get() || wallet !== expectedWallet) return@withLock
            if (!networkOk) {
                refreshStateLocked(sync = false, attemptBoard = false)
                _arkState.value =
                    _arkState.value.copy(
                        isSyncing = isSyncSpinnerHeld(),
                        aspHydrated = false,
                        error = lastError ?: _arkState.value.error,
                    )
            } else if (aspHydratedWalletId != walletId) {
                markAspHydratedLocked(walletId)
            }
            SecureLog.w(
                TAG,
                "Ark hydrate finished ($reason) empty=${isArkStateVisiblyEmptyLocked()} " +
                    "hydrated=${aspHydratedWalletId == walletId} " +
                    "spendable=${_arkState.value.spendableSats} " +
                    "server=${secureStorage.getArkServerAddress()} " +
                    "mailbox=${runCatching { expectedWallet.mailboxIdentifier() }.getOrNull()}",
            )
        }
        } finally {
            if (indicateSync) endSyncSpinner()
        }
    }

    private fun beginSyncSpinner() {
        syncSpinner.begin()
        if (!_arkState.value.isSyncing) {
            _arkState.value = _arkState.value.copy(isSyncing = true)
        }
        syncSpinnerTimeoutJob?.cancel()
        syncSpinnerTimeoutJob =
            eventScope.launch {
                delay(ARK_SYNC_SPINNER_MAX_MS)
                forceStopSyncSpinner()
            }
    }

    private fun endSyncSpinner() {
        if (syncSpinner.end() && _arkState.value.isSyncing) {
            _arkState.value = _arkState.value.copy(isSyncing = false)
        }
        if (!syncSpinner.isHeld()) {
            syncSpinnerTimeoutJob?.cancel()
            syncSpinnerTimeoutJob = null
        }
    }

    private fun forceStopSyncSpinner() {
        syncSpinner.reset()
        syncSpinnerTimeoutJob?.cancel()
        syncSpinnerTimeoutJob = null
        if (_arkState.value.isSyncing) {
            _arkState.value = _arkState.value.copy(isSyncing = false)
        }
    }

    private fun isSyncSpinnerHeld(): Boolean = syncSpinner.isHeld()

    private fun markAspHydratedLocked(walletId: String) {
        if (walletId.isBlank() || _loadedWalletId.value != walletId) return
        aspHydratedWalletId = walletId
        _arkState.value =
            _arkState.value.copy(
                isSyncing = isSyncSpinnerHeld(),
                isConnecting = false,
                isConnected = true,
                aspHydrated = true,
                error = null,
            )
    }

    private fun isArkStateVisiblyEmptyForWallet(walletId: String): Boolean {
        val state = _arkState.value
        if (state.walletId != walletId) return true
        return state.spendableSats == 0L &&
            state.pendingInRoundSats == 0L &&
            state.pendingBoardSats == 0L &&
            state.pendingExitSats == 0L &&
            state.pendingLightningSendSats == 0L &&
            state.claimableLightningReceiveSats == 0L &&
            state.movements.isEmpty() &&
            state.vtxos.isEmpty() &&
            !state.hasInboundDeposit
    }

    /**
     * Snapshot of Ark fund-risk for wallet-delete UI.
     * Uses live state when loaded, else the SecureStorage cache.
     */
    data class ArkDeleteRisk(
        val hasActivity: Boolean,
        val totalSats: Long,
        val hasPendingExits: Boolean,
        val hasClaimableExits: Boolean,
        val isBackupProtected: Boolean,
    ) {
        val blocksDelete: Boolean
            get() = hasPendingExits || hasClaimableExits

        val warnsDelete: Boolean
            get() = hasActivity && !isBackupProtected
    }

    fun assessDeleteRisk(walletId: String): ArkDeleteRisk {
        if (walletId.isBlank()) {
            return ArkDeleteRisk(
                hasActivity = false,
                totalSats = 0L,
                hasPendingExits = false,
                hasClaimableExits = false,
                isBackupProtected = false,
            )
        }
        val state =
            _arkState.value.takeIf { it.walletId == walletId }
                ?: secureStorage.getArkWalletStateCache(walletId)
        val total = state?.totalSats ?: 0L
        val pending = state?.hasPendingExits == true || (state?.exitVtxos?.isNotEmpty() == true)
        val claimable = state?.hasClaimableExits == true || (state?.claimableExitVtxos?.isNotEmpty() == true)
        val activity = state?.hasVtxoActivity == true || total > 0L || pending || claimable
        return ArkDeleteRisk(
            hasActivity = activity,
            totalSats = total,
            hasPendingExits = pending,
            hasClaimableExits = claimable,
            isBackupProtected = secureStorage.isArkDbBackupProtected(walletId),
        )
    }

    suspend fun deleteWalletData(walletId: String) =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                if (_loadedWalletId.value == walletId) {
                    unloadLocked()
                }
                if (pendingImportWalletId == walletId) {
                    deleteSessionDataDir(pendingImportSessionDir)
                    pendingImportSessionDir = null
                    pendingImportWalletId = null
                }
            }
            // Scrub any residue (legacy durable + stable/orphan session dirs for this wallet).
            runCatching {
                File(legacyDurableArkRoot(), walletId).takeIf { it.exists() }?.deleteRecursively()
            }
            runCatching {
                deleteSessionDataDir(sessionDataDirForWallet(walletId))
            }
            runCatching {
                arkSessionRootDir()
                    .listFiles()
                    ?.filter { it.isDirectory && it.name.startsWith("$walletId-") }
                    ?.forEach { it.deleteRecursively() }
            }
            secureStorage.clearArkWalletStateCache(walletId)
            // External auto-backup folder is user-owned; leave it on wallet delete.
            secureStorage.setArkEnabledForWallet(walletId, false)
        }

    /**
     * Best-effort unload + delete of all Ark data dirs for auto-wipe.
     * Does not hang forever on a stuck native call: tries mutex with a short wait,
     * then deletes files regardless so wipe cannot leave spendable DB on disk.
     */
    suspend fun prepareForFullWipe() =
        withContext(Dispatchers.IO) {
            val unloaded =
                withTimeoutOrNull(FULL_WIPE_UNLOAD_TIMEOUT_MS) {
                    mutex.withLock { unloadLocked() }
                    true
                } ?: false
            if (!unloaded) {
                SecureLog.w(TAG, "Ark wipe unload timed out; forcing handle drop + delete")
                // Drop handles without waiting on native close so file delete can proceed.
                runCatching {
                    wallet = null
                    onchainWallet = null
                    _loadedWalletId.value = null
                    sessionDataDir = null
                    sessionWalletId = null
                    pendingImportSessionDir = null
                    pendingImportWalletId = null
                    aspHydratedWalletId = null
                }
            }
            runCatching {
                legacyDurableArkRoot().takeIf { it.exists() }?.deleteRecursively()
            }.onFailure { SecureLog.w(TAG, "Ark wipe legacy dir delete failed") }
            runCatching {
                arkSessionRootDir().takeIf { it.exists() }?.deleteRecursively()
            }.onFailure { SecureLog.w(TAG, "Ark wipe session dir delete failed") }
            // Legacy in-app auto-backup dir (pre external-only); wipe if still present.
            runCatching {
                File(context.filesDir, LEGACY_AUTO_BACKUP_DIR).takeIf { it.exists() }?.deleteRecursively()
            }
        }

    suspend fun receive(
        kind: ArkReceiveKind,
        amountSats: Long? = null,
        description: String? = null,
        forceNew: Boolean = false,
    ) =
        withContext(Dispatchers.IO) {
            // Paint cached address before waiting on [mutex] (refresh/Esplora can hold it).
            if (!forceNew) {
                paintCachedReceiveKindUnlocked(kind)
                val painted =
                    (_receiveState.value as? ArkReceiveState.Ready)
                        ?.takeIf { it.kind == kind && it.paymentRequest.isNotBlank() }
                // Cache hit is enough for address tabs — don't block on refresh/board.
                if (
                    painted != null &&
                        (kind == ArkReceiveKind.ARK_ADDRESS || kind == ArkReceiveKind.BITCOIN_ADDRESS)
                ) {
                    return@withContext
                }
            }
            mutex.withLock {
                receiveLocked(kind, amountSats, description, forceNew)
            }
        }

    /**
     * Paint last Ark/on-chain receive address immediately (prefs / in-memory), even before
     * Bark is open. BOLT11 still requires a live wallet.
     */
    fun primeReceiveFromCache(walletId: String?) {
        if (walletId.isNullOrBlank()) return
        // Best-effort without blocking UI; load path also primes under mutex.
        primeCachedReceiveStateUnlocked(walletId)
    }

    /**
     * Immediately show the cached address for [kind] without waiting for Bark / mutex.
     * Used when switching Receive tabs so BTC is not stuck behind history refresh.
     */
    fun primeReceiveKindFromCache(
        walletId: String?,
        kind: ArkReceiveKind,
    ) {
        if (walletId.isNullOrBlank()) return
        paintCachedReceiveKindUnlocked(kind, walletId)
    }

    /** Cache-only receive paint; safe without [mutex]. */
    private fun paintCachedReceiveKindUnlocked(
        kind: ArkReceiveKind,
        walletIdOverride: String? = null,
    ) {
        val walletId =
            walletIdOverride
                ?: _loadedWalletId.value
                ?: _arkState.value.walletId
                ?: return
        when (kind) {
            ArkReceiveKind.ARK_ADDRESS -> {
                val cached =
                    (
                        _arkState.value.currentAddress?.takeIf { it.isNotBlank() }
                            ?: secureStorage.getArkReceiveAddress(walletId)?.takeIf { it.isNotBlank() }
                    )?.takeUnless { isAddressUsed(it, walletId = walletId) }
                        ?: return
                val current = _receiveState.value
                if (current is ArkReceiveState.Paid) return
                if (
                    current is ArkReceiveState.Ready &&
                        current.kind == ArkReceiveKind.ARK_ADDRESS &&
                        current.paymentRequest.equals(cached, ignoreCase = true)
                ) {
                    return
                }
                // Don't clobber an open LN invoice.
                if (
                    current is ArkReceiveState.Ready &&
                        current.kind == ArkReceiveKind.BOLT11_INVOICE
                ) {
                    return
                }
                _receiveState.value =
                    ArkReceiveState.Ready(
                        kind = ArkReceiveKind.ARK_ADDRESS,
                        paymentRequest = cached,
                    )
            }
            ArkReceiveKind.BITCOIN_ADDRESS -> {
                val cached =
                    secureStorage
                        .getArkOnchainDepositAddress(walletId)
                        ?.takeIf { it.isNotBlank() }
                        ?.takeUnless { isAddressUsed(it, walletId = walletId) }
                        ?: return
                val current = _receiveState.value
                if (current is ArkReceiveState.Paid) return
                if (
                    current is ArkReceiveState.Ready &&
                        current.kind == ArkReceiveKind.BITCOIN_ADDRESS &&
                        current.paymentRequest.equals(cached, ignoreCase = true)
                ) {
                    return
                }
                if (
                    current is ArkReceiveState.Ready &&
                        current.kind == ArkReceiveKind.BOLT11_INVOICE
                ) {
                    return
                }
                _receiveState.value =
                    ArkReceiveState.Ready(
                        kind = ArkReceiveKind.BITCOIN_ADDRESS,
                        paymentRequest = cached,
                    )
            }
            ArkReceiveKind.BOLT11_INVOICE -> Unit
        }
    }

    /** Must hold [mutex]. */
    private suspend fun receiveLocked(
        kind: ArkReceiveKind,
        amountSats: Long? = null,
        description: String? = null,
        forceNew: Boolean = false,
    ) {
        val w = wallet
        val walletId = _loadedWalletId.value ?: _arkState.value.walletId
        // Cache-first paint before Bark is open (addresses only).
        if (w == null) {
            if (!forceNew) {
                when (kind) {
                    ArkReceiveKind.ARK_ADDRESS -> {
                        val cached =
                            (
                                _arkState.value.currentAddress?.takeIf { it.isNotBlank() }
                                    ?: walletId?.let { secureStorage.getArkReceiveAddress(it) }
                            )?.takeUnless { isAddressUsed(it, walletId = walletId) }
                        if (!cached.isNullOrBlank()) {
                            _receiveState.value =
                                ArkReceiveState.Ready(
                                    kind = ArkReceiveKind.ARK_ADDRESS,
                                    paymentRequest = cached,
                                )
                            return
                        }
                    }
                    ArkReceiveKind.BITCOIN_ADDRESS -> {
                        val cached =
                            walletId
                                ?.let { secureStorage.getArkOnchainDepositAddress(it) }
                                ?.takeUnless { isAddressUsed(it, walletId = walletId) }
                        if (!cached.isNullOrBlank()) {
                            _receiveState.value =
                                ArkReceiveState.Ready(
                                    kind = ArkReceiveKind.BITCOIN_ADDRESS,
                                    paymentRequest = cached,
                                )
                            return
                        }
                    }
                    ArkReceiveKind.BOLT11_INVOICE -> Unit
                }
            }
            // Wait for Bark open — do not flash a hard error while connecting.
            if (kind == ArkReceiveKind.BOLT11_INVOICE) {
                _receiveState.value =
                    ArkReceiveState.Error(localizedString(R.string.ark_error_wallet_not_loaded))
            } else if (_receiveState.value !is ArkReceiveState.Ready) {
                _receiveState.value = ArkReceiveState.Loading
            }
            return
        }
        // Reuse last unused address unless user requests new or history shows it used.
        if (!forceNew) {
            when (kind) {
                ArkReceiveKind.ARK_ADDRESS -> {
                    val unused = resolveUnusedArkAddressLocked(w, walletId, forceNew = false)
                    if (!unused.isNullOrBlank()) {
                        _receiveState.value =
                            ArkReceiveState.Ready(
                                kind = ArkReceiveKind.ARK_ADDRESS,
                                paymentRequest = unused,
                            )
                        _arkState.value = _arkState.value.copy(currentAddress = unused)
                        return
                    }
                }
                ArkReceiveKind.BITCOIN_ADDRESS -> {
                    val unused =
                        resolveUnusedBitcoinDepositAddressLocked(forceNew = false)
                    if (!unused.isNullOrBlank()) {
                        _receiveState.value =
                            ArkReceiveState.Ready(
                                kind = ArkReceiveKind.BITCOIN_ADDRESS,
                                paymentRequest = unused,
                            )
                        return
                    }
                }
                ArkReceiveKind.BOLT11_INVOICE -> Unit
            }
        }
        val previousReady = _receiveState.value as? ArkReceiveState.Ready
        if (forceNew && walletId != null) {
            when (kind) {
                ArkReceiveKind.ARK_ADDRESS -> {
                    previousReady
                        ?.takeIf { it.kind == ArkReceiveKind.ARK_ADDRESS }
                        ?.paymentRequest
                        ?.trim()
                        ?.takeIf { it.isNotEmpty() }
                        ?.let { secureStorage.markArkReceiveAddressUsed(walletId, it) }
                    _arkState.value.currentAddress
                        ?.trim()
                        ?.takeIf { it.isNotEmpty() }
                        ?.let { secureStorage.markArkReceiveAddressUsed(walletId, it) }
                }
                ArkReceiveKind.BITCOIN_ADDRESS -> {
                    previousReady
                        ?.takeIf { it.kind == ArkReceiveKind.BITCOIN_ADDRESS }
                        ?.paymentRequest
                        ?.trim()
                        ?.takeIf { it.isNotEmpty() }
                        ?.let { secureStorage.markArkOnchainDepositAddressUsed(walletId, it) }
                    secureStorage
                        .getArkOnchainDepositAddress(walletId)
                        ?.trim()
                        ?.takeIf { it.isNotEmpty() }
                        ?.let { secureStorage.markArkOnchainDepositAddressUsed(walletId, it) }
                }
                ArkReceiveKind.BOLT11_INVOICE -> Unit
            }
        }
        _receiveState.value = ArkReceiveState.Loading
        try {
            when (kind) {
                ArkReceiveKind.ARK_ADDRESS -> {
                    val address =
                        resolveUnusedArkAddressLocked(w, walletId, forceNew = true)
                            ?: w.newAddress()
                    if (walletId != null) {
                        secureStorage.setArkReceiveAddress(walletId, address)
                    }
                    _receiveState.value =
                        ArkReceiveState.Ready(
                            kind = ArkReceiveKind.ARK_ADDRESS,
                            paymentRequest = address,
                        )
                    _arkState.value = _arkState.value.copy(currentAddress = address)
                }
                ArkReceiveKind.BOLT11_INVOICE -> {
                    val sats = amountSats ?: 0L
                    if (sats <= 0L) {
                        _receiveState.value =
                            ArkReceiveState.Error(
                                localizedString(R.string.ark_error_enter_amount_lightning_invoice),
                            )
                        return
                    }
                    val desc = description?.takeIf { it.isNotBlank() }
                    val arkAddress =
                        resolveUnusedArkAddressLocked(w, walletId, forceNew = false)
                            ?: runCatching { w.newAddress() }.getOrNull()
                    val feeSats =
                        runCatching {
                            w.estimateLightningReceiveFee(sats.toULong()).feeSats.toLong()
                        }.getOrDefault(0L)
                    // Bind claim to this wallet's Ark address so delivery can complete offline.
                    val invoice =
                        if (!arkAddress.isNullOrBlank()) {
                            w.bolt11InvoiceForAddress(
                                amountSats = sats.toULong(),
                                claimDestination = arkAddress,
                                description = desc,
                                token = null,
                            )
                        } else {
                            w.bolt11Invoice(
                                amountSats = sats.toULong(),
                                description = desc,
                                token = null,
                            )
                        }
                    receiveLightningBaselineSats = _arkState.value.claimableLightningReceiveSats
                    _receiveState.value =
                        ArkReceiveState.Ready(
                            kind = ArkReceiveKind.BOLT11_INVOICE,
                            paymentRequest = invoice.invoice,
                            amountSats = invoice.amountSats.toLong(),
                            feeSats = feeSats,
                            paymentHash = invoice.paymentHash,
                        )
                    scheduleLightningReceivePaidWatch()
                }
                ArkReceiveKind.BITCOIN_ADDRESS -> {
                    val address =
                        resolveUnusedBitcoinDepositAddressLocked(forceNew = true)
                            ?: throw IllegalStateException(
                                localizedString(R.string.ark_error_onchain_wallet_unavailable),
                            )
                    if (walletId != null) {
                        secureStorage.setArkOnchainDepositAddress(walletId, address)
                    }
                    _receiveState.value =
                        ArkReceiveState.Ready(
                            kind = ArkReceiveKind.BITCOIN_ADDRESS,
                            paymentRequest = address,
                        )
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _receiveState.value = ArkReceiveState.Error(publicError(e))
        }
    }

    private suspend fun checkLightningReceivePaidLocked() {
        val current = _receiveState.value
        if (current !is ArkReceiveState.Ready || current.kind != ArkReceiveKind.BOLT11_INVOICE) return
        val w = wallet
        val hash = current.paymentHash?.trim().orEmpty()
        val paidByHash =
            if (w != null && hash.isNotEmpty()) {
                runCatching { w.isInvoicePaid(hash) }.getOrDefault(false)
            } else {
                false
            }
        val paidByBalance =
            receiveLightningBaselineSats >= 0L &&
                _arkState.value.claimableLightningReceiveSats > receiveLightningBaselineSats
        if (!paidByHash && !paidByBalance) return
        markLightningReceivePaidLocked(current)
    }

    private fun markLightningReceivePaidLocked(current: ArkReceiveState.Ready) {
        receiveLightningBaselineSats = -1L
        lightningReceiveWatchJob?.cancel()
        lightningReceiveWatchJob = null
        _receiveState.value =
            ArkReceiveState.Paid(
                kind = ArkReceiveKind.BOLT11_INVOICE,
                amountSats = current.amountSats ?: 0L,
                paymentRequest = current.paymentRequest,
            )
    }

    /** Poll invoice paid status while a BOLT11 receive is open (notifications alone can lag). */
    private fun scheduleLightningReceivePaidWatch() {
        lightningReceiveWatchJob?.cancel()
        lightningReceiveWatchJob =
            eventScope.launch {
                repeat(LIGHTNING_RECEIVE_WATCH_ATTEMPTS) {
                    delay(LIGHTNING_RECEIVE_WATCH_INTERVAL_MS)
                    val stillOpen =
                        mutex.withLock {
                            val current = _receiveState.value
                            if (current !is ArkReceiveState.Ready ||
                                current.kind != ArkReceiveKind.BOLT11_INVOICE
                            ) {
                                return@withLock false
                            }
                            checkLightningReceivePaidLocked()
                            _receiveState.value is ArkReceiveState.Ready &&
                                (_receiveState.value as? ArkReceiveState.Ready)?.kind ==
                                ArkReceiveKind.BOLT11_INVOICE
                        }
                    if (!stillOpen) return@launch
                }
            }
    }

    fun resetReceiveState() {
        lightningReceiveWatchJob?.cancel()
        lightningReceiveWatchJob = null
        receiveLightningBaselineSats = -1L
        _receiveState.value = ArkReceiveState.Idle
    }

    private var preparedMultiItems: List<ArkSendState.MultiPreview.MultiItem> = emptyList()
    private var preparedMultiLabel: String? = null

    fun resetSendState() {
        preparedDestination = null
        preparedAmountSats = null
        preparedMethod = null
        preparedPayKind = null
        preparedUseAllFunds = false
        preparedLabel = null
        preparedMultiItems = emptyList()
        preparedMultiLabel = null
        _sendState.value = ArkSendState.Idle
    }

    fun resetTransferState() {
        _transferState.value = ArkTransferState.Idle
    }

    suspend fun prepareSend(
        destination: String,
        amountSats: Long?,
        useAllFunds: Boolean = false,
        label: String? = null,
    ) =
        withContext(Dispatchers.IO) {
            // Paint Preparing immediately so review dialog is not stuck on Idle while
            // waiting for heartbeat/maintenance mutex or a slow fee quote.
            _sendState.value = ArkSendState.Preparing
            try {
                val w =
                    mutex.withLock { wallet }
                        ?: run {
                            _sendState.value =
                                ArkSendState.Error(localizedString(R.string.ark_error_wallet_not_loaded))
                            return@withContext
                        }
                val resolved =
                    resolveArkDestination(destination)
                        ?: run {
                            _sendState.value =
                                ArkSendState.Error(
                                    localizedString(R.string.ark_error_unsupported_destination),
                                )
                            return@withContext
                        }
                val spendable = runCatching { w.balance().spendableSats.toLong() }.getOrDefault(0L)
                val quote =
                    withTimeoutOrNull(ARK_SEND_PREVIEW_TIMEOUT_MS) {
                        buildSendPreviewQuote(
                            w = w,
                            resolved = resolved,
                            amountSats = amountSats,
                            useAllFunds = useAllFunds,
                            spendable = spendable,
                        )
                    }
                        ?: run {
                            _sendState.value =
                                ArkSendState.Error(localizedString(R.string.ark_error_send_preview_timeout))
                            return@withContext
                        }
                when (quote) {
                    is SendPreviewQuote.Failure -> {
                        _sendState.value = ArkSendState.Error(quote.message)
                        return@withContext
                    }
                    is SendPreviewQuote.Success -> {
                        // Only commit prepared state if this wallet handle is still live.
                        mutex.withLock {
                            if (wallet !== w) {
                                _sendState.value =
                                    ArkSendState.Error(localizedString(R.string.ark_error_wallet_not_loaded))
                                return@withLock
                            }
                            preparedDestination = resolved.payTarget
                            preparedAmountSats = quote.payAmount
                            preparedMethod = quote.method
                            preparedPayKind = resolved.kind
                            preparedUseAllFunds = useAllFunds
                            preparedLabel = label?.trim()?.takeIf { it.isNotBlank() }
                            _sendState.value =
                                ArkSendState.Preview(
                                    destination = resolved.payTarget,
                                    amountSats = quote.previewAmount,
                                    feeSats = quote.feeSats,
                                    method = quote.method,
                                    netAmountSats = quote.net,
                                    useAllFunds = useAllFunds,
                                    label = preparedLabel,
                                )
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _sendState.value = ArkSendState.Error(publicError(e))
            }
        }

    private sealed class SendPreviewQuote {
        data class Success(
            val method: String,
            val feeSats: Long?,
            val previewAmount: Long?,
            val net: Long?,
            val payAmount: Long?,
        ) : SendPreviewQuote()

        data class Failure(val message: String) : SendPreviewQuote()
    }

    /**
     * Fee quotes for send review. Must not hold [mutex] — Bark estimate FFI can block on ASP/network.
     */
    private suspend fun buildSendPreviewQuote(
        w: Wallet,
        resolved: ResolvedArkDestination,
        amountSats: Long?,
        useAllFunds: Boolean,
        spendable: Long,
    ): SendPreviewQuote {
        val method: String
        val feeSats: Long?
        val previewAmount: Long?
        val net: Long?
        val payAmount: Long?
        val gross: Long?

        when (resolved.kind) {
            ArkPayKind.ARKOOR -> {
                if (!w.validateArkoorAddress(resolved.payTarget)) {
                    return SendPreviewQuote.Failure(localizedString(R.string.ark_error_different_server))
                }
                val amount =
                    when {
                        useAllFunds -> {
                            // Fee is deducted from spendable; recipient gets net.
                            backlogMaxByBinarySearch(
                                maxBudget = spendable,
                                estimate = { candidate ->
                                    w.estimateArkoorPaymentFee(candidate.toULong())
                                },
                            )
                        }
                        else -> amountSats ?: resolved.fixedAmountSats
                    }
                if (amount == null || amount <= 0L) {
                    return SendPreviewQuote.Failure(localizedString(R.string.ark_enter_amount))
                }
                val estimate = w.estimateArkoorPaymentFee(amount.toULong())
                method = "Ark"
                feeSats = estimate.feeSats.toLong()
                previewAmount = amount
                net = estimate.netAmountSats.toLong()
                payAmount = amount
                gross = estimate.grossAmountSats.toLong()
                if (gross > spendable) {
                    return SendPreviewQuote.Failure(
                        localizedString(
                            R.string.ark_error_insufficient_need_fee_format,
                            gross.toString(),
                        ),
                    )
                }
            }
            ArkPayKind.BOLT11 -> {
                val amount = amountSats ?: resolved.fixedAmountSats
                if (amount == null || amount <= 0L) {
                    return SendPreviewQuote.Failure(localizedString(R.string.ark_enter_amount))
                }
                val fee = w.estimateLightningSendFee(amount.toULong()).feeSats.toLong()
                method = "Lightning"
                feeSats = fee
                previewAmount = amount
                net = amount
                payAmount = amount
                gross = amount + fee
                if (gross > spendable) {
                    return SendPreviewQuote.Failure(
                        localizedString(
                            R.string.ark_error_insufficient_need_fee_format,
                            gross.toString(),
                        ),
                    )
                }
            }
            ArkPayKind.BOLT12 -> {
                val amount = amountSats ?: resolved.fixedAmountSats
                if (amount == null || amount <= 0L) {
                    return SendPreviewQuote.Failure(localizedString(R.string.ark_enter_amount))
                }
                val fee =
                    runCatching {
                        w.estimateLightningSendFee(amount.toULong()).feeSats.toLong()
                    }.getOrNull()
                method = "BOLT12"
                feeSats = fee
                previewAmount = amount
                net = amount
                payAmount = amount
                gross = if (fee != null) amount + fee else amount
                if (gross > spendable) {
                    return SendPreviewQuote.Failure(
                        localizedString(
                            R.string.ark_error_insufficient_need_fee_format,
                            gross.toString(),
                        ),
                    )
                }
            }
            ArkPayKind.LN_ADDRESS, ArkPayKind.LNURL -> {
                val amount =
                    when {
                        useAllFunds -> {
                            backlogMaxByBinarySearch(
                                maxBudget = spendable,
                                estimate = { candidate ->
                                    w.estimateLightningSendFee(candidate.toULong())
                                },
                            )
                        }
                        else -> amountSats ?: resolved.fixedAmountSats
                    }
                if (amount == null || amount <= 0L) {
                    return SendPreviewQuote.Failure(localizedString(R.string.ark_enter_amount))
                }
                val estimate = w.estimateLightningSendFee(amount.toULong())
                method =
                    if (resolved.kind == ArkPayKind.LNURL) {
                        "LNURL"
                    } else {
                        "LN Address"
                    }
                feeSats = estimate.feeSats.toLong()
                previewAmount = amount
                net = estimate.netAmountSats.toLong()
                payAmount = amount
                gross = estimate.grossAmountSats.toLong()
                if (gross > spendable) {
                    return SendPreviewQuote.Failure(
                        localizedString(
                            R.string.ark_error_insufficient_need_fee_format,
                            gross.toString(),
                        ),
                    )
                }
            }
            ArkPayKind.ONCHAIN -> {
                if (SilentPayment.isSilentPaymentAddress(resolved.payTarget)) {
                    return SendPreviewQuote.Failure(
                        localizedString(R.string.ark_error_silent_payments),
                    )
                }
                val amount =
                    when {
                        useAllFunds -> {
                            backlogMaxByBinarySearch(
                                maxBudget = spendable,
                                estimate = { candidate ->
                                    w.estimateSendOnchainFee(
                                        resolved.payTarget,
                                        candidate.toULong(),
                                    )
                                },
                            )
                        }
                        else -> amountSats ?: resolved.fixedAmountSats
                    }
                if (amount == null || amount <= 0L) {
                    return SendPreviewQuote.Failure(localizedString(R.string.ark_enter_amount))
                }
                val estimate =
                    w.estimateSendOnchainFee(resolved.payTarget, amount.toULong())
                method = "On-chain"
                feeSats = estimate.feeSats.toLong()
                previewAmount = amount
                net = estimate.netAmountSats.toLong()
                payAmount = amount
                gross = estimate.grossAmountSats.toLong()
                if (gross > spendable) {
                    return SendPreviewQuote.Failure(
                        localizedString(
                            R.string.ark_error_insufficient_need_fee_format,
                            gross.toString(),
                        ),
                    )
                }
            }
        }
        return SendPreviewQuote.Success(
            method = method,
            feeSats = feeSats,
            previewAmount = previewAmount,
            net = net,
            payAmount = payAmount,
        )
    }

    private sealed class MultiSendPreviewQuote {
        data class Success(
            val items: List<ArkSendState.MultiPreview.MultiItem>,
            val totalAmountSats: Long,
            val totalFeeSats: Long,
        ) : MultiSendPreviewQuote()

        data class Failure(val message: String) : MultiSendPreviewQuote()
    }

    /**
     * Sequential multi-payment prepare for **Arkoor addresses only** (one Bark payment each).
     * [recipients] is address → amount sats; requires ≥2 entries.
     */
    suspend fun prepareSendMany(
        recipients: List<Pair<String, Long>>,
        label: String? = null,
    ) = withContext(Dispatchers.IO) {
        _sendState.value = ArkSendState.Preparing
        try {
            if (recipients.size < 2) {
                _sendState.value =
                    ArkSendState.Error(localizedString(R.string.send_multi_need_two))
                return@withContext
            }
            val w =
                mutex.withLock { wallet }
                    ?: run {
                        _sendState.value =
                            ArkSendState.Error(localizedString(R.string.ark_error_wallet_not_loaded))
                        return@withContext
                    }
            val quote =
                withTimeoutOrNull(ARK_SEND_PREVIEW_TIMEOUT_MS) {
                    val spendable = runCatching { w.balance().spendableSats.toLong() }.getOrDefault(0L)
                    val items = mutableListOf<ArkSendState.MultiPreview.MultiItem>()
                    var totalAmount = 0L
                    var totalFee = 0L
                    for ((rawDest, amount) in recipients) {
                        if (amount <= 0L) {
                            return@withTimeoutOrNull MultiSendPreviewQuote.Failure(
                                localizedString(R.string.ark_enter_amount),
                            )
                        }
                        val resolved =
                            resolveArkDestination(rawDest)
                                ?: return@withTimeoutOrNull MultiSendPreviewQuote.Failure(
                                    localizedString(R.string.ark_error_unsupported_destination),
                                )
                        if (resolved.kind != ArkPayKind.ARKOOR) {
                            return@withTimeoutOrNull MultiSendPreviewQuote.Failure(
                                localizedString(R.string.send_multi_ark_only),
                            )
                        }
                        if (!w.validateArkoorAddress(resolved.payTarget)) {
                            return@withTimeoutOrNull MultiSendPreviewQuote.Failure(
                                localizedString(R.string.ark_error_different_server),
                            )
                        }
                        val estimate = w.estimateArkoorPaymentFee(amount.toULong())
                        val fee = estimate.feeSats.toLong()
                        totalAmount += amount
                        totalFee += fee
                        if (totalAmount + totalFee > spendable) {
                            return@withTimeoutOrNull MultiSendPreviewQuote.Failure(
                                localizedString(
                                    R.string.ark_error_insufficient_need_fee_format,
                                    (totalAmount + totalFee).toString(),
                                ),
                            )
                        }
                        items.add(
                            ArkSendState.MultiPreview.MultiItem(
                                destination = resolved.payTarget,
                                amountSats = amount,
                                feeSats = fee,
                            ),
                        )
                    }
                    MultiSendPreviewQuote.Success(
                        items = items.toList(),
                        totalAmountSats = totalAmount,
                        totalFeeSats = totalFee,
                    )
                }
                    ?: run {
                        _sendState.value =
                            ArkSendState.Error(localizedString(R.string.ark_error_send_preview_timeout))
                        return@withContext
                    }
            when (quote) {
                is MultiSendPreviewQuote.Failure -> {
                    _sendState.value = ArkSendState.Error(quote.message)
                }
                is MultiSendPreviewQuote.Success -> {
                    mutex.withLock {
                        if (wallet !== w) {
                            _sendState.value =
                                ArkSendState.Error(localizedString(R.string.ark_error_wallet_not_loaded))
                            return@withLock
                        }
                        preparedMultiItems = quote.items
                        preparedMultiLabel = label
                        preparedDestination = null
                        preparedAmountSats = null
                        preparedPayKind = null
                        preparedUseAllFunds = false
                        preparedLabel = null
                        _sendState.value =
                            ArkSendState.MultiPreview(
                                items = quote.items,
                                totalAmountSats = quote.totalAmountSats,
                                totalFeeSats = quote.totalFeeSats,
                                label = label,
                            )
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _sendState.value = ArkSendState.Error(publicError(e))
        }
    }

    suspend fun sendPreparedMany() =
        withContext(Dispatchers.IO) {
            // Paint progress before mutex so UI leaves MultiPreview immediately.
            val initialTotal = preparedMultiItems.size
            if (initialTotal >= 2) {
                _sendState.value =
                    ArkSendState.MultiSending(completed = 0, total = initialTotal)
            }
            mutex.withLock {
                val w = wallet
                val items = preparedMultiItems
                if (w == null || items.size < 2) {
                    _sendState.value =
                        ArkSendState.Error(localizedString(R.string.ark_error_nothing_prepared))
                    return@withLock
                }
                var succeeded = 0
                var failed = 0
                var skipped = 0
                var lastError: String? = null
                val remaining = items.toMutableList()
                try {
                    while (remaining.isNotEmpty()) {
                        val index = succeeded + failed
                        val item = remaining.first()
                        _sendState.value =
                            ArkSendState.MultiSending(
                                completed = index,
                                total = items.size,
                            )
                        try {
                            w.sendArkoorPayment(item.destination, item.amountSats.toULong())
                            pendingSendDestination = item.destination
                            pendingSendAmountSats = item.amountSats
                            pendingSendKind = ArkPayKind.ARKOOR
                            val walletId = _loadedWalletId.value
                            runCatching {
                                refreshStateLocked(sync = true)
                                if (walletId != null) {
                                    attachPendingSendDestinationLocked(walletId)
                                    val label = preparedMultiLabel
                                    if (!label.isNullOrBlank()) {
                                        val labeled =
                                            _arkState.value.movements
                                                .asReversed()
                                                .firstOrNull { m ->
                                                    m.effectiveBalanceSats < 0L &&
                                                        m.sentToAddresses.any {
                                                            it.equals(item.destination, ignoreCase = true)
                                                        } &&
                                                        m.label.isNullOrBlank()
                                                }
                                        if (labeled != null) {
                                            saveMovementLabel(walletId, labeled.id, label)
                                        }
                                    }
                                }
                            }
                            remaining.removeAt(0)
                            succeeded++
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            failed = 1
                            skipped = remaining.size - 1
                            lastError = publicError(e)
                            // Keep unsent remainder (including the failed item) for retry.
                            preparedMultiItems = remaining.toList()
                            break
                        }
                    }
                    if (failed == 0) {
                        preparedMultiItems = emptyList()
                        preparedMultiLabel = null
                        _sendState.value =
                            ArkSendState.MultiSent(
                                succeeded = succeeded,
                                failed = 0,
                                detail = null,
                            )
                    } else {
                        // Remainder stays in preparedMultiItems for a retry of unsent only.
                        _sendState.value =
                            ArkSendState.MultiSent(
                                succeeded = succeeded,
                                failed = failed + skipped.coerceAtLeast(0),
                                detail =
                                    buildString {
                                        append(lastError.orEmpty())
                                        if (remaining.isNotEmpty()) {
                                            if (isNotEmpty()) append(" · ")
                                            append(
                                                localizedString(
                                                    R.string.ark_send_multi_remainder_format,
                                                    remaining.size,
                                                ),
                                            )
                                        }
                                    }.ifBlank { lastError },
                            )
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    clearPreparedSendStateLocked()
                    _sendState.value = ArkSendState.Error(publicError(e))
                }
            }
        }

    suspend fun sendPrepared() =
        withContext(Dispatchers.IO) {
            // Paint Sending before mutex wait so confirm dialog does not sit on Preview.
            _sendState.value = ArkSendState.Sending
            // Snapshot prepared fields under lock, then pay outside so heartbeat can proceed.
            data class PreparedPay(
                val wallet: Wallet,
                val dest: String,
                val kind: ArkPayKind,
                val amount: Long?,
                val comment: String?,
                val label: String?,
                val walletId: String?,
            )
            val prepared =
                mutex.withLock {
                    val w = wallet
                    val dest = preparedDestination
                    val kind = preparedPayKind
                    if (w == null || dest == null || kind == null) {
                        null
                    } else {
                        // Disarm re-entry before leaving the lock (double-tap / concurrent send).
                        val snap =
                            PreparedPay(
                                wallet = w,
                                dest = dest,
                                kind = kind,
                                amount = preparedAmountSats,
                                comment = preparedLabel,
                                label = preparedLabel,
                                walletId = _loadedWalletId.value,
                            )
                        clearPreparedSendStateLocked()
                        snap
                    }
                }
            if (prepared == null) {
                _sendState.value =
                    ArkSendState.Error(localizedString(R.string.ark_error_nothing_prepared))
                return@withContext
            }
            val w = prepared.wallet
            val dest = prepared.dest
            val kind = prepared.kind
            val amount = prepared.amount
            val comment = prepared.comment
            // True once a network pay call has been entered — timeouts after dispatch
            // must not invite a blind retry of the same payment.
            var paymentMayHaveCompleted = false
            try {
                val detail =
                    when (kind) {
                        ArkPayKind.ARKOOR -> {
                            val sats = amount ?: error(localizedString(R.string.ark_error_amount_required))
                            paymentMayHaveCompleted = true
                            w.sendArkoorPayment(dest, sats.toULong())
                            dest
                        }
                        ArkPayKind.BOLT11 -> {
                            paymentMayHaveCompleted = true
                            val status =
                                withTimeout(LIGHTNING_PAY_TIMEOUT_MS) {
                                    w.payLightningInvoice(
                                        invoice = dest,
                                        amountSats = amount?.toULong(),
                                        wait = true,
                                    )
                                }
                            lightningSendDetail(status)
                        }
                        ArkPayKind.BOLT12 -> {
                            paymentMayHaveCompleted = true
                            val status =
                                withTimeout(LIGHTNING_PAY_TIMEOUT_MS) {
                                    w.payLightningOffer(
                                        offer = dest,
                                        amountSats = amount?.toULong(),
                                        wait = true,
                                    )
                                }
                            lightningSendDetail(status)
                        }
                        ArkPayKind.LN_ADDRESS -> {
                            val sats = amount ?: error(localizedString(R.string.ark_error_amount_required))
                            paymentMayHaveCompleted = true
                            val status =
                                withTimeout(LIGHTNING_PAY_TIMEOUT_MS) {
                                    w.payLightningAddress(
                                        lightningAddress = dest,
                                        amountSats = sats.toULong(),
                                        comment = comment,
                                        wait = true,
                                    )
                                }
                            lightningSendDetail(status)
                        }
                        ArkPayKind.LNURL -> {
                            val sats = amount ?: error(localizedString(R.string.ark_error_amount_required))
                            paymentMayHaveCompleted = true
                            val status =
                                withTimeout(LIGHTNING_PAY_TIMEOUT_MS) {
                                    w.payLnurl(
                                        lnurl = dest,
                                        amountSats = sats.toULong(),
                                        comment = comment,
                                        wait = true,
                                    )
                                }
                            lightningSendDetail(status)
                        }
                        ArkPayKind.ONCHAIN -> {
                            val sats = amount ?: error(localizedString(R.string.ark_error_amount_required))
                            paymentMayHaveCompleted = true
                            w.sendOnchain(dest, sats.toULong())
                        }
                    }
                val walletId = prepared.walletId
                val label = prepared.label
                val sentKind = kind
                val sentDest = dest
                val sentAmount = amount
                mutex.withLock {
                    if (wallet !== w) return@withLock
                    // Hold until attach succeeds across refresh/notification races.
                    if (sentDest.isNotBlank() &&
                        (sentKind == ArkPayKind.ARKOOR || sentKind == ArkPayKind.ONCHAIN)
                    ) {
                        pendingSendDestination = sentDest
                        pendingSendAmountSats = sentAmount
                        pendingSendKind = sentKind
                    }
                    runCatching {
                        refreshStateLocked(sync = true)
                        if (walletId != null) {
                            attachPendingSendDestinationLocked(walletId)
                            if (!label.isNullOrBlank()) {
                                val labeled =
                                    _arkState.value.movements
                                        .asReversed()
                                        .firstOrNull { m ->
                                            m.effectiveBalanceSats < 0L &&
                                                m.sentToAddresses.any { it.equals(sentDest, ignoreCase = true) } &&
                                                m.label.isNullOrBlank()
                                        }
                                if (labeled != null) {
                                    saveMovementLabel(walletId, labeled.id, label)
                                }
                            }
                        }
                    }
                }
                _sendState.value =
                    ArkSendState.Sent(
                        detail =
                            detail?.takeIf { it.isNotBlank() }
                                ?: sentDest.takeIf {
                                    sentKind == ArkPayKind.ARKOOR || sentKind == ArkPayKind.ONCHAIN
                                },
                    )
            } catch (e: CancellationException) {
                // TimeoutCancellationException is a CancellationException — convert to Error
                // so the UI does not stay stuck on Sending forever.
                if (e is TimeoutCancellationException) {
                    mutex.withLock {
                        if (wallet === w) {
                            runCatching { refreshStateLocked(sync = true) }
                        }
                    }
                    val base = localizedString(R.string.ark_send_lightning_timeout)
                    _sendState.value =
                        ArkSendState.Error(
                            if (paymentMayHaveCompleted) {
                                localizedString(R.string.ark_send_may_have_completed_format, base)
                            } else {
                                base
                            },
                        )
                    return@withContext
                }
                throw e
            } catch (e: Exception) {
                mutex.withLock {
                    if (wallet === w) {
                        runCatching { refreshStateLocked(sync = true) }
                    }
                }
                val base = publicError(e)
                val message =
                    if (paymentMayHaveCompleted) {
                        localizedString(R.string.ark_send_may_have_completed_format, base)
                    } else {
                        base
                    }
                _sendState.value = ArkSendState.Error(message)
            }
        }

    private fun clearPreparedSendStateLocked() {
        preparedDestination = null
        preparedAmountSats = null
        preparedMethod = null
        preparedPayKind = null
        preparedUseAllFunds = false
        preparedLabel = null
        preparedMultiItems = emptyList()
        preparedMultiLabel = null
    }

    suspend fun prepareBoard(amountSats: Long, boardAll: Boolean) =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val w = wallet
                if (w == null) {
                    _transferState.value = ArkTransferState.Error(localizedString(R.string.ark_error_wallet_not_loaded))
                    return@withLock
                }
                _transferState.value = ArkTransferState.Preparing
                try {
                    // Always estimate with a concrete amount. boardAll still uses the
                    // requested amountSats (UI passes available L1 balance when Max).
                    val estimateAmount = amountSats.coerceAtLeast(1L)
                    if (estimateAmount <= 0L) {
                        _transferState.value = ArkTransferState.Error(localizedString(R.string.ark_enter_amount))
                        return@withLock
                    }
                    val minBoard =
                        runCatching { w.arkInfo()?.minBoardAmountSats?.toLong() }
                            .getOrNull()
                            ?.takeIf { it > 0L }
                            ?: _arkState.value.minBoardAmountSats?.takeIf { it > 0L }
                    if (ArkDepositPolicy.isBelowMinBoardAmount(estimateAmount, minBoard) && minBoard != null) {
                        _transferState.value =
                            ArkTransferState.Error(
                                localizedString(
                                    R.string.ark_transfer_below_min_board_format,
                                    "$minBoard sats",
                                ),
                            )
                        return@withLock
                    }
                    val estimate = w.estimateBoardFee(estimateAmount.toULong())
                    // BTC→Ark boards from Bark's bundled on-chain wallet; show that Bitcoin address.
                    val bitcoinDepositAddress =
                        resolveUnusedBitcoinDepositAddressLocked(forceNew = false)
                    _transferState.value =
                        ArkTransferState.BoardPreview(
                            amountSats = amountSats,
                            feeSats = estimate.feeSats.toLong(),
                            netAmountSats = estimate.netAmountSats.toLong(),
                            boardAll = boardAll,
                            bitcoinDepositAddress = bitcoinDepositAddress,
                            feeRateSatPerVb = feeRateFromEstimate(estimate),
                            grossAmountSats = estimate.grossAmountSats.toLong(),
                        )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _transferState.value = ArkTransferState.Error(publicError(e))
                }
            }
        }

    /**
     * Finish BTC→Ark after Ibis L1 funding broadcast. Completes immediately with the L1
     * txid — do not block on Bark Esplora sync / board (that hung Confirm for ~1 min).
     * Auto-board only when the user opted in; otherwise funds stay on-chain until Boarding tab.
     */
    fun completeLayer1Funding(fundingTxid: String) {
        val txid = fundingTxid.trim()
        _transferState.value =
            ArkTransferState.Completed(detail = txid.takeIf { it.isNotBlank() })
        if (txid.isNotBlank()) {
            val walletId = _loadedWalletId.value
            if (walletId != null) {
                secureStorage.addArkFundingTxid(walletId, txid)
            }
            // Immediately resurface funding txid on any board movements missing metadata.
            runCatching {
                val current = _arkState.value
                if (current.movements.isNotEmpty()) {
                    _arkState.value =
                        current.copy(
                            movements =
                                current.movements.map { movement ->
                                    injectFundingTxid(movement, listOf(txid.lowercase()))
                                },
                        )
                }
            }
        }
        if (secureStorage.isArkAutoBoardEnabled()) {
            scheduleDeferredBoardAttempts()
        } else {
            // Paint on-chain deposit without boarding.
            eventScope.launch {
                runCatching { refreshState(indicateSync = false) }
            }
        }
    }

    /** Marks BTC→Ark L1 funding in progress (dialog busy state). */
    fun markBoardFundingInProgress() {
        _transferState.value = ArkTransferState.InProgress
    }

    fun markBoardFundingFailed(message: String) {
        _transferState.value = ArkTransferState.Error(message)
    }

    /**
     * Kick off staggered board attempts (immediate + delayed). Cancels any previous schedule.
     * Only used when auto-board is enabled or after an explicit user board that needs retries.
     */
    fun scheduleDeferredBoardAttempts() {
        deferredBoardJob?.cancel()
        deferredBoardJob =
            eventScope.launch {
                val delaysMs =
                    longArrayOf(
                        0L,
                        15_000L,
                        45_000L,
                        90_000L,
                        180_000L,
                        300_000L,
                        600_000L,
                        900_000L,
                        1_200_000L,
                        1_800_000L,
                    )
                for ((index, waitMs) in delaysMs.withIndex()) {
                    if (waitMs > 0L) delay(waitMs)
                    if (wallet == null) break
                    // Auto path only — explicit board uses boardOnchain* APIs.
                    if (!secureStorage.isArkAutoBoardEnabled() && index > 0) break
                    val state = _arkState.value
                    if (
                        state.onchainTotalSats <= 0L &&
                        state.pendingBoardSats <= 0L &&
                        index > 0
                    ) {
                        break
                    }
                    val boarded = tryBoardPendingOnchainFunds(forceRefresh = true, force = false)
                    if (boarded) {
                        SecureLog.w(TAG, "Ark deferred board succeeded (attempt ${index + 1})")
                        repeat(4) { i ->
                            delay(if (i == 0) 10_000L else 30_000L)
                            if (wallet == null) return@launch
                            tryBoardPendingOnchainFunds(forceRefresh = true, force = false)
                            if (_arkState.value.pendingBoardSats <= 0L &&
                                _arkState.value.onchainTotalSats <= 0L
                            ) {
                                return@launch
                            }
                        }
                        break
                    }
                }
            }
    }

    /**
     * Explicit user board of all eligible on-chain funds ([Wallet.boardAll]).
     */
    suspend fun boardOnchainAll(): Result<Long> =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val before = _arkState.value.onchainConfirmedSats
                val ok = boardPendingOnchainFundsLocked(force = true, amountSats = null)
                runCatching { refreshStateLocked(sync = true, attemptBoard = false) }
                if (ok) {
                    val amount = before.coerceAtLeast(0L)
                    _events.tryEmit(ArkEvent.BoardSucceeded(amountSats = amount))
                    Result.success(amount)
                } else {
                    Result.failure(
                        IllegalStateException(localizedString(R.string.ark_board_failed_generic)),
                    )
                }
            }
        }

    /**
     * Explicit user board of [amountSats] via [Wallet.boardAmount] (Bark coin-selects).
     */
    suspend fun boardOnchainAmount(amountSats: Long): Result<Long> =
        withContext(Dispatchers.IO) {
            val amount = amountSats.coerceAtLeast(0L)
            if (amount <= 0L) {
                return@withContext Result.failure(
                    IllegalArgumentException(localizedString(R.string.ark_board_failed_generic)),
                )
            }
            mutex.withLock {
                val ok = boardPendingOnchainFundsLocked(force = true, amountSats = amount)
                runCatching { refreshStateLocked(sync = true, attemptBoard = false) }
                if (ok) {
                    _events.tryEmit(ArkEvent.BoardSucceeded(amountSats = amount))
                    Result.success(amount)
                } else {
                    Result.failure(
                        IllegalStateException(localizedString(R.string.ark_board_failed_generic)),
                    )
                }
            }
        }

    /**
     * Best-effort: sync Bark on-chain and board when allowed.
     * [force] true = user-initiated (ignores auto-board pref).
     */
    private suspend fun tryBoardPendingOnchainFunds(
        forceRefresh: Boolean = false,
        force: Boolean = false,
    ): Boolean {
        return withContext(Dispatchers.IO) {
            mutex.withLock {
                val boarded = boardPendingOnchainFundsLocked(force = force, amountSats = null)
                if (forceRefresh) {
                    runCatching { refreshStateLocked(sync = true, attemptBoard = false) }
                }
                boarded
            }
        }
    }

    /**
     * Must hold [mutex]. Session wipe erased the Bark on-chain DB's revealed-address set;
     * replay the deterministic [OnchainWallet.newAddress] sequence until every deposit
     * address Ibis handed out in previous sessions is revealed again. Without this the
     * wallet's sync scans nothing and confirmed deposits can never board.
     */
    private suspend fun catchUpOnchainDepositRevelationLocked(onchain: OnchainWallet) {
        val walletId = _loadedWalletId.value ?: return
        val known =
            secureStorage.getArkOnchainDepositAddressHistory(walletId)
                .map { it.trim() }
                .filter { it.isNotEmpty() }
        if (known.isEmpty()) return
        val remaining = known.toMutableSet()
        var revealed = 0
        var consecutiveFailures = 0
        repeat(ONCHAIN_REVEAL_CATCHUP_MAX) {
            if (remaining.isEmpty()) return@repeat
            val addr =
                runCatching { onchain.newAddress() }
                    .getOrNull()
                    ?.trim()
                    .orEmpty()
            if (addr.isEmpty()) {
                consecutiveFailures++
                if (consecutiveFailures >= 2) return
                return@repeat
            }
            consecutiveFailures = 0
            revealed++
            remaining.remove(addr)
        }
        if (remaining.isNotEmpty()) {
            SecureLog.w(
                TAG,
                "Ark on-chain reveal catch-up incomplete: ${remaining.size}/${known.size} " +
                    "known deposit address(es) not reproduced after $revealed reveals",
            )
        } else if (revealed > 0) {
            SecureLog.w(TAG, "Ark on-chain reveal catch-up: $revealed address(es) re-revealed")
        }
    }

    /**
     * Must hold [mutex]. Sync on-chain and optionally board.
     * [force] true = user-initiated (ignores auto-board pref).
     * [amountSats] null = boardAll; non-null = boardAmount.
     * Never boards while unilateral exits are pending (fee UTXOs).
     */
    private suspend fun boardPendingOnchainFundsLocked(
        force: Boolean = false,
        amountSats: Long? = null,
    ): Boolean {
        val w = wallet ?: return false
        if (!force && !secureStorage.isArkAutoBoardEnabled()) {
            runCatching { w.syncPendingBoards() }
            return false
        }
        val onchain = onchainWallet
        if (onchain == null) {
            SecureLog.w(TAG, "Ark board skipped: on-chain wallet not loaded")
            if (force || _arkState.value.hasInboundDeposit || _arkState.value.movements.isNotEmpty()) {
                emitOnchainUnavailableThrottled()
            }
            if (force) {
                _events.tryEmit(
                    ArkEvent.BoardFailed(
                        localizedString(R.string.ark_error_onchain_wallet_unavailable),
                    ),
                )
            }
            return false
        }
        val hasPendingExits =
            runCatching { w.hasPendingExits() }.getOrDefault(_arkState.value.hasPendingExits)
        if (!ArkUnilateralExitPolicy.shouldAutoBoardOnchainFunds(hasPendingExits)) {
            runCatching { w.syncPendingBoards() }
            if (force) {
                _events.tryEmit(
                    ArkEvent.BoardFailed(localizedString(R.string.ark_board_disabled_exit)),
                )
            }
            return false
        }
        if (!onchainRevealCatchUpDone) {
            onchainRevealCatchUpDone = true
            catchUpOnchainDepositRevelationLocked(onchain)
        }
        runCatching { onchain.sync() }
            .onFailure { SecureLog.w(TAG, "Ark on-chain sync failed: ${publicError(it)}") }
        var bal = runCatching { onchain.balance() }.getOrNull()
        var confirmed = bal?.confirmedSats?.toLong() ?: 0L
        var pending = bal?.pendingSats?.toLong() ?: 0L
        var total = bal?.totalSats?.toLong() ?: (confirmed + pending)
        if (total <= 0L && _arkState.value.onchainTotalSats > 0L) {
            delay(1_500L)
            runCatching { onchain.sync() }
            bal = runCatching { onchain.balance() }.getOrNull()
            confirmed = bal?.confirmedSats?.toLong() ?: 0L
            pending = bal?.pendingSats?.toLong() ?: 0L
            total = bal?.totalSats?.toLong() ?: (confirmed + pending)
        }
        if (total <= 0L) {
            SecureLog.d(
                TAG,
                "Ark board skipped: on-chain total=0 " +
                    "(uiOnchain=${_arkState.value.onchainTotalSats} " +
                    "pendingBoard=${_arkState.value.pendingBoardSats})",
            )
            if (_arkState.value.hasInboundDeposit) {
                blindBoardAttempts++
                if (blindBoardAttempts >= ONCHAIN_BLIND_BOARD_ATTEMPTS_ALERT) {
                    blindBoardAttempts = 0
                    SecureLog.w(
                        TAG,
                        "Ark board blind: Esplora sees deposit but Bark on-chain balance=0 " +
                            "after reveal catch-up + sync",
                    )
                    emitOnchainUnavailableThrottled()
                }
            }
            if (force) {
                _events.tryEmit(
                    ArkEvent.BoardFailed(localizedString(R.string.ark_board_failed_generic)),
                )
            }
            runCatching { w.syncPendingBoards() }
            return false
        }
        blindBoardAttempts = 0
        val minBoard =
            runCatching { w.arkInfo()?.minBoardAmountSats?.toLong() }
                .getOrNull()
                ?.takeIf { it > 0L }
                ?: _arkState.value.minBoardAmountSats?.takeIf { it > 0L }
        val boardTarget = amountSats?.takeIf { it > 0L } ?: confirmed
        if (
            minBoard != null &&
            boardTarget > 0L &&
            boardTarget < minBoard
        ) {
            emitBoardBelowMinimumThrottled(
                onchainConfirmedSats = confirmed.coerceAtLeast(boardTarget),
                minBoardAmountSats = minBoard,
            )
            if (force) {
                _events.tryEmit(
                    ArkEvent.BoardFailed(
                        localizedString(
                            R.string.ark_transfer_below_min_board_format,
                            formatBoardMinLabel(minBoard),
                        ),
                    ),
                )
            }
            runCatching { w.syncPendingBoards() }
            return false
        }
        if (
            amountSats == null &&
            ArkDepositPolicy.isStuckBelowMinBoard(
                onchainConfirmedSats = confirmed,
                pendingBoardSats = _arkState.value.pendingBoardSats,
                minBoardAmountSats = minBoard,
            ) &&
            minBoard != null
        ) {
            emitBoardBelowMinimumThrottled(
                onchainConfirmedSats = confirmed,
                minBoardAmountSats = minBoard,
            )
            runCatching { w.syncPendingBoards() }
            return false
        }
        val boardResult =
            runCatching {
                if (amountSats != null && amountSats > 0L) {
                    w.boardAmount(amountSats.toULong())
                } else {
                    w.boardAll()
                }
            }.onFailure { err ->
                val msg = publicError(err)
                SecureLog.w(
                    TAG,
                    "Ark board deferred (onchain total=$total conf=$confirmed pend=$pending " +
                        "amount=$amountSats force=$force): $msg",
                )
                if (
                    minBoard != null &&
                    (
                        msg.contains("minimum", ignoreCase = true) ||
                            msg.contains("min board", ignoreCase = true) ||
                            msg.contains("does not meet", ignoreCase = true)
                    )
                ) {
                    emitBoardBelowMinimumThrottled(
                        onchainConfirmedSats = confirmed.coerceAtLeast(total),
                        minBoardAmountSats = minBoard,
                    )
                }
                if (force) {
                    _events.tryEmit(ArkEvent.BoardFailed(msg))
                }
            }
        runCatching { w.syncPendingBoards() }
        return boardResult.isSuccess
    }

    private fun formatBoardMinLabel(minBoard: Long): String = "$minBoard sats"

    /**
     * Sweep confirmed Bark on-chain funds (stuck below ASP min board) to a Layer 1 address.
     * Uses Bark [OnchainWallet.send] — not board.
     *
     * Native send/sync run **outside** [mutex] so a hung Esplora/send cannot brick Ark UI.
     * Mutex waits are bounded so a stuck refresh cannot pin the Recover spinner.
     */
    suspend fun recoverOnchainDepositToLayer1(
        destinationAddress: String,
        feeRateSatPerVb: Long = 2L,
    ): Result<String> =
        withContext(Dispatchers.IO) {
            val dest = destinationAddress.trim()
            if (dest.isBlank()) {
                return@withContext emitRecoverResult(
                    Result.failure(Exception(localizedString(R.string.loc_9f80cab8))),
                )
            }
            val snapshot =
                withMutexTimeout(RECOVER_MUTEX_WAIT_MS) {
                    RecoverOnchainSnapshot(
                        onchain = onchainWallet,
                        walletId = _loadedWalletId.value,
                        deposit = snapshotPendingDepositForRecoverLocked(),
                        paintedConfirmedSats = _arkState.value.onchainConfirmedSats,
                        paintedUtxos = _arkState.value.onchainUtxos,
                    )
                } ?: return@withContext emitRecoverResult(
                    Result.failure(Exception(localizedString(R.string.ark_recover_onchain_busy))),
                )
            val onchain =
                snapshot.onchain
                    ?: return@withContext emitRecoverResult(
                        Result.failure(Exception(localizedString(R.string.ark_error_onchain_wallet_unavailable))),
                    )
            val walletId = snapshot.walletId
            val depositSnapshot = snapshot.deposit

            // send() spends already-indexed BDK UTXOs — do not Esplora-sync first.
            var confirmed =
                runCatching { onchain.balance().confirmedSats.toLong() }.getOrDefault(0L)
            if (confirmed <= 0L) {
                withMutexTimeout(RECOVER_MUTEX_WAIT_MS) {
                    if (!onchainRevealCatchUpDone) {
                        onchainRevealCatchUpDone = true
                        catchUpOnchainDepositRevelationLocked(onchain)
                    }
                }
                try {
                    withTimeout(RECOVER_ONCHAIN_SYNC_TIMEOUT_MS) {
                        onchain.sync()
                    }
                } catch (err: TimeoutCancellationException) {
                    if (snapshot.paintedConfirmedSats <= 0L) {
                        return@withContext emitRecoverResult(
                            Result.failure(Exception(localizedString(R.string.ark_recover_onchain_timeout))),
                        )
                    }
                } catch (err: CancellationException) {
                    throw err
                } catch (err: Exception) {
                    if (snapshot.paintedConfirmedSats <= 0L) {
                        return@withContext emitRecoverResult(Result.failure(Exception(publicError(err))))
                    }
                }
                confirmed = runCatching { onchain.balance().confirmedSats.toLong() }.getOrDefault(0L)
            }
            if (confirmed <= 0L) {
                return@withContext emitRecoverResult(
                    Result.failure(Exception(localizedString(R.string.ark_recover_onchain_nothing))),
                )
            }

            val rate = feeRateSatPerVb.coerceIn(1L, 200L)
            // 1-in-1-out P2TR is ~110 vB; keep a small pad so change is not dust.
            val feePad = (rate * 140L).coerceAtLeast(200L)
            val sendAmount = (confirmed - feePad).coerceAtLeast(546L)
            if (sendAmount >= confirmed) {
                return@withContext emitRecoverResult(
                    Result.failure(Exception(localizedString(R.string.ark_recover_onchain_fee_too_high))),
                )
            }

            val txid =
                try {
                    onchain.send(dest, sendAmount.toULong(), rate.toULong())
                } catch (err: CancellationException) {
                    throw err
                } catch (err: Exception) {
                    return@withContext emitRecoverResult(Result.failure(Exception(publicError(err))))
                }

            val persisted =
                withMutexTimeout(RECOVER_MUTEX_WAIT_MS) {
                    persistRecoveredOnchainDepositLocked(
                        walletId = walletId,
                        destinationAddress = dest,
                        recoverTxid = txid,
                        snapshot = depositSnapshot,
                        amountSats = confirmed,
                        paintedUtxos = snapshot.paintedUtxos,
                    )
                    applyRecoverOnchainPaintClearedLocked(walletId)
                    true
                }
            if (persisted == null) {
                persistRecoveredOnchainDepositLocked(
                    walletId = walletId,
                    destinationAddress = dest,
                    recoverTxid = txid,
                    snapshot = depositSnapshot,
                    amountSats = confirmed,
                    paintedUtxos = snapshot.paintedUtxos,
                )
                applyRecoverOnchainPaintClearedLocked(walletId)
            }
            emitRecoverResult(Result.success(txid))
        }

    private fun emitRecoverResult(result: Result<String>): Result<String> {
        result.fold(
            onSuccess = { txid -> _events.tryEmit(ArkEvent.RecoverSucceeded(txid)) },
            onFailure = { err ->
                _events.tryEmit(
                    ArkEvent.RecoverFailed(
                        err.message ?: localizedString(R.string.ark_error_generic),
                    ),
                )
            },
        )
        return result
    }

    private suspend fun <T> withMutexTimeout(
        timeoutMs: Long,
        block: suspend () -> T,
    ): T? =
        withTimeoutOrNull(timeoutMs) {
            mutex.withLock { block() }
        }

    private data class RecoverOnchainSnapshot(
        val onchain: OnchainWallet?,
        val walletId: String?,
        val deposit: ArkMovement?,
        val paintedConfirmedSats: Long,
        val paintedUtxos: List<ArkOnchainUtxo>,
    )

    /** Must hold [mutex]. Clear on-chain paint + funding tags after L1 recover. */
    private fun applyRecoverOnchainPaintClearedLocked(walletId: String?) {
        markOnchainRecoverSuppressedLocked(walletId, suppressed = true)
        _arkState.value =
            _arkState.value.copy(
                onchainConfirmedSats = 0L,
                onchainPendingSats = 0L,
                onchainUtxos = emptyList(),
            )
        if (walletId != null) {
            secureStorage.setArkFundingTxids(walletId, emptyList())
            secureStorage.saveArkWalletStateCache(
                walletId,
                _arkState.value.withNoiseMovementsFiltered(walletId),
            )
        }
    }

    suspend fun prepareOffboard(
        destinationAddress: String,
        amountSats: Long?,
        offboardAll: Boolean,
    ) =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val w = wallet
                if (w == null) {
                    _transferState.value = ArkTransferState.Error(localizedString(R.string.ark_error_wallet_not_loaded))
                    return@withLock
                }
                _transferState.value = ArkTransferState.Preparing
                try {
                    val dest = destinationAddress.trim()
                    if (dest.isBlank()) {
                        _transferState.value =
                            ArkTransferState.Error(localizedString(R.string.loc_9f80cab8))
                        return@withLock
                    }
                    val estimate: FeeEstimate =
                        if (offboardAll) {
                            w.estimateOffboardAllFee(dest)
                        } else {
                            val amount = amountSats ?: 0L
                            if (amount <= 0L) {
                                _transferState.value = ArkTransferState.Error(localizedString(R.string.ark_enter_amount))
                                return@withLock
                            }
                            // Partial exact amount: cooperative on-chain send fee path
                            w.estimateSendOnchainFee(dest, amount.toULong())
                        }
                    _transferState.value =
                        ArkTransferState.OffboardPreview(
                            destinationAddress = dest,
                            amountSats = amountSats ?: estimate.netAmountSats.toLong(),
                            feeSats = estimate.feeSats.toLong(),
                            netAmountSats = estimate.netAmountSats.toLong(),
                            offboardAll = offboardAll,
                            feeRateSatPerVb = feeRateFromEstimate(estimate),
                            grossAmountSats = estimate.grossAmountSats.toLong(),
                        )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _transferState.value = ArkTransferState.Error(publicError(e))
                }
            }
        }

    suspend fun executeOffboard() =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val w = wallet
                val preview = _transferState.value as? ArkTransferState.OffboardPreview
                if (w == null || preview == null) {
                    _transferState.value = ArkTransferState.Error(localizedString(R.string.ark_error_nothing_prepared))
                    return@withLock
                }
                _transferState.value = ArkTransferState.InProgress
                try {
                    val detail =
                        if (preview.offboardAll) {
                            w.offboardAll(preview.destinationAddress).roundId
                        } else {
                            val amount = preview.amountSats ?: error(localizedString(R.string.ark_error_amount_required))
                            w.sendOnchain(preview.destinationAddress, amount.toULong())
                        }
                    _transferState.value = ArkTransferState.Completed(detail = detail)
                    refreshStateLocked(sync = true)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _transferState.value = ArkTransferState.Error(publicError(e))
                }
            }
        }

    suspend fun maintenance() =
        withContext(Dispatchers.IO) {
            // Native maintenance can block for a long time — never hold [mutex] across it
            // or send review waits forever behind the 5-minute tick.
            val w = mutex.withLock { wallet } ?: return@withContext
            val feeRate = resolveExitProgressFeeRateSatPerVbLocked()
            runCatching { w.progressPendingRounds() }
            runCatching {
                exitOperationMutex.withLock {
                    if (w.hasPendingExits()) {
                        val progress =
                            w.progressExits(
                                feeRateSatPerVb = feeRate.toULong(),
                            )
                        if (progress.isNotEmpty()) {
                            _events.tryEmit(
                                ArkEvent.ExitProgressUpdate(
                                    statuses = progress.map { it.toArkExitProgress() },
                                ),
                            )
                        }
                    }
                }
            }.onFailure { SecureLog.w(TAG, "Ark exit progress failed") }
            runCatching { w.syncExits() }
            runCatching { w.syncForceExitedVtxos() }
            runCatching { w.syncPendingBoards() }
            val onchain =
                mutex.withLock {
                    if (wallet !== w) return@withContext
                    onchainWallet
                }
            runCatching { onchain?.sync() }
            mutex.withLock {
                if (wallet !== w) return@withLock
                // Opt-in auto-board only (default off).
                if (secureStorage.isArkAutoBoardEnabled()) {
                    runCatching { boardPendingOnchainFundsLocked(force = false) }
                }
                refreshStateLocked(sync = true, attemptBoard = false)
            }
        }

    fun resetLifecycleState() {
        // Pending refresh badges live on [ArkWalletState.pendingRefreshVtxoIds]; lifecycle
        // can return to Idle so Manual refresh is not locked after the dialog closes.
        _lifecycleState.value = ArkLifecycleState.Idle
    }

    private fun publishPendingRefreshToWalletStateLocked() {
        val ids = trackedRefreshVtxoIds
        val height = trackedRefreshScheduledHeight
        val current = _arkState.value
        if (
            current.pendingRefreshVtxoIds == ids &&
                current.pendingRefreshScheduledHeight == height
        ) {
            return
        }
        _arkState.value =
            current.copy(
                pendingRefreshVtxoIds = ids,
                pendingRefreshScheduledHeight = height,
            )
    }

    private fun clearTrackedRefreshLocked() {
        trackedRefreshRoundId = null
        trackedRefreshScheduledHeight = null
        trackedRefreshAutomatic = false
        trackedRefreshVtxoIds = emptyList()
        publishPendingRefreshToWalletStateLocked()
    }

    /**
     * Clear manual/auto refresh tracking when the ASP round is gone or the submitted VTXO
     * ids no longer exist (refresh replaced them). Prevents stuck pending badges / locks.
     */
    private suspend fun reconcileTrackedRefreshLocked(
        w: Wallet,
        liveVtxoIds: Collection<String>,
    ) {
        if (trackedRefreshVtxoIds.isEmpty() && trackedRefreshRoundId == null) return
        val roundId = trackedRefreshRoundId
        if (roundId != null) {
            val stillPending =
                runCatching { w.pendingRoundStates().any { it.id == roundId } }
                    .getOrDefault(true)
            if (!stillPending) {
                val automatic = trackedRefreshAutomatic
                clearTrackedRefreshLocked()
                if (_lifecycleState.value is ArkLifecycleState.RefreshPending) {
                    _lifecycleState.value = ArkLifecycleState.Completed()
                }
                _events.tryEmit(
                    ArkEvent.RefreshCompleted(
                        automatic = automatic,
                        delegated = true,
                    ),
                )
                return
            }
        }
        val live = liveVtxoIds.toSet()
        // Submitted ids are gone → round consumed the old VTXOs.
        if (trackedRefreshVtxoIds.isNotEmpty() && trackedRefreshVtxoIds.none { it in live }) {
            val automatic = trackedRefreshAutomatic
            clearTrackedRefreshLocked()
            if (_lifecycleState.value is ArkLifecycleState.RefreshPending) {
                _lifecycleState.value = ArkLifecycleState.Completed()
            }
            _events.tryEmit(
                ArkEvent.RefreshCompleted(
                    automatic = automatic,
                    delegated = true,
                ),
            )
            return
        }
        // Still waiting — keep badges painted.
        if (trackedRefreshVtxoIds.isNotEmpty()) {
            publishPendingRefreshToWalletStateLocked()
            if (_lifecycleState.value is ArkLifecycleState.RefreshPending) {
                _lifecycleState.value =
                    ArkLifecycleState.RefreshPending(
                        scheduledHeight = trackedRefreshScheduledHeight,
                        vtxoIds = trackedRefreshVtxoIds,
                        inFlight = trackedRefreshRoundId != null || manualRefreshJob?.isActive == true,
                    )
            }
        }
    }

    /**
     * Opens the refresh review dialog. With selected [vtxoIds], paints [RefreshPreview]
     * **synchronously** (no Loading spinner, no wait on ASP). Fee quote is best-effort in
     * the background and may stay Unavailable.
     */
    fun prepareRefresh(vtxoIds: List<String> = emptyList()) {
        val alreadyPending = trackedRefreshVtxoIds.toSet()
        if (vtxoIds.isNotEmpty()) {
            val targets = vtxoIds.filter { it !in alreadyPending }
            if (targets.isEmpty()) {
                _lifecycleState.value =
                    ArkLifecycleState.Error(
                        localizedString(R.string.ark_error_refresh_already_pending),
                    )
                return
            }
            // Instant paint on caller thread — dialog never blocks on IO / fee FFI.
            _lifecycleState.value =
                ArkLifecycleState.RefreshPreview(
                    vtxoIds = targets,
                    feeSats = null,
                    netAmountSats = null,
                    refreshAll = false,
                    scheduledHeight = null,
                )
            eventScope.launch {
                fillRefreshFeeQuote(targets)
            }
            return
        }

        // No ids: resolve due list off the UI thread (rare / auto path).
        _lifecycleState.value = ArkLifecycleState.Loading
        eventScope.launch {
            try {
                val w =
                    mutex.withLock { wallet }
                        ?: run {
                            _lifecycleState.value =
                                ArkLifecycleState.Error(
                                    localizedString(R.string.ark_error_wallet_not_loaded),
                                )
                            return@launch
                        }
                val rawTargets =
                    withTimeoutOrNull(ARK_REFRESH_QUOTE_TIMEOUT_MS) {
                        runCatching { w.getVtxosToRefresh().map { it.id } }.getOrNull()
                    }.orEmpty()
                val targets = rawTargets.filter { it !in alreadyPending }
                if (targets.isEmpty()) {
                    _lifecycleState.value =
                        ArkLifecycleState.Error(
                            if (rawTargets.isNotEmpty() && alreadyPending.isNotEmpty()) {
                                localizedString(R.string.ark_error_refresh_already_pending)
                            } else {
                                localizedString(R.string.ark_error_no_vtxos_need_refresh)
                            },
                        )
                    return@launch
                }
                _lifecycleState.value =
                    ArkLifecycleState.RefreshPreview(
                        vtxoIds = targets,
                        feeSats = null,
                        netAmountSats = null,
                        refreshAll = true,
                        scheduledHeight = null,
                    )
                fillRefreshFeeQuote(targets)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _lifecycleState.value = ArkLifecycleState.Error(publicError(e))
            }
        }
    }

    /** Best-effort fee fill for an already-painted [RefreshPreview]. Never blocks the dialog. */
    private suspend fun fillRefreshFeeQuote(targets: List<String>) {
        val w = mutex.withLock { wallet } ?: return
        val estimate =
            withTimeoutOrNull(ARK_REFRESH_QUOTE_TIMEOUT_MS) {
                runCatching { w.estimateRefreshFee(targets) }.getOrNull()
            } ?: return
        val current = _lifecycleState.value as? ArkLifecycleState.RefreshPreview ?: return
        if (current.vtxoIds != targets) return
        if (mutex.withLock { wallet !== w }) return
        _lifecycleState.value =
            current.copy(
                feeSats = estimate.feeSats.toLong(),
                netAmountSats = estimate.netAmountSats.toLong(),
            )
    }

    /** Lifecycle confirm. Manual and automatic UI paths use delegated refresh only. */
    suspend fun executeRefresh(delegated: Boolean = true) =
        withContext(Dispatchers.IO) {
            val preview =
                _lifecycleState.value as? ArkLifecycleState.RefreshPreview
                    ?: run {
                        _lifecycleState.value =
                            ArkLifecycleState.Error(localizedString(R.string.ark_error_nothing_prepared))
                        return@withContext
                    }
            val w =
                mutex.withLock { wallet }
                    ?: run {
                        _lifecycleState.value =
                            ArkLifecycleState.Error(localizedString(R.string.ark_error_wallet_not_loaded))
                        return@withContext
                    }
            val preferDelegated = delegated
            // Allow another manual refresh only for VTXOs not already in a pending round.
            val alreadyPending = trackedRefreshVtxoIds.toSet()
            val submitIds = preview.vtxoIds.filter { it !in alreadyPending }
            if (submitIds.isEmpty()) {
                _lifecycleState.value =
                    ArkLifecycleState.Error(localizedString(R.string.ark_error_refresh_already_pending))
                return@withContext
            }
            if (manualRefreshJob?.isActive == true) return@withContext
            // Bark refreshVtxosDelegated blocks until a round finishes (can be minutes).
            // FFI cancel cannot interrupt it. Paint dismissible Pending immediately so the
            // review dialog never sticks on Working…; native work continues in the background.
            trackedRefreshVtxoIds = (trackedRefreshVtxoIds + submitIds).distinct()
            trackedRefreshScheduledHeight = preview.scheduledHeight
            trackedRefreshAutomatic = false
            publishPendingRefreshToWalletStateLocked()
            _lifecycleState.value =
                ArkLifecycleState.RefreshPending(
                    scheduledHeight = preview.scheduledHeight,
                    vtxoIds = submitIds,
                    inFlight = true,
                )
            _events.tryEmit(
                ArkEvent.RefreshSubmitted(
                    automatic = false,
                    scheduledHeight = preview.scheduledHeight,
                    vtxoCount = submitIds.size,
                ),
            )
            val job =
                eventScope.launch {
                    try {
                        val result =
                            runDelegatedRefreshWithFallbackLocked(
                                wallet = w,
                                vtxoIds = submitIds,
                                preferDelegated = preferDelegated,
                                allowNonDelegatedFallback = false,
                                scheduledHeight = preview.scheduledHeight,
                            )
                        mutex.withLock {
                            if (wallet !== w) return@withLock
                            if (result.delegated) {
                                trackedRefreshRoundId = result.roundId
                                trackedRefreshScheduledHeight = result.scheduledHeight
                                trackedRefreshAutomatic = false
                                trackedRefreshVtxoIds =
                                    (trackedRefreshVtxoIds + submitIds).distinct()
                                publishPendingRefreshToWalletStateLocked()
                            }
                            runCatching {
                                refreshStateLocked(sync = !result.delegated, attemptBoard = false)
                            }
                            val current = _lifecycleState.value
                            // Don't clobber Error (user may still see dialog).
                            if (current is ArkLifecycleState.Error) return@withLock
                            if (!result.delegated) {
                                clearTrackedRefreshLocked()
                                _lifecycleState.value =
                                    ArkLifecycleState.Completed(detail = result.detail)
                            } else {
                                _lifecycleState.value =
                                    ArkLifecycleState.RefreshPending(
                                        scheduledHeight = result.scheduledHeight,
                                        vtxoIds = submitIds,
                                        inFlight = result.roundId != null,
                                    )
                            }
                        }
                        if (!result.delegated) {
                            _events.tryEmit(
                                ArkEvent.RefreshCompleted(
                                    detail = result.detail,
                                    automatic = false,
                                    delegated = false,
                                ),
                            )
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        val msg = publicError(e)
                        mutex.withLock {
                            // Drop only the ids from this failed attempt; keep other pending.
                            trackedRefreshVtxoIds =
                                trackedRefreshVtxoIds.filterNot { it in submitIds.toSet() }
                            if (trackedRefreshVtxoIds.isEmpty()) {
                                trackedRefreshRoundId = null
                                trackedRefreshScheduledHeight = null
                            }
                            publishPendingRefreshToWalletStateLocked()
                        }
                        val current = _lifecycleState.value
                        if (
                            current is ArkLifecycleState.InProgress ||
                                current is ArkLifecycleState.RefreshPending
                        ) {
                            _lifecycleState.value = ArkLifecycleState.Error(msg)
                        }
                        _events.tryEmit(
                            ArkEvent.RefreshFailed(
                                message = msg,
                                automatic = false,
                                delegated = preferDelegated,
                            ),
                        )
                    } finally {
                        manualRefreshJob = null
                    }
                }
            manualRefreshJob = job
        }

    /** Balance one-tap: schedule ahead when soon, otherwise submit delegated to the next round. */
    suspend fun quickRefreshVtxos() =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val w = wallet
                if (w == null) {
                    _events.tryEmit(
                        ArkEvent.RefreshFailed(
                            message = localizedString(R.string.ark_error_wallet_not_loaded),
                            automatic = false,
                            delegated = true,
                        ),
                    )
                    return@withLock
                }
                _arkState.value = _arkState.value.copy(isAutoRefreshing = true)
                try {
                    val due = w.getVtxosToRefresh().map { it.toArkVtxo() }
                    val allVtxos =
                        if (due.isEmpty()) {
                            w.vtxos()
                                .map { it.toArkVtxo() }
                                .filter { ArkBarkMappers.isSpendableLabel(it.state) }
                        } else {
                            emptyList()
                        }
                    val firstExpiry =
                        runCatching { w.getFirstExpiringVtxoBlockheight()?.toInt() }.getOrNull()
                    val targets = ArkRefreshPolicy.autoRefreshTargets(due, allVtxos, firstExpiry)
                    if (targets.isEmpty()) return@withLock
                    val tip = resolveChainTipHeight()
                    val nextHeight =
                        runCatching { w.getNextRequiredRefreshBlockheight()?.toInt() }.getOrNull()
                    val scheduledHeight =
                        ArkRefreshPolicy.scheduledHeight(nextHeight, firstExpiry, tip, targets)
                    val result =
                        runDelegatedRefreshWithFallbackLocked(
                            wallet = w,
                            vtxoIds = targets.map { it.id },
                            preferDelegated = true,
                            allowNonDelegatedFallback = false,
                            scheduledHeight = scheduledHeight,
                        )
                    trackedRefreshRoundId = result.roundId
                    trackedRefreshScheduledHeight = result.scheduledHeight
                    trackedRefreshAutomatic = false
                    trackedRefreshVtxoIds =
                        (trackedRefreshVtxoIds + targets.map { it.id }).distinct()
                    publishPendingRefreshToWalletStateLocked()
                    refreshStateLocked(sync = false, attemptBoard = false)
                    _lifecycleState.value =
                        ArkLifecycleState.RefreshPending(
                            scheduledHeight = result.scheduledHeight,
                            vtxoIds = targets.map { it.id },
                            inFlight = result.roundId != null,
                        )
                    _events.tryEmit(
                        ArkEvent.RefreshSubmitted(
                            automatic = false,
                            scheduledHeight = result.scheduledHeight,
                            vtxoCount = targets.size,
                        ),
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _events.tryEmit(
                        ArkEvent.RefreshFailed(
                            message = publicError(e),
                            automatic = false,
                            delegated = true,
                        ),
                    )
                } finally {
                    _arkState.value = _arkState.value.copy(isAutoRefreshing = false)
                }
            }
        }

    /**
     * Shared path for auto + one-tap + lifecycle.
     * [preferDelegated]=true tries ASP-delegated first.
     * [scheduledHeight] uses Bark 0.5 height-priced delegated refresh when ahead of tip.
     * [allowNonDelegatedFallback]=false (auto path) fails closed — never silent-spend under a
     * different trust/fee model than the user opted into.
     */
    private data class RefreshSubmission(
        val detail: String,
        val delegated: Boolean,
        val scheduledHeight: Int? = null,
        val roundId: UInt? = null,
    )

    private suspend fun runDelegatedRefreshWithFallbackLocked(
        wallet: Wallet,
        vtxoIds: List<String>,
        preferDelegated: Boolean,
        allowNonDelegatedFallback: Boolean = true,
        scheduledHeight: Int? = null,
    ): RefreshSubmission {
        if (preferDelegated) {
            val height = scheduledHeight?.takeIf { it > 0 }
            val delegatedResult =
                if (height != null) {
                    runCatching {
                        wallet.refreshVtxosScheduled(
                                vtxoIds = vtxoIds,
                                scheduledHeight = height.toUInt(),
                            )
                    }
                } else {
                    runCatching {
                        wallet.refreshVtxosDelegated(vtxoIds)
                    }
                }
            if (delegatedResult.isSuccess) {
                val round = delegatedResult.getOrThrow()
                return RefreshSubmission(
                    detail = "refresh round ${round?.id ?: "?"}",
                    delegated = round != null,
                    scheduledHeight = height?.takeIf { round != null },
                    roundId = round?.id,
                )
            }
            // Scheduled path may be rejected; fall through to immediate delegated once.
            if (height != null) {
                val immediate =
                    runCatching {
                        wallet.refreshVtxosDelegated(vtxoIds)
                    }
                if (immediate.isSuccess) {
                    val round = immediate.getOrThrow()
                    return RefreshSubmission(
                        detail = "refresh round ${round?.id ?: "?"}",
                        delegated = true,
                        roundId = round?.id,
                    )
                }
                if (!allowNonDelegatedFallback) {
                    throw immediate.exceptionOrNull()
                        ?: delegatedResult.exceptionOrNull()
                        ?: Exception(localizedString(R.string.ark_error_refresh_delegated_failed))
                }
                SecureLog.w(
                    TAG,
                    "Scheduled/delegated VTXO refresh failed; falling back to non-delegated: ${
                        publicError(immediate.exceptionOrNull() ?: delegatedResult.exceptionOrNull()!!)
                    }",
                )
            } else {
                if (!allowNonDelegatedFallback) {
                    throw delegatedResult.exceptionOrNull()
                        ?: Exception(localizedString(R.string.ark_error_refresh_delegated_failed))
                }
                SecureLog.w(
                    TAG,
                    "Delegated VTXO refresh failed; falling back to non-delegated: ${
                        publicError(delegatedResult.exceptionOrNull()!!)
                    }",
                )
            }
        }
        val plain = wallet.refreshVtxos(vtxoIds)
        return RefreshSubmission(
            detail = plain?.takeIf { it.isNotBlank() } ?: "refresh",
            delegated = false,
        )
    }

    private fun autoRefreshFeeDayKey(): Long =
        System.currentTimeMillis() / (24L * 60L * 60L * 1000L)

    private fun ensureAutoRefreshFeeDayLocked() {
        val day = autoRefreshFeeDayKey()
        if (autoRefreshFeeDayEpoch != day) {
            autoRefreshFeeDayEpoch = day
            autoRefreshFeeSpentTodaySats = 0L
        }
    }

    private fun recordAutoRefreshFeeLocked(feeSats: Long) {
        if (feeSats <= 0L) return
        ensureAutoRefreshFeeDayLocked()
        autoRefreshFeeSpentTodaySats += feeSats
    }

    private fun autoRefreshDailyRemainingLocked(): Long {
        ensureAutoRefreshFeeDayLocked()
        return (AUTO_REFRESH_DAILY_CAP_SATS - autoRefreshFeeSpentTodaySats).coerceAtLeast(0L)
    }

    private suspend fun estimateScheduledRefreshFeeLocked(
        wallet: Wallet,
        vtxos: Collection<ArkVtxo>,
        scheduledHeight: Int,
    ): Long? {
        val refresh =
            runCatching { wallet.arkInfo()?.feeSchedule?.refresh }.getOrNull() ?: return null
        val base = refresh.baseFeeSats.toLong().takeIf { it >= 0L } ?: return null
        val tiers =
            refresh.ppmExpiryTable.map { entry ->
                ArkRefreshPolicy.PpmTier(
                    expiryBlocksThreshold = entry.expiryBlocksThreshold.toInt(),
                    ppm = entry.ppm.toLong(),
                )
            }.filter { it.expiryBlocksThreshold >= 0 && it.ppm >= 0L }
        if (tiers.isEmpty()) return null
        return ArkRefreshPolicy.estimateScheduledFeeSats(vtxos, scheduledHeight, base, tiers)
    }

    suspend fun startUnilateralExit(
        vtxoIds: List<String> = emptyList(),
        entireWallet: Boolean = false,
    ) =
        withContext(Dispatchers.IO) {
            _lifecycleState.value = ArkLifecycleState.InProgress
            val w = mutex.withLock { wallet }
            val spendableIds =
                if (w != null) {
                    runCatching { w.spendableVtxos().map { it.id } }.getOrDefault(emptyList())
                } else {
                    emptyList()
                }
            val plan =
                ArkUnilateralExitPolicy.planStartExit(
                    walletLoaded = w != null,
                    entireWallet = entireWallet,
                    requestedVtxoIds = vtxoIds,
                    spendableVtxoIds = spendableIds,
                )
            if (plan is ArkUnilateralExitPolicy.StartExitPlan.Error) {
                _lifecycleState.value =
                    ArkLifecycleState.Error(
                        when (plan.reason) {
                            ArkUnilateralExitPolicy.StartExitError.WALLET_NOT_LOADED ->
                                localizedString(R.string.ark_error_wallet_not_loaded)
                            ArkUnilateralExitPolicy.StartExitError.NO_SPENDABLE_VTXOS ->
                                localizedString(R.string.ark_error_no_spendable_vtxos)
                        },
                    )
                return@withContext
            }
            checkNotNull(w)
            exitOperationMutex.withLock {
                try {
                    val selected =
                        when (plan) {
                            is ArkUnilateralExitPolicy.StartExitPlan.EntireWallet -> {
                                val beforeSpendable = spendableIds.toSet()
                                w.startExitForEntireWallet()
                                val exitIds =
                                    runCatching { w.getExitVtxos().map { it.vtxoId } }
                                        .getOrDefault(emptyList())
                                        .ifEmpty {
                                            val afterSpendable =
                                                runCatching { w.spendableVtxos().map { it.id } }
                                                    .getOrDefault(emptyList())
                                                    .toSet()
                                            (beforeSpendable - afterSpendable).toList()
                                        }
                                ArkUnilateralExitPolicy.resolveStartedVtxoIds(plan, exitIds)
                            }
                            is ArkUnilateralExitPolicy.StartExitPlan.Selected -> {
                                w.startExitForVtxos(plan.vtxoIds)
                                ArkUnilateralExitPolicy.resolveStartedVtxoIds(plan, emptyList())
                            }
                            is ArkUnilateralExitPolicy.StartExitPlan.Error -> emptyList()
                        }

                    // Starting only registers the exit. Advance once immediately so the user
                    // does not have to discover and press a separate Push action.
                    runCatching { onchainWallet?.sync() }
                    runCatching { w.syncExits() }
                    // Fee helper is pure (no wallet handle) — avoid taking [mutex] under
                    // [exitOperationMutex] (opposite of maintenance lock order).
                    val feeRate = resolveExitProgressFeeRateSatPerVbLocked().toULong()
                    val statuses =
                        w.progressExits(feeRateSatPerVb = feeRate)
                            .map { it.toArkExitProgress() }
                    runCatching { w.syncExits() }
                    val progressError = resolveExitProgressError(statuses)

                    mutex.withLock {
                        if (wallet !== w) return@withLock
                        refreshStateLocked(sync = false, attemptBoard = false)
                        _lifecycleState.value =
                            when {
                                progressError != null ->
                                    // Exit is registered; surface the push failure so the user can retry.
                                    ArkLifecycleState.Error(progressError)
                                statuses.isNotEmpty() ->
                                    ArkLifecycleState.ExitProgressing(statuses)
                                else ->
                                    ArkLifecycleState.ExitStarted(
                                        vtxoIds = selected,
                                        entireWallet =
                                            ArkUnilateralExitPolicy.markEntireWalletInResult(plan),
                                    )
                            }
                    }
                    if (progressError == null && statuses.isNotEmpty()) {
                        _events.tryEmit(ArkEvent.ExitProgressUpdate(statuses))
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _lifecycleState.value = ArkLifecycleState.Error(publicError(e))
                    // Start may have persisted before a later network call failed.
                    mutex.withLock {
                        if (wallet === w) refreshStateLocked(sync = false, attemptBoard = false)
                    }
                }
            }
        }

    suspend fun progressUnilateralExits(
        feeRateSatPerVb: Long = ArkUnilateralExitPolicy.DEFAULT_EXIT_FEE_RATE_SAT_VB,
    ) =
        withContext(Dispatchers.IO) {
            _lifecycleState.value = ArkLifecycleState.InProgress
            val (w, onchain) =
                mutex.withLock {
                    val current = wallet
                    if (current == null) {
                        null
                    } else {
                        current to onchainWallet
                    }
                } ?: run {
                    _lifecycleState.value =
                        ArkLifecycleState.Error(localizedString(R.string.ark_error_wallet_not_loaded))
                    return@withContext
                }
            if (onchain == null) {
                _lifecycleState.value =
                    ArkLifecycleState.Error(localizedString(R.string.ark_error_onchain_wallet_unavailable))
                return@withContext
            }
            val feeRate = resolveExitProgressFeeRateSatPerVbLocked(feeRateSatPerVb)
            exitOperationMutex.withLock {
                try {
                    // CPFP / package broadcast needs a fresh on-chain UTXO view.
                    runCatching { onchain.sync() }
                        .onFailure {
                            SecureLog.w(TAG, "Ark exit on-chain sync failed: ${publicError(it)}")
                        }
                    runCatching { w.syncExits() }
                    runCatching { w.syncForceExitedVtxos() }
                    val statuses =
                        w.progressExits(feeRateSatPerVb = feeRate.toULong())
                            .map { it.toArkExitProgress() }
                    runCatching { w.syncExits() }
                    val progressError = resolveExitProgressError(statuses)
                    mutex.withLock {
                        if (wallet !== w) return@withLock
                        refreshStateLocked(sync = false, attemptBoard = false)
                        _lifecycleState.value =
                            if (progressError != null) {
                                ArkLifecycleState.Error(progressError)
                            } else {
                                ArkLifecycleState.ExitProgressing(statuses = statuses)
                            }
                    }
                    if (progressError == null) {
                        _events.tryEmit(ArkEvent.ExitProgressUpdate(statuses = statuses))
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _lifecycleState.value = ArkLifecycleState.Error(publicError(e))
                    mutex.withLock {
                        if (wallet === w) refreshStateLocked(sync = false, attemptBoard = false)
                    }
                }
            }
        }

    suspend fun prepareClaimExits(
        destinationAddress: String,
        vtxoIds: List<String> = emptyList(),
        feeRateSatPerVb: Long = ArkUnilateralExitPolicy.DEFAULT_EXIT_FEE_RATE_SAT_VB,
    ) =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val w = wallet
                val claimableFromWallet =
                    if (w != null) {
                        runCatching { w.listClaimableExits().map { it.vtxoId } }.getOrDefault(emptyList())
                    } else {
                        emptyList()
                    }
                when (
                    val plan =
                        ArkUnilateralExitPolicy.planClaimPrepare(
                            walletLoaded = w != null,
                            destinationAddress = destinationAddress,
                            requestedVtxoIds = vtxoIds,
                            claimableVtxoIds = claimableFromWallet,
                            feeRateSatPerVb = feeRateSatPerVb,
                        )
                ) {
                    is ArkUnilateralExitPolicy.ClaimPreparePlan.Error -> {
                        _lifecycleState.value =
                            ArkLifecycleState.Error(
                                when (plan.reason) {
                                    ArkUnilateralExitPolicy.ClaimPrepareError.WALLET_NOT_LOADED ->
                                        localizedString(R.string.ark_error_wallet_not_loaded)
                                    ArkUnilateralExitPolicy.ClaimPrepareError.INVALID_DESTINATION ->
                                        localizedString(R.string.loc_9f80cab8)
                                    ArkUnilateralExitPolicy.ClaimPrepareError.NO_CLAIMABLE_EXITS ->
                                        localizedString(R.string.ark_error_no_claimable_exits)
                                },
                            )
                        return@withLock
                    }
                    is ArkUnilateralExitPolicy.ClaimPreparePlan.Ready -> {
                        checkNotNull(w)
                        _lifecycleState.value = ArkLifecycleState.Loading
                        try {
                            val claim =
                                w.drainExits(
                                    vtxoIds = plan.vtxoIds,
                                    address = plan.destinationAddress,
                                    feeRateSatPerVb = plan.feeRateSatPerVb.toULong(),
                                )
                            _lifecycleState.value =
                                ArkLifecycleState.ClaimPreview(
                                    vtxoIds = plan.vtxoIds,
                                    destinationAddress = plan.destinationAddress,
                                    feeSats = claim.feeSats.toLong(),
                                    feeRateSatPerVb = plan.feeRateSatPerVb,
                                    psbtBase64 = claim.psbtBase64,
                                )
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            _lifecycleState.value = ArkLifecycleState.Error(publicError(e))
                        }
                    }
                }
            }
        }

    /**
     * Sign + broadcast the claim PSBT from [ClaimPreview].
     * @param expectedPsbtBase64 when non-null, must match the preview PSBT exactly (TOCTOU guard).
     */
    suspend fun executeClaimExits(expectedPsbtBase64: String? = null) =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val w = wallet
                val preview = _lifecycleState.value as? ArkLifecycleState.ClaimPreview
                when (
                    ArkUnilateralExitPolicy.planClaimExecute(
                        walletLoaded = w != null,
                        hasClaimPreview = preview != null,
                    )
                ) {
                    ArkUnilateralExitPolicy.ClaimExecuteError.WALLET_NOT_LOADED -> {
                        _lifecycleState.value =
                            ArkLifecycleState.Error(localizedString(R.string.ark_error_wallet_not_loaded))
                        return@withLock
                    }
                    ArkUnilateralExitPolicy.ClaimExecuteError.NOTHING_PREPARED -> {
                        _lifecycleState.value =
                            ArkLifecycleState.Error(localizedString(R.string.ark_error_nothing_prepared))
                        return@withLock
                    }
                    null -> Unit
                }
                checkNotNull(w)
                checkNotNull(preview)
                if (expectedPsbtBase64 != null &&
                    expectedPsbtBase64 != preview.psbtBase64
                ) {
                    _lifecycleState.value =
                        ArkLifecycleState.Error(localizedString(R.string.ark_error_claim_preview_stale))
                    return@withLock
                }
                // Re-validate destination shape before signing (defense in depth).
                if (!ArkUnilateralExitPolicy.isUsableBitcoinClaimAddress(preview.destinationAddress)) {
                    _lifecycleState.value =
                        ArkLifecycleState.Error(localizedString(R.string.loc_9f80cab8))
                    return@withLock
                }
                _lifecycleState.value = ArkLifecycleState.InProgress
                try {
                    val claimAmount =
                        runCatching {
                            w.listClaimableExits()
                                .filter { it.vtxoId in preview.vtxoIds }
                                .sumOf { it.amountSats.toLong() }
                        }.getOrDefault(0L)
                    val signed = w.signExitClaimInputs(preview.psbtBase64)
                    val txid = w.broadcastTx(signed)
                    val walletId = _loadedWalletId.value
                    if (walletId != null) {
                        secureStorage.saveArkExitClaimHistory(
                            walletId = walletId,
                            claim =
                                ArkExitClaimHistory(
                                    txid = txid,
                                    destinationAddress = preview.destinationAddress,
                                    amountSats = (claimAmount - preview.feeSats).coerceAtLeast(0L),
                                    feeSats = preview.feeSats,
                                    vtxoIds = preview.vtxoIds,
                                    createdAt = Instant.now().toString(),
                                ),
                        )
                    }
                    _lifecycleState.value = ArkLifecycleState.Completed(detail = txid)
                    refreshStateLocked(sync = true)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _lifecycleState.value = ArkLifecycleState.Error(publicError(e))
                }
            }
        }

    /**
     * Snapshot Bark per-wallet data dir (db.sqlite + onchain) as base64 encrypted payload for full backup.
     * Returns null when the directory is missing or empty.
     */
    fun exportWalletDataBase64(walletId: String): String? {
        val payload = zipWalletDataBytes(walletId) ?: return null
        return android.util.Base64.encodeToString(payload, android.util.Base64.NO_WRAP)
    }

    /**
     * Encrypted Bark session snapshot for standalone external file export.
     * Must zip **while loaded** — the session dir is deleted on unload.
     * Durable copies live only on the user-chosen SAF path (Backup tab / auto-backup).
     */
    suspend fun exportWalletDataZipBytes(walletId: String): ByteArray? =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                if (_loadedWalletId.value != walletId || wallet == null) {
                    // No live session to snapshot; external SAF is the durable store.
                    return@withLock null
                }
                zipWalletDataBytes(walletId)
            }
        }

    fun getAutoDbBackupLastMs(walletId: String): Long = secureStorage.getArkAutoDbBackupLastMs(walletId)

    /** Metadata for the newest external auto-backup (from last successful write + folder scan). */
    fun getLatestAutoDbBackupInfo(walletId: String): ArkAutoDbBackupInfo? {
        if (walletId.isBlank()) return null
        val folderUri = secureStorage.getArkAutoDbBackupFolderUri() ?: return storedAutoDbBackupInfo(walletId)
        val scanned = scanExternalLatestBackup(walletId, folderUri)
        if (scanned != null) return scanned
        return storedAutoDbBackupInfo(walletId)
    }

    private fun storedAutoDbBackupInfo(walletId: String): ArkAutoDbBackupInfo? {
        val ms = secureStorage.getArkAutoDbBackupLastMs(walletId)
        val name = secureStorage.getArkAutoDbBackupLastFileName(walletId)
        if (ms <= 0L || name.isNullOrBlank()) return null
        val count = secureStorage.getArkAutoDbBackupLastCount(walletId).coerceAtLeast(1)
        return ArkAutoDbBackupInfo(
            fileName = name,
            sizeBytes = secureStorage.getArkAutoDbBackupLastSizeBytes(walletId),
            timestampMs = ms,
            snapshotCount = count,
            hasBackupCopy = count >= 2,
        )
    }

    /**
     * Restore Bark DB from the latest zip in the linked external auto-backup folder.
     * Unloads first when loaded. Caller should reload Ark after.
     */
    suspend fun restoreLatestAutoDbBackup(walletId: String) =
        withContext(Dispatchers.IO) {
            val folderUriRaw =
                secureStorage.getArkAutoDbBackupFolderUri()
                    ?: error(localizedString(R.string.ark_db_auto_backup_folder_required))
            val docs = listExternalBackupDocs(walletId, folderUriRaw)
            val candidates =
                listOfNotNull(
                    docs.firstOrNull { it.fileName.equals(AUTO_BACKUP_LATEST_NAME, ignoreCase = true) },
                    docs.firstOrNull { it.fileName.equals(AUTO_BACKUP_BACKUP_NAME, ignoreCase = true) },
                ) +
                    docs
                        .filter { isCommittedAutoBackupName(it.fileName).not() }
                        .sortedByDescending { it.modifiedMs }
            var lastError: Exception? = null
            for (candidate in candidates) {
                val bytes =
                    runCatching {
                        context.contentResolver.openInputStream(candidate.docUri)?.use { it.readBytes() }
                    }.getOrNull()
                if (bytes == null || !isValidAutoBackupPayload(bytes)) {
                    lastError = Exception(localizedString(R.string.ark_db_import_empty))
                    continue
                }
                try {
                    importWalletDataZipBytes(walletId, bytes)
                    return@withContext
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    lastError = e
                }
            }
            throw lastError
                ?: Exception(localizedString(R.string.ark_db_auto_backup_none))
        }

    /**
     * Restore Bark per-wallet data dir from a base64 zip produced by [exportWalletDataBase64].
     * Unloads the wallet first when it matches [walletId].
     */
    suspend fun importWalletDataBase64(walletId: String, base64: String) =
        withContext(Dispatchers.IO) {
            val bytes = android.util.Base64.decode(base64, android.util.Base64.DEFAULT)
            importWalletDataZipBytes(walletId, bytes)
        }

    /**
     * Restore Bark data from an external backup zip into a **session** dir (cache only).
     * Unloads first when loaded. Caller should reload Ark after — [loadWallet] will open
     * the pending import session (mailbox/ASP hydrate still runs when reachable).
     *
     * Durable copies remain only on the user SAF folder; nothing is written under filesDir.
     */
    suspend fun importWalletDataZipBytes(
        walletId: String,
        zipBytes: ByteArray,
    ) = withContext(Dispatchers.IO) {
        // Supersede any in-flight load so it bails after its next generation check instead of
        // holding [mutex] through ASP recover while import waits.
        loadGeneration.incrementAndGet()
        var installedDir: File? = null
        try {
            val seed = resolveBip39Seed(walletId)
            val plainZip =
                try {
                    ArkBackupCrypto.unwrapIfEncrypted(zipBytes, seed)
                } catch (e: ArkBackupCrypto.WrongWalletException) {
                    throw Exception(localizedString(R.string.ark_db_import_wrong_wallet))
                } catch (e: ArkBackupCrypto.InvalidPayloadException) {
                    throw Exception(localizedString(R.string.ark_db_import_invalid))
                } finally {
                    seed.fill(0)
                }
            if (!ArkWalletDataPack.isValidZipStructure(plainZip)) {
                throw Exception(localizedString(R.string.ark_db_import_invalid))
            }
            // Detach (and dispose outside mutex) until no live handles, then install into session.
            repeat(4) { attempt ->
                val stale =
                    mutex.withLock {
                        if (wallet != null || _loadedWalletId.value == walletId) {
                            // Never re-write pre-import history into SecureStorage during unload —
                            // that race re-paints ghost txs after the imported DB is installed.
                            unloadLocked(disposeHandles = false, persistCache = false)
                        } else {
                            null
                        }
                    }
                    if (stale != null) {
                    disposeBarkHandles(stale.first, stale.second)
                    stopEsploraRelays()
                }
                val installed =
                    mutex.withLock {
                        if (wallet != null || _loadedWalletId.value == walletId) {
                            false
                        } else {
                            val dir = createEmptySessionDataDir(walletId, loadGeneration.get())
                            installedDir = dir
                            installImportedArkDbLocked(walletId, dir, plainZip)
                            true
                        }
                    }
                if (installed) return@withContext
                SecureLog.w(TAG, "Ark DB import retry detach attempt ${attempt + 1}")
            }
            throw Exception(localizedString(R.string.ark_db_import_failed))
        } catch (e: CancellationException) {
            deleteSessionDataDir(installedDir)
            throw e
        } catch (e: Exception) {
            deleteSessionDataDir(installedDir)
            mutex.withLock {
                if (pendingImportSessionDir == installedDir) {
                    pendingImportSessionDir = null
                    pendingImportWalletId = null
                }
            }
            SecureLog.w(TAG, "Ark data import failed")
            throw e
        }
    }

    /** Caller holds [mutex]; wallet handles must already be null. */
    private fun installImportedArkDbLocked(
        walletId: String,
        dir: File,
        plainZip: ByteArray,
    ) {
        // Extract Ibis history before install (sidecar is not written into Bark datadir).
        val importedMovements =
            ArkWalletDataPack.readHistoryJson(plainZip)
                ?.let { secureStorage.decodeArkMovementsFromBackup(it) }
                .orEmpty()
        ArkWalletDataPack.installAtomically(dir, plainZip)
        markMailboxScanned(dir)
        // Disaster restore into session dir — next load opens this path (stable wallet path preferred).
        // ASP/mailbox still hydrates when reachable; external SAF remains the durable copy.
        deleteSessionDataDir(pendingImportSessionDir)
        pendingImportSessionDir = dir
        pendingImportWalletId = walletId
        // Prefer history embedded in the zip; legacy zips without sidecar clear the cache.
        val orderedImport = ArkDepositPolicy.sortMovementsChronologically(importedMovements)
        if (orderedImport.isNotEmpty()) {
            secureStorage.saveArkWalletStateCache(
                walletId,
                ArkWalletState(
                    walletId = walletId,
                    isInitialized = true,
                    movements = orderedImport,
                    lastSyncTimestamp = System.currentTimeMillis(),
                ),
            )
        } else {
            secureStorage.clearArkWalletStateCache(walletId)
        }
        aspHydratedWalletId = null
        sessionDataDir = null
        sessionWalletId = null
        _arkState.value =
            ArkWalletState(
                walletId = walletId,
                isInitialized = true,
                isSyncing = true,
                aspHydrated = false,
                movements = orderedImport,
            )
        _arkMovementLabels.value = emptyMap()
        // Destinations / funding txids are app-side annotations keyed by movement id — they can
        // mis-attach onto the imported DB's different id space and invent peer/txid details.
        secureStorage.clearArkMovementDestinations(walletId)
        secureStorage.clearArkExitClaimHistory(walletId)
        secureStorage.setArkFundingTxids(walletId, emptyList())
        secureStorage.markArkAutoDbBackupSuppressOnce(walletId)
        lastAutoDbBackupFingerprint = null
        autoDbBackupJob?.cancel()
        autoDbBackupJob = null
        pendingSendDestination = null
        pendingSendAmountSats = null
        pendingSendKind = null
    }

    /**
     * Build encrypted Bark snapshot for [walletId] from the **live session dir**.
     * Must be called while the wallet is loaded (export / auto-backup under mutex).
     */
    private fun zipWalletDataBytes(walletId: String): ByteArray? {
        val dir = activeSessionDataDir(walletId) ?: return null
        val seed = runCatching { resolveBip39Seed(walletId) }.getOrNull() ?: return null
        return try {
            val state =
                _arkState.value.takeIf { it.walletId == walletId }
                    ?: secureStorage.getArkWalletStateCache(walletId)
            val movements = state?.movements.orEmpty()
            val manifest =
                ArkWalletDataPack.Manifest(
                    seedFingerprint = ArkBackupCrypto.seedFingerprint(seed),
                    walletId = walletId,
                    movementCount = movements.size,
                    maxMovementId = movements.maxOfOrNull { it.id } ?: 0,
                    spendableSats = state?.spendableSats ?: 0L,
                    chainTipHeight = state?.chainTipHeight?.toLong() ?: 0L,
                    createdAtMs = System.currentTimeMillis(),
                )
            // Mailbox restores VTXOs only — embed Ibis history so disaster restore keeps txs.
            val historyJson =
                movements
                    .takeIf { it.isNotEmpty() }
                    ?.let { secureStorage.encodeArkMovementsForBackup(it) }
            val zip =
                ArkWalletDataPack.zipDirectory(
                    dir = dir,
                    manifest = manifest,
                    historyJson = historyJson,
                ) ?: return null
            ArkBackupCrypto.encrypt(zip, seed)
        } catch (e: Exception) {
            SecureLog.w(TAG, "Ark data export failed")
            null
        } finally {
            seed.fill(0)
        }
    }

    /** BIP39 64-byte seed for [walletId] (mnemonic + optional passphrase). Caller must zero. */
    private fun resolveBip39Seed(walletId: String): ByteArray {
        val raw =
            secureStorage.getMnemonic(walletId)
                ?: error(localizedString(R.string.ark_error_wallet_not_loaded))
        val mnemonic =
            raw
                .trim()
                .lowercase()
                .split(Regex("\\s+"))
                .filter { it.isNotBlank() }
                .joinToString(" ")
        if (mnemonic.isBlank()) error(localizedString(R.string.ark_error_wallet_not_loaded))
        val passphrase = secureStorage.getPassphrase(walletId)
        return ElectrumSeedUtil.bip39MnemonicToSeed(mnemonic, passphrase)
    }

    /**
     * Fingerprint of Bark state that should trigger a new auto-backup when it changes
     * (VTXO set, movements, balances). Labels alone do not change this.
     */
    private fun arkStateBackupFingerprint(state: ArkWalletState): String {
        val vtxoPart =
            state.vtxos
                .asSequence()
                .map { "${it.id}:${it.amountSats}:${it.state}:${it.expiryHeight}" }
                .sorted()
                .joinToString("|")
        val movementPart =
            state.movements
                .asSequence()
                .map {
                    "${it.id}:${it.status}:${it.effectiveBalanceSats}:${it.offchainFeeSats}:" +
                        "${it.updatedAt}:${it.completedAt.orEmpty()}"
                }
                .sorted()
                .joinToString("|")
        val exitPart =
            state.exitVtxos
                .asSequence()
                .map { "${it.vtxoId}:${it.state}:${it.isClaimable}:${it.amountSats}" }
                .sorted()
                .joinToString("|")
        return listOf(
            state.walletId.orEmpty(),
            state.spendableSats.toString(),
            state.pendingInRoundSats.toString(),
            state.pendingBoardSats.toString(),
            state.pendingExitSats.toString(),
            state.pendingLightningSendSats.toString(),
            state.claimableLightningReceiveSats.toString(),
            state.onchainConfirmedSats.toString(),
            state.onchainPendingSats.toString(),
            vtxoPart,
            movementPart,
            exitPart,
        ).joinToString("§")
    }

    /**
     * Debounced auto-snapshot after VTXO/movement activity → external folder only.
     * No folder linked or toggle off → no-op. Snapshot holds the Bark mutex so
     * concurrent sends/maintenance cannot interleave with the file copy; manual
     * export still unloads for a hard-quiesced SQLite.
     *
     * First observation after load/import only baselines the fingerprint (no write) when
     * a valid external latest already exists, or after an explicit DB import/restore.
     * Empty wallets never auto-backup. Payload is seed-encrypted ([ArkBackupCrypto]).
     */
    private fun scheduleAutoDbBackup(
        walletId: String,
        fingerprint: String,
    ) {
        if (!secureStorage.isArkAutoDbBackupEnabled()) return
        if (secureStorage.getArkAutoDbBackupFolderUri().isNullOrBlank()) return
        if (walletId.isBlank()) return
        if (fingerprint == lastAutoDbBackupFingerprint) return
        // Empty Bark state: nothing useful to snapshot (seed-only / pre-fund).
        if (isEmptyAutoBackupFingerprint(fingerprint)) {
            lastAutoDbBackupFingerprint = fingerprint
            return
        }
        // Post-import / full-backup restore: adopt state as baseline without rewriting folder.
        if (secureStorage.consumeArkAutoDbBackupSuppressOnce(walletId)) {
            lastAutoDbBackupFingerprint = fingerprint
            return
        }
        // First observation this session with external latest already present: baseline only.
        // Avoids re-writing the same VTXO set on every cold start / reload.
        if (lastAutoDbBackupFingerprint == null &&
            hasValidExternalLatestAutoBackup(walletId)
        ) {
            lastAutoDbBackupFingerprint = fingerprint
            return
        }
        autoDbBackupJob?.cancel()
        autoDbBackupJob =
            eventScope.launch {
                delay(AUTO_DB_BACKUP_DEBOUNCE_MS)
                if (!secureStorage.isArkAutoDbBackupEnabled()) return@launch
                if (secureStorage.getArkAutoDbBackupFolderUri().isNullOrBlank()) return@launch
                if (_loadedWalletId.value != walletId) return@launch
                if (fingerprint == lastAutoDbBackupFingerprint) return@launch
                if (!autoDbBackupRunning.compareAndSet(false, true)) return@launch
                try {
                    runAutoDbBackup(walletId, fingerprint)
                } finally {
                    autoDbBackupRunning.set(false)
                }
            }
    }

    /** Fingerprint with no VTXOs/movements/balances — skip external auto-backup. */
    private fun isEmptyAutoBackupFingerprint(fingerprint: String): Boolean {
        // arkStateBackupFingerprint joins walletId + numeric balances + empty list parts.
        val parts = fingerprint.split('§')
        if (parts.size < 9) return false
        // indices 1..8 are balance fields (spendable … onchainPending)
        val balancesEmpty = parts.subList(1, 9).all { it == "0" }
        val listsEmpty = parts.drop(9).all { it.isEmpty() }
        return balancesEmpty && listsEmpty
    }

    private fun hasValidExternalLatestAutoBackup(walletId: String): Boolean {
        val folderUri = secureStorage.getArkAutoDbBackupFolderUri() ?: return false
        val latest = findLatestExternalBackup(walletId, folderUri) ?: return false
        if (!latest.fileName.equals(AUTO_BACKUP_LATEST_NAME, ignoreCase = true) &&
            !isCommittedAutoBackupName(latest.fileName)
        ) {
            return false
        }
        return isDocumentValidAutoBackupZip(
            context.contentResolver,
            latest.docUri,
            latest.sizeBytes,
        )
    }

    private suspend fun runAutoDbBackup(
        walletId: String,
        fingerprint: String,
    ) {
        val folderUriRaw = secureStorage.getArkAutoDbBackupFolderUri() ?: return
        // Hold the Bark mutex during the file snapshot so maintenance/send cannot
        // mutate SQLite mid-copy. Manual export still unloads for a harder quiesce.
        val zip =
            mutex.withLock {
                if (_loadedWalletId.value != walletId && _loadedWalletId.value != null) {
                    return
                }
                zipWalletDataBytes(walletId)
            } ?: return
        if (zip.isEmpty()) return
        val now = System.currentTimeMillis()
        try {
            val writeResult =
                writeExternalAutoBackup(walletId, folderUriRaw, zip)
                    ?: error(localizedString(R.string.ark_db_auto_backup_failed))
            lastAutoDbBackupFingerprint = fingerprint
            val count = writeResult.snapshotCount.coerceAtLeast(1)
            val info =
                ArkAutoDbBackupInfo(
                    fileName = AUTO_BACKUP_LATEST_NAME,
                    sizeBytes = zip.size.toLong(),
                    timestampMs = now,
                    snapshotCount = count,
                    hasBackupCopy = count >= 2 || writeResult.hasBackupCopy,
                )
            secureStorage.setArkAutoDbBackupLastInfo(
                walletId = walletId,
                timestampMs = now,
                fileName = AUTO_BACKUP_LATEST_NAME,
                sizeBytes = zip.size.toLong(),
                snapshotCount = if (info.hasBackupCopy) 2 else count,
            )
            _events.tryEmit(ArkEvent.ArkDbAutoBackedUp(info = info))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            SecureLog.w(TAG, "Ark auto DB backup failed: ${publicError(e)}")
            _events.tryEmit(
                ArkEvent.ArkDbAutoBackupFailed(
                    message = publicError(e).ifBlank { localizedString(R.string.ark_db_auto_backup_failed) },
                ),
            )
        }
    }

    private data class ExternalBackupWriteResult(
        val snapshotCount: Int,
        val hasBackupCopy: Boolean,
    )

    private data class ExternalBackupDoc(
        val docUri: Uri,
        val fileName: String,
        val sizeBytes: Long,
        val modifiedMs: Long,
    )

    /**
     * Durable two-slot external backup (mirror):
     * 1. Write full zip to a temp document (fsync + size/zip verify)
     * 2. Promote temp → latest
     * 3. Write the same bytes to backup (copy of latest)
     * Crash/interrupt during (1) leaves previous latest/backup untouched.
     * [AUTO_BACKUP_BACKUP_NAME] is always a second copy of the current latest, not an older generation.
     */
    private fun writeExternalAutoBackup(
        walletId: String,
        folderUriRaw: String,
        zip: ByteArray,
    ): ExternalBackupWriteResult? {
        if (!isValidAutoBackupPayload(zip)) return null
        val treeUri = runCatching { folderUriRaw.toUri() }.getOrNull() ?: return null
        if (treeUri == Uri.EMPTY) return null
        val resolver = context.contentResolver
        val docId = DocumentsContract.getTreeDocumentId(treeUri)
        val treeDocUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
        val walletDocUri =
            resolveOrCreateAutoBackupWalletDir(resolver, treeDocUri, walletId) ?: return null
        cleanupAutoBackupTempsAndStrays(resolver, walletDocUri)
        // Stage complete payload first — never overwrite latest until this succeeds.
        val tempUri =
            writeDocumentBytesDurable(
                resolver = resolver,
                parentDocUri = walletDocUri,
                fileName = AUTO_BACKUP_LATEST_TMP_NAME,
                bytes = zip,
            ) ?: return null
        try {
            // 1) Commit latest from verified temp
            promoteTempToLatest(resolver, walletDocUri, tempUri, zip)
            // 2) Mirror: backup is a second full copy of the same bytes as latest
            val backupOk =
                writeDocumentBytesDurable(
                    resolver = resolver,
                    parentDocUri = walletDocUri,
                    fileName = AUTO_BACKUP_BACKUP_NAME,
                    bytes = zip,
                ) != null
            if (!backupOk) {
                SecureLog.w(TAG, "Ark auto-backup mirror (backup copy) failed; latest is intact")
            }
        } catch (e: Exception) {
            runCatching { DocumentsContract.deleteDocument(resolver, tempUri) }
            throw e
        } finally {
            cleanupAutoBackupTempsAndStrays(resolver, walletDocUri, keepManaged = true)
        }
        val committed =
            listZipChildren(resolver, walletDocUri)
                .filter { isCommittedAutoBackupName(it.fileName) }
        val hasBackup =
            committed.any { it.fileName.equals(AUTO_BACKUP_BACKUP_NAME, ignoreCase = true) }
        return ExternalBackupWriteResult(
            snapshotCount = committed.size.coerceAtLeast(1),
            hasBackupCopy = hasBackup,
        )
    }

    /**
     * Write [bytes] to [fileName] under [parentDocUri] with fsync and post-checks.
     * Replaces any existing same-name child only after the new payload is verified.
     */
    private fun writeDocumentBytesDurable(
        resolver: android.content.ContentResolver,
        parentDocUri: Uri,
        fileName: String,
        bytes: ByteArray,
    ): Uri? {
        if (bytes.isEmpty() || !isValidAutoBackupPayload(bytes)) return null
        // Unique staging name avoids clobbering a live file mid-write.
        val stagingName =
            if (fileName.endsWith(".tmp.zip", ignoreCase = true)) {
                fileName
            } else {
                "$fileName.partial"
            }
        deleteChildrenNamed(resolver, parentDocUri, stagingName)
        val stagingUri =
            DocumentsContract.createDocument(
                resolver,
                parentDocUri,
                "application/zip",
                stagingName,
            ) ?: return null
        val writtenOk =
            runCatching {
                writeAndSyncDocument(resolver, stagingUri, bytes)
            }.getOrDefault(false)
        if (!writtenOk) {
            runCatching { DocumentsContract.deleteDocument(resolver, stagingUri) }
            return null
        }
        // Final name: rename staging → target when needed.
        if (stagingName.equals(fileName, ignoreCase = true)) {
            return stagingUri
        }
        deleteChildrenNamed(resolver, parentDocUri, fileName)
        val renamed =
            runCatching {
                DocumentsContract.renameDocument(resolver, stagingUri, fileName)
            }.getOrNull()
        if (renamed != null) return renamed
        // Rename unsupported: write target directly (still fsync), then drop staging.
        deleteChildrenNamed(resolver, parentDocUri, fileName)
        val targetUri =
            DocumentsContract.createDocument(
                resolver,
                parentDocUri,
                "application/zip",
                fileName,
            )
        if (targetUri == null) {
            runCatching { DocumentsContract.deleteDocument(resolver, stagingUri) }
            return null
        }
        val targetOk =
            runCatching { writeAndSyncDocument(resolver, targetUri, bytes) }.getOrDefault(false)
        runCatching { DocumentsContract.deleteDocument(resolver, stagingUri) }
        if (!targetOk) {
            runCatching { DocumentsContract.deleteDocument(resolver, targetUri) }
            return null
        }
        return targetUri
    }

    private fun writeAndSyncDocument(
        resolver: android.content.ContentResolver,
        docUri: Uri,
        bytes: ByteArray,
    ): Boolean {
        val pfd =
            resolver.openFileDescriptor(docUri, "w")
                ?: return false
        pfd.use { descriptor ->
            FileOutputStream(descriptor.fileDescriptor).use { fos ->
                fos.write(bytes)
                fos.flush()
                // Force kernel buffers to storage before we treat the write as durable.
                runCatching { fos.fd.sync() }
            }
        }
        val size = queryDocumentSize(resolver, docUri)
        if (size != null && size != bytes.size.toLong()) {
            return false
        }
        // Re-read head to confirm provider stored payload magic (encrypted or legacy zip).
        val head =
            runCatching {
                resolver.openInputStream(docUri)?.use { input ->
                    val buf = ByteArray(8)
                    val n = input.read(buf)
                    if (n < 4) null else buf.copyOf(n)
                }
            }.getOrNull()
        return head != null && isValidAutoBackupPayloadHead(head)
    }

    private fun isValidAutoBackupPayloadHead(head: ByteArray): Boolean {
        if (head.size >= 8) {
            val magic = ArkBackupCrypto.MAGIC.toByteArray(Charsets.US_ASCII)
            var match = true
            for (i in magic.indices) {
                if (head[i] != magic[i]) {
                    match = false
                    break
                }
            }
            if (match) return true
        }
        return ArkWalletDataPack.isZipMagic(head)
    }

    private fun promoteTempToLatest(
        resolver: android.content.ContentResolver,
        walletDocUri: Uri,
        tempUri: Uri,
        zip: ByteArray,
    ) {
        deleteChildrenNamed(resolver, walletDocUri, AUTO_BACKUP_LATEST_NAME)
        val renamed =
            runCatching {
                DocumentsContract.renameDocument(
                    resolver,
                    tempUri,
                    AUTO_BACKUP_LATEST_NAME,
                )
            }.getOrNull()
        if (renamed != null) return
        // Fallback: durable write of in-memory zip (already verified), then drop temp.
        val latestUri =
            writeDocumentBytesDurable(
                resolver = resolver,
                parentDocUri = walletDocUri,
                fileName = AUTO_BACKUP_LATEST_NAME,
                bytes = zip,
            )
        runCatching { DocumentsContract.deleteDocument(resolver, tempUri) }
        if (latestUri == null) {
            error("Failed to promote Ark auto-backup temp to latest")
        }
    }

    private fun cleanupAutoBackupTempsAndStrays(
        resolver: android.content.ContentResolver,
        walletDocUri: Uri,
        keepManaged: Boolean = false,
    ) {
        listZipChildren(resolver, walletDocUri).forEach { child ->
            val name = child.fileName
            val isTemp =
                name.endsWith(".tmp.zip", ignoreCase = true) ||
                    name.endsWith(".partial", ignoreCase = true) ||
                    name.equals(AUTO_BACKUP_LATEST_TMP_NAME, ignoreCase = true)
            val isCommitted = isCommittedAutoBackupName(name)
            val drop =
                when {
                    isTemp -> true
                    keepManaged && isCommitted -> false
                    !isCommitted -> true // legacy timestamped names
                    else -> false
                }
            if (drop) {
                runCatching { DocumentsContract.deleteDocument(resolver, child.docUri) }
            }
        }
    }

    private fun deleteChildrenNamed(
        resolver: android.content.ContentResolver,
        parentDocUri: Uri,
        fileName: String,
    ) {
        listZipChildren(resolver, parentDocUri)
            .filter { it.fileName.equals(fileName, ignoreCase = true) }
            .forEach { child ->
                runCatching { DocumentsContract.deleteDocument(resolver, child.docUri) }
            }
    }

    private fun queryDocumentSize(
        resolver: android.content.ContentResolver,
        docUri: Uri,
    ): Long? =
        runCatching {
            resolver
                .query(
                    docUri,
                    arrayOf(DocumentsContract.Document.COLUMN_SIZE),
                    null,
                    null,
                    null,
                )?.use { cursor ->
                    if (!cursor.moveToFirst()) return@use null
                    val idx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
                    if (idx < 0) null else cursor.getLong(idx)
                }
        }.getOrNull()

    /** Accept encrypted Ark payloads or legacy plaintext zip snapshots. */
    private fun isValidAutoBackupPayload(bytes: ByteArray): Boolean {
        if (bytes.isEmpty()) return false
        if (ArkBackupCrypto.isEncrypted(bytes)) {
            return bytes.size >= ArkBackupCrypto.MIN_ENCRYPTED_SIZE
        }
        return ArkWalletDataPack.isValidZipStructure(bytes)
    }

    private fun isDocumentValidAutoBackupZip(
        resolver: android.content.ContentResolver,
        docUri: Uri,
        reportedSize: Long,
    ): Boolean {
        if (reportedSize in 1 until 22 && reportedSize < ArkBackupCrypto.MIN_ENCRYPTED_SIZE) {
            return false
        }
        return runCatching {
            resolver.openInputStream(docUri)?.use { input ->
                val bytes = input.readBytes()
                isValidAutoBackupPayload(bytes)
            } ?: false
        }.getOrDefault(false)
    }

    private fun isCommittedAutoBackupName(fileName: String): Boolean {
        val n = fileName.trim()
        return n.equals(AUTO_BACKUP_LATEST_NAME, ignoreCase = true) ||
            n.equals(AUTO_BACKUP_BACKUP_NAME, ignoreCase = true)
    }

    private fun isManagedAutoBackupName(fileName: String): Boolean {
        val n = fileName.trim()
        return isCommittedAutoBackupName(n) ||
            n.equals(AUTO_BACKUP_LATEST_TMP_NAME, ignoreCase = true) ||
            n.endsWith(".tmp.zip", ignoreCase = true) ||
            n.endsWith(".partial", ignoreCase = true)
    }

    private fun scanExternalLatestBackup(
        walletId: String,
        folderUriRaw: String,
    ): ArkAutoDbBackupInfo? {
        val docs = listExternalBackupDocs(walletId, folderUriRaw)
        val latest = findPreferredExternalBackup(docs) ?: return null
        val hasBackup =
            docs.any { it.fileName.equals(AUTO_BACKUP_BACKUP_NAME, ignoreCase = true) }
        val count =
            docs.count { isCommittedAutoBackupName(it.fileName) }.coerceAtLeast(1)
        return ArkAutoDbBackupInfo(
            fileName = latest.fileName,
            sizeBytes = latest.sizeBytes,
            timestampMs = latest.modifiedMs,
            snapshotCount = count,
            hasBackupCopy = hasBackup,
        )
    }

    private fun findLatestExternalBackup(
        walletId: String,
        folderUriRaw: String,
    ): ExternalBackupDoc? =
        findPreferredExternalBackup(listExternalBackupDocs(walletId, folderUriRaw))

    /** Prefer labelled latest, then labelled backup, then newest zip (legacy). */
    private fun findPreferredExternalBackup(docs: List<ExternalBackupDoc>): ExternalBackupDoc? {
        docs.firstOrNull { it.fileName.equals(AUTO_BACKUP_LATEST_NAME, ignoreCase = true) }
            ?.let { return it }
        docs.firstOrNull { it.fileName.equals(AUTO_BACKUP_BACKUP_NAME, ignoreCase = true) }
            ?.let { return it }
        return docs
            .filter { isCommittedAutoBackupName(it.fileName) || it.fileName.endsWith(".zip", true) }
            .filter { !it.fileName.contains(".tmp", ignoreCase = true) }
            .filter { !it.fileName.endsWith(".partial", ignoreCase = true) }
            .maxByOrNull { it.modifiedMs }
    }

    private fun listExternalBackupDocs(
        walletId: String,
        folderUriRaw: String,
    ): List<ExternalBackupDoc> {
        val treeUri = runCatching { folderUriRaw.toUri() }.getOrNull() ?: return emptyList()
        if (treeUri == Uri.EMPTY) return emptyList()
        return try {
            val resolver = context.contentResolver
            val docId = DocumentsContract.getTreeDocumentId(treeUri)
            val treeDocUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
            val walletDocUri =
                findAutoBackupWalletDir(resolver, treeDocUri, walletId) ?: return emptyList()
            listZipChildren(resolver, walletDocUri)
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Human-readable per-wallet folder: `ibis-ark-{WalletName}-{shortId}`.
     * shortId keeps uniqueness if two wallets share a name.
     */
    private fun autoBackupWalletFolderName(walletId: String): String {
        val rawName =
            secureStorage.getWalletMetadata(walletId)?.name?.trim().orEmpty()
        val safeName = sanitizeAutoBackupFolderLabel(rawName).ifBlank { "wallet" }
        val shortId =
            walletId
                .replace("-", "")
                .take(8)
                .ifBlank { walletId.take(8) }
        return "ibis-ark-$safeName-$shortId"
    }

    private fun legacyAutoBackupWalletFolderName(walletId: String): String = "ibis-ark-$walletId"

    private fun sanitizeAutoBackupFolderLabel(name: String): String {
        val cleaned =
            name
                .map { ch ->
                    when {
                        ch.isLetterOrDigit() -> ch
                        ch == ' ' || ch == '-' || ch == '_' -> '-'
                        else -> '-'
                    }
                }
                .joinToString("")
                .replace(Regex("-+"), "-")
                .trim('-')
        return cleaned.take(40)
    }

    private fun findAutoBackupWalletDir(
        resolver: android.content.ContentResolver,
        parentDocUri: Uri,
        walletId: String,
    ): Uri? {
        val preferred = autoBackupWalletFolderName(walletId)
        findDocumentDir(resolver, parentDocUri, preferred)?.let { return it }
        // Legacy uuid-only folders from earlier builds.
        findDocumentDir(resolver, parentDocUri, legacyAutoBackupWalletFolderName(walletId))
            ?.let { return it }
        // Match any dir ending with -{shortId} or containing full walletId (renamed manually).
        val shortId = walletId.replace("-", "").take(8)
        return findDocumentDirMatching(resolver, parentDocUri) { name ->
            name.startsWith("ibis-ark-", ignoreCase = true) &&
                (
                    name.endsWith("-$shortId", ignoreCase = true) ||
                        name.contains(walletId, ignoreCase = true)
                )
        }
    }

    private fun resolveOrCreateAutoBackupWalletDir(
        resolver: android.content.ContentResolver,
        parentDocUri: Uri,
        walletId: String,
    ): Uri? {
        findAutoBackupWalletDir(resolver, parentDocUri, walletId)?.let { existing ->
            // Best-effort rename legacy uuid folder → name+shortId when possible.
            val preferred = autoBackupWalletFolderName(walletId)
            val legacy = legacyAutoBackupWalletFolderName(walletId)
            val currentName = queryDocumentDisplayName(resolver, existing)
            if (currentName != null &&
                currentName.equals(legacy, ignoreCase = true) &&
                !currentName.equals(preferred, ignoreCase = true) &&
                findDocumentDir(resolver, parentDocUri, preferred) == null
            ) {
                val renamed =
                    runCatching {
                        DocumentsContract.renameDocument(resolver, existing, preferred)
                    }.getOrNull()
                if (renamed != null) return renamed
            }
            return existing
        }
        return findOrCreateDocumentDir(
            resolver,
            parentDocUri,
            autoBackupWalletFolderName(walletId),
        )
    }

    private fun queryDocumentDisplayName(
        resolver: android.content.ContentResolver,
        docUri: Uri,
    ): String? =
        runCatching {
            resolver
                .query(
                    docUri,
                    arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                    null,
                    null,
                    null,
                )?.use { cursor ->
                    if (!cursor.moveToFirst()) return@use null
                    val idx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    if (idx < 0) null else cursor.getString(idx)?.takeIf { it.isNotBlank() }
                }
        }.getOrNull()

    private fun findDocumentDir(
        resolver: android.content.ContentResolver,
        parentDocUri: Uri,
        displayName: String,
    ): Uri? =
        findDocumentDirMatching(resolver, parentDocUri) { name ->
            name.equals(displayName, ignoreCase = true)
        }

    private fun findDocumentDirMatching(
        resolver: android.content.ContentResolver,
        parentDocUri: Uri,
        predicate: (String) -> Boolean,
    ): Uri? {
        queryChildDocuments(resolver, parentDocUri).use { cursor ->
            if (cursor == null) return null
            val idIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
            while (cursor.moveToNext()) {
                if (idIdx < 0 || nameIdx < 0 || mimeIdx < 0) break
                val name = cursor.getString(nameIdx) ?: continue
                val mime = cursor.getString(mimeIdx) ?: continue
                if (mime == DocumentsContract.Document.MIME_TYPE_DIR && predicate(name)) {
                    val childId = cursor.getString(idIdx) ?: continue
                    return DocumentsContract.buildDocumentUriUsingTree(parentDocUri, childId)
                }
            }
        }
        return null
    }

    private fun listZipChildren(
        resolver: android.content.ContentResolver,
        parentDocUri: Uri,
    ): List<ExternalBackupDoc> {
        val zips = mutableListOf<ExternalBackupDoc>()
        queryChildDocuments(resolver, parentDocUri).use { cursor ->
            if (cursor == null) return emptyList()
            val idIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
            val modIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
            val sizeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
            while (cursor.moveToNext()) {
                if (idIdx < 0 || nameIdx < 0 || mimeIdx < 0) break
                val name = cursor.getString(nameIdx) ?: continue
                val mime = cursor.getString(mimeIdx).orEmpty()
                if (!name.endsWith(".zip", ignoreCase = true) &&
                    !mime.contains("zip", ignoreCase = true)
                ) {
                    continue
                }
                val childId = cursor.getString(idIdx) ?: continue
                val modified = if (modIdx >= 0) cursor.getLong(modIdx) else 0L
                val size = if (sizeIdx >= 0) cursor.getLong(sizeIdx) else 0L
                zips +=
                    ExternalBackupDoc(
                        docUri = DocumentsContract.buildDocumentUriUsingTree(parentDocUri, childId),
                        fileName = name,
                        sizeBytes = size.coerceAtLeast(0L),
                        modifiedMs = modified,
                    )
            }
        }
        return zips
    }

    private fun findOrCreateDocumentDir(
        resolver: android.content.ContentResolver,
        parentDocUri: Uri,
        displayName: String,
    ): Uri? {
        queryChildDocuments(resolver, parentDocUri).use { cursor ->
            if (cursor != null) {
                val idIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                while (cursor.moveToNext()) {
                    if (idIdx < 0 || nameIdx < 0 || mimeIdx < 0) break
                    val name = cursor.getString(nameIdx) ?: continue
                    val mime = cursor.getString(mimeIdx) ?: continue
                    if (name == displayName && mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        val childId = cursor.getString(idIdx) ?: continue
                        return DocumentsContract.buildDocumentUriUsingTree(parentDocUri, childId)
                    }
                }
            }
        }
        return DocumentsContract.createDocument(
            resolver,
            parentDocUri,
            DocumentsContract.Document.MIME_TYPE_DIR,
            displayName,
        )
    }

    private fun queryChildDocuments(
        resolver: android.content.ContentResolver,
        parentDocUri: Uri,
    ): android.database.Cursor? {
        val childrenUri =
            DocumentsContract.buildChildDocumentsUriUsingTree(
                parentDocUri,
                DocumentsContract.getDocumentId(parentDocUri),
            )
        return runCatching {
            resolver.query(
                childrenUri,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE,
                    DocumentsContract.Document.COLUMN_LAST_MODIFIED,
                    DocumentsContract.Document.COLUMN_SIZE,
                ),
                null,
                null,
                null,
            )
        }.getOrNull()
    }

    fun saveMovementLabel(walletId: String, movementId: Int, label: String) {
        secureStorage.setArkMovementLabel(walletId, movementId, label)
        if (_loadedWalletId.value == walletId) {
            _arkMovementLabels.value = secureStorage.getAllArkMovementLabels(walletId)
        }
    }

    fun deleteArkMovementFromHistory(
        walletId: String,
        movementId: Int,
    ) {
        secureStorage.hideArkMovement(walletId, movementId)
        secureStorage.purgeHiddenArkMovementMetadata(walletId, movementId)
        if (_loadedWalletId.value == walletId || _arkState.value.walletId == walletId) {
            val updated =
                filterHiddenArkMovements(
                    walletId,
                    _arkState.value.movements.filterNot { it.id == movementId },
                )
            _arkState.value = _arkState.value.copy(movements = updated)
            _arkMovementLabels.value = secureStorage.getAllArkMovementLabels(walletId)
            secureStorage.saveArkWalletStateCache(walletId, _arkState.value)
        }
    }

    fun deleteAllArkMovementsFromHistory(walletId: String) {
        val ids =
            _arkState.value
                .takeIf { it.walletId == walletId }
                ?.movements
                ?.map { it.id }
                .orEmpty()
                .ifEmpty {
                    secureStorage.getArkWalletStateCache(walletId)?.movements?.map { it.id }.orEmpty()
                }
                .distinct()
        if (ids.isEmpty()) return
        ids.forEach { id ->
            secureStorage.hideArkMovement(walletId, id)
            secureStorage.purgeHiddenArkMovementMetadata(walletId, id)
        }
        if (_loadedWalletId.value == walletId || _arkState.value.walletId == walletId) {
            _arkState.value = _arkState.value.copy(movements = emptyList())
            _arkMovementLabels.value = secureStorage.getAllArkMovementLabels(walletId)
            secureStorage.saveArkWalletStateCache(walletId, _arkState.value)
        }
    }

    private fun filterHiddenArkMovements(
        walletId: String?,
        movements: List<ArkMovement>,
    ): List<ArkMovement> {
        // Drop Bark bookkeeping / unpaid stubs before user-hidden ids.
        val withoutNoise = movements.filterNot { ArkDepositPolicy.isNoiseArkMovement(it) }
        val visible =
            if (walletId.isNullOrBlank() || withoutNoise.isEmpty()) {
                withoutNoise
            } else {
                val hidden = secureStorage.getHiddenArkMovementIds(walletId)
                if (hidden.isEmpty()) {
                    withoutNoise
                } else {
                    // Live unboarded deposits must always show (even if user hid legacy id -1
                    // via "delete all history"). Boarding manage list and history stay in sync.
                    withoutNoise.filterNot { movement ->
                        movement.id in hidden &&
                            !ArkDepositPolicy.isSyntheticPendingOnchainDeposit(movement)
                    }
                }
            }
        // Bark history and synthetic pending deposits are not always chronological;
        // never pin pending above newer confirmed movements.
        return ArkDepositPolicy.sortMovementsChronologically(visible)
    }

    fun saveAddressLabel(walletId: String, address: String, label: String) {
        secureStorage.setArkAddressLabel(walletId, address, label)
        if (_loadedWalletId.value == walletId) {
            _arkAddressLabels.value = secureStorage.getAllArkAddressLabels(walletId)
        }
    }

    fun deleteAddressLabel(walletId: String, address: String) {
        secureStorage.setArkAddressLabel(walletId, address, "")
        if (_loadedWalletId.value == walletId) {
            _arkAddressLabels.value = secureStorage.getAllArkAddressLabels(walletId)
        }
    }

    fun getAllArkMovementLabels(walletId: String): Map<String, String> =
        secureStorage.getAllArkMovementLabels(walletId)

    fun getAllArkAddressLabels(walletId: String): Map<String, String> =
        secureStorage.getAllArkAddressLabels(walletId)

    /** Merge imported address labels (non-blank values overwrite matching keys). */
    fun saveArkAddressLabels(
        walletId: String,
        labels: Map<String, String>,
    ) {
        if (labels.isEmpty()) return
        val merged = secureStorage.getAllArkAddressLabels(walletId).toMutableMap()
        labels.forEach { (address, label) ->
            val trimmed = label.trim()
            val key = address.trim()
            if (trimmed.isNotEmpty() && key.isNotEmpty()) {
                merged[key] = trimmed
            }
        }
        secureStorage.saveArkAddressLabels(walletId, merged)
        if (_loadedWalletId.value == walletId) {
            _arkAddressLabels.value = secureStorage.getAllArkAddressLabels(walletId)
        }
    }

    /**
     * Merge imported movement labels. BIP 329 `tx` refs for Ark are movement ids
     * (decimal strings), not on-chain txids.
     */
    fun saveArkMovementLabels(
        walletId: String,
        labels: Map<String, String>,
    ) {
        if (labels.isEmpty()) return
        val merged = secureStorage.getAllArkMovementLabels(walletId).toMutableMap()
        labels.forEach { (movementId, label) ->
            val trimmed = label.trim()
            val key = movementId.trim()
            if (trimmed.isNotEmpty() && key.isNotEmpty()) {
                merged[key] = trimmed
            }
        }
        secureStorage.saveArkMovementLabels(walletId, merged)
        if (_loadedWalletId.value == walletId) {
            _arkMovementLabels.value = secureStorage.getAllArkMovementLabels(walletId)
            // Refresh movement list labels without a full network sync.
            val current = _arkState.value
            if (current.walletId == walletId && current.movements.isNotEmpty()) {
                val labelMap = secureStorage.getAllArkMovementLabels(walletId)
                _arkState.value =
                    current.copy(
                        movements =
                            current.movements.map { m ->
                                m.copy(label = labelMap[m.id.toString()] ?: m.label)
                            },
                    )
            }
        }
    }

    fun clearWalletDisplayState() {
        val walletId = _loadedWalletId.value ?: _arkState.value.walletId
        _sendState.value = ArkSendState.Idle
        _receiveState.value = ArkReceiveState.Idle
        _transferState.value = ArkTransferState.Idle
        _lifecycleState.value = ArkLifecycleState.Idle
        preparedDestination = null
        preparedAmountSats = null
        preparedMethod = null
        // Keep last-known balance/history offline (cache), same as Spark/L1.
        if (walletId.isNullOrBlank()) {
            _arkState.value = ArkWalletState(isInitialized = true)
            _arkMovementLabels.value = emptyMap()
            _arkAddressLabels.value = emptyMap()
        } else {
            applyDisconnectedArkStateLocked(walletId)
            // Same wallet still active — keep Receive QR from cache while offline.
            primeCachedReceiveStateLocked(walletId)
        }
    }

    /**
     * Eager UI retarget for wallet switches: paint SecureStorage cache for [walletId]
     * immediately so Balance never shows the previous wallet while Bark unload/open runs.
     */
    fun beginConnecting(walletId: String) {
        if (walletId.isBlank()) return
        // Persist the wallet we are leaving before overwriting display state.
        val leavingId = _loadedWalletId.value ?: _arkState.value.walletId
        if (!leavingId.isNullOrBlank() && leavingId != walletId) {
            val live = _arkState.value
            if (
                live.walletId == leavingId &&
                live.isInitialized &&
                live.error == null &&
                (live.movements.isNotEmpty() || live.totalSats > 0L || live.vtxos.isNotEmpty())
            ) {
                secureStorage.saveArkWalletStateCache(
                    leavingId,
                    live.withNoiseMovementsFiltered(leavingId),
                )
            }
        }
        _isConnecting.value = true
        _isConnected.value = false
        _sendState.value = ArkSendState.Idle
        _receiveState.value = ArkReceiveState.Idle
        _transferState.value = ArkTransferState.Idle
        _lifecycleState.value = ArkLifecycleState.Idle
        clearPreparedSendStateLocked()
        pendingSendDestination = null
        pendingSendAmountSats = null
        pendingSendKind = null
        applyLoadingArkStateLocked(walletId)
        primeCachedReceiveStateUnlocked(walletId)
    }

    /** Pill = ASP liveness. Sync spinner = mailbox / hydrate / refresh. */
    private fun markArkServerLiveLocked(walletId: String) {
        _isConnecting.value = false
        _isConnected.value = true
        if (_arkState.value.walletId != walletId) return
        _arkState.value =
            _arkState.value.copy(
                isConnecting = false,
                isConnected = true,
                isSyncing = true,
                error = null,
            )
    }

    fun markLoadFailed(walletId: String, message: String) {
        aspHydratedWalletId = null
        forceStopSyncSpinner()
        _arkState.value =
            ArkWalletState(
                walletId = walletId,
                isInitialized = true,
                aspHydrated = false,
                error = message,
            )
        _isConnected.value = false
        _isConnecting.value = false
    }

    /**
     * Restore last-known Ark balances/movements from SecureStorage (or keep same-wallet memory)
     * so Balance paints immediately while Bark open + sync run.
     */
    private fun applyLoadingArkStateLocked(walletId: String) {
        loadLabelsLocked(walletId)
        val cached = secureStorage.getArkWalletStateCache(walletId)?.withNoiseMovementsFiltered(walletId)
        val inMemory =
            _arkState.value
                .takeIf { it.walletId == walletId && it.isInitialized && it.error == null }
                ?.withNoiseMovementsFiltered(walletId)
        val base =
            cached
                ?: inMemory
                ?: ArkWalletState(
                    walletId = walletId,
                    isInitialized = true,
                    serverAddress = secureStorage.getArkServerAddress(),
                )
        val cachedArkAddress =
            base.currentAddress?.takeIf { it.isNotBlank() }
                ?: secureStorage.getArkReceiveAddress(walletId)?.takeIf { it.isNotBlank() }
        // Cache/local paint only — ASP remains source of truth until hydrate completes.
        aspHydratedWalletId = null
        _arkState.value =
            base.copy(
                walletId = walletId,
                isInitialized = true,
                isConnecting = true,
                isConnected = false,
                isSyncing = true,
                isAutoRefreshing = false,
                aspHydrated = false,
                error = null,
                currentAddress = cachedArkAddress ?: base.currentAddress,
                serverAddress = base.serverAddress ?: secureStorage.getArkServerAddress(),
            )
    }

    /**
     * Publish cached Ark / on-chain deposit addresses into [receiveState] so Receive can show a
     * QR before Bark finishes opening. Validated/rotated once the wallet is connected.
     */
    private fun primeCachedReceiveStateLocked(walletId: String) {
        primeCachedReceiveStateUnlocked(walletId)
    }

    /** Safe without [mutex] — only writes receive/address StateFlows from prefs. */
    private fun primeCachedReceiveStateUnlocked(walletId: String) {
        val ark =
            (
                _arkState.value.currentAddress?.takeIf { it.isNotBlank() }
                    ?: secureStorage.getArkReceiveAddress(walletId)?.takeIf { it.isNotBlank() }
            )?.takeUnless { isAddressUsed(it, walletId = walletId) }
        val bitcoin =
            secureStorage
                .getArkOnchainDepositAddress(walletId)
                ?.takeIf { it.isNotBlank() }
                ?.takeUnless { isAddressUsed(it, walletId = walletId) }
        primeReceiveStateIfNeededLocked(arkAddress = ark, bitcoinAddress = bitcoin)
    }

    /**
     * Promote a known address into Ready when receive UI has nothing useful yet.
     * Does not clobber Lightning invoices, Paid state, or a Ready address of another kind
     * the user is already viewing (tab switch reloads via [receive]).
     */
    private fun primeReceiveStateIfNeededLocked(
        arkAddress: String?,
        bitcoinAddress: String?,
    ) {
        val current = _receiveState.value
        if (current is ArkReceiveState.Paid) return
        if (current is ArkReceiveState.Ready && current.kind == ArkReceiveKind.BOLT11_INVOICE) return

        val preferredArk = arkAddress?.takeIf { it.isNotBlank() }
        val preferredBtc = bitcoinAddress?.takeIf { it.isNotBlank() }

        when (current) {
            is ArkReceiveState.Ready -> {
                when (current.kind) {
                    ArkReceiveKind.ARK_ADDRESS -> {
                        if (preferredArk != null &&
                            !current.paymentRequest.equals(preferredArk, ignoreCase = true)
                        ) {
                            _receiveState.value =
                                ArkReceiveState.Ready(
                                    kind = ArkReceiveKind.ARK_ADDRESS,
                                    paymentRequest = preferredArk,
                                )
                        }
                    }
                    ArkReceiveKind.BITCOIN_ADDRESS -> {
                        if (preferredBtc != null &&
                            !current.paymentRequest.equals(preferredBtc, ignoreCase = true)
                        ) {
                            _receiveState.value =
                                ArkReceiveState.Ready(
                                    kind = ArkReceiveKind.BITCOIN_ADDRESS,
                                    paymentRequest = preferredBtc,
                                )
                        }
                    }
                    ArkReceiveKind.BOLT11_INVOICE -> Unit
                }
            }
            else -> {
                when {
                    preferredArk != null -> {
                        _receiveState.value =
                            ArkReceiveState.Ready(
                                kind = ArkReceiveKind.ARK_ADDRESS,
                                paymentRequest = preferredArk,
                            )
                        if (_arkState.value.currentAddress.isNullOrBlank()) {
                            _arkState.value = _arkState.value.copy(currentAddress = preferredArk)
                        }
                    }
                    preferredBtc != null -> {
                        _receiveState.value =
                            ArkReceiveState.Ready(
                                kind = ArkReceiveKind.BITCOIN_ADDRESS,
                                paymentRequest = preferredBtc,
                            )
                    }
                }
            }
        }
    }

    /**
     * Offline paint after Bark unload: last cache, not empty zeros.
     * @param persistCache when false (DB import), do not write live history back to SecureStorage.
     */
    private fun applyDisconnectedArkStateLocked(
        walletId: String,
        persistCache: Boolean = true,
    ) {
        loadLabelsLocked(walletId)
        val live = _arkState.value
        if (
            persistCache &&
            live.walletId == walletId &&
            live.isInitialized &&
            live.error == null &&
            (live.movements.isNotEmpty() || live.totalSats > 0L || live.vtxos.isNotEmpty())
        ) {
            secureStorage.saveArkWalletStateCache(
                walletId,
                live.withNoiseMovementsFiltered(walletId),
            )
        }
        val cached = secureStorage.getArkWalletStateCache(walletId)?.withNoiseMovementsFiltered(walletId)
        val inMemory =
            live
                .takeIf { it.walletId == walletId && it.isInitialized }
                ?.withNoiseMovementsFiltered(walletId)
        val base =
            if (persistCache) {
                cached ?: inMemory
            } else {
                // Import path: never re-surface pre-import in-memory history.
                cached
            }
                ?: ArkWalletState(
                    walletId = walletId,
                    isInitialized = true,
                    serverAddress = secureStorage.getArkServerAddress(),
                )
        val cachedArkAddress =
            base.currentAddress?.takeIf { it.isNotBlank() }
                ?: secureStorage.getArkReceiveAddress(walletId)?.takeIf { it.isNotBlank() }
        // Offline/cache paint is never ASP-confirmed for this session.
        if (aspHydratedWalletId == walletId) {
            aspHydratedWalletId = null
        }
        _arkState.value =
            base.copy(
                walletId = walletId,
                isInitialized = true,
                isConnecting = false,
                isConnected = false,
                isSyncing = false,
                isAutoRefreshing = false,
                aspHydrated = false,
                error = null,
                currentAddress = cachedArkAddress ?: base.currentAddress,
                serverAddress = base.serverAddress ?: secureStorage.getArkServerAddress(),
            )
    }

    private fun ArkWalletState.withNoiseMovementsFiltered(walletId: String): ArkWalletState {
        if (movements.isEmpty()) return this
        val filtered = filterHiddenArkMovements(walletId, movements)
        return if (filtered === movements || filtered == movements) this else copy(movements = filtered)
    }

    private suspend fun refreshStateLocked(
        sync: Boolean = false,
        attemptBoard: Boolean = false,
    ) {
        val w = wallet ?: return
        val walletId = _loadedWalletId.value ?: return
        val previousNeedsRefresh = _arkState.value.needsRefresh
        val previousRefreshSoon = _arkState.value.refreshSoon
        try {
            if (sync) {
                runCatching { w.sync() }
                runCatching { onchainWallet?.sync() }
                runCatching { w.tryClaimAllLightningReceives(wait = false) }
                runCatching { w.syncExits() }
                runCatching { w.syncPendingBoards() }
            }
            // Auto-board only when opted in (default off). Explicit board uses boardOnchain*.
            if (
                attemptBoard &&
                _isConnected.value &&
                secureStorage.isArkAutoBoardEnabled()
            ) {
                runCatching { boardPendingOnchainFundsLocked(force = false) }
            }
            val balance = w.balance()
            val onchainBalance = runCatching { onchainWallet?.balance() }.getOrNull()
            val previousState = _arkState.value
            // Unsynced paints often read 0 from Bark on-chain before Esplora catches up.
            // Final on-chain buckets are resolved after depositChainDetails (Esplora bridge).
            val liveOnchainConfirmed = onchainBalance?.confirmedSats?.toLong() ?: 0L
            val liveOnchainPending = onchainBalance?.pendingSats?.toLong() ?: 0L
            val history = runCatching { w.history() }.getOrDefault(emptyList())
            val vtxos = listLiveVtxos(w)
            val pendingBoards = runCatching { w.pendingBoards() }.getOrDefault(emptyList())
            val pendingBoardVtxos = runCatching { w.pendingBoardVtxos() }.getOrDefault(emptyList())
            val allVtxos =
                (vtxos + pendingBoardVtxos)
                    .distinctBy { it.id }
            val refreshList = runCatching { w.getVtxosToRefresh() }.getOrDefault(emptyList())
            val expiringSoonList =
                runCatching {
                    w.getExpiringVtxos(ArkWalletState.EXPIRING_SOON_THRESHOLD_BLOCKS.toUInt())
                }.getOrDefault(emptyList())
            val exitList = runCatching { w.getExitVtxos() }.getOrDefault(emptyList())
            val claimableList = runCatching { w.listClaimableExits() }.getOrDefault(emptyList())
            val pendingExits = runCatching { w.hasPendingExits() }.getOrDefault(false)
            val nextRefresh =
                runCatching {
                    w.getNextRequiredRefreshBlockheight()?.toInt()?.takeIf { it > 0 }
                }.getOrNull()
            val firstExpiring =
                runCatching {
                    w.getFirstExpiringVtxoBlockheight()?.toInt()?.takeIf { it > 0 }
                }.getOrNull()
            val tipHeight = resolveChainTipHeight()
            if (tipHeight != null) lastKnownChainTipHeight = tipHeight
            val fingerprint = runCatching { w.fingerprint() }.getOrNull()
            val labels = secureStorage.getAllArkMovementLabels(walletId)
            val destinations = secureStorage.getAllArkMovementDestinations(walletId)
            val mappedRefresh = refreshList.map { it.toArkVtxo() }
            val mappedExpiring =
                expiringSoonList
                    .map { it.toArkVtxo() }
                    .filter { soon -> mappedRefresh.none { it.id == soon.id } }
                    // Advance past used receive addresses before publishing state / receive UI.
            val address =
                resolveUnusedArkAddressLocked(w, walletId, forceNew = false)
                    ?: _arkState.value.currentAddress
                    ?: runCatching { w.newAddress() }.getOrNull()?.also { fresh ->
                        secureStorage.setArkReceiveAddress(walletId, fresh)
                    }
            val bitcoinDeposit =
                runCatching { resolveUnusedBitcoinDepositAddressLocked(forceNew = false) }.getOrNull()
            val arkInfo = runCatching { w.arkInfo() }.getOrNull()
            val requiredBoardConfirmations =
                arkInfo?.requiredBoardConfirmations?.toInt()?.takeIf { it > 0 }
                    ?: _arkState.value.requiredBoardConfirmations?.takeIf { it > 0 }
                    ?: DEFAULT_BOARD_CONFIRMATIONS
            val minBoardAmountSats =
                arkInfo?.minBoardAmountSats?.toLong()?.takeIf { it > 0L }
                    ?: _arkState.value.minBoardAmountSats?.takeIf { it > 0L }
            val knownBoardTxids =
                (
                    pendingBoards.map { it.txid } +
                        history
                            .filter { movement ->
                                val kind =
                                    "${movement.subsystemName} ${movement.subsystemKind}"
                                        .lowercase(Locale.US)
                                kind.contains("board") && !kind.contains("offboard")
                            }
                            .flatMap { movement ->
                                parseMovementMetadata(movement.metadataJson).txids
                            }
                ).map { it.trim().lowercase() }
                    .filter { it.length == 64 }
                    .distinct()
            // Bridge Esplora-known inbound + preserve previous when Bark on-chain still 0.
            // Also preserve across sync=true (session wipe forces full resync every open).
            // After L1 recover of a stuck deposit, never re-inflate stale inbound paint —
            // including fee-pad leftover dust still below ASP min (session + prefs flag).
            val liveOnchainTotal = liveOnchainConfirmed + liveOnchainPending
            // /utxo already includes mempool. /txs fallback can resurrect spent boarded funding.
            val allowTxHistoryFallback = balance.pendingBoardSats.toLong() > 0L
            val recoveredOnchainTxids = recoveredOnchainTxidsLocked(walletId)
            val boardedOnchainTxids = boardedOnchainTxidsLocked(walletId, previousState.movements)
            val ignoredOnchainTxids = recoveredOnchainTxids + boardedOnchainTxids + knownBoardTxids
            val liveListedOnchainUtxos =
                if (onchainWallet == null) {
                    emptyList()
                } else {
                    listArkOnchainUtxosLocked(
                        walletId = walletId,
                        tipHeight = tipHeight,
                        extraAddresses =
                            listOfNotNull(
                                bitcoinDeposit,
                                previousState.onchainUtxos.firstOrNull()?.address,
                            ),
                        allowTxHistoryFallback = allowTxHistoryFallback,
                    ).filterNot { ignoredOnchainTxids.contains(it.txid.trim().lowercase()) }
                }
            val depositChainDetails =
                resolveArkDepositChainDetails(
                    walletId = walletId,
                    pendingBoards = pendingBoards,
                    fallbackAmountSats =
                        maxOf(
                            liveOnchainTotal,
                            previousState.onchainTotalSats,
                            balance.pendingBoardSats.toLong(),
                            liveListedOnchainUtxos.sumOf { it.amountSats.coerceAtLeast(0L) },
                        ),
                    tipHeight = tipHeight,
                    knownUtxos = liveListedOnchainUtxos,
                )
            // Leftover recover change lives on an unremembered Bark address, so Esplora never
            // lists it while Bark balance still counts it. With no fresh listed UTXO, that is
            // recover dust — keep suppress on even if the flag was already cleared.
            val recoverSuppressed =
                suppressStaleOnchainPaint ||
                    secureStorage.isArkOnchainRecoverSuppressed(walletId) ||
                    (
                        recoveredOnchainTxids.isNotEmpty() &&
                            liveListedOnchainUtxos.isEmpty()
                    )
            if (recoverSuppressed != suppressStaleOnchainPaint) {
                suppressStaleOnchainPaint = recoverSuppressed
            }
            val suppressActive = recoverSuppressed && liveListedOnchainUtxos.isEmpty()
            if (recoverSuppressed && liveListedOnchainUtxos.isNotEmpty()) {
                // A new unspent deposit (not a recovered funding / leftover change tx) resumes paint.
                markOnchainRecoverSuppressedLocked(walletId, suppressed = false)
            }
            val listedOnchainUtxos =
                if (suppressActive || onchainWallet == null) {
                    emptyList()
                } else {
                    liveListedOnchainUtxos
                }
            // Prefer sum of unspent deposit UTXOs (self-fund / multi-deposit) over a single
            // chainDetails pick when listing Esplora UTXOs. Include 0-conf.
            val utxoConfirmedSats =
                listedOnchainUtxos.filter { it.isConfirmed }.sumOf { it.amountSats.coerceAtLeast(0L) }
            val utxoPendingSats =
                listedOnchainUtxos.filter { !it.isConfirmed }.sumOf { it.amountSats.coerceAtLeast(0L) }
            val utxoTotalSats = utxoConfirmedSats + utxoPendingSats
            // Bark may see mempool deposits before Esplora /utxo indexes them — still paint.
            val barkPendingOnly =
                liveOnchainPending.coerceAtLeast(0L).takeIf {
                    it > 0L && utxoTotalSats <= 0L && (depositChainDetails?.amountSats ?: 0L) <= 0L
                } ?: 0L
            val onchainUtxos =
                when {
                    listedOnchainUtxos.isNotEmpty() -> listedOnchainUtxos
                    barkPendingOnly > 0L ->
                        listOf(
                            ArkOnchainUtxo(
                                txid = "",
                                vout = 0,
                                amountSats = barkPendingOnly,
                                confirmations = 0,
                                address =
                                    bitcoinDeposit
                                        ?: depositChainDetails?.address.orEmpty(),
                                isConfirmed = false,
                            ),
                        )
                    depositChainDetails != null &&
                        depositChainDetails.amountSats > 0L &&
                        depositChainDetails.fundingConfirmations <= 0 &&
                        depositChainDetails.boardTxid.isNullOrBlank() ->
                        listOf(
                            ArkOnchainUtxo(
                                txid = depositChainDetails.fundingTxid,
                                vout = depositChainDetails.fundingVout,
                                amountSats = depositChainDetails.amountSats,
                                confirmations = 0,
                                address = depositChainDetails.address,
                                isConfirmed = false,
                            ),
                        )
                    else -> listedOnchainUtxos
                }
            val esploraAmountForBuckets =
                when {
                    utxoTotalSats > 0L -> utxoTotalSats
                    barkPendingOnly > 0L -> barkPendingOnly
                    depositChainDetails?.boardTxid.isNullOrBlank() ->
                        depositChainDetails?.amountSats ?: 0L
                    else -> 0L
                }
            val esploraConfsForBuckets =
                when {
                    utxoConfirmedSats > 0L ->
                        listedOnchainUtxos.filter { it.isConfirmed }.maxOfOrNull { it.confirmations }
                            ?: 1
                    utxoPendingSats > 0L || barkPendingOnly > 0L -> 0
                    depositChainDetails != null && depositChainDetails.fundingConfirmations <= 0 -> 0
                    else -> depositChainDetails?.fundingConfirmations
                }
            val onchainBuckets =
                if (suppressActive) {
                    ArkDepositPolicy.OnchainBuckets(confirmedSats = 0L, pendingSats = 0L)
                } else {
                    ArkDepositPolicy.resolveOnchainBuckets(
                        liveConfirmedSats = liveOnchainConfirmed,
                        livePendingSats = liveOnchainPending,
                        previousConfirmedSats = previousState.onchainConfirmedSats,
                        previousPendingSats = previousState.onchainPendingSats,
                        pendingBoardSats = balance.pendingBoardSats.toLong(),
                        esploraAmountSats = esploraAmountForBuckets,
                        esploraFundingConfirmations = esploraConfsForBuckets,
                        onchainWalletPresent = onchainWallet != null,
                        preservePreviousWhenLiveZero = !recoverSuppressed,
                    spendableSats =
                        maxOf(
                            balance.spendableSats.toLong(),
                            allVtxos
                                .filter { it.state is VtxoState.Spendable }
                                .sumOf { it.amountSats.toLong().coerceAtLeast(0L) },
                        ),
                    )
                }
            // Keep mempool (0-conf) deposits visible even when Bark live total > 0 but lagging.
            val resolvedOnchainConfirmed =
                maxOf(onchainBuckets.confirmedSats, utxoConfirmedSats)
            val resolvedOnchainPending =
                maxOf(
                    onchainBuckets.pendingSats,
                    utxoPendingSats,
                    barkPendingOnly,
                    liveOnchainPending.coerceAtLeast(0L),
                )
            if (
                onchainWallet != null &&
                    (liveOnchainConfirmed + liveOnchainPending) <= 0L &&
                    onchainBuckets.totalSats > 0L
            ) {
                SecureLog.d(
                    TAG,
                    "Ark on-chain paint bridge: bark=0 esplora/prev=${onchainBuckets.totalSats} " +
                        "pendingBoard=${balance.pendingBoardSats} confs=" +
                        "${depositChainDetails?.fundingConfirmations}/${depositChainDetails?.boardConfirmations}",
                )
            }
            // Keep open Receive UI warm without waiting for a separate receive() call.
            primeReceiveStateIfNeededLocked(
                arkAddress = address,
                bitcoinAddress = bitcoinDeposit,
            )
            val fundingTxids =
                secureStorage.getArkFundingTxids(walletId).map { it.lowercase() }
            val movements =
                history
                    .map {
                        it.toArkMovement(
                            label = labels[it.id.toString()],
                            storedDestination = destinations[it.id.toString()],
                            ownArkAddress = address,
                        )
                    }
                    .let { mapped ->
                        val claimMovements =
                            secureStorage.getArkExitClaimHistory(walletId).map { claim ->
                                val confirmed =
                                    claim.vtxoIds.isNotEmpty() &&
                                        claim.vtxoIds.all { vtxoId ->
                                            runCatching {
                                                w.getExitStatus(
                                                    vtxoId = vtxoId,
                                                    includeHistory = false,
                                                    includeTransactions = false,
                                                )?.state?.let(ArkBarkMappers::isClaimed) == true
                                            }.getOrDefault(false)
                                        }
                                exitClaimToArkMovement(claim, confirmed)
                            }
                        val transactionHistory =
                            mapped.filterNot(::isArkExitBookkeepingMovement) +
                                claimMovements
                        val pendingBoardTxids =
                            pendingBoards
                                .mapNotNull { board ->
                                    board.txid.trim().lowercase().takeIf { it.length == 64 }
                                }
                        val knownTxids = (pendingBoardTxids + fundingTxids).distinct()
                        val withFunding =
                            if (knownTxids.isEmpty()) {
                                transactionHistory
                            } else {
                                transactionHistory.map { injectFundingTxid(it, knownTxids) }
                            }
                        val withRecovered =
                            withFunding + recoveredOnchainDepositsToMovements(walletId)
                        val withPendingDeposit =
                            addPendingOnchainDepositMovement(
                                movements = withRecovered,
                                previousMovements = previousState.movements,
                                confirmedSats = resolvedOnchainConfirmed,
                                pendingSats = resolvedOnchainPending,
                                pendingBoardSats = balance.pendingBoardSats.toLong(),
                                pendingBoards = pendingBoards,
                                // Prefer stable cached deposit address; never invent a rotated one
                                // solely for the synthetic history row.
                                depositAddress =
                                    previousState.movements
                                        .firstOrNull {
                                            ArkDepositPolicy.isSyntheticPendingOnchainDeposit(it)
                                        }
                                        ?.receivedOnAddresses
                                        ?.firstOrNull()
                                        ?.takeIf { it.isNotBlank() }
                                        ?: secureStorage.getArkOnchainDepositAddress(walletId)
                                        ?: onchainUtxos.firstOrNull()?.address
                                        ?: bitcoinDeposit,
                                fundingTxids = fundingTxids,
                                chainDetails = depositChainDetails,
                                onchainUtxos = onchainUtxos,
                                requiredBoardConfirmations = requiredBoardConfirmations,
                            )
                        // Fresh session: Bark history() is often empty after mailbox VTXO
                        // recovery — never clobber SecureStorage-painted movements with [].
                        val mergedHistory =
                            ArkDepositPolicy.mergePreservedMovements(
                                live = withPendingDeposit,
                                previous = previousState.movements,
                            )
                        filterHiddenArkMovements(
                            walletId,
                            attachPendingSendDestinationToList(walletId, mergedHistory),
                        )
                    }
            // Persist proven-used addresses, then rotate current receive past them.
            rememberUsedAddressesFromMovements(walletId, movements)
            val unusedAfterHistory =
                ensureReceiveAddressUnusedLocked(
                    w = w,
                    walletId = walletId,
                    movements = movements,
                    preferredArk = address,
                )
            val unusedBitcoinAfterHistory =
                bitcoinDeposit
                    ?.takeIf { !isAddressUsed(it, movements = movements, walletId = walletId) }
                    ?: runCatching {
                        resolveUnusedBitcoinDepositAddressLocked(forceNew = bitcoinDeposit != null)
                    }.getOrNull()
            primeReceiveStateIfNeededLocked(
                arkAddress = unusedAfterHistory ?: address,
                bitcoinAddress = unusedBitcoinAfterHistory,
            )
            val wasAspHydrated = aspHydratedWalletId == walletId
            // Drop refresh tracking once the round is gone or submitted VTXO ids were replaced.
            reconcileTrackedRefreshLocked(w, allVtxos.map { it.id })
            _arkState.value =
                ArkWalletState(
                    walletId = walletId,
                    isInitialized = true,
                    isSyncing = isSyncSpinnerHeld(),
                    isConnected = true,
                    isConnecting = false,
                    fingerprint = fingerprint,
                    spendableSats = balance.spendableSats.toLong(),
                    pendingInRoundSats = balance.pendingInRoundSats.toLong(),
                    pendingBoardSats = balance.pendingBoardSats.toLong(),
                    pendingExitSats = balance.pendingExitSats.toLong(),
                    pendingLightningSendSats = balance.pendingLightningSendSats.toLong(),
                    claimableLightningReceiveSats = balance.claimableLightningReceiveSats.toLong(),
                    onchainConfirmedSats = resolvedOnchainConfirmed,
                    onchainPendingSats = resolvedOnchainPending,
                    onchainUtxos = onchainUtxos,
                    movements = movements,
                    vtxos = allVtxos.map { it.toArkVtxo() },
                    vtxosToRefresh = mappedRefresh,
                    expiringSoonVtxos = mappedExpiring,
                    exitVtxos = exitList.map { it.toArkExitVtxo() },
                    claimableExitVtxos = claimableList.map { it.toArkExitVtxo() },
                    hasPendingExits = pendingExits || exitList.isNotEmpty(),
                    nextRefreshHeight = nextRefresh,
                    firstExpiringHeight = firstExpiring,
                    chainTipHeight = tipHeight,
                    isAutoRefreshing = _arkState.value.isAutoRefreshing,
                    pendingRefreshVtxoIds = trackedRefreshVtxoIds,
                    pendingRefreshScheduledHeight = trackedRefreshScheduledHeight,
                    currentAddress = unusedAfterHistory ?: address,
                    serverAddress = secureStorage.getArkServerAddress(),
                    lastSyncTimestamp = System.currentTimeMillis(),
                    aspHydrated = wasAspHydrated,
                    minBoardAmountSats = minBoardAmountSats,
                    requiredBoardConfirmations = requiredBoardConfirmations,
                )
            // Never persist empty movements over a non-empty cache while funds/VTXOs exist
            // (mailbox recovery session: history not yet rehydrated).
            val stateToCache = _arkState.value
            val priorCache = secureStorage.getArkWalletStateCache(walletId)
            val priorMovements = priorCache?.movements.orEmpty()
            val cacheSafe =
                if (
                    stateToCache.movements.isEmpty() &&
                        priorMovements.isNotEmpty() &&
                        (stateToCache.totalSats > 0L || stateToCache.vtxos.isNotEmpty())
                ) {
                    stateToCache.copy(movements = priorMovements)
                } else {
                    stateToCache
                }
            if (cacheSafe.movements !== stateToCache.movements) {
                _arkState.value = cacheSafe
            }
            secureStorage.saveArkWalletStateCache(walletId, cacheSafe)
            checkLightningReceivePaidLocked()
            // Keep open receive UI on the unused address after a payment lands.
            advanceOpenReceiveIfAddressUsedLocked()
            val now = System.currentTimeMillis()
             if (mappedRefresh.isNotEmpty() &&
                 (!previousNeedsRefresh || now - lastNeedsRefreshEmitMs > REFRESH_SIGNAL_COOLDOWN_MS)
             ) {
                lastNeedsRefreshEmitMs = now
                _events.tryEmit(
                    ArkEvent.NeedsRefresh(
                        vtxoCount = mappedRefresh.size,
                        nextRefreshHeight = nextRefresh,
                        autoStarted = false,
                    ),
                )
            }
            val soonState = _arkState.value
            if (soonState.refreshSoon &&
                (!previousRefreshSoon || now - lastRefreshSoonEmitMs > REFRESH_SIGNAL_COOLDOWN_MS)
            ) {
                lastRefreshSoonEmitMs = now
                _events.tryEmit(
                    ArkEvent.RefreshSoon(
                        vtxoCount =
                            maxOf(
                                soonState.expiringSoonVtxos.size,
                                if (soonState.blocksUntilRequiredRefresh != null) 1 else 0,
                            ),
                        blocksRemaining = soonState.blocksUntilRequiredRefresh,
                    ),
                )
            }
            maybeScheduleAutoRefreshLocked()
            val refreshed = _arkState.value
            if (refreshed.isInitialized && refreshed.error == null) {
                scheduleAutoDbBackup(
                    walletId = walletId,
                    fingerprint = arkStateBackupFingerprint(refreshed),
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _arkState.value =
                _arkState.value.copy(
                    isSyncing = isSyncSpinnerHeld(),
                    error = publicError(e),
                )
        }
    }

    /**
     * Clear in-memory Ark session state.
     * @param disposeHandles when true, close Bark handles under the lock (slow). When false,
     *   returns the handles so the caller can dispose them outside the mutex (faster wallet switch).
     * @param persistCache when false, skip writing live balances/history to SecureStorage
     *   (required for DB import so pre-import ghosts cannot reappear).
     */
    private suspend fun unloadLocked(
        disposeHandles: Boolean = true,
        persistCache: Boolean = true,
    ): Pair<Wallet?, OnchainWallet?>? {
        val postOpen = postOpenSyncJob
        postOpenSyncJob = null
        lightningReceiveWatchJob?.cancel()
        lightningReceiveWatchJob = null
        receiveLightningBaselineSats = -1L
        val staleJobs =
            listOfNotNull(
                notificationJob,
                deferredBoardJob,
                autoRefreshJob,
                autoDbBackupJob,
                manualRefreshJob,
            )
        notificationJob = null
        notificationWallet = null
        deferredBoardJob = null
        autoRefreshJob = null
        autoDbBackupJob = null
        manualRefreshJob = null
        trackedRefreshRoundId = null
        trackedRefreshScheduledHeight = null
        trackedRefreshAutomatic = false
        trackedRefreshVtxoIds = emptyList()
        // Wallet state is wiped below; no need to publishPendingRefresh here.
        cancelDatadirLockReopen()
        datadirLockReopenAttempts = 0
        postOpen?.cancel()
        staleJobs.forEach { it.cancel() }
        // Wait for in-flight native FFI calls to unwind before closing handles below.
        // Cancellation alone cannot interrupt a blocking Bark call — closing while a
        // collector/board/refresh job is inside the native lib aborts the process.
        // Post-open ASP sync can run longer than notification collectors; give it more time
        // so DB import does not dispose mid-sync (SIGABRT).
        withTimeoutOrNull(ARK_POST_OPEN_JOIN_TIMEOUT_MS) { postOpen?.join() }
        withTimeoutOrNull(ARK_JOB_JOIN_TIMEOUT_MS) { staleJobs.joinAll() }
        lastNeedsRefreshEmitMs = 0L
        lastRefreshSoonEmitMs = 0L
        lastAutoRefreshAttemptMs = 0L
        lastAutoRefreshFailed = false
        lastKnownChainTipHeight = null
        lastAutoDbBackupFingerprint = null
        onchainRevealCatchUpDone = false
        blindBoardAttempts = 0
        val current = wallet
        val currentOnchain = onchainWallet
        wallet = null
        onchainWallet = null
        aspHydratedWalletId = null
        // Keep cacheDir session for fast reopen; wipe only on deleteWalletData / full wipe.
        detachSessionDataDirLocked()
        val unloadedWalletId = _loadedWalletId.value
        // beginConnecting may already have painted the *next* wallet's cache; don't clobber it.
        val displayWalletId = _arkState.value.walletId
        val switchedAway =
            !unloadedWalletId.isNullOrBlank() &&
                !displayWalletId.isNullOrBlank() &&
                unloadedWalletId != displayWalletId
        val walletIdForCache = unloadedWalletId ?: displayWalletId
        if (disposeHandles) {
            // Close under the load mutex so Esplora reloads don't race a still-open Bark DB.
            disposeBarkHandles(current, currentOnchain)
            stopEsploraRelays()
            flushPendingSessionDirDeletes()
        }
        _loadedWalletId.value = null
        _isConnected.value = false
        forceStopSyncSpinner()
        // Keep isConnecting when UI already retargeted to another wallet mid-switch.
        if (!switchedAway) {
            _isConnecting.value = false
        }
        _sendState.value = ArkSendState.Idle
        _receiveState.value = ArkReceiveState.Idle
        _transferState.value = ArkTransferState.Idle
        _lifecycleState.value = ArkLifecycleState.Idle
        clearPreparedSendStateLocked()
        pendingSendDestination = null
        pendingSendAmountSats = null
        pendingSendKind = null
        if (switchedAway) {
            // Display already retargeted via beginConnecting — leave next-wallet paint alone.
        } else if (walletIdForCache.isNullOrBlank()) {
            _arkState.value = ArkWalletState(isInitialized = true, aspHydrated = false)
            _arkMovementLabels.value = emptyMap()
            _arkAddressLabels.value = emptyMap()
        } else {
            applyDisconnectedArkStateLocked(walletIdForCache, persistCache = persistCache)
        }
        return if (disposeHandles) null else (current to currentOnchain)
    }

    /**
     * Opt-in **auto delegated** refresh: only when the settings toggle is on, wallet is live,
     * Bark lists due VTXOs, and the fee estimate is under [AUTO_REFRESH_MAX_FEE_SATS].
     * Always uses [runDelegatedRefreshWithFallbackLocked] (delegated first).
     * Not a closed-wallet / OS background job — live Ark session only.
     *
     * Triggered from [refreshStateLocked] after each successful state rebuild (heartbeat,
     * notifications, manual refresh, opt-in toggle). Cooldown applies only after a real
     * refresh attempt starts — fee-cap skips do not burn the cooldown window.
     */
    private fun autoRefreshCooldownMs(): Long =
        if (lastAutoRefreshFailed) AUTO_REFRESH_FAILURE_RETRY_MS else AUTO_REFRESH_COOLDOWN_MS

    private fun maybeScheduleAutoRefreshLocked() {
        if (!secureStorage.isArkAutoDelegatedRefreshEnabled()) return
        if (!_isConnected.value || wallet == null) return
        if (trackedRefreshRoundId != null) return
        if (_arkState.value.isAutoRefreshing) return
        if (autoRefreshJob?.isActive == true) return
        if (!ArkRefreshPolicy.shouldRunAutoRefresh(_arkState.value.needsRefresh, _arkState.value.refreshSoon)) return
        val now = System.currentTimeMillis()
        if (now - lastAutoRefreshAttemptMs < autoRefreshCooldownMs()) return
        autoRefreshJob =
            eventScope.launch {
                var shouldReschedule = false
                try {
                    mutex.withLock {
                        if (!secureStorage.isArkAutoDelegatedRefreshEnabled()) return@withLock
                        val w = wallet ?: return@withLock
                        if (!_isConnected.value) return@withLock
                        if (_arkState.value.isAutoRefreshing) return@withLock
                        if (!ArkRefreshPolicy.shouldRunAutoRefresh(
                                _arkState.value.needsRefresh,
                                _arkState.value.refreshSoon,
                            )
                        ) {
                            return@withLock
                        }
                        // Re-check cooldown under lock (another job may have started).
                        val lockedNow = System.currentTimeMillis()
                        if (lockedNow - lastAutoRefreshAttemptMs < autoRefreshCooldownMs()) {
                            return@withLock
                        }
                        val due = w.getVtxosToRefresh().map { it.toArkVtxo() }
                        val allVtxos =
                            if (due.isEmpty()) {
                                w.vtxos()
                                    .map { it.toArkVtxo() }
                                    .filter { ArkBarkMappers.isSpendableLabel(it.state) }
                            } else {
                                emptyList()
                            }
                        val firstExpiry = _arkState.value.firstExpiringHeight
                        val targetVtxos = ArkRefreshPolicy.autoRefreshTargets(due, allVtxos, firstExpiry)
                        val targets = targetVtxos.map { it.id }
                        if (targets.isEmpty()) return@withLock
                        val tip = resolveChainTipHeight()
                        val nextHeight = _arkState.value.nextRefreshHeight
                        val scheduledHeight =
                            ArkRefreshPolicy.scheduledHeight(
                                nextHeight,
                                firstExpiry,
                                tip,
                                targetVtxos,
                            )
                        val fee =
                            if (scheduledHeight != null) {
                                estimateScheduledRefreshFeeLocked(w, targetVtxos, scheduledHeight)
                            } else {
                                runCatching { w.estimateRefreshFee(targets).feeSats.toLong() }.getOrNull()
                            }
                        val dailyRemaining = autoRefreshDailyRemainingLocked()
                        val overPerAttempt = fee == null || fee > AUTO_REFRESH_MAX_FEE_SATS
                        val overDaily = fee == null || fee > dailyRemaining
                        if (overPerAttempt || overDaily || dailyRemaining <= 0L) {
                            // Too expensive to silent-auto — banner only; do not burn cooldown.
                            val emitNow = System.currentTimeMillis()
                            if (emitNow - lastNeedsRefreshEmitMs > REFRESH_SIGNAL_COOLDOWN_MS) {
                                lastNeedsRefreshEmitMs = emitNow
                                _events.tryEmit(
                                    ArkEvent.NeedsRefresh(
                                        vtxoCount = targets.size,
                                        nextRefreshHeight = _arkState.value.nextRefreshHeight,
                                        autoStarted = false,
                                    ),
                                )
                            }
                            return@withLock
                        }
                        // Commit cooldown only once we are about to spend / hit the ASP.
                        lastAutoRefreshAttemptMs = lockedNow
                        lastAutoRefreshFailed = false
                        _arkState.value = _arkState.value.copy(isAutoRefreshing = true)
                        try {
                            _events.tryEmit(
                                ArkEvent.NeedsRefresh(
                                    vtxoCount = targets.size,
                                    nextRefreshHeight = _arkState.value.nextRefreshHeight,
                                    autoStarted = true,
                                ),
                            )
                            val result =
                                runDelegatedRefreshWithFallbackLocked(
                                    wallet = w,
                                    vtxoIds = targets,
                                    preferDelegated = true,
                                    allowNonDelegatedFallback = false,
                                    scheduledHeight = scheduledHeight,
                                )
                            trackedRefreshRoundId = result.roundId
                            trackedRefreshScheduledHeight = result.scheduledHeight
                            trackedRefreshAutomatic = true
                            trackedRefreshVtxoIds =
                                (trackedRefreshVtxoIds + targets).distinct()
                            publishPendingRefreshToWalletStateLocked()
                            recordAutoRefreshFeeLocked(checkNotNull(fee))
                            refreshStateLocked(sync = false, attemptBoard = false)
                            lastAutoRefreshFailed = false
                            _events.tryEmit(
                                ArkEvent.RefreshSubmitted(
                                    automatic = true,
                                    scheduledHeight = result.scheduledHeight,
                                    vtxoCount = targets.size,
                                ),
                            )
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            lastAutoRefreshFailed = true
                            _events.tryEmit(
                                ArkEvent.RefreshFailed(
                                    message = publicError(e),
                                    automatic = true,
                                    delegated = true,
                                ),
                            )
                        } finally {
                            _arkState.value = _arkState.value.copy(isAutoRefreshing = false)
                            shouldReschedule =
                                secureStorage.isArkAutoDelegatedRefreshEnabled() &&
                                    _isConnected.value &&
                                    trackedRefreshRoundId == null &&
                                    ArkRefreshPolicy.shouldRunAutoRefresh(
                                        _arkState.value.needsRefresh,
                                        _arkState.value.refreshSoon,
                                    )
                        }
                    }
                } finally {
                    // Outside the prior withLock: pick up remaining due VTXOs after failure retry window
                    // or if Bark still lists due notes (respects cooldown / failure retry).
                    if (shouldReschedule) {
                        mutex.withLock { maybeScheduleAutoRefreshLocked() }
                    }
                }
            }
    }

    /**
     * Prefer Bark on-chain wallet tip when open; fall back to Esplora HTTP.
     * Used for refresh scheduling and blocks-until-refresh UI.
     */
    private suspend fun resolveChainTipHeight(): Int? {
        val now = System.currentTimeMillis()
        if (lastKnownChainTipHeight != null && now - lastTipFetchMs < TIP_CACHE_MS) {
            return lastKnownChainTipHeight
        }
        val fromOnchain =
            runCatching {
                onchainWallet?.tipHeight()?.toInt()?.takeIf { it > 0 }
            }.getOrNull()
        if (fromOnchain != null) {
            lastKnownChainTipHeight = fromOnchain
            lastTipFetchMs = now
            return fromOnchain
        }
        return fetchEsploraTipHeight()
    }

    /** Best-effort Bitcoin tip from configured Esplora (for blocks-until-refresh UI). */
    private fun fetchEsploraTipHeight(): Int? {
        val now = System.currentTimeMillis()
        if (lastKnownChainTipHeight != null && now - lastTipFetchMs < TIP_CACHE_MS) {
            return lastKnownChainTipHeight
        }
        // Same base as fetchEsploraJson — onion goes through EsploraTorRelay when running.
        val base = activeEsploraHttpBase()
        if (base.isBlank()) return lastKnownChainTipHeight
        // Direct onion without relay cannot be reached from app OkHttp; keep last tip.
        if (isOnionEsplora(base) && esploraTorRelay?.takeIf { it.isRunning() } == null) {
            return lastKnownChainTipHeight
        }
        val tip =
            runCatching {
                val request =
                    Request.Builder()
                        .url("${base.trimEnd('/')}/blocks/tip/height")
                        .header("Accept", "text/plain,application/json")
                        .get()
                        .build()
                esploraHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    response.body?.string()?.trim()?.toIntOrNull()?.takeIf { it > 0 }
                }
            }.getOrNull()
        if (tip != null) {
            lastKnownChainTipHeight = tip
            lastTipFetchMs = now
        }
        return tip ?: lastKnownChainTipHeight
    }

    private suspend fun disposeBarkHandles(
        barkWallet: Wallet?,
        onchain: OnchainWallet?,
    ) = nativeHandleMutex.withLock {
        disposeBarkHandlesLocked(barkWallet, onchain)
        // Orphan/temp dirs queued after failed opens or import cleanup.
        flushPendingSessionDirDeletes()
    }

    /** Caller must hold [nativeHandleMutex]. */
    private suspend fun disposeBarkHandlesLocked(
        barkWallet: Wallet?,
        onchain: OnchainWallet?,
    ) {
        if (unattachedBarkHandles?.wallet === barkWallet) {
            unattachedBarkHandles = null
        }
        if (barkWallet != null && barkWallet === closedUnattachedWallet) {
            closedUnattachedWallet = null
            return
        }
        if (barkWallet != null) {
            runCatching { barkWallet.stopDaemon() }
            runCatching { barkWallet.close() }
        }
        if (onchain != null) {
            runCatching { onchain.close() }
        }
    }

    private suspend fun disposeOpenedArkWalletLocked(opened: OpenedArkWallet) {
        if (opened.wallet === closedUnattachedWallet) return
        closedUnattachedWallet = opened.wallet
        runCatching { opened.wallet.stopDaemon() }
        runCatching { opened.wallet.close() }
        opened.onchain?.let { runCatching { it.close() } }
    }

    private fun cancelDatadirLockReopen() {
        datadirLockReopenJob?.cancel()
        datadirLockReopenJob = null
    }

    /**
     * After a datadir-lock failure the UI would otherwise stay on the error until
     * the user restarts the app. Retry load once the stale close has had time to finish.
     */
    private fun scheduleDatadirLockReopen(walletId: String) {
        if (walletId.isBlank()) return
        if (datadirLockReopenAttempts >= ArkDatadirLockPolicy.MAX_AUTO_REOPENS) return
        datadirLockReopenAttempts += 1
        val scheduledGeneration = loadGeneration.get()
        cancelDatadirLockReopen()
        datadirLockReopenJob =
            eventScope.launch {
                delay(ArkDatadirLockPolicy.retryDelayMs(datadirLockReopenAttempts + 1))
                if (loadGeneration.get() != scheduledGeneration) return@launch
                if (_isConnected.value || _isConnecting.value) return@launch
                if (_arkState.value.walletId != walletId) return@launch
                if (!isEligible(walletId)) return@launch
                SecureLog.w(TAG, "Ark datadir lock — automatic reopen")
                runCatching { loadWallet(walletId) }
            }
    }

    private fun loadLabelsLocked(walletId: String) {
        _arkMovementLabels.value = secureStorage.getAllArkMovementLabels(walletId)
        _arkAddressLabels.value = secureStorage.getAllArkAddressLabels(walletId)
    }

    private fun startNotificationsLocked(opened: Wallet) {
        notificationJob?.cancel()
        notificationWallet = opened
        notificationJob =
            eventScope.launch {
                runCatching {
                    opened.notificationsFlow().collect { event ->
                        when (event) {
                            is WalletNotification.MovementCreated -> {
                                val amount = event.movement.effectiveBalanceSats
                                if (amount > 0L) {
                                    _events.tryEmit(
                                        ArkEvent.PaymentReceived(
                                            amountSats = amount,
                                            movementId = event.movement.id.toInt(),
                                        ),
                                    )
                                }
                                mutex.withLock {
                                    checkLightningReceivePaidLocked()
                                    refreshStateLocked(sync = false)
                                }
                            }
                            is WalletNotification.MovementUpdated ->
                                mutex.withLock {
                                    checkLightningReceivePaidLocked()
                                    refreshStateLocked(sync = false)
                                }
                            is WalletNotification.ChannelLagging ->
                                mutex.withLock { refreshStateLocked(sync = false) }
                        }
                    }
                }
            }
    }

    private data class BarkCredentials(
        /** Value for Wallet.open — space-separated BIP39 words, or 64-byte hex when passphrase. */
        val mnemonicOrSeed: String,
        /** Value for OnchainWallet.default (phrase only); null when passphrase is set. */
        val onchainMnemonic: String?,
    )

    /**
     * Bark's on-chain default wallet requires a BIP-39 phrase. Without a passphrase we pass
     * the normalized word list. With a passphrase we pass the 64-byte seed hex to
     * [Wallet.open] (supported) and skip the built-in on-chain wallet (no seed-hex ctor).
     */
    private fun resolveBarkCredentials(walletId: String): BarkCredentials {
        val raw =
            secureStorage.getMnemonic(walletId)
                ?: error("No mnemonic for wallet")
        val mnemonic =
            raw
                .trim()
                .lowercase()
                .split(Regex("\\s+"))
                .filter { it.isNotBlank() }
                .joinToString(" ")
        if (mnemonic.isBlank()) error("Empty mnemonic")
        val wordCount = mnemonic.split(" ").size
        if (wordCount !in setOf(12, 15, 18, 21, 24)) {
            error("Invalid mnemonic word count: $wordCount")
        }
        val passphrase = secureStorage.getPassphrase(walletId).orEmpty()
        return if (passphrase.isEmpty()) {
            BarkCredentials(
                mnemonicOrSeed = mnemonic,
                onchainMnemonic = mnemonic,
            )
        } else {
            val seed = ElectrumSeedUtil.bip39MnemonicToSeed(mnemonic, passphrase)
            val seedHex = seed.joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
            BarkCredentials(
                mnemonicOrSeed = seedHex,
                onchainMnemonic = null,
            )
        }
    }

    private fun buildConfig(esploraAddress: String = secureStorage.getArkEsploraAddress()): Config =
        Config(
            serverAddress = secureStorage.getArkServerAddress(),
            serverAccessToken = null,
            esploraAddress = esploraAddress.trim().trimEnd('/'),
            bitcoindAddress = null,
            bitcoindCookiefile = null,
            bitcoindUser = null,
            bitcoindPass = null,
            vtxoRefreshExpiryThreshold = null,
            vtxoExitMargin = null,
            htlcRecvClaimDelta = null,
            fallbackFeeRate = null,
            roundTxRequiredConfirmations = null,
            daemonSyncIntervalSecs = null,
            offboardRequiredConfirmations = null,
            daemonManualSync = null,
            lightningReceiveClaimRetries = null,
            userAgent = ArkDefaults.USER_AGENT,
        )

    private fun normalizeEsploraUrl(url: String): String = url.trim().trimEnd('/')

    private fun activeEsploraHttpBase(): String =
        esploraTorRelay?.takeIf { it.isRunning() }?.apiBaseUrl()
            ?: esploraClearnetRelay?.takeIf { it.isRunning() }?.apiBaseUrl()
            ?: normalizeEsploraUrl(
                configuredEsploraUrl.ifBlank { secureStorage.getArkEsploraAddress() },
            )

    /** In-memory Esplora base actually used after open (may differ from configured on fallback). */
    fun activeEsploraUrl(): String =
        configuredEsploraUrl.ifBlank { secureStorage.getArkEsploraAddress() }

    /** Last Esplora that successfully opened Bark this process (and persisted across cold starts). */
    private var lastSuccessfulEsploraUrl: String? =
        secureStorage.getArkLastSuccessfulEsploraAddress()

    private fun maybeEmitEsploraFallback(
        preferred: String,
        active: String,
    ) {
        val pref = normalizeEsploraUrl(preferred)
        val act = normalizeEsploraUrl(active)
        if (pref.isBlank() || act.isBlank()) return
        if (pref.equals(act, ignoreCase = true)) return
        if (isOnionEsplora(pref)) return
        _events.tryEmit(
            ArkEvent.EsploraFallbackUsed(
                configuredUrl = pref,
                activeUrl = act,
            ),
        )
    }

    private fun isOnionEsplora(url: String): Boolean =
        runCatching {
            java.net.URI(normalizeEsploraUrl(url)).host?.endsWith(".onion", ignoreCase = true) == true
        }.getOrElse {
            normalizeEsploraUrl(url).contains(".onion", ignoreCase = true)
        }

    private fun esploraCandidates(): List<String> {
        val preferred = normalizeEsploraUrl(secureStorage.getArkEsploraAddress())
        // Onion is explicit privacy choice — don't silently fall back to clearnet.
        if (isOnionEsplora(preferred)) {
            return listOf(preferred)
        }
        val lastGood =
            lastSuccessfulEsploraUrl
                ?.let { normalizeEsploraUrl(it) }
                ?.takeIf { it.isNotBlank() && !isOnionEsplora(it) }
        return (listOfNotNull(preferred, lastGood) + ArkDefaults.ESPLORA_FALLBACKS)
            .map { normalizeEsploraUrl(it) }
            .filter { it.isNotBlank() && !isOnionEsplora(it) }
            .distinct()
    }

    private data class ArkOpenEndpoints(
        val esploraOrder: List<String>,
        val aspReachable: Boolean,
    )

    /**
     * Probe ASP + Esplora in parallel before [Wallet.open]. Only reachable Esplora
     * hosts (fastest first) are handed to Bark. Runs outside the wallet mutex.
     */
    private suspend fun probeArkOpenEndpoints(): ArkOpenEndpoints {
        val aspUrl = secureStorage.getArkServerAddress()
        val candidates = esploraCandidates()
        if (candidates.isEmpty()) {
            return ArkOpenEndpoints(emptyList(), preflightClearnetHttps(aspUrl))
        }
        if (candidates.size == 1 && isOnionEsplora(candidates.first())) {
            return ArkOpenEndpoints(candidates, preflightClearnetHttps(aspUrl))
        }
        val clearnet = candidates.filter { !isOnionEsplora(it) }
        if (clearnet.isEmpty()) {
            return ArkOpenEndpoints(candidates, preflightClearnetHttps(aspUrl))
        }
        return coroutineScope {
            val aspProbe = async(Dispatchers.IO) { preflightClearnetHttps(aspUrl) }
            val esploraProbes =
                clearnet.map { url ->
                    async(Dispatchers.IO) { url to preflightClearnetEsploraTimed(url) }
                }
            val aspOk = aspProbe.await()
            if (!aspOk) {
                SecureLog.w(TAG, "Ark ASP preflight failed ($aspUrl)")
            }
            val reachable =
                esploraProbes.awaitAll()
                    .mapNotNull { (url, elapsedMs) -> elapsedMs?.let { url to it } }
                    .sortedBy { it.second }
                    .map { it.first }
            if (reachable.isEmpty()) {
                SecureLog.w(TAG, "All Esplora preflights failed")
            }
            ArkOpenEndpoints(
                esploraOrder = ArkEsploraOpenPolicy.orderForOpen(reachable),
                aspReachable = aspOk,
            )
        }
    }

    private fun rememberSuccessfulEsplora(esplora: String) {
        val normalized = normalizeEsploraUrl(esplora)
        if (normalized.isBlank() || isOnionEsplora(normalized)) return
        lastSuccessfulEsploraUrl = normalized
        secureStorage.setArkLastSuccessfulEsploraAddress(normalized)
    }

    /**
     * Fast HTTP probe before handing Esplora to Bark. Skips hosts that hang forever on
     * broken IPv6 (common with mempool.space AAAA) so fallbacks can run quickly.
     * Onion is not preflighted here (handled by the Tor loopback relay).
     * @return elapsed ms when reachable, null when the host failed.
     */
    private fun preflightClearnetEsploraTimed(esplora: String): Long? {
        if (isOnionEsplora(esplora)) return 0L
        val started = System.nanoTime()
        val ok =
            runCatching {
                val base = normalizeEsploraUrl(esplora)
                val request =
                    Request.Builder()
                        .url("$base/blocks/tip/height")
                        .header("Accept", "text/plain,application/json")
                        .get()
                        .build()
                preflightHttpClient.newCall(request).execute().use { response ->
                    response.isSuccessful
                }
            }.getOrElse { error ->
                SecureLog.w(TAG, "Esplora preflight failed ($esplora): ${publicError(error)}")
                false
            }
        return if (ok) (System.nanoTime() - started) / 1_000_000L else null
    }

    /** TCP/TLS reachability for the ASP so enable fails fast instead of hanging in Wallet.open. */
    private fun preflightClearnetHttps(url: String): Boolean {
        val normalized = url.trim().trimEnd('/')
        if (normalized.isBlank()) return false
        if (isOnionEsplora(normalized)) return true
        return runCatching {
            val request =
                Request.Builder()
                    .url(normalized)
                    .header("Accept", "*/*")
                    .get()
                    .build()
            preflightHttpClient.newCall(request).execute().use { response ->
                // Any HTTP response means TLS + route work (ASP root is often 404).
                response.code > 0
            }
        }.getOrElse { error ->
            SecureLog.w(TAG, "HTTPS preflight failed ($normalized): ${publicError(error)}")
            false
        }
    }

    /**
     * Resolve the Esplora base URL Bark should actually dial.
     *
     * Clearnet: start [EsploraClearnetRelay] (loopback HTTP → IPv4-first HTTPS)
     * so Bark never dials broken AAAA records.
     * Onion: start [EsploraTorRelay] (loopback HTTP → Tor SOCKS) so Bark never
     * DNS-resolves `.onion` itself — the UniFFI stack does not reliably honor
     * Android `ALL_PROXY` env vars.
     *
     * @return URL to put in Bark [Config.esploraAddress]
     */
    private suspend fun resolveBarkEsploraEndpoint(esplora: String): String {
        stopEsploraRelays()
        configuredEsploraUrl = esplora
        if (!isOnionEsplora(esplora)) {
            val relay = EsploraClearnetRelay.fromUrl(esplora)
            if (relay == null) return esplora
            val localBase = relay.start()
            esploraClearnetRelay = relay
            SecureLog.w(TAG, "Clearnet Esplora via IPv4 loopback relay $localBase → $esplora")
            return localBase
        }
        val onionHost =
            EsploraTorRelay.onionHostFromEsploraUrl(esplora)
                ?: error("Invalid onion Esplora URL")
        if (!torManager.isReady()) {
            torManager.start()
            if (!torManager.awaitReady(TOR_BOOTSTRAP_TIMEOUT_MS)) {
                error("Tor connection timed out for onion Esplora")
            }
            delay(TOR_POST_BOOTSTRAP_DELAY_MS)
        }
        val relay = EsploraTorRelay(onionHost = onionHost)
        val localBase = relay.start()
        esploraTorRelay = relay
        SecureLog.w(TAG, "Onion Esplora via loopback relay $localBase → $onionHost")
        return localBase
    }

    private fun stopEsploraRelays() {
        runCatching { esploraTorRelay?.stop() }
        esploraTorRelay = null
        runCatching { esploraClearnetRelay?.stop() }
        esploraClearnetRelay = null
    }

    /**
     * Try Esplora hosts until Bark can create its chain source. Some networks fail
     * DNS for niche hosts while public mempool.space / Blockstream work.
     * If on-chain setup is the only failure, open Ark without the bundled on-chain
     * wallet so off-chain still loads (boarding needs on-chain later).
     */
    private data class OpenedArkWallet(
        val wallet: Wallet,
        val onchain: OnchainWallet?,
        /** True when a BIP39 wallet had to open without the on-chain wallet (no boarding). */
        val onchainDegraded: Boolean = false,
    )

    /**
     * @param esploraOrder pre-ordered (and preferably preflighted) hosts from
     * [selectEsploraCandidatesForOpen]. Skips a second sequential preflight.
     * @param skipRecovery when true, reopen an existing local Bark DB without seed-mailbox
     * recovery (ASP hydrate still runs post-open).
     */
    private suspend fun openBarkWalletWithFallbacks(
        credentials: BarkCredentials,
        datadir: String,
        esploraOrder: List<String> = emptyList(),
        skipRecovery: Boolean = false,
    ): OpenedArkWallet {
        // Serialize native open vs close, but release [nativeHandleMutex] between
        // datadir-lock retries so a stale close can finish. Holding the mutex across
        // delay() deadlocks reopen until process death.
        var lastError: Exception? = null
        repeat(ArkDatadirLockPolicy.MAX_ATTEMPTS) { attempt ->
            try {
                return nativeHandleMutex.withLock {
                    unattachedBarkHandles?.let { leftover ->
                        unattachedBarkHandles = null
                        if (leftover.wallet !== wallet) {
                            disposeOpenedArkWalletLocked(leftover)
                        }
                    }
                    val opened =
                        openBarkWalletWithFallbacksLocked(
                            credentials = credentials,
                            datadir = datadir,
                            esploraOrder = esploraOrder,
                            skipRecovery = skipRecovery,
                        )
                    unattachedBarkHandles = opened
                    opened
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (!isDatadirLockError(e) ||
                    !ArkDatadirLockPolicy.shouldRetry(attempt)
                ) {
                    throw e
                }
                lastError = e
                SecureLog.w(
                    TAG,
                    "Ark open hit datadir lock, retry ${attempt + 1}/${ArkDatadirLockPolicy.MAX_ATTEMPTS}",
                )
                delay(ArkDatadirLockPolicy.retryDelayMs(attempt))
            }
        }
        throw lastError ?: Exception("Ark datadir lock retry failed")
    }

    /**
     * Caller must hold [nativeHandleMutex].
     *
     * Phase 1 tries on-chain open across preflight-reachable Esplora hosts (fastest first).
     * Degrading to off-chain-only is phase 2 only — boarding needs a working chain source.
     */
    private suspend fun openBarkWalletWithFallbacksLocked(
        credentials: BarkCredentials,
        datadir: String,
        esploraOrder: List<String>,
        skipRecovery: Boolean,
    ): OpenedArkWallet {
        var lastError: Exception? = null
        val hosts = ArkEsploraOpenPolicy.orderForOpen(esploraOrder)
        if (hosts.isEmpty()) {
            throw lastError ?: Exception(localizedString(R.string.ark_error_chain_source))
        }
        suspend fun prepareHost(esplora: String): Config? =
            try {
                buildConfig(resolveBarkEsploraEndpoint(esplora))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastError = e
                SecureLog.w(TAG, "Ark Tor/Esplora prepare failed ($esplora): ${publicError(e)}")
                stopEsploraRelays()
                null
            }
        // Phase 1: full open (ASP + on-chain) — one host at a time so the IPv4 relay stays live.
        if (credentials.onchainMnemonic != null) {
            for (esplora in hosts) {
                val config = prepareHost(esplora) ?: continue
                try {
                    val opened =
                        run {
                            val onchainDatadir =
                                File(datadir, "onchain").apply { mkdirs() }.absolutePath
                            val onchain =
                                OnchainWallet.default(
                                    network = Network.BITCOIN,
                                    mnemonic = credentials.onchainMnemonic,
                                    config = config,
                                    datadir = onchainDatadir,
                                )
                            try {
                                openBarkWallet(
                                    credentials.mnemonicOrSeed,
                                    config,
                                    datadir,
                                    onchain,
                                    skipRecovery = skipRecovery,
                                ) to onchain
                            } catch (e: Exception) {
                                disposeBarkHandlesLocked(null, onchain)
                                throw e
                            }
                        }
                    maybeEmitEsploraFallback(preferred = secureStorage.getArkEsploraAddress(), active = esplora)
                    configuredEsploraUrl = esplora
                    rememberSuccessfulEsplora(esplora)
                    return OpenedArkWallet(wallet = opened.first, onchain = opened.second)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    lastError = e
                    SecureLog.w(TAG, "Ark open+onchain failed ($esplora): ${publicError(e)}")
                    if (!isChainSourceError(e) && !isDnsOrNetworkError(e)) {
                        stopEsploraRelays()
                        throw e
                    }
                    // Chain-source/network only — try on-chain with the next host.
                }
            }
            SecureLog.w(
                TAG,
                "Ark on-chain open failed on all Esplora hosts — boarding disabled this session",
            )
        }
        // Phase 2: off-chain-only so ASP features still work; boarding is retried on reload.
        for (esplora in hosts) {
            val config = prepareHost(esplora) ?: continue
            try {
                val opened =
                    openBarkWallet(
                        credentials.mnemonicOrSeed,
                        config,
                        datadir,
                        onchain = null,
                        skipRecovery = skipRecovery,
                    )
                maybeEmitEsploraFallback(preferred = secureStorage.getArkEsploraAddress(), active = esplora)
                configuredEsploraUrl = esplora
                rememberSuccessfulEsplora(esplora)
                val degraded = credentials.onchainMnemonic != null
                if (degraded) {
                    lastOnchainReopenAttemptMs = System.currentTimeMillis()
                    emitOnchainUnavailableThrottled(force = true)
                }
                return OpenedArkWallet(
                    wallet = opened,
                    onchain = null,
                    onchainDegraded = degraded,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastError = e
                SecureLog.w(TAG, "Ark open failed ($esplora): ${publicError(e)}")
                if (!isChainSourceError(e) && !isDnsOrNetworkError(e)) {
                    stopEsploraRelays()
                    throw e
                }
                stopEsploraRelays()
            }
        }
        stopEsploraRelays()
        throw lastError ?: Exception("Ark open failed")
    }

    /**
     * True when this session opened off-chain-only but the wallet can have an on-chain
     * wallet — boarding silently no-ops until a re-open succeeds. Debounced.
     */
    private fun shouldRetryOnchainReopen(walletId: String): Boolean {
        if (walletId.isBlank()) return false
        if (onchainWallet != null) return false
        val canHaveOnchain =
            runCatching { resolveBarkCredentials(walletId).onchainMnemonic != null }
                .getOrDefault(false)
        if (!canHaveOnchain) return false
        val now = System.currentTimeMillis()
        if (now - lastOnchainReopenAttemptMs < ONCHAIN_REOPEN_DEBOUNCE_MS) return false
        lastOnchainReopenAttemptMs = now
        return true
    }

    private fun markOnchainRecoverSuppressedLocked(
        walletId: String?,
        suppressed: Boolean,
    ) {
        suppressStaleOnchainPaint = suppressed
        if (walletId.isNullOrBlank()) return
        secureStorage.setArkOnchainRecoverSuppressed(walletId, suppressed)
    }

    private fun emitOnchainUnavailableThrottled(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastOnchainUnavailableEmitMs < ONCHAIN_UNAVAILABLE_COOLDOWN_MS) return
        lastOnchainUnavailableEmitMs = now
        _events.tryEmit(
            ArkEvent.OnchainUnavailable(
                message = localizedString(R.string.ark_onchain_unavailable),
            ),
        )
    }

    private fun emitBoardBelowMinimumThrottled(
        onchainConfirmedSats: Long,
        minBoardAmountSats: Long,
    ) {
        val now = System.currentTimeMillis()
        if (now - lastBoardBelowMinEmitMs < ONCHAIN_UNAVAILABLE_COOLDOWN_MS) return
        lastBoardBelowMinEmitMs = now
        val shortfall =
            ArkDepositPolicy.shortfallToMinBoard(onchainConfirmedSats, minBoardAmountSats)
        val minLabel = "$minBoardAmountSats sats"
        val message =
            if (shortfall != null && shortfall > 0L) {
                localizedString(
                    R.string.ark_board_below_min_shortfall_format,
                    "$onchainConfirmedSats sats",
                    minLabel,
                    "$shortfall sats",
                )
            } else {
                localizedString(
                    R.string.ark_board_below_min_format,
                    "$onchainConfirmedSats sats",
                    minLabel,
                )
            }
        _events.tryEmit(
            ArkEvent.BoardBelowMinimum(
                onchainConfirmedSats = onchainConfirmedSats,
                minBoardAmountSats = minBoardAmountSats,
                shortfallSats = shortfall,
                message = message,
            ),
        )
    }

    private suspend fun openBarkWallet(
        mnemonicOrSeed: String,
        config: Config,
        datadir: String,
        onchain: OnchainWallet?,
        skipRecovery: Boolean,
    ): Wallet {
        val openArgs =
            WalletOpenArgs(
                runDaemon = true,
                datadir = datadir,
                onchain = onchain,
                createIfNotExists = true,
                createWithoutServer = false,
                // Fresh DB: mailbox recovery. Reused cache DB: skip (hydrate still runs).
                skipRecovery = skipRecovery,
            )
        return Wallet.open(
            network = Network.BITCOIN,
            mnemonicOrSeed = mnemonicOrSeed,
            config = config,
            args = openArgs,
        )
    }

    /**
     * Bark [FeeEstimate] has no explicit sat/vB; approximate from fee vs amount delta when
     * the package looks like a typical single-input board/offboard (≈140–250 vB).
     * Returns null when fee is zero/unknown so the UI doesn't show a fake rate.
     */
    private fun feeRateFromEstimate(estimate: FeeEstimate): Double? {
        val fee = estimate.feeSats.toLong()
        if (fee <= 0L) return null
        val gross = estimate.grossAmountSats.toLong().coerceAtLeast(0L)
        val net = estimate.netAmountSats.toLong().coerceAtLeast(0L)
        val impliedFromAmounts = (gross - net).takeIf { it > 0L }
        val feeForRate = maxOf(fee, impliedFromAmounts ?: 0L)
        // Typical P2WPKH single-in boarding/claim size band; used only for display.
        val assumedVbytes = 180.0
        val rate = feeForRate.toDouble() / assumedVbytes
        return rate.takeIf { it > 0.0 }
    }

    /**
     * Bark datadir lock-manager conflict: a timed-out prior native open/close still holds
     * the datadir lock (holder PID is this same process). Transient — retry lets the stale
     * handle finish closing before the next [Wallet.open].
     */
    private fun isDatadirLockError(error: Throwable): Boolean =
        ArkDatadirLockPolicy.isDatadirLockError(error.message)

    private fun isChainSourceError(error: Throwable): Boolean {
        val message = error.message.orEmpty().lowercase()
        return message.contains("chain source") ||
            message.contains("esplora") ||
            message.contains("dns error") ||
            message.contains("failed to lookup") ||
            message.contains("error sending request") ||
            message.contains("connection refused") ||
            message.contains("connection reset") ||
            message.contains("tls") ||
            message.contains("certificate") ||
            message.contains("http status") ||
            message.contains("status code") ||
            message.contains("503") ||
            message.contains("502") ||
            message.contains("429") ||
            message.contains("unable to get") ||
            message.contains("blockchain")
    }

    private fun isDnsOrNetworkError(error: Throwable): Boolean {
        val message = error.message.orEmpty().lowercase()
        return message.contains("dns") ||
            message.contains("lookup") ||
            message.contains("connect") ||
            message.contains("network") ||
            message.contains("timed out") ||
            message.contains("timeout") ||
            message.contains("unreachable") ||
            message.contains("no route") ||
            message.contains("broken pipe") ||
            message.contains("eof") ||
            message.contains("ssl") ||
            message.contains("handshake")
    }

    /** ASP / Esplora / lock races — keep the cached Bark DB. */
    private fun isTransientArkOpenError(error: Throwable): Boolean =
        isChainSourceError(error) || isDnsOrNetworkError(error) || isDatadirLockError(error)

    /** Signals that the on-disk Bark DB itself is unusable (safe to wipe + mailbox recover). */
    private fun isLocalBarkDbOpenError(error: Throwable): Boolean {
        val message = error.message.orEmpty().lowercase()
        return message.contains("sqlite") ||
            message.contains("database") ||
            message.contains("db.sqlite") ||
            message.contains("corrupt") ||
            message.contains("malformed") ||
            message.contains("disk image") ||
            message.contains("migration") ||
            message.contains("schema") ||
            message.contains("no such table") ||
            message.contains("file is not a database") ||
            message.contains("unable to open") ||
            message.contains("failed to open")
    }

    private fun publicError(error: Throwable): String {
        val raw =
            when (error) {
                is BarkException -> error.message
                else -> error.message
            }
        val message =
            raw
                ?.lineSequence()
                ?.firstOrNull()
                ?.take(220)
                ?.takeIf { it.isNotBlank() }
                ?: localizedString(R.string.ark_error_generic)
        // Surface clearer copy for common chain-source DNS failures / Bark jargon.
        return when {
            ArkDatadirLockPolicy.isDatadirLockError(message) ->
                localizedString(R.string.ark_error_datadir_locked)
            message.contains("dns error", ignoreCase = true) ||
                message.contains("failed to lookup", ignoreCase = true) ->
                localizedString(R.string.ark_error_esplora_dns)
            message.contains("chain source", ignoreCase = true) ->
                localizedString(R.string.ark_error_chain_source)
            else ->
                message
                    .replace(Regex("(?i)board\\s*all"), "deposit all")
                    .replace(Regex("(?i)offboard\\s*all"), "withdraw all")
                    .replace(Regex("(?i)\\bboarding\\b"), "deposit")
                    .replace(Regex("(?i)\\boffboarding\\b"), "withdrawal")
                    .replace(Regex("(?i)\\boffboard\\b"), "withdraw")
                    .replace(Regex("(?i)\\bboard\\b"), "deposit")
        }
    }

    private enum class ArkPayKind {
        ARKOOR,
        BOLT11,
        BOLT12,
        LN_ADDRESS,
        LNURL,
        ONCHAIN,
    }

    private data class ResolvedArkDestination(
        val kind: ArkPayKind,
        val payTarget: String,
        val fixedAmountSats: Long? = null,
        val displayRaw: String,
    )

    /**
     * Normalize paste/scan input into a Bark pay target via [parseSendRecipient],
     * falling back to lightweight detectors for bare forms the shared parser skips.
     */
    private fun resolveArkDestination(raw: String): ResolvedArkDestination? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        when (val parsed = parseSendRecipient(trimmed)) {
            is ParsedSendRecipient.Ark ->
                return ResolvedArkDestination(
                    kind = ArkPayKind.ARKOOR,
                    payTarget = parsed.address,
                    fixedAmountSats = parsed.amountSats,
                    displayRaw = trimmed,
                )
            is ParsedSendRecipient.Bitcoin -> {
                if (SilentPayment.isSilentPaymentAddress(parsed.address)) return null
                return ResolvedArkDestination(
                    kind = ArkPayKind.ONCHAIN,
                    payTarget = parsed.address,
                    fixedAmountSats = parsed.amountSats,
                    displayRaw = trimmed,
                )
            }
            is ParsedSendRecipient.Lightning -> {
                val target = stripLightningScheme(parsed.paymentInput)
                return when {
                    isLightningAddressPayment(parsed) || looksLikeLnAddress(target) ->
                        ResolvedArkDestination(
                            kind = ArkPayKind.LN_ADDRESS,
                            payTarget = target,
                            fixedAmountSats = parsed.amountSats,
                            displayRaw = trimmed,
                        )
                    parsed.kind == LightningKind.LNURL || looksLikeLnurl(target) ->
                        ResolvedArkDestination(
                            kind = ArkPayKind.LNURL,
                            payTarget = target,
                            fixedAmountSats = parsed.amountSats,
                            displayRaw = trimmed,
                        )
                    parsed.kind == LightningKind.BOLT12 || looksLikeBolt12(target) ->
                        ResolvedArkDestination(
                            kind = ArkPayKind.BOLT12,
                            payTarget = target,
                            fixedAmountSats = parsed.amountSats,
                            displayRaw = trimmed,
                        )
                    else ->
                        ResolvedArkDestination(
                            kind = ArkPayKind.BOLT11,
                            payTarget = target,
                            fixedAmountSats = parsed.amountSats,
                            displayRaw = trimmed,
                        )
                }
            }
            else -> Unit
        }
        // Fallback bare detectors (parser Unknown missing schemes).
        val bare = stripLightningScheme(trimmed)
        return when {
            isArkAddress(bare) ->
                ResolvedArkDestination(ArkPayKind.ARKOOR, bare, null, trimmed)
            looksLikeBolt11(bare) ->
                ResolvedArkDestination(ArkPayKind.BOLT11, bare, null, trimmed)
            looksLikeBolt12(bare) ->
                ResolvedArkDestination(ArkPayKind.BOLT12, bare, null, trimmed)
            looksLikeLnurl(bare) ->
                ResolvedArkDestination(ArkPayKind.LNURL, bare, null, trimmed)
            looksLikeLnAddress(bare) ->
                ResolvedArkDestination(ArkPayKind.LN_ADDRESS, bare, null, trimmed)
            looksLikeBitcoinAddress(bare) && !SilentPayment.isSilentPaymentAddress(bare) ->
                ResolvedArkDestination(ArkPayKind.ONCHAIN, bare, null, trimmed)
            else -> null
        }
    }

    private fun stripLightningScheme(value: String): String {
        val t = value.trim()
        return if (t.startsWith("lightning:", ignoreCase = true)) {
            t.substringAfter(':').trim()
        } else {
            t
        }
    }

    /**
     * wait=true Lightning calls throw on real failures. Non-Paid terminal status here
     * means the payment was dispatched but not yet settled — report honestly instead
     * of erroring (Bark returns InProgress for in-flight pays that settle fine).
     */
    private fun lightningSendDetail(status: LightningSendStatus): String =
        when (status) {
            is LightningSendStatus.Paid ->
                status.preimage.takeIf { it.isNotBlank() }
                    ?: localizedString(R.string.ark_send_lightning_settled_detail)
            is LightningSendStatus.InProgress ->
                localizedString(R.string.ark_send_lightning_in_progress)
            LightningSendStatus.Unknown ->
                localizedString(R.string.ark_send_lightning_unknown)
        }

    /**
     * Binary-search the largest recipient amount whose FeeEstimate.gross <= [maxBudget].
     * Bark estimates take the recipient amount; gross = amount + fees for some rails.
     */
    private suspend fun backlogMaxByBinarySearch(
        maxBudget: Long,
        estimate: suspend (Long) -> FeeEstimate,
    ): Long? {
        if (maxBudget <= 0L) return null
        var lo = 1L
        var hi = maxBudget
        var best: Long? = null
        var guard = 0
        while (lo <= hi && guard < 40) {
            guard++
            val mid = lo + (hi - lo) / 2L
            val gross =
                runCatching { estimate(mid).grossAmountSats.toLong() }
                    .getOrElse { Long.MAX_VALUE }
            if (gross <= maxBudget) {
                best = mid
                lo = mid + 1L
            } else {
                hi = mid - 1L
            }
        }
        return best
    }

    private fun isArkAddress(value: String): Boolean {
        val trimmed = value.trim()
        if (!trimmed.startsWith("ark1", ignoreCase = true)) return false
        return runCatching { validateArkAddress(trimmed) }.getOrDefault(false)
    }

    private fun looksLikeBolt11(value: String): Boolean =
        value.startsWith("lnbc", ignoreCase = true) ||
            value.startsWith("lightning:lnbc", ignoreCase = true)

    private fun looksLikeBolt12(value: String): Boolean =
        value.startsWith("lno", ignoreCase = true) ||
            value.startsWith("lightning:lno", ignoreCase = true)

    private fun looksLikeLnurl(value: String): Boolean =
        value.startsWith("lnurl1", ignoreCase = true) ||
            value.startsWith("lightning:lnurl1", ignoreCase = true)

    private fun looksLikeLnAddress(value: String): Boolean {
        val parts = value.trim().split("@", limit = 3)
        return parts.size == 2 && parts[0].isNotBlank() && parts[1].contains('.')
    }

    private fun looksLikeBitcoinAddress(value: String): Boolean {
        val v = value.trim()
        return v.startsWith("bc1", ignoreCase = true) ||
            v.startsWith("1") ||
            v.startsWith("3")
    }

    /**
     * True if [address] is known used: durable prefs, Bark history/metadata, or funded BTC
     * deposit evidence (pending deposit / Esplora history).
     */
    private fun isAddressUsed(
        address: String,
        movements: List<ArkMovement> = _arkState.value.movements,
        walletId: String? = _loadedWalletId.value ?: _arkState.value.walletId,
    ): Boolean {
        val needle = address.trim()
        if (needle.isEmpty()) return false
        if (walletId != null) {
            if (secureStorage.isArkReceiveAddressUsed(walletId, needle)) return true
            if (secureStorage.isArkOnchainDepositAddressUsed(walletId, needle)) return true
        }
        return movements.any { m ->
            m.receivedOnAddresses.any { it.equals(needle, ignoreCase = true) } ||
                m.sentToAddresses.any { it.equals(needle, ignoreCase = true) } ||
                m.metadataJson
                    ?.let { parseMovementMetadata(it).addresses }
                    ?.any { it.equals(needle, ignoreCase = true) } == true
        }
    }

    /** Persist any addresses proven used by the current movement set. */
    private fun rememberUsedAddressesFromMovements(
        walletId: String,
        movements: List<ArkMovement>,
    ) {
        movements.forEach { m ->
            val candidates =
                (
                    m.receivedOnAddresses +
                        m.sentToAddresses +
                        (
                            m.metadataJson
                                ?.let { parseMovementMetadata(it).addresses }
                                .orEmpty()
                        )
                ).map { it.trim() }.filter { it.isNotEmpty() }
            candidates.forEach { addr ->
                when {
                    looksLikeBitcoinAddress(addr) ->
                        secureStorage.markArkOnchainDepositAddressUsed(walletId, addr)
                    else -> secureStorage.markArkReceiveAddressUsed(walletId, addr)
                }
            }
        }
    }

    /**
     * Local-only address mint right after Bark open. Skips history/Esplora so Receive can
     * paint before [refreshStateLocked] finishes.
     */
    private suspend fun publishReceiveAddressesFastLocked(walletId: String) {
        val w = wallet ?: return
        val ark =
            resolveUnusedArkAddressLocked(w, walletId, forceNew = false)
                ?: runCatching { w.newAddress() }.getOrNull()?.takeIf { it.isNotBlank() }?.also {
                    secureStorage.setArkReceiveAddress(walletId, it)
                }
        // Prefer one quick onchain.newAddress() when cache is empty — don't burn the
        // reveal-catchup loop on first paint (that runs later during board).
        val bitcoin =
            runCatching {
                val cached =
                    secureStorage
                        .getArkOnchainDepositAddress(walletId)
                        ?.takeIf { it.isNotBlank() }
                        ?.takeUnless { isAddressUsed(it, walletId = walletId) }
                if (!cached.isNullOrBlank()) return@runCatching cached
                val onchain = onchainWallet ?: return@runCatching null
                onchain.newAddress().takeIf { it.isNotBlank() }?.also {
                    secureStorage.setArkOnchainDepositAddress(walletId, it)
                }
            }.getOrNull()?.takeIf { it.isNotBlank() }
        if (!ark.isNullOrBlank()) {
            _arkState.value = _arkState.value.copy(currentAddress = ark)
            secureStorage.setArkReceiveAddress(walletId, ark)
        }
        if (!bitcoin.isNullOrBlank()) {
            secureStorage.setArkOnchainDepositAddress(walletId, bitcoin)
        }
        // Prefer promoting whichever kind Receive is already waiting on.
        val current = _receiveState.value
        when (current) {
            is ArkReceiveState.Ready -> {
                when (current.kind) {
                    ArkReceiveKind.ARK_ADDRESS -> {
                        if (
                            !ark.isNullOrBlank() &&
                                (
                                    current.paymentRequest.isBlank() ||
                                        isAddressUsed(current.paymentRequest, walletId = walletId)
                                )
                        ) {
                            _receiveState.value =
                                ArkReceiveState.Ready(
                                    kind = ArkReceiveKind.ARK_ADDRESS,
                                    paymentRequest = ark,
                                )
                        }
                    }
                    ArkReceiveKind.BITCOIN_ADDRESS -> {
                        if (
                            !bitcoin.isNullOrBlank() &&
                                (
                                    current.paymentRequest.isBlank() ||
                                        isAddressUsed(current.paymentRequest, walletId = walletId)
                                )
                        ) {
                            _receiveState.value =
                                ArkReceiveState.Ready(
                                    kind = ArkReceiveKind.BITCOIN_ADDRESS,
                                    paymentRequest = bitcoin,
                                )
                        }
                    }
                    ArkReceiveKind.BOLT11_INVOICE -> Unit
                }
            }
            is ArkReceiveState.Loading,
            is ArkReceiveState.Idle,
            is ArkReceiveState.Error,
            -> {
                when {
                    !ark.isNullOrBlank() ->
                        _receiveState.value =
                            ArkReceiveState.Ready(
                                kind = ArkReceiveKind.ARK_ADDRESS,
                                paymentRequest = ark,
                            )
                    !bitcoin.isNullOrBlank() ->
                        _receiveState.value =
                            ArkReceiveState.Ready(
                                kind = ArkReceiveKind.BITCOIN_ADDRESS,
                                paymentRequest = bitcoin,
                            )
                }
            }
            else -> Unit
        }
        // Keep the other kind warm in prefs / state for instant tab switch.
        primeReceiveStateIfNeededLocked(arkAddress = ark, bitcoinAddress = bitcoin)
    }

    /**
     * Return a cached unused Ark address, or generate a new one when [forceNew] or used.
     * Must run under [mutex].
     */
    private suspend fun resolveUnusedArkAddressLocked(
        w: Wallet,
        walletId: String?,
        forceNew: Boolean,
    ): String? {
        if (!forceNew) {
            val cached =
                _arkState.value.currentAddress?.takeIf { it.isNotBlank() }
                    ?: walletId?.let { secureStorage.getArkReceiveAddress(it) }
            if (!cached.isNullOrBlank() && !isAddressUsed(cached, walletId = walletId)) {
                if (walletId != null) secureStorage.setArkReceiveAddress(walletId, cached)
                return cached
            }
            if (!cached.isNullOrBlank() && walletId != null) {
                secureStorage.markArkReceiveAddressUsed(walletId, cached)
            }
        }
        // Generate until unused (cap avoids infinite loop if history is noisy).
        repeat(16) {
            val fresh =
                runCatching { w.newAddress() }.getOrNull()?.takeIf { it.isNotBlank() } ?: return null
            if (!isAddressUsed(fresh, walletId = walletId)) {
                if (walletId != null) secureStorage.setArkReceiveAddress(walletId, fresh)
                return fresh
            }
            if (walletId != null) {
                secureStorage.markArkReceiveAddressUsed(walletId, fresh)
            }
        }
        return runCatching { w.newAddress() }.getOrNull()?.also { fresh ->
            if (walletId != null && fresh.isNotBlank()) {
                secureStorage.setArkReceiveAddress(walletId, fresh)
            }
        }
    }

    /**
     * Return a cached unused Bark on-chain deposit address, or generate when [forceNew]/used.
     * Must run under [mutex].
     */
    private suspend fun resolveUnusedBitcoinDepositAddressLocked(forceNew: Boolean): String? {
        val walletId = _loadedWalletId.value
        val onchain = onchainWallet ?: return null
        if (!forceNew) {
            val id = walletId
            val cached = id?.let { secureStorage.getArkOnchainDepositAddress(it) }
            if (!cached.isNullOrBlank() && !isAddressUsed(cached, walletId = id)) {
                return cached
            }
            if (id != null && !cached.isNullOrBlank()) {
                secureStorage.markArkOnchainDepositAddressUsed(id, cached)
            }
        }
        repeat(16) {
            val fresh =
                runCatching { onchain.newAddress() }.getOrNull()?.takeIf { it.isNotBlank() }
                    ?: return null
            if (!isAddressUsed(fresh, walletId = walletId)) {
                if (walletId != null) secureStorage.setArkOnchainDepositAddress(walletId, fresh)
                return fresh
            }
            if (walletId != null) {
                secureStorage.markArkOnchainDepositAddressUsed(walletId, fresh)
            }
        }
        return runCatching { onchain.newAddress() }.getOrNull()?.also { fresh ->
            if (walletId != null && !fresh.isNullOrBlank()) {
                secureStorage.setArkOnchainDepositAddress(walletId, fresh)
            }
        }
    }

    /**
     * After history is known, ensure cached Ark receive is unused; rotate if needed.
     */
    private suspend fun ensureReceiveAddressUnusedLocked(
        w: Wallet,
        walletId: String,
        movements: List<ArkMovement>,
        preferredArk: String?,
    ): String? {
        val current =
            preferredArk?.takeIf { it.isNotBlank() }
                ?: secureStorage.getArkReceiveAddress(walletId)
                ?: return null
        if (!isAddressUsed(current, movements = movements, walletId = walletId)) return current
        secureStorage.markArkReceiveAddressUsed(walletId, current)
        return resolveUnusedArkAddressLocked(w, walletId, forceNew = true)
    }

    /** If receive UI is showing a now-used address, advance it to a fresh unused one. */
    private suspend fun advanceOpenReceiveIfAddressUsedLocked() {
        val ready = _receiveState.value as? ArkReceiveState.Ready ?: return
        val shown = ready.paymentRequest.trim()
        if (shown.isEmpty()) return
        val walletId = _loadedWalletId.value
        when (ready.kind) {
            ArkReceiveKind.ARK_ADDRESS -> {
                if (!isAddressUsed(shown, walletId = walletId)) return
                val w = wallet ?: return
                if (walletId != null) secureStorage.markArkReceiveAddressUsed(walletId, shown)
                val next = resolveUnusedArkAddressLocked(w, walletId, forceNew = true) ?: return
                _receiveState.value =
                    ArkReceiveState.Ready(
                        kind = ArkReceiveKind.ARK_ADDRESS,
                        paymentRequest = next,
                    )
                _arkState.value = _arkState.value.copy(currentAddress = next)
            }
            ArkReceiveKind.BITCOIN_ADDRESS -> {
                if (!isAddressUsed(shown, walletId = walletId)) return
                if (walletId != null) {
                    secureStorage.markArkOnchainDepositAddressUsed(walletId, shown)
                }
                val next = resolveUnusedBitcoinDepositAddressLocked(forceNew = true) ?: return
                _receiveState.value =
                    ArkReceiveState.Ready(
                        kind = ArkReceiveKind.BITCOIN_ADDRESS,
                        paymentRequest = next,
                    )
            }
            ArkReceiveKind.BOLT11_INVOICE -> Unit
        }
    }

    /**
     * Stamp [pendingSendDestination] onto the newest matching outbound movement.
     * History is typically oldest-first — always prefer highest movement id / last match.
     */
    private fun attachPendingSendDestinationLocked(walletId: String) {
        val dest = pendingSendDestination?.takeIf { it.isNotBlank() } ?: return
        val updated = attachPendingSendDestinationToList(walletId, _arkState.value.movements)
        if (updated !== _arkState.value.movements) {
            _arkState.value = _arkState.value.copy(movements = updated)
        }
    }

    private fun attachPendingSendDestinationToList(
        walletId: String,
        movements: List<ArkMovement>,
    ): List<ArkMovement> {
        val dest = pendingSendDestination?.takeIf { it.isNotBlank() } ?: return movements
        val kind = pendingSendKind ?: ArkPayKind.ARKOOR
        val amount = pendingSendAmountSats
        val candidates =
            movements.filter { m ->
                m.effectiveBalanceSats < 0L &&
                    when (kind) {
                        ArkPayKind.ARKOOR ->
                            m.paymentHash.isNullOrBlank() &&
                                m.lightningInvoice.isNullOrBlank() &&
                                !m.subsystemName.lowercase(Locale.US).contains("board") &&
                                !m.subsystemKind.lowercase(Locale.US).contains("board")
                        ArkPayKind.ONCHAIN ->
                            m.onchainTxids.isNotEmpty() ||
                                m.subsystemName.lowercase(Locale.US).let {
                                    it.contains("offboard") || it.contains("onchain") || it.contains("bitcoin")
                                }
                        else -> false
                    }
            }
        // Prefer newest (highest id); Bark ids increase. Fall back to last in list.
        val newest =
            if (amount != null && amount > 0L) {
                candidates
                    .filter { kotlin.math.abs(it.effectiveBalanceSats) == amount }
                    .maxByOrNull { it.id }
                    ?: candidates
                        .filter {
                            kotlin.math.abs(kotlin.math.abs(it.effectiveBalanceSats) - amount) <= 2L
                        }
                        .maxByOrNull { it.id }
                    ?: candidates.maxByOrNull { it.id }
            } else {
                candidates.maxByOrNull { it.id }
            } ?: return movements
        secureStorage.setArkMovementDestination(walletId, newest.id, dest)
        pendingSendDestination = null
        pendingSendAmountSats = null
        pendingSendKind = null
        return movements.map { m ->
            if (m.id == newest.id) {
                m.copy(sentToAddresses = listOf(dest))
            } else {
                m
            }
        }
    }

    private fun Movement.toArkMovement(
        label: String?,
        storedDestination: String? = null,
        @Suppress("UNUSED_PARAMETER") ownArkAddress: String? = null,
    ): ArkMovement {
        val parsed = parseMovementMetadata(metadataJson)
        val isLightning =
            !paymentHash.isNullOrBlank() ||
                !lightningInvoice.isNullOrBlank() ||
                !lightningOffer.isNullOrBlank() ||
                listOf(subsystemName, subsystemKind)
                    .joinToString(" ")
                    .lowercase(Locale.US)
                    .let { blob ->
                        blob.contains("lightning") ||
                            blob.contains("bolt") ||
                            blob.contains("lnurl") ||
                            blob.contains("invoice")
                    }
        // Payment hashes are 64-hex and must not be treated as on-chain txids.
        val onchainTxids =
            if (isLightning) {
                emptyList()
            } else {
                parsed.txids.filterNot { txid ->
                    paymentHash?.equals(txid, ignoreCase = true) == true
                }
            }
        // Bark address lists are often empty for ARKOOR; fall back to metadata + Ibis-stored dest.
        val barkSent = sentToAddresses.filter(::looksLikeUsableAddressField)
        val barkReceived = receivedOnAddresses.filter(::looksLikeUsableAddressField)
        val metaAddrs = parsed.addresses.filter(::looksLikeUsableAddressField)
        val dest = storedDestination?.trim()?.takeIf { looksLikeUsableAddressField(it) }
        val isSend = effectiveBalanceSats < 0L
        val sent =
            when {
                barkSent.isNotEmpty() -> barkSent
                isSend && dest != null -> listOf(dest)
                isSend && metaAddrs.isNotEmpty() -> metaAddrs
                else -> emptyList()
            }
        val received =
            when {
                // Prefer genuine Bark / metadata addresses only — never inject own address here
                // (that would mark the current receive address as "used" and force churn).
                barkReceived.isNotEmpty() -> barkReceived
                !isSend && dest != null -> listOf(dest)
                !isSend && metaAddrs.isNotEmpty() -> metaAddrs
                else -> emptyList()
            }
        return ArkMovement(
            id = id.toInt(),
            status = status,
            subsystemName = subsystemName,
            subsystemKind = subsystemKind,
            intendedBalanceSats = intendedBalanceSats,
            effectiveBalanceSats = effectiveBalanceSats,
            offchainFeeSats = offchainFeeSats.toLong(),
            sentToAddresses = sent,
            receivedOnAddresses = received,
            createdAt = createdAt,
            updatedAt = updatedAt,
            completedAt = completedAt,
            paymentHash = paymentHash,
            lightningInvoice = lightningInvoice,
            lightningOffer = lightningOffer,
            label = label,
            metadataJson = metadataJson.takeIf { it.isNotBlank() },
            onchainTxids = onchainTxids,
            inputVtxoIds = inputVtxoIds.ifEmpty { parsed.inputVtxoIds },
            outputVtxoIds = outputVtxoIds.ifEmpty { parsed.outputVtxoIds },
            onchainFeeSats = if (isLightning) null else parsed.onchainFeeSats,
        )
    }

    /**
     * Bark exposes on-chain balance and pending boards separately from movement history.
     * Keep unboarded deposits visible as a synthetic inbound row until Bark publishes a
     * board movement for the same funding — including after older boards already exist
     * (self-fund Ark→own deposit must show Sent + Received).
     */
    private fun addPendingOnchainDepositMovement(
        movements: List<ArkMovement>,
        previousMovements: List<ArkMovement>,
        confirmedSats: Long,
        pendingSats: Long,
        pendingBoardSats: Long,
        pendingBoards: List<uniffi.bark.PendingBoard>,
        depositAddress: String?,
        fundingTxids: List<String> = emptyList(),
        chainDetails: ArkDepositChainDetails? = null,
        onchainUtxos: List<ArkOnchainUtxo> = emptyList(),
        requiredBoardConfirmations: Int = DEFAULT_BOARD_CONFIRMATIONS,
    ): List<ArkMovement> {
        // Annotate only board rows that match this funding/board txid (never all boards).
        val annotated =
            if (chainDetails == null) {
                movements
            } else {
                val fundingKey = chainDetails.fundingTxid.trim().lowercase(Locale.US)
                val boardKey = chainDetails.boardTxid?.trim()?.lowercase(Locale.US)
                movements.map { movement ->
                    if (!ArkDepositPolicy.isBoardDepositMovement(movement)) return@map movement
                    val chainIds = ArkDepositPolicy.movementChainTxids(movement)
                    val matches =
                        (fundingKey.length == 64 && fundingKey in chainIds) ||
                            (boardKey != null && boardKey.length == 64 && boardKey in chainIds)
                    if (!matches) {
                        movement
                    } else {
                        movement.copy(
                            receivedOnAddresses =
                                listOfNotNull(
                                    chainDetails.address.takeIf { it.isNotBlank() },
                                ).ifEmpty { movement.receivedOnAddresses },
                            onchainTxids =
                                listOfNotNull(
                                    chainDetails.fundingTxid.takeIf { it.length == 64 },
                                ).ifEmpty { movement.onchainTxids },
                            boardTxid = chainDetails.boardTxid ?: movement.boardTxid,
                            fundingConfirmations = chainDetails.fundingConfirmations,
                            boardConfirmations = chainDetails.boardConfirmations,
                            requiredBoardConfirmations = requiredBoardConfirmations,
                        )
                    }
                }
            }

        val onchainTotal = (confirmedSats + pendingSats).coerceAtLeast(0L)
        val pendingBoardTotal =
            pendingBoards.sumOf { it.amountSats.toLong() }.coerceAtLeast(pendingBoardSats)
        val minBoard =
            _arkState.value.minBoardAmountSats?.takeIf { it > 0L }
        val now = Instant.now().toString()
        val boardTxid = chainDetails?.boardTxid ?: pendingBoards.firstOrNull()?.txid?.trim()
        val boardVtxoIds = pendingBoards.map { it.vtxoId }.filter { it.isNotBlank() }

        // One history row per unspent deposit UTXO (0-conf included).
        val depositUtxos =
            onchainUtxos.filter { it.amountSats > 0L }.ifEmpty {
                val chainAmt = chainDetails?.amountSats?.takeIf { it > 0L }
                val fallbackAmt = chainAmt ?: maxOf(onchainTotal, pendingBoardTotal)
                if (fallbackAmt <= 0L) {
                    emptyList()
                } else {
                    val confs = chainDetails?.fundingConfirmations ?: if (confirmedSats > 0L) 1 else 0
                    listOf(
                        ArkOnchainUtxo(
                            txid = chainDetails?.fundingTxid.orEmpty(),
                            vout = chainDetails?.fundingVout ?: 0,
                            amountSats = fallbackAmt,
                            confirmations = confs.coerceAtLeast(0),
                            address =
                                chainDetails?.address
                                    ?: depositAddress.orEmpty(),
                            isConfirmed = confs > 0 || confirmedSats > 0L,
                        ),
                    )
                }
            }

        // Drop all prior synthetic pending deposits; rebuild from live UTXOs.
        val withoutSynthetics =
            annotated.filterNot { ArkDepositPolicy.isSyntheticPendingOnchainDeposit(it) }

        if (depositUtxos.isEmpty()) {
            return withoutSynthetics
        }

        val previousByOutpoint =
            (previousMovements + annotated)
                .filter { ArkDepositPolicy.isSyntheticPendingOnchainDeposit(it) }
                .associateBy { m ->
                    val tx = m.onchainTxids.firstOrNull()?.lowercase(Locale.US).orEmpty()
                    // Prefer outpoint id match; fall back to funding txid key.
                    if (tx.length == 64) tx else m.id.toString()
                }

        val syntheticRows =
            depositUtxos.mapNotNull { utxo ->
                val fundingTxid = utxo.txid.trim().lowercase(Locale.US)
                // Skip if Bark already published a real board for this funding tx.
                if (
                    fundingTxid.length == 64 &&
                    withoutSynthetics.any { m ->
                        ArkDepositPolicy.isBoardDepositMovement(m) &&
                            !ArkDepositPolicy.isSyntheticPendingOnchainDeposit(m) &&
                            fundingTxid in ArkDepositPolicy.movementChainTxids(m)
                    }
                ) {
                    return@mapNotNull null
                }
                val movementId =
                    if (fundingTxid.length == 64) {
                        ArkDepositPolicy.pendingOnchainOutpointMovementId(fundingTxid, utxo.vout)
                    } else {
                        ArkDepositPolicy.PENDING_ONCHAIN_DEPOSIT_MOVEMENT_ID
                    }
                val previous =
                    previousByOutpoint[fundingTxid]
                        ?: previousByOutpoint[movementId.toString()]
                        ?: previousMovements.firstOrNull {
                            it.id == movementId ||
                                (
                                    fundingTxid.length == 64 &&
                                        fundingTxid in
                                        it.onchainTxids.map { t -> t.lowercase(Locale.US) }
                                )
                        }
                val amount = utxo.amountSats.coerceAtLeast(0L)
                val confs = utxo.confirmations.coerceAtLeast(0)
                val belowMin =
                    pendingBoardTotal <= 0L &&
                        utxo.isConfirmed &&
                        ArkDepositPolicy.isBelowMinBoardAmount(amount, minBoard)
                val status =
                    when {
                        pendingBoardTotal > 0L -> "boarding"
                        !utxo.isConfirmed || confs <= 0 -> "confirming"
                        belowMin -> ArkDepositPolicy.STATUS_BELOW_MIN
                        else -> "pending"
                    }
                val address =
                    utxo.address.takeIf { it.isNotBlank() }
                        ?: chainDetails?.address?.takeIf { it.isNotBlank() }
                        ?: previous?.receivedOnAddresses?.firstOrNull()?.takeIf { it.isNotBlank() }
                        ?: depositAddress?.takeIf { it.isNotBlank() }
                ArkMovement(
                    id = movementId,
                    status = status,
                    subsystemName = "Bitcoin",
                    subsystemKind = "board",
                    intendedBalanceSats = amount,
                    effectiveBalanceSats = amount,
                    offchainFeeSats = 0L,
                    receivedOnAddresses = listOfNotNull(address),
                    createdAt = previous?.createdAt ?: now,
                    updatedAt = now,
                    onchainTxids =
                        listOfNotNull(fundingTxid.takeIf { it.length == 64 })
                            .ifEmpty { fundingTxids.map { it.lowercase(Locale.US) }.filter { it.length == 64 } },
                    outputVtxoIds = boardVtxoIds,
                    boardTxid = boardTxid?.takeIf { it.length == 64 },
                    fundingConfirmations = confs,
                    boardConfirmations = chainDetails?.boardConfirmations,
                    requiredBoardConfirmations = requiredBoardConfirmations,
                )
            }

        if (syntheticRows.isEmpty()) return withoutSynthetics
        return syntheticRows + withoutSynthetics
    }

    private fun recoveredOnchainDepositsToMovements(
        walletId: String,
    ): List<ArkMovement> =
        secureStorage.getArkRecoveredOnchainDeposits(walletId)
            // One history row per recover sweep, even if older builds wrote one record
            // per painted funding UTXO.
            .distinctBy { deposit ->
                deposit.recoverTxid.trim().lowercase().ifBlank {
                    deposit.fundingTxid.trim().lowercase()
                }
            }
            .map { deposit ->
            val fundingKey =
                deposit.fundingTxid.trim().lowercase().ifBlank {
                    deposit.recoverTxid.trim().lowercase()
                }
            val onchainTxids =
                listOf(deposit.fundingTxid, deposit.recoverTxid)
                    .map { it.trim().lowercase() }
                    .filter { it.length == 64 }
                    .distinct()
            ArkMovement(
                id = ArkDepositPolicy.recoveredOnchainMovementId(fundingKey),
                status = ArkDepositPolicy.STATUS_RECOVERED_L1,
                subsystemName = "Bitcoin",
                subsystemKind = "board",
                intendedBalanceSats = deposit.amountSats,
                effectiveBalanceSats = deposit.amountSats,
                offchainFeeSats = 0L,
                sentToAddresses =
                    listOfNotNull(deposit.destinationAddress.takeIf { it.isNotBlank() }),
                receivedOnAddresses =
                    listOfNotNull(deposit.depositAddress?.takeIf { it.isNotBlank() }),
                createdAt = deposit.createdAt.ifBlank { deposit.recoveredAt },
                updatedAt = deposit.recoveredAt,
                completedAt = deposit.recoveredAt,
                onchainTxids = onchainTxids,
            )
        }

    private fun snapshotPendingDepositForRecoverLocked(): ArkMovement? {
        val synthetics =
            _arkState.value.movements.filter {
                ArkDepositPolicy.isSyntheticPendingOnchainDeposit(it)
            }
        if (synthetics.isEmpty()) return null
        // Recover sweeps all confirmed on-chain; snapshot largest for history amount/txid.
        return synthetics.maxByOrNull { it.effectiveBalanceSats }
    }

    private fun persistRecoveredOnchainDepositLocked(
        walletId: String?,
        destinationAddress: String,
        recoverTxid: String,
        snapshot: ArkMovement?,
        amountSats: Long,
        paintedUtxos: List<ArkOnchainUtxo> = emptyList(),
    ) {
        if (walletId.isNullOrBlank()) return
        val fundingTxid =
            snapshot?.onchainTxids?.firstOrNull { it.length == 64 }.orEmpty().ifBlank {
                // Prefer longest-known funding id from secure storage when synthetic row lacked it.
                secureStorage.getArkFundingTxids(walletId).lastOrNull().orEmpty()
            }
        // Suppress every painted funding output + the recover sweep itself, not just the
        // snapshot row — leftover recover change must never re-paint as a fresh deposit.
        val suppressTxids =
            (
                paintedUtxos.map { it.txid } +
                    listOfNotNull(
                        fundingTxid.takeIf { it.length == 64 },
                        recoverTxid.takeIf { it.length == 64 },
                    )
            )
        secureStorage.addArkRecoveredTxids(walletId, suppressTxids)
        val now = Instant.now().toString()
        secureStorage.saveArkRecoveredOnchainDeposit(
            walletId,
            ArkRecoveredOnchainDeposit(
                fundingTxid = fundingTxid.ifBlank { recoverTxid },
                amountSats = amountSats.coerceAtLeast(snapshot?.effectiveBalanceSats ?: 0L),
                destinationAddress = destinationAddress,
                recoverTxid = recoverTxid,
                depositAddress = snapshot?.receivedOnAddresses?.firstOrNull(),
                createdAt = snapshot?.createdAt ?: now,
                recoveredAt = now,
            ),
        )
    }

    private fun recoveredOnchainTxidsLocked(walletId: String): Set<String> =
        secureStorage.getArkRecoveredTxids(walletId) +
            secureStorage.getArkRecoveredOnchainDeposits(walletId)
                .flatMap { deposit ->
                    listOf(deposit.fundingTxid, deposit.recoverTxid)
                }
                .map { it.trim().lowercase() }
                .filter { it.length == 64 }

    private fun boardedOnchainTxidsLocked(
        walletId: String,
        previousMovements: List<ArkMovement>,
    ): Set<String> =
        (
            previousMovements +
                recoveredOnchainDepositsToMovements(walletId)
        ).filter { movement ->
            ArkDepositPolicy.isBoardDepositMovement(movement) &&
                !ArkDepositPolicy.isSyntheticPendingOnchainDeposit(movement) &&
                !ArkDepositPolicy.isRecoveredOnchainMovement(movement)
        }
            .flatMap { ArkDepositPolicy.movementChainTxids(it) }
            .toSet()

    private data class ArkDepositChainDetails(
        val fundingTxid: String,
        val fundingVout: Int,
        val address: String,
        val amountSats: Long,
        val fundingConfirmations: Int,
        val boardTxid: String?,
        val boardConfirmations: Int,
    )

    /**
     * Resolve actual Bitcoin details from Esplora. A pending-board tx spends the external
     * funding output, so its vin.prevout is authoritative for Received at / amount.
     */
    private fun resolveArkDepositChainDetails(
        walletId: String,
        pendingBoards: List<uniffi.bark.PendingBoard>,
        fallbackAmountSats: Long,
        tipHeight: Int?,
        knownUtxos: List<ArkOnchainUtxo> = emptyList(),
    ): ArkDepositChainDetails? {
        // Live pending boards only — historical board txids resurrect already-boarded funding.
        val liveBoardTxids =
            pendingBoards
                .map { it.txid.trim().lowercase() }
                .filter { it.length == 64 }
                .distinct()
        for (boardTxid in liveBoardTxids) {
            fetchEsploraTransaction(boardTxid)?.let { boardTx ->
                val boardStatus = boardTx.optJSONObject("status")
                // Store uncapped confs; UI caps only the X/required progress label.
                val boardConfirmations = esploraConfirmationCount(boardStatus, tipHeight)
                val inputs = boardTx.optJSONArray("vin")
                for (index in 0 until (inputs?.length() ?: 0)) {
                    val input = inputs?.optJSONObject(index) ?: continue
                    val prevout = input.optJSONObject("prevout") ?: continue
                    val address = prevout.optString("scriptpubkey_address").trim()
                    val amount = prevout.optLong("value", 0L)
                    val fundingTxid = input.optString("txid").trim()
                    val fundingVout = input.optInt("vout", -1)
                    if (
                        address.isNotBlank() &&
                        amount > 0L &&
                        fundingTxid.length == 64 &&
                        fundingVout >= 0
                    ) {
                        secureStorage.rememberArkOnchainDepositAddress(walletId, address)
                        val fundingStatus = fetchEsploraTransaction(fundingTxid)?.optJSONObject("status")
                        return ArkDepositChainDetails(
                            fundingTxid = fundingTxid,
                            fundingVout = fundingVout,
                            address = address,
                            amountSats = amount,
                            fundingConfirmations = esploraConfirmationCount(fundingStatus, tipHeight),
                            boardTxid = boardTxid,
                            boardConfirmations = boardConfirmations,
                        )
                    }
                }
            }
        }

        // Reuse UTXOs already listed this refresh — avoid a second address crawl.
        if (knownUtxos.isNotEmpty()) {
            val best =
                knownUtxos.minByOrNull {
                    kotlin.math.abs(it.amountSats - fallbackAmountSats)
                } ?: return null
            if (best.txid.length != 64 || best.amountSats <= 0L) return null
            return ArkDepositChainDetails(
                fundingTxid = best.txid,
                fundingVout = best.vout,
                address = best.address,
                amountSats = best.amountSats,
                fundingConfirmations = best.confirmations,
                boardTxid = null,
                boardConfirmations = 0,
            )
        }

        // Before Bark creates a board tx, only unspent deposit outputs (spent funding after
        // boardAll must not re-inflate on-chain paint / double the balance).
        val addresses = secureStorage.getArkOnchainDepositAddressHistory(walletId)
        val candidates =
            addresses.flatMap { address ->
                fetchEsploraAddressUtxos(address).mapNotNull { utxo ->
                    val txid = utxo.optString("txid").trim()
                    val vout = utxo.optInt("vout", -1)
                    val amount = utxo.optLong("value", 0L)
                    if (txid.length != 64 || vout < 0 || amount <= 0L) return@mapNotNull null
                    val status = utxo.optJSONObject("status")
                    ArkDepositChainDetails(
                        fundingTxid = txid,
                        fundingVout = vout,
                        address = address,
                        amountSats = amount,
                        fundingConfirmations = esploraConfirmationCount(status, tipHeight),
                        boardTxid = null,
                        boardConfirmations = 0,
                    )
                }
            }
        return candidates.minByOrNull { kotlin.math.abs(it.amountSats - fallbackAmountSats) }
    }

    private fun fetchEsploraTransaction(txid: String): org.json.JSONObject? =
        fetchEsploraJson("tx/$txid") as? org.json.JSONObject

    private fun fetchEsploraOutspend(
        txid: String,
        vout: Int,
    ): org.json.JSONObject? = fetchEsploraJson("tx/$txid/outspend/$vout") as? org.json.JSONObject

    private fun fetchEsploraAddressUtxos(address: String): List<org.json.JSONObject> {
        val key = address.trim()
        if (key.isEmpty()) return emptyList()
        val now = System.currentTimeMillis()
        esploraUtxoCache[key]?.let { cached ->
            if (now - cached.fetchedAtMs <= ESPLORA_UTXO_CACHE_MS) return cached.utxos
        }
        val array =
            fetchEsploraJson("address/$key/utxo") as? org.json.JSONArray ?: return emptyList()
        val utxos = List(array.length()) { index -> array.optJSONObject(index) }.filterNotNull()
        esploraUtxoCache[key] = CachedEsploraUtxos(fetchedAtMs = now, utxos = utxos)
        return utxos
    }

    /**
     * Esplora UTXOs across remembered Bark deposit addresses.
     * Includes **0-conf / mempool** outputs via `/utxo`. Falls back to address tx history only
     * when `/utxo` is empty and Bark/previous state already shows on-chain funds (slow path).
     */
    private fun listArkOnchainUtxosLocked(
        walletId: String,
        tipHeight: Int?,
        extraAddresses: List<String> = emptyList(),
        allowTxHistoryFallback: Boolean = false,
    ): List<ArkOnchainUtxo> {
        val addresses =
            (
                secureStorage.getArkOnchainDepositAddressHistory(walletId) +
                    listOfNotNull(secureStorage.getArkOnchainDepositAddress(walletId)) +
                    extraAddresses
            ).map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
        if (addresses.isEmpty()) return emptyList()
        return addresses
            .flatMap { address ->
                val fromUtxoEndpoint =
                    fetchEsploraAddressUtxos(address).mapNotNull { utxo ->
                        parseEsploraUtxoJson(utxo, address, tipHeight)
                    }
                if (fromUtxoEndpoint.isNotEmpty() || !allowTxHistoryFallback) {
                    fromUtxoEndpoint
                } else {
                    // Slow path: /txs + outspend only when /utxo is empty but funds are expected.
                    fetchEsploraAddressUnspentFromTxs(address, tipHeight)
                }
            }
            .distinctBy { it.outpoint }
            .sortedWith(
                // Pending (0-conf) first so new deposits are obvious.
                compareBy<ArkOnchainUtxo> { it.isConfirmed }
                    .thenByDescending { it.amountSats },
            )
    }

    private fun parseEsploraUtxoJson(
        utxo: org.json.JSONObject,
        address: String,
        tipHeight: Int?,
    ): ArkOnchainUtxo? {
        val txid = utxo.optString("txid").trim().lowercase(Locale.US)
        val vout = utxo.optInt("vout", -1)
        val amount = utxo.optLong("value", 0L)
        if (txid.length != 64 || vout < 0 || amount <= 0L) return null
        val status = utxo.optJSONObject("status")
        val confs = esploraConfirmationCount(status, tipHeight)
        val confirmed = status?.optBoolean("confirmed", false) == true || confs > 0
        return ArkOnchainUtxo(
            txid = txid,
            vout = vout,
            amountSats = amount,
            confirmations = confs,
            address = address,
            isConfirmed = confirmed,
        )
    }

    /**
     * Unspent outputs from address tx history (includes mempool). Used when `/utxo` is empty
     * or lags on 0-conf; skips spent outs via outspend check.
     */
    private fun fetchEsploraAddressUnspentFromTxs(
        address: String,
        tipHeight: Int?,
    ): List<ArkOnchainUtxo> {
        // Cap history + outspend fan-out — full address history is too slow over Tor/clearnet.
        val txs = fetchEsploraAddressTransactions(address).take(ESPLORA_TX_HISTORY_FALLBACK_LIMIT)
        return txs.flatMap { tx ->
            val txid = tx.optString("txid").trim().lowercase(Locale.US)
            if (txid.length != 64) return@flatMap emptyList()
            val status = tx.optJSONObject("status")
            val confs = esploraConfirmationCount(status, tipHeight)
            val confirmed = status?.optBoolean("confirmed", false) == true || confs > 0
            val outputs = tx.optJSONArray("vout") ?: return@flatMap emptyList()
            buildList {
                for (index in 0 until outputs.length()) {
                    val output = outputs.optJSONObject(index) ?: continue
                    if (
                        !output.optString("scriptpubkey_address")
                            .equals(address, ignoreCase = true)
                    ) {
                        continue
                    }
                    val amount = output.optLong("value", 0L)
                    if (amount <= 0L) continue
                    val outspend = fetchEsploraOutspend(txid, index)
                    val spent = outspend?.optBoolean("spent")
                    if (spent == true) continue
                    // Do not resurrect old confirmed funding when outspend lookup failed.
                    // Mempool outputs may be absent from /utxo briefly, so retain that fallback.
                    if (confirmed && spent != false) continue
                    add(
                        ArkOnchainUtxo(
                            txid = txid,
                            vout = index,
                            amountSats = amount,
                            confirmations = confs,
                            address = address,
                            isConfirmed = confirmed,
                        ),
                    )
                }
            }
        }
    }

    private fun fetchEsploraAddressTransactions(address: String): List<org.json.JSONObject> {
        val array = fetchEsploraJson("address/$address/txs") as? org.json.JSONArray ?: return emptyList()
        return List(array.length()) { index -> array.optJSONObject(index) }.filterNotNull()
    }

    private fun fetchEsploraJson(path: String): Any? {
        val base = activeEsploraHttpBase()
        if (base.isBlank()) return null
        return runCatching {
            val request =
                Request.Builder()
                    .url("${base.trimEnd('/')}/${path.trimStart('/')}")
                    .header("Accept", "application/json")
                    .get()
                    .build()
            esploraHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val raw = response.body?.string()?.takeIf { it.isNotBlank() } ?: return@use null
                when (raw.firstOrNull()) {
                    '{' -> org.json.JSONObject(raw)
                    '[' -> org.json.JSONArray(raw)
                    else -> null
                }
            }
        }.getOrNull()
    }

    private fun esploraConfirmationCount(
        status: org.json.JSONObject?,
        tipHeight: Int?,
    ): Int {
        val confirmed = status?.optBoolean("confirmed", false) == true
        val blockHeight = status?.optInt("block_height", 0)?.takeIf { it > 0 }
        return ArkDepositPolicy.confirmationCount(
            confirmed = confirmed,
            blockHeight = blockHeight,
            tipHeight = tipHeight,
        )
    }

    private fun isArkExitBookkeepingMovement(movement: ArkMovement): Boolean {
        val blob =
            listOf(movement.subsystemName, movement.subsystemKind)
                .joinToString(" ")
                .lowercase(Locale.US)
        return blob.contains("exit") && !blob.contains("offboard")
    }

    private fun exitClaimToArkMovement(
        claim: ArkExitClaimHistory,
        confirmed: Boolean,
    ): ArkMovement =
        ArkMovement(
            // Stable negative id namespace avoids collisions with Bark movement ids.
            id = -(claim.txid.takeLast(7).toIntOrNull(16) ?: 1),
            status = if (confirmed) "completed" else "pending",
            subsystemName = "Bitcoin",
            subsystemKind = "exit-claim",
            intendedBalanceSats = -claim.amountSats,
            effectiveBalanceSats = -claim.amountSats,
            offchainFeeSats = 0L,
            sentToAddresses = listOf(claim.destinationAddress),
            createdAt = claim.createdAt,
            updatedAt = claim.createdAt,
            completedAt = claim.createdAt.takeIf { confirmed },
            onchainTxids = listOf(claim.txid),
            inputVtxoIds = claim.vtxoIds,
            onchainFeeSats = claim.feeSats,
        )

    /** True for real addresses/invoices; false for metadata JSON dumped into address fields. */
    private fun looksLikeUsableAddressField(value: String): Boolean {
        val v = value.trim()
        if (v.isEmpty()) return false
        if (v.startsWith("{") || v.startsWith("[")) return false
        // Drop pure 64-hex (payment hash / txid masquerading as address).
        if (v.length == 64 && v.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) return false
        val lower = v.lowercase(Locale.US)
        // Prefer recognizable payment destinations over free-form labels.
        if (lower.startsWith("ark1") ||
            lower.startsWith("bc1") ||
            lower.startsWith("tb1") ||
            lower.startsWith("lnbc") ||
            lower.startsWith("lntb") ||
            lower.startsWith("lightning:") ||
            lower.startsWith("lnurl") ||
            (lower.length in 26..90 && (lower.startsWith("1") || lower.startsWith("3")))
        ) {
            return true
        }
        // Allow other non-json strings that look like bech32/base58 peers (length gate).
        return v.length in 14..200 && !v.contains(' ') && !v.contains('\n')
    }

    /** Walk nested JSON for address-like strings (Bark metadata is often nested). */
    private fun harvestNestedAddresses(
        node: Any?,
        into: MutableList<String>,
        depth: Int = 0,
    ) {
        if (node == null || depth > 6) return
        when (node) {
            is org.json.JSONObject -> {
                val keys = node.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val lowerKey = key.lowercase(Locale.US)
                    val value = node.opt(key)
                    if (value is String &&
                        looksLikeUsableAddressField(value) &&
                        (
                            lowerKey.contains("address") ||
                                lowerKey.contains("dest") ||
                                lowerKey.contains("recipient") ||
                                lowerKey == "to" ||
                                lowerKey.contains("ark") ||
                                lowerKey.contains("peer")
                        )
                    ) {
                        into.add(value.trim())
                    }
                    harvestNestedAddresses(value, into, depth + 1)
                }
            }
            is org.json.JSONArray -> {
                for (i in 0 until node.length()) {
                    harvestNestedAddresses(node.opt(i), into, depth + 1)
                }
            }
        }
    }

    /**
     * Attach known L1 funding / board txids onto Bitcoin-rail movements that lack metadata.
     * Matches by board list first, then injects onto deposit-shaped movements still missing a txid.
     */
    private fun injectFundingTxid(
        movement: ArkMovement,
        knownTxids: List<String>,
    ): ArkMovement {
        if (knownTxids.isEmpty()) return movement
        if (movement.onchainTxids.isNotEmpty()) return movement
        val blob =
            listOf(movement.subsystemName, movement.subsystemKind)
                .joinToString(" ")
                .lowercase(Locale.US)
        val looksLikeBoardDeposit =
            movement.effectiveBalanceSats >= 0L &&
                (
                    blob.contains("board") ||
                        blob.contains("deposit") ||
                        blob.contains("onchain") ||
                        blob.contains("on-chain") ||
                        blob.contains("bitcoin")
                )
        if (!looksLikeBoardDeposit) return movement
        // Prefer a single newest funding tx when only one is outstanding.
        val txid = knownTxids.lastOrNull() ?: return movement
        return movement.copy(onchainTxids = listOf(txid))
    }

    private data class ParsedMovementMetadata(
        val txids: List<String> = emptyList(),
        val inputVtxoIds: List<String> = emptyList(),
        val outputVtxoIds: List<String> = emptyList(),
        val onchainFeeSats: Long? = null,
        val addresses: List<String> = emptyList(),
    )

    /**
     * Best-effort parse of Bark [Movement.metadataJson]. Schema varies by subsystem;
     * we harvest any 64-hex txids, common fee fields, and address-like strings.
     */
    private fun parseMovementMetadata(raw: String?): ParsedMovementMetadata {
        if (raw.isNullOrBlank()) return ParsedMovementMetadata()
        val txidPattern = Regex("""\b[0-9a-fA-F]{64}\b""")
        val txids =
            txidPattern
                .findAll(raw)
                .map { it.value.lowercase() }
                .distinct()
                .toList()
        var onchainFee: Long? = null
        val inputIds = mutableListOf<String>()
        val outputIds = mutableListOf<String>()
        val addresses = mutableListOf<String>()
        runCatching {
            val obj = org.json.JSONObject(raw)
            fun harvestFee(key: String) {
                if (onchainFee != null) return
                if (!obj.has(key) || obj.isNull(key)) return
                val v =
                    when (val any = obj.get(key)) {
                        is Number -> any.toLong()
                        is String -> any.toLongOrNull()
                        else -> null
                    }
                if (v != null && v >= 0L) onchainFee = v
            }
            listOf(
                "onchain_fee_sats",
                "onchainFeeSats",
                "chain_fee_sats",
                "chainFeeSats",
                "fee_sats",
                "feeSats",
                "network_fee_sats",
                "networkFeeSats",
            ).forEach(::harvestFee)
            fun harvestStringArray(key: String, into: MutableList<String>) {
                val arr = obj.optJSONArray(key) ?: return
                for (i in 0 until arr.length()) {
                    arr.optString(i)?.takeIf { it.isNotBlank() }?.let(into::add)
                }
            }
            harvestStringArray("input_vtxo_ids", inputIds)
            harvestStringArray("inputVtxoIds", inputIds)
            harvestStringArray("output_vtxo_ids", outputIds)
            harvestStringArray("outputVtxoIds", outputIds)
            fun harvestAddress(key: String) {
                val v = obj.optString(key)?.trim()?.takeIf { it.isNotBlank() } ?: return
                if (looksLikeUsableAddressField(v)) addresses.add(v)
            }
            listOf(
                "address",
                "ark_address",
                "arkAddress",
                "destination",
                "destination_address",
                "destinationAddress",
                "to",
                "to_address",
                "toAddress",
                "recipient",
                "recipient_address",
                "recipientAddress",
                "bitcoin_address",
                "bitcoinAddress",
            ).forEach(::harvestAddress)
            listOf(
                "addresses",
                "sent_to",
                "sentTo",
                "sent_to_addresses",
                "sentToAddresses",
                "received_on",
                "receivedOn",
                "received_on_addresses",
                "receivedOnAddresses",
            ).forEach { key -> harvestStringArray(key, addresses) }
            harvestNestedAddresses(obj, addresses)
        }
        // Also scan raw JSON for ark1… / bc1… tokens when keys are nested/unknown.
        Regex("""\b(ark1[0-9a-z]{20,}|bc1[qpzry9x8gf2tvdw0s3jn54khce6mua7l]{20,}|[13][a-km-zA-HJ-NP-Z1-9]{25,34})\b""")
            .findAll(raw)
            .map { it.value }
            .filter(::looksLikeUsableAddressField)
            .forEach { addresses.add(it) }
        return ParsedMovementMetadata(
            txids = txids,
            inputVtxoIds = inputIds.distinct(),
            outputVtxoIds = outputIds.distinct(),
            onchainFeeSats = onchainFee,
            addresses = addresses.distinct(),
        )
    }

    private suspend fun listLiveVtxos(w: Wallet): List<Vtxo> {
        val spendable = runCatching { w.spendableVtxos() }.getOrDefault(emptyList())
        val current = runCatching { w.vtxos() }.getOrDefault(emptyList())
        val merged = (spendable + current).distinctBy { it.id }
        if (merged.isNotEmpty()) return merged
        return runCatching { w.allVtxos() }
            .getOrDefault(emptyList())
            .filter { it.state is VtxoState.Spendable || it.state is VtxoState.Locked }
    }

    private fun Vtxo.toArkVtxo(): ArkVtxo =
        ArkVtxo(
            id = id,
            amountSats = amountSats.toLong(),
            expiryHeight = expiryHeight.toInt(),
            kind = kind,
            state = ArkBarkMappers.vtxoStateLabel(state),
            exitDepth = exitDepth.toInt(),
            exitTxWeightWu = exitTxWeightWu.toLong(),
            registered = registered,
        )

    private fun uniffi.bark.ExitVtxo.toArkExitVtxo(): ArkExitVtxo =
        ArkExitVtxo(
            vtxoId = vtxoId,
            amountSats = amountSats.toLong(),
            state = ArkBarkMappers.exitStateLabel(state),
            isClaimable = isClaimable,
        )

    private fun uniffi.bark.ExitProgressStatus.toArkExitProgress(): ArkExitProgress =
        ArkExitProgress(
            vtxoId = vtxoId,
            state = ArkBarkMappers.exitStateLabel(state),
            error = error?.takeIf { it.isNotBlank() },
        )

    private fun resolveExitProgressError(statuses: List<ArkExitProgress>): String? =
        ArkUnilateralExitPolicy.firstProgressErrorFromMessages(statuses.map { it.error })
            ?: if (ArkUnilateralExitPolicy.needsCpfpFunding(statuses.map { it.state })) {
                val statusIds = statuses.map { it.vtxoId }.toSet()
                val state = _arkState.value
                val estimatedFee =
                    ArkUnilateralExitPolicy.estimateCpfpFeeSats(
                        state.vtxos
                            .filter { it.id in statusIds }
                            .map { it.exitTxWeightWu },
                    )
                val requiredFunds = estimatedFee?.plus(ArkUnilateralExitPolicy.CPFP_CHANGE_DUST_SATS)
                if (requiredFunds != null) {
                    val confirmed = state.onchainConfirmedSats.coerceAtLeast(0L)
                    val shortfall = (requiredFunds - confirmed).coerceAtLeast(0L)
                    localizedString(
                        R.string.ark_error_exit_cpfp_funding_format,
                        "%,d".format(Locale.US, requiredFunds),
                        "%,d".format(Locale.US, confirmed),
                        "%,d".format(Locale.US, shortfall),
                    )
                } else {
                    localizedString(R.string.ark_error_exit_cpfp_funding)
                }
            } else {
                null
            }

    /**
     * Prefer a modest market-aware fee for automatic exit progression.
     * Floor at default (2), ceiling at [ArkUnilateralExitPolicy.MAX_EXIT_FEE_RATE_SAT_VB].
     * When the caller passes an explicit rate above default, use that (still clamped).
     * Bark [OnchainWallet.feeRates] (sat/kwu) is preferred when the on-chain wallet is open.
     */
    private suspend fun resolveExitProgressFeeRateSatPerVbLocked(
        requestedFeeRateSatPerVb: Long = ArkUnilateralExitPolicy.DEFAULT_EXIT_FEE_RATE_SAT_VB,
    ): Long {
        val fromBark =
            runCatching {
                onchainWallet
                    ?.feeRates()
                    ?.regularSatPerKwu
                    ?.toLong()
                    ?.takeIf { it > 0L }
                    ?.let { kwu ->
                        // 1 sat/vB = 250 sat/kWU
                        kotlin.math.ceil(kwu.toDouble() / 250.0).toLong().coerceAtLeast(1L)
                    }
            }.getOrNull()
        val preferred =
            fromBark ?: ArkUnilateralExitPolicy.PROGRESS_EXIT_FEE_RATE_SAT_VB
        val floor =
            maxOf(
                ArkUnilateralExitPolicy.DEFAULT_EXIT_FEE_RATE_SAT_VB,
                preferred,
                requestedFeeRateSatPerVb,
            )
        return ArkUnilateralExitPolicy.clampExitFeeRateSatPerVb(floor)
    }

    companion object {
        private const val TAG = "ArkRepository"
        /** Reserved local-only history id for BTC visible before Bark creates a board movement. */
        /** Bark working dirs under cacheDir — reused per wallet; never Android-backed-up storage. */
        private const val ARK_SESSION_ROOT = "ark-session"
        /** Minimum SQLite size to treat a cached Bark DB as reopenable (skip mailbox recovery). */
        private const val MIN_REUSABLE_BARK_DB_BYTES = 100L
        private const val TOR_BOOTSTRAP_TIMEOUT_MS = 90_000L
        private const val TOR_POST_BOOTSTRAP_DELAY_MS = 1_500L
        // Tight preflight: ASP + Esplora probes run in parallel; dead hosts never reach Wallet.open.
        private const val ESPLORA_PREFLIGHT_CONNECT_SECONDS = 2L
        private const val ESPLORA_PREFLIGHT_READ_SECONDS = 2L
        private const val ESPLORA_PREFLIGHT_CALL_SECONDS = 3L
        private const val ESPLORA_DETAIL_TIMEOUT_SECONDS = 8L
        /** Reuse /utxo responses within one pull-to-refresh burst. */
        private const val ESPLORA_UTXO_CACHE_MS = 15_000L
        /** Cap slow /txs fallback (and its per-output outspend fan-out). */
        private const val ESPLORA_TX_HISTORY_FALLBACK_LIMIT = 8
        /** Fallback when ArkInfo is unknown — Second ASP uses 6. */
        private const val DEFAULT_BOARD_CONFIRMATIONS = 6
        /** Only used when Bark's local BDK cache has no confirmed funds. */
        private const val RECOVER_ONCHAIN_SYNC_TIMEOUT_MS = 20_000L
        /** Fail recover quickly when another Ark job is holding [mutex]. */
        private const val RECOVER_MUTEX_WAIT_MS = 3_000L
        /** Balance-card spinner hard cap — native ASP hydrate must not hold it indefinitely. */
        private const val ARK_SYNC_SPINNER_MAX_MS = 12_000L
        /** Poll open BOLT11 receive for paid status while Receive is open. */
        private const val LIGHTNING_RECEIVE_WATCH_INTERVAL_MS = 2_000L
        private const val LIGHTNING_RECEIVE_WATCH_ATTEMPTS = 150
        /** Quiet auto-refresh fee cap per attempt — above this, only banner / manual refresh. */
        private const val AUTO_REFRESH_MAX_FEE_SATS = 5_000L
        /** Cumulative auto-refresh fee budget per UTC day. */
        private const val AUTO_REFRESH_DAILY_CAP_SATS = 25_000L
        /** Minimum gap between successful auto-refresh attempts while session is live. */
        private const val AUTO_REFRESH_COOLDOWN_MS = 15 * 60_000L
        /** Faster retry after a failed auto-refresh (still rate-limited). */
        private const val AUTO_REFRESH_FAILURE_RETRY_MS = 2 * 60_000L
        private const val REFRESH_SIGNAL_COOLDOWN_MS = 10 * 60_000L
        private const val TIP_CACHE_MS = 5 * 60_000L
        /** Pre external-only path; deleted on full wipe if still present. */
        private const val LEGACY_AUTO_BACKUP_DIR = "ark_auto_backup"
        private const val AUTO_DB_BACKUP_DEBOUNCE_MS = 8_000L
        /** Most recent auto snapshot. */
        private const val AUTO_BACKUP_LATEST_NAME = "ibis-ark-db-latest.zip"
        /** Second copy of the same snapshot as latest (redundancy, not an older generation). */
        private const val AUTO_BACKUP_BACKUP_NAME = "ibis-ark-db-backup.zip"
        /** Staging file; never treated as restore source. */
        private const val AUTO_BACKUP_LATEST_TMP_NAME = "ibis-ark-db-latest.tmp.zip"
        /** Bound wait=true Lightning pays so a hung ASP cannot hold the wallet mutex forever. */
        private const val LIGHTNING_PAY_TIMEOUT_MS = 120_000L
        /** Max wait for mutex unload during auto-wipe before force-deleting files. */
        private const val FULL_WIPE_UNLOAD_TIMEOUT_MS = 5_000L
        /**
         * Max wait for cancelled Ark jobs (notifications/board/refresh/backup) to unwind
         * native FFI calls before handles are closed. Bounded so a stuck native call
         * cannot hang unload; on timeout we close anyway rather than leak the wallet.
         */
        private const val ARK_JOB_JOIN_TIMEOUT_MS = 10_000L
        /** Post-open ASP sync may be mid-native call; wait longer before dispose on import/unload. */
        private const val ARK_POST_OPEN_JOIN_TIMEOUT_MS = 30_000L
        /**
         * Background fee quote after RefreshPreview is already painted.
         * Keep short — UI must not wait; null fee is fine (Unavailable).
         */
        private const val ARK_REFRESH_QUOTE_TIMEOUT_MS = 4_000L
        private const val ARK_REFRESH_STATUS_POLL_MS = 10_000L
        private const val ARK_REFRESH_INITIAL_STATUS_CHECKS = 3
        /** Bound send fee-quote FFI so review cannot spin forever on a hung ASP. */
        private const val ARK_SEND_PREVIEW_TIMEOUT_MS = 45_000L
        /** Min gap between forced re-opens when a seed wallet is running off-chain-only. */
        private const val ONCHAIN_REOPEN_DEBOUNCE_MS = 2 * 60_000L
        /** Min gap between on-chain-unavailable snackbars. */
        private const val ONCHAIN_UNAVAILABLE_COOLDOWN_MS = 10 * 60_000L
        /** Max newAddress() replays to re-reveal prior-session deposit addresses. */
        private const val ONCHAIN_REVEAL_CATCHUP_MAX = 64
        /** Blind board attempts (Esplora sees funds, Bark doesn't) before alerting. */
        private const val ONCHAIN_BLIND_BOARD_ATTEMPTS_ALERT = 3
    }
}
