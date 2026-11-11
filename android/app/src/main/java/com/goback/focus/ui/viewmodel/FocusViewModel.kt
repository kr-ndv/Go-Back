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
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.json.JSONArray
import java.time.Instant
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
    private var pendingPresetPackages: Set<String> = emptySet()

    init {
        restoreState()
    }

    /**
     * Applies a one-shot preset payload saved by FocusPresetsScreen.
     * Session package rules are applied only when the user starts the session.
     */
    fun loadPendingPreset() {
        if (_uiState.value.isRunning || _uiState.value.isPaused) return
        val raw = prefs.getString("focus.pending_preset", null) ?: return
        try {
            val json = JSONObject(raw)
            val mode = when (json.optString("mode", "timer")) {
                "pomodoro" -> FocusMode.POMODORO
                "stopwatch" -> FocusMode.STOPWATCH
                else -> FocusMode.TIMER
            }
            val minutes = json.optInt("durationMinutes", 25).coerceIn(1, 180)
            val packages = mutableSetOf<String>()
            val array = json.optJSONArray("blockedPackages") ?: JSONArray()
            for (index in 0 until array.length()) {
                val value = array.optString(index).trim()
                if (value.isNotEmpty() && value != getApplication<Application>().packageName) {
                    packages += value.lowercase()
                }
            }
            pendingPresetPackages = packages
            _uiState.update {
                it.copy(
                    mode = mode,
                    sessionType = PomodoroSessionType.FOCUS,
                    selectedMinutes = minutes,
                    remainingSeconds = minutes * 60,
                    elapsedSeconds = 0,
                    pomodoroRounds = 0,
                    presetName = json.optString("name", "").take(60),
                    isRunning = false,
                    isPaused = false,
                    customMinutesInput = ""
                )
            }
            prefs.edit().remove("focus.pending_preset").apply()
        } catch (_: Exception) {
            prefs.edit().remove("focus.pending_preset").apply()
        }
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

        if (pendingPresetPackages.isNotEmpty()) {
            prefs.edit()
                .putString("session_blocked_packages", JSONArray(pendingPresetPackages.toList()).toString())
                .apply()
        } else {
            prefs.edit().remove("session_blocked_packages").apply()
        }

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

            val todaySessions = sessionDao.getTodaySessions().firstOrNull() ?: emptyList()
            val priorSecondsToday = todaySessions.filter { it.sessionType == "focus" && it.id != sId }.sumOf { max(0, it.durationSeconds) }
            val totalSecondsToday = priorSecondsToday + elapsed

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
        pendingPresetPackages = emptySet()

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
        GoBackApplication.postGoalCelebrationNotification(
            getApplication<Application>(),
            goalMinutes
        )
    }
}
