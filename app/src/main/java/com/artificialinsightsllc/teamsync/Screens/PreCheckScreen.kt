// In file: app/src/main/java/com/artificialinsightsllc/teamsync/Screens/PreCheckScreen.kt
package com.artificialinsightsllc.teamsync.Screens

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets // Import WindowInsets
import androidx.compose.foundation.layout.asPaddingValues // Import asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBars // Import navigationBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import com.artificialinsightsllc.teamsync.Navigation.NavRoutes
import com.artificialinsightsllc.teamsync.R
import com.artificialinsightsllc.teamsync.Services.LocationTrackingService
import com.artificialinsightsllc.teamsync.TeamSyncApplication
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.delay // Import delay
import kotlinx.coroutines.launch // Import launch
import androidx.compose.runtime.rememberCoroutineScope // Import rememberCoroutineScope
import kotlinx.coroutines.tasks.await

class PreCheckScreen(private val navController: NavHostController) {

    // Define the sequence of permissions we need to request and their types
    private enum class PermissionType {
        LOCATION_FOREGROUND, // ACCESS_FINE_LOCATION (implies COARSE)
        LOCATION_BACKGROUND, // ACCESS_BACKGROUND_LOCATION
        CAMERA,
        READ_MEDIA, // READ_MEDIA_IMAGES or READ_EXTERNAL_STORAGE
        POST_NOTIFICATIONS,
        BATTERY_OPTIMIZATION // NEW: For ignoring battery optimizations
    }

    // Data class to hold information for each permission type, used in UI
    private data class PermissionInfo(
        val type: PermissionType,
        val title: String,
        val description: String,
        val manifestPermissions: Array<String>? = null, // Nullable for BATTERY_OPTIMIZATION
        val isSystemSetting: Boolean = false, // NEW: Indicates if it's a system setting
        val direction: String? = null // NEW: Specific instruction for the user
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as PermissionInfo

            if (type != other.type) return false
            if (title != other.title) return false
            if (description != other.description) return false
            if (manifestPermissions != null) {
                if (other.manifestPermissions == null || !manifestPermissions.contentEquals(other.manifestPermissions)) return false
            } else if (other.manifestPermissions != null) return false
            if (isSystemSetting != other.isSystemSetting) return false // NEW: Compare this field too
            if (direction != other.direction) return false // NEW: Compare direction

            return true
        }

