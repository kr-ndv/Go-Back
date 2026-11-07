# Go-Back Native Android: E2E Test Infrastructure & Test Matrix

## Overview
This document defines the End-to-End (E2E) and logic testing infrastructure for the Go-Back native Android application. The testing strategy follows a rigorous 4-tier methodology to ensure complete feature parity with the reference implementation, numerical precision, fault tolerance, and stability against crashes across all productivity domains.

---

## 4-Tier Testing Methodology

```
┌─────────────────────────────────────────────────────────────┐
│  Tier 4: Real-World Application Scenarios                   │
│  - Student Daily Study Flow                                 │
│  - Strict Exam Preparation Session                          │
├─────────────────────────────────────────────────────────────┤
│  Tier 3: Cross-Feature Combinations                         │
│  - Timer & Session Presets x Accessibility Blocking         │
│  - Streak Shield x Daily Goal Celebration                   │
│  - Advanced Schedules x 3-Stage Exam Reminders              │
├─────────────────────────────────────────────────────────────┤
│  Tier 2: Boundary & Corner Cases (>=5 per feature)          │
│  - Negative/zero times, overflow, clock skew, DST           │
│  - Malformed JSON, corrupted data, casing/whitespace        │
│  - Boundary thresholds (3d, 7d, 14d, 30d, 50d, 100d streaks)│
├─────────────────────────────────────────────────────────────┤
│  Tier 1: Feature Coverage (>=5 per feature)                 │
│  - Focus Timer & Pomodoro Math & State Transitions          │
│  - Streak Tiers & Shield Calculations                       │
│  - Blocking & Whitelist Filtering                           │
│  - Schedules & 3-Stage Exam Reminders                       │
└─────────────────────────────────────────────────────────────┘
```

---

## Tier 1: Feature Coverage (>=5 per Feature)

### 1. Focus Timer & Pomodoro Math, Phase Transitions, and Recovery
1. **Timer Countdown Duration**: Standard durations (15m, 25m, 45m, 60m, 180m) compute exact remaining seconds and progress percentage $1.0 - (\text{remaining} / \text{total})$.
2. **Pomodoro Work to Short Break**: Finishing focus round 1, 2, or 3 transitions to `short-break` (5 minutes) and increments completed rounds counter.
3. **Pomodoro Round 4 Long Break**: Finishing focus round 4 ($(\text{rounds} + 1) \pmod 4 == 0$) transitions to `long-break` (15 minutes) and increments counter to 4.
4. **Pomodoro Post-Long-Break Reset**: Completing `long-break` resets phase to `focus` (25 minutes) and resets completed focus rounds to 0.
5. **Stopwatch Elapsed Time**: Accumulated seconds plus $(\text{now} - \text{runStartedAt}) / 1000$ computes correct elapsed seconds; pauses preserve exact accumulated time.
6. **Session Recovery (In-Flight)**: When app resumes and $\text{now} < \text{endAt}$, timer recalculates $\text{remaining} = \lceil(\text{endAt} - \text{now}) / 1000\rceil$ and maintains active state.
7. **Session Recovery (Expired)**: When app resumes and $\text{now} \ge \text{endAt}$, session is marked `expired = true`, `remaining = 0`, and total elapsed seconds equals duration.

### 2. Streak Tiers Calculation & 1-Day Streak Shield
1. **Tier Spark (3 Days)**: Consecutive daily goal achievement of 3 to 6 days maps to Tier `spark`, label "Spark", badge "✨", next milestone 7 days.
2. **Tier Momentum (7 Days)**: Consecutive achievement of 7 to 13 days maps to Tier `momentum`, label "Momentum", badge "🔥", next milestone 14 days.
3. **Tier Commitment (14 Days)**: Consecutive achievement of 14 to 29 days maps to Tier `commitment`, label "Commitment", badge "⚡", next milestone 30 days.
4. **Tier Habit Builder (30 Days)**: Consecutive achievement of 30 to 49 days maps to Tier `habit`, label "Habit Builder", badge "🛡️", next milestone 50 days.
5. **Tier Focus Mastery (50 Days)**: Consecutive achievement of 50 to 99 days maps to Tier `mastery`, label "Focus Mastery", badge "💎", next milestone 100 days.
6. **Tier Centurion (100 Days)**: Consecutive achievement of $\ge 100$ days maps to Tier `centurion`, label "Centurion", badge "👑".
7. **1-Day Shield Preservation**: When a user misses exactly 1 day between active goal days (e.g., Mon=met, Tue=missed, Wed=met), the shield activates (`streakShieldActive = true`), bridging the gap and preserving the streak.

