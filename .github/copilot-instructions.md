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

## Design document
See [design.md](./design.md) for detailed design decisions, data model, and state management rules. This is the source of truth for all architectural and implementation choices. When the user makes a request that requires an update to the design, update `design.md` first to reflect the new decisions before implementing the code changes.

## Commit as you go
Make small, focused commits with clear messages in feature branches.
Committing directly to `main` is not allowed. Use pull requests for code review and merging.
Committing changes is non optional, even if the user does not specifically ask for it. 

## DB versioning
When making changes to the Room database schema, increment the version number in `AppDatabase` and provide a migration strategy. Always ensure that existing data is preserved during migrations.