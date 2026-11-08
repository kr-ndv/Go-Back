package com.goback.focus.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.runtime.DisposableEffect
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
    onNavigateToPresets: () -> Unit = {},
    viewModel: FocusViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.loadPendingPreset()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

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
            if (!state.isRunning && !state.isPaused) {
                TextButton(onClick = onNavigateToPresets) {
                    Text("Presets", fontWeight = FontWeight.Bold)
                }
            } else {
                Spacer(modifier = Modifier.size(48.dp))
            }
        }

        if (state.presetName.isNotBlank()) {
            Text(
                text = "Preset · ${state.presetName}",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth()
            )
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
