package com.goback.focus.ui.screens

import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.goback.focus.R
import com.goback.focus.GoBackApplication
import com.goback.focus.data.db.GoBackDatabase
import com.goback.focus.data.entity.SettingEntity
import com.goback.focus.service.FocusTileService
import com.goback.focus.ui.components.ProfileAvatar
import com.goback.focus.ui.components.PinModalMode
import com.goback.focus.ui.components.SecurityPinDialog
import com.goback.focus.ui.components.SecurityPinUtil
import com.goback.focus.ui.theme.CardShape
import com.goback.focus.ui.theme.ChipShape
import com.goback.focus.ui.theme.ModalShape
import com.goback.focus.ui.theme.PillShape
import kotlinx.coroutines.launch

data class ThemeColorOption(val name: String, val color: Color)

val THEME_PALETTES = listOf(
    ThemeColorOption("Forest", Color(0xFF365C32)),
    ThemeColorOption("Blue", Color(0xFF2563EB)),
    ThemeColorOption("Violet", Color(0xFF7C3AED)),
    ThemeColorOption("Amber", Color(0xFFD97706)),
    ThemeColorOption("Rose", Color(0xFFE11D48)),
    ThemeColorOption("Teal", Color(0xFF0D9488)),
    ThemeColorOption("Berry", Color(0xFF9333EA)),
    ThemeColorOption("Sakura", Color(0xFFEC4899)),
    ThemeColorOption("Graphite", Color(0xFF4B5563)),
    ThemeColorOption("Coral", Color(0xFFEA580C)),
    ThemeColorOption("Lavender", Color(0xFF8B5CF6)),
    ThemeColorOption("Crimson", Color(0xFFDC2626))
)

