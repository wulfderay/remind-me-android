package com.wulfderay.remindme.data

import androidx.room.Database
import androidx.room.RoomDatabase

const val DATABASE_VERSION = 1

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
