import 'package:flutter/material.dart';
import 'package:flutter/services.dart'; // Import for MethodChannel
import 'package:google_fonts/google_fonts.dart';
import 'package:shared_preferences/shared_preferences.dart'; // Import Shared Preferences
import 'package:zeroad/models.dart';

class ThreatDetailPage extends StatelessWidget {
  final AppThreatInfo appThreatInfo;
  static const MethodChannel _platform = MethodChannel('zeroad.security/scanner');

  const ThreatDetailPage({super.key, required this.appThreatInfo});

  Color _getSeverityColor(String severity) {
    switch (severity.toUpperCase()) {
      case 'HIGH':
        return Colors.red.shade700;
      case 'MEDIUM':
        return Colors.orange.shade700;
      case 'LOW':
        return Colors.yellow.shade700;
      default:
        return Colors.grey.shade700;
    }
  }

  IconData _getThreatIcon(String type) {
    switch (type.toUpperCase()) {
      case 'PERMISSION_ABUSE':
        return Icons.security_update_warning_rounded;
      case 'AD_SDK':
        return Icons.ad_units_rounded;
      case 'BEHAVIORAL':
        return Icons.warning_rounded;
      default:
        return Icons.info_outline_rounded;
    }
  }

  Future<void> _handleUninstall(BuildContext context) async {
    if (appThreatInfo.isSystemApp) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('System apps cannot be uninstalled directly.')),
      );
      return;
    }

    try {
      await _platform.invokeMethod('uninstallApp', {'packageName': appThreatInfo.packageName});
      // Note: We can't easily know if the user actually completed the uninstall 
      // because the intent launches the system dialog.
    } on PlatformException catch (e) {
      if (context.mounted) {
         ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text("Failed to launch uninstall: '${e.message}'.")),
        );
      }
    }
  }

  Future<void> _handleWhitelist(BuildContext context) async {
    final prefs = await SharedPreferences.getInstance();
    final List<String> whitelist = prefs.getStringList('whitelisted_apps') ?? [];
    
    if (!whitelist.contains(appThreatInfo.packageName)) {
      whitelist.add(appThreatInfo.packageName);
      await prefs.setStringList('whitelisted_apps', whitelist);
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('${appThreatInfo.appName} added to whitelist.')),
        );
        Navigator.pop(context, true); // Return true to indicate change
      }
    } else {
       if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('App is already whitelisted.')),
        );
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text(
          '${appThreatInfo.appName} Details',
          style: GoogleFonts.poppins(fontWeight: FontWeight.bold),
        ),
        backgroundColor: Theme.of(context).colorScheme.surface,
      ),
      body: ListView(
        padding: const EdgeInsets.all(16.0),
        children: [
          Card(
            elevation: 4.0,
            shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12.0)),
            color: Theme.of(context).colorScheme.surface,
            child: Padding(
              padding: const EdgeInsets.all(24.0),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    appThreatInfo.appName,
                    style: GoogleFonts.poppins(
                      textStyle: Theme.of(context).textTheme.headlineSmall,
                      fontWeight: FontWeight.bold,
                      color: Theme.of(context).colorScheme.onSurface,
                    ),
                  ),
                  Text(
                    appThreatInfo.packageName,
                    style: GoogleFonts.poppins(
                      textStyle: Theme.of(context).textTheme.bodyMedium,
                      color: Theme.of(context).colorScheme.onSurfaceVariant,
                    ),
                  ),
                  const SizedBox(height: 16.0),
                  Text(
                    'System App: ${appThreatInfo.isSystemApp ? 'Yes' : 'No'}',
                    style: GoogleFonts.poppins(
                      textStyle: Theme.of(context).textTheme.bodyMedium,
                      color: Theme.of(context).colorScheme.onSurface,
                    ),
                  ),
                  const SizedBox(height: 24.0),
                  Text(
                    'Detected Threats:',
                    style: GoogleFonts.poppins(
                      textStyle: Theme.of(context).textTheme.titleLarge,
                      fontWeight: FontWeight.bold,
                      color: Theme.of(context).colorScheme.error,
                    ),
                  ),
                  const SizedBox(height: 16.0),
                  ...appThreatInfo.detectedThreats.map((threat) => Padding(
                        padding: const EdgeInsets.only(bottom: 12.0),
                        child: Row(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Icon(
                              _getThreatIcon(threat.type),
                              color: _getSeverityColor(threat.severity),
                              size: 24.0,
                            ),
                            const SizedBox(width: 12.0),
                            Expanded(
                              child: Column(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  Text(
                                    '${threat.type} (${threat.severity})',
                                    style: GoogleFonts.poppins(
                                      textStyle: Theme.of(context).textTheme.titleMedium,
                                      fontWeight: FontWeight.bold,
                                      color: _getSeverityColor(threat.severity),
                                    ),
                                  ),
                                  Text(
                                    threat.description,
                                    style: GoogleFonts.poppins(
                                      textStyle: Theme.of(context).textTheme.bodyMedium,
                                      color: Theme.of(context).colorScheme.onSurface,
                                    ),
                                  ),
                                  const SizedBox(height: 8.0),
                                  // Action Buttons
                                  Row(
                                    children: [
                                      ElevatedButton(
                                        onPressed: () => _handleUninstall(context),
                                        style: ElevatedButton.styleFrom(
                                          backgroundColor: Theme.of(context).colorScheme.primary,
                                          foregroundColor: Theme.of(context).colorScheme.onPrimary,
                                        ),
                                        child: Text(
                                          appThreatInfo.isSystemApp ? "Can't Uninstall" : 'Uninstall',
                                          style: GoogleFonts.poppins(),
                                        ),
                                      ),
                                      const SizedBox(width: 8.0),
                                      OutlinedButton(
                                        onPressed: () => _handleWhitelist(context),
                                        style: OutlinedButton.styleFrom(
                                          side: BorderSide(color: Theme.of(context).colorScheme.primary),
                                        ),
                                        child: Text(
                                          'Whitelist',
                                          style: GoogleFonts.poppins(color: Theme.of(context).colorScheme.primary),
                                        ),
                                      ),
                                    ],
                                  ),
                                ],
                              ),
                            ),
                          ],
                        ),
                      )),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }
}
