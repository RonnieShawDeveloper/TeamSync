// In file: app/src/main/java/com/artificialinsightsllc/teamsync/Screens/MapMarkerInfoDialog.kt
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
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat // Corrected to AutoMirrored
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
import com.artificialinsightsllc.teamsync.Models.MapMarker
import com.artificialinsightsllc.teamsync.Models.MapMarkerType
import com.artificialinsightsllc.teamsync.ui.theme.DarkBlue
import com.artificialinsightsllc.teamsync.ui.theme.LightCream
import com.artificialinsightsllc.teamsync.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapMarkerInfoDialog(
    mapMarker: MapMarker, // Now accepts a MapMarker object
    onDismissRequest: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var timeAgoString by remember { mutableStateOf("") }
    var currentAddress by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    // LaunchedEffect to update the timeAgoString and perform geocoding
    LaunchedEffect(mapMarker.timestamp, mapMarker.latitude, mapMarker.longitude) {
        launch {
            while (true) {
                timeAgoString = TimeFormatter.getRelativeTimeSpanString(mapMarker.timestamp).toString()
                delay(1000)
            }
        }

        currentAddress = geocodeLocation(context, mapMarker.latitude, mapMarker.longitude)
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
        containerColor = Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .background(Color.Transparent)
        ) {
            Image(
                painter = painterResource(id = R.drawable.background1),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
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
                // Display content based on marker type
                when (mapMarker.markerType) {
                    MapMarkerType.PHOTO -> {
                        mapMarker.photoUrl?.let { url ->
                            Image(
                                painter = rememberAsyncImagePainter(
                                    model = url,
                                    error = painterResource(id = R.drawable.no_image) // Fallback
                                ),
                                contentDescription = "Photo Marker",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(200.dp) // Larger size for photo
                                    .clip(RoundedCornerShape(8.dp))
                                    .shadow(8.dp, RoundedCornerShape(8.dp), clip = false)
                                    .background(Color.LightGray)
                            )
                        } ?: Image(
                            painter = painterResource(id = R.drawable.no_image),
                            contentDescription = "No Image Available",
                            modifier = Modifier.size(120.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = mapMarker.message,
                            fontSize = 16.sp,
                            color = DarkBlue.copy(alpha = 0.9f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    MapMarkerType.CHAT -> {
                        // Chat icon or just message, depending on preference
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Chat,
                            contentDescription = "Chat Marker",
                            tint = DarkBlue,
                            modifier = Modifier.size(60.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = mapMarker.message,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium,
                            color = DarkBlue,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Common info for all marker types
                currentAddress?.let {
                    Text(
                        text = it,
                        fontSize = 14.sp,
                        color = DarkBlue.copy(alpha = 0.8f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                } ?: Text(
                    text = "Address: Looking up...",
                    fontSize = 12.sp,
                    color = DarkBlue.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "Posted $timeAgoString",
                    fontSize = 12.sp,
                    color = DarkBlue.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                mapMarker.cameraBearing?.let {
                    Text(
                        text = "Camera Dir: ${UnitConverter.getCardinalDirection(it)}",
                        fontSize = 12.sp,
                        color = DarkBlue.copy(alpha = 0.8f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(Modifier.height(16.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            Toast.makeText(context, "Private Chat functionality coming soon!", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier
                            .size(80.dp)
                            .shadow(8.dp, CircleShape, clip = false),
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(containerColor = DarkBlue)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Chat,
                            contentDescription = "Private Chat",
                            tint = Color.White,
                            modifier = Modifier.size(40.dp)
                        )
                    }

                    Button(
                        onClick = {
                            coroutineScope.launch {
                                sheetState.hide()
                            }.invokeOnCompletion {
                                onDismissRequest()
                            }
                        },
                        modifier = Modifier
                            .size(80.dp)
                            .shadow(8.dp, CircleShape, clip = false),
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF800000))
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Close",
                            tint = Color.White,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }
            }
        }
    }
}

// Helper function for geocoding a location to an address string (copied from MarkerInfoDialog)
private suspend fun geocodeLocation(context: Context, latitude: Double, longitude: Double): String {
    return withContext(Dispatchers.IO) {
        val geocoder = Geocoder(context, Locale.getDefault())
        try {
            val addresses = geocoder.getFromLocation(latitude, longitude, 1)
            addresses?.firstOrNull()?.getAddressLine(0) ?: "Address not found"
        } catch (e: IOException) {
            Log.e("MapMarkerInfoDialog", "Geocoding failed for $latitude, $longitude: ${e.message}")
            "Address lookup failed"
        } catch (e: IllegalArgumentException) {
            Log.e("MapMarkerInfoDialog", "Invalid LatLng for geocoding: $latitude, $longitude: ${e.message}")
            "Invalid location"
        }
    }
}
