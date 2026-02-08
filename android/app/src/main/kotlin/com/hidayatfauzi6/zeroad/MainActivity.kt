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
        fun sendLogToFlutter(log: String) {
            logSink?.let { sink ->
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    try { sink.success(log) } catch (e: Exception) {}
                }
            }
        }
    }

    private fun loadAdSignatures(): List<AdSignature> {
        return try {
            val jsonString = assets.open("ad_signatures.json").bufferedReader().use { it.readText() }
            val container = Json.decodeFromString<AdSignaturesContainer>(jsonString)
            container.signatures
        } catch (e: Exception) { listOf() }
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

                "uninstallApp" -> {
                    val pkg = call.argument<String>("packageName")
                    if (pkg != null) {
                        startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:$pkg")))
                        result.success(true)
                    } else result.error("ERROR", "Null package", null)
                }

                "startAdBlock" -> {
                    val intent = VpnService.prepare(this)
                    if (intent != null) {
                        pendingResult = result
                        startActivityForResult(intent, VPN_REQUEST_CODE)
                    } else {
                        CoroutineScope(Dispatchers.IO).launch {
                            val essentialApps = getEssentialApps()
                            withContext(Dispatchers.Main) {
                                startVpnService(essentialApps)
                                result.success(true)
                            }
                        }
                    }
                }

                "stopAdBlock" -> {
                    stopVpnService()
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
                        if (ok) { stopVpnService(); android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ startVpnService(getEssentialApps()) }, 300) }
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
                        if (ok) { stopVpnService(); android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ startVpnService(getEssentialApps()) }, 300) }
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
                override fun onListen(args: Any?, events: EventChannel.EventSink?) { logSink = events; AdBlockVpnService.getLogs().reversed().forEach { sendLogToFlutter(it) } }
                override fun onCancel(args: Any?) { logSink = null }
            }
        )
    }

    private fun getEssentialApps(): ArrayList<String> {
        val list = ArrayList<String>()
        val packages = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
        for (app in packages) {
            val pkg = app.packageName.lowercase()
            
            // 1. Deteksi Kategori Sistem (Jika tersedia)
            val isShoppingCategory = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                app.category == 8 || app.category == 6 || app.category == 7
            } else false
            
            // 2. Deteksi Kata Kunci Industri (Finance, E-commerce, Ojol, Travel)
            val isIndustryMatch = pkg.contains(".bank") || pkg.contains(".pay") || 
                                 pkg.contains(".wallet") || pkg.contains(".finance") || 
                                 pkg.contains("payment") || pkg.contains(".shop") || 
                                 pkg.contains(".mall") || pkg.contains(".market") ||
                                 pkg.contains("commerce") || pkg.contains("traveloka") ||
                                 pkg.contains("grab") || pkg.contains("gojek") ||
                                 pkg.contains("tokopedia") || pkg.contains("shopee") ||
                                 pkg.contains("lazada") || pkg.contains("blibli")

            // 3. Deteksi Aplikasi Populer Indonesia yang sering bermasalah dengan AdBlock
            val popularApps = listOf("com.shopee.id", "com.tokopedia.tkpd", "com.lazada.android", "com.gojek.app", "com.grabtaxi.driverid", "com.dana.id", "com.btpn.pbtit", "id.dana")
            val isPopularMatch = popularApps.any { pkg.startsWith(it) }

            if (isShoppingCategory || isIndustryMatch || isPopularMatch) {
                list.add(app.packageName)
            }
        }
        android.util.Log.d("ZeroAd_Smart", "Essential Apps Detected: ${list.size}")
        return list
    }

    private fun startVpnService(essentialApps: ArrayList<String>? = null) {
        val intent = Intent(this, AdBlockVpnService::class.java).apply { 
            action = AdBlockVpnService.ACTION_START 
            if (essentialApps != null) putStringArrayListExtra("essential_apps", essentialApps)
        }
        startService(intent)
    }

    private fun stopVpnService() {
        val intent = Intent(this, AdBlockVpnService::class.java).apply { action = AdBlockVpnService.ACTION_STOP }
        startService(intent)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == VPN_REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK) { startVpnService(getEssentialApps()); pendingResult?.success(true) }
            else pendingResult?.error("REJECTED", "VPN Rejected", null)
            pendingResult = null
        }
    }
}
