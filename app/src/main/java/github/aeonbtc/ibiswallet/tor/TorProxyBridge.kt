package github.aeonbtc.ibiswallet.tor

import android.util.Log
import github.aeonbtc.ibiswallet.BuildConfig
import github.aeonbtc.ibiswallet.util.TofuTrustManager
import kotlinx.coroutines.*
import kotlinx.coroutines.selects.select
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.net.SocketTimeoutException
import javax.net.ssl.SSLSocket

/**
 * A local TCP proxy that terminates SSL at the Kotlin/Android layer, allowing
 * BDK's ElectrumClient to connect via plaintext tcp:// to a local port while
 * the actual server connection uses SSL handled by Android's native TLS stack.
 *
 * This is necessary because BDK's bundled rustls does not work reliably for SSL
 * on Android. By handling SSL in Kotlin, we get TOFU (Trust-On-First-Use) certificate
 * verification for self-signed certs (common in Electrum) and Android-native TLS.
 *
 * Supports both clearnet and Tor (SOCKS5) connections:
 *
 * - Clearnet SSL: Local TCP -> direct SSL socket to server
 * - Tor SSL:      Local TCP -> SOCKS5 proxy -> SSL socket to server
 * - Tor TCP:      Not needed (BDK handles SOCKS5 natively for tcp://)
 * - Clearnet TCP: Not needed (BDK connects directly)
 */
