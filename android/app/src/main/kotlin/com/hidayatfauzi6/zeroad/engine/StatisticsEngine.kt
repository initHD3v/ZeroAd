package com.hidayatfauzi6.zeroad.engine

import android.util.Log
import java.util.concurrent.ConcurrentHashMap

/**
 * ZeroAd 2.0 - Statistics Engine
 * 
 * Menyediakan real-time statistics untuk:
 * - Total requests/blocked
 * - Top blocked domains
 * - Per-app statistics
 * - Hourly/daily trends
 */
class StatisticsEngine {
    
    companion object {
        private const val TAG = "StatisticsEngine"
        
        // Keep stats for last 24 hours
        private const val MAX_HOURS = 24
        
        // Max domains to track per hour
        private const val MAX_DOMAINS_PER_HOUR = 1000
        
        // Max apps to track
        private const val MAX_APPS = 500
    }
    
    data class HourlyStats(
        val hour: Long,  // Unix hour (ms / 3600000)
        var totalRequests: Int = 0,
        var blockedRequests: Int = 0,
        val blockedDomains: MutableMap<String, Int> = mutableMapOf(),
        val perAppStats: MutableMap<String, AppStats> = mutableMapOf()
    )
    
    data class AppStats(
        val packageName: String,
        var totalRequests: Int = 0,
        var blockedRequests: Int = 0,
        val topBlockedDomains: MutableMap<String, Int> = mutableMapOf()
    )
    
    // Stats per hour
    private val hourlyStats = ConcurrentHashMap<Long, HourlyStats>()
    
    // Global app stats (accumulated)
    private val appStats = ConcurrentHashMap<String, AppStats>()
    
    // Global domain stats
    private val globalBlockedDomains = ConcurrentHashMap<String, Int>()
    
    /**
     * Record DNS request
     */
    fun recordRequest(
        domain: String,
        packageName: String,
        appName: String,
        wasBlocked: Boolean,
        blockReason: String = ""
    ) {
        val hour = System.currentTimeMillis() / 3600000
        
        // Update hourly stats
        val hourStats = hourlyStats.getOrPut(hour) { HourlyStats(hour) }
        
        synchronized(hourStats) {
            hourStats.totalRequests++
            
            if (wasBlocked) {
                hourStats.blockedRequests++
                
                // Update blocked domains (with limit)
                if (hourStats.blockedDomains.size < MAX_DOMAINS_PER_HOUR) {
                    hourStats.blockedDomains[domain] = 
                        (hourStats.blockedDomains[domain] ?: 0) + 1
                }
                
                // Update global blocked domains
                globalBlockedDomains[domain] = 
                    (globalBlockedDomains[domain] ?: 0) + 1
            }
            
            // Update per-app stats for this hour
            val appStats = hourStats.perAppStats.getOrPut(packageName) {
                AppStats(packageName)
            }
            
            synchronized(appStats) {
                appStats.totalRequests++
                
                if (wasBlocked) {
                    appStats.blockedRequests++
                    
                    // Track top blocked domains per app
                    if (appStats.topBlockedDomains.size < 100) {
                        appStats.topBlockedDomains[domain] = 
                            (appStats.topBlockedDomains[domain] ?: 0) + 1
                    }
                }
            }
        }
        
        // Update global app stats
        val globalAppStats = appStats.getOrPut(packageName) {
            AppStats(packageName)
        }
        
        synchronized(globalAppStats) {
            globalAppStats.totalRequests++
            
            if (wasBlocked) {
                globalAppStats.blockedRequests++
            }
        }
        
        // Cleanup old stats
        cleanupOldStats()
    }
    
    /**
     * Get top blocked domains (global)
     */
    fun getTopBlockedDomains(limit: Int = 10): List<Pair<String, Int>> {
        return globalBlockedDomains.entries
            .sortedByDescending { it.value }
            .take(limit)
            .map { it.key to it.value }
    }
    
