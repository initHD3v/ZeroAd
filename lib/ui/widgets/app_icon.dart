
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

/// Widget [AppIcon] adalah komponen modular untuk menampilkan ikon aplikasi Android.
/// 
/// Keunggulan:
/// 1. Memiliki internal cache [_iconCache] untuk menghindari pemanggilan Native berulang.
/// 2. Menggunakan [GaplessPlayback] untuk transisi visual yang halus.
/// 3. Otomatis menangani fallback ke inisial huruf jika ikon gagal dimuat.
class AppIcon extends StatefulWidget {
  final String packageName;
  final double size;
  final String? fallbackLetter;

  const AppIcon({
    super.key,
    required this.packageName,
    this.size = 40,
    this.fallbackLetter,
  });

  @override
  State<AppIcon> createState() => _AppIconState();
}

class _AppIconState extends State<AppIcon> {
  static const MethodChannel _platform = MethodChannel('zeroad.security/scanner');
  static final Map<String, Uint8List> _iconCache = {};

  Future<Uint8List?> _getAppIcon(String packageName) async {
    if (_iconCache.containsKey(packageName)) return _iconCache[packageName];
    try {
      final Uint8List? icon = await _platform.invokeMethod('getAppIcon', {'packageName': packageName});
      if (icon != null) {
        _iconCache[packageName] = icon;
      }
      return icon;
    } catch (e) {
      return null;
    }
  }

  @override
  Widget build(BuildContext context) {
    // Gunakan pixel ratio perangkat untuk akurasi cache image
    final double pixelRatio = MediaQuery.of(context).devicePixelRatio;
    final int cacheSize = (widget.size * pixelRatio).toInt();

    return FutureBuilder<Uint8List?>(
      future: _getAppIcon(widget.packageName),
      builder: (context, snapshot) {
        if (snapshot.hasData && snapshot.data != null) {
          return ClipRRect(
            borderRadius: BorderRadius.circular(widget.size * 0.25),
            child: Image.memory(
              snapshot.data!,
              width: widget.size,
              height: widget.size,
              fit: BoxFit.cover,
              gaplessPlayback: true,
              cacheWidth: cacheSize,
              filterQuality: FilterQuality.low,
            ),
          );
        }
        
        // Fallback UI
        return FutureBuilder<String>(
          future: _getAppLabel(widget.packageName),
          builder: (context, labelSnapshot) {
            final label = labelSnapshot.data ?? widget.packageName;
            return Container(
              width: widget.size,
              height: widget.size,
              decoration: BoxDecoration(
                color: Theme.of(context).colorScheme.primaryContainer.withAlpha(50),
                borderRadius: BorderRadius.circular(widget.size * 0.25),
              ),
              child: Center(
                child: Text(
                  widget.fallbackLetter ?? (label.isNotEmpty ? label[0].toUpperCase() : "?"),
                  style: TextStyle(
                    fontSize: widget.size * 0.4,
                    fontWeight: FontWeight.bold,
                    color: Theme.of(context).colorScheme.primary,
                  ),
                ),
              ),
            );
          },
        );
      },
    );
  }

  Future<String> _getAppLabel(String packageName) async {
    try {
      final String label = await _platform.invokeMethod('getAppLabel', {'packageName': packageName});
      return label;
    } catch (e) {
      return packageName;
    }
  }
}
