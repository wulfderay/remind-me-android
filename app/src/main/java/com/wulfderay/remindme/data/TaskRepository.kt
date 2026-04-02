package com.wulfderay.remindme.data

import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for task data operations.
 * Abstracts data source (Room) from the rest of the app.
 */
@Singleton
class TaskRepository @Inject constructor(
    private val taskDao: TaskDao
) {
    fun getAllTasksByAlarmTime(): Flow<List<TaskEntity>> =
        taskDao.getAllTasksByAlarmTime()

    fun getAllTasksByCreatedAt(): Flow<List<TaskEntity>> =
        taskDao.getAllTasksByCreatedAt()

    fun getTaskById(taskId: Long): Flow<TaskEntity?> =
        taskDao.getTaskById(taskId)

    suspend fun getTaskByIdOnce(taskId: Long): TaskEntity? =
        taskDao.getTaskByIdOnce(taskId)

    suspend fun getAllActiveTasks(): List<TaskEntity> =
        taskDao.getAllActiveTasks()

    suspend fun insert(task: TaskEntity): Long =
        taskDao.insert(task)

    suspend fun update(task: TaskEntity) =
        taskDao.update(task)

    suspend fun delete(task: TaskEntity) =
        taskDao.delete(task)

    suspend fun markInactive(taskId: Long) =
        taskDao.markInactive(taskId)

    fun searchTasks(query: String): Flow<List<TaskEntity>> =
        taskDao.searchTasks(query)
}
