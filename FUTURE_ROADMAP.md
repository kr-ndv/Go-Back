# Go Back — Future Feature Roadmap

**Status:** Active implementation and validation tracker  
**Last updated:** 2026-10-30  
**Applies to:** Go Back Android app  
**Current development branch:** `contrib/timer-alerts-and-roadmap`

This document is the persistent source of context for future Go Back planning. It records features discussed and agreed as candidates for future work so that development can resume even if earlier chat history is unavailable.

## Important project rules

- The current app feature set and UI are in **feature freeze** while production readiness and stability work is underway.
- Items in this document are proposals/backlog items, **not authorization to implement them now**. Start implementation only when explicitly approved.
- Do not redesign existing screens or add unrelated features without explicit approval.
- Work on the appropriate development branch and validate changes with CI and real-device testing.
- Do not merge into `main` unless the user explicitly asks.
- Preserve existing behavior that the user has specifically liked, including the Home Usage glance, session history/custom date range, focus schedule, scrollable app-blocking list, and existing productive/distracting/neutral app categories.
- Treat reliability, data integrity, privacy, accessibility, performance, and clear permission handling as priorities—not just feature count.

## Priority definitions

- **P1 — Earlier:** reliability, completing or repairing existing capabilities, and essential release-management/data-safety work.
- **P2 — Later:** useful enhancements to existing focus, usage, and scheduling workflows.
- **P3 — Optional:** quality-of-life additions that should be evaluated against complexity and user demand.

Priorities are planning guidance, not a committed release schedule.

## P1 — Earlier candidates

### 1. GitHub release checker and update notifications

**Status:** Implemented on the current development branch; automated and real-device validation remain pending.

Check the official `kr-ndv/Go-Back` GitHub Releases for a newer stable release than the version installed on the device. Notify the user when a new release is available, with the version and a link to the official release page/download.

Requirements and safeguards:
- Prefer published stable releases; do not notify users for every commit or unfinished development build.
- Check on app launch and use a battery-conscious periodic check where Android permits. Android may delay background work, so do not promise immediate delivery.
- Notify at most once per version unless the user chooses to revisit the update.
- Tapping the notification should open the release notes/release page.
- Do not silently install APKs. Keep Android's normal download, package-verification, and installation confirmation flow.
- Handle offline state, API/network failures, rate limits, and missing release assets gracefully.
- Avoid collecting unnecessary user data. The feature should not require an account.
- Consider a setting to disable update notifications, if appropriate.

**Acceptance criteria:** Current installed version is compared correctly with published releases; duplicate notifications are suppressed; invalid/unavailable responses do not crash the app; the release link points to the official repository; CI and real-device tests pass.

### 2. YouTube allowed-channel limiting

**Status:** Still testing; currently disabled in the UI. Code is present for user testing, but it must remain disabled until multi-device validation and explicit approval.

The intended behavior is to allow users to choose YouTube channels that remain accessible during a focus session while restricting other channels/content. The feature was observed to be unreliable on at least one device: a disallowed channel could continue in the mini-player. It must remain visibly marked **STILL TESTING** and unavailable to users until the behavior is dependable.

Before enabling it again:
- Test channel changes, navigation, Shorts, mini-player playback, and transitions between allowed and disallowed content.
- Test across real devices and relevant YouTube app versions.
- Check for bypasses and false positives without disrupting ordinary allowed-channel playback.
- Clearly communicate limitations if full enforcement is not technically possible.
- Do not claim reliable blocking based on a single emulator or device test.

**Acceptance criteria:** Reproducible test cases pass on multiple real-device/app-version combinations, the mini-player issue is resolved or the limitation is clearly defined, and the user explicitly approves re-enabling it.

### 3. Session/timer recovery and permission health checks

**Status:** Recovery and permission-health foundation implemented; regression and device validation remain pending.

Ensure active focus sessions remain internally consistent if Android kills the app process, the device restarts, or required permissions change. Explain when Usage Access or other required permissions are missing or disabled.

Requirements:
- Use a reliable source of truth for session timing; avoid counting time twice after recovery.
- Reconcile notification, timer, blocking, and stored session state.
- Recover gracefully after process death/restart, or clearly explain cases that cannot be recovered.
- Provide actionable permission explanations without repeatedly nagging users.
- Never let recovery errors corrupt session history.

**Acceptance criteria:** Tests cover process death, restart, permission revocation, duplicate events, and interrupted persistence without duplicate or negative durations.

### 4. Backup and restore

**Status:** Backup/restore schema v5 and recovery snapshot implemented; validation remains pending.

Allow users to export and restore appropriate Go Back data when changing devices or reinstalling the app.

Potential data: settings, focus schedules, app classifications, and session history, subject to a documented schema and platform constraints.

Requirements:
- Make it clear which data is included and excluded.
- Validate imported files and handle incompatible/invalid backups safely.
- Avoid overwriting existing data without a clear user confirmation and recovery path.
- Protect exported data and explain that the file may contain personal usage history.
- Keep the core app usable offline.

