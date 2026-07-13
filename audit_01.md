# ZeroAd System Audit — audit_01.md

**Date:** 2026-07-14
**Version Audited:** 1.2.0+3
**Package:** com.hidayatfauzi6.zeroad

---

## 1. Executive Summary

ZeroAd adalah aplikasi **ad-blocking Android berbasis VPN lokal** dengan fitur pemindai adware. Setelah audit, engine 2.0 telah diintegrasikan penuh (DnsFilterEngine pipeline 9-layer aktif), MethodChannel stubs diperbaiki, error handling ditingkatkan, CI/CD ditambahkan, dan crash reporting (Sentry) terpasang.

---

## 2. Tech Stack

| Layer | Teknologi |
|-------|-----------|
| UI | Flutter 3.x, Dart SDK ^3.9.0, Material 3 |
| State Management | Provider (ChangeNotifier) |
| Native Engine | Kotlin 2.1.0, compileSdk 36, minSdk 26 |
| VPN | Android VpnService + raw socket DNS forwarding |
| Storage | SharedPreferences + flat files |
| Serialization | json_annotation 4.9 / kotlinx-serialization-json 1.6.3 |
| Async Native | kotlinx-coroutines-android 1.7.3 |

---

## 3. Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                     FLUTTER UI LAYER (Dart)                         │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐              │
│  │  Scanner Tab │  │  Shield Tab  │  │ Activity Tab │              │
│  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘              │
│         │                 │                 │                       │
│              SecurityProvider (ChangeNotifier)                       │
│              BlocklistService (Isolate processing)                   │
└───────────────────────────┼──────────────────────────────────────────┘
                            │
              MethodChannel / EventChannel
                            │
