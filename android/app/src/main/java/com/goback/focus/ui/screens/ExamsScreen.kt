package com.goback.focus.ui.screens

import android.app.AlarmManager
import android.app.Application
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.goback.focus.GoBackApplication
import com.goback.focus.data.dao.ExamDao
import com.goback.focus.data.dao.SubjectDao
import com.goback.focus.data.entity.ExamEntity
import com.goback.focus.logic.AlarmScheduler
import com.goback.focus.receiver.FocusAlarmReceiver
import com.goback.focus.ui.theme.CardShape
import com.goback.focus.ui.theme.ChipShape
import com.goback.focus.ui.theme.DangerRed
import com.goback.focus.ui.theme.PillShape
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.ceil

// =============================================================================
// UI Models & Helpers
// =============================================================================

data class UiExamItem(
    val id: String,
    val title: String,
    val rawDate: String, // "YYYY-MM-DD"
    val details: String,
    val daysRemaining: Int, // < 0: done, 0: today, 1: tomorrow, > 1: X days
    val dayNumber: String,
    val monthAbbrev: String,
    val formattedDateFull: String,
    val countdownPillText: String,
    val isDone: Boolean
)

data class ExamsUiState(
    val nextUpcomingExam: UiExamItem? = null,
    val upcomingExams: List<UiExamItem> = emptyList(),
    val pastExams: List<UiExamItem> = emptyList(),
    val syllabusSubjects: List<String> = emptyList(),
    val isLoading: Boolean = true
)

object ExamDateUtils {
    private val isoFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val fullDateFormat = SimpleDateFormat("EEE, d MMM yyyy", Locale.getDefault())
    private val monthFormat = SimpleDateFormat("MMM", Locale.getDefault())

    fun parseDaysRemaining(rawDateStr: String): Int {
        return try {
            val date = isoFormat.parse(rawDateStr.trim()) ?: return 0
            val todayStart = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis

            val diffMs = date.time - todayStart
            ceil(diffMs.toDouble() / 86400000.0).toInt()
        } catch (_: Exception) {
            0
        }
    }

    fun formatExamDate(rawDateStr: String): Triple<String, String, String> {
        return try {
            val date = isoFormat.parse(rawDateStr.trim()) ?: return Triple("", "", rawDateStr)
            val cal = Calendar.getInstance().apply { time = date }
            val dayNum = cal.get(Calendar.DAY_OF_MONTH).toString()
            val month = monthFormat.format(date).uppercase(Locale.getDefault())
            val full = fullDateFormat.format(date)
            Triple(dayNum, month, full)
        } catch (_: Exception) {
            Triple("", "", rawDateStr)
        }
    }
}

// =============================================================================
// ViewModel
// =============================================================================

class ExamsViewModel(application: Application) : AndroidViewModel(application) {
    private val examDao: ExamDao = (application as GoBackApplication).database.examDao()
    private val subjectDao: SubjectDao = (application as GoBackApplication).database.subjectDao()
    private val context: Context get() = getApplication()

    private val _uiState = MutableStateFlow(ExamsUiState())
    val uiState: StateFlow<ExamsUiState> = _uiState.asStateFlow()

    private var rawExams: List<ExamEntity> = emptyList()

    init {
        observeData()
    }

    private fun observeData() {
        viewModelScope.launch {
            combine(
                examDao.getAll(),
                subjectDao.getAll()
            ) { exams, subjects ->
                rawExams = exams
                val subjectNames = subjects.map { it.name }.distinct().sorted()

                val mappedExams = exams.map { entity ->
                    val daysRemaining = ExamDateUtils.parseDaysRemaining(entity.examDate)
                    val (dayNum, month, fullDate) = ExamDateUtils.formatExamDate(entity.examDate)
                    val pillText = when {
                        daysRemaining < 0 -> "Done"
                        daysRemaining == 0 -> "Today"
                        daysRemaining == 1 -> "Tomorrow"
                        else -> "${daysRemaining}d"
                    }
                    UiExamItem(
                        id = entity.id,
                        title = entity.title,
                        rawDate = entity.examDate,
                        details = entity.details,
                        daysRemaining = daysRemaining,
                        dayNumber = dayNum,
                        monthAbbrev = month,
                        formattedDateFull = fullDate,
                        countdownPillText = pillText,
                        isDone = daysRemaining < 0
                    )
                }

                // Partition into upcoming vs past
                val upcoming = mappedExams.filter { !it.isDone }.sortedBy { it.daysRemaining }
                val past = mappedExams.filter { it.isDone }.sortedByDescending { it.rawDate }
                val nextUpcoming = upcoming.firstOrNull()

                ExamsUiState(
                    nextUpcomingExam = nextUpcoming,
                    upcomingExams = upcoming,
                    pastExams = past,
                    syllabusSubjects = subjectNames,
                    isLoading = false
                )
            }.collect { newState ->
                _uiState.value = newState
                rescheduleAllExamReminders(rawExams)
            }
        }
    }

