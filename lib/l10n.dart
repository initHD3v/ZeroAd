import 'dart:ui' as ui;

class AppLocalizations {
  final String lang;
  AppLocalizations(this.lang);

  static AppLocalizations of() {
    String deviceLang = ui.PlatformDispatcher.instance.locale.languageCode;
    return AppLocalizations(deviceLang == 'id' ? 'id' : 'en');
  }

  bool get isId => lang == 'id';

  // --- GENERAL ---
  String get appName => "ZeroAd";
  String get scannerTab => isId ? "Pemindai" : "Scanner";
  String get shieldTab => isId ? "Perisai" : "Shield";
  String get activityTab => isId ? "Aktivitas" : "Activity";
  
  // --- ACTIVITY SUB-TABS ---
  String get trafficSubTab => isId ? "Lalu Lintas" : "Traffic";
  String get trustedSubTab => isId ? "Aplikasi Terpercaya" : "Trusted Apps";

  // --- SCANNER TAB ---
  String get scanTitle => isId ? "Pemindai Adware" : "Adware Scanner";
  String get scanBtn => isId ? "Pindai Sekarang" : "Scan Now";
  String get scanningStatus => isId ? "Memindai aplikasi..." : "Scanning apps...";
  String get scanIdle => isId ? "Tekan pindai untuk memulai" : "Press scan to check device";
  String get threatsDetected => isId ? "Ancaman ditemukan!" : "Threats detected!";
  String get systemClean => isId ? "Sistem terlihat aman." : "System looks clean.";
  String get totalApps => isId ? "Total Aplikasi" : "Total Apps";
  String get threatsCount => isId ? "Ancaman" : "Threats";
  String get analysisReport => isId ? "Laporan Analisis" : "Analysis Report";
  String get criticalRisk => isId ? "Risiko Kritis" : "Critical Risk";
  String get warnings => isId ? "Peringatan" : "Warnings";
  String get lowPriority => isId ? "Prioritas Rendah" : "Low Priority";
  String get noAdwareFound => isId ? "Tidak ada adware terdeteksi.\nAnda aman!" : "No adware detected.\nYou are safe!";

  // --- SHIELD TAB ---
  String get shieldTitle => isId ? "Perisai Jaringan" : "Network Shield";
  String get protectionActive => isId ? "Perlindungan Aktif" : "Protection Active";
  String get protectionDisabled => isId ? "Perlindungan Mati" : "Protection Disabled";
  String get adGuardActive => isId ? "DNS AdGuard sedang memfilter iklan." : "AdGuard DNS is filtering ads.";
  String get enableToBlock => isId ? "Aktifkan untuk memblokir iklan di semua aplikasi." : "Enable to block ads across all apps.";
  String get connect => isId ? "HUBUNGKAN" : "CONNECT";
  String get disconnect => isId ? "PUTUSKAN" : "DISCONNECT";
  String get activeLabel => isId ? "AKTIF" : "ACTIVE";
  String get offLabel => isId ? "MATI" : "OFF";

  // --- ACTIVITY TAB ---
  String get liveActivity => isId ? "Aktivitas Langsung" : "Live Activity";
  String get blockedRequests => isId ? "Permintaan Diblokir" : "Blocked Requests";
  String get realTimeLog => isId ? "Log penyaringan DNS real-time" : "Real-time DNS filtering log";
  String get clearBtn => isId ? "Bersihkan" : "Clear";
  String get noTraffic => isId ? "Belum ada lalu lintas..." : "No traffic detected yet...";
  String get shieldInactive => isId ? "Perisai tidak aktif" : "Shield is inactive";
  String get blockedLabel => isId ? "DIBLOKIR" : "BLOCKED";

  // --- ABOUT DIALOG ---
  String get aboutTitle => isId ? "Tentang ZeroAd" : "About ZeroAd";
  String get appVersion => isId ? "Versi Aplikasi" : "App Version";
  String get developer => isId ? "Pengembang" : "Developer";
  String get appDesc => isId 
    ? "ZeroAd adalah solusi keamanan Android modern yang menggabungkan deteksi adware mendalam dengan pemblokiran iklan berbasis DNS." 
    : "ZeroAd is a modern Android security solution combining deep adware detection with DNS-based ad blocking.";
  String get closeBtn => isId ? "Tutup" : "Close";
  String get trustApp => isId ? "Percayai Aplikasi Ini" : "Trust this App";
  String get trustDomain => isId ? "Buka Blokir Tautan" : "Unblock this Link";
  String get trustAppDesc => isId ? "Izinkan semua aktivitas dari aplikasi ini." : "Allow all activities from this app.";
  String get trustDomainDesc => isId ? "Hanya izinkan alamat spesifik ini." : "Only allow this specific address.";
  String get fixIssue => isId ? "Aplikasi Bermasalah?" : "App not working?";

  // --- DETAIL PAGE ---
  String get securityDetails => isId ? "Detail Keamanan" : "Security Details";
  String get threatAnalysis => isId ? "Analisis Ancaman" : "Threat Analysis";
  String get whatIsRisk => isId ? "Apa risikonya?" : "What is the risk?";
  String get impactOnYou => isId ? "Dampak bagi Anda:" : "Impact on you:";
  String get recommendationLabel => isId ? "Saran Tindakan:" : "Recommendation:";
  String get uninstallBtn => isId ? "Hapus Aplikasi" : "Uninstall App";
  String get ignoreBtn => isId ? "Abaikan" : "Ignore";
}
