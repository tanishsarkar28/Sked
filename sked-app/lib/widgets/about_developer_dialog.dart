import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:url_launcher/url_launcher.dart';
import '../theme/app_theme.dart';

class AboutDeveloperDialog extends StatelessWidget {
  const AboutDeveloperDialog({super.key});

  static const String devName = 'Tanish Sarkar';
  static const String devRole = 'Creator & Developer • LPU';
  static const String instaUrl = 'https://www.instagram.com/tanishsarkar28/';
  static const String linkedInUrl = 'https://www.linkedin.com/in/tanish-sarkar28/';
  static const String gitHubUrl = 'https://github.com/tanishsarkar28';

  Future<void> _openLink(String urlStr) async {
    final uri = Uri.parse(urlStr);
    try {
      if (await canLaunchUrl(uri)) {
        await launchUrl(uri, mode: LaunchMode.externalApplication);
      }
    } catch (_) {}
  }

  @override
  Widget build(BuildContext context) {
    return Dialog(
      backgroundColor: SkedColors.slab,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
      child: Container(
        padding: const EdgeInsets.all(20),
        constraints: const BoxConstraints(maxWidth: 400),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            // Top row
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Row(
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
                      'ABOUT DEVELOPER',
                      style: GoogleFonts.barlowCondensed(
                        fontSize: 13,
                        fontWeight: FontWeight.w700,
                        color: SkedColors.blaze,
                        letterSpacing: 0.5,
                      ),
                    ),
                  ],
                ),
                IconButton(
                  icon: const Icon(Icons.close, color: SkedColors.slate, size: 20),
                  onPressed: () => Navigator.of(context).pop(),
                  padding: EdgeInsets.zero,
                  constraints: const BoxConstraints(),
                ),
              ],
            ),
            const SizedBox(height: 16),

            // Profile & Name
            Row(
              children: [
                Container(
                  width: 48,
                  height: 48,
                  decoration: BoxDecoration(
                    color: SkedColors.ink,
                    borderRadius: BorderRadius.circular(24),
                    border: Border.all(color: SkedColors.blaze.withOpacity(0.5), width: 1.5),
                  ),
                  alignment: Alignment.center,
                  child: Text(
                    'TS',
                    style: GoogleFonts.barlowCondensed(
                      fontSize: 20,
                      fontWeight: FontWeight.w700,
                      color: SkedColors.blaze,
                    ),
                  ),
                ),
                const SizedBox(width: 14),
                Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      devName,
                      style: GoogleFonts.barlowCondensed(
                        fontSize: 22,
                        fontWeight: FontWeight.w700,
                        color: SkedColors.chalk,
                      ),
                    ),
                    Text(
                      devRole,
                      style: const TextStyle(
                        fontSize: 12,
                        color: SkedColors.slate,
                      ),
                    ),
                  ],
                ),
              ],
            ),
            const SizedBox(height: 18),

            // Social Buttons
            Row(
              children: [
                Expanded(
                  child: _socialButton(
                    label: 'Instagram',
                    icon: Icons.camera_alt_outlined,
                    onTap: () => _openLink(instaUrl),
                  ),
                ),
                const SizedBox(width: 8),
                Expanded(
                  child: _socialButton(
                    label: 'LinkedIn',
                    icon: Icons.work_outline,
                    onTap: () => _openLink(linkedInUrl),
                  ),
                ),
                const SizedBox(width: 8),
                Expanded(
                  child: _socialButton(
                    label: 'GitHub',
                    icon: Icons.code,
                    onTap: () => _openLink(gitHubUrl),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 18),
            const Divider(color: SkedColors.rule),
            const SizedBox(height: 12),

            // Privacy Notice
            Container(
              padding: const EdgeInsets.all(12),
              decoration: BoxDecoration(
                color: SkedColors.ink,
                borderRadius: BorderRadius.circular(6),
                border: Border.all(color: SkedColors.rule),
              ),
              child: Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const Icon(Icons.shield_outlined, color: SkedColors.blaze, size: 18),
                  const SizedBox(width: 10),
                  const Expanded(
                    child: Text(
                      '100% On-Device & Private. All timetable and datesheet data scraped and stored locally on your iPhone with zero external servers.',
                      style: TextStyle(
                        fontSize: 11,
                        color: SkedColors.slate,
                        height: 1.4,
                      ),
                    ),
                  ),
                ],
              ),
            ),
            const SizedBox(height: 12),

            // Version info
            Align(
              alignment: Alignment.centerRight,
              child: Text(
                'SKED v1.1.0 • iOS Edition',
                style: const TextStyle(
                  fontFamily: 'monospace',
                  fontSize: 10,
                  color: SkedColors.textMuted,
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _socialButton({
    required String label,
    required IconData icon,
    required VoidCallback onTap,
  }) {
    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(4),
      child: Container(
        padding: const EdgeInsets.symmetric(vertical: 8),
        decoration: BoxDecoration(
          color: SkedColors.ink,
          borderRadius: BorderRadius.circular(4),
          border: Border.all(color: SkedColors.rule),
        ),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(icon, size: 16, color: SkedColors.chalk),
            const SizedBox(height: 4),
            Text(
              label,
              style: GoogleFonts.barlowCondensed(
                fontSize: 11,
                fontWeight: FontWeight.w600,
                color: SkedColors.slate,
              ),
            ),
          ],
        ),
      ),
    );
  }
}
