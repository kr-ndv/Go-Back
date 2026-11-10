# GitHub release checklist

This repository is prepared for a **source-code release candidate**. No APK, AAB, build log or other generated artifact is committed, and no GitHub Actions workflow is configured.

## Before tagging

- [ ] Open \`android/\` in Android Studio using JDK 17 and Android SDK 35.
- [ ] Run \`./gradlew test\` from \`android/\` and resolve every failure.
- [ ] Run \`./gradlew assembleDebug\` locally and install the build on a physical device.
- [ ] Complete [the native release test matrix](RELEASE_TEST_MATRIX.md).
- [ ] Test the Room 0→1 migration against a copy of a real previous-version \`goback.db\`.
- [ ] Test JSON backup / restore, including schema 1–5 backups and malformed files.
- [ ] Confirm Accessibility, Usage Access, notifications and exact-alarm disclosures are accurate.
- [ ] Decide whether the historical Tasks / Habits / Goals references are expected in this release; those modules are not present in the latest active source tree.
- [x] Select and add the repository's intended software licence (MIT License added in repository root).
- [ ] Configure local release signing credentials if publishing a signed APK. Never commit keystores or passwords.

## Source-only GitHub Release

1. Confirm the candidate source commit is on \`main\`.
2. Tag only after the build and release test gates pass.
3. Create a GitHub Release with changelog notes and GitHub's source archive.
4. Do not attach APK/AAB files unless they are deliberately built and signed outside GitHub Actions.

## Automation policy

The \`.github/workflows/\` directory is intentionally empty. No push, pull-request, tag or manual workflow should compile Android, create APKs/AABs or upload artifacts unless the repository owner explicitly requests it in a later change.
