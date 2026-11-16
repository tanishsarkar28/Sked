import 'dart:core';

class LeaveBalance {
  final int dayTotal;
  final int dayAvailed;
  final int dayBalance;
  final int nightTotal;
  final int nightAvailed;
  final int nightBalance;

  const LeaveBalance({
    this.dayTotal = 0,
    this.dayAvailed = 0,
    this.dayBalance = 0,
    this.nightTotal = 0,
    this.nightAvailed = 0,
    this.nightBalance = 0,
  });

  bool get isLoaded => dayTotal > 0 || nightTotal > 0;

  Map<String, dynamic> toJson() => {
    'dayTotal': dayTotal,
    'dayAvailed': dayAvailed,
    'dayBalance': dayBalance,
    'nightTotal': nightTotal,
    'nightAvailed': nightAvailed,
    'nightBalance': nightBalance,
  };

  factory LeaveBalance.fromJson(Map<String, dynamic> json) => LeaveBalance(
    dayTotal: json['dayTotal'] as int? ?? 0,
    dayAvailed: json['dayAvailed'] as int? ?? 0,
    dayBalance: json['dayBalance'] as int? ?? 0,
    nightTotal: json['nightTotal'] as int? ?? 0,
    nightAvailed: json['nightAvailed'] as int? ?? 0,
    nightBalance: json['nightBalance'] as int? ?? 0,
  );
}

class LeaveRecord {
  final String leaveId;
  final String leaveType;
  final String applyDate;
  final String leaveDate;
  final String returnDate;
  final String status;
  final String checkOut;
  final String checkIn;

  const LeaveRecord({
    this.leaveId = '',
    this.leaveType = '',
    this.applyDate = '',
    this.leaveDate = '',
    this.returnDate = '',
    this.status = '',
    this.checkOut = '',
    this.checkIn = '',
  });

  Map<String, dynamic> toJson() => {
    'leaveId': leaveId,
    'leaveType': leaveType,
    'applyDate': applyDate,
    'leaveDate': leaveDate,
    'returnDate': returnDate,
    'status': status,
    'checkOut': checkOut,
    'checkIn': checkIn,
  };

  factory LeaveRecord.fromJson(Map<String, dynamic> json) => LeaveRecord(
    leaveId: json['leaveId'] as String? ?? '',
    leaveType: json['leaveType'] as String? ?? '',
    applyDate: json['applyDate'] as String? ?? '',
    leaveDate: json['leaveDate'] as String? ?? '',
    returnDate: json['returnDate'] as String? ?? '',
    status: json['status'] as String? ?? '',
    checkOut: json['checkOut'] as String? ?? '',
    checkIn: json['checkIn'] as String? ?? '',
  );
}

class LeaveProfile {
  final String uid;
  final String name;
  final String hostel;
  final String roomNo;
  final String authority;
  final String registeredMobile;
  final String homeAddress;

  const LeaveProfile({
    this.uid = '',
    this.name = '',
    this.hostel = '',
    this.roomNo = '',
    this.authority = 'Warden',
    this.registeredMobile = '',
    this.homeAddress = '',
  });

  Map<String, dynamic> toJson() => {
    'uid': uid,
    'name': name,
    'hostel': hostel,
    'roomNo': roomNo,
    'authority': authority,
    'registeredMobile': registeredMobile,
    'homeAddress': homeAddress,
  };

  factory LeaveProfile.fromJson(Map<String, dynamic> json) => LeaveProfile(
    uid: json['uid'] as String? ?? '',
    name: json['name'] as String? ?? '',
    hostel: json['hostel'] as String? ?? '',
    roomNo: json['roomNo'] as String? ?? '',
    authority: json['authority'] as String? ?? 'Warden',
    registeredMobile: json['registeredMobile'] as String? ?? '',
    homeAddress: json['homeAddress'] as String? ?? '',
  );
}

class GateActivation {
  final String gateName;
  final String status;

  const GateActivation({
    this.gateName = '',
    this.status = '✔ Activated',
  });

  Map<String, dynamic> toJson() => {
    'gateName': gateName,
    'status': status,
  };

  factory GateActivation.fromJson(Map<String, dynamic> json) => GateActivation(
    gateName: json['gateName'] as String? ?? '',
    status: json['status'] as String? ?? '✔ Activated',
  );
}

class HostelGatePass {
  final String universityTitle;
  final String passSubtitle;
  final String leaveType;
  final String leaveId;
  final String leaveCode;
  final String regNo;
  final String name;
  final String dateOfApply;
  final String dateOfLeaving;
  final String dateOfReturn;
  final String checkOutTime;
  final String checkInTime;
  final String contactNo;
  final String hostel;
  final String roomNo;
  final List<GateActivation> gates;
  final String note;
  final bool hasActivePass;
  final String photoUrl;

