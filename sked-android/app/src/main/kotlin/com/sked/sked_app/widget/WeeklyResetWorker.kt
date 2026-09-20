package com.sked.sked_app.widget

import android.content.Context
import androidx.work.*
import com.sked.sked_app.AttendanceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * WorkManager CoroutineWorker that runs every Sunday at 11:59 PM (23:59:00).
 * Resets all weekly timetable attendance statuses back to UNMARKED for the upcoming week
 * and reschedules itself for the subsequent Sunday.
 */
class WeeklyResetWorker(ctx: Context, params: WorkerParameters) :
    CoroutineWorker(ctx, params) {

    companion object {
        const val WORK_NAME = "SkedWeeklyResetWorker"

        /**
         * Schedule the worker to run at the upcoming Sunday at 11:59 PM.
         */
        fun schedule(context: Context) {
            val delayMs = AttendanceManager.getMillisUntilNextSunday2359()
            val req = OneTimeWorkRequestBuilder<WeeklyResetWorker>()
                .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                .addTag(WORK_NAME)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                req
            )
            val minutesUntil = delayMs / 1000 / 60
            android.util.Log.i("WeeklyResetWorker", "Weekly reset scheduled in $minutesUntil minutes (Sunday 11:59 PM)")
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            android.util.Log.i("WeeklyResetWorker", "Sunday 11:59 PM reached: performing weekly attendance reset...")
            AttendanceManager.resetWeeklyAttendance(applicationContext)

            // Re-schedule for next week's Sunday 11:59 PM
            schedule(applicationContext)

            Result.success()
        } catch (e: Exception) {
            android.util.Log.e("WeeklyResetWorker", "Error executing weekly reset", e)
            Result.retry()
        }
    }
}
