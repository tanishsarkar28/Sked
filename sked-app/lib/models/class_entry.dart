/// Represents a single class/lecture entry returned from the Sked backend.
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
      type: json['type'] as String? ?? '',
      teacher: json['teacher'] as String? ?? '',
      section: json['section'] as String? ?? '',
      group: json['group'] as String? ?? '',
      description: json['description'] as String? ?? '',
    );
  }

  /// Returns true if this class is currently ongoing at [now].
  bool isOngoing(DateTime now) {
    final todayDate = DateTime(now.year, now.month, now.day);
    final startParts = start.split(':');
    final endParts = end.split(':');
    if (startParts.length < 2 || endParts.length < 2) return false;
    final startDt = todayDate.add(Duration(
      hours: int.tryParse(startParts[0]) ?? 0,
      minutes: int.tryParse(startParts[1]) ?? 0,
    ));
    final endDt = todayDate.add(Duration(
      hours: int.tryParse(endParts[0]) ?? 0,
      minutes: int.tryParse(endParts[1]) ?? 0,
    ));
    return now.isAfter(startDt) && now.isBefore(endDt);
  }

  /// Returns true if this class is upcoming (starts in the future) at [now].
  bool isUpcoming(DateTime now) {
    final todayDate = DateTime(now.year, now.month, now.day);
    final startParts = start.split(':');
    if (startParts.length < 2) return false;
    final startDt = todayDate.add(Duration(
      hours: int.tryParse(startParts[0]) ?? 0,
      minutes: int.tryParse(startParts[1]) ?? 0,
    ));
    return now.isBefore(startDt);
  }

  @override
  String toString() => 'ClassEntry($courseCode @ $start–$end, $day)';
}
