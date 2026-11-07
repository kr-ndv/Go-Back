# Go-Back Specification Mining & Feature Parity Report

## Observation

Authoritative reference sources inspected:
1. `ORIGINAL_REQUEST.md`: Standalone Kotlin/Compose Android app parity requirements across 11 core domains, launcher icon replacement, and production release build.
2. Codebase architecture & reference implementation:
   - Root React Native / Expo implementation: `src/lib/`, `src/state/`, `src/components/`, `app/`
   - Reference documentation: `docs/ROADMAP.md`, `docs/ROADMAP_IMPLEMENTATION_PLAN.md`, `docs/REGAIN_FEATURE_GAP_ANALYSIS.md`, `docs/PRODUCTION_READINESS.md`
   - Native Android implementation in `android/app/src/main/`: Room database, Accessibility Service, Foreground Service, WorkManager, TileService, Glance Widget, Broadcast Receivers.
3. Reference app screenshots in `docs/screenshots/` and root directory:
   - `01-personal-notes.png`, `02-focus-mode.jpg`, `03-tasks.jpg`, `04-home-dashboard.jpg`, `05-screen-time-insights.png`, `06-goals.jpg`, `07-daily-habits.jpg`, `08-exam-planner.jpg`, `09-syllabus-tracker.jpg`, `10-focus-schedule.jpg`, `11-session-history.jpg`, `12-settings.jpg`.

---

## Features Discovered

