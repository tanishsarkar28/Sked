import '../models/leave_models.dart';

class ParsedLeaveData {
  final LeaveBalance balance;
  final LeaveProfile profile;
  final List<LeaveRecord> records;
  final bool requiresOtp;

  const ParsedLeaveData({
    this.balance = const LeaveBalance(),
    this.profile = const LeaveProfile(),
    this.records = const [],
    this.requiresOtp = false,
  });
}

class LeaveParser {
  LeaveParser._();

  static ParsedLeaveData parse(String html) {
    if (html.trim().isEmpty) return const ParsedLeaveData();

    final balance = parseBalance(html);
    final profile = parseProfile(html);
    final records = parseRecords(html);
    final requiresOtp = html.toLowerCase().contains('parent verification otp') ||
        html.toLowerCase().contains('otp has been sent to parent') ||
        html.toLowerCase().contains('parent otp verification') ||
        html.toLowerCase().contains('txtotp') && html.toLowerCase().contains('radtooltip1');

    return ParsedLeaveData(
      balance: balance,
      profile: profile,
      records: records,
      requiresOtp: requiresOtp,
    );
  }

  static LeaveBalance parseBalance(String html) {
    int dayTotal = 0;
    int dayAvailed = 0;
    int dayBalance = 0;

    int nightTotal = 0;
    int nightAvailed = 0;
    int nightBalance = 0;

    List<int>? extractCounts(String snippet) {
      if (snippet.trim().isEmpty) return null;
      int tot = -1;
      int av = -1;
      int bal = -1;
      bool foundAny = false;

      // 1. RadGrid TypeLabel and CountLabel spans
      final rowPattern = RegExp(
        r'TypeLabel[^>]*>\s*([^<]+)\s*</span>.*?CountLabel[^>]*>\s*(\d+)\s*</span>',
        caseSensitive: false,
        dotAll: true,
      );
      for (final m in rowPattern.allMatches(snippet)) {
        foundAny = true;
        final type = m.group(1)?.trim().toLowerCase() ?? '';
        final count = int.tryParse(m.group(2) ?? '') ?? 0;
        if (type == 'total') tot = count;
        if (type == 'availed') av = count;
        if (type == 'balance') bal = count;
      }

      // 2. Table cell pairs
      if (!foundAny || tot < 0) {
        final tdPattern = RegExp(
          r'<td[^>]*>\s*(Total|Availed|Balance)\s*</td>\s*<td[^>]*>\s*(\d+)\s*</td>',
          caseSensitive: false,
          dotAll: true,
        );
        for (final m in tdPattern.allMatches(snippet)) {
          foundAny = true;
          final type = m.group(1)?.trim().toLowerCase() ?? '';
          final count = int.tryParse(m.group(2) ?? '') ?? 0;
          if (type == 'total') tot = count;
          if (type == 'availed') av = count;
          if (type == 'balance') bal = count;
        }
      }

      // 3. Fallback pattern
      if (!foundAny || tot < 0) {
        final fallback = RegExp(
          r'(Total|Availed|Balance)[^\d<]{1,150}(\d+)',
          caseSensitive: false,
          dotAll: true,
        );
        for (final m in fallback.allMatches(snippet)) {
          foundAny = true;
          final type = m.group(1)?.trim().toLowerCase() ?? '';
          final count = int.tryParse(m.group(2) ?? '') ?? 0;
          if (type == 'total') tot = count;
          if (type == 'availed') av = count;
          if (type == 'balance') bal = count;
        }
      }

      if (tot >= 0 || av >= 0 || bal >= 0) {
        final safeTot = tot >= 0 ? tot : 0;
        final safeAv = av >= 0 ? av : 0;
        final safeBal = bal >= 0 ? bal : (safeTot - safeAv < 0 ? 0 : safeTot - safeAv);
        return [safeTot, safeAv, safeBal];
      }
      return null;
    }

    try {
      final dayHeaderMatch = RegExp(r'Day\s+Leave\s+Details', caseSensitive: false).firstMatch(html);
      final dayHeaderIdx = dayHeaderMatch?.start ?? -1;

      final nightHeaderMatch = RegExp(r'Night\s+Leave\s+Details', caseSensitive: false).firstMatch(html);
      final nightHeaderIdx = nightHeaderMatch?.start ?? -1;

      String daySnippet = '';
      String nightSnippet = '';

      if (dayHeaderIdx != -1 && nightHeaderIdx != -1 && dayHeaderIdx < nightHeaderIdx) {
        daySnippet = html.substring(dayHeaderIdx, nightHeaderIdx);
        final endBound = nightHeaderIdx + 3000 > html.length ? html.length : nightHeaderIdx + 3000;
        final afterNight = html.substring(nightHeaderIdx, endBound);
        final endTable = afterNight.toLowerCase().indexOf('</table>');
        nightSnippet = endTable != -1 ? afterNight.substring(0, endTable + 8) : afterNight;
      } else if (dayHeaderIdx != -1 && nightHeaderIdx != -1 && nightHeaderIdx < dayHeaderIdx) {
        nightSnippet = html.substring(nightHeaderIdx, dayHeaderIdx);
        final endBound = dayHeaderIdx + 3000 > html.length ? html.length : dayHeaderIdx + 3000;
        final afterDay = html.substring(dayHeaderIdx, endBound);
        final endTable = afterDay.toLowerCase().indexOf('</table>');
        daySnippet = endTable != -1 ? afterDay.substring(0, endTable + 8) : afterDay;
      } else {
        if (dayHeaderIdx != -1) {
          final endBound = dayHeaderIdx + 3000 > html.length ? html.length : dayHeaderIdx + 3000;
          final sub = html.substring(dayHeaderIdx, endBound);
          final endTable = sub.toLowerCase().indexOf('</table>');
          daySnippet = endTable != -1 ? sub.substring(0, endTable + 8) : sub;
        }
        if (nightHeaderIdx != -1) {
          final endBound = nightHeaderIdx + 3000 > html.length ? html.length : nightHeaderIdx + 3000;
          final sub = html.substring(nightHeaderIdx, endBound);
          final endTable = sub.toLowerCase().indexOf('</table>');
          nightSnippet = endTable != -1 ? sub.substring(0, endTable + 8) : sub;
        }
      }

      final dayCounts = extractCounts(daySnippet);
      if (dayCounts != null) {
        dayTotal = dayCounts[0];
        dayAvailed = dayCounts[1];
        dayBalance = dayCounts[2];
      }

      final nightCounts = extractCounts(nightSnippet);
      if (nightCounts != null) {
        nightTotal = nightCounts[0];
        nightAvailed = nightCounts[1];
        nightBalance = nightCounts[2];
      }
    } catch (_) {}

    return LeaveBalance(
      dayTotal: dayTotal,
      dayAvailed: dayAvailed,
      dayBalance: dayBalance,
      nightTotal: nightTotal,
      nightAvailed: nightAvailed,
      nightBalance: nightBalance,
    );
  }

