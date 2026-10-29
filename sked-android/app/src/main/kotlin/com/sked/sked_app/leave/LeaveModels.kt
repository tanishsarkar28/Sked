package com.sked.sked_app.leave

data class LeaveBalance(
    val dayTotal: Int = 0,
    val dayAvailed: Int = 0,
    val dayBalance: Int = 0,
    val nightTotal: Int = 0,
    val nightAvailed: Int = 0,
    val nightBalance: Int = 0
) {
    val isLoaded: Boolean get() = dayTotal > 0 || nightTotal > 0
}

data class LeaveRecord(
    val leaveId: String = "",
    val leaveType: String = "",
    val applyDate: String = "",
    val leaveDate: String = "",
    val returnDate: String = "",
    val status: String = "",
    val checkOut: String = "",
    val checkIn: String = ""
)

data class LeaveProfile(
    val uid: String = "",
    val name: String = "",
    val hostel: String = "",
    val roomNo: String = "",
    val authority: String = "",
    val registeredMobile: String = "",
    val homeAddress: String = ""
)

enum class LeaveTypeOption(
    val code: String,
    val rawValue: String,
    val displayName: String,
    val curfewNotice: String
) {
    DAY_EXTENDED(
        code = "6",
        rawValue = "Day Leave(Extended Leave Timing)",
        displayName = "Day Leave (Extended)",
        curfewNotice = "Return by 10:45 PM today"
    ),
    DAY_STANDARD(
        code = "3",
        rawValue = "Day Leave",
        displayName = "Day Leave (Standard)",
        curfewNotice = "Return by 8:00 PM today"
    ),
    NIGHT_STANDARD(
        code = "1",
        rawValue = "Night Leave",
        displayName = "Night Leave",
        curfewNotice = "Overnight stay • Return by 8:00 PM"
    ),
    NIGHT_EXTENDED(
        code = "7",
        rawValue = "Night Leave(Extended Leave Timing)",
        displayName = "Night Leave (Extended)",
        curfewNotice = "Overnight stay • Return by 10:45 PM"
    ),
    VACATION(
        code = "10",
        rawValue = "Vacation Leave",
        displayName = "Vacation Leave",
        curfewNotice = "Approved university vacation period"
    )
}

enum class VisitPlaceOption(
    val code: String,
    val displayName: String
) {
    OTHER("Other", "Other (Market, Law Gate, Outing)"),
    HOME("Home", "Home (Registered Address)"),
    LOCAL_GUARDIAN("Local Guardian", "Local Guardian")
}
