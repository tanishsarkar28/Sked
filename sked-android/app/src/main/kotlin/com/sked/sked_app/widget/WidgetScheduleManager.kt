package com.sked.sked_app.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.sked.sked_app.TimetableParser
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Manages exact, dynamic widget reload timings tailored specifically to
 * each student's unique timetable start and end times.
 *
 * Instead of periodic polling, this calculates the exact next transition
 * (class start, class end, or midnight roll-over) and arms an AlarmManager
 * exact alarm so the widget refreshes at the exact minute any class begins or ends.
 */
object WidgetScheduleManager {

    private const val TAG = "WidgetScheduleManager"
    const val ACTION_WIDGET_SCHEDULE_UPDATE = "com.sked.sked_app.widget.ACTION_WIDGET_SCHEDULE_UPDATE"
    private const val ALARM_REQUEST_CODE = 4040
    const val KEY_NEXT_SCHEDULED_RELOAD = "next_widget_reload_timestamp"

    /**
     * Parses time strings in 24-hr ("10:20", "13:40") or 12-hr ("10:20 AM", "1:40 PM") format.
     */
    fun parseHourMinute(timeStr: String): Pair<Int, Int>? {
        if (timeStr.isBlank()) return null
        val clean = timeStr.trim().uppercase(Locale.US)
        val isPm = clean.contains("PM")
        val isAm = clean.contains("AM")
        val numOnly = clean.replace("AM", "").replace("PM", "").trim()
        val parts = numOnly.split(":")
        if (parts.size >= 2) {
            var h = parts[0].trim().toIntOrNull() ?: return null
            val m = parts[1].trim().take(2).toIntOrNull() ?: return null
            if (isPm && h < 12) h += 12
            if (isAm && h == 12) h = 0
            return Pair(h, m)
        }
        return null
    }

    /**
     * Calculates all upcoming class transitions for today and schedules
     * the AlarmManager to wake up and update the widget at the earliest boundary.
     */
    fun scheduleNextUpdate(context: Context) {
        try {
            val allEntries = TimetableParser.loadFromPrefs(context)
            val todayName = TimetableParser.todayName()
            val todayEntries = TimetableParser.filterByDay(allEntries, todayName)

            val now = System.currentTimeMillis()
            val candidateMillis = mutableListOf<Long>()

            // 1. Gather all class start and end times for today
            todayEntries.forEach { entry ->
                // Start time transition (Widget flips to "Live / Happening Now")
                val startHm = parseHourMinute(entry.start)
                if (startHm != null) {
                    val cal = Calendar.getInstance().apply {
                        set(Calendar.HOUR_OF_DAY, startHm.first)
                        set(Calendar.MINUTE, startHm.second)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    if (cal.timeInMillis > now + 1500L) {
                        candidateMillis.add(cal.timeInMillis)
                    }
                }

                // End time transition (Widget flips to "Break / Next Class")
                val endHm = parseHourMinute(entry.end.ifBlank { entry.start })
                if (endHm != null) {
                    val cal = Calendar.getInstance().apply {
                        set(Calendar.HOUR_OF_DAY, endHm.first)
                        set(Calendar.MINUTE, endHm.second)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    if (cal.timeInMillis > now + 1500L) {
                        candidateMillis.add(cal.timeInMillis)
                    }
                }
            }

            // 2. Midnight roll-over transition (Switches to the new day's timetable)
            val midnightCal = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 5) // 5 seconds past midnight
                set(Calendar.MILLISECOND, 0)
            }
            candidateMillis.add(midnightCal.timeInMillis)

            // 3. Select the earliest upcoming transition
            val nextTriggerMillis = candidateMillis.minOrNull() ?: midnightCal.timeInMillis

            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val intent = Intent(context, WidgetUpdateAlarmReceiver::class.java).apply {
                action = ACTION_WIDGET_SCHEDULE_UPDATE
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                ALARM_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // 4. Arm exact alarm with Doze mode immunity
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (alarmManager.canScheduleExactAlarms()) {
                        alarmManager.setExactAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            nextTriggerMillis,
                            pendingIntent
                        )
                    } else {
                        alarmManager.setAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            nextTriggerMillis,
                            pendingIntent
                        )
                    }
                } else {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        nextTriggerMillis,
                        pendingIntent
                    )
                }
            } catch (se: SecurityException) {
                Log.w(TAG, "Exact alarm permission restricted; falling back to setAndAllowWhileIdle: ${se.message}")
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    nextTriggerMillis,
                    pendingIntent
                )
            }

            // Record timestamp in preferences for diagnostics
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putLong(KEY_NEXT_SCHEDULED_RELOAD, nextTriggerMillis).apply()

            val formatted = SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss", Locale.US).format(Date(nextTriggerMillis))
            Log.i(TAG, "Dynamic widget reload scheduled for: $formatted (${todayEntries.size} classes today)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule next widget reload: ${e.message}", e)
        }
    }
}
