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

data class DepartmentItem(
    val id: String,
    val name: String,
    val streamCode: String,
    val count: Int,
    val color: Long // ARGB color
)

data class TelemetrySnapshot(
    val totalInstalls: Int = 0,
    val dauToday: Int = 0,
    val widgetSyncsToday: Int = 0,
    // Cohorts / Academic Years
    val batch2026: Int = 0, // 1st Year
    val batch2025: Int = 0, // 2nd Year
    val batch2024: Int = 0, // 3rd Year
    val batch2023: Int = 0, // 4th Year
    val batchOther: Int = 0,
    // Departments
    val deptCse: Int = 0,
    val deptMgmt: Int = 0,
    val deptEce: Int = 0,
    val deptMech: Int = 0,
    val deptBio: Int = 0,
    val deptPharma: Int = 0,
    val deptLaw: Int = 0,
    val deptDesign: Int = 0,
    val deptOther: Int = 0
) {
    fun getDepartmentList(): List<DepartmentItem> {
        return listOf(
            DepartmentItem("cse", "Computer Science & IT", "CSE / INT / CAP", deptCse, 0xFFFF6B1A),
            DepartmentItem("mgmt", "Mittal School of Business", "MBA / BBA / MKT", deptMgmt, 0xFFA855F7),
            DepartmentItem("ece", "Electronics & Electrical", "ECE / EEE / PEL", deptEce, 0xFF38BDF8),
            DepartmentItem("mech", "Mechanical & Civil Eng", "ME / CE / CHE", deptMech, 0xFFF59E0B),
            DepartmentItem("bio", "Bioengineering & Biotech", "BTY / BIO / BOT", deptBio, 0xFF10B981),
            DepartmentItem("pharma", "Pharmaceutical Sciences", "B.Pharm / M.Pharm", deptPharma, 0xFFF43F5E),
            DepartmentItem("law", "School of Law", "BA-LLB / LLM", deptLaw, 0xFF818CF8),
            DepartmentItem("design", "Design & Architecture", "B.Des / B.Arch", deptDesign, 0xFF14B8A6),
            DepartmentItem("other", "Applied Sciences & Other", "General / Sciences", deptOther, 0xFF71717A)
        )
    }
}

object TelemetryManager {

    private const val TAG = "TelemetryManager"
    private const val PREFS_NAME = "SkedTelemetryPrefs"
    private const val KEY_INSTALLED = "has_registered_install"
    private const val KEY_LAST_DAU_DATE = "last_dau_date"
    private const val KEY_LAST_SYNC_HOUR = "last_sync_hour"

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
                val json = JSONObject(body)
                if (json.has("value")) json.optInt("value", 0) else 0
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
     * 2026: 1st Year (126... or K26...)
     * 2025: 2nd Year (125... or K25...)
     * 2024: 3rd Year (124... or K24...)
     * 2023: 4th Year (123... or K23...)
     */
    fun recordStudentBatch(context: Context, regNo: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (regNo.length >= 3) {
            val trimmed = regNo.trim()
            val batchKey = when {
                trimmed.startsWith("126") || trimmed.contains("K26", ignoreCase = true) -> "batch_2026"
                trimmed.startsWith("125") || trimmed.contains("K25", ignoreCase = true) -> "batch_2025"
                trimmed.startsWith("124") || trimmed.contains("K24", ignoreCase = true) -> "batch_2024"
                trimmed.startsWith("123") || trimmed.contains("K23", ignoreCase = true) -> "batch_2023"
                else -> "batch_other"
            }
            if (!prefs.getBoolean("has_registered_$batchKey", false)) {
                pingCounter(batchKey)
                prefs.edit().putBoolean("has_registered_$batchKey", true).apply()
                Log.i(TAG, "Student cohort recorded: $batchKey")
            }
        }
    }

    /**
     * Identifies academic department from enrolled course codes.
     */
    fun detectDepartment(courseCodes: List<String>): String {
        var cseCount = 0
        var mgmtCount = 0
        var eceCount = 0
        var mechCount = 0
        var bioCount = 0
        var pharmaCount = 0
        var lawCount = 0
        var designCount = 0
        var otherCount = 0

        for (rawCode in courseCodes) {
            val code = rawCode.trim().uppercase()
            val prefix = if (code.length >= 3) code.take(3) else code
            when (prefix) {
                "CSE", "INT", "CAP", "CSN", "CYB", "DTA", "MCA", "BCA" -> cseCount++
                "MKT", "MGM", "FIN", "ACC", "ECO", "HRM", "SCM", "BUS", "COM", "BBA", "MBA" -> mgmtCount++
                "ECE", "EEE", "PEL", "ETE", "ICE", "POW" -> eceCount++
                "MEC", "CIV", "CHE", "MEE", "AUT", "GEO", "STR" -> mechCount++
                "BTY", "BOT", "ZOO", "BIO", "BCH", "GEN", "MIC" -> bioCount++
                "PHA", "PCL", "PCG", "PHM" -> pharmaCount++
                "LAW", "POL", "HIS", "PSY", "SOC", "PUB", "ENG" -> lawCount++
                "DES", "ARC", "FAS", "GAM", "FIL", "IDN" -> designCount++
                "PEA", "PES", "GEN" -> {} // General university foundation
                else -> otherCount++
            }
        }

        val candidates = listOf(
            "cse" to cseCount,
            "mgmt" to mgmtCount,
            "ece" to eceCount,
            "mech" to mechCount,
            "bio" to bioCount,
            "pharma" to pharmaCount,
            "law" to lawCount,
            "design" to designCount,
            "other" to otherCount
        )

        val top = candidates.maxByOrNull { it.second }
        return if (top != null && top.second > 0) top.first else "cse"
    }

    /**
     * Records student department once per device.
     */
    fun recordStudentDepartment(context: Context, courseCodes: List<String>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (courseCodes.isNotEmpty() && !prefs.getBoolean("has_registered_dept_v2", false)) {
            val deptKey = detectDepartment(courseCodes)
            pingCounter("dept_$deptKey")
            prefs.edit().putBoolean("has_registered_dept_v2", true).putString("cached_dept", deptKey).apply()
            Log.i(TAG, "Student department recorded: dept_$deptKey")
        }
    }
}