        override fun hashCode(): Int {
            var result = type.hashCode()
            result = 31 * result + title.hashCode()
            result = 31 * result + description.hashCode()
            result = 31 * result + (manifestPermissions?.contentHashCode() ?: 0)
            result = 31 * result + isSystemSetting.hashCode() // NEW: Include in hashcode
            result = 31 * result + (direction?.hashCode() ?: 0) // NEW: Include direction in hashcode
            return result
        }
    }

    // Define all permission info upfront as a class member
    private val requiredPermissionsInfo = mutableListOf<PermissionInfo>().apply {
        add(PermissionInfo(
            type = PermissionType.LOCATION_FOREGROUND,
            title = "Location (Precise)",
            description = "TeamSync needs your precise location to show your position and your group members' positions on the map. This is fundamental for the app's core mapping features.",
            manifestPermissions = getManifestPermissionsForType(PermissionType.LOCATION_FOREGROUND),
            direction = "Choose 'Allow all the time' or 'Allow only while using the app'" // Specific direction
        ))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            add(PermissionInfo(
                type = PermissionType.LOCATION_BACKGROUND,
                title = "Location (Background)",
                description = "For continuous team coordination and real-time tracking, TeamSync requires background location access. This allows your location to update even when the app is closed or not in active use, ensuring your team always knows your last known position.",
                manifestPermissions = getManifestPermissionsForType(PermissionType.LOCATION_BACKGROUND),
                direction = "Choose 'Allow all the time' or 'Allow in background'" // Specific direction
            ))
        }
        add(PermissionInfo(
            type = PermissionType.CAMERA,
            title = "Camera Access",
            description = "TeamSync needs access to your camera if you wish to take new photos directly within the app, for example, to set your profile picture or add geotagged images to the map.",
            manifestPermissions = getManifestPermissionsForType(PermissionType.CAMERA),
            direction = "Choose 'Allow' or 'While using the app'" // Specific direction
        ))
        add(PermissionInfo(
            type = PermissionType.READ_MEDIA,
            title = "Photos/Media Access",
            description = "TeamSync needs access to your photos and media to allow you to select and upload existing images from your device's gallery, for instance, to set your profile picture or share media with your group.",
            manifestPermissions = getManifestPermissionsForType(PermissionType.READ_MEDIA),
            direction = "Choose 'Allow'" // Specific direction
        ))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(PermissionInfo(
                type = PermissionType.POST_NOTIFICATIONS,
                title = "Notifications",
                description = "On Android 13 (API 33) and higher, TeamSync needs permission to post notifications. This is essential for receiving important updates, alerts (like critical location events), and for the background location service to run without interruption.",
                manifestPermissions = getManifestPermissionsForType(PermissionType.POST_NOTIFICATIONS),
                direction = "Choose 'Allow' or 'Enable'" // Specific direction
            ))
        }
        // NEW: Battery Optimization exemption
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) { // API 23+ for battery optimizations
            add(PermissionInfo(
                type = PermissionType.BATTERY_OPTIMIZATION,
                title = "Unrestricted Battery Usage",
                description = "For reliable, continuous background location tracking, TeamSync needs to be exempt from battery optimizations. This prevents the Android system from putting the app to sleep, ensuring your team always has your up-to-date location. This is crucial for real-time coordination.",
                isSystemSetting = true, // Mark as a system setting
                direction = "Choose 'Unrestricted' or 'Not optimized'" // Specific direction
            ))
        }
    }

    // --- Helper functions for permission checks ---
    private fun isPermissionGranted(context: Context, permissionType: PermissionType): Boolean {
        return when (permissionType) {
            PermissionType.LOCATION_FOREGROUND -> {
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
            }
            PermissionType.LOCATION_BACKGROUND -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
                } else {
                    true // Not needed on older Android versions, fine/coarse cover implicitly
                }
            }
            PermissionType.CAMERA -> {
                ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
            }
            PermissionType.READ_MEDIA -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
                } else {
                    ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
                }
            }
            PermissionType.POST_NOTIFICATIONS -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                } else {
                    true // Not needed on older Android versions
                }
            }
            // NEW: Check for battery optimization exemption
            PermissionType.BATTERY_OPTIMIZATION -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val packageName = context.packageName
                    val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                    pm.isIgnoringBatteryOptimizations(packageName)
                } else {
                    true // Not applicable on older Android versions
                }
            }
        }
    }

    private fun getManifestPermissionsForType(permissionType: PermissionType): Array<String>? { // Changed return type to Array<String>?
        return when (permissionType) {
            PermissionType.LOCATION_FOREGROUND -> arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            PermissionType.LOCATION_BACKGROUND -> arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            PermissionType.CAMERA -> arrayOf(Manifest.permission.CAMERA)
            PermissionType.READ_MEDIA -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
            } else {
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            PermissionType.POST_NOTIFICATIONS -> arrayOf(Manifest.permission.POST_NOTIFICATIONS)
            PermissionType.BATTERY_OPTIMIZATION -> null // No direct manifest permission
        }?.filterNotNull()?.toTypedArray() // Filter nulls and convert to array
    }

    // --- Services Start/Stop helpers (These are NOT called from here anymore) ---
    // Moved to be private functions in this class for organization, but removed calls from Content().
    private fun startForegroundLocationService(context: Context) {
        val serviceIntent = Intent(context, LocationTrackingService::class.java).apply {
            action = LocationTrackingService.ACTION_START_SERVICE
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                context.startForegroundService(serviceIntent)
                Log.d("PreCheckScreen", "Attempted to start foreground service via startForegroundService.")
            } catch (e: Exception) {
                Log.e("PreCheckScreen", "Failed to start foreground service: ${e.message}")
                Toast.makeText(context, "Cannot start background location service. Please open the app or grant necessary permissions.", Toast.LENGTH_LONG).show()
            }
        } else {
            context.startService(serviceIntent)
            Log.d("PreCheckScreen", "Attempted to start service via startService (pre-Oreo).")
        }
    }

    private fun stopForegroundLocationService(context: Context) {
        val serviceIntent = Intent(context, LocationTrackingService::class.java).apply {
            action = LocationTrackingService.ACTION_STOP_SERVICE
        }
        context.stopService(serviceIntent)
        Log.d("PreCheckScreen", "Requested stop of LocationTrackingService completely.")
    }

    // Helper to update all permission states, called on init and after launcher result
    private fun updateAllPermissionStates(
        context: Context,
        statesMap: MutableState<Map<PermissionType, Boolean>>,
        allPermissionInfos: List<PermissionInfo>
    ) {
        val newStates = allPermissionInfos.associate { info ->
            info.type to isPermissionGranted(context, info.type)
        }
        statesMap.value = newStates
        Log.d("PreCheckScreen", "Updated permission states: $newStates")
    }

    @Composable
    fun Content() {
        val context = LocalContext.current
        val groupMonitorService = (context.applicationContext as TeamSyncApplication).groupMonitorService
        val firestore = FirebaseFirestore.getInstance()
        val coroutineScope = rememberCoroutineScope() // Coroutine scope for delayed re-check

        // State to manage overall loading/checking phase
        var showLoadingIndicator by remember { mutableStateOf(true) }
        var initialUserCheckDone by remember { mutableStateOf(false) }

        // State to store current permission status for each type
        val permissionStates = remember { mutableStateOf(mapOf<PermissionType, Boolean>()) }

        // Derived state: true if ALL required permissions are granted
        val allPermissionsFullyGranted by remember(permissionStates.value) {
            val allGranted = requiredPermissionsInfo.all { info ->
                permissionStates.value[info.type] == true
            }
            mutableStateOf<Boolean>(allGranted)
        }

        // State to store the user's selected active group ID fetched from Firestore
        var initialUserSelectedGroupId by remember { mutableStateOf<String?>(null) }

        // NEW: State for AlertDialog
        var showAlertDialog by remember { mutableStateOf(false) }
        var alertDialogTitle by remember { mutableStateOf("") }
        var alertDialogMessage by remember { mutableStateOf("") }
        var alertDialogConfirmAction: (() -> Unit)? by remember { mutableStateOf(null) }

        // NEW: State for Re-Check Permissions Button
        var showRecheckButton by remember { mutableStateOf(false) }


        // DarkBlue definition (assuming it's not globally available in this specific file scope)
        val DarkBlue = Color(0xFF0D47A1)


        // --- Permission Launcher for system dialogs ---
        val permissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestMultiplePermissions()
        ) { permissionsMap ->
            Log.d("PreCheckScreen", "System permission dialog result received. Updating permission states.")
            updateAllPermissionStates(context, permissionStates, requiredPermissionsInfo) // Update UI immediately
            // The LaunchedEffect(allPermissionsFullyGranted) will now handle navigation if all are granted.
        }

        // NEW: Launcher for battery optimization settings
        val batteryOptimizationLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult()
        ) {
            // After returning from settings, re-check all permissions WITH A DELAY
            Log.d("PreCheckScreen", "Returned from Battery Optimization settings. Scheduling re-check with delay.")
            coroutineScope.launch {
                delay(300L) // Small delay to allow system state to update
                updateAllPermissionStates(context, permissionStates, requiredPermissionsInfo)
            }
        }


        // --- LaunchedEffect for initial setup (runs once) ---
        LaunchedEffect(Unit) {
            if (!initialUserCheckDone) {
                val auth = FirebaseAuth.getInstance()
                val userId = auth.currentUser?.uid
                if (userId == null) {
                    Log.w("PreCheckScreen", "No authenticated user found. Navigating to LoginScreen.")
                    navController.navigate(NavRoutes.LOGIN) {
                        popUpTo(NavRoutes.PRE_CHECK) { inclusive = true }
                    }
                    return@LaunchedEffect
                }

                try {
                    val userDocRef = firestore.collection("users").document(userId)
                    val userSnapshot = userDocRef.get().await()
                    initialUserSelectedGroupId = userSnapshot.toObject(com.artificialinsightsllc.teamsync.Models.UserModel::class.java)?.selectedActiveGroupId
                    Log.d("PreCheckScreen", "User profile loaded, selected active group ID: ${initialUserSelectedGroupId ?: "None"}")
                } catch (e: Exception) {
                    Log.e("PreCheckScreen", "Error loading user profile: ${e.message}")
                    alertDialogTitle = "Error Loading User Data"
                    alertDialogMessage = "Failed to load your user profile: ${e.localizedMessage}. Please try logging in again."
                    alertDialogConfirmAction = {
                        navController.navigate(NavRoutes.LOGIN) {
                            popUpTo(NavRoutes.PRE_CHECK) { inclusive = true }
                        }
                    }
                    showAlertDialog = true
                    return@LaunchedEffect
                }
                initialUserCheckDone = true // Mark as done to prevent re-running
            }

            // After user check, update all permission states initially
            updateAllPermissionStates(context, permissionStates, requiredPermissionsInfo)

            // CRITICAL LOGIC FOR INITIAL DISPLAY:
            // If all permissions are granted, we keep showLoadingIndicator as true, and auto-navigate.
            // This ensures permission cards are never shown.
            // If NOT all are granted, we set showLoadingIndicator to false to reveal the cards.
            if (!allPermissionsFullyGranted) {
                showLoadingIndicator = false // Permissions are missing, so show the cards.
                showRecheckButton = true // Enable recheck button if cards are shown.
                Log.d("PreCheckScreen", "Permissions missing after initial check. Displaying permission options.")
            } else {
                Log.d("PreCheckScreen", "All permissions initially granted. Preparing for auto-navigation. Keeping loading indicator visible.")
            }
        }

        // --- LaunchedEffect to automatically proceed when all permissions are granted ---
        // This is the trigger for navigation to MAIN screen
        LaunchedEffect(allPermissionsFullyGranted, initialUserCheckDone) {
            // Only proceed if all permissions are granted AND we've finished the initial user check
            if (allPermissionsFullyGranted && initialUserCheckDone) {
                Log.d("PreCheckScreen", "All permissions now granted (triggered by state change). Auto-navigating.")
                // No longer calling groupMonitorService.startMonitoring here.
                // It's called earlier in TeamSyncApplication.onCreate().
                groupMonitorService.setUiPermissionsGranted(true) // Notify GroupMonitorService that permissions are granted for UI
                navController.navigate(NavRoutes.MAIN) {
                    popUpTo(NavRoutes.PRE_CHECK) { inclusive = true }
                }
            }
        }


        // --- UI Layout ---
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            Image(
                painter = painterResource(id = R.drawable.background1),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // This outer 'if' determines if we show the loading screen OR the permission cards.
            if (showLoadingIndicator) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(modifier = Modifier.padding(bottom = 16.dp))
                    Text(
                        text = "Loading user data and checking initial permissions...",
                        color = DarkBlue, // Set to DarkBlue
                        fontWeight = FontWeight.Bold, // Set to Bold
                        textAlign = TextAlign.Center, // Centered horizontally
                        modifier = Modifier.fillMaxWidth() // Fill width for centering
                    )
                }
            } else if (!allPermissionsFullyGranted) { // This else block will ONLY be composed and rendered if showLoadingIndicator is false
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp)
                        .padding(WindowInsets.systemBars.asPaddingValues()), // Apply system bars padding
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Security Permissions",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = DarkBlue, // Changed to DarkBlue for consistency
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 24.dp)
                    )

                    requiredPermissionsInfo.forEach { permInfo ->
                        val isGranted = permissionStates.value[permInfo.type] == true
                        val buttonColor = if (isGranted) Color(0xFF4CAF50) else Color(0xFFF44336) // Green vs Red

                        // Special handling for Background Location button enable state
                        val buttonEnabled = when (permInfo.type) {
                            PermissionType.LOCATION_BACKGROUND -> permissionStates.value[PermissionType.LOCATION_FOREGROUND] == true
                            PermissionType.BATTERY_OPTIMIZATION -> true // Always enabled, just launches system settings
                            // FIX: Added 'else' branch to the 'when' expression to make it exhaustive
                            else -> true
                        }

                        PermissionItemCard(
                            title = permInfo.title,
                            direction = permInfo.direction,
                            description = permInfo.description,
                            isGranted = isGranted,
                            buttonColor = buttonColor,
                            buttonEnabled = buttonEnabled,
                            onGrantClick = {
                                if (permInfo.isSystemSetting) {
                                    if (permInfo.type == PermissionType.BATTERY_OPTIMIZATION) {
                                        val packageName = context.packageName
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                                            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                                                fun attemptLaunchBatterySettings(index: Int) {
                                                    val intentsToTry = listOf(
                                                        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                                            data = Uri.fromParts("package", packageName, null)
                                                        },
                                                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                                            data = Uri.fromParts("package", packageName, null)
                                                        },
                                                        Intent(Settings.ACTION_SETTINGS)
                                                    )

                                                    if (index >= intentsToTry.size) {
                                                        Log.e("PreCheckScreen", "All programmatic attempts to open battery optimization settings failed.")
                                                        alertDialogTitle = "Action Required: Manual Steps"
                                                        alertDialogMessage = "Could not open any relevant settings automatically. Please manually navigate to your device's settings:\n\n1. Go to 'Settings'\n2. Tap 'Apps & notifications' (or 'Apps' / 'Applications')\n3. Find and tap 'TeamSync'\n4. Tap 'Battery' (or 'Battery usage')\n5. Choose 'Unrestricted' or 'Not optimized'. This is essential for continuous background tracking."
                                                        alertDialogConfirmAction = null
                                                        showAlertDialog = true
                                                        return
                                                    }

                                                    val intent = intentsToTry[index]
                                                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

                                                    if (intent.resolveActivity(context.packageManager) != null) {
                                                        Log.d("PreCheckScreen", "Attempting to launch intent: ${intent.action} (Index: $index) for $packageName")
                                                        try {
                                                            batteryOptimizationLauncher.launch(intent)
                                                        } catch (e: Exception) {
                                                            Log.e("PreCheckScreen", "Exception launching intent ${intent.action} (Index: $index): ${e.message}", e)
                                                            alertDialogTitle = "Battery Optimization Settings"
                                                            alertDialogMessage = "Could not open settings using this method. Please try the next option.\n\nError: ${e.localizedMessage}"
                                                            alertDialogConfirmAction = { attemptLaunchBatterySettings(index + 1) }
                                                            showAlertDialog = true
                                                        }
                                                    } else {
                                                        Log.w("PreCheckScreen", "Intent ${intent.action} (Index: $index) cannot be resolved on this device. Trying next.")
                                                        alertDialogTitle = "Battery Optimization Settings"
                                                        alertDialogMessage = "This method is not supported on your device. Please try the next option."
                                                        alertDialogConfirmAction = { attemptLaunchBatterySettings(index + 1) }
                                                        showAlertDialog = true
                                                    }
                                                }

                                                alertDialogTitle = "Open Battery Settings"
                                                alertDialogMessage = "TeamSync will now attempt to open your device's battery optimization settings. Please tap 'OK' to proceed. You may need to manually select 'Unrestricted' or 'Not optimized' for TeamSync on the next screen."
                                                alertDialogConfirmAction = { attemptLaunchBatterySettings(0) }
                                                showAlertDialog = true

                                            } else {
                                                alertDialogTitle = "${permInfo.title} is already granted!"
                                                alertDialogMessage = "This permission is already set to 'Unrestricted' or 'Not optimized'.\n\nIf you wish to change this, you can do so manually via 'Settings > Apps & notifications > TeamSync > Battery'."
                                                alertDialogConfirmAction = null
                                                showAlertDialog = true
                                            }
                                        }
                                    }
                                } else { // Regular manifest permissions
                                    if (!isGranted) {
                                        permInfo.manifestPermissions?.let {
                                            permissionLauncher.launch(it)
                                        } ?: run {
                                            alertDialogTitle = "Permission Error"
                                            alertDialogMessage = "Failed to request permission for ${permInfo.title}. This type of permission has no manifest entry."
                                            alertDialogConfirmAction = null
                                            showAlertDialog = true
                                        }
                                    } else { // Already granted manifest permission
                                        alertDialogTitle = "${permInfo.title} is already granted!"
                                        // Removed `direction` from the `else` branch message as it's not a generic status string for granted permissions.
                                        alertDialogMessage = "This permission is already granted. You do not need to take any action."
                                        alertDialogConfirmAction = null
                                        showAlertDialog = true
                                    }
                                }
                            }
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "All permissions must be granted to continue.",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                    // Re-Check Permissions Button (Only visible if not all permissions are granted)
                    if (showRecheckButton) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = {
                                Log.d("PreCheckScreen", "Re-Check Permissions button clicked. Updating all permission states.")
                                updateAllPermissionStates(context, permissionStates, requiredPermissionsInfo)
                            },
                            modifier = Modifier
                                .fillMaxWidth(0.7f)
                                .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                        ) {
                            Text("Re-Check Permissions", color = Color.White)
                        }
                    }
                }
            }
        }

        // Generic AlertDialog for messages
        if (showAlertDialog) {
            AlertDialog(
                onDismissRequest = { showAlertDialog = false },
                title = { Text(alertDialogTitle) },
                text = { Text(alertDialogMessage) },
                confirmButton = {
                    Button(onClick = {
                        showAlertDialog = false
                        alertDialogConfirmAction?.invoke() // Execute the stored action
                    }) {
                        Text("OK")
                    }
                }
            )
        }
    }

    @Composable
    private fun PermissionItemCard(
        title: String,
        direction: String?,
        description: String,
        isGranted: Boolean,
        buttonColor: Color,
        buttonEnabled: Boolean,
        onGrantClick: () -> Unit
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // Fixed background color to be always 0.9f alpha when cards are displayed
                .background(Color.White.copy(alpha = 0.9f), RoundedCornerShape(12.dp))
                .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
                .padding(16.dp),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            direction?.let {
                Text(
                    text = "Direction: ${it}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Red
                )
            }
            Text(
                text = description,
                fontSize = 14.sp,
                color = Color.DarkGray
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = onGrantClick,
                enabled = !isGranted && buttonEnabled,
                colors = ButtonDefaults.buttonColors(
                    containerColor = buttonColor,
                    disabledContainerColor = buttonColor.copy(alpha = 0.5f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (isGranted) "Permission Granted" else "Grant Permission",
                    color = Color.White
                )
            }
            if (!buttonEnabled && !isGranted) {
                Text(
                    text = "Requires Foreground Location first.",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
        }
    }
}
