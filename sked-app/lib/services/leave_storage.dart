import 'dart:convert';
import 'package:shared_preferences/shared_preferences.dart';
import '../models/leave_models.dart';

class LeaveStorage {
  LeaveStorage._();

  static const String keyBalanceJson = 'key_leave_balance';
  static const String keyRecordsJson = 'key_leave_records';
  static const String keyProfileJson = 'key_leave_profile';
  static const String keyGatePassJson = 'key_gate_pass_json';
  static const String keyLastStayAddr = 'key_last_stay_addr';
  static const String keyLastReason = 'key_last_reason';
  static const String keyLastRelativeMobile = 'key_last_rel_mobile';
  static const String keyIsOtpRequired = 'key_is_otp_required';

  static const LeaveBalance defaultBalance = LeaveBalance(
    dayTotal: 75,
    dayAvailed: 12,
    dayBalance: 63,
    nightTotal: 60,
    nightAvailed: 9,
    nightBalance: 51,
  );

  static const LeaveProfile defaultProfile = LeaveProfile(
    uid: '',
    name: '',
    hostel: '',
    roomNo: '',
    authority: 'Warden',
    registeredMobile: '',
    homeAddress: '',
  );

  static Future<void> saveBalance(LeaveBalance balance) async {
    if (balance.dayTotal <= 0 && balance.nightTotal <= 0) return;
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(keyBalanceJson, jsonEncode(balance.toJson()));
  }

  static Future<LeaveBalance> loadBalance() async {
    final prefs = await SharedPreferences.getInstance();
    final raw = prefs.getString(keyBalanceJson);
    if (raw == null || raw.isEmpty) return defaultBalance;
    try {
      final map = jsonDecode(raw) as Map<String, dynamic>;
      final b = LeaveBalance.fromJson(map);
      return b.isLoaded ? b : defaultBalance;
    } catch (_) {
      return defaultBalance;
    }
  }

  static Future<void> saveRecords(List<LeaveRecord> list) async {
    final prefs = await SharedPreferences.getInstance();
    final arr = list.map((e) => e.toJson()).toList();
    await prefs.setString(keyRecordsJson, jsonEncode(arr));
  }

  static Future<List<LeaveRecord>> loadRecords() async {
    final prefs = await SharedPreferences.getInstance();
    final raw = prefs.getString(keyRecordsJson);
    if (raw == null || raw.isEmpty) return [];
    try {
      final list = jsonDecode(raw) as List<dynamic>;
      return list.map((e) => LeaveRecord.fromJson(e as Map<String, dynamic>)).toList();
    } catch (_) {
      return [];
    }
  }

  static Future<void> saveProfile(LeaveProfile profile) async {
    if (profile.uid.isEmpty && profile.name.isEmpty) return;
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(keyProfileJson, jsonEncode(profile.toJson()));
  }

  static Future<LeaveProfile> loadProfile() async {
    final prefs = await SharedPreferences.getInstance();
    final raw = prefs.getString(keyProfileJson);
    if (raw == null || raw.isEmpty) return defaultProfile;
    try {
      final map = jsonDecode(raw) as Map<String, dynamic>;
      final p = LeaveProfile.fromJson(map);
      return (p.uid.isNotEmpty || p.name.isNotEmpty) ? p : defaultProfile;
    } catch (_) {
      return defaultProfile;
    }
  }

  static Future<void> saveFormDefaults({
    required String stayAddress,
    required String reason,
    required String relativeMobile,
  }) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(keyLastStayAddr, stayAddress);
    await prefs.setString(keyLastReason, reason);
    await prefs.setString(keyLastRelativeMobile, relativeMobile);
  }

  static Future<String> getLastStayAddress() async {
    final prefs = await SharedPreferences.getInstance();
    return prefs.getString(keyLastStayAddr) ?? 'Phagwara Market';
  }

  static Future<String> getLastReason() async {
    final prefs = await SharedPreferences.getInstance();
    return prefs.getString(keyLastReason) ?? 'Personal work';
  }

  static Future<String> getLastRelativeMobile() async {
    final prefs = await SharedPreferences.getInstance();
    return prefs.getString(keyLastRelativeMobile) ?? '';
  }

  static Future<void> saveGatePass(HostelGatePass pass) async {
    final isExpired = pass.dateOfReturn.isNotEmpty && isLeaveExpired(pass.dateOfReturn, 0);
    final isCompleted = pass.checkInTime.isNotEmpty;
    final isInvalid = !pass.hasActivePass || pass.leaveId.isEmpty || pass.leaveId == '0' || pass.leaveId == '6000' || pass.leaveCode == '6000';

    if (isExpired || isCompleted || isInvalid) {
      await clearGatePass();
      return;
    }
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(keyGatePassJson, jsonEncode(pass.toJson()));
  }

  static Future<void> clearGatePass() async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.remove(keyGatePassJson);
  }

  static Future<HostelGatePass> loadGatePass() async {
    final prefs = await SharedPreferences.getInstance();
    final raw = prefs.getString(keyGatePassJson);
    if (raw == null || raw.isEmpty) return const HostelGatePass();
    try {
      final map = jsonDecode(raw) as Map<String, dynamic>;
      final pass = HostelGatePass.fromJson(map);
      final isExpired = pass.dateOfReturn.isNotEmpty && isLeaveExpired(pass.dateOfReturn, 0);
      final isCompleted = pass.checkInTime.isNotEmpty;
      final isInvalid = !pass.hasActivePass || pass.leaveId.isEmpty || pass.leaveId == '0' || pass.leaveId == '6000';

      if (isExpired || isCompleted || isInvalid) {
        await clearGatePass();
        return const HostelGatePass();
      }
      return pass;
    } catch (_) {
      return const HostelGatePass();
    }
  }

  static Future<bool> isOtpRequired() async {
    final prefs = await SharedPreferences.getInstance();
    return prefs.getBool(keyIsOtpRequired) ?? false;
  }

  static Future<void> setOtpRequired(bool required) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool(keyIsOtpRequired, required);
  }
}
