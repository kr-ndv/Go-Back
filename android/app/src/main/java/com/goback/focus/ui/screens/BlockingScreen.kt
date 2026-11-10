package com.goback.focus.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.goback.focus.ui.components.PinModalMode
import com.goback.focus.ui.components.SecurityPinDialog
import com.goback.focus.ui.theme.CardShape
import com.goback.focus.ui.theme.ChipShape
import com.goback.focus.ui.theme.PillShape
import com.goback.focus.ui.viewmodel.BlockingViewModel

data class LaunchableApp(
    val packageName: String,
    val appName: String,
    val isSystemApp: Boolean
)

data class BlockingConfigState(
    val blockOnlyDuringFocus: Boolean = false,
    val strictMode: Boolean = false,
    val strictUninstallProtection: Boolean = false,
    val blockSplitScreen: Boolean = false,
    val blockShorts: Boolean = false,
    val blockReels: Boolean = false,
    val blockSpotlight: Boolean = false,
    val blockAdultSites: Boolean = false,
    val blockYoutube: Boolean = false,
    val allowedChannels: Set<String> = emptySet(),
    val blockedPackages: Set<String> = emptySet()
)

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun BlockingScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BlockingViewModel = viewModel()
) {
    val context = LocalContext.current

    val config by viewModel.config.collectAsStateWithLifecycle()
    val installedApps by viewModel.installedApps.collectAsStateWithLifecycle()
    val appSearchQuery by viewModel.appSearchQuery.collectAsStateWithLifecycle()
    val showSystemApps by viewModel.showSystemApps.collectAsStateWithLifecycle()
    val selectedOnly by viewModel.selectedOnly.collectAsStateWithLifecycle()
    val channelInputText by viewModel.channelInputText.collectAsStateWithLifecycle()
    val isAccessibilityEnabled by viewModel.isAccessibilityEnabled.collectAsStateWithLifecycle()
    val lastActionDiagnostic by viewModel.lastActionDiagnostic.collectAsStateWithLifecycle()
    val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()
    val hasUnsavedChanges by viewModel.hasUnsavedChanges.collectAsStateWithLifecycle()
    val storedPinHash by viewModel.storedPinHash.collectAsStateWithLifecycle()
    val isPinUnlocked by viewModel.isPinUnlocked.collectAsStateWithLifecycle()
    val pinModalVisible by viewModel.pinModalVisible.collectAsStateWithLifecycle()
    val pinModalMode by viewModel.pinModalMode.collectAsStateWithLifecycle()
    val filteredApps by viewModel.filteredApps.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("App & Content Blocking", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Go Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.loadConfiguration() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh Status")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp),
            contentPadding = PaddingValues(bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Status Header
            item {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    Text(
                        text = "DISTRACTION CONTROL",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.6.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Guard your focus.",
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // Accessibility Status Card
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.surfaceVariant, CardShape),
                    shape = CardShape,
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(14.dp)
                                    .clip(CircleShape)
                                    .background(if (isAccessibilityEnabled) Color(0xFF34D399) else Color(0xFFF59E0B))
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (isAccessibilityEnabled) "Accessibility Service Active" else "Accessibility Service Disabled",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (isAccessibilityEnabled) "Distraction rules and app blocking are actively enforced."
                                    else "Blocking cannot trigger until Go Back is enabled in Android Accessibility settings.",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        if (!isAccessibilityEnabled) {
                            Button(
                                onClick = {
                                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                },
                                shape = PillShape,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Filled.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Open Accessibility Settings", fontWeight = FontWeight.Bold)
                            }
                        }

                        if (lastActionDiagnostic.isNotEmpty()) {
                            Text(
                                text = "Last action: $lastActionDiagnostic",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            // General Enforcement Card
            item {
                Text("General Enforcement", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.surfaceVariant, CardShape),
                    shape = CardShape,
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        ToggleRow(
                            title = "Only block during focus sessions",
                            detail = "Enforce blocking rules only while a focus countdown or stopwatch is active.",
                            value = config.blockOnlyDuringFocus,
                            onValueChange = { checked -> viewModel.updateConfig { it.copy(blockOnlyDuringFocus = checked) } }
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                        ToggleRow(
                            title = "Strict Mode (Challenge to exit)",
                            detail = "Requires typing commitment phrase 'I choose to break my focus' before canceling early.",
                            value = config.strictMode,
                            onValueChange = { checked -> viewModel.updateConfig { it.copy(strictMode = checked) } }
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                        ToggleRow(
                            title = "Settings & Uninstall Protection",
                            detail = "Prevents opening Android Settings to force-stop or uninstall Go Back while active.",
                            value = config.strictUninstallProtection,
                            onValueChange = { checked -> viewModel.updateConfig { it.copy(strictUninstallProtection = checked) } }
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                        ToggleRow(
                            title = "Block Floating & Split-Screen Windows",
                            detail = "Immediately terminates blocked apps if launched in split-screen or multi-window mode.",
                            value = config.blockSplitScreen,
                            onValueChange = { checked -> viewModel.updateConfig { it.copy(blockSplitScreen = checked) } }
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

                        // PIN Lock trigger row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setPinModalMode(if (storedPinHash.isNullOrEmpty()) PinModalMode.SET else PinModalMode.REMOVE)
                                    viewModel.setPinModalVisible(true)
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Self-Control PIN Lock", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text(
                                    if (!storedPinHash.isNullOrEmpty()) "PIN protection active · Tap to change or remove"
                                    else "Require a 4-digit PIN to modify blocking settings",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(
                                imageVector = if (!storedPinHash.isNullOrEmpty()) Icons.Filled.Lock else Icons.Filled.LockOpen,
                                contentDescription = null,
                                tint = if (!storedPinHash.isNullOrEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Granular Content Blocking Card
            item {
                Text("Short-Form & Adult Content", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.surfaceVariant, CardShape),
                    shape = CardShape,
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        ToggleRow(
                            title = "Block YouTube Shorts",
                            detail = "Automatically navigates Back when the Shorts feed or tab is detected in YouTube.",
                            value = config.blockShorts,
                            onValueChange = { checked -> viewModel.updateConfig { it.copy(blockShorts = checked) } }
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                        ToggleRow(
                            title = "Block Instagram Reels",
                            detail = "Closes the Reels surface immediately upon detection in Instagram.",
                            value = config.blockReels,
                            onValueChange = { checked -> viewModel.updateConfig { it.copy(blockReels = checked) } }
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                        ToggleRow(
                            title = "Block Snapchat Spotlight",
                            detail = "Navigates away from Spotlight videos inside Snapchat.",
                            value = config.blockSpotlight,
                            onValueChange = { checked -> viewModel.updateConfig { it.copy(blockSpotlight = checked) } }
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                        ToggleRow(
                            title = "Block Adult Websites",
                            detail = "Monitors browser address bars across Chrome, Firefox, Edge, Brave, Samsung Internet & DuckDuckGo.",
                            value = config.blockAdultSites,
                            onValueChange = { checked -> viewModel.updateConfig { it.copy(blockAdultSites = checked) } }
                        )
                    }
                }
            }

            // YouTube Study Mode
            item {
                Text("YouTube Study Mode", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.surfaceVariant, CardShape),
                    shape = CardShape,
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        ToggleRow(
                            title = "Limit YouTube to Allowed Channels",
                            detail = "Dismisses video player and miniplayers when watching channels not on your study allowlist.",
                            value = config.blockYoutube,
                            onValueChange = { checked -> viewModel.updateConfig { it.copy(blockYoutube = checked) } }
                        )

                        AnimatedVisibility(visible = config.blockYoutube) {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("Allowed Study Channels (Max 50)", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedTextField(
                                        value = channelInputText,
                                        onValueChange = { viewModel.setChannelInputText(it) },
                                        placeholder = { Text("@handle or channel name", fontSize = 13.sp) },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(14.dp)
                                    )
                                    Button(
                                        onClick = { viewModel.addChannel() },
                                        shape = RoundedCornerShape(14.dp),
                                        modifier = Modifier.height(52.dp)
                                    ) {
                                        Icon(Icons.Filled.Add, contentDescription = "Add Channel")
                                    }
                                }

                                if (config.allowedChannels.isNotEmpty()) {
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        config.allowedChannels.forEach { channel ->
                                            InputChip(
                                                selected = true,
                                                onClick = { viewModel.removeChannel(channel) },
                                                label = { Text(channel, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                                trailingIcon = { Icon(Icons.Filled.Close, contentDescription = "Remove", modifier = Modifier.size(16.dp)) },
                                                shape = ChipShape
                                            )
                                        }
                                    }
                                } else {
                                    Text(
                                        "No allowed channels added yet. Videos from all channels will be blocked until allowlisted.",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Whole-App Blocking Section
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Installed Apps Picker", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Text(
                        "${config.blockedPackages.size}/100 selected",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Choose apps to block. Whenever launched, Go Back immediately closes them and returns to your launcher home screen.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Search Box
                OutlinedTextField(
                    value = appSearchQuery,
                    onValueChange = { viewModel.setAppSearchQuery(it) },
                    placeholder = { Text("Search apps by name or package…", fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        if (appSearchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.setAppSearchQuery("") }) {
                                Icon(Icons.Filled.Clear, contentDescription = "Clear search")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Filter Chips
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = selectedOnly,
                        onClick = { viewModel.setSelectedOnly(!selectedOnly) },
                        label = { Text("Selected (${config.blockedPackages.size})") }
                    )
                    FilterChip(
                        selected = showSystemApps,
                        onClick = { viewModel.setShowSystemApps(!showSystemApps) },
                        label = { Text("Include System Apps") }
                    )
                }
            }

            // App Items
            if (filteredApps.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (installedApps.isEmpty()) "Loading installed applications…" else "No matching apps found.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp
                        )
                    }
                }
            } else {
                items(filteredApps, key = { it.packageName }) { app ->
                    val isBlocked = config.blockedPackages.contains(app.packageName)
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, MaterialTheme.colorScheme.surfaceVariant, CardShape),
                        shape = CardShape,
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.toggleBlockedPackage(app.packageName) }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (isBlocked) MaterialTheme.colorScheme.primaryContainer
                                        else MaterialTheme.colorScheme.surfaceVariant
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.PhoneAndroid,
                                    contentDescription = null,
                                    tint = if (isBlocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = app.appName,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = app.packageName,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Switch(
                                checked = isBlocked,
                                onCheckedChange = { viewModel.toggleBlockedPackage(app.packageName) }
                            )
                        }
                    }
                }
            }

            // Save Rules & Status feedback
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { viewModel.saveRules() },
                    shape = PillShape,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    Icon(Icons.Filled.Shield, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (hasUnsavedChanges) "Save Blocking Rules (Changes Pending)" else "Blocking Rules Saved",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }

                if (statusMessage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = statusMessage.orEmpty(),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
            }
        }
    }

    // Security PIN Dialog
    if (pinModalVisible) {
        SecurityPinDialog(
            mode = pinModalMode,
            storedPinHash = storedPinHash,
            onSuccess = { newPin ->
                viewModel.onPinSuccess(newPin)
            },
            onDismiss = {
                viewModel.onPinDismiss(onNavigateBack)
            }
        )
    }
}

@Composable
fun ToggleRow(
    title: String,
    detail: String,
    value: Boolean,
    onValueChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onValueChange(!value) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = title, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(text = detail, fontSize = 12.sp, lineHeight = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(checked = value, onCheckedChange = onValueChange)
    }
}
