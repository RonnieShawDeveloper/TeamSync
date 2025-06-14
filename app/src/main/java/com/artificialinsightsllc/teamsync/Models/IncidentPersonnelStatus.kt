// In file: app/src/main/java/com/artificialinsightsllc/teamsync/Models/IncidentPersonnelStatus.kt
package com.artificialinsightsllc.teamsync.Models

/**
 * Represents the specific status and details of a single person assigned to an active incident.
 * This object will be nested within the 'assignedPersonnel' map of an Incident document.
 *
 * @property currentStatus The current status of the personnel in relation to the incident (e.g., "EN_ROUTE", "ON_SCENE", "WITH_CUSTOMER", "HOOKED", "CLEARED_SCENE", "LEFT_SCENE").
 * This is a customizable string defined by the group owner/admin for different incident types.
 * @property enteredZoneTime The Unix timestamp in milliseconds when the personnel first entered the incident's geofence zone. Null if not yet entered.
 * @property exitedZoneTime The Unix timestamp in milliseconds when the personnel last exited the incident's geofence zone. Null if currently inside or never entered.
 * @property timeInZoneMillis The accumulated time in milliseconds this personnel has spent inside the incident's geofence zone. Updated upon exiting or incident resolution.
 * @property lastStatusUpdateTime The Unix timestamp of the last time this personnel's status was updated.
 * @property lastStatusUpdateMessage An optional message accompanying the last status update.
 */
data class IncidentPersonnelStatus(
    val currentStatus: String = "ASSIGNED", // Default status for newly assigned personnel
    val enteredZoneTime: Long? = null,
    val exitedZoneTime: Long? = null,
    val timeInZoneMillis: Long? = null, // Can be calculated dynamically or updated on exit
    val lastStatusUpdateTime: Long = System.currentTimeMillis(),
    val lastStatusUpdateMessage: String? = null
)
