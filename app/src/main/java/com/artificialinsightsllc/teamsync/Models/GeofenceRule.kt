// In file: app/src/main/java/com/artificialinsightsllc/teamsync/Models/GeofenceRule.kt
package com.artificialinsightsllc.teamsync.Models

/**
 * Represents a specific rule or trigger associated with a GeofenceZone.
 * When certain conditions related to the zone are met, this rule defines what action should be taken.
 *
 * @property id Unique identifier for this GeofenceRule document in Firestore.
 * @property geofenceZoneId The ID of the GeofenceZone this rule belongs to.
 * @property name A descriptive name for the rule (e.g., "Enter Zone Alert", "Speeding Check").
 * @property description An optional longer description of the rule's purpose.
 * @property triggerCondition What event causes this rule to activate (e.g., "ENTER_ZONE", "EXIT_ZONE", "SPEED_EXCEEDS", "STAY_INSIDE_TOO_LONG", "NO_DATA_IN_ZONE").
 * @property actionType What action to perform when the rule is triggered (e.g., "NOTIFY_DISPATCH", "NOTIFY_GROUP", "AUTO_CHECK_IN", "TRIGGER_ALERT", "UPDATE_INCIDENT_STATUS").
 * @property notificationMessageTemplate A template for the notification message, possibly including placeholders like "{member_name}", "{zone_name}".
 * @property targetAudience A list of recipient types for actions (e.g., "all_members", "assigned_members", "dispatchers", "specific_roles").
 * @property thresholdValue A numeric value used by certain triggers (e.g., speed limit for "SPEED_EXCEEDS", duration in milliseconds for "STAY_INSIDE_TOO_LONG"). Null if not applicable.
 * @property cooldownPeriodMillis Prevents spamming alerts; a duration in milliseconds before this rule can trigger again for the same entity/event. Null if no cooldown.
 * @property isEnabled A boolean indicating if this rule is currently active.
 * @property severity Optional: For alerts, indicates the urgency/severity (e.g., "LOW", "MEDIUM", "HIGH", "CRITICAL").
 * @property createdByUserId The ID of the user who created this rule.
 * @property creationTimestamp The Unix timestamp in milliseconds when the rule was created.
 */
data class GeofenceRule(
    val id: String = "",
    val geofenceZoneId: String = "",
    val name: String = "",
    val description: String? = null,
    val triggerCondition: String = "ENTER_ZONE", // e.g., "ENTER_ZONE", "EXIT_ZONE", "SPEED_EXCEEDS", "STAY_INSIDE_TOO_LONG", "NO_DATA_IN_ZONE"
    val actionType: String = "NOTIFY_DISPATCH", // e.g., "NOTIFY_DISPATCH", "NOTIFY_GROUP", "AUTO_CHECK_IN", "TRIGGER_ALERT", "UPDATE_INCIDENT_STATUS"
    val notificationMessageTemplate: String? = null,
    val targetAudience: List<String> = emptyList(), // e.g., "all_members", "assigned_members", "dispatchers", "specific_roles"
    val thresholdValue: Double? = null,
    val cooldownPeriodMillis: Long? = null,
    val isEnabled: Boolean = true,
    val severity: String? = null, // e.g., "LOW", "MEDIUM", "HIGH", "CRITICAL"
    val createdByUserId: String = "",
    val creationTimestamp: Long = System.currentTimeMillis()
)
