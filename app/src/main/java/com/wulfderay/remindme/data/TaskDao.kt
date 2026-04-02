package com.wulfderay.remindme.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for TaskEntity.
 * Provides reactive Flow-based queries and suspend CRUD operations.
 */
@Dao
interface TaskDao {

    /** Observe all tasks ordered by alarm time (next due first). */
    @Query("SELECT * FROM tasks ORDER BY alarmTime IS NULL ASC, alarmTime ASC")
    fun getAllTasksByAlarmTime(): Flow<List<TaskEntity>>

    /** Observe all tasks ordered by creation time (latest first). */
    @Query("SELECT * FROM tasks ORDER BY createdAt DESC")
    fun getAllTasksByCreatedAt(): Flow<List<TaskEntity>>

    /** Get a single task by ID (reactive). */
    @Query("SELECT * FROM tasks WHERE id = :taskId")
    fun getTaskById(taskId: Long): Flow<TaskEntity?>

    /** Get a single task by ID (one-shot, for alarm scheduling). */
    @Query("SELECT * FROM tasks WHERE id = :taskId")
    suspend fun getTaskByIdOnce(taskId: Long): TaskEntity?

    /** Get all active tasks (for rescheduling alarms on boot). */
    @Query("SELECT * FROM tasks WHERE isActive = 1")
    suspend fun getAllActiveTasks(): List<TaskEntity>

    /** Insert a new task and return the generated ID. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: TaskEntity): Long

    /** Update an existing task. */
    @Update
    suspend fun update(task: TaskEntity)

    /** Delete a task permanently. */
    @Delete
    suspend fun delete(task: TaskEntity)

    /** Mark a task as inactive (completed). */
    @Query("UPDATE tasks SET isActive = 0 WHERE id = :taskId")
    suspend fun markInactive(taskId: Long)

    /** Mark a task as active again. */
    @Query("UPDATE tasks SET isActive = 1 WHERE id = :taskId")
    suspend fun markActive(taskId: Long)

    /** Search tasks by title. */
    @Query("SELECT * FROM tasks WHERE title LIKE '%' || :query || '%' ORDER BY alarmTime IS NULL ASC, alarmTime ASC")
    fun searchTasks(query: String): Flow<List<TaskEntity>>
}
