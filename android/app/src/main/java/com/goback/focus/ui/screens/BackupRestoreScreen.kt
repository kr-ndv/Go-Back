package com.goback.focus.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.room.withTransaction
import com.goback.focus.data.db.GoBackDatabase
import com.goback.focus.data.entity.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant

private const val RESTORE_POINT_FILE = "go-back-before-last-restore.json"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupRestoreScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val database = remember { GoBackDatabase.getDatabase(context) }
    val scope = rememberCoroutineScope()
    val restorePoint = remember { File(context.filesDir, RESTORE_POINT_FILE) }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var pendingImport by remember { mutableStateOf<String?>(null) }
    var showConfirm by remember { mutableStateOf(false) }
    var hasRestorePoint by remember { mutableStateOf(restorePoint.exists()) }

    suspend fun exportJson(): String = withContext(Dispatchers.IO) {
        val tables = JSONObject()
        tables.put("notes", JSONArray().apply {
            database.noteDao().getAll().first().forEach { row ->
                put(JSONObject().put("id", row.id).put("title", row.title).put("body", row.body).put("updated_at", row.updatedAt))
            }
        })
        tables.put("subjects", JSONArray().apply {
            database.subjectDao().getAll().first().forEach { row ->
                put(JSONObject().put("id", row.id).put("name", row.name).put("chapters_json", row.chaptersJson).put("updated_at", row.updatedAt))
            }
        })
        tables.put("focus_sessions", JSONArray().apply {
            database.focusSessionDao().getAll().first().forEach { row ->
                put(JSONObject().put("id", row.id).put("started_at", row.startedAt).put("duration_seconds", row.durationSeconds)
                    .put("completed", row.completed).put("session_type", row.sessionType).put("mode", row.mode).put("tag", row.tag).put("notes", row.notes))
            }
        })
        tables.put("focus_schedules", JSONArray().apply {
            database.focusScheduleDao().getAll().first().forEach { row ->
                put(JSONObject().put("id", row.id).put("title", row.title).put("days_json", row.daysJson).put("start_time", row.startTime)
                    .put("duration_minutes", row.durationMinutes).put("enabled", row.enabled).put("created_at", row.createdAt)
                    .put("reminder_minutes", row.reminderMinutes).put("skip_dates_json", row.skipDatesJson).put("routine_type", row.routineType).put("timezone", row.timezone))
            }
        })
        tables.put("focus_presets", JSONArray().apply {
            database.focusPresetDao().getAll().first().forEach { row ->
                put(JSONObject().put("id", row.id).put("name", row.name).put("mode", row.mode).put("duration_minutes", row.durationMinutes)
                    .put("blocked_packages_json", row.blockedPackagesJson).put("created_at", row.createdAt))
            }
        })
        tables.put("usage_limits", JSONArray().apply {
            database.usageLimitDao().getAll().first().forEach { row ->
                put(JSONObject().put("id", row.id).put("package_name", row.packageName).put("app_name", row.appName).put("limit_minutes", row.limitMinutes)
                    .put("warning_percent", row.warningPercent).put("enabled", row.enabled).put("created_at", row.createdAt))
            }
        })
        tables.put("exams", JSONArray().apply {
            database.examDao().getAll().first().forEach { row ->
                put(JSONObject().put("id", row.id).put("title", row.title).put("exam_date", row.examDate).put("details", row.details))
            }
        })
        tables.put("settings", JSONArray().apply {
            database.settingDao().getAll().first().filter { it.key != "security.pin" }.forEach { row ->
                put(JSONObject().put("key", row.key).put("value", row.value))
            }
        })
        val checksum = backupChecksum(tables.toString())
        JSONObject()
            .put("app", "Go Back")
            .put("schemaVersion", 5)
            .put("exportedAt", Instant.now().toString())
            .put("checksum", checksum)
            .put("includes", JSONArray(listOf("notes", "syllabus", "focus history", "focus schedules", "focus presets", "screen-time limits", "exams", "settings")))
            .put("tables", tables)
            .toString(2)
    }

    suspend fun restoreJson(json: String, createRestorePoint: Boolean) = withContext(Dispatchers.IO) {
        require(json.length <= 20_000_000) { "The selected backup is too large to restore safely." }
        val backup = JSONObject(json)
        require(backup.optString("app") == "Go Back") { "This file is not a Go Back backup." }
        val schema = backup.optInt("schemaVersion", 0)
        require(schema in 1..5) { "This backup version is not supported by this release." }
        val tables = backup.optJSONObject("tables") ?: error("The backup does not contain table data.")
        val expectedChecksum = backup.optString("checksum", "")
        if (expectedChecksum.isNotBlank()) {
            require(expectedChecksum == backupChecksum(tables.toString())) {
                "Backup integrity check failed. The file may be damaged."
            }
        }

        fun rows(name: String): List<JSONObject> {
            val array = tables.optJSONArray(name) ?: JSONArray()
            require(array.length() <= 50_000) { "The backup contains too many $name records." }
            return List(array.length()) { index ->
                array.optJSONObject(index) ?: error("A record in $name is invalid.")
            }
        }
        fun requiredString(row: JSONObject, key: String): String {
            require(row.has(key) && !row.isNull(key)) { "A $key value is missing from the backup." }
            return row.getString(key)
        }
        fun intValue(row: JSONObject, key: String, fallback: Int = 0): Int =
            if (row.has(key) && !row.isNull(key)) row.getInt(key) else fallback

        val notes = rows("notes").map { NoteEntity(requiredString(it, "id"), requiredString(it, "title"), it.optString("body", ""), requiredString(it, "updated_at")) }
        val subjects = rows("subjects").map { SubjectEntity(requiredString(it, "id"), requiredString(it, "name"), it.optString("chapters_json", "[]"), requiredString(it, "updated_at")) }
        val sessions = rows("focus_sessions").map {
            val duration = intValue(it, "duration_seconds")
            require(duration in 0..31_536_000) { "A focus session has an invalid duration." }
            FocusSessionEntity(
                requiredString(it, "id"), requiredString(it, "started_at"), duration,
                intValue(it, "completed"), it.optString("session_type", "focus"),
                it.optString("mode", "timer"), it.optString("tag", ""), it.optString("notes", "")
            )
        }
        val schedules = rows("focus_schedules").map {
            val duration = intValue(it, "duration_minutes", 25)
            require(duration in 5..180) { "A focus schedule has an invalid duration." }
            FocusScheduleEntity(
                requiredString(it, "id"), requiredString(it, "title"), it.optString("days_json", "[]"),
                requiredString(it, "start_time"), duration, intValue(it, "enabled", 1),
                requiredString(it, "created_at"), intValue(it, "reminder_minutes"),
                it.optString("skip_dates_json", "[]"), it.optString("routine_type", "custom"), it.optString("timezone", "")
            )
        }
        val presets = rows("focus_presets").map {
            val duration = intValue(it, "duration_minutes", 25)
            require(duration in 1..180) { "A focus preset has an invalid duration." }
            FocusPresetEntity(
                requiredString(it, "id"), requiredString(it, "name"), it.optString("mode", "timer"),
                duration, it.optString("blocked_packages_json", "[]"), requiredString(it, "created_at")
            )
        }
        val limits = rows("usage_limits").map {
            val duration = intValue(it, "limit_minutes")
            require(duration in 1..1440) { "A screen-time limit has an invalid duration." }
            UsageLimitEntity(
                requiredString(it, "id"), requiredString(it, "package_name"), requiredString(it, "app_name"),
                duration, intValue(it, "warning_percent", 80), intValue(it, "enabled", 1), requiredString(it, "created_at")
            )
        }
        val exams = rows("exams").map {
            ExamEntity(requiredString(it, "id"), requiredString(it, "title"), requiredString(it, "exam_date"), it.optString("details", ""))
        }
        val settings = rows("settings").map {
            SettingEntity(requiredString(it, "key"), requiredString(it, "value"))
        }.filter { it.key != "security.pin" }

        require(notes.map { it.id }.toSet().size == notes.size) { "The backup contains duplicate note IDs." }
        require(subjects.map { it.id }.toSet().size == subjects.size) { "The backup contains duplicate subject IDs." }
        require(sessions.map { it.id }.toSet().size == sessions.size) { "The backup contains duplicate focus-session IDs." }
        require(schedules.map { it.id }.toSet().size == schedules.size) { "The backup contains duplicate schedule IDs." }
        require(presets.map { it.id }.toSet().size == presets.size) { "The backup contains duplicate preset IDs." }
        require(limits.map { it.packageName }.toSet().size == limits.size) { "The backup contains duplicate app limits." }
        require(exams.map { it.id }.toSet().size == exams.size) { "The backup contains duplicate exam IDs." }
        require(settings.map { it.key }.toSet().size == settings.size) { "The backup contains duplicate setting keys." }

        val localPin = database.settingDao().getValue("security.pin")
        if (createRestorePoint) {
            val before = exportJson()
            restorePoint.writeText(before)
        }

        database.withTransaction {
            database.settingDao().deleteAll()
            database.examDao().deleteAll()
            database.usageLimitDao().deleteAll()
            database.focusPresetDao().deleteAll()
            database.focusScheduleDao().deleteAll()
            database.focusSessionDao().deleteAll()
            database.subjectDao().deleteAll()
            database.noteDao().deleteAll()
            notes.forEach { database.noteDao().upsert(it) }
            subjects.forEach { database.subjectDao().upsert(it) }
            sessions.forEach { database.focusSessionDao().insert(it) }
            schedules.forEach { database.focusScheduleDao().upsert(it) }
            presets.forEach { database.focusPresetDao().upsert(it) }
            limits.forEach { database.usageLimitDao().upsert(it) }
            exams.forEach { database.examDao().upsert(it) }
            settings.forEach { database.settingDao().setValue(it) }
            if (!localPin.isNullOrBlank()) {
                database.settingDao().setValue(SettingEntity("security.pin", localPin))
            }
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            scope.launch {
                busy = true
                status = null
                runCatching {
                    val json = exportJson()
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(json) }
                            ?: error("Android could not open the selected destination.")
                    }
                }.onSuccess { status = "Backup exported successfully." }
                    .onFailure { status = it.message ?: "Backup export failed." }
                busy = false
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                busy = true
                status = null
                runCatching {
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                            ?: error("Android could not read the selected file.")
                    }
                }.onSuccess {
                    pendingImport = it
                    showConfirm = true
                }.onFailure { status = it.message ?: "Backup could not be read." }
                busy = false
            }
        }
    }

    fun runRestore(json: String, createRestorePoint: Boolean) {
        scope.launch {
            busy = true
            status = null
            runCatching { restoreJson(json, createRestorePoint) }
                .onSuccess {
                    status = if (createRestorePoint) "Backup restored. A local restore point was saved so you can undo this restore." else "Previous restore point restored."
                    hasRestorePoint = restorePoint.exists()
                    context.getSharedPreferences("go_back_blocking", 0).edit()
                        .putBoolean("focus_active", false)
                        .remove("focus_state")
                        .remove("session_blocked_packages")
                        .apply()
                }
                .onFailure { status = it.message ?: "Backup restore failed. Your current database was not replaced." }
            busy = false
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Backup & Restore", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { insets ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(insets).padding(horizontal = 18.dp),
            contentPadding = PaddingValues(vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Text("YOUR DATA STAYS YOURS", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                Text("Keep a safe copy", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                Text(
                    "Back up notes, syllabus, focus history, schedules, presets, app limits, exams and saved settings to a JSON file you choose.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            item {
                ElevatedCard {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(Icons.Filled.Backup, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text("Export backup", fontWeight = FontWeight.Bold)
                        }
                        Text("Create a portable Go Back backup. PIN credentials are deliberately excluded.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Button(onClick = { exportLauncher.launch("go-back-backup-${java.time.LocalDate.now()}.json") }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Filled.FileDownload, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Export JSON backup")
                        }
                    }
                }
            }
            item {
                ElevatedCard {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(Icons.Filled.Restore, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text("Restore backup", fontWeight = FontWeight.Bold)
                        }
                        Text("Restoring replaces the local Go Back database. A restore point is created first and can be restored once to undo the operation.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/json", "text/*")) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Filled.FileUpload, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Choose backup file")
                        }
                        if (hasRestorePoint) {
                            TextButton(onClick = {
                                scope.launch {
                                    val content = withContext(Dispatchers.IO) { restorePoint.readText() }
                                    runRestore(content, false)
                                }
                            }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                                Text("Undo last restore")
                            }
                        }
                    }
                }
            }
            if (busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            status?.let { message ->
                item {
                    Text(message, color = if (message.contains("failed", true) || message.contains("could not", true)) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }

    if (showConfirm && pendingImport != null) {
        AlertDialog(
            onDismissRequest = { showConfirm = false; pendingImport = null },
            title = { Text("Replace local data?") },
            text = { Text("This restores the selected backup and replaces your current notes, syllabus, focus history, schedules, presets, limits, exams and settings. Go Back will first save a restore point.") },
            confirmButton = {
                TextButton(onClick = {
                    val json = pendingImport
                    showConfirm = false
                    pendingImport = null
                    if (json != null) runRestore(json, true)
                }) { Text("Restore backup", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm = false; pendingImport = null }) { Text("Cancel") }
            }
        )
    }
}

private fun backupChecksum(value: String): String {
    var hash = 0x811c9dc5L
    value.forEach { character ->
        hash = ((hash xor character.code.toLong()) * 0x01000193L) and 0xffffffffL
    }
    return hash.toString(16).padStart(8, '0')
}
