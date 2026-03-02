package com.hidayatfauzi6.zeroad.engine

import android.net.VpnService
import android.util.Log
import java.io.IOException
import java.net.InetAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * ZeroAd 2.0 - TCP Connection Manager
 * 
 * Mengelola TCP connections dengan proper connection pooling:
 * - Persistent connections untuk HTTP/HTTPS
 * - Connection reuse
 * - Proper keep-alive handling
 * - Timeout management
 */
class TcpConnectionManager(private val vpnService: VpnService) {
    
    companion object {
        private const val TAG = "TcpConnectionManager"
        
        // Connection timeout (ms)
        private const val CONNECT_TIMEOUT_MS = 5000L
        private const val READ_TIMEOUT_MS = 5000L
        private const val WRITE_TIMEOUT_MS = 5000L
        
        // Max connections per destination
        private const val MAX_CONNECTIONS_PER_DEST = 4
        
        // Connection idle timeout (ms)
        private const val IDLE_TIMEOUT_MS = 60000L
    }
    
    data class ConnectionKey(
        val dstIp: String,
        val dstPort: Int
    )
    
    data class TcpConnection(
        val socket: Socket,
        var lastUsedTime: Long = System.currentTimeMillis()
    )
    
    // Connection pool: dst -> list of connections
    private val connectionPool = ConcurrentHashMap<ConnectionKey, MutableList<TcpConnection>>()
    
    // Locks per connection key (untuk thread safety)
    private val connectionLocks = ConcurrentHashMap<ConnectionKey, ReentrantLock>()
    
    // Connection counters
    private val connectionCounts = ConcurrentHashMap<ConnectionKey, Int>()
    
    /**
     * Forward TCP packet dengan connection pooling
     */
    suspend fun forwardTcp(
        packet: ByteBuffer,
        ipHeaderLen: Int
    ): ByteArray? {
        val dstIp = getDestIp(packet, ipHeaderLen)
        val dstPort = getDestPort(packet, ipHeaderLen)
        val payload = extractTcpPayload(packet, ipHeaderLen)
        
        if (payload.isEmpty()) {
            Log.w(TAG, "Empty TCP payload")
            return null
        }
        
        val key = ConnectionKey(dstIp, dstPort)
        val lock = connectionLocks.getOrPut(key) { ReentrantLock() }
        
        return try {
            lock.withLock {
                // Get or create connection
                val connection = getOrCreateConnection(key)
                
                if (connection != null) {
                    // Send payload
                    sendPayload(connection.socket, payload)
                    
                    // Read response (non-blocking dengan timeout)
                    val response = readResponse(connection.socket)
                    
                    // Update last used time
                    connection.lastUsedTime = System.currentTimeMillis()
                    
                    // Return connection to pool if still usable
                    if (connection.socket.isConnected && !connection.socket.isClosed) {
                        returnConnectionToPool(key, connection)
                    } else {
                        removeConnection(key, connection)
                    }
                    
                    response
                } else {
                    // Could not get connection
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "TCP forward error for $dstIp:$dstPort", e)
            cleanupConnection(key)
            null
        }
    }
    
    /**
     * Get or create TCP connection
     */
    private fun getOrCreateConnection(key: ConnectionKey): TcpConnection? {
        // Check existing connections in pool
        val pool = connectionPool[key]
        
        if (pool != null && pool.isNotEmpty()) {
            // Find active connection
            val now = System.currentTimeMillis()
            
            for (connection in pool) {
                // Check if connection is still valid
                if (connection.socket.isConnected && 
                    !connection.socket.isClosed &&
                    now - connection.lastUsedTime < IDLE_TIMEOUT_MS) {
                    
                    pool.remove(connection)
                    connection.lastUsedTime = now
                    return connection
                }
            }
            
            // All connections expired, cleanup
            pool.forEach { 
                try { it.socket.close() } catch (e: Exception) {}
            }
            pool.clear()
        }
        
        // Create new connection
        return try {
            val socket = createTcpConnection(key)
            TcpConnection(socket)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create TCP connection", e)
            null
        }
    }
    
    /**
     * Create new TCP connection
     */
    private fun createTcpConnection(key: ConnectionKey): Socket {
        val socket = Socket(InetAddress.getByName(key.dstIp), key.dstPort)
        
        // Configure socket
        socket.tcpNoDelay = true  // Disable Nagle's algorithm
        socket.keepAlive = true   // Enable keep-alive
        socket.soTimeout = READ_TIMEOUT_MS.toInt()
        
        // Set timeouts (API 24+)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            try {
                socket.soTimeout = READ_TIMEOUT_MS.toInt()
            } catch (e: Exception) {
                // Ignore
            }
        }
        
        // Protect from VPN routing
        try {
            vpnService.protect(socket)
        } catch (e: Exception) {
            Log.e(TAG, "Error protecting socket", e)
        }
        
        // Track connection count
        connectionCounts[key] = (connectionCounts[key] ?: 0) + 1
        
        Log.d(TAG, "New TCP connection to ${key.dstIp}:${key.dstPort} (total: ${connectionCounts[key]})")
        
