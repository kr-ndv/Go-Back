# Go Back — implementation roadmap

## Stage 1: Foundation (in progress)
- Expo + React Native + TypeScript + Expo Router
- Premium forest/lime design system and navigation
- Local SQLite schema for tasks, notes, subjects, focus sessions, exams and settings
- First dashboard, task list, personal notes and basic focus timer
- Theme selection and EAS APK build profiles

## Stage 2: Core productivity
- Focus timer uses absolute end timestamps, app-resume recalculation, and completion notifications; validate behavior on real Android devices
- Task priorities and due dates; local goals, daily habits, and streaks are implemented in the current prototype
- Exam countdown, exam timetable, and student syllabus/chapter tracker with computed progress
- Personal notes editor with debounced autosave and search across titles and contents is implemented; pinning and standalone note export remain planned
- Reminder scheduling, haptics, font scaling, accent color and dashboard customization

## Stage 3: Android system features
- An Android home-screen quick-actions widget is implemented through an Expo config plugin; add a live focus-status and exam-countdown widget in a later pass
- Android UsageStatsManager bridge and explicit Usage Access onboarding are implemented in the native config plugin; real-device validation and usage-history accuracy checks remain
- Experimental Android AccessibilityService blocking controls are implemented for whole-app YouTube/Instagram blocking, heuristic Shorts/Reels tab detection, and selected YouTube channel-name matching; validate on devices and refine false positives
- Scheduled blocking, strict mode and a clear emergency exit remain planned
- Test on real Android devices and document battery/background behavior

## Stage 4: Quality and release
- Unit and component tests, accessibility review, error handling and migration strategy
- JSON backup/restore with schema and record validation is implemented; encryption remains outstanding
- EAS or local Gradle release build, APK install test, SHA-256 checksum and release notes
- CI checks and privacy/security review

## Intentionally deferred
- AI features, ads, analytics, trackers, cloud sync and backend services
- Live friend/accountability features and real multi-user rooms/leaderboards require a backend and must not be simulated as real
- iOS Screen Time / Family Controls capabilities require Apple entitlements and platform-specific review

## Current prototype limitations
The timer supports custom focus durations, short/long breaks, active-session persistence, elapsed-time recalculation when the app returns to the foreground and completion notifications when permitted; background behavior must be tested on real devices. Screen-time reporting uses Android UsageStatsManager behind explicit user-granted Usage Access. The quick-actions widget and experimental AccessibilityService blocking controls are implemented; live focus/exam widgets, scheduled blocking and strict mode remain outstanding. Shorts/Reels and channel matching depend on accessibility labels exposed by third-party apps and are not guaranteed to detect every screen. Do not market the prototype as production-ready until platform features are implemented and device-tested.
