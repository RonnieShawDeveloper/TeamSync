// In file: app/src/main/java/com/artificialinsightsllc/teamsync/Screens/PreCheckScreen.kt
package com.artificialinsightsllc.teamsync.Screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import kotlinx.coroutines.tasks.await

class PreCheckScreen(private val navController: NavHostController) {

    // Define the sequence of permissions we need to request and their types
    private enum class PermissionType {
        LOCATION_FOREGROUND, // ACCESS_FINE_LOCATION (implies COARSE)
        LOCATION_BACKGROUND, // ACCESS_BACKGROUND_LOCATION
        CAMERA,
        READ_MEDIA, // READ_MEDIA_IMAGES or READ_EXTERNAL_STORAGE
        POST_NOTIFICATIONS
    }

    // Data class to hold information for each permission type, used in UI
    private data class PermissionInfo(
        val type: PermissionType,
        val title: String,
        val description: String,
        val manifestPermissions: Array<String>
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as PermissionInfo

            if (type != other.type) return false
            if (title != other.title) return false
            if (description != other.description) return false
            if (!manifestPermissions.contentEquals(other.manifestPermissions)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = type.hashCode()
            result = 31 * result + title.hashCode()
            result = 31 * result + description.hashCode()
            result = 31 * result + manifestPermissions.contentHashCode()
            return result
        }
    }

    // Define all permission info upfront as a class member
    private val requiredPermissionsInfo = mutableListOf<PermissionInfo>().apply {
        add(PermissionInfo(
            type = PermissionType.LOCATION_FOREGROUND,
            title = "Location (Precise)",
            description = "TeamSync needs your precise location to show your position and your group members' positions on the map. This is fundamental for the app's core mapping features.",
            manifestPermissions = getManifestPermissionsForType(PermissionType.LOCATION_FOREGROUND)
        ))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            add(PermissionInfo(
                type = PermissionType.LOCATION_BACKGROUND,
                title = "Location (Background)",
                description = "For continuous team coordination and real-time tracking, TeamSync requires background location access. This allows your location to update even when the app is closed or not in active use, ensuring your team always knows your last known position.",
                manifestPermissions = getManifestPermissionsForType(PermissionType.LOCATION_BACKGROUND)
            ))
        }
        add(PermissionInfo(
            type = PermissionType.CAMERA,
            title = "Camera Access",
            description = "TeamSync needs access to your camera if you wish to take new photos directly within the app, for example, to set your profile picture or add geotagged images to the map.",
            manifestPermissions = getManifestPermissionsForType(PermissionType.CAMERA)
        ))
        add(PermissionInfo(
            type = PermissionType.READ_MEDIA,
            title = "Photos/Media Access",
            description = "TeamSync needs access to your photos and media to allow you to select and upload existing images from your device's gallery, for instance, to set your profile picture or share media with your group.",
            manifestPermissions = getManifestPermissionsForType(PermissionType.READ_MEDIA)
        ))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(PermissionInfo(
                type = PermissionType.POST_NOTIFICATIONS,
                title = "Notifications",
                description = "On Android 13 (API 33) and higher, TeamSync needs permission to post notifications. This is essential for receiving important updates, alerts (like critical location events), and for the background location service to run without interruption.",
                manifestPermissions = getManifestPermissionsForType(PermissionType.POST_NOTIFICATIONS)
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
        }
    }

    private fun getManifestPermissionsForType(permissionType: PermissionType): Array<String> {
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
        }.filterNotNull().toTypedArray()
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


