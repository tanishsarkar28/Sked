import 'dart:async';
import 'dart:typed_data';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:intl/intl.dart';
import 'package:url_launcher/url_launcher.dart';
import 'package:webview_flutter/webview_flutter.dart';
import '../models/leave_models.dart';
import '../services/leave_storage.dart';
import '../services/leave_submit_engine.dart';
import '../theme/app_theme.dart';
import '../widgets/barcode_widget.dart';

enum LeaveSlipMode {
  digitalPass,
  umsPortal,
}

class LeaveSlipScreen extends StatefulWidget {
  final VoidCallback onBack;
  final VoidCallback? onApplyLeave;
  final VoidCallback? onReSync;

  const LeaveSlipScreen({
    super.key,
    required this.onBack,
    this.onApplyLeave,
    this.onReSync,
  });

  @override
  State<LeaveSlipScreen> createState() => _LeaveSlipScreenState();
}

class _LeaveSlipScreenState extends State<LeaveSlipScreen> {
  LeaveSlipMode _selectedMode = LeaveSlipMode.digitalPass;

  HostelGatePass _gatePass = const HostelGatePass();
  LeaveProfile _profile = const LeaveProfile();
  List<LeaveRecord> _records = [];
  Uint8List? _cachedPhotoBytes;

  bool _isFetchingSlip = false;

  late Timer _clockTimer;
  String _liveTimeText = '';
  String _liveDateText = '';

  late final WebViewController _portalWebViewController;

  @override
  void initState() {
    super.initState();
    _startLiveClock();
    _loadStoredData();
    _initPortalWebView();
    _fetchGatePassData();
  }

  void _startLiveClock() {
    void updateTime() {
      final now = DateTime.now();
      setState(() {
        _liveTimeText = DateFormat('hh:mm:ss a').format(now).toUpperCase();
        _liveDateText = DateFormat('EEEE, MMMM d, yyyy').format(now);
      });
    }

    updateTime();
    _clockTimer = Timer.periodic(const Duration(seconds: 1), (_) => updateTime());
  }

  void _initPortalWebView() {
    _portalWebViewController = WebViewController()
      ..setJavaScriptMode(JavaScriptMode.unrestricted)
      ..setUserAgent(LeaveSubmitEngine.defaultUserAgent)
      ..loadRequest(Uri.parse(LeaveSubmitEngine.leaveSlipUrl));
  }

  @override
  void dispose() {
    _clockTimer.cancel();
    super.dispose();
  }

  Future<void> _loadStoredData() async {
    final pass = await LeaveStorage.loadGatePass();
    final prof = await LeaveStorage.loadProfile();
    final recs = await LeaveStorage.loadRecords();
    final photo = await LeaveSubmitEngine.getCachedPhotoBytes();

    if (mounted) {
      setState(() {
        _gatePass = pass;
        _profile = prof;
        _records = recs;
        _cachedPhotoBytes = photo;
      });
    }
  }

  Future<void> _fetchGatePassData() async {
    if (_isFetchingSlip) return;
    setState(() => _isFetchingSlip = true);

    try {
      final pass = await LeaveSubmitEngine.fetchGatePass();
      final syncRes = await LeaveSubmitEngine.fetchLatestData();
      final photo = await LeaveSubmitEngine.getCachedPhotoBytes();

      if (!mounted) return;

      setState(() {
        _cachedPhotoBytes = photo;
        if (pass != null) {
          final isExpired = pass.dateOfReturn.isNotEmpty && isLeaveExpired(pass.dateOfReturn, 0);
          final isCheckInDone = pass.checkInTime.isNotEmpty;
          final umsHasNoSlip = !pass.hasActivePass || pass.leaveCode == '6000' || pass.leaveId == '0' || pass.leaveId.isEmpty;

          if (umsHasNoSlip || isExpired || isCheckInDone) {
            _gatePass = const HostelGatePass();
          } else {
            _gatePass = pass;
          }
        }
        if (syncRes is LeaveSyncSuccess) {
          _records = syncRes.data.records;
          if (syncRes.data.profile.name.isNotEmpty) {
            _profile = syncRes.data.profile;
          }
        }
      });
    } catch (_) {} finally {
      if (mounted) setState(() => _isFetchingSlip = false);
    }
  }

