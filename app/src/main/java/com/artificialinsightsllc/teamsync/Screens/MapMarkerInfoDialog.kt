// In file: app/src/main/java/com/artificialinsightsllc/teamsync/Screens/MapMarkerInfoDialog.kt
package com.artificialinsightsllc.teamsync.Screens

import android.content.Context
import android.location.Geocoder
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.artificialinsightsllc.teamsync.Models.GroupMembers
import com.artificialinsightsllc.teamsync.Models.MapMarker
import com.artificialinsightsllc.teamsync.Models.UserModel
import com.artificialinsightsllc.teamsync.R
import com.artificialinsightsllc.teamsync.ui.theme.DarkBlue
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapMarkerInfoDialog(
    mapMarker: MapMarker,
    onDismissRequest: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val coroutineScope = rememberCoroutineScope()
    var timeAgoString by remember { mutableStateOf("") }
    var currentAddress by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val DarkBlue = Color(0xFF0D47A1)
    var creatorName by remember { mutableStateOf("Loading...") }
    var canDelete by remember { mutableStateOf(false) }
    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid

    LaunchedEffect(mapMarker.userId, currentUserId) {
        if (currentUserId == null) return@LaunchedEffect

        val db = FirebaseFirestore.getInstance()
        // Check if the current user is the creator
        if (mapMarker.userId == currentUserId) {
            canDelete = true
        } else {
            // Check if the current user is an ADMIN or OWNER of the group
            try {
                val memberDoc = db.collection("groups").document(mapMarker.groupId)
                    .collection("members").document(currentUserId).get().await()

                if (memberDoc.exists()) {
                    val member = memberDoc.toObject(GroupMembers::class.java)
                    if (member?.memberRole.toString() == "ADMIN" || member?.memberRole.toString() == "OWNER") {
                        canDelete = true
                    }
                }
            } catch (e: Exception) {
                Log.e("MapMarkerInfo", "Error checking delete permission: ${e.message}")
            }
        }
    }

    LaunchedEffect(mapMarker.userId) {
        val db = FirebaseFirestore.getInstance()
        try {
            val userDoc = db.collection("users").document(mapMarker.userId).get().await()
            creatorName = if (userDoc.exists()) {
                userDoc.toObject(UserModel::class.java)?.displayName ?: "Unknown Member"
            } else {
                "Unknown User"
            }
        } catch (e: Exception) {
            Log.e("MapMarkerInfo", "Error fetching creator name: ${e.message}")
            creatorName = "Unknown Member"
        }
    }

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
        onDismissRequest = onDismissRequest,
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
                if (!mapMarker.photoUrl.isNullOrEmpty()) {
                    Image(
                        painter = rememberAsyncImagePainter(model = mapMarker.photoUrl),
                        contentDescription = "Marker Image",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                }

                Text(
                    text = mapMarker.message,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = DarkBlue,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(4.dp))

                currentAddress?.let {
                    Text(
                        text = it,
                        fontSize = 16.sp,
                        color = DarkBlue.copy(alpha = 0.8f),
                        textAlign = TextAlign.Center
                    )
                } ?: Text(
                    text = "Address: Looking up...",
                    fontSize = 14.sp,
                    color = DarkBlue.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "Created by: $creatorName",
                    fontSize = 14.sp,
                    color = DarkBlue.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "Created $timeAgoString",
                    fontSize = 14.sp,
                    color = DarkBlue.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val FAB_SIZE = 60.dp
                    val ICON_SIZE = 40.dp

                    // Delete Button (conditionally displayed)
                    if (canDelete) {
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    try {
                                        Log.d("MapMarkerInfo", "Deleting marker: ${mapMarker.id}")
                                        FirebaseFirestore.getInstance()
                                            .collection("mapMarkers").document(mapMarker.id)
                                            .delete().await()
                                        Toast.makeText(context, "Marker deleted", Toast.LENGTH_SHORT).show()
                                        sheetState.hide()
                                        onDismissRequest()
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Failed to delete marker", Toast.LENGTH_SHORT).show()
                                        Log.e("MapMarkerInfo", "Error deleting marker: ${e.message}")
                                    }
                                }
                            },
                            modifier = Modifier
                                .size(FAB_SIZE)
                                .shadow(4.dp, CircleShape, clip = false),
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Delete,
                                contentDescription = "Delete Marker",
                                tint = Color.White,
                                modifier = Modifier.size(ICON_SIZE)
                            )
                        }
                    }

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
                            .size(FAB_SIZE)
                            .shadow(4.dp, CircleShape, clip = false),
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF800000)),
                        contentPadding = PaddingValues(0.dp)
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
    }
}

private suspend fun geocodeLocation(context: Context, latitude: Double, longitude: Double): String {
    return withContext(Dispatchers.IO) {
        val geocoder = Geocoder(context, Locale.getDefault())
        try {
            if (!Geocoder.isPresent()) {
                Log.e("Geocoding", "Geocoder is not present.")
                return@withContext "Address lookup failed"
            }
            val addresses = geocoder.getFromLocation(latitude, longitude, 1)
            addresses?.firstOrNull()?.getAddressLine(0) ?: "Address not found"
        } catch (e: IOException) {
            Log.e("Geocoding", "Geocoding failed: ${e.message}")
            "Address lookup failed"
        } catch (e: Exception) {
            Log.e("Geocoding", "Unexpected geocoding error: ${e.message}")
            "Address lookup failed"
        }
    }
}
