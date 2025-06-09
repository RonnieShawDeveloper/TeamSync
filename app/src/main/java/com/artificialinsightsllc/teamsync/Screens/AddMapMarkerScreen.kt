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
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage
import com.yalantis.ucrop.UCrop // RE-INTRODUCING UCROP
import kotlinx.coroutines.tasks.await
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import android.provider.MediaStore // Import MediaStore for ACTION_IMAGE_CAPTURE Intent


import androidx.compose.runtime.MutableState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import java.io.IOException
import coil.compose.rememberAsyncImagePainter
// Removed TimeoutCancellationException and withTimeout as they are not in SignupScreen's pattern

class AddMapMarkerScreen(private val navController: NavHostController) {

    // Define a Saver for Uri
    private val UriSaver = Saver<Uri?, String>(
        save = { it?.toString() },
        restore = { it?.let { Uri.parse(it) } }
    )

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
        // THIS WILL NOW HOLD THE URI SUPPLIED TO CAMERA, THEN THE CROPPED URI
        val capturedImageUriState = rememberSaveable(stateSaver = UriSaver) { mutableStateOf<Uri?>(null) }
        var photoMessage by remember { mutableStateOf("") }
        var isLoading by remember { mutableStateOf(false) }
        var showScreen by remember { mutableStateOf(false) } // For animation visibility

        var showGeneralPermissionRationaleDialog by remember { mutableStateOf(false) }
        var generalPermissionRationaleText by remember { mutableStateOf("") }

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

