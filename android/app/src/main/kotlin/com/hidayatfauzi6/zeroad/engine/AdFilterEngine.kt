package com.hidayatfauzi6.zeroad.engine

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.ConcurrentHashMap

@Serializable
data class BypassListContainer(val bypass_packages: List<String>)

enum class AppCategory {
    GAME,
    SYSTEM,
    GENERAL
}

class AdFilterEngine(private val context: Context) {
    private val blockedDomains = HashSet<String>()
    private val hardAdsDomains = HashSet<String>() // Khusus untuk kategori GAME
    private val autoWhitelistedDomains = HashSet<String>()
    private val autonomousEssentialApps = HashSet<String>()
    private val userWhitelistedApps = HashSet<String>()
    private val packageKeywordMap = ConcurrentHashMap<String, String>()
    
    // Domain DoH yang harus diblokir agar Chrome fallback ke DNS standar secara instan
    private val DOH_DOMAINS = hashSetOf(
        "dns.google", "dns.google.com", "cloudflare-dns.com", 
        "doh.pub", "doh.cleanbrowsing.org", "dns.adguard.com"
    )

    // Whitelist Infrastruktur Kritis (Subdomain Specific)
    private val SYSTEM_WHITELIST = hashSetOf(
        "google.com", "google.co.id", "googleapis.com", "gstatic.com", "googleusercontent.com",
        "ggpht.com", "gvt1.com", "googlevideo.com", "googletagmanager.com",
        "firebaseio.com", "firebaseinstallations.googleapis.com", "firebase.io",
        "oauthaccountmanager.googleapis.com", "identitytoolkit.googleapis.com",
        "play.googleapis.com", "play-fe.googleapis.com", "android.clients.google.com",
        "payments.google.com", "checkout.google.com", "billing.google.com", "google-analytics.com",
        "tuningclub.app", "twoheadedshark.com", "exitgames.com", "photonengine.io", "photonengine.com",
        "apple.com", "itunes.apple.com", "icloud.com", "mzstatic.com",
        "unity3d.com", "unity.com", "cloud.unity3d.com", "config.unity3d.com", 
        "epicgames.com", "unrealengine.com", "akamaihd.net", "cloudfront.net",
        "facebook.net", "fbcdn.net", "facebook.com", "adjust.com", "appsflyer.com",
        "hoyoverse.com", "mihoyo.com", "starrails.com", "supercell.com", "moonton.com",
        "roblox.com", "garena.com", "xboxlive.com", "playfab.com", "gameservices.google.com",
        "azureedge.net", "akamaiedge.net", "discordapp.com", "discord.gg",
        "shopee.co.id", "shopee.com", "shopeemobile.com", "tokopedia.com", "tokopedia.net",
        "lazada.co.id", "lazada.com", "alicdn.com", "blibli.com", "bukalapak.com", "tiktokcdn.com",
        "dana.id", "ovo.id", "klikbca.com", "bankmandiri.co.id", "bni.co.id", "bri.co.id",
        "jenius.com", "jago.com", "neobank.co.id", "paypal.com", "wise.com"
    )

    fun loadBlocklists(prefs: SharedPreferences) {
        try {
            val newBlocked = HashSet<String>()
            val newHardAds = HashSet<String>()
            
            context.assets.open("hosts.txt").bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    val domain = cleanDomain(line)
                    if (domain.isNotEmpty()) {
                        newBlocked.add(domain)
                        // Identifikasi Hard-Ads untuk Game (Pola lebih ketat agar tidak salah blokir)
                        if (isHardAdPattern(domain)) {
                            newHardAds.add(domain)
                        }
                    }
                }
            }
            
            val dynamicPath = prefs.getString("dynamic_blocklist_path", null)
            if (dynamicPath != null) {
                val dynamicFile = File(dynamicPath)
                if (dynamicFile.exists()) {
                    dynamicFile.bufferedReader().useLines { it.forEach { d -> 
                        val cd = cleanDomain(d)
                        if (cd.isNotEmpty()) {
                            newBlocked.add(cd)
                            if (isHardAdPattern(cd)) newHardAds.add(cd)
                        }
                    } }
                }
            }
            
            synchronized(blockedDomains) {
                blockedDomains.clear()
                blockedDomains.addAll(newBlocked)
            }
            synchronized(hardAdsDomains) {
                hardAdsDomains.clear()
                hardAdsDomains.addAll(newHardAds)
            }
            
            buildKeywordMap()
            
            val autoWhitePath = prefs.getString("auto_whitelist_path", null)
            if (autoWhitePath != null) {
                val whiteFile = File(autoWhitePath)
                if (whiteFile.exists()) {
                    val newWhite = HashSet<String>()
                    whiteFile.bufferedReader().useLines { it.forEach { d -> 
                        val cd = cleanDomain(d)
                        if (cd.isNotEmpty()) newWhite.add(cd)
                    } }
                    synchronized(autoWhitelistedDomains) {
                        autoWhitelistedDomains.clear()
                        autoWhitelistedDomains.addAll(newWhite)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("AdFilterEngine", "Error loading blocklists", e)
        }
    }

    private fun cleanDomain(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty() || trimmed.startsWith("#")) return ""
        val parts = trimmed.split("\\s+".toRegex())
        val domain = if (parts.size > 1) parts[1] else parts[0]
        return domain.lowercase().trimEnd('.')
    }

