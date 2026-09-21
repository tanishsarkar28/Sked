import 'dart:async';
import 'dart:convert';
import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:webview_flutter/webview_flutter.dart';
import '../services/timetable_parser.dart';
import '../services/exam_parser.dart';
import '../theme/app_theme.dart';

class UmsAuthBridgeSheet extends StatefulWidget {
  final String userId;
  final String password;
  final VoidCallback onDismiss;
  final ValueChanged<String> onSuccess;

  const UmsAuthBridgeSheet({
    super.key,
    required this.userId,
    required this.password,
    required this.onDismiss,
    required this.onSuccess,
  });

  @override
  State<UmsAuthBridgeSheet> createState() => _UmsAuthBridgeSheetState();
}

class _UmsAuthBridgeSheetState extends State<UmsAuthBridgeSheet> {
  late final WebViewController _controller;
  String _statusText = 'Connecting to LPU UMS...';
  bool _isSyncing = false;
  bool _isDone = false;
  Timer? _pollTimer;

  @override
  void initState() {
    super.initState();
    _initWebView();
  }

  void _initWebView() {
    _controller = WebViewController()
      ..setJavaScriptMode(JavaScriptMode.unrestricted)
      ..setBackgroundColor(SkedColors.ink)
      ..setNavigationDelegate(
        NavigationDelegate(
          onPageStarted: (url) {
            if (mounted) setState(() => _statusText = 'Loading UMS Portal...');
          },
          onPageFinished: (url) {
            if (mounted) setState(() => _statusText = 'Waiting for Turnstile verification...');
          },
          onWebResourceError: (error) {
            if (mounted && !_isDone) {
              setState(() => _statusText = 'Connection notice: ${error.description}');
            }
          },
        ),
      )
      ..loadRequest(Uri.parse('https://ums.lpu.in/lpuums/LoginNew.aspx'));

    _startPolling();
  }

  void _startPolling() {
    _pollTimer?.cancel();
    _pollTimer = Timer.periodic(const Duration(milliseconds: 900), (_) => _tick());
  }

