package github.aeonbtc.ibiswallet.data.local

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import androidx.core.database.sqlite.transaction
import github.aeonbtc.ibiswallet.BuildConfig
import github.aeonbtc.ibiswallet.data.model.ConfirmationTime
import github.aeonbtc.ibiswallet.data.model.TransactionDetails
import github.aeonbtc.ibiswallet.data.model.TransactionSearchDocument
import github.aeonbtc.ibiswallet.data.model.TransactionSearchFilters
import github.aeonbtc.ibiswallet.data.model.TransactionSearchLayer
import github.aeonbtc.ibiswallet.data.model.TransactionSearchResult
import github.aeonbtc.ibiswallet.util.buildTransactionSearchMatchQuery
import github.aeonbtc.ibiswallet.util.buildTransactionSearchableText
import java.io.File

/**
 * Persistent SQLite cache for Electrum server responses.
 *
 * Stores immutable data that does not need to be re-fetched from the server
 * on every app launch:
 *
 * - **Raw transaction hex** (`tx_raw`): keyed by txid. Once confirmed, tx hex
 *   never changes. Eliminates BDK's cold tx_cache penalty on startup.
 *
 * - **Verbose transaction JSON** (`tx_verbose`): keyed by txid. Contains
 *   size/vsize/weight fields used by fetchTransactionVsizeFromElectrum.
 *   Only permanently cached for confirmed txs; unconfirmed entries are pruned
 *   after 1 hour.
 *
 * The cache is shared across all wallets — transaction data is wallet-agnostic.
 * Database file: `<databases>/electrum_cache.db`
 */
