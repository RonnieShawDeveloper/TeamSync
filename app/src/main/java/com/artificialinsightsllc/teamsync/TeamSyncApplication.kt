// In file: app/src/main/java/com/artificialinsightsllc/teamsync/TeamSyncApplication.kt
package com.artificialinsightsllc.teamsync

import android.app.Application
import android.util.Log
import com.artificialinsightsllc.teamsync.Services.GroupMonitorService
import com.artificialinsightsllc.teamsync.Services.MarkerMonitorService // NEW IMPORT

class TeamSyncApplication : Application() {

    lateinit var groupMonitorService: GroupMonitorService
        private set

    // NEW: Make MarkerMonitorService a singleton accessible throughout the app
    lateinit var markerMonitorService: MarkerMonitorService
        private set // Only allow setting internally

    override fun onCreate() {
        super.onCreate()
        Log.d("TeamSyncApplication", "Application onCreate: Initializing services.")
        // Initialize GroupMonitorService
        groupMonitorService = GroupMonitorService(applicationContext)
        // NEW: Initialize MarkerMonitorService
        markerMonitorService = MarkerMonitorService()
    }

    override fun onTerminate() {
        super.onTerminate()
        Log.d("TeamSyncApplication", "Application onTerminate: Shutting down services.")
        // Ensure services are shut down when the application process terminates
        groupMonitorService.shutdown()
        // NEW: Shutdown MarkerMonitorService
        markerMonitorService.shutdown()
    }
}
