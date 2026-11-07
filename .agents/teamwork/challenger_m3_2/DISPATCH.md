# Dispatch: Challenger M3-2 — Stress Testing Exam Reminders, Syllabus Serialization & Widget State

## Mandatory Files to Read First
- `c:\Users\visha\Desktop\Projects\Go-Back\.agents\teamwork\ORIGINAL_REQUEST.md` (MUST read first)
- `c:\Users\visha\Desktop\Projects\Go-Back\.agents\teamwork\orchestrator\PROJECT.md`
- `c:\Users\visha\Desktop\Projects\Go-Back\.agents\teamwork\worker_m3\handoff.md`

## Mission
Write unit tests / stress test harnesses in `android/app/src/test/java/com/goback/focus/` to empirically challenge and stress-test:
1. Exam 3-stage reminder alarm schedule calculations:
   - Exams 30 days away vs exams today vs past exams.
   - Stage 1 (7 days prior 9am), Stage 2 (1 day prior 9am), Stage 3 (exam morning 7am).
   - Leap year and month-boundary transitions.
2. `SyllabusJsonUtils` (or equivalent chapter parser):
   - Empty chapter lists, corrupted JSON, special characters, unicode, quotes, newlines in titles.
   - Fraction and percentage calculation when total = 0, 1, 100, 1000.
3. Glance Widget SharedPreferences contract:
   - Ensure all 8 canonical keys (`is_active`, `remaining_seconds`, `today_focus_minutes`, `daily_goal_minutes`, `streak_days`, `streak_tier_emoji`, `next_exam_name`, `next_exam_days`) are correctly read and gracefully fall back on null/missing values.

## Verification Requirements
- Execute: `cd android; .\gradlew.bat testDebugUnitTest` and ensure all tests pass.
- Document stress test cases, methodology, and empirical verdict: **CONFIRMED (PASS)** or **FAILED (REJECT)** in `c:\Users\visha\Desktop\Projects\Go-Back\.agents\teamwork\challenger_m3_2\handoff.md`.
- Report verdict back to orchestrator.


## 2026-11-07T18:34:12Z
You are Challenger M3-2 (teamwork_challenger). Your working directory is c:\Users\visha\Desktop\Projects\Go-Back\.agents\teamwork\challenger_m3_2\.
First, read your dispatch instructions at c:\Users\visha\Desktop\Projects\Go-Back\.agents\teamwork\challenger_m3_2\DISPATCH.md.

MANDATORY FIRST READ:
Read c:\Users\visha\Desktop\Projects\Go-Back\.agents\teamwork\ORIGINAL_REQUEST.md before starting your work.
Also read c:\Users\visha\Desktop\Projects\Go-Back\.agents\teamwork\orchestrator\PROJECT.md and c:\Users\visha\Desktop\Projects\Go-Back\.agents\teamwork\worker_m3\handoff.md.

YOUR ASSIGNMENT:
Write empirical stress tests in android/app/src/test/java/com/goback/focus/ to challenge:
1. Exam 3-stage reminder alarm date calculations:
   - Exams 30 days away, exams tomorrow, exams today, past exams.
   - Stage 1 (7 days prior 9am), Stage 2 (1 day prior 9am), Stage 3 (exam morning 7am).
   - Leap year and month-boundary transitions.
2. Syllabus chapter parsing & serialization:
   - Empty chapter lists, corrupted JSON, special characters, unicode, quotes, newlines in titles.
   - Progress fraction/percentage when total = 0, 1, 100, 1000.
3. Glance Widget SharedPreferences contract:
   - Verify all 8 canonical keys (is_active, remaining_seconds, today_focus_minutes, daily_goal_minutes, streak_days, streak_tier_emoji, next_exam_name, next_exam_days) handle default and fallback states properly.

VERIFICATION:
- Run: cd android; .\gradlew.bat testDebugUnitTest
- Ensure all 135+ existing tests AND your new stress tests pass 100%.
- Document your empirical test cases and verdict (CONFIRMED (PASS) or FAILED (REJECT)) in c:\Users\visha\Desktop\Projects\Go-Back\.agents\teamwork\challenger_m3_2\handoff.md.
- Notify the orchestrator with send_message when done.
