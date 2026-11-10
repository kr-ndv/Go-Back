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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.goback.focus.ui.viewmodel.BackupRestoreViewModel
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupRestoreScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BackupRestoreViewModel = viewModel()
) {
    val status by viewModel.status.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val showConfirm by viewModel.showConfirm.collectAsStateWithLifecycle()
    val pendingImport by viewModel.pendingImport.collectAsStateWithLifecycle()
    val hasRestorePoint by viewModel.hasRestorePoint.collectAsStateWithLifecycle()

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            viewModel.exportToUri(uri)
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            viewModel.importFromUri(uri)
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
                        Button(onClick = { exportLauncher.launch("go-back-backup-${LocalDate.now()}.json") }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
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
                            TextButton(onClick = { viewModel.undoLastRestore() }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
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
            onDismissRequest = { viewModel.cancelConfirm() },
            title = { Text("Replace local data?") },
            text = { Text("This restores the selected backup and replaces your current notes, syllabus, focus history, schedules, presets, limits, exams and settings. Go Back will first save a restore point.") },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmRestore() }) { Text("Restore backup", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelConfirm() }) { Text("Cancel") }
            }
        )
    }
}
