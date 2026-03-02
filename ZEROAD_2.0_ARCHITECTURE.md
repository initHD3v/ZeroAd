# ZeroAd 2.0 - Complete AdBlock Engine Re-engineering

## 🎯 Vision

Membangun ad blocker Android yang:
1. ✅ **Smart** - Context-aware filtering, tidak break apps
2. ✅ **Effective** - Block ads & tracking secara agresif tapi kontekstual
3. ✅ **Reliable** - Tidak cause "server unavailable" atau connection issues
4. ✅ **Transparent** - Live activity, statistics, user control
5. ✅ **Extensible** - Easy to add new filtering rules, DNS providers

---

## 📐 Architecture Overview

### System Components

```
┌─────────────────────────────────────────────────────────────────────────┐
│                         FLUTTER UI LAYER                                │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐   │
│  │ Shield Tab  │  │Activity Log │  │ Statistics  │   │  Settings   │   │
│  └──────┬──────┘  └──────┬──────┘  └──────┬──────┘  └──────┬──────┘   │
│         │                │                │                │           │
│         └────────────────┴────────────────┴────────────────┘           │
│                              │                                          │
│                    MethodChannel / EventChannel                         │
└──────────────────────────────┼──────────────────────────────────────────┘
                               │
┌──────────────────────────────┼──────────────────────────────────────────┐
│                      ANDROID NATIVE LAYER                               │
│  ┌───────────────────────────▼─────────────────────────────────────┐   │
│  │                      MainActivity                                │   │
│  │  • VPN permission handling                                       │   │
│  │  • App scanning (ad SDKs, permissions)                          │   │
│  │  • Whitelist management (dynamic, no restart)                   │   │
│  │  • Log streaming to Flutter                                      │   │
│  └───────────────────────────┬─────────────────────────────────────┘   │
│                              │                                           │
│  ┌───────────────────────────▼─────────────────────────────────────┐   │
│  │                   AdBlockVpnService (Refactored)                │   │
│  │                                                                  │   │
│  │  ┌────────────────────────────────────────────────────────────┐ │   │
│  │  │ PacketClassifier                                           │ │   │
│  │  │ • Route by app category (not hardcoded bypass)             │ │   │
│  │  │ • Dynamic rules (update without restart)                   │ │   │
│  │  └────────────────────────────────────────────────────────────┘ │   │
│  │                                                                  │   │
│  │  ┌────────────────────────────────────────────────────────────┐ │   │
│  │  │ DnsFilterEngine (NEW)                                      │ │   │
│  │  │ • Multi-layer filtering                                    │ │   │
│  │  │ • AdGuard DNS integration                                  │ │   │
│  │  │ • Smart domain classification                              │ │   │
│  │  │ • Sinkhole for games                                       │ │   │
│  │  └────────────────────────────────────────────────────────────┘ │   │
│  │                                                                  │   │
│  │  ┌────────────────────────────────────────────────────────────┐ │   │
│  │  │ SmartBypassEngine (NEW)                                    │ │   │
│  │  │ • Auto-detect app category                                 │ │   │
│  │  │ • Dynamic bypass rules                                     │ │   │
│  │  │ • Learning from failures                                   │ │   │
│  │  └────────────────────────────────────────────────────────────┘ │   │
│  │                                                                  │   │
│  │  ┌────────────────────────────────────────────────────────────┐ │   │
│  │  │ StatisticsEngine (NEW)                                     │ │   │
│  │  │ • Real-time stats                                          │ │   │
│  │  │ • Top blocked domains                                      │ │   │
│  │  │ • Per-app statistics                                       │ │   │
│  │  └────────────────────────────────────────────────────────────┘ │   │
│  │                                                                  │   │
│  │  ┌────────────────────────────────────────────────────────────┐ │   │
│  │  │ TcpConnectionManager (NEW)                                 │ │   │
│  │  │ • Connection pooling                                       │ │   │
│  │  │ • Persistent HTTP/HTTPS support                            │ │   │
│  │  │ • Proper keep-alive handling                               │ │   │
│  │  └────────────────────────────────────────────────────────────┘ │   │
│  │                                                                  │   │
│  │  ┌────────────────────────────────────────────────────────────┐ │   │
│  │  │ DohBlocker (NEW)                                           │ │   │
│  │  │ • Block DNS-over-HTTPS to prevent bypass                   │ │   │
│  │  │ • Force apps to use system DNS                             │ │   │
│  │  └────────────────────────────────────────────────────────────┘ │   │
│  └─────────────────────────────────────────────────────────────────┘   │
│                                                                       │
└───────────────────────────────────────────────────────────────────────┘
                               │
                    ┌──────────┴──────────┐
                    │                     │
                    ▼                     ▼
          ┌─────────────────┐   ┌─────────────────┐
          │  AdGuard DNS    │   │    ISP DNS      │
          │  (Primary)      │   │   (Fallback)    │
          │  94.140.14.140  │   │   8.8.8.8       │
          └─────────────────┘   └─────────────────┘
```

