package com.wulfderay.remindme

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Application class annotated with @HiltAndroidApp to trigger
 * Hilt's code generation and serve as the app-level dependency container.
 */
@HiltAndroidApp
class RemindMeApplication : Application()
