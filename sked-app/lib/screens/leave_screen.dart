import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:intl/intl.dart';
import 'package:url_launcher/url_launcher.dart';
import '../models/leave_models.dart';
import '../services/leave_parser.dart';
import '../services/leave_storage.dart';
import '../services/leave_submit_engine.dart';
import '../theme/app_theme.dart';

class UmsTimeSlot {
  final int hour;
  final int minute;
  const UmsTimeSlot(this.hour, this.minute);

  String get display {
    final dt = DateTime(2026, 1, 1, hour, minute);
    return DateFormat('h:mm a').format(dt);
  }
}

List<UmsTimeSlot> get departureTimeSlots {
  final list = <UmsTimeSlot>[];
  for (var h = 5; h <= 22; h++) {
    for (final m in [0, 15, 30, 45]) {
      list.add(UmsTimeSlot(h, m));
    }
  }
  return list;
}

List<UmsTimeSlot> get returnTimeSlots {
  final list = <UmsTimeSlot>[];
  for (var h = 5; h <= 23; h++) {
    for (final m in [0, 15, 30, 45]) {
      list.add(UmsTimeSlot(h, m));
    }
  }
  return list;
}

class LeaveScreen extends StatefulWidget {
  final VoidCallback onBack;
  final VoidCallback? onReSync;

  const LeaveScreen({
    super.key,
    required this.onBack,
    this.onReSync,
  });

  @override
  State<LeaveScreen> createState() => _LeaveScreenState();
}

class _LeaveScreenState extends State<LeaveScreen> {
  LeaveBalance _balance = const LeaveBalance();
  LeaveProfile _profile = const LeaveProfile();
  List<LeaveRecord> _records = [];

  bool _isRefreshing = false;
  bool _isSubmitting = false;
  String _submitStatusText = '';
  String? _errorMessageDialog;
  String? _successLeaveIdDialog;

  // Parent OTP State
  bool _isOtpUser = false;
  bool _showParentOtpDialog = false;
  String _parentOtpPhoneTarget = '';

  // Form States
  LeaveTypeOption _selectedType = LeaveTypeOption.dayExtended;
  VisitPlaceOption _selectedPlace = VisitPlaceOption.other;
  final TextEditingController _stayAddressCtrl = TextEditingController();
  final TextEditingController _relativeMobileCtrl = TextEditingController();
  final TextEditingController _reasonCtrl = TextEditingController();
  bool _declarationAccepted = true;

  late DateTime _startDateTime;
  late DateTime _endDateTime;

  final DateFormat _dateFormat = DateFormat('EEE, MMM d, yyyy');
  final DateFormat _timeFormat = DateFormat('h:mm a');

  @override
  void initState() {
    super.initState();
    _initDateTimeDefaults();
    _loadStoredData();
    _refreshLeaveData(isUserInitiated: false);
  }

  void _initDateTimeDefaults() {
    final now = DateTime.now();
    DateTime start;
    if (now.hour >= 20) {
      // If it's already 8:00 PM or later, default to tomorrow 8:00 AM
      final tomorrow = now.add(const Duration(days: 1));
      start = DateTime(tomorrow.year, tomorrow.month, tomorrow.day, 8, 0);
    } else {
      final snappedMin = now.minute < 15
          ? 15
          : now.minute < 30
              ? 30
              : now.minute < 45
                  ? 45
                  : 0;
      final addHour = now.minute >= 45 ? 1 : 0;
      var h = now.hour + addHour;
      if (h < 5) h = 5;
      start = DateTime(now.year, now.month, now.day, h, snappedMin);
    }
    _startDateTime = start;

    final maxHour = isWinterCurfew(start) ? 21 : 22;
    _endDateTime = DateTime(start.year, start.month, start.day, maxHour, 45);
  }

  Future<void> _loadStoredData() async {
    final bal = await LeaveStorage.loadBalance();
    final prof = await LeaveStorage.loadProfile();
    final recs = await LeaveStorage.loadRecords();
    final isOtp = await LeaveStorage.isOtpRequired();
    final lastStay = await LeaveStorage.getLastStayAddress();
    final lastReason = await LeaveStorage.getLastReason();
    final lastMobile = await LeaveStorage.getLastRelativeMobile();

    if (mounted) {
      setState(() {
        _balance = bal;
        _profile = prof;
        _records = recs;
        _isOtpUser = isOtp;
        _stayAddressCtrl.text = lastStay;
        _reasonCtrl.text = lastReason;
        _relativeMobileCtrl.text = lastMobile.isNotEmpty ? lastMobile : prof.registeredMobile;
      });
    }
  }

  void _onLeaveTypeChanged(LeaveTypeOption opt) {
    setState(() {
      _selectedType = opt;
      final validPlaces = VisitPlaceOption.optionsFor(opt);
      if (!validPlaces.any((p) => p.code == _selectedPlace.code)) {
        _selectedPlace = validPlaces.firstOrNull ?? VisitPlaceOption.other;
      }

      final maxHour = isWinterCurfew(_startDateTime) ? 21 : 22;
      switch (opt) {
        case LeaveTypeOption.dayExtended:
          _endDateTime = DateTime(
              _startDateTime.year, _startDateTime.month, _startDateTime.day, maxHour, 45);
          break;
        case LeaveTypeOption.dayStandard:
          _endDateTime = DateTime(
              _startDateTime.year, _startDateTime.month, _startDateTime.day, 20, 0);
          break;
        case LeaveTypeOption.nightStandard:
          final next = _startDateTime.add(const Duration(days: 1));
          _endDateTime = DateTime(next.year, next.month, next.day, 20, 0);
          break;
        case LeaveTypeOption.nightExtended:
          final next = _startDateTime.add(const Duration(days: 1));
          _endDateTime = DateTime(next.year, next.month, next.day, maxHour, 45);
          break;
        case LeaveTypeOption.vacation:
          final next = _startDateTime.add(const Duration(days: 5));
          _endDateTime = DateTime(next.year, next.month, next.day, 18, 0);
          break;
      }
    });
  }

