package com.hidayatfauzi6.zeroad.engine

import android.net.VpnService
import android.util.Log

class RoutingManager(private val builder: VpnService.Builder) {

    fun configureDnsOnlyRouting() {
        try {
            builder.addAddress("192.168.50.1", 24)

            // Set DNS system ke Google (sebagai umpan — DNS queries akan diarahkan ke sini)
            builder.addDnsServer("8.8.8.8")
            builder.addDnsServer("1.1.1.1")

            // Route DNS servers populer ke VPN agar bisa kita filter
            // Google DNS
            builder.addRoute("8.8.8.8", 32)
            builder.addRoute("8.8.4.4", 32)
            // Cloudflare DNS
            builder.addRoute("1.1.1.1", 32)
            builder.addRoute("1.0.0.1", 32)
            // AdGuard DNS (untuk jaga-jaga jika app hardcode)
            builder.addRoute("94.140.14.14", 32)
            builder.addRoute("94.140.15.15", 32)

            builder.setMtu(1280)
            builder.setSession("ZeroAd DNS")
            builder.allowBypass()
            
            Log.d("ZeroAd_Routing", "DNS routes configured for 6 servers")
        } catch (e: Exception) {
            Log.e("RoutingManager", "Routing Error", e)
        }
    }
}
