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

            // 2. Rute Khusus DNS Virtual
            // Kita buat IP 'palsu' 10.0.0.1 sebagai tujuan DNS.
            // Dengan hanya me-route IP ini, data browsing Chrome DIJAMIN 100% lewat ISP.
            builder.addRoute("10.0.0.1", 32)

            // 3. Daftarkan 10.0.0.1 sebagai DNS Server sistem
            builder.addDnsServer("10.0.0.1")
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                builder.addAddress("fd00::2", 128)
                builder.addRoute("fd00::1", 128)
                builder.addDnsServer("fd00::1")
            }

            builder.allowBypass()
            builder.setMtu(1280)
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