  Future<void> _refreshLeaveData({bool isUserInitiated = false}) async {
    if (_isRefreshing) return;
    setState(() => _isRefreshing = true);

    try {
      final res = await LeaveSubmitEngine.fetchLatestData();
      if (!mounted) return;

      if (res is LeaveSyncSuccess) {
        setState(() {
          if (res.data.balance.dayTotal > 0 || res.data.balance.nightTotal > 0) {
            _balance = res.data.balance;
          }
          if (res.data.profile.uid.isNotEmpty || res.data.profile.name.isNotEmpty) {
            _profile = res.data.profile;
          }
          if (res.data.records.isNotEmpty) {
            _records = res.data.records;
          }
          if (res.data.requiresOtp) {
            _isOtpUser = true;
          }
          if (_relativeMobileCtrl.text.isEmpty && res.data.profile.registeredMobile.isNotEmpty) {
            _relativeMobileCtrl.text = res.data.profile.registeredMobile;
          }
        });
      } else if (res is LeaveSyncExpired && isUserInitiated) {
        widget.onReSync?.call();
      }
    } catch (_) {} finally {
      if (mounted) setState(() => _isRefreshing = false);
    }
  }

  void _showTimeSlotPicker({required bool isStart}) {
    final activeSlots = isStart ? departureTimeSlots : returnTimeSlots;
    final targetDt = isStart ? _startDateTime : _endDateTime;

    showDialog(
      context: context,
      builder: (ctx) => Dialog(
        backgroundColor: SkedColors.slabElevated,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(16),
          side: const BorderSide(color: SkedColors.rule),
        ),
        insetPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 24),
        child: Container(
          padding: const EdgeInsets.all(16),
          height: MediaQuery.of(context).size.height * 0.72,
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        isStart ? 'SELECT DEPARTURE TIME' : 'SELECT RETURN TIME',
                        style: GoogleFonts.barlowCondensed(
                          fontSize: 16,
                          fontWeight: FontWeight.w700,
                          color: SkedColors.chalk,
                          letterSpacing: 0.5,
                        ),
                      ),
                      const SizedBox(height: 2),
                      Text(
                        isStart
                            ? '15-minute slots • 5:00 AM to 10:45 PM'
                            : '15-minute slots • 5:00 AM to 11:45 PM',
                        style: const TextStyle(fontSize: 11, color: SkedColors.slate),
                      ),
                    ],
                  ),
                  IconButton(
                    icon: const Icon(Icons.close, color: SkedColors.slate, size: 20),
                    onPressed: () => Navigator.pop(ctx),
                  ),
                ],
              ),
              const SizedBox(height: 8),
              const Divider(color: SkedColors.rule, height: 1),
              const SizedBox(height: 12),
              Expanded(
                child: GridView.builder(
                  gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
                    crossAxisCount: 4,
                    mainAxisSpacing: 6,
                    crossAxisSpacing: 6,
                    childAspectRatio: 2.2,
                  ),
                  itemCount: activeSlots.length,
                  itemBuilder: (context, i) {
                    final slot = activeSlots[i];
                    final isSelected = slot.hour == targetDt.hour && slot.minute == targetDt.minute;
                    return InkWell(
                      onTap: () {
                        setState(() {
                          if (isStart) {
                            _startDateTime = DateTime(
                              _startDateTime.year,
                              _startDateTime.month,
                              _startDateTime.day,
                              slot.hour,
                              slot.minute,
                            );
                            if (_endDateTime.isBefore(_startDateTime)) {
                              _endDateTime = _startDateTime.add(const Duration(hours: 2));
                            }
                          } else {
                            _endDateTime = DateTime(
                              _endDateTime.year,
                              _endDateTime.month,
                              _endDateTime.day,
                              slot.hour,
                              slot.minute,
                            );
                          }
                        });
                        Navigator.pop(ctx);
                      },
                      borderRadius: BorderRadius.circular(6),
                      child: Container(
                        decoration: BoxDecoration(
                          color: isSelected ? SkedColors.blaze : SkedColors.slab,
                          borderRadius: BorderRadius.circular(6),
                          border: Border.all(
                            color: isSelected ? SkedColors.blaze : SkedColors.rule,
                          ),
                        ),
                        alignment: Alignment.center,
                        child: Text(
                          slot.display,
                          style: GoogleFonts.barlowCondensed(
                            fontSize: 12,
                            fontWeight: isSelected ? FontWeight.w700 : FontWeight.w500,
                            color: isSelected ? SkedColors.ink : SkedColors.chalk,
                          ),
                        ),
                      ),
                    );
                  },
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Future<void> _selectDate({required bool isStart}) async {
    final target = isStart ? _startDateTime : _endDateTime;
    final picked = await showDatePicker(
      context: context,
      initialDate: target,
      firstDate: DateTime.now().subtract(const Duration(days: 1)),
      lastDate: DateTime.now().add(const Duration(days: 60)),
      builder: (context, child) {
        return Theme(
          data: ThemeData.dark().copyWith(
            colorScheme: const ColorScheme.dark(
              primary: SkedColors.blaze,
              onPrimary: SkedColors.ink,
              surface: SkedColors.slabElevated,
              onSurface: SkedColors.chalk,
            ),
          ),
          child: child!,
        );
      },
    );

    if (picked != null) {
      setState(() {
        if (isStart) {
          _startDateTime = DateTime(
            picked.year,
            picked.month,
            picked.day,
            _startDateTime.hour,
            _startDateTime.minute,
          );
          if (_selectedType == LeaveTypeOption.dayExtended ||
              _selectedType == LeaveTypeOption.dayStandard) {
            _endDateTime = DateTime(
              picked.year,
              picked.month,
              picked.day,
              _endDateTime.hour,
              _endDateTime.minute,
            );
          } else if (_endDateTime.isBefore(_startDateTime)) {
            _endDateTime = _startDateTime.add(const Duration(days: 1));
          }
        } else {
          _endDateTime = DateTime(
            picked.year,
            picked.month,
            picked.day,
            _endDateTime.hour,
            _endDateTime.minute,
          );
        }
      });
    }
  }

  void _onAuthorizeAndApply() {
    final mob = _relativeMobileCtrl.text.trim();
    final rsn = _reasonCtrl.text.trim();

    if (mob.length < 10) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('Please enter a valid 10-digit mobile number'),
          backgroundColor: SkedColors.dangerRed,
        ),
      );
      return;
    }
    if (rsn.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('Please enter a reason for leaving'),
          backgroundColor: SkedColors.dangerRed,
        ),
      );
      return;
    }
    if (_endDateTime.isBefore(_startDateTime)) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('Return date & time must be after departure time'),
          backgroundColor: SkedColors.dangerRed,
        ),
      );
      return;
    }
    if (!_declarationAccepted) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('Please accept the leave declaration'),
          backgroundColor: SkedColors.dangerRed,
        ),
      );
      return;
    }

    LeaveStorage.saveFormDefaults(
      stayAddress: _stayAddressCtrl.text.trim(),
      reason: rsn,
      relativeMobile: mob,
    );

    // If Parent OTP is required on this account, trigger the exact OTP notice dialog
    if (_isOtpUser) {
      setState(() {
        _parentOtpPhoneTarget = mob.length >= 4 ? 'xxxxxxx${mob.substring(mob.length - 4)}' : '';
        _showParentOtpDialog = true;
      });
      return;
    }

    // Direct submission: Trigger Parent OTP Notice dialog or WebView submit
    setState(() {
      _parentOtpPhoneTarget = mob.length >= 4 ? 'xxxxxxx${mob.substring(mob.length - 4)}' : '';
      _showParentOtpDialog = true;
      _isOtpUser = true;
    });
    LeaveStorage.setOtpRequired(true);
  }

  Future<void> _openUmsPortal() async {
    final url = Uri.parse(LeaveSubmitEngine.leaveUrl);
    try {
      await launchUrl(url, mode: LaunchMode.externalApplication);
    } catch (_) {}
  }

  @override
  Widget build(BuildContext context) {
    final isDayLeave = _selectedType == LeaveTypeOption.dayExtended ||
        _selectedType == LeaveTypeOption.dayStandard;

    return Scaffold(
      backgroundColor: SkedColors.ink,
      appBar: AppBar(
        backgroundColor: SkedColors.ink,
        leading: IconButton(
          icon: const Icon(Icons.arrow_back, color: SkedColors.chalk),
          onPressed: widget.onBack,
        ),
        title: Row(
          children: [
            Text(
              'SKED',
              style: GoogleFonts.barlowCondensed(
                fontSize: 22,
                fontWeight: FontWeight.w700,
                color: SkedColors.blaze,
              ),
            ),
            const SizedBox(width: 8),
            Text(
              '• HOSTEL LEAVE',
              style: GoogleFonts.barlowCondensed(
                fontSize: 16,
                fontWeight: FontWeight.w700,
                color: SkedColors.slate,
              ),
            ),
          ],
        ),
      ),
      body: Stack(
        children: [
          ListView(
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
            children: [
              // ── 1. Header & Live Balance Summary Cards ────────────────────────
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Text(
                    'HOSTEL LEAVE BALANCE',
                    style: GoogleFonts.barlowCondensed(
                      fontSize: 13,
                      fontWeight: FontWeight.w700,
                      color: SkedColors.slate,
                      letterSpacing: 0.8,
                    ),
                  ),
                  InkWell(
                    onTap: _isRefreshing ? null : () => _refreshLeaveData(isUserInitiated: true),
                    borderRadius: BorderRadius.circular(6),
                    child: Padding(
                      padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                      child: Row(
                        children: [
                          if (_isRefreshing) ...[
                            const SizedBox(
                              width: 12,
                              height: 12,
                              child: CircularProgressIndicator(
                                strokeWidth: 1.5,
                                color: SkedColors.blaze,
                              ),
                            ),
                            const SizedBox(width: 4),
                          ],
                          Text(
                            _isRefreshing ? 'SYNCING...' : 'REFRESH',
                            style: GoogleFonts.barlowCondensed(
                              fontSize: 11,
                              fontWeight: FontWeight.w700,
                              color: _isRefreshing ? SkedColors.blaze : SkedColors.slate,
                              letterSpacing: 0.5,
                            ),
                          ),
                        ],
                      ),
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 8),

              // Balance Cards Row
              Row(
                children: [
                  // Day Leave Card
                  Expanded(
                    child: Container(
                      padding: const EdgeInsets.all(14),
                      decoration: BoxDecoration(
                        color: SkedColors.slab,
                        borderRadius: BorderRadius.circular(10),
                        border: Border.all(color: SkedColors.rule),
                      ),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Row(
                            mainAxisAlignment: MainAxisAlignment.spaceBetween,
                            children: [
                              Text(
                                'DAY LEAVE',
                                style: GoogleFonts.barlowCondensed(
                                  fontSize: 12,
                                  fontWeight: FontWeight.w700,
                                  color: SkedColors.slate,
                                  letterSpacing: 0.5,
                                ),
                              ),
                              Text(
                                _balance.isLoaded
                                    ? '${_balance.dayAvailed}/${_balance.dayTotal} USED'
                                    : '--',
                                style: const TextStyle(
                                  fontSize: 10,
                                  fontFamily: 'monospace',
                                  color: SkedColors.slate,
                                ),
                              ),
                            ],
                          ),
                          const SizedBox(height: 4),
                          Row(
                            crossAxisAlignment: CrossAxisAlignment.end,
                            children: [
                              Text(
                                _balance.isLoaded ? '${_balance.dayBalance}' : '--',
                                style: GoogleFonts.barlowCondensed(
                                  fontSize: 28,
                                  fontWeight: FontWeight.w700,
                                  color: _balance.dayBalance > 5
                                      ? SkedColors.blaze
                                      : SkedColors.dangerRed,
                                ),
                              ),
                              const SizedBox(width: 4),
                              Padding(
                                padding: const EdgeInsets.only(bottom: 4),
                                child: Text(
                                  'REMAINING',
                                  style: GoogleFonts.barlowCondensed(
                                    fontSize: 11,
                                    color: SkedColors.slate,
                                  ),
                                ),
                              ),
                            ],
                          ),
                        ],
                      ),
                    ),
                  ),
                  const SizedBox(width: 10),

                  // Night Leave Card
                  Expanded(
                    child: Container(
                      padding: const EdgeInsets.all(14),
                      decoration: BoxDecoration(
                        color: SkedColors.slab,
                        borderRadius: BorderRadius.circular(10),
                        border: Border.all(color: SkedColors.rule),
                      ),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Row(
                            mainAxisAlignment: MainAxisAlignment.spaceBetween,
                            children: [
                              Text(
                                'NIGHT LEAVE',
                                style: GoogleFonts.barlowCondensed(
                                  fontSize: 12,
                                  fontWeight: FontWeight.w700,
                                  color: SkedColors.slate,
                                  letterSpacing: 0.5,
                                ),
                              ),
                              Text(
                                _balance.isLoaded
                                    ? '${_balance.nightAvailed}/${_balance.nightTotal} USED'
                                    : '--',
                                style: const TextStyle(
                                  fontSize: 10,
                                  fontFamily: 'monospace',
                                  color: SkedColors.slate,
                                ),
                              ),
                            ],
                          ),
                          const SizedBox(height: 4),
                          Row(
                            crossAxisAlignment: CrossAxisAlignment.end,
                            children: [
                              Text(
                                _balance.isLoaded ? '${_balance.nightBalance}' : '--',
                                style: GoogleFonts.barlowCondensed(
                                  fontSize: 28,
                                  fontWeight: FontWeight.w700,
                                  color: _balance.nightBalance > 3
                                      ? SkedColors.chalk
                                      : SkedColors.dangerRed,
                                ),
                              ),
                              const SizedBox(width: 4),
                              Padding(
                                padding: const EdgeInsets.only(bottom: 4),
                                child: Text(
                                  'REMAINING',
                                  style: GoogleFonts.barlowCondensed(
                                    fontSize: 11,
                                    color: SkedColors.slate,
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
              const SizedBox(height: 16),

              // ── 2. New Application Form ───────────────────────────────────────
              Container(
                padding: const EdgeInsets.all(16),
                decoration: BoxDecoration(
                  color: SkedColors.slab,
                  borderRadius: BorderRadius.circular(12),
                  border: Border.all(color: SkedColors.rule),
                ),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      'APPLY HOSTEL LEAVE',
                      style: GoogleFonts.barlowCondensed(
                        fontSize: 14,
                        fontWeight: FontWeight.w700,
                        color: SkedColors.chalk,
                        letterSpacing: 0.5,
                      ),
                    ),
                    const SizedBox(height: 14),

                    // Leave Type Selector
                    Text(
                      'LEAVE TYPE',
                      style: GoogleFonts.barlowCondensed(
                        fontSize: 11,
                        fontWeight: FontWeight.w700,
                        color: SkedColors.slate,
                      ),
                    ),
                    const SizedBox(height: 6),
                    SingleChildScrollView(
                      scrollDirection: Axis.horizontal,
                      child: Row(
                        children: LeaveTypeOption.values.map((opt) {
                          final isSelected = opt == _selectedType;
                          return Padding(
                            padding: const EdgeInsets.only(right: 8),
                            child: InkWell(
                              onTap: () => _onLeaveTypeChanged(opt),
                              borderRadius: BorderRadius.circular(6),
                              child: Container(
                                padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
                                decoration: BoxDecoration(
                                  color: isSelected
                                      ? SkedColors.blaze.withOpacity(0.15)
                                      : SkedColors.slabElevated,
                                  borderRadius: BorderRadius.circular(6),
                                  border: Border.all(
                                    color: isSelected ? SkedColors.blaze : SkedColors.rule,
                                  ),
                                ),
                                child: Text(
                                  opt.displayName,
                                  style: GoogleFonts.barlowCondensed(
                                    fontSize: 12,
                                    fontWeight: isSelected ? FontWeight.w700 : FontWeight.w400,
                                    color: isSelected ? SkedColors.blaze : SkedColors.chalk,
                                  ),
                                ),
                              ),
                            ),
                          );
                        }).toList(),
                      ),
                    ),
                    const SizedBox(height: 8),

                    // Curfew Alert Pill
                    Container(
                      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                      decoration: BoxDecoration(
                        color: SkedColors.slabElevated,
                        borderRadius: BorderRadius.circular(4),
                        border: Border.all(color: SkedColors.rule),
                      ),
                      child: Row(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          const Icon(Icons.access_time, color: SkedColors.blaze, size: 13),
                          const SizedBox(width: 5),
                          Text(
                            _selectedType.curfewNotice,
                            style: GoogleFonts.barlowCondensed(
                              fontSize: 11,
                              color: SkedColors.slate,
                            ),
                          ),
                        ],
                      ),
                    ),
                    const SizedBox(height: 14),

                    // Visit Place Dropdown
                    Text(
                      'VISIT PLACE',
                      style: GoogleFonts.barlowCondensed(
                        fontSize: 11,
                        fontWeight: FontWeight.w700,
                        color: SkedColors.slate,
                      ),
                    ),
                    const SizedBox(height: 6),
                    Container(
                      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 2),
                      decoration: BoxDecoration(
                        color: SkedColors.slabElevated,
                        borderRadius: BorderRadius.circular(6),
                        border: Border.all(color: SkedColors.rule),
                      ),
                      child: DropdownButtonHideUnderline(
                        child: DropdownButton<VisitPlaceOption>(
                          value: _selectedPlace,
                          dropdownColor: SkedColors.slabElevated,
                          isExpanded: true,
                          icon: const Icon(Icons.keyboard_arrow_down, color: SkedColors.slate),
                          items: VisitPlaceOption.optionsFor(_selectedType).map((p) {
                            return DropdownMenuItem(
                              value: p,
                              child: Row(
                                children: [
                                  const Icon(Icons.place, color: SkedColors.blaze, size: 16),
                                  const SizedBox(width: 8),
                                  Text(
                                    p.displayName,
                                    style: const TextStyle(
                                      fontSize: 13,
                                      fontWeight: FontWeight.w600,
                                      color: SkedColors.chalk,
                                    ),
                                  ),
                                ],
                              ),
                            );
                          }).toList(),
                          onChanged: (val) {
                            if (val != null) {
                              setState(() {
                                _selectedPlace = val;
                                if (val == VisitPlaceOption.home && _profile.homeAddress.isNotEmpty) {
                                  _stayAddressCtrl.text = _profile.homeAddress;
                                }
                              });
                            }
                          },
                        ),
                      ),
                    ),
                    const SizedBox(height: 14),

                    // Stay Address / Location
                    Text(
                      'STAY ADDRESS / LOCATION',
                      style: GoogleFonts.barlowCondensed(
                        fontSize: 11,
                        fontWeight: FontWeight.w700,
                        color: SkedColors.slate,
                      ),
                    ),
                    const SizedBox(height: 4),
                    TextField(
                      controller: _stayAddressCtrl,
                      style: const TextStyle(color: SkedColors.chalk, fontSize: 13),
                      decoration: const InputDecoration(
                        hintText: 'Enter destination address or city',
                      ),
                    ),

                    // Quick Stay Address Chips
                    const SizedBox(height: 6),
                    SingleChildScrollView(
                      scrollDirection: Axis.horizontal,
                      child: Row(
                        children: [
                          'Phagwara Market',
                          'Law Gate',
                          'Jalandhar',
                          'Rama Mandi',
                          'Deep Nagar',
                        ].map((chip) {
                          return Padding(
                            padding: const EdgeInsets.only(right: 6),
                            child: InkWell(
                              onTap: () => setState(() => _stayAddressCtrl.text = chip),
                              borderRadius: BorderRadius.circular(4),
                              child: Container(
                                padding: const EdgeInsets.symmetric(horizontal: 7, vertical: 3),
                                decoration: BoxDecoration(
                                  color: SkedColors.slabElevated,
                                  borderRadius: BorderRadius.circular(4),
                                  border: Border.all(color: SkedColors.rule),
                                ),
                                child: Text(
                                  chip,
                                  style: const TextStyle(fontSize: 10.5, color: SkedColors.slate),
                                ),
                              ),
                            ),
                          );
                        }).toList(),
                      ),
                    ),
                    const SizedBox(height: 14),

                    // Relative Mobile Number
                    Text(
                      'RELATIVE / CONTACT MOBILE',
                      style: GoogleFonts.barlowCondensed(
                        fontSize: 11,
                        fontWeight: FontWeight.w700,
                        color: SkedColors.slate,
                      ),
                    ),
                    const SizedBox(height: 4),
                    TextField(
                      controller: _relativeMobileCtrl,
                      keyboardType: TextInputType.phone,
                      inputFormatters: [
                        FilteringTextInputFormatter.digitsOnly,
                        LengthLimitingTextInputFormatter(10),
                      ],
                      style: const TextStyle(color: SkedColors.chalk, fontSize: 13),
                      decoration: const InputDecoration(
                        hintText: '10-digit mobile number',
                      ),
                    ),
                    const SizedBox(height: 16),

                    // Start Date & Time of Leave
                    Text(
                      'START DATE & TIME OF LEAVE',
                      style: GoogleFonts.barlowCondensed(
                        fontSize: 11,
                        fontWeight: FontWeight.w700,
                        color: SkedColors.slate,
                      ),
                    ),
                    const SizedBox(height: 6),
                    Row(
                      children: [
                        Expanded(
                          flex: 11,
                          child: InkWell(
                            onTap: () => _selectDate(isStart: true),
                            borderRadius: BorderRadius.circular(6),
                            child: Container(
                              padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 10),
                              decoration: BoxDecoration(
                                color: SkedColors.slabElevated,
                                borderRadius: BorderRadius.circular(6),
                                border: Border.all(color: SkedColors.rule),
                              ),
                              child: Row(
                                children: [
                                  const Icon(Icons.calendar_today, color: SkedColors.blaze, size: 13),
                                  const SizedBox(width: 6),
                                  Expanded(
                                    child: Text(
                                      _dateFormat.format(_startDateTime),
                                      style: const TextStyle(
                                        fontSize: 12,
                                        fontWeight: FontWeight.bold,
                                        color: SkedColors.chalk,
                                      ),
                                      overflow: TextOverflow.ellipsis,
                                    ),
                                  ),
                                ],
                              ),
                            ),
                          ),
                        ),
                        const SizedBox(width: 8),
                        Expanded(
                          flex: 9,
                          child: InkWell(
                            onTap: () => _showTimeSlotPicker(isStart: true),
                            borderRadius: BorderRadius.circular(6),
                            child: Container(
                              padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 10),
                              decoration: BoxDecoration(
                                color: SkedColors.slabElevated,
                                borderRadius: BorderRadius.circular(6),
                                border: Border.all(color: SkedColors.rule),
                              ),
                              child: Row(
                                children: [
                                  const Icon(Icons.schedule, color: SkedColors.blaze, size: 14),
                                  const SizedBox(width: 6),
                                  Text(
                                    _timeFormat.format(_startDateTime),
                                    style: const TextStyle(
                                      fontSize: 12.5,
                                      fontWeight: FontWeight.bold,
                                      color: SkedColors.chalk,
                                    ),
                                  ),
                                ],
                              ),
                            ),
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 16),

                    // Leave Ending Date Time
                    Text(
                      'LEAVE ENDING DATE TIME',
                      style: GoogleFonts.barlowCondensed(
                        fontSize: 11,
                        fontWeight: FontWeight.w700,
                        color: SkedColors.slate,
                      ),
                    ),
                    const SizedBox(height: 6),
                    Row(
                      children: [
                        Expanded(
                          flex: 11,
                          child: InkWell(
                            onTap: isDayLeave ? null : () => _selectDate(isStart: false),
                            borderRadius: BorderRadius.circular(6),
                            child: Container(
                              padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 10),
                              decoration: BoxDecoration(
                                color: isDayLeave
                                    ? SkedColors.slabElevated.withOpacity(0.5)
                                    : SkedColors.slabElevated,
                                borderRadius: BorderRadius.circular(6),
                                border: Border.all(color: SkedColors.rule),
                              ),
                              child: Row(
                                children: [
                                  Icon(
                                    Icons.calendar_today,
                                    color: isDayLeave ? SkedColors.slate : SkedColors.blaze,
                                    size: 13,
                                  ),
                                  const SizedBox(width: 6),
                                  Expanded(
                                    child: Text(
                                      _dateFormat.format(_endDateTime),
                                      style: TextStyle(
                                        fontSize: 12,
                                        fontWeight: FontWeight.bold,
                                        color: isDayLeave ? SkedColors.slate : SkedColors.chalk,
                                      ),
                                      overflow: TextOverflow.ellipsis,
                                    ),
                                  ),
                                ],
                              ),
                            ),
                          ),
                        ),
                        const SizedBox(width: 8),
                        Expanded(
                          flex: 9,
                          child: InkWell(
                            onTap: () => _showTimeSlotPicker(isStart: false),
                            borderRadius: BorderRadius.circular(6),
                            child: Container(
                              padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 10),
                              decoration: BoxDecoration(
                                color: SkedColors.slabElevated,
                                borderRadius: BorderRadius.circular(6),
                                border: Border.all(color: SkedColors.rule),
                              ),
                              child: Row(
                                children: [
                                  const Icon(Icons.schedule, color: SkedColors.blaze, size: 14),
                                  const SizedBox(width: 6),
                                  Text(
                                    _timeFormat.format(_endDateTime),
                                    style: const TextStyle(
                                      fontSize: 12.5,
                                      fontWeight: FontWeight.bold,
                                      color: SkedColors.chalk,
                                    ),
                                  ),
                                ],
                              ),
                            ),
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 14),

                    // Reason for Leaving
                    Text(
                      'REASON FOR LEAVING',
                      style: GoogleFonts.barlowCondensed(
                        fontSize: 11,
                        fontWeight: FontWeight.w700,
                        color: SkedColors.slate,
                      ),
                    ),
                    const SizedBox(height: 4),
                    TextField(
                      controller: _reasonCtrl,
                      style: const TextStyle(color: SkedColors.chalk, fontSize: 13),
                      decoration: const InputDecoration(
                        hintText: 'e.g. Personal work, Buying books, Market',
                      ),
                    ),

                    // Quick Reason Chips
                    const SizedBox(height: 6),
                    SingleChildScrollView(
                      scrollDirection: Axis.horizontal,
                      child: Row(
                        children: [
                          'Personal work',
                          'Market & Groceries',
                          'Buying Books',
                          'Outing',
                          'Medical',
                        ].map((chip) {
                          return Padding(
                            padding: const EdgeInsets.only(right: 6),
                            child: InkWell(
                              onTap: () => setState(() => _reasonCtrl.text = chip),
                              borderRadius: BorderRadius.circular(4),
                              child: Container(
                                padding: const EdgeInsets.symmetric(horizontal: 7, vertical: 3),
                                decoration: BoxDecoration(
                                  color: SkedColors.slabElevated,
                                  borderRadius: BorderRadius.circular(4),
                                  border: Border.all(color: SkedColors.rule),
                                ),
                                child: Text(
                                  chip,
                                  style: const TextStyle(fontSize: 10.5, color: SkedColors.slate),
                                ),
                              ),
                            ),
                          );
                        }).toList(),
                      ),
                    ),
                    const SizedBox(height: 16),

                    // Declaration Checkbox
                    InkWell(
                      onTap: () => setState(() => _declarationAccepted = !_declarationAccepted),
                      child: Row(
                        children: [
                          Checkbox(
                            value: _declarationAccepted,
                            onChanged: (val) => setState(() => _declarationAccepted = val ?? false),
                            activeColor: SkedColors.blaze,
                            checkColor: SkedColors.ink,
                          ),
                          const SizedBox(width: 4),
                          const Expanded(
                            child: Text(
                              'I acknowledge hostel leave rules & agree to return before curfew.',
                              style: TextStyle(
                                fontSize: 11,
                                color: SkedColors.slate,
                                height: 1.3,
                              ),
                            ),
                          ),
                        ],
                      ),
                    ),
                    const SizedBox(height: 16),

                    // Submit or Feature Coming Card for Parent OTP Users
                    if (_isOtpUser) ...[
                      Container(
                        width: double.infinity,
                        padding: const EdgeInsets.all(14),
                        decoration: BoxDecoration(
                          color: SkedColors.slabElevated,
                          borderRadius: BorderRadius.circular(10),
                          border: Border.all(color: SkedColors.blaze.withOpacity(0.4)),
                        ),
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Row(
                              mainAxisAlignment: MainAxisAlignment.spaceBetween,
                              children: [
                                Container(
                                  padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                                  decoration: BoxDecoration(
                                    color: SkedColors.blaze.withOpacity(0.15),
                                    borderRadius: BorderRadius.circular(4),
                                    border: Border.all(color: SkedColors.blaze, width: 0.5),
                                  ),
                                  child: Text(
                                    'FEATURE COMING SOON',
                                    style: GoogleFonts.barlowCondensed(
                                      fontSize: 11,
                                      fontWeight: FontWeight.w700,
                                      color: SkedColors.blaze,
                                      letterSpacing: 0.5,
                                    ),
                                  ),
                                ),
                                TextButton(
                                  onPressed: () {
                                    setState(() => _isOtpUser = false);
                                    LeaveStorage.setOtpRequired(false);
                                    ScaffoldMessenger.of(context).showSnackBar(
                                      const SnackBar(
                                        content: Text('OTP status reset. You can try direct submission.'),
                                      ),
                                    );
                                  },
                                  child: Text(
                                    'Re-check',
                                    style: GoogleFonts.barlowCondensed(
                                      fontSize: 11,
                                      color: SkedColors.slate,
                                    ),
                                  ),
                                ),
                              ],
                            ),
                            const SizedBox(height: 8),
                            Text(
                              'Parent OTP Verification Required',
                              style: GoogleFonts.barlowCondensed(
                                fontSize: 15,
                                fontWeight: FontWeight.w700,
                                color: SkedColors.chalk,
                              ),
                            ),
                            const SizedBox(height: 4),
                            const Text(
                              'Your hostel account requires Parent/Guardian OTP verification to approve leaves. Direct in-app OTP authorization is coming soon in an upcoming update!\n\nPlease submit via the official UMS portal for now.',
                              style: TextStyle(
                                fontSize: 12,
                                color: SkedColors.slate,
                                height: 1.4,
                              ),
                            ),
                            const SizedBox(height: 12),
                            SizedBox(
                              width: double.infinity,
                              height: 42,
                              child: ElevatedButton.icon(
                                onPressed: _openUmsPortal,
                                icon: const Icon(Icons.open_in_browser, color: SkedColors.ink, size: 18),
                                label: Text(
                                  'OPEN UMS LEAVE PORTAL',
                                  style: GoogleFonts.barlowCondensed(
                                    fontSize: 13,
                                    fontWeight: FontWeight.w700,
                                    color: SkedColors.ink,
                                    letterSpacing: 0.5,
                                  ),
                                ),
                              ),
                            ),
                          ],
                        ),
                      ),
                    ] else ...[
                      SizedBox(
                        width: double.infinity,
                        height: 46,
                        child: ElevatedButton.icon(
                          onPressed: _onAuthorizeAndApply,
                          icon: const Icon(Icons.fingerprint, color: SkedColors.ink, size: 20),
                          label: Text(
                            'AUTHORIZE & APPLY LEAVE',
                            style: GoogleFonts.barlowCondensed(
                              fontSize: 14,
                              fontWeight: FontWeight.w700,
                              color: SkedColors.ink,
                              letterSpacing: 0.5,
                            ),
                          ),
                        ),
                      ),
                    ],
                  ],
                ),
              ),
              const SizedBox(height: 20),

              // ── 3. Leave Records / History ────────────────────────────────────
              if (_records.isNotEmpty) ...[
                Text(
                  'PAST LEAVE APPLICATIONS',
                  style: GoogleFonts.barlowCondensed(
                    fontSize: 13,
                    fontWeight: FontWeight.w700,
                    color: SkedColors.slate,
                    letterSpacing: 0.8,
                  ),
                ),
                const SizedBox(height: 8),
                ..._records.map((rec) {
                  final isApproved = rec.status.toLowerCase().contains('approved');
                  final isRejected = rec.status.toLowerCase().contains('reject');
                  final statusCol = isApproved
                      ? SkedColors.onGoingGreen
                      : isRejected
                          ? SkedColors.dangerRed
                          : SkedColors.blaze;

                  return Container(
                    margin: const EdgeInsets.only(bottom: 10),
                    padding: const EdgeInsets.all(12),
                    decoration: BoxDecoration(
                      color: SkedColors.slab,
                      borderRadius: BorderRadius.circular(8),
                      border: Border.all(color: SkedColors.rule),
                    ),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Row(
                          mainAxisAlignment: MainAxisAlignment.spaceBetween,
                          children: [
                            Text(
                              rec.leaveType.isNotEmpty ? rec.leaveType : 'Hostel Leave',
                              style: GoogleFonts.barlowCondensed(
                                fontSize: 13,
                                fontWeight: FontWeight.w700,
                                color: SkedColors.chalk,
                              ),
                            ),
                            Container(
                              padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                              decoration: BoxDecoration(
                                color: statusCol.withOpacity(0.15),
                                borderRadius: BorderRadius.circular(4),
                                border: Border.all(color: statusCol.withOpacity(0.5)),
                              ),
                              child: Text(
                                rec.status,
                                style: GoogleFonts.barlowCondensed(
                                  fontSize: 10.5,
                                  fontWeight: FontWeight.w700,
                                  color: statusCol,
                                ),
                              ),
                            ),
                          ],
                        ),
                        const SizedBox(height: 6),
                        Row(
                          children: [
                            const Icon(Icons.schedule, size: 12, color: SkedColors.slate),
                            const SizedBox(width: 4),
                            Expanded(
                              child: Text(
                                '${rec.leaveDate} -> ${rec.returnDate}',
                                style: const TextStyle(
                                  fontSize: 11,
                                  color: SkedColors.slate,
                                  fontFamily: 'monospace',
                                ),
                              ),
                            ),
                          ],
                        ),
                        if (rec.leaveId.isNotEmpty) ...[
                          const SizedBox(height: 4),
                          Text(
                            'Leave ID #${rec.leaveId} • Applied ${rec.applyDate}',
                            style: const TextStyle(fontSize: 10.5, color: SkedColors.slate),
                          ),
                        ],
                      ],
                    ),
                  );
                }),
              ],
            ],
          ),

          // ── Parent OTP Intercept Dialog (EXACT MATCH to uploaded screenshot) ──
          if (_showParentOtpDialog)
            GestureDetector(
              onTap: () => setState(() => _showParentOtpDialog = false),
              child: Container(
                color: Colors.black.withOpacity(0.65),
                alignment: Alignment.center,
                padding: const EdgeInsets.all(24),
                child: GestureDetector(
                  onTap: () {}, // Prevent dismissal when tapping inside card
                  child: Container(
                    width: double.infinity,
                    constraints: const BoxConstraints(maxWidth: 360),
                    padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 26),
                    decoration: BoxDecoration(
                      color: SkedColors.slabElevated,
                      borderRadius: BorderRadius.circular(16),
                      border: Border.all(color: SkedColors.rule),
                    ),
                    child: Column(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        const Icon(
                          Icons.lock,
                          color: SkedColors.blaze,
                          size: 32,
                        ),
                        const SizedBox(height: 12),
                        Text(
                          'PARENT OTP REQUIRED',
                          style: GoogleFonts.barlowCondensed(
                            fontSize: 18,
                            fontWeight: FontWeight.w700,
                            color: SkedColors.chalk,
                            letterSpacing: 0.5,
                          ),
                          textAlign: TextAlign.center,
                        ),
                        const SizedBox(height: 14),
                        Container(
                          padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                          decoration: BoxDecoration(
                            color: SkedColors.blaze.withOpacity(0.15),
                            borderRadius: BorderRadius.circular(4),
                            border: Border.all(color: SkedColors.blaze, width: 0.5),
                          ),
                          child: Text(
                            'FEATURE COMING SOON',
                            style: GoogleFonts.barlowCondensed(
                              fontSize: 11,
                              fontWeight: FontWeight.w700,
                              color: SkedColors.blaze,
                              letterSpacing: 0.5,
                            ),
                          ),
                        ),
                        const SizedBox(height: 14),
                        Text(
                          'UMS requires Parent/Guardian OTP verification to approve leave applications for your hostel account${_parentOtpPhoneTarget.isNotEmpty ? " (sent to $_parentOtpPhoneTarget)" : ""}.\n\nDirect in-app OTP authorization is currently in development and coming soon in a future update!\n\nIn the meantime, please submit this leave application via the official UMS portal.',
                          style: const TextStyle(
                            fontSize: 13,
                            color: SkedColors.slate,
                            height: 1.45,
                          ),
                          textAlign: TextAlign.left,
                        ),
                        const SizedBox(height: 20),
                        SizedBox(
                          width: double.infinity,
                          height: 42,
                          child: ElevatedButton.icon(
                            style: ElevatedButton.styleFrom(
                              backgroundColor: SkedColors.blaze,
                              foregroundColor: SkedColors.ink,
                              shape: RoundedRectangleBorder(
                                borderRadius: BorderRadius.circular(6),
                              ),
                            ),
                            onPressed: () {
                              setState(() => _showParentOtpDialog = false);
                              _openUmsPortal();
                            },
                            icon: const Icon(Icons.open_in_browser, color: SkedColors.ink, size: 16),
                            label: Text(
                              'OPEN UMS PORTAL',
                              style: GoogleFonts.barlowCondensed(
                                fontSize: 13,
                                fontWeight: FontWeight.w700,
                                color: SkedColors.ink,
                                letterSpacing: 0.5,
                              ),
                            ),
                          ),
                        ),
                        const SizedBox(height: 10),
                        TextButton(
                          onPressed: () => setState(() => _showParentOtpDialog = false),
                          child: Text(
                            'DISMISS',
                            style: GoogleFonts.barlowCondensed(
                              fontSize: 13,
                              fontWeight: FontWeight.w600,
                              color: SkedColors.slate,
                              letterSpacing: 0.5,
                            ),
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
              ),
            ),
        ],
      ),
    );
  }
}
