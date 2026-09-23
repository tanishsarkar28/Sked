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
        return when (code.uppercase().trim()) {
            "PEA306" -> "Analytical Skills-II"
            "PEA305" -> "Analytical Skills-I"
            "PEA307" -> "Analytical Skills-III"
            "PEA308" -> "Analytical Skills-IV"
            "CSE408" -> "Design and Analysis of Algorithms"
            "INT257" -> "Software Project Management"
            "INT252" -> "Web App Development with ReactJS"
            "INT219" -> "Front End Web Development"
            "INT222" -> "Advanced Web Development"
            "INT306" -> "Database Management Systems"
            "CSE316" -> "Operating Systems"
            "CSE325" -> "Operating Systems Laboratory"
            "CSE205" -> "Data Structures and Algorithms"
            "CSE202" -> "Object Oriented Programming"
            "CSE306" -> "Computer Networks"
            "CSE307" -> "Computer Networks Laboratory"
            "CSE320" -> "Software Engineering"
            "CSE310" -> "Programming in Java"
            "CSE311" -> "Java Laboratory"
            "CSE101" -> "Computer Programming"
            "PES390" -> "Soft Skills"
            "PES318" -> "Soft Skills-II"
            "MKT311" -> "Marketing"
            "MTH401" -> "Discrete Mathematics"
            "MTH166" -> "Differential Equations"
            "PHY110" -> "Engineering Physics"
            "CHE110" -> "Engineering Chemistry"
            "PEL121" -> "Communication Skills-I"
            "PEL131" -> "Communication Skills-II"
            else -> ""
        }
    }

    fun formatTimeSlot(raw: String): String {
        val m = Regex("""(\d{1,2}):(\d{2})\s*[–\-]\s*(\d{1,2}):(\d{2})""").find(raw) ?: return raw
        val h1 = m.groupValues[1].toIntOrNull() ?: return raw
        val m1 = m.groupValues[2]
        val h2 = m.groupValues[3].toIntOrNull() ?: return raw
        val m2 = m.groupValues[4]

        fun to12(h: Int, min: String): String {
            val ampm = if (h >= 12) "PM" else "AM"
            val h12 = when {
                h == 0 -> 12
                h > 12 -> h - 12
                else -> h
            }
            return String.format(java.util.Locale.US, "%02d:%s %s", h12, min, ampm)
        }
        return "${to12(h1, m1)} – ${to12(h2, m2)}"
    }

    /**
     * Reads saved exam list from SharedPreferences.
     * Sanitizes any legacy or timetable artifact titles (e.g., "Lecture / G:All C:PEA306...").
     */
    fun loadExamsFromPrefs(context: Context): List<ExamItem> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_EXAMS_JSON, null)
        if (!raw.isNullOrBlank() && raw != "[]") {
            try {
                val arr = JSONArray(raw)
                val list = (0 until arr.length()).map { i ->
                    val obj = arr.getJSONObject(i)
                    val code = obj.optString("courseCode")
                    val rawTitle = obj.optString("courseTitle")
                    val cleanTitle = if (rawTitle.contains("Lecture", true) ||
                        rawTitle.contains("Practical", true) ||
                        rawTitle.contains("Teacher:", true) ||
                        rawTitle.contains("G:All", true) ||
                        rawTitle.contains("R:", true) ||
                        rawTitle.contains("S:", true) ||
                        rawTitle.contains("C:", true) ||
                        rawTitle.startsWith("/") ||
                        rawTitle.startsWith(":")
                    ) {
                        getCourseTitle(code)
                    } else rawTitle.ifBlank { getCourseTitle(code) }

                    val rawType = obj.optString("examType", "MTE")
                    val cleanType = when (rawType.uppercase().trim()) {
                        "PRAC", "PRACTICAL", "LAB" -> "ETP"
                        "MTP" -> "MTP"
                        "MTE", "MID", "MID TERM" -> "MTE"
                        "ETE", "END", "END TERM" -> "ETE"
                        else -> rawType.ifBlank { "MTE" }
                    }

                    ExamItem(
                        courseCode = code,
                        courseTitle = cleanTitle,
                        dateStr = obj.optString("dateStr"),
                        dayName = obj.optString("dayName"),
                        timeSlot = obj.optString("timeSlot", "09:00 AM – 12:00 PM"),
                        session = obj.optString("session", "Morning"),
                        examType = cleanType,
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

        // Return empty list if no real datesheet has been synced yet
        return emptyList()
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
    fun parseDatesheetHtml(raw: String, courseTitleMap: Map<String, String> = emptyMap()): List<ExamItem> {
        val items = mutableListOf<ExamItem>()
        if (raw.isBlank()) return items

        try {
            // 0. Pre-check: Direct JSON parsing if raw contains JSON array with CourseCode
            val jsonStart = raw.indexOf("[{\"")
            if (jsonStart != -1) {
                val jsonEnd = raw.lastIndexOf("}]")
                if (jsonEnd != -1 && jsonEnd > jsonStart) {
                    try {
                        val jsonSub = raw.substring(jsonStart, jsonEnd + 2)
                        val arr = JSONArray(jsonSub)
                        for (k in 0 until arr.length()) {
                            val obj = arr.getJSONObject(k)
                            val code = obj.optString("CourseCode").trim()
                            if (code.isNotBlank()) {
                                val title = obj.optString("CourseName").ifBlank { courseTitleMap[code.uppercase()] ?: getCourseTitle(code) }
                                val dStr = obj.optString("ExamDate")
                                val tSlot = obj.optString("ExamTime", "09:00 AM – 12:00 PM")
                                val roomNo = obj.optString("RoomNo").ifBlank { "Seating Awaited" }
                                val repTime = obj.optString("ReportingTime")
                                val typeDesc = obj.optString("ExamTypeDesc", "MTE")
                                items.add(
                                    ExamItem(
                                        courseCode = code,
                                        courseTitle = title,
                                        dateStr = dStr,
                                        timeSlot = formatTimeSlot(tSlot),
                                        session = if (tSlot.contains("PM", ignoreCase = true) && !tSlot.contains("09:") && !tSlot.contains("10:") && !tSlot.contains("11:")) "Evening" else "Morning",
                                        examType = if (typeDesc.contains("End", true) || typeDesc.contains("ETE", true)) "ETE" else "MTE",
                                        room = roomNo,
                                        seatNo = "Awaited",
                                        reportingTime = if (repTime.isNotBlank()) "Report $repTime" else ""
                                    )
                                )
                            }
                        }
                        if (items.isNotEmpty()) return items.sortedBy { it.getExamDate()?.time ?: Long.MAX_VALUE }
                    } catch (_: Exception) {}
                }
            }

            val tagStrip = Regex("""<[^>]+>|&nbsp;|\t""")

            // 1. First attempt: Line-by-line card format (from studentums.lpu.in)
            val cleanText = tagStrip.replace(raw, "\n")
            val lines = cleanText.split(Regex("""[\r\n]+"""))
                .map { it.replace(Regex("""\s+"""), " ").trim() }
                .filter { it.isNotBlank() }

            val foundKeys = mutableSetOf<String>()

            for (i in lines.indices) {
                val line = lines[i]
                if (line.contains("Lecture", ignoreCase = true) || line.contains("Teacher:", ignoreCase = true) || line.contains("G:All", ignoreCase = true)) {
                    continue
                }
                val codeMatch = Regex("""\b([A-Z]{2,5}\d{3,4})\b""").find(line)
                if (codeMatch != null && !line.contains("Term", ignoreCase = true) && !line.contains("Total", ignoreCase = true)) {
                    val code = codeMatch.groupValues[1]
                    var rawTitle = line.substringAfter(code).trim().removePrefix("-").removePrefix("–").trim()
                    var title = if (rawTitle.isBlank() ||
                        rawTitle.contains("Lecture", true) ||
                        rawTitle.contains("Practical", true) ||
                        rawTitle.contains("Teacher:", true) ||
                        rawTitle.contains("G:All", true) ||
                        rawTitle.contains("G:", true) ||
                        rawTitle.contains("R:", true) ||
                        rawTitle.contains("S:", true) ||
                        rawTitle.contains("C:", true) ||
                        rawTitle.startsWith("/") ||
                        rawTitle.startsWith(":")
                    ) {
                        getCourseTitle(code)
                    } else rawTitle.ifBlank { getCourseTitle(code) }

                    var dateStr = ""
                    var timeSlot = "09:00 AM – 12:00 PM"
                    var reporting = ""
                    var room = "Seating Awaited"
                    var seatNo = "Awaited"
                    var examType = "MTE"

                    // Look ahead in subsequent lines for details of this exam
                    for (j in (i + 1)..minOf(lines.size - 1, i + 8)) {
                        val nextLine = lines[j]
                        if (j > i + 1 && Regex("""^[A-Z]{2,5}\d{3,4}\b""").containsMatchIn(nextLine)) {
                            break
                        }
                        val dMatch = Regex("""\b(\d{1,2}\s+[A-Za-z]{3,9}\s+\d{2,4}|\d{1,2}[-/.](?:[A-Za-z]{3,9}|\d{1,2})[-/.]\d{2,4}|[A-Za-z]{3,9}\s+\d{1,2},?\s+\d{2,4})\b""").find(nextLine)
                        if (dMatch != null && dateStr.isBlank()) {
                            dateStr = dMatch.groupValues[1]
                        }

                        val tMatch = Regex("""\b(\d{1,2}:\d{2}\s*[–\-]\s*\d{1,2}:\d{2}(?:\s*(?:AM|PM))?|\d{1,2}:\d{2}\s*(?:AM|PM))\b""", RegexOption.IGNORE_CASE).find(nextLine)
                        if (tMatch != null && (timeSlot == "09:00 AM – 12:00 PM" || timeSlot.isBlank())) {
                            timeSlot = formatTimeSlot(tMatch.groupValues[1])
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

                        val seatMatch = Regex("""(?:Seat|Desk)(?:\s*No\.?)?\s*[:\-]?\s*([A-Za-z0-9\-]+)""", RegexOption.IGNORE_CASE).find(nextLine)
                        if (seatMatch != null && seatNo == "Awaited") {
                            seatNo = seatMatch.groupValues[1].trim()
                        }

                        if (Regex("""Mid\s*Term|MTE""", RegexOption.IGNORE_CASE).containsMatchIn(nextLine)) {
                            examType = "MTE"
                        } else if (Regex("""End\s*Term|ETE""", RegexOption.IGNORE_CASE).containsMatchIn(nextLine)) {
                            examType = "ETE"
                        } else if (Regex("""Practical|Lab|PRAC|ETP""", RegexOption.IGNORE_CASE).containsMatchIn(nextLine)) {
                            examType = "ETP"
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
                val cellRegex = Regex("""<(?:td|th)[^>]*>([\s\S]*?)<\/(?:td|th)>""", RegexOption.IGNORE_CASE)
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
                            var title = cells.getOrNull(codeIdx + 1)?.takeIf { !it.matches(Regex(""".*\d{1,2}[-/].*""")) } ?: ""
                            if (title.isBlank() || title.contains("Lecture") || title.contains("Teacher:") || title.contains("G:All") || title.startsWith("/")) {
                                title = getCourseTitle(code)
                            }

                            val dateCell = cells.find {
                                it.matches(Regex(""".*\b(\d{1,2}[-/.](?:[A-Za-z]{3,9}|\d{1,2})[-/.]\d{2,4}|[A-Za-z]{3,9}\s+\d{1,2},?\s+\d{2,4}|\d{1,2}\s+[A-Za-z]{3,9}\s+\d{2,4})\b.*"""))
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
                                code.endsWith("P", ignoreCase = true) || title.contains("Practical", ignoreCase = true) || title.contains("Lab", ignoreCase = true) -> "ETP"
                                raw.contains("Mid Term", ignoreCase = true) || title.contains("Mid Term", ignoreCase = true) -> "MTE"
                                else -> "ETE"
                            }

                            if (dateCell.isNotBlank()) {
                                val cleanDate = Regex("""\b(\d{1,2}[-/.](?:[A-Za-z]{3,9}|\d{1,2})[-/.]\d{2,4}|[A-Za-z]{3,9}\s+\d{1,2},?\s+\d{2,4}|\d{1,2}\s+[A-Za-z]{3,9}\s+\d{2,4})\b""")
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
