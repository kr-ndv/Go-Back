# Native Android release test matrix

Use a physical device for permission-dependent and background behaviour. Mark each case only after it has been run on the release candidate.

| Area | Test | Expected result | Result |
|---|---|---|---|
| Install / upgrade | Install clean build on API 24+ | App launches, icon and label render | ☐ |
| Upgrade data | Install over a copy of a prior Go Back build with Expo SQLite `user_version=0` | Existing notes, subjects, sessions, schedules, exams, settings and limits remain after Room 0→1 migration | ☐ |
| Older beta data | Test a copy missing the later focus session and schedule columns | Migration supplies only the legacy defaults and retains every existing row | ☐ |
| Launch / navigation | Visit every bottom-tab and secondary route | No crash; back stack and bottom navigation behave consistently | ☐ |
| Theme | Change system/light/dark and every accent palette | Compose scheme updates immediately and persists after restart | ☐ |
| Focus timer | Start, pause, resume, finish early and complete naturally | Time is accurate; session saved once; UI recovers after process restart | ☐ |
| Pomodoro | Complete focus, short break and long break phases | Correct next phase and round count | ☐ |
| Stopwatch | Start, pause, resume, finish | Elapsed time is preserved and saved | ☐ |
| Strict mode | Attempt early exit with wrong and correct challenge phrase | Wrong phrase cannot end session; correct phrase can | ☐ |
| Focus presets | Save timer / Pomodoro / stopwatch preset; apply; delete | Mode and duration load; only selected session apps are blocked | ☐ |
| Session app blocks | Start a preset session, pause it, stop it and complete it | Session-only blocks are active only while the focus session is active and are cleared afterwards | ☐ |
| Timer notification | Run timer in background and open Quick Settings tile | Ongoing status remains accurate; tile actions work | ☐ |
| Finish alarms | Test default, bell, chime and digital sound | Each selection uses its expected sound; channel settings are documented | ☐ |
| Exact alarm | Test with exact-alarm access allowed and denied | Graceful handling of both states; no crash | ☐ |
| Blocking permission | Enable / disable Accessibility service | Blocking starts only when enabled and stops when revoked | ☐ |
| Selected-app blocking | Block a user-selected app | App is redirected while the rule is active | ☐ |
| Focus-only blocking | Enable and disable focus-only rules | Rules apply only during an active focus session | ☐ |
| Content blocking | Test Shorts, Reels, Spotlight and supported browser keyword checks | Supported target surfaces are handled; unsupported surfaces are not overclaimed | ☐ |
| Split screen | Test configured app in split-screen / floating window | Behaviour matches supported Android window reporting | ☐ |
| Usage access | Grant / revoke Usage Access | Usage summaries and limits report unavailable state when permission is absent | ☐ |
| Daily limits | Trigger warning and limit thresholds; cross midnight | Notifications, blocking state and daily reset behave as intended | ☐ |
| Syllabus | Add subject, chapters and progress | Entries persist and dashboard progress matches | ☐ |
| Notes | Create, edit, search and delete | Data persists with correct updated timestamp | ☐ |
| Exams | Create, edit and delete exams; test reminders | Dates, countdown and reminder scheduling are correct | ☐ |
| Schedules | Create recurring schedule, reminder and skip date | Only intended dates/times are scheduled; timezone change handled | ☐ |
| Analytics | Compare session history with saved records | Durations, type filters, range totals and streaks are correct | ☐ |
| Profile photo | Choose an image from the system document picker; restart app | URI permission remains available; photo appears in Settings and dashboard; display-name fallback works | ☐ |
| Backup export | Export JSON to local file provider | All eight tables included; PIN omitted | ☐ |
| Backup import | Restore a current and schema 1–5 backup | Data replaced atomically; local PIN retained; restore point created | ☐ |
| Backup integrity | Modify checksum, duplicate an ID, corrupt table type and cancel restore | Invalid input is rejected; current database remains unchanged | ☐ |
| Undo restore | Restore previous local restore point | Data returns to pre-restore snapshot | ☐ |
| Widget | Add, refresh and interact with home-screen widget | Widget reflects current focus state | ☐ |
| Update check | Enable/disable background release check | GitHub request occurs only when enabled and network is available | ☐ |
| Accessibility | TalkBack, font scaling, touch targets and dark mode | Key tasks remain readable and usable | ☐ |
| Release source | Inspect workflow directory and tracked generated files | No Actions workflow or committed build artifact remains | ☐ |

## Local checks

From \`android/\` on the release machine:

\`\`\`bash
./gradlew test
./gradlew assembleDebug
\`\`\`

These commands are intentionally not run by the repository's GitHub Actions; no Actions workflow is present. Complete the test cases above and add a signed release configuration locally before publishing a binary.
