# Go Back — implementation roadmap

## Stage 1: Foundation (in progress)
- Expo + React Native + TypeScript + Expo Router
- Premium forest/lime design system and navigation
- Local SQLite schema for tasks, notes, subjects, focus sessions, weekly focus schedules, exams and settings
- First dashboard, task list, personal notes and basic focus timer
- Theme selection and EAS APK build profiles

## Stage 2: Core productivity
- Focus timer uses absolute end timestamps, app-resume recalculation, and completion notifications; countdown, stopwatch and guided Pomodoro modes are implemented with session recovery; validate behavior on real Android devices
- Task priorities and due dates; local goals, daily habits, and streaks are implemented in the current prototype
- Exam countdown, exam timetable, and student syllabus/chapter tracker with computed progress
- Focus sessions support optional tags/notes, a recent session-history screen, a 7-day summary, and an adjustable daily focus goal.
- Usage insights support locally saved productive/distracting/neutral categories per app.
- Personal notes editor with debounced autosave and search across titles and contents is implemented; pinning and standalone note export remain planned
- Weekly focus reminders are implemented with day/time/duration controls and recurring local notifications. They do not start sessions or block apps automatically.
- Haptics, font scaling, accent color and dashboard customization

## Stage 3: Android system features
- An Android home-screen quick-actions widget is implemented through an Expo config plugin; add a live focus-status and exam-countdown widget in a later pass
- Android UsageStatsManager bridge and explicit Usage Access onboarding are implemented in the native config plugin; real-device validation and usage-history accuracy checks remain
- Android AccessibilityService blocking supports a user-selected list of installed launchable apps plus the original YouTube/Instagram rules; Shorts/Reels tab detection and channel matching remain heuristic. Validate all rules on devices and refine false positives
- Scheduled app blocking, daily app-use limits, strict mode and a clear emergency exit remain planned; focus-schedule reminders are not blocking rules
- Test on real Android devices and document battery/background behavior

## Stage 4: Quality and release
- Unit and component tests, accessibility review, error handling and migration strategy
- JSON backup/restore supports schema versions 1–4, preserves focus mode/tag/note metadata, and includes weekly schedules while remaining compatible with older backups; encryption remains outstanding
- EAS or local Gradle release build, APK install test, SHA-256 checksum and release notes
- CI checks and privacy/security review

## Intentionally deferred
- AI features, ads, analytics, trackers, cloud sync and backend services
- Live friend/accountability features and real multi-user rooms/leaderboards require a backend and must not be simulated as real
- iOS Screen Time / Family Controls capabilities require Apple entitlements and platform-specific review


## Explicit product exclusions
- Do not add focus music/audio libraries.
- Do not add group study rooms or live social study sessions.
- Do not add leaderboards.
- Do not add the Rega motivational companion.

These exclusions are intentional scope decisions. Continue prioritizing local-first focus, reliable blocking, understandable insights, privacy, and Material 3 Expressive quality.

## Current prototype limitations
The timer supports custom focus durations, timer/stopwatch/Pomodoro modes, short/long breaks, active-session persistence, elapsed-time recalculation when the app returns to the foreground and completion notifications when permitted; background behavior must be tested on real devices. Focus sessions can store tags/notes and are included in the session history, daily focus goal and versioned backups. Weekly focus schedules create recurring local notifications but do not start sessions or block apps automatically. Screen-time reporting uses Android UsageStatsManager behind explicit user-granted Usage Access. The quick-actions widget and AccessibilityService blocking controls are implemented; general enforcement still depends on Android accessibility events, selected-app blocks should be tested on devices, and scheduled blocking, daily usage limits, live focus/exam widgets and strict mode remain outstanding. Shorts/Reels and channel matching depend on accessibility labels exposed by third-party apps and are not guaranteed to detect every screen. Do not market the prototype as production-ready until platform features are implemented and device-tested.