  const HostelGatePass({
    this.universityTitle = 'Lovely Professional University',
    this.passSubtitle = 'Hostel Leave Gate Pass',
    this.leaveType = '',
    this.leaveId = '',
    this.leaveCode = '',
    this.regNo = '',
    this.name = '',
    this.dateOfApply = '',
    this.dateOfLeaving = '',
    this.dateOfReturn = '',
    this.checkOutTime = '',
    this.checkInTime = '',
    this.contactNo = '',
    this.hostel = '',
    this.roomNo = '',
    this.gates = const [],
    this.note = 'Note : Do not look back after checkout; please proceed forward only.',
    this.hasActivePass = false,
    this.photoUrl = '',
  });

  Map<String, dynamic> toJson() => {
    'universityTitle': universityTitle,
    'passSubtitle': passSubtitle,
    'leaveType': leaveType,
    'leaveId': leaveId,
    'leaveCode': leaveCode,
    'regNo': regNo,
    'name': name,
    'dateOfApply': dateOfApply,
    'dateOfLeaving': dateOfLeaving,
    'dateOfReturn': dateOfReturn,
    'checkOutTime': checkOutTime,
    'checkInTime': checkInTime,
    'contactNo': contactNo,
    'hostel': hostel,
    'roomNo': roomNo,
    'gates': gates.map((g) => g.toJson()).toList(),
    'note': note,
    'hasActivePass': hasActivePass,
    'photoUrl': photoUrl,
  };

  factory HostelGatePass.fromJson(Map<String, dynamic> json) {
    final rawGates = json['gates'] as List<dynamic>? ?? [];
    return HostelGatePass(
      universityTitle: json['universityTitle'] as String? ?? 'Lovely Professional University',
      passSubtitle: json['passSubtitle'] as String? ?? 'Hostel Leave Gate Pass',
      leaveType: json['leaveType'] as String? ?? '',
      leaveId: json['leaveId'] as String? ?? '',
      leaveCode: json['leaveCode'] as String? ?? '',
      regNo: json['regNo'] as String? ?? '',
      name: json['name'] as String? ?? '',
      dateOfApply: json['dateOfApply'] as String? ?? '',
      dateOfLeaving: json['dateOfLeaving'] as String? ?? '',
      dateOfReturn: json['dateOfReturn'] as String? ?? '',
      checkOutTime: json['checkOutTime'] as String? ?? '',
      checkInTime: json['checkInTime'] as String? ?? '',
      contactNo: json['contactNo'] as String? ?? '',
      hostel: json['hostel'] as String? ?? '',
      roomNo: json['roomNo'] as String? ?? '',
      gates: rawGates.map((e) => GateActivation.fromJson(e as Map<String, dynamic>)).toList(),
      note: json['note'] as String? ?? 'Note : Do not look back after checkout; please proceed forward only.',
      hasActivePass: json['hasActivePass'] as bool? ?? false,
      photoUrl: json['photoUrl'] as String? ?? '',
    );
  }
}

bool isWinterCurfew([DateTime? dt]) {
  final d = dt ?? DateTime.now();
  return d.month == DateTime.november ||
      d.month == DateTime.december ||
      d.month == DateTime.january ||
      d.month == DateTime.february;
}

enum LeaveTypeOption {
  dayExtended('6', 'Day Leave(Extended Leave Timing)', 'Day Leave (Extended)', 'Return by 10:45 PM today'),
  dayStandard('3', 'Day Leave', 'Day Leave (Standard)', 'Return by 8:00 PM today'),
  nightStandard('1', 'Night Leave', 'Night Leave', 'Overnight stay • Return by 8:00 PM'),
  nightExtended('7', 'Night Leave(Extended Leave Timing)', 'Night Leave (Extended)', 'Overnight stay • Return by 10:45 PM'),
  vacation('10', 'Vacation Leave', 'Vacation Leave', 'Approved university vacation period');

  final String code;
  final String rawValue;
  final String displayName;
  final String defaultCurfewNotice;

  const LeaveTypeOption(this.code, this.rawValue, this.displayName, this.defaultCurfewNotice);

  String get curfewNotice {
    if (this == LeaveTypeOption.dayStandard || this == LeaveTypeOption.nightStandard) {
      return isWinterCurfew() ? 'Winter Curfew: Return by 7:30 PM' : defaultCurfewNotice;
    }
    return defaultCurfewNotice;
  }
}

