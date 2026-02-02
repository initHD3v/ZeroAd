import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:google_fonts/google_fonts.dart';
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
          'title': 'Pemasang Tersembunyi',
          'risk': 'Aplikasi ini dapat mengunduh dan menginstal aplikasi lain tanpa melalui Play Store.',
          'impact': 'Bisa menyebabkan instalasi malware otomatis di latar belakang.'
        },
        'en': {
          'title': 'Stealth Installer',
          'risk': 'App can download and install other applications without Play Store involvement.',
          'impact': 'May lead to automatic background malware installation.'
        }
      },
      'BOOT_OVERLAY': {
        'id': {
          'title': 'Iklan Overlay Otomatis',
          'risk': 'Aplikasi berjalan saat HP menyala dan bisa tampil di atas aplikasi lain.',
          'impact': 'Sangat mengganggu; HP akan sering muncul iklan popup tiba-tiba.'
        },
        'en': {
          'title': 'Boot Overlay',
          'risk': 'App starts at boot and can draw over other active applications.',
          'impact': 'Highly intrusive; your phone will show pop-up ads unexpectedly.'
        }
      },
      'PRIVACY_MINER': {
        'id': {
          'title': 'Pengumpul Data Privasi',
          'risk': 'Aplikasi alat sederhana meminta akses ke SMS atau Log Panggilan.',
          'impact': 'Data pribadi Anda bisa dicuri dan dikirim ke server iklan.'
        },
        'en': {
          'title': 'Privacy Miner',
          'risk': 'Simple utility app requesting access to SMS or Call Logs.',
          'impact': 'Your personal data could be harvested and sent to ad servers.'
        }
      },
      'ACCESSIBILITY_ABUSE': {
        'id': {
          'title': 'Penyalahgunaan Aksesibilitas',
          'risk': 'Meminta izin untuk membaca seluruh layar dan melakukan klik otomatis.',
          'impact': 'Dapat membaca sandi, chat, dan mengontrol HP Anda sepenuhnya.'
        },
        'en': {
          'title': 'Accessibility Abuse',
          'risk': 'Requests permission to read your entire screen and perform auto-clicks.',
          'impact': 'Can read passwords, chats, and take full control of your device.'
        }
      },
      'AD_SDK_ADMOB': {
        'id': {
          'title': 'Integrasi Iklan (Google)',
          'risk': 'Mengandung kode untuk menampilkan iklan dari jaringan Google.',
          'impact': 'Penggunaan data internet bertambah untuk memuat materi iklan.'
        },
        'en': {
          'title': 'Ad Integration (Google)',
          'risk': 'Contains code to display ads from the Google network.',
          'impact': 'Increased data usage to download advertisement content.'
        }
      }
    };

    final entry = data[code] ?? {
      'id': {'title': 'Ancaman Tidak Dikenal', 'risk': 'Perilaku mencurigakan terdeteksi.', 'impact': 'Risiko keamanan umum.'},
      'en': {'title': 'Unknown Threat', 'risk': 'Suspicious behavior detected.', 'impact': 'General security risk.'}
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
        title: Text(l10n.securityDetails, style: GoogleFonts.poppins(fontWeight: FontWeight.bold)),
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
                        Text(appThreatInfo.appName, style: GoogleFonts.poppins(fontSize: 18, fontWeight: FontWeight.bold)),
                        Text(appThreatInfo.packageName, style: GoogleFonts.poppins(fontSize: 12, color: Colors.grey)),
                      ],
                    ),
                  ),
                ],
              ),
            ),
          ),
          const SizedBox(height: 24),
          Text(l10n.threatAnalysis, style: GoogleFonts.poppins(fontWeight: FontWeight.bold, fontSize: 16)),
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
                        Text(info['title']!, style: GoogleFonts.poppins(fontWeight: FontWeight.bold, color: color)),
                        const Spacer(),
                        Container(
                          padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
                          decoration: BoxDecoration(color: color, borderRadius: BorderRadius.circular(4)),
                          child: Text(threat.severity, style: const TextStyle(color: Colors.white, fontSize: 10, fontWeight: FontWeight.bold)),
                        ),
                      ],
                    ),
                    const Divider(height: 24),
                    Text(l10n.whatIsRisk, style: GoogleFonts.poppins(fontWeight: FontWeight.bold, fontSize: 13)),
                    Text(info['risk']!, style: GoogleFonts.poppins(fontSize: 13, color: Theme.of(context).colorScheme.onSurfaceVariant)),
                    const SizedBox(height: 12),
                    Text(l10n.impactOnYou, style: GoogleFonts.poppins(fontWeight: FontWeight.bold, fontSize: 13)),
                    Text(info['impact']!, style: GoogleFonts.poppins(fontSize: 13, color: Theme.of(context).colorScheme.onSurfaceVariant)),
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