---

## 🔧 Core Components Design

### 1. DnsFilterEngine (NEW)

**Responsibility:** Multi-layer DNS filtering decisions

**Filtering Pipeline:**
```
DNS Query Received
       │
       ▼
┌─────────────────────────────┐
│ Layer 1: App Whitelist      │ → ALLOW (forward to ISP)
│ (User-selected apps)        │
└──────────────┬──────────────┘
               │ NO
               ▼
┌─────────────────────────────┐
│ Layer 2: Google Services    │ → ALLOW (forward to ISP)
│ (GMS, Play, Firebase)       │
└──────────────┬──────────────┘
               │ NO
               ▼
┌─────────────────────────────┐
│ Layer 3: DoH Domains        │ → BLOCK (sinkhole)
│ (dns.google, etc.)          │
└──────────────┬──────────────┘
               │ NO
               ▼
┌─────────────────────────────┐
│ Layer 4: Game Soft-Whitelist│ → ALLOW (forward to AdGuard)
│ (TCO, etc.)                 │
└──────────────┬──────────────┘
               │ NO
               ▼
┌─────────────────────────────┐
│ Layer 5: System Whitelist   │ → ALLOW (forward to ISP)
│ (Critical infrastructure)   │
└──────────────┬──────────────┘
               │ NO
               ▼
┌─────────────────────────────┐
│ Layer 6: Google Ads         │ → BLOCK (sinkhole)
│ (googleads.g.doubleclick)   │
└──────────────┬──────────────┘
               │ NO
               ▼
┌─────────────────────────────┐
│ Layer 7: Local Blocklist    │ → BLOCK (sinkhole)
│ (hosts.txt, dynamic)        │
└──────────────┬──────────────┘
               │ NO
               ▼
┌─────────────────────────────┐
│ Layer 8: AdGuard DNS        │ → FORWARD (94.140.14.140)
│ (Cloud-filtered DNS)        │
└──────────────┬──────────────┘
               │ TIMEOUT
               ▼
┌─────────────────────────────┐
│ Layer 9: Fallback ISP DNS   │ → FORWARD (8.8.8.8)
└─────────────────────────────┘
```

