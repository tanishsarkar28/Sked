package com.sked.sked_app

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.GlanceAppWidgetManager
import com.sked.sked_app.widget.TimetableWidget
import com.sked.sked_app.widget.TimetableWidgetReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class AttendanceStatus {
    UNMARKED,
    PRESENT,
    ABSENT,
    DUTY_LEAVE
}

data class AttendanceHistoryItem(
    val dateKey: String,
    val formattedDate: String,
    val dayOfWeek: String,
    val timeSlot: String,
    val status: AttendanceStatus
)

data class WeekAttendanceSummary(
    val attendedCount: Int = 0,
    val absentCount: Int = 0,
    val dutyLeaveCount: Int = 0,
    val overallPercentage: Double = 100.0
)

data class CourseAttendance(
    val courseCode: String,
    val courseName: String = "",
    val attended: Int = 0,
    val delivered: Int = 0,
    val percentage: Double = 0.0,
    val dutyLeave: Int = 0
) {

    /**
     * How many consecutive classes must be attended to reach 75%
     */
    val neededToAttend: Int
        get() {
            if (delivered == 0 || percentage >= 75.0) return 0
            val effectiveAttended = attended + dutyLeave
            val req = 3 * delivered - 4 * effectiveAttended
            return req.coerceAtLeast(1)
        }
}

object AttendanceManager {
    private const val PREFS_NAME = "sked_attendance_prefs"
    private const val KEY_COURSES = "course_attendance_map"
    private const val KEY_LAST_RESET_WEEK = "last_reset_week_start"
    private const val KEY_LAST_RESET_TIME = "last_weekly_reset_time"

    fun getTodayKey(): String {
        return SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
    }

    fun parseDateToKey(dateStr: String): String {
        val trimmed = dateStr.trim()
        if (trimmed.isBlank()) return ""

        // 1. /Date(1789500000000+0530)/ or /Date(1789500000000)/
        val mEpoch = Regex("""/Date\((\d+)(?:[+-]\d+)?\)/""").find(trimmed)
        if (mEpoch != null) {
            val epoch = mEpoch.groupValues[1].toLongOrNull()
            if (epoch != null) {
                return SimpleDateFormat("yyyyMMdd", Locale.US).format(Date(epoch))
            }
        }

        // 2. ISO yyyy-MM-dd
        val mIso = Regex("""(\d{4})-(\d{1,2})-(\d{1,2})""").find(trimmed)
        if (mIso != null) {
            val y = mIso.groupValues[1].toInt()
            val m = mIso.groupValues[2].toInt()
            val d = mIso.groupValues[3].toInt()
            return String.format(Locale.US, "%04d%02d%02d", y, m, d)
        }

        // 3. dd-MMM-yyyy e.g. 16-Sep-2026 or 16-SEP-2026
        try {
            val sdfMmm = SimpleDateFormat("dd-MMM-yyyy", Locale.US)
            val d = sdfMmm.parse(trimmed)
            if (d != null) return SimpleDateFormat("yyyyMMdd", Locale.US).format(d)
        } catch (_: Exception) {}

        // 4. MM/dd/yyyy or dd/MM/yyyy
        val mSlash = Regex("""(\d{1,2})/(\d{1,2})/(\d{4})""").find(trimmed)
        if (mSlash != null) {
            val p1 = mSlash.groupValues[1].toInt()
            val p2 = mSlash.groupValues[2].toInt()
            val year = mSlash.groupValues[3].toInt()
            val (month, day) = if (p1 > 12) {
                Pair(p2, p1) // p1 was day (>12), p2 is month
            } else {
                Pair(p1, p2) // standard UMS format: MM/dd/yyyy
            }
            return String.format(Locale.US, "%04d%02d%02d", year, month, day)
        }

        return ""
    }

    fun parseStartTo24h(timeStr: String): String {
        val m = Regex("""(\d{1,2}):(\d{2})\s*(?:-\s*(\d{1,2}):(\d{2}))?\s*(AM|PM)?""", RegexOption.IGNORE_CASE).find(timeStr)
            ?: return timeStr.take(5).replace(" ", "")
        var hour = m.groupValues[1].toIntOrNull() ?: 0
        val minute = m.groupValues[2]
        val ampm = m.groupValues[5].uppercase()
        if (ampm == "PM" && hour < 12) {
            hour += 12
        } else if (ampm == "AM" && hour == 12) {
            hour = 0
        }
        return String.format(Locale.US, "%02d:%s", hour, minute)
    }

    /**
     * Map a day name ("Monday", "Tue", "Wednesday", etc.) to that day's yyyyMMdd dateKey
     * in the active academic week (Monday to Sunday).
     */
    fun getDateKeyForDay(dayName: String): String {
        val targetOffset = when (dayName.trim().lowercase()) {
            "monday", "mon"       -> 0
            "tuesday", "tue"      -> 1
            "wednesday", "wed"    -> 2
            "thursday", "thu"     -> 3
            "friday", "fri"       -> 4
            "saturday", "sat"     -> 5
            "sunday", "sun"       -> 6
            else                  -> return getTodayKey()
        }
        val cal = Calendar.getInstance()
        val dow = cal.get(Calendar.DAY_OF_WEEK)
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val minute = cal.get(Calendar.MINUTE)
        val isPastSunday2359 = (dow == Calendar.SUNDAY && (hour == 23 && minute >= 59))

        if (dow == Calendar.SUNDAY && isPastSunday2359) {
            // After Sunday 23:59: weekly reset has triggered for upcoming week, advance to next week
            cal.add(Calendar.DAY_OF_YEAR, targetOffset + 1)
        } else {
            // Monday through Sunday (before 23:59): this is the active academic week that started on Monday.
            // In Java Calendar, Sunday is 1, so days since Monday is 6.
            val daysSinceMonday = if (dow == Calendar.SUNDAY) 6 else (dow - Calendar.MONDAY)
            cal.add(Calendar.DAY_OF_YEAR, targetOffset - daysSinceMonday)
        }
        return SimpleDateFormat("yyyyMMdd", Locale.US).format(cal.time)
    }

