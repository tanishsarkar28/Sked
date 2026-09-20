<p align="center">
  <img src="assets/banner.jpg" alt="SKED — Never Miss A Class Again" width="100%" />
</p>

<h1 align="center">SKED<span>.</span></h1>

<p align="center">
  <strong>Never Miss A Class Again</strong><br/>
  Real-time class schedule, live departures widget, exam countdown radar, zero LPU Touch conflicts, and pure relaxation on Sundays.<br/>
  100% on-device. Zero SaaS clutter. Zero tracking.
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android%20%7C%20iOS-FF6B1A?style=for-the-badge&logo=android&logoColor=white" />
  <img src="https://img.shields.io/badge/Version-1.1.0-FF6B1A?style=for-the-badge" />
  <img src="https://img.shields.io/badge/Privacy-100%25%20On--Device-22C55E?style=for-the-badge&logo=shieldcheck&logoColor=white" />
  <img src="https://img.shields.io/badge/Website-sked--gold.vercel.app-FF6B1A?style=for-the-badge" />
</p>

---

## ✨ Features

### 📋 Live Departures Board
Inspired by European airport departures boards. Shows ongoing, upcoming, and finished classes with real-time **NOW.** live spotlight, room numbers, and teacher details with clean color indicators (`ON GOING` in green, `UPCOMING` in blue, and `OVER` in muted grey).

### 🎯 Exam Timetable & Seating Radar (MTE & ETE)
Never get caught unprepared. Tracks all Mid Term (MTE) and End Term (ETE) examinations synchronized with the new `studentums.lpu.in` portal. Includes:
- **Hero Exam Radar**: Highlights your very next exam with a live countdown badge (`IN 11 DAYS`).
- **Complete Timing & Reporting Alerts**: Exact exam windows and mandatory reporting times (e.g. `Report 12:00 PM`).
- **Seating & Room Allocations**: Displays room and desk assignments as soon as they are released.
- **Fast Filter Chips**: Toggle between `ALL`, `UPCOMING`, and `OVER` exam views.

### 🛡️ LPU Touch Compatible (Zero Session Conflict)
Pure Web UMS timetable scraping with zero mobile webservice calls. Keeps your official **LPU Touch** mobile app permanently logged in without single-device session invalidations.

### ☕ Sunday Bitmoji Chill Mode
Sundays are meant for relaxing. The widget automatically strips all academic stress and displays a centered chilling Bitmoji mascot with **"Enjoy your Sunday."**

### 🔒 100% On-Device Privacy
No third-party cloud servers, external proxies, or credential harvesting. All UMS authentication and HTML parsing happens directly inside your phone's secure sandboxed storage.

### 📴 100% Offline Ready
UMS servers crash during 8:30 AM – 9:30 AM peak rush hours. Sked keeps your complete weekly timetable and datesheet cached locally so you never miss your class or exam room.

### 🔄 In-App OTA Updates (Android)
Automatic update detection linked to the Sked website. When a new version is uploaded, the app detects it and prompts the user with a single-tap update flow — download, install, done.

---

## 🏗️ Architecture (100% On-Device)

```
Sked/
├── sked-android/          Native Android app (Primary — Kotlin + Jetpack Compose)
│   ├── MainActivity.kt          Dashboard, Login, Class Cards, Update Dialog
│   ├── TimetableParser.kt       On-device HTML timetable extractor (no server needed)
│   ├── AboutDeveloperDialog.kt  Developer info + manual update check
│   ├── AppUpdateManager.kt      OTA update manager (fetch, download, install)
│   ├── exam/
│   │   ├── ExamParser.kt        Datesheet & seating plan extractor (studentums & classic)
│   │   └── ExamScreen.kt        Exam Radar hero card, countdown chips, and MTE/ETE lists
│   └── widget/
│       ├── TimetableWidget.kt          Glance home-screen widget
│       ├── TimetableWidgetReceiver.kt  Widget broadcast receiver
│       └── TimetableRefreshWorker.kt   Widget background refresh worker
│
├── sked-app/              Flutter mobile client (iOS .IPA + cross-platform)
│   ├── lib/main.dart
│   ├── lib/screens/            today_screen, week_screen, settings_screen
│   ├── lib/theme/app_theme.dart
│   └── lib/widgets/class_card.dart
│
└── sked-web/              Landing page & download portal (Vite + React + TypeScript)
    ├── src/App.tsx
    ├── src/components/         Navbar, Hero, LiveMockup, WidgetPreview,
    │                           FeatureShowcase, InstallSteps, DeveloperCard
    └── public/
        ├── version.json        OTA update metadata (bump this to push updates)
        └── downloads/          sked-android.apk, sked-ios.ipa, sked-ios-source.zip
```

