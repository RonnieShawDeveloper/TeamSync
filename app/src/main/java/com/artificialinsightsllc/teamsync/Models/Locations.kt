// In file: app/src/main/java/com/artificialinsightsllc/teamsync/Models/Locations.kt
package com.artificialinsightsllc.teamsync.Models

data class Locations(
    val userId: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val timestamp: Long = System.currentTimeMillis(),
    val speed: Float? = null, // New: Speed in meters/second
    val bearing: Float? = null // New: Bearing in degrees (0-360)
)