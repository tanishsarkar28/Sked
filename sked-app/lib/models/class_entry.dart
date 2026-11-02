/// Timing states for departure-board class cards, matching native Android app.
enum ClassTimingState {
  onGoing,
  upcoming,
  pending,
  over,
}

/// Represents a single class/lecture entry in Sked.
class ClassEntry {
  final String day;
  final String timeRange;
  final String start;
  final String end;
  final String room;
  final String courseCode;
  final String type; // "Lecture" | "Practical" | "Tutorial"
  final String teacher;
  final String section;
  final String group;
  final String description;

  const ClassEntry({
    required this.day,
    required this.timeRange,
    required this.start,
    required this.end,
    required this.room,
    required this.courseCode,
    required this.type,
    required this.teacher,
    required this.section,
    required this.group,
    required this.description,
  });

  factory ClassEntry.fromJson(Map<String, dynamic> json) {
    return ClassEntry(
      day: json['day'] as String? ?? '',
      timeRange: json['timeRange'] as String? ?? '',
      start: json['start'] as String? ?? '',
      end: json['end'] as String? ?? '',
      room: json['room'] as String? ?? '',
      courseCode: json['courseCode'] as String? ?? '',
      type: json['type'] as String? ?? 'Lecture',
      teacher: json['teacher'] as String? ?? '',
      section: json['section'] as String? ?? '',
      group: json['group'] as String? ?? '',
      description: json['description'] as String? ?? '',
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'day': day,
      'timeRange': timeRange,
      'start': start,
      'end': end,
      'room': room,
      'courseCode': courseCode,
      'type': type,
      'teacher': teacher,
      'section': section,
      'group': group,
      'description': description,
    };
  }

  static int parseMinutes(String timeStr) {
    final clean = timeStr.trim().toUpperCase();
    final isPm = clean.contains('PM') || clean.contains('P.M.');
    final isAm = clean.contains('AM') || clean.contains('A.M.');
    final timeOnly = clean.replaceAll(RegExp(r'[^0-9:]'), '');
    final parts = timeOnly.split(':');
    if (parts.length >= 2) {
      var h = int.tryParse(parts[0].trim()) ?? 0;
      final mStr = parts[1].trim();
      final m = int.tryParse(mStr.length >= 2 ? mStr.substring(0, 2) : mStr) ?? 0;
      if (isPm && h < 12) {
        h += 12;
      } else if (isAm && h == 12) {
        h = 0;
      }
      return h * 60 + m;
    }
    return 0;
  }

  int get startMinutes => parseMinutes(start);

  int get endMinutes {
    final val = parseMinutes(end);
    if (val > 0) return val;
    final s = startMinutes;
    return s > 0 ? s + 50 : 0;
  }

  /// Returns true if this class is identified as a makeup, extra, or adjustment class.
  bool get isMakeup {
    final t = type.toLowerCase();
    final d = description.toLowerCase();
    return t.contains('make') ||
        t.contains('adjust') ||
        t.contains('extra') ||
        d.contains('make-up') ||
        d.contains('makeup') ||
        d.contains('adjustment') ||
        d.contains('extra class') ||
        d.contains('make up');
  }

  /// Unique key for timetable diffing and notification deduplication.
  String get uniqueKey =>
      '${courseCode.toUpperCase()}_${day.toLowerCase()}_${start.trim()}_${end.trim()}_${room.toLowerCase()}';

  /// Returns true if this class is currently ongoing at [nowMinutes].
  bool isOngoingNow(int nowMinutes) {
    final s = startMinutes;
    final e = endMinutes;
    return s > 0 && e > 0 && nowMinutes >= s && nowMinutes <= e;
  }

  @override
  String toString() => 'ClassEntry($courseCode @ $start–$end, $day)';
}