  HostelGatePass _getEffectiveGatePass() {
    final isPassActive = _gatePass.hasActivePass &&
        _gatePass.leaveId.isNotEmpty &&
        _gatePass.leaveId != '0' &&
        _gatePass.leaveId != '6000' &&
        _gatePass.leaveCode != '6000';
    final isPassStaleOrDone = _gatePass.checkInTime.isNotEmpty ||
        (_gatePass.dateOfReturn.isNotEmpty && isLeaveExpired(_gatePass.dateOfReturn, 0));

    if (isPassStaleOrDone) {
      return const HostelGatePass();
    }

    final approvedRecord = _records.cast<LeaveRecord?>().firstWhere(
      (r) {
        if (r == null) return false;
        if (!r.status.toLowerCase().contains('approved')) return false;
        if (r.checkIn.isNotEmpty) return false;
        if (r.returnDate.isNotEmpty && isLeaveExpired(r.returnDate, 0)) return false;
        return true;
      },
      orElse: () => null,
    );

    if (approvedRecord != null) {
      final approvedId = int.tryParse(approvedRecord.leaveId) ?? 0;
      final currentPassId = int.tryParse(_gatePass.leaveId) ?? 0;
      if (!isPassActive || approvedId > currentPassId) {
        return HostelGatePass(
          universityTitle: 'Lovely Professional University',
          passSubtitle: 'Hostel Leave Gate Pass',
          leaveType: approvedRecord.leaveType.isNotEmpty
              ? approvedRecord.leaveType
              : 'Hostel Leave',
          leaveId: approvedRecord.leaveId,
          leaveCode: (_gatePass.leaveCode.isNotEmpty &&
                  _gatePass.leaveCode != '6000' &&
                  _gatePass.leaveId == approvedRecord.leaveId)
              ? _gatePass.leaveCode
              : approvedRecord.leaveId,
          regNo: _profile.uid.isNotEmpty ? _profile.uid : _gatePass.regNo,
          name: _profile.name.isNotEmpty ? _profile.name : _gatePass.name,
          dateOfApply: approvedRecord.applyDate.isNotEmpty
              ? approvedRecord.applyDate
              : _gatePass.dateOfApply,
          dateOfLeaving: approvedRecord.leaveDate.isNotEmpty
              ? approvedRecord.leaveDate
              : _gatePass.dateOfLeaving,
          dateOfReturn: approvedRecord.returnDate.isNotEmpty
              ? approvedRecord.returnDate
              : _gatePass.dateOfReturn,
          checkOutTime: approvedRecord.checkOut.isNotEmpty
              ? approvedRecord.checkOut
              : _gatePass.checkOutTime,
          checkInTime: approvedRecord.checkIn,
          contactNo: _profile.registeredMobile.isNotEmpty
              ? _profile.registeredMobile
              : _gatePass.contactNo,
          hostel: _profile.hostel.isNotEmpty ? _profile.hostel : _gatePass.hostel,
          roomNo: _profile.roomNo.isNotEmpty ? _profile.roomNo : _gatePass.roomNo,
          gates: _gatePass.gates.isNotEmpty
              ? _gatePass.gates
              : const [
                  GateActivation(gateName: 'Main Gate', status: '✔ Activated'),
                  GateActivation(gateName: 'Hostel Gate', status: '✔ Activated'),
                ],
          note: 'Note : Do not look back after checkout; please proceed forward only.',
          hasActivePass: true,
        );
      }
    }

    if (isPassActive) {
      if (_gatePass.name.isEmpty && _profile.name.isNotEmpty) {
        return HostelGatePass(
          universityTitle: _gatePass.universityTitle,
          passSubtitle: _gatePass.passSubtitle,
          leaveType: _gatePass.leaveType,
          leaveId: _gatePass.leaveId,
          leaveCode: _gatePass.leaveCode,
          regNo: _gatePass.regNo.isNotEmpty ? _gatePass.regNo : _profile.uid,
          name: _profile.name,
          dateOfApply: _gatePass.dateOfApply,
          dateOfLeaving: _gatePass.dateOfLeaving,
          dateOfReturn: _gatePass.dateOfReturn,
          checkOutTime: _gatePass.checkOutTime,
          checkInTime: _gatePass.checkInTime,
          contactNo: _gatePass.contactNo.isNotEmpty ? _gatePass.contactNo : _profile.registeredMobile,
          hostel: _gatePass.hostel.isNotEmpty ? _gatePass.hostel : _profile.hostel,
          roomNo: _gatePass.roomNo.isNotEmpty ? _gatePass.roomNo : _profile.roomNo,
          gates: _gatePass.gates,
          note: _gatePass.note,
          hasActivePass: true,
          photoUrl: _gatePass.photoUrl,
        );
      }
      return _gatePass;
    }

    return const HostelGatePass();
  }

