# Go Back — Approved Roadmap Implementation Plan

**Branch:** `contrib/timer-alerts-and-roadmap`  
**Approval:** User explicitly approved implementation of all ten items in `FUTURE_ROADMAP.md` on 2026-10-29.  
**UI constraint:** Preserve the existing Go Back visual language and screen structure. New controls/screens must reuse the app's current typography, spacing, cards, chips, buttons, colours, theme tokens and accessibility patterns. No visual redesign.

## Guiding rules

1. Do not merge into `main` without explicit approval.
2. Prefer additive database migrations and backward-compatible settings.
3. Keep the app local-first; only the GitHub release checker requires network access.
4. Do not silently install updates or enforce unsupported Android behaviour.
5. Keep YouTube allowed-channel limiting disabled and marked `STILL TESTING` until the roadmap's multi-device acceptance tests pass and the user explicitly approves re-enabling it.
6. Reuse existing screens/components whenever possible. Add new routes only when a feature would make an existing screen crowded or ambiguous.
7. Every feature must have automated tests where logic can be tested without Android, plus real-device test notes for native behaviours.

## Current-state audit

The current branch already has:

- Timer, Pomodoro and Stopwatch modes with active-session persistence.
- Ongoing focus notifications and scheduled completion notifications.
- Session history with Today / 7 days / 30 days / custom range.
- Daily focus goal setting.
- Weekly repeating focus reminders.
- Usage Access integration with Today / 7 / 30-day app totals.
- Productive / distracting / neutral app categories.
- Selectable whole-app blocking and experimental Shorts/Reels detection.
- YouTube allowlist code in the native service, intentionally disabled in the UI.
- JSON backup/restore with schema validation and transactional restore.
- Home-screen quick-action widget.
- Local goals and habits.

The implementation below extends or hardens these instead of duplicating them.

---

## Shared foundation and schema changes

### Database migration

Add two new tables and extend `focus_schedules` additively:

### `focus_presets`
- `id TEXT PRIMARY KEY`
- `name TEXT NOT NULL`
- `mode TEXT NOT NULL DEFAULT 'timer'`
- `duration_minutes INTEGER NOT NULL DEFAULT 25`
- `blocked_packages_json TEXT NOT NULL DEFAULT '[]'`
- `created_at TEXT NOT NULL`

Purpose: reusable Study / Deep Work / Reading / Sleep style configurations without changing the user's global blocking defaults.

### `usage_limits`
- `id TEXT PRIMARY KEY`
- `package_name TEXT NOT NULL UNIQUE`
- `app_name TEXT NOT NULL`
- `limit_minutes INTEGER NOT NULL`
- `warning_percent INTEGER NOT NULL DEFAULT 80`
- `enabled INTEGER NOT NULL DEFAULT 1`
- `created_at TEXT NOT NULL`

Purpose: local daily app-limit alert rules. Limits are alerts only, not guaranteed OS enforcement.

### Extend `focus_schedules`
Add columns with safe defaults:
- `reminder_minutes INTEGER NOT NULL DEFAULT 0`
- `skip_dates_json TEXT NOT NULL DEFAULT '[]'`
- `routine_type TEXT NOT NULL DEFAULT 'custom'`
- `timezone TEXT NOT NULL DEFAULT ''`

Existing schedule rows remain valid and continue to work.

### Backup schema
Move backup schema to v5 and include:
- `focus_presets`
- `usage_limits`
- the new schedule columns

Continue accepting older backups. Validate data strictly before replacing current data. Add an automatic pre-restore snapshot so the last successful restore can be undone from Settings.

---

# 1. GitHub release checker and update notifications

## Implementation

Create `src/lib/releaseChecker.ts` and a shared background-maintenance task.

