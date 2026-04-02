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
 * BroadcastReceiver that fires when a task's alarm triggers.
 * Looks up the task from the database and posts a notification.
 */
@AndroidEntryPoint
class AlarmReceiver : BroadcastReceiver() {

    @Inject lateinit var taskDao: TaskDao
    @Inject lateinit var notificationHelper: NotificationHelper

    companion object {
        private const val TAG = "AlarmReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getLongExtra(AlarmScheduler.EXTRA_TASK_ID, -1)
        if (taskId == -1L) {
            Log.w(TAG, "Received alarm with no task ID")
            return
        }

        Log.d(TAG, "Alarm triggered for task $taskId")

        // Use goAsync() to extend receiver lifetime for DB lookup
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val task = taskDao.getTaskByIdOnce(taskId)
                if (task != null && task.isActive) {
                    notificationHelper.showAlarmNotification(task)
                } else {
                    Log.d(TAG, "Task $taskId is null or inactive, skipping notification")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error handling alarm for task $taskId", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
