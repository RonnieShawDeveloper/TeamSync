package com.artificialinsightsllc.teamsync.Models

// Defines constants for various notification types to be used in FCM data payloads
// and for filtering in the UI.
object NotificationType {
    // Messages intended for a specific group (sent via group topic)
    const val GROUP_MESSAGE = "GROUP_MESSAGE"
    // Messages intended for a single user (sent via individual token)
    const val DIRECT_MESSAGE = "DIRECT_MESSAGE"
    // Alerts (e.g., geofence breaches, low battery) - can be group or direct, depending on context
    const val ALERT = "ALERT"
    // System messages (e.g., app updates, announcements from developers)
    const val SYSTEM_MESSAGE = "SYSTEM_MESSAGE"
    // Internal type for messages simulated as "sent" by the current user from the app
    const val SENT_MESSAGE_SIMULATED = "SENT_MESSAGE_SIMULATED"
}
