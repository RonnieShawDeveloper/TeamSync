// In file: app/src/main/java/com/artificialinsightsllc/teamsync/Models/UserModel.kt
package com.artificialinsightsllc.teamsync.Models

// REMOVED: import com.google.firebase.firestore.PropertyName // No longer needed as field names will match directly

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
    // FIXED: Renamed to match Firestore's expected field name (without 'is' prefix)
    val verified: Boolean = false, // Renamed from isVerified
    val profileComplete: Boolean = false,
    val selectedActiveGroupId: String? = null
)