**Implementation:**
```kotlin
class DnsFilterEngine(
    private val context: Context,
    private val whitelistManager: WhitelistManager,
    private val blocklistManager: BlocklistManager,
    private val dnsForwarder: DnsForwarder
) {
    
    sealed class FilteringResult {
        object ALLOWED_WHITELIST : FilteringResult()
        object ALLOWED_GOOGLE : FilteringResult()
        object ALLOWED_SAFE_DOMAIN : FilteringResult()
        object BLOCKED_DOH : FilteringResult()
        object BLOCKED_ADS : FilteringResult()
        object BLOCKED_TRACKER : FilteringResult()
        object FORWARD_TO_ADGUARD : FilteringResult()
        object FORWARD_TO_ISP : FilteringResult()
    }
    
    suspend fun handleDnsQuery(
        payload: ByteArray,
        appInfo: AppInfo
    ): DnsResponse {
        val domain = extractDomain(payload)
        
        // Layer 1: User whitelist
        if (whitelistManager.isWhitelisted(appInfo.packageName)) {
            return dnsForwarder.forward(payload, DnsProvider.ISP)
        }
        
        // Layer 2: Google Services
        if (isGoogleService(domain, appInfo.packageName)) {
            return dnsForwarder.forward(payload, DnsProvider.ISP)
        }
        
        // Layer 3: DoH blocking
        if (isDohDomain(domain)) {
            return createSinkholeResponse(payload)
        }
        
        // Layer 4: Game soft-whitelist
        if (isGameWithAnalytics(appInfo.packageName)) {
            return dnsForwarder.forward(payload, DnsProvider.ADGUARD)
        }
        
        // Layer 5: System whitelist
        if (blocklistManager.isSystemWhitelisted(domain)) {
            return dnsForwarder.forward(payload, DnsProvider.ISP)
        }
        
        // Layer 6: Google Ads (priority block)
        if (blocklistManager.isGoogleAds(domain)) {
            return createSinkholeResponse(payload)
        }
        
        // Layer 7: Local blocklist
        if (blocklistManager.isBlocked(domain)) {
            return createSinkholeResponse(payload)
        }
        
        // Layer 8: AdGuard DNS (default)
        return try {
            dnsForwarder.forward(payload, DnsProvider.ADGUARD)
        } catch (e: TimeoutException) {
            // Layer 9: Fallback
            dnsForwarder.forward(payload, DnsProvider.ISP)
        }
    }
}
```

---

### 2. SmartBypassEngine (NEW)

**Responsibility:** Dynamic app categorization and bypass decisions

**App Categories:**
```kotlin
enum class AppCategory {
    SYSTEM,             // Android system apps
    GOOGLE_SERVICES,    // GMS, Play Store, Play Games
    CRITICAL_COMM,      // WhatsApp, messaging apps
    BANKING,            // Banking & finance
    E_COMMERCE,         // Shopping apps
    SOCIAL_MEDIA,       // Facebook, Instagram, Twitter
    STREAMING,          // Netflix, Spotify, YouTube
    BROWSER,            // Chrome, Firefox, etc.
    GAME_WITH_IAP,      // Games with in-app purchases
    GAME_CASUAL,        // Games without IAP
    GENERAL             // Default category
}

enum class TunnelStrategy {
    BYPASS_FULL,        // Direct ISP, no VPN
    BYPASS_DNS_ONLY,    // VPN but no DNS filtering
    TUNNEL_SAFE,        // VPN + whitelist-heavy filtering
    TUNNEL_FILTER,      // VPN + full filtering
    TUNNEL_AGGRESSIVE   // VPN + aggressive blocking
}
```

**Auto-Detection Logic:**
```kotlin
class SmartBypassEngine(private val context: Context) {
    
    fun categorizeApp(packageName: String): AppCategory {
        val appInfo = context.packageManager.getApplicationInfo(packageName, 0)
        
        // Check system apps
        if (appInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0) {
            return AppCategory.SYSTEM
        }
        
        // Check Google apps (certificate-based)
        if (isSignedByGoogle(packageName)) {
            return AppCategory.GOOGLE_SERVICES
        }
        
        // Check for IAP (games with purchases)
        if (hasInAppBilling(packageName)) {
            return AppCategory.GAME_WITH_IAP
        }
        
        // Check permissions (banking apps)
        if (hasBankingPermissions(packageName)) {
            return AppCategory.BANKING
        }
        
        // Check declared category (Android 10+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return when (appInfo.category) {
                ApplicationInfo.CATEGORY_GAME -> AppCategory.GAME_WITH_IAP
                ApplicationInfo.CATEGORY_AUDIO -> AppCategory.STREAMING
                ApplicationInfo.CATEGORY_VIDEO -> AppCategory.STREAMING
                ApplicationInfo.CATEGORY_MAP -> AppCategory.STREAMING
                else -> AppCategory.GENERAL
            }
        }
        
        // Fallback: Package name patterns
        return categorizeByPackageName(packageName)
    }
    
    fun getTunnelStrategy(category: AppCategory): TunnelStrategy {
        return when (category) {
            AppCategory.SYSTEM, AppCategory.GOOGLE_SERVICES -> 
                TunnelStrategy.BYPASS_DNS_ONLY
            
            AppCategory.CRITICAL_COMM -> 
                TunnelStrategy.BYPASS_FULL  // WhatsApp critical
            
            AppCategory.BANKING, AppCategory.E_COMMERCE -> 
                TunnelStrategy.BYPASS_DNS_ONLY  // Don't risk breaking
            
            AppCategory.SOCIAL_MEDIA -> 
                TunnelStrategy.TUNNEL_SAFE  // Block ads, keep core
            
            AppCategory.STREAMING -> 
                TunnelStrategy.TUNNEL_SAFE  // Block ads, keep CDN
            
            AppCategory.BROWSER -> 
                TunnelStrategy.TUNNEL_FILTER  // Full filtering
            
            AppCategory.GAME_WITH_IAP -> 
                TunnelStrategy.TUNNEL_SAFE  // Don't break IAP
            
            AppCategory.GAME_CASUAL -> 
                TunnelStrategy.TUNNEL_FILTER  // Can block more
            
            AppCategory.GENERAL -> 
                TunnelStrategy.TUNNEL_FILTER  // Default filtering
        }
    }
}
```

