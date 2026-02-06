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
data class Threat(
    val type: String, 
    val severity: String,
    val code: String, 
    val description: String
)

@Serializable
data class AppThreatInfo(
    val packageName: String,
    val appName: String,
    val isSystemApp: Boolean,
    val detectedThreats: List<Threat>,
    val zeroScore: Int = 0
)

@Serializable
data class AdSignature(val pattern: String, val name: String)

@Serializable
data class AdSignaturesContainer(val signatures: List<AdSignature>)

@Serializable
data class ScanResultKotlin(
    val totalInstalledPackages: Int,
    val suspiciousPackagesCount: Int,
    val threats: List<AppThreatInfo>
)

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
        } catch (e: Exception) {
            listOf()
        }
    }

    override fun configureFlutterEngine(@NonNull flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL).setMethodCallHandler {
            call, result ->
            when (call.method) {
                "requestNotificationPermission" -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), NOTIF_PERMISSION_CODE)
                            result.success(false)
                        } else {
                            result.success(true)
                        }
                    } else {
                        result.success(true)
                    }
                }

                "scan" -> {
                    CoroutineScope(Dispatchers.IO).launch {
                        val packageManager: PackageManager = applicationContext.packageManager
                        val installedApps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
                        val adSignatures = loadAdSignatures()
                        val threatsDetected = mutableListOf<AppThreatInfo>()
                        var suspiciousCount = 0

                        for (appInfo in installedApps) {
                            val detectedThreatsForApp = mutableListOf<Threat>()
                            var appZeroScore = 0
                            val appName = packageManager.getApplicationLabel(appInfo).toString()
                            val isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0

                            fun getSeverityScore(severity: String): Int = when (severity) {
                                "HIGH" -> 30
                                "MEDIUM" -> 15
                                "LOW" -> 5
                                else -> 0
                            }

                            try {
                                val packageInfo = packageManager.getPackageInfo(appInfo.packageName, 
                                    PackageManager.GET_PERMISSIONS or PackageManager.GET_ACTIVITIES or PackageManager.GET_SERVICES)
                                
                                val permissions = packageInfo.requestedPermissions?.toList() ?: listOf()

                                if (permissions.contains("android.permission.REQUEST_INSTALL_PACKAGES") && !isSystemApp) {
                                    val threat = Threat("SYSTEM_CONTROL", "HIGH", "STEALTH_INSTALLER", 
                                        "App can install other applications without direct Play Store involvement.")
                                    detectedThreatsForApp.add(threat)
                                    appZeroScore += getSeverityScore(threat.severity)
                                }

                                if (permissions.contains("android.permission.RECEIVE_BOOT_COMPLETED") && 
                                    permissions.contains("android.permission.SYSTEM_ALERT_WINDOW") && !isSystemApp) {
                                    val threat = Threat("BEHAVIORAL", "HIGH", "BOOT_OVERLAY", 
                                        "App starts at boot and can draw over other apps. Highly typical of intrusive adware.")
                                    detectedThreatsForApp.add(threat)
                                    appZeroScore += getSeverityScore(threat.severity)
                                }

                                if (!isSystemApp && (appName.contains("Editor", ignoreCase = true) || appName.contains("Flashlight", ignoreCase = true))) {
                                    if (permissions.contains("android.permission.READ_SMS") || permissions.contains("android.permission.READ_CALL_LOG")) {
                                        val threat = Threat("PRIVACY", "HIGH", "PRIVACY_MINER", 
                                            "Utility app requesting sensitive personal data (SMS/Logs) unrelated to its function.")
                                        detectedThreatsForApp.add(threat)
                                        appZeroScore += getSeverityScore(threat.severity)
                                    }
                                }

                                if (permissions.contains("android.permission.BIND_ACCESSIBILITY_SERVICE") && !isSystemApp) {
                                    val threat = Threat("PERMISSION_ABUSE", "HIGH", "ACCESSIBILITY_ABUSE", "Requests full screen reading & control.")
                                    detectedThreatsForApp.add(threat)
                                    appZeroScore += getSeverityScore(threat.severity)
                                }

                                if (!isSystemApp) {
                                    val components = mutableListOf<String>()
                                    packageInfo.activities?.forEach { components.add(it.name) }
                                    packageInfo.services?.forEach { components.add(it.name) }

                                    for (sig in adSignatures) {
                                        if (components.any { it.contains(sig.pattern, ignoreCase = true) }) {
                                            val threat = Threat("AD_SDK", "MEDIUM", "AD_LIB_DETECTED", 
                                                "Embedded Ad Framework detected: ${sig.name}")
                                            detectedThreatsForApp.add(threat)
                                            appZeroScore += getSeverityScore(threat.severity)
                                            break
                                        }
                                    }
                                }

                            } catch (e: Exception) {}

                            if (detectedThreatsForApp.isNotEmpty()) {
                                threatsDetected.add(AppThreatInfo(appInfo.packageName, appName, isSystemApp, detectedThreatsForApp, appZeroScore))
                                if (appZeroScore >= 15) suspiciousCount++
                            }
                        }

                        withContext(Dispatchers.Main) {
                            val scanResultKotlin = ScanResultKotlin(installedApps.size, suspiciousCount, threatsDetected)
                            result.success(Json.encodeToString(scanResultKotlin))
                        }
                    }
                }

                "uninstallApp" -> {
                    val packageName = call.argument<String>("packageName")
                    if (packageName != null) {
                        val intent = Intent(Intent.ACTION_DELETE, Uri.parse("package:$packageName"))
                        startActivity(intent)
                        result.success(true)
                    } else result.error("ERROR", "Null package", null)
                }

                "startAdBlock" -> {
                    val intent = VpnService.prepare(this)
                    if (intent != null) {
                        pendingResult = result
                        startActivityForResult(intent, VPN_REQUEST_CODE)
                    } else {
                        startVpnService()
                        result.success(true)
                    }
                }

                "stopAdBlock" -> {
                    stopVpnService()
                    result.success(false)
                }

                "getVpnLogs" -> result.success(AdBlockVpnService.getLogs())

                "addToWhitelist" -> {
                    val packageName = call.argument<String>("packageName")
                    if (packageName != null) {
                        val prefs = getSharedPreferences("ZeroAdPrefs", Context.MODE_PRIVATE)
                        val currentSet = prefs.getStringSet("whitelisted_apps", mutableSetOf())?.toMutableSet() ?: mutableSetOf()
                        currentSet.add(packageName)
                        val success = prefs.edit().putStringSet("whitelisted_apps", currentSet).commit()
                        
                        if (success) {
                            stopVpnService()
                            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                val intent = Intent(this, AdBlockVpnService::class.java).apply { 
                                    action = AdBlockVpnService.ACTION_START 
                                    putStringArrayListExtra("whitelisted_apps", ArrayList(currentSet))
                                }
                                startService(intent)
                            }, 300)
                        }
                        result.success(success)
                    } else result.error("ERROR", "Null package", null)
                }

                "addDomainToWhitelist" -> {
                    val domain = call.argument<String>("domain")
                    if (domain != null) {
                        val prefs = getSharedPreferences("ZeroAdPrefs", Context.MODE_PRIVATE)
                        val currentSet = prefs.getStringSet("whitelisted_domains", mutableSetOf())?.toMutableSet() ?: mutableSetOf()
                        currentSet.add(domain.lowercase())
                        val success = prefs.edit().putStringSet("whitelisted_domains", currentSet).commit()
                        
                        if (success) {
                            val intent = Intent(this, AdBlockVpnService::class.java).apply { action = AdBlockVpnService.ACTION_UPDATE_WHITELIST }
                            startService(intent)
                        }
                        result.success(success)
                    } else result.error("ERROR", "Null domain", null)
                }

                "removeFromWhitelist" -> {
                    val packageName = call.argument<String>("packageName")
                    if (packageName != null) {
                        val prefs = getSharedPreferences("ZeroAdPrefs", Context.MODE_PRIVATE)
                        val currentSet = prefs.getStringSet("whitelisted_apps", mutableSetOf())?.toMutableSet() ?: mutableSetOf()
                        currentSet.remove(packageName)
                        val success = prefs.edit().putStringSet("whitelisted_apps", currentSet).commit()
                        
                        if (success) {
                            stopVpnService()
                            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                val intent = Intent(this, AdBlockVpnService::class.java).apply { 
                                    action = AdBlockVpnService.ACTION_START 
                                    putStringArrayListExtra("whitelisted_apps", ArrayList(currentSet))
                                }
                                startService(intent)
                            }, 300)
                        }
                        result.success(success)
                    } else result.error("ERROR", "Null package", null)
                }

                else -> result.notImplemented()
            }
        }

        EventChannel(flutterEngine.dartExecutor.binaryMessenger, STREAM_CHANNEL).setStreamHandler(
            object : EventChannel.StreamHandler {
                override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
                    logSink = events
                    AdBlockVpnService.getLogs().reversed().forEach {
                        sendLogToFlutter(it)
                    }
                }

                override fun onCancel(arguments: Any?) {
                    logSink = null
                }
            }
        )
    }

    private fun startVpnService() {
        val intent = Intent(this, AdBlockVpnService::class.java).apply { action = AdBlockVpnService.ACTION_START }
        startService(intent)
    }

    private fun stopVpnService() {
        val intent = Intent(this, AdBlockVpnService::class.java).apply { action = AdBlockVpnService.ACTION_STOP }
        startService(intent)
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