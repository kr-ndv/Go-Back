# Roadmap

## Native Android foundation

- Standalone Kotlin / Jetpack Compose application using Material 3
- Room-backed notes, subjects, focus sessions, schedules, presets, usage limits, exams and settings
- Focus timer, Pomodoro, stopwatch, strict-mode challenge and session recovery
- Accessibility-based blocking and session-scoped app package rules
- Syllabus, exam planner, focus scheduling, screen-time limits and analytics
- Home-screen widget and Quick Settings tile
- Saved focus presets and local JSON backup / restore
- Bundled finish-sound resources, launcher artwork and persisted theme preferences
- Local profile display name and photo

## Release verification still required

1. Run Gradle unit tests and local debug compilation using JDK 17 / SDK 35.
2. Validate Room migration and data preservation against real previous-version databases.
3. Complete physical-device coverage for Android permissions, Accessibility, exact alarms, widgets, usage tracking and notifications.
4. Complete backup / restore interoperability tests for legacy schema versions 1–5.
5. Decide whether separately documented legacy Tasks / Habits / Goals modules must be restored before release; no active source routes or DB entities for them are present in the current tree.
6. Select a licence and configure release signing locally.
7. Publish a source-only GitHub Release after the gates pass; binary artifacts remain a separate, explicit decision.

No CI workflow is included. See [production readiness](PRODUCTION_READINESS.md), [feature parity audit](FEATURE_PARITY_AUDIT.md) and [release checklist](RELEASE_CHECKLIST.md).
