<div align="center">

<img src="zeroad.png" alt="ZeroAd Logo" width="140" height="140" />

# ZeroAd

**Android Adware Detection & DNS-Based Ad Blocking**

<p align="center">
<b>ZeroAd</b> adalah aplikasi keamanan Android yang berfokus pada deteksi adware dan pemblokiran iklan berbasis DNS. Dibangun dengan arsitektur ringan dan pendekatan non-invasif, ZeroAd bekerja sepenuhnya tanpa akses root dan mematuhi batasan keamanan sistem Android.
</p>

[Laporkan Bug](https://github.com/initHD3v/ZeroAd/issues) · [Ajukan Fitur](https://github.com/initHD3v/ZeroAd/issues)

</div>

---

## ✨ Fitur Utama

ZeroAd dirancang untuk memberikan perlindungan praktis terhadap adware dan iklan invasif tanpa membebani sistem.

### 🛡️ Adware Detection Engine
Mesin deteksi adware berbasis heuristik dan signature yang menganalisis aplikasi terinstal secara lokal.
*   **Analisis Heuristik**
    Mengidentifikasi pola perilaku mencurigakan seperti penyalahgunaan overlay, izin tidak relevan, dan layanan latar belakang tersembunyi.
*   **Signature Detection**
    Mendeteksi SDK iklan umum (misalnya AdMob, Unity Ads, IronSource) berdasarkan fingerprint library.
*   **Risk Classification**
    Setiap aplikasi diberi tingkat risiko: **Critical**, **Warning**, atau **Low** berdasarkan hasil analisis.
*   **Guided Mitigation**
    Menyediakan rekomendasi tindakan seperti peninjauan izin atau penghapusan aplikasi melalui mekanisme sistem Android.

---

## ⚡ Network Shield (DNS Filtering)
Perlindungan aktif berbasis jaringan menggunakan layanan VPN lokal Android.
*   **System-wide DNS Filtering**
    Memblokir resolusi domain iklan dan pelacak pada tingkat DNS.
*   **AdGuard DNS Integration**
    Menggunakan resolver DNS publik AdGuard (`94.140.14.14`) untuk stabilitas dan efektivitas.
*   **Battery Efficient**
    Mengandalkan `VpnService` Android tanpa inspeksi paket atau pemrosesan berat di latar belakang.
*   **Privacy First**
    Tidak ada lalu lintas yang dialihkan ke server ZeroAd dan tidak ada pencatatan aktivitas pengguna.

---

## 📸 Antarmuka Aplikasi

| Dashboard | Network Shield | Threat Details |
| :---: | :---: | :---: |
| <img src="https://via.placeholder.com/300x600?text=Dashboard" width="200"> | <img src="https://via.placeholder.com/300x600?text=Shield" width="200"> | <img src="https://via.placeholder.com/300x600?text=Details" width="200"> |

*Catatan: Tangkapan layar akan diperbarui mengikuti implementasi Material 3 Dark Mode.*

---

## 🛠️ Teknologi

ZeroAd menggunakan arsitektur hibrida untuk menyeimbangkan performa, fleksibilitas, dan akses sistem.

| Layer | Teknologi | Keterangan |
| :--- | :--- | :--- |
| **UI** | Flutter (Dart) | Material 3, animasi ringan, state management sederhana |
| **Core** | Kotlin (Android Native) | Package analysis, VpnService, coroutine-based async |
| **Bridge** | MethodChannel | Komunikasi efisien Flutter ↔ Native |
| **Storage** | SharedPreferences | Penyimpanan konfigurasi & whitelist |

---

## 🚀 Memulai

### Prasyarat
*   Flutter SDK 3.0+
*   Android SDK (API 21+)
*   Perangkat Android fisik (disarankan untuk pengujian VPN)

### Instalasi
1.  **Clone repositori**
    ```bash
    git clone https://github.com/initHD3v/ZeroAd.git
    cd ZeroAd
    ```
2.  **Instal dependensi**
    ```bash
    flutter pub get
    ```
3.  **Jalankan aplikasi**
    ```bash
    flutter run
    ```

---

## ⚙️ Cara Kerja

### Adware Scanner
ZeroAd memindai aplikasi menggunakan `PackageManager` dan melakukan analisis read-only berdasarkan:
1.  **Permissions** – Konsistensi antara fungsi aplikasi dan izin yang diminta.
2.  **Signatures** – Kecocokan dengan fingerprint ad SDK atau pola adware.
3.  **Components** – Deteksi layanan atau receiver tersembunyi yang berjalan di latar belakang.

Setiap aplikasi diberikan skor risiko internal (**ZeroScore**) untuk membantu pengambilan keputusan pengguna.

---

### Network Shield
ZeroAd menginisialisasi `VpnService` lokal untuk mencegat permintaan DNS. Domain iklan yang diblokir tidak akan ter-resolve, sehingga konten iklan gagal dimuat tanpa melakukan inspeksi payload jaringan.

---

## ⚠️ Batasan Teknis
ZeroAd beroperasi tanpa akses root dan sepenuhnya mematuhi kebijakan keamanan Android:
*   Tidak dapat menghapus aplikasi sistem.
*   Tidak memodifikasi partisi `/system` atau `/vendor`.
*   Tidak melakukan packet inspection selain filtering DNS.

Pendekatan ini dipilih untuk menjaga keamanan, stabilitas, dan kepatuhan terhadap kebijakan Play Store.

---

## 🔐 Keamanan & Privasi
*   ZeroAd tidak mengumpulkan data pribadi.
*   Tidak menggunakan analytics pihak ketiga.
*   Semua proses pemindaian dilakukan secara lokal.
*   Tidak ada data yang dikirim ke server eksternal.

---

## 🤝 Kontribusi
Kontribusi selalu terbuka.
1.  Fork repositori ini
2.  Buat branch fitur (`feature/nama-fitur`)
3.  Commit perubahan Anda
4.  Push ke branch tersebut
5.  Ajukan Pull Request

---

## 📝 Lisensi
Proyek ini dirilis di bawah Lisensi MIT. Lihat file `LICENSE` untuk detail lengkap.

---

<div align="center">
Dikembangkan dengan fokus pada efisiensi dan kejelasan oleh <a href="https://github.com/initHD3v">initHD3v</a>
</div>