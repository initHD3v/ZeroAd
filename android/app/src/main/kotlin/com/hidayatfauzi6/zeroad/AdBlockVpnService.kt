package com.hidayatfauzi6.zeroad

import android.content.Intent
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.system.OsConstants
import android.util.Log
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
            "instagram.com", "android.com", "play.google.com"
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
            
            // Critical Google Services Bypass to ensure Connectivity
            val bypassApps = listOf(
                "com.google.android.gms",
                "com.google.android.gsf",
                "com.android.vending",
                "com.google.android.youtube",
                "com.google.android.apps.youtube.music",
                "com.android.chrome",
                "com.google.android.gm",
                "com.google.android.apps.maps",
                "com.google.android.googlequicksearchbox",
                "com.whatsapp",
                "com.facebook.katana",
                "com.instagram.android"
            )
            
            for (app in bypassApps) {
                try { builder.addDisallowedApplication(app) } catch (e: Exception) {}
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
        
        // 1. Check SYSTEM_WHITELIST (Critical Protection)
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
        socket.soTimeout = 2000 // 2 seconds timeout

        // Robust DNS List - Try until one works
        val upstreamDns = listOf(
            "1.1.1.1", // Cloudflare (Fastest usually)
            "8.8.8.8", // Google
            "9.9.9.9"  // Quad9
        )

        for (dnsIp in upstreamDns) {
            try {
                val address = InetAddress.getByName(dnsIp)
                val packet = DatagramPacket(payload, payload.size, address, 53)
                socket.send(packet)

                val data = ByteArray(1500)
                val response = DatagramPacket(data, data.size)
                socket.receive(response)
                
                socket.close()
                return data.copyOf(response.length)
            } catch (e: Exception) {
                // Try next DNS
                continue
            }
        }
        socket.close()
        return null // All failed
    }

    private fun stopVpn() {
        isRunning = false
        executor?.shutdown()
        try { vpnInterface?.close() } catch (e: Exception) {}
        vpnInterface = null
        stopSelf()
    }
}
