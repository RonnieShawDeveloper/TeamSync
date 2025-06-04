// In file: app/src/main/java/com/artificialinsightsllc/teamsync/Screens/MainScreen.kt
package com.artificialinsightsllc.teamsync.Screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import com.google.android.gms.maps.model.BitmapDescriptor
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextAlign
import com.artificialinsightsllc.teamsync.Screens.MarkerInfoDialog
import com.artificialinsightsllc.teamsync.TeamSyncApplication

class MainScreen(private val navController: NavHostController) {

    data class MarkerDisplayInfo(
        val title: String,
        val timestamp: Long,
        val speed: Float?,
        val bearing: Float?
    )

    @SuppressLint("MissingPermission") // Suppress lint warning, as permissions are handled at runtime
    @Composable
    fun Content() {
        val context = LocalContext.current
        val lifecycleOwner = LocalLifecycleOwner.current
        val toastMessage: (String) -> Unit = { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }

        val groupMonitorService = (context.applicationContext as TeamSyncApplication).groupMonitorService
        val firestoreService = remember { FirestoreService() }

        val isInGroup by groupMonitorService.isInActiveGroup.collectAsStateWithLifecycle()
        val activeGroup by groupMonitorService.activeGroup.collectAsStateWithLifecycle()
        val activeGroupMember by groupMonitorService.activeGroupMember.collectAsStateWithLifecycle()
        val userMessage by groupMonitorService.userMessage.collectAsStateWithLifecycle()
        val effectiveLocationUpdateInterval by groupMonitorService.effectiveLocationUpdateInterval.collectAsStateWithLifecycle()

        // --- NEW: Collect other members' locations and profiles ---
        val otherMembersLocations by groupMonitorService.otherMembersLocations.collectAsStateWithLifecycle()
        val otherMembersProfiles by groupMonitorService.otherMembersProfiles.collectAsStateWithLifecycle()


        var userLocation by remember { mutableStateOf<Location?>(null) }

        val cameraPositionState = rememberCameraPositionState {
            position = LatLng(27.9506, -82.4572).let { CameraPosition.fromLatLngZoom(it, 10f) }
        }
        val fusedLocationClient: FusedLocationProviderClient = remember {
            getFusedLocationProviderClient(context)
        }

        var showPermissionRationaleDialog by remember { mutableStateOf(false) }
        var hasLocationPermission by remember {
            mutableStateOf(
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
            )
        }

        var currentUserModel by remember { mutableStateOf<UserModel?>(null) }

        var mapLockedToUserLocation by remember { mutableStateOf(true) }
        var hasPerformedInitialCenter by remember { mutableStateOf(false) }

        val coroutineScope = rememberCoroutineScope()

        val locationCallback = remember {
            object : LocationCallback() {
                override fun onLocationResult(locationResult: LocationResult) {
                    locationResult.lastLocation?.let { location ->
                        userLocation = location
                        Log.d("MainScreen", "Local GPS Location received: ${location.latitude}, ${location.longitude}")

                        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
                        val isSharingGloballyEnabled = groupMonitorService.isLocationSharingGloballyEnabled.value
                        if (currentUserId != null && isInGroup && isSharingGloballyEnabled) {
                            coroutineScope.launch {
                                val locationData = Locations(
                                    userId = currentUserId,
                                    latitude = location.latitude,
                                    longitude = location.longitude,
                                    timestamp = System.currentTimeMillis(),
                                    speed = if (location.hasSpeed()) location.speed else null,
                                    bearing = if (location.hasBearing()) location.bearing else null
                                )
                                firestoreService.addLocationLog(locationData)
                                firestoreService.saveCurrentLocation(locationData)
                                Log.d("MainScreen", "Location sent to Firestore for user $currentUserId.")
                            }
                        } else {
                            Log.d("MainScreen", "Not sending location to Firestore (not in group or sharing disabled).")
                        }
                    }
                }
            }
        }

        val locationPermissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            hasLocationPermission = isGranted
            if (isGranted) {
                Log.d("MainScreen", "Location permission granted. Starting local GPS updates.")
                startLocationUpdates(fusedLocationClient, locationCallback, effectiveLocationUpdateInterval)
            } else {
                Log.w("MainScreen", "Location permission denied by user. Local GPS updates will not start.")
                stopLocationUpdates(fusedLocationClient, locationCallback)
                coroutineScope.launch {
                    groupMonitorService.updatePersonalLocationSharing(false)
                }
            }
        }


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
                    } else {
                        Log.w("MainScreen", "User document not found for ID: $userId")
                    }
                } catch (e: Exception) {
                    Log.e("MainScreen", "Error loading user profile: ${e.message}")
                }
            } else {
                Log.d("MainScreen", "No authenticated user found. Consider navigating to LoginScreen.")
            }

            if (hasLocationPermission) {
                startLocationUpdates(fusedLocationClient, locationCallback, effectiveLocationUpdateInterval)
            } else {
                showPermissionRationaleDialog = true
            }
        }

        DisposableEffect(lifecycleOwner, fusedLocationClient, hasLocationPermission, effectiveLocationUpdateInterval) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_START) {
                    if (hasLocationPermission) {
                        startLocationUpdates(fusedLocationClient, locationCallback, effectiveLocationUpdateInterval)
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

        LaunchedEffect(isInGroup, hasLocationPermission) {
            if (isInGroup && !hasLocationPermission) {
                showPermissionRationaleDialog = true
            } else {
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

        val fabEnabledColor = MaterialTheme.colorScheme.primaryContainer
        val fabDisabledColor = MaterialTheme.colorScheme.surfaceVariant
        val fabEnabledContentColor = MaterialTheme.colorScheme.onPrimaryContainer
        val fabDisabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)

        var showCustomMarkerInfoDialog by remember { mutableStateOf(false) }
        var currentMarkerInfo by remember { mutableStateOf<MarkerDisplayInfo?>(null) }

        val snackbarHostState = remember { SnackbarHostState() }

        LaunchedEffect(userMessage) {
            userMessage?.let { message ->
                snackbarHostState.showSnackbar(message)
                groupMonitorService.clearUserMessage()
            }
        }

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
                        isMyLocationEnabled = hasLocationPermission,
                        isBuildingEnabled = true
                    ),
                    uiSettings = MapUiSettings(zoomControlsEnabled = false)
                ) {
                    // --- Display Current User's Custom Marker (from local GPS) ---
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
                                        bearing = if (loc.hasBearing()) loc.bearing else null
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
                                        bearing = if (loc.hasBearing()) loc.bearing else null
                                    )
                                    showCustomMarkerInfoDialog = true
                                    true
                                }
                            )
                        }
                    }

                    // --- NEW: Display Other Members' Markers (from Firestore) ---
                    // Only show other members if currently in an active group
                    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
                    if (isInGroup && currentUserId != null) {
                        otherMembersLocations.forEach { otherLoc ->
                            // Exclude current user's own location from other members' markers
                            if (otherLoc.userId != currentUserId) {
                                val otherMemberProfile = otherMembersProfiles[otherLoc.userId]
                                if (otherMemberProfile != null) {
                                    val otherMemberLatLng = LatLng(otherLoc.latitude, otherLoc.longitude)
                                    val otherMarkerState = rememberMarkerState(position = otherMemberLatLng)

                                    // Update marker position if location changes
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
                                                    bearing = otherLoc.bearing
                                                )
                                                showCustomMarkerInfoDialog = true
                                                true
                                            }
                                        )
                                    } else {
                                        // Fallback marker if custom icon fails
                                        Marker(
                                            state = otherMarkerState,
                                            title = otherMemberProfile.displayName ?: "Unknown Member",
                                            onClick = {
                                                currentMarkerInfo = MarkerDisplayInfo(
                                                    title = it.title ?: "Unknown Member",
                                                    timestamp = otherLoc.timestamp,
                                                    speed = otherLoc.speed,
                                                    bearing = otherLoc.bearing
                                                )
                                                showCustomMarkerInfoDialog = true
                                                true
                                            }
                                        )
                                    }
                                }
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
                            text = "ACTIVE GROUP: "+groupNameText, // Added "ACTIVE GROUP: "
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
                                            CameraUpdateFactory.newCameraPosition(newCameraPosition)
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
                            imageVector = if (mapLockedToUserLocation) Icons.Filled.Lock else Icons.Filled.LockOpen,
                            contentDescription = if (mapLockedToUserLocation) "Lock Map" else "Unlock Map"
                        )
                    }
                    FloatingActionButton(
                        onClick = { toastMessage("Member Preferences/Settings clicked!") },
                        containerColor = fabEnabledColor, contentColor = fabEnabledContentColor
                    ) { Icon(Icons.Filled.Settings, "Member Settings") }

                    FloatingActionButton(
                        onClick = { if (isInGroup) { toastMessage("Chat Window clicked!") } },
                        containerColor = if (isInGroup) fabEnabledColor else fabDisabledColor,
                        contentColor = if (isInGroup) fabEnabledContentColor else fabDisabledContentColor
                    ) { Icon(Icons.Filled.Chat, "Chat Window") }

                    FloatingActionButton(
                        onClick = { if (isInGroup) { toastMessage("Add Marker clicked!") } },
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
                        onClick = { if (isInGroup) { toastMessage("Team List clicked!") } },
                        containerColor = if (isInGroup) fabEnabledColor else fabDisabledColor,
                        contentColor = if (isInGroup) fabEnabledContentColor else fabDisabledContentColor
                    ) { Icon(Icons.Filled.People, "Team List") }

                    FloatingActionButton(
                        onClick = { navController.navigate(NavRoutes.GROUPS_LIST) },
                        containerColor = fabEnabledColor, contentColor = fabEnabledContentColor
                    ) { Icon(Icons.Filled.Groups, "Groups List") }
                }

                if (showPermissionRationaleDialog) {
                    PermissionRationaleDialog(
                        onConfirm = {
                            showPermissionRationaleDialog = false
                            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                        },
                        onDismiss = {
                            showPermissionRationaleDialog = false
                            Log.d("MainScreen", "User dismissed location permission rationale.")
                        }
                    )
                }

                if (showCustomMarkerInfoDialog && currentMarkerInfo != null) {
                    MarkerInfoDialog(
                        title = currentMarkerInfo!!.title,
                        timestamp = currentMarkerInfo!!.timestamp,
                        speed = currentMarkerInfo!!.speed,
                        bearing = currentMarkerInfo!!.bearing,
                        onDismissRequest = { showCustomMarkerInfoDialog = false }
                    )
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates(
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

    private fun stopLocationUpdates(
        fusedLocationClient: FusedLocationProviderClient,
        locationCallback: LocationCallback
    ) {
        fusedLocationClient.removeLocationUpdates(locationCallback)
        Log.d("MainScreen", "Stopped local GPS location updates.")
    }

    @Composable
    fun PermissionRationaleDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Permissions Required for TeamSync") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        text = "TeamSync needs the following permissions to provide its full functionality:\n\n" +
                                "• Location (Precise & Approximate): Essential for showing your " +
                                "position and your group members' positions on the map, " +
                                "enabling features like finding friends, and geotagging photos. " +
                                "Precise location ensures accurate tracking. This permission is crucial for the app's core functionality.\n\n" +
                                "• Camera: Required if you want to take photos directly within " +
                                "the app to share with your group, especially geotagged ones " +
                                "that appear on the map.\n\n" +
                                "• Photos/Media: Needed to allow you to select and upload " +
                                "existing images from your device's gallery to share with your group." +
                                "• Internet: Necessary for all communication, map loading, and " +
                                "location updates over the network.\n\n" +
                                "You can manage these permissions at any time in your device's app settings.",
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                }
            },
            confirmButton = {
                Button(onClick = onConfirm) {
                    Text("Continue")
                }
            },
            dismissButton = {
                Button(onClick = onDismiss) {
                    Text("Not Now")
                }
            }
        )
    }
}