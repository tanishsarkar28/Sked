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

data class GateActivation(
    val gateName: String = "",
    val status: String = "✔ Activated"
)

data class HostelGatePass(
    val universityTitle: String = "Lovely Professional University",
    val passSubtitle: String = "Hostel Leave Gate Pass",
    val leaveType: String = "Day Leave(Extended Leave Timing)",
    val leaveId: String = "5821114",
    val leaveCode: String = "833168",
    val regNo: String = "12407229",
    val name: String = "Tanish Sarkar",
    val dateOfApply: String = "30 Oct 2026 Time - 5:57PM",
    val dateOfLeaving: String = "30 Oct 2026 Time - 6:00PM",
    val dateOfReturn: String = "30 Oct 2026 Time - 10:45PM",
    val checkOutTime: String = "30 Oct 2026 Time - 6:06PM",
    val checkInTime: String = "",
    val contactNo: String = "8293118470",
    val hostel: String = "Boys Hostel-01",
    val roomNo: String = "A706-Bed A",
    val gates: List<GateActivation> = listOf(
        GateActivation("CheckOut(Exit) Gate-G3", "✔ Activated"),
        GateActivation("CheckOut(Exit) Gate-G4", "✔ Activated"),
        GateActivation("CheckIn(Entry) Gate-G5", "✔ Activated"),
        GateActivation("CheckIn(Entry) Gate-G6", "✔ Activated")
    ),
    val note: String = "Note : Do not look back after checkout; please proceed forward only.",
    val hasActivePass: Boolean = true
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

data class VisitPlaceOption(
    val code: String,
    val displayName: String
) {
    companion object {
        val HOME = VisitPlaceOption("Home", "Home")
        val LOCAL_GUARDIAN = VisitPlaceOption("Local Guardian", "Local Guardian")
        val OTHER = VisitPlaceOption("Other", "Other")

        fun optionsFor(leaveType: LeaveTypeOption): List<VisitPlaceOption> {
            return when (leaveType) {
                LeaveTypeOption.DAY_EXTENDED -> listOf(
                    VisitPlaceOption("Phagwara", "Phagwara"),
                    VisitPlaceOption("Jalandhar", "Jalandhar"),
                    VisitPlaceOption("Ludhiana", "Ludhiana"),
                    VisitPlaceOption("Other", "Other")
                )
                LeaveTypeOption.DAY_STANDARD -> listOf(
                    VisitPlaceOption("Local Visit", "Local Visit"),
                    VisitPlaceOption("Personal grooming", "Personal grooming"),
                    VisitPlaceOption("Medical checkup", "Medical checkup"),
                    VisitPlaceOption("Due to Academic purposes", "Academic"),
                    VisitPlaceOption("Coaching", "Coaching"),
                    VisitPlaceOption("Placement", "Placement"),
                    VisitPlaceOption("Out Station Visit", "Out Station"),
                    VisitPlaceOption("Home", "Home"),
                    VisitPlaceOption("Local Guardian", "Local Guardian"),
                    VisitPlaceOption("Other", "Other")
                )
                LeaveTypeOption.NIGHT_STANDARD,
                LeaveTypeOption.NIGHT_EXTENDED,
                LeaveTypeOption.VACATION -> listOf(
                    VisitPlaceOption("Home", "Home"),
                    VisitPlaceOption("Local Guardian", "Local Guardian"),
                    VisitPlaceOption("Other", "Other")
                )
            }
        }
    }
}
