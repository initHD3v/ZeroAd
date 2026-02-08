import 'package:flutter/material.dart';
import 'package:zeroad/l10n.dart';

/// [ShieldTab] adalah tampilan utama status perlindungan.
/// 
/// Menggunakan animasi breathing untuk memberikan feedback visual
/// bahwa perlindungan sedang aktif.
class ShieldTab extends StatelessWidget {
  final bool isActive;
  final VoidCallback onToggle;
  final Animation<double> glowAnimation;
  final AppLocalizations l10n;
  
  // New props for dynamic blocklist
  final int blockedCount;
  final int safeCount;
  final DateTime? lastUpdate;
  final bool isUpdating;
  final VoidCallback onUpdate;

  const ShieldTab({
    super.key,
    required this.isActive,
    required this.onToggle,
    required this.glowAnimation,
    required this.l10n,
    this.blockedCount = 0,
    this.safeCount = 0,
    this.lastUpdate,
    this.isUpdating = false,
    required this.onUpdate,
  });

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;
    final activeColor = Colors.greenAccent.shade700;
    final inactiveColor = colorScheme.outline;

    return SingleChildScrollView(
      child: Padding(
        padding: const EdgeInsets.symmetric(vertical: 40),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            AnimatedBuilder(
              animation: glowAnimation,
              builder: (context, staticChild) {
                return Container(
                  height: 240,
                  width: 240,
                  decoration: BoxDecoration(
                    shape: BoxShape.circle,
                    color: isActive ? activeColor.withAlpha(30) : inactiveColor.withAlpha(20),
                    border: Border.all(color: isActive ? activeColor : inactiveColor, width: 4),
                    boxShadow: isActive 
                      ? [
                          BoxShadow(
                            color: activeColor.withAlpha(80), 
                            blurRadius: glowAnimation.value, 
                            spreadRadius: glowAnimation.value / 4
                          ),
                          BoxShadow(
                            color: activeColor.withAlpha(40), 
                            blurRadius: glowAnimation.value * 2, 
                            spreadRadius: glowAnimation.value / 2
                          )
                        ] 
                      : [],
                  ),
                  child: staticChild,
                );
              },
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  Icon(
                    isActive ? Icons.verified_user_rounded : Icons.shield_rounded, 
                    size: 80, 
                    color: isActive ? activeColor : inactiveColor
                  ),
                  const SizedBox(height: 10),
                  Text(
                    isActive ? l10n.activeLabel : l10n.offLabel, 
                    style: TextStyle(
                      fontSize: 18, 
                      fontWeight: FontWeight.w900, 
                      letterSpacing: 2, 
                      color: isActive ? activeColor : inactiveColor
                    )
                  ),
                ],
              ),
            ),
            const SizedBox(height: 40),
            Text(
              isActive ? l10n.protectionActive : l10n.protectionDisabled, 
              textAlign: TextAlign.center, 
              style: const TextStyle(fontSize: 20, fontWeight: FontWeight.bold)
            ),
            const SizedBox(height: 12),
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 40),
              child: Text(
                isActive ? l10n.adGuardActive : l10n.enableToBlock, 
                textAlign: TextAlign.center, 
                style: TextStyle(color: colorScheme.onSurfaceVariant, fontSize: 14)
              ),
            ),
            const SizedBox(height: 40),
            SizedBox(
              width: 220,
              height: 64,
              child: ElevatedButton.icon(
                onPressed: onToggle,
                icon: Icon(isActive ? Icons.power_settings_new : Icons.play_arrow_rounded),
                label: Text(isActive ? l10n.disconnect : l10n.connect),
                style: ElevatedButton.styleFrom(
                  backgroundColor: isActive ? colorScheme.surfaceContainerHighest : activeColor,
                  foregroundColor: isActive ? colorScheme.onSurface : Colors.white,
                  textStyle: const TextStyle(fontSize: 16, fontWeight: FontWeight.w900, letterSpacing: 1),
                  shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(32)),
                  elevation: isActive ? 0 : 8,
                ),
              ),
            ),
            
            // --- Info Database ---
            const SizedBox(height: 40),
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 24),
              child: Container(
                padding: const EdgeInsets.all(20),
                decoration: BoxDecoration(
                  color: colorScheme.surfaceContainerLow,
                  borderRadius: BorderRadius.circular(24),
                  border: Border.all(color: colorScheme.outlineVariant.withAlpha(100)),
                ),
                child: Column(
                  children: [
                    Row(
                      children: [
                        Icon(Icons.storage_rounded, size: 20, color: colorScheme.primary),
                        const SizedBox(width: 12),
                        Text(l10n.databaseStatus, style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 14)),
                        const Spacer(),
                        if (isUpdating)
                          const SizedBox(width: 16, height: 16, child: CircularProgressIndicator(strokeWidth: 2))
                        else
                          TextButton.icon(
                            onPressed: onUpdate,
                            icon: const Icon(Icons.refresh_rounded, size: 14),
                            label: Text(l10n.updateNow, style: const TextStyle(fontSize: 10, fontWeight: FontWeight.bold)),
                            style: TextButton.styleFrom(
                              padding: const EdgeInsets.symmetric(horizontal: 12),
                              visualDensity: VisualDensity.compact,
                            ),
                          ),
                      ],
                    ),
                    const Divider(height: 32),
                    Row(
                      mainAxisAlignment: MainAxisAlignment.spaceAround,
                      children: [
                        _buildInfoColumn(l10n.domainsBlocked, "$blockedCount", colorScheme),
                        Container(width: 1, height: 30, color: colorScheme.outlineVariant),
                        _buildInfoColumn(l10n.intelligenceDomains, "$safeCount", colorScheme),
                        Container(width: 1, height: 30, color: colorScheme.outlineVariant),
                        _buildInfoColumn(
                          l10n.lastUpdate, 
                          lastUpdate != null 
                            ? "${lastUpdate!.day}/${lastUpdate!.month} ${lastUpdate!.hour.toString().padLeft(2, '0')}:${lastUpdate!.minute.toString().padLeft(2, '0')}"
                            : l10n.neverUpdated, 
                          colorScheme
                        ),
                      ],
                    ),
                  ],
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildInfoColumn(String label, String value, ColorScheme colorScheme) {
    return Column(
      children: [
        Text(label, style: TextStyle(fontSize: 10, color: colorScheme.onSurfaceVariant, fontWeight: FontWeight.w500)),
        const SizedBox(height: 4),
        Text(value, style: const TextStyle(fontSize: 14, fontWeight: FontWeight.w900)),
      ],
    );
  }
}
