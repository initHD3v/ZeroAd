<div align="center">

<img src="zeroad.png" alt="ZeroAd Logo" width="140" height="140" />

# ZeroAd

**Advanced Adware Detection & Global DNS Shield for Android**

ZeroAd adalah aplikasi keamanan Android yang dirancang untuk memberikan perlindungan komprehensif terhadap gangguan adware dan pelacakan data. Dengan menggabungkan pemindaian heuristik mendalam (Deep Scan) dan sistem pertahanan jaringan berbasis DNS, ZeroAd memastikan perangkat Anda tetap bersih, cepat, dan privat tanpa memerlukan akses Root.

[Laporkan Bug](https://github.com/initHD3v/ZeroAd/issues) · [Ajukan Fitur](https://github.com/initHD3v/ZeroAd/issues)

</div>

---

## 🚀 Update Terbaru (Februari 2026)

Kami terus meningkatkan efisiensi dan akurasi deteksi. Berikut adalah pembaharuan signifikan pada versi terbaru:

*   **Identifikasi Aplikasi per Paket (Per-App Attribution):** Berbeda dengan filter DNS standar, ZeroAd kini mampu mendeteksi aplikasi spesifik mana yang melakukan pemanggilan domain iklan. Hal ini dicapai melalui integrasi `ConnectivityManager` untuk Android modern dan fallback `/proc/net` untuk perangkat lama.
*   **Smart DNS Failover:** Mengatasi masalah "No Internet" yang sering terjadi pada layanan VPN. Jika server DNS utama (seperti Google DNS) diblokir oleh ISP lokal, ZeroAd akan secara otomatis mengalihkan permintaan ke Cloudflare (1.1.1.1) atau Quad9 secara instan.
*   **Hierarki Live Activity:** Antarmuka pemantauan kini dikelompokkan berdasarkan aplikasi. Anda dapat melihat aplikasi mana yang paling banyak mengirimkan iklan, lalu masuk ke detail untuk melihat domain spesifik yang diblokir.
*   **Sistem Whitelist Aplikasi:** Pengguna kini memiliki kendali penuh untuk mengecualikan aplikasi tertentu dari filter iklan jika diperlukan, langsung dari layar pemantauan aktivitas.

---

## ✨ Fitur Utama

### 🛡️ Deep Scan Engine
Mesin pemindai yang bekerja dengan menganalisis manifes dan pola perilaku aplikasi untuk menemukan potensi ancaman yang sering lolos dari antivirus konvensional:
*   **Stealth Installer:** Deteksi aplikasi yang memiliki izin untuk menginstal paket lain secara diam-diam.
*   **Boot Overlay Patterns:** Mengidentifikasi aplikasi yang memaksa tampil di layar (popup) segera setelah perangkat dinyalakan.
*   **Privacy Miner:** Menandai aplikasi utilitas sederhana (seperti senter atau editor foto) yang meminta akses tidak wajar ke SMS dan log panggilan.

### ⚡ Enhanced Network Shield
Melindungi seluruh perangkat melalui sinkhole DNS lokal yang efisien:
*   **Smart Split Tunneling:** Menghemat baterai dan menjaga kecepatan dengan hanya memproses trafik DNS. Aplikasi berat seperti YouTube, WhatsApp, dan Game tetap berjalan di jalur internet asli tanpa lag.
*   **Blokade IPv6:** Menutup celah iklan yang seringkali masuk melalui jalur jaringan modern (IPv6).
*   **Sistem Label Manusiawi:** Menghilangkan kode teknis yang membingungkan dan menggantinya dengan informasi yang mudah dimengerti oleh pengguna awam.

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

*   **Frontend:** Flutter (Dart) untuk antarmuka yang responsif dan dukungan multi-bahasa.
*   **Backend Core:** Kotlin Native yang berinteraksi langsung dengan `VpnService` Android untuk performa tinggi.
*   **Security:** Implementasi SELinux-compliant API untuk deteksi pemilik koneksi pada Android 10+.

---

## 🔐 Keamanan & Privasi

Privasi adalah pondasi utama ZeroAd:
*   **Local-First Processing:** Semua data pemindaian dan log DNS diproses 100% di dalam perangkat Anda. Tidak ada data yang dikirim ke server eksternal.
*   **No Data Mining:** ZeroAd tidak mengumpulkan riwayat pencarian atau data penggunaan aplikasi Anda.
*   **Open Architecture:** Dirancang untuk bekerja dalam batasan sandbox Android demi keamanan sistem yang maksimal.

---

<div align="center">
Didesain untuk keamanan maksimal oleh <a href="https://github.com/initHD3v">initHD3v</a>
</div>
