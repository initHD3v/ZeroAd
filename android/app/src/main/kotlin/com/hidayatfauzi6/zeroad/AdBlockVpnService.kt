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

@Serializable
data class BypassListContainer(val bypass_packages: List<String>)

class AdBlockVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private var executor: ExecutorService? = null
    private var isRunning = false
    private var whitelistedApps: MutableSet<String> = mutableSetOf()
    private var whitelistedDomains: MutableSet<String> = mutableSetOf()
    private val blockedDomains = HashSet<String>()
    private lateinit var prefs: SharedPreferences
    
    // Notification State
    private var blockedCount = 0
    private var lastNotificationUpdate = 0L
    private val NOTIFICATION_ID = 1
    private val CHANNEL_ID = "ZeroAd_Shield_Channel"

    companion object {
        const val ACTION_START = "com.hidayatfauzi6.zeroad.START"
        const val ACTION_STOP = "com.hidayatfauzi6.zeroad.STOP"
        const val ACTION_UPDATE_WHITELIST = "com.hidayatfauzi6.zeroad.UPDATE_WHITELIST"
        private const val TAG = "ZeroAdService"
        
        // Format: Time|Domain|Type|Status|PackageName|AppName
        private val logBuffer = ConcurrentLinkedQueue<String>()

        fun addLog(log: String) {
            logBuffer.add(log)
            // Keep buffer size manageable (max 500)
            if (logBuffer.size > 500) {
                logBuffer.poll()
            }
            // Stream to Flutter real-time
            MainActivity.sendLogToFlutter(log)
        }

        fun getLogs(): List<String> {
            // Return a snapshot of current logs without clearing them immediately
            // This allows the UI to fetch all history reliably
            return logBuffer.toList().reversed() // Newest first
        }

        private val SYSTEM_WHITELIST = hashSetOf(
            "google.com", "googleapis.com", "gstatic.com", "googleusercontent.com",
            "whatsapp.net", "whatsapp.com", "facebook.com", "fbcdn.net",
            "instagram.com", "android.com", "play.google.com", "drive.google.com"
        )

        // Pre-cached DNS servers to avoid overhead
        private val UPSTREAM_DNS_SERVERS = listOf(
            InetAddress.getByAddress(byteArrayOf(1, 1, 1, 1)), // Cloudflare
            InetAddress.getByAddress(byteArrayOf(8, 8, 8, 8)), // Google
            InetAddress.getByAddress(byteArrayOf(9, 9, 9, 9))  // Quad9
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
                if (executor == null) executor = Executors.newFixedThreadPool(10)
                
                // Load Whitelist from Prefs first
                loadWhitelist()
                
                // INSTANT SYNC: If intent contains fresh whitelist, use it immediately
                val freshWhitelist = intent.getStringArrayListExtra("whitelisted_apps")
                if (freshWhitelist != null) {
                    whitelistedApps.addAll(freshWhitelist)
                    Log.d(TAG, "Instant Sync: Received ${freshWhitelist.size} apps via Intent")
                }
                
                loadBlocklistFromAssets()
                
                val notification = createNotification()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
                
                startVpn()
            }
            ACTION_UPDATE_WHITELIST -> loadWhitelist()
        }
        return START_STICKY
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "ZeroAd Shield Status"
            val descriptionText = "Menampilkan status perlindungan aktif"
            val importance = NotificationManager.IMPORTANCE_LOW 
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val stopIntent = Intent(this, AdBlockVpnService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val appIntent = packageManager.getLaunchIntentForPackage(packageName)
        val appPendingIntent = PendingIntent.getActivity(
            this, 0, appIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("ZeroAd Shield Aktif")
            .setContentText("$blockedCount Iklan & Pelacak Diblokir")
            .setSmallIcon(R.mipmap.launcher_icon)
            .setContentIntent(appPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "MATIKAN", stopPendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }
    
    private fun updateNotificationCounter() {
        blockedCount++
        val now = System.currentTimeMillis()
        if (now - lastNotificationUpdate > 2000) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(NOTIFICATION_ID, createNotification())
            lastNotificationUpdate = now
        }
    }

    private fun loadBlocklistFromAssets() {
        executor?.execute {
            try {
                val newBlockedDomains = HashSet<String>()
                assets.open("hosts.txt").bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        val trimmed = line.trim()
                        if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                            // Support "0.0.0.0 domain.com" or just "domain.com"
                            val parts = trimmed.split("\\s+".toRegex())
                            val domain = if (parts.size > 1) parts[1] else parts[0]
                            newBlockedDomains.add(domain.lowercase())
                        }
                    }
                }
                synchronized(blockedDomains) {
                    blockedDomains.clear()
                    blockedDomains.addAll(newBlockedDomains)
                }
                Log.d(TAG, "Loaded ${blockedDomains.size} domains from blocklist")
            } catch (e: Exception) {
                Log.e(TAG, "Error loading blocklist: $e")
            }
        }
    }

    private fun loadSystemBypassList(): List<String> {
        return try {
            val jsonString = assets.open("system_bypass.json").bufferedReader().use { it.readText() }
            val container = Json.decodeFromString<BypassListContainer>(jsonString)
            container.bypass_packages
        } catch (e: Exception) {
            Log.e(TAG, "Error loading bypass list", e)
            listOf()
        }
    }

    private fun loadWhitelist() {
        prefs = getSharedPreferences("ZeroAdPrefs", Context.MODE_PRIVATE)
        val savedList = prefs.getStringSet("whitelisted_apps", emptySet()) ?: emptySet()
        whitelistedApps.clear()
        whitelistedApps.addAll(savedList)
        Log.d(TAG, "Whitelist updated: ${whitelistedApps.size} apps")
    }

    private fun startVpn() {
        if (isRunning) return
        isRunning = true
        if (executor == null) executor = Executors.newFixedThreadPool(10)

        try {
            val builder = Builder()
            builder.setSession("ZeroAd Protection")
            builder.addAddress("10.0.0.2", 32)
            
            // Route DNS IPs specifically
            val targetDns = listOf(
                "8.8.8.8", "8.8.4.4", // Google
                "1.1.1.1", "1.0.0.1", // Cloudflare
                "9.9.9.9", "149.112.112.112", // Quad9
                "208.67.222.222", "208.67.220.220" // OpenDNS
            )
            for (ip in targetDns) {
                try { builder.addRoute(ip, 32) } catch (e: Exception) {}
            }
            
            builder.addDnsServer("8.8.8.8")
            builder.setMtu(1500)
            
            // 1. Apply System Bypass List from JSON
            val bypassApps = loadSystemBypassList()
            for (app in bypassApps) {
                try { builder.addDisallowedApplication(app) } catch (e: Exception) {
                    Log.w(TAG, "Could not bypass system app: $app")
                }
            }
            
            // 2. Apply User Whitelist (Direct Internet for Trusted Apps)
            for (app in whitelistedApps) {
                // Don't bypass ourselves and avoid empty strings
                if (app != packageName && app.isNotEmpty()) {
                    try { 
                        builder.addDisallowedApplication(app)
                        Log.d(TAG, "Dynamic Bypass applied for trusted app: $app")
                    } catch (e: Exception) {
                        Log.w(TAG, "Could not bypass trusted app: $app")
                    }
                }
            }

            vpnInterface = builder.establish()
            Log.i(TAG, "ZeroAd Started - Enhanced Mode")
            
            Thread { runLoop() }.start()

        } catch (e: Exception) {
            Log.e(TAG, "Error starting VPN", e)
            stopVpn()
        }
    }

    private fun runLoop() {
        val input = FileInputStream(vpnInterface!!.fileDescriptor)
        val output = FileOutputStream(vpnInterface!!.fileDescriptor)
        val buffer = ByteBuffer.allocate(32767)

        while (isRunning && vpnInterface != null) {
            try {
                val length = input.read(buffer.array())
                if (length > 0) {
                    val packet = ByteBuffer.wrap(buffer.array(), 0, length)
                    
                    if (isDnsPacket(packet)) {
                        val packetCopy = ByteArray(length)
                        System.arraycopy(buffer.array(), 0, packetCopy, 0, length)
                        
                        // Capture metadata needed for identification
                        val ipHeaderLen = (packet.get(0).toInt() and 0x0F) * 4
                        val srcPort = packet.getShort(ipHeaderLen).toInt() and 0xFFFF
                        val sourceIp = getSourceIp(packet)
                        val destIp = getDestIp(packet)
                        
                        executor?.execute {
                            handleDnsRequest(ByteBuffer.wrap(packetCopy), output, srcPort, sourceIp, destIp)
                        }
                    } 
                }
            } catch (e: Exception) {
                if (isRunning) Log.e(TAG, "Loop error", e)
            }
        }
    }

    private fun isDnsPacket(packet: ByteBuffer): Boolean {
        val version = (packet.get(0).toInt() shr 4) and 0xF
        if (version != 4) return false
        val protocol = packet.get(9).toInt() and 0xFF
        if (protocol != 17) return false // UDP Only
        
        val ipHeaderLen = (packet.get(0).toInt() and 0x0F) * 4
        val dstPort = packet.getShort(ipHeaderLen + 2).toInt() and 0xFFFF
        return dstPort == 53
    }
    
    private fun getSourceIp(packet: ByteBuffer): String {
        val bytes = ByteArray(4)
        packet.position(12)
        packet.get(bytes)
        return InetAddress.getByAddress(bytes).hostAddress ?: "0.0.0.0"
    }

    private fun getDestIp(packet: ByteBuffer): String {
        val bytes = ByteArray(4)
        packet.position(16)
        packet.get(bytes)
        return InetAddress.getByAddress(bytes).hostAddress ?: "0.0.0.0"
    }

    private fun handleDnsRequest(packet: ByteBuffer, output: FileOutputStream, srcPort: Int, sourceIp: String, destIp: String) {
        try {
            val dnsInfo = SimpleDnsParser.parse(packet) ?: return
            
            // Identify App
            val uid = getUidForPort(srcPort, sourceIp, destIp)
            val appPackage = getPackageNameFromUid(uid)
            val appName = getAppNameFromPackage(appPackage)
            
            // 1. Check App Whitelist (Fuzzy Match for sub-processes)
            val isWhitelistedApp = whitelistedApps.any { appPackage.startsWith(it) }
            
            if (isWhitelistedApp) {
                 val responsePayload = forwardQuery(dnsInfo.payload)
                if (responsePayload != null) {
                    addLog("${System.currentTimeMillis()}|${dnsInfo.domain}|APP_WHITELISTED|ALLOWED|$appPackage|$appName")
                    val fullResponse = SimpleDnsParser.createResponsePacket(packet, responsePayload)
                    safeWrite(output, fullResponse)
                }
                return
            }

            // 2. Check Blacklist / Domain Whitelist
            if (isAd(dnsInfo.domain)) {
                Log.d(TAG, "BLOCKING: ${dnsInfo.domain} from $appName ($appPackage)")
                addLog("${System.currentTimeMillis()}|${dnsInfo.domain}|AD_CONTENT|BLOCKED|$appPackage|$appName")
                updateNotificationCounter() // Update Smart Notification
                val response = SimpleDnsParser.createNxDomainResponse(packet)
                safeWrite(output, response)
            } else {
                val responsePayload = forwardQuery(dnsInfo.payload)
                if (responsePayload != null) {
                    // Smart Logging: Record allowed traffic so apps appear in Activity Tab
                    addLog("${System.currentTimeMillis()}|${dnsInfo.domain}|DNS_QUERY|ALLOWED|$appPackage|$appName")
                    val fullResponse = SimpleDnsParser.createResponsePacket(packet, responsePayload)
                    safeWrite(output, fullResponse)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "DNS Handle Error", e)
        }
    }

    private fun safeWrite(output: FileOutputStream, data: ByteArray) {
        try {
            if (isRunning && vpnInterface != null) {
                synchronized(output) {
                    output.write(data)
                }
            }
        } catch (e: Exception) {
            // Silence EBADF errors during restarts as they are expected
            // Log.w(TAG, "Write skipped: Interface closing")
        }
    }

    // --- APP IDENTIFICATION LOGIC ---
    
    private fun getUidForPort(port: Int, sourceIp: String, destIp: String): Int {
        // Try up to 3 times with a tiny delay to overcome OS race condition
        for (attempt in 1..3) {
            // Method 1: Android Q+ ConnectivityManager (SELinux Safe)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                try {
                    val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
                    val local = java.net.InetSocketAddress(InetAddress.getByName(sourceIp), port)
                    val remote = java.net.InetSocketAddress(InetAddress.getByName(destIp), 53)
                    
                    val uid = cm.getConnectionOwnerUid(OsConstants.IPPROTO_UDP, local, remote)
                    if (uid != android.os.Process.INVALID_UID) {
                        return uid
                    }
                } catch (e: Exception) { }
            }

            // Method 2: Fallback to /proc/net scanning
            val files = listOf("/proc/net/udp", "/proc/net/udp6")
            for (filePath in files) {
                try {
                    val file = File(filePath)
                    if (!file.exists()) continue
                    
                    BufferedReader(FileReader(file)).use { reader ->
                        reader.readLine() // Skip header
                        var line = reader.readLine()
                        while (line != null) {
                            val parts = line.trim().split("\\s+".toRegex())
                            if (parts.size >= 10) {
                                val localAddress = parts[1]
                                val uidStr = parts[7]
                                
                                val addrParts = localAddress.split(":")
                                if (addrParts.size >= 2) {
                                    val portHex = addrParts[addrParts.size - 1]
                                    val currentPort = Integer.parseInt(portHex, 16)
                                    
                                    if (currentPort == port) {
                                        return uidStr.toInt()
                                    }
                                }
                            }
                            line = reader.readLine()
                        }
                    }
                } catch (e: Exception) { }
            }
            
            // If not found, wait 5ms and try again
            if (attempt < 3) try { Thread.sleep(5) } catch (e: Exception) {}
        }
        return -1
    }
    
    private fun getPackageNameFromUid(uid: Int): String {
        if (uid == -1 || uid == 0 || uid == 1000) return "com.android.system"
        val packages = packageManager.getPackagesForUid(uid)
        return if (!packages.isNullOrEmpty()) packages[0] else "system.uid.$uid"
    }
    
    private fun getAppNameFromPackage(packageName: String): String {
        if (packageName == "com.android.system") return "Sistem Android"
        return try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            if (packageName.startsWith("system.uid.")) "Layanan Sistem" else packageName
        }
    }

    private fun isAd(domain: String): Boolean {
        val lowerDomain = domain.lowercase()
        
        // 1. Check SYSTEM_WHITELIST (Efficient suffix check)
        if (SYSTEM_WHITELIST.any { lowerDomain == it || lowerDomain.endsWith(".$it") }) {
            return false
        }

        // 2. Check User Domain Whitelist
        if (whitelistedDomains.any { lowerDomain == it || lowerDomain.endsWith(".$it") }) {
            return false
        }
        
        // 3. Check blockedDomains HashSet
        synchronized(blockedDomains) {
            // Check exact match
            if (blockedDomains.contains(lowerDomain)) return true
            
            // Check subdomain match (e.g. ads.google.com -> google.com)
            var parent = lowerDomain
            while (parent.contains(".")) {
                parent = parent.substringAfter(".")
                if (blockedDomains.contains(parent)) return true
            }
        }
        
        return false
    }

    private fun forwardQuery(payload: ByteArray): ByteArray? {
        val socket = DatagramSocket()
        protect(socket)
        socket.soTimeout = 1500 // Snappier timeout

        val outPacket = DatagramPacket(payload, payload.size)
        val inData = ByteArray(1500)
        val inPacket = DatagramPacket(inData, inData.size)

        for (address in UPSTREAM_DNS_SERVERS) {
            try {
                outPacket.address = address
                outPacket.port = 53
                socket.send(outPacket)
                socket.receive(inPacket)
                
                socket.close()
                return inData.copyOf(inPacket.length)
            } catch (e: Exception) {
                continue
            }
        }
        socket.close()
        return null
    }

    private fun stopVpn() {
        isRunning = false
        executor?.shutdown()
        try { vpnInterface?.close() } catch (e: Exception) {}
        vpnInterface = null
        stopSelf()
    }
}
