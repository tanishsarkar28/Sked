import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';
import '../models/exam_item.dart';
import '../theme/app_theme.dart';

class ExamScreen extends StatefulWidget {
  final List<ExamItem> exams;
  final VoidCallback onRefresh;

  const ExamScreen({
    super.key,
    required this.exams,
    required this.onRefresh,
  });

  @override
  State<ExamScreen> createState() => _ExamScreenState();
}

class _ExamScreenState extends State<ExamScreen> {
  String _selectedFilter = 'ALL';

  @override
  Widget build(BuildContext context) {
    final upcomingExams = widget.exams.where((e) => e.getStatus() != ExamStatus.completed).toList();
    final completedExams = widget.exams.where((e) => e.getStatus() == ExamStatus.completed).toList();

    ExamItem? nextExam;
    if (upcomingExams.isNotEmpty) {
      final sorted = List<ExamItem>.from(upcomingExams)..sort((a, b) => a.daysUntil().compareTo(b.daysUntil()));
      nextExam = sorted.first;
    }

    final filteredList = _selectedFilter == 'UPCOMING'
        ? upcomingExams
        : _selectedFilter == 'COMPLETED'
            ? completedExams
            : widget.exams;

    if (widget.exams.isEmpty) {
      return _buildEmptyState();
    }

    return ListView(
      padding: const EdgeInsets.fromLTRB(16, 8, 16, 24),
      children: [
        // ── Next Exam Hero Countdown Radar ──────────────────────────────────
        _buildHeroExamRadar(nextExam),

        const SizedBox(height: 16),

        // ── Filter Chips: ALL, UPCOMING, COMPLETED ───────────────────────────
        Row(
          children: [
            _filterChip('ALL', 'ALL', widget.exams.length),
            const SizedBox(width: 8),
            _filterChip('UPCOMING', 'UPCOMING', upcomingExams.length),
            const SizedBox(width: 8),
            _filterChip('COMPLETED', 'OVER', completedExams.length),
          ],
        ),

        const SizedBox(height: 12),

        // ── Filtered Exam Cards List ─────────────────────────────────────────
        if (filteredList.isEmpty)
          Padding(
            padding: const EdgeInsets.symmetric(vertical: 40),
            child: Center(
              child: Text(
                'No $_selectedFilter exams.',
                style: GoogleFonts.barlowCondensed(
                  fontSize: 18,
                  fontWeight: FontWeight.w700,
                  color: SkedColors.slate,
                ),
              ),
            ),
          )
        else
          ...filteredList.map((exam) => _buildExamCard(exam)),
      ],
    );
  }

