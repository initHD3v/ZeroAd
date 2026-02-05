import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'dart:ui' as ui;
import 'package:zeroad/models.dart';
import 'package:zeroad/l10n.dart';

class ThreatDetailPage extends StatelessWidget {
  final AppThreatInfo appThreatInfo;
  static const MethodChannel _platform = MethodChannel('zeroad.security/scanner');

  const ThreatDetailPage({super.key, required this.appThreatInfo});

  // Memperbaiki return type menjadi Map<String, String>
  Map<String, String> _getRiskInfo(String code, String lang) {
    bool isId = lang == 'id';
    
    final Map<String, Map<String, Map<String, String>>> data = {
      'STEALTH_INSTALLER': {
        'id': {
          'title': 'Pemasang Aplikasi Gelap',
          'risk': 'Aplikasi ini memiliki kemampuan untuk memasang aplikasi lain secara diam-diam tanpa persetujuan Anda.',
          'impact': 'Bisa memasukkan virus atau aplikasi berbahaya lainnya ke HP Anda tanpa terlihat.',
          'recommendation': 'Sangat Disarankan Hapus: Kecuali jika ini adalah aplikasi toko aplikasi resmi.'
        },
        'en': {
          'title': 'Stealth Installer',
          'risk': 'App can download and install other applications without Play Store involvement.',
          'impact': 'May lead to automatic background malware installation.',
          'recommendation': 'Highly Recommend Uninstall: Unless this is an official app store.'
        }
      },
      'BOOT_OVERLAY': {
        'id': {
          'title': 'Iklan Menutup Layar',
          'risk': 'Aplikasi otomatis berjalan saat HP baru dinyalakan dan bisa muncul tiba-tiba di atas aplikasi lain.',
          'impact': 'Sangat mengganggu; HP akan sering muncul iklan popup bahkan saat aplikasi tidak dibuka.',
          'recommendation': 'Saran Hapus: Perilaku ini sangat umum ditemukan pada adware/iklan sampah.'
        },
        'en': {
          'title': 'Intrusive Popups',
          'risk': 'App starts at boot and can draw over other active applications.',
          'impact': 'Highly intrusive; your phone will show pop-up ads unexpectedly even when the app is closed.',
          'recommendation': 'Recommend Uninstall: This behavior is typical for intrusive adware.'
        }
      },
      'PRIVACY_MINER': {
        'id': {
          'title': 'Pencuri Data Pribadi',
          'risk': 'Aplikasi alat sederhana (seperti senter/editor) meminta akses ke SMS atau Log Panggilan yang tidak diperlukan.',
          'impact': 'Riwayat chat atau daftar telepon Anda bisa diambil dan dijual ke pihak ketiga.',
          'recommendation': 'Sangat Disarankan Hapus: Aplikasi ini meminta izin yang tidak wajar untuk fungsinya.'
        },
        'en': {
          'title': 'Privacy Miner',
          'risk': 'Simple utility app requesting access to SMS or Call Logs which is unnecessary for its function.',
          'impact': 'Your personal data could be harvested and sold to third parties.',
          'recommendation': 'Highly Recommend Uninstall: This app requests unusual permissions for its features.'
        }
      },
      'ACCESSIBILITY_ABUSE': {
        'id': {
          'title': 'Kontrol HP Penuh',
          'risk': 'Meminta izin khusus untuk membaca seluruh layar dan meniru gerakan klik Anda.',
          'impact': 'Bahaya Tinggi! Aplikasi bisa membaca password, saldo bank, dan mengambil alih kendali HP Anda.',
          'recommendation': 'Hapus Segera: Izin ini sangat berbahaya jika diberikan kepada aplikasi yang tidak Anda percayai.'
        },
        'en': {
          'title': 'Accessibility Abuse',
          'risk': 'Requests permission to read your entire screen and mimic your clicks.',
          'impact': 'High Danger! App can read passwords, bank balances, and take full control of your phone.',
          'recommendation': 'Uninstall Immediately: This permission is extremely dangerous if given to untrusted apps.'
        }
      },
      'AD_SDK_ADMOB': {
        'id': {
          'title': 'Iklan Google Terintegrasi',
          'risk': 'Aplikasi ini menggunakan jaringan iklan Google untuk menampilkan konten promosi.',
          'impact': 'Mengonsumsi sedikit kuota internet dan baterai untuk memuat iklan.',
          'recommendation': 'Aman: Ini adalah jenis iklan standar yang banyak digunakan aplikasi gratis.'
        },
        'en': {
          'title': 'Google Ad Integration',
          'risk': 'Contains code to display ads from the Google network.',
          'impact': 'Increased data usage and battery consumption to load advertisement content.',
          'recommendation': 'Safe: This is a standard ad network used by many free legitimate apps.'
        }
      },
      'AD_SDK_APPLOVIN': {
        'id': {
          'title': 'Jaringan Iklan AppLovin',
          'risk': 'Aplikasi mengandung kode iklan dari AppLovin.',
          'impact': 'Biasanya menampilkan iklan video atau interstatial di dalam aplikasi.',
          'recommendation': 'Biarkan: Umumnya aman, tetapi periksa jika iklan terasa terlalu mengganggu.'
        },
        'en': {
          'title': 'AppLovin Ad Network',
          'risk': 'Contains ad delivery code from AppLovin.',
          'impact': 'Typically shows video or interstitial ads within the app.',
          'recommendation': 'Keep: Generally safe, but monitor if ads become too intrusive.'
        }
      },
      'AD_SDK_UNITY': {
        'id': {
          'title': 'Iklan Unity Ads (Game)',
          'risk': 'Mengandung sistem iklan yang umum ditemukan pada game seluler.',
          'impact': 'Sering menampilkan iklan video yang tidak bisa dilewati.',
          'recommendation': 'Aman: Biasa ditemukan di game gratis sebagai bentuk dukungan untuk pengembang.'
        },
        'en': {
          'title': 'Unity Ad Integration',
          'risk': 'Contains ad systems commonly found in mobile games.',
          'impact': 'Often shows unskippable video ads.',
          'recommendation': 'Safe: Common in free games to support developers.'
        }
      },
      'AD_SDK_IRONSOURCE': {
        'id': {
          'title': 'Layanan Iklan IronSource',
          'risk': 'Menggunakan platform iklan pihak ketiga untuk promosi aplikasi.',
          'impact': 'Dapat menampilkan iklan yang menawarkan instalasi aplikasi lain.',
          'recommendation': 'Biarkan: Aman, tetapi waspada jika Anda sering diarahkan untuk menginstal aplikasi lain.'
        },
        'en': {
          'title': 'IronSource Ad Service',
          'risk': 'Uses a third-party ad platform for app promotion.',
          'impact': 'Can show ads suggesting you to install other apps.',
          'recommendation': 'Keep: Safe, but be cautious if you are frequently redirected to install other apps.'
        }
      },
      'AD_SDK_VUNGLE': {
        'id': {
          'title': 'Penyedia Iklan Vungle',
          'risk': 'Mengintegrasikan konten iklan video berdurasi pendek.',
          'impact': 'Meningkatkan penggunaan data untuk memuat video iklan.',
          'recommendation': 'Aman: Jaringan iklan standar.'
        },
        'en': {
          'title': 'Vungle Ad Provider',
          'risk': 'Integrates short-form video ad content.',
          'impact': 'Increases data usage to load video ads.',
          'recommendation': 'Safe: Standard ad network.'
        }
      },
      'AD_SDK_MINTEGRAL': {
        'id': {
          'title': 'Iklan Agresif Mintegral',
          'risk': 'Aplikasi menggunakan sistem iklan dari Mintegral.',
          'impact': 'Kadang menampilkan iklan yang lebih agresif atau sulit ditutup.',
          'recommendation': 'Waspada: Jika Anda melihat iklan yang aneh di luar aplikasi, hapus aplikasi ini.'
        },
        'en': {
          'title': 'Mintegral Ad System',
          'risk': 'App uses the Mintegral ad system.',
          'impact': 'Can sometimes display ads that are more aggressive or hard to close.',
          'recommendation': 'Monitor: If you see weird ads outside the app, consider uninstalling.'
        }
      }
    };

    final entry = data[code] ?? {
      'id': {'title': 'Aktivitas Mencurigakan', 'risk': 'Aplikasi melakukan tindakan yang tidak lazim di latar belakang.', 'impact': 'Dapat mengganggu kinerja HP atau privasi Anda.', 'recommendation': 'Hapus jika Anda tidak merasa memasang aplikasi ini.'},
      'en': {'title': 'Suspicious Activity', 'risk': 'App performs unusual actions in the background.', 'impact': 'May affect phone performance or your privacy.', 'recommendation': 'Uninstall if you do not recognize this app.'}
    };

    return Map<String, String>.from(isId ? entry['id']! : entry['en']!);
  }

