<div align="center">

<img src="zeroad.png" alt="ZeroAd Logo" width="140" height="140" />

# ZeroAd
**Advanced Adware Detection & Global DNS Shield for Android**

ZeroAd adalah solusi keamanan Android tingkat lanjut yang dirancang untuk melindungi privasi pengguna dari gangguan adware dan pelacakan data tanpa memerlukan akses Root. Menggabungkan teknik pemindaian manifest mendalam dengan pertahanan jaringan berbasis DNS yang sangat efisien.

[Laporkan Masalah](https://github.com/initHD3v/ZeroAd/issues) · [Ajukan Fitur](https://github.com/initHD3v/ZeroAd/issues)

</div>

---

## 🚀 Perjalanan Pengembangan (Roadmap)

Evolusi ZeroAd dibangun dengan fokus pada keseimbangan antara keamanan ketat, performa ringan, dan pengalaman pengguna premium.

### **Fase 1: Fondasi & Konsep Jaringan**
*   **VpnService Core:** Implementasi dasar terowongan VPN untuk mencegat trafik DNS pada Port 53 secara lokal.
*   **DNS Parser Engine:** Mesin ringan untuk membongkar dan membaca domain dari paket jaringan UDP secara real-time.
*   **Static Filtering:** Penggunaan daftar blokir awal untuk pengujian konsep pencegatan domain iklan.

### **Fase 2: Intelegensi & Deteksi Aplikasi**
*   **Manifest Heuristics:** Scanner cerdas untuk mendeteksi pola perizinan agresif (seperti *Boot Overlay* dan *Accessibility Abuse*).
*   **Native UID Identification:** Implementasi API `ConnectivityManager` dan scanning `/proc/net/` untuk mengetahui aplikasi pengirim trafik.
*   **System Awareness:** Sistem pengecualian otomatis untuk aplikasi inti sistem agar stabilitas perangkat tetap terjaga.

### **Fase 3: Optimasi Performa & UX Premium**
*   **HashSet Engine:** Migrasi database filter ke struktur data `HashSet` untuk pencarian domain dalam waktu mikrodetik (O(1)).
*   **Material 3 UI:** Perombakan antarmuka menggunakan standar desain Google terbaru, efek gradien dinamis, dan hirarki informasi yang bersih.
*   **JSON Signature Database:** Pemisahan logika program dengan database signature iklan eksternal untuk kemudahan pembaruan.

### **Fase 4: Stabilitas & Streaming Real-time**
*   **EventChannel Architecture:** Migrasi dari sistem *polling* ke aliran data real-time dari Native Kotlin ke Flutter.
*   **Dynamic Bypass:** Implementasi *Split Tunneling* dinamis untuk aplikasi terpercaya guna mendapatkan kecepatan internet asli ISP.
*   **Smart Notification:** Sistem notifikasi latar depan yang informatif dan patuh terhadap aturan Android 14/15.

### **Fase 5: Modularitas & High-Performance (Status Saat Ini)**
*   **Clean Modular Architecture:** Refaktorisasi total kode ke dalam modul UI, Logic, dan Widgets untuk skalabilitas industri.
*   **Provider State Management:** Implementasi manajemen state terpusat yang reaktif dan sangat hemat sumber daya.
*   **Isolate-Based Processing:** Pemindahan beban berat pemindaian manifest ke *Background Isolate* untuk menjaga UI tetap mulus (60 FPS).
*   **Reverse Domain Mapping:** Logika heuristik untuk mengidentifikasi aplikasi di balik trafik sistem (seperti Shopee/TikTok) yang sering tersembunyi.
*   **Native Scaling Engine:** Sistem cerdas pengambil ikon dan label aplikasi asli dengan optimasi memori bitmap.

---

## ✨ Fitur Unggulan

*   🛡️ **Deep Scanner:** Membedah aplikasi hingga level Activity & Service untuk menemukan SDK iklan tersembunyi.
*   ⚡ **Smart Shield:** Perlindungan DNS global dengan konsumsi baterai minimal dan efisiensi CPU maksimal.
*   ✅ **Real-App Attribution:** Deteksi akurat aplikasi asli di balik trafik DNS sistem melalui *Heuristic Mapping*.
*   🖼️ **Premium Visuals:** Antarmuka modern yang menampilkan ikon asli aplikasi untuk identifikasi yang lebih manusiawi.
*   🌐 **DNS Failover:** Otomatis beralih ke Cloudflare, Google, atau Quad9 jika terjadi gangguan jaringan.

---

## 🛠️ Arsitektur Teknologi

*   **Frontend:** Flutter (Dart) - Modular Clean Architecture.
*   **State Management:** Provider - Reactive & Performant.
*   **Backend:** Kotlin Native - Android VpnService & Kernel Interaction.
*   **Performance:** Multithreading (Isolates & Coroutines) + Log Batching System.

---

## 🔐 Privasi Tanpa Kompromi

ZeroAd dirancang dengan prinsip **Local-First**. Tidak ada data penggunaan, daftar aplikasi, atau riwayat DNS Anda yang dikirim ke server mana pun. Seluruh proses pemindaian dan pemblokiran terjadi 100% di dalam perangkat Anda.

---

<div align="center">
Didesain dengan ❤️ untuk keamanan maksimal oleh <a href="https://github.com/initHD3v">initHD3v</a>
</div>