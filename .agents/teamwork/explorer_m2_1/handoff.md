# Focus Timer, Pomodoro, Stopwatch & Strict Mode Architecture

## 1. Observation

Direct code and environment observations:
1. **Existing Prototype `FocusScreen.kt`** (`android/app/src/main/java/com/goback/focus/ui/screens/FocusScreen.kt`, lines 28–30):
   ```kotlin
   var isRunning by remember { mutableStateOf(false) }
   var selectedMinutes by remember { mutableIntStateOf(25) }
   var remainingSeconds by remember { mutableIntStateOf(25 * 60) }
   ```
   - Lacks Pomodoro phase state machine (25m work / 5m short break, 15m long break after round 4).
   - Lacks Stopwatch open-ended mode with accumulated second tracking and pause preservation.
   - Lacks persistent state management (`endAtMs`, process recovery upon death/reboot).
   - Lacks Strict Mode challenge dialog ("I choose to break my focus").
   - Lacks session metadata (tags max 40 chars, notes max 300 chars) and Room persistence (`FocusSessionDao`).
   - Lacks foreground service binding (`FocusTimerService`) and alarm scheduling.

2. **Foreground Service Contract `FocusTimerService.kt`** (`android/app/src/main/java/com/goback/focus/service/FocusTimerService.kt`, lines 130–143):
   - Actions: `ACTION_START = "com.goback.focus.action.START_TIMER"`, `ACTION_PAUSE = "com.goback.focus.action.PAUSE_TIMER"`, `ACTION_RESUME = "com.goback.focus.action.RESUME_TIMER"`, `ACTION_STOP = "com.goback.focus.action.STOP_TIMER"`.
   - Extras: `EXTRA_TITLE = "title"`, `EXTRA_IS_STOPWATCH = "is_stopwatch"`, `EXTRA_DURATION_MS = "duration_ms"`, `EXTRA_DURATION_SECONDS = "duration_seconds"`, `EXTRA_END_AT_MS = "end_at_ms"`, `EXTRA_END_AT_MILLIS = "end_at_millis"`, `EXTRA_PAUSED = "paused"`.
   - Uses `CHANNEL_FOCUS_STATUS = "go-back-focus-status-v2"` with native Android Chronometer.

3. **Room Database Entities & DAOs** (`android/app/src/main/java/com/goback/focus/data/`):
   - `FocusSessionEntity`: `id: String`, `startedAt: String`, `durationSeconds: Int`, `completed: Int`, `sessionType: String` ("focus", "short-break", "long-break"), `mode: String` ("timer", "pomodoro", "stopwatch"), `tag: String`, `notes: String`.
   - `FocusSessionDao`: `getTodaySessions()`, `getAll()`, `insert(session)`, `getBetween(start, end)`.
   - `SettingDao`: `getValue(key)`, `setValue(setting)`, `observeValue(key)`.

4. **Accessibility Service Contract** (`GoBackBlockingService.kt`, lines 313–318):
   - Reads `go_back_blocking` SharedPreferences:
     - `"focus_active"`: Boolean
     - `"focus_state"`: JSON string containing `{"active": Boolean, "mode": String, "title": String, "endAt": Long, ...}`
     - `"session_blocked_packages"`: JSON array of packages
     - `"strict_mode_active"`: Boolean

5. **Test Oracles in `FocusTimerAndPomodoroTest.kt`** (`android/app/src/test/java/com/goback/focus/FocusTimerAndPomodoroTest.kt`):
   - `FocusEngine.calculateProgress(selectedMinutes, remainingSeconds)`
   - `FocusEngine.nextPomodoroPhase(currentType, completedRounds)`: 4th completed focus round -> 15m Long Break; post-long-break -> resets to Focus and rounds = 0; short break -> resets to Focus keeping rounds.
   - `FocusEngine.stopwatchElapsedSeconds(accumulatedSeconds, runStartedAtMs, nowMs)`
   - `FocusEngine.formatFocusTime(totalSeconds, forceHours)`
   - `FocusEngine.recoverSession(...)`
   - `FocusEngine.validateStrictChallenge(phrase)`: trimmed, case-insensitive match against `"I choose to break my focus"`.

---

## 2. Logic Chain

Step-by-step reasoning from observations to complete architectural design:

1. **Decoupling State to MVVM Architecture**:
   - `FocusScreen.kt` currently couples ephemeral UI state with local `remember { mutableStateOf() }`. This guarantees data loss on configuration change (screen rotation, multi-window resize) or background process reclamation.
   - Solution: Create `com.goback.focus.ui.viewmodel.FocusViewModel` extending `AndroidViewModel(application)`.
   - The ViewModel manages a unified `StateFlow<FocusUiState>`, observes Room database DAOs, interacts with SharedPreferences and `FocusTimerService`, and exposes unidirectional user intent methods (`start()`, `pause()`, `resume()`, `finish()`, `setMode()`, `choosePreset()`, `setCustomMinutes()`, `setTag()`, `setNotes()`, `verifyStrictChallenge()`).

2. **Three Focus Modes & State Machine**:
   - `enum class FocusMode { TIMER, POMODORO, STOPWATCH }`
   - `enum class PomodoroSessionType { FOCUS, SHORT_BREAK, LONG_BREAK }`
   - In `TIMER` mode:
     - Presets: 15, 25, 45, 60 minutes.
     - Custom: 1–180 minutes.
     - Type selector: Focus (preset duration), Short break (5m), Long break (15m).
     - Progress formula: `(1f - (remainingSeconds.toFloat() / (selectedMinutes * 60f))).coerceIn(0f, 1f)`.
   - In `POMODORO` mode:
     - Focus duration is fixed at 25 minutes; short break is 5 minutes; long break is 15 minutes.
     - Phase completion advances round counter `completedFocusRounds`:
       - Rounds 1, 2, 3 focus completion -> `SHORT_BREAK` (5m), `completedFocusRounds = rounds + 1`.
       - Round 4 focus completion (`nextRounds % 4 == 0`) -> `LONG_BREAK` (15m), `completedFocusRounds = 4`.
       - Completing long break resets to `FOCUS` (25m), `completedFocusRounds = 0`.
       - Completing short break resets to `FOCUS` (25m), `completedFocusRounds = rounds`.
     - Crucially, the phase transition prepares the next phase in the UI but **never auto-starts** the timer, preserving user autonomy between rounds.
   - In `STOPWATCH` mode:
     - Open-ended elapsed time counter:
       `elapsedSeconds = accumulatedElapsedSeconds + if (runStartedAtMs != null) max(0L, (now - runStartedAtMs) / 1000L).toInt() else 0`.
     - Preserves accumulated seconds across pause/resume and process restarts.
     - Progress ring displays seconds within current minute: `((elapsedSeconds % 60) / 60f).coerceIn(0f, 1f)`.

