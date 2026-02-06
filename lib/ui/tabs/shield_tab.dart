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

  const ShieldTab({
    super.key,
    required this.isActive,
    required this.onToggle,
    required this.glowAnimation,
    required this.l10n,
  });

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;
    final activeColor = Colors.greenAccent.shade700;
    final inactiveColor = colorScheme.outline;

    return Center(
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
          const SizedBox(height: 60),
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
          const SizedBox(height: 60),
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
        ],
      ),
    );
  }
}
