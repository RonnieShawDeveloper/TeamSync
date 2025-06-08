// In file: app/src/main/java/com/artificialinsightsllc/teamsync/Screens/UserSettingsScreen.kt
package com.artificialinsightsllc.teamsync.Screens

import android.app.Activity
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.expandVertically // Import expandVertically
import androidx.compose.animation.shrinkVertically // Import shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.navigation.NavHostController
import coil.compose.rememberAsyncImagePainter
import com.artificialinsightsllc.teamsync.Models.UserModel
import com.artificialinsightsllc.teamsync.Navigation.NavRoutes
import com.artificialinsightsllc.teamsync.R
import com.artificialinsightsllc.teamsync.Services.FirestoreService
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage
import com.yalantis.ucrop.UCrop
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.semantics.Role
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Info // For General tab
import androidx.compose.ui.graphics.vector.ImageVector // Import ImageVector
import androidx.core.content.ContextCompat
import com.artificialinsightsllc.teamsync.Helpers.BatteryInfoHelper // Added back BatteryInfoHelper import
import kotlinx.coroutines.delay


// Reusable AboutAppDialog Composable (can be called from LoginScreen or UserSettingsScreen)
@Composable
fun AboutAppDialog(onDismissRequest: () -> Unit) {
    val DarkBlue = Color(0xFF0D47A1)
    AlertDialog(
        onDismissRequest = onDismissRequest,
        containerColor = Color.White.copy(alpha = 0.9f),
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "About TeamSync",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = DarkBlue,
                    textAlign = TextAlign.Center
                )
            }
        },
        text = {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.9f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                ) {
                    Text(
                        text = "TeamSync is a robust Android application designed to keep teams, families, and friends connected through real-time location sharing and streamlined group coordination.\n\n",
                        fontSize = 16.sp,
                        color = DarkBlue
                    )
                    Text(
                        text = "Developed by Artificial Insights, LLC.\n",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = DarkBlue
                    )
                    Text(
                        text = "Development Team Lead: Ronnie Shaw, Certified Android Developer.\n\n",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = DarkBlue
                    )
                    Text(
                        text = "Built natively on Android using Kotlin and Jetpack Compose, TeamSync leverages cutting-edge technologies including Firebase for secure authentication, real-time data synchronization with Firestore, and cloud storage for rich media. Google Maps Platform powers intuitive location tracking and custom map markers.\n\n",
                        fontSize = 16.sp,
                        color = DarkBlue
                    )
                    Text(
                        text = "Our mission is to provide a seamless and reliable platform for enhanced communication, safety, and operational efficiency for any group needing to stay in sync on the go.",
                        fontSize = 16.sp,
                        color = DarkBlue
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismissRequest, colors = ButtonDefaults.buttonColors(containerColor = DarkBlue)) {
                Text("Close", color = Color.White)
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class) // Added OptIn for ExperimentalMaterial3Api
class UserSettingsScreen(private val navController: NavHostController) {

    @OptIn(ExperimentalMaterial3Api::class) // Added OptIn for ExperimentalMaterial3Api
    @Composable
    fun Content() {
        val context = LocalContext.current
        val coroutineScope = rememberCoroutineScope()
        val firestoreService = remember { FirestoreService() }
        val auth = remember { FirebaseAuth.getInstance() }
        val firebaseStorage = remember { FirebaseStorage.getInstance() }

        var currentUserModel by remember { mutableStateOf<UserModel?>(null) }
        var isLoading by remember { mutableStateOf(true) }
        var showScreen by remember { mutableStateOf(false) } // For animation visibility

        // val tabs = listOf("My Profile", "Location & Privacy", "Notifications", "Map & Display", "General") // Tabs removed for accordion

        // NEW: Accordion expanded state management
        val sectionTitles = listOf("My Profile", "Location & Privacy", "Notifications", "Map & Display", "General")
        // NEW: Map to store icons for each section
        val sectionIcons = remember {
            mapOf(
                "My Profile" to Icons.Default.Person,
                "Location & Privacy" to Icons.Default.LocationOn,
                "Notifications" to Icons.Default.Notifications,
                "Map & Display" to Icons.Default.Map,
                "General" to Icons.Default.Info
            )
        }
        var expandedSections by remember { mutableStateOf(sectionTitles.associateWith { it == "My Profile" }.toMutableMap()) } // My Profile expanded by default


        // Profile Tab States (now Profile Section States)
        var firstName by remember { mutableStateOf("") }
        var middleName by remember { mutableStateOf("") }
        var lastName by remember { mutableStateOf("") }
        var displayName by remember { mutableStateOf("") }
        var city by remember { mutableStateOf("") }
        var state by remember { mutableStateOf("") }
        var profilePhotoUri by remember { mutableStateOf<Uri?>(null) }
        var showPhotoOptionDialog by remember { mutableStateOf(false) }
        var tempPhotoUri: Uri? by remember { mutableStateOf(null) } // For camera capture temporary URI

        // Location & Privacy Tab States (now Location & Privacy Section States)
        var shareLiveLocation by remember { mutableStateOf(true) }
        val locationUpdateIntervalOptions = listOf("Device Default", "Real-time (1s)", "10 Seconds", "1 Minute", "5 Minutes")
        val locationUpdateIntervalValues = listOf(null, 1000L, 10000L, 60000L, 300000L) // null for device default
        var selectedLocationUpdateIntervalIndex by remember { mutableIntStateOf(0) } // Default to Device Default
        var locationIntervalDropdownExpanded by remember { mutableStateOf(false) }
        var shareBatteryLevel by remember { mutableStateOf(true) }
        var shareAppStatus by remember { mutableStateOf(true) }

        // Notifications Tab States (now Notifications Section States)
        var receiveGroupChatNotifications by remember { mutableStateOf(true) }
        var receivePrivateChatMessages by remember { mutableStateOf(true) }
        var receiveCriticalLocationAlerts by remember { mutableStateOf(true) }
        var muteNotificationsWhenAppOpen by remember { mutableStateOf(false) }

        // Map & Display Tab States (now Map & Display Section States)
        val mapTypeOptions = listOf("STANDARD", "SATELLITE", "HYBRID", "TERRAIN")
        var selectedMapTypeIndex by remember { mutableIntStateOf(0) }
        var mapTypeDropdownExpanded by remember { mutableStateOf(false) }

        val unitOptions = listOf("IMPERIAL (miles/mph)", "METRIC (km/kph)", "NAUTICAL (nm/knots)")
        var selectedUnitIndex by remember { mutableIntStateOf(0) }
        var unitDropdownExpanded by remember { mutableStateOf(false) }

        var showMyOwnMarker by remember { mutableStateOf(true) }
        var showMemberNamesOnMap by remember { mutableStateOf(true) }

        // General Tab States (now General Section States)
        var showAboutDialog by remember { mutableStateOf(false) }

        // Generic Alert Dialog state
        var showAlertDialog by remember { mutableStateOf(false) }
        var alertDialogTitle by remember { mutableStateOf("") }
        var alertDialogMessage by remember { mutableStateOf("") }

        // Colors
        val DarkBlue = Color(0xFF0D47A1)
        val LightCream = Color(0xFFFFFDD0)


        // UCrop launcher setup
        val cropResultLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                profilePhotoUri = UCrop.getOutput(result.data!!)
                Log.d("UserSettings", "Cropped image URI: $profilePhotoUri")
            } else if (result.resultCode == UCrop.RESULT_ERROR) {
                val cropError = UCrop.getError(result.data!!)
                Log.e("UserSettings", "UCrop error: $cropError")
                Toast.makeText(context, "Image cropping failed.", Toast.LENGTH_SHORT).show()
            }
        }

        // Image picker launcher
        val imagePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let {
                val destinationUri = Uri.fromFile(File(context.cacheDir, "cropped_profile_pic.jpg"))
                val intent = UCrop.of(it, destinationUri)
                    .withAspectRatio(1f, 1f)
                    .withMaxResultSize(512, 512) // Ensure reasonable size
                    .getIntent(context)
                cropResultLauncher.launch(intent)
            }
        }

        // Camera launcher
        val takePhotoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            if (success && tempPhotoUri != null) {
                val destinationUri = Uri.fromFile(File(context.cacheDir, "cropped_profile_pic.jpg"))
                val options = UCrop.Options().apply {
                    setCircleDimmedLayer(true) // For circular crop
                    setShowCropFrame(false)
                    setShowCropGrid(false)
                    setToolbarTitle("Crop Profile Photo")
                    setToolbarColor(DarkBlue.toArgb())
                    setStatusBarColor(DarkBlue.toArgb())
                    setToolbarWidgetColor(Color.White.toArgb())
                }

                val intent = UCrop.of(tempPhotoUri!!, destinationUri)
                    .withAspectRatio(1f, 1f)
                    .withMaxResultSize(512, 512)
                    .withOptions(options)
                    .getIntent(context)
                cropResultLauncher.launch(intent)
            } else {
                Toast.makeText(context, "Failed to capture photo.", Toast.LENGTH_SHORT).show()
            }
        }

        // Request camera permission launcher
        val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                val photoFile = File(context.cacheDir, "temp_profile_pic.jpg")
                tempPhotoUri = FileProvider.getUriForFile(context, "${context.packageName}.provider", photoFile)
                takePhotoLauncher.launch(tempPhotoUri!!)
            } else {
                Toast.makeText(context, "Camera permission is required to take a new profile photo.", Toast.LENGTH_SHORT).show()
            }
        }

        LaunchedEffect(Unit) {
            isLoading = true
            showScreen = true // Enable animation visibility

            val userId = auth.currentUser?.uid
            if (userId != null) {
                firestoreService.getUserProfile(userId).onSuccess { user ->
                    currentUserModel = user
                    firstName = user.firstName
                    middleName = user.middleName
                    lastName = user.lastName
                    displayName = user.displayName
                    city = user.city
                    state = user.state
                    profilePhotoUri = if (user.profilePhotoUrl.isNotEmpty()) Uri.parse(user.profilePhotoUrl) else null

                    // Populate settings from UserModel
                    shareLiveLocation = user.shareLiveLocation
                    selectedLocationUpdateIntervalIndex = locationUpdateIntervalValues.indexOf(user.personalLocationUpdateIntervalMillis).coerceAtLeast(0)
                    shareBatteryLevel = user.shareBatteryLevel
                    shareAppStatus = user.shareAppStatus

                    selectedMapTypeIndex = mapTypeOptions.indexOf(user.defaultMapType).coerceAtLeast(0)
                    selectedUnitIndex = unitOptions.indexOfFirst { it.startsWith(user.unitsOfMeasurement) }.coerceAtLeast(0)
                    showMyOwnMarker = user.showMyOwnMarker
                    showMemberNamesOnMap = user.showMemberNamesOnMap

                    receiveGroupChatNotifications = user.receiveGroupChatNotifications
                    receivePrivateChatMessages = user.receivePrivateChatMessages
                    receiveCriticalLocationAlerts = user.valReceiveCriticalLocationAlerts
                    muteNotificationsWhenAppOpen = user.muteNotificationsWhenAppOpen

                }.onFailure { e ->
                    alertDialogTitle = "Error Loading Profile"
                    alertDialogMessage = "Failed to load your profile: ${e.message}"
                    showAlertDialog = true
                }
            } else {
                alertDialogTitle = "Not Logged In"
                alertDialogMessage = "You must be logged in to view settings."
                showAlertDialog = true
                navController.popBackStack() // Go back if not logged in
            }
            isLoading = false // End loading indicator
        }


        AnimatedVisibility(
            visible = showScreen,
            enter = slideInHorizontally(animationSpec = tween(durationMillis = 500, delayMillis = 50)) { fullWidth ->
                fullWidth // Start from right
            } + scaleIn(animationSpec = tween(500, delayMillis = 50), initialScale = 0.8f) + fadeIn(animationSpec = tween(500, delayMillis = 50)),
            exit = slideOutHorizontally(animationSpec = tween(durationMillis = 500)) { fullWidth ->
                fullWidth // Exit to right
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
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to Main", tint = DarkBlue)
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "User Settings",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = DarkBlue,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(48.dp))
                    }

                    Spacer(Modifier.height(16.dp))

                    if (isLoading) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = DarkBlue)
                        }
                    } else {
                        // REPLACED Tabs with Accordion Items
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState()), // Make the outer column scrollable
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            sectionTitles.forEach { title ->
                                AccordionItem(
                                    title = title,
                                    icon = sectionIcons[title] ?: Icons.Default.Info, // Pass icon, fallback to Info
                                    expanded = expandedSections[title] ?: false,
                                    onToggle = {
                                        expandedSections = expandedSections.toMutableMap().apply {
                                            // Close all other sections, then toggle the clicked one
                                            keys.forEach { key -> put(key, false) } // Collapse all
                                            this[title] = !(this[title] ?: false) // Toggle clicked one
                                        }
                                    }
                                ) {
                                    // Content for each accordion item
                                    when (title) {
                                        "My Profile" -> MyProfileTab(
                                            firstName = firstName, onFirstNameChange = { firstName = it },
                                            middleName = middleName, onMiddleNameChange = { middleName = it },
                                            lastName = lastName, onLastNameChange = { lastName = it },
                                            displayName = displayName, onDisplayNameChange = { displayName = it },
                                            city = city, onCityChange = { city = it },
                                            state = state, onStateChange = { state = it },
                                            profilePhotoUri = profilePhotoUri,
                                            onSelectPhoto = { showPhotoOptionDialog = true }
                                        )
                                        "Location & Privacy" -> LocationPrivacyTab(
                                            shareLiveLocation = shareLiveLocation, onShareLiveLocationChange = { shareLiveLocation = it },
                                            selectedIntervalIndex = selectedLocationUpdateIntervalIndex, onIntervalIndexChange = { selectedLocationUpdateIntervalIndex = it },
                                            intervalOptions = locationUpdateIntervalOptions, intervalValues = locationUpdateIntervalValues,
                                            intervalDropdownExpanded = locationIntervalDropdownExpanded, onIntervalDropdownExpandedChange = { locationIntervalDropdownExpanded = it },
                                            shareBatteryLevel = shareBatteryLevel, onShareBatteryLevelChange = { shareBatteryLevel = it },
                                            shareAppStatus = shareAppStatus, onShareAppStatusChange = { shareAppStatus = it },
                                            onViewTravelReports = {
                                                auth.currentUser?.uid?.let { userId ->
                                                    coroutineScope.launch {
                                                        // Ensure BatteryInfoHelper is not trying to access live flows in this part.
                                                        // This navigation will eventually cause a recomposition of LocationPrivacyTab.
                                                        // The battery info is fetched via `remember` on composition, so it's fine.
                                                        showScreen = false // Hide settings screen immediately
                                                        delay(50) // Small delay for effect
                                                        navController.navigate(NavRoutes.TRAVEL_REPORT.replace("{userId}", userId))
                                                    }
                                                }
                                            },
                                            onClearLocationHistory = {
                                                // TODO: Implement clearing location history (alert dialog for confirmation)
                                                Toast.makeText(context, "Clear history functionality coming soon!", Toast.LENGTH_SHORT).show()
                                            }
                                        )
                                        "Notifications" -> NotificationsTab(
                                            receiveGroupChatNotifications = receiveGroupChatNotifications, onReceiveGroupChatNotificationsChange = { receiveGroupChatNotifications = it },
                                            receivePrivateChatMessages = receivePrivateChatMessages, onReceivePrivateChatMessagesChange = { receivePrivateChatMessages = it },
                                            receiveCriticalLocationAlerts = receiveCriticalLocationAlerts, onReceiveCriticalLocationAlertsChange = { receiveCriticalLocationAlerts = it },
                                            muteNotificationsWhenAppOpen = muteNotificationsWhenAppOpen, onMuteNotificationsWhenAppOpenChange = { muteNotificationsWhenAppOpen = it }
                                        )
                                        "Map & Display" -> MapDisplayTab(
                                            selectedMapTypeIndex = selectedMapTypeIndex, onMapTypeIndexChange = { selectedMapTypeIndex = it },
                                            mapTypeOptions = mapTypeOptions, mapTypeDropdownExpanded = mapTypeDropdownExpanded, onMapTypeDropdownExpandedChange = { mapTypeDropdownExpanded = it },
                                            selectedUnitIndex = selectedUnitIndex, onUnitIndexChange = { selectedUnitIndex = it },
                                            unitOptions = unitOptions, unitDropdownExpanded = unitDropdownExpanded, onUnitDropdownExpandedChange = { unitDropdownExpanded = it },
                                            showMyOwnMarker = showMyOwnMarker, onShowMyOwnMarkerChange = { showMyOwnMarker = it },
                                            showMemberNamesOnMap = showMemberNamesOnMap, onShowMemberNamesOnMapChange = { showMemberNamesOnMap = it }
                                        )
                                        "General" -> GeneralTab(
                                            onAboutAppClick = { showAboutDialog = true },
                                            onLogoutClick = {
                                                auth.signOut()
                                                navController.navigate(NavRoutes.LOGIN) {
                                                    popUpTo(NavRoutes.MAIN) { inclusive = true } // Clear backstack up to MAIN
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(24.dp))
                            // Save Settings Button
                            Button(
                                onClick = {
                                    coroutineScope.launch {
                                        isLoading = true
                                        val userId = auth.currentUser?.uid
                                        if (userId != null) {
                                            var photoUrl: String? = currentUserModel?.profilePhotoUrl
                                            if (profilePhotoUri != null && profilePhotoUri.toString() != currentUserModel?.profilePhotoUrl) {
                                                // Only upload if URI has changed or it's a new URI
                                                try {
                                                    val ref = firebaseStorage.reference.child("profilePhotos/$userId.jpg")
                                                    ref.putFile(profilePhotoUri!!).await()
                                                    photoUrl = ref.downloadUrl.await().toString()
                                                    Log.d("UserSettings", "Profile photo uploaded: $photoUrl")
                                                } catch (e: Exception) {
                                                    Log.e("UserSettings", "Failed to upload profile photo: ${e.message}")
                                                    Toast.makeText(context, "Failed to upload profile photo: ${e.message}", Toast.LENGTH_LONG).show()
                                                    isLoading = false
                                                    return@launch
                                                }
                                            } else if (profilePhotoUri == null && currentUserModel?.profilePhotoUrl?.isNotEmpty() == true) {
                                                // If photo was removed (set to null), clear it in storage if needed.
                                                // For now, just set photoUrl to empty string.
                                                photoUrl = ""
                                            }


                                            val updatedUser = currentUserModel?.copy(
                                                firstName = firstName,
                                                middleName = middleName,
                                                lastName = lastName,
                                                displayName = displayName,
                                                city = city,
                                                state = state,
                                                profilePhotoUrl = photoUrl ?: "", // Save potentially new photo URL

                                                shareLiveLocation = shareLiveLocation,
                                                personalLocationUpdateIntervalMillis = locationUpdateIntervalValues[selectedLocationUpdateIntervalIndex],
                                                shareBatteryLevel = shareBatteryLevel,
                                                shareAppStatus = shareAppStatus,

                                                defaultMapType = mapTypeOptions[selectedMapTypeIndex],
                                                unitsOfMeasurement = unitOptions[selectedUnitIndex].split(" ")[0], // Extract "IMPERIAL", "METRIC", "NAUTICAL"
                                                showMyOwnMarker = showMyOwnMarker,
                                                showMemberNamesOnMap = showMemberNamesOnMap,

                                                receiveGroupChatNotifications = receiveGroupChatNotifications,
                                                receivePrivateChatMessages = receivePrivateChatMessages,
                                                valReceiveCriticalLocationAlerts = receiveCriticalLocationAlerts,
                                                muteNotificationsWhenAppOpen = muteNotificationsWhenAppOpen
                                            )

                                            if (updatedUser != null) {
                                                firestoreService.saveUserProfile(updatedUser).onSuccess {
                                                    currentUserModel = updatedUser // Update local state
                                                    Toast.makeText(context, "Settings saved successfully!", Toast.LENGTH_SHORT).show()
                                                }.onFailure { e ->
                                                    alertDialogTitle = "Error Saving Settings"
                                                    alertDialogMessage = "Failed to save settings: ${e.message}"
                                                    showAlertDialog = true
                                                }
                                            }
                                        }
                                        isLoading = false
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth(0.7f)
                                    .height(50.dp),
                                shape = RoundedCornerShape(24.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = DarkBlue)
                            ) {
                                Text("Save Settings", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.height(32.dp))
                        }
                    }
                }
            }
        }

        // Photo Option Dialog
        if (showPhotoOptionDialog) {
            AlertDialog(
                onDismissRequest = { showPhotoOptionDialog = false },
                title = { Text("Set Profile Photo", color = DarkBlue) },
                text = { Text("Would you like to take a new photo or select one from your gallery?", color = DarkBlue) },
                confirmButton = {
                    TextButton(onClick = {
                        showPhotoOptionDialog = false
                        val permissionGranted = ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED
                        if (permissionGranted) {
                            val photoFile = File(context.cacheDir, "temp_profile_pic.jpg")
                            tempPhotoUri = FileProvider.getUriForFile(context, "${context.packageName}.provider", photoFile)
                            takePhotoLauncher.launch(tempPhotoUri!!)
                        } else {
                            cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA) // Request permission
                        }
                    }) {
                        Text("Take New Photo", color = DarkBlue)
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showPhotoOptionDialog = false
                        imagePickerLauncher.launch("image/*")
                    }) {
                        Text("Upload from Gallery", color = DarkBlue)
                    }
                },
                containerColor = LightCream // Light Cream background for this specific dialog
            )
        }

        // Generic Alert Dialog
        if (showAlertDialog) {
            AlertDialog(
                onDismissRequest = { showAlertDialog = false },
                title = { Text(alertDialogTitle, color = DarkBlue) },
                text = { Text(alertDialogMessage, color = DarkBlue) },
                confirmButton = {
                    Button(onClick = { showAlertDialog = false }, colors = ButtonDefaults.buttonColors(containerColor = DarkBlue)) {
                        Text("OK", color = Color.White)
                    }
                },
                containerColor = LightCream // Light Cream background for generic alerts
            )
        }

        // About This App Dialog
        if (showAboutDialog) {
            AboutAppDialog(onDismissRequest = { showAboutDialog = false })
        }
    }

    @Composable
    fun ProfilePictureSection(profilePhotoUri: Uri?, onSelectPhoto: () -> Unit) {
        val context = LocalContext.current
        val DarkBlue = Color(0xFF0D47A1)

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Image(
                painter = rememberAsyncImagePainter(
                    model = profilePhotoUri,
                    error = painterResource(id = R.drawable.default_profile_pic)
                ),
                contentDescription = "Profile Picture",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(Color.LightGray)
                    .border(2.dp, DarkBlue, CircleShape) // DarkBlue border for profile pic
                    .clickable { onSelectPhoto() }
            )
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onSelectPhoto) {
                Text("Change Profile Photo", color = DarkBlue)
            }
        }
    }


    @Composable
    fun MyProfileTab(
        firstName: String, onFirstNameChange: (String) -> Unit,
        middleName: String, onMiddleNameChange: (String) -> Unit,
        lastName: String, onLastNameChange: (String) -> Unit,
        displayName: String, onDisplayNameChange: (String) -> Unit,
        city: String, onCityChange: (String) -> Unit,
        state: String, onStateChange: (String) -> Unit,
        profilePhotoUri: Uri?,
        onSelectPhoto: () -> Unit
    ) {
        val DarkBlue = Color(0xFF0D47A1)
        val context = LocalContext.current // Added context here for Toast
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.9f))
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = "Personal Information", fontWeight = FontWeight.Bold, color = DarkBlue)
                ProfilePictureSection(profilePhotoUri, onSelectPhoto)
                SettingsTextField("First Name", firstName, onFirstNameChange, DarkBlue)
                SettingsTextField("Middle Name", middleName, onMiddleNameChange, DarkBlue)
                SettingsTextField("Last Name", lastName, onLastNameChange, DarkBlue)
                SettingsTextField("Display Name", displayName, onDisplayNameChange, DarkBlue)
                SettingsTextField("City", city, onCityChange, DarkBlue)
                SettingsTextField("State", state, onStateChange, DarkBlue)
                // Password change will be a separate action/dialog, not directly in a text field
                // Add a button for "Change Password" (TODO: Implement dialog/screen for this)
                Button(onClick = { Toast.makeText(context, "Change password functionality coming soon!", Toast.LENGTH_SHORT).show() },
                    colors = ButtonDefaults.buttonColors(containerColor = DarkBlue)
                ) {
                    Text("Change Password", color = Color.White)
                }
            }
        }
    }

    @Composable
    fun LocationPrivacyTab(
        shareLiveLocation: Boolean, onShareLiveLocationChange: (Boolean) -> Unit,
        selectedIntervalIndex: Int, onIntervalIndexChange: (Int) -> Unit,
        intervalOptions: List<String>, intervalValues: List<Long?>,
        intervalDropdownExpanded: Boolean, onIntervalDropdownExpandedChange: (Boolean) -> Unit,
        shareBatteryLevel: Boolean, onShareBatteryLevelChange: (Boolean) -> Unit,
        shareAppStatus: Boolean, onShareAppStatusChange: (Boolean) -> Unit,
        onViewTravelReports: () -> Unit,
        onClearLocationHistory: () -> Unit
    ) {
        val DarkBlue = Color(0xFF0D47A1)
        val LightCream = Color(0xFFFFFDD0)

        // Fetch battery info snapshot directly here
        val context = LocalContext.current // Correctly capture context in Composable scope
        val (batteryLevel, chargingStatusString) = remember { BatteryInfoHelper.getBatteryInfo(context) } // Use captured context

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.9f))
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = "Location & Privacy", fontWeight = FontWeight.Bold, color = DarkBlue)
                FeatureToggle(text = "Share My Live Location", checked = shareLiveLocation, onCheckedChange = onShareLiveLocationChange, enabled = true, DarkBlue = DarkBlue)

                Column {
                    Text("My Location Update Frequency:", color = DarkBlue, fontWeight = FontWeight.Medium)
                    ExposedDropdownMenuBox(
                        expanded = intervalDropdownExpanded,
                        onExpandedChange = onIntervalDropdownExpandedChange
                    ) {
                        OutlinedTextField(
                            value = intervalOptions[selectedIntervalIndex],
                            onValueChange = {},
                            readOnly = true,
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
                            expanded = intervalDropdownExpanded,
                            onDismissRequest = { onIntervalDropdownExpandedChange(false) },
                            modifier = Modifier.background(LightCream)
                        ) {
                            intervalOptions.forEachIndexed { index, selectionOption ->
                                DropdownMenuItem(
                                    text = { Text(selectionOption, color = DarkBlue) },
                                    onClick = {
                                        onIntervalIndexChange(index)
                                        onIntervalDropdownExpandedChange(false)
                                    }
                                )
                            }
                        }
                    }
                }
                FeatureToggle(text = "Share My Battery Level", checked = shareBatteryLevel, onCheckedChange = onShareBatteryLevelChange, enabled = true, DarkBlue = DarkBlue)

                // NEW: Display current battery information (snapshot)
                Column(modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 4.dp)) {
                    Text(
                        text = "Current: ${batteryLevel ?: "N/A"}%",
                        fontSize = 14.sp,
                        color = DarkBlue.copy(alpha = 0.8f)
                    )
                    Text(
                        text = "Status: ${chargingStatusString ?: "UNKNOWN"}",
                        fontSize = 14.sp,
                        color = DarkBlue.copy(alpha = 0.8f)
                    )
                    Text(
                        text = "This information is used for internal battery management and alerts about your battery to you and Group Management.",
                        fontSize = 12.sp,
                        color = DarkBlue.copy(alpha = 0.7f)
                    )
                }
                Spacer(Modifier.height(8.dp)) // Add a small spacer after the battery info

                FeatureToggle(text = "Share My App Status (Online/Offline)", checked = shareAppStatus, onCheckedChange = onShareAppStatusChange, enabled = true, DarkBlue = DarkBlue)

                Spacer(Modifier.height(16.dp))
                Button(onClick = onViewTravelReports,
                    colors = ButtonDefaults.buttonColors(containerColor = DarkBlue)
                ) {
                    Text("View My Travel Reports", color = Color.White)
                }
                Button(onClick = onClearLocationHistory,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Clear My Personal Location History", color = Color.White)
                }
            }
        }
    }

    @Composable
    fun NotificationsTab(
        receiveGroupChatNotifications: Boolean, onReceiveGroupChatNotificationsChange: (Boolean) -> Unit,
        receivePrivateChatMessages: Boolean, onReceivePrivateChatMessagesChange: (Boolean) -> Unit,
        receiveCriticalLocationAlerts: Boolean, onReceiveCriticalLocationAlertsChange: (Boolean) -> Unit,
        muteNotificationsWhenAppOpen: Boolean, onMuteNotificationsWhenAppOpenChange: (Boolean) -> Unit
    ) {
        val DarkBlue = Color(0xFF0D47A1)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.9f))
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = "Notifications", fontWeight = FontWeight.Bold, color = DarkBlue)
                FeatureToggle(text = "Receive Group Chat Messages", checked = receiveGroupChatNotifications, onCheckedChange = onReceiveGroupChatNotificationsChange, enabled = true, DarkBlue = DarkBlue)
                FeatureToggle(text = "Receive Private Chat Messages", checked = receivePrivateChatMessages, onCheckedChange = onReceivePrivateChatMessagesChange, enabled = true, DarkBlue = DarkBlue)
                FeatureToggle(text = "Receive Critical Location Alerts", checked = receiveCriticalLocationAlerts, onCheckedChange = onReceiveCriticalLocationAlertsChange, enabled = true, DarkBlue = DarkBlue)
                FeatureToggle(text = "Mute Notifications When App Is Open", checked = muteNotificationsWhenAppOpen, onCheckedChange = onMuteNotificationsWhenAppOpenChange, enabled = true, DarkBlue = DarkBlue)
            }
        }
    }

    @Composable
    fun MapDisplayTab(
        selectedMapTypeIndex: Int, onMapTypeIndexChange: (Int) -> Unit,
        mapTypeOptions: List<String>, mapTypeDropdownExpanded: Boolean, onMapTypeDropdownExpandedChange: (Boolean) -> Unit,
        selectedUnitIndex: Int, onUnitIndexChange: (Int) -> Unit,
        unitOptions: List<String>, unitDropdownExpanded: Boolean, onUnitDropdownExpandedChange: (Boolean) -> Unit,
        showMyOwnMarker: Boolean, onShowMyOwnMarkerChange: (Boolean) -> Unit,
        showMemberNamesOnMap: Boolean, onShowMemberNamesOnMapChange: (Boolean) -> Unit
    ) {
        val DarkBlue = Color(0xFF0D47A1)
        val LightCream = Color(0xFFFFFDD0)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.9f))
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = "Map & Display Preferences", fontWeight = FontWeight.Bold, color = DarkBlue)

                Column {
                    Text("Default Map Type:", color = DarkBlue, fontWeight = FontWeight.Medium)
                    ExposedDropdownMenuBox(
                        expanded = mapTypeDropdownExpanded,
                        onExpandedChange = onMapTypeDropdownExpandedChange
                    ) {
                        OutlinedTextField(
                            value = mapTypeOptions[selectedMapTypeIndex],
                            onValueChange = {},
                            readOnly = true,
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
                            expanded = mapTypeDropdownExpanded,
                            onDismissRequest = { onMapTypeDropdownExpandedChange(false) },
                            modifier = Modifier.background(LightCream)
                        ) {
                            mapTypeOptions.forEachIndexed { index, selectionOption ->
                                DropdownMenuItem(
                                    text = { Text(selectionOption, color = DarkBlue) },
                                    onClick = {
                                        onMapTypeIndexChange(index)
                                        onMapTypeDropdownExpandedChange(false)
                                    }
                                )
                            }
                        }
                    }
                }

                Column {
                    Text("Units of Measurement:", color = DarkBlue, fontWeight = FontWeight.Medium)
                    ExposedDropdownMenuBox(
                        expanded = unitDropdownExpanded,
                        onExpandedChange = onUnitDropdownExpandedChange
                    ) {
                        OutlinedTextField(
                            value = unitOptions[selectedUnitIndex],
                            onValueChange = {},
                            readOnly = true,
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
                            expanded = unitDropdownExpanded,
                            onDismissRequest = { onUnitDropdownExpandedChange(false) },
                            modifier = Modifier.background(LightCream)
                        ) {
                            unitOptions.forEachIndexed { index, selectionOption ->
                                DropdownMenuItem(
                                    text = { Text(selectionOption, color = DarkBlue) },
                                    onClick = {
                                        onUnitIndexChange(index)
                                        onUnitDropdownExpandedChange(false)
                                    }
                                )
                            }
                        }
                    }
                }

                FeatureToggle(text = "Show My Own Location Marker", checked = showMyOwnMarker, onCheckedChange = onShowMyOwnMarkerChange, enabled = true, DarkBlue = DarkBlue)
                FeatureToggle(text = "Show Member Names on Map", checked = showMemberNamesOnMap, onCheckedChange = onShowMemberNamesOnMapChange, enabled = true, DarkBlue = DarkBlue)
            }
        }
    }

    @Composable
    fun GeneralTab(onAboutAppClick: () -> Unit, onLogoutClick: () -> Unit) {
        val DarkBlue = Color(0xFF0D47A1)
        val context = LocalContext.current
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.9f))
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = "General", fontWeight = FontWeight.Bold, color = DarkBlue)
                Button(onClick = onAboutAppClick,
                    colors = ButtonDefaults.buttonColors(containerColor = DarkBlue)
                ) {
                    Text("About TeamSync", color = Color.White)
                }
                Text("App Version: ${context.packageManager.getPackageInfo(context.packageName, 0).versionName}",
                    color = DarkBlue.copy(alpha = 0.8f), fontSize = 14.sp)
                Spacer(Modifier.height(16.dp))
                Button(onClick = onLogoutClick,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Log Out", color = Color.White)
                }
            }
        }
    }

    @Composable
    fun SettingsTextField(label: String, value: String, onValueChange: (String) -> Unit, darkBlue: Color) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label, color = darkBlue) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = darkBlue, unfocusedTextColor = darkBlue.copy(alpha = 0.8f),
                focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                focusedBorderColor = darkBlue, unfocusedBorderColor = darkBlue.copy(alpha = 0.5f),
                focusedLabelColor = darkBlue, unfocusedLabelColor = darkBlue.copy(alpha = 0.7f),
                cursorColor = darkBlue
            ),
            keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Text) // Default keyboard type
        )
    }

    // Reusable FeatureToggle Composable (copied from GroupCreationScreen, if it's not already in Helpers)
    @Composable
    fun FeatureToggle(
        text: String,
        checked: Boolean,
        onCheckedChange: (Boolean) -> Unit,
        enabled: Boolean,
        DarkBlue: Color,
        content: @Composable (() -> Unit)? = null
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = enabled) { onCheckedChange(!checked) }
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = text,
                    color = if (enabled) DarkBlue else DarkBlue.copy(alpha = 0.5f),
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = checked,
                    onCheckedChange = onCheckedChange,
                    enabled = enabled,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = DarkBlue,
                        checkedTrackColor = DarkBlue.copy(alpha = 0.7f),
                        uncheckedThumbColor = DarkBlue.copy(alpha = 0.5f),
                        uncheckedTrackColor = DarkBlue.copy(alpha = 0.3f),
                        disabledCheckedThumbColor = DarkBlue.copy(alpha = 0.3f),
                        disabledCheckedTrackColor = DarkBlue.copy(alpha = 0.2f),
                        disabledUncheckedThumbColor = DarkBlue.copy(alpha = 0.2f),
                        disabledUncheckedTrackColor = DarkBlue.copy(alpha = 0.1f)
                    )
                )
            }
            content?.invoke()
        }
    }

    // NEW: AccordionItem Composable
    @Composable
    fun AccordionItem(
        title: String,
        icon: ImageVector, // NEW: Icon parameter
        expanded: Boolean,
        onToggle: () -> Unit,
        content: @Composable () -> Unit
    ) {
        val DarkBlue = Color(0xFF0D47A1)
        val LightCream = Color(0xFFFFFDD0)

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp), // Some padding between items
            shape = RoundedCornerShape(16.dp), // Slightly more rounded corners for accordion
            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.95f)), // Slightly less transparent for content readability
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onToggle() }
                        .padding(18.dp), // Slightly more padding for touch target
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) { // Row for icon and title
                        Icon(
                            imageVector = icon, // Display the icon
                            contentDescription = title,
                            tint = DarkBlue,
                            modifier = Modifier.size(24.dp) // Icon size
                        )
                        Spacer(Modifier.width(12.dp)) // Space between icon and title
                        Text(
                            text = title,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp, // Slightly larger font for title
                            color = DarkBlue
                        )
                    }
                    Icon(
                        imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (expanded) "Collapse" else "Expand",
                        tint = DarkBlue,
                        modifier = Modifier.size(28.dp) // Slightly larger icon
                    )
                }

                AnimatedVisibility(
                    visible = expanded,
                    enter = expandVertically(animationSpec = tween(300)) + fadeIn(animationSpec = tween(300)),
                    exit = shrinkVertically(animationSpec = tween(300)) + fadeOut(animationSpec = tween(300))
                ) {
                    Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp)) { // Padding for content
                        content()
                    }
                }
            }
        }
    }
}
