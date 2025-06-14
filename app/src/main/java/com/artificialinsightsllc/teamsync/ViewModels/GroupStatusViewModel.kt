// In file: app/src/main/java/com/artificialinsightsllc/teamsync/ViewModels/GroupStatusViewModel.kt
package com.artificialinsightsllc.teamsync.ViewModels

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.artificialinsightsllc.teamsync.Models.NotificationEntity
import com.artificialinsightsllc.teamsync.Models.NotificationType
import com.artificialinsightsllc.teamsync.Models.GroupMembers
import com.artificialinsightsllc.teamsync.Models.UserModel
import com.artificialinsightsllc.teamsync.Services.FirestoreService
import com.artificialinsightsllc.teamsync.Services.GroupMonitorService
import com.artificialinsightsllc.teamsync.TeamSyncApplication
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.UUID
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted

class GroupStatusViewModel(application: Application) : AndroidViewModel(application) {

    // Dependencies
    private val firestoreService: FirestoreService = FirestoreService()
    internal val auth: FirebaseAuth = FirebaseAuth.getInstance()
    internal val groupMonitorService: GroupMonitorService = (application as TeamSyncApplication).groupMonitorService

    // Dummy Data for Group Health & Activity Summary (will be replaced later)
    private val _onlineMemberCount = MutableStateFlow(12)
    val onlineMemberCount: StateFlow<Int> = _onlineMemberCount.asStateFlow()

    private val _totalMemberCount = MutableStateFlow(15)
    val totalMemberCount: StateFlow<Int> = _totalMemberCount.asStateFlow()

    private val _overallActivityFeed = MutableStateFlow(
        listOf(
            "John Doe entered Geofence 'Main Gate' - 5 mins ago",
            "Alice's battery is low (15%) - 10 mins ago",
            "New photo posted by Bob in chat - 15 mins ago",
            "Group transitioned to 'Dispatch Mode' - 30 mins ago",
            "Emergency SOS triggered by Jane Smith - 1 hour ago"
        )
    )
    val overallActivityFeed: StateFlow<List<String>> = _overallActivityFeed.asStateFlow()

    private val _avgLocationUpdateFrequency = MutableStateFlow("Group updates every 10 seconds (Simulated)")
    val avgLocationUpdateFrequency: StateFlow<String> = _avgLocationUpdateFrequency.asStateFlow()

    // Dummy Data for Geofence & Zone Monitoring Overview
    data class GeofenceStatus(val name: String, val status: String, val membersInside: Int, val lastEvent: String?)
    private val _activeGeofences = MutableStateFlow(
        listOf(
            GeofenceStatus("Main Gate", "Active", 3, "Entered 10 mins ago"),
            GeofenceStatus("Command Post", "Active", 2, "Exited 30 mins ago"),
            GeofenceStatus("East Perimeter", "Active", 0, "No recent events")
        )
    )
    val activeGeofences: StateFlow<List<GeofenceStatus>> = _activeGeofences.asStateFlow()

    private val _geofenceEventCountLastHour = MutableStateFlow(5)
    val geofenceEventCountLastHour: StateFlow<Int> = _geofenceEventCountLastHour.asStateFlow()

    data class ZoneMemberCount(val zoneName: String, val memberCount: Int)
    private val _membersByZone = MutableStateFlow(
        listOf(
            ZoneMemberCount("Main Gate", 3),
            ZoneMemberCount("Command Post", 2),
            ZoneMemberCount("Transit", 7),
            ZoneMemberCount("Off Duty", 3)
        )
    )
    val membersByZone: StateFlow<List<ZoneMemberCount>> = _membersByZone.asStateFlow()

    // Dummy Data for Communication Channel Health
    private val _unreadGroupChatMessages = MutableStateFlow(7)
    val unreadGroupChatMessages: StateFlow<Int> = _unreadGroupChatMessages.asStateFlow()

    private val _pendingBroadcasts = MutableStateFlow(0)
    val pendingBroadcasts: StateFlow<Int> = _pendingBroadcasts.asStateFlow()

    private val _communicationReliability = MutableStateFlow("Excellent")
    val communicationReliability: StateFlow<String> = _communicationReliability.asStateFlow()

    // Dummy Data for Critical Alerts & Safety Summary
    private val _activeAlertsCount = MutableStateFlow(1) // e.g., one SOS active
    val activeAlertsCount: StateFlow<Int> = _activeAlertsCount.asStateFlow()

    data class LowBatteryMember(val name: String, val level: Int)
    private val _lowBatteryMembers = MutableStateFlow(
        listOf(
            LowBatteryMember("Alice", 15),
            LowBatteryMember("Charlie", 10)
        )
    )
    val lowBatteryMembers: StateFlow<List<LowBatteryMember>> = _lowBatteryMembers.asStateFlow()

