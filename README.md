<div align="center">

<img src="zeroad.png" alt="ZeroAd Logo" width="140" height="140" />

# ZeroAd

**Advanced Adware Detection & Global DNS Shield**

<p align="center">
<b>ZeroAd</b> adalah solusi keamanan Android modern yang menggabungkan deteksi adware mendalam dengan pemblokiran iklan berbasis DNS. Kini dilengkapi dengan <b>Deep Scan Engine</b> dan dukungan <b>Multi-bahasa</b> otomatis.
</p>

[Laporkan Bug](https://github.com/initHD3v/ZeroAd/issues) · [Ajukan Fitur](https://github.com/initHD3v/ZeroAd/issues)

</div>

---

## ✨ Fitur Utama Terbaru

ZeroAd kini lebih cerdas dan komunikatif dalam melindungi perangkat Anda.

### 🛡️ Deep Scan Engine (Mesin Pemindai Mendalam)
Versi terbaru kini mampu mendeteksi pola aplikasi jahat yang lebih kompleks:
*   **Stealth Installer:** Mendeteksi aplikasi yang memiliki kemampuan menginstal paket lain tanpa interaksi pengguna.
*   **Boot Overlay Patterns:** Mengidentifikasi kombinasi izin agresif yang sering digunakan untuk menampilkan iklan popup saat HP baru menyala.
*   **Privacy Miner Detection:** Menandai aplikasi utilitas (kalkulator, editor foto) yang meminta akses sensitif ke SMS atau Log Panggilan.
*   **Detailed Risk Insights:** Setiap ancaman kini dilengkapi penjelasan manusiawi tentang **"Apa risikonya?"** dan **"Dampaknya bagi Anda"**.

### ⚡ Enhanced Network Shield (v2.0)
*   **Global DNS Hijacking:** Mencegat trafik DNS yang di-hardcode (seperti 8.8.8.8) untuk memastikan tidak ada iklan yang lolos.
*   **IPv6 Protection:** Menutup celah iklan yang masuk melalui jalur jaringan modern.
*   **Smart Bypass:** YouTube, WhatsApp, dan layanan sistem tetap lancar tanpa gangguan pemuatan konten.

### 🌐 Dukungan Multi-bahasa Otomatis
ZeroAd sekarang mendeteksi bahasa sistem perangkat Anda dan menyesuaikan seluruh antarmuka secara otomatis:
*   **Bahasa Indonesia:** Penjelasan risiko dan panduan teknis dalam bahasa lokal yang mudah dimengerti.
*   **English:** Standard professional security terminology.

---

## 📸 Antarmuka Aplikasi

<div align="center">
  <table style="width:100%">
    <tr>
      <td align="center"><b>Scanner</b></td>
      <td align="center"><b>Network Shield</b></td>
      <td align="center"><b>Live Activity</b></td>
    </tr>
    <tr>
      <td><img src="uix/Screenshot_20260202-191539.png" width="250"></td>
      <td><img src="uix/Screenshot_20260202-191545.png" width="250"></td>
      <td><img src="uix/Screenshot_20260202-191550.png" width="250"></td>
    </tr>
    <tr>
      <td align="center"><b>Risk Classification</b></td>
      <td align="center"><b>Deep Risk Analysis</b></td>
      <td></td>
    </tr>
    <tr>
      <td><img src="uix/Screenshot_20260202-191559.png" width="250"></td>
      <td><img src="uix/Screenshot_20260202-191641.png" width="250"></td>
      <td></td>
    </tr>
  </table>
</div>

---

## 🛠️ Arsitektur Teknologi

| Layer | Teknologi | Keterangan |
| :--- | :--- | :--- |
| **UI** | Flutter (Dart) | Navigasi 3-tab, Material 3, **Localization System** |
| **Core** | Kotlin Native | **Deep Heuristics**, VpnService Interface, Coroutines |
| **Build** | Gradle (Groovy) | Kompatibilitas Android 15 & Arsitektur 64-bit |
| **Storage** | SharedPreferences | Persistent Whitelist & Security States |

---

## ⚙️ Cara Kerja Deep Scan

ZeroAd tidak hanya memindai nama paket, tetapi melakukan pembedahan logika terhadap aplikasi:
1.  **Iterasi & Koleksi:** Mengumpulkan data manifes dari seluruh aplikasi.
2.  **Korelasi Izin (Heuristik):** Menghitung skor penalti jika aplikasi utilitas meminta izin sistem yang tinggi (misal: Aksesibilitas + Boot Completed).
3.  **Local Dictionary:** Mencocokkan temuan dengan database risiko lokal untuk memberikan penjelasan detail tanpa memerlukan koneksi internet.

---

## 🔐 Keamanan & Privasi
*   **Local Processing:** Semua pemindaian dilakukan 100% di dalam perangkat.
*   **Zero Data Collection:** Tidak ada data penggunaan atau riwayat DNS yang dikirim ke server ZeroAd.
*   **Standard Compliance:** Bekerja dalam batasan *Sandbox* Android tanpa memerlukan akses Root.

---

## 🤝 Kontribusi
Kontribusi untuk memperkaya database tanda tangan adware sangat kami hargai.
1.  Fork repositori ini.
2.  Tambahkan pola deteksi baru di `MainActivity.kt`.
3.  Ajukan Pull Request.

---

<div align="center">
Didesain untuk keamanan maksimal oleh <a href="https://github.com/initHD3v">initHD3v</a>
</div>