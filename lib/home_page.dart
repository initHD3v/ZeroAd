import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:flutter/services.dart';

// Logic
import 'package:zeroad/logic/security_provider.dart';
import 'package:zeroad/l10n.dart';

// UI Components
import 'package:zeroad/ui/widgets/app_icon.dart';
import 'package:zeroad/ui/tabs/shield_tab.dart';
import 'package:zeroad/ui/tabs/activity_tab.dart';
import 'package:zeroad/ui/tabs/scanner_tab.dart';
import 'package:zeroad/threat_detail_page.dart';

class MyHomePage extends StatefulWidget {
  const MyHomePage({super.key, required this.title});
  final String title;

  @override
  State<MyHomePage> createState() => _MyHomePageState();
}

class _MyHomePageState extends State<MyHomePage> with SingleTickerProviderStateMixin {
  late AnimationController _breathingController;
  late Animation<double> _glowAnimation;
  int _currentIndex = 1;

  @override
  void initState() {
    super.initState();
    _breathingController = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 2000),
    );
    _glowAnimation = Tween<double>(begin: 5.0, end: 35.0).animate(
      CurvedAnimation(parent: _breathingController, curve: Curves.easeInOut),
    );

    WidgetsBinding.instance.addPostFrameCallback((_) {
      final security = Provider.of<SecurityProvider>(context, listen: false);
      _requestPermissions(security);
    });
  }

  Future<void> _requestPermissions(SecurityProvider security) async {
    try {
      await const MethodChannel('zeroad.security/scanner').invokeMethod('requestNotificationPermission');
    } catch (_) {}
  }

  @override
  void dispose() {
    _breathingController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of();
    
    // Perhatikan: Scaffold tidak lagi dibungkus Consumer secara keseluruhan!
    return Scaffold(
      appBar: AppBar(
        title: Text(_getTitle(l10n), style: const TextStyle(fontWeight: FontWeight.bold)),
        centerTitle: true,
        actions: [
          if (_currentIndex == 0)
            Consumer<SecurityProvider>(
              builder: (context, security, _) => IconButton(
                icon: const Icon(Icons.refresh), 
                onPressed: security.isScanning ? null : security.performScan
              ),
            ),
          IconButton(icon: const Icon(Icons.info_outline), onPressed: () => _showAbout(context)),
        ],
      ),
      body: IndexedStack(
        index: _currentIndex,
        children: [
          // Hanya bagian konten yang mendengarkan Provider
          Consumer<SecurityProvider>(
            builder: (context, security, _) => ScannerTab(
              isScanning: security.isScanning,
              scanResult: security.lastScanResult,
              l10n: l10n,
              onRefresh: security.performScan,
            ),
          ),
          Consumer<SecurityProvider>(
            builder: (context, security, _) {
              if (security.isAdBlockActive) {
                if (!_breathingController.isAnimating) _breathingController.repeat(reverse: true);
              } else {
                _breathingController.stop();
              }
              return ShieldTab(
                isActive: security.isAdBlockActive,
                onToggle: security.toggleAdBlock,
                glowAnimation: _glowAnimation,
                l10n: l10n,
              );
            },
          ),
          Consumer<SecurityProvider>(
            builder: (context, security, _) => ActivityTab(
              vpnLogs: security.vpnLogs,
              trustedPackages: security.trustedPackages,
              isAdBlockActive: security.isAdBlockActive,
              logFilter: security.logFilter,
              l10n: l10n,
              onFilterChanged: security.setLogFilter,
              onClearLogs: security.clearLogs,
              onShowAppDetails: (name, pkg, logs) => _showAppDetails(context, security, name, pkg, logs),
              onRemoveFromWhitelist: security.removeFromWhitelist,
            ),
          ),
        ],
      ),
      bottomNavigationBar: NavigationBar(
        selectedIndex: _currentIndex,
        onDestinationSelected: (i) => setState(() => _currentIndex = i),
        destinations: [
          NavigationDestination(icon: const Icon(Icons.radar), label: l10n.scannerTab),
          NavigationDestination(icon: const Icon(Icons.shield), label: l10n.shieldTab),
          NavigationDestination(icon: const Icon(Icons.list_alt), label: l10n.activityTab),
        ],
      ),
    );
  }

  String _getTitle(AppLocalizations l10n) {
    switch (_currentIndex) {
      case 0: return l10n.scanTitle;
      case 1: return l10n.shieldTitle;
      default: return l10n.liveActivity;
    }
  }

  void _showAppDetails(BuildContext context, SecurityProvider security, String appName, String packageName, List<String> logs) {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (context) => DraggableScrollableSheet(
        initialChildSize: 0.7,
        maxChildSize: 0.95,
        builder: (context, scrollController) => Container(
          decoration: BoxDecoration(
            color: Theme.of(context).colorScheme.surface,
            borderRadius: const BorderRadius.vertical(top: Radius.circular(20)),
          ),
          child: Column(
            children: [
              ListTile(
                contentPadding: const EdgeInsets.all(16),
                leading: AppIcon(packageName: packageName, size: 48),
                title: Text(appName, style: const TextStyle(fontWeight: FontWeight.bold)),
                subtitle: Text(packageName),
                trailing: IconButton(
                  icon: const Icon(Icons.verified, color: Colors.green),
                  onPressed: () {
                    security.addToWhitelist(packageName);
                    Navigator.pop(context);
                  },
                ),
              ),
              const Divider(),
              Expanded(
                child: ListView.builder(
                  controller: scrollController,
                  itemCount: logs.length,
                  itemBuilder: (context, i) {
                    final parts = logs[i].split('|');
                    if (parts.length < 3) return const SizedBox();
                    final domain = parts[1];
                    final category = parts[2];
                    final isBlocked = logs[i].contains('BLOCKED') || category == 'AD_CONTENT';

                    return ListTile(
                      dense: true,
                      leading: Icon(isBlocked ? Icons.block : Icons.language, color: isBlocked ? Colors.red : Colors.grey, size: 18),
                      title: Text(domain, style: const TextStyle(fontSize: 13, fontWeight: FontWeight.w500)),
                      subtitle: Text(category, style: const TextStyle(fontSize: 11)),
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

  void _showAbout(BuildContext context) {
    showAboutDialog(context: context, applicationName: "ZeroAd", applicationVersion: "1.0.0");
  }
}
