package com.sked.sked_app

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import android.graphics.BitmapFactory
import com.sked.sked_app.widget.PREFS_NAME
import com.sked.sked_app.widget.TimetableRefreshWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.Calendar
import java.util.concurrent.atomic.AtomicInteger

import android.webkit.CookieManager
import android.webkit.WebSettings
import android.util.Log

enum class AndroidSyncResultType {
    SUCCESS,
    SKIPPED_COOLDOWN,
    AUTH_EXPIRED,
    SERVER_ERROR,
    DISABLED
}

data class AndroidSyncResult(
    val type: AndroidSyncResultType,
    val message: String,
    val newMakeupClasses: List<ClassItem> = emptyList(),
    val cancelledDescriptions: List<String> = emptyList()
)

object AutoSyncManager {
    const val CHANNEL_ID = "sked_makeup_alerts"
    private const val CHANNEL_NAME = "Makeup Classes & Schedule Alerts"

    const val KEY_UMS_COOKIES = "ums_session_cookies"
    const val KEY_AUTO_SYNC_ENABLED = "auto_sync_enabled"
    const val KEY_LAST_SYNC_TIMESTAMP = "last_sync_timestamp"
    const val KEY_LAST_SYNC_STATUS = "last_sync_status"
    const val KEY_ACTIVE_MAKEUP_KEYS = "active_makeup_keys"
    const val KEY_NOTIFIED_MAKEUP_KEYS = "notified_makeup_keys"
    const val KEY_SYNC_AUTH_FAILED = "sync_auth_failed"

    // 2-hour cooldown between background syncs (Rule 3)
    private const val COOLDOWN_MILLIS = 2 * 60 * 60 * 1000L

    // BUG-18 FIX: AtomicInteger counter for unique notification IDs.
    // System.currentTimeMillis() % 100000 could collide if two notifications
    // are posted within the same millisecond during batch makeup sync.
    private val notifIdCounter = AtomicInteger(1000)

