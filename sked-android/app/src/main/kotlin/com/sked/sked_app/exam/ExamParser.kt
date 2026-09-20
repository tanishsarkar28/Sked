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

    fun getCourseTitle(code: String): String {
        return when (code.uppercase()) {
            "PEA306" -> "Analytical Skills-II"
            "CSE408" -> "Design and Analysis of Algorithms"
            "INT257" -> "Software Project Management"
            "INT252" -> "Web App Development with ReactJS"
            "PES390" -> "Soft Skills"
            "MKT311" -> "Marketing"
            else -> ""
        }
    }

    /**
     * Official examination schedule for current term as published on studentums.lpu.in.
     */
    fun getVerifiedSchedule(): List<ExamItem> {
        return listOf(
            ExamItem(
                courseCode = "PEA306",
                courseTitle = "Analytical Skills-II",
                dateStr = "01 Oct 2026",
                dayName = "Thursday",
                timeSlot = "12:30 PM – 01:30 PM",
                session = "Evening",
                examType = "MTE",
                room = "Seating Awaited",
                seatNo = "Awaited",
                reportingTime = "Report 12:00 PM"
            ),
            ExamItem(
                courseCode = "CSE408",
                courseTitle = "Design and Analysis of Algorithms",
                dateStr = "06 Oct 2026",
                dayName = "Tuesday",
                timeSlot = "12:30 PM – 02:00 PM",
                session = "Evening",
                examType = "MTE",
                room = "Seating Awaited",
                seatNo = "Awaited",
                reportingTime = "Report 12:00 PM"
            ),
            ExamItem(
                courseCode = "CSE408",
                courseTitle = "Design and Analysis of Algorithms",
                dateStr = "15 Dec 2026",
                dayName = "Tuesday",
                timeSlot = "01:30 PM – 04:30 PM",
                session = "Evening",
                examType = "ETE",
                room = "Seating Awaited",
                seatNo = "Awaited",
                reportingTime = "Report 01:00 PM"
            ),
            ExamItem(
                courseCode = "PEA306",
                courseTitle = "Analytical Skills-II",
                dateStr = "21 Dec 2026",
                dayName = "Monday",
                timeSlot = "01:30 PM – 03:30 PM",
                session = "Evening",
                examType = "ETE",
                room = "Seating Awaited",
                seatNo = "Awaited",
                reportingTime = "Report 01:00 PM"
            )
        ).sortedBy { it.getExamDate()?.time ?: Long.MAX_VALUE }
    }

    /**
     * Reads saved exam list from SharedPreferences.
     * Falls back to verified studentums datesheet if not yet synced.
     */
    fun loadExamsFromPrefs(context: Context): List<ExamItem> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_EXAMS_JSON, null)
        if (!raw.isNullOrBlank() && raw != "[]") {
            try {
                val arr = JSONArray(raw)
                val list = (0 until arr.length()).map { i ->
                    val obj = arr.getJSONObject(i)
                    ExamItem(
                        courseCode = obj.optString("courseCode"),
                        courseTitle = obj.optString("courseTitle"),
                        dateStr = obj.optString("dateStr"),
                        dayName = obj.optString("dayName"),
                        timeSlot = obj.optString("timeSlot", "09:00 AM – 12:00 PM"),
                        session = obj.optString("session", "Morning"),
                        examType = obj.optString("examType", "MTE"),
                        room = obj.optString("room"),
                        seatNo = obj.optString("seatNo"),
                        reportingTime = obj.optString("reportingTime")
                    )
                }
                if (list.isNotEmpty()) return list.sortedBy { it.getExamDate()?.time ?: Long.MAX_VALUE }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load exams from prefs", e)
            }
        }

        // Fallback to verified official 4-exam datesheet
        val verified = getVerifiedSchedule()
        saveExamsToPrefs(context, verified)
        return verified
    }

    /**
     * Purges saved exam cache.
     */
    fun clearExams(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_EXAMS_JSON).apply()
    }

    /**
     * Parses datesheet from either modern studentums.lpu.in card text or classic UMS HTML tables.
     */
    fun parseDatesheetHtml(raw: String): List<ExamItem> {
        val items = mutableListOf<ExamItem>()
        if (raw.isBlank()) return items

        try {
            val tagStrip = Regex("""<[^>]+>|&nbsp;|\t""")

            // 1. First attempt: Line-by-line card format (from studentums.lpu.in)
            val cleanText = tagStrip.replace(raw, "\n")
            val lines = cleanText.split(Regex("""[\r\n]+"""))
                .map { it.replace(Regex("""\s+"""), " ").trim() }
                .filter { it.isNotBlank() }

            val foundKeys = mutableSetOf<String>()

            for (i in lines.indices) {
                val line = lines[i]
                val codeMatch = Regex("""\b([A-Z]{2,5}\d{3,4})\b""").find(line)
                if (codeMatch != null && !line.contains("Term", ignoreCase = true) && !line.contains("Total", ignoreCase = true)) {
                    val code = codeMatch.groupValues[1]
                    var title = line.substringAfter(code).trim().removePrefix("-").removePrefix("–").trim()
                    if (title.isBlank()) title = getCourseTitle(code)
                    var dateStr = ""
                    var timeSlot = "09:00 AM – 12:00 PM"
                    var reporting = ""
                    var room = "Seating Awaited"
                    val seatNo = "Awaited"
                    var examType = "MTE"

                    // Look ahead in subsequent lines for details of this exam
                    for (j in (i + 1)..minOf(lines.size - 1, i + 8)) {
                        val nextLine = lines[j]
                        if (j > i + 1 && Regex("""^[A-Z]{2,5}\d{3,4}\b""").containsMatchIn(nextLine)) {
                            break
                        }

                        val dMatch = Regex("""\b(\d{1,2}\s+[A-Za-z]{3}\s+\d{4}|\d{1,2}[-/](?:[A-Za-z]{3}|\d{1,2})[-/]\d{2,4})\b""").find(nextLine)
                        if (dMatch != null && dateStr.isBlank()) {
                            dateStr = dMatch.groupValues[1]
                        }

                        val tMatch = Regex("""\b(\d{1,2}:\d{2}\s*[–\-]\s*\d{1,2}:\d{2}(?:\s*(?:AM|PM))?|\d{1,2}:\d{2}\s*(?:AM|PM))\b""", RegexOption.IGNORE_CASE).find(nextLine)
                        if (tMatch != null && (timeSlot == "09:00 AM – 12:00 PM" || timeSlot.isBlank())) {
                            timeSlot = tMatch.groupValues[1]
                        }

                        val rMatch = Regex("""Report\s+([^\]]+)""", RegexOption.IGNORE_CASE).find(nextLine)
                        if (rMatch != null && reporting.isBlank()) {
                            reporting = "Report " + rMatch.groupValues[1].take(30).trim()
                        }

                        if (Regex("""Block\s+\d+|Room\s+\d+|\d{2}-\d{3}""", RegexOption.IGNORE_CASE).containsMatchIn(nextLine)) {
                            room = nextLine
                        } else if (nextLine.contains("Awaited", ignoreCase = true)) {
                            room = "Seating Awaited"
                        }

                        if (Regex("""Mid\s*Term|MTE""", RegexOption.IGNORE_CASE).containsMatchIn(nextLine)) {
                            examType = "MTE"
                        } else if (Regex("""End\s*Term|ETE""", RegexOption.IGNORE_CASE).containsMatchIn(nextLine)) {
                            examType = "ETE"
                        } else if (Regex("""Practical|Lab|PRAC""", RegexOption.IGNORE_CASE).containsMatchIn(nextLine)) {
                            examType = "PRAC"
                        }
                    }

                    if (dateStr.isNotBlank()) {
                        val key = "${code}_${dateStr}"
                        if (!foundKeys.contains(key)) {
                            foundKeys.add(key)
                            items.add(
                                ExamItem(
                                    courseCode = code,
                                    courseTitle = title,
                                    dateStr = dateStr,
                                    timeSlot = timeSlot,
                                    session = if (timeSlot.contains("PM", ignoreCase = true) && !timeSlot.contains("09:") && !timeSlot.contains("10:") && !timeSlot.contains("11:")) "Evening" else "Morning",
                                    examType = examType,
                                    room = room,
                                    seatNo = seatNo,
                                    reportingTime = reporting
                                )
                            )
                        }
                    }
                }
            }

            // 2. Fallback attempt: Table row parsing (classic UMS HTML)
            if (items.isEmpty()) {
                val rowRegex = Regex("""<tr[^>]*>([\s\S]*?)<\/tr>""", RegexOption.IGNORE_CASE)
                val cellRegex = Regex("""<td[^>]*>([\s\S]*?)<\/td>""", RegexOption.IGNORE_CASE)
                val rows = rowRegex.findAll(raw).toList()
                for (r in rows) {
                    val rowContent = r.groupValues[1]
                    val cells = cellRegex.findAll(rowContent)
                        .map { tagStrip.replace(it.groupValues[1], " ").replace(Regex("""\s+"""), " ").trim() }
                        .filter { it.isNotBlank() }
                        .toList()

                    if (cells.size >= 3) {
                        val codeIdx = cells.indexOfFirst { it.matches(Regex("""^[A-Z]{2,5}[0-9]{3,4}$""")) }
                        if (codeIdx != -1) {
                            val code = cells[codeIdx]
                            val title = cells.getOrNull(codeIdx + 1)?.takeIf { !it.matches(Regex(""".*\d{1,2}[-/].*""")) } ?: ""

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
                                raw.contains("Mid Term", ignoreCase = true) || title.contains("Mid Term", ignoreCase = true) -> "MTE"
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
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing datesheet", e)
        }

        return items.sortedBy { it.getExamDate()?.time ?: Long.MAX_VALUE }
    }
}
