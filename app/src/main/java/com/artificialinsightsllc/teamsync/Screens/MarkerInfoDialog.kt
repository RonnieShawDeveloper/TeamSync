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
import androidx.compose.material.icons.filled.ListAlt
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
import androidx.navigation.NavHostController // NEW IMPORT for NavHostController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarkerInfoDialog(
    navController: NavHostController, // NEW: Added navController parameter
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


    LaunchedEffect(timestamp, latLng) {
        launch {
            while (true) {
                timeAgoString = TimeFormatter.getRelativeTimeSpanString(timestamp).toString()
                delay(1000)
            }
        }

        if (latLng != null) {
            currentAddress = geocodeLocation(context, latLng.latitude, latLng.longitude)
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

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Private Chat Button
                    Button(
                        onClick = {
                            Toast.makeText(context, "Private Chat functionality coming soon!", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(80.dp)
                            .shadow(8.dp, CircleShape, clip = false)
                            .padding(horizontal = 4.dp),
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

                    Spacer(modifier = Modifier.width(8.dp))

                    // View Travel Report Button
                    Button(
                        onClick = {
                            coroutineScope.launch { sheetState.hide() }.invokeOnCompletion {
                                onDismissRequest() // Dismiss the dialog immediately after hiding
                                if (personUserId != null) {
                                    Log.d("MarkerInfoDialog", "Navigating to travel report for userId: $personUserId")
                                    // Navigate using the passed navController
                                    navController.navigate(NavRoutes.TRAVEL_REPORT.replace("{userId}", personUserId))
                                } else {
                                    Toast.makeText(context, "Cannot view report: User ID not available.", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        enabled = personUserId != null,
                        modifier = Modifier
                            .weight(1f)
                            .height(80.dp)
                            .shadow(8.dp, CircleShape, clip = false)
                            .padding(horizontal = 4.dp),
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ListAlt,
                            contentDescription = "View Report",
                            tint = Color.White,
                            modifier = Modifier.size(40.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Close Button
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                sheetState.hide()
                            }.invokeOnCompletion {
                                onDismissRequest()
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(80.dp)
                            .shadow(8.dp, CircleShape, clip = false)
                            .padding(horizontal = 4.dp),
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

private suspend fun geocodeLocation(context: Context, latitude: Double, longitude: Double): String {
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
            Log.e("Geocoding", "Invalid LatLng for geocoding: $latitude, $longitude: ${e.message}")
            "Invalid location (Coordinates error)"
        } catch (e: Exception) {
            Log.e("Geocoding", "Unexpected geocoding error for $latitude, $longitude: ${e.message}")
            "Address lookup failed (Unknown error)"
        }
    }
}
