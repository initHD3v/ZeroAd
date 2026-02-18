/// [ThreatDatabase] menyimpan seluruh data deskripsi ancaman secara terpusat.
///
/// Memisahkan data dari UI memungkinkan:
/// - Penambahan kode ancaman baru tanpa menyentuh widget
/// - Pengujian data secara independen
/// - Konsistensi penamaan dan deskripsi di seluruh aplikasi
class ThreatDatabase {
  ThreatDatabase._();

  /// Mengembalikan informasi risiko berdasarkan [code] ancaman dan [lang] bahasa.
  ///
  /// Return map berisi key: `title`, `risk`, `impact`, `recommendation`.
  static Map<String, String> getRiskInfo(String code, String lang) {
    final bool isId = lang == 'id';
    final entry = _data[code] ?? _data['UNKNOWN']!;
    return Map<String, String>.from(isId ? entry['id']! : entry['en']!);
  }

  /// Daftar semua kode ancaman yang didukung.
  static List<String> get supportedCodes => _data.keys.toList();

  static const Map<String, Map<String, Map<String, String>>> _data = {
    'STEALTH_INSTALLER': {
      'id': {
        'title': 'Pemasang Aplikasi Gelap',
        'risk':
            'Aplikasi ini memiliki kemampuan untuk memasang aplikasi lain secara diam-diam tanpa persetujuan Anda.',
        'impact':
            'Bisa memasukkan virus atau aplikasi berbahaya lainnya ke HP Anda tanpa terlihat.',
        'recommendation':
            'Sangat Disarankan Hapus: Kecuali jika ini adalah aplikasi toko aplikasi resmi.',
      },
      'en': {
        'title': 'Stealth Installer',
        'risk':
            'App can download and install other applications without Play Store involvement.',
        'impact': 'May lead to automatic background malware installation.',
        'recommendation':
            'Highly Recommend Uninstall: Unless this is an official app store.',
      },
    },
    'BOOT_OVERLAY': {
      'id': {
        'title': 'Iklan Menutup Layar',
        'risk':
            'Aplikasi otomatis berjalan saat HP baru dinyalakan dan bisa muncul tiba-tiba di atas aplikasi lain.',
        'impact':
            'Sangat mengganggu; HP akan sering muncul iklan popup bahkan saat aplikasi tidak dibuka.',
        'recommendation':
            'Saran Hapus: Perilaku ini sangat umum ditemukan pada adware/iklan sampah.',
      },
      'en': {
        'title': 'Intrusive Popups',
        'risk':
            'App starts at boot and can draw over other active applications.',
        'impact':
            'Highly intrusive; your phone will show pop-up ads unexpectedly even when the app is closed.',
        'recommendation':
            'Recommend Uninstall: This behavior is typical for intrusive adware.',
      },
    },
    'PRIVACY_MINER': {
      'id': {
        'title': 'Pencuri Data Pribadi',
        'risk':
            'Aplikasi alat sederhana (seperti senter/editor) meminta akses ke SMS atau Log Panggilan yang tidak diperlukan.',
        'impact':
            'Riwayat chat atau daftar telepon Anda bisa diambil dan dijual ke pihak ketiga.',
        'recommendation':
            'Sangat Disarankan Hapus: Aplikasi ini meminta izin yang tidak wajar untuk fungsinya.',
      },
      'en': {
        'title': 'Privacy Miner',
        'risk':
            'Simple utility app requesting access to SMS or Call Logs which is unnecessary for its function.',
        'impact':
            'Your personal data could be harvested and sold to third parties.',
        'recommendation':
            'Highly Recommend Uninstall: This app requests unusual permissions for its features.',
      },
    },
    'ACCESSIBILITY_ABUSE': {
      'id': {
        'title': 'Kontrol HP Penuh',
        'risk':
            'Meminta izin khusus untuk membaca seluruh layar dan meniru gerakan klik Anda.',
        'impact':
            'Bahaya Tinggi! Aplikasi bisa membaca password, saldo bank, dan mengambil alih kendali HP Anda.',
        'recommendation':
            'Hapus Segera: Izin ini sangat berbahaya jika diberikan kepada aplikasi yang tidak Anda percayai.',
      },
      'en': {
        'title': 'Accessibility Abuse',
        'risk':
            'Requests permission to read your entire screen and mimic your clicks.',
        'impact':
            'High Danger! App can read passwords, bank balances, and take full control of your phone.',
        'recommendation':
            'Uninstall Immediately: This permission is extremely dangerous if given to untrusted apps.',
      },
    },
    'AD_LIB_DETECTED': {
      'id': {
        'title': 'SDK Iklan Terdeteksi',
        'risk': 'Aplikasi ini menggunakan framework iklan pihak ketiga.',
        'impact':
            'Mengonsumsi kuota internet dan baterai untuk memuat konten iklan.',
        'recommendation':
            'Aman: Ini adalah jenis iklan standar yang banyak digunakan aplikasi gratis.',
      },
      'en': {
        'title': 'Ad SDK Detected',
        'risk': 'App uses a third-party advertising framework.',
        'impact':
            'Increased data usage and battery consumption to load advertisement content.',
        'recommendation':
            'Safe: This is a standard ad network used by many free legitimate apps.',
      },
    },
    'AD_SDK_ADMOB': {
      'id': {
        'title': 'Iklan Google Terintegrasi',
        'risk':
            'Aplikasi ini menggunakan jaringan iklan Google untuk menampilkan konten promosi.',
        'impact':
            'Mengonsumsi sedikit kuota internet dan baterai untuk memuat iklan.',
        'recommendation':
            'Aman: Ini adalah jenis iklan standar yang banyak digunakan aplikasi gratis.',
      },
      'en': {
        'title': 'Google Ad Integration',
        'risk': 'Contains code to display ads from the Google network.',
        'impact':
            'Increased data usage and battery consumption to load advertisement content.',
        'recommendation':
            'Safe: This is a standard ad network used by many free legitimate apps.',
      },
    },
    'AD_SDK_APPLOVIN': {
      'id': {
        'title': 'Jaringan Iklan AppLovin',
        'risk': 'Aplikasi mengandung kode iklan dari AppLovin.',
        'impact':
            'Biasanya menampilkan iklan video atau interstatial di dalam aplikasi.',
        'recommendation':
            'Biarkan: Umumnya aman, tetapi periksa jika iklan terasa terlalu mengganggu.',
      },
      'en': {
        'title': 'AppLovin Ad Network',
        'risk': 'Contains ad delivery code from AppLovin.',
        'impact': 'Typically shows video or interstitial ads within the app.',
        'recommendation':
            'Keep: Generally safe, but monitor if ads become too intrusive.',
      },
    },
    'AD_SDK_UNITY': {
      'id': {
        'title': 'Iklan Unity Ads (Game)',
        'risk':
            'Mengandung sistem iklan yang umum ditemukan pada game seluler.',
        'impact': 'Sering menampilkan iklan video yang tidak bisa dilewati.',
        'recommendation':
            'Aman: Biasa ditemukan di game gratis sebagai bentuk dukungan untuk pengembang.',
      },
      'en': {
        'title': 'Unity Ad Integration',
        'risk': 'Contains ad systems commonly found in mobile games.',
        'impact': 'Often shows unskippable video ads.',
        'recommendation': 'Safe: Common in free games to support developers.',
      },
    },
    'AD_SDK_IRONSOURCE': {
      'id': {
        'title': 'Layanan Iklan IronSource',
        'risk':
            'Menggunakan platform iklan pihak ketiga untuk promosi aplikasi.',
        'impact':
            'Dapat menampilkan iklan yang menawarkan instalasi aplikasi lain.',
        'recommendation':
            'Biarkan: Aman, tetapi waspada jika Anda sering diarahkan untuk menginstal aplikasi lain.',
      },
      'en': {
        'title': 'IronSource Ad Service',
        'risk': 'Uses a third-party ad platform for app promotion.',
        'impact': 'Can show ads suggesting you to install other apps.',
        'recommendation':
            'Keep: Safe, but be cautious if you are frequently redirected to install other apps.',
      },
    },
    'AD_SDK_VUNGLE': {
      'id': {
        'title': 'Penyedia Iklan Vungle',
        'risk': 'Mengintegrasikan konten iklan video berdurasi pendek.',
        'impact': 'Meningkatkan penggunaan data untuk memuat video iklan.',
        'recommendation': 'Aman: Jaringan iklan standar.',
      },
      'en': {
        'title': 'Vungle Ad Provider',
        'risk': 'Integrates short-form video ad content.',
        'impact': 'Increases data usage to load video ads.',
        'recommendation': 'Safe: Standard ad network.',
      },
    },
    'AD_SDK_MINTEGRAL': {
      'id': {
        'title': 'Iklan Agresif Mintegral',
        'risk': 'Aplikasi menggunakan sistem iklan dari Mintegral.',
        'impact':
            'Kadang menampilkan iklan yang lebih agresif atau sulit ditutup.',
        'recommendation':
            'Waspada: Jika Anda melihat iklan yang aneh di luar aplikasi, hapus aplikasi ini.',
      },
      'en': {
        'title': 'Mintegral Ad System',
        'risk': 'App uses the Mintegral ad system.',
        'impact':
            'Can sometimes display ads that are more aggressive or hard to close.',
        'recommendation':
            'Monitor: If you see weird ads outside the app, consider uninstalling.',
      },
    },
    'UNKNOWN': {
      'id': {
        'title': 'Aktivitas Mencurigakan',
        'risk':
            'Aplikasi melakukan tindakan yang tidak lazim di latar belakang.',
        'impact': 'Dapat mengganggu kinerja HP atau privasi Anda.',
        'recommendation':
            'Hapus jika Anda tidak merasa memasang aplikasi ini.',
      },
      'en': {
        'title': 'Suspicious Activity',
        'risk': 'App performs unusual actions in the background.',
        'impact': 'May affect phone performance or your privacy.',
        'recommendation': 'Uninstall if you do not recognize this app.',
      },
    },
  };
}
