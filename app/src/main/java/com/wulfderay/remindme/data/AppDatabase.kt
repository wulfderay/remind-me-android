package com.wulfderay.remindme.data

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Room database for the RemindMe app.
 * Contains a single table for tasks.
 */
@Database(
    entities = [TaskEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
}
