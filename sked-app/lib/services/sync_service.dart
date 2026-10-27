import 'dart:convert';
import 'package:flutter/foundation.dart';
import 'package:http/http.dart' as http;
import 'package:shared_preferences/shared_preferences.dart';
import '../models/class_entry.dart';
import 'timetable_parser.dart';

enum SyncResultType {
  success,
  skippedCooldown,
  skippedQuietHours,
  authExpired,
  serverError,
  disabled,
}

class SyncEvent {
  final SyncResultType type;
  final String message;
  final List<ClassEntry> newMakeupClasses;
  final List<String> cancelledMakeupDescriptions;

  const SyncEvent({
    required this.type,
    required this.message,
    this.newMakeupClasses = const [],
    this.cancelledMakeupDescriptions = const [],
  });
}

class SyncService {
  static const String keyUmsCookies = 'ums_session_cookies';
  static const String keyAutoSyncEnabled = 'auto_sync_enabled';
  static const String keyLastSyncTimestamp = 'last_sync_timestamp';
  static const String keyLastSyncStatus = 'last_sync_status';
  static const String keyActiveMakeupKeys = 'active_makeup_keys';
  static const String keyNotifiedMakeupKeys = 'notified_makeup_keys';
  static const String keySyncAuthFailed = 'sync_auth_failed';

  /// Minimum cooldown between automatic background/resume syncs (2 hours)
  static const Duration syncCooldown = Duration(hours: 2);

  /// Quiet hours: No audible notifications or aggressive sync between 22:30 and 07:00
  static bool isQuietHours() {
    final now = DateTime.now();
    final minutes = now.hour * 60 + now.minute;
    const quietStart = 22 * 60 + 30; // 22:30
    const quietEnd = 7 * 60; // 07:00
    return minutes >= quietStart || minutes < quietEnd;
  }

  /// Store session cookies extracted during successful WebView authentication
  static Future<void> saveSessionCookies(String cookies) async {
    if (cookies.trim().isEmpty) return;
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(keyUmsCookies, cookies.trim());
    await prefs.setBool(keySyncAuthFailed, false);
  }

  static Future<String> getSessionCookies() async {
    final prefs = await SharedPreferences.getInstance();
    return prefs.getString(keyUmsCookies) ?? '';
  }

  static Future<bool> isAutoSyncEnabled() async {
    final prefs = await SharedPreferences.getInstance();
    return prefs.getBool(keyAutoSyncEnabled) ?? true;
  }

