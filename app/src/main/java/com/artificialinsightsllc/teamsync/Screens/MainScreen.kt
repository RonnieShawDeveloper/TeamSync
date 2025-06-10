// In file: app/src/main/java/com/artificialinsightsllc/teamsync/Screens/MainScreen.kt
package com.artificialinsightsllc.teamsync.Screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.os.Build
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Polyline
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Close // Import Close icon
import androidx.compose.material.icons.filled.ExitToApp // Icon for Logout/Shutdown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavHostController
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices.getFusedLocationProviderClient
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberMarkerState
import com.artificialinsightsllc.teamsync.Navigation.NavRoutes
import com.artificialinsightsllc.teamsync.R
import com.artificialinsightsllc.teamsync.Models.UserModel
import com.artificialinsightsllc.teamsync.Models.Locations
import com.artificialinsightsllc.teamsync.Models.MapMarker
import com.artificialinsightsllc.teamsync.Models.MapMarkerType
import com.artificialinsightsllc.teamsync.Helpers.MarkerIconHelper
import com.artificialinsightsllc.teamsync.Helpers.TimeFormatter
import com.artificialinsightsllc.teamsync.Helpers.UnitConverter
import com.artificialinsightsllc.teamsync.Services.GroupMonitorService
import com.artificialinsightsllc.teamsync.Services.FirestoreService
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import androidx.compose.ui.geometry.Offset
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextAlign
import com.artificialinsightsllc.teamsync.Screens.MarkerInfoDialog
import com.artificialinsightsllc.teamsync.TeamSyncApplication
import com.artificialinsightsllc.teamsync.Services.LocationTrackingService
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit

// NEW: Add BorderStroke import for the close button
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape // Import CircleShape

// NEW: Import DarkBlue and LightCream from your theme colors
import com.artificialinsightsllc.teamsync.ui.theme.DarkBlue
import com.artificialinsightsllc.teamsync.ui.theme.LightCream
import androidx.compose.foundation.shape.RoundedCornerShape // For card shape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.draw.shadow


class MainScreen(private val navController: NavHostController) {

    data class MarkerDisplayInfo(
        val title: String,
        val timestamp: Long,
        val speed: Float?,
        val bearing: Float?,
        val profilePhotoUrl: String?,
        val latLng: LatLng?,
        val mapMarker: MapMarker? = null,
        val userId: String? = null
    )

