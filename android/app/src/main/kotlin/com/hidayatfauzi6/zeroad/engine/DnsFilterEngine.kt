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
 * 1. User Whitelist → Forward to AdGuard
 * 2. Google Services → Forward to AdGuard
 * 3. DoH Domains → Block (NXDOMAIN)
 * 4. Games → Forward to AdGuard (no local filtering — prevents game-breakage)
 * 5. System Apps → Forward (zero filtering)
 * 6. System Whitelist (domain) → Forward
 * 7. Google Ads (hard ads) → Block (Sinkhole)
 * 8. Browser Apps → check local blocklist → Forward/Block
 * 9. All other apps → Forward to AdGuard (AdGuard handles smart filtering)
 * 10. ISP Fallback → Forward (timeout)
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

    // DNS forwarder — shared socket pool
    private val dnsForwarder = DnsForwarder(vpnService)

    /**
     * Handle DNS query dengan multi-layer filtering
     *
     * @return DNS response payload (full IP packet untuk block, DNS payload untuk forward)
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

        Log.d(TAG, "DNS: $domain | App: $packageName | Category: $category")

        // ========== LAYER 1: User Whitelist ==========
        if (whitelistManager.isWhitelisted(packageName)) {
            Log.d(TAG, "Layer 1: User whitelist → Forward")
            statisticsEngine.recordRequest(domain, packageName, appName, false, "USER_WHITELIST")
            return forwardToAdGuard(dnsInfo.payload)
        }

        // ========== LAYER 2: Google Services ==========
        if (isGoogleService(domain, packageName)) {
            Log.d(TAG, "Layer 2: Google services → Forward")
            statisticsEngine.recordRequest(domain, packageName, appName, false, "GOOGLE_SERVICE")
            return forwardToAdGuard(dnsInfo.payload)
        }

        // ========== LAYER 3: DoH Blocking ==========
        if (dohBlocker.isDohDomain(domain)) {
            Log.d(TAG, "Layer 3: DoH domain detected → Block")
            statisticsEngine.recordRequest(domain, packageName, appName, true, "DOH_BLOCKED")
            return dohBlocker.createBlockedResponse(packet, ipHeaderLen)
        }

        // ========== LAYER 4: Games ==========
        // Semua game → Forward ke AdGuard DNS, TANPA filter lokal agresif.
        // AdGuard DNS secara cerdas memblokir iklan tanpa merusak fungsi game.
        if (category == AppCategory.GAME ||
            category == AppCategory.GAME_WITH_IAP ||
            category == AppCategory.GAME_CASUAL ||
            isGameWithAnalytics(packageName)) {
            Log.d(TAG, "Layer 4: Game → Forward to AdGuard (safe)")
            statisticsEngine.recordRequest(domain, packageName, appName, false, "ADGUARD_GAME_PASS")
            return try {
                forwardToAdGuard(dnsInfo.payload)
            } catch (e: Exception) {
                Log.w(TAG, "AdGuard timeout for game, fallback to ISP")
                forwardToIsp(dnsInfo.payload)
            }
        }

        // ========== LAYER 5: System Apps ==========
        // System & Google apps → zero filtering, forward langsung
        if (category == AppCategory.SYSTEM || category == AppCategory.GOOGLE_SERVICES) {
            Log.d(TAG, "Layer 5: System app → Forward (zero filter)")
            statisticsEngine.recordRequest(domain, packageName, appName, false, "SYSTEM_PASS")
            return forwardToAdGuard(dnsInfo.payload)
        }

        // ========== LAYER 6: Domain System Whitelist ==========
        if (adFilterEngine.isWhitelisted(domain)) {
            Log.d(TAG, "Layer 6: Domain whitelisted → Forward")
            statisticsEngine.recordRequest(domain, packageName, appName, false, "SYSTEM_WHITELIST")
            return forwardToAdGuard(dnsInfo.payload)
        }

        // ========== LAYER 7: Google Ads (Hard Block) ==========
        // Domain ini PASTI iklan — blokir di semua kategori
        if (isGoogleAds(domain)) {
            Log.d(TAG, "Layer 7: Google Ads → Block")
            statisticsEngine.recordRequest(domain, packageName, appName, true, "GOOGLE_ADS_BLOCKED")
            return createSinkholeResponse(packet, ipHeaderLen)
        }

        // ========== LAYER 8: Browser (Agresif) ==========
        // Browser bisa handle NXDOMAIN gracefully — terapkan full blocklist
        if (category == AppCategory.BROWSER) {
            if (adFilterEngine.shouldBlock(domain, AppCategory.GENERAL)) {
                Log.d(TAG, "Layer 8: Browser blocklist match → Block")
                statisticsEngine.recordRequest(domain, packageName, appName, true, "BROWSER_BLOCKED")
                return createNxDomainResponse(packet, ipHeaderLen)
            }
            Log.d(TAG, "Layer 8: Browser → Forward to AdGuard")
            statisticsEngine.recordRequest(domain, packageName, appName, false, "ADGUARD_FORWARD")
            return try {
                forwardToAdGuard(dnsInfo.payload)
            } catch (e: Exception) {
                Log.w(TAG, "AdGuard timeout for browser, fallback to ISP")
                forwardToIsp(dnsInfo.payload)
            }
        }

        // ========== LAYER 9: All Other Apps (Default) ==========
        // Kategori lain (BANKING, E_COMMERCE, SOCIAL_MEDIA, STREAMING, GENERAL):
        // Gunakan AdGuard DNS sebagai filter SMART — bukan blocklist lokal agresif.
        // AdGuard DNS memblokir iklan tapi tetap mengizinkan fungsi penting.
        Log.d(TAG, "Layer 9: Default → Forward to AdGuard")
        statisticsEngine.recordRequest(domain, packageName, appName, false, "ADGUARD_FORWARD")

        return try {
            forwardToAdGuard(dnsInfo.payload)
        } catch (e: Exception) {
            // ========== LAYER 10: ISP Fallback ==========
            Log.w(TAG, "AdGuard timeout → Fallback to ISP")
            forwardToIsp(dnsInfo.payload)
        }
    }

    // ==================== Forwarding Methods ====================

    private suspend fun forwardToIsp(payload: ByteArray): ByteArray {
        return try {
            dnsForwarder.forward(payload, DnsForwarder.DnsProvider.ADGUARD)
        } catch (e: Exception) {
            Log.w(TAG, "AdGuard failed, using ISP fallback")
            dnsForwarder.forward(payload, DnsForwarder.DnsProvider.ISP)
        }
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
        if (packageName.startsWith("com.google.") ||
            packageName.startsWith("com.android.")) {
            return true
        }

        val googleServicePatterns = listOf(
            "googleapis.com", "googleusercontent.com", "gstatic.com",
            "gvt1.com", "googlevideo.com", "ggpht.com",
            "play.googleapis.com", "android.googleapis.com",
            "firebaseio.com", "firebase.googleapis.com",
            "firestore.googleapis.com", "fcm.googleapis.com",
            "playbilling.googleapis.com", "gameservices.google.com",
            "identitytoolkit.googleapis.com",
        )

        return googleServicePatterns.any { domain.contains(it) }
    }

    private fun isGameWithAnalytics(packageName: String): Boolean {
        val hardAnalyticsGames = listOf(
            "twoheadedshark", "tco", "tuningclub", "yandex"
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

    /**
     * Cleanup resources (close sockets, etc.)
     * Call when VPN service stops
     */
    fun cleanup() {
        dnsForwarder.closeAll()
        Log.d(TAG, "DnsFilterEngine cleaned up")
    }

    // ==================== Helper Classes ====================

    data class AppInfo(
        val packageName: String,
        val appName: String,
        val uid: Int,
        val category: AppCategory
    )
}