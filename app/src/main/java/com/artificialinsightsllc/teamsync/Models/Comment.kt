// In file: app/src/main/java/com/artificialinsightsllc/teamsync/Models/Comment.kt
package com.artificialinsightsllc.teamsync.Models

/**
 * Represents a single comment on a chat message (post).
 *
 * @property id Unique identifier for this comment document in Firestore.
 * @property messageId The ID of the chat message this comment belongs to.
 * @property groupId The ID of the group where the chat message and comment reside.
 * @property senderId The ID of the user who posted this comment.
 * @property timestamp The Unix timestamp in milliseconds when the comment was created.
 * @property text The text content of the comment.
 * @property editedTimestamp The Unix timestamp if the comment was edited, null otherwise.
 */
data class Comment(
    val id: String = "",
    val messageId: String = "",
    val groupId: String = "",
    val senderId: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val text: String = "",
    val editedTimestamp: Long? = null
)
