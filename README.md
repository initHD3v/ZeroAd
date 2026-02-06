<div align="center">

<img src="zeroad.png" alt="ZeroAd Logo" width="140" height="140" />

# ZeroAd

**Advanced Adware Detection & Global DNS Shield for Android**

ZeroAd adalah solusi keamanan Android yang dirancang untuk melindungi privasi pengguna dari gangguan adware dan pelacakan data tanpa memerlukan akses Root. Aplikasi ini menggabungkan teknik pemindaian manifest aplikasi yang mendalam dengan pertahanan jaringan berbasis DNS yang sangat efisien.

[Laporkan Masalah](https://github.com/initHD3v/ZeroAd/issues) · [Ajukan Fitur](https://github.com/initHD3v/ZeroAd/issues)

</div>

---

## 🚀 Perjalanan Pengembangan (Roadmap)

### **Fase 4: Optimasi Lanjut & Arsitektur Standar Industri (Status Saat Ini)**
*   **Modular Architecture:** Refaktorisasi total kode menjadi struktur modular (UI, Logic, Widgets) untuk skalabilitas jangka panjang.
*   **Provider State Management:** Implementasi `Provider` untuk manajemen state yang reaktif, efisien, dan performa tinggi (60 FPS).
*   **Isolate-Based Processing:** Memindahkan beban berat pemrosesan JSON manifest ke Flutter Isolate (`compute`) untuk menghindari UI Freeze.
*   **Native Icon & Label Engine:** Sistem cerdas untuk mengekstrak ikon asli dan nama aplikasi ramah pengguna langsung dari Android framework.
*   **Log Batching System:** Teknik peredaman banjir data DNS untuk menjaga penggunaan CPU tetap rendah di perangkat low-end.
*   **Reverse Domain Mapping:** Logika heuristik cerdas untuk mengidentifikasi aplikasi di balik trafik DNS sistem (seperti Shopee atau TikTok).

### **Fase sebelumnya...**
*   **VpnService Core:** Implementasi dasar terowongan VPN untuk mencegat trafik DNS.
*   **Manifest Heuristics:** Scanner tingkat lanjut untuk pola adware agresif (Boot Overlay & Accessibility Abuse).
*   **HashSet Engine:** Pemindaian ribuan domain dalam waktu mikrodetik (O(1)).
*   **Material 3 UI:** Desain antarmuka modern dengan gradien dinamis dan animasi premium.

---

## ✨ Fitur Unggulan

*   🛡️ **Deep Scanner:** Membedah aplikasi hingga ke level Activity dan Service untuk menemukan SDK iklan tersembunyi.
*   ⚡ **Smart Shield:** Melindungi seluruh perangkat dari iklan dengan konsumsi baterai yang sangat minim.
*   ✅ **Real-App Identification:** Deteksi cerdas aplikasi asli di balik trafik sistem menggunakan *Heuristic Mapping*.
*   🖼️ **Visual Experience:** Tampilan daftar aplikasi lengkap dengan ikon asli dan nama yang ramah pengguna.
*   🌐 **DNS Failover:** Otomatis beralih ke Cloudflare atau Quad9 jika Google DNS diblokir oleh ISP.

---

## 🛠️ Arsitektur Teknologi

*   **Frontend:** Flutter (Dart) - Menggunakan pola **Modular Clean Architecture**.
*   **State Management:** **Provider** - Reaktif dan performa tinggi.
*   **Backend:** Kotlin Native - Berinteraksi dengan kernel Android via `VpnService`.
*   **Communication:** `MethodChannel` & `EventChannel` dengan optimasi *Log Batching*.
*   **Performance:** **Multithreading** (Isolates di Flutter & Coroutines di Kotlin).

---

## 🔐 Privasi Tanpa Kompromi

ZeroAd dirancang dengan prinsip **Local-First**. Tidak ada data penggunaan, daftar aplikasi, atau riwayat DNS Anda yang dikirim ke server mana pun. Keamanan Anda adalah privasi Anda.

---

<div align="center">
Didesain dengan ❤️ untuk keamanan maksimal oleh <a href="https://github.com/initHD3v">initHD3v</a>
</div>