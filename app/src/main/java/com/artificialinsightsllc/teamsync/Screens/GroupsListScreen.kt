// In file: app/src/main/java/com/artificialinsightsllc/teamsync/Screens/GroupsListScreen.kt
package com.artificialinsightsllc.teamsync.Screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.artificialinsightsllc.teamsync.R
import com.artificialinsightsllc.teamsync.Models.Groups
import com.artificialinsightsllc.teamsync.Models.GroupMembers
import com.artificialinsightsllc.teamsync.Models.MemberRole
import com.artificialinsightsllc.teamsync.Services.FirestoreService
import com.artificialinsightsllc.teamsync.Services.GroupMonitorService
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.unit.IntOffset
import java.util.UUID
import androidx.compose.ui.draw.shadow

// Assuming DarkBlue is defined globally or imported from ui.theme.Color
// val DarkBlue = Color(0xFF00008B)
// val LightCream = Color(0xFFFFFDD0)

class GroupsListScreen(private val navController: NavHostController) {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun Content() {
        val context = LocalContext.current
        val firestoreService = remember { FirestoreService() }
        val auth = remember { FirebaseAuth.getInstance() }
        val coroutineScope = rememberCoroutineScope()

        val groupMonitorService = remember { GroupMonitorService(context) }
        val activeGroup by groupMonitorService.activeGroup.collectAsStateWithLifecycle()
        val activeGroupMember by groupMonitorService.activeGroupMember.collectAsStateWithLifecycle()


        var joinGroupCode by remember { mutableStateOf("") }
        var joinGroupPassword by remember { mutableStateOf("") }
        var passwordVisible by remember { mutableStateOf(false) }

        var myGroups by remember { mutableStateOf<List<Groups>>(emptyList()) }
        var myGroupMemberships by remember { mutableStateOf<List<GroupMembers>>(emptyList()) }

        var showScreen by remember { mutableStateOf(false) }

        var showLeaveGroupDialog by remember { mutableStateOf(false) }
        var groupToLeave: Groups? by remember { mutableStateOf(null) }

        var showAlertDialog by remember { mutableStateOf(false) }
        var alertDialogTitle by remember { mutableStateOf("") }
        var alertDialogMessage by remember { mutableStateOf("") }


        LaunchedEffect(Unit) {
            showScreen = true
            val currentUserId = auth.currentUser?.uid
            if (currentUserId != null) {
                firestoreService.getGroupMembershipsForUser(currentUserId).onSuccess { memberships ->
                    myGroupMemberships = memberships
                    val groupIds = memberships.map { it.groupId }.distinct()
                    if (groupIds.isNotEmpty()) {
                        firestoreService.getGroupsByIds(groupIds).onSuccess { groups ->
                            myGroups = groups.filter { group ->
                                val membership = memberships.find { it.groupId == group.groupID }
                                membership?.unjoinedTimestamp == null && (group.groupEndTimestamp?.let { it > System.currentTimeMillis() } ?: true)
                            }
                        }.onFailure { e ->
                            alertDialogTitle = "Error Loading Groups"
                            alertDialogMessage = "Failed to load group details: ${e.message}"
                            showAlertDialog = true
                        }
                    }
                }.onFailure { e ->
                    alertDialogTitle = "Error Loading Memberships"
                    alertDialogMessage = "Failed to load your group memberships: ${e.message}"
                    showAlertDialog = true
                }
            } else {
                alertDialogTitle = "Not Logged In"
                alertDialogMessage = "Please log in to view your groups."
                showAlertDialog = true
            }
        }

