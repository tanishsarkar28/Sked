import 'dart:convert';
import 'package:shared_preferences/shared_preferences.dart';
import '../models/exam_item.dart';

/// On-device datesheet and exam parser — Dart port of ExamParser.kt.
class ExamParser {
  static const String keyExamsJson = 'sked_exam_entries';
  static const String keyLastSynced = 'sked_exam_last_synced';

  static String getCourseTitle(String code) {
    switch (code.toUpperCase()) {
      case 'PEA306': return 'Analytical Skills-II';
      case 'CSE408': return 'Design and Analysis of Algorithms';
      case 'INT257': return 'Software Project Management';
      case 'INT252': return 'Web App Development with ReactJS';
      case 'PES390': return 'Soft Skills';
      case 'MKT311': return 'Marketing';
      default: return '';
    }
  }

  /// Official examination schedule for current term as published on studentums.lpu.in.
  static List<ExamItem> getVerifiedSchedule() {
    final list = [
      const ExamItem(
        courseCode: 'PEA306',
        courseTitle: 'Analytical Skills-II',
        dateStr: '01 Oct 2026',
        dayName: 'Thursday',
        timeSlot: '12:30 PM – 01:30 PM',
        session: 'Evening',
        examType: 'MTE',
        room: 'Seating Awaited',
        seatNo: 'Awaited',
        reportingTime: 'Report 12:00 PM',
      ),
      const ExamItem(
        courseCode: 'CSE408',
        courseTitle: 'Design and Analysis of Algorithms',
        dateStr: '06 Oct 2026',
        dayName: 'Tuesday',
        timeSlot: '12:30 PM – 02:00 PM',
        session: 'Evening',
        examType: 'MTE',
        room: 'Seating Awaited',
        seatNo: 'Awaited',
        reportingTime: 'Report 12:00 PM',
      ),
      const ExamItem(
        courseCode: 'CSE408',
        courseTitle: 'Design and Analysis of Algorithms',
        dateStr: '15 Dec 2026',
        dayName: 'Tuesday',
        timeSlot: '01:30 PM – 04:30 PM',
        session: 'Evening',
        examType: 'ETE',
        room: 'Seating Awaited',
        seatNo: 'Awaited',
        reportingTime: 'Report 01:00 PM',
      ),
      const ExamItem(
        courseCode: 'PEA306',
        courseTitle: 'Analytical Skills-II',
        dateStr: '21 Dec 2026',
        dayName: 'Monday',
        timeSlot: '01:30 PM – 03:30 PM',
        session: 'Evening',
        examType: 'ETE',
        room: 'Seating Awaited',
        seatNo: 'Awaited',
        reportingTime: 'Report 01:00 PM',
      ),
    ];
    list.sort((a, b) => (a.getExamDate()?.millisecondsSinceEpoch ?? 9999999999999)
        .compareTo(b.getExamDate()?.millisecondsSinceEpoch ?? 9999999999999));
    return list;
  }

  static Future<void> saveExamsToPrefs(List<ExamItem> exams) async {
    final prefs = await SharedPreferences.getInstance();
    final jsonList = jsonEncode(exams.map((e) => e.toJson()).toList());
    await prefs.setString(keyExamsJson, jsonList);
    await prefs.setInt(keyLastSynced, DateTime.now().millisecondsSinceEpoch);
  }

  static Future<List<ExamItem>> loadExamsFromPrefs() async {
    final prefs = await SharedPreferences.getInstance();
    final raw = prefs.getString(keyExamsJson);
    if (raw != null && raw.trim().isNotEmpty && raw != '[]') {
      try {
        final decoded = jsonDecode(raw) as List<dynamic>;
        final list = decoded.map((e) => ExamItem.fromJson(e as Map<String, dynamic>)).toList();
        if (list.isNotEmpty) {
          list.sort((a, b) => (a.getExamDate()?.millisecondsSinceEpoch ?? 9999999999999)
              .compareTo(b.getExamDate()?.millisecondsSinceEpoch ?? 9999999999999));
          return list;
        }
      } catch (_) {}
    }

    final verified = getVerifiedSchedule();
    await saveExamsToPrefs(verified);
    return verified;
  }

