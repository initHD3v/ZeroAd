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
                // Bypass Chrome secara permanen agar tidak macet
                val chromePkgs = listOf("com.android.chrome", "com.chrome.beta", "com.chrome.dev", "com.chrome.canary")
                chromePkgs.forEach { if (!essentialApps.contains(it)) essentialApps.add(it) }
                
                filterEngine.updateEssentialApps(essentialApps)
                
                executor?.execute {
                    filterEngine.loadBlocklists(prefs)
                    val userWhitelist = prefs.getStringSet("whitelisted_apps", emptySet()) ?: emptySet()
                    filterEngine.updateUserWhitelist(userWhitelist)
                }

                startForeground(NOTIFICATION_ID, createNotification())
                startVpn()
            }
            ACTION_UPDATE_WHITELIST -> {
                executor?.execute {
                    filterEngine.loadBlocklists(prefs)
                    val userWhitelist = prefs.getStringSet("whitelisted_apps", emptySet()) ?: emptySet()
                    filterEngine.updateUserWhitelist(userWhitelist)
                }
            }
        }
        return START_STICKY
    }

    private fun startVpn() {
        if (isRunning) return
        isRunning = true
        try {
            val builder = Builder()
            val routingManager = RoutingManager(builder)
            
            routingManager.configureDnsOnlyRouting()
            routingManager.applyBypassApps(packageName, filterEngine.getEssentialApps())

            vpnInterface = builder.establish()
            
            outputExecutor?.execute {
                val fd = vpnInterface!!.fileDescriptor
                val output = FileOutputStream(fd)
                while (isRunning && vpnInterface != null) {
                    try {
                        val packet = outputQueue.poll(500, TimeUnit.MILLISECONDS)
                        if (packet != null) synchronized(output) { output.write(packet) }
                    } catch (e: Exception) {}
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
                if (length <= 0) continue
                
                val packet = ByteBuffer.wrap(buffer.array(), 0, length)
                val protocol = packet.get(9).toInt() and 0xFF
                
                if (protocol == 17) { // UDP
                    val ipHeaderLen = (packet.get(0).toInt() and 0x0F) * 4
                    val dstPort = packet.getShort(ipHeaderLen + 2).toInt() and 0xFFFF
                    if (dstPort == 53) {
                        val packetCopy = ByteArray(length); System.arraycopy(buffer.array(), 0, packetCopy, 0, length)
                        executor?.execute { handleDnsRequest(ByteBuffer.wrap(packetCopy)) }
                    }
                }
            } catch (e: Exception) {}
        }
    }

    private fun handleDnsRequest(packet: ByteBuffer) {
        try {
            val ipHeaderLen = (packet.get(0).toInt() and 0x0F) * 4
            val srcPort = packet.getShort(ipHeaderLen).toInt() and 0xFFFF
            
            val dnsInfo = SimpleDnsParser.parse(packet) ?: return
            val domain = dnsInfo.domain.lowercase()
            
            // 1. Cek Cache
            val cachedResponse = dnsCache[domain]
            if (cachedResponse != null && (System.currentTimeMillis() - cachedResponse.second < CACHE_TTL)) {
                val patchedPayload = cachedResponse.first.copyOf()
                patchedPayload[0] = dnsInfo.payload[0]
                patchedPayload[1] = dnsInfo.payload[1]
                sendSimpleDnsPacket(packet, patchedPayload)
                return
            }

            // 2. Filter Iklan
            if (filterEngine.isAd(domain)) {
                SimpleDnsParser.createNullIpResponse(packet).let { res -> outputQueue.offer(res) }
                updateNotificationCounter()
                executor?.execute {
                    val appInfo = identifyApp(srcPort, packet, domain)
                    addLog("${System.currentTimeMillis()}|$domain|AD_ENGINE|BLOCKED|${appInfo.first}|${appInfo.second}")
                }
            } else {
                // 3. Forward Query
                executor?.execute {
                    forwardQueryShared(dnsInfo.payload)?.let { res ->
                        dnsCache[domain] = Pair(res, System.currentTimeMillis())
                        sendSimpleDnsPacket(packet, res)
                        
                        val appInfo = identifyApp(srcPort, packet, domain)
                        addLog("${System.currentTimeMillis()}|$domain|DNS_QUERY|ALLOWED|${appInfo.first}|${appInfo.second}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "DNS Handle Error", e)
        }
    }

    private fun identifyApp(srcPort: Int, packet: ByteBuffer, domain: String): Pair<String, String> {
        val srcIp = getSourceIp(packet)
        val destIp = getDestIp(packet)
        val uid = getUidForPort(srcPort, srcIp, destIp)
        
        val pkg = if (uid <= 0) {
            filterEngine.findPackageFromDomain(domain) ?: "unknown.system"
        } else {
            getPackageNameFromUid(uid)
        }
        return Pair(pkg, getAppNameFromPackage(pkg))
    }

    private fun forwardQueryShared(payload: ByteArray): ByteArray? {
        var socket: DatagramSocket? = null
        try {
            socket = DatagramSocket()
            protect(socket)
            socket.soTimeout = 2000
            val dnsServer = getSystemDns() ?: "1.1.1.1"
            val out = DatagramPacket(payload, payload.size, InetAddress.getByName(dnsServer), 53)
            val inData = ByteArray(1500); val inP = DatagramPacket(inData, inData.size)
            socket.send(out)
            socket.receive(inP)
            return inData.copyOf(inP.length)
        } catch (e: Exception) { return null } finally { try { socket?.close() } catch (e: Exception) {} }
    }

    private fun getSystemDns(): String? {
        return try {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
            val activeNetwork = cm.activeNetwork
            val linkProperties = cm.getLinkProperties(activeNetwork)
            linkProperties?.dnsServers?.firstOrNull()?.hostAddress
        } catch (e: Exception) { null }
    }

    private fun sendSimpleDnsPacket(request: ByteBuffer, payload: ByteArray) {
        SimpleDnsParser.createResponsePacket(request, payload).let { res -> outputQueue.offer(res) }
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
            executor?.execute { try { nm.notify(NOTIFICATION_ID, createNotification()) } catch (e: Exception) {} }
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