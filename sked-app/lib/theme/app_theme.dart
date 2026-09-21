import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';

// ── Sked Palette — logo-anchored: orange on black, one accent, no scatter ───
class SkedColors {
  SkedColors._();

  static const ink   = Color(0xFF0A0A0A); // True near-black — logo canvas
  static const slab  = Color(0xFF141414); // Card/surface — barely visible lift
  static const rule  = Color(0xFF252525); // Dividers, borders — mechanical hairline
  static const chalk = Color(0xFFE8E6E3); // Primary text — warm off-white
  static const slate = Color(0xFF7A7774); // Secondary/metadata — warm mid-grey
  static const blaze = Color(0xFFFF6B1A); // Logo orange — THE ONLY chromatic colour

  // Aliases & Semantics
  static const background    = ink;
  static const surface       = slab;
  static const card          = slab;
  static const border        = rule;

  static const accent        = blaze;
  static const textPrimary   = chalk;
  static const textSecondary = slate;
  static const textMuted     = Color(0xFF5A5856);

  // Type distinctions stay monochrome
  static const lecture       = slate;
  static const practical     = slate;
  static const tutorial      = slate;

  static const success       = blaze;
  static const warning       = blaze;
  static const error         = Color(0xFFFF4D4D);
  static const dangerRed     = Color(0xFFEF4444);

  // Timing State Colors (matching Android)
  static const onGoingGreen   = Color(0xFF10B981);
  static const upcomingOrange = Color(0xFFFF8533);
  static const pendingIndigo  = Color(0xFF818CF8);
  static const overGrey       = Color(0xFF71717A);
  static const overBar        = Color(0xFF383838);
}

// ── Gradients removed in favour of flat mechanical surfaces ──────────────────
class SkedGradients {
  SkedGradients._();

  // Kept for backward-compatibility if referenced, but flat
  static const accentVertical = LinearGradient(
    colors: [SkedColors.blaze, SkedColors.blaze],
  );

  static const cardGlass = LinearGradient(
    colors: [SkedColors.slab, SkedColors.slab],
  );
}

// ── Typography — Barlow Condensed for display, Inter for body ────────────────
class SkedTextStyles {
  SkedTextStyles._();

  static TextStyle get displayLarge => GoogleFonts.barlowCondensed(
    fontSize: 28,
    fontWeight: FontWeight.w700,
    color: SkedColors.chalk,
    letterSpacing: 0.5,
  );

  static TextStyle get headlineMedium => GoogleFonts.barlowCondensed(
    fontSize: 22,
    fontWeight: FontWeight.w700,
    color: SkedColors.chalk,
    letterSpacing: 0.5,
  );

  static TextStyle get titleMedium => GoogleFonts.barlowCondensed(
    fontSize: 18,
    fontWeight: FontWeight.w700,
    color: SkedColors.chalk,
  );

  static TextStyle get bodyMedium => GoogleFonts.inter(
    fontSize: 14,
    fontWeight: FontWeight.w400,
    color: SkedColors.slate,
  );

  static TextStyle get labelSmall => GoogleFonts.barlowCondensed(
    fontSize: 12,
    fontWeight: FontWeight.w700,
    color: SkedColors.slate,
    letterSpacing: 0.5,
  );
}

// ── Main Theme — sharp 6dp cards, 4dp inputs, departures-board aesthetic ──────
ThemeData buildSkedTheme() {
  return ThemeData(
    useMaterial3: true,
    brightness: Brightness.dark,
    scaffoldBackgroundColor: SkedColors.ink,
    colorScheme: const ColorScheme.dark(
      primary: SkedColors.blaze,
      secondary: SkedColors.slate,
      surface: SkedColors.slab,
      background: SkedColors.ink,
      onPrimary: SkedColors.ink,
      onSurface: SkedColors.chalk,
    ),
    fontFamily: GoogleFonts.inter().fontFamily,
    appBarTheme: AppBarTheme(
      backgroundColor: SkedColors.ink,
      elevation: 0,
      centerTitle: false,
      titleTextStyle: SkedTextStyles.headlineMedium,
      iconTheme: const IconThemeData(color: SkedColors.chalk),
    ),
    navigationBarTheme: NavigationBarThemeData(
      backgroundColor: SkedColors.slab,
      surfaceTintColor: Colors.transparent,
      indicatorColor: SkedColors.rule,
      labelTextStyle: MaterialStateProperty.resolveWith((states) {
        if (states.contains(MaterialState.selected)) {
          return GoogleFonts.barlowCondensed(
            fontSize: 13,
            fontWeight: FontWeight.w700,
            color: SkedColors.blaze,
          );
        }
        return GoogleFonts.barlowCondensed(
          fontSize: 13,
          fontWeight: FontWeight.w600,
          color: SkedColors.slate,
        );
      }),
      iconTheme: MaterialStateProperty.resolveWith((states) {
        if (states.contains(MaterialState.selected)) {
          return const IconThemeData(color: SkedColors.blaze);
        }
        return const IconThemeData(color: SkedColors.slate);
      }),
    ),
    inputDecorationTheme: InputDecorationTheme(
      filled: true,
      fillColor: SkedColors.slab,
      border: OutlineInputBorder(
        borderRadius: BorderRadius.circular(4),
        borderSide: const BorderSide(color: SkedColors.rule),
      ),
      enabledBorder: OutlineInputBorder(
        borderRadius: BorderRadius.circular(4),
        borderSide: const BorderSide(color: SkedColors.rule),
      ),
      focusedBorder: OutlineInputBorder(
        borderRadius: BorderRadius.circular(4),
        borderSide: const BorderSide(color: SkedColors.blaze, width: 1.5),
      ),
      labelStyle: TextStyle(color: SkedColors.slate, fontFamily: GoogleFonts.inter().fontFamily),
      hintStyle: const TextStyle(color: SkedColors.textMuted),
    ),
    elevatedButtonTheme: ElevatedButtonThemeData(
      style: ElevatedButton.styleFrom(
        backgroundColor: SkedColors.blaze,
        foregroundColor: SkedColors.ink,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(4)),
        padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 14),
        textStyle: GoogleFonts.barlowCondensed(
          fontSize: 16,
          fontWeight: FontWeight.w700,
          letterSpacing: 0.5,
        ),
      ),
    ),
    dividerTheme: const DividerThemeData(color: SkedColors.rule, thickness: 1),
  );
}
