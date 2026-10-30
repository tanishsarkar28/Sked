package com.sked.sked_app.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.sked.sked_app.TimetableParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Triggered by AlarmManager precisely when a class starts, ends,
 * or when the date rolls over at midnight.
 *
 * Runs 100% offline using locally cached data.
 */
class WidgetUpdateAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.i("WidgetUpdateAlarm", "Alarm triggered! Updating widget on-device...")

                // 1. Refresh today's entries from local cache in case the day rolled over
                val allEntries = TimetableParser.loadFromPrefs(context)
                if (allEntries.isNotEmpty()) {
                    val todayName = TimetableParser.todayName()
                    val todayEntries = TimetableParser.filterByDay(allEntries, todayName)
                    val todayJson = TimetableParser.entriesToJsonArray(todayEntries)
                    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    prefs.edit().putString(KEY_TODAY, todayJson.toString()).apply()
                }

                // 2. Trigger instant Glance widget redraw
                TimetableRefreshWorker.updateWidget(context)

                // 3. Immediately arm the exact alarm for the very next class transition
                WidgetScheduleManager.scheduleNextUpdate(context)
            } catch (e: Exception) {
                Log.e("WidgetUpdateAlarm", "Error handling widget reload alarm: ${e.message}", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
