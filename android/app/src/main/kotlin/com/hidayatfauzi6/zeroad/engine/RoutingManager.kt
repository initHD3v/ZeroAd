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

            // 2. Local DNS Proxy
            builder.addDnsServer("10.0.0.2")
            
            // 3. DNS TRAPPING
            // Sangat penting: Kita harus menambahkan route untuk IP DNS publik agar bisa dicegat.
            // Tanpa route ini, trafik ke 8.8.8.8 akan langsung di-bypass oleh sistem Android.
            try {
                builder.addRoute("8.8.8.8", 32)
                builder.addRoute("8.8.4.4", 32)
                builder.addRoute("1.1.1.1", 32)
                builder.addRoute("1.0.0.1", 32)
            } catch (e: Exception) { Log.e("RoutingManager", "Gagal tambah DNS routes", e) }

            // IPv6 Local DNS
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                builder.addAddress("fd00::2", 128)
                builder.addDnsServer("fd00::2")
                try {
                    builder.addRoute("2001:4860:4860::8888", 128)
                    builder.addRoute("2606:4700:4700::1111", 128)
                } catch (e: Exception) {}
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
