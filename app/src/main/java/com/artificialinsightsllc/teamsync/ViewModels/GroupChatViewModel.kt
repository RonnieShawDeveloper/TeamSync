// In file: app/src/main/java/com/artificialinsightsllc/teamsync/ViewModels/GroupChatViewModel.kt
package com.artificialinsightsllc.teamsync.ViewModels

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.artificialinsightsllc.teamsync.Models.ChatMessage
import com.artificialinsightsllc.teamsync.Models.ChatMessageType
import com.artificialinsightsllc.teamsync.Models.Comment
import com.artificialinsightsllc.teamsync.Models.UserModel
import com.artificialinsightsllc.teamsync.Services.FirestoreService
import com.artificialinsightsllc.teamsync.Services.GroupMonitorService
import com.artificialinsightsllc.teamsync.TeamSyncApplication
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID

class GroupChatViewModel(application: Application) : AndroidViewModel(application) {

    // Dependencies
    private val firestoreService: FirestoreService = FirestoreService()
    internal val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val firebaseStorage: FirebaseStorage = FirebaseStorage.getInstance()
    internal val groupMonitorService: GroupMonitorService = (application as TeamSyncApplication).groupMonitorService

    // Chat Messages State
    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()

    // User Profiles Map (for displaying sender info)
    private val _userProfiles = MutableStateFlow<Map<String, UserModel>>(emptyMap())
    val userProfiles: StateFlow<Map<String, UserModel>> = _userProfiles.asStateFlow()

    // Comments for a specific message (for comment dialog)
    private val _comments = MutableStateFlow<List<Comment>>(emptyList())
    val comments: StateFlow<List<Comment>> = _comments.asStateFlow()

    // UI states
    private val _isLoadingInitialMessages = MutableStateFlow(true)
    val isLoadingInitialMessages: StateFlow<Boolean> = _isLoadingInitialMessages.asStateFlow()

    private val _isLoadingMoreMessages = MutableStateFlow(false)
    val isLoadingMoreMessages: StateFlow<Boolean> = _isLoadingMoreMessages.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _showToast = MutableSharedFlow<String>()
    val showToast: SharedFlow<String> = _showToast.asSharedFlow()

    private val _showCommentDialog = MutableStateFlow(false)
    val showCommentDialog: StateFlow<Boolean> = _showCommentDialog.asStateFlow()

    private val _selectedMessageForComments = MutableStateFlow<ChatMessage?>(null)
    val selectedMessageForComments: StateFlow<ChatMessage?> = _selectedMessageForComments.asStateFlow()

    // Pagination/Refresh State
    private var lastLoadedMessageTimestamp: Long? = null
    private var lastLoadedMessageId: String? = null
    private var firstMessageTimestamp: Long? = null // To track the newest message for pull-to-refresh
    private var allMessagesLoaded = false

    // Constants
    private val PAGE_SIZE = 50L // Load 50 messages at a time

    // Private variable for new message count
    private var _newMessagesCount = MutableStateFlow(0)
    val newMessagesCount: StateFlow<Int> = _newMessagesCount.asStateFlow()

    private var lastSeenTimestamp: Long = 0L // To keep track of the last time chat was opened/seen


