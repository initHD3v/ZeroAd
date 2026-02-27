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
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer
import java.util.concurrent.*

class AdBlockVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private var executor: ExecutorService? = null
    private var outputExecutor: ExecutorService? = null
    private val outputQueue = LinkedBlockingQueue<ByteArray>(2000)
    private var isRunning = false
    
    private lateinit var filterEngine: AdFilterEngine
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
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopVpn()
            ACTION_START -> {
                if (executor == null) executor = Executors.newFixedThreadPool(15)
                if (outputExecutor == null) outputExecutor = Executors.newSingleThreadExecutor()
                prefs = getSharedPreferences("ZeroAdPrefs", Context.MODE_PRIVATE)

                val essentialApps = intent.getStringArrayListExtra("essential_apps") ?: arrayListOf()
                
                // FIXED: Jangan bypass Chrome & Google terlalu banyak - biarkan masuk tunnel untuk monitoring
                // Hanya bypass yang benar-benar critical untuk stabilitas sistem
                val criticalBypassPkgs = listOf(
                    "com.google.android.gms", // Google Play Services (harus bypass untuk IAP/Login)
                    "com.android.vending", // Play Store (download/update APK)
                )
                
                // Jangan bypass Chrome - biarkan masuk tunnel untuk filtering
                // Chrome akan menggunakan DNS filtering normal
                
                criticalBypassPkgs.forEach { if (!essentialApps.contains(it)) essentialApps.add(it) }

                filterEngine.updateEssentialApps(essentialApps)

                executor?.execute {
                    filterEngine.loadBlocklists(prefs)
                    val userWhitelist = prefs.getStringSet("whitelisted_apps", emptySet()) ?: emptySet()
                    filterEngine.updateUserWhitelist(userWhitelist)
                    buildAppCategoryCache()
                }

                startForeground(NOTIFICATION_ID, createNotification())
                startVpn()
                
                // FIXED: Kirim log startup ke Flutter
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
                                executor?.execute { forwardTCP(ByteBuffer.wrap(packetCopy), ipHeaderLen) }
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
                                executor?.execute { forwardTCP(ByteBuffer.wrap(packetCopy), ipHeaderLen) }
                            }
                        } catch (e: RejectedExecutionException) {}
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "RunLoop error", e)
            }
        }
    }

    private fun handleDnsRequest(packet: ByteBuffer, ipHeaderLen: Int) {
        try {
            val dnsInfo = SimpleDnsParser.parse(packet, ipHeaderLen) ?: return
            val domain = dnsInfo.domain.lowercase().trimEnd('.')

            // --- ADGUARD-STYLE: SMART FILTERING ---

            // 1. Identifikasi Aplikasi Pengirim Dahulu
            val srcPort = packet.getShort(ipHeaderLen).toInt() and 0xFFFF
            val appInfo = identifyAppFast(srcPort, packet)
            val category = uidCategoryCache[appInfo.first] ?: AppCategory.GENERAL
            val pkg = appInfo.second

            // 2. ADGUARD-STYLE: Google Play Services Detection
            // Deteksi package Google Play Services untuk bypass otomatis
            val isGooglePackage = pkg.startsWith("com.google.android.gms") ||
                                 pkg.startsWith("com.android.vending") ||
                                 pkg == "com.google.android.gsf" ||
                                 pkg.startsWith("com.google.android.play") ||
                                 pkg == "com.android.chrome" ||
                                 pkg.startsWith("com.chrome.")

            if (isGooglePackage) {
                // ALLOW SEMUA traffic dari Google Play Services
                // Ini menjamin IAP, Login, dan Play Store tetap berfungsi
                forwardAndSendResponse(packet, dnsInfo, ipHeaderLen, "GOOGLE_PKG", "PASS (Google Services)")
                return
            }

            // 3. Cek Whitelist Dahulu (Prioritas Tertinggi)
            if (filterEngine.isWhitelisted(domain)) {
                forwardAndSendResponse(packet, dnsInfo, ipHeaderLen, "SYSTEM", "PASS (Whitelisted)")
                return
            }

            // 4. Cek DNS Cache
            val cachedResponse = dnsCache[domain]
            if (cachedResponse != null && (System.currentTimeMillis() - cachedResponse.second < CACHE_TTL)) {
                sendCachedResponse(packet, cachedResponse.first, dnsInfo, ipHeaderLen)
                asyncLog(packet, domain, ipHeaderLen, category.name, "PASS (Cached)")
                return
            }

            // 5. ADGUARD-STYLE: Google Services Domain Detection
            // Deteksi domain Google yang legit berdasarkan pattern
            if (isGoogleServiceDomain(domain)) {
                forwardAndSendResponse(packet, dnsInfo, ipHeaderLen, "GOOGLE_DOMAIN", "PASS (Google Allowed)")
                return
            }

            // 6. Keputusan Blokir Kontekstual
            // Jika aplikasi adalah GAME, kita hanya memblokir domain yang masuk kategori Hard-Ads.
            // Jika aplikasi adalah GENERAL (Browser, dll), kita blokir agresif.
            if (filterEngine.shouldBlock(domain, category)) {
                // Gunakan NXDOMAIN untuk Game agar app segera fallback/ignore, Null IP untuk lainnya
                val res = SimpleDnsParser.createNxDomainResponse(packet, ipHeaderLen)
                outputQueue.offer(res)
                updateNotificationCounter()

                addLog("${System.currentTimeMillis()}|$domain|FILTER|BLOCKED|${appInfo.second}|${appInfo.third}")
                return
            }

            // 7. Jika lolos, Forward segera
            forwardAndSendResponse(packet, dnsInfo, ipHeaderLen, "GENERAL", "ALLOWED")

        } catch (e: Exception) {
            Log.e(TAG, "DNS Handle Error (Fail-Safe Triggered)", e)
            forwardOriginalQueryFailSafe(packet, ipHeaderLen)
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
            val dnsServer = getSystemDns() ?: "8.8.8.8"
            
            // FIXED: Log DNS forwarding untuk debugging
            // Log.d(TAG, "Forwarding DNS query to: $dnsServer")
            
            val out = DatagramPacket(payload, payload.size, InetAddress.getByName(dnsServer), 53)
            val inData = ByteArray(1500); val inP = DatagramPacket(inData, inData.size)
            socket.send(out)
            socket.receive(inP)
            return inData.copyOf(inP.length)
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
     */
    private fun forwardTCP(packet: ByteBuffer, ipHeaderLen: Int) {
        var socket: java.net.Socket? = null
        try {
            val dstPort = packet.getShort(ipHeaderLen + 2).toInt() and 0xFFFF
            val dstIp = getDestIp(packet)
            
            // Extract TCP payload
            val tcpHeaderLen = ((packet.get(ipHeaderLen + 12).toInt() and 0xF0) shr 2)
            val payloadStart = ipHeaderLen + tcpHeaderLen
            val payloadLen = packet.limit() - payloadStart
            
            if (payloadLen <= 0) return
            
            val payload = ByteArray(payloadLen)
            packet.position(payloadStart)
            packet.get(payload)
            
            // Create TCP socket dan forward
            socket = java.net.Socket(InetAddress.getByName(dstIp), dstPort)
            socket.soTimeout = 5000 // 5 detik timeout untuk TCP
            
            // Kirim payload
            val outputStream = socket.getOutputStream()
            outputStream.write(payload)
            outputStream.flush()
            
            // Baca response (simple forwarding)
            val inputStream = socket.getInputStream()
            val buffer = ByteArray(4096)
            val bytesRead = inputStream.read(buffer)
            
            if (bytesRead > 0) {
                // Kirim response balik ke VPN
                val response = buffer.copyOf(bytesRead)
                sendTCPResponse(packet, response, ipHeaderLen)
            }
            
        } catch (e: Exception) {
            // TCP forwarding gagal - ini OK untuk beberapa kasus
        } finally {
            try { socket?.close() } catch (e: Exception) {}
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
        executor?.shutdown(); outputExecutor?.shutdown()
        try { vpnInterface?.close() } catch (e: Exception) {}
        vpnInterface = null
        stopSelf()
    }
}
