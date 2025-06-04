// In file: app/src/main/java/com/artificialinsightsllc/teamsync/Models/MapMarker.kt
package com.artificialinsightsllc.teamsync.Models

// Enum to differentiate between marker types
enum class MapMarkerType {
    CHAT,
    PHOTO
}

data class MapMarker(
    val id: String = "", // Unique ID for the marker document (Firestore document ID)
    val groupId: String = "", // ID of the group this marker belongs to
    val userId: String = "", // ID of the user who posted the marker
    val markerType: MapMarkerType = MapMarkerType.CHAT, // Type of marker: CHAT or PHOTO
    val message: String = "", // The text message associated with the marker
    val photoUrl: String? = null, // URL to the photo if markerType is PHOTO, nullable for CHAT
    val latitude: Double = 0.0, // Latitude of the marker's location
    val longitude: Double = 0.0, // Longitude of the marker's location
    val timestamp: Long = System.currentTimeMillis(), // When the marker was posted
    val cameraBearing: Float? = null // Direction camera was facing for PHOTO markers (0-360 degrees), nullable for CHAT
)
