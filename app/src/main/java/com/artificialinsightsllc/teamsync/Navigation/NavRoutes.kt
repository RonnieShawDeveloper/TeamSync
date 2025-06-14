// In file: app/src/main/java/com/artificialinsightsllc/teamsync/Navigation/NavRoutes.kt
package com.artificialinsightsllc.teamsync.Navigation

object NavRoutes {
    const val SPLASH = "splash"
    const val LOGIN = "login"
    const val SIGNUP = "signup"
    const val PRE_CHECK = "pre_check"
    const val MAIN = "main"
    const val CREATE_GROUP = "create_group"
    const val GROUPS_LIST = "groups_list"
    const val TEAM_LIST = "team_list"
    const val ADD_MAP_MARKER = "add_map_marker"
    const val TRAVEL_REPORT = "travel_report/{userId}/{timeRangeMillis}"
    const val USER_SETTINGS = "user_settings"
    const val SHUTDOWN = "shutdown"
    // REMOVED: const val GEOFENCE = "geofence" // This was the old placeholder GeofenceScreen route
    // REMOVED: const val CREATE_GEOFENCE = "create_geofence" // This was the old placeholder CreateGeofenceScreen route
    const val NOTIFICATIONS = "notifications"
    const val GROUP_CHAT = "group_chat"
    const val GROUP_STATUS = "group_status"

    // NEW: Geofence Management Routes
    const val GEOFENCE_MANAGEMENT = "geofence_management" // New route for GeofenceManagementScreen
    const val CREATE_GEOFENCE_DRAW = "create_geofence_draw" // Route for the future geofence drawing screen
    // TODO: Add routes for Geofence/Rule/Assignment details/edit screens
    // TODO: Add routes for Incident details/creation/management screens
    // TODO: Add routes for Geofence Packages screen
    // TODO: Add routes for Group Settings screen (if separate from UserSettings)
}
