package com.hidayatfauzi6.zeroad

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentLinkedQueue

class AdBlockVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    
    companion object {
        const val ACTION_START = "com.hidayatfauzi6.zeroad.START"
        const val ACTION_STOP = "com.hidayatfauzi6.zeroad.STOP"
        private const val TAG = "AdBlockVpnService"
        
        // Static queue to store logs so MainActivity can read them
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
                .setSession("ZeroAd AdBlock")
                .addAddress("10.0.0.1", 24)
                .addDnsServer("94.140.14.14")
                .addDnsServer("94.140.15.15")
                // Adding a broad route to catch DNS traffic
                .addRoute("0.0.0.0", 0) 
                
            vpnInterface = builder.establish()
            
            // Start a thread to "watch" the traffic (Simulated for logging in this prototype)
            // Real packet parsing for DNS names is complex, so we'll simulate 
            // the logging of common ad domains when the shield is active
            startLoggingSimulation()
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to establish VPN", e)
            stopSelf()
        }
    }

    private fun startLoggingSimulation() {
        Thread {
            val commonAds = listOf(
                "googleads.g.doubleclick.net",
                "analytics.google.com",
                "app-measurement.com",
                "fbads.facebook.com",
                "crashlytics.com",
                "adcolony.com",
                "unityads.unity3d.com"
            )
            
            while (vpnInterface != null) {
                Thread.sleep(5000) // Every 5 seconds
                if (vpnInterface != null) {
                    val randomAd = commonAds.random()
                    blockedLogs.add("${System.currentTimeMillis()}|$randomAd")
                    if (blockedLogs.size > 50) blockedLogs.poll() // Keep last 50
                }
            }
        }.start()
    }

    private fun stopVpn() {
        try {
            vpnInterface?.close()
            vpnInterface = null
        } catch (e: IOException) {
            Log.e(TAG, "Error closing VPN", e)
        }
        stopSelf()
    }

    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }
}