val FINISH_ALARM_SOUNDS = listOf(
    Pair("default", "System default notification"),
    Pair("focus_bell.wav", "Soft bell"),
    Pair("focus_chime.wav", "Warm chime"),
    Pair("focus_digital.wav", "Digital alert")
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    onNavigateToBlocking: () -> Unit = {},
    onNavigateToUsageLimits: () -> Unit = {},
    onNavigateToSchedules: () -> Unit = {},
    onNavigateToExams: () -> Unit = {},
    onNavigateToFocusPresets: () -> Unit = {},
    onNavigateToBackupRestore: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { GoBackDatabase.getDatabase(context) }

    var profileName by remember { mutableStateOf("") }
    var profileImageUri by remember { mutableStateOf<String?>(null) }
    var dailyGoalMinutes by remember { mutableStateOf(120) }
    var selectedThemeMode by remember { mutableStateOf("system") }
    var selectedPaletteName by remember { mutableStateOf("Forest") }
    var selectedAlarmSound by remember { mutableStateOf("default") }
    var storedPinHash by remember { mutableStateOf<String?>(null) }
    var feedbackMessage by remember { mutableStateOf<String?>(null) }

    // PIN modal
    var pinModalVisible by remember { mutableStateOf(false) }
    var pinModalMode by remember { mutableStateOf(PinModalMode.SET) }

    LaunchedEffect(Unit) {
        profileName = db.settingDao().getValue("profile.name") ?: ""
        profileImageUri = db.settingDao().getValue("profile.imageUri")
        dailyGoalMinutes = db.settingDao().getValue("focus.dailyGoalMinutes")?.toIntOrNull() ?: 120
        selectedThemeMode = db.settingDao().getValue("theme.mode") ?: "system"
        selectedPaletteName = db.settingDao().getValue("theme.palette") ?: "Forest"
        selectedAlarmSound = db.settingDao().getValue("focus.alarmSound") ?: "default"
        val alarmSoundKey = context.getSharedPreferences("go_back_preferences", Context.MODE_PRIVATE)
            .getString("focus_alarm_sound", selectedAlarmSound) ?: selectedAlarmSound
        selectedAlarmSound = alarmSoundKey
        context.getSharedPreferences("go_back_preferences", Context.MODE_PRIVATE)
            .edit().putString("focus_alarm_sound", alarmSoundKey).apply()
        GoBackApplication.ensureFocusAlarmChannel(context, alarmSoundKey)
        storedPinHash = db.settingDao().getValue(SecurityPinUtil.PIN_SETTING_KEY)
    }

    val profilePhotoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            scope.launch {
                runCatching {
                    db.settingDao().setValue(SettingEntity("profile.imageUri", uri.toString()))
                }.onSuccess {
                    profileImageUri = uri.toString()
                    feedbackMessage = "Profile photo saved on this device."
                }.onFailure {
                    feedbackMessage = "Profile photo could not be saved."
                }
            }
        }
    }

    fun playPreviewSound(soundKey: String) {
        try {
            val resName = soundKey.removeSuffix(".wav").lowercase()
            val resId = context.resources.getIdentifier(resName, "raw", context.packageName)
            if (resId != 0) {
                val mp = MediaPlayer.create(context, resId)
                mp?.setOnCompletionListener { it.release() }
                mp?.start()
            } else {
                val ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                val ringtone = RingtoneManager.getRingtone(context, ringtoneUri)
                ringtone?.play()
            }
            feedbackMessage = "Playing sound preview"
        } catch (_: Exception) {
            val ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val ringtone = RingtoneManager.getRingtone(context, ringtoneUri)
            ringtone?.play()
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        contentPadding = PaddingValues(bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column {
                Text(
                    text = "PREFERENCES & PRIVACY",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.6.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Settings",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        // Local-First Badge
        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.surfaceVariant, CardShape),
                shape = CardShape,
                color = MaterialTheme.colorScheme.surface
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Security,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(26.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "100% Local-First by Default",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Your syllabus, notes, focus history, and app rules never leave your device. Zero cloud tracking.",
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Feature Navigation Hub
        item {
            Text("Distraction & Routine Controls", fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Spacer(modifier = Modifier.height(6.dp))
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.surfaceVariant, CardShape),
                shape = CardShape,
                color = MaterialTheme.colorScheme.surface
            ) {
                Column {
                    SettingsNavRow(
                        icon = Icons.Filled.Shield,
                        title = "App & Content Blocking",
                        desc = "Whole-app blocker, YouTube Shorts, Reels, Spotlight & adult sites",
                        onClick = onNavigateToBlocking
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                    SettingsNavRow(
                        icon = Icons.Filled.HourglassBottom,
                        title = "Daily Screen-Time Limits",
                        desc = "App quotas with advance alerts at 80% and blocking at 100%",
                        onClick = onNavigateToUsageLimits
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                    SettingsNavRow(
                        icon = Icons.Filled.CalendarMonth,
                        title = "Weekly Focus Schedules",
                        desc = "Recurring weekly study blocks and routine pre-reminders",
                        onClick = onNavigateToSchedules
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                    SettingsNavRow(
                        icon = Icons.Filled.School,
                        title = "Exam Timetable Planner",
                        desc = "Countdown to exams with 3-stage reminders",
                        onClick = onNavigateToExams
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                    SettingsNavRow(
                        icon = Icons.Filled.Tune,
                        title = "Focus Presets",
                        desc = "Save reusable timer modes and temporary app blocks",
                        onClick = onNavigateToFocusPresets
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                    SettingsNavRow(
                        icon = Icons.Filled.Backup,
                        title = "Backup & Restore",
                        desc = "Export your local data or restore a Go Back backup",
                        onClick = onNavigateToBackupRestore
                    )
                }
            }
        }

        // Finish Alarm Sound Section
        item {
            Text("Focus Finish Sound", fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Spacer(modifier = Modifier.height(6.dp))
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.surfaceVariant, CardShape),
                shape = CardShape,
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Sound played when a focus timer or Pomodoro phase completes.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    FINISH_ALARM_SOUNDS.forEach { (key, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedAlarmSound = key
                                    scope.launch {
                                        db.settingDao().setValue(SettingEntity("focus.alarmSound", key))
                                    }
                                    context.getSharedPreferences("go_back_preferences", Context.MODE_PRIVATE)
                                        .edit().putString("focus_alarm_sound", key).apply()
                                    GoBackApplication.ensureFocusAlarmChannel(context, key)
                                    playPreviewSound(key)
                                }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedAlarmSound == key,
                                onClick = {
                                    selectedAlarmSound = key
                                    scope.launch {
                                        db.settingDao().setValue(SettingEntity("focus.alarmSound", key))
                                    }
                                    playPreviewSound(key)
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            IconButton(onClick = { playPreviewSound(key) }) {
                                Icon(Icons.Filled.VolumeUp, contentDescription = "Preview", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }

        // Daily Focus Goal Section
        item {
            Text("Daily Focus Goal", fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Spacer(modifier = Modifier.height(6.dp))
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.surfaceVariant, CardShape),
                shape = CardShape,
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Current Goal", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("${dailyGoalMinutes / 60}h ${dailyGoalMinutes % 60}m ($dailyGoalMinutes min)", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        listOf(30, 60, 90, 120, 180, 240).forEach { goal ->
                            FilterChip(
                                selected = dailyGoalMinutes == goal,
                                onClick = {
                                    dailyGoalMinutes = goal
                                    scope.launch {
                                        db.settingDao().setValue(SettingEntity("focus.dailyGoalMinutes", goal.toString()))
                                    }
                                },
                                label = { Text("${goal}m") }
                            )
                        }
                    }
                }
            }
        }

        // Appearance & Theme Studio
        item {
            Text("Appearance & Theme Studio", fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Spacer(modifier = Modifier.height(6.dp))
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.surfaceVariant, CardShape),
                shape = CardShape,
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Theme Mode", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        listOf(
                            Triple("system", "System", Icons.Filled.PhoneAndroid),
                            Triple("light", "Light", Icons.Filled.LightMode),
                            Triple("dark", "Dark", Icons.Filled.DarkMode)
                        ).forEach { (mode, label, icon) ->
                            FilterChip(
                                selected = selectedThemeMode == mode,
                                onClick = {
                                    selectedThemeMode = mode
                                    scope.launch { db.settingDao().setValue(SettingEntity("theme.mode", mode)) }
                                },
                                label = { Text(label) },
                                leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Accent Color Palette", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        THEME_PALETTES.forEach { pal ->
                            val isSelected = selectedPaletteName == pal.name
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier
                                    .clickable {
                                        selectedPaletteName = pal.name
                                        scope.launch { db.settingDao().setValue(SettingEntity("theme.palette", pal.name)) }
                                    }
                                    .border(
                                        width = if (isSelected) 2.dp else 1.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                        shape = RoundedCornerShape(12.dp)
                                    )
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(modifier = Modifier.size(16.dp).clip(CircleShape).background(pal.color))
                                    Text(pal.name, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Security PIN Card
        item {
            Text("Security PIN Protection", fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Spacer(modifier = Modifier.height(6.dp))
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.surfaceVariant, CardShape),
                shape = CardShape,
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(
                            imageVector = if (!storedPinHash.isNullOrEmpty()) Icons.Filled.Lock else Icons.Filled.LockOpen,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                if (!storedPinHash.isNullOrEmpty()) "PIN Protection Active" else "PIN Protection Inactive",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Text(
                                if (!storedPinHash.isNullOrEmpty()) "Protects App Blocking & Usage Limits against impulse modifications."
                                else "Prevent yourself from turning off blockers or extending limits in weak moments.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (storedPinHash.isNullOrEmpty()) {
                            Button(
                                onClick = { pinModalMode = PinModalMode.SET; pinModalVisible = true },
                                shape = PillShape
                            ) {
                                Text("Set 4-Digit PIN")
                            }
                        } else {
                            Button(
                                onClick = { pinModalMode = PinModalMode.SET; pinModalVisible = true },
                                shape = PillShape
                            ) {
                                Text("Change PIN")
                            }
                            OutlinedButton(
                                onClick = { pinModalMode = PinModalMode.REMOVE; pinModalVisible = true },
                                shape = PillShape
                            ) {
                                Text("Remove PIN", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }

        // Quick Settings Tile Shortcut
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.surfaceVariant, CardShape),
                    shape = CardShape,
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Icon(Icons.Filled.Grid3x3, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Quick Settings Focus Tile", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("Start and stop focus sessions right from the Android pull-down notification shade.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Button(
                            onClick = {
                                val statusBarManager = context.getSystemService(StatusBarManager::class.java)
                                val executor = ContextCompat.getMainExecutor(context)
                                statusBarManager?.requestAddTileService(
                                    ComponentName(context, FocusTileService::class.java),
                                    "Go Back Focus",
                                    android.graphics.drawable.Icon.createWithResource(context, R.drawable.ic_focus_tile),
                                    executor,
                                    { /* Consumer */ }
                                )
                            },
                            shape = PillShape
                        ) {
                            Text("Add Tile")
                        }
                    }
                }
            }
        }

        // Local profile
        item {
            Text("Local Profile", fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Spacer(modifier = Modifier.height(6.dp))
            Surface(
                modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.surfaceVariant, CardShape),
                shape = CardShape,
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        ProfileAvatar(name = profileName, imageUri = profileImageUri, modifier = Modifier.size(64.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Profile photo", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            OutlinedButton(
                                onClick = { profilePhotoPicker.launch(arrayOf("image/*")) },
                                shape = PillShape
                            ) {
                                Text(if (profileImageUri.isNullOrBlank()) "Choose photo" else "Change photo")
                            }
                        }
                    }
                    Text("Display Name", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = profileName,
                            onValueChange = { profileName = it.take(60) },
                            placeholder = { Text("Your name (optional)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp)
                        )
                        Button(
                            onClick = {
                                scope.launch {
                                    db.settingDao().setValue(SettingEntity("profile.name", profileName.trim()))
                                    feedbackMessage = "Profile name saved."
                                }
                            },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.height(52.dp)
                        ) {
                            Text("Save")
                        }
                    }
                }
            }
        }

        // Feedback Banner
        if (feedbackMessage != null) {
            item {
                Text(
                    text = feedbackMessage.orEmpty(),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }
    }

    // Security PIN Dialog
    if (pinModalVisible) {
        SecurityPinDialog(
            mode = pinModalMode,
            storedPinHash = storedPinHash,
            onSuccess = { newPin ->
                pinModalVisible = false
                if (pinModalMode == PinModalMode.SET && newPin != null) {
                    scope.launch {
                        val hashed = SecurityPinUtil.hashPin(newPin)
                        db.settingDao().setValue(SettingEntity(SecurityPinUtil.PIN_SETTING_KEY, hashed))
                        storedPinHash = hashed
                        feedbackMessage = "Security PIN successfully updated."
                    }
                } else if (pinModalMode == PinModalMode.REMOVE) {
                    scope.launch {
                        db.settingDao().delete(SecurityPinUtil.PIN_SETTING_KEY)
                        storedPinHash = null
                        feedbackMessage = "Security PIN removed."
                    }
                }
            },
            onDismiss = { pinModalVisible = false }
        )
    }
}

@Composable
fun SettingsNavRow(
    icon: ImageVector,
    title: String,
    desc: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Text(desc, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(
            imageVector = Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
