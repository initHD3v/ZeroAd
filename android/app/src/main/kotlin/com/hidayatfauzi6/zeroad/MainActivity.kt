package com.hidayatfauzi6.zeroad

import androidx.annotation.NonNull
import io.flutter.embedding.android.FlutterActivity
import android.content.pm.PackageManager
import android.content.pm.ApplicationInfo
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

// Data class to hold information about a specific threat
@Serializable
data class Threat(
    val type: String, // e.g., "PERMISSION_ABUSE", "AD_SDK", "BEHAVIORAL"
    val severity: String, // e.g., "HIGH", "MEDIUM", "LOW"
    val description: String
)

// Data class to hold information about detected threats in an app
@Serializable
data class AppThreatInfo(
    val packageName: String,
    val appName: String,
    val isSystemApp: Boolean,
    val detectedThreats: List<Threat>, // Now a list of Threat objects
    val zeroScore: Int = 0 // Overall threat score for the app
)

// Data class to hold the overall scan result, serializable
@Serializable
data class ScanResultKotlin(
    val totalInstalledPackages: Int,
    val suspiciousPackagesCount: Int,
    val threats: List<AppThreatInfo> // This will now directly be a List<AppThreatInfo>
)

class MainActivity : FlutterActivity() {
    private val CHANNEL = "zeroad.security/scanner"
    private val VPN_REQUEST_CODE = 101
    private var pendingResult: MethodChannel.Result? = null

