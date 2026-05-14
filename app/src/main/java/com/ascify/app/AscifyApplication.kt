package com.ascify.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class AscifyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialize any app-level singletons here
    }
}
