// In file: app/src/main/java/com/artificialinsightsllc/teamsync/Models/ChatMessage.kt
package com.artificialinsightsllc.teamsync.Models

/**
 * Defines the type of content a chat message contains.
 * - TEXT: A message composed primarily of text.
 * - PHOTO: A message that includes an image, possibly with a text caption.
 */
enum class ChatMessageType {
    TEXT,
    PHOTO
}

/**
 * Represents a single chat message (post) within a group chat.
 *
 * @property id Unique identifier for this chat message document in Firestore.
 * @property groupId The ID of the group this message belongs to.
 * @property senderId The ID of the user who sent this message.
 * @property timestamp The Unix timestamp in milliseconds when the message was created.
 * @property message The text content of the message (nullable if only an image).
 * @property imageUrl URL to the image if the message is of type PHOTO (nullable for TEXT messages).
 * @property chatMessageType The type of content this message holds (TEXT or PHOTO).
 * @property likes A list of user IDs who have liked this message.
 * @property commentCount The total number of comments on this message (can be managed client-side or server-side).
 * @property lastCommentTimestamp The Unix timestamp of the most recent comment, useful for sorting or displaying.
 * @property editedTimestamp The Unix timestamp if the message was edited, null otherwise.
 */
data class ChatMessage(
    val id: String = "",
    val groupId: String = "",
    val senderId: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val message: String? = null,
    val imageUrl: String? = null,
    val chatMessageType: ChatMessageType = ChatMessageType.TEXT,
    val likes: List<String> = emptyList(), // Store user IDs of those who liked
    val commentCount: Int = 0,
    val lastCommentTimestamp: Long? = null,
    val editedTimestamp: Long? = null
)