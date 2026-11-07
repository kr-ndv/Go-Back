# Dispatch: Challenger M3-1 — Stress Testing Schedules, Security PIN & Usage Limits

## Mandatory Files to Read First
- `c:\Users\visha\Desktop\Projects\Go-Back\.agents\teamwork\ORIGINAL_REQUEST.md` (MUST read first)
- `c:\Users\visha\Desktop\Projects\Go-Back\.agents\teamwork\orchestrator\PROJECT.md`
- `c:\Users\visha\Desktop\Projects\Go-Back\.agents\teamwork\worker_m3\handoff.md`

## Mission
Write unit tests / stress test harnesses in `android/app/src/test/java/com/goback/focus/` to empirically challenge and stress-test:
1. `ScheduleEvaluationEngine`:
   - Normal intraday schedules (e.g. 09:00 to 11:00).
   - Overnight / cross-midnight schedules (e.g. 22:00 to 02:00 next day).
   - Exact boundary times (start second, end second, 1ms outside).
   - Active day matching (e.g. Monday-only schedule evaluated on Tuesday).
2. `SecurityPinUtil`:
   - Empty PIN, leading zeros (e.g. "0000", "0123"), 4-8 digit pins.
   - Salt consistency and collision resistance against wrong PINs.
3. Usage limit quota calculation & warning percent logic (50% to 100%).

## Verification Requirements
- Execute: `cd android; .\gradlew.bat testDebugUnitTest` and ensure all tests pass (existing + new stress tests).
- Document stress test cases, methodology, and empirical verdict: **CONFIRMED (PASS)** or **FAILED (REJECT)** in `c:\Users\visha\Desktop\Projects\Go-Back\.agents\teamwork\challenger_m3_1\handoff.md`.
- Report verdict back to orchestrator.


## 2026-11-07T18:33:55Z
You are Challenger M3-1 (teamwork_challenger). Your working directory is c:\Users\visha\Desktop\Projects\Go-Back\.agents\teamwork\challenger_m3_1\.
First, read your dispatch instructions at c:\Users\visha\Desktop\Projects\Go-Back\.agents\teamwork\challenger_m3_1\DISPATCH.md.

MANDATORY FIRST READ:
Read c:\Users\visha\Desktop\Projects\Go-Back\.agents\teamwork\ORIGINAL_REQUEST.md before starting your work.
Also read c:\Users\visha\Desktop\Projects\Go-Back\.agents\teamwork\orchestrator\PROJECT.md and c:\Users\visha\Desktop\Projects\Go-Back\.agents\teamwork\worker_m3\handoff.md.

YOUR ASSIGNMENT:
Write empirical stress tests in android/app/src/test/java/com/goback/focus/ to challenge:
1. ScheduleEvaluationEngine:
   - Daytime intervals vs overnight / cross-midnight intervals (e.g. 23:00 to 03:00).
   - Exact minute boundaries (start minute, end minute, 1 minute before/after).
   - Day-of-week bitmasks / active day lists.
2. SecurityPinUtil:
   - Salted Murmur32 hashing consistency, empty pin handling, leading zeroes ("0000", "0123").
   - Collision resistance against incorrect guesses.
3. UsageLimit boundary conditions:
   - 0 minute limit, limits > daily minutes, 50% vs 99% vs 100% threshold calculations.

VERIFICATION:
- Run: cd android; .\gradlew.bat testDebugUnitTest
- Ensure all 135+ existing tests AND your new stress tests pass 100%.
- Document your empirical test cases and verdict (CONFIRMED (PASS) or FAILED (REJECT)) in c:\Users\visha\Desktop\Projects\Go-Back\.agents\teamwork\challenger_m3_1\handoff.md.
- Notify the orchestrator with send_message when done.
