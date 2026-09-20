import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:google_fonts/google_fonts.dart';
import '../services/api_service.dart';
import '../theme/app_theme.dart';

class SettingsScreen extends StatefulWidget {
  const SettingsScreen({super.key});

  @override
  State<SettingsScreen> createState() => _SettingsScreenState();
}

class _SettingsScreenState extends State<SettingsScreen> {
  final _cookieCtrl   = TextEditingController();
  final _baseUrlCtrl  = TextEditingController();
  final _termIdCtrl   = TextEditingController();
  final _formKey      = GlobalKey<FormState>();

  bool _testing = false;
  String? _testResult;
  bool _testOk = false;
  bool _obscureCookie = true;

  @override
  void initState() {
    super.initState();
    _loadSaved();
  }

  Future<void> _loadSaved() async {
    _baseUrlCtrl.text = await ApiService.instance.getBaseUrl();
    _cookieCtrl.text  = await ApiService.instance.getSavedCookie();
  }

  @override
  void dispose() {
    _cookieCtrl.dispose();
    _baseUrlCtrl.dispose();
    _termIdCtrl.dispose();
    super.dispose();
  }

  Future<void> _testAndSave() async {
    if (!_formKey.currentState!.validate()) return;
    setState(() { _testing = true; _testResult = null; });

    // Save base URL locally first so getStatus() uses the new one
    await ApiService.instance.setBaseUrl(_baseUrlCtrl.text.trim());

    // Push cookie to backend if provided
    if (_cookieCtrl.text.trim().isNotEmpty) {
      try {
        await ApiService.instance.updateCookie(_cookieCtrl.text.trim());
      } catch (_) {}
    }

    // Push termId if provided
    if (_termIdCtrl.text.trim().isNotEmpty) {
      try {
        await ApiService.instance.updateTermId(_termIdCtrl.text.trim());
      } catch (_) {}
    }

    // Status check
    try {
      final status = await ApiService.instance.getStatus();
      final ok       = status['ok'] == true;
      final cookieOk = status['cookieValid'] == true;
      setState(() {
        _testing  = false;
        _testOk   = ok;
        _testResult = ok
            ? 'Connected. Cookie valid, backend reachable.'
            : cookieOk == false
                ? 'Cookie expired. Please refresh it.'
                : 'Backend reachable but misconfigured.';
      });
    } on ApiException catch (e) {
      setState(() {
        _testing = false;
        _testOk  = false;
        _testResult = e.message;
      });
    } catch (e) {
      setState(() {
        _testing = false;
        _testOk  = false;
        _testResult = e.toString();
      });
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
          'SETTINGS.',
          style: GoogleFonts.barlowCondensed(
            fontSize: 22,
            fontWeight: FontWeight.w700,
            color: SkedColors.chalk,
            letterSpacing: 0.5,
          ),
        ),
      ),
      body: Form(
        key: _formKey,
        child: ListView(
          padding: const EdgeInsets.all(20),
          children: [
            _sectionHeader('BACKEND.'),
            const SizedBox(height: 10),
            TextFormField(
              controller: _baseUrlCtrl,
              decoration: const InputDecoration(
                labelText: 'Backend URL',
                hintText: 'http://192.168.1.100:3000',
                helperText: "Your PC's LAN IP + port 3000",
                prefixIcon: Icon(Icons.link_rounded, color: SkedColors.slate, size: 20),
              ),
              keyboardType: TextInputType.url,
              style: const TextStyle(color: SkedColors.chalk, fontSize: 14),
              validator: (v) {
                if (v == null || v.trim().isEmpty) return 'Required';
                if (!v.startsWith('http')) return 'Must start with http:// or https://';
                return null;
              },
            ),

            const SizedBox(height: 28),
            _sectionHeader('AUTHENTICATION.'),
            const SizedBox(height: 10),
            _cookieInstructions(),
            const SizedBox(height: 12),
            TextFormField(
              controller: _cookieCtrl,
              decoration: InputDecoration(
                labelText: 'Session Cookie',
                hintText: 'ASP.NET_SessionId=abc123...',
                prefixIcon: const Icon(Icons.vpn_key_rounded, color: SkedColors.slate, size: 20),
                suffixIcon: Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    IconButton(
                      icon: Icon(
                        _obscureCookie ? Icons.visibility_rounded : Icons.visibility_off_rounded,
                        color: SkedColors.slate,
                        size: 20,
                      ),
                      onPressed: () => setState(() => _obscureCookie = !_obscureCookie),
                    ),
                    IconButton(
                      icon: const Icon(Icons.paste_rounded, color: SkedColors.slate, size: 20),
                      onPressed: () async {
                        final data = await Clipboard.getData('text/plain');
                        if (data?.text != null) setState(() => _cookieCtrl.text = data!.text!.trim());
                      },
                    ),
                  ],
                ),
              ),
              obscureText: _obscureCookie,
              maxLines: _obscureCookie ? 1 : 3,
              style: const TextStyle(fontFamily: 'monospace', fontSize: 13, color: SkedColors.chalk),
            ),

            const SizedBox(height: 28),
            _sectionHeader('SEMESTER.'),
            const SizedBox(height: 10),
            TextFormField(
              controller: _termIdCtrl,
              decoration: const InputDecoration(
                labelText: 'Term ID (optional)',
                hintText: 'e.g. 12345',
                helperText: 'Leave blank to keep existing value',
                prefixIcon: Icon(Icons.tag_rounded, color: SkedColors.slate, size: 20),
              ),
              keyboardType: TextInputType.number,
              inputFormatters: [FilteringTextInputFormatter.digitsOnly],
              style: const TextStyle(fontFamily: 'monospace', fontSize: 14, color: SkedColors.chalk),
            ),

            const SizedBox(height: 32),
            SizedBox(
              width: double.infinity,
              height: 48,
              child: ElevatedButton(
                onPressed: _testing ? null : _testAndSave,
                child: _testing
                    ? const SizedBox(
                        width: 18,
                        height: 18,
                        child: CircularProgressIndicator(strokeWidth: 2, color: SkedColors.ink),
                      )
                    : const Text('SAVE & TEST.'),
              ),
            ),

            if (_testResult != null) ...[
              const SizedBox(height: 14),
              Container(
                padding: const EdgeInsets.all(12),
                decoration: BoxDecoration(
                  color: SkedColors.slab,
                  borderRadius: BorderRadius.circular(4),
                  border: Border.all(
                    color: _testOk ? SkedColors.blaze : SkedColors.error,
                    width: 1,
                  ),
                ),
                child: Text(
                  _testResult!,
                  style: TextStyle(
                    color: _testOk ? SkedColors.blaze : SkedColors.error,
                    fontSize: 13,
                    fontWeight: FontWeight.w500,
                  ),
                ),
              ),
            ],

            const SizedBox(height: 32),
            _sectionHeader('DANGER ZONE.'),
            const SizedBox(height: 10),
            OutlinedButton(
              onPressed: () async {
                await ApiService.instance.invalidateCache();
                if (mounted) {
                  ScaffoldMessenger.of(context).showSnackBar(
                    const SnackBar(
                      content: Text('Cache cleared — next load re-fetches from UMS'),
                      backgroundColor: SkedColors.slab,
                    ),
                  );
                }
              },
              style: OutlinedButton.styleFrom(
                side: const BorderSide(color: SkedColors.rule, width: 1),
                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(4)),
                padding: const EdgeInsets.symmetric(vertical: 12),
              ),
              child: Text(
                'CLEAR CACHE.',
                style: GoogleFonts.barlowCondensed(
                  fontSize: 14,
                  fontWeight: FontWeight.w700,
                  color: SkedColors.slate,
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _sectionHeader(String label) {
    return Row(
      children: [
        Text(
          label,
          style: GoogleFonts.barlowCondensed(
            fontSize: 14,
            fontWeight: FontWeight.w700,
            color: SkedColors.chalk,
            letterSpacing: 0.5,
          ),
        ),
        const SizedBox(width: 10),
        const Expanded(child: Divider(color: SkedColors.rule, thickness: 1)),
      ],
    );
  }

  Widget _cookieInstructions() {
    return Container(
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: SkedColors.slab,
        borderRadius: BorderRadius.circular(4),
        border: Border.all(color: SkedColors.rule),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: const [
          Text(
            'How to get your cookie:',
            style: TextStyle(fontWeight: FontWeight.w600, fontSize: 12, color: SkedColors.chalk),
          ),
          SizedBox(height: 6),
          Text('1. Open Chrome → Log in to ums.lpu.in manually', style: TextStyle(fontSize: 11, color: SkedColors.slate)),
          Text('2. Press F12 → Application → Cookies → ums.lpu.in', style: TextStyle(fontSize: 11, color: SkedColors.slate)),
          Text('3. Find ASP.NET_SessionId — copy the Value', style: TextStyle(fontSize: 11, color: SkedColors.slate)),
          Text('4. Paste it above as:  ASP.NET_SessionId=<value>', style: TextStyle(fontSize: 11, color: SkedColors.slate)),
        ],
      ),
    );
  }
}