    fun addExam(title: String, date: String, details: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val cleanTitle = title.trim().ifEmpty { "Upcoming Exam" }
            val cleanDate = date.trim()
            val cleanDetails = details.trim()

            val entity = ExamEntity(
                id = UUID.randomUUID().toString(),
                title = cleanTitle,
                examDate = cleanDate,
                details = cleanDetails
            )
            examDao.upsert(entity)
        }
    }

    fun deleteExam(examId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val entity = rawExams.firstOrNull { it.id == examId } ?: return@launch
            examDao.delete(entity)
            cancelExamReminders(entity.id)
        }
    }

    // =========================================================================
    // 3-Stage Exam Reminder Scheduler via AlarmManager
    // =========================================================================

    /**
     * Schedules 3 automatic notifications per exam:
     * 1) 7 days prior at 9:00 AM
     * 2) 1 day prior at 9:00 AM
     * 3) Morning of exam at 7:00 AM
     * All targeted at GoBackApplication.CHANNEL_EXAMS.
     */
    private fun rescheduleAllExamReminders(exams: List<ExamEntity>) {
        viewModelScope.launch(Dispatchers.IO) {
            AlarmScheduler.rescheduleAllExams(context)
        }
    }

    private fun cancelExamReminders(examId: String) {
        AlarmScheduler.cancelExam(context, examId)
    }
}

// =============================================================================
// ExamsScreen (Compose)
// =============================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExamsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ExamsViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    var showAddDialog by remember { mutableStateOf(false) }
    var examToDelete by remember { mutableStateOf<UiExamItem?>(null) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Exam planner",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Keep the date in sight",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddDialog = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Add Exam", fontWeight = FontWeight.Bold) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 18.dp),
            contentPadding = PaddingValues(top = 10.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Hero Card: UP NEXT
            state.nextUpcomingExam?.let { nextExam ->
                item(key = "up_next_hero") {
                    UpNextHeroCard(exam = nextExam)
                }
            } ?: item(key = "no_upcoming_hero") {
                NoUpcomingHeroCard(onAddClick = { showAddDialog = true })
            }

            // 2. Upcoming Exams Section
            if (state.upcomingExams.isNotEmpty()) {
                item(key = "upcoming_header") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Upcoming timetable",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${state.upcomingExams.size} scheduled",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                items(state.upcomingExams, key = { it.id }) { exam ->
                    ExamCard(
                        exam = exam,
                        onDelete = { examToDelete = exam }
                    )
                }
            }

            // 3. Past Exams Section
            if (state.pastExams.isNotEmpty()) {
                item(key = "past_header") {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Completed exams",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                items(state.pastExams, key = { it.id }) { exam ->
                    ExamCard(
                        exam = exam,
                        onDelete = { examToDelete = exam }
                    )
                }
            }
        }
    }

    // Add Exam Dialog
    if (showAddDialog) {
        AddExamDialog(
            syllabusSubjects = state.syllabusSubjects,
            onDismiss = { showAddDialog = false },
            onSave = { title, date, details ->
                viewModel.addExam(title, date, details)
                showAddDialog = false
            }
        )
    }

    // Delete Confirmation Dialog
    examToDelete?.let { exam ->
        AlertDialog(
            onDismissRequest = { examToDelete = null },
            title = { Text("Remove exam?", fontWeight = FontWeight.Bold) },
            text = { Text("Remove \"${exam.title}\" from your timetable? Scheduled countdowns and reminders will be canceled.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteExam(exam.id)
                        examToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DangerRed)
                ) {
                    Text("Remove", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { examToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

// =============================================================================
// Hero Cards
// =============================================================================

@Composable
fun UpNextHeroCard(exam: UiExamItem) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = CardShape,
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                shape = PillShape,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.2f),
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Text(
                    text = "UP NEXT",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.4.sp,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }

            Text(
                text = exam.title,
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val displayCount = if (exam.daysRemaining == 0) "TODAY" else exam.daysRemaining.toString()
                Text(
                    text = displayCount,
                    fontSize = if (exam.daysRemaining == 0) 36.sp else 50.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onPrimary,
                    lineHeight = 44.sp
                )
                if (exam.daysRemaining > 0) {
                    Text(
                        text = if (exam.daysRemaining == 1) "DAY TO GO" else "DAYS TO GO",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.2.sp,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }
            }

            Text(
                text = "${exam.formattedDateFull}${if (exam.details.isNotEmpty()) " · " + exam.details else ""}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f)
            )
        }
    }
}

@Composable
fun NoUpcomingHeroCard(onAddClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = CardShape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier
                .padding(26.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Event,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
            Text(
                text = "Your next milestone starts here.",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Add your exam dates to see countdowns, study milestones, and receive 7-day and morning reminders.",
                fontSize = 12.sp,
                lineHeight = 17.sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Button(
                onClick = onAddClick,
                shape = PillShape
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Add first exam", fontWeight = FontWeight.Bold)
            }
        }
    }
}

// =============================================================================
// ExamCard
// =============================================================================

@Composable
fun ExamCard(
    exam: UiExamItem,
    onDelete: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = CardShape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Calendar Date Box
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        if (exam.isDone) MaterialTheme.colorScheme.surfaceVariant
                        else MaterialTheme.colorScheme.primaryContainer
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = exam.dayNumber,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black,
                        color = if (exam.isDone) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = exam.monthAbbrev,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (exam.isDone) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Title & Details
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = exam.title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (exam.isDone) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${exam.formattedDateFull}${if (exam.details.isNotEmpty()) " · " + exam.details else ""}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Countdown Pill
            Surface(
                shape = PillShape,
                color = when {
                    exam.isDone -> MaterialTheme.colorScheme.surfaceVariant
                    exam.daysRemaining <= 1 -> DangerRed.copy(alpha = 0.15f)
                    else -> MaterialTheme.colorScheme.primaryContainer
                },
                contentColor = when {
                    exam.isDone -> MaterialTheme.colorScheme.onSurfaceVariant
                    exam.daysRemaining <= 1 -> DangerRed
                    else -> MaterialTheme.colorScheme.primary
                }
            ) {
                Text(
                    text = exam.countdownPillText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            // Options / Delete
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = "Delete exam",
                    tint = DangerRed.copy(alpha = 0.75f),
                    modifier = Modifier.size(17.dp)
                )
            }
        }
    }
}

