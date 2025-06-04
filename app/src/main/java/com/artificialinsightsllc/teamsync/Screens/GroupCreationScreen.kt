// In file: app/src/main/java/com/artificialinsightsllc/teamsync/Screens/GroupCreationScreen.kt
package com.artificialinsightsllc.teamsync.Screens

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.artificialinsightsllc.teamsync.Models.GroupType
import com.artificialinsightsllc.teamsync.R

import com.artificialinsightsllc.teamsync.Services.FirestoreService
import com.artificialinsightsllc.teamsync.Models.Groups
import com.artificialinsightsllc.teamsync.Models.GroupMembers
import com.artificialinsightsllc.teamsync.Models.MemberRole
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import java.util.UUID

// Custom color for dark blue text
val DarkBlue = Color(0xFF00008B)

// Custom color for dropdown background
val LightCream = Color(0xFFFFFDD0)

class GroupCreationScreen(private val navController: NavHostController) {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun Content() {
        val context = LocalContext.current
        val scrollState = rememberScrollState()

        val firestoreService = remember { FirestoreService() }
        val coroutineScope = rememberCoroutineScope()
        val auth = remember { FirebaseAuth.getInstance() }

        var groupName by remember { mutableStateOf("") }
        var groupDescription by remember { mutableStateOf("") }
        var groupAccessCode by remember { mutableStateOf("") }
        var groupAccessPassword by remember { mutableStateOf("") }
        var passwordVisible by remember { mutableStateOf(false) }

        var selectedGroupType by remember { mutableStateOf(GroupType.FREEMIUM) }

        val locationUpdateIntervalOptions = listOf(
            "5 Minutes (Freemium)", "1 Minute (\$5/mo)", "10 Seconds (\$10/mo)", "Real-time (\$20/mo)"
        )
        val locationUpdateIntervalValues = listOf(300000L, 60000L, 10000L, 1000L)
        var selectedLocationUpdateIntervalIndex by remember { mutableStateOf(0) }
        var locationIntervalDropdownExpanded by remember { mutableStateOf(false) }

        val maxMemberOptions = listOf("10 (Freemium)", "20 (\$5/mo)", "50 (\$10/mo)", "100 (\$15/mo)", "Unlimited (\$30/mo)")
        val maxMemberValues = listOf(10, 20, 50, 100, Int.MAX_VALUE)
        var selectedMaxMembersIndex by remember { mutableStateOf(0) }
        var maxMembersDropdownExpanded by remember { mutableStateOf(false) }

        val durationOptions = listOf(
            "4 Hours (Freemium)", "12 Hours (\$2/mo)", "24 Hours (\$4/mo)",
            "3 Days (\$8/mo)", "7 Days (\$15/mo)", "1 Month (\$40/mo)",
            "3 Months (\$100/mo)", "6 Months (\$180/mo)", "Unlimited (\$300/mo)"
        )
        val durationValuesMillis = listOf(
            4 * 60 * 60 * 1000L, 12 * 60 * 60 * 1000L, 24 * 60 * 60 * 1000L,
            3 * 24 * 60 * 60 * 1000L, 7 * 24 * 60 * 60 * 1000L, 30 * 24 * 60 * 60 * 1000L,
            90 * 24 * 60 * 60 * 1000L, 180 * 24 * 60 * 60 * 1000L, null
        )
        var selectedDurationIndex by remember { mutableStateOf(0) }
        var durationDropdownExpanded by remember { mutableStateOf(false) }


        var enablePrivateChats by remember { mutableStateOf(false) }
        var enablePhotoSharing by remember { mutableStateOf(false) }
        var enableAudioMessages by remember { mutableStateOf(false) }
        var enableFileSharing by remember { mutableStateOf(false) }
        var dispatchModeEnabled by remember { mutableStateOf(false) }
        var maxGeofences by remember { mutableStateOf(0) }
        var enableLocationHistory by remember { mutableStateOf(false) }
        var locationHistoryRetentionDays by remember { mutableStateOf(0) }

