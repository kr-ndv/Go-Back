package com.goback.focus.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.RadioSquare
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Warning
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
import com.goback.focus.ui.theme.CardShape
import com.goback.focus.ui.theme.PillShape
import com.goback.focus.ui.viewmodel.AnalyticsPeriod
import com.goback.focus.ui.viewmodel.AnalyticsUiState
import com.goback.focus.ui.viewmodel.AnalyticsViewModel
import com.goback.focus.ui.viewmodel.AnalyticsViewModelFactory
import com.goback.focus.ui.viewmodel.ClassifiedAppItem
import com.goback.focus.ui.viewmodel.DailyChartPoint
import com.goback.focus.ui.viewmodel.SessionHistoryFilter
import com.goback.focus.ui.viewmodel.SessionLogItem
import com.goback.focus.ui.viewmodel.UsageCategory

/**
 * AnalyticsScreen — Deep productivity insights, Screen Time vs Focus comparison,
 * live app classification, and filterable session history.
 */
@Composable
fun AnalyticsScreen(
    onNavigateToFocus: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: AnalyticsViewModel = viewModel(
        factory = AnalyticsViewModelFactory(LocalContext.current)
    )
) {
    val state by viewModel.uiState.collectAsState()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        contentPadding = PaddingValues(bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Header & Period Selector
        item {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Column {
                    Text(
                        text = "REGAIN INSIGHTS & SCREEN TIME",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Usage Analytics",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Period Filter Chips (Today 1d, 7d, 30d)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AnalyticsPeriod.values().forEach { period ->
                        val selected = state.period == period
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp)
                                .clip(PillShape)
                                .clickable { viewModel.setPeriod(period) },
                            shape = PillShape,
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = period.label,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }

        // 2. Top KPI Metric Cards (Focus Time vs Screen Time + % Changes)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Focus Time KPI
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clip(CardShape)
                        .border(1.dp, MaterialTheme.colorScheme.surfaceVariant, CardShape),
                    color = MaterialTheme.colorScheme.surface,
                    shape = CardShape
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(PillShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Timer,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Text(
                            text = "FOCUS TIME",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            letterSpacing = 1.sp
                        )

                        val focusMins = state.focusSeconds / 60
                        val focusStr = if (focusMins >= 60) "${focusMins / 60}h ${focusMins % 60}m" else "${focusMins}m"
                        Text(
                            text = focusStr,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        val changeStr = formatPercentChange(state.focusPercentChange)
                        Text(
                            text = "$changeStr vs prev period",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if ((state.focusPercentChange ?: 0) >= 0) Color(0xFF16A34A) else Color(0xFFDC2626)
                        )
                    }
                }

                // Screen Time KPI
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clip(CardShape)
                        .border(1.dp, MaterialTheme.colorScheme.surfaceVariant, CardShape),
                    color = MaterialTheme.colorScheme.surface,
                    shape = CardShape
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(PillShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PhoneAndroid,
                                contentDescription = null,
                                tint = Color(0xFF2563EB),
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Text(
                            text = "SCREEN TIME",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            letterSpacing = 1.sp
                        )

                        val screenMins = state.screenTimeMs / 60000L
                        val screenStr = if (state.hasUsageAccess) {
                            if (screenMins >= 60) "${screenMins / 60}h ${screenMins % 60}m" else "${screenMins}m"
                        } else {
                            "Permission off"
                        }

                        Text(
                            text = screenStr,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        val screenChangeStr = if (state.hasUsageAccess) {
                            val change = formatPercentChange(state.screenPercentChange)
                            "$change vs prev period"
                        } else {
                            "Enable Usage Access"
                        }

                        Text(
                            text = screenChangeStr,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // 3. Consistency Strip: Streak & Phone Unlocks
        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(CardShape)
                    .border(1.dp, MaterialTheme.colorScheme.surfaceVariant, CardShape),
                color = MaterialTheme.colorScheme.surface,
                shape = CardShape
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp, horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "🔥 ${state.currentStreak}d",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Current streak",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    VerticalDivider(
                        modifier = Modifier.height(30.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    )

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "🏆 ${state.bestStreak}d",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Best streak",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    VerticalDivider(
                        modifier = Modifier.height(30.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    )

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "🔓 ${state.unlocksToday}",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Unlocks today",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // 4. Dual-bar Visual Comparison Chart: Focus vs Screen Time
        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(CardShape)
                    .border(1.dp, MaterialTheme.colorScheme.surfaceVariant, CardShape),
                color = MaterialTheme.colorScheme.surface,
                shape = CardShape
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Focus vs Screen Time",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        // Chart Legend
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(PillShape)
                                        .background(MaterialTheme.colorScheme.primary)
                                )
                                Text(
                                    text = "Focus",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(PillShape)
                                        .background(Color(0xFF60A5FA))
                                )
                                Text(
                                    text = "Screen",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Render Dual Bars
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        state.dailyChartPoints.forEach { point ->
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Bottom,
                                modifier = Modifier.weight(1f)
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                                    verticalAlignment = Alignment.Bottom,
                                    modifier = Modifier.height(105.dp)
                                ) {
                                    // Focus Bar
                                    val fFraction = (point.focusMinutes.toFloat() / state.maxMinutesInChart.toFloat()).coerceIn(0.04f, 1f)
                                    Box(
                                        modifier = Modifier
                                            .width(7.dp)
                                            .fillMaxHeight(fFraction)
                                            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                            .background(MaterialTheme.colorScheme.primary)
                                    )

                                    // Screen Time Bar
                                    val uFraction = (point.usageMinutes.toFloat() / state.maxMinutesInChart.toFloat()).coerceIn(0.04f, 1f)
                                    Box(
                                        modifier = Modifier
                                            .width(7.dp)
                                            .fillMaxHeight(uFraction)
                                            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                            .background(Color(0xFF60A5FA))
                                    )
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                Text(
                                    text = point.label,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        // 5. App Categorization Split Bar (Productive, Distracting, Neutral)
        if (state.hasUsageAccess) {
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(CardShape)
                        .border(1.dp, MaterialTheme.colorScheme.surfaceVariant, CardShape),
                    color = MaterialTheme.colorScheme.surface,
                    shape = CardShape
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "App Categories Breakdown",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        // Segmented Horizontal Bar
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(12.dp)
                                .clip(PillShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            if (state.productivePercent > 0) {
                                Box(
                                    modifier = Modifier
                                        .weight(state.productivePercent.toFloat().coerceAtLeast(0.1f))
                                        .fillMaxHeight()
                                        .background(Color(0xFF34D399))
                                )
                            }
                            if (state.distractingPercent > 0) {
                                Box(
                                    modifier = Modifier
                                        .weight(state.distractingPercent.toFloat().coerceAtLeast(0.1f))
                                        .fillMaxHeight()
                                        .background(Color(0xFFF87171))
                                )
                            }
                            if (state.neutralPercent > 0) {
                                Box(
                                    modifier = Modifier
                                        .weight(state.neutralPercent.toFloat().coerceAtLeast(0.1f))
                                        .fillMaxHeight()
                                        .background(Color(0xFF9CA3AF))
                                )
                            }
                        }

                        // Category Details & Durations
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            CategoryLegendRow(
                                color = Color(0xFF34D399),
                                label = "Productive",
                                timeMs = state.productiveMs,
                                percent = state.productivePercent
                            )
                            CategoryLegendRow(
                                color = Color(0xFFF87171),
                                label = "Distracting",
                                timeMs = state.distractingMs,
                                percent = state.distractingPercent
                            )
                            CategoryLegendRow(
                                color = Color(0xFF9CA3AF),
                                label = "Neutral",
                                timeMs = state.neutralMs,
                                percent = state.neutralPercent
                            )
                        }
                    }
                }
            }
        }

        // 6. Usage Access Permission Prompt if disabled
        if (!state.hasUsageAccess) {
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(CardShape)
                        .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), CardShape),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                    shape = CardShape
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Shield,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                        Text(
                            text = "Usage Access Required",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Grant Android Usage Access to track daily screen time, compute app categorization, and enforce study limits.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Button(
                            onClick = { viewModel.openUsageAccessSettings() },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            shape = PillShape
                        ) {
                            Text(text = "Open Android Settings", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // 7. Interactive App Classification List
        if (state.hasUsageAccess) {
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(CardShape)
                        .border(1.dp, MaterialTheme.colorScheme.surfaceVariant, CardShape),
                    color = MaterialTheme.colorScheme.surface,
                    shape = CardShape
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Classify Your Apps",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${state.classifiedApps.size} apps found",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // App Search Input
                        OutlinedTextField(
                            value = state.appSearchQuery,
                            onValueChange = { viewModel.setAppSearch(it) },
                            placeholder = { Text("Search apps to categorize…", fontSize = 12.sp) },
                            leadingIcon = {
                                Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                            },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = PillShape
                        )

                        // App Rows
                        Column {
                            state.classifiedApps.take(25).forEachIndexed { index, app ->
                                AppClassificationRow(
                                    app = app,
                                    onSelectCategory = { cat -> viewModel.assignCategory(app.packageName, cat) }
                                )
                                if (index < minOf(24, state.classifiedApps.lastIndex)) {
                                    HorizontalDivider(
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        thickness = 1.dp
                                    )
                                }
                            }
                            if (state.classifiedApps.isEmpty()) {
                                Text(
                                    text = "No apps recorded during this period.",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 16.dp),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        }

        // 8. Session History Filterable Log Section
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Session History",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    // History Filter (All, Focus, Breaks)
                    Row(
                        modifier = Modifier
                            .clip(PillShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                            .padding(2.dp)
                    ) {
                        SessionHistoryFilter.values().forEach { filter ->
                            val active = state.historyFilter == filter
                            Surface(
                                modifier = Modifier
                                    .clip(PillShape)
                                    .clickable { viewModel.setSessionFilter(filter) },
                                shape = PillShape,
                                color = if (active) MaterialTheme.colorScheme.primary else Color.Transparent
                            ) {
                                Text(
                                    text = filter.label,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }

                // Range Summary Banner
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(CardShape)
                        .border(1.dp, MaterialTheme.colorScheme.surfaceVariant, CardShape),
                    color = MaterialTheme.colorScheme.surface,
                    shape = CardShape
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val totalMins = state.rangeTotalFocusSeconds / 60
                        val durStr = if (totalMins >= 60) "${totalMins / 60}h ${totalMins % 60}m" else "${totalMins}m"
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = durStr, fontSize = 18.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                            Text(text = "Focus recorded", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        VerticalDivider(modifier = Modifier.height(28.dp), color = MaterialTheme.colorScheme.surfaceVariant)

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = "${state.rangeCompletedSessions}", fontSize = 18.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
                            Text(text = "Completed", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        VerticalDivider(modifier = Modifier.height(28.dp), color = MaterialTheme.colorScheme.surfaceVariant)

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = "${state.rangeTotalSessions}", fontSize = 18.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
                            Text(text = "Total sessions", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }

        // 9. Session Cards List
        if (state.filteredSessions.isEmpty()) {
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(CardShape)
                        .border(1.dp, MaterialTheme.colorScheme.surfaceVariant, CardShape),
                    color = MaterialTheme.colorScheme.surface,
                    shape = CardShape
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Timer,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                        Text(
                            text = "No sessions recorded in this period",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Start a focus timer, pomodoro, or stopwatch to log your study sessions.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(state.filteredSessions, key = { it.id }) { session ->
                SessionHistoryItemCard(session = session)
            }
        }
    }
}

@Composable
private fun CategoryLegendRow(
    color: Color,
    label: String,
    timeMs: Long,
    percent: Int
) {
    val mins = timeMs / 60000L
    val timeStr = if (mins >= 60) "${mins / 60}h ${mins % 60}m" else "${mins}m"

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(PillShape)
                    .background(color)
            )
            Text(
                text = "$label: $timeStr",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Text(
            text = "$percent%",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun AppClassificationRow(
    app: ClassifiedAppItem,
    onSelectCategory: (UsageCategory) -> Unit
) {
    val mins = app.foregroundTimeMs / 60000L
    val timeStr = if (mins >= 60) "${mins / 60}h ${mins % 60}m" else "${mins}m"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = app.appName,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "$timeStr used",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // 3 Category Assignment Buttons
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            // Productive
            CategoryButton(
                selected = app.category == UsageCategory.PRODUCTIVE,
                activeColor = Color(0xFF34D399),
                icon = Icons.Filled.CheckCircle,
                contentDescription = "Mark as Productive",
                onClick = { onSelectCategory(UsageCategory.PRODUCTIVE) }
            )

            // Distracting
            CategoryButton(
                selected = app.category == UsageCategory.DISTRACTING,
                activeColor = Color(0xFFF87171),
                icon = Icons.Filled.Warning,
                contentDescription = "Mark as Distracting",
                onClick = { onSelectCategory(UsageCategory.DISTRACTING) }
            )

            // Neutral
            CategoryButton(
                selected = app.category == UsageCategory.NEUTRAL,
                activeColor = Color(0xFF9CA3AF),
                icon = Icons.Filled.RadioSquare,
                contentDescription = "Mark as Neutral",
                onClick = { onSelectCategory(UsageCategory.NEUTRAL) }
            )
        }
    }
}

@Composable
private fun CategoryButton(
    selected: Boolean,
    activeColor: Color,
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .size(32.dp)
            .clip(PillShape)
            .clickable { onClick() },
        shape = PillShape,
        color = if (selected) activeColor else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (selected) activeColor else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
        )
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (selected) Color(0xFF081B14) else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun SessionHistoryItemCard(
    session: SessionLogItem
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .border(1.dp, MaterialTheme.colorScheme.surfaceVariant, CardShape),
        color = MaterialTheme.colorScheme.surface,
        shape = CardShape
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(PillShape)
                    .background(
                        if (session.sessionType == "focus") MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (session.sessionType == "focus") Icons.Filled.Spa else Icons.Filled.Coffee,
                    contentDescription = null,
                    tint = if (session.sessionType == "focus") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Info
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = when (session.sessionType) {
                            "long-break" -> "Long break"
                            "short-break" -> "Short break"
                            else -> "Focus"
                        },
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = session.formattedDuration,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Text(
                    text = "${session.formattedDate} · ${session.mode.replaceFirstChar { it.uppercase() }}",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (session.tag.isNotBlank()) {
                    Surface(
                        shape = PillShape,
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                    ) {
                        Text(
                            text = session.tag,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }

                if (session.notes.isNotBlank()) {
                    Text(
                        text = session.notes,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(PillShape)
                            .background(if (session.completed) Color(0xFF16A34A) else Color(0xFFD97706))
                    )
                    Text(
                        text = if (session.completed) "Completed" else "Ended early",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private fun formatPercentChange(value: Int?): String {
    return when {
        value == null -> "New"
        value == 0 -> "0%"
        value > 0 -> "+$value%"
        else -> "$value%"
    }
}
