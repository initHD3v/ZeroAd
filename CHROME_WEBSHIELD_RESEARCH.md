# 🔬 Laporan Penelitian Teknis: Kegagalan WebShield pada Chrome (Non-Root)

**Status Proyek:** Dihentikan (Depresiasi Fitur)
**Objektif:** Melakukan pemblokiran iklan pada Google Chrome menggunakan `VpnService` API tanpa akses Root dengan tetap mempertahankan fitur *Live Activity*.

---

## 1. Kode yang Diuji (The Failed Implementation)

Berikut adalah inti dari logika asinkron yang kita terapkan untuk mencoba menyeimbangkan kecepatan internet Chrome dengan detail Live Activity:

```kotlin
// ARSITEKTUR ASYNC RESOLVER (FAILED)
private fun handleDnsRequest(packet: ByteBuffer) {
    // 1. JALUR KILAT (FAST PATH)
    // Tujuannya agar Chrome tidak menunggu lama
    val dnsInfo = SimpleDnsParser.parse(packet) ?: return
    if (filterEngine.isAd(dnsInfo.domain)) {
        SimpleDnsParser.createNullIpResponse(packet).let { res -> outputQueue.offer(res) }
    } else {
        executor?.execute {
            val response = forwardQueryShared(dnsInfo.payload)
            sendSimpleDnsPacket(packet, response)
        }
    }

    // 2. JALUR SLOW (LOGGING PATH)
    // Mencari tahu siapa pengirimnya (Inilah yang menyebabkan macet di MIUI)
    logQueue.offer(PendingLog(dnsInfo.domain, srcPort)) 
}

private fun identifyAppAsync(srcPort: Int): String {
    // Fungsi ini memicu SecurityException di MIUI jika dipanggil saat trafik padat
    val uid = connectivityManager.getConnectionOwnerUid(IPPROTO_UDP, ...)
    return packageManager.getNameForUid(uid)
}
```

---

## 2. Analisis Kegagalan Utama

### A. Konflik Arsitektur Sandbox Chrome
Chrome menggunakan proses **Sandboxed** untuk setiap tab. Proses ini tidak memiliki UID yang konsisten di mata sistem Android. Ketika aplikasi kita memanggil `getConnectionOwnerUid`, sistem Android (khususnya MIUI) melakukan *looping* validasi keamanan yang memakan waktu (blocking). 
- **Dampak:** Latensi meningkat > 500ms.
- **Respon Chrome:** Chrome menganggap jaringan "Hijacked" atau "Down" dan memutus koneksi secara paksa (stalling).

### B. MIUI AppOps Deadlock
Logcat membuktikan adanya error internal MIUI:
`E AppOps : java.lang.SecurityException: Specified package "com.hidayatfauzi6.zeroad" under uid 10089 but it is not`
Sistem MIUI tidak mengizinkan aplikasi pihak ketiga (VPN kita) untuk secara agresif menanyakan pemilik koneksi dari aplikasi sistem/Chrome. Hal ini menyebabkan "kemacetan birokrasi" di dalam kernel Android yang berujung pada internet macet total.

### C. Bottleneck Flutter-Native Bridge
Meskipun kita menggunakan *Batch Logging*, jumlah kueri DNS Chrome sangat masif (bisa ratusan per detik saat memuat halaman berita). Proses serialisasi log ke JSON untuk dikirim ke Flutter menciptakan beban CPU (Main Thread) yang memicu log:
`I/Choreographer: Skipped 540 frames! The application may be doing too much work on its main thread.`

---

## 3. Kesimpulan Diagnosa

**WebShield + Live Activity + Non-Root = Mustahil (pada MIUI/Chrome)**

Untuk mendapatkan internet Chrome yang lancar, kita harus:
1. Menghilangkan `VpnService` (Gunakan Private DNS / DoH secara manual).
2. ATAU, melakukan bypass penuh pada Chrome agar tidak melewati VPN (Inilah yang akhirnya kita pilih).

---

## 4. Peluang Penelitian Masa Depan

Bagi peneliti yang ingin melanjutkan, disarankan untuk mengeksplorasi:
1. **eBPF (Extended Berkeley Packet Filter):** Jika di masa depan Android mengizinkan penggunaan eBPF untuk aplikasi non-root (saat ini hampir mustahil).
2. **Local DoH Proxy:** Membangun proxy HTTPS lokal di dalam aplikasi yang mampu melakukan *handshake* lebih cepat daripada Chrome, namun ini membutuhkan manajemen sertifikat CA yang kompleks (User must install Root Certificate).
3. **C++ Core Engine:** Mengganti seluruh logika Kotlin dengan C++ (JNI) untuk memangkas latensi hingga ke level mikrodetik, mirip dengan yang dilakukan oleh *AdGuard*.

---
*Laporan ini dibuat sebagai arsip permanen pengembangan ZeroAd v1.2.0.*