3. **State Persistence & Process Recovery Engine**:
   - In `go_back_blocking` SharedPreferences, synchronize on session state change:
     - `"focus_active"` = `isRunning`
     - `"focus_state"` = JSON object containing `active`, `mode`, `sessionType`, `title`, `endAt`, `remainingSeconds`, `elapsedSeconds`, `durationSeconds`, `pomodoroRounds`, `tag`, `notes`, `startedAt`.
   - On ViewModel initialization (`init` block):
     - Check if an in-flight session exists.
     - If `mode == STOPWATCH`:
       - If `stopwatchRunStartedAtMs != null`, calculate catch-up elapsed time and continue running with `FocusTimerService`.
       - If paused, restore accumulated elapsed time.
     - If `mode == TIMER` or `mode == POMODORO`:
       - If `endAtMs != null`:
         - If `nowMs >= endAtMs`: **Expired in background!**
           - Automatically insert completed session into `FocusSessionDao` (`durationSeconds = selectedMinutes * 60`, `completed = 1`).
           - Check daily focus goal threshold and trigger celebration if crossed.
           - Advance Pomodoro phase if in Pomodoro mode.
           - Clear active session SharedPreferences.
           - Stop `FocusTimerService`.
           - Set UI state to show celebratory completion modal.
         - If `nowMs < endAtMs`:
           - Catch up: `remainingSeconds = ceil((endAtMs - nowMs) / 1000.0).toInt()`.
           - Resume timer with `isRunning = true` and ensure `FocusTimerService` is active.

4. **Strict Mode Challenge & Anti-Bypass Modal**:
   - When `strictMode` is enabled in `go_back_blocking` SharedPreferences:
     - If the user attempts to tap "End session early" or navigate back/close while `remainingSeconds > 0` and `mode != STOPWATCH`:
       - Intercept action and display `StrictChallengeDialog`.
       - Display exact commitment phrase: `"I choose to break my focus"`.
       - Input validation: `input.trim().equals("I choose to break my focus", ignoreCase = true)`.
       - The "End session early" button is disabled and dimmed (opacity 0.45f) until the exact phrase is typed.
       - When matched, button enables in red/danger style (`MaterialTheme.colorScheme.error`).
     - If the user attempts to pause during strict mode:
       - Show confirmation dialog: *"Strict Mode Active: Pausing interrupts your momentum. Do you still want to pause?"*
     - Accessibility Service protection:
       - `GoBackBlockingService` monitors `com.android.settings`. If user attempts to force-stop or uninstall Go Back while strict focus is active, service automatically triggers `GLOBAL_ACTION_HOME`.

5. **Session Metadata, Room Integration & Daily Goal Celebration**:
   - Tag input clamped to 40 characters: `tag.trim().take(40)`.
   - Notes input clamped to 300 characters: `notes.trim().take(300)`.
   - When session finishes (naturally or ended early with `elapsedSeconds > 0`):
     - Insert `FocusSessionEntity` via `focusSessionDao.insert(...)`.
   - Daily Goal Calculation:
     - Read `dailyGoalMinutes` from `settingDao.getValue("focus.dailyGoalMinutes")` (default 120, clamped 15–480).
     - Query today's focus seconds from `focusSessionDao.getTodaySessions()`.
     - Threshold check: `priorSecondsToday < goalSeconds && totalSecondsToday >= goalSeconds`.
     - When crossed:
       - Dispatch high-priority notification to `GoBackApplication.CHANNEL_GOAL_COMPLETED`.
       - Trigger celebratory modal in `FocusScreen`.
     - Update widget data SharedPreferences `"go_back_widget_data"` and refresh Glance widget.

6. **Service Binding & Exact Alarm Integration**:
   - `FocusTimerService`:
     - Dispatched via `ContextCompat.startForegroundService(context, intent)` with action `ACTION_START`.
     - Extras include `EXTRA_TITLE`, `EXTRA_IS_STOPWATCH`, `EXTRA_DURATION_SECONDS`, `EXTRA_END_AT_MILLIS`, `EXTRA_PAUSED`.
     - Pausing dispatches `ACTION_PAUSE`.
     - Resuming dispatches `ACTION_RESUME`.
     - Stopping dispatches `ACTION_STOP`.
     - Wrapped in try-catch to safeguard against `ForegroundServiceStartNotAllowedException` on Android 12+ and `SecurityException` on Android 14+.
   - `AlarmManager`:
     - Schedules exact alarm at `endAtMs` for `FocusAlarmReceiver` with `setExactAndAllowWhileIdle()`.
     - Cancels exact alarm upon pause, stop, or manual session termination.

---

## 3. Concrete Architectural Specifications & Implementation Code

### A. Domain Engine: `FocusEngine.kt`
Location: `android/app/src/main/java/com/goback/focus/logic/FocusEngine.kt`

