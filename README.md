# Go Back

Go Back is a local-first focus and digital-wellbeing app for Android. The active application is a **native Kotlin + Jetpack Compose** project in \`android/\`; the previous Expo / React Native application has been retired.

## Features

- **Focus sessions:** countdown timer, Pomodoro cycles, stopwatch, strict-mode exit challenge, session tags and notes, background timer notification, completion alerts and session recovery.
- **Reusable focus presets:** save timer, Pomodoro or stopwatch setups with optional session-only app blocking.
- **App and content blocking:** selected-app blocking, focus-only rules, YouTube Shorts, Instagram Reels, Snapchat Spotlight, supported-browser adult-keyword checks and split-screen checks through an opt-in Accessibility service.
- **Screen-time limits:** per-app daily limits, threshold notifications and app blocking after the configured limit when the required Usage Access and Accessibility permissions are active.
- **Study planning:** subjects and syllabus progress, exams and reminders, recurring focus schedules, skip dates and pre-session reminders.
- **Personal workspace:** on-device notes, session history, analytics, usage insights, Quick Settings tile and home-screen widget.
- **Preferences:** light / dark / system theme, accent palettes, daily focus goal, local display name and photo, finish sounds and PIN protection for in-app rule changes.
- **Backup and restore:** portable JSON backup with integrity check and a local restore point before data replacement.

Notes, study records, focus history and preferences are stored on-device in Room / SQLite. The optional release checker contacts the public GitHub Releases API; it does not upload user notes or focus history.

## Open the Android project

Open the \`android/\` directory in Android Studio, or use the Gradle wrapper from that directory.

### Requirements

- Android Studio with Android SDK Platform 35 installed
- JDK 17
- Gradle wrapper included in \`android/gradlew\`

### Local verification

From the repository root:

\`\`\`bash
cd android
./gradlew test
./gradlew assembleDebug
\`\`\`

The debug APK, when you choose to build it locally, is written to:

\`\`\`
android/app/build/outputs/apk/debug/app-debug.apk
\`\`\`

A signed release build requires a signing key configured on the maintainer's machine. Do not commit keystores, signing passwords or private keys. The repository intentionally contains **no GitHub Actions workflow for building Android or publishing APKs**; pushing code does not compile or publish APKs.

## Permissions and platform limitations

Some features require explicit user action in Android Settings:

- **Usage Access** for screen-time usage statistics.
- **Accessibility service** for app/site blocking. This is optional and can be disabled or revoked at any time.
- **Notifications** for timer, exam, schedule and usage-limit alerts.
- **Exact alarms** where supported and permitted by the Android version and device settings.

Blocking is best-effort and depends on Android version, vendor behaviour, target-app UI and enabled permissions. It is not device-owner enforcement, cannot make the app uninstall-proof and cannot guarantee that every browser or app surface is detected. Users can always disable Accessibility or uninstall the app through Android system controls.

## Data and migration

The native Room database uses the same \`goback.db\` file name and table layout as the previous app. Backups from schema versions 1–5 are supported by the in-app restore path. Keep a backup before installing a build that changes the database schema. New schema changes must include an explicit Room migration; destructive fallback is intentionally not enabled.

## Project layout

\`\`\`
android/
  app/src/main/java/com/goback/focus/
    data/       Room entities, DAOs and database
    logic/      Focus timer and scheduling logic
    receiver/   Alarm and boot receivers
    service/    Accessibility, timer and maintenance services
    ui/         Compose screens, components and theme
    widget/     Glance home-screen widget
  app/src/test/ Native JVM tests
docs/           Project, release and test documentation
assets/icon.svg Source artwork for the launcher icon
\`\`\`

## Release preparation

See [the release checklist](docs/RELEASE_CHECKLIST.md), [the test matrix](docs/RELEASE_TEST_MATRIX.md) and [production readiness notes](docs/PRODUCTION_READINESS.md).

The repository does not currently declare a software licence. Choose and add a licence before describing the project as open source for reuse or redistribution.
