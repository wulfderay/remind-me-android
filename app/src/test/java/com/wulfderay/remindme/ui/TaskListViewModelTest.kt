package com.wulfderay.remindme.ui

import app.cash.turbine.test
import com.wulfderay.remindme.alarm.AlarmScheduler
import com.wulfderay.remindme.data.TaskEntity
import com.wulfderay.remindme.data.TaskRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TaskListViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var fakeRepository: FakeTaskRepository
    private lateinit var fakeAlarmScheduler: FakeAlarmScheduler
    private lateinit var viewModel: TaskListViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        fakeRepository = FakeTaskRepository()
        fakeAlarmScheduler = FakeAlarmScheduler()
        viewModel = TaskListViewModel(fakeRepository, fakeAlarmScheduler)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state has empty task list`() = runTest {
        advanceUntilIdle()
        val state = viewModel.uiState.value
        assertTrue(state.tasks.isEmpty())
        assertEquals(SortMode.BY_ALARM_TIME, state.sortMode)
    }

    @Test
    fun `tasks are emitted when repository has data`() = runTest {
        val task = TaskEntity(
            id = 1,
            title = "Test",
            alarmTime = System.currentTimeMillis() + 60000
        )

        viewModel.uiState.test {
            assertTrue(awaitItem().tasks.isEmpty())

            fakeRepository.tasksByAlarmTime.value = listOf(task)
            advanceUntilIdle()

            val state = awaitItem()
            assertEquals(1, state.tasks.size)
            assertEquals("Test", state.tasks[0].title)
        }
    }

    @Test
    fun `sort mode changes update task source`() = runTest {
        val taskByAlarm = TaskEntity(id = 1, title = "ByAlarm", alarmTime = 1000)
        val taskByCreated = TaskEntity(id = 2, title = "ByCreated", alarmTime = 2000, createdAt = 5000)

        viewModel.uiState.test {
            assertTrue(awaitItem().tasks.isEmpty())

            fakeRepository.tasksByAlarmTime.value = listOf(taskByAlarm)
            fakeRepository.tasksByCreatedAt.value = listOf(taskByCreated)
            advanceUntilIdle()

            assertEquals("ByAlarm", awaitItem().tasks[0].title)

            viewModel.setSortMode(SortMode.BY_CREATED_AT)
            advanceUntilIdle()

            val updatedState = awaitItem()
            assertEquals(SortMode.BY_CREATED_AT, updatedState.sortMode)
            assertEquals("ByCreated", updatedState.tasks[0].title)
        }
    }

    @Test
    fun `marking task complete makes it inactive and cancels alarm`() = runTest {
        val task = TaskEntity(id = 1, title = "Test", alarmTime = System.currentTimeMillis() + 60_000)
        fakeRepository.tasksByAlarmTime.value = listOf(task)
        advanceUntilIdle()

        viewModel.setTaskCompleted(task, completed = true)
        advanceUntilIdle()

        assertTrue(fakeRepository.markedInactiveIds.contains(1L))
        assertTrue(fakeAlarmScheduler.cancelledIds.contains(1L))
    }

    @Test
    fun `unchecking a completed task marks it active and reschedules alarm`() = runTest {
        val task = TaskEntity(
            id = 1,
            title = "Test",
            alarmTime = System.currentTimeMillis() + 60_000,
            isActive = false
        )

        viewModel.setTaskCompleted(task, completed = false)
        advanceUntilIdle()

        assertTrue(fakeRepository.markedActiveIds.contains(1L))
        assertEquals(listOf(task.copy(isActive = true)), fakeAlarmScheduler.scheduledTasks)
    }

    @Test
    fun `deleteTask removes task and cancels alarm`() = runTest {
        val task = TaskEntity(id = 1, title = "Test", alarmTime = 1000)
        fakeRepository.tasksByAlarmTime.value = listOf(task)
        advanceUntilIdle()

        viewModel.deleteTask(task)
        advanceUntilIdle()

        assertTrue(fakeRepository.deletedTasks.contains(task))
        assertTrue(fakeAlarmScheduler.cancelledIds.contains(1L))
    }

    @Test
    fun `search query filters tasks`() = runTest {
        val searchResult = TaskEntity(id = 3, title = "SearchResult", alarmTime = 3000)

        viewModel.uiState.test {
            assertTrue(awaitItem().tasks.isEmpty())

            fakeRepository.searchResults.value = listOf(searchResult)
            viewModel.setSearchQuery("Search")
            advanceUntilIdle()

            assertEquals("SearchResult", awaitItem().tasks[0].title)
        }
    }
}

class AlarmDateTimeTest {

    @Test
    fun `date picker selection uses UTC midnight for the local task date`() {
        val timezone = java.util.TimeZone.getTimeZone("America/Los_Angeles")
        val alarmTime = java.util.Calendar.getInstance(timezone).apply {
            clear()
            set(2026, java.util.Calendar.APRIL, 2, 15, 30, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis

        val pickerMillis = datePickerSelectionMillis(alarmTime, timezone)
        val utcCalendar = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply {
            timeInMillis = pickerMillis
        }

        assertEquals(2026, utcCalendar.get(java.util.Calendar.YEAR))
        assertEquals(java.util.Calendar.APRIL, utcCalendar.get(java.util.Calendar.MONTH))
        assertEquals(2, utcCalendar.get(java.util.Calendar.DAY_OF_MONTH))
        assertEquals(0, utcCalendar.get(java.util.Calendar.HOUR_OF_DAY))
        assertEquals(0, utcCalendar.get(java.util.Calendar.MINUTE))
    }

    @Test
    fun `merging picked date preserves selected day in local time`() {
        val timezone = java.util.TimeZone.getTimeZone("America/Los_Angeles")
        val existingAlarmTime = java.util.Calendar.getInstance(timezone).apply {
            clear()
            set(2026, java.util.Calendar.APRIL, 1, 15, 30, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
        val selectedDateUtcMillis = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(2026, java.util.Calendar.APRIL, 2, 0, 0, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis

        val mergedAlarmTime = mergePickedDateWithExistingTime(
            selectedDateUtcMillis = selectedDateUtcMillis,
            existingAlarmTimeMillis = existingAlarmTime,
            timeZone = timezone
        )
        val localCalendar = java.util.Calendar.getInstance(timezone).apply {
            timeInMillis = mergedAlarmTime
        }

        assertEquals(2026, localCalendar.get(java.util.Calendar.YEAR))
        assertEquals(java.util.Calendar.APRIL, localCalendar.get(java.util.Calendar.MONTH))
        assertEquals(2, localCalendar.get(java.util.Calendar.DAY_OF_MONTH))
        assertEquals(15, localCalendar.get(java.util.Calendar.HOUR_OF_DAY))
        assertEquals(30, localCalendar.get(java.util.Calendar.MINUTE))
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class TaskDetailViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state for new task has default values`() = runTest {
        val repo = FakeTaskRepository()
        val scheduler = FakeAlarmScheduler()
        val savedState = androidx.lifecycle.SavedStateHandle(mapOf("taskId" to -1L))
        val viewModel = TaskDetailViewModel(savedState, repo, scheduler)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("", state.title)
        assertEquals("", state.description)
        assertEquals(null, state.alarmTime)
        assertFalse(state.isEditing)
        assertFalse(state.isSaved)
    }

    @Test
    fun `enabling alarm on new task assigns a future default time`() = runTest {
        val repo = FakeTaskRepository()
        val scheduler = FakeAlarmScheduler()
        val savedState = androidx.lifecycle.SavedStateHandle(mapOf("taskId" to -1L))
        val viewModel = TaskDetailViewModel(savedState, repo, scheduler)
        advanceUntilIdle()

        val beforeEnable = System.currentTimeMillis()
        viewModel.setAlarmEnabled(true)
        val alarmTime = viewModel.uiState.value.alarmTime

        assertTrue(alarmTime != null && alarmTime > beforeEnable)
    }

    @Test
    fun `updating title clears title error`() = runTest {
        val repo = FakeTaskRepository()
        val scheduler = FakeAlarmScheduler()
        val savedState = androidx.lifecycle.SavedStateHandle(mapOf("taskId" to -1L))
        val viewModel = TaskDetailViewModel(savedState, repo, scheduler)
        advanceUntilIdle()

        // Try saving with empty title to trigger error
        viewModel.saveTask()
        advanceUntilIdle()
        assertEquals("Title cannot be empty", viewModel.uiState.value.titleError)

        // Update title should clear the error
        viewModel.updateTitle("New Title")
        assertEquals(null, viewModel.uiState.value.titleError)
    }

    @Test
    fun `saveTask validates empty title`() = runTest {
        val repo = FakeTaskRepository()
        val scheduler = FakeAlarmScheduler()
        val savedState = androidx.lifecycle.SavedStateHandle(mapOf("taskId" to -1L))
        val viewModel = TaskDetailViewModel(savedState, repo, scheduler)
        advanceUntilIdle()

        viewModel.saveTask()
        advanceUntilIdle()

        assertEquals("Title cannot be empty", viewModel.uiState.value.titleError)
        assertFalse(viewModel.uiState.value.isSaved)
    }

    @Test
    fun `saveTask validates past alarm time`() = runTest {
        val repo = FakeTaskRepository()
        val scheduler = FakeAlarmScheduler()
        val savedState = androidx.lifecycle.SavedStateHandle(mapOf("taskId" to -1L))
        val viewModel = TaskDetailViewModel(savedState, repo, scheduler)
        advanceUntilIdle()

        viewModel.updateTitle("Test Task")
        viewModel.updateAlarmTime(1000L) // Way in the past
        viewModel.saveTask()
        advanceUntilIdle()

        assertEquals("Alarm time must be in the future", viewModel.uiState.value.alarmTimeError)
        assertFalse(viewModel.uiState.value.isSaved)
    }

    @Test
    fun `saveTask succeeds with valid data`() = runTest {
        val repo = FakeTaskRepository()
        val scheduler = FakeAlarmScheduler()
        val savedState = androidx.lifecycle.SavedStateHandle(mapOf("taskId" to -1L))
        val viewModel = TaskDetailViewModel(savedState, repo, scheduler)
        advanceUntilIdle()

        viewModel.updateTitle("Valid Task")
        viewModel.updateAlarmTime(System.currentTimeMillis() + 3600_000)
        viewModel.saveTask()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isSaved)
        assertEquals(1, repo.insertedTasks.size)
        assertEquals("Valid Task", repo.insertedTasks[0].title)
    }

    @Test
    fun `saveTask schedules alarm for new task`() = runTest {
        val repo = FakeTaskRepository()
        val scheduler = FakeAlarmScheduler()
        val savedState = androidx.lifecycle.SavedStateHandle(mapOf("taskId" to -1L))
        val viewModel = TaskDetailViewModel(savedState, repo, scheduler)
        advanceUntilIdle()

        viewModel.updateTitle("Alarm Task")
        viewModel.updateAlarmTime(System.currentTimeMillis() + 3600_000)
        viewModel.saveTask()
        advanceUntilIdle()

        assertEquals(1, scheduler.scheduledTasks.size)
    }

    @Test
    fun `new task can be saved without an alarm`() = runTest {
        val repo = FakeTaskRepository()
        val scheduler = FakeAlarmScheduler()
        val savedState = androidx.lifecycle.SavedStateHandle(mapOf("taskId" to -1L))
        val viewModel = TaskDetailViewModel(savedState, repo, scheduler)
        advanceUntilIdle()

        viewModel.updateTitle("No Alarm Task")
        viewModel.saveTask()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isSaved)
        assertEquals(1, repo.insertedTasks.size)
        assertEquals(null, repo.insertedTasks[0].alarmTime)
        assertTrue(scheduler.scheduledTasks.isEmpty())
    }

    @Test
    fun `disabling alarm for existing task cancels without rescheduling`() = runTest {
        val existingTask = TaskEntity(
            id = 7L,
            title = "Existing Task",
            alarmTime = System.currentTimeMillis() + 3600_000
        )
        val repo = FakeTaskRepository().apply {
            tasksById[existingTask.id] = existingTask
        }
        val scheduler = FakeAlarmScheduler()
        val savedState = androidx.lifecycle.SavedStateHandle(mapOf("taskId" to existingTask.id))
        val viewModel = TaskDetailViewModel(savedState, repo, scheduler)
        advanceUntilIdle()

        viewModel.setAlarmEnabled(false)
        viewModel.saveTask()
        advanceUntilIdle()

        assertEquals(listOf(existingTask.id), scheduler.cancelledIds)
        assertTrue(scheduler.scheduledTasks.isEmpty())
        assertEquals(null, repo.updatedTasks.single().alarmTime)
    }
}

