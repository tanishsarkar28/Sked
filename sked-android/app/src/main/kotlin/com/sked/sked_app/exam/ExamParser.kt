package com.sked.sked_app.exam

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

object ExamParser {

    private const val TAG = "ExamParser"
    private const val PREFS_NAME = "SkedExamPrefs"
    private const val KEY_EXAMS_JSON = "sked_exam_entries"
    private const val KEY_LAST_SYNCED = "sked_exam_last_synced"

    /**
     * Persists real exam list to SharedPreferences.
     */
    fun saveExamsToPrefs(context: Context, exams: List<ExamItem>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val arr = JSONArray()
        exams.forEach { item ->
            arr.put(JSONObject().apply {
                put("courseCode", item.courseCode)
                put("courseTitle", item.courseTitle)
                put("dateStr", item.dateStr)
                put("dayName", item.dayName)
                put("timeSlot", item.timeSlot)
                put("session", item.session)
                put("examType", item.examType)
                put("room", item.room)
                put("seatNo", item.seatNo)
                put("reportingTime", item.reportingTime)
            })
        }
        prefs.edit()
            .putString(KEY_EXAMS_JSON, arr.toString())
            .putLong(KEY_LAST_SYNCED, System.currentTimeMillis())
            .apply()
    }

    /**
     * Reads saved exam list from SharedPreferences.
     */
    fun loadExamsFromPrefs(context: Context): List<ExamItem> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_EXAMS_JSON, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                ExamItem(
                    courseCode = obj.optString("courseCode"),
                    courseTitle = obj.optString("courseTitle"),
                    dateStr = obj.optString("dateStr"),
                    dayName = obj.optString("dayName"),
                    timeSlot = obj.optString("timeSlot", "09:00 AM – 12:00 PM"),
                    session = obj.optString("session", "Morning"),
                    examType = obj.optString("examType", "ETE"),
                    room = obj.optString("room"),
                    seatNo = obj.optString("seatNo"),
                    reportingTime = obj.optString("reportingTime")
                )
            }.sortedBy { it.getExamDate()?.time ?: Long.MAX_VALUE }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load exams from prefs", e)
            emptyList()
        }
    }

    /**
     * Purges saved exam cache.
     */
    fun clearExams(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_EXAMS_JSON).apply()
    }

    /**
     * Parses real HTML returned from UMS frmStudentDateSheet.aspx or frmStudentExamSchedule.aspx.
     */
    fun parseDatesheetHtml(html: String): List<ExamItem> {
        val items = mutableListOf<ExamItem>()
        if (html.isBlank()) return items

        try {
            // Find all table rows
            val rowRegex = Regex("""<tr[^>]*>([\s\S]*?)<\/tr>""", RegexOption.IGNORE_CASE)
            val cellRegex = Regex("""<td[^>]*>([\s\S]*?)<\/td>""", RegexOption.IGNORE_CASE)
            val tagStrip = Regex("""<[^>]+>|&nbsp;|\r|\n|\t""")

            val rows = rowRegex.findAll(html).toList()
            for (r in rows) {
                val rowContent = r.groupValues[1]
                val cells = cellRegex.findAll(rowContent)
                    .map { tagStrip.replace(it.groupValues[1], " ").replace(Regex("""\s+"""), " ").trim() }
                    .filter { it.isNotBlank() }
                    .toList()

                if (cells.size >= 3) {
                    // Look for course code e.g. INT257, CSE408, PEA306, CAP123, CHE110, MEC101
                    val codeIdx = cells.indexOfFirst { it.matches(Regex("""^[A-Z]{2,5}[0-9]{3,4}$""")) }
                    if (codeIdx != -1) {
                        val code = cells[codeIdx]
                        val title = cells.getOrNull(codeIdx + 1)?.takeIf { !it.matches(Regex(""".*\d{1,2}[-/].*""")) } ?: ""

                        // Look for date in cells (e.g. 24/11/2026 or 24-Nov-2026 or 24-11-2026 or Oct 15, 2026)
                        val dateCell = cells.find {
                            it.matches(Regex(""".*\d{1,2}[-/]([A-Za-z]{3}|\d{1,2})[-/]\d{2,4}.*""")) ||
                            it.matches(Regex("""[A-Za-z]{3,9}\s+\d{1,2},?\s+\d{4}"""))
                        } ?: ""

                        val timeCell = cells.find {
                            (it.contains("AM", ignoreCase = true) || it.contains("PM", ignoreCase = true)) &&
                            it.matches(Regex(""".*\d{1,2}:\d{2}.*"""))
                        } ?: "09:00 AM – 12:00 PM"

                        val roomCell = cells.find {
                            it.contains("Block", ignoreCase = true) || it.contains("Room", ignoreCase = true) || it.matches(Regex("""\d{2}-\d{3}"""))
                        } ?: ""

                        val seatCell = cells.find {
                            it.contains("Desk", ignoreCase = true) || it.contains("Seat", ignoreCase = true) || it.matches(Regex("""[A-Z]-\d{1,3}"""))
                        } ?: ""

                        val reportingCell = cells.find {
                            it.contains("Report", ignoreCase = true)
                        } ?: ""

                        val examType = when {
                            code.endsWith("P", ignoreCase = true) || title.contains("Practical", ignoreCase = true) || title.contains("Lab", ignoreCase = true) -> "PRAC"
                            html.contains("Mid Term", ignoreCase = true) || title.contains("Mid Term", ignoreCase = true) -> "MTE"
                            else -> "ETE"
                        }

                        if (dateCell.isNotBlank()) {
                            val cleanDate = Regex("""\b(\d{1,2}[-/]([A-Za-z]{3}|\d{1,2})[-/]\d{2,4}|[A-Za-z]{3,9}\s+\d{1,2},?\s+\d{4})\b""")
                                .find(dateCell)?.value ?: dateCell

                            items.add(
                                ExamItem(
                                    courseCode = code,
                                    courseTitle = title,
                                    dateStr = cleanDate,
                                    timeSlot = timeCell,
                                    session = if (timeCell.contains("PM", ignoreCase = true) && !timeCell.contains("09:") && !timeCell.contains("10:")) "Evening" else "Morning",
                                    examType = examType,
                                    room = roomCell,
                                    seatNo = seatCell,
                                    reportingTime = reportingCell
                                )
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing datesheet HTML", e)
        }

        return items.sortedBy { it.getExamDate()?.time ?: Long.MAX_VALUE }
    }
}
