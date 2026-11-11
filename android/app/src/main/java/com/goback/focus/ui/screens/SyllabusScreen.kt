package com.goback.focus.ui.screens

import android.app.Application
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
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.goback.focus.GoBackApplication
import com.goback.focus.data.dao.SubjectDao
import com.goback.focus.data.entity.SubjectEntity
import com.goback.focus.ui.components.M3SquigglyProgress
import com.goback.focus.ui.theme.CardShape
import com.goback.focus.ui.theme.DangerRed
import com.goback.focus.ui.theme.PillShape
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

// =============================================================================
// UI Models & JSON Helpers
// =============================================================================

data class UiChapter(
    val id: String,
    val title: String,
    val done: Boolean
)

data class UiSubject(
    val id: String,
    val name: String,
    val chapters: List<UiChapter>,
    val totalChapters: Int,
    val doneChapters: Int,
    val progressFraction: Float,
    val progressPercent: Int
)

data class SyllabusUiState(
    val subjects: List<UiSubject> = emptyList(),
    val totalChapters: Int = 0,
    val doneChapters: Int = 0,
    val overallProgressFraction: Float = 0f,
    val overallProgressPercent: Int = 0,
    val isLoading: Boolean = true
)

object SyllabusJsonUtils {
    fun parseChapters(json: String): List<UiChapter> {
        val list = mutableListOf<UiChapter>()
        try {
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val id = obj.optString("id", UUID.randomUUID().toString())
                val title = obj.optString("title", "Chapter")
                val done = obj.optBoolean("done", false)
                list.add(UiChapter(id = id, title = title, done = done))
            }
        } catch (_: Exception) {}
        return list
    }

    fun serializeChapters(chapters: List<UiChapter>): String {
        val array = JSONArray()
        for (ch in chapters) {
            val obj = JSONObject().apply {
                put("id", ch.id)
                put("title", ch.title)
                put("done", ch.done)
            }
            array.put(obj)
        }
        return array.toString()
    }
}

// =============================================================================
// ViewModel
// =============================================================================

class SyllabusViewModel(application: Application) : AndroidViewModel(application) {
    private val subjectDao: SubjectDao = (application as GoBackApplication).database.subjectDao()

    private val _uiState = MutableStateFlow(SyllabusUiState())
    val uiState: StateFlow<SyllabusUiState> = _uiState.asStateFlow()

    init {
        observeSubjects()
    }

    private fun observeSubjects() {
        viewModelScope.launch {
            subjectDao.getAll().collect { entities ->
                var overallTotal = 0
                var overallDone = 0

                val uiSubjects = entities.map { entity ->
                    val chapters = SyllabusJsonUtils.parseChapters(entity.chaptersJson)
                    val total = chapters.size
                    val done = chapters.count { it.done }
                    val fraction = if (total > 0) done.toFloat() / total.toFloat() else 0f
                    val percent = if (total > 0) (fraction * 100).toInt() else 0

                    overallTotal += total
                    overallDone += done

                    UiSubject(
                        id = entity.id,
                        name = entity.name,
                        chapters = chapters,
                        totalChapters = total,
                        doneChapters = done,
                        progressFraction = fraction,
                        progressPercent = percent
                    )
                }

                val overallFraction = if (overallTotal > 0) overallDone.toFloat() / overallTotal.toFloat() else 0f
                val overallPercent = if (overallTotal > 0) (overallFraction * 100).toInt() else 0

                _uiState.value = SyllabusUiState(
                    subjects = uiSubjects,
                    totalChapters = overallTotal,
                    doneChapters = overallDone,
                    overallProgressFraction = overallFraction,
                    overallProgressPercent = overallPercent,
                    isLoading = false
                )
            }
        }
    }

    fun addSubject(name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val clean = name.trim().ifEmpty { "New Subject" }
            val nowIso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }.format(Date())

