import 'dart:io';
import 'package:http/http.dart' as http;
import 'package:path_provider/path_provider.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:flutter/foundation.dart';

/// [BlocklistService] mengelola siklus hidup daftar blokir dinamis.
class BlocklistService {
  static const String _defaultSourceUrl = 'https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts';
  
  static const List<String> _globalWhitelists = [
    'https://raw.githubusercontent.com/anudeepND/whitelist/master/domains/whitelist.txt',
    'https://raw.githubusercontent.com/AdguardTeam/AdguardFilters/master/WhitelistFilter/sections/exclude.txt',
    'https://raw.githubusercontent.com/AdguardTeam/HttpsExclusionList/master/mobile/android.txt'
  ];
  
  static const String _fileName = 'zeroad_dynamic_hosts.txt';
  static const String _whitelistFileName = 'zeroad_auto_whitelist.txt';

  static const List<String> _protectedDomains = [
    'google.com', 'googleapis.com', 'gstatic.com', 'googleusercontent.com',
    'android.com', 'play.google.com', 'whatsapp.net', 'whatsapp.com',
    'facebook.com', 'fbcdn.net', 'instagram.com', 'apple.com', 'icloud.com',
    'github.com', 'githubusercontent.com', 'adjust.com', 'adjust.world', 'adjust.in',
  ];

  Future<bool> updateBlocklist() async {
    try {
      debugPrint('ZeroAd_Intelligence: Memulai sinkronisasi global...');

      // 1. Ambil Daftar Blokir Utama
      debugPrint('ZeroAd_Intelligence: Mengunduh Blocklist utama...');
      final blockResponse = await http.get(Uri.parse(_defaultSourceUrl)).timeout(const Duration(seconds: 30));
      
      // 2. Agregasi Intelijen Dunia (Multiple Sources)
      Set<String> aggregatedWhitelists = {};
      for (var url in _globalWhitelists) {
        try {
          debugPrint('ZeroAd_Intelligence: Mengunduh Whitelist dari ${url.split('/').last}...');
          final res = await http.get(Uri.parse(url)).timeout(const Duration(seconds: 15));
          if (res.statusCode == 200) {
            // Gunakan compute untuk sanitize whitelist agar tidak lag
            final cleaned = await compute(_simpleSanitize, res.body);
            aggregatedWhitelists.addAll(cleaned.split('\n'));
          }
        } catch (e) {
          debugPrint('ZeroAd_Intelligence: Gagal mengambil whitelist dari $url: $e');
        }
      }

      if (blockResponse.statusCode == 200) {
        debugPrint('ZeroAd_Intelligence: Memproses dan menyaring data (Scrubbing)...');
        
        // Proses Blokir di Isolate
        final cleanedBlock = await compute(_scrubAndSanitize, blockResponse.body);
        final blockFile = await _getLocalFile();
        await blockFile.writeAsString(cleanedBlock);

        // Simpan Hasil Agregasi Whitelist
        if (aggregatedWhitelists.isNotEmpty) {
          debugPrint('ZeroAd_Intelligence: Menyimpan ${aggregatedWhitelists.length} domain intelijen...');
          final whiteFile = await _getWhitelistFile();
          await whiteFile.writeAsString(aggregatedWhitelists.join('\n'));
          
          final prefs = await SharedPreferences.getInstance();
          await prefs.setString('auto_whitelist_path', whiteFile.path);
        }
        
        final prefs = await SharedPreferences.getInstance();
        await prefs.setInt('last_blocklist_update', DateTime.now().millisecondsSinceEpoch);
        await prefs.setString('last_blocklist_path', blockFile.path);
        
        debugPrint('ZeroAd_Intelligence: Sinkronisasi selesai dengan sukses.');
        return true;
      }
      return false;
    } catch (e) {
      debugPrint('ZeroAd_Intelligence: Error fatal saat update: $e');
      return false;
    }
  }

  static String _simpleSanitize(String raw) {
    return raw.split(RegExp(r'\r?\n'))
        .map((l) => l.trim())
        .where((l) => l.isNotEmpty && !l.startsWith('#'))
        .join('\n');
  }

  static String _scrubAndSanitize(String rawData) {
    final lines = rawData.split(RegExp(r'\r?\n'));
    final Set<String> validDomains = {};

    for (var line in lines) {
      final trimmed = line.trim();
      if (trimmed.isEmpty || trimmed.startsWith('#')) continue;
      
      final parts = trimmed.split(RegExp(r'\s+'));
      String domain = (parts.length >= 2) ? parts[1].toLowerCase() : 
                      (parts.length == 1 && !parts[0].contains('0.0.0.0')) ? parts[0].toLowerCase() : '';

      if (domain.isNotEmpty) {
        bool isProtected = false;
        for (var protected in _protectedDomains) {
          if (domain == protected || domain.endsWith('.$protected')) {
            isProtected = true;
            break;
          }
        }
        if (!isProtected) validDomains.add(domain);
      }
    }
    return validDomains.join('\n');
  }

  Future<File> _getLocalFile() async => File('${(await getApplicationDocumentsDirectory()).path}/$_fileName');
  Future<File> _getWhitelistFile() async => File('${(await getApplicationDocumentsDirectory()).path}/$_whitelistFileName');

  Future<String?> getActiveBlocklistPath() async {
    final file = await _getLocalFile();
    return await file.exists() ? file.path : null;
  }
  
  Future<int> getBlocklistCount() async {
    try {
      final file = await _getLocalFile();
      if (await file.exists()) {
        final content = await file.readAsString();
        return content.split('\n').where((l) => l.isNotEmpty).length;
      }
    } catch (_) {}
    return 0;
  }

  Future<int> getWhitelistCount() async {
    try {
      final file = await _getWhitelistFile();
      if (await file.exists()) {
        final content = await file.readAsString();
        return content.split('\n').where((l) => l.isNotEmpty).length;
      }
    } catch (_) {}
    return 0;
  }
}