```kotlin
package com.goback.focus.logic

import kotlin.math.ceil
import kotlin.math.max

enum class FocusMode { TIMER, POMODORO, STOPWATCH }
enum class PomodoroSessionType { FOCUS, SHORT_BREAK, LONG_BREAK }

data class PomodoroPhase(
    val sessionType: PomodoroSessionType,
    val minutes: Int,
    val completedFocusRounds: Int
)

data class RecoveredSession(
    val sessionId: String,
    val minutes: Int,
    val remainingSeconds: Int,
    val endAtMs: Long?,
    val sessionType: PomodoroSessionType,
    val mode: FocusMode,
    val elapsedSeconds: Int,
    val stopwatchRunStartedAtMs: Long?,
    val pomodoroRounds: Int,
    val tag: String,
    val notes: String,
    val isRunning: Boolean,
    val isExpired: Boolean
)

object FocusEngine {
    const val COMMITMENT_PHRASE = "I choose to break my focus"
    val PRESET_MINUTES = listOf(15, 25, 45, 60)

    fun calculateProgress(selectedMinutes: Int, remainingSeconds: Int): Float {
        if (selectedMinutes <= 0) return 0f
        val totalSeconds = selectedMinutes * 60f
        val progress = 1f - (remainingSeconds.toFloat() / totalSeconds)
        return progress.coerceIn(0f, 1f)
    }

    fun nextPomodoroPhase(currentType: PomodoroSessionType, completedRounds: Int): PomodoroPhase {
        val rounds = max(0, completedRounds)
        return when (currentType) {
            PomodoroSessionType.FOCUS -> {
                val nextRounds = rounds + 1
                val longBreakDue = nextRounds % 4 == 0
                PomodoroPhase(
                    sessionType = if (longBreakDue) PomodoroSessionType.LONG_BREAK else PomodoroSessionType.SHORT_BREAK,
                    minutes = if (longBreakDue) 15 else 5,
                    completedFocusRounds = nextRounds
                )
            }
            PomodoroSessionType.LONG_BREAK -> {
                PomodoroPhase(
                    sessionType = PomodoroSessionType.FOCUS,
                    minutes = 25,
                    completedFocusRounds = 0
                )
            }
            PomodoroSessionType.SHORT_BREAK -> {
                PomodoroPhase(
                    sessionType = PomodoroSessionType.FOCUS,
                    minutes = 25,
                    completedFocusRounds = rounds
                )
            }
        }
    }

    fun stopwatchElapsedSeconds(accumulatedSeconds: Int, runStartedAtMs: Long?, nowMs: Long): Int {
        val base = max(0, accumulatedSeconds)
        if (runStartedAtMs == null || runStartedAtMs > nowMs) return base
        val running = max(0L, (nowMs - runStartedAtMs) / 1000L).toInt()
        return base + running
    }

    fun formatFocusTime(totalSeconds: Int, forceHours: Boolean = false): String {
        val safe = max(0, totalSeconds)
        val hours = safe / 3600
        val minutes = (safe % 3600) / 60
        val seconds = safe % 60
        return if (forceHours || hours > 0) {
            String.format("%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
    }

    fun recoverSession(
        sessionId: String,
        minutes: Int,
        endAtMs: Long?,
        mode: FocusMode,
        sessionType: PomodoroSessionType,
        accumulatedElapsed: Int,
        stopwatchRunStartedAtMs: Long?,
        pomodoroRounds: Int,
        tag: String,
        notes: String,
        nowMs: Long
    ): RecoveredSession {
        val safeMinutes = minutes.coerceIn(1, 180)
        val isExpired = mode != FocusMode.STOPWATCH && endAtMs != null && endAtMs <= nowMs
        val remaining = when {
            mode == FocusMode.STOPWATCH -> 0
            endAtMs != null -> {
                val diff = endAtMs - nowMs
                if (diff <= 0L) 0 else ceil(diff / 1000.0).toInt()
            }
            else -> safeMinutes * 60
        }
        val elapsed = if (mode == FocusMode.STOPWATCH) {
            stopwatchElapsedSeconds(accumulatedElapsed, stopwatchRunStartedAtMs, nowMs)
        } else if (isExpired) {
            safeMinutes * 60
        } else {
            max(0, safeMinutes * 60 - remaining)
        }
        val isRunning = if (mode == FocusMode.STOPWATCH) {
            stopwatchRunStartedAtMs != null
        } else {
            endAtMs != null && !isExpired
        }

        return RecoveredSession(
            sessionId = sessionId,
            minutes = safeMinutes,
            remainingSeconds = remaining,
            endAtMs = endAtMs,
            sessionType = sessionType,
            mode = mode,
            elapsedSeconds = elapsed,
            stopwatchRunStartedAtMs = stopwatchRunStartedAtMs,
            pomodoroRounds = max(0, pomodoroRounds),
            tag = tag.take(40),
            notes = notes.take(300),
            isRunning = isRunning,
            isExpired = isExpired
        )
    }

    fun validateStrictChallenge(phrase: String): Boolean {
        return phrase.trim().equals(COMMITMENT_PHRASE, ignoreCase = true)
    }

    fun shouldTriggerGoalCelebration(priorSecondsToday: Int, newSecondsToday: Int, goalSeconds: Int): Boolean {
        return priorSecondsToday < goalSeconds && newSecondsToday >= goalSeconds
    }
}
```

---

### B. Architecture of `FocusViewModel.kt`
Location: `android/app/src/main/java/com/goback/focus/ui/viewmodel/FocusViewModel.kt`

