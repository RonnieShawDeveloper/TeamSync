// In file: app/src/main/java/com/artificialinsightsllc/teamsync/Models/Locations.kt
package com.artificialinsightsllc.teamsync.Models

data class Locations(
    val userId: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val timestamp: Long = System.currentTimeMillis(),
    val speed: Float? = null,
    val bearing: Float? = null,
    val batteryLevel: Int? = null,
    val batteryChargingStatus: String? = null, // "CHARGING", "DISCHARGING", "FULL", "UNKNOWN"
    val appStatus: String? = null, // "FOREGROUND", "BACKGROUND"
    val activeTrackingGroupId: String? = null // NEW: The ID of the group this user is actively tracking for
)

