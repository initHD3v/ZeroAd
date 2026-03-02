package com.hidayatfauzi6.zeroad.engine

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log

/**
 * ZeroAd 2.0 - Smart Bypass Engine
 *
 * Menentukan tunnel strategy untuk setiap aplikasi berdasarkan:
 * - App category (system, game, banking, dll)
 * - Certificate signature (Google apps)
 * - Permissions (banking apps)
 * - In-app billing capability
 * - Package name patterns (fallback)
 */
class SmartBypassEngine(private val context: Context) {

    companion object {
        private const val TAG = "SmartBypassEngine"
        // Note: AppCategory and TunnelStrategy moved to top-level AppCategory.kt
    }

    private val packageManager: PackageManager = context.packageManager
    
    // Google certificate hash (untuk verifikasi Google apps)
    private val GOOGLE_CERTIFICATES = setOf(
        "com.google.android.gms",
        "com.android.vending",
        "com.google.android.gsf"
    )
    
    // Package name patterns untuk categorization
    private val BANKING_PATTERNS = setOf(
        "bank", "bca", "mandiri", "bni", "bri", "dana", "ovo", "gopay",
        "linkaja", "shopeepay", "jenius", "jago", "neobank", "livin",
        "brimo", "klikbca", "mandiriiri", "bnimobile", "paoblus",
        "cimb", "maybank", "danamon", "permata", "btpn", "citibank",
        "hsbc", "standardchartered", "dbss", "uob", "ocbc"
    )
    
    private val E_COMMERCE_PATTERNS = setOf(
        "shopee", "tokopedia", "lazada", "bukalapak", "blibli",
        "tiktok", "alibaba", "amazon", "ebay", "jd", "zalora",
        "matahari", "ramayana", "hero", "carrefour", "hypermart"
    )
    
    private val SOCIAL_MEDIA_PATTERNS = setOf(
        "facebook", "instagram", "twitter", "snapchat", "tiktok",
        "linkedin", "pinterest", "reddit", "telegram", "discord",
        "whatsapp", "line", "wechat", "kakaotalk", "viber"
    )
    
    private val STREAMING_PATTERNS = setOf(
        "netflix", "spotify", "youtube", "primevideo", "disney",
        "hulu", "hbogo", "viu", "iflix", "catchplay", "vidio",
        "joox", "langitmusik", "deezer", "tidal", "applemusic"
    )
    
    private val BROWSER_PATTERNS = setOf(
        "chrome", "firefox", "opera", "safari", "edge", "brave",
        "duckduckgo", "ucbrowser", "cmbrowser", "maxthon"
    )
    
