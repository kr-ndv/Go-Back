# Go Back — Future Feature Roadmap

**Status:** Planning only  
**Last updated:** 2026-10-30  
**Applies to:** Go Back Android app  
**Current development branch:** `feature/live-focus-notification-progress`

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

**Status:** Planned; not implemented.

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

**Status:** Still testing; currently disabled in the UI.

The intended behavior is to allow users to choose YouTube channels that remain accessible during a focus session while restricting other channels/content. The feature was observed to be unreliable on at least one device: a disallowed channel could continue in the mini-player. It must remain visibly marked **STILL TESTING** and unavailable to users until the behavior is dependable.

Before enabling it again:
- Test channel changes, navigation, Shorts, mini-player playback, and transitions between allowed and disallowed content.
- Test across real devices and relevant YouTube app versions.
- Check for bypasses and false positives without disrupting ordinary allowed-channel playback.
- Clearly communicate limitations if full enforcement is not technically possible.
- Do not claim reliable blocking based on a single emulator or device test.

**Acceptance criteria:** Reproducible test cases pass on multiple real-device/app-version combinations, the mini-player issue is resolved or the limitation is clearly defined, and the user explicitly approves re-enabling it.

### 3. Session/timer recovery and permission health checks

**Status:** Proposed reliability work.

Ensure active focus sessions remain internally consistent if Android kills the app process, the device restarts, or required permissions change. Explain when Usage Access or other required permissions are missing or disabled.

Requirements:
- Use a reliable source of truth for session timing; avoid counting time twice after recovery.
- Reconcile notification, timer, blocking, and stored session state.
- Recover gracefully after process death/restart, or clearly explain cases that cannot be recovered.
- Provide actionable permission explanations without repeatedly nagging users.
- Never let recovery errors corrupt session history.

**Acceptance criteria:** Tests cover process death, restart, permission revocation, duplicate events, and interrupted persistence without duplicate or negative durations.

### 4. Live notification progress for Timer, Pomodoro, and Stopwatch

**Status:** Approved to investigate and implement on `feature/live-focus-notification-progress`; not yet implemented.

Upgrade the existing persistent focus notification from a periodically refreshed text-only readout into a useful live status surface for every focus mode. The current implementation refreshes the notification approximately every 15 seconds; it does not expose a true progress indicator.

Requirements:
- **Timer:** show remaining time and a progress indicator that reflects elapsed/remaining duration.
- **Pomodoro:** show the current phase (Focus, Short Break, or Long Break), remaining time, and progress for that phase; update correctly when a phase completes and the next phase is prepared.
- **Stopwatch:** show continuously increasing elapsed time and an appropriate elapsed-time indicator.
- Keep the notification stable and ongoing while the session is running; update the existing notification rather than dismissing/recreating it on every refresh where the platform permits.
- Use the persisted session timestamps as the source of truth. The displayed progress must recover accurately after app backgrounding, process recreation, and device restart; do not depend on a JavaScript interval remaining alive.
- Prefer native Android notification capabilities (for example, chronometer/countdown and progress support) where Expo Notifications cannot express the required behavior. Avoid an aggressive one-second schedule of notification reposts; balance freshness with Android battery and notification-update limits.
- Preserve the existing completion alarm, session persistence, app-blocking synchronization, notification permission handling, and tap-to-open behavior.
- Handle paused sessions clearly and do not display a moving progress indicator while paused.
- Keep the existing notification concise, accessible, and consistent with Go Back's visual identity. Do not add unrelated notification actions or redesign the Focus screen as part of this work.

**Acceptance criteria:** Timer, all Pomodoro phases, and Stopwatch each display the correct live time/progress; pause/resume and completion transitions stay synchronized; notification state is restored after app process death/relaunch; no duplicate ongoing notifications or completion alarms are created; battery-conscious behavior is verified; TypeScript/Jest/Android build checks pass; and the behavior is tested on physical Android devices across supported Android versions.

### 5. Backup and restore

**Status:** Proposed.

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

### 6. Weekly and monthly insights

Extend existing usage and session history with clear trends, most productive days, distracting-app patterns, and comparisons with previous periods. Use actual recorded data, explain incomplete permissions/data, and avoid presenting estimates as exact measurements.

### 7. Focus modes and presets

Let users save reusable configurations such as Study, Deep Work, Reading, and Sleep, including duration and selected blocked apps. Presets should reuse existing capabilities and avoid duplicating settings or changing current defaults unexpectedly.

### 8. Daily screen-time limit alerts

Let users configure daily usage limits for selected apps and receive warnings when approaching or exceeding them. Keep this separate from the existing behavior where apps are blocked only during focus sessions. Clearly communicate Android Usage Access limitations and avoid promising enforcement that the OS cannot guarantee.

### 9. Home-screen widget and Quick Settings tile

Explore Android widget/Quick Settings integration for starting a focus session, viewing remaining time, or opening the app quickly. Ensure controls remain synchronized with the actual session and do not create duplicate timers or bypass intended session rules.

## P3 — Optional candidates

### 10. Focus streaks and personal goals

Allow a user to set a realistic daily focus goal and see consistency/milestones. Keep the experience supportive: avoid shaming, punitive mechanics, or treating missed days as failure.

### 11. Advanced focus scheduling

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
- **Regression coverage:** protect existing functionality with automated tests and real-device checks.

## Suggested order of work

1. Finish production-readiness checks and regression testing for the current feature-frozen release.
2. Continue reliability work, including session recovery and permission handling.
3. Implement live notification progress for Timer, Pomodoro, and Stopwatch on the approved feature branch.
4. Investigate and validate YouTube allowed-channel limiting; keep it disabled until explicitly approved.
5. Implement the GitHub stable-release checker/update notification.
6. Evaluate backup/restore and then prioritize insights, presets, limits, widgets, goals, and scheduling based on value and complexity.

This is a proposed order, not a commitment to ship every item.

## Existing behavior to preserve

Unless explicitly approved otherwise, future work must preserve:
- Home dashboard's data-first focus minutes and task/syllabus progress.
- The Home Usage tool showing today's total phone usage.
- Existing productive/distracting/neutral app categorization in Screen-time insights.
- Focus timer's ongoing notification with remaining/elapsed time.
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
