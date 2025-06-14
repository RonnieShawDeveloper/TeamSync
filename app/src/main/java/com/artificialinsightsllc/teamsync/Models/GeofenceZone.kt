// In file: app/src/main/java/com/artificialinsightsllc/teamsync/Models/GeofenceZone.kt
package com.artificialinsightsllc.teamsync.Models

import com.google.android.gms.maps.model.LatLng // Import LatLng for coordinates

/**
 * Represents a defined geographical zone (geofence) within a group.
 * This can be a static pre-planned zone or a dynamic incident-specific zone.
 *
 * @property id Unique identifier for this GeofenceZone document in Firestore.
 * @property groupId The ID of the group this geofence belongs to.
 * @property name A user-friendly name for the geofence (e.g., "Main Stage Perimeter", "Entry Point A").
 * @property description An optional longer description of the geofence's purpose.
 * @property type The category of this geofence (e.g., "PRESET", "INCIDENT_AD_HOC", "JOB_SITE").
 * @property shapeType The geometric shape of the geofence (e.g., "CIRCLE", "POLYGON", "RECTANGLE").
 * @property coordinates A list of LatLng points defining the shape.
 * For a CIRCLE, this list contains only one element: its center.
 * For a RECTANGLE, this list contains 4 elements representing its corners.
 * For a POLYGON, this list contains all vertices.
 * @property radiusMeters The radius in meters, applicable only if `shapeType` is "CIRCLE". Null otherwise.
 * @property parentZoneId Optional ID of a larger, encompassing geofence, creating a hierarchy. Null if top-level.
 * @property isActive A boolean indicating if this geofence is currently active for monitoring.
 * @property creationTimestamp The Unix timestamp in milliseconds when the geofence was created.
 * @property createdByUserId The ID of the user who created this geofence.
 * @property incidentId Optional: If this geofence is created for a specific incident, this links to the Incident.kt ID.
 * @property expirationTimestamp Optional: For dynamic/incident-based geofences, a timestamp when it should expire/be removed.
 */
data class GeofenceZone(
    val id: String = "",
    val groupId: String = "",
    val name: String = "",
    val description: String? = null,
    val type: String = "PRESET", // e.g., "PRESET", "INCIDENT_AD_HOC", "JOB_SITE"
    val shapeType: String = "CIRCLE", // e.g., "CIRCLE", "POLYGON", "RECTANGLE"
    val coordinates: List<LatLng> = emptyList(),
    val radiusMeters: Double? = null, // Only for CIRCLE type
    val parentZoneId: String? = null, // For nested zones
    val isActive: Boolean = true,
    val creationTimestamp: Long = System.currentTimeMillis(),
    val createdByUserId: String = "",
    val incidentId: String? = null, // Links to Incident.kt if this is an incident-specific geofence
    val expirationTimestamp: Long? = null // For time-limited zones
)