        // --- Permission Launcher for system dialogs ---
        val permissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestMultiplePermissions()
        ) { permissionsMap ->
            Log.d("PreCheckScreen", "System permission dialog result received. Updating permission states.")
            updateAllPermissionStates(context, permissionStates, requiredPermissionsInfo) // Update UI immediately
            // The LaunchedEffect(allPermissionsFullyGranted) will now handle navigation if all are granted.
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
                    Toast.makeText(context, "Error loading user data: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                    navController.navigate(NavRoutes.LOGIN) {
                        popUpTo(NavRoutes.PRE_CHECK) { inclusive = true }
                    }
                    return@LaunchedEffect
                }
                initialUserCheckDone = true // Mark as done to prevent re-running
            }

            // After user check, update all permission states initially
            updateAllPermissionStates(context, permissionStates, requiredPermissionsInfo)

            // Auto-navigate if all are already granted on launch
            // The check for allPermissionsFullyGranted is now handled by the separate LaunchedEffect below
            // to ensure it triggers any time the permissionStates.value changes.
            if (allPermissionsFullyGranted) {
                Log.d("PreCheckScreen", "All permissions initially granted. Triggering auto-navigation via state change.")
                // The actual service start and navigation will be handled by LaunchedEffect(allPermissionsFullyGranted)
            } else {
                showLoadingIndicator = false // Done with initial loading, show buttons
                Log.d("PreCheckScreen", "Permissions missing. Displaying permission options.")
            }
        }

        // --- NEW LaunchedEffect to automatically proceed when all permissions are granted ---
        // This ensures that whenever permissionStates.value updates AND all are granted,
        // the app automatically navigates.
        LaunchedEffect(allPermissionsFullyGranted, initialUserCheckDone) {
            // Only proceed if all permissions are granted AND we've finished the initial user check
            // and we're not already loading (which indicates a pending action from a button click/launcher result)
            if (allPermissionsFullyGranted && initialUserCheckDone) {
                Log.d("PreCheckScreen", "All permissions now granted (triggered by state change). Auto-navigating.")
                // It is now the responsibility of GroupMonitorService to start/stop the LocationTrackingService
                // and pass the memberId correctly.
                groupMonitorService.setUiPermissionsGranted(true)
                groupMonitorService.startMonitoring(initialUserSelectedGroupId) // Ensure latest selected group is passed
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

            if (showLoadingIndicator) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(modifier = Modifier.padding(bottom = 16.dp))
                    Text("Loading user data and checking initial permissions...", color = Color.White)
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Security Permissions",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 24.dp)
                    )

                    requiredPermissionsInfo.forEach { permInfo ->
                        val isGranted = permissionStates.value[permInfo.type] == true
                        val buttonColor = if (isGranted) Color(0xFF4CAF50) else Color(0xFFF44336) // Green vs Red
                        val isLocationForegroundGranted = permissionStates.value[PermissionType.LOCATION_FOREGROUND] == true

                        // Special handling for Background Location button enable state
                        val buttonEnabled = when (permInfo.type) {
                            PermissionType.LOCATION_BACKGROUND -> isLocationForegroundGranted // Only enabled if foreground is granted
                            else -> true // Other buttons are always enabled (unless granted)
                        }

                        PermissionItemCard(
                            title = permInfo.title,
                            description = permInfo.description,
                            isGranted = isGranted,
                            buttonColor = buttonColor,
                            buttonEnabled = buttonEnabled,
                            onGrantClick = {
                                // Direct launch of system permission dialog
                                if (!isGranted) {
                                    permissionLauncher.launch(permInfo.manifestPermissions)
                                } else {
                                    Toast.makeText(context, "${permInfo.title} is already granted!", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    if (!allPermissionsFullyGranted) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "All permissions must be granted to continue.",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center
                        )
                    } else {
                        // Optional: Show a "All permissions granted, proceeding..." message briefly
                        Text(
                            "All permissions granted. Proceeding to TeamSync...",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        // Removed the PermissionRationaleDialog composable and its conditional rendering block
    }

    @Composable
    private fun PermissionItemCard(
        title: String,
        description: String,
        isGranted: Boolean,
        buttonColor: Color,
        buttonEnabled: Boolean,
        onGrantClick: () -> Unit
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
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
            Text(
                text = description,
                fontSize = 14.sp,
                color = Color.DarkGray
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = onGrantClick,
                enabled = !isGranted && buttonEnabled, // Button is enabled only if not granted AND its specific dependency is met
                colors = ButtonDefaults.buttonColors(
                    containerColor = buttonColor,
                    disabledContainerColor = buttonColor.copy(alpha = 0.5f) // Lighter shade when disabled
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (isGranted) "Permission Granted" else "Grant Permission",
                    color = Color.White
                )
            }
            if (!buttonEnabled && !isGranted) { // Show message if disabled due to dependency
                Text(
                    text = "Requires Foreground Location first.",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
        }
    }
}
