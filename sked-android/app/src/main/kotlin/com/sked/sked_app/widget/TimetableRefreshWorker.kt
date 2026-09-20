package com.sked.sked_app.widget

import android.content.Context
import android.content.SharedPreferences
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.work.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

/**
 * WorkManager CoroutineWorker that:
 *   1. Updates today's entries from locally cached full-week timetable (works 100% offline)
 *   2. Optionally syncs with backend if KEY_BACKEND_URL is configured
 *   3. Triggers a Glance widget update
 *
 * Schedule at app start (or from MainActivity) with:
 *   TimetableRefreshWorker.schedule(context)
 */
class TimetableRefreshWorker(ctx: Context, params: WorkerParameters) :
    CoroutineWorker(ctx, params) {

    companion object {
        private const val WORK_TAG = "SkedTimetableRefresh"
        const val KEY_BACKEND_URL  = "sked_backend_url"
        const val KEY_USER_ID      = "sked_user_id"

        /**
         * Enqueue a periodic refresh every 15 minutes (Android system minimum).
         * Runs without network constraint so local date transitions update offline.
         */
        fun schedule(context: Context) {
            val req = PeriodicWorkRequestBuilder<TimetableRefreshWorker>(15, TimeUnit.MINUTES)
                .addTag(WORK_TAG)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_TAG, ExistingPeriodicWorkPolicy.UPDATE, req
            )
        }

        /** Trigger an immediate one-off refresh. */
        fun runNow(context: Context) {
            val req = OneTimeWorkRequestBuilder<TimetableRefreshWorker>().addTag(WORK_TAG).build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                "ImmediateWidgetRefresh",
                ExistingWorkPolicy.REPLACE,
                req
            )
        }

        /** Directly update all widget instances from any coroutine context. */
        suspend fun updateWidget(context: Context) {
            try {
                val manager = GlanceAppWidgetManager(context)
                val ids = manager.getGlanceIds(TimetableWidget::class.java)
                ids.forEach { id ->
                    TimetableWidget().update(context, id)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val prefs = applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val backendUrl = prefs.getString(KEY_BACKEND_URL, "") ?: ""
            val userId = prefs.getString(KEY_USER_ID, null)

            // 1. Try optional backend fetch if backend URL is configured
            var backendUpdated = false
            if (backendUrl.isNotBlank()) {
                try {
                    val endpoint = if (!userId.isNullOrBlank()) {
                        "$backendUrl/api/timetable/today?userId=${java.net.URLEncoder.encode(userId, "UTF-8")}"
                    } else {
                        "$backendUrl/api/timetable/today"
                    }

                    val url = URL(endpoint)
                    val conn = url.openConnection() as HttpURLConnection
                    conn.requestMethod = "GET"
                    conn.connectTimeout = 5_000
                    conn.readTimeout = 8_000

                    if (conn.responseCode == 200) {
                        val body = conn.inputStream.bufferedReader().readText()
                        val obj = JSONObject(body)
                        val entries = obj.optJSONArray("entries") ?: JSONArray()
                        prefs.edit().putString(KEY_TODAY, entries.toString()).apply()
                        backendUpdated = true
                    }
                } catch (_: Exception) {
                    // Backend unavailable — fall through to local cache
                }
            }

            // 2. If backend wasn't used/updated, update from local cached week timetable
            if (!backendUpdated) {
                val allEntries = com.sked.sked_app.TimetableParser.loadFromPrefs(applicationContext)
                if (allEntries.isNotEmpty()) {
                    val todayName = com.sked.sked_app.TimetableParser.todayName()
                    val todayEntries = com.sked.sked_app.TimetableParser.filterByDay(allEntries, todayName)
                    val todayJson = com.sked.sked_app.TimetableParser.entriesToJsonArray(todayEntries)
                    prefs.edit().putString(KEY_TODAY, todayJson.toString()).apply()
                }
            }

            // 2b. Passive weekly attendance check (if new week rolled over)
            try {
                com.sked.sked_app.AttendanceManager.checkAndResetWeekly(applicationContext)
            } catch (_: Exception) {}

            // 2c. Attempt to refresh live attendance via saved mobile API token
            try {
                com.sked.sked_app.AttendanceManager.refreshFromMobileApi(applicationContext)
            } catch (_: Exception) {}

            // 3. Trigger Glance widget redraw
            val manager = GlanceAppWidgetManager(applicationContext)
            val ids = manager.getGlanceIds(TimetableWidget::class.java)
            ids.forEach { id ->
                TimetableWidget().update(applicationContext, id)
            }

            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.success()
        }
    }
}
