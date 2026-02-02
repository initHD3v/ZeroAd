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

## ⚙️ Cara Kerja Secara Detail

ZeroAd menggabungkan dua mekanisme pertahanan utama: **Analisis Statis & Heuristik** untuk aplikasi terinstal, dan **Penyaringan Jaringan Dinamis** untuk lalu lintas internet.

### 1. Mekanisme Pemindaian Adware (Deep Scan)

Pemindaian dilakukan secara asinkron menggunakan **Kotlin Coroutines** agar tidak mengganggu performa UI. Proses ini melibatkan beberapa fase analisis:

*   **Fase Iterasi Paket:** 
    ZeroAd menggunakan API `PackageManager` untuk mengambil daftar seluruh paket yang terinstal (`getInstalledApplications`). Pada tahap ini, aplikasi sistem dan aplikasi pihak ketiga dipisahkan namun keduanya tetap dianalisis.
*   **Analisis Heuristik Izin (Contextual Permission Check):**
    Mesin pemindai tidak hanya melihat daftar izin, tapi juga melakukan korelasi. 
    *   *Contoh:* Jika aplikasi kategori "Alat/Kalkulator" meminta izin `BIND_ACCESSIBILITY_SERVICE` dan `SYSTEM_ALERT_WINDOW`, sistem akan memberikan skor penalti tinggi karena kombinasi ini sering digunakan adware untuk menampilkan iklan overlay yang sulit ditutup.
*   **Deteksi Fingerprint SDK:**
    Banyak adware membawa *Library* iklan pihak ketiga yang masif. ZeroAd memindai nama paket internal dan *signature* untuk mencari jejak SDK iklan agresif seperti **Presage**, **AppLovin**, atau **AdColony**. Jika sebuah aplikasi memiliki lebih dari 3 SDK iklan yang berbeda, tingkat risikonya akan otomatis naik.
*   **Analisis Komponen Latar Belakang:**
    Sistem memeriksa keberadaan `Service` atau `BroadcastReceiver` yang memiliki nama mencurigakan (seperti `BootReceiver` atau `AdActivity`) yang dikonfigurasi untuk berjalan otomatis saat ponsel dinyalakan (`RECEIVE_BOOT_COMPLETED`).
*   **Kalkulasi ZeroScore:**
    Setiap temuan dikonversi menjadi poin:
    *   **High Severity (30 poin):** Izin kritis yang tidak relevan dengan fungsi aplikasi.
    *   **Medium Severity (15 poin):** Keberadaan SDK iklan agresif atau boot receiver.
    *   **Low Severity (5 poin):** Nama paket yang mencurigakan.
    Aplikasi dengan total skor > 30 masuk kategori **Critical**.

### 2. Mekanisme Network Shield (VPN/DNS Interception)

Meskipun disebut "VPN", ZeroAd tidak mengenkripsi atau merutekan lalu lintas ke server luar. Ini adalah **Local DNS Proxy**:

*   **Pembuatan Interface TUN:**
    ZeroAd menggunakan `VpnService` Android untuk membuat *virtual network interface* di dalam perangkat. Semua lalu lintas internet (IPv4 & IPv6) diarahkan ke interface ini.
*   **DNS Hijacking (Non-Invasif):**
    Sistem hanya tertarik pada paket DNS (Port 53). ZeroAd tidak melakukan *Deep Packet Inspection* (DPI) terhadap payload data Anda (seperti isi chat atau password), sehingga sangat aman dan privat.
*   **Upstream Forwarding:**
    Setiap permintaan domain (misalnya `m.doubleclick.net`) dikirimkan ke server **AdGuard DNS** (`94.140.14.14`).
*   **Blocking at Resolution Level:**
    Jika domain tersebut adalah server iklan, AdGuard akan mengembalikan alamat IP `0.0.0.0` (null). Karena alamatnya tidak valid, aplikasi atau browser tidak akan pernah bisa mengunduh konten iklan tersebut. Iklan tidak akan pernah muncul karena koneksinya "mati" di tingkat DNS.

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

## ⚠️ Batasan Teknis
ZeroAd beroperasi tanpa akses root dan sepenuhnya mematuhi kebijakan keamanan Android:
*   Tidak dapat menghapus aplikasi sistem secara otomatis (memerlukan persetujuan pengguna melalui dialog sistem).
*   Tidak memodifikasi partisi `/system` atau `/vendor`.
*   Penyaringan DNS tidak dapat memblokir iklan yang dikirimkan melalui domain yang sama dengan konten utama (misalnya iklan YouTube yang terintegrasi di dalam aliran video).

---

## 🔐 Keamanan & Privasi
*   **Pemrosesan Lokal:** Semua analisis aplikasi dilakukan di dalam perangkat Anda.
*   **Tanpa Analytics:** ZeroAd tidak menggunakan pelacak pihak ketiga seperti Firebase Analytics atau Facebook SDK.
*   **Privasi Jaringan:** ZeroAd tidak melihat atau menyimpan riwayat browsing Anda.

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