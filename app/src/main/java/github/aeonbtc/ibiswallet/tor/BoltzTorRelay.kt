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
import kotlin.concurrent.thread

/**
 * Loopback relay for LWK BoltzSession traffic.
 *
 * LWK 0.18 exposes an apiUrl override but not SOCKS proxy configuration. This
 * relay lets LWK connect to http://127.0.0.1:<port>/api/v2 while every upstream
 * REST/WebSocket connection is made through Tor SOCKS5 to Boltz's onion API.
 */
class BoltzTorRelay(
    private val torSocksHost: String = "127.0.0.1",
    private val torSocksPort: Int = 9050,
    private val targetHost: String = BOLTZ_ONION_HOST,
    private val targetPort: Int = 80,
) {
    private val running = AtomicBoolean(false)
    private val lock = Any()
    private var serverSocket: ServerSocket? = null
    private var acceptThread: Thread? = null

    fun start(): String = synchronized(lock) {
        if (running.get()) {
            return@synchronized apiUrl(requireNotNull(serverSocket).localPort)
        }
        val socket = ServerSocket(0, BACKLOG, InetSocketAddress(LOOPBACK_HOST, 0).address)
        serverSocket = socket
        running.set(true)
        acceptThread = thread(name = "BoltzTorRelay", isDaemon = true) {
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

    private fun apiUrl(port: Int): String = "http://$LOOPBACK_HOST:$port/api/v2"

    private fun acceptLoop(server: ServerSocket) {
        while (running.get()) {
            val client =
                try {
                    server.accept()
                } catch (_: SocketException) {
                    break
                } catch (error: IOException) {
                    if (BuildConfig.DEBUG) Log.w(TAG, "Accept failed: ${error.message}")
                    continue
                }
            thread(name = "BoltzTorRelay-conn", isDaemon = true) {
                handleConnection(client)
            }
        }
    }

    private fun handleConnection(client: Socket) {
        var upstream: Socket? = null
        try {
            upstream = Socket(Proxy(Proxy.Type.SOCKS, InetSocketAddress(torSocksHost, torSocksPort))).also {
                it.soTimeout = READ_TIMEOUT_MS
                it.connect(InetSocketAddress.createUnresolved(targetHost, targetPort), CONNECT_TIMEOUT_MS)
            }
            client.soTimeout = READ_TIMEOUT_MS

            val upstreamSocket = upstream
            val upstreamToClient = thread(name = "BoltzTorRelay-up", isDaemon = true) {
                runCatching { pipe(upstreamSocket.getInputStream(), client.getOutputStream()) }
                closeQuietly(client)
                closeQuietly(upstreamSocket)
            }

            rewriteHttpRequests(
                input = client.getInputStream(),
                output = upstreamSocket.getOutputStream(),
                targetHostHeader = targetHost,
            )
            upstreamToClient.join(JOIN_TIMEOUT_MS)
        } catch (error: Exception) {
            if (BuildConfig.DEBUG) Log.w(TAG, "Connection failed: ${error.message}")
        } finally {
            closeQuietly(client)
            upstream?.let(::closeQuietly)
        }
    }

    private fun rewriteHttpRequests(
        input: InputStream,
        output: OutputStream,
        targetHostHeader: String,
    ) {
        while (true) {
            val headerBytes = readHeaders(input) ?: return
            val headerText = String(headerBytes, StandardCharsets.ISO_8859_1)
            val rewritten = rewriteHostHeader(headerText, targetHostHeader)
            output.write(rewritten.toByteArray(StandardCharsets.ISO_8859_1))
            output.flush()

            if (isWebSocketUpgrade(headerText)) {
                pipe(input, output)
                return
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
        headerText.lineSequence().any { it.equals("Upgrade: websocket", ignoreCase = true) }

    private fun parseContentLength(headerText: String): Long =
        headerText.lineSequence()
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

    private companion object {
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
    }
}