  Widget _buildEmptyState() {
    return Center(
      child: Container(
        margin: const EdgeInsets.all(24),
        padding: const EdgeInsets.all(28),
        decoration: BoxDecoration(
          color: SkedColors.slab,
          borderRadius: BorderRadius.circular(8),
          border: Border.all(color: SkedColors.rule),
        ),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Container(
              width: 52,
              height: 52,
              decoration: BoxDecoration(
                color: SkedColors.blaze.withOpacity(0.12),
                shape: BoxShape.circle,
              ),
              child: const Icon(Icons.school_outlined, color: SkedColors.blaze, size: 28),
            ),
            const SizedBox(height: 16),
            Text(
              'NO DATESHEET RELEASED YET',
              style: GoogleFonts.barlowCondensed(
                fontSize: 18,
                fontWeight: FontWeight.w700,
                color: SkedColors.chalk,
                letterSpacing: 0.5,
              ),
            ),
            const SizedBox(height: 8),
            const Text(
              'The official Examination Branch has not published the datesheet on UMS for this semester yet.\n\nAs soon as LPU announces your examination schedule or seating plan, tap Re-Sync to fetch it directly into Sked.',
              style: TextStyle(
                fontSize: 12,
                color: SkedColors.slate,
                height: 1.5,
              ),
              textAlign: TextAlign.center,
            ),
            const SizedBox(height: 20),
            ElevatedButton.icon(
              onPressed: widget.onRefresh,
              icon: const Icon(Icons.sync, size: 16),
              label: Text(
                'CHECK / RE-SYNC FROM UMS',
                style: GoogleFonts.barlowCondensed(
                  fontSize: 13,
                  fontWeight: FontWeight.w700,
                  letterSpacing: 0.5,
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildHeroExamRadar(ExamItem? nextExam) {
    return Container(
      decoration: BoxDecoration(
        color: SkedColors.slab,
        borderRadius: BorderRadius.circular(8),
        border: Border.all(
          color: nextExam != null ? SkedColors.blaze.withOpacity(0.5) : SkedColors.rule,
          width: 1.5,
        ),
      ),
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Row(
                children: [
                  Container(
                    width: 8,
                    height: 8,
                    decoration: const BoxDecoration(
                      color: SkedColors.blaze,
                      shape: BoxShape.circle,
                    ),
                  ),
                  const SizedBox(width: 8),
                  Text(
                    'EXAM RADAR',
                    style: GoogleFonts.barlowCondensed(
                      fontSize: 12,
                      fontWeight: FontWeight.w700,
                      color: SkedColors.blaze,
                      letterSpacing: 1.0,
                    ),
                  ),
                ],
              ),
              if (nextExam != null)
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
                  decoration: BoxDecoration(
                    color: SkedColors.blaze.withOpacity(0.15),
                    borderRadius: BorderRadius.circular(4),
                    border: Border.all(color: SkedColors.blaze.withOpacity(0.4)),
                  ),
                  child: Text(
                    nextExam.statusLabel(),
                    style: GoogleFonts.barlowCondensed(
                      fontSize: 11,
                      fontWeight: FontWeight.w700,
                      color: SkedColors.blaze,
                      letterSpacing: 0.5,
                    ),
                  ),
                ),
            ],
          ),
          const SizedBox(height: 12),

          if (nextExam != null) ...[
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Text(
                  nextExam.courseCode,
                  style: GoogleFonts.barlowCondensed(
                    fontSize: 24,
                    fontWeight: FontWeight.w700,
                    color: SkedColors.chalk,
                  ),
                ),
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                  decoration: BoxDecoration(
                    borderRadius: BorderRadius.circular(3),
                    border: Border.all(color: SkedColors.rule),
                  ),
                  child: Text(
                    nextExam.examType,
                    style: GoogleFonts.barlowCondensed(
                      fontSize: 11,
                      fontWeight: FontWeight.w700,
                      color: SkedColors.slate,
                    ),
                  ),
                ),
              ],
            ),
            if (nextExam.courseTitle.isNotEmpty) ...[
              const SizedBox(height: 2),
              Text(
                nextExam.courseTitle,
                style: const TextStyle(
                  fontSize: 12,
                  color: SkedColors.slate,
                ),
              ),
            ],
            const SizedBox(height: 10),
            Row(
              children: [
                const Icon(Icons.calendar_today_outlined, size: 13, color: SkedColors.slate),
                const SizedBox(width: 6),
                Text(
                  nextExam.formattedDate(),
                  style: const TextStyle(
                    fontFamily: 'monospace',
                    fontSize: 12,
                    fontWeight: FontWeight.w600,
                    color: SkedColors.chalk,
                  ),
                ),
                const SizedBox(width: 12),
                const Icon(Icons.access_time_outlined, size: 13, color: SkedColors.slate),
                const SizedBox(width: 6),
                Text(
                  nextExam.timeSlot,
                  style: const TextStyle(
                    fontFamily: 'monospace',
                    fontSize: 12,
                    color: SkedColors.slate,
                  ),
                ),
              ],
            ),
            const SizedBox(height: 8),
            Row(
              children: [
                if (nextExam.reportingTime.isNotEmpty) ...[
                  Container(
                    padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                    decoration: BoxDecoration(
                      color: SkedColors.ink,
                      borderRadius: BorderRadius.circular(3),
                      border: Border.all(color: SkedColors.rule),
                    ),
                    child: Text(
                      nextExam.reportingTime,
                      style: const TextStyle(
                        fontFamily: 'monospace',
                        fontSize: 10,
                        fontWeight: FontWeight.w600,
                        color: SkedColors.blaze,
                      ),
                    ),
                  ),
                  const SizedBox(width: 8),
                ],
                Text(
                  'Room: ${nextExam.room.isNotEmpty ? nextExam.room : "Awaited"}',
                  style: const TextStyle(
                    fontSize: 11,
                    color: SkedColors.slate,
                  ),
                ),
                const SizedBox(width: 8),
                Text(
                  'Seat: ${nextExam.seatNo.isNotEmpty ? nextExam.seatNo : "Awaited"}',
                  style: const TextStyle(
                    fontSize: 11,
                    color: SkedColors.slate,
                  ),
                ),
              ],
            ),
          ] else
            const Text(
              'All scheduled examinations for this semester are completed.',
              style: TextStyle(
                fontSize: 13,
                color: SkedColors.slate,
              ),
            ),
        ],
      ),
    );
  }

  Widget _filterChip(String filterKey, String label, int count) {
    final isSelected = _selectedFilter == filterKey;
    return InkWell(
      onTap: () => setState(() => _selectedFilter = filterKey),
      borderRadius: BorderRadius.circular(4),
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
        decoration: BoxDecoration(
          color: isSelected ? SkedColors.blaze : SkedColors.slab,
          borderRadius: BorderRadius.circular(4),
          border: Border.all(color: isSelected ? SkedColors.blaze : SkedColors.rule),
        ),
        child: Text(
          '$label ($count)',
          style: GoogleFonts.barlowCondensed(
            fontSize: 11,
            fontWeight: FontWeight.w700,
            color: isSelected ? SkedColors.ink : SkedColors.slate,
            letterSpacing: 0.5,
          ),
        ),
      ),
    );
  }

  Widget _buildExamCard(ExamItem exam) {
    final isCompleted = exam.getStatus() == ExamStatus.completed;
    final isToday = exam.getStatus() == ExamStatus.today;
    final barColor = isToday
        ? SkedColors.onGoingGreen
        : isCompleted
            ? SkedColors.overBar
            : SkedColors.upcomingOrange;

    return Container(
      margin: const EdgeInsets.symmetric(vertical: 5),
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: SkedColors.slab,
        borderRadius: BorderRadius.circular(6),
        border: Border.all(
          color: isToday ? SkedColors.onGoingGreen.withOpacity(0.4) : SkedColors.rule,
          width: isToday ? 1.5 : 1,
        ),
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.center,
        children: [
          // Left status bar
          Container(
            width: 3,
            height: 64,
            decoration: BoxDecoration(
              color: barColor,
              borderRadius: BorderRadius.circular(1.5),
            ),
          ),
          const SizedBox(width: 12),

          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                // Top row: Course Code & Status pill
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    Text(
                      exam.courseCode,
                      style: GoogleFonts.barlowCondensed(
                        fontSize: 18,
                        fontWeight: FontWeight.w700,
                        color: SkedColors.chalk,
                      ),
                    ),
                    Row(
                      children: [
                        Container(
                          padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                          decoration: BoxDecoration(
                            color: Colors.transparent,
                            borderRadius: BorderRadius.circular(3),
                            border: Border.all(color: SkedColors.rule),
                          ),
                          child: Text(
                            exam.examType,
                            style: GoogleFonts.barlowCondensed(
                              fontSize: 10,
                              fontWeight: FontWeight.w700,
                              color: SkedColors.slate,
                            ),
                          ),
                        ),
                        const SizedBox(width: 6),
                        Container(
                          padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                          decoration: BoxDecoration(
                            color: barColor.withOpacity(0.12),
                            borderRadius: BorderRadius.circular(3),
                            border: Border.all(color: barColor.withOpacity(0.3)),
                          ),
                          child: Text(
                            exam.statusLabel(),
                            style: GoogleFonts.barlowCondensed(
                              fontSize: 10,
                              fontWeight: FontWeight.w700,
                              color: barColor,
                              letterSpacing: 0.3,
                            ),
                          ),
                        ),
                      ],
                    ),
                  ],
                ),
                if (exam.courseTitle.isNotEmpty) ...[
                  const SizedBox(height: 2),
                  Text(
                    exam.courseTitle,
                    style: const TextStyle(
                      fontSize: 11,
                      color: SkedColors.slate,
                    ),
                  ),
                ],
                const SizedBox(height: 6),

                // Date & Time
                Row(
                  children: [
                    Text(
                      exam.formattedDate(),
                      style: TextStyle(
                        fontFamily: 'monospace',
                        fontSize: 11,
                        fontWeight: FontWeight.w600,
                        color: isToday ? SkedColors.onGoingGreen : SkedColors.chalk,
                      ),
                    ),
                    const SizedBox(width: 8),
                    Text(
                      '· ${exam.timeSlot}',
                      style: const TextStyle(
                        fontFamily: 'monospace',
                        fontSize: 11,
                        color: SkedColors.slate,
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 4),

                // Reporting, Room & Desk
                Row(
                  children: [
                    if (exam.reportingTime.isNotEmpty) ...[
                      Text(
                        exam.reportingTime,
                        style: const TextStyle(
                          fontFamily: 'monospace',
                          fontSize: 10,
                          fontWeight: FontWeight.w600,
                          color: SkedColors.blaze,
                        ),
                      ),
                      const SizedBox(width: 8),
                    ],
                    Text(
                      'Room: ${exam.room.isNotEmpty ? exam.room : "Awaited"}',
                      style: const TextStyle(
                        fontSize: 10,
                        color: SkedColors.textMuted,
                      ),
                    ),
                    const SizedBox(width: 6),
                    Text(
                      'Seat: ${exam.seatNo.isNotEmpty ? exam.seatNo : "Awaited"}',
                      style: const TextStyle(
                        fontSize: 10,
                        color: SkedColors.textMuted,
                      ),
                    ),
                  ],
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}
