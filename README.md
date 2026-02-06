<div align="center">

<img src="zeroad.png" alt="ZeroAd Logo" width="140" height="140" />

# ZeroAd

**Advanced Adware Detection & Global DNS Shield for Android**

ZeroAd adalah solusi keamanan Android yang dirancang untuk melindungi privasi pengguna dari gangguan adware dan pelacakan data tanpa memerlukan akses Root. Aplikasi ini menggabungkan teknik pemindaian manifest aplikasi yang mendalam dengan pertahanan jaringan berbasis DNS yang sangat efisien.

[Laporkan Masalah](https://github.com/initHD3v/ZeroAd/issues) · [Ajukan Fitur](https://github.com/initHD3v/ZeroAd/issues)

</div>

---

## 🚀 Perjalanan Pengembangan (Roadmap)

Sejak awal dibangun, ZeroAd telah melalui berbagai tahap optimasi untuk memastikan keseimbangan antara keamanan ketat dan kenyamanan pengguna. Berikut adalah sejarah evolusi sistem kami:

### **Fase 1: Fondasi & Konsep Jaringan**
*   **VpnService Core:** Implementasi dasar terowongan VPN untuk mencegat trafik DNS (Port 53).
*   **Simple DNS Parser:** Mesin ringan untuk membongkar dan membaca domain dari paket jaringan.
*   **Hardcoded Filters:** Daftar blokir awal yang ditanam langsung di dalam kode untuk pengujian konsep.

### **Fase 2: Intelegensi & Deteksi Aplikasi**
*   **Manifest Heuristics:** Scanner ditingkatkan untuk mendeteksi pola perizinan yang sering disalahgunakan oleh adware (seperti *Boot Overlay* dan *Accessibility Abuse*).
*   **Per-App Identification:** Implementasi API `ConnectivityManager` untuk mengetahui aplikasi spesifik mana yang memicu iklan secara real-time.
*   **System Awareness:** Penambahan sistem pengecualian cerdas agar aplikasi inti sistem (seperti Google Play Services) tidak dianggap sebagai ancaman.

### **Fase 3: Optimasi Performa & UX Premium**
*   **HashSet Engine:** Migrasi daftar blokir ke struktur data `HashSet` untuk memastikan pemindaian ribuan domain dilakukan dalam waktu mikrodetik (O(1)).
*   **Material 3 UI:** Perombakan antarmuka menggunakan standar desain terbaru, termasuk efek gradien dinamis dan hirarki informasi yang lebih bersih.
*   **JSON-Based Database:** Memisahkan data (database iklan & signature) dari logika program menggunakan file eksternal di folder `assets`.

### **Fase 4: Stabilitas & Real-time (Status Saat Ini)**
*   **EventChannel Streaming:** Mengganti sistem *polling* yang boros baterai dengan aliran data real-time dari Native ke Flutter.
*   **Dynamic Bypass (Jalur Tol):** Implementasi *Split Tunneling* dinamis untuk aplikasi terpercaya (seperti Shopee atau TikTok) guna mendapatkan kecepatan internet 100% asli ISP.
*   **Smart Notification:** Sistem notifikasi latar depan yang informatif, menampilkan jumlah blokir tanpa mengganggu aktivitas pengguna.
*   **Android 14/15 Ready:** Kepatuhan penuh terhadap aturan keamanan terbaru Google terkait *Foreground Service Types*.

---

## ✨ Fitur Unggulan

*   🛡️ **Deep Scanner:** Membedah aplikasi hingga ke level Activity dan Service untuk menemukan SDK iklan tersembunyi.
*   ⚡ **Smart Shield:** Melindungi seluruh perangkat dari iklan dengan konsumsi baterai yang sangat minim.
*   ✅ **Fuzzy Whitelist:** Memungkinkan pengguna memberikan izin pada aplikasi tertentu dengan satu klik, termasuk dukungan otomatis untuk anak proses (*sub-processes*).
*   🌐 **DNS Failover:** Otomatis beralih ke Cloudflare atau Quad9 jika Google DNS diblokir oleh ISP, menjamin internet tidak pernah mati.

---

## 📸 Antarmuka Terbaru

<div align="center">
  <table style="width:100%">
    <tr>
      <td align="center"><b>Dashboard Scanner</b></td>
      <td align="center"><b>Sub-Tab Activity</b></td>
      <td align="center"><b>Smart Notification</b></td>
    </tr>
    <tr>
      <td><img src="uix/Screenshot_20260202-191539.png" width="250"></td>
      <td><img src="uix/Screenshot_20260202-191550.png" width="250"></td>
      <td><img src="uix/Screenshot_20260202-191545.png" width="250"></td>
    </tr>
  </table>
</div>

---

## 🛠️ Arsitektur Teknologi

*   **Frontend:** Flutter (Dart) - Mengelola UI, state, dan logika visual.
*   **Backend:** Kotlin Native - Berinteraksi dengan kernel Android dan `VpnService`.
*   **Communication:** `MethodChannel` untuk aksi satu arah dan `EventChannel` untuk streaming data real-time.
*   **Storage:** `SharedPreferences` dengan sinkronisasi instan via `commit()`.

---

## 🔐 Privasi Tanpa Kompromi

ZeroAd dirancang dengan prinsip **Local-First**. Tidak ada data penggunaan, daftar aplikasi, atau riwayat DNS Anda yang dikirim ke server mana pun. Keamanan Anda adalah privasi Anda.

---

<div align="center">
Didesain dengan ❤️ untuk keamanan maksimal oleh <a href="https://github.com/initHD3v">initHD3v</a>
</div>