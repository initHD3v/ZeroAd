package com.hidayatfauzi6.zeroad.engine

import android.content.Context
import android.net.VpnService
import android.util.Log
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.util.concurrent.ConcurrentHashMap

/**
 * ZeroAd 2.0 - DNS Forwarder dengan Multi-Provider Support
 * 
 * Mendukung forwarding ke berbagai DNS provider:
 * - AdGuard DNS (primary)
 * - Cloudflare DNS
 * - Google DNS
 * - ISP DNS (fallback)
 */
class DnsForwarder(private val context: Context) {
    
    companion object {
        private const val TAG = "DnsForwarder"
        
        // DNS Server Addresses
        private val DNS_SERVERS = mapOf(
            DnsProvider.ADGUARD to "94.140.14.140",
            DnsProvider.ADGUARD_SECONDARY to "94.140.14.141",
            DnsProvider.CLOUDFLARE to "1.1.1.1",
            DnsProvider.CLOUDFLARE_SECONDARY to "1.0.0.1",
            DnsProvider.GOOGLE to "8.8.8.8",
            DnsProvider.GOOGLE_SECONDARY to "8.8.4.4"
        )
        
        // Timeout settings (ms)
        private const val DNS_TIMEOUT_MS = 3000L
        private const val MAX_RETRIES = 2
    }
    
    enum class DnsProvider {
        ADGUARD,
        ADGUARD_SECONDARY,
        CLOUDFLARE,
        CLOUDFLARE_SECONDARY,
        GOOGLE,
        GOOGLE_SECONDARY,
        ISP
    }
    
    // Cache untuk system DNS (hasil dari getSystemDns)
    private var cachedSystemDns: String? = null
    private var lastDnsCacheTime = 0L
    private val DNS_CACHE_TTL = 60000L // 1 menit
    
    /**
     * Forward DNS query ke provider yang ditentukan
     * 
     * @param payload DNS query payload
     * @param provider DNS provider tujuan
     * @return DNS response payload
     * @throws SocketTimeoutException jika timeout
     */
    suspend fun forward(
        payload: ByteArray,
        provider: DnsProvider
    ): ByteArray {
        val dnsServer = when (provider) {
            DnsProvider.ISP -> getSystemDns() ?: "8.8.8.8"
            else -> DNS_SERVERS[provider] ?: "8.8.8.8"
        }
        
        var lastException: Exception? = null
        
        // Retry logic
        for (attempt in 1..MAX_RETRIES) {
            try {
                return forwardWithTimeout(payload, dnsServer)
            } catch (e: SocketTimeoutException) {
                lastException = e
                Log.w(TAG, "DNS timeout to $dnsServer (attempt $attempt/$MAX_RETRIES)")
            } catch (e: Exception) {
                lastException = e
                Log.e(TAG, "DNS forward error to $dnsServer", e)
            }
        }
        
        // Semua retry gagal
        throw lastException ?: SocketTimeoutException("DNS forward failed after $MAX_RETRIES attempts")
    }
    
    /**
     * Forward DNS dengan timeout
     */
    private suspend fun forwardWithTimeout(
        payload: ByteArray,
        dnsServer: String
    ): ByteArray {
        var socket: DatagramSocket? = null
        
        try {
            socket = DatagramSocket()
            
            // Protect socket dari VPN routing
            protect(socket)
            
            // Set timeout
            socket.soTimeout = DNS_TIMEOUT_MS.toInt()
            
            // Prepare outgoing packet
            val out = DatagramPacket(
                payload,
                payload.size,
                InetAddress.getByName(dnsServer),
                53
            )
            
            // Send query
            socket.send(out)
            
            // Prepare incoming packet
            val inData = ByteArray(1500)
            val inPacket = DatagramPacket(inData, inData.size)
            
            // Receive response (blocking dengan timeout)
            socket.receive(inPacket)
            
            Log.d(TAG, "DNS success: $dnsServer (${inPacket.length} bytes)")
            
            return inData.copyOf(inPacket.length)
            
        } finally {
            try { socket?.close() } catch (e: Exception) {}
        }
    }
    
    /**
     * Get system DNS dengan caching
     */
    private fun getSystemDns(): String? {
        val now = System.currentTimeMillis()
        
        // Return cached jika belum expired
        if (cachedSystemDns != null && 
            now - lastDnsCacheTime < DNS_CACHE_TTL) {
            return cachedSystemDns
        }
        
        // Refresh cache
        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) 
                     as android.net.ConnectivityManager
            
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                val activeNetwork = cm.activeNetwork
                val lp = cm.getLinkProperties(activeNetwork)
                val dnsServers = lp?.dnsServers
                
                // Cari IPv4 DNS (prioritas)
                val ipv4Dns = dnsServers?.firstOrNull { 
                    it.hostAddress?.contains(":") == false 
                }
                
                // Fallback ke IPv6 jika tidak ada IPv4
                val dns = ipv4Dns ?: dnsServers?.firstOrNull()
                
                cachedSystemDns = dns?.hostAddress
                
                // Filter out private/VPN DNS
                if (cachedSystemDns == "10.0.0.2" || 
                    cachedSystemDns == "fd00::2" ||
                    cachedSystemDns == "127.0.0.1") {
                    cachedSystemDns = "8.8.8.8"
                }
                
                lastDnsCacheTime = now
                
                Log.d(TAG, "System DNS: $cachedSystemDns")
                
                return cachedSystemDns
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting system DNS", e)
        }
        
        return "8.8.8.8"
    }
    
    /**
     * Protect socket dari VPN routing
     */
    private fun protect(socket: DatagramSocket) {
        try {
            if (context is VpnService) {
                (context as VpnService).protect(socket)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error protecting socket", e)
        }
    }
    
    /**
     * Clear DNS cache
     */
    fun clearCache() {
        cachedSystemDns = null
        lastDnsCacheTime = 0
    }
}
