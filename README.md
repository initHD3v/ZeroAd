<div align="center">

<img src="zeroad.png" alt="ZeroAd Logo" width="160" height="160" />

# ZeroAd
**Advanced Adware Detection & Global DNS Shield for Android**

![Android](https://img.shields.io/badge/Platform-Android-3DDC84?style=for-the-badge&logo=android&logoColor=white)
![Flutter](https://img.shields.io/badge/Framework-Flutter-02569B?style=for-the-badge&logo=flutter&logoColor=white)
![Kotlin](https://img.shields.io/badge/Backend-Kotlin-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)
![License](https://img.shields.io/badge/Status-Development-orange?style=for-the-badge)

ZeroAd adalah solusi keamanan Android tingkat lanjut yang dirancang untuk melindungi privasi dari gangguan adware dan pelacakan data tanpa memerlukan akses Root.

[Report Issues](https://github.com/initHD3v/ZeroAd/issues) • [Feature Request](https://github.com/initHD3v/ZeroAd/issues)

</div>

---

##  Overview

ZeroAd beroperasi secara lokal dengan menggabungkan dua lapis pertahanan: **Static Analysis** (Scanner) dan **Dynamic Traffic Filtering** (Shield). Dengan memanfaatkan `VpnService` lokal, sistem mampu mencegat paket DNS pada Port 53 tanpa mengalihkan trafik data utama ke server eksternal, menjamin latensi rendah dan keamanan data tetap terjaga di dalam perangkat.

---

##  Mekanisme Kerja (Technical Workflow)

### 1. Deep Adware Scanning
Proses ini mendeteksi potensi ancaman sebelum aplikasi dijalankan melalui:
* **Manifest Heuristics:** Pembedahan berkas `AndroidManifest.xml` untuk mendeteksi kombinasi perizinan berbahaya (misal: *Overlay* + *Boot Completed*).
* **SDK Signature Matching:** Pencocokan komponen internal aplikasi dengan database signature SDK iklan agresif.
* **Scoring System:** Pemberian penilaian risiko (**ZeroScore**) berdasarkan akumulasi temuan ancaman.

### 2. Global DNS Shield (AdBlock)
Pertahanan jaringan real-time tanpa memengaruhi kecepatan internet:
* **VpnService Interception:** Pembuatan tunnel lokal khusus untuk Port 53 (DNS). Paket data lainnya tetap mengalir langsung ke internet.
* **Fast HashSet Lookup:** Pengecekan domain pada blacklist menggunakan struktur data `HashSet` dengan kompleksitas **O(1)**.
* **NXDOMAIN Response:** Pengiriman respon "Domain Tidak Ditemukan" untuk domain iklan, sehingga konten tidak pernah terunduh.

### 3. Smart Whitelisting & Bypass
* **Dynamic Split-Tunneling:** Pembebasan aplikasi *whitelist* dari tunnel VPN di level kernel untuk akses internet 100% original.
* **Reverse Domain Mapping:** Logika heuristik untuk membedah domain dan mengatribusikannya kembali ke aplikasi asal secara akurat.

---

##  Development Milestones

<details open>
<summary><b>Fase 1: Networking Core</b></summary>

* Implementasi `VpnService` lokal untuk intersepsi Port 53.
* Pengembangan DNS Parser untuk pembacaan query UDP real-time.
</details>

<details>
<summary><b>Fase 2: Application Intelligence</b></summary>

* Scanner heuristik untuk deteksi pola perizinan agresif.
* Implementasi `ConnectivityManager` & `/proc/net/` untuk identifikasi UID pengirim trafik.
</details>

<details>
<summary><b>Fase 3: Performance & UX</b></summary>

* Migrasi ke **HashSet Engine** untuk lookup domain instan.
* Implementasi desain **Material 3** dengan hirarki informasi yang bersih.
</details>

<details>
<summary><b>Fase 4: Real-time Streaming</b></summary>

* Arsitektur **EventChannel** untuk aliran data Native Kotlin ke Flutter.
* Sistem notifikasi latar depan sesuai standar API Android terbaru.
</details>

<details>
<summary><b>Fase 5: Modularitas & High-Performance</b></summary>

* **Clean Modular Architecture** untuk skalabilitas jangka panjang.
* **Isolate-Based Processing** guna menjaga performa UI tetap stabil di 60 FPS.
* **Native Scaling Engine** untuk efisiensi manajemen memori bitmap.
</details>

---

## 🔐 Privacy & Data Handling

> [!IMPORTANT]
> Keamanan data pengguna adalah prinsip operasional utama ZeroAd.

* **Local-First Processing:** Seluruh inspeksi trafik DNS dilakukan 100% di perangkat. Tidak ada data yang dikirim ke server eksternal.
* **Zero Logging Policy:** Tidak ada penyimpanan riwayat penjelajahan atau daftar aplikasi secara permanen.
* **No External Rerouting:** Trafik internet utama tidak melalui perantara, menjamin data terenkripsi (HTTPS) tidak dapat diakses oleh sistem.

---

<div align="center">
Developed with focus on performance and privacy by <a href="https://github.com/initHD3v">initHD3v</a>
</div>
