// In file: app/src/main/java/com/artificialinsightsllc/teamsync/Models/GroupSettings.kt
package com.artificialinsightsllc.teamsync.Models

/**
 * Represents customizable settings for a specific group, configurable by the group owner/admin.
 * This includes definitions for custom incident types and the various status messages
 * associated with them, as well as pre-defined roles and their permissions.
 *
 * @property id Unique identifier for this GroupSettings document in Firestore.
 * This should typically be the same as the corresponding Group.groupID.
 * @property groupId The ID of the group these settings apply to.
 * @property customIncidentTypes A map defining incident types and their allowed statuses.
 * The key is the incident type name (e.g., "TowJob", "SecurityEvent").
 * The value is a list of strings, where each string is an allowed status for that incident type
 * (e.g., ["EN_ROUTE", "ON_SCENE", "HOOKED", "DROPPED"], ["RESPONDING", "STABILIZED", "CLEARED"]).
 * @property presetGeofenceRules A map of pre-defined GeofenceRule templates that can be quickly applied.
 * The key is a unique name for the rule template (e.g., "Standard Entry Alert").
 * The value is a GeofenceRule object, defining the default properties for that rule.
 * Note: When applied, a new GeofenceRule document will be created, copying these properties.
 * @property predefinedRoles A map defining custom roles and their default permissions/capabilities within this group.
 * The key is the role name (e.g., "Dispatcher", "Officer", "FieldAgent").
 * The value is a list of strings representing capabilities/permissions (e.g., ["CREATE_INCIDENT", "VIEW_ALL_LOCATIONS", "MANAGE_ASSIGNMENTS"]).
 * This is distinct from MemberRole (OWNER, ADMIN, MEMBER).
 * @property lastModifiedTimestamp The Unix timestamp of the last time these settings were modified.
 * @property lastModifiedByUserId The ID of the user who last modified these settings.
 */
data class GroupSettings(
    val id: String = "", // Should match Group.groupID
    val groupId: String = "",
    val customIncidentTypes: Map<String, List<String>> = emptyMap(),
    val presetGeofenceRules: Map<String, GeofenceRule> = emptyMap(), // Stores rule templates
    val predefinedRoles: Map<String, List<String>> = emptyMap(), // Custom roles and their capabilities
    val lastModifiedTimestamp: Long = System.currentTimeMillis(),
    val lastModifiedByUserId: String = ""
)
