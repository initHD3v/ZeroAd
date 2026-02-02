import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart'; // Import Shared Preferences
import 'package:zeroad/main.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  group('ZeroAd App Widget Tests', () {
    const MethodChannel channel = MethodChannel('zeroad.security/scanner');

    setUp(() {
      SharedPreferences.setMockInitialValues({}); // Mock SharedPreferences
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger.setMockMethodCallHandler(channel, null);
    });

    testWidgets('App displays initial scanning state and then scan results after initial scan', (WidgetTester tester) async {
      // Mock the MethodChannel response for a clean scan
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger.setMockMethodCallHandler(channel, (MethodCall methodCall) async {
        if (methodCall.method == 'scan') {
          await Future.delayed(const Duration(milliseconds: 100)); // Simulate async delay
          return '{"totalInstalledPackages": 123, "suspiciousPackagesCount": 0, "threats": []}';
        }
        return null;
      });

      await tester.pumpWidget(const MyApp());
      await tester.pump(); // Allow initState to trigger scan and rebuild to loading state

      // Expect main loading indicator
      expect(find.byKey(const Key('mainProgressIndicator')), findsOneWidget);

      await tester.pumpAndSettle(); // Wait for the initial scan to complete and UI to update

      // Verify AppBar title
      expect(find.text('ZeroAd Home'), findsOneWidget);
      // Verify main scan status text updates (No threats) using widget properties
      final Finder mainScanResultFinder = find.byKey(const Key('mainScanResultText'));
      expect(mainScanResultFinder, findsOneWidget);
      expect(tester.widget<Text>(mainScanResultFinder).data, 'No suspicious activity found.');

      expect(find.text('Total Apps'), findsOneWidget);
      expect(find.text('123'), findsOneWidget); // Verify total apps count
      expect(find.text('Suspicious'), findsOneWidget);
      expect(find.text('0'), findsOneWidget); // Verify suspicious apps count

      // Verify dark theme is applied
      final materialApp = tester.widget<MaterialApp>(find.byType(MaterialApp));
      expect(materialApp.themeMode, ThemeMode.dark);
      expect(materialApp.darkTheme?.brightness, Brightness.dark);

      // Tap the scan button again and trigger a frame.
      await tester.tap(find.text('Scan Now'));
      await tester.pump(); // Allow button press to trigger scan and rebuild to loading state
      expect(find.byKey(const Key('mainProgressIndicator')), findsOneWidget); // Should show main loading indicator again
      await tester.pumpAndSettle(); // Wait for the second scan to complete

      // Verify that the scan result text remains the same (as mock returns same)
      expect(tester.widget<Text>(mainScanResultFinder).data, 'No suspicious activity found.');
    });

    testWidgets('App displays error message on PlatformException', (WidgetTester tester) async {
      // Mock the MethodChannel to throw a PlatformException
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger.setMockMethodCallHandler(channel, (MethodCall methodCall) async {
        if (methodCall.method == 'scan') {
          await Future.delayed(const Duration(milliseconds: 100)); // Simulate async delay
          throw PlatformException(code: 'ERROR', message: 'Failed to connect to native service');
        }
        return null;
      });

      await tester.pumpWidget(const MyApp());
      await tester.pump(); // Allow initState to trigger scan and rebuild to loading state

      // Expect main loading indicator
      expect(find.byKey(const Key('mainProgressIndicator')), findsOneWidget);

      await tester.pumpAndSettle(); // Wait for the initial scan to complete

      // Verify that the error message is displayed using widget properties
      final Finder mainScanResultFinder = find.byKey(const Key('mainScanResultText'));
      expect(mainScanResultFinder, findsOneWidget);
      expect(tester.widget<Text>(mainScanResultFinder).data, "Failed to get scan result: 'Failed to connect to native service'.");
    });

    testWidgets('App displays detailed threats when present', (WidgetTester tester) async {
      // Mock the MethodChannel response for a scan with threats
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger.setMockMethodCallHandler(channel, (MethodCall methodCall) async {
        if (methodCall.method == 'scan') {
          await Future.delayed(const Duration(milliseconds: 100)); // Simulate async delay
          // Updated JSON format to match the new Threat model structure
          return '{"totalInstalledPackages": 10, "suspiciousPackagesCount": 1, "threats": [{"packageName": "com.malware.app", "appName": "Malware App", "isSystemApp": false, "detectedThreats": [{"type": "PERMISSION_ABUSE", "severity": "HIGH", "description": "Abusing Accessibility Service"}]}]}';
        }
        return null;
      });

      await tester.pumpWidget(const MyApp());
      await tester.pump(); // Allow initState to trigger scan and rebuild to loading state
      expect(find.byKey(const Key('mainProgressIndicator')), findsOneWidget); // Expect main loading indicator
      await tester.pumpAndSettle();

      final Finder mainScanResultFinder = find.byKey(const Key('mainScanResultText'));
      expect(mainScanResultFinder, findsOneWidget);
      expect(tester.widget<Text>(mainScanResultFinder).data, 'Threats detected!');

      expect(find.text('Detected Threats:'), findsOneWidget);
      expect(find.text('Malware App'), findsOneWidget);
      expect(find.text('com.malware.app'), findsOneWidget);
      // The display logic changed slightly, checking for "issues detected" text or specific threat details if expanded.
      // Based on _buildThreatItem in home_page.dart, it shows "X issues detected."
      expect(find.text('1 issues detected.'), findsOneWidget);
    });
  });
}
