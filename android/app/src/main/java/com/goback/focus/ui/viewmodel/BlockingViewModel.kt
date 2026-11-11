package com.goback.focus.ui.viewmodel

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.goback.focus.GoBackApplication
import com.goback.focus.data.entity.SettingEntity
import com.goback.focus.ui.components.PinModalMode
import com.goback.focus.ui.components.SecurityPinUtil
import com.goback.focus.ui.screens.BlockingConfigState
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
import java.util.Locale

class BlockingViewModel(application: Application) : AndroidViewModel(application) {

    private val db = (application as GoBackApplication).database
    private val prefs = application.getSharedPreferences("go_back_blocking", Context.MODE_PRIVATE)

    private val _config = MutableStateFlow(BlockingConfigState())
    val config: StateFlow<BlockingConfigState> = _config.asStateFlow()

    private val _installedApps = MutableStateFlow<List<LaunchableApp>>(emptyList())
    val installedApps: StateFlow<List<LaunchableApp>> = _installedApps.asStateFlow()

    private val _appSearchQuery = MutableStateFlow("")
    val appSearchQuery: StateFlow<String> = _appSearchQuery.asStateFlow()

    private val _showSystemApps = MutableStateFlow(false)
    val showSystemApps: StateFlow<Boolean> = _showSystemApps.asStateFlow()

    private val _selectedOnly = MutableStateFlow(false)
    val selectedOnly: StateFlow<Boolean> = _selectedOnly.asStateFlow()

    private val _channelInputText = MutableStateFlow("")
    val channelInputText: StateFlow<String> = _channelInputText.asStateFlow()

    private val _isAccessibilityEnabled = MutableStateFlow(false)
    val isAccessibilityEnabled: StateFlow<Boolean> = _isAccessibilityEnabled.asStateFlow()

    private val _lastActionDiagnostic = MutableStateFlow("")
    val lastActionDiagnostic: StateFlow<String> = _lastActionDiagnostic.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _hasUnsavedChanges = MutableStateFlow(false)
    val hasUnsavedChanges: StateFlow<Boolean> = _hasUnsavedChanges.asStateFlow()

    private val _storedPinHash = MutableStateFlow<String?>(null)
    val storedPinHash: StateFlow<String?> = _storedPinHash.asStateFlow()

    private val _isPinUnlocked = MutableStateFlow(false)
    val isPinUnlocked: StateFlow<Boolean> = _isPinUnlocked.asStateFlow()

    private val _pinModalVisible = MutableStateFlow(false)
    val pinModalVisible: StateFlow<Boolean> = _pinModalVisible.asStateFlow()

    private val _pinModalMode = MutableStateFlow(PinModalMode.VERIFY)
    val pinModalMode: StateFlow<PinModalMode> = _pinModalMode.asStateFlow()

