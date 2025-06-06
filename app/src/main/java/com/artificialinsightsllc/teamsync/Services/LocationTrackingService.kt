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
import com.artificialinsightsllc.teamsync.Helpers.BatteryInfoHelper
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
import com.artificialinsightsllc.teamsync.TeamSyncApplication

class LocationTrackingService : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val firestoreService = FirestoreService()
    private val auth = FirebaseAuth.getInstance()

    private var activeGroupMemberId: String? = null

    companion object {
        const val CHANNEL_ID = "TeamSyncLocationChannel"
        const val NOTIFICATION_ID = 12345
        const val ACTION_START_SERVICE = "ACTION_START_SERVICE"
        const val ACTION_STOP_SERVICE = "ACTION_STOP_SERVICE"
        const val ACTION_UPDATE_TRACKING_STATE = "ACTION_UPDATE_TRACKING_STATE"
        const val EXTRA_LOCATION_INTERVAL = "extra_location_interval"
        const val EXTRA_IS_SHARING_LOCATION = "extra_is_sharing_location"
        const val EXTRA_ACTIVE_GROUP_MEMBER_ID = "extra_active_group_member_id"
    }

    override fun onCreate() {
        super.onCreate()
        Log.d("LocationService", "Service onCreate")
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        setupLocationCallback()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("LocationService", "Service onStartCommand: STARTING. Action: ${intent?.action}")

        // Ensure service runs in foreground (required for location tracking)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                Log.e("LocationService", "POST_NOTIFICATIONS permission not granted. Cannot start foreground service (notification requirement).")
                stopSelf()
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
            stopSelf()
            return START_NOT_STICKY
        }


        // Handle different actions from intents
        when (intent?.action) {
            ACTION_START_SERVICE, ACTION_UPDATE_TRACKING_STATE -> {
                val locationInterval = intent.getLongExtra(EXTRA_LOCATION_INTERVAL, 300000L)
                val isSharingLocation = intent.getBooleanExtra(EXTRA_IS_SHARING_LOCATION, true)
                activeGroupMemberId = intent.getStringExtra(EXTRA_ACTIVE_GROUP_MEMBER_ID)

                Log.d("LocationService", "onStartCommand received activeGroupMemberId: $activeGroupMemberId")
                Log.d("LocationService", "Handling action: ${intent?.action}. Sharing: $isSharingLocation, Interval: $locationInterval. Member ID: $activeGroupMemberId")

                if (isSharingLocation && ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED && activeGroupMemberId != null) {
                    startLocationUpdates(locationInterval)
                    Log.d("LocationService", "Location updates STARTED based on intent.")
                } else {
                    stopLocationUpdates()
                    Log.d("LocationService", "Location updates STOPPED based on intent (sharing=$isSharingLocation, memberIdIsNull=${activeGroupMemberId == null}).")
                }
            }
            ACTION_STOP_SERVICE -> {
                Log.d("LocationService", "Handling ACTION_STOP_SERVICE. Stopping service completely.")
                stopLocationUpdates()
                activeGroupMemberId = null
                stopSelf()
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
        activeGroupMemberId = null
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
            .setContentTitle("TeamSync is running in the background")
            .setContentText("Tap to open. Your location is being managed based on group settings.")
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

                    if (auth.currentUser != null && activeGroupMemberId != null) {
                        sendLocationAndStatusToFirestore(location, activeGroupMemberId!!)
                    } else {
                        Log.d("LocationService", "Location received, but not sending to Firestore (userLogged=${auth.currentUser?.uid != null}, memberIdIsNull=${activeGroupMemberId == null}).")
                    }
                }
            }
        }
    }

    @SuppressLint("MissingPermission") // Permissions are handled in PreCheckScreen and checked in onStartCommand
    private fun startLocationUpdates(interval: Long) {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, interval)
            // FIX: Changed setMinUpdateIntervalMillis to match the desired interval
            .setMinUpdateIntervalMillis(interval)
            .build()

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
            Log.d("LocationService", "Requested FusedLocationProviderClient updates with interval: $interval ms")
        } catch (e: SecurityException) {
            Log.e("LocationService", "Location permission denied when starting updates: ${e.message}")
        }
    }

    private fun stopLocationUpdates() {
        if (::fusedLocationClient.isInitialized) {
            fusedLocationClient.removeLocationUpdates(locationCallback)
            Log.d("LocationService", "Stopped FusedLocationProviderClient updates.")
        }
    }

    /**
     * Sends location, battery status, and app status to Firestore.
     * This is the authoritative update point for the user's current status.
     */
    private fun sendLocationAndStatusToFirestore(location: Location, memberId: String) {
        val userId = auth.currentUser?.uid
        if (userId != null) {
            val (batteryLevel, chargingStatus) = BatteryInfoHelper.getBatteryInfo(this)

            val appStatus = (application as TeamSyncApplication).isAppInForeground.let {
                if (it) "FOREGROUND" else "BACKGROUND"
            }

            val locationData = Locations(
                userId = userId,
                latitude = location.latitude,
                longitude = location.longitude,
                timestamp = System.currentTimeMillis(),
                speed = if (location.hasSpeed()) location.speed else null,
                bearing = if (location.hasBearing()) location.bearing else null
            )
            serviceScope.launch {
                // 1. Save to history
                val historyResult = firestoreService.addLocationLog(locationData)
                if (historyResult.isSuccess) {
                    // Log.d("LocationService", "Location history saved for user $userId. Doc ID: ${historyResult.getOrNull()}")
                } else {
                    Log.e("LocationService", "Failed to save location history for user $userId: ${historyResult.exceptionOrNull()?.message}")
                }

                // 2. Save to current_user_locations (overwrites previous)
                val currentResult = firestoreService.saveCurrentLocation(locationData)
                if (currentResult.isSuccess) {
                    Log.d("LocationService", "Current location updated for user $userId.")
                } else {
                    Log.e("LocationService", "Failed to update current location for user $userId: ${currentResult.exceptionOrNull()?.message}")
                }

                // 3. Update GroupMembers record with location, battery, and app status
                val memberUpdateResult = firestoreService.updateGroupMemberStatus(
                    memberId = memberId,
                    newLat = location.latitude,
                    newLon = location.longitude,
                    newUpdateTime = System.currentTimeMillis(),
                    newBatteryLevel = batteryLevel,
                    newBatteryChargingStatus = chargingStatus,
                    newAppStatus = appStatus
                )
                if (memberUpdateResult.isSuccess) {
                    Log.d("LocationService", "Group member status updated successfully for member $memberId (Lat: ${location.latitude}, Lon: ${location.longitude}, Battery: $batteryLevel%, AppStatus: $appStatus)")
                } else {
                    Log.e("LocationService", "Failed to update group member status for member $memberId: ${memberUpdateResult.exceptionOrNull()?.message}")
                }
            }
        } else {
            Log.w("LocationService", "No authenticated user to save location for.")
        }
    }
}
