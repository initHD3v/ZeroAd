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

            // 2. Local DNS Proxy - Gunakan DNS ISP yang aman
            builder.addDnsServer("10.0.0.2")

            // 3. FIXED: ROUTING UNTUK SEMUA TRAFFIC (Bukan hanya DNS)
            // Ini penting agar YouTube dan aplikasi lain bisa connect melalui VPN
            // VPN akan menangkap SEMUA traffic, tapi HANYA filter DNS port 53
            try {
                // Route semua traffic IPv4 melalui VPN
                builder.addRoute("0.0.0.0", 0)
                
                // Tetap tambahkan route spesifik untuk DNS publik sebagai backup
                builder.addRoute("8.8.8.8", 32)
                builder.addRoute("8.8.4.4", 32)
                builder.addRoute("1.1.1.1", 32)
                builder.addRoute("1.0.0.1", 32)
            } catch (e: Exception) { 
                Log.e("RoutingManager", "Gagal tambah routes", e)
                // Fallback: jika gagal, tetap gunakan route default
                try {
                    builder.addRoute("0.0.0.0", 0)
                } catch (e2: Exception) {}
            }

            // IPv6 Local DNS
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                builder.addAddress("fd00::2", 128)
                builder.addDnsServer("fd00::2")
                try {
                    // Route semua IPv6 traffic
                    builder.addRoute("::", 0)
                } catch (e: Exception) {}
            }

            // FIXED: Allow bypass untuk aplikasi yang di-whitelist
            builder.allowBypass()
            builder.setMtu(1500)
            builder.setSession("ZeroAd Smart Shield")
            
            // FIXED: Tambahkan DNS server backup untuk fallback
            try {
                builder.addDnsServer("8.8.8.8")
                builder.addDnsServer("1.1.1.1")
            } catch (e: Exception) {}
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
