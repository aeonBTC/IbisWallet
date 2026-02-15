package github.aeonbtc.ibiswallet.data.local

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import androidx.core.database.sqlite.transaction
import github.aeonbtc.ibiswallet.BuildConfig

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
 * - **Block headers** (`block_headers`): keyed by height. Immutable once
 *   confirmed (deep enough). Eliminates repeated header fetches during sync.
 *
 * The cache is shared across all wallets — transaction data is wallet-agnostic.
 * Database file: `<filesDir>/electrum_cache.db`
 */
class ElectrumCache(context: Context) : SQLiteOpenHelper(
    context,
    DATABASE_NAME,
    null,
    DATABASE_VERSION,
) {
    companion object {
        private const val TAG = "ElectrumCache"
        private const val DATABASE_NAME = "electrum_cache.db"
        private const val DATABASE_VERSION = 2

        // Table: raw transaction hex (blockchain.transaction.get non-verbose)
        private const val TABLE_TX_RAW = "tx_raw"
        private const val COL_TXID = "txid"
        private const val COL_HEX = "hex"
        private const val COL_CACHED_AT = "cached_at"

        // Table: verbose transaction JSON (blockchain.transaction.get verbose=true)
        private const val TABLE_TX_VERBOSE = "tx_verbose"
        private const val COL_JSON = "json"
        private const val COL_CONFIRMED = "confirmed"

        // Table: block headers (blockchain.block.header)
        private const val TABLE_BLOCK_HEADERS = "block_headers"
        private const val COL_HEIGHT = "height"
        private const val COL_HEADER = "header"

        // Table: persisted script hash statuses for smartSync on app relaunch.
        // Compared against subscription responses to detect changes while the app was closed.
        private const val TABLE_SCRIPT_HASH_STATUSES = "script_hash_statuses"
        private const val COL_SCRIPT_HASH = "script_hash"
        private const val COL_STATUS = "status"
        // COL_CACHED_AT reused from above

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
            CREATE TABLE $TABLE_BLOCK_HEADERS (
                $COL_HEIGHT INTEGER PRIMARY KEY,
                $COL_HEADER TEXT NOT NULL,
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
     * Replaces all existing entries (full snapshot).
     */
    fun saveScriptHashStatuses(statuses: Map<String, String?>) {
        if (statuses.isEmpty()) return
        try {
            writableDatabase.transaction {
                delete(TABLE_SCRIPT_HASH_STATUSES, null, null)
                val now = System.currentTimeMillis()
                for ((scriptHash, status) in statuses) {
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
            val result = mutableMapOf<String, String?>()
            readableDatabase.query(
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
            result
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
     * Clear all cached data. Used when switching to a different Electrum server
     * (cached data may be from a different chain/network).
     */
    fun clearAll() {
        try {
            val db = writableDatabase
            db.delete(TABLE_TX_RAW, null, null)
            db.delete(TABLE_TX_VERBOSE, null, null)
            db.delete(TABLE_BLOCK_HEADERS, null, null)
            db.delete(TABLE_SCRIPT_HASH_STATUSES, null, null)
            if (BuildConfig.DEBUG) Log.d(TAG, "Cleared all cache tables")
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.w(TAG, "Failed to clear cache: ${e.message}")
        }
    }
}
