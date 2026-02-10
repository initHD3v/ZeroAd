import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:zeroad/l10n.dart';
import 'package:zeroad/ui/widgets/app_icon.dart';

/// [ActivityTab] menyajikan log trafik real-time dan manajemen aplikasi terpercaya.
class ActivityTab extends StatelessWidget {
  final List<String> vpnLogs;
  final List<String> trustedPackages;
  final bool isAdBlockActive;
  final String logFilter;
  final Function(String) onFilterChanged;
  final VoidCallback onClearLogs;
  final Function(String, String, List<String>) onShowAppDetails;
  final Function(String) onRemoveFromWhitelist;
  final AppLocalizations l10n;

  const ActivityTab({
    super.key,
    required this.vpnLogs,
    required this.trustedPackages,
    required this.isAdBlockActive,
    required this.logFilter,
    required this.onFilterChanged,
    required this.onClearLogs,
    required this.onShowAppDetails,
    required this.onRemoveFromWhitelist,
    required this.l10n,
  });

  @override
  Widget build(BuildContext context) {
    return DefaultTabController(
      length: 2,
      child: Column(
        children: [
          Container(
            color: Theme.of(context).colorScheme.surface,
            child: TabBar(
              labelColor: Theme.of(context).colorScheme.primary,
              unselectedLabelColor: Colors.grey,
              indicatorColor: Theme.of(context).colorScheme.primary,
              tabs: [
                Tab(text: l10n.trafficSubTab),
                Tab(text: l10n.trustedSubTab),
              ],
            ),
          ),
          Expanded(
            child: TabBarView(
              children: [
                _buildTrafficTab(context),
                _buildTrustedTab(context),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildTrafficTab(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;
    
    final filteredLogs = vpnLogs.where((log) {
      if (logFilter == 'ALL') return true;
      final parts = log.split('|');
      if (parts.length < 3) return false;
      
      final status = parts[2];
      // Filter Cerdas: Tampilkan jika mengandung indikasi blokir atau ancaman
      return status == 'AD_CONTENT' || 
             status == 'WEB_SHIELD' || 
             status == 'AD_ENGINE' || 
             status == 'DOH_BLOCK' ||
             status == 'BLOCKED' ||
             status == 'TRACKER';
    }).toList();

    final Map<String, List<String>> groupedLogs = {};
    final Map<String, String> appNames = {};

    for (var log in filteredLogs) {
      final parts = log.split('|');
      if (parts.length < 6) continue;
      final packageName = parts[4];
      groupedLogs.putIfAbsent(packageName, () => []).add(log);
      appNames[packageName] = parts[5];
    }

    final sortedKeys = groupedLogs.keys.toList();

    return Column(
      children: [
        Container(
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
          color: colorScheme.surfaceContainerLow,
          child: Row(
            children: [
              _buildFilterChip(context, "ALL APPS", 'ALL'),
              const SizedBox(width: 8),
              _buildFilterChip(context, "THREATS ONLY", 'ADS'),
              const Spacer(),
              IconButton(onPressed: onClearLogs, icon: const Icon(Icons.refresh_rounded), color: colorScheme.primary),
            ],
          ),
        ),
        Expanded(
          child: sortedKeys.isEmpty 
            ? _buildEmptyState(context, isAdBlockActive ? l10n.noTraffic : l10n.shieldInactive)
            : ListView.builder(
                padding: const EdgeInsets.symmetric(vertical: 8),
                itemCount: sortedKeys.length,
                itemBuilder: (context, index) {
                  final pkg = sortedKeys[index];
                  final logs = groupedLogs[pkg]!;
                  final name = appNames[pkg] ?? "Unknown App";
                  if (trustedPackages.contains(pkg)) return const SizedBox();

                  final isSystem = pkg == "com.android.system" || pkg.startsWith("system.uid");
                  final blockedCount = logs.where((l) => l.contains('|BLOCKED|') || l.contains('AD_CONTENT')).length;
                  
                  // Ambil domain terakhir untuk membantu identifikasi
                  final lastLogParts = logs.first.split('|');
                  final lastDomain = lastLogParts.length > 1 ? lastLogParts[1] : '';

                  return Card(
                    margin: const EdgeInsets.symmetric(horizontal: 12, vertical: 4),
                    elevation: 0,
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(12),
                      side: BorderSide(color: colorScheme.outlineVariant.withAlpha(50)),
                    ),
                    child: ListTile(
                      onTap: () => onShowAppDetails(name, pkg, logs),
                      leading: AppIcon(packageName: pkg, fallbackLetter: isSystem ? "S" : name[0]),
                      title: Text(isSystem ? "Layanan Sistem" : name, style: const TextStyle(fontWeight: FontWeight.bold)),
                      subtitle: Text(
                        isSystem ? "Domain: $lastDomain" : pkg, 
                        style: TextStyle(fontSize: 11, color: isSystem ? colorScheme.primary : null, fontWeight: isSystem ? FontWeight.bold : null),
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                      ),
                      trailing: Row(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          if (blockedCount > 0)
                            Container(
                              padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
                              decoration: BoxDecoration(color: colorScheme.error, borderRadius: BorderRadius.circular(12)),
                              child: Text("$blockedCount Ad", style: const TextStyle(color: Colors.white, fontSize: 10, fontWeight: FontWeight.bold)),
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

  Widget _buildTrustedTab(BuildContext context) {
    return trustedPackages.isEmpty 
      ? _buildEmptyState(context, l10n.isId ? "Belum ada aplikasi terpercaya" : "No trusted apps yet")
      : ListView.builder(
          padding: const EdgeInsets.symmetric(vertical: 8),
          itemCount: trustedPackages.length,
          itemBuilder: (context, index) {
            final pkg = trustedPackages[index];
            return Card(
              margin: const EdgeInsets.symmetric(horizontal: 12, vertical: 4),
              elevation: 0,
              shape: RoundedRectangleBorder(
                borderRadius: BorderRadius.circular(12),
                side: BorderSide(color: Colors.blue.withAlpha(50)),
              ),
              child: ListTile(
                leading: AppIcon(packageName: pkg),
                title: FutureBuilder<String>(
                  future: const MethodChannel('zeroad.security/scanner').invokeMethod<String>('getAppLabel', {'packageName': pkg}).then((value) => value ?? pkg),
                  builder: (context, snapshot) {
                    return Text(
                      snapshot.data ?? pkg, 
                      style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 14)
                    );
                  },
                ),
                subtitle: Text(l10n.isId ? "Akses Internet Langsung" : "Direct Internet Access", style: const TextStyle(fontSize: 11)),
                trailing: IconButton(
                  icon: const Icon(Icons.block_rounded, color: Colors.red),
                  onPressed: () => onRemoveFromWhitelist(pkg),
                ),
              ),
            );
          },
        );
  }

  Widget _buildEmptyState(BuildContext context, String message) {
    return Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Icon(Icons.history_toggle_off_rounded, size: 64, color: Theme.of(context).colorScheme.onSurfaceVariant.withAlpha(50)),
          const SizedBox(height: 16),
          Text(message, style: TextStyle(color: Theme.of(context).colorScheme.onSurfaceVariant)),
        ],
      ),
    );
  }

  Widget _buildFilterChip(BuildContext context, String label, String value) {
    final isSelected = logFilter == value;
    final colorScheme = Theme.of(context).colorScheme;
    return GestureDetector(
      onTap: () => onFilterChanged(value),
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
}
