import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';
import '../models/class_entry.dart';
import '../services/api_service.dart';
import '../widgets/class_card.dart';
import '../theme/app_theme.dart';

class TodayScreen extends StatefulWidget {
  const TodayScreen({super.key});

  @override
  State<TodayScreen> createState() => _TodayScreenState();
}

class _TodayScreenState extends State<TodayScreen> {
  List<ClassEntry> _entries = [];
  String _day = '';
  DateTime? _lastFetched;
  bool _loading = true;
  String? _error;
  bool _cookieExpired = false;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    setState(() { _loading = true; _error = null; _cookieExpired = false; });
    try {
      final result = await ApiService.instance.getTodayTimetable();
      setState(() {
        _entries = result.entries;
        _day = result.day;
        _lastFetched = result.lastFetched;
        _loading = false;
      });
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
      body: RefreshIndicator(
        color: SkedColors.blaze,
        backgroundColor: SkedColors.slab,
        onRefresh: _load,
        child: CustomScrollView(
          physics: const AlwaysScrollableScrollPhysics(),
          slivers: [
            _buildAppBar(),
            if (_cookieExpired) _buildCookieExpiredBanner(),
            if (_error != null) _buildErrorBanner(),
            if (_loading) _buildLoadingSliver(),
            if (!_loading && _entries.isEmpty && !_cookieExpired && _error == null)
              _buildEmptySliver(),
            if (!_loading && _entries.isNotEmpty)
              _buildEntryList(),
          ],
        ),
      ),
    );
  }

  SliverToBoxAdapter _buildAppBar() {
    final count = _entries.length;
    final headline = count > 0 ? '$count classes today.' : 'Nothing today.';

    return SliverToBoxAdapter(
      child: SafeArea(
        bottom: false,
        child: Padding(
          padding: const EdgeInsets.fromLTRB(16, 16, 16, 8),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              // Top wordmark row
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Row(
                    children: [
                      Text(
                        'SKED.',
                        style: GoogleFonts.barlowCondensed(
                          fontSize: 22,
                          fontWeight: FontWeight.w700,
                          color: SkedColors.blaze,
                          letterSpacing: 0.5,
                        ),
                      ),
                      const SizedBox(width: 8),
                      // Synced dot
                      Container(
                        width: 6,
                        height: 6,
                        decoration: const BoxDecoration(
                          color: SkedColors.blaze,
                          shape: BoxShape.circle,
                        ),
                      ),
                    ],
                  ),
                  if (_lastFetched != null)
                    Text(
                      'Updated ${_formatTime(_lastFetched!)}',
                      style: const TextStyle(
                        fontFamily: 'monospace',
                        fontSize: 11,
                        color: SkedColors.slate,
                      ),
                    ),
                ],
              ),
              const SizedBox(height: 14),

              // Screen Title
              Text(
                'TODAY.',
                style: GoogleFonts.barlowCondensed(
                  fontSize: 32,
                  fontWeight: FontWeight.w700,
                  color: SkedColors.chalk,
                  letterSpacing: 0.5,
                ),
              ),
              const SizedBox(height: 2),

              // Subtitle
              Text(
                headline,
                style: GoogleFonts.barlowCondensed(
                  fontSize: 16,
                  fontWeight: FontWeight.w600,
                  color: SkedColors.slate,
                ),
              ),
              const SizedBox(height: 12),
              const Divider(color: SkedColors.rule, thickness: 1),
            ],
          ),
        ),
      ),
    );
  }

  SliverToBoxAdapter _buildCookieExpiredBanner() {
    return SliverToBoxAdapter(
      child: Container(
        margin: const EdgeInsets.fromLTRB(16, 8, 16, 8),
        padding: const EdgeInsets.all(12),
        decoration: BoxDecoration(
          color: SkedColors.slab,
          borderRadius: BorderRadius.circular(4),
          border: Border.all(color: SkedColors.blaze.withOpacity(0.5)),
        ),
        child: Row(
          children: [
            const Icon(Icons.warning_amber_rounded, color: SkedColors.blaze, size: 18),
            const SizedBox(width: 10),
            const Expanded(
              child: Text(
                'Session cookie expired. Refresh in Settings.',
                style: TextStyle(color: SkedColors.chalk, fontSize: 13),
              ),
            ),
            TextButton(
              onPressed: () => Navigator.pushNamed(context, '/settings').then((_) => _load()),
              style: TextButton.styleFrom(
                padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
              ),
              child: Text(
                'FIX.',
                style: GoogleFonts.barlowCondensed(
                  fontSize: 13,
                  fontWeight: FontWeight.w700,
                  color: SkedColors.blaze,
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  SliverToBoxAdapter _buildErrorBanner() {
    return SliverToBoxAdapter(
      child: Container(
        margin: const EdgeInsets.fromLTRB(16, 8, 16, 8),
        padding: const EdgeInsets.all(12),
        decoration: BoxDecoration(
          color: SkedColors.slab,
          borderRadius: BorderRadius.circular(4),
          border: Border.all(color: SkedColors.error.withOpacity(0.5)),
        ),
        child: Row(
          children: [
            const Icon(Icons.error_outline_rounded, color: SkedColors.error, size: 18),
            const SizedBox(width: 10),
            Expanded(
              child: Text(
                _error!,
                style: const TextStyle(color: SkedColors.chalk, fontSize: 13),
              ),
            ),
            IconButton(
              onPressed: _load,
              icon: const Icon(Icons.refresh_rounded, color: SkedColors.chalk, size: 18),
            ),
          ],
        ),
      ),
    );
  }

  SliverFillRemaining _buildLoadingSliver() {
    return const SliverFillRemaining(
      child: Center(
        child: CircularProgressIndicator(color: SkedColors.blaze, strokeWidth: 2),
      ),
    );
  }

  SliverFillRemaining _buildEmptySliver() {
    return SliverFillRemaining(
      hasScrollBody: false,
      child: Center(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Text(
              'Nothing today.',
              style: GoogleFonts.barlowCondensed(
                fontSize: 28,
                fontWeight: FontWeight.w700,
                color: SkedColors.chalk,
              ),
            ),
            const SizedBox(height: 4),
            const Text(
              "You're clear.",
              style: TextStyle(
                fontSize: 14,
                color: SkedColors.slate,
              ),
            ),
          ],
        ),
      ),
    );
  }

  SliverPadding _buildEntryList() {
    final now = DateTime.now();
    return SliverPadding(
      padding: const EdgeInsets.fromLTRB(16, 8, 16, 32),
      sliver: SliverList(
        delegate: SliverChildBuilderDelegate(
          (context, i) {
            final e = _entries[i];
            return ClassCard(
              key: ValueKey('${e.courseCode}-${e.start}'),
              entry: e,
              isOngoing: e.isOngoing(now),
              isNext: !_entries.any((x) => x.isOngoing(now)) &&
                  e.isUpcoming(now) &&
                  i == _entries.indexWhere((x) => x.isUpcoming(now)),
            );
          },
          childCount: _entries.length,
        ),
      ),
    );
  }

  String _formatTime(DateTime dt) {
    final h = dt.hour.toString().padLeft(2, '0');
    final m = dt.minute.toString().padLeft(2, '0');
    return '$h:$m';
  }
}