---

### 3. StatisticsEngine (NEW)

**Responsibility:** Real-time statistics and analytics

```kotlin
class StatisticsEngine {
    
    data class HourlyStats(
        val hour: Long,
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
    
    private val hourlyStats = ConcurrentHashMap<Long, HourlyStats>()
    private val appStats = ConcurrentHashMap<String, AppStats>()
    
    fun recordRequest(
        domain: String,
        packageName: String,
        wasBlocked: Boolean
    ) {
        val hour = System.currentTimeMillis() / 3600000
        
        // Update hourly stats
        val hourStats = hourlyStats.getOrPut(hour) { HourlyStats(hour) }
        hourStats.totalRequests++
        if (wasBlocked) {
            hourStats.blockedRequests++
            hourStats.blockedDomains[domain] = 
                (hourStats.blockedDomains[domain] ?: 0) + 1
        }
        
        // Update app stats
        val app = appStats.getOrPut(packageName) { 
            AppStats(packageName) 
        }
        app.totalRequests++
        if (wasBlocked) {
            app.blockedRequests++
            app.topBlockedDomains[domain] = 
                (app.topBlockedDomains[domain] ?: 0) + 1
        }
        
        // Cleanup old stats (keep last 24 hours)
        cleanupOldStats()
    }
    
    fun getTopBlockedDomains(limit: Int = 10): List<Pair<String, Int>> {
        return hourlyStats.values
            .flatMap { it.blockedDomains.entries }
            .groupingBy { it.key }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .take(limit)
            .map { it.key to it.value }
    }
    
    fun getAppStats(packageName: String): AppStats? = appStats[packageName]
    
    fun getTotalBlocked(): Int = 
        hourlyStats.values.sumOf { it.blockedRequests }
    
    fun getTotalAllowed(): Int = 
        hourlyStats.values.sumOf { it.totalRequests - it.blockedRequests }
    
    fun getBlockingRate(): Double {
        val total = hourlyStats.values.sumOf { it.totalRequests }
        val blocked = hourlyStats.values.sumOf { it.blockedRequests }
        return if (total > 0) blocked.toDouble() / total else 0.0
    }
}
```

---

### 4. TcpConnectionManager (NEW)

**Responsibility:** Proper TCP connection handling with pooling

