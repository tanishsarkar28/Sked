import 'dart:async';
import 'dart:convert';
import 'dart:io';
import 'package:flutter/foundation.dart';
import 'package:http/http.dart' as http;
import 'package:shared_preferences/shared_preferences.dart';
import 'package:webview_flutter/webview_flutter.dart';
import '../models/leave_models.dart';
import 'leave_parser.dart';
import 'leave_storage.dart';
import 'sync_service.dart';

sealed class LeaveSyncResult {
  const LeaveSyncResult();
}

class LeaveSyncSuccess extends LeaveSyncResult {
  final ParsedLeaveData data;
  const LeaveSyncSuccess(this.data);
}

class LeaveSyncExpired extends LeaveSyncResult {
  const LeaveSyncExpired();
}

class LeaveSyncError extends LeaveSyncResult {
  final String message;
  const LeaveSyncError(this.message);
}

sealed class LeaveSubmitResult {
  const LeaveSubmitResult();
}

class LeaveSubmitSuccess extends LeaveSubmitResult {
  final String message;
  const LeaveSubmitSuccess(this.message);
}

class LeaveSubmitOtpRequired extends LeaveSubmitResult {
  final String maskedPhone;
  const LeaveSubmitOtpRequired(this.maskedPhone);
}

class LeaveSubmitError extends LeaveSubmitResult {
  final String message;
  const LeaveSubmitError(this.message);
}

class LeaveSubmitExpired extends LeaveSubmitResult {
  const LeaveSubmitExpired();
}

class LeaveSubmitEngine {
  LeaveSubmitEngine._();

  static const String leaveUrl =
      'https://ums.lpu.in/lpuums/frmStudentHostelLeaveApplicationTermWise.aspx';
  static const String leaveSlipUrl =
      'https://ums.lpu.in/lpuums/frmHostelLeaveSlipTest.aspx';
  static const String studentPhotoUrl =
      'https://ums.lpu.in/lpuums/DisplayImageForPowerBi.aspx';

  static const String defaultUserAgent =
      'Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Mobile/15E148';

  /// Fetches leave balance, profile & history records using saved session cookies.
  static Future<LeaveSyncResult> fetchLatestData() async {
    try {
      final cookies = await SyncService.getSessionCookies();
      if (cookies.isEmpty) {
        return const LeaveSyncExpired();
      }

      final response = await http.get(
        Uri.parse(leaveUrl),
        headers: {
          'Cookie': cookies,
          'User-Agent': defaultUserAgent,
          'Accept':
              'text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8',
        },
      ).timeout(const Duration(seconds: 12));

      if (response.statusCode >= 300 && response.statusCode < 400) {
        final loc = response.headers['location'] ?? '';
        if (loc.toLowerCase().contains('login')) {
          return const LeaveSyncExpired();
        }
      }

      if (response.statusCode != 200) {
        return LeaveSyncError('HTTP ${response.statusCode}');
      }

      final html = response.body;
      if (html.toLowerCase().contains('loginnew.aspx') ||
          html.toLowerCase().contains('login.aspx') ||
          html.toLowerCase().contains('lockerror')) {
        return const LeaveSyncExpired();
      }

      if (html.isNotEmpty) {
        final parsed = LeaveParser.parse(html);
        final hasBal = parsed.balance.dayTotal > 0 || parsed.balance.nightTotal > 0;
        final hasProf = parsed.profile.uid.isNotEmpty || parsed.profile.name.isNotEmpty;
        final hasRecs = parsed.records.isNotEmpty;

        if (hasBal || hasRecs || hasProf) {
          if (hasBal) await LeaveStorage.saveBalance(parsed.balance);
          if (hasProf) await LeaveStorage.saveProfile(parsed.profile);
          if (hasRecs) await LeaveStorage.saveRecords(parsed.records);
          if (parsed.requiresOtp) {
            await LeaveStorage.setOtpRequired(true);
          }
          return LeaveSyncSuccess(parsed);
        }
      }
      return const LeaveSyncError('Could not parse leave portal data');
    } catch (e) {
      debugPrint('LeaveSubmitEngine.fetchLatestData error: $e');
      return LeaveSyncError(e.toString());
    }
  }

  /// Fetches official gate pass from frmHostelLeaveSlipTest.aspx
  static Future<HostelGatePass?> fetchGatePass() async {
    try {
      final cookies = await SyncService.getSessionCookies();
      if (cookies.isEmpty) return null;

      final response = await http.get(
        Uri.parse(leaveSlipUrl),
        headers: {
          'Cookie': cookies,
          'User-Agent': defaultUserAgent,
          'Referer': 'https://ums.lpu.in/lpuums/StudentDashboard.aspx',
        },
      ).timeout(const Duration(seconds: 12));

      if (response.statusCode != 200) return null;
      final html = response.body;

      if (html.toLowerCase().contains('loginnew.aspx') ||
          html.toLowerCase().contains('login.aspx')) {
        return null;
      }

      final pass = LeaveParser.parseGatePass(html);
      if (pass != null) {
        final isExpired = pass.dateOfReturn.isNotEmpty && isLeaveExpired(pass.dateOfReturn, 0);
        final isCheckInDone = pass.checkInTime.isNotEmpty;
        final umsHasNoSlip = !pass.hasActivePass || pass.leaveCode == '6000' || pass.leaveId == '0' || pass.leaveId.isEmpty;

        if (umsHasNoSlip || isExpired || isCheckInDone) {
          await LeaveStorage.clearGatePass();
        } else if (pass.hasActivePass) {
          await LeaveStorage.saveGatePass(pass);
          final photoUrl = pass.photoUrl.isNotEmpty ? pass.photoUrl : studentPhotoUrl;
          await downloadPhoto(photoUrl, cookies);
        }
        return pass;
      }
    } catch (e) {
      debugPrint('LeaveSubmitEngine.fetchGatePass error: $e');
    }
    return null;
  }

  /// Downloads student photo and saves it in app cache directory
  static Future<void> downloadPhoto(String photoUrl, String cookies) async {
    if (photoUrl.isEmpty) return;
    try {
      final response = await http.get(
        Uri.parse(photoUrl),
        headers: {
          'Cookie': cookies,
          'User-Agent': defaultUserAgent,
          'Referer': leaveSlipUrl,
        },
      ).timeout(const Duration(seconds: 10));

      if (response.statusCode == 200 && response.bodyBytes.length > 200) {
        final prefs = await SharedPreferences.getInstance();
        final base64Image = base64Encode(response.bodyBytes);
        await prefs.setString('cached_student_photo_base64', base64Image);
      }
    } catch (e) {
      debugPrint('downloadPhoto error: $e');
    }
  }

  /// Loads cached student photo bytes if available
  static Future<Uint8List?> getCachedPhotoBytes() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      final base64Image = prefs.getString('cached_student_photo_base64');
      if (base64Image != null && base64Image.isNotEmpty) {
        return base64Decode(base64Image);
      }
    } catch (_) {}
    return null;
  }
}
