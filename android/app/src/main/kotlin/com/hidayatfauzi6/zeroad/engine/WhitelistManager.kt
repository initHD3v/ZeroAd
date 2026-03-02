package com.hidayatfauzi6.zeroad.engine

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import java.util.concurrent.ConcurrentHashMap

/**
 * ZeroAd 2.0 - Whitelist Manager dengan Dynamic Update
 * 
 * Mengelola whitelist aplikasi tanpa perlu restart VPN service:
 * - User whitelist (manual selection)
 * - Auto whitelist (detected critical apps)
 * - Temporary bypass (auto-expiry)
 */
class WhitelistManager(private val context: Context) {
    
    companion object {
        private const val TAG = "WhitelistManager"
        private const val PREFS_NAME = "ZeroAdPrefs"
        private const val KEY_USER_WHITELIST = "whitelisted_apps"
    }
    
    // User-selected whitelist (persistent)
    private val userWhitelist = ConcurrentHashMap<String, Boolean>()
    
    // Auto-detected whitelist (critical apps)
    private val autoWhitelist = ConcurrentHashMap<String, Boolean>()
    
    // Temporary bypass (with expiry time)
    private val temporaryBypass = ConcurrentHashMap<String, Long>()
    
    // SharedPreferences
    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    
    /**
     * Load whitelist dari SharedPreferences
     */
    fun loadFromPrefs() {
        val userSet = prefs.getStringSet(KEY_USER_WHITELIST, emptySet()) ?: emptySet()
        
        userWhitelist.clear()
        userSet.forEach { pkg ->
            userWhitelist[pkg] = true
        }
        
        Log.d(TAG, "Loaded ${userWhitelist.size} user whitelisted apps")
    }
    
    /**
     * Add app ke user whitelist (persistent)
     */
    fun addToWhitelist(packageName: String) {
        userWhitelist[packageName] = true
        saveToPrefs()
        Log.d(TAG, "Added to whitelist: $packageName")
    }
    
    /**
     * Remove app dari user whitelist
     */
    fun removeFromWhitelist(packageName: String) {
        userWhitelist.remove(packageName)
        saveToPrefs()
        Log.d(TAG, "Removed from whitelist: $packageName")
    }
    
    /**
     * Add ke temporary bypass (auto-expiry)
     * @param durationMs Duration dalam milliseconds (default: 1 jam)
     */
    fun addToTemporaryBypass(packageName: String, durationMs: Long = 3600000L) {
        val expiry = System.currentTimeMillis() + durationMs
        temporaryBypass[packageName] = expiry
        Log.d(TAG, "Added to temporary bypass: $packageName (expires in ${durationMs / 1000}s)")
    }
    
    /**
     * Check apakah app di-whitelist
     */
    fun isWhitelisted(packageName: String): Boolean {
        // Check user whitelist
        if (userWhitelist.containsKey(packageName)) {
            return true
        }
        
        // Check auto whitelist
        if (autoWhitelist.containsKey(packageName)) {
            return true
        }
        
        // Check temporary bypass (dengan expiry check)
        val expiry = temporaryBypass[packageName]
        if (expiry != null) {
            if (expiry > System.currentTimeMillis()) {
                return true  // Masih aktif
            } else {
                temporaryBypass.remove(packageName)  // Expired, remove
            }
        }
        
        return false
    }
    
    /**
     * Set auto whitelist (critical apps)
     */
    fun setAutoWhitelist(packages: Collection<String>) {
        autoWhitelist.clear()
        packages.forEach { pkg ->
            autoWhitelist[pkg] = true
        }
        Log.d(TAG, "Set ${autoWhitelist.size} auto-whitelisted apps")
    }
    
    /**
     * Add single app ke auto whitelist
     */
    fun addToAutoWhitelist(packageName: String) {
        autoWhitelist[packageName] = true
    }
    
    /**
     * Get semua user whitelisted packages
     */
    fun getUserWhitelist(): Set<String> {
        return userWhitelist.keys.toSet()
    }
    
    /**
     * Get total whitelisted apps count
     */
    fun getWhitelistCount(): Int {
        return userWhitelist.size + autoWhitelist.size + temporaryBypass.size
    }
    
    /**
     * Clear temporary bypass (expired entries)
     */
    fun cleanupTemporaryBypass() {
        val now = System.currentTimeMillis()
        val expired = temporaryBypass.filterValues { it <= now }.keys
        expired.forEach { temporaryBypass.remove(it) }
        
        if (expired.isNotEmpty()) {
            Log.d(TAG, "Cleaned up ${expired.size} expired temporary bypass entries")
        }
    }
    
    /**
     * Clear semua whitelist (untuk reset)
     */
    fun clearAll() {
        userWhitelist.clear()
        autoWhitelist.clear()
        temporaryBypass.clear()
        prefs.edit().remove(KEY_USER_WHITELIST).apply()
    }
    
    /**
     * Save ke SharedPreferences
     */
    private fun saveToPrefs() {
        prefs.edit()
            .putStringSet(KEY_USER_WHITELIST, userWhitelist.keys.toSet())
            .apply()
    }
}
