import 'package:flutter/material.dart';

/// Pure Dart Code 128 (Subset B) Barcode Encoder.
///
/// Implements ISO/IEC 15417 specification without external dependencies.
/// Capable of rendering CODE128 barcodes readable by hardware laser turnstile scanners.
class Code128Encoder {
  Code128Encoder._();

  static const List<String> patterns = [
    '212222', '222122', '222221', '121223', '121322', '131222', '122213', '122312', '132212', '221213',
    '221312', '231212', '112232', '122132', '122231', '113222', '123122', '123221', '223211', '221132',
    '221231', '213212', '223112', '312131', '311222', '321122', '321221', '312212', '322112', '322211',
    '212123', '212321', '232121', '111323', '131123', '131321', '112313', '132113', '132311', '211313',
    '231113', '231311', '112133', '112331', '132131', '113123', '113321', '133121', '313121', '211331',
    '231131', '213113', '213311', '213131', '311123', '311321', '331121', '312113', '312311', '332111',
    '314111', '221411', '431111', '111224', '111422', '121124', '121421', '141122', '141221', '112214',
    '112412', '122114', '122411', '142112', '142211', '241211', '221114', '413111', '241112', '134111',
    '111242', '121142', '121241', '114212', '124112', '124211', '411212', '421112', '421211', '212141',
    '214121', '412121', '111143', '111341', '131141', '114113', '114311', '411113', '411311', '113141',
    '114131', '311141', '411131', '211412', '211214', '211232', '2331112'
  ];

  static const int startB = 104;
  static const int stop = 106;
  static const int quietZoneModules = 10;

  static List<bool> encode(String text) {
    final clean = text.trim().isEmpty ? '0000' : text.trim();
    final values = <int>[startB];

    for (var i = 0; i < clean.length; i++) {
      final code = (clean.codeUnitAt(i) - 32).clamp(0, 95);
      values.add(code);
    }

    // Checksum calculation: (Start + sum(pos * code)) % 103
    var checksum = values[0];
    for (var i = 1; i < values.length; i++) {
      checksum += i * values[i];
    }
    values.add(checksum % 103);
    values.add(stop);

    // Expand patterns into modules
    final modules = <bool>[];

    // Left Quiet Zone
    for (var i = 0; i < quietZoneModules; i++) {
      modules.add(false);
    }

    // Code symbols
    for (final patternIndex in values) {
      final pat = (patternIndex >= 0 && patternIndex < patterns.length)
          ? patterns[patternIndex]
          : patterns[0];
      var isBar = true;
      for (var p = 0; p < pat.length; p++) {
        final width = int.tryParse(pat[p]) ?? 1;
        for (var w = 0; w < width; w++) {
          modules.add(isBar);
        }
        isBar = !isBar;
      }
    }

    // Right Quiet Zone
    for (var i = 0; i < quietZoneModules; i++) {
      modules.add(false);
    }

    return modules;
  }
}

class BarcodePainter extends CustomPainter {
  final List<bool> modules;
  final Color barColor;

  BarcodePainter({
    required this.modules,
    this.barColor = Colors.black,
  });

  @override
  void paint(Canvas canvas, Size size) {
    if (modules.isEmpty) return;
    final totalModules = modules.length;
    final moduleWidth = size.width / totalModules;
    final paint = Paint()
      ..color = barColor
      ..style = PaintingStyle.fill;

    for (var i = 0; i < modules.length; i++) {
      if (modules[i]) {
        canvas.drawRect(
          Rect.fromLTWH(i * moduleWidth, 0, moduleWidth + 0.35, size.height),
          paint,
        );
      }
    }
  }

  @override
  bool shouldRepaint(covariant BarcodePainter oldDelegate) {
    return oldDelegate.modules != modules || oldDelegate.barColor != barColor;
  }
}

class Code128BarcodeWidget extends StatelessWidget {
  final String code;
  final double height;
  final Color barColor;
  final Color backgroundColor;

  const Code128BarcodeWidget({
    super.key,
    required this.code,
    this.height = 70.0,
    this.barColor = Colors.black,
    this.backgroundColor = Colors.white,
  });

  @override
  Widget build(BuildContext context) {
    final modules = Code128Encoder.encode(code);

    return Container(
      width: double.infinity,
      height: height,
      color: backgroundColor,
      padding: const EdgeInsets.symmetric(horizontal: 4, vertical: 4),
      child: CustomPaint(
        painter: BarcodePainter(
          modules: modules,
          barColor: barColor,
        ),
      ),
    );
  }
}