- Read installed version from Expo config.
- Request only the official `kr-ndv/Go-Back` GitHub Releases endpoint.
- Ignore drafts and prereleases.
- Compare semantic version components safely.
- Store `updates.lastNotifiedVersion` to suppress duplicates.
- Store `updates.enabled` with default `true`.
- On app launch, check once without blocking startup.
- Register a battery-conscious background task where Android permits; task timing remains inexact.
- Create a dedicated notification channel.
- Put only official GitHub release URLs into notification data.
- On notification tap, validate the host/path before opening the release page.
- Never download or install an APK automatically.

## UI

Add an `App updates` card/row in existing Settings styling:
- enable/disable update notifications
- `Check now`
- last checked / current status text

## Tests

- semver comparison
- draft/prerelease filtering
- malformed response handling
- duplicate suppression
- official URL validation

---

# 2. YouTube allowed-channel limiting

## Implementation status rule

Strengthen the native implementation now, but **do not re-enable the UI toggle** until device testing satisfies the roadmap acceptance criteria.

## Native changes

- Keep allowlist configuration persisted.
- Improve watch-page/channel matching and normalize handles/names.
- Explicitly close full player and mini-player when a disallowed video remains active.
- Add cooldown/debounce protection so repeated accessibility events do not create navigation loops.
- Ensure the rule respects `blockOnlyDuringFocus`.
- Add diagnostic reason/state fields available to the app for testing without collecting or uploading user content.
- Preserve ordinary allowed-channel playback.

## Validation matrix

Test on multiple real devices / YouTube versions:
- allowed channel -> allowed channel
- allowed -> disallowed
- disallowed -> allowed
- mini-player transitions
- Shorts
- search results
- subscriptions/home feed navigation
- back/forward navigation
- screen rotation where applicable

The UI remains disabled and labelled `STILL TESTING` until the user explicitly approves re-enabling after these tests.

---

# 3. Session/timer recovery and permission health

## Recovery model

Keep timestamps as the source of truth:
- countdown/Pomodoro: persisted `endAt`
- Stopwatch: persisted accumulated seconds + run-start timestamp

Add a stable `sessionId` to active-session state so repeated completion/recovery cannot create duplicate history rows.

## Reconciliation

Create `src/lib/focusRecovery.ts`:
- validate persisted active state
- clamp invalid/negative values
- reconcile expired timers on launch
- prevent double completion
- reconcile notification identifiers
- synchronize the native blocking/widget snapshot
- preserve paused state

## Native snapshot

Extend the existing Android bridge with a small shared focus-state snapshot so:
- app blocking can detect an expired timed focus session even after JS process death
- widget and Quick Settings can show active/inactive status
- process restart can restore a coherent state

## Permission health

Create `src/lib/permissionHealth.ts` to report:
- notification permission
- Usage Access
- Accessibility service

Use existing screen styles to show actionable status on relevant screens/Settings. No repeated modal nagging.

## Tests

- timer expiry while process is absent
- stopwatch recovery
- duplicate completion guard
- malformed persisted state
- revoked permission states
- no negative durations

---

# 4. Backup and restore

The core already exists; harden it rather than redesign it.

## Changes

- schema v5 with new tables/columns
- export manifest describing included data
- strict validation for IDs, dates, durations, arrays and row counts
- automatic pre-restore snapshot
- `Undo last restore` action if a valid snapshot exists
- retain atomic SQLite transaction behaviour
- clear disclosure that backup files may contain personal usage/focus history and are not encrypted

## Tests

- round-trip v5
- restore older v1-v4 backups
- malformed JSON
- missing/invalid tables
- duplicate IDs/unique conflicts
- rollback on failed restore
- successful undo snapshot restore

---

# 5. Weekly and monthly insights

## Data

Create `src/lib/insights.ts` for pure aggregation.

Extend the Android usage bridge with daily usage buckets so the app can calculate real trends instead of estimating from an aggregate total.

Calculate:
- current 7 days vs previous 7 days
- current 30 days vs previous 30 days
- daily focus minutes
- daily phone usage
- productive/distracting/neutral usage using existing categories
- top distracting apps for the selected period
- most focused/productive days

## UI

