package com.hidayatfauzi6.zeroad.engine

import android.util.Log
import com.hidayatfauzi6.zeroad.SimpleDnsParser
import java.nio.ByteBuffer

/**
 * ZeroAd 2.0 - DNS-over-HTTPS Blocker
 * 
 * Mencegah aplikasi bypass VPN DNS dengan cara:
 * - Block DoH provider domains
 * - Force apps menggunakan system DNS (yang kita filter)
 * - Return NXDOMAIN untuk DoH queries
 */
class DohBlocker {
    
    companion object {
        private const val TAG = "DohBlocker"
        
        // Daftar DoH provider yang umum
        val DOH_DOMAINS = hashSetOf(
            // Google DoH
            "dns.google",
            "dns.google.com",
            
            // Cloudflare DoH
            "cloudflare-dns.com",
            "mozilla.cloudflare-dns.com",
            
            // AdGuard DoH
            "dns.adguard.com",
            
            // Quad9 DoH
            "dns.quad9.net",
            
            // China DNS
            "doh.pub",
            "doh.360.cn",
            
            // CleanBrowsing
            "doh.cleanbrowsing.org",
            
            // Cisco Umbrella
            "doh.opendns.com",
            
            // NextDNS
            "dns.nextdns.io",
            
            // Control D
            "freedns.controld.com"
        )
    }
    
    /**
     * Check apakah domain adalah DoH provider
     */
    fun isDohDomain(domain: String): Boolean {
        val cleanDomain = domain.lowercase().trimEnd('.')
        
        // Exact match atau suffix match
        return DOH_DOMAINS.any { dohDomain ->
            cleanDomain == dohDomain || cleanDomain.endsWith(".$dohDomain")
        }
    }
    
    /**
     * Create NXDOMAIN response untuk DoH queries
     * Memberitahu app bahwa domain tidak ada
     */
    fun createBlockedResponse(request: ByteBuffer, ipHeaderLen: Int): ByteArray {
        Log.d(TAG, "Blocking DoH domain, returning NXDOMAIN")
        return SimpleDnsParser.createNxDomainResponse(request, ipHeaderLen)
    }
    
    /**
     * Get daftar DoH domains (untuk UI/display)
     */
    fun getDohDomains(): Set<String> {
        return DOH_DOMAINS.toSet()
    }
    
    /**
     * Add custom DoH domain ke blocklist
     */
    fun addDohDomain(domain: String) {
        DOH_DOMAINS.add(domain.lowercase().trimEnd('.'))
    }
}