┌───────────────────────────┼──────────────────────────────────────────┐
│                  ANDROID NATIVE LAYER (Kotlin)                       │
│  MainActivity.kt (Bridge + Scanner)                                  │
│  AdBlockVpnService.kt (VPN core - ZeroAd 2.0 Engine)                 │
│  SimpleDnsParser.kt (DNS packet parser)                              │
│  engine/                                                             │
│   ├── RoutingManager.kt          - DNS-only VPN routing             │
│   ├── AdFilterEngine.kt          - Domain block/allow               │
│   ├── DnsFilterEngine.kt         - 9-layer DNS pipeline (AKTIF)     │
│   ├── DnsForwarder.kt            - Multi-provider DNS forwarding    │
│   ├── SmartBypassEngine.kt       - App categorization               │
│   ├── WhitelistManager.kt        - Dynamic whitelist management     │
│   ├── DohBlocker.kt              - DNS-over-HTTPS blocking          │
│   ├── StatisticsEngine.kt        - Real-time stats tracking         │
│   ├── TcpConnectionManager.kt    - TCP connection pooling           │
│   └── AppCategory.kt             - Shared enums                     │
└──────────────────────────────────────────────────────────────────────┘
```

### Communication: MethodChannel (`zeroad.security/scanner`) + EventChannel (`zeroad.security/stream`)

---

## 4. Codebase Metrics

| Metrik | Nilai |
|--------|-------|
| Dart source | ~2,300 lines (12 files) |
| Kotlin source | ~2,900 lines (13 files) |
| Test coverage | ~15 tests (model serialization, localization) |
| Dokumentasi | 20 markdown files |
| Git commits | 29 |
| Assets | 3 files (hosts.txt, ad_signatures.json, system_bypass.json) |

---

## 5. Findings by Severity

### 🔴 Critical

| ID | Temuan | File | Status |
|----|--------|------|--------|
| C-01 | **Engine 2.0 belum terintegrasi** — DnsFilterEngine, WhitelistManager, StatisticsEngine, DohBlocker, TcpConnectionManager sudah ditulis tapi tidak dipanggil dari AdBlockVpnService. Live code masih menggunakan forwarding langsung ke AdGuard tanpa filtering lokal. | `AdBlockVpnService.kt:113-175` | ✅ |
| C-02 | **MethodChannel stubs tidak berfungsi** — `addToWhitelist`, `removeFromWhitelist`, `updateBlocklistPath` di native hanya return `true` tanpa implementasi. Fitur whitelist pengguna tidak sinkron. | `MainActivity.kt:167-174` | ✅ |
| C-03 | **Empty catch blocks** — Error handling buruk di `handleDns`, `forwardToAdGuard`, scanner loop, `loadBlocklists`. Error ditelan tanpa logging. | Multiple files | ✅ |
| C-04 | **Tidak ada error reporting** — Tidak ada crash reporting (Sentry/Crashlytics). Crashes di native tidak terdeteksi. | — | ✅ |
| C-05 | **Zero DNS filtering lokal** — Semua query DNS langsung diteruskan ke AdGuard tanpa pengecekan blocklist lokal. Jika AdGuard offline, query di-drop (DNS failure). | `AdBlockVpnService.kt:120` | ✅ |

### 🟡 Medium

| ID | Temuan | File | Status |
|----|--------|------|--------|
| M-01 | **Test coverage sangat minim** — Hanya 15 tests untuk model + localization. Tidak ada test untuk VPN service, DNS filtering, scanner, atau UI. | `test/` | ❌ |
| M-02 | **Tidak ada CI/CD** — Tidak ada GitHub Actions, code analysis, atau build pipeline. | — | ✅ |
| M-03 | **VPN service restart tidak ada** — Tidak ada auto-reconnect jika VPN mati. START_STICKY tidak menjamin di semua vendor. | `AdBlockVpnService.kt:41` | ❌ |
| M-04 | **DatagramSocket per query** — Buka-tutup socket untuk setiap query DNS. Harusnya pool/reuse. | `AdBlockVpnService.kt:159`, `DnsForwarder.kt:99` | ✅ |
| M-05 | **SharedPreferences untuk whitelist besar** — Performance turun jika 100+ apps. | `WhitelistManager.kt:166` | ❌ |
| M-06 | **Tight loop tanpa backpressure** — `runLoop()` baca VPN FD tanpa rate limiting. Log stream via Handler.post() bisa backpressure. | `AdBlockVpnService.kt:84-111` | ⚠️ |
| M-07 | **Dual kategorisasi** — AppCategory vs LegacyAppCategory, dua sistem parallel. | `AppCategory.kt`, `AdFilterEngine.kt` | ❌ |
| M-08 | **UI logic bercampur business logic** — `home_page.dart` berisi parsing/filter yang seharusnya di provider. | `home_page.dart:310-452` | ❌ |

### 🟢 Low

| ID | Temuan | Status |
|----|--------|--------|
| L-01 | `flutter_launcher_icons` konfigurasi di pubspec.yaml root | ⚠️ |
| L-02 | `getVpnLogs()` return empty list — dead code | ⚠️ |
| L-03 | Tidak ada database (SQLite/Room) — fine untuk sekarang, limiting ke depan | ℹ️ |
| L-04 | Hardcoded string di UI tercampur dengan localized string | ⚠️ |
| L-05 | Tidak ada sertifikat pinning / integrity check blocklist download | ⚠️ |

---

## 6. Detail Temuan Kritikal

### C-01: DnsFilterEngine Tidak Terintegrasi

**Lokasi:** `AdBlockVpnService.kt:113-175`

```kotlin
// CURRENT: Langsung forward ke AdGuard tanpa filtering
private fun handleDns(requestData: ByteArray, ipHeaderLen: Int) {
    val dnsResponse = forwardToAdGuard(dnsQuery) ?: return
    // ... rebuild response packet ...
}

// SEHARUSNYA: Pipeline 9-layer filtering via DnsFilterEngine
// Layer 1: User Whitelist → Forward
// Layer 2: Google Services → Forward  
// Layer 3: DoH → Block
// Layer 4: Games → Forward to AdGuard
// Layer 5: System Whitelist → Forward
// Layer 6: Google Ads → Block
// Layer 7: Local Blocklist → Block
// Layer 8: AdGuard DNS → Forward
// Layer 9: ISP Fallback → Forward
```

**File engine sudah siap:**
- `DnsFilterEngine.kt` — 250 lines, pipeline lengkap
- `WhitelistManager.kt` — 170 lines, whitelist dengan temporary bypass
- `StatisticsEngine.kt` — 267 lines, hourly stats + per-app
- `DohBlocker.kt` — 88 lines, block DoH provider
- `SmartBypassEngine.kt` — 243 lines, kategorisasi berdasarkan package name + permissions
- `DnsForwarder.kt` — 209 lines, multi-provider dengan timeout + retry

**Dampak:** Semua engine 2.0 adalah dead code. Fitur blocklist lokal, smart bypass, statistik tidak berfungsi.

---

### C-02: MethodChannel Stubs

**Lokasi:** `MainActivity.kt:167-174`

```kotlin
"addToWhitelist", "removeFromWhitelist" -> {
    // DNS Changer tidak butuh whitelist per-aplikasi karena data otomatis bypass
    result.success(true)
}