        AnimatedVisibility(
            visible = showScreen,
            enter = slideInHorizontally(animationSpec = tween(durationMillis = 500, delayMillis = 50)) { fullWidth ->
                -fullWidth
            } + scaleIn(animationSpec = tween(500, delayMillis = 50), initialScale = 0.8f) + fadeIn(animationSpec = tween(500, delayMillis = 50)),
            exit = slideOutHorizontally(animationSpec = tween(durationMillis = 500)) { fullWidth ->
                -fullWidth
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
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                        .padding(WindowInsets.systemBars.asPaddingValues()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
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
                            Icon(Icons.Filled.ArrowBack, "Back to Map", tint = DarkBlue)
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "My Groups",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = DarkBlue,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(48.dp))
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.9f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(text = "Join a Group", fontWeight = FontWeight.Bold, color = DarkBlue)
                            OutlinedTextField(
                                value = joinGroupCode,
                                onValueChange = { joinGroupCode = it },
                                label = { Text("Group Access Code") },
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = DarkBlue, unfocusedTextColor = DarkBlue.copy(alpha = 0.8f),
                                    focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                                    focusedBorderColor = DarkBlue, unfocusedBorderColor = DarkBlue.copy(alpha = 0.5f),
                                    focusedLabelColor = DarkBlue, unfocusedLabelColor = DarkBlue.copy(alpha = 0.7f),
                                    cursorColor = DarkBlue
                                ),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = joinGroupPassword,
                                onValueChange = { joinGroupPassword = it },
                                label = { Text("Group Password (Optional)") },
                                modifier = Modifier.fillMaxWidth(),
                                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                trailingIcon = {
                                    val image = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                        Icon(imageVector = image, contentDescription = "Toggle password visibility", tint = DarkBlue)
                                    }
                                },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = DarkBlue, unfocusedTextColor = DarkBlue.copy(alpha = 0.8f),
                                    focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                                    focusedBorderColor = DarkBlue, unfocusedBorderColor = DarkBlue.copy(alpha = 0.5f),
                                    focusedLabelColor = DarkBlue, unfocusedLabelColor = DarkBlue.copy(alpha = 0.7f),
                                    cursorColor = DarkBlue
                                )
                            )
                            Button(
                                onClick = {
                                    if (joinGroupCode.isBlank()) {
                                        alertDialogTitle = "Missing Code"
                                        alertDialogMessage = "Group Access Code cannot be empty."
                                        showAlertDialog = true
                                        return@Button
                                    }

                                    coroutineScope.launch {
                                        val currentUserId = auth.currentUser?.uid
                                        if (currentUserId == null) {
                                            alertDialogTitle = "Not Logged In"
                                            alertDialogMessage = "You must be logged in to join a group."
                                            showAlertDialog = true
                                            return@launch
                                        }

                                        val findGroupResult = firestoreService.findGroupByAccessCode(joinGroupCode, joinGroupPassword.ifBlank { null })
                                        if (findGroupResult.isFailure) {
                                            alertDialogTitle = "Error"
                                            alertDialogMessage = "Error finding group: ${findGroupResult.exceptionOrNull()?.message}"
                                            showAlertDialog = true
                                            return@launch
                                        }
                                        val foundGroup = findGroupResult.getOrNull()
                                        if (foundGroup == null) {
                                            alertDialogTitle = "Group Not Found"
                                            alertDialogMessage = "No group found with that code and/or password. Please check and try again."
                                            showAlertDialog = true
                                            return@launch
                                        }

                                        val existingMemberships = firestoreService.getGroupMembershipsForUser(currentUserId).getOrNull() ?: emptyList()
                                        val alreadyMemberOfThisGroup = existingMemberships.any { it.groupId == foundGroup.groupID && it.unjoinedTimestamp == null }

                                        if (alreadyMemberOfThisGroup) {
                                            alertDialogTitle = "Already a Member"
                                            alertDialogMessage = "You are already a member of '${foundGroup.groupName}'."
                                            showAlertDialog = true
                                            return@launch
                                        }

                                        val newMember = GroupMembers(
                                            id = "",
                                            groupId = foundGroup.groupID,
                                            userId = currentUserId,
                                            joinedTimestamp = System.currentTimeMillis(),
                                            unjoinedTimestamp = null,
                                            memberRole = MemberRole.MEMBER,
                                            sharingLocation = true, // Corrected property name
                                            lastKnownLocationLat = null,
                                            lastKnownLocationLon = null,
                                            lastLocationUpdateTime = null,
                                            batteryLevel = null,
                                            online = true, // Corrected property name
                                            personalLocationUpdateIntervalMillis = null,
                                            personalIsSharingLocationOverride = null,
                                            customMarkerIconUrl = null,
                                            notificationPreferences = null
                                        )

                                        val addMemberResult = firestoreService.addGroupMember(newMember)
                                        if (addMemberResult.isSuccess) {
                                            firestoreService.updateUserSelectedActiveGroup(currentUserId, foundGroup.groupID).onSuccess {
                                                alertDialogTitle = "Group Joined!"
                                                alertDialogMessage = "Successfully joined group '${foundGroup.groupName}' and set it as your active group!"
                                                showAlertDialog = true
                                                val updatedMemberships = firestoreService.getGroupMembershipsForUser(currentUserId).getOrNull() ?: emptyList()
                                                myGroupMemberships = updatedMemberships
                                                val groupIds = updatedMemberships.map { it.groupId }.distinct()
                                                if (groupIds.isNotEmpty()) {
                                                    firestoreService.getGroupsByIds(groupIds).onSuccess { groups ->
                                                        myGroups = groups.filter { g ->
                                                            updatedMemberships.any { m -> m.groupId == g.groupID && m.unjoinedTimestamp == null && (g.groupEndTimestamp?.let { it > System.currentTimeMillis() } ?: true) }
                                                        }
                                                    }
                                                } else {
                                                    myGroups = emptyList()
                                                }
                                            }.onFailure { e ->
                                                alertDialogTitle = "Error"
                                                alertDialogMessage = "Joined group '${foundGroup.groupName}' but failed to set it as active: ${e.message}"
                                                showAlertDialog = true
                                            }
                                        } else {
                                            alertDialogTitle = "Error"
                                            alertDialogMessage = "Failed to join group: ${addMemberResult.exceptionOrNull()?.message}"
                                            showAlertDialog = true
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = DarkBlue)
                            ) {
                                Text("Join Group", color = Color.White)
                            }
                        }
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.9f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(text = "My Active Groups", fontWeight = FontWeight.Bold, color = DarkBlue)
                            if (myGroups.isEmpty()) {
                                Text(
                                    text = "You are not a member of any active groups.",
                                    color = DarkBlue.copy(alpha = 0.7f),
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = TextAlign.Center,
                                    fontSize = 14.sp
                                )
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    myGroups.forEach { group ->
                                        val membership = myGroupMemberships.find { it.groupId == group.groupID && it.userId == auth.currentUser?.uid }
                                        if (membership != null) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(group.groupName, color = DarkBlue, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                                                Spacer(Modifier.width(8.dp))

                                                val isCurrentlyActiveGroup = activeGroup?.groupID == group.groupID
                                                val isOwner = membership.memberRole == MemberRole.OWNER

                                                if (!isCurrentlyActiveGroup) {
                                                    Button(
                                                        onClick = {
                                                            coroutineScope.launch {
                                                                val currentUserId = auth.currentUser?.uid
                                                                if (currentUserId != null) {
                                                                    firestoreService.updateUserSelectedActiveGroup(currentUserId, group.groupID).onSuccess {
                                                                        alertDialogTitle = "Group Switched"
                                                                        alertDialogMessage = "Switched active group to '${group.groupName}'."
                                                                        showAlertDialog = true
                                                                        val updatedMemberships = firestoreService.getGroupMembershipsForUser(currentUserId).getOrNull() ?: emptyList()
                                                                        myGroupMemberships = updatedMemberships
                                                                        val groupIds = updatedMemberships.map { it.groupId }.distinct()
                                                                        if (groupIds.isNotEmpty()) {
                                                                            firestoreService.getGroupsByIds(groupIds).onSuccess { groups ->
                                                                                myGroups = groups.filter { g ->
                                                                                    updatedMemberships.any { m -> m.groupId == g.groupID && m.unjoinedTimestamp == null && (g.groupEndTimestamp?.let { it > System.currentTimeMillis() } ?: true) }
                                                                                }
                                                                            }
                                                                        } else {
                                                                            myGroups = emptyList()
                                                                        }
                                                                    }.onFailure { e ->
                                                                        alertDialogTitle = "Error"
                                                                        alertDialogMessage = "Failed to switch active group: ${e.message}"
                                                                        showAlertDialog = true
                                                                    }
                                                                } else {
                                                                    alertDialogTitle = "Not Logged In"
                                                                    alertDialogMessage = "User not logged in."
                                                                    showAlertDialog = true
                                                                }
                                                            }
                                                        },
                                                        colors = ButtonDefaults.buttonColors(containerColor = DarkBlue.copy(alpha = 0.8f)),
                                                        modifier = Modifier.wrapContentWidth()
                                                    ) {
                                                        Text("JOIN", color = Color.White, fontSize = 12.sp)
                                                    }
                                                } else {
                                                    Text(
                                                        "ACTIVE",
                                                        color = DarkBlue.copy(alpha = 0.6f),
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 12.sp,
                                                        modifier = Modifier.padding(horizontal = 8.dp)
                                                    )
                                                }
                                                Spacer(Modifier.width(4.dp))

                                                Button(
                                                    onClick = {
                                                        groupToLeave = group
                                                        showLeaveGroupDialog = true
                                                    },
                                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                                    modifier = Modifier.wrapContentWidth()
                                                ) {
                                                    Text("LEAVE", color = Color.White, fontSize = 12.sp)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(32.dp))
                    }
                }
            }
        }

