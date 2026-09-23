import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'screens/login_screen.dart';
import 'screens/dashboard_screen.dart';
import 'widgets/ums_auth_bridge_sheet.dart';
import 'theme/app_theme.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  SystemChrome.setPreferredOrientations([DeviceOrientation.portraitUp, DeviceOrientation.portraitDown]);
  SystemChrome.setSystemUIOverlayStyle(const SystemUiOverlayStyle(
    statusBarColor: Colors.transparent,
    statusBarBrightness: Brightness.dark,
    statusBarIconBrightness: Brightness.light,
  ));
  runApp(const SkedApp());
}

class SkedApp extends StatelessWidget {
  const SkedApp({super.key});

  @override
  Widget build(BuildContext context) {
    final base = buildSkedTheme();
    return MaterialApp(
      title: 'Sked',
      debugShowCheckedModeBanner: false,
      theme: base.copyWith(
        textTheme: GoogleFonts.interTextTheme(base.textTheme).apply(
          bodyColor: SkedColors.chalk,
          displayColor: SkedColors.chalk,
        ),
      ),
      home: const SkedAuthGate(),
    );
  }
}

class SkedAuthGate extends StatefulWidget {
  const SkedAuthGate({super.key});

  @override
  State<SkedAuthGate> createState() => _SkedAuthGateState();
}

class _SkedAuthGateState extends State<SkedAuthGate> {
  String _currentUserId = '';
  bool _isCheckingAuth = true;

  bool _showBridge = false;
  String _pendingUserId = '';
  String _pendingPassword = '';
  String _loginErrorMessage = '';

  @override
  void initState() {
    super.initState();
    _checkSavedUser();
  }

  Future<void> _checkSavedUser() async {
    final prefs = await SharedPreferences.getInstance();
    final savedId = prefs.getString('sked_user_id') ?? '';
    if (mounted) {
      setState(() {
        _currentUserId = savedId;
        _isCheckingAuth = false;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    if (_isCheckingAuth) {
      return const Scaffold(
        backgroundColor: SkedColors.ink,
        body: Center(
          child: CircularProgressIndicator(
            color: SkedColors.blaze,
            strokeWidth: 2,
          ),
        ),
      );
    }

    if (_showBridge) {
      return UmsAuthBridgeSheet(
        userId: _pendingUserId,
        password: _pendingPassword,
        onDismiss: () => setState(() => _showBridge = false),
        onError: (errMsg) {
          setState(() {
            _showBridge = false;
            _loginErrorMessage = errMsg;
          });
        },
        onSuccess: (syncedUserId) async {
          final prefs = await SharedPreferences.getInstance();
          await prefs.setString('sked_user_id', syncedUserId);
          await prefs.setString('saved_ums_pwd', _pendingPassword);
          if (mounted) {
            setState(() {
              _currentUserId = syncedUserId;
              _loginErrorMessage = '';
              _showBridge = false;
            });
          }
        },
      );
    }

    if (_currentUserId.isEmpty) {
      return LoginScreen(
        initialUserId: _pendingUserId,
        initialErrorMessage: _loginErrorMessage,
        onStartLogin: (uid, pwd) async {
          final prefs = await SharedPreferences.getInstance();
          await prefs.setString('saved_ums_pwd', pwd);
          setState(() {
            _pendingUserId = uid;
            _pendingPassword = pwd;
            _loginErrorMessage = '';
            _showBridge = true;
          });
        },
      );
    }

    return DashboardScreen(
      userId: _currentUserId,
      onLogout: () {
        setState(() {
          _currentUserId = '';
          _pendingUserId = '';
          _pendingPassword = '';
          _loginErrorMessage = '';
        });
      },
    );
  }
}
