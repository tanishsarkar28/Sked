package com.sked.sked_app.leave

import java.util.regex.Pattern

object LeaveParser {

    data class ParsedLeaveData(
        val balance: LeaveBalance = LeaveBalance(),
        val profile: LeaveProfile = LeaveProfile(),
        val records: List<LeaveRecord> = emptyList()
    )

    fun parse(html: String): ParsedLeaveData {
        if (html.isBlank()) return ParsedLeaveData()

        val balance = parseBalance(html)
        val profile = parseProfile(html)
        val records = parseRecords(html)

        return ParsedLeaveData(
            balance = balance,
            profile = profile,
            records = records
        )
    }

    private fun parseBalance(html: String): LeaveBalance {
        var dayTotal = 75
        var dayAvailed = 0
        var dayBalance = 75

        var nightTotal = 60
        var nightAvailed = 0
        var nightBalance = 60

        try {
            // Day Leave: RadGrid1
            val grid1Pattern = Pattern.compile("ctl00_cphHeading_RadGrid1.*?<tbody>(.*?)</tbody>", Pattern.DOTALL)
            val m1 = grid1Pattern.matcher(html)
            if (m1.find()) {
                val tbody = m1.group(1) ?: ""
                val countPattern = Pattern.compile("<td>(\\d+)</td>\\s*<td>(Total|Availed|Balance)</td>\\s*<td>(\\d+)</td>")
                val mc = countPattern.matcher(tbody)
                while (mc.find()) {
                    val type = mc.group(2) ?: ""
                    val count = mc.group(3)?.toIntOrNull() ?: 0
                    when (type.lowercase()) {
                        "total" -> dayTotal = count
                        "availed" -> dayAvailed = count
                        "balance" -> dayBalance = count
                    }
                }
            }

            // Night Leave: RadGrid2
            val grid2Pattern = Pattern.compile("ctl00_cphHeading_RadGrid2.*?<tbody>(.*?)</tbody>", Pattern.DOTALL)
            val m2 = grid2Pattern.matcher(html)
            if (m2.find()) {
                val tbody = m2.group(1) ?: ""
                val countPattern = Pattern.compile("<td>(\\d+)</td>\\s*<td>(Total|Availed|Balance)</td>\\s*<td>(\\d+)</td>")
                val mc = countPattern.matcher(tbody)
                while (mc.find()) {
                    val type = mc.group(2) ?: ""
                    val count = mc.group(3)?.toIntOrNull() ?: 0
                    when (type.lowercase()) {
                        "total" -> nightTotal = count
                        "availed" -> nightAvailed = count
                        "balance" -> nightBalance = count
                    }
                }
            }
        } catch (_: Exception) {}

        return LeaveBalance(
            dayTotal = dayTotal,
            dayAvailed = dayAvailed,
            dayBalance = dayBalance,
            nightTotal = nightTotal,
            nightAvailed = nightAvailed,
            nightBalance = nightBalance
        )
    }

    private fun parseProfile(html: String): LeaveProfile {
        fun extractByPattern(regexStr: String): String {
            val p = Pattern.compile(regexStr, Pattern.CASE_INSENSITIVE)
            val m = p.matcher(html)
            return if (m.find()) m.group(1)?.trim() ?: "" else ""
        }

        val uid = extractByPattern("UID\\s*[:\\-]?\\s*(\\d{7,10})")
        val name = extractByPattern("Name\\s*[:\\-]?\\s*</td>\\s*<td[^>]*>\\s*([^<]+)")
        val hostel = extractByPattern("Hostel\\s*[:\\-]?\\s*</td>\\s*<td[^>]*>\\s*([^<]+)")
        val roomNo = extractByPattern("Room\\s*No\\s*[:\\-]?\\s*</td>\\s*<td[^>]*>\\s*([^<]+)")
        val authority = extractByPattern("Sanctioning\\s*Authority\\s*[:\\-]?\\s*</td>\\s*<td[^>]*>\\s*([^<]+)")
        val registeredMobile = extractByPattern("ctl00_cphHeading_txtContactNo[^>]*value=[\"'](\\d{10})[\"']")
        val homeAddress = extractByPattern("ctl00_cphHeading_lblAddress[^>]*>([^<]+)<")

        return LeaveProfile(
            uid = uid,
            name = name,
            hostel = hostel,
            roomNo = roomNo,
            authority = authority,
            registeredMobile = registeredMobile,
            homeAddress = homeAddress
        )
    }

    private fun parseRecords(html: String): List<LeaveRecord> {
        val list = mutableListOf<LeaveRecord>()
        try {
            val gridPattern = Pattern.compile("id=[\"']ctl00_cphHeading_grdLeaveDetail[\"'].*?>(.*?)</table>", Pattern.DOTALL)
            val m = gridPattern.matcher(html)
            if (!m.find()) return emptyList()

            val tableHtml = m.group(1) ?: return emptyList()
            val trPattern = Pattern.compile("<tr[^>]*>(.*?)</tr>", Pattern.DOTALL)
            val trMatcher = trPattern.matcher(tableHtml)

            var isHeader = true
            while (trMatcher.find()) {
                val rowHtml = trMatcher.group(1) ?: continue
                if (isHeader) {
                    isHeader = false
                    continue
                }

                val tdPattern = Pattern.compile("<td[^>]*>(.*?)</td>", Pattern.DOTALL)
                val tdMatcher = tdPattern.matcher(rowHtml)
                val cells = mutableListOf<String>()
                while (tdMatcher.find()) {
                    val rawText = tdMatcher.group(1) ?: ""
                    // Strip HTML tags and entities
                    val clean = rawText
                        .replace(Regex("<[^>]+>"), "")
                        .replace("&nbsp;", " ")
                        .trim()
                    cells.add(clean)
                }

                if (cells.size >= 8 && cells[0].isNotBlank()) {
                    list.add(
                        LeaveRecord(
                            leaveId = cells[0],
                            leaveType = cells.getOrElse(1) { "" },
                            applyDate = cells.getOrElse(2) { "" },
                            leaveDate = cells.getOrElse(3) { "" },
                            returnDate = cells.getOrElse(4) { "" },
                            status = cells.getOrElse(6) { "Approved" },
                            checkOut = cells.getOrElse(7) { "" },
                            checkIn = cells.getOrElse(8) { "" }
                        )
                    )
                }
            }
        } catch (_: Exception) {}

        return list
    }
}
