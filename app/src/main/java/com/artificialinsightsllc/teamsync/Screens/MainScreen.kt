// In file: app/src/main/java/com/artificialinsightsllc/teamsync/Screens/MainScreen.kt
package com.artificialinsightsllc.teamsync.Screens

import android.annotation.SuppressLint
import android.widget.Toast
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Polyline
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExitToApp
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
import androidx.navigation.NavHostController
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
import com.artificialinsightsllc.teamsync.Helpers.MarkerIconHelper
import com.google.firebase.auth.FirebaseAuth
import androidx.compose.ui.geometry.Offset
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextAlign
import com.artificialinsightsllc.teamsync.Screens.MarkerInfoDialog
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import com.artificialinsightsllc.teamsync.ui.theme.DarkBlue
import com.artificialinsightsllc.teamsync.ui.theme.LightCream
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.draw.shadow
import androidx.lifecycle.viewmodel.compose.viewModel
import com.artificialinsightsllc.teamsync.ViewModels.MainViewModel
import androidx.compose.material3.Badge // NEW: Import Badge
import androidx.compose.material3.BadgedBox // NEW: Import BadgedBox
import androidx.compose.foundation.layout.size // NEW: Import size
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextButton
// NEW: Imports for dynamic incident geofence creation
import com.artificialinsightsllc.teamsync.ViewModels.GeofenceAndIncidentViewModel
import com.artificialinsightsllc.teamsync.Models.Incident
import com.artificialinsightsllc.teamsync.Models.GeofenceZone
import com.artificialinsightsllc.teamsync.Models.IncidentLogEntry
import com.artificialinsightsllc.teamsync.Models.IncidentPersonnelStatus
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.Polygon
import java.util.UUID

class MainScreen(private val navController: NavHostController) {