    /**
     * Populates active weekly timetable keys from stored course history records.
     * Ensures all attendance marked throughout the week is immediately visible.
     */
    fun populateWeekFromHistory(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val weekStartKey = getDateKeyForDay("Monday")
        val weekEndKey = getDateKeyForDay("Sunday")
        val editor = prefs.edit()
        var populated = 0
        for (k in prefs.all.keys) {
            if (k.startsWith("history_")) {
                val cCode = k.removePrefix("history_")
                val jsonStr = prefs.getString(k, null) ?: continue
                try {
                    val arr = JSONArray(jsonStr)
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        val dKey = obj.optString("dateKey")
                        val statusStr = obj.optString("status")
                        val timeSlot = obj.optString("timeSlot")
                        val start24 = parseStartTo24h(timeSlot)
                        if (dKey.isNotBlank() && dKey >= weekStartKey && dKey <= weekEndKey) {
                            if (start24.isNotBlank()) {
                                editor.putString(makeKey(dKey, cCode, start24), statusStr)
                            }
                            val existing = prefs.getString(makeKey(dKey, cCode, ""), null)
                            if (statusStr != AttendanceStatus.UNMARKED.name || existing == null) {
                                editor.putString(makeKey(dKey, cCode, ""), statusStr)
                            }
                            populated++
                        }
                    }
                } catch (_: Exception) {}
            }
        }
        if (populated > 0) {
            editor.apply()
            android.util.Log.i("SkedAttendance", "Populated $populated week keys from history ($weekStartKey to $weekEndKey)")
        }
        return populated
    }

    /**
     * Milliseconds of Monday 00:00:00.000 for the week containing nowMs.
     */
    fun getWeekStartTimestamp(nowMs: Long = System.currentTimeMillis()): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = nowMs
        }
        val dow = cal.get(Calendar.DAY_OF_WEEK)
        val daysSinceMonday = if (dow == Calendar.SUNDAY) 6 else (dow - Calendar.MONDAY)
        cal.add(Calendar.DAY_OF_YEAR, -daysSinceMonday)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    /**
     * When reset occurs on Sunday at 23:59, it applies to the upcoming week starting Monday.
     * On other days / before 23:59 on Sunday, it applies to the active week starting Monday.
     */
    fun getActiveOrUpcomingWeekStart(nowMs: Long = System.currentTimeMillis()): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = nowMs
        }
        val dow = cal.get(Calendar.DAY_OF_WEEK)
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val minute = cal.get(Calendar.MINUTE)
        val daysSinceMonday = if (dow == Calendar.SUNDAY) 6 else (dow - Calendar.MONDAY)
        cal.add(Calendar.DAY_OF_YEAR, -daysSinceMonday)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        if (dow == Calendar.SUNDAY && (hour == 23 && minute >= 59)) {
            cal.add(Calendar.DAY_OF_YEAR, 7)
        }
        return cal.timeInMillis
    }

    /**
     * Calculate delay in milliseconds until upcoming Sunday at 23:59:00.
     */
    fun getMillisUntilNextSunday2359(nowMs: Long = System.currentTimeMillis()): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = nowMs
        }
        val dow = cal.get(Calendar.DAY_OF_WEEK)
        val daysUntilSunday = (Calendar.SUNDAY - dow + 7) % 7
        val targetSunday = Calendar.getInstance().apply {
            timeInMillis = nowMs
            add(Calendar.DAY_OF_YEAR, daysUntilSunday)
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        if (targetSunday.timeInMillis <= nowMs) {
            targetSunday.add(Calendar.DAY_OF_YEAR, 7)
        }

        return (targetSunday.timeInMillis - nowMs).coerceAtLeast(1000L)
    }

    /**
     * Resets all weekly timetable attendance statuses back to UNMARKED
     * for the upcoming week and triggers widget refresh.
     */
    fun resetWeeklyAttendance(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        val allKeys = prefs.all.keys
        var removedCount = 0
        for (k in allKeys) {
            if (k.matches(Regex("""^\d{8}_.*"""))) {
                editor.remove(k)
                removedCount++
            }
        }
        editor.remove("last_att_raw")
        editor.putLong(KEY_LAST_RESET_TIME, System.currentTimeMillis())
        editor.putLong(KEY_LAST_RESET_WEEK, getActiveOrUpcomingWeekStart())
        editor.apply()

        android.util.Log.i("SkedAttendance", "Weekly reset executed: removed $removedCount attendance keys")

        // Trigger widget update
        try {
            val intent = Intent(context, TimetableWidgetReceiver::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            }
            context.sendBroadcast(intent)
        } catch (_: Exception) {}

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val manager = GlanceAppWidgetManager(context)
                val ids = manager.getGlanceIds(TimetableWidget::class.java)
                ids.forEach { id ->
                    TimetableWidget().update(context, id)
                }
            } catch (_: Exception) {}
        }
    }

    /**
     * Passive catch-up: if the app or widget is opened in a new week and the Sunday reset
     * was missed (e.g. phone turned off), reset immediately.
     */
    fun checkAndResetWeekly(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastResetWeek = prefs.getLong(KEY_LAST_RESET_WEEK, 0L)
        val currentWeek = getActiveOrUpcomingWeekStart()
        if (lastResetWeek == 0L) {
            prefs.edit().putLong(KEY_LAST_RESET_WEEK, currentWeek).apply()
            return
        }
        if (lastResetWeek < currentWeek) {
            android.util.Log.i("SkedAttendance", "New week detected! Catch-up weekly reset triggering...")
            resetWeeklyAttendance(context)
        }
    }

    private fun normalizeCourse(code: String): String {
        return code.trim().uppercase().replace(Regex("[^A-Z0-9]"), "")
    }

    private fun normalizeTime(t: String): String {
        return t.trim().take(5).replace(" ", "")
    }

    private fun makeKey(dateKey: String, courseCode: String, start: String): String {
        val normCourse = normalizeCourse(courseCode)
        val normStart = normalizeTime(start)
        return if (normStart.isEmpty()) {
            "${dateKey}_${normCourse}"
        } else {
            "${dateKey}_${normCourse}_${normStart}"
        }
    }

    fun getStatus(
        context: Context,
        courseCode: String,
        start: String,
        dateKey: String = getTodayKey()
    ): AttendanceStatus {
        checkAndResetWeekly(context)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // Tier 1: Try exact match with start time
        val rawExact = prefs.getString(makeKey(dateKey, courseCode, start), null)
        if (!rawExact.isNullOrBlank()) {
            try {
                return AttendanceStatus.valueOf(rawExact)
            } catch (_: Exception) {}
        }

        // Tier 2: Fallback to course-level status for the date
        val rawCourse = prefs.getString(makeKey(dateKey, courseCode, ""), null)
        if (!rawCourse.isNullOrBlank()) {
            try {
                return AttendanceStatus.valueOf(rawCourse)
            } catch (_: Exception) {}
        }

        return AttendanceStatus.UNMARKED
    }

    fun setStatus(
        context: Context,
        courseCode: String,
        start: String,
        status: AttendanceStatus,
        dateKey: String = getTodayKey()
    ) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        val exactKey = makeKey(dateKey, courseCode, start)
        editor.putString(exactKey, status.name)
        // Also keep course-level fallback
        editor.putString(makeKey(dateKey, courseCode, ""), status.name)
        editor.apply()
    }

    fun cycleStatus(
        context: Context,
        courseCode: String,
        start: String,
        dateKey: String = getTodayKey()
    ): AttendanceStatus {
        val current = getStatus(context, courseCode, start, dateKey)
        val next = when (current) {
            AttendanceStatus.UNMARKED   -> AttendanceStatus.PRESENT
            AttendanceStatus.PRESENT    -> AttendanceStatus.ABSENT
            AttendanceStatus.ABSENT     -> AttendanceStatus.DUTY_LEAVE
            AttendanceStatus.DUTY_LEAVE -> AttendanceStatus.UNMARKED
        }
        setStatus(context, courseCode, start, next, dateKey)
        return next
    }

    fun saveLiveAttendanceBatch(context: Context, rawJson: String, dateKey: String = getTodayKey()): Int {
        if (rawJson.isBlank()) return 0
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        var markedCount = 0

        try {
            val root = JSONObject(rawJson)

            // 1. Process today's live class attendance status
            val todayArr = root.optJSONArray("todayStatus")
            if (todayArr != null) {
                for (i in 0 until todayArr.length()) {
                    val item = todayArr.getJSONObject(i)
                    val code = item.optString("courseCode")
                    val start = item.optString("start")
                    val statusStr = item.optString("status")

                    val status = when (statusStr.uppercase()) {
                        "PRESENT"    -> AttendanceStatus.PRESENT
                        "ABSENT"     -> AttendanceStatus.ABSENT
                        "DUTY_LEAVE" -> AttendanceStatus.DUTY_LEAVE
                        else         -> AttendanceStatus.UNMARKED
                    }

                    if (code.isNotBlank()) {
                        if (start.isNotBlank()) {
                            editor.putString(makeKey(dateKey, code, start), status.name)
                        }
                        val existingFallback = prefs.getString(makeKey(dateKey, code, ""), null)
                        if (status != AttendanceStatus.UNMARKED || existingFallback == null) {
                            editor.putString(makeKey(dateKey, code, ""), status.name)
                        }
                        if (status != AttendanceStatus.UNMARKED) markedCount++
                    }
                }
            }

            // 2. Process course-wise attendance summary
            val coursesArr = root.optJSONArray("courses")
            if (coursesArr != null) {
                val coursesObj = JSONObject()
                for (i in 0 until coursesArr.length()) {
                    val c = coursesArr.getJSONObject(i)
                    val code = normalizeCourse(c.optString("courseCode"))
                    if (code.isNotBlank()) {
                        coursesObj.put(code, c)
                    }
                }
                editor.putString(KEY_COURSES, coursesObj.toString())
            }

            // 3. Process course-wise semester historical attendance details from Day 1
            val detailsArr = root.optJSONArray("courseDetails")
            if (detailsArr != null) {
                val weekStartKey = getDateKeyForDay("Monday")
                val weekEndKey = getDateKeyForDay("Sunday")
                for (i in 0 until detailsArr.length()) {
                    val detailObj = detailsArr.getJSONObject(i)
                    val cCode = detailObj.optString("courseCode").trim().uppercase()
                    val recordsArr = detailObj.optJSONArray("records") ?: JSONArray()
                    if (cCode.isBlank()) continue

                    val historyList = mutableListOf<AttendanceHistoryItem>()
                    for (j in 0 until recordsArr.length()) {
                        val record = recordsArr.getJSONObject(j)
                        val attDateRaw = record.optString("AttendanceDate")
                        val dKey = parseDateToKey(attDateRaw)
                        val codeStr = record.optString("AttendanceCode").trim().uppercase()
                        val status = when {
                            codeStr == "P" -> AttendanceStatus.PRESENT
                            codeStr == "A" -> AttendanceStatus.ABSENT
                            codeStr.contains("D") -> AttendanceStatus.DUTY_LEAVE
                            else -> AttendanceStatus.UNMARKED
                        }
                        val attTime = record.optString("AttendanceTime")
                        val start24 = parseStartTo24h(attTime)

                        if (dKey.isNotBlank() && status != AttendanceStatus.UNMARKED) {
                            historyList.add(
                                AttendanceHistoryItem(
                                    dateKey = dKey,
                                    formattedDate = formatDateKey(dKey),
                                    dayOfWeek = getDayOfWeekFromDateKey(dKey),
                                    timeSlot = attTime.ifBlank { start24 },
                                    status = status
                                )
                            )
                        }

                        // Also populate weekly table keys for current week
                        if (dKey.isNotBlank() && dKey >= weekStartKey && dKey <= weekEndKey) {
                            if (start24.isNotBlank()) {
                                editor.putString(makeKey(dKey, cCode, start24), status.name)
                            }
                            val existingFallback = prefs.getString(makeKey(dKey, cCode, ""), null)
                            if (status != AttendanceStatus.UNMARKED || existingFallback == null) {
                                editor.putString(makeKey(dKey, cCode, ""), status.name)
                            }
                        }
                    }

                    if (historyList.isNotEmpty()) {
                        val normCode = normalizeCourse(cCode)
                        val arr = JSONArray()
                        for (r in historyList) {
                            val obj = JSONObject().apply {
                                put("dateKey", r.dateKey)
                                put("timeSlot", r.timeSlot)
                                put("status", r.status.name)
                            }
                            arr.put(obj)
                        }
                        editor.putString("history_$normCode", arr.toString())
                        markedCount += historyList.size
                    }
                }
            }

            editor.apply()
        } catch (_: Exception) {}

        return markedCount
    }

    /**
     * Authenticate directly against LPU Mobile API using native AES encryption
     * and sync full semester course attendance and history.
     */
    fun loginAndSyncMobileApi(context: Context, userId: String, password: String): Boolean {
        if (userId.isBlank() || password.isBlank()) return false
        return try {
            val devId = "3fa85f64-5717-4562-b3fc-2c963f66afa6"
            val payloadObj = JSONObject().apply {
                put("url", "milkyway")
                put("action", "post")
                put("data", JSONObject().apply {
                    put("UserId", userId.trim())
                    put("password", password.trim())
                    put("Identity", "aphone")
                    put("DeviceId", devId)
                    put("PlayerId", "vbnxvcjhvbvcgghgjhgjhdddddjhgjf")
                })
                put("guest", "ums.lovely.university")
                put("guestcount", "20.87")
            }

            val keyBytes = android.util.Base64.decode("m0rDSdPyzt+bo/BuTLgmXssN6TSzRPACdahgiCt5SLs=", android.util.Base64.NO_WRAP)
            val iv = ByteArray(16).also { java.security.SecureRandom().nextBytes(it) }
            val cipher = javax.crypto.Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, javax.crypto.spec.SecretKeySpec(keyBytes, "AES"), javax.crypto.spec.IvParameterSpec(iv))
            val encrypted = cipher.doFinal(payloadObj.toString().toByteArray(Charsets.UTF_8))
            val dStr = android.util.Base64.encodeToString(encrypted, android.util.Base64.NO_WRAP)
            val vStr = android.util.Base64.encodeToString(iv, android.util.Base64.NO_WRAP)

            val pvrUrl = java.net.URL("https://ums.lpu.in/umswebservice/umswebservice.svc/PVR")
            val conn = pvrUrl.openConnection() as java.net.HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 10000
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            val pvrBody = JSONObject().apply {
                put("v", vStr)
                put("d", dStr)
            }.toString()
            conn.outputStream.use { it.write(pvrBody.toByteArray(Charsets.UTF_8)) }
            if (conn.responseCode != 200) return false
            val pvrResp = conn.inputStream.bufferedReader().readText()
            val pvrObj = JSONObject(pvrResp)
            val pvrList = JSONArray(pvrObj.optString("PVRResult", "[]"))
            if (pvrList.length() == 0) return false
            val token = pvrList.getJSONObject(0).optString("AccessToken")
            if (token.isBlank()) return false

            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .putString("mobile_token", token)
                .putString("mobile_user_id", userId.trim())
                .putString("mobile_device_id", devId)
                .putString("saved_ums_pwd", password.trim())
                .apply()

            refreshFromMobileApi(context)
            true
        } catch (e: Exception) {
            android.util.Log.e("SkedAttendance", "loginAndSyncMobileApi failed", e)
            false
        }
    }

    /**
     * Background refresh using the saved LPU Mobile WebService AccessToken
     */
    fun refreshFromMobileApi(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val token = prefs.getString("mobile_token", null) ?: return 0
        val userId = prefs.getString("mobile_user_id", null) ?: return 0
        val deviceId = prefs.getString("mobile_device_id", "3fa85f64-5717-4562-b3fc-2c963f66afa6") ?: return 0

        return try {
            val basicUrl = java.net.URL("https://ums.lpu.in/umswebservice/umswebservice.svc/StudentBasicInfoForService/$userId/$token/$deviceId/null/null")
            val conn = basicUrl.openConnection() as java.net.HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 10000
            conn.requestMethod = "GET"
            if (conn.responseCode != 200) return 0
            val basicText = conn.inputStream.bufferedReader().readText()
            if (basicText.contains("session has expired", ignoreCase = true)) {
                prefs.edit().remove("mobile_token").apply()
                return 0
            }

            val basicArr = when {
                basicText.trim().startsWith("[") -> JSONArray(basicText)
                basicText.trim().startsWith("{") -> {
                    val obj = JSONObject(basicText)
                    obj.optJSONArray("StudentBasicInfoForServiceResult") ?: JSONArray()
                }
                else -> JSONArray()
            }
            if (basicArr.length() == 0) return 0
            val basicObj = basicArr.getJSONObject(0)
            val ttArr = basicObj.optJSONArray("TimeTable") ?: JSONArray()

            val todayStatus = JSONArray()
            for (i in 0 until ttArr.length()) {
                val tt = ttArr.getJSONObject(i)
                val cCode = tt.optString("CourseCode").trim().uppercase()
                val attType = tt.optString("AttendanceType").trim()
                val attTime = tt.optString("AttendanceTime").trim()
                val start = parseStartTo24h(attTime)

                val status = when {
                    attType.contains("present", ignoreCase = true) -> "PRESENT"
                    attType.contains("absent", ignoreCase = true) -> "ABSENT"
                    attType.contains("duty", ignoreCase = true) || attType.contains("leave", ignoreCase = true) -> "DUTY_LEAVE"
                    else -> "UNMARKED"
                }

                val item = JSONObject()
                item.put("courseCode", cCode)
                item.put("start", start)
                item.put("timeRange", attTime)
                item.put("status", status)
                todayStatus.put(item)
            }

            // Fetch course summary
            val courses = JSONArray()
            try {
                val coursesUrl = java.net.URL("https://ums.lpu.in/umswebservice/umswebservice.svc/StudentAttendanceForServiceNew/$userId/$token/$deviceId")
                val cConn = coursesUrl.openConnection() as java.net.HttpURLConnection
                cConn.connectTimeout = 8000
                cConn.readTimeout = 10000
                cConn.requestMethod = "GET"
                if (cConn.responseCode == 200) {
                    val cText = cConn.inputStream.bufferedReader().readText()
                    val cArr = when {
                        cText.trim().startsWith("[") -> JSONArray(cText)
                        cText.trim().startsWith("{") -> {
                            val obj = JSONObject(cText)
                            obj.optJSONArray("StudentAttendanceForServiceNewResult")
                                ?: obj.optJSONArray("StudentAttendanceForServiceResult")
                                ?: JSONArray()
                        }
                        else -> JSONArray()
                    }
                    for (i in 0 until cArr.length()) {
                        val cItem = cArr.getJSONObject(i)
                        val cCode = cItem.optString("CourseCode").ifEmpty { cItem.optString("courseCode") }.trim().uppercase()
                        if (cCode.isNotBlank()) {
                            val cObj = JSONObject()
                            cObj.put("courseCode", cCode)
                            cObj.put("courseName", cItem.optString("CourseName").ifEmpty { cItem.optString("courseName") })
                            val attd = cItem.optString("Total_Attd").ifEmpty { cItem.optString("attended") }.toIntOrNull()
                                ?: cItem.optInt("Total_Attd", cItem.optInt("attended", 0))
                            val delv = cItem.optString("Total_Delv").ifEmpty { cItem.optString("delivered") }.toIntOrNull()
                                ?: cItem.optInt("Total_Delv", cItem.optInt("delivered", 0))
                            val perc = cItem.optString("Total_Perc").ifEmpty { cItem.optString("percentage") }.toDoubleOrNull()
                                ?: cItem.optDouble("Total_Perc", cItem.optDouble("percentage", 0.0))
                            val dl = cItem.optString("DutyLeave").ifEmpty { cItem.optString("dutyLeave") }.toIntOrNull()
                                ?: cItem.optInt("DutyLeave", cItem.optInt("dutyLeave", 0))

                            cObj.put("attended", attd)
                            cObj.put("delivered", delv)
                            cObj.put("percentage", perc)
                            cObj.put("dutyLeave", dl)
                            courses.put(cObj)
                        }
                    }
                }
            } catch (_: Exception) {}

            val payload = JSONObject()
            payload.put("todayStatus", todayStatus)
            payload.put("courses", courses)

            val todayCount = saveLiveAttendanceBatch(context, payload.toString())

            // 3. Fetch full weekly attendance detail for every course (Mon, Tue, Wed, Thu, Fri, Sat)
            val weekStartKey = getDateKeyForDay("Monday")
            val weekEndKey = getDateKeyForDay("Sunday")
            val editor = prefs.edit()
            var weeklyDetailMarks = 0

            for (i in 0 until courses.length()) {
                val cObj = courses.getJSONObject(i)
                val cCode = cObj.optString("courseCode").trim().uppercase()
                if (cCode.isBlank()) continue

                try {
                    val detailUrl = java.net.URL("https://ums.lpu.in/umswebservice/umswebservice.svc/StudentAttendanceDetailForService/$userId/$token/$deviceId/$cCode")
                    val dConn = detailUrl.openConnection() as java.net.HttpURLConnection
                    dConn.connectTimeout = 6000
                    dConn.readTimeout = 8000
                    dConn.requestMethod = "GET"
                    if (dConn.responseCode == 200) {
                        val dText = dConn.inputStream.bufferedReader().readText()
                        val dArr = JSONArray(dText)
                        val fullHistoryArr = JSONArray()
                        for (j in 0 until dArr.length()) {
                            val record = dArr.getJSONObject(j)
                            val attDateRaw = record.optString("AttendanceDate")
                            val dKey = parseDateToKey(attDateRaw)
                            val codeStr = record.optString("AttendanceCode").trim().uppercase()
                            val status = when {
                                codeStr == "P" -> AttendanceStatus.PRESENT
                                codeStr == "A" -> AttendanceStatus.ABSENT
                                codeStr.contains("D") -> AttendanceStatus.DUTY_LEAVE
                                else -> AttendanceStatus.UNMARKED
                            }
                            val attTime = record.optString("AttendanceTime")
                            val start24 = parseStartTo24h(attTime)

                            if (dKey.isNotBlank() && status != AttendanceStatus.UNMARKED) {
                                val hObj = JSONObject().apply {
                                    put("dateKey", dKey)
                                    put("timeSlot", attTime.ifBlank { start24 })
                                    put("status", status.name)
                                }
                                fullHistoryArr.put(hObj)
                            }

                            if (dKey.isNotBlank() && dKey >= weekStartKey && dKey <= weekEndKey) {
                                if (start24.isNotBlank()) {
                                    editor.putString(makeKey(dKey, cCode, start24), status.name)
                                }
                                val existingFallback = prefs.getString(makeKey(dKey, cCode, ""), null)
                                if (status != AttendanceStatus.UNMARKED || existingFallback == null) {
                                    editor.putString(makeKey(dKey, cCode, ""), status.name)
                                }
                                if (status != AttendanceStatus.UNMARKED) {
                                    weeklyDetailMarks++
                                }
                            }
                        }
                        if (fullHistoryArr.length() > 0) {
                            editor.putString("history_${normalizeCourse(cCode)}", fullHistoryArr.toString())
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("SkedAttendance", "Failed to fetch attendance detail for $cCode", e)
                }
            }
            editor.apply()
            android.util.Log.i("SkedAttendance", "Weekly details sync complete: saved $weeklyDetailMarks marks for current week ($weekStartKey to $weekEndKey)")

            // Update widgets with the new week-wide attendance data
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val manager = GlanceAppWidgetManager(context)
                    val ids = manager.getGlanceIds(TimetableWidget::class.java)
                    ids.forEach { id ->
                        TimetableWidget().update(context, id)
                    }
                } catch (_: Exception) {}
            }

            todayCount + weeklyDetailMarks
        } catch (e: Exception) {
            android.util.Log.e("SkedAttendance", "refreshFromMobileApi failed", e)
            0
        }
    }

    fun getCourseAttendance(context: Context, courseCode: String): CourseAttendance? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_COURSES, null)
        val normCode = normalizeCourse(courseCode)
        var c: JSONObject? = null
        if (!raw.isNullOrBlank()) {
            try {
                val root = JSONObject(raw)
                c = root.optJSONObject(normCode)
                    ?: root.optJSONObject(courseCode.trim().uppercase())
                    ?: root.optJSONObject(courseCode.trim())
                if (c == null) {
                    val keys = root.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        if (normalizeCourse(k) == normCode) {
                            c = root.getJSONObject(k)
                            break
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        val name = c?.optString("courseName") ?: ""
        val attd = c?.let { it.optInt("attended", -1).takeIf { a -> a >= 0 } ?: it.optString("attended").toIntOrNull() } ?: 0
        val delv = c?.let { it.optInt("delivered", -1).takeIf { d -> d >= 0 } ?: it.optString("delivered").toIntOrNull() } ?: 0
        val perc = c?.let { it.optDouble("percentage", -1.0).takeIf { p -> p >= 0.0 } ?: it.optString("percentage").toDoubleOrNull() } ?: 0.0
        val dl = c?.let { it.optInt("dutyLeave", -1).takeIf { d -> d >= 0 } ?: it.optString("dutyLeave").toIntOrNull() } ?: 0

        // Tier 1: Official UMS summary with delivered classes
        if (c != null && delv > 0) {
            return CourseAttendance(
                courseCode = courseCode,
                courseName = name,
                attended = attd,
                delivered = delv,
                percentage = perc,
                dutyLeave = dl
            )
        }

        // Tier 2: Check persistent semester history (StudentAttendanceDetailForService)
        val historyRaw = prefs.getString("history_$normCode", null)
        if (!historyRaw.isNullOrBlank()) {
            try {
                val arr = JSONArray(historyRaw)
                var histDelv = 0
                var histAttd = 0
                var histDl = 0
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val st = try { AttendanceStatus.valueOf(obj.optString("status")) } catch (_: Exception) { AttendanceStatus.UNMARKED }
                    if (st != AttendanceStatus.UNMARKED) {
                        histDelv++
                        if (st == AttendanceStatus.PRESENT) histAttd++
                        else if (st == AttendanceStatus.DUTY_LEAVE) histDl++
                    }
                }
                if (histDelv > 0) {
                    val histPerc = (histAttd + histDl).toDouble() / histDelv * 100.0
                    return CourseAttendance(
                        courseCode = courseCode,
                        courseName = name,
                        attended = histAttd,
                        delivered = histDelv,
                        percentage = histPerc,
                        dutyLeave = histDl
                    )
                }
            } catch (_: Exception) {}
        }

        // Tier 3: 0 delivered classes or not yet delivered — defaults to 100% (never 0%)
        return CourseAttendance(
            courseCode = courseCode,
            courseName = name,
            attended = 0,
            delivered = 0,
            percentage = 100.0,
            dutyLeave = 0
        )
    }

    fun getAllCourseAttendance(context: Context): Map<String, CourseAttendance> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_COURSES, null) ?: return emptyMap()
        val result = mutableMapOf<String, CourseAttendance>()
        try {
            val root = JSONObject(raw)
            val keys = root.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val c = root.getJSONObject(k)
                result[k] = CourseAttendance(
                    courseCode = c.optString("courseCode", k),
                    courseName = c.optString("courseName"),
                    attended = c.optInt("attended", 0),
                    delivered = c.optInt("delivered", 0),
                    percentage = c.optDouble("percentage", 0.0),
                    dutyLeave = c.optInt("dutyLeave", 0)
                )
            }
        } catch (_: Exception) {}
        return result
    }

    fun getWeekAttendanceSummary(context: Context): WeekAttendanceSummary {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val weekStartKey = getDateKeyForDay("Monday")
        val weekEndKey = getDateKeyForDay("Sunday")
        var att = 0
        var abs = 0
        var dl = 0
        for ((k, v) in prefs.all) {
            if (k.matches(Regex("""^\d{8}_[A-Z0-9]+_\d{2}:\d{2}$"""))) {
                val dKey = k.take(8)
                if (dKey in weekStartKey..weekEndKey) {
                    when (v as? String) {
                        AttendanceStatus.PRESENT.name -> att++
                        AttendanceStatus.ABSENT.name -> abs++
                        AttendanceStatus.DUTY_LEAVE.name -> dl++
                    }
                }
            }
        }
        val courses = getAllCourseAttendance(context)
        var totalAtt = 0
        var totalDel = 0
        for (c in courses.values) {
            totalAtt += c.attended + c.dutyLeave
            totalDel += c.delivered
        }
        val pct = if (totalDel > 0) (totalAtt.toDouble() / totalDel * 100.0) else 100.0
        return WeekAttendanceSummary(att, abs, dl, pct)
    }

    fun formatDateKey(dateKey: String): String {
        if (dateKey.length == 8) {
            try {
                val sdfIn = SimpleDateFormat("yyyyMMdd", Locale.US)
                val sdfOut = SimpleDateFormat("EEE, d MMM yyyy", Locale.US)
                val d = sdfIn.parse(dateKey)
                if (d != null) return sdfOut.format(d)
            } catch (_: Exception) {}
        }
        return dateKey
    }

    fun getDayOfWeekFromDateKey(dateKey: String): String {
        if (dateKey.length == 8) {
            try {
                val sdfIn = SimpleDateFormat("yyyyMMdd", Locale.US)
                val sdfOut = SimpleDateFormat("EEEE", Locale.US)
                val d = sdfIn.parse(dateKey)
                if (d != null) return sdfOut.format(d)
            } catch (_: Exception) {}
        }
        return ""
    }

    fun getCourseHistory(context: Context, courseCode: String): List<AttendanceHistoryItem> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val normCode = normalizeCourse(courseCode)
        val itemsMap = mutableMapOf<String, AttendanceHistoryItem>()

        // 1. Read persistent history array for this course
        val historyRaw = prefs.getString("history_$normCode", null)
        if (!historyRaw.isNullOrBlank()) {
            try {
                val arr = JSONArray(historyRaw)
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val dKey = obj.optString("dateKey")
                    val tSlot = obj.optString("timeSlot")
                    val stStr = obj.optString("status")
                    val st = try { AttendanceStatus.valueOf(stStr) } catch (_: Exception) { AttendanceStatus.UNMARKED }
                    if (dKey.isNotBlank() && st != AttendanceStatus.UNMARKED) {
                        val formatted = formatDateKey(dKey)
                        val dayName = getDayOfWeekFromDateKey(dKey)
                        val timeKey = parseStartTo24h(tSlot)
                        val key = "${dKey}_$timeKey"
                        itemsMap[key] = AttendanceHistoryItem(
                            dateKey = dKey,
                            formattedDate = formatted,
                            dayOfWeek = dayName,
                            timeSlot = tSlot,
                            status = st
                        )
                    }
                }
            } catch (_: Exception) {}
        }

        // 2. Read all weekly keys matching ^(\d{8})_([A-Z0-9]+)(?:_(\d{1,2}:\d{2}))?$
        val allKeys = prefs.all
        val regex = Regex("""^(\d{8})_([A-Z0-9]+)(?:_(\d{1,2}:\d{2}))?$""")
        for ((k, v) in allKeys) {
            val m = regex.find(k) ?: continue
            val dKey = m.groupValues[1]
            val cCode = m.groupValues[2]
            val timePart = if (m.groupValues.size > 3) m.groupValues[3] else ""

            if (cCode.equals(normCode, ignoreCase = true) && v is String) {
                val st = try { AttendanceStatus.valueOf(v) } catch (_: Exception) { AttendanceStatus.UNMARKED }
                if (st != AttendanceStatus.UNMARKED) {
                    val formatted = formatDateKey(dKey)
                    val dayName = getDayOfWeekFromDateKey(dKey)
                    val timeKey = if (timePart.isNotBlank()) parseStartTo24h(timePart) else ""
                    val key = if (timeKey.isNotBlank()) "${dKey}_$timeKey" else "${dKey}_course"
                    if (!itemsMap.containsKey(key) || timeKey.isNotBlank()) {
                        itemsMap[key] = AttendanceHistoryItem(
                            dateKey = dKey,
                            formattedDate = formatted,
                            dayOfWeek = dayName,
                            timeSlot = if (timePart.isNotBlank()) timePart else "Scheduled Class",
                            status = st
                        )
                    }
                }
            }
        }

        // Drop generic course fallback if specific timed slots exist for that date
        val datesWithTimedSlots = itemsMap.values
            .filter { it.timeSlot != "Scheduled Class" }
            .map { it.dateKey }
            .toSet()

        val cleanList = itemsMap.filterNot { (k, v) ->
            k.endsWith("_course") && datesWithTimedSlots.contains(v.dateKey)
        }.values

        return cleanList.sortedWith(
            compareByDescending<AttendanceHistoryItem> { it.dateKey }
                .thenByDescending { it.timeSlot }
        )
    }

    fun saveCourseHistory(context: Context, courseCode: String, records: List<AttendanceHistoryItem>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val normCode = normalizeCourse(courseCode)
        val arr = JSONArray()
        for (r in records) {
            val obj = JSONObject().apply {
                put("dateKey", r.dateKey)
                put("timeSlot", r.timeSlot)
                put("status", r.status.name)
            }
            arr.put(obj)
        }
        prefs.edit().putString("history_$normCode", arr.toString()).apply()
    }

    fun fetchSingleCourseHistory(context: Context, courseCode: String): List<AttendanceHistoryItem> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val token = prefs.getString("mobile_token", null) ?: return getCourseHistory(context, courseCode)
        val userId = prefs.getString("mobile_user_id", null) ?: return getCourseHistory(context, courseCode)
        val deviceId = prefs.getString("mobile_device_id", "3fa85f64-5717-4562-b3fc-2c963f66afa6") ?: "3fa85f64-5717-4562-b3fc-2c963f66afa6"
        val normCode = normalizeCourse(courseCode)

        return try {
            val detailUrl = java.net.URL("https://ums.lpu.in/umswebservice/umswebservice.svc/StudentAttendanceDetailForService/$userId/$token/$deviceId/$normCode")
            val dConn = detailUrl.openConnection() as java.net.HttpURLConnection
            dConn.connectTimeout = 6000
            dConn.readTimeout = 8000
            dConn.requestMethod = "GET"
            if (dConn.responseCode == 200) {
                val dText = dConn.inputStream.bufferedReader().readText()
                val dArr = when {
                    dText.trim().startsWith("[") -> JSONArray(dText)
                    dText.trim().startsWith("{") -> {
                        val obj = JSONObject(dText)
                        obj.optJSONArray("StudentAttendanceDetailForServiceResult") ?: JSONArray()
                    }
                    else -> JSONArray()
                }
                val historyList = mutableListOf<AttendanceHistoryItem>()
                for (j in 0 until dArr.length()) {
                    val record = dArr.getJSONObject(j)
                    val attDateRaw = record.optString("AttendanceDate")
                    val dKey = parseDateToKey(attDateRaw)
                    val codeStr = record.optString("AttendanceCode").trim().uppercase()
                    val status = when {
                        codeStr == "P" -> AttendanceStatus.PRESENT
                        codeStr == "A" -> AttendanceStatus.ABSENT
                        codeStr.contains("D") -> AttendanceStatus.DUTY_LEAVE
                        else -> AttendanceStatus.UNMARKED
                    }
                    val attTime = record.optString("AttendanceTime")
                    if (dKey.isNotBlank() && status != AttendanceStatus.UNMARKED) {
                        historyList.add(
                            AttendanceHistoryItem(
                                dateKey = dKey,
                                formattedDate = formatDateKey(dKey),
                                dayOfWeek = getDayOfWeekFromDateKey(dKey),
                                timeSlot = attTime.ifBlank { "Scheduled Class" },
                                status = status
                            )
                        )
                    }
                }
                if (historyList.isNotEmpty()) {
                    saveCourseHistory(context, normCode, historyList)
                }
            }
            getCourseHistory(context, courseCode)
        } catch (_: Exception) {
            getCourseHistory(context, courseCode)
        }
    }

    fun clearAll(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
    }
}
