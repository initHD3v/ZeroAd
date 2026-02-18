import 'dart:convert';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:zeroad/models.dart';
import 'package:zeroad/data/threat_database.dart';
import 'package:zeroad/l10n.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  const MethodChannel channel = MethodChannel('zeroad.security/scanner');

  setUp(() {
    SharedPreferences.setMockInitialValues({});
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channel, (MethodCall methodCall) async {
      // Default: semua method return null agar tidak crash
      if (methodCall.method == 'requestNotificationPermission') return true;
      return null;
    });
  });

  // =============================================
  // GROUP 1: Model Serialization Tests
  // =============================================
  group('Model Serialization', () {
    test('Threat.fromJson parses correctly', () {
      final json = {
        'type': 'AD_SDK',
        'severity': 'MEDIUM',
        'code': 'AD_LIB_DETECTED',
        'description': 'Embedded Ad Framework: AdMob',
      };
      final threat = Threat.fromJson(json);

      expect(threat.type, 'AD_SDK');
      expect(threat.severity, 'MEDIUM');
      expect(threat.code, 'AD_LIB_DETECTED');
      expect(threat.description, 'Embedded Ad Framework: AdMob');
    });

    test('Threat.toJson roundtrip works', () {
      final original = Threat(
        type: 'BEHAVIORAL',
        severity: 'HIGH',
        code: 'BOOT_OVERLAY',
        description: 'App starts at boot.',
      );
      final json = original.toJson();
      final restored = Threat.fromJson(json);

      expect(restored.type, original.type);
      expect(restored.severity, original.severity);
      expect(restored.code, original.code);
      expect(restored.description, original.description);
    });

    test('AppThreatInfo.fromJson parses correctly with threats', () {
      final json = {
        'packageName': 'com.test.app',
        'appName': 'Test App',
        'isSystemApp': false,
        'detectedThreats': [
          {
            'type': 'SYSTEM_CONTROL',
            'severity': 'HIGH',
            'code': 'STEALTH_INSTALLER',
            'description': 'Can install apps.',
          }
        ],
        'zeroScore': 30,
      };
      final info = AppThreatInfo.fromJson(json);

      expect(info.packageName, 'com.test.app');
      expect(info.appName, 'Test App');
      expect(info.isSystemApp, false);
      expect(info.detectedThreats.length, 1);
      expect(info.detectedThreats.first.code, 'STEALTH_INSTALLER');
      expect(info.zeroScore, 30);
    });

    test('AppThreatInfo.fromJson uses default zeroScore of 0', () {
      final json = {
        'packageName': 'com.safe.app',
        'appName': 'Safe App',
        'isSystemApp': true,
        'detectedThreats': [],
      };
      final info = AppThreatInfo.fromJson(json);
      expect(info.zeroScore, 0);
    });

    test('ScanResultModel.fromJson parses full scan result', () {
      final jsonStr = '''
      {
        "totalInstalledPackages": 150,
        "suspiciousPackagesCount": 2,
        "threats": [
          {
            "packageName": "com.bad.app",
            "appName": "Bad App",
            "isSystemApp": false,
            "detectedThreats": [
              {"type": "BEHAVIORAL", "severity": "HIGH", "code": "BOOT_OVERLAY", "description": "Overlay at boot."}
            ],
            "zeroScore": 30
          },
          {
            "packageName": "com.ad.app",
            "appName": "Ad App",
            "isSystemApp": false,
            "detectedThreats": [
              {"type": "AD_SDK", "severity": "MEDIUM", "code": "AD_LIB_DETECTED", "description": "AdMob detected."}
            ],
            "zeroScore": 15
          }
        ]
      }
      ''';
      final json = jsonDecode(jsonStr) as Map<String, dynamic>;
      final result = ScanResultModel.fromJson(json);

      expect(result.totalInstalledPackages, 150);
      expect(result.suspiciousPackagesCount, 2);
      expect(result.threats.length, 2);
      expect(result.threats[0].appName, 'Bad App');
      expect(result.threats[1].zeroScore, 15);
    });

    test('ScanResultModel.toJson roundtrip works', () {
      final original = ScanResultModel(
        totalInstalledPackages: 50,
        suspiciousPackagesCount: 0,
        threats: [],
      );
      final json = original.toJson();
      final restored = ScanResultModel.fromJson(json);

      expect(restored.totalInstalledPackages, original.totalInstalledPackages);
      expect(restored.suspiciousPackagesCount, original.suspiciousPackagesCount);
      expect(restored.threats.length, 0);
    });
  });

  // =============================================
  // GROUP 2: ThreatDatabase Tests
  // =============================================
  group('ThreatDatabase', () {
    test('returns correct Indonesian info for known code', () {
      final info = ThreatDatabase.getRiskInfo('STEALTH_INSTALLER', 'id');
      expect(info['title'], 'Pemasang Aplikasi Gelap');
      expect(info.containsKey('risk'), true);
      expect(info.containsKey('impact'), true);
      expect(info.containsKey('recommendation'), true);
    });

    test('returns correct English info for known code', () {
      final info = ThreatDatabase.getRiskInfo('BOOT_OVERLAY', 'en');
      expect(info['title'], 'Intrusive Popups');
    });

    test('returns fallback for unknown code', () {
      final info = ThreatDatabase.getRiskInfo('NONEXISTENT_CODE', 'en');
      expect(info['title'], 'Suspicious Activity');
    });

    test('returns fallback for unknown code in Indonesian', () {
      final info = ThreatDatabase.getRiskInfo('NONEXISTENT_CODE', 'id');
      expect(info['title'], 'Aktivitas Mencurigakan');
    });

    test('all supported codes return valid data for both languages', () {
      for (final code in ThreatDatabase.supportedCodes) {
        if (code == 'UNKNOWN') continue; // Skip fallback entry
        
        final infoId = ThreatDatabase.getRiskInfo(code, 'id');
        final infoEn = ThreatDatabase.getRiskInfo(code, 'en');

        expect(infoId['title'], isNotEmpty, reason: '$code ID title is empty');
        expect(infoId['risk'], isNotEmpty, reason: '$code ID risk is empty');
        expect(infoId['impact'], isNotEmpty, reason: '$code ID impact is empty');
        expect(infoId['recommendation'], isNotEmpty, reason: '$code ID recommendation is empty');

        expect(infoEn['title'], isNotEmpty, reason: '$code EN title is empty');
        expect(infoEn['risk'], isNotEmpty, reason: '$code EN risk is empty');
        expect(infoEn['impact'], isNotEmpty, reason: '$code EN impact is empty');
        expect(infoEn['recommendation'], isNotEmpty, reason: '$code EN recommendation is empty');
      }
    });

    test('supportedCodes returns non-empty list', () {
      expect(ThreatDatabase.supportedCodes, isNotEmpty);
      expect(ThreatDatabase.supportedCodes, contains('STEALTH_INSTALLER'));
      expect(ThreatDatabase.supportedCodes, contains('BOOT_OVERLAY'));
      expect(ThreatDatabase.supportedCodes, contains('AD_LIB_DETECTED'));
    });
  });

  // =============================================
  // GROUP 3: Localization Tests
  // =============================================
  group('AppLocalizations', () {
    test('Indonesian locale returns correct strings', () {
      final l10n = AppLocalizations('id');
      expect(l10n.isId, true);
      expect(l10n.scannerTab, 'Pemindai');
      expect(l10n.shieldTab, 'Perisai');
      expect(l10n.activityTab, 'Aktivitas');
      expect(l10n.protectionActive, 'Perlindungan Aktif');
    });

    test('English locale returns correct strings', () {
      final l10n = AppLocalizations('en');
      expect(l10n.isId, false);
      expect(l10n.scannerTab, 'Scanner');
      expect(l10n.shieldTab, 'Shield');
      expect(l10n.activityTab, 'Activity');
      expect(l10n.protectionActive, 'Protection Active');
    });
  });
}
