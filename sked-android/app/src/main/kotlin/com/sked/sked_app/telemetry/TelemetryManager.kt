package com.sked.sked_app.telemetry

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class TelemetrySnapshot(
    val totalInstalls: Int = 0,
    val dauToday: Int = 0,
    val widgetSyncsToday: Int = 0,
    val batch2024: Int = 0,
    val batch2023: Int = 0,
    val batch2022: Int = 0,
    val batchOther: Int = 0
)

object TelemetryManager {

    private const val TAG = "TelemetryManager"
    private const val PREFS_NAME = "SkedTelemetryPrefs"
    private const val KEY_INSTALLED = "has_registered_install"
    private const val KEY_LAST_DAU_DATE = "last_dau_date"
    private const val KEY_LAST_SYNC_HOUR = "last_sync_hour"
    private const val KEY_BATCH_REGISTERED = "has_registered_batch"

    private const val BASE_URL = "https://abacus.jasoncameron.dev"
    private const val NAMESPACE = "sked_official_app"

    private fun getTodayDateString(): String {
        return SimpleDateFormat("yyyy_MM_dd", Locale.US).format(Date())
    }

    private fun pingCounter(key: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = URL("$BASE_URL/hit/$NAMESPACE/$key")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 6000
                    readTimeout = 6000
                    requestMethod = "GET"
                    setRequestProperty("User-Agent", "Sked-Telemetry")
                }
                conn.responseCode
                conn.disconnect()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to ping counter $key: ${e.message}")
            }
        }
    }

    private suspend fun fetchCounter(key: String): Int = withContext(Dispatchers.IO) {
        try {
            val url = URL("$BASE_URL/get/$NAMESPACE/$key")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 6000
                readTimeout = 6000
                requestMethod = "GET"
                setRequestProperty("User-Agent", "Sked-Telemetry")
            }
            if (conn.responseCode in 200..299) {
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()
                JSONObject(body).optInt("value", 0)
            } else {
                conn.disconnect()
                0
            }
        } catch (_: Exception) {
            0
        }
    }

    /**
     * Records install on first launch.
     */
    fun recordInstallIfNeeded(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_INSTALLED, false)) {
            pingCounter("installs")
            prefs.edit().putBoolean(KEY_INSTALLED, true).apply()
            Log.i(TAG, "Lifetime install recorded.")
        }
    }

    /**
     * Records Daily Active User (DAU) once per calendar day.
     */
    fun recordDailyActiveUser(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val today = getTodayDateString()
        val lastDate = prefs.getString(KEY_LAST_DAU_DATE, null)

        if (lastDate != today) {
            pingCounter("dau_$today")
            prefs.edit().putString(KEY_LAST_DAU_DATE, today).apply()
            Log.i(TAG, "DAU recorded for $today.")
        }
    }

    /**
     * Records widget background refresh (throttled to once per hour per device).
     */
    fun recordWidgetSync(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val currentHour = System.currentTimeMillis() / (1000 * 60 * 60)
        val lastSyncHour = prefs.getLong(KEY_LAST_SYNC_HOUR, 0L)

        if (currentHour > lastSyncHour) {
            val today = getTodayDateString()
            pingCounter("syncs_$today")
            prefs.edit().putLong(KEY_LAST_SYNC_HOUR, currentHour).apply()
            Log.i(TAG, "Widget sync recorded for $today.")
        }
    }

    /**
     * Records student cohort/batch once per device based on Registration Number.
     * E.g. 12407229 -> "24" -> batch_2024
     */
    fun recordStudentBatch(context: Context, regNo: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_BATCH_REGISTERED, false) && regNo.length >= 3) {
            val trimmed = regNo.trim()
            val batchKey = when {
                trimmed.startsWith("124") || trimmed.contains("K24", ignoreCase = true) -> "batch_2024"
                trimmed.startsWith("123") || trimmed.contains("K23", ignoreCase = true) -> "batch_2023"
                trimmed.startsWith("122") || trimmed.contains("K22", ignoreCase = true) -> "batch_2022"
                else -> "batch_other"
            }
            pingCounter(batchKey)
            prefs.edit().putBoolean(KEY_BATCH_REGISTERED, true).apply()
            Log.i(TAG, "Student cohort recorded: $batchKey")
        }
    }

    /**
     * Fetches all telemetry metrics concurrently for the Admin Console.
     */
    suspend fun fetchFullTelemetry(): TelemetrySnapshot = withContext(Dispatchers.IO) {
        val today = getTodayDateString()

        val installsDeferred = async { fetchCounter("installs") }
        val dauDeferred = async { fetchCounter("dau_$today") }
        val syncsDeferred = async { fetchCounter("syncs_$today") }
        val b2024Deferred = async { fetchCounter("batch_2024") }
        val b2023Deferred = async { fetchCounter("batch_2023") }
        val b2022Deferred = async { fetchCounter("batch_2022") }
        val bOtherDeferred = async { fetchCounter("batch_other") }

        TelemetrySnapshot(
            totalInstalls = installsDeferred.await(),
            dauToday = dauDeferred.await(),
            widgetSyncsToday = syncsDeferred.await(),
            batch2024 = b2024Deferred.await(),
            batch2023 = b2023Deferred.await(),
            batch2022 = b2022Deferred.await(),
            batchOther = bOtherDeferred.await()
        )
    }
}
