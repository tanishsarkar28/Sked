package com.sked.sked_app

import android.content.Context
import android.content.SharedPreferences
import com.sked.sked_app.widget.KEY_TODAY
import com.sked.sked_app.widget.PREFS_NAME
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

/**
 * On-device timetable parser — Kotlin port of sked-backend/src/timetableParser.js
 *
 * Parses the HTML fragment returned by the ASP.NET PageMethod:
 *   POST https://ums.lpu.in/lpuums/frmMyCurrentTimeTable.aspx/GetTimeTable
 *
 * Response shape: { "d": "<html string>" }
 *
 * Each class block has an onclick like:
 *   openPopup("Lecture / G:All C:INT257 / R: 33-612 / S:K2P24RL / Teacher: 30767::Akash Pundir",
 *             "10:20-11:10", "Monday", "33-612", "INT257", "L")
 *
 * Args (0-indexed):
 *   0 — full description string
 *   1 — time range  "HH:MM-HH:MM"
 *   2 — day name    e.g. "Monday"
 *   3 — room        e.g. "33-612"
 *   4 — course code e.g. "INT257"
 *   5 — class type  "L" = Lecture, "P" = Practical, "T" = Tutorial
 */
object TimetableParser {

    // ── Type mapping ─────────────────────────────────────────────────────────

    private val TYPE_LABELS = mapOf("L" to "Lecture", "P" to "Practical", "T" to "Tutorial")

    private val DAY_NAMES = arrayOf(
        "Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"
    )

    // ── Regex patterns ───────────────────────────────────────────────────────

    /**
     * Matches: openPopup("arg0","arg1","arg2","arg3","arg4","arg5")
     * Each arg is captured as a group. Handles escaped quotes inside args.
     */
    private val OPEN_POPUP_REGEX = Regex(
        """openPopup\(\s*"((?:[^"\\]|\\.)*)"\s*,\s*"((?:[^"\\]|\\.)*)"\s*,\s*"((?:[^"\\]|\\.)*)"\s*,\s*"((?:[^"\\]|\\.)*)"\s*,\s*"((?:[^"\\]|\\.)*)"\s*,\s*"((?:[^"\\]|\\.)*)"\s*\)"""
    )

    private val TEACHER_REGEX = Regex("""Teacher:\s*\d+::(.+)$""", RegexOption.IGNORE_CASE)
    private val SECTION_REGEX = Regex("""S:([^\s/]+)""")
    private val GROUP_REGEX = Regex("""G:([^\s/]+)""")

    // ── Parsing helpers ──────────────────────────────────────────────────────

    private fun parseTeacher(description: String): String {
        return TEACHER_REGEX.find(description)?.groupValues?.get(1)?.trim() ?: "Unknown"
    }

    private fun parseSection(description: String): String {
        return SECTION_REGEX.find(description)?.groupValues?.get(1)?.trim() ?: ""
    }

    private fun parseGroup(description: String): String {
        return GROUP_REGEX.find(description)?.groupValues?.get(1)?.trim() ?: ""
    }

    private fun parseTimeRange(range: String): Pair<String, String> {
        val parts = range.split("-")
        return Pair(parts.getOrElse(0) { "" }.trim(), parts.getOrElse(1) { "" }.trim())
    }

    private fun parseType(code: String): String {
        return TYPE_LABELS[code] ?: code
    }

    fun todayName(): String {
        return DAY_NAMES[Calendar.getInstance().get(Calendar.DAY_OF_WEEK) - 1]
    }

    // ── Main parsing ─────────────────────────────────────────────────────────

    /**
     * Parse the raw response from GetTimeTable (JSON string or HTML).
     * Returns a list of ClassItem entries for the entire week.
     *
     * @param rawResponse The raw response — can be:
     *   - JSON string: {"d": "<html>"}
     *   - Just the HTML string
     *   - Double-encoded JSON string from WebView
     */
    fun parse(rawResponse: String): List<ClassItem> {
        val html = extractHtml(rawResponse)
        if (html.isBlank()) return emptyList()

        // Unescape HTML entities so openPopup(&quot;...&quot;) becomes openPopup("...")
        val normalized = html
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&amp;", "&")

        val entries = mutableListOf<ClassItem>()

        // Find all openPopup(...) calls in the HTML
        OPEN_POPUP_REGEX.findAll(normalized).forEach { match ->
            val (description, timeRange, day, room, courseCode, typeCode) = match.destructured
            val (start, end) = parseTimeRange(timeRange)

            entries.add(
                ClassItem(
                    day = day.trim(),
                    timeRange = timeRange.trim(),
                    start = start,
                    end = end,
                    room = room.trim(),
                    courseCode = courseCode.trim(),
                    type = parseType(typeCode.trim()),
                    teacher = parseTeacher(description),
                    section = parseSection(description),
                    group = parseGroup(description),
                    description = description.trim()
                )
            )
        }

        return entries
    }

