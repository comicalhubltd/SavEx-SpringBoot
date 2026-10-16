import 'package:flutter/material.dart';

class AppTheme {
  static const _primaryGreen  = Color(0xFF00A86B);   // SavEx brand green
  static const _secondaryGold = Color(0xFFF5A623);   // payout/reward gold
  static const _darkBg        = Color(0xFF0F1923);
  static const _cardDark      = Color(0xFF1A2535);

  static ThemeData get light => ThemeData(
    useMaterial3: true,
    colorScheme: ColorScheme.fromSeed(
      seedColor: _primaryGreen,
      brightness: Brightness.light,
    ),
    fontFamily: 'Poppins',
    appBarTheme: const AppBarTheme(centerTitle: true, elevation: 0),
    elevatedButtonTheme: ElevatedButtonThemeData(
      style: ElevatedButton.styleFrom(
        backgroundColor: _primaryGreen,
        foregroundColor: Colors.white,
        minimumSize: const Size.fromHeight(52),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
        textStyle: const TextStyle(fontWeight: FontWeight.w600, fontSize: 16),
      ),
    ),
    inputDecorationTheme: InputDecorationTheme(
      filled: true,
      border: OutlineInputBorder(
        borderRadius: BorderRadius.circular(12),
        borderSide: BorderSide.none,
      ),
      contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
    ),
  );

  static ThemeData get dark => ThemeData(
    useMaterial3: true,
    colorScheme: ColorScheme.fromSeed(
      seedColor: _primaryGreen,
      brightness: Brightness.dark,
      surface: _cardDark,
    ),
    scaffoldBackgroundColor: _darkBg,
    fontFamily: 'Poppins',
    appBarTheme: const AppBarTheme(
      centerTitle: true,
      elevation: 0,
      backgroundColor: _darkBg,
    ),
  );

  static const TextStyle nairaAmount = TextStyle(
    fontSize: 28,
    fontWeight: FontWeight.w700,
    color: _primaryGreen,
    letterSpacing: -0.5,
  );

  static const TextStyle payoutHighlight = TextStyle(
    fontSize: 22,
    fontWeight: FontWeight.w600,
    color: _secondaryGold,
  );
}
