package com.hidayatfauzi6.zeroad

import java.nio.ByteBuffer

// Helper Object untuk memproses DNS tanpa pusing header
object SimpleDnsParser {

    data class DnsInfo(val domain: String, val payload: ByteArray)

    fun parse(packet: ByteBuffer): DnsInfo? {
        val ipHeaderLen = (packet.get(0).toInt() and 0x0F) * 4
        val udpOffset = ipHeaderLen
        val dnsOffset = udpOffset + 8
        
        if (packet.limit() <= dnsOffset) return null

        // Ambil DNS Payload
        val payloadLen = packet.limit() - dnsOffset
        val payload = ByteArray(payloadLen)
        packet.position(dnsOffset)
        packet.get(payload)

        // Parse Domain Name dari DNS Payload
        // Skip Header (12 bytes)
        val domain = extractDomain(payload, 12)
        
        return DnsInfo(domain, payload)
    }

    // Ekstraktor domain yang tahan Kompresi (Pointer 0xC0)
    private fun extractDomain(data: ByteArray, offset: Int): String {
        val sb = StringBuilder()
        var pos = offset
        var jumped = false
        var jumps = 0 // Anti infinite loop

        while (pos < data.size && jumps < 10) {
            val len = data[pos].toInt() and 0xFF
            if (len == 0) break
            
            if ((len and 0xC0) == 0xC0) {
                // Pointer detected! Jump to offset.
                if (!jumped) jumped = true // Hanya sekali kita perlu track posisi asli
                val b2 = data[pos + 1].toInt() and 0xFF
                val newOffset = ((len and 0x3F) shl 8) or b2
                pos = newOffset
                jumps++
                continue
            }
            
            pos++
            if (pos + len > data.size) break
            
            for (i in 0 until len) {
                sb.append(data[pos + i].toChar())
            }
            sb.append('.')
            pos += len
        }
        return sb.toString().trimEnd('.')
    }

    // Membuat paket respon "NXDOMAIN" (Not Found)
    // Kita gunakan ulang header request, lalu tukar IP & Port
    fun createNxDomainResponse(request: ByteBuffer): ByteArray {
        val ipHeaderLen = (request.get(0).toInt() and 0x0F) * 4
        val raw = request.array().copyOf(request.limit())
        val buffer = ByteBuffer.wrap(raw)

        // 1. SWAP IP (Src <-> Dst)
        val srcIp = ByteArray(4); val dstIp = ByteArray(4)
        buffer.position(12); buffer.get(srcIp); buffer.get(dstIp)
        buffer.position(12); buffer.put(dstIp); buffer.put(srcIp)

        // 2. SWAP Port (Src <-> Dst)
        buffer.position(ipHeaderLen)
        val srcPort = buffer.short; val dstPort = buffer.short
        buffer.position(ipHeaderLen); buffer.putShort(dstPort); buffer.putShort(srcPort)

        // 3. Modifikasi DNS Flags (NXDOMAIN)
        val dnsStart = ipHeaderLen + 8
        // Flags ada di byte ke-2 dan 3 dari DNS Header
        // 0x8183 = Response, Recursion Avail, NXDOMAIN error
        buffer.put(dnsStart + 2, 0x81.toByte()) 
        buffer.put(dnsStart + 3, 0x83.toByte())

        // 4. Reset Checksum IP (Set 0 biar aman)
        buffer.putShort(10, 0)
        // Reset Checksum UDP (Set 0 = Disabled)
        buffer.putShort(ipHeaderLen + 6, 0)

        return raw
    }

    // Membuat respon "A Record" dengan IP 0.0.0.0
    // Ini lebih baik daripada NXDOMAIN karena menipu aplikasi agar menganggap internet ada
    fun createNullIpResponse(request: ByteBuffer): ByteArray {
        val ipHeaderLen = (request.get(0).toInt() and 0x0F) * 4
        val dnsStart = ipHeaderLen + 8
        
        // Ambil DNS Payload asli untuk mengekstrak ID dan Question
        val requestArray = request.array().copyOf(request.limit())
        val dnsId = requestArray[dnsStart].toInt() and 0xFF shl 8 or (requestArray[dnsStart + 1].toInt() and 0xFF)
        
        // Cari akhir dari Question Section (biasanya setelah domain name + 4 bytes untuk Type & Class)
        var pos = dnsStart + 12
        while (pos < requestArray.size && requestArray[pos].toInt() != 0) {
            val len = requestArray[pos].toInt() and 0xFF
            if ((len and 0xC0) == 0xC0) { pos += 2; break }
            pos += len + 1
        }
        if (pos < requestArray.size && requestArray[pos].toInt() == 0) pos++ // Skip null terminator
        pos += 4 // Skip Type & Class
        
        val questionLen = pos - dnsStart
        
        // Bangun DNS Response (Header + Question + Answer)
        // Header (12) + Question + Answer (Name Pointer(2) + Type(2) + Class(2) + TTL(4) + RDLen(2) + RData(4))
        val responsePayloadSize = 12 + (questionLen - 12) + 16
        val resPayload = ByteBuffer.allocate(responsePayloadSize)
        
        // DNS Header
        resPayload.putShort(dnsId.toShort())
        resPayload.putShort(0x8180.toShort()) // Flags: Standard query response, No error
        resPayload.putShort(1.toShort()) // Questions count
        resPayload.putShort(1.toShort()) // Answer count
        resPayload.putShort(0.toShort()) // Authority count
        resPayload.putShort(0.toShort()) // Additional count
        
        // Copy Question Section
        resPayload.put(requestArray, dnsStart + 12, questionLen - 12)
        
        // Answer Section
        resPayload.putShort(0xC00C.toShort()) // Name: Pointer to domain in question
        resPayload.putShort(1.toShort())      // Type: A
        resPayload.putShort(1.toShort())      // Class: IN
        resPayload.putInt(60)                 // TTL: 60 seconds
        resPayload.putShort(4.toShort())      // RDLENGTH: 4 bytes
        resPayload.put(byteArrayOf(0, 0, 0, 0)) // RDATA: 0.0.0.0
        
        return createResponsePacket(request, resPayload.array())
    }

    // Membungkus respon DNS asli (dari internet) ke dalam paket IP untuk dikirim ke Game
    fun createResponsePacket(request: ByteBuffer, dnsResponsePayload: ByteArray): ByteArray {
        val ipHeaderLen = (request.get(0).toInt() and 0x0F) * 4
        
        // Total Length baru = IP Header + UDP Header + New DNS Payload
        val totalLen = ipHeaderLen + 8 + dnsResponsePayload.size
        val response = ByteBuffer.allocate(totalLen)

        // Copy IP Header dari Request (sebagai template)
        response.put(request.array(), 0, ipHeaderLen)
        
        // Swap IP
        val srcIp = ByteArray(4); val dstIp = ByteArray(4)
        request.position(12); request.get(srcIp); request.get(dstIp)
        response.position(12); response.put(dstIp); response.put(srcIp)

        // IP Total Length
        response.putShort(2, totalLen.toShort())
        // IP Checksum 0 (Biar HP hitung sendiri atau terima apa adanya)
        response.putShort(10, 0)

        // Bangun UDP Header
        request.position(ipHeaderLen)
        val srcPort = request.short; val dstPort = request.short
        response.position(ipHeaderLen)
        response.putShort(dstPort) // Swap
        response.putShort(srcPort) // Swap
        response.putShort((8 + dnsResponsePayload.size).toShort()) // UDP Length
        response.putShort(0) // UDP Checksum 0

        // Masukkan DNS Payload
        response.put(dnsResponsePayload)

        return response.array()
    }
}