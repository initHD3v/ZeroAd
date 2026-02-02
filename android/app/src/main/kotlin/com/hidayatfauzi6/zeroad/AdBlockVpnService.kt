package com.hidayatfauzi6.zeroad

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.IOException

class AdBlockVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null

    companion object {
        const val ACTION_START = "com.hidayatfauzi6.zeroad.START"
        const val ACTION_STOP = "com.hidayatfauzi6.zeroad.STOP"
        private const val TAG = "AdBlockVpnService"
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startVpn()
            ACTION_STOP -> stopVpn()
        }
        return START_STICKY
    }

    private fun startVpn() {
        if (vpnInterface != null) {
            Log.d(TAG, "VPN is already running")
            return
        }

        try {
            val builder = Builder()
                .setSession("ZeroAd AdBlock")
                .addAddress("10.0.0.1", 24) // Dummy local address
                // AdGuard DNS (Default filtering)
                .addDnsServer("94.140.14.14")
                .addDnsServer("94.140.15.15")
                // Blocking IPv6 ads too
                .addDnsServer("2a10:50c0::ad1:ff")
                .addDnsServer("2a10:50c0::ad2:ff")
                
            // We only want to intercept DNS traffic if possible, 
            // but the simplest reliable way is to let the system 
            // use our DNS servers for all traffic routed through here.
            
            vpnInterface = builder.establish()
            Log.d(TAG, "VPN Interface established with AdGuard DNS")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to establish VPN interface", e)
            stopSelf()
        }
    }

    private fun stopVpn() {
        try {
            vpnInterface?.close()
            vpnInterface = null
            Log.d(TAG, "VPN Interface closed")
        } catch (e: IOException) {
            Log.e(TAG, "Error closing VPN interface", e)
        }
        stopSelf()
    }

    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }
}
