# Release & Git Rules for Sked

## 1. Strict Git Push Policy (No Direct/Autonomous Pushes)
- **NEVER** run `git push` autonomously or directly to GitHub without explicit, direct permission from the user in that conversation.
- Local commits (`git commit`) are permitted to save and organize progress locally.
- When ready to push changes to remote, **always stop and ask the user**: *"May I push commit <hash> to GitHub now?"*
- Wait for the user to explicitly approve before running `git push`.

## 2. Automatic Version Bumping Rule
Whenever an APK and/or IPA build is uploaded/released to `https://sked-gold.vercel.app/`:
- **The very next time** you work on the codebase (starting a new feature, bug fix, UI tweak, or release iteration), the version **must be bumped accordingly** before or alongside the work.
- When bumping the version, update all five canonical version files in synchronization:
  1. **`sked-web/public/version.json`**:
     - Increment `versionCode` (e.g., `2` -> `3`).
     - Bump `versionName` (e.g., `1.1.0` -> `1.2.0` or `1.1.1` as appropriate).
     - Update `updatedAt` to the current local date (`yyyy-MM-dd`).
     - Update `releaseNotes` with bullet points reflecting the new changes.
  2. **`sked-android/app/build.gradle.kts`**:
     - Update `versionCode` (matching `version.json`).
     - Update `versionName` (matching `version.json`).
  3. **`sked-app/pubspec.yaml`**:
     - Update `version:` in `<versionName>+<versionCode>` format (e.g., `1.2.0+3`).
  4. **`sked-app/lib/services/update_service.dart`**:
     - Update `currentVersionCode` and `currentVersionName`.
  5. **`sked-app/lib/widgets/about_developer_dialog.dart`**:
     - Update displayed version string (e.g., `'SKED v1.2.0 • iOS'`).

## 3. Deployment & Domain Consistency
- The production web domain is **`https://sked-gold.vercel.app/`**.
- Keep all direct download URLs aligned:
  - APK: `https://sked-gold.vercel.app/downloads/sked-android.apk`
  - IPA: `https://sked-gold.vercel.app/downloads/sked-ios.ipa`
  - Version metadata: `https://sked-gold.vercel.app/version.json`
- Preserve 100% on-device architecture for both Android and iOS (zero external proxy servers).
