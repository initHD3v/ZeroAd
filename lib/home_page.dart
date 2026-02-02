import 'dart:convert'; // Import for JSON decoding
import 'dart:async'; // Import for Timer
import 'package:flutter/material.dart';
import 'package:flutter/services.dart'; // Import for MethodChannel
import 'package:google_fonts/google_fonts.dart'; // Import for Google Fonts
import 'package:shared_preferences/shared_preferences.dart'; // Import Shared Preferences
import 'package:zeroad/models.dart'; // Import for data models
import 'package:zeroad/threat_detail_page.dart'; // Import the new ThreatDetailPage
import 'package:zeroad/l10n.dart'; // Import localization

class MyHomePage extends StatefulWidget {
  const MyHomePage({super.key, required this.title});

  final String title;

  @override
  State<MyHomePage> createState() => _MyHomePageState();
}

class _MyHomePageState extends State<MyHomePage> {
  static const MethodChannel _platform = MethodChannel('zeroad.security/scanner');
  
  // -- Localization --
  late AppLocalizations l10n;

  // -- Navigation State --
  int _currentIndex = 1; // Default to Shield (middle tab)

  // -- Scanner State --
  final ValueNotifier<bool> _isScanning = ValueNotifier<bool>(false);
  String _scanResultText = ''; 
  ScanResultModel? _lastScanResult;

  // -- AdBlock State --
  bool _isAdBlockActive = false;
  List<String> _vpnLogs = []; // List to store activity logs
  Timer? _logTimer;

  @override
  void initState() {
    super.initState();
    _loadAdBlockStatus();
    _scanForAdware(); // Initial scan on startup
    _startLogSync(); // Start syncing logs
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    l10n = AppLocalizations.of();
    if (_scanResultText.isEmpty) {
      _scanResultText = l10n.scanIdle;
    }
  }

  @override
  void dispose() {
    _logTimer?.cancel();
    super.dispose();
  }

  void _startLogSync() {
    _logTimer = Timer.periodic(const Duration(seconds: 3), (timer) {
      if (_isAdBlockActive) {
        _fetchLogs();
      }
    });
  }

  Future<void> _fetchLogs() async {
    try {
      final List<dynamic> newLogs = await _platform.invokeMethod('getVpnLogs');
      if (newLogs.isNotEmpty && mounted) {
        setState(() {
          _vpnLogs = [...newLogs.map((e) => e.toString()), ..._vpnLogs];
          if (_vpnLogs.length > 100) { 
            _vpnLogs = _vpnLogs.sublist(0, 100);
          }
        });
      }
    } catch (e) {
      debugPrint("Error fetching logs: $e");
    }
  }

  void _clearLogs() {
    setState(() {
      _vpnLogs.clear();
    });
  }

  Future<void> _loadAdBlockStatus() async {
    final prefs = await SharedPreferences.getInstance();
    setState(() {
      _isAdBlockActive = prefs.getBool('adblock_active') ?? false;
    });
  }

