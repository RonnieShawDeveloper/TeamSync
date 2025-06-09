// In file: app/src/main/java/com/artificialinsightsllc/teamsync/Screens/MarkerInfoDialog.kt
package com.artificialinsightsllc.teamsync.Screens

import android.content.Context
import android.location.Geocoder
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ListAlt // Icon for text-based report
import androidx.compose.material.icons.filled.Timeline // Icon for map-based history
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog // Required for AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import com.artificialinsightsllc.teamsync.Helpers.TimeFormatter
import com.artificialinsightsllc.teamsync.Helpers.UnitConverter
import com.artificialinsightsllc.teamsync.ui.theme.DarkBlue
import com.artificialinsightsllc.teamsync.R
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.Locale
import com.artificialinsightsllc.teamsync.Navigation.NavRoutes
import androidx.navigation.NavHostController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarkerInfoDialog(
    navController: NavHostController,
    profilePhotoUrl: String?,
    title: String,
    latLng: LatLng?,
    timestamp: Long,
    speed: Float?,
    bearing: Float?,
    onDismissRequest: () -> Unit,
    personUserId: String? = null
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var timeAgoString by remember { mutableStateOf("") }
    var currentAddress by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    val DarkBlue = Color(0xFF0D47A1)

    // State for the time range selection dialog (for Travel History)
    var showTimeRangeDialog by remember { mutableStateOf(false) }

    LaunchedEffect(timestamp, latLng) {
        launch {
            while (true) {
                timeAgoString = TimeFormatter.getRelativeTimeSpanString(timestamp).toString()
                delay(1000)
            }
        }

        if (latLng != null) {
            // Call geocodeLocation via the new LocationUtils object
            currentAddress = LocationUtils.geocodeLocation(context, latLng.latitude, latLng.longitude)
        } else {
            currentAddress = null
        }
    }

    LaunchedEffect(Unit) {
        sheetState.show()
    }

    ModalBottomSheet(
        onDismissRequest = {
            onDismissRequest()
        },
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        containerColor = Color.Transparent, // Makes the background image visible
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .background(Color.Transparent) // Ensures the background image shows through
        ) {
            // Background Image for the Modal Bottom Sheet
            Image(
                painter = painterResource(id = R.drawable.background1),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .matchParentSize()
                    .clip(
                        RoundedCornerShape(
                            topStart = 16.dp,
                            topEnd = 16.dp
                        )
                    ) // Clip to match sheet shape
            )

            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .padding(bottom = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Profile Picture
                Image(
                    painter = rememberAsyncImagePainter(
                        model = profilePhotoUrl,
                        error = painterResource(id = R.drawable.default_profile_pic)
                    ),
                    contentDescription = "Profile Picture",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(150.dp)
                        .shadow(12.dp, CircleShape, clip = false)
                        .clip(CircleShape)
                        .background(Color.LightGray)
                )

                Text(
                    text = title,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = DarkBlue,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(4.dp))

                // Address
                currentAddress?.let {
                    Text(
                        text = it,
                        fontSize = 16.sp,
                        color = DarkBlue.copy(alpha = 0.8f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                } ?: Text(
                    text = "Address: Looking up...",
                    fontSize = 14.sp,
                    color = DarkBlue.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                // Updated time
                Text(
                    text = "Updated $timeAgoString",
                    fontSize = 14.sp,
                    color = DarkBlue.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                // Speed
                speed?.let {
                    Text(
                        text = "Speed: ${
                            String.format(
                                "%.1f",
                                UnitConverter.metersPerSecondToMilesPerHour(it)
                            )
                        } MPH",
                        fontSize = 14.sp,
                        color = DarkBlue.copy(alpha = 0.8f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Direction
                bearing?.let {
                    Text(
                        text = "Direction: ${UnitConverter.getCardinalDirection(it)}",
                        fontSize = 14.sp,
                        color = DarkBlue.copy(alpha = 0.8f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(Modifier.height(16.dp))

                // Row for action buttons (4 buttons)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly, // Evenly spaced for 4 buttons
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val FAB_SIZE = 60.dp // Size of the circular buttons
                    val ICON_SIZE = 40.dp // Size of the icons inside buttons

                    // 1. Private Chat Button
                    Button(
                        onClick = {
                            Toast.makeText(
                                context,
                                "Private Chat functionality coming soon!",
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        modifier = Modifier
                            .size(FAB_SIZE)
                            .shadow(4.dp, CircleShape, clip = false),
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(containerColor = DarkBlue)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Chat,
                            contentDescription = "Private Chat",
                            tint = Color.White,
                            modifier = Modifier.size(ICON_SIZE)
                        )
                    }

                    // 2. Travel Report (Text) Button
                    Button(
                        onClick = {
                            coroutineScope.launch { sheetState.hide() }.invokeOnCompletion {
                                onDismissRequest() // Dismiss the dialog immediately after hiding
                                if (personUserId != null) {
                                    Log.d(
                                        "MarkerInfoDialog",
                                        "Navigating to text travel report for userId: $personUserId"
                                    )
                                    // Default to 24 hours for the text report
                                    navController.navigate(
                                        NavRoutes.TRAVEL_REPORT.replace(
                                            "{userId}",
                                            personUserId
                                        ).replace(
                                            "{timeRangeMillis}",
                                            (24 * 60 * 60 * 1000L).toString()
                                        )
                                    )
                                } else {
                                    Toast.makeText(
                                        context,
                                        "Cannot view report: User ID not available.",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        },
                        enabled = personUserId != null,
                        modifier = Modifier
                            .size(FAB_SIZE)
                            .shadow(4.dp, CircleShape, clip = false),
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary) // Primary color for distinction
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ListAlt,
                            contentDescription = "View Report",
                            tint = Color.White,
                            modifier = Modifier.size(ICON_SIZE)
                        )
                    }

                    // 3. Travel History (Map Plot) Button
                    Button(
                        onClick = {
                            if (personUserId != null) {
                                showTimeRangeDialog = true // Show time range selection dialog
                            } else {
                                Toast.makeText(
                                    context,
                                    "Cannot view history: User ID not available.",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        },
                        enabled = personUserId != null,
                        modifier = Modifier
                            .size(FAB_SIZE)
                            .shadow(4.dp, CircleShape, clip = false),
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary) // Secondary color for distinction
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Timeline, // Timeline icon for history
                            contentDescription = "View Travel History",
                            tint = Color.White,
                            modifier = Modifier.size(ICON_SIZE)
                        )
                    }

                    // 4. Close Button
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                sheetState.hide() // Hide the modal bottom sheet
                            }.invokeOnCompletion {
                                onDismissRequest() // Call the original dismiss request callback
                            }
                        },
                        modifier = Modifier
                            .size(FAB_SIZE)
                            .shadow(4.dp, CircleShape, clip = false),
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF800000)) // Maroon color for close
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Close",
                            tint = Color.White,
                            modifier = Modifier.size(ICON_SIZE)
                        )
                    }
                }
            }
        }

        // NEW: Time Range Selection Dialog called as a top-level composable
        if (showTimeRangeDialog) {
            // Correctly calling the top-level composable
            TimeRangeSelectionDialog(
                onDismissRequest = { showTimeRangeDialog = false },
                onTimeRangeSelected = { selectedTimeRangeMillis: Long ->
                    coroutineScope.launch { sheetState.hide() }.invokeOnCompletion {
                        onDismissRequest() // Dismiss the main MarkerInfoDialog
                        showTimeRangeDialog = false // Dismiss the time range dialog itself
                        navController.navigate(
                            NavRoutes.TRAVEL_REPORT
                                .replace("{userId}", personUserId!!)
                                .replace("{timeRangeMillis}", selectedTimeRangeMillis.toString())
                        )
                    }
                }
            )
        }
    } // End of ModalBottomSheet for MarkerInfoDialog
} // End of MarkerInfoDialog composable function


// ---
// TOP-LEVEL COMPOSABLES AND FUNCTIONS START HERE
// These functions are outside of the MarkerInfoDialog composable.
// ---

/**
 * A dialog composable for selecting a time range for travel history.
 * This is a top-level composable function.
 *
 * @param onDismissRequest Callback invoked when the dialog is dismissed (e.g., by clicking outside or cancel).
 * @param onTimeRangeSelected Callback invoked when a time range button is selected, providing the duration in milliseconds.
 */
@Composable
fun TimeRangeSelectionDialog(
    onDismissRequest: () -> Unit,
    onTimeRangeSelected: (Long) -> Unit
) {
    // These colors should ideally come from a theme, but defined locally for now if not imported.
    val DarkBlue = Color(0xFF0D47A1)
    val LightCream = Color(0xFFFFFDD0)

    val timeRanges = listOf(
        "Last Hour" to 1 * 60 * 60 * 1000L,
        "Last 3 Hours" to 3 * 60 * 60 * 1000L,
        "Last 6 Hours" to 6 * 60 * 60 * 1000L,
        "Last 12 Hours" to 12 * 60 * 60 * 1000L,
        "Last 24 Hours" to 24 * 60 * 60 * 1000L
    )

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(
                "Select History Time Range",
                color = DarkBlue,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                timeRanges.forEach { (label, duration) ->
                    Button(
                        onClick = { onTimeRangeSelected(duration) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = DarkBlue)
                    ) {
                        Text(label, color = Color.White)
                    }
                }
            }
        },
        confirmButton = {
            // No confirm button needed, selection triggers action
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Cancel", color = DarkBlue)
            }
        },
        containerColor = Color.White.copy(alpha = 0.95f), // Slightly transparent white background
        shape = RoundedCornerShape(16.dp), // Rounded corners for the dialog
        modifier = Modifier.shadow(
            8.dp,
            RoundedCornerShape(16.dp)
        ) // Shadow for floating effect
    )
}