    data class OfflineMember(val name: String, val lastSeen: String)
    private val _offlineMembers = MutableStateFlow(
        listOf(
            OfflineMember("David", "2 hours ago"),
            OfflineMember("Eve", "5 hours ago")
        )
    )
    val offlineMembers: StateFlow<List<OfflineMember>> = _offlineMembers.asStateFlow()

    // Dummy Data for Operational Mode & Resources
    private val _currentGroupPlan = MutableStateFlow("Basic Paid Plan")
    val currentGroupPlan: StateFlow<String> = _currentGroupPlan.asStateFlow()

    private val _dispatchModeActive = MutableStateFlow(true)
    val dispatchModeActive: StateFlow<Boolean> = _dispatchModeActive.asStateFlow()

    data class ResourceStatus(val name: String, val status: String)
    private val _resourceUtilization = MutableStateFlow(
        listOf(
            ResourceStatus("Vehicle Alpha", "In Use"),
            ResourceStatus("Drone Unit", "Available"),
            ResourceStatus("Med Kit #1", "Checked Out")
        )
    )
    val resourceUtilization: StateFlow<List<ResourceStatus>> = _resourceUtilization.asStateFlow()


    // NEW: Data class for combined member status display
    data class MemberStatusDisplayData(
        val userId: String,
        val displayName: String,
        val profilePhotoUrl: String?,
        val batteryLevel: Int?, // Nullable if not available
        val batteryChargingStatus: String?, // Nullable if not available ("CHARGING", "DISCHARGING", "FULL", "NOT_CHARGING", "UNKNOWN")
        val appStatus: String?, // Nullable if not available ("FOREGROUND", "BACKGROUND")
        val isOnline: Boolean, // Derived from GroupMembers.online (based on appStatus for display)
        val lastLocationUpdateTime: Long? // NEW: Added lastLocationUpdateTime
    )

    // NEW: Live data for all member statuses by combining GroupMembers and UserModels
    val allMemberStatuses: StateFlow<List<MemberStatusDisplayData>> =
        combine(
            groupMonitorService.activeGroupAllMembers, // All GroupMembers for the active group
            groupMonitorService.otherMembersProfiles // UserModels for all members being monitored
        ) { activeGroupAllMembers, otherMembersProfilesMap ->
            Log.e("GroupStatusViewModel", "All Group Members: $activeGroupAllMembers")
            Log.e("GroupStatusViewModel", "Other Members Profiles Map: $otherMembersProfilesMap")

            val membersList = activeGroupAllMembers.map { groupMember ->
                // Retrieve the user profile from the map, which should contain all monitored users
                val userProfile = otherMembersProfilesMap[groupMember.userId]

                val isOnlineDerived = groupMember.appStatus == "FOREGROUND"

                MemberStatusDisplayData(
                    userId = groupMember.userId,
                    displayName = userProfile?.displayName ?: "Unknown User", // Use actual display name
                    profilePhotoUrl = userProfile?.profilePhotoUrl, // Use actual profile photo URL
                    batteryLevel = groupMember.batteryLevel,
                    batteryChargingStatus = groupMember.batteryChargingStatus,
                    appStatus = groupMember.appStatus,
                    isOnline = isOnlineDerived,
                    lastLocationUpdateTime = groupMember.lastLocationUpdateTime // NEW: Pass the lastLocationUpdateTime
                )
            }.sortedBy { it.displayName } // Sort alphabetically by display name

            Log.d("GroupStatusViewModel", "Combined member statuses: ${membersList.size} members.")
            membersList
        }.stateIn(
            viewModelScope,
            SharingStarted.Lazily,
            emptyList()
        )


    init {
        // Placeholder for future data fetching logic
        // For now, dummy data is static
        viewModelScope.launch {
            // Example of how you might update dummy data over time (for demonstration)
            // This can be removed once real data integration begins.
            // For example, simulate a new activity event every 10 seconds
            var activityCounter = 0
            while (true) {
                kotlinx.coroutines.delay(10000L) // Delay for 10 seconds
                activityCounter++
                val newActivity = when (activityCounter % 4) {
                    0 -> "Member ${('A'..'Z').random()} entered Geofence 'Zone ${activityCounter % 3 + 1}'"
                    1 -> "Member ${('A'..'Z').random()}'s battery is low (${10 + (activityCounter % 10)}%)."
                    2 -> "New photo posted by Member ${('A'..'Z').random()} in chat."
                    else -> "Operational update from Dispatcher X."
                }
                _overallActivityFeed.value = listOf(newActivity + " - Just now") + _overallActivityFeed.value.take(4) // Keep 5 most recent
                if (activityCounter % 2 == 0) _unreadGroupChatMessages.value = (_unreadGroupChatMessages.value + 1).coerceAtMost(99)

                // The old dummy simulation for allMemberStatuses is removed here,
                // as `allMemberStatuses` is now derived from GroupMonitorService flows.
            }
        }
    }
}
