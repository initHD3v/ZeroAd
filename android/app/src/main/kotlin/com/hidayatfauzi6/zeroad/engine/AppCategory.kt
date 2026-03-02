package com.hidayatfauzi6.zeroad.engine

/**
 * ZeroAd 2.0 - Shared App Category Enum
 * 
 * Digunakan oleh semua engine untuk kategorisasi aplikasi:
 * - AdFilterEngine (legacy)
 * - SmartBypassEngine
 * - DnsFilterEngine
 * - StatisticsEngine
 * 
 * Kategori ini menentukan bagaimana traffic aplikasi di-handle:
 * - BYPASS: Direct connection ke ISP (no filtering)
 * - TUNNEL_SAFE: VPN dengan whitelist-heavy filtering
 * - TUNNEL_FILTER: VPN dengan full filtering
 * - TUNNEL_AGGRESSIVE: VPN dengan aggressive blocking
 */
enum class AppCategory {
    /**
     * Android system apps
     * Strategy: BYPASS_DNS_ONLY (don't break system functionality)
     */
    SYSTEM,
    
    /**
     * Google services (GMS, Play Store, Play Games, Firebase)
     * Strategy: BYPASS_DNS_ONLY (don't break Google services)
     */
    GOOGLE_SERVICES,
    
    /**
     * Critical communication apps (WhatsApp, Telegram, Signal)
     * Strategy: BYPASS_FULL (critical messaging, don't risk breaking)
     */
    CRITICAL_COMM,
    
    /**
     * Banking & finance apps (BCA, Mandiri, DANA, OVO, etc.)
     * Strategy: BYPASS_DNS_ONLY (don't risk breaking financial apps)
     */
    BANKING,
    
    /**
     * E-commerce apps (Shopee, Tokopedia, Lazada, etc.)
     * Strategy: BYPASS_DNS_ONLY (don't break shopping experience)
     */
    E_COMMERCE,
    
    /**
     * Social media apps (Facebook, Instagram, Twitter, TikTok)
     * Strategy: TUNNEL_SAFE (block ads but keep core functionality)
     */
    SOCIAL_MEDIA,
    
    /**
     * Streaming apps (Netflix, Spotify, YouTube, Disney+)
     * Strategy: TUNNEL_SAFE (block ads but keep CDN access)
     */
    STREAMING,
    
    /**
     * Web browsers (Chrome, Firefox, Opera, Brave)
     * Strategy: TUNNEL_FILTER (full filtering with DoH blocking)
     */
    BROWSER,
    
    /**
     * Games - all games (with or without IAP)
     * Strategy: TUNNEL_SAFE (block only hard ads, don't break game)
     * Note: For more granular control, use GAME_WITH_IAP or GAME_CASUAL
     */
    GAME,
    
    /**
     * Games with in-app purchases (IAP)
     * Strategy: TUNNEL_SAFE (don't break IAP, block only hard ads)
     */
    GAME_WITH_IAP,
    
    /**
     * Casual games without IAP
     * Strategy: TUNNEL_FILTER (can block more aggressively)
     */
    GAME_CASUAL,
    
    /**
     * Default category for unknown apps
     * Strategy: TUNNEL_FILTER (default filtering)
     */
    GENERAL
}

/**
 * Tunnel strategy untuk setiap kategori aplikasi
 * 
 * Menentukan bagaimana traffic aplikasi di-handle:
 * - BYPASS_FULL: Direct ISP connection, no VPN, no filtering
 * - BYPASS_DNS_ONLY: VPN tunnel tapi no DNS filtering
 * - TUNNEL_SAFE: VPN + whitelist-heavy filtering (allow most, block only confirmed ads)
 * - TUNNEL_FILTER: VPN + full filtering (block known ads & trackers)
 * - TUNNEL_AGGRESSIVE: VPN + aggressive blocking (block suspicious domains too)
 */
enum class TunnelStrategy {
    BYPASS_FULL,
    BYPASS_DNS_ONLY,
    TUNNEL_SAFE,
    TUNNEL_FILTER,
    TUNNEL_AGGRESSIVE
}

/**
 * Mapping dari AppCategory ke TunnelStrategy
 *
 * Ini adalah "smart bypass logic" yang membuat ZeroAd tidak break apps
 */
fun AppCategory.getDefaultTunnelStrategy(): TunnelStrategy {
    return when (this) {
        // Critical apps - don't risk breaking
        AppCategory.SYSTEM, AppCategory.GOOGLE_SERVICES -> TunnelStrategy.BYPASS_DNS_ONLY
        AppCategory.CRITICAL_COMM -> TunnelStrategy.BYPASS_FULL  // WhatsApp critical

        // Financial & shopping - don't break transactions
        AppCategory.BANKING, AppCategory.E_COMMERCE -> TunnelStrategy.BYPASS_DNS_ONLY

        // Social & streaming - block ads but keep content
        AppCategory.SOCIAL_MEDIA, AppCategory.STREAMING -> TunnelStrategy.TUNNEL_SAFE

        // Browsers - full filtering (with DoH blocking)
        AppCategory.BROWSER -> TunnelStrategy.TUNNEL_FILTER

        // Games - safe filtering
        AppCategory.GAME, AppCategory.GAME_WITH_IAP, AppCategory.GAME_CASUAL -> TunnelStrategy.TUNNEL_SAFE

        // Default - full filtering
        AppCategory.GENERAL -> TunnelStrategy.TUNNEL_FILTER
    }
}