  static LeaveProfile parseProfile(String html) {
    String extractByPattern(String pattern) {
      final m = RegExp(pattern, caseSensitive: false, dotAll: true).firstMatch(html);
      return m?.group(1)?.replaceAll(RegExp(r'<[^>]+>'), '').trim() ?? '';
    }

    final uid = extractByPattern(r'UID\s*[:\-]?\s*(\d{7,10})');
    final name = extractByPattern(r'Name\s*[:\-]?\s*</td>\s*<td[^>]*>\s*([^<]+)');
    final hostel = extractByPattern(r'Hostel\s*[:\-]?\s*</td>\s*<td[^>]*>\s*([^<]+)');
    final roomNo = extractByPattern(r'Room\s*No\s*[:\-]?\s*</td>\s*<td[^>]*>\s*([^<]+)');
    final authority = extractByPattern(r'Sanctioning\s*Authority\s*[:\-]?\s*</td>\s*<td[^>]*>\s*([^<]+)');

    String registeredMobile = '';
    final contactNoMatch = RegExp(r'<input[^>]*ctl00_cphHeading_txtContactNo[^>]*>', caseSensitive: false).firstMatch(html);
    if (contactNoMatch != null) {
      final tag = contactNoMatch.group(0) ?? '';
      final valMatch = RegExp(r'''value=["'](\d{10})["']''').firstMatch(tag);
      if (valMatch != null) {
        registeredMobile = valMatch.group(1) ?? '';
      }
    }

    final homeAddress = extractByPattern(r'ctl00_cphHeading_lblAddress[^>]*>([^<]+)<');

    return LeaveProfile(
      uid: uid,
      name: name,
      hostel: hostel,
      roomNo: roomNo,
      authority: authority.isNotEmpty ? authority : 'Warden',
      registeredMobile: registeredMobile,
      homeAddress: homeAddress,
    );
  }

