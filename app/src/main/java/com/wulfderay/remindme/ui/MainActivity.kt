package com.wulfderay.remindme.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.navigation.compose.rememberNavController
import com.wulfderay.remindme.alarm.AlarmScheduler
import com.wulfderay.remindme.data.TaskRepository
import com.wulfderay.remindme.ui.navigation.RemindMeNavGraph
import com.wulfderay.remindme.ui.theme.RemindMeTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Main entry point for the app.
 * Handles notification permission request and rehydrates alarms on launch.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var alarmScheduler: AlarmScheduler
    @Inject lateinit var repository: TaskRepository

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Permission result handled by the system */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request notification permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // Rehydrate alarms from database on app start
        rehydrateAlarms()

        setContent {
            RemindMeTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    RemindMeNavGraph(navController = navController)
                }
            }
        }
    }

    /**
     * Reschedule all active alarms from the database.
     * This ensures alarms survive app restarts.
     */
    private fun rehydrateAlarms() {
        CoroutineScope(Dispatchers.IO).launch {
            val activeTasks = repository.getAllActiveTasks()
            activeTasks.forEach { task ->
                alarmScheduler.schedule(task)
            }
        }
    }
}
