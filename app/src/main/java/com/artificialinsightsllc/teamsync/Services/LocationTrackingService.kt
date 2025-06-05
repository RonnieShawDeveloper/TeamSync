// In file: app/src/main/java/com/artificialinsightsllc/teamsync/Services/LocationTrackingService.kt
package com.artificialinsightsllc.teamsync.Services

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.artificialinsightsllc.teamsync.MainActivity
import com.artificialinsightsllc.teamsync.Models.Locations
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
import com.artificialinsightsllc.teamsync.R

class LocationTrackingService : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val firestoreService = FirestoreService()
    private val auth = FirebaseAuth.getInstance()

    // Flag to keep track of whether location updates are currently requested
    private var isUpdatingLocation: Boolean = false

    companion object {
        const val CHANNEL_ID = "TeamSyncLocationChannel"
        const val NOTIFICATION_ID = 12345
        const val ACTION_START_SERVICE = "ACTION_START_SERVICE"
        const val ACTION_STOP_SERVICE = "ACTION_STOP_SERVICE"
        // New action to update tracking state without necessarily starting/stopping the service itself
        const val ACTION_UPDATE_TRACKING_STATE = "ACTION_UPDATE_TRACKING_STATE"
        const val EXTRA_LOCATION_INTERVAL = "extra_location_interval"
        const val EXTRA_IS_SHARING_LOCATION = "extra_is_sharing_location" // True to start tracking, false to stop
    }

    override fun onCreate() {
        super.onCreate()
        Log.d("LocationService", "Service onCreate")
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        setupLocationCallback()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("LocationService", "Service onStartCommand: STARTING. Action: ${intent?.action}")

        // This block ensures the service immediately goes into the foreground state
        // regardless of location tracking status. This is CRITICAL for preventing
        // ForegroundServiceDidNotStartInTimeException.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Check for POST_NOTIFICATIONS permission on Android 13+ before calling startForeground
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                Log.e("LocationService", "POST_NOTIFICATIONS permission not granted. Cannot start foreground service (notification requirement).")
                stopSelf() // Cannot be foreground without notification, so stop
                return START_NOT_STICKY
            }
        }

        try {
            createNotificationChannel()
            val notification = buildNotification()
            startForeground(NOTIFICATION_ID, notification.build())
            Log.d("LocationService", "Called startForeground successfully.")
        } catch (e: Exception) {
            Log.e("LocationService", "CRITICAL ERROR: Failed to call startForeground: ${e.message}", e)
            stopSelf() // Stop service immediately if it cannot enter foreground state
            return START_NOT_STICKY
        }


        // Handle different actions from intents
        when (intent?.action) {
            ACTION_START_SERVICE, ACTION_UPDATE_TRACKING_STATE -> {
                val locationInterval = intent.getLongExtra(EXTRA_LOCATION_INTERVAL, 300000L) // Default 5 min
                val isSharingLocation = intent.getBooleanExtra(EXTRA_IS_SHARING_LOCATION, true) // Default true

                Log.d("LocationService", "Handling action: ${intent?.action}. Sharing: $isSharingLocation, Interval: $locationInterval.")

                if (isSharingLocation) {
                    // Start or update location tracking if permissions are granted
                    if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                        if (!isUpdatingLocation) { // Only call startLocationUpdates if not already updating
                            startLocationUpdates(locationInterval)
                            isUpdatingLocation = true
                            Log.d("LocationService", "Started location updates.")
                        } else {
                            // If already updating, but interval changed, update the request
                            // (requires removing and re-adding updates, or recreating LocationRequest)
                            stopLocationUpdates() // Stop existing updates
                            startLocationUpdates(locationInterval) // Start with new interval
                            Log.d("LocationService", "Updated location updates interval.")
                        }
                    } else {
                        Log.e("LocationService", "Location permission not granted. Cannot start/continue location updates.")
                        stopLocationUpdates() // Stop updates if permissions are suddenly revoked or not present
                        isUpdatingLocation = false
                        // Do not stopSelf here. The service should remain foreground, but not track.
                    }
                } else {
                    // Stop location updates if sharing is disabled, but keep service foreground
                    Log.d("LocationService", "Location sharing disabled, stopping updates.")
                    stopLocationUpdates()
                    isUpdatingLocation = false
                }
            }
            ACTION_STOP_SERVICE -> {
                Log.d("LocationService", "Handling ACTION_STOP_SERVICE. Stopping service completely.")
                stopLocationUpdates()
                isUpdatingLocation = false
                stopSelf() // Completely stop the service
            }
        }
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
        isUpdatingLocation = false
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "TeamSync Location Service",
                NotificationManager.IMPORTANCE_LOW
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
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("TeamSync is running in the background") // Generic title
            .setContentText("Tap to open. Your location is being managed based on group settings.") // More descriptive text
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true) // Makes the notification non-dismissible by user
    }

    private fun setupLocationCallback() {
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    Log.d("LocationService", "Location received: ${location.latitude}, ${location.longitude}")
                    // Only send to Firestore if currently active (isUpdatingLocation flag) and user is authenticated
                    if (isUpdatingLocation && auth.currentUser != null) {
                        sendLocationToFirestore(location)
                    } else {
                        Log.d("LocationService", "Location received, but not sending to Firestore (not updating or user logged out).")
                    }
                }
            }
        }
    }

    @SuppressLint("MissingPermission") // Permissions are handled in MainScreen before starting service, and checked in onStartCommand
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
            Log.d("LocationService", "Requested FusedLocationProviderClient updates with interval: $interval ms")
        } catch (e: SecurityException) {
            Log.e("LocationService", "Location permission denied when starting updates: ${e.message}")
            isUpdatingLocation = false // Ensure flag is false if this fails
        }
    }

    private fun stopLocationUpdates() {
        if (::fusedLocationClient.isInitialized && isUpdatingLocation) { // Only try to remove if initialized AND currently updating
            fusedLocationClient.removeLocationUpdates(locationCallback)
            Log.d("LocationService", "Stopped FusedLocationProviderClient updates.")
            isUpdatingLocation = false
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
                    // Log.d("LocationService", "Location history saved for user $userId. Doc ID: ${historyResult.getOrNull()}")
                } else {
                    Log.e("LocationService", "Failed to save location history for user $userId: ${historyResult.exceptionOrNull()?.message}")
                }

                // Save to current location (overwrites previous)
                val currentResult = firestoreService.saveCurrentLocation(locationData)
                if (currentResult.isSuccess) {
                    // Log.d("LocationService", "Current location updated for user $userId.")
                } else {
                    Log.e("LocationService", "Failed to update current location for user $userId: ${currentResult.exceptionOrNull()?.message}")
                }
            }
        } else {
            Log.w("LocationService", "No authenticated user to save location for.")
        }
    }
}
