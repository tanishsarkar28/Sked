package com.sked.sked_app.leave

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object LeaveStorage {
    private const val PREFS_NAME = "sked_hostel_leave_prefs"
    private const val KEY_BALANCE_JSON = "key_leave_balance"
    private const val KEY_RECORDS_JSON = "key_leave_records"
    private const val KEY_PROFILE_JSON = "key_leave_profile"
    private const val KEY_LAST_STAY_ADDR = "key_last_stay_addr"
    private const val KEY_LAST_REASON = "key_last_reason"
    private const val KEY_LAST_RELATIVE_MOBILE = "key_last_rel_mobile"

    fun saveBalance(context: Context, balance: LeaveBalance) {
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
        val raw = prefs.getString(KEY_BALANCE_JSON, null) ?: return LeaveBalance()
        return try {
            val obj = JSONObject(raw)
            LeaveBalance(
                dayTotal = obj.optInt("dayTotal", 75),
                dayAvailed = obj.optInt("dayAvailed", 0),
                dayBalance = obj.optInt("dayBalance", 75),
                nightTotal = obj.optInt("nightTotal", 60),
                nightAvailed = obj.optInt("nightAvailed", 0),
                nightBalance = obj.optInt("nightBalance", 60)
            )
        } catch (_: Exception) {
            LeaveBalance()
        }
    }

    fun saveRecords(context: Context, list: List<LeaveRecord>) {
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
        val raw = prefs.getString(KEY_RECORDS_JSON, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
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
            result
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveProfile(context: Context, profile: LeaveProfile) {
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
        val raw = prefs.getString(KEY_PROFILE_JSON, null) ?: return LeaveProfile()
        return try {
            val obj = JSONObject(raw)
            LeaveProfile(
                uid = obj.optString("uid", ""),
                name = obj.optString("name", ""),
                hostel = obj.optString("hostel", ""),
                roomNo = obj.optString("roomNo", ""),
                authority = obj.optString("authority", ""),
                registeredMobile = obj.optString("registeredMobile", ""),
                homeAddress = obj.optString("homeAddress", "")
            )
        } catch (_: Exception) {
            LeaveProfile()
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

    fun getLastRelativeMobile(context: Context): String =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_LAST_RELATIVE_MOBILE, "") ?: ""
}
