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

    companion object {
        const val CHANNEL_ID = "TeamSyncLocationChannel"
        const val NOTIFICATION_ID = 12345
        const val ACTION_START_SERVICE = "ACTION_START_SERVICE"
        const val ACTION_STOP_SERVICE = "ACTION_STOP_SERVICE"
        const val EXTRA_LOCATION_INTERVAL = "extra_location_interval"
        const val EXTRA_IS_SHARING_LOCATION = "extra_is_sharing_location"
    }

    override fun onCreate() {
        super.onCreate()
        Log.d("LocationService", "Service onCreate")
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        setupLocationCallback()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("LocationService", "Service onStartCommand: STARTING. Action: ${intent?.action}")

        // --- CRITICAL FIX: Always call startForeground immediately when ACTION_START_SERVICE ---
        // This must happen within 5 seconds of startForegroundService() being called.
        // It's the very first thing we do if the intent is to start the service.
        if (intent?.action == ACTION_START_SERVICE) {
            // NEW: Check for POST_NOTIFICATIONS permission on Android 13+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                Log.e("LocationService", "POST_NOTIFICATIONS permission not granted. Cannot start foreground service.")
                // Stop the service immediately if it cannot enter foreground state
                // This prevents the ForegroundServiceDidNotStartInTimeException
                stopSelf()
                return START_NOT_STICKY // Do not restart if foreground setup failed
            }

            try {
                createNotificationChannel()
                val notification = buildNotification()
                startForeground(NOTIFICATION_ID, notification.build())
                Log.d("LocationService", "Called startForeground successfully for ACTION_START_SERVICE.")
            } catch (e: Exception) {
                // If startForeground fails critically, log and stop the service to avoid ANR/crash
                Log.e("LocationService", "CRITICAL ERROR: Failed to call startForeground: ${e.message}", e)
                stopSelf() // Stop service immediately if it cannot enter foreground state
                return START_NOT_STICKY // Do not restart if foreground setup failed
            }
        }

        when (intent?.action) {
            ACTION_START_SERVICE -> {
                val locationInterval = intent.getLongExtra(EXTRA_LOCATION_INTERVAL, 300000L)
                val isSharingLocation = intent.getBooleanExtra(EXTRA_IS_SHARING_LOCATION, true)

                Log.d("LocationService", "Service onStartCommand: Handling ACTION_START_SERVICE. Sharing: $isSharingLocation, Interval: $locationInterval.")
                if (isSharingLocation) {
                    // Defensive check: Ensure permissions are still granted before requesting updates
                    if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                        startLocationUpdates(locationInterval)
                        Log.d("LocationService", "Started location updates after defensive permission check.")
                    } else {
                        Log.e("LocationService", "Location permission not granted when trying to start location updates in FGS. Stopping updates and service.")
                        stopLocationUpdates()
                        stopSelf() // Stop service if it cannot perform its core function (tracking)
                    }
                } else {
                    Log.d("LocationService", "Location sharing disabled by user, stopping updates but keeping service foreground.")
                    stopLocationUpdates()
                }
            }
            ACTION_STOP_SERVICE -> {
                Log.d("LocationService", "Service onStartCommand: Handling ACTION_STOP_SERVICE. Stopping service.")
                stopLocationUpdates()
                stopSelf()
            }
            else -> {
                Log.d("LocationService", "Service onStartCommand: Unknown action or implicit restart. Action: ${intent?.action}.")
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("LocationService", "Service onDestroy")
        stopLocationUpdates()
        serviceScope.cancel()
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
            .setContentTitle("TeamSync is tracking your location")
            .setContentText("Your location is being shared with your active group.")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
    }

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

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates(interval: Long) {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, interval)
            .setMinUpdateIntervalMillis(interval / 2)
            .build()

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
            Log.d("LocationService", "Started FusedLocationProviderClient updates with interval: $interval ms")
        } catch (e: SecurityException) {
            Log.e("LocationService", "FusedLocationProviderClient update request failed due to permission: ${e.message}")
        }
    }

    private fun stopLocationUpdates() {
        if (::fusedLocationClient.isInitialized) {
            fusedLocationClient.removeLocationUpdates(locationCallback)
            Log.d("LocationService", "Stopped FusedLocationProviderClient updates.")
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
                val historyResult = firestoreService.addLocationLog(locationData)
                if (historyResult.isSuccess) {
                    Log.d("LocationService", "Location history saved for user $userId. Doc ID: ${historyResult.getOrNull()}")
                } else {
                    Log.e("LocationService", "Failed to save location history for user $userId: ${historyResult.exceptionOrNull()?.message}")
                }

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
