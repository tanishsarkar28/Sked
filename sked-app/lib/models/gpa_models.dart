import 'dart:convert';
import 'package:shared_preferences/shared_preferences.dart';
import 'class_entry.dart';

enum LpuGrade {
  o('O', 10.0, '90–100', 'Outstanding'),
  aPlus('A+', 9.0, '80–89', 'Excellent'),
  a('A', 8.0, '70–79', 'Very Good'),
  bPlus('B+', 7.0, '60–69', 'Good'),
  b('B', 6.0, '50–59', 'Above Avg'),
  c('C', 5.0, '45–49', 'Average'),
  d('D', 4.0, '40–44', 'Pass'),
  f('F', 0.0, '<40', 'Fail');

  final String letter;
  final double points;
  final String marksRange;
  final String label;

  const LpuGrade(this.letter, this.points, this.marksRange, this.label);

  static LpuGrade? fromLetter(String? letter) {
    if (letter == null) return null;
    for (final g in LpuGrade.values) {
      if (g.letter.toLowerCase() == letter.toLowerCase()) return g;
    }
    return null;
  }

  static LpuGrade fromMarks(double total) {
    if (total >= 90.0) return LpuGrade.o;
    if (total >= 80.0) return LpuGrade.aPlus;
    if (total >= 70.0) return LpuGrade.a;
    if (total >= 60.0) return LpuGrade.bPlus;
    if (total >= 50.0) return LpuGrade.b;
    if (total >= 45.0) return LpuGrade.c;
    if (total >= 40.0) return LpuGrade.d;
    return LpuGrade.f;
  }
}

class GpaSubject {
  final String courseCode;
  final String courseTitle;
  final double credits;
  final String? selectedGrade;
  final bool isMarksMode;
  final double? caMarks;
  final double? midTermMarks;
  final double? endTermMarks;
  final double? attendanceMarks;
  final bool isCustom;

  const GpaSubject({
    required this.courseCode,
    this.courseTitle = '',
    this.credits = 3.0,
    this.selectedGrade,
    this.isMarksMode = false,
    this.caMarks,
    this.midTermMarks,
    this.endTermMarks,
    this.attendanceMarks,
    this.isCustom = false,
  });

  double get totalMarks =>
      (caMarks ?? 0.0) +
      (midTermMarks ?? 0.0) +
      (endTermMarks ?? 0.0) +
      (attendanceMarks ?? 0.0);

  LpuGrade? get effectiveGrade {
    if (isMarksMode &&
        (caMarks != null ||
            midTermMarks != null ||
            endTermMarks != null ||
            attendanceMarks != null)) {
      return LpuGrade.fromMarks(totalMarks);
    }
    return LpuGrade.fromLetter(selectedGrade);
  }

  double? get gradePoints => effectiveGrade?.points;
  double get weightedPoints => credits * (gradePoints ?? 0.0);

  GpaSubject copyWith({
    String? courseCode,
    String? courseTitle,
    double? credits,
    String? selectedGrade,
    bool clearGrade = false,
    bool? isMarksMode,
    double? caMarks,
    double? midTermMarks,
    double? endTermMarks,
    double? attendanceMarks,
    bool? isCustom,
  }) {
    return GpaSubject(
      courseCode: courseCode ?? this.courseCode,
      courseTitle: courseTitle ?? this.courseTitle,
      credits: credits ?? this.credits,
      selectedGrade: clearGrade ? null : (selectedGrade ?? this.selectedGrade),
      isMarksMode: isMarksMode ?? this.isMarksMode,
      caMarks: caMarks ?? this.caMarks,
      midTermMarks: midTermMarks ?? this.midTermMarks,
      endTermMarks: endTermMarks ?? this.endTermMarks,
      attendanceMarks: attendanceMarks ?? this.attendanceMarks,
      isCustom: isCustom ?? this.isCustom,
    );
  }

  Map<String, dynamic> toJson() => {
        'courseCode': courseCode,
        'courseTitle': courseTitle,
        'credits': credits,
        if (selectedGrade != null) 'selectedGrade': selectedGrade,
        'isMarksMode': isMarksMode,
        if (caMarks != null) 'caMarks': caMarks,
        if (midTermMarks != null) 'midTermMarks': midTermMarks,
        if (endTermMarks != null) 'endTermMarks': endTermMarks,
        if (attendanceMarks != null) 'attendanceMarks': attendanceMarks,
        'isCustom': isCustom,
      };

