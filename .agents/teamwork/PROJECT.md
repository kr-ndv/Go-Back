# Project: Go-Back Native Android Stabilization & Feature Parity

## Architecture
- **Platform**: Standalone Native Android (Kotlin + Jetpack Compose Material 3), AGP 8.7.2, Kotlin 2.0.21, minSdk 24, targetSdk 35, JVM 17.
- **UI & Navigation**: Single Activity (`MainActivity.kt`) with `NavHost` managing Compose screens:
  - `TodayScreen`: Overview dashboard, daily focus goal ring, streak badge & shield indicator, quick stats, upcoming exams card, syllabus progress.
  - `FocusScreen`: Core focus engine with 3 modes (Countdown Timer, Pomodoro, Stopwatch), circular progress indicator, tag/note selector, Strict Mode challenge, finish alarm sound.
  - `AnalyticsScreen`: Dual-bar Focus vs Screen Time chart (Today 1d, 7d, 30d), KPI metrics, categorized app usage breakdown.
  - `NotesScreen` & `NoteEditorScreen`: Local-first Markdown note editor with 500ms debounced autosave, hardware back flush, live search.
  - `SyllabusScreen`: Subject & chapter completion tracker with Room persistence and squiggly progress bars.
  - `ExamsScreen`: Exam countdown timetable, upcoming hero card, 3-stage reminder scheduling.
  - `SchedulesScreen`: Weekly focus blocks (time, duration, recurring days, pre-session alerts).
  - `BlockingScreen`: Whole-app blocking picker (up to 100 apps), Shorts/Reels/Spotlight toggles, adult website blocker, YouTube study mode channel allowlist.
  - `UsageLimitsScreen`: Daily app screen time limits and warning thresholds (50-99%).
  - `SettingsScreen`: Blocking, PIN security lock, theme, sound selection, and v5 JSON backup/restore.
- **Data Layer**: Room SQLite Database (`GoBackDatabase` v1) with reactive Kotlin `Flow`s:
  - `NoteDao`, `SubjectDao`, `FocusSessionDao`, `ExamDao`, `FocusScheduleDao`, `FocusPresetDao`, `UsageLimitDao`, `SettingDao`.
- **System Integration**:
  - `GoBackBlockingService`: Accessibility Service (`BIND_ACCESSIBILITY_SERVICE`) with in-memory caching and debounced event dispatch.
  - `FocusTimerService`: Foreground Service (`specialUse`) with live Android chronometer notification.
  - `FocusTileService`: Quick Settings `TileService`.
  - `GoBackFocusWidget`: Jetpack Glance AppWidget.
  - `GoBackMaintenanceWorker`: WorkManager periodic worker (app limits & release updates).
  - `FocusAlarmReceiver` & `BootReceiver`: Exact timer alerts and boot recovery.

---

