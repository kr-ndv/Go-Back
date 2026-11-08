# Native migration feature-parity audit

**Baseline reviewed:** default-branch source tree before this migration cleanup (November 7, 2026). This audit distinguishes active source code from old release notes, agent handoffs and screenshot archives.

## Present in the current native app

| Feature | Native location / implementation | Maintenance in this pass |
|---|---|---|
| Home dashboard, daily focus goal and streak | Today screen and TodayViewModel | Profile photo now displayed in home avatar |
| Countdown focus timer and Pomodoro | FocusScreen / FocusViewModel / FocusTimerService | Preset navigation added |
| Stopwatch and strict-mode early-exit challenge | FocusScreen / FocusViewModel | Retained |
| Session recovery, history, tags and notes | FocusViewModel / AnalyticsScreen / Room | Retained |
| Searchable app selection and Accessibility blocking | BlockingScreen / GoBackBlockingService | Launcher package visibility added |
| Shorts / Reels / Spotlight / adult-site checks | Accessibility service rules | Experimental behaviour remains best-effort |
| YouTube allowed-channel configuration | Blocking configuration and Accessibility service | Retained; validate on devices before relying on it |
| Daily per-app usage limits and category reports | UsageLimitsScreen / AnalyticsScreen / maintenance worker | Retained |
| Subjects and chapter checklist | SyllabusScreen / SubjectEntity | Retained |
| Exam planner and notifications | ExamsScreen / ExamEntity / receivers | Retained |
| Recurring focus schedules, skip dates and reminders | SchedulesScreen / FocusScheduleEntity / worker | Retained |
| Personal notes | NotesScreen / NoteEditorScreen / NoteEntity | Retained |
| Theme mode and 12 accent palettes | SettingsScreen / GoBackTheme | Saved palette selection now drives Material 3 colors; legacy lowercase values are accepted |
| Local profile display name and photo | SettingsScreen / TodayViewModel | Photo picker, persisted URI and native avatar added |
| PIN protection | SecurityPinDialog / SecurityPinUtil | Retained |
| Focus finish sound choice | SettingsScreen / FocusAlarmReceiver | Raw sound assets bundled and per-sound channels added |
| Quick Settings Focus tile and home widget | FocusTileService / Glance widget | Retained |
| Release notifications | GoBackMaintenanceWorker | Retained |
| Local backup / restore | BackupRestoreScreen / Room DAOs | Native JSON export/restore with pre-restore snapshot added |

## Added during this maintenance pass

- Native reusable focus presets (timer, Pomodoro and stopwatch) with optional per-session app blocking. Applying a preset does not overwrite global blocker settings.
- JSON backup and restore for all eight current Room tables, schema-version validation, checksum validation, transaction-based replacement, local undo snapshot and local PIN preservation.
- A 0→1 Room migration for existing Expo SQLite databases. It rebuilds the known legacy tables to the canonical Room schema and copies existing data, defaulting only documented columns introduced in later legacy versions.
- Custom focus finish-sound resources and per-sound notification channels.
- Profile image picker and shared avatar rendering on Settings and Home.
- Launcher icon fallback resources for pre-Android 8 devices.
- Removal of Expo build configuration, AI-agent handoff archive, compiler/crash logs, duplicate screenshots and all GitHub Actions workflows.

## Historical references that are not in the latest active source tree

The retained Beta 6 release note and October 25 screenshots describe separate **Tasks**, **Daily Habits** and **Goals** screens. The current Expo routes, Room schema and native Kotlin sources in the reviewed default branch do not contain those routes, tables or entities. They therefore cannot be described as migrated features based on the available source. This cleanup does not silently delete working Tasks/Habits/Goals code: no such active implementation was present in the source tree being migrated. If those screens were part of an earlier private build and are required for release, they must be recreated from that build/specification and added to the release test matrix.

The project also intentionally excludes online group focus, leaderboards, cloud accounts/sync, focus music libraries and the Rega companion, consistent with the older product-scope notes.

## Not yet release-verified

No Android build, Gradle test run or physical-device test was performed in this pass. The Room migration needs testing against copies of real beta databases; Accessibility and UsageStats behaviour must be validated on devices and supported target-app versions. See [the release test matrix](RELEASE_TEST_MATRIX.md) and [production readiness](PRODUCTION_READINESS.md).
