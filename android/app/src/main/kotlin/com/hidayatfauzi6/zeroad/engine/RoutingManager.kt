package com.hidayatfauzi6.zeroad.engine

import android.net.VpnService
import android.os.Build
import android.util.Log
import java.lang.Exception

class RoutingManager(private val builder: VpnService.Builder) {

    fun configureDnsOnlyRouting() {
        try {
            // 1. IP Lokal VPN
            builder.addAddress("10.0.0.2", 32)

            // 2. Trap DNS (Menggunakan IP asli agar app yang di-bypass tetap bisa resolusi DNS via ISP)
            // Dengan me-route IP ini, app yang di-filter (Game) akan masuk ke VPN.
            // App yang di-bypass (GMS, Chrome) akan langsung ke ISP.
            val dnsV4Traps = listOf("8.8.8.8", "8.8.4.4", "1.1.1.1", "1.0.0.1")
            dnsV4Traps.forEach {
                builder.addRoute(it, 32)
                builder.addDnsServer(it)
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                builder.addAddress("fd00::2", 128)
                val dnsV6Traps = listOf("2001:4860:4860::8888", "2606:4700:4700::1111")
                dnsV6Traps.forEach {
                    builder.addRoute(it, 128)
                    builder.addDnsServer(it)
                }
            }

            builder.allowBypass()
            builder.setMtu(1500)
            builder.setSession("ZeroAd Smart Shield")
        } catch (e: Exception) {
            Log.e("RoutingManager", "Error konfigurasi routing", e)
        }
    }

    fun applyBypassApps(packageName: String, essentialApps: Set<String>) {
        try { builder.addDisallowedApplication(packageName) } catch (e: Exception) {}
        essentialApps.forEach {
            try { 
                builder.addDisallowedApplication(it)
                Log.d("ZeroAdService", "Bypass Aktif (ISP Murni): $it")
            } catch (e: Exception) {}
        }
    }
}
