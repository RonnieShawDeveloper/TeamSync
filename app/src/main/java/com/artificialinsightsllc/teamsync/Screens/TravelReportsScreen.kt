// In file: app/src/main/java/com/artificialinsightsllc/teamsync/Screens/TravelReportScreen.kt
package com.artificialinsightsllc.teamsync.Screens

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf // NEW: For live polyline updates
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.navigation.NavHostController
import com.artificialinsightsllc.teamsync.Services.FirestoreService
import com.artificialinsightsllc.teamsync.R
import androidx.compose.foundation.layout.systemBarsPadding
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

import com.artificialinsightsllc.teamsync.ui.theme.DarkBlue
import com.artificialinsightsllc.teamsync.ui.theme.LightCream

import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Marker
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberMarkerState
import com.google.maps.android.compose.Polyline
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.LatLngBounds
import android.location.Location
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.lerp
import com.artificialinsightsllc.teamsync.Models.Locations
import com.artificialinsightsllc.teamsync.Helpers.UnitConverter


class TravelReportScreen(private val navController: NavHostController, private val userId: String, private val timeRangeMillis: Long) {

    // Data class to represent a polyline segment with its color
    private data class PolylineSegment(
        val points: List<LatLng>,
        val color: Color,
        val speedMph: Double // Store speed for debugging/info if needed
    )

