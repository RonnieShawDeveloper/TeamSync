// In file: app/src/main/java/com/artificialinsightsllc/teamsync/Helpers/MarkerPin.kt
package com.artificialinsightsllc.teamsync.Helpers

data class MarkerPin(
    val userId: String, // Unique ID of the user
    val profileImageUrl: String, // URL of the user's profile image
    val latitude: Double, // Latitude of the marker's location
    val longitude: Double, // Longitude of the marker's location
    val isOnline: Boolean = true, // Optional: Is the user currently online?
    val bearing: Float? = null, // Optional: Bearing/direction the user is facing
    // Add other fields as needed (e.g., battery level, custom icon URL for premium)
)