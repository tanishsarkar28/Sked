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
        var dayTotal = 0
        var dayAvailed = 0
        var dayBalance = 0

        var nightTotal = 0
        var nightAvailed = 0
        var nightBalance = 0

        fun parseGrid(gridId: String): Triple<Int, Int, Int>? {
            val idx = html.indexOf(gridId)
            val searchArea = if (idx != -1) {
                html.substring(idx, (idx + 6000).coerceAtMost(html.length))
            } else {
                val gridPattern = Pattern.compile("$gridId.*?<tbody>(.*?)</tbody>", Pattern.DOTALL)
                val m = gridPattern.matcher(html)
                if (m.find()) m.group(1) ?: return null else return null
            }

            var tot = -1
            var av = -1
            var bal = -1

            // 1. Match ASP.NET RadGrid TypeLabel and CountLabel spans
            val rowPattern = Pattern.compile("TypeLabel[^>]*>\\s*([^<]+)\\s*</span>.*?CountLabel[^>]*>\\s*(\\d+)\\s*</span>", Pattern.DOTALL)
            val rm = rowPattern.matcher(searchArea)
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

            // 2. Match table cell pairs (e.g. <td>Total</td><td>75</td>)
            if (!foundAny || tot < 0) {
                val tdPattern = Pattern.compile("<td[^>]*>\\s*(Total|Availed|Balance)\\s*</td>\\s*<td[^>]*>\\s*(\\d+)\\s*</td>", Pattern.CASE_INSENSITIVE or Pattern.DOTALL)
                val tm = tdPattern.matcher(searchArea)
                while (tm.find()) {
                    foundAny = true
                    val type = tm.group(1)?.trim()?.lowercase() ?: ""
                    val count = tm.group(2)?.toIntOrNull() ?: 0
                    when (type) {
                        "total" -> tot = count
                        "availed" -> av = count
                        "balance" -> bal = count
                    }
                }
            }

            // 3. Fallback pattern if span or td structure differs
            if (!foundAny || tot < 0) {
                val fallback = Pattern.compile("(Total|Availed|Balance)[^\\d<]{1,150}(\\d+)", Pattern.DOTALL or Pattern.CASE_INSENSITIVE)
                val fm = fallback.matcher(searchArea)
                while (fm.find()) {
                    foundAny = true
                    val type = fm.group(1)?.trim()?.lowercase() ?: ""
                    val count = fm.group(2)?.toIntOrNull() ?: 0
                    when (type) {
                        "total" -> tot = count
                        "availed" -> av = count
                        "balance" -> bal = count
                    }
                }
            }

            if (tot >= 0 || av >= 0 || bal >= 0) {
                val safeTot = if (tot >= 0) tot else 0
                val safeAv = if (av >= 0) av else 0
                val safeBal = if (bal >= 0) bal else (safeTot - safeAv).coerceAtLeast(0)
                return Triple(safeTot, safeAv, safeBal)
            }
            return null
        }

        try {
            parseGrid("ctl00_cphHeading_RadGrid1")?.let { (tot, av, bal) ->
                dayTotal = tot
                dayAvailed = av
                dayBalance = bal
            }
            parseGrid("ctl00_cphHeading_RadGrid2")?.let { (tot, av, bal) ->
                nightTotal = tot
                nightAvailed = av
                nightBalance = bal
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
            val gridPattern = Pattern.compile("(?:id\\s*=\\s*[\"'][^\"']*grdLeaveDetail[^\"']*[\"']|grdLeaveDetail).*?>(.*?)</table>", Pattern.DOTALL or Pattern.CASE_INSENSITIVE)
            val m = gridPattern.matcher(html)
            if (!m.find()) return emptyList()

            val tableHtml = m.group(1) ?: return emptyList()
            val trPattern = Pattern.compile("<tr[^>]*>(.*?)</tr>", Pattern.DOTALL or Pattern.CASE_INSENSITIVE)
            val trMatcher = trPattern.matcher(tableHtml)

            var isHeader = true
            while (trMatcher.find()) {
                val rowHtml = trMatcher.group(1) ?: continue
                if (isHeader) {
                    isHeader = false
                    continue
                }

                val tdPattern = Pattern.compile("<td[^>]*>(.*?)</td>", Pattern.DOTALL or Pattern.CASE_INSENSITIVE)
                val tdMatcher = tdPattern.matcher(rowHtml)
                val cells = mutableListOf<String>()
                while (tdMatcher.find()) {
                    val rawText = tdMatcher.group(1) ?: ""
                    val clean = rawText
                        .replace(Regex("<[^>]+>"), "")
                        .replace("&nbsp;", " ")
                        .trim()
                    cells.add(clean)
                }

                if (cells.size >= 6 && cells[0].isNotBlank() && cells[0].all { it.isDigit() }) {
                    val statusIdx = cells.indexOfFirst { c ->
                        c.equals("Approved", ignoreCase = true) ||
                        c.equals("Pending", ignoreCase = true) ||
                        c.equals("Rejected", ignoreCase = true) ||
                        c.equals("Cancelled", ignoreCase = true)
                    }.takeIf { it >= 0 } ?: 6

                    list.add(
                        LeaveRecord(
                            leaveId = cells[0],
                            leaveType = cells.getOrElse(1) { "" },
                            applyDate = cells.getOrElse(2) { "" },
                            leaveDate = cells.getOrElse(3) { "" },
                            returnDate = cells.getOrElse(4) { "" },
                            status = cells.getOrElse(statusIdx) { "Approved" },
                            checkOut = cells.getOrElse(statusIdx + 1) { cells.getOrElse(7) { "" } },
                            checkIn = cells.getOrElse(statusIdx + 2) { cells.getOrElse(8) { "" } }
                        )
                    )
                }
            }
        } catch (_: Exception) {}

        return list
    }

    fun parseGatePass(html: String): HostelGatePass? {
        if (html.isBlank()) return null

        // Session expired / login page detection
        if (html.contains("LoginNew.aspx", ignoreCase = true) ||
            html.contains("Login.aspx", ignoreCase = true) ||
            html.contains("lockerror", ignoreCase = true)
        ) {
            return null
        }

        // Universal flexible text/value extractor across any HTML tag & attribute ordering
        fun extractText(idSubstr: String): String {
            // 1. Matches <tagName ... id="...idSubstr...">content</tagName>
            val p1 = Pattern.compile("<[^>]+id\\s*=\\s*[\"'][^\"']*$idSubstr[^\"']*[\"'][^>]*>(.*?)</", Pattern.DOTALL or Pattern.CASE_INSENSITIVE)
            val m1 = p1.matcher(html)
            if (m1.find()) {
                val t = m1.group(1)?.replace(Regex("<[^>]+>"), "")?.replace("&nbsp;", " ")?.trim() ?: ""
                if (t.isNotBlank()) return t
            }
            // 2. Matches <input ... value="..." ... id="...idSubstr..."> or <input ... id="...idSubstr..." ... value="...">
            val p2 = Pattern.compile("<input[^>]+>", Pattern.CASE_INSENSITIVE)
            val m2 = p2.matcher(html)
            while (m2.find()) {
                val tag = m2.group(0) ?: ""
                if (tag.contains(idSubstr, ignoreCase = true)) {
                    val vp = Pattern.compile("value\\s*=\\s*[\"']([^\"']*)[\"']", Pattern.CASE_INSENSITIVE).matcher(tag)
                    if (vp.find()) {
                        val v = vp.group(1)?.trim() ?: ""
                        if (v.isNotBlank()) return v
                    }
                }
            }
            // 3. Fallback: match by name="..."
            val p3 = Pattern.compile("<[^>]+name\\s*=\\s*[\"'][^\"']*$idSubstr[^\"']*[\"'][^>]*>(.*?)</", Pattern.DOTALL or Pattern.CASE_INSENSITIVE)
            val m3 = p3.matcher(html)
            if (m3.find()) {
                val t = m3.group(1)?.replace(Regex("<[^>]+>"), "")?.replace("&nbsp;", " ")?.trim() ?: ""
                if (t.isNotBlank()) return t
            }
            return ""
        }

        val hasTable = html.contains("tblSlipData", ignoreCase = true) ||
                html.contains("grdGateStatus", ignoreCase = true) ||
                html.contains("lblLid", ignoreCase = true) ||
                html.contains("lblLCode", ignoreCase = true) ||
                html.contains("hfLeaveCode", ignoreCase = true) ||
                html.contains("Hostel Leave", ignoreCase = true) ||
                html.contains("CheckOut", ignoreCase = true)

        var leaveId = extractText("lblLid")
        if (leaveId.isBlank()) {
            val m = Pattern.compile("\\b(5\\d{6})\\b").matcher(html)
            if (m.find()) leaveId = m.group(1) ?: ""
        }

        // In UMS, the real leave code is in lblLCode (hfLeaveCode is usually blank)
        var leaveCode = extractText("lblLCode")
        if (leaveCode.isBlank()) {
            leaveCode = extractText("hfLeaveCode")
        }
        if (leaveCode.isBlank() && leaveId.isNotBlank()) {
            leaveCode = leaveId
        }

        val leaveType = extractText("lblLName").ifBlank { "Day Leave(Extended Leave Timing)" }
        val regNo = extractText("lblRegNo")
        val name = extractText("lblName")
        val dateOfApply = extractText("lblDateofapply")
        val dateOfLeaving = extractText("lblDateofleave").ifBlank { extractText("lblLeaveDate") }
        val dateOfReturn = extractText("lblDateofreturn").ifBlank { extractText("lblReturnDate") }
        val checkOutTime = extractText("lblcheckout").ifBlank { extractText("lblCheckOut") }
        val checkInTime = extractText("lblcheckin").ifBlank { extractText("lblCheckIn") }
        val contactNo = extractText("lblmobile")
        val hostel = extractText("lblhostel")
        val roomNo = extractText("lblroomno")

        // Parse student photo URL
        var photoUrl = ""
        val pm1 = Pattern.compile("id=[\"'][^\"']*Image1[^\"']*[\"'][^>]*src=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE).matcher(html)
        if (pm1.find()) {
            photoUrl = pm1.group(1) ?: ""
        } else {
            val pm2 = Pattern.compile("src=[\"']([^\"']*DisplayImageForPowerBi\\.aspx[^\"']*)[\"']", Pattern.CASE_INSENSITIVE).matcher(html)
            if (pm2.find()) {
                photoUrl = pm2.group(1) ?: ""
            }
        }
        if (photoUrl.startsWith("/")) {
            photoUrl = "https://ums.lpu.in$photoUrl"
        }

        // Parse gates
        val gates = mutableListOf<GateActivation>()
        try {
            val gridP = Pattern.compile("(?:id=[\"'][^\"']*grdGateStatus[^\"']*[\"'][^>]*>|grdGateStatus).*?</table>", Pattern.DOTALL or Pattern.CASE_INSENSITIVE)
            val gm = gridP.matcher(html)
            if (gm.find()) {
                val tableContent = gm.group(0) ?: ""
                val trP = Pattern.compile("<tr[^>]*>(.*?)</tr>", Pattern.DOTALL or Pattern.CASE_INSENSITIVE)
                val trM = trP.matcher(tableContent)
                while (trM.find()) {
                    val rowHtml = trM.group(1) ?: continue
                    val tdP = Pattern.compile("<td[^>]*>(.*?)</td>", Pattern.DOTALL or Pattern.CASE_INSENSITIVE)
                    val tdM = tdP.matcher(rowHtml)
                    val cells = mutableListOf<String>()
                    while (tdM.find()) {
                        val cText = tdM.group(1)?.replace(Regex("<[^>]+>"), "")?.replace("&nbsp;", " ")?.trim() ?: ""
                        cells.add(cText)
                    }
                    if (cells.size >= 2 && cells[0].isNotBlank()) {
                        gates.add(GateActivation(gateName = cells[0], status = cells[1].ifBlank { "✔ Activated" }))
                    }
                }
            }
        } catch (_: Exception) {}

        // Active pass is present if we have valid leave ID or leave code or leaving date
        val hasActivePass = (hasTable || html.contains("Hostel", ignoreCase = true)) &&
                ((leaveId.isNotBlank() && leaveId != "0" && leaveId != "6000") ||
                 (leaveCode.isNotBlank() && leaveCode != "6000" && leaveCode != "0") ||
                 dateOfLeaving.isNotBlank())

        val isLeaveSlipPage = html.contains("frmHostelLeaveSlipTest", ignoreCase = true) ||
                html.contains("ctl00_cphHeading", ignoreCase = true) ||
                html.contains("tblSlipData", ignoreCase = true) ||
                html.contains("Hostel Leave", ignoreCase = true)

        if (!hasActivePass) {
            if (isLeaveSlipPage) {
                return HostelGatePass(
                    universityTitle = "Lovely Professional University",
                    passSubtitle = "Hostel Leave Gate Pass",
                    leaveType = leaveType,
                    leaveId = leaveId,
                    leaveCode = leaveCode,
                    regNo = regNo,
                    name = name,
                    dateOfApply = dateOfApply,
                    dateOfLeaving = dateOfLeaving,
                    dateOfReturn = dateOfReturn,
                    checkOutTime = checkOutTime,
                    checkInTime = checkInTime,
                    contactNo = contactNo,
                    hostel = hostel,
                    roomNo = roomNo,
                    gates = emptyList(),
                    note = "Note : Do not look back after checkout; please proceed forward only.",
                    hasActivePass = false,
                    photoUrl = photoUrl
                )
            } else {
                return null
            }
        }

        val effectiveCode = if (leaveCode.isNotBlank() && leaveCode != "6000") leaveCode else leaveId

        return HostelGatePass(
            universityTitle = "Lovely Professional University",
            passSubtitle = "Hostel Leave Gate Pass",
            leaveType = leaveType,
            leaveId = leaveId,
            leaveCode = effectiveCode,
            regNo = regNo,
            name = name,
            dateOfApply = dateOfApply,
            dateOfLeaving = dateOfLeaving,
            dateOfReturn = dateOfReturn,
            checkOutTime = checkOutTime,
            checkInTime = checkInTime,
            contactNo = contactNo,
            hostel = hostel,
            roomNo = roomNo,
            gates = gates,
            note = "Note : Do not look back after checkout; please proceed forward only.",
            hasActivePass = true,
            photoUrl = photoUrl
        )
    }
}
