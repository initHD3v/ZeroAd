package com.hidayatfauzi6.zeroad

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.util.Log
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
                                
                                val components = mutableListOf<String>()
                                packageInfo.activities?.forEach { components.add(it.name) }
                                packageInfo.services?.forEach { components.add(it.name) }
                                for (sig in adSignatures) {
                                    if (components.any { it.contains(sig.pattern, ignoreCase = true) }) {
                                        detectedThreatsForApp.add(Threat("AD_SDK", "MEDIUM", "AD_LIB_DETECTED", "Embedded Ad Framework: ${sig.name}"))
                                        appZeroScore += 15; break
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e("ZeroAd_MainActivity", "Error scanning package: ${appInfo.packageName}", e)
                            }

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
                            startVpnService()
                            result.success(true)
                        }
                    } catch (e: Exception) {
                        result.error("ERROR", e.message, null)
                    }
                }

                "stopAdBlock" -> {
                    val intent = Intent(this, AdBlockVpnService::class.java).apply { action = AdBlockVpnService.ACTION_STOP }
                    startService(intent)
                    result.success(false)
                }

                "getVpnLogs" -> {
                    // Fitur log lama dimatikan untuk kestabilan DNS Changer
                    result.success(listOf<String>())
                }

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
                    val packageName = call.argument<String>("packageName")
                    if (packageName != null) {
                        WhitelistManager.addToPrefs(this, packageName)
                        Log.d("ZeroAd_MainActivity", "Added to whitelist: $packageName")
                    }
                    result.success(true)
                }

                "removeFromWhitelist" -> {
                    val packageName = call.argument<String>("packageName")
                    if (packageName != null) {
                        WhitelistManager.removeFromPrefs(this, packageName)
                        Log.d("ZeroAd_MainActivity", "Removed from whitelist: $packageName")
                    }
                    result.success(true)
                }

                "updateBlocklistPath" -> {
                    // Reload blocklists on the VPN service if running
                    AdBlockVpnService.instance?.reloadBlocklists()
                    Log.d("ZeroAd_MainActivity", "Blocklist path updated, reload triggered")
                    result.success(true)
                }

                else -> result.notImplemented()
            }
        }

        EventChannel(flutterEngine.dartExecutor.binaryMessenger, STREAM_CHANNEL).setStreamHandler(
            object : EventChannel.StreamHandler {
                override fun onListen(args: Any?, events: EventChannel.EventSink?) { 
                    logSink = events
                    sendLogToFlutter("${System.currentTimeMillis()}|system|SYSTEM|READY|ZeroAd|DNS Changer Ready")
                }
                override fun onCancel(args: Any?) { logSink = null }
            }
        )
    }

    private fun startVpnService() {
        val intent = Intent(this, AdBlockVpnService::class.java).apply { 
            action = AdBlockVpnService.ACTION_START 
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
                startVpnService()
                pendingResult?.success(true) 
            } else pendingResult?.error("REJECTED", "VPN Rejected", null)
            pendingResult = null
        }
    }
}
