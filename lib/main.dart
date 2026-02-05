import 'package:flutter/material.dart';
import 'package:zeroad/home_page.dart'; 

void main() {
  runApp(const MyApp());
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
      theme: ThemeData(
        useMaterial3: true,
        brightness: Brightness.light,
        colorSchemeSeed: seedColor,
        // Using default system font to stop typeface log spam
      ),
      darkTheme: ThemeData(
        useMaterial3: true,
        brightness: Brightness.dark,
        colorSchemeSeed: seedColor,
      ),
      home: const MyHomePage(title: 'ZeroAd'),
    );
  }
}