    /**
     * Get top blocked domains (last hour)
     */
    fun getTopBlockedDomainsLastHour(limit: Int = 10): List<Pair<String, Int>> {
        val currentHour = System.currentTimeMillis() / 3600000
        val hourStats = hourlyStats[currentHour] ?: return emptyList()
        
        return hourStats.blockedDomains.entries
            .sortedByDescending { it.value }
            .take(limit)
            .map { it.key to it.value }
    }
    
    /**
     * Get stats untuk app tertentu
     */
    fun getAppStats(packageName: String): AppStats? {
        return appStats[packageName]
    }
    
    /**
     * Get semua app stats
     */
    fun getAllAppStats(): Map<String, AppStats> {
        return appStats.toMap()
    }
    
    /**
     * Get total requests (all time tracked)
     */
    fun getTotalRequests(): Int {
        return hourlyStats.values.sumOf { it.totalRequests }
    }
    
    /**
     * Get total blocked requests
     */
    fun getTotalBlocked(): Int {
        return hourlyStats.values.sumOf { it.blockedRequests }
    }
    
    /**
     * Get total allowed requests
     */
    fun getTotalAllowed(): Int {
        return getTotalRequests() - getTotalBlocked()
    }
    
    /**
     * Get blocking rate (0.0 - 1.0)
     */
    fun getBlockingRate(): Double {
        val total = getTotalRequests()
        val blocked = getTotalBlocked()
        return if (total > 0) blocked.toDouble() / total else 0.0
    }
    
    /**
     * Get stats summary untuk UI
     */
    fun getSummary(): StatsSummary {
        return StatsSummary(
            totalRequests = getTotalRequests(),
            totalBlocked = getTotalBlocked(),
            totalAllowed = getTotalAllowed(),
            blockingRate = getBlockingRate(),
            uniqueDomainsBlocked = globalBlockedDomains.size,
            uniqueAppsTracked = appStats.size
        )
    }
    
    data class StatsSummary(
        val totalRequests: Int,
        val totalBlocked: Int,
        val totalAllowed: Int,
        val blockingRate: Double,
        val uniqueDomainsBlocked: Int,
        val uniqueAppsTracked: Int
    )
    
    /**
     * Cleanup stats yang sudah tua (> 24 jam)
     */
    private fun cleanupOldStats() {
        val currentHour = System.currentTimeMillis() / 3600000
        val cutoffHour = currentHour - MAX_HOURS
        
        val expiredHours = hourlyStats.filterKeys { it < cutoffHour }.keys
        
        if (expiredHours.isNotEmpty()) {
            expiredHours.forEach { hourlyStats.remove(it) }
            Log.d(TAG, "Cleaned up ${expiredHours.size} expired hours of stats")
        }
        
        // Limit app stats count
        if (appStats.size > MAX_APPS) {
            val sorted = appStats.entries.sortedByDescending { it.value.totalRequests }
            val toRemove = appStats.keys - sorted.take(MAX_APPS).map { it.key }.toSet()
            toRemove.forEach { appStats.remove(it) }
            Log.d(TAG, "Cleaned up ${toRemove.size} least active apps from stats")
        }
    }
    
    /**
     * Clear semua statistics
     */
    fun clear() {
        hourlyStats.clear()
        appStats.clear()
        globalBlockedDomains.clear()
        Log.d(TAG, "Statistics cleared")
    }
    
    /**
     * Export stats untuk backup/debug
     */
    fun exportToJson(): String {
        val summary = getSummary()
        val topDomains = getTopBlockedDomains(20)
        
        return buildString {
            appendLine("{")
            appendLine("  \"totalRequests\": ${summary.totalRequests},")
            appendLine("  \"totalBlocked\": ${summary.totalBlocked},")
            appendLine("  \"blockingRate\": ${summary.blockingRate},")
            appendLine("  \"topBlockedDomains\": [")
            topDomains.forEachIndexed { index, (domain, count) ->
                val comma = if (index < topDomains.size - 1) "," else ""
                appendLine("    {\"domain\": \"$domain\", \"count\": $count}$comma")
            }
            appendLine("  ]")
            appendLine("}")
        }
    }
}