Add `app/insights.tsx` using the same header, cards, chips, typography and bar patterns already used by Session History and Screen-time Insights.

Entry points:
- Screen-time Insights
- Session History

No home dashboard redesign.

## Accuracy rules

- If Usage Access is missing, show focus-only insights and explain that phone-usage trends are unavailable.
- Never label incomplete Android data as exact.

---

# 6. Focus modes and presets

## Data

Use `focus_presets` table.

Each preset stores:
- name
- Timer / Pomodoro / Stopwatch mode
- duration when applicable
- selected blocked apps for that preset

## Behaviour

- Applying a preset does not overwrite global blocking rules.
- Preset-specific packages are stored as a temporary focus-session override in the native bridge.
- The override is active only while that focus session is running and is cleared when the session ends.
- Preset choice is persisted with active-session recovery.

## UI

Add `app/focus-presets.tsx` using existing card/chip/list styles.

Add a small `Presets` entry/action from the current Focus experience without altering its layout hierarchy.

---

# 7. Daily screen-time limit alerts

## Data and behaviour

Use `usage_limits` table.

Users can configure per-app:
- daily minute limit
- warning threshold (default 80%)
- enabled/disabled state

Create `src/lib/usageLimits.ts`:
- compare today's Android UsageStats with limits
- warn once when approaching a limit
- warn once when exceeding a limit
- suppress duplicates per package/date/threshold

Use the shared background-maintenance task for battery-conscious periodic checks and also evaluate limits when the app returns to foreground.

This feature sends alerts only; it does not claim exact real-time enforcement.

## UI

Add `app/usage-limits.tsx` in the existing Screen-time styling, linked from `app/usage.tsx`.

---

# 8. Home-screen widget and Quick Settings tile

## Existing widget

Keep the current quick-action widget and extend it rather than replacing it.

## Focus snapshot

Native shared state contains:
- active/inactive
- mode
- end timestamp for countdown/Pomodoro
- elapsed/start timestamp for Stopwatch

## Widget

- preserve existing shortcuts
- show current focus state when active
- use Android `Chronometer` where practical so countdown/elapsed display can update without waking JS every second
- tapping focus status opens the current Focus screen
- never create a duplicate session from the widget

## Quick Settings

Add a `TileService` through the existing config plugin.

- inactive: label indicates Focus is ready; tap opens Focus
- active: tile state shows active Focus; tap opens the current Focus screen
- do not silently start a timer from a locked device
- add Android 13+ request-to-add-tile helper where available; older Android remains manual

---

# 9. Focus streaks and personal goals

The daily focus goal already exists, so keep it and derive streaks from recorded sessions.

## Logic

Create pure helpers in `src/lib/focusInsights.ts`:
- daily focus totals
- current goal streak
- best goal streak
- goal days in current week/month
- lightweight milestones based only on recorded data

A day counts toward the goal streak when recorded focus time reaches the user's configured daily goal.

## UI

Extend Session History/Insights with cards matching existing styling:
- current streak
- best streak
- goal days this week/month

Use supportive language; missed days never display punitive wording.

---

# 10. Advanced focus scheduling

## Preserve current schedules

Existing rows and weekly reminder behaviour remain valid through additive defaults.

## New options

- Weekday preset (Mon–Fri)
- Weekend preset (Sat–Sun)
- custom days
- optional reminder before session (0/5/10/15/30 minutes)
- skip dates
- timezone stored with each schedule

## Scheduling strategy

Instead of relying only on indefinite repeating notifications, maintain a rolling set of upcoming concrete occurrences. This allows skip dates and pre-reminders to be honoured and makes timezone changes easier to reconcile.

- regenerate upcoming occurrences on app launch, schedule edits and maintenance runs
- exclude skip dates
- recalculate when the device timezone differs from the stored schedule timezone
- preserve the user's local wall-clock start time
- avoid duplicate scheduled notifications by tagging each occurrence

Android may delay background maintenance; messaging must remain honest about that limitation.

---

## Additional user-requested timer/notification work

