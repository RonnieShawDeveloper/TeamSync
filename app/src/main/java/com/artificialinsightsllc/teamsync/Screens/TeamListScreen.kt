// In file: app/src/main/java/com/artificialinsightsllc/teamsync/Screens/TeamListScreen.kt
package com.artificialinsightsllc.teamsync.Screens

import android.content.Context
import android.location.Geocoder
import android.util.Log
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues // Correct import for PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack // Using AutoMirrored
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import coil.compose.rememberAsyncImagePainter
import com.artificialinsightsllc.teamsync.Helpers.TimeFormatter
import com.artificialinsightsllc.teamsync.Models.GroupMembers
import com.artificialinsightsllc.teamsync.Models.Groups
import com.artificialinsightsllc.teamsync.Models.Locations
import com.artificialinsightsllc.teamsync.Models.UserModel
import com.artificialinsightsllc.teamsync.R
import com.artificialinsightsllc.teamsync.Services.FirestoreService
import com.artificialinsightsllc.teamsync.Services.GroupMonitorService
import com.artificialinsightsllc.teamsync.ui.theme.DarkBlue
import com.artificialinsightsllc.teamsync.ui.theme.LightCream
import com.artificialinsightsllc.teamsync.TeamSyncApplication
import com.artificialinsightsllc.teamsync.Helpers.UnitConverter
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.Locale

class TeamListScreen(private val navController: NavHostController) {

    // Data class to combine all necessary info for a single team member in the list
    data class TeamMemberDisplayInfo(
        val userId: String,
        val displayName: String,
        val profilePhotoUrl: String?,
        var currentAddress: String?, // Nullable as geocoding is asynchronous
        val speed: Float?,
        val bearing: Float?,
        val lastUpdateTimestamp: Long,
        val latLng: LatLng? // Added LatLng for passing to MarkerInfoDialog
    )