```kotlin
class TcpConnectionManager {
    
    data class ConnectionKey(
        val srcIp: String,
        val srcPort: Int,
        val dstIp: String,
        val dstPort: Int
    )
    
    private val connectionPool = ConcurrentHashMap<ConnectionKey, Socket>()
    private val connectionLocks = ConcurrentHashMap<ConnectionKey, ReentrantLock>()
    
    suspend fun forwardTcp(
        packet: ByteBuffer,
        ipHeaderLen: Int
    ): ByteArray? {
        val key = extractConnectionKey(packet, ipHeaderLen)
        val lock = connectionLocks.getOrPut(key) { ReentrantLock() }
        
        return withContext(Dispatchers.IO) {
            lock.withLock {
                try {
                    // Get or create connection
                    val socket = connectionPool.getOrPut(key) {
                        createTcpConnection(key)
                    }
                    
                    // Send payload
                    val payload = extractTcpPayload(packet, ipHeaderLen)
                    socket.getOutputStream().write(payload)
                    socket.getOutputStream().flush()
                    
                    // Read response (non-blocking)
                    socket.soTimeout = 5000
                    val buffer = ByteArray(4096)
                    val bytesRead = socket.getInputStream().read(buffer)
                    
                    if (bytesRead > 0) {
                        buffer.copyOf(bytesRead)
                    } else {
                        null
                    }
                } catch (e: Exception) {
                    // Connection failed, remove from pool
                    connectionPool.remove(key)?.close()
                    null
                }
            }
        }
    }
    
    private fun createTcpConnection(key: ConnectionKey): Socket {
        return Socket(InetAddress.getByName(key.dstIp), key.dstPort).apply {
            tcpNoDelay = true
            keepAlive = true
            soTimeout = 5000
        }
    }
    
    fun closeConnection(key: ConnectionKey) {
        connectionPool.remove(key)?.close()
        connectionLocks.remove(key)
    }
    
    fun cleanup() {
        connectionPool.values.forEach { it.close() }
        connectionPool.clear()
        connectionLocks.clear()
    }
}
```

---

### 5. DnsForwarder (NEW)

**Responsibility:** Forward DNS queries to upstream servers

```kotlin
class DnsForwarder {
    
    enum class DnsProvider {
        ADGUARD,    // 94.140.14.140
        CLOUDFLARE, // 1.1.1.1
        GOOGLE,     // 8.8.8.8
        ISP         // System DNS
    }
    
    private val dnsServers = mapOf(
        DnsProvider.ADGUARD to "94.140.14.140",
        DnsProvider.CLOUDFLARE to "1.1.1.1",
        DnsProvider.GOOGLE to "8.8.8.8"
    )
    
    suspend fun forward(
        payload: ByteArray,
        provider: DnsProvider
    ): ByteArray {
        val dnsServer = when (provider) {
            DnsProvider.ISP -> getSystemDns() ?: "8.8.8.8"
            else -> dnsServers[provider] ?: "8.8.8.8"
        }
        
        return withContext(Dispatchers.IO) {
            var socket: DatagramSocket? = null
            try {
                socket = DatagramSocket()
                protect(socket)  // VPN bypass
                
                // Try with timeout
                socket.soTimeout = 3000
                
                val out = DatagramPacket(
                    payload, 
                    payload.size, 
                    InetAddress.getByName(dnsServer), 
                    53
                )
                socket.send(out)
                
                val inData = ByteArray(1500)
                val inPacket = DatagramPacket(inData, inData.size)
                socket.receive(inPacket)
                
                inData.copyOf(inPacket.length)
            } catch (e: SocketTimeoutException) {
                throw TimeoutException("DNS timeout to $dnsServer")
            } finally {
                socket?.close()
            }
        }
    }
    
    private fun getSystemDns(): String? {
        // Implementation from current code
    }
    
    private fun protect(socket: DatagramSocket) {
        // VPNService.protect() call
    }
}
```

---

### 6. WhitelistManager (NEW)

**Responsibility:** Dynamic whitelist management without service restart