// =============================================================================
// AddExamDialog
// =============================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddExamDialog(
    syllabusSubjects: List<String>,
    onDismiss: () -> Unit,
    onSave: (title: String, date: String, details: String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var details by remember { mutableStateOf("") }

    // Date state (default: 7 days from now)
    val defaultCal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 7) }
    var year by remember { mutableIntStateOf(defaultCal.get(Calendar.YEAR)) }
    var month by remember { mutableIntStateOf(defaultCal.get(Calendar.MONTH) + 1) } // 1-12
    var day by remember { mutableIntStateOf(defaultCal.get(Calendar.DAY_OF_MONTH)) }

    val formattedDateIso = String.format(Locale.US, "%04d-%02d-%02d", year, month, day)

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
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Add Exam to Timetable",
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                // Subject / Title input
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Subject or exam title") },
                    placeholder = { Text("e.g. Mathematics, Calculus") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                )

                // Quick Subject Chips from Syllabus
                if (syllabusSubjects.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Or choose from your syllabus:",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(syllabusSubjects) { subject ->
                                FilterChip(
                                    selected = title == subject,
                                    onClick = { title = subject },
                                    label = { Text(subject, fontSize = 11.sp) },
                                    shape = PillShape
                                )
                            }
                        }
                    }
                }

                // Date Selector (Year, Month, Day)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Exam date: $formattedDateIso",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Day Picker
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { if (day > 1) day-- }) {
                                Icon(Icons.Default.Remove, contentDescription = "Day -")
                            }
                            Text(
                                text = String.format(Locale.US, "%02d", day),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            IconButton(onClick = { if (day < 31) day++ }) {
                                Icon(Icons.Default.Add, contentDescription = "Day +")
                            }
                            Text("Day", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        // Month Picker
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { if (month > 1) month-- }) {
                                Icon(Icons.Default.Remove, contentDescription = "Month -")
                            }
                            Text(
                                text = String.format(Locale.US, "%02d", month),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            IconButton(onClick = { if (month < 12) month++ }) {
                                Icon(Icons.Default.Add, contentDescription = "Month +")
                            }
                            Text("Mo", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        // Year Picker
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { year-- }) {
                                Icon(Icons.Default.Remove, contentDescription = "Year -")
                            }
                            Text(
                                text = year.toString(),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            IconButton(onClick = { year++ }) {
                                Icon(Icons.Default.Add, contentDescription = "Year +")
                            }
                        }
                    }
                }

                // Optional Details
                OutlinedTextField(
                    value = details,
                    onValueChange = { details = it },
                    label = { Text("Details (optional)") },
                    placeholder = { Text("e.g. 10:00 AM · Hall B · Chapters 1-4") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                )

                // Info banner on 3-stage reminders
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Notifications,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Automatic reminders will alert you 7 days before, 1 day before, and on the morning of the exam.",
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Action Buttons
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
                            if (title.isNotBlank()) {
                                onSave(title, formattedDateIso, details)
                            }
                        },
                        enabled = title.isNotBlank(),
                        shape = PillShape
                    ) {
                        Text("Add to timetable", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