These changes are part of the same branch and should land before the broader feature work is considered ready:

### Selectable timer alarm
- Settings entry using existing style
- System default + bundled Go Back sounds
- preview action
- custom sound assets bundled through the Expo notifications config plugin

### Heads-up completion notification
- separate silent ongoing-status channel from max-importance completion channels
- never reuse the legacy low-importance channel for completion alerts
- schedule completion notifications on the correct Android channel
- keep foreground `shouldShowBanner` / `shouldShowList`
- respect the user's Android channel/DND settings

### Live Timer / Pomodoro / Stopwatch notification progress

The current implementation still relies on an Expo notification whose body is refreshed about every 15 seconds by dismissing and recreating the ongoing notification. That gives a periodic text readout, not a continuously advancing notification progress surface.

Implement this as a native Android enhancement while retaining the Expo fallback on platforms without the native bridge:

- Keep one stable ongoing notification ID and update it in place; do not dismiss/recreate it for routine progress updates.
- Timer and each Pomodoro phase: show a native countdown chronometer where supported plus a determinate progress bar (elapsed / total). Include the phase label: Focus, Short Break, or Long Break.
- Stopwatch: show a native elapsed-time chronometer based on the persisted run-start timestamp; do not invent a finite completion percentage for an open-ended stopwatch.
- Paused session: freeze the displayed time and progress and clearly label it as paused.
- Use the persisted `endAt` / accumulated stopwatch seconds + run-start timestamp as the source of truth. JS intervals must not be required to keep the clock moving while the app is backgrounded.
- Update determinate progress at a battery-conscious cadence; let Android's chronometer animate the displayed time between updates. Avoid a one-second notification repost loop.
- Keep the existing completion alarm, silent ongoing-status channel, notification permission behavior, notification tap deep link, session recovery, and native focus-blocking state synchronized.
- Ensure Pomodoro phase changes update the same notification without stale labels or a gap in status.

**Acceptance tests:** Timer, Focus, Short Break, Long Break, Stopwatch, pause/resume, app backgrounding, process death/relaunch, phase transition, timer completion, notification permission denied, and duplicate-notification cleanup. Validate on physical Android devices across supported API levels. A green CI run alone is not sufficient for the native chronometer/progress behavior.


---

# Staged commit order

## Stage A — foundation and focus reliability
1. schema migrations + backup v5
2. active-session recovery/duplicate guard
3. alarm selector + notification-channel fix
4. permission health

## Stage B — release/update and background maintenance
5. release checker
6. background task registration
7. notification tap handling

## Stage C — insights, streaks and limits
8. daily usage buckets native bridge
9. insights aggregation + screen
10. streak cards
11. usage-limit rules + alert evaluation

## Stage D — presets and scheduling
12. focus presets + native per-session blocking override
13. advanced schedule migration/UI/scheduling engine

## Stage E — Android surfaces and YouTube hardening
14. widget focus-state extension
15. Quick Settings TileService
16. YouTube allowlist reliability changes while keeping UI disabled

## Stage F — verification/documentation
17. implement and test native live notification progress for Timer/Pomodoro/Stopwatch
18. unit tests and CI fixes
19. update `FUTURE_ROADMAP.md` implementation statuses
20. update real-device test matrix
21. open a draft PR; do not merge to `main`

# Required verification before merge

Automated:
- TypeScript typecheck
- Jest tests
- Android build workflow

Physical Android device:
- timer/stopwatch/Pomodoro recovery
- live notification chronometer/progress for Timer, all Pomodoro phases and Stopwatch
- heads-up notification and custom alarm sounds
- notification denied/DND cases
- Usage Access removed/restored
- Accessibility removed/restored
- backup export/restore/undo
- usage limits
- widget state
- Quick Settings tile
- timezone/schedule behaviour
- YouTube test matrix on multiple devices/app versions before re-enabling the allowlist

Until those native tests pass, the PR can be code-complete but should remain a draft/review candidate rather than being merged into `main`.
