package com.goback.focus.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey val id: String,
    val title: String,
    val body: String = "",
    @ColumnInfo(name = "updated_at") val updatedAt: String
)

@Entity(tableName = "subjects")
data class SubjectEntity(
    @PrimaryKey val id: String,
    val name: String,
    @ColumnInfo(name = "chapters_json") val chaptersJson: String = "[]",
    @ColumnInfo(name = "updated_at") val updatedAt: String
)

@Entity(tableName = "focus_sessions")
data class FocusSessionEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "started_at") val startedAt: String,
    @ColumnInfo(name = "duration_seconds") val durationSeconds: Int,
    val completed: Int = 0,
    @ColumnInfo(name = "session_type") val sessionType: String = "focus",
    val mode: String = "timer",
    val tag: String = "",
    val notes: String = ""
)

@Entity(tableName = "focus_schedules")
data class FocusScheduleEntity(
    @PrimaryKey val id: String,
    val title: String,
    @ColumnInfo(name = "days_json") val daysJson: String = "[]",
    @ColumnInfo(name = "start_time") val startTime: String,
    @ColumnInfo(name = "duration_minutes") val durationMinutes: Int = 25,
    val enabled: Int = 1,
    @ColumnInfo(name = "created_at") val createdAt: String,
    @ColumnInfo(name = "reminder_minutes") val reminderMinutes: Int = 0,
    @ColumnInfo(name = "skip_dates_json") val skipDatesJson: String = "[]",
    @ColumnInfo(name = "routine_type") val routineType: String = "custom",
    val timezone: String = ""
)

@Entity(tableName = "focus_presets")
data class FocusPresetEntity(
    @PrimaryKey val id: String,
    val name: String,
    val mode: String = "timer",
    @ColumnInfo(name = "duration_minutes") val durationMinutes: Int = 25,
    @ColumnInfo(name = "blocked_packages_json") val blockedPackagesJson: String = "[]",
    @ColumnInfo(name = "created_at") val createdAt: String
)

@Entity(
    tableName = "usage_limits",
    indices = [Index(value = ["package_name"], unique = true)]
)
data class UsageLimitEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "package_name") val packageName: String,
    @ColumnInfo(name = "app_name") val appName: String,
    @ColumnInfo(name = "limit_minutes") val limitMinutes: Int,
    @ColumnInfo(name = "warning_percent") val warningPercent: Int = 80,
    val enabled: Int = 1,
    @ColumnInfo(name = "created_at") val createdAt: String
)

@Entity(tableName = "exams")
data class ExamEntity(
    @PrimaryKey val id: String,
    val title: String,
    @ColumnInfo(name = "exam_date") val examDate: String,
    val details: String = ""
)

@Entity(tableName = "settings")
data class SettingEntity(
    @PrimaryKey val key: String,
    val value: String
)