"updateBlocklistPath" -> {
    result.success(true)
}
```

**Dampak:** Pengguna menambahkan app ke whitelist, UI menunjukkan sukses, tapi native tidak menyimpan. Blocklist path dikirim tapi tidak diproses native.

---

### C-03: Empty Catch Blocks

Tersebar di 6+ lokasi. Contoh:

```kotlin
// AdBlockVpnService.kt:156
catch (e: Exception) {}

// MainActivity.kt:110 (scanner loop)
catch (e: Exception) {}

// AdFilterEngine.kt:248 (buildKeywordMap)
catch (e: Exception) {}
```

**Dampak:** Error tidak terlihat. Debugging mustahil tanpa logcat.

---

## 7. Prioritas Perbaikan

| Priority | Action | Est. Effort | Status |
|----------|--------|-------------|--------|
| P-0 | Hapus keystore dari git (sudah di .gitignore, verifikasi) | < 1 jam | ✅ |
| P-1 | **Integrasi DnsFilterEngine** ke AdBlockVpnService | 4-6 jam | ✅ |
| P-2 | **Fix MethodChannel stub** — whitelist real, blocklist path | 1-2 jam | ✅ |
| P-3 | **Fix error handling** — ganti empty catch dengan logging | 1-2 jam | ✅ |
| P-4 | **Setup CI** — GitHub Actions build + lint | 2-3 jam | ✅ |
| P-5 | **Tambahkan crash reporting** — Sentry | 2-3 jam | ✅ |
| P-6 | **Tambahkan Integration Tests** untuk VPN + DNS pipeline | 4-6 jam | ❌ |

---

## 8. Development Roadmap

### Phase 1: Stabilisasi (1-2 minggu)
- [x] Integrasi DnsFilterEngine ke AdBlockVpnService  
- [x] Fix MethodChannel stubs
- [x] Fix empty catch blocks
- [x] Setup CI (GitHub Actions)
- [x] Integrasi Sentry crash reporting

### Phase 2: Observability & Reliability (2-3 minggu)
- [ ] Integration tests (Android instrumentation)
- [ ] Structured logging levels
- [ ] VPN health check + auto-reconnect
- [ ] DatagramSocket pooling

### Phase 3: Production Hardening (3-4 minggu)
- [ ] Database migration (Drift SQLite)
- [ ] Blocklist integrity check (SHA-256)
- [ ] Certificate pinning
- [ ] DNS-over-HTTPS forwarding
- [ ] Packet processing ring buffer

### Phase 4: Advanced Features (1-2 bulan)
- [ ] Local DNS cache (LRU)
- [ ] Custom blocklist (user add/remove UI)
- [ ] Traffic usage stats chart
- [ ] Scheduling Shield (time/WiFi-based)
- [ ] Export/Import config

### Phase 5: Cross-Platform (2-3 bulan)
- [ ] iOS (NEPacketTunnelProvider)
- [ ] Community blocklist
- [ ] Premium features tier

---

## 9. Kesimpulan

ZeroAd memiliki **fondasi arsitektur yang sangat solid** — local-first, privacy-by-design, modular. Tim telah melakukan refactoring besar (ZeroAd 2.0) yang menunjukkan pemahaman arsitektur yang baik.

**Masalah utama:** Proyek dalam keadaan transisi — engine 2.0 sudah ditulis tapi belum di-wire, engine lama sudah tidak dipakai. Ini menyebabkan semua fitur baru tidak berfungsi.

**Prioritas mutlak:** Selesaikan integrasi engine 2.0. Tanpa itu, semua kode baru adalah dead code.

**Potensi:** ZeroAd bisa menjadi alternatif open-source utama untuk AdGuard di Android, dengan investasi di testing + reliability + cross-platform.
