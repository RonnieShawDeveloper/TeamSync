// In file: app/src/main/java/com/artificialinsightsllc/teamsync/Screens/AddMapMarkerScreen.kt
package com.artificialinsightsllc.teamsync.Screens

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.location.Location
import android.net.Uri
import android.os.Build
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.artificialinsightsllc.teamsync.Models.MapMarker
import com.artificialinsightsllc.teamsync.Models.MapMarkerType
import com.artificialinsightsllc.teamsync.R
import com.artificialinsightsllc.teamsync.Services.FirestoreService
import com.artificialinsightsllc.teamsync.Services.GroupMonitorService
import com.artificialinsightsllc.teamsync.TeamSyncApplication
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.model.LatLng
import id.zelory.compressor.Compressor // For image compression
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage
import com.yalantis.ucrop.UCrop
import kotlinx.coroutines.tasks.await
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class AddMapMarkerScreen(private val navController: NavHostController) {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun Content() {
        val context = LocalContext.current
        val coroutineScope = rememberCoroutineScope()
        val firestoreService = remember { FirestoreService() }
        val firebaseStorage = remember { FirebaseStorage.getInstance() }
        val auth = remember { FirebaseAuth.getInstance() }
        val groupMonitorService = (context.applicationContext as TeamSyncApplication).groupMonitorService

        val activeGroup = groupMonitorService.activeGroup.collectAsStateWithLifecycle().value
        val currentUserId = auth.currentUser?.uid

        var selectedTabIndex by remember { mutableIntStateOf(0) } // 0 for Chat, 1 for Photo
        var chatMessage by remember { mutableStateOf("") }
        var photoMessage by remember { mutableStateOf("") }
        var capturedImageUri by remember { mutableStateOf<Uri?>(null) }
        var isLoading by remember { mutableStateOf(false) }
        var showScreen by remember { mutableStateOf(false) } // For animation visibility

        var showPermissionRationaleDialog by remember { mutableStateOf(false) }
        var hasCameraPermission by remember {
            mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
        }
        var hasLocationPermission by remember {
            mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
        }

        var currentLatLng by remember { mutableStateOf<LatLng?>(null) }
        var currentBearing by remember { mutableStateOf<Float?>(null) }

        val fusedLocationClient: FusedLocationProviderClient = remember {
            LocationServices.getFusedLocationProviderClient(context)
        }

        val locationCallback = remember {
            object : LocationCallback() {
                override fun onLocationResult(locationResult: LocationResult) {
                    locationResult.lastLocation?.let { location ->
                        currentLatLng = LatLng(location.latitude, location.longitude)
                        currentBearing = if (location.hasBearing()) location.bearing else null
                        Log.d("AddMapMarkerScreen", "Location captured for marker: ${currentLatLng?.latitude}, ${currentLatLng?.longitude}, Bearing: $currentBearing")
                    }
                }
            }
        }

        val cropResultLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val resultUri = UCrop.getOutput(result.data!!)
                capturedImageUri = resultUri
                Log.d("AddMapMarkerScreen", "Image cropped and captured: $capturedImageUri")
            } else if (result.resultCode == UCrop.RESULT_ERROR) {
                val cropError = UCrop.getError(result.data!!)
                Log.e("AddMapMarkerScreen", "UCrop error: $cropError")
                Toast.makeText(context, "Image cropping failed.", Toast.LENGTH_SHORT).show()
            }
        }

        val takePhotoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            if (success && capturedImageUri != null) {
                // Photo taken, now launch uCrop for square cropping and compression
                val destinationUri = Uri.fromFile(File(context.cacheDir, "cropped_marker_image_${UUID.randomUUID()}.jpg"))
                val options = UCrop.Options().apply {
                    setCompressionQuality(70) // Compress to 70%
                    setHideBottomControls(false) // Show controls for user to adjust if needed
                    setFreeStyleCropEnabled(false) // Disable free style cropping
                    setToolbarTitle("Crop Photo Marker")
                    setToolbarColor(Color(0xFF0D47A1).toArgb()) // Using hardcoded DarkBlue
                    setStatusBarColor(Color(0xFF0D47A1).toArgb()) // Using hardcoded DarkBlue
                    setToolbarWidgetColor(Color.White.toArgb())
                }
                val intent = UCrop.of(capturedImageUri!!, destinationUri)
                    .withAspectRatio(1f, 1f) // Applied here directly on UCrop instance
                    .withOptions(options)
                    .getIntent(context)
                cropResultLauncher.launch(intent)
            } else {
                Toast.makeText(context, "Failed to capture photo.", Toast.LENGTH_SHORT).show()
                capturedImageUri = null // Clear URI if capture failed
            }
        }

        val cameraPermissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            hasCameraPermission = isGranted
            if (isGranted) {
                // If camera permission granted, launch camera
                launchCamera(context, takePhotoLauncher) // Corrected call
            } else {
                Toast.makeText(context, "Camera permission is required to take photos.", Toast.LENGTH_SHORT).show()
            }
        }

        val locationPermissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            hasLocationPermission = isGranted
            if (isGranted) {
                // If location permission granted, start location updates
                startLocationUpdates(fusedLocationClient, locationCallback)
            } else {
                Toast.makeText(context, "Location permission is required to geotag photos.", Toast.LENGTH_SHORT).show()
                currentLatLng = null
                currentBearing = null
            }
        }






        // Lifecycle observer for location updates
        val lifecycleOwner = LocalLifecycleOwner.current
        DisposableEffect(lifecycleOwner, hasLocationPermission) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_START) {
                    if (hasLocationPermission) {
                        startLocationUpdates(fusedLocationClient, locationCallback)
                    }
                } else if (event == Lifecycle.Event.ON_STOP) {
                    stopLocationUpdates(fusedLocationClient, locationCallback)
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
                stopLocationUpdates(fusedLocationClient, locationCallback)
            }
        }

        LaunchedEffect(Unit) {
            showScreen = true // Trigger slide-in animation
            // Request permissions on screen entry if not already granted
            if (!hasCameraPermission) {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
            if (!hasLocationPermission) {
                locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            } else {
                // If permission already granted, ensure location updates start
                startLocationUpdates(fusedLocationClient, locationCallback)
            }
        }

        AnimatedVisibility(
            visible = showScreen,
            enter = slideInHorizontally(animationSpec = tween(durationMillis = 500, delayMillis = 50)) { fullWidth ->
                -fullWidth
            } + scaleIn(animationSpec = tween(500, delayMillis = 50), initialScale = 0.8f) + fadeIn(animationSpec = tween(500, delayMillis = 50)),
            exit = slideOutHorizontally(animationSpec = tween(durationMillis = 500)) { fullWidth ->
                -fullWidth
            } + scaleOut(animationSpec = tween(500), targetScale = 0.8f) + fadeOut(animationSpec = tween(500))
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Image(
                    painter = painterResource(id = R.drawable.background1),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(WindowInsets.systemBars.asPaddingValues())
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Start
                    ) {
                        IconButton(onClick = {
                            coroutineScope.launch {
                                showScreen = false // Trigger slide-out animation
                                delay(550) // Wait for animation to complete
                                navController.popBackStack()
                            }
                        }) {
                            Icon(Icons.Filled.ArrowBack, "Back to Map", tint = Color(0xFF0D47A1)) // Using hardcoded DarkBlue
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Add Map Marker",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF0D47A1), // Using hardcoded DarkBlue
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(48.dp))
                    }

                    Spacer(Modifier.height(16.dp))

                    TabRow(selectedTabIndex = selectedTabIndex) {
                        Tab(
                            selected = selectedTabIndex == 0,
                            onClick = { selectedTabIndex = 0 },
                            text = { Text("Chat Marker", color = Color(0xFF0D47A1)) } // Using hardcoded DarkBlue
                        )
                        Tab(
                            selected = selectedTabIndex == 1,
                            onClick = { selectedTabIndex = 1 },
                            text = { Text("Photo Marker", color = Color(0xFF0D47A1)) } // Using hardcoded DarkBlue
                        )
                    }

                    Spacer(Modifier.height(16.dp))

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.9f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            if (selectedTabIndex == 0) { // Chat Marker Tab
                                OutlinedTextField(
                                    value = chatMessage,
                                    onValueChange = { if (it.length <= 160) chatMessage = it },
                                    label = { Text("Enter message (max 160 chars)") },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = Color(0xFF0D47A1), unfocusedTextColor = Color(0xFF0D47A1).copy(alpha = 0.8f),
                                        focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                                        focusedBorderColor = Color(0xFF0D47A1), unfocusedBorderColor = Color(0xFF0D47A1).copy(alpha = 0.5f),
                                        focusedLabelColor = Color(0xFF0D47A1), unfocusedLabelColor = Color(0xFF0D47A1).copy(alpha = 0.7f),
                                        cursorColor = Color(0xFF0D47A1)
                                    )
                                )
                                Text(
                                    text = "${chatMessage.length}/160",
                                    modifier = Modifier.align(Alignment.End),
                                    color = Color.Gray,
                                    fontSize = 12.sp
                                )
                            } else { // Photo Marker Tab
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color.LightGray),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (capturedImageUri != null) {
                                        Image(
                                            bitmap = BitmapFactory.decodeStream(context.contentResolver.openInputStream(capturedImageUri!!)).asImageBitmap(),
                                            contentDescription = "Captured Photo",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Image(
                                            painter = painterResource(id = R.drawable.no_image),
                                            contentDescription = "No Image Placeholder",
                                            modifier = Modifier.size(120.dp)
                                        )
                                    }
                                    FloatingActionButton(
                                        onClick = {
                                            if (hasCameraPermission) {
                                                launchCamera(context, takePhotoLauncher)
                                            } else {
                                                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                            }
                                        },
                                        modifier = Modifier.align(Alignment.Center)
                                    ) {
                                        Icon(Icons.Filled.CameraAlt, "Take Photo")
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = photoMessage,
                                    onValueChange = { if (it.length <= 160) photoMessage = it },
                                    label = { Text("Enter message (max 160 chars)") },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = Color(0xFF0D47A1), unfocusedTextColor = Color(0xFF0D47A1).copy(alpha = 0.8f),
                                        focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                                        focusedBorderColor = Color(0xFF0D47A1), unfocusedBorderColor = Color(0xFF0D47A1).copy(alpha = 0.5f),
                                        focusedLabelColor = Color(0xFF0D47A1), unfocusedLabelColor = Color(0xFF0D47A1).copy(alpha = 0.7f),
                                        cursorColor = Color(0xFF0D47A1)
                                    )
                                )
                                Text(
                                    text = "${photoMessage.length}/160",
                                    modifier = Modifier.align(Alignment.End),
                                    color = Color.Gray,
                                    fontSize = 12.sp
                                )
                            }

                            Spacer(Modifier.height(16.dp))

                            Button(
                                onClick = {
                                    if (isLoading) return@Button // Prevent double-click
                                    isLoading = true

                                    val groupId = activeGroup?.groupID
                                    val userId = currentUserId

                                    if (groupId == null || userId == null) {
                                        Toast.makeText(context, "Error: Not in an active group or user not logged in.", Toast.LENGTH_LONG).show()
                                        isLoading = false
                                        return@Button
                                    }

                                    coroutineScope.launch {
                                        try {
                                            val newMarkerId = UUID.randomUUID().toString()
                                            val markerToSave: MapMarker

                                            if (selectedTabIndex == 0) { // Chat Marker
                                                if (chatMessage.isBlank()) {
                                                    Toast.makeText(context, "Chat message cannot be empty.", Toast.LENGTH_SHORT).show()
                                                    isLoading = false
                                                    return@launch
                                                }
                                                markerToSave = MapMarker(
                                                    id = newMarkerId,
                                                    groupId = groupId,
                                                    userId = userId,
                                                    markerType = MapMarkerType.CHAT,
                                                    message = chatMessage,
                                                    latitude = currentLatLng?.latitude ?: 0.0,
                                                    longitude = currentLatLng?.longitude ?: 0.0,
                                                    timestamp = System.currentTimeMillis()
                                                )
                                                firestoreService.addMapMarker(markerToSave).onSuccess {
                                                    Toast.makeText(context, "Chat marker posted!", Toast.LENGTH_SHORT).show()
                                                    navController.popBackStack()
                                                }.onFailure { e ->
                                                    Toast.makeText(context, "Failed to post chat marker: ${e.message}", Toast.LENGTH_LONG).show()
                                                }

                                            } else { // Photo Marker
                                                if (capturedImageUri == null) {
                                                    Toast.makeText(context, "Please take a photo first.", Toast.LENGTH_SHORT).show()
                                                    isLoading = false
                                                    return@launch
                                                }
                                                if (currentLatLng == null) {
                                                    Toast.makeText(context, "Waiting for location data. Please try again.", Toast.LENGTH_SHORT).show()
                                                    isLoading = false
                                                    return@launch
                                                }

                                                // Upload photo to Firebase Storage
                                                val photoRef = firebaseStorage.reference.child("map_markers/${groupId}/${newMarkerId}.jpg")
                                                val uploadResult = photoRef.putFile(capturedImageUri!!).await()
                                                val photoUrl = uploadResult.storage.downloadUrl.await().toString()

                                                markerToSave = MapMarker(
                                                    id = newMarkerId,
                                                    groupId = groupId,
                                                    userId = userId,
                                                    markerType = MapMarkerType.PHOTO,
                                                    message = photoMessage,
                                                    photoUrl = photoUrl,
                                                    latitude = currentLatLng!!.latitude,
                                                    longitude = currentLatLng!!.longitude,
                                                    timestamp = System.currentTimeMillis(),
                                                    cameraBearing = currentBearing
                                                )
                                                firestoreService.addMapMarker(markerToSave).onSuccess {
                                                    Toast.makeText(context, "Photo marker posted!", Toast.LENGTH_SHORT).show()
                                                    navController.popBackStack()
                                                }.onFailure { e ->
                                                    Toast.makeText(context, "Failed to post photo marker: ${e.message}", Toast.LENGTH_LONG).show()
                                                }
                                            }
                                        } catch (e: Exception) {
                                            Log.e("AddMapMarkerScreen", "Error posting marker: ${e.message}", e)
                                            Toast.makeText(context, "Error posting marker: ${e.message}", Toast.LENGTH_LONG).show()
                                        } finally {
                                            isLoading = false
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth(0.7f)
                                    .height(50.dp),
                                shape = RoundedCornerShape(24.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D47A1)), // Using hardcoded DarkBlue
                                enabled = !isLoading
                            ) {
                                if (isLoading) {
                                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                                } else {
                                    Text("Post to Map", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates(
        fusedLocationClient: FusedLocationProviderClient,
        locationCallback: LocationCallback
    ) {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L) // Request updates every second
            .setMinUpdateIntervalMillis(500L)
            .build()

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
            Log.d("AddMapMarkerScreen", "Started location updates for marker creation.")
        } catch (e: SecurityException) {
            Log.e("AddMapMarkerScreen", "Location permission denied for marker creation: ${e.message}")
        }
    }

    private fun stopLocationUpdates(
        fusedLocationClient: FusedLocationProviderClient,
        locationCallback: LocationCallback
    ) {
        fusedLocationClient.removeLocationUpdates(locationCallback)
        Log.d("AddMapMarkerScreen", "Stopped location updates for marker creation.")
    }

    private fun launchCamera(
        context: Context,
        takePhotoLauncher: ActivityResultLauncher<Uri> // Parameter added here
    ) {
        val photoFile = File(context.cacheDir, "temp_marker_photo_${UUID.randomUUID()}.jpg")
        val photoUri = FileProvider.getUriForFile(context, "${context.packageName}.provider", photoFile)
        takePhotoLauncher.launch(photoUri)
    }
}
