import 'dart:convert';
import 'package:flutter/material.dart';
import 'package:http/http.dart' as http;
import 'package:google_fonts/google_fonts.dart';
import 'package:url_launcher/url_launcher.dart';
import '../theme/app_theme.dart';

class AppUpdateInfo {
  final int versionCode;
  final String versionName;
  final String releaseNotes;
  final String ipaUrl;
  final String directDownloadUrl;
  final String fileSize;

  const AppUpdateInfo({
    required this.versionCode,
    required this.versionName,
    required this.releaseNotes,
    required this.ipaUrl,
    required this.directDownloadUrl,
    required this.fileSize,
  });
}

class UpdateService {
  static const int currentVersionCode = 4;
  static const String currentVersionName = '1.2.1';

  static const List<String> versionUrls = [
    'https://sked-gold.vercel.app/version.json',
    'https://raw.githubusercontent.com/tanishsarkar28/Sked/main/sked-web/public/version.json',
  ];

  static Future<AppUpdateInfo?> checkForUpdate() async {
    for (final url in versionUrls) {
      try {
        final res = await http.get(Uri.parse(url)).timeout(const Duration(seconds: 5));
        if (res.statusCode == 200) {
          final data = jsonDecode(res.body) as Map<String, dynamic>;
          final remoteCode = data['versionCode'] as int? ?? 1;
          final remoteName = data['versionName'] as String? ?? '1.0.0';
          final releaseNotes = data['releaseNotes'] as String? ?? 'A new update is available.';
          final ipaUrl = data['ipaUrl'] as String? ??
              'https://sked-gold.vercel.app/downloads/sked-ios.ipa';
          final directUrl = data['directIpaDownloadUrl'] as String? ??
              data['directDownloadUrl'] as String? ??
              '/downloads/sked-ios.ipa';
          final fileSize = data['ipaFileSize'] as String? ??
              data['fileSize'] as String? ??
              '7.1 MB';

          if (remoteCode > currentVersionCode) {
            return AppUpdateInfo(
              versionCode: remoteCode,
              versionName: remoteName,
              releaseNotes: releaseNotes,
              ipaUrl: ipaUrl,
              directDownloadUrl: directUrl,
              fileSize: fileSize,
            );
          } else {
            return null;
          }
        }
      } catch (_) {
        // Fallback to next URL
      }
    }
    return null;
  }

  static void showUpdateDialog(BuildContext context, AppUpdateInfo update) {
    showDialog(
      context: context,
      barrierDismissible: true,
      builder: (ctx) => AlertDialog(
        backgroundColor: SkedColors.slab,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
        title: Row(
          children: [
            Container(
              width: 8,
              height: 8,
              decoration: const BoxDecoration(
                color: SkedColors.blaze,
                shape: BoxShape.circle,
              ),
            ),
            const SizedBox(width: 8),
            Text(
              'UPDATE AVAILABLE • v${update.versionName}',
              style: GoogleFonts.barlowCondensed(
                fontSize: 16,
                fontWeight: FontWeight.w700,
                color: SkedColors.blaze,
                letterSpacing: 0.5,
              ),
            ),
          ],
        ),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              'A new version of Sked is available for your iPhone (${update.fileSize}).',
              style: const TextStyle(fontSize: 13, color: SkedColors.chalk, height: 1.4),
            ),
            const SizedBox(height: 12),
            Container(
              padding: const EdgeInsets.all(10),
              decoration: BoxDecoration(
                color: SkedColors.ink,
                borderRadius: BorderRadius.circular(6),
                border: Border.all(color: SkedColors.rule),
              ),
              child: Text(
                update.releaseNotes,
                style: const TextStyle(fontSize: 12, color: SkedColors.slate, height: 1.4),
              ),
            ),
            const SizedBox(height: 14),
            const Text(
              'Download the updated .IPA and install via AltStore or SideStore.',
              style: TextStyle(fontSize: 11, color: SkedColors.textMuted),
            ),
          ],
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(ctx).pop(),
            child: const Text('LATER', style: TextStyle(color: SkedColors.slate, fontSize: 12)),
          ),
          ElevatedButton.icon(
            style: ElevatedButton.styleFrom(
              backgroundColor: SkedColors.blaze,
              foregroundColor: SkedColors.ink,
              shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(6)),
            ),
            onPressed: () async {
              Navigator.of(ctx).pop();
              final uri = Uri.parse(update.ipaUrl);
              if (await canLaunchUrl(uri)) {
                await launchUrl(uri, mode: LaunchMode.externalApplication);
              }
            },
            icon: const Icon(Icons.download, size: 16),
            label: Text(
              'DOWNLOAD IPA',
              style: GoogleFonts.barlowCondensed(fontWeight: FontWeight.w700, fontSize: 13),
            ),
          ),
        ],
      ),
    );
  }
}
