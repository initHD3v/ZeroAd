import 'dart:async';
import 'dart:convert';
import 'package:flutter/foundation.dart'; // For compute

import 'package:flutter/services.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:zeroad/models.dart';
import 'package:zeroad/logic/blocklist_service.dart';

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
  
  // Performance optimized state
  final Map<String, List<String>> _groupedLogs = {};
  final Map<String, String> _appNames = {};
  List<String> _sortedPkgKeys = [];

  // Dynamic Blocklist State
  final BlocklistService _blocklistService = BlocklistService();
  int _blockedDomainsCount = 0;
  int _autoWhitelistedCount = 0;
  DateTime? _lastBlocklistUpdate;
  bool _isUpdatingBlocklist = false;

  // -- Getters --
  bool get isAdBlockActive => _isAdBlockActive;
  List<String> get vpnLogs => _vpnLogs;
  List<String> get trustedPackages => _trustedPackages;
  ScanResultModel? get lastScanResult => _lastScanResult;
  bool get isScanning => _isScanning;
  String get logFilter => _logFilter;
  
  Map<String, List<String>> get groupedLogs => _groupedLogs;
  Map<String, String> get appNames => _appNames;
  List<String> get sortedPkgKeys => _sortedPkgKeys;
  
  int get blockedDomainsCount => _blockedDomainsCount;
  int get autoWhitelistedCount => _autoWhitelistedCount;
  DateTime? get lastBlocklistUpdate => _lastBlocklistUpdate;
  bool get isUpdatingBlocklist => _isUpdatingBlocklist;

  StreamSubscription? _nativeSub;

  final List<String> _pendingLogs = [];
  Timer? _batchTimer;

  /// Inisialisasi awal: Memuat data dari storage dan mendengarkan stream log.
  Future<void> init() async {
    final prefs = await SharedPreferences.getInstance();
    _isAdBlockActive = prefs.getBool('adblock_active') ?? false;
    _trustedPackages = prefs.getStringList('whitelisted_apps') ?? [];
    
    // Load blocklist info
    _blockedDomainsCount = await _blocklistService.getBlocklistCount();
    _autoWhitelistedCount = await _blocklistService.getWhitelistCount();
    final lastUpdate = prefs.getInt('last_blocklist_update');
    if (lastUpdate != null) {
      _lastBlocklistUpdate = DateTime.fromMillisecondsSinceEpoch(lastUpdate);
    }
    
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
          if (parts.length > 5) {
            final currentDomain = parts[1];
            if (currentDomain != lastDomain) {
              newLogs.add(log);
              lastDomain = currentDomain;

              // Pre-process for ActivityTab
              final pkg = parts[4];
              final name = parts[5];
              
              _groupedLogs.putIfAbsent(pkg, () => []).add(log);
              _appNames[pkg] = name;
            }
          }
        }

        _vpnLogs.insertAll(0, newLogs);
        if (_vpnLogs.length > 300) {
           _vpnLogs = _vpnLogs.sublist(0, 300);
           // Prune grouped logs when vpnLogs are pruned
           _rebuildGroupedLogs();
        } else {
           _sortedPkgKeys = _groupedLogs.keys.toList();
        }
        
        _pendingLogs.clear();
        notifyListeners(); 
      }
    });
  }

  /// Rebuilds grouped logs from the main vpnLogs list to keep things in sync after pruning.
  void _rebuildGroupedLogs() {
    _groupedLogs.clear();
    _appNames.clear();
    for (var log in _vpnLogs) {
      final parts = log.split('|');
      if (parts.length > 5) {
        final pkg = parts[4];
        final name = parts[5];
        _groupedLogs.putIfAbsent(pkg, () => []).add(log);
        _appNames[pkg] = name;
      }
    }
    _sortedPkgKeys = _groupedLogs.keys.toList();
  }

  // --- VPN Actions ---

  Future<void> updateDynamicBlocklist() async {
    if (_isUpdatingBlocklist) return;
    _isUpdatingBlocklist = true;
    notifyListeners();

    try {
      final success = await _blocklistService.updateBlocklist();
      if (success) {
        final path = await _blocklistService.getActiveBlocklistPath();
        if (path != null) {
          // Kirim path ke native
          await _platform.invokeMethod('updateBlocklistPath', {'path': path});
          
          // Update local state
          _blockedDomainsCount = await _blocklistService.getBlocklistCount();
          _autoWhitelistedCount = await _blocklistService.getWhitelistCount();
          _lastBlocklistUpdate = DateTime.now();
        }
      }
    } catch (e) {
      debugPrint("Blocklist Update Failed: $e");
    } finally {
      _isUpdatingBlocklist = false;
      notifyListeners();
    }
  }

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
    _groupedLogs.clear();
    _appNames.clear();
    _sortedPkgKeys.clear();
    notifyListeners();
  }

  @override
  void dispose() {
    _nativeSub?.cancel();
    super.dispose();
  }
}