### 3. Blocking Configuration Filtering, Allowlists, and Domain Matching
1. **Whole-App Blocking Filter**: Given a JSON array or StringSet of blocked packages (e.g. `["com.instagram.android", "com.snapchat.android"]`), incoming package matches return true; unblocked apps return false.
2. **YouTube Study Mode Channel Allowlist**: Channel name or handle comparison against `allowedChannels` array (case-insensitive substring match); non-allowed videos trigger dismissal.
3. **Adult Domain Keyword Matcher**: Text containing known adult domains (e.g. `pornhub.com`, `xvideos.com`, `onlyfans.com`, `rule34`) flags positive for blocking across browser window titles and URLs.
4. **Social Feed Content Flags**: Accessibility detection for `blockShorts`, `blockReels`, and `blockSpotlight` flags selected/checked labels and navigation surfaces.
5. **Split-Screen Window Evaluation**: When `TYPE_APPLICATION` windows $> 1$ and any background/split window matches a blocked package, blocking logic triggers.

### 4. Schedules Recurring Times & 3-Stage Exam Reminders
1. **Weekday Bitmask / Array Parsing**: Converts JSON string `"[1, 2, 3, 4, 5]"` to sorted unique days, formatting as "Weekdays"; `"[0, 6]"` formats as "Weekends"; all 7 days formats as "Every day".
2. **Upcoming Schedule Horizon**: Generates rolling schedule occurrences over a 35-day horizon matching scheduled time of day and selected active days.
3. **Skip Dates Exclusion**: Explicit skip dates in `skip_dates_json` (e.g. holidays) suppress occurrence generation for those dates.
4. **3-Stage Exam Notification: 7 Days Prior**: Scheduled for exactly 7 days before exam date at 09:00:00 local time.
5. **3-Stage Exam Notification: 1 Day Prior**: Scheduled for exactly 1 day before exam date at 09:00:00 local time.
6. **3-Stage Exam Notification: Morning of Exam**: Scheduled for exam date at 07:00:00 local time.

---

## Tier 2: Boundary & Corner Cases (>=5 per Feature)

### 1. Focus Timer & Recovery Boundary Cases
1. **Minimum Duration (1 minute)**: 60 seconds counts down cleanly to 0 with progress $0 \to 1.0$.
2. **Maximum Duration (180 minutes)**: 10,800 seconds handles large integer calculations without integer overflow.
3. **Zero / Negative Remaining Clamping**: Negative or fractional timestamps clamp cleanly to 0 without underflow exceptions.
4. **Sub-Second Precision & Ceil vs Floor**: Recovery uses $\lceil(\text{endAt} - \text{now})/1000\rceil$ to prevent premature zero-rounding when 500ms remain.
5. **Clock Skew / Time Traveling Backward**: If device clock shifts backward after start, remaining seconds is capped at total duration without exceeding 100%.

