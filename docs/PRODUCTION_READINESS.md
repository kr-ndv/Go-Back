# Production readiness

## Current state

The repository has been consolidated around the standalone Kotlin / Jetpack Compose Android application in \`android/\`. Legacy Expo sources, stale agent handoffs, JVM crash logs and all GitHub Actions workflows have been removed. The app has native screens for the existing local focus, study-planning and screen-time feature areas, including saved focus presets and JSON backup / restore.

**This is source preparation, not a verified production build.** The Android project was not compiled in this maintenance pass, the JVM test suite was not executed and no physical-device validation was performed. No APK or other build artifact was generated.

## Release gates

| Area | State | Required before release |
|---|---|---|
| Native project structure | Prepared | Open \`android/\` in Android Studio and sync Gradle |
| Build toolchain | Configured in source | Verify JDK 17 and Android SDK 35 on the release machine |
| JVM tests | Present, not run | Run \`./gradlew test\` and investigate every failure |
| Android compile | Not verified | Run \`./gradlew assembleDebug\` locally; do not use a GitHub Actions build |
| Device coverage | Not verified | Test at least Android 7/API 24 and a current Android release |
| Database migration | Caution | Test against a copy of an actual previous-version \`goback.db\` before publishing |
| Backup / restore | Implemented in source, not device-tested | Test schema versions 1–5, corrupted checksum, duplicate IDs, rollback and PIN retention |
| Accessibility blocking | Permission-dependent | Verify user disclosure, opt-in, disable path, Settings interactions and supported app versions |
| Usage limits | Permission-dependent | Verify Usage Access, thresholds, midnight reset and post-limit blocking |
| Timer alerts | Implemented in source | Verify exact-alarm permission, Do Not Disturb and each custom sound on device |
| Release signing | Not configured | Use a maintainer-owned signing key kept outside Git |
| GitHub Actions | Intentionally absent | Do not add an APK-producing workflow unless explicitly requested |
| Licence | Selected | MIT License added in repository root |

## Data preservation

The database is named \`goback.db\` to retain compatibility with the previous application. The Room destructive-migration fallback has been removed. A Room 0→1 migration rebuilds all eight known Expo SQLite tables to the canonical Room schema and copies existing columns; values for columns added in later beta versions receive only their documented legacy defaults. Missing required columns cause the migration transaction to abort instead of dropping or replacing data. Any future entity or schema change must be accompanied by a tested Room migration. The previous Expo SQLite schema may vary by app version; validate against a copy of a real database rather than relying solely on synthetic rows.

The JSON restore path validates the app marker, supported schema version, table shapes, record counts, primary-key uniqueness, usage-limit package uniqueness, duration ranges and an optional FNV checksum. Profile photo URI and display name are retained as ordinary local settings; imported photo URIs may only render if their source remains accessible on the device. It writes a local backup before replacing data. The local PIN is not exported and the current device PIN is retained across restore.

## Platform and permission disclosures

Blocking features depend on user-enabled Android Accessibility and Usage Access. They are not device-owner controls. The app must not claim to prevent uninstalling or guarantee that every third-party app/browser surface is blocked. Review the in-app disclosure and README limitation text before release.

## Release format

The repository is deliberately source-only. GitHub Actions workflows have been removed; pushes to \`main\` do not compile the project, publish APKs or upload build artifacts. A maintainer may compile and sign a release locally after the release gates above are complete.
