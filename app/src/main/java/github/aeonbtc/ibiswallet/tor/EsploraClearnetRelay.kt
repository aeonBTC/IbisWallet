package github.aeonbtc.ibiswallet.tor

import android.util.Log
import github.aeonbtc.ibiswallet.BuildConfig
import github.aeonbtc.ibiswallet.util.PreferIpv4Dns
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.net.URI
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import kotlin.concurrent.thread

/**
 * Loopback HTTP → IPv4-first HTTPS/HTTP relay for clearnet Esplora.
 *
 * Bark's native client is IPv6-first and can stall a full connect timeout on a
 * broken AAAA (common with mempool.space). Ibis preflights with [PreferIpv4Dns],
 * then points Bark at `http://127.0.0.1:<port>/…` so native code never dials
 * those AAAA records.
 */
class EsploraClearnetRelay(
    private val upstreamHost: String,
    private val upstreamPort: Int,
    private val useTls: Boolean,
    private val pathPrefix: String,
) {
    private val running = AtomicBoolean(false)
    private val lock = Any()
    private var serverSocket: ServerSocket? = null
    private var acceptThread: Thread? = null
    private var boundPort: Int = -1

    fun isRunning(): Boolean = running.get()

    fun apiBaseUrl(): String {
        val port = boundPort
        check(port > 0) { "EsploraClearnetRelay not started" }
        return "http://$LOOPBACK_HOST:$port$pathPrefix"
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
                thread(name = "EsploraClearnetRelay", isDaemon = true) {
                    acceptLoop(socket)
                }
            if (BuildConfig.DEBUG) {
                Log.d(
                    TAG,
                    "Started on port $boundPort → $upstreamHost:$upstreamPort tls=$useTls",
                )
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
            thread(name = "EsploraClearnetRelay-conn", isDaemon = true) {
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
                thread(name = "EsploraClearnetRelay-up", isDaemon = true) {
                    runCatching {
                        pipe(upstreamSocket.getInputStream(), client.getOutputStream())
                    }
                    closeQuietly(client)
                    closeQuietly(upstreamSocket)
                }

            rewriteHttpRequests(
                input = client.getInputStream(),
                output = upstreamSocket.getOutputStream(),
                targetHostHeader = hostHeader(),
            )
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

    private fun openUpstream(): Socket {
        val addresses = PreferIpv4Dns.lookup(upstreamHost)
        if (addresses.isEmpty()) {
            error("No addresses for $upstreamHost")
        }
        var lastError: Exception? = null
        for (address in addresses) {
            val plain = Socket()
            try {
                plain.soTimeout = READ_TIMEOUT_MS
                plain.connect(InetSocketAddress(address, upstreamPort), CONNECT_TIMEOUT_MS)
                return if (!useTls) {
                    plain
                } else {
                    wrapTls(plain)
                }
            } catch (error: Exception) {
                lastError = error
                closeQuietly(plain)
            }
        }
        throw lastError ?: IOException("Esplora upstream connect failed")
    }

    private fun wrapTls(plain: Socket): Socket {
        val factory = SSLSocketFactory.getDefault() as SSLSocketFactory
        return factory.createSocket(plain, upstreamHost, upstreamPort, true).also { ssl ->
            ssl.soTimeout = READ_TIMEOUT_MS
            val tls = ssl as SSLSocket
            tls.sslParameters =
                tls.sslParameters.apply {
                    endpointIdentificationAlgorithm = "HTTPS"
                }
            tls.startHandshake()
        }
    }

    private fun hostHeader(): String =
        if (
            (useTls && upstreamPort == 443) ||
                (!useTls && upstreamPort == 80)
        ) {
            upstreamHost
        } else {
            "$upstreamHost:$upstreamPort"
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
        var state = 0
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
        private const val TAG = "EsploraClearnetRelay"
        private const val LOOPBACK_HOST = "127.0.0.1"
        private const val BACKLOG = 32
        private const val CONNECT_TIMEOUT_MS = 3_000
        private const val READ_TIMEOUT_MS = 30_000
        private const val JOIN_TIMEOUT_MS = 60_000L
        private const val PIPE_BUFFER = 16 * 1024
        private const val MAX_HEADER_BYTES = 64 * 1024

        fun parse(url: String): Target? =
            runCatching {
                val uri = URI(if ("://" in url) url else "https://$url")
                val host = uri.host?.takeIf { it.isNotBlank() } ?: return@runCatching null
                if (host.endsWith(".onion", ignoreCase = true)) return@runCatching null
                val scheme = uri.scheme?.lowercase().orEmpty()
                val useTls =
                    when (scheme) {
                        "https" -> true
                        "http" -> false
                        else -> return@runCatching null
                    }
                val port =
                    when {
                        uri.port > 0 -> uri.port
                        useTls -> 443
                        else -> 80
                    }
                val path = uri.rawPath.orEmpty().trimEnd('/')
                Target(
                    host = host,
                    port = port,
                    useTls = useTls,
                    pathPrefix = path,
                    loopback = host == LOOPBACK_HOST || host == "localhost",
                )
            }.getOrNull()

        fun fromUrl(url: String): EsploraClearnetRelay? {
            val target = parse(url) ?: return null
            if (target.loopback) return null
            return EsploraClearnetRelay(
                upstreamHost = target.host,
                upstreamPort = target.port,
                useTls = target.useTls,
                pathPrefix = target.pathPrefix,
            )
        }
    }

    data class Target(
        val host: String,
        val port: Int,
        val useTls: Boolean,
        val pathPrefix: String,
        val loopback: Boolean,
    )
}
