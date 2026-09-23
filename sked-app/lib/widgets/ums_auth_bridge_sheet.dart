import 'dart:async';
import 'dart:convert';
import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:webview_flutter/webview_flutter.dart';
import '../models/exam_item.dart';
import '../services/timetable_parser.dart';
import '../services/exam_parser.dart';
import '../theme/app_theme.dart';

class UmsAuthBridgeSheet extends StatefulWidget {
  final String userId;
  final String password;
  final VoidCallback onDismiss;
  final ValueChanged<String> onSuccess;
  final ValueChanged<String>? onError;

  const UmsAuthBridgeSheet({
    super.key,
    required this.userId,
    required this.password,
    required this.onDismiss,
    required this.onSuccess,
    this.onError,
  });

  @override
  State<UmsAuthBridgeSheet> createState() => _UmsAuthBridgeSheetState();
}

class _UmsAuthBridgeSheetState extends State<UmsAuthBridgeSheet>
    with SingleTickerProviderStateMixin {
  late final WebViewController _controller;
  String _statusText = 'Connecting to LPU UMS...';
  bool _isSyncing = false;
  bool _isDone = false;
  bool _showRawWebView = false;
  int _elapsedSeconds = 0;

  Timer? _pollTimer;
  Timer? _secondTimer;

  late final AnimationController _pulseController;
  late final Animation<double> _pulseScale;
  late final Animation<double> _pulseAlpha;

  @override
  void initState() {
    super.initState();

    _pulseController = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 1200),
    )..repeat(reverse: true);

    _pulseScale = Tween<double>(begin: 0.92, end: 1.08).animate(
      CurvedAnimation(parent: _pulseController, curve: Curves.easeInOut),
    );

    _pulseAlpha = Tween<double>(begin: 0.08, end: 0.22).animate(
      CurvedAnimation(parent: _pulseController, curve: Curves.easeInOut),
    );

    _secondTimer = Timer.periodic(const Duration(seconds: 1), (_) {
      if (mounted) setState(() => _elapsedSeconds++);
    });

    _initWebView();
  }

  void _initWebView() {
    _controller = WebViewController()
      ..setJavaScriptMode(JavaScriptMode.unrestricted)
      ..setBackgroundColor(SkedColors.ink)
      ..setNavigationDelegate(
        NavigationDelegate(
          onPageStarted: (url) {
            if (mounted && !_isSyncing) {
              setState(() => _statusText = 'Loading UMS Portal...');
            }
          },
          onPageFinished: (url) {
            if (mounted && !_isSyncing) {
              setState(() => _statusText = 'Verifying security check...');
            }
          },
          onWebResourceError: (error) {
            if (mounted && !_isDone && !_isSyncing) {
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

                var dsUrls = [
                    '/lpuums/frmStudentDateSheet.aspx',
                    '/frmStudentDateSheet.aspx',
                    '/lpuums/frmDateSheet.aspx',
                    '/frmDateSheet.aspx',
                    '/lpuums/frmStudentExamSchedule.aspx',
                    '/frmStudentExamSchedule.aspx',
                    '/lpuums/frmSeatingPlan.aspx',
                    '/frmSeatingPlan.aspx',
                    '/lpuums/frmStudentSeatingPlan.aspx',
                    '/frmStudentSeatingPlan.aspx',
                    '/lpuums/frmExamSeatingPlan.aspx'
                ];
                try {
                    var regex = /href=["']([^"']*(?:datesheet|seating|exam|schedule)[^"']*)["']/gi;
                    var match;
                    while ((match = regex.exec(loginHtml)) !== null) {
                        var u = match[1];
                        if (u && !u.startsWith('javascript:') && !u.startsWith('#') && u.indexOf('openapp.aspx') === -1 && dsUrls.indexOf(u) === -1) {
                            dsUrls.push(u);
                        }
                    }
                    var links = document.querySelectorAll('a[href]');
                    for (var li = 0; li < links.length; li++) {
                        var href = links[li].getAttribute('href') || '';
                        if (href && /(?:datesheet|seating|exam)/i.test(href) && !href.startsWith('javascript:') && !href.startsWith('#') && href.indexOf('openapp.aspx') === -1 && dsUrls.indexOf(href) === -1) {
                            dsUrls.push(href);
                        }
                    }
                } catch(e) {}

                var dsPromise = Promise.all(
                    dsUrls.map(function(u) {
                        return safeFetch(u, u);
                    })
                ).then(function(results) {
                    var combinedHtml = '';
                    for (var i = 0; i < results.length; i++) {
                        var h = results[i].html || '';
                        if (h && (/[A-Z]{2,5}\\d{3,4}/.test(h) || /datesheet|seating|exam/i.test(h))) {
                            combinedHtml += '\\n<!-- PAGE: ' + results[i].name + ' -->\\n' + h;
                        }
                    }
                    window._skedDatesheetResult = combinedHtml;
                }).catch(function() {
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
                }

                // Authentication Failure Detection
                window._skedIsSyncing = false;
                window._skedAuthFailed = true;

                var errM = html.match(/id=["']lockerror["'][^>]*>([^<]+)<\\/span>/i)
                        || html.match(/id=["']lblError["'][^>]*>([^<]+)<\\/span>/i)
                        || html.match(/class=["'][^"']*error[^"']*["'][^>]*>([^<]+)<\\//i);

                var serverMsg = errM ? errM[1].replace(/<[^>]+>/g, '').trim() : '';

                if (serverMsg) {
                    window._skedError = serverMsg;
                } else if (/invalid\\s*(?:user\\s*name\\s*\\/?\\s*)?password/i.test(html) || /wrong\\s*password/i.test(html) || /incorrect/i.test(html)) {
                    window._skedError = 'Invalid Registration No or Password.';
                } else if (/account\\s*is\\s*locked/i.test(html) || /locked/i.test(html)) {
                    window._skedError = 'Account locked. Please reset password on UMS.';
                } else {
                    window._skedError = 'Invalid Registration No or Password.';
                }
            }).catch(function(err) {
                window._skedIsSyncing = false;
                window._skedError = 'Network error: ' + err.toString();
            });
        }

        window.skedForceSubmit = function() {
            window._skedAuthFailed = false;
            window._skedError = '';
            triggerSubmit();
        };

        if (form && !form.__skedBound) {
            form.__skedBound = true;
            form.onsubmit = function(e) {
                if (e) { e.preventDefault(); e.stopPropagation(); }
                window._skedAuthFailed = false;
                window._skedError = '';
                triggerSubmit();
                return false;
            };
            var submitBtn = form.querySelector('input[type="submit"]');
            if (submitBtn) {
                submitBtn.onclick = function(e) {
                    if (e) { e.preventDefault(); e.stopPropagation(); }
                    window._skedAuthFailed = false;
                    window._skedError = '';
                    triggerSubmit();
                    return false;
                };
            }
        }

        if (!window._skedIsSyncing && !window._skedTimetableResult && !window._skedAuthFailed) {
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
            authFailed: !!window._skedAuthFailed,
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
      final authFailed = stateObj['authFailed'] == true;
      final hasRes = stateObj['hasResult'] == true;

      if (mounted) {
        setState(() {
          if (st.isNotEmpty) _statusText = st;
          if (err.isNotEmpty) _statusText = err;
          _isSyncing = syncing;
        });
      }

      if (authFailed) {
        _pollTimer?.cancel();
        _secondTimer?.cancel();
        final errText = err.isNotEmpty ? err : 'Invalid Registration No or Password.';
        if (mounted) {
          setState(() {
            _isSyncing = false;
            _statusText = errText;
          });
        }
        if (widget.onError != null) {
          widget.onError!(errText);
        } else {
          widget.onDismiss();
        }
        return;
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
      final titleMap = {
        for (final e in entries)
          if (e.courseCode.isNotEmpty && e.description.isNotEmpty)
            e.courseCode.toUpperCase(): e.description
      };

      // 1. FAST PATH (Classic UMS): Check if classic UMS HTML already contains exams
      List<ExamItem> examsFound = [];
      if (dsRaw.isNotEmpty) {
        final classic = ExamParser.parseDatesheetHtml(dsRaw, titleMap);
        if (classic.isNotEmpty) {
          examsFound = classic;
        }
      }

      // 2. MODERN PORTAL PATH (1st & 2nd Year): Load via official UMS SSO redirect
      if (examsFound.isEmpty) {
        if (mounted) {
          setState(() => _statusText = 'Syncing Examination Schedule...');
        }

        // Navigate WebView to official openapp.aspx SSO redirect which forwards to studentums.lpu.in with authentic 256-hex token
        await _controller.loadRequest(
          Uri.parse('https://ums.lpu.in/lpuums/openapp.aspx?from=ums&toApp=nextproject&pagename=dashboard/examination/conduct/seatingplan'),
        );

        for (var attempt = 0; attempt < 50; attempt++) {
          await Future.delayed(const Duration(milliseconds: 500));
          if (!mounted) return;

          final elapsedSec = (attempt + 1) ~/ 2;
          if (mounted) {
            setState(() => _statusText = 'Syncing Examination Schedule (${elapsedSec}s)...');
          }

          try {
            // Check if redirected to studentdashboard without seatingplan, redirect in JS
            final redirectCheckJs = '''
(function() {
    try {
        var href = window.location.href;
        if (href.indexOf('studentdashboard') !== -1 && href.indexOf('seatingplan') === -1) {
            window.location.href = 'https://studentums.lpu.in/dashboard/examination/conduct/seatingplan';
        }
        return document.body ? document.body.innerText : '';
    } catch(e) {
        return '';
    }
})()
''';
            final resObj = await _controller.runJavaScriptReturningResult(redirectCheckJs);
            var textRaw = resObj.toString();
            if (textRaw.startsWith('"') && textRaw.endsWith('"')) {
              try {
                final dec = jsonDecode('{"v":$textRaw}') as Map<String, dynamic>;
                textRaw = dec['v'] as String? ?? '';
              } catch (_) {}
            }

            final parsed = ExamParser.parseDatesheetHtml(textRaw, titleMap);
            if (parsed.isNotEmpty) {
              examsFound = parsed;
              break;
            } else if (attempt >= 20 && (
                textRaw.toLowerCase().contains('exam not scheduled') ||
                textRaw.toLowerCase().contains('no record found') ||
                (textRaw.contains('Total Exam 0') && !textRaw.toLowerCase().contains('loading'))
            )) {
              break;
            }
          } catch (_) {}
        }
      }

      if (examsFound.isNotEmpty) {
        await ExamParser.saveExamsToPrefs(examsFound);
      }
      final exams = examsFound;

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
    _secondTimer?.cancel();
    _pulseController.dispose();
    super.dispose();
  }

  String _computeTitle() {
    final s = _statusText.toLowerCase();
    if (s.contains('datesheet') || s.contains('schedule') || s.contains('classes') || s.contains('examination')) {
      return 'SYNCING TIMETABLE';
    }
    if (s.contains('authenticating') || s.contains('submitting') || s.contains('logging')) {
      return 'LOGGING IN';
    }
    if (s.contains('turnstile') || s.contains('verifying') || s.contains('security')) {
      return 'SECURITY CHECK';
    }
    return 'CONNECTING TO UMS';
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: SkedColors.ink,
      body: SafeArea(
        child: Stack(
          children: [
            // 1. Embedded Webview kept active in view hierarchy
            Positioned.fill(
              child: Padding(
                padding: EdgeInsets.only(top: _showRawWebView ? 56 : 0),
                child: WebViewWidget(controller: _controller),
              ),
            ),

            // 2. Foreground UI: Portal Browser Inspection OR SKED Modern Loading Screen
            if (_showRawWebView)
              Positioned(
                top: 0,
                left: 0,
                right: 0,
                child: Container(
                  color: SkedColors.slab,
                  padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
                  child: Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            Text(
                              'LPU UMS BROWSER',
                              style: GoogleFonts.barlowCondensed(
                                fontSize: 17,
                                fontWeight: FontWeight.w700,
                                color: SkedColors.chalk,
                              ),
                            ),
                            Text(
                              _statusText,
                              style: TextStyle(
                                fontSize: 12,
                                color: _statusText.toLowerCase().contains('invalid') || _statusText.toLowerCase().contains('error')
                                    ? SkedColors.dangerRed
                                    : SkedColors.slate,
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
                          TextButton(
                            onPressed: () => setState(() => _showRawWebView = false),
                            child: Text(
                              'HIDE',
                              style: GoogleFonts.barlowCondensed(
                                fontSize: 13,
                                fontWeight: FontWeight.w700,
                                color: SkedColors.blaze,
                              ),
                            ),
                          ),
                          IconButton(
                            icon: const Icon(Icons.refresh, color: SkedColors.slate, size: 20),
                            onPressed: () => _controller.reload(),
                          ),
                          IconButton(
                            icon: const Icon(Icons.close, color: SkedColors.slate, size: 20),
                            onPressed: widget.onDismiss,
                          ),
                        ],
                      ),
                    ],
                  ),
                ),
              )
            else
              Positioned.fill(
                child: Container(
                  color: SkedColors.ink,
                  padding: const EdgeInsets.symmetric(horizontal: 28, vertical: 20),
                  child: Column(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      // Top Bar
                      Row(
                        mainAxisAlignment: MainAxisAlignment.spaceBetween,
                        children: [
                          Text(
                            'SKED.',
                            style: GoogleFonts.barlowCondensed(
                              fontSize: 28,
                              fontWeight: FontWeight.w700,
                              color: SkedColors.blaze,
                              letterSpacing: 1.0,
                            ),
                          ),
                          IconButton(
                            icon: const Icon(Icons.close, color: SkedColors.slate, size: 22),
                            onPressed: widget.onDismiss,
                          ),
                        ],
                      ),

                      // Center Content
                      Column(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          // Pulsing Logo Indicator
                          AnimatedBuilder(
                            animation: _pulseController,
                            builder: (context, child) {
                              return SizedBox(
                                width: 110,
                                height: 110,
                                child: Stack(
                                  alignment: Alignment.center,
                                  children: [
                                    Transform.scale(
                                      scale: _pulseScale.value,
                                      child: Container(
                                        width: 110,
                                        height: 110,
                                        decoration: BoxDecoration(
                                          shape: BoxShape.circle,
                                          color: SkedColors.blaze.withOpacity(_pulseAlpha.value),
                                        ),
                                      ),
                                    ),
                                    Container(
                                      width: 80,
                                      height: 80,
                                      decoration: BoxDecoration(
                                        shape: BoxShape.circle,
                                        color: SkedColors.slab,
                                        border: Border.all(color: SkedColors.rule),
                                      ),
                                    ),
                                    const SizedBox(
                                      width: 56,
                                      height: 56,
                                      child: CircularProgressIndicator(
                                        color: SkedColors.blaze,
                                        strokeWidth: 3,
                                      ),
                                    ),
                                    Icon(
                                      _isSyncing ? Icons.sync : Icons.lock,
                                      color: SkedColors.blaze,
                                      size: 24,
                                    ),
                                  ],
                                ),
                              );
                            },
                          ),

                          const SizedBox(height: 28),

                          // Dynamic Step Title
                          Text(
                            _computeTitle(),
                            style: GoogleFonts.barlowCondensed(
                              fontSize: 22,
                              fontWeight: FontWeight.w700,
                              color: SkedColors.chalk,
                              letterSpacing: 1.0,
                            ),
                          ),

                          const SizedBox(height: 8),

                          // Status text
                          Padding(
                            padding: const EdgeInsets.symmetric(horizontal: 20),
                            child: Text(
                              _statusText,
                              style: const TextStyle(
                                fontSize: 13,
                                color: SkedColors.slate,
                              ),
                              textAlign: TextAlign.center,
                            ),
                          ),

                          const SizedBox(height: 28),

                          // Steps Milestone Checklist
                          Container(
                            width: double.infinity,
                            padding: const EdgeInsets.all(16),
                            decoration: BoxDecoration(
                              color: SkedColors.slab,
                              borderRadius: BorderRadius.circular(6),
                              border: Border.all(color: SkedColors.rule),
                            ),
                            child: Column(
                              children: [
                                _buildSyncStepRow(
                                  title: 'Connect to LPU Portal',
                                  isDone: true,
                                  isActive: false,
                                ),
                                const SizedBox(height: 10),
                                _buildSyncStepRow(
                                  title: 'Cloudflare Security Check',
                                  isDone: _isSyncing || _statusText.toLowerCase().contains('verified') || _statusText.toLowerCase().contains('authenticating') || _statusText.toLowerCase().contains('schedule'),
                                  isActive: !_isSyncing && (_statusText.toLowerCase().contains('turnstile') || _statusText.toLowerCase().contains('loading') || _statusText.toLowerCase().contains('security') || _statusText.toLowerCase().contains('connecting')),
                                ),
                                const SizedBox(height: 10),
                                _buildSyncStepRow(
                                  title: 'Sync Schedule & Datesheet',
                                  isDone: false,
                                  isActive: _isSyncing,
                                ),
                              ],
                            ),
                          ),

                          // Humorous & Relatable UMS Speed Indicator Pill
                          if (_elapsedSeconds >= 4) ...[
                            const SizedBox(height: 16),
                            Container(
                              padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 6),
                              decoration: BoxDecoration(
                                color: SkedColors.slab,
                                borderRadius: BorderRadius.circular(20),
                                border: Border.all(color: SkedColors.blaze.withOpacity(0.35)),
                              ),
                              child: Text(
                                _elapsedSeconds >= 14
                                    ? 'UMS is dying right now 😭 hang tight...'
                                    : _elapsedSeconds >= 9
                                        ? 'UMS is painfully slow today 😭'
                                        : 'UMS is too slow 😭',
                                style: GoogleFonts.barlowCondensed(
                                  fontSize: 12,
                                  fontWeight: FontWeight.w600,
                                  color: SkedColors.blaze,
                                  letterSpacing: 0.5,
                                ),
                              ),
                            ),
                          ],
                        ],
                      ),

                      // Bottom Action Buttons
                      Column(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          if (_elapsedSeconds >= 8)
                            TextButton(
                              onPressed: () => setState(() => _showRawWebView = true),
                              child: const Text(
                                'Taking longer than usual? View Portal',
                                style: TextStyle(
                                  color: SkedColors.blaze,
                                  fontSize: 12,
                                  fontWeight: FontWeight.w600,
                                ),
                              ),
                            )
                          else
                            TextButton(
                              onPressed: () => setState(() => _showRawWebView = true),
                              child: Text(
                                'VIEW BROWSER DETAILS',
                                style: TextStyle(
                                  color: SkedColors.slate.withOpacity(0.5),
                                  fontSize: 11,
                                  letterSpacing: 1.0,
                                ),
                              ),
                            ),
                          TextButton(
                            onPressed: widget.onDismiss,
                            child: const Text(
                              'CANCEL',
                              style: TextStyle(
                                color: SkedColors.slate,
                                fontSize: 12,
                                fontWeight: FontWeight.bold,
                                letterSpacing: 1.0,
                              ),
                            ),
                          ),
                        ],
                      ),
                    ],
                  ),
                ),
              ),
          ],
        ),
      ),
    );
  }

  Widget _buildSyncStepRow({
    required String title,
    required bool isDone,
    required bool isActive,
  }) {
    return Row(
      children: [
        if (isDone)
          const Icon(Icons.check_circle, size: 16, color: SkedColors.blaze)
        else if (isActive)
          const SizedBox(
            width: 14,
            height: 14,
            child: CircularProgressIndicator(
              color: SkedColors.blaze,
              strokeWidth: 2,
            ),
          )
        else
          Container(
            width: 14,
            height: 14,
            decoration: BoxDecoration(
              shape: BoxShape.circle,
              border: Border.all(color: SkedColors.slate.withOpacity(0.4), width: 1.5),
            ),
          ),
        const SizedBox(width: 10),
        Text(
          title,
          style: GoogleFonts.barlowCondensed(
            fontSize: 14,
            fontWeight: isActive || isDone ? FontWeight.w600 : FontWeight.w500,
            color: isDone || isActive ? SkedColors.chalk : SkedColors.slate,
            letterSpacing: 0.3,
          ),
        ),
      ],
    );
  }
}
