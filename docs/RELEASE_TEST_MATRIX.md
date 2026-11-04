# Go Back — Android Release Test Matrix

Use this checklist for every release candidate. Record **date, app version, commit SHA, device model, Android version, launcher, test result, and issue link**. Do not mark an item passed without performing the test on the target device.

## Device coverage

Select at least three physical devices before a production release:
- A current Android version supported by the app.
- The oldest Android version the app intends to support.
- A low- or mid-range device with vendor-specific background/battery management.

Include different manufacturers/launchers where possible.

## Core app and persistence

- [ ] Fresh install opens without a crash and creates the local database.
- [ ] Create, edit, complete, and delete tasks; force-stop/relaunch and verify persistence.
- [ ] Create, edit, pin if supported, search, and delete notes; verify autosave before navigating away.
- [ ] Create subjects/chapters and update completion; verify progress before and after restart.
- [ ] Create and edit exams/timetable details; verify date display around midnight and time-zone changes.
- [ ] Create habits/goals and update progress; verify day boundaries and streaks.
- [ ] Switch each appearance mode and each accent palette; verify after app restart.
- [ ] Test long text, empty fields, fast repeated taps, back navigation, rotation/size changes where supported, and font scaling.
- [ ] Simulate storage or import failure where feasible; ensure errors are understandable and existing data remains intact.

## Focus timer and notifications

- [ ] Start, pause, resume, complete, and cancel a session.
- [ ] Background the app, lock the screen, open another app, and return.
- [ ] Verify elapsed/remaining time after process death and relaunch.
- [ ] Test across midnight and after changing the device time/time zone.
- [ ] Test notification permission accepted and denied.
- [ ] Verify completion notification appears once, at the right time, and does not fire after cancellation.
- [ ] Test battery saver/Doze and vendor battery restrictions; record limitations.
- [ ] Reboot during an active session and verify documented recovery behavior.

## Usage insights

- [ ] Open insights before granting Usage Access and verify clear onboarding.
- [ ] Grant access, return to Go Back, and verify data loads.
- [ ] Revoke access and verify the app recovers with a clear message.
- [ ] Test no data, newly installed apps, removed apps, and date-range boundaries.
- [ ] Confirm the app does not include its own usage in reported third-party totals unless intentionally designed.

## Accessibility-based blocking (experimental until validated)

- [ ] Explain permission purpose and local processing before opening system settings.
- [ ] Enable and disable the service and verify the state updates correctly.
- [ ] Test each blocking toggle independently.
- [ ] Test YouTube feed previews versus an active watch page; avoid blocking unrelated screens.
- [ ] Test Instagram feed versus Reels and navigation surfaces.
- [ ] Test configured channel matching against similar/overlapping names and missing labels.
- [ ] Test third-party app version changes and accessibility trees.
- [ ] Verify a clear escape route and that disabling a rule/service returns control immediately.
- [ ] Verify core Go Back features remain usable when permission is denied or the target apps are not installed.

## Home-screen widgets and links

- [ ] Add, resize, remove, and re-add the quick-action widget.
- [ ] Tap every action while the app is closed, in the background, and already open.
- [ ] Test individual action widgets and verify each route opens the intended screen.
- [ ] Test supported launchers and Android versions; record launcher-specific behavior.
- [ ] Verify no crash or dead-end route occurs from malformed/unknown deep links.

## Backup and restore

- [ ] Export backup and verify file name, schema/version, and data completeness.
- [ ] Restore into a clean app install and compare record counts and important fields.
- [ ] Round-trip non-ASCII text, emoji, long notes, and special characters.
- [ ] Try malformed JSON, unsupported schema versions, missing fields, wrong types, duplicate IDs, and oversized files.
- [ ] Verify an invalid backup is rejected without partially replacing existing data.
- [ ] Verify cancellation leaves current data untouched and errors show clear next steps.
- [ ] Confirm UI/docs clearly state that exported JSON is not encrypted unless encryption has been implemented and tested.

## Release artifact and upgrade

- [ ] Run CI from a clean checkout with lockfile-based dependency installation.
- [ ] Verify unit tests, TypeScript checks, Expo prebuild, and release Gradle build.
- [ ] Install the produced APK/AAB on a clean physical device and launch every main feature.
- [ ] Verify app label, icon, application ID `com.goback.focus`, version name, and monotonically increasing versionCode.
- [ ] Verify the artifact is signed with the intended release key and is not test-signed.
- [ ] Verify SHA-256 checksum and build metadata correspond to the exact published artifact.
- [ ] Upgrade over the previous public beta/release; check that data remains intact.
- [ ] Test the documented backup-and-restore path before uninstalling.
- [ ] Verify release notes, privacy policy, licence, permission disclosures, and source tag match the artifact.

## Result

Release candidate:  
Commit SHA:  
App version / versionCode:  
Test date:  
Devices / Android versions:  
Blockers:  
Decision: **GO / NO-GO**  
Approved by:
