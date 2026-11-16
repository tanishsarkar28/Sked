import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:url_launcher/url_launcher.dart';
import '../models/class_entry.dart';
import '../models/exam_item.dart';
import '../models/leave_models.dart';
import '../services/timetable_parser.dart';
import '../services/exam_parser.dart';
import '../services/leave_storage.dart';
import '../services/leave_submit_engine.dart';
import '../theme/app_theme.dart';
import '../widgets/class_card.dart';
import '../widgets/about_developer_dialog.dart';
import '../widgets/ums_auth_bridge_sheet.dart';
import '../services/update_service.dart';
import '../services/widget_service.dart';
import '../services/sync_service.dart';
import 'exam_screen.dart';
import 'leave_screen.dart';
import 'leave_slip_screen.dart';

class DashboardScreen extends StatefulWidget {
  final String userId;
  final VoidCallback onLogout;

  const DashboardScreen({
    super.key,
    required this.userId,
    required this.onLogout,
  });

  @override
  State<DashboardScreen> createState() => _DashboardScreenState();
}

class _DashboardScreenState extends State<DashboardScreen>
    with SingleTickerProviderStateMixin, WidgetsBindingObserver {
  final List<String> _days = const [
    'Monday',
    'Tuesday',
    'Wednesday',
    'Thursday',
    'Friday',
    'Saturday'
  ];

  String _activeTab = 'HOME'; // 'HOME' | 'CLASSES' | 'EXAMS' | 'LEAVE' | 'LEAVE_SLIP'
  late String _selectedDay;
  late String _todayName;

  List<ClassEntry> _todayClasses = [];
  Map<String, List<ClassEntry>> _weekClasses = {};
  List<ExamItem> _exams = [];

  LeaveBalance _leaveBalance = const LeaveBalance();
  HostelGatePass _gatePass = const HostelGatePass();
  List<LeaveRecord> _leaveRecords = [];

  bool _isLoading = false;
  bool _showWebViewBridge = false;
  String _resyncPassword = '';
  int _lastSyncTimestamp = 0;

  String _formatLastSyncTime() {
    if (_lastSyncTimestamp <= 0) return '';
    final now = DateTime.now();
    final syncDate = DateTime.fromMillisecondsSinceEpoch(_lastSyncTimestamp);
    final diff = now.difference(syncDate);
    final hour = syncDate.hour % 12 == 0 ? 12 : syncDate.hour % 12;
    final minute = syncDate.minute.toString().padLeft(2, '0');
    final period = syncDate.hour >= 12 ? 'PM' : 'AM';
    final timeStr = '$hour:$minute $period';

    if (diff.inMinutes < 1) {
      return 'Synced just now';
    } else if (diff.inMinutes < 60) {
      return 'Synced ${diff.inMinutes}m ago';
    } else if (diff.inHours < 24 && now.day == syncDate.day) {
      return 'Synced $timeStr';
    } else {
      const months = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
      return 'Synced ${months[syncDate.month - 1]} ${syncDate.day}, $timeStr';
    }
  }

  late final AnimationController _refreshAnimCtrl;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _todayName = TimetableParser.todayName();
    _selectedDay = _days.contains(_todayName) ? _todayName : 'Monday';

    _refreshAnimCtrl = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 900),
    );

    _loadLocalData();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      _checkAppUpdate();
      _runAutoSyncCheck();
    });
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) {
      _runAutoSyncCheck();
      _loadLocalData();
    }
  }

  Future<void> _runAutoSyncCheck({bool force = false}) async {
    final event = await SyncService.performSync(force: force);
    if (!mounted) return;

    final prefs = await SharedPreferences.getInstance();
    final updatedTs = prefs.getInt(SyncService.keyLastSyncTimestamp) ?? 0;
    setState(() {
      _lastSyncTimestamp = updatedTs;
    });

    if (event.type == SyncResultType.success) {
      if (event.newMakeupClasses.isNotEmpty || event.cancelledMakeupDescriptions.isNotEmpty) {
        await _loadLocalData();
      }

      if (event.newMakeupClasses.isNotEmpty) {
        final count = event.newMakeupClasses.length;
        final title = count == 1
            ? '🔔 New Makeup Class: ${event.newMakeupClasses.first.courseCode} (${event.newMakeupClasses.first.day})'
            : '🔔 $count New Makeup Classes Detected!';

        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            backgroundColor: SkedColors.blaze,
            content: Text(
              title,
              style: GoogleFonts.barlowCondensed(
                fontSize: 15,
                fontWeight: FontWeight.w700,
                color: Colors.white,
                letterSpacing: 0.3,
              ),
            ),
            duration: const Duration(seconds: 5),
          ),
        );
      } else if (event.cancelledMakeupDescriptions.isNotEmpty) {
        final desc = event.cancelledMakeupDescriptions.join(', ');
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            backgroundColor: SkedColors.slabElevated,
            content: Text(
              '⚠️ Makeup class cancelled: $desc',
              style: GoogleFonts.barlowCondensed(
                fontSize: 14,
                fontWeight: FontWeight.w600,
                color: SkedColors.chalk,
              ),
            ),
          ),
        );
      }
    } else if (event.type == SyncResultType.authExpired && force) {
      _triggerReSync();
    }
  }

  Future<void> _checkAppUpdate() async {
    try {
      final update = await UpdateService.checkForUpdate();
      if (update != null && mounted) {
        final shouldNotify = await UpdateService.shouldNotifyUpdate(update.versionCode);
        if (shouldNotify) {
          await UpdateService.markUpdateNotified(update.versionCode);
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(
              backgroundColor: SkedColors.blaze,
              content: Row(
                children: [
                  const Icon(Icons.system_update_alt, color: Colors.white, size: 20),
                  const SizedBox(width: 10),
                  Expanded(
                    child: Text(
                      '🚀 Sked v${update.versionName} is available! Tap to install.',
                      style: GoogleFonts.barlowCondensed(
                        fontSize: 15,
                        fontWeight: FontWeight.w700,
                        color: Colors.white,
                      ),
                    ),
                  ),
                ],
              ),
              action: SnackBarAction(
                label: 'INSTALL',
                textColor: Colors.white,
                onPressed: () => UpdateService.showUpdateDialog(context, update),
              ),
              duration: const Duration(seconds: 10),
            ),
          );
          UpdateService.showUpdateDialog(context, update);
        }
      }
    } catch (e) {
      debugPrint('_checkAppUpdate error: $e');
    }
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _refreshAnimCtrl.dispose();
    super.dispose();
  }

  Future<void> _loadLocalData() async {
    setState(() => _isLoading = true);
    _refreshAnimCtrl.repeat();

    try {
      final allEntries = await TimetableParser.loadFromPrefs();
      _todayName = TimetableParser.todayName();
      final todayEntries = TimetableParser.filterByDay(allEntries, _todayName);
      final weekMap = TimetableParser.groupByDay(allEntries);
      final realExams = await ExamParser.loadExamsFromPrefs();
      final prefs = await SharedPreferences.getInstance();
      final lastSyncTs = prefs.getInt(SyncService.keyLastSyncTimestamp) ?? 0;

      final bal = await LeaveStorage.loadBalance();
      final pass = await LeaveStorage.loadGatePass();
      final recs = await LeaveStorage.loadRecords();

      if (mounted) {
        setState(() {
          _todayClasses = todayEntries;
          _weekClasses = weekMap;
          _exams = realExams;
          _leaveBalance = bal;
          _gatePass = pass;
          _leaveRecords = recs;
          _lastSyncTimestamp = lastSyncTs;
          if (!_days.contains(_selectedDay)) {
            _selectedDay = _days.contains(_todayName) ? _todayName : 'Monday';
          }
        });

        final todayExam = realExams.where((e) => e.getStatus() == ExamStatus.today).firstOrNull;
        final tomorrowExam = realExams.where((e) => e.getStatus() == ExamStatus.tomorrow).firstOrNull;
        WidgetService.updateWidgetData(
          todayEntries: todayEntries,
          isSunday: _todayName == 'Sunday',
          todayExam: todayExam,
          tomorrowExam: tomorrowExam,
        );
      }
    } catch (e) {
      debugPrint('_loadLocalData error: $e');
    } finally {
      if (mounted) {
        setState(() => _isLoading = false);
        _refreshAnimCtrl.reset();
      }
    }
  }

  Future<void> _triggerReSync() async {
    setState(() => _isLoading = true);
    _refreshAnimCtrl.repeat();

    try {
      final event = await SyncService.performSync(force: true);

      if (mounted) {
        setState(() => _isLoading = false);
        _refreshAnimCtrl.reset();
      }

      if (event.type == SyncResultType.success) {
        await _loadLocalData();
        if (mounted) {
          final msg = event.newMakeupClasses.isNotEmpty
              ? 'Synced: ${event.newMakeupClasses.length} new makeup classes found!'
              : 'Timetable up to date.';
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(
              backgroundColor: SkedColors.blaze,
              content: Text(
                msg,
                style: GoogleFonts.barlowCondensed(
                  fontSize: 14,
                  fontWeight: FontWeight.w600,
                  color: Colors.white,
                ),
              ),
              duration: const Duration(seconds: 2),
            ),
          );
        }
      } else if (event.type == SyncResultType.authExpired || event.type == SyncResultType.serverError) {
        if (mounted) {
          _showFullReAuthDialog();
        }
      }
    } catch (e) {
      if (mounted) {
        setState(() => _isLoading = false);
        _refreshAnimCtrl.reset();
      }
    }
  }

  Future<void> _showFullReAuthDialog({String? prefill, String? errorMsg}) async {
    final prefs = await SharedPreferences.getInstance();
    final savedPwd = prefs.getString('saved_ums_pwd') ?? '';

    if (savedPwd.isNotEmpty && errorMsg == null) {
      setState(() {
        _resyncPassword = savedPwd;
        _showWebViewBridge = true;
      });
      return;
    }

    final initial = prefill ?? savedPwd;
    final pwdCtrl = TextEditingController(text: initial);
    var passVisible = false;

    if (!mounted) return;
    showDialog(
      context: context,
      builder: (ctx) => StatefulBuilder(
        builder: (context, setDlgState) => AlertDialog(
          backgroundColor: SkedColors.slab,
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
          title: Row(
            children: [
              const Icon(Icons.sync, color: SkedColors.blaze, size: 20),
              const SizedBox(width: 8),
              Text(
                'Re-Sync Timetable',
                style: GoogleFonts.barlowCondensed(
                  fontSize: 18,
                  fontWeight: FontWeight.w700,
                  color: SkedColors.chalk,
                ),
              ),
            ],
          ),
          content: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              if (errorMsg != null && errorMsg.isNotEmpty) ...[
                Container(
                  width: double.infinity,
                  padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
                  decoration: BoxDecoration(
                    color: SkedColors.dangerRed.withOpacity(0.12),
                    borderRadius: BorderRadius.circular(6),
                    border: Border.all(color: SkedColors.dangerRed.withOpacity(0.35)),
                  ),
                  child: Text(
                    errorMsg,
                    style: const TextStyle(
                      fontSize: 12,
                      fontWeight: FontWeight.w600,
                      color: Color(0xFFF87171),
                    ),
                  ),
                ),
                const SizedBox(height: 10),
              ],
              Text(
                'Enter your UMS password to refresh your latest schedule for ${widget.userId}:',
                style: const TextStyle(fontSize: 13, color: SkedColors.slate),
              ),
              const SizedBox(height: 14),
              TextField(
                controller: pwdCtrl,
                obscureText: !passVisible,
                style: const TextStyle(color: SkedColors.chalk, fontSize: 14),
                decoration: InputDecoration(
                  labelText: 'UMS Password',
                  prefixIcon: const Icon(Icons.lock_outline, color: SkedColors.slate, size: 20),
                  suffixIcon: IconButton(
                    icon: Icon(
                      passVisible ? Icons.visibility_outlined : Icons.visibility_off_outlined,
                      color: SkedColors.slate,
                      size: 20,
                    ),
                    onPressed: () => setDlgState(() => passVisible = !passVisible),
                  ),
                ),
              ),
            ],
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(ctx),
              child: Text(
                'Cancel',
                style: GoogleFonts.barlowCondensed(fontSize: 14, color: SkedColors.slate),
              ),
            ),
            ElevatedButton(
              onPressed: () {
                if (pwdCtrl.text.trim().isNotEmpty) {
                  final entered = pwdCtrl.text.trim();
                  prefs.setString('saved_ums_pwd', entered);
                  Navigator.pop(ctx);
                  setState(() {
                    _resyncPassword = entered;
                    _showWebViewBridge = true;
                  });
                }
              },
              child: const Text('Sync Now'),
            ),
          ],
        ),
      ),
    );
  }

  void _showShareSheet(BuildContext context) {
    showModalBottomSheet(
      context: context,
      backgroundColor: SkedColors.slab,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(16)),
      ),
      builder: (ctx) {
        return SafeArea(
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 16),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Center(
                  child: Container(
                    width: 36,
                    height: 4,
                    decoration: BoxDecoration(
                      color: SkedColors.rule,
                      borderRadius: BorderRadius.circular(2),
                    ),
                  ),
                ),
                const SizedBox(height: 16),
                Text(
                  'SHARE SKED',
                  style: GoogleFonts.barlowCondensed(
                    fontSize: 18,
                    fontWeight: FontWeight.w700,
                    color: SkedColors.chalk,
                    letterSpacing: 0.5,
                  ),
                ),
                const SizedBox(height: 6),
                const Text(
                  'Share the official download link with your classmates & friends:',
                  style: TextStyle(fontSize: 12, color: SkedColors.slate),
                ),
                const SizedBox(height: 16),
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
                  decoration: BoxDecoration(
                    color: SkedColors.ink,
                    borderRadius: BorderRadius.circular(6),
                    border: Border.all(color: SkedColors.rule),
                  ),
                  child: Row(
                    children: [
                      const Icon(Icons.link, size: 18, color: SkedColors.blaze),
                      const SizedBox(width: 10),
                      const Expanded(
                        child: Text(
                          'https://sked-gold.vercel.app/',
                          style: TextStyle(
                            fontFamily: 'monospace',
                            fontSize: 12,
                            color: SkedColors.chalk,
                          ),
                          overflow: TextOverflow.ellipsis,
                        ),
                      ),
                      InkWell(
                        onTap: () {
                          Clipboard.setData(
                            const ClipboardData(text: 'https://sked-gold.vercel.app/'),
                          );
                          Navigator.of(ctx).pop();
                          ScaffoldMessenger.of(context).showSnackBar(
                            const SnackBar(
                              content: Text('Website link copied to clipboard!'),
                              backgroundColor: SkedColors.slabElevated,
                              duration: Duration(seconds: 2),
                            ),
                          );
                        },
                        borderRadius: BorderRadius.circular(4),
                        child: Container(
                          padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
                          decoration: BoxDecoration(
                            color: SkedColors.blaze,
                            borderRadius: BorderRadius.circular(4),
                          ),
                          child: Text(
                            'COPY',
                            style: GoogleFonts.barlowCondensed(
                              fontSize: 12,
                              fontWeight: FontWeight.w700,
                              color: SkedColors.ink,
                            ),
                          ),
                        ),
                      ),
                    ],
                  ),
                ),
                const SizedBox(height: 14),
                InkWell(
                  onTap: () async {
                    Navigator.of(ctx).pop();
                    final url = Uri.parse(
                      'https://api.whatsapp.com/send?text=${Uri.encodeComponent('Track your LPU timetable, exams & seating plan with Sked: https://sked-gold.vercel.app/')}',
                    );
                    try {
                      if (await canLaunchUrl(url)) {
                        await launchUrl(url, mode: LaunchMode.externalApplication);
                      }
                    } catch (_) {}
                  },
                  borderRadius: BorderRadius.circular(6),
                  child: Container(
                    padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
                    decoration: BoxDecoration(
                      color: SkedColors.ink,
                      borderRadius: BorderRadius.circular(6),
                      border: Border.all(color: SkedColors.rule),
                    ),
                    child: Row(
                      children: [
                        const Icon(Icons.chat_bubble_outline, color: SkedColors.onGoingGreen, size: 20),
                        const SizedBox(width: 12),
                        Text(
                          'Share via WhatsApp',
                          style: GoogleFonts.barlowCondensed(
                            fontSize: 14,
                            fontWeight: FontWeight.w600,
                            color: SkedColors.chalk,
                          ),
                        ),
                        const Spacer(),
                        const Icon(Icons.arrow_forward_ios, color: SkedColors.slate, size: 12),
                      ],
                    ),
                  ),
                ),
              ],
            ),
          ),
        );
      },
    );
  }

  void _showLogoutDialog() {
    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        backgroundColor: SkedColors.slab,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
        title: Text(
          'Log Out from Sked?',
          style: GoogleFonts.barlowCondensed(
            fontSize: 20,
            fontWeight: FontWeight.w700,
            color: SkedColors.chalk,
          ),
          textAlign: TextAlign.center,
        ),
        content: const Text(
          'This will remove your saved credentials and timetable from this device. You can log back in anytime.',
          style: TextStyle(fontSize: 13, color: SkedColors.slate, height: 1.4),
          textAlign: TextAlign.center,
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx),
            child: Text(
              'Cancel',
              style: GoogleFonts.barlowCondensed(
                fontSize: 14,
                fontWeight: FontWeight.w700,
                color: SkedColors.slate,
              ),
            ),
          ),
          ElevatedButton(
            style: ElevatedButton.styleFrom(
              backgroundColor: SkedColors.dangerRed,
              foregroundColor: Colors.white,
            ),
            onPressed: () async {
              Navigator.pop(ctx);
              final prefs = await SharedPreferences.getInstance();
              await prefs.clear();
              await ExamParser.clearExams();
              widget.onLogout();
            },
            child: const Text('Log Out'),
          ),
        ],
      ),
    );
  }

  String _formattedCurrentDate() {
    final dt = DateTime.now();
    const weekdays = ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday', 'Sunday'];
    const months = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
    final wd = weekdays[dt.weekday - 1];
    final mo = months[dt.month - 1];
    return '$wd • $mo ${dt.day}';
  }

  String? _getLeaveSlipBadge() {
    if (_gatePass.hasActivePass) {
      if (_gatePass.checkOutTime.isNotEmpty && _gatePass.checkInTime.isEmpty) {
        return 'OUT';
      }
      return 'PASS';
    }
    final recentApproved = _leaveRecords.cast<LeaveRecord?>().firstWhere(
      (r) => r != null && r.status.toLowerCase().contains('approved') && r.checkIn.isEmpty,
      orElse: () => null,
    );
    if (recentApproved != null) return 'PASS';
    final recentPending = _leaveRecords.cast<LeaveRecord?>().firstWhere(
      (r) => r != null && r.status.toLowerCase().contains('pending'),
      orElse: () => null,
    );
    if (recentPending != null) return 'APPLIED';
    return null;
  }

  @override
  Widget build(BuildContext context) {
    if (_showWebViewBridge) {
      return UmsAuthBridgeSheet(
        userId: widget.userId,
        password: _resyncPassword,
        onDismiss: () => setState(() => _showWebViewBridge = false),
        onError: (errMsg) async {
          setState(() => _showWebViewBridge = false);
          final isAuthError = errMsg.toLowerCase().contains('password') ||
              errMsg.toLowerCase().contains('invalid') ||
              errMsg.toLowerCase().contains('locked') ||
              errMsg.toLowerCase().contains('credential');
          if (isAuthError) {
            final prefs = await SharedPreferences.getInstance();
            await prefs.remove('saved_ums_pwd');
            if (mounted) {
              _showFullReAuthDialog(prefill: _resyncPassword, errorMsg: errMsg);
            }
          } else {
            ScaffoldMessenger.of(context).showSnackBar(
              SnackBar(
                content: Text(errMsg),
                backgroundColor: SkedColors.dangerRed,
              ),
            );
          }
        },
        onSuccess: (_) {
          setState(() => _showWebViewBridge = false);
          _loadLocalData();
        },
      );
    }

    final isSunday = _todayName.toLowerCase() == 'sunday';

    return WillPopScope(
      onWillPop: () async {
        if (_activeTab != 'HOME') {
          setState(() => _activeTab = 'HOME');
          return false;
        }
        return true;
      },
      child: Scaffold(
        backgroundColor: SkedColors.ink,
        appBar: AppBar(
          backgroundColor: SkedColors.ink,
          leading: _activeTab != 'HOME'
              ? IconButton(
                  icon: const Icon(Icons.arrow_back, color: SkedColors.chalk),
                  onPressed: () => setState(() => _activeTab = 'HOME'),
                )
              : null,
          title: Row(
            children: [
              Text(
                _activeTab == 'HOME' ? 'SKED.' : 'SKED',
                style: GoogleFonts.barlowCondensed(
                  fontSize: 24,
                  fontWeight: FontWeight.w700,
                  color: SkedColors.blaze,
                  letterSpacing: 0.5,
                ),
              ),
              if (_activeTab != 'HOME') ...[
                const SizedBox(width: 8),
                Text(
                  '• ' +
                      (_activeTab == 'CLASSES'
                          ? 'TIMETABLE'
                          : _activeTab == 'EXAMS'
                              ? 'EXAMS'
                              : _activeTab == 'LEAVE'
                                  ? 'HOSTEL LEAVE'
                                  : 'GATE PASS'),
                  style: GoogleFonts.barlowCondensed(
                    fontSize: 16,
                    fontWeight: FontWeight.w700,
                    color: SkedColors.slate,
                  ),
                ),
              ],
            ],
          ),
          actions: [
            IconButton(
              tooltip: 'Share Sked',
              icon: const Icon(Icons.share_outlined, color: SkedColors.slate, size: 20),
              onPressed: () => _showShareSheet(context),
            ),
            IconButton(
              tooltip: 'About Developer',
              icon: const Icon(Icons.info_outline, color: SkedColors.slate, size: 20),
              onPressed: () {
                showDialog(
                  context: context,
                  builder: (_) => const AboutDeveloperDialog(),
                );
              },
            ),
            IconButton(
              tooltip: 'Log Out',
              icon: const Icon(Icons.logout, color: SkedColors.dangerRed, size: 20),
              onPressed: _showLogoutDialog,
            ),
          ],
        ),
        body: _buildCurrentBody(isSunday),
      ),
    );
  }

  Widget _buildCurrentBody(bool isSunday) {
    switch (_activeTab) {
      case 'CLASSES':
        return _buildClassesTab(isSunday);
      case 'EXAMS':
        return ExamScreen(exams: _exams, onRefresh: _showFullReAuthDialog);
      case 'LEAVE':
        return LeaveScreen(
          onBack: () => setState(() => _activeTab = 'HOME'),
          onReSync: _triggerReSync,
        );
      case 'LEAVE_SLIP':
        return LeaveSlipScreen(
          onBack: () => setState(() => _activeTab = 'HOME'),
          onApplyLeave: () => setState(() => _activeTab = 'LEAVE'),
          onReSync: _triggerReSync,
        );
      case 'HOME':
      default:
        return _buildHomeDashboard(isSunday);
    }
  }

  Widget _buildHomeDashboard(bool isSunday) {
    final displayCount = _todayClasses.length;

    return ListView(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
      children: [
        // ── 1. Header Row (Reg No, Date, Re-sync) ───────────────────────────
        Row(
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: [
            Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    Text(
                      'REG: ${widget.userId}',
                      style: const TextStyle(
                        fontFamily: 'monospace',
                        fontSize: 12,
                        fontWeight: FontWeight.w700,
                        color: SkedColors.slate,
                      ),
                    ),
                    const SizedBox(width: 10),
                    Text(
                      _formattedCurrentDate(),
                      style: const TextStyle(
                        fontSize: 12,
                        color: SkedColors.slate,
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 3),
                Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Text(
                      isSunday
                          ? 'Sunday chill mode.'
                          : (displayCount > 0
                              ? '$displayCount classes today.'
                              : 'Nothing today.'),
                      style: GoogleFonts.barlowCondensed(
                        fontSize: 16,
                        fontWeight: FontWeight.w700,
                        color: SkedColors.chalk,
                      ),
                    ),
                  ],
                ),
              ],
            ),

            // RE-SYNC button
            Column(
              crossAxisAlignment: CrossAxisAlignment.end,
              mainAxisSize: MainAxisSize.min,
              children: [
                OutlinedButton.icon(
                  onPressed: _isLoading ? null : () => _triggerReSync(),
                  style: OutlinedButton.styleFrom(
                    shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(4)),
                    side: BorderSide(color: _isLoading ? SkedColors.blaze : SkedColors.rule),
                    padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
                  ),
                  icon: RotationTransition(
                    turns: _refreshAnimCtrl,
                    child: const Icon(Icons.sync, size: 14, color: SkedColors.blaze),
                  ),
                  label: Text(
                    _isLoading ? 'SYNCING...' : 'RE-SYNC',
                    style: GoogleFonts.barlowCondensed(
                      fontSize: 11,
                      fontWeight: FontWeight.w700,
                      color: _isLoading ? SkedColors.blaze : SkedColors.chalk,
                    ),
                  ),
                ),
                if (_formatLastSyncTime().isNotEmpty) ...[
                  const SizedBox(height: 2),
                  Text(
                    _formatLastSyncTime(),
                    style: const TextStyle(
                      fontSize: 10,
                      fontFamily: 'monospace',
                      color: SkedColors.slate,
                    ),
                  ),
                ],
              ],
            ),
          ],
        ),
        const SizedBox(height: 12),
        const Divider(color: SkedColors.rule, height: 1),
        const SizedBox(height: 16),

        // ── 2. Today's Classes Highlights ────────────────────────────────────
        if (_todayClasses.isNotEmpty && !isSunday) ...[
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text(
                "TODAY'S CLASSES",
                style: GoogleFonts.barlowCondensed(
                  fontSize: 13,
                  fontWeight: FontWeight.w700,
                  color: SkedColors.slate,
                  letterSpacing: 0.8,
                ),
              ),
              InkWell(
                onTap: () => setState(() {
                  _selectedDay = _todayName;
                  _activeTab = 'CLASSES';
                }),
                child: Text(
                  'VIEW ALL >',
                  style: GoogleFonts.barlowCondensed(
                    fontSize: 12,
                    fontWeight: FontWeight.w700,
                    color: SkedColors.blaze,
                    letterSpacing: 0.5,
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: 10),
          ..._todayClasses.take(2).map((item) {
            return Padding(
              padding: const EdgeInsets.only(bottom: 8),
              child: ClassCard(
                item: item,
                timingState: ClassTimingState.upcoming,
                onClick: () => setState(() {
                  _selectedDay = _todayName;
                  _activeTab = 'CLASSES';
                }),
              ),
            );
          }),
          const SizedBox(height: 14),
        ],

        // ── 3. Quick Access 2x2 Grid ──────────────────────────────────────────
        Text(
          'QUICK ACCESS',
          style: GoogleFonts.barlowCondensed(
            fontSize: 13,
            fontWeight: FontWeight.w700,
            color: SkedColors.slate,
            letterSpacing: 0.8,
          ),
        ),
        const SizedBox(height: 10),

        // 2 X 2 Grid: Row 1 [Timetable] [Exams]
        Row(
          children: [
            Expanded(
              child: QuickAccessFeatureBox(
                title: 'Timetable',
                icon: Icons.calendar_today,
                badgeText: _todayClasses.isNotEmpty ? '${_todayClasses.length}' : null,
                onClick: () {
                  setState(() {
                    _selectedDay = _days.contains(_todayName) ? _todayName : 'Monday';
                    _activeTab = 'CLASSES';
                  });
                },
              ),
            ),
            const SizedBox(width: 10),
            Expanded(
              child: QuickAccessFeatureBox(
                title: 'Exams',
                icon: Icons.school,
                badgeText: _exams.isNotEmpty ? '${_exams.length}' : null,
                onClick: () => setState(() => _activeTab = 'EXAMS'),
              ),
            ),
          ],
        ),
        const SizedBox(height: 10),

        // Row 2 [Leave Application] [Leave Slip]
        Row(
          children: [
            Expanded(
              child: QuickAccessFeatureBox(
                title: 'Leave Application',
                icon: Icons.exit_to_app,
                badgeText: _leaveBalance.dayBalance > 0 ? '${_leaveBalance.dayBalance}' : null,
                onClick: () => setState(() => _activeTab = 'LEAVE'),
              ),
            ),
            const SizedBox(width: 10),
            Expanded(
              child: QuickAccessFeatureBox(
                title: 'Leave Slip',
                icon: Icons.confirmation_number_outlined,
                badgeText: _getLeaveSlipBadge(),
                onClick: () => setState(() => _activeTab = 'LEAVE_SLIP'),
              ),
            ),
          ],
        ),

        const SizedBox(height: 20),

        // ── 4. Developer Footer Card & Bug Report ─────────────────────────────
        _developerFooterCard(),
        const SizedBox(height: 8),
        _bugReportFooterCard(),
        const SizedBox(height: 24),
      ],
    );
  }

  Widget _developerFooterCard() {
    return Container(
      decoration: BoxDecoration(
        color: const Color(0xFF101010),
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: SkedColors.rule),
      ),
      child: Material(
        color: Colors.transparent,
        child: InkWell(
          onTap: () {
            showDialog(
              context: context,
              builder: (_) => const AboutDeveloperDialog(),
            );
          },
          borderRadius: BorderRadius.circular(8),
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
            child: Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Row(
                  children: [
                    Container(
                      width: 24,
                      height: 24,
                      decoration: BoxDecoration(
                        color: SkedColors.blaze.withOpacity(0.15),
                        shape: BoxShape.circle,
                        border: Border.all(color: SkedColors.blaze.withOpacity(0.3)),
                      ),
                      alignment: Alignment.center,
                      child: Text(
                        'TS',
                        style: GoogleFonts.barlowCondensed(
                          fontSize: 11,
                          fontWeight: FontWeight.w700,
                          color: SkedColors.blaze,
                        ),
                      ),
                    ),
                    const SizedBox(width: 10),
                    Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          'BUILT BY TANISH SARKAR',
                          style: GoogleFonts.barlowCondensed(
                            fontSize: 13,
                            fontWeight: FontWeight.w700,
                            letterSpacing: 0.5,
                            color: SkedColors.chalk,
                          ),
                        ),
                        const Text(
                          'Tap to view developer profiles',
                          style: TextStyle(fontSize: 10, color: SkedColors.slate),
                        ),
                      ],
                    ),
                  ],
                ),
                const Icon(Icons.arrow_forward_ios, size: 12, color: SkedColors.slate),
              ],
            ),
          ),
        ),
      ),
    );
  }

  Widget _bugReportFooterCard() {
    return Center(
      child: InkWell(
        onTap: () async {
          final url = Uri.parse('https://forms.gle/YGqY216NCnHJih7h7');
          try {
            await launchUrl(url, mode: LaunchMode.externalApplication);
          } catch (_) {}
        },
        borderRadius: BorderRadius.circular(6),
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              const Icon(Icons.bug_report, size: 14, color: SkedColors.slate),
              const SizedBox(width: 6),
              Text(
                'REPORT A BUG',
                style: GoogleFonts.barlowCondensed(
                  fontSize: 11,
                  fontWeight: FontWeight.w700,
                  color: SkedColors.slate,
                  letterSpacing: 0.5,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildClassesTab(bool isSunday) {
    return Column(
      children: [
        // Day selector — Mon through Sat
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 4),
          child: Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: _days.map((day) {
              final isSelected = _selectedDay.toLowerCase() == day.toLowerCase();
              final isCurrentDay = _todayName.toLowerCase() == day.toLowerCase();
              final count = _weekClasses.entries
                      .firstWhere(
                        (e) => e.key.toLowerCase() == day.toLowerCase(),
                        orElse: () => const MapEntry('', <ClassEntry>[]),
                      )
                      .value
                      .length;
              final label = day.toUpperCase().substring(0, 3);

              return Expanded(
                child: InkWell(
                  onTap: () => setState(() => _selectedDay = day),
                  borderRadius: BorderRadius.circular(4),
                  child: Padding(
                    padding: const EdgeInsets.symmetric(vertical: 4),
                    child: Column(
                      children: [
                        Row(
                          mainAxisAlignment: MainAxisAlignment.center,
                          children: [
                            Text(
                              label,
                              style: GoogleFonts.barlowCondensed(
                                fontSize: 14,
                                fontWeight: FontWeight.w700,
                                color: isSelected
                                    ? SkedColors.blaze
                                    : (isCurrentDay ? SkedColors.chalk : SkedColors.slate),
                              ),
                            ),
                            if (isCurrentDay) ...[
                              const SizedBox(width: 3),
                              Container(
                                width: 4,
                                height: 4,
                                decoration: const BoxDecoration(
                                  color: SkedColors.blaze,
                                  shape: BoxShape.circle,
                                ),
                              ),
                            ],
                          ],
                        ),
                        if (count > 0)
                          Text(
                            '$count',
                            style: TextStyle(
                              fontFamily: 'monospace',
                              fontSize: 10,
                              color: isSelected ? SkedColors.blaze : SkedColors.slate,
                            ),
                          )
                        else
                          const SizedBox(height: 13),
                        const SizedBox(height: 4),
                        Container(
                          width: 22,
                          height: 2,
                          color: isSelected ? SkedColors.blaze : Colors.transparent,
                        ),
                      ],
                    ),
                  ),
                ),
              );
            }).toList(),
          ),
        ),

        const Divider(color: SkedColors.rule, height: 1),

        // Class list
        Expanded(
          child: RefreshIndicator(
            color: SkedColors.blaze,
            backgroundColor: SkedColors.slab,
            onRefresh: _loadLocalData,
            child: _buildClassList(isSunday),
          ),
        ),
      ],
    );
  }

  Widget _buildClassList(bool isSunday) {
    final isToday = _selectedDay.toLowerCase() == _todayName.toLowerCase();

    // Sunday Chill Mode
    if (isSunday && isToday) {
      return Center(
        child: SingleChildScrollView(
          physics: const AlwaysScrollableScrollPhysics(),
          padding: const EdgeInsets.all(32),
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              const Icon(
                Icons.weekend_outlined,
                size: 80,
                color: SkedColors.blaze,
              ),
              const SizedBox(height: 18),
              Text(
                'Enjoy your Sunday.',
                style: GoogleFonts.barlowCondensed(
                  fontSize: 28,
                  fontWeight: FontWeight.w700,
                  color: SkedColors.chalk,
                ),
              ),
              const SizedBox(height: 6),
              const Text(
                'Sundays are meant for relaxing.\nZero academic stress, full recharge mode.',
                textAlign: TextAlign.center,
                style: TextStyle(fontSize: 13, color: SkedColors.slate, height: 1.4),
              ),
            ],
          ),
        ),
      );
    }

    final listForDay = _weekClasses.entries
            .firstWhere(
              (e) => e.key.toLowerCase() == _selectedDay.toLowerCase(),
              orElse: () => MapEntry('', isToday ? _todayClasses : <ClassEntry>[]),
            )
            .value;

    if (listForDay.isEmpty) {
      return Center(
        child: SingleChildScrollView(
          physics: const AlwaysScrollableScrollPhysics(),
          padding: const EdgeInsets.all(32),
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Text(
                isToday ? 'Nothing today.' : 'No classes scheduled.',
                style: GoogleFonts.barlowCondensed(
                  fontSize: 28,
                  fontWeight: FontWeight.w700,
                  color: SkedColors.chalk,
                ),
              ),
              const SizedBox(height: 6),
              Text(
                isToday ? "You're clear." : 'Free day.',
                style: const TextStyle(fontSize: 14, color: SkedColors.slate),
              ),
            ],
          ),
        ),
      );
    }

    return ListView.builder(
      padding: const EdgeInsets.fromLTRB(16, 12, 16, 24),
      itemCount: listForDay.length,
      itemBuilder: (context, index) {
        final item = listForDay[index];
        return ClassCard(
          item: item,
          timingState: ClassTimingState.pending,
        );
      },
    );
  }
}

