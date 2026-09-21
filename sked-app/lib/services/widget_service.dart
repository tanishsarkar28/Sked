import 'dart:convert';
import 'package:home_widget/home_widget.dart';
import '../models/class_entry.dart';
import '../models/exam_item.dart';

class WidgetService {
  static const String appGroupId = 'group.com.sked.skedApp';
  static const String iOSWidgetName = 'TimetableWidget';

  static Future<void> updateWidgetData({
    required List<ClassEntry> todayEntries,
    required bool isSunday,
    ExamItem? todayExam,
    ExamItem? tomorrowExam,
  }) async {
    try {
      await HomeWidget.setAppGroupId(appGroupId);

      // Serialize today's classes
      final classesJsonList = todayEntries.map((e) => {
        'courseCode': e.courseCode,
        'start': e.start,
        'end': e.end,
        'room': e.room,
        'type': e.type,
        'timeRange': e.timeRange,
        'teacher': e.teacher,
        'section': e.section,
      }).toList();

      await HomeWidget.saveWidgetData<String>(
        'today_entries',
        jsonEncode(classesJsonList),
      );

      await HomeWidget.saveWidgetData<bool>('is_sunday', isSunday);

      if (todayExam != null) {
        await HomeWidget.saveWidgetData<String>(
          'today_exam',
          jsonEncode({
            'courseCode': todayExam.courseCode,
            'courseTitle': todayExam.courseTitle,
            'timeSlot': todayExam.timeSlot,
            'session': todayExam.session,
            'examType': todayExam.examType,
            'room': todayExam.room,
            'seatNo': todayExam.seatNo,
            'reportingTime': todayExam.reportingTime,
            'isToday': true,
          }),
        );
      } else if (tomorrowExam != null) {
        await HomeWidget.saveWidgetData<String>(
          'today_exam',
          jsonEncode({
            'courseCode': tomorrowExam.courseCode,
            'courseTitle': tomorrowExam.courseTitle,
            'timeSlot': tomorrowExam.timeSlot,
            'session': tomorrowExam.session,
            'examType': tomorrowExam.examType,
            'room': tomorrowExam.room,
            'seatNo': tomorrowExam.seatNo,
            'reportingTime': tomorrowExam.reportingTime,
            'isToday': false,
          }),
        );
      } else {
        await HomeWidget.saveWidgetData<String?>('today_exam', null);
      }

      await HomeWidget.updateWidget(
        name: iOSWidgetName,
        iOSName: iOSWidgetName,
      );
    } catch (_) {
      // Best-effort widget update
    }
  }
}
