package com.wulfderay.remindme.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

const val DATABASE_VERSION = 2

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS tasks_new (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                title TEXT NOT NULL,
                description TEXT NOT NULL,
                alarmTime INTEGER,
                isActive INTEGER NOT NULL,
                createdAt INTEGER NOT NULL
            )
            """.trimIndent()
        )
        database.execSQL(
            """
            INSERT INTO tasks_new (id, title, description, alarmTime, isActive, createdAt)
            SELECT id, title, description, alarmTime, isActive, createdAt FROM tasks
            """.trimIndent()
        )
        database.execSQL("DROP TABLE tasks")
        database.execSQL("ALTER TABLE tasks_new RENAME TO tasks")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_tasks_alarmTime ON tasks(alarmTime)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_tasks_createdAt ON tasks(createdAt)")
    }
}

/**
 * Room database for the RemindMe app.
 * Contains a single table for tasks.
 */
@Database(
    entities = [TaskEntity::class],
    version = DATABASE_VERSION,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
}
