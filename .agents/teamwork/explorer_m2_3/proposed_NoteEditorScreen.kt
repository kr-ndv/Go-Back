package com.goback.focus.ui.screens

import android.app.Application
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.goback.focus.GoBackApplication
import com.goback.focus.data.entity.NoteEntity
import com.goback.focus.ui.theme.DangerRed
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class NoteEditorViewModel(
    application: Application,
    private val noteId: String
) : AndroidViewModel(application) {

    private val noteDao = (application as GoBackApplication).database.noteDao()

    private val _title = MutableStateFlow("")
    val title: StateFlow<String> = _title.asStateFlow()

    private val _body = MutableStateFlow("")
    val body: StateFlow<String> = _body.asStateFlow()

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    private val _saveStatus = MutableStateFlow("Loading note…")
    val saveStatus: StateFlow<String> = _saveStatus.asStateFlow()

    private val _isPreviewMode = MutableStateFlow(false)
    val isPreviewMode: StateFlow<Boolean> = _isPreviewMode.asStateFlow()

    private var autoSaveJob: Job? = null
    private val persistenceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        loadNote()
    }

    private fun loadNote() {
        viewModelScope.launch {
            try {
                val note = withContext(Dispatchers.IO) {
                    noteDao.getById(noteId)
                }
                if (note != null) {
                    _title.value = note.title
                    _body.value = note.body
                    _isReady.value = true
                    _saveStatus.value = "Saved locally"
                } else {
                    _saveStatus.value = "Note not found"
                }
            } catch (e: Exception) {
                _saveStatus.value = "Could not load note"
            }
        }
    }

    fun onTitleChange(newTitle: String) {
        _title.value = newTitle
        triggerDebouncedAutosave()
    }

    fun onBodyChange(newBody: String) {
        _body.value = newBody
        triggerDebouncedAutosave()
    }

    fun togglePreviewMode() {
        _isPreviewMode.value = !_isPreviewMode.value
    }

    private fun triggerDebouncedAutosave() {
        if (!_isReady.value) return
        _saveStatus.value = "Saving…"
        autoSaveJob?.cancel()
        autoSaveJob = viewModelScope.launch {
            delay(500)
            executeSave()
        }
    }

    suspend fun executeSave(): Boolean {
        if (!_isReady.value) return false
        _saveStatus.value = "Saving…"
        return withContext(Dispatchers.IO) {
            try {
                val cleanTitle = _title.value.trim().ifEmpty { "Untitled note" }
                val cleanBody = _body.value
                val timestamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }.format(Date())

                val entity = NoteEntity(
                    id = noteId,
                    title = cleanTitle,
                    body = cleanBody,
                    updatedAt = timestamp
                )
                noteDao.upsert(entity)
                withContext(Dispatchers.Main) {
                    _saveStatus.value = "Saved locally"
                }
                true
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _saveStatus.value = "Save failed"
                }
                false
            }
        }
    }

    /**
     * Immediate flush on back navigation or app backgrounding.
     * Uses persistent IO scope to ensure completion even if ViewModel is cleared.
     */
    fun flushSave() {
        autoSaveJob?.cancel()
        if (!_isReady.value) return
        val currentTitle = _title.value.trim().ifEmpty { "Untitled note" }
        val currentBody = _body.value
        val timestamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date())

        persistenceScope.launch {
            try {
                noteDao.upsert(
                    NoteEntity(
                        id = noteId,
                        title = currentTitle,
                        body = currentBody,
                        updatedAt = timestamp
                    )
                )
            } catch (_: Exception) {}
        }
    }

    fun deleteCurrentNote(onDeleted: () -> Unit) {
        autoSaveJob?.cancel()
        persistenceScope.launch {
            try {
                noteDao.deleteById(noteId)
                withContext(Dispatchers.Main) {
                    onDeleted()
                }
            } catch (_: Exception) {}
        }
    }

    override fun onCleared() {
        super.onCleared()
        flushSave()
    }
}

