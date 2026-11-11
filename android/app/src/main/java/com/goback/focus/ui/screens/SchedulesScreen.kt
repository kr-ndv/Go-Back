package com.goback.focus.ui.screens

import android.app.AlarmManager
import android.app.Application
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.goback.focus.GoBackApplication
import com.goback.focus.MainActivity
import com.goback.focus.data.dao.FocusScheduleDao
import com.goback.focus.data.entity.FocusScheduleEntity
import com.goback.focus.logic.AlarmScheduler
import com.goback.focus.logic.ScheduleEvaluationEngine
import com.goback.focus.receiver.FocusAlarmReceiver
import com.goback.focus.ui.theme.CardShape
import com.goback.focus.ui.theme.ChipShape
import com.goback.focus.ui.theme.DangerRed
import com.goback.focus.ui.theme.PillShape
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.*

// =============================================================================
// UI Models
// =============================================================================

data class AppItem(
    val packageName: String,
    val appName: String
)

data class UiScheduleItem(
    val id: String,
    val title: String,
    val startTime: String,
    val durationMinutes: Int,
    val days: Set<Int>,
    val daysSummary: String,
    val enabled: Boolean,
    val reminderMinutes: Int,
    val routineType: String,
    val isActiveNow: Boolean,
    val timeSpanLabel: String,
    val blockedPackages: Set<String>
)

data class SchedulesUiState(
    val schedules: List<UiScheduleItem> = emptyList(),
    val isAnyScheduleActiveNow: Boolean = false,
    val activeScheduleTitle: String? = null,
    val activeScheduleEndTime: String? = null,
    val installedApps: List<AppItem> = emptyList(),
    val isLoading: Boolean = true
)

// =============================================================================
// ViewModel
// =============================================================================

class SchedulesViewModel(application: Application) : AndroidViewModel(application) {
    private val scheduleDao: FocusScheduleDao = (application as GoBackApplication).database.focusScheduleDao()
    private val context: Context get() = getApplication()

    private val _uiState = MutableStateFlow(SchedulesUiState())
    val uiState: StateFlow<SchedulesUiState> = _uiState.asStateFlow()

    private var rawSchedules: List<FocusScheduleEntity> = emptyList()

    init {
        observeSchedules()
        loadInstalledApps()
        startEvaluationTicker()
    }

    private fun observeSchedules() {
        viewModelScope.launch {
            scheduleDao.getAll().collect { entities ->
                rawSchedules = entities
                recomputeState()
                rescheduleAlarms(entities)
            }
        }
    }

    private fun loadInstalledApps() {
        viewModelScope.launch(Dispatchers.IO) {
            val pm = context.packageManager
            val intent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val apps = pm.queryIntentActivities(intent, 0)
                .mapNotNull { resolveInfo ->
                    val pkg = resolveInfo.activityInfo.packageName
                    if (pkg != context.packageName) {
                        val name = resolveInfo.loadLabel(pm).toString()
                        AppItem(packageName = pkg, appName = name)
                    } else null
                }
                .distinctBy { it.packageName }
                .sortedBy { it.appName.lowercase(Locale.ROOT) }

            _uiState.update { it.copy(installedApps = apps) }
        }
    }

    private fun startEvaluationTicker() {
        viewModelScope.launch {
            while (isActive) {
                recomputeState()
                delay(15_000L) // Re-evaluate every 15 seconds for clock precision
            }
        }
    }

