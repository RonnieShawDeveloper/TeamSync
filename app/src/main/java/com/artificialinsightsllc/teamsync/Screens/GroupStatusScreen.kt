// In file: app/src/main/java/com/artificialinsightsllc/teamsync/Screens/GroupStatusScreen.kt
package com.artificialinsightsllc.teamsync.Screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Battery1Bar
import androidx.compose.material.icons.filled.Battery2Bar
import androidx.compose.material.icons.filled.Battery3Bar
import androidx.compose.material.icons.filled.Battery4Bar
import androidx.compose.material.icons.filled.Battery5Bar
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CrisisAlert
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.OnlinePrediction
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import coil.compose.rememberAsyncImagePainter
import com.artificialinsightsllc.teamsync.R
import com.artificialinsightsllc.teamsync.ViewModels.GroupStatusViewModel
import com.artificialinsightsllc.teamsync.ui.theme.DarkBlue
import com.artificialinsightsllc.teamsync.ui.theme.LightCream
import com.artificialinsightsllc.teamsync.Helpers.TimeFormatter // Import TimeFormatter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class GroupStatusScreen(private val navController: NavHostController) {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun Content(viewModel: GroupStatusViewModel = viewModel()) {
        val context = LocalContext.current
        val coroutineScope = rememberCoroutineScope()

        // Collect dummy data from ViewModel
        val onlineMemberCount by viewModel.onlineMemberCount.collectAsStateWithLifecycle()
        val totalMemberCount by viewModel.totalMemberCount.collectAsStateWithLifecycle()
        val overallActivityFeed by viewModel.overallActivityFeed.collectAsStateWithLifecycle()
        val avgLocationUpdateFrequency by viewModel.avgLocationUpdateFrequency.collectAsStateWithLifecycle()

        val activeGeofences by viewModel.activeGeofences.collectAsStateWithLifecycle()
        val geofenceEventCountLastHour by viewModel.geofenceEventCountLastHour.collectAsStateWithLifecycle()
        val membersByZone by viewModel.membersByZone.collectAsStateWithLifecycle()

        val unreadGroupChatMessages by viewModel.unreadGroupChatMessages.collectAsStateWithLifecycle()
        val pendingBroadcasts by viewModel.pendingBroadcasts.collectAsStateWithLifecycle()
        val communicationReliability by viewModel.communicationReliability.collectAsStateWithLifecycle()

        val activeAlertsCount by viewModel.activeAlertsCount.collectAsStateWithLifecycle()
        val lowBatteryMembers by viewModel.lowBatteryMembers.collectAsStateWithLifecycle()
        val offlineMembers by viewModel.offlineMembers.collectAsStateWithLifecycle()

        val currentGroupPlan by viewModel.currentGroupPlan.collectAsStateWithLifecycle()
        val dispatchModeActive by viewModel.dispatchModeActive.collectAsStateWithLifecycle()
        val resourceUtilization by viewModel.resourceUtilization.collectAsStateWithLifecycle()

        val allMemberStatuses by viewModel.allMemberStatuses.collectAsStateWithLifecycle()


        var showScreen by remember { mutableStateOf(false) } // For slide-in animation

        LaunchedEffect(Unit) {
            showScreen = true
        }

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

                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = {
                                Text(
                                    text = "Group Status Overview",
                                    color = DarkBlue,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = Color.White.copy(alpha = 0.7f)
                            ),
                            navigationIcon = {
                                IconButton(onClick = {
                                    coroutineScope.launch {
                                        showScreen = false
                                        delay(550)
                                        navController.popBackStack()
                                    }
                                }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = DarkBlue)
                                }
                            }
                        )
                    },
                    containerColor = Color.Transparent
                ) { innerPadding ->
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .padding(horizontal = 4.dp, vertical = 8.dp) // Reduced horizontal padding
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.6f)), // Semi-transparent background for content area
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 16.dp) // Reduced inner horizontal padding
                    ) {
                        // 1. Group Health & Activity Summary
                        item {
                            StatusCard(title = "Group Health & Activity Summary") {
                                StatRow(Icons.Filled.Groups, "Members Online:", "$onlineMemberCount/$totalMemberCount")
                                StatRow(Icons.Filled.GpsFixed, "Avg. Update Freq.:", avgLocationUpdateFrequency)
                                Spacer(Modifier.height(8.dp))
                                Text("Recent Activity Highlights:", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = DarkBlue)
                                overallActivityFeed.forEach { event ->
                                    Text("• $event", fontSize = 13.sp, color = DarkBlue.copy(alpha = 0.8f))
                                }
                                Spacer(Modifier.height(16.dp))
                                Text("Member Status Overview:", fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = DarkBlue, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                                Divider(color = DarkBlue.copy(alpha = 0.5f), thickness = 1.dp, modifier = Modifier.padding(vertical = 4.dp))
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(300.dp) // Fixed height for demo, will be dynamic
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(LightCream.copy(alpha = 0.6f)),
                                    contentPadding = PaddingValues(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(allMemberStatuses) { member ->
                                        MemberStatusItem(member = member)
                                    }
                                }
                            }
                        }

                        // 2. Geofence & Zone Monitoring Overview
                        item {
                            StatusCard(title = "Geofence & Zone Monitoring") {
                                StatRow(Icons.Filled.LocationOn, "Geofence Events (Last Hour):", "$geofenceEventCountLastHour")
                                Spacer(Modifier.height(8.dp))
                                Text("Active Geofences:", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = DarkBlue)
                                activeGeofences.forEach { geofence ->
                                    Text("• ${geofence.name}: ${geofence.status} (${geofence.membersInside} members inside)", fontSize = 13.sp, color = DarkBlue.copy(alpha = 0.8f))
                                    geofence.lastEvent?.let { Text("  Last Event: $it", fontSize = 11.sp, color = DarkBlue.copy(alpha = 0.6f)) }
                                }
                                Spacer(Modifier.height(8.dp))
                                Text("Members by Zone:", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = DarkBlue)
                                membersByZone.forEach { zone ->
                                    Text("• ${zone.zoneName}: ${zone.memberCount} members", fontSize = 13.sp, color = DarkBlue.copy(alpha = 0.8f))
                                }
                            }
                        }

                        // 3. Communication Channel Health
                        item {
                            StatusCard(title = "Communication Channel Health") {
                                StatRow(Icons.Filled.Chat, "Unread Group Chat Msgs:", "$unreadGroupChatMessages")
                                StatRow(Icons.Filled.Notifications, "Pending Broadcasts:", "$pendingBroadcasts")
                                StatRow(Icons.Filled.OnlinePrediction, "Reliability:", communicationReliability)
                            }
                        }

                        // 4. Critical Alerts & Safety Summary
                        item {
                            StatusCard(title = "Critical Alerts & Safety Summary") {
                                StatRow(Icons.Filled.CrisisAlert, "Active Alerts:", "$activeAlertsCount")
                                Spacer(Modifier.height(8.dp))
                                Text("Low Battery Members:", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = DarkBlue)
                                if (lowBatteryMembers.isEmpty()) {
                                    Text("None", fontSize = 13.sp, color = DarkBlue.copy(alpha = 0.8f))
                                } else {
                                    lowBatteryMembers.forEach { member ->
                                        Text("• ${member.name} (${member.level}%)", fontSize = 13.sp, color = DarkBlue.copy(alpha = 0.8f))
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                                Text("Offline Members:", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = DarkBlue)
                                if (offlineMembers.isEmpty()) {
                                    Text("None", fontSize = 13.sp, color = DarkBlue.copy(alpha = 0.8f))
                                } else {
                                    offlineMembers.forEach { member ->
                                        Text("• ${member.name} (Last seen: ${member.lastSeen})", fontSize = 13.sp, color = DarkBlue.copy(alpha = 0.8f))
                                    }
                                }
                            }
                        }

                        // 5. Operational Mode & Resources
                        item {
                            StatusCard(title = "Operational Mode & Resources") {
                                StatRow(Icons.Filled.Policy, "Current Group Plan:", currentGroupPlan)
                                StatRow(Icons.Filled.Tune, "Dispatch Mode Active:", if (dispatchModeActive) "Yes" else "No")
                                Spacer(Modifier.height(8.dp))
                                Text("Resource Status:", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = DarkBlue)
                                resourceUtilization.forEach { resource ->
                                    Text("• ${resource.name}: ${resource.status}", fontSize = 13.sp, color = DarkBlue.copy(alpha = 0.8f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun StatusCard(title: String, content: @Composable () -> Unit) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(4.dp, RoundedCornerShape(12.dp)),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = LightCream.copy(alpha = 0.9f))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = DarkBlue,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
                Divider(color = DarkBlue.copy(alpha = 0.5f), thickness = 1.dp, modifier = Modifier.padding(vertical = 4.dp))
                content()
            }
        }
    }

    @Composable
    fun StatRow(icon: ImageVector, label: String, value: String) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = icon, contentDescription = label, tint = DarkBlue, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(8.dp))
                Text(text = label, fontSize = 15.sp, color = DarkBlue, fontWeight = FontWeight.Medium)
            }
            Text(text = value, fontSize = 15.sp, color = DarkBlue.copy(alpha = 0.9f), fontWeight = FontWeight.Bold)
        }
    }

    @Composable
    fun MemberStatusItem(member: GroupStatusViewModel.MemberStatusDisplayData) {
        val batteryColor = when {
            member.batteryLevel == null -> Color.Gray
            member.batteryLevel <= 20 -> Color.Red
            member.batteryLevel <= 40 -> Color.Magenta
            member.batteryLevel <= 60 -> Color.Yellow
            else -> Color.Green
        }

        val batteryIcon = when {
            member.batteryLevel == null -> Icons.Filled.BatteryAlert
            member.batteryLevel <= 20 -> Icons.Filled.Battery1Bar
            member.batteryLevel <= 30 -> Icons.Filled.Battery2Bar
            member.batteryLevel <= 50 -> Icons.Filled.Battery3Bar
            member.batteryLevel <= 60 -> Icons.Filled.Battery4Bar
            member.batteryLevel <= 80 -> Icons.Filled.Battery5Bar
            else -> Icons.Filled.BatteryFull
        }

        val appStatusIcon = when (member.appStatus) {
            "FOREGROUND" -> Icons.Filled.Smartphone
            "BACKGROUND" -> Icons.Filled.CloudOff
            else -> Icons.Filled.CloudOff // Default for unknown/null
        }
        val appStatusColor = if (member.isOnline) DarkBlue else Color.Red // Red if offline

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White.copy(alpha = 0.8f), RoundedCornerShape(8.dp))
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Profile Pic and Name
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = rememberAsyncImagePainter(
                        model = member.profilePhotoUrl,
                        error = painterResource(id = R.drawable.default_profile_pic)
                    ),
                    contentDescription = "${member.displayName}'s Profile",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.LightGray)
                )
                Spacer(Modifier.width(8.dp))
                Column(
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = member.displayName,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = DarkBlue
                    )
                    // Last Update Time
                    member.lastLocationUpdateTime?.let {
                        Text(
                            text = "Updated ${TimeFormatter.getRelativeTimeSpanString(it)}",
                            fontSize = 10.sp,
                            color = DarkBlue.copy(alpha = 0.7f)
                        )
                    } ?: run {
                        Text(
                            text = "Update time N/A",
                            fontSize = 10.sp,
                            color = DarkBlue.copy(alpha = 0.5f)
                        )
                    }
                }
            }

            // Status Icons (aligned to the right)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End
            ) {
                // App Status Icon (Smartphone/CloudOff)
                Icon(
                    imageVector = appStatusIcon,
                    contentDescription = "App Status",
                    tint = appStatusColor,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(4.dp))

                // Battery Icon
                if (member.batteryLevel != null) {
                    Icon(
                        imageVector = batteryIcon,
                        contentDescription = "Battery Level ${member.batteryLevel}%",
                        tint = batteryColor,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(text = "${member.batteryLevel}%", fontSize = 12.sp, color = batteryColor)
                } else {
                    Text("N/A", fontSize = 12.sp, color = Color.Gray)
                }
                Spacer(Modifier.width(4.dp))

                // Charging Indicator
                if (member.batteryChargingStatus == "CHARGING") {
                    Icon(
                        imageVector = Icons.Filled.FlashOn,
                        contentDescription = "Charging",
                        tint = Color.Green,
                        modifier = Modifier.size(20.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.FlashOff,
                        contentDescription = "Not Charging",
                        tint = Color.Red,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
