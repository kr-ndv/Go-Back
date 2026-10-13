# Go Back

**Go Back** is an Android-first, local-first focus and personal productivity app built with React Native, Expo, and TypeScript. The design direction is premium and calm: deep forest surfaces, fresh lime accents, rounded cards, and simple navigation.

## Current status

The initial application foundation is in progress. It includes:
- Expo Router navigation and a Today dashboard
- Local SQLite tables for tasks, notes, subjects, focus sessions, exams and settings
- A basic task list and personal notes editor
- A basic foreground focus timer
- Light/dark theme selection
- Android package ID: `com.goback.focus`
- EAS build profiles for development, preview APK and production APK

**Not yet production-ready:** background-safe focus timing, actual Android home-screen widgets, app blocking, screen-time access, full exam/syllabus workflows, backup/restore, automated tests, and device verification. These will be implemented and validated in subsequent stages. No AI, cloud backend, ads, or analytics are configured.

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

Build an internal APK using EAS:

```bash
npm install --global eas-cli
eas build --platform android --profile preview
```

## Privacy approach

The app is designed to keep personal data on-device using SQLite. Do not add external analytics, ad SDKs, AI APIs, or cloud sync without an explicit product decision and updated privacy documentation.

## Project roadmap

See [docs/ROADMAP.md](docs/ROADMAP.md) for implementation phases and known platform limitations.
