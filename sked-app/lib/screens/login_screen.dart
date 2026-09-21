import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:google_fonts/google_fonts.dart';
import '../theme/app_theme.dart';
import '../widgets/about_developer_dialog.dart';

class LoginScreen extends StatefulWidget {
  final void Function(String userId, String password) onStartLogin;

  const LoginScreen({
    super.key,
    required this.onStartLogin,
  });

  @override
  State<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends State<LoginScreen> {
  final _userIdCtrl = TextEditingController();
  final _passwordCtrl = TextEditingController();
  bool _passwordVisible = false;
  String _errorMessage = '';

  @override
  void dispose() {
    _userIdCtrl.dispose();
    _passwordCtrl.dispose();
    super.dispose();
  }

  void _handleSubmit() {
    final uid = _userIdCtrl.text.trim();
    final pwd = _passwordCtrl.text;

    if (uid.isEmpty) {
      setState(() => _errorMessage = 'Please enter your registration number');
      return;
    }
    if (pwd.isEmpty) {
      setState(() => _errorMessage = 'Please enter your UMS password');
      return;
    }

    setState(() => _errorMessage = '');
    widget.onStartLogin(uid, pwd);
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: SkedColors.ink,
      body: SafeArea(
        child: Center(
          child: SingleChildScrollView(
            padding: const EdgeInsets.symmetric(horizontal: 28),
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              crossAxisAlignment: CrossAxisAlignment.center,
              children: [
                const SizedBox(height: 24),

                // SKED. Wordmark Logo
                Text(
                  'SKED.',
                  style: GoogleFonts.barlowCondensed(
                    fontSize: 48,
                    fontWeight: FontWeight.w700,
                    color: SkedColors.blaze,
                    letterSpacing: 1.0,
                  ),
                ),

                const SizedBox(height: 36),

                // Sign-in card
                Container(
                  width: double.infinity,
                  padding: const EdgeInsets.all(20),
                  decoration: BoxDecoration(
                    color: SkedColors.slab,
                    borderRadius: BorderRadius.circular(6),
                    border: Border.all(color: SkedColors.rule),
                  ),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        'SIGN IN.',
                        style: GoogleFonts.barlowCondensed(
                          fontSize: 18,
                          fontWeight: FontWeight.w700,
                          color: SkedColors.chalk,
                        ),
                      ),
                      const SizedBox(height: 4),
                      const Text(
                        'Enter your UMS credentials to sync your class schedule.',
                        style: TextStyle(
                          fontSize: 13,
                          color: SkedColors.slate,
                          height: 1.4,
                        ),
                      ),
                      const SizedBox(height: 20),

                      // Registration Number
                      TextField(
                        controller: _userIdCtrl,
                        keyboardType: TextInputType.number,
                        inputFormatters: [FilteringTextInputFormatter.digitsOnly],
                        style: const TextStyle(
                          fontFamily: 'monospace',
                          fontWeight: FontWeight.w600,
                          fontSize: 15,
                          color: SkedColors.chalk,
                        ),
                        onChanged: (_) {
                          if (_errorMessage.isNotEmpty) {
                            setState(() => _errorMessage = '');
                          }
                        },
                        decoration: const InputDecoration(
                          labelText: 'Registration Number',
                          hintText: 'e.g. 1240xxxx',
                          prefixIcon: Icon(Icons.badge_outlined, color: SkedColors.slate, size: 20),
                        ),
                      ),

                      const SizedBox(height: 14),

                      // Password
                      TextField(
                        controller: _passwordCtrl,
                        obscureText: !_passwordVisible,
                        style: const TextStyle(
                          fontSize: 15,
                          color: SkedColors.chalk,
                        ),
                        onChanged: (_) {
                          if (_errorMessage.isNotEmpty) {
                            setState(() => _errorMessage = '');
                          }
                        },
                        decoration: InputDecoration(
                          labelText: 'UMS Password',
                          hintText: 'Enter your password',
                          prefixIcon: const Icon(Icons.lock_outline, color: SkedColors.slate, size: 20),
                          suffixIcon: IconButton(
                            icon: Icon(
                              _passwordVisible ? Icons.visibility_outlined : Icons.visibility_off_outlined,
                              color: SkedColors.slate,
                              size: 20,
                            ),
                            onPressed: () => setState(() => _passwordVisible = !_passwordVisible),
                          ),
                        ),
                      ),

                      // Error message banner
                      if (_errorMessage.isNotEmpty) ...[
                        const SizedBox(height: 12),
                        Container(
                          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
                          decoration: BoxDecoration(
                            color: SkedColors.blaze.withOpacity(0.12),
                            borderRadius: BorderRadius.circular(4),
                            border: Border.all(color: SkedColors.blaze.withOpacity(0.3)),
                          ),
                          child: Row(
                            children: [
                              const Icon(Icons.error_outline, color: SkedColors.blaze, size: 16),
                              const SizedBox(width: 8),
                              Expanded(
                                child: Text(
                                  _errorMessage,
                                  style: const TextStyle(
                                    fontSize: 12,
                                    color: SkedColors.blaze,
                                  ),
                                ),
                              ),
                            ],
                          ),
                        ),
                      ],

                      const SizedBox(height: 22),

                      // SIGN IN Button
                      SizedBox(
                        width: double.infinity,
                        height: 48,
                        child: ElevatedButton(
                          onPressed: _handleSubmit,
                          child: Text(
                            'SIGN IN.',
                            style: GoogleFonts.barlowCondensed(
                              fontSize: 16,
                              fontWeight: FontWeight.w700,
                              color: SkedColors.ink,
                              letterSpacing: 0.5,
                            ),
                          ),
                        ),
                      ),

                      const SizedBox(height: 14),

                      // Trust Notice
                      Row(
                        mainAxisAlignment: MainAxisAlignment.center,
                        children: const [
                          Icon(Icons.security, color: SkedColors.slate, size: 13),
                          SizedBox(width: 6),
                          Text(
                            'On-device only. Never leaves your phone.',
                            style: TextStyle(
                              fontSize: 11,
                              color: SkedColors.slate,
                            ),
                          ),
                        ],
                      ),
                    ],
                  ),
                ),

                const SizedBox(height: 24),

                // Built by footer button
                InkWell(
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
                      'BUILT BY TANISH SARKAR',
                      style: GoogleFonts.barlowCondensed(
                        fontSize: 11,
                        fontWeight: FontWeight.w700,
                        letterSpacing: 0.5,
                        color: SkedColors.slate,
                      ),
                    ),
                  ),
                ),

                const SizedBox(height: 24),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
