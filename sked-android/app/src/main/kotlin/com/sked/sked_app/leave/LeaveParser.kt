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

        fun parseGrid(gridId: String): Triple<Int, Int, Int>? {
            val gridPattern = Pattern.compile("$gridId.*?<tbody>(.*?)</tbody>", Pattern.DOTALL)
            val m = gridPattern.matcher(html)
            if (!m.find()) return null
            val tbody = m.group(1) ?: return null

            var tot = 0
            var av = 0
            var bal = 0

            // Match ASP.NET RadGrid TypeLabel and CountLabel spans
            val rowPattern = Pattern.compile("TypeLabel[^>]*>\\s*([^<]+)\\s*</span>.*?CountLabel[^>]*>\\s*(\\d+)\\s*</span>", Pattern.DOTALL)
            val rm = rowPattern.matcher(tbody)
            var foundAny = false
            while (rm.find()) {
                foundAny = true
                val type = rm.group(1)?.trim()?.lowercase() ?: ""
                val count = rm.group(2)?.toIntOrNull() ?: 0
                when (type) {
                    "total" -> tot = count
                    "availed" -> av = count
                    "balance" -> bal = count
                }
            }

            // Fallback pattern if span structure differs
            if (!foundAny) {
                val fallback = Pattern.compile("(Total|Availed|Balance)[^\\d]*(\\d+)", Pattern.DOTALL or Pattern.CASE_INSENSITIVE)
                val fm = fallback.matcher(tbody)
                while (fm.find()) {
                    val type = fm.group(1)?.trim()?.lowercase() ?: ""
                    val count = fm.group(2)?.toIntOrNull() ?: 0
                    when (type) {
                        "total" -> tot = count
                        "availed" -> av = count
                        "balance" -> bal = count
                    }
                }
            }
            return Triple(tot, av, bal)
        }

        try {
            parseGrid("ctl00_cphHeading_RadGrid1")?.let { (tot, av, bal) ->
                if (tot > 0) {
                    dayTotal = tot
                    dayAvailed = av
                    dayBalance = bal
                }
            }
            parseGrid("ctl00_cphHeading_RadGrid2")?.let { (tot, av, bal) ->
                if (tot > 0) {
                    nightTotal = tot
                    nightAvailed = av
                    nightBalance = bal
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

        // Extract registered mobile from txtContactNo input regardless of attribute ordering
        var registeredMobile = ""
        val contactNoMatcher = Pattern.compile("<input[^>]*ctl00_cphHeading_txtContactNo[^>]*>", Pattern.CASE_INSENSITIVE).matcher(html)
        if (contactNoMatcher.find()) {
            val tag = contactNoMatcher.group(0) ?: ""
            val valMatcher = Pattern.compile("value=[\"'](\\d{10})[\"']").matcher(tag)
            if (valMatcher.find()) {
                registeredMobile = valMatcher.group(1) ?: ""
            }
        }
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