| # | Category | Feature | Description | Inputs | Outputs | Error Behavior | Discovered Via |
|---|----------|---------|-------------|--------|---------|----------------|----------------|
| 1 | Focus Timer | Multi-Mode Countdown Timer | Configurable countdown timer with preset chips (15m, 25m, 45m, 60m) and custom input (1–180 min). Renders M3 circular progress ring. | Duration in minutes (1–180), start/pause/end triggers | Visual countdown, ongoing chronometer notification, Room session persistence | Negative/invalid durations clamped or rejected; dialog shown for non-integer or >180 values | `app/focus.tsx`, `src/lib/focusLogic.ts` |
| 2 | Focus Timer | Timer Persistence & Recovery | Survives backgrounding, process death, and reboot via `endAt` timestamp and `focus.active` setting. Reconciles elapsed time on app resume. | System time on resume, persisted `endAt` timestamp | Catch-up to current remaining time, auto-finish if expired with celebratory dialog | If `endAt <= now`, automatically completes session and inserts into `focus_sessions` | `src/lib/focusRecovery.ts`, `app/focus.tsx` |
| 3 | Focus Timer | Session Tags & Notes | Attach metadata tag (max 40 chars) and freeform note (max 300 chars) to any active or completed session. | Tag string, Note string | Saved in `focus_sessions.tag` and `focus_sessions.notes` | String length clamped: tag.slice(0,40), notes.slice(0,300) | `app/focus.tsx`, `src/lib/database.ts` |
| 4 | Pomodoro | 4-Round Pomodoro Cycle | 25 min work sessions followed by 5 min short breaks. Every 4th completed focus round grants a 15 min long break. Auto-prepares next phase but user manually initiates. | Focus/Break completion events, round counter (0–3) | Next phase state (`short-break`, `long-break`, `focus`), notification alert | Invalid saved round clamped to >= 0; cycles modulo 4 (`(rounds + 1) % 4 === 0`) | `src/lib/focusLogic.ts`, `app/focus.tsx` |
| 5 | Stopwatch | Open-Ended Stopwatch | Open-ended study tracking with accumulated seconds and `runStartedAt` timestamp. Pauses and resumes accurately across process death. | Start, Pause, Finish triggers | Elapsed time formatted as MM:SS or HH:MM:SS, recorded to `focus_sessions` | Open-ended stopwatch has no percentage completion in notifications | `src/lib/focusLogic.ts`, `app/focus.tsx` |
| 6 | Notes | Personal Notes Repository | Local-first Markdown/plaintext notes. Automatic debounced autosave (500ms) and hardware/gesture back flush. Real-time search across titles and bodies. | Note title, note body, search query | Note list sorted by `updated_at DESC`, Note detail editor | Empty title blocked with dialog; missing note shows error dialog and redirects back | `app/(tabs)/notes.tsx`, `app/note-editor.tsx` |
| 7 | Analytics | Screen-Time vs Focus Insights | Dual-bar visual comparison chart of daily Focus Time vs Screen Time. 1-day (Today), 7-day, and 30-day aggregation filters. | Range selection (1, 7, 30 days), UsageStats, Focus history | Visual bar chart, KPI metric cards, % change vs previous period | Missing Usage Access falls back to focus-only insights with permission prompt | `app/(tabs)/analytics.tsx`, `src/lib/insights.ts` |
| 8 | Analytics | App Classification & Categories | Classify installed apps as Productive (`#34D399`), Distracting (`#F87171`), or Neutral (`#9CA3AF`). Computes category split breakdown bar. | Package name, category selection | Category breakdown percentages, top distracting apps list | Unclassified apps default to 'neutral'; persisted in `settings` table | `app/(tabs)/analytics.tsx`, `src/lib/insights.ts` |
| 9 | Analytics | Session History & Range Filters | Filterable log of all past focus sessions and breaks. Filter by All, Focus, or Break. Range summary for Today, 7d, 30d, or Custom Date Range. | Filter enum, date range (`start`, `end`) | List of session cards with duration, timestamp, completion badge, tag, note | Invalid date format rejected with error message | `app/sessions.tsx` |
| 10 | Blocking | Whole-App Blocking | Installed launchable apps picker. When any selected app is opened, Accessibility service detects package and executes `GLOBAL_ACTION_HOME`. | Selected package names (up to 100) | Immediate redirect to launcher home screen | Max 100 packages allowed; Go Back package self-excluded; 300ms action cooldown | `app/blocking.tsx`, `GoBackBlockingService.kt` |
| 11 | Blocking | YouTube Shorts Blocking | Detects YouTube Shorts feed or tab via accessibility label and view text heuristics; executes `GLOBAL_ACTION_BACK`. | Window event on `com.google.android.youtube` | Navigates user out of Shorts feed | 300ms cooldown prevents navigation loop | `GoBackBlockingService.kt` |
| 12 | Blocking | Instagram Reels Blocking | Detects Instagram Reels tab or video surface; executes `GLOBAL_ACTION_BACK`. | Window event on `com.instagram.android` | Closes Reels screen | 300ms cooldown | `GoBackBlockingService.kt` |
| 13 | Blocking | Snapchat Spotlight Blocking | Detects Snapchat Spotlight tab; executes `GLOBAL_ACTION_BACK`. | Window event on `com.snapchat.android` | Closes Spotlight surface | 300ms cooldown | `GoBackBlockingService.kt` |
| 14 | Blocking | Adult Website Blocker | Inspects address bar URL/content text across 7 major browsers (Chrome, Firefox, Brave, Edge, Opera, Samsung, DuckDuckGo) against domain keywords. | Browser window events, address bar text | Closes browser tab / executes `GLOBAL_ACTION_HOME` | Keyword match triggers home action; screen text remains entirely on-device | `GoBackBlockingService.kt` |
| 15 | Blocking | YouTube Study Mode | Channel allowlist (up to 50 channels/@handles). When a video from a non-allowed channel plays, service clicks player close / miniplayer dismiss or sends Back. | Allowed channel strings, video surface text | Closes disallowed video playback and miniplayers | 1400ms action cooldown; fallback multi-step back dispatch | `GoBackBlockingService.kt`, `app/blocking.tsx` |
| 16 | Blocking | Strict Mode & Anti-Bypass | During active focus, exiting early requires typing commitment phrase: `"I choose to break my focus"`. Pausing requires confirmation dialog. | User input phrase string | Dismisses lock modal or denies session termination | Case-insensitive trim match; button disabled until exact phrase is typed | `app/focus.tsx` |
| 17 | Blocking | Settings / Uninstall Protection | Prevents opening `com.android.settings` to force-stop or uninstall Go Back while Strict Mode or `strictUninstallProtection` is active. | Window events from `com.android.settings` | `GLOBAL_ACTION_HOME` if view contains "com.goback.focus" or "go back" | Only triggers when Go Back app settings surface is visible | `GoBackBlockingService.kt` |
| 18 | Blocking | Split-Screen / Multi-Window Block | Closes blocked apps if launched in split-screen or multi-window mode while focus session is running. | `AccessibilityWindowInfo.TYPE_APPLICATION` count > 1 | `GLOBAL_ACTION_HOME` if any window matches blocked package | Ignores non-app windows (system UI, keyboards) | `GoBackBlockingService.kt` |
| 19 | Schedules | Weekly Focus Schedules | Recurring focus reminder blocks. Set title, 24-hr start time (HH:MM), duration (5–180m), and day selection (Sunday–Saturday). | Start time string, duration, days array | Saved in `focus_schedules`, schedules local notifications | Invalid time format or empty days rejected with dialog | `app/schedule.tsx`, `src/lib/scheduleLogic.ts` |
| 20 | Schedules | Advanced Schedules Engine | Pre-session reminders (0, 5, 10, 15, 30 min before), routine presets (Weekdays, Weekends, Custom), skip dates calendar, timezone tracking. | Pre-reminder minutes, routine type, skip dates JSON | Generates rolling concrete upcoming occurrences (35-day horizon) | Automatic wall-clock time preservation upon device timezone shift | `src/lib/scheduleAdvanced.ts`, `app/schedule-advanced.tsx` |
| 21 | Exams | Exam Timetable Planner | Exam countdown list with subjects, exam dates (YYYY-MM-DD), and optional room/notes. Hero countdown card shows UP NEXT exam and days remaining. | Subject name, date string, details string | Saved in `exams` table, sorted chronologically | Date picker validation; subject selectable from syllabus subjects | `app/exams.tsx`, `src/lib/examNotifications.ts` |
| 22 | Exams | 3-Stage Exam Reminders | Schedules 3 automatic notifications per exam: 1) 7 days prior at 9:00 AM, 2) 1 day prior at 9:00 AM, 3) Morning of exam at 7:00 AM. | Exam date, Title | Scheduled high-importance notifications on `exam-reminders-v1` | Past trigger dates ignored; rescheduled automatically on add/remove | `src/lib/examNotifications.ts` |
| 23 | Streaks | Daily Focus Goal & Celebration | Configurable daily goal (15–480 min, default 120m). Crossing goal threshold fires immediate celebratory dialog and notification alert. | `duration_seconds` of completed focus sessions | Celebratory dialog & high-importance notification on `focus-goal-complete-v1` | Triggers exactly once per day when session crosses threshold | `src/lib/goalCelebration.ts` |
| 24 | Streaks | Streak Tiers Gamification | Tier badges based on consecutive days meeting daily focus goal: Spark (3d ✨), Momentum (7d 🔥), Commitment (14d ⚡), Habit Builder (30d 🛡️), Mastery (50d 💎), Centurion (100d 👑). | Consecutive goal days count | Milestone badge, next milestone target, tier progress percentage | Tier progress clamped 0–100%; supportive non-punitive messaging | `src/lib/focusInsights.ts` |
| 25 | Streaks | 1-Day Streak Shield | Grace period protection: if a user misses exactly 1 single day within an active running streak, the shield activates and protects the streak from resetting. | Focus history date gaps | `streakShieldActive: boolean`, preserves running streak count | Shield covers at most 1 missed day; 2 consecutive missed days breaks streak | `src/lib/focusInsights.ts` |
| 26 | Widgets | Glance AppWidget | Home screen Android widget built with Jetpack Glance. Displays active status, today's focus minutes vs goal, streak badge, and next exam countdown. | SharedPreferences `go_back_widget_data` & `go_back_blocking` | 2x2 responsive widget with "Start Focus" / "View Session" CTA button | Graceful fallbacks when data is empty ("Ready to Focus") | `GoBackFocusWidget.kt`, `nativeBridge.ts` |
| 27 | Widgets | Quick Settings Tile | System Quick Settings `TileService`. Shows active session title and stopwatch/countdown state. Tap launches app directly into Focus screen. | System QS pull-down event, `focus_state` in SharedPreferences | Dynamic tile state (`STATE_ACTIVE` / `STATE_INACTIVE`), label & subtitle | Safe activity launch using `PendingIntent` / `startActivityAndCollapse` | `FocusTileService.kt` |
| 28 | Notifications | Foreground Service Chronometer | Ongoing notification displaying a live native Android Chronometer for running timers (countdown) and stopwatches (elapsed). Silent LOW importance channel. | Service intent extras: `title`, `duration_ms`, `endAt_ms`, `paused` | Persistent non-dismissible notification with Stop action | Chronometer counts down without waking app CPU every second | `FocusTimerService.kt`, `GoBackApplication.kt` |
| 29 | Notifications | Multi-Channel Alert Dispatch | Dedicated channels for each purpose: Focus Status (Low), Focus Completion Alert (Max), Schedules (High), Screen Limits (High), Exams (High), Goal Celebration (High). | Notification payloads, channel IDs | System notification tray with unique sound, light colors, and vibration patterns | Respects DND and per-channel system notification settings | `GoBackApplication.kt`, `focusNotifications.ts` |
| 30 | Presets | Focus Presets Engine | Reusable focus modes (Timer/Pomodoro/Stopwatch, duration, and session-only blocked packages). Presets override blocking only for session duration. | Name, mode, duration, package array | Saved in `focus_presets`; passes session packages to Accessibility service | Cleared on session completion; normal blocking rules remain untouched | `app/focus-presets.tsx`, `src/lib/focusPresets.ts` |
| 31 | Limits | Daily Screen-Time App Limits | Set daily minute limits and warning thresholds (50–99%, default 80%) per app. Background WorkManager checks usage and issues alerts. | Package name, limit minutes, warning percentage | Notifications on `screen-time-limits-native-v2` | Native SharedPreferences prevents duplicate notifications within same day | `app/usage-limits.tsx`, `GoBackMaintenanceWorker.kt` |
| 32 | Security | Self-Control 4-Digit PIN Lock | Protects blocking configuration, usage limits, and strict settings with a 4-digit PIN. Salted Murmur-style hash verification. | 4-digit numeric string | Blocks access until correct PIN entered; provides Change & Remove PIN flows | Non-numeric or non-4-digit input rejected; salt prevents plain text storage | `src/lib/securityPin.ts`, `PinProtectionModal.tsx` |
| 33 | Syllabus | Syllabus & Chapter Tracker | Track chapters completed per subject with interactive checkboxes and M3 squiggly progress bars. Displays overall and per-subject completion %. | Subject name, chapter title, done boolean | Saved in `subjects` table (`chapters_json`); drives dashboard syllabus card | Add/delete subjects, add/delete chapters, toggle chapter status | `app/syllabus.tsx`, `SyllabusScreen.kt` |
| 34 | Backup | JSON Backup & Restore v5 | Full database export/import (v1–v5 compatible). Validates checksum, table schemas, and row limits. Automatically takes pre-restore snapshot with Undo. | File picker JSON content | Restores all tables in an atomic SQLite transaction | Corrupted JSON or mismatched checksum aborts transaction with zero data loss | `src/lib/backupV5.ts` |
| 35 | Updates | GitHub Release Checker | WorkManager checks GitHub Releases API every 6 hours for new stable versions of `kr-ndv/Go-Back`. Alerts user if newer semver tag exists. | GitHub API releases JSON | Notification with tap-to-open GitHub Release download link | Drafts/prereleases ignored; duplicates suppressed via `last_notified_version` | `GoBackMaintenanceWorker.kt`, `releaseChecker.ts` |
| 36 | Audio | Selectable Finish Alarm Sound | Select notification sound for timer completion: System default, Soft bell (`focus_bell.wav`), Warm chime (`focus_chime.wav`), Digital alert (`focus_digital.wav`). | Sound key selection | Preview notification playback, updates notification channel sound | Fallback to system default sound if asset unavailable | `app/alarm-sound.tsx`, `src/lib/focusAlarm.ts` |

