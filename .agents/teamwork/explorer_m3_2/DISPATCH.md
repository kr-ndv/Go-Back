# Dispatch: Explorer M3-2 (Schedules, Exam Timetable & Syllabus Tracker)

Date: 2026-11-07

## Mandatory Reading
- `c:\Users\visha\Desktop\Projects\Go-Back\.agents\teamwork\ORIGINAL_REQUEST.md` (MUST read first)
- `c:\Users\visha\Desktop\Projects\Go-Back\.agents\teamwork\orchestrator\PROJECT.md`
- `c:\Users\visha\Desktop\Projects\Go-Back\.agents\teamwork\spec_miner_survey_2\handoff.md`

## Mission
Investigate and design the complete standalone native Kotlin / Jetpack Compose implementation for Schedules, Exam Timetable, and Syllabus Tracking:
1. `android/app/src/main/java/com/goback/focus/ui/screens/SchedulesScreen.kt`:
   - Recurring blocking schedules: active days of week (bitmask or set), start time, end time, label, blocked packages selection, enabled/disabled toggle.
   - Connected to Room `ScheduleDao` (`ScheduleEntity`).
   - Active schedule evaluation engine (checks if current time falls within any active schedule interval, and updates `"scheduled_blocking_active"` in `"go_back_blocking"` SharedPreferences).
2. `android/app/src/main/java/com/goback/focus/ui/screens/ExamsScreen.kt`:
   - Exam timetable: title, subject name, exam date/time, room/location, notes.
   - Connected to Room `ExamDao` (`ExamEntity`).
   - Chronological sorting, days remaining countdown pill, exam reminder alarms via `AlarmManager`.
3. `android/app/src/main/java/com/goback/focus/ui/screens/SyllabusScreen.kt`:
   - Subjects and chapters tracker: subject title, color code, list of chapters with done/total toggles stored in `chapters_json`.
   - Connected to Room `SubjectDao` (`SubjectEntity`).
   - Chapter addition/deletion, completion percentage recalculation.

## Deliverables
1. Write architectural analysis, concrete data flows, and complete production-ready Compose Kotlin code to `c:\Users\visha\Desktop\Projects\Go-Back\.agents\teamwork\explorer_m3_2\handoff.md`.
2. Provide proposed code files if needed in `.agents/teamwork/explorer_m3_2/`.
3. Notify orchestrator via `send_message`.


## 2026-11-07T17:56:40Z
You are Explorer M3-2 (teamwork_explorer). Your working directory is c:\Users\visha\Desktop\Projects\Go-Back\.agents\teamwork\explorer_m3_2\.
Read your dispatch brief at c:\Users\visha\Desktop\Projects\Go-Back\.agents\teamwork\explorer_m3_2\DISPATCH.md.

MANDATORY FIRST READ:
Read c:\Users\visha\Desktop\Projects\Go-Back\.agents\teamwork\ORIGINAL_REQUEST.md.
Also read c:\Users\visha\Desktop\Projects\Go-Back\.agents\teamwork\orchestrator\PROJECT.md and c:\Users\visha\Desktop\Projects\Go-Back\.agents\teamwork\spec_miner_survey_2\handoff.md.

YOUR ASSIGNMENT:
Investigate and produce full, concrete architectural specifications and complete drop-in production-ready Compose Kotlin code for:
- SchedulesScreen.kt (recurring blocking schedules with days of week, start/end time, label, blocked packages, active interval evaluation engine updating "scheduled_blocking_active")
- ExamsScreen.kt (exam timetable, chronological countdown pills, reminders, Room ExamDao integration)
- SyllabusScreen.kt (subjects & chapters tracking, completion percentage, chapters_json CRUD, Room SubjectDao integration)

Write your complete hard handoff report with full code to c:\Users\visha\Desktop\Projects\Go-Back\.agents\teamwork\explorer_m3_2\handoff.md and notify orchestrator via send_message.
