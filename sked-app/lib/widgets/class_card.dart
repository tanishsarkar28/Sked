import 'dart:ui';
import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';
import '../models/class_entry.dart';
import '../theme/app_theme.dart';

/// Departures-board class card matching the native Android Sked implementation.
class ClassCard extends StatelessWidget {
  final ClassEntry item;
  final ClassTimingState timingState;
  final VoidCallback? onClick;

  const ClassCard({
    super.key,
    required this.item,
    this.timingState = ClassTimingState.pending,
    this.onClick,
  });

  String get _tagLabel {
    switch (item.type) {
      case 'Lecture':   return 'LEC';
      case 'Practical': return 'PRAC';
      case 'Tutorial':  return 'TUT';
      default:          return item.type.toUpperCase().length > 4
          ? item.type.toUpperCase().substring(0, 4)
          : item.type.toUpperCase();
    }
  }

  Color get _verticalBarColor {
    switch (timingState) {
      case ClassTimingState.onGoing:   return SkedColors.onGoingGreen;
      case ClassTimingState.upcoming:  return SkedColors.upcomingOrange;
      case ClassTimingState.pending:   return SkedColors.pendingIndigo.withOpacity(0.7);
      case ClassTimingState.over:      return SkedColors.overBar;
    }
  }

  Color get _statusBadgeColor {
    switch (timingState) {
      case ClassTimingState.onGoing:   return SkedColors.onGoingGreen;
      case ClassTimingState.upcoming:  return SkedColors.upcomingOrange;
      case ClassTimingState.pending:   return SkedColors.pendingIndigo;
      case ClassTimingState.over:      return SkedColors.overGrey;
    }
  }

  String get _statusText {
    switch (timingState) {
      case ClassTimingState.onGoing:   return 'ON GOING';
      case ClassTimingState.upcoming:  return 'UPCOMING';
      case ClassTimingState.pending:   return 'PENDING';
      case ClassTimingState.over:      return 'OVER';
    }
  }

  @override
  Widget build(BuildContext context) {
    final isOnGoing = timingState == ClassTimingState.onGoing;

    return Container(
      margin: const EdgeInsets.symmetric(vertical: 4),
      decoration: BoxDecoration(
        color: SkedColors.slab,
        borderRadius: BorderRadius.circular(6),
        border: Border.all(
          color: isOnGoing ? SkedColors.onGoingGreen.withOpacity(0.4) : SkedColors.rule,
          width: isOnGoing ? 1.5 : 1,
        ),
      ),
      child: InkWell(
        onTap: onClick,
        borderRadius: BorderRadius.circular(6),
        child: Padding(
          padding: const EdgeInsets.all(14),
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.center,
            children: [
              // Left vertical status bar
              Container(
                width: 3,
                height: 56,
                decoration: BoxDecoration(
                  color: _verticalBarColor,
                  borderRadius: BorderRadius.circular(1.5),
                ),
              ),
              const SizedBox(width: 12),

              // Main content
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    // Top row: Course Code & Tags
                    Row(
                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                      crossAxisAlignment: CrossAxisAlignment.center,
                      children: [
                        Text(
                          item.courseCode,
                          style: GoogleFonts.barlowCondensed(
                            fontSize: 18,
                            fontWeight: FontWeight.w700,
                            color: SkedColors.chalk,
                          ),
                        ),
                        Row(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            // Monochrome type pill
                            Container(
                              padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                              decoration: BoxDecoration(
                                color: Colors.transparent,
                                borderRadius: BorderRadius.circular(3),
                                border: Border.all(color: SkedColors.rule),
                              ),
                              child: Text(
                                _tagLabel,
                                style: GoogleFonts.barlowCondensed(
                                  fontSize: 10,
                                  fontWeight: FontWeight.w700,
                                  color: SkedColors.slate,
                                ),
                              ),
                            ),
                            const SizedBox(width: 6),

                            // Timing status badge
                            Container(
                              padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                              decoration: BoxDecoration(
                                color: _statusBadgeColor.withOpacity(0.12),
                                borderRadius: BorderRadius.circular(3),
                                border: Border.all(color: _statusBadgeColor.withOpacity(0.35)),
                              ),
                              child: Text(
                                _statusText,
                                style: GoogleFonts.barlowCondensed(
                                  fontSize: 10,
                                  fontWeight: FontWeight.w700,
                                  color: _statusBadgeColor,
                                  letterSpacing: 0.3,
                                ),
                              ),
                            ),
                          ],
                        ),
                      ],
                    ),
                    const SizedBox(height: 5),

                    // Time Range & Room
                    Row(
                      children: [
                        Text(
                          '${item.start} – ${item.end}',
                          style: TextStyle(
                            fontFamily: 'monospace',
                            fontSize: 12,
                            fontWeight: FontWeight.w600,
                            color: isOnGoing ? SkedColors.onGoingGreen : SkedColors.slate,
                            fontFeatures: const [FontFeature.tabularFigures()],
                          ),
                        ),
                        const SizedBox(width: 8),
                        Text(
                          '· ${item.room}',
                          style: const TextStyle(
                            fontFamily: 'monospace',
                            fontSize: 12,
                            color: SkedColors.slate,
                          ),
                        ),
                      ],
                    ),

                    // Teacher & Section
                    if (item.teacher.isNotEmpty) ...[
                      const SizedBox(height: 3),
                      Row(
                        children: [
                          Flexible(
                            child: Text(
                              item.teacher,
                              overflow: TextOverflow.ellipsis,
                              style: const TextStyle(
                                fontSize: 11,
                                color: SkedColors.textMuted,
                              ),
                            ),
                          ),
                          if (item.group.isNotEmpty && item.group != 'All') ...[
                            const SizedBox(width: 6),
                            Text(
                              '· G:${item.group}',
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
          ),
        ),
      ),
    );
  }
}
