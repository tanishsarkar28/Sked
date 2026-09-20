import 'dart:ui';
import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';
import '../models/class_entry.dart';
import '../theme/app_theme.dart';

/// A departures-board style card displaying a single class entry.
class ClassCard extends StatelessWidget {
  final ClassEntry entry;
  final bool isOngoing;
  final bool isNext;
  final bool compact;

  const ClassCard({
    super.key,
    required this.entry,
    this.isOngoing = false,
    this.isNext = false,
    this.compact = false,
  });

  String get _typeTag {
    switch (entry.type) {
      case 'Lecture':   return 'LEC';
      case 'Practical': return 'LAB';
      case 'Tutorial':  return 'TUT';
      default:          return entry.type.toUpperCase().length > 3
          ? entry.type.toUpperCase().substring(0, 3)
          : entry.type.toUpperCase();
    }
  }

  Color get _barColor {
    if (isOngoing) return SkedColors.blaze;
    if (isNext) return SkedColors.blaze.withOpacity(0.5);
    return SkedColors.rule;
  }

  @override
  Widget build(BuildContext context) {
    return Container(
      margin: EdgeInsets.symmetric(vertical: compact ? 3 : 5),
      decoration: BoxDecoration(
        color: SkedColors.slab,
        borderRadius: BorderRadius.circular(6),
        border: Border.all(
          color: isOngoing ? SkedColors.blaze.withOpacity(0.4) : SkedColors.rule,
          width: isOngoing ? 1.5 : 1,
        ),
      ),
      child: Padding(
        padding: EdgeInsets.all(compact ? 10 : 14),
        child: compact ? _buildCompact() : _buildFull(),
      ),
    );
  }

  Widget _buildFull() {
    return Row(
      crossAxisAlignment: CrossAxisAlignment.center,
      children: [
        // Left bar indicator — the only colour signal
        Container(
          width: 3,
          height: 52,
          decoration: BoxDecoration(
            color: _barColor,
            borderRadius: BorderRadius.circular(1.5),
          ),
        ),
        const SizedBox(width: 12),

        // Main content
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              // Course Code & status/type tags
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                crossAxisAlignment: CrossAxisAlignment.center,
                children: [
                  Text(
                    entry.courseCode,
                    style: GoogleFonts.barlowCondensed(
                      fontSize: 18,
                      fontWeight: FontWeight.w700,
                      color: SkedColors.chalk,
                    ),
                  ),
                  Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      if (isOngoing)
                        Padding(
                          padding: const EdgeInsets.only(right: 8),
                          child: Text(
                            'NOW.',
                            style: GoogleFonts.barlowCondensed(
                              fontSize: 12,
                              fontWeight: FontWeight.w700,
                              color: SkedColors.blaze,
                            ),
                          ),
                        )
                      else if (isNext)
                        Padding(
                          padding: const EdgeInsets.only(right: 8),
                          child: Text(
                            'NEXT.',
                            style: GoogleFonts.barlowCondensed(
                              fontSize: 12,
                              fontWeight: FontWeight.w700,
                              color: SkedColors.slate,
                            ),
                          ),
                        ),

                      // Monochrome type pill
                      Container(
                        padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                        decoration: BoxDecoration(
                          color: Colors.transparent,
                          borderRadius: BorderRadius.circular(3),
                          border: Border.all(color: SkedColors.rule),
                        ),
                        child: Text(
                          _typeTag,
                          style: GoogleFonts.barlowCondensed(
                            fontSize: 10,
                            fontWeight: FontWeight.w700,
                            color: SkedColors.slate,
                          ),
                        ),
                      ),
                    ],
                  ),
                ],
              ),
              const SizedBox(height: 4),

              // Time & Room row
              Row(
                children: [
                  Text(
                    '${entry.start} – ${entry.end}',
                    style: TextStyle(
                      fontFamily: 'monospace',
                      fontSize: 12,
                      fontWeight: FontWeight.w500,
                      color: isOngoing ? SkedColors.blaze : SkedColors.slate,
                      fontFeatures: const [FontFeature.tabularFigures()],
                    ),
                  ),
                  const SizedBox(width: 8),
                  Text(
                    '· ${entry.room}',
                    style: const TextStyle(
                      fontFamily: 'monospace',
                      fontSize: 12,
                      color: SkedColors.slate,
                    ),
                  ),
                ],
              ),

              // Teacher & Section
              if (entry.teacher.isNotEmpty) ...[
                const SizedBox(height: 3),
                Row(
                  children: [
                    Text(
                      entry.teacher,
                      style: const TextStyle(
                        fontSize: 11,
                        color: SkedColors.textMuted,
                      ),
                    ),
                    if (entry.group.isNotEmpty && entry.group != 'All') ...[
                      const SizedBox(width: 6),
                      Text(
                        '· G:${entry.group}',
                        style: const TextStyle(
                          fontFamily: 'monospace',
                          fontSize: 10,
                          color: SkedColors.textMuted,
                        ),
                      ),
                    ],
                  ],
                ),
              ],
            ],
          ),
        ),
      ],
    );
  }

  Widget _buildCompact() {
    return Row(
      children: [
        Container(
          width: 3,
          height: 24,
          decoration: BoxDecoration(
            color: _barColor,
            borderRadius: BorderRadius.circular(1.5),
          ),
        ),
        const SizedBox(width: 8),
        Text(
          entry.start,
          style: TextStyle(
            fontFamily: 'monospace',
            fontSize: 12,
            fontWeight: FontWeight.w600,
            color: isOngoing ? SkedColors.blaze : SkedColors.slate,
            fontFeatures: const [FontFeature.tabularFigures()],
          ),
        ),
        const SizedBox(width: 10),
        Expanded(
          child: Text(
            entry.courseCode,
            style: GoogleFonts.barlowCondensed(
              fontSize: 14,
              fontWeight: FontWeight.w700,
              color: SkedColors.chalk,
            ),
          ),
        ),
        Text(
          entry.room,
          style: const TextStyle(
            fontFamily: 'monospace',
            fontSize: 11,
            color: SkedColors.slate,
          ),
        ),
      ],
    );
  }
}