            val entity = SubjectEntity(
                id = UUID.randomUUID().toString(),
                name = clean,
                chaptersJson = "[]",
                updatedAt = nowIso
            )
            subjectDao.upsert(entity)
        }
    }

    fun renameSubject(subjectId: String, newName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val clean = newName.trim().ifEmpty { return@launch }
            val nowIso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }.format(Date())
            subjectDao.rename(subjectId, clean, nowIso)
        }
    }

    fun deleteSubject(subject: UiSubject) {
        viewModelScope.launch(Dispatchers.IO) {
            val entity = SubjectEntity(
                id = subject.id,
                name = subject.name,
                chaptersJson = SyllabusJsonUtils.serializeChapters(subject.chapters),
                updatedAt = ""
            )
            subjectDao.delete(entity)
        }
    }

    fun addChapter(subjectId: String, title: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val clean = title.trim().ifEmpty { return@launch }
            val current = _uiState.value.subjects.firstOrNull { it.id == subjectId } ?: return@launch
            val updatedChapters = current.chapters + UiChapter(
                id = UUID.randomUUID().toString(),
                title = clean,
                done = false
            )
            saveSubjectChapters(subjectId, current.name, updatedChapters)
        }
    }

    fun toggleChapterDone(subjectId: String, chapterId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val current = _uiState.value.subjects.firstOrNull { it.id == subjectId } ?: return@launch
            val updatedChapters = current.chapters.map { ch ->
                if (ch.id == chapterId) ch.copy(done = !ch.done) else ch
            }
            saveSubjectChapters(subjectId, current.name, updatedChapters)
        }
    }

    fun editChapter(subjectId: String, chapterId: String, newTitle: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val clean = newTitle.trim().ifEmpty { return@launch }
            val current = _uiState.value.subjects.firstOrNull { it.id == subjectId } ?: return@launch
            val updatedChapters = current.chapters.map { ch ->
                if (ch.id == chapterId) ch.copy(title = clean) else ch
            }
            saveSubjectChapters(subjectId, current.name, updatedChapters)
        }
    }

    fun deleteChapter(subjectId: String, chapterId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val current = _uiState.value.subjects.firstOrNull { it.id == subjectId } ?: return@launch
            val updatedChapters = current.chapters.filter { it.id != chapterId }
            saveSubjectChapters(subjectId, current.name, updatedChapters)
        }
    }

    private suspend fun saveSubjectChapters(subjectId: String, name: String, chapters: List<UiChapter>) {
        val json = SyllabusJsonUtils.serializeChapters(chapters)
        val nowIso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date())

        val entity = SubjectEntity(
            id = subjectId,
            name = name,
            chaptersJson = json,
            updatedAt = nowIso
        )
        subjectDao.upsert(entity)
    }
}

