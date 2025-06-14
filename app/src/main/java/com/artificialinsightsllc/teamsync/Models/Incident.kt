// In file: app/src/main/java/com/artificialinsightsllc/teamsync/Models/Incident.kt
package com.artificialinsightsllc.teamsync.Models

/**
 * Represents a dynamic, live incident within a group, such as a "Fight" or "Tow Job".
 * This model tracks the overall status, assigned personnel, and a chronological log of events.
 *
 * @property id Unique identifier for this Incident document in Firestore.
 * @property groupId The ID of the group where this incident occurred.
 * @property geofenceZoneId The ID of the specific GeofenceZone created for this incident (dynamic geofence).
 * @property incidentType The category of the incident (e.g., "Fight", "Disorderly Person", "Broken Down Vehicle", "Medical Emergency").
 * This type can be customized by the group owner/admin.
 * @property title A concise title for the incident (e.g., "Fight - West Field", "Tow Job - Main Office").
 * @property description An optional longer description of the incident.
 * @property status The current status of the incident (e.g., "OPEN", "DISPATCHED", "RESPONDING", "ON_SCENE", "RESOLVED", "COMPLETED", "CANCELED").
 * @property creationTimestamp The Unix timestamp in milliseconds when the incident was created.
 * @property lastUpdateTimestamp The Unix timestamp of the most recent update to the incident.
 * @property createdByUserId The ID of the user who initiated/created this incident.
 * @property assignedPersonnel A map where the key is the userId and the value is an IncidentPersonnelStatus object,
 * detailing that person's specific status within this incident.
 * @property resolutionDetails Optional: A summary of how the incident was resolved.
 * @property resolvedByUserId Optional: The ID of the user who marked the incident as resolved/completed.
 * @property resolvedTimestamp Optional: The Unix timestamp when the incident was resolved/completed.
 */
data class Incident(
    val id: String = "",
    val groupId: String = "",
    val geofenceZoneId: String = "",
    val incidentType: String = "", // e.g., "Fight", "Tow Job"
    val title: String = "",
    val description: String? = null,
    val status: String = "OPEN", // e.g., "OPEN", "DISPATCHED", "RESPONDING", "ON_SCENE", "RESOLVED", "COMPLETED", "CANCELED"
    val creationTimestamp: Long = System.currentTimeMillis(),
    val lastUpdateTimestamp: Long = System.currentTimeMillis(),
    val createdByUserId: String = "",
    val assignedPersonnel: Map<String, IncidentPersonnelStatus> = emptyMap(), // Map of userId to IncidentPersonnelStatus
    val resolutionDetails: String? = null,
    val resolvedByUserId: String? = null,
    val resolvedTimestamp: Long? = null
)
