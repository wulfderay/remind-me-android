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
 * Reschedules all active alarms after device reboot, app update,
 * or time/timezone changes.
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject lateinit var taskDao: TaskDao
    @Inject lateinit var alarmScheduler: AlarmScheduler

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED &&
            action != Intent.ACTION_TIME_CHANGED &&
            action != Intent.ACTION_TIMEZONE_CHANGED
        ) {
            return
        }

        Log.d(TAG, "Rescheduling alarms due to: $action")

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val activeTasks = taskDao.getAllActiveTasks()
                activeTasks.forEach { task ->
                    alarmScheduler.schedule(task)
                }
                Log.d(TAG, "Rescheduled ${activeTasks.size} active alarms")
            } catch (e: Exception) {
                Log.e(TAG, "Error rescheduling alarms", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
