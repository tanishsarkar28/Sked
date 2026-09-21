import 'dart:async';
import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:shared_preferences/shared_preferences.dart';
import '../models/class_entry.dart';
import '../models/exam_item.dart';
import '../services/timetable_parser.dart';
import '../services/exam_parser.dart';
import '../theme/app_theme.dart';
import '../widgets/class_card.dart';
import '../widgets/about_developer_dialog.dart';
import '../widgets/ums_auth_bridge_sheet.dart';
import '../services/update_service.dart';
import 'exam_screen.dart';

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

class _DashboardScreenState extends State<DashboardScreen> with SingleTickerProviderStateMixin {
  final List<String> _days = const ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday'];

  String _activeTab = 'CLASSES'; // 'CLASSES' | 'EXAMS'
  late String _selectedDay;
  late String _todayName;

  List<ClassEntry> _todayClasses = [];
  Map<String, List<ClassEntry>> _weekClasses = {};
  List<ExamItem> _exams = [];

  bool _isLoading = false;
  bool _showWebViewBridge = false;
  String _resyncPassword = '';

  late final AnimationController _refreshAnimCtrl;

  @override
  void initState() {
    super.initState();
    _todayName = TimetableParser.todayName();
    _selectedDay = _days.contains(_todayName) ? _todayName : 'Monday';

    _refreshAnimCtrl = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 900),
    );

    _loadLocalData();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      _checkAppUpdate();
    });
  }

  Future<void> _checkAppUpdate() async {
    try {
      final update = await UpdateService.checkForUpdate();
      if (update != null && mounted) {
        UpdateService.showUpdateDialog(context, update);
      }
    } catch (_) {}
  }

  @override
  void dispose() {
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

      if (mounted) {
        setState(() {
          _todayClasses = todayEntries;
          _weekClasses = weekMap;
          _exams = realExams;
          if (!_days.contains(_selectedDay)) {
            _selectedDay = _days.contains(_todayName) ? _todayName : 'Monday';
          }
        });
      }
    } catch (_) {
    } finally {
      if (mounted) {
        setState(() => _isLoading = false);
        _refreshAnimCtrl.reset();
      }
    }
  }

  Future<void> _triggerReSync() async {
    final prefs = await SharedPreferences.getInstance();
    final savedPwd = prefs.getString('saved_ums_pwd') ?? '';

    if (savedPwd.isNotEmpty) {
      setState(() {
        _resyncPassword = savedPwd;
        _showWebViewBridge = true;
      });
      return;
    }

    final pwdCtrl = TextEditingController();
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
          'This will remove your saved credentials and timetable from this iPhone. You can log back in anytime.',
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

  @override
  Widget build(BuildContext context) {
    if (_showWebViewBridge) {
      return UmsAuthBridgeSheet(
        userId: widget.userId,
        password: _resyncPassword,
        onDismiss: () => setState(() => _showWebViewBridge = false),
        onSuccess: (_) {
          setState(() => _showWebViewBridge = false);
          _loadLocalData();
        },
      );
    }

    final isTodaySelected = _selectedDay.toLowerCase() == _todayName.toLowerCase();
    final currentDayList = _weekClasses.entries
            .firstWhere(
              (e) => e.key.toLowerCase() == _selectedDay.toLowerCase(),
              orElse: () => MapEntry('', isTodaySelected ? _todayClasses : <ClassEntry>[]),
            )
            .value;
    final displayCount = currentDayList.length;

    final isSunday = _todayName.toLowerCase() == 'sunday';

    return Scaffold(
      backgroundColor: SkedColors.ink,
      appBar: AppBar(
        title: Text(
          'SKED.',
          style: GoogleFonts.barlowCondensed(
            fontSize: 24,
            fontWeight: FontWeight.w700,
            color: SkedColors.blaze,
            letterSpacing: 0.5,
          ),
        ),
        actions: [
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
          RotationTransition(
            turns: _refreshAnimCtrl,
            child: IconButton(
              tooltip: 'Refresh',
              icon: Icon(
                Icons.refresh,
                color: _isLoading ? SkedColors.blaze : SkedColors.chalk,
                size: 20,
              ),
              onPressed: _loadLocalData,
            ),
          ),
          IconButton(
            tooltip: 'Log Out',
            icon: const Icon(Icons.logout, color: SkedColors.dangerRed, size: 20),
            onPressed: _showLogoutDialog,
          ),
        ],
      ),
      body: Column(
        children: [
          // Header Bar
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 6),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
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
                        Text(
                          isTodaySelected
                              ? (displayCount > 0 ? '$displayCount classes today.' : 'Nothing today.')
                              : (displayCount > 0 ? '$displayCount classes on $_selectedDay.' : 'No classes on $_selectedDay.'),
                          style: GoogleFonts.barlowCondensed(
                            fontSize: 16,
                            fontWeight: FontWeight.w700,
                            color: SkedColors.chalk,
                          ),
                        ),
                      ],
                    ),

                    // RE-SYNC button
                    OutlinedButton.icon(
                      onPressed: _triggerReSync,
                      style: OutlinedButton.styleFrom(
                        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(4)),
                        side: const BorderSide(color: SkedColors.rule),
                        padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
                      ),
                      icon: const Icon(Icons.sync, size: 14, color: SkedColors.blaze),
                      label: Text(
                        'RE-SYNC',
                        style: GoogleFonts.barlowCondensed(
                          fontSize: 11,
                          fontWeight: FontWeight.w700,
                          color: SkedColors.chalk,
                        ),
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 8),
                const Divider(color: SkedColors.rule, height: 1),
                const SizedBox(height: 8),

                // ── Mode Switcher: CLASSES vs EXAMS ─────────────────────────
                Row(
                  children: [
                    Expanded(
                      child: _modeButton(
                        label: 'CLASSES',
                        icon: Icons.calendar_today_outlined,
                        isActive: _activeTab == 'CLASSES',
                        onTap: () => setState(() => _activeTab = 'CLASSES'),
                      ),
                    ),
                    const SizedBox(width: 8),
                    Expanded(
                      child: _modeButton(
                        label: 'EXAMS (${_exams.length})',
                        icon: Icons.school_outlined,
                        isActive: _activeTab == 'EXAMS',
                        onTap: () => setState(() => _activeTab = 'EXAMS'),
                      ),
                    ),
                  ],
                ),
              ],
            ),
          ),

          // Content Area
          Expanded(
            child: _activeTab == 'EXAMS'
                ? ExamScreen(exams: _exams, onRefresh: _triggerReSync)
                : _buildClassesTab(isSunday),
          ),
        ],
      ),
    );
  }

  Widget _modeButton({
    required String label,
    required IconData icon,
    required bool isActive,
    required VoidCallback onTap,
  }) {
    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(4),
      child: Container(
        height: 34,
        decoration: BoxDecoration(
          color: isActive ? SkedColors.blaze : SkedColors.slab,
          borderRadius: BorderRadius.circular(4),
          border: Border.all(color: isActive ? SkedColors.blaze : SkedColors.rule),
        ),
        child: Row(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(
              icon,
              size: 13,
              color: isActive ? SkedColors.ink : SkedColors.slate,
            ),
            const SizedBox(width: 6),
            Text(
              label,
              style: GoogleFonts.barlowCondensed(
                fontSize: 12,
                fontWeight: FontWeight.w700,
                color: isActive ? SkedColors.ink : SkedColors.slate,
                letterSpacing: 0.5,
              ),
            ),
          ],
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

        // Class list / Sunday Chill state / Empty state
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
              Image.asset(
                'assets/sunday_bitmoji.png',
                width: 140,
                height: 140,
                errorBuilder: (_, __, ___) => const Icon(
                  Icons.weekend_outlined,
                  size: 80,
                  color: SkedColors.blaze,
                ),
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
              const SizedBox(height: 24),
              _developerFooterButton(),
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
              const SizedBox(height: 24),
              _developerFooterButton(),
            ],
          ),
        ),
      );
    }

    final now = DateTime.now();
    final nowMinutes = now.hour * 60 + now.minute;
    final todayIndex = _days.indexWhere((d) => d.toLowerCase() == _todayName.toLowerCase());
    final targetDayIndex = _days.indexWhere((d) => d.toLowerCase() == _selectedDay.toLowerCase());

    // Check if today all done
    final todayList = isToday ? listForDay : _todayClasses;
    final todayAllDone = todayList.isNotEmpty &&
        todayList.every((item) {
          final eM = item.endMinutes;
          return eM > 0 && nowMinutes > eM;
        });

    // Find the single next upcoming class
    ClassEntry? upcomingClassItem;
    String? upcomingDayName;

    if (todayIndex >= 0 && !todayAllDone) {
      final live = todayList.firstWhere(
        (item) => item.isOngoingNow(nowMinutes),
        orElse: () => const ClassEntry(day: '', timeRange: '', start: '', end: '', room: '', courseCode: '', type: '', teacher: '', section: '', group: '', description: ''),
      );
      if (live.courseCode.isNotEmpty) {
        upcomingDayName = _todayName;
        upcomingClassItem = live;
      } else {
        final nextToday = todayList.firstWhere(
          (item) => item.startMinutes > nowMinutes,
          orElse: () => const ClassEntry(day: '', timeRange: '', start: '', end: '', room: '', courseCode: '', type: '', teacher: '', section: '', group: '', description: ''),
        );
        if (nextToday.courseCode.isNotEmpty) {
          upcomingDayName = _todayName;
          upcomingClassItem = nextToday;
        }
      }
    }

    if (upcomingClassItem == null && todayIndex >= 0) {
      for (var checkIndex = todayIndex + 1; checkIndex < _days.length; checkIndex++) {
        final dName = _days[checkIndex];
        final dList = _weekClasses.entries
            .firstWhere(
              (e) => e.key.toLowerCase() == dName.toLowerCase(),
              orElse: () => const MapEntry('', <ClassEntry>[]),
            )
            .value;
        if (dList.isNotEmpty) {
          upcomingDayName = dName;
          upcomingClassItem = dList.first;
          break;
        }
      }
    }

    final allDone = isToday && todayAllDone;

    return ListView.builder(
      padding: const EdgeInsets.fromLTRB(16, 12, 16, 24),
      itemCount: listForDay.length + (allDone ? 1 : 0) + 1,
      itemBuilder: (context, index) {
        if (allDone && index == 0) {
          return _celebrationCard(listForDay.length);
        }

        final itemIndex = allDone ? index - 1 : index;
        if (itemIndex == listForDay.length) {
          return Padding(
            padding: const EdgeInsets.only(top: 16),
            child: _developerFooterButton(),
          );
        }

        final item = listForDay[itemIndex];
        final sM = item.startMinutes;
        final eM = item.endMinutes;

        final isLive = isToday && (sM > 0 && eM > 0 && nowMinutes >= sM && nowMinutes <= eM);
        final isUpcoming = _selectedDay.toLowerCase() == upcomingDayName?.toLowerCase() && (item == upcomingClassItem);

        final isOver = isSunday
            ? true
            : (targetDayIndex < todayIndex
                ? true
                : (targetDayIndex == todayIndex ? (eM > 0 && nowMinutes > eM) : false));

        final timingState = isLive
            ? ClassTimingState.onGoing
            : isUpcoming
                ? ClassTimingState.upcoming
                : isOver
                    ? ClassTimingState.over
                    : ClassTimingState.pending;

        return ClassCard(
          item: item,
          timingState: timingState,
        );
      },
    );
  }

  Widget _celebrationCard(int count) {
    return Container(
      margin: const EdgeInsets.only(bottom: 12),
      padding: const EdgeInsets.all(20),
      decoration: BoxDecoration(
        color: SkedColors.slab,
        borderRadius: BorderRadius.circular(6),
        border: Border.all(color: SkedColors.rule),
      ),
      child: Column(
        children: [
          Text(
            'All done.',
            style: GoogleFonts.barlowCondensed(
              fontSize: 24,
              fontWeight: FontWeight.w700,
              color: SkedColors.chalk,
            ),
          ),
          const SizedBox(height: 4),
          Text(
            '$count classes completed.',
            style: const TextStyle(fontSize: 13, color: SkedColors.slate),
          ),
        ],
      ),
    );
  }

  Widget _developerFooterButton() {
    return Center(
      child: InkWell(
        onTap: () {
          showDialog(
            context: context,
            builder: (_) => const AboutDeveloperDialog(),
          );
        },
        borderRadius: BorderRadius.circular(6),
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 8),
          child: Text(
            'DESIGNED & DEVELOPED BY TANISH SARKAR',
            style: GoogleFonts.barlowCondensed(
              fontSize: 11,
              fontWeight: FontWeight.w700,
              color: SkedColors.slate,
              letterSpacing: 0.5,
            ),
          ),
        ),
      ),
    );
  }
}