**Acceptance criteria:** A valid export can be restored; malformed/incompatible files fail safely; duplicate records and unintended data loss are prevented.

## P2 — Later candidates

### 5. Weekly and monthly insights

**Status:** Implemented on the development branch; data-quality/device validation pending.

Extend existing usage and session history with clear trends, most productive days, distracting-app patterns, and comparisons with previous periods. Use actual recorded data, explain incomplete permissions/data, and avoid presenting estimates as exact measurements.

### 6. Focus modes and presets

**Status:** Implemented on the development branch; regression validation pending.

Let users save reusable configurations such as Study, Deep Work, Reading, and Sleep, including duration and selected blocked apps. Presets should reuse existing capabilities and avoid duplicating settings or changing current defaults unexpectedly.

### 7. Daily screen-time limit alerts

**Status:** Implemented on the development branch; Usage Access and real-device validation pending.

Let users configure daily usage limits for selected apps and receive warnings when approaching or exceeding them. Keep this separate from the existing behavior where apps are blocked only during focus sessions. Clearly communicate Android Usage Access limitations and avoid promising enforcement that the OS cannot guarantee.

### 8. Home-screen widget and Quick Settings tile

**Status:** Implemented on the development branch; launcher and device validation pending.

Explore Android widget/Quick Settings integration for starting a focus session, viewing remaining time, or opening the app quickly. Ensure controls remain synchronized with the actual session and do not create duplicate timers or bypass intended session rules.

## P3 — Optional candidates

### 9. Focus streaks and personal goals

**Status:** Implemented on the development branch; analytics regression validation pending.

Allow a user to set a realistic daily focus goal and see consistency/milestones. Keep the experience supportive: avoid shaming, punitive mechanics, or treating missed days as failure.

### 10. Advanced focus scheduling

**Status:** Implemented on the development branch; timezone and device validation pending.

Build on the existing focus schedule with weekday/weekend routines, skip dates, and reminders before sessions. Preserve current schedules and ensure daylight-saving/time-zone changes do not trigger unintended sessions.

## Cross-cutting quality backlog

These improvements should be considered during relevant maintenance work and do not necessarily require new UI:

- **Session and database integrity:** prevent duplicate sessions, incorrect totals, and lost history.
- **Permission health:** identify missing/revoked permissions and explain how to restore them.
- **Offline-first reliability:** keep core focus tools working without network access.
- **Battery/performance audit:** minimize unnecessary background work and avoid timer/notification drift.
- **Accessibility audit:** verify screen-reader labels, contrast, touch targets, and text scaling.
- **Privacy transparency:** document local data, exports, permissions, and any network requests.
- **Notification quality:** use clear, useful notification text and avoid disturbing active focus sessions unnecessarily.
- **Live focus notification progress (implemented 2026-10-30 on the development branch):** native Android chronometer/countdown and progress indicators are committed for Timer/Pomodoro; Stopwatch uses elapsed chronometer without a fake percentage; paused sessions retain frozen status. The Expo notification remains the fallback when the native bridge is unavailable. CI and physical-device validation are still pending. See `docs/ROADMAP_IMPLEMENTATION_PLAN.md`.
- **Regression coverage:** protect existing functionality with automated tests and real-device checks.

## Suggested order of work

1. Finish production-readiness checks and regression testing for the current feature-frozen release.
2. Continue reliability work, including session recovery and permission handling.
3. Investigate and validate YouTube allowed-channel limiting; keep it disabled until explicitly approved.
4. Implement the GitHub stable-release checker/update notification.
5. Complete the CI and physical-device validation matrix for the implemented branch; keep YouTube allowlist unavailable until it passes its separate multi-device acceptance criteria.

This is a proposed order, not a commitment to ship every item.

## Existing behavior to preserve

Unless explicitly approved otherwise, future work must preserve:
- Home dashboard's data-first focus minutes and task/syllabus progress.
- The Home Usage tool showing today's total phone usage.
- Existing productive/distracting/neutral app categorization in Screen-time insights.
- Focus timer's ongoing notification with remaining/elapsed time, native progress/chronometer implementation on the development branch, the silent status channel, and the completion alarm.
- Session History filters, including Today, 7 days, 30 days, and custom date range.
- The existing focus schedule.
- The scrollable launchable-app blocking list and its current focus-only blocking behavior.
- The current UI and visual design during feature freeze.
- Hidden Local profile ID display/share controls; underlying ID generation remains intact.

## How to use this document in future work

Before implementing a roadmap item:
1. Read this file and inspect the current repository state.
2. Confirm the item is still relevant and has not already been implemented.
3. Check platform/API limitations and define acceptance tests.
4. Ask for explicit approval before starting feature work.
5. Work on the appropriate branch, run CI, and test on real Android devices.
6. Update this file's status and notes when the user approves a change or implementation state changes.

**End of roadmap.**
