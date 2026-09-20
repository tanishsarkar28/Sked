import 'dart:convert';
import 'package:http/http.dart' as http;
import 'package:shared_preferences/shared_preferences.dart';
import '../models/class_entry.dart';

/// Typed exception for expired session cookies.
class CookieExpiredException implements Exception {
  final String message;
  const CookieExpiredException([this.message = 'Session cookie has expired. Please refresh it in Settings.']);
  @override
  String toString() => message;
}

/// Typed exception for generic API errors.
class ApiException implements Exception {
  final String message;
  final int? statusCode;
  const ApiException(this.message, {this.statusCode});
  @override
  String toString() => 'ApiException($statusCode): $message';
}

/// Communicates with the sked-backend running on the user\'s PC.
class ApiService {
  static const _defaultBaseUrl = 'http://192.168.1.100:3000';
  static const _prefKeyBaseUrl = 'sked_backend_url';
  static const _prefKeyCookie = 'sked_session_cookie';

  static ApiService? _instance;
  ApiService._();
  static ApiService get instance => _instance ??= ApiService._();

  // ── settings helpers ──────────────────────────────────────────────────────

  Future<String> getBaseUrl() async {
    final prefs = await SharedPreferences.getInstance();
    return prefs.getString(_prefKeyBaseUrl) ?? _defaultBaseUrl;
  }

  Future<void> setBaseUrl(String url) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(_prefKeyBaseUrl, url.trimRight().replaceAll(RegExp(r'/$'), ''));
  }

  Future<String> getSavedCookie() async {
    final prefs = await SharedPreferences.getInstance();
    return prefs.getString(_prefKeyCookie) ?? '';
  }

  Future<void> saveCookie(String cookie) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(_prefKeyCookie, cookie.trim());
  }

  // ── internal HTTP ─────────────────────────────────────────────────────────

  Future<Map<String, dynamic>> _get(String path) async {
    final base = await getBaseUrl();
    final uri = Uri.parse('$base$path');

    final http.Response response;
    try {
      response = await http.get(uri).timeout(const Duration(seconds: 15));
    } catch (e) {
      throw ApiException('Cannot reach the Sked backend at $base. Is the server running and are you on the same Wi-Fi?\n\nDetails: $e');
    }

    if (response.statusCode == 401) {
      final body = jsonDecode(response.body) as Map<String, dynamic>;
      if (body['error'] == 'COOKIE_EXPIRED') throw const CookieExpiredException();
      throw ApiException(body['message'] ?? 'Unauthorized', statusCode: 401);
    }

    if (response.statusCode != 200) {
      throw ApiException('HTTP ${response.statusCode}', statusCode: response.statusCode);
    }

    return jsonDecode(response.body) as Map<String, dynamic>;
  }

  Future<void> _post(String path, Map<String, dynamic> body) async {
    final base = await getBaseUrl();
    final uri = Uri.parse('$base$path');
    final response = await http
        .post(uri, headers: {'Content-Type': 'application/json'}, body: jsonEncode(body))
        .timeout(const Duration(seconds: 15));
    if (response.statusCode != 200) {
      throw ApiException('POST $path failed (${response.statusCode})', statusCode: response.statusCode);
    }
  }

  // ── public API ────────────────────────────────────────────────────────────

  /// Fetches today\'s class entries.
  Future<({String day, List<ClassEntry> entries, DateTime? lastFetched})> getTodayTimetable() async {
    final data = await _get('/api/timetable/today');
    final rawEntries = (data['entries'] as List<dynamic>?) ?? [];
    final entries = rawEntries
        .map((e) => ClassEntry.fromJson(e as Map<String, dynamic>))
        .toList();
    DateTime? lastFetched;
    if (data['lastFetched'] != null) {
      lastFetched = DateTime.tryParse(data['lastFetched'] as String);
    }
    return (day: data['day'] as String? ?? '', entries: entries, lastFetched: lastFetched);
  }

  /// Fetches the full week timetable grouped by day name.
  Future<Map<String, List<ClassEntry>>> getWeekTimetable() async {
    final data = await _get('/api/timetable/week');
    final week = data['week'] as Map<String, dynamic>? ?? {};
    return week.map((day, rawList) {
      final list = (rawList as List<dynamic>)
          .map((e) => ClassEntry.fromJson(e as Map<String, dynamic>))
          .toList();
      return MapEntry(day, list);
    });
  }

  /// Returns the backend status (cookie validity, cache state, etc.).
  Future<Map<String, dynamic>> getStatus() async {
    return _get('/api/status');
  }

  /// Pushes an updated session cookie to the backend at runtime.
  Future<void> updateCookie(String cookie) async {
    await _post('/api/settings/cookie', {'cookie': cookie});
    await saveCookie(cookie);
  }

  /// Pushes an updated TermId to the backend at runtime.
  Future<void> updateTermId(String termId) async {
    await _post('/api/settings/termid', {'termId': termId});
  }

  /// Tells the backend to drop its cache so the next request re-fetches.
  Future<void> invalidateCache() async {
    final base = await getBaseUrl();
    await http.post(Uri.parse('$base/api/cache/invalidate'));
  }
}
