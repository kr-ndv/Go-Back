# Comprehensive Investigation & Architecture Analysis: TodayScreen & AnalyticsScreen

**Agent**: Explorer M2-2  
**Milestone**: M2 (Core Focus Engine & Productivity Screens)  
**Date**: 2026-11-07  

---

## 1. Executive Summary

This investigation provides the complete architectural design, mathematical algorithms, Room Database reactive bindings, native `UsageStatsManager` / `UsageEvents` querying, and Jetpack Compose Material 3 implementations for:
1. **`TodayScreen.kt`**: Live Room DB dashboard binding (`FocusSessionDao`, `ExamDao`, `SubjectDao`, `SettingDao`), Material 3 daily focus goal progress ring (`M3CircularProgress`), 6-tier streak gamification engine (Spark to Centurion) with 1-day streak shield indicator, syllabus completion tracker, upcoming exams countdown card, and quick tool launcher.
2. **`AnalyticsScreen.kt`**: Dual-bar Focus vs Screen Time visual comparison chart (1d, 7d, 30d views), % change calculations vs previous period, 3-way app classification (Productive, Distracting, Neutral) with segmented split bar and instant persistence, real device usage queries via `UsageStatsManager` and `UsageEvents` (unlocked screen count), and filterable session history log (All, Focus, Break) with date range aggregations.

All code has been designed for 100% feature parity with the reference Go-Back application (`src/lib/focusInsights.ts`, `src/lib/insights.ts`, `app/(tabs)/index.tsx`, `app/(tabs)/analytics.tsx`, and `app/sessions.tsx`), zero mock data, and full offline-first resilience.

---

## 2. Room Database Architecture & Reactive Call Chains

### 2.1 DAOs Utilized
| DAO | Method | Purpose in Screens |
|-----|--------|-------------------|
| `FocusSessionDao` | `getAll(): Flow<List<FocusSessionEntity>>` | Reactive stream of all focus and break sessions used for streak calculation, daily focus total, dual-bar chart, and session history log. |
| `FocusSessionDao` | `getTodaySessions(): Flow<List<FocusSessionEntity>>` | Fast query for today's sessions. |
| `ExamDao` | `getNextUpcoming(): Flow<ExamEntity?>` | Emits next chronological exam with `exam_date >= date('now', 'localtime')`. |
| `SubjectDao` | `getAll(): Flow<List<SubjectEntity>>` | Emits subjects to parse `chapters_json` for total vs completed chapters. |
| `NoteDao` | `getAll(): Flow<List<NoteEntity>>` | Total count of saved notes for tools card. |
| `SettingDao` | `observeValue(key: String): Flow<String?>` | Observes `"focus.dailyGoalMinutes"` (15–480m) and `"profile.name"`. |
| `SettingDao` | `setValue(setting: SettingEntity)` | Persists app classification JSON to `"usage.categories"`. |

### 2.2 Data Pipeline & Concurrency
- All Room queries are collected on `Dispatchers.IO` via Kotlin coroutines `combine(...)` and emitted as a single immutable `StateFlow<TodayUiState>` and `StateFlow<AnalyticsUiState>`.
- UI layers consume state via `viewModel.uiState.collectAsState()` with Compose lifecycle safety.
- Screen time and unlock queries use `UsageStatsManager` and `UsageEvents` in non-blocking background threads.

---

## 3. Mathematical Specifications & Core Algorithms

### 3.1 Streak Gamification & 1-Day Streak Shield Algorithm
The streak engine implements 6 progressive tiers with supportive non-punitive messaging:

| Tier Key | Badge | Label | Milestone Day | Next Target |
|:---------|:-----:|:------|:-------------:|:-----------:|
| `none` | 🌱 | Getting Started | < 3 days | 3 days |
| `spark` | ✨ | Spark | 3 days | 7 days |
| `momentum` | 🔥 | Momentum | 7 days | 14 days |
| `commitment` | ⚡ | Commitment | 14 days | 30 days |
| `habit` | 🛡️ | Habit Builder | 30 days | 50 days |
| `mastery` | 💎 | Focus Mastery | 50 days | 100 days |
| `centurion` | 👑 | Centurion | 100 days | 150 days |