/**
 * An object containing utility functions related to location services.
 * This helps to avoid naming conflicts for common utility functions.
 */
object LocationUtils {
    /**
     * Helper function for geocoding a given latitude and longitude into a human-readable address.
     * This is a suspend function that performs an IO operation.
     *
     * @param context The Android context, typically LocalContext.current.
     * @param latitude The latitude of the location.
     * @param longitude The longitude of the location.
     * @return The formatted address string, or an error message if geocoding fails.
     */
    suspend fun geocodeLocation(
        context: Context,
        latitude: Double,
        longitude: Double
    ): String {
        return withContext(Dispatchers.IO) {
            val geocoder = Geocoder(context, Locale.getDefault())
            try {
                if (!Geocoder.isPresent()) {
                    Log.e("Geocoding", "Geocoder is not present on this device.")
                    return@withContext "Address lookup failed (Geocoder not available)"
                }
                val addresses = geocoder.getFromLocation(latitude, longitude, 1)
                addresses?.firstOrNull()?.getAddressLine(0) ?: "Address not found"
            } catch (e: IOException) {
                Log.e("Geocoding", "Geocoding failed for $latitude, $longitude: ${e.message}")
                "Address lookup failed (Network/IO error)"
            } catch (e: IllegalArgumentException) {
                Log.e(
                    "Geocoding",
                    "Invalid LatLng for geocoding: $latitude, $longitude: ${e.message}"
                )
                "Invalid location (Coordinates error)"
            } catch (e: Exception) {
                Log.e(
                    "Geocoding",
                    "Unexpected geocoding error for $latitude, $longitude: ${e.message}"
                )
                "Address lookup failed (Unknown error)"
            }
        }
    }
}
