package com.goback.focus.ui.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.goback.focus.ui.components.M3CircularProgress
import com.goback.focus.ui.components.ProfileAvatar
import com.goback.focus.ui.components.M3SquigglyProgress
import com.goback.focus.ui.theme.CardShape
import com.goback.focus.ui.theme.PillShape
import com.goback.focus.ui.viewmodel.TodayUiState
import com.goback.focus.ui.viewmodel.TodayViewModel
import com.goback.focus.ui.viewmodel.TodayViewModelFactory

/**
 * TodayScreen — Main dashboard screen of Go Back.
 * Fully bound to live Room Database (FocusSessionDao, ExamDao, SubjectDao, SettingDao)
 * and native UsageStatsManager for daily screen time & phone unlock count.
 */
@Composable
fun TodayScreen(
    onNavigateToFocus: () -> Unit,
    onNavigateToSyllabus: () -> Unit,
    onNavigateToExams: () -> Unit,
    onNavigateToAnalytics: () -> Unit,
    onNavigateToBlocking: () -> Unit = {},
    onNavigateToUsageLimits: () -> Unit = {},
    onNavigateToSchedules: () -> Unit = {},
    onNavigateToNotes: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: TodayViewModel = viewModel(
        factory = TodayViewModelFactory(LocalContext.current)
    )
) {
    val state by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .padding(bottom = 84.dp), // Clearance for floating navigation dock
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        state.loadError?.let { message ->
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                shape = MaterialTheme.shapes.medium
            ) {
                Text(
                    text = message,
                    modifier = Modifier.padding(14.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        // 1. Top Header & Welcome Greeting
        TopHeaderSection(
            profileName = state.profileName,
            profileImageUri = state.profileImageUri,
            onProfileClick = onNavigateToSettings
        )

        // 2. Hero Focus Card with Daily Goal Progress Ring & Streak Badge
        HeroFocusCard(
            state = state,
            onCardClick = onNavigateToFocus
        )

        // 3. Screen Time & Phone Unlocks Glance Card
        ScreenTimeGlanceCard(
            state = state,
            onCardClick = onNavigateToAnalytics
        )

        // 4. Study Milestones Section (Syllabus & Upcoming Exam)
        MilestonesSection(
            state = state,
            onSyllabusClick = onNavigateToSyllabus,
            onExamsClick = onNavigateToExams
        )

        // 5. Focus & Study Tools Quick Access
        ToolsListSection(
            state = state,
            onBlockingClick = onNavigateToBlocking,
            onLimitsClick = onNavigateToUsageLimits,
            onSchedulesClick = onNavigateToSchedules,
            onSyllabusClick = onNavigateToSyllabus,
            onExamsClick = onNavigateToExams,
            onNotesClick = onNavigateToNotes
        )

        // 6. Calm Footer
        Text(
            text = "100% LOCAL-FIRST · REGAIN YOUR TIME",
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.8.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

@Composable
private fun TopHeaderSection(
    profileName: String,
    profileImageUri: String,
    onProfileClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "FOCUS & REGAIN YOUR TIME",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Go Back",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = ".",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Text(
                text = if (profileName.isNotBlank()) "Welcome back, $profileName." else "Welcome back.",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Profile Avatar Button
        Surface(
            modifier = Modifier
                .size(44.dp)
                .clip(PillShape)
                .clickable { onProfileClick() },
            shape = PillShape,
            color = MaterialTheme.colorScheme.surfaceVariant,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            ProfileAvatar(
                name = profileName,
                imageUri = profileImageUri,
                modifier = Modifier.fillMaxSize(),
                contentDescription = "Open profile settings"
            )
        }
    }
}

@Composable
private fun HeroFocusCard(
    state: TodayUiState,
    onCardClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .clickable { onCardClick() },
        color = MaterialTheme.colorScheme.primary,
        shape = CardShape,
        shadowElevation = 4.dp
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Row: Label, Streak Badge & 1-day Shield
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "TODAY'S FOCUS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
                        letterSpacing = 1.sp
                    )

                    // Streak Pill
                    if (state.currentStreak > 0) {
                        Surface(
                            shape = PillShape,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.2f)
                        ) {
                            Text(
                                text = "🔥 ${state.currentStreak}d streak · ${state.streakBadge} ${state.streakLabel}",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                    }

                    // 1-Day Streak Shield Indicator
                    if (state.streakShieldActive) {
                        Surface(
                            shape = PillShape,
                            color = Color(0xFF1E3A8A).copy(alpha = 0.85f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = "🛡️ Shield Active",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }

                Icon(
                    imageVector = Icons.Filled.ArrowForward,
                    contentDescription = "Start Focus",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(22.dp)
                )
            }

            // Dual Representation: Daily Focus Goal Progress Ring + Text Stats
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Circular Progress Ring
                M3CircularProgress(
                    progress = state.goalProgress,
                    size = 94.dp,
                    strokeWidth = 8.dp,
                    active = state.focusMinutesToday > 0,
                    color = MaterialTheme.colorScheme.onPrimary,
                    trackColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.28f)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${(state.goalProgress * 100).toInt()}%",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Text(
                            text = "of goal",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                        )
                    }
                }

                // Focus Stats Details
                Column(modifier = Modifier.weight(1f)) {
                    val focusText = if (state.focusMinutesToday >= 60) {
                        "${state.focusMinutesToday / 60}h ${state.focusMinutesToday % 60}m"
                    } else {
                        "${state.focusMinutesToday}m"
                    }
                    val goalText = if (state.dailyGoalMinutes >= 60) {
                        "${state.dailyGoalMinutes / 60}h ${state.dailyGoalMinutes % 60}m goal"
                    } else {
                        "${state.dailyGoalMinutes}m goal"
                    }

                    Text(
                        text = "$focusText / $goalText",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onPrimary
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = if (state.goalProgress >= 1f) {
                            "🎉 Daily goal achieved! Keep your momentum going."
                        } else {
                            "${(state.goalProgress * 100).toInt()}% of daily focus goal completed"
                        },
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f)
                    )
                }
            }

            // M3 Squiggly Wave Linear Progress
            M3SquigglyProgress(
                progress = state.goalProgress,
                active = state.focusMinutesToday > 0,
                color = MaterialTheme.colorScheme.onPrimary,
                trackColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.3f),
                height = 14.dp,
                strokeWidth = 3.5.dp
            )

            // Bottom Action Bar: CTA button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (state.focusMinutesToday == 0) "Ready to start your first session" else "Focus session active on device",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                )

                Surface(
                    shape = PillShape,
                    color = MaterialTheme.colorScheme.onPrimary
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = "Start focus",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ScreenTimeGlanceCard(
    state: TodayUiState,
    onCardClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .clickable { onCardClick() }
            .border(1.dp, MaterialTheme.colorScheme.surfaceVariant, CardShape),
        color = MaterialTheme.colorScheme.surface,
        shape = CardShape
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(PillShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.PhoneAndroid,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "SCREEN TIME TODAY",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 1.sp
                )

                val screenTimeText = if (state.hasUsageAccess) {
                    val minutes = (state.screenTimeTodayMs / 60000L).toInt()
                    val h = minutes / 60
                    val m = minutes % 60
                    val timeStr = if (h > 0) "${h}h ${m}m" else "${m}m"
                    val unlocksStr = if (state.unlocksToday > 0) "${state.unlocksToday} unlocks" else "Unlock tracking ready"
                    "$timeStr · $unlocksStr"
                } else {
                    "Tap to enable Usage Access · Privacy First"
                }

                Text(
                    text = screenTimeText,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Icon(
                imageVector = Icons.Filled.ArrowForward,
                contentDescription = "Details",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun MilestonesSection(
    state: TodayUiState,
    onSyllabusClick: () -> Unit,
    onExamsClick: () -> Unit
) {
    Text(
        text = "Study milestones",
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Syllabus Progress Card
        Surface(
            modifier = Modifier
                .weight(1f)
                .clip(CardShape)
                .clickable { onSyllabusClick() }
                .border(1.dp, MaterialTheme.colorScheme.surfaceVariant, CardShape),
            color = MaterialTheme.colorScheme.surface,
            shape = CardShape
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Book,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "SYLLABUS",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    text = "${state.chaptersDone} / ${state.chaptersTotal}",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface
                )

                M3SquigglyProgress(
                    progress = state.syllabusProgress,
                    active = state.chaptersDone > 0,
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    height = 12.dp,
                    strokeWidth = 3.dp
                )

                Text(
                    text = "${(state.syllabusProgress * 100).toInt()}% chapters done",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // 2. Next Exam Card
        Surface(
            modifier = Modifier
                .weight(1f)
                .clip(CardShape)
                .clickable { onExamsClick() }
                .border(1.dp, MaterialTheme.colorScheme.surfaceVariant, CardShape),
            color = MaterialTheme.colorScheme.surface,
            shape = CardShape
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.School,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "NEXT EXAM",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    text = state.nextExamTitle ?: "No exam planned",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                val daysRemainingText = when (val days = state.nextExamDaysRemaining) {
                    null -> "Add timetable"
                    0 -> "Today"
                    1 -> "Tomorrow"
                    else -> if (days < 0) "Completed" else "$days days to go"
                }

                Text(
                    text = daysRemainingText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Text(
                    text = state.nextExamFormattedDate ?: "Tap to add",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ToolsListSection(
    state: TodayUiState,
    onBlockingClick: () -> Unit,
    onLimitsClick: () -> Unit,
    onSchedulesClick: () -> Unit,
    onSyllabusClick: () -> Unit,
    onExamsClick: () -> Unit,
    onNotesClick: () -> Unit
) {
    Text(
        text = "Focus & Study Tools",
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface
    )

    data class ToolItem(
        val icon: ImageVector,
        val title: String,
        val detail: String,
        val metric: String,
        val onClick: () -> Unit
    )

    val tools = listOf(
        ToolItem(
            icon = Icons.Filled.Shield,
            title = "App & Content Blocking",
            detail = "Shorts, Reels, Spotlight & adult sites",
            metric = "Rules active",
            onClick = onBlockingClick
        ),
        ToolItem(
            icon = Icons.Filled.HourglassBottom,
            title = "Screen-time limits",
            detail = "Gentle alerts before time slips away",
            metric = "Boundaries",
            onClick = onLimitsClick
        ),
        ToolItem(
            icon = Icons.Filled.CalendarMonth,
            title = "Focus schedule",
            detail = "Repeating weekly study blocks",
            metric = "Weekly routine",
            onClick = onSchedulesClick
        ),
        ToolItem(
            icon = Icons.Filled.Book,
            title = "Syllabus tracker",
            detail = "Chapters and topics completed",
            metric = "${state.chaptersDone}/${state.chaptersTotal}",
            onClick = onSyllabusClick
        ),
        ToolItem(
            icon = Icons.Filled.School,
            title = "Exam timetable",
            detail = state.nextExamTitle ?: "Countdown timetable",
            metric = state.nextExamFormattedDate ?: "Exams",
            onClick = onExamsClick
        ),
        ToolItem(
            icon = Icons.Filled.Description,
            title = "Personal notes",
            detail = "Local Markdown & quick notes",
            metric = "${state.totalNotes} notes",
            onClick = onNotesClick
        )
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .border(1.dp, MaterialTheme.colorScheme.surfaceVariant, CardShape),
        color = MaterialTheme.colorScheme.surface,
        shape = CardShape
    ) {
        Column {
            tools.forEachIndexed { index, tool ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { tool.onClick() }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(PillShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = tool.icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(19.dp)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = tool.title,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = tool.detail,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Text(
                        text = tool.metric,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Icon(
                        imageVector = Icons.Filled.ArrowForward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(16.dp)
                    )
                }

                if (index < tools.size - 1) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        thickness = 1.dp,
                        modifier = Modifier.padding(horizontal = 14.dp)
                    )
                }
            }
        }
    }
}
