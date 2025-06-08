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

// NEW: Add BorderStroke import for the close button
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape // Import CircleShape

class MainScreen(private val navController: NavHostController) {

    data class MarkerDisplayInfo(
        val title: String,
        val timestamp: Long,
        val speed: Float?,
        val bearing: Float?,
        val profilePhotoUrl: String?,
        val latLng: LatLng?,
        val mapMarker: MapMarker? = null,
        val userId: String? = null // NEW: Added userId to MarkerDisplayInfo
    )

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
        var hasPerformedInitialCenter by remember { mutableStateOf(false) }

        var showPermissionRationaleDialog by remember { mutableStateOf(false) }
        var allPermissionsGranted by remember { mutableStateOf(false) }
        var hasNotificationPermission by remember { mutableStateOf(false) }

        var showExpiredGroupDialog by remember { mutableStateOf(false) }
        var expiredGroupDialogMessage by remember { mutableStateOf<String?>(null) }

        val snackbarHostState = remember { SnackbarHostState() }
        var showCustomMarkerInfoDialog by remember { mutableStateOf(false) }
        var currentMarkerInfo by remember { mutableStateOf<MarkerDisplayInfo?>(null) }


        val cameraPositionState = rememberCameraPositionState {
            position = LatLng(27.9506, -82.4572).let { CameraPosition.fromLatLngZoom(it, 10f) }
        }
        val fusedLocationClient: FusedLocationProviderClient = remember {
            getFusedLocationProviderClient(context)
        }

        // Live data from GroupMonitorService
        val isInGroup by groupMonitorService.isInActiveGroup.collectAsStateWithLifecycle()
        val activeGroup by groupMonitorService.activeGroup.collectAsStateWithLifecycle()
        val activeGroupMember by groupMonitorService.activeGroupMember.collectAsStateWithLifecycle()
        val userMessage by groupMonitorService.userMessage.collectAsStateWithLifecycle()
        val effectiveLocationUpdateInterval by groupMonitorService.effectiveLocationUpdateInterval.collectAsStateWithLifecycle()
        val isLocationSharingGloballyEnabled by groupMonitorService.isLocationSharingGloballyEnabled.collectAsStateWithLifecycle()

        val otherMembersLocations by groupMonitorService.otherMembersLocations.collectAsStateWithLifecycle()
        val otherMembersProfiles by groupMonitorService.otherMembersProfiles.collectAsStateWithLifecycle()
        val mapMarkers by markerMonitorService.mapMarkers.collectAsStateWithLifecycle()
        Log.d("MainScreen", "Collected ${mapMarkers.size} map markers.")


        // Permissions needed by the app
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

