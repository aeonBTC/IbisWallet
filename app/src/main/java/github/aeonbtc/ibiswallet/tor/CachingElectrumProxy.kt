package github.aeonbtc.ibiswallet.tor

import android.util.Log
import github.aeonbtc.ibiswallet.BuildConfig
import github.aeonbtc.ibiswallet.data.local.ElectrumCache
import github.aeonbtc.ibiswallet.util.TofuTrustManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.selects.select
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.PrintWriter
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.net.SocketTimeoutException
import java.security.cert.X509Certificate
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import kotlin.concurrent.withLock
import kotlin.math.roundToLong

/**
 * Notifications pushed by the Electrum server over the subscription socket.
 * Collected by WalletRepository to trigger targeted wallet updates.
 */
sealed class ElectrumNotification {
    /** A subscribed script hash's status changed (new tx or confirmation). */
    data class ScriptHashChanged(val scriptHash: String, val status: String?) : ElectrumNotification()

    /** A new block was mined. */
    data class NewBlockHeader(val height: Int, val hexHeader: String) : ElectrumNotification()

    /** Subscription socket detected the server connection is dead. */
    data object ConnectionLost : ElectrumNotification()
}

/**
 * Protocol-aware local TCP proxy between BDK's ElectrumClient and the real
 * Electrum server. Combines the roles of the former TorProxyBridge (SSL
 * termination, Tor SOCKS5) and ElectrumFeatureService (relay fee, verbose tx)
 * into a single class with three upstream connections:
 *
 * 1. **Bridge connection** — Bidirectional stream copy (preserves BDK batching)
 *    with line-level interception for `blockchain.transaction.get`:
 *    - BDK→Server: cache hits served instantly without forwarding
 *    - Server→BDK: responses cached in SQLite for future hits
 *
 * 2. **Direct query connection** — Separate socket for verbose tx queries and
 *    relay fee queries. Lazy-initialized, reused across calls.
 *
 * 3. **Subscription connection** — Dedicated socket for `blockchain.scripthash.subscribe`
 *    and `blockchain.headers.subscribe`. A long-running listener coroutine reads
 *    server push notifications and emits them via [notifications] flow. This is
 *    how the app detects incoming payments, confirmations, and new blocks in
 *    real-time without polling.
 *
 * This gives 3 upstream connections while preserving BDK's batched/pipelined
 * request pattern that is critical for sync performance.
 */
