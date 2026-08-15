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
 * Loopback HTTP → Tor SOCKS relay for Esplora when Bark is pointed at a .onion.
 *
 * Bark's native HTTP client does not reliably pick up Android process env proxy
 * vars (ALL_PROXY) for UniFFI reqwest stacks, so onion Esplora fails with
 * local DNS errors. Same pattern as [BoltzTorRelay]: Bark talks clear HTTP to
 * 127.0.0.1; this process opens SOCKS5 to the onion host via [TorManager].
 *
 * Present base URL to Bark as `http://127.0.0.1:<port>/api` (matches public
 * Esplora path layout).
 */
class EsploraTorRelay(
    private val onionHost: String,
    private val onionPort: Int = DEFAULT_ONION_PORT,
    private val torSocksHost: String = "127.0.0.1",
    private val torSocksPortProvider: () -> Int = { TorManager.socksPort() },
) {
    private val running = AtomicBoolean(false)
    private val lock = Any()
    private var serverSocket: ServerSocket? = null
    private var acceptThread: Thread? = null
    private var boundPort: Int = -1

    fun isRunning(): Boolean = running.get()

    /** Base Esplora URL for Bark Config (`.../api`). */
    fun apiBaseUrl(): String {
        val port = boundPort
        check(port > 0) { "EsploraTorRelay not started" }
        return "http://$LOOPBACK_HOST:$port/api"
    }

    fun start(): String =
        synchronized(lock) {
            if (running.get() && boundPort > 0) {
                return@synchronized apiBaseUrl()
            }
            val socket =
                ServerSocket(0, BACKLOG, InetSocketAddress(LOOPBACK_HOST, 0).address)
            serverSocket = socket
            boundPort = socket.localPort
            running.set(true)
            acceptThread =
                thread(name = "EsploraTorRelay", isDaemon = true) {
                    acceptLoop(socket)
                }
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "Started on port $boundPort → $onionHost:$onionPort via Tor SOCKS")
            }
            apiBaseUrl()
        }

    fun stop() =
        synchronized(lock) {
            running.set(false)
            runCatching { serverSocket?.close() }
            serverSocket = null
            acceptThread = null
            boundPort = -1
        }

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
            thread(name = "EsploraTorRelay-conn", isDaemon = true) {
                handleConnection(client)
            }
        }
    }

    private fun handleConnection(client: Socket) {
        var upstream: Socket? = null
        try {
            upstream = openUpstream()
            client.soTimeout = READ_TIMEOUT_MS
            upstream.soTimeout = READ_TIMEOUT_MS

            val upstreamSocket = upstream
            val upstreamToClient =
                thread(name = "EsploraTorRelay-up", isDaemon = true) {
                    runCatching {
                        pipe(upstreamSocket.getInputStream(), client.getOutputStream())
                    }
                    closeQuietly(client)
                    closeQuietly(upstreamSocket)
                }

            rewriteHttpRequests(
                input = client.getInputStream(),
                output = upstreamSocket.getOutputStream(),
                targetHostHeader = onionHost,
            )
            // Short-lived REST: finish after backside pipe or timeout.
            upstreamToClient.join(JOIN_TIMEOUT_MS)
        } catch (error: Exception) {
            if (BuildConfig.DEBUG) {
                Log.w(TAG, "Connection failed: ${error.message}")
            }
        } finally {
            closeQuietly(client)
            upstream?.let(::closeQuietly)
        }
    }

    private fun openUpstream(): Socket =
        Socket(
            Proxy(
                Proxy.Type.SOCKS,
                InetSocketAddress(torSocksHost, torSocksPortProvider()),
            ),
        ).also {
            it.soTimeout = READ_TIMEOUT_MS
            // Unresolved keeps hostname for SOCKS (required for .onion).
            it.connect(
                InetSocketAddress.createUnresolved(onionHost, onionPort),
                CONNECT_TIMEOUT_MS,
            )
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
            val contentLength = parseContentLength(headerText)
            output.write(rewritten.toByteArray(StandardCharsets.ISO_8859_1))
            if (contentLength > 0) {
                copyExactly(input, output, contentLength.toLong())
            }
            output.flush()
            // HTTP/1.0 or Connection: close → single request.
            if (headerText.contains("Connection: close", ignoreCase = true) ||
                headerText.startsWith("GET ", ignoreCase = false) &&
                !headerText.contains("HTTP/1.1")
            ) {
                // Keep reading pipelined HTTP/1.1 until EOF in outer loop.
            }
        }
    }

    private fun rewriteHostHeader(
        headerText: String,
        targetHost: String,
    ): String {
        val lines = headerText.split("\r\n").toMutableList()
        if (lines.isEmpty()) return headerText
        var hostReplaced = false
        for (i in lines.indices) {
            if (lines[i].startsWith("Host:", ignoreCase = true)) {
                lines[i] = "Host: $targetHost"
                hostReplaced = true
            }
        }
        if (!hostReplaced && lines.isNotEmpty()) {
            // Insert after request line.
            lines.add(1, "Host: $targetHost")
        }
        val cleaned = lines.filterNot { it.isEmpty() && lines.indexOf(it) != lines.lastIndex }
        val body = cleaned.joinToString("\r\n")
        return if (body.endsWith("\r\n\r\n")) body else "$body\r\n\r\n"
    }

    private fun parseContentLength(headerText: String): Int {
        val line =
            headerText.lineSequence()
                .firstOrNull { it.startsWith("Content-Length:", ignoreCase = true) }
                ?: return 0
        return line.substringAfter(':').trim().toIntOrNull() ?: 0
    }

    private fun readHeaders(input: InputStream): ByteArray? {
        val buffer = java.io.ByteArrayOutputStream()
        var prev = 0
        var state = 0 // 0: normal, 1: \r, 2: \r\n, 3: \r\n\r
        while (true) {
            val b = input.read()
            if (b < 0) {
                return if (buffer.size() == 0) null else buffer.toByteArray()
            }
            buffer.write(b)
            when (state) {
                0 -> state = if (b == '\r'.code) 1 else 0
                1 -> state = if (b == '\n'.code) 2 else if (b == '\r'.code) 1 else 0
                2 -> state = if (b == '\r'.code) 3 else 0
                3 -> {
                    if (b == '\n'.code) return buffer.toByteArray()
                    state = if (b == '\r'.code) 1 else 0
                }
            }
            if (buffer.size() > MAX_HEADER_BYTES) {
                error("HTTP headers too large")
            }
            prev = b
        }
    }

    private fun pipe(
        input: InputStream,
        output: OutputStream,
    ) {
        val buf = ByteArray(PIPE_BUFFER)
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            output.write(buf, 0, n)
            output.flush()
        }
    }

    private fun copyExactly(
        input: InputStream,
        output: OutputStream,
        byteCount: Long,
    ) {
        var remaining = byteCount
        val buf = ByteArray(PIPE_BUFFER)
        while (remaining > 0) {
            val toRead = minOf(buf.size.toLong(), remaining).toInt()
            val n = input.read(buf, 0, toRead)
            if (n < 0) break
            output.write(buf, 0, n)
            remaining -= n
        }
    }

    private fun closeQuietly(socket: Socket) {
        runCatching { socket.close() }
    }

    companion object {
        private const val TAG = "EsploraTorRelay"
        private const val LOOPBACK_HOST = "127.0.0.1"
        private const val DEFAULT_ONION_PORT = 80
        private const val BACKLOG = 32
        private const val CONNECT_TIMEOUT_MS = 60_000
        private const val READ_TIMEOUT_MS = 120_000
        private const val JOIN_TIMEOUT_MS = 120_000L
        private const val PIPE_BUFFER = 16 * 1024
        private const val MAX_HEADER_BYTES = 64 * 1024

        fun onionHostFromEsploraUrl(url: String): String? =
            runCatching {
                val uri =
                    java.net.URI(
                        if ("://" in url) url else "http://$url",
                    )
                uri.host?.takeIf { it.endsWith(".onion", ignoreCase = true) }
            }.getOrNull()
    }
}