class NoteEditorViewModelFactory(
    private val application: Application,
    private val noteId: String
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return NoteEditorViewModel(application, noteId) as T
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditorScreen(
    noteId: String,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current.applicationContext as Application
    val viewModel: NoteEditorViewModel = viewModel(
        key = "editor_$noteId",
        factory = remember(noteId) { NoteEditorViewModelFactory(context, noteId) }
    )

    val title by viewModel.title.collectAsStateWithLifecycle()
    val body by viewModel.body.collectAsStateWithLifecycle()
    val isReady by viewModel.isReady.collectAsStateWithLifecycle()
    val saveStatus by viewModel.saveStatus.collectAsStateWithLifecycle()
    val isPreviewMode by viewModel.isPreviewMode.collectAsStateWithLifecycle()

    var showDeleteConfirm by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    // 1. Hardware and gesture back handler with immediate flush
    BackHandler {
        viewModel.flushSave()
        onNavigateBack()
    }

    // 2. Lifecycle observer: flush when app backgrounded or screen paused
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                viewModel.flushSave()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.flushSave()
        }
    }

    // 3. Delete confirmation dialog
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            icon = { Icon(Icons.Filled.DeleteForever, contentDescription = null, tint = DangerRed) },
            title = { Text("Delete Note?", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to delete this note? This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        viewModel.deleteCurrentNote { onNavigateBack() }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DangerRed)
                ) {
                    Text("Delete", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = saveStatus,
                        fontSize = 12.sp,
                        color = when (saveStatus) {
                            "Save failed", "Note not found", "Could not load note" -> DangerRed
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            viewModel.flushSave()
                            onNavigateBack()
                        },
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .size(38.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Save and go back",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                },
                actions = {
                    // Markdown Preview toggle
                    IconButton(
                        onClick = { viewModel.togglePreviewMode() },
                        enabled = isReady
                    ) {
                        Icon(
                            imageVector = if (isPreviewMode) Icons.Outlined.Edit else Icons.Outlined.Visibility,
                            contentDescription = if (isPreviewMode) "Switch to Edit" else "Switch to Preview",
                            tint = if (isPreviewMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Delete button
                    IconButton(
                        onClick = { showDeleteConfirm = true },
                        enabled = isReady
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.DeleteOutline,
                            contentDescription = "Delete note",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Explicit Save button
                    TextButton(
                        onClick = {
                            coroutineScope.launch {
                                viewModel.executeSave()
                            }
                        },
                        enabled = isReady
                    ) {
                        Text(
                            text = "Save",
                            fontWeight = FontWeight.Bold,
                            color = if (isReady) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        if (!isReady && saveStatus == "Note not found") {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Note Not Found",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "This note may have been deleted.",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(onClick = onNavigateBack) {
                        Text("Return to Notes")
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 24.dp, vertical = 12.dp)
            ) {
                // Title Input
                TextField(
                    value = title,
                    onValueChange = { viewModel.onTitleChange(it) },
                    placeholder = {
                        Text(
                            text = "Note title",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = (-0.8).sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    },
                    textStyle = MaterialTheme.typography.headlineMedium.copy(
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = (-0.8).sp,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    singleLine = false,
                    maxLines = 3,
                    enabled = isReady,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                if (title.isBlank() && isReady) {
                    Text(
                        text = "Title is required (will default to 'Untitled note' on exit)",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.padding(start = 4.dp, top = 2.dp, bottom = 8.dp)
                    )
                } else {
                    Spacer(modifier = Modifier.height(8.dp))
                }

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    thickness = 1.dp
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Body Section: Editor vs Markdown Preview
                if (isPreviewMode) {
                    MarkdownContent(
                        markdownText = body,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    )
                } else {
                    TextField(
                        value = body,
                        onValueChange = { viewModel.onBodyChange(it) },
                        placeholder = {
                            Text(
                                text = "Start writing… (Markdown supported: # Headers, - Lists, **bold**, *italic*, `code`)",
                                fontSize = 16.sp,
                                lineHeight = 26.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                        },
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = 16.sp,
                            lineHeight = 26.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        enabled = isReady,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
fun MarkdownContent(
    markdownText: String,
    modifier: Modifier = Modifier
) {
    val lines = remember(markdownText) { markdownText.lines() }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (markdownText.isBlank()) {
            Text(
                text = "Nothing to preview yet. Switch to Edit mode and start writing.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@Column
        }

        var inCodeBlock = false
        val codeBlockLines = mutableListOf<String>()

        lines.forEach { line ->
            val trimmed = line.trim()
            if (trimmed.startsWith("```")) {
                if (inCodeBlock) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ) {
                        Text(
                            text = codeBlockLines.joinToString("\n"),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                    codeBlockLines.clear()
                    inCodeBlock = false
                } else {
                    inCodeBlock = true
                }
            } else if (inCodeBlock) {
                codeBlockLines.add(line)
            } else if (trimmed.startsWith("# ")) {
                Text(
                    text = trimmed.removePrefix("# ").trim(),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface
                )
            } else if (trimmed.startsWith("## ")) {
                Text(
                    text = trimmed.removePrefix("## ").trim(),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            } else if (trimmed.startsWith("### ")) {
                Text(
                    text = trimmed.removePrefix("### ").trim(),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            } else if (trimmed.startsWith("- ") || trimmed.startsWith("* ")) {
                Row(
                    modifier = Modifier.padding(start = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = "•",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = parseInlineMarkdown(trimmed.substring(2).trim()),
                        fontSize = 15.sp,
                        lineHeight = 22.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            } else if (trimmed.startsWith("> ")) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 4.dp),
                    shape = RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    border = BorderStroke(3.dp, MaterialTheme.colorScheme.primary)
                ) {
                    Text(
                        text = parseInlineMarkdown(trimmed.removePrefix("> ").trim()),
                        fontStyle = FontStyle.Italic,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            } else if (trimmed == "---" || trimmed == "***") {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    thickness = 1.dp,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            } else if (trimmed.isEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
            } else {
                Text(
                    text = parseInlineMarkdown(trimmed),
                    fontSize = 15.sp,
                    lineHeight = 23.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

fun parseInlineMarkdown(text: String): AnnotatedString {
    return buildAnnotatedString {
        var i = 0
        while (i < text.length) {
            when {
                text.startsWith("**", i) -> {
                    val end = text.indexOf("**", i + 2)
                    if (end != -1) {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                            append(text.substring(i + 2, end))
                        }
                        i = end + 2
                    } else {
                        append(text[i])
                        i++
                    }
                }
                text.startsWith("`", i) -> {
                    val end = text.indexOf("`", i + 1)
                    if (end != -1) {
                        withStyle(
                            SpanStyle(
                                fontFamily = FontFamily.Monospace,
                                background = Color(0x33888888)
                            )
                        ) {
                            append(" ${text.substring(i + 1, end)} ")
                        }
                        i = end + 1
                    } else {
                        append(text[i])
                        i++
                    }
                }
                text.startsWith("*", i) -> {
                    val end = text.indexOf("*", i + 1)
                    if (end != -1) {
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                            append(text.substring(i + 1, end))
                        }
                        i = end + 1
                    } else {
                        append(text[i])
                        i++
                    }
                }
                else -> {
                    append(text[i])
                    i++
                }
            }
        }
    }
}