  Color _getSeverityColor(String severity) {
    switch (severity.toUpperCase()) {
      case 'HIGH': return Colors.red.shade700;
      case 'MEDIUM': return Colors.orange.shade700;
      default: return Colors.yellow.shade800;
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of();
    String deviceLang = ui.PlatformDispatcher.instance.locale.languageCode;

    return Scaffold(
      appBar: AppBar(
        title: const Text('Security Details', style: TextStyle(fontWeight: FontWeight.bold)),
      ),
      body: ListView(
        padding: const EdgeInsets.all(16.0),
        children: [
          Card(
            elevation: 0,
            color: Theme.of(context).colorScheme.surfaceContainerHighest.withAlpha(100),
            shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
            child: Padding(
              padding: const EdgeInsets.all(20),
              child: Row(
                children: [
                  CircleAvatar(
                    radius: 30,
                    backgroundColor: Theme.of(context).colorScheme.primary,
                    child: Text(appThreatInfo.appName.isNotEmpty ? appThreatInfo.appName[0] : '?', style: const TextStyle(color: Colors.white, fontSize: 24, fontWeight: FontWeight.bold)),
                  ),
                  const SizedBox(width: 16),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(appThreatInfo.appName, style: const TextStyle(fontSize: 18, fontWeight: FontWeight.bold)),
                        Text(appThreatInfo.packageName, style: const TextStyle(fontSize: 12, color: Colors.grey)),
                      ],
                    ),
                  ),
                ],
              ),
            ),
          ),
          const SizedBox(height: 24),
          Text(l10n.threatAnalysis, style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 16)),
          const SizedBox(height: 12),
          ...appThreatInfo.detectedThreats.map((threat) {
            final info = _getRiskInfo(threat.code, deviceLang);
            final color = _getSeverityColor(threat.severity);
            return Card(
              margin: const EdgeInsets.only(bottom: 16),
              shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16), side: BorderSide(color: color.withAlpha(50))),
              child: Padding(
                padding: const EdgeInsets.all(16),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      children: [
                        Icon(Icons.warning_amber_rounded, color: color),
                        const SizedBox(width: 8),
                        Text(info['title']!, style: TextStyle(fontWeight: FontWeight.bold, color: color)),
                        const Spacer(),
                        Container(
                          padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
                          decoration: BoxDecoration(color: color, borderRadius: BorderRadius.circular(4)),
                          child: Text(threat.severity, style: const TextStyle(color: Colors.white, fontSize: 10, fontWeight: FontWeight.bold)),
                        ),
                      ],
                    ),
                    const Divider(height: 24),
                    Text(l10n.whatIsRisk, style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 13)),
                    Text(info['risk']!, style: TextStyle(fontSize: 13, color: Theme.of(context).colorScheme.onSurfaceVariant)),
                    const SizedBox(height: 12),
                    Text(l10n.impactOnYou, style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 13)),
                    Text(info['impact']!, style: TextStyle(fontSize: 13, color: Theme.of(context).colorScheme.onSurfaceVariant)),
                    const SizedBox(height: 12),
                    Container(
                      padding: const EdgeInsets.all(12),
                      decoration: BoxDecoration(
                        color: color.withAlpha(20),
                        borderRadius: BorderRadius.circular(12),
                        border: Border.all(color: color.withAlpha(50)),
                      ),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Row(
                            children: [
                              Icon(Icons.info_outline, size: 16, color: color),
                              const SizedBox(width: 6),
                              Text(l10n.recommendationLabel, style: TextStyle(fontWeight: FontWeight.bold, fontSize: 13, color: color)),
                            ],
                          ),
                          const SizedBox(height: 4),
                          Text(info['recommendation']!, style: const TextStyle(fontSize: 13, fontWeight: FontWeight.w500)),
                        ],
                      ),
                    ),
                  ],
                ),
              ),
            );
          }),
          const SizedBox(height: 24),
          Row(
            children: [
              Expanded(
                child: ElevatedButton.icon(
                  onPressed: () async {
                    await _platform.invokeMethod('uninstallApp', {'packageName': appThreatInfo.packageName});
                  },
                  icon: const Icon(Icons.delete_forever),
                  label: Text(l10n.uninstallBtn),
                  style: ElevatedButton.styleFrom(
                    backgroundColor: Colors.red.shade900,
                    foregroundColor: Colors.white,
                    padding: const EdgeInsets.symmetric(vertical: 16),
                    shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                  ),
                ),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: OutlinedButton.icon(
                  onPressed: () async {
                    final prefs = await SharedPreferences.getInstance();
                    final list = prefs.getStringList('whitelisted_apps') ?? [];
                    list.add(appThreatInfo.packageName);
                    await prefs.setStringList('whitelisted_apps', list);
                    if (context.mounted) Navigator.pop(context, true);
                  },
                  icon: const Icon(Icons.verified_user),
                  label: Text(l10n.ignoreBtn),
                  style: OutlinedButton.styleFrom(
                    padding: const EdgeInsets.symmetric(vertical: 16),
                    shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                  ),
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }
}
