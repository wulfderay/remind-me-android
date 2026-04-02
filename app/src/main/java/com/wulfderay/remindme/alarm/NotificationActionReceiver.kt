package com.wulfderay.remindme.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.wulfderay.remindme.data.TaskDao
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Handles notification action buttons: Snooze and Stop.
 */
@AndroidEntryPoint
class NotificationActionReceiver : BroadcastReceiver() {

    @Inject lateinit var taskDao: TaskDao
    @Inject lateinit var alarmScheduler: AlarmScheduler
    @Inject lateinit var notificationHelper: NotificationHelper

    companion object {
        private const val TAG = "NotificationActionRcvr"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getLongExtra(AlarmScheduler.EXTRA_TASK_ID, -1)
        if (taskId == -1L) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    NotificationHelper.ACTION_SNOOZE -> {
                        Log.d(TAG, "Snoozing task $taskId")
                        // Reschedule alarm with +10 minute offset
                        alarmScheduler.rescheduleWithOffset(
                            taskId,
                            NotificationHelper.SNOOZE_DURATION_MILLIS
                        )
                        // Update the alarm time in the database
                        val task = taskDao.getTaskByIdOnce(taskId)
                        if (task != null) {
                            val newAlarmTime = System.currentTimeMillis() +
                                    NotificationHelper.SNOOZE_DURATION_MILLIS
                            taskDao.update(task.copy(alarmTime = newAlarmTime))
                        }
                        notificationHelper.cancelNotification(taskId)
                    }

                    NotificationHelper.ACTION_STOP -> {
                        Log.d(TAG, "Stopping task $taskId")
                        // Mark task as inactive and cancel alarm
                        taskDao.markInactive(taskId)
                        alarmScheduler.cancel(taskId)
                        notificationHelper.cancelNotification(taskId)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error handling notification action for task $taskId", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
