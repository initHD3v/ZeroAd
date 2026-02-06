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
        
        private val logBuffer = ConcurrentLinkedQueue<String>()

        fun addLog(log: String) {
            logBuffer.add(log)
            if (logBuffer.size > 500) {
                logBuffer.poll()
            }
            MainActivity.sendLogToFlutter(log)
        }

        fun getLogs(): List<String> {
            return logBuffer.toList().reversed()
        }

        private val SYSTEM_WHITELIST = hashSetOf(
            "google.com", "googleapis.com", "gstatic.com", "googleusercontent.com",
            "whatsapp.net", "whatsapp.com", "facebook.com", "fbcdn.net",
            "instagram.com", "android.com", "play.google.com", "drive.google.com"
        )

        private val UPSTREAM_DNS_SERVERS = listOf(
            InetAddress.getByAddress(byteArrayOf(1, 1, 1, 1)),
            InetAddress.getByAddress(byteArrayOf(8, 8, 8, 8)),
            InetAddress.getByAddress(byteArrayOf(9, 9, 9, 9))
        )
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    private var cachedInstalledApps: List<String> = listOf()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopVpn()
            ACTION_START -> {
                if (executor == null) executor = Executors.newFixedThreadPool(10)
                loadWhitelist()
                
                // Pre-load installed packages to avoid Binder Flooding in DNS loop
                executor?.execute {
                    try {
                        cachedInstalledApps = packageManager.getInstalledPackages(0).map { it.packageName }
                        Log.d(TAG, "Pre-loaded ${cachedInstalledApps.size} apps for fast lookup")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to pre-load apps", e)
                    }
                }
                
                val freshWhitelist = intent.getStringArrayListExtra("whitelisted_apps")
                if (freshWhitelist != null) {
                    whitelistedApps.addAll(freshWhitelist)
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
            val importance = NotificationManager.IMPORTANCE_LOW 
            val channel = NotificationChannel(CHANNEL_ID, name, importance)
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
                        val parts = line.trim().split("\\s+".toRegex())
                        val domain = if (parts.size > 1) parts[1] else parts[0]
                        if (domain.isNotEmpty() && !domain.startsWith("#")) newBlockedDomains.add(domain.lowercase())
                    }
                }
                synchronized(blockedDomains) {
                    blockedDomains.clear()
                    blockedDomains.addAll(newBlockedDomains)
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
            
            listOf("8.8.8.8", "8.8.4.4", "1.1.1.1", "1.0.0.1", "9.9.9.9").forEach {
                try { builder.addRoute(it, 32) } catch (e: Exception) {}
            }
            
            builder.addDnsServer("8.8.8.8")
            builder.setMtu(1500)
            
            loadSystemBypassList().forEach { try { builder.addDisallowedApplication(it) } catch (e: Exception) {} }
            whitelistedApps.forEach { if (it != packageName && it.isNotEmpty()) try { builder.addDisallowedApplication(it) } catch (e: Exception) {} }

            vpnInterface = builder.establish()
            Thread { runLoop() }.start()
        } catch (e: Exception) { stopVpn() }
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
                        val ipHeaderLen = (packet.get(0).toInt() and 0x0F) * 4
                        val srcPort = packet.getShort(ipHeaderLen).toInt() and 0xFFFF
                        val sourceIp = getSourceIp(packet)
                        val destIp = getDestIp(packet)
                        executor?.execute { handleDnsRequest(ByteBuffer.wrap(packetCopy), output, srcPort, sourceIp, destIp) }
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
    
    private fun getSourceIp(packet: ByteBuffer): String {
        val bytes = ByteArray(4)
        packet.position(12); packet.get(bytes)
        return InetAddress.getByAddress(bytes).hostAddress ?: "0.0.0.0"
    }

    private fun getDestIp(packet: ByteBuffer): String {
        val bytes = ByteArray(4)
        packet.position(16); packet.get(bytes)
        return InetAddress.getByAddress(bytes).hostAddress ?: "0.0.0.0"
    }

    private fun handleDnsRequest(packet: ByteBuffer, output: FileOutputStream, srcPort: Int, sourceIp: String, destIp: String) {
        try {
            val dnsInfo = SimpleDnsParser.parse(packet) ?: return
            val uid = getUidForPort(srcPort, sourceIp, destIp)
            val appPackage = getPackageNameFromUid(uid, dnsInfo.domain)
            val appName = getAppNameFromPackage(appPackage)
            
            val isWhitelistedApp = whitelistedApps.any { appPackage.startsWith(it) }
            
            if (isWhitelistedApp) {
                forwardQuery(dnsInfo.payload)?.let { 
                    addLog("${System.currentTimeMillis()}|${dnsInfo.domain}|APP_WHITELISTED|ALLOWED|$appPackage|$appName")
                    SimpleDnsParser.createResponsePacket(packet, it).let { res -> synchronized(output) { output.write(res) } }
                }
                return
            }

            if (isAd(dnsInfo.domain)) {
                addLog("${System.currentTimeMillis()}|${dnsInfo.domain}|AD_CONTENT|BLOCKED|$appPackage|$appName")
                updateNotificationCounter()
                SimpleDnsParser.createNxDomainResponse(packet).let { res -> synchronized(output) { output.write(res) } }
            } else {
                forwardQuery(dnsInfo.payload)?.let { 
                    addLog("${System.currentTimeMillis()}|${dnsInfo.domain}|DNS_QUERY|ALLOWED|$appPackage|$appName")
                    SimpleDnsParser.createResponsePacket(packet, it).let { res -> synchronized(output) { output.write(res) } }
                }
            }
        } catch (e: Exception) {}
    }

    private fun getUidForPort(port: Int, sourceIp: String, destIp: String): Int {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
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
        if (cachedInstalledApps.isEmpty()) return null
        
        val ld = domain.lowercase()
        // Ambil bagian tengah domain untuk pencocokan cepat (misal: shopee dari ads.shopee.id)
        val parts = ld.split(".").filter { it.length > 3 && it !in listOf("com", "net", "org", "gov", "edu", "cloud", "unity3d") }
        
        for (pkg in cachedInstalledApps) {
            for (p in parts) {
                // Pencocokan cerdas: Apakah domain terkandung dalam package name?
                if (pkg.contains(p, ignoreCase = true)) return pkg
            }
        }
        return null
    }
    
    private fun getAppNameFromPackage(packageName: String): String {
        if (packageName == "com.android.system") return "Layanan Sistem"
        return try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) { if (packageName.startsWith("system.uid.")) "Layanan Sistem" else packageName }
    }

    private fun isAd(domain: String): Boolean {
        val ld = domain.lowercase()
        if (SYSTEM_WHITELIST.any { ld == it || ld.endsWith(".$it") } || whitelistedDomains.any { ld == it || ld.endsWith(".$it") }) return false
        synchronized(blockedDomains) {
            if (blockedDomains.contains(ld)) return true
            var p = ld
            while (p.contains(".")) { p = p.substringAfter("."); if (blockedDomains.contains(p)) return true }
        }
        return false
    }

    private fun forwardQuery(payload: ByteArray): ByteArray? {
        val socket = DatagramSocket()
        protect(socket)
        socket.soTimeout = 1500
        val out = DatagramPacket(payload, payload.size)
        val inData = ByteArray(1500)
        val inP = DatagramPacket(inData, inData.size)
        for (addr in UPSTREAM_DNS_SERVERS) {
            try {
                out.address = addr; out.port = 53
                socket.send(out); socket.receive(inP)
                socket.close(); return inData.copyOf(inP.length)
            } catch (e: Exception) {}
        }
        socket.close(); return null
    }

    private fun stopVpn() {
        isRunning = false
        executor?.shutdown()
        try { vpnInterface?.close() } catch (e: Exception) {}
        vpnInterface = null
        stopSelf()
    }
}