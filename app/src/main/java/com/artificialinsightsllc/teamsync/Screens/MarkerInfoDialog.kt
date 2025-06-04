// In file: app/src/main/java/com/artificialinsightsllc/teamsync/Screens/MarkerInfoDialog.kt
package com.artificialinsightsllc.teamsync.Screens

import android.content.Context
import android.location.Geocoder
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
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.MaterialTheme
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
import com.artificialinsightsllc.teamsync.R
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarkerInfoDialog(
    profilePhotoUrl: String?,
    title: String,
    latLng: LatLng?, // New: Pass LatLng for geocoding
    timestamp: Long,
    speed: Float?,
    bearing: Float?,
    onDismissRequest: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var timeAgoString by remember { mutableStateOf("") }
    var currentAddress by remember { mutableStateOf<String?>(null) } // State for geocoded address
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    // LaunchedEffect to update the timeAgoString and perform geocoding
    LaunchedEffect(timestamp, latLng) {
        // Update timeAgoString every second
        launch {
            while (true) {
                timeAgoString = TimeFormatter.getRelativeTimeSpanString(timestamp).toString()
                delay(1000)
            }
        }

        // Perform geocoding when latLng changes
        if (latLng != null) {
            currentAddress = geocodeLocation(context, latLng.latitude, latLng.longitude)
        } else {
            currentAddress = null
        }
    }

    LaunchedEffect(Unit) {
        sheetState.show() // Automatically show the bottom sheet when composed
    }

    ModalBottomSheet(
        onDismissRequest = {
            onDismissRequest() // Call the dismiss callback when the sheet is dismissed
        },
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        // Apply the background image to the container
        containerColor = Color.Transparent, // Make container transparent to show the image
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth() // Fill the width of the modal area
                .wrapContentHeight() // Make height wrap content
                .background(Color.Transparent) // Ensure Box itself is transparent
        ) {
            // Background Image for the ModalBottomSheet
            Image(
                painter = painterResource(id = R.drawable.background1),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .matchParentSize() // Fill the size of the parent Box
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)) // Clip to match sheet corners
            )

            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .padding(bottom = 8.dp), // Added 8.dp bottom padding
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Profile Picture
                Image(
                    painter = rememberAsyncImagePainter(
                        model = profilePhotoUrl,
                        error = painterResource(id = R.drawable.default_profile_pic) // Fallback image
                    ),
                    contentDescription = "Profile Picture",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(150.dp)
                        .shadow(12.dp, CircleShape, clip = false) // Added shadow modifier
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
                    text = "Address: Looking up...", // Show a loading message for address
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
                        text = "Speed: ${String.format("%.1f", UnitConverter.metersPerSecondToMilesPerHour(it))} MPH",
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

                // Row for the two action buttons at the bottom
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Private Chat Button (Bottom Left)
                    Button(
                        onClick = {
                            Toast.makeText(context, "Private Chat functionality coming soon!", Toast.LENGTH_SHORT).show()
                            // TODO: Navigate to private chat screen
                        },
                        modifier = Modifier
                            .size(80.dp) // Increased button size
                            .shadow(8.dp, CircleShape, clip = false),
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(containerColor = DarkBlue)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Chat,
                            contentDescription = "Private Chat",
                            tint = Color.White,
                            modifier = Modifier.size(40.dp) // Icon size adjusted
                        )
                    }

                    // Close Button (Bottom Right)
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                sheetState.hide() // Hide the sheet with animation
                            }.invokeOnCompletion {
                                onDismissRequest() // Call dismiss callback after animation
                            }
                        },
                        modifier = Modifier
                            .size(80.dp) // Increased button size
                            .shadow(8.dp, CircleShape, clip = false),
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF800000)) // Deep red/maroon
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Close",
                            tint = Color.White,
                            modifier = Modifier.size(40.dp) // Icon size adjusted
                        )
                    }
                }
                // Removed the final Spacer(Modifier.height(8.dp)) here as padding is applied to column
            }
        }
    }
}

// Helper function for geocoding a location to an address string
private suspend fun geocodeLocation(context: Context, latitude: Double, longitude: Double): String {
    return withContext(Dispatchers.IO) { // Perform network/IO operation on Dispatchers.IO
        val geocoder = Geocoder(context, Locale.getDefault())
        try {
            val addresses = geocoder.getFromLocation(latitude, longitude, 1)
            addresses?.firstOrNull()?.getAddressLine(0) ?: "Address not found"
        } catch (e: IOException) {
            // Log the error for debugging
            println("Geocoding failed for $latitude, $longitude: ${e.message}")
            "Address lookup failed" // Message for network/IO errors
        } catch (e: IllegalArgumentException) {
            // Log the error for debugging
            println("Invalid LatLng for geocoding: $latitude, $longitude: ${e.message}")
            "Invalid location" // Message for invalid coordinates
        }
    }
}
