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

class MainScreen(private val navController: NavHostController) {

    @OptIn(ExperimentalMaterial3Api::class)
    @SuppressLint("MissingPermission")
    @Composable
    fun Content(viewModel: MainViewModel = viewModel()) {
        val context = LocalContext.current
        val coroutineScope = rememberCoroutineScope()

        // UI state variables - now collected from ViewModel
        val currentUserId by viewModel.currentUserId.collectAsStateWithLifecycle()
        val currentUserLocation by viewModel.currentUserLocation.collectAsStateWithLifecycle()
        val currentUserModel by viewModel.currentUserModel.collectAsStateWithLifecycle()
        val isInActiveGroup by viewModel.isInActiveGroup.collectAsStateWithLifecycle()
        val activeGroup by viewModel.activeGroup.collectAsStateWithLifecycle()
        val otherMembersLocations by viewModel.otherMembersLocations.collectAsStateWithLifecycle()
        val otherMembersProfiles by viewModel.otherMembersProfiles.collectAsStateWithLifecycle()
        val mapMarkers by viewModel.mapMarkers.collectAsStateWithLifecycle()
        val userMessage by viewModel.userMessage.collectAsStateWithLifecycle()

        val mapLockedToUserLocation by viewModel.mapLockedToUserLocation.collectAsStateWithLifecycle()
        val showInstructionsOverlay by viewModel.showInstructionsOverlay.collectAsStateWithLifecycle()
        val countdownString by viewModel.countdownString.collectAsStateWithLifecycle()

        val showExpiredGroupDialog by viewModel.showExpiredGroupDialog.collectAsStateWithLifecycle()
        val expiredGroupDialogMessage by viewModel.expiredGroupDialogMessage.collectAsStateWithLifecycle()

        val showCustomMarkerInfoDialog by viewModel.showCustomMarkerInfoDialog.collectAsStateWithLifecycle()
        val currentMarkerInfo by viewModel.currentMarkerInfo.collectAsStateWithLifecycle()

        val showMapLoadingDialog by viewModel.showMapLoadingDialog.collectAsStateWithLifecycle()
        val lastKnownCameraPosition by viewModel.lastKnownCameraPosition.collectAsStateWithLifecycle() // NEW: Collect last known camera position


        // Local UI state (not persistent across recompositions/navigation, purely visual)
        val snackbarHostState = remember { SnackbarHostState() }


        // NEW: Initialize cameraPositionState based on lastKnownCameraPosition or default
        val cameraPositionState = rememberCameraPositionState {
            position = lastKnownCameraPosition ?: LatLng(39.8283, -98.5795).let { CameraPosition.fromLatLngZoom(it, 3f) }
        }

        // Effect for showing Snackbar messages from ViewModel
        LaunchedEffect(userMessage) {
            userMessage?.let { message ->
                if (!message.contains("has expired. You have been automatically removed.")) {
                    snackbarHostState.showSnackbar(message)
                }
                viewModel.clearUserMessage()
            }
        }

        // Effect for observing Toast events from ViewModel
        LaunchedEffect(viewModel.toastEvent) {
            viewModel.toastEvent.collect { message ->
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }
        }

        // Effect for observing navigation events from ViewModel
        LaunchedEffect(viewModel.navigationEvent) {
            viewModel.navigationEvent.collect { route ->
                navController.navigate(route)
            }
        }

        // Effect for map centering and initial load
        LaunchedEffect(currentUserLocation, showMapLoadingDialog, mapLockedToUserLocation) { // Added mapLockedToUserLocation to trigger re-evaluation
            currentUserLocation?.let { loc ->
                val targetLatLng = LatLng(loc.latitude, loc.longitude)
                // Only animate to user's location if map is locked OR it's the initial loading phase
                if (mapLockedToUserLocation || showMapLoadingDialog) {
                    cameraPositionState.animate(
                        CameraUpdateFactory.newLatLngZoom(targetLatLng, 15f)
                    )
                    // Log.d("MainScreen", "Camera moved to: ${loc.latitude}, ${loc.longitude}") // Removed for brevity
                }
            }
        }

        // NEW: Observe camera idle events to save position to ViewModel
        LaunchedEffect(cameraPositionState.isMoving) {
            if (!cameraPositionState.isMoving) { // When camera stops moving (idle)
                viewModel.setLastKnownCameraPosition(cameraPositionState.position)
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
                    properties = MapProperties(isMyLocationEnabled = false, isBuildingEnabled = true),
                    uiSettings = MapUiSettings(zoomControlsEnabled = false)
                ) {
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
                                        viewModel.showMarkerInfoDialog(
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
                                        viewModel.showMarkerInfoDialog(
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
                                                viewModel.showMarkerInfoDialog(
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
                                                viewModel.showMarkerInfoDialog(
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
                                        viewModel.showMarkerInfoDialog(
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
                        onClick = { viewModel.navigateToShutdown() },
                        label = "SHUTDOWN",
                        icon = { Icon(Icons.Filled.ExitToApp, "Logout/Shutdown") },
                        enabled = true,
                        fabEnabledColor = MaterialTheme.colorScheme.error,
                        fabDisabledColor = MaterialTheme.colorScheme.error,
                        fabEnabledContentColor = MaterialTheme.colorScheme.onError,
                        fabDisabledContentColor = MaterialTheme.colorScheme.onError
                    )

                    LabeledFab(
                        onClick = { viewModel.toggleMapLock() },
                        label = if (mapLockedToUserLocation) "UNLOCK MAP" else "LOCK MAP",
                        icon = { Icon(imageVector = if (mapLockedToUserLocation) Icons.Filled.LockOpen else Icons.Filled.Lock, contentDescription = "Toggle Map Lock") },
                        enabled = true,
                        fabEnabledColor = if (mapLockedToUserLocation) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primary,
                        fabDisabledColor = Color.Transparent,
                        fabEnabledContentColor = if (mapLockedToUserLocation) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onPrimary,
                        fabDisabledContentColor = Color.Transparent
                    )

                    LabeledFab(
                        onClick = { viewModel.navigateToUserSettings() },
                        label = "USER SETTINGS",
                        icon = { Icon(Icons.Filled.Settings, "Member Settings") },
                        enabled = true,
                        fabEnabledColor = fabEnabledColor,
                        fabDisabledColor = fabDisabledColor,
                        fabEnabledContentColor = fabEnabledContentColor,
                        fabDisabledContentColor = fabDisabledContentColor
                    )

                    LabeledFab(
                        onClick = { if (isInActiveGroup) { viewModel.showToastEvent("Chat Window clicked!") } else { viewModel.showToastEvent("Must be in a Group for Chat!") } },
                        label = "GROUP CHAT",
                        icon = { Icon(Icons.Filled.Chat, "Chat Window") },
                        enabled = isInActiveGroup,
                        fabEnabledColor = fabEnabledColor,
                        fabDisabledColor = fabDisabledColor,
                        fabEnabledContentColor = fabEnabledContentColor,
                        fabDisabledContentColor = fabDisabledContentColor
                    )

                    LabeledFab(
                        onClick = {
                            if (isInActiveGroup) {
                                viewModel.navigateToAddMapMarker()
                            } else {
                                viewModel.showToastEvent("Must be in a Group to add markers!")
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
                        onClick = { viewModel.navigateToCreateGroup() },
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
                        onClick = { if (isInActiveGroup) { viewModel.showToastEvent("Group Settings clicked!") } else { viewModel.showToastEvent("Must be in a Group to access Group Settings!") } },
                        label = "GROUP SETTINGS",
                        icon = { Icon(Icons.Filled.Tune, "Group Settings") },
                        enabled = isInActiveGroup,
                        fabEnabledColor = fabEnabledColor,
                        fabDisabledColor = fabDisabledColor,
                        fabEnabledContentColor = fabEnabledContentColor,
                        fabDisabledContentColor = fabDisabledContentColor
                    )

                    LabeledFab(
                        onClick = { if (isInActiveGroup) { viewModel.showToastEvent("Group Status clicked!") } else { viewModel.showToastEvent("Must be in a Group to access Group Status!") } },
                        label = "GROUP STATUS",
                        icon = { Icon(Icons.Filled.Info, "Group Status") },
                        enabled = isInActiveGroup,
                        fabEnabledColor = fabEnabledColor,
                        fabDisabledColor = fabDisabledColor,
                        fabEnabledContentColor = fabEnabledContentColor,
                        fabDisabledContentColor = fabDisabledContentColor
                    )

                    LabeledFab(
                        onClick = { if (isInActiveGroup) { viewModel.showToastEvent("Send FCM Notification clicked!") } else { viewModel.showToastEvent("Must be in a Group to Send Alerts!") } },
                        label = "SEND ALERTS",
                        icon = { Icon(Icons.Filled.Notifications, "Send FCM Notification") },
                        enabled = isInActiveGroup,
                        fabEnabledColor = fabEnabledColor,
                        fabDisabledColor = fabDisabledColor,
                        fabEnabledContentColor = fabEnabledContentColor,
                        fabDisabledContentColor = fabDisabledContentColor
                    )

                    LabeledFab(
                        onClick = { if (isInActiveGroup) { viewModel.navigateToGeofence() } else { viewModel.showToastEvent("Must be in a Group for Geofencing!") } },
                        label = "GEOFENCING",
                        icon = { Icon(Icons.Filled.Polyline, "Add GeoFence") },
                        enabled = isInActiveGroup,
                        fabEnabledColor = fabEnabledColor,
                        fabDisabledColor = fabDisabledColor,
                        fabEnabledContentColor = fabEnabledContentColor,
                        fabDisabledContentColor = fabDisabledContentColor
                    )

                    LabeledFab(
                        onClick = {
                            if (isInActiveGroup) {
                                viewModel.navigateToTeamList()
                            } else {
                                viewModel.showToastEvent("Must be in a Group to view Team List!")
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
                        onClick = { viewModel.navigateToGroupsList() },
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
                            onDismissRequest = { viewModel.dismissMarkerInfoDialog() },
                            personUserId = currentMarkerInfo!!.userId
                        )
                    } else {
                        MapMarkerInfoDialog(
                            mapMarker = currentMarkerInfo!!.mapMarker!!,
                            onDismissRequest = { viewModel.dismissMarkerInfoDialog() }
                        )
                    }
                }

                if (showExpiredGroupDialog && expiredGroupDialogMessage != null) {
                    AlertDialog(
                        onDismissRequest = {
                            viewModel.dismissExpiredGroupDialog()
                        },
                        title = { Text("Group Expired") },
                        text = { Text(expiredGroupDialogMessage!!) },
                        confirmButton = {
                            Button(onClick = {
                                viewModel.dismissExpiredGroupDialog()
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
                        onClick = { viewModel.dismissInstructionsOverlay() },
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
                    .padding(bottom = 4.dp) // Maintain spacing from the FAB circle
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
