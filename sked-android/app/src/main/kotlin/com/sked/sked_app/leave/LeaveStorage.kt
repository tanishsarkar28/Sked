package com.sked.sked_app.leave

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object LeaveStorage {
    private const val PREFS_NAME = "sked_hostel_leave_prefs"
    private const val KEY_BALANCE_JSON = "key_leave_balance"
    private const val KEY_RECORDS_JSON = "key_leave_records"
    private const val KEY_PROFILE_JSON = "key_leave_profile"
    private const val KEY_GATE_PASS_JSON = "key_gate_pass_json"
    private const val KEY_LAST_STAY_ADDR = "key_last_stay_addr"
    private const val KEY_LAST_REASON = "key_last_reason"
    private const val KEY_LAST_RELATIVE_MOBILE = "key_last_rel_mobile"

    val DEFAULT_BALANCE = LeaveBalance(
        dayTotal = 75,
        dayAvailed = 12,
        dayBalance = 63,
        nightTotal = 60,
        nightAvailed = 9,
        nightBalance = 51
    )

    val DEFAULT_PROFILE = LeaveProfile(
        uid = "",
        name = "",
        hostel = "",
        roomNo = "",
        authority = "Warden",
        registeredMobile = "",
        homeAddress = ""
    )

    val DEFAULT_GATE_PASS = HostelGatePass()

    val DEFAULT_RECORDS = listOf(
        LeaveRecord("5821114", "Day Leave(Extended Leave Timing)", "10/30/2026 5:57:00 PM", "10/30/2026 6:00:00 PM", "10/30/2026 10:45:00 PM", "Approved", "10/30/2026 6:06:00 PM", "10/30/2026 9:45:00 PM"),
        LeaveRecord("5768261", "Day Leave(Extended Leave Timing)", "10/20/2026 2:46:21 PM", "10/20/2026 3:00:00 PM", "10/20/2026 10:45:00 PM", "Approved", "10/20/2026 2:50:47 PM", "10/20/2026 9:43:22 PM"),
        LeaveRecord("5757328", "Day Leave(Extended Leave Timing)", "10/18/2026 5:25:49 PM", "10/18/2026 5:30:00 PM", "10/18/2026 10:45:00 PM", "Approved", "10/18/2026 5:39:33 PM", "10/18/2026 8:43:58 PM"),
        LeaveRecord("5725392", "Day Leave(Extended Leave Timing)", "10/7/2026 4:56:02 PM", "10/7/2026 5:00:00 PM", "10/7/2026 10:45:00 PM", "Approved", "10/7/2026 5:07:25 PM", "10/7/2026 9:07:07 PM"),
        LeaveRecord("5719382", "Night Leave", "9/30/2026 3:34:44 PM", "9/30/2026 7:45:00 PM", "10/5/2026 12:00:00 PM", "Approved", "9/30/2026 3:59:22 PM", "10/5/2026 6:23:16 PM"),
        LeaveRecord("5690559", "Day Leave(Extended Leave Timing)", "9/26/2026 6:26:32 PM", "9/26/2026 6:45:00 PM", "9/26/2026 10:45:00 PM", "Approved", "9/26/2026 7:14:17 PM", "9/26/2026 8:55:28 PM"),
        LeaveRecord("5626446", "Day Leave(Extended Leave Timing)", "9/12/2026 5:52:26 PM", "9/12/2026 6:00:00 PM", "9/12/2026 10:45:00 PM", "Approved", "9/12/2026 7:23:47 PM", "9/12/2026 8:47:57 PM"),
        LeaveRecord("5600847", "Day Leave(Extended Leave Timing)", "9/6/2026 4:57:50 PM", "9/6/2026 6:00:00 PM", "9/6/2026 10:45:00 PM", "Approved", "9/6/2026 6:50:58 PM", "9/6/2026 8:58:10 PM"),
        LeaveRecord("5553802", "Night Leave", "8/27/2026 5:24:59 PM", "8/27/2026 7:45:00 PM", "8/31/2026 11:00:00 AM", "Approved", "8/27/2026 7:38:27 PM", "8/31/2026 5:49:03 AM"),
        LeaveRecord("5525638", "Day Leave(Extended Leave Timing)", "8/21/2026 3:02:38 PM", "8/21/2026 6:30:00 PM", "8/21/2026 10:45:00 PM", "Approved", "8/21/2026 7:51:37 PM", "8/21/2026 9:31:32 PM"),
        LeaveRecord("5504154", "Day Leave", "8/15/2026 12:01:15 PM", "8/15/2026 12:15:00 PM", "8/15/2026 5:00:00 PM", "Approved", "8/15/2026 1:15:46 PM", "8/15/2026 1:57:59 PM"),
        LeaveRecord("5501602", "Day Leave(Extended Leave Timing)", "8/14/2026 6:39:28 PM", "8/14/2026 7:00:00 PM", "8/14/2026 10:45:00 PM", "Approved", "8/14/2026 7:51:26 PM", "8/14/2026 9:26:01 PM"),
        LeaveRecord("5486983", "Day Leave(Extended Leave Timing)", "8/9/2026 5:07:56 PM", "8/9/2026 5:15:00 PM", "8/9/2026 10:45:00 PM", "Approved", "8/9/2026 5:48:08 PM", "8/9/2026 9:12:44 PM")
    )

    fun saveBalance(context: Context, balance: LeaveBalance) {
        if (balance.dayTotal <= 0 && balance.nightTotal <= 0) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val obj = JSONObject().apply {
            put("dayTotal", balance.dayTotal)
            put("dayAvailed", balance.dayAvailed)
            put("dayBalance", balance.dayBalance)
            put("nightTotal", balance.nightTotal)
            put("nightAvailed", balance.nightAvailed)
            put("nightBalance", balance.nightBalance)
        }
        prefs.edit().putString(KEY_BALANCE_JSON, obj.toString()).apply()
    }

    fun loadBalance(context: Context): LeaveBalance {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_BALANCE_JSON, null)
        if (raw == null) {
            saveBalance(context, DEFAULT_BALANCE)
            return DEFAULT_BALANCE
        }
        return try {
            val obj = JSONObject(raw)
            val bal = LeaveBalance(
                dayTotal = obj.optInt("dayTotal", 0),
                dayAvailed = obj.optInt("dayAvailed", 0),
                dayBalance = obj.optInt("dayBalance", 0),
                nightTotal = obj.optInt("nightTotal", 0),
                nightAvailed = obj.optInt("nightAvailed", 0),
                nightBalance = obj.optInt("nightBalance", 0)
            )
            // If stored balance is the corrupted dummy placeholder (0/75 used & 0/60 used), restore DEFAULT_BALANCE
            if (bal.dayTotal == 75 && bal.dayAvailed == 0 && bal.dayBalance == 75 &&
                bal.nightTotal == 60 && bal.nightAvailed == 0 && bal.nightBalance == 60
            ) {
                return DEFAULT_BALANCE
            }
            // If stored balance was corrupted by DOM bleed (dayTotal was overwritten with nightTotal 60)
            if (bal.dayTotal == 60 && bal.nightTotal == 60 && bal.dayAvailed == bal.nightAvailed) {
                return DEFAULT_BALANCE
            }
            if (bal.dayTotal > 0 || bal.nightTotal > 0) bal else DEFAULT_BALANCE
        } catch (_: Exception) {
            DEFAULT_BALANCE
        }
    }

    fun saveRecords(context: Context, list: List<LeaveRecord>) {
        if (list.isEmpty()) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val arr = JSONArray()
        for (item in list) {
            val obj = JSONObject().apply {
                put("leaveId", item.leaveId)
                put("leaveType", item.leaveType)
                put("applyDate", item.applyDate)
                put("leaveDate", item.leaveDate)
                put("returnDate", item.returnDate)
                put("status", item.status)
                put("checkOut", item.checkOut)
                put("checkIn", item.checkIn)
            }
            arr.put(obj)
        }
        prefs.edit().putString(KEY_RECORDS_JSON, arr.toString()).apply()
    }

    fun loadRecords(context: Context): List<LeaveRecord> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_RECORDS_JSON, null)
        if (raw == null) {
            saveRecords(context, DEFAULT_RECORDS)
            return DEFAULT_RECORDS
        }
        return try {
            val arr = JSONArray(raw)
            if (arr.length() == 0) return DEFAULT_RECORDS
            val result = mutableListOf<LeaveRecord>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                result.add(
                    LeaveRecord(
                        leaveId = o.optString("leaveId", ""),
                        leaveType = o.optString("leaveType", ""),
                        applyDate = o.optString("applyDate", ""),
                        leaveDate = o.optString("leaveDate", ""),
                        returnDate = o.optString("returnDate", ""),
                        status = o.optString("status", ""),
                        checkOut = o.optString("checkOut", ""),
                        checkIn = o.optString("checkIn", "")
                    )
                )
            }
            if (result.isNotEmpty()) result else DEFAULT_RECORDS
        } catch (_: Exception) {
            DEFAULT_RECORDS
        }
    }

    fun saveProfile(context: Context, profile: LeaveProfile) {
        if (profile.uid.isBlank() && profile.name.isBlank()) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val obj = JSONObject().apply {
            put("uid", profile.uid)
            put("name", profile.name)
            put("hostel", profile.hostel)
            put("roomNo", profile.roomNo)
            put("authority", profile.authority)
            put("registeredMobile", profile.registeredMobile)
            put("homeAddress", profile.homeAddress)
        }
        prefs.edit().putString(KEY_PROFILE_JSON, obj.toString()).apply()
    }

    fun loadProfile(context: Context): LeaveProfile {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_PROFILE_JSON, null)
        if (raw == null) {
            saveProfile(context, DEFAULT_PROFILE)
            return DEFAULT_PROFILE
        }
        return try {
            val obj = JSONObject(raw)
            val prof = LeaveProfile(
                uid = obj.optString("uid", ""),
                name = obj.optString("name", ""),
                hostel = obj.optString("hostel", ""),
                roomNo = obj.optString("roomNo", ""),
                authority = obj.optString("authority", ""),
                registeredMobile = obj.optString("registeredMobile", ""),
                homeAddress = obj.optString("homeAddress", "")
            )
            if (prof.uid.isNotBlank() || prof.name.isNotBlank()) {
                prof
            } else {
                DEFAULT_PROFILE
            }
        } catch (_: Exception) {
            DEFAULT_PROFILE
        }
    }

    fun saveFormDefaults(context: Context, stayAddress: String, reason: String, relativeMobile: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_LAST_STAY_ADDR, stayAddress)
            .putString(KEY_LAST_REASON, reason)
            .putString(KEY_LAST_RELATIVE_MOBILE, relativeMobile)
            .apply()
    }

    fun getLastStayAddress(context: Context): String =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_LAST_STAY_ADDR, "Phagwara Market") ?: "Phagwara Market"

    fun getLastReason(context: Context): String =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_LAST_REASON, "Personal work") ?: "Personal work"

    fun getLastRelativeMobile(context: Context): String {
        val m = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_LAST_RELATIVE_MOBILE, "") ?: ""
        return if (m.isNotBlank()) m else DEFAULT_PROFILE.registeredMobile
    }

    fun saveGatePass(context: Context, pass: HostelGatePass) {
        if (!pass.hasActivePass && pass.leaveId.isBlank() && pass.leaveCode.isBlank()) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val obj = JSONObject().apply {
            put("universityTitle", pass.universityTitle)
            put("passSubtitle", pass.passSubtitle)
            put("leaveType", pass.leaveType)
            put("leaveId", pass.leaveId)
            put("leaveCode", pass.leaveCode)
            put("regNo", pass.regNo)
            put("name", pass.name)
            put("dateOfApply", pass.dateOfApply)
            put("dateOfLeaving", pass.dateOfLeaving)
            put("dateOfReturn", pass.dateOfReturn)
            put("checkOutTime", pass.checkOutTime)
            put("checkInTime", pass.checkInTime)
            put("contactNo", pass.contactNo)
            put("hostel", pass.hostel)
            put("roomNo", pass.roomNo)
            put("note", pass.note)
            put("hasActivePass", pass.hasActivePass)
            put("photoUrl", pass.photoUrl)

            val gatesArr = JSONArray()
            for (g in pass.gates) {
                val gObj = JSONObject().apply {
                    put("gateName", g.gateName)
                    put("status", g.status)
                }
                gatesArr.put(gObj)
            }
            put("gates", gatesArr)
        }
        prefs.edit().putString(KEY_GATE_PASS_JSON, obj.toString()).apply()
    }

    fun loadGatePass(context: Context): HostelGatePass {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_GATE_PASS_JSON, null)
        if (raw == null) {
            saveGatePass(context, DEFAULT_GATE_PASS)
            return DEFAULT_GATE_PASS
        }
        return try {
            val obj = JSONObject(raw)
            val gatesList = mutableListOf<GateActivation>()
            val gatesArr = obj.optJSONArray("gates")
            if (gatesArr != null) {
                for (i in 0 until gatesArr.length()) {
                    val gObj = gatesArr.getJSONObject(i)
                    gatesList.add(
                        GateActivation(
                            gateName = gObj.optString("gateName", ""),
                            status = gObj.optString("status", "✔ Activated")
                        )
                    )
                }
            }
            val pass = HostelGatePass(
                universityTitle = obj.optString("universityTitle", DEFAULT_GATE_PASS.universityTitle),
                passSubtitle = obj.optString("passSubtitle", DEFAULT_GATE_PASS.passSubtitle),
                leaveType = obj.optString("leaveType", DEFAULT_GATE_PASS.leaveType),
                leaveId = obj.optString("leaveId", DEFAULT_GATE_PASS.leaveId),
                leaveCode = obj.optString("leaveCode", DEFAULT_GATE_PASS.leaveCode),
                regNo = obj.optString("regNo", DEFAULT_GATE_PASS.regNo),
                name = obj.optString("name", DEFAULT_GATE_PASS.name),
                dateOfApply = obj.optString("dateOfApply", DEFAULT_GATE_PASS.dateOfApply),
                dateOfLeaving = obj.optString("dateOfLeaving", DEFAULT_GATE_PASS.dateOfLeaving),
                dateOfReturn = obj.optString("dateOfReturn", DEFAULT_GATE_PASS.dateOfReturn),
                checkOutTime = obj.optString("checkOutTime", DEFAULT_GATE_PASS.checkOutTime),
                checkInTime = obj.optString("checkInTime", DEFAULT_GATE_PASS.checkInTime),
                contactNo = obj.optString("contactNo", DEFAULT_GATE_PASS.contactNo),
                hostel = obj.optString("hostel", DEFAULT_GATE_PASS.hostel),
                roomNo = obj.optString("roomNo", DEFAULT_GATE_PASS.roomNo),
                gates = if (gatesList.isNotEmpty()) gatesList else emptyList(),
                note = obj.optString("note", "Note : Do not look back after checkout; please proceed forward only."),
                hasActivePass = obj.optBoolean("hasActivePass", false),
                photoUrl = obj.optString("photoUrl", "")
            )
            if (pass.hasActivePass || pass.leaveId.isNotBlank() || pass.name.isNotBlank()) {
                pass
            } else {
                DEFAULT_GATE_PASS
            }
        } catch (_: Exception) {
            DEFAULT_GATE_PASS
        }
    }
}
