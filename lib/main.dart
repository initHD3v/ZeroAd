import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:sentry_flutter/sentry_flutter.dart';
import 'package:zeroad/home_page.dart'; 
import 'package:zeroad/logic/security_provider.dart';

Future<void> main() async {
  await SentryFlutter.init(
    (options) {
      options.dsn = 'https://examplePublicKey@o0.ingest.sentry.io/0';
      options.tracesSampleRate = 0.2;
    },
    appRunner: () => runApp(
      ChangeNotifierProvider(
        create: (_) => SecurityProvider()..init(),
        child: const MyApp(),
      ),
    ),
  );
}

class MyApp extends StatelessWidget {
  const MyApp({super.key});

  // This widget is the root of your application.
  @override
  Widget build(BuildContext context) {
    const seedColor = Color(0xFF6750A4);

    return MaterialApp(
      title: 'ZeroAd',
      themeMode: ThemeMode.system,
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        useMaterial3: true,
        brightness: Brightness.light,
        colorSchemeSeed: seedColor,
        fontFamily: '', // Forced empty to bypass OEM font injection
        typography: Typography.material2021(), // Explicit typography set
      ),
      darkTheme: ThemeData(
        useMaterial3: true,
        brightness: Brightness.dark,
        colorSchemeSeed: seedColor,
        fontFamily: '', // Forced empty to bypass OEM font injection
        typography: Typography.material2021(),
      ),
      home: const MyHomePage(title: 'ZeroAd'),
    );
  }
}