    @Composable
    fun Content() {
        val context = LocalContext.current
        val coroutineScope = rememberCoroutineScope()
        val firestoreService = remember { FirestoreService() }
        val groupMonitorService = (context.applicationContext as TeamSyncApplication).groupMonitorService

        val activeGroup by groupMonitorService.activeGroup.collectAsStateWithLifecycle()
        // Get current user ID from GroupMonitorService, assuming it's available via auth.currentUser
        val currentUserId = groupMonitorService.auth.currentUser?.uid

        var teamMembersToDisplay by remember { mutableStateOf<List<TeamMemberDisplayInfo>>(emptyList()) }
        var isLoading by remember { mutableStateOf(true) }
        var showScreen by remember { mutableStateOf(false) } // For animation visibility

        // Placeholder for dialog when a member row is clicked
        var showMemberOptionsDialog by remember { mutableStateOf(false) }
        var selectedMemberForOptions: TeamMemberDisplayInfo? by remember { mutableStateOf(null) }

        // Generic Alert Dialog state for errors/messages
        var showAlertDialog by remember { mutableStateOf(false) }
        var alertDialogTitle by remember { mutableStateOf("") }
        var alertDialogMessage by remember { mutableStateOf("") }


        LaunchedEffect(Unit) {
            showScreen = true // Trigger slide-in animation
            isLoading = true

            val groupId = activeGroup?.groupID
            if (groupId == null) {
                alertDialogTitle = "No Active Group"
                alertDialogMessage = "Please select or create an active group to view team members."
                showAlertDialog = true
                isLoading = false
                return@LaunchedEffect
            }

            try {
                // 1. Get all active group memberships for the current group
                val groupMembershipsResult = firestoreService.getGroupMembershipsForGroup(groupId)
                val allActiveMemberships = groupMembershipsResult.getOrNull()
                    ?.filter { it.unjoinedTimestamp == null } ?: emptyList()
                Log.d("TeamListScreen", "Fetched ${allActiveMemberships.size} active group memberships for group: $groupId.")

                if (allActiveMemberships.isEmpty()) {
                    teamMembersToDisplay = emptyList()
                    isLoading = false
                    return@LaunchedEffect
                }

                // 2. Collect all unique user IDs from these active members
                val userIdsInGroup = allActiveMemberships.map { it.userId }.distinct()
                Log.d("TeamListScreen", "Unique user IDs in group: ${userIdsInGroup.size}")

                // 3. Fetch all UserModels for these user IDs
                val userProfilesMap = mutableMapOf<String, UserModel>()
                // Firestore 'whereIn' query has a limit of 10, so chunk the user IDs
                userIdsInGroup.chunked(10).forEach { chunk ->
                    firestoreService.getUserProfilesByIds(chunk).onSuccess { profiles ->
                        profiles.forEach { profile ->
                            userProfilesMap[profile.userId] = profile
                        }
                    }.onFailure { e ->
                        Log.e("TeamListScreen", "Failed to fetch user profiles for chunk: ${e.message}")
                    }
                }
                Log.d("TeamListScreen", "Fetched ${userProfilesMap.size} user profiles.")

                // 4. Fetch last known locations for these user IDs
                val locationsMap = mutableMapOf<String, Locations>()
                userIdsInGroup.chunked(10).forEach { chunk ->
                    firestoreService.getCurrentLocationsByIds(chunk).onSuccess { locations ->
                        locations.forEach { location ->
                            locationsMap[location.userId] = location
                        }
                    }.onFailure { e ->
                        Log.e("TeamListScreen", "Failed to fetch locations for chunk: ${e.message}")
                    }
                }
                Log.d("TeamListScreen", "Fetched ${locationsMap.size} current locations.")


                // 5. Combine data into TeamMemberDisplayInfo list, geocode asynchronously
                val compiledList = mutableListOf<TeamMemberDisplayInfo>()

                allActiveMemberships.forEach { memberShip ->
                    val profile = userProfilesMap[memberShip.userId]
                    val location = locationsMap[memberShip.userId]

                    if (profile != null) {
                        compiledList.add(
                            TeamMemberDisplayInfo(
                                userId = memberShip.userId,
                                displayName = profile.displayName,
                                profilePhotoUrl = profile.profilePhotoUrl,
                                currentAddress = null, // Will be filled by geocoding
                                speed = location?.speed,
                                bearing = location?.bearing,
                                lastUpdateTimestamp = location?.timestamp ?: 0L,
                                latLng = location?.let { LatLng(it.latitude, it.longitude) }
                            )
                        )
                    } else {
                        Log.w("TeamListScreen", "Profile not found for membership: ${memberShip.userId}. Skipping.")
                    }
                }

                // Sort by display name, and put current user first if they are in the list
                teamMembersToDisplay = compiledList
                    .sortedWith(compareBy<TeamMemberDisplayInfo> { it.userId != currentUserId }
                        .thenBy { it.displayName }) // Sort others alphabetically

                isLoading = false

                // Asynchronously geocode addresses for all displayed members
                teamMembersToDisplay.forEachIndexed { index, memberInfo ->
                    val location = locationsMap[memberInfo.userId]
                    if (location != null && memberInfo.currentAddress == null) { // Only geocode if location available and address not yet set
                        coroutineScope.launch(Dispatchers.IO) {
                            val address = geocodeLocation(context, location.latitude, location.longitude)
                            // Update the state directly or through a copy to trigger recomposition
                            val updatedList = teamMembersToDisplay.toMutableList()
                            val itemIndex = updatedList.indexOfFirst { it.userId == memberInfo.userId }
                            if (itemIndex != -1) {
                                updatedList[itemIndex] = updatedList[itemIndex].copy(currentAddress = address)
                                teamMembersToDisplay = updatedList // Re-assign to trigger recomposition
                            }
                        }
                    } else if (location == null && memberInfo.currentAddress == null) {
                        // If no location data, set address to a default message
                        val updatedList = teamMembersToDisplay.toMutableList()
                        val itemIndex = updatedList.indexOfFirst { it.userId == memberInfo.userId }
                        if (itemIndex != -1) {
                            updatedList[itemIndex] = updatedList[itemIndex].copy(currentAddress = "Location unknown")
                            teamMembersToDisplay = updatedList
                        }
                    }
                }

            } catch (e: Exception) {
                Log.e("TeamListScreen", "Error fetching or processing team data: ${e.message}", e)
                alertDialogTitle = "Error Loading Team"
                alertDialogMessage = "Failed to load team members: ${e.message}"
                showAlertDialog = true
                teamMembersToDisplay = emptyList()
            } finally {
                isLoading = false
            }
        }

        // Screen animation (Slide in from right, scale in)
        AnimatedVisibility(
            visible = showScreen,
            enter = slideInHorizontally(animationSpec = tween(durationMillis = 500, delayMillis = 50)) { fullWidth ->
                fullWidth // Start from right
            } + scaleIn(animationSpec = tween(500, delayMillis = 50), initialScale = 0.8f) + fadeIn(animationSpec = tween(500, delayMillis = 50)),
            exit = slideOutHorizontally(animationSpec = tween(durationMillis = 500)) { fullWidth ->
                fullWidth // Exit to right
            } + scaleOut(animationSpec = tween(500), targetScale = 0.8f) + fadeOut(animationSpec = tween(500))
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Image(
                    painter = painterResource(id = R.drawable.background1),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(WindowInsets.systemBars.asPaddingValues())
                        .padding(8.dp), // Overall padding for the column
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Start
                    ) {
                        IconButton(onClick = {
                            coroutineScope.launch {
                                showScreen = false // Trigger slide-out animation
                                delay(550) // Wait for animation to complete
                                navController.popBackStack()
                            }
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to Map", tint = DarkBlue)
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Group's Members",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = DarkBlue,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f)
                        )

                        Spacer(Modifier.width(48.dp)) // To balance the back button
                    }

                    Spacer(Modifier.height(16.dp))

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f), // Card takes remaining height
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.4f))
                    ) {
                        if (isLoading) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = DarkBlue)
                            }
                        } else if (teamMembersToDisplay.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    text = "No active team members found in your current group.",
                                    color = DarkBlue.copy(alpha = 0.7f),
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = PaddingValues( // Define PaddingValues directly
                                    start = 8.dp,
                                    end = 8.dp,
                                    top = 8.dp, // Small top padding for the list content
                                    bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 8.dp // Add 8.dp extra padding at the bottom
                                )
                            ) {
                                items(teamMembersToDisplay, key = { it.userId }) { member ->
                                    TeamMemberListItem(member = member) { clickedMember ->
                                        selectedMemberForOptions = clickedMember
                                        showMemberOptionsDialog = true
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Member Options Dialog (now using MarkerInfoDialog as a ModalBottomSheet)
        if (showMemberOptionsDialog && selectedMemberForOptions != null) {
            // FIX: Pass navController to MarkerInfoDialog
            MarkerInfoDialog(
                navController = navController, // Pass the navController instance
                profilePhotoUrl = selectedMemberForOptions!!.profilePhotoUrl,
                title = selectedMemberForOptions!!.displayName,
                latLng = selectedMemberForOptions!!.latLng,
                timestamp = selectedMemberForOptions!!.lastUpdateTimestamp,
                speed = selectedMemberForOptions!!.speed,
                bearing = selectedMemberForOptions!!.bearing,
                onDismissRequest = { showMemberOptionsDialog = false },
                personUserId = selectedMemberForOptions!!.userId // Ensure userId is passed
            )
        }

        // Generic AlertDialog for messages (e.g., "No Active Group" error)
        if (showAlertDialog) {
            AlertDialog(
                onDismissRequest = { showAlertDialog = false },
                title = { Text(alertDialogTitle) },
                text = { Text(alertDialogMessage) },
                confirmButton = {
                    Button(onClick = { showAlertDialog = false }) {
                        Text("OK")
                    }
                }
            )
        }
    }

    // Composable for a single item in the team member list
    @Composable
    fun TeamMemberListItem(member: TeamMemberDisplayInfo, onClick: (TeamMemberDisplayInfo) -> Unit) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp, vertical = 4.dp) // Item padding
                .clickable { onClick(member) }
                .shadow(2.dp, RoundedCornerShape(12.dp)), // Added shadow for floating effect
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.9f)),
            // Create a dark blue stroke for the card border
            border = BorderStroke(width = 1.dp, color = DarkBlue) // Define the BorderStroke here

        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min) // Makes children match height
                    .padding(3.dp), // Content padding inside the card
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start // Align items to start, relying on weight
            ) {
                // Left: Profile Picture
                Image(
                    painter = rememberAsyncImagePainter(
                        model = member.profilePhotoUrl,
                        error = painterResource(id = R.drawable.default_profile_pic) // Fallback image
                    ),
                    contentDescription = "${member.displayName}'s Profile Picture",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(60.dp) // Larger profile picture
                        .clip(CircleShape)
                        .background(Color.LightGray)
                )

                Spacer(Modifier.width(12.dp))

                // Center-Left: Display Name & Address
                Column(
                    modifier = Modifier.weight(1f), // Takes available horizontal space
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = member.displayName,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = DarkBlue,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    // Display address if available, otherwise "Location unknown"
                    Text(
                        text = member.currentAddress ?: "Location unknown", // Display "Location unknown" while geocoding or if not found
                        fontSize = 12.sp,
                        color = Color.Gray,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    // Last Updated time
                    Text(
                        text = "Updated ${TimeFormatter.getRelativeTimeSpanString(member.lastUpdateTimestamp)}",
                        fontSize = 10.sp,
                        color = Color.Gray.copy(alpha = 0.7f)
                    )
                }

                Spacer(Modifier.width(12.dp))

                // Right: Speed & Direction
                Column(
                    horizontalAlignment = Alignment.End, // Aligns content to the right
                    verticalArrangement = Arrangement.Center
                ) {
                    member.speed?.let {
                        val speedMph = UnitConverter.metersPerSecondToMilesPerHour(it)
                        Text(
                            text = "${String.format("%.1f", speedMph)} MPH",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = DarkBlue
                        )
                    } ?: Text(
                        text = "Speed: N/A",
                        fontSize = 12.sp,
                        color = Color.Gray.copy(alpha = 0.7f)
                    )
                    member.bearing?.let {
                        val direction = UnitConverter.getCardinalDirection(it)
                        Text(
                            text = direction,
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    } ?: Text(
                        text = "Dir: N/A",
                        fontSize = 12.sp,
                        color = Color.Gray.copy(alpha = 0.7f)
                    )
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
                Log.e("TeamListScreen", "Geocoding failed for $latitude, $longitude: ${e.message}")
                "Address lookup failed" // Message for network/IO errors
            } catch (e: IllegalArgumentException) {
                Log.e("TeamListScreen", "Invalid LatLng for geocoding: $latitude, $longitude: ${e.message}")
                "Invalid location" // Message for invalid coordinates
            }
        }
    }
}
