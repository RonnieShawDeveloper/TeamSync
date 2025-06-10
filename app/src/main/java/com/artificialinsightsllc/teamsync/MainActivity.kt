// In file: app/src/main/java/com/artificialinsightsllc/teamsync/MainActivity.kt
package com.artificialinsightsllc.teamsync

import android.os.Bundle
import androidx.activity.ComponentActivity
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
import androidx.compose.ui.graphics.Color // Import Color
import androidx.compose.ui.text.font.FontWeight // Import FontWeight
import androidx.compose.ui.text.style.TextAlign // Import TextAlign
import androidx.compose.foundation.layout.padding // Import padding
import androidx.compose.ui.unit.dp // Import dp
import androidx.compose.ui.unit.sp // Import sp
import android.util.Log // Import Android Log
import com.google.firebase.ktx.BuildConfig


class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            TeamSyncTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    val context = LocalContext.current // Get context for packageManager
                    var showUpdateDialog by remember { mutableStateOf(false) }

                    LaunchedEffect(Unit) {
                        val remoteConfig = Firebase.remoteConfig
                        val configSettings = remoteConfigSettings {
                            // Set a minimum fetch interval to control how often config is fetched.
                            // For development, use a low value like 0. For production, increase this (e.g., 3600L for 1 hour).
                            minimumFetchIntervalInSeconds = if (BuildConfig.DEBUG) 0 else 3600 // Fetch every hour in prod, immediately in debug
                        }
                        remoteConfig.setConfigSettingsAsync(configSettings)

                        // Set default values if remote config values are not yet fetched
                        // Using current app version as default is safer to prevent immediate update dialog
                        val defaultCurrentAppVersion = try {
                            context.packageManager.getPackageInfo(context.packageName, 0).versionName
                        } catch (e: Exception) {
                            "0.0" // Fallback if version name cannot be retrieved
                        }
                        remoteConfig.setDefaultsAsync(mapOf("version" to defaultCurrentAppVersion))

                        remoteConfig.fetchAndActivate()
                            .addOnCompleteListener { task ->
                                if (task.isSuccessful) {
                                    // Trim any whitespace from the remote config string
                                    val updatedVersion = remoteConfig.getString("version").trim()
                                    val currentAppVersion = try {
                                        // Trim any whitespace from the package manager version name
                                        context.packageManager.getPackageInfo(context.packageName, 0).versionName?.trim() ?: "0.0" // Ensure it's non-nullable
                                    } catch (e: Exception) {
                                        "0.0" // Fallback if version name cannot be retrieved
                                    }

                                    Log.d("MainActivity", "Remote Config Version: '$updatedVersion'")
                                    Log.d("MainActivity", "Current App Version: '$currentAppVersion'")

                                    // Check if remote version is newer than current app version
                                    // Assuming "version" is a string like "1.0", "1.1", "2.0"
                                    if (compareVersions(updatedVersion, currentAppVersion) > 0) {
                                        Log.d("MainActivity", "Newer version found: $updatedVersion > $currentAppVersion")
                                        showUpdateDialog = true
                                    } else {
                                        Log.d("MainActivity", "No update required: $updatedVersion <= $currentAppVersion")
                                    }
                                } else {
                                    // Log the error but allow the app to proceed if fetching fails.
                                    // This prevents users from being locked out due to network issues.
                                    // You might want to show a warning if this happens frequently.
                                    // For a critical app, you might still want to force a block.
                                    // For now, we'll allow it to proceed.
                                    Log.e("MainActivity", "Remote Config fetch failed: ${task.exception?.message}")
                                }
                            }
                    }

                    if (showUpdateDialog) {
                        AlertDialog(
                            onDismissRequest = {
                                // User cannot dismiss; they must update.
                                // Calling finish() will close the app.
                                finish()
                            },
                            title = {
                                Text(
                                    text = "Update Required",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0D47A1) // DarkBlue
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
                                        // Direct user to app store/Play Store
                                        val appPackageName = context.packageName
                                        try {
                                            context.startActivity(android.content.Intent(
                                                android.content.Intent.ACTION_VIEW,
                                                android.net.Uri.parse("market://details?id=$appPackageName")
                                            ))
                                        } catch (e: android.content.ActivityNotFoundException) {
                                            // Fallback for devices without Play Store (e.g., some custom ROMs)
                                            context.startActivity(android.content.Intent(
                                                android.content.Intent.ACTION_VIEW,
                                                android.net.Uri.parse("https://play.google.com/store/apps/details?id=$appPackageName")
                                            ))
                                        }
                                        finish() // Close the app after directing to store
                                    }
                                ) {
                                    Text("Update Now", color = Color.White)
                                }
                            },
                            containerColor = Color.White // Set the background color to white
                        )
                    } else {
                        // Only show the app content if no update is required
                        AppNavGraph(navController)
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
