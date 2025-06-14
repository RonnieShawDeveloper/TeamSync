// In file: app/src/main/java/com/artificialinsightsllc/teamsync/Models/GeofenceAssignment.kt
package com.artificialinsightsllc.teamsync.Models

/**
 * Represents the assignment of a group member or an asset to a specific GeofenceZone.
 * This indicates that the assigned entity is relevant to or monitored within this zone.
 *
 * @property id Unique identifier for this GeofenceAssignment document in Firestore.
 * @property geofenceZoneId The ID of the GeofenceZone to which this entity is assigned.
 * @property assignedEntityId The ID of the entity being assigned. This could be a userId or an assetId.
 * @property assignedEntityType The type of entity being assigned (e.g., "USER", "ASSET").
 * @property assignmentRole Optional: A role within the context of this assignment (e.g., "RESPONDER", "MONITOR", "PATROL").
 * @property isActive A boolean indicating if this assignment is currently active.
 * @property assignmentTimestamp The Unix timestamp in milliseconds when the assignment was made.
 * @property unassignmentTimestamp The Unix timestamp when the assignment ended (e.g., manual unassign, incident resolved). Null if still active.
 * @property createdByUserId The ID of the user who made this assignment.
 */
data class GeofenceAssignment(
    val id: String = "",
    val geofenceZoneId: String = "",
    val assignedEntityId: String = "",
    val assignedEntityType: String = "USER", // e.g., "USER", "ASSET"
    val assignmentRole: String? = null, // e.g., "RESPONDER", "MONITOR", "PATROL"
    val isActive: Boolean = true,
    val assignmentTimestamp: Long = System.currentTimeMillis(),
    val unassignmentTimestamp: Long? = null,
    val createdByUserId: String = ""
)