    init {
        Log.d("GroupChatViewModel", "GroupChatViewModel initialized.")

        viewModelScope.launch {
            // Observe active group changes and load chat messages
            groupMonitorService.activeGroup.collect { activeGroup ->
                val groupId = activeGroup?.groupID
                if (groupId != null) {
                    Log.d("GroupChatViewModel", "Active group changed to $groupId. Loading chat messages.")
                    resetAndLoadInitialMessages(groupId)
                } else {
                    _chatMessages.value = emptyList()
                    _userProfiles.value = emptyMap()
                    _isLoadingInitialMessages.value = false
                    _isLoadingMoreMessages.value = false
                    _isRefreshing.value = false
                    _comments.value = emptyList()
                    _selectedMessageForComments.value = null
                    _showCommentDialog.value = false
                    lastLoadedMessageTimestamp = null
                    lastLoadedMessageId = null
                    firstMessageTimestamp = null
                    allMessagesLoaded = false
                    _newMessagesCount.value = 0 // Reset new message count when group changes
                    Log.d("GroupChatViewModel", "No active group. Chat messages cleared.")
                }
            }
        }

        // Combine chat messages and fetch sender profiles
        viewModelScope.launch {
            _chatMessages.collect { messages ->
                val senderIds = messages.map { it.senderId }.distinct().filter { it.isNotBlank() }
                if (senderIds.isNotEmpty()) {
                    val existingProfiles = _userProfiles.value.toMutableMap()
                    val newSenderIds = senderIds.filter { !existingProfiles.containsKey(it) }

                    if (newSenderIds.isNotEmpty()) {
                        firestoreService.getUserProfilesByIds(newSenderIds.toList()).onSuccess { newProfiles ->
                            newProfiles.forEach { profile ->
                                existingProfiles[profile.userId] = profile
                            }
                            _userProfiles.value = existingProfiles
                            Log.d("GroupChatViewModel", "Fetched ${newProfiles.size} new user profiles for chat senders.")
                        }.onFailure { e ->
                            Log.e("GroupChatViewModel", "Failed to fetch user profiles for chat senders: ${e.message}")
                            _showToast.emit("Failed to load some user profiles.")
                        }
                    }
                }
            }
        }

        // Observe comments for the selected message
        viewModelScope.launch {
            _selectedMessageForComments.collect { message ->
                if (message != null) {
                    loadCommentsForMessage(message.id)
                } else {
                    _comments.value = emptyList()
                }
            }
        }
    }

    // --- Message Loading and Pagination ---

    private suspend fun resetAndLoadInitialMessages(groupId: String) {
        _isLoadingInitialMessages.value = true
        _chatMessages.value = emptyList()
        _userProfiles.value = emptyMap() // Clear profiles to ensure fresh fetch
        lastLoadedMessageTimestamp = null
        lastLoadedMessageId = null
        firstMessageTimestamp = null
        allMessagesLoaded = false
        _newMessagesCount.value = 0 // Reset new message count on group change
        lastSeenTimestamp = System.currentTimeMillis() // Reset last seen timestamp when chat loads initially

        loadMoreMessages(groupId, isInitialLoad = true)
    }

    fun loadMoreMessages() {
        val groupId = groupMonitorService.activeGroup.value?.groupID
        if (groupId != null && !isLoadingMoreMessages.value && !allMessagesLoaded) {
            loadMoreMessages(groupId, isInitialLoad = false)
        }
    }

    private fun loadMoreMessages(groupId: String, isInitialLoad: Boolean) {
        viewModelScope.launch {
            if (isInitialLoad) _isLoadingInitialMessages.value = true
            _isLoadingMoreMessages.value = true

            Log.d("GroupChatViewModel", "Loading messages for group $groupId. Initial: $isInitialLoad, Last timestamp: $lastLoadedMessageTimestamp, Last ID: $lastLoadedMessageId")

            val result = firestoreService.getChatMessages(
                groupId = groupId,
                limit = PAGE_SIZE,
                startAfterTimestamp = lastLoadedMessageTimestamp,
                startAfterDocumentId = lastLoadedMessageId
            )

            result.onSuccess { newMessages ->
                Log.d("GroupChatViewModel", "Fetched ${newMessages.size} new messages.")
                if (newMessages.isEmpty()) {
                    allMessagesLoaded = true
                    Log.d("GroupChatViewModel", "No more messages to load. All messages loaded.")
                    if (isInitialLoad && _chatMessages.value.isEmpty()) {
                        // Only show "no messages" if it's initial load AND truly empty
                        // Don't show toast for empty results during pagination (it means end of list)
                    }
                } else {
                    val currentMessages = _chatMessages.value.toMutableList()

                    // Ensure uniqueness and add new messages (Firestore pagination typically handles this, but a safeguard)
                    val uniqueNewMessages = newMessages.filter { newMessage ->
                        currentMessages.none { it.id == newMessage.id }
                    }

                    // Prepend new messages if pulling to refresh (newest first for DESCENDING query)
                    // Append new messages if lazy loading (older messages)
                    val updatedList = if (isInitialLoad || lastLoadedMessageTimestamp == null) {
                        uniqueNewMessages.sortedByDescending { it.timestamp } // Initial load or first pagination, ensure sorted newest to oldest
                    } else {
                        (currentMessages + uniqueNewMessages).sortedByDescending { it.timestamp }
                    }

                    _chatMessages.value = updatedList

                    // Update pagination cursors (oldest message from the *newly added batch*)
                    // Or, if using startAfterDocumentId, it would be the last document in the *previous* fetch
                    if (uniqueNewMessages.isNotEmpty()) {
                        val oldestNewMessage = uniqueNewMessages.minByOrNull { it.timestamp }
                        lastLoadedMessageTimestamp = oldestNewMessage?.timestamp
                        lastLoadedMessageId = oldestNewMessage?.id
                        firstMessageTimestamp = _chatMessages.value.maxByOrNull { it.timestamp }?.timestamp // Keep track of newest
                    }

                    Log.d("GroupChatViewModel", "Messages updated. Total messages: ${_chatMessages.value.size}. Oldest message timestamp: $lastLoadedMessageTimestamp, ID: $lastLoadedMessageId")
                }
            }.onFailure { e ->
                Log.e("GroupChatViewModel", "Error loading messages: ${e.message}")
                // Only show toast if it's not a "not found" error, or if it's the initial load.
                // Firebase FAILED_PRECONDITION errors due to missing indexes are valid errors.
                if (!e.message.orEmpty().contains("FAILED_PRECONDITION")) {
                    _showToast.emit("Error loading chat messages: ${e.message}")
                }
            }

            if (isInitialLoad) _isLoadingInitialMessages.value = false
            _isLoadingMoreMessages.value = false
        }
    }