        val isPaidBasicOrAbove = selectedGroupType == GroupType.PAID_BASIC

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
                    .verticalScroll(scrollState)
                    .padding(16.dp)
                    .padding(WindowInsets.systemBars.asPaddingValues()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Spacer(Modifier.height(16.dp))

                Text(
                    text = "Create New Group",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = DarkBlue,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.9f))
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(text = "Group Information", fontWeight = FontWeight.Bold, color = DarkBlue)
                        OutlinedTextField(
                            value = groupName,
                            onValueChange = { groupName = it },
                            label = { Text("Group Name") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = DarkBlue,
                                unfocusedTextColor = DarkBlue.copy(alpha = 0.8f),
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedBorderColor = DarkBlue,
                                unfocusedBorderColor = DarkBlue.copy(alpha = 0.5f),
                                focusedLabelColor = DarkBlue,
                                unfocusedLabelColor = DarkBlue.copy(alpha = 0.7f),
                                cursorColor = DarkBlue
                            ),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = groupDescription,
                            onValueChange = { groupDescription = it },
                            label = { Text("Group Description (Optional)") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = DarkBlue,
                                unfocusedTextColor = DarkBlue.copy(alpha = 0.8f),
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedBorderColor = DarkBlue,
                                unfocusedBorderColor = DarkBlue.copy(alpha = 0.5f),
                                focusedLabelColor = DarkBlue,
                                unfocusedLabelColor = DarkBlue.copy(alpha = 0.7f),
                                cursorColor = DarkBlue
                            )
                        )
                        OutlinedTextField(
                            value = groupAccessCode,
                            onValueChange = { groupAccessCode = it },
                            label = { Text("Group Access Code (e.g., TEAM123)") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = DarkBlue,
                                unfocusedTextColor = DarkBlue.copy(alpha = 0.8f),
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedBorderColor = DarkBlue,
                                unfocusedBorderColor = DarkBlue.copy(alpha = 0.5f),
                                focusedLabelColor = DarkBlue,
                                unfocusedLabelColor = DarkBlue.copy(alpha = 0.7f),
                                cursorColor = DarkBlue
                            ),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = groupAccessPassword,
                            onValueChange = { groupAccessPassword = it },
                            label = { Text("Group Password (Optional)") },
                            modifier = Modifier.fillMaxWidth(),
                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            trailingIcon = {
                                val image = if (passwordVisible)
                                    Icons.Filled.Visibility
                                else Icons.Filled.VisibilityOff
                                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Icon(imageVector = image, contentDescription = "Toggle password visibility", tint = DarkBlue)
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = DarkBlue,
                                unfocusedTextColor = DarkBlue.copy(alpha = 0.8f),
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedBorderColor = DarkBlue,
                                unfocusedBorderColor = DarkBlue.copy(alpha = 0.5f),
                                focusedLabelColor = DarkBlue,
                                unfocusedLabelColor = DarkBlue.copy(alpha = 0.7f),
                                cursorColor = DarkBlue
                            )
                        )
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.9f))
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(text = "Choose Your Plan", fontWeight = FontWeight.Bold, color = DarkBlue)

                        Row(
                            Modifier.fillMaxWidth().selectableGroup(),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            GroupType.entries.forEach { type ->
                                Row(
                                    Modifier
                                        .weight(1f)
                                        .selectable(
                                            selected = (type == selectedGroupType),
                                            onClick = { selectedGroupType = type },
                                            role = Role.RadioButton
                                        )
                                        .padding(8.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = (type == selectedGroupType),
                                        onClick = null,
                                        colors = RadioButtonDefaults.colors(selectedColor = DarkBlue, unselectedColor = DarkBlue.copy(alpha = 0.7f))
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = when (type) {
                                            GroupType.FREEMIUM -> "Freemium"
                                            GroupType.PAID_BASIC -> "Basic Paid"
                                        },
                                        fontWeight = FontWeight.Medium,
                                        color = DarkBlue
                                    )
                                }
                            }
                        }

                        Text(
                            text = when (selectedGroupType) {
                                GroupType.FREEMIUM -> "• Max 10 members\n• Location updates every 5 minutes\n• Basic group chat (text only)\n• Group ends after 4 hours"
                                GroupType.PAID_BASIC -> "• Starting 20 members (expandable)\n• Customizable location updates\n• Private, Photo, Audio, File chats\n• Unlimited duration\n• Basic Geofencing & Location History\n• Supports Dispatch Mode as an add-on"
                            },
                            color = DarkBlue,
                            fontSize = 13.sp
                        )
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.9f))
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(text = "Customize Features", fontWeight = FontWeight.Bold, color = DarkBlue)

                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Location Update Interval:", color = DarkBlue, fontWeight = FontWeight.Medium)
                            ExposedDropdownMenuBox(
                                expanded = locationIntervalDropdownExpanded,
                                onExpandedChange = {
                                    if (isPaidBasicOrAbove) locationIntervalDropdownExpanded = !locationIntervalDropdownExpanded
                                }
                            ) {
                                OutlinedTextField(
                                    value = locationUpdateIntervalOptions[selectedLocationUpdateIntervalIndex],
                                    onValueChange = {},
                                    readOnly = true,
                                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                                    enabled = isPaidBasicOrAbove,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = DarkBlue, unfocusedTextColor = DarkBlue.copy(alpha = 0.8f),
                                        focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                                        focusedBorderColor = DarkBlue, unfocusedBorderColor = DarkBlue.copy(alpha = 0.5f),
                                        focusedLabelColor = DarkBlue, unfocusedLabelColor = DarkBlue.copy(alpha = 0.7f),
                                        disabledTextColor = DarkBlue.copy(alpha = 0.5f), disabledLabelColor = DarkBlue.copy(alpha = 0.3f),
                                        disabledBorderColor = DarkBlue.copy(alpha = 0.3f), cursorColor = DarkBlue
                                    )
                                )
                                ExposedDropdownMenu(
                                    expanded = locationIntervalDropdownExpanded,
                                    onDismissRequest = { locationIntervalDropdownExpanded = false },
                                    modifier = Modifier.background(LightCream)
                                ) {
                                    locationUpdateIntervalOptions.forEachIndexed { index, selectionOption ->
                                        DropdownMenuItem(
                                            text = { Text(selectionOption, color = DarkBlue) },
                                            onClick = {
                                                selectedLocationUpdateIntervalIndex = index
                                                locationIntervalDropdownExpanded = false
                                            },
                                            enabled = isPaidBasicOrAbove
                                        )
                                    }
                                }
                            }
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Max Members:", color = DarkBlue, fontWeight = FontWeight.Medium)
                            ExposedDropdownMenuBox(
                                expanded = maxMembersDropdownExpanded,
                                onExpandedChange = {
                                    if (isPaidBasicOrAbove) maxMembersDropdownExpanded = !maxMembersDropdownExpanded
                                }
                            ) {
                                OutlinedTextField(
                                    value = maxMemberOptions[selectedMaxMembersIndex],
                                    onValueChange = {},
                                    readOnly = true,
                                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                                    enabled = isPaidBasicOrAbove,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = DarkBlue, unfocusedTextColor = DarkBlue.copy(alpha = 0.8f),
                                        focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                                        focusedBorderColor = DarkBlue, unfocusedBorderColor = DarkBlue.copy(alpha = 0.5f),
                                        focusedLabelColor = DarkBlue, unfocusedLabelColor = DarkBlue.copy(alpha = 0.7f),
                                        disabledTextColor = DarkBlue.copy(alpha = 0.5f), disabledLabelColor = DarkBlue.copy(alpha = 0.3f),
                                        disabledBorderColor = DarkBlue.copy(alpha = 0.3f), cursorColor = DarkBlue
                                    )
                                )
                                ExposedDropdownMenu(
                                    expanded = maxMembersDropdownExpanded,
                                    onDismissRequest = { maxMembersDropdownExpanded = false },
                                    modifier = Modifier.background(LightCream)
                                ) {
                                    maxMemberOptions.forEachIndexed { index, selectionOption ->
                                        DropdownMenuItem(
                                            text = { Text(selectionOption, color = DarkBlue) },
                                            onClick = {
                                                selectedMaxMembersIndex = index
                                                maxMembersDropdownExpanded = false
                                            },
                                            enabled = isPaidBasicOrAbove
                                        )
                                    }
                                }
                            }
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Duration:", color = DarkBlue, fontWeight = FontWeight.Medium)
                            ExposedDropdownMenuBox(
                                expanded = durationDropdownExpanded,
                                onExpandedChange = {
                                    if (isPaidBasicOrAbove) durationDropdownExpanded = !durationDropdownExpanded
                                }
                            ) {
                                OutlinedTextField(
                                    value = durationOptions[selectedDurationIndex],
                                    onValueChange = {},
                                    readOnly = true,
                                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                                    enabled = isPaidBasicOrAbove,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = DarkBlue, unfocusedTextColor = DarkBlue.copy(alpha = 0.8f),
                                        focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                                        focusedBorderColor = DarkBlue, unfocusedBorderColor = DarkBlue.copy(alpha = 0.5f),
                                        focusedLabelColor = DarkBlue, unfocusedLabelColor = DarkBlue.copy(alpha = 0.7f),
                                        disabledTextColor = DarkBlue.copy(alpha = 0.5f), disabledLabelColor = DarkBlue.copy(alpha = 0.3f),
                                        disabledBorderColor = DarkBlue.copy(alpha = 0.3f), cursorColor = DarkBlue
                                    )
                                )
                                ExposedDropdownMenu(
                                    expanded = durationDropdownExpanded,
                                    onDismissRequest = { durationDropdownExpanded = false },
                                    modifier = Modifier.background(LightCream)
                                ) {
                                    durationOptions.forEachIndexed { index, selectionOption ->
                                        DropdownMenuItem(
                                            text = { Text(selectionOption, color = DarkBlue) },
                                            onClick = {
                                                selectedDurationIndex = index
                                                durationDropdownExpanded = false
                                            },
                                            enabled = isPaidBasicOrAbove
                                        )
                                    }
                                }
                            }
                        }

                        FeatureToggle(
                            text = "Enable Private 1-to-1 Chats (\$3/mo)",
                            checked = enablePrivateChats,
                            onCheckedChange = { enablePrivateChats = it },
                            enabled = isPaidBasicOrAbove,
                            DarkBlue = DarkBlue
                        )
                        FeatureToggle(
                            text = "Enable Photo Sharing on Map/Chat (\$3/mo)",
                            checked = enablePhotoSharing,
                            onCheckedChange = { enablePhotoSharing = it },
                            enabled = isPaidBasicOrAbove,
                            DarkBlue = DarkBlue
                        )
                        FeatureToggle(
                            text = "Enable Audio Messages in Chat (\$2/mo)",
                            checked = enableAudioMessages,
                            onCheckedChange = { enableAudioMessages = it },
                            enabled = isPaidBasicOrAbove,
                            DarkBlue = DarkBlue
                        )
                        FeatureToggle(
                            text = "Enable File Sharing in Chat (\$5/mo)",
                            checked = enableFileSharing,
                            onCheckedChange = { enableFileSharing = it },
                            enabled = isPaidBasicOrAbove,
                            DarkBlue = DarkBlue
                        )
                        FeatureToggle(
                            text = "Enable Location History (7 Days) (\$7/mo)",
                            checked = enableLocationHistory,
                            onCheckedChange = { enableLocationHistory = it },
                            enabled = isPaidBasicOrAbove,
                            DarkBlue = DarkBlue
                        ) {
                            if (enableLocationHistory) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Retention Days: ", color = DarkBlue, fontWeight = FontWeight.Normal)
                                    Spacer(Modifier.width(4.dp))
                                    Slider(
                                        value = locationHistoryRetentionDays.toFloat(),
                                        onValueChange = { locationHistoryRetentionDays = it.toInt() },
                                        valueRange = 0f..30f,
                                        steps = 29,
                                        modifier = Modifier.weight(1f),
                                        colors = SliderDefaults.colors(
                                            thumbColor = DarkBlue,
                                            activeTrackColor = DarkBlue.copy(alpha = 0.7f),
                                            inactiveTrackColor = DarkBlue.copy(alpha = 0.3f)
                                        )
                                    )
                                    Text("${locationHistoryRetentionDays} days", color = DarkBlue)
                                }
                            }
                        }

                        FeatureToggle(
                            text = "Enable Dispatch Mode Tools (\$20/mo)",
                            checked = dispatchModeEnabled,
                            onCheckedChange = { dispatchModeEnabled = it },
                            enabled = isPaidBasicOrAbove,
                            DarkBlue = DarkBlue
                        ) {
                            if (dispatchModeEnabled) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Max GeoFences: ", color = DarkBlue, fontWeight = FontWeight.Normal)
                                    Spacer(Modifier.width(4.dp))
                                    Slider(
                                        value = maxGeofences.toFloat(),
                                        onValueChange = { maxGeofences = it.toInt() },
                                        valueRange = 0f..100f,
                                        steps = 99,
                                        modifier = Modifier.weight(1f),
                                        colors = SliderDefaults.colors(
                                            thumbColor = DarkBlue,
                                            activeTrackColor = DarkBlue.copy(alpha = 0.7f),
                                            inactiveTrackColor = DarkBlue.copy(alpha = 0.3f)
                                        )
                                    )
                                    Text("$maxGeofences geofences", color = DarkBlue)
                                }
                            }
                        }
                    }
                }

                Button(
                    onClick = {
                        if (groupName.isBlank()) {
                            Toast.makeText(context, "Group Name cannot be empty", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        if (groupAccessCode.isBlank()) {
                            Toast.makeText(context, "Group Access Code cannot be empty", Toast.LENGTH_SHORT).show()
                            return@Button
                        }

                        coroutineScope.launch {
                            val currentUserId = auth.currentUser?.uid
                            if (currentUserId == null) {
                                Toast.makeText(context, "User not logged in. Please log in to create a group.", Toast.LENGTH_LONG).show()
                                return@launch
                            }

                            val newGroupId = UUID.randomUUID().toString()
                            val currentTimeMillis = System.currentTimeMillis()

                            val calculatedGroupEndTimestamp = when (selectedGroupType) {
                                GroupType.FREEMIUM -> currentTimeMillis + (4 * 60 * 60 * 1000L)
                                GroupType.PAID_BASIC -> durationValuesMillis[selectedDurationIndex]
                                    ?.let { it + currentTimeMillis }
                            }

                            val calculatedLocationUpdateInterval = when (selectedGroupType) {
                                GroupType.FREEMIUM -> 300000L
                                GroupType.PAID_BASIC -> locationUpdateIntervalValues[selectedLocationUpdateIntervalIndex]
                            }

                            val calculatedMaxMembers = when (selectedGroupType) {
                                GroupType.FREEMIUM -> 10
                                GroupType.PAID_BASIC -> maxMemberValues[selectedMaxMembersIndex]
                            }

                            val newGroup = Groups(
                                groupID = newGroupId,
                                groupName = groupName,
                                groupDescription = groupDescription,
                                fcmName = "group_${newGroupId}",
                                groupOwnerId = currentUserId,
                                groupCreateTimestamp = currentTimeMillis,
                                groupType = selectedGroupType,
                                locationUpdateIntervalMillis = calculatedLocationUpdateInterval,
                                groupEndTimestamp = calculatedGroupEndTimestamp,
                                maxMembers = calculatedMaxMembers,
                                allowPollingLocation = isPaidBasicOrAbove,
                                allowCheckInRequests = isPaidBasicOrAbove,
                                enableLocationHistory = enableLocationHistory,
                                locationHistoryRetentionDays = if (enableLocationHistory) locationHistoryRetentionDays else null,
                                enableGroupChat = true,
                                enablePrivateChats = enablePrivateChats,
                                enablePhotoSharing = enablePhotoSharing,
                                enableAudioMessages = enableAudioMessages,
                                enableFileSharing = enableFileSharing,
                                groupAccessCode = groupAccessCode,
                                groupAccessPassword = groupAccessPassword.ifBlank { null },
                                dispatchModeEnabled = dispatchModeEnabled,
                                maxGeofences = if (dispatchModeEnabled) maxGeofences else 0
                            )

                            val createGroupResult = firestoreService.createGroup(newGroup)

                            if (createGroupResult.isSuccess) {
                                val ownerMember = GroupMembers(
                                    id = "",
                                    groupId = newGroupId,
                                    userId = currentUserId,
                                    joinedTimestamp = currentTimeMillis,
                                    unjoinedTimestamp = null,
                                    memberRole = MemberRole.OWNER,
                                    sharingLocation = true, // <--- FIXED HERE
                                    lastKnownLocationLat = null,
                                    lastKnownLocationLon = null,
                                    lastLocationUpdateTime = null,
                                    batteryLevel = null,
                                    online = true, // <--- FIXED HERE
                                    personalLocationUpdateIntervalMillis = null,
                                    personalIsSharingLocationOverride = null,
                                    customMarkerIconUrl = null,
                                    notificationPreferences = null
                                )

                                val addMemberResult = firestoreService.addGroupMember(ownerMember)

                                if (addMemberResult.isSuccess) {
                                    Toast.makeText(context, "Group '${groupName}' created successfully!", Toast.LENGTH_LONG).show()
                                    navController.popBackStack()
                                } else {
                                    Toast.makeText(context, "Failed to add group owner: ${addMemberResult.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                                }
                            } else {
                                Toast.makeText(context, "Failed to create group: ${createGroupResult.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth(0.7f)
                        .height(50.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DarkBlue)
                ) {
                    Text("Create Group", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
fun FeatureToggle(
    text: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean,
    DarkBlue: Color,
    content: @Composable (() -> Unit)? = null
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled) { onCheckedChange(!checked) }
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = text,
                color = if (enabled) DarkBlue else DarkBlue.copy(alpha = 0.5f),
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = DarkBlue,
                    checkedTrackColor = DarkBlue.copy(alpha = 0.7f),
                    uncheckedThumbColor = DarkBlue.copy(alpha = 0.5f),
                    uncheckedTrackColor = DarkBlue.copy(alpha = 0.3f),
                    disabledCheckedThumbColor = DarkBlue.copy(alpha = 0.3f),
                    disabledCheckedTrackColor = DarkBlue.copy(alpha = 0.2f),
                    disabledUncheckedThumbColor = DarkBlue.copy(alpha = 0.2f),
                    disabledUncheckedTrackColor = DarkBlue.copy(alpha = 0.1f)
                )
            )
        }
        content?.invoke()
    }
}