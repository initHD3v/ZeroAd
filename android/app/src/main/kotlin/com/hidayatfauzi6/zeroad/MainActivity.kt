package com.hidayatfauzi6.zeroad

import androidx.annotation.NonNull
import io.flutter.embedding.android.FlutterActivity
import android.content.pm.PackageManager
import android.content.pm.ApplicationInfo
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.VpnService
import android.provider.Settings
import android.content.ComponentName
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.app.Activity

@Serializable
data class Threat(
    val type: String, 
    val severity: String,
    val code: String, // New field for localization and detailed mapping
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
    private val VPN_REQUEST_CODE = 101
    private var pendingResult: MethodChannel.Result? = null

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

                            // --- ADVANCED HEURISTICS (Deep Scan) ---
                            try {
                                // Request Activities & Services for Deep Component Scan
                                val packageInfo = packageManager.getPackageInfo(appInfo.packageName, 
                                    PackageManager.GET_PERMISSIONS or PackageManager.GET_ACTIVITIES or PackageManager.GET_SERVICES)
                                
                                val permissions = packageInfo.requestedPermissions?.toList() ?: listOf()

                                // 1. Stealth Installer Check
                                if (permissions.contains("android.permission.REQUEST_INSTALL_PACKAGES") && !isSystemApp) {
                                    val threat = Threat("SYSTEM_CONTROL", "HIGH", "STEALTH_INSTALLER", 
                                        "App can install other applications without direct Play Store involvement.")
                                    detectedThreatsForApp.add(threat)
                                    appZeroScore += getSeverityScore(threat.severity)
                                }

                                // 2. Boot Overlay Combo (Aggressive Adware Pattern)
                                if (permissions.contains("android.permission.RECEIVE_BOOT_COMPLETED") && 
                                    permissions.contains("android.permission.SYSTEM_ALERT_WINDOW") && !isSystemApp) {
                                    val threat = Threat("BEHAVIORAL", "HIGH", "BOOT_OVERLAY", 
                                        "App starts at boot and can draw over other apps. Highly typical of intrusive adware.")
                                    detectedThreatsForApp.add(threat)
                                    appZeroScore += getSeverityScore(threat.severity)
                                }

                                // 3. Privacy Miner (Contextual) - Only for non-system apps
                                if (!isSystemApp && (appName.contains("Editor", ignoreCase = true) || appName.contains("Flashlight", ignoreCase = true))) {
                                    if (permissions.contains("android.permission.READ_SMS") || permissions.contains("android.permission.READ_CALL_LOG")) {
                                        val threat = Threat("PRIVACY", "HIGH", "PRIVACY_MINER", 
                                            "Utility app requesting sensitive personal data (SMS/Logs) unrelated to its function.")
                                        detectedThreatsForApp.add(threat)
                                        appZeroScore += getSeverityScore(threat.severity)
                                    }
                                }

                                // 4. Basic Accessibility Abuse - Highly sensitive for non-system apps
                                if (permissions.contains("android.permission.BIND_ACCESSIBILITY_SERVICE") && !isSystemApp) {
                                    val threat = Threat("PERMISSION_ABUSE", "HIGH", "ACCESSIBILITY_ABUSE", "Requests full screen reading & control.")
                                    detectedThreatsForApp.add(threat)
                                    appZeroScore += getSeverityScore(threat.severity)
                                }

                                // --- 5. DEEP COMPONENT SCAN (Ad SDK Detection) ---
                                // Skip this for system apps as they may provide ad frameworks as a service
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
                                            break // Count once per app
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
                    val intent = Intent(this, AdBlockVpnService::class.java).apply { action = AdBlockVpnService.ACTION_STOP }
                    startService(intent)
                    result.success(false)
                }

                "getVpnLogs" -> result.success(AdBlockVpnService.getLogs())

                "addToWhitelist" -> {
                    val packageName = call.argument<String>("packageName")
                    if (packageName != null) {
                        val prefs = getSharedPreferences("ZeroAdPrefs", Context.MODE_PRIVATE)
                        val currentSet = prefs.getStringSet("whitelisted_apps", mutableSetOf())?.toMutableSet() ?: mutableSetOf()
                        currentSet.add(packageName)
                        prefs.edit().putStringSet("whitelisted_apps", currentSet).apply()
                        
                        // Notify VPN Service
                        val intent = Intent(this, AdBlockVpnService::class.java).apply { action = AdBlockVpnService.ACTION_UPDATE_WHITELIST }
                        startService(intent)
                        
                        result.success(true)
                    } else result.error("ERROR", "Null package", null)
                }

                "removeFromWhitelist" -> {
                    val packageName = call.argument<String>("packageName")
                    if (packageName != null) {
                        val prefs = getSharedPreferences("ZeroAdPrefs", Context.MODE_PRIVATE)
                        val currentSet = prefs.getStringSet("whitelisted_apps", mutableSetOf())?.toMutableSet() ?: mutableSetOf()
                        currentSet.remove(packageName)
                        prefs.edit().putStringSet("whitelisted_apps", currentSet).apply()
                        
                        // Notify VPN Service
                        val intent = Intent(this, AdBlockVpnService::class.java).apply { action = AdBlockVpnService.ACTION_UPDATE_WHITELIST }
                        startService(intent)
                        
                        result.success(true)
                    } else result.error("ERROR", "Null package", null)
                }

                else -> result.notImplemented()
            }
        }
    }

    private fun startVpnService() {
        val intent = Intent(this, AdBlockVpnService::class.java).apply { action = AdBlockVpnService.ACTION_START }
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