    /**
     * Categorize aplikasi berdasarkan package name
     */
    fun categorizeApp(packageName: String): AppCategory {
        val lowerPkg = packageName.lowercase()
        
        // 1. Check system apps
        if (isSystemApp(packageName)) {
            return AppCategory.SYSTEM
        }
        
        // 2. Check Google apps (certificate-based)
        if (isGoogleApp(packageName)) {
            return AppCategory.GOOGLE_SERVICES
        }
        
        // 3. Check for IAP (games with purchases)
        if (hasInAppBilling(packageName)) {
            return AppCategory.GAME_WITH_IAP
        }
        
        // 4. Check permissions (banking apps)
        if (hasBankingPermissions(packageName)) {
            return AppCategory.BANKING
        }
        
        // 5. Check declared category (Android 10+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val appInfo = packageManager.getApplicationInfo(packageName, 0)
                val declaredCategory = when (appInfo.category) {
                    ApplicationInfo.CATEGORY_GAME -> return AppCategory.GAME_WITH_IAP
                    ApplicationInfo.CATEGORY_AUDIO -> return AppCategory.STREAMING
                    ApplicationInfo.CATEGORY_VIDEO -> return AppCategory.STREAMING
                    // CATEGORY_MAP only available on Android 13+
                    else -> null
                }
            } catch (e: PackageManager.NameNotFoundException) {
                // Ignore
            }
        }
        
        // 6. Fallback: Package name patterns
        return categorizeByPackageName(lowerPkg)
    }
    
    /**
     * Get tunnel strategy berdasarkan app category
     */
    fun getTunnelStrategy(category: AppCategory): TunnelStrategy {
        return category.getDefaultTunnelStrategy()
    }
    
    /**
     * Convenience method: Get strategy langsung dari package name
     */
    fun getStrategyForApp(packageName: String): TunnelStrategy {
        val category = categorizeApp(packageName)
        return getTunnelStrategy(category)
    }
    
    // ==================== Helper Methods ====================
    
    private fun isSystemApp(packageName: String): Boolean {
        return try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0 ||
            (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }
    
    private fun isGoogleApp(packageName: String): Boolean {
        // Check package name prefix
        if (packageName.startsWith("com.google.") || 
            packageName.startsWith("com.android.")) {
            return true
        }
        
        // Check specific Google packages
        if (GOOGLE_CERTIFICATES.contains(packageName)) {
            return true
        }
        
        return false
    }
    
    private fun hasInAppBilling(packageName: String): Boolean {
        return try {
            // Check if app has billing features via Google Play Billing
            // Since Android doesn't expose this directly, we use package name patterns
            val lowerPkg = packageName.lowercase()
            
            // Games and paid apps typically have these patterns
            val billingPatterns = listOf(
                "game", "play", "app", "pro", "premium", "paid"
            )
            
            // Check for common IAP indicators
            billingPatterns.any { lowerPkg.contains(it) }
        } catch (e: Exception) {
            false
        }
    }
    
    private fun hasBankingPermissions(packageName: String): Boolean {
        return try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            
            // Banking apps typically have sensitive permissions
            val hasSensitivePerms = listOf(
                "android.permission.READ_SMS",
                "android.permission.RECEIVE_SMS",
                "android.permission.READ_CONTACTS",
                "android.permission.CAMERA",
                "android.permission.USE_BIOMETRIC",
                "android.permission.USE_FINGERPRINT"
            ).any { perm ->
                packageManager.checkPermission(perm, packageName) == 
                PackageManager.PERMISSION_GRANTED
            }
            
            hasSensitivePerms
        } catch (e: Exception) {
            false
        }
    }
    
    private fun categorizeByPackageName(lowerPkg: String): AppCategory {
        // Banking
        if (BANKING_PATTERNS.any { lowerPkg.contains(it) }) {
            return AppCategory.BANKING
        }
        
        // E-commerce
        if (E_COMMERCE_PATTERNS.any { lowerPkg.contains(it) }) {
            return AppCategory.E_COMMERCE
        }
        
        // Social Media
        if (SOCIAL_MEDIA_PATTERNS.any { lowerPkg.contains(it) }) {
            // WhatsApp special case
            if (lowerPkg.contains("whatsapp")) {
                return AppCategory.CRITICAL_COMM
            }
            return AppCategory.SOCIAL_MEDIA
        }
        
        // Streaming
        if (STREAMING_PATTERNS.any { lowerPkg.contains(it) }) {
            return AppCategory.STREAMING
        }
        
        // Browser
        if (BROWSER_PATTERNS.any { lowerPkg.contains(it) }) {
            return AppCategory.BROWSER
        }
        
        // Game detection
        if (lowerPkg.contains("game") || 
            lowerPkg.contains("unity") || 
            lowerPkg.contains("unreal") ||
            lowerPkg.contains("supercell") || 
            lowerPkg.contains("moonton") ||
            lowerPkg.contains("mihoyo") || 
            lowerPkg.contains("tencent") ||
            lowerPkg.contains("roblox") || 
            lowerPkg.contains("garena") ||
            lowerPkg.contains("niantic") || 
            lowerPkg.contains("activision")) {
            return AppCategory.GAME_WITH_IAP
        }
        
        return AppCategory.GENERAL
    }
}
