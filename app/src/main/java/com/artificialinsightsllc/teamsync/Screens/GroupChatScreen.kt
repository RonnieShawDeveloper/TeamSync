// In file: app/src/main/java/com/artificialinsightsllc/teamsync/Screens/GroupChatScreen.kt
package com.artificialinsightsllc.teamsync.Screens

import android.app.Activity
import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import android.view.WindowMetrics
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Comment
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Delete // NEW: Import Delete icon
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import coil.compose.rememberAsyncImagePainter
import com.artificialinsightsllc.teamsync.Helpers.TimeFormatter
import com.artificialinsightsllc.teamsync.Models.ChatMessage
import com.artificialinsightsllc.teamsync.Models.ChatMessageType
import com.artificialinsightsllc.teamsync.Models.Comment
import com.artificialinsightsllc.teamsync.Models.UserModel
import com.artificialinsightsllc.teamsync.R
import com.artificialinsightsllc.teamsync.ViewModels.GroupChatViewModel
import com.artificialinsightsllc.teamsync.ui.theme.DarkBlue
import com.artificialinsightsllc.teamsync.ui.theme.LightCream
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import com.yalantis.ucrop.UCrop
import kotlinx.coroutines.delay
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalConfiguration
import com.yalantis.ucrop.UCropActivity
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

class GroupChatScreen(private val navController: NavHostController) {

