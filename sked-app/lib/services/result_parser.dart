import 'dart:convert';
import 'package:flutter/foundation.dart';
import '../models/class_entry.dart';
import '../models/gpa_models.dart';

class ParsedTerm {
  final String name;
  final int? semester;
  final double? tgpa;
  final double? totalCredits;
  final List<GpaSubject> subjects;

  const ParsedTerm({
    required this.name,
    this.semester,
    this.tgpa,
    this.totalCredits,
    this.subjects = const [],
  });
}

class ParsedResultSummary {
  final double? cgpa;
  final List<ParsedTerm> terms;
  final String source;

  const ParsedResultSummary({
    this.cgpa,
    this.terms = const [],
    this.source = 'ums',
  });
}

class ResultParser {
  /// Parses result summary from any available string payload:
  /// - Next.js console hook JSON (CoursewiseGrade, TGPA Data, profileData)
  /// - DOM text from studentums.lpu.in
  /// - Classic UMS HTML tables
  static ParsedResultSummary parse(String rawInput) {
    final clean = rawInput.trim();
    if (clean.isEmpty) return const ParsedResultSummary();

    // 1. Try JSON parsing first
    final jsonResult = _parseFromJson(clean);
    if (jsonResult.terms.isNotEmpty || jsonResult.cgpa != null) {
      debugPrint('[ResultParser] Parsed ${jsonResult.terms.length} terms from JSON (CGPA: ${jsonResult.cgpa})');
      return jsonResult;
    }

    // 2. Try DOM text parsing
    final textResult = _parseFromDomText(clean);
    if (textResult.terms.isNotEmpty || textResult.cgpa != null) {
      debugPrint('[ResultParser] Parsed ${textResult.terms.length} terms from DOM text (CGPA: ${textResult.cgpa})');
      return textResult;
    }

    // 3. Try classic UMS HTML table parsing
    final htmlResult = _parseFromClassicHtml(clean);
    if (htmlResult.terms.isNotEmpty || htmlResult.cgpa != null) {
      debugPrint('[ResultParser] Parsed ${htmlResult.terms.length} terms from Classic HTML (CGPA: ${htmlResult.cgpa})');
      return htmlResult;
    }

    return const ParsedResultSummary();
  }