---

## Edge Cases

| # | Feature | Input | Observed Behavior |
|---|---------|-------|-------------------|
| 1 | Focus Timer | Timer running while OS kills app process in background | Source of truth is `endAt` timestamp. When reopened, if `now >= endAt`, timer completes automatically, inserts into `focus_sessions`, triggers goal celebration, and shows celebratory dialog. If `now < endAt`, recalculates `remaining = (endAt - now)` and resumes smoothly. |
| 2 | Stopwatch | App killed while stopwatch is running | Stored as `elapsedSeconds` (accumulated) and `stopwatchRunStartedAt` timestamp. On resume, elapsed time equals `elapsedSeconds + (now - stopwatchRunStartedAt)`. No elapsed time is lost. |
| 3 | Pomodoro | User completes round 4 of focus | Next phase resolves to `long-break` (15 minutes). After long break completes, next phase resets to `focus` with `completedFocusRounds = 0`. Each phase prepares automatically without auto-starting, allowing user autonomy. |
| 4 | Strict Mode | User tries to cancel focus session before countdown ends | Strict challenge modal intercepts action. Displays `"I choose to break my focus"`. Quit button remains disabled and faded until user types the exact phrase (case-insensitive). |
| 5 | Accessibility Blocking | User opens Android Settings while strict focus is active | Service inspects `com.android.settings` view hierarchy. If text contains `"com.goback.focus"` or `"go back"`, service immediately issues `GLOBAL_ACTION_HOME`, preventing user from force-stopping or disabling the app. |
| 6 | Content Blocking | Rapid scroll or navigation loop in YouTube Shorts / Instagram Reels | Cooldown timer enforces minimum 300ms interval between global actions (`sendBack` / `sendHome`), preventing event floods or UI lockups. |
| 7 | YouTube Study Mode | Disallowed video opens in full player or miniplayer | Service first attempts clicking the in-app player close / dismiss button (`"close mini player"`, `"dismiss miniplayer"`). If not clickable, executes `GLOBAL_ACTION_BACK` with sequential delayed checks (320ms, 420ms). |
| 8 | Multi-Window Blocking | User launches blocked app in split-screen mode | Service inspects `windows` of type `TYPE_APPLICATION`. If app window count > 1 and any window contains a blocked app package, `GLOBAL_ACTION_HOME` is triggered immediately. |
| 9 | Daily Goal Celebration | User completes a session crossing the daily goal threshold | Daily celebration triggers only on the exact session where `priorSecondsToday < goalSeconds && totalSecondsToday >= goalSeconds`. Subsequent sessions on the same day do not re-trigger duplicate celebration alerts. |
| 10 | Streak Shield | User misses 1 day (e.g., studies Mon, Tue, misses Wed, studies Thu) | Streak calculation cursor skips the single missed day (Wed), marks `streakShieldActive = true`, and connects Thu to Tue, preserving the 3-day active streak. If 2 consecutive days are missed, the streak breaks. |
| 11 | Weekly Schedules | User changes device timezone across time zones | Schedules detect mismatch between `schedule.timezone` and `detectedTimeZone()`. Preserves local wall-clock time (e.g. 18:00 remains 18:00 in the new location) and updates the schedule entity. |
| 12 | Exam Reminders | Exam date is today or already passed | Trigger dates in the past are ignored. Upcoming exams calculate 7d, 1d, and morning (7:00 AM) notifications. Completed exams display "Done" badge. |
| 13 | Screen Limits | App reaches warning threshold (80%) vs limit (100%) | Generates 'warning' event at 80% and 'exceeded' event at 100%. Native SharedPreferences tracks notified level (`1` for warning, `2` for exceeded) for the date key, preventing repeated nagging. |
| 14 | Database Backup | Corrupted or tampered JSON backup imported | `validateBackup` verifies JSON structure, checks row limits (max 50,000 per table), and computes FNV-1a checksum against `tables`. If mismatched, restore is rejected with error before touching SQLite. |
| 15 | Backup Restore Rollback | Restore fails or user wants to revert | Before executing restore transaction, system saves `go-back-before-last-restore.json` snapshot to local app documents directory. "Undo last restore" action allows 1-tap rollback. |
| 16 | Security PIN | Tamperer attempts to edit app blocking rules | Navigation to Blocking screen or Usage Limits screen checks `getSecurityPin()`. If set, full-screen PIN dialog locks screen until 4 digits match stored hash. Canceling navigates back to previous screen. |