    // Define a Saver for Uri, to retain state across process death
    private val UriSaver = Saver<Uri?, String>(
        save = { it?.toString() ?: "" }, // Save null as empty string
        restore = { if (it.isNotBlank()) Uri.parse(it) else null } // Restore empty string as null
    )

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun Content(viewModel: GroupChatViewModel = viewModel()) {
        val context = LocalContext.current
        val coroutineScope = rememberCoroutineScope()

        val chatMessages by viewModel.chatMessages.collectAsStateWithLifecycle()
        val userProfiles by viewModel.userProfiles.collectAsStateWithLifecycle()
        val isLoadingInitialMessages by viewModel.isLoadingInitialMessages.collectAsStateWithLifecycle()
        val isLoadingMoreMessages by viewModel.isLoadingMoreMessages.collectAsStateWithLifecycle()
        val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
        val showToast by viewModel.showToast.collectAsStateWithLifecycle(initialValue = null)

        val showCommentDialog by viewModel.showCommentDialog.collectAsStateWithLifecycle()
        val selectedMessageForComments by viewModel.selectedMessageForComments.collectAsStateWithLifecycle()
        val commentsForSelectedMessage by viewModel.comments.collectAsStateWithLifecycle() // For comments dialog

        val activeGroup by viewModel.groupMonitorService.activeGroup.collectAsStateWithLifecycle()
        val currentUserId = viewModel.auth.currentUser?.uid // Get current user ID

        var showScreen by remember { mutableStateOf(false) } // For slide-in animation
        val listState = rememberLazyListState()

        var messageInput by remember { mutableStateOf(TextFieldValue("")) }
        var selectedImageUri by rememberSaveable(stateSaver = UriSaver) { mutableStateOf<Uri?>(null) } // Local state for selected/captured image
        var showImageSourceDialog by remember { mutableStateOf(false) } // To choose camera or gallery

        // --- Permissions and Image Launchers ---
        var tempPhotoUri: Uri? by rememberSaveable(stateSaver = UriSaver) { mutableStateOf(null) }

        // State for delete confirmation dialog
        var showDeleteConfirmationDialog by remember { mutableStateOf(false) }
        var messageToDelete by remember { mutableStateOf<ChatMessage?>(null) }


        val cropResultLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                selectedImageUri = UCrop.getOutput(result.data!!)
                Log.d("GroupChatScreen", "Image cropped and selected: $selectedImageUri")
            } else if (result.resultCode == UCrop.RESULT_ERROR) {
                val cropError = UCrop.getError(result.data!!)
                Log.e("GroupChatScreen", "UCrop error: $cropError")
                Toast.makeText(context, "Image cropping failed.", Toast.LENGTH_SHORT).show()
                selectedImageUri = null
            } else {
                Log.d("GroupChatScreen", "UCrop cancelled or returned unexpected result: ${result.resultCode}")
                selectedImageUri = null
            }
            // Clean up original temp file if it exists after cropping
            tempPhotoUri?.path?.let { path ->
                val file = File(path)
                // Ensure it's truly a temporary file created by us for the camera
                if (file.exists() && file.name.startsWith("temp_image_")) {
                    file.delete()
                    Log.d("GroupChatScreen", "Deleted temporary source image file: ${file.absolutePath}")
                }
            }
            tempPhotoUri = null // Clear temp URI
        }

        val takePhotoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            if (success && tempPhotoUri != null) {
                Log.d("GroupChatScreen", "Photo captured to: $tempPhotoUri")
                val destinationUri = Uri.fromFile(File(context.cacheDir, "cropped_chat_photo.jpg"))
                val options = UCrop.Options().apply {
                    setToolbarTitle("Crop Photo")
                    setToolbarColor(DarkBlue.toArgb())
                    setStatusBarColor(DarkBlue.toArgb())
                    setToolbarWidgetColor(Color.White.toArgb())
                    setAllowedGestures(UCropActivity.NONE, UCropActivity.SCALE, UCropActivity.SCALE)
                    setShowCropGrid(true) // optional
                    setFreeStyleCropEnabled(false) // optional, enforces fixed crop box
                    setHideBottomControls(false) // hides rotate/scale buttons (optional for extra lock-down)
                }
                val uCropIntent = UCrop.of(tempPhotoUri!!, destinationUri)
                    .withOptions(options)
                    .withAspectRatio(1f, 1f)
                    .getIntent(context) // Get the Intent from UCrop
                cropResultLauncher.launch(uCropIntent) // Launch the intent
            } else {
                Toast.makeText(context, "Photo capture cancelled or failed.", Toast.LENGTH_SHORT).show()
                tempPhotoUri?.path?.let { File(it).delete() } // Clean up temp file
                tempPhotoUri = null
                selectedImageUri = null
            }
        }

        val selectImageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let {
                val destinationUri = Uri.fromFile(File(context.cacheDir, "cropped_chat_photo.jpg"))
                val options = UCrop.Options().apply {
                    setToolbarTitle("Crop Photo")
                    setToolbarColor(DarkBlue.toArgb())
                    setStatusBarColor(DarkBlue.toArgb())
                    setToolbarWidgetColor(Color.White.toArgb())
                    setAllowedGestures(UCropActivity.NONE, UCropActivity.SCALE, UCropActivity.SCALE)
                    setShowCropGrid(true) // optional
                    setFreeStyleCropEnabled(false) // optional, enforces fixed crop box
                    setHideBottomControls(false) // hides rotate/scale buttons (optional for extra lock-down)
                }
                val uCropIntent = UCrop.of(it, destinationUri)
                    .withOptions(options)
                    .withAspectRatio(1f, 1f)
                    .getIntent(context) // Get the Intent from UCrop
                cropResultLauncher.launch(uCropIntent) // Launch the intent
            } ?: run {
                selectedImageUri = null
                Toast.makeText(context, "Image selection cancelled.", Toast.LENGTH_SHORT).show()
            }
        }

        val cameraPermissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            if (isGranted) {
                val photoFile = File(context.cacheDir, "temp_image_${System.currentTimeMillis()}.jpg")
                tempPhotoUri = FileProvider.getUriForFile(context, "${context.packageName}.provider", photoFile)
                takePhotoLauncher.launch(tempPhotoUri!!)
            } else {
                Toast.makeText(context, "Camera permission is required to take photos.", Toast.LENGTH_SHORT).show()
            }
        }

        // --- Effects ---

        LaunchedEffect(Unit) {
            showScreen = true
            viewModel.markChatAsSeen() // Mark chat as seen when entering the screen
        }

        // Observe toast events from ViewModel
        LaunchedEffect(showToast) {
            showToast?.let { message ->
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }
        }

        // Pagination: Load more messages when user scrolls near the end
        LaunchedEffect(listState) {
            snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
                .collect { lastVisibleIndex ->
                    // Trigger load more if we are near the end (last 5 items) and not already loading
                    if (lastVisibleIndex != null && lastVisibleIndex >= chatMessages.size - 5 && !isLoadingMoreMessages && !isLoadingInitialMessages) {
                        viewModel.loadMoreMessages()
                    }
                }
        }


        // --- UI Structure ---
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

                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = {
                                Text(
                                    text = activeGroup?.groupName ?: "Group Chat",
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
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding) // Base padding from Scaffold
                            .imePadding(), // Handles keyboard pushing content up
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Chat Message List
                        SwipeRefresh(
                            state = rememberSwipeRefreshState(isRefreshing),
                            onRefresh = { viewModel.refreshMessages() },
                            modifier = Modifier.weight(1f) // Takes remaining vertical space
                        ) {
                            LazyColumn(
                                state = listState,
                                reverseLayout = true, // To show newest messages at the bottom
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 8.dp), // Horizontal padding for the list items
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                // contentPadding is removed as imePadding on parent handles it
                            ) {
                                if (isLoadingInitialMessages) {
                                    item {
                                        Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                                            CircularProgressIndicator(color = DarkBlue)
                                        }
                                    }
                                } else if (chatMessages.isEmpty()) {
                                    item {
                                        Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                                            Text("No messages yet. Be the first to chat!", color = DarkBlue.copy(alpha = 0.7f))
                                        }
                                    }
                                } else {
                                    items(chatMessages, key = { it.id }) { message ->
                                        // Pass the needed info to ChatItem, ViewModel to handle interactions
                                        val senderProfile = userProfiles[message.senderId]
                                        // Pass delete function and current user/group owner IDs
                                        ChatItem(
                                            message = message,
                                            senderProfile = senderProfile,
                                            currentUserId = currentUserId,
                                            groupOwnerId = activeGroup?.groupOwnerId, // Pass group owner ID
                                            onLikeClick = { msgId, currentLikes -> viewModel.toggleLike(msgId, currentLikes) },
                                            onCommentClick = { viewModel.showComments(it) },
                                            onDeleteClick = { msg ->
                                                messageToDelete = msg
                                                showDeleteConfirmationDialog = true
                                            }
                                        )
                                    }
                                    if (isLoadingMoreMessages) {
                                        item {
                                            Box(modifier = Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                                                CircularProgressIndicator(color = DarkBlue)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Message Input Area
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .wrapContentHeight()
                                .navigationBarsPadding() // Adjust for nav bars at bottom
                                .shadow(8.dp, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)),
                            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.7f))
                        ) {
                            Column(
                                modifier = Modifier.padding(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                // Image Preview / Message Input Row
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.Bottom,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    // Image Preview (if selected)
                                    if (selectedImageUri != null) {
                                        Box(modifier = Modifier.size(80.dp).clip(RoundedCornerShape(8.dp))) {
                                            Image(
                                                painter = rememberAsyncImagePainter(selectedImageUri),
                                                contentDescription = "Selected Image Preview",
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                            IconButton(
                                                onClick = { selectedImageUri = null },
                                                modifier = Modifier
                                                    .align(Alignment.TopEnd)
                                                    .size(24.dp)
                                                    .clip(CircleShape)
                                                    .background(Color.Black.copy(alpha = 0.5f))
                                            ) {
                                                Icon(Icons.Default.Close, "Remove Image", tint = Color.White, modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    }

                                    OutlinedTextField(
                                        value = messageInput,
                                        onValueChange = { messageInput = it },
                                        label = { Text("Type your message...", color = DarkBlue) },
                                        modifier = Modifier.weight(1f),
                                        minLines = 1,
                                        maxLines = 5,
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedTextColor = DarkBlue, unfocusedTextColor = DarkBlue.copy(alpha = 0.8f),
                                            focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                                            focusedBorderColor = DarkBlue, unfocusedBorderColor = DarkBlue.copy(alpha = 0.5f),
                                            focusedLabelColor = DarkBlue, unfocusedLabelColor = DarkBlue.copy(alpha = 0.7f),
                                            cursorColor = DarkBlue
                                        ),
                                        trailingIcon = {
                                            // Camera/Gallery Icons
                                            if (selectedImageUri == null) {
                                                Row {
                                                    IconButton(onClick = {
                                                        showImageSourceDialog = true
                                                    }) {
                                                        Icon(Icons.Default.AddAPhoto, "Add Photo", tint = DarkBlue)
                                                    }
                                                }
                                            }
                                        }
                                    )
                                }

                                // Send Button
                                Button(
                                    onClick = {
                                        if (selectedImageUri != null) {
                                            viewModel.sendPhotoMessage(selectedImageUri!!, messageInput.text.ifBlank { null })
                                            selectedImageUri = null
                                            messageInput = TextFieldValue("")
                                        } else {
                                            viewModel.sendTextMessage(messageInput.text)
                                            messageInput = TextFieldValue("")
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = messageInput.text.isNotBlank() || selectedImageUri != null,
                                    colors = ButtonDefaults.buttonColors(containerColor = DarkBlue)
                                ) {
                                    Icon(Icons.Default.Send, "Send Message", tint = Color.White)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Send", color = Color.White)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Image Source Dialog
        if (showImageSourceDialog) {
            AlertDialog(
                onDismissRequest = { showImageSourceDialog = false },
                title = { Text("Choose Image Source") },
                text = { Text("Select an option to add a photo to your chat message.") },
                confirmButton = {
                    Button(onClick = {
                        showImageSourceDialog = false
                        // Check camera permission and launch
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                            val photoFile = File(context.cacheDir, "temp_image_${System.currentTimeMillis()}.jpg")
                            tempPhotoUri = FileProvider.getUriForFile(context, "${context.packageName}.provider", photoFile)
                            takePhotoLauncher.launch(tempPhotoUri!!)
                        } else {
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    }) {
                        Text("Take Photo")
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showImageSourceDialog = false
                        selectImageLauncher.launch("image/*") // Launch gallery picker
                    }) {
                        Text("Select from Gallery")
                    }
                }
            )
        }

        // Comments Dialog
        if (showCommentDialog && selectedMessageForComments != null) {
            CommentDialog(
                message = selectedMessageForComments!!,
                comments = commentsForSelectedMessage,
                onDismissRequest = { viewModel.dismissComments() },
                onAddComment = { messageId, text -> viewModel.addComment(messageId, text) },
                userProfiles = userProfiles,
                currentUserId = viewModel.auth.currentUser?.uid // Pass current user ID to CommentDialog
            )
        }

        // Delete Confirmation Dialog
        if (showDeleteConfirmationDialog && messageToDelete != null) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirmationDialog = false; messageToDelete = null },
                title = { Text("Delete Message?") },
                text = { Text("Are you sure you want to delete this message? This action cannot be undone and will delete all associated comments and photos.") },
                confirmButton = {
                    Button(
                        onClick = {
                            messageToDelete?.let { viewModel.deleteChatMessage(it) }
                            showDeleteConfirmationDialog = false
                            messageToDelete = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Delete", color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirmationDialog = false; messageToDelete = null }) {
                        Text("Cancel")
                    }
                }
            )
        }
    } // End of GroupChatScreen.Content

    // --- Composable for a single Chat Message Item ---
    @Composable
    fun ChatItem(
        message: ChatMessage,
        senderProfile: UserModel?,
        currentUserId: String?,
        groupOwnerId: String?, // NEW: Pass group owner ID
        onLikeClick: (String, List<String>) -> Unit,
        onCommentClick: (ChatMessage) -> Unit,
        onDeleteClick: (ChatMessage) -> Unit // NEW: Callback for delete
    ) {
        val isCurrentUser = message.senderId == currentUserId
        // Determine if the current user has permission to delete this message
        val canDeleteMessage = isCurrentUser || (currentUserId == groupOwnerId)

        val horizontalAlignment = if (isCurrentUser) Alignment.End else Alignment.Start
        val backgroundColor = if (isCurrentUser) DarkBlue.copy(alpha = 0.85f) else LightCream.copy(alpha = 0.85f)
        val textColor = if (isCurrentUser) Color.White else DarkBlue
        val profilePhotoUrl = senderProfile?.profilePhotoUrl
        val displayName = senderProfile?.displayName ?: "Unknown User"
        val timeAgo = TimeFormatter.getRelativeTimeSpanString(message.timestamp).toString()

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .shadow(4.dp, RoundedCornerShape(16.dp)),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = backgroundColor)
        ) {
            Column(
                modifier = Modifier
                    .padding(12.dp)
                    .fillMaxWidth(),
                horizontalAlignment = horizontalAlignment // Now uses the correctly typed variable
            ) {
                // Sender Info (Profile Pic, Name, Time Ago)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = if (isCurrentUser) Arrangement.End else Arrangement.Start
                ) {
                    if (!isCurrentUser) { // Show sender profile for others
                        Image(
                            painter = rememberAsyncImagePainter(
                                model = profilePhotoUrl,
                                error = painterResource(id = R.drawable.default_profile_pic)
                            ),
                            contentDescription = "${displayName}'s Profile",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color.Gray)
                        )
                        Spacer(Modifier.width(8.dp))
                    }

                    Column(horizontalAlignment = if (isCurrentUser) Alignment.End else Alignment.Start) {
                        Text(
                            text = displayName,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = textColor
                        )
                        Text(
                            text = timeAgo,
                            fontSize = 10.sp,
                            color = textColor.copy(alpha = 0.7f)
                        )
                    }

                    if (isCurrentUser) { // Show sender profile for current user on the right
                        Spacer(Modifier.width(8.dp))
                        Image(
                            painter = rememberAsyncImagePainter(
                                model = profilePhotoUrl,
                                error = painterResource(id = R.drawable.default_profile_pic)
                            ),
                            contentDescription = "${displayName}'s Profile",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color.Gray)
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Message Content (Text or Image)
                if (message.chatMessageType == ChatMessageType.PHOTO && !message.imageUrl.isNullOrBlank()) {
                    // Get the current screen width
                    val configuration = LocalConfiguration.current
                    val screenWidth = configuration.screenWidthDp.dp // Screen width in Dp
                    Image(
                        painter = rememberAsyncImagePainter(model = message.imageUrl),
                        contentDescription = "Shared Photo",
                        contentScale = ContentScale.FillWidth, // This is the key change
                        modifier = Modifier
                            .aspectRatio(1f) // Maintain aspect ratio
                            .fillMaxHeight()
                            .width(screenWidth - 20.dp) // Fill width minus padding
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.LightGray)
                    )
                    Spacer(Modifier.height(4.dp))
                }
                message.message?.let {
                    Text(
                        text = it,
                        fontSize = 16.sp,
                        color = textColor,
                        textAlign = if (isCurrentUser) TextAlign.End else TextAlign.Start,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(Modifier.height(8.dp))

                // Likes, Comments, and Delete Section
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween, // Use SpaceBetween to push delete to start, others to end
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Delete Button (conditionally visible)
                    if (canDeleteMessage) {
                        TextButton(onClick = { onDeleteClick(message) }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete Message",
                                tint = MaterialTheme.colorScheme.error // Red color for delete
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("Delete", color = MaterialTheme.colorScheme.error)
                        }
                    } else {
                        // Empty box to maintain layout balance if delete button is not shown
                        Box(modifier = Modifier.width(48.dp)) {} // Match approximate width of delete button
                    }


                    Row(verticalAlignment = Alignment.CenterVertically) { // Group Likes and Comments
                        // Like Button
                        val isLiked = message.likes.contains(currentUserId)
                        TextButton(onClick = { onLikeClick(message.id, message.likes) }) {
                            Icon(
                                imageVector = Icons.Default.Favorite,
                                contentDescription = "Like",
                                tint = if (isLiked) Color.Red else textColor.copy(alpha = 0.7f)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(message.likes.size.toString(), color = textColor.copy(alpha = 0.7f))
                        }

                        Spacer(Modifier.width(8.dp))

                        // Comment Button
                        TextButton(onClick = { onCommentClick(message) }) {
                            Icon(
                                imageVector = Icons.Default.Comment,
                                contentDescription = "Comment",
                                tint = textColor.copy(alpha = 0.7f)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(message.commentCount.toString(), color = textColor.copy(alpha = 0.7f))
                        }
                    }
                }
            }
        }
    } // End of ChatItem Composable

    // --- Composable for Comment Dialog ---
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun CommentDialog(
        message: ChatMessage,
        comments: List<Comment>,
        onDismissRequest: () -> Unit,
        onAddComment: (String, String) -> Unit,
        userProfiles: Map<String, UserModel>,
        currentUserId: String?
    ) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val coroutineScope = rememberCoroutineScope()
        var commentInput by remember { mutableStateOf(TextFieldValue("")) }

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
                    .fillMaxSize()
                    .background(Color.Transparent)
            ) {
                // Background Image
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
                        .fillMaxSize()
                        .padding(top = 16.dp, start = 16.dp, end = 16.dp)
                        .imePadding() // Adjust for keyboard
                        .navigationBarsPadding(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "Comments",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = DarkBlue,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Divider(color = DarkBlue.copy(alpha = 0.5f), thickness = 1.dp, modifier = Modifier.padding(bottom = 8.dp))

                    // Original Post Preview (Optional, can be a summary)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight()
                            .padding(bottom = 8.dp)
                            .shadow(4.dp, RoundedCornerShape(12.dp)),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = LightCream.copy(alpha = 0.9f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val senderProfile = userProfiles[message.senderId]
                                Image(
                                    painter = rememberAsyncImagePainter(
                                        model = senderProfile?.profilePhotoUrl,
                                        error = painterResource(id = R.drawable.default_profile_pic)
                                    ),
                                    contentDescription = "Sender Profile",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(24.dp).clip(CircleShape).background(Color.Gray)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    senderProfile?.displayName ?: "Unknown User",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                    color = DarkBlue
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    TimeFormatter.getRelativeTimeSpanString(message.timestamp).toString(),
                                    fontSize = 10.sp,
                                    color = DarkBlue.copy(alpha = 0.7f)
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            message.message?.let {
                                Text(it, fontSize = 14.sp, color = DarkBlue, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                            if (message.chatMessageType == ChatMessageType.PHOTO && !message.imageUrl.isNullOrBlank()) {
                                Image(
                                    painter = rememberAsyncImagePainter(message.imageUrl),
                                    contentDescription = "Original Photo",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(100.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color.LightGray)
                                )
                            }
                        }
                    }

                    // Comments List
                    if (comments.isEmpty()) {
                        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Text("No comments yet. Be the first!", color = DarkBlue.copy(alpha = 0.7f))
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            contentPadding = WindowInsets.systemBars.asPaddingValues()
                        ) {
                            items(comments, key = { it.id }) { comment ->
                                CommentItem(
                                    comment = comment,
                                    senderProfile = userProfiles[comment.senderId],
                                    currentUserId = currentUserId
                                )
                            }
                        }
                    }

                    // Add Comment Input
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = commentInput,
                            onValueChange = { commentInput = it },
                            label = { Text("Add a comment...", color = DarkBlue) },
                            modifier = Modifier.weight(1f),
                            singleLine = false,
                            maxLines = 3,
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
                                onAddComment(message.id, commentInput.text)
                                commentInput = TextFieldValue("")
                            },
                            enabled = commentInput.text.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(containerColor = DarkBlue)
                        ) {
                            Icon(Icons.Default.Send, "Send Comment", tint = Color.White)
                        }
                    }
                }
            }
        }
    } // End of CommentDialog

    // --- Composable for a single Comment Item ---
    @Composable
    fun CommentItem(
        comment: Comment,
        senderProfile: UserModel?,
        currentUserId: String?
    ) {
        val isCurrentUser = comment.senderId == currentUserId
        // FIXED: Direct assignment of Alignment.Horizontal instances
        val horizontalAlignment = if (isCurrentUser) Alignment.End else Alignment.Start
        val backgroundColor = if (isCurrentUser) DarkBlue.copy(alpha = 0.1f) else LightCream.copy(alpha = 0.7f) // Lighter for comments
        val textColor = if (isCurrentUser) DarkBlue.copy(alpha = 0.9f) else DarkBlue
        val profilePhotoUrl = senderProfile?.profilePhotoUrl
        val displayName = senderProfile?.displayName ?: "Unknown User"
        val timeAgo = TimeFormatter.getRelativeTimeSpanString(comment.timestamp).toString()

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp)
                .shadow(1.dp, RoundedCornerShape(8.dp)),
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = backgroundColor)
        ) {
            Column(
                modifier = Modifier.padding(8.dp),
                horizontalAlignment = horizontalAlignment // Now uses the correctly typed variable
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = if (isCurrentUser) Arrangement.End else Arrangement.Start
                ) {
                    if (!isCurrentUser) {
                        Image(
                            painter = rememberAsyncImagePainter(
                                model = profilePhotoUrl,
                                error = painterResource(id = R.drawable.default_profile_pic)
                            ),
                            contentDescription = "${displayName}'s Profile",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(Color.Gray)
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        text = displayName,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        color = textColor
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = timeAgo,
                        fontSize = 9.sp,
                        color = textColor.copy(alpha = 0.6f)
                    )
                    if (isCurrentUser) {
                        Spacer(Modifier.width(6.dp))
                        Image(
                            painter = rememberAsyncImagePainter(
                                model = profilePhotoUrl,
                                error = painterResource(id = R.drawable.default_profile_pic)
                            ),
                            contentDescription = "${displayName}'s Profile",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(24.dp).clip(CircleShape).background(Color.Gray)
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = comment.text,
                    fontSize = 13.sp,
                    color = textColor,
                    textAlign = if (isCurrentUser) TextAlign.End else TextAlign.Start,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    } // End of CommentItem Composable

}