  static List<LeaveRecord> parseRecords(String html) {
    final list = <LeaveRecord>[];
    try {
      final gridMatch = RegExp(
        r'(?:id\s*=\s*["\'][^"\']*grdLeaveDetail[^"\']*["\']|grdLeaveDetail).*?>(.*?)</table>',
        caseSensitive: false,
        dotAll: true,
      ).firstMatch(html);
      if (gridMatch == null) return [];

      final tableHtml = gridMatch.group(1) ?? '';
      final trPattern = RegExp(r'<tr[^>]*>(.*?)</tr>', caseSensitive: false, dotAll: true);
      final trMatches = trPattern.allMatches(tableHtml);

      bool isHeader = true;
      for (final tr in trMatches) {
        final rowHtml = tr.group(1) ?? '';
        if (isHeader) {
          isHeader = false;
          continue;
        }

        final tdPattern = RegExp(r'<td[^>]*>(.*?)</td>', caseSensitive: false, dotAll: true);
        final cells = tdPattern.allMatches(rowHtml).map((m) {
          return (m.group(1) ?? '')
              .replaceAll(RegExp(r'<[^>]+>'), '')
              .replaceAll('&nbsp;', ' ')
              .trim();
        }).toList();

        if (cells.isNotEmpty && RegExp(r'^\d+$').hasMatch(cells[0])) {
          int statusIdx = 6;
          for (var i = 0; i < cells.length; i++) {
            final c = cells[i].toLowerCase();
            if (c == 'approved' || c == 'pending' || c == 'rejected' || c == 'cancelled') {
              statusIdx = i;
              break;
            }
          }

          list.add(
            LeaveRecord(
              leaveId: cells[0],
              leaveType: cells.length > 1 ? cells[1] : '',
              applyDate: cells.length > 2 ? cells[2] : '',
              leaveDate: cells.length > 3 ? cells[3] : '',
              returnDate: cells.length > 4 ? cells[4] : '',
              status: cells.length > statusIdx ? cells[statusIdx] : 'Approved',
              checkOut: cells.length > statusIdx + 1 ? cells[statusIdx + 1] : '',
              checkIn: cells.length > statusIdx + 2 ? cells[statusIdx + 2] : '',
            ),
          );
        }
      }
    } catch (_) {}
    return list;
  }

