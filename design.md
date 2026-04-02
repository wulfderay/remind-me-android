# RemindMe Android – Copilot Instructions

## Project Overview

A hybrid To-Do + Alarm Android app. Each task is a Room entity that is **never hard-deleted on completion** — it transitions to `isActive = false`. Alarms are managed by AlarmManager and must always reflect the live DB state.

## Stack

| Layer | Technology |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose + Material 3 |
| Architecture | MVVM |
| DI | Hilt (`SingletonComponent`) |
| Persistence | Room (SQLite), DB name: `remindme_database` |
| Async | Kotlin Coroutines + Flow |
| Background | AlarmManager + BroadcastReceiver |
| Notifications | NotificationCompat, channel `remindme_alarms` |

`compileSdk`/`targetSdk` = 34, `minSdk` = 26, Java 17 toolchain, KSP for annotation processing.

## Package Structure

```
com.wulfderay.remindme
├── alarm/          AlarmScheduler, AlarmReceiver, BootReceiver,
│                   NotificationActionReceiver, NotificationHelper
├── data/           TaskEntity, TaskDao, AppDatabase, TaskRepository
├── di/             AppModule (Hilt SingletonComponent)
├── ui/             Compose screens + ViewModels, navigation/, theme/
└── RemindMeApplication.kt
```

## Data Model

`TaskEntity` (Room table `tasks`):

```kotlin
@Entity(
    tableName = "tasks",
    indices = [Index(value = ["alarmTime"]), Index(value = ["createdAt"])]
)
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String = "",
    val alarmTime: Long? = null,   // Unix epoch millis, null = no alarm
    val isActive: Boolean = true,  // false = completed, never triggers alarms
    val createdAt: Long = System.currentTimeMillis()
)
```

**State rules (enforce always):**
- Completed tasks (`isActive = false`) must never trigger alarms.
- Deleting a task must cancel its scheduled alarm before removing from DB.
- Updating `alarmTime` must reschedule the alarm when one is enabled (cancel old, schedule new).
- Only `isActive = true` tasks are eligible for alarm scheduling.
- New tasks start with no alarm. Enabling an alarm defaults to `System.currentTimeMillis() + 3_600_000` (1 hour from now).

## Repository & DAO

`TaskRepository` is `@Singleton open class` (open to allow fake subclasses in tests). All methods delegate to `TaskDao`.

Key DAO patterns:
- Reactive list queries return `Flow<List<TaskEntity>>` — Room emits updates automatically.
- One-shot lookups are `suspend fun` (e.g., `getTaskByIdOnce`, `getAllActiveTasks`).
- Completing a task uses the dedicated `@Query("UPDATE … SET isActive = 0 WHERE id = :taskId")` — never update the whole entity just to flip a boolean.
- Search uses SQL `LIKE '%' || :query || '%'`.
- Alarm-time ordering keeps tasks with alarms first and tasks without alarms last.

## Alarm Scheduling

Use `AlarmScheduler` interface; inject `AlarmSchedulerImpl` via Hilt.

```kotlin
interface AlarmScheduler {
    fun schedule(task: TaskEntity)
    fun cancel(taskId: Long)
    fun rescheduleWithOffset(taskId: Long, offsetMillis: Long)

    companion object {
        const val EXTRA_TASK_ID = "extra_task_id"
    }
}
```

`AlarmSchedulerImpl` rules:
- Skip scheduling if task is inactive, has no `alarmTime`, or `alarmTime` is in the past.
- Check `alarmManager.canScheduleExactAlarms()` on Android 12+ (API 31+) before calling `setExactAndAllowWhileIdle`.
- Use `AlarmManager.RTC_WAKEUP` + `setExactAndAllowWhileIdle`.
- `PendingIntent` `requestCode` = `taskId.toInt()`, flags = `FLAG_UPDATE_CURRENT or FLAG_IMMUTABLE`.
- Snooze offset = `NotificationHelper.SNOOZE_DURATION_MILLIS` (10 minutes = `10 * 60 * 1000L`).

## Notification

Channel ID: `remindme_alarms`, importance: `IMPORTANCE_HIGH`, vibration enabled, badge shown.

`NotificationHelper.showAlarmNotification(task)`:
- Title = `task.title`, text = `task.description` (fallback: `"Task alarm!"`).
- Small icon: `R.drawable.ic_notification`.
- Category: `CATEGORY_ALARM`, `autoCancel = true`.
- Two actions: **Snooze** (`ACTION_SNOOZE`) and **Stop** (`ACTION_STOP`).
- Action `PendingIntent` IDs: snooze = `(taskId * 10 + 1).toInt()`, stop = `(taskId * 10 + 2).toInt()`.

Broadcast action constants live on `NotificationHelper.Companion`:
```kotlin
const val ACTION_SNOOZE = "com.wulfderay.remindme.ACTION_SNOOZE"
const val ACTION_STOP   = "com.wulfderay.remindme.ACTION_STOP"
```

## BroadcastReceivers