  Future<void> _toggleAdBlock() async {
    try {
      bool result;
      if (!_isAdBlockActive) {
        result = await _platform.invokeMethod('startAdBlock');
      } else {
        result = await _platform.invokeMethod('stopAdBlock');
      }
      
      setState(() {
        _isAdBlockActive = result;
      });
      
      final prefs = await SharedPreferences.getInstance();
      await prefs.setBool('adblock_active', _isAdBlockActive);

    } on PlatformException catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text("AdBlock Error: '${e.message}'")),
        );
      }
    }
  }

  Future<void> _scanForAdware() async {
    _isScanning.value = true;
    setState(() {}); 

    try {
      final String response = await _platform.invokeMethod('scan');
      final Map<String, dynamic> jsonResponse = jsonDecode(response);
      
      final prefs = await SharedPreferences.getInstance();
      final List<String> whitelist = prefs.getStringList('whitelisted_apps') ?? [];

      ScanResultModel fullResult = ScanResultModel.fromJson(jsonResponse);
      List<AppThreatInfo> filteredThreats = fullResult.threats
          .where((threat) => !whitelist.contains(threat.packageName))
          .toList();

      _lastScanResult = ScanResultModel(
        totalInstalledPackages: fullResult.totalInstalledPackages,
        suspiciousPackagesCount: filteredThreats.length,
        threats: filteredThreats,
      );

      _scanResultText = _lastScanResult!.threats.isNotEmpty
          ? l10n.threatsDetected
          : l10n.systemClean;

    } on PlatformException catch (e) {
      _scanResultText = "Error: '${e.message}'.";
      _lastScanResult = null;
    } catch (e) {
      _scanResultText = "Error: '${e.toString()}'.";
      _lastScanResult = null;
    } finally {
      _isScanning.value = false;
      setState(() {});
    }
  }

  Color _getSeverityColor(String severity) {
    switch (severity.toUpperCase()) {
      case 'HIGH': return Colors.red.shade700;
      case 'MEDIUM': return Colors.orange.shade700;
      default: return Colors.yellow.shade700;
    }
  }

  IconData _getThreatIcon(String type) {
    switch (type.toUpperCase()) {
      case 'PERMISSION_ABUSE': return Icons.security_update_warning_rounded;
      case 'AD_SDK': return Icons.ad_units_rounded;
      case 'BEHAVIORAL': return Icons.warning_rounded;
      default: return Icons.info_outline_rounded;
    }
  }

  Widget _buildScannerView() {
    return ValueListenableBuilder<bool>(
      valueListenable: _isScanning,
      builder: (context, isScanning, child) {
        List<AppThreatInfo> highRisk = [];
        List<AppThreatInfo> mediumRisk = [];
        List<AppThreatInfo> lowRisk = [];

        if (_lastScanResult != null) {
          for (var app in _lastScanResult!.threats) {
            if (app.detectedThreats.any((t) => t.severity == 'HIGH')) {
              highRisk.add(app);
            } else if (app.detectedThreats.any((t) => t.severity == 'MEDIUM')) {
              mediumRisk.add(app);
            } else {
              lowRisk.add(app);
            }
          }
        }

        return ListView(
          padding: const EdgeInsets.all(16.0),
          children: <Widget>[
            Card(
              elevation: 4.0,
              shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16.0)),
              color: Theme.of(context).colorScheme.surfaceContainerHighest,
              child: Padding(
                padding: const EdgeInsets.all(24.0),
                child: Column(
                  children: [
                     Icon(
                      isScanning ? Icons.radar : (_lastScanResult?.suspiciousPackagesCount ?? 0) > 0 ? Icons.warning_amber_rounded : Icons.check_circle_outline,
                      size: 64,
                      color: isScanning 
                          ? Theme.of(context).colorScheme.primary 
                          : (_lastScanResult?.suspiciousPackagesCount ?? 0) > 0 ? Theme.of(context).colorScheme.error : Colors.green,
                    ),
                    const SizedBox(height: 16),
                    Text(
                      isScanning ? l10n.scanningStatus : _scanResultText,
                      textAlign: TextAlign.center,
                      style: GoogleFonts.poppins(
                        textStyle: Theme.of(context).textTheme.titleLarge,
                        fontWeight: FontWeight.bold,
                      ),
                    ),
                  ],
                ),
              ),
            ),
            const SizedBox(height: 20),
            Row(
              children: <Widget>[
                Expanded(
                  child: _buildStatCard(l10n.totalApps, '${_lastScanResult?.totalInstalledPackages ?? 0}', Theme.of(context).colorScheme.primary),
                ),
                const SizedBox(width: 16.0),
                Expanded(
                  child: _buildStatCard(l10n.threatsCount, '${_lastScanResult?.suspiciousPackagesCount ?? 0}', (_lastScanResult?.suspiciousPackagesCount ?? 0) > 0 ? Theme.of(context).colorScheme.error : Theme.of(context).colorScheme.primary),
                ),
              ],
            ),
            const SizedBox(height: 24.0),
            if (_lastScanResult != null && _lastScanResult!.threats.isNotEmpty) ...[
              Text(l10n.analysisReport, style: GoogleFonts.poppins(textStyle: Theme.of(context).textTheme.titleMedium, fontWeight: FontWeight.bold, color: Theme.of(context).colorScheme.onSurface)),
              const SizedBox(height: 12.0),
              if (highRisk.isNotEmpty) _buildSeveritySection(l10n.criticalRisk, highRisk, Colors.red.shade700, Icons.gpp_bad_rounded, true),
              if (mediumRisk.isNotEmpty) _buildSeveritySection(l10n.warnings, mediumRisk, Colors.orange.shade800, Icons.warning_rounded, false),
              if (lowRisk.isNotEmpty) _buildSeveritySection(l10n.lowPriority, lowRisk, Colors.yellow.shade800, Icons.info_rounded, false),
              const SizedBox(height: 80.0),
            ] else if (!isScanning && _lastScanResult != null) ...[
              Center(
                child: Padding(
                  padding: const EdgeInsets.only(top: 40),
                  child: Text(l10n.noAdwareFound, textAlign: TextAlign.center, style: GoogleFonts.poppins(color: Colors.grey)),
                ),
              )
            ],
          ],
        );
      },
    );
  }

  Widget _buildSeveritySection(String title, List<AppThreatInfo> apps, Color color, IconData icon, bool initExpanded) {
    return Card(
      elevation: 2,
      margin: const EdgeInsets.only(bottom: 16),
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
      clipBehavior: Clip.antiAlias,
      child: ExpansionTile(
        initiallyExpanded: initExpanded,
        collapsedBackgroundColor: color.withAlpha(20),
        backgroundColor: Theme.of(context).colorScheme.surface,
        leading: Icon(icon, color: color),
        title: Text(title, style: GoogleFonts.poppins(fontWeight: FontWeight.bold, color: color)),
        trailing: Container(
          padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
          decoration: BoxDecoration(color: color.withAlpha(50), borderRadius: BorderRadius.circular(12)),
          child: Text('${apps.length}', style: GoogleFonts.poppins(fontWeight: FontWeight.bold, color: color)),
        ),
        children: apps.map((app) => _buildThreatItem(app, context)).toList(),
      ),
    );
  }

  Widget _buildStatCard(String title, String value, Color color) {
    return Card(
      elevation: 2.0,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12.0)),
      child: Padding(
        padding: const EdgeInsets.all(16.0),
        child: Column(
          children: [
            Text(title, style: GoogleFonts.poppins(color: Theme.of(context).colorScheme.onSurfaceVariant)),
            const SizedBox(height: 4),
            Text(value, style: GoogleFonts.poppins(fontSize: 24, fontWeight: FontWeight.bold, color: color)),
          ],
        ),
      ),
    );
  }

  Widget _buildThreatItem(AppThreatInfo appThreatInfo, BuildContext context) {
    String highestSeverity = 'LOW';
    if (appThreatInfo.detectedThreats.any((t) => t.severity == 'HIGH')) highestSeverity = 'HIGH';
    else if (appThreatInfo.detectedThreats.any((t) => t.severity == 'MEDIUM')) highestSeverity = 'MEDIUM';

    return Card(
      margin: const EdgeInsets.only(bottom: 12.0, left: 8, right: 8),
      elevation: 0,
      color: Theme.of(context).colorScheme.surface,
      child: ListTile(
        onTap: () async {
          final result = await Navigator.push(
            context,
            MaterialPageRoute(builder: (context) => ThreatDetailPage(appThreatInfo: appThreatInfo)),
          );
          if (result == true) _scanForAdware();
        },
        leading: CircleAvatar(
          backgroundColor: _getSeverityColor(highestSeverity).withAlpha(50),
          child: Icon(_getThreatIcon(appThreatInfo.detectedThreats.first.type), color: _getSeverityColor(highestSeverity)),
        ),
        title: Text(appThreatInfo.appName, style: GoogleFonts.poppins(fontWeight: FontWeight.bold)),
        subtitle: Text(appThreatInfo.packageName, style: GoogleFonts.poppins(fontSize: 12)),
        trailing: Icon(Icons.chevron_right, color: Theme.of(context).colorScheme.onSurfaceVariant),
      ),
    );
  }

  Widget _buildShieldView() {
    final colorScheme = Theme.of(context).colorScheme;
    final activeColor = Colors.greenAccent.shade700;
    final inactiveColor = Colors.grey.shade800;

    return Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          AnimatedContainer(
            duration: const Duration(milliseconds: 500),
            height: 240,
            width: 240,
            decoration: BoxDecoration(
              shape: BoxShape.circle,
              color: _isAdBlockActive ? activeColor.withAlpha(30) : inactiveColor.withAlpha(30),
              border: Border.all(color: _isAdBlockActive ? activeColor : inactiveColor, width: 4),
              boxShadow: _isAdBlockActive ? [BoxShadow(color: activeColor.withAlpha(100), blurRadius: 40, spreadRadius: 10)] : [],
            ),
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                Icon(_isAdBlockActive ? Icons.verified_user_rounded : Icons.shield_rounded, size: 80, color: _isAdBlockActive ? activeColor : inactiveColor),
                const SizedBox(height: 10),
                Text(_isAdBlockActive ? l10n.activeLabel : l10n.offLabel, style: GoogleFonts.poppins(fontSize: 18, fontWeight: FontWeight.w900, letterSpacing: 2, color: _isAdBlockActive ? activeColor : inactiveColor)),
              ],
            ),
          ),
          const SizedBox(height: 60),
          Text(_isAdBlockActive ? l10n.protectionActive : l10n.protectionDisabled, textAlign: TextAlign.center, style: GoogleFonts.poppins(fontSize: 20, fontWeight: FontWeight.bold, color: colorScheme.onSurface)),
          const SizedBox(height: 12),
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 40),
            child: Text(_isAdBlockActive ? l10n.adGuardActive : l10n.enableToBlock, textAlign: TextAlign.center, style: GoogleFonts.poppins(color: colorScheme.onSurfaceVariant, fontSize: 14)),
          ),
          const SizedBox(height: 60),
          SizedBox(
            width: 220,
            height: 64,
            child: ElevatedButton.icon(
              onPressed: _toggleAdBlock,
              icon: Icon(_isAdBlockActive ? Icons.power_settings_new : Icons.play_arrow_rounded),
              label: Text(_isAdBlockActive ? l10n.disconnect : l10n.connect),
              style: ElevatedButton.styleFrom(
                backgroundColor: _isAdBlockActive ? colorScheme.surfaceContainerHighest : activeColor,
                foregroundColor: _isAdBlockActive ? colorScheme.onSurface : Colors.black,
                textStyle: GoogleFonts.poppins(fontSize: 16, fontWeight: FontWeight.w900, letterSpacing: 1),
                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(32)),
                elevation: _isAdBlockActive ? 0 : 8,
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildActivityView() {
    final colorScheme = Theme.of(context).colorScheme;
    return Column(
      children: [
        Container(
          padding: const EdgeInsets.all(20),
          color: colorScheme.surfaceContainer,
          child: Row(
            children: [
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(l10n.blockedRequests, style: GoogleFonts.poppins(fontWeight: FontWeight.bold, fontSize: 18)),
                    Text(l10n.realTimeLog, style: GoogleFonts.poppins(fontSize: 12, color: colorScheme.onSurfaceVariant)),
                  ],
                ),
              ),
              if (_vpnLogs.isNotEmpty)
                TextButton.icon(
                  onPressed: _clearLogs,
                  icon: const Icon(Icons.delete_sweep_outlined, size: 20),
                  label: Text(l10n.clearBtn, style: GoogleFonts.poppins()),
                  style: TextButton.styleFrom(foregroundColor: colorScheme.error),
                ),
            ],
          ),
        ),
        Expanded(
          child: _vpnLogs.isEmpty 
            ? Center(
                child: Column(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    Icon(Icons.history_toggle_off_rounded, size: 64, color: colorScheme.onSurfaceVariant.withAlpha(50)),
                    const SizedBox(height: 16),
                    Text(_isAdBlockActive ? l10n.noTraffic : l10n.shieldInactive, style: GoogleFonts.poppins(color: colorScheme.onSurfaceVariant)),
                  ],
                ),
              )
            : ListView.separated(
                padding: const EdgeInsets.symmetric(vertical: 8),
                itemCount: _vpnLogs.length,
                separatorBuilder: (context, index) => const Divider(height: 1, indent: 70),
                itemBuilder: (context, index) {
                  final parts = _vpnLogs[index].split('|');
                  final time = DateTime.fromMillisecondsSinceEpoch(int.tryParse(parts[0]) ?? 0);
                  final domain = parts.length > 1 ? parts[1] : "Unknown Domain";
                  return ListTile(
                    leading: CircleAvatar(backgroundColor: colorScheme.errorContainer, child: Icon(Icons.block_flipped, color: colorScheme.error, size: 20)),
                    title: Text(domain, overflow: TextOverflow.ellipsis, style: GoogleFonts.poppins(fontSize: 14, fontWeight: FontWeight.w600)),
                    subtitle: Text("${time.hour.toString().padLeft(2, '0')}:${time.minute.toString().padLeft(2, '0')}:${time.second.toString().padLeft(2, '0')} • DNS Block", style: GoogleFonts.poppins(fontSize: 11)),
                    trailing: Text(l10n.blockedLabel, style: GoogleFonts.poppins(fontSize: 10, fontWeight: FontWeight.w900, color: colorScheme.error)),
                  );
                },
              ),
        ),
      ],
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text(_currentIndex == 0 ? l10n.scanTitle : (_currentIndex == 1 ? l10n.shieldTitle : l10n.liveActivity), style: GoogleFonts.poppins(fontWeight: FontWeight.bold)),
        centerTitle: true,
        actions: [
          if (_currentIndex == 0)
            IconButton(icon: const Icon(Icons.refresh), onPressed: _isScanning.value ? null : _scanForAdware, tooltip: l10n.scanBtn),
        ],
      ),
      body: IndexedStack(
        index: _currentIndex,
        children: [_buildScannerView(), _buildShieldView(), _buildActivityView()],
      ),
      bottomNavigationBar: NavigationBar(
        selectedIndex: _currentIndex,
        onDestinationSelected: (index) => setState(() => _currentIndex = index),
        destinations: [
          NavigationDestination(icon: const Icon(Icons.radar_outlined), selectedIcon: const Icon(Icons.radar), label: l10n.scannerTab),
          NavigationDestination(icon: const Icon(Icons.shield_outlined), selectedIcon: const Icon(Icons.shield), label: l10n.shieldTab),
          NavigationDestination(icon: const Icon(Icons.list_alt_rounded), selectedIcon: const Icon(Icons.list_alt_rounded), label: l10n.activityTab),
        ],
      ),
    );
  }
}