    @OptIn(ExperimentalMaterial3Api::class)
    @SuppressLint("MissingPermission")
    @Composable
    fun Content(
        mainViewModel: MainViewModel = viewModel(),
        geofenceAndIncidentViewModel: GeofenceAndIncidentViewModel = viewModel() // NEW: Inject GeofenceAndIncidentViewModel
    ) {
        val context = LocalContext.current
        val coroutineScope = rememberCoroutineScope()

        // MainViewModel UI state variables
        val currentUserId by mainViewModel.currentUserId.collectAsStateWithLifecycle()
        val currentUserLocation by mainViewModel.currentUserLocation.collectAsStateWithLifecycle()
        val currentUserModel by mainViewModel.currentUserModel.collectAsStateWithLifecycle()
        val isInActiveGroup by mainViewModel.isInActiveGroup.collectAsStateWithLifecycle()
        val activeGroup by mainViewModel.activeGroup.collectAsStateWithLifecycle()
        val otherMembersLocations by mainViewModel.otherMembersLocations.collectAsStateWithLifecycle()
        val otherMembersProfiles by mainViewModel.otherMembersProfiles.collectAsStateWithLifecycle()
        val mapMarkers by mainViewModel.mapMarkers.collectAsStateWithLifecycle()
        val userMessage by mainViewModel.userMessage.collectAsStateWithLifecycle()
        val mapLockedToUserLocation by mainViewModel.mapLockedToUserLocation.collectAsStateWithLifecycle()
        val showInstructionsOverlay by mainViewModel.showInstructionsOverlay.collectAsStateWithLifecycle()
        val countdownString by mainViewModel.countdownString.collectAsStateWithLifecycle()
        val showExpiredGroupDialog by mainViewModel.showExpiredGroupDialog.collectAsStateWithLifecycle()
        val expiredGroupDialogMessage by mainViewModel.expiredGroupDialogMessage.collectAsStateWithLifecycle()
        val showCustomMarkerInfoDialog by mainViewModel.showCustomMarkerInfoDialog.collectAsStateWithLifecycle()
        val currentMarkerInfo by mainViewModel.currentMarkerInfo.collectAsStateWithLifecycle()
        val showMapLoadingDialog by mainViewModel.showMapLoadingDialog.collectAsStateWithLifecycle()
        val lastKnownCameraPosition by mainViewModel.lastKnownCameraPosition.collectAsStateWithLifecycle()
        val newChatMessagesCount by mainViewModel.newChatMessagesCount.collectAsStateWithLifecycle()

        // GeofenceAndIncidentViewModel UI state variables
        val activeGeofenceZones by geofenceAndIncidentViewModel.activeGeofenceZones.collectAsStateWithLifecycle()
        val activeIncidents by geofenceAndIncidentViewModel.activeIncidents.collectAsStateWithLifecycle()
        val groupSettings by geofenceAndIncidentViewModel.groupSettings.collectAsStateWithLifecycle()
        val isGeofenceLoading by geofenceAndIncidentViewModel.isLoading.collectAsStateWithLifecycle() // NEW: Collect isLoading as state


        // Local UI states for MainScreen
        val snackbarHostState = remember { SnackbarHostState() }
        val cameraPositionState = rememberCameraPositionState {
            position = lastKnownCameraPosition ?: LatLng(39.8283, -98.5795).let { CameraPosition.fromLatLngZoom(it, 3f) }
        }

        // NEW: State for map long-press menu
        var showMapContextMenu by remember { mutableStateOf(false) }
        var mapContextMenuLatLng by remember { mutableStateOf<LatLng?>(null) }
        var showCreateIncidentGeofenceDialog by remember { mutableStateOf(false) }
        var incidentGeofenceName by remember { mutableStateOf("") }
        var incidentGeofenceDescription by remember { mutableStateOf("") }
        var incidentType by remember { mutableStateOf("") }
        var incidentTitle by remember { mutableStateOf("") }
        var incidentDescription by remember { mutableStateOf("") }
        var incidentTypeDropdownExpanded by remember { mutableStateOf(false) }


        // Effect for showing Snackbar messages from ViewModel
        LaunchedEffect(userMessage) {
            userMessage?.let { message ->
                if (!message.contains("has expired. You have been automatically removed.")) {
                    snackbarHostState.showSnackbar(message)
                }
                mainViewModel.clearUserMessage()
            }
        }

        // Effect for observing Toast events from ViewModel
        LaunchedEffect(mainViewModel.toastEvent) {
            mainViewModel.toastEvent.collect { message ->
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }
        }

        // Effect for observing navigation events from ViewModel
        LaunchedEffect(mainViewModel.navigationEvent) {
            mainViewModel.navigationEvent.collect { route ->
                navController.navigate(route)
            }
        }

        // Effect for map centering and initial load
        LaunchedEffect(currentUserLocation, showMapLoadingDialog, mapLockedToUserLocation) {
            currentUserLocation?.let { loc ->
                val targetLatLng = LatLng(loc.latitude, loc.longitude)
                if (mapLockedToUserLocation || showMapLoadingDialog) {
                    cameraPositionState.animate(
                        CameraUpdateFactory.newLatLngZoom(targetLatLng, 15f)
                    )
                }
            }
        }

        // Observe camera idle events to save position to ViewModel
        LaunchedEffect(cameraPositionState.isMoving) {
            if (!cameraPositionState.isMoving) {
                mainViewModel.setLastKnownCameraPosition(cameraPositionState.position)
            }
        }

        // LaunchedEffect to periodically check for new chat messages for the badge
        LaunchedEffect(activeGroup) {
            if (activeGroup != null) {
                while (true) {
                    mainViewModel.checkForNewChatMessagesForBadge()
                    kotlinx.coroutines.delay(30000L) // Check every 30 seconds
                }
            }
        }

        // Helper for FAB colors
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
                    properties = MapProperties(isMyLocationEnabled = false, isBuildingEnabled = true),
                    uiSettings = MapUiSettings(zoomControlsEnabled = false),
                    onMapLongClick = { latLng ->
                        if (isInActiveGroup) {
                            mapContextMenuLatLng = latLng
                            showMapContextMenu = true
                        } else {
                            mainViewModel.showToastEvent("Must be in an active group to create incident geofences.")
                        }
                    }
                ) {
                    // Render current user location
                    currentUserLocation?.let { loc ->
                        val userLatLng = LatLng(loc.latitude, loc.longitude)
                        val markerState = rememberMarkerState()

                        LaunchedEffect(currentUserLocation) {
                            markerState.position = userLatLng
                        }

                        val userProfilePicUrl = currentUserModel?.profilePhotoUrl
                        val isCurrentUserLocationStale = System.currentTimeMillis() - loc.timestamp >= 300000L

                        val userMarkerIcon = MarkerIconHelper.rememberUserMarkerIcon(
                            profileImageUrl = userProfilePicUrl,
                            defaultProfileResId = R.drawable.default_profile_pic,
                            markerPinResId = R.drawable.pin_base_shape,
                            isLocationStale = isCurrentUserLocationStale
                        )

                        if (currentUserModel?.showMyOwnMarker == true) {
                            if (userMarkerIcon != null) {
                                Marker(
                                    state = markerState,
                                    title = currentUserModel?.displayName ?: "My Location",
                                    icon = userMarkerIcon,
                                    anchor = Offset(0.5f, 1.0f),
                                    onClick = {
                                        mainViewModel.showMarkerInfoDialog(
                                            MainViewModel.MarkerDisplayInfo(
                                                title = it.title ?: "Unknown User",
                                                timestamp = loc.timestamp,
                                                speed = loc.speed,
                                                bearing = loc.bearing,
                                                profilePhotoUrl = userProfilePicUrl,
                                                latLng = userLatLng,
                                                userId = currentUserId
                                            )
                                        )
                                        true
                                    }
                                )
                            } else {
                                Marker(
                                    state = markerState,
                                    title = currentUserModel?.displayName ?: "My Location",
                                    onClick = {
                                        mainViewModel.showMarkerInfoDialog(
                                            MainViewModel.MarkerDisplayInfo(
                                                title = it.title ?: "Unknown User",
                                                timestamp = loc.timestamp,
                                                speed = loc.speed,
                                                bearing = loc.bearing,
                                                profilePhotoUrl = userProfilePicUrl,
                                                latLng = userLatLng,
                                                userId = currentUserId
                                            )
                                        )
                                        true
                                    }
                                )
                            }
                        }
                    }

                    // Render other members' locations and map markers (existing logic)
                    if (isInActiveGroup && currentUserId != null) {
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
                                                mainViewModel.showMarkerInfoDialog(
                                                    MainViewModel.MarkerDisplayInfo(
                                                        title = it.title ?: "Unknown Member",
                                                        timestamp = otherLoc.timestamp,
                                                        speed = otherLoc.speed,
                                                        bearing = otherLoc.bearing,
                                                        profilePhotoUrl = otherProfilePicUrl,
                                                        latLng = otherMemberLatLng,
                                                        userId = otherLoc.userId
                                                    )
                                                )
                                                true
                                            }
                                        )
                                    } else {
                                        Marker(
                                            state = otherMarkerState,
                                            title = otherMemberProfile.displayName ?: "Unknown Member",
                                            onClick = {
                                                mainViewModel.showMarkerInfoDialog(
                                                    MainViewModel.MarkerDisplayInfo(
                                                        title = it.title ?: "Unknown Member",
                                                        timestamp = otherLoc.timestamp,
                                                        speed = otherLoc.speed,
                                                        bearing = otherLoc.bearing,
                                                        profilePhotoUrl = otherProfilePicUrl,
                                                        latLng = otherMemberLatLng,
                                                        userId = otherLoc.userId
                                                    )
                                                )
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
                                        mainViewModel.showMarkerInfoDialog(
                                            MainViewModel.MarkerDisplayInfo(
                                                title = mapMarker.message,
                                                timestamp = mapMarker.timestamp,
                                                speed = null,
                                                bearing = mapMarker.cameraBearing,
                                                profilePhotoUrl = mapMarker.photoUrl,
                                                latLng = markerLatLng,
                                                mapMarker = mapMarker,
                                                userId = null
                                            )
                                        )
                                        true
                                    }
                                )
                            }
                        }

                        // NEW: Render active geofence zones
                        activeGeofenceZones.forEach { geofenceZone ->
                            when (geofenceZone.shapeType) {
                                "CIRCLE" -> {
                                    geofenceZone.coordinates.firstOrNull()?.let { center ->
                                        geofenceZone.radiusMeters?.let { radius ->
                                            Circle(
                                                center = center,
                                                radius = radius,
                                                strokeColor = Color.Blue, // Customize color based on type/status
                                                strokeWidth = 5f,
                                                fillColor = Color.Blue.copy(alpha = 0.2f)
                                            )
                                        }
                                    }
                                }
                                "POLYGON", "RECTANGLE" -> {
                                    if (geofenceZone.coordinates.size >= 3) { // Polygons need at least 3 points
                                        Polygon(
                                            points = geofenceZone.coordinates,
                                            strokeColor = Color.Red, // Customize color based on type/status
                                            strokeWidth = 5f,
                                            fillColor = Color.Red.copy(alpha = 0.2f)
                                        )
                                    }
                                }
                            }
                        }

                        // NEW: Render active incidents as distinct markers/icons on the map
                        activeIncidents.forEach { incident ->
                            // You might want a custom icon for incidents, e.g., a "fire" icon
                            val incidentLatLng = geofenceAndIncidentViewModel.activeGeofenceZones.value.find { it.id == incident.geofenceZoneId }?.coordinates?.firstOrNull()
                            incidentLatLng?.let {
                                Marker(
                                    state = rememberMarkerState(position = it),
                                    title = incident.title,
                                    snippet = incident.status,
                                    // Use MarkerIconHelper.bitmapDescriptorFromVector directly
                                    icon = MarkerIconHelper.bitmapDescriptorFromVector(context, R.drawable.ic_crisis_alert),
                                    onClick = {
                                        // TODO: Implement navigation to IncidentDetailScreen
                                        mainViewModel.showToastEvent("Clicked on Incident: ${incident.title}")
                                        // To open incident detail screen, you might set a selectedIncident in VM and trigger a dialog/navigation
                                        true
                                    }
                                )
                            }
                        }
                    }
                }

                // Top app bar / logo area
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
                        onClick = { mainViewModel.navigateToShutdown() },
                        label = "SHUTDOWN",
                        icon = { Icon(Icons.Filled.ExitToApp, "Logout/Shutdown") },
                        enabled = true,
                        fabEnabledColor = MaterialTheme.colorScheme.error,
                        fabDisabledColor = MaterialTheme.colorScheme.error,
                        fabEnabledContentColor = MaterialTheme.colorScheme.onError,
                        fabDisabledContentColor = MaterialTheme.colorScheme.onError
                    )

                    LabeledFab(
                        onClick = { mainViewModel.toggleMapLock() },
                        label = if (mapLockedToUserLocation) "UNLOCK MAP" else "LOCK MAP",
                        icon = { Icon(imageVector = if (mapLockedToUserLocation) Icons.Filled.LockOpen else Icons.Filled.Lock, contentDescription = "Toggle Map Lock") },
                        enabled = true,
                        fabEnabledColor = if (mapLockedToUserLocation) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primary,
                        fabDisabledColor = Color.Transparent,
                        fabEnabledContentColor = if (mapLockedToUserLocation) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onPrimary,
                        fabDisabledContentColor = Color.Transparent
                    )

                    LabeledFab(
                        onClick = { mainViewModel.navigateToUserSettings() },
                        label = "USER SETTINGS",
                        icon = { Icon(Icons.Filled.Settings, "Member Settings") },
                        enabled = true,
                        fabEnabledColor = fabEnabledColor,
                        fabDisabledColor = fabDisabledColor,
                        fabEnabledContentColor = fabEnabledContentColor,
                        fabDisabledContentColor = fabDisabledContentColor
                    )

                    // NEW: Group Chat FAB with Badge
                    BadgedBox(
                        badge = {
                            if (newChatMessagesCount.toInt() > 0) {
                                Badge(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    contentColor = Color.White,
                                    modifier = Modifier.size(20.dp)
                                ) {
                                    Text(
                                        text = if (newChatMessagesCount.toInt() > 9) "10+" else newChatMessagesCount.toString(),
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        },
                        modifier = Modifier.padding(bottom = 0.dp)
                    ) {
                        LabeledFab(
                            onClick = {
                                if (isInActiveGroup) {
                                    mainViewModel.navigateToGroupChat()
                                } else {
                                    mainViewModel.showToastEvent("Must be in a Group for Chat!")
                                }
                            },
                            label = "GROUP CHAT",
                            icon = { Icon(Icons.Filled.Chat, "Chat Window") },
                            enabled = isInActiveGroup,
                            fabEnabledColor = fabEnabledColor,
                            fabDisabledColor = fabDisabledColor,
                            fabEnabledContentColor = fabEnabledContentColor,
                            fabDisabledContentColor = fabDisabledContentColor
                        )
                    }


                    LabeledFab(
                        onClick = {
                            if (isInActiveGroup) {
                                mainViewModel.navigateToAddMapMarker()
                            } else {
                                mainViewModel.showToastEvent("Must be in a Group to add markers!")
                            }
                        },
                        label = "ADD MARKER",
                        icon = { Icon(Icons.Filled.LocationOn, "Add Marker") },
                        enabled = isInActiveGroup,
                        fabEnabledColor = fabEnabledColor,
                        fabDisabledColor = fabDisabledColor,
                        fabEnabledContentColor = fabEnabledContentColor,
                        fabDisabledContentColor = fabDisabledContentColor
                    )

                    LabeledFab(
                        onClick = { mainViewModel.navigateToCreateGroup() },
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
                        onClick = { if (isInActiveGroup) { mainViewModel.showToastEvent("Group Settings clicked!") } else { mainViewModel.showToastEvent("Must be in a Group to access Group Settings!") } },
                        label = "GROUP SETTINGS",
                        icon = { Icon(Icons.Filled.Tune, "Group Settings") },
                        enabled = isInActiveGroup,
                        fabEnabledColor = fabEnabledColor,
                        fabDisabledColor = fabDisabledColor,
                        fabEnabledContentColor = fabEnabledContentColor,
                        fabDisabledContentColor = fabDisabledContentColor
                    )

                    LabeledFab(
                        onClick = {
                            if (isInActiveGroup) {
                                mainViewModel.navigateToGroupStatus()
                            } else {
                                mainViewModel.showToastEvent("Must be in a Group to view Group Status!")
                            }
                        },
                        label = "GROUP STATUS",
                        icon = { Icon(Icons.Filled.Info, "Group Status") },
                        enabled = isInActiveGroup,
                        fabEnabledColor = fabEnabledColor,
                        fabDisabledColor = fabDisabledColor,
                        fabEnabledContentColor = fabEnabledContentColor,
                        fabDisabledContentColor = fabDisabledContentColor
                    )


                    LabeledFab(
                        onClick = { mainViewModel.navigateToNotifications() },
                        label = "NOTIFICATIONS",
                        icon = { Icon(Icons.Filled.Notifications, "Notifications") },
                        enabled = true,
                        fabEnabledColor = fabEnabledColor,
                        fabDisabledColor = fabDisabledColor,
                        fabEnabledContentColor = fabEnabledContentColor,
                        fabDisabledContentColor = fabDisabledContentColor
                    )

                    // NEW: Navigate to GeofenceManagementScreen
                    LabeledFab(
                        onClick = {
                            if (isInActiveGroup) {
                                navController.navigate(NavRoutes.GEOFENCE_MANAGEMENT) // Navigate to the new management screen
                            } else {
                                mainViewModel.showToastEvent("Must be in a Group for Geofencing!")
                            }
                        },
                        label = "GEOFENCING",
                        icon = { Icon(Icons.Filled.Polyline, "Manage Geofences") }, // Changed icon description
                        enabled = isInActiveGroup,
                        fabEnabledColor = fabEnabledColor,
                        fabDisabledColor = fabDisabledColor,
                        fabEnabledContentColor = fabEnabledContentColor,
                        fabDisabledContentColor = fabDisabledContentColor
                    )

                    LabeledFab(
                        onClick = {
                            if (isInActiveGroup) {
                                mainViewModel.navigateToTeamList()
                            } else {
                                mainViewModel.showToastEvent("Must be in a Group to view Team List!")
                            }
                        },
                        label = "GROUP MEMBERS",
                        icon = { Icon(Icons.Filled.People, "Team List") },
                        enabled = isInActiveGroup,
                        fabEnabledColor = fabEnabledColor,
                        fabDisabledColor = fabDisabledColor,
                        fabEnabledContentColor = fabEnabledContentColor,
                        fabDisabledContentColor = fabDisabledContentColor
                    )

                    LabeledFab(
                        onClick = { mainViewModel.navigateToGroupsList() },
                        label = "JOIN GROUPS",
                        icon = { Icon(Icons.Filled.Groups, "Groups List") },
                        enabled = true,
                        fabEnabledColor = fabEnabledColor,
                        fabDisabledColor = fabDisabledColor,
                        fabEnabledContentColor = fabEnabledContentColor,
                        fabDisabledContentColor = fabDisabledContentColor
                    )
                }

                // NEW: Map Context Menu for Incident Geofence
                if (showMapContextMenu && mapContextMenuLatLng != null) {
                    AlertDialog(
                        onDismissRequest = { showMapContextMenu = false; mapContextMenuLatLng = null },
                        title = { Text("Map Options") },
                        text = {
                            Column {
                                Button(
                                    onClick = {
                                        showMapContextMenu = false
                                        showCreateIncidentGeofenceDialog = true
                                        // Pre-fill incident geofence name based on context menu location or a generic name
                                        incidentGeofenceName = "Incident Zone @ ${String.format("%.4f, %.4f", mapContextMenuLatLng!!.latitude, mapContextMenuLatLng!!.longitude)}"
                                        // You could also pre-select a drawing mode here if you want to force circle/rect
                                    },
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                ) {
                                    Text("Create Incident Geofence Here")
                                }
                                // Other potential options (e.g., "Add Marker Here", "View Nearby")
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = { showMapContextMenu = false }) {
                                Text("Close")
                            }
                        }
                    )
                }

                // NEW: Create Incident Geofence Dialog (similar to CreateGeofenceDrawScreen's save dialog)
                if (showCreateIncidentGeofenceDialog && mapContextMenuLatLng != null) {
                    AlertDialog(
                        onDismissRequest = { showCreateIncidentGeofenceDialog = false; incidentGeofenceName = ""; incidentDescription = ""; incidentTitle = ""; incidentType = "" },
                        title = { Text("Create Incident Geofence") },
                        text = {
                            Column {
                                Text("Define a temporary geofence for this incident.", color = DarkBlue.copy(alpha = 0.8f), fontSize = 14.sp)
                                Spacer(Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = incidentGeofenceName,
                                    onValueChange = { incidentGeofenceName = it },
                                    label = { Text("Geofence Name", color = DarkBlue) },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )
                                Spacer(Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = incidentGeofenceDescription,
                                    onValueChange = { incidentGeofenceDescription = it },
                                    label = { Text("Geofence Description (Optional)", color = DarkBlue) },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = OutlinedTextFieldDefaults.colors( // Added colors for better theming consistency
                                        focusedTextColor = DarkBlue, unfocusedTextColor = DarkBlue.copy(alpha = 0.8f),
                                        focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                                        focusedBorderColor = DarkBlue, unfocusedBorderColor = DarkBlue.copy(alpha = 0.5f),
                                        focusedLabelColor = DarkBlue, unfocusedLabelColor = DarkBlue.copy(alpha = 0.7f)
                                    )
                                )
                                Spacer(Modifier.height(16.dp))
                                Text("Incident Details:", fontWeight = FontWeight.Bold, color = DarkBlue)
                                OutlinedTextField(
                                    value = incidentTitle,
                                    onValueChange = { incidentTitle = it },
                                    label = { Text("Incident Title (e.g., 'Fight - West Field')", color = DarkBlue) },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors( // Added colors for better theming consistency
                                        focusedTextColor = DarkBlue, unfocusedTextColor = DarkBlue.copy(alpha = 0.8f),
                                        focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                                        focusedBorderColor = DarkBlue, unfocusedBorderColor = DarkBlue.copy(alpha = 0.5f),
                                        focusedLabelColor = DarkBlue, unfocusedLabelColor = DarkBlue.copy(alpha = 0.7f)
                                    )
                                )
                                Spacer(Modifier.height(8.dp))
                                // Incident Type Dropdown (from GroupSettings)
                                ExposedDropdownMenuBox(
                                    expanded = incidentTypeDropdownExpanded,
                                    onExpandedChange = { incidentTypeDropdownExpanded = !incidentTypeDropdownExpanded }
                                ) {
                                    OutlinedTextField(
                                        value = incidentType,
                                        onValueChange = {},
                                        readOnly = true,
                                        label = { Text("Incident Type", color = DarkBlue) },
                                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                                        colors = OutlinedTextFieldDefaults.colors( // Added colors for better theming consistency
                                            focusedTextColor = DarkBlue, unfocusedTextColor = DarkBlue.copy(alpha = 0.8f),
                                            focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                                            focusedBorderColor = DarkBlue, unfocusedBorderColor = DarkBlue.copy(alpha = 0.5f),
                                            focusedLabelColor = DarkBlue, unfocusedLabelColor = DarkBlue.copy(alpha = 0.7f)
                                        )
                                    )
                                    ExposedDropdownMenu(
                                        expanded = incidentTypeDropdownExpanded,
                                        onDismissRequest = { incidentTypeDropdownExpanded = false },
                                        modifier = Modifier.background(LightCream)
                                    ) {
                                        // Ensure groupSettings and customIncidentTypes are not null
                                        groupSettings?.customIncidentTypes?.keys?.forEach { type ->
                                            DropdownMenuItem(
                                                text = { Text(type, color = DarkBlue) },
                                                onClick = {
                                                    incidentType = type
                                                    incidentTypeDropdownExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = incidentDescription,
                                    onValueChange = { incidentDescription = it },
                                    label = { Text("Incident Description (Optional)", color = DarkBlue) },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = OutlinedTextFieldDefaults.colors( // Added colors for better theming consistency
                                        focusedTextColor = DarkBlue, unfocusedTextColor = DarkBlue.copy(alpha = 0.8f),
                                        focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                                        focusedBorderColor = DarkBlue, unfocusedBorderColor = DarkBlue.copy(alpha = 0.5f),
                                        focusedLabelColor = DarkBlue, unfocusedLabelColor = DarkBlue.copy(alpha = 0.7f)
                                    )
                                )
                                // TODO: UI for assigning members to incident (multi-select from active group members)
                            }
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    val currentLatLng = mapContextMenuLatLng // Use the long-pressed location
                                    val currentGroup = activeGroup
                                    val currentUser = currentUserId

                                    if (currentLatLng == null || currentGroup == null || currentUser == null) {
                                        mainViewModel.showToastEvent("Error: Missing location, group, or user data.")
                                        return@Button
                                    }
                                    if (incidentGeofenceName.isBlank() || incidentTitle.isBlank() || incidentType.isBlank()) {
                                        mainViewModel.showToastEvent("Please fill in all required fields.")
                                        return@Button
                                    }

                                    coroutineScope.launch {
                                        // Create the Incident first
                                        val newIncident = Incident(
                                            id = UUID.randomUUID().toString(),
                                            groupId = currentGroup.groupID,
                                            geofenceZoneId = "", // Will be filled after zone is created
                                            incidentType = incidentType,
                                            title = incidentTitle,
                                            description = incidentDescription,
                                            status = "OPEN",
                                            createdByUserId = currentUser,
                                            assignedPersonnel = emptyMap() // For now, no assigned personnel at creation
                                        )
                                        val addIncidentResult = geofenceAndIncidentViewModel.addIncident(newIncident) // Use VM function directly

                                        if (addIncidentResult.isSuccess) {
                                            val incidentId = addIncidentResult.getOrNull()
                                            val newZone = GeofenceZone(
                                                id = UUID.randomUUID().toString(), // Generate new ID
                                                groupId = currentGroup.groupID,
                                                name = incidentGeofenceName,
                                                description = incidentGeofenceDescription,
                                                type = "INCIDENT_AD_HOC",
                                                shapeType = "CIRCLE", // Default to circle for quick incident drop
                                                coordinates = listOf(currentLatLng), // Center is the tapped location
                                                radiusMeters = 50.0, // Default small radius for incident
                                                createdByUserId = currentUser,
                                                incidentId = incidentId,
                                                expirationTimestamp = System.currentTimeMillis() + (24 * 60 * 60 * 1000L) // Expires in 24 hours
                                            )
                                            val addZoneResult = geofenceAndIncidentViewModel.addGeofenceZone(newZone) // Use VM function directly

                                            if (addZoneResult.isSuccess) {
                                                val zoneId = addZoneResult.getOrNull() ?: newZone.id
                                                // Update the incident with the new geofence ID
                                                val updatedIncident = newIncident.copy(id = incidentId!!, geofenceZoneId = zoneId)
                                                geofenceAndIncidentViewModel.updateIncident(updatedIncident)
                                                geofenceAndIncidentViewModel.addIncidentLogEntry(IncidentLogEntry(
                                                    incidentId = incidentId,
                                                    userId = currentUser,
                                                    type = "INCIDENT_CREATED",
                                                    details = "Incident '${incidentTitle}' created with geofence '${incidentGeofenceName}' at ${currentLatLng.latitude}, ${currentLatLng.longitude}."
                                                ))
                                                mainViewModel.showToastEvent("Incident Geofence created!")
                                                showCreateIncidentGeofenceDialog = false
                                                showMapContextMenu = false
                                                mapContextMenuLatLng = null
                                            } else {
                                                mainViewModel.showToastEvent("Failed to create incident geofence: ${addZoneResult.exceptionOrNull()?.message}")
                                                // TODO: Potentially delete the incident if geofence creation failed
                                            }
                                        } else {
                                            mainViewModel.showToastEvent("Failed to create incident: ${addIncidentResult.exceptionOrNull()?.message}")
                                        }
                                    }
                                },
                                enabled = !isGeofenceLoading // NEW: Use collected state
                            ) {
                                if (isGeofenceLoading) { // NEW: Use collected state
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
                                } else {
                                    Text("Create Incident")
                                }
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showCreateIncidentGeofenceDialog = false; showMapContextMenu = false; mapContextMenuLatLng = null }) {
                                Text("Cancel")
                            }
                        }
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
                            onDismissRequest = { mainViewModel.dismissMarkerInfoDialog() },
                            personUserId = currentMarkerInfo!!.userId
                        )
                    } else {
                        MapMarkerInfoDialog(
                            mapMarker = currentMarkerInfo!!.mapMarker!!,
                            onDismissRequest = { mainViewModel.dismissMarkerInfoDialog() }
                        )
                    }
                }

                if (showExpiredGroupDialog && expiredGroupDialogMessage != null) {
                    AlertDialog(
                        onDismissRequest = {
                            mainViewModel.dismissExpiredGroupDialog()
                        },
                        title = { Text("Group Expired") },
                        text = { Text(expiredGroupDialogMessage!!) },
                        confirmButton = {
                            Button(onClick = {
                                mainViewModel.dismissExpiredGroupDialog()
                            }) {
                                Text("OK")
                            }
                        }
                    )
                }
            }

            // Instructions overlay
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
                        onClick = { mainViewModel.dismissInstructionsOverlay() },
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

            // Map Loading Dialog
            if (showMapLoadingDialog && currentUserLocation == null) {
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
            ) {
                Text(
                    text = label,
                    color = if (enabled) fabEnabledContentColor else fabDisabledContentColor,
                    fontSize = 8.sp,
                    lineHeight = 4.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }
        }
    }
}