// =============================================================================
// SyllabusScreen (Compose)
// =============================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyllabusScreen(
    onNavigateBack: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: SyllabusViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    var showAddSubjectDialog by remember { mutableStateOf(false) }
    var subjectToRename by remember { mutableStateOf<UiSubject?>(null) }
    var subjectToDelete by remember { mutableStateOf<UiSubject?>(null) }
    var chapterToEdit by remember { mutableStateOf<Pair<UiSubject, UiChapter>?>(null) }

    var newSubjectTitle by remember { mutableStateOf("") }

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
            // 1. Top Bar matching 08-exam-planner.jpg (Syllabus tracker)
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

                    Text(
                        text = "Syllabus tracker",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.size(40.dp))
                }
            }

            // 2. Eyebrow
            item(key = "eyebrow") {
                Text(
                    text = "EVERY CHAPTER COUNTS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.3.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 3. Overall Progress Card
            item(key = "overall_progress") {
                OverallProgressCard(
                    doneChapters = state.doneChapters,
                    totalChapters = state.totalChapters,
                    progressFraction = state.overallProgressFraction,
                    progressPercent = state.overallProgressPercent
                )
            }

            // 4. Inline Add Subject Input Row
            item(key = "add_subject_row") {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp)),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextField(
                            value = newSubjectTitle,
                            onValueChange = { newSubjectTitle = it },
                            placeholder = {
                                Text(
                                    text = "Add a subject...",
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
                            ),
                            modifier = Modifier.weight(1f)
                        )

                        IconButton(
                            onClick = {
                                val clean = newSubjectTitle.trim()
                                if (clean.isNotEmpty()) {
                                    viewModel.addSubject(clean)
                                    newSubjectTitle = ""
                                }
                            },
                            enabled = newSubjectTitle.isNotBlank(),
                            modifier = Modifier
                                .size(46.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    if (newSubjectTitle.isNotBlank())
                                        MaterialTheme.colorScheme.primary
                                    else
                                        MaterialTheme.colorScheme.surfaceVariant
                                )
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = "Add subject",
                                tint = if (newSubjectTitle.isNotBlank())
                                    MaterialTheme.colorScheme.onPrimary
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // 5. Empty State or Subjects List
            if (state.subjects.isEmpty() && !state.isLoading) {
                item(key = "empty_state") {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(28.dp)
                                .fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Book,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(36.dp)
                            )
                            Text(
                                text = "Build your syllabus checklist.",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Add a subject, then add chapters. Your percentage updates automatically as you tick them off.",
                                fontSize = 12.sp,
                                lineHeight = 17.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                item(key = "footer_tip") {
                    Text(
                        text = "Tip: use the subject menu to rename or delete a subject. Edit a chapter with the pencil, or remove it with the delete icon. Everything stays on this device.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                    )
                }
            } else {
                items(state.subjects, key = { it.id }) { subject ->
                    SubjectCard(
                        subject = subject,
                        onToggleChapter = { chId -> viewModel.toggleChapterDone(subject.id, chId) },
                        onAddChapter = { title -> viewModel.addChapter(subject.id, title) },
                        onEditChapter = { ch -> chapterToEdit = Pair(subject, ch) },
                        onDeleteChapter = { chId -> viewModel.deleteChapter(subject.id, chId) },
                        onRenameSubject = { subjectToRename = subject },
                        onDeleteSubject = { subjectToDelete = subject }
                    )
                }
            }
        }
    }

    // Add Subject Dialog
    if (showAddSubjectDialog) {
        var subjectName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddSubjectDialog = false },
            title = { Text("Add New Subject", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = subjectName,
                    onValueChange = { subjectName = it },
                    label = { Text("Subject name") },
                    placeholder = { Text("e.g. Calculus, Physics, History") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (subjectName.isNotBlank()) {
                            viewModel.addSubject(subjectName)
                            showAddSubjectDialog = false
                        }
                    },
                    enabled = subjectName.isNotBlank(),
                    shape = PillShape
                ) {
                    Text("Add", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddSubjectDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Rename Subject Dialog
    subjectToRename?.let { subject ->
        var renameText by remember { mutableStateOf(subject.name) }
        AlertDialog(
            onDismissRequest = { subjectToRename = null },
            title = { Text("Rename Subject", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text("Subject name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (renameText.isNotBlank()) {
                            viewModel.renameSubject(subject.id, renameText)
                            subjectToRename = null
                        }
                    },
                    enabled = renameText.isNotBlank(),
                    shape = PillShape
                ) {
                    Text("Save", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { subjectToRename = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Delete Subject Dialog
    subjectToDelete?.let { subject ->
        AlertDialog(
            onDismissRequest = { subjectToDelete = null },
            title = { Text("Delete Subject?", fontWeight = FontWeight.Bold) },
            text = { Text("This will permanently remove \"${subject.name}\" and all of its ${subject.totalChapters} chapters.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteSubject(subject)
                        subjectToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DangerRed)
                ) {
                    Text("Delete", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { subjectToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Edit Chapter Dialog
    chapterToEdit?.let { (subject, chapter) ->
        var chapterTitle by remember { mutableStateOf(chapter.title) }
        AlertDialog(
            onDismissRequest = { chapterToEdit = null },
            title = { Text("Edit Chapter", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = chapterTitle,
                    onValueChange = { chapterTitle = it },
                    label = { Text("Chapter title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (chapterTitle.isNotBlank()) {
                            viewModel.editChapter(subject.id, chapter.id, chapterTitle)
                            chapterToEdit = null
                        }
                    },
                    enabled = chapterTitle.isNotBlank(),
                    shape = PillShape
                ) {
                    Text("Save", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { chapterToEdit = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

// =============================================================================
// OverallProgressCard
// =============================================================================

@Composable
fun OverallProgressCard(
    doneChapters: Int,
    totalChapters: Int,
    progressFraction: Float,
    progressPercent: Int
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Overall progress",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "$doneChapters of $totalChapters chapters completed",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = "$progressPercent%",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            M3SquigglyProgress(
                progress = progressFraction,
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                height = 16.dp,
                strokeWidth = 4.dp
            )
        }
    }
}

// =============================================================================
// SubjectCard
// =============================================================================

@Composable
fun SubjectCard(
    subject: UiSubject,
    onToggleChapter: (String) -> Unit,
    onAddChapter: (String) -> Unit,
    onEditChapter: (UiChapter) -> Unit,
    onDeleteChapter: (String) -> Unit,
    onRenameSubject: () -> Unit,
    onDeleteSubject: () -> Unit
) {
    var newChapterTitle by remember { mutableStateOf("") }
    var showMenu by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header Row: Subject Name & Menu
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = subject.name,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${subject.doneChapters}/${subject.totalChapters} chapters · ${subject.progressPercent}%",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Options",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Rename subject") },
                            leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                onRenameSubject()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete subject", color = DangerRed) },
                            leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null, tint = DangerRed) },
                            onClick = {
                                showMenu = false
                                onDeleteSubject()
                            }
                        )
                    }
                }
            }

            // Squiggly Progress Indicator
            M3SquigglyProgress(
                progress = subject.progressFraction,
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                height = 12.dp,
                strokeWidth = 3.dp
            )

            // Chapters List
            if (subject.chapters.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    subject.chapters.forEach { chapter ->
                        ChapterRow(
                            chapter = chapter,
                            onToggle = { onToggleChapter(chapter.id) },
                            onEdit = { onEditChapter(chapter) },
                            onDelete = { onDeleteChapter(chapter.id) }
                        )
                    }
                }
            } else {
                Text(
                    text = "No chapters added yet. Type below to add chapter 1.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))

            // Add Chapter Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = newChapterTitle,
                    onValueChange = { newChapterTitle = it },
                    placeholder = { Text("Add chapter...", fontSize = 12.sp) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                )
                IconButton(
                    onClick = {
                        if (newChapterTitle.isNotBlank()) {
                            onAddChapter(newChapterTitle)
                            newChapterTitle = ""
                        }
                    },
                    enabled = newChapterTitle.isNotBlank()
                ) {
                    Icon(
                        imageVector = Icons.Default.AddCircle,
                        contentDescription = "Add chapter",
                        tint = if (newChapterTitle.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
    }
}

// =============================================================================
// ChapterRow
// =============================================================================

@Composable
fun ChapterRow(
    chapter: UiChapter,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onToggle)
            .padding(vertical = 6.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Checkbox(
            checked = chapter.done,
            onCheckedChange = { onToggle() },
            modifier = Modifier.size(24.dp)
        )
        Text(
            text = chapter.title,
            fontSize = 13.sp,
            color = if (chapter.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            textDecoration = if (chapter.done) TextDecoration.LineThrough else TextDecoration.None,
            modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        IconButton(
            onClick = onEdit,
            modifier = Modifier.size(24.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Edit,
                contentDescription = "Edit chapter",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.size(15.dp)
            )
        }
        IconButton(
            onClick = onDelete,
            modifier = Modifier.size(24.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Delete,
                contentDescription = "Delete chapter",
                tint = DangerRed.copy(alpha = 0.7f),
                modifier = Modifier.size(15.dp)
            )
        }
    }
}