  factory GpaSubject.fromJson(Map<String, dynamic> json) => GpaSubject(
        courseCode: json['courseCode'] ?? '',
        courseTitle: json['courseTitle'] ?? '',
        credits: (json['credits'] as num?)?.toDouble() ?? 3.0,
        selectedGrade: json['selectedGrade'] as String?,
        isMarksMode: json['isMarksMode'] == true,
        caMarks: (json['caMarks'] as num?)?.toDouble(),
        midTermMarks: (json['midTermMarks'] as num?)?.toDouble(),
        endTermMarks: (json['endTermMarks'] as num?)?.toDouble(),
        attendanceMarks: (json['attendanceMarks'] as num?)?.toDouble(),
        isCustom: json['isCustom'] == true,
      );
}

class TermData {
  final String id;
  final String name;
  final bool isCurrent;
  final List<GpaSubject> subjects;
  final double? manualTgpa;
  final double? manualCredits;

  const TermData({
    required this.id,
    required this.name,
    this.isCurrent = false,
    this.subjects = const [],
    this.manualTgpa,
    this.manualCredits,
  });

  List<GpaSubject> get gradedSubjects =>
      subjects.where((s) => s.gradePoints != null).toList();

  double get totalCredits => subjects.isNotEmpty
      ? subjects.fold(0.0, (acc, s) => acc + s.credits)
      : (manualCredits ?? 0.0);

  double get evaluatedCredits => subjects.isNotEmpty
      ? gradedSubjects.fold(0.0, (acc, s) => acc + s.credits)
      : (manualCredits ?? 0.0);

  double get weightedPoints => subjects.isNotEmpty
      ? gradedSubjects.fold(0.0, (acc, s) => acc + s.weightedPoints)
      : ((manualTgpa ?? 0.0) * (manualCredits ?? 0.0));

  double? get tgpa {
    if (subjects.isNotEmpty) {
      final cr = evaluatedCredits;
      if (cr <= 0.0) return null;
      return weightedPoints / cr;
    }
    return manualTgpa;
  }

  TermData copyWith({
    String? id,
    String? name,
    bool? isCurrent,
    List<GpaSubject>? subjects,
    double? manualTgpa,
    double? manualCredits,
  }) {
    return TermData(
      id: id ?? this.id,
      name: name ?? this.name,
      isCurrent: isCurrent ?? this.isCurrent,
      subjects: subjects ?? this.subjects,
      manualTgpa: manualTgpa ?? this.manualTgpa,
      manualCredits: manualCredits ?? this.manualCredits,
    );
  }

  Map<String, dynamic> toJson() => {
        'id': id,
        'name': name,
        'isCurrent': isCurrent,
        if (manualTgpa != null) 'manualTgpa': manualTgpa,
        if (manualCredits != null) 'manualCredits': manualCredits,
        'subjects': subjects.map((s) => s.toJson()).toList(),
      };

  factory TermData.fromJson(Map<String, dynamic> json) => TermData(
        id: json['id'] ?? '',
        name: json['name'] ?? '',
        isCurrent: json['isCurrent'] == true,
        manualTgpa: (json['manualTgpa'] as num?)?.toDouble(),
        manualCredits: (json['manualCredits'] as num?)?.toDouble(),
        subjects: (json['subjects'] as List? ?? [])
            .map((s) => GpaSubject.fromJson(s as Map<String, dynamic>))
            .toList(),
      );
}

class GpaState {
  final List<TermData> terms;
  final String activeTermId;
  final double? targetCgpa;
  final double? officialCgpa;

  const GpaState({
    this.terms = const [],
    this.activeTermId = '',
    this.targetCgpa,
    this.officialCgpa,
  });

  TermData? get activeTerm =>
      terms.where((t) => t.id == activeTermId).firstOrNull ?? terms.firstOrNull;

  double get totalCompletedCredits =>
      terms.fold(0.0, (acc, t) => acc + t.evaluatedCredits);

  double get totalCompletedWeightedPoints =>
      terms.fold(0.0, (acc, t) => acc + t.weightedPoints);

  double? get displayCgpa {
    final currentTerm = terms.where((t) => t.isCurrent).firstOrNull;
    final currentHasGrades = currentTerm != null && currentTerm.evaluatedCredits > 0.0;
    if (currentHasGrades || officialCgpa == null) {
      return cumulativeCgpa ?? officialCgpa;
    }
    return officialCgpa;
  }

  double? get cumulativeCgpa {
    final validTerms =
        terms.where((t) => t.tgpa != null && t.evaluatedCredits > 0.0).toList();
    if (validTerms.isEmpty) return null;
    final totalCr = validTerms.fold(0.0, (acc, t) => acc + t.evaluatedCredits);
    final totalWeighted = validTerms.fold(0.0, (acc, t) => acc + t.weightedPoints);
    if (totalCr <= 0.0) return null;
    return totalWeighted / totalCr;
  }

