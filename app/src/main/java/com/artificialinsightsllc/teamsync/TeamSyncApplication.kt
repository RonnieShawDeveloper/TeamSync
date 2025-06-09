// In file: app/src/main/java/com/artificialinsightsllc/teamsync/TeamSyncApplication.kt
package com.artificialinsightsllc.teamsync

import android.app.Application
import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.artificialinsightsllc.teamsync.Services.GroupMonitorService
import com.artificialinsightsllc.teamsync.Services.MarkerMonitorService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

// Implement Application.ActivityLifecycleCallbacks to track app foreground/background state
class TeamSyncApplication : Application(), Application.ActivityLifecycleCallbacks {

    lateinit var groupMonitorService: GroupMonitorService
        private set

    lateinit var markerMonitorService: MarkerMonitorService
        private set

    // State to track if the app is currently in the foreground
    var isAppInForeground: Boolean by mutableStateOf(false)
        private set

    // Counter for started activities. Used to determine foreground/background.
    private var activityCount = 0

    // Handler to introduce a small delay for accurate background detection
    private val handler = Handler(Looper.getMainLooper())
    private var backgroundDetectionRunnable: Runnable? = null

    // NEW: MutableStateFlow to hold the combined state of LocationTrackingService
    // This allows GroupMonitorService to observe these states as Flows.
    // Triple<Boolean, Long, String?> represents <isRunning, interval, memberId>
    private val _locationServiceRunningState = MutableStateFlow(Triple(false, 0L, null as String?))
    val locationServiceRunningState: StateFlow<Triple<Boolean, Long, String?>> = _locationServiceRunningState.asStateFlow()

    /**
     * Updates the internal state variables reflecting the LocationTrackingService's status.
     * This method is called by the LocationTrackingService itself.
     * @param running True if the service is actively running and tracking.
     * @param interval The current tracking interval.
     * @param memberId The current active group member ID being tracked.
     */
    internal fun setLocationServiceState(running: Boolean, interval: Long, memberId: String?) {
        _locationServiceRunningState.update { Triple(running, interval, memberId) }
        Log.d("TeamSyncApplication", "setLocationServiceState updated to: Running=$running, Interval=$interval, MemberId=$memberId")
    }


    override fun onCreate() {
        super.onCreate()
        Log.d("TeamSyncApplication", "Application onCreate: Initializing services.")
        // Register activity lifecycle callbacks
        registerActivityLifecycleCallbacks(this)

        markerMonitorService = MarkerMonitorService()
        groupMonitorService = GroupMonitorService(applicationContext, markerMonitorService = markerMonitorService)

        // NEW: Call startMonitoring here, in the Application's onCreate.
        // This ensures the GroupMonitorService's AuthStateListener is attached very early
        // in the app's lifecycle, allowing it to reliably pick up Firebase Auth changes.
        groupMonitorService.startMonitoring()
        Log.d("TeamSyncApplication", "GroupMonitorService.startMonitoring() called from Application onCreate.")
    }

    override fun onTerminate() {
        super.onTerminate()
        Log.d("TeamSyncApplication", "Application onTerminate: Shutting down services.")
        // Unregister activity lifecycle callbacks
        unregisterActivityLifecycleCallbacks(this)
        groupMonitorService.shutdown()
        markerMonitorService.shutdown()
    }

    // --- Activity Lifecycle Callback Implementations ---

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        Log.d("AppStatus", "onActivityCreated: ${activity.javaClass.simpleName}")
    }

    override fun onActivityStarted(activity: Activity) {
        activityCount++
        Log.d("AppStatus", "onActivityStarted: ${activity.javaClass.simpleName}, Count: $activityCount")
        // If coming from background, cancel any pending background detection
        backgroundDetectionRunnable?.let { handler.removeCallbacks(it) }
        isAppInForeground = true // Immediately set to foreground as an activity started
    }

    override fun onActivityResumed(activity: Activity) {
        Log.d("AppStatus", "onActivityResumed: ${activity.javaClass.simpleName}")
        // isAppInForeground is already true from onActivityStarted
    }

    override fun onActivityPaused(activity: Activity) {
        Log.d("AppStatus", "onActivityPaused: ${activity.javaClass.simpleName}")
        // Set a delayed runnable to check if app goes to background
        backgroundDetectionRunnable = Runnable {
            if (activityCount == 0) { // No activities left started
                isAppInForeground = false
                Log.d("AppStatus", "App detected as in background")
            }
        }
        // Small delay to account for quick activity transitions within the app
        handler.postDelayed(backgroundDetectionRunnable!!, 500) // 500ms delay
    }

    override fun onActivityStopped(activity: Activity) {
        activityCount--
        Log.d("AppStatus", "onActivityStopped: ${activity.javaClass.simpleName}, Count: $activityCount")
        // onActivityPaused would have scheduled the check
    }

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
        Log.d("AppStatus", "onActivitySaveInstanceState: ${activity.javaClass.simpleName}")
    }

    override fun onActivityDestroyed(activity: Activity) {
        Log.d("AppStatus", "onActivityDestroyed: ${activity.javaClass.simpleName}")
    }
}
