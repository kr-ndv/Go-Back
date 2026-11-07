# Survey Report: Native Android Implementation & Architecture Analysis

**Agent**: Explorer 1  
**Milestone**: Survey & Native Android Implementation Analysis  
**Working Directory**: `c:\Users\visha\Desktop\Projects\Go-Back\.agents\teamwork\explorer_survey_1\`  
**Date**: 2026-11-07  

---

## 1. Observation

### 1.1 Project Structure & Build Configuration
- **Root Directory Context**: The project contains a legacy React Native / Expo application at the repository root (`package.json`, `app/`, `src/`) alongside a standalone native Kotlin/Android application in `android/`.
- **Root Gradle Files**:
  - `android/build.gradle.kts`:
    - Android Gradle Plugin: `com.android.application` version `8.7.2` (line 3).
    - Kotlin Android & Compose Compiler: `2.0.21` (lines 4-5).
    - KSP: `2.0.21-1.0.28` (line 6).
  - `android/settings.gradle.kts`:
    - Root project name `"Go-Back"` including `:app` module (lines 22-23).
  - `android/local.properties`: Configures `sdk.dir=C\:\\Users\\visha\\AppData\\Local\\Android\\Sdk`.
- **App Module Configuration (`android/app/build.gradle.kts`)**:
  - `namespace = "com.goback.focus"`
  - `compileSdk = 35`, `minSdk = 24`, `targetSdk = 35` (lines 9-15).
  - Java compatibility: `JavaVersion.VERSION_17`, JVM target `"17"` (lines 35-42).
  - Features: `compose = true`, `buildConfig = true` (lines 44-47).
  - `buildTypes { release { ... } }` (lines 25-33): `isMinifyEnabled = false`. **Zero `signingConfig` is attached to release builds**, which causes AGP to produce an unsigned APK (`app-release-unsigned.apk`) unless a keystore is configured.
  - Dependencies (lines 56-101):
    - Compose BOM: `androidx.compose:compose-bom:2024.10.01` with Material 3.
    - Navigation: `androidx.navigation:navigation-compose:2.8.3`.
    - Lifecycle: `androidx.lifecycle:lifecycle-runtime-compose:2.8.7`, `lifecycle-viewmodel-compose:2.8.7`.
    - Room DB: `androidx.room:room-runtime:2.6.1`, `room-ktx:2.6.1`, `room-compiler:2.6.1` via KSP.
    - WorkManager: `androidx.work:work-runtime-ktx:2.9.1`.
    - DataStore: `androidx.datastore:datastore-preferences:1.1.1`.
    - Glance AppWidget: `androidx.glance:glance-appwidget:1.1.1`, `glance-material3:1.1.1`.
    - Coroutines: `kotlinx-coroutines-android:1.9.0`.

### 1.2 Build & Compilation Test Results
- `gradlew.bat assembleDebug`:
  - **Result**: `BUILD SUCCESSFUL in 48s` (Task :app:assembleDebug completed).
  - Output artifact produced: `android/app/build/outputs/apk/debug/app-debug.apk` (19,434,142 bytes).
- `gradlew.bat assembleRelease`:
  - Successfully compiles Kotlin (`:app:compileReleaseKotlin`) and Java (`:app:compileReleaseJavaWithJavac`), processes resources, and dexes code (`:app:mergeDexRelease`).
  - AGP 8.7.2 / Kotlin 2.0.21 outputs warnings during lint vital: `KaLifetimeTokenFactory#afterLeavingAnalysisContext`, `KaSessionProvider#getNoWriteActionInAnalyseCallChecker`.
  - Requires explicit signing configuration to generate a signed distributable APK.

### 1.3 AndroidManifest & Permissions (`android/app/src/main/AndroidManifest.xml`)
- Declares permissions:
  - `android.permission.PACKAGE_USAGE_STATS` (line 7)
  - `android.permission.POST_NOTIFICATIONS` (line 11)
  - `android.permission.SCHEDULE_EXACT_ALARM`, `USE_EXACT_ALARM` (lines 14-15)
  - `android.permission.FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE` (lines 18-19)
  - `android.permission.WAKE_LOCK`, `RECEIVE_BOOT_COMPLETED` (lines 20-21)