    val filteredApps: StateFlow<List<LaunchableApp>> = combine(
        _installedApps,
        _appSearchQuery,
        _showSystemApps,
        _selectedOnly,
        _config
    ) { apps, query, system, selected, cfg ->
        val cleanQuery = query.trim()
        apps.filter { app ->
            val matchesQuery = cleanQuery.isEmpty() ||
                    app.appName.contains(cleanQuery, ignoreCase = true) ||
                    app.packageName.contains(cleanQuery, ignoreCase = true)
            val matchesSystem = system || !app.isSystemApp
            val matchesSelected = !selected || cfg.blockedPackages.contains(app.packageName)
            matchesQuery && matchesSystem && matchesSelected
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    init {
        loadConfiguration()
        checkPin()
        loadInstalledApps()
    }

    fun checkAccessibilityStatus(): Boolean {
        val context = getApplication<Application>()
        return try {
            val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
            val enabledViaList = am?.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)?.any {
                it.resolveInfo.serviceInfo.packageName == context.packageName &&
                        it.resolveInfo.serviceInfo.name.endsWith("GoBackBlockingService")
            } ?: false
            val enabledServices = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
            val enabledViaSettings = enabledServices.split(':').any {
                it.contains("GoBackBlockingService", ignoreCase = true) && it.contains(context.packageName, ignoreCase = true)
            }
            enabledViaList || enabledViaSettings
        } catch (_: Exception) {
            false
        }
    }

    fun loadConfiguration() {
        val blocked = prefs.getStringSet("blocked_packages", emptySet()) ?: emptySet()
        val channels = prefs.getStringSet("youtube_allowed_channels", emptySet()) ?: emptySet()
        val reason = prefs.getString("last_action_reason", "").orEmpty()

        _config.value = BlockingConfigState(
            blockOnlyDuringFocus = prefs.getBoolean("block_only_during_focus", false),
            strictMode = prefs.getBoolean("strict_mode_active", false),
            strictUninstallProtection = prefs.getBoolean("strict_uninstall_protection", false),
            blockSplitScreen = prefs.getBoolean("block_split_screen", false),
            blockShorts = prefs.getBoolean("block_shorts", false),
            blockReels = prefs.getBoolean("block_reels", false),
            blockSpotlight = prefs.getBoolean("block_spotlight", false),
            blockAdultSites = prefs.getBoolean("block_adult", false),
            blockYoutube = prefs.getBoolean("youtube_study_mode", false),
            allowedChannels = channels,
            blockedPackages = blocked
        )
        _isAccessibilityEnabled.value = checkAccessibilityStatus()
        _lastActionDiagnostic.value = reason
        _hasUnsavedChanges.value = false
    }

    private fun checkPin() {
        viewModelScope.launch {
            val pin = db.settingDao().getValue(SecurityPinUtil.PIN_SETTING_KEY)
            _storedPinHash.value = pin
            if (!pin.isNullOrEmpty() && !_isPinUnlocked.value) {
                _pinModalMode.value = PinModalMode.VERIFY
                _pinModalVisible.value = true
            } else {
                _isPinUnlocked.value = true
            }
        }
    }

    private fun loadInstalledApps() {
        viewModelScope.launch {
            val context = getApplication<Application>()
            val list = withContext(Dispatchers.IO) {
                val pm = context.packageManager
                val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                runCatching {
                    pm.queryIntentActivities(intent, 0).mapNotNull { resolve ->
                        val appInfo = resolve.activityInfo?.applicationInfo ?: return@mapNotNull null
                        if (appInfo.packageName == context.packageName) return@mapNotNull null
                        val label = resolve.loadLabel(pm)?.toString()?.trim().orEmpty()
                        if (label.isEmpty()) return@mapNotNull null
                        val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                        LaunchableApp(appInfo.packageName, label, isSystem)
                    }.distinctBy { it.packageName }.sortedBy { it.appName.lowercase(Locale.ROOT) }
                }.getOrDefault(emptyList())
            }
            _installedApps.value = list
        }
    }

    fun setAppSearchQuery(query: String) {
        _appSearchQuery.value = query
    }

    fun setShowSystemApps(show: Boolean) {
        _showSystemApps.value = show
    }

    fun setSelectedOnly(selected: Boolean) {
        _selectedOnly.value = selected
    }

    fun setChannelInputText(text: String) {
        _channelInputText.value = text
    }

    fun updateConfig(transform: (BlockingConfigState) -> BlockingConfigState) {
        _config.value = transform(_config.value)
        _hasUnsavedChanges.value = true
        saveRules()
    }

    fun addChannel() {
        val clean = _channelInputText.value.trim().replace("\\s+".toRegex(), " ")
        if (clean.isEmpty()) return
        if (clean.length > 80) {
            _statusMessage.value = "Channel name is too long (max 80 chars)."
            return
        }
        if (_config.value.allowedChannels.size >= 50) {
            _statusMessage.value = "Allowlist is full (max 50 channels)."
            return
        }
        _config.value = _config.value.copy(allowedChannels = _config.value.allowedChannels + clean)
        _channelInputText.value = ""
        _hasUnsavedChanges.value = true
        saveRules()
    }

    fun removeChannel(channel: String) {
        _config.value = _config.value.copy(allowedChannels = _config.value.allowedChannels - channel)
        _hasUnsavedChanges.value = true
        saveRules()
    }

    fun toggleBlockedPackage(pkg: String) {
        val current = _config.value.blockedPackages
        if (current.contains(pkg)) {
            _config.value = _config.value.copy(blockedPackages = current - pkg)
            _hasUnsavedChanges.value = true
            saveRules()
        } else {
            if (current.size >= 100) {
                _statusMessage.value = "Whole-app block limit reached (max 100 apps)."
                return
            }
            _config.value = _config.value.copy(blockedPackages = current + pkg)
            _hasUnsavedChanges.value = true
            saveRules()
        }
    }

    fun saveRules() {
        viewModelScope.launch {
            val cfg = _config.value
            val jsonConfig = JSONObject().apply {
                put("blockYoutube", cfg.blockYoutube)
                put("blockShorts", cfg.blockShorts)
                put("blockReels", cfg.blockReels)
                put("blockSpotlight", cfg.blockSpotlight)
                put("blockAdultSites", cfg.blockAdultSites)
                put("blockSplitScreen", cfg.blockSplitScreen)
                put("strictUninstallProtection", cfg.strictUninstallProtection)
                put("strictMode", cfg.strictMode)
                put("blockOnlyDuringFocus", cfg.blockOnlyDuringFocus)
                put("allowedChannels", JSONArray(cfg.allowedChannels))
                put("blockedPackages", JSONArray(cfg.blockedPackages))
            }

            prefs.edit()
                .putStringSet("blocked_packages", cfg.blockedPackages)
                .putStringSet("youtube_allowed_channels", cfg.allowedChannels)
                .putBoolean("block_only_during_focus", cfg.blockOnlyDuringFocus)
                .putBoolean("strict_mode_active", cfg.strictMode)
                .putBoolean("strict_uninstall_protection", cfg.strictUninstallProtection)
                .putBoolean("block_split_screen", cfg.blockSplitScreen)
                .putBoolean("block_adult", cfg.blockAdultSites)
                .putBoolean("block_shorts", cfg.blockShorts)
                .putBoolean("youtube_study_mode", cfg.blockYoutube)
                .putBoolean("block_reels", cfg.blockReels)
                .putBoolean("block_spotlight", cfg.blockSpotlight)
                .putString("config", jsonConfig.toString())
                .apply()

            _hasUnsavedChanges.value = false
            _isAccessibilityEnabled.value = checkAccessibilityStatus()
            _statusMessage.value = if (_isAccessibilityEnabled.value) "Blocking rules saved & actively enforced."
            else "Rules saved. Enable Go Back in Accessibility settings to activate."
        }
    }

    fun setPinModalVisible(visible: Boolean) {
        _pinModalVisible.value = visible
    }

    fun setPinModalMode(mode: PinModalMode) {
        _pinModalMode.value = mode
    }

    fun onPinSuccess(newPin: String?) {
        _pinModalVisible.value = false
        _isPinUnlocked.value = true
        val currentMode = _pinModalMode.value
        if (currentMode == PinModalMode.SET && newPin != null) {
            viewModelScope.launch {
                val hashed = SecurityPinUtil.hashPin(newPin)
                db.settingDao().setValue(SettingEntity(SecurityPinUtil.PIN_SETTING_KEY, hashed))
                _storedPinHash.value = hashed
                _statusMessage.value = "Security PIN enabled."
            }
        } else if (currentMode == PinModalMode.REMOVE) {
            viewModelScope.launch {
                db.settingDao().delete(SecurityPinUtil.PIN_SETTING_KEY)
                _storedPinHash.value = null
                _statusMessage.value = "Security PIN removed."
            }
        }
    }

    fun onPinDismiss(onCancelNavigateBack: () -> Unit) {
        _pinModalVisible.value = false
        if (!_isPinUnlocked.value) {
            onCancelNavigateBack()
        }
    }
}