---

## Logic Chain

1. **Analysis of Current Standalone Kotlin Codebase (`android/app/src/main/`)**:
   - The native app in `android/app/src/main/` has Room entities (`Entities.kt`) and DAOs (`Daos.kt`) for `notes`, `subjects`, `focus_sessions`, `focus_schedules`, `focus_presets`, `usage_limits`, `exams`, and `settings`.
   - The native `GoBackBlockingService.kt` contains the complete Accessibility Service implementation for whole-app blocking, adult sites, Shorts, Reels, Spotlight, and YouTube study mode.
   - The native `FocusTimerService.kt` contains a foreground service with Android Chronometer integration.
   - The native `GoBackMaintenanceWorker.kt` implements periodic WorkManager checks for daily screen-time limits and GitHub releases.
   - The native `GoBackFocusWidget.kt` implements a Jetpack Glance AppWidget.
   - **Root Cause of Crash / Stability Defect**: In `MainActivity.kt`, navigation routes were only partially hooked up (`TodayScreen`, `FocusScreen`, `NotesScreen`, `SettingsScreen`, `SyllabusScreen`, `AnalyticsScreen`). Crucial routes (`exams`, `blocking`, `schedule`, `presets`, `usage-limits`, `alarm-sound`, `note-editor`) were unhandled or contained placeholder stubs (e.g., line 70 `onNavigateToExams = { /* Navigate to exams */ }`).
   - Screens in `ui/screens/` (`TodayScreen.kt`, `FocusScreen.kt`, `NotesScreen.kt`, `AnalyticsScreen.kt`) contained static mock lists or hardcoded values rather than observing Room DAO Flows or triggering actual services.
   - `FocusScreen.kt` in Kotlin was an early prototype lacking the Pomodoro state machine, Stopwatch mode, foreground service connection, strict mode challenge dialog, and session recording.