**No PC server or external proxy required.** The native Android app handles authentication, schedule parsing, storage, and widget updates entirely on your phone.

---

## 🎨 Design System

Built around a bold, logo-anchored visual identity: **Blaze orange on Ink near-black** with crisp departures-board typography.

| Token | Hex | Role |
|:---:|:---:|:---|
| **Ink** | `#0A0A0A` | Primary background — true near-black |
| **Slab** | `#141414` | Card/surface — subtle mechanical lift |
| **SlabElevated** | `#1C1C1C` | Elevated surfaces — dialogs, popovers |
| **Rule** | `#252525` | Hairline dividers and borders |
| **Chalk** | `#E8E6E3` | Primary text — warm off-white |
| **Slate** | `#7A7774` | Secondary/metadata text — warm mid-grey |
| **Blaze** | `#FF6B1A` | Logo orange — **the single chromatic accent** |

### Typography
- **Display**: Barlow Condensed Bold (700) — headlines, course codes, status tags (`SKED.`, `TODAY.`, `NOW.`, `EXAM RADAR`)
- **Body & Times**: Inter + tabular monospace — times, room codes, sections, dates
- **Structure**: Sharp `6dp` card radii, `4dp` inputs, monochrome `LEC` / `PRAC` / `TUT` badges

### Lecture & Exam Status Colors
| State | Color | Hex | Role |
|:---:|:---:|:---|:---|
| **ON GOING** | 🟢 Green | `#22C55E` | Currently active lecture |
| **UPCOMING** | 🔵 Light Blue | `#38BDF8` | Lectures scheduled for later today |
| **OVER** | ⚫ Dark Slate | `#3A3A3A` | Concluded lectures |
| **MTE** | 🟠 Blaze Orange | `#FF6B1A` | Mid Term Examination |
| **ETE** | ⚪ Slate Border | `#7A7774` | End Term Examination |

---

## 📱 Android — Build & Install

### Prerequisites
- Android Studio / Android SDK (API 34+)
- JDK 17+
- USB Debugging enabled on your phone

### Build Debug APK
```bash
cd sked-android
./gradlew assembleDebug
```

APK output: `sked-android/app/build/outputs/apk/debug/app-debug.apk`