    @Composable
    fun Content() {
        val context = LocalContext.current
        val firestoreService = remember { FirestoreService() }
        val coroutineScope = rememberCoroutineScope()
        val DarkBlue = Color(0xFF0D47A1)
        val LightCream = Color(0xFFFFFDD0)

        // `isProcessing` controls the visibility of the overlay, not the map itself
        var isProcessing by remember { mutableStateOf(true) }
        val polylineSegments = remember { mutableStateListOf<PolylineSegment>() } // Now a mutable list for live updates
        val mapMarkers = remember { mutableStateListOf<LatLng>() } // Now a mutable list for live updates
        var errorMessage by remember { mutableStateOf<String?>(null) }
        var showScreen by remember { mutableStateOf(false) } // For animation visibility

        // Progress states for the overlay
        var totalLocations by remember { mutableIntStateOf(0) }
        var processedLocations by remember { mutableIntStateOf(0) }
        var processingMessage by remember { mutableStateOf("Fetching location data...") }
        var progress by remember { mutableFloatStateOf(0f) }


        val cameraPositionState = rememberCameraPositionState {
            position = CameraPosition.fromLatLngZoom(LatLng(0.0, 0.0), 10f) // Default, will be updated
        }

        // LaunchedEffect for data fetching and progressive plotting
        LaunchedEffect(userId, timeRangeMillis) {
            Log.d("TravelReportScreen", "LaunchedEffect: Data fetching and plotting started for userId: $userId, timeRange: $timeRangeMillis")
            showScreen = true // Trigger slide-in animation
            isProcessing = true // Show overlay
            errorMessage = null
            polylineSegments.clear() // Clear any old data
            mapMarkers.clear() // Clear any old data
            totalLocations = 0
            processedLocations = 0
            processingMessage = "Fetching location data..."
            progress = 0f


            try {
                val endTime = System.currentTimeMillis()
                val startTime = endTime - timeRangeMillis

                val locationsResult = firestoreService.getLocationsHistoryForUser(userId, startTime, endTime)

                if (locationsResult.isSuccess) {
                    val rawLocations = locationsResult.getOrNull() ?: emptyList()
                    totalLocations = rawLocations.size

                    if (rawLocations.isEmpty()) {
                        errorMessage = "No location history found for the selected period."
                        Log.d("TravelReportScreen", "No location history for user $userId for period $timeRangeMillis ms.")
                        processingMessage = "No data found."
                    } else {
                        Log.d("TravelReportScreen", "Fetched ${rawLocations.size} raw locations. Starting live plotting...")
                        processingMessage = "Plotting locations..."

                        val boundsBuilder = LatLngBounds.builder()

                        // Add start marker immediately and center camera on it if it's the only point
                        val firstLoc = rawLocations.firstOrNull()
                        firstLoc?.let {
                            val startLatLng = LatLng(it.latitude, it.longitude)
                            mapMarkers.add(startLatLng) // Add to live map markers
                            boundsBuilder.include(startLatLng)
                            // If there's only one point, center on it immediately
                            if (rawLocations.size == 1) {
                                cameraPositionState.animate(
                                    CameraUpdateFactory.newLatLngZoom(startLatLng, 15f)
                                )
                                processingMessage = "Single location plotted."
                            }
                        }

                        // Loop through locations to progressively add polylines
                        for (i in 0 until rawLocations.size - 1) {
                            val loc1 = rawLocations[i]
                            val loc2 = rawLocations[i + 1]

                            val latLng1 = LatLng(loc1.latitude, loc1.longitude)
                            val latLng2 = LatLng(loc2.latitude, loc2.longitude)

                            val distance = FloatArray(1)
                            Location.distanceBetween(loc1.latitude, loc1.longitude, loc2.latitude, loc2.longitude, distance)
                            val segmentDistanceMeters = distance[0].toDouble()

                            val timeDiffMillis = (loc2.timestamp - loc1.timestamp).toDouble()

                            val speedMps = if (timeDiffMillis > 1000) (segmentDistanceMeters / (timeDiffMillis / 1000.0)) else 0.0
                            val speedMph = UnitConverter.metersPerSecondToMilesPerHour(speedMps.toFloat()).toDouble()

                            val segmentColor = getSpeedColor(speedMph.toFloat())

                            // Add segment to mutableStateListOf directly
                            polylineSegments.add(
                                PolylineSegment(
                                    points = listOf(latLng1, latLng2),
                                    color = segmentColor,
                                    speedMph = speedMph
                                )
                            )
                            boundsBuilder.include(latLng1)
                            boundsBuilder.include(latLng2)

                            // Update progress
                            processedLocations = i + 1
                            progress = processedLocations.toFloat() / (totalLocations - 1).toFloat().coerceAtLeast(1f) // Avoid division by zero for totalLocations=1
                            processingMessage = "Plotting locations (${processedLocations}/${totalLocations-1})..."

                            // Small delay for visual effect of plotting
                            if (processedLocations % 20 == 0 || processedLocations == totalLocations - 1) { // Update frequently or at end
                                delay(10) // Allow UI to recompose
                            }
                        }

                        // Add end marker after all polylines are added
                        val lastLoc = rawLocations.lastOrNull()
                        if (lastLoc != null && rawLocations.size > 1) {
                            val endLatLng = LatLng(lastLoc.latitude, lastLoc.longitude)
                            mapMarkers.add(endLatLng) // Add to live map markers
                            boundsBuilder.include(endLatLng)
                        }

                        // Final camera animation to fit all points after all segments are added
                        if (rawLocations.size > 1) { // Only animate to bounds if there's more than one point
                            val bounds = boundsBuilder.build()
                            cameraPositionState.animate(
                                CameraUpdateFactory.newLatLngBounds(bounds, 100) // 100 padding in pixels
                            )
                            Log.d("TravelReportScreen", "Final camera animation to fit bounds.")
                        } else if (rawLocations.size == 1) {
                            // Already centered on single point above
                        } else {
                            errorMessage = "No movement data found for this period to plot on map."
                        }
                    }
                } else {
                    errorMessage = "Failed to load location history: ${locationsResult.exceptionOrNull()?.message}"
                    Log.e("TravelReportScreen", "Error fetching location history: ${locationsResult.exceptionOrNull()?.message}")
                    Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
                    processingMessage = "Error fetching data."
                }
            } catch (e: Exception) {
                errorMessage = "An unexpected error occurred during plotting: ${e.message}"
                Log.e("TravelReportScreen", "Unexpected error during plotting: ${e.message}", e)
                Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
                processingMessage = "An error occurred."
            } finally {
                // Ensure all progress is finalized before hiding the overlay
                processedLocations = totalLocations
                progress = 1f
                processingMessage = "Done."
                delay(500) // Small delay to show "Done." message briefly
                isProcessing = false // Hide overlay
                Log.d("TravelReportScreen", "Plotting complete. isProcessing set to FALSE.")
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(WindowInsets.systemBars.asPaddingValues()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
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
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = DarkBlue)
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Travel History",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = DarkBlue,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(48.dp))
            }

            Spacer(Modifier.height(16.dp))

            // Map Section (Always visible, progress overlay on top)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 8.dp)
                    .shadow(4.dp, RoundedCornerShape(12.dp)),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = LightCream.copy(alpha = 0.9f))
            ) {
                Box(modifier = Modifier.fillMaxSize()) { // Use a Box to layer Map and Overlay
                    GoogleMap(
                        modifier = Modifier.fillMaxSize(),
                        cameraPositionState = cameraPositionState
                    ) {
                        // Draw all polyline segments
                        polylineSegments.forEach { segment ->
                            Polyline(
                                points = segment.points,
                                color = segment.color,
                                width = 10f // Line width
                            )
                        }
                        // Add start and end markers
                        if (mapMarkers.isNotEmpty()) {
                            Marker(
                                state = rememberMarkerState(position = mapMarkers.first()),
                                title = "Start",
                                snippet = "Beginning of trip",
                                icon = BitmapDescriptorFactory.defaultMarker(
                                    BitmapDescriptorFactory.HUE_GREEN
                                )
                            )
                            if (mapMarkers.size > 1) { // Only add end marker if there's more than one point
                                Marker(
                                    state = rememberMarkerState(position = mapMarkers.last()),
                                    title = "End",
                                    snippet = "End of trip",
                                    icon = BitmapDescriptorFactory.defaultMarker(
                                        BitmapDescriptorFactory.HUE_RED
                                    )
                                )
                            }
                        }
                    }

                    // Progress / Error Overlay
                    if (isProcessing || errorMessage != null || (polylineSegments.isEmpty() && mapMarkers.isEmpty())) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(LightCream.copy(alpha = 0.8f)), // Semi-transparent background
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                if (isProcessing) {
                                    CircularProgressIndicator(color = DarkBlue)
                                    Spacer(Modifier.height(16.dp))
                                    Text(
                                        text = processingMessage,
                                        color = DarkBlue.copy(alpha = 0.8f),
                                        fontSize = 16.sp,
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    if (totalLocations > 0 && progress < 1f) {
                                        LinearProgressIndicator(
                                            progress = progress,
                                            modifier = Modifier.fillMaxWidth(0.8f),
                                            color = DarkBlue,
                                            trackColor = DarkBlue.copy(alpha = 0.3f)
                                        )
                                    }
                                } else if (errorMessage != null) {
                                    Text(
                                        text = errorMessage ?: "No data to display.",
                                        color = MaterialTheme.colorScheme.error,
                                        fontSize = 16.sp,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(16.dp)
                                    )
                                } else if (polylineSegments.isEmpty() && mapMarkers.isEmpty()) {
                                    Text(
                                        text = "No movement data found for this period to plot on map.",
                                        color = DarkBlue.copy(alpha = 0.7f),
                                        fontSize = 16.sp,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Helper function to get color based on speed (MPH)
    private fun getSpeedColor(speedMph: Float): Color {
        // Define key speeds and their corresponding colors
        val greenSpeed = 5f    // Green up to 5 MPH
        val yellowSpeed = 50f  // Yellow at 50 MPH
        val redSpeed = 100f    // Red at 100 MPH

        return when {
            speedMph <= greenSpeed -> Color.Green
            speedMph >= redSpeed -> Color.Red
            speedMph <= yellowSpeed -> {
                // Interpolate between Green and Yellow
                val fraction = (speedMph - greenSpeed) / (yellowSpeed - greenSpeed)
                lerp(Color.Green, Color.Yellow, fraction.coerceIn(0f, 1f))
            }
            else -> {
                // Interpolate between Yellow and Red
                val fraction = (speedMph - yellowSpeed) / (redSpeed - yellowSpeed)
                lerp(Color.Yellow, Color.Red, fraction.coerceIn(0f, 1f))
            }
        }
    }
}