  static ParsedResultSummary _parseFromJson(String raw) {
    double? cgpa;
    final terms = <ParsedTerm>[];

    try {
      final startBracket = raw.indexOf('[');
      final startBrace = raw.indexOf('{');
      int startIdx = -1;
      if (startBracket != -1 && startBrace != -1) {
        startIdx = startBracket < startBrace ? startBracket : startBrace;
      } else if (startBracket != -1) {
        startIdx = startBracket;
      } else {
        startIdx = startBrace;
      }

      if (startIdx == -1) return const ParsedResultSummary();
      final candidate = raw.substring(startIdx);

      if (candidate.startsWith('{')) {
        final obj = jsonDecode(candidate) as Map<String, dynamic>;

        // 1. Profile / CGPA check
        final profObj = (obj['profile'] is Map<String, dynamic>)
            ? obj['profile'] as Map<String, dynamic>
            : obj;
        final profArr = profObj['profileData'];
        if (profArr is List && profArr.isNotEmpty) {
          final p = profArr.first as Map<String, dynamic>;
          final rawCgpa = (p['cgpa'] as num?)?.toDouble();
          if (rawCgpa != null && rawCgpa >= 1.0 && rawCgpa <= 10.0) {
            cgpa = rawCgpa;
          }
        }
        if (cgpa == null) {
          final rawCgpa = (obj['cgpa'] as num?)?.toDouble();
          if (rawCgpa != null && rawCgpa >= 1.0 && rawCgpa <= 10.0) {
            cgpa = rawCgpa;
          }
        }

        // 2. TGPA Data check (from Next.js getStudentTgpaAction console hook)
        final tgpaData = obj['tgpaData'];
        if (tgpaData is List) {
          for (final row in tgpaData) {
            if (row is! Map<String, dynamic>) continue;
            final cat = row['Category']?.toString() ?? '';
            if (cat.toLowerCase() == 'student' || tgpaData.length == 1) {
              for (final entry in row.entries) {
                final k = entry.key;
                if (k.toLowerCase().startsWith('tgpasem')) {
                  final semNum = int.tryParse(k.substring(7));
                  if (semNum == null) continue;
                  final tgpaVal = (entry.value as num?)?.toDouble() ?? double.tryParse(entry.value?.toString() ?? '');
                  if (tgpaVal != null && tgpaVal >= 1.0 && tgpaVal <= 10.0) {
                    terms.add(ParsedTerm(
                      name: 'Term $semNum',
                      semester: semNum,
                      tgpa: tgpaVal,
                      totalCredits: 20.0,
                    ));
                  }
                }
              }
            }
          }
        }

        // 3. Check CoursewiseGrade or Terms inside object
        final coursewise = obj['coursewise'] ?? obj['CoursewiseGrade'] ?? obj['coursewiseGrade'] ?? obj['terms'];
        if (coursewise is List) {
          final parsedCoursewise = _parseTermsArray(coursewise);
          for (final pt in parsedCoursewise) {
            final existingIdx = terms.indexWhere((t) => t.semester == pt.semester || t.name.toLowerCase() == pt.name.toLowerCase());
            if (existingIdx != -1) {
              final existing = terms[existingIdx];
              terms[existingIdx] = ParsedTerm(
                name: existing.name,
                semester: existing.semester,
                tgpa: existing.tgpa ?? pt.tgpa,
                totalCredits: pt.subjects.isNotEmpty ? pt.subjects.fold(0.0, (acc, s) => acc + s.credits) : existing.totalCredits,
                subjects: pt.subjects.isNotEmpty ? pt.subjects : existing.subjects,
              );
            } else {
              terms.add(pt);
            }
          }
        }
      } else if (candidate.startsWith('[')) {
        final arr = jsonDecode(candidate) as List;
        terms.addAll(_parseTermsArray(arr));
      }
    } catch (e) {
      debugPrint('[ResultParser] JSON parse notice: $e');
    }

    terms.sort((a, b) => (a.semester ?? 99).compareTo(b.semester ?? 99));
    return ParsedResultSummary(cgpa: cgpa, terms: terms, source: 'json');
  }

  static List<ParsedTerm> _parseTermsArray(List arr) {
    final result = <ParsedTerm>[];
    for (var i = 0; i < arr.length; i++) {
      final item = arr[i];
      if (item is! Map<String, dynamic>) continue;

      final sem = (item['Semester'] as num?)?.toInt() ??
          (item['semester'] as num?)?.toInt() ??
          (i + 1);
      final rawTag = (item['TermTag'] as String?) ?? (item['termTag'] as String?) ?? 'Term $sem';
      final termName = rawTag.trim().isNotEmpty ? rawTag.trim() : 'Term $sem';

      final rawTgpa = (item['Tgpa'] as num?)?.toDouble() ??
          (item['tgpa'] as num?)?.toDouble() ??
          (item['TGPA'] as num?)?.toDouble() ??
          double.tryParse(item['Tgpa']?.toString() ?? '');
      final tgpaNum = (rawTgpa != null && rawTgpa >= 1.0 && rawTgpa <= 10.0) ? rawTgpa : null;

      final subjects = <GpaSubject>[];
      final courses = item['Courses'] ?? item['courses'] ?? item['Subjects'] ?? item['subjects'];
      if (courses is List) {
        for (final c in courses) {
          if (c is! Map<String, dynamic>) continue;
          final code = (c['CourseCode'] ?? c['courseCode'] ?? c['code'] ?? '')
              .toString()
              .trim()
              .toUpperCase();
          if (code.isEmpty) continue;
          final title = (c['CourseTitle'] ?? c['courseTitle'] ?? c['courseName'] ?? '').toString().trim();
          final cr = (c['Credits'] as num?)?.toDouble() ?? (c['credits'] as num?)?.toDouble() ?? 3.0;
          final gr = (c['Grade'] ?? c['grade'])?.toString().trim().toUpperCase();

          subjects.add(GpaSubject(
            courseCode: code,
            courseTitle: title,
            credits: cr > 0 ? cr : 3.0,
            selectedGrade: (gr != null && gr.isNotEmpty) ? gr : null,
          ));
        }
      }

      final totalCr = subjects.isNotEmpty
          ? subjects.fold(0.0, (acc, s) => acc + s.credits)
          : ((item['totalCredits'] as num?)?.toDouble() ?? 20.0);

      result.add(ParsedTerm(
        name: termName,
        semester: sem,
        tgpa: tgpaNum,
        totalCredits: totalCr,
        subjects: subjects,
      ));
    }
    return result;
  }