- **Critical Missing Permission**:
  - `android.permission.INTERNET` is **completely missing**. `GoBackMaintenanceWorker.kt` (line 130) performs `URL("https://api.github.com/repos/kr-ndv/Go-Back/releases").openConnection() as HttpURLConnection`. Without `INTERNET`, this immediately throws `SecurityException`.
- Components Declared:
  - Activity: `MainActivity` (single top / launcher).
  - Services:
    - `GoBackBlockingService`: Accessibility Service (`BIND_ACCESSIBILITY_SERVICE`) with `@xml/accessibility_service_config`.
    - `FocusTimerService`: Foreground service with `foregroundServiceType="specialUse"` and subtype `"Focus session timer and countdown tracking"`.
    - `FocusTileService`: Quick Settings Tile (`BIND_QUICK_SETTINGS_TILE`).
  - Receivers:
    - `FocusAlarmReceiver`: Exported `false` for exact timer alerts.
    - `BootReceiver`: Exported `false` for `BOOT_COMPLETED`.
    - `GoBackFocusWidgetReceiver`: Exported `true` for `APPWIDGET_UPDATE`.

### 1.4 Codebase & Screen Implementation Audit

| Component / Screen | File Location | Observed Status | Key Deficiencies / Bugs |
|---|---|---|---|
| **`MainActivity` & Navigation** | `com/goback/focus/MainActivity.kt` | **Partial** | Uses `NavHost` with `Home`, `Focus`, `Analytics`, `Notes`, `Settings`, and `"syllabus"`. Line 70 has `onNavigateToExams = { /* Navigate to exams */ }` which is an empty no-op. No route exists for Exams or Schedules. |
| **`TodayScreen`** | `ui/screens/TodayScreen.kt` | **Stubbed (Mock)** | All metrics ("45m / 120m goal", "🔥 3d streak · ✨ Spark", "2h 15m · 34 unlocks", "14 / 28" chapters, "Calculus II 12 days to go") are hardcoded literals. Zero ViewModel or Room Flow observation. |
| **`FocusScreen`** | `ui/screens/FocusScreen.kt` | **Broken / Stubbed** | Circular progress ring and duration buttons (15, 25, 45, 60m) exist. Button click (`isRunning = !isRunning`) only toggles a boolean; `remainingSeconds` **never counts down**. No Pomodoro mode. No Stopwatch mode. No integration with `FocusTimerService` or `FocusAlarmReceiver`. No session saving to `FocusSessionDao`. No blocking activation. |
| **`AnalyticsScreen`** | `ui/screens/AnalyticsScreen.kt` | **Stubbed (Mock)** | Static cards: "2h 15m Screen Time", "34 Phone Unlocks", "1h 45m Focus Time". Hardcoded list of apps ("YouTube", "Instagram", "Chrome", "AnkiDroid"). No query to `UsageStatsManager`, `UsageEvents`, or `FocusSessionDao`. |
| **`NotesScreen`** | `ui/screens/NotesScreen.kt` | **Stubbed (Mock)** | Displays 3 hardcoded `UiNote` items. FAB `onClick = { /* Add note */ }` is a no-op. Note clicks do nothing. No Note editor, no note creation dialog, no Room `NoteDao` connection. |
| **`SettingsScreen`** | `ui/screens/SettingsScreen.kt` | **Stubbed (Mock)** | Displays 4 list items ("App & Content Blocking", "Security PIN Protection", "Theme & Palette", "Backup & Restore"). All items have empty click listeners: `clickable { /* Handle click */ }`. No sub-screens, toggles, or PIN logic. |
| **`SyllabusScreen`** | `ui/screens/SyllabusScreen.kt` | **Partially Implemented** | Interactive chapter checkboxes stored in local `remember { mutableStateOf(...) }`. **Zero persistence**: state resets on navigation or recreation; not connected to Room `SubjectDao`. No ability to add/edit/delete subjects or chapters. |
| **`ExamsScreen`** | None | **Missing** | `ExamDao` and `ExamEntity` exist in Room database, but no Composable screen exists in the native project. |
| **`SchedulesScreen`** | None | **Missing** | `FocusScheduleDao` and `FocusScheduleEntity` exist in Room database, but no UI screen or AlarmManager scheduler exists. |
| **`GoBackBlockingService`** | `service/GoBackBlockingService.kt` | **Implemented with severe perf risk** | Reads SharedPreferences `"go_back_blocking"` and executes JSON parsing + deep tree recursion (`rootInActiveWindow` depth 5) on **every** `TYPE_WINDOW_CONTENT_CHANGED` event. Causes main-thread freezing and ANR vulnerability. |
| **`FocusTimerService`** | `service/FocusTimerService.kt` | **Implemented** | Foreground chronometer notification service. Calls `startForeground(NOTIFICATION_ID, notification)` without explicit `foregroundServiceType` parameter (fragile on Android 14+). |
| **`GoBackFocusWidget`** | `widget/GoBackFocusWidget.kt` | **Implemented / Disconnected** | Glance AppWidget layout is implemented. However, nothing in the app ever updates `go_back_widget_data` or triggers widget re-rendering when sessions start or end. |
| **Launcher Icons** | `res/mipmap-anydpi-v26/`, `res/drawable/` | **Non-compliant** | Only adaptive vector XML (`ic_launcher.xml`, generic hourglass) is present. No legacy mipmap densities (`mipmap-mdpi`, `mipmap-hdpi`, etc.) for `minSdk = 24`. Does not use user-specified icon image (`media_1794057979794_1140e298.jpg`). |