        return socket
    }
    
    /**
     * Send TCP payload
     */
    private fun sendPayload(socket: Socket, payload: ByteArray) {
        try {
            socket.getOutputStream().use { outputStream ->
                outputStream.write(payload)
                outputStream.flush()
            }
        } catch (e: SocketTimeoutException) {
            throw e
        } catch (e: IOException) {
            Log.e(TAG, "Error sending TCP payload", e)
            throw e
        }
    }
    
    /**
     * Read TCP response (non-blocking dengan timeout)
     */
    private fun readResponse(socket: Socket): ByteArray? {
        return try {
            socket.getInputStream().use { inputStream ->
                val buffer = ByteArray(4096)
                val bytesRead = inputStream.read(buffer)
                
                if (bytesRead > 0) {
                    buffer.copyOf(bytesRead)
                } else {
                    null  // Connection closed
                }
            }
        } catch (e: SocketTimeoutException) {
            null  // Timeout, but connection still valid
        } catch (e: IOException) {
            Log.e(TAG, "Error reading TCP response", e)
            null
        }
    }
    
    /**
     * Return connection to pool
     */
    private fun returnConnectionToPool(key: ConnectionKey, connection: TcpConnection) {
        // Check connection limit
        val pool = connectionPool.getOrPut(key) { mutableListOf() }
        
        if (pool.size < MAX_CONNECTIONS_PER_DEST) {
            pool.add(connection)
        } else {
            // Pool full, close connection
            try { connection.socket.close() } catch (e: Exception) {}
        }
    }
    
    /**
     * Remove connection from pool
     */
    private fun removeConnection(key: ConnectionKey, connection: TcpConnection) {
        connectionPool[key]?.remove(connection)
        try { connection.socket.close() } catch (e: Exception) {}
    }
    
    /**
     * Cleanup connections for a key
     */
    private fun cleanupConnection(key: ConnectionKey) {
        val pool = connectionPool.remove(key)
        pool?.forEach { 
            try { it.socket.close() } catch (e: Exception) {}
        }
        
        connectionLocks.remove(key)
        connectionCounts.remove(key)
    }
    
    /**
     * Cleanup all connections (on VPN stop)
     */
    fun cleanup() {
        Log.d(TAG, "Cleaning up all TCP connections")
        
        connectionPool.values.forEach { connections ->
            connections.forEach { 
                try { it.socket.close() } catch (e: Exception) {}
            }
        }
        
        connectionPool.clear()
        connectionLocks.clear()
        connectionCounts.clear()
    }
    
    /**
     * Periodic cleanup of expired connections
     */
    fun cleanupExpiredConnections() {
        val now = System.currentTimeMillis()
        val expiredKeys = mutableListOf<ConnectionKey>()
        
        connectionPool.forEach { (key, connections) ->
            val expired = connections.filter { 
                now - it.lastUsedTime > IDLE_TIMEOUT_MS 
            }
            
            expired.forEach { 
                try { it.socket.close() } catch (e: Exception) {}
                connections.remove(it)
            }
            
            if (connections.isEmpty()) {
                expiredKeys.add(key)
            }
        }
        
        expiredKeys.forEach { connectionPool.remove(it) }
        
        if (expiredKeys.isNotEmpty()) {
            Log.d(TAG, "Cleaned up ${expiredKeys.size} expired connection pools")
        }
    }
    
    // ==================== Helper Methods ====================
    
    private fun getDestIp(packet: ByteBuffer, ipHeaderLen: Int): String {
        val version = (packet.get(0).toInt() shr 4) and 0x0F
        
        return if (version == 4) {
            // IPv4: dst IP at offset 16-19
            val bytes = ByteArray(4)
            packet.position(16)
            packet.get(bytes)
            InetAddress.getByAddress(bytes).hostAddress ?: "0.0.0.0"
        } else {
            // IPv6: dst IP at offset 24-39
            val bytes = ByteArray(16)
            packet.position(24)
            packet.get(bytes)
            InetAddress.getByAddress(bytes).hostAddress ?: "::0"
        }
    }
    
    private fun getDestPort(packet: ByteBuffer, ipHeaderLen: Int): Int {
        // TCP destination port is at ipHeaderLen + 2 (after source port)
        return ((packet.get(ipHeaderLen + 2).toInt() and 0xFF) shl 8) or
               (packet.get(ipHeaderLen + 3).toInt() and 0xFF)
    }
    
    private fun extractTcpPayload(packet: ByteBuffer, ipHeaderLen: Int): ByteArray {
        val tcpHeaderLen = ((packet.get(ipHeaderLen + 12).toInt() shr 4) and 0x0F) * 4
        val payloadStart = ipHeaderLen + tcpHeaderLen
        val payloadLen = packet.limit() - payloadStart
        
        if (payloadLen <= 0) {
            return ByteArray(0)
        }
        
        val payload = ByteArray(payloadLen)
        packet.position(payloadStart)
        packet.get(payload)
        
        return payload
    }
    
    /**
     * Get stats untuk debugging
     */
    fun getStats(): Map<String, Any> {
        return mapOf(
            "activePools" to connectionPool.size,
            "totalConnections" to connectionPool.values.sumOf { it.size },
            "totalKeys" to connectionCounts.size
        )
    }
}