## Feature Inventory
| # | Feature | Description | Milestone | Source |
|---|---------|-------------|-----------|--------|
| 1 | Multi-Mode Countdown Timer | Presets (15, 25, 45, 60m), custom (1–180m), M3 progress ring | M2 | Survey / Spec |
| 2 | Timer Persistence & Recovery | `endAt` timestamp recovery across process death / reboot | M2 | Survey / Spec |
| 3 | Session Tags & Notes | Tag (max 40 char) and note (max 300 char) metadata per session | M2 | Survey / Spec |
| 4 | 4-Round Pomodoro Cycle | 25m work / 5m short break, 15m long break after round 4 | M2 | Survey / Spec |
| 5 | Open-Ended Stopwatch | Accumulated elapsed seconds and `runStartedAt` timestamp recovery | M2 | Survey / Spec |
| 6 | Notes Repository & Editor | Full Markdown/plaintext CRUD, 500ms debounced autosave, search | M2 | Survey / Spec |
| 7 | Screen-Time vs Focus Insights | Dual-bar comparison chart (1d, 7d, 30d), % change vs prior period | M2 | Survey / Spec |
| 8 | App Classification | Productive (`#34D399`), Distracting (`#F87171`), Neutral (`#9CA3AF`) | M2 | Survey / Spec |
| 9 | Session History & Date Filters | Filterable session log (All, Focus, Break) with date range | M2 | Survey / Spec |
| 10 | Whole-App Blocking | Select up to 100 launchable packages, `GLOBAL_ACTION_HOME` on launch | M3 | Survey / Spec |
| 11 | YouTube Shorts Blocking | Detect Shorts feed/tab, execute `GLOBAL_ACTION_BACK` | M3 | Survey / Spec |
| 12 | Instagram Reels Blocking | Detect Reels tab/surface, execute `GLOBAL_ACTION_BACK` | M3 | Survey / Spec |
| 13 | Snapchat Spotlight Blocking | Detect Spotlight tab, execute `GLOBAL_ACTION_BACK` | M3 | Survey / Spec |
| 14 | Adult Website Blocker | Block adult domains across 7 major browsers via address bar scan | M3 | Survey / Spec |
| 15 | YouTube Study Mode | Channel allowlist (up to 50 channels), dismiss non-allowed videos | M3 | Survey / Spec |
| 16 | Strict Mode Challenge | Commitment phrase typing ("I choose to break my focus") to quit early | M2 | Survey / Spec |
| 17 | Settings / Uninstall Protection | Prevent force-stopping or uninstalling Go Back while focus active | M3 | Survey / Spec |
| 18 | Split-Screen Protection | Block distraction apps when opened in multi-window mode | M3 | Survey / Spec |
| 19 | Weekly Focus Schedules | Recurring weekly focus blocks with 24-hr time, duration, and days | M3 | Survey / Spec |
| 20 | Advanced Schedules Engine | Pre-reminders (0/5/10/15/30m), routine presets, timezone preservation | M3 | Survey / Spec |
| 21 | Exam Timetable Planner | Chronological exam list, dates, subjects, UP NEXT hero card | M3 | Survey / Spec |
| 22 | 3-Stage Exam Reminders | Automatic reminders 7d before (9am), 1d before (9am), morning (7am) | M3 | Survey / Spec |
| 23 | Daily Focus Goal & Celebration | Configurable 15–480m goal, instant celebration dialog & alert | M2 | Survey / Spec |
| 24 | Streak Tiers Gamification | 6 tiers: Spark (3d), Momentum (7d), Commitment (14d), Habit (30d), Mastery (50d), Centurion (100d) | M2 | Survey / Spec |
| 25 | 1-Day Streak Shield | 1-day grace period preserving streak on a single missed day | M2 | Survey / Spec |
| 26 | Glance 2x2 AppWidget | Home screen widget with active timer, today focus, streak, exam countdown | M3 | Survey / Spec |
| 27 | Quick Settings Tile | System QS Tile toggling focus session and opening app | M3 | Survey / Spec |
| 28 | Foreground Live Chronometer | Ongoing notification with native Android Chronometer count | M2 | Survey / Spec |
| 29 | Multi-Channel Notifications | 7 dedicated channels with custom importance, sounds, vibrations | M3 | Survey / Spec |
| 30 | Focus Presets Engine | Reusable focus modes with session-temporary app blocking lists | M3 | Survey / Spec |
| 31 | Daily Screen-Time App Limits | Daily limits, warning alerts at 80%, WorkManager evaluation | M3 | Survey / Spec |
| 32 | 4-Digit Security PIN Lock | Protects blocking configuration with salted PIN verification | M3 | Survey / Spec |
| 33 | Syllabus & Chapter Tracker | Track subjects and chapters completed with Room persistence | M3 | Survey / Spec |
| 34 | JSON Backup & Restore v5 | Full database export/import with checksum and rollback snapshot | M3 | Survey / Spec |
| 35 | GitHub Release Checker | WorkManager periodic check for newer GitHub release tags | M1 | Survey / Spec |
| 36 | Selectable Finish Alarm Sound | Soft bell, warm chime, digital alert, system default sounds | M2 | Survey / Spec |
| 37 | Service ANR Optimization | In-memory cache & debouncing in `GoBackBlockingService` | M1 | Survey / Code |
| 38 | AndroidManifest Permissions | Add `INTERNET` permission for worker network calls | M1 | Survey / Code |
| 39 | Foreground Service Type Fix | Explicit `foregroundServiceType="specialUse"` for Android 14+ | M1 | Survey / Code |
| 40 | Launcher Icon & Adaptive Layers | Green back-arrow vector (`#C7F485`), dark background (`#081B14`), full mipmap densities | M4 | Survey / Asset |
| 41 | Release Signing Configuration | Generate `release.keystore`, configure `signingConfigs.release` in Gradle | M5 | Survey / Build |
| 42 | Production Release Deliverable | Build release APK via Gradle, copy to `C:\Users\visha\Downloads\` | M5 | Survey / Build |

---

## Milestones
| # | Name | Scope | Dependencies | Status |
|---|------|-------|-------------|--------|
| M1 | Build Setup, Stability & Crash Fixes | Fix `AndroidManifest.xml` permissions (`INTERNET`), optimize `GoBackBlockingService` with in-memory caching to eliminate ANR/freezing risks, fix foreground service launch type | none | DONE |
| M2 | Core Focus Engine & Productivity Screens | Implement full `FocusScreen` (Timer, Pomodoro, Stopwatch, Strict Mode challenge, tags/notes), wire `TodayScreen` to live Room DB and streak tiers + shield, wire `NotesScreen` + `NoteEditorScreen` with Room CRUD, wire `AnalyticsScreen` with UsageStats | M1 | DONE |
| M3 | System Integration, Blocking, Schedules & Exams | Implement `BlockingScreen` & connect to Accessibility service, implement `SchedulesScreen` & `ExamsScreen` with reminders, wire `SyllabusScreen` with Room DB, wire Glance widget & Quick Settings tile, wire 7 notification channels | M2 | PLANNED |
| M4 | Visual Assets & Launcher Icon Update | Replace generic hourglass with green curved back-arrow vector from `assets/icon.svg` (`#C7F485`), set background `#081B14`, generate adaptive icon layers and legacy mipmaps (`mdpi`–`xxxhdpi`) | none | PLANNED |
| M5 | E2E Testing, Release Signing & Production Deliverable | Run full E2E verification, configure Gradle release signing with keystore, execute `assembleRelease`, verify signed APK, copy to `C:\Users\visha\Downloads\` | M1, M2, M3, M4 | PLANNED |

---

## Interface Contracts

### 1. Focus Timer Service Contract
- **Service**: `com.goback.focus.service.FocusTimerService`
- **Actions**:
  - `ACTION_START`: Extras `EXTRA_DURATION_SECONDS` (Long), `EXTRA_END_AT_MILLIS` (Long), `EXTRA_IS_STOPWATCH` (Boolean), `EXTRA_TITLE` (String).
  - `ACTION_PAUSE`: Pauses chronometer notification.
  - `ACTION_RESUME`: Resumes chronometer notification.
  - `ACTION_STOP`: Stops foreground service and dismisses notification.
- **State Broadcast / StateFlow**: Broadcasts `ACTION_TIMER_TICK` or updates shared state singleton with current remaining seconds.

### 2. Blocking Configuration Contract
- **SharedPreferences Name**: `"go_back_blocking"`
- **Keys**:
  - `"blocked_packages"`: StringSet of package names (e.g. `["com.google.android.youtube", "com.instagram.android"]`)
  - `"block_shorts"`: Boolean
  - `"block_reels"`: Boolean
  - `"block_spotlight"`: Boolean
  - `"block_adult"`: Boolean
  - `"youtube_study_mode"`: Boolean
  - `"youtube_allowed_channels"`: StringSet
  - `"strict_mode_active"`: Boolean
  - `"strict_uninstall_protection"`: Boolean
- **Sync**: Modified in `BlockingScreen` / `FocusScreen` -> immediately read into memory cache of `GoBackBlockingService`.

### 3. Widget Data Contract
- **SharedPreferences Name**: `"go_back_widget_data"`
- **Keys**:
  - `"is_active"`: Boolean
  - `"remaining_seconds"`: Long
  - `"today_focus_minutes"`: Int
  - `"daily_goal_minutes"`: Int
  - `"streak_days"`: Int
  - `"streak_tier_emoji"`: String
  - `"next_exam_name"`: String
  - `"next_exam_days"`: Int
- **Trigger**: App updates widget prefs on session start/tick/finish, then invokes `GoBackFocusWidget().updateAll(context)`.

---

## Code Layout
- `android/app/src/main/AndroidManifest.xml`: Permissions, activities, services, receivers.
- `android/app/build.gradle.kts`: Dependencies, build types, signing configs.
- `android/app/src/main/java/com/goback/focus/`:
  - `MainActivity.kt`: Navigation routes and top-level scaffolding.
  - `data/db/`: `GoBackDatabase.kt`, `Entities.kt`, `Daos.kt`.
  - `service/`: `GoBackBlockingService.kt`, `FocusTimerService.kt`, `FocusTileService.kt`.
  - `receiver/`: `FocusAlarmReceiver.kt`, `BootReceiver.kt`, `GoBackFocusWidgetReceiver.kt`.
  - `worker/`: `GoBackMaintenanceWorker.kt`.
  - `widget/`: `GoBackFocusWidget.kt`.
  - `ui/screens/`:
    - `TodayScreen.kt`, `FocusScreen.kt`, `AnalyticsScreen.kt`, `NotesScreen.kt`, `NoteEditorScreen.kt`, `SyllabusScreen.kt`, `ExamsScreen.kt`, `SchedulesScreen.kt`, `BlockingScreen.kt`, `UsageLimitsScreen.kt`, `SettingsScreen.kt`.
  - `ui/viewmodel/`: ViewModels connecting Room DAOs & system services to Compose UI.
- `android/app/src/main/res/`:
  - `values/colors.xml`: Palette definitions including `ic_launcher_background`.
  - `drawable/ic_launcher_foreground.xml`: Vector icon.
  - `mipmap-anydpi-v26/`: Adaptive icon XMLs.
  - `mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/`: Raster mipmaps for legacy launchers.
