import 'dart:async';
import 'dart:convert';
import 'package:flutter/services.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:zeroad/models.dart';

/// [SecurityController] bertindak sebagai pusat logika keamanan.
/// 
/// Memisahkan Platform Channels dari UI memungkinkan pengujian yang lebih mudah
/// dan kode UI yang lebih bersih. Semua State Management terkait keamanan
/// diproses di sini.
class SecurityController {
  static const MethodChannel _platform = MethodChannel('zeroad.security/scanner');
  static const EventChannel _stream = EventChannel('zeroad.security/stream');

  // Singleton pattern agar state konsisten di seluruh aplikasi
  static final SecurityController _instance = SecurityController._internal();
  factory SecurityController() => _instance;
  SecurityController._internal();

  final _logController = StreamController<String>.broadcast();
  StreamSubscription? _nativeSub;

  Stream<String> get logStream => _logController.stream;

  void init() {
    _nativeSub?.cancel();
    _nativeSub = _stream.receiveBroadcastStream().listen((log) {
      _logController.add(log.toString());
    });
  }

  void dispose() {
    _nativeSub?.cancel();
    _logController.close();
  }

  // --- VPN Logic ---
  
  Future<bool> toggleAdBlock(bool active) async {
    try {
      final bool result = await _platform.invokeMethod(active ? 'stopAdBlock' : 'startAdBlock');
      final prefs = await SharedPreferences.getInstance();
      await prefs.setBool('adblock_active', result);
      return result;
    } catch (e) {
      rethrow;
    }
  }

  // --- Scanner Logic ---

  Future<ScanResultModel> scanForAdware() async {
    try {
      final String response = await _platform.invokeMethod('scan');
      final Map<String, dynamic> jsonResponse = jsonDecode(response);
      
      final prefs = await SharedPreferences.getInstance();
      final List<String> whitelist = prefs.getStringList('whitelisted_apps') ?? [];

      ScanResultModel fullResult = ScanResultModel.fromJson(jsonResponse);
      
      // Filter out whitelisted apps from the threat report
      List<AppThreatInfo> filteredThreats = fullResult.threats
          .where((threat) => !whitelist.contains(threat.packageName))
          .toList();

      return ScanResultModel(
        totalInstalledPackages: fullResult.totalInstalledPackages,
        suspiciousPackagesCount: filteredThreats.length,
        threats: filteredThreats,
      );
    } catch (e) {
      rethrow;
    }
  }

  // --- Whitelist Logic ---

  Future<bool> addToWhitelist(String packageName) async {
    final success = await _platform.invokeMethod('addToWhitelist', {'packageName': packageName});
    if (success == true) {
      final prefs = await SharedPreferences.getInstance();
      final currentSet = prefs.getStringList('whitelisted_apps') ?? [];
      if (!currentSet.contains(packageName)) {
        currentSet.add(packageName);
        await prefs.setStringList('whitelisted_apps', currentSet);
      }
    }
    return success ?? false;
  }

  Future<bool> removeFromWhitelist(String packageName) async {
    final success = await _platform.invokeMethod('removeFromWhitelist', {'packageName': packageName});
    if (success == true) {
      final prefs = await SharedPreferences.getInstance();
      final currentSet = prefs.getStringList('whitelisted_apps') ?? [];
      currentSet.remove(packageName);
      await prefs.setStringList('whitelisted_apps', currentSet);
    }
    return success ?? false;
  }

  Future<String> getAppLabel(String packageName) async {
    try {
      return await _platform.invokeMethod('getAppLabel', {'packageName': packageName});
    } catch (e) {
      return packageName;
    }
  }

  Future<bool> addDomainToWhitelist(String domain) async {
    final success = await _platform.invokeMethod('addDomainToWhitelist', {'domain': domain});
    return success ?? false;
  }
}