### 2. Streak & Shield Boundary Cases
1. **Zero / Sub-Threshold Days**: Days with focus seconds $> 0$ but $< \text{goalSeconds}$ do not qualify as goal days.
2. **Consecutive Missed Days Breaks Shield**: If 2 or more consecutive days are missed, the shield cannot bridge the gap; streak resets to 0 (or today's count).
3. **Single Missed Day at Beginning of Streak**: Missing the day before a 1-day streak does not trigger shield; streak is simply 1 day.
4. **Multiple Shield Attempts in History**: Shield only covers a single 1-day gap in the current active running chain; second gap terminates counting.
5. **Month / Year Boundary Transitions**: Calculating streak across December 31 $\to$ January 1 or leap year February 28/29 $\to$ March 1 calculates contiguous dates correctly.

### 3. Blocking & Whitelist Boundary Cases
1. **Empty Blocklist / Empty Allowlist**: Empty package list blocks nothing; empty YouTube allowlist in Study Mode blocks all non-empty video surfaces.
2. **Package Name Whitespace & Case Insensitivity**: `com.Instagram.Android ` trims and matches `com.instagram.android`.
3. **Subdomain and Substring Matching**: Adult keyword `rule34` matches `https://rule34.xxx/post` but does not match false positives like `rule3400.com` incorrectly.
4. **Maximum 100 Package Cap**: Enforces hard limit of 100 packages, self-excluding `com.goback.focus`.
5. **Rapid Event Cooldown**: Fast repeated events within 300ms cooldown window are throttled to prevent global action storming.

### 4. Schedules & Exam Reminders Boundary Cases
1. **Past Exam Dates**: Exams with dates in the past are ignored for reminder scheduling and flagged as completed.
2. **Same-Day Exam Registration**: Exam registered for today skips the 7-day and 1-day reminders; only fires 7:00 AM reminder if current time is before 7:00 AM.
3. **Invalid Time String Formatting**: Malformed time inputs (e.g. `24:00`, `12:60`, `invalid`) are rejected by validator regex `^([01]\d|2[0-3]):[0-5]\d$`.
4. **Reminder Minutes Offset Larger Than Occurrence Gap**: 120-minute pre-reminder clamps so reminder time does not fall before current time.
5. **Empty Days Array in Schedule**: Schedule with `days_json = "[]"` produces 0 upcoming occurrences without throwing exceptions.

---

## Tier 3: Cross-Feature Combinations

1. **Timer Recovery x Session Presets x App Blocking**:
   - Starting a 45m session with preset "Math Deep Work" temporarily attaches `["com.twitter.android", "com.reddit.frontpage"]` to `session_blocked_packages`.
   - Process is killed and restored.
   - On recovery, `session_blocked_packages` is verified intact, remaining time is recalculated, and blocking continues until timer expires.
2. **Streak Shield Activation x Daily Goal Celebration**:
   - User had 5-day streak, missed yesterday (shield eligible), and today completes a 60m session crossing their 120m daily goal.
   - Goal celebration fires (`priorSecondsToday < goal && newSecondsToday >= goal`), today is recorded as met, shield bridges yesterday, and streak advances from 5 to 6 days.
3. **Advanced Schedules x Exam Timetable Conflict**:
   - Scheduled focus block occurs on the morning of an exam.
   - Both reminders fire at their respective trigger times (7:00 AM exam reminder, 8:00 AM scheduled study block reminder) on distinct notification channels (`go-back-exams-v1` vs `go-back-schedule-v3`).
4. **Strict Mode Challenge x Pomodoro Phase Transitions**:
   - Strict mode is enabled during Pomodoro focus round 4.
   - User cannot exit without typing "I choose to break my focus".
   - When timer expires naturally, transition to 15m Long Break occurs smoothly without requiring the challenge phrase.

---

## Tier 4: Real-World Application Scenarios

### Scenario 1: Daily Student Study Workflow
- **Context**: A student preparing for semester finals.
- **Workflow**:
  1. Checks dashboard in morning: Next exam "Calculus II" in 5 days; streak shows 4 days (Spark ✨).
  2. Starts a 4-round Pomodoro study session:
     - Round 1 (25m Focus) $\to$ Short break (5m).
     - Round 2 (25m Focus) $\to$ Short break (5m).
     - Round 3 (25m Focus) $\to$ Short break (5m).
     - Round 4 (25m Focus) $\to$ Long break (15m).
  3. During Round 2, student impulsively tries to open YouTube Shorts; Accessibility service detects Shorts feed and sends `GLOBAL_ACTION_BACK`.
  4. Student opens YouTube to watch an allowed educational lecture from "3Blue1Brown"; channel allowlist recognizes handle and permits video playback.
  5. By the end of Round 4 (100 total minutes of focus), student completes their remaining 20 minutes with a 25m countdown timer.
  6. At 120 minutes, daily focus goal celebration triggers; streak increments from 4 to 5 days; progress towards Momentum (7d) badge increases.

### Scenario 2: Strict Exam Preparation Session
- **Context**: Night before the exam.
- **Workflow**:
  1. 1-day exam reminder fired at 9:00 AM: "⚠️ Exam tomorrow: Calculus II".
  2. Student schedules evening study block 19:00 to 21:00 with Strict Mode active.
  3. Pre-reminder fires 15 minutes before at 18:45.
  4. At 19:00, session begins. Student attempts to abandon session after 15 minutes; Strict Mode challenge intercepts and requires exact phrase typing.
  5. Student persists, finishes 120 minutes, and earns full day completion.
  6. Morning reminder fires at 7:00 AM on exam day: "🎯 Exam day: Calculus II".

---

## Test Execution & Verification Matrix
- **Test Command**: `./gradlew testDebugUnitTest`
- **Output Artifacts**: JUnit XML reports in `android/app/build/test-results/testDebugUnitTest/`
- **Execution Target**: Headless JVM 17 runner, zero emulator required, fast execution time (< 15 seconds).
