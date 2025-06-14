// In file: app/src/main/java/com/artificialinsightsllc/teamsync/Models/IncidentLogEntry.kt
package com.artificialinsightsllc.teamsync.Models

/**
 * Represents a single chronological entry or event in the log of an Incident.
 * This can be an automated geofence event, a manual status update, or a message from personnel.
 * These entries will form the detailed history of an incident.
 *
 * @property id Unique identifier for this IncidentLogEntry document in Firestore.
 * @property incidentId The ID of the Incident this log entry belongs to.
 * @property timestamp The Unix timestamp in milliseconds when this log entry was created.
 * @property userId The ID of the user associated with this log entry (e.g., who entered a zone, who sent a message). Null if system-generated.
 * @property type The type of event recorded (e.g., "ZONE_ENTER", "ZONE_EXIT", "STATUS_UPDATE", "MESSAGE", "INCIDENT_CLOSED", "ASSIGNED_PERSONNEL").
 * @property details A descriptive string explaining the event (e.g., "John Doe entered Main Stage Zone", "Status changed to ON_SCENE", "Driver updated: With Customer").
 * @property additionalData Optional: A map for any additional context-specific data (e.g., {"previousStatus": "DISPATCHED", "newStatus": "ON_SCENE"}).
 */
data class IncidentLogEntry(
    val id: String = "",
    val incidentId: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val userId: String? = null,
    val type: String = "MESSAGE", // e.g., "ZONE_ENTER", "ZONE_EXIT", "STATUS_UPDATE", "MESSAGE", "INCIDENT_CLOSED"
    val details: String = "",
    val additionalData: Map<String, String>? = null // Flexible for storing extra details
)
