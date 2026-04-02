package com.wulfderay.remindme.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity representing a task with alarm scheduling behavior.
 * Tasks are not deleted when completed; they transition to inactive state.
 */
@Entity(
    tableName = "tasks",
    indices = [
        Index(value = ["alarmTime"]),
        Index(value = ["createdAt"])
    ]
)
data class TaskEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val description: String = "",
    val alarmTime: Long? = null,
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)
