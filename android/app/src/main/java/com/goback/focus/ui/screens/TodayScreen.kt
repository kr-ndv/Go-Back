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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.*
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.goback.focus.ui.components.M3SquigglyProgress
import com.goback.focus.ui.components.ProfileAvatar
import com.goback.focus.ui.theme.CardShape
import com.goback.focus.ui.theme.PillShape
import com.goback.focus.ui.viewmodel.TodayUiState
import com.goback.focus.ui.viewmodel.TodayViewModel
import com.goback.focus.ui.viewmodel.TodayViewModelFactory

/**
 * TodayScreen — Main dashboard screen of Go Back.
 * Faithful implementation matching screenshot:
 * Header (YOUR SPACE / Go Back.), Hero Focus Card (Vivid Lime / Accent),
 * Today's progress (Syllabus progress), Your tools (Unified card list),
 * and footer (YOUR TIME. YOUR TERMS.).
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
            .padding(bottom = 100.dp), // Clearance for floating navigation dock
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // 1. Top Header & Welcome Greeting
        TopHeaderSection(
            profileName = state.profileName,
            profileImageUri = state.profileImageUri,
            onProfileClick = onNavigateToSettings
        )

        // 2. Hero Focus Card (Vivid Lime / Accent container)
        HeroFocusCard(
            state = state,
            onStartFocus = onNavigateToFocus,
            onHistoryClick = onNavigateToAnalytics
        )

        // 3. Today's Progress Section (Syllabus progress with squiggly waves)
        TodayProgressSection(
            state = state,
            onSyllabusClick = onNavigateToSyllabus
        )

        // 4. Your Tools Section (Unified card with quick-access tools)
        YourToolsSection(
            state = state,
            onSyllabusClick = onNavigateToSyllabus,
            onExamsClick = onNavigateToExams,
            onNotesClick = onNavigateToNotes,
            onUsageClick = onNavigateToAnalytics,
            onBlockingClick = onNavigateToBlocking,
            onSchedulesClick = onNavigateToSchedules,
            onUsageLimitsClick = onNavigateToUsageLimits
        )

        // 5. Calm Footer
        Text(
            text = "YOUR TIME. YOUR TERMS.",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun TopHeaderSection(
    profileName: String,
    profileImageUri: String,
    onProfileClick: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "YOUR SPACE",
                    fontSize = 11.sp,
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
            }

            // Profile Avatar Button
            Surface(
                modifier = Modifier
                    .size(44.dp)
                    .clip(PillShape)
                    .clickable { onProfileClick() },
                shape = PillShape,
                color = MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                if (profileImageUri.isNotBlank()) {
                    ProfileAvatar(
                        name = profileName,
                        imageUri = profileImageUri,
                        modifier = Modifier.fillMaxSize(),
                        contentDescription = "Open profile settings"
                    )
                } else {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.Person,
                            contentDescription = "Open profile settings",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = if (profileName.isNotBlank()) "Welcome back, $profileName." else "Welcome back.",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "A clear view of what you've done today.",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun HeroFocusCard(
    state: TodayUiState,
    onStartFocus: () -> Unit,
    onHistoryClick: () -> Unit
) {
    val containerColor = MaterialTheme.colorScheme.primary
    val contentColor = MaterialTheme.colorScheme.onPrimary

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .clickable { onStartFocus() },
        color = containerColor,
        shape = CardShape,
        shadowElevation = 4.dp
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Top Row: Timer Icon, Focus Time Today, and Arrow in circle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(PillShape)
                            .background(Color.Black.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Timer,
                            contentDescription = null,
                            tint = contentColor,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Column {
                        Text(
                            text = "FOCUS TIME TODAY",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = contentColor.copy(alpha = 0.82f),
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "${state.focusMinutesToday} min",
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Black,
                            color = contentColor
                        )
                        Text(
                            text = "Completed sessions · saved history",
                            fontSize = 12.sp,
                            color = contentColor.copy(alpha = 0.78f)
                        )
                    }
                }

                // Arrow circle button
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(PillShape)
                        .border(1.5.dp, contentColor.copy(alpha = 0.6f), PillShape)
                        .clickable { onHistoryClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.ArrowForward,
                        contentDescription = "Session history",
                        tint = contentColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Animated daily-goal progress: keeps the existing hero card layout,
            // while giving today's focus total a clear visual sense of momentum.
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "DAILY FOCUS GOAL",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        color = contentColor.copy(alpha = 0.78f)
                    )
                    Text(
                        text = "${(state.goalProgress * 100).toInt()}%",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = contentColor
                    )
                }
                M3SquigglyProgress(
                    progress = state.goalProgress,
                    modifier = Modifier.fillMaxWidth(),
                    height = 12.dp,
                    strokeWidth = 3.dp,
                    amplitude = 2.5.dp,
                    color = contentColor,
                    trackColor = Color.Black.copy(alpha = 0.20f)
                )
            }

            HorizontalDivider(
                color = contentColor.copy(alpha = 0.15f),
                thickness = 1.dp
            )

            // Bottom Row: Message and Pill Start Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Make room for one more session",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = contentColor.copy(alpha = 0.9f)
                )

                Surface(
                    shape = PillShape,
                    color = Color(0xFF10201B),
                    modifier = Modifier.clickable { onStartFocus() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = null,
                            tint = containerColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Start focus",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = containerColor
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TodayProgressSection(
    state: TodayUiState,
    onSyllabusClick: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Column {
            Text(
                text = "Today's progress",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Based on your saved syllabus progress",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Syllabus Card with Expressive Squiggly Wave Indicator
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(CardShape)
                .clickable { onSyllabusClick() },
            shape = CardShape,
            color = MaterialTheme.colorScheme.surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(PillShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Book,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Text(
                            text = "SYLLABUS",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            letterSpacing = 1.sp
                        )
                    }

                    Text(
                        text = "${(state.syllabusProgress * 100).toInt()}% complete",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Text(
                    text = "${state.chaptersDone} / ${state.chaptersTotal} chapters done",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                M3SquigglyProgress(
                    progress = state.syllabusProgress,
                    modifier = Modifier.fillMaxWidth(),
                    height = 14.dp,
                    strokeWidth = 3.5.dp,
                    amplitude = 3.5.dp,
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }
        }
    }
}

@Composable
private fun YourToolsSection(
    state: TodayUiState,
    onSyllabusClick: () -> Unit,
    onExamsClick: () -> Unit,
    onNotesClick: () -> Unit,
    onUsageClick: () -> Unit,
    onBlockingClick: () -> Unit,
    onSchedulesClick: () -> Unit = {},
    onUsageLimitsClick: () -> Unit = {}
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Column {
            Text(
                text = "Your tools",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "More details and controls below",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        data class ToolRow(
            val icon: ImageVector,
            val title: String,
            val detail: String,
            val trailing: String,
            val onClick: () -> Unit
        )

        val usageText = if (state.hasUsageAccess) {
            val minutes = (state.screenTimeTodayMs / 60000L).toInt()
            val h = minutes / 60
            val m = minutes % 60
            if (h > 0) "${h}h ${m}m today" else "${m}m today"
        } else {
            "Usage access"
        }

        val examTrailing = if (state.nextExamDaysRemaining != null) {
            "${state.nextExamDaysRemaining}d left"
        } else {
            "Add an exam date"
        }

        val toolItems = listOf(
            ToolRow(
                icon = Icons.Outlined.Book,
                title = "Syllabus",
                detail = "chapters complete",
                trailing = "${state.chaptersDone}/${state.chaptersTotal}",
                onClick = onSyllabusClick
            ),
            ToolRow(
                icon = Icons.Outlined.CalendarMonth,
                title = "Exams",
                detail = state.nextExamTitle ?: "No upcoming exam",
                trailing = examTrailing,
                onClick = onExamsClick
            ),
            ToolRow(
                icon = Icons.Outlined.Description,
                title = "Notes",
                detail = "saved notes",
                trailing = "${state.totalNotes}",
                onClick = onNotesClick
            ),
            ToolRow(
                icon = Icons.Outlined.Schedule,
                title = "Focus schedules",
                detail = "automatic focus routines",
                trailing = "Schedules",
                onClick = onSchedulesClick
            ),
            ToolRow(
                icon = Icons.Outlined.ShowChart,
                title = "Screen time",
                detail = "phone usage · today",
                trailing = usageText,
                onClick = onUsageClick
            ),
            ToolRow(
                icon = Icons.Outlined.Shield,
                title = "App blocking",
                detail = "apps and video limits",
                trailing = "Blocking rules",
                onClick = onBlockingClick
            ),
            ToolRow(
                icon = Icons.Outlined.HourglassTop,
                title = "Usage limits",
                detail = "daily app timeouts",
                trailing = "Limits",
                onClick = onUsageLimitsClick
            )
        )

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(CardShape),
            shape = CardShape,
            color = MaterialTheme.colorScheme.surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column {
                toolItems.forEachIndexed { index, tool ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { tool.onClick() }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(PillShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = tool.icon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = tool.title,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = tool.detail,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = tool.trailing,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Icon(
                                imageVector = Icons.Filled.ArrowForward,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }

                    if (index < toolItems.size - 1) {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                            thickness = 1.dp,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }
            }
        }
    }
}
