package com.goback.focus.ui.viewmodel

import android.app.Application
import android.content.Context
import android.media.MediaPlayer
import android.media.RingtoneManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.goback.focus.GoBackApplication
import com.goback.focus.data.entity.SettingEntity
import com.goback.focus.ui.components.PinModalMode
import com.goback.focus.ui.components.SecurityPinUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val db = (application as GoBackApplication).database
    private val prefs = application.getSharedPreferences("go_back_preferences", Context.MODE_PRIVATE)

    private val _profileName = MutableStateFlow("")
    val profileName: StateFlow<String> = _profileName.asStateFlow()

    private val _profileImageUri = MutableStateFlow<String?>(null)
    val profileImageUri: StateFlow<String?> = _profileImageUri.asStateFlow()

    private val _dailyGoalMinutes = MutableStateFlow(120)
    val dailyGoalMinutes: StateFlow<Int> = _dailyGoalMinutes.asStateFlow()

    private val _selectedThemeMode = MutableStateFlow("system")
    val selectedThemeMode: StateFlow<String> = _selectedThemeMode.asStateFlow()

    private val _selectedPaletteName = MutableStateFlow("Forest")
    val selectedPaletteName: StateFlow<String> = _selectedPaletteName.asStateFlow()

    private val _selectedAlarmSound = MutableStateFlow("default")
    val selectedAlarmSound: StateFlow<String> = _selectedAlarmSound.asStateFlow()

    private val _storedPinHash = MutableStateFlow<String?>(null)
    val storedPinHash: StateFlow<String?> = _storedPinHash.asStateFlow()

    private val _feedbackMessage = MutableStateFlow<String?>(null)
    val feedbackMessage: StateFlow<String?> = _feedbackMessage.asStateFlow()

    private val _pinModalVisible = MutableStateFlow(false)
    val pinModalVisible: StateFlow<Boolean> = _pinModalVisible.asStateFlow()

    private val _pinModalMode = MutableStateFlow(PinModalMode.SET)
    val pinModalMode: StateFlow<PinModalMode> = _pinModalMode.asStateFlow()

    private var activeMediaPlayer: MediaPlayer? = null

    init {
        loadSettings()
    }

    fun loadSettings() {
        viewModelScope.launch {
            _profileName.value = db.settingDao().getValue("profile.name") ?: ""
            _profileImageUri.value = db.settingDao().getValue("profile.imageUri")
            _dailyGoalMinutes.value = db.settingDao().getValue("focus.dailyGoalMinutes")?.toIntOrNull() ?: 120
            _selectedThemeMode.value = db.settingDao().getValue("theme.mode") ?: "system"
            _selectedPaletteName.value = db.settingDao().getValue("theme.palette") ?: "Forest"

            val dbAlarm = db.settingDao().getValue("focus.alarmSound") ?: "default"
            val alarmSoundKey = prefs.getString("focus_alarm_sound", dbAlarm) ?: dbAlarm
            _selectedAlarmSound.value = alarmSoundKey
            prefs.edit().putString("focus_alarm_sound", alarmSoundKey).apply()
            GoBackApplication.ensureFocusAlarmChannel(getApplication(), alarmSoundKey)

            _storedPinHash.value = db.settingDao().getValue(SecurityPinUtil.PIN_SETTING_KEY)
        }
    }

    fun setProfileName(name: String) {
        _profileName.value = name
    }

    fun saveProfileName() {
        viewModelScope.launch {
            val cleanName = _profileName.value.trim()
            db.settingDao().setValue(SettingEntity("profile.name", cleanName))
            _feedbackMessage.value = "Profile name saved."
        }
    }

    fun saveProfileImageUri(uriString: String) {
        viewModelScope.launch {
            runCatching {
                db.settingDao().setValue(SettingEntity("profile.imageUri", uriString))
            }.onSuccess {
                _profileImageUri.value = uriString
                _feedbackMessage.value = "Profile photo saved on this device."
            }.onFailure {
                _feedbackMessage.value = "Profile photo could not be saved."
            }
        }
    }

    fun setDailyGoalMinutes(minutes: Int) {
        _dailyGoalMinutes.value = minutes
        viewModelScope.launch {
            db.settingDao().setValue(SettingEntity("focus.dailyGoalMinutes", minutes.toString()))
        }
    }

    fun setThemeMode(mode: String) {
        _selectedThemeMode.value = mode
        getApplication<Application>().getSharedPreferences("go_back_preferences", Context.MODE_PRIVATE)
            .edit().putString("theme.mode", mode).apply()
        viewModelScope.launch {
            db.settingDao().setValue(SettingEntity("theme.mode", mode))
        }
    }

    fun setThemePalette(paletteName: String) {
        _selectedPaletteName.value = paletteName
        getApplication<Application>().getSharedPreferences("go_back_preferences", Context.MODE_PRIVATE)
            .edit().putString("theme.palette", paletteName).apply()
        viewModelScope.launch {
            db.settingDao().setValue(SettingEntity("theme.palette", paletteName))
        }
    }

    fun setAlarmSound(soundKey: String) {
        _selectedAlarmSound.value = soundKey
        viewModelScope.launch {
            db.settingDao().setValue(SettingEntity("focus.alarmSound", soundKey))
        }
        val app = getApplication<Application>()
        prefs.edit().putString("focus_alarm_sound", soundKey).apply()
        GoBackApplication.ensureFocusAlarmChannel(app, soundKey)
        playPreviewSound(soundKey)
    }

    fun playPreviewSound(soundKey: String) {
        val context = getApplication<Application>()
        try {
            activeMediaPlayer?.stop()
            activeMediaPlayer?.release()
            activeMediaPlayer = null

            val resName = soundKey.removeSuffix(".wav").lowercase()
            val resId = context.resources.getIdentifier(resName, "raw", context.packageName)
            if (resId != 0) {
                val mp = MediaPlayer.create(context, resId)
                mp?.setOnCompletionListener {
                    try { it.release() } catch (_: Exception) {}
                    if (activeMediaPlayer == it) activeMediaPlayer = null
                }
                activeMediaPlayer = mp
                mp?.start()
            } else {
                val ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                val ringtone = RingtoneManager.getRingtone(context, ringtoneUri)
                ringtone?.play()
            }
            _feedbackMessage.value = "Playing sound preview"
        } catch (_: Exception) {
            val ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val ringtone = RingtoneManager.getRingtone(context, ringtoneUri)
            ringtone?.play()
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
        val currentMode = _pinModalMode.value
        if (currentMode == PinModalMode.SET && newPin != null) {
            viewModelScope.launch {
                val hashed = SecurityPinUtil.hashPin(newPin)
                db.settingDao().setValue(SettingEntity(SecurityPinUtil.PIN_SETTING_KEY, hashed))
                _storedPinHash.value = hashed
                _feedbackMessage.value = "Security PIN successfully updated."
            }
        } else if (currentMode == PinModalMode.REMOVE) {
            viewModelScope.launch {
                db.settingDao().delete(SecurityPinUtil.PIN_SETTING_KEY)
                _storedPinHash.value = null
                _feedbackMessage.value = "Security PIN removed."
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        try {
            activeMediaPlayer?.stop()
            activeMediaPlayer?.release()
            activeMediaPlayer = null
        } catch (_: Exception) {}
    }
}
