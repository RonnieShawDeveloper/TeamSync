package com.artificialinsightsllc.teamsync.Models

import androidx.room.Entity
import androidx.room.PrimaryKey

// Define a Room Entity for storing notifications locally
@Entity(tableName = "notifications")
data class NotificationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,            // Auto-generated primary key for Room
    val messageId: String?,     // FCM message ID (can be null for system/local notifications)
    val title: String?,         // Notification title
    val body: String?,          // Notification body/content
    val timestamp: Long,        // When the notification was received/created (System.currentTimeMillis())
    val type: String?,          // "GROUP_CHAT", "DIRECT_MESSAGE", "ALERT", "SYSTEM"
    val senderId: String?,      // User ID of the sender (if applicable, e.g., for direct messages)
    val groupId: String?,       // Group ID (if applicable, e.g., for group chat/alerts)
    val isRead: Boolean = false, // Status: read/unread
    val dataPayload: String?    // Full data payload as a JSON string (for advanced handling)
)
