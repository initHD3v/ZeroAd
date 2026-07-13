package com.hidayatfauzi6.zeroad.engine

import android.net.VpnService
import android.util.Log

class RoutingManager(private val builder: VpnService.Builder) {

    fun configureDnsOnlyRouting() {
        try {
            // Gunakan IP internal VPN yang standar
            builder.addAddress("192.168.50.1", 24)

            // 1. Set DNS sistem ke Google & Cloudflare (sebagai umpan)
            builder.addDnsServer("8.8.8.8")
            builder.addDnsServer("1.1.1.1")

            // 2. HIJACK rute DNS tersebut agar masuk ke VPN ZeroAd
            // Hanya IP ini yang ditangkap, traffic lain (YouTube data, dll) tetap lewat ISP
            builder.addRoute("8.8.8.8", 32)
            builder.addRoute("1.1.1.1", 32)

            // 3. Stabilitas
            builder.setMtu(1280)
            builder.setSession("ZeroAd DNS")
            builder.allowBypass()
            
            Log.d("ZeroAd_Routing", "DNS Hijack Configured: 8.8.8.8 & 1.1.1.1 redirected to VPN")
        } catch (e: Exception) {
            Log.e("RoutingManager", "Routing Error", e)
        }
    }
}
