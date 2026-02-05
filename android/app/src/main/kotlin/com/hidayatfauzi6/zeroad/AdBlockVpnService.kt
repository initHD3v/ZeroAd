package com.hidayatfauzi6.zeroad

import android.content.Intent
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.system.OsConstants
import android.util.Log
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
    private val blockedDomains = HashSet<String>()
    private lateinit var prefs: SharedPreferences

    companion object {
        const val ACTION_START = "com.hidayatfauzi6.zeroad.START"
        const val ACTION_STOP = "com.hidayatfauzi6.zeroad.STOP"
        const val ACTION_UPDATE_WHITELIST = "com.hidayatfauzi6.zeroad.UPDATE_WHITELIST"
        private const val TAG = "ZeroAdService"
        
        // Format: Time|Domain|Type|Status|PackageName|AppName
        val blockedLogs = ConcurrentLinkedQueue<String>()

        fun getLogs(): List<String> {
            val logs = mutableListOf<String>()
            while (blockedLogs.isNotEmpty()) {
                blockedLogs.poll()?.let { logs.add(it) }
            }
            return logs
        }

        private val SYSTEM_WHITELIST = listOf(
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

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopVpn()
            ACTION_START -> {
                if (executor == null) executor = Executors.newFixedThreadPool(10)
                loadWhitelist()
                loadBlocklistFromAssets()
                startVpn()
            }
            ACTION_UPDATE_WHITELIST -> loadWhitelist()
        }
        return START_STICKY
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
            
            // Load and apply bypass list from JSON
            val bypassApps = loadSystemBypassList()
            for (app in bypassApps) {
                try { builder.addDisallowedApplication(app) } catch (e: Exception) {
                    Log.w(TAG, "Could not bypass app: $app")
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
            
            // Check Whitelist
            if (whitelistedApps.contains(appPackage)) {
                 val responsePayload = forwardQuery(dnsInfo.payload)
                if (responsePayload != null) {
                    blockedLogs.add("${System.currentTimeMillis()}|${dnsInfo.domain}|WHITELISTED|ALLOWED|$appPackage|$appName")
                    val fullResponse = SimpleDnsParser.createResponsePacket(packet, responsePayload)
                    synchronized(output) { output.write(fullResponse) }
                }
                return
            }

            // Check Blacklist
            if (isAd(dnsInfo.domain)) {
                Log.d(TAG, "BLOCKING: ${dnsInfo.domain} from $appName")
                blockedLogs.add("${System.currentTimeMillis()}|${dnsInfo.domain}|AD_CONTENT|BLOCKED|$appPackage|$appName")
                val response = SimpleDnsParser.createNxDomainResponse(packet)
                synchronized(output) { output.write(response) }
            } else {
                val responsePayload = forwardQuery(dnsInfo.payload)
                if (responsePayload != null) {
                    val fullResponse = SimpleDnsParser.createResponsePacket(packet, responsePayload)
                    synchronized(output) { output.write(fullResponse) }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "DNS Handle Error", e)
        }
    }

    // --- APP IDENTIFICATION LOGIC ---
    
    private fun getUidForPort(port: Int, sourceIp: String, destIp: String): Int {
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
            } catch (e: Exception) {
                // Log.e(TAG, "Q+ identification failed: $e")
            }
        }

        // Method 2: Fallback to /proc/net scanning (For Older Android or if Method 1 fails)
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
        
        // 2. Check blockedDomains HashSet
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