---

## 2. Logic Chain

1. **Build Viability**:
   - `build.gradle.kts` and `app/build.gradle.kts` have correct modern Kotlin 2.0.21 / AGP 8.7.2 setup and compile cleanly with `assembleDebug`.
   - The native app code is fully capable of building without syntax or compilation errors.
   - However, for release distribution (`assembleRelease`), `android/app/build.gradle.kts` lacks a signing configuration, preventing a signed release APK from being emitted.

2. **Stability & Crash Causality**:
   - **Crash / Bug 1 (`SecurityException`)**: In `GoBackMaintenanceWorker.doWork() -> checkStableRelease()`, an HTTP connection is initiated to the GitHub API. Because `android.permission.INTERNET` is missing from `AndroidManifest.xml`, any network access fails.
   - **Crash / Bug 2 (Accessibility ANR & Freezing)**: In `GoBackBlockingService.onAccessibilityEvent`, listening to `TYPE_WINDOW_CONTENT_CHANGED` causes invocation hundreds of times per second during UI animations. Synchronously opening SharedPreferences, parsing JSON strings, and recursively traversing the active window node hierarchy on the UI thread causes dropped frames, input delays, and system ANR kills.
   - **Crash / Bug 3 (Legacy Launcher Inflation Crash on Android 7.x)**: `minSdk = 24`, but only `mipmap-anydpi-v26` is provided. Devices on Android 7.0/7.1 (API 24/25) attempting to resolve `@mipmap/ic_launcher` can crash or display broken fallback icons.
   - **Crash / Bug 4 (Timer & Service Lifecycle)**: In `FocusScreen.kt`, starting a session does not launch `FocusTimerService` or acquire a wake lock. If the user turns off the screen or switches apps, the timer stops because there is no background service or AlarmManager backing it.

3. **Feature Parity Gap**:
   - The reference implementation in `src/lib/` (`focusLogic.ts`, `focusInsights.ts`, `blockingConfig.ts`, `examNotifications.ts`) has rich domain models for:
     - 3 focus modes (Countdown Timer, Pomodoro with 4-cycle long breaks, and Stopwatch count-up).
     - Streak calculation with 7 tiers (`spark`, `momentum`, `commitment`, `habit`, `mastery`, `centurion`) and 1-day streak shield.
     - Full CRUD for Notes, Subjects/Chapters, Exams, Schedules, and Usage Limits.
   - In contrast, the native Compose layer in `android/` currently consists primarily of static visual shells with hardcoded UI text and unhandled button callbacks.