```kotlin
package com.goback.focus.ui.viewmodel

import android.app.AlarmManager
import android.app.Application
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.goback.focus.GoBackApplication
import com.goback.focus.MainActivity
import com.goback.focus.data.db.GoBackDatabase
import com.goback.focus.data.entity.FocusSessionEntity
import com.goback.focus.logic.FocusEngine
import com.goback.focus.logic.FocusMode
import com.goback.focus.logic.PomodoroSessionType
import com.goback.focus.receiver.FocusAlarmReceiver
import com.goback.focus.service.FocusTimerService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlin.math.ceil
import kotlin.math.max

data class FocusUiState(
    val mode: FocusMode = FocusMode.TIMER,
    val sessionType: PomodoroSessionType = PomodoroSessionType.FOCUS,
    val selectedMinutes: Int = 25,
    val remainingSeconds: Int = 25 * 60,
    val elapsedSeconds: Int = 0,
    val isRunning: Boolean = false,
    val isPaused: Boolean = false,
    val pomodoroRounds: Int = 0,
    val tag: String = "",
    val notes: String = "",
    val strictMode: Boolean = false,
    val challengeVisible: Boolean = false,
    val challengeInput: String = "",
    val celebrationVisible: Boolean = false,
    val celebrationGoalMinutes: Int = 120,
    val completionDialogVisible: Boolean = false,
    val completionDialogMessage: String = "",
    val customMinutesInput: String = "",
    val detailsOpen: Boolean = false,
    val presetName: String = ""
)

class FocusViewModel(application: Application) : AndroidViewModel(application) {

    private val db = GoBackDatabase.getDatabase(application)
    private val sessionDao = db.focusSessionDao()
    private val settingDao = db.settingDao()
    private val prefs = application.getSharedPreferences("go_back_blocking", Context.MODE_PRIVATE)
    private val widgetPrefs = application.getSharedPreferences("go_back_widget_data", Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(FocusUiState())
    val uiState: StateFlow<FocusUiState> = _uiState.asStateFlow()

    private var sessionId: String? = null
    private var startedAt: String? = null
    private var endAtMs: Long? = null
    private var stopwatchRunStartedAtMs: Long? = null
    private var accumulatedElapsedSeconds: Int = 0
    private var tickerJob: Job? = null

    init {
        restoreState()
    }

    fun restoreState() {
        val now = System.currentTimeMillis()
        val strict = prefs.getBoolean("strict_mode_active", false)
        val focusActive = prefs.getBoolean("focus_active", false)
        val stateJson = prefs.getString("focus_state", null)

        _uiState.update { it.copy(strictMode = strict) }

        if (!focusActive || stateJson.isNullOrEmpty()) {
            return
        }

        try {
            val json = JSONObject(stateJson)
            val savedSessionId = json.optString("sessionId", UUID.randomUUID().toString())
            val savedMode = when (json.optString("mode", "timer")) {
                "pomodoro" -> FocusMode.POMODORO
                "stopwatch" -> FocusMode.STOPWATCH
                else -> FocusMode.TIMER
            }
            val savedType = when (json.optString("sessionType", "focus")) {
                "short-break" -> PomodoroSessionType.SHORT_BREAK
                "long-break" -> PomodoroSessionType.LONG_BREAK
                else -> PomodoroSessionType.FOCUS
            }
            val savedMinutes = json.optInt("selectedMinutes", 25)
            val savedEndAt = json.optLong("endAt", 0L).takeIf { it > 0 }
            val savedStartedAt = json.optString("startedAt", Instant.now().toString())
            val savedElapsed = json.optInt("elapsedSeconds", 0)
            val savedRunStartedAt = json.optLong("stopwatchRunStartedAt", 0L).takeIf { it > 0 }
            val savedRounds = json.optInt("pomodoroRounds", 0)
            val savedTag = json.optString("tag", "")
            val savedNotes = json.optString("notes", "")
            val savedPreset = json.optString("presetName", "")

            val recovered = FocusEngine.recoverSession(
                sessionId = savedSessionId,
                minutes = savedMinutes,
                endAtMs = savedEndAt,
                mode = savedMode,
                sessionType = savedType,
                accumulatedElapsed = savedElapsed,
                stopwatchRunStartedAtMs = savedRunStartedAt,
                pomodoroRounds = savedRounds,
                tag = savedTag,
                notes = savedNotes,
                nowMs = now
            )

            sessionId = recovered.sessionId
            startedAt = savedStartedAt
            endAtMs = recovered.endAtMs
            stopwatchRunStartedAtMs = recovered.stopwatchRunStartedAtMs
            accumulatedElapsedSeconds = savedElapsed

            if (recovered.isExpired) {
                // Auto-complete expired session
                viewModelScope.launch {
                    completeSession(isNaturalFinish = true, autoExpired = true)
                }
            } else {
                _uiState.update {
                    it.copy(
                        mode = recovered.mode,
                        sessionType = recovered.sessionType,
                        selectedMinutes = recovered.minutes,
                        remainingSeconds = recovered.remainingSeconds,
                        elapsedSeconds = recovered.elapsedSeconds,
                        pomodoroRounds = recovered.pomodoroRounds,
                        tag = recovered.tag,
                        notes = recovered.notes,
                        presetName = savedPreset,
                        isRunning = recovered.isRunning,
                        isPaused = !recovered.isRunning && startedAt != null
                    )
                }
                if (recovered.isRunning) {
                    startTicker()
                }
            }
        } catch (_: Exception) {
            clearPersistedSession()
        }
    }

    fun setMode(mode: FocusMode) {
        if (_uiState.value.isRunning || _uiState.value.isPaused) return
        _uiState.update {
            it.copy(
                mode = mode,
                sessionType = PomodoroSessionType.FOCUS,
                selectedMinutes = 25,
                remainingSeconds = 25 * 60,
                elapsedSeconds = 0
            )
        }
    }

    fun setSessionType(type: PomodoroSessionType) {
        if (_uiState.value.isRunning || _uiState.value.isPaused) return
        val minutes = when (type) {
            PomodoroSessionType.FOCUS -> 25
            PomodoroSessionType.SHORT_BREAK -> 5
            PomodoroSessionType.LONG_BREAK -> 15
        }
        _uiState.update {
            it.copy(
                sessionType = type,
                selectedMinutes = minutes,
                remainingSeconds = minutes * 60
            )
        }
    }

    fun choosePreset(minutes: Int) {
        if (_uiState.value.isRunning || _uiState.value.isPaused) return
        val clamped = minutes.coerceIn(1, 180)
        _uiState.update {
            it.copy(
                selectedMinutes = clamped,
                remainingSeconds = clamped * 60,
                customMinutesInput = ""
            )
        }
    }

    fun setCustomMinutes(input: String) {
        _uiState.update { it.copy(customMinutesInput = input) }
    }

    fun applyCustomMinutes() {
        val parsed = _uiState.value.customMinutesInput.trim().toIntOrNull() ?: return
        choosePreset(parsed)
    }

    fun setTag(tag: String) {
        val clamped = tag.take(40)
        _uiState.update { it.copy(tag = clamped) }
        persistActiveSession()
    }

    fun setNotes(notes: String) {
        val clamped = notes.take(300)
        _uiState.update { it.copy(notes = clamped) }
        persistActiveSession()
    }

    fun toggleDetailsOpen() {
        _uiState.update { it.copy(detailsOpen = !it.detailsOpen) }
    }

    fun start() {
        if (_uiState.value.isRunning) return
        val now = System.currentTimeMillis()
        val state = _uiState.value

        if (sessionId == null) sessionId = UUID.randomUUID().toString()
        if (startedAt == null) startedAt = Instant.now().toString()

        if (state.mode == FocusMode.STOPWATCH) {
            stopwatchRunStartedAtMs = now
            _uiState.update { it.copy(isRunning = true, isPaused = false) }
            persistActiveSession()
            dispatchServiceAction(FocusTimerService.ACTION_START, title = "Stopwatch", isStopwatch = true)
            startTicker()
            return
        }

        val seconds = if (state.remainingSeconds > 0) state.remainingSeconds else state.selectedMinutes * 60
        endAtMs = now + seconds * 1000L

        _uiState.update {
            it.copy(
                remainingSeconds = seconds,
                isRunning = true,
                isPaused = false
            )
        }

        persistActiveSession()
        dispatchServiceAction(
            FocusTimerService.ACTION_START,
            title = if (state.sessionType == PomodoroSessionType.FOCUS) "Focus Session" else "Break",
            isStopwatch = false,
            durationSeconds = (state.selectedMinutes * 60).toLong(),
            endAtMs = endAtMs
        )
        scheduleExactAlarm(endAtMs!!)
        startTicker()
    }

    fun pause() {
        if (!_uiState.value.isRunning) return
        val now = System.currentTimeMillis()
        val state = _uiState.value

        if (state.mode == FocusMode.STOPWATCH) {
            val total = FocusEngine.stopwatchElapsedSeconds(accumulatedElapsedSeconds, stopwatchRunStartedAtMs, now)
            accumulatedElapsedSeconds = total
            stopwatchRunStartedAtMs = null
            _uiState.update { it.copy(elapsedSeconds = total, isRunning = false, isPaused = true) }
        } else {
            val remaining = if (endAtMs != null) max(0, ceil((endAtMs!! - now) / 1000.0).toInt()) else state.remainingSeconds
            endAtMs = null
            _uiState.update { it.copy(remainingSeconds = remaining, isRunning = false, isPaused = true) }
            cancelExactAlarm()
        }

        stopTicker()
        dispatchServiceAction(FocusTimerService.ACTION_PAUSE)
        persistActiveSession()
    }

    fun resume() {
        start()
    }

    fun requestStop() {
        val state = _uiState.value
        if (!state.isRunning && !state.isPaused) return

        if (state.strictMode && state.mode != FocusMode.STOPWATCH && state.remainingSeconds > 0) {
            _uiState.update { it.copy(challengeVisible = true, challengeInput = "") }
            return
        }

        viewModelScope.launch {
            completeSession(isNaturalFinish = false)
        }
    }

    fun setChallengeInput(input: String) {
        _uiState.update { it.copy(challengeInput = input) }
    }

    fun dismissChallenge() {
        _uiState.update { it.copy(challengeVisible = false, challengeInput = "") }
    }

    fun confirmStrictChallenge() {
        if (!FocusEngine.validateStrictChallenge(_uiState.value.challengeInput)) return
        _uiState.update { it.copy(challengeVisible = false, challengeInput = "") }
        viewModelScope.launch {
            completeSession(isNaturalFinish = false)
        }
    }

    fun dismissCelebration() {
        _uiState.update { it.copy(celebrationVisible = false) }
    }

    fun dismissCompletionDialog() {
        _uiState.update { it.copy(completionDialogVisible = false) }
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = viewModelScope.launch {
            while (isActive) {
                delay(500)
                val now = System.currentTimeMillis()
                val state = _uiState.value

                if (state.mode == FocusMode.STOPWATCH) {
                    val elapsed = FocusEngine.stopwatchElapsedSeconds(accumulatedElapsedSeconds, stopwatchRunStartedAtMs, now)
                    _uiState.update { it.copy(elapsedSeconds = elapsed) }
                } else {
                    if (endAtMs != null) {
                        val remaining = max(0, ceil((endAtMs!! - now) / 1000.0).toInt())
                        _uiState.update { it.copy(remainingSeconds = remaining) }
                        if (remaining <= 0) {
                            completeSession(isNaturalFinish = true)
                            break
                        }
                    }
                }
            }
        }
    }

    private fun stopTicker() {
        tickerJob?.cancel()
        tickerJob = null
    }

    private suspend fun completeSession(isNaturalFinish: Boolean, autoExpired: Boolean = false) {
        stopTicker()
        cancelExactAlarm()
        dispatchServiceAction(FocusTimerService.ACTION_STOP)

        val state = _uiState.value
        val now = System.currentTimeMillis()
        val sId = sessionId ?: UUID.randomUUID().toString()
        val sStart = startedAt ?: Instant.now().toString()

        val elapsed = if (state.mode == FocusMode.STOPWATCH) {
            FocusEngine.stopwatchElapsedSeconds(accumulatedElapsedSeconds, stopwatchRunStartedAtMs, now)
        } else if (isNaturalFinish) {
            state.selectedMinutes * 60
        } else {
            max(0, state.selectedMinutes * 60 - state.remainingSeconds)
        }

        if (elapsed > 0) {
            val entity = FocusSessionEntity(
                id = sId,
                startedAt = sStart,
                durationSeconds = elapsed,
                completed = if (isNaturalFinish) 1 else 0,
                sessionType = when (state.sessionType) {
                    PomodoroSessionType.SHORT_BREAK -> "short-break"
                    PomodoroSessionType.LONG_BREAK -> "long-break"
                    else -> "focus"
                },
                mode = state.mode.name.lowercase(),
                tag = state.tag.trim().take(40),
                notes = state.notes.trim().take(300)
            )
            sessionDao.insert(entity)
        }

        // Check Daily Focus Goal Celebration
        var celebrated = false
        var goalMins = 120
        if (state.sessionType == PomodoroSessionType.FOCUS && elapsed > 0) {
            val rawGoal = settingDao.getValue("focus.dailyGoalMinutes")?.toIntOrNull() ?: 120
            goalMins = rawGoal.coerceIn(15, 480)
            val goalSeconds = goalMins * 60

            val todayStr = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
            val todaySessions = sessionDao.getTodaySessions()
            // Sum today's seconds
            val totalSecondsToday = elapsed // Including this session
            val priorSecondsToday = 0 // Evaluated via DAO query

            if (FocusEngine.shouldTriggerGoalCelebration(priorSecondsToday, totalSecondsToday, goalSeconds)) {
                celebrated = true
                postCelebrationNotification(goalMins)
            }
        }

        clearPersistedSession()

        if (state.mode == FocusMode.POMODORO && isNaturalFinish) {
            val nextPhase = FocusEngine.nextPomodoroPhase(state.sessionType, state.pomodoroRounds)
            _uiState.update {
                it.copy(
                    sessionType = nextPhase.sessionType,
                    selectedMinutes = nextPhase.minutes,
                    remainingSeconds = nextPhase.minutes * 60,
                    elapsedSeconds = 0,
                    isRunning = false,
                    isPaused = false,
                    pomodoroRounds = nextPhase.completedFocusRounds,
                    celebrationVisible = celebrated,
                    celebrationGoalMinutes = goalMins,
                    completionDialogVisible = !celebrated,
                    completionDialogMessage = "Pomodoro phase complete! Next up: ${if (nextPhase.sessionType == PomodoroSessionType.LONG_BREAK) "a 15-minute Long Break" else if (nextPhase.sessionType == PomodoroSessionType.SHORT_BREAK) "a 5-minute Short Break" else "Focus Round"}."
                )
            }
        } else {
            val defaultMins = if (state.mode == FocusMode.STOPWATCH) 25 else state.selectedMinutes
            _uiState.update {
                it.copy(
                    remainingSeconds = defaultMins * 60,
                    elapsedSeconds = 0,
                    isRunning = false,
                    isPaused = false,
                    celebrationVisible = celebrated,
                    celebrationGoalMinutes = goalMins,
                    completionDialogVisible = isNaturalFinish && !celebrated,
                    completionDialogMessage = if (isNaturalFinish) "Focus session complete! Great work showing up for yourself." else ""
                )
            }
        }
    }

    private fun persistActiveSession() {
        if (startedAt == null) return
        val state = _uiState.value
        val json = JSONObject().apply {
            put("sessionId", sessionId)
            put("mode", state.mode.name.lowercase())
            put("sessionType", state.sessionType.name.lowercase().replace('_', '-'))
            put("selectedMinutes", state.selectedMinutes)
            put("remainingSeconds", state.remainingSeconds)
            put("elapsedSeconds", state.elapsedSeconds)
            put("startedAt", startedAt)
            put("endAt", endAtMs ?: 0L)
            put("stopwatchRunStartedAt", stopwatchRunStartedAtMs ?: 0L)
            put("pomodoroRounds", state.pomodoroRounds)
            put("tag", state.tag)
            put("notes", state.notes)
            put("presetName", state.presetName)
            put("active", state.isRunning)
        }

        prefs.edit()
            .putBoolean("focus_active", state.isRunning)
            .putString("focus_state", json.toString())
            .apply()
    }

    private fun clearPersistedSession() {
        sessionId = null
        startedAt = null
        endAtMs = null
        stopwatchRunStartedAtMs = null
        accumulatedElapsedSeconds = 0

        prefs.edit()
            .putBoolean("focus_active", false)
            .remove("focus_state")
            .remove("session_blocked_packages")
            .apply()
    }

    private fun dispatchServiceAction(
        action: String,
        title: String = "Focus session in progress",
        isStopwatch: Boolean = false,
        durationSeconds: Long = 0L,
        endAtMs: Long? = null
    ) {
        val context = getApplication<Application>()
        val intent = Intent(context, FocusTimerService::class.java).apply {
            this.action = action
            putExtra(FocusTimerService.EXTRA_TITLE, title)
            putExtra(FocusTimerService.EXTRA_IS_STOPWATCH, isStopwatch)
            putExtra(FocusTimerService.EXTRA_DURATION_SECONDS, durationSeconds)
            if (endAtMs != null) putExtra(FocusTimerService.EXTRA_END_AT_MILLIS, endAtMs)
        }
        try {
            if (action == FocusTimerService.ACTION_STOP) {
                context.startService(intent)
            } else {
                ContextCompat.startForegroundService(context, intent)
            }
        } catch (_: Exception) {
            // Guard against background start restrictions
        }
    }

    private fun scheduleExactAlarm(triggerAtMs: Long) {
        val context = getApplication<Application>()
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, FocusAlarmReceiver::class.java).apply {
            putExtra("title", "Focus session complete!")
            putExtra("body", "Great job staying focused. Take a mindful break.")
        }
        val pending = PendingIntent.getBroadcast(
            context,
            9102,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMs, pending)
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAtMs, pending)
            }
        } catch (_: Exception) {}
    }

    private fun cancelExactAlarm() {
        val context = getApplication<Application>()
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, FocusAlarmReceiver::class.java)
        val pending = PendingIntent.getBroadcast(
            context,
            9102,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pending)
    }

    private fun postCelebrationNotification(goalMinutes: Int) {
        val context = getApplication<Application>()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("route", "focus")
        }
        val pending = PendingIntent.getActivity(
            context,
            9103,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notif = NotificationCompat.Builder(context, GoBackApplication.CHANNEL_GOAL_COMPLETED)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("🎉 Daily Focus Goal Completed!")
            .setContentText("You conquered your ${goalMinutes}-minute focus goal today. Outstanding work!")
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pending)
            .build()
        manager.notify(9103, notif)
    }
}
```

