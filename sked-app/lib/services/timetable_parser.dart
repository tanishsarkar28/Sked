import 'dart:convert';
import 'package:shared_preferences/shared_preferences.dart';
import '../models/class_entry.dart';

/// On-device timetable parser — Dart port of TimetableParser.kt.
///
/// Parses the HTML fragment returned by the ASP.NET PageMethod:
///   POST https://ums.lpu.in/lpuums/frmMyCurrentTimeTable.aspx/GetTimeTable
class TimetableParser {
  static const String prefsAllEntries = 'all_entries';
  static const String prefsToday = 'sked_today_entries';
  static const String prefsUserId = 'sked_user_id';

  static const Map<String, String> typeLabels = {
    'L': 'Lecture',
    'P': 'Practical',
    'T': 'Tutorial',
  };

  static const List<String> dayNames = [
    'Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday'
  ];

  static final RegExp _openPopupRegex = RegExp(
    r'openPopup\(\s*"((?:[^"\\]|\\.)*)"\s*,\s*"((?:[^"\\]|\\.)*)"\s*,\s*"((?:[^"\\]|\\.)*)"\s*,\s*"((?:[^"\\]|\\.)*)"\s*,\s*"((?:[^"\\]|\\.)*)"\s*,\s*"((?:[^"\\]|\\.)*)"\s*\)',
  );

  static final RegExp _teacherRegex = RegExp(r'Teacher:\s*\d+::(.+)$', caseSensitive: false);
  static final RegExp _sectionRegex = RegExp(r'S:([^\s/]+)');
  static final RegExp _groupRegex = RegExp(r'G:([^\s/]+)');

  static String parseTeacher(String description) {
    final m = _teacherRegex.firstMatch(description);
    return m?.group(1)?.trim() ?? 'Unknown';
  }

  static String parseSection(String description) {
    final m = _sectionRegex.firstMatch(description);
    return m?.group(1)?.trim() ?? '';
  }

  static String parseGroup(String description) {
    final m = _groupRegex.firstMatch(description);
    return m?.group(1)?.trim() ?? '';
  }

  static (String start, String end) parseTimeRange(String range) {
    final parts = range.split('-');
    final start = parts.isNotEmpty ? parts[0].trim() : '';
    final end = parts.length > 1 ? parts[1].trim() : '';
    return (start, end);
  }

  static String parseType(String code) {
    return typeLabels[code] ?? code;
  }

  static String todayName() {
    final dt = DateTime.now();
    // DateTime weekday: 1 = Monday, ..., 7 = Sunday
    // Map to dayNames array where 0 = Sunday, 1 = Monday, ...
    final sundayZeroIndex = dt.weekday % 7;
    return dayNames[sundayZeroIndex];
  }

  /// Parses the raw response from GetTimeTable (JSON string or HTML).
  static List<ClassEntry> parse(String rawResponse) {
    final html = extractHtml(rawResponse);
    if (html.trim().isEmpty) return [];

    // Unescape HTML entities so openPopup(&quot;...&quot;) becomes openPopup("...")
    final normalized = html
        .replaceAll('&quot;', '"')
        .replaceAll('&#39;', "'")
        .replaceAll('&amp;', '&');

    final entries = <ClassEntry>[];

    for (final match in _openPopupRegex.allMatches(normalized)) {
      final description = match.group(1) ?? '';
      final timeRange = match.group(2) ?? '';
      final day = match.group(3) ?? '';
      final room = match.group(4) ?? '';
      final courseCode = match.group(5) ?? '';
      final typeCode = match.group(6) ?? '';

      final (start, end) = parseTimeRange(timeRange);

      entries.add(
        ClassEntry(
          day: day.trim(),
          timeRange: timeRange.trim(),
          start: start,
          end: end,
          room: room.trim(),
          courseCode: courseCode.trim(),
          type: parseType(typeCode.trim()),
          teacher: parseTeacher(description),
          section: parseSection(description),
          group: parseGroup(description),
          description: description.trim(),
        ),
      );
    }

    return entries;
  }

  /// Extracts HTML string from possible response formats (JSON object, double encoded, raw HTML).
  static String extractHtml(String rawResponse) {
    var trimmed = rawResponse.trim();

    // Handle double-encoded JSON string from WebView evaluateJavascript
    if (trimmed.startsWith('"') && trimmed.endsWith('"')) {
      try {
        final decoded = jsonDecode('{"v":$trimmed}') as Map<String, dynamic>;
        trimmed = (decoded['v'] as String? ?? '').trim();
      } catch (_) {}
    }

    // If it starts with '<', it's already HTML
    if (trimmed.startsWith('<')) return trimmed;

    // Try to parse as JSON
    try {
      final decoded = jsonDecode(trimmed);
      if (decoded is Map<String, dynamic>) {
        if (decoded.containsKey('d') && decoded['d'] is String) {
          return decoded['d'] as String;
        }
      }
      return trimmed;
    } catch (_) {
      return trimmed;
    }
  }

  /// Filter entries for a specific day (case-insensitive), sorted by start time.
  static List<ClassEntry> filterByDay(List<ClassEntry> entries, String dayName) {
    final filtered = entries.where((e) => e.day.toLowerCase() == dayName.toLowerCase()).toList();
    filtered.sort((a, b) => a.start.compareTo(b.start));
    return filtered;
  }

  /// Group all entries by day name. Each day's list is sorted by start time.
  static Map<String, List<ClassEntry>> groupByDay(List<ClassEntry> entries) {
    final map = <String, List<ClassEntry>>{};
    for (final e in entries) {
      map.putIfAbsent(e.day, () => []).add(e);
    }
    for (final day in map.keys) {
      map[day]!.sort((a, b) => a.start.compareTo(b.start));
    }
    return map;
  }

  // ── SharedPreferences persistence ────────────────────────────────────────

  static Future<void> saveToPrefs(List<ClassEntry> entries, String userId) async {
    final prefs = await SharedPreferences.getInstance();
    final allJson = jsonEncode(entries.map((e) => e.toJson()).toList());
    final todayEntries = filterByDay(entries, todayName());
    final todayJson = jsonEncode(todayEntries.map((e) => e.toJson()).toList());

    await prefs.setString(prefsAllEntries, allJson);
    await prefs.setString(prefsToday, todayJson);
    await prefs.setString(prefsUserId, userId);
  }

  static Future<List<ClassEntry>> loadFromPrefs() async {
    final prefs = await SharedPreferences.getInstance();
    final raw = prefs.getString(prefsAllEntries);
    if (raw == null || raw.trim().isEmpty) return [];

    try {
      final decoded = jsonDecode(raw) as List<dynamic>;
      return decoded.map((e) => ClassEntry.fromJson(e as Map<String, dynamic>)).toList();
    } catch (_) {
      return [];
    }
  }

  static Future<List<ClassEntry>> loadTodayFromPrefs() async {
    final prefs = await SharedPreferences.getInstance();
    final raw = prefs.getString(prefsToday);
    if (raw == null || raw.trim().isEmpty) return [];

    try {
      final decoded = jsonDecode(raw) as List<dynamic>;
      return decoded.map((e) => ClassEntry.fromJson(e as Map<String, dynamic>)).toList();
    } catch (_) {
      return [];
    }
  }
}
