# Go Back — Regain Feature and Gap Analysis

**Purpose:** Compare the Regain app (`ai.regainapp`, EpowerX Labs) and the Regain screenshots supplied for this project with the current Go Back implementation. This is a feature-planning document, not a commitment to copy every feature or to claim that undocumented behavior has been tested.

**Sources reviewed on 2026-10-24**
- Regain Android listing: https://play.google.com/store/apps/details?id=ai.regainapp
- Regain official website: https://www.regainapp.ai/
- User-provided screenshot archive: `Regain_ss.zip`
- Go Back current `main`: https://github.com/kr-ndv/Go-Back/tree/main
- Go Back implementation roadmap: https://github.com/kr-ndv/Go-Back/blob/main/docs/ROADMAP.md

Regain's public listing was last shown as updated on 9 September 2026. Public descriptions can include Pro-only features and functionality that differs by platform, Android version, or app release. Treat this as an inventory of advertised/visible features, then verify exact behavior by testing the installed reference app.

## Executive summary

Go Back already has a useful local-first productivity foundation: tasks, notes, focus countdowns, syllabus/chapter tracking, exam planning, habits, goals, local themes, JSON backup/restore, basic UsageStats reporting, Android widgets, and experimental YouTube/Instagram blocking.

The main product gap is that Go Back currently behaves more like a student planner with a basic focus timer and experimental app-blocking add-on. Regain is more specifically a **focus-session and digital-distraction control product**: timer modes, strict sessions, flexible app/site blocking, detailed usage/focus insights, scheduling, sound/theme experiences, widgets, achievements and group study.

The immediate priority should not be implementing every feature at once. First capture and reproduce the friend's reported issues (device + Android version + exact steps), fix any reliability/data-loss problems, and then build the strongest local-first Regain-inspired features in small, testable phases.

## 1. Feature inventory from Regain

The public Google Play description and website advertise the following feature groups:

### Focus session experience
- Timer, stopwatch, and Pomodoro modes.
- Custom focus and break durations, including short and long breaks.
- Focus completion notifications and progress/streak mechanics.
- Strict Mode to resist exiting early; some enhanced blocking controls are Pro-dependent.
- Session themes and built-in focus sounds/music (for example, rain, ambient audio, white noise and lo-fi).
- Session tracking with focus insights, tags and session activity.

### App and content blocking
- Select apps to block and set daily app-use limits.
- Block full distracting apps or only specific content surfaces such as Reels/Shorts.
- Advertised supported content surfaces include YouTube Shorts, Instagram Reels, Snapchat Spotlight and Facebook Reels.
- YouTube Study Mode: allow selected learning channels while blocking Shorts and distracting navigation/content.
- Browser Study Mode: allow productive sites and block distracting sites while studying.
- Website blocking for distracting and adult sites.
- Additional block protection options advertised include uninstall prevention, floating-window blocking and split-screen blocking.
- Focus schedules and strict options interact with blocking.

### Screen-time and self-review
- Daily/weekly usage reports and application-level breakdowns.
- Productive versus distracting classifications.
- Focus duration summaries, session trends, tags, streaks and related insights.
- Screenshots show day/week/month usage navigation, a month calendar, app-specific usage charts, category/tag controls, and a profile overview with total focus time and weekly reports.
- A Phone Unlock Widget is visible in the supplied screenshots; verify its exact data and Android-version behavior before treating it as a guaranteed capability.

### Planning and accountability
- Schedule focus sessions for today and future days, including repeating schedules and a weekly calendar.
- Focus groups/rooms with friends and other students, live group sessions and leaderboards.
- Profile overview, achievements, streaks and a focus companion (“Rega”) that provides friendly nudges/motivation.
- Add-on widgets visible in the supplied screenshots: Daily Focus Goal, Usage, and Phone Unlock, in addition to focus/app surfaces.

### Customization and app settings
- Session-specific themes and focus music.
- Separate controls for notification sounds (screenshots show session start, timer end, break start and Rega sound).
- Account/subscription management, language, widgets, feedback, privacy and legal pages.

**Note:** This list describes Regain's advertised or screenshot-visible capabilities, not proof that every feature is free, works on every device, or is equally available on all platforms.

## 2. Go Back versus Regain: implementation comparison

