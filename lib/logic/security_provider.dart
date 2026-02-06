import 'dart:async';
import 'dart:convert';
import 'package:flutter/foundation.dart'; // For compute
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:zeroad/models.dart';

/// Helper function for compute (must be top-level or static)
/// Memindahkan pengolahan data JSON yang berat ke Isolate terpisah.
ScanResultModel _processScanData(Map<String, dynamic> data) {
  final String response = data['response'];
  final List<String> whitelist = data['whitelist'];
  
  final Map<String, dynamic> jsonResponse = jsonDecode(response);
  final ScanResultModel fullResult = ScanResultModel.fromJson(jsonResponse);
  
  final List<AppThreatInfo> filteredThreats = fullResult.threats
      .where((threat) => !whitelist.contains(threat.packageName))
      .toList();

  return ScanResultModel(
    totalInstalledPackages: fullResult.totalInstalledPackages,
    suspiciousPackagesCount: filteredThreats.length,
    threats: filteredThreats,
  );
}

/// [SecurityProvider] mengelola State aplikasi secara reaktif menggunakan Provider.
class SecurityProvider with ChangeNotifier {
  static const MethodChannel _platform = MethodChannel('zeroad.security/scanner');
  static const EventChannel _stream = EventChannel('zeroad.security/stream');

  // -- State Data --
  bool _isAdBlockActive = false;
  List<String> _vpnLogs = [];
  List<String> _trustedPackages = [];
  ScanResultModel? _lastScanResult;
  bool _isScanning = false;
  String _logFilter = 'ALL';

  // -- Getters --
  bool get isAdBlockActive => _isAdBlockActive;
  List<String> get vpnLogs => _vpnLogs;
  List<String> get trustedPackages => _trustedPackages;
  ScanResultModel? get lastScanResult => _lastScanResult;
  bool get isScanning => _isScanning;
  String get logFilter => _logFilter;

  StreamSubscription? _nativeSub;

  List<String> _pendingLogs = [];
  Timer? _batchTimer;

  /// Inisialisasi awal: Memuat data dari storage dan mendengarkan stream log.
  Future<void> init() async {
    final prefs = await SharedPreferences.getInstance();
    _isAdBlockActive = prefs.getBool('adblock_active') ?? false;
    _trustedPackages = prefs.getStringList('whitelisted_apps') ?? [];
    
    _nativeSub?.cancel();
    _nativeSub = _stream.receiveBroadcastStream().listen((log) {
      _pendingLogs.add(log.toString());
      _startBatchTimer();
    });

    notifyListeners();
  }

  void _startBatchTimer() {
    if (_batchTimer?.isActive ?? false) return;
    
    _batchTimer = Timer(const Duration(milliseconds: 600), () {
      if (_pendingLogs.isNotEmpty) {
        // Gabungkan log baru dengan log lama, filter duplikasi domain beruntun
        final List<String> newLogs = [];
        String? lastDomain;
        
        for (var log in _pendingLogs.reversed) {
          final parts = log.split('|');
          if (parts.length > 1) {
            final currentDomain = parts[1];
            if (currentDomain != lastDomain) {
              newLogs.add(log);
              lastDomain = currentDomain;
            }
          }
        }

        _vpnLogs.insertAll(0, newLogs);
        if (_vpnLogs.length > 300) _vpnLogs = _vpnLogs.sublist(0, 300);
        
        _pendingLogs.clear();
        notifyListeners(); 
      }
    });
  }

  // --- VPN Actions ---

  Future<void> toggleAdBlock() async {
    try {
      final bool result = await _platform.invokeMethod(_isAdBlockActive ? 'stopAdBlock' : 'startAdBlock');
      _isAdBlockActive = result;
      
      final prefs = await SharedPreferences.getInstance();
      await prefs.setBool('adblock_active', _isAdBlockActive);
      
      notifyListeners();
    } catch (e) {
      debugPrint("VPN Toggle Error: $e");
      rethrow;
    }
  }

  // --- Scanner Actions ---

  Future<void> performScan() async {
    if (_isScanning) return;
    _isScanning = true;
    notifyListeners();

    try {
      final String? response = await _platform.invokeMethod('scan');
      if (response == null) return;

      final prefs = await SharedPreferences.getInstance();
      final List<String> whitelist = prefs.getStringList('whitelisted_apps') ?? [];

      // EKSEKUSI DI ISOLATE: Tidak menghambat UI Thread
      _lastScanResult = await compute(_processScanData, {
        'response': response,
        'whitelist': whitelist,
      });
      
    } catch (e) {
      debugPrint("Scan Error: $e");
    } finally {
      _isScanning = false;
      notifyListeners();
    }
  }

  // --- Whitelist Actions ---

  Future<void> addToWhitelist(String packageName) async {
    final success = await _platform.invokeMethod('addToWhitelist', {'packageName': packageName});
    if (success == true) {
      if (!_trustedPackages.contains(packageName)) {
        _trustedPackages.add(packageName);
        final prefs = await SharedPreferences.getInstance();
        await prefs.setStringList('whitelisted_apps', _trustedPackages);
        notifyListeners();
        performScan();
      }
    }
  }

  Future<void> removeFromWhitelist(String packageName) async {
    final success = await _platform.invokeMethod('removeFromWhitelist', {'packageName': packageName});
    if (success == true) {
      _trustedPackages.remove(packageName);
      final prefs = await SharedPreferences.getInstance();
      await prefs.setStringList('whitelisted_apps', _trustedPackages);
      notifyListeners();
    }
  }

  void setLogFilter(String filter) {
    _logFilter = filter;
    notifyListeners();
  }

  void clearLogs() {
    _vpnLogs.clear();
    notifyListeners();
  }

  @override
  void dispose() {
    _nativeSub?.cancel();
    super.dispose();
  }
}