class CachingElectrumProxy(
    private val targetHost: String,
    private val targetPort: Int,
    private val useSsl: Boolean = false,
    private val useTorProxy: Boolean = false,
    private val torSocksHost: String = "127.0.0.1",
    private val torSocksPort: Int = 9050,
    private val connectionTimeoutMs: Int = 60000,
    private val soTimeoutMs: Int = 60000,
    private val sslTrustManager: TofuTrustManager? = null,
    private val cache: ElectrumCache? = null,
) {
    companion object {
        private const val TAG = "CachingElectrumProxy"
        private const val BUFFER_SIZE = 32768
        private const val TOR_CONNECT_RETRIES = 3
        private const val TOR_RETRY_DELAY_MS = 2000L
        const val DEFAULT_MIN_FEE_RATE = 1.0
        private const val BTC_PER_KB_TO_SAT_PER_VB = 100_000.0

        /** Read timeout for the subscription socket (5 minutes).
         *  If no data arrives in this window (not even a block header),
         *  we ping the server to verify it's still alive. */
        private const val SUB_SOCKET_TIMEOUT_MS = 300_000

        /** Read timeout applied to the direct socket during ping() only (8s).
         *  Shorter than the heartbeat coroutine timeout (10s) so readLine()
         *  completes and releases directLock before the coroutine is cancelled. */
        private const val PING_SOCKET_TIMEOUT_MS = 8_000

        /** How long to wait for directLock before assuming the connection is
         *  alive (another operation is actively using the socket). */
        private const val PING_LOCK_TIMEOUT_MS = 3_000L
    }

    // ==================== Bridge (BDK traffic) ====================

    private var serverSocket: ServerSocket? = null
    private var localPort: Int = 0

    @Volatile private var isRunning = false
    private var bridgeJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var clientSocket: Socket? = null
    private var bridgeTargetSocket: Socket? = null
    private var preConnectedSocket: Socket? = null

    // Tracks in-flight blockchain.transaction.get request ids for caching responses.
    // Populated by the BDK→Server reader, consumed by the Server→BDK reader.
    private val pendingTxGetRequests = ConcurrentHashMap<Int, String>() // id → txid

    // ==================== Direct queries (verbose tx, relay fee) ====================

    private var directSocket: Socket? = null
    private var directWriter: PrintWriter? = null
    private var directReader: BufferedReader? = null
    private val directLock = ReentrantLock()
    private var directRequestId = 100_000
    private var directHandshakeDone = false

    // ==================== Subscription socket (push notifications) ====================

    private var subSocket: Socket? = null
    private var subWriter: PrintWriter? = null
    private var subReader: BufferedReader? = null
    private val subLock = ReentrantLock()
    private var subRequestId = 200_000
    private var subHandshakeDone = false
    private var subListenerJob: Job? = null

    @Volatile private var subListenerPaused = false

    private val _notifications = MutableSharedFlow<ElectrumNotification>(extraBufferCapacity = 64)

    /** Server push notifications (script hash changes, new blocks). Collect from repository. */
    val notifications: SharedFlow<ElectrumNotification> = _notifications.asSharedFlow()

    // ==================== Lifecycle ====================

    @Synchronized
    fun start(): Int {
        if (isRunning) {
            if (BuildConfig.DEBUG) Log.d(TAG, "Proxy already running on port $localPort")
            return localPort
        }

        try {
            serverSocket =
                ServerSocket(0, 50, java.net.InetAddress.getByName("127.0.0.1")).apply {
                    soTimeout = connectionTimeoutMs
                    reuseAddress = true
                }
            localPort = serverSocket!!.localPort
            isRunning = true

            if (BuildConfig.DEBUG) {
                Log.d(
                    TAG,
                    "Proxy started on port $localPort -> $targetHost:$targetPort (SSL=$useSsl, Tor=$useTorProxy)",
                )
            }

            bridgeJob = scope.launch { acceptConnections() }
            return localPort
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Failed to start proxy", e)
            stop()
            throw e
        }
    }

    @Synchronized
    fun stop() {
        if (BuildConfig.DEBUG) Log.d(TAG, "Stopping proxy")
        isRunning = false
        bridgeJob?.cancel()
        bridgeJob = null
        stopSubscriptionListener()
        closeQuietly(clientSocket)
        closeQuietly(bridgeTargetSocket)
        closeQuietly(preConnectedSocket)
        closeQuietly(serverSocket)
        // Close the direct socket first (without the lock) to interrupt any
        // blocking readLine() inside ping()/subscribeScriptHashes(). This
        // causes the holder of directLock to throw and release it promptly.
        closeQuietly(directSocket)
        closeDirectConnection()
        clientSocket = null
        bridgeTargetSocket = null
        preConnectedSocket = null
        serverSocket = null
        localPort = 0
        pendingTxGetRequests.clear()
    }

    fun setPreConnectedSocket(socket: Socket) {
        preConnectedSocket = socket
    }

    // ==================== Bidirectional Bridge ====================

    private suspend fun acceptConnections() =
        withContext(Dispatchers.IO) {
            while (isRunning && isActive) {
                try {
                    if (BuildConfig.DEBUG) Log.d(TAG, "Waiting for BDK connection on port $localPort...")
                    val client = serverSocket?.accept() ?: break
                    client.soTimeout = soTimeoutMs
                    clientSocket = client

                    if (BuildConfig.DEBUG) Log.d(TAG, "Accepted BDK connection")

                    val target = createTargetConnection()
                    if (target == null) {
                        if (BuildConfig.DEBUG) Log.e(TAG, "Failed to connect to $targetHost:$targetPort")
                        closeQuietly(client)
                        continue
                    }
                    bridgeTargetSocket = target

                    if (BuildConfig.DEBUG) Log.d(TAG, "Bridge connected to target, starting bidirectional copy")
                    bridgeConnections(client, target)
                } catch (_: SocketTimeoutException) {
                    // Accept timeout — loop
                } catch (e: SocketException) {
                    if (isRunning) {
                        if (BuildConfig.DEBUG) Log.e(TAG, "Socket exception in accept loop", e)
                    }
                    break
                } catch (e: Exception) {
                    if (isRunning) {
                        if (BuildConfig.DEBUG) Log.e(TAG, "Error in accept loop", e)
                    }
                }
            }
            if (BuildConfig.DEBUG) Log.d(TAG, "Accept loop ended")
        }

    /**
     * Bidirectional bridge between BDK and the Electrum server.
     *
     * Uses two concurrent coroutines that copy data line-by-line in each
     * direction, preserving BDK's batched/pipelined request pattern:
     *
     * **BDK → Server**: Reads each JSON-RPC line from BDK. For
     * `blockchain.transaction.get` requests with a cache hit, responds to BDK
     * directly without forwarding. Cache misses and all other requests are
     * forwarded to the server immediately (preserving batch timing). Pending
     * tx.get request ids are tracked in [pendingTxGetRequests] for response
     * caching.
     *
     * **Server → BDK**: Reads each line from the server. If the response id
     * matches a pending tx.get request, the result is cached in SQLite. All
     * lines are forwarded to BDK.
     *
     * Both directions use smart flush (only when no more data is immediately
     * available), which batches small writes into fewer Tor circuit sends.
     */
    private suspend fun bridgeConnections(
        client: Socket,
        target: Socket,
    ) = withContext(Dispatchers.IO) {
        pendingTxGetRequests.clear()

        // Synchronized writer for BDK — both directions may write to it
        // (cache-hit responses from clientToServer, forwarded from serverToClient)
        val bdkOutput = client.getOutputStream()
        val bdkWriteLock = ReentrantLock()

        try {
            val clientToServer =
                async {
                    copyClientToServer(
                        client.getInputStream(),
                        target.getOutputStream(),
                        bdkOutput,
                        bdkWriteLock,
                    )
                }

            val serverToClient =
                async {
                    copyServerToClient(
                        target.getInputStream(),
                        bdkOutput,
                        bdkWriteLock,
                    )
                }

            // Wait for either direction to complete (connection closed)
            select {
                clientToServer.onAwait {}
                serverToClient.onAwait {}
            }

            if (BuildConfig.DEBUG) Log.d(TAG, "Bridge connection ended")
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Error in bridge", e)
        } finally {
            closeQuietly(client)
            closeQuietly(target)
            clientSocket = null
            bridgeTargetSocket = null
            pendingTxGetRequests.clear()
        }
    }

    /**
     * BDK → Server direction. Reads lines from BDK, intercepts tx.get cache
     * hits, forwards everything else to the server.
     */
    private fun copyClientToServer(
        bdkInput: InputStream,
        serverOutput: OutputStream,
        bdkOutput: OutputStream,
        bdkWriteLock: ReentrantLock,
    ): Long {
        val bdkReader = BufferedReader(InputStreamReader(bdkInput), BUFFER_SIZE)
        val serverBuffered = BufferedOutputStream(serverOutput, BUFFER_SIZE)
        var totalBytes = 0L

        try {
            while (true) {
                val line = bdkReader.readLine() ?: break
                if (line.isBlank()) continue

                // Try to serve blockchain.transaction.get from cache
                val cacheResponse = tryServFromCache(line)
                if (cacheResponse != null) {
                    // Cache hit — respond directly to BDK, don't forward to server
                    val responseBytes = (cacheResponse + "\n").toByteArray()
                    bdkWriteLock.withLock {
                        bdkOutput.write(responseBytes)
                        bdkOutput.flush()
                    }
                    totalBytes += responseBytes.size
                    continue
                }

                // Track tx.get requests so we can cache the server response
                trackTxGetRequest(line)

                // Forward to server
                val bytes = (line + "\n").toByteArray()
                serverBuffered.write(bytes)
                totalBytes += bytes.size

                // Smart flush: only flush when BDK has no more data ready.
                // This preserves batching — if BDK sent 500 lines, we write
                // them all before flushing once.
                if (!bdkReader.ready()) {
                    serverBuffered.flush()
                }
            }
        } catch (_: SocketException) {
            if (BuildConfig.DEBUG) Log.d(TAG, "[BDK->Server] Socket closed, total bytes: $totalBytes")
        } catch (e: IOException) {
            if (BuildConfig.DEBUG) Log.d(TAG, "[BDK->Server] IO error: ${e.message}, total bytes: $totalBytes")
        }
        try {
            serverBuffered.flush()
        } catch (_: Exception) {
        }
        return totalBytes
    }

    /**
     * Server → BDK direction. Reads lines from server, caches tx.get responses,
     * forwards everything to BDK.
     */
    private fun copyServerToClient(
        serverInput: InputStream,
        bdkOutput: OutputStream,
        bdkWriteLock: ReentrantLock,
    ): Long {
        val serverReader = BufferedReader(InputStreamReader(serverInput), BUFFER_SIZE)
        var totalBytes = 0L

        try {
            while (true) {
                val line = serverReader.readLine() ?: break

                // Opportunistically cache tx.get responses
                tryCacheServerResponse(line)

                val bytes = (line + "\n").toByteArray()
                bdkWriteLock.withLock {
                    bdkOutput.write(bytes)
                    // Smart flush: only when no more server data is ready
                    if (!serverReader.ready()) {
                        bdkOutput.flush()
                    }
                }
                totalBytes += bytes.size
            }
        } catch (_: SocketException) {
            if (BuildConfig.DEBUG) Log.d(TAG, "[Server->BDK] Socket closed, total bytes: $totalBytes")
        } catch (e: IOException) {
            if (BuildConfig.DEBUG) Log.d(TAG, "[Server->BDK] IO error: ${e.message}, total bytes: $totalBytes")
        }
        bdkWriteLock.withLock {
            try {
                bdkOutput.flush()
            } catch (_: Exception) {
            }
        }
        return totalBytes
    }

    // ==================== Cache Interception ====================

    /**
     * Try to serve a BDK request from cache. Only intercepts non-verbose
     * `blockchain.transaction.get` requests.
     *
     * @return JSON-RPC response string if cache hit, null if miss
     */
    private fun tryServFromCache(requestLine: String): String? {
        val txCache = cache ?: return null
        return try {
            val json = JSONObject(requestLine)
            val method = json.optString("method", "")
            if (method != "blockchain.transaction.get") return null

            val params = json.optJSONArray("params") ?: return null
            if (params.length() < 1) return null

            // Only intercept non-verbose (BDK always uses non-verbose)
            if (params.length() >= 2) {
                val verbose = params.opt(1)
                if (verbose == true || verbose == java.lang.Boolean.TRUE) return null
            }

            val txid = params.optString(0, "")
            if (txid.isBlank()) return null

            val cachedHex = txCache.getRawTx(txid) ?: return null

            val response =
                JSONObject().apply {
                    put("jsonrpc", "2.0")
                    put("id", json.opt("id"))
                    put("result", cachedHex)
                }
            if (BuildConfig.DEBUG) Log.d(TAG, "Cache HIT: tx $txid")
            response.toString()
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Track a tx.get request so we can cache the server's response.
     */
    private fun trackTxGetRequest(requestLine: String) {
        try {
            val json = JSONObject(requestLine)
            val method = json.optString("method", "")
            if (method != "blockchain.transaction.get") return

            val params = json.optJSONArray("params") ?: return
            if (params.length() < 1) return

            // Only track non-verbose requests
            if (params.length() >= 2) {
                val verbose = params.opt(1)
                if (verbose == true || verbose == java.lang.Boolean.TRUE) return
            }

            val txid = params.optString(0, "")
            val id = json.optInt("id", -1)
            if (txid.isNotBlank() && id >= 0) {
                pendingTxGetRequests[id] = txid
            }
        } catch (_: Exception) {
        }
    }

    /**
     * Try to cache a server response line if it matches a pending tx.get request.
     */
    private fun tryCacheServerResponse(responseLine: String) {
        val txCache = cache ?: return
        if (pendingTxGetRequests.isEmpty()) return
        try {
            val json = JSONObject(responseLine)
            val id = json.optInt("id", -1)
            val txid = pendingTxGetRequests.remove(id) ?: return

            if (json.has("error") && !json.isNull("error")) return
            val hex = json.optString("result", "")
            if (hex.isBlank() || hex.length < 20) return

            txCache.putRawTx(txid, hex)
            if (BuildConfig.DEBUG) Log.d(TAG, "Cache STORE: tx $txid (${hex.length / 2} bytes)")
        } catch (_: Exception) {
        }
    }

    // ==================== Direct Query Methods ====================

    /**
     * Ensure the direct query connection is alive and handshake complete.
     * Creates a new connection if needed. Must be called while holding [directLock].
     */
    private fun ensureDirectConnectionLocked(): Boolean {
        val writer = directWriter
        val reader = directReader
        if (writer != null && reader != null) {
            val socket = directSocket
            if (socket != null && !socket.isClosed && socket.isConnected) {
                if (!directHandshakeDone) {
                    return performDirectHandshakeLocked(writer, reader)
                }
                return true
            }
        }

        // Create a new direct connection
        closeDirectConnectionLocked()
        try {
            val socket = createTargetConnection() ?: return false
            directSocket = socket

            val newWriter = PrintWriter(socket.getOutputStream(), false)
            val newReader = BufferedReader(InputStreamReader(socket.getInputStream()), BUFFER_SIZE)
            directWriter = newWriter
            directReader = newReader
            directHandshakeDone = false

            return performDirectHandshakeLocked(newWriter, newReader)
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.w(TAG, "Failed to create direct connection: ${e.message}")
            return false
        }
    }

    private fun performDirectHandshakeLocked(
        writer: PrintWriter,
        reader: BufferedReader,
    ): Boolean {
        return try {
            val req =
                JSONObject().apply {
                    put("id", directRequestId++)
                    put("method", "server.version")
                    put(
                        "params",
                        JSONArray().apply {
                            put("IbisWallet")
                            put("1.4")
                        },
                    )
                }
            writer.println(req.toString())
            writer.flush()

            // Read response, skipping push notifications
            while (true) {
                val line = reader.readLine() ?: return false
                if (!isServerPushNotification(line)) break
            }

            directHandshakeDone = true
            if (BuildConfig.DEBUG) Log.d(TAG, "Direct query handshake completed")
            true
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.w(TAG, "Direct handshake failed: ${e.message}")
            false
        }
    }

    private fun sendDirectRequestLocked(
        writer: PrintWriter,
        reader: BufferedReader,
        method: String,
        params: JSONArray,
    ): String? {
        val id = directRequestId++
        val req =
            JSONObject().apply {
                put("id", id)
                put("method", method)
                put("params", params)
            }

        writer.println(req.toString())
        writer.flush()

        // Read response, skipping push notifications
        while (true) {
            val line = reader.readLine() ?: return null
            if (isServerPushNotification(line)) continue
            return line
        }
    }

    private fun isServerPushNotification(line: String): Boolean {
        return try {
            val json = JSONObject(line)
            json.has("method") && (!json.has("id") || json.isNull("id"))
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Subscribe to script hashes via the direct query connection.
     * Pipelined: sends all requests in one flush, reads all responses.
     */
    fun subscribeScriptHashes(scriptHashes: List<String>): Map<String, String?> {
        if (scriptHashes.isEmpty()) return emptyMap()

        return directLock.withLock {
            if (!ensureDirectConnectionLocked()) return@withLock emptyMap()
            val writer = directWriter ?: return@withLock emptyMap()
            val reader = directReader ?: return@withLock emptyMap()

            try {
                val idToScriptHash = mutableMapOf<Int, String>()
                for (scriptHash in scriptHashes) {
                    val id = directRequestId++
                    idToScriptHash[id] = scriptHash
                    val req =
                        JSONObject().apply {
                            put("id", id)
                            put("method", "blockchain.scripthash.subscribe")
                            put("params", JSONArray().apply { put(scriptHash) })
                        }
                    writer.println(req.toString())
                }
                writer.flush()

                val results = mutableMapOf<String, String?>()
                repeat(scriptHashes.size) {
                    val line = reader.readLine()
                    if (line == null) {
                        closeDirectConnectionLocked()
                        return@withLock results
                    }
                    val json = JSONObject(line)

                    if (json.has("method") && json.optString("method") == "blockchain.scripthash.subscribe") {
                        val extra = reader.readLine()
                        if (extra != null) {
                            val extraJson = JSONObject(extra)
                            val extraId = extraJson.optInt("id", -1)
                            val extraHash = idToScriptHash[extraId]
                            if (extraHash != null) {
                                val status: String? =
                                    if (extraJson.isNull("result")) {
                                        null
                                    } else {
                                        extraJson.optString("result")
                                    }
                                results[extraHash] = status
                            }
                        } else {
                            closeDirectConnectionLocked()
                            return@withLock results
                        }
                    } else {
                        val responseId = json.optInt("id", -1)
                        val scriptHash = idToScriptHash[responseId]
                        if (scriptHash != null) {
                            val status: String? =
                                if (json.isNull("result")) {
                                    null
                                } else {
                                    json.optString("result")
                                }
                            results[scriptHash] = status
                        }
                    }
                }
                results
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.w(TAG, "Script hash subscribe failed: ${e.message}")
                closeDirectConnectionLocked()
                emptyMap()
            }
        }
    }

    /**
     * Check if any script hashes changed compared to cached statuses.
     * @return true if changes detected (or error), false if no changes
     */
    fun checkForScriptHashChanges(cachedStatuses: Map<String, String?>): Boolean {
        if (cachedStatuses.isEmpty()) return true
        try {
            val currentStatuses = subscribeScriptHashes(cachedStatuses.keys.toList())
            if (currentStatuses.isEmpty()) return true
            for ((scriptHash, cachedStatus) in cachedStatuses) {
                if (currentStatuses[scriptHash] != cachedStatus) {
                    if (BuildConfig.DEBUG) Log.d(TAG, "Script hash change: $scriptHash")
                    return true
                }
            }
            return false
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.w(TAG, "Script hash check failed: ${e.message}")
            return true
        }
    }

    /**
     * Lightweight health check: sends `server.ping` on the direct query socket.
     * Returns true if the server responded, false if the connection is dead.
     *
     * Uses [tryLock] so that if another operation (sync, verbose tx fetch)
     * is actively using the direct socket, ping returns true immediately —
     * lock contention proves the connection is alive. A tighter socket read
     * timeout ([PING_SOCKET_TIMEOUT_MS]) ensures readLine() completes and
     * releases the lock before the heartbeat coroutine timeout fires,
     * preventing cascading failures from stale lock holds.
     */
    fun ping(): Boolean {
        // If we can't acquire the lock, someone else is actively using the
        // direct socket — the connection is alive by definition.
        if (!directLock.tryLock(PING_LOCK_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
            if (BuildConfig.DEBUG) Log.d(TAG, "Ping: direct socket busy (lock held), treating as alive")
            return true
        }
        try {
            if (!ensureDirectConnectionLocked()) return false
            val writer = directWriter ?: return false
            val reader = directReader ?: return false
            val socket = directSocket ?: return false

            // Temporarily tighten the read timeout so readLine() completes
            // well within the heartbeat's coroutine timeout, then restore.
            val originalTimeout = socket.soTimeout
            try {
                socket.soTimeout = PING_SOCKET_TIMEOUT_MS
                val response =
                    sendDirectRequestLocked(
                        writer,
                        reader,
                        "server.ping",
                        JSONArray(),
                    )
                return response != null
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.w(TAG, "Ping failed: ${e.message}")
                closeDirectConnectionLocked()
                return false
            } finally {
                // Restore only if the socket wasn't closed by the catch block
                if (!socket.isClosed) {
                    try {
                        socket.soTimeout = originalTimeout
                    } catch (_: Exception) {
                    }
                }
            }
        } finally {
            directLock.unlock()
        }
    }

    // ==================== Subscription Socket (real-time push) ====================

    /**
     * Ensure the subscription connection is alive and handshake complete.
     * Must be called while holding [subLock].
     */
    private fun ensureSubConnectionLocked(): Boolean {
        val writer = subWriter
        val reader = subReader
        if (writer != null && reader != null) {
            val socket = subSocket
            if (socket != null && !socket.isClosed && socket.isConnected) {
                if (!subHandshakeDone) {
                    return performSubHandshakeLocked(writer, reader)
                }
                return true
            }
        }

        closeSubConnectionLocked()
        try {
            val socket = createTargetConnection() ?: return false
            // Subscription socket uses a read timeout so we can detect silently-dead
            // servers. On timeout we ping to verify, and emit ConnectionLost if dead.
            socket.soTimeout = SUB_SOCKET_TIMEOUT_MS
            subSocket = socket

            val newWriter = PrintWriter(socket.getOutputStream(), false)
            val newReader = BufferedReader(InputStreamReader(socket.getInputStream()), BUFFER_SIZE)
            subWriter = newWriter
            subReader = newReader
            subHandshakeDone = false

            return performSubHandshakeLocked(newWriter, newReader)
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.w(TAG, "Failed to create subscription connection: ${e.message}")
            return false
        }
    }

    private fun performSubHandshakeLocked(
        writer: PrintWriter,
        reader: BufferedReader,
    ): Boolean {
        return try {
            val req =
                JSONObject().apply {
                    put("id", subRequestId++)
                    put("method", "server.version")
                    put(
                        "params",
                        JSONArray().apply {
                            put("IbisWallet")
                            put("1.4")
                        },
                    )
                }
            writer.println(req.toString())
            writer.flush()

            // Read response, skipping any stale push notifications
            while (true) {
                val line = reader.readLine() ?: return false
                if (!isServerPushNotification(line)) break
            }

            subHandshakeDone = true
            if (BuildConfig.DEBUG) Log.d(TAG, "Subscription socket handshake completed")
            true
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.w(TAG, "Subscription handshake failed: ${e.message}")
            false
        }
    }

    /**
     * Subscribe ALL wallet addresses + block headers on the dedicated subscription
     * socket, then start the notification listener coroutine.
     *
     * This is a one-shot call after connecting. The listener coroutine reads
     * server push notifications for the lifetime of the connection. Call
     * [stopSubscriptionListener] on disconnect.
     *
     * @param scriptHashes All revealed script hashes (receive + change) to monitor.
     * @return Map of scriptHash -> initial status (null for unused addresses),
     *         or empty map on failure.
     */
    fun startSubscriptions(scriptHashes: List<String>): Map<String, String?> {
        return subLock.withLock {
            if (!ensureSubConnectionLocked()) return@withLock emptyMap()
            val writer = subWriter ?: return@withLock emptyMap()
            val reader = subReader ?: return@withLock emptyMap()

            try {
                // 1. Subscribe to block headers
                val headersReq =
                    JSONObject().apply {
                        put("id", subRequestId++)
                        put("method", "blockchain.headers.subscribe")
                        put("params", JSONArray())
                    }
                writer.println(headersReq.toString())

                // 2. Subscribe to all script hashes (pipelined)
                val idToScriptHash = mutableMapOf<Int, String>()
                for (scriptHash in scriptHashes) {
                    val id = subRequestId++
                    idToScriptHash[id] = scriptHash
                    val req =
                        JSONObject().apply {
                            put("id", id)
                            put("method", "blockchain.script hash.subscribe")
                            put("params", JSONArray().apply { put(scriptHash) })
                        }
                    writer.println(req.toString())
                }
                writer.flush()

                // 3. Read block header response
                var headerLine = reader.readLine()
                // Skip any interleaved push notifications
                while (headerLine != null && isServerPushNotification(headerLine)) {
                    headerLine = reader.readLine()
                }
                if (headerLine != null) {
                    try {
                        val headerJson = JSONObject(headerLine)
                        val result = headerJson.optJSONObject("result")
                        if (result != null) {
                            val height = result.optInt("height", -1)
                            val hex = result.optString("hex", "")
                            if (height > 0) {
                                _notifications.tryEmit(ElectrumNotification.NewBlockHeader(height, hex))
                                if (BuildConfig.DEBUG) Log.d(TAG, "Subscribed to block headers, tip at $height")
                            }
                        }
                    } catch (e: Exception) {
                        if (BuildConfig.DEBUG) Log.w(TAG, "Failed to parse header subscribe response: ${e.message}")
                    }
                }

                // 4. Read all script hash subscribe responses
                val results = mutableMapOf<String, String?>()
                val expectedResponses = scriptHashes.size
                var received = 0
                while (received < expectedResponses) {
                    val line = reader.readLine()
                    if (line == null) {
                        closeSubConnectionLocked()
                        return@withLock results
                    }

                    val json = JSONObject(line)

                    // Server push notification interleaved with responses
                    if (isServerPushNotification(line)) {
                        dispatchPushNotification(json)
                        continue // Don't count as a response
                    }

                    // Normal response — match by id
                    val responseId = json.optInt("id", -1)
                    val scriptHash = idToScriptHash[responseId]
                    if (scriptHash != null) {
                        val status: String? =
                            if (json.isNull("result")) {
                                null
                            } else {
                                json.optString("result")
                            }
                        results[scriptHash] = status
                        received++
                    } else {
                        // Unknown id — could be the headers response if we miscounted
                        received++
                    }
                }

                if (BuildConfig.DEBUG) Log.d(TAG, "Subscribed ${results.size} script hashes on subscription socket")

                // 5. Start the notification listener coroutine
                startNotificationListener(reader)

                results
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.w(TAG, "Subscription setup failed: ${e.message}")
                closeSubConnectionLocked()
                emptyMap()
            }
        }
    }

    /**
     * Subscribe additional script hashes on the existing subscription socket
     * (e.g., after a sync reveals new addresses from gap limit expansion).
     *
     * Pauses the listener via [subListenerPaused] flag so responses are read
     * here rather than dispatched as notifications. The listener continues
     * running but ignores lines while paused.
     *
     * @return Map of scriptHash -> initial status for the new subscriptions.
     */
    fun subscribeAdditionalScriptHashes(scriptHashes: List<String>): Map<String, String?> {
        if (scriptHashes.isEmpty()) return emptyMap()

        val writer = subWriter ?: return emptyMap()
        val socket = subSocket ?: return emptyMap()
        if (socket.isClosed || !socket.isConnected) return emptyMap()

        // Pause the listener — it will ignore lines while this flag is set.
        // The listener may still read some lines, but they'll be silently
        // dropped. This is fine — subscription responses have our request IDs,
        // and push notifications will be re-delivered by the server on the next
        // status change.
        subListenerPaused = true
        try {
            val idToScriptHash = mutableMapOf<Int, String>()
            for (scriptHash in scriptHashes) {
                val id = subRequestId++
                idToScriptHash[id] = scriptHash
                val req =
                    JSONObject().apply {
                        put("id", id)
                        put("method", "blockchain.scripthash.subscribe")
                        put("params", JSONArray().apply { put(scriptHash) })
                    }
                writer.println(req.toString())
            }
            writer.flush()

            // Note: responses will be read by the listener coroutine (which
            // ignores them because subListenerPaused=true). We don't read
            // responses here to avoid fighting the listener for socket reads.
            // The initial status for these new addresses is unknown, but the
            // next push notification or background sync will catch changes.
            if (BuildConfig.DEBUG) {
                Log.d(
                    TAG,
                    "Sent ${scriptHashes.size} additional subscribe requests (responses handled by listener)",
                )
            }

            // Return the script hashes as subscribed with null status (unknown).
            // The server will push notifications for any future changes.
            return scriptHashes.associateWith { null as String? }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.w(TAG, "Additional subscription failed: ${e.message}")
            return emptyMap()
        } finally {
            subListenerPaused = false
        }
    }

    /**
     * Start the long-running notification listener coroutine.
     * Reads lines from the subscription socket and dispatches push
     * notifications to the [_notifications] flow.
     *
     * IMPORTANT: The listener does NOT hold [subLock] during readLine().
     * readLine() blocks indefinitely (soTimeout=0) waiting for server pushes,
     * so holding a lock would deadlock stop(). Instead, coordination with
     * [subscribeAdditionalScriptHashes] uses [subListenerPaused]: the writer
     * sets this flag to pause the listener, sends requests, reads responses
     * itself, then unpauses.
     */
    private fun startNotificationListener(reader: BufferedReader) {
        subListenerJob?.cancel()
        subListenerPaused = false
        subListenerJob =
            scope.launch {
                if (BuildConfig.DEBUG) Log.d(TAG, "Notification listener started")
                try {
                    while (isActive && isRunning) {
                        val line =
                            withContext(Dispatchers.IO) {
                                try {
                                    reader.readLine()
                                } catch (_: SocketTimeoutException) {
                                    // No data in SUB_SOCKET_TIMEOUT_MS — verify connection
                                    // is alive with a lightweight ping on the direct socket.
                                    if (BuildConfig.DEBUG) Log.d(TAG, "Sub socket timeout, pinging server...")
                                    if (ping()) {
                                        // Server is alive, just quiet. Continue listening.
                                        if (BuildConfig.DEBUG) Log.d(TAG, "Ping OK, server still alive")
                                        return@withContext "" // blank line = continue
                                    } else {
                                        // Server is dead — emit ConnectionLost
                                        if (BuildConfig.DEBUG) Log.w(TAG, "Ping failed, server is dead")
                                        _notifications.tryEmit(ElectrumNotification.ConnectionLost)
                                        return@withContext null
                                    }
                                } catch (_: Exception) {
                                    null
                                }
                            }

                        if (line == null) {
                            if (BuildConfig.DEBUG) Log.d(TAG, "Subscription socket closed, listener exiting")
                            break
                        }

                        if (line.isBlank()) continue

                        // If paused (subscribeAdditionalScriptHashes is reading),
                        // skip dispatching — the caller handles its own responses.
                        if (subListenerPaused) continue

                        try {
                            val json = JSONObject(line)
                            if (isServerPushNotification(line)) {
                                dispatchPushNotification(json)
                            }
                            // Non-push lines (stale responses) are silently ignored
                        } catch (e: Exception) {
                            if (BuildConfig.DEBUG) Log.w(TAG, "Failed to parse notification: ${e.message}")
                        }
                    }
                } catch (_: CancellationException) {
                    // Normal cancellation
                } catch (e: Exception) {
                    if (BuildConfig.DEBUG) Log.w(TAG, "Notification listener error: ${e.message}")
                }
                if (BuildConfig.DEBUG) Log.d(TAG, "Notification listener stopped")
            }
    }

    /**
     * Parse a server push notification and emit it to the [_notifications] flow.
     */
    private fun dispatchPushNotification(json: JSONObject) {
        val method = json.optString("method", "")
        val params = json.optJSONArray("params") ?: return

        when (method) {
            "blockchain.scripthash.subscribe" -> {
                if (params.length() >= 2) {
                    val scriptHash = params.optString(0, "")
                    val status: String? = if (params.isNull(1)) null else params.optString(1)
                    if (scriptHash.isNotEmpty()) {
                        _notifications.tryEmit(ElectrumNotification.ScriptHashChanged(scriptHash, status))
                        if (BuildConfig.DEBUG) Log.d(TAG, "Push: script hash changed $scriptHash")
                    }
                }
            }
            "blockchain.headers.subscribe" -> {
                if (params.length() >= 1) {
                    val headerObj = params.optJSONObject(0)
                    if (headerObj != null) {
                        val height = headerObj.optInt("height", -1)
                        val hex = headerObj.optString("hex", "")
                        if (height > 0) {
                            _notifications.tryEmit(ElectrumNotification.NewBlockHeader(height, hex))
                            if (BuildConfig.DEBUG) Log.d(TAG, "Push: new block at height $height")
                        }
                    }
                }
            }
        }
    }

    /**
     * Stop the subscription listener and close the subscription socket.
     *
     * Closes the socket FIRST (without holding [subLock]) to unblock
     * any readLine() call in the listener, then cancels the job and
     * cleans up references. This avoids a deadlock where stop() tries
     * to acquire [subLock] while the listener holds it during readLine().
     */
    private fun stopSubscriptionListener() {
        // 1. Close socket to unblock any blocking readLine() in the listener.
        //    This causes readLine() to throw IOException → returns null → listener exits.
        try {
            subSocket?.close()
        } catch (_: Exception) {
        }
        // 2. Cancel the coroutine job (in case it's at a suspension point)
        subListenerJob?.cancel()
        subListenerJob = null
        // 3. Clean up references (safe now — listener has exited or will exit)
        subWriter = null
        subReader = null
        subSocket = null
        subHandshakeDone = false
        subListenerPaused = false
    }

    /**
     * Close the subscription connection. Must be called while holding [subLock].
     * Used by [ensureSubConnectionLocked] and [startSubscriptions] for error cleanup.
     */
    private fun closeSubConnectionLocked() {
        try {
            subWriter?.close()
        } catch (_: Exception) {
        }
        try {
            subReader?.close()
        } catch (_: Exception) {
        }
        try {
            subSocket?.close()
        } catch (_: Exception) {
        }
        subWriter = null
        subReader = null
        subSocket = null
        subHandshakeDone = false
    }

    /**
     * Pipeline blockchain.transaction.get requests via the direct query
     * connection. Caches responses in [ElectrumCache].
     */
    fun pipelineFetchTransactions(txids: List<String>): Int {
        if (txids.isEmpty()) return 0
        return directLock.withLock {
            if (!ensureDirectConnectionLocked()) return@withLock 0
            val writer = directWriter ?: return@withLock 0
            val reader = directReader ?: return@withLock 0

            try {
                val idToTxid = mutableMapOf<Int, String>()
                for (txid in txids) {
                    val id = directRequestId++
                    idToTxid[id] = txid
                    val req =
                        JSONObject().apply {
                            put("id", id)
                            put("method", "blockchain.transaction.get")
                            put("params", JSONArray().apply { put(txid) })
                        }
                    writer.println(req.toString())
                }
                writer.flush()

                var received = 0
                repeat(txids.size) {
                    val line = reader.readLine()
                    if (line == null) {
                        closeDirectConnectionLocked()
                        return@withLock received
                    }
                    val json = JSONObject(line)
                    if (json.has("method") && json.optString("method") == "blockchain.scripthash.subscribe") {
                        val extra = reader.readLine()
                        if (extra == null) {
                            closeDirectConnectionLocked()
                            return@withLock received
                        }
                        tryCachePipelineResponse(extra, idToTxid)
                        received++
                    } else {
                        tryCachePipelineResponse(line, idToTxid)
                        received++
                    }
                }
                received
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.w(TAG, "Pipeline tx fetch failed: ${e.message}")
                closeDirectConnectionLocked()
                0
            }
        }
    }

    private fun tryCachePipelineResponse(
        line: String,
        idToTxid: Map<Int, String>,
    ) {
        val txCache = cache ?: return
        try {
            val json = JSONObject(line)
            val id = json.optInt("id", -1)
            val txid = idToTxid[id] ?: return
            if (json.has("error") && !json.isNull("error")) return
            val hex = json.optString("result", "")
            if (hex.isBlank() || hex.length < 20) return
            txCache.putRawTx(txid, hex)
        } catch (_: Exception) {
        }
    }

    data class TxDetails(
        val txid: String,
        val size: Int,
        val vsize: Int,
        val weight: Int,
    )

    /**
     * Result of analyzing a transaction for a specific address.
     */
    data class AddressTxInfo(
        val netAmountSats: Long, // Positive = received, negative = spent
        val timestamp: Long?, // Unix timestamp from blocktime/time field, null if unconfirmed
        val counterpartyAddress: String? = null, // For sends: recipient address; for receives: sender is unknown
        val feeSats: Long? = null, // Transaction fee in sats (if determinable)
    )

    /**
     * Get the net amount (in sats) and timestamp for a specific address from a transaction.
     * Checks vouts for received amounts and vins (via prevout lookup) for spent amounts.
     * Returns null if the transaction can't be fetched.
     */
    fun getAddressTxInfo(
        txid: String,
        address: String,
    ): AddressTxInfo? {
        val verboseJson = getVerboseTxJson(txid) ?: return null

        // Extract timestamp (blocktime preferred, falls back to time)
        val blocktime = verboseJson.optLong("blocktime", 0L)
        val time = verboseJson.optLong("time", 0L)
        val timestamp =
            if (blocktime > 0L) {
                blocktime
            } else if (time > 0L) {
                time
            } else {
                null
            }

        // Sum outputs paying to our address (received)
        var received = 0L
        val vouts = verboseJson.optJSONArray("vout")
        if (vouts != null) {
            for (i in 0 until vouts.length()) {
                val vout = vouts.optJSONObject(i) ?: continue
                val valueBtc = vout.optDouble("value", 0.0)
                val scriptPubKey = vout.optJSONObject("scriptPubKey") ?: continue
                val addr = scriptPubKey.optString("address", "")
                if (addr == address) {
                    received += (valueBtc * 100_000_000.0).roundToLong()
                }
            }
        }

        // Sum inputs spending from our address (spent)
        var spent = 0L
        val vins = verboseJson.optJSONArray("vin")
        if (vins != null) {
            for (i in 0 until vins.length()) {
                val vin = vins.optJSONObject(i) ?: continue
                if (vin.has("coinbase")) continue

                // Try prevout (included by some Electrum servers)
                val prevout = vin.optJSONObject("prevout")
                if (prevout != null) {
                    val spk = prevout.optJSONObject("scriptPubKey")
                    val prevAddr = spk?.optString("address", "") ?: ""
                    if (prevAddr == address) {
                        spent += (prevout.optDouble("value", 0.0) * 100_000_000.0).roundToLong()
                    }
                    continue
                }

                // Fall back: fetch the previous tx and look up the referenced vout
                val prevTxid = vin.optString("txid", "")
                val prevVoutIdx = vin.optInt("vout", -1)
                if (prevTxid.isBlank() || prevVoutIdx < 0) continue

                val prevTxJson = getVerboseTxJson(prevTxid) ?: continue
                val prevVouts = prevTxJson.optJSONArray("vout") ?: continue
                if (prevVoutIdx >= prevVouts.length()) continue

                val prevVout = prevVouts.optJSONObject(prevVoutIdx) ?: continue
                val prevSpk = prevVout.optJSONObject("scriptPubKey") ?: continue
                val prevAddr = prevSpk.optString("address", "")
                if (prevAddr == address) {
                    spent += (prevVout.optDouble("value", 0.0) * 100_000_000.0).roundToLong()
                }
            }
        }

        val net = received - spent

        // For sends (net < 0), find the recipient address (first vout NOT paying to our address)
        var counterparty: String? = null
        if (net < 0 && vouts != null) {
            for (i in 0 until vouts.length()) {
                val vout = vouts.optJSONObject(i) ?: continue
                val scriptPubKey = vout.optJSONObject("scriptPubKey") ?: continue
                val addr = scriptPubKey.optString("address", "")
                if (addr.isNotBlank() && addr != address) {
                    counterparty = addr
                    break
                }
            }
        }

        // Calculate fee: sum(vin values) - sum(vout values)
        var totalVoutValue = 0L
        if (vouts != null) {
            for (i in 0 until vouts.length()) {
                val vout = vouts.optJSONObject(i) ?: continue
                totalVoutValue += (vout.optDouble("value", 0.0) * 100_000_000.0).roundToLong()
            }
        }
        // Total input value = spent (inputs from our address) + other inputs
        // We can only compute fee if we resolved all inputs
        val totalVinValue =
            spent +
                run {
                    var otherInputs = 0L
                    if (vins != null) {
                        for (i in 0 until vins.length()) {
                            val vin = vins.optJSONObject(i) ?: continue
                            if (vin.has("coinbase")) continue

                            val prevout = vin.optJSONObject("prevout")
                            if (prevout != null) {
                                val spk = prevout.optJSONObject("scriptPubKey")
                                val prevAddr = spk?.optString("address", "") ?: ""
                                if (prevAddr != address) {
                                    otherInputs += (prevout.optDouble("value", 0.0) * 100_000_000.0).roundToLong()
                                }
                                continue
                            }

                            val prevTxid = vin.optString("txid", "")
                            val prevVoutIdx = vin.optInt("vout", -1)
                            if (prevTxid.isBlank() || prevVoutIdx < 0) {
                                otherInputs = -1L
                                break
                            }

                            val prevTxJson = getVerboseTxJson(prevTxid)
                            if (prevTxJson == null) {
                                otherInputs = -1L
                                break
                            }
                            val prevVoutsArr = prevTxJson.optJSONArray("vout")
                            if (prevVoutsArr == null || prevVoutIdx >= prevVoutsArr.length()) {
                                otherInputs = -1L
                                break
                            }

                            val prevVout = prevVoutsArr.optJSONObject(prevVoutIdx)
                            if (prevVout == null) {
                                otherInputs = -1L
                                break
                            }
                            val prevSpk = prevVout.optJSONObject("scriptPubKey")
                            val prevAddr = prevSpk?.optString("address", "") ?: ""
                            if (prevAddr != address) {
                                otherInputs += (prevVout.optDouble("value", 0.0) * 100_000_000.0).roundToLong()
                            }
                        }
                    }
                    otherInputs
                }
        val feeSats =
            if (totalVinValue >= 0 && totalVoutValue >= 0) {
                val fee = totalVinValue - totalVoutValue
                if (fee >= 0) fee else null
            } else {
                null
            }

        return AddressTxInfo(
            netAmountSats = net,
            timestamp = timestamp,
            counterpartyAddress = counterparty,
            feeSats = feeSats,
        )
    }

    /**
     * Fetch the verbose transaction JSON object. Checks cache first, then queries server.
     */
    private fun getVerboseTxJson(txid: String): JSONObject? {
        cache?.getVerboseTx(txid)?.let { cachedJson ->
            return try {
                JSONObject(cachedJson)
            } catch (_: Exception) {
                null
            }
        }

        return directLock.withLock {
            if (!ensureDirectConnectionLocked()) return@withLock null
            val writer = directWriter ?: return@withLock null
            val reader = directReader ?: return@withLock null

            try {
                val response =
                    sendDirectRequestLocked(
                        writer, reader,
                        "blockchain.transaction.get",
                        JSONArray().apply {
                            put(txid)
                            put(true)
                        },
                    ) ?: return@withLock null

                val json = JSONObject(response)
                if (json.has("error") && !json.isNull("error")) return@withLock null

                val result = json.optJSONObject("result")
                if (result != null) {
                    val confirmed = result.optInt("confirmations", 0) > 0
                    cache?.putVerboseTx(txid, result.toString(), confirmed)
                }
                result
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.w(TAG, "Verbose tx query failed for $txid: ${e.message}")
                closeDirectConnectionLocked()
                null
            }
        }
    }

    /**
     * Fetch verbose transaction details. Checks cache first, then queries
     * server via the direct query connection.
     */
    fun getTransactionDetails(txid: String): TxDetails? {
        val result = getVerboseTxJson(txid) ?: return null
        return parseVerboseTxDetails(txid, result)
    }

    private fun parseVerboseTxDetails(
        txid: String,
        result: JSONObject,
    ): TxDetails? {
        val size = result.optInt("size", -1)
        val vsize = result.optInt("vsize", -1)
        val weight = result.optInt("weight", -1)
        val calculatedVsize =
            when {
                vsize > 0 -> vsize
                weight > 0 -> (weight + 3) / 4
                size > 0 -> size
                else -> return null
            }
        return TxDetails(
            txid = txid,
            size = if (size > 0) size else calculatedVsize,
            vsize = calculatedVsize,
            weight = if (weight > 0) weight else calculatedVsize * 4,
        )
    }

    /**
     * Query the Electrum server's minimum relay fee via the direct query connection.
     */
    fun getMinAcceptableFeeRate(): Double {
        return directLock.withLock {
            if (!ensureDirectConnectionLocked()) return@withLock DEFAULT_MIN_FEE_RATE
            val writer = directWriter ?: return@withLock DEFAULT_MIN_FEE_RATE
            val reader = directReader ?: return@withLock DEFAULT_MIN_FEE_RATE

            try {
                val response =
                    sendDirectRequestLocked(
                        writer, reader, "blockchain.relayfee", JSONArray(),
                    ) ?: return@withLock DEFAULT_MIN_FEE_RATE

                val json = JSONObject(response)
                if (json.has("error") && !json.isNull("error")) return@withLock DEFAULT_MIN_FEE_RATE

                val relayFeeBtcPerKb = json.optDouble("result", -1.0)
                if (relayFeeBtcPerKb < 0) return@withLock DEFAULT_MIN_FEE_RATE

                val minFeeRate = relayFeeBtcPerKb * BTC_PER_KB_TO_SAT_PER_VB
                if (minFeeRate !in 0.01..100.0) return@withLock DEFAULT_MIN_FEE_RATE

                if (BuildConfig.DEBUG) Log.d(TAG, "Server relay fee: $relayFeeBtcPerKb BTC/kB = $minFeeRate sat/vB")
                minFeeRate
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.w(TAG, "Relay fee query failed: ${e.message}")
                closeDirectConnectionLocked()
                DEFAULT_MIN_FEE_RATE
            }
        }
    }

    /**
     * Query the balance for a script hash via blockchain.scripthash.get_balance.
     * Returns Pair(confirmed, unconfirmed) in satoshis, or null on failure.
     */
    fun getScriptHashBalance(scriptHash: String): Pair<Long, Long>? {
        return directLock.withLock {
            if (!ensureDirectConnectionLocked()) return@withLock null
            val writer = directWriter ?: return@withLock null
            val reader = directReader ?: return@withLock null

            try {
                val params = JSONArray().apply { put(scriptHash) }
                val response =
                    sendDirectRequestLocked(
                        writer, reader, "blockchain.scripthash.get_balance", params,
                    ) ?: return@withLock null

                val json = JSONObject(response)
                if (json.has("error") && !json.isNull("error")) return@withLock null

                val result = json.optJSONObject("result") ?: return@withLock null
                val confirmed = result.optLong("confirmed", 0L)
                val unconfirmed = result.optLong("unconfirmed", 0L)
                Pair(confirmed, unconfirmed)
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.w(TAG, "Script hash balance query failed: ${e.message}")
                closeDirectConnectionLocked()
                null
            }
        }
    }

    /**
     * Query the transaction history for a script hash via blockchain.scripthash.get_history.
     * Returns list of (txid, height) pairs. Height 0 or -1 means unconfirmed.
     */
    fun getScriptHashHistory(scriptHash: String): List<Pair<String, Int>>? {
        return directLock.withLock {
            if (!ensureDirectConnectionLocked()) return@withLock null
            val writer = directWriter ?: return@withLock null
            val reader = directReader ?: return@withLock null

            try {
                val params = JSONArray().apply { put(scriptHash) }
                val response =
                    sendDirectRequestLocked(
                        writer, reader, "blockchain.scripthash.get_history", params,
                    ) ?: return@withLock null

                val json = JSONObject(response)
                if (json.has("error") && !json.isNull("error")) return@withLock null

                val result = json.optJSONArray("result") ?: return@withLock emptyList()
                val history = mutableListOf<Pair<String, Int>>()
                for (i in 0 until result.length()) {
                    val entry = result.getJSONObject(i)
                    val txHash = entry.optString("tx_hash", "")
                    val height = entry.optInt("height", 0)
                    if (txHash.isNotBlank()) history.add(Pair(txHash, height))
                }
                history
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.w(TAG, "Script hash history query failed: ${e.message}")
                closeDirectConnectionLocked()
                null
            }
        }
    }

    // ==================== Connection Management ====================

    private fun closeDirectConnectionLocked() {
        try {
            directWriter?.close()
        } catch (_: Exception) {
        }
        try {
            directReader?.close()
        } catch (_: Exception) {
        }
        try {
            directSocket?.close()
        } catch (_: Exception) {
        }
        directWriter = null
        directReader = null
        directSocket = null
        directHandshakeDone = false
    }

    private fun closeDirectConnection() {
        directLock.withLock { closeDirectConnectionLocked() }
    }

    private fun createTargetConnection(): Socket? {
        preConnectedSocket?.let { socket ->
            preConnectedSocket = null
            if (!socket.isClosed && socket.isConnected) {
                if (BuildConfig.DEBUG) Log.d(TAG, "Reusing pre-connected socket from cert probe")
                return socket
            }
            closeQuietly(socket)
        }

        val maxAttempts = if (useTorProxy) TOR_CONNECT_RETRIES else 1
        for (attempt in 1..maxAttempts) {
            try {
                val socket =
                    if (useTorProxy) {
                        val proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress(torSocksHost, torSocksPort))
                        Socket(proxy).also {
                            it.soTimeout = soTimeoutMs
                            it.connect(
                                InetSocketAddress.createUnresolved(targetHost, targetPort),
                                connectionTimeoutMs,
                            )
                        }
                    } else {
                        Socket().also {
                            it.soTimeout = soTimeoutMs
                            it.connect(InetSocketAddress(targetHost, targetPort), connectionTimeoutMs)
                        }
                    }
                return if (useSsl) wrapWithSsl(socket) else socket
            } catch (e: SocketException) {
                if (BuildConfig.DEBUG) Log.w(TAG, "Connection attempt $attempt/$maxAttempts failed: ${e.message}")
                if (attempt < maxAttempts) {
                    try {
                        Thread.sleep(TOR_RETRY_DELAY_MS * attempt)
                    } catch (_: InterruptedException) {
                        return null
                    }
                }
            } catch (_: SocketTimeoutException) {
                if (BuildConfig.DEBUG) Log.w(TAG, "Connection attempt $attempt/$maxAttempts timed out")
                if (attempt < maxAttempts) {
                    try {
                        Thread.sleep(TOR_RETRY_DELAY_MS * attempt)
                    } catch (_: InterruptedException) {
                        return null
                    }
                }
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.e(TAG, "Failed to create target connection", e)
                return null
            }
        }
        return null
    }

    /**
     * Create a trust-all SSLSocketFactory for .onion connections.
     * Tor already provides transport security for .onion addresses.
     * Kept private to prevent misuse on non-.onion hosts.
     */
    @android.annotation.SuppressLint("CustomX509TrustManager", "TrustAllX509TrustManager")
    private fun createOnionSSLSocketFactory(): SSLSocketFactory {
        val trustAll =
            object : X509TrustManager {
                override fun checkClientTrusted(
                    chain: Array<out X509Certificate>,
                    authType: String,
                ) {}

                override fun checkServerTrusted(
                    chain: Array<out X509Certificate>,
                    authType: String,
                ) {}

                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            }
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, arrayOf<TrustManager>(trustAll), null)
        return sslContext.socketFactory
    }

    private fun wrapWithSsl(socket: Socket): SSLSocket {
        val sslSocketFactory =
            if (sslTrustManager != null) {
                TofuTrustManager.createSSLSocketFactory(sslTrustManager)
            } else {
                createOnionSSLSocketFactory()
            }
        val sslSocket =
            sslSocketFactory.createSocket(
                socket,
                targetHost,
                targetPort,
                true,
            ) as SSLSocket
        sslSocket.startHandshake()
        if (BuildConfig.DEBUG) Log.d(TAG, "SSL handshake completed with $targetHost:$targetPort")
        return sslSocket
    }

    private fun closeQuietly(socket: Socket?) {
        try {
            socket?.close()
        } catch (_: Exception) {
        }
    }

    private fun closeQuietly(socket: ServerSocket?) {
        try {
            socket?.close()
        } catch (_: Exception) {
        }
    }
}