        // RE-INTRODUCING UCROP LAUNCHER - EXACTLY FROM SIGNUP
        val cropResultLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val croppedUri = UCrop.getOutput(result.data!!)
                capturedImageUriState.value = croppedUri // Set to the cropped URI
                Log.d("AddMapMarkerScreen", "Image cropped and captured: ${capturedImageUriState.value}")
            } else if (result.resultCode == UCrop.RESULT_ERROR) {
                val cropError = UCrop.getError(result.data!!)
                Log.e("AddMapMarkerScreen", "UCrop error: $cropError")
                Toast.makeText(context, "Image cropping failed.", Toast.LENGTH_SHORT).show()
                capturedImageUriState.value = null // Clear URI on crop error
            } else {
                Log.d("AddMapMarkerScreen", "UCrop cancelled or returned unexpected result: ${result.resultCode}")
                capturedImageUriState.value = null // Clear URI on cancellation
            }
            // IMPORTANT: Delete the temporary photo file that the camera wrote to (from cacheDir)
            // This file is the one `capturedImageUriState.value` held *before* UCrop.
            // UCrop saves its output to `destinationUri`, which is a different file.
            // The `capturedImageUriState.value` itself has been updated to the cropped URI,
            // so we need the *original* file from the cacheDir.
            // This is a subtle but important cleanup step.
            val originalCameraTempFile = capturedImageUriState.value?.path?.let { File(it) }
            if (originalCameraTempFile != null && originalCameraTempFile.exists() && originalCameraTempFile.name.startsWith("photo.jpg")) { // Check for "photo.jpg" name for safety
                originalCameraTempFile.delete()
                Log.d("AddMapMarkerScreen", "Deleted original camera temp file: ${originalCameraTempFile.absolutePath}")
            }
        }


        // RE-INTRODUCING TakePicture LAUNCHER - EXACTLY FROM SIGNUP
        val takePhotoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            // capturedImageUriState.value holds the URI we supplied to the camera
            if (success && capturedImageUriState.value != null) {
                Log.d("AddMapMarkerScreen", "Photo captured successfully to: ${capturedImageUriState.value}")
                Log.d("AddMapMarkerScreen", "Location at photo capture: Lat=${currentLatLng?.latitude}, Lon=${currentLatLng?.longitude}, Bearing=${currentBearing}")

                // UCrop destination file (also in cacheDir for exact replication)
                val destinationUri = Uri.fromFile(File(context.cacheDir, "cropped_image.jpg"))
                val options = UCrop.Options().apply {
                    // Set the colors directly since the resources are not defined.
                    // This forces UCrop to add the necessary padding to avoid drawing content behind the system UI.
                    setStatusBarColor(Color.Black.hashCode())

                    // Set a toolbar color that fits the app's theme.
                    setToolbarColor(Color(0xFF0D47A1).hashCode()) // Using your DarkBlue color

                    // Set the color of the active widget controls (e.g., the selected aspect ratio).
                    // Note the corrected method name: setActiveControlsWidgetColor
                    setActiveControlsWidgetColor(Color(0xFF0D47A1).hashCode()) // Using your DarkBlue color

                    // Set the color of the toolbar's title text and icons (e.g., the checkmark).
                    setToolbarWidgetColor(Color.White.hashCode())

                    // Set the title of the cropping screen.
                    setToolbarTitle("Crop Photo")
                }


                // Ensure the parent directory for the cropped image exists
                destinationUri.path?.let { path -> File(path).parentFile?.mkdirs() }

                val intent = UCrop.of(capturedImageUriState.value!!, destinationUri)
                    .withAspectRatio(1f, 1f)
                    .withMaxResultSize(1024, 1024)
                    .withOptions(options)
                    .getIntent(context)

                cropResultLauncher.launch(intent)

            } else {
                Toast.makeText(context, "Photo capture was cancelled or failed.", Toast.LENGTH_SHORT).show()
                // If capture was cancelled, clear the URI state and delete the original temp file
                capturedImageUriState.value?.path?.let { File(it).delete() }
                capturedImageUriState.value = null
            }
        }

        val cameraPermissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            hasCameraPermission = isGranted
            if (isGranted) {
                // Prepare a URI for the camera to write the full-size image to
                // EXACTLY as in SignupScreen: using "photo.jpg" in cacheDir
                val photoFile = File(context.cacheDir, "photo.jpg")
                try {
                    // Ensure the file exists (will overwrite if it does)
                    if (photoFile.exists()) {
                        photoFile.delete() // Delete old file to prevent issues
                    }
                    val fileCreated = photoFile.createNewFile()
                    if (!fileCreated) {
                        Log.e("AddMapMarkerScreen", "Failed to create new file at: ${photoFile.absolutePath}")
                        Toast.makeText(context, "Failed to create photo file.", Toast.LENGTH_LONG).show()
                        capturedImageUriState.value = null
                        return@rememberLauncherForActivityResult
                    }


                    val photoUri = FileProvider.getUriForFile(context, "${context.packageName}.provider", photoFile)
                    capturedImageUriState.value = photoUri // Store this URI for the launcher callback

                    val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                        putExtra(MediaStore.EXTRA_OUTPUT, photoUri)
                        // Add read/write flags, though FileProvider and grantUriPermission are primary
                        addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }

                    // Explicitly grant URI permissions for the camera app
                    val cameraAppPackageName = cameraIntent.resolveActivity(context.packageManager)?.packageName
                    if (cameraAppPackageName != null) {
                        context.grantUriPermission(cameraAppPackageName, photoUri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        Log.d("AddMapMarkerScreen", "Explicitly granted URI permissions to $cameraAppPackageName for $photoUri")
                    } else {
                        Log.w("AddMapMarkerScreen", "Could not resolve camera app package name. Explicit URI permission grant skipped.")
                    }

                    Log.d("AddMapMarkerScreen", "Launching camera intent. Target URI: $photoUri. File exists: ${photoFile.exists()}, size: ${photoFile.length()} (before launch).")
                    takePhotoLauncher.launch(photoUri) // Launch TakePicture with the URI

                } catch (e: IOException) {
                    Log.e("AddMapMarkerScreen", "Failed to create temp file for camera: ${e.message}", e)
                    Toast.makeText(context, "Error setting up camera capture.", Toast.LENGTH_LONG).show()
                    capturedImageUriState.value = null
                } catch (e: Exception) {
                    Log.e("AddMapMarkerScreen", "Unexpected error launching camera: ${e.message}", e)
                    Toast.makeText(context, "Unexpected error setting up camera.", Toast.LENGTH_LONG).show()
                    capturedImageUriState.value = null
                }
            } else {
                Toast.makeText(context, "Camera permission is required to take photos.", Toast.LENGTH_SHORT).show()
            }
        }

        val locationPermissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            hasLocationPermission = isGranted
            if (isGranted) {
                startLocationUpdates(fusedLocationClient, locationCallback)
            } else {
                Toast.makeText(context, "Location permission is required to geotag photos.", Toast.LENGTH_SHORT).show()
                currentLatLng = null
                currentBearing = null
            }
        }

        // Lifecycle observer for location updates
        val lifecycleOwner = LocalLifecycleOwner.current
        DisposableEffect(lifecycleOwner, hasLocationPermission, selectedTabIndex) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_START) {
                    // Only start location updates if the tab is Photo or if currentLatLng is still null (for Chat initial fix)
                    if (hasLocationPermission && (selectedTabIndex == 1 || currentLatLng == null)) {
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
            showScreen = true
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
                                showScreen = false
                                delay(550)
                                navController.popBackStack()
                            }
                        }) {
                            Icon(Icons.Filled.ArrowBack, "Back to Map", tint = Color(0xFF0D47A1))
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Add Map Marker",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF0D47A1),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(48.dp))
                    }

                    Spacer(Modifier.height(16.dp))

                    TabRow(
                        selectedTabIndex = selectedTabIndex,
                        containerColor = Color(0xFF0D47A1)
                    ) {
                        Tab(
                            selected = selectedTabIndex == 0,
                            onClick = {
                                selectedTabIndex = 0
                                if (!hasLocationPermission) {
                                    if (ActivityCompat.shouldShowRequestPermissionRationale(context as Activity, Manifest.permission.ACCESS_FINE_LOCATION)) {
                                        generalPermissionRationaleText = "Location (Precise) is recommended for chat markers to accurately pinpoint their location. Please grant this permission."
                                        showGeneralPermissionRationaleDialog = true
                                    } else {
                                        locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                                    }
                                } else {
                                    startLocationUpdates(fusedLocationClient, locationCallback)
                                }
                            },
                            text = { Text("Chat Marker", color = Color.White) },
                            selectedContentColor = Color.White,
                            unselectedContentColor = Color.White.copy(alpha = 0.7f)
                        )
                        Tab(
                            selected = selectedTabIndex == 1,
                            onClick = {
                                selectedTabIndex = 1
                                if (!hasLocationPermission) {
                                    if (ActivityCompat.shouldShowRequestPermissionRationale(context as Activity, Manifest.permission.ACCESS_FINE_LOCATION)) {
                                        generalPermissionRationaleText = "Location (Precise) is essential for geotagging your photo markers on the map. Please grant this permission."
                                        showGeneralPermissionRationaleDialog = true
                                    } else {
                                        locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                                    }
                                } else {
                                    startLocationUpdates(fusedLocationClient, locationCallback)
                                }
                            },
                            text = { Text("Photo Marker", color = Color.White) },
                            selectedContentColor = Color.White,
                            unselectedContentColor = Color.White.copy(alpha = 0.7f)
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
                                    // Coil's rememberAsyncImagePainter for robust loading
                                    Image(
                                        painter = rememberAsyncImagePainter(
                                            model = capturedImageUriState.value,
                                            error = painterResource(id = R.drawable.no_image) // Fallback on error or null
                                        ),
                                        contentDescription = "Captured Photo Preview",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )

                                    // Conditional visibility for Take Photo FAB and Delete FAB
                                    if (capturedImageUriState.value == null) {
                                        // Show Take Photo FAB if no photo is taken
                                        FloatingActionButton(
                                            onClick = {
                                                if (hasCameraPermission) {
                                                    // Launch TakePicture intent
                                                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA) // Request permission if not already granted, then launch camera
                                                } else {
                                                    if (ActivityCompat.shouldShowRequestPermissionRationale(context as Activity, Manifest.permission.CAMERA)) {
                                                        generalPermissionRationaleText = "Camera access is needed to take a photo for your map marker. Please grant this permission."
                                                        showGeneralPermissionRationaleDialog = true
                                                    } else {
                                                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                                    }
                                                }
                                            },
                                            modifier = Modifier.align(Alignment.Center)
                                        ) {
                                            Icon(Icons.Filled.CameraAlt, "Take Photo")
                                        }
                                    } else {
                                        // Show Delete FAB if a photo is present
                                        FloatingActionButton(
                                            onClick = {
                                                // Delete the temporary file from disk
                                                capturedImageUriState.value?.path?.let { filePath ->
                                                    val fileToDelete = File(filePath)
                                                    if (fileToDelete.exists()) {
                                                        fileToDelete.delete()
                                                        Log.d("AddMapMarkerScreen", "Deleted temporary photo file: $filePath")
                                                    }
                                                }
                                                capturedImageUriState.value = null // Clear the URI state
                                                photoMessage = "" // Clear associated message
                                                Toast.makeText(context, "Photo deleted.", Toast.LENGTH_SHORT).show()
                                            },
                                            modifier = Modifier
                                                .align(Alignment.TopEnd) // Position in top right corner
                                                .padding(8.dp) // Add some padding
                                                .size(40.dp), // Make it smaller
                                            containerColor = MaterialTheme.colorScheme.error // Red color for delete
                                        ) {
                                            Icon(Icons.Filled.Delete, "Delete Photo", tint = Color.White)
                                        }
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                                // Location status text
                                if (currentLatLng == null) {
                                    Text(
                                        text = "Acquiring precise location (for geotagging)...",
                                        color = Color.Gray.copy(alpha = 0.7f),
                                        fontSize = 12.sp,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                } else {
                                    Text(
                                        text = "Location acquired.",
                                        color = Color.Green.copy(alpha = 0.7f),
                                        fontSize = 12.sp,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.fillMaxWidth()
                                    )
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
                                    if (isLoading) return@Button
                                    isLoading = true

                                    val groupId = activeGroup?.groupID
                                    val userId = currentUserId

                                    if (groupId == null || userId == null) {
                                        Toast.makeText(context, "Error: Not in an active group or user not logged in.", Toast.LENGTH_LONG).show()
                                        isLoading = false
                                        return@Button
                                    }
                                    // Consolidated location/photo validation for "Post to Map"
                                    if (selectedTabIndex == 1) { // Photo Marker specific checks
                                        if (capturedImageUriState.value == null) {
                                            Toast.makeText(context, "Please take a photo first.", Toast.LENGTH_SHORT).show()
                                            isLoading = false
                                            return@Button
                                        }
                                        if (currentLatLng == null) {
                                            Toast.makeText(context, "Waiting for precise location data. Please try again.", Toast.LENGTH_LONG).show()
                                            isLoading = false
                                            return@Button
                                        }
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
                                                // Log location for chat marker too for verification
                                                Log.d("AddMapMarkerScreen", "Chat Marker Location: Lat=${currentLatLng?.latitude}, Lon=${currentLatLng?.longitude}")
                                                markerToSave = MapMarker(
                                                    id = newMarkerId,
                                                    groupId = groupId,
                                                    userId = userId,
                                                    markerType = MapMarkerType.CHAT,
                                                    message = chatMessage,
                                                    latitude = currentLatLng?.latitude ?: 0.0, // Use acquired location or default
                                                    longitude = currentLatLng?.longitude ?: 0.0, // Use acquired location or default
                                                    timestamp = System.currentTimeMillis()
                                                )
                                                firestoreService.addMapMarker(markerToSave).onSuccess {
                                                    Toast.makeText(context, "Chat marker posted!", Toast.LENGTH_SHORT).show()
                                                    navController.popBackStack()
                                                }.onFailure { e ->
                                                    Toast.makeText(context, "Failed to post chat marker: ${e.message}", Toast.LENGTH_LONG).show()
                                                }

                                            } else { // Photo Marker
                                                // Log location for photo marker
                                                Log.d("AddMapMarkerScreen", "Photo Marker Location (final check): Lat=${currentLatLng?.latitude}, Lon=${currentLatLng?.longitude}, Bearing=${currentBearing}")

                                                // Upload photo to Firebase Storage
                                                val photoRef = firebaseStorage.reference.child("map_markers/${groupId}/${newMarkerId}.jpg")
                                                // Using capturedImageUriState.value (which now points to the cropped file in cacheDir)
                                                val uploadResult = photoRef.putFile(capturedImageUriState.value!!).await()
                                                val photoUrl = uploadResult.storage.downloadUrl.await().toString()

                                                // Clean up the temporary cropped image file AFTER successful upload
                                                capturedImageUriState.value?.path?.let { File(it).delete() }


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
                                                    cameraBearing = currentBearing // Pass the captured bearing
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
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D47A1)),
                                // Disable button if no photo or no location (for photo marker)
                                enabled = !isLoading && (
                                        (selectedTabIndex == 0 && chatMessage.isNotBlank()) ||
                                                (selectedTabIndex == 1 && capturedImageUriState.value != null && currentLatLng != null)
                                        )
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

        // General Permission Rationale Dialog
        if (showGeneralPermissionRationaleDialog && generalPermissionRationaleText.isNotBlank()) {
            AlertDialog(
                onDismissRequest = { showGeneralPermissionRationaleDialog = false },
                title = { Text("Permission Required") },
                text = { Text(generalPermissionRationaleText) },
                confirmButton = {
                    Button(onClick = {
                        showGeneralPermissionRationaleDialog = false
                        // Determine which launcher to use based on the text/context
                        if (generalPermissionRationaleText.contains("Camera access")) {
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        } else if (generalPermissionRationaleText.contains("Location (Precise)")) {
                            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                        }
                    }) {
                        Text("Continue")
                    }
                },
                dismissButton = {
                    Button(onClick = { showGeneralPermissionRationaleDialog = false }) {
                        Text("Not Now")
                    }
                }
            )
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
}
