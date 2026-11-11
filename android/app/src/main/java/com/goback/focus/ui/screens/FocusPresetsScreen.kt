package com.goback.focus.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.goback.focus.data.entity.FocusPresetEntity
import com.goback.focus.ui.theme.CardShape
import com.goback.focus.ui.viewmodel.FocusPresetsViewModel
import org.json.JSONArray

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusPresetsScreen(
    onNavigateBack: () -> Unit,
    onApplyPreset: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FocusPresetsViewModel = viewModel()
) {
    val presets by viewModel.presets.collectAsStateWithLifecycle()
    val installedApps by viewModel.installedApps.collectAsStateWithLifecycle()
    val name by viewModel.name.collectAsStateWithLifecycle()
    val mode by viewModel.mode.collectAsStateWithLifecycle()
    val duration by viewModel.duration.collectAsStateWithLifecycle()
    val appSearch by viewModel.appSearch.collectAsStateWithLifecycle()
    val visibleApps by viewModel.visibleApps.collectAsStateWithLifecycle()
    val selectedPackages by viewModel.selectedPackages.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val saving by viewModel.saving.collectAsStateWithLifecycle()
    val presetToDelete by viewModel.presetToDelete.collectAsStateWithLifecycle()

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
            // Top Bar
            item {
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
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
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
                        text = "Focus presets",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.size(40.dp))
                }
            }
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
                            onValueChange = { viewModel.setName(it.take(60)) },
                            label = { Text("Preset name") },
                            placeholder = { Text("Deep Work, Reading, Revision…") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                            listOf("timer" to "Timer", "pomodoro" to "Pomodoro", "stopwatch" to "Stopwatch").forEach { (value, label) ->
                                FilterChip(
                                    selected = mode == value,
                                    onClick = { viewModel.setMode(value) },
                                    label = { Text(label) }
                                )
                            }
                        }
                        if (mode != "stopwatch") {
                            OutlinedTextField(
                                value = duration,
                                onValueChange = { viewModel.setDuration(it.filter(Char::isDigit).take(3)) },
                                label = { Text("Duration in minutes") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        Text("SESSION APP BLOCKS · OPTIONAL", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        OutlinedTextField(
                            value = appSearch,
                            onValueChange = { viewModel.setAppSearch(it) },
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
                                            viewModel.togglePackage(app.packageName)
                                        }.padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Checkbox(
                                            checked = app.packageName in selectedPackages,
                                            onCheckedChange = { _ ->
                                                viewModel.togglePackage(app.packageName)
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
                            onClick = { viewModel.savePreset() },
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
                            FilledTonalButton(onClick = {
                                viewModel.applyPreset(preset)
                                onApplyPreset()
                            }) {
                                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text("Use")
                            }
                            IconButton(onClick = { viewModel.setPresetToDelete(preset) }) {
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
            onDismissRequest = { viewModel.setPresetToDelete(null) },
            title = { Text("Delete preset?") },
            text = { Text("Remove “${preset.name}” from your saved focus presets?") },
            confirmButton = {
                TextButton(onClick = { viewModel.deletePreset(preset) }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.setPresetToDelete(null) }) {
                    Text("Cancel")
                }
            }
        )
    }
}