### Install via ADB
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Direct Download
Grab the latest `sked-android.apk` directly from the [Sked Web Portal](https://sked-gold.vercel.app/) or download it from `sked-web/public/downloads/sked-android.apk`.

---

## 🍎 iOS — Sideload via AltStore / SideStore

### Prerequisites
- iPhone / iPad running iOS 14.0+
- [AltStore](https://altstore.io/), [SideStore](https://sidestore.io/), or [Sideloadly](https://sideloadly.io/)
- Free Apple ID (7-day certificate) or paid Developer account (365-day)

### Steps
1. Download `sked-ios.ipa` from the [Sked Web Portal](https://sked-gold.vercel.app/) or `sked-web/public/downloads/sked-ios.ipa`.
2. Open AltStore/SideStore on your iPhone → tap **"+"** in My Apps.
3. Select the downloaded `sked-ios.ipa` → sign with your Apple ID.
4. Go to **Settings → General → VPN & Device Management** → Trust the developer profile.
5. On iOS 16+: Enable **Developer Mode** in Privacy & Security.

> **Automated GitHub Actions Build:** Pushing changes to `sked-app/**` automatically triggers `.github/workflows/build-ios.yml` on a native macOS-14 runner to build and package `sked-ios.ipa`.

---

## 🌐 Website (sked-web)

The landing page serves as the download portal, feature showcase, and interactive schedule mockups.

- **Production URL**: [https://sked-gold.vercel.app/](https://sked-gold.vercel.app/)

### Run Locally
```bash
cd sked-web
npm install
npm run dev
```

### Build for Production
```bash
npm run build
```

### Tech Stack
- **Vite** + **React** + **TypeScript**
- **Tailwind CSS** + **shadcn/ui** components
- **Framer Motion** animations
- **@paper-design/shaders-react** — Liquid Metal hero background

---

## 🔄 OTA Update System (Android)

When releasing a new version:

1. **Bump Version:** Update `versionCode` and `versionName` in `sked-android/app/build.gradle.kts`.
2. **Build APK:** Run `./gradlew assembleDebug` and copy the binary to `sked-web/public/downloads/sked-android.apk`.
3. **Update Metadata:** Bump `sked-web/public/version.json`:

```json
{
  "versionCode": 2,
  "versionName": "1.1.0",
  "minSupportedVersion": 1,
  "releaseNotes": "• Exam Timetable & Live Countdown Radar (MTE & ETE)\n• Support for modern studentums.lpu.in examination portal\n• Zero LPU Touch session conflict & 100% offline cache\n• Live home screen timetable widget",
  "apkUrl": "https://raw.githubusercontent.com/tanishsarkar28/Sked/main/sked-web/public/downloads/sked-android.apk",
  "directDownloadUrl": "/downloads/sked-android.apk",
  "fileSize": "20.4 MB",
  "updatedAt": "2026-09-20"
}
```

4. **Deploy:** Deploy `sked-web` — all active users automatically receive the in-app update banner on their next app launch.

---

## 🏠 Home Screen Widget

The Jetpack Glance widget brings your schedule directly to your home screen:

- **Resizable**: 4×2, 4×3, 4×4, or 4×5
- **Status Badges**: Distinct `ON GOING` and `UPCOMING` lecture tags
- **Sunday mode**: Displays Bitmoji chill illustration with "Enjoy your Sunday."
- **Tablet optimized**: Scales smoothly up to full-screen on any tablet layout

### Add the Widget
1. Long-press on your home screen
2. Tap **Widgets** → Find **Sked**
3. Place the **Timetable Widget**
4. Resize to your preferred dimensions

---

## 📂 Key Files Reference

| File | Purpose |
|:---|:---|
| `MainActivity.kt` | Main Compose UI — dashboard, login, cards, update dialog, bridge |
| `TimetableParser.kt` | On-device HTML table extraction from UMS |
| `ExamParser.kt` | Examination datesheet & seating plan parser (Next.js & classic) |
| `ExamScreen.kt` | Exam Radar hero card, countdown chips, and MTE/ETE cards |
| `TimetableWidget.kt` | Jetpack Glance home-screen widget |
| `TimetableRefreshWorker.kt` | Background widget refresh worker |
| `AppUpdateManager.kt` | In-app OTA update — fetch, download, install APK |
| `AboutDeveloperDialog.kt` | Developer info + manual update check button |
| `version.json` | OTA update metadata — bump to push new versions |

---

## 👨‍💻 Developer

**Tanish Sarkar**

<p>
  <a href="https://www.instagram.com/tanishsarkar28/">
    <img src="https://img.shields.io/badge/Instagram-@tanishsarkar28-E1306C?style=for-the-badge&logo=instagram&logoColor=white" />
  </a>
  <a href="https://www.linkedin.com/in/tanish-sarkar28/">
    <img src="https://img.shields.io/badge/LinkedIn-tanish--sarkar28-0A66C2?style=for-the-badge&logo=linkedin&logoColor=white" />
  </a>
  <a href="https://github.com/tanishsarkar28">
    <img src="https://img.shields.io/badge/GitHub-tanishsarkar28-181717?style=for-the-badge&logo=github&logoColor=white" />
  </a>
</p>

---

<p align="center">
  <strong>Built with ❤️ for LPU students</strong><br/>
  <sub>Kotlin • Jetpack Compose • Glance • Flutter • React • TypeScript</sub>
</p>