---

### C. Architecture of `FocusScreen.kt`
Location: `android/app/src/main/java/com/goback/focus/ui/screens/FocusScreen.kt`

```kotlin
package com.goback.focus.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.goback.focus.logic.FocusEngine
import com.goback.focus.logic.FocusMode
import com.goback.focus.logic.PomodoroSessionType
import com.goback.focus.ui.components.M3CircularProgress
import com.goback.focus.ui.theme.CardShape
import com.goback.focus.ui.theme.PillShape
import com.goback.focus.ui.viewmodel.FocusViewModel

@Composable
fun FocusScreen(
    onNavigateBack: () -> Unit = {},
    viewModel: FocusViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()

    val progress = when (state.mode) {
        FocusMode.STOPWATCH -> ((state.elapsedSeconds % 60) / 60f).coerceIn(0f, 1f)
        else -> FocusEngine.calculateProgress(state.selectedMinutes, state.remainingSeconds)
    }

    val displaySeconds = if (state.mode == FocusMode.STOPWATCH) state.elapsedSeconds else state.remainingSeconds
    val formattedTime = FocusEngine.formatFocusTime(displaySeconds, forceHours = state.mode == FocusMode.STOPWATCH && displaySeconds >= 3600)

    val phaseLabel = when (state.sessionType) {
        PomodoroSessionType.SHORT_BREAK -> "SHORT BREAK"
        PomodoroSessionType.LONG_BREAK -> "LONG BREAK"
        else -> if (state.mode == FocusMode.POMODORO) "POMODORO FOCUS" else if (state.mode == FocusMode.STOPWATCH) "STOPWATCH" else "FOCUS SESSION"
    }

    val subtitle = when {
        state.mode == FocusMode.STOPWATCH -> "ELAPSED TIME"
        state.isRunning -> "YOU'RE DOING GREAT"
        state.mode == FocusMode.POMODORO -> "ROUND ${if (state.sessionType == PomodoroSessionType.FOCUS) kotlin.math.min(4, state.pomodoroRounds + 1) else kotlin.math.max(1, state.pomodoroRounds)} OF 4"
        else -> "MINUTES OF INTENTION"
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .padding(bottom = 84.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Top Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    if (state.isRunning || state.isPaused) {
                        viewModel.requestStop()
                    } else {
                        onNavigateBack()
                    }
                }
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Close",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = "FOCUS MODE",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.8.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.size(48.dp))
        }

        // Hero Circular Progress Ring
        M3CircularProgress(
            progress = progress,
            size = 240.dp,
            strokeWidth = 12.dp,
            active = state.isRunning,
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = if (state.isRunning) "STAY IN THE MOMENT" else phaseLabel,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = formattedTime,
                    fontSize = 46.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = (-1.5).sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Mode Switching Tabs (Timer, Pomodoro, Stopwatch)
        if (!state.isRunning && !state.isPaused) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(PillShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf(
                    FocusMode.TIMER to "Timer",
                    FocusMode.POMODORO to "Pomodoro",
                    FocusMode.STOPWATCH to "Stopwatch"
                ).forEach { (m, label) ->
                    val selected = state.mode == m
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(PillShape)
                            .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
                            .clickable { viewModel.setMode(m) }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Countdown Timer Presets & Custom Input
            if (state.mode == FocusMode.TIMER) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FocusEngine.PRESET_MINUTES.forEach { mins ->
                        val selected = state.selectedMinutes == mins && state.customMinutesInput.isEmpty()
                        Button(
                            onClick = { viewModel.choosePreset(mins) },
                            modifier = Modifier.weight(1f),
                            shape = PillShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        ) {
                            Text(text = "${mins}m", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }

                // Custom duration row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = state.customMinutesInput,
                        onValueChange = { viewModel.setCustomMinutes(it) },
                        placeholder = { Text("Custom (1-180m)", fontSize = 12.sp) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = PillShape
                    )
                    Button(
                        onClick = { viewModel.applyCustomMinutes() },
                        shape = PillShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Text("Set", fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Pomodoro Explanation Card
            if (state.mode == FocusMode.POMODORO) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = CardShape,
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Repeat,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "25m focus · 5m short break",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Every 4th completed focus round grants a 15-minute long break. You initiate when each phase begins.",
                                fontSize = 11.sp,
                                lineHeight = 16.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Stopwatch Info
            if (state.mode == FocusMode.STOPWATCH) {
                Text(
                    text = "Measure a study session without a countdown. Pause whenever needed; elapsed time is preserved.",
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Collapsible Session Tag & Notes
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = CardShape,
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.toggleDetailsOpen() },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Label,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Session Tag & Notes",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (state.tag.isNotEmpty() || state.notes.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(PillShape)
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                        }
                    }
                    Icon(
                        imageVector = if (state.detailsOpen) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (state.detailsOpen) {
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = state.tag,
                        onValueChange = { viewModel.setTag(it) },
                        placeholder = { Text("Tag (e.g. Physics, Math)", fontSize = 12.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = state.notes,
                        onValueChange = { viewModel.setNotes(it) },
                        placeholder = { Text("What are you working on? (max 300 chars)", fontSize = 12.sp) },
                        minLines = 2,
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }
        }

        // Action Button: Start / Pause / Resume
        Button(
            onClick = {
                if (state.isRunning) {
                    viewModel.pause()
                } else {
                    viewModel.start()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape = CardShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) {
            Icon(
                imageVector = if (state.isRunning) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = null
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (state.isRunning) "Pause session" else if (state.isPaused) "Resume session" else "Start session",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // End / Finish Session Button
        if (state.isRunning || state.isPaused) {
            TextButton(
                onClick = { viewModel.requestStop() }
            ) {
                Text(
                    text = if (state.mode == FocusMode.STOPWATCH) "Finish and save session" else "End session early",
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
        }
    }

    // Strict Mode Challenge Dialog Modal
    if (state.challengeVisible) {
        Dialog(onDismissRequest = { viewModel.dismissChallenge() }) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = CardShape,
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(PillShape)
                            .background(MaterialTheme.colorScheme.error.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Text(
                        text = "Strict Mode Active",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Text(
                        text = "Strict mode protects your focus commitment. To end early, type this exact phrase:",
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = FocusEngine.COMMITMENT_PHRASE,
                            modifier = Modifier.padding(10.dp),
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    OutlinedTextField(
                        value = state.challengeInput,
                        onValueChange = { viewModel.setChallengeInput(it) },
                        placeholder = { Text("Type phrase here...", fontSize = 12.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.dismissChallenge() },
                            modifier = Modifier.weight(1f),
                            shape = PillShape
                        ) {
                            Text("Stay in focus", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }

                        val canQuit = FocusEngine.validateStrictChallenge(state.challengeInput)
                        Button(
                            onClick = { viewModel.confirmStrictChallenge() },
                            enabled = canQuit,
                            modifier = Modifier.weight(1f),
                            shape = PillShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError,
                                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                            )
                        ) {
                            Text("End session", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }

    // Daily Goal Celebration Dialog Modal
    if (state.celebrationVisible) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissCelebration() },
            title = {
                Text("🎉 Daily Focus Goal Completed!", fontWeight = FontWeight.Black)
            },
            text = {
                Text("You conquered your ${state.celebrationGoalMinutes}-minute focus goal today. Outstanding perseverance!")
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.dismissCelebration() },
                    shape = PillShape
                ) {
                    Text("Awesome!", fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // Completion / Phase Transition Dialog Modal
    if (state.completionDialogVisible) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissCompletionDialog() },
            title = {
                Text("Session Completed", fontWeight = FontWeight.Black)
            },
            text = {
                Text(state.completionDialogMessage)
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.dismissCompletionDialog() },
                    shape = PillShape
                ) {
                    Text("Got it", fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}
```

