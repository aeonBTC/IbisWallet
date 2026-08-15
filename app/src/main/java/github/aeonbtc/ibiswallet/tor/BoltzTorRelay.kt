package github.aeonbtc.ibiswallet.tor

import android.util.Log
import github.aeonbtc.ibiswallet.BuildConfig
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import kotlin.concurrent.thread

/**
 * Loopback relay for LWK BoltzSession traffic.
 *
 * LWK 0.18 exposes an apiUrl override but not a SOCKS/TLS stack that is reliable
 * on Android for Boltz websockets (native rustls/tungstenite fails subscribe and
 * surfaces as BoltzApi Generic "Failed to send restart signal").
 *
 * LWK connects to http://127.0.0.1:<port>/api/v2 (WS becomes ws://.../ws). Every
 * upstream REST/WebSocket hop is opened by this process:
 * - [Mode.CLEARNET]: platform TLS to api.boltz.exchange:443
 * - [Mode.TOR]: SOCKS5 → Boltz onion :80
 */
class BoltzTorRelay(
    val mode: Mode = Mode.TOR,
    private val torSocksHost: String = "127.0.0.1",
    private val torSocksPortProvider: () -> Int = { TorManager.socksPort() },
    // Test seams: allow pointing the clearnet upstream at a local fake without TLS.
    private val clearnetHostOverride: String = BOLTZ_CLEARNET_HOST,
    private val clearnetPortOverride: Int = BOLTZ_CLEARNET_PORT,
    private val useTlsUpstream: Boolean = true,
) {
    enum class Mode {
        CLEARNET,
        TOR,
    }

    private val running = AtomicBoolean(false)
    private val lock = Any()
    private var serverSocket: ServerSocket? = null
    private var acceptThread: Thread? = null

    fun isRunning(): Boolean = running.get()

    fun start(): String = synchronized(lock) {
        if (running.get()) {
            return@synchronized apiUrl(requireNotNull(serverSocket).localPort)
        }
        val socket = ServerSocket(0, BACKLOG, InetSocketAddress(LOOPBACK_HOST, 0).address)
        serverSocket = socket
        running.set(true)
        acceptThread = thread(name = "BoltzLocalRelay-${mode.name}", isDaemon = true) {
            acceptLoop(socket)
        }
        apiUrl(socket.localPort)
    }

    fun stop() = synchronized(lock) {
        running.set(false)
        runCatching { serverSocket?.close() }
        serverSocket = null
        acceptThread = null
    }

    private fun apiUrl(port: Int): String =
        // Clearnet Boltz is https://api.boltz.exchange/v2/...
        // Onion Boltz is http://<onion>/api/v2/...
        when (mode) {
            Mode.CLEARNET -> "http://$LOOPBACK_HOST:$port/v2"
            Mode.TOR -> "http://$LOOPBACK_HOST:$port/api/v2"
        }

    private fun acceptLoop(server: ServerSocket) {
        while (running.get()) {
            val client =
                try {
                    server.accept()
                } catch (_: SocketException) {
                    break
                } catch (error: IOException) {
                    logWarn("Accept failed: ${error.message}")
                    continue
                }
            thread(name = "BoltzLocalRelay-conn", isDaemon = true) {
                handleConnection(client)
            }
        }
    }

    private fun handleConnection(client: Socket) {
        var upstream: Socket? = null
        try {
            upstream = openUpstream()
            client.soTimeout = READ_TIMEOUT_MS

            val upstreamSocket = upstream
            val upstreamToClient = thread(name = "BoltzLocalRelay-up", isDaemon = true) {
                runCatching { pipe(upstreamSocket.getInputStream(), client.getOutputStream()) }
                // EOF/error in either direction tears down both sides.
                closeQuietly(client)
                closeQuietly(upstreamSocket)
            }

            val upgraded =
                rewriteHttpRequests(
                    input = client.getInputStream(),
                    output = upstreamSocket.getOutputStream(),
                    targetHostHeader = targetHostHeader(),
                )

            if (upgraded) {
                // WebSocket: Boltz pushes swap updates with arbitrarily long idle gaps and
                // LWK mostly only receives, so the client->upstream pipe blocks on an idle
                // read. Joining with a short timeout here would close both sockets ~1s after
                // the upgrade, before the first swap.created push arrives — which made
                // BoltzSession.invoice() wait out its whole timeout and discard the swap.
                // Wait for real EOF instead; teardown is driven by the pipes.
                upstreamToClient.join()
            } else {
                upstreamToClient.join(JOIN_TIMEOUT_MS)
            }
        } catch (error: Exception) {
            logWarn("Connection failed mode=$mode: ${error.message}")
        } finally {
            closeQuietly(client)
            upstream?.let(::closeQuietly)
        }
    }

    private fun openUpstream(): Socket {
        return when (mode) {
            Mode.TOR -> {
                Socket(
                    Proxy(
                        Proxy.Type.SOCKS,
                        InetSocketAddress(torSocksHost, torSocksPortProvider()),
                    ),
                ).also {
                    it.soTimeout = READ_TIMEOUT_MS
                    it.connect(
                        InetSocketAddress.createUnresolved(BOLTZ_ONION_HOST, BOLTZ_ONION_PORT),
                        CONNECT_TIMEOUT_MS,
                    )
                }
            }
            Mode.CLEARNET -> {
                val plain = Socket()
                plain.soTimeout = READ_TIMEOUT_MS
                plain.connect(
                    InetSocketAddress(clearnetHostOverride, clearnetPortOverride),
                    CONNECT_TIMEOUT_MS,
                )
                if (!useTlsUpstream) {
                    plain
                } else {
                    val factory = SSLSocketFactory.getDefault() as SSLSocketFactory
                    factory.createSocket(plain, clearnetHostOverride, clearnetPortOverride, true).also { ssl ->
                        ssl.soTimeout = READ_TIMEOUT_MS
                        val tls = ssl as SSLSocket
                        tls.sslParameters =
                            tls.sslParameters.apply {
                                endpointIdentificationAlgorithm = "HTTPS"
                            }
                        tls.startHandshake()
                    }
                }
            }
        }
    }

    private fun targetHostHeader(): String =
        when (mode) {
            Mode.TOR -> BOLTZ_ONION_HOST
            Mode.CLEARNET -> clearnetHostOverride
        }

    /**
     * Forwards client requests upstream, rewriting the `Host` header.
     *
     * @return true when the connection was upgraded to a WebSocket, meaning the
     * caller must keep both sockets open until a real EOF instead of applying a
     * short join timeout.
     */
    private fun rewriteHttpRequests(
        input: InputStream,
        output: OutputStream,
        targetHostHeader: String,
    ): Boolean {
        while (true) {
            val headerBytes = readHeaders(input) ?: return false
            val headerText = String(headerBytes, StandardCharsets.ISO_8859_1)
            val rewritten = rewriteHostHeader(headerText, targetHostHeader)
            output.write(rewritten.toByteArray(StandardCharsets.ISO_8859_1))
            output.flush()

            if (isWebSocketUpgrade(headerText)) {
                logDebug("WebSocket upgrade forwarded mode=$mode")
                // Full-duplex tunnel for the upgraded connection (client → upstream).
                // Blocks until the client closes or errors.
                pipe(input, output)
                return true
            }

            val contentLength = parseContentLength(headerText)
            if (contentLength > 0L) {
                copyExactly(input, output, contentLength)
                output.flush()
            }
        }
    }

    private fun readHeaders(input: InputStream): ByteArray? {
        val bytes = ArrayList<Byte>(1024)
        var matched = 0
        while (bytes.size < MAX_HEADER_BYTES) {
            val next = input.read()
            if (next == -1) return if (bytes.isEmpty()) null else bytes.toByteArray()
            bytes += next.toByte()
            matched = if (next == HEADER_END[matched].toInt()) matched + 1 else if (next == '\r'.code) 1 else 0
            if (matched == HEADER_END.size) return bytes.toByteArray()
        }
        throw IOException("HTTP headers exceeded $MAX_HEADER_BYTES bytes")
    }

    private fun rewriteHostHeader(headerText: String, targetHostHeader: String): String {
        val lines = headerText.split("\r\n").toMutableList()
        val hostIndex = lines.indexOfFirst { it.startsWith("Host:", ignoreCase = true) }
        if (hostIndex >= 0) {
            lines[hostIndex] = "Host: $targetHostHeader"
        }
        return lines.joinToString("\r\n")
    }

    private fun isWebSocketUpgrade(headerText: String): Boolean =
        isBoltzTorRelayWebSocketUpgrade(headerText)

    private fun parseContentLength(headerText: String): Long =
        boltzTorRelayHeaderLines(headerText)
            .firstOrNull { it.startsWith("Content-Length:", ignoreCase = true) }
            ?.substringAfter(':')
            ?.trim()
            ?.toLongOrNull()
            ?: 0L

    private fun copyExactly(input: InputStream, output: OutputStream, byteCount: Long) {
        var remaining = byteCount
        val buffer = ByteArray(BUFFER_SIZE)
        while (remaining > 0L) {
            val read = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
            if (read == -1) throw IOException("Unexpected EOF while copying HTTP body")
            output.write(buffer, 0, read)
            remaining -= read.toLong()
        }
    }

    private fun pipe(input: InputStream, output: OutputStream) {
        val buffer = ByteArray(BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read == -1) return
            output.write(buffer, 0, read)
            output.flush()
        }
    }

    private fun closeQuietly(socket: Socket) {
        runCatching { socket.close() }
    }

    /**
     * Relay threads must never die because logging failed (for example
     * `android.util.Log` being unavailable under JVM unit tests). Losing the
     * connection thread would silently break the Boltz WebSocket tunnel.
     */
    private fun logDebug(message: String) {
        if (!BuildConfig.DEBUG) return
        runCatching { Log.d(TAG, message) }
    }

    private fun logWarn(message: String) {
        if (!BuildConfig.DEBUG) return
        runCatching { Log.w(TAG, message) }
    }

    internal companion object {
        private const val TAG = "BoltzTorRelay"
        private const val LOOPBACK_HOST = "127.0.0.1"
        private const val BACKLOG = 16
        private const val CONNECT_TIMEOUT_MS = 30_000
        private const val READ_TIMEOUT_MS = 0
        private const val JOIN_TIMEOUT_MS = 1_000L
        private const val BUFFER_SIZE = 32 * 1024
        private const val MAX_HEADER_BYTES = 64 * 1024
        private val HEADER_END = byteArrayOf('\r'.code.toByte(), '\n'.code.toByte(), '\r'.code.toByte(), '\n'.code.toByte())
        private const val BOLTZ_ONION_HOST = "boltzzzbnus4m7mta3cxmflnps4fp7dueu2tgurstbvrbt6xswzcocyd.onion"
        private const val BOLTZ_ONION_PORT = 80
        internal const val BOLTZ_CLEARNET_HOST = "api.boltz.exchange"
        internal const val BOLTZ_CLEARNET_PORT = 443
    }
}

/**
 * HTTP headers use CRLF. [String.lineSequence] keeps trailing `\r`, so exact
 * header equality checks must normalize line endings first — otherwise the
 * Boltz WebSocket upgrade is never detected and LWK's boltz-rust loop never
 * connects through the relay (manifesting as "Failed to send restart signal").
 */
internal fun boltzTorRelayHeaderLines(headerText: String): List<String> {
    return headerText
        .split("\r\n", "\n")
        .map { it.trimEnd('\r') }
        .filter { it.isNotEmpty() }
}

internal fun isBoltzTorRelayWebSocketUpgrade(headerText: String): Boolean {
    return boltzTorRelayHeaderLines(headerText).any {
        it.equals("Upgrade: websocket", ignoreCase = true)
    }
}
