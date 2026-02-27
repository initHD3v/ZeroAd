package com.hidayatfauzi6.zeroad

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.content.ComponentName
import android.Manifest
import androidx.annotation.NonNull
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.EventChannel
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Serializable
data class Threat(val type: String, val severity: String, val code: String, val description: String)

@Serializable
data class AppThreatInfo(val packageName: String, val appName: String, val isSystemApp: Boolean, val detectedThreats: List<Threat>, val zeroScore: Int = 0)

@Serializable
data class AdSignature(val pattern: String, val name: String)

@Serializable
data class AdSignaturesContainer(val signatures: List<AdSignature>)

@Serializable
data class ScanResultKotlin(val totalInstalledPackages: Int, val suspiciousPackagesCount: Int, val threats: List<AppThreatInfo>)

class MainActivity : FlutterActivity() {
    private val CHANNEL = "zeroad.security/scanner"
    private val STREAM_CHANNEL = "zeroad.security/stream"
    private val VPN_REQUEST_CODE = 101
    private val NOTIF_PERMISSION_CODE = 102
    private var pendingResult: MethodChannel.Result? = null

    companion object {
        var logSink: EventChannel.EventSink? = null
        
        // Gunakan Handler Utama agar pengiriman log ke Flutter selalu aman dari thread mana pun
        private val uiHandler = android.os.Handler(android.os.Looper.getMainLooper())

        fun sendLogToFlutter(log: String) {
            uiHandler.post {
                try {
                    logSink?.success(log)
                } catch (e: Exception) {
                    android.util.Log.e("ZeroAd_MainActivity", "Gagal kirim log ke Flutter", e)
                }
            }
        }
    }