/**
 * Fake implementation of TaskRepository for testing.
 */
class FakeTaskRepository : TaskRepository(
    taskDao = FakeTaskDao()
) {
    val tasksByAlarmTime = MutableStateFlow<List<TaskEntity>>(emptyList())
    val tasksByCreatedAt = MutableStateFlow<List<TaskEntity>>(emptyList())
    val searchResults = MutableStateFlow<List<TaskEntity>>(emptyList())
    val markedInactiveIds = mutableListOf<Long>()
    val markedActiveIds = mutableListOf<Long>()
    val deletedTasks = mutableListOf<TaskEntity>()
    val insertedTasks = mutableListOf<TaskEntity>()
    val updatedTasks = mutableListOf<TaskEntity>()
    val tasksById = mutableMapOf<Long, TaskEntity>()
    private var nextId = 1L

    override fun getAllTasksByAlarmTime(): Flow<List<TaskEntity>> = tasksByAlarmTime
    override fun getAllTasksByCreatedAt(): Flow<List<TaskEntity>> = tasksByCreatedAt
    override fun searchTasks(query: String): Flow<List<TaskEntity>> = searchResults

    override suspend fun markInactive(taskId: Long) {
        markedInactiveIds.add(taskId)
    }

    override suspend fun markActive(taskId: Long) {
        markedActiveIds.add(taskId)
    }

    override suspend fun delete(task: TaskEntity) {
        deletedTasks.add(task)
    }

    override suspend fun insert(task: TaskEntity): Long {
        insertedTasks.add(task)
        val id = nextId++
        tasksById[id] = task.copy(id = id)
        return id
    }

    override suspend fun update(task: TaskEntity) {
        updatedTasks.add(task)
        tasksById[task.id] = task
    }

    override suspend fun getTaskByIdOnce(taskId: Long): TaskEntity? = tasksById[taskId]
    override fun getTaskById(taskId: Long): Flow<TaskEntity?> = MutableStateFlow(null)
    override suspend fun getAllActiveTasks(): List<TaskEntity> = emptyList()
}

