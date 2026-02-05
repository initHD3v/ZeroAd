import 'dart:convert'; // Import for JSON decoding
import 'dart:async'; // Import for Timer
import 'package:flutter/material.dart';
import 'package:flutter/services.dart'; // Import for MethodChannel
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

class _MyHomePageState extends State<MyHomePage> with SingleTickerProviderStateMixin {
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
  String _logFilter = 'ALL'; // ALL, ADS

  // -- Animation State --
  late AnimationController _breathingController;
  late Animation<double> _glowAnimation;

  @override
  void initState() {
    super.initState();
    _loadAdBlockStatus();
    _startLogSync(); // Start syncing logs

    // Delay scan to prevent blocking UI during startup
    Future.delayed(const Duration(milliseconds: 800), () {
      if (mounted) _scanForAdware();
    });

    _breathingController = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 2000),
    );

    _glowAnimation = Tween<double>(begin: 5.0, end: 35.0).animate(
      CurvedAnimation(parent: _breathingController, curve: Curves.easeInOut),
    );
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
    _breathingController.dispose();
    super.dispose();
  }

  void _startLogSync() {
    _logTimer?.cancel();
    _logTimer = Timer.periodic(const Duration(seconds: 1), (timer) {
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
      if (_isAdBlockActive) {
        _breathingController.repeat(reverse: true);
      }
    });
  }

  void _updateAnimationState() {
    if (_currentIndex == 1 && _isAdBlockActive) {
      if (!_breathingController.isAnimating) {
        _breathingController.repeat(reverse: true);
      }
    } else {
      if (_breathingController.isAnimating) {
        _breathingController.stop();
      }
    }
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
        _updateAnimationState();
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
                      style: Theme.of(context).textTheme.titleLarge?.copyWith(
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
              Text(l10n.analysisReport, style: TextStyle(fontWeight: FontWeight.bold, color: Theme.of(context).colorScheme.onSurface)),
              const SizedBox(height: 12.0),
              if (highRisk.isNotEmpty) _buildSeveritySection(l10n.criticalRisk, highRisk, Colors.red.shade700, Icons.gpp_bad_rounded, true),
              if (mediumRisk.isNotEmpty) _buildSeveritySection(l10n.warnings, mediumRisk, Colors.orange.shade800, Icons.warning_rounded, false),
              if (lowRisk.isNotEmpty) _buildSeveritySection(l10n.lowPriority, lowRisk, Colors.yellow.shade800, Icons.info_rounded, false),
              const SizedBox(height: 80.0),
            ] else if (!isScanning && _lastScanResult != null) ...[
              Center(
                child: Padding(
                  padding: const EdgeInsets.only(top: 40),
                  child: Text(l10n.noAdwareFound, textAlign: TextAlign.center, style: const TextStyle(color: Colors.grey)),
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
        title: Text(title, style: TextStyle(fontWeight: FontWeight.bold, color: color)),
        trailing: Container(
          padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
          decoration: BoxDecoration(color: color.withAlpha(50), borderRadius: BorderRadius.circular(12)),
          child: Text('${apps.length}', style: TextStyle(fontWeight: FontWeight.bold, color: color)),
        ),
        children: [
          ConstrainedBox(
            constraints: BoxConstraints(maxHeight: apps.length > 5 ? 400 : apps.length * 80.0),
            child: ListView.builder(
              shrinkWrap: true,
              physics: apps.length > 5 ? const BouncingScrollPhysics() : const NeverScrollableScrollPhysics(),
              itemCount: apps.length,
              itemBuilder: (context, index) => _buildThreatItem(apps[index], context),
            ),
          ),
        ],
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
            Text(title, style: TextStyle(color: Theme.of(context).colorScheme.onSurfaceVariant)),
            const SizedBox(height: 4),
            Text(value, style: TextStyle(fontSize: 24, fontWeight: FontWeight.bold, color: color)),
          ],
        ),
      ),
    );
  }

  Widget _buildThreatItem(AppThreatInfo appThreatInfo, BuildContext context) {
    String highestSeverity = 'LOW';
    if (appThreatInfo.detectedThreats.any((t) => t.severity == 'HIGH')) {
      highestSeverity = 'HIGH';
    } else if (appThreatInfo.detectedThreats.any((t) => t.severity == 'MEDIUM')) {
      highestSeverity = 'MEDIUM';
    }

    return Card(
      margin: const EdgeInsets.only(bottom: 8.0, left: 8, right: 8, top: 4),
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
        title: Text(appThreatInfo.appName, style: const TextStyle(fontWeight: FontWeight.bold)),
        subtitle: Text(appThreatInfo.packageName, style: const TextStyle(fontSize: 12)),
        trailing: Icon(Icons.chevron_right, color: Theme.of(context).colorScheme.onSurfaceVariant),
      ),
    );
  }

  Widget _buildShieldView() {
    final colorScheme = Theme.of(context).colorScheme;
    final activeColor = Colors.greenAccent.shade700;
    final inactiveColor = colorScheme.outline;

    final shieldContent = Column(
      mainAxisAlignment: MainAxisAlignment.center,
      children: [
        Icon(
          _isAdBlockActive ? Icons.verified_user_rounded : Icons.shield_rounded, 
          size: 80, 
          color: _isAdBlockActive ? activeColor : inactiveColor
        ),
        const SizedBox(height: 10),
                            Text(
                              _isAdBlockActive ? l10n.activeLabel : l10n.offLabel, 
                              style: TextStyle(
                                fontSize: 18, 
                                fontWeight: FontWeight.w900, 
                                letterSpacing: 2, 
                                color: _isAdBlockActive ? activeColor : inactiveColor
                              )
                            ),      ],
    );

    return Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          AnimatedBuilder(
            animation: _glowAnimation,
            child: shieldContent,
            builder: (context, staticChild) {
              return Container(
                height: 240,
                width: 240,
                decoration: BoxDecoration(
                  shape: BoxShape.circle,
                  color: _isAdBlockActive ? activeColor.withAlpha(30) : inactiveColor.withAlpha(20),
                  border: Border.all(color: _isAdBlockActive ? activeColor : inactiveColor, width: 4),
                  boxShadow: _isAdBlockActive 
                    ? [
                        BoxShadow(
                          color: activeColor.withAlpha(80), 
                          blurRadius: _glowAnimation.value, 
                          spreadRadius: _glowAnimation.value / 4
                        ),
                        BoxShadow(
                          color: activeColor.withAlpha(40), 
                          blurRadius: _glowAnimation.value * 2, 
                          spreadRadius: _glowAnimation.value / 2
                        )
                      ] 
                    : [],
                ),
                child: staticChild,
              );
            },
          ),
          const SizedBox(height: 60),
          Text(_isAdBlockActive ? l10n.protectionActive : l10n.protectionDisabled, textAlign: TextAlign.center, style: const TextStyle(fontSize: 20, fontWeight: FontWeight.bold)),
          const SizedBox(height: 12),
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 40),
            child: Text(_isAdBlockActive ? l10n.adGuardActive : l10n.enableToBlock, textAlign: TextAlign.center, style: TextStyle(color: colorScheme.onSurfaceVariant, fontSize: 14)),
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
                foregroundColor: _isAdBlockActive ? colorScheme.onSurface : Colors.white,
                textStyle: const TextStyle(fontSize: 16, fontWeight: FontWeight.w900, letterSpacing: 1),
                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(32)),
                elevation: _isAdBlockActive ? 0 : 8,
              ),
            ),
          ),
        ],
      ),
    );
  }

  Future<void> _addToWhitelist(String packageName) async {
    try {
      await _platform.invokeMethod('addToWhitelist', {'packageName': packageName});
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text("App '$packageName' whitelisted successfully.")),
        );
        Navigator.pop(context); // Close detail sheet
      }
    } on PlatformException catch (e) {
      debugPrint("Error whitelisting: $e");
    }
  }

  void _showLogDetail(String rawLog) {
    final parts = rawLog.split('|');
    if (parts.length < 4) return;
    
    final time = DateTime.fromMillisecondsSinceEpoch(int.tryParse(parts[0]) ?? 0);
    final domain = parts[1];
    final category = parts[2];
    final status = parts[3]; // BLOCKED, ALLOWED, WHITELISTED
    final packageName = parts.length > 4 ? parts[4] : 'Unknown';
    final appName = parts.length > 5 ? parts[5] : 'Unknown App';

    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (context) => DraggableScrollableSheet(
        initialChildSize: 0.5,
        minChildSize: 0.3,
        maxChildSize: 0.95,
        builder: (context, scrollController) => Container(
          decoration: BoxDecoration(
            color: Theme.of(context).colorScheme.surface,
            borderRadius: const BorderRadius.vertical(top: Radius.circular(20)),
          ),
          padding: const EdgeInsets.all(24.0),
          child: ListView(
            controller: scrollController,
            children: [
              Center(
                child: Container(
                  width: 40,
                  height: 4,
                  margin: const EdgeInsets.only(bottom: 20),
                  decoration: BoxDecoration(
                    color: Colors.grey.withAlpha(100),
                    borderRadius: BorderRadius.circular(2),
                  ),
                ),
              ),
              Row(
                children: [
                  Icon(Icons.security, color: Theme.of(context).colorScheme.primary),
                  const SizedBox(width: 12),
                  const Text("Traffic Detail", style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold)),
                ],
              ),
              const Divider(height: 32),
              _buildDetailRow("Source App", appName),
              _buildDetailRow("Package", packageName),
              _buildDetailRow("Domain", domain),
              _buildDetailRow("Category", _getFriendlyCategory(category)),
              _buildDetailRow("Status", status),
              _buildDetailRow("Time", time.toString()),
              const SizedBox(height: 24),
              Row(
                children: [
                  Expanded(
                    child: OutlinedButton.icon(
                      onPressed: () {
                        Clipboard.setData(ClipboardData(text: rawLog));
                        ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text("Log Copied!")));
                      },
                      icon: const Icon(Icons.copy),
                      label: const Text("Copy"),
                    ),
                  ),
                  const SizedBox(width: 12),
                  if (status == 'BLOCKED')
                    Expanded(
                      child: ElevatedButton.icon(
                        onPressed: () => _addToWhitelist(packageName),
                        icon: const Icon(Icons.check_circle_outline),
                        label: const Text("Allow App"),
                        style: ElevatedButton.styleFrom(
                          backgroundColor: Colors.green,
                          foregroundColor: Colors.white,
                        ),
                      ),
                    ),
                ],
              ),
              const SizedBox(height: 20),
            ],
          ),
        ),
      ),
    );
  }

  String _getFriendlyCategory(String cat) {
    switch (cat) {
      case 'AD_CONTENT': return "Ad / Tracker";
      case 'TRACKER': return "Tracker";
      case 'WHITELISTED': return "Whitelisted App";
      case 'DNS_QUERY': return "Standard DNS";
      default: return cat;
    }
  }

  Widget _buildDetailRow(String label, String value) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 12),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(label, style: const TextStyle(fontSize: 12, color: Colors.grey, fontWeight: FontWeight.bold)),
          Text(value, style: const TextStyle(fontSize: 14, fontWeight: FontWeight.w500)),
        ],
      ),
    );
  }

  void _showAppDetails(String appName, String packageName, List<String> appLogs) {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (context) => DraggableScrollableSheet(
        initialChildSize: 0.7,
        minChildSize: 0.5,
        maxChildSize: 0.95,
        builder: (context, scrollController) => Container(
          decoration: BoxDecoration(
            color: Theme.of(context).colorScheme.surface,
            borderRadius: const BorderRadius.vertical(top: Radius.circular(20)),
          ),
          padding: const EdgeInsets.symmetric(horizontal: 0, vertical: 20),
          child: Column(
            children: [
              // Handle
              Center(
                child: Container(
                  width: 40,
                  height: 4,
                  margin: const EdgeInsets.only(bottom: 20),
                  decoration: BoxDecoration(
                    color: Colors.grey.withAlpha(100),
                    borderRadius: BorderRadius.circular(2),
                  ),
                ),
              ),
              // Header
              Padding(
                padding: const EdgeInsets.symmetric(horizontal: 24.0),
                child: Row(
                  children: [
                    CircleAvatar(
                      radius: 24,
                      backgroundColor: Theme.of(context).colorScheme.primaryContainer,
                      child: Text(
                        appName.isNotEmpty ? appName[0].toUpperCase() : "?",
                        style: TextStyle(fontSize: 20, fontWeight: FontWeight.bold, color: Theme.of(context).colorScheme.primary),
                      ),
                    ),
                    const SizedBox(width: 16),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(appName, style: const TextStyle(fontSize: 18, fontWeight: FontWeight.bold)),
                          Text(packageName, style: TextStyle(fontSize: 12, color: Theme.of(context).colorScheme.onSurfaceVariant)),
                        ],
                      ),
                    ),
                    IconButton(
                      icon: const Icon(Icons.check_circle_outline, color: Colors.green),
                      onPressed: () => _addToWhitelist(packageName),
                      tooltip: "Allow this App",
                    )
                  ],
                ),
              ),
              const Divider(height: 32),
              // List of Domains
              Expanded(
                child: ListView.separated(
                  controller: scrollController,
                  padding: const EdgeInsets.symmetric(horizontal: 16),
                  itemCount: appLogs.length,
                  separatorBuilder: (context, index) => const Divider(height: 1, indent: 64),
                  itemBuilder: (context, index) {
                    final rawLog = appLogs[index];
                    final parts = rawLog.split('|');
                    if (parts.length < 3) return const SizedBox();
                    
                    final time = DateTime.fromMillisecondsSinceEpoch(int.tryParse(parts[0]) ?? 0);
                    final domain = parts[1];
                    final category = parts[2];
                    final isBlocked = category == 'AD_CONTENT' || parts[3] == 'BLOCKED';

                    return ListTile(
                      dense: true,
                      onTap: () => _showLogDetail(rawLog),
                      leading: Icon(
                        isBlocked ? Icons.block : Icons.language, 
                        color: isBlocked ? Theme.of(context).colorScheme.error : Colors.grey,
                        size: 20
                      ),
                      title: Text(domain, style: const TextStyle(fontWeight: FontWeight.w500)),
                      subtitle: Text("${time.hour.toString().padLeft(2,'0')}:${time.minute.toString().padLeft(2,'0')}:${time.second.toString().padLeft(2,'0')} • $category"),
                      trailing: const Icon(Icons.chevron_right, size: 16),
                    );
                  },
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildActivityView() {
    final colorScheme = Theme.of(context).colorScheme;
    
    // 1. Filter Logs
    final filteredLogs = _vpnLogs.where((log) {
      if (_logFilter == 'ALL') return true;
      final parts = log.split('|');
      if (parts.length < 3) return false;
      final category = parts[2];
      return category == 'AD_CONTENT' || category == 'TRACKER';
    }).toList();

    // 2. Group by PackageName
    final Map<String, List<String>> groupedLogs = {};
    final Map<String, String> appNames = {}; // Cache app names

    for (var log in filteredLogs) {
      final parts = log.split('|');
      if (parts.length < 6) continue;
      
      final packageName = parts[4];
      final appName = parts[5];
      
      if (!groupedLogs.containsKey(packageName)) {
        groupedLogs[packageName] = [];
        appNames[packageName] = appName;
      }
      groupedLogs[packageName]!.add(log);
    }

    final sortedKeys = groupedLogs.keys.toList();

    return Column(
      children: [
        Container(
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
          color: colorScheme.surfaceContainer,
          child: Column(
            children: [
              Row(
                children: [
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(l10n.blockedRequests, style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 18)),
                        const Text("True Core Packet Filter v6.0", style: TextStyle(fontSize: 12, color: Colors.grey)),
                      ],
                    ),
                  ),
                  if (_vpnLogs.isNotEmpty)
                    IconButton(
                      onPressed: _clearLogs,
                      icon: const Icon(Icons.delete_sweep_outlined),
                      color: colorScheme.error,
                      tooltip: l10n.clearBtn,
                    ),
                ],
              ),
              const SizedBox(height: 12),
              Row(
                children: [
                  _buildFilterChip("ALL APPS", 'ALL'),
                  const SizedBox(width: 8),
                  _buildFilterChip("BLOCKED ONLY", 'ADS'),
                ],
              ),
            ],
          ),
        ),
        Expanded(
          child: sortedKeys.isEmpty 
            ? Center(
                child: Column(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    Icon(Icons.history_toggle_off_rounded, size: 64, color: colorScheme.onSurfaceVariant.withAlpha(50)),
                    const SizedBox(height: 16),
                    Text(_isAdBlockActive ? l10n.noTraffic : l10n.shieldInactive, style: TextStyle(color: colorScheme.onSurfaceVariant)),
                  ],
                ),
              )
            : ListView.builder(
                padding: const EdgeInsets.symmetric(vertical: 8),
                itemCount: sortedKeys.length,
                itemBuilder: (context, index) {
                  final packageName = sortedKeys[index];
                  final logs = groupedLogs[packageName]!;
                  final appName = appNames[packageName] ?? "Unknown App";
                  final blockedCount = logs.where((l) => l.contains('|BLOCKED|') || l.contains('AD_CONTENT')).length;

                  return Card(
                    margin: const EdgeInsets.symmetric(horizontal: 12, vertical: 4),
                    elevation: 0,
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(12),
                      side: BorderSide(color: colorScheme.outlineVariant.withAlpha(50)),
                    ),
                    child: ListTile(
                      onTap: () => _showAppDetails(appName, packageName, logs),
                      contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 4),
                      leading: CircleAvatar(
                        backgroundColor: colorScheme.primaryContainer,
                        child: Text(
                          appName.isNotEmpty ? appName[0].toUpperCase() : "?",
                          style: TextStyle(fontWeight: FontWeight.bold, color: colorScheme.primary),
                        ),
                      ),
                      title: Text(appName, style: const TextStyle(fontWeight: FontWeight.bold)),
                      subtitle: Text("$packageName • ${logs.length} requests", style: const TextStyle(fontSize: 12)),
                      trailing: Row(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          if (blockedCount > 0)
                            Container(
                              padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
                              decoration: BoxDecoration(
                                color: colorScheme.error,
                                borderRadius: BorderRadius.circular(12),
                              ),
                              child: Text(
                                "$blockedCount Ad", 
                                style: const TextStyle(color: Colors.white, fontSize: 10, fontWeight: FontWeight.bold)
                              ),
                            ),
                          const SizedBox(width: 8),
                          const Icon(Icons.chevron_right),
                        ],
                      ),
                    ),
                  );
                },
              ),
        ),
      ],
    );
  }

  Widget _buildFilterChip(String label, String value) {
    final isSelected = _logFilter == value;
    final colorScheme = Theme.of(context).colorScheme;
    return GestureDetector(
      onTap: () => setState(() => _logFilter = value),
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
        decoration: BoxDecoration(
          color: isSelected ? colorScheme.primary : colorScheme.surfaceContainerHighest,
          borderRadius: BorderRadius.circular(20),
        ),
        child: Text(label, style: TextStyle(color: isSelected ? colorScheme.onPrimary : colorScheme.onSurfaceVariant, fontSize: 12, fontWeight: FontWeight.bold)),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text(_currentIndex == 0 ? l10n.scanTitle : (_currentIndex == 1 ? l10n.shieldTitle : l10n.liveActivity), style: const TextStyle(fontWeight: FontWeight.bold)),
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
        onDestinationSelected: (index) {
          setState(() {
            _currentIndex = index;
            _updateAnimationState();
          });
        },
        destinations: [
          NavigationDestination(icon: const Icon(Icons.radar_outlined), selectedIcon: const Icon(Icons.radar), label: l10n.scannerTab),
          NavigationDestination(icon: const Icon(Icons.shield_outlined), selectedIcon: const Icon(Icons.shield), label: l10n.shieldTab),
          NavigationDestination(icon: const Icon(Icons.list_alt_rounded), selectedIcon: const Icon(Icons.list_alt_rounded), label: l10n.activityTab),
        ],
      ),
    );
  }
}