    @OptIn(ExperimentalMaterial3Api::class)
    @SuppressLint("MissingPermission")
    @Composable
    fun Content() {
        val context = LocalContext.current
        val lifecycleOwner = LocalLifecycleOwner.current
        val toastMessage: (String) -> Unit = { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }

        val teamSyncApplication = context.applicationContext as TeamSyncApplication
        val groupMonitorService = teamSyncApplication.groupMonitorService
        val markerMonitorService = teamSyncApplication.markerMonitorService
        val firestoreService = remember { FirestoreService() }
        val coroutineScope = rememberCoroutineScope()

        // UI state variables
        var userLocation by remember { mutableStateOf<Location?>(null) }
        var mapLockedToUserLocation by remember { mutableStateOf(true) }
        var hasPerformedInitialCenter by remember { mutableStateOf(false) } // Indicates if map has centered on user's first GPS fix

        var showPermissionRationaleDialog by remember { mutableStateOf(false) }
        var allPermissionsGranted by remember { mutableStateOf(false) }
        var hasNotificationPermission by remember { mutableStateOf(false) }

        var showExpiredGroupDialog by remember { mutableStateOf(false) }
        var expiredGroupDialogMessage by remember { mutableStateOf<String?>(null) }

        val snackbarHostState = remember { SnackbarHostState() }
        var showCustomMarkerInfoDialog by remember { mutableStateOf(false) }
        var currentMarkerInfo by remember { mutableStateOf<MarkerDisplayInfo?>(null) }

        var showMapLoadingDialog by remember { mutableStateOf(true) }

        // State for the countdown timer
        var countdownString by remember { mutableStateOf("") }


        val cameraPositionState = rememberCameraPositionState {
            position = LatLng(39.8283, -98.5795).let { CameraPosition.fromLatLngZoom(it, 3f) }
        }
        val fusedLocationClient: FusedLocationProviderClient = remember {
            getFusedLocationProviderClient(context)
        }

        val isInGroup by groupMonitorService.isInActiveGroup.collectAsStateWithLifecycle()
        val activeGroup by groupMonitorService.activeGroup.collectAsStateWithLifecycle()
        val userMessage by groupMonitorService.userMessage.collectAsStateWithLifecycle()
        val effectiveLocationUpdateInterval by groupMonitorService.effectiveLocationUpdateInterval.collectAsStateWithLifecycle()

        val otherMembersLocations by groupMonitorService.otherMembersLocations.collectAsStateWithLifecycle()
        val otherMembersProfiles by groupMonitorService.otherMembersProfiles.collectAsStateWithLifecycle()
        val mapMarkers by markerMonitorService.mapMarkers.collectAsStateWithLifecycle()
        Log.d("MainScreen", "Collected ${mapMarkers.size} map markers.")


        val allPermissionsNeeded = remember {
            mutableListOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.CAMERA,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    Manifest.permission.READ_MEDIA_IMAGES
                } else {
                    Manifest.permission.READ_EXTERNAL_STORAGE
                }
            ).apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    add(Manifest.permission.POST_NOTIFICATIONS)
                }
            }.filterNotNull().toTypedArray()
        }


        fun startAppTrackingService() {
            val serviceIntent = Intent(context, LocationTrackingService::class.java).apply {
                action = LocationTrackingService.ACTION_START_SERVICE
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                try {
                    context.startForegroundService(serviceIntent)
                    Log.d("MainScreen", "Attempted to start foreground service via startForegroundService.")
                } catch (e: Exception) {
                    Log.e("MainScreen", "Failed to start foreground service: ${e.message}")
                    Toast.makeText(context, "Cannot start background location service. Please open the app or grant necessary permissions.", Toast.LENGTH_LONG).show()
                }
            } else {
                context.startService(serviceIntent)
                Log.d("MainScreen", "Attempted to start service via startService (pre-Oreo).")
            }
        }

        fun stopAppTrackingService() {
            val serviceIntent = Intent(context, LocationTrackingService::class.java).apply {
                action = LocationTrackingService.ACTION_STOP_SERVICE
            }
            context.stopService(serviceIntent)
            Log.d("MainScreen", "Requested stop of LocationTrackingService.")
        }

        val locationCallback = remember {
            object : LocationCallback() {
                override fun onLocationResult(locationResult: LocationResult) {
                    locationResult.lastLocation?.let { location ->
                        userLocation = location
                        Log.d("MainScreen", "Local GPS Location received: ${location.latitude}, ${location.longitude}")
                        Log.d("MainScreen", "MainScreen local location received, but not sending to Firestore. LocationTrackingService is responsible for persistence.")
                    }
                }
            }
        }

        val multiplePermissionsLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            allPermissionsGranted = permissions.all { it.value }
            hasNotificationPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                permissions[Manifest.permission.POST_NOTIFICATIONS] ?: false
            } else {
                true
            }

            if (allPermissionsGranted && hasNotificationPermission) {
                Log.d("MainScreen", "All required permissions granted via launcher. Attempting to start Foreground Service.")
                startAppTrackingService()
            } else {
                Log.w("MainScreen", "Not all required permissions granted via launcher. Foreground Service will not start or will stop.")
                stopAppTrackingService()
            }
            groupMonitorService.setUiPermissionsGranted(allPermissionsGranted && hasNotificationPermission)
        }


        var currentUserModel by remember { mutableStateOf<UserModel?>(null) }

        var showInstructionsOverlay by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) {
            val auth = FirebaseAuth.getInstance()
            val firestore = FirebaseFirestore.getInstance()
            val userId = auth.currentUser?.uid

            if (userId != null) {
                try {
                    val userDocRef = firestore.collection("users").document(userId)
                    val userSnapshot = userDocRef.get().await()

                    if (userSnapshot.exists()) {
                        currentUserModel = userSnapshot.toObject(UserModel::class.java)
                        Log.d("MainScreen", "User profile loaded: ${currentUserModel?.displayName}")

                        if (currentUserModel?.mainInstructionsSeen != true) {
                            showInstructionsOverlay = true
                        }

                    } else {
                        Log.w("MainScreen", "User document not found for ID: $userId")
                    }
                } catch (e: Exception) {
                    Log.e("MainScreen", "Error loading user profile: ${e.message}")
                }
            } else {
                Log.d("MainScreen", "No authenticated user found. Consider navigating to LoginScreen.")
            }

            val currentAllPermissionsGranted = allPermissionsNeeded.all {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }
            val currentHasNotificationPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }

            allPermissionsGranted = currentAllPermissionsGranted
            hasNotificationPermission = currentHasNotificationPermission

            if (!(currentAllPermissionsGranted && currentHasNotificationPermission)) {
                showPermissionRationaleDialog = true
            } else {
                Log.d("MainScreen", "All permissions initially granted on app launch. Attempting to start Foreground Service immediately.")
                startAppTrackingService()
            }
            groupMonitorService.setUiPermissionsGranted(allPermissionsGranted && hasNotificationPermission)
        }

        // Countdown Timer Logic
        LaunchedEffect(activeGroup) {
            val endTime = activeGroup?.groupEndTimestamp
            if (endTime != null) {
                while (true) {
                    val remaining = endTime - System.currentTimeMillis()
                    if (remaining > 0) {
                        val days = TimeUnit.MILLISECONDS.toDays(remaining)
                        val hours = TimeUnit.MILLISECONDS.toHours(remaining) % 24
                        val minutes = TimeUnit.MILLISECONDS.toMinutes(remaining) % 60
                        val seconds = TimeUnit.MILLISECONDS.toSeconds(remaining) % 60
                        countdownString = String.format("%02d:%02d:%02d:%02d", days, hours, minutes, seconds)
                    } else {
                        countdownString = "00:00:00:00"
                        break
                    }
                    delay(1000L)
                }
            } else {
                countdownString = ""
            }
        }


        DisposableEffect(lifecycleOwner, fusedLocationClient, allPermissionsGranted, effectiveLocationUpdateInterval) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_START) {
                    val fineLocationGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                    val coarseLocationGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                    val allLocationPermissionsGrantedForUI = (fineLocationGranted || coarseLocationGranted)

                    if (allLocationPermissionsGrantedForUI) {
                        startLocationUpdates(fusedLocationClient, locationCallback, effectiveLocationUpdateInterval)
                    } else {
                        Log.w("MainScreen", "Not all required location permissions are granted on ON_START. Local GPS updates for map dot will not start.")
                        Toast.makeText(context, "Location permission missing. Map features may be limited.", Toast.LENGTH_LONG).show()
                    }
                } else if (event == Lifecycle.Event.ON_STOP) {
                    stopLocationUpdates(fusedLocationClient, locationCallback)
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
                fusedLocationClient.removeLocationUpdates(locationCallback)
                Log.d("MainScreen", "Stopped local GPS location updates on dispose.")
            }
        }

        LaunchedEffect(hasPerformedInitialCenter) {
            if (hasPerformedInitialCenter) {
                showMapLoadingDialog = false
            }
        }

        LaunchedEffect(isInGroup, allPermissionsGranted, hasNotificationPermission) {
            if (!(allPermissionsGranted && hasNotificationPermission) && !showPermissionRationaleDialog) {
                Log.d("MainScreen", "Permissions changed or missing, triggering rationale check.")
                showPermissionRationaleDialog = true
            } else if (showPermissionRationaleDialog && (allPermissionsGranted && hasNotificationPermission)) {
                showPermissionRationaleDialog = false
            }
        }

        LaunchedEffect(userLocation, mapLockedToUserLocation) {
            userLocation?.let { loc ->
                val newLatLng = LatLng(loc.latitude, loc.longitude)

                if (mapLockedToUserLocation || !hasPerformedInitialCenter) {
                    cameraPositionState.animate(
                        CameraUpdateFactory.newLatLngZoom(newLatLng, 15f)
                    )
                    Log.d("MainScreen", "Camera moved to: ${loc.latitude}, ${loc.longitude}")
                    if (!hasPerformedInitialCenter) {
                        hasPerformedInitialCenter = true
                    }
                }
            }
        }

        LaunchedEffect(userMessage) {
            userMessage?.let { message ->
                if (message.contains("has expired. You have been automatically removed.")) {
                    expiredGroupDialogMessage = message
                    showExpiredGroupDialog = true
                } else {
                    snackbarHostState.showSnackbar(message)
                }
                groupMonitorService.clearUserMessage()
            }
        }


        val fabEnabledColor = MaterialTheme.colorScheme.primaryContainer
        val fabDisabledColor = MaterialTheme.colorScheme.surfaceVariant
        val fabEnabledContentColor = MaterialTheme.colorScheme.onPrimaryContainer
        val fabDisabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)


        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            modifier = Modifier.fillMaxSize()
        ) { innerPadding ->
            Box(modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
            ) {
                GoogleMap(
                    modifier = Modifier.fillMaxSize(),
                    cameraPositionState = cameraPositionState,
                    properties = MapProperties(
                        isMyLocationEnabled = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED,
                        isBuildingEnabled = true
                    ),
                    uiSettings = MapUiSettings(zoomControlsEnabled = false)
                ) {
                    val isCurrentUserLocationStale = userLocation?.let {
                        System.currentTimeMillis() - it.time >= 300000L
                    } ?: false

                    userLocation?.let { loc ->
                        val userLatLng = LatLng(loc.latitude, loc.longitude)
                        val markerState = rememberMarkerState()

                        LaunchedEffect(userLocation) {
                            markerState.position = userLatLng
                        }

                        val userProfilePicUrl = currentUserModel?.profilePhotoUrl
                        val userMarkerIcon = MarkerIconHelper.rememberUserMarkerIcon(
                            profileImageUrl = userProfilePicUrl,
                            defaultProfileResId = R.drawable.default_profile_pic,
                            markerPinResId = R.drawable.pin_base_shape,
                            isLocationStale = isCurrentUserLocationStale
                        )

                        if (userMarkerIcon != null) {
                            Marker(
                                state = markerState,
                                title = currentUserModel?.displayName ?: "My Location",
                                icon = userMarkerIcon,
                                anchor = Offset(0.5f, 1.0f),
                                onClick = {
                                    currentMarkerInfo = MarkerDisplayInfo(
                                        title = it.title ?: "Unknown User",
                                        timestamp = loc.time,
                                        speed = if (loc.hasSpeed()) loc.speed else null,
                                        bearing = if (loc.hasBearing()) loc.bearing else null,
                                        profilePhotoUrl = userProfilePicUrl,
                                        latLng = userLatLng,
                                        userId = FirebaseAuth.getInstance().currentUser?.uid
                                    )
                                    showCustomMarkerInfoDialog = true
                                    true
                                }
                            )
                        } else {
                            Marker(
                                state = markerState,
                                title = currentUserModel?.displayName ?: "My Location",
                                onClick = {
                                    currentMarkerInfo = MarkerDisplayInfo(
                                        title = it.title ?: "Unknown User",
                                        timestamp = loc.time,
                                        speed = if (loc.hasSpeed()) loc.speed else null,
                                        bearing = if (loc.hasBearing()) loc.bearing else null,
                                        profilePhotoUrl = userProfilePicUrl,
                                        latLng = userLatLng,
                                        userId = FirebaseAuth.getInstance().currentUser?.uid
                                    )
                                    showCustomMarkerInfoDialog = true
                                    true
                                }
                            )
                        }
                    }

                    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
                    if (isInGroup && currentUserId != null) {
                        otherMembersLocations.forEach { otherLoc ->
                            if (otherLoc.userId != currentUserId) {
                                val otherMemberProfile = otherMembersProfiles[otherLoc.userId]
                                if (otherMemberProfile != null) {
                                    val otherMemberLatLng = LatLng(otherLoc.latitude, otherLoc.longitude)
                                    val otherMarkerState = rememberMarkerState(position = otherMemberLatLng)

                                    val isOtherMemberLocationStale = System.currentTimeMillis() - otherLoc.timestamp >= 300000L

                                    LaunchedEffect(otherLoc) {
                                        otherMarkerState.position = otherMemberLatLng
                                    }

                                    val otherProfilePicUrl = otherMemberProfile.profilePhotoUrl
                                    val otherMarkerIcon = MarkerIconHelper.rememberUserMarkerIcon(
                                        profileImageUrl = otherProfilePicUrl,
                                        defaultProfileResId = R.drawable.default_profile_pic,
                                        markerPinResId = R.drawable.pin_base_shape,
                                        isLocationStale = isOtherMemberLocationStale
                                    )

                                    if (otherMarkerIcon != null) {
                                        Marker(
                                            state = otherMarkerState,
                                            title = otherMemberProfile.displayName ?: "Unknown Member",
                                            icon = otherMarkerIcon,
                                            anchor = Offset(0.5f, 1.0f),
                                            onClick = {
                                                currentMarkerInfo = MarkerDisplayInfo(
                                                    title = it.title ?: "Unknown Member",
                                                    timestamp = otherLoc.timestamp,
                                                    speed = otherLoc.speed,
                                                    bearing = otherLoc.bearing,
                                                    profilePhotoUrl = otherProfilePicUrl,
                                                    latLng = otherMemberLatLng,
                                                    userId = otherLoc.userId
                                                )
                                                showCustomMarkerInfoDialog = true
                                                true
                                            }
                                        )
                                    } else {
                                        Marker(
                                            state = otherMarkerState,
                                            title = otherMemberProfile.displayName ?: "Unknown Member",
                                            onClick = {
                                                currentMarkerInfo = MarkerDisplayInfo(
                                                    title = it.title ?: "Unknown Member",
                                                    timestamp = otherLoc.timestamp,
                                                    speed = otherLoc.speed,
                                                    bearing = otherLoc.bearing,
                                                    profilePhotoUrl = otherProfilePicUrl,
                                                    latLng = otherMemberLatLng,
                                                    userId = otherLoc.userId
                                                )
                                                showCustomMarkerInfoDialog = true
                                                true
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        mapMarkers.forEach { mapMarker ->
                            val markerLatLng = LatLng(mapMarker.latitude, mapMarker.longitude)
                            val markerState = rememberMarkerState(position = markerLatLng)

                            LaunchedEffect(mapMarker.id, mapMarker.latitude, mapMarker.longitude) {
                                markerState.position = markerLatLng
                            }

                            val mapMarkerIcon = MarkerIconHelper.rememberMapMarkerIcon(
                                markerType = mapMarker.markerType,
                                cameraBearing = mapMarker.cameraBearing
                            )

                            if (mapMarkerIcon != null) {
                                Marker(
                                    state = markerState,
                                    title = mapMarker.message,
                                    icon = mapMarkerIcon,
                                    anchor = Offset(0.5f, 0.5f),
                                    onClick = {
                                        currentMarkerInfo = MarkerDisplayInfo(
                                            title = mapMarker.message,
                                            timestamp = mapMarker.timestamp,
                                            speed = null,
                                            bearing = mapMarker.cameraBearing,
                                            profilePhotoUrl = mapMarker.photoUrl,
                                            latLng = markerLatLng,
                                            mapMarker = mapMarker,
                                            userId = null
                                        )
                                        showCustomMarkerInfoDialog = true
                                        true
                                    }
                                )
                            }
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(WindowInsets.systemBars.asPaddingValues())
                        .align(Alignment.TopCenter)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight()
                            .background(Color.White.copy(alpha = 0.7f)),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.logo),
                            contentDescription = "TeamSync Logo",
                            modifier = Modifier
                                .fillMaxWidth(0.6f)
                                .padding(vertical = 4.dp, horizontal = 16.dp)
                        )
                        val groupNameText = activeGroup?.groupName ?: "No Active Group"
                        Text(
                            text = "ACTIVE GROUP: "+groupNameText,
                            color = Color.DarkGray,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                }

                // Countdown Timer Badge
                AnimatedVisibility(
                    visible = activeGroup?.groupEndTimestamp != null,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = innerPadding.calculateBottomPadding() + 8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .shadow(4.dp, RoundedCornerShape(12.dp))
                            .background(DarkBlue, RoundedCornerShape(12.dp))
                            .padding(horizontal = 8.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "GROUP EXPIRES IN\n$countdownString",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            fontSize = 14.sp,
                            lineHeight = 12.sp
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(
                            end = 16.dp,
                            bottom = 16.dp + innerPadding.calculateBottomPadding()
                        ),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    LabeledFab(
                        onClick = { navController.navigate(NavRoutes.SHUTDOWN) },
                        label = "SHUTDOWN",
                        icon = { Icon(Icons.Filled.ExitToApp, "Logout/Shutdown") },
                        enabled = true,
                        fabEnabledColor = MaterialTheme.colorScheme.error,
                        fabDisabledColor = MaterialTheme.colorScheme.error,
                        fabEnabledContentColor = MaterialTheme.colorScheme.onError,
                        fabDisabledContentColor = MaterialTheme.colorScheme.onError
                    )

                    LabeledFab(
                        onClick = {
                            mapLockedToUserLocation = !mapLockedToUserLocation
                            if (mapLockedToUserLocation) {
                                userLocation?.let { loc ->
                                    val newLatLng = LatLng(loc.latitude, loc.longitude)
                                    coroutineScope.launch {
                                        cameraPositionState.animate(
                                            CameraUpdateFactory.newLatLngZoom(newLatLng, 15f)
                                        )
                                    }
                                    toastMessage("Map Locked to User Location")
                                }
                            } else {
                                toastMessage("Map Unlocked (can roam)")
                            }
                        },
                        label = if (mapLockedToUserLocation) "UNLOCK MAP" else "LOCK MAP",
                        icon = { Icon(imageVector = if (mapLockedToUserLocation) Icons.Filled.LockOpen else Icons.Filled.Lock, contentDescription = "Toggle Map Lock") },
                        enabled = true,
                        fabEnabledColor = if (mapLockedToUserLocation) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primary,
                        fabDisabledColor = Color.Transparent,
                        fabEnabledContentColor = if (mapLockedToUserLocation) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onPrimary,
                        fabDisabledContentColor = Color.Transparent
                    )

                    LabeledFab(
                        onClick = { navController.navigate(NavRoutes.USER_SETTINGS) },
                        label = "USER SETTINGS",
                        icon = { Icon(Icons.Filled.Settings, "Member Settings") },
                        enabled = true,
                        fabEnabledColor = fabEnabledColor,
                        fabDisabledColor = fabDisabledColor,
                        fabEnabledContentColor = fabEnabledContentColor,
                        fabDisabledContentColor = fabDisabledContentColor
                    )

                    LabeledFab(
                        onClick = { if (isInGroup) { toastMessage("Chat Window clicked!") } },
                        label = "GROUP CHAT",
                        icon = { Icon(Icons.Filled.Chat, "Chat Window") },
                        enabled = isInGroup,
                        fabEnabledColor = fabEnabledColor,
                        fabDisabledColor = fabDisabledColor,
                        fabEnabledContentColor = fabEnabledContentColor,
                        fabDisabledContentColor = fabDisabledContentColor
                    )

                    LabeledFab(
                        onClick = {
                            if (isInGroup) {
                                navController.navigate(NavRoutes.ADD_MAP_MARKER)
                            } else {
                                toastMessage("Must be in a Group to add markers!")
                            }
                        },
                        label = "ADD MARKER",
                        icon = { Icon(Icons.Filled.LocationOn, "Add Marker") },
                        enabled = isInGroup,
                        fabEnabledColor = fabEnabledColor,
                        fabDisabledColor = fabDisabledColor,
                        fabEnabledContentColor = fabEnabledContentColor,
                        fabDisabledContentColor = fabDisabledContentColor
                    )

                    LabeledFab(
                        onClick = { navController.navigate(NavRoutes.CREATE_GROUP) },
                        label = "CREATE GROUP",
                        icon = { Icon(Icons.Filled.Add, "Create Group") },
                        enabled = true,
                        fabEnabledColor = fabEnabledColor,
                        fabDisabledColor = fabDisabledColor,
                        fabEnabledContentColor = fabEnabledContentColor,
                        fabDisabledContentColor = fabDisabledContentColor
                    )
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(
                            start = 16.dp,
                            bottom = 16.dp + innerPadding.calculateBottomPadding()
                        ),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    LabeledFab(
                        onClick = { if (isInGroup) { toastMessage("Group Settings clicked!") } },
                        label = "GROUP SETTINGS",
                        icon = { Icon(Icons.Filled.Tune, "Group Settings") },
                        enabled = isInGroup,
                        fabEnabledColor = fabEnabledColor,
                        fabDisabledColor = fabDisabledColor,
                        fabEnabledContentColor = fabEnabledContentColor,
                        fabDisabledContentColor = fabDisabledContentColor
                    )

                    LabeledFab(
                        onClick = { if (isInGroup) { toastMessage("Group Status clicked!") } },
                        label = "GROUP STATUS",
                        icon = { Icon(Icons.Filled.Info, "Group Status") },
                        enabled = isInGroup,
                        fabEnabledColor = fabEnabledColor,
                        fabDisabledColor = fabDisabledColor,
                        fabEnabledContentColor = fabEnabledContentColor,
                        fabDisabledContentColor = fabDisabledContentColor
                    )

                    LabeledFab(
                        onClick = { if (isInGroup) { toastMessage("Send FCM Notification clicked!") } },
                        label = "SEND ALERTS",
                        icon = { Icon(Icons.Filled.Notifications, "Send FCM Notification") },
                        enabled = isInGroup,
                        fabEnabledColor = fabEnabledColor,
                        fabDisabledColor = fabDisabledColor,
                        fabEnabledContentColor = fabEnabledContentColor,
                        fabDisabledContentColor = fabDisabledContentColor
                    )

                    LabeledFab(
                        onClick = { if (isInGroup) { navController.navigate(NavRoutes.GEOFENCE) } },
                        label = "GEOFENCING",
                        icon = { Icon(Icons.Filled.Polyline, "Add GeoFence") },
                        enabled = isInGroup,
                        fabEnabledColor = fabEnabledColor,
                        fabDisabledColor = fabDisabledColor,
                        fabEnabledContentColor = fabEnabledContentColor,
                        fabDisabledContentColor = fabDisabledContentColor
                    )

                    LabeledFab(
                        onClick = {
                            if (isInGroup) {
                                navController.navigate(NavRoutes.TEAM_LIST)
                            } else {
                                toastMessage("Must be in a Group to view Team List!")
                            }
                        },
                        label = "GROUP MEMBERS",
                        icon = { Icon(Icons.Filled.People, "Team List") },
                        enabled = isInGroup,
                        fabEnabledColor = fabEnabledColor,
                        fabDisabledColor = fabDisabledColor,
                        fabEnabledContentColor = fabEnabledContentColor,
                        fabDisabledContentColor = fabDisabledContentColor
                    )

                    LabeledFab(
                        onClick = { navController.navigate(NavRoutes.GROUPS_LIST) },
                        label = "JOIN GROUPS",
                        icon = { Icon(Icons.Filled.Groups, "Groups List") },
                        enabled = true,
                        fabEnabledColor = fabEnabledColor,
                        fabDisabledColor = fabDisabledColor,
                        fabEnabledContentColor = fabEnabledContentColor,
                        fabDisabledContentColor = fabDisabledContentColor
                    )
                }



                if (showCustomMarkerInfoDialog && currentMarkerInfo != null) {
                    if (currentMarkerInfo!!.mapMarker == null) {
                        MarkerInfoDialog(
                            navController = navController,
                            profilePhotoUrl = currentMarkerInfo!!.profilePhotoUrl,
                            title = currentMarkerInfo!!.title,
                            latLng = currentMarkerInfo!!.latLng,
                            timestamp = currentMarkerInfo!!.timestamp,
                            speed = currentMarkerInfo!!.speed,
                            bearing = currentMarkerInfo!!.bearing,
                            onDismissRequest = { showCustomMarkerInfoDialog = false },
                            personUserId = currentMarkerInfo!!.userId
                        )
                    } else {
                        MapMarkerInfoDialog(
                            mapMarker = currentMarkerInfo!!.mapMarker!!,
                            onDismissRequest = { showCustomMarkerInfoDialog = false }
                        )
                    }
                }

                if (showExpiredGroupDialog && expiredGroupDialogMessage != null) {
                    AlertDialog(
                        onDismissRequest = {
                            showExpiredGroupDialog = false
                            groupMonitorService.clearUserMessage()
                        },
                        title = { Text("Group Expired") },
                        text = { Text(expiredGroupDialogMessage!!) },
                        confirmButton = {
                            Button(onClick = {
                                showExpiredGroupDialog = false
                                groupMonitorService.clearUserMessage()
                            }) {
                                Text("OK")
                            }
                        }
                    )
                }
            }

            AnimatedVisibility(
                visible = showInstructionsOverlay,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.fillMaxSize()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f))
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.instructions),
                        contentDescription = "Instructions",
                        modifier = Modifier
                            .align(Alignment.Center)
                            .fillMaxWidth(),
                        contentScale = ContentScale.Fit
                    )

                    FloatingActionButton(
                        onClick = {
                            coroutineScope.launch {
                                showInstructionsOverlay = false
                                currentUserModel?.let { user ->
                                    val updatedUser = user.copy(mainInstructionsSeen = true)
                                    firestoreService.saveUserProfile(updatedUser).onSuccess {
                                        Log.d("MainScreen", "mainInstructionsSeen updated to true in Firestore.")
                                        currentUserModel = updatedUser
                                    }.onFailure { e ->
                                        Log.e("MainScreen", "Failed to update mainInstructionsSeen: ${e.message}")
                                    }
                                }
                            }
                        },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 30.dp, end = 16.dp)
                            .border(2.dp, Color.Black, CircleShape),
                        containerColor = Color(0xFFFF0000),
                        contentColor = Color.White,
                        shape = CircleShape
                    ) {
                        Icon(Icons.Filled.Close, "Close Instructions")
                    }
                }
            }

            if (showMapLoadingDialog) {
                AlertDialog(
                    onDismissRequest = { },
                    properties = androidx.compose.ui.window.DialogProperties(
                        dismissOnBackPress = false,
                        dismissOnClickOutside = false
                    ),
                    modifier = Modifier
                        .fillMaxWidth(0.8f)
                        .wrapContentHeight()
                        .shadow(8.dp, RoundedCornerShape(16.dp), clip = false)
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = LightCream)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            CircularProgressIndicator(color = DarkBlue)
                            Text(
                                text = "Setting up the map... one moment",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = DarkBlue,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun startLocationUpdates(
        fusedLocationClient: FusedLocationProviderClient,
        locationCallback: LocationCallback,
        interval: Long
    ) {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, interval)
            .setMinUpdateIntervalMillis(interval / 2)
            .build()

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
            Log.d("MainScreen", "Started local GPS location updates with interval: $interval ms")
        } catch (e: SecurityException) {
            Log.e("MainScreen", "Local GPS location permission denied: ${e.message}")
        }
    }

    @Composable
    fun LabeledFab(
        onClick: () -> Unit,
        label: String,
        icon: @Composable () -> Unit,
        enabled: Boolean,
        fabEnabledColor: Color,
        fabDisabledColor: Color,
        fabEnabledContentColor: Color,
        fabDisabledContentColor: Color
    ) {
        Box(
            contentAlignment = Alignment.Center
        ) {
            FloatingActionButton(
                onClick = { if(enabled) onClick() },
                containerColor = if (enabled) fabEnabledColor else fabDisabledColor,
                contentColor = if (enabled) fabEnabledContentColor else fabDisabledContentColor,
                shape = CircleShape
            ) {
                icon()
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 4.dp)
                    .shadow(elevation = 2.dp, shape = RoundedCornerShape(8.dp))
                    .background(if (enabled) fabEnabledColor else fabDisabledColor, RoundedCornerShape(8.dp))
                    .padding(horizontal = 0.dp, vertical = 0.dp)
            ) {
                Text(
                    text = label,
                    color = if (enabled) fabEnabledContentColor else fabDisabledContentColor,
                    fontSize = 8.sp,
                    lineHeight = 4.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 1.dp, vertical = 1.dp)
                )
            }
        }
    }



    fun stopLocationUpdates(
        fusedLocationClient: FusedLocationProviderClient,
        locationCallback: LocationCallback
    ) {
        fusedLocationClient.removeLocationUpdates(locationCallback)
        Log.d("MainScreen", "Stopped local GPS location updates.")
    }
}
