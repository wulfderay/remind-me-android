package com.wulfderay.remindme.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wulfderay.remindme.alarm.AlarmScheduler
import com.wulfderay.remindme.data.TaskEntity
import com.wulfderay.remindme.data.TaskRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Sorting modes for the task list.
 */
enum class SortMode {
    BY_ALARM_TIME,   // Next due first
    BY_CREATED_AT    // Latest added first
}

/**
 * Immutable UI state for the task list screen.
 */
data class TaskListUiState(
    val tasks: List<TaskEntity> = emptyList(),
    val sortMode: SortMode = SortMode.BY_ALARM_TIME,
    val searchQuery: String = ""
)

/**
 * ViewModel for the main task list screen.
 * Manages sorting, search, and task lifecycle operations.
 */
@HiltViewModel
class TaskListViewModel @Inject constructor(
    private val repository: TaskRepository,
    private val alarmScheduler: AlarmScheduler
) : ViewModel() {

    private val _sortMode = MutableStateFlow(SortMode.BY_ALARM_TIME)
    val sortMode: StateFlow<SortMode> = _sortMode.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    /**
     * Reactive task list that automatically updates when:
     * - Sort mode changes
     * - Search query changes
     * - Database content changes
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<TaskListUiState> = combine(
        _sortMode,
        _searchQuery
    ) { sort, query ->
        Pair(sort, query)
    }.flatMapLatest { (sort, query) ->
        val tasksFlow = if (query.isNotBlank()) {
            repository.searchTasks(query)
        } else {
            when (sort) {
                SortMode.BY_ALARM_TIME -> repository.getAllTasksByAlarmTime()
                SortMode.BY_CREATED_AT -> repository.getAllTasksByCreatedAt()
            }
        }
        tasksFlow.combine(_sortMode) { tasks, sortMode ->
            TaskListUiState(
                tasks = tasks,
                sortMode = sortMode,
                searchQuery = query
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = TaskListUiState()
    )

    fun setSortMode(mode: SortMode) {
        _sortMode.value = mode
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    /**
     * Mark a task as completed (inactive).
     * Cancels any scheduled alarm for the task.
     */
    fun completeTask(taskId: Long) {
        viewModelScope.launch {
            repository.markInactive(taskId)
            alarmScheduler.cancel(taskId)
        }
    }

    /**
     * Permanently delete a task.
     * Cancels any scheduled alarm for the task.
     */
    fun deleteTask(task: TaskEntity) {
        viewModelScope.launch {
            alarmScheduler.cancel(task.id)
            repository.delete(task)
        }
    }
}