  Future<void> _tick() async {
    if (_isDone || !mounted) return;

    final safeUser = jsonEncode(widget.userId);
    final safePass = jsonEncode(widget.password);

    final tickJs = '''
(function() {
    try {
        var u = document.getElementById('txtU');
        var p = document.querySelector('input[type="password"]');
        var cf = document.querySelector('[name="cf-turnstile-response"]');
        var form = document.querySelector('form');

        if (u) {
            u.removeAttribute('onchange');
            u.onchange = null;
            if ($safeUser && (!u.value || u.value.length === 0)) {
                u.value = $safeUser;
            }
        }
        if (p && $safePass && (!p.value || p.value.length === 0)) {
            p.value = $safePass;
        }

        if (window._skedTimetableResult) {
            return JSON.stringify({ hasResult: true, status: 'Timetable ready' });
        }

        if (window._skedIsSyncing) {
            return JSON.stringify({ syncing: true, status: window._skedStatus || 'Authenticating with UMS...' });
        }

        function triggerSubmit() {
            if (window._skedIsSyncing || window._skedTimetableResult) return;
            var curU = document.getElementById('txtU');
            var curP = document.querySelector('input[type="password"]');
            var curCf = document.querySelector('[name="cf-turnstile-response"]');

            if (curU && !curU.value && $safeUser) curU.value = $safeUser;
            if (curP && !curP.value && $safePass) curP.value = $safePass;

            if (!curU || !curU.value) {
                window._skedStatus = 'Please enter Registration No';
                return;
            }
            if (!curP || !curP.value) {
                window._skedStatus = 'Please enter password';
                return;
            }
            if (!curCf || !curCf.value) {
                window._skedStatus = 'Please complete Turnstile check';
                return;
            }

            window._skedIsSyncing = true;
            window._skedStatus = 'Authenticating with UMS...';

            var fd = new FormData(form);
            var params = new URLSearchParams(fd);
            var submitBtn = form ? form.querySelector('input[type="submit"]') : null;
            if (submitBtn && !params.has(submitBtn.name)) {
                params.append(submitBtn.name, submitBtn.value || 'Login');
            }

            function fetchAllData(loginHtml, curUser, curPass) {
                window._skedStatus = 'Fetching schedule & datesheet...';

                function safeFetch(url, name) {
                    return fetch(url, { credentials: 'include' })
                        .then(function(r) {
                            if (!r.ok) return { name: name, status: r.status, html: '' };
                            return r.text().then(function(t) {
                                if (t.includes('LoginNew') || t.includes('lockerror') || t.includes('lblError')) {
                                    return { name: name, status: 401, html: '' };
                                }
                                return { name: name, status: r.status, html: t };
                            });
                        })
                        .catch(function(e) {
                            return { name: name, status: 0, html: '', error: e.toString() };
                        });
                }

                var ttPromise = safeFetch('/lpuums/frmMyCurrentTimeTable.aspx', 'ttAspx')
                    .then(function(res) {
                        var m = res.html ? res.html.match(/id=["']Select1["'][^>]*>([\\s\\S]*?)<\\/select>/i) : null;
                        var termId = '';
                        if (m) {
                            var selM = m[1].match(/selected[^>]*value=["']([^"']+)["']/i)
                                    || m[1].match(/value=["']([^"']+)["'][^>]*selected/i)
                                    || m[1].match(/value=["']([^"']+)["']/i);
                            if (selM) termId = selM[1];
                        }
                        return fetch('/lpuums/frmMyCurrentTimeTable.aspx/GetTimeTable', {
                            method: 'POST',
                            credentials: 'include',
                            headers: {
                                'Content-Type': 'application/json; charset=utf-8',
                                'X-Requested-With': 'XMLHttpRequest'
                            },
                            body: JSON.stringify({ TermId: termId || null })
                        }).then(function(r) { return r.text(); });
                    })
                    .then(function(json) {
                        window._skedTimetableResult = json;
                    })
                    .catch(function(e) {
                        window._skedError = 'Failed to fetch timetable: ' + e.toString();
                    });

                var dsPromise = safeFetch('/lpuums/frmStudentDateSheet.aspx', 'datesheet')
                    .then(function(res) {
                        if (res.html && res.html.length > 200 && !res.html.includes('LoginNew')) {
                            window._skedDatesheetResult = res.html;
                        } else {
                            return safeFetch('/lpuums/frmDateSheet.aspx', 'datesheet2')
                                .then(function(res2) {
                                    if (res2.html && res2.html.length > 200 && !res2.html.includes('LoginNew')) {
                                        window._skedDatesheetResult = res2.html;
                                    } else {
                                        return safeFetch('/lpuums/frmSeatingPlan.aspx', 'seating')
                                            .then(function(res3) {
                                                window._skedDatesheetResult = (res3.html && res3.html.length > 200 && !res3.html.includes('LoginNew')) ? res3.html : '';
                                            });
                                    }
                                });
                        }
                    })
                    .catch(function() {
                        window._skedDatesheetResult = '';
                    });

                return Promise.all([ttPromise, dsPromise]);
            }

            fetch(form ? form.action : window.location.href, {
                method: 'POST',
                headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                body: params.toString()
            }).then(function(r) {
                return r.text();
            }).then(function(html) {
                if (html.includes('StudentDashboard') || html.includes('frmMyCurrentTimeTable') || html.includes('w-schedule') || html.includes('Default3')) {
                    return fetchAllData(html, curU.value, curP.value);
                } else if (html.includes('This App is not trusted')) {
                    window._skedIsSyncing = false;
                    window._skedError = 'LPU server rejected request. Retrying...';
                } else {
                    window._skedIsSyncing = false;
                    var errM = html.match(/id=["']lockerror["'][^>]*>([^<]+)<\\/span>/i)
                            || html.match(/id=["']lblError["'][^>]*>([^<]+)<\\/span>/i);
                    window._skedError = errM ? errM[1] : 'Invalid credentials. Please verify ID and password.';
                }
            }).catch(function(err) {
                window._skedIsSyncing = false;
                window._skedError = 'Network error: ' + err.toString();
            });
        }

        window.skedForceSubmit = triggerSubmit;

        if (form && !form.__skedBound) {
            form.__skedBound = true;
            form.onsubmit = function(e) {
                if (e) { e.preventDefault(); e.stopPropagation(); }
                triggerSubmit();
                return false;
            };
            var submitBtn = form.querySelector('input[type="submit"]');
            if (submitBtn) {
                submitBtn.onclick = function(e) {
                    if (e) { e.preventDefault(); e.stopPropagation(); }
                    triggerSubmit();
                    return false;
                };
            }
        }

        if (!window._skedIsSyncing && !window._skedTimetableResult) {
            if (window.location.href.includes('Default3') || window.location.href.includes('StudentDashboard') || window.location.href.includes('frmMyCurrentTimeTable')) {
                window._skedIsSyncing = true;
                fetchAllData(document.documentElement.outerHTML, $safeUser, $safePass);
            } else if (cf && cf.value && cf.value.length > 20 && u && u.value && p && p.value) {
                triggerSubmit();
            }
        }

        var st = 'Waiting for Turnstile verification...';
        if (cf && cf.value && cf.value.length > 20) st = 'Turnstile verified! Submitting...';

        return JSON.stringify({
            status: window._skedError || st,
            error: window._skedError || '',
            syncing: !!window._skedIsSyncing,
            hasResult: !!window._skedTimetableResult
        });
    } catch(e) {
        return JSON.stringify({ error: e.toString() });
    }
})();
''';

    try {
      final raw = await _controller.runJavaScriptReturningResult(tickJs);
      final rawStr = raw.toString().trim();
      if (rawStr.isEmpty || rawStr == '""' || rawStr == 'null') return;

      var unquoted = rawStr;
      if (unquoted.startsWith('"') && unquoted.endsWith('"')) {
        try {
          final dec = jsonDecode('{"v":$unquoted}') as Map<String, dynamic>;
          unquoted = dec['v'] as String? ?? '';
        } catch (_) {}
      }

      final stateObj = jsonDecode(unquoted) as Map<String, dynamic>;
      final st = stateObj['status'] as String? ?? '';
      final err = stateObj['error'] as String? ?? '';
      final syncing = stateObj['syncing'] == true;
      final hasRes = stateObj['hasResult'] == true;

      if (mounted) {
        setState(() {
          if (st.isNotEmpty) _statusText = st;
          if (err.isNotEmpty) _statusText = err;
          _isSyncing = syncing;
        });
      }

      if (hasRes && !_isDone) {
        _isDone = true;
        _pollTimer?.cancel();
        if (mounted) {
          setState(() {
            _statusText = 'Syncing schedule & datesheet...';
            _isSyncing = true;
          });
        }
        await _processResults();
      }
    } catch (_) {}
  }