All receivers are `@AndroidEntryPoint` and use `goAsync()` + a `CoroutineScope(Dispatchers.IO)` coroutine, calling `pendingResult.finish()` in `finally`.

| Receiver | Exported | Actions |
|---|---|---|
| `AlarmReceiver` | false | alarm fires → fetch task → show notification if active |
| `BootReceiver` | true | `BOOT_COMPLETED`, `MY_PACKAGE_REPLACED`, `TIME_CHANGED`, `TIMEZONE_CHANGED` → reschedule all active alarms |
| `NotificationActionReceiver` | false | `ACTION_SNOOZE`, `ACTION_STOP` |

`NotificationActionReceiver` — Snooze flow: reschedule alarm, update `task.alarmTime` in DB, cancel notification. Stop flow: `markInactive(taskId)`, cancel alarm, cancel notification.

## UI State Pattern

Use immutable `data class` for UI state, backed by `MutableStateFlow<UiState>`, exposed as `StateFlow` via `.asStateFlow()`. Consume with `collectAsStateWithLifecycle()` in Compose.

```kotlin
// ViewModel exposes:
val uiState: StateFlow<TaskListUiState> = /* ... */
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TaskListUiState())
```

`TaskListViewModel` combines sort mode + search query using `flatMapLatest` + `combine`:
- If `searchQuery` is blank → sort by `SortMode` (BY_ALARM_TIME or BY_CREATED_AT).
- If `searchQuery` is non-blank → use `repository.searchTasks(query)` regardless of sort.

`TaskDetailViewModel` reads `taskId: Long` from `SavedStateHandle` (`-1L` = creating new task).

Task detail UI rules:
- Show an alarm enabled toggle on the edit/create screen.
- Hide the alarm date/time title and picker buttons when `alarmTime == null`.
- Turning the toggle on should populate a default alarm time 1 hour in the future.
- Turning the toggle off should clear `alarmTime` and remove alarm validation errors.

## Navigation

```kotlin
object Routes {
    const val TASK_LIST   = "task_list"
    const val TASK_DETAIL = "task_detail/{taskId}"
    const val TASK_CREATE = "task_detail/-1"

    fun taskDetail(taskId: Long) = "task_detail/$taskId"
}
```

`taskId` nav argument type: `NavType.LongType`, `defaultValue = -1L`.

## Form Validation

`saveTask()` in `TaskDetailViewModel` must validate:
1. `title.isNotBlank()` — set `titleError` on failure.
2. If `alarmTime != null`, `alarmTime > System.currentTimeMillis()` — set `alarmTimeError` on failure.

Only proceed with DB + alarm operations if both pass. On success, set `isSaved = true` to trigger navigation back.

## Theme

`RemindMeTheme` uses Material 3 dynamic color on Android 12+ (`Build.VERSION_CODES.S`), with static fallback light/dark schemes. Always wrap content in `RemindMeTheme`.

## Testing Conventions

- Test class: `TaskListViewModelTest` (and `TaskDetailViewModelTest`).
- Use `StandardTestDispatcher` + `Dispatchers.setMain(...)` in `@Before`; reset in `@After`.
- Use `runTest { advanceUntilIdle() }` to drain coroutines.
- Use Turbine (`app.cash.turbine:turbine`) to test Flows.
- Fake test doubles extend `TaskRepository` (open class) and implement `AlarmScheduler`.
  - `FakeTaskRepository` uses `MutableStateFlow<List<TaskEntity>>` as its backing store and tracks `markedInactiveIds`, `insertedTasks`, `deletedTasks`.
  - `FakeAlarmScheduler` tracks `scheduledTasks: MutableList<TaskEntity>` and `cancelledIds: MutableList<Long>`.

## Permissions (Manifest)

```xml
RECEIVE_BOOT_COMPLETED
SCHEDULE_EXACT_ALARM
USE_EXACT_ALARM
POST_NOTIFICATIONS          <!-- runtime-requested on Android 13+ in MainActivity -->
VIBRATE
USE_FULL_SCREEN_INTENT
WAKE_LOCK
```

Request `POST_NOTIFICATIONS` at runtime in `MainActivity.onCreate` for `Build.VERSION_CODES.TIRAMISU` and above.

## App Startup (MainActivity)

1. Request `POST_NOTIFICATIONS` permission if needed.
2. Call `rehydrateAlarms()` — launches `Dispatchers.IO` coroutine to fetch all active tasks and reschedule their alarms.
3. Set Compose content: `RemindMeTheme { Surface { RemindMeNavGraph(...) } }`.

## Key Conventions

- Never block the main thread — all DB and alarm operations on `Dispatchers.IO`.
- Never delete a task via `@Delete` from UI interactions; use `markInactive` for completion.
- Always cancel an alarm via `alarmScheduler.cancel(task.id)` before deleting a task from the DB.
- `PendingIntent` flags must include `FLAG_IMMUTABLE` (required for Android 12+).
- Log all alarm scheduling operations for debuggability.
- `TaskRepository` methods are all `open` — do not add `final` or remove `open`.