  static HostelGatePass? parseGatePass(String html) {
    if (html.trim().isEmpty) return null;
    if (html.toLowerCase().contains('loginnew.aspx') ||
        html.toLowerCase().contains('login.aspx') ||
        html.toLowerCase().contains('lockerror')) {
      return null;
    }

    String extractText(String idSubstr) {
      final p1 = RegExp('<[^>]+id\\s*=\\s*["\'][^"\']*$idSubstr[^"\']*["\'][^>]*>(.*?)</', caseSensitive: false, dotAll: true);
      final m1 = p1.firstMatch(html);
      if (m1 != null) {
        final t = (m1.group(1) ?? '').replaceAll(RegExp(r'<[^>]+>'), '').replaceAll('&nbsp;', ' ').trim();
        if (t.isNotEmpty) return t;
      }
      final p2 = RegExp(r'<input[^>]+>', caseSensitive: false);
      for (final m in p2.allMatches(html)) {
        final tag = m.group(0) ?? '';
        if (tag.toLowerCase().contains(idSubstr.toLowerCase())) {
          final vp = RegExp(r'''value\s*=\s*["']([^"']*)["']''', caseSensitive: false).firstMatch(tag);
          if (vp != null) {
            final v = vp.group(1)?.trim() ?? '';
            if (v.isNotEmpty) return v;
          }
        }
      }
      return '';
    }

    var leaveId = extractText('lblLid');
    if (leaveId.isEmpty) {
      final m = RegExp(r'\b(5\d{6})\b').firstMatch(html);
      if (m != null) leaveId = m.group(1) ?? '';
    }

    var leaveCode = extractText('lblLCode');
    if (leaveCode.isEmpty) leaveCode = extractText('hfLeaveCode');
    if (leaveCode.isEmpty && leaveId.isNotEmpty) leaveCode = leaveId;

    final leaveType = extractText('lblLName').isNotEmpty ? extractText('lblLName') : 'Day Leave(Extended Leave Timing)';
    final regNo = extractText('lblRegNo');
    final name = extractText('lblName');
    final dateOfApply = extractText('lblDateofapply');
    final dateOfLeaving = extractText('lblDateofleave').isNotEmpty ? extractText('lblDateofleave') : extractText('lblLeaveDate');
    final dateOfReturn = extractText('lblDateofreturn').isNotEmpty ? extractText('lblDateofreturn') : extractText('lblReturnDate');
    final checkOutTime = extractText('lblcheckout').isNotEmpty ? extractText('lblcheckout') : extractText('lblCheckOut');
    final checkInTime = extractText('lblcheckin').isNotEmpty ? extractText('lblcheckin') : extractText('lblCheckIn');
    final contactNo = extractText('lblmobile');
    final hostel = extractText('lblhostel');
    final roomNo = extractText('lblroomno');

    final gates = <GateActivation>[];
    try {
      final gateGridMatch = RegExp(r'grdGateStatus.*?>(.*?)</table>', caseSensitive: false, dotAll: true).firstMatch(html);
      if (gateGridMatch != null) {
        final trMatches = RegExp(r'<tr[^>]*>(.*?)</tr>', caseSensitive: false, dotAll: true).allMatches(gateGridMatch.group(1) ?? '');
        for (final tr in trMatches) {
          final tdMatches = RegExp(r'<td[^>]*>(.*?)</td>', caseSensitive: false, dotAll: true).allMatches(tr.group(1) ?? '').map((m) {
            return (m.group(1) ?? '').replaceAll(RegExp(r'<[^>]+>'), '').replaceAll('&nbsp;', ' ').trim();
          }).toList();
          if (tdMatches.length >= 2 && tdMatches[0].isNotEmpty && tdMatches[0].toLowerCase() != 'gate') {
            gates.add(GateActivation(gateName: tdMatches[0], status: tdMatches[1].isNotEmpty ? tdMatches[1] : '✔ Activated'));
          }
        }
      }
    } catch (_) {}

    final hasActive = leaveId.isNotEmpty && leaveId != '0' && leaveCode != '6000';

    return HostelGatePass(
      universityTitle: 'Lovely Professional University',
      passSubtitle: 'Hostel Leave Gate Pass',
      leaveType: leaveType,
      leaveId: leaveId,
      leaveCode: leaveCode,
      regNo: regNo,
      name: name,
      dateOfApply: dateOfApply,
      dateOfLeaving: dateOfLeaving,
      dateOfReturn: dateOfReturn,
      checkOutTime: checkOutTime,
      checkInTime: checkInTime,
      contactNo: contactNo,
      hostel: hostel,
      roomNo: roomNo,
      gates: gates.isNotEmpty ? gates : const [GateActivation(gateName: 'Main Gate', status: '✔ Activated')],
      note: 'Note : Do not look back after checkout; please proceed forward only.',
      hasActivePass: hasActive,
      photoUrl: 'https://ums.lpu.in/lpuums/DisplayImageForPowerBi.aspx',
    );
  }
}
