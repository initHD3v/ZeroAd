import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'dart:ui' as ui;
import 'package:zeroad/models.dart';
import 'package:zeroad/l10n.dart';
import 'package:zeroad/data/threat_database.dart';

class ThreatDetailPage extends StatelessWidget {
  final AppThreatInfo appThreatInfo;
  static const MethodChannel _platform = MethodChannel('zeroad.security/scanner');

  const ThreatDetailPage({super.key, required this.appThreatInfo});

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
            final info = ThreatDatabase.getRiskInfo(threat.code, deviceLang);
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
