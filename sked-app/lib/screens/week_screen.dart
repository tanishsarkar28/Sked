import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';
import '../models/class_entry.dart';
import '../services/api_service.dart';
import '../widgets/class_card.dart';
import '../theme/app_theme.dart';

const _orderedDays = ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday', 'Sunday'];

class WeekScreen extends StatefulWidget {
  const WeekScreen({super.key});

  @override
  State<WeekScreen> createState() => _WeekScreenState();
}

class _WeekScreenState extends State<WeekScreen> with TickerProviderStateMixin {
  Map<String, List<ClassEntry>> _week = {};
  bool _loading = true;
  String? _error;
  bool _cookieExpired = false;
  late TabController _tabController;

  @override
  void initState() {
    super.initState();
    _tabController = TabController(length: _orderedDays.length, vsync: this);
    final today = _todayName();
    final idx = _orderedDays.indexOf(today);
    if (idx >= 0) _tabController.index = idx;
    _load();
  }

  @override
  void dispose() {
    _tabController.dispose();
    super.dispose();
  }

  String _todayName() {
    const names = ['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday'];
    return names[DateTime.now().weekday % 7];
  }

  Future<void> _load() async {
    setState(() { _loading = true; _error = null; _cookieExpired = false; });
    try {
      final week = await ApiService.instance.getWeekTimetable();
      setState(() { _week = week; _loading = false; });
    } on CookieExpiredException {
      setState(() { _loading = false; _cookieExpired = true; });
    } on ApiException catch (e) {
      setState(() { _loading = false; _error = e.message; });
    } catch (e) {
      setState(() { _loading = false; _error = e.toString(); });
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: SkedColors.ink,
      appBar: AppBar(
        backgroundColor: SkedColors.ink,
        elevation: 0,
        title: Text(
          'WEEK.',
          style: GoogleFonts.barlowCondensed(
            fontSize: 24,
            fontWeight: FontWeight.w700,
            color: SkedColors.chalk,
            letterSpacing: 0.5,
          ),
        ),
        bottom: TabBar(
          controller: _tabController,
          isScrollable: true,
          tabAlignment: TabAlignment.start,
          indicatorColor: SkedColors.blaze,
          indicatorWeight: 2,
          indicatorSize: TabBarIndicatorSize.label,
          labelColor: SkedColors.blaze,
          unselectedLabelColor: SkedColors.slate,
          dividerColor: SkedColors.rule,
          tabs: _orderedDays.map((d) {
            final count = _week[d]?.length ?? 0;
            final isSelected = _tabController.index == _orderedDays.indexOf(d);
            return Tab(
              child: Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Text(
                    d.substring(0, 3).toUpperCase(),
                    style: GoogleFonts.barlowCondensed(
                      fontSize: 14,
                      fontWeight: FontWeight.w700,
                    ),
                  ),
                  if (count > 0) ...[
                    const SizedBox(width: 4),
                    Text(
                      '$count',
                      style: TextStyle(
                        fontFamily: 'monospace',
                        fontSize: 10,
                        fontWeight: FontWeight.w600,
                        color: isSelected ? SkedColors.blaze : SkedColors.slate,
                      ),
                    ),
                  ],
                ],
              ),
            );
          }).toList(),
        ),
      ),
      body: _loading
          ? const Center(child: CircularProgressIndicator(color: SkedColors.blaze, strokeWidth: 2))
          : _error != null
              ? _buildError()
              : _cookieExpired
                  ? _buildCookieExpired()
                  : TabBarView(
                      controller: _tabController,
                      children: _orderedDays.map((day) => _buildDayTab(day)).toList(),
                    ),
    );
  }

  Widget _buildDayTab(String day) {
    final entries = _week[day] ?? [];
    if (entries.isEmpty) {
      return Center(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Text(
              'No classes.',
              style: GoogleFonts.barlowCondensed(
                fontSize: 24,
                fontWeight: FontWeight.w700,
                color: SkedColors.chalk,
              ),
            ),
            const SizedBox(height: 4),
            Text(
              'Clear for $day.',
              style: const TextStyle(
                fontSize: 13,
                color: SkedColors.slate,
              ),
            ),
          ],
        ),
      );
    }
    return RefreshIndicator(
      color: SkedColors.blaze,
      backgroundColor: SkedColors.slab,
      onRefresh: _load,
      child: ListView.builder(
        padding: const EdgeInsets.fromLTRB(16, 12, 16, 32),
        itemCount: entries.length,
        itemBuilder: (ctx, i) => ClassCard(
          key: ValueKey('${entries[i].courseCode}-${entries[i].start}'),
          entry: entries[i],
        ),
      ),
    );
  }

  Widget _buildError() {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(32),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Icon(Icons.error_outline_rounded, color: SkedColors.error, size: 40),
            const SizedBox(height: 12),
            Text(
              _error!,
              style: const TextStyle(color: SkedColors.slate, fontSize: 13),
              textAlign: TextAlign.center,
            ),
            const SizedBox(height: 16),
            ElevatedButton(
              onPressed: _load,
              child: const Text('RETRY.'),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildCookieExpired() {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(32),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Text(
              'SESSION EXPIRED.',
              style: GoogleFonts.barlowCondensed(
                fontSize: 22,
                fontWeight: FontWeight.w700,
                color: SkedColors.chalk,
              ),
            ),
            const SizedBox(height: 6),
            const Text(
              'Please paste a fresh session cookie in Settings.',
              style: TextStyle(color: SkedColors.slate, fontSize: 13),
              textAlign: TextAlign.center,
            ),
            const SizedBox(height: 20),
            ElevatedButton(
              onPressed: () => Navigator.pushNamed(context, '/settings').then((_) => _load()),
              child: const Text('GO TO SETTINGS.'),
            ),
          ],
        ),
      ),
    );
  }
}
