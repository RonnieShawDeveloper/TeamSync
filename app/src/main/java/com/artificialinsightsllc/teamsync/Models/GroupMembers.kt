// In file: app/src/main/java/com/artificialinsightsllc/teamsync/Models/GroupMembers.kt
package com.artificialinsightsllc.teamsync.Models

// REMOVED: import com.google.firebase.firestore.PropertyName // No longer needed as field names will match directly

enum class MemberRole {
    OWNER,
    ADMIN,
    MEMBER,
    DISPATCHER,
    VIEWER
}

data class GroupMembers(
    val id: String = "", // Unique ID for this specific GroupMember record (e.g., a Firestore document ID)
    val groupId: String = "", // ID of the group this member belongs to (links to Groups.groupID)
    val userId: String = "", // ID of the user (links to your User Profile/Auth ID)

    // --- Membership Status & Timestamps ---
    val joinedTimestamp: Long = 0L, // Unix timestamp when the user joined the group
    val unjoinedTimestamp: Long? = null, // Unix timestamp when the user left the group, nullable if still a member

    // --- Role & Permissions ---
    val memberRole: MemberRole = MemberRole.MEMBER, // The role of this member within the group

    // --- Location & Status ---
    // FIXED: Renamed to match Firestore's expected field names (without 'is' prefix)
    val sharingLocation: Boolean = true, // Renamed from isSharingLocation
    val lastKnownLocationLat: Double? = null, // Latitude of the member's last reported location
    val lastKnownLocationLon: Double? = null, // Longitude of the member's last reported location
    val lastLocationUpdateTime: Long? = null, // Unix timestamp of the last location update
    val batteryLevel: Int? = null, // Last reported battery percentage (0-100), nullable if not available/enabled
    // FIXED: Renamed to match Firestore's expected field names (without 'is' prefix)
    val online: Boolean = true, // Renamed from isOnline

    // --- Personal Overrides for Group Settings ---
    val personalLocationUpdateIntervalMillis: Long? = null, // User's preferred location update interval
    val personalIsSharingLocationOverride: Boolean? = null, // User's personal override for location sharing

    // --- Customization & Preferences ---
    val customMarkerIconUrl: String? = null, // URL to a custom map marker icon for this member, nullable if none
    val notificationPreferences: Map<String, Boolean>? = null // Map of notification settings (e.g., "muteChat": true)
)