| Feature area | Go Back status on current `main` | Gap / decision |
| --- | --- | --- |
| General student planner | Tasks, notes, exams, syllabus/chapter progress, habits and goals are implemented with local SQLite. | Keep. Treat this as a differentiator instead of replacing it with a clone of Regain. |
| Focus countdown | Implemented with presets 15/25/45/60 minutes, custom duration, focus/short-break/long-break session types, persisted active-session recovery and completion notifications. | Test on real devices. No stopwatch or automatic Pomodoro-cycle flow was found in the current focus screen. |
| Focus session history | Completed/partial sessions are stored in SQLite and today's completed minutes are surfaced on the dashboard. | Add a dedicated, useful session history and review experience; verify whether all saved sessions can be reached in the UI. |
| Session tags and notes | Current `focus_sessions` schema stores ID, start time, duration, completion state and session type. | Add optional tags/notes (and optionally link to a subject/task) so reports can explain what a session was for. |
| Strict focus mode | No complete strict-session mode was found in the current focus screen. | High value, but design it with honest disclosures, reliable native behavior and a documented safe escape path. Do not promise “impossible to bypass” without extensive testing. |
| Focus sounds/music | No in-session audio library/player was found in the current feature implementation. | Useful later. Start with small bundled, properly licensed sounds and explicit playback controls; avoid unnecessary network streaming. |
| Session themes | App-wide appearance modes and 12 accent palettes exist. | Regain-style immersive per-session backgrounds are a separate enhancement, not an existing gap in app-wide theming. |
| Selectable app blocking | Current native blocking configuration has YouTube, Instagram, Shorts, Reels and an allowed-channel list. | Too narrow compared with a general selected-app blocker and more platforms. Expand deliberately after reliability and consent review. |
| YouTube Study Mode | Current implementation has an allowed-channel setting and tries to stop playback outside the list; the feed remains available. | Different from Regain's advertised allowlisted-study experience that also controls homepage/Shorts. This needs a clear behavior spec and false-positive tests. |
| Reels/Shorts | Experimental accessibility heuristics for YouTube Shorts and Instagram Reels. | Keep clearly marked experimental until measured; add regression fixtures/test matrix for app version, language, feed previews and navigation. |
| Snapchat/Facebook content blocking | Not present in current Go Back blocking configuration. | Potential future expansion; do not add until core detection architecture is maintainable. |
| Arbitrary app selection and daily app limits | Not present in current blocking UI/configuration. | Major gap. Separate the concept of a full-app block, an app time limit and a content-surface block in the UI/data model. |
| Website / browser blocking | No dedicated website blocker or browser allowlist mode found in current Go Back routes/config. | Large feature with different technical/policy risks. Plan separately after store-policy and architecture research. |
| Strict blocking protections | No complete uninstall/floating-window/split-screen prevention feature set found. | Do not imitate invasive controls merely for parity. Evaluate Android constraints, user autonomy, recovery and store policy first. |
| Screen-time reports | Native Android UsageStats bridge; user chooses 1, 7 or 30 days; app usage data is read locally and displayed. | Extend reports: day/week/month navigation, app details, productive/distracting categories, focus-versus-screen-time comparison, and clear data limitations. |
| Phone unlock counts | No unlock-count implementation found in current usage bridge. | Investigate whether a robust, privacy-preserving metric is available and reliable across supported Android versions. |
| Focus schedule planner | Exam calendar exists, but no recurring focus-session schedule planner was found. | Add local weekly/daily scheduling before considering cloud/group features. |
| Widgets | Native quick-action widgets open Focus, Tasks, Notes, Exams, Syllabus, Habits, Goals, Usage and Blocking screens. | Add data widgets later (daily focus goal, active timer/status, usage summary) and validate update behavior across launchers. |
| Profile / achievements | Local profile name/photo settings exist; habits have streaks and goals have progress. | A consolidated focus profile, achievements and weekly report are not evident as a complete feature. Build locally first. |
| Focus groups / multiplayer | Not implemented. Roadmap intentionally defers multi-user/backend features. | Defer. Requires accounts, sync, real-time state, privacy design, anti-abuse and backend operations. Never simulate real shared rooms locally. |
| Personal companion / nudges | No Rega-like companion or focus-aware nudge system found. | Optional later feature; keep nudges user-controlled and low-noise. |
| Backup/restore | JSON export/import; current code checks schema/version and row limits and uses a database transaction. Files are not encrypted, and restore replaces the current dataset. | Keep current disclosure; extend malformed-input/migration tests and decide whether encrypted backups are needed before production. |
| Notification controls | Focus completion scheduling exists and uses the default notification sound. | Consider per-event notification preferences once timing/recovery behavior is stable. |
| Cloud accounts/subscriptions | Go Back is currently local-first with no cloud backend configured. | Preserve offline core. Don't introduce accounts, subscriptions or sync unless a defined user need justifies their cost/privacy trade-offs. |