  Future<void> _processResults() async {
    try {
      final ttRawObj = await _controller.runJavaScriptReturningResult('window._skedTimetableResult');
      final dsRawObj = await _controller.runJavaScriptReturningResult("window._skedDatesheetResult || ''");

      final ttRaw = ttRawObj.toString();
      var dsRaw = dsRawObj.toString();

      if (dsRaw.startsWith('"') && dsRaw.endsWith('"')) {
        try {
          final dec = jsonDecode('{"v":$dsRaw}') as Map<String, dynamic>;
          dsRaw = dec['v'] as String? ?? '';
        } catch (_) {}
      }

      final entries = TimetableParser.parse(ttRaw);
      if (entries.isNotEmpty) {
        await TimetableParser.saveToPrefs(entries, widget.userId);
      }

      final parsedExams = ExamParser.parseDatesheetHtml(dsRaw);
      final exams = parsedExams.isNotEmpty ? parsedExams : await ExamParser.loadExamsFromPrefs();
      await ExamParser.saveExamsToPrefs(exams);

      if (mounted) {
        if (entries.isNotEmpty) {
          final examSuffix = exams.isNotEmpty ? ' & ${exams.length} exams' : '';
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(
              content: Text('Synced timetable (${entries.length} classes$examSuffix)'),
              backgroundColor: SkedColors.slab,
            ),
          );
          widget.onSuccess(widget.userId);
        } else {
          setState(() {
            _isSyncing = false;
            _statusText = 'No classes found in timetable';
          });
        }
      }
    } catch (e) {
      if (mounted) {
        setState(() {
          _isSyncing = false;
          _statusText = 'Parse error: $e';
        });
      }
    }
  }

  @override
  void dispose() {
    _pollTimer?.cancel();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: SkedColors.ink,
      body: SafeArea(
        child: Column(
          children: [
            // Header Bar
            Container(
              color: SkedColors.slab,
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
              child: Row(
                children: [
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          'LPU UMS Verification',
                          style: GoogleFonts.barlowCondensed(
                            fontSize: 18,
                            fontWeight: FontWeight.w700,
                            color: SkedColors.chalk,
                          ),
                        ),
                        const SizedBox(height: 2),
                        Text(
                          _statusText,
                          style: TextStyle(
                            fontSize: 12,
                            color: _isSyncing ? SkedColors.blaze : SkedColors.slate,
                          ),
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                        ),
                      ],
                    ),
                  ),
                  Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      IconButton(
                        tooltip: 'Force Submit',
                        icon: const Icon(Icons.play_arrow, color: SkedColors.blaze, size: 22),
                        onPressed: () {
                          _controller.runJavaScript('window.skedForceSubmit && window.skedForceSubmit()');
                        },
                      ),
                      IconButton(
                        tooltip: 'Reload',
                        icon: const Icon(Icons.refresh, color: SkedColors.slate, size: 20),
                        onPressed: () => _controller.reload(),
                      ),
                      IconButton(
                        tooltip: 'Close',
                        icon: const Icon(Icons.close, color: SkedColors.slate, size: 20),
                        onPressed: widget.onDismiss,
                      ),
                    ],
                  ),
                ],
              ),
            ),

            if (_isSyncing)
              const LinearProgressIndicator(
                color: SkedColors.blaze,
                backgroundColor: SkedColors.slab,
                minHeight: 2,
              ),

            // Embedded Webview
            Expanded(
              child: WebViewWidget(controller: _controller),
            ),
          ],
        ),
      ),
    );
  }
}
