import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';
import '../models/class_entry.dart';
import '../models/gpa_models.dart';
import '../theme/app_theme.dart';

class GpaScreen extends StatefulWidget {
  final List<ClassEntry> timetable;
  final VoidCallback? onSyncUms;

  const GpaScreen({super.key, required this.timetable, this.onSyncUms});

  @override
  State<GpaScreen> createState() => _GpaScreenState();
}

class _GpaScreenState extends State<GpaScreen> {
  GpaState _state = const GpaState();
  bool _isLoading = true;
  bool _globalMarksMode = false;
  bool _showTargetSimulator = false;

  @override
  void initState() {
    super.initState();
    _loadData();
  }

  Future<void> _loadData() async {
    final loaded = await GpaStorage.load(widget.timetable);
    if (mounted) {
      setState(() {
        _state = loaded;
        _isLoading = false;
      });
    }
  }

  Future<void> _updateState(GpaState newState) async {
    setState(() => _state = newState);
    await GpaStorage.save(newState);
  }

  Color _getGradeColor(String letter) {
    switch (letter.toUpperCase()) {
      case 'O':
        return const Color(0xFF00E676);
      case 'A+':
        return const Color(0xFF00E5FF);
      case 'A':
        return const Color(0xFF40C4FF);
      case 'B+':
        return const Color(0xFF7C4DFF);
      case 'B':
        return const Color(0xFFB388FF);
      case 'C':
        return const Color(0xFFFFB74D);
      case 'D':
      case 'P':
        return const Color(0xFFFF9100);
      default:
        return const Color(0xFFFF5252);
    }
  }

