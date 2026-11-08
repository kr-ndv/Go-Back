package com.goback.focus.ui.screens

import android.content.Intent
import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.goback.focus.data.db.GoBackDatabase
import com.goback.focus.data.entity.FocusPresetEntity
import com.goback.focus.logic.FocusMode
import com.goback.focus.ui.theme.CardShape
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusPresetsScreen(
    onNavigateBack: () -> Unit,
    onApplyPreset: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val database = remember { GoBackDatabase.getDatabase(context) }
    val scope = rememberCoroutineScope()
    val presets by database.focusPresetDao().getAll().collectAsStateWithLifecycle(initialValue = emptyList())

    var name by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf("timer") }
    var duration by remember { mutableStateOf("25") }
    var appSearch by remember { mutableStateOf("") }
    var installedApps by remember { mutableStateOf<List<LaunchableApp>>(emptyList()) }
    var selectedPackages by remember { mutableStateOf<Set<String>>(emptySet()) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var presetToDelete by remember { mutableStateOf<FocusPresetEntity?>(null) }

    LaunchedEffect(Unit) {
        installedApps = withContext(Dispatchers.IO) {
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
    }

    val visibleApps = remember(installedApps, appSearch) {
        val query = appSearch.trim().lowercase()
        installedApps.filter {
            query.isBlank() || it.appName.lowercase().contains(query) || it.packageName.lowercase().contains(query)
        }
    }

    fun savePreset() {
        val cleanName = name.trim()
        val minutes = duration.trim().toIntOrNull()
        if (cleanName.isBlank()) {
            error = "Enter a name for this preset."
            return
        }
        if (mode != "stopwatch" && (minutes == null || minutes !in 1..180)) {
            error = "Choose a timer duration from 1 to 180 minutes."
            return
        }
        if (selectedPackages.size > 100) {
            error = "A preset can block up to 100 apps."
            return
        }
        saving = true
        error = null
        scope.launch {
            runCatching {
                database.focusPresetDao().upsert(
                    FocusPresetEntity(
                        id = "preset_${UUID.randomUUID()}",
                        name = cleanName.take(60),
                        mode = mode,
                        durationMinutes = if (mode == "stopwatch") 25 else minutes!!,
                        blockedPackagesJson = JSONArray(selectedPackages.sorted()).toString(),
                        createdAt = Instant.now().toString()
                    )
                )
            }.onSuccess {
                name = ""
                mode = "timer"
                duration = "25"
                selectedPackages = emptySet()
                appSearch = ""
            }.onFailure {
                error = "The preset could not be saved. Please try again."
            }
            saving = false
        }
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
        context.getSharedPreferences("go_back_blocking", Context.MODE_PRIVATE)
            .edit().putString("focus.pending_preset", payload.toString()).apply()
        onApplyPreset()
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Focus presets", fontWeight = FontWeight.Bold) },
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
            contentPadding = PaddingValues(top = 8.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text("REUSE WHAT WORKS", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                Text("Your focus modes", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                Text(
                    "Save a timer style and optional apps to block only for that session. Your usual blocking rules are left unchanged.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            item {
                Surface(shape = CardShape, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it.take(60) },
                            label = { Text("Preset name") },
                            placeholder = { Text("Deep Work, Reading, Revision…") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                            listOf("timer" to "Timer", "pomodoro" to "Pomodoro", "stopwatch" to "Stopwatch").forEach { (value, label) ->
                                FilterChip(
                                    selected = mode == value,
                                    onClick = { mode = value },
                                    label = { Text(label) }
                                )
                            }
                        }
                        if (mode != "stopwatch") {
                            OutlinedTextField(
                                value = duration,
                                onValueChange = { duration = it.filter(Char::isDigit).take(3) },
                                label = { Text("Duration in minutes") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        Text("SESSION APP BLOCKS · OPTIONAL", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        OutlinedTextField(
                            value = appSearch,
                            onValueChange = { appSearch = it },
                            placeholder = { Text("Search installed apps") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (installedApps.isEmpty()) {
                            Text(
                                "No launcher apps were returned by Android. You can still save a timer-only preset.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                items(visibleApps, key = { it.packageName }) { app ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth().clickable {
                                            selectedPackages = if (app.packageName in selectedPackages) {
                                                selectedPackages - app.packageName
                                            } else if (selectedPackages.size < 100) {
                                                selectedPackages + app.packageName
                                            } else selectedPackages
                                        }.padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Checkbox(
                                            checked = app.packageName in selectedPackages,
                                            onCheckedChange = { checked ->
                                                selectedPackages = if (checked) selectedPackages + app.packageName else selectedPackages - app.packageName
                                            }
                                        )
                                        Column(Modifier.weight(1f)) {
                                            Text(app.appName, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                                            Text(app.packageName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                        }
                                    }
                                }
                            }
                        }
                        Text(
                            "${selectedPackages.size} apps selected",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = { savePreset() },
                            enabled = !saving,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (saving) "Saving…" else "Save preset")
                        }
                        error?.let {
                            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            item {
                Text("SAVED PRESETS", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
            if (presets.isEmpty()) {
                item {
                    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
                        Text("No presets yet. Create one above; your regular focus defaults will remain unchanged.", Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                items(presets, key = { it.id }) { preset ->
                    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(preset.name, fontWeight = FontWeight.Bold)
                                val blocks = runCatching { JSONArray(preset.blockedPackagesJson).length() }.getOrDefault(0)
                                val time = if (preset.mode == "stopwatch") "Stopwatch" else "${preset.mode.replaceFirstChar { it.uppercase() }} · ${preset.durationMinutes} min"
                                Text("$time · $blocks app block(s)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            FilledTonalButton(onClick = { applyPreset(preset) }) {
                                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text("Use")
                            }
                            IconButton(onClick = { presetToDelete = preset }) {
                                Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete ${preset.name}", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }

    presetToDelete?.let { preset ->
        AlertDialog(
            onDismissRequest = { presetToDelete = null },
            title = { Text("Delete preset?") },
            text = { Text("Remove “${preset.name}” from your saved focus presets?") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { database.focusPresetDao().delete(preset) }
                    presetToDelete = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { presetToDelete = null }) { Text("Cancel") } }
        )
    }
}