    fun refreshMessages() {
        val groupId = groupMonitorService.activeGroup.value?.groupID
        if (groupId != null && !isRefreshing.value) {
            viewModelScope.launch {
                _isRefreshing.value = true
                Log.d("GroupChatViewModel", "Refreshing messages for group $groupId. Oldest current message timestamp: $firstMessageTimestamp")

                val result = firestoreService.getNewChatMessages(
                    groupId = groupId,
                    startAtTimestamp = firstMessageTimestamp ?: 0L // Fetch messages newer than the newest currently in cache
                )

                result.onSuccess { newMessages ->
                    Log.d("GroupChatViewModel", "Fetched ${newMessages.size} new messages on refresh.")
                    if (newMessages.isNotEmpty()) {
                        val currentMessages = _chatMessages.value.toMutableList()
                        val uniqueNewMessages = newMessages.filter { newMessage ->
                            currentMessages.none { it.id == newMessage.id }
                        }
                        if (uniqueNewMessages.isNotEmpty()) {
                            val combinedList = (uniqueNewMessages + currentMessages).sortedByDescending { it.timestamp }
                            _chatMessages.value = combinedList
                            firstMessageTimestamp = combinedList.maxByOrNull { it.timestamp }?.timestamp
                            Log.d("GroupChatViewModel", "Messages refreshed. Added ${uniqueNewMessages.size} new messages. Total messages: ${_chatMessages.value.size}")

                            // Update new message count
                            val latestMessageTimestampAfterRefresh = _chatMessages.value.firstOrNull()?.timestamp ?: 0L
                            val newUnreadMessages = _chatMessages.value.filter { it.timestamp > lastSeenTimestamp }
                            _newMessagesCount.value = newUnreadMessages.size
                        } else {
                            Log.d("GroupChatViewModel", "No truly new messages found on refresh that aren't already displayed.")
                        }
                    } else {
                        Log.d("GroupChatViewModel", "No new messages found on refresh.")
                    }
                }.onFailure { e ->
                    Log.e("GroupChatViewModel", "Error refreshing messages: ${e.message}")
                    _showToast.emit("Error refreshing chat: ${e.message}")
                }
                _isRefreshing.value = false
            }
        }
    }

    // --- Message Sending ---