    private fun isHardAdPattern(domain: String): Boolean {
        // Pola yang HANYA digunakan oleh server iklan (Sangat Spesifik)
        // Kita hindari memblokir domain 'config' atau 'settings' dari Unity/AdMob karena sering berisi fungsionalitas lain.
        val hardAdKeywords = listOf(
            "unityads.unity3d.com/v2/ads", "ads.prd.ie.unity3d.com", 
            "googleads.g.doubleclick.net", "pagead2.googlesyndication.com",
            "admob.google.com", "applovin.com/ad", "a.applovin.com",
            "ads-pau.unity3d.com"
        )
        return hardAdKeywords.any { domain.contains(it) }
    }

    private fun buildKeywordMap() {
        try {
            val apps = context.packageManager.getInstalledPackages(0)
            val newMap = mutableMapOf<String, String>()
            for (app in apps) {
                val pkg = app.packageName
                val parts = pkg.split(".")
                for (part in parts) {
                    if (part.length > 3 && part !in listOf("com", "android", "google", "apps", "mobile")) {
                        newMap[part.lowercase()] = pkg
                    }
                }
            }
            packageKeywordMap.clear()
            packageKeywordMap.putAll(newMap)
        } catch (e: Exception) {}
    }

    fun findPackageFromDomain(domain: String): String? {
        if (packageKeywordMap.isEmpty()) return null
        val cleanDomain = domain.lowercase()
        val parts = cleanDomain.split(".").filter { it.length > 3 }
        for (p in parts) { packageKeywordMap[p]?.let { return it } }
        return null
    }

    fun updateEssentialApps(apps: List<String>) {
        synchronized(autonomousEssentialApps) {
            autonomousEssentialApps.clear()
            autonomousEssentialApps.addAll(apps)
        }
    }

    fun updateUserWhitelist(apps: Set<String>) {
        synchronized(userWhitelistedApps) {
            userWhitelistedApps.clear()
            userWhitelistedApps.addAll(apps)
        }
    }

    fun getEssentialApps(): Set<String> = autonomousEssentialApps

    fun loadSystemBypassList(): List<String> {
        return try {
            val jsonString = context.assets.open("system_bypass.json").bufferedReader().use { it.readText() }
            val container = Json.decodeFromString<BypassListContainer>(jsonString)
            container.bypass_packages
        } catch (e: Exception) { listOf() }
    }

    fun shouldBypass(pkg: String): Boolean {
        return autonomousEssentialApps.contains(pkg) || userWhitelistedApps.contains(pkg) ||
               pkg.startsWith("com.android.") || pkg.startsWith("com.google.android.") ||
               pkg == "com.hidayatfauzi6.zeroad"
    }

    /**
     * Smart Filtering Logic
     * @param domain Domain yang diminta
     * @param category Kategori aplikasi pengirim
     */
    fun shouldBlock(domain: String, category: AppCategory?): Boolean {
        val ld = domain.lowercase().trimEnd('.')
        
        // 1. Fast Track: DoH Blocking (Agar Chrome fallback)
        if (DOH_DOMAINS.contains(ld)) return true

        // 2. Fast Track: Critical System Whitelist (Recursive Suffix Match)
        if (isWhitelisted(ld)) return false

        // 3. Contextual Filtering
        return when (category) {
            AppCategory.GAME -> {
                // Untuk Game: Hanya blokir jika ada di daftar Hard-Ads
                matchRecursive(ld, hardAdsDomains)
            }
            AppCategory.SYSTEM, null -> {
                // Untuk System atau Unknown: Jangan blokir (PASS ON UNCERTAINTY)
                false
            }
            AppCategory.GENERAL -> {
                // Untuk Browser/Lainnya: Blokir Agresif
                matchRecursive(ld, blockedDomains)
            }
        }
    }

    fun isWhitelisted(domain: String): Boolean {
        // Cek User Whitelist & System Whitelist menggunakan Suffix Matching
        val ld = domain.lowercase().trimEnd('.')
        if (matchRecursive(ld, SYSTEM_WHITELIST)) return true
        if (matchRecursive(ld, autoWhitelistedDomains)) return true
        return false
    }

    /**
     * Recursive Suffix Matcher
     * Contoh: ads.google.com -> cek ads.google.com, lalu google.com, lalu com.
     */
    private fun matchRecursive(domain: String, set: HashSet<String>): Boolean {
        if (set.isEmpty()) return false
        if (set.contains(domain)) return true
        
        var current = domain
        while (current.contains(".")) {
            current = current.substringAfter(".")
            if (set.contains(current)) return true
        }
        return false
    }

    // Deprecated: Diganti oleh shouldBlock
    fun isAd(domain: String): Boolean = shouldBlock(domain, AppCategory.GENERAL)
}