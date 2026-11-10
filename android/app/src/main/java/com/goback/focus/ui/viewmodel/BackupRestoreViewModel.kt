package com.goback.focus.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.goback.focus.GoBackApplication
import com.goback.focus.data.entity.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant

private const val RESTORE_POINT_FILE = "go-back-before-last-restore.json"

class BackupRestoreViewModel(application: Application) : AndroidViewModel(application) {

    private val database = (application as GoBackApplication).database
    private val restorePoint = File(application.filesDir, RESTORE_POINT_FILE)

    private val _status = MutableStateFlow<String?>(null)
    val status: StateFlow<String?> = _status.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _pendingImport = MutableStateFlow<String?>(null)
    val pendingImport: StateFlow<String?> = _pendingImport.asStateFlow()

    private val _showConfirm = MutableStateFlow(false)
    val showConfirm: StateFlow<Boolean> = _showConfirm.asStateFlow()

    private val _hasRestorePoint = MutableStateFlow(restorePoint.exists())
    val hasRestorePoint: StateFlow<Boolean> = _hasRestorePoint.asStateFlow()

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

    fun exportToUri(uri: Uri) {
        viewModelScope.launch {
            _busy.value = true
            _status.value = null
            runCatching {
                val json = exportJson()
                withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(json) }
                        ?: error("Android could not open the selected destination.")
                }
            }.onSuccess { _status.value = "Backup exported successfully." }
                .onFailure { _status.value = it.message ?: "Backup export failed." }
            _busy.value = false
        }
    }

    fun importFromUri(uri: Uri) {
        viewModelScope.launch {
            _busy.value = true
            _status.value = null
            runCatching {
                withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                        ?: error("Android could not read the selected file.")
                }
            }.onSuccess {
                _pendingImport.value = it
                _showConfirm.value = true
            }.onFailure { _status.value = it.message ?: "Backup could not be read." }
            _busy.value = false
        }
    }

    fun confirmRestore() {
        val json = _pendingImport.value ?: return
        _showConfirm.value = false
        _pendingImport.value = null
        runRestore(json, createRestorePoint = true)
    }

    fun cancelConfirm() {
        _showConfirm.value = false
        _pendingImport.value = null
    }

    fun undoLastRestore() {
        viewModelScope.launch {
            if (!restorePoint.exists()) return@launch
            val content = withContext(Dispatchers.IO) { restorePoint.readText() }
            runRestore(content, createRestorePoint = false)
        }
    }

    fun runRestore(json: String, createRestorePoint: Boolean) {
        viewModelScope.launch {
            _busy.value = true
            _status.value = null
            runCatching { restoreJson(json, createRestorePoint) }
                .onSuccess {
                    _status.value = if (createRestorePoint) "Backup restored. A local restore point was saved so you can undo this restore." else "Previous restore point restored."
                    _hasRestorePoint.value = restorePoint.exists()
                    getApplication<Application>().getSharedPreferences("go_back_blocking", 0).edit()
                        .putBoolean("focus_active", false)
                        .remove("focus_state")
                        .remove("session_blocked_packages")
                        .apply()
                }
                .onFailure { _status.value = it.message ?: "Backup restore failed. Your current database was not replaced." }
            _busy.value = false
        }
    }

    companion object {
        fun backupChecksum(value: String): String {
            var hash = 0x811c9dc5L
            value.forEach { character ->
                hash = ((hash xor character.code.toLong()) * 0x01000193L) and 0xffffffffL
            }
            return hash.toString(16).padStart(8, '0')
        }
    }
}
