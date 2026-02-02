<div align="center">

  <img src="zeroad.png" alt="ZeroAd Logo" width="140" height="140">

  # ZeroAd
  **Pemindai Adware & Perisai Jaringan Android yang Tangguh**

  [![Flutter](https://img.shields.io/badge/Flutter-3.0%2B-02569B?style=for-the-badge&logo=flutter&logoColor=white)](https://flutter.dev/)
  [![Kotlin](https://img.shields.io/badge/Kotlin-1.9-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)](https://kotlinlang.org/)
  [![Platform](https://img.shields.io/badge/Platform-Android-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://www.android.com/)
  [![License](https://img.shields.io/badge/License-MIT-yellow.svg?style=for-the-badge)](LICENSE)

  <p align="center">
    <b>ZeroAd</b> adalah alat keamanan ganda yang dirancang untuk membebaskan perangkat Android Anda dari iklan invasif dan aplikasi berbahaya. Menggabungkan <b>pemindaian heuristik</b> canggih dengan <b>Perisai Jaringan DNS</b> sistem-luas.
  </p>

  [Laporkan Bug](https://github.com/initHD3v/ZeroAd/issues) • [Ajukan Fitur](https://github.com/initHD3v/ZeroAd/issues)

</div>

---

## 🔥 Fitur Ungkapan

ZeroAd beroperasi dengan filosofi "Cari & Musnahkan" yang digabungkan dengan perlindungan aktif.

### 1. 🛡️ Pemindai Adware (Cari & Musnahkan)
Mendeteksi aplikasi terinstal yang menyalahgunakan izin sistem atau mengandung tanda-tanda adware yang dikenal.
- **Analisis Heuristik:** Mengidentifikasi perilaku mencurigakan (misal: overlay tersembunyi, penyalahgunaan Layanan Aksesibilitas).
- **Deteksi Signature:** Memindai SDK Iklan yang dikenal (Unity Ads, AdMob, IronSource) yang tertanam di dalam aplikasi.
- **Kategorisasi Risiko:** Mengelompokkan ancaman secara otomatis ke dalam **Critical**, **Warning**, dan **Low Priority**.
- **Tindakan Langsung:** Hapus instalan aplikasi berbahaya langsung dari dashboard.

### 2. ⚡ Perisai Jaringan (Perlindungan Aktif)
Layanan VPN lokal yang kuat untuk memfilter lalu lintas internet dan memblokir iklan sebelum dimuat.
- **AdBlock Seluruh Sistem:** Memblokir iklan di browser, game, dan aplikasi.
- **Integrasi DNS AdGuard:** Menggunakan penyaringan DNS standar industri (`94.140.14.14`) untuk keandalan maksimum.
- **Hemat Baterai:** Menggunakan antarmuka VPN asli Android tanpa pemrosesan latar belakang yang berat.
- **Privasi Utama:** Lalu lintas difilter secara lokal melalui DNS; tidak ada data yang dicatat atau dikirim ke server eksternal.

---

## 📸 Antarmuka Aplikasi

| **Dashboard (Scanner)** | **Perisai Jaringan** | **Detail Ancaman** |
|:---:|:---:|:---:|
| <img src="https://via.placeholder.com/300x600?text=Scanner+UI" alt="Scanner UI" width="200"> | <img src="https://via.placeholder.com/300x600?text=Shield+UI" alt="Shield UI" width="200"> | <img src="https://via.placeholder.com/300x600?text=Details+UI" alt="Details UI" width="200"> |

> *Catatan: Cuplikan layar akan diperbarui dengan UI Material 3 Dark Mode terbaru.*

---

## 🛠️ Teknologi yang Digunakan

ZeroAd dibangun dengan arsitektur hibrida untuk memastikan performa tinggi dan integrasi sistem yang mendalam.

| Komponen | Teknologi | Deskripsi |
| :--- | :--- | :--- |
| **Frontend** | ![Flutter](https://img.shields.io/badge/-Flutter-02569B?logo=flutter&logoColor=white) | **Dart**. Desain Material 3, Animasi Kustom, Navigasi Bawah. |
| **Backend** | ![Kotlin](https://img.shields.io/badge/-Kotlin-7F52FF?logo=kotlin&logoColor=white) | **Android Native**. Implementasi `VpnService`, heuristik `PackageManager`, Coroutines untuk IO Async. |
| **Bridge** | **MethodChannel** | Komunikasi performa tinggi antara UI Dart dan logika Native Kotlin. |
| **Penyimpanan** | **SharedPreferences** | Manajemen status persisten untuk Whitelist dan Pengaturan. |

---

## 🚀 Memulai

### Prasyarat
- Flutter SDK (3.0 atau lebih tinggi)
- Android SDK (API 21+)
- Perangkat Android fisik (disarankan untuk pengujian VPN) atau Emulator.

### Instalasi

1. **Clone repositori**
   ```bash
   git clone https://github.com/initHD3v/ZeroAd.git
   cd zeroad
   ```

2. **Instal Dependensi**
   ```bash
   flutter pub get
   ```

3. **Jalankan Aplikasi**
   ```bash
   flutter run
   ```

---

## ⚙️ Cara Kerja

### Mesin Pemindai
Pemindai melakukan iterasi melalui `PackageManager.getInstalledApplications`. Ia menetapkan "ZeroScore" untuk setiap aplikasi berdasarkan:
1.  **Izin:** Apakah aplikasi kalkulator membutuhkan `CAMERA` atau `SYSTEM_ALERT_WINDOW`?
2.  **Signature:** Apakah nama paket cocok dengan keluarga adware yang dikenal?
3.  **Komponen:** Apakah memiliki layanan latar belakang tersembunyi (`AdService`)?

### Perisai VPN
ZeroAd membuat antarmuka `VpnService` lokal. Alih-alih merutekan lalu lintas melalui server jarak jauh, ia mencegat paket DNS dan memaksanya melalui **AdGuard DNS**. Ini mencegah domain iklan untuk terurai (resolve), secara efektif mematikan permintaan iklan di tingkat jaringan.

---

## 🤝 Kontribusi

Kontribusi adalah hal yang membuat komunitas sumber terbuka menjadi tempat yang luar biasa untuk belajar, menginspirasi, dan berkreasi. Setiap kontribusi yang Anda berikan **sangat dihargai**.

1. Fork Proyek ini
2. Buat Branch Fitur Anda (`git checkout -b feature/FiturLuarBiasa`)
3. Commit Perubahan Anda (`git commit -m 'Menambahkan Fitur Luar Biasa'`)
4. Push ke Branch tersebut (`git push origin feature/FiturLuarBiasa`)
5. Buka Pull Request

---

## 📝 Lisensi

Didistribusikan di bawah Lisensi MIT. Lihat `LICENSE` untuk informasi lebih lanjut.

---

<div align="center">
  <p>Dibuat dengan ❤️ oleh <a href="https://github.com/initHD3v">initHD3v</a></p>
</div>
