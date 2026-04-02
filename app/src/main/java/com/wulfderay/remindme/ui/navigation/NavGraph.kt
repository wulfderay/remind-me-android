package com.wulfderay.remindme.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.wulfderay.remindme.ui.TaskDetailScreen
import com.wulfderay.remindme.ui.TaskListScreen

/**
 * Navigation routes for the app.
 */
object Routes {
    const val TASK_LIST = "task_list"
    const val TASK_DETAIL = "task_detail/{taskId}"
    const val TASK_CREATE = "task_detail/-1"

    fun taskDetail(taskId: Long) = "task_detail/$taskId"
}

/**
 * Navigation graph for the RemindMe app.
 */
@Composable
fun RemindMeNavGraph(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = Routes.TASK_LIST
    ) {
        composable(Routes.TASK_LIST) {
            TaskListScreen(
                onNavigateToDetail = { taskId ->
                    navController.navigate(Routes.taskDetail(taskId))
                },
                onNavigateToCreate = {
                    navController.navigate(Routes.TASK_CREATE)
                }
            )
        }

        composable(
            route = Routes.TASK_DETAIL,
            arguments = listOf(
                navArgument("taskId") {
                    type = NavType.LongType
                    defaultValue = -1L
                }
            )
        ) {
            TaskDetailScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
