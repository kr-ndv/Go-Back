package com.goback.focus.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.goback.focus.data.dao.*
import com.goback.focus.data.entity.*

@Database(
    entities = [
        NoteEntity::class,
        SubjectEntity::class,
        FocusSessionEntity::class,
        FocusScheduleEntity::class,
        FocusPresetEntity::class,
        UsageLimitEntity::class,
        ExamEntity::class,
        SettingEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class GoBackDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao
    abstract fun subjectDao(): SubjectDao
    abstract fun focusSessionDao(): FocusSessionDao
    abstract fun focusScheduleDao(): FocusScheduleDao
    abstract fun focusPresetDao(): FocusPresetDao
    abstract fun usageLimitDao(): UsageLimitDao
    abstract fun examDao(): ExamDao
    abstract fun settingDao(): SettingDao

    companion object {
        @Volatile
        private var INSTANCE: GoBackDatabase? = null

        fun getDatabase(context: Context): GoBackDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    GoBackDatabase::class.java,
                    "goback.db"
                )
                .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
