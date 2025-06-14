// In file: app/src/main/java/com/artificialinsightsllc/teamsync/Screens/CreateGeofenceDrawScreen.kt
package com.artificialinsightsllc.teamsync.Screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.location.Location
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.annotation.RequiresApi
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList // Import this if needed
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.artificialinsightsllc.teamsync.Models.GeofenceZone
import com.artificialinsightsllc.teamsync.Models.GeofenceRule
import com.artificialinsightsllc.teamsync.Models.Incident
import com.artificialinsightsllc.teamsync.Models.IncidentLogEntry
import com.artificialinsightsllc.teamsync.Models.IncidentPersonnelStatus
import com.artificialinsightsllc.teamsync.R
import com.artificialinsightsllc.teamsync.Services.FirestoreService
import com.artificialinsightsllc.teamsync.ViewModels.GeofenceAndIncidentViewModel
import com.artificialinsightsllc.teamsync.ui.theme.DarkBlue
import com.artificialinsightsllc.teamsync.ui.theme.LightCream
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.SphericalUtil
import com.google.maps.android.compose.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID


// Enum to manage which shape is currently being edited
private enum class DrawingMode {
    NONE, CIRCLE, POLYGON, RECTANGLE
}

// Data class to hold properties of the shape being drawn/edited
private data class EditableShape(
    val id: String = UUID.randomUUID().toString(),
    var type: DrawingMode,
    var center: LatLng? = null, // For CIRCLE
    var radius: Double? = null, // For CIRCLE
    var points: SnapshotStateList<LatLng> = mutableStateListOf(), // For POLYGON, RECTANGLE
    var isEditing: Boolean = true // True if actively being drawn/resized
)

class CreateGeofenceDrawScreen(private val navController: NavHostController) {

