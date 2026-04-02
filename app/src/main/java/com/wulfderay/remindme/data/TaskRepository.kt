package com.wulfderay.remindme.data

import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for task data operations.
 * Abstracts data source (Room) from the rest of the app.
 */
@Singleton
open class TaskRepository @Inject constructor(
    private val taskDao: TaskDao
) {
    open fun getAllTasksByAlarmTime(): Flow<List<TaskEntity>> =
        taskDao.getAllTasksByAlarmTime()

    open fun getAllTasksByCreatedAt(): Flow<List<TaskEntity>> =
        taskDao.getAllTasksByCreatedAt()

    open fun getTaskById(taskId: Long): Flow<TaskEntity?> =
        taskDao.getTaskById(taskId)

    open suspend fun getTaskByIdOnce(taskId: Long): TaskEntity? =
        taskDao.getTaskByIdOnce(taskId)

    open suspend fun getAllActiveTasks(): List<TaskEntity> =
        taskDao.getAllActiveTasks()

    open suspend fun insert(task: TaskEntity): Long =
        taskDao.insert(task)

    open suspend fun update(task: TaskEntity) =
        taskDao.update(task)

    open suspend fun delete(task: TaskEntity) =
        taskDao.delete(task)

    open suspend fun markInactive(taskId: Long) =
        taskDao.markInactive(taskId)

    open fun searchTasks(query: String): Flow<List<TaskEntity>> =
        taskDao.searchTasks(query)
}
