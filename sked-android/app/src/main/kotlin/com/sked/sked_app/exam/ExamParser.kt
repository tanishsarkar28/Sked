package com.sked.sked_app.exam

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object ExamParser {

    private const val TAG = "ExamParser"
    private const val PREFS_NAME = "SkedExamPrefs"
    private const val KEY_EXAMS_JSON = "sked_exam_entries"
    private const val KEY_LAST_SYNCED = "sked_exam_last_synced"

    /**
     * Persists exam list to SharedPreferences.
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
     * Parses HTML returned from UMS frmStudentDateSheet.aspx or frmStudentExamSchedule.aspx.
     */
    fun parseDatesheetHtml(html: String): List<ExamItem> {
        val items = mutableListOf<ExamItem>()
        if (html.isBlank()) return items

        try {
            // Find all table rows
            val rowRegex = Regex("""<tr[^>]*>([\s\S]*?)<\/tr>""", RegexOption.IGNORE_CASE)
            val cellRegex = Regex("""<td[^>]*>([\s\S]*?)<\/td>""", RegexOption.IGNORE_CASE)
            val tagStrip = Regex("""<[^>]+>|&nbsp;|\r|\n""")

            val rows = rowRegex.findAll(html).toList()
            for (r in rows) {
                val rowContent = r.groupValues[1]
                val cells = cellRegex.findAll(rowContent)
                    .map { tagStrip.replace(it.groupValues[1], "").trim() }
                    .filter { it.isNotBlank() }
                    .toList()

                if (cells.size >= 4) {
                    // Look for course code like CSE408, INT257
                    val codeIdx = cells.indexOfFirst { it.matches(Regex("""^[A-Z]{2,4}[0-9]{3,4}$""")) }
                    if (codeIdx != -1) {
                        val code = cells[codeIdx]
                        val title = cells.getOrNull(codeIdx + 1) ?: ""

                        // Look for date in subsequent cells (e.g. 24/11/2026 or 24-Nov-2026 or 24-11-2026)
                        val dateCell = cells.find {
                            it.matches(Regex(""".*\d{1,2}[-/]([A-Za-z]{3}|\d{1,2})[-/]\d{2,4}.*"""))
                        } ?: ""

                        val timeCell = cells.find {
                            it.contains("AM", ignoreCase = true) || it.contains("PM", ignoreCase = true) || it.contains(":")
                        } ?: "09:00 AM – 12:00 PM"

                        val roomCell = cells.find {
                            it.contains("Block", ignoreCase = true) || it.contains("Room", ignoreCase = true) || it.matches(Regex("""\d{2}-\d{3}"""))
                        } ?: ""

                        val seatCell = cells.find {
                            it.contains("Desk", ignoreCase = true) || it.contains("Seat", ignoreCase = true) || it.matches(Regex("""[A-Z]-\d{1,3}"""))
                        } ?: ""

                        val examType = when {
                            code.contains("P", ignoreCase = true) || title.contains("Practical", ignoreCase = true) -> "PRAC"
                            html.contains("Mid Term", ignoreCase = true) -> "MTE"
                            else -> "ETE"
                        }

                        items.add(
                            ExamItem(
                                courseCode = code,
                                courseTitle = title,
                                dateStr = dateCell,
                                timeSlot = timeCell,
                                session = if (timeCell.contains("PM", ignoreCase = true) && !timeCell.contains("09:") && !timeCell.contains("10:")) "Evening" else "Morning",
                                examType = examType,
                                room = roomCell,
                                seatNo = seatCell
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing datesheet HTML", e)
        }

        return items.sortedBy { it.getExamDate()?.time ?: Long.MAX_VALUE }
    }

    /**
     * Generates a provisional exam schedule for the student's enrolled courses
     * if the official datesheet is not yet released on UMS.
     */
    fun generateProvisionalSchedule(courseCodes: List<String>): List<ExamItem> {
        val uniqueCodes = courseCodes.distinct().filter { it.isNotBlank() }
        if (uniqueCodes.isEmpty()) return emptyList()

        val cal = Calendar.getInstance()
        // Default to upcoming exam slot starting in 18 days
        cal.add(Calendar.DAY_OF_MONTH, 14)

        val dayFmt = SimpleDateFormat("EEEE", Locale.US)
        val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)

        val courseTitles = mapOf(
            "INT257" to "Software Project Management",
            "CSE408" to "Design & Analysis of Algorithms",
            "INT252" to "Web App Development with ReactJS",
            "MKT311" to "Digital Marketing",
            "PEA306" to "Analytical Skills-II",
            "PEAS01" to "Soft Skills Workshop"
        )

        return uniqueCodes.mapIndexed { index, code ->
            val examCal = (cal.clone() as Calendar).apply {
                add(Calendar.DAY_OF_MONTH, index * 3) // Every 3 days
                // Skip Sundays
                if (get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY) {
                    add(Calendar.DAY_OF_MONTH, 1)
                }
            }

            val isPractical = code.startsWith("INT") || code.contains("PRAC", ignoreCase = true)
            val isMorning = index % 2 == 0

            ExamItem(
                courseCode = code,
                courseTitle = courseTitles[code] ?: "Core University Curriculum",
                dateStr = dateFmt.format(examCal.time),
                dayName = dayFmt.format(examCal.time),
                timeSlot = if (isMorning) "09:00 AM – 12:00 PM" else "01:30 PM – 04:30 PM",
                session = if (isMorning) "Morning Session" else "Evening Session",
                examType = if (isPractical) "PRAC" else "ETE",
                room = "Block 34, Room ${301 + index}",
                seatNo = "Desk ${('A' + (index % 5))}-${10 + index}",
                reportingTime = if (isMorning) "08:30 AM" else "01:00 PM"
            )
        }
    }
}
