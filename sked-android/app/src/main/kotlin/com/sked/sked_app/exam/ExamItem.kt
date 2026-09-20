package com.sked.sked_app.exam

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

enum class ExamStatus {
    TODAY,
    TOMORROW,
    UPCOMING,
    COMPLETED
}

data class ExamItem(
    val courseCode: String,
    val courseTitle: String = "",
    val dateStr: String,          // Format: "yyyy-MM-dd" e.g. "2026-10-15"
    val dayName: String = "",      // e.g. "Thursday"
    val timeSlot: String,         // e.g. "09:00 AM – 12:00 PM"
    val session: String = "Morning", // "Morning" or "Evening"
    val examType: String = "ETE", // "ETE" (End Term), "MTE" (Mid Term), "PRAC" (Practical)
    val room: String = "",        // e.g. "33-612" or "Block 33"
    val seatNo: String = "",      // e.g. "Desk A-14"
    val reportingTime: String = ""// e.g. "08:30 AM"
) {
    /**
     * Parses the date string and returns the calendar instance at midnight.
     */
    fun getExamDate(): Date? {
        return try {
            val formats = listOf(
                SimpleDateFormat("yyyy-MM-dd", Locale.US),
                SimpleDateFormat("dd-MM-yyyy", Locale.US),
                SimpleDateFormat("dd MMM yyyy", Locale.US),
                SimpleDateFormat("MMM dd, yyyy", Locale.US)
            )
            for (fmt in formats) {
                try {
                    val parsed = fmt.parse(dateStr.trim())
                    if (parsed != null) return parsed
                } catch (_: Exception) {}
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Formats the date into a human readable format: e.g. "Thu, 15 Oct"
     */
    fun formattedDate(): String {
        val parsed = getExamDate() ?: return dateStr
        return SimpleDateFormat("EEE, dd MMM yyyy", Locale.US).format(parsed)
    }

    /**
     * Calculates the days remaining between today and the exam date.
     */
    fun daysUntil(): Int {
        val examDate = getExamDate() ?: return 999
        val todayCal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val examCal = Calendar.getInstance().apply {
            time = examDate
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val diffMillis = examCal.timeInMillis - todayCal.timeInMillis
        return TimeUnit.MILLISECONDS.toDays(diffMillis).toInt()
    }

    /**
     * Computes the current exam status badge.
     */
    fun getStatus(): ExamStatus {
        val days = daysUntil()
        return when {
            days < 0 -> ExamStatus.COMPLETED
            days == 0 -> ExamStatus.TODAY
            days == 1 -> ExamStatus.TOMORROW
            else -> ExamStatus.UPCOMING
        }
    }

    /**
     * Display label for status tag.
     */
    fun statusLabel(): String {
        val days = daysUntil()
        return when {
            days < 0 -> "OVER"
            days == 0 -> "TODAY"
            days == 1 -> "TOMORROW"
            else -> "IN $days DAYS"
        }
    }
}
