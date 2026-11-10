package com.goback.focus.ui.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.goback.focus.GoBackApplication
import com.goback.focus.data.entity.FocusPresetEntity
import com.goback.focus.ui.screens.LaunchableApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.util.UUID

class FocusPresetsViewModel(application: Application) : AndroidViewModel(application) {

    private val database = (application as GoBackApplication).database
    private val presetDao = database.focusPresetDao()

    val presets: StateFlow<List<FocusPresetEntity>> = presetDao.getAll().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val _installedApps = MutableStateFlow<List<LaunchableApp>>(emptyList())
    val installedApps: StateFlow<List<LaunchableApp>> = _installedApps.asStateFlow()

    private val _name = MutableStateFlow("")
    val name: StateFlow<String> = _name.asStateFlow()

    private val _mode = MutableStateFlow("timer")
    val mode: StateFlow<String> = _mode.asStateFlow()

    private val _duration = MutableStateFlow("25")
    val duration: StateFlow<String> = _duration.asStateFlow()

    private val _appSearch = MutableStateFlow("")
    val appSearch: StateFlow<String> = _appSearch.asStateFlow()

    private val _selectedPackages = MutableStateFlow<Set<String>>(emptySet())
    val selectedPackages: StateFlow<Set<String>> = _selectedPackages.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving.asStateFlow()

    private val _presetToDelete = MutableStateFlow<FocusPresetEntity?>(null)
    val presetToDelete: StateFlow<FocusPresetEntity?> = _presetToDelete.asStateFlow()

    val visibleApps: StateFlow<List<LaunchableApp>> = combine(_installedApps, _appSearch) { apps, query ->
        val q = query.trim().lowercase()
        if (q.isBlank()) {
            apps
        } else {
            apps.filter { it.appName.lowercase().contains(q) || it.packageName.lowercase().contains(q) }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    init {
        loadInstalledApps()
    }

    private fun loadInstalledApps() {
        viewModelScope.launch {
            val list = withContext(Dispatchers.IO) {
                val context = getApplication<Application>()
                val pm = context.packageManager
                val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                runCatching {
                    pm.queryIntentActivities(query, 0).mapNotNull { resolve ->
                        val info = resolve.activityInfo?.applicationInfo ?: return@mapNotNull null
                        if (info.packageName == context.packageName) return@mapNotNull null
                        val label = resolve.loadLabel(pm)?.toString()?.trim().orEmpty()
                        if (label.isBlank()) return@mapNotNull null
                        LaunchableApp(
                            packageName = info.packageName,
                            appName = label,
                            isSystemApp = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                        )
                    }.distinctBy { it.packageName }.sortedBy { it.appName.lowercase() }
                }.getOrDefault(emptyList())
            }
            _installedApps.value = list
        }
    }

    fun setName(value: String) { _name.value = value }
    fun setMode(value: String) { _mode.value = value }
    fun setDuration(value: String) { _duration.value = value }
    fun setAppSearch(value: String) { _appSearch.value = value }

    fun togglePackage(pkg: String) {
        val current = _selectedPackages.value
        _selectedPackages.value = if (current.contains(pkg)) current - pkg else current + pkg
    }

    fun setPresetToDelete(preset: FocusPresetEntity?) {
        _presetToDelete.value = preset
    }

    fun deletePreset(preset: FocusPresetEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            presetDao.delete(preset)
            withContext(Dispatchers.Main) {
                _presetToDelete.value = null
            }
        }
    }

    fun savePreset(): Boolean {
        val cleanName = _name.value.trim()
        val minutes = _duration.value.trim().toIntOrNull()
        if (cleanName.isBlank()) {
            _error.value = "Enter a name for this preset."
            return false
        }
        if (_mode.value != "stopwatch" && (minutes == null || minutes !in 1..180)) {
            _error.value = "Choose a timer duration from 1 to 180 minutes."
            return false
        }
        if (_selectedPackages.value.size > 100) {
            _error.value = "A preset can block up to 100 apps."
            return false
        }

        _saving.value = true
        _error.value = null
        val packages = _selectedPackages.value
        val presetMode = _mode.value
        val finalMinutes = if (presetMode == "stopwatch") 25 else minutes!!

        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                presetDao.upsert(
                    FocusPresetEntity(
                        id = "preset_${UUID.randomUUID()}",
                        name = cleanName.take(60),
                        mode = presetMode,
                        durationMinutes = finalMinutes,
                        blockedPackagesJson = JSONArray(packages.sorted()).toString(),
                        createdAt = Instant.now().toString()
                    )
                )
            }.onSuccess {
                withContext(Dispatchers.Main) {
                    _name.value = ""
                    _mode.value = "timer"
                    _duration.value = "25"
                    _selectedPackages.value = emptySet()
                    _appSearch.value = ""
                    _saving.value = false
                }
            }.onFailure {
                withContext(Dispatchers.Main) {
                    _error.value = "The preset could not be saved. Please try again."
                    _saving.value = false
                }
            }
        }
        return true
    }

    fun applyPreset(preset: FocusPresetEntity) {
        val packages = runCatching {
            val array = JSONArray(preset.blockedPackagesJson)
            List(array.length()) { array.optString(it) }.filter { it.isNotBlank() }
        }.getOrDefault(emptyList())
        val payload = JSONObject()
            .put("name", preset.name)
            .put("mode", preset.mode)
            .put("durationMinutes", preset.durationMinutes)
            .put("blockedPackages", JSONArray(packages))
        getApplication<Application>().getSharedPreferences("go_back_blocking", Context.MODE_PRIVATE)
            .edit().putString("focus.pending_preset", payload.toString()).apply()
    }
}
