// In file: app/src/main/java/com/artificialinsightsllc/teamsync/TeamSyncApplication.kt
package com.artificialinsightsllc.teamsync

import android.app.Application
import android.util.Log
import com.artificialinsightsllc.teamsync.Services.GroupMonitorService
import com.artificialinsightsllc.teamsync.Services.MarkerMonitorService

class TeamSyncApplication : Application() {

    lateinit var groupMonitorService: GroupMonitorService
        private set

    lateinit var markerMonitorService: MarkerMonitorService
        private set

    override fun onCreate() {
        super.onCreate()
        Log.d("TeamSyncApplication", "Application onCreate: Initializing services.")
        // NEW: Initialize MarkerMonitorService first
        markerMonitorService = MarkerMonitorService()
        // Initialize GroupMonitorService, passing the *same* markerMonitorService instance
        groupMonitorService = GroupMonitorService(applicationContext, markerMonitorService = markerMonitorService)
    }

    override fun onTerminate() {
        super.onTerminate()
        Log.d("TeamSyncApplication", "Application onTerminate: Shutting down services.")
        // Ensure services are shut down when the application process terminates
        groupMonitorService.shutdown()
        markerMonitorService.shutdown()
    }
}