    private fun recomputeState() {
        val now = Calendar.getInstance()
        val isAnyActive = ScheduleEvaluationEngine.evaluateAndSync(context, rawSchedules)
        val activeSchedule = ScheduleEvaluationEngine.findActiveSchedule(rawSchedules, now)

        val activeEndTime = activeSchedule?.let {
            ScheduleEvaluationEngine.calculateEndTime12Hour(it.startTime, it.durationMinutes)
        }

        val prefs = context.getSharedPreferences("go_back_blocking", Context.MODE_PRIVATE)

        val uiItems = rawSchedules.map { entity ->
            val days = ScheduleEvaluationEngine.getActiveDays(entity)
            val daysSummary = ScheduleEvaluationEngine.formatDaysSummary(days, entity.routineType)
            val startTime12 = ScheduleEvaluationEngine.format12HourTime(entity.startTime)
            val endTime12 = ScheduleEvaluationEngine.calculateEndTime12Hour(entity.startTime, entity.durationMinutes)
            val timeSpan = "$startTime12 – $endTime12 · ${entity.durationMinutes} min"
            val isActiveNow = ScheduleEvaluationEngine.isScheduleActive(entity, now)

            // Read custom blocked packages for this schedule if set
            val scheduleBlocked = prefs.getStringSet("schedule_pkgs_${entity.id}", null) ?: emptySet()

            UiScheduleItem(
                id = entity.id,
                title = entity.title,
                startTime = entity.startTime,
                durationMinutes = entity.durationMinutes,
                days = days,
                daysSummary = daysSummary,
                enabled = entity.enabled == 1,
                reminderMinutes = entity.reminderMinutes,
                routineType = entity.routineType,
                isActiveNow = isActiveNow,
                timeSpanLabel = timeSpan,
                blockedPackages = scheduleBlocked
            )
        }

        _uiState.update {
            it.copy(
                schedules = uiItems,
                isAnyScheduleActiveNow = isAnyActive,
                activeScheduleTitle = activeSchedule?.title,
                activeScheduleEndTime = activeEndTime,
                isLoading = false
            )
        }
    }

    fun toggleSchedule(scheduleId: String, enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val entity = rawSchedules.firstOrNull { it.id == scheduleId } ?: return@launch
            val updated = entity.copy(enabled = if (enabled) 1 else 0)
            scheduleDao.upsert(updated)
        }
    }

    fun saveSchedule(
        id: String? = null,
        title: String,
        startTime: String,
        durationMinutes: Int,
        days: Set<Int>,
        routineType: String,
        reminderMinutes: Int,
        blockedPackages: Set<String>
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val cleanTitle = title.trim().ifEmpty { "Focus block" }
            val scheduleId = id ?: UUID.randomUUID().toString()
            val daysJson = JSONArray(days.sorted()).toString()
            val nowIso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }.format(Date())

            val entity = FocusScheduleEntity(
                id = scheduleId,
                title = cleanTitle,
                daysJson = daysJson,
                startTime = startTime,
                durationMinutes = durationMinutes,
                enabled = 1,
                createdAt = nowIso,
                reminderMinutes = reminderMinutes,
                skipDatesJson = "[]",
                routineType = routineType,
                timezone = TimeZone.getDefault().id
            )

            scheduleDao.upsert(entity)

            // Persist blocked packages selection for this specific schedule
            context.getSharedPreferences("go_back_blocking", Context.MODE_PRIVATE)
                .edit()
                .putStringSet("schedule_pkgs_$scheduleId", blockedPackages)
                .apply()
        }
    }

    fun deleteSchedule(scheduleId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val entity = rawSchedules.firstOrNull { it.id == scheduleId } ?: return@launch
            scheduleDao.delete(entity)
            AlarmScheduler.cancelSchedule(context, scheduleId)

            context.getSharedPreferences("go_back_blocking", Context.MODE_PRIVATE)
                .edit()
                .remove("schedule_pkgs_$scheduleId")
                .apply()
        }
    }

    private fun rescheduleAlarms(schedules: List<FocusScheduleEntity>) {
        viewModelScope.launch(Dispatchers.IO) {
            schedules.forEach { schedule ->
                AlarmScheduler.scheduleNextOccurrence(context, schedule)
            }
        }
    }
}