## 3. Prioritized build roadmap

### P0 — Reproduce the friend's problems and stabilize what exists

Before new feature work:
1. Record the exact symptoms, steps, expected result and actual result.
2. Record Go Back version/build source, phone model, Android version, launcher, and whether Usage Access/Accessibility/notification permissions were granted.
3. Reproduce on the same setup if possible; don't infer the bug from the feature name.
4. Fix any crash, timer drift/double completion, failed save, misleading permission state, broken navigation, or data-loss defect before adding features.
5. Ensure the native features are tested in a real native Android build. Expo Go does not provide Go Back's custom native modules.

### P1 — Complete the focus-session core

1. Add stopwatch mode with correct elapsed-time persistence and recovery.
2. Add a proper Pomodoro state machine: focus round, short/long break rules, skip/pause/resume behavior, completion notification, and restart/process-death recovery.
3. Add optional session tags/notes and a dedicated session history with date range, duration, session type and completion state.
4. Add a Daily Focus Goal and simple weekly report, with progress derived from recorded sessions rather than hard-coded values.
5. Add an optional strict session mode only after requirements, disclosure, cancellation/escape and device testing are agreed.

### P1 — Make blocking useful and trustworthy

1. Create one consistent target-selection model for full-app blocks, app limits and content-level rules.
2. Improve YouTube/Instagram reliability and test false positives before adding more apps.
3. Introduce daily app limits and scheduled blocks with clear start/end rules.
4. Explore Snapchat/Facebook content surfaces only when target app labels and navigation behavior are testable.
5. Consider Website/Browser Study Mode as a separate project; define supported browsers, offline behavior, bypass limitations and Android policy requirements first.
6. Provide obvious permission explanations, visible active status, disable controls and a safe escape path. Do not describe accessibility heuristics as guaranteed enforcement.

### P1 — Upgrade screen-time insights

1. Keep daily/7-day/30-day reporting and improve data labels, empty states and data refresh.
2. Add app-specific daily/weekly charts and date navigation.
3. Allow local categorization into productive, distracting and neutral apps.
4. Add focus time vs screen time comparisons, tags and weekly trends.
5. Research and test whether phone-unlock counts can be measured correctly on supported versions; omit the metric if it cannot be made reliable.

### P2 — Scheduling, widgets, sounds and rewards

- Weekly/repeating focus schedules and scheduled blocking.
- Home-screen widgets for daily focus goal, current session and usage snapshot.
- Small, licensed offline focus-sound library and per-session theme options.
- Local achievements, focus streaks and editable weekly summaries.
- Per-event notification sound controls.

### P3 — Online features (explicitly deferred)

- Focus rooms, friend presence and leaderboards.
- Account, cloud sync and cross-device state.
- Companion/coach features requiring personalized recommendations or remote services.

These features require a backend and a privacy policy decision; they should not delay fixing bugs in the offline experience.

## 4. Product decisions to preserve

- **Keep Go Back's own identity and UI.** Use Regain to understand product patterns, not to copy its name, mascot, assets, copy or exact screens.
- **Local-first by default.** Tasks, notes, session data and basic planning should remain useful without an account or internet.
- **Treat blockers as productivity aids, not security products.** Be honest about Android limitations and don't market heuristic detection as unbypassable.
- **Make user consent understandable.** Explain Usage Access and Accessibility before opening system settings; state what is accessed and what remains on-device.
- **No fake multiplayer.** If group focus is ever implemented, it must be real, with a well-defined sync/backend architecture.
- **Make features testable.** New timer modes, app limits and reports need automated tests plus device test cases.

## 5. Source and verification notes

- Regain Google Play listing: https://play.google.com/store/apps/details?id=ai.regainapp
- Regain official website: https://www.regainapp.ai/
- Go Back focus screen: https://github.com/kr-ndv/Go-Back/blob/main/app/focus.tsx
- Go Back blocking screen: https://github.com/kr-ndv/Go-Back/blob/main/app/blocking.tsx
- Go Back usage screen: https://github.com/kr-ndv/Go-Back/blob/main/app/usage.tsx
- Go Back database: https://github.com/kr-ndv/Go-Back/blob/main/src/lib/database.ts
- Go Back backup/restore: https://github.com/kr-ndv/Go-Back/blob/main/src/lib/backup.ts
- Go Back roadmap: https://github.com/kr-ndv/Go-Back/blob/main/docs/ROADMAP.md

## Open input needed from device testing

The screenshots establish what the reference app looks like, but they do not reveal which bugs the friend encountered in Go Back. Add each reported problem as a separate reproducible test case before selecting the next implementation task.
