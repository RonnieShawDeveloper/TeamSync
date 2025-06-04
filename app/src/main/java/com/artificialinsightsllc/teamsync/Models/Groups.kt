// In file: app/src/main/java/com/artificialinsightsllc/teamsync/Models/Groups.kt
package com.artificialinsightsllc.teamsync.Models

// Define the GroupType enum (unchanged)
enum class GroupType {
    FREEMIUM,
    PAID_BASIC
}

data class Groups(
    // --- Core Group Identification & Metadata ---
    val groupID: String = "", // Provide default empty string
    val groupName: String = "", // Provide default empty string
    val groupDescription: String = "", // Provide default empty string
    val fcmName: String = "", // Provide default empty string
    val groupOwnerId: String = "", // Provide default empty string
    val groupCreateTimestamp: Long = 0L, // Provide default 0L
    val groupType: GroupType = GroupType.FREEMIUM, // Provide a default GroupType

    // --- Capacity & Duration Control ---
    val groupEndTimestamp: Long? = null, // Already nullable, default is fine
    val maxMembers: Int = 0, // Provide default 0

    // --- Location Tracking Features ---
    val locationUpdateIntervalMillis: Long = 0L, // Provide default 0L
    val allowPollingLocation: Boolean = false, // Provide default false
    val allowCheckInRequests: Boolean = false, // Provide default false
    val enableLocationHistory: Boolean = false, // Provide default false
    val locationHistoryRetentionDays: Int? = null, // Already nullable, default is fine

    // --- Chat & Communication Features ---
    val enableGroupChat: Boolean = false, // Provide default false (though you set true in creation)
    val enablePrivateChats: Boolean = false, // Provide default false
    val enablePhotoSharing: Boolean = false, // Provide default false
    val enableAudioMessages: Boolean = false, // Provide default false
    val enableFileSharing: Boolean = false, // Provide default false

    // --- Group Access & Security ---
    val groupAccessCode: String = "", // Provide default empty string
    val groupAccessPassword: String? = null, // Already nullable, default is fine

    // --- Dispatch Mode & Advanced Features ---
    val dispatchModeEnabled: Boolean = false, // Provide default false
    val maxGeofences: Int = 0 // Provide default 0
)