        fun stopLocationUpdates(
            fusedLocationClient: FusedLocationProviderClient,
            locationCallback: LocationCallback
        ) {
            fusedLocationClient.removeLocationUpdates(locationCallback)
            Log.d("MainScreen", "Stopped local GPS location updates.")
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

        // NEW: State for instructional overlay visibility
        var showInstructionsOverlay by remember { mutableStateOf(false) }

        // --- LaunchedEffects for initial setup and state observation ---

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

                        // Check mainInstructionsSeen flag here
                        if (currentUserModel?.mainInstructionsSeen != true) {
                            showInstructionsOverlay = true
                            // Don't save immediately, wait for user to close it.
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

            val initialSelectedGroupId = currentUserModel?.selectedActiveGroupId
            groupMonitorService.startMonitoring(initialSelectedGroupId)
            groupMonitorService.setUiPermissionsGranted(allPermissionsGranted && hasNotificationPermission)
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
                            markerPinResId = R.drawable.pin_base_shape
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
                                        userId = FirebaseAuth.getInstance().currentUser?.uid // Pass current user's ID
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
                                        userId = FirebaseAuth.getInstance().currentUser?.uid // Pass current user's ID
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

                                    LaunchedEffect(otherLoc) {
                                        otherMarkerState.position = otherMemberLatLng
                                    }

                                    val otherProfilePicUrl = otherMemberProfile.profilePhotoUrl
                                    val otherMarkerIcon = MarkerIconHelper.rememberUserMarkerIcon(
                                        profileImageUrl = otherProfilePicUrl,
                                        defaultProfileResId = R.drawable.default_profile_pic,
                                        markerPinResId = R.drawable.pin_base_shape
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
                                                    userId = otherLoc.userId // Pass the other member's userId
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
                                                    userId = otherLoc.userId // Pass the other member's userId
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
                                            userId = null // Map markers don't have a direct userId for the report
                                        )
                                        showCustomMarkerInfoDialog = true
                                        true
                                    }
                                )
                            }
                        }
                    }
                }

                // --- Logo Overlay with Group Name ---
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

                // --- Floating Buttons on the Right Side ---
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(
                            end = 16.dp,
                            bottom = 16.dp + innerPadding.calculateBottomPadding()
                        ),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    FloatingActionButton(
                        onClick = {
                            mapLockedToUserLocation = !mapLockedToUserLocation
                            if (mapLockedToUserLocation) {
                                userLocation?.let { loc ->
                                    val newLatLng = LatLng(loc.latitude, loc.longitude)
                                    val currentZoom = cameraPositionState.position.zoom
                                    coroutineScope.launch {
                                        val newCameraPosition = CameraPosition.Builder()
                                            .target(newLatLng)
                                            .zoom(currentZoom)
                                            .tilt(0f)
                                            .bearing(0f)
                                            .build()
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
                        containerColor = if (mapLockedToUserLocation) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = if (mapLockedToUserLocation) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Lock,
                            contentDescription = if (mapLockedToUserLocation) "Lock Map" else "Unlock Map"
                        )
                    }
                    FloatingActionButton(
                        onClick = { navController.navigate(NavRoutes.USER_SETTINGS) }, // Navigate to UserSettingsScreen
                        containerColor = fabEnabledColor, contentColor = fabEnabledContentColor
                    ) { Icon(Icons.Filled.Settings, "Member Settings") }

                    FloatingActionButton(
                        onClick = { if (isInGroup) { toastMessage("Chat Window clicked!") } },
                        containerColor = if (isInGroup) fabEnabledColor else fabDisabledColor,
                        contentColor = if (isInGroup) fabEnabledContentColor else fabDisabledContentColor
                    ) { Icon(Icons.Filled.Chat, "Chat Window") }

                    FloatingActionButton(
                        onClick = {
                            if (isInGroup) {
                                navController.navigate(NavRoutes.ADD_MAP_MARKER)
                            } else {
                                toastMessage("Add Marker - Must be in a Group to add markers!")
                            }
                        },
                        containerColor = if (isInGroup) fabEnabledColor else fabDisabledColor,
                        contentColor = if (isInGroup) fabEnabledContentColor else fabDisabledContentColor
                    ) { Icon(Icons.Filled.LocationOn, "Add Marker") }

                    FloatingActionButton(
                        onClick = { navController.navigate(NavRoutes.CREATE_GROUP) },
                        containerColor = fabEnabledColor, contentColor = fabEnabledContentColor
                    ) { Icon(Icons.Filled.Add, "Create Group") }
                }

                // --- Floating Buttons on the Left Side ---
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(
                            start = 16.dp,
                            bottom = 16.dp + innerPadding.calculateBottomPadding()
                        ),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    FloatingActionButton(
                        onClick = { if (isInGroup) { toastMessage("Group Settings clicked!") } },
                        containerColor = if (isInGroup) fabEnabledColor else fabDisabledColor,
                        contentColor = if (isInGroup) fabEnabledContentColor else fabDisabledContentColor
                    ) { Icon(Icons.Filled.Tune, "Group Settings") }

                    FloatingActionButton(
                        onClick = { if (isInGroup) { toastMessage("Group Status clicked!") } },
                        containerColor = if (isInGroup) fabEnabledColor else fabDisabledColor,
                        contentColor = if (isInGroup) fabEnabledContentColor else fabDisabledContentColor
                    ) { Icon(Icons.Filled.Info, "Group Status") }

                    FloatingActionButton(
                        onClick = { if (isInGroup) { toastMessage("Send FCM Notification clicked!") } },
                        containerColor = if (isInGroup) fabEnabledColor else fabDisabledColor,
                        contentColor = if (isInGroup) fabEnabledContentColor else fabDisabledContentColor
                    ) { Icon(Icons.Filled.Notifications, "Send FCM Notification") }

                    FloatingActionButton(
                        onClick = { if (isInGroup) { toastMessage("Add GeoFence clicked!") } },
                        containerColor = if (isInGroup) fabEnabledColor else fabDisabledColor,
                        contentColor = if (isInGroup) fabEnabledContentColor else fabDisabledContentColor
                    ) { Icon(Icons.Filled.Polyline, "Add GeoFence") }

                    FloatingActionButton(
                        onClick = {
                            if (isInGroup) {
                                navController.navigate(NavRoutes.TEAM_LIST)
                            } else {
                                toastMessage("Team List - Must be in a Group to view!")
                            }
                        },
                        containerColor = if (isInGroup) fabEnabledColor else fabDisabledColor,
                        contentColor = if (isInGroup) fabEnabledContentColor else fabDisabledContentColor
                    ) { Icon(Icons.Filled.People, "Team List") }

                    FloatingActionButton(
                        onClick = { navController.navigate(NavRoutes.GROUPS_LIST) },
                        containerColor = fabEnabledColor, contentColor = fabEnabledContentColor
                    ) { Icon(Icons.Filled.Groups, "Groups List") }
                }


                if (showCustomMarkerInfoDialog && currentMarkerInfo != null) {
                    if (currentMarkerInfo!!.mapMarker == null) {
                        MarkerInfoDialog(
                            navController = navController, // NEW: Pass navController here
                            profilePhotoUrl = currentMarkerInfo!!.profilePhotoUrl,
                            title = currentMarkerInfo!!.title,
                            latLng = currentMarkerInfo!!.latLng,
                            timestamp = currentMarkerInfo!!.timestamp,
                            speed = currentMarkerInfo!!.speed,
                            bearing = currentMarkerInfo!!.bearing,
                            onDismissRequest = { showCustomMarkerInfoDialog = false },
                            personUserId = currentMarkerInfo!!.userId // Pass userId here
                        )
                    } else {
                        // MapMarkerInfoDialog does not need navController or userId currently
                        MapMarkerInfoDialog(
                            mapMarker = currentMarkerInfo!!.mapMarker!!,
                            onDismissRequest = { showCustomMarkerInfoDialog = false }
                        )
                    }
                }

                // Expired Group Dialog
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

            // NEW: Instructional Overlay
            AnimatedVisibility(
                visible = showInstructionsOverlay,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.fillMaxSize()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f)) // Semi-transparent black background
                ) {
                    // Instructional Graphic
                    Image(
                        painter = painterResource(id = R.drawable.instructions), // Your drawable
                        contentDescription = "Instructions",
                        modifier = Modifier
                            .align(Alignment.Center) // Center the image
                            .fillMaxWidth(),
                        contentScale = ContentScale.Fit // IMPORTANT: Fit to prevent stretching
                    )

                    // Close Button (top right)
                    FloatingActionButton(
                        onClick = {
                            coroutineScope.launch {
                                showInstructionsOverlay = false
                                currentUserModel?.let { user ->
                                    val updatedUser = user.copy(mainInstructionsSeen = true)
                                    firestoreService.saveUserProfile(updatedUser).onSuccess {
                                        Log.d("MainScreen", "mainInstructionsSeen updated to true in Firestore.")
                                        currentUserModel = updatedUser // Update local state immediately
                                    }.onFailure { e ->
                                        Log.e("MainScreen", "Failed to update mainInstructionsSeen: ${e.message}")
                                    }
                                }
                            }
                        },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 30.dp, end = 16.dp) // Maintain padding
                            .border(2.dp, Color.Black, CircleShape), // Correct way to add border
                        containerColor = Color(0xFFFF0000), // Bright red
                        contentColor = Color.White, // White 'X'
                        shape = CircleShape // Use CircleShape directly from material3
                    ) {
                        Icon(Icons.Filled.Close, "Close Instructions")
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

    fun stopLocationUpdates(
        fusedLocationClient: FusedLocationProviderClient,
        locationCallback: LocationCallback
    ) {
        fusedLocationClient.removeLocationUpdates(locationCallback)
        Log.d("MainScreen", "Stopped local GPS location updates.")
    }
}
