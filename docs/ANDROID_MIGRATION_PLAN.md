# Go Back — Native Android Migration Plan

**Status:** Proposal for review; documentation only. No application code or existing release has been changed.

**Target:** Replace the React Native/Expo implementation with the PBMR app-family Kotlin + Jetpack Compose architecture described in [PBMR Android App Family Architecture](https://github.com/kr-ndv/PBMR-Browser/blob/architecture/app-family-baseline/docs/APP_FAMILY_ARCHITECTURE.md).

## 1. Current baseline

The current `main` branch is a React Native + Expo SDK 54 + TypeScript app using Expo Router. The repository includes:
- Focus timer, stopwatch/Pomodoro logic, session history and recovery utilities.
- Tasks, personal notes, exam planning and syllabus/chapter tracking.
- Usage insights and user-configured app categories/limits.
- Focus presets and schedules, goals/habits and theme customization.
- SQLite persistence, a versioned JSON backup/restore implementation and unit tests.
- Android-specific behavior generated through Expo config plugins, including UsageStats, blocking/native focus state and home-screen/launcher integration.

The exact release state of each feature must be verified against the latest source and on-device builds. A README or roadmap entry is not proof of production reliability.

## 2. Non-negotiable compatibility requirements

### Android identity and upgrade path
- Preserve application ID `com.goback.focus` unless a separately approved migration says otherwise.
- An in-place APK update also requires the same signing key lineage as the installed app. Verify this against actual distributed builds before promising upgrade compatibility.
- Keep versionCode monotonic for releases.
- Preserve launcher shortcuts, deep links and widget identities where Android requires stable component identifiers.
- Do not remove the old Expo implementation until the native implementation has reached agreed feature parity and a tested migration path exists.

### Local data and backups
The current database opens `goback.db` and defines these tables: `notes`, `subjects`, `focus_sessions`, `focus_schedules`, `focus_presets`, `usage_limits`, `exams` and `settings`.

Important compatibility details:
- `subjects.chapters_json`, schedule day/skip-date data and blocked-package lists currently contain serialized JSON values.
- The current backup implementation writes schema version **5** and supports import of older backup versions 1–5.
- The security PIN setting is intentionally excluded from JSON backup.
- Existing records, IDs, dates, timestamps, timer metadata, schedule rules and setting keys must survive migration.
- Do not reset the database or silently replace backup formats.

Before creating the Room schema, document every table, column, index, default, foreign-key relationship and setting key. Confirm the actual on-device SQLite database path and the behavior of already-installed builds. Implement Room migrations and a validated v5 backup importer/exporter. Keep old backup files restorable.

### Android platform behavior
The existing Expo config plugin/native bridge path includes product-specific Android behavior. Inventory and port it as native Kotlin services/adapters before removing Expo integration:
- Usage access and UsageStatsManager reports.
- Accessibility-based focus blocking and the emergency/disable path.
- Focus-state synchronization and timer notifications.
- Alarm sounds, notification channels and scheduled reminders.
- Home-screen widget, launcher icon/shortcuts and Quick Settings integration.
- Maintenance/update checks and any app-specific system intents.

The exact current behavior must be verified from generated native sources and physical devices. Do not replace an implemented system feature with a visual placeholder.

## 3. Target Go Back module map

The native Go Back app should use the same Gradle conventions and core modules as PBMR Browser:

```text
app/
core/
  common/
  model/
  designsystem/
  navigation/
  database/
  datastore/
  platform/
feature/
  focus/
  tasks/
  notes/
  study-planner/
  exams/
  syllabus/
  schedules/
  habits-goals/
  insights/
  app-blocking/
  settings/
```

Each feature follows the shared Presentation / Domain / Data pattern:
- **Presentation:** Compose screens, screen ViewModels, immutable UI state and UI events.
- **Domain:** entities/value objects, use cases, validation and repository contracts.
- **Data:** Room/DataStore/file-backed repository implementations and platform adapters.

Go Back's product modules do not depend on PBMR Browser. The only shared modules are product-neutral infrastructure and the PBMR design system.

## 4. Migration sequence

### Phase 0 — Baseline and preservation
- Freeze a known-good snapshot/tag of the existing Expo app.
- Record all screens, routes, user-visible behaviors, permissions, shortcuts, widgets and native services.
- Capture database schema and backup fixtures from real installs.
- Record the exact signing/build configuration and existing application ID.
- Turn the current tests and production-readiness checklist into explicit acceptance criteria.

**Exit gate:** feature inventory, data fixtures and signing/upgrade facts are recorded.

### Phase 1 — Native foundation
- Create the native Kotlin/Compose app shell while keeping `com.goback.focus`.
- Adopt shared Gradle conventions, dependency injection, navigation and PBMR Design System.
- Add CI for clean debug/release compilation, lint/static checks and tests.
- Keep the app shell separate from product feature implementation.

**Exit gate:** reproducible APK builds, theme/navigation foundation and automated tests are green.

### Phase 2 — Data and platform foundation
- Define Room entities/DAOs and typed DataStore settings.
- Implement and test imports for all existing backup versions 1–5.
- Preserve the security-PIN backup exclusion and sensitive-data handling.
- Port UsageStats, AccessibilityService, alarms/notifications, widgets, shortcuts and Quick Settings adapters.
- Test permission denial, revoked permissions, process death, reboot and background restrictions.

**Exit gate:** old data can be restored without loss; critical Android integrations have test evidence.

### Phase 3 — Feature migration
Port one vertical feature slice at a time, with its UI, ViewModel, domain rules, repositories, persistence and tests:
1. Focus timer, stopwatch, Pomodoro, recovery, session history and reminders.
2. Tasks and personal notes, including autosave/search behavior.
3. Exams, syllabus and chapter progress.
4. Schedules, presets, goals and habits.
5. Usage insights, app categories and daily limits.
6. Settings, profile, backup/restore and remaining utilities.

The order can be adjusted only when dependencies or risk justify it. Do not mark a feature migrated because its screen exists; its data and Android behavior must work too.

**Exit gate:** feature-by-feature parity checklist passes.

### Phase 4 — Release and transition
- Verify APK signature continuity and in-place update on a device containing real Go Back data.
- Test restore from exported v5 backup and all older supported fixtures.
- Run a physical-device matrix for focus timer, notifications, accessibility blocking, UsageStats, widgets and reboot recovery.
- Compare startup, memory, battery and scrolling performance on low/mid-range Android hardware.
- Publish release notes that clearly explain migration and any verified limitations.
- Retire Expo workflows only after the native release is accepted.

**Exit gate:** no known data-loss issue, no critical feature regression, reproducible signed release and approved rollback plan.

## 5. Explicit migration safeguards

- No feature removal or scope reduction without a separate product decision.
- No replacement of real Android functionality with mock data or a UI-only implementation.
- No claims that Shorts/Reels/channel detection is guaranteed; retain its documented heuristic limitations.
- No silent database reset, backup-version break or package-ID change.
- Do not assume the old app's signing credentials or database location; verify them.
- Keep a rollback path to the last working Expo release until the native app is validated.

## 6. Definition of done

Go Back is considered migrated only when:
- It is a native Kotlin + Jetpack Compose application using the shared PBMR architecture and design system.
- All agreed features have equivalent behavior or have an explicitly approved replacement.
- Existing local data and supported backups are preserved.
- Permissions and Android system services behave correctly on physical devices.
- CI, unit tests, Room migration tests, Compose tests and required device tests pass.
- The app can be installed as an update where signing continuity is verified.
- Release documentation accurately describes supported behavior and limitations.
