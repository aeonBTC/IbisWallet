package github.aeonbtc.ibiswallet.tor

import android.util.Log
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
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import java.security.cert.X509Certificate

/**
 * A TCP-to-SOCKS5 bridge that allows BDK (which doesn't support SOCKS5 proxy)
 * to connect to .onion addresses through Tor.
 * 
 * How it works:
 * 1. Opens a local ServerSocket on a random port
 * 2. BDK connects to this local port
 * 3. Bridge creates a SOCKS5 connection through Tor to the actual target
 * 4. Bidirectionally pipes data between BDK and the target server
 */
class TorProxyBridge(
    private val targetHost: String,
    private val targetPort: Int,
    private val useSsl: Boolean = false,
    private val torSocksHost: String = "127.0.0.1",
    private val torSocksPort: Int = 9050,
    private val connectionTimeoutMs: Int = 60000,
    private val soTimeoutMs: Int = 60000
) {
    companion object {
        private const val TAG = "TorProxyBridge"
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
     * Start the bridge and return the local port to connect to
     */
    @Synchronized
    fun start(): Int {
        if (isRunning) {
            Log.d(TAG, "Bridge already running on port $localPort")
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
            
            Log.d(TAG, "Bridge started on port $localPort, target: $targetHost:$targetPort (SSL: $useSsl)")
            
            // Start accepting connections in background
            bridgeJob = scope.launch {
                acceptConnections()
            }
            
            return localPort
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start bridge", e)
            stop()
            throw e
        }
    }
    
    /**
     * Stop the bridge and close all connections
     */
    @Synchronized
    fun stop() {
        Log.d(TAG, "Stopping bridge")
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
     * Accept connections from BDK and bridge them to the target through Tor
     */
    private suspend fun acceptConnections() = withContext(Dispatchers.IO) {
        while (isRunning && isActive) {
            try {
                Log.d(TAG, "Waiting for connection on port $localPort...")
                
                // Accept connection from BDK
                val client = serverSocket?.accept() ?: break
                client.soTimeout = soTimeoutMs
                clientSocket = client
                
                Log.d(TAG, "Accepted connection from ${client.inetAddress}:${client.port}")
                
                // Create SOCKS5 connection to target through Tor
                val target = createTorConnection()
                if (target == null) {
                    Log.e(TAG, "Failed to create Tor connection to $targetHost:$targetPort")
                    closeQuietly(client)
                    continue
                }
                targetSocket = target
                
                Log.d(TAG, "Connected to target $targetHost:$targetPort through Tor")
                
                // Bridge the connections
                bridgeConnections(client, target)
                
            } catch (e: SocketTimeoutException) {
                // Timeout waiting for connection, continue loop
                Log.d(TAG, "Accept timeout, continuing...")
            } catch (e: SocketException) {
                if (isRunning) {
                    Log.e(TAG, "Socket exception in accept loop", e)
                }
                // Socket was closed, exit loop
                break
            } catch (e: Exception) {
                if (isRunning) {
                    Log.e(TAG, "Error in accept loop", e)
                }
            }
        }
        
        Log.d(TAG, "Accept loop ended")
    }
    
    /**
     * Create a connection to the target through Tor's SOCKS5 proxy
     */
    private fun createTorConnection(): Socket? {
        return try {
            // Create SOCKS5 proxy
            val proxy = Proxy(
                Proxy.Type.SOCKS,
                InetSocketAddress(torSocksHost, torSocksPort)
            )
            
            // Create socket through proxy
            val socket = Socket(proxy)
            socket.soTimeout = soTimeoutMs
            
            // Connect to target through the proxy
            // For .onion addresses, the SOCKS5 proxy (Tor) handles DNS resolution
            socket.connect(
                InetSocketAddress.createUnresolved(targetHost, targetPort),
                connectionTimeoutMs
            )
            
            // If SSL is required, wrap the socket
            if (useSsl) {
                wrapWithSsl(socket)
            } else {
                socket
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create Tor connection", e)
            null
        }
    }
    
    /**
     * Wrap a socket with SSL/TLS
     * For Tor connections to onion addresses, we use a trust-all manager
     * since onion services often use self-signed certificates
     */
    private fun wrapWithSsl(socket: Socket): SSLSocket {
        // Create trust manager that accepts all certificates
        val trustAllCerts = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
            override fun checkServerTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        }
        
        val trustManagers = arrayOf<TrustManager>(trustAllCerts)
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, trustManagers, null)
        
        val sslSocketFactory: SSLSocketFactory = sslContext.socketFactory
        val sslSocket = sslSocketFactory.createSocket(
            socket,
            targetHost,
            targetPort,
            true // Auto-close underlying socket
        ) as SSLSocket
        
        // Start handshake
        sslSocket.startHandshake()
        
        Log.d(TAG, "SSL handshake completed with $targetHost:$targetPort")
        return sslSocket
    }
    
    /**
     * Bridge two sockets by copying data in both directions
     */
    private suspend fun bridgeConnections(client: Socket, target: Socket) = withContext(Dispatchers.IO) {
        Log.d(TAG, "Starting bidirectional bridge")
        
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
            
            Log.d(TAG, "Bridge connection ended")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in bridge", e)
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
                    Log.d(TAG, "[$direction] EOF reached, total bytes: $totalBytes")
                    break
                }
                
                output.write(buffer, 0, bytesRead)
                output.flush()
                totalBytes += bytesRead
            }
        } catch (e: SocketException) {
            Log.d(TAG, "[$direction] Socket closed, total bytes: $totalBytes")
        } catch (e: IOException) {
            Log.d(TAG, "[$direction] IO error: ${e.message}, total bytes: $totalBytes")
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