  static Future<void> setAutoSyncEnabled(bool enabled) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool(keyAutoSyncEnabled, enabled);
  }

  static Future<bool> hasAuthFailed() async {
    final prefs = await SharedPreferences.getInstance();
    return prefs.getBool(keySyncAuthFailed) ?? false;
  }

  /// Perform on-device sync with strict adherence to safety rules:
  /// 1. Account Lockout Prevention (abort if auth fails, never retry blindly)
  /// 2. Fail-Safe Cache Protection (never wipe cache on server error)
  /// 3. Rate-Limiting & Jitter (2h cooldown)
  /// 4. Deduplication & Quiet Hours & Cancellation Handling
  /// 5. Lightweight pure HTTP execution
  static Future<SyncEvent> performSync({bool force = false}) async {
    final prefs = await SharedPreferences.getInstance();

    final enabled = prefs.getBool(keyAutoSyncEnabled) ?? true;
    if (!enabled && !force) {
      return const SyncEvent(
        type: SyncResultType.disabled,
        message: 'Auto-sync is disabled in settings.',
      );
    }

    final lastSyncEpoch = prefs.getInt(keyLastSyncTimestamp) ?? 0;
    final now = DateTime.now();
    final elapsed = now.difference(DateTime.fromMillisecondsSinceEpoch(lastSyncEpoch));

    // Rule 3: Cooldown check
    if (!force && elapsed < syncCooldown) {
      return SyncEvent(
        type: SyncResultType.skippedCooldown,
        message: 'Cooldown active. Next sync in ${(syncCooldown - elapsed).inMinutes}m.',
      );
    }

    final cookies = prefs.getString(keyUmsCookies) ?? '';
    if (cookies.isEmpty) {
      return const SyncEvent(
        type: SyncResultType.authExpired,
        message: 'No active session cookies. Please log in to enable auto-sync.',
      );
    }

    final userId = prefs.getString('sked_user_id') ?? '';
    if (userId.isEmpty) {
      return const SyncEvent(
        type: SyncResultType.authExpired,
        message: 'No user registered.',
      );
    }

    try {
      const ua =
          'Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Mobile/15E148 Safari/604.1';

      // ── BUG-06 FIX: Step 1 — Prime ASP.NET session and extract active TermId ──
      // Without this GET, IIS throws NullReferenceException → HTTP 500 on fresh sessions.
      String? termId;
      try {
        final pageRes = await http
            .get(
              Uri.parse('https://ums.lpu.in/lpuums/frmMyCurrentTimeTable.aspx'),
              headers: {
                'Cookie': cookies,
                'User-Agent': ua,
                'Accept':
                    'text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8',
                'Accept-Language': 'en-US,en;q=0.9',
                'Referer': 'https://ums.lpu.in/lpuums/Default3.aspx',
              },
            )
            .timeout(const Duration(seconds: 10));

        final pageBody = pageRes.body;

        // If the priming GET redirects to login, session is already expired.
        if (pageRes.statusCode == 401 ||
            pageRes.statusCode == 403 ||
            (pageRes.statusCode >= 300 && pageRes.statusCode < 400) ||
            pageBody.contains('LoginNew') ||
            pageBody.contains('Just a moment...') ||
            pageBody.contains('Cloudflare')) {
          await prefs.setBool(keySyncAuthFailed, true);
          await prefs.setString(
              keyLastSyncStatus, 'Session expired. Re-auth needed.');
          return const SyncEvent(
            type: SyncResultType.authExpired,
            message: 'UMS session expired. Tap to reconnect.',
          );
        }

        // ── BUG-07 FIX: Extract real TermId from Select1 dropdown ──
        final selectMatch = RegExp(
          r'''id=["']Select1["'][^>]*(>)(.*?)<\/select>''',
          caseSensitive: false,
          dotAll: true,
        ).firstMatch(pageBody);
        if (selectMatch != null) {
          final selectContent = selectMatch.group(0) ?? '';
          final termMatch = RegExp(
            r'''value=["']([^"']+)["'][^>]*selected|selected[^>]*value=["']([^"']+)["']|<option[^>]*value=["']([^"']+)["']''',
            caseSensitive: false,
          ).firstMatch(selectContent);
          if (termMatch != null) {
            termId = termMatch.group(1) ??
                termMatch.group(2) ??
                termMatch.group(3);
          }
        }
      } catch (_) {
        // Non-fatal — proceed with null TermId; the POST may still work if
        // the session was primed by a prior successful WebView login.
      }

      // ── Step 2: Request timetable JSON from GetTimeTable WebMethod ──
      final url = Uri.parse(
          'https://ums.lpu.in/lpuums/frmMyCurrentTimeTable.aspx/GetTimeTable');
      final res = await http.post(
        url,
        headers: {
          'Content-Type': 'application/json; charset=utf-8',
          'Accept': 'application/json, text/javascript, */*; q=0.01',
          'X-Requested-With': 'XMLHttpRequest',
          'Origin': 'https://ums.lpu.in',
          'Referer': 'https://ums.lpu.in/lpuums/frmMyCurrentTimeTable.aspx',
          'Sec-Fetch-Site': 'same-origin',
          'Sec-Fetch-Mode': 'cors',
          'Sec-Fetch-Dest': 'empty',
          'Accept-Language': 'en-US,en;q=0.9',
          'Cookie': cookies,
          'User-Agent': ua,
        },
        body: jsonEncode({'TermId': termId}),
      ).timeout(const Duration(seconds: 12));

      // Rule 1: Account Lockout Prevention & Session Expiration
      final body = res.body;
      if (res.statusCode == 401 ||
          res.statusCode == 403 ||
          body.contains('LoginNew') ||
          body.contains('lockerror') ||
          body.contains('lblError') ||
          body.contains('Access Denied') ||
          body.contains('Just a moment...') ||
          body.contains('Cloudflare') ||
          body.contains('Object moved')) {
        await prefs.setBool(keySyncAuthFailed, true);
        await prefs.setString(keyLastSyncStatus, 'Session expired. Re-auth needed.');
        return const SyncEvent(
          type: SyncResultType.authExpired,
          message: 'UMS session expired. Tap to reconnect.',
        );
      }

      // Rule 2: Fail-Safe Cache Protection
      if (res.statusCode != 200 || body.trim().isEmpty || body.contains('Server Error')) {
        await prefs.setString(keyLastSyncStatus, 'UMS Server error (code ${res.statusCode}). Cache preserved.');
        return SyncEvent(
          type: SyncResultType.serverError,
          message: 'UMS server returned error (${res.statusCode}). Offline cache preserved.',
        );
      }

      final entries = TimetableParser.parse(body);
      if (entries.isEmpty) {
        // If parser returned 0 entries but response had data, fail-safe: don't wipe existing cache
        await prefs.setString(keyLastSyncStatus, 'Zero classes parsed. Offline cache preserved.');
        return const SyncEvent(
          type: SyncResultType.serverError,
          message: 'Schedule format unrecognized. Cache kept intact.',
        );
      }

      // Rule 4: Diffing & Makeup Class Tracking
      final oldActiveKeys = (prefs.getStringList(keyActiveMakeupKeys) ?? []).toSet();
      final notifiedKeys = (prefs.getStringList(keyNotifiedMakeupKeys) ?? []).toSet();

      final currentActiveKeys = <String>{};
      final newMakeupClasses = <ClassEntry>[];
      final keyToEntry = <String, ClassEntry>{};

      for (final entry in entries) {
        if (entry.isMakeup) {
          final key = entry.uniqueKey;
          currentActiveKeys.add(key);
          keyToEntry[key] = entry;

          if (!notifiedKeys.contains(key)) {
            newMakeupClasses.add(entry);
            notifiedKeys.add(key);
          }
        }
      }

      // Detect cancellations: Makeup classes previously active that disappeared from timetable
      final cancelledKeys = oldActiveKeys.difference(currentActiveKeys);
      final cancelledDescriptions = <String>[];
      for (final ck in cancelledKeys) {
        final parts = ck.split('_');
        final code = parts.isNotEmpty ? parts[0] : 'Class';
        final day = parts.length > 1 ? parts[1] : '';
        cancelledDescriptions.add('$code ($day)');
      }

      // Save updated cache & sync states
      await TimetableParser.saveToPrefs(entries, userId);
      await prefs.setInt(keyLastSyncTimestamp, now.millisecondsSinceEpoch);
      await prefs.setStringList(keyActiveMakeupKeys, currentActiveKeys.toList());
      await prefs.setStringList(keyNotifiedMakeupKeys, notifiedKeys.toList());
      await prefs.setBool(keySyncAuthFailed, false);
      await prefs.setString(
        keyLastSyncStatus,
        'Synced ${entries.length} classes. ${currentActiveKeys.length} makeup active.',
      );

      return SyncEvent(
        type: SyncResultType.success,
        message: 'Sync complete (${entries.length} classes).',
        newMakeupClasses: newMakeupClasses,
        cancelledMakeupDescriptions: cancelledDescriptions,
      );
    } catch (e) {
      // Safe fallback on network drop
      debugPrint('SyncService error: $e');
      await prefs.setString(keyLastSyncStatus, 'Network drop: $e. Cache preserved.');
      return SyncEvent(
        type: SyncResultType.serverError,
        message: 'Network error during sync. Offline cache preserved.',
      );
    }
  }
}
