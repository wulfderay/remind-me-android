package com.wulfderay.remindme.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.wulfderay.remindme.data.TaskEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Interface for alarm scheduling operations.
 * Allows easy substitution in tests.
 */
interface AlarmScheduler {
    fun schedule(task: TaskEntity)
    fun cancel(taskId: Long)
    fun rescheduleWithOffset(taskId: Long, offsetMillis: Long)

    companion object {
        const val EXTRA_TASK_ID = "extra_task_id"
    }
}

/**
 * Production implementation of [AlarmScheduler].
 * Uses AlarmManager.setExactAndAllowWhileIdle for reliable delivery.
 * Each task gets a unique PendingIntent keyed by task.id.
 */
@Singleton
class AlarmSchedulerImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : AlarmScheduler {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    companion object {
        private const val TAG = "AlarmScheduler"
    }

    /**
     * Schedule an exact alarm for the given task.
     * Only schedules if the task is active and alarm time is in the future.
     */
    override fun schedule(task: TaskEntity) {
        if (!task.isActive) {
            Log.d(TAG, "Skipping alarm for inactive task ${task.id}")
            return
        }

        if (task.alarmTime <= System.currentTimeMillis()) {
            Log.d(TAG, "Skipping alarm for past time on task ${task.id}")
            return
        }

        // Check if we can schedule exact alarms on Android 12+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!alarmManager.canScheduleExactAlarms()) {
                Log.w(TAG, "Cannot schedule exact alarms - permission not granted")
                return
            }
        }

        val pendingIntent = createPendingIntent(task.id)

        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            task.alarmTime,
            pendingIntent
        )

        Log.d(TAG, "Scheduled alarm for task ${task.id} at ${task.alarmTime}")
    }

    /**
     * Cancel any scheduled alarm for the given task ID.
     */
    override fun cancel(taskId: Long) {
        val pendingIntent = createPendingIntent(taskId)
        alarmManager.cancel(pendingIntent)
        Log.d(TAG, "Cancelled alarm for task $taskId")
    }

    /**
     * Reschedule an alarm with a time offset (used for snooze).
     */
    override fun rescheduleWithOffset(taskId: Long, offsetMillis: Long) {
        val newTime = System.currentTimeMillis() + offsetMillis
        val pendingIntent = createPendingIntent(taskId)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!alarmManager.canScheduleExactAlarms()) {
                Log.w(TAG, "Cannot schedule exact alarms - permission not granted")
                return
            }
        }

        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            newTime,
            pendingIntent
        )

        Log.d(TAG, "Rescheduled alarm for task $taskId to $newTime")
    }

    private fun createPendingIntent(taskId: Long): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra(AlarmScheduler.EXTRA_TASK_ID, taskId)
        }
        return PendingIntent.getBroadcast(
            context,
            taskId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
