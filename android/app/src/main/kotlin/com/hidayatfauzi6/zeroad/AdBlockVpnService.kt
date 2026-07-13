package com.hidayatfauzi6.zeroad

import android.content.Intent
import android.content.SharedPreferences
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.core.app.NotificationCompat
import com.hidayatfauzi6.zeroad.engine.*
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.util.concurrent.*
import kotlinx.coroutines.runBlocking

class AdBlockVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private var executor: ExecutorService? = null
    private val outputQueue = LinkedBlockingQueue<ByteArray>(2000)
    private var isRunning = false
    
    private val NOTIFICATION_ID = 1
    private val CHANNEL_ID = "ZeroAd_DNS_Channel"

    // Engine 2.0 components
    private lateinit var adFilterEngine: AdFilterEngine
    private lateinit var whitelistManager: WhitelistManager
    private lateinit var smartBypassEngine: SmartBypassEngine
    private lateinit var statisticsEngine: StatisticsEngine
    private lateinit var dohBlocker: DohBlocker
    private lateinit var dnsFilterEngine: DnsFilterEngine

    // Per-app UID tracking (untuk kategorisasi game vs non-game)
    private val uidCategoryCache = ConcurrentHashMap<Int, AppCategory>()
    private val uidToPackageMap = ConcurrentHashMap<Int, String>()
    private val uidToNameMap = ConcurrentHashMap<Int, String>()
    private var appCacheBuilt = false

    // Game yang dikenal bermasalah dengan VPN — ISP Direct (zero filtering)
    private val gamesNeedISPDirect = setOf(
        "com.twoheadedshark.tco",
        "com.miniclip.realsniper",
        "com.yandex.music",
        "com.roblox.client",
    )

    companion object {
        const val ACTION_START = "com.hidayatfauzi6.zeroad.START"
        const val ACTION_STOP = "com.hidayatfauzi6.zeroad.STOP"
        private const val TAG = "ZeroAdDNS"
        
        private const val ADGUARD_PRIMARY = "94.140.14.14"
        private const val ADGUARD_SECONDARY = "94.140.15.15"
        
        // Shared preferences for blocklist paths
        private const val PREFS_NAME = "ZeroAdPrefs"
        
        // Singleton reference for MainActivity access
        var instance: AdBlockVpnService? = null
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        initEngines()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance === this) instance = null
    }

    private fun initEngines() {
        try {
            adFilterEngine = AdFilterEngine(this)
            whitelistManager = WhitelistManager(this)
            smartBypassEngine = SmartBypassEngine(this)
            statisticsEngine = StatisticsEngine()
            dohBlocker = DohBlocker()
            dnsFilterEngine = DnsFilterEngine(
                context = this,
                vpnService = this,
                whitelistManager = whitelistManager,
                smartBypassEngine = smartBypassEngine,
                statisticsEngine = statisticsEngine,
                dohBlocker = dohBlocker,
                adFilterEngine = adFilterEngine
            )

            // Load blocklists from assets and prefs
            val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            adFilterEngine.loadBlocklists(prefs)
            whitelistManager.loadFromPrefs()

            Log.d(TAG, "ZeroAd 2.0 Engines initialized")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize engines", e)
        }
    }

    /**
     * Reload blocklists dynamically (called from MainActivity via MethodChannel)
     */
    fun reloadBlocklists() {
        try {
            val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            adFilterEngine.loadBlocklists(prefs)
            whitelistManager.loadFromPrefs()
            Log.d(TAG, "Blocklists reloaded")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to reload blocklists", e)
        }
    }

    /**
     * Build cache of all installed apps with their UIDs and categories.
     * Dipanggil saat VPN start untuk per-app identification.
     */
    private fun buildAppCategoryCache() {
        try {
            val pm = packageManager
            val installedApps = pm.getInstalledApplications(0)
            
            uidCategoryCache.clear()
            uidToPackageMap.clear()
            uidToNameMap.clear()
            
            for (app in installedApps) {
                val uid = app.uid
                val pkg = app.packageName
                val isGame = (app.flags and android.content.pm.ApplicationInfo.FLAG_IS_GAME) != 0 ||
                             app.category == android.content.pm.ApplicationInfo.CATEGORY_GAME
                
                val category = when {
                    gamesNeedISPDirect.contains(pkg) -> AppCategory.GENERAL
                    isGame -> AppCategory.GAME
                    pkg.startsWith("com.google.") || pkg.startsWith("com.android.") -> AppCategory.GOOGLE_SERVICES
                    else -> smartBypassEngine.categorizeApp(pkg)
                }
                
                uidCategoryCache[uid] = category
                uidToPackageMap[uid] = pkg
                uidToNameMap[uid] = pm.getApplicationLabel(app).toString()
            }
            
            appCacheBuilt = true
            Log.d(TAG, "App category cache built: ${uidCategoryCache.size} apps")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to build app category cache", e)
        }
    }

    /**
     * Identify the app that sent a DNS packet using the local UDP source port.
     *
     * Android API 30+ provides getConnectionOwnerUid() which maps
     * (protocol, srcAddr, srcPort, dstAddr, dstPort) → UID.
     * For older APIs, fall back to GENERAL.
     */
    private fun identifyAppFromPacket(requestData: ByteArray, ipHeaderLen: Int): DnsFilterEngine.AppInfo {
        if (!appCacheBuilt) buildAppCategoryCache()
        
        val uid = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            resolveUidApi30(requestData, ipHeaderLen)
        } else {
            -1
        }
        
        if (uid > 0) {
            val pkg = uidToPackageMap[uid]
            val name = uidToNameMap[uid]
            val category = uidCategoryCache[uid]
            
            if (pkg != null && category != null) {
                return DnsFilterEngine.AppInfo(
                    packageName = pkg,
                    appName = name ?: pkg,
                    uid = uid,
                    category = category
                )
            }
        }
        
        return DnsFilterEngine.AppInfo(
            packageName = "unknown",
            appName = "Unknown",
            uid = -1,
            category = AppCategory.GENERAL
        )
    }

    /**
     * Resolve UID using getConnectionOwnerUid() (API 30+).
     */
    private fun resolveUidApi30(requestData: ByteArray, ipHeaderLen: Int): Int {
        return try {
            val version = (requestData[0].toInt() shr 4) and 0x0F
            if (version != 4) return -1 // IPv6 not supported yet
            
            // Extract src IP (offset 12) and dst IP (offset 16)
            val srcIpBytes = requestData.copyOfRange(12, 16)
            val dstIpBytes = requestData.copyOfRange(16, 20)
            val srcAddr = java.net.InetAddress.getByAddress(srcIpBytes)
            val dstAddr = java.net.InetAddress.getByAddress(dstIpBytes)
            
            // Extract src port (UDP header at ipHeaderLen) and dst port (ipHeaderLen + 2)
            val srcPort = ((requestData[ipHeaderLen].toInt() and 0xFF) shl 8) or
                          (requestData[ipHeaderLen + 1].toInt() and 0xFF)
            val dstPort = ((requestData[ipHeaderLen + 2].toInt() and 0xFF) shl 8) or
                          (requestData[ipHeaderLen + 3].toInt() and 0xFF)
            
            // Protocol 17 = UDP
            getConnectionOwnerUid(17, srcAddr, srcPort, dstAddr, dstPort)
        } catch (e: Exception) {
            Log.w(TAG, "UID resolution failed", e)
            -1
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopVpn()
        } else if (intent?.action == ACTION_START) {
            if (executor == null) executor = Executors.newFixedThreadPool(8)
            startForeground(NOTIFICATION_ID, createNotification())
            startVpn()
        }
        return START_STICKY
    }

    private fun startVpn() {
        if (isRunning) return
        isRunning = true
        try {
            val builder = Builder()
            RoutingManager(builder).configureDnsOnlyRouting()
            builder.addDisallowedApplication(packageName)

            vpnInterface = builder.establish()
            
            // Writer Thread
            Thread {
                val output = FileOutputStream(vpnInterface?.fileDescriptor)
                while (isRunning) {
                    try {
                        val packet = outputQueue.poll(50, TimeUnit.MILLISECONDS)
                        if (packet != null) {
                            output.write(packet)
                        }
                    } catch (e: Exception) {
                        if (isRunning) Log.e(TAG, "Writer error", e)
                    }
                }
            }.start()

            // Reader Thread
            Thread { runLoop() }.start()
            
            Log.d(TAG, "ZeroAd 2.0 DNS Engine Started")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start VPN", e)
            stopVpn()
        }
    }

    private fun runLoop() {
        val input = FileInputStream(vpnInterface?.fileDescriptor)
        val buffer = ByteBuffer.allocate(32767)
        
        while (isRunning) {
            try {
                val length = input.read(buffer.array())
                if (length <= 0) continue

                val packetData = buffer.array().copyOf(length)
                if (packetData.isEmpty()) continue
                
                val ipHeaderLen = (packetData[0].toInt() and 0x0F) * 4
                val protocol = packetData[9].toInt() and 0xFF

                if (protocol == 17) { // UDP
                    val dstPort = ((packetData[ipHeaderLen + 2].toInt() and 0xFF) shl 8) or (packetData[ipHeaderLen + 3].toInt() and 0xFF)
                    if (dstPort == 53) {
                        executor?.execute { handleDns(packetData, ipHeaderLen) }
                    }
                }
            } catch (e: Exception) {
                if (isRunning) Log.e(TAG, "Read Error", e)
            }
        }
    }

    private fun handleDns(requestData: ByteArray, ipHeaderLen: Int) {
        try {
            val buffer = ByteBuffer.wrap(requestData)
            
            // Identify which app sent this DNS query
            val appInfo = identifyAppFromPacket(requestData, ipHeaderLen)
            
            // ISP Direct bypass untuk game yang dikenal bermasalah dengan VPN
            // Zero filtering — forward langsung ke ISP DNS
            if (gamesNeedISPDirect.contains(appInfo.packageName)) {
                Log.d(TAG, "ISP Direct bypass for ${appInfo.packageName}")
                val dnsPayloadStart = ipHeaderLen + 8
                val dnsPayloadLen = requestData.size - dnsPayloadStart
                val dnsQuery = ByteArray(dnsPayloadLen)
                System.arraycopy(requestData, dnsPayloadStart, dnsQuery, 0, dnsPayloadLen)
                
                try {
                    val socket = java.net.DatagramSocket()
                    protect(socket)
                    socket.soTimeout = 3000
                    val addr = java.net.InetAddress.getByName("8.8.8.8")
                    socket.send(java.net.DatagramPacket(dnsQuery, dnsQuery.size, addr, 53))
                    val inData = ByteArray(1500)
                    val inP = java.net.DatagramPacket(inData, inData.size)
                    socket.receive(inP)
                    val dnsResponse = inData.copyOf(inP.length)
                    val responsePacket = buildResponsePacket(requestData, ipHeaderLen, dnsResponse)
                    outputQueue.offer(responsePacket)
                    socket.close()
                } catch (e: Exception) {
                    Log.e(TAG, "ISP Direct DNS error for ${appInfo.packageName}", e)
                }
                return
            }

            // Run through ZeroAd 2.0 multi-layer DNS filtering pipeline
            // with PER-APP categorization
            val response = runBlocking {
                dnsFilterEngine.handleDnsQuery(buffer, ipHeaderLen, appInfo)
            }

            if (response.isEmpty()) return

            val version = (response[0].toInt() shr 4) and 0x0F
            if (version == 4 || version == 6) {
                outputQueue.offer(response)
            } else {
                val responsePacket = buildResponsePacket(requestData, ipHeaderLen, response)
                outputQueue.offer(responsePacket)
            }
        } catch (e: Exception) {
            Log.e(TAG, "handleDns error", e)
        }
    }

    private fun buildResponsePacket(requestData: ByteArray, ipHeaderLen: Int, dnsResponse: ByteArray): ByteArray {
        val totalLen = ipHeaderLen + 8 + dnsResponse.size
        val responsePacket = ByteArray(totalLen)
        System.arraycopy(requestData, 0, responsePacket, 0, ipHeaderLen)

        // Swap IP addresses
        System.arraycopy(requestData, 12, responsePacket, 16, 4)
        System.arraycopy(requestData, 16, responsePacket, 12, 4)
        
        // Swap UDP ports
        responsePacket[ipHeaderLen] = requestData[ipHeaderLen + 2]
        responsePacket[ipHeaderLen + 1] = requestData[ipHeaderLen + 3]
        responsePacket[ipHeaderLen + 2] = requestData[ipHeaderLen]
        responsePacket[ipHeaderLen + 3] = requestData[ipHeaderLen + 1]

        // Fix IP total length
        responsePacket[2] = (totalLen shr 8).toByte()
        responsePacket[3] = (totalLen and 0xFF).toByte()

        // Fix UDP length
        val udpLen = 8 + dnsResponse.size
        responsePacket[ipHeaderLen + 4] = (udpLen shr 8).toByte()
        responsePacket[ipHeaderLen + 5] = (udpLen and 0xFF).toByte()

        // Calculate IP checksum
        responsePacket[10] = 0; responsePacket[11] = 0
        val ipCksum = calculateChecksum(responsePacket, 0, ipHeaderLen)
        responsePacket[10] = (ipCksum shr 8).toByte()
        responsePacket[11] = (ipCksum and 0xFF).toByte()
        
        // Zero UDP checksum (acceptable for DNS over local tunnel)
        responsePacket[ipHeaderLen + 6] = 0; responsePacket[ipHeaderLen + 7] = 0

        // Copy DNS response payload
        System.arraycopy(dnsResponse, 0, responsePacket, ipHeaderLen + 8, dnsResponse.size)

        return responsePacket
    }

    private fun calculateChecksum(data: ByteArray, offset: Int, length: Int): Int {
        var sum = 0
        var i = offset
        while (i < offset + length) {
            val high = data[i].toInt() and 0xFF
            val low = if (i + 1 < offset + length) data[i + 1].toInt() and 0xFF else 0
            sum += (high shl 8) or low
            i += 2
        }
        while (sum shr 16 != 0) sum = (sum and 0xFFFF) + (sum shr 16)
        return sum.inv() and 0xFFFF
    }

    private fun createNotification(): Notification {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL_ID, "ZeroAd DNS", NotificationManager.IMPORTANCE_LOW))
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("ZeroAd Protection")
            .setContentText("Ad-Blocking Active")
            .setSmallIcon(R.mipmap.launcher_icon)
            .setOngoing(true).build()
    }

    private fun stopVpn() {
        isRunning = false
        executor?.shutdown()
        
        // Cleanup engine resources
        if (::dnsFilterEngine.isInitialized) {
            dnsFilterEngine.cleanup()
        }
        
        try { vpnInterface?.close() } catch (e: Exception) {
            Log.e(TAG, "Error closing VPN interface", e)
        }
        vpnInterface = null
        stopSelf()
        
        Log.d(TAG, "VPN stopped and resources cleaned up")
    }
}
