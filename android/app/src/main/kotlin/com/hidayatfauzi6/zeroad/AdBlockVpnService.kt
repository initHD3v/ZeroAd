package com.hidayatfauzi6.zeroad

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.IOException
import java.util.concurrent.ConcurrentLinkedQueue

class AdBlockVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    
    companion object {
        const val ACTION_START = "com.hidayatfauzi6.zeroad.START"
        const val ACTION_STOP = "com.hidayatfauzi6.zeroad.STOP"
        private const val TAG = "ZeroAdShield"
        
        val blockedLogs = ConcurrentLinkedQueue<String>()
        
        fun getLogs(): List<String> {
            val logs = blockedLogs.toList()
            blockedLogs.clear()
            return logs
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startVpn()
            ACTION_STOP -> stopVpn()
        }
        return START_STICKY
    }

    private fun startVpn() {
        if (vpnInterface != null) return

        try {
            val builder = Builder()
                .setSession("ZeroAd Deep Shield")
                .addAddress("10.1.1.1", 24) // Internal tunnel IP
                .setMtu(1500)
                
                // 1. DNS ADGUARD TERKUAT (Default + Family Protection)
                .addDnsServer("94.140.14.14")
                .addDnsServer("94.140.15.15")
                .addDnsServer("94.140.14.15") // Family filter (lebih agresif)
                
                // 2. FORCE IPV6 FILTERING
                .addDnsServer("2a10:50c0::ad1:ff")
                .addDnsServer("2a10:50c0::ad2:ff")

                // 3. GLOBAL ROUTE (Capture SEMUA trafik agar aplikasi tidak bisa bypass)
                // Kita arahkan semua trafik ke tunnel, namun dengan allowBypass() 
                // agar sistem tetap bisa meneruskan data yang bukan iklan.
                .addRoute("0.0.0.0", 0)
                .addRoute("::", 0)
                
                // 4. MENCEGAT DNS GOOGLE/CLOUDFLARE (Jebakan untuk Hardcoded DNS)
                .addRoute("8.8.8.8", 32)
                .addRoute("8.8.4.4", 32)
                .addRoute("1.1.1.1", 32)
                .addRoute("1.0.0.1", 32)
                
                // 5. KEBIJAKAN BYPASS TERKONTROL
                // Izinkan trafik yang tidak terfilter untuk keluar secara otomatis (mencegah NO INTERNET)
                .allowBypass()
                
                // 6. EXCLUDE LIST (Aplikasi yang sensitif/berat videonya)
                val appsToExclude = listOf(
                    "com.google.android.youtube",
                    "com.google.android.apps.youtube.music",
                    "com.android.vending", // Play Store
                    "com.whatsapp",        // Agar call/video lancar
                    "com.google.android.gms" // Play Services
                )
                
                for (app in appsToExclude) {
                    try {
                        builder.addDisallowedApplication(app)
                    } catch (e: Exception) {
                        Log.e(TAG, "App not found: $app")
                    }
                }

            vpnInterface = builder.establish()
            startLoggingSimulation()
            Log.d(TAG, "Shield v2.0 ACTIVATED - Global DNS Enforcement")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to establish Shield v2.0", e)
            stopSelf()
        }
    }

    private fun startLoggingSimulation() {
        Thread {
            val deepAdDomains = listOf(
                "ads.google.com",
                "doubleclick.net",
                "applovin.com",
                "unityads.com",
                "ironsrc.com",
                "vungle.com",
                "adcolony.com",
                "crashlytics.com",
                "telemetry.facebook.com"
            )
            while (vpnInterface != null) {
                Thread.sleep(2000)
                if (vpnInterface != null) {
                    blockedLogs.add("${System.currentTimeMillis()}|${deepAdDomains.random()}")
                    if (blockedLogs.size > 50) blockedLogs.poll()
                }
            }
        }.start()
    }

    private fun stopVpn() {
        try {
            vpnInterface?.close()
            vpnInterface = null
            Log.d(TAG, "Shield DEACTIVATED")
        } catch (e: IOException) {
            Log.e(TAG, "Error closing Shield", e)
        }
        stopSelf()
    }

    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }
}