  @override
  Widget build(BuildContext context) {
    final effectivePass = _getEffectiveGatePass();
    final isExpired = effectivePass.dateOfReturn.isNotEmpty &&
        isLeaveExpired(effectivePass.dateOfReturn, 0);
    final isCheckInDone = effectivePass.checkInTime.isNotEmpty;
    final isCheckedOut = effectivePass.checkOutTime.isNotEmpty && !isCheckInDone;
    final hasValidSlip = effectivePass.hasActivePass &&
        (effectivePass.leaveId.isNotEmpty || effectivePass.leaveCode.isNotEmpty) &&
        effectivePass.leaveId != '0' &&
        effectivePass.leaveId != '6000' &&
        effectivePass.leaveCode != '6000' &&
        !isExpired &&
        !isCheckInDone;

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
              '• GATE PASS',
              style: GoogleFonts.barlowCondensed(
                fontSize: 16,
                fontWeight: FontWeight.w700,
                color: SkedColors.slate,
              ),
            ),
          ],
        ),
        actions: [
          // FETCH SLIP BUTTON
          InkWell(
            onTap: _isFetchingSlip ? null : _fetchGatePassData,
            borderRadius: BorderRadius.circular(6),
            child: Container(
              margin: const EdgeInsets.symmetric(vertical: 10, horizontal: 4),
              padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
              decoration: BoxDecoration(
                color: SkedColors.blaze,
                borderRadius: BorderRadius.circular(6),
              ),
              child: Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  if (_isFetchingSlip) ...[
                    const SizedBox(
                      width: 12,
                      height: 12,
                      child: CircularProgressIndicator(
                        strokeWidth: 1.5,
                        color: SkedColors.ink,
                      ),
                    ),
                    const SizedBox(width: 5),
                  ] else ...[
                    const Icon(Icons.refresh, color: SkedColors.ink, size: 14),
                    const SizedBox(width: 4),
                  ],
                  Text(
                    'FETCH SLIP',
                    style: GoogleFonts.barlowCondensed(
                      fontSize: 12,
                      fontWeight: FontWeight.w700,
                      color: SkedColors.ink,
                      letterSpacing: 0.5,
                    ),
                  ),
                ],
              ),
            ),
          ),
          IconButton(
            tooltip: 'Open in Browser',
            icon: const Icon(Icons.open_in_new, color: SkedColors.slate, size: 20),
            onPressed: () async {
              final url = Uri.parse(LeaveSubmitEngine.leaveSlipUrl);
              try {
                await launchUrl(url, mode: LaunchMode.externalApplication);
              } catch (_) {}
            },
          ),
        ],
      ),
      body: Column(
        children: [
          // Mode Selector: [ DIGITAL GATE PASS ]  [ OFFICIAL UMS SLIP ]
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
            child: Container(
              height: 38,
              padding: const EdgeInsets.all(3),
              decoration: BoxDecoration(
                color: SkedColors.slab,
                borderRadius: BorderRadius.circular(8),
                border: Border.all(color: SkedColors.rule),
              ),
              child: Row(
                children: [
                  Expanded(
                    child: InkWell(
                      onTap: () => setState(() => _selectedMode = LeaveSlipMode.digitalPass),
                      borderRadius: BorderRadius.circular(6),
                      child: Container(
                        decoration: BoxDecoration(
                          color: _selectedMode == LeaveSlipMode.digitalPass
                              ? SkedColors.blaze
                              : Colors.transparent,
                          borderRadius: BorderRadius.circular(6),
                        ),
                        alignment: Alignment.Center,
                        child: Row(
                          mainAxisAlignment: MainAxisAlignment.center,
                          children: [
                            Icon(
                              Icons.qr_code,
                              size: 14,
                              color: _selectedMode == LeaveSlipMode.digitalPass
                                  ? SkedColors.ink
                                  : SkedColors.slate,
                            ),
                            const SizedBox(width: 6),
                            Text(
                              'DIGITAL GATE PASS',
                              style: GoogleFonts.barlowCondensed(
                                fontSize: 12,
                                fontWeight: FontWeight.w700,
                                color: _selectedMode == LeaveSlipMode.digitalPass
                                  ? SkedColors.ink
                                  : SkedColors.chalk,
                                letterSpacing: 0.5,
                              ),
                            ),
                          ],
                        ),
                      ),
                    ),
                  ),
                  const SizedBox(width: 4),
                  Expanded(
                    child: InkWell(
                      onTap: () => setState(() => _selectedMode = LeaveSlipMode.umsPortal),
                      borderRadius: BorderRadius.circular(6),
                      child: Container(
                        decoration: BoxDecoration(
                          color: _selectedMode == LeaveSlipMode.umsPortal
                              ? SkedColors.blaze
                              : Colors.transparent,
                          borderRadius: BorderRadius.circular(6),
                        ),
                        alignment: Alignment.Center,
                        child: Row(
                          mainAxisAlignment: MainAxisAlignment.center,
                          children: [
                            Icon(
                              Icons.shield,
                              size: 14,
                              color: _selectedMode == LeaveSlipMode.umsPortal
                                  ? SkedColors.ink
                                  : SkedColors.slate,
                            ),
                            const SizedBox(width: 6),
                            Text(
                              'OFFICIAL UMS SLIP',
                              style: GoogleFonts.barlowCondensed(
                                fontSize: 12,
                                fontWeight: FontWeight.w700,
                                color: _selectedMode == LeaveSlipMode.umsPortal
                                  ? SkedColors.ink
                                  : SkedColors.chalk,
                                letterSpacing: 0.5,
                              ),
                            ),
                          ],
                        ),
                      ),
                    ),
                  ),
                ],
              ),
            ),
          ),
          const Divider(color: SkedColors.rule, height: 1),

          // Main Content
          Expanded(
            child: _selectedMode == LeaveSlipMode.umsPortal
                ? WebViewWidget(controller: _portalWebViewController)
                : _buildDigitalPassView(
                    effectivePass: effectivePass,
                    hasValidSlip: hasValidSlip,
                    isExpired: isExpired,
                    isCheckInDone: isCheckInDone,
                    isCheckedOut: isCheckedOut,
                  ),
          ),
        ],
      ),
    );
  }

  Widget _buildDigitalPassView({
    required HostelGatePass effectivePass,
    required bool hasValidSlip,
    required bool isExpired,
    required bool isCheckInDone,
    required bool isCheckedOut,
  }) {
    if (!hasValidSlip) {
      // Empty state
      final statusCol = isExpired ? SkedColors.dangerRed : SkedColors.slate;
      final statusLabel = isExpired
          ? 'LEAVE EXPIRED • RETURN TIME PASSED'
          : isCheckInDone
              ? 'CHECKED IN • COMPLETED'
              : 'NO ACTIVE GATE PASS';

      return ListView(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 16),
        children: [
          Center(
            child: Container(
              padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 6),
              decoration: BoxDecoration(
                color: statusCol.withOpacity(0.12),
                borderRadius: BorderRadius.circular(24),
                border: Border.all(color: statusCol.withOpacity(0.4)),
              ),
              child: Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Container(
                    width: 8,
                    height: 8,
                    decoration: BoxDecoration(
                      color: statusCol,
                      shape: BoxShape.circle,
                    ),
                  ),
                  const SizedBox(width: 8),
                  Text(
                    statusLabel,
                    style: GoogleFonts.barlowCondensed(
                      fontSize: 12,
                      fontWeight: FontWeight.w700,
                      color: statusCol,
                      letterSpacing: 0.8,
                    ),
                  ),
                ],
              ),
            ),
          ),
          const SizedBox(height: 10),

          // Live Ticking Clock
          Center(
            child: Column(
              children: [
                Text(
                  _liveTimeText.isNotEmpty ? _liveTimeText : '00:00:00 AM',
                  style: const TextStyle(
                    fontFamily: 'monospace',
                    fontSize: 24,
                    fontWeight: FontWeight.bold,
                    color: SkedColors.chalk,
                    letterSpacing: 1,
                  ),
                ),
                Text(
                  _liveDateText.isNotEmpty ? _liveDateText : 'Today',
                  style: const TextStyle(fontSize: 11.5, color: SkedColors.slate),
                ),
              ],
            ),
          ),
          const SizedBox(height: 20),

          // Large Empty State Card
          Container(
            padding: const EdgeInsets.all(22),
            decoration: BoxDecoration(
              color: SkedColors.slab,
              borderRadius: BorderRadius.circular(12),
              border: Border.all(color: SkedColors.rule),
            ),
            child: Column(
              children: [
                Icon(
                  isExpired ? Icons.schedule : Icons.shield,
                  color: isExpired ? SkedColors.dangerRed : SkedColors.blaze,
                  size: 46,
                ),
                const SizedBox(height: 14),
                Text(
                  isExpired
                      ? 'Gate Pass Expired'
                      : isCheckInDone
                          ? 'Leave Completed'
                          : 'No Active Gate Pass',
                  style: GoogleFonts.barlowCondensed(
                    fontSize: 20,
                    fontWeight: FontWeight.w700,
                    color: SkedColors.chalk,
                  ),
                ),
                const SizedBox(height: 6),
                Text(
                  isExpired
                      ? 'Your leave gate pass expired after scheduled return time (${effectivePass.dateOfReturn}). It has been removed from active view.'
                      : isCheckInDone
                          ? 'You checked in at ${effectivePass.checkInTime}. This gate pass is now completed and closed.'
                          : 'There is currently no approved or active hostel gate pass on your UMS portal.',
                  style: const TextStyle(fontSize: 12.5, color: SkedColors.slate, height: 1.4),
                  textAlign: TextAlign.center,
                ),
                const SizedBox(height: 20),
                SizedBox(
                  width: double.infinity,
                  height: 44,
                  child: ElevatedButton.icon(
                    onPressed: _isFetchingSlip ? null : _fetchGatePassData,
                    icon: const Icon(Icons.refresh, color: SkedColors.ink, size: 16),
                    label: Text(
                      'FETCH SLIP FROM UMS',
                      style: GoogleFonts.barlowCondensed(
                        fontSize: 13,
                        fontWeight: FontWeight.w700,
                        color: SkedColors.ink,
                        letterSpacing: 0.5,
                      ),
                    ),
                  ),
                ),
                if (widget.onApplyLeave != null) ...[
                  const SizedBox(height: 10),
                  SizedBox(
                    width: double.infinity,
                    height: 44,
                    child: OutlinedButton(
                      style: OutlinedButton.styleFrom(
                        side: const BorderSide(color: SkedColors.rule),
                        backgroundColor: SkedColors.slabElevated,
                        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(6)),
                      ),
                      onPressed: widget.onApplyLeave,
                      child: Text(
                        'APPLY NEW LEAVE',
                        style: GoogleFonts.barlowCondensed(
                          fontSize: 13,
                          fontWeight: FontWeight.w700,
                          color: SkedColors.chalk,
                          letterSpacing: 0.5,
                        ),
                      ),
                    ),
                  ),
                ],
                const SizedBox(height: 10),
                TextButton(
                  onPressed: () => setState(() => _selectedMode = LeaveSlipMode.umsPortal),
                  child: Text(
                    'VIEW OFFICIAL UMS SLIP PORTAL',
                    style: GoogleFonts.barlowCondensed(
                      fontSize: 12,
                      fontWeight: FontWeight.w600,
                      color: SkedColors.slate,
                      letterSpacing: 0.5,
                    ),
                  ),
                ),
              ],
            ),
          ),
        ],
      );
    }

    // Active gate pass presentation
    final statusCol = isExpired
        ? SkedColors.dangerRed
        : isCheckedOut
            ? SkedColors.blaze
            : SkedColors.onGoingGreen;
    final statusLabel = isExpired
        ? 'RETURN TIME PASSED • PLEASE CHECK IN AT GATE'
        : isCheckedOut
            ? 'CHECKED OUT • ON LEAVE'
            : 'APPROVED • READY FOR GATE';

    final leaveCode = effectivePass.leaveCode.isNotEmpty
        ? effectivePass.leaveCode
        : effectivePass.leaveId;

    return ListView(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
      children: [
        // Status Pill with Live Pulsing Dot
        Center(
          child: Container(
            padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 6),
            decoration: BoxDecoration(
              color: statusCol.withOpacity(0.12),
              borderRadius: BorderRadius.circular(24),
              border: Border.all(color: statusCol.withOpacity(0.4)),
            ),
            child: Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                Container(
                  width: 8,
                  height: 8,
                  decoration: BoxDecoration(
                    color: statusCol,
                    shape: BoxShape.circle,
                  ),
                ),
                const SizedBox(width: 8),
                Text(
                  statusLabel,
                  style: GoogleFonts.barlowCondensed(
                    fontSize: 12,
                    fontWeight: FontWeight.w700,
                    color: statusCol,
                    letterSpacing: 0.8,
                  ),
                ),
              ],
            ),
          ),
        ),
        const SizedBox(height: 8),

        // Live Ticking Clock (Warden anti-screenshot proof)
        Center(
          child: Column(
            children: [
              Text(
                _liveTimeText.isNotEmpty ? _liveTimeText : '00:00:00 AM',
                style: const TextStyle(
                  fontFamily: 'monospace',
                  fontSize: 24,
                  fontWeight: FontWeight.bold,
                  color: SkedColors.chalk,
                  letterSpacing: 1,
                ),
              ),
              Text(
                _liveDateText.isNotEmpty ? _liveDateText : 'Today',
                style: const TextStyle(fontSize: 11.5, color: SkedColors.slate),
              ),
            ],
          ),
        ),
        const SizedBox(height: 14),

        // ── OFFICIAL UMS HOSTEL LEAVE GATE PASS (WHITE SLIP) ────────────────
        Container(
          decoration: BoxDecoration(
            color: Colors.white,
            borderRadius: BorderRadius.circular(4),
            border: Border.all(color: const Color(0xFF222222)),
            boxShadow: [
              BoxShadow(
                color: Colors.black.withOpacity(0.3),
                blurRadius: 8,
                offset: const Offset(0, 4),
              ),
            ],
          ),
          padding: const EdgeInsets.all(14),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              // Header Row: University Title + Student Photo
              Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Expanded(
                    child: Column(
                      children: [
                        Text(
                          effectivePass.universityTitle,
                          style: const TextStyle(
                            fontSize: 15,
                            fontWeight: FontWeight.bold,
                            color: Colors.black,
                          ),
                          textAlign: TextAlign.center,
                        ),
                        const SizedBox(height: 2),
                        Text(
                          effectivePass.passSubtitle,
                          style: const TextStyle(
                            fontSize: 12.5,
                            color: Color(0xFF222222),
                          ),
                          textAlign: TextAlign.center,
                        ),
                        const SizedBox(height: 2),
                        Text(
                          effectivePass.leaveType,
                          style: const TextStyle(
                            fontSize: 12,
                            color: Color(0xFF222222),
                          ),
                          textAlign: TextAlign.center,
                        ),
                      ],
                    ),
                  ),
                  const SizedBox(width: 8),

                  // Student Photo (49px x 56px in official slip)
                  Container(
                    width: 52,
                    height: 60,
                    decoration: BoxDecoration(
                      border: Border.all(color: const Color(0xFF888888)),
                      borderRadius: BorderRadius.circular(2),
                    ),
                    clipBehavior: Clip.antiAlias,
                    child: _cachedPhotoBytes != null
                        ? Image.memory(
                            _cachedPhotoBytes!,
                            fit: BoxFit.cover,
                          )
                        : Container(
                            color: const Color(0xFFEEEEEE),
                            alignment: Alignment.Center,
                            child: const Icon(Icons.person, color: Colors.grey, size: 28),
                          ),
                  ),
                ],
              ),
              const SizedBox(height: 14),

              // Barcode Container (Code 128 encoding leaveCode)
              Column(
                children: [
                  Code128BarcodeWidget(
                    code: leaveCode,
                    height: 70,
                    barColor: Colors.black,
                    backgroundColor: Colors.white,
                  ),
                  const SizedBox(height: 4),
                  Text(
                    leaveCode,
                    style: const TextStyle(
                      fontFamily: 'monospace',
                      fontSize: 13,
                      fontWeight: FontWeight.bold,
                      color: Colors.black,
                      letterSpacing: 2,
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 12),
              const Divider(color: Color(0xFFCCCCCC), thickness: 1),
              const SizedBox(height: 8),

              // Details Table
              _passDetailRow('Registration No.', effectivePass.regNo),
              _passDetailRow('Student Name', effectivePass.name),
              _passDetailRow('Date of Apply', effectivePass.dateOfApply),
              _passDetailRow('Date of Leaving', effectivePass.dateOfLeaving),
              _passDetailRow('Date of Return', effectivePass.dateOfReturn),
              _passDetailRow('Check Out Time', effectivePass.checkOutTime.isNotEmpty ? effectivePass.checkOutTime : '--'),
              _passDetailRow('Check In Time', effectivePass.checkInTime.isNotEmpty ? effectivePass.checkInTime : '--'),
              _passDetailRow('Contact No.', effectivePass.contactNo),
              _passDetailRow('Hostel', effectivePass.hostel),
              _passDetailRow('Room No.', effectivePass.roomNo),

              const SizedBox(height: 10),
              const Divider(color: Color(0xFFCCCCCC), thickness: 1),
              const SizedBox(height: 8),

              // Gate Status Badges
              if (effectivePass.gates.isNotEmpty) ...[
                const Text(
                  'GATE ACTIVATION STATUS',
                  style: TextStyle(
                    fontSize: 10.5,
                    fontWeight: FontWeight.bold,
                    color: Color(0xFF555555),
                    letterSpacing: 0.5,
                  ),
                ),
                const SizedBox(height: 6),
                Wrap(
                  spacing: 6,
                  runSpacing: 6,
                  children: effectivePass.gates.map((g) {
                    return Container(
                      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                      decoration: BoxDecoration(
                        color: const Color(0xFFE8F5E9),
                        borderRadius: BorderRadius.circular(4),
                        border: Border.all(color: const Color(0xFFA5D6A7)),
                      ),
                      child: Text(
                        '${g.gateName}: ${g.status}',
                        style: const TextStyle(
                          fontSize: 11,
                          fontWeight: FontWeight.w600,
                          color: Color(0xFF2E7D32),
                        ),
                      ),
                    );
                  }).toList(),
                ),
                const SizedBox(height: 10),
              ],

              // Official Note
              Text(
                effectivePass.note,
                style: const TextStyle(
                  fontSize: 10,
                  fontStyle: FontStyle.italic,
                  color: Color(0xFF666666),
                ),
                textAlign: TextAlign.center,
              ),
            ],
          ),
        ),
        const SizedBox(height: 24),
      ],
    );
  }

  Widget _passDetailRow(String label, String value) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 3),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          SizedBox(
            width: 120,
            child: Text(
              label,
              style: const TextStyle(
                fontSize: 12,
                color: Color(0xFF555555),
              ),
            ),
          ),
          const Text(
            ': ',
            style: TextStyle(fontSize: 12, color: Color(0xFF555555)),
          ),
          Expanded(
            child: Text(
              value.isNotEmpty ? value : '--',
              style: const TextStyle(
                fontSize: 12,
                fontWeight: FontWeight.bold,
                color: Colors.black,
              ),
            ),
          ),
        ],
      ),
    );
  }
}
