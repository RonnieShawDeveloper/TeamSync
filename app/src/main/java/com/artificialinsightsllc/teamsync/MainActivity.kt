package com.artificialinsightsllc.teamsync

import android.os.Bundle
import androidx.activity.compose.setContent
   import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.artificialinsightsllc.teamsync.Navigation.AppNavGraph
import com.artificialinsightsllc.teamsync.ui.theme.TeamSyncTheme
import androidx.fragment.app.FragmentActivity
import com.google.firebase.ktx.Firebase
import com.google.firebase.remoteconfig.ktx.remoteConfig
import com.google.firebase.remoteconfig.ktx.remoteConfigSettings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.util.Log
import androidx.navigation.NavController
import com.artificialinsightsllc.teamsync.Services.TeamSyncFirebaseMessagingService.Companion.FCM_DATA_PAYLOAD_KEY
import com.artificialinsightsllc.teamsync.Models.NotificationEntity
import com.artificialinsightsllc.teamsync.Models.NotificationType
import java.util.HashMap
import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresApi
import com.google.firebase.ktx.BuildConfig
import android.view.View
import android.view.ViewTreeObserver.OnWindowFocusChangeListener // Correct import for the listener interface
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.activity.compose.LocalActivity
import com.artificialinsightsllc.teamsync.TeamSyncApplication


class MainActivity : FragmentActivity() {

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // enableEdgeToEdge() is typically for content to draw behind system bars
        // For truly hiding them, we'll manage system UI visibility directly.
        // Uncomment if you still want content to draw behind when bars are transiently visible.
        // enableEdgeToEdge()

        // Get the application instance early
        val app = application as TeamSyncApplication

        // Handle incoming deep link and FCM data payload on cold start
        val incomingIntent = intent
        Log.d("MainActivity", "MainActivity onCreate: Incoming Intent Action: ${incomingIntent.action}, Data: ${incomingIntent.data}")

        // Check for FCM data payload manually if app was killed (onMessageReceived not called)
        // This data comes from FCM_DATA_PAYLOAD_KEY extra set in TeamSyncFirebaseMessagingService
        val fcmDataPayload = incomingIntent.getSerializableExtra(FCM_DATA_PAYLOAD_KEY) as? HashMap<String, String>
        if (fcmDataPayload != null && fcmDataPayload.isNotEmpty()) {
            Log.d("MainActivity", "Received FCM data payload on cold start (from Intent extras): $fcmDataPayload")

            val notificationEntity = NotificationEntity(
                messageId = fcmDataPayload["google.message_id"] ?: fcmDataPayload["messageId"],
                title = fcmDataPayload["title"] ?: fcmDataPayload["notification_title"],
                body = fcmDataPayload["body"] ?: fcmDataPayload["notification_body"],
                timestamp = fcmDataPayload["sent_time"]?.toLongOrNull() ?: System.currentTimeMillis(),
                type = fcmDataPayload["type"],
                senderId = fcmDataPayload["senderId"],
                groupId = fcmDataPayload["groupId"],
                isRead = false,
                dataPayload = fcmDataPayload.toString()
            )
            app.setPendingNotificationForSave(notificationEntity)
            Log.d("MainActivity", "Stored notification for delayed save from cold start: ${notificationEntity.title}")
        }


