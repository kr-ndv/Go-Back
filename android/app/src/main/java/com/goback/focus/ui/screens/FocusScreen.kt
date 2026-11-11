package com.goback.focus.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.outlined.*
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
import com.goback.focus.ui.viewmodel.SettingsViewModel

@Composable
fun FocusScreen(
    onNavigateBack: () -> Unit = {},
    onNavigateToPresets: () -> Unit = {},
    onNavigateToHistory: () -> Unit = {},
    onNavigateToSchedules: () -> Unit = {},
    viewModel: FocusViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val settingsViewModel: SettingsViewModel = viewModel()
    val selectedAlarmSound by settingsViewModel.selectedAlarmSound.collectAsStateWithLifecycle()
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
        else -> if (state.mode == FocusMode.POMODORO) "POMODORO FOCUS" else if (state.mode == FocusMode.STOPWATCH) "STOPWATCH" else "READY WHEN YOU ARE"
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .padding(bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // 1. Top Header
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = "MAKE SPACE FOR WHAT MATTERS",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.6.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Focus mode",
                fontSize = 30.sp,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "One thing at a time is enough.",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Preset indicator if active
        if (state.presetName.isNotBlank()) {
            Surface(
                shape = PillShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.align(Alignment.Start)
            ) {
                Text(
                    text = "Preset · ${state.presetName}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }

        // 2. Hero Focus Card with Circular Ring
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = CardShape,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Circular Ring
                M3CircularProgress(
                    progress = progress,
                    size = 230.dp,
                    strokeWidth = 10.dp,
                    active = state.isRunning,
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.HourglassBottom,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = formattedTime,
                            fontSize = 42.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = (-1.2).sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (state.isRunning) "YOU'RE DOING GREAT" else phaseLabel,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Main CTA Button
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
                        .height(52.dp),
                    shape = PillShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(
                        imageVector = if (state.isRunning) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (state.isRunning) "Pause session" else if (state.isPaused) "Resume session" else "Open focus timer",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Stop / End early button
                if (state.isRunning || state.isPaused) {
                    TextButton(onClick = { viewModel.requestStop() }) {
                        Text(
                            text = if (state.mode == FocusMode.STOPWATCH) "Finish and save session" else "End session early",
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }

        // 3. Choose how you focus Section
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "Choose how you focus",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Use a flexible countdown, a guided Pomodoro cycle, or a stopwatch to track open-ended study sessions.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Card 1: Session history
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(CardShape)
                    .clickable { onNavigateToHistory() },
                shape = CardShape,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(PillShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.History,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Session history",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Review saved sessions, tags, notes and weekly focus time.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        imageVector = Icons.Filled.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Card 2: Focus schedule
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(CardShape)
                    .clickable { onNavigateToSchedules() },
                shape = CardShape,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(PillShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CalendarMonth,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Focus schedule",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Plan repeating reminders for your weekly study routine.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        imageVector = Icons.Filled.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // 4. Timer Controls & Presets Drawer
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = CardShape,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                            imageVector = Icons.Outlined.Tune,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Timer settings & mode",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Icon(
                        imageVector = if (state.detailsOpen) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (state.detailsOpen || state.isRunning) {
                    // Mode Switching Tabs
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
                                        .padding(vertical = 8.dp),
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

                        // Presets
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
                                        ),
                                        contentPadding = PaddingValues(vertical = 4.dp)
                                    ) {
                                        Text(text = "${mins}m", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                }
                            }

                            // Presets screen link
                            TextButton(
                                onClick = onNavigateToPresets,
                                modifier = Modifier.align(Alignment.End)
                            ) {
                                Text("Custom presets & app rules >", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }

                    // Session Tag & Notes
                    OutlinedTextField(
                        value = state.tag,
                        onValueChange = { viewModel.setTag(it) },
                        placeholder = { Text("Tag (e.g. Physics, Math)", fontSize = 12.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = state.notes,
                        onValueChange = { viewModel.setNotes(it) },
                        placeholder = { Text("What are you working on? (max 300 chars)", fontSize = 12.sp) },
                        minLines = 2,
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Session finish sound",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = FINISH_ALARM_SOUNDS.find { it.first == selectedAlarmSound }?.second ?: "System default",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(
                            onClick = {
                                val current = FINISH_ALARM_SOUNDS.indexOfFirst { it.first == selectedAlarmSound }
                                val next = FINISH_ALARM_SOUNDS[(current + 1).mod(FINISH_ALARM_SOUNDS.size)]
                                settingsViewModel.setAlarmSound(next.first)
                            }
                        ) {
                            Icon(Icons.Outlined.NotificationsActive, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Change")
                        }
                    }
                }
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
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Text(
                        text = "To end your session early, type this confirmation phrase exactly:",
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = FocusEngine.COMMITMENT_PHRASE,
                            fontWeight = FontWeight.Black,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(10.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    OutlinedTextField(
                        value = state.challengeInput,
                        onValueChange = { viewModel.setChallengeInput(it) },
                        placeholder = { Text("Type phrase here", fontSize = 12.sp) },
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
                            Text("Keep Focusing")
                        }
                        Button(
                            onClick = { viewModel.confirmStrictChallenge() },
                            modifier = Modifier.weight(1f),
                            shape = PillShape,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Give Up", color = Color.White)
                        }
                    }
                }
            }
        }
    }
}