    /**
     * Extract the HTML string from various possible response formats.
     */
    private fun extractHtml(rawResponse: String): String {
        var trimmed = rawResponse.trim()

        // Handle double-encoded JSON strings from WebView evaluateJavascript
        if (trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            try {
                trimmed = JSONObject("{\"v\":$trimmed}").getString("v").trim()
            } catch (_: Exception) {}
        }

        // If it starts with '<', it's already HTML
        if (trimmed.startsWith("<")) return trimmed

        // Try to parse as JSON
        return try {
            val obj = JSONObject(trimmed)
            // Standard ASP.NET PageMethod response: { "d": "<html>" }
            if (obj.has("d")) {
                obj.getString("d")
            } else {
                trimmed
            }
        } catch (_: Exception) {
            // Maybe it's a plain string — use as-is
            trimmed
        }
    }

    // ── Filtering & grouping ─────────────────────────────────────────────────

    /**
     * Filter entries for a specific day (case-insensitive), sorted by start time.
     */
    fun filterByDay(entries: List<ClassItem>, dayName: String): List<ClassItem> {
        return entries
            .filter { it.day.equals(dayName, ignoreCase = true) }
            .sortedBy { it.start }
    }

    /**
     * Group all entries by day name. Each day's list is sorted by start time.
     */
    fun groupByDay(entries: List<ClassItem>): Map<String, List<ClassItem>> {
        return entries
            .groupBy { it.day }
            .mapValues { (_, dayEntries) -> dayEntries.sortedBy { it.start } }
    }

    // ── SharedPreferences persistence ────────────────────────────────────────

    private const val KEY_ALL_ENTRIES = "all_entries"
    private const val KEY_USER_ID = "sked_user_id"

    /**
     * Save parsed timetable entries to SharedPreferences.
     * Stores both the full week and today's entries for the widget.
     */
    fun saveToPrefs(context: Context, entries: List<ClassItem>, userId: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // Save the full week as JSON array
        val allJson = entriesToJsonArray(entries)
        // Save today's entries for the widget
        val todayEntries = filterByDay(entries, todayName())
        val todayJson = entriesToJsonArray(todayEntries)

        prefs.edit()
            .putString(KEY_ALL_ENTRIES, allJson.toString())
            .putString(KEY_TODAY, todayJson.toString())
            .putString(KEY_USER_ID, userId)
            .apply()
    }

    /**
     * Load all entries from SharedPreferences.
     */
    fun loadFromPrefs(context: Context): List<ClassItem> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_ALL_ENTRIES, null) ?: return emptyList()
        return jsonArrayToEntries(raw)
    }

    /**
     * Load today's entries from SharedPreferences.
     */
    fun loadTodayFromPrefs(context: Context): List<ClassItem> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_TODAY, null) ?: return emptyList()
        return jsonArrayToEntries(raw)
    }

    // ── JSON serialization ───────────────────────────────────────────────────

    fun entriesToJsonArray(entries: List<ClassItem>): JSONArray {
        val arr = JSONArray()
        entries.forEach { item ->
            arr.put(JSONObject().apply {
                put("day", item.day)
                put("timeRange", item.timeRange)
                put("start", item.start)
                put("end", item.end)
                put("room", item.room)
                put("courseCode", item.courseCode)
                put("type", item.type)
                put("teacher", item.teacher)
                put("section", item.section)
                put("group", item.group)
                put("description", item.description)
            })
        }
        return arr
    }

    private fun jsonArrayToEntries(raw: String): List<ClassItem> {
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                ClassItem(
                    day = obj.optString("day"),
                    timeRange = obj.optString("timeRange"),
                    start = obj.optString("start"),
                    end = obj.optString("end"),
                    room = obj.optString("room"),
                    courseCode = obj.optString("courseCode"),
                    type = obj.optString("type", "Lecture"),
                    teacher = obj.optString("teacher"),
                    section = obj.optString("section"),
                    group = obj.optString("group"),
                    description = obj.optString("description")
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