  static ParsedResultSummary _parseFromDomText(String text) {
    double? cgpa;
    final terms = <ParsedTerm>[];

    // 1. CGPA Extraction
    // Match number before CGPA (e.g. "8.65\nCGPA" or "8.65 CGPA")
    final cgpaNumFirstExp = RegExp(r'([0-9]+\.[0-9]+)\s*(?:\n|\r|\s)+\s*(?:CGPA|NET\s*PERCENTAGE)', caseSensitive: false);
    final m1 = cgpaNumFirstExp.firstMatch(text);
    if (m1 != null) {
      final v = double.tryParse(m1.group(1) ?? '');
      if (v != null && v >= 1.0 && v <= 10.0) {
        cgpa = v;
      }
    }

    // Match CGPA before number (e.g. "CGPA: 8.65")
    if (cgpa == null) {
      final cgpaExp = RegExp(r'(?:CGPA|Cumulative\s*Grade\s*Point\s*Average|Net\s*Percentage)\s*[:\-]?\s*([0-9]+\.[0-9]+)', caseSensitive: false);
      final m2 = cgpaExp.firstMatch(text);
      if (m2 != null) {
        final v = double.tryParse(m2.group(1) ?? '');
        if (v != null && v >= 1.0 && v <= 10.0) {
          cgpa = v;
        }
      }
    }

    // 2. Term & TGPA Extraction (allowing newlines between Term and TGPA)
    final termExp = RegExp(r'Term\s*([0-9]+)[\s\S]{0,100}?(?:TGPA|Term\s*GPA|Grade\s*Point\s*Average)\s*[:\-]?\s*([0-9]+\.[0-9]+)', caseSensitive: false);
    final foundTerms = <int, double>{};
    for (final m in termExp.allMatches(text)) {
      final sem = int.tryParse(m.group(1) ?? '');
      final tgpa = double.tryParse(m.group(2) ?? '');
      if (sem != null && tgpa != null && tgpa >= 1.0 && tgpa <= 10.0) {
        foundTerms[sem] = tgpa;
      }
    }

    // Fallback for chips "Term 1: 8.40"
    if (foundTerms.isEmpty()) {
      final chipExp = RegExp(r'Term\s*([0-9]+)\s*[:\-]\s*([0-9]+\.[0-9]+)', caseSensitive: false);
      for (final m in chipExp.allMatches(text)) {
        final sem = int.tryParse(m.group(1) ?? '');
        final tgpa = double.tryParse(m.group(2) ?? '');
        if (sem != null && tgpa != null && tgpa >= 1.0 && tgpa <= 10.0) {
          foundTerms[sem] = tgpa;
        }
      }
    }

    // Fallback for reversed pattern "TGPA: 8.40 ... Term 1"
    if (foundTerms.isEmpty()) {
      final revExp = RegExp(r'(?:TGPA|Term\s*GPA)\s*[:\-]?\s*([0-9]+\.[0-9]+)[\s\S]{0,100}?Term\s*([0-9]+)', caseSensitive: false);
      for (final m in revExp.allMatches(text)) {
        final tgpa = double.tryParse(m.group(1) ?? '');
        final sem = int.tryParse(m.group(2) ?? '');
        if (sem != null && tgpa != null && tgpa >= 1.0 && tgpa <= 10.0) {
          foundTerms[sem] = tgpa;
        }
      }
    }

    final sortedKeys = foundTerms.keys.toList()..sort();
    for (final sem in sortedKeys) {
      terms.add(ParsedTerm(
        name: 'Term $sem',
        semester: sem,
        tgpa: foundTerms[sem],
        totalCredits: 20.0,
      ));
    }

    return ParsedResultSummary(cgpa: cgpa, terms: terms, source: 'dom_text');
  }