---

## 3. Caveats

- **Runtime Device Testing**: Verification was performed via Gradle compilation (`assembleDebug` and `assembleRelease` tasks) and static AST inspection; live on-device execution (e.g. adb logcat during physical app blocking of YouTube Shorts) was not run directly in this read-only exploration phase.
- **Reference Codebase**: The root project contains both React Native code (`app/`, `src/`) and native Android code (`android/`). As per the project specification, the native Android implementation in `android/` is the active codebase to fix, enhance, and deliver as the production standalone APK.

---

## 4. Conclusion

1. **Build Status**: The native Android project in `android/` is syntactically sound and builds with Gradle (`assembleDebug` succeeds; release build needs signing config and internet permission).
2. **Crash & Stability Hazards**:
   - Add `<uses-permission android:name="android.permission.INTERNET" />` to `AndroidManifest.xml`.
   - Add legacy density mipmaps (`mdpi` through `xxxhdpi`) alongside adaptive icon layers.
   - Optimize `GoBackBlockingService` with in-memory cached configuration and debounced event processing to avoid ANR crashes.
3. **Core Feature Deficiencies to Implement**:
   - **Architecture**: Introduce ViewModels backed by `GoBackDatabase` DAOs for `TodayScreen`, `FocusScreen`, `AnalyticsScreen`, `NotesScreen`, `SyllabusScreen`, and new `ExamsScreen` / `SchedulesScreen`.
   - **Focus Timer Engine**: Implement active countdown, Pomodoro phase transitions (focus, short break, long break), and stopwatch counting, coupled with `FocusTimerService` and `FocusAlarmReceiver`.
   - **Analytics & Screen Time**: Integrate `UsageStatsManager` for real app usage metrics and unlock events.
   - **Persistence**: Hook `NoteDao`, `SubjectDao`, and `ExamDao` directly into Compose UI for complete CRUD operations.
   - **Launcher Icon**: Generate adaptive and legacy mipmap assets from the user-uploaded image (`media_1794057979794_1140e298.jpg`).
   - **Release Packaging**: Configure release keystore signing in `android/app/build.gradle.kts` and produce signed APK.

---

## 5. Verification Method

To independently verify these findings:
1. **Compilation Check**:
   ```bash
   cd android
   ./gradlew.bat assembleDebug
   ```
   Inspect generated APK at `android/app/build/outputs/apk/debug/app-debug.apk`.
2. **Missing Permission Verification**:
   Inspect `android/app/src/main/AndroidManifest.xml` line 1-22: confirm lack of `android.permission.INTERNET`.
3. **Mock UI Verification**:
   - Inspect `android/app/src/main/java/com/goback/focus/ui/screens/FocusScreen.kt`: lines 28-30 (local-only state) and line 131 (`onClick = { isRunning = !isRunning }` without any timer decrement).
   - Inspect `android/app/src/main/java/com/goback/focus/ui/screens/TodayScreen.kt`: lines 128, 129, 227 (hardcoded string literals).
   - Inspect `android/app/src/main/java/com/goback/focus/ui/screens/NotesScreen.kt`: lines 26-30 (mock sample list) and line 61 (`onClick = { /* Add note */ }`).
   - Inspect `android/app/src/main/java/com/goback/focus/ui/screens/SettingsScreen.kt`: line 105 (`clickable { /* Handle click */ }`).
   - Inspect `android/app/src/main/java/com/goback/focus/MainActivity.kt`: line 70 (`onNavigateToExams = { /* Navigate to exams */ }`).
4. **Invalidation Conditions**:
   The conclusions would be invalidated if an alternative module or hidden background worker already bound Room DAOs to these screens, or if an external build variant bypassed the current `app/build.gradle.kts` configuration.
