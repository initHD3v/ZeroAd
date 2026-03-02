package com.hidayatfauzi6.zeroad.engine

import android.content.Context
import android.net.VpnService
import android.util.Log
import com.hidayatfauzi6.zeroad.SimpleDnsParser
import java.nio.ByteBuffer

/**
 * ZeroAd 2.0 - DNS Filter Engine
 * 
 * Multi-layer DNS filtering pipeline:
 * 1. User Whitelist → Forward to ISP
 * 2. Google Services → Forward to ISP
 * 3. DoH Domains → Block (NXDOMAIN)
 * 4. Game Soft-Whitelist → Forward to AdGuard
 * 5. System Whitelist → Forward to ISP
 * 6. Google Ads → Block (Sinkhole)
 * 7. Local Blocklist → Block (Sinkhole)
 * 8. AdGuard DNS → Forward (default)
 * 9. Fallback ISP → Forward (timeout)
 */
class DnsFilterEngine(
    private val context: Context,
    private val vpnService: VpnService,
    private val whitelistManager: WhitelistManager,
    private val smartBypassEngine: SmartBypassEngine,
    private val statisticsEngine: StatisticsEngine,
    private val dohBlocker: DohBlocker,
    private val adFilterEngine: AdFilterEngine
) {
    
    companion object {
        private const val TAG = "DnsFilterEngine"
    }
    
    // DNS forwarder
    private val dnsForwarder = DnsForwarder(context)

    /**
     * Handle DNS query dengan multi-layer filtering
     * 
     * @return DNS response payload
     */
    suspend fun handleDnsQuery(
        packet: ByteBuffer,
        ipHeaderLen: Int,
        appInfo: AppInfo
    ): ByteArray {
        val dnsInfo = SimpleDnsParser.parse(packet, ipHeaderLen)
            ?: return createErrorResponse(packet, ipHeaderLen)

        val domain = dnsInfo.domain.lowercase().trimEnd('.')
        val packageName = appInfo.packageName
        val appName = appInfo.appName
        val category = appInfo.category

        Log.d(TAG, "DNS Query: $domain | App: $packageName ($appName) | Category: $category")

        // ========== LAYER 1: User Whitelist ==========
        if (whitelistManager.isWhitelisted(packageName)) {
            Log.d(TAG, "Layer 1: User whitelist → Forward to ISP")
            statisticsEngine.recordRequest(domain, packageName, appName, false, "USER_WHITELIST")
            return forwardToIsp(dnsInfo.payload)
        }

        // ========== LAYER 2: Google Services ==========
        if (isGoogleService(domain, packageName)) {
            Log.d(TAG, "Layer 2: Google services → Forward to ISP")
            statisticsEngine.recordRequest(domain, packageName, appName, false, "GOOGLE_SERVICE")
            return forwardToIsp(dnsInfo.payload)
        }

        // ========== LAYER 3: DoH Blocking ==========
        if (dohBlocker.isDohDomain(domain)) {
            Log.d(TAG, "Layer 3: DoH domain detected → Block")
            statisticsEngine.recordRequest(domain, packageName, appName, true, "DOH_BLOCKED")
            return dohBlocker.createBlockedResponse(packet, ipHeaderLen)
        }

        // ========== LAYER 4: Game Soft-Whitelist ==========
        if (isGameWithAnalytics(packageName)) {
            Log.d(TAG, "Layer 4: Game with analytics → Forward to AdGuard")
            statisticsEngine.recordRequest(domain, packageName, appName, false, "GAME_SOFT_WHITELIST")
            return try {
                forwardToAdGuard(dnsInfo.payload)
            } catch (e: Exception) {
                Log.w(TAG, "AdGuard timeout, fallback to ISP")
                forwardToIsp(dnsInfo.payload)
            }
        }

        // ========== LAYER 5: System Whitelist ==========
        if (adFilterEngine.isWhitelisted(domain)) {
            Log.d(TAG, "Layer 5: System whitelist → Forward to ISP")
            statisticsEngine.recordRequest(domain, packageName, appName, false, "SYSTEM_WHITELIST")
            return forwardToIsp(dnsInfo.payload)
        }

        // ========== LAYER 6: Google Ads (Priority Block) ==========
        if (isGoogleAds(domain)) {
            Log.d(TAG, "Layer 6: Google Ads detected → Sinkhole")
            statisticsEngine.recordRequest(domain, packageName, appName, true, "GOOGLE_ADS_BLOCKED")
            return createSinkholeResponse(packet, ipHeaderLen)
        }

        // ========== LAYER 7: Local Blocklist ==========
        val shouldBlock = shouldBlockByCategory(domain, category)
        if (shouldBlock) {
            Log.d(TAG, "Layer 7: Local blocklist → Sinkhole")
            statisticsEngine.recordRequest(domain, packageName, appName, true, "LOCAL_BLOCKED")
            
            // Use sinkhole for games, NXDOMAIN for others
            return if (category == AppCategory.GAME || 
                      category == AppCategory.GAME_WITH_IAP ||
                      category == AppCategory.GAME_CASUAL) {
                createSinkholeResponse(packet, ipHeaderLen)
            } else {
                createNxDomainResponse(packet, ipHeaderLen)
            }
        }

        // ========== LAYER 8: AdGuard DNS (Default) ==========
        Log.d(TAG, "Layer 8: No filter match → Forward to AdGuard")
        statisticsEngine.recordRequest(domain, packageName, appName, false, "ADGUARD_FORWARD")

        return try {
            forwardToAdGuard(dnsInfo.payload)
        } catch (e: Exception) {
            // ========== LAYER 9: Fallback to ISP ==========
            Log.w(TAG, "Layer 9: AdGuard timeout → Fallback to ISP")
            forwardToIsp(dnsInfo.payload)
        }
    }

    // ==================== Forwarding Methods ====================
    
    private suspend fun forwardToIsp(payload: ByteArray): ByteArray {
        return dnsForwarder.forward(payload, DnsForwarder.DnsProvider.ISP)
    }
    
    private suspend fun forwardToAdGuard(payload: ByteArray): ByteArray {
        return dnsForwarder.forward(payload, DnsForwarder.DnsProvider.ADGUARD)
    }

    // ==================== Response Creation ====================

    private fun createSinkholeResponse(packet: ByteBuffer, ipHeaderLen: Int): ByteArray {
        return SimpleDnsParser.createNullIpResponse(packet, ipHeaderLen)
    }

    private fun createNxDomainResponse(packet: ByteBuffer, ipHeaderLen: Int): ByteArray {
        return SimpleDnsParser.createNxDomainResponse(packet, ipHeaderLen)
    }

    private fun createErrorResponse(packet: ByteBuffer, ipHeaderLen: Int): ByteArray {
        return SimpleDnsParser.createNxDomainResponse(packet, ipHeaderLen)
    }

    // ==================== Detection Methods ====================

    private fun isGoogleService(domain: String, packageName: String): Boolean {
        // Check Google package
        if (packageName.startsWith("com.google.") || 
            packageName.startsWith("com.android.")) {
            return true
        }
        
        // Check Google service domains
        val googleServicePatterns = listOf(
            "googleapis.com", "googleusercontent.com", "gstatic.com",
            "gvt1.com", "googlevideo.com", "ggpht.com",
            "play.googleapis.com", "android.googleapis.com",
            "firebaseio.com", "firebase.googleapis.com",
            "firestore.googleapis.com", "fcm.googleapis.com"
        )
        
        return googleServicePatterns.any { domain.contains(it) }
    }

    private fun isGameWithAnalytics(packageName: String): Boolean {
        // Games known to have hard analytics dependencies
        val hardAnalyticsGames = listOf(
            "twoheadedshark",  // TCO developer
            "tco",             // Tuning Club Online
            "tuningclub",
            "yandex"           // Yandex SDK games
        )
        
        val lowerPkg = packageName.lowercase()
        return hardAnalyticsGames.any { lowerPkg.contains(it) }
    }

    private fun isGoogleAds(domain: String): Boolean {
        val googleAdsPatterns = listOf(
            "googleads.g.doubleclick.net",
            "googleadservices.com",
            "googleadsserving.cn",
            "pagead2.googlesyndication.com",
            "pagead.google.com",
            "adservice.google.com",
            "adx.google.com",
            "ad.doubleclick.net",
            "2mdn.net"
        )
        
        return googleAdsPatterns.any { 
            domain == it || domain.endsWith(".$it") 
        }
    }

    private fun shouldBlockByCategory(domain: String, category: AppCategory): Boolean {
        // Games: Only block hard ads
        if (category == AppCategory.GAME || 
            category == AppCategory.GAME_WITH_IAP ||
            category == AppCategory.GAME_CASUAL) {
            return adFilterEngine.shouldBlock(domain, AppCategory.GAME)
        }
        
        // System/Google: Never block
        if (category == AppCategory.SYSTEM || 
            category == AppCategory.GOOGLE_SERVICES) {
            return false
        }
        
        // Others: Full filtering
        return adFilterEngine.shouldBlock(domain, AppCategory.GENERAL)
    }

    // ==================== Helper Classes ====================

    data class AppInfo(
        val packageName: String,
        val appName: String,
        val uid: Int,
        val category: AppCategory
    )
}