/**
 * Minimal fake TaskDao (needed because FakeTaskRepository extends TaskRepository
 * which requires a TaskDao in constructor).
 */
private class FakeTaskDao : com.wulfderay.remindme.data.TaskDao {
    override fun getAllTasksByAlarmTime() = MutableStateFlow<List<TaskEntity>>(emptyList())
    override fun getAllTasksByCreatedAt() = MutableStateFlow<List<TaskEntity>>(emptyList())
    override fun getTaskById(taskId: Long) = MutableStateFlow<TaskEntity?>(null)
    override suspend fun getTaskByIdOnce(taskId: Long): TaskEntity? = null
    override suspend fun getAllActiveTasks(): List<TaskEntity> = emptyList()
    override suspend fun insert(task: TaskEntity): Long = 1
    override suspend fun update(task: TaskEntity) {}
    override suspend fun delete(task: TaskEntity) {}
    override suspend fun markInactive(taskId: Long) {}
    override suspend fun markActive(taskId: Long) {}
    override fun searchTasks(query: String) = MutableStateFlow<List<TaskEntity>>(emptyList())
}

/**
 * Fake AlarmScheduler for testing.
 */
class FakeAlarmScheduler : AlarmScheduler {
    val scheduledTasks = mutableListOf<TaskEntity>()
    val cancelledIds = mutableListOf<Long>()

    override fun schedule(task: TaskEntity) {
        scheduledTasks.add(task)
    }

    override fun cancel(taskId: Long) {
        cancelledIds.add(taskId)
    }

    override fun rescheduleWithOffset(taskId: Long, offsetMillis: Long) {
        // No-op for tests
    }
}