    fun sendTextMessage(message: String) {
        val groupId = groupMonitorService.activeGroup.value?.groupID
        val senderId = auth.currentUser?.uid

        if (groupId == null || senderId == null) {
            viewModelScope.launch { _showToast.emit("Not in an active group or not logged in.") }
            return
        }
        if (message.isBlank()) {
            viewModelScope.launch { _showToast.emit("Message cannot be empty.") }
            return
        }

        viewModelScope.launch {
            val newChatMessage = ChatMessage(
                id = UUID.randomUUID().toString(), // Generate client-side ID
                groupId = groupId,
                senderId = senderId,
                timestamp = System.currentTimeMillis(),
                message = message,
                chatMessageType = ChatMessageType.TEXT
            )

            // Optimistic update for immediate display
            _chatMessages.update { listOf(newChatMessage) + it }
            Log.d("GroupChatViewModel", "Optimistically added text message.")

            firestoreService.addChatMessage(newChatMessage)
                .onSuccess { docId ->
                    Log.d("GroupChatViewModel", "Text message successfully sent with ID: $docId")
                    // If Firestore generated a new ID, update the local copy
                    if (newChatMessage.id != docId) {
                        _chatMessages.update { currentList ->
                            currentList.map { if (it.id == newChatMessage.id) it.copy(id = docId) else it }
                        }
                    }
                }
                .onFailure { e ->
                    Log.e("GroupChatViewModel", "Failed to send text message: ${e.message}", e)
                    _showToast.emit("Failed to send message: ${e.message}")
                    // Revert optimistic update on failure (more complex to implement properly for exact match)
                    _chatMessages.update { currentList -> currentList.filter { it.id != newChatMessage.id } }
                }
        }
    }

    fun sendPhotoMessage(photoUri: Uri, caption: String?) {
        val groupId = groupMonitorService.activeGroup.value?.groupID
        val senderId = auth.currentUser?.uid

        if (groupId == null || senderId == null) {
            viewModelScope.launch { _showToast.emit("Not in an active group or not logged in.") }
            return
        }

        viewModelScope.launch {
            val newChatMessageId = UUID.randomUUID().toString()
            val photoRef = firebaseStorage.reference.child("chat_photos/${groupId}/${newChatMessageId}.jpg")

            // Optimistic update with a placeholder URI for faster display
            val placeholderMessage = ChatMessage(
                id = newChatMessageId,
                groupId = groupId,
                senderId = senderId,
                timestamp = System.currentTimeMillis(),
                message = caption,
                imageUrl = photoUri.toString(), // Use local URI for optimistic display
                chatMessageType = ChatMessageType.PHOTO
            )
            _chatMessages.update { listOf(placeholderMessage) + it }
            _showToast.emit("Uploading photo...")
            Log.d("GroupChatViewModel", "Optimistically added photo message placeholder.")

            try {
                // Upload photo
                val uploadResult = photoRef.putFile(photoUri).await()
                val photoUrl = uploadResult.storage.downloadUrl.await().toString()

                // Create final ChatMessage with uploaded URL
                val finalChatMessage = placeholderMessage.copy(imageUrl = photoUrl)

                // Save to Firestore
                firestoreService.addChatMessage(finalChatMessage)
                    .onSuccess { docId ->
                        Log.d("GroupChatViewModel", "Photo message successfully sent with ID: $docId")
                        // Update the optimistic message with the actual URL from Firestore
                        _chatMessages.update { currentList ->
                            currentList.map { if (it.id == newChatMessageId) finalChatMessage.copy(id = docId) else it }
                        }
                        _showToast.emit("Photo sent!")
                    }
                    .onFailure { e ->
                        Log.e("GroupChatViewModel", "Failed to save photo message to Firestore: ${e.message}", e)
                        _showToast.emit("Failed to send photo: ${e.message}")
                        // Remove optimistic update on failure
                        _chatMessages.update { currentList -> currentList.filter { it.id != newChatMessageId } }
                    }
            } catch (e: Exception) {
                Log.e("GroupChatViewModel", "Failed to upload photo: ${e.message}", e)
                _showToast.emit("Failed to upload photo: ${e.message}")
                // Remove optimistic update on failure
                _chatMessages.update { currentList -> currentList.filter { it.id != newChatMessageId } }
            }
        }
    }

    // --- Likes and Comments ---

    fun toggleLike(messageId: String, currentLikes: List<String>) {
        val currentUserId = auth.currentUser?.uid
        if (currentUserId == null) {
            viewModelScope.launch { _showToast.emit("You must be logged in to like messages.") }
            return
        }

        val newLikes = if (currentLikes.contains(currentUserId)) {
            currentLikes.filter { it != currentUserId }
        } else {
            currentLikes + currentUserId
        }

        // Optimistic update
        _chatMessages.update { messages ->
            messages.map {
                if (it.id == messageId) it.copy(likes = newLikes) else it
            }
        }

        viewModelScope.launch {
            firestoreService.updateChatMessageLikes(messageId, newLikes)
                .onSuccess {
                    Log.d("GroupChatViewModel", "Likes updated successfully for message $messageId. New count: ${newLikes.size}")
                }
                .onFailure { e ->
                    Log.e("GroupChatViewModel", "Failed to update likes: ${e.message}", e)
                    _showToast.emit("Failed to update like status: ${e.message}")
                    // Revert optimistic update on failure
                    _chatMessages.update { messages ->
                        messages.map {
                            if (it.id == messageId) it.copy(likes = currentLikes) else it
                        }
                    }
                }
        }
    }