class ElectrumCache(context: Context) : SQLiteOpenHelper(
    context,
    DATABASE_NAME,
    null,
    DATABASE_VERSION,
) {
    private val appContext = context.applicationContext

    companion object {
        private const val TAG = "ElectrumCache"
        private const val DATABASE_NAME = "electrum_cache.db"
        private const val DATABASE_VERSION = 5
        private const val SQLITE_IN_CHUNK_SIZE = 900
        private const val SQLITE_DELETE_CHUNK_SIZE = 500

        // Table: raw transaction hex (blockchain.transaction.get non-verbose)
        private const val TABLE_TX_RAW = "tx_raw"
        private const val COL_TXID = "txid"
        private const val COL_HEX = "hex"
        private const val COL_CACHED_AT = "cached_at"

        // Table: verbose transaction JSON (blockchain.transaction.get verbose=true)
        private const val TABLE_TX_VERBOSE = "tx_verbose"
        private const val COL_JSON = "json"
        private const val COL_CONFIRMED = "confirmed"

        // Table: persisted script hash statuses for smartSync on app relaunch.
        // Compared against subscription responses to detect changes while the app was closed.
        private const val TABLE_SCRIPT_HASH_STATUSES = "script_hash_statuses"
        private const val COL_SCRIPT_HASH = "script_hash"
        private const val COL_STATUS = "status"
        // COL_CACHED_AT reused from above

        // Table: cached scripthash history responses keyed by the status hash that
        // validated them. If the current status differs, the history is stale.
        private const val TABLE_SCRIPT_HASH_HISTORY = "script_hash_history"
        private const val COL_HISTORY_JSON = "history_json"

        // Table: wallet-specific confirmed transaction display rows.
        // Lets us reuse expensive per-tx enrichment work across launches.
        private const val TABLE_WALLET_TX_DETAILS = "wallet_tx_details"
        private const val COL_WALLET_ID = "wallet_id"
        private const val COL_DESCRIPTOR_KEY = "descriptor_key"
        // COL_TXID reused from above
        private const val COL_AMOUNT_SATS = "amount_sats"
        // COL_FEE reused from below
        private const val COL_FEE = "fee"
        private const val COL_WEIGHT = "weight"
        private const val COL_CONFIRMATION_HEIGHT = "confirmation_height"
        private const val COL_CONFIRMATION_TIMESTAMP = "confirmation_timestamp"
        private const val COL_ADDRESS = "address"
        private const val COL_ADDRESS_AMOUNT = "address_amount"
        private const val COL_CHANGE_ADDRESS = "change_address"
        private const val COL_CHANGE_AMOUNT = "change_amount"
        private const val COL_IS_SELF_TRANSFER = "is_self_transfer"

        // Tables: wallet-scoped transaction search metadata + FTS text index.
        private const val TABLE_TX_SEARCH_DOCS = "tx_search_docs"
        private const val TABLE_TX_SEARCH_FTS = "tx_search_fts"
        private const val COL_LAYER = "layer"
        private const val COL_SORT_TIMESTAMP = "sort_timestamp"
        private const val COL_SORT_HEIGHT = "sort_height"
        private const val COL_IS_SWAP = "is_swap"
        private const val COL_IS_LIGHTNING = "is_lightning"
        private const val COL_IS_NATIVE = "is_native"
        private const val COL_HAS_USDT = "has_usdt"
        private const val COL_LABEL_TEXT = "label_text"
        private const val COL_RECIPIENT_ADDRESS = "recipient_address"
        private const val COL_ADDRESS_LABEL_TEXT = "address_label_text"
        private const val COL_DATE_TOKENS = "date_tokens"
        private const val COL_SEARCH_TEXT = "search_text"

        // Prune unconfirmed verbose tx entries older than this
        private const val UNCONFIRMED_TTL_MS = 3_600_000L // 1 hour
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE_TX_RAW (
                $COL_TXID TEXT PRIMARY KEY,
                $COL_HEX TEXT NOT NULL,
                $COL_CACHED_AT INTEGER NOT NULL
            )
            """.trimIndent(),
        )

        db.execSQL(
            """
            CREATE TABLE $TABLE_TX_VERBOSE (
                $COL_TXID TEXT PRIMARY KEY,
                $COL_JSON TEXT NOT NULL,
                $COL_CONFIRMED INTEGER NOT NULL DEFAULT 0,
                $COL_CACHED_AT INTEGER NOT NULL
            )
            """.trimIndent(),
        )

        db.execSQL(
            """
            CREATE TABLE $TABLE_SCRIPT_HASH_STATUSES (
                $COL_SCRIPT_HASH TEXT PRIMARY KEY,
                $COL_STATUS TEXT,
                $COL_CACHED_AT INTEGER NOT NULL
            )
            """.trimIndent(),
        )

        db.execSQL(
            """
            CREATE TABLE $TABLE_SCRIPT_HASH_HISTORY (
                $COL_SCRIPT_HASH TEXT PRIMARY KEY,
                $COL_STATUS TEXT NOT NULL,
                $COL_HISTORY_JSON TEXT NOT NULL,
                $COL_CACHED_AT INTEGER NOT NULL
            )
            """.trimIndent(),
        )

        db.execSQL(
            """
            CREATE TABLE $TABLE_WALLET_TX_DETAILS (
                $COL_WALLET_ID TEXT NOT NULL,
                $COL_DESCRIPTOR_KEY TEXT NOT NULL,
                $COL_TXID TEXT NOT NULL,
                $COL_AMOUNT_SATS INTEGER NOT NULL,
                $COL_FEE INTEGER,
                $COL_WEIGHT INTEGER,
                $COL_CONFIRMATION_HEIGHT INTEGER NOT NULL,
                $COL_CONFIRMATION_TIMESTAMP INTEGER NOT NULL,
                $COL_ADDRESS TEXT,
                $COL_ADDRESS_AMOUNT INTEGER,
                $COL_CHANGE_ADDRESS TEXT,
                $COL_CHANGE_AMOUNT INTEGER,
                $COL_IS_SELF_TRANSFER INTEGER NOT NULL DEFAULT 0,
                $COL_CACHED_AT INTEGER NOT NULL,
                PRIMARY KEY ($COL_WALLET_ID, $COL_DESCRIPTOR_KEY, $COL_TXID)
            )
            """.trimIndent(),
        )

        createTransactionSearchSchema(db)
    }

    override fun onUpgrade(
        db: SQLiteDatabase,
        oldVersion: Int,
        newVersion: Int,
    ) {
        if (oldVersion < 2) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS $TABLE_SCRIPT_HASH_STATUSES (
                    $COL_SCRIPT_HASH TEXT PRIMARY KEY,
                    $COL_STATUS TEXT,
                    $COL_CACHED_AT INTEGER NOT NULL
                )
                """.trimIndent(),
            )
        }
        if (oldVersion < 3) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS $TABLE_WALLET_TX_DETAILS (
                    $COL_WALLET_ID TEXT NOT NULL,
                    $COL_DESCRIPTOR_KEY TEXT NOT NULL,
                    $COL_TXID TEXT NOT NULL,
                    $COL_AMOUNT_SATS INTEGER NOT NULL,
                    $COL_FEE INTEGER,
                    $COL_WEIGHT INTEGER,
                    $COL_CONFIRMATION_HEIGHT INTEGER NOT NULL,
                    $COL_CONFIRMATION_TIMESTAMP INTEGER NOT NULL,
                    $COL_ADDRESS TEXT,
                    $COL_ADDRESS_AMOUNT INTEGER,
                    $COL_CHANGE_ADDRESS TEXT,
                    $COL_CHANGE_AMOUNT INTEGER,
                    $COL_IS_SELF_TRANSFER INTEGER NOT NULL DEFAULT 0,
                    $COL_CACHED_AT INTEGER NOT NULL,
                    PRIMARY KEY ($COL_WALLET_ID, $COL_DESCRIPTOR_KEY, $COL_TXID)
                )
                """.trimIndent(),
            )
        }
        if (oldVersion < 4) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS $TABLE_SCRIPT_HASH_HISTORY (
                    $COL_SCRIPT_HASH TEXT PRIMARY KEY,
                    $COL_STATUS TEXT NOT NULL,
                    $COL_HISTORY_JSON TEXT NOT NULL,
                    $COL_CACHED_AT INTEGER NOT NULL
                )
                """.trimIndent(),
            )
        }
        if (oldVersion < 5) {
            createTransactionSearchSchema(db)
        }
    }

    private fun createTransactionSearchSchema(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_TX_SEARCH_DOCS (
                $COL_WALLET_ID TEXT NOT NULL,
                $COL_LAYER TEXT NOT NULL,
                $COL_TXID TEXT NOT NULL,
                $COL_SORT_TIMESTAMP INTEGER,
                $COL_SORT_HEIGHT INTEGER,
                $COL_IS_SWAP INTEGER NOT NULL DEFAULT 0,
                $COL_IS_LIGHTNING INTEGER NOT NULL DEFAULT 0,
                $COL_IS_NATIVE INTEGER NOT NULL DEFAULT 0,
                $COL_HAS_USDT INTEGER NOT NULL DEFAULT 0,
                $COL_LABEL_TEXT TEXT NOT NULL DEFAULT '',
                $COL_ADDRESS TEXT NOT NULL DEFAULT '',
                $COL_CHANGE_ADDRESS TEXT NOT NULL DEFAULT '',
                $COL_RECIPIENT_ADDRESS TEXT NOT NULL DEFAULT '',
                $COL_ADDRESS_LABEL_TEXT TEXT NOT NULL DEFAULT '',
                $COL_DATE_TOKENS TEXT NOT NULL DEFAULT '',
                $COL_CACHED_AT INTEGER NOT NULL,
                PRIMARY KEY ($COL_WALLET_ID, $COL_LAYER, $COL_TXID)
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS idx_tx_search_docs_wallet_layer_sort
            ON $TABLE_TX_SEARCH_DOCS ($COL_WALLET_ID, $COL_LAYER, $COL_SORT_TIMESTAMP DESC, $COL_SORT_HEIGHT DESC)
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS idx_tx_search_docs_wallet_layer_address
            ON $TABLE_TX_SEARCH_DOCS ($COL_WALLET_ID, $COL_LAYER, $COL_ADDRESS)
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE VIRTUAL TABLE IF NOT EXISTS $TABLE_TX_SEARCH_FTS
            USING fts4(
                $COL_WALLET_ID,
                $COL_LAYER,
                $COL_TXID,
                $COL_SEARCH_TEXT
            )
            """.trimIndent(),
        )
    }

    // ==================== Raw Transaction Hex ====================

    /**
     * Get cached raw transaction hex by txid.
     * @return hex string or null if not cached
     */
    fun getRawTx(txid: String): String? {
        return try {
            readableDatabase.query(
                TABLE_TX_RAW,
                arrayOf(COL_HEX),
                "$COL_TXID = ?",
                arrayOf(txid),
                null,
                null,
                null,
            ).use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.w(TAG, "Failed to read tx cache for $txid: ${e.message}")
            null
        }
    }

    /**
     * Return the txids from [txids] that are not yet cached.
     * Uses chunked IN queries to avoid one SQLite lookup per txid.
     */
    fun findMissingRawTxids(txids: List<String>): List<String> {
        if (txids.isEmpty()) return emptyList()

        return try {
            val cachedTxids = HashSet<String>(txids.size)
            txids.chunked(SQLITE_IN_CHUNK_SIZE).forEach { chunk ->
                val placeholders = List(chunk.size) { "?" }.joinToString(",")
                readableDatabase.query(
                    TABLE_TX_RAW,
                    arrayOf(COL_TXID),
                    "$COL_TXID IN ($placeholders)",
                    chunk.toTypedArray(),
                    null,
                    null,
                    null,
                ).use { cursor ->
                    while (cursor.moveToNext()) {
                        cachedTxids += cursor.getString(0)
                    }
                }
            }

            txids.filterNot(cachedTxids::contains)
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.w(TAG, "Failed to batch-read tx cache: ${e.message}")
            txids
        }
    }

    /**
     * Cache raw transaction hex. Uses INSERT OR REPLACE for idempotency.
     */
    fun putRawTx(
        txid: String,
        hex: String,
    ) {
        try {
            val values =
                ContentValues().apply {
                    put(COL_TXID, txid)
                    put(COL_HEX, hex)
                    put(COL_CACHED_AT, System.currentTimeMillis())
                }
            writableDatabase.insertWithOnConflict(
                TABLE_TX_RAW,
                null,
                values,
                SQLiteDatabase.CONFLICT_REPLACE,
            )
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.w(TAG, "Failed to cache tx $txid: ${e.message}")
        }
    }

    // ==================== Verbose Transaction JSON ====================

    /**
     * Get cached verbose transaction JSON by txid.
     * @return JSON string or null if not cached
     */
    fun getVerboseTx(txid: String): String? {
        return try {
            readableDatabase.query(
                TABLE_TX_VERBOSE,
                arrayOf(COL_JSON),
                "$COL_TXID = ?",
                arrayOf(txid),
                null,
                null,
                null,
            ).use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.w(TAG, "Failed to read verbose tx cache for $txid: ${e.message}")
            null
        }
    }

    /**
     * Cache verbose transaction JSON.
     * @param confirmed whether the tx is confirmed (permanent cache) or unconfirmed (TTL-based)
     */
    fun putVerboseTx(
        txid: String,
        json: String,
        confirmed: Boolean,
    ) {
        try {
            val values =
                ContentValues().apply {
                    put(COL_TXID, txid)
                    put(COL_JSON, json)
                    put(COL_CONFIRMED, if (confirmed) 1 else 0)
                    put(COL_CACHED_AT, System.currentTimeMillis())
                }
            writableDatabase.insertWithOnConflict(
                TABLE_TX_VERBOSE,
                null,
                values,
                SQLiteDatabase.CONFLICT_REPLACE,
            )
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.w(TAG, "Failed to cache verbose tx $txid: ${e.message}")
        }
    }

    // ==================== Block Headers ====================

    // ==================== Script Hash Statuses ====================

    /**
     * Persist script hash statuses to survive app restarts.
     * Updates only changed entries and removes hashes that disappeared.
     */
    fun saveScriptHashStatuses(statuses: Map<String, String?>) {
        if (statuses.isEmpty()) return
        try {
            writableDatabase.transaction {
                val existing = loadScriptHashStatuses(this)
                val removedHashes = existing.keys - statuses.keys
                val now = System.currentTimeMillis()
                removedHashes.chunked(SQLITE_DELETE_CHUNK_SIZE).forEach { chunk ->
                    val placeholders = List(chunk.size) { "?" }.joinToString(",")
                    delete(
                        TABLE_SCRIPT_HASH_STATUSES,
                        "$COL_SCRIPT_HASH IN ($placeholders)",
                        chunk.toTypedArray(),
                    )
                }
                for ((scriptHash, status) in statuses) {
                    if (existing[scriptHash] == status) continue
                    val values =
                        ContentValues().apply {
                            put(COL_SCRIPT_HASH, scriptHash)
                            put(COL_STATUS, status)
                            put(COL_CACHED_AT, now)
                        }
                    insertWithOnConflict(
                        TABLE_SCRIPT_HASH_STATUSES,
                        null,
                        values,
                        SQLiteDatabase.CONFLICT_REPLACE,
                    )
                }
            }
            if (BuildConfig.DEBUG) Log.d(TAG, "Persisted ${statuses.size} script hash statuses")
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.w(TAG, "Failed to persist script hash statuses: ${e.message}")
        }
    }

    /**
     * Load persisted script hash statuses from the previous session.
     * @return Map of scriptHash -> status (null for unused addresses), or empty map.
     */
    fun loadScriptHashStatuses(): Map<String, String?> {
        return try {
            loadScriptHashStatuses(readableDatabase)
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.w(TAG, "Failed to load script hash statuses: ${e.message}")
            emptyMap()
        }
    }

    /**
     * Clear persisted script hash statuses.
     * Called on wallet switch or server change.
     */
    fun clearScriptHashStatuses() {
        try {
            writableDatabase.delete(TABLE_SCRIPT_HASH_STATUSES, null, null)
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.w(TAG, "Failed to clear script hash statuses: ${e.message}")
        }
    }

    private fun loadScriptHashStatuses(db: SQLiteDatabase): Map<String, String?> {
        val result = mutableMapOf<String, String?>()
        db.query(
            TABLE_SCRIPT_HASH_STATUSES,
            arrayOf(COL_SCRIPT_HASH, COL_STATUS),
            null,
            null,
            null,
            null,
            null,
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val scriptHash = cursor.getString(0)
                val status = if (cursor.isNull(1)) null else cursor.getString(1)
                result[scriptHash] = status
            }
        }
        return result
    }

    // ==================== Script Hash History ====================

    /**
     * Get cached script hash history when [currentStatus] still matches the
     * status used to validate the cached history response.
     */
    fun getHistory(
        scriptHash: String,
        currentStatus: String,
    ): String? {
        return try {
            readableDatabase.query(
                TABLE_SCRIPT_HASH_HISTORY,
                arrayOf(COL_STATUS, COL_HISTORY_JSON),
                "$COL_SCRIPT_HASH = ?",
                arrayOf(scriptHash),
                null,
                null,
                null,
            ).use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                val cachedStatus = cursor.getString(0)
                if (cachedStatus != currentStatus) return@use null
                cursor.getString(1)
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.w(TAG, "Failed to read history cache for $scriptHash: ${e.message}")
            null
        }
    }

    fun putHistory(
        scriptHash: String,
        status: String,
        historyJson: String,
    ) {
        try {
            val values =
                ContentValues().apply {
                    put(COL_SCRIPT_HASH, scriptHash)
                    put(COL_STATUS, status)
                    put(COL_HISTORY_JSON, historyJson)
                    put(COL_CACHED_AT, System.currentTimeMillis())
                }
            writableDatabase.insertWithOnConflict(
                TABLE_SCRIPT_HASH_HISTORY,
                null,
                values,
                SQLiteDatabase.CONFLICT_REPLACE,
            )
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.w(TAG, "Failed to cache history for $scriptHash: ${e.message}")
        }
    }

    fun clearAllHistory() {
        try {
            writableDatabase.delete(TABLE_SCRIPT_HASH_HISTORY, null, null)
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.w(TAG, "Failed to clear script hash history: ${e.message}")
        }
    }

    // ==================== Confirmed Transaction Details ====================

    fun loadConfirmedTransactionDetails(
        walletId: String,
        descriptorKey: String,
        txids: List<String>,
    ): Map<String, TransactionDetails> {
        if (txids.isEmpty()) return emptyMap()

        return try {
            val result = linkedMapOf<String, TransactionDetails>()
            txids.chunked(SQLITE_IN_CHUNK_SIZE).forEach { chunk ->
                val placeholders = List(chunk.size) { "?" }.joinToString(",")
                val args = arrayOf(walletId, descriptorKey, *chunk.toTypedArray())
                readableDatabase.query(
                    TABLE_WALLET_TX_DETAILS,
                    arrayOf(
                        COL_TXID,
                        COL_AMOUNT_SATS,
                        COL_FEE,
                        COL_WEIGHT,
                        COL_CONFIRMATION_HEIGHT,
                        COL_CONFIRMATION_TIMESTAMP,
                        COL_ADDRESS,
                        COL_ADDRESS_AMOUNT,
                        COL_CHANGE_ADDRESS,
                        COL_CHANGE_AMOUNT,
                        COL_IS_SELF_TRANSFER,
                    ),
                    "$COL_WALLET_ID = ? AND $COL_DESCRIPTOR_KEY = ? AND $COL_TXID IN ($placeholders)",
                    args,
                    null,
                    null,
                    null,
                ).use { cursor ->
                    while (cursor.moveToNext()) {
                        val txid = cursor.getString(0)
                        val amountSats = cursor.getLong(1)
                        val fee = if (cursor.isNull(2)) null else cursor.getLong(2).toULong()
                        val weight = if (cursor.isNull(3)) null else cursor.getLong(3).toULong()
                        val confirmationHeight = cursor.getLong(4).toUInt()
                        val confirmationTimestamp = cursor.getLong(5).toULong()
                        val address = if (cursor.isNull(6)) null else cursor.getString(6)
                        val addressAmount = if (cursor.isNull(7)) null else cursor.getLong(7).toULong()
                        val changeAddress = if (cursor.isNull(8)) null else cursor.getString(8)
                        val changeAmount = if (cursor.isNull(9)) null else cursor.getLong(9).toULong()
                        val isSelfTransfer = cursor.getInt(10) != 0
                        result[txid] =
                            TransactionDetails(
                                txid = txid,
                                amountSats = amountSats,
                                fee = fee,
                                weight = weight,
                                confirmationTime =
                                    ConfirmationTime(
                                        height = confirmationHeight,
                                        timestamp = confirmationTimestamp,
                                    ),
                                isConfirmed = true,
                                timestamp = confirmationTimestamp.toLong(),
                                address = address,
                                addressAmount = addressAmount,
                                changeAddress = changeAddress,
                                changeAmount = changeAmount,
                                isSelfTransfer = isSelfTransfer,
                            )
                    }
                }
            }
            result
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) {
                Log.w(TAG, "Failed to load cached transaction details for $walletId: ${e.message}")
            }
            emptyMap()
        }
    }

    fun putConfirmedTransactionDetails(
        walletId: String,
        descriptorKey: String,
        transactions: Collection<TransactionDetails>,
    ) {
        if (transactions.isEmpty()) return

        try {
            val now = System.currentTimeMillis()
            writableDatabase.transaction {
                transactions.forEach { tx ->
                    if (!tx.isConfirmed) return@forEach
                    val confirmationTime = tx.confirmationTime ?: return@forEach
                    val values =
                        ContentValues().apply {
                            put(COL_WALLET_ID, walletId)
                            put(COL_DESCRIPTOR_KEY, descriptorKey)
                            put(COL_TXID, tx.txid)
                            put(COL_AMOUNT_SATS, tx.amountSats)
                            put(COL_FEE, tx.fee?.toLong())
                            put(COL_WEIGHT, tx.weight?.toLong())
                            put(COL_CONFIRMATION_HEIGHT, confirmationTime.height.toLong())
                            put(COL_CONFIRMATION_TIMESTAMP, confirmationTime.timestamp.toLong())
                            put(COL_ADDRESS, tx.address)
                            put(COL_ADDRESS_AMOUNT, tx.addressAmount?.toLong())
                            put(COL_CHANGE_ADDRESS, tx.changeAddress)
                            put(COL_CHANGE_AMOUNT, tx.changeAmount?.toLong())
                            put(COL_IS_SELF_TRANSFER, if (tx.isSelfTransfer) 1 else 0)
                            put(COL_CACHED_AT, now)
                        }
                    insertWithOnConflict(
                        TABLE_WALLET_TX_DETAILS,
                        null,
                        values,
                        SQLiteDatabase.CONFLICT_REPLACE,
                    )
                }
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) {
                Log.w(TAG, "Failed to cache confirmed transaction details for $walletId: ${e.message}")
            }
        }
    }

    fun clearConfirmedTransactionDetails(walletId: String) {
        try {
            writableDatabase.delete(
                TABLE_WALLET_TX_DETAILS,
                "$COL_WALLET_ID = ?",
                arrayOf(walletId),
            )
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) {
                Log.w(TAG, "Failed to clear cached transaction details for $walletId: ${e.message}")
            }
        }
    }

    fun replaceTransactionSearchDocuments(
        walletId: String,
        layer: TransactionSearchLayer,
        documents: Collection<TransactionSearchDocument>,
    ) {
        if (walletId.isBlank()) return

        try {
            writableDatabase.transaction {
                deleteTransactionSearchDocumentsInternal(
                    walletId = walletId,
                    layer = layer,
                    txids = null,
                )
                upsertTransactionSearchDocumentsInternal(documents)
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) {
                Log.w(TAG, "Failed to replace transaction search docs for $walletId/${layer.dbValue}: ${e.message}")
            }
        }
    }

    fun upsertTransactionSearchDocuments(documents: Collection<TransactionSearchDocument>) {
        if (documents.isEmpty()) return

        try {
            writableDatabase.transaction {
                upsertTransactionSearchDocumentsInternal(documents)
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) {
                Log.w(TAG, "Failed to upsert transaction search docs: ${e.message}")
            }
        }
    }

    fun deleteTransactionSearchDocuments(
        walletId: String,
        layer: TransactionSearchLayer,
        txids: Collection<String>,
    ) {
        if (walletId.isBlank() || txids.isEmpty()) return

        try {
            writableDatabase.transaction {
                deleteTransactionSearchDocumentsInternal(
                    walletId = walletId,
                    layer = layer,
                    txids = txids,
                )
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) {
                Log.w(TAG, "Failed to delete transaction search docs for $walletId/${layer.dbValue}: ${e.message}")
            }
        }
    }

    fun clearTransactionSearchDocuments(walletId: String) {
        if (walletId.isBlank()) return

        try {
            writableDatabase.transaction {
                deleteTransactionSearchDocumentsInternal(
                    walletId = walletId,
                    layer = null,
                    txids = null,
                )
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) {
                Log.w(TAG, "Failed to clear transaction search docs for $walletId: ${e.message}")
            }
        }
    }

    fun updateTransactionSearchLabel(
        walletId: String,
        layer: TransactionSearchLayer,
        txid: String,
        label: String,
    ) {
        if (walletId.isBlank() || txid.isBlank()) return

        val document =
            readTransactionSearchDocuments(
                selection = "$COL_WALLET_ID = ? AND $COL_LAYER = ? AND $COL_TXID = ?",
                selectionArgs = arrayOf(walletId, layer.dbValue, txid),
            ).firstOrNull()
                ?: return

        upsertTransactionSearchDocuments(
            listOf(document.copy(label = label.trim())),
        )
    }

    fun updateTransactionSearchAddressLabel(
        walletId: String,
        layer: TransactionSearchLayer,
        address: String,
        label: String,
    ) {
        if (walletId.isBlank() || address.isBlank()) return

        val documents =
            readTransactionSearchDocuments(
                selection = "$COL_WALLET_ID = ? AND $COL_LAYER = ? AND $COL_ADDRESS = ?",
                selectionArgs = arrayOf(walletId, layer.dbValue, address),
            )
        if (documents.isEmpty()) return

        upsertTransactionSearchDocuments(
            documents.map { it.copy(addressLabel = label.trim()) },
        )
    }

    fun searchTransactionTxids(
        walletId: String,
        layer: TransactionSearchLayer,
        query: String,
        limit: Int,
        filters: TransactionSearchFilters = TransactionSearchFilters(),
    ): TransactionSearchResult {
        if (walletId.isBlank()) return TransactionSearchResult(emptyList(), 0)

        val queryParts =
            buildTransactionSearchQueryParts(
                walletId = walletId,
                layer = layer,
                query = query,
                filters = filters,
            ) ?: return TransactionSearchResult(emptyList(), 0)

        val cappedLimit = limit.coerceAtLeast(1)
        val orderByClause =
            "ORDER BY COALESCE($TABLE_TX_SEARCH_DOCS.$COL_SORT_TIMESTAMP, -1) DESC, " +
                "COALESCE($TABLE_TX_SEARCH_DOCS.$COL_SORT_HEIGHT, -1) DESC"

        return try {
            val totalCount =
                readableDatabase.rawQuery(
                    "SELECT COUNT(*) FROM ${queryParts.fromClause} WHERE ${queryParts.whereClause}",
                    queryParts.whereArgs,
                ).use { cursor ->
                    if (cursor.moveToFirst()) cursor.getInt(0) else 0
                }

            val txids = mutableListOf<String>()
            readableDatabase.rawQuery(
                "SELECT $TABLE_TX_SEARCH_DOCS.$COL_TXID " +
                    "FROM ${queryParts.fromClause} " +
                    "WHERE ${queryParts.whereClause} " +
                    "$orderByClause LIMIT ?",
                queryParts.whereArgs + cappedLimit.toString(),
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    txids += cursor.getString(0)
                }
            }
            TransactionSearchResult(txids = txids, totalCount = totalCount)
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) {
                Log.w(TAG, "Failed to search transaction docs for $walletId/${layer.dbValue}: ${e.message}")
            }
            TransactionSearchResult(emptyList(), 0)
        }
    }

    private data class TransactionSearchQueryParts(
        val fromClause: String,
        val whereClause: String,
        val whereArgs: Array<String>,
    )

    private fun buildTransactionSearchQueryParts(
        walletId: String,
        layer: TransactionSearchLayer,
        query: String,
        filters: TransactionSearchFilters,
    ): TransactionSearchQueryParts? {
        val whereClauses = mutableListOf<String>()
        val whereArgs = mutableListOf<String>()
        val matchQuery = buildTransactionSearchMatchQuery(query)

        val fromClause =
            if (matchQuery != null) {
                whereClauses += "$TABLE_TX_SEARCH_FTS MATCH ?"
                whereArgs += matchQuery
                "$TABLE_TX_SEARCH_DOCS INNER JOIN $TABLE_TX_SEARCH_FTS ON " +
                    "$TABLE_TX_SEARCH_DOCS.$COL_WALLET_ID = $TABLE_TX_SEARCH_FTS.$COL_WALLET_ID AND " +
                    "$TABLE_TX_SEARCH_DOCS.$COL_LAYER = $TABLE_TX_SEARCH_FTS.$COL_LAYER AND " +
                    "$TABLE_TX_SEARCH_DOCS.$COL_TXID = $TABLE_TX_SEARCH_FTS.$COL_TXID"
            } else {
                TABLE_TX_SEARCH_DOCS
            }

        whereClauses += "$TABLE_TX_SEARCH_DOCS.$COL_WALLET_ID = ?"
        whereClauses += "$TABLE_TX_SEARCH_DOCS.$COL_LAYER = ?"
        whereArgs += walletId
        whereArgs += layer.dbValue

        when (layer) {
            TransactionSearchLayer.BITCOIN -> {
                if (filters.swapOnly) {
                    whereClauses += "$TABLE_TX_SEARCH_DOCS.$COL_IS_SWAP = 1"
                }
            }

            TransactionSearchLayer.LIQUID -> {
                val allSourcesIncluded =
                    filters.includeSwap &&
                        filters.includeLightning &&
                        filters.includeNative &&
                        filters.includeUsdt
                if (!allSourcesIncluded) {
                    val sourceClauses = mutableListOf<String>()
                    if (filters.includeSwap) sourceClauses += "$TABLE_TX_SEARCH_DOCS.$COL_IS_SWAP = 1"
                    if (filters.includeLightning) sourceClauses += "$TABLE_TX_SEARCH_DOCS.$COL_IS_LIGHTNING = 1"
                    if (filters.includeNative) sourceClauses += "$TABLE_TX_SEARCH_DOCS.$COL_IS_NATIVE = 1"
                    if (filters.includeUsdt) sourceClauses += "$TABLE_TX_SEARCH_DOCS.$COL_HAS_USDT = 1"
                    if (sourceClauses.isEmpty()) return null
                    whereClauses += sourceClauses.joinToString(
                        separator = " OR ",
                        prefix = "(",
                        postfix = ")",
                    )
                }
            }
        }

        return TransactionSearchQueryParts(
            fromClause = fromClause,
            whereClause = whereClauses.joinToString(separator = " AND "),
            whereArgs = whereArgs.toTypedArray(),
        )
    }

    private fun readTransactionSearchDocuments(
        selection: String,
        selectionArgs: Array<String>,
    ): List<TransactionSearchDocument> {
        return try {
            readableDatabase.query(
                TABLE_TX_SEARCH_DOCS,
                arrayOf(
                    COL_WALLET_ID,
                    COL_LAYER,
                    COL_TXID,
                    COL_SORT_TIMESTAMP,
                    COL_SORT_HEIGHT,
                    COL_IS_SWAP,
                    COL_IS_LIGHTNING,
                    COL_IS_NATIVE,
                    COL_HAS_USDT,
                    COL_LABEL_TEXT,
                    COL_ADDRESS,
                    COL_CHANGE_ADDRESS,
                    COL_RECIPIENT_ADDRESS,
                    COL_ADDRESS_LABEL_TEXT,
                    COL_DATE_TOKENS,
                ),
                selection,
                selectionArgs,
                null,
                null,
                null,
            ).use { cursor ->
                val documents = mutableListOf<TransactionSearchDocument>()
                while (cursor.moveToNext()) {
                    val layer =
                        TransactionSearchLayer.fromDbValue(cursor.getString(1))
                            ?: continue
                    documents +=
                        TransactionSearchDocument(
                            walletId = cursor.getString(0),
                            layer = layer,
                            txid = cursor.getString(2),
                            sortTimestampSeconds = if (cursor.isNull(3)) null else cursor.getLong(3),
                            sortHeight = if (cursor.isNull(4)) null else cursor.getLong(4),
                            isSwap = cursor.getInt(5) != 0,
                            isLightning = cursor.getInt(6) != 0,
                            isNative = cursor.getInt(7) != 0,
                            hasUsdt = cursor.getInt(8) != 0,
                            label = cursor.getString(9),
                            address = cursor.getString(10),
                            changeAddress = cursor.getString(11),
                            recipientAddress = cursor.getString(12),
                            addressLabel = cursor.getString(13),
                            dateTokens = cursor.getString(14),
                        )
                }
                documents
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) {
                Log.w(TAG, "Failed to read transaction search docs: ${e.message}")
            }
            emptyList()
        }
    }

    private fun SQLiteDatabase.upsertTransactionSearchDocumentsInternal(documents: Collection<TransactionSearchDocument>) {
        if (documents.isEmpty()) return

        val now = System.currentTimeMillis()
        documents.forEach { document ->
            val values =
                ContentValues().apply {
                    put(COL_WALLET_ID, document.walletId)
                    put(COL_LAYER, document.layer.dbValue)
                    put(COL_TXID, document.txid)
                    put(COL_SORT_TIMESTAMP, document.sortTimestampSeconds)
                    put(COL_SORT_HEIGHT, document.sortHeight)
                    put(COL_IS_SWAP, if (document.isSwap) 1 else 0)
                    put(COL_IS_LIGHTNING, if (document.isLightning) 1 else 0)
                    put(COL_IS_NATIVE, if (document.isNative) 1 else 0)
                    put(COL_HAS_USDT, if (document.hasUsdt) 1 else 0)
                    put(COL_LABEL_TEXT, document.label)
                    put(COL_ADDRESS, document.address)
                    put(COL_CHANGE_ADDRESS, document.changeAddress)
                    put(COL_RECIPIENT_ADDRESS, document.recipientAddress)
                    put(COL_ADDRESS_LABEL_TEXT, document.addressLabel)
                    put(COL_DATE_TOKENS, document.dateTokens)
                    put(COL_CACHED_AT, now)
                }
            insertWithOnConflict(
                TABLE_TX_SEARCH_DOCS,
                null,
                values,
                SQLiteDatabase.CONFLICT_REPLACE,
            )

            delete(
                TABLE_TX_SEARCH_FTS,
                "$COL_WALLET_ID = ? AND $COL_LAYER = ? AND $COL_TXID = ?",
                arrayOf(document.walletId, document.layer.dbValue, document.txid),
            )
            insert(
                TABLE_TX_SEARCH_FTS,
                null,
                ContentValues().apply {
                    put(COL_WALLET_ID, document.walletId)
                    put(COL_LAYER, document.layer.dbValue)
                    put(COL_TXID, document.txid)
                    put(COL_SEARCH_TEXT, buildTransactionSearchableText(document))
                },
            )
        }
    }

    private fun SQLiteDatabase.deleteTransactionSearchDocumentsInternal(
        walletId: String,
        layer: TransactionSearchLayer?,
        txids: Collection<String>?,
    ) {
        val layerValue = layer?.dbValue
        if (txids.isNullOrEmpty()) {
            val selection =
                if (layerValue != null) {
                    "$COL_WALLET_ID = ? AND $COL_LAYER = ?"
                } else {
                    "$COL_WALLET_ID = ?"
                }
            val args =
                if (layerValue != null) {
                    arrayOf(walletId, layerValue)
                } else {
                    arrayOf(walletId)
                }
            delete(TABLE_TX_SEARCH_DOCS, selection, args)
            delete(TABLE_TX_SEARCH_FTS, selection, args)
            return
        }

        txids.chunked(SQLITE_DELETE_CHUNK_SIZE).forEach { chunk ->
            val placeholders = List(chunk.size) { "?" }.joinToString(",")
            val selection =
                buildString {
                    append("$COL_WALLET_ID = ?")
                    if (layerValue != null) {
                        append(" AND $COL_LAYER = ?")
                    }
                    append(" AND $COL_TXID IN ($placeholders)")
                }
            val args =
                buildList {
                    add(walletId)
                    if (layerValue != null) add(layerValue)
                    addAll(chunk)
                }.toTypedArray()
            delete(TABLE_TX_SEARCH_DOCS, selection, args)
            delete(TABLE_TX_SEARCH_FTS, selection, args)
        }
    }

    // ==================== Maintenance ====================

    /**
     * Remove unconfirmed verbose tx entries older than [UNCONFIRMED_TTL_MS].
     * Called periodically to prevent stale unconfirmed data from accumulating.
     */
    fun pruneStaleUnconfirmed() {
        try {
            val cutoff = System.currentTimeMillis() - UNCONFIRMED_TTL_MS
            val deleted =
                writableDatabase.delete(
                    TABLE_TX_VERBOSE,
                    "$COL_CONFIRMED = 0 AND $COL_CACHED_AT < ?",
                    arrayOf(cutoff.toString()),
                )
            if (deleted > 0 && BuildConfig.DEBUG) {
                Log.d(TAG, "Pruned $deleted stale unconfirmed verbose tx entries")
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.w(TAG, "Prune failed: ${e.message}")
        }
    }

    /**
     * Close the helper and remove the physical SQLite database plus sidecars.
     * Used during full wallet wipe to avoid leaving recoverable free pages behind.
     */
    fun deleteDatabaseFile(): Boolean {
        return try {
            close()
            appContext.deleteDatabase(DATABASE_NAME)

            val dbPath = appContext.getDatabasePath(DATABASE_NAME)
            val sqliteFiles = listOf(
                dbPath,
                File("${dbPath.path}-wal"),
                File("${dbPath.path}-shm"),
                File("${dbPath.path}-journal"),
            )
            val success = sqliteFiles.none { it.exists() }

            if (!success) {
                Log.e(TAG, "Failed to delete Electrum cache database file")
            } else if (BuildConfig.DEBUG) {
                Log.d(TAG, "Deleted Electrum cache database file")
            }

            success
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete Electrum cache database file")
            if (BuildConfig.DEBUG) Log.e(TAG, "Electrum cache delete exception", e)
            false
        }
    }
}
