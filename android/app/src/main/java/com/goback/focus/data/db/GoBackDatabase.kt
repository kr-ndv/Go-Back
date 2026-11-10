package com.goback.focus.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
        /**
         * Expo SQLite left PRAGMA user_version at 0. Its table definitions also
         * evolved additively across beta releases. Rebuild known tables to Room's
         * canonical schema while copying every available column from the old table.
         * Missing columns are only defaulted when their old schema default is known.
         */
        private val MIGRATION_0_1 = object : Migration(0, 1) {
            override fun migrate(database: SupportSQLiteDatabase) {
                migrateLegacyTable(
                    database, "notes",
                    "CREATE TABLE IF NOT EXISTS `notes` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, `body` TEXT NOT NULL, `updated_at` TEXT NOT NULL, PRIMARY KEY(`id`))",
                    listOf("id", "title", "body", "updated_at"),
                    mapOf("body" to "''")
                )
                migrateLegacyTable(
                    database, "subjects",
                    "CREATE TABLE IF NOT EXISTS `subjects` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `chapters_json` TEXT NOT NULL, `updated_at` TEXT NOT NULL, PRIMARY KEY(`id`))",
                    listOf("id", "name", "chapters_json", "updated_at"),
                    mapOf("chapters_json" to "'[]'")
                )
                migrateLegacyTable(
                    database, "focus_sessions",
                    "CREATE TABLE IF NOT EXISTS `focus_sessions` (`id` TEXT NOT NULL, `started_at` TEXT NOT NULL, `duration_seconds` INTEGER NOT NULL, `completed` INTEGER NOT NULL, `session_type` TEXT NOT NULL, `mode` TEXT NOT NULL, `tag` TEXT NOT NULL, `notes` TEXT NOT NULL, PRIMARY KEY(`id`))",
                    listOf("id", "started_at", "duration_seconds", "completed", "session_type", "mode", "tag", "notes"),
                    mapOf(
                        "completed" to "0",
                        "session_type" to "'focus'",
                        "mode" to "'timer'",
                        "tag" to "''",
                        "notes" to "''"
                    )
                )
                migrateLegacyTable(
                    database, "focus_schedules",
                    "CREATE TABLE IF NOT EXISTS `focus_schedules` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, `days_json` TEXT NOT NULL, `start_time` TEXT NOT NULL, `duration_minutes` INTEGER NOT NULL, `enabled` INTEGER NOT NULL, `created_at` TEXT NOT NULL, `reminder_minutes` INTEGER NOT NULL, `skip_dates_json` TEXT NOT NULL, `routine_type` TEXT NOT NULL, `timezone` TEXT NOT NULL, PRIMARY KEY(`id`))",
                    listOf("id", "title", "days_json", "start_time", "duration_minutes", "enabled", "created_at", "reminder_minutes", "skip_dates_json", "routine_type", "timezone"),
                    mapOf(
                        "days_json" to "'[]'",
                        "duration_minutes" to "25",
                        "enabled" to "1",
                        "reminder_minutes" to "0",
                        "skip_dates_json" to "'[]'",
                        "routine_type" to "'custom'",
                        "timezone" to "''"
                    )
                )
                migrateLegacyTable(
                    database, "focus_presets",
                    "CREATE TABLE IF NOT EXISTS `focus_presets` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `mode` TEXT NOT NULL, `duration_minutes` INTEGER NOT NULL, `blocked_packages_json` TEXT NOT NULL, `created_at` TEXT NOT NULL, PRIMARY KEY(`id`))",
                    listOf("id", "name", "mode", "duration_minutes", "blocked_packages_json", "created_at"),
                    mapOf(
                        "mode" to "'timer'",
                        "duration_minutes" to "25",
                        "blocked_packages_json" to "'[]'"
                    )
                )
                migrateLegacyTable(
                    database, "usage_limits",
                    "CREATE TABLE IF NOT EXISTS `usage_limits` (`id` TEXT NOT NULL, `package_name` TEXT NOT NULL, `app_name` TEXT NOT NULL, `limit_minutes` INTEGER NOT NULL, `warning_percent` INTEGER NOT NULL, `enabled` INTEGER NOT NULL, `created_at` TEXT NOT NULL, PRIMARY KEY(`id`))",
                    listOf("id", "package_name", "app_name", "limit_minutes", "warning_percent", "enabled", "created_at"),
                    mapOf("warning_percent" to "80", "enabled" to "1")
                )
                migrateLegacyTable(
                    database, "exams",
                    "CREATE TABLE IF NOT EXISTS `exams` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, `exam_date` TEXT NOT NULL, `details` TEXT NOT NULL, PRIMARY KEY(`id`))",
                    listOf("id", "title", "exam_date", "details"),
                    mapOf("details" to "''")
                )
                migrateLegacyTable(
                    database, "settings",
                    "CREATE TABLE IF NOT EXISTS `settings` (`key` TEXT NOT NULL, `value` TEXT NOT NULL, PRIMARY KEY(`key`))",
                    listOf("key", "value"),
                    emptyMap()
                )

                database.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_usage_limits_package_name` ON `usage_limits` (`package_name`)"
                )
            }
        }

        private fun migrateLegacyTable(
            database: SupportSQLiteDatabase,
            table: String,
            createSql: String,
            columns: List<String>,
            fallbackValues: Map<String, String>
        ) {
            val exists = database.query(
                "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?",
                arrayOf(table)
            ).use { it.moveToFirst() }

            if (!exists) {
                database.execSQL(createSql)
                return
            }

            val oldColumns = mutableSetOf<String>()
            database.query("PRAGMA table_info(`$table`)").use { cursor ->
                val nameIndex = cursor.getColumnIndex("name")
                while (cursor.moveToNext()) {
                    if (nameIndex >= 0) oldColumns += cursor.getString(nameIndex)
                }
            }

            val unavailableRequired = columns.filter { it !in oldColumns && it !in fallbackValues }
            check(unavailableRequired.isEmpty()) {
                "Cannot safely migrate the Go Back $table table. Missing required columns: ${unavailableRequired.joinToString()}. Your existing database has not been replaced."
            }

            val backupTable = "${table}_legacy_migration"
            database.execSQL("DROP TABLE IF EXISTS `$backupTable`")
            database.execSQL("ALTER TABLE `$table` RENAME TO `$backupTable`")
            database.execSQL(createSql)

            val insertColumns = columns.joinToString(", ") { "`$it`" }
            val selectValues = columns.joinToString(", ") { column ->
                if (column in oldColumns) "`$column`" else fallbackValues.getValue(column)
            }
            database.execSQL(
                "INSERT INTO `$table` ($insertColumns) SELECT $selectValues FROM `$backupTable`"
            )
            database.execSQL("DROP TABLE `$backupTable`")
        }

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
                    .addMigrations(MIGRATION_0_1)
                    .fallbackToDestructiveMigration()
                    .fallbackToDestructiveMigrationOnDowngrade()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
