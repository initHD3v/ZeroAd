import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart'; // Import for Google Fonts
import 'package:zeroad/home_page.dart'; // Import the new HomePage widget

void main() {
  runApp(const MyApp());
}

class MyApp extends StatelessWidget {
  const MyApp({super.key});

  // This widget is the root of your application.
  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'ZeroAd',
      themeMode: ThemeMode.dark, // Enforce dark mode
      darkTheme: ThemeData( // Define dark theme
        brightness: Brightness.dark,
        primarySwatch: Colors.blueGrey,
        colorScheme: ColorScheme.fromSeed(seedColor: Colors.blueGrey, brightness: Brightness.dark),
        useMaterial3: true,
        textTheme: GoogleFonts.poppinsTextTheme(Theme.of(context).textTheme), // Use Poppins for text theme
      ),
      theme: ThemeData(
        // This will be the light theme, though not used due to themeMode: ThemeMode.dark
        colorScheme: ColorScheme.fromSeed(seedColor: Colors.deepPurple),
        useMaterial3: true,
        textTheme: GoogleFonts.poppinsTextTheme(Theme.of(context).textTheme), // Use Poppins for light theme as well (for completeness)
      ),
      home: const MyHomePage(title: 'ZeroAd Home'), // Updated title
    );
  }
}