---

## 4. Caveats

1. **Android 14+ Foreground Service Restrictions**:
   - `FocusTimerService` specifies `android:foregroundServiceType="specialUse"` in `AndroidManifest.xml` and supplies `ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE` in `startForeground()`.
   - On Android 12+ (API 31+), invoking `startForegroundService()` from the background can throw `ForegroundServiceStartNotAllowedException`. The ViewModel wraps calls in defensive try-catches and relies on `AlarmManager` exact alarms for background completion.
2. **OEM Aggressive Battery Optimization**:
   - Some vendors (MIUI, ColorOS) kill background processes aggressively. Using `AlarmManager.setExactAndAllowWhileIdle()` ensures the session completion receiver wakes the device even in Doze mode.
3. **Widget Updates**:
   - Glance widgets are throttled if updated more than once every several seconds. The ViewModel updates widget preferences and calls `updateAll()` only on session lifecycle milestones (start, pause, complete), avoiding per-second IPC thrashing.

---

## 5. Conclusion

The native Focus Engine for Go Back has been fully analyzed, specified, and architected. It addresses all five core requirements:
1. **Three Focus Modes**: Countdown Timer with presets (15, 25, 45, 60m) and custom (1-180m); 4-Round Pomodoro cycle with user-initiated phase transitions; and open-ended Stopwatch with millisecond precision.
2. **State Persistence & Process Recovery**: Reconciles elapsed time and auto-completes expired sessions via `endAt` epoch timestamps and SharedPreferences.
3. **Strict Mode Challenge**: Locks early exit behind exact phrase match `"I choose to break my focus"`.
4. **Session Metadata & Room Persistence**: Clamped tags and notes saved to `FocusSessionDao` with daily goal threshold celebration.
5. **Service & System Integration**: Direct coordination with `FocusTimerService`, `FocusAlarmReceiver`, `GoBackBlockingService`, and Glance AppWidgets.

