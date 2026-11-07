package com.goback.focus.data.dao

import androidx.room.*
import com.goback.focus.data.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes ORDER BY updated_at DESC")
    fun getAll(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): NoteEntity?

    @Query("SELECT * FROM notes WHERE id = :id LIMIT 1")
    fun observeById(id: String): Flow<NoteEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(note: NoteEntity)

    @Delete
    suspend fun delete(note: NoteEntity)

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface SubjectDao {
    @Query("SELECT * FROM subjects ORDER BY updated_at DESC")
    fun getAll(): Flow<List<SubjectEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(subject: SubjectEntity)

    @Query("UPDATE subjects SET name = :name, updated_at = :updatedAt WHERE id = :id")
    suspend fun rename(id: String, name: String, updatedAt: String)

    @Delete
    suspend fun delete(subject: SubjectEntity)
}

@Dao
interface FocusSessionDao {
    @Query("SELECT * FROM focus_sessions ORDER BY started_at DESC")
    fun getAll(): Flow<List<FocusSessionEntity>>

    @Query("SELECT * FROM focus_sessions WHERE date(started_at) >= date(:start) AND date(started_at) <= date(:end) ORDER BY started_at DESC")
    fun getBetween(start: String, end: String): Flow<List<FocusSessionEntity>>

    @Query("SELECT * FROM focus_sessions WHERE date(started_at) = date('now', 'localtime')")
    fun getTodaySessions(): Flow<List<FocusSessionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: FocusSessionEntity)

    @Delete
    suspend fun delete(session: FocusSessionEntity)
}

@Dao
interface FocusScheduleDao {
    @Query("SELECT * FROM focus_schedules ORDER BY start_time ASC")
    fun getAll(): Flow<List<FocusScheduleEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(schedule: FocusScheduleEntity)

    @Delete
    suspend fun delete(schedule: FocusScheduleEntity)
}

@Dao
interface FocusPresetDao {
    @Query("SELECT * FROM focus_presets ORDER BY created_at DESC")
    fun getAll(): Flow<List<FocusPresetEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(preset: FocusPresetEntity)

    @Delete
    suspend fun delete(preset: FocusPresetEntity)
}

@Dao
interface UsageLimitDao {
    @Query("SELECT * FROM usage_limits ORDER BY app_name ASC")
    fun getAll(): Flow<List<UsageLimitEntity>>

    @Query("SELECT * FROM usage_limits WHERE enabled = 1")
    suspend fun getEnabledLimitsSync(): List<UsageLimitEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(limit: UsageLimitEntity)

    @Delete
    suspend fun delete(limit: UsageLimitEntity)
}

@Dao
interface ExamDao {
    @Query("SELECT * FROM exams ORDER BY exam_date ASC")
    fun getAll(): Flow<List<ExamEntity>>

    @Query("SELECT * FROM exams WHERE date(exam_date) >= date('now', 'localtime') ORDER BY exam_date ASC LIMIT 1")
    fun getNextUpcoming(): Flow<ExamEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(exam: ExamEntity)

    @Delete
    suspend fun delete(exam: ExamEntity)
}

@Dao
interface SettingDao {
    @Query("SELECT value FROM settings WHERE key = :key LIMIT 1")
    suspend fun getValue(key: String): String?

    @Query("SELECT value FROM settings WHERE key = :key LIMIT 1")
    fun observeValue(key: String): Flow<String?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setValue(setting: SettingEntity)

    @Query("DELETE FROM settings WHERE key = :key")
    suspend fun delete(key: String)
}