    override fun configureFlutterEngine(@NonNull flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL).setMethodCallHandler {
            call, result ->
            when (call.method) {
                "scan" -> {
                    // Launch a coroutine in the IO dispatcher for background processing
                    CoroutineScope(Dispatchers.IO).launch {
                        val packageManager: PackageManager = applicationContext.packageManager
                        val installedApps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
                        val threatsDetected = mutableListOf<AppThreatInfo>()

                        var suspiciousCount = 0

                        for (appInfo in installedApps) {
                            val detectedThreatsForApp = mutableListOf<Threat>()
                            var appZeroScore = 0 // Initialize zeroScore for this app

                            val appName = packageManager.getApplicationLabel(appInfo).toString()
                            val isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0

                            // Helper for scoring severity
                            fun getSeverityScore(severity: String): Int {
                                return when (severity) {
                                    "HIGH" -> 30
                                    "MEDIUM" -> 15
                                    "LOW" -> 5
                                    else -> 0
                                }
                            }

                            // 1. Permission Analysis (Heuristic tuning & Contextual Analysis)
                            try {
                                val packageInfo = packageManager.getPackageInfo(appInfo.packageName, PackageManager.GET_PERMISSIONS or PackageManager.GET_SERVICES or PackageManager.GET_RECEIVERS)
                                val permissions = packageInfo.requestedPermissions
                                if (permissions != null) {
                                    if (permissions.contains("android.permission.BIND_ACCESSIBILITY_SERVICE")) {
                                        val threat = Threat(
                                            type = "PERMISSION_ABUSE",
                                            severity = "HIGH",
                                            description = "App requests BIND_ACCESSIBILITY_SERVICE which can be abused for malicious activities."
                                        )
                                        detectedThreatsForApp.add(threat)
                                        appZeroScore += getSeverityScore(threat.severity)
                                    }
                                    if (permissions.contains("android.permission.SYSTEM_ALERT_WINDOW")) {
                                        val threat = Threat(
                                            type = "PERMISSION_ABUSE",
                                            severity = "MEDIUM",
                                            description = "App can draw over other apps (SYSTEM_ALERT_WINDOW), often used for deceptive overlays."
                                        )
                                        detectedThreatsForApp.add(threat)
                                        appZeroScore += getSeverityScore(threat.severity)
                                    }
                                    
                                    // Contextual Permission Analysis (Placeholder)
                                    // Example: A calculator app asking for contacts or camera
                                    if (appName.contains("Calculator", ignoreCase = true) && (permissions.contains("android.permission.READ_CONTACTS") || permissions.contains("android.permission.CAMERA"))) {
                                        val threat = Threat(
                                            type = "PERMISSION_ABUSE",
                                            severity = "HIGH",
                                            description = "Calculator app requesting irrelevant permissions (Contacts/Camera)."
                                        )
                                        detectedThreatsForApp.add(threat)
                                        appZeroScore += getSeverityScore(threat.severity)
                                    }
                                    // Add more contextual checks here
                                }
                            } catch (e: PackageManager.NameNotFoundException) {
                                println("Package info not found for ${appInfo.packageName}")
                            }

                            // 2. Ad SDK Fingerprinting (Signature Detection) - Simulated Database Check
                            val knownAdSdkSignatures = listOf(
                                "com.google.android.gms.ads", // Google Mobile Ads
                                "com.facebook.ads",            // Facebook Audience Network
                                "com.applovin.sdk",            // AppLovin
                                "com.unity3d.ads",             // Unity Ads
                                "io.presage.adn",              // Presage SDK
                                "com.adcolony",                // AdColony
                                "com.ironsource",              // IronSource
                                // ... extend with more known ad SDKs
                            )
                            // Check if package name contains any known ad SDK signature
                            if (knownAdSdkSignatures.any { appInfo.packageName.contains(it, ignoreCase = true) }) {
                                val threat = Threat(
                                    type = "AD_SDK",
                                    severity = "MEDIUM", // Elevated to Medium for known SDKs
                                    description = "Contains known ad SDK package name: ${appInfo.packageName.substringAfterLast('.')}"
                                )
                                detectedThreatsForApp.add(threat)
                                appZeroScore += getSeverityScore(threat.severity)
                            } else if (appInfo.packageName.contains("ad.sdk", ignoreCase = true) || appName.contains("adware", ignoreCase = true)) {
                                val threat = Threat(
                                    type = "AD_SDK",
                                    severity = "LOW",
                                    description = "Package name or app name suggests presence of ad SDK."
                                )
                                detectedThreatsForApp.add(threat)
                                appZeroScore += getSeverityScore(threat.severity)
                            }


                            // 3. Behavioral Heuristics (Abuse Detection) - Services/Receivers
                            try {
                                val packageInfo = packageManager.getPackageInfo(appInfo.packageName, PackageManager.GET_SERVICES or PackageManager.GET_RECEIVERS)
                                val services = packageInfo.services
                                val receivers = packageInfo.receivers

                                // Check for services that run constantly without apparent user interaction in non-system apps
                                if (!isSystemApp && services != null && services.any { it.name.contains("AdService", ignoreCase = true) || it.name.contains("TrackingService", ignoreCase = true) }) {
                                    val threat = Threat(
                                        type = "BEHAVIORAL",
                                        severity = "MEDIUM",
                                        description = "Non-system app declares suspicious background service (AdService/TrackingService)."
                                    )
                                    detectedThreatsForApp.add(threat)
                                    appZeroScore += getSeverityScore(threat.severity)
                                }

                                // Check for receivers that listen to broad/suspicious intents (e.g., BOOT_COMPLETED) in non-system apps
                                if (!isSystemApp && receivers != null && receivers.any { it.name.contains("BootReceiver", ignoreCase = true) && it.enabled }) {
                                    val threat = Threat(
                                        type = "BEHAVIORAL",
                                        severity = "MEDIUM",
                                        description = "Non-system app declares a boot receiver, potentially auto-starting."
                                    )
                                    detectedThreatsForApp.add(threat)
                                    appZeroScore += getSeverityScore(threat.severity)
                                }

                                if (appInfo.packageName.contains("launcher", ignoreCase = true) && !isSystemApp) {
                                    val threat = Threat(
                                        type = "BEHAVIORAL",
                                        severity = "MEDIUM",
                                        description = "Non-system app with 'launcher' in package name detected, potentially a hidden launcher."
                                    )
                                    detectedThreatsForApp.add(threat)
                                    appZeroScore += getSeverityScore(threat.severity)
                                }


                            } catch (e: PackageManager.NameNotFoundException) {
                                println("Package info (services/receivers) not found for ${appInfo.packageName}")
                            }


                            // Add to threatsDetected list only if issues were found
                            if (detectedThreatsForApp.isNotEmpty()) {
                                threatsDetected.add(AppThreatInfo(
                                    packageName = appInfo.packageName,
                                    appName = appName,
                                    isSystemApp = isSystemApp,
                                    detectedThreats = detectedThreatsForApp,
                                    zeroScore = appZeroScore // Include the calculated zeroScore
                                ))
                                // Increment overall suspicious count only if the app's zeroScore is above a threshold
                                if (appZeroScore >= getSeverityScore("MEDIUM")) { // Example threshold
                                    suspiciousCount++
                                }
                            }
                        }

                        // Switch back to Main thread to send result
                        withContext(Dispatchers.Main) {
                            val scanResultKotlin = ScanResultKotlin(
                                totalInstalledPackages = installedApps.size,
                                suspiciousPackagesCount = suspiciousCount,
                                threats = threatsDetected // Directly use the list of threats
                            )
                            result.success(Json.encodeToString(scanResultKotlin)) // Return the entire ScanResultKotlin object as a JSON string
                        }
                    }
                }

                "uninstallApp" -> {
                    val packageName = call.argument<String>("packageName")
                    if (packageName != null) {
                        val intent = Intent(Intent.ACTION_DELETE)
                        intent.data = Uri.parse("package:$packageName")
                        startActivity(intent)
                        result.success(true) // Indicate that the intent was launched
                    } else {
                        result.error("INVALID_ARGUMENT", "Package name cannot be null", null)
                    }
                }

                "startAdBlock" -> {
                    val intent = VpnService.prepare(this)
                    if (intent != null) {
                        pendingResult = result
                        startActivityForResult(intent, VPN_REQUEST_CODE)
                    } else {
                        onActivityResult(VPN_REQUEST_CODE, Activity.RESULT_OK, null)
                        result.success(true)
                    }
                }

                "stopAdBlock" -> {
                    val intent = Intent(this, AdBlockVpnService::class.java)
                    intent.action = AdBlockVpnService.ACTION_STOP
                    startService(intent)
                    result.success(false) // Returns false to indicate inactive
                }

                "getVpnLogs" -> {
                    val logs = AdBlockVpnService.getLogs()
                    result.success(logs)
                }
                
                else -> result.notImplemented()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == VPN_REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK) {
                val intent = Intent(this, AdBlockVpnService::class.java)
                intent.action = AdBlockVpnService.ACTION_START
                startService(intent)
                pendingResult?.success(true)
            } else {
                pendingResult?.error("VPN_REJECTED", "User rejected VPN permission", null)
            }
            pendingResult = null
        }
    }
}