  static Future<void> clearExams() async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.remove(keyExamsJson);
  }

  /// Parses datesheet from either modern studentums.lpu.in card text or classic UMS HTML tables.
  static List<ExamItem> parseDatesheetHtml(String raw) {
    final items = <ExamItem>[];
    if (raw.trim().isEmpty) return items;

    try {
      final tagStrip = RegExp(r'<[^>]+>|&nbsp;|\t');

      // 1. First attempt: Line-by-line card format (from studentums.lpu.in)
      final cleanText = raw.replaceAll(tagStrip, '\n');
      final lines = cleanText
          .split(RegExp(r'[\r\n]+'))
          .map((l) => l.replaceAll(RegExp(r'\s+'), ' ').trim())
          .where((l) => l.isNotEmpty)
          .toList();

      final foundKeys = <String>{};

      for (var i = 0; i < lines.length; i++) {
        final line = lines[i];
        final codeMatch = RegExp(r'\b([A-Z]{2,5}\d{3,4})\b').firstMatch(line);
        if (codeMatch != null &&
            !line.contains(RegExp('Term', caseSensitive: false)) &&
            !line.contains(RegExp('Total', caseSensitive: false))) {
          final code = codeMatch.group(1)!;
          var title = line.substring(line.indexOf(code) + code.length).trim();
          if (title.startsWith('-') || title.startsWith('–')) {
            title = title.substring(1).trim();
          }
          if (title.isEmpty) title = getCourseTitle(code);

          var dateStr = '';
          var timeSlot = '09:00 AM – 12:00 PM';
          var reporting = '';
          var room = 'Seating Awaited';
          const seatNo = 'Awaited';
          var examType = 'MTE';

          final maxJ = (i + 8) < lines.length ? (i + 8) : (lines.length - 1);
          for (var j = i + 1; j <= maxJ; j++) {
            final nextLine = lines[j];
            if (j > i + 1 && RegExp(r'^[A-Z]{2,5}\d{3,4}\b').hasMatch(nextLine)) {
              break;
            }

            final dMatch = RegExp(r'\b(\d{1,2}\s+[A-Za-z]{3}\s+\d{4}|\d{1,2}[-/](?:[A-Za-z]{3}|\d{1,2})[-/]\d{2,4})\b').firstMatch(nextLine);
            if (dMatch != null && dateStr.isEmpty) {
              dateStr = dMatch.group(1)!;
            }

            final tMatch = RegExp(r'\b(\d{1,2}:\d{2}\s*[–\-]\s*\d{1,2}:\d{2}(?:\s*(?:AM|PM))?|\d{1,2}:\d{2}\s*(?:AM|PM))\b', caseSensitive: false).firstMatch(nextLine);
            if (tMatch != null && (timeSlot == '09:00 AM – 12:00 PM' || timeSlot.isEmpty)) {
              timeSlot = tMatch.group(1)!;
            }

            final rMatch = RegExp(r'Report\s+([^\]]+)', caseSensitive: false).firstMatch(nextLine);
            if (rMatch != null && reporting.isEmpty) {
              reporting = 'Report ${rMatch.group(1)!.trim()}';
              if (reporting.length > 30) reporting = reporting.substring(0, 30);
            }

            if (RegExp(r'Block\s+\d+|Room\s+\d+|\d{2}-\d{3}', caseSensitive: false).hasMatch(nextLine)) {
              room = nextLine;
            } else if (nextLine.toLowerCase().contains('awaited')) {
              room = 'Seating Awaited';
            }

            if (RegExp(r'Mid\s*Term|MTE', caseSensitive: false).hasMatch(nextLine)) {
              examType = 'MTE';
            } else if (RegExp(r'End\s*Term|ETE', caseSensitive: false).hasMatch(nextLine)) {
              examType = 'ETE';
            } else if (RegExp(r'Practical|Lab|PRAC', caseSensitive: false).hasMatch(nextLine)) {
              examType = 'PRAC';
            }
          }

          if (dateStr.isNotEmpty) {
            final key = '${code}_$dateStr';
            if (!foundKeys.contains(key)) {
              foundKeys.add(key);
              final isEvening = timeSlot.toUpperCase().contains('PM') &&
                  !timeSlot.contains('09:') &&
                  !timeSlot.contains('10:') &&
                  !timeSlot.contains('11:');

              items.add(
                ExamItem(
                  courseCode: code,
                  courseTitle: title,
                  dateStr: dateStr,
                  timeSlot: timeSlot,
                  session: isEvening ? 'Evening' : 'Morning',
                  examType: examType,
                  room: room,
                  seatNo: seatNo,
                  reportingTime: reporting,
                ),
              );
            }
          }
        }
      }

      // 2. Fallback attempt: Table row parsing (classic UMS HTML)
      if (items.isEmpty) {
        final rowRegex = RegExp(r'<tr[^>]*>([\s\S]*?)<\/tr>', caseSensitive: false);
        final cellRegex = RegExp(r'<td[^>]*>([\s\S]*?)<\/td>', caseSensitive: false);
        final rows = rowRegex.allMatches(raw).toList();

        for (final r in rows) {
          final rowContent = r.group(1) ?? '';
          final cells = cellRegex.allMatches(rowContent)
              .map((c) => (c.group(1) ?? '').replaceAll(tagStrip, ' ').replaceAll(RegExp(r'\s+'), ' ').trim())
              .where((c) => c.isNotEmpty)
              .toList();

          if (cells.length >= 3) {
            final codeIdx = cells.indexWhere((c) => RegExp(r'^[A-Z]{2,5}[0-9]{3,4}$').hasMatch(c));
            if (codeIdx != -1) {
              final code = cells[codeIdx];
              var title = (codeIdx + 1 < cells.length && !RegExp(r'.*\d{1,2}[-/].*').hasMatch(cells[codeIdx + 1]))
                  ? cells[codeIdx + 1]
                  : '';
              if (title.isEmpty) title = getCourseTitle(code);

              final dateCell = cells.firstWhere(
                (c) => RegExp(r'.*\d{1,2}[-/]([A-Za-z]{3}|\d{1,2})[-/]\d{2,4}.*').hasMatch(c) ||
                    RegExp(r'[A-Za-z]{3,9}\s+\d{1,2},?\s+\d{4}').hasMatch(c),
                orElse: () => '',
              );

              final timeCell = cells.firstWhere(
                (c) => (c.toUpperCase().contains('AM') || c.toUpperCase().contains('PM')) &&
                    RegExp(r'.*\d{1,2}:\d{2}.*').hasMatch(c),
                orElse: () => '09:00 AM – 12:00 PM',
              );

              final roomCell = cells.firstWhere(
                (c) => c.toLowerCase().contains('block') ||
                    c.toLowerCase().contains('room') ||
                    RegExp(r'\d{2}-\d{3}').hasMatch(c),
                orElse: () => 'Seating Awaited',
              );

              final seatCell = cells.firstWhere(
                (c) => c.toLowerCase().contains('desk') ||
                    c.toLowerCase().contains('seat') ||
                    RegExp(r'[A-Z]-\d{1,3}').hasMatch(c),
                orElse: () => 'Awaited',
              );

              final reportingCell = cells.firstWhere(
                (c) => c.toLowerCase().contains('report'),
                orElse: () => '',
              );

              final examType = (code.toUpperCase().endsWith('P') || title.toLowerCase().contains('practical') || title.toLowerCase().contains('lab'))
                  ? 'PRAC'
                  : (raw.toLowerCase().contains('mid term') || title.toLowerCase().contains('mid term'))
                      ? 'MTE'
                      : 'ETE';

              if (dateCell.isNotEmpty) {
                final cleanDate = RegExp(r'\b(\d{1,2}[-/]([A-Za-z]{3}|\d{1,2})[-/]\d{2,4}|[A-Za-z]{3,9}\s+\d{1,2},?\s+\d{4})\b')
                    .firstMatch(dateCell)?.group(1) ?? dateCell;

                final isEvening = timeCell.toUpperCase().contains('PM') &&
                    !timeCell.contains('09:') &&
                    !timeCell.contains('10:') &&
                    !timeCell.contains('11:');

                items.add(
                  ExamItem(
                    courseCode: code,
                    courseTitle: title,
                    dateStr: cleanDate,
                    timeSlot: timeCell,
                    session: isEvening ? 'Evening' : 'Morning',
                    examType: examType,
                    room: roomCell,
                    seatNo: seatCell,
                    reportingTime: reportingCell,
                  ),
                );
              }
            }
          }
        }
      }
    } catch (_) {}

    items.sort((a, b) => (a.getExamDate()?.millisecondsSinceEpoch ?? 9999999999999)
        .compareTo(b.getExamDate()?.millisecondsSinceEpoch ?? 9999999999999));
    return items;
  }
}
