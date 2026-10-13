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
- Personal notes editor is implemented; autosave, search, pinning and export remain planned
- Reminder scheduling, haptics, font scaling, accent color and dashboard customization

## Stage 3: Android system features
- An Android home-screen quick-actions widget is implemented through an Expo config plugin; add a live focus-status and exam-countdown widget in a later pass
- Usage access onboarding and usage-statistics integration where Android permits
- App blocking via an explicit, transparent Android implementation; assess AccessibilityService / UsageStatsManager limitations and Play policy before shipping
- Scheduled blocking and strict mode with a clear emergency exit
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
The timer recalculates elapsed time when the app returns to the foreground and schedules a completion notification when permitted, but background behavior must be tested on real devices. The quick-actions widget is implemented; live focus/exam widgets and app blocking are still outstanding. Do not market the prototype as production-ready until platform features are implemented and device-tested.