```kotlin
class WhitelistManager(private val context: Context) {
    
    private val userWhitelist = ConcurrentHashMap<String, Boolean>()
    private val autoWhitelist = ConcurrentHashMap<String, Boolean>()
    private val temporaryBypass = ConcurrentHashMap<String, Long>() // pkg → expiry
    
    fun loadFromPrefs() {
        val prefs = context.getSharedPreferences("ZeroAdPrefs", Context.MODE_PRIVATE)
        val userSet = prefs.getStringSet("whitelisted_apps", emptySet()) ?: emptySet()
        
        userWhitelist.clear()
        userSet.forEach { userWhitelist[it] = true }
    }
    
    fun addToWhitelist(packageName: String) {
        userWhitelist[packageName] = true
        saveToPrefs()
        // NO NEED TO RESTART SERVICE
    }
    
    fun removeFromWhitelist(packageName: String) {
        userWhitelist.remove(packageName)
        saveToPrefs()
    }
    
    fun isWhitelisted(packageName: String): Boolean {
        // Check user whitelist
        if (userWhitelist.containsKey(packageName)) return true
        
        // Check auto whitelist
        if (autoWhitelist.containsKey(packageName)) return true
        
        // Check temporary bypass
        val expiry = temporaryBypass[packageName]
        if (expiry != null && expiry > System.currentTimeMillis()) {
            return true
        } else if (expiry != null) {
            temporaryBypass.remove(packageName)  // Expired
        }
        
        return false
    }
    
    fun addToTemporaryBypass(packageName: String, durationMs: Long = 3600000) {
        temporaryBypass[packageName] = System.currentTimeMillis() + durationMs
    }
    
    private fun saveToPrefs() {
        val prefs = context.getSharedPreferences("ZeroAdPrefs", Context.MODE_PRIVATE)
        prefs.edit().putStringSet("whitelisted_apps", userWhitelist.keys).apply()
    }
}
```

---

### 7. DohBlocker (NEW)

**Responsibility:** Block DNS-over-HTTPS to prevent bypass

```kotlin
class DohBlocker {
    
    private val dohDomains = setOf(
        "dns.google",
        "dns.google.com",
        "cloudflare-dns.com",
        "doh.pub",
        "doh.cleanbrowsing.org",
        "dns.adguard.com",
        "mozilla.cloudflare-dns.com",
        "private.canadianshield.cira.ca",
        "dns.quad9.net"
    )
    
    fun isDohDomain(domain: String): Boolean {
        val cleanDomain = domain.lowercase().trimEnd('.')
        return dohDomains.any { 
            cleanDomain == it || cleanDomain.endsWith(".$it") 
        }
    }
    
    fun createBlockedResponse(request: ByteBuffer): ByteArray {
        // Return NXDOMAIN or sinkhole
        return SimpleDnsParser.createNxDomainResponse(request)
    }
}
```

---

## 📋 Implementation Plan

### Phase 1: Core Infrastructure (Week 1)
- [ ] Create new package structure
- [ ] Implement DnsFilterEngine
- [ ] Implement DnsForwarder
- [ ] Implement WhitelistManager
- [ ] Integrate into AdBlockVpnService

### Phase 2: Smart Features (Week 2)
- [ ] Implement SmartBypassEngine
- [ ] Implement app category detection
- [ ] Implement tunnel strategies
- [ ] Add DoH blocking

### Phase 3: TCP & Reliability (Week 3)
- [ ] Implement TcpConnectionManager
- [ ] Fix connection pooling
- [ ] Add failover logic
- [ ] Test with TCO and problematic apps

### Phase 4: Statistics & UI (Week 4)
- [ ] Implement StatisticsEngine
- [ ] Add live activity streaming
- [ ] Add top blocked domains
- [ ] Add per-app statistics
- [ ] Update Flutter UI

---

## 🎯 Success Metrics

| Metric | Current | Target |
|--------|---------|--------|
| TCO playable | ❌ No | ✅ Yes |
| Ads blocked (social media) | 0% | 60-80% |
| Ads blocked (games) | 0% | 70-90% |
| App breakage rate | High | <5% |
| Memory usage | Unbounded | <100MB |
| Battery drain | High | Moderate |
| User control | Limited | Full |

---

## 🔧 Migration Strategy

1. **Backward Compatible:** Keep existing API for Flutter
2. **Gradual Rollout:** Enable features one by one
3. **Fallback:** Can revert to old engine if issues
4. **Testing:** Extensive testing with problematic apps

---

## ✅ Next Steps

1. Review and approve this architecture
2. Start Phase 1 implementation
3. Test each component as built
4. Iterate based on real-world testing