        // Leave Group Confirmation Dialog
        if (showLeaveGroupDialog && groupToLeave != null) {
            val currentUserId = auth.currentUser?.uid
            val isOwnerOfGroupToLeave = groupToLeave!!.groupOwnerId == currentUserId
            val dialogTitle = if (isOwnerOfGroupToLeave) "Shut Down Group?" else "Leave Group?"
            val dialogMessage = if (isOwnerOfGroupToLeave) {
                "You are the owner of '${groupToLeave!!.groupName}'. Leaving this group will permanently shut it down for all members and delete all group data. This action cannot be undone."
            } else {
                "Are you sure you want to leave '${groupToLeave!!.groupName}'? You will no longer be visible on the map, receive messages, or access group features."
            }

            AlertDialog(
                onDismissRequest = { showLeaveGroupDialog = false },
                properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
                modifier = Modifier.fillMaxWidth(0.85f)
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                        .shadow(8.dp, RoundedCornerShape(16.dp)),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.9f))
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(dialogTitle, color = DarkBlue, fontWeight = FontWeight.Bold, fontSize = 20.sp, textAlign = TextAlign.Center)
                        Text(dialogMessage, color = DarkBlue.copy(alpha = 0.8f), fontSize = 14.sp, textAlign = TextAlign.Center)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceAround,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = {
                                    coroutineScope.launch {
                                        val group = groupToLeave!!
                                        val userId = auth.currentUser?.uid
                                        if (userId == null) {
                                            alertDialogTitle = "Not Logged In"
                                            alertDialogMessage = "User not logged in."
                                            showAlertDialog = true
                                            showLeaveGroupDialog = false
                                            groupToLeave = null
                                            return@launch
                                        }

                                        if (isOwnerOfGroupToLeave) {
                                            alertDialogTitle = "Shutting Down Group"
                                            alertDialogMessage = "Initiating shutdown for group '${group.groupName}'..."
                                            showAlertDialog = true
                                            // TODO: Implement actual group deletion:
                                            alertDialogTitle = "Group Shut Down"
                                            alertDialogMessage = "Group '${group.groupName}' has been shut down (simulated). Data cleanup will occur."
                                            showAlertDialog = true
                                        } else {
                                            alertDialogTitle = "Leaving Group"
                                            alertDialogMessage = "Attempting to leave group '${group.groupName}'..."
                                            showAlertDialog = true
                                            val membershipToLeave = myGroupMemberships.find { it.groupId == group.groupID && it.userId == userId && it.unjoinedTimestamp == null }
                                            if (membershipToLeave != null) {
                                                val updatedMembership = membershipToLeave.copy(unjoinedTimestamp = System.currentTimeMillis())
                                                firestoreService.saveGroupMember(updatedMembership).onSuccess {
                                                    alertDialogTitle = "Group Left"
                                                    alertDialogMessage = "Successfully left group '${group.groupName}'."
                                                    showAlertDialog = true
                                                    if (activeGroup?.groupID == group.groupID) {
                                                        firestoreService.updateUserSelectedActiveGroup(userId, null).onSuccess {
                                                            alertDialogTitle = "Active Group Cleared"
                                                            alertDialogMessage = "Your active group has been cleared."
                                                            showAlertDialog = true
                                                        }.onFailure { e ->
                                                            alertDialogTitle = "Error"
                                                            alertDialogMessage = "Failed to clear active group: ${e.message}"
                                                            showAlertDialog = true
                                                        }
                                                    }
                                                    val updatedMemberships = firestoreService.getGroupMembershipsForUser(userId).getOrNull() ?: emptyList()
                                                    myGroupMemberships = updatedMemberships
                                                    val groupIds = updatedMemberships.map { it.groupId }.distinct()
                                                    if (groupIds.isNotEmpty()) {
                                                        firestoreService.getGroupsByIds(groupIds).onSuccess { groups ->
                                                            myGroups = groups.filter { g ->
                                                                updatedMemberships.any { m -> m.groupId == g.groupID && m.unjoinedTimestamp == null && (g.groupEndTimestamp?.let { it > System.currentTimeMillis() } ?: true) }
                                                            }
                                                        }
                                                    } else {
                                                        myGroups = emptyList()
                                                    }
                                                }.onFailure { e ->
                                                    alertDialogTitle = "Error"
                                                    alertDialogMessage = "Failed to leave group: ${e.message}"
                                                    showAlertDialog = true
                                                }
                                            } else {
                                                alertDialogTitle = "Error"
                                                alertDialogMessage = "Could not find active membership to leave."
                                                showAlertDialog = true
                                            }
                                        }
                                        showLeaveGroupDialog = false
                                        groupToLeave = null
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            ) {
                                Text(if (isOwnerOfGroupToLeave) "Shut Down" else "Leave", color = Color.White)
                            }
                            Button(onClick = { showLeaveGroupDialog = false }) {
                                Text("Cancel", color = DarkBlue)
                            }
                        }
                    }
                }
            }
        }

        // Generic AlertDialog for messages
        if (showAlertDialog) {
            AlertDialog(
                onDismissRequest = { showAlertDialog = false },
                properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
                modifier = Modifier.fillMaxWidth(0.85f)
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                        .shadow(8.dp, RoundedCornerShape(16.dp)),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.9f))
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(alertDialogTitle, color = DarkBlue, fontWeight = FontWeight.Bold, fontSize = 20.sp, textAlign = TextAlign.Center)
                        Text(alertDialogMessage, color = DarkBlue.copy(alpha = 0.8f), fontSize = 14.sp, textAlign = TextAlign.Center)
                        Button(onClick = { showAlertDialog = false }, colors = ButtonDefaults.buttonColors(containerColor = DarkBlue)) {
                            Text("OK", color = Color.White)
                        }
                    }
                }
            }
        }
    }
}