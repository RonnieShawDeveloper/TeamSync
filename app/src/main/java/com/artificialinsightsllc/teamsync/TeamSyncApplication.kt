// In file: app/src/main/java/com/artificialinsightsllc/teamsync/TeamSyncApplication.kt
package com.artificialinsightsllc.teamsync

import android.app.Application
import android.util.Log
import com.artificialinsightsllc.teamsync.Services.GroupMonitorService

class TeamSyncApplication : Application() {

    // Make GroupMonitorService a singleton accessible throughout the app
    lateinit var groupMonitorService: GroupMonitorService
        private set // Only allow setting internally

    override fun onCreate() {
        super.onCreate()
        Log.d("TeamSyncApplication", "Application onCreate: Initializing GroupMonitorService.")
        // Initialize GroupMonitorService here, as early as possible
        groupMonitorService = GroupMonitorService(applicationContext)
    }

    override fun onTerminate() {
        super.onTerminate()
        Log.d("TeamSyncApplication", "Application onTerminate: Shutting down GroupMonitorService.")
        // Ensure the service is shut down when the application process terminates
        groupMonitorService.shutdown()
    }
}