    @RequiresApi(Build.VERSION_CODES.Q) // For background location, if used in future
    @OptIn(ExperimentalMaterial3Api::class)
    @SuppressLint("MissingPermission")
    @Composable
    fun Content(
        geofenceZoneId: String? = null, // Optional parameter for editing existing geofence
        viewModel: GeofenceAndIncidentViewModel = viewModel()
    ) {
        val context = LocalContext.current
        val coroutineScope = rememberCoroutineScope()
        val currentUserId = viewModel.auth.currentUser?.uid
        val activeGroupId = viewModel.groupMonitorService.activeGroup.collectAsStateWithLifecycle().value?.groupID

        val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
        val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()

        // UI States for drawing
        var drawingMode by remember { mutableStateOf(DrawingMode.NONE) }
        var currentShape by remember { mutableStateOf<EditableShape?>(null) }
        var tempPolygonPoints = remember { mutableStateListOf<LatLng>() } // For polygon taps

        // UI States for saving
        var showSaveDialog by remember { mutableStateOf(false) }
        var geofenceName by remember { mutableStateOf("") }
        var geofenceDescription by remember { mutableStateOf("") }
        var geofenceType by remember { mutableStateOf("PRESET") } // Default type
        val geofenceTypeOptions = listOf("PRESET", "INCIDENT_AD_HOC", "JOB_SITE")
        var geofenceTypeDropdownExpanded by remember { mutableStateOf(false) }

        var showIncidentCreationDialog by remember { mutableStateOf(false) }
        var incidentTitle by remember { mutableStateOf("") }
        var incidentType by remember { mutableStateOf("") }
        var incidentDescription by remember { mutableStateOf("") }
        var incidentAssignedUsers = remember { mutableStateListOf<String>() }
        val customIncidentTypes by viewModel.groupSettings.collectAsStateWithLifecycle().value?.customIncidentTypes.let {
            remember(it) { mutableStateOf(it ?: emptyMap()) }
        }
        var incidentTypeDropdownExpanded by remember { mutableStateOf(false) }

        // Map & Location States
        val cameraPositionState = rememberCameraPositionState {
            position = CameraPosition.fromLatLngZoom(LatLng(39.8283, -98.5795), 4f) // Default US center
        }
        var hasCenteredMapOnUser by remember { mutableStateOf(false) }
        val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

        // Icons for drawing handles
        val handleIcon = remember(context) { bitmapDescriptorFromVector(context, R.drawable.ic_handle_circle) }
        val resizeIcon = remember(context) { bitmapDescriptorFromVector(context, R.drawable.ic_swap_horiz) }
        val moveIcon = remember(context) { bitmapDescriptorFromVector(context, R.drawable.ic_open_with) }

        // State for the geofence being edited (if in edit mode)
        var loadedGeofenceZone by remember { mutableStateOf<GeofenceZone?>(null) }
        // State for original incident if editing an incident-linked geofence
        var loadedIncident by remember { mutableStateOf<Incident?>(null) }


        // Location permission launcher
        val locationPermissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
            onResult = { isGranted ->
                if (isGranted) {
                    fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
                        location?.let {
                            if (!hasCenteredMapOnUser && loadedGeofenceZone == null) { // Only center on user if not editing
                                val userLatLng = LatLng(it.latitude, it.longitude)
                                coroutineScope.launch {
                                    cameraPositionState.animate(CameraUpdateFactory.newCameraPosition(CameraPosition.fromLatLngZoom(userLatLng, 15f)))
                                    hasCenteredMapOnUser = true
                                }
                            }
                        }
                    }
                } else {
                    Toast.makeText(context, "Location permission is required to center the map.", Toast.LENGTH_LONG).show()
                }
            }
        )

        // Request location permission on launch
        LaunchedEffect(Unit) {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)

            // Fetch existing geofence if in edit mode
            if (geofenceZoneId != null) {
                viewModel.firestoreService.getGeofenceZone(geofenceZoneId).onSuccess { zone ->
                    loadedGeofenceZone = zone
                    if (zone != null) {
                        geofenceName = zone.name
                        geofenceDescription = zone.description ?: ""
                        geofenceType = zone.type
                        drawingMode = DrawingMode.valueOf(zone.shapeType)

                        currentShape = when (drawingMode) {
                            DrawingMode.CIRCLE -> EditableShape(
                                id = zone.id,
                                type = DrawingMode.CIRCLE,
                                center = zone.coordinates.firstOrNull(),
                                radius = zone.radiusMeters,
                                isEditing = true // Can be edited
                            )
                            // Corrected to use mutableStateListOf for points
                            DrawingMode.POLYGON, DrawingMode.RECTANGLE -> EditableShape(
                                id = zone.id,
                                type = drawingMode,
                                points = zone.coordinates.toMutableStateList(), // Use toMutableStateList
                                isEditing = true // Can be edited
                            )
                            else -> null
                        }
                        // Corrected: If it's a polygon or rectangle, tempPolygonPoints should reflect currentShape.points
                        if (drawingMode == DrawingMode.POLYGON || drawingMode == DrawingMode.RECTANGLE) {
                            tempPolygonPoints.clear()
                            currentShape?.points?.let { tempPolygonPoints.addAll(it) }
                        }


                        // Center map on the loaded geofence
                        when (drawingMode) {
                            DrawingMode.CIRCLE -> zone.coordinates.firstOrNull()?.let { center ->
                                coroutineScope.launch {
                                    cameraPositionState.animate(CameraUpdateFactory.newCameraPosition(CameraPosition.fromLatLngZoom(center, 15f)))
                                }
                            }
                            DrawingMode.POLYGON, DrawingMode.RECTANGLE -> {
                                if (zone.coordinates.isNotEmpty()) {
                                    val boundsBuilder = com.google.android.gms.maps.model.LatLngBounds.builder()
                                    zone.coordinates.forEach { boundsBuilder.include(it) }
                                    coroutineScope.launch {
                                        cameraPositionState.animate(CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), 100))
                                    }
                                }
                            }
                            else -> {}
                        }

                        // If linked to an incident, load incident details
                        if (zone.incidentId != null) {
                            viewModel.firestoreService.getIncident(zone.incidentId).onSuccess { incident ->
                                loadedIncident = incident
                                incidentTitle = incident?.title ?: ""
                                incidentType = incident?.incidentType ?: ""
                                incidentDescription = incident?.description ?: ""
                                incidentAssignedUsers.clear()
                                incident?.assignedPersonnel?.keys?.let { incidentAssignedUsers.addAll(it) }
                            }.onFailure { e ->
                                Log.e("CreateGeofenceDraw", "Error loading incident ${zone.incidentId}: ${e.message}")
                            }
                        }
                    } else {
                        Toast.makeText(context, "Geofence not found.", Toast.LENGTH_LONG).show()
                        navController.popBackStack()
                    }
                }.onFailure { e ->
                    Toast.makeText(context, "Failed to load geofence for editing: ${e.message}", Toast.LENGTH_LONG).show()
                    navController.popBackStack()
                }
            }
        }

        // Handle error messages from ViewModel
        LaunchedEffect(errorMessage) {
            errorMessage?.let { msg ->
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                viewModel.clearErrorMessage() // Clear the message after showing
            }
        }

        // UI Visibility control
        var showScreen by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            showScreen = true
        }

        fun resetDrawingState() {
            drawingMode = DrawingMode.NONE
            currentShape = null
            tempPolygonPoints.clear()
            geofenceName = ""
            geofenceDescription = ""
            geofenceType = "PRESET"
            incidentTitle = ""
            incidentType = ""
            incidentDescription = ""
            incidentAssignedUsers.clear()
            loadedGeofenceZone = null
            loadedIncident = null
        }

        fun saveGeofenceAndOptionalIncident() {
            if (activeGroupId == null || currentUserId == null) {
                viewModel.setErrorMessage("Active group or user not found. Cannot save geofence.")
                return
            }
            val currentShapeSnapshot = currentShape ?: run {
                viewModel.setErrorMessage("No shape defined to save.")
                return
            }

            // Basic validation
            if (geofenceName.isBlank()) {
                viewModel.setErrorMessage("Geofence name cannot be empty.")
                return
            }
            if (currentShapeSnapshot.points.isEmpty() && currentShapeSnapshot.type != DrawingMode.CIRCLE) {
                viewModel.setErrorMessage("Geofence shape not drawn or incomplete.")
                return
            }
            if (currentShapeSnapshot.type == DrawingMode.CIRCLE && (currentShapeSnapshot.center == null || currentShapeSnapshot.radius == null || currentShapeSnapshot.radius!! <= 0)) {
                viewModel.setErrorMessage("Circle geofence is incomplete (center or radius missing/invalid).")
                return
            }

            coroutineScope.launch {
                var incidentId: String? = loadedIncident?.id
                var newIncident: Incident? = loadedIncident

                if (geofenceType == "INCIDENT_AD_HOC") {
                    if (incidentTitle.isBlank()) {
                        viewModel.setErrorMessage("Incident title cannot be empty for incident geofences.")
                        return@launch
                    }
                    if (incidentType.isBlank()) {
                        viewModel.setErrorMessage("Incident type must be selected.")
                        return@launch
                    }

                    if (newIncident == null) { // Create new incident if not editing an existing one
                        newIncident = Incident(
                            id = UUID.randomUUID().toString(),
                            groupId = activeGroupId,
                            geofenceZoneId = "",
                            incidentType = incidentType,
                            title = incidentTitle,
                            description = incidentDescription,
                            status = "OPEN",
                            createdByUserId = currentUserId,
                            assignedPersonnel = incidentAssignedUsers.associateWith {
                                IncidentPersonnelStatus(currentStatus = "ASSIGNED")
                            }
                        )
                        val addIncidentResult = viewModel.addIncident(newIncident)
                        if (addIncidentResult.isSuccess) {
                            incidentId = addIncidentResult.getOrNull()
                            Log.d("CreateGeofenceDraw", "Incident created with ID: $incidentId")
                        } else {
                            viewModel.setErrorMessage("Failed to create incident: ${addIncidentResult.exceptionOrNull()?.message}")
                            return@launch
                        }
                    } else { // Update existing incident
                        newIncident = newIncident.copy(
                            incidentType = incidentType,
                            title = incidentTitle,
                            description = incidentDescription,
                            assignedPersonnel = incidentAssignedUsers.associateWith {
                                IncidentPersonnelStatus(currentStatus = newIncident?.assignedPersonnel?.get(it)?.currentStatus ?: "ASSIGNED")
                            }
                        )
                        val updateIncidentResult = viewModel.firestoreService.updateIncident(newIncident)
                        if (updateIncidentResult.isSuccess) {
                            Log.d("CreateGeofenceDraw", "Incident updated: ${newIncident.id}")
                            incidentId = newIncident.id
                        } else {
                            viewModel.setErrorMessage("Failed to update incident: ${updateIncidentResult.exceptionOrNull()?.message}")
                            return@launch
                        }
                    }
                } else if (loadedIncident != null) {
                    // If geofence type changed from incident to non-incident, potentially disassociate/delete incident
                    incidentId = null
                    newIncident = null
                    Log.d("CreateGeofenceDraw", "Geofence type changed from incident, disassociating incident.")
                }


                val newZone = (loadedGeofenceZone ?: GeofenceZone(id = UUID.randomUUID().toString())).copy(
                    groupId = activeGroupId,
                    name = geofenceName,
                    description = geofenceDescription,
                    type = geofenceType,
                    shapeType = currentShapeSnapshot.type.name,
                    coordinates = currentShapeSnapshot.points.toList(),
                    radiusMeters = currentShapeSnapshot.radius,
                    createdByUserId = currentUserId,
                    incidentId = incidentId,
                    expirationTimestamp = if (geofenceType == "INCIDENT_AD_HOC") System.currentTimeMillis() + (24 * 60 * 60 * 1000L) else null
                )

                val result = if (loadedGeofenceZone != null) {
                    viewModel.firestoreService.updateGeofenceZone(newZone)
                } else {
                    viewModel.addGeofenceZone(newZone)
                }

                if (result.isSuccess) {
                    val zoneId = if (loadedGeofenceZone != null) newZone.id else result.getOrNull()
                    Log.d("CreateGeofenceDraw", "Geofence Zone saved with ID: $zoneId")

                    // Update the incident with the geofenceZoneId if an incident was created/updated
                    if (incidentId != null && newIncident != null && newIncident.geofenceZoneId != zoneId) {
                        val updatedIncidentWithZone = newIncident.copy(id = incidentId, geofenceZoneId = (zoneId ?: "").toString())
                        viewModel.updateIncident(updatedIncidentWithZone)
                        // Corrected string formatting for LatLng objects
                        val locationDetails: String = currentShapeSnapshot.center?.let { center ->
                            "Lat: %.4f, Lon: %.4f".format(center.latitude, center.longitude)
                        } ?: currentShapeSnapshot.points.firstOrNull()?.let { point ->
                            "Lat: %.4f, Lon: %.4f".format(point.latitude, point.longitude)
                        } ?: "unknown location"

                        viewModel.addIncidentLogEntry(IncidentLogEntry(
                            incidentId = incidentId,
                            userId = currentUserId,
                            type = if (loadedGeofenceZone != null) "INCIDENT_GEOFENCE_UPDATED" else "INCIDENT_CREATED_WITH_GEOFENCE",
                            details = "Incident '${incidentTitle}' geofence updated/created: '${geofenceName}' at $locationDetails."
                        ))
                    } else if (incidentId == null && loadedIncident != null) {
                        // If geofence type changed away from incident, and there was a loaded incident, clear its geofenceZoneId
                        val disassociatedIncident = loadedIncident!!.copy(geofenceZoneId = "")
                        viewModel.updateIncident(disassociatedIncident)
                        viewModel.addIncidentLogEntry(IncidentLogEntry(
                            incidentId = disassociatedIncident.id,
                            userId = currentUserId,
                            type = "INCIDENT_GEOFENCE_DISASSOCIATED",
                            details = "Geofence for incident '${disassociatedIncident.title}' disassociated."
                        ))
                    }

                    Toast.makeText(context, "Geofence '${geofenceName}' saved!", Toast.LENGTH_SHORT).show()
                    resetDrawingState()
                    showSaveDialog = false
                    navController.popBackStack()
                } else {
                    viewModel.setErrorMessage("Failed to save geofence: ${result.exceptionOrNull()?.message}")
                }
            }
        }


        AnimatedVisibility(
            visible = showScreen,
            enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
            exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Image(
                    painter = painterResource(id = R.drawable.background1),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = {
                                Text(
                                    text = if (geofenceZoneId != null) "Edit Geofence" else "Draw Geofence",
                                    color = DarkBlue,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = Color.White.copy(alpha = 0.7f)
                            ),
                            navigationIcon = {
                                IconButton(onClick = { navController.popBackStack() }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = DarkBlue)
                                }
                            }
                        )
                    },
                    containerColor = Color.Transparent
                ) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        // Map Section
                        GoogleMap(
                            modifier = Modifier.weight(1f),
                            cameraPositionState = cameraPositionState,
                            uiSettings = MapUiSettings(zoomControlsEnabled = false, myLocationButtonEnabled = true),
                            properties = MapProperties(isMyLocationEnabled = true),
                            onMapClick = { latLng ->
                                if (geofenceZoneId == null || drawingMode == DrawingMode.POLYGON) {
                                    if (drawingMode == DrawingMode.POLYGON) {
                                        tempPolygonPoints.add(latLng)
                                        currentShape = EditableShape(type = DrawingMode.POLYGON, points = tempPolygonPoints)
                                    } else if (drawingMode == DrawingMode.NONE && currentShape == null) {
                                        // No action if not in drawing mode or no shape yet
                                    }
                                }
                            }
                        ) {
                            // Render the current shape being drawn
                            currentShape?.let { shape ->
                                when (shape.type) {
                                    DrawingMode.CIRCLE -> {
                                        shape.center?.let { center ->
                                            shape.radius?.let { radius ->
                                                Circle(
                                                    center = center,
                                                    radius = radius,
                                                    strokeColor = DarkBlue,
                                                    strokeWidth = 8f,
                                                    fillColor = DarkBlue.copy(alpha = 0.3f)
                                                )
                                                // Center handle
                                                val centerMarkerState = rememberMarkerState(position = center)
                                                LaunchedEffect(centerMarkerState) {
                                                    // Observe drag events for the center marker
                                                    snapshotFlow { centerMarkerState.position }
                                                        .collect { newLatLng ->
                                                            currentShape = shape.copy(center = newLatLng)
                                                        }
                                                }
                                                Marker(
                                                    state = centerMarkerState,
                                                    draggable = true, // Enable dragging
                                                    icon = moveIcon,
                                                    anchor = Offset(0.5f, 0.5f)
                                                )

                                                // Resize handle
                                                val resizeHandlePos = SphericalUtil.computeOffset(center, radius, 90.0) // East direction
                                                val resizeMarkerState = rememberMarkerState(position = resizeHandlePos)
                                                LaunchedEffect(resizeMarkerState) {
                                                    // Observe drag events for the resize handle marker
                                                    snapshotFlow { resizeMarkerState.position }
                                                        .collect { newLatLng ->
                                                            val newR = SphericalUtil.computeDistanceBetween(center, newLatLng)
                                                            if (newR > 10) currentShape = shape.copy(radius = newR)
                                                        }
                                                }
                                                Marker(
                                                    state = resizeMarkerState,
                                                    draggable = true, // Enable dragging
                                                    icon = resizeIcon,
                                                    anchor = Offset(0.5f, 0.5f)
                                                )
                                            }
                                        }
                                    }
                                    DrawingMode.POLYGON, DrawingMode.RECTANGLE -> {
                                        if (shape.points.isNotEmpty()) {
                                            Polygon(
                                                points = shape.points,
                                                strokeColor = DarkBlue,
                                                strokeWidth = 8f,
                                                fillColor = DarkBlue.copy(alpha = 0.3f)
                                            )
                                            shape.points.forEachIndexed { index, point ->
                                                val handleMarkerState = rememberMarkerState(position = point)
                                                LaunchedEffect(handleMarkerState) {
                                                    // Observe drag events for each polygon/rectangle handle
                                                    snapshotFlow { handleMarkerState.position }
                                                        .collect { newLatLng ->
                                                            val updatedPoints = shape.points.toMutableList() // Make a mutable copy
                                                            updatedPoints[index] = newLatLng
                                                            // For rectangle, ensure the other corners adjust to maintain orthogonal lines
                                                            if (shape.type == DrawingMode.RECTANGLE && updatedPoints.size == 4) {
                                                                // Assume points[0] and points[2] are diagonal corners
                                                                // If dragging point 0, update point 1 and 3's lat/lon
                                                                val p0 = updatedPoints[0]
                                                                val p2 = updatedPoints[2]

                                                                // Recalculate p1 (same lat as p0, same lon as p2)
                                                                updatedPoints[1] = LatLng(p0.latitude, p2.longitude)
                                                                // Recalculate p3 (same lat as p2, same lon as p0)
                                                                updatedPoints[3] = LatLng(p2.latitude, p0.longitude)

                                                                currentShape = shape.copy(points = updatedPoints.toMutableStateList())

                                                            } else {
                                                                currentShape = shape.copy(points = updatedPoints.toMutableStateList())
                                                            }
                                                        }
                                                }
                                                Marker(
                                                    state = handleMarkerState,
                                                    draggable = true, // Enable dragging
                                                    icon = handleIcon,
                                                    anchor = Offset(0.5f, 0.5f)
                                                )
                                            }
                                        }
                                    }
                                    DrawingMode.NONE -> { /* Do nothing */ }
                                }
                            }
                        }

                        // Drawing Controls & Save Button
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .wrapContentHeight()
                                .shadow(8.dp, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                                .background(LightCream.copy(alpha = 0.9f))
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (geofenceZoneId == null) { // Only show drawing mode buttons if creating new
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceAround
                                ) {
                                    DrawingModeButton(
                                        icon = Icons.Filled.RadioButtonUnchecked,
                                        label = "Circle",
                                        selected = drawingMode == DrawingMode.CIRCLE
                                    ) {
                                        resetDrawingState() // Reset for new drawing
                                        drawingMode = DrawingMode.CIRCLE
                                        currentShape = EditableShape(type = DrawingMode.CIRCLE, center = cameraPositionState.position.target, radius = 500.0) // Default 500m radius
                                    }
                                    DrawingModeButton(
                                        icon = Icons.Filled.CropSquare,
                                        label = "Rectangle",
                                        selected = drawingMode == DrawingMode.RECTANGLE
                                    ) {
                                        resetDrawingState() // Reset for new drawing
                                        drawingMode = DrawingMode.RECTANGLE
                                        // Initialize with a default small rectangle around center
                                        val center = cameraPositionState.position.target
                                        val offsetLat = 0.002 // Approx 200m in lat
                                        val offsetLon = 0.002 // Approx 200m in lon
                                        val p1 = LatLng(center.latitude - offsetLat, center.longitude - offsetLon)
                                        val p2 = LatLng(center.latitude - offsetLat, center.longitude + offsetLon)
                                        val p3 = LatLng(center.latitude + offsetLat, center.longitude + offsetLon)
                                        val p4 = LatLng(center.latitude + offsetLat, center.longitude - offsetLon)
                                        currentShape = EditableShape(type = DrawingMode.RECTANGLE, points = mutableStateListOf(p1, p2, p3, p4))
                                    }
                                    DrawingModeButton(
                                        icon = Icons.Filled.Polyline,
                                        label = "Polygon",
                                        selected = drawingMode == DrawingMode.POLYGON
                                    ) {
                                        resetDrawingState() // Reset for new drawing
                                        drawingMode = DrawingMode.POLYGON
                                        // Polygon drawing starts with taps, so no initial points
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                            }

                            // Show "Finish Polygon" or "Save Geofence"
                            if (drawingMode == DrawingMode.POLYGON && tempPolygonPoints.size >= 2) {
                                Button(
                                    onClick = {
                                        if (tempPolygonPoints.size < 3) {
                                            Toast.makeText(context, "Polygon needs at least 3 points to finish.", Toast.LENGTH_SHORT).show()
                                        } else {
                                            currentShape = currentShape?.copy(isEditing = false) // Mark as finished drawing
                                            drawingMode = DrawingMode.NONE // Exit drawing mode
                                            showSaveDialog = true
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = DarkBlue)
                                ) {
                                    Text("Finish Polygon", color = Color.White)
                                }
                            } else if (currentShape != null) { // If a shape is drawn or loaded for editing
                                Button(
                                    onClick = { showSaveDialog = true },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = DarkBlue)
                                ) {
                                    Text(if (geofenceZoneId != null) "Save Changes" else "Save Geofence", color = Color.White)
                                }
                            }

                            Spacer(Modifier.height(8.dp))
                            // Clear/Cancel Drawing Button
                            Button(
                                onClick = { resetDrawingState() },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            ) {
                                Text("Clear / Cancel", color = Color.White)
                            }
                        }
                    }
                }
            }
        }

        // Save Geofence Dialog
        if (showSaveDialog && currentShape != null) {
            val isIncidentType = geofenceType == "INCIDENT_AD_HOC"
            AlertDialog(
                onDismissRequest = { showSaveDialog = false /* Don't reset drawing state here, user might want to continue editing */ },
                title = { Text(if (geofenceZoneId != null) "Save Geofence Changes" else if (isIncidentType) "Create Incident Geofence" else "Save Geofence") },
                text = {
                    Column {
                        OutlinedTextField(
                            value = geofenceName,
                            onValueChange = { geofenceName = it },
                            label = { Text("Geofence Name", color = DarkBlue) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = DarkBlue, unfocusedTextColor = DarkBlue.copy(alpha = 0.8f),
                                focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                                focusedBorderColor = DarkBlue, unfocusedBorderColor = DarkBlue.copy(alpha = 0.5f),
                                focusedLabelColor = DarkBlue, unfocusedLabelColor = DarkBlue.copy(alpha = 0.7f),
                                cursorColor = DarkBlue
                            ),
                            singleLine = true
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = geofenceDescription,
                            onValueChange = { geofenceDescription = it },
                            label = { Text("Description (Optional)", color = DarkBlue) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = DarkBlue, unfocusedTextColor = DarkBlue.copy(alpha = 0.8f),
                                focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                                focusedBorderColor = DarkBlue, unfocusedBorderColor = DarkBlue.copy(alpha = 0.5f),
                                focusedLabelColor = DarkBlue, unfocusedLabelColor = DarkBlue.copy(alpha = 0.7f),
                                cursorColor = DarkBlue
                            )
                        )
                        Spacer(Modifier.height(8.dp))

                        // Geofence Type Dropdown
                        ExposedDropdownMenuBox(
                            expanded = geofenceTypeDropdownExpanded,
                            onExpandedChange = { geofenceTypeDropdownExpanded = !geofenceTypeDropdownExpanded }
                        ) {
                            OutlinedTextField(
                                value = geofenceType,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Geofence Type", color = DarkBlue) },
                                modifier = Modifier.menuAnchor().fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = DarkBlue, unfocusedTextColor = DarkBlue.copy(alpha = 0.8f),
                                    focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                                    focusedBorderColor = DarkBlue, unfocusedBorderColor = DarkBlue.copy(alpha = 0.5f),
                                    focusedLabelColor = DarkBlue, unfocusedLabelColor = DarkBlue.copy(alpha = 0.7f),
                                    cursorColor = DarkBlue
                                )
                            )
                            ExposedDropdownMenu(
                                expanded = geofenceTypeDropdownExpanded,
                                onDismissRequest = { geofenceTypeDropdownExpanded = false },
                                modifier = Modifier.background(LightCream)
                            ) {
                                geofenceTypeOptions.forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(option, color = DarkBlue) },
                                        onClick = {
                                            geofenceType = option
                                            geofenceTypeDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        // Incident-specific fields (conditionally visible)
                        if (isIncidentType) {
                            Spacer(Modifier.height(16.dp))
                            Text("Incident Details:", fontWeight = FontWeight.Bold, color = DarkBlue)
                            OutlinedTextField(
                                value = incidentTitle,
                                onValueChange = { incidentTitle = it },
                                label = { Text("Incident Title (e.g., 'Fight - West Field')", color = DarkBlue) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = DarkBlue, unfocusedTextColor = DarkBlue.copy(alpha = 0.8f),
                                    focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                                    focusedBorderColor = DarkBlue, unfocusedBorderColor = DarkBlue.copy(alpha = 0.5f),
                                    focusedLabelColor = DarkBlue, unfocusedLabelColor = DarkBlue.copy(alpha = 0.7f),
                                    cursorColor = DarkBlue
                                ),
                                singleLine = true
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
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = DarkBlue, unfocusedTextColor = DarkBlue.copy(alpha = 0.8f),
                                        focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                                        focusedBorderColor = DarkBlue, unfocusedBorderColor = DarkBlue.copy(alpha = 0.5f),
                                        focusedLabelColor = DarkBlue, unfocusedLabelColor = DarkBlue.copy(alpha = 0.7f),
                                        cursorColor = DarkBlue
                                    )
                                )
                                ExposedDropdownMenu(
                                    expanded = incidentTypeDropdownExpanded,
                                    onDismissRequest = { incidentTypeDropdownExpanded = false },
                                    modifier = Modifier.background(LightCream)
                                ) {
                                    // Ensure groupSettings and customIncidentTypes are not null
                                    viewModel.groupSettings.collectAsStateWithLifecycle().value?.customIncidentTypes?.keys?.forEach { type ->
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
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = DarkBlue, unfocusedTextColor = DarkBlue.copy(alpha = 0.8f),
                                    focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                                    focusedBorderColor = DarkBlue, unfocusedBorderColor = DarkBlue.copy(alpha = 0.5f),
                                    focusedLabelColor = DarkBlue, unfocusedLabelColor = DarkBlue.copy(alpha = 0.7f),
                                    cursorColor = DarkBlue
                                )
                            )
                            // TODO: UI for assigning members to incident (multi-select from active group members)
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { saveGeofenceAndOptionalIncident() },
                        enabled = !isLoading,
                        colors = ButtonDefaults.buttonColors(containerColor = DarkBlue)
                    ) {
                        if (isLoading) CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
                        else Text(if (geofenceZoneId != null) "Save Changes" else "Create Geofence", color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showSaveDialog = false }) {
                        Text("Cancel", color = DarkBlue)
                    }
                },
                containerColor = LightCream
            )
        }
    }

    @Composable
    private fun DrawingModeButton(
        icon: ImageVector,
        label: String,
        selected: Boolean,
        onClick: () -> Unit
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(onClick = onClick)) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (selected) DarkBlue else DarkBlue.copy(alpha = 0.6f),
                modifier = Modifier.size(36.dp)
            )
            Text(
                text = label,
                fontSize = 10.sp,
                color = if (selected) DarkBlue else DarkBlue.copy(alpha = 0.6f),
                fontWeight = FontWeight.Bold
            )
        }
    }

    // Helper to convert vector drawable to BitmapDescriptor
    fun bitmapDescriptorFromVector(context: Context, @DrawableRes vectorResId: Int): BitmapDescriptor {
        val vectorDrawable = ContextCompat.getDrawable(context, vectorResId)
        vectorDrawable?.setBounds(0, 0, vectorDrawable.intrinsicWidth, vectorDrawable.intrinsicHeight)
        val bitmap = Bitmap.createBitmap(
            vectorDrawable?.intrinsicWidth ?: 1,
            vectorDrawable?.intrinsicHeight ?: 1,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        vectorDrawable?.draw(canvas)
        return BitmapDescriptorFactory.fromBitmap(bitmap)
    }
}
