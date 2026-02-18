import 'package:flutter/material.dart';
import 'package:zeroad/l10n.dart';
import 'package:zeroad/models.dart';
import 'package:zeroad/ui/widgets/app_icon.dart';
import 'package:zeroad/threat_detail_page.dart';

class ScannerTab extends StatelessWidget {
  final bool isScanning;
  final ScanResultModel? scanResult;
  final AppLocalizations l10n;
  final VoidCallback onRefresh;

  const ScannerTab({
    super.key,
    required this.isScanning,
    required this.scanResult,
    required this.l10n,
    required this.onRefresh,
  });

  @override
  Widget build(BuildContext context) {
    // 1. Tentukan State UI
    final bool neverScanned = scanResult == null;
    final bool hasThreats = (scanResult?.suspiciousPackagesCount ?? 0) > 0;

    return CustomScrollView(
      physics: const BouncingScrollPhysics(),
      slivers: [
        SliverToBoxAdapter(
          child: Padding(
            padding: const EdgeInsets.all(16.0),
            child: Column(
              children: [
                // Main Hero Card
                _buildHeroCard(context, neverScanned, hasThreats),
                const SizedBox(height: 24),
                
                // Stats Row (Hanya tampil jika sudah pernah scan)
                if (!neverScanned)
                  Row(
                    children: [
                      Expanded(child: _buildStatCard(context, "Apps Scanned", "${scanResult?.totalInstalledPackages ?? 0}", Icons.apps_rounded)),
                      const SizedBox(width: 12),
                      Expanded(child: _buildStatCard(context, "Threats", "${scanResult?.suspiciousPackagesCount ?? 0}", Icons.security_rounded, isError: hasThreats)),
                    ],
                  ),
                
                if (!neverScanned && scanResult!.threats.isNotEmpty) ...[
                  const SizedBox(height: 32),
                  Align(
                    alignment: Alignment.centerLeft,
                    child: Text(
                      "LAPORAN DETEKSI",
                      style: TextStyle(
                        fontSize: 12, 
                        fontWeight: FontWeight.bold, 
                        letterSpacing: 1.2,
                        color: Theme.of(context).colorScheme.onSurfaceVariant
                      ),
                    ),
                  ),
                  const SizedBox(height: 16),
                ],
              ],
            ),
          ),
        ),
        
        // List Ancaman
        if (!neverScanned && scanResult!.threats.isNotEmpty)
          _buildThreatList(context)
        else if (!neverScanned && !isScanning && scanResult!.threats.isEmpty)
          _buildSecureState(context),
          
        // Jika belum pernah scan, tampilkan instruksi di bawah hero card
        if (neverScanned && !isScanning)
          SliverFillRemaining(
            hasScrollBody: false,
            child: Padding(
              padding: const EdgeInsets.all(32.0),
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  Icon(Icons.info_outline_rounded, color: Theme.of(context).colorScheme.outline.withAlpha(100)),
                  const SizedBox(height: 12),
                  Text(
                    "ZeroAd akan memindai manifest aplikasi Anda untuk mencari pola adware tersembunyi.",
                    textAlign: TextAlign.center,
                    style: TextStyle(color: Theme.of(context).colorScheme.outline, fontSize: 13),
                  ),
                ],
              ),
            ),
          ),
      ],
    );
  }

  Widget _buildHeroCard(BuildContext context, bool neverScanned, bool hasThreats) {
    // Tentukan warna dan ikon berdasarkan state
    Color colorTop;
    Color colorBottom;
    IconData icon;
    String title;
    String subtitle;

    if (isScanning) {
      colorTop = Theme.of(context).colorScheme.primary;
      colorBottom = Theme.of(context).colorScheme.primaryContainer;
      icon = Icons.sync;
      title = l10n.scanningStatus;
      subtitle = "Menganalisis manifest aplikasi...";
    } else if (neverScanned) {
      colorTop = Colors.blueGrey.shade700;
      colorBottom = Colors.blueGrey.shade400;
      icon = Icons.radar_rounded;
      title = "Siap Memindai";
      subtitle = "Sistem belum diperiksa hari ini";
    } else if (hasThreats) {
      colorTop = Colors.red.shade800;
      colorBottom = Colors.red.shade400;
      icon = Icons.gpp_maybe_rounded;
      title = "Risiko Terdeteksi";
      subtitle = "ZeroAd menemukan ancaman potensial";
    } else {
      colorTop = Colors.green.shade700;
      colorBottom = Colors.green.shade400;
      icon = Icons.verified_user_rounded;
      title = "Sistem Aman";
      subtitle = "Tidak ditemukan pola adware";
    }

    return Container(
      width: double.infinity,
      padding: const EdgeInsets.symmetric(vertical: 40, horizontal: 24),
      decoration: BoxDecoration(
        gradient: LinearGradient(begin: Alignment.topLeft, end: Alignment.bottomRight, colors: [colorTop, colorBottom]),
        borderRadius: BorderRadius.circular(32),
        boxShadow: [
          BoxShadow(color: colorTop.withAlpha(60), blurRadius: 20, offset: const Offset(0, 10))
        ],
      ),
      child: Column(
        children: [
          isScanning 
            ? const RepaintBoundary(
                child: SizedBox(
                  height: 80, 
                  width: 80, 
                  child: CircularProgressIndicator(color: Colors.white, strokeWidth: 6)
                ),
              )
            : Icon(icon, size: 80, color: Colors.white),
          const SizedBox(height: 24),
          Text(title, textAlign: TextAlign.center, style: const TextStyle(color: Colors.white, fontSize: 24, fontWeight: FontWeight.w900)),
          const SizedBox(height: 8),
          Text(subtitle, textAlign: TextAlign.center, style: TextStyle(color: Colors.white.withAlpha(200), fontSize: 14)),
          
          // Tombol Aksi Utama di dalam Hero Card agar sangat jelas
          if (!isScanning) ...[
            const SizedBox(height: 32),
            SizedBox(
              width: double.infinity,
              height: 56,
              child: ElevatedButton.icon(
                onPressed: onRefresh,
                icon: Icon(neverScanned ? Icons.search : Icons.refresh_rounded, color: colorTop),
                label: Text(
                  neverScanned ? "MULAI PEMINDAIAN" : "PINDAI ULANG",
                  style: TextStyle(color: colorTop, fontWeight: FontWeight.w900, letterSpacing: 1.2),
                ),
                style: ElevatedButton.styleFrom(
                  backgroundColor: Colors.white,
                  shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
                  elevation: 0,
                ),
              ),
            ),
          ],
        ],
      ),
    );
  }

  Widget _buildStatCard(BuildContext context, String title, String value, IconData icon, {bool isError = false}) {
    final color = isError ? Colors.red.shade700 : Theme.of(context).colorScheme.primary;
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: Theme.of(context).colorScheme.surfaceContainerLow,
        borderRadius: BorderRadius.circular(20),
        border: Border.all(color: Theme.of(context).colorScheme.outlineVariant.withAlpha(100)),
      ),
      child: Row(
        children: [
          Icon(icon, color: color, size: 24),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(title, style: TextStyle(fontSize: 11, color: Theme.of(context).colorScheme.onSurfaceVariant), maxLines: 1, overflow: TextOverflow.ellipsis),
                Text(value, style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold, color: Theme.of(context).colorScheme.onSurface), maxLines: 1, overflow: TextOverflow.ellipsis),
              ],
            ),
          )
        ],
      ),
    );
  }

  Widget _buildThreatList(BuildContext context) {
    List<AppThreatInfo> highRisk = [];
    List<AppThreatInfo> mediumRisk = [];
    List<AppThreatInfo> lowRisk = [];

    for (var app in scanResult!.threats) {
      if (app.detectedThreats.any((t) => t.severity == 'HIGH')) {
        highRisk.add(app);
      } else if (app.detectedThreats.any((t) => t.severity == 'MEDIUM')) {
        mediumRisk.add(app);
      } else {
        lowRisk.add(app);
      }
    }

    return SliverList(
      delegate: SliverChildListDelegate([
        if (highRisk.isNotEmpty) _buildSeveritySection(context, l10n.criticalRisk, highRisk, Colors.red.shade700, Icons.gpp_bad_rounded),
        if (mediumRisk.isNotEmpty) _buildSeveritySection(context, l10n.warnings, mediumRisk, Colors.orange.shade800, Icons.warning_amber_rounded),
        if (lowRisk.isNotEmpty) _buildSeveritySection(context, l10n.lowPriority, lowRisk, Colors.blueGrey.shade700, Icons.info_outline_rounded),
        const SizedBox(height: 100),
      ]),
    );
  }

  Widget _buildSeveritySection(BuildContext context, String title, List<AppThreatInfo> apps, Color color, IconData icon) {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Icon(icon, color: color, size: 18),
              const SizedBox(width: 8),
              Expanded(
                child: Text(
                  title, 
                  style: TextStyle(color: color, fontWeight: FontWeight.bold, fontSize: 16),
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                ),
              ),
              const SizedBox(width: 8),
              Text("${apps.length}", style: TextStyle(color: color, fontWeight: FontWeight.bold, fontSize: 12)),
            ],
          ),
          const SizedBox(height: 12),
          ...apps.map((app) => _buildThreatItem(context, app)),
        ],
      ),
    );
  }

  Widget _buildThreatItem(BuildContext context, AppThreatInfo app) {
    return Card(
      margin: const EdgeInsets.only(bottom: 10),
      elevation: 0,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(16),
        side: BorderSide(color: Theme.of(context).colorScheme.outlineVariant.withAlpha(80)),
      ),
      child: ListTile(
        onTap: () => Navigator.push(context, MaterialPageRoute(builder: (context) => ThreatDetailPage(appThreatInfo: app))).then((_) => onRefresh()),
        leading: AppIcon(packageName: app.packageName, size: 48),
        title: Text(app.appName, style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 15), maxLines: 1, overflow: TextOverflow.ellipsis),
        subtitle: Text(app.packageName, style: const TextStyle(fontSize: 11, color: Colors.grey), maxLines: 1, overflow: TextOverflow.ellipsis),
        trailing: const Icon(Icons.chevron_right_rounded, size: 20),
      ),
    );
  }

  Widget _buildSecureState(BuildContext context) {
    return SliverFillRemaining(
      hasScrollBody: false,
      child: Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(Icons.verified_user_rounded, size: 64, color: Colors.green.withAlpha(100)),
            const SizedBox(height: 16),
            const Text("Semua aplikasi terlihat bersih!", style: TextStyle(color: Colors.green, fontWeight: FontWeight.w500)),
          ],
        ),
      ),
    );
  }
}