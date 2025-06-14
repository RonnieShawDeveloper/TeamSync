// In file: app/src/main/java/com/artificialinsightsllc/teamsync/Models/GeofencePackage.kt
package com.artificialinsightsllc.teamsync.Models

/**
 * Represents a saved package or template of GeofenceZones and their associated GeofenceRules.
 * This allows users to pre-design complex geofence setups for different venues or scenarios
 * and then load them quickly into an active group.
 *
 * @property id Unique identifier for this GeofencePackage document in Firestore.
 * @property name A user-friendly name for the package (e.g., "Stadium Event Layout", "Campus Security Patrols").
 * @property description An optional longer description of the package's purpose.
 * @property createdByUserId The ID of the user who created this package.
 * @property creationTimestamp The Unix timestamp in milliseconds when the package was created.
 * @property lastModifiedTimestamp The Unix timestamp of the last modification to this package.
 * @property zoneDataJson A JSON string representation of all GeofenceZone and GeofenceRule objects
 * that belong to this package. This allows for self-contained storage
 * and easy transfer/loading. When loaded, this JSON will be parsed to
 * create new GeofenceZone and GeofenceRule documents in the target group.
 * It should include all necessary properties to recreate the zones and rules,
 * including any relationships (e.g., parentZoneId).
 */
data class GeofencePackage(
    val id: String = "",
    val name: String = "",
    val description: String? = null,
    val createdByUserId: String = "",
    val creationTimestamp: Long = System.currentTimeMillis(),
    val lastModifiedTimestamp: Long = System.currentTimeMillis(),
    val zoneDataJson: String = "" // This will store the serialized zones and rules
)