    fun showComments(message: ChatMessage) {
        _selectedMessageForComments.value = message
        _showCommentDialog.value = true
        Log.d("GroupChatViewModel", "Showing comments for message: ${message.id}")
    }

    fun dismissComments() {
        _selectedMessageForComments.value = null
        _showCommentDialog.value = false
        Log.d("GroupChatViewModel", "Dismissed comment dialog.")
    }

    fun addComment(messageId: String, commentText: String) {
        val groupId = groupMonitorService.activeGroup.value?.groupID
        val senderId = auth.currentUser?.uid

        if (groupId == null || senderId == null) {
            viewModelScope.launch { _showToast.emit("Not logged in or in an active group.") }
            return
        }
        if (commentText.isBlank()) {
            viewModelScope.launch { _showToast.emit("Comment cannot be empty.") }
            return
        }

        viewModelScope.launch {
            val newComment = Comment(
                id = UUID.randomUUID().toString(),
                messageId = messageId,
                groupId = groupId,
                senderId = senderId,
                timestamp = System.currentTimeMillis(),
                text = commentText
            )

            // Optimistic update for comments list in dialog
            _comments.update { it + newComment }
            // Optimistic update for parent message's comment count
            _chatMessages.update { messages ->
                messages.map {
                    if (it.id == messageId) it.copy(commentCount = it.commentCount + 1) else it
                }
            }


            firestoreService.addComment(newComment)
                .onSuccess { docId ->
                    Log.d("GroupChatViewModel", "Comment added successfully with ID: $docId")
                    firestoreService.incrementChatMessageCommentCount(messageId) // Update count in parent message
                        .onSuccess { Log.d("GroupChatViewModel", "Comment count incremented for message $messageId.") }
                        .onFailure { e -> Log.e("GroupChatViewModel", "Failed to increment comment count: ${e.message}") }
                }
                .onFailure { e ->
                    Log.e("GroupChatViewModel", "Failed to add comment: ${e.message}", e)
                    _showToast.emit("Failed to add comment: ${e.message}")
                    // Revert optimistic updates on failure
                    _comments.update { currentList -> currentList.filter { it.id != newComment.id } }
                    _chatMessages.update { messages ->
                        messages.map {
                            if (it.id == messageId) it.copy(commentCount = it.commentCount - 1) else it
                        }
                    }
                }
        }
    }

    private fun loadCommentsForMessage(messageId: String) {
        viewModelScope.launch {
            // For now, load all comments, but for large chats, this would need pagination too
            val result = firestoreService.getCommentsForMessage(messageId, limit = 1000) // Large limit for now
            result.onSuccess {
                _comments.value = it
                Log.d("GroupChatViewModel", "Loaded ${it.size} comments for message $messageId.")
            }.onFailure { e ->
                Log.e("GroupChatViewModel", "Failed to load comments for message $messageId: ${e.message}")
                _showToast.emit("Failed to load comments: ${e.message}")
                _comments.value = emptyList()
            }
        }
    }

