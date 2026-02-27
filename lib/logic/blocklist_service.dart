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

  // ADGUARD-STYLE: Google Services yang HARUS dilindungi (tidak boleh diblokir)
  static const List<String> _googleAllowlist = [
    // Google Play Services (IAP, Download, Update)
    'play.googleapis.com',
    'play.google.com',
    'android.clients.google.com',
    'clientservices.googleapis.com',
    'content.googleapis.com',
    'download.googleapis.com',
    'storage.googleapis.com',
    'playassetdelivery.googleapis.com',
    'playappasset.googleapis.com',
    
    // Google Authentication & Account
    'accounts.google.com',
    'auth.googleapis.com',
    'oauthaccountmanager.googleapis.com',
    'identitytoolkit.googleapis.com',
    'oauth2.googleapis.com',
    'www.googleapis.com',
    
    // Firebase (Game Save, Remote Config, Analytics)
    'firebase.googleapis.com',
    'firestore.googleapis.com',
    'firebaseinstallations.googleapis.com',
    'firebaseremoteconfig.googleapis.com',
    'firebaseabtesting.googleapis.com',
    'firebaseinappmessaging.googleapis.com',
    'firebaseappdistribution.googleapis.com',
    'firebaseappcheck.googleapis.com',
    'firebasestorage.googleapis.com',
    'firebasedynamiclinks.googleapis.com',
    'firebasefunctions.googleapis.com',
    'firebasemessaging.googleapis.com',
    
    // Google Payments (IAP)
    'payments.google.com',
    'checkout.google.com',
    'billing.google.com',
    'purchase.google.com',
    'playbilling.googleapis.com',
    
    // Google Play Games (Achievements, Cloud Save, Leaderboards)
    'games.googleapis.com',
    'playgames.google.com',
    
    // Google Maps API (Location-based games)
    'maps.googleapis.com',
    'maps.google.com',
    'tile.googleapis.com',
    
    // YouTube API (Video rewards)
    'youtube.googleapis.com',
    'www.youtube-nocookie.com',
    
    // Android System
    'android.googleapis.com',
    'googleapis.l.google.com',
  ];

  static const List<String> _protectedDomains = [
    'google.com', 'googleapis.com', 'gstatic.com', 'googleusercontent.com',
    'android.com', 'play.google.com', 'whatsapp.net', 'whatsapp.com',
    'facebook.com', 'fbcdn.net', 'instagram.com', 'apple.com', 'icloud.com',
    'github.com', 'githubusercontent.com', 'adjust.com', 'adjust.world', 'adjust.in',
    'unity3d.com', 'unity.com', 'epicgames.com', 'unrealengine.com',
    'akamaihd.net', 'cloudfront.net', 'photonengine.io', 'photonengine.com',
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
      String domain = (parts.length >= 2) 
          ? parts[1].toLowerCase()
          : (parts.length == 1 && !parts[0].contains('0.0.0.0')) 
              ? parts[0].toLowerCase() 
              : '';

      if (domain.isNotEmpty) {
        // --- ADGUARD-STYLE PROTECTION ---
        
        // 1. Protect Google Services (allowlist) - PRIORITAS TERTINGGI
        bool isProtectedGoogleService = false;
        for (var allowed in _googleAllowlist) {
          if (domain == allowed || domain.endsWith('.$allowed')) {
            isProtectedGoogleService = true;
            break;
          }
        }
        if (isProtectedGoogleService) continue; // SKIP - jangan masukkan ke blocklist
        
        // 2. Check Google Ads domains - HARUS diblokir
        // Domain ini tidak boleh masuk whitelist meskipun mengandung "google"
        bool isGoogleAds = false;
        final googleAdsPatterns = [
          'googleads.g.doubleclick.net',
          'googleadservices.com',
          'googleadsserving.cn',
          'pagead2.googlesyndication.com',
          'pagead.google.com',
          'adx.google.com',
          'ad.doubleclick.net',
          'adservice.google.com',
          'afs.googlesyndication.com',
          'bid.g.doubleclick.net',
          'cm.g.doubleclick.net',
          'fls.doubleclick.net',
          'tpc.googlesyndication.com',
          's0.2mdn.net',
          's1.2mdn.net',
          'admob.com',
          'admob.google.com',
          'doubleclick.net',
          '2mdn.net',
          'googlesyndication.com',
        ];
        for (var adsPattern in googleAdsPatterns) {
          if (domain == adsPattern || domain.endsWith('.$adsPattern')) {
            isGoogleAds = true;
            break;
          }
        }
        
        if (isGoogleAds) {
          validDomains.add(domain); // Tambahkan ke blocklist
          continue;
        }
        
        // 3. Protect general domains
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
