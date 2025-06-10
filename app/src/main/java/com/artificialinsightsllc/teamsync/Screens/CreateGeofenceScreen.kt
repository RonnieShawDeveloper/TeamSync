// In file: app/src/main/java/com/artificialinsightsllc/teamsync/Screens/CreateGeofenceScreen.kt
package com.artificialinsightsllc.teamsync.Screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import com.artificialinsightsllc.teamsync.R
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import com.google.maps.android.compose.Circle
import com.artificialinsightsllc.teamsync.ui.theme.DarkBlue
import kotlinx.coroutines.launch
import android.location.Location
import com.google.maps.android.SphericalUtil

// Enum to manage which shape is currently being edited
private enum class EditingShape {
    NONE, CIRCLE, SQUARE, POLYGON
}

class CreateGeofenceScreen(private val navController: NavHostController) {

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    @OptIn(ExperimentalMaterial3Api::class)
    @SuppressLint("MissingPermission")
    @Composable
    fun Content() {
        val context = LocalContext.current
        val coroutineScope = rememberCoroutineScope()

        // State Management
        var editingShape by remember { mutableStateOf(EditingShape.NONE) }
        var showSaveDialog by remember { mutableStateOf(false) }
        var geofenceName by remember { mutableStateOf("") }
        var hasCenteredMap by remember { mutableStateOf(false) }

        // Shape States
        var circleCenter by remember { mutableStateOf<LatLng?>(null) }
        var circleRadius by remember { mutableStateOf(1000.0) }
        var squarePoints by remember { mutableStateOf<List<LatLng>>(emptyList()) }
        var polygonPoints by remember { mutableStateOf<List<LatLng>>(emptyList()) }

        // Marker States
        val circleCenterMarkerState = rememberMarkerState()
        val circleResizeMarkerState = rememberMarkerState()
        val squareMarkerStates = remember { List(4) { MarkerState() } }
        val polygonMarkerStates = remember { mutableStateListOf<MarkerState>() }

        // Common handle icon
        val handleIcon = remember(context) { bitmapDescriptorFromVector(context, R.drawable.ic_handle_circle) }

        val cameraPositionState = rememberCameraPositionState {
            position = CameraPosition.fromLatLngZoom(LatLng(39.8283, -98.5795), 4f)
        }

        // Helper function to reset all editing states
        fun cancelAndResetEditing() {
            editingShape = EditingShape.NONE
            circleCenter = null
            squarePoints = emptyList()
            polygonPoints = emptyList()
            polygonMarkerStates.clear()
        }

        // ===== Effects for Circle =====
        LaunchedEffect(editingShape) {
            if (editingShape == EditingShape.CIRCLE && circleCenter == null) {
                circleCenter = cameraPositionState.position.target
            }
        }
        LaunchedEffect(circleCenter, circleRadius) {
            if (editingShape == EditingShape.CIRCLE) {
                circleCenter?.let { center ->
                    if(circleCenterMarkerState.position != center) circleCenterMarkerState.position = center
                    val resizeHandlePosition = SphericalUtil.computeOffset(center, circleRadius, 90.0)
                    if(circleResizeMarkerState.position != resizeHandlePosition) circleResizeMarkerState.position = resizeHandlePosition
                }
            }
        }
        LaunchedEffect(circleCenterMarkerState.position) {
            if (editingShape == EditingShape.CIRCLE && circleCenterMarkerState.dragState == DragState.DRAG) {
                circleCenter = circleCenterMarkerState.position
            }
        }
        LaunchedEffect(circleResizeMarkerState.position) {
            if (editingShape == EditingShape.CIRCLE && circleResizeMarkerState.dragState == DragState.DRAG && circleCenter != null) {
                val newRadius = SphericalUtil.computeDistanceBetween(circleCenter!!, circleResizeMarkerState.position)
                if (newRadius > 10) circleRadius = newRadius
            }
        }

        // ===== Effects for Square =====
        LaunchedEffect(editingShape) {
            if (editingShape == EditingShape.SQUARE && squarePoints.isEmpty()) {
                val mapCenter = cameraPositionState.position.target
                val initialSize = 1000.0
                squarePoints = listOf(
                    SphericalUtil.computeOffset(mapCenter, initialSize, -45.0),
                    SphericalUtil.computeOffset(mapCenter, initialSize, -135.0),
                    SphericalUtil.computeOffset(mapCenter, initialSize, 135.0),
                    SphericalUtil.computeOffset(mapCenter, initialSize, 45.0)
                )
            }
        }
        LaunchedEffect(squarePoints) {
            if (editingShape == EditingShape.SQUARE) {
                squarePoints.forEachIndexed { index, latLng ->
                    if(squareMarkerStates[index].position != latLng) squareMarkerStates[index].position = latLng
                }
            }
        }
        squareMarkerStates.forEachIndexed { index, markerState ->
            LaunchedEffect(markerState.position) {
                if (editingShape == EditingShape.SQUARE && markerState.dragState == DragState.DRAG) {
                    val newPoints = squarePoints.toMutableList()
                    newPoints[index] = markerState.position
                    squarePoints = newPoints
                }
            }
        }

        // ===== Effects for Polygon =====
        LaunchedEffect(polygonPoints) {
            if (editingShape == EditingShape.POLYGON) {
                while (polygonMarkerStates.size < polygonPoints.size) {
                    polygonMarkerStates.add(MarkerState())
                }
                while (polygonMarkerStates.size > polygonPoints.size) {
                    polygonMarkerStates.removeLast()
                }
                polygonPoints.forEachIndexed { index, latLng ->
                    if(polygonMarkerStates[index].position != latLng) {
                        polygonMarkerStates[index].position = latLng
                    }
                }
            }
        }
        polygonMarkerStates.forEachIndexed { index, markerState ->
            LaunchedEffect(markerState.position) {
                if (editingShape == EditingShape.POLYGON && markerState.dragState == DragState.DRAG) {
                    val newPoints = polygonPoints.toMutableList()
                    if (index < newPoints.size) {
                        newPoints[index] = markerState.position
                        polygonPoints = newPoints
                    }
                }
            }
        }

        // Location and Permissions
        val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
        val locationPermissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
            onResult = { isGranted ->
                if (isGranted) {
                    fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
                        location?.let {
                            if (!hasCenteredMap) {
                                val userLatLng = LatLng(it.latitude, it.longitude)
                                coroutineScope.launch {
                                    cameraPositionState.animate(CameraUpdateFactory.newCameraPosition(CameraPosition.fromLatLngZoom(userLatLng, 11f)))
                                    hasCenteredMap = true
                                }
                            }
                        }
                    }
                } else {
                    Toast.makeText(context, "Location permission is required to center the map.", Toast.LENGTH_LONG).show()
                }
            }
        )

        LaunchedEffect(Unit) {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        val fabEnabledColor = MaterialTheme.colorScheme.primaryContainer
        val fabDisabledColor = MaterialTheme.colorScheme.surfaceVariant
        val fabEnabledContentColor = MaterialTheme.colorScheme.onPrimaryContainer
        val fabDisabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Create Geofence", color = DarkBlue, fontWeight = FontWeight.Bold) },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White.copy(alpha = 0.7f)),
                    navigationIcon = {
                        IconButton(onClick = {
                            if (editingShape != EditingShape.NONE) {
                                cancelAndResetEditing()
                            } else {
                                navController.popBackStack()
                            }
                        }) { Icon(Icons.Filled.ArrowBack, "Back", tint = DarkBlue) }
                    }
                )
            },
            containerColor = Color.Transparent
        ) { innerPadding ->
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                GoogleMap(
                    modifier = Modifier.fillMaxSize(),
                    cameraPositionState = cameraPositionState,
                    uiSettings = MapUiSettings(zoomControlsEnabled = false, myLocationButtonEnabled = false),
                    properties = MapProperties(isMyLocationEnabled = false),
                    onMapClick = {
                        if (editingShape == EditingShape.POLYGON) {
                            polygonPoints = polygonPoints + it
                        }
                    }
                ) {
                    when (editingShape) {
                        EditingShape.CIRCLE -> {
                            circleCenter?.let { center ->
                                Circle(center = center, radius = circleRadius, strokeColor = DarkBlue, strokeWidth = 8f, fillColor = DarkBlue.copy(alpha = 0.3f))
                                Marker(state = circleCenterMarkerState, title = "Fence Center", draggable = true, icon = handleIcon, anchor = Offset(0.5f, 0.5f))
                                Marker(state = circleResizeMarkerState, title = "Resize Fence", draggable = true, icon = handleIcon, anchor = Offset(0.5f, 0.5f))
                            }
                        }
                        EditingShape.SQUARE -> {
                            if (squarePoints.isNotEmpty()) {
                                Polygon(points = squarePoints, strokeColor = DarkBlue, strokeWidth = 8f, fillColor = DarkBlue.copy(alpha = 0.3f))
                                squareMarkerStates.forEachIndexed { index, state ->
                                    Marker(state = state, title = "Corner ${index + 1}", draggable = true, icon = handleIcon, anchor = Offset(0.5f, 0.5f))
                                }
                            }
                        }
                        EditingShape.POLYGON -> {
                            if (polygonPoints.size >= 2) {
                                Polygon(points = polygonPoints, strokeColor = DarkBlue, strokeWidth = 8f, fillColor = DarkBlue.copy(alpha = 0.3f))
                            }
                            polygonMarkerStates.forEachIndexed { index, state ->
                                Marker(state = state, title = "Point ${index + 1}", draggable = true, icon = handleIcon, anchor = Offset(0.5f, 0.5f))
                            }
                        }
                        EditingShape.NONE -> {}
                    }
                }

                // UI Visibility Logic
                AnimatedVisibility(visible = editingShape == EditingShape.NONE, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Column(modifier = Modifier.align(Alignment.BottomStart).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            // UPDATED: Changed from "CANCEL" to "FINISHED" and removed the disabled "SAVE" button
                            LabeledFab(onClick = { navController.popBackStack() }, label = "FINISHED", icon = { Icon(Icons.Filled.Check, "Finished") }, enabled = true, fabEnabledColor, fabDisabledColor, fabEnabledContentColor, fabDisabledContentColor)
                        }
                        Column(modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            // UPDATED: Removed "SETTINGS" button
                            LabeledFab(onClick = { editingShape = EditingShape.POLYGON }, label = "DRAW POLYGON", icon = { Icon(Icons.Filled.Draw, "Draw") }, enabled = hasCenteredMap, fabEnabledColor, fabDisabledColor, fabEnabledContentColor, fabDisabledContentColor)
                            LabeledFab(onClick = { editingShape = EditingShape.SQUARE }, label = "ADD SQUARE", icon = { Icon(Icons.Filled.CropSquare, "Square") }, enabled = hasCenteredMap, fabEnabledColor, fabDisabledColor, fabEnabledContentColor, fabDisabledContentColor)
                            LabeledFab(onClick = { editingShape = EditingShape.CIRCLE }, label = "ADD CIRCLE", icon = { Icon(Icons.Filled.RadioButtonUnchecked, "Circle") }, enabled = hasCenteredMap, fabEnabledColor, fabDisabledColor, fabEnabledContentColor, fabDisabledContentColor)
                        }
                    }
                }

                AnimatedVisibility(
                    visible = editingShape != EditingShape.NONE,
                    modifier = Modifier.align(Alignment.TopEnd).padding(16.dp),
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    FloatingActionButton(
                        onClick = { cancelAndResetEditing() },
                        containerColor = Color.Red,
                        contentColor = Color.White,
                        shape = CircleShape
                    ) {
                        Icon(Icons.Filled.Close, "Cancel Editing")
                    }
                }

                AnimatedVisibility(visible = editingShape != EditingShape.NONE, modifier = Modifier.align(Alignment.BottomCenter), enter = fadeIn(), exit = fadeOut()) {
                    Button(onClick = { showSaveDialog = true }, modifier = Modifier.padding(bottom = 24.dp), colors = ButtonDefaults.buttonColors(containerColor = DarkBlue), shape = RoundedCornerShape(50)) {
                        Text("SAVE GEOFENCE", modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp), color = Color.White)
                    }
                }

                if (showSaveDialog) {
                    AlertDialog(onDismissRequest = { showSaveDialog = false }, title = { Text("Name Your Geofence") },
                        text = { OutlinedTextField(value = geofenceName, onValueChange = { geofenceName = it }, label = { Text("Geofence Name") }, singleLine = true) },
                        confirmButton = {
                            Button(onClick = {
                                when(editingShape) {
                                    EditingShape.CIRCLE -> Log.d("CreateGeofenceScreen", "Saving CIRCLE: $geofenceName, Center: $circleCenter, Radius: $circleRadius")
                                    EditingShape.SQUARE -> Log.d("CreateGeofenceScreen", "Saving SQUARE: $geofenceName, Points: $squarePoints")
                                    EditingShape.POLYGON -> Log.d("CreateGeofenceScreen", "Saving POLYGON: $geofenceName, Points: $polygonPoints")
                                    else -> {}
                                }
                                showSaveDialog = false
                                cancelAndResetEditing()
                                geofenceName = ""
                            }) { Text("Save") }
                        },
                        dismissButton = { TextButton(onClick = { showSaveDialog = false }) { Text("Cancel") } }
                    )
                }
            }
        }
    }

    @Composable
    private fun LabeledFab(onClick: () -> Unit, label: String, icon: @Composable () -> Unit, enabled: Boolean, fabEnabledColor: Color, fabDisabledColor: Color, fabEnabledContentColor: Color, fabDisabledContentColor: Color) {
        Box(contentAlignment = Alignment.Center) {
            FloatingActionButton(onClick = { if (enabled) onClick() }, containerColor = if (enabled) fabEnabledColor else fabDisabledColor, contentColor = if (enabled) fabEnabledContentColor else fabDisabledContentColor, shape = CircleShape) { icon() }
            Box(modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp).shadow(2.dp, RoundedCornerShape(8.dp)).background(if (enabled) fabEnabledColor else fabDisabledColor, RoundedCornerShape(8.dp)).padding(horizontal = 0.dp, vertical = 0.dp)) {
                Text(text = label, color = if (enabled) fabEnabledContentColor else fabDisabledContentColor, fontSize = 8.sp, lineHeight = 4.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 1.dp, vertical = 1.dp))
            }
        }
    }

    private fun bitmapDescriptorFromVector(context: Context, @DrawableRes resId: Int): BitmapDescriptor {
        val vectorDrawable = ContextCompat.getDrawable(context, resId) ?: return BitmapDescriptorFactory.defaultMarker()
        vectorDrawable.setBounds(0, 0, vectorDrawable.intrinsicWidth, vectorDrawable.intrinsicHeight)
        val bitmap = Bitmap.createBitmap(vectorDrawable.intrinsicWidth, vectorDrawable.intrinsicHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        vectorDrawable.draw(canvas)
        return BitmapDescriptorFactory.fromBitmap(bitmap)
    }
}
