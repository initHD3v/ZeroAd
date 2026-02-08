import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:flutter/services.dart';
import 'package:url_launcher/url_launcher.dart';
import 'package:share_plus/share_plus.dart';

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

  Future<void> _launchUrl(String url) async {
    final Uri uri = Uri.parse(url);
    if (!await launchUrl(uri, mode: LaunchMode.externalApplication)) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text("Tidak dapat membuka: $url")),
        );
      }
    }
  }

  void _showAbout(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;
    
    showDialog(
      context: context,
      barrierDismissible: true,
      builder: (context) => AlertDialog(
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(32)),
        contentPadding: EdgeInsets.zero,
        clipBehavior: Clip.antiAlias,
        content: SizedBox(
          width: MediaQuery.of(context).size.width * 0.9,
          child: SingleChildScrollView(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                Container(
                  width: double.infinity,
                  padding: const EdgeInsets.symmetric(vertical: 40),
                  decoration: BoxDecoration(
                    gradient: LinearGradient(
                      begin: Alignment.topLeft,
                      end: Alignment.bottomRight,
                      colors: [colorScheme.primary, colorScheme.primaryContainer],
                    ),
                  ),
                  child: Column(
                    children: [
                      Container(
                        padding: const EdgeInsets.all(12),
                        decoration: BoxDecoration(
                          color: Colors.white,
                          borderRadius: BorderRadius.circular(24),
                          boxShadow: [
                            BoxShadow(
                              color: Colors.black.withAlpha(40),
                              blurRadius: 20,
                              offset: const Offset(0, 10),
                            )
                          ],
                        ),
                        child: ClipRRect(
                          borderRadius: BorderRadius.circular(16),
                          child: Image.asset("zeroad.png", width: 80, height: 80),
                        ),
                      ),
                      const SizedBox(height: 20),
                      const Text(
                        "ZeroAd",
                        style: TextStyle(
                          color: Colors.white,
                          fontSize: 28,
                          fontWeight: FontWeight.w900,
                          letterSpacing: 1.5,
                        ),
                      ),
                                        const Text(
                                          "Version 1.2.0+3",
                                          style: TextStyle(
                                            color: Colors.white70,
                                            fontSize: 14,
                                            fontWeight: FontWeight.w500,
                                          ),
                                        ),                    ],
                  ),
                ),
                Padding(
                  padding: const EdgeInsets.all(24),
                  child: Column(
                    children: [
                      Text(
                        "Solusi keamanan Android modern yang menggabungkan deteksi adware berbasis manifest dengan Global DNS Shield tanpa akses Root.",
                        textAlign: TextAlign.center,
                        style: TextStyle(fontSize: 14, color: colorScheme.onSurfaceVariant, height: 1.5),
                      ),
                      const SizedBox(height: 32),
                      Container(
                        padding: const EdgeInsets.all(16),
                        decoration: BoxDecoration(
                          color: colorScheme.surfaceContainerHighest.withAlpha(100),
                          borderRadius: BorderRadius.circular(20),
                          border: Border.all(color: colorScheme.outlineVariant.withAlpha(100)),
                        ),
                        child: Column(
                          children: [
                            _buildAboutRow(Icons.code_rounded, "Developer", "initHD3v"),
                            const Divider(height: 24),
                            _buildAboutRow(Icons.terminal_rounded, "Engine", "Flutter & Kotlin"),
                            const Divider(height: 24),
                            _buildAboutRow(Icons.security_rounded, "Privacy", "100% Local-First"),
                          ],
                        ),
                      ),
                      const SizedBox(height: 32),
                      Row(
                        mainAxisAlignment: MainAxisAlignment.spaceEvenly,
                        children: [
                          _buildIconButton(context, Icons.code_rounded, "GitHub", () => _launchUrl("https://github.com/initHD3v/ZeroAd")),
                          _buildIconButton(context, Icons.email_rounded, "Email", () => _launchUrl("mailto:arunapicturee@gmail.com?subject=ZeroAd Feedback")),
                          _buildIconButton(context, Icons.share_rounded, "Share", () => Share.share("Lindungi HP kamu dari adware dengan ZeroAd! Download di: https://github.com/initHD3v/ZeroAd")),
                        ],
                      ),
                      const SizedBox(height: 24),
                      Text(
                        "© 2026 ZeroAd Project • All Rights Reserved",
                        style: TextStyle(fontSize: 10, color: colorScheme.outline, fontWeight: FontWeight.bold),
                      ),
                    ],
                  ),
                ),
              ],
            ),
          ),
        ),
        actions: [
          Padding(
            padding: const EdgeInsets.only(right: 16, bottom: 16),
            child: TextButton(
              onPressed: () => Navigator.pop(context),
              child: const Text("TUTUP", style: TextStyle(fontWeight: FontWeight.bold)),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildAboutRow(IconData icon, String label, String value) {
    return Row(
      children: [
        Icon(icon, size: 20, color: Colors.blueGrey),
        const SizedBox(width: 12),
        Text(label, style: const TextStyle(fontSize: 13, fontWeight: FontWeight.bold, color: Colors.grey)),
        const Spacer(),
        Text(value, style: const TextStyle(fontSize: 13, fontWeight: FontWeight.bold)),
      ],
    );
  }

  Widget _buildIconButton(BuildContext context, IconData icon, String label, VoidCallback onTap) {
    return Column(
      children: [
        IconButton.filledTonal(onPressed: onTap, icon: Icon(icon, size: 20)),
        const SizedBox(height: 4),
        Text(label, style: const TextStyle(fontSize: 10, fontWeight: FontWeight.bold)),
      ],
    );
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of();
    
    return Consumer<SecurityProvider>(
      builder: (context, security, child) {
        if (security.isAdBlockActive && _currentIndex == 1) {
          if (!_breathingController.isAnimating) _breathingController.repeat(reverse: true);
        } else {
          _breathingController.stop();
        }

        return Scaffold(
          appBar: AppBar(
            title: Text(_getTitle(l10n), style: const TextStyle(fontWeight: FontWeight.bold)),
            centerTitle: true,
            actions: [
              if (_currentIndex == 0)
                IconButton(
                  icon: const Icon(Icons.refresh), 
                  onPressed: security.isScanning ? null : security.performScan
                ),
              IconButton(icon: const Icon(Icons.info_outline), onPressed: () => _showAbout(context)),
            ],
          ),
          body: IndexedStack(
            index: _currentIndex,
            children: [
              ScannerTab(
                isScanning: security.isScanning,
                scanResult: security.lastScanResult,
                l10n: l10n,
                onRefresh: security.performScan,
              ),
                              ShieldTab(
                                isActive: security.isAdBlockActive,
                                onToggle: security.toggleAdBlock,
                                glowAnimation: _glowAnimation,
                                l10n: l10n,
                                blockedCount: security.blockedDomainsCount,
                                safeCount: security.autoWhitelistedCount,
                                lastUpdate: security.lastBlocklistUpdate,
                                isUpdating: security.isUpdatingBlocklist,
                                onUpdate: security.updateDynamicBlocklist,
                              ),              ActivityTab(
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
      },
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
    final allowedLogs = logs.where((l) => l.contains('ALLOWED') || l.contains('APP_WHITELISTED') || l.contains('DNS_QUERY')).toList();
    final blockedLogs = logs.where((l) => l.contains('BLOCKED') || l.contains('AD_CONTENT')).toList();

    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (context) => DraggableScrollableSheet(
        initialChildSize: 0.75,
        maxChildSize: 0.95,
        minChildSize: 0.5,
        builder: (context, scrollController) => Container(
          decoration: BoxDecoration(
            color: Theme.of(context).colorScheme.surface,
            borderRadius: const BorderRadius.vertical(top: Radius.circular(28)),
          ),
          child: DefaultTabController(
            length: 2,
            child: Column(
              children: [
                // Handle bar
                Center(
                  child: Container(
                    margin: const EdgeInsets.only(top: 12),
                    width: 40, height: 4,
                    decoration: BoxDecoration(color: Colors.grey.withAlpha(100), borderRadius: BorderRadius.circular(2)),
                  ),
                ),
                ListTile(
                  contentPadding: const EdgeInsets.fromLTRB(24, 16, 16, 8),
                  leading: AppIcon(packageName: packageName, size: 52),
                  title: Text(appName, style: const TextStyle(fontWeight: FontWeight.w900, fontSize: 18)),
                  subtitle: Text(packageName, style: const TextStyle(fontSize: 12)),
                  trailing: IconButton.filledTonal(
                    icon: const Icon(Icons.verified_user_rounded, color: Colors.green),
                    onPressed: () {
                      security.addToWhitelist(packageName);
                      Navigator.pop(context);
                    },
                  ),
                ),
                TabBar(
                  dividerColor: Colors.transparent,
                  indicatorSize: TabBarIndicatorSize.label,
                  tabs: [
                    Tab(child: Row(mainAxisSize: MainAxisSize.min, children: [
                      const Icon(Icons.public, size: 16), const SizedBox(width: 8), 
                      Text("AMAN (${allowedLogs.length})", style: const TextStyle(fontWeight: FontWeight.bold))
                    ])),
                    Tab(child: Row(mainAxisSize: MainAxisSize.min, children: [
                      const Icon(Icons.block_flipped, size: 16), const SizedBox(width: 8), 
                      Text("DIBLOKIR (${blockedLogs.length})", style: const TextStyle(fontWeight: FontWeight.bold))
                    ])),
                  ],
                ),
                const Divider(height: 1),
                Expanded(
                  child: TabBarView(
                    children: [
                      _buildLogList(allowedLogs, scrollController, true),
                      _buildLogList(blockedLogs, scrollController, false),
                    ],
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildLogList(List<String> filteredLogs, ScrollController scrollController, bool isSafe) {
    if (filteredLogs.isEmpty) {
      return Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(isSafe ? Icons.cloud_done_outlined : Icons.security_outlined, size: 48, color: Colors.grey.withAlpha(100)),
            const SizedBox(height: 16),
            Text(isSafe ? "Belum ada trafik aman" : "Tidak ada iklan terdeteksi", style: const TextStyle(color: Colors.grey)),
          ],
        ),
      );
    }

    return ListView.builder(
      controller: scrollController,
      padding: const EdgeInsets.symmetric(vertical: 8),
      itemCount: filteredLogs.length,
      itemBuilder: (context, i) {
        final parts = filteredLogs[i].split('|');
        if (parts.length < 3) return const SizedBox();
        final domain = parts[1];
        final type = parts[2];

        return ListTile(
          dense: true,
          contentPadding: const EdgeInsets.symmetric(horizontal: 24, vertical: 0),
          leading: isSafe 
            ? Stack(
                alignment: Alignment.bottomRight,
                children: [
                  const Icon(Icons.public, color: Colors.green, size: 24),
                  Container(
                    decoration: const BoxDecoration(color: Colors.white, shape: BoxShape.circle),
                    child: const Icon(Icons.check_circle, color: Colors.green, size: 12),
                  ),
                ],
              )
            : const Icon(Icons.block, color: Colors.red, size: 22),
          title: Text(domain, style: const TextStyle(fontSize: 13, fontWeight: FontWeight.w600, letterSpacing: -0.2)),
          subtitle: Text(type, style: const TextStyle(fontSize: 10, color: Colors.grey)),
          trailing: isSafe 
            ? const Icon(Icons.bolt, color: Colors.amber, size: 14) // Menandakan jalur cepat/ISP Direct
            : null,
        );
      },
    );
  }
}