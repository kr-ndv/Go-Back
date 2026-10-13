# Go Back — implementation roadmap

## Stage 1: Foundation (in progress)
- Expo + React Native + TypeScript + Expo Router
- Premium forest/lime design system and navigation
- Local SQLite schema for tasks, notes, subjects, focus sessions, exams and settings
- First dashboard, task list, personal notes and basic focus timer
- Theme selection and EAS APK build profiles

## Stage 2: Core productivity
- Timer persistence based on absolute end timestamps; background notifications and session recovery
- Full task editing, due dates, priorities, habits, goals and streaks
- Exam countdown, exam timetable, and student syllabus/chapter tracker with computed progress
- Personal notes editor autosave, search, pinning and export
- Reminder scheduling, haptics, font scaling, accent color and dashboard customization

## Stage 3: Android system features
- Native home-screen widgets (focus status, quick actions, exam countdown)
- Usage access onboarding and usage-statistics integration where Android permits
- App blocking via an explicit, transparent Android implementation; assess AccessibilityService / UsageStatsManager limitations and Play policy before shipping
- Scheduled blocking and strict mode with a clear emergency exit
- Test on real Android devices and document battery/background behavior

## Stage 4: Quality and release
- Unit and component tests, accessibility review, error handling and migration strategy
- Backup/restore with integrity validation; encrypt backups if implemented
- EAS or local Gradle release build, APK install test, SHA-256 checksum and release notes
- CI checks and privacy/security review

## Intentionally deferred
- AI features, ads, analytics, trackers, cloud sync and backend services
- Live friend/accountability features and real multi-user rooms/leaderboards require a backend and must not be simulated as real
- iOS Screen Time / Family Controls capabilities require Apple entitlements and platform-specific review

## Current prototype limitations
The current timer is foreground-only; Android may suspend JavaScript timers while the app is backgrounded. Native widgets and app blocking are not implemented in this stage. Do not market the prototype as production-ready until these items are implemented and device-tested.
