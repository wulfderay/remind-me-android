package com.wulfderay.remindme.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wulfderay.remindme.alarm.AlarmScheduler
import com.wulfderay.remindme.data.TaskEntity
import com.wulfderay.remindme.data.TaskRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Immutable UI state for the task detail/edit screen.
 */
data class TaskDetailUiState(
    val title: String = "",
    val description: String = "",
    val alarmTime: Long = System.currentTimeMillis() + 3600_000, // Default: 1 hour from now
    val isActive: Boolean = true,
    val isEditing: Boolean = false,
    val isSaved: Boolean = false,
    val titleError: String? = null,
    val alarmTimeError: String? = null
)

/**
 * ViewModel for the task creation and editing screen.
 * Handles form validation, persistence, and alarm scheduling.
 */
@HiltViewModel
class TaskDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: TaskRepository,
    private val alarmScheduler: AlarmScheduler
) : ViewModel() {

    private val taskId: Long = savedStateHandle.get<Long>("taskId") ?: -1L

    private val _uiState = MutableStateFlow(TaskDetailUiState())
    val uiState: StateFlow<TaskDetailUiState> = _uiState.asStateFlow()

    init {
        if (taskId > 0) {
            loadTask(taskId)
        }
    }

    private fun loadTask(id: Long) {
        viewModelScope.launch {
            repository.getTaskByIdOnce(id)?.let { task ->
                _uiState.update {
                    it.copy(
                        title = task.title,
                        description = task.description,
                        alarmTime = task.alarmTime,
                        isActive = task.isActive,
                        isEditing = true
                    )
                }
            }
        }
    }

    fun updateTitle(title: String) {
        _uiState.update { it.copy(title = title, titleError = null) }
    }

    fun updateDescription(description: String) {
        _uiState.update { it.copy(description = description) }
    }

    fun updateAlarmTime(alarmTime: Long) {
        _uiState.update { it.copy(alarmTime = alarmTime, alarmTimeError = null) }
    }

    /**
     * Validate and save the task.
     * Returns true if saved successfully, false if validation fails.
     */
    fun saveTask() {
        val state = _uiState.value

        // Validation
        var hasError = false

        if (state.title.isBlank()) {
            _uiState.update { it.copy(titleError = "Title cannot be empty") }
            hasError = true
        }

        if (state.alarmTime <= System.currentTimeMillis()) {
            _uiState.update { it.copy(alarmTimeError = "Alarm time must be in the future") }
            hasError = true
        }

        if (hasError) return

        viewModelScope.launch {
            if (state.isEditing && taskId > 0) {
                // Update existing task
                val existingTask = repository.getTaskByIdOnce(taskId) ?: return@launch
                val updatedTask = existingTask.copy(
                    title = state.title,
                    description = state.description,
                    alarmTime = state.alarmTime
                )
                repository.update(updatedTask)

                // Reschedule alarm if task is active
                if (updatedTask.isActive) {
                    alarmScheduler.cancel(updatedTask.id)
                    alarmScheduler.schedule(updatedTask)
                }
            } else {
                // Create new task
                val newTask = TaskEntity(
                    title = state.title,
                    description = state.description,
                    alarmTime = state.alarmTime
                )
                val id = repository.insert(newTask)

                // Schedule alarm for the new task
                val savedTask = newTask.copy(id = id)
                alarmScheduler.schedule(savedTask)
            }

            _uiState.update { it.copy(isSaved = true) }
        }
    }
}
