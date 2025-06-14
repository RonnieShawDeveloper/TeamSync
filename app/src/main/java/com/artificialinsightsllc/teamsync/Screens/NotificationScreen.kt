package com.artificialinsightsllc.teamsync.Screens

import android.util.Log
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.artificialinsightsllc.teamsync.Models.NotificationEntity
import com.artificialinsightsllc.teamsync.Models.NotificationType // NEW: Import NotificationType
import com.artificialinsightsllc.teamsync.R
import com.artificialinsightsllc.teamsync.ViewModels.NotificationViewModel
import com.artificialinsightsllc.teamsync.ui.theme.DarkBlue
import com.artificialinsightsllc.teamsync.ui.theme.LightCream
import com.google.firebase.auth.FirebaseAuth
import com.artificialinsightsllc.teamsync.Services.GroupMonitorService
import com.artificialinsightsllc.teamsync.Services.TeamSyncFirebaseMessagingService
import com.artificialinsightsllc.teamsync.TeamSyncApplication
import kotlinx.coroutines.launch

class NotificationScreen(private val navController: NavHostController) {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun Content(viewModel: NotificationViewModel = viewModel()) {
        val context = LocalContext.current
        val coroutineScope = rememberCoroutineScope()
        val auth = remember { FirebaseAuth.getInstance() }

        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val notificationIdFromDeepLink = navBackStackEntry?.arguments?.getInt(TeamSyncFirebaseMessagingService.NOTIFICATION_ID_PARAM) ?: -1


        val notifications by viewModel.notifications.collectAsStateWithLifecycle()
        var showScreen by remember { mutableStateOf(false) }

        var selectedTab by remember { mutableIntStateOf(0) } // 0 for Group, 1 for Individual
        var notificationMessage by remember { mutableStateOf("") }
        var showConfirmDeleteAllDialog by remember { mutableStateOf(false) }

        val app = context.applicationContext as TeamSyncApplication
        val groupMonitorService = app.groupMonitorService
        val activeGroup by groupMonitorService.activeGroup.collectAsStateWithLifecycle()
        val isInActiveGroup by groupMonitorService.isInActiveGroup.collectAsStateWithLifecycle()


        LaunchedEffect(Unit) {
            showScreen = true
        }

        LaunchedEffect(notificationIdFromDeepLink) {
            if (notificationIdFromDeepLink != -1) {
                viewModel.markNotificationAsRead(notificationIdFromDeepLink)
                Log.d("NotificationScreen", "Deep link opened with notification ID: $notificationIdFromDeepLink. Marked as read.")
            }
        }

        // NEW: Filter notifications based on selected tab
        val filteredNotifications = remember(notifications, selectedTab) {
            notifications.filter { notification ->
                when (selectedTab) {
                    // Group Notifications Tab
                    0 -> notification.type == NotificationType.GROUP_MESSAGE ||
                            notification.type == NotificationType.ALERT || // Alerts can be group wide
                            notification.type == NotificationType.SENT_MESSAGE_SIMULATED // Show sent messages in group context
                    // Individual Notifications Tab
                    1 -> notification.type == NotificationType.DIRECT_MESSAGE ||
                            notification.type == NotificationType.SYSTEM_MESSAGE || // System messages are usually individual
                            (notification.type == NotificationType.ALERT && notification.groupId == null) // Personal alerts
                    else -> false // Should not happen
                }
            }
        }


