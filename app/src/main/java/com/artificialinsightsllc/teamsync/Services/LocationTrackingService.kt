// In file: app/src/main/java/com/artificialinsightsllc/teamsync/Services/LocationTrackingService.kt
package com.artificialinsightsllc.teamsync.Services

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.artificialinsightsllc.teamsync.MainActivity // Import your MainActivity
import com.artificialinsightsllc.teamsync.Models.Locations // Import your Locations model
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import com.artificialinsightsllc.teamsync.R // Import R for your drawable resources

class LocationTrackingService : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO) // Coroutine scope for the service
    private val firestoreService = FirestoreService() // Instance of your FirestoreService
    private val auth = FirebaseAuth.getInstance() // Firebase Auth instance

    // Notification Channel ID (must be unique for your app)
    companion object {
        const val CHANNEL_ID = "TeamSyncLocationChannel"
        const val NOTIFICATION_ID = 12345
        const val ACTION_START_SERVICE = "ACTION_START_SERVICE"
        const val ACTION_STOP_SERVICE = "ACTION_STOP_SERVICE"
        const val EXTRA_LOCATION_INTERVAL = "extra_location_interval" // To pass interval from UI
        const val EXTRA_IS_SHARING_LOCATION = "extra_is_sharing_location" // To pass sharing status
    }

    override fun onCreate() {
        super.onCreate()
        Log.d("LocationService", "Service onCreate")
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        setupLocationCallback() // Initialize the location callback
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("LocationService", "Service onStartCommand: ${intent?.action}")

        // --- CRITICAL FIX: Always call startForeground immediately when ACTION_START_SERVICE ---
        // This must happen within 5 seconds of startForegroundService() being called.
        // It's the very first thing we do if the intent is to start the service.
        if (intent?.action == ACTION_START_SERVICE) {
            createNotificationChannel()
            val notification = buildNotification()
            startForeground(NOTIFICATION_ID, notification.build())
            Log.d("LocationService", "Called startForeground immediately for ACTION_START_SERVICE.")
        }

        when (intent?.action) {
            ACTION_START_SERVICE -> {
                val locationInterval = intent.getLongExtra(EXTRA_LOCATION_INTERVAL, 300000L) // Default 5 min
                val isSharingLocation = intent.getBooleanExtra(EXTRA_IS_SHARING_LOCATION, true) // Default true

                if (isSharingLocation) { // Only start tracking if sharing is enabled
                    startLocationUpdates(locationInterval)
                } else {
                    Log.d("LocationService", "Location sharing disabled by user, stopping updates but keeping service foreground.")
                    stopLocationUpdates() // Stop actual GPS updates
                    // Do NOT call stopSelf() here. GroupMonitorService will eventually call stopService()
                    // when it determines the service is no longer needed at all (e.g., user leaves group).
                }
            }
            ACTION_STOP_SERVICE -> {
                stopLocationUpdates()
                stopSelf() // Fully stop the service
            }
        }
        // If the system kills the service, it will restart it but not redeliver the last intent.
        // START_REDELIVER_INTENT would redeliver the last intent.
        return START_STICKY // Service will be recreated if killed, but onStartCommand receives null intent
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null // Not a bound service
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("LocationService", "Service onDestroy")
        stopLocationUpdates() // Ensure updates are stopped
        serviceScope.cancel() // Cancel all coroutines launched in this scope
    }

    // --- Notification Setup ---
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "TeamSync Location Service",
                NotificationManager.IMPORTANCE_LOW // Low importance to be less intrusive
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun buildNotification(): NotificationCompat.Builder {
        val notificationIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE // Required for Android 12+
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("TeamSync is tracking your location")
            .setContentText("Your location is being shared with your active group.")
            .setSmallIcon(R.drawable.ic_launcher_foreground) // Use a proper icon for production
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true) // Makes the notification non-dismissible by user
    }

    // --- Location Update Logic ---
    private fun setupLocationCallback() {
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    Log.d("LocationService", "Location received: ${location.latitude}, ${location.longitude}")
                    sendLocationToFirestore(location)
                }
            }
        }
    }

    @SuppressLint("MissingPermission") // Permissions are handled in MainScreen before starting service
    private fun startLocationUpdates(interval: Long) {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, interval)
            .setMinUpdateIntervalMillis(interval / 2) // Update at least half the interval
            .build()

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper() // Use main looper for callbacks
            )
            Log.d("LocationService", "Started location updates with interval: $interval ms")
        } catch (e: SecurityException) {
            Log.e("LocationService", "Location permission denied when starting updates: ${e.message}")
            // If permissions are revoked while service is running, it might fail here.
            // GroupMonitorService should handle stopping the service if permissions are missing.
        }
    }

    private fun stopLocationUpdates() {
        if (::fusedLocationClient.isInitialized) { // Check if initialized before removing updates
            fusedLocationClient.removeLocationUpdates(locationCallback)
            Log.d("LocationService", "Stopped location updates")
        }
    }

    private fun sendLocationToFirestore(location: Location) {
        val userId = auth.currentUser?.uid
        if (userId != null) {
            val locationData = Locations(
                userId = userId,
                latitude = location.latitude,
                longitude = location.longitude,
                timestamp = System.currentTimeMillis(),
                speed = if (location.hasSpeed()) location.speed else null,
                bearing = if (location.hasBearing()) location.bearing else null
            )
            serviceScope.launch {
                // Save to history
                val historyResult = firestoreService.addLocationLog(locationData)
                if (historyResult.isSuccess) {
                    Log.d("LocationService", "Location history saved for user $userId. Doc ID: ${historyResult.getOrNull()}")
                } else {
                    Log.e("LocationService", "Failed to save location history for user $userId: ${historyResult.exceptionOrNull()?.message}")
                }

                // Save to current location (overwrites previous)
                val currentResult = firestoreService.saveCurrentLocation(locationData)
                if (currentResult.isSuccess) {
                    Log.d("LocationService", "Current location updated for user $userId.")
                } else {
                    Log.e("LocationService", "Failed to update current location for user $userId: ${currentResult.exceptionOrNull()?.message}")
                }
            }
        } else {
            Log.w("LocationService", "No authenticated user to save location for.")
        }
    }
}