    override fun configureFlutterEngine(@NonNull flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL).setMethodCallHandler { call, result ->
            when (call.method) {
                "requestNotificationPermission" -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), NOTIF_PERMISSION_CODE)
                            result.success(false)
                        } else { result.success(true) }
                    } else { result.success(true) }
                }

                "scan" -> {
                    CoroutineScope(Dispatchers.IO).launch {
                        val pm: PackageManager = applicationContext.packageManager
                        val installedApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
                        val adSignatures = loadAdSignatures()
                        val threatsDetected = mutableListOf<AppThreatInfo>()
                        var suspiciousCount = 0

                        for (appInfo in installedApps) {
                            val detectedThreatsForApp = mutableListOf<Threat>()
                            var appZeroScore = 0
                            val appName = pm.getApplicationLabel(appInfo).toString()
                            val isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0

                            try {
                                val packageInfo = pm.getPackageInfo(appInfo.packageName, PackageManager.GET_PERMISSIONS or PackageManager.GET_ACTIVITIES or PackageManager.GET_SERVICES)
                                val permissions = packageInfo.requestedPermissions?.toList() ?: listOf()

                                if (permissions.contains("android.permission.REQUEST_INSTALL_PACKAGES") && !isSystemApp) {
                                    detectedThreatsForApp.add(Threat("SYSTEM_CONTROL", "HIGH", "STEALTH_INSTALLER", "App can install other applications."))
                                    appZeroScore += 30
                                }
                                if (permissions.contains("android.permission.RECEIVE_BOOT_COMPLETED") && permissions.contains("android.permission.SYSTEM_ALERT_WINDOW") && !isSystemApp) {
                                    detectedThreatsForApp.add(Threat("BEHAVIORAL", "HIGH", "BOOT_OVERLAY", "App starts at boot and draws over other apps."))
                                    appZeroScore += 30
                                }
                                
                                val components = mutableListOf<String>()
                                packageInfo.activities?.forEach { components.add(it.name) }
                                packageInfo.services?.forEach { components.add(it.name) }
                                for (sig in adSignatures) {
                                    if (components.any { it.contains(sig.pattern, ignoreCase = true) }) {
                                        detectedThreatsForApp.add(Threat("AD_SDK", "MEDIUM", "AD_LIB_DETECTED", "Embedded Ad Framework: ${sig.name}"))
                                        appZeroScore += 15; break
                                    }
                                }
                            } catch (e: Exception) {}

                            if (detectedThreatsForApp.isNotEmpty()) {
                                threatsDetected.add(AppThreatInfo(appInfo.packageName, appName, isSystemApp, detectedThreatsForApp, appZeroScore))
                                if (appZeroScore >= 15) suspiciousCount++
                            }
                        }
                        withContext(Dispatchers.Main) {
                            result.success(Json.encodeToString(ScanResultKotlin(installedApps.size, suspiciousCount, threatsDetected)))
                        }
                    }
                }

                "startAdBlock" -> {
                    try {
                        val intent = VpnService.prepare(this)
                        if (intent != null) {
                            pendingResult = result
                            startActivityForResult(intent, VPN_REQUEST_CODE)
                        } else {
                            // Jika sudah diizinkan, langsung jalankan
                            val essentialApps = getEssentialApps()
                            startVpnService(essentialApps)
                            result.success(true)
                        }
                    } catch (e: SecurityException) {
                        // Solusi untuk MIUI UID mismatch: arahkan user ke info aplikasi untuk reset izin jika perlu
                        android.util.Log.e("ZeroAd_MainActivity", "MIUI Security Exception Detected", e)
                        result.error("SECURITY_ERROR", "Izin VPN ditolak oleh sistem MIUI. Silakan hapus data aplikasi dan coba lagi.", e.message)
                    } catch (e: Exception) {
                        result.error("ERROR", e.message, null)
                    }
                }

                "stopAdBlock" -> {
                    val intent = Intent(this, AdBlockVpnService::class.java).apply { action = AdBlockVpnService.ACTION_STOP }
                    startService(intent)
                    result.success(false)
                }

                "getVpnLogs" -> result.success(AdBlockVpnService.getLogs())

                "getAppIcon" -> {
                    val pkg = call.argument<String>("packageName")
                    if (pkg != null) {
                        CoroutineScope(Dispatchers.IO).launch {
                            try {
                                val drawable = packageManager.getApplicationIcon(pkg)
                                val bitmap = android.graphics.Bitmap.createBitmap(120, 120, android.graphics.Bitmap.Config.ARGB_8888)
                                val canvas = android.graphics.Canvas(bitmap)
                                drawable.setBounds(0, 0, 120, 120); drawable.draw(canvas)
                                val stream = java.io.ByteArrayOutputStream()
                                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 80, stream)
                                val bytes = stream.toByteArray(); bitmap.recycle()
                                withContext(Dispatchers.Main) { result.success(bytes) }
                            } catch (e: Exception) { withContext(Dispatchers.Main) { result.error("ICON_ERROR", e.message, null) } }
                        }
                    } else result.error("ERROR", "Null package", null)
                }

                "addToWhitelist" -> {
                    val pkg = call.argument<String>("packageName")
                    if (pkg != null) {
                        val prefs = getSharedPreferences("ZeroAdPrefs", Context.MODE_PRIVATE)
                        val current = prefs.getStringSet("whitelisted_apps", mutableSetOf())?.toMutableSet() ?: mutableSetOf()
                        current.add(pkg)
                        val ok = prefs.edit().putStringSet("whitelisted_apps", current).commit()
                        if (ok) { 
                            val stopIntent = Intent(this, AdBlockVpnService::class.java).apply { action = AdBlockVpnService.ACTION_STOP }
                            startService(stopIntent)
                            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ 
                                CoroutineScope(Dispatchers.IO).launch {
                                    val apps = getEssentialApps()
                                    withContext(Dispatchers.Main) { startVpnService(apps) }
                                }
                            }, 500)
                        }
                        result.success(ok)
                    } else result.error("ERROR", "Null package", null)
                }

                "removeFromWhitelist" -> {
                    val pkg = call.argument<String>("packageName")
                    if (pkg != null) {
                        val prefs = getSharedPreferences("ZeroAdPrefs", Context.MODE_PRIVATE)
                        val current = prefs.getStringSet("whitelisted_apps", mutableSetOf())?.toMutableSet() ?: mutableSetOf()
                        current.remove(pkg)
                        val ok = prefs.edit().putStringSet("whitelisted_apps", current).commit()
                        if (ok) { 
                            val stopIntent = Intent(this, AdBlockVpnService::class.java).apply { action = AdBlockVpnService.ACTION_STOP }
                            startService(stopIntent)
                            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ 
                                CoroutineScope(Dispatchers.IO).launch {
                                    val apps = getEssentialApps()
                                    withContext(Dispatchers.Main) { startVpnService(apps) }
                                }
                            }, 500)
                        }
                        result.success(ok)
                    } else result.error("ERROR", "Null package", null)
                }

                "updateBlocklistPath" -> {
                    val path = call.argument<String>("path")
                    if (path != null) {
                        val prefs = getSharedPreferences("ZeroAdPrefs", Context.MODE_PRIVATE)
                        val ok = prefs.edit().putString("dynamic_blocklist_path", path).commit()
                        if (ok) { val i = Intent(this, AdBlockVpnService::class.java).apply { action = AdBlockVpnService.ACTION_UPDATE_WHITELIST }; startService(i) }
                        result.success(ok)
                    } else result.error("ERROR", "Null path", null)
                }

                else -> result.notImplemented()
            }
        }

        EventChannel(flutterEngine.dartExecutor.binaryMessenger, STREAM_CHANNEL).setStreamHandler(
            object : EventChannel.StreamHandler {
                override fun onListen(args: Any?, events: EventChannel.EventSink?) { 
                    logSink = events
                    // Kirim ulang log terakhir saat UI terhubung
                    CoroutineScope(Dispatchers.IO).launch {
                        val logs = AdBlockVpnService.getLogs()
                        withContext(Dispatchers.Main) {
                            for (log in logs) { sendLogToFlutter(log) }
                        }
                    }
                }
                override fun onCancel(args: Any?) { logSink = null }
            }
        )
    }

    private fun getEssentialApps(): ArrayList<String> {
        val list = ArrayList<String>()
        val prefs = getSharedPreferences("ZeroAdPrefs", Context.MODE_PRIVATE)
        val userWhitelist = prefs.getStringSet("whitelisted_apps", emptySet()) ?: emptySet()

        val packages = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
        for (app in packages) {
            val pkg = app.packageName.lowercase()
            val appName = packageManager.getApplicationLabel(app).toString().lowercase()

            // 1. Always include User Whitelist (prioritas tertinggi)
            if (userWhitelist.contains(app.packageName)) {
                list.add(app.packageName)
                continue
            }

            // 2. AUTO-WHITELIST: YouTube & Google Apps
            // YouTube harus di-whitelist agar bisa stream dengan ISP murni
            if (pkg.contains("youtube") || 
                pkg.contains("com.google.android.youtube") ||
                pkg.contains("com.google.android.apps.youtube")) {
                list.add(app.packageName)
                continue
            }

            // 3. AUTO-WHITELIST: Browser Apps
            // Browser harus dapat ISP murni untuk performa maksimal
            if (pkg.contains("chrome") || 
                pkg.contains("browser") || 
                pkg.contains("webview") ||
                pkg.contains("opera") ||
                pkg.contains("firefox") ||
                pkg.contains("edge") ||
                pkg.contains("samsung") && pkg.contains("browser") ||
                pkg.contains("duckduckgo") ||
                pkg.contains("brave")) {
                list.add(app.packageName)
                continue
            }

            // 4. AUTO-WHITELIST: E-Commerce & Shopping
            val ecommercePatterns = listOf(
                "shopee", "tokopedia", "lazada", "bukalapak", "blibli",
                "tiktok", "amazon", "ebay", "aliexpress", "alibaba",
                "jd.id", "jd_com", "zalora", "mataharimall",
                "raja", "olx", "carousell", "facebook.marketplace"
            )
            if (ecommercePatterns.any { pkg.contains(it) }) {
                list.add(app.packageName)
                continue
            }

            // 5. AUTO-WHITELIST: Finance & Banking
            val financePatterns = listOf(
                // Banking - Indonesia
                "bca", "mandiri", "bni", "bri", "cimb", "danamon",
                "permata", "ocbc", "uob", "hsbc", "citibank",
                "jenius", "jago", "neobank", "seabank", "allo", "neo",
                "bsi", "btn", "btpn", "maybank", "panin",
                
                // Banking - International
                "bank", "banking", "chase", "wellsfargo", "bofa",
                
                // E-Wallet & Payment
                "dana", "ovo", "gopay", "linkaja", "shopeepay",
                "wallet", "payment", "pay", "paypal", "venmo",
                "cashapp", "gcash", "grabpay", "gopay",
                
                // Investment & Trading
                "ajaib", "stockbit", "bibit", "ipot", "mirae",
                "invest", "trading", "stocks", "crypto",
                "binance", "indodax", "tokocrypto", "pintu",
                "reksa", "saham", "sekuritas",
                
                // Insurance
                "insurance", "asuransi", "axa", "allianz", "aia",
                "prudential", "manulife", "sinarmas", "bumiputera"
            )
            if (financePatterns.any { pkg.contains(it) }) {
                list.add(app.packageName)
                continue
            }

            // 6. AUTO-WHITELIST: Travel, Hotel & Flight Booking
            val travelPatterns = listOf(
                // Indonesia
                "traveloka", "tiket.com", "pegipegi", "nusatrip",
                "airy", "reddoorz", "tickle",
                
                // International
                "booking.com", "booking_com", "agoda", "expedia",
                "hotels.com", "hotels_com", "airbnb", "vrbo",
                
                // Airlines
                "airasia", "garuda", "lionair", "citilink",
                "sriwijaya", "batikair", "wingsof",
                "singapore.airlines", "cathay", "emirates",
                "qatar", "etihad", "turkish",
                
                // Ride Hailing & Transport
                "grab", "gojek", "gojek.gosend", "gojek.goride",
                "uber", "lyft", "inriver"
            )
            if (travelPatterns.any { pkg.contains(it) }) {
                list.add(app.packageName)
                continue
            }

            // 7. AUTO-WHITELIST: Food Delivery
            val foodPatterns = listOf(
                "gofood", "grabfood", "shopeefood", "foodpanda",
                "deliveroo", "doordash", "ubereats",
                "zenness", "klikmakan"
            )
            if (foodPatterns.any { pkg.contains(it) }) {
                list.add(app.packageName)
                continue
            }

            // 8. AUTO-WHITELIST: Social Media & Communication
            val socialPatterns = listOf(
                "facebook", "instagram", "tiktok", "twitter",
                "whatsapp", "telegram", "signal", "line",
                "wechat", "snapchat", "discord", "slack",
                "zoom", "teams", "meet", "skype",
                "messenger", "viber", "kakaotalk", "wechat"
            )
            if (socialPatterns.any { pkg.contains(it) }) {
                list.add(app.packageName)
                continue
            }

            // 9. AUTO-WHITELIST: Google Services (Critical)
            val googleServices = listOf(
                "com.google.android.gms", // Google Play Services
                "com.google.android.gsf", // Google Services Framework
                "com.android.vending", // Google Play Store
                "com.google.android.play.games", // Google Play Games
                "com.google.android.apps.maps", // Google Maps
                "com.google.android.apps.docs", // Google Docs
                "com.google.android.apps.photos", // Google Photos
                "com.google.android.apps.translate", // Google Translate
                "com.google.android.calendar", // Google Calendar
                "com.google.android.contacts", // Google Contacts
                "com.google.android.dialer", // Google Phone
                "com.google.android.apps.messaging" // Google Messages
            )
            if (googleServices.any { pkg.contains(it) }) {
                list.add(app.packageName)
                continue
            }

            // 10. AUTO-WHITELIST: Streaming & Entertainment
            val streamingPatterns = listOf(
                "netflix", "spotify", "youtube.music",
                "disney", "hulu", "hbo", "prime.video",
                "vidio", "iflix", "viu", "wetv",
                "joox", "apple.music", "soundcloud"
            )
            if (streamingPatterns.any { pkg.contains(it) }) {
                list.add(app.packageName)
                continue
            }

            // 11. Heuristic: Package name contains industry keywords
            val isIndustryMatch = pkg.contains(".bank") || pkg.contains(".pay") ||
                                 pkg.contains(".wallet") || pkg.contains(".finance") ||
                                 pkg.contains("payment") || pkg.contains("vending") ||
                                 pkg.contains("google.android.gms") || pkg.contains("google.android.gsf") ||
                                 pkg.contains("com.android.vending") || // Play Store
                                 pkg.contains("id.dana") || pkg.contains("shopee") ||
                                 pkg.contains("tokopedia") || pkg.contains("lazada") ||
                                 pkg.contains("traveloka") || pkg.contains("tiket") ||
                                 pkg.contains("grab") || pkg.contains("gojek") ||
                                 // SOCIAL MEDIA
                                 pkg.contains("facebook") || pkg.contains("instagram") ||
                                 pkg.contains("tiktok") || pkg.contains("whatsapp") ||
                                 pkg.contains("twitter") || pkg.contains("maps") ||
                                 // GAMES (Stabilitas 100%)
                                 pkg.contains("mobile.legends") || pkg.contains("tencent.ig") ||
                                 pkg.contains("twoheadshark.tco") || pkg.contains("garena.game")

            if (isIndustryMatch) list.add(app.packageName)
        }
        return list
    }

    private fun startVpnService(essentialApps: ArrayList<String>? = null) {
        val intent = Intent(this, AdBlockVpnService::class.java).apply { 
            action = AdBlockVpnService.ACTION_START 
            if (essentialApps != null) putStringArrayListExtra("essential_apps", essentialApps)
        }
        startService(intent)
    }

    private fun loadAdSignatures(): List<AdSignature> {
        return try {
            val jsonString = assets.open("ad_signatures.json").bufferedReader().use { it.readText() }
            val container = Json.decodeFromString<AdSignaturesContainer>(jsonString)
            container.signatures
        } catch (e: Exception) { listOf() }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == VPN_REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK) { 
                CoroutineScope(Dispatchers.IO).launch {
                    val apps = getEssentialApps()
                    withContext(Dispatchers.Main) { startVpnService(apps) }
                }
                pendingResult?.success(true) 
            } else pendingResult?.error("REJECTED", "VPN Rejected", null)
            pendingResult = null
        }
    }
}