  @override
  Widget build(BuildContext context) {
    if (_isLoading) {
      return const Center(
        child: CircularProgressIndicator(color: SkedColors.blaze),
      );
    }

    final activeTerm = _state.activeTerm;

    return ListView(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
      children: [
        // ── TOP COMBINED CGPA BANNER ─────────────────────────────────────────
        _buildCgpaBanner(),
        if (_showTargetSimulator) ...[
          const SizedBox(height: 10),
          _buildTargetSimulator(),
        ],
        const SizedBox(height: 12),

        // ── TERM SELECTOR STRIP ──────────────────────────────────────────────
        _buildTermStrip(),
        const SizedBox(height: 12),

        if (activeTerm != null) ...[
          // ── ACTIVE TERM HEADER (TGPA) ──────────────────────────────────────
          _buildActiveTermCard(activeTerm),
          const SizedBox(height: 14),

          // ── SUBJECTS HEADER ────────────────────────────────────────────────
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text(
                '${activeTerm.name.toUpperCase()} COURSES (${activeTerm.subjects.length})',
                style: GoogleFonts.barlowCondensed(
                  fontSize: 13,
                  fontWeight: FontWeight.w700,
                  color: SkedColors.slate,
                  letterSpacing: 0.5,
                ),
              ),
              InkWell(
                onTap: () => _showAddCustomDialog(activeTerm),
                child: Row(
                  children: [
                    const Icon(Icons.add, size: 14, color: SkedColors.blaze),
                    const SizedBox(width: 4),
                    Text(
                      'ADD COURSE',
                      style: GoogleFonts.barlowCondensed(
                        fontSize: 11,
                        fontWeight: FontWeight.w700,
                        color: SkedColors.blaze,
                      ),
                    ),
                  ],
                ),
              ),
            ],
          ),
          const SizedBox(height: 8),

          if (activeTerm.subjects.isEmpty)
            Container(
              padding: const EdgeInsets.all(20),
              decoration: BoxDecoration(
                color: SkedColors.slab,
                borderRadius: BorderRadius.circular(6),
                border: Border.all(color: SkedColors.rule),
              ),
              child: Column(
                children: [
                  Text(
                    'No subjects added for ${activeTerm.name}',
                    style: GoogleFonts.barlowCondensed(fontSize: 14, color: SkedColors.slate),
                  ),
                  const SizedBox(height: 10),
                  OutlinedButton(
                    onPressed: () => _showAddCustomDialog(activeTerm),
                    style: OutlinedButton.styleFrom(
                      side: const BorderSide(color: SkedColors.blaze),
                      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(4)),
                    ),
                    child: Text('ADD COURSE',
                        style: GoogleFonts.barlowCondensed(color: SkedColors.blaze)),
                  ),
                ],
              ),
            )
          else
            ...activeTerm.subjects.map((s) => Padding(
                  padding: const EdgeInsets.only(bottom: 10),
                  child: _buildSubjectCard(s, activeTerm),
                )),
        ],
        const SizedBox(height: 40),
      ],
    );
  }

  Widget _buildCgpaBanner() {
    final cgpa = _state.displayCgpa;

    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: SkedColors.slab,
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: SkedColors.rule),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text(
                _state.officialCgpa != null ? 'COMBINED CUMULATIVE SCORE • UMS' : 'COMBINED CUMULATIVE SCORE',
                style: GoogleFonts.barlowCondensed(
                  fontSize: 11,
                  fontWeight: FontWeight.w700,
                  color: _state.officialCgpa != null ? SkedColors.blaze : SkedColors.slate,
                  letterSpacing: 0.5,
                ),
              ),
              Row(
                children: [
                  if (widget.onSyncUms != null) ...[
                    InkWell(
                      onTap: widget.onSyncUms,
                      child: Row(
                        children: [
                          const Icon(Icons.sync, size: 12, color: SkedColors.slate),
                          const SizedBox(width: 3),
                          Text(
                            'SYNC UMS',
                            style: GoogleFonts.barlowCondensed(
                              fontSize: 11,
                              fontWeight: FontWeight.w700,
                              color: SkedColors.slate,
                            ),
                          ),
                        ],
                      ),
                    ),
                    const SizedBox(width: 8),
                  ],
                  InkWell(
                    onTap: () => setState(() => _showTargetSimulator = !_showTargetSimulator),
                    child: Row(
                      children: [
                        Icon(Icons.track_changes,
                            size: 13, color: _showTargetSimulator ? SkedColors.blaze : SkedColors.slate),
                        const SizedBox(width: 4),
                        Text(
                          _showTargetSimulator ? 'HIDE GOAL' : 'TARGET GOAL',
                          style: GoogleFonts.barlowCondensed(
                            fontSize: 11,
                            fontWeight: FontWeight.w700,
                            color: _showTargetSimulator ? SkedColors.blaze : SkedColors.slate,
                          ),
                        ),
                      ],
                    ),
                  ),
                ],
              ),
            ],
          ),
          const SizedBox(height: 8),
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            crossAxisAlignment: CrossAxisAlignment.end,
            children: [
              Row(
                crossAxisAlignment: CrossAxisAlignment.baseline,
                textBaseline: TextBaseline.alphabetic,
                children: [
                  Text(
                    cgpa != null ? cgpa.toStringAsFixed(2) : '—',
                    style: const TextStyle(
                      fontSize: 42,
                      fontFamily: 'monospace',
                      fontWeight: FontWeight.bold,
                      color: SkedColors.chalk,
                      height: 1.0,
                    ),
                  ),
                  const SizedBox(width: 8),
                  Text(
                    'CGPA',
                    style: GoogleFonts.barlowCondensed(
                      fontSize: 16,
                      fontWeight: FontWeight.w700,
                      color: const Color(0xFF00E5FF),
                    ),
                  ),
                ],
              ),
              Column(
                crossAxisAlignment: CrossAxisAlignment.end,
                children: [
                  Text(
                    '${_state.terms.length} Terms Combined',
                    style: GoogleFonts.barlowCondensed(
                      fontSize: 12,
                      fontWeight: FontWeight.w600,
                      color: SkedColors.chalk,
                    ),
                  ),
                  Text(
                    '${_state.totalCompletedCredits.toStringAsFixed(1)} Graded Credits',
                    style: GoogleFonts.barlowCondensed(
                      fontSize: 11,
                      color: SkedColors.slate,
                    ),
                  ),
                ],
              ),
            ],
          ),
        ],
      ),
    );
  }

  Widget _buildTermStrip() {
    return SingleChildScrollView(
      scrollDirection: Axis.horizontal,
      child: Row(
        children: [
          ..._state.terms.map((term) {
            final isSelected = term.id == _state.activeTermId;
            final tgpaStr =
                term.tgpa != null ? term.tgpa!.toStringAsFixed(2) : '—';

            return InkWell(
              onTap: () => _updateState(_state.copyWith(activeTermId: term.id)),
              child: Container(
                margin: const EdgeInsets.only(right: 8),
                padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 5),
                decoration: BoxDecoration(
                  color: isSelected ? SkedColors.blaze : SkedColors.slab,
                  borderRadius: BorderRadius.circular(4),
                  border: Border.all(color: isSelected ? SkedColors.blaze : SkedColors.rule),
                ),
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Text(
                      term.isCurrent ? '${term.name} ★' : term.name,
                      style: GoogleFonts.barlowCondensed(
                        fontSize: 12,
                        fontWeight: FontWeight.w700,
                        color: isSelected ? SkedColors.ink : SkedColors.chalk,
                        letterSpacing: 0.5,
                      ),
                    ),
                    Text(
                      'TGPA: $tgpaStr',
                      style: TextStyle(
                        fontFamily: 'monospace',
                        fontSize: 10,
                        fontWeight: FontWeight.w600,
                        color: isSelected ? SkedColors.ink.withOpacity(0.8) : SkedColors.slate,
                      ),
                    ),
                  ],
                ),
              ),
            );
          }),
          InkWell(
            onTap: _showAddTermDialog,
            child: Container(
              padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
              decoration: BoxDecoration(
                color: SkedColors.slab,
                borderRadius: BorderRadius.circular(4),
                border: Border.all(color: SkedColors.rule),
              ),
              child: Row(
                children: [
                  const Icon(Icons.add, size: 14, color: SkedColors.blaze),
                  const SizedBox(width: 4),
                  Text(
                    'NEW TERM',
                    style: GoogleFonts.barlowCondensed(
                      fontSize: 11,
                      fontWeight: FontWeight.w700,
                      color: SkedColors.blaze,
                    ),
                  ),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildActiveTermCard(TermData term) {
    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: SkedColors.slab,
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: SkedColors.rule),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Row(
                children: [
                  Text(
                    term.name.toUpperCase(),
                    style: GoogleFonts.barlowCondensed(
                      fontSize: 14,
                      fontWeight: FontWeight.w700,
                      color: SkedColors.blaze,
                      letterSpacing: 0.5,
                    ),
                  ),
                  if (!term.isCurrent && _state.terms.length > 1) ...[
                    const SizedBox(width: 8),
                    InkWell(
                      onTap: () {
                        final remaining =
                            _state.terms.where((t) => t.id != term.id).toList();
                        final nextActive = remaining.firstWhere(
                            (t) => t.isCurrent,
                            orElse: () => remaining.first);
                        _updateState(_state.copyWith(
                            terms: remaining, activeTermId: nextActive.id));
                      },
                      child: const Icon(Icons.delete_outline, color: Color(0xFFFF5252), size: 14),
                    ),
                  ],
                ],
              ),
              Container(
                decoration: BoxDecoration(
                  color: SkedColors.ink,
                  borderRadius: BorderRadius.circular(4),
                  border: Border.all(color: SkedColors.rule),
                ),
                padding: const EdgeInsets.all(2),
                child: Row(
                  children: [
                    InkWell(
                      onTap: () {
                        setState(() => _globalMarksMode = false);
                        final updated =
                            term.subjects.map((s) => s.copyWith(isMarksMode: false)).toList();
                        _updateTermSubjects(term, updated);
                      },
                      child: Container(
                        padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2.5),
                        decoration: BoxDecoration(
                          color: !_globalMarksMode ? SkedColors.blaze : Colors.transparent,
                          borderRadius: BorderRadius.circular(3),
                        ),
                        child: Text(
                          'GRADES',
                          style: GoogleFonts.barlowCondensed(
                            fontSize: 10,
                            fontWeight: FontWeight.w700,
                            color: !_globalMarksMode ? SkedColors.ink : SkedColors.slate,
                          ),
                        ),
                      ),
                    ),
                    InkWell(
                      onTap: () {
                        setState(() => _globalMarksMode = true);
                        final updated =
                            term.subjects.map((s) => s.copyWith(isMarksMode: true)).toList();
                        _updateTermSubjects(term, updated);
                      },
                      child: Container(
                        padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2.5),
                        decoration: BoxDecoration(
                          color: _globalMarksMode ? SkedColors.blaze : Colors.transparent,
                          borderRadius: BorderRadius.circular(3),
                        ),
                        child: Text(
                          'MARKS',
                          style: GoogleFonts.barlowCondensed(
                            fontSize: 10,
                            fontWeight: FontWeight.w700,
                            color: _globalMarksMode ? SkedColors.ink : SkedColors.slate,
                          ),
                        ),
                      ),
                    ),
                  ],
                ),
              ),
            ],
          ),
          const SizedBox(height: 8),
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            crossAxisAlignment: CrossAxisAlignment.end,
            children: [
              Row(
                crossAxisAlignment: CrossAxisAlignment.baseline,
                textBaseline: TextBaseline.alphabetic,
                children: [
                  Text(
                    term.tgpa != null ? term.tgpa!.toStringAsFixed(2) : '—',
                    style: const TextStyle(
                      fontSize: 36,
                      fontFamily: 'monospace',
                      fontWeight: FontWeight.bold,
                      color: SkedColors.chalk,
                      height: 1.0,
                    ),
                  ),
                  const SizedBox(width: 6),
                  Text(
                    'TGPA',
                    style: GoogleFonts.barlowCondensed(
                      fontSize: 14,
                      fontWeight: FontWeight.w700,
                      color: SkedColors.blaze,
                    ),
                  ),
                ],
              ),
              Column(
                crossAxisAlignment: CrossAxisAlignment.end,
                children: [
                  Text(
                    '${term.totalCredits.toStringAsFixed(1)} Credits',
                    style: GoogleFonts.barlowCondensed(
                      fontSize: 12,
                      fontWeight: FontWeight.w600,
                      color: SkedColors.chalk,
                    ),
                  ),
                  Text(
                    '${term.gradedSubjects.length} of ${term.subjects.length} Graded',
                    style: GoogleFonts.barlowCondensed(
                      fontSize: 11,
                      color: SkedColors.slate,
                    ),
                  ),
                ],
              ),
            ],
          ),
        ],
      ),
    );
  }

  Widget _buildTargetSimulator() {
    final target = _state.targetCgpa ?? 8.50;
    final activeTerm = _state.activeTerm;
    final activeCr = activeTerm?.totalCredits ?? 0.0;
    final totalCr = _state.totalCompletedCredits;
    final otherWeighted = _state.terms
        .where((t) => t.id != activeTerm?.id)
        .fold(0.0, (acc, t) => acc + t.weightedPoints);

    final neededTgpa = activeCr > 0
        ? ((target * totalCr) - otherWeighted) / activeCr
        : target;

    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: SkedColors.slab,
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: SkedColors.blaze.withOpacity(0.5)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Row(
                children: [
                  const Icon(Icons.track_changes, size: 15, color: SkedColors.blaze),
                  const SizedBox(width: 6),
                  Text(
                    'CGPA TARGET SIMULATOR',
                    style: GoogleFonts.barlowCondensed(
                      fontSize: 12,
                      fontWeight: FontWeight.w700,
                      color: SkedColors.blaze,
                    ),
                  ),
                ],
              ),
              Row(
                children: ['8.00', '8.50', '9.00'].map((goal) {
                  final isSel = target.toStringAsFixed(2) == goal;
                  return InkWell(
                    onTap: () => _updateState(_state.copyWith(targetCgpa: double.parse(goal))),
                    child: Container(
                      margin: const EdgeInsets.only(left: 4),
                      padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                      decoration: BoxDecoration(
                        color: isSel ? SkedColors.blaze : SkedColors.ink,
                        borderRadius: BorderRadius.circular(3),
                        border: Border.all(color: SkedColors.rule),
                      ),
                      child: Text(
                        goal,
                        style: TextStyle(
                          fontFamily: 'monospace',
                          fontSize: 10,
                          fontWeight: FontWeight.bold,
                          color: isSel ? SkedColors.ink : SkedColors.chalk,
                        ),
                      ),
                    ),
                  );
                }).toList(),
              ),
            ],
          ),
          const SizedBox(height: 10),
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    'To hit ${target.toStringAsFixed(2)} CGPA overall:',
                    style: GoogleFonts.barlowCondensed(fontSize: 13, color: SkedColors.chalk),
                  ),
                  Text(
                    neededTgpa <= 0
                        ? 'Target already secured!'
                        : neededTgpa > 10.0
                            ? 'Requires > 10.0 TGPA (Not possible this term)'
                            : neededTgpa >= 9.0
                                ? "Requires predominantly 'O' and 'A+' grades"
                                : neededTgpa >= 8.0
                                    ? "Requires 'A' or 'B+' average this term"
                                    : "Achievable with 'B' average",
                    style: GoogleFonts.barlowCondensed(
                      fontSize: 11,
                      color: neededTgpa > 10.0 ? const Color(0xFFFF5252) : SkedColors.slate,
                    ),
                  ),
                ],
              ),
              Column(
                crossAxisAlignment: CrossAxisAlignment.end,
                children: [
                  Text(
                    neededTgpa > 10.0 ? 'IMPOSSIBLE' : neededTgpa.toStringAsFixed(2),
                    style: TextStyle(
                      fontFamily: 'monospace',
                      fontSize: 20,
                      fontWeight: FontWeight.bold,
                      color: neededTgpa > 10.0
                          ? const Color(0xFFFF5252)
                          : ((activeTerm?.tgpa ?? 0.0) >= neededTgpa
                              ? const Color(0xFF00E676)
                              : SkedColors.blaze),
                    ),
                  ),
                  Text(
                    'NEEDED TGPA',
                    style: GoogleFonts.barlowCondensed(
                      fontSize: 10,
                      fontWeight: FontWeight.w700,
                      color: SkedColors.slate,
                    ),
                  ),
                ],
              ),
            ],
          ),
        ],
      ),
    );
  }

  Widget _buildSubjectCard(GpaSubject subject, TermData term) {
    final grade = subject.effectiveGrade;
    final gradeColor = grade != null ? _getGradeColor(grade.letter) : SkedColors.slate;

    return Container(
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: SkedColors.slab,
        borderRadius: BorderRadius.circular(6),
        border: Border.all(color: SkedColors.rule),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Row(
                children: [
                  Text(
                    subject.courseCode,
                    style: GoogleFonts.barlowCondensed(
                      fontSize: 16,
                      fontWeight: FontWeight.w700,
                      color: SkedColors.chalk,
                    ),
                  ),
                  const SizedBox(width: 8),
                  Container(
                    decoration: BoxDecoration(
                      color: SkedColors.ink,
                      borderRadius: BorderRadius.circular(3),
                      border: Border.all(color: SkedColors.rule),
                    ),
                    padding: const EdgeInsets.symmetric(horizontal: 4, vertical: 2),
                    child: Row(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        InkWell(
                          onTap: () {
                            if (subject.credits > 0.5) {
                              _updateSubjectInTerm(
                                  term, subject.copyWith(credits: subject.credits - 0.5));
                            }
                          },
                          child: const Padding(
                            padding: EdgeInsets.symmetric(horizontal: 4),
                            child: Text('-',
                                style: TextStyle(
                                    fontFamily: 'monospace',
                                    fontWeight: FontWeight.bold,
                                    fontSize: 12,
                                    color: SkedColors.slate)),
                          ),
                        ),
                        Text(
                          '${subject.credits.toStringAsFixed(1)} Cr',
                          style: const TextStyle(
                            fontFamily: 'monospace',
                            fontWeight: FontWeight.w600,
                            fontSize: 11,
                            color: SkedColors.chalk,
                          ),
                        ),
                        InkWell(
                          onTap: () {
                            if (subject.credits < 10.0) {
                              _updateSubjectInTerm(
                                  term, subject.copyWith(credits: subject.credits + 0.5));
                            }
                          },
                          child: const Padding(
                            padding: EdgeInsets.symmetric(horizontal: 4),
                            child: Text('+',
                                style: TextStyle(
                                    fontFamily: 'monospace',
                                    fontWeight: FontWeight.bold,
                                    fontSize: 12,
                                    color: SkedColors.slate)),
                          ),
                        ),
                      ],
                    ),
                  ),
                  if (subject.isCustom) ...[
                    const SizedBox(width: 6),
                    Text(
                      'CUSTOM',
                      style: GoogleFonts.barlowCondensed(fontSize: 9, color: SkedColors.slate),
                    ),
                  ],
                ],
              ),
              Row(
                children: [
                  if (grade != null)
                    Container(
                      padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                      decoration: BoxDecoration(
                        color: gradeColor.withOpacity(0.15),
                        borderRadius: BorderRadius.circular(3),
                        border: Border.all(color: gradeColor.withOpacity(0.5)),
                      ),
                      child: Text(
                        '${grade.letter} (${grade.points.toStringAsFixed(0)} pts)',
                        style: GoogleFonts.barlowCondensed(
                          fontSize: 11,
                          fontWeight: FontWeight.w700,
                          color: gradeColor,
                        ),
                      ),
                    )
                  else
                    Text(
                      'Tap grade below',
                      style: GoogleFonts.barlowCondensed(fontSize: 11, color: SkedColors.slate),
                    ),
                  const SizedBox(width: 6),
                  InkWell(
                    onTap: () {
                      final updated =
                          term.subjects.where((s) => s != subject).toList();
                      _updateTermSubjects(term, updated);
                    },
                    child: const Icon(Icons.close, color: SkedColors.slate, size: 15),
                  ),
                ],
              ),
            ],
          ),
          const SizedBox(height: 10),
          if (_globalMarksMode || subject.isMarksMode) ...[
            Row(
              children: [
                Expanded(
                  child: _buildMarksField('CA (30)', subject.caMarks, (v) {
                    _updateSubjectInTerm(term, subject.copyWith(caMarks: v));
                  }),
                ),
                const SizedBox(width: 6),
                Expanded(
                  child: _buildMarksField('MID (20)', subject.midTermMarks, (v) {
                    _updateSubjectInTerm(term, subject.copyWith(midTermMarks: v));
                  }),
                ),
                const SizedBox(width: 6),
                Expanded(
                  child: _buildMarksField('END (45)', subject.endTermMarks, (v) {
                    _updateSubjectInTerm(term, subject.copyWith(endTermMarks: v));
                  }),
                ),
                const SizedBox(width: 6),
                Expanded(
                  child: _buildMarksField('ATT (5)', subject.attendanceMarks, (v) {
                    _updateSubjectInTerm(term, subject.copyWith(attendanceMarks: v));
                  }),
                ),
              ],
            ),
            const SizedBox(height: 4),
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Text(
                  'Total: ${subject.totalMarks.toStringAsFixed(1)} / 100',
                  style: TextStyle(
                    fontFamily: 'monospace',
                    fontSize: 11,
                    color: subject.totalMarks > 100.0 ? const Color(0xFFFF5252) : SkedColors.slate,
                  ),
                ),
                if (grade != null)
                  Text(
                    'Predicted: ${grade.letter} • ${grade.label}',
                    style: GoogleFonts.barlowCondensed(fontSize: 11, color: gradeColor),
                  ),
              ],
            ),
          ] else ...[
            Row(
              children: LpuGrade.values.map((g) {
                final isSelected = subject.selectedGrade?.toLowerCase() == g.letter.toLowerCase();
                final gColor = _getGradeColor(g.letter);
                return Expanded(
                  child: GestureDetector(
                    onTap: () {
                      final newGrade = isSelected ? null : g.letter;
                      _updateSubjectInTerm(
                          term,
                          subject.copyWith(
                            selectedGrade: newGrade,
                            clearGrade: isSelected,
                          ));
                    },
                    child: Container(
                      height: 30,
                      margin: const EdgeInsets.symmetric(horizontal: 1.5),
                      decoration: BoxDecoration(
                        color: isSelected ? gColor : SkedColors.ink,
                        borderRadius: BorderRadius.circular(3),
                        border: Border.all(color: isSelected ? gColor : SkedColors.rule),
                      ),
                      alignment: Alignment.Center,
                      child: Text(
                        g.letter,
                        style: GoogleFonts.barlowCondensed(
                          fontSize: 12,
                          fontWeight: FontWeight.w700,
                          color: isSelected ? SkedColors.ink : SkedColors.chalk,
                        ),
                      ),
                    ),
                  ),
                );
              }).toList(),
            ),
          ],
        ],
      ),
    );
  }

  Widget _buildMarksField(String label, double? val, ValueChanged<double?> onChanged) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(label, style: GoogleFonts.barlowCondensed(fontSize: 9, color: SkedColors.slate)),
        const SizedBox(height: 2),
        SizedBox(
          height: 36,
          child: TextFormField(
            initialValue: val != null ? val.toStringAsFixed(1) : '',
            keyboardType: const TextInputType.numberWithOptions(decimal: true),
            style: const TextStyle(
              fontFamily: 'monospace',
              fontSize: 12,
              color: SkedColors.chalk,
            ),
            textAlign: TextAlign.center,
            decoration: InputDecoration(
              isDense: true,
              contentPadding: const EdgeInsets.symmetric(vertical: 8),
              filled: true,
              fillColor: SkedColors.ink,
              enabledBorder: OutlineInputBorder(
                borderRadius: BorderRadius.circular(4),
                borderSide: const BorderSide(color: SkedColors.rule),
              ),
              focusedBorder: OutlineInputBorder(
                borderRadius: BorderRadius.circular(4),
                borderSide: const BorderSide(color: SkedColors.blaze),
              ),
            ),
            onChanged: (str) {
              final parsed = double.tryParse(str.trim());
              onChanged(parsed);
            },
          ),
        ),
      ],
    );
  }

  void _updateTermSubjects(TermData term, List<GpaSubject> subjects) {
    final updatedTerms = _state.terms
        .map((t) => t.id == term.id ? t.copyWith(subjects: subjects) : t)
        .toList();
    _updateState(_state.copyWith(terms: updatedTerms));
  }

  void _updateSubjectInTerm(TermData term, GpaSubject subject) {
    final updatedSubs = term.subjects
        .map((s) => (s.courseCode == subject.courseCode && s.isCustom == subject.isCustom)
            ? subject
            : s)
        .toList();
    _updateTermSubjects(term, updatedSubs);
  }

  void _showAddTermDialog() {
    final nameCtrl = TextEditingController(text: 'Term ${_state.terms.length + 1}');
    final tgpaCtrl = TextEditingController();
    final crCtrl = TextEditingController(text: '20.0');

    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        backgroundColor: SkedColors.slab,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
        title: Text(
          'ADD NEW TERM',
          style: GoogleFonts.barlowCondensed(
            fontWeight: FontWeight.w700,
            fontSize: 16,
            color: SkedColors.chalk,
          ),
        ),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            TextField(
              controller: nameCtrl,
              style: const TextStyle(color: SkedColors.chalk),
              decoration: const InputDecoration(labelText: 'Term Name (e.g. Term 1)'),
            ),
            const SizedBox(height: 10),
            Row(
              children: [
                Expanded(
                  child: TextField(
                    controller: tgpaCtrl,
                    keyboardType: const TextInputType.numberWithOptions(decimal: true),
                    style: const TextStyle(color: SkedColors.chalk),
                    decoration: const InputDecoration(labelText: 'TGPA (Optional)'),
                  ),
                ),
                const SizedBox(width: 8),
                Expanded(
                  child: TextField(
                    controller: crCtrl,
                    keyboardType: const TextInputType.numberWithOptions(decimal: true),
                    style: const TextStyle(color: SkedColors.chalk),
                    decoration: const InputDecoration(labelText: 'Credits'),
                  ),
                ),
              ],
            ),
          ],
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx),
            child: Text('CANCEL', style: GoogleFonts.barlowCondensed(color: SkedColors.slate)),
          ),
          ElevatedButton(
            style: ElevatedButton.styleFrom(backgroundColor: SkedColors.blaze),
            onPressed: () {
              final name = nameCtrl.text.trim();
              final tg = double.tryParse(tgpaCtrl.text.trim());
              final cr = double.tryParse(crCtrl.text.trim()) ?? 20.0;
              if (name.isNotEmpty) {
                final newTerm = TermData(
                  id: DateTime.now().millisecondsSinceEpoch.toString(),
                  name: name,
                  manualTgpa: tg,
                  manualCredits: cr,
                );
                final updated = [..._state.terms, newTerm];
                _updateState(_state.copyWith(terms: updated, activeTermId: newTerm.id));
              }
              Navigator.pop(ctx);
            },
            child: Text('CREATE TERM',
                style: GoogleFonts.barlowCondensed(
                    color: SkedColors.ink, fontWeight: FontWeight.bold)),
          ),
        ],
      ),
    );
  }

  void _showAddCustomDialog(TermData term) {
    final codeCtrl = TextEditingController();
    final crCtrl = TextEditingController(text: '3.0');

    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        backgroundColor: SkedColors.slab,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
        title: Text(
          'ADD COURSE TO ${term.name.toUpperCase()}',
          style: GoogleFonts.barlowCondensed(
            fontWeight: FontWeight.w700,
            fontSize: 16,
            color: SkedColors.chalk,
          ),
        ),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            TextField(
              controller: codeCtrl,
              textCapitalization: TextCapitalization.characters,
              style: const TextStyle(color: SkedColors.chalk),
              decoration: const InputDecoration(labelText: 'Course Code (e.g. INT306)'),
            ),
            const SizedBox(height: 10),
            TextField(
              controller: crCtrl,
              keyboardType: const TextInputType.numberWithOptions(decimal: true),
              style: const TextStyle(color: SkedColors.chalk),
              decoration: const InputDecoration(labelText: 'Credits (e.g. 3.0)'),
            ),
          ],
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx),
            child: Text('CANCEL', style: GoogleFonts.barlowCondensed(color: SkedColors.slate)),
          ),
          ElevatedButton(
            style: ElevatedButton.styleFrom(backgroundColor: SkedColors.blaze),
            onPressed: () {
              final code = codeCtrl.text.trim().toUpperCase();
              final cr = double.tryParse(crCtrl.text.trim()) ?? 3.0;
              if (code.isNotEmpty) {
                final exists = term.subjects.any((s) => s.courseCode == code);
                if (!exists) {
                  final newSub = GpaSubject(
                    courseCode: code,
                    courseTitle: 'Custom Course',
                    credits: cr,
                    isCustom: true,
                  );
                  _updateTermSubjects(term, [...term.subjects, newSub]);
                }
              }
              Navigator.pop(ctx);
            },
            child: Text('ADD COURSE',
                style: GoogleFonts.barlowCondensed(
                    color: SkedColors.ink, fontWeight: FontWeight.bold)),
          ),
        ],
      ),
    );
  }
}
