package com.hidayatfauzi6.zeroad

import android.content.Intent
import android.content.Context
import android.content.SharedPreferences
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import com.hidayatfauzi6.zeroad.engine.AdFilterEngine
import com.hidayatfauzi6.zeroad.engine.AppCategory
import com.hidayatfauzi6.zeroad.engine.RoutingManager
import com.hidayatfauzi6.zeroad.engine.WhitelistManager
import com.hidayatfauzi6.zeroad.engine.SmartBypassEngine
import com.hidayatfauzi6.zeroad.engine.StatisticsEngine
import com.hidayatfauzi6.zeroad.engine.DohBlocker
import com.hidayatfauzi6.zeroad.engine.TcpConnectionManager
import com.hidayatfauzi6.zeroad.engine.DnsFilterEngine
import com.hidayatfauzi6.zeroad.engine.DnsForwarder
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer
import java.util.concurrent.*
import kotlinx.coroutines.runBlocking

class AdBlockVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private var executor: ExecutorService? = null
    private var outputExecutor: ExecutorService? = null
    private val outputQueue = LinkedBlockingQueue<ByteArray>(2000)
    private var isRunning = false

    // Legacy filter engine
    private lateinit var filterEngine: AdFilterEngine

    // ZeroAd 2.0 engines
    private lateinit var whitelistManager: WhitelistManager
    private lateinit var smartBypassEngine: SmartBypassEngine
    private lateinit var statisticsEngine: StatisticsEngine
    private lateinit var dohBlocker: DohBlocker
    private lateinit var tcpConnectionManager: TcpConnectionManager
    private lateinit var dnsFilterEngine: DnsFilterEngine
    private lateinit var dnsForwarder: DnsForwarder

    // HYBRID SYSTEM: Pre-built whitelist dari MainActivity
    private var prebuiltWhitelist: Set<String> = emptySet()
    
    private val dnsCache = ConcurrentHashMap<String, Pair<ByteArray, Long>>()
    private val CACHE_TTL = 300000L
    private lateinit var prefs: SharedPreferences

    // --- SMART CACHE ---
    data class UidEntry(val uid: Int, val timestamp: Long)
    private val portUidCache = ConcurrentHashMap<Int, UidEntry>()
    private val uidCategoryCache = ConcurrentHashMap<Int, AppCategory>()
    private val PORT_CACHE_TTL = 30000L // 30 Detik
    
    private var blockedCount = 0
    private var lastNotificationUpdate = 0L
    private val NOTIFICATION_ID = 1
    private val CHANNEL_ID = "ZeroAd_Shield_Channel"

    companion object {
        const val ACTION_START = "com.hidayatfauzi6.zeroad.START"
        const val ACTION_STOP = "com.hidayatfauzi6.zeroad.STOP"
        const val ACTION_UPDATE_WHITELIST = "com.hidayatfauzi6.zeroad.UPDATE_WHITELIST"
        private const val TAG = "ZeroAdService"
        
        private val logBuffer = ConcurrentLinkedQueue<String>()
        private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
        private var isBatchScheduled = false
        private val pendingLogs = ConcurrentLinkedQueue<String>()

        fun addLog(log: String) {
            logBuffer.add(log)
            if (logBuffer.size > 500) logBuffer.poll()
            
            pendingLogs.add(log)
            if (!isBatchScheduled) {
                isBatchScheduled = true
                mainHandler.postDelayed({
                    val logsToSend = mutableListOf<String>()
                    while (pendingLogs.isNotEmpty()) {
                        pendingLogs.poll()?.let { logsToSend.add(it) }
                    }
                    logsToSend.forEach { MainActivity.sendLogToFlutter(it) }
                    isBatchScheduled = false
                }, 200)
            }
        }
        fun getLogs(): List<String> = logBuffer.toList().reversed()
    }

    override fun onCreate() {
        super.onCreate()
        filterEngine = AdFilterEngine(this)
        createNotificationChannel()
        
        // Initialize ZeroAd 2.0 engines
        whitelistManager = WhitelistManager(this)
        whitelistManager.loadFromPrefs()
        
        smartBypassEngine = SmartBypassEngine(this)
        statisticsEngine = StatisticsEngine()
        dohBlocker = DohBlocker()
        tcpConnectionManager = TcpConnectionManager(this)
        dnsForwarder = DnsForwarder(this)
        
        // Create DNS filter engine with all dependencies
        dnsFilterEngine = DnsFilterEngine(
            context = this,
            vpnService = this,
            whitelistManager = whitelistManager,
            smartBypassEngine = smartBypassEngine,
            statisticsEngine = statisticsEngine,
            dohBlocker = dohBlocker,
            adFilterEngine = filterEngine
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopVpn()
            ACTION_START -> {
                if (executor == null) executor = Executors.newFixedThreadPool(15)
                if (outputExecutor == null) outputExecutor = Executors.newSingleThreadExecutor()
                prefs = getSharedPreferences("ZeroAdPrefs", Context.MODE_PRIVATE)

                // HYBRID SYSTEM: Get pre-built whitelist from MainActivity
                val essentialApps = intent.getStringArrayListExtra("essential_apps") ?: arrayListOf()
                prebuiltWhitelist = essentialApps.toSet()
                
                android.util.Log.d(TAG, "🎯 Pre-built whitelist loaded: ${prebuiltWhitelist.size} apps will get ISP Direct")

                // Add critical system apps
                val criticalBypassPkgs = listOf(
                    "com.google.android.gms", // Google Play Services
                    "com.android.vending", // Play Store
                )
                criticalBypassPkgs.forEach { if (!prebuiltWhitelist.contains(it)) essentialApps.add(it) }

                filterEngine.updateEssentialApps(essentialApps)

                executor?.execute {
                    filterEngine.loadBlocklists(prefs)
                    val userWhitelist = prefs.getStringSet("whitelisted_apps", emptySet()) ?: emptySet()
                    filterEngine.updateUserWhitelist(userWhitelist)
                    buildAppCategoryCache()
                }

                startForeground(NOTIFICATION_ID, createNotification())
                startVpn()

                // Send startup log
                addLog("${System.currentTimeMillis()}|system.service|SYSTEM|STARTED|ZeroAd|ZeroAd Service")
            }
            ACTION_UPDATE_WHITELIST -> {
                executor?.execute {
                    filterEngine.loadBlocklists(prefs)
                    val userWhitelist = prefs.getStringSet("whitelisted_apps", emptySet()) ?: emptySet()
                    filterEngine.updateUserWhitelist(userWhitelist)
                    buildAppCategoryCache()
                }
            }
        }
        return START_STICKY
    }

    private fun buildAppCategoryCache() {
        try {
            val pm = packageManager
            val apps = pm.getInstalledApplications(android.content.pm.PackageManager.GET_META_DATA)
            val newCache = mutableMapOf<Int, AppCategory>()
            for (app in apps) {
                var category = AppCategory.GENERAL

                // 1. Deteksi Sistem
                if (app.packageName.startsWith("com.android") ||
                    app.packageName.startsWith("com.google.android") ||
                    (app.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0) {
                    category = AppCategory.SYSTEM
                }
                // 2. Deteksi Game
                else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    if (app.category == android.content.pm.ApplicationInfo.CATEGORY_GAME ||
                        (app.flags and android.content.pm.ApplicationInfo.FLAG_IS_GAME) != 0) {
                        category = AppCategory.GAME
                    }
                } else if ((app.flags and android.content.pm.ApplicationInfo.FLAG_IS_GAME) != 0) {
                    category = AppCategory.GAME
                }

                // 3. Fallback Heuristic Detection (Package Name Keywords)
                if (category == AppCategory.GENERAL) {
                    val pkg = app.packageName.lowercase()
                    if (pkg.contains("game") || pkg.contains("unity") || pkg.contains("unreal") ||
                        pkg.contains("supercell") || pkg.contains("moonton") || pkg.contains("mihoyo") ||
                        pkg.contains("tencent") || pkg.contains("roblox") || pkg.contains("garena") ||
                        pkg.contains("niantic") || pkg.contains("activision")) {
                        category = AppCategory.GAME
                    }
                }

                // 4. Fallback Heuristic Detection (E-Commerce -> SYSTEM Bypass)
                // E-Commerce apps often break with any filtering. We treat them as SYSTEM to bypass filtering.
                if (category == AppCategory.GENERAL) {
                    val pkg = app.packageName.lowercase()
                    if (pkg.contains("shopee") || pkg.contains("tokopedia") || pkg.contains("lazada") ||
                        pkg.contains("bukalapak") || pkg.contains("blibli") || pkg.contains("tiktok") ||
                        pkg.contains("alibaba") || pkg.contains("amazon") || pkg.contains("ebay") ||
                        // Finance / Banking
                        pkg.contains("bank") || pkg.contains("finance") || pkg.contains("wallet") ||
                        pkg.contains("dana") || pkg.contains("ovo") || pkg.contains("gopay") ||
                        pkg.contains("bca") || pkg.contains("mandiri") || pkg.contains("bni") ||
                        pkg.contains("bri") || pkg.contains("cimb") || pkg.contains("jenius") ||
                        pkg.contains("jago") || pkg.contains("neobank") || pkg.contains("livin") ||
                        pkg.contains("brimo")) {
                        category = AppCategory.SYSTEM
                    }
                }

                newCache[app.uid] = category
            }
            uidCategoryCache.clear()
            uidCategoryCache.putAll(newCache)
            Log.d(TAG, "App Category Cache Rebuilt: ${uidCategoryCache.size} apps")
        } catch (e: Exception) {
            Log.e(TAG, "Error building category cache", e)
        }
    }

    private fun startVpn() {
        if (isRunning) return
        isRunning = true
        try {
            val builder = Builder()
            val routingManager = RoutingManager(builder)
            
            routingManager.configureDnsOnlyRouting()
            // Kita kembalikan bypass untuk aplikasi esensial (Chrome, GMS, dll)
            // Ini menjamin kestabilan sistem dan internet Chrome.
            routingManager.applyBypassApps(packageName, filterEngine.getEssentialApps())

            vpnInterface = builder.establish()
            
            try {
                if (outputExecutor?.isShutdown == false) {
                    outputExecutor?.execute {
                        val fd = vpnInterface?.fileDescriptor ?: return@execute
                        val output = FileOutputStream(fd)
                        while (isRunning && vpnInterface != null) {
                            try {
                                val packet = outputQueue.poll(500, TimeUnit.MILLISECONDS)
                                if (packet != null) synchronized(output) { output.write(packet) }
                            } catch (e: Exception) {}
                        }
                    }
                }
            } catch (e: RejectedExecutionException) {}
            Thread { runLoop() }.start()
        } catch (e: Exception) { stopVpn() }
    }

    private fun runLoop() {
        val fd = vpnInterface?.fileDescriptor ?: return
        val input = FileInputStream(fd)
        val buffer = ByteBuffer.allocate(32767)
        while (isRunning && vpnInterface != null) {
            try {
                val length = input.read(buffer.array())
                if (length <= 0) continue

                val packet = ByteBuffer.wrap(buffer.array(), 0, length)
                val firstByte = packet.get(0).toInt() and 0xFF
                val version = firstByte shr 4

                if (version == 4) {
                    val protocol = packet.get(9).toInt() and 0xFF
                    val ipHeaderLen = (firstByte and 0x0F) * 4
                    
                    if (protocol == 17) { // UDP
                        val dstPort = packet.getShort(ipHeaderLen + 2).toInt() and 0xFFFF
                        val packetCopy = ByteArray(length); System.arraycopy(buffer.array(), 0, packetCopy, 0, length)
                        
                        if (dstPort == 53) {
                            // DNS Query - Filter ini
                            try {
                                if (executor?.isShutdown == false) {
                                    executor?.execute { handleDnsRequest(ByteBuffer.wrap(packetCopy), ipHeaderLen) }
                                }
                            } catch (e: RejectedExecutionException) {}
                        } else {
                            // Non-DNS UDP - Forward langsung (game, dll)
                            try {
                                if (executor?.isShutdown == false) {
                                    executor?.execute { forwardNonDnsUDP(ByteBuffer.wrap(packetCopy), ipHeaderLen) }
                                }
                            } catch (e: RejectedExecutionException) {}
                        }
                    } else if (protocol == 6) { // TCP - FIXED: Forward TCP traffic (HTTP/HTTPS)
                        // Ini yang membuat YouTube, browser, dll bisa connect!
                        val packetCopy = ByteArray(length); System.arraycopy(buffer.array(), 0, packetCopy, 0, length)
                        try {
                            if (executor?.isShutdown == false) {
                                // Cek apakah ini dari game dengan analytics dependency
                                val srcPort = ((packetCopy[ipHeaderLen].toInt() and 0xFF) shl 8) or (packetCopy[ipHeaderLen + 1].toInt() and 0xFF)
                                if (shouldBypassTCPForGame(srcPort, packetCopy)) {
                                    // Bypass TCP langsung ke internet (no filtering)
                                    executor?.execute { forwardTCPBypass(ByteBuffer.wrap(packetCopy), ipHeaderLen) }
                                } else {
                                    // Normal TCP forwarding dengan filtering
                                    executor?.execute { forwardTCP(ByteBuffer.wrap(packetCopy), ipHeaderLen) }
                                }
                            }
                        } catch (e: RejectedExecutionException) {}
                    }
                } else if (version == 6) {
                    val nextHeader = packet.get(6).toInt() and 0xFF
                    val ipHeaderLen = 40
                    
                    if (nextHeader == 17) { // UDP
                        val dstPort = packet.getShort(ipHeaderLen + 2).toInt() and 0xFFFF
                        val packetCopy = ByteArray(length); System.arraycopy(buffer.array(), 0, packetCopy, 0, length)
                        
                        if (dstPort == 53) {
                            // IPv6 DNS
                            try {
                                if (executor?.isShutdown == false) {
                                    executor?.execute { forwardOriginalQueryFailSafe(ByteBuffer.wrap(packetCopy), ipHeaderLen) }
                                }
                            } catch (e: RejectedExecutionException) {}
                        } else {
                            // IPv6 Non-DNS UDP
                            try {
                                if (executor?.isShutdown == false) {
                                    executor?.execute { forwardNonDnsUDP(ByteBuffer.wrap(packetCopy), ipHeaderLen) }
                                }
                            } catch (e: RejectedExecutionException) {}
                        }
                    } else if (nextHeader == 6) { // IPv6 TCP
                        val packetCopy = ByteArray(length); System.arraycopy(buffer.array(), 0, packetCopy, 0, length)
                        try {
                            if (executor?.isShutdown == false) {
                                // Cek apakah ini dari game dengan analytics dependency
                                val srcPort = ((packetCopy[ipHeaderLen].toInt() and 0xFF) shl 8) or (packetCopy[ipHeaderLen + 1].toInt() and 0xFF)
                                if (shouldBypassTCPForGame(srcPort, packetCopy)) {
                                    // Bypass TCP langsung ke internet (no filtering)
                                    executor?.execute { forwardTCPBypass(ByteBuffer.wrap(packetCopy), ipHeaderLen) }
                                } else {
                                    // Normal TCP forwarding dengan filtering
                                    executor?.execute { forwardTCP(ByteBuffer.wrap(packetCopy), ipHeaderLen) }
                                }
                            }
                        } catch (e: RejectedExecutionException) {}
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "RunLoop error", e)
            }
        }
    }

    /**
     * Games that MUST bypass VPN (ISP Direct) due to connectivity issues
     * These games detect VPN and show "Server unavailable"
     */
    private val gamesNeedISPDirect = setOf(
        "com.twoheadedshark.tco",      // TCO - Server unavailable with VPN
        "com.miniclip.realsniper",     // Pure Sniper - IAP fails with VPN
        // Add more games here if they have issues
    )
    
    /**
     * Check if app is a game package
     * Games get special handling: AdGuard DNS filtering + No logging
     */
    private fun isGamePackage(pkg: String): Boolean {
        val gamePatterns = listOf(
            // Game publishers
            "twoheadedshark", "miniclip", "garena", "gameloft",
            "mobile.legends", "tencent.ig", "pubg", "pubgmobile",
            "riotgames", "supercell", "miHoYo", "mihoyo",
            "roblox", "ea.", "ubisoft", "activision", "blizzard",
            "netease", "square.enix", "YoStar", "yostar",
            "boltrend", "lilithgame", "igg.android",
            "topwar", "funplus", "dts.freefire", "dts.",
            "moonton", "epicgames", "mojang", "innersloth",
            "axlebolt", "krafton", "pearlabyss", "com2us",
            "netmarble", "ncsoft", "devsisters",
            // Game engines (might indicate game)
            "unity", "unreal", "cocos2d", "godot"
        )
        return gamePatterns.any { pkg.contains(it) }
    }
    
    /**
     * Check if game needs ISP Direct bypass (no tunnel)
     */
    private fun needsISPDirectBypass(pkg: String): Boolean {
        return gamesNeedISPDirect.contains(pkg)
    }
    
    /**
     * Handle DNS request - main entry point for all DNS queries
     */
    private fun handleDnsRequest(packet: ByteBuffer, ipHeaderLen: Int) {
        try {
            val dnsInfo = SimpleDnsParser.parse(packet, ipHeaderLen) ?: return
            val srcPort = packet.getShort(ipHeaderLen).toInt() and 0xFFFF
            val appInfo = identifyAppFast(srcPort, packet)
            val pkg = appInfo.second
            
            // Check if this is a game that needs ISP Direct bypass
            if (isGamePackage(pkg)) {
                if (needsISPDirectBypass(pkg)) {
                    // ✅ Problematic game: Forward directly to ISP (no filtering)
                    handleBypassGameDnsQuery(packet, dnsInfo, ipHeaderLen)
                } else {
                    // ✅ Normal game: AdGuard DNS filtering, NO logging
                    handleGameDnsQuery(packet, dnsInfo, ipHeaderLen, appInfo)
                }
                return
            }
            
            // Non-game: Normal handling with logging
            handleNonGameDnsQuery(packet, dnsInfo, ipHeaderLen, appInfo)
            
        } catch (e: Exception) {
            Log.e(TAG, "DNS handle error", e)
            forwardOriginalQueryFailSafe(packet, ipHeaderLen)
        }
    }
    
    /**
     * Handle DNS query for games that need ISP Direct bypass
     * - Forward directly to ISP DNS (no filtering)
     * - NO logging (no Live Activity)
     * - Full connectivity (no ad blocking)
     */
    private fun handleBypassGameDnsQuery(packet: ByteBuffer, dnsInfo: SimpleDnsParser.DnsInfo, ipHeaderLen: Int) {
        executor?.execute {
            try {
                // Forward directly to ISP DNS (no filtering)
                val response = forwardQueryShared(dnsInfo.payload)
                
                if (response != null) {
                    sendSimpleDnsPacket(packet, response, ipHeaderLen)
                    // ❌ NO LOGGING for games!
                } else {
                    // Fallback
                    forwardOriginalQueryFailSafe(packet, ipHeaderLen)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Bypass game DNS error for ${dnsInfo.domain}", e)
                forwardOriginalQueryFailSafe(packet, ipHeaderLen)
            }
        }
    }
    
    /**
     * Handle DNS query for games
     * - Forward to AdGuard DNS (ads blocked)
     * - NO logging (no Live Activity)
     * - Critical domains allowed
     */
    private fun handleGameDnsQuery(packet: ByteBuffer, dnsInfo: SimpleDnsParser.DnsInfo, 
                                   ipHeaderLen: Int, appInfo: Triple<Int, String, String>) {
        executor?.execute {
            try {
                // Use DNS filter engine with game-optimized rules
                val response = runBlocking {
                    dnsFilterEngine.handleDnsQuery(packet, ipHeaderLen, 
                        DnsFilterEngine.AppInfo(
                            packageName = appInfo.second,
                            appName = appInfo.third,
                            uid = appInfo.first,
                            category = AppCategory.GAME
                        )
                    )
                }
                
                if (response != null) {
                    sendSimpleDnsPacket(packet, response, ipHeaderLen)
                    // ❌ NO LOGGING for games!
                    // Log.d(TAG, "Game DNS success: ${dnsInfo.domain}")
                } else {
                    // Fallback if filtering fails
                    forwardOriginalQueryFailSafe(packet, ipHeaderLen)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Game DNS error for ${dnsInfo.domain}", e)
                forwardOriginalQueryFailSafe(packet, ipHeaderLen)
            }
        }
    }
    
    /**
     * Handle DNS query for non-game apps
     * - Full filtering
     * - WITH logging (Live Activity)
     */
    private fun handleNonGameDnsQuery(packet: ByteBuffer, dnsInfo: SimpleDnsParser.DnsInfo, 
                                      ipHeaderLen: Int, appInfo: Triple<Int, String, String>) {
        val category = uidCategoryCache[appInfo.first] ?: AppCategory.GENERAL
        val newAppInfo = DnsFilterEngine.AppInfo(
            packageName = appInfo.second,
            appName = appInfo.third,
            uid = appInfo.first,
            category = category
        )

        executor?.execute {
            try {
                val response = runBlocking {
                    dnsFilterEngine.handleDnsQuery(packet, ipHeaderLen, newAppInfo)
                }
                sendSimpleDnsPacket(packet, response, ipHeaderLen)
            } catch (e: Exception) {
                Log.e(TAG, "DNS filter error, fallback to legacy", e)
                forwardOriginalQueryFailSafe(packet, ipHeaderLen)
            }
        }
    }

    /**
     * ADGUARD-STYLE: Deteksi domain Google Services yang legit
     * Ini adalah "rahasia" mengapa AdGuard tidak break Google services
     */
    private fun isGoogleServiceDomain(domain: String): Boolean {
        val safeGooglePatterns = listOf(
            // Google Play Services (IAP, Download, Update)
            Regex("""^play\.googleapis\.com$"""),
            Regex("""^play\.google\.com$"""),
            Regex("""^android\.clients\.google\.com$"""),
            Regex("""^clientservices\.googleapis\.com$"""),
            Regex("""^content\.googleapis\.com$"""),
            Regex("""^download\.googleapis\.com$"""),
            Regex("""^storage\.googleapis\.com$"""),
            Regex("""^playassetdelivery\.googleapis\.com$"""),
            Regex("""^playappasset\.googleapis\.com$"""),
            
            // Google Authentication & Account
            Regex("""^accounts\.google\.com$"""),
            Regex("""^auth\.googleapis\.com$"""),
            Regex("""^oauthaccountmanager\.googleapis\.com$"""),
            Regex("""^identitytoolkit\.googleapis\.com$"""),
            Regex("""^oauth2\.googleapis\.com$"""),
            Regex("""^www\.googleapis\.com$"""),
            
            // Firebase (Game Save, Remote Config)
            Regex("""^firebase\.googleapis\.com$"""),
            Regex("""^firestore\.googleapis\.com$"""),
            Regex("""^firebaseinstallations\.googleapis\.com$"""),
            Regex("""^firebaseremoteconfig\.googleapis\.com$"""),
            Regex("""^firebaseabtesting\.googleapis\.com$"""),
            Regex("""^firebaseinappmessaging\.googleapis\.com$"""),
            Regex("""^firebasestorage\.googleapis\.com$"""),
            Regex("""^firebasedynamiclinks\.googleapis\.com$"""),
            Regex("""^firebasefunctions\.googleapis\.com$"""),
            Regex("""^firebasemessaging\.googleapis\.com$"""),
            
            // Google Payments (IAP)
            Regex("""^payments\.google\.com$"""),
            Regex("""^checkout\.google\.com$"""),
            Regex("""^billing\.google\.com$"""),
            Regex("""^purchase\.google\.com$"""),
            Regex("""^playbilling\.googleapis\.com$"""),
            
            // Google Play Games
            Regex("""^games\.googleapis\.com$"""),
            Regex("""^playgames\.google\.com$"""),
            
            // Google Maps API
            Regex("""^maps\.googleapis\.com$"""),
            Regex("""^maps\.google\.com$"""),
            Regex("""^tile\.googleapis\.com$"""),
            
            // YouTube API
            Regex("""^youtube\.googleapis\.com$"""),
            Regex("""^www\.youtube-nocookie\.com$"""),
            
            // Android System
            Regex("""^android\.googleapis\.com$"""),
            Regex("""^googleapis\.l\.google\.com$"""),
        )
        
        return safeGooglePatterns.any { it.matches(domain) }
    }

    private fun forwardAndSendResponse(packet: ByteBuffer, dnsInfo: SimpleDnsParser.DnsInfo, ipHeaderLen: Int, category: String, action: String) {
        try {
            if (executor?.isShutdown == false) {
                executor?.execute {
                    forwardQueryShared(dnsInfo.payload).let { res ->
                        if (res != null) {
                            if (action == "ALLOWED") dnsCache[dnsInfo.domain] = Pair(res, System.currentTimeMillis())
                            sendSimpleDnsPacket(packet, res, ipHeaderLen)
                            asyncLog(packet, dnsInfo.domain, ipHeaderLen, category, action)
                        } else {
                            // Jika res null, berarti gagal koneksi ke ISP
                            asyncLog(packet, dnsInfo.domain, ipHeaderLen, category, "ISP_FAIL (No Connectivity)")
                        }
                    }
                }
            }
        } catch (e: RejectedExecutionException) {
            Log.w(TAG, "Task rejected during shutdown")
        }
    }

    private fun sendCachedResponse(packet: ByteBuffer, cachedPayload: ByteArray, dnsInfo: SimpleDnsParser.DnsInfo, ipHeaderLen: Int) {
        val patchedPayload = cachedPayload.copyOf()
        patchedPayload[0] = dnsInfo.payload[0]
        patchedPayload[1] = dnsInfo.payload[1]
        sendSimpleDnsPacket(packet, patchedPayload, ipHeaderLen)
    }

    private fun asyncLog(packet: ByteBuffer, domain: String, ipHeaderLen: Int, category: String, action: String) {
        val srcPort = packet.getShort(ipHeaderLen).toInt() and 0xFFFF

        // FIXED: Copy packet dengan benar sebelum di-consume
        val packetCopy = ByteBuffer.allocate(packet.limit())
        val originalPosition = packet.position()
        packet.rewind()
        packetCopy.put(packet)
        packet.position(originalPosition) // Restore position
        packetCopy.rewind()

        try {
            // FIXED: Langsung log tanpa executor untuk memastikan log terkirim
            val appInfo = identifyAppFast(srcPort, packetCopy)
            
            // ❌ SKIP logging for games!
            if (isGamePackage(appInfo.second)) {
                // Games don't appear in Live Activity
                return
            }
            
            val logEntry = "${System.currentTimeMillis()}|$domain|$category|$action|${appInfo.second}|${appInfo.third}"
            addLog(logEntry)

            // Debug log untuk troubleshooting
            // Log.d(TAG, "AsyncLog: $logEntry")
        } catch (e: Exception) {
            // Fallback: Log minimal jika identifyApp gagal
            try {
                addLog("${System.currentTimeMillis()}|$domain|$category|$action|unknown|Unknown")
            } catch (e2: Exception) {}
        }
    }

    private fun identifyAppFast(srcPort: Int, packet: ByteBuffer): Triple<Int, String, String> {
        val now = System.currentTimeMillis()
        val cached = portUidCache[srcPort]

        val uid = if (cached != null && (now - cached.timestamp < PORT_CACHE_TTL)) {
            cached.uid
        } else {
            val srcIp = getSourceIp(packet)
            val destIp = getDestIp(packet)
            val resolvedUid = getUidForPort(srcPort, srcIp, destIp)

            // Cache hasil, bahkan jika -1 (dengan TTL lebih singkat untuk -1)
            val ttl = if (resolvedUid > 0) PORT_CACHE_TTL else 5000L // 5 detik untuk unknown
            portUidCache[srcPort] = UidEntry(resolvedUid, now)
            resolvedUid
        }

        val pkg = getPackageNameFromUid(uid)
        val name = getAppNameFromPackage(pkg)
        return Triple(uid, pkg, name)
    }
    
    /**
     * HYBRID SYSTEM: Check if app should be bypassed
     * Menggunakan pre-built whitelist dari MainActivity (scan saat VPN start)
     * Tidak perlu on-demand check lagi - semua sudah di-scan di awal!
     */
    private fun shouldBypassApp(pkg: String): Boolean {
        // Check pre-built whitelist (dari MainActivity.getEssentialApps())
        if (prebuiltWhitelist.contains(pkg)) {
            Log.d(TAG, "✅ BYPASS Pre-built Whitelist: $pkg")
            return true
        }
        
        // Fallback: Check hard analytics dependency (games)
        if (hasHardAnalyticsDependency(pkg)) {
            Log.d(TAG, "✅ BYPASS Hard Analytics: $pkg")
            return true
        }
        
        Log.d(TAG, "❌ FILTER: $pkg")
        return false
    }

    /**
     * Deteksi game yang punya hard dependency pada analytics/tracking
     * Game seperti ini tidak bisa load jika analytics di-block (akan stuck di "Server unavailable")
     * Solusi: Forward semua DNS untuk game ini (soft whitelist)
     */
    private fun hasHardAnalyticsDependency(pkg: String): Boolean {
        // Game yang diketahui menggunakan analytics SDK dengan hard dependency
        val hardAnalyticsGames = listOf(
            "twoheadedshark",  // Two Headed Shark (developer TCO)
            "tco",             // Tuning Club Online (short name)
            "tuningclub",      // Alternative keyword
        )

        // Cek apakah package mengandung keyword
        val lowerPkg = pkg.lowercase()
        
        // Debug log untuk tracking
        Log.d(TAG, "Checking hard analytics for: $pkg")
        
        if (hardAnalyticsGames.any { lowerPkg.contains(it) }) {
            Log.d(TAG, "Matched hard analytics dependency: $pkg")
            return true
        }

        // Cek apakah game menggunakan Yandex SDK (dari package name)
        if (lowerPkg.contains("yandex")) {
            Log.d(TAG, "Matched Yandex SDK: $pkg")
            return true
        }

        return false
    }

    /**
     * Cek apakah TCP dari game ini harus di-bypass (tanpa filtering)
     * Game dengan hard analytics dependency perlu TCP bypass agar connect stabil
     */
    private fun shouldBypassTCPForGame(srcPort: Int, packet: ByteArray): Boolean {
        val uid = getUidForPortFromPacket(srcPort, packet)
        if (uid <= 0) return false

        val pkg = getPackageNameFromUid(uid)
        
        // Check menggunakan shouldBypassApp (on-demand, tidak scan semua apps)
        return shouldBypassApp(pkg)
    }

    /**
     * Get UID dari packet TCP (helper untuk bypass detection)
     */
    private fun getUidForPortFromPacket(srcPort: Int, packet: ByteArray): Int {
        // Parse IP header untuk dapatkan src IP
        val version = (packet[0].toInt() shr 4) and 0x0F
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
                val srcIp = if (version == 4) {
                    InetAddress.getByAddress(packet.copyOfRange(12, 16)).hostAddress
                } else {
                    InetAddress.getByAddress(packet.copyOfRange(8, 24)).hostAddress
                } ?: return -1
                
                cm.getConnectionOwnerUid(android.system.OsConstants.IPPROTO_TCP,
                    java.net.InetSocketAddress(InetAddress.getByName(srcIp), srcPort),
                    java.net.InetSocketAddress(InetAddress.getByName("0.0.0.0"), 0))
            } else {
                -1
            }
        } catch (e: Exception) {
            -1
        }
    }

    /**
     * Forward TCP langsung ke internet tanpa filtering (bypass mode)
     * Ini untuk game dengan analytics dependency yang butuh koneksi stabil
     */
    private fun forwardTCPBypass(packet: ByteBuffer, ipHeaderLen: Int) {
        var socket: java.net.Socket? = null
        try {
            val dstPort = packet.getShort(ipHeaderLen + 2).toInt() and 0xFFFF
            val dstIp = getDestIp(packet)
            val payloadStart = ipHeaderLen + 20 // TCP header minimal
            val payloadLen = packet.limit() - payloadStart

            if (payloadLen <= 0) return

            val payload = ByteArray(payloadLen)
            packet.position(payloadStart)
            packet.get(payload)

            // Direct TCP connection (no filtering)
            socket = java.net.Socket(InetAddress.getByName(dstIp), dstPort)
            socket.soTimeout = 5000

            val outputStream = socket.getOutputStream()
            outputStream.write(payload)
            outputStream.flush()

            val inputStream = socket.getInputStream()
            val buffer = ByteArray(4096)
            val bytesRead = inputStream.read(buffer)

            if (bytesRead > 0) {
                val response = buffer.copyOf(bytesRead)
                sendTCPResponse(packet, response, ipHeaderLen)
            }

        } catch (e: Exception) {
            // TCP bypass gagal - silent fail
        } finally {
            try { socket?.close() } catch (e: Exception) {}
        }
    }

    private fun forwardOriginalQueryFailSafe(packet: ByteBuffer, ipHeaderLen: Int) {
        try {
            val dnsInfo = SimpleDnsParser.parse(packet, ipHeaderLen) ?: return
            try {
                if (executor?.isShutdown == false) {
                    executor?.execute {
                        forwardQueryShared(dnsInfo.payload)?.let { res ->
                            sendSimpleDnsPacket(packet, res, ipHeaderLen)
                        }
                    }
                }
            } catch (e: RejectedExecutionException) {}
        } catch (e: Exception) {}
    }

    private fun forwardQueryShared(payload: ByteArray): ByteArray? {
        var socket: DatagramSocket? = null
        try {
            socket = DatagramSocket()
            protect(socket)
            socket.soTimeout = 2000
            
            val primaryDns = getSystemDns() ?: "8.8.8.8"
            
            // Coba DNS utama dulu
            try {
                val out = DatagramPacket(payload, payload.size, InetAddress.getByName(primaryDns), 53)
                val inData = ByteArray(1500); val inP = DatagramPacket(inData, inData.size)
                socket.send(out)
                socket.receive(inP)
                Log.d(TAG, "DNS success: $primaryDns")
                return inData.copyOf(inP.length)
            } catch (e: java.net.SocketTimeoutException) {
                // Timeout! Fallback ke IPv4 DNS
                Log.w(TAG, "DNS timeout with $primaryDns, fallback to 8.8.8.8")
                socket.close()
                socket = DatagramSocket()
                protect(socket)
                socket.soTimeout = 3000
                
                val fallbackDns = "8.8.8.8"
                val out = DatagramPacket(payload, payload.size, InetAddress.getByName(fallbackDns), 53)
                val inData = ByteArray(1500); val inP = DatagramPacket(inData, inData.size)
                socket.send(out)
                socket.receive(inP)
                Log.d(TAG, "DNS success via fallback: $fallbackDns")
                return inData.copyOf(inP.length)
            }
        } catch (e: Exception) {
            Log.e(TAG, "DNS Forward Error", e)
            return null
        } finally {
            try { socket?.close() } catch (e: Exception) {}
        }
    }

    private fun forwardNonDnsUDP(packet: ByteBuffer, ipHeaderLen: Int) {
        var socket: DatagramSocket? = null
        try {
            val dstPort = packet.getShort(ipHeaderLen + 2).toInt() and 0xFFFF
            val dstIp = getDestIp(packet)
            
            val payloadLen = (packet.getShort(ipHeaderLen + 4).toInt() and 0xFFFF) - 8
            if (payloadLen <= 0) return
            
            val payload = ByteArray(payloadLen)
            packet.position(ipHeaderLen + 8)
            packet.get(payload)
            
            socket = DatagramSocket()
            protect(socket)
            socket.soTimeout = 800 // Timeout lebih cepat untuk tes koneksi game
            val out = DatagramPacket(payload, payload.size, InetAddress.getByName(dstIp), dstPort)
            socket.send(out)
            
            val inData = ByteArray(1500)
            val inP = DatagramPacket(inData, inData.size)
            socket.receive(inP)
            
            val res = inData.copyOf(inP.length)
            sendSimpleDnsPacket(packet, res, ipHeaderLen)
            
            // Log aktivitas non-DNS agar tetap terlihat di Live Activity
            asyncLog(packet, "$dstIp:$dstPort", ipHeaderLen, "GENERAL", "PASS (UDP)")
        } catch (e: Exception) {
            // Jika gagal, biarkan saja (Game biasanya akan retry atau fallback)
        } finally { try { socket?.close() } catch (e: Exception) {} }
    }

    /**
     * FIXED: Forward TCP traffic (HTTP/HTTPS)
     * Ini yang membuat YouTube, browser, Instagram, dll bisa connect!
     * VPN hanya meneruskan TCP tanpa filtering (hanya DNS yang difilter)
     * 
     * ZeroAd 2.0: Uses TcpConnectionManager for connection pooling
     */
    private fun forwardTCP(packet: ByteBuffer, ipHeaderLen: Int) {
        executor?.execute {
            try {
                val response = runBlocking {
                    tcpConnectionManager.forwardTcp(packet, ipHeaderLen)
                }
                
                if (response != null) {
                    sendTCPResponse(packet, response, ipHeaderLen)
                }
            } catch (e: Exception) {
                Log.e(TAG, "TCP forward error", e)
            }
        }
    }
    
    /**
     * Send TCP response back through VPN
     */
    private fun sendTCPResponse(request: ByteBuffer, payload: ByteArray, ipHeaderLen: Int) {
        try {
            outputQueue.offer(payload)
        } catch (e: Exception) {}
    }

    private fun getSystemDns(): String? {
        return try {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val activeNetwork = cm.activeNetwork
                val lp = cm.getLinkProperties(activeNetwork)
                val dnsServers = lp?.dnsServers

                // FIXED: Cari IP DNS yang BUKAN merupakan IP lokal VPN kita sendiri (untuk mencegah LOOP)
                // Prioritaskan DNS ISP untuk performa terbaik
                val validDns = dnsServers?.firstOrNull {
                    val addr = it.hostAddress ?: ""
                    // Filter out VPN DNS dan localhost
                    addr != "10.0.0.2" && 
                    addr != "fd00::2" &&
                    addr != "127.0.0.1" && 
                    addr != "0.0.0.0" &&
                    !addr.startsWith("10.") // Filter private VPN ranges
                }
                
                // FIXED: Jika ada DNS ISP, gunakan. Jika tidak, gunakan DNS publik yang reliable
                val dnsToUse = validDns?.hostAddress 
                    ?: run {
                        // Fallback berurutan: 8.8.8.8 → 1.1.1.1
                        Log.w(TAG, "No valid ISP DNS found, using fallback")
                        "8.8.8.8"
                    }
                
                Log.d(TAG, "Using DNS: $dnsToUse")
                dnsToUse
            } else {
                "8.8.8.8"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting system DNS", e)
            "8.8.8.8"
        }
    }

    private fun sendSimpleDnsPacket(request: ByteBuffer, payload: ByteArray, ipHeaderLen: Int) {
        SimpleDnsParser.createResponsePacket(request, payload, ipHeaderLen).let { res -> outputQueue.offer(res) }
    }

    private fun getSourceIp(packet: ByteBuffer): String {
        val bytes = ByteArray(4); packet.position(12); packet.get(bytes)
        return InetAddress.getByAddress(bytes).hostAddress ?: "0.0.0.0"
    }

    private fun getDestIp(packet: ByteBuffer): String {
        val bytes = ByteArray(4); packet.position(16); packet.get(bytes)
        return InetAddress.getByAddress(bytes).hostAddress ?: "0.0.0.0"
    }

    private fun getUidForPort(port: Int, srcIp: String, dstIp: String): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
                return cm.getConnectionOwnerUid(android.system.OsConstants.IPPROTO_UDP, 
                    java.net.InetSocketAddress(InetAddress.getByName(srcIp), port), 
                    java.net.InetSocketAddress(InetAddress.getByName(dstIp), 53))
            } catch (e: Exception) {}
        }
        return -1
    }

    private fun getPackageNameFromUid(uid: Int): String {
        if (uid <= 0 || uid == 1000) return "system.service"
        return packageManager.getPackagesForUid(uid)?.firstOrNull() ?: "unknown.uid.$uid"
    }

    private fun getAppNameFromPackage(pkg: String): String {
        if (pkg == "system.service") return "Layanan Sistem"
        return try {
            val info = packageManager.getApplicationInfo(pkg, 0)
            packageManager.getApplicationLabel(info).toString()
        } catch (e: Exception) { pkg }
    }

    private fun updateNotificationCounter() {
        blockedCount++
        val now = System.currentTimeMillis()
        if (now - lastNotificationUpdate > 5000) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            try {
                if (executor?.isShutdown == false) {
                    executor?.execute { try { nm.notify(NOTIFICATION_ID, createNotification()) } catch (e: Exception) {} }
                }
            } catch (e: RejectedExecutionException) {}
            lastNotificationUpdate = now
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "ZeroAd Shield", NotificationManager.IMPORTANCE_LOW)
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val stopIntent = Intent(this, AdBlockVpnService::class.java).apply { action = ACTION_STOP }
        val stopPendingIntent = PendingIntent.getService(this, 0, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("ZeroAd Shield Aktif")
            .setContentText("$blockedCount Iklan & Pelacak Diblokir")
            .setSmallIcon(R.mipmap.launcher_icon)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "MATIKAN", stopPendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun stopVpn() {
        isRunning = false
        executor?.shutdown()
        outputExecutor?.shutdown()
        
        // Cleanup ZeroAd 2.0 engines
        tcpConnectionManager.cleanup()
        
        try { vpnInterface?.close() } catch (e: Exception) {}
        vpnInterface = null
        stopSelf()
    }
}
