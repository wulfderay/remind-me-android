package com.wulfderay.remindme.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import com.wulfderay.remindme.R
import com.wulfderay.remindme.data.TaskEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles creating notification channels and posting alarm notifications.
 * Notifications include Snooze (+10 min) and Stop (mark inactive) actions.
 */
@Singleton
class NotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    companion object {
        const val CHANNEL_ID = "remindme_alarms"
        const val CHANNEL_NAME = "Task Alarms"
        const val ACTION_SNOOZE = "com.wulfderay.remindme.ACTION_SNOOZE"
        const val ACTION_STOP = "com.wulfderay.remindme.ACTION_STOP"
        const val SNOOZE_DURATION_MILLIS = 10 * 60 * 1000L // 10 minutes
        private const val TAG = "NotificationHelper"
    }

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Notifications for task alarms"
            enableVibration(true)
            setShowBadge(true)
            setSound(
                soundUri,
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
        }
        notificationManager.createNotificationChannel(channel)
    }

    /**
     * Posts a high-priority notification for the triggered alarm.
     */
    fun showAlarmNotification(task: TaskEntity) {
        val snoozeIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = ACTION_SNOOZE
            putExtra(AlarmScheduler.EXTRA_TASK_ID, task.id)
        }
        val snoozePendingIntent = PendingIntent.getBroadcast(
            context,
            (task.id * 10 + 1).toInt(),
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = ACTION_STOP
            putExtra(AlarmScheduler.EXTRA_TASK_ID, task.id)
        }
        val stopPendingIntent = PendingIntent.getBroadcast(
            context,
            (task.id * 10 + 2).toInt(),
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(task.title)
            .setContentText(task.description.ifEmpty { "Task alarm!" })
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setDefaults(Notification.DEFAULT_SOUND)
            .addAction(R.drawable.ic_notification, "Snooze (+10 min)", snoozePendingIntent)
            .addAction(R.drawable.ic_notification, "Stop", stopPendingIntent)
            .build()

        notificationManager.notify(task.id.toInt(), notification)
        Log.d(TAG, "Posted notification for task ${task.id}")
    }

    /**
     * Dismiss the notification for a given task.
     */
    fun cancelNotification(taskId: Long) {
        notificationManager.cancel(taskId.toInt())
    }
}
