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

class AdFilterEngine(private val context: Context) {
    private val blockedDomains = HashSet<String>()
    private val autoWhitelistedDomains = HashSet<String>()
    private val autonomousEssentialApps = HashSet<String>()
    private val userWhitelistedApps = HashSet<String>()
    private val packageKeywordMap = ConcurrentHashMap<String, String>()
    
    // Domain DoH yang harus diblokir agar Chrome fallback ke DNS standar secara instan
    private val DOH_DOMAINS = hashSetOf(
        "dns.google", "dns.google.com", "cloudflare-dns.com", 
        "doh.pub", "doh.cleanbrowsing.org", "dns.adguard.com"
    )

    private val SYSTEM_WHITELIST = hashSetOf(
        "google.com", "googleapis.com", "gstatic.com", "googleusercontent.com",
        "whatsapp.net", "whatsapp.com", "facebook.com", "fbcdn.net",
        "instagram.com", "android.com", "play.google.com", "drive.google.com",
        "github.com", "githubusercontent.com", "adjust.com", "adjust.world", "adjust.in"
    )

    fun loadBlocklists(prefs: SharedPreferences) {
        try {
            val newBlocked = HashSet<String>()
            context.assets.open("hosts.txt").bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    val parts = line.trim().split("\\s+".toRegex())
                    val domain = if (parts.size > 1) parts[1] else parts[0]
                    if (domain.isNotEmpty() && !domain.startsWith("#")) newBlocked.add(domain.lowercase())
                }
            }
            val dynamicPath = prefs.getString("dynamic_blocklist_path", null)
            if (dynamicPath != null) {
                val dynamicFile = File(dynamicPath)
                if (dynamicFile.exists()) {
                    dynamicFile.bufferedReader().useLines { it.forEach { d -> if (d.isNotEmpty()) newBlocked.add(d.lowercase()) } }
                }
            }
            synchronized(blockedDomains) {
                blockedDomains.clear()
                blockedDomains.addAll(newBlocked)
            }
            buildKeywordMap()
            val autoWhitePath = prefs.getString("auto_whitelist_path", null)
            if (autoWhitePath != null) {
                val whiteFile = File(autoWhitePath)
                if (whiteFile.exists()) {
                    val newWhite = HashSet<String>()
                    whiteFile.bufferedReader().useLines { it.forEach { d -> if (d.isNotEmpty()) newWhite.add(d.lowercase()) } }
                    synchronized(autoWhitelistedDomains) {
                        autoWhitelistedDomains.clear()
                        autoWhitelistedDomains.addAll(newWhite)
                    }
                }
            }
        } catch (e: Exception) {}
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
        val parts = domain.lowercase().split(".").filter { it.length > 3 }
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

    fun isAd(domain: String): Boolean {
        val ld = domain.lowercase()
        // Cerdas: Jika Chrome mencoba DoH, blokir domain resolver-nya agar dia fallback ke UDP
        if (DOH_DOMAINS.contains(ld)) return true
        
        if (SYSTEM_WHITELIST.any { ld == it || ld.endsWith(".$it") }) return false
        if (autoWhitelistedDomains.contains(ld)) return false
        synchronized(blockedDomains) {
            if (blockedDomains.contains(ld)) return true
            var p = ld
            while (p.contains(".")) {
                p = p.substringAfter(".")
                if (blockedDomains.contains(p)) return true
            }
        }
        return false
    }
}