class TorProxyBridge(
    private val targetHost: String,
    private val targetPort: Int,
    private val useSsl: Boolean = false,
    private val useTorProxy: Boolean = false,
    private val torSocksHost: String = "127.0.0.1",
    private val torSocksPort: Int = 9050,
    private val connectionTimeoutMs: Int = 60000,
    private val soTimeoutMs: Int = 60000,
    private val sslTrustManager: TofuTrustManager? = null
) {
    companion object {
        private const val TAG = "SslProxyBridge"
        private const val BUFFER_SIZE = 8192
    }
    
    private var serverSocket: ServerSocket? = null
    private var localPort: Int = 0
    private var isRunning = false
    private var bridgeJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // Current active connections
    private var clientSocket: Socket? = null
    private var targetSocket: Socket? = null
    
    /**
     * Start the bridge and return the local port to connect to.
     * BDK should connect to tcp://127.0.0.1:<returnedPort>
     */
    @Synchronized
    fun start(): Int {
        if (isRunning) {
            if (BuildConfig.DEBUG) Log.d(TAG, "Bridge already running on port $localPort")
            return localPort
        }
        
        try {
            // Create server socket on random available port
            serverSocket = ServerSocket(0).apply {
                soTimeout = connectionTimeoutMs
                reuseAddress = true
            }
            localPort = serverSocket!!.localPort
            isRunning = true
            
            if (BuildConfig.DEBUG) Log.d(TAG, "Bridge started on port $localPort, target: $targetHost:$targetPort (SSL: $useSsl, Tor: $useTorProxy)")
            
            // Start accepting connections in background
            bridgeJob = scope.launch {
                acceptConnections()
            }
            
            return localPort
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Failed to start bridge", e)
            stop()
            throw e
        }
    }
    
    /**
     * Stop the bridge and close all connections
     */
    @Synchronized
    fun stop() {
        if (BuildConfig.DEBUG) Log.d(TAG, "Stopping bridge")
        isRunning = false
        
        bridgeJob?.cancel()
        bridgeJob = null
        
        closeQuietly(clientSocket)
        closeQuietly(targetSocket)
        closeQuietly(serverSocket)
        
        clientSocket = null
        targetSocket = null
        serverSocket = null
        localPort = 0
    }
    
    /**
     * Check if the bridge is running
     */
    fun isRunning(): Boolean = isRunning
    
    /**
     * Get the local port (0 if not running)
     */
    fun getLocalPort(): Int = localPort
    
    /**
     * Accept connections from BDK and bridge them to the target
     */
    private suspend fun acceptConnections() = withContext(Dispatchers.IO) {
        while (isRunning && isActive) {
            try {
                if (BuildConfig.DEBUG) Log.d(TAG, "Waiting for connection on port $localPort...")
                
                // Accept connection from BDK
                val client = serverSocket?.accept() ?: break
                client.soTimeout = soTimeoutMs
                clientSocket = client
                
                if (BuildConfig.DEBUG) Log.d(TAG, "Accepted connection from ${client.inetAddress}:${client.port}")
                
                // Create connection to target (with optional Tor proxy and SSL)
                val target = createTargetConnection()
                if (target == null) {
                    if (BuildConfig.DEBUG) Log.e(TAG, "Failed to create connection to $targetHost:$targetPort")
                    closeQuietly(client)
                    continue
                }
                targetSocket = target
                
                if (BuildConfig.DEBUG) Log.d(TAG, "Connected to target $targetHost:$targetPort (SSL: $useSsl, Tor: $useTorProxy)")
                
                // Bridge the connections
                bridgeConnections(client, target)
                
            } catch (e: SocketTimeoutException) {
                // Timeout waiting for connection, continue loop
                if (BuildConfig.DEBUG) Log.d(TAG, "Accept timeout, continuing...")
            } catch (e: SocketException) {
                if (isRunning) {
                    if (BuildConfig.DEBUG) Log.e(TAG, "Socket exception in accept loop", e)
                }
                // Socket was closed, exit loop
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
     * Create a connection to the target server.
     * - If Tor is enabled, connects through SOCKS5 proxy
     * - If SSL is enabled, wraps the socket with SSL using Android's TLS stack
     * - For clearnet+SSL: direct SSL socket to server
     * - For Tor+SSL: SOCKS5 socket wrapped with SSL
     */
    private fun createTargetConnection(): Socket? {
        return try {
            val socket = if (useTorProxy) {
                // Create SOCKS5 proxy connection through Tor
                val proxy = Proxy(
                    Proxy.Type.SOCKS,
                    InetSocketAddress(torSocksHost, torSocksPort)
                )
                
                val proxySocket = Socket(proxy)
                proxySocket.soTimeout = soTimeoutMs
                
                // For .onion addresses, the SOCKS5 proxy (Tor) handles DNS resolution
                proxySocket.connect(
                    InetSocketAddress.createUnresolved(targetHost, targetPort),
                    connectionTimeoutMs
                )
                proxySocket
            } else {
                // Direct clearnet connection
                val directSocket = Socket()
                directSocket.soTimeout = soTimeoutMs
                directSocket.connect(
                    InetSocketAddress(targetHost, targetPort),
                    connectionTimeoutMs
                )
                directSocket
            }
            
            // If SSL is required, wrap the socket with Android's TLS
            if (useSsl) {
                wrapWithSsl(socket)
            } else {
                socket
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Failed to create target connection", e)
            null
        }
    }
    
    /**
     * Wrap a socket with SSL/TLS using Android's native TLS stack.
     * Uses the provided TofuTrustManager for certificate verification,
     * or a trust-all fallback for .onion connections (Tor provides transport security).
     */
    private fun wrapWithSsl(socket: Socket): SSLSocket {
        val sslSocketFactory = if (sslTrustManager != null) {
            TofuTrustManager.createSSLSocketFactory(sslTrustManager)
        } else {
            // Fallback for .onion or when no trust manager provided
            TofuTrustManager.createOnionSSLSocketFactory()
        }
        
        val sslSocket = sslSocketFactory.createSocket(
            socket,
            targetHost,
            targetPort,
            true // Auto-close underlying socket
        ) as SSLSocket
        
        // Start handshake - this triggers TofuTrustManager.checkServerTrusted()
        // which may throw CertificateFirstUseException or CertificateMismatchException
        sslSocket.startHandshake()
        
        if (BuildConfig.DEBUG) Log.d(TAG, "SSL handshake completed with $targetHost:$targetPort")
        return sslSocket
    }
    
    /**
     * Bridge two sockets by copying data in both directions
     */
    private suspend fun bridgeConnections(client: Socket, target: Socket) = withContext(Dispatchers.IO) {
        if (BuildConfig.DEBUG) Log.d(TAG, "Starting bidirectional bridge")
        
        try {
            // Launch two coroutines to copy data in both directions
            val clientToTarget = async {
                copyStream(client.getInputStream(), target.getOutputStream(), "client->target")
            }
            
            val targetToClient = async {
                copyStream(target.getInputStream(), client.getOutputStream(), "target->client")
            }
            
            // Wait for either direction to complete (connection closed)
            select<Unit> {
                clientToTarget.onAwait {}
                targetToClient.onAwait {}
            }
            
            if (BuildConfig.DEBUG) Log.d(TAG, "Bridge connection ended")
            
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Error in bridge", e)
        } finally {
            // Clean up both connections
            closeQuietly(client)
            closeQuietly(target)
            clientSocket = null
            targetSocket = null
        }
    }
    
    /**
     * Copy data from input stream to output stream until EOF or error
     */
    private fun copyStream(input: InputStream, output: OutputStream, direction: String): Long {
        val buffer = ByteArray(BUFFER_SIZE)
        var totalBytes = 0L
        
        try {
            while (true) {
                val bytesRead = input.read(buffer)
                if (bytesRead == -1) {
                    if (BuildConfig.DEBUG) Log.d(TAG, "[$direction] EOF reached, total bytes: $totalBytes")
                    break
                }
                
                output.write(buffer, 0, bytesRead)
                output.flush()
                totalBytes += bytesRead
            }
        } catch (e: SocketException) {
            if (BuildConfig.DEBUG) Log.d(TAG, "[$direction] Socket closed, total bytes: $totalBytes")
        } catch (e: IOException) {
            if (BuildConfig.DEBUG) Log.d(TAG, "[$direction] IO error: ${e.message}, total bytes: $totalBytes")
        }
        
        return totalBytes
    }
    
    /**
     * Close a socket quietly, ignoring exceptions
     */
    private fun closeQuietly(socket: Socket?) {
        try {
            socket?.close()
        } catch (e: Exception) {
            // Ignore
        }
    }
    
    /**
     * Close a server socket quietly, ignoring exceptions
     */
    private fun closeQuietly(socket: ServerSocket?) {
        try {
            socket?.close()
        } catch (e: Exception) {
            // Ignore
        }
    }
}