        AnimatedVisibility(
            visible = showScreen,
            enter = slideInHorizontally(animationSpec = tween(durationMillis = 500, delayMillis = 50)) { fullWidth ->
                fullWidth
            } + fadeIn(animationSpec = tween(500, delayMillis = 50)),
            exit = slideOutHorizontally(animationSpec = tween(durationMillis = 500)) { fullWidth ->
                fullWidth
            } + fadeOut(animationSpec = tween(500))
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Background Image
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
                                    "Notifications",
                                    color = DarkBlue,
                                    fontWeight = FontWeight.Bold
                                )
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = Color.White.copy(alpha = 0.7f)
                            ),
                            navigationIcon = {
                                IconButton(onClick = { navController.popBackStack() }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = DarkBlue)
                                }
                            }
                        )
                    },
                    containerColor = Color.Transparent
                ) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Tab Row for Group vs Individual Messages
                        TabRow(
                            selectedTabIndex = selectedTab,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp)),
                            containerColor = DarkBlue.copy(alpha = 0.8f),
                            contentColor = Color.White
                        ) {
                            Tab(
                                selected = selectedTab == 0,
                                onClick = { selectedTab = 0 },
                                text = { Text("Group Notifications", fontSize = 14.sp) },
                                selectedContentColor = Color.White,
                                unselectedContentColor = Color.White.copy(alpha = 0.6f)
                            )
                            Tab(
                                selected = selectedTab == 1,
                                onClick = { selectedTab = 1 },
                                text = { Text("Individual Notifications", fontSize = 14.sp) },
                                selectedContentColor = Color.White,
                                unselectedContentColor = Color.White.copy(alpha = 0.6f)
                            )
                        }

                        Spacer(Modifier.height(16.dp))

                        // Notification List
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .shadow(4.dp, RoundedCornerShape(12.dp)),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.9f))
                        ) {
                            if (filteredNotifications.isEmpty()) { // NEW: Use filteredNotifications
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "No notifications to display.",
                                        color = DarkBlue.copy(alpha = 0.7f),
                                        fontSize = 16.sp,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(16.dp)
                                    )
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(filteredNotifications) { notification -> // NEW: Use filteredNotifications
                                        NotificationItem(
                                            notification = notification,
                                            onMarkRead = { viewModel.markNotificationAsRead(it) },
                                            onDelete = { viewModel.deleteNotification(it) }
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        // Management Buttons (Mark all read, Delete all)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceAround,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = { viewModel.notifications.value.filter { !it.isRead }.forEach { viewModel.markNotificationAsRead(it.id) } }, // Mark all unread as read
                                colors = ButtonDefaults.buttonColors(containerColor = DarkBlue.copy(alpha = 0.8f)),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Filled.MarkEmailRead, "Mark All Read", tint = Color.White)
                                Spacer(Modifier.width(8.dp))
                                Text("Mark All Read", color = Color.White)
                            }
                            Button(
                                onClick = { showConfirmDeleteAllDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Filled.Delete, "Delete All", tint = Color.White)
                                Spacer(Modifier.width(8.dp))
                                Text("Delete All", color = Color.White)
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        // Send Notification Section
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .shadow(4.dp, RoundedCornerShape(12.dp)),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.9f))
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = when (selectedTab) {
                                        0 -> "Send Group Message"
                                        1 -> "Send Individual Message"
                                        else -> ""
                                    },
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = DarkBlue
                                )
                                OutlinedTextField(
                                    value = notificationMessage,
                                    onValueChange = { notificationMessage = it },
                                    label = { Text("Your Message", color = DarkBlue) },
                                    modifier = Modifier.fillMaxWidth().height(100.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = DarkBlue, unfocusedTextColor = DarkBlue.copy(alpha = 0.8f),
                                        focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                                        focusedBorderColor = DarkBlue, unfocusedBorderColor = DarkBlue.copy(alpha = 0.5f),
                                        focusedLabelColor = DarkBlue, unfocusedLabelColor = DarkBlue.copy(alpha = 0.7f),
                                        cursorColor = DarkBlue
                                    ),
                                    singleLine = false
                                )

                                Button(
                                    onClick = {
                                        if (notificationMessage.isBlank()) {
                                            Toast.makeText(context, "Message cannot be empty.", Toast.LENGTH_SHORT).show()
                                            return@Button
                                        }

                                        when (selectedTab) {
                                            0 -> { // Send to Group
                                                if (isInActiveGroup) {
                                                    val currentUserId = auth.currentUser?.uid
                                                    val groupId = activeGroup?.groupID
                                                    if (currentUserId != null && groupId != null) {
                                                        Log.d("NotificationScreen", "Sending Group Message to $groupId: $notificationMessage")
                                                        Toast.makeText(context, "Group message sent (simulated).", Toast.LENGTH_SHORT).show()

                                                        coroutineScope.launch {
                                                            viewModel.insertNotification(
                                                                NotificationEntity(
                                                                    messageId = null,
                                                                    title = "Sent to Group: ${activeGroup?.groupName}",
                                                                    body = notificationMessage,
                                                                    timestamp = System.currentTimeMillis(),
                                                                    type = NotificationType.SENT_MESSAGE_SIMULATED, // NEW: Use SENT_MESSAGE_SIMULATED type
                                                                    senderId = currentUserId,
                                                                    groupId = groupId,
                                                                    isRead = true,
                                                                    dataPayload = "{}"
                                                                )
                                                            )
                                                        }
                                                        notificationMessage = ""
                                                    } else {
                                                        Toast.makeText(context, "Not in an active group or user not logged in.", Toast.LENGTH_SHORT).show()
                                                    }
                                                } else {
                                                    Toast.makeText(context, "You are not in an active group to send a group message.", Toast.LENGTH_LONG).show()
                                                }
                                            }
                                            1 -> { // Send to Individual (Placeholder for future Firebase Function)
                                                Toast.makeText(context, "Direct messaging coming soon (requires Firebase Function).", Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = DarkBlue)
                                ) {
                                    Icon(Icons.Filled.Send, "Send Message", tint = Color.White)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Send Message", color = Color.White)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Confirm Delete All Dialog
        if (showConfirmDeleteAllDialog) {
            AlertDialog(
                onDismissRequest = { showConfirmDeleteAllDialog = false },
                title = { Text("Delete All Notifications?") },
                text = { Text("Are you sure you want to delete all notifications? This action cannot be undone.") },
                confirmButton = {
                    Button(onClick = {
                        viewModel.deleteAllNotifications()
                        showConfirmDeleteAllDialog = false
                        Toast.makeText(context, "All notifications deleted.", Toast.LENGTH_SHORT).show()
                    }) {
                        Text("Delete All")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showConfirmDeleteAllDialog = false }) {
                        Text("Cancel")
                    }
                },
                containerColor = LightCream
            )
        }
    }

    @Composable
    fun NotificationItem(
        notification: NotificationEntity,
        onMarkRead: (Int) -> Unit,
        onDelete: (Int) -> Unit
    ) {
        // Determine background color based on read status and if it's a simulated sent message
        val backgroundColor = when {
            notification.isRead -> Color.White.copy(alpha = 0.7f)
            notification.type == NotificationType.SENT_MESSAGE_SIMULATED -> Color(0xFFE0E0E0).copy(alpha = 0.7f) // Slightly darker for sent
            else -> LightCream // Unread received messages
        }
        val textColor = DarkBlue
        val timestampText = com.artificialinsightsllc.teamsync.Helpers.TimeFormatter.getRelativeTimeSpanString(notification.timestamp)

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(2.dp, RoundedCornerShape(12.dp))
                .clickable { onMarkRead(notification.id) },
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = backgroundColor)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // NEW: Display type prefix for clarity (e.g., "[Group] Title")
                    val titlePrefix = when (notification.type) {
                        NotificationType.GROUP_MESSAGE -> "[Group] "
                        NotificationType.DIRECT_MESSAGE -> "[Direct] "
                        NotificationType.ALERT -> "[Alert] "
                        NotificationType.SYSTEM_MESSAGE -> "[System] "
                        NotificationType.SENT_MESSAGE_SIMULATED -> "[Sent] "
                        else -> ""
                    }
                    Text(
                        text = "$titlePrefix${notification.title ?: "No Title"}", // NEW: Add prefix
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = textColor,
                        modifier = Modifier.weight(1f),
                        overflow = TextOverflow.Ellipsis,
                        maxLines = 1
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = timestampText.toString(),
                        fontSize = 12.sp,
                        color = textColor.copy(alpha = 0.7f)
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = notification.body ?: "No Content",
                    fontSize = 14.sp,
                    color = textColor,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    if (!notification.isRead) {
                        TextButton(onClick = { onMarkRead(notification.id) }) {
                            Text("Mark Read", color = DarkBlue)
                        }
                    }
                    TextButton(onClick = { onDelete(notification.id) }) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}
