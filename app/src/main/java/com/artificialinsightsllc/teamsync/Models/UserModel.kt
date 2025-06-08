// In file: app/src/main/java/com/artificialinsightsllc/teamsync/Models/UserModel.kt
package com.artificialinsightsllc.teamsync.Models

data class UserModel(
    val authId: String = "",               // Firebase Auth UID
    val userId: String = "",               // Optional internal ID
    val firstName: String = "",
    val middleName: String = "",
    val lastName: String = "",
    val displayName: String = "",          // Public screen name
    val city: String = "",
    val state: String = "",
    val dateOfBirth: String = "",          // Format: MMDDYYYY or ISO-8601
    val profilePhotoUrl: String = "",
    val email: String = "",
    val fcmToken: String = "",             // Firebase Cloud Messaging token
    val createdAt: Long = System.currentTimeMillis(),
    val verified: Boolean = false,
    val profileComplete: Boolean = false,
    val selectedActiveGroupId: String? = null,
    val mainInstructionsSeen: Boolean = false, // Added for instruction overlay control

    // NEW: User Settings Fields
    val shareLiveLocation: Boolean = true, // User preference to share location
    val personalLocationUpdateIntervalMillis: Long? = null, // User's personal override for interval
    val shareBatteryLevel: Boolean = true, // User preference to share battery
    val shareAppStatus: Boolean = true, // User preference to share online/offline status

    val defaultMapType: String = "STANDARD", // STANDARD, SATELLITE, HYBRID, TERRAIN
    val unitsOfMeasurement: String = "IMPERIAL", // IMPERIAL (miles/mph), METRIC (km/kph), NAUTICAL (nm/knots)
    val showMyOwnMarker: Boolean = true, // Show/hide user's own marker on map
    val showMemberNamesOnMap: Boolean = true, // Show/hide display names next to markers

    val receiveGroupChatNotifications: Boolean = true, // Notifications for group chat
    val receivePrivateChatMessages: Boolean = true, // Notifications for private chat
    val valReceiveCriticalLocationAlerts: Boolean = true, // Notifications for critical alerts
    val muteNotificationsWhenAppOpen: Boolean = false // Mute notifications when app is in foreground
)