        setContent {
            TeamSyncTheme {
                val activity = LocalActivity.current
                val window = activity?.window
                val lifecycleOwner = LocalLifecycleOwner.current

                // Function to apply immersive mode flags
                val applyImmersiveMode = {
                    if (window != null) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            // For Android 11 (API 30) and above
                            window.setDecorFitsSystemWindows(false)
                            window.insetsController?.let {
                                it.hide(WindowInsets.Type.systemBars())
                                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                            }
                        } else {
                            // For older Android versions
                            @Suppress("DEPRECATION")
                            window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                                    or View.SYSTEM_UI_FLAG_FULLSCREEN)
                        }
                        Log.d("MainActivity", "Applied immersive mode flags.")
                    }
                }

                // Initial application of immersive mode when Composable enters composition
                LaunchedEffect(window) {
                    applyImmersiveMode()
                }

                // Re-apply immersive mode whenever the window gains focus (e.g., returning from recent apps, notification shade)
                DisposableEffect(lifecycleOwner, window) {
                    val decorView = window?.decorView
                    val focusChangeListener = OnWindowFocusChangeListener { hasFocus ->
                        if (hasFocus) {
                            Log.d("MainActivity", "Window gained focus, re-applying immersive mode.")
                            applyImmersiveMode()
                        }
                    }

                    // Correctly add and remove the listener via ViewTreeObserver
                    decorView?.viewTreeObserver?.addOnWindowFocusChangeListener(focusChangeListener)

                    onDispose {
                        decorView?.viewTreeObserver?.removeOnWindowFocusChangeListener(focusChangeListener)
                        Log.d("MainActivity", "Removed onWindowFocusChangeListener.")
                    }
                }


                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    val context = LocalContext.current
                    var showUpdateDialog by remember { mutableStateOf(false) }

                    LaunchedEffect(Unit) {
                        val remoteConfig = Firebase.remoteConfig
                        val configSettings = remoteConfigSettings {
                            minimumFetchIntervalInSeconds = if (BuildConfig.DEBUG) 0 else 3600
                        }
                        remoteConfig.setConfigSettingsAsync(configSettings)

                        val defaultCurrentAppVersion = try {
                            context.packageManager.getPackageInfo(context.packageName, 0).versionName?.trim() ?: "0.0"
                        } catch (e: Exception) {
                            "0.0"
                        }
                        remoteConfig.setDefaultsAsync(mapOf("version" to defaultCurrentAppVersion))

                        remoteConfig.fetchAndActivate()
                            .addOnCompleteListener { task ->
                                if (task.isSuccessful) {
                                    val updatedVersion = remoteConfig.getString("version").trim()
                                    val currentAppVersion = try {
                                        context.packageManager.getPackageInfo(context.packageName, 0).versionName?.trim() ?: "0.0"
                                    } catch (e: Exception) {
                                        "0.0"
                                    }

                                    Log.d("MainActivity", "Remote Config Version: '$updatedVersion'")
                                    Log.d("MainActivity", "Current App Version: '$currentAppVersion'")

                                    if (compareVersions(updatedVersion, currentAppVersion) > 0) {
                                        Log.d("MainActivity", "Newer version found: $updatedVersion > $currentAppVersion")
                                        showUpdateDialog = true
                                    } else {
                                        Log.d("MainActivity", "No update required: $updatedVersion <= $currentAppVersion")
                                    }
                                } else {
                                    Log.e("MainActivity", "Remote Config fetch failed: ${task.exception?.message}")
                                }
                            }
                    }

                    if (showUpdateDialog) {
                        AlertDialog(
                            onDismissRequest = {
                                finish()
                            },
                            title = {
                                Text(
                                    text = "Update Required",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0D47A1)
                                )
                            },
                            text = {
                                Text(
                                    text = "A newer version of TeamSync is available. Please update to continue using the app.",
                                    fontSize = 16.sp,
                                    color = Color.DarkGray
                                )
                            },
                            confirmButton = {
                                Button(
                                    onClick = {
                                        val appPackageName = context.packageName
                                        try {
                                            context.startActivity(android.content.Intent(
                                                android.content.Intent.ACTION_VIEW,
                                                android.net.Uri.parse("market://details?id=$appPackageName")
                                            ))
                                        } catch (e: android.content.ActivityNotFoundException) {
                                            context.startActivity(android.content.Intent(
                                                android.content.Intent.ACTION_VIEW,
                                                android.net.Uri.parse("https://play.google.com/store/apps/details?id=$appPackageName")
                                            ))
                                        }
                                        finish()
                                    }
                                ) {
                                    Text("Update Now", color = Color.White)
                                }
                            },
                            containerColor = Color.White
                        )
                    } else {
                        AppNavGraph(navController = navController, startDestination = null)
                    }
                }
            }
        }
    }

    /**
     * Compares two version strings (e.g., "1.0", "1.1.2").
     * Returns:
     * - Negative if version1 is older than version2
     * - Zero if version1 is the same as version2
     * - Positive if version1 is newer than version2
     */
    private fun compareVersions(version1: String, version2: String): Int {
        val parts1 = version1.split(".").map { it.toInt() }
        val parts2 = version2.split(".").map { it.toInt() }

        val length = Math.max(parts1.size, parts2.size)
        for (i in 0 until length) {
            val p1 = parts1.getOrElse(i) { 0 }
            val p2 = parts2.getOrElse(i) { 0 }
            if (p1 < p2) return -1
            if (p1 > p2) return 1
        }
        return 0
    }
}