    // --- NEW: Message Deletion Logic ---
    fun deleteChatMessage(chatMessage: ChatMessage) {
        val currentUserId = auth.currentUser?.uid
        val activeGroupOwnerId = groupMonitorService.activeGroup.value?.groupOwnerId

        if (currentUserId == null) {
            viewModelScope.launch { _showToast.emit("You must be logged in to delete messages.") }
            return
        }

        // Check if current user is the sender OR the group owner
        val canDelete = chatMessage.senderId == currentUserId || activeGroupOwnerId == currentUserId

        if (!canDelete) {
            viewModelScope.launch { _showToast.emit("You do not have permission to delete this message.") }
            return
        }

        // Optimistically remove from UI
        _chatMessages.update { currentList ->
            currentList.filter { it.id != chatMessage.id }
        }
        viewModelScope.launch { _showToast.emit("Deleting message...") }


        viewModelScope.launch {
            try {
                // 1. If it's a photo message, delete the image from Firebase Storage
                if (chatMessage.chatMessageType == ChatMessageType.PHOTO && !chatMessage.imageUrl.isNullOrBlank()) {
                    try {
                        firebaseStorage.getReferenceFromUrl(chatMessage.imageUrl).delete().await()
                        Log.d("GroupChatViewModel", "Deleted photo from storage: ${chatMessage.imageUrl}")
                    } catch (e: Exception) {
                        Log.e("GroupChatViewModel", "Failed to delete photo from storage for message ${chatMessage.id}: ${e.message}", e)
                        // Don't re-throw, proceed to delete message even if photo fails
                    }
                }

                // 2. Delete all associated comments for this message
                firestoreService.deleteAllCommentsForMessage(chatMessage.id)
                    .onSuccess { Log.d("GroupChatViewModel", "Comments deleted for message ${chatMessage.id}.") }
                    .onFailure { e -> Log.e("GroupChatViewModel", "Failed to delete comments for message ${chatMessage.id}: ${e.message}", e) }

                // 3. Delete the chat message document itself
                firestoreService.deleteChatMessage(chatMessage.id)
                    .onSuccess {
                        viewModelScope.launch { _showToast.emit("Message deleted successfully.") }
                        Log.d("GroupChatViewModel", "Message ${chatMessage.id} deleted from Firestore.")
                    }
                    .onFailure { e ->
                        viewModelScope.launch { _showToast.emit("Failed to delete message: ${e.message}") }
                        Log.e("GroupChatViewModel", "Failed to delete message ${chatMessage.id}: ${e.message}", e)
                        // Revert optimistic update if Firestore deletion fails
                        _chatMessages.update { currentList -> listOf(chatMessage) + currentList } // Prepend it back (might need re-sorting)
                    }
            } catch (e: Exception) {
                viewModelScope.launch { _showToast.emit("An error occurred during deletion: ${e.message}") }
                Log.e("GroupChatViewModel", "Unexpected error during message deletion: ${e.message}", e)
                // Revert optimistic update on any unexpected error
                _chatMessages.update { currentList -> listOf(chatMessage) + currentList }
            }
        }
    }

    // --- New Message Count Logic ---

    // This function should be called when the chat screen becomes visible/resumed
    fun markChatAsSeen() {
        Log.d("GroupChatViewModel", "Marking chat as seen. Current new messages: ${_newMessagesCount.value}")
        _newMessagesCount.value = 0
        lastSeenTimestamp = System.currentTimeMillis()
    }

    // This function can be called periodically (e.g., from MainScreen) or when new messages arrive
    // to update the new message count for the FAB badge.
    fun checkForNewMessagesForBadge() {
        val groupId = groupMonitorService.activeGroup.value?.groupID
        if (groupId != null && !isRefreshing.value) { // Don't check if already refreshing
            viewModelScope.launch {
                val lastCheckedTimestamp = lastSeenTimestamp // Use the last seen timestamp
                Log.d("GroupChatViewModel", "Checking for new messages for badge since: $lastCheckedTimestamp")

                val result = firestoreService.getNewChatMessages(groupId, lastCheckedTimestamp)
                result.onSuccess { newMessages ->
                    val actualNewMessages = newMessages.filter { msg ->
                        // Only count messages that are genuinely new and not already in _chatMessages.value
                        // This might be redundant if the timestamp logic is solid, but good for edge cases.
                        _chatMessages.value.none { it.id == msg.id } && msg.timestamp > lastCheckedTimestamp
                    }
                    val currentCount = _newMessagesCount.value
                    val countToAdd = actualNewMessages.size

                    // Update the count only if there are genuinely new messages not yet displayed
                    if (countToAdd > 0) {
                        _newMessagesCount.update { it + countToAdd }
                        Log.d("GroupChatViewModel", "New messages detected for badge: $countToAdd. Total: ${_newMessagesCount.value}")
                    } else {
                        Log.d("GroupChatViewModel", "No new messages found for badge since last check.")
                    }
                }.onFailure { e ->
                    Log.e("GroupChatViewModel", "Failed to check for new messages for badge: ${e.message}")
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        Log.d("GroupChatViewModel", "MainViewModel onCleared.")
        // Any cleanup if necessary
    }
}
