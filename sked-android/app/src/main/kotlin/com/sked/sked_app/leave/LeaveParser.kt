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

    fun parseGatePass(html: String): HostelGatePass? {
        if (html.isBlank()) return null

        fun extractSpan(id: String): String {
            val p = Pattern.compile("id=[\"']$id[\"'][^>]*>(.*?)</span>", Pattern.DOTALL or Pattern.CASE_INSENSITIVE)
            val m = p.matcher(html)
            return if (m.find()) {
                m.group(1)?.replace(Regex("<[^>]+>"), "")?.replace("&nbsp;", " ")?.trim() ?: ""
            } else ""
        }

        fun extractHiddenField(name: String): String {
            val p = Pattern.compile("name=[\"']$name[\"'][^>]*value=[\"'](.*?)[\"']", Pattern.CASE_INSENSITIVE)
            val m = p.matcher(html)
            return if (m.find()) m.group(1)?.trim() ?: "" else ""
        }

        val hasTable = html.contains("ctl00_cphHeading_tblSlipData")
        val leaveId = extractSpan("ctl00_cphHeading_lblLid")
        var leaveCode = extractHiddenField("ctl00\$cphHeading\$hfLeaveCode")
        if (leaveCode.isBlank()) {
            leaveCode = extractSpan("ctl00_cphHeading_lblLCode")
        }

        val hasActivePass = hasTable && leaveCode.isNotBlank() && leaveCode != "6000"

        val leaveType = extractSpan("ctl00_cphHeading_lblLName").ifBlank { "Day Leave(Extended Leave Timing)" }
        val regNo = extractSpan("ctl00_cphHeading_lblRegNo")
        val name = extractSpan("ctl00_cphHeading_lblName")
        val dateOfApply = extractSpan("ctl00_cphHeading_lblDateofapply")
        val dateOfLeaving = extractSpan("ctl00_cphHeading_lblDateofleaveing")
        val dateOfReturn = extractSpan("ctl00_cphHeading_lblDateofreturn")
        val checkOutTime = extractSpan("ctl00_cphHeading_lblcheckouttime")
        val checkInTime = extractSpan("ctl00_cphHeading_lblcheckintime")
        val contactNo = extractSpan("ctl00_cphHeading_lblmobile")
        val hostel = extractSpan("ctl00_cphHeading_lblhostel")
        val roomNo = extractSpan("ctl00_cphHeading_lblroomno")

        // Parse gates
        val gates = mutableListOf<GateActivation>()
        try {
            val gridP = Pattern.compile("id=[\"']ctl00_cphHeading_grdGateStatus[\"'].*?</table>", Pattern.DOTALL or Pattern.CASE_INSENSITIVE)
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

        // Session expired / login page detection
        if (html.contains("LoginNew.aspx", ignoreCase = true) ||
            html.contains("Login.aspx", ignoreCase = true) ||
            html.contains("lockerror", ignoreCase = true)
        ) {
            return null
        }

        val isLeaveSlipPage = html.contains("frmHostelLeaveSlipTest", ignoreCase = true) ||
                html.contains("ctl00_cphHeading", ignoreCase = true) ||
                html.contains("tblSlipData", ignoreCase = true) ||
                html.contains("Hostel Leave", ignoreCase = true)

        if (!hasActivePass && (!hasTable || leaveCode == "6000" || leaveId.isBlank())) {
            if (isLeaveSlipPage) {
                return HostelGatePass(
                    universityTitle = "Lovely Professional University",
                    passSubtitle = "Hostel Leave Gate Pass",
                    leaveType = leaveType,
                    leaveId = leaveId,
                    leaveCode = leaveCode,
                    regNo = regNo.ifBlank { "12407229" },
                    name = name.ifBlank { "Tanish Sarkar" },
                    dateOfApply = dateOfApply,
                    dateOfLeaving = dateOfLeaving,
                    dateOfReturn = dateOfReturn,
                    checkOutTime = checkOutTime,
                    checkInTime = checkInTime,
                    contactNo = contactNo.ifBlank { "8293118470" },
                    hostel = hostel.ifBlank { "Boys Hostel-01" },
                    roomNo = roomNo.ifBlank { "A706-Bed A" },
                    gates = emptyList(),
                    note = "Note : Do not look back after checkout; please proceed forward only.",
                    hasActivePass = false
                )
            } else {
                return null
            }
        }

        return HostelGatePass(
            universityTitle = "Lovely Professional University",
            passSubtitle = "Hostel Leave Gate Pass",
            leaveType = leaveType,
            leaveId = leaveId.ifBlank { "5821114" },
            leaveCode = leaveCode.ifBlank { "833168" },
            regNo = regNo.ifBlank { "12407229" },
            name = name.ifBlank { "Tanish Sarkar" },
            dateOfApply = dateOfApply,
            dateOfLeaving = dateOfLeaving,
            dateOfReturn = dateOfReturn,
            checkOutTime = checkOutTime,
            checkInTime = checkInTime,
            contactNo = contactNo.ifBlank { "8293118470" },
            hostel = hostel.ifBlank { "Boys Hostel-01" },
            roomNo = roomNo.ifBlank { "A706-Bed A" },
            gates = if (gates.isNotEmpty()) gates else LeaveStorage.DEFAULT_GATE_PASS.gates,
            note = "Note : Do not look back after checkout; please proceed forward only.",
            hasActivePass = hasActivePass
        )
    }
}