  static ParsedResultSummary _parseFromClassicHtml(String html) {
    double? cgpa;
    final terms = <ParsedTerm>[];

    final cgpaMatch = RegExp(r'(?:CGPA|Cumulative\s*Grade)[^<0-9]*([0-9]+(?:\.[0-9]+)?)', caseSensitive: false).firstMatch(html);
    if (cgpaMatch != null) {
      final v = double.tryParse(cgpaMatch.group(1) ?? '');
      if (v != null && v >= 1.0 && v <= 10.0) {
        cgpa = v;
      }
    }

    final tgpaMatches = RegExp(r'(?:Term|Semester)\s*([0-9]+)[^<]*?TGPA[^<0-9]*([0-9]+(?:\.[0-9]+)?)', caseSensitive: false).allMatches(html);
    for (final m in tgpaMatches) {
      final sem = int.tryParse(m.group(1) ?? '');
      final tgpa = double.tryParse(m.group(2) ?? '');
      if (sem != null && tgpa != null && tgpa >= 1.0 && tgpa <= 10.0 && !terms.any((t) => t.semester == sem)) {
        terms.add(ParsedTerm(
          name: 'Term $sem',
          semester: sem,
          tgpa: tgpa,
          totalCredits: 20.0,
        ));
      }
    }

    return ParsedResultSummary(cgpa: cgpa, terms: terms, source: 'classic_html');
  }

  /// Merges parsed results into storage while preserving Current Term
  static Future<GpaState> mergeIntoStorage(ParsedResultSummary summary, List<ClassEntry> timetable) async {
    final currentState = await GpaStorage.load(timetable);
    final currentTerm = currentState.terms.where((t) => t.isCurrent).firstOrNull ??
        const TermData(id: 'current_term', name: 'Current Term', isCurrent: true);

    final updatedTerms = <TermData>[];

    final sortedParsed = List<ParsedTerm>.from(summary.terms)
      ..sort((a, b) => (a.semester ?? 99).compareTo(b.semester ?? 99));

    for (final pt in sortedParsed) {
      final termId = 'term_${pt.name.toLowerCase().replaceAll(' ', '_')}';
      if (termId == currentTerm.id) continue;

      final existing = currentState.terms.where((t) => t.id == termId || t.name.toLowerCase() == pt.name.toLowerCase()).firstOrNull;
      final mergedSubjects = pt.subjects.isNotEmpty ? pt.subjects : (existing?.subjects ?? const []);

      updatedTerms.add(TermData(
        id: termId,
        name: pt.name,
        isCurrent: false,
        subjects: mergedSubjects,
        manualTgpa: pt.tgpa ?? existing?.manualTgpa,
        manualCredits: pt.totalCredits ?? existing?.manualCredits ?? 20.0,
      ));
    }

    for (final et in currentState.terms) {
      if (!et.isCurrent && !updatedTerms.any((t) => t.id == et.id || t.name.toLowerCase() == et.name.toLowerCase())) {
        updatedTerms.add(et);
      }
    }

    updatedTerms.add(currentTerm);

    final validCgpa = (summary.cgpa != null && summary.cgpa! >= 1.0 && summary.cgpa! <= 10.0) ? summary.cgpa : null;
    final newState = currentState.copyWith(
      terms: updatedTerms,
      activeTermId: currentTerm.id,
      officialCgpa: validCgpa ?? currentState.officialCgpa,
    );

    await GpaStorage.save(newState);
    return newState;
  }
}