  GpaState copyWith({
    List<TermData>? terms,
    String? activeTermId,
    double? targetCgpa,
    double? officialCgpa,
  }) {
    return GpaState(
      terms: terms ?? this.terms,
      activeTermId: activeTermId ?? this.activeTermId,
      targetCgpa: targetCgpa ?? this.targetCgpa,
      officialCgpa: officialCgpa ?? this.officialCgpa,
    );
  }
}

class GpaStorage {
  static const String keyTerms = 'saved_gpa_terms_v2';
  static const String keyActiveTermId = 'saved_gpa_active_term_id';
  static const String keyTarget = 'saved_gpa_target_cgpa';
  static const String keyOfficial = 'saved_gpa_official_cgpa';

  static Future<GpaState> load(List<ClassEntry> timetable) async {
    final prefs = await SharedPreferences.getInstance();
    final termsRaw = prefs.getString(keyTerms);
    final savedActiveId = prefs.getString(keyActiveTermId) ?? '';
    final target = prefs.getDouble(keyTarget);
    final official = prefs.getDouble(keyOfficial);

    final terms = <TermData>[];
    if (termsRaw != null && termsRaw.isNotEmpty) {
      try {
        final list = jsonDecode(termsRaw) as List;
        for (final item in list) {
          terms.add(TermData.fromJson(item as Map<String, dynamic>));
        }
      } catch (_) {}
    }

    final uniqueCourses = timetable
        .map((c) => c.courseCode.trim().toUpperCase())
        .where((code) => code.isNotEmpty)
        .toSet();

    TermData? currentTerm = terms.where((t) => t.isCurrent).firstOrNull;

    if (currentTerm == null) {
      final initialSubjects = uniqueCourses.map((code) {
        final isLab = timetable.any(
            (c) => c.courseCode.toUpperCase() == code && c.isPractical);
        final defaultCr = isLab ? 2.0 : 3.0;
        final sample = timetable.firstWhere(
            (c) => c.courseCode.toUpperCase() == code,
            orElse: () => timetable.first);
        return GpaSubject(
          courseCode: code,
          courseTitle: sample.type,
          credits: defaultCr,
          selectedGrade: null,
        );
      }).toList();

      final newCurrent = TermData(
        id: 'current_term',
        name: 'Current Term',
        isCurrent: true,
        subjects: initialSubjects,
      );
      terms.add(newCurrent);
      currentTerm = newCurrent;
    } else {
      final existingCodes =
          currentTerm.subjects.map((s) => s.courseCode.toUpperCase()).toSet();
      final newSubs = List<GpaSubject>.from(currentTerm.subjects);
      for (final code in uniqueCourses) {
        if (!existingCodes.contains(code)) {
          final isLab = timetable.any(
              (c) => c.courseCode.toUpperCase() == code && c.isPractical);
          final defaultCr = isLab ? 2.0 : 3.0;
          final sample = timetable.firstWhere(
              (c) => c.courseCode.toUpperCase() == code,
              orElse: () => timetable.first);
          newSubs.add(GpaSubject(
            courseCode: code,
            courseTitle: sample.type,
            credits: defaultCr,
            selectedGrade: null,
          ));
        }
      }
      final idx = terms.indexWhere((t) => t.id == currentTerm!.id);
      if (idx >= 0) {
        terms[idx] = currentTerm.copyWith(subjects: newSubs);
      }
    }

    final activeId = (savedActiveId.isNotEmpty &&
            terms.any((t) => t.id == savedActiveId))
        ? savedActiveId
        : currentTerm.id;

    return GpaState(
      terms: terms,
      activeTermId: activeId,
      targetCgpa: target,
      officialCgpa: official,
    );
  }

  static Future<void> save(GpaState state) async {
    final prefs = await SharedPreferences.getInstance();
    final termsJson = jsonEncode(state.terms.map((t) => t.toJson()).toList());

    await prefs.setString(keyTerms, termsJson);
    await prefs.setString(keyActiveTermId, state.activeTermId);

    if (state.targetCgpa != null) {
      await prefs.setDouble(keyTarget, state.targetCgpa!);
    } else {
      await prefs.remove(keyTarget);
    }

    if (state.officialCgpa != null) {
      await prefs.setDouble(keyOfficial, state.officialCgpa!);
    } else {
      await prefs.remove(keyOfficial);
    }
  }
}
