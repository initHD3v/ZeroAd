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
 *
 * Menggunakan shared socket pool untuk setiap provider DNS
 * agar tidak perlu buat-tutup socket per query.
 */
class DnsForwarder(private val context: Context) {

    companion object {
        private const val TAG = "DnsForwarder"

        // DNS Server Addresses
        private val DNS_SERVERS = mapOf(
            DnsProvider.ADGUARD to "94.140.14.14",
            DnsProvider.ADGUARD_SECONDARY to "94.140.15.15",
            DnsProvider.CLOUDFLARE to "1.1.1.1",
            DnsProvider.CLOUDFLARE_SECONDARY to "1.0.0.1",
            DnsProvider.GOOGLE to "8.8.8.8",
            DnsProvider.GOOGLE_SECONDARY to "8.8.4.4"
        )

        // Timeout settings (ms)
        private const val DNS_TIMEOUT_MS = 3000L
        private const val MAX_RETRIES = 2

        // Shared socket reuse timeout (ms)
        private const val SOCKET_IDLE_TIMEOUT_MS = 30000L
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

    // Shared sockets per DNS server address
    private val sharedSockets = ConcurrentHashMap<String, DatagramSocket>()

    // Per-server InetAddress cache
    private val addressCache = ConcurrentHashMap<String, InetAddress>()

    /**
     * Forward DNS query ke provider yang ditentukan
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

        for (attempt in 1..MAX_RETRIES) {
            try {
                return forwardWithTimeout(payload, dnsServer)
            } catch (e: SocketTimeoutException) {
                lastException = e
                Log.w(TAG, "DNS timeout to $dnsServer (attempt $attempt/$MAX_RETRIES)")
                // Timeout -> reconnect socket for next attempt
                closeSocket(dnsServer)
            } catch (e: Exception) {
                lastException = e
                Log.e(TAG, "DNS forward error to $dnsServer", e)
                closeSocket(dnsServer)
            }
        }

        throw lastException ?: SocketTimeoutException("DNS forward failed after $MAX_RETRIES attempts")
    }

    /**
     * Forward DNS dengan shared socket (reuse per server)
     */
    private suspend fun forwardWithTimeout(
        payload: ByteArray,
        dnsServer: String
    ): ByteArray {
        val socket = getOrCreateSocket(dnsServer)
        val addr = getOrCreateAddress(dnsServer)

        // Prepare outgoing packet
        val out = DatagramPacket(
            payload,
            payload.size,
            addr,
            53
        )

        socket.send(out)

        // Prepare incoming packet
        val inData = ByteArray(1500)
        val inPacket = DatagramPacket(inData, inData.size)

        socket.receive(inPacket)

        Log.d(TAG, "DNS success: $dnsServer (${inPacket.length} bytes)")

        return inData.copyOf(inPacket.length)
    }

    /**
     * Mendapatkan atau membuat shared socket untuk server tertentu
     */
    private fun getOrCreateSocket(dnsServer: String): DatagramSocket {
        val existing = sharedSockets[dnsServer]
        if (existing != null && !existing.isClosed) {
            // Refresh timeout via check — re-set soTimeout reassures usability
            try {
                existing.soTimeout = DNS_TIMEOUT_MS.toInt()
                return existing
            } catch (e: Exception) {
                // Socket is dead, remove and create new
                closeSocket(dnsServer)
            }
        }

        // Create new socket
        val socket = DatagramSocket()

        // Protect dari VPN routing
        protect(socket)

        socket.soTimeout = DNS_TIMEOUT_MS.toInt()

        sharedSockets[dnsServer] = socket
        Log.d(TAG, "New shared socket for $dnsServer")

        return socket
    }

    /**
     * Dapatkan InetAddress dengan cache
     */
    private fun getOrCreateAddress(dnsServer: String): InetAddress {
        return addressCache.getOrPut(dnsServer) {
            InetAddress.getByName(dnsServer)
        }
    }

    /**
     * Close socket untuk server tertentu
     */
    private fun closeSocket(dnsServer: String) {
        try {
            sharedSockets.remove(dnsServer)?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing socket for $dnsServer", e)
        }
    }

    /**
     * Close semua shared sockets (panggil saat VPN stop)
     */
    fun closeAll() {
        Log.d(TAG, "Closing all shared DNS sockets")
        sharedSockets.values.forEach { socket ->
            try { socket.close() } catch (e: Exception) {}
        }
        sharedSockets.clear()
        addressCache.clear()
    }

    /**
     * Get system DNS dengan caching
     */
    private fun getSystemDns(): String? {
        val now = System.currentTimeMillis()

        if (cachedSystemDns != null &&
            now - lastDnsCacheTime < DNS_CACHE_TTL) {
            return cachedSystemDns
        }

        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE)
                     as android.net.ConnectivityManager

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                val activeNetwork = cm.activeNetwork
                val lp = cm.getLinkProperties(activeNetwork)
                val dnsServers = lp?.dnsServers

                val ipv4Dns = dnsServers?.firstOrNull {
                    it.hostAddress?.contains(":") == false
                }

                val dns = ipv4Dns ?: dnsServers?.firstOrNull()

                cachedSystemDns = dns?.hostAddress

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