2. **Resolution Strategy for 100% Feature Parity**:
   - The reference implementation in `src/lib/` and `app/` provides the exact math, state machines, and UX patterns.
   - In Kotlin/Compose, every screen must bind directly to the Room DAOs (`NoteDao`, `SubjectDao`, `FocusSessionDao`, `ExamDao`, `FocusScheduleDao`, `FocusPresetDao`, `UsageLimitDao`, `SettingDao`) via `StateFlow` / `collectAsStateWithLifecycle()`.
   - Connect `FocusScreen` in Compose to start/stop `FocusTimerService`, save to `FocusSessionDao`, evaluate daily goal celebrations, and synchronize `go_back_blocking` SharedPreferences.
   - Implement missing screens in Compose: `ExamsScreen`, `BlockingScreen`, `SchedulesScreen`, `UsageLimitsScreen`, `NoteEditorScreen`.
   - Update launcher icon mipmaps to user-specified image.
   - Configure release signing and produce signed release APK.

---

## Caveats

1. **Android Accessibility Service**: App blocking relies on Android Accessibility Events (`TYPE_WINDOW_STATE_CHANGED`, etc.). On some OEM Android versions (MIUI, ColorOS, OneUI), aggressive battery optimizations can kill accessibility services; the app UI must guide users to grant autostart and disable battery restrictions.
2. **Usage Access Permission**: Screen time reporting requires `PACKAGE_USAGE_STATS`. Android's `UsageStatsManager` provides data bucketed by time intervals and may have a brief delay (up to a few minutes) in recording foreground app usage.
3. **Exact Alarms (Android 12+)**: Scheduling exact alarms for timer completion requires `SCHEDULE_EXACT_ALARM` or `USE_EXACT_ALARM`. Go Back includes both in `AndroidManifest.xml` and falls back to WorkManager/AlarmManager.

---

## Conclusion

The reference Go-Back application contains a rich, polished feature set designed specifically around calm, local-first productivity. All 11 requested domains plus the 8 extension domains have been completely mined and documented with exact data models, algorithms, timing parameters, and error behavior.

The native Kotlin / Compose app already has the underlying architecture (Room DB, Services, Glance, Workers) in place, but requires wiring the screens to real data flows and adding the missing screen implementations to achieve full 100% feature parity without runtime crashes.

---

## Verification Method

1. **Database & Room Verification**:
   Inspect `android/app/src/main/java/com/goback/focus/data/db/GoBackDatabase.kt`, `Entities.kt`, and `Daos.kt`.
2. **Build Verification**:
   Run `cd android; .\gradlew.bat compileDebugKotlin` and `.\gradlew.bat assembleDebug` to verify Kotlin and Compose compilation.
3. **Feature Parity Test**:
   - Verify Timer / Pomodoro / Stopwatch modes and active session recovery.
   - Verify Note creation, search, debounced auto-save, and deletion.
   - Verify Exam countdown, 7d/1d/morning reminders.
   - Verify Accessibility Service blocking rules and Strict Mode challenge.
   - Verify Glance widget rendering and Quick Settings tile toggling.