    /**
     * Quiet hours: No audible sound or vibration between 22:30 and 07:00 (Rule 4)
     */
    fun isQuietHours(): Boolean {
        val cal = Calendar.getInstance()
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val minute = cal.get(Calendar.MINUTE)
        val currentMinutes = hour * 60 + minute
        val quietStart = 22 * 60 + 30 // 22:30
        val quietEnd = 7 * 60         // 07:00
        return currentMinutes >= quietStart || currentMinutes < quietEnd
    }

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifies you when makeup classes or schedule changes are detected on UMS"
                enableVibration(true)
                setShowBadge(true)
            }
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.createNotificationChannel(channel)
        }
    }

    fun saveCookies(context: Context, cookies: String) {
        if (cookies.isBlank()) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_UMS_COOKIES, cookies.trim())
            .putBoolean(KEY_SYNC_AUTH_FAILED, false)
            .apply()
    }

    fun hasAuthFailed(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_SYNC_AUTH_FAILED, false)
    }

    fun postMakeupNotification(
        context: Context,
        title: String,
        body: String,
        isCancelled: Boolean = false
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                return
            }
        }

        createNotificationChannel(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            (System.currentTimeMillis() % 10000).toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val quiet = isQuietHours()
        val largeIcon = try {
            BitmapFactory.decodeResource(context.resources, R.drawable.sked_logo)
        } catch (_: Exception) {
            null
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_sked)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        if (largeIcon != null) {
            builder.setLargeIcon(largeIcon)
        }

        if (quiet) {
            builder.setSilent(true)
        }

        // BUG-18 FIX: use incrementing AtomicInteger instead of millis % 100000
        val notificationId = notifIdCounter.getAndIncrement()
        try {
            NotificationManagerCompat.from(context).notify(notificationId, builder.build())
        } catch (_: Exception) {}
    }

    /**
     * Performs lightweight on-device auto-sync adhering to all 5 safety rules:
     * 1. Account Lockout Prevention (stop immediately on auth failure)
     * 2. Fail-Safe Cache Protection (never clear cache on error)
     * 3. Rate-Limiting & Jitter (2-hour minimum cooldown)
     * 4. Deduplication & Quiet Hours & Cancellation Handling
     * 5. Lightweight pure HTTP execution
     */
    suspend fun performSync(context: Context, force: Boolean = false): AndroidSyncResult =
        withContext(Dispatchers.IO) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val enabled = prefs.getBoolean(KEY_AUTO_SYNC_ENABLED, true)
            if (!enabled && !force) {
                return@withContext AndroidSyncResult(
                    type = AndroidSyncResultType.DISABLED,
                    message = "Auto-sync is disabled in settings."
                )
            }

            val lastSyncTime = prefs.getLong(KEY_LAST_SYNC_TIMESTAMP, 0L)
            val now = System.currentTimeMillis()
            val elapsed = now - lastSyncTime

            // Rule 3: Cooldown check
            if (!force && elapsed < COOLDOWN_MILLIS) {
                val remainingMins = ((COOLDOWN_MILLIS - elapsed) / (60 * 1000L)).toInt()
                return@withContext AndroidSyncResult(
                    type = AndroidSyncResultType.SKIPPED_COOLDOWN,
                    message = "Cooldown active. Next sync in ${remainingMins}m."
                )
            }

            // Sync latest cookies from WebView CookieManager if available
            var cookies = prefs.getString(KEY_UMS_COOKIES, "") ?: ""
            try {
                val cmCookies = CookieManager.getInstance().getCookie("https://ums.lpu.in") ?: ""
                if (cmCookies.isNotBlank()) {
                    cookies = cmCookies
                    saveCookies(context, cmCookies)
                }
            } catch (_: Exception) {}

            if (cookies.isBlank()) {
                return@withContext AndroidSyncResult(
                    type = AndroidSyncResultType.AUTH_EXPIRED,
                    message = "No active session cookies. Log in to enable auto-sync."
                )
            }

            val userId = prefs.getString("sked_user_id", "") ?: ""
            if (userId.isBlank()) {
                return@withContext AndroidSyncResult(
                    type = AndroidSyncResultType.AUTH_EXPIRED,
                    message = "No student registration found."
                )
            }

            val userAgent = try {
                WebSettings.getDefaultUserAgent(context)
            } catch (_: Exception) {
                "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Mobile Safari/537.36"
            }

            try {
                // Step 1: Prime ASP.NET page session and extract active TermId
                var termId: String? = null
                val pageUrl = URL("https://ums.lpu.in/lpuums/frmMyCurrentTimeTable.aspx")
                val pageConn = pageUrl.openConnection() as HttpURLConnection
                pageConn.requestMethod = "GET"
                pageConn.connectTimeout = 8_000
                pageConn.readTimeout = 10_000
                pageConn.instanceFollowRedirects = false
                pageConn.setRequestProperty("User-Agent", userAgent)
                pageConn.setRequestProperty("Cookie", cookies)
                pageConn.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                pageConn.setRequestProperty("Accept-Language", "en-US,en;q=0.9")

                val pageCode = pageConn.responseCode
                val pageStream = if (pageCode in 200..299) pageConn.inputStream else pageConn.errorStream
                val pageHtml = pageStream?.bufferedReader()?.readText() ?: ""

                if (pageCode in 300..399 || pageCode == 401 || pageCode == 403 || pageCode == 500 ||
                    pageHtml.contains("LoginNew") || pageHtml.contains("lockerror") ||
                    pageHtml.contains("lblError") || pageHtml.contains("Access Denied") ||
                    pageHtml.contains("Just a moment...") || pageHtml.contains("Cloudflare") ||
                    pageHtml.contains("Server Error in '/' Application")
                ) {
                    prefs.edit()
                        .putBoolean(KEY_SYNC_AUTH_FAILED, true)
                        .putString(KEY_LAST_SYNC_STATUS, "Session expired. Re-auth required.")
                        .apply()
                    return@withContext AndroidSyncResult(
                        type = AndroidSyncResultType.AUTH_EXPIRED,
                        message = "UMS session expired. Reconnect via login."
                    )
                }

                // Extract TermId from Select1 dropdown if present
                val selectMatch = Regex("""id=["']Select1["'][^>]*>([\s\S]*?)<\/select>""", RegexOption.IGNORE_CASE).find(pageHtml)
                if (selectMatch != null) {
                    val selectContent = selectMatch.groupValues[1]
                    val termMatch = Regex("""value=["']([^"']+)["'][^>]*selected|selected[^>]*value=["']([^"']+)["']|value=["']([^"']+)["']""", RegexOption.IGNORE_CASE).find(selectContent)
                    if (termMatch != null) {
                        termId = termMatch.groupValues.drop(1).firstOrNull { it.isNotBlank() }
                    }
                }

                // Step 2: Request timetable JSON from GetTimeTable WebMethod
                val url = URL("https://ums.lpu.in/lpuums/frmMyCurrentTimeTable.aspx/GetTimeTable")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.connectTimeout = 10_000
                conn.readTimeout = 12_000
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.setRequestProperty("Accept", "application/json, text/javascript, */*; q=0.01")
                conn.setRequestProperty("X-Requested-With", "XMLHttpRequest")
                conn.setRequestProperty("Origin", "https://ums.lpu.in")
                conn.setRequestProperty("Referer", "https://ums.lpu.in/lpuums/frmMyCurrentTimeTable.aspx")
                conn.setRequestProperty("Sec-Fetch-Site", "same-origin")
                conn.setRequestProperty("Sec-Fetch-Mode", "cors")
                conn.setRequestProperty("Sec-Fetch-Dest", "empty")
                conn.setRequestProperty("Accept-Language", "en-US,en;q=0.9")
                conn.setRequestProperty("User-Agent", userAgent)
                conn.setRequestProperty("Cookie", cookies)

                OutputStreamWriter(conn.outputStream).use { writer ->
                    val payload = if (!termId.isNullOrBlank()) "{\"TermId\":\"$termId\"}" else "{\"TermId\":null}"
                    writer.write(payload)
                    writer.flush()
                }

                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val body = stream?.bufferedReader()?.readText() ?: ""

                Log.i("AutoSyncManager", "GetTimeTable HTTP code: $code, response length: ${body.length}")

                // Rule 1: Account Lockout Prevention & Session Expiration
                if (code == 401 ||
                    code == 403 ||
                    code == 500 ||
                    body.contains("LoginNew") ||
                    body.contains("lockerror") ||
                    body.contains("lblError") ||
                    body.contains("Access Denied") ||
                    body.contains("Just a moment...") ||
                    body.contains("Cloudflare") ||
                    body.contains("Object moved") ||
                    body.contains("Server Error in '/' Application")
                ) {
                    prefs.edit()
                        .putBoolean(KEY_SYNC_AUTH_FAILED, true)
                        .putString(KEY_LAST_SYNC_STATUS, "Session expired. Re-auth required.")
                        .apply()
                    return@withContext AndroidSyncResult(
                        type = AndroidSyncResultType.AUTH_EXPIRED,
                        message = "UMS session expired. Reconnect via login."
                    )
                }

                // Rule 2: Fail-Safe Cache Protection
                if (code != 200 || body.isBlank() || body.contains("Server Error in '/' Application")) {
                    prefs.edit()
                        .putString(KEY_LAST_SYNC_STATUS, "UMS server error ($code). Cache preserved.")
                        .apply()
                    return@withContext AndroidSyncResult(
                        type = AndroidSyncResultType.SERVER_ERROR,
                        message = "UMS server error ($code). Offline cache preserved."
                    )
                }

                val entries = TimetableParser.parse(body)
                if (entries.isEmpty()) {
                    prefs.edit()
                        .putString(KEY_LAST_SYNC_STATUS, "Zero classes parsed. Offline cache preserved.")
                        .apply()
                    return@withContext AndroidSyncResult(
                        type = AndroidSyncResultType.SERVER_ERROR,
                        message = "Schedule format unrecognized. Offline cache preserved."
                    )
                }

                // Step 3: Fast live hostel leave data sync using active cookies
                try {
                    val leaveUrl = URL("https://ums.lpu.in/lpuums/frmStudentHostelLeaveApplicationTermWise.aspx")
                    val leaveConn = leaveUrl.openConnection() as HttpURLConnection
                    leaveConn.requestMethod = "GET"
                    leaveConn.connectTimeout = 6_000
                    leaveConn.readTimeout = 8_000
                    leaveConn.instanceFollowRedirects = false
                    leaveConn.setRequestProperty("User-Agent", userAgent)
                    leaveConn.setRequestProperty("Cookie", cookies)
                    val leaveCode = leaveConn.responseCode
                    if (leaveCode in 200..299) {
                        val leaveHtml = leaveConn.inputStream.bufferedReader().use { it.readText() }
                        if (leaveHtml.contains("ctl00_cphHeading") || leaveHtml.contains("RadGrid") || leaveHtml.contains("LeaveDetail") || leaveHtml.contains("Hostel Leave")) {
                            val parsedLeave = com.sked.sked_app.leave.LeaveParser.parse(leaveHtml)
                            if (parsedLeave.balance.dayTotal > 0 || parsedLeave.balance.nightTotal > 0) {
                                com.sked.sked_app.leave.LeaveStorage.saveBalance(context, parsedLeave.balance)
                                Log.i("AutoSyncManager", "Fast live leave balance saved: ${parsedLeave.balance.dayAvailed}/${parsedLeave.balance.dayTotal}")
                            }
                            if (parsedLeave.profile.uid.isNotBlank() || parsedLeave.profile.name.isNotBlank()) {
                                com.sked.sked_app.leave.LeaveStorage.saveProfile(context, parsedLeave.profile)
                            }
                            if (parsedLeave.records.isNotEmpty()) {
                                com.sked.sked_app.leave.LeaveStorage.saveRecords(context, parsedLeave.records)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w("AutoSyncManager", "Fast leave sync skipped: ${e.message}")
                }

                // Rule 4: Diffing & Makeup Class Tracking
                val oldActiveKeys = prefs.getStringSet(KEY_ACTIVE_MAKEUP_KEYS, emptySet())?.toMutableSet() ?: mutableSetOf()
                val notifiedKeys = prefs.getStringSet(KEY_NOTIFIED_MAKEUP_KEYS, emptySet())?.toMutableSet() ?: mutableSetOf()

                val currentActiveKeys = mutableSetOf<String>()
                val newMakeupClasses = mutableListOf<ClassItem>()

                for (entry in entries) {
                    if (entry.isMakeup) {
                        val key = entry.uniqueKey
                        currentActiveKeys.add(key)
                        if (!notifiedKeys.contains(key)) {
                            newMakeupClasses.add(entry)
                            notifiedKeys.add(key)
                        }
                    }
                }

                // Detect cancellations
                val cancelledKeys = oldActiveKeys - currentActiveKeys
                val cancelledDescriptions = mutableListOf<String>()
                for (ck in cancelledKeys) {
                    val parts = ck.split("_")
                    val course = if (parts.isNotEmpty()) parts[0] else "Class"
                    val day = if (parts.size > 1) parts[1] else ""
                    cancelledDescriptions.add("$course ($day)")
                }

                // Persist cache and update widget
                TimetableParser.saveToPrefs(context, entries, userId)
                try {
                    TimetableRefreshWorker.updateWidget(context)
                } catch (_: Exception) {}

                prefs.edit()
                    .putLong(KEY_LAST_SYNC_TIMESTAMP, now)
                    .putStringSet(KEY_ACTIVE_MAKEUP_KEYS, currentActiveKeys)
                    .putStringSet(KEY_NOTIFIED_MAKEUP_KEYS, notifiedKeys)
                    .putBoolean(KEY_SYNC_AUTH_FAILED, false)
                    .putString(
                        KEY_LAST_SYNC_STATUS,
                        "Synced ${entries.size} classes. ${currentActiveKeys.size} makeup active."
                    )
                    .apply()

                // Dispatch notifications if any
                for (item in newMakeupClasses) {
                    val title = "🔔 Makeup Class Added: ${item.courseCode}"
                    val desc = "${item.day} | ${item.timeRange} | Room ${item.room} (${item.teacher})"
                    postMakeupNotification(context, title, desc, isCancelled = false)
                }

                for (cancelledDesc in cancelledDescriptions) {
                    val title = "⚠️ Makeup Class Cancelled"
                    val desc = "Scheduled makeup class for $cancelledDesc was removed from UMS."
                    postMakeupNotification(context, title, desc, isCancelled = true)
                }

                AndroidSyncResult(
                    type = AndroidSyncResultType.SUCCESS,
                    message = "Synced ${entries.size} classes.",
                    newMakeupClasses = newMakeupClasses,
                    cancelledDescriptions = cancelledDescriptions
                )
            } catch (e: Exception) {
                prefs.edit()
                    .putString(KEY_LAST_SYNC_STATUS, "Network drop: ${e.message}. Cache preserved.")
                    .apply()
                AndroidSyncResult(
                    type = AndroidSyncResultType.SERVER_ERROR,
                    message = "Network error during sync. Offline cache preserved."
                )
            }
        }
}

class NotificationTestReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val type = intent.getStringExtra("type") ?: "ALL"
        if (type.equals("MAKEUP", ignoreCase = true) || type.equals("ALL", ignoreCase = true)) {
            AutoSyncManager.createNotificationChannel(context)
            AutoSyncManager.postMakeupNotification(
                context,
                "🔔 Makeup Class Added: CSE316",
                "Tomorrow (Wednesday) • 10:20 AM – 11:10 AM • Room 34-601 (Dr. Rohit Sharma)"
            )
        }
        if (type.equals("UPDATE", ignoreCase = true) || type.equals("ALL", ignoreCase = true)) {
            com.sked.sked_app.update.AppUpdateManager.createUpdateNotificationChannel(context)
            com.sked.sked_app.update.AppUpdateManager.notifyUpdateAvailable(
                context,
                com.sked.sked_app.update.UpdateInfo(
                    versionCode = 9,
                    versionName = "1.3.1",
                    releaseNotes = "• Faster timetable synchronization\n• Real-time makeup class alerts\n• Exam radar updates",
                    apkUrl = "https://sked-gold.vercel.app/downloads/sked-android.apk"
                ),
                force = true
            )
        }
    }
}
