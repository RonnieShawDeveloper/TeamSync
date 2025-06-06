// In file: app/src/main/java/com/artificialinsightsllc/teamsync/Services/GroupMonitorService.kt
package com.artificialinsightsllc.teamsync.Services

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.artificialinsightsllc.teamsync.Models.Groups
import com.artificialinsightsllc.teamsync.Models.GroupMembers
import com.artificialinsightsllc.teamsync.Models.Locations
import com.artificialinsightsllc.teamsync.Models.UserModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel
import kotlinx.coroutines.Job // Import Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map // Explicitly import map for derived StateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay

class GroupMonitorService(
    internal val context: Context,
    private val firestoreService: FirestoreService = FirestoreService(),
    internal val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val markerMonitorService: MarkerMonitorService
) {
    private val monitorScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    public enum class GroupDetailsStatus {
        LOADING,           // Initial state, or after a membership changes and we're fetching new group details
        ACTIVE,            // Group details loaded, and groupEndTimestamp is in the future
        EXPIRED,           // Group details loaded, and groupEndTimestamp is in the past
        NOT_FOUND,         // Group document does not exist in Firestore (e.g., deleted by owner)
        ERROR              // An error occurred while fetching group details (e.g., network issue)
    }

    private val _activeGroupMember = MutableStateFlow<GroupMembers?>(null)
    val activeGroupMember: StateFlow<GroupMembers?> = _activeGroupMember.asStateFlow()

    private val _activeGroup = MutableStateFlow<Groups?>(null)
    val activeGroup: StateFlow<Groups?> = _activeGroup.asStateFlow()

    private val _isInActiveGroup = MutableStateFlow(false)
    val isInActiveGroup: StateFlow<Boolean> = _isInActiveGroup.asStateFlow()

    private val _effectiveLocationUpdateInterval = MutableStateFlow(300000L)
    val effectiveLocationUpdateInterval: StateFlow<Long> = _effectiveLocationUpdateInterval.asStateFlow()

    val isLocationSharingGloballyEnabled: StateFlow<Boolean> = _isInActiveGroup.asStateFlow()


    private val _otherGroupMembers = MutableStateFlow<List<GroupMembers>>(emptyList())
    val otherGroupMembers: StateFlow<List<GroupMembers>> = _otherGroupMembers.asStateFlow()

    private val _otherMembersLocations = MutableStateFlow<List<Locations>>(emptyList())
    val otherMembersLocations: StateFlow<List<Locations>> = _otherMembersLocations.asStateFlow()

    private val _otherMembersProfiles = MutableStateFlow<Map<String, UserModel>>(emptyMap())
    val otherMembersProfiles: StateFlow<Map<String, UserModel>> = _otherMembersProfiles.asStateFlow()

    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

    private val _groupDetailsStatus = MutableStateFlow(GroupDetailsStatus.LOADING)
    val groupDetailsStatus: StateFlow<GroupDetailsStatus> = _groupDetailsStatus.asStateFlow()


    private var currentUserGroupMembershipsListener: ListenerRegistration? = null
    private var activeGroupDetailsListener: ListenerRegistration? = null
    private var otherGroupMembersListener: ListenerRegistration? = null
    private var otherMembersLocationsListener: ListenerRegistration? = null
    private var otherMembersProfilesListeners: MutableMap<String, ListenerRegistration> = mutableMapOf()

    private val _uiPermissionsGranted = MutableStateFlow(false)
    val uiPermissionsGranted: StateFlow<Boolean> = _uiPermissionsGranted.asStateFlow()

    private var locationTrackingIntentJob: Job? = null

    init {
        Log.d("GroupMonitorService", "GroupMonitorService initialized. Awaiting explicit startMonitoring call.")
    }

    /**
     * Public method to set the permission status from the UI (MainScreen).
     * @param granted True if all necessary runtime permissions are granted, false otherwise.
     */
    fun setUiPermissionsGranted(granted: Boolean) {
        if (_uiPermissionsGranted.value != granted) {
            _uiPermissionsGranted.value = granted
            Log.d("GroupMonitorService", "UI Permissions Granted status set to: $granted")
        }
    }

    private fun stopOtherMembersProfilesListeners() {
        otherMembersProfilesListeners.values.forEach { it.remove() }
        otherMembersProfilesListeners.clear()
        _otherMembersProfiles.value = emptyMap()
        Log.d("GroupMonitorService", "Stopped other members' profile listeners.")
    }

    private fun stopOtherMembersLocationsListener() {
        otherMembersLocationsListener?.remove()
        otherMembersLocationsListener = null
        _otherMembersLocations.value = emptyList()
        Log.d("GroupMonitorService", "Stopped other members' locations listener.")
    }

    private fun stopOtherMembersListeners() {
        otherGroupMembersListener?.remove()
        otherGroupMembersListener = null
        _otherGroupMembers.value = emptyList()

        stopOtherMembersLocationsListener()
        stopOtherMembersProfilesListeners()
        Log.d("GroupMonitorService", "Stopped other members' data listeners.")
    }

    fun stopAllListeners() {
        currentUserGroupMembershipsListener?.remove()
        currentUserGroupMembershipsListener = null
        activeGroupDetailsListener?.remove()
        activeGroupDetailsListener = null
        stopOtherMembersListeners()
        markerMonitorService.stopMonitoringMarkers()
        Log.d("GroupMonitorService", "Stopped all Firestore listeners.")
        _groupDetailsStatus.value = GroupDetailsStatus.LOADING
        locationTrackingIntentJob?.cancel()
    }

    private fun sendLocationTrackingIntentDebounced(interval: Long, isSharing: Boolean, stopServiceCompletely: Boolean, memberId: String?) {
        locationTrackingIntentJob?.cancel()
        locationTrackingIntentJob = monitorScope.launch {
            delay(100)
            sendLocationTrackingUpdateIntent(interval, isSharing, stopServiceCompletely, memberId)
        }
    }

    private fun sendLocationTrackingUpdateIntent(interval: Long, isSharing: Boolean, stopServiceCompletely: Boolean, memberId: String?) {
        val intent = Intent(context, LocationTrackingService::class.java).apply {
            action = if (stopServiceCompletely) LocationTrackingService.ACTION_STOP_SERVICE else LocationTrackingService.ACTION_UPDATE_TRACKING_STATE
            putExtra(LocationTrackingService.EXTRA_LOCATION_INTERVAL, interval)
            putExtra(LocationTrackingService.EXTRA_IS_SHARING_LOCATION, isSharing)
            putExtra(LocationTrackingService.EXTRA_ACTIVE_GROUP_MEMBER_ID, memberId)
        }
        if (stopServiceCompletely) {
            context.stopService(intent)
            Log.d("GroupMonitorService", "Explicitly sending ACTION_STOP_SERVICE to LocationTrackingService.")
        } else {
            context.startService(intent)
            Log.d("GroupMonitorService", "Sending ACTION_UPDATE_TRACKING_STATE to LocationTrackingService: isSharing=$isSharing, interval=$interval, memberId=$memberId.")
        }
    }

    fun startMonitoring(selectedActiveGroupId: String? = null) {
        if (currentUserGroupMembershipsListener == null) {
            auth.addAuthStateListener { firebaseAuth ->
                val user = firebaseAuth.currentUser
                if (user != null) {
                    Log.d("GroupMonitorService", "User logged in: ${user.uid}. Starting group member listener (gated by UI permissions).")
                    listenForCurrentUserGroupMemberships(user.uid, selectedActiveGroupId)
                } else {
                    Log.d("GroupMonitorService", "User logged out. Stopping all monitoring.")
                    stopAllListeners()
                    _activeGroupMember.value = null
                    _activeGroup.value = null
                    _isInActiveGroup.value = false
                    sendLocationTrackingIntentDebounced(0L, false, true, null)
                    _userMessage.value = "You have been logged out."
                }
            }
        }

        monitorScope.launch {
            var lastProcessedDesiredIsSharing: Boolean? = null
            var lastProcessedDesiredInterval: Long? = null
            var lastProcessedDesiredMemberId: String? = null

            combine(
                _activeGroupMember,
                _activeGroup,
                _groupDetailsStatus,
                _uiPermissionsGranted
            ) { args: Array<Any?> ->
                val member = args[0] as GroupMembers?
                val group = args[1] as Groups?
                val detailsStatus = args[2] as GroupDetailsStatus
                val uiPermissionsGranted = args[3] as Boolean

                val currentUserId = auth.currentUser?.uid

                val memberIsActive = member != null && member.unjoinedTimestamp == null
                val groupIsValidAndActive = group != null && (group.groupEndTimestamp?.let { it > System.currentTimeMillis() } ?: true)

                Log.d("GroupMonitorService", "Combine Evaluation: member=${member?.groupId}, group=${group?.groupID}, DetailsStatus=$detailsStatus, UI Permissions Granted: $uiPermissionsGranted")

                val desiredIsSharing: Boolean
                val desiredInterval: Long
                val desiredMemberId: String?

                when (detailsStatus) {
                    GroupDetailsStatus.LOADING, GroupDetailsStatus.ERROR -> {
                        desiredIsSharing = false
                        desiredInterval = 0L
                        desiredMemberId = member?.id
                        Log.d("GroupMonitorService", "Group details are LOADING or ERROR. Not actively tracking.")
                        _isInActiveGroup.value = false
                        stopOtherMembersListeners()
                        markerMonitorService.stopMonitoringMarkers()
                    }
                    GroupDetailsStatus.ACTIVE -> {
                        if (memberIsActive && member?.groupId == group?.groupID) {
                            val nonNullMember = member!!
                            val nonNullGroup = group!!

                            desiredIsSharing = uiPermissionsGranted // Always share if UI permissions are granted when group is active

                            desiredInterval = nonNullMember.personalLocationUpdateIntervalMillis
                                ?: nonNullGroup.locationUpdateIntervalMillis
                                        ?: 300000L
                            desiredMemberId = nonNullMember.id

                            Log.d("GroupMonitorService", "Group is ACTIVE and member active. Desired tracking: $desiredIsSharing, interval: $desiredInterval.")
                            _isInActiveGroup.value = true
                            listenForOtherGroupMembers(nonNullGroup.groupID, nonNullMember.userId)
                            markerMonitorService.startMonitoringMarkers(nonNullGroup.groupID)
                        } else {
                            desiredIsSharing = false
                            desiredInterval = 0L
                            desiredMemberId = member?.id
                            Log.d("GroupMonitorService", "Group is ACTIVE but member is not. Not actively tracking.")
                            _isInActiveGroup.value = false
                            stopOtherMembersListeners()
                            markerMonitorService.stopMonitoringMarkers()
                        }
                    }
                    GroupDetailsStatus.EXPIRED, GroupDetailsStatus.NOT_FOUND -> {
                        desiredIsSharing = false
                        desiredInterval = 0L
                        desiredMemberId = member?.id
                        Log.d("GroupMonitorService", "Group EXPIRED or NOT_FOUND. Not actively tracking.")
                        _isInActiveGroup.value = false
                        stopOtherMembersListeners()
                        markerMonitorService.stopMonitoringMarkers()

                        if (memberIsActive && member?.unjoinedTimestamp == null && currentUserId != null && member?.groupId == _activeGroup.value?.groupID) {
                            val messagePrefix = if (detailsStatus == GroupDetailsStatus.EXPIRED) "expired" else "no longer exists"
                            Log.w("GroupMonitorService", "Active member (${member.userId}) found in $messagePrefix group (${_activeGroup.value?.groupName ?: member.groupId}). Automatically unjoining.")
                            monitorScope.launch {
                                val unjoinTimestamp = System.currentTimeMillis()
                                val updatedMember = member.copy(unjoinedTimestamp = unjoinTimestamp)
                                firestoreService.saveGroupMember(updatedMember).onSuccess {
                                    Log.d("GroupMonitorService", "Successfully marked membership ${member.id} as unjoined due to group $messagePrefix.")
                                    firestoreService.updateUserSelectedActiveGroup(currentUserId, null).onSuccess {
                                        Log.d("GroupMonitorService", "Cleared selected active group for user $currentUserId.")
                                    }.onFailure { e ->
                                        Log.e("GroupMonitorService", "Failed to clear selected active group: ${e.message}")
                                    }
                                    _userMessage.value = "Your active group '${_activeGroup.value?.groupName ?: "Unknown Group"}' has $messagePrefix. You have been automatically removed. Please create or join a new group."
                                }.onFailure { e ->
                                    Log.e("GroupMonitorService", "Failed to unjoin member from $messagePrefix group: ${e.message}")
                                    _userMessage.value = "Error automatically removing you from expired group. Please try manually leaving the group."
                                }
                            }
                        } else {
                            Log.d("GroupMonitorService", "Group is EXPIRED/NOT_FOUND but member is already inactive or not current. No unjoin action needed.")
                        }
                    }
                }

                if (desiredIsSharing != lastProcessedDesiredIsSharing ||
                    desiredInterval != lastProcessedDesiredInterval ||
                    desiredMemberId != lastProcessedDesiredMemberId) {

                    sendLocationTrackingIntentDebounced(desiredInterval, desiredIsSharing, false, desiredMemberId)

                    lastProcessedDesiredIsSharing = desiredIsSharing
                    lastProcessedDesiredInterval = desiredInterval
                    lastProcessedDesiredMemberId = desiredMemberId
                } else {
                    Log.d("GroupMonitorService", "Location tracking intent state unchanged. Skipping dispatch.")
                }

                _effectiveLocationUpdateInterval.value = desiredInterval

            }.collect { /* collect to keep the flow active */ }
        }
    }


    /**
     * Listens for the current user's group memberships from Firestore.
     * @param userId The ID of the current authenticated user.
     * @param initialSelectedGroupId An optional group ID to prioritize as active.
     */
    private fun listenForCurrentUserGroupMemberships(userId: String, initialSelectedGroupId: String?) {
        currentUserGroupMembershipsListener?.remove()

        currentUserGroupMembershipsListener = db.collection("groupMembers")
            .whereEqualTo("userId", userId)
            .addSnapshotListener { snapshots, e ->
                Log.d("GroupMonitorService", "listenForCurrentUserGroupMemberships: Listener triggered for user $userId. Error: ${e?.message}, Snapshots: ${snapshots?.size()}")
                if (e != null) {
                    Log.w("GroupMonitorService", "Listen for current user's group memberships failed.", e)
                    _activeGroupMember.value = null
                    _activeGroup.value = null
                    _isInActiveGroup.value = false
                    stopOtherMembersListeners()
                    _groupDetailsStatus.value = GroupDetailsStatus.ERROR
                    return@addSnapshotListener
                }

                if (snapshots != null && !snapshots.isEmpty()) {
                    val allMemberships = snapshots.documents.mapNotNull { doc ->
                        doc.toObject(GroupMembers::class.java)?.copy(id = doc.id)
                    }
                    val activeMemberships = allMemberships.filter { it.unjoinedTimestamp == null }
                    Log.d("GroupMonitorService", "listenForCurrentUserGroupMemberships: Found ${activeMemberships.size} active memberships.")

                    var primaryActiveMember: GroupMembers? = null

                    if (initialSelectedGroupId != null) {
                        primaryActiveMember = activeMemberships.find { it.groupId == initialSelectedGroupId }
                        if (primaryActiveMember == null) {
                            Log.d("GroupMonitorService", "Selected active group ($initialSelectedGroupId) not found or not active. Falling back to most recent active.")
                            monitorScope.launch {
                                firestoreService.updateUserSelectedActiveGroup(userId, null)
                            }
                        }
                    }

                    if (primaryActiveMember == null) {
                        primaryActiveMember = activeMemberships.maxByOrNull { it.joinedTimestamp }
                    }


                    if (primaryActiveMember != null) {
                        Log.d("GroupMonitorService", "Active group member found: ${primaryActiveMember.userId} in group ${primaryActiveMember.groupId}")
                        _activeGroupMember.value = primaryActiveMember
                        _groupDetailsStatus.value = GroupDetailsStatus.LOADING // Set to loading while fetching details
                        listenForActiveGroupDetails(primaryActiveMember.groupId)
                    } else {
                        Log.d("GroupMonitorService", "No active group memberships found for current user.")
                        _activeGroupMember.value = null
                        _activeGroup.value = null
                        _isInActiveGroup.value = false
                        stopOtherMembersListeners()
                        _groupDetailsStatus.value = GroupDetailsStatus.NOT_FOUND
                    }
                } else {
                    Log.d("GroupMonitorService", "No group memberships found for current user.")
                    _activeGroupMember.value = null
                    _activeGroup.value = null
                    _isInActiveGroup.value = false
                    stopOtherMembersListeners()
                    _groupDetailsStatus.value = GroupDetailsStatus.NOT_FOUND
                }
            }
    }

    /**
     * Listens for details of the currently active group.
     */
    private fun listenForActiveGroupDetails(groupId: String) {
        activeGroupDetailsListener?.remove()

        Log.d("GroupMonitorService", "listenForActiveGroupDetails: Attaching listener for group $groupId.")
        activeGroupDetailsListener = db.collection("groups").document(groupId)
            .addSnapshotListener { snapshot, e ->
                Log.d("GroupMonitorService", "listenForActiveGroupDetails: Snapshot received for group $groupId. Exists: ${snapshot?.exists()}, Error: ${e?.message}")
                if (e != null) {
                    Log.w("GroupMonitorService", "Listen for active group details failed for $groupId.", e)
                    _activeGroup.value = null
                    _groupDetailsStatus.value = GroupDetailsStatus.ERROR
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    val group = snapshot.toObject(Groups::class.java)
                    if (group != null) {
                        Log.d("GroupMonitorService", "Active group details updated: ${group.groupName}")
                        _activeGroup.value = group
                        if (group.groupEndTimestamp?.let { it > System.currentTimeMillis() } ?: true) {
                            _groupDetailsStatus.value = GroupDetailsStatus.ACTIVE
                        } else {
                            _groupDetailsStatus.value = GroupDetailsStatus.EXPIRED
                        }
                    } else {
                        Log.d("GroupMonitorService", "Active group data for $groupId is null or malformed. Treating as NOT_FOUND.")
                        _activeGroup.value = null
                        _groupDetailsStatus.value = GroupDetailsStatus.NOT_FOUND
                    }
                } else {
                    Log.d("GroupMonitorService", "Active group document $groupId no longer exists in Firestore. Triggering cleanup.")
                    _activeGroup.value = null
                    _groupDetailsStatus.value = GroupDetailsStatus.NOT_FOUND
                }
            }
    }

    /**
     * Listens for other active members within the specified group.
     */
    private fun listenForOtherGroupMembers(groupId: String, currentUserId: String) {
        otherGroupMembersListener?.remove()
        Log.d("GroupMonitorService", "listenForOtherGroupMembers: Attaching listener for group $groupId, excluding $currentUserId.")

        if (groupId.isEmpty()) {
            _otherGroupMembers.value = emptyList()
            stopOtherMembersLocationsListener()
            stopOtherMembersProfilesListeners()
            Log.d("GroupMonitorService", "listenForOtherGroupMembers: Group ID is empty, skipping listener attachment.")
            return
        }

        otherGroupMembersListener = db.collection("groupMembers")
            .whereEqualTo("groupId", groupId)
            .addSnapshotListener { snapshots, e ->
                Log.d("GroupMonitorService", "listenForOtherGroupMembers: Snapshot received. Error: ${e?.message}, Snapshots: ${snapshots?.size()}")
                if (e != null) {
                    Log.w("GroupMonitorService", "Listen for other group members failed.", e)
                    _otherGroupMembers.value = emptyList()
                    stopOtherMembersLocationsListener()
                    stopOtherMembersProfilesListeners()
                    return@addSnapshotListener
                }

                if (snapshots != null) {
                    val otherMembers = snapshots.documents.mapNotNull { doc ->
                        doc.toObject(GroupMembers::class.java)?.copy(id = doc.id)
                    }.filter {
                        it.userId != currentUserId &&
                                it.unjoinedTimestamp == null &&
                                it.sharingLocation
                    }
                    _otherGroupMembers.value = otherMembers
                    Log.d("GroupMonitorService", "Found ${otherMembers.size} other active members.")

                    val memberUserIds = otherMembers.map { it.userId }.distinct()
                    listenForOtherMembersLocations(memberUserIds)
                    listenForOtherMembersProfiles(memberUserIds)
                }
            }
    }

    /**
     * Listens for the current location of specified other members.
     */
    private fun listenForOtherMembersLocations(userIds: List<String>) {
        otherMembersLocationsListener?.remove()
        Log.d("GroupMonitorService", "listenForOtherMembersLocations: Attaching listener for ${userIds.size} user locations.")

        if (userIds.isEmpty()) {
            _otherMembersLocations.value = emptyList()
            Log.d("GroupMonitorService", "listenForOtherMembersLocations: User IDs list is empty, skipping listener attachment.")
            return
        }

        val distinctUserIds = userIds.distinct().take(10)

        if (distinctUserIds.isEmpty()) {
            _otherMembersLocations.value = emptyList()
            Log.d("GroupMonitorService", "listenForOtherMembersLocations: Distinct user IDs list is empty after take(10), skipping listener attachment.")
            return
        }

        otherMembersLocationsListener = db.collection("current_user_locations")
            .whereIn("userId", distinctUserIds)
            .addSnapshotListener { snapshots, e ->
                Log.d("GroupMonitorService", "listenForOtherMembersLocations: Snapshot received. Error: ${e?.message}, Snapshots: ${snapshots?.size()}")
                if (e != null) {
                    Log.w("GroupMonitorService", "Listen for other members' locations failed.", e)
                    _otherMembersLocations.value = emptyList()
                    return@addSnapshotListener
                }

                if (snapshots != null) {
                    val locations = snapshots.documents.mapNotNull { doc ->
                        doc.toObject(Locations::class.java)
                    }
                    _otherMembersLocations.value = locations
                    Log.d("GroupMonitorService", "Updated ${locations.size} other members' locations.")
                }
            }
    }

    /**
     * Listens for profile updates of specified other members.
     */
    private fun listenForOtherMembersProfiles(userIds: List<String>) {
        stopOtherMembersProfilesListeners()
        Log.d("GroupMonitorService", "listenForOtherMembersProfiles: Attaching listeners for ${userIds.size} user profiles.")

        if (userIds.isEmpty()) {
            _otherMembersProfiles.value = emptyMap()
            Log.d("GroupMonitorService", "listenForOtherMembersProfiles: User IDs list is empty, skipping listener attachment.")
            return
        }

        val newListeners = mutableMapOf<String, ListenerRegistration>()

        userIds.distinct().forEach { userId ->
            val listener = db.collection("users").document(userId)
                .addSnapshotListener { snapshot, e ->
                    Log.d("GroupMonitorService", "listenForOtherMembersProfiles: Snapshot received for $userId. Exists: ${snapshot?.exists()}, Error: ${e?.message}")
                    if (e != null) {
                        Log.w("GroupMonitorService", "Listen for profile $userId failed.", e)
                        _otherMembersProfiles.update { it.toMutableMap().apply { remove(userId) } }
                        return@addSnapshotListener
                    }

                    if (snapshot != null && snapshot.exists()) {
                        val userProfile = snapshot.toObject(UserModel::class.java)
                        if (userProfile != null) {
                            _otherMembersProfiles.update { it.toMutableMap().apply { put(userId, userProfile) } }
                            Log.d("GroupMonitorService", "Updated profile for $userId.")
                        } else {
                            _otherMembersProfiles.update { it.toMutableMap().apply { remove(userId) } }
                        }
                    } else {
                        _otherMembersProfiles.update { it.toMutableMap().apply { remove(userId) } }
                    }
                }
            newListeners[userId] = listener
        }
        otherMembersProfilesListeners = newListeners
    }

    /**
     * Updates the user's personal location update interval in Firestore.
     * @param newInterval The new interval in milliseconds, or null to clear override.
     */
    suspend fun updatePersonalLocationInterval(newInterval: Long?) {
        val currentMember = _activeGroupMember.value
        if (currentMember != null) {
            val updatedMember = currentMember.copy(personalLocationUpdateIntervalMillis = newInterval)
            val result = firestoreService.saveGroupMember(updatedMember)
            if (result.isSuccess) {
                Log.d("GroupMonitorService", "Updated personal location interval for ${currentMember.userId}")
            } else {
                Log.e("GroupMonitorService", "Failed to update personal interval: ${result.exceptionOrNull()?.message}")
            }
        }
    }

    /**
     * Updates the user's personal location sharing preference in Firestore.
     * @param isSharing True to share, false to stop sharing, or null to clear override.
     */
    suspend fun updatePersonalLocationSharing(isSharing: Boolean?) {
        val currentMember = _activeGroupMember.value
        if (currentMember != null) {
            val updatedMember = currentMember.copy(personalIsSharingLocationOverride = isSharing)
            val result = firestoreService.saveGroupMember(updatedMember)
            if (result.isSuccess) {
                Log.d("GroupMonitorService", "Updated personal location sharing for ${currentMember.userId}")
            } else {
                Log.e("GroupMonitorService", "Failed to update personal sharing: ${result.exceptionOrNull()?.message}")
            }
        }
    }

    /**
     * Shuts down the service's coroutine scope and stops all listeners.
     */
    fun shutdown() {
        stopAllListeners()
        monitorScope.cancel()
        Log.d("GroupMonitorService", "GroupMonitorService shutdown.")
    }

    /**
     * Clears any active user message.
     */
    fun clearUserMessage() {
        _userMessage.value = null
    }
}