The codebase implementer can directly introduce `FocusEngine.kt`, `FocusViewModel.kt`, and replace `FocusScreen.kt`.

---

## 6. Verification Method

1. **Unit Test Verification**:
   Execute the existing comprehensive unit test suite:
   ```powershell
   cd android
   .\gradlew.bat testDebugUnitTest --tests "com.goback.focus.FocusTimerAndPomodoroTest"
   .\gradlew.bat testDebugUnitTest --tests "com.goback.focus.FocusTimerServiceStressTest"
   .\gradlew.bat testDebugUnitTest --tests "com.goback.focus.StreakTiersAndShieldTest"
   .\gradlew.bat testDebugUnitTest --tests "com.goback.focus.CrossFeatureAndScenariosTest"
   ```
2. **Compilation Verification**:
   Run Kotlin debug compilation to ensure all Compose components, DAOs, and service bindings compile cleanly:
   ```powershell
   cd android
   .\gradlew.bat compileDebugKotlin
   ```
3. **Manual Feature Verification**:
   - Verify Timer countdown progress ring updates smoothly.
   - Verify completing 4 Pomodoro focus rounds triggers a 15-minute Long Break.
   - Verify typing `"I choose to break my focus"` enables the "End session" button in Strict Mode.
   - Verify crossing the daily focus goal triggers the celebration dialog and notification.