#### 1-Day Streak Shield Mechanics:
```kotlin
var currentStreak = 0
var cursorKey = todayKey
var streakShieldActive = false

if (!todayGoalMet) {
    cursorKey = previousDateKey(cursorKey, 1) // Start from yesterday if today is in-progress
}

for (guard in 0 until 3650) {
    val daySeconds = dailyTotals[cursorKey] ?: 0
    if (daySeconds >= goalSeconds) {
        currentStreak++
        cursorKey = previousDateKey(cursorKey, 1)
    } else if (!streakShieldActive && guard > 0) {
        // 1-Day Streak Shield: Grace period skips 1 missed day if the day before met the goal
        val dayBefore = previousDateKey(cursorKey, 1)
        if ((dailyTotals[dayBefore] ?: 0) >= goalSeconds) {
            streakShieldActive = true
            cursorKey = dayBefore
        } else {
            break // 2 consecutive missed days breaks the streak
        }
    } else {
        break
    }
}
```

### 3.2 Focus vs Screen Time Dual-Bar Chart
- **1-Day View**: Today's Focus vs Today's Screen Time.
- **7-Day View**: Daily dual vertical bars for the last 7 days (M, T, W, T, F, S, S).
- **30-Day View**: Daily dual vertical bars for the last 14 days with numeric date labels.
- Dual bar rendering:
  - Primary Green bar for Focus (`MaterialTheme.colorScheme.primary`).
  - Accent Blue bar for Screen Time (`#60A5FA`).
  - Bar heights dynamically scaled:
    $$\text{heightFraction} = \text{clamp}\left(\frac{\text{minutes}}{\max(60, \text{maxMinutesInChart})}, 0.04, 1.0\right)$$

### 3.3 Period % Change Calculation
$$\Delta\% = \begin{cases} 
\text{null (New)}, & \text{if } \text{previous} \le 0 \text{ and } \text{current} > 0 \\
0\%, & \text{if } \text{previous} \le 0 \text{ and } \text{current} \le 0 \\
\operatorname{round}\left(\frac{\text{current} - \text{previous}}{\text{previous}} \times 100\right), & \text{if } \text{previous} > 0
\end{cases}$$

### 3.4 App Categorization & Segmented Split Bar
- Installed apps are categorized into 3 states:
  - **Productive** (`#34D399`): Educational and study tools.
  - **Distracting** (`#F87171`): Social media and entertainment.
  - **Neutral** (`#9CA3AF`): System tools, maps, utilities.
- Stored as a JSON dictionary in Room `settings` under key `"usage.categories"`.
- Segmented split bar displays exact proportional widths:
  $$\text{productive\%} = \operatorname{round}\left(\frac{\text{productiveMs}}{\text{totalCategorizedMs}} \times 100\right)$$

### 3.5 UsageStatsManager & UsageEvents Query
- **Usage Access Permission Check**:
  ```kotlin
  val ops = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
  val granted = ops.checkOpNoThrow(
      AppOpsManager.OPSTR_GET_USAGE_STATS,
      Process.myUid(),
      context.packageName
  ) == AppOpsManager.MODE_ALLOWED
  ```
- **Phone Unlock Count**:
  Iterates `UsageEvents` between `startOfDay` and `now` matching `event.eventType == 18` (`UsageEvents.Event.KEYGUARD_HIDDEN`).

---

## 4. Proposed Artifacts in `explorer_m2_2/`

1. `proposed_TodayScreen.kt`:
   - Material 3 Compose screen.
   - Circular progress ring (`M3CircularProgress`) + squiggly wave progress (`M3SquigglyProgress`).
   - Dynamic streak tier pill and 1-day shield status.
   - Syllabus completion and exam countdown cards.
   - Quick launch tools list.
2. `proposed_TodayViewModel.kt`:
   - Room Flow combiners (`FocusSessionDao`, `ExamDao`, `SubjectDao`, `NoteDao`, `SettingDao`).
   - Streak calculation with 1-day shield.
   - Automatic SharedPreferences sync for `GoBackFocusWidget`.
3. `proposed_AnalyticsScreen.kt`:
   - Dual-bar Focus vs Screen Time chart.
   - 3-period selector (Today, 7d, 30d) and KPI metric cards.
   - Segmented app categorization bar and interactive classification controls.
   - Filterable session history log (All, Focus, Breaks).
4. `proposed_AnalyticsViewModel.kt`:
   - Multi-day `UsageStatsManager` queries.
   - `UsageEvents` unlock counter.
   - App category persistence.
   - History log filtering and date-window summarization.
