package com.hidayatfauzi6.zeroad

import android.content.Intent
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.system.OsConstants
import android.util.Log
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.FileReader
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.Inet4Address
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.Timer
import java.util.TimerTask
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.LinkedBlockingQueue

@Serializable
data class BypassListContainer(val bypass_packages: List<String>)

class AdBlockVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private var executor: ExecutorService? = null
    private var outputExecutor: ExecutorService? = null
    private val outputQueue = LinkedBlockingQueue<ByteArray>(1000)
    private var isRunning = false
    private var whitelistedApps: MutableSet<String> = mutableSetOf()
    private var autonomousEssentialApps: MutableSet<String> = mutableSetOf()
    private var whitelistedDomains: MutableSet<String> = mutableSetOf()
    private val autoWhitelistedDomains = HashSet<String>()
    private val blockedDomains = HashSet<String>()
    private lateinit var prefs: SharedPreferences
    
    private val packageKeywordMap = ConcurrentHashMap<String, String>()
    private val dnsCache = ConcurrentHashMap<String, Pair<ByteArray, Long>>()
    private val CACHE_TTL = 300000L
    
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
        private val lastLogTimestamp = ConcurrentHashMap<String, Long>()

        fun addLog(log: String) {
            val parts = log.split("|")
            if (parts.size >= 5) {
                val key = "${parts[1]}_${parts[4]}_${parts[3]}"
                val now = System.currentTimeMillis()
                val lastTime = lastLogTimestamp[key] ?: 0L
                if (now - lastTime < 2000) return
                lastLogTimestamp[key] = now
            }
            logBuffer.add(log)
            if (logBuffer.size > 500) logBuffer.poll()
            MainActivity.sendLogToFlutter(log)
        }

        fun getLogs(): List<String> { return logBuffer.toList().reversed() }

        private val SYSTEM_WHITELIST = hashSetOf(
            "google.com", "googleapis.com", "gstatic.com", "googleusercontent.com",
            "whatsapp.net", "whatsapp.com", "facebook.com", "fbcdn.net",
            "instagram.com", "android.com", "play.google.com", "drive.google.com",
            "github.com", "githubusercontent.com", "adjust.com", "adjust.world", "adjust.in"
        )

        private val UPSTREAM_DNS_SERVERS = listOf(
            InetAddress.getByName("1.1.1.1"),
            InetAddress.getByName("8.8.8.8"),
            InetAddress.getByName("9.9.9.9")
        )

        private val DOH_IPS = hashSetOf(
            "1.1.1.1", "1.0.0.1", "8.8.8.8", "8.8.4.4", "9.9.9.9", 
            "149.112.112.112", "208.67.222.222", "208.67.220.220",
            "94.140.14.14", "94.140.15.15", "45.90.28.0", "45.90.30.0"
        )
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopVpn()
            ACTION_START -> {
                if (executor == null) executor = Executors.newFixedThreadPool(15)
                if (outputExecutor == null) outputExecutor = Executors.newSingleThreadExecutor()
                
                Timer().scheduleAtFixedRate(object : TimerTask() {
                    override fun run() {
                        val now = System.currentTimeMillis()
                        dnsCache.entries.removeIf { now - it.value.second > CACHE_TTL }
                    }
                }, 600000, 600000)

                loadWhitelist()
                
                val essentialApps = intent.getStringArrayListExtra("essential_apps")
                if (essentialApps != null) {
                    autonomousEssentialApps.clear()
                    autonomousEssentialApps.addAll(essentialApps)
                    Log.d(TAG, "Cerdas: Autonomous trust built for ${autonomousEssentialApps.size} apps")
                }

                executor?.execute {
                    try {
                        val apps = packageManager.getInstalledPackages(0)
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
                
                val freshWhitelist = intent.getStringArrayListExtra("whitelisted_apps")
                if (freshWhitelist != null) whitelistedApps.addAll(freshWhitelist)
                
                loadBlocklistFromAssets()
                
                val notification = createNotification()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
                
                startVpn()
            }
            ACTION_UPDATE_WHITELIST -> {
                loadWhitelist()
                loadBlocklistFromAssets()
            }
        }
        return START_STICKY
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "ZeroAd Shield Status", NotificationManager.IMPORTANCE_LOW)
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val stopIntent = Intent(this, AdBlockVpnService::class.java).apply { action = ACTION_STOP }
        val stopPendingIntent = PendingIntent.getService(this, 0, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val appIntent = packageManager.getLaunchIntentForPackage(packageName)
        val appPendingIntent = PendingIntent.getActivity(this, 0, appIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("ZeroAd Shield Aktif")
            .setContentText("$blockedCount Iklan & Pelacak Diblokir")
            .setSmallIcon(R.mipmap.launcher_icon)
            .setContentIntent(appPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "MATIKAN", stopPendingIntent)
            .setOngoing(true)
            .build()
    }
    
    private fun updateNotificationCounter() {
        blockedCount++
        val now = System.currentTimeMillis()
        if (now - lastNotificationUpdate > 5000) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            executor?.execute {
                try {
                    notificationManager.notify(NOTIFICATION_ID, createNotification())
                } catch (e: Exception) {}
            }
            lastNotificationUpdate = now
        }
    }

    private fun loadBlocklistFromAssets() {
        executor?.execute {
            try {
                val newBlockedDomains = HashSet<String>()
                assets.open("hosts.txt").bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        val parts = line.trim().split("\\s+".toRegex())
                        val domain = if (parts.size > 1) parts[1] else parts[0]
                        if (domain.isNotEmpty() && !domain.startsWith("#")) newBlockedDomains.add(domain.lowercase())
                    }
                }
                val dynamicPath = prefs.getString("dynamic_blocklist_path", null)
                if (dynamicPath != null) {
                    val dynamicFile = File(dynamicPath)
                    if (dynamicFile.exists()) {
                        dynamicFile.bufferedReader().useLines { lines ->
                            lines.forEach { line ->
                                val domain = line.trim()
                                if (domain.isNotEmpty()) newBlockedDomains.add(domain.lowercase())
                            }
                        }
                    }
                }
                synchronized(blockedDomains) {
                    blockedDomains.clear()
                    blockedDomains.addAll(newBlockedDomains)
                }
                val autoWhitePath = prefs.getString("auto_whitelist_path", null)
                if (autoWhitePath != null) {
                    val whiteFile = File(autoWhitePath)
                    if (whiteFile.exists()) {
                        val newAutoWhite = HashSet<String>()
                        whiteFile.bufferedReader().useLines { lines ->
                            lines.forEach { if (it.isNotEmpty()) newAutoWhite.add(it.lowercase()) }
                        }
                        synchronized(autoWhitelistedDomains) {
                            autoWhitelistedDomains.clear()
                            autoWhitelistedDomains.addAll(newAutoWhite)
                        }
                    }
                }
            } catch (e: Exception) {}
        }
    }

    private fun loadSystemBypassList(): List<String> {
        return try {
            val jsonString = assets.open("system_bypass.json").bufferedReader().use { it.readText() }
            val container = Json.decodeFromString<BypassListContainer>(jsonString)
            container.bypass_packages
        } catch (e: Exception) { listOf() }
    }

    private fun loadWhitelist() {
        prefs = getSharedPreferences("ZeroAdPrefs", Context.MODE_PRIVATE)
        val savedList = prefs.getStringSet("whitelisted_apps", emptySet()) ?: emptySet()
        whitelistedApps.clear()
        whitelistedApps.addAll(savedList)
    }

    private fun startVpn() {
        if (isRunning) return
        isRunning = true
        try {
            val builder = Builder()
            builder.setSession("ZeroAd Protection")
            builder.addAddress("10.0.0.2", 32)
            
            // Izinkan trafik sistem & lokal bypass tunnel untuk Wireless Debugging
            builder.allowBypass() 
            
            DOH_IPS.forEach { try { builder.addRoute(it, 32) } catch (e: Exception) {} }
            builder.addDnsServer("8.8.8.8")
            builder.setMtu(1500)
            
            // --- SOLUSI ABSOLUT: SYSTEM-LEVEL BYPASS ---
            // Aplikasi di bawah ini akan mendapatkan "Internet Asli dari ISP"
            // Mereka tidak akan melewati tunnel Zeroad sama sekali.
            
            // 1. Bypass Zeroad sendiri
            try { builder.addDisallowedApplication(packageName) } catch (e: Exception) {}
            
            // 2. Bypass Aplikasi Esensial (E-commerce, Bank, dll yang dideteksi otomatis)
            autonomousEssentialApps.forEach { 
                try { 
                    builder.addDisallowedApplication(it)
                    Log.d(TAG, "Bypass Aktif (ISP Murni): $it")
                } catch (e: Exception) {} 
            }
            
            // 3. Bypass Whitelist Manual dari User
            whitelistedApps.forEach { 
                if (it != packageName && it.isNotEmpty()) {
                    try { builder.addDisallowedApplication(it) } catch (e: Exception) {} 
                }
            }

            // 4. Bypass Layanan Sistem Penting
            loadSystemBypassList().forEach { try { builder.addDisallowedApplication(it) } catch (e: Exception) {} }

            vpnInterface = builder.establish()
            
            outputExecutor?.execute {
                val output = FileOutputStream(vpnInterface!!.fileDescriptor)
                while (isRunning && vpnInterface != null) {
                    try {
                        val packet = outputQueue.poll(500, TimeUnit.MILLISECONDS)
                        if (packet != null) synchronized(output) { output.write(packet) }
                    } catch (e: Exception) { if (isRunning) Log.e(TAG, "Output queue error", e) }
                }
            }
            Thread { runLoop() }.start()
        } catch (e: Exception) { stopVpn() }
    }

    private fun runLoop() {
        val input = FileInputStream(vpnInterface!!.fileDescriptor)
        val buffer = ByteBuffer.allocate(32767)
        while (isRunning && vpnInterface != null) {
            try {
                val length = input.read(buffer.array())
                if (length > 0) {
                    val packet = ByteBuffer.wrap(buffer.array(), 0, length)
                    if (isDnsPacket(packet)) {
                        val packetCopy = ByteArray(length); System.arraycopy(buffer.array(), 0, packetCopy, 0, length)
                        val ipHeaderLen = (packet.get(0).toInt() and 0x0F) * 4
                        val srcPort = packet.getShort(ipHeaderLen).toInt() and 0xFFFF
                        val sourceIp = getSourceIp(packet); val destIp = getDestIp(packet)
                        executor?.execute { handleDnsRequest(ByteBuffer.wrap(packetCopy), srcPort, sourceIp, destIp) }
                    } else if (isDohPacket(packet)) {
                        val destIp = getDestIp(packet)
                        addLog("${System.currentTimeMillis()}|$destIp|DOH_BYPASS|INTERCEPTED|system|System Force Fallback")
                    }
                }
            } catch (e: Exception) { if (isRunning) Log.e(TAG, "Loop error", e) }
        }
    }

    private fun isDnsPacket(packet: ByteBuffer): Boolean {
        val protocol = packet.get(9).toInt() and 0xFF
        if (protocol != 17) return false
        val ipHeaderLen = (packet.get(0).toInt() and 0x0F) * 4
        return (packet.getShort(ipHeaderLen + 2).toInt() and 0xFFFF) == 53
    }

    private fun isDohPacket(packet: ByteBuffer): Boolean {
        val protocol = packet.get(9).toInt() and 0xFF
        if (protocol != 6) return false
        val ipHeaderLen = (packet.get(0).toInt() and 0x0F) * 4
        val dstPort = packet.getShort(ipHeaderLen + 2).toInt() and 0xFFFF
        if (dstPort != 443) return false
        return DOH_IPS.contains(getDestIp(packet))
    }
    
    private fun getSourceIp(packet: ByteBuffer): String {
        val bytes = ByteArray(4); packet.position(12); packet.get(bytes)
        return InetAddress.getByAddress(bytes).hostAddress ?: "0.0.0.0"
    }

    private fun getDestIp(packet: ByteBuffer): String {
        val bytes = ByteArray(4); packet.position(16); packet.get(bytes)
        return InetAddress.getByAddress(bytes).hostAddress ?: "0.0.0.0"
    }

    private fun handleDnsRequest(packet: ByteBuffer, srcPort: Int, sourceIp: String, destIp: String) {
        try {
            val dnsInfo = SimpleDnsParser.parse(packet) ?: return
            val domain = dnsInfo.domain.lowercase()
            
            val cachedResponse = dnsCache[domain]
            if (cachedResponse != null && (System.currentTimeMillis() - cachedResponse.second < CACHE_TTL)) {
                sendSimpleDnsPacket(packet, cachedResponse.first); return
            }

            val uid = getUidForPort(srcPort, sourceIp, destIp)
            val appPackage = getPackageNameFromUid(uid, domain)
            val appName = getAppNameFromPackage(appPackage)
            
            // 1. Identifikasi Aplikasi Esensial (Shopping, Finance, Maps)
            val isEssentialApp = autonomousEssentialApps.contains(appPackage)
            
            val isSystemOrVendor = appPackage.startsWith("com.android.") || 
                                  appPackage.startsWith("com.google.android.") ||
                                  appPackage.startsWith("com.miui.") ||
                                  appPackage.startsWith("com.xiaomi.")

            // --- STRATEGI FINAL: ESSENTIAL APP BYPASS ---
            // Jika aplikasi adalah E-commerce/Bank/Ojol -> IZINKAN SEMUA (Kecuali Malware)
            // Ini adalah satu-satunya cara menjamin 100% kompatibilitas tanpa menebak-nebak domain tracker.
            if (isEssentialApp || isSystemOrVendor || whitelistedApps.any { appPackage.startsWith(it) }) {
                forwardQueryParallel(dnsInfo.payload)?.let { resPayload ->
                    dnsCache[domain] = Pair(resPayload, System.currentTimeMillis())
                    addLog("${System.currentTimeMillis()}|$domain|TRUSTED_APP_BYPASS|ALLOWED|$appPackage|$appName")
                    sendSimpleDnsPacket(packet, resPayload)
                }
                return
            }

            // 2. Untuk aplikasi NON-Esensial (Game, Browser, Sosmed), terapkan filter iklan ketat
            if (isAd(domain)) {
                addLog("${System.currentTimeMillis()}|$domain|AD_CONTENT|BLOCKED|$appPackage|$appName")
                updateNotificationCounter()
                SimpleDnsParser.createNullIpResponse(packet).let { res -> outputQueue.offer(res) }
            } else {
                forwardQueryParallel(dnsInfo.payload)?.let { resPayload ->
                    dnsCache[domain] = Pair(resPayload, System.currentTimeMillis())
                    addLog("${System.currentTimeMillis()}|$domain|DNS_QUERY|ALLOWED|$appPackage|$appName")
                    sendSimpleDnsPacket(packet, resPayload)
                }
            }
        } catch (e: Exception) {}
    }

    private fun sendSimpleDnsPacket(request: ByteBuffer, payload: ByteArray) {
        SimpleDnsParser.createResponsePacket(request, payload).let { res -> outputQueue.offer(res) }
    }

    private fun isAd(domain: String): Boolean {
        val ld = domain.lowercase()
        if (SYSTEM_WHITELIST.any { ld == it || ld.endsWith(".$it") }) return false
        if (whitelistedDomains.any { ld == it || ld.endsWith(".$it") }) return false
        synchronized(autoWhitelistedDomains) { if (autoWhitelistedDomains.contains(ld)) return false }
        synchronized(blockedDomains) {
            if (blockedDomains.contains(ld)) return true
            var p = ld
            while (p.contains(".")) { p = p.substringAfter("."); if (blockedDomains.contains(p)) return true }
        }
        return false
    }

    private fun forwardQueryParallel(payload: ByteArray): ByteArray? {
        val results = LinkedBlockingQueue<ByteArray>()
        val sockets = CopyOnWriteArrayList<DatagramSocket>()
        val latch = CountDownLatch(1)
        for (addr in UPSTREAM_DNS_SERVERS) {
            executor?.execute {
                var socket: DatagramSocket? = null
                try {
                    socket = DatagramSocket()
                    protect(socket)
                    socket.soTimeout = 2000
                    sockets.add(socket)
                    val out = DatagramPacket(payload, payload.size, addr, 53)
                    socket.send(out)
                    val inData = ByteArray(1500); val inP = DatagramPacket(inData, inData.size)
                    socket.receive(inP)
                    results.offer(inData.copyOf(inP.length))
                    latch.countDown()
                } catch (e: Exception) {}
            }
        }
        return try {
            latch.await(2500, TimeUnit.MILLISECONDS)
            results.poll()
        } catch (e: Exception) { null }
        finally { for (s in sockets) { try { if (!s.isClosed) s.close() } catch (e: Exception) {} } }
    }

    private fun getUidForPort(port: Int, sourceIp: String, destIp: String): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
                val uid = cm.getConnectionOwnerUid(OsConstants.IPPROTO_UDP, java.net.InetSocketAddress(InetAddress.getByName(sourceIp), port), java.net.InetSocketAddress(InetAddress.getByName(destIp), 53))
                if (uid != android.os.Process.INVALID_UID) return uid
            } catch (e: Exception) {}
        }
        val files = listOf("/proc/net/udp", "/proc/net/udp6")
        for (f in files) {
            try {
                File(f).bufferedReader().use { r ->
                    r.readLine()
                    var l = r.readLine()
                    while (l != null) {
                        val p = l.trim().split("\\s+".toRegex())
                        if (p.size >= 10 && Integer.parseInt(p[1].split(":")[1], 16) == port) return p[7].toInt()
                        l = r.readLine()
                    }
                }
            } catch (e: Exception) {}
        }
        return -1
    }

    private fun getPackageNameFromUid(uid: Int, domain: String): String {
        if (uid == -1 || uid == 0 || uid == 1000 || uid == 1051) {
            findPackageFromDomain(domain)?.let { return it }
            return "com.android.system"
        }
        return packageManager.getPackagesForUid(uid)?.firstOrNull() ?: "system.uid.$uid"
    }

    private fun findPackageFromDomain(domain: String): String? {
        if (packageKeywordMap.isEmpty()) return null
        val parts = domain.lowercase().split(".").filter { it.length > 3 }
        for (p in parts) { packageKeywordMap[p]?.let { return it } }
        return null
    }
    
    private fun getAppNameFromPackage(packageName: String): String {
        if (packageName == "com.android.system") return "Layanan Sistem"
        return try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) { if (packageName.startsWith("system.uid.")) "Layanan Sistem" else packageName }
    }

    private fun stopVpn() {
        isRunning = false
        executor?.shutdown()
        outputExecutor?.shutdown()
        try { vpnInterface?.close() } catch (e: Exception) {}
        vpnInterface = null
        stopSelf()
    }
}
