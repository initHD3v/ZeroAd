# 📄 Laporan Kegagalan Riset: WebShield Adblock pada Chrome (Non-Root)

**Status:** GAGAL (Penelitian Dihentikan)
**Tanggal:** 10 Februari 2026
**Perangkat Uji:** Xiaomi Pad 5 (nabu) - MIUI 14 / Android 13

## 1. Ringkasan Masalah
Upaya untuk memfilter trafik iklan pada Google Chrome menggunakan metode VPN Lokal (VpnService API) tanpa akses Root mengakibatkan kemacetan total (stalling) pada browser. Koneksi internet di Chrome berhenti berfungsi meskipun status VPN aktif.

## 2. Analisis Teknikal (Penyebab Kegagalan)

### A. Chromium Network Stack Sensitivity
Chrome menggunakan engine jaringan (Cronet) yang sangat sensitif terhadap latensi. Chrome memiliki mekanisme internal bernama **"Happy Eyeballs"** dan **"Async DNS"**. 
- Permintaan DNS harus dijawab dalam hitungan milidetik (< 20ms).
- VpnService API di Android memaksa paket data melompat dari *Kernel Space* ke *User Space* (aplikasi kita). Lompatan ini saja sudah menambah latensi.
- Jika ada proses tambahan (Logging/Filter), latensi melewati ambang batas toleransi Chrome, sehingga Chrome memutus koneksi secara sepihak.

### B. MIUI Security & AppOps Conflict
Sistem operasi Xiaomi (MIUI) memiliki lapisan keamanan agresif pada `AppOpsService`.
- Setiap kali ZeroAd mencoba melakukan identifikasi aplikasi (`getConnectionOwnerUid`), MIUI melakukan validasi keamanan yang memakan waktu (blocking).
- Logcat menunjukkan `SecurityException` saat ZeroAd mencoba mengintip proses sandboxed Chrome. Ini menciptakan "deadlock" di mana Chrome menunggu internet, dan internet menunggu validasi MIUI yang ditolak.

### C. DNS over HTTPS (DoH) Bypass
Chrome secara aktif mencoba membungkus kueri DNS ke dalam protokol HTTPS (Port 443). 
- Memblokir IP DoH memaksa Chrome melakukan *fallback*.
- Proses *fallback* ini memicu timeout internal di Chrome yang seringkali berujung pada status "No Internet" jika sistem VPN tidak mampu merespons dengan kecepatan native.

## 3. Kesimpulan Riset
Implementasi WebShield yang transparan (disertai Live Activity) **tidak mungkin dilakukan secara stabil pada perangkat dengan kustomisasi OS berat seperti MIUI** tanpa akses Root. Fitur Live Activity menciptakan *overhead* yang tidak bisa ditoleransi oleh arsitektur *sandboxing* Chrome.

## 4. Rekomendasi Masa Depan
- Gunakan metode **Private DNS (DoH/DoT)** jika ingin memblokir iklan Chrome tanpa VPN.
- Jika tetap menggunakan VPN, Chrome harus **di-exclude (bypass)** sepenuhnya dari jalur filter untuk menjaga stabilitas perangkat.
