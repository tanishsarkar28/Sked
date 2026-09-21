enum ExamStatus {
  today,
  tomorrow,
  upcoming,
  completed,
}

class ExamItem {
  final String courseCode;
  final String courseTitle;
  final String dateStr;
  final String dayName;
  final String timeSlot;
  final String session;
  final String examType;
  final String room;
  final String seatNo;
  final String reportingTime;

  const ExamItem({
    required this.courseCode,
    this.courseTitle = '',
    required this.dateStr,
    this.dayName = '',
    required this.timeSlot,
    this.session = 'Morning',
    this.examType = 'ETE',
    this.room = '',
    this.seatNo = '',
    this.reportingTime = '',
  });

  factory ExamItem.fromJson(Map<String, dynamic> json) {
    return ExamItem(
      courseCode: json['courseCode'] as String? ?? '',
      courseTitle: json['courseTitle'] as String? ?? '',
      dateStr: json['dateStr'] as String? ?? '',
      dayName: json['dayName'] as String? ?? '',
      timeSlot: json['timeSlot'] as String? ?? '09:00 AM – 12:00 PM',
      session: json['session'] as String? ?? 'Morning',
      examType: json['examType'] as String? ?? 'MTE',
      room: json['room'] as String? ?? '',
      seatNo: json['seatNo'] as String? ?? '',
      reportingTime: json['reportingTime'] as String? ?? '',
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'courseCode': courseCode,
      'courseTitle': courseTitle,
      'dateStr': dateStr,
      'dayName': dayName,
      'timeSlot': timeSlot,
      'session': session,
      'examType': examType,
      'room': room,
      'seatNo': seatNo,
      'reportingTime': reportingTime,
    };
  }

  static const _monthMap = {
    'jan': 1, 'feb': 2, 'mar': 3, 'apr': 4, 'may': 5, 'jun': 6,
    'jul': 7, 'aug': 8, 'sep': 9, 'oct': 10, 'nov': 11, 'dec': 12
  };

  /// Parses diverse date formats: e.g. "01 Oct 2026", "2026-10-01", "01-10-2026", "1 Oct 2026"
  DateTime? getExamDate() {
    final raw = dateStr.trim();
    if (raw.isEmpty) return null;

    // ISO format: yyyy-MM-dd
    final iso = DateTime.tryParse(raw);
    if (iso != null) {
      return DateTime(iso.year, iso.month, iso.day);
    }

    // e.g. "01 Oct 2026" or "1 Oct 2026" or "01-Oct-2026"
    final namedMatch = RegExp(r'^(\d{1,2})[\s\-/]+([A-Za-z]{3,9})[\s\-/]+(\d{2,4})$').firstMatch(raw);
    if (namedMatch != null) {
      final day = int.tryParse(namedMatch.group(1)!) ?? 1;
      final mStr = namedMatch.group(2)!.toLowerCase().substring(0, 3);
      final month = _monthMap[mStr] ?? 1;
      var year = int.tryParse(namedMatch.group(3)!) ?? DateTime.now().year;
      if (year < 100) year += 2000;
      return DateTime(year, month, day);
    }

    // e.g. "Oct 01, 2026"
    final namedMatch2 = RegExp(r'^([A-Za-z]{3,9})\s+(\d{1,2}),?\s+(\d{2,4})$').firstMatch(raw);
    if (namedMatch2 != null) {
      final mStr = namedMatch2.group(1)!.toLowerCase().substring(0, 3);
      final month = _monthMap[mStr] ?? 1;
      final day = int.tryParse(namedMatch2.group(2)!) ?? 1;
      var year = int.tryParse(namedMatch2.group(3)!) ?? DateTime.now().year;
      if (year < 100) year += 2000;
      return DateTime(year, month, day);
    }

    // e.g. "01-10-2026" or "01/10/2026"
    final dmyMatch = RegExp(r'^(\d{1,2})[-/](\d{1,2})[-/](\d{2,4})$').firstMatch(raw);
    if (dmyMatch != null) {
      final day = int.tryParse(dmyMatch.group(1)!) ?? 1;
      final month = int.tryParse(dmyMatch.group(2)!) ?? 1;
      var year = int.tryParse(dmyMatch.group(3)!) ?? DateTime.now().year;
      if (year < 100) year += 2000;
      return DateTime(year, month, day);
    }

    return null;
  }

  String formattedDate() {
    final dt = getExamDate();
    if (dt == null) return dateStr;
    const weekdays = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'];
    const months = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
    final wd = weekdays[dt.weekday - 1];
    final mo = months[dt.month - 1];
    final dayPad = dt.day.toString().padLeft(2, '0');
    return '$wd, $dayPad $mo ${dt.year}';
  }

  int daysUntil() {
    final examDate = getExamDate();
    if (examDate == null) return 999;
    final now = DateTime.now();
    final todayMidnight = DateTime(now.year, now.month, now.day);
    return examDate.difference(todayMidnight).inDays;
  }

  ExamStatus getStatus() {
    final days = daysUntil();
    if (days < 0) return ExamStatus.completed;
    if (days == 0) return ExamStatus.today;
    if (days == 1) return ExamStatus.tomorrow;
    return ExamStatus.upcoming;
  }

  String statusLabel() {
    final days = daysUntil();
    if (days < 0) return 'OVER';
    if (days == 0) return 'TODAY';
    if (days == 1) return 'TOMORROW';
    return 'IN $days DAYS';
  }
}
