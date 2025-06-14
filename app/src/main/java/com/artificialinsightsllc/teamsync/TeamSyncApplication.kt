// In file: app/src/main/java/com/artificialinsightsllc/teamsync/TeamSyncApplication.kt
package com.artificialinsightsllc.teamsync

import android.app.Application
import android.app.Activity
import android.net.Uri // NEW: Import Uri
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
import com.artificialinsightsllc.teamsync.Database.AppDatabase
import com.artificialinsightsllc.teamsync.Database.NotificationRepository
import com.artificialinsightsllc.teamsync.Models.NotificationEntity // NEW: Import NotificationEntity
import kotlinx.coroutines.CoroutineScope // NEW: Import CoroutineScope
import kotlinx.coroutines.SupervisorJob // NEW: Import SupervisorJob
import kotlinx.coroutines.Dispatchers // NEW: Import Dispatchers
import kotlinx.coroutines.launch // NEW: Import launch
import kotlinx.coroutines.flow.filterNotNull // NEW: Import filterNotNull
import kotlinx.coroutines.flow.first // NEW: Import first


// Implement Application.ActivityLifecycleCallbacks to track app foreground/background state
class TeamSyncApplication : Application(), Application.ActivityLifecycleCallbacks {

    lateinit var groupMonitorService: GroupMonitorService
        private set

    lateinit var markerMonitorService: MarkerMonitorService
        private set

    lateinit var notificationRepository: NotificationRepository
        private set

    // State to track if the app is currently in the foreground
    var isAppInForeground: Boolean by mutableStateOf(false)
        private set

    // Counter for started activities. Used to determine foreground/background.
    private var activityCount = 0

    // Handler to introduce a small delay for accurate background detection
    private val handler = Handler(Looper.getMainLooper())
    private var backgroundDetectionRunnable: Runnable? = null

    // MutableStateFlow to hold the combined state of LocationTrackingService
    private val _locationServiceRunningState = MutableStateFlow(Triple(false, 0L, null as String?))
    val locationServiceRunningState: StateFlow<Triple<Boolean, Long, String?>> = _locationServiceRunningState.asStateFlow()

    // NEW: StateFlow to hold a pending notification received on cold start (from MainActivity)
    private val _pendingNotificationForSave = MutableStateFlow<NotificationEntity?>(null)

    // NEW: Optional - to hold deep link URI, if we ever want to explicitly navigate to it later
    private val _pendingDeepLinkUri = MutableStateFlow<Uri?>(null)
    val pendingDeepLinkUri: StateFlow<Uri?> = _pendingDeepLinkUri.asStateFlow() // Expose as StateFlow


    /**
     * Updates the internal state variables reflecting the LocationTrackingService's status.
     */
    internal fun setLocationServiceState(running: Boolean, interval: Long, memberId: String?) {
        _locationServiceRunningState.update { Triple(running, interval, memberId) }
        Log.d("TeamSyncApplication", "setLocationServiceState updated to: Running=$running, Interval=$interval, MemberId=$memberId")
    }

    /**
     * Call this from MainActivity to set a pending deep link URI.
     */
    fun setPendingDeepLink(uri: Uri?) {
        _pendingDeepLinkUri.value = uri
    }

    /**
     * Call this from MainActivity to set a notification entity received on launch (cold start).
     */
    fun setPendingNotificationForSave(notification: NotificationEntity?) {
        _pendingNotificationForSave.value = notification
    }


    override fun onCreate() {
        super.onCreate()
        Log.d("TeamSyncApplication", "Application onCreate: Initializing services.")
        registerActivityLifecycleCallbacks(this)

        markerMonitorService = MarkerMonitorService()
        groupMonitorService = GroupMonitorService(applicationContext, markerMonitorService = markerMonitorService)

        val database = AppDatabase.getDatabase(applicationContext)
        notificationRepository = NotificationRepository(database.notificationDao())

        // NEW: CoroutineScope for application-level background tasks
        val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        // NEW: Observe _pendingNotificationForSave and insert into DB once repository is ready
        applicationScope.launch {
            _pendingNotificationForSave.filterNotNull().collect { notificationToSave ->
                try {
                    notificationRepository.insertNotification(notificationToSave)
                    Log.d("TeamSyncApplication", "Successfully saved pending notification from cold start: ${notificationToSave.title}")
                    _pendingNotificationForSave.value = null // Clear after saving
                } catch (e: Exception) {
                    Log.e("TeamSyncApplication", "Failed to save pending notification from cold start: ${e.message}", e)
                }
            }
        }

        groupMonitorService.startMonitoring()
        Log.d("TeamSyncApplication", "GroupMonitorService.startMonitoring() called from Application onCreate.")
    }

    override fun onTerminate() {
        super.onTerminate()
        Log.d("TeamSyncApplication", "Application onTerminate: Shutting down services.")
        unregisterActivityLifecycleCallbacks(this)
        groupMonitorService.shutdown()
        markerMonitorService.shutdown()
        // Room database manages its own lifecycle, no explicit shutdown needed here.
    }

    // --- Activity Lifecycle Callback Implementations ---

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        Log.d("AppStatus", "onActivityCreated: ${activity.javaClass.simpleName}")
    }

    override fun onActivityStarted(activity: Activity) {
        activityCount++
        Log.d("AppStatus", "onActivityStarted: ${activity.javaClass.simpleName}, Count: $activityCount")
        backgroundDetectionRunnable?.let { handler.removeCallbacks(it) }
        isAppInForeground = true
    }

    override fun onActivityResumed(activity: Activity) {
        Log.d("AppStatus", "onActivityResumed: ${activity.javaClass.simpleName}")
    }

    override fun onActivityPaused(activity: Activity) {
        Log.d("AppStatus", "onActivityPaused: ${activity.javaClass.simpleName}")
        backgroundDetectionRunnable = Runnable {
            if (activityCount == 0) {
                isAppInForeground = false
                Log.d("AppStatus", "App detected as in background")
            }
        }
        handler.postDelayed(backgroundDetectionRunnable!!, 500)
    }

    override fun onActivityStopped(activity: Activity) {
        activityCount--
        Log.d("AppStatus", "onActivityStopped: ${activity.javaClass.simpleName}, Count: $activityCount")
    }

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
        Log.d("AppStatus", "onActivitySaveInstanceState: ${activity.javaClass.simpleName}")
    }

    override fun onActivityDestroyed(activity: Activity) {
        Log.d("AppStatus", "onActivityDestroyed: ${activity.javaClass.simpleName}")
    }
}