class QuickAccessFeatureBox extends StatelessWidget {
  final String title;
  final IconData icon;
  final String? badgeText;
  final VoidCallback onClick;

  const QuickAccessFeatureBox({
    super.key,
    required this.title,
    required this.icon,
    this.badgeText,
    required this.onClick,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      height: 132,
      decoration: BoxDecoration(
        color: SkedColors.slab,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: SkedColors.rule),
      ),
      child: Material(
        color: Colors.transparent,
        child: InkWell(
          onTap: onClick,
          borderRadius: BorderRadius.circular(12),
          child: Stack(
            children: [
              if (badgeText != null && badgeText!.isNotEmpty)
                Positioned(
                  top: 0,
                  right: 0,
                  child: Container(
                    padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                    decoration: const BoxDecoration(
                      color: SkedColors.blaze,
                      borderRadius: BorderRadius.only(
                        topRight: Radius.circular(11),
                        bottomLeft: Radius.circular(8),
                      ),
                    ),
                    child: Text(
                      badgeText!,
                      style: GoogleFonts.barlowCondensed(
                        fontSize: 10.5,
                        fontWeight: FontWeight.w700,
                        color: SkedColors.ink,
                      ),
                    ),
                  ),
                ),
              Padding(
                padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 12),
                child: Column(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    const SizedBox(height: 1),
                    Container(
                      width: 48,
                      height: 48,
                      decoration: BoxDecoration(
                        color: SkedColors.blaze.withOpacity(0.12),
                        shape: BoxShape.circle,
                      ),
                      alignment: Alignment.center,
                      child: Icon(icon, color: SkedColors.blaze, size: 26),
                    ),
                    Container(
                      width: double.infinity,
                      padding: const EdgeInsets.symmetric(horizontal: 4, vertical: 4),
                      decoration: BoxDecoration(
                        color: const Color(0xFF1E1E1E),
                        borderRadius: BorderRadius.circular(4),
                        border: Border.all(color: SkedColors.rule),
                      ),
                      child: Text(
                        title,
                        style: GoogleFonts.barlowCondensed(
                          fontSize: 13,
                          fontWeight: FontWeight.w700,
                          color: SkedColors.chalk,
                        ),
                        textAlign: TextAlign.center,
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                      ),
                    ),
                  ],
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