// =============================================================================
// SchedulesScreen (Compose)
// =============================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SchedulesScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SchedulesViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    var showCreateDialog by remember { mutableStateOf(false) }
    var scheduleToDelete by remember { mutableStateOf<UiScheduleItem?>(null) }

    // Inline reminder creation state matching 07-daily-habits.jpg
    var inlinePlanTitle by remember { mutableStateOf("") }
    var inlineStartTime by remember { mutableStateOf("18:00") }
    var inlineDuration by remember { mutableStateOf(25) }
    var inlineDays by remember { mutableStateOf(setOf(2, 3, 4, 5, 6)) } // Mon-Fri default

    val dayNames = listOf(
        1 to "Sun",
        2 to "Mon",
        3 to "Tue",
        4 to "Wed",
        5 to "Thu",
        6 to "Fri",
        7 to "Sat"
    )

    val daysSummary = when {
        inlineDays.size == 7 -> "Every day"
        inlineDays == setOf(2, 3, 4, 5, 6) -> "Weekdays"
        inlineDays == setOf(1, 7) -> "Weekends"
        inlineDays.isEmpty() -> "Select days"
        else -> "${inlineDays.size} days a week"
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            contentPadding = PaddingValues(bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Top Bar matching 07-daily-habits.jpg
            item(key = "top_bar") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onNavigateBack() },
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Focus schedule",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "A gentle reminder for your plan",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Surface(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { showCreateDialog = true },
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = "Refresh",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            // 2. Active Focus Block Banner if active
            if (state.isAnyScheduleActiveNow) {
                item(key = "active_banner") {
                    ActiveScheduleLiveBanner(
                        title = state.activeScheduleTitle ?: "Focus Block",
                        endTime = state.activeScheduleEndTime ?: ""
                    )
                }
            }

            // 3. Hero Card: Make time for what matters.
            item(key = "hero_card") {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Schedule,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Text(
                            text = "Make time for what matters.",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Text(
                            text = "Create a repeating weekly reminder to begin a focus session. Go Back will notify you at the chosen time; it will not start the timer or block other apps automatically.",
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }

            // 4. Create a weekly reminder Card (Inline Form)
            item(key = "create_reminder_card") {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = "Create a weekly reminder",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        // What are you planning?
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "What are you planning?",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp)),
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                TextField(
                                    value = inlinePlanTitle,
                                    onValueChange = { inlinePlanTitle = it },
                                    placeholder = {
                                        Text(
                                            text = "e.g. Evening revision",
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    },
                                    singleLine = true,
                                    colors = TextFieldDefaults.colors(
                                        focusedContainerColor = Color.Transparent,
                                        unfocusedContainerColor = Color.Transparent,
                                        focusedIndicatorColor = Color.Transparent,
                                        unfocusedIndicatorColor = Color.Transparent
                                    )
                                )
                            }
                        }

                        // Row: Start time + Session length
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Start Time
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "Start time (24-hour)",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp)),
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant
                                ) {
                                    TextField(
                                        value = inlineStartTime,
                                        onValueChange = { inlineStartTime = it },
                                        singleLine = true,
                                        colors = TextFieldDefaults.colors(
                                            focusedContainerColor = Color.Transparent,
                                            unfocusedContainerColor = Color.Transparent,
                                            focusedIndicatorColor = Color.Transparent,
                                            unfocusedIndicatorColor = Color.Transparent
                                        )
                                    )
                                }
                                Text(
                                    text = formatTo12Hour(inlineStartTime),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            // Session length
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "Session length",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp)),
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant
                                ) {
                                    TextField(
                                        value = inlineDuration.toString(),
                                        onValueChange = { str ->
                                            str.filter { it.isDigit() }.toIntOrNull()?.let {
                                                inlineDuration = it.coerceIn(5, 360)
                                            }
                                        },
                                        singleLine = true,
                                        colors = TextFieldDefaults.colors(
                                            focusedContainerColor = Color.Transparent,
                                            unfocusedContainerColor = Color.Transparent,
                                            focusedIndicatorColor = Color.Transparent,
                                            unfocusedIndicatorColor = Color.Transparent
                                        )
                                    )
                                }
                                // Preset length pills: 15m, 25m, 45m, 60m
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    listOf(15, 25, 45, 60).forEach { mins ->
                                        val isSel = inlineDuration == mins
                                        Surface(
                                            modifier = Modifier
                                                .clip(PillShape)
                                                .clickable { inlineDuration = mins },
                                            shape = PillShape,
                                            color = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                            border = BorderStroke(
                                                1.dp,
                                                if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                            )
                                        ) {
                                            Text(
                                                text = "${mins}m",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Repeat on Section
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "Repeat on",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                dayNames.forEach { (dayCode, name) ->
                                    val isDaySelected = inlineDays.contains(dayCode)
                                    Surface(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(38.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable {
                                                inlineDays = if (isDaySelected) {
                                                    inlineDays - dayCode
                                                } else {
                                                    inlineDays + dayCode
                                                }
                                            },
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (isDaySelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                        border = BorderStroke(
                                            1.dp,
                                            if (isDaySelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                        )
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = name,
                                                fontSize = 11.sp,
                                                fontWeight = if (isDaySelected) FontWeight.Bold else FontWeight.Medium,
                                                color = if (isDaySelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }
                            }

                            Text(
                                text = daysSummary,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Save weekly reminder Button
                        Button(
                            onClick = {
                                val title = inlinePlanTitle.ifBlank { "Focus Reminder" }
                                val days = if (inlineDays.isEmpty()) setOf(2, 3, 4, 5, 6) else inlineDays
                                viewModel.saveSchedule(
                                    title = title,
                                    startTime = inlineStartTime,
                                    durationMinutes = inlineDuration,
                                    days = days,
                                    routineType = "Deep Study",
                                    reminderMinutes = 5,
                                    blockedPackages = emptySet()
                                )
                                inlinePlanTitle = ""
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Schedule,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Save weekly reminder",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }

            // 5. Your schedules Section
            item(key = "schedules_header") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Your schedules",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${state.schedules.size}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 6. Empty State or Schedule list
            if (state.schedules.isEmpty()) {
                item(key = "empty_state") {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Schedule,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(36.dp)
                            )
                            Text(
                                text = "Your week, your rhythm.",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Your weekly reminders will appear here. Start with one study block and adjust it as your routine changes.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }

                item(key = "no_reminders_pill") {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Notifications,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "No reminders are active.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(state.schedules, key = { it.id }) { schedule ->
                    ScheduleCard(
                        schedule = schedule,
                        onToggleEnabled = { enabled ->
                            viewModel.toggleSchedule(schedule.id, enabled)
                        },
                        onDelete = {
                            scheduleToDelete = schedule
                        }
                    )
                }
            }

            // 7. Footer disclaimer
            item(key = "disclaimer") {
                Text(
                    text = "Reminders use your device's local time and Android notification settings. Battery restrictions, OS updates or notification permission changes may affect delivery. Schedules and reminder configuration stay on this device.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }
    }

    // Create / Edit Schedule Dialog
    if (showCreateDialog) {
        CreateScheduleDialog(
            installedApps = state.installedApps,
            onDismiss = { showCreateDialog = false },
            onSave = { title, startTime, duration, days, routineType, reminder, blockedPkgs ->
                viewModel.saveSchedule(
                    title = title,
                    startTime = startTime,
                    durationMinutes = duration,
                    days = days,
                    routineType = routineType,
                    reminderMinutes = reminder,
                    blockedPackages = blockedPkgs
                )
                showCreateDialog = false
            }
        )
    }

    // Delete Confirmation Dialog
    scheduleToDelete?.let { schedule ->
        AlertDialog(
            onDismissRequest = { scheduleToDelete = null },
            title = { Text("Delete focus schedule?", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to delete \"${schedule.title}\"? Scheduled alarms and automated blocking for this routine will be canceled.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteSchedule(schedule.id)
                        scheduleToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DangerRed)
                ) {
                    Text("Delete", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { scheduleToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

// =============================================================================
// ScheduleCard
// =============================================================================

@Composable
fun ScheduleCard(
    schedule: UiScheduleItem,
    onToggleEnabled: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = CardShape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = if (schedule.isActiveNow) 2.dp else 1.dp,
            color = if (schedule.isActiveNow) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (schedule.isActiveNow) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (schedule.isActiveNow) Icons.Default.PlayArrow else Icons.Default.Schedule,
                            contentDescription = null,
                            tint = if (schedule.isActiveNow) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = schedule.title,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (schedule.isActiveNow) {
                                Surface(
                                    shape = PillShape,
                                    color = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ) {
                                    Text(
                                        text = "ACTIVE NOW",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Black,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        Text(
                            text = schedule.timeSpanLabel,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Switch(
                    checked = schedule.enabled,
                    onCheckedChange = onToggleEnabled
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Repeat,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = schedule.daysSummary,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (schedule.reminderMinutes > 0) {
                        Text(text = "·", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Icon(
                            imageVector = Icons.Outlined.Notifications,
                            contentDescription = null,
                            modifier = Modifier.size(13.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${schedule.reminderMinutes}m early",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (schedule.blockedPackages.isNotEmpty()) {
                        Text(text = "·", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = "${schedule.blockedPackages.size} apps blocked",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = "Delete routine",
                        tint = DangerRed.copy(alpha = 0.8f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

// =============================================================================
// ActiveScheduleLiveBanner
// =============================================================================

@Composable
fun ActiveScheduleLiveBanner(title: String, endTime: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = CardShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(22.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                    Text(
                        text = "SCHEDULED BLOCKING ACTIVE",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = if (endTime.isNotEmpty()) "Distractions blocked until $endTime" else "Distraction apps blocked",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                )
            }
        }
    }
}

// =============================================================================
// CreateScheduleDialog
// =============================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateScheduleDialog(
    installedApps: List<AppItem>,
    onDismiss: () -> Unit,
    onSave: (
        title: String,
        startTime: String,
        durationMinutes: Int,
        days: Set<Int>,
        routineType: String,
        reminderMinutes: Int,
        blockedPackages: Set<String>
    ) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var startHour by remember { mutableIntStateOf(18) }
    var startMinute by remember { mutableIntStateOf(0) }
    var durationMinutes by remember { mutableIntStateOf(25) }
    var selectedDays by remember { mutableStateOf(setOf(1, 2, 3, 4, 5)) }
    var routineType by remember { mutableStateOf("weekday") }
    var reminderMinutes by remember { mutableIntStateOf(0) }
    var selectedPackages by remember { mutableStateOf(emptySet<String>()) }

    var showAppPicker by remember { mutableStateOf(false) }

    val durationPresets = listOf(15, 25, 45, 60, 90)
    val reminderOptions = listOf(0 to "At start", 5 to "5m early", 10 to "10m early", 15 to "15m early", 30 to "30m early")

    val formattedStartTime24 = String.format(Locale.US, "%02d:%02d", startHour, startMinute)
    val formattedTime12 = ScheduleEvaluationEngine.format12HourTime(formattedStartTime24)

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(22.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "New Focus Schedule",
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                // Title Input
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("What are you planning?") },
                    placeholder = { Text("e.g. Evening revision, Deep work") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                )

                // Start Time Selector
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Start Time: $formattedTime12 ($formattedStartTime24)",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            IconButton(onClick = { startHour = if (startHour > 0) startHour - 1 else 23 }) {
                                Icon(Icons.Default.Remove, contentDescription = "Hour down")
                            }
                            Text(
                                text = String.format(Locale.US, "%02d", startHour),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            IconButton(onClick = { startHour = (startHour + 1) % 24 }) {
                                Icon(Icons.Default.Add, contentDescription = "Hour up")
                            }
                            Text("hrs", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            IconButton(onClick = { startMinute = if (startMinute >= 5) startMinute - 5 else 55 }) {
                                Icon(Icons.Default.Remove, contentDescription = "Minute down")
                            }
                            Text(
                                text = String.format(Locale.US, "%02d", startMinute),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            IconButton(onClick = { startMinute = (startMinute + 5) % 60 }) {
                                Icon(Icons.Default.Add, contentDescription = "Minute up")
                            }
                            Text("min", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                // Session Length
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Session length: $durationMinutes min",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        durationPresets.forEach { minutes ->
                            FilterChip(
                                selected = durationMinutes == minutes,
                                onClick = { durationMinutes = minutes },
                                label = { Text("${minutes}m", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                shape = PillShape
                            )
                        }
                    }
                }

                // Repeat Routine Selection
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Repeat on",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("weekday" to "Weekdays", "weekend" to "Weekends", "all" to "Every day", "custom" to "Custom").forEach { (type, label) ->
                            FilterChip(
                                selected = routineType == type,
                                onClick = {
                                    routineType = type
                                    when (type) {
                                        "weekday" -> selectedDays = setOf(1, 2, 3, 4, 5)
                                        "weekend" -> selectedDays = setOf(0, 6)
                                        "all" -> selectedDays = setOf(0, 1, 2, 3, 4, 5, 6)
                                    }
                                },
                                label = { Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                                shape = PillShape
                            )
                        }
                    }

                    // Individual Day Toggle Chips
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        ScheduleEvaluationEngine.WEEKDAY_LABELS.forEachIndexed { index, label ->
                            val isSelected = selectedDays.contains(index)
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.surfaceVariant
                                    )
                                    .clickable {
                                        routineType = "custom"
                                        selectedDays = if (isSelected) selectedDays - index else selectedDays + index
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label.take(1),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                // Reminder Selection
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Pre-session alert",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        reminderOptions.take(3).forEach { (m, label) ->
                            FilterChip(
                                selected = reminderMinutes == m,
                                onClick = { reminderMinutes = m },
                                label = { Text(label, fontSize = 10.sp) },
                                shape = PillShape
                            )
                        }
                    }
                }

                // Blocked Apps Selector Button
                OutlinedButton(
                    onClick = { showAppPicker = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Block, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (selectedPackages.isEmpty()) "Block standard distraction apps" else "${selectedPackages.size} custom apps blocked",
                        fontSize = 12.sp
                    )
                }

                // Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (selectedDays.isEmpty()) selectedDays = setOf(1, 2, 3, 4, 5)
                            onSave(
                                title.ifBlank { "Focus block" },
                                formattedStartTime24,
                                durationMinutes,
                                selectedDays,
                                routineType,
                                reminderMinutes,
                                selectedPackages
                            )
                        },
                        shape = PillShape
                    ) {
                        Text("Save routine", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    if (showAppPicker) {
        AppSelectionDialog(
            installedApps = installedApps,
            selectedPackages = selectedPackages,
            onDismiss = { showAppPicker = false },
            onConfirm = { pkgs ->
                selectedPackages = pkgs
                showAppPicker = false
            }
        )
    }
}

// =============================================================================
// AppSelectionDialog
// =============================================================================

@Composable
fun AppSelectionDialog(
    installedApps: List<AppItem>,
    selectedPackages: Set<String>,
    onDismiss: () -> Unit,
    onConfirm: (Set<String>) -> Unit
) {
    var tempSelection by remember { mutableStateOf(selectedPackages) }
    var searchQuery by remember { mutableStateOf("") }

    val filtered = remember(installedApps, searchQuery) {
        if (searchQuery.isBlank()) installedApps
        else installedApps.filter { it.appName.contains(searchQuery, ignoreCase = true) }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.8f),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Select Blocked Apps",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search apps...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(filtered, key = { it.packageName }) { app ->
                        val isChecked = tempSelection.contains(app.packageName)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    tempSelection = if (isChecked) tempSelection - app.packageName else tempSelection + app.packageName
                                }
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = { checked ->
                                    tempSelection = if (checked) tempSelection + app.packageName else tempSelection - app.packageName
                                }
                            )
                            Text(
                                text = app.appName,
                                fontSize = 14.sp,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { tempSelection = emptySet() }) {
                        Text("Clear all")
                    }
                    Row {
                        TextButton(onClick = onDismiss) { Text("Cancel") }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(onClick = { onConfirm(tempSelection) }, shape = PillShape) {
                            Text("Done (${tempSelection.size})")
                        }
                    }
                }
            }
        }
    }
}

private fun formatTo12Hour(time24: String): String {
    return try {
        val parts = time24.split(":")
        val h = parts[0].toInt()
        val m = parts[1].toInt()
        val amPm = if (h >= 12) "PM" else "AM"
        val h12 = when (h % 12) {
            0 -> 12
            else -> h % 12
        }
        String.format("%d:%02d %s", h12, m, amPm)
    } catch (_: Exception) {
        time24
    }
}