enum VisitPlaceOption {
  home('1', 'Home'),
  city('2', 'City / Local Market'),
  outing('3', 'Outing / Mall'),
  other('4', 'Other');

  final String code;
  final String displayName;

  const VisitPlaceOption(this.code, this.displayName);

  static List<VisitPlaceOption> optionsFor(LeaveTypeOption type) {
    if (type == LeaveTypeOption.dayExtended || type == LeaveTypeOption.dayStandard) {
      return [VisitPlaceOption.city, VisitPlaceOption.outing, VisitPlaceOption.other];
    } else {
      return [VisitPlaceOption.home, VisitPlaceOption.city, VisitPlaceOption.other];
    }
  }
}

DateTime? parseUmsReturnDate(String returnDateStr) {
  if (returnDateStr.trim().isEmpty) return null;
  final clean = returnDateStr
      .replaceAll(RegExp(r'Time\s*-', caseSensitive: false), ' ')
      .replaceAll(RegExp(r'\s+'), ' ')
      .trim();

  // Try standard ISO parsing
  final iso = DateTime.tryParse(clean);
  if (iso != null) return iso;

  // Pattern 1: e.g. "30 Oct 2026 10:45PM" or "30 Oct 2026 10:45 PM" or "30-Oct-2026 10:45 PM"
  final alphaMonthMatch = RegExp(
    r'^(\d{1,2})[\s\-]+([A-Za-z]{3})[\s\-]+(\d{4})[\s,]+(\d{1,2}):(\d{2})(?::(\d{2}))?\s*(AM|PM)?$',
    caseSensitive: false,
  ).firstMatch(clean);

  if (alphaMonthMatch != null) {
    final day = int.tryParse(alphaMonthMatch.group(1) ?? '') ?? 1;
    final monthStr = (alphaMonthMatch.group(2) ?? '').toLowerCase();
    final year = int.tryParse(alphaMonthMatch.group(3) ?? '') ?? 2026;
    var hour = int.tryParse(alphaMonthMatch.group(4) ?? '') ?? 0;
    final minute = int.tryParse(alphaMonthMatch.group(5) ?? '') ?? 0;
    final second = int.tryParse(alphaMonthMatch.group(6) ?? '') ?? 0;
    final amPm = alphaMonthMatch.group(7)?.toUpperCase();

    const monthMap = {
      'jan': 1, 'feb': 2, 'mar': 3, 'apr': 4, 'may': 5, 'jun': 6,
      'jul': 7, 'aug': 8, 'sep': 9, 'oct': 10, 'nov': 11, 'dec': 12
    };
    final month = monthMap[monthStr] ?? 1;

    if (amPm == 'PM' && hour < 12) hour += 12;
    if (amPm == 'AM' && hour == 12) hour = 0;

    return DateTime(year, month, day, hour, minute, second);
  }

  // Pattern 2: e.g. "10/30/2026 10:45:00 PM" or "9/30/2026 3:34 PM" (Month/Day/Year)
  final numMatch = RegExp(
    r'^(\d{1,2})/(\d{1,2})/(\d{4})[\s,]+(\d{1,2}):(\d{2})(?::(\d{2}))?\s*(AM|PM)?$',
    caseSensitive: false,
  ).firstMatch(clean);

  if (numMatch != null) {
    final mOrD1 = int.tryParse(numMatch.group(1) ?? '') ?? 1;
    final mOrD2 = int.tryParse(numMatch.group(2) ?? '') ?? 1;
    final year = int.tryParse(numMatch.group(3) ?? '') ?? 2026;
    var hour = int.tryParse(numMatch.group(4) ?? '') ?? 0;
    final minute = int.tryParse(numMatch.group(5) ?? '') ?? 0;
    final second = int.tryParse(numMatch.group(6) ?? '') ?? 0;
    final amPm = numMatch.group(7)?.toUpperCase();

    // Standard ASP.NET is Month/Day/Year
    int month = mOrD1;
    int day = mOrD2;
    if (month > 12 && day <= 12) {
      // Swapped format Day/Month/Year
      final tmp = month;
      month = day;
      day = tmp;
    }

    if (amPm == 'PM' && hour < 12) hour += 12;
    if (amPm == 'AM' && hour == 12) hour = 0;

    return DateTime(year, month, day, hour, minute, second);
  }

  return null;
}

bool isLeaveExpired(String returnDateStr, [int bufferMinutes = 0]) {
  final dt = parseUmsReturnDate(returnDateStr);
  if (dt == null) return false;
  final expiryMillis = dt.millisecondsSinceEpoch + (bufferMinutes * 60 * 1000);
  return DateTime.now().millisecondsSinceEpoch > expiryMillis;
}
