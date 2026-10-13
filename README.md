# Go Back

**Go Back** is an Android-first, local-first focus and personal productivity app built with React Native, Expo, and TypeScript. The design direction is premium and calm: deep forest surfaces, fresh lime accents, rounded cards, and simple navigation.

## Current status

The initial application foundation is in progress. It includes:
- Expo Router navigation and a Today dashboard
- Local SQLite tables for tasks, notes, subjects, focus sessions, exams and settings
- Task list with priorities and optional due dates
- Personal notes editor
- Focus timer with preset durations, elapsed-time recovery, completion notifications, and session history
- Exam countdown and timetable
- Subject/chapter syllabus tracker with automatic progress
- Daily habits with streak tracking and goals with progress controls
- Android home-screen quick-actions widget (Focus, Tasks, Exams, Habits) generated through an Expo config plugin
- Light/dark/system themes, accent colours, local profile, and JSON backup/restore
- Android package ID: `com.goback.focus`
- EAS build profiles for development, preview APK and production APK

**Not yet production-ready:** timer notifications and background behavior need real-device validation; app blocking, screen-time access, encrypted backups, automated tests, and device verification remain outstanding. Backup files are currently plain JSON and should be kept private. No AI, cloud backend, ads, or analytics are configured.

## Requirements

- Node.js LTS
- npm
- Android Studio + Android SDK for local Android builds, or an EAS account for cloud builds

## Getting started

```bash
npm install
npx expo start
```

Run on Android:

```bash
npx expo run:android
```

Type-check:

```bash
npm run typecheck
```

Build an installable **debug APK** from GitHub Actions:
1. Open the repository's **Actions** tab.
2. Select **Android APK Build**.
3. Click **Run workflow** and wait for the job to finish.
4. Download the `Go-Back-debug-apk` artifact from that run.

This debug APK is for device testing; it is **not** the signed production release. To build a preview/release APK with EAS, configure an Expo account and project, then run:

```bash
npm install --global eas-cli
eas build --platform android --profile preview
```

## Privacy approach

The app is designed to keep personal data on-device using SQLite. Do not add external analytics, ad SDKs, AI APIs, or cloud sync without an explicit product decision and updated privacy documentation.

## Project roadmap

See [docs/ROADMAP.md](docs/ROADMAP.md) for implementation phases and known platform limitations.
