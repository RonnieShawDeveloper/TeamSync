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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay // Added import for delay

class GroupMonitorService(
    private val context: Context,
    private val firestoreService: FirestoreService = FirestoreService(),
    internal val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val markerMonitorService: MarkerMonitorService
) {
    private val monitorScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _activeGroupMember = MutableStateFlow<GroupMembers?>(null)
    val activeGroupMember: StateFlow<GroupMembers?> = _activeGroupMember.asStateFlow()

    private val _activeGroup = MutableStateFlow<Groups?>(null)
    val activeGroup: StateFlow<Groups?> = _activeGroup.asStateFlow()

    private val _isInActiveGroup = MutableStateFlow(false)
    val isInActiveGroup: StateFlow<Boolean> = _isInActiveGroup.asStateFlow()

    private val _effectiveLocationUpdateInterval = MutableStateFlow(300000L)
    val effectiveLocationUpdateInterval: StateFlow<Long> = _effectiveLocationUpdateInterval.asStateFlow()

    private val _isLocationSharingGloballyEnabled = MutableStateFlow(true)
    val isLocationSharingGloballyEnabled: StateFlow<Boolean> = _isLocationSharingGloballyEnabled.asStateFlow()

    private val _otherGroupMembers = MutableStateFlow<List<GroupMembers>>(emptyList())
    val otherGroupMembers: StateFlow<List<GroupMembers>> = _otherGroupMembers.asStateFlow()

    private val _otherMembersLocations = MutableStateFlow<List<Locations>>(emptyList())
    val otherMembersLocations: StateFlow<List<Locations>> = _otherMembersLocations.asStateFlow()

    private val _otherMembersProfiles = MutableStateFlow<Map<String, UserModel>>(emptyMap())
    val otherMembersProfiles: StateFlow<Map<String, UserModel>> = _otherMembersProfiles.asStateFlow()

    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

    private val _isInitialGroupMembershipLoaded = MutableStateFlow(false)
    private var _currentlyExpectingGroupTransition: String? = null


    private var currentUserGroupMembershipsListener: ListenerRegistration? = null
    private var activeGroupDetailsListener: ListenerRegistration? = null
    private var otherGroupMembersListener: ListenerRegistration? = null
    private var otherMembersLocationsListener: ListenerRegistration? = null
    private var otherMembersProfilesListeners: MutableMap<String, ListenerRegistration> = mutableMapOf()

    private val _uiPermissionsGranted = MutableStateFlow(false)
    val uiPermissionsGranted: StateFlow<Boolean> = _uiPermissionsGranted.asStateFlow()

    init {
        Log.d("GroupMonitorService", "GroupMonitorService initialized. Awaiting explicit startMonitoring call.")
    }

    /**
     * Public method to set the permission status from the UI (MainScreen).
     * This will trigger the main combine flow to re-evaluate service startup.
     * @param granted True if all necessary runtime permissions are granted, false otherwise.
     */
    fun setUiPermissionsGranted(granted: Boolean) {
        if (_uiPermissionsGranted.value != granted) {
            _uiPermissionsGranted.value = granted
            Log.d("GroupMonitorService", "UI Permissions Granted status set to: $granted")
        }
    }


    /**
     * Stops listeners for other members' profile data and clears the profile map.
     */
    private fun stopOtherMembersProfilesListeners() {
        otherMembersProfilesListeners.values.forEach { it.remove() }
        otherMembersProfilesListeners.clear()
        _otherMembersProfiles.value = emptyMap()
        Log.d("GroupMonitorService", "Stopped other members' profile listeners.")
    }

    /**
     * Stops the listener for other members' location data and clears the location list.
     */
    private fun stopOtherMembersLocationsListener() {
        otherMembersLocationsListener?.remove()
        otherMembersLocationsListener = null
        _otherMembersLocations.value = emptyList()
        Log.d("GroupMonitorService", "Stopped other members' locations listener.")
    }

    /**
     * Stops all listeners related to other group members (memberships, locations, profiles).
     */
    private fun stopOtherMembersListeners() {
        otherGroupMembersListener?.remove()
        otherGroupMembersListener = null
        _otherGroupMembers.value = emptyList()

        stopOtherMembersLocationsListener()
        stopOtherMembersProfilesListeners()
        Log.d("GroupMonitorService", "Stopped other members' data listeners.")
    }

    /**
     * Stops all Firestore listeners managed by this service.
     */
    fun stopAllListeners() {
        currentUserGroupMembershipsListener?.remove()
        currentUserGroupMembershipsListener = null
        activeGroupDetailsListener?.remove()
        activeGroupDetailsListener = null
        stopOtherMembersListeners()
        markerMonitorService.stopMonitoringMarkers()
        Log.d("GroupMonitorService", "Stopped all Firestore listeners.")
        _isInitialGroupMembershipLoaded.value = false
        _currentlyExpectingGroupTransition = null
    }

    /**
     * Starts monitoring group data based on authentication status and UI permissions.
     * This method will now be called from MainScreen once permissions are handled.
     * @param expectedGroupId If provided, the service will wait for this specific group to become active
     * before considering initial data loaded.
     */
    fun startMonitoring(expectedGroupId: String? = null) {
        _currentlyExpectingGroupTransition = expectedGroupId
        Log.d("GroupMonitorService", "startMonitoring called. Expecting group transition: $expectedGroupId")

        // Only add auth state listener once.
        if (currentUserGroupMembershipsListener == null) {
            monitorScope.launch {
                auth.addAuthStateListener { firebaseAuth ->
                    val user = firebaseAuth.currentUser
                    if (user != null) {
                        Log.d("GroupMonitorService", "User logged in: ${user.uid}. Starting group member listener (gated by UI permissions).")
                        listenForCurrentUserGroupMemberships(user.uid)
                    } else {
                        Log.d("GroupMonitorService", "User logged out. Stopping all monitoring.")
                        stopAllListeners()
                        _activeGroupMember.value = null
                        _activeGroup.value = null
                        _isInActiveGroup.value = false
                        stopLocationTrackingService()
                        _userMessage.value = "You have been logged out."
                    }
                }
            }
        }


        monitorScope.launch {
            combine(
                _activeGroupMember,
                _activeGroup,
                _isLocationSharingGloballyEnabled,
                _isInitialGroupMembershipLoaded,
                _uiPermissionsGranted
            ) { args: Array<Any?> ->
                val member = args[0] as GroupMembers?
                val group = args[1] as Groups?
                val isSharingGloballyEnabled = args[2] as Boolean
                val isInitialLoad = args[3] as Boolean
                val uiPermissionsGranted = args[4] as Boolean

                val expectedId = _currentlyExpectingGroupTransition // Get expected ID from internal state

                val currentUserId = auth.currentUser?.uid

                val memberIsActive = member != null && member.unjoinedTimestamp == null
                val groupIsValidAndActive = group != null && (group.groupEndTimestamp?.let { it > System.currentTimeMillis() } ?: true)
                val inGroup = memberIsActive && groupIsValidAndActive

                // NEW: Logic to determine if we are in a "grace period" for group transition
                val inGroupTransitionGracePeriod = expectedId != null && (member?.groupId != expectedId || group?.groupID != expectedId || member.unjoinedTimestamp != null)

                Log.d("GroupMonitorService", "Combine Evaluation: member=${member?.groupId}, group=${group?.groupID}, ExpectedId=$expectedId, InGracePeriod=$inGroupTransitionGracePeriod")


                // Handle automatic unjoining from expired groups
                // NEW: Prevent auto-unjoin if we are currently in a group transition grace period
                if (!inGroupTransitionGracePeriod && memberIsActive && !groupIsValidAndActive && member?.unjoinedTimestamp == null) {
                    Log.w("GroupMonitorService", "Active member (${member.userId}) found in expired group (${group?.groupName ?: member.groupId}). Automatically unjoining.")
                    monitorScope.launch {
                        val unjoinTimestamp = System.currentTimeMillis()
                        val updatedMember = member.copy(unjoinedTimestamp = unjoinTimestamp)
                        firestoreService.saveGroupMember(updatedMember).onSuccess {
                            Log.d("GroupMonitorService", "Successfully marked membership ${member.id} as unjoined due to group expiration.")
                            if (currentUserId != null) {
                                val userProfile = firestoreService.getUserProfile(currentUserId).getOrNull()
                                if (userProfile?.selectedActiveGroupId == member.groupId) {
                                    firestoreService.updateUserSelectedActiveGroup(currentUserId, null).onSuccess {
                                        Log.d("GroupMonitorService", "Cleared selected active group for user $currentUserId.")
                                    }.onFailure { e ->
                                        Log.e("GroupMonitorService", "Failed to clear selected active group: ${e.message}")
                                    }
                                }
                            }
                            _userMessage.value = "Your active group '${group?.groupName ?: "Unknown Group"}' has expired. You have been automatically removed. Please create or join a new group."
                        }.onFailure { e ->
                            Log.e("GroupMonitorService", "Failed to unjoin member from expired group: ${e.message}")
                            _userMessage.value = "Error automatically removing you from expired group. Please try manually leaving the group."
                        }
                    }
                    _isInActiveGroup.value = false
                    stopLocationTrackingService()
                    stopOtherMembersListeners()
                    markerMonitorService.stopMonitoringMarkers()
                    return@combine
                }

                // Decide on isInActiveGroup for UI
                _isInActiveGroup.value = inGroup


                // Determine if services *can* be started based on app logic and permissions
                val canStartServices = inGroup && isSharingGloballyEnabled && uiPermissionsGranted

                Log.d("GroupMonitorService", "Final Service Decision: Can Start Services: $canStartServices (isInGroup: $inGroup, Sharing: $isSharingGloballyEnabled, UI Perms: $uiPermissionsGranted, Expected ID: $expectedId, InGracePeriod=$inGroupTransitionGracePeriod)")

                // IMPORTANT: Only start services if we are definitively in a state to do so.
                // If we are in a grace period (expecting a group to become active) but conditions
                // for starting services aren't met *yet*, do NOT stop services prematurely.
                if (canStartServices) {
                    // Reset grace period flag once the expected group is definitively active
                    if (expectedId != null && member?.groupId == expectedId && group?.groupID == expectedId && member.unjoinedTimestamp == null) {
                        _currentlyExpectingGroupTransition = null
                        Log.d("GroupMonitorService", "Expected group ($expectedId) fully loaded and active. Grace period ended.")
                    }

                    // Get the interval here, within the combine lambda's scope
                    val interval = member?.personalLocationUpdateIntervalMillis
                        ?: group?.locationUpdateIntervalMillis
                        ?: 300000L

                    // *** CRITICAL DELAY ADDED HERE before starting foreground service ***
                    // This delay is AFTER all logical conditions (inGroup, sharingEnabled, uiPermissionsGranted)
                    // and data consistency checks (isGroupDataFullyLoadedAndMatchesExpected) pass.
                    delay(1000) // Changed delay to 1000ms

                    startLocationTrackingService(interval, isSharingGloballyEnabled)
                    group?.groupID?.let { groupId ->
                        member?.userId?.let { userId ->
                            listenForOtherGroupMembers(groupId, userId)
                            markerMonitorService.startMonitoringMarkers(groupId)
                        }
                    }
                } else {
                    // Only stop services if we are NOT in a group transition grace period.
                    // This prevents flickering/crashing during the brief state changes of group creation.
                    if (!inGroupTransitionGracePeriod) {
                        Log.d("GroupMonitorService", "Stopping services: Not in group, or sharing disabled, or permissions not granted, AND NOT in a grace period.")
                        stopLocationTrackingService()
                        stopOtherMembersListeners()
                        markerMonitorService.stopMonitoringMarkers()
                    } else {
                        Log.d("GroupMonitorService", "Services not starting, but staying active/on hold due to active group transition grace period for $expectedId.")
                        // Keep services potentially running, but not actively tracking if conditions aren't met yet
                        // (e.g., location sharing disabled within the new group, but we are still waiting for it to be fully active).
                        // More importantly, prevent stopping during the critical transient period.
                    }
                }
            }.collect { /* collect to keep the flow active */ }
        }
    }

    /**
     * Listens for the current user's group memberships from Firestore.
     * @param userId The ID of the current authenticated user.
     */
    private fun listenForCurrentUserGroupMemberships(userId: String) {
        currentUserGroupMembershipsListener?.remove()

        currentUserGroupMembershipsListener = db.collection("groupMembers")
            .whereEqualTo("userId", userId)
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    Log.w("GroupMonitorService", "Listen for current user's group memberships failed.", e)
                    _activeGroupMember.value = null
                    _activeGroup.value = null
                    _isInActiveGroup.value = false
                    stopOtherMembersListeners()
                    _isInitialGroupMembershipLoaded.value = true // Even if failed, initial load is done for this part
                    return@addSnapshotListener
                }

                if (snapshots != null && !snapshots.isEmpty()) { // Ensure snapshots is not empty before mapping
                    val allMemberships = snapshots.documents.mapNotNull { doc ->
                        doc.toObject(GroupMembers::class.java)?.copy(id = doc.id)
                    }
                    val activeMemberships = allMemberships.filter { it.unjoinedTimestamp == null }

                    var primaryActiveMember: GroupMembers? = null
                    // Fetch user profile to get selectedActiveGroupId
                    val currentUserProfile = runBlocking(Dispatchers.IO) { // Consider making this async
                        firestoreService.getUserProfile(userId).getOrNull()
                    }
                    val selectedActiveGroupId = currentUserProfile?.selectedActiveGroupId

                    if (selectedActiveGroupId != null) {
                        primaryActiveMember = activeMemberships.find { it.groupId == selectedActiveGroupId }
                        if (primaryActiveMember == null) {
                            Log.d("GroupMonitorService", "Selected active group ($selectedActiveGroupId) not found or not active. Falling back to most recent active.")
                            monitorScope.launch {
                                // Clear selected active group if it's no longer valid/active
                                firestoreService.updateUserSelectedActiveGroup(userId, null)
                            }
                        }
                    }

                    // If no explicit active group, try to find the most recent active one
                    if (primaryActiveMember == null) {
                        primaryActiveMember = activeMemberships.maxByOrNull { it.joinedTimestamp }
                    }

                    if (primaryActiveMember != null) {
                        Log.d("GroupMonitorService", "Active group member found: ${primaryActiveMember.userId} in group ${primaryActiveMember.groupId}")
                        _activeGroupMember.value = primaryActiveMember
                        listenForActiveGroupDetails(primaryActiveMember.groupId) {
                            _isInitialGroupMembershipLoaded.value = true // Data for primary member/group is loaded
                        }
                    } else {
                        Log.d("GroupMonitorService", "No active group memberships found for current user.")
                        _activeGroupMember.value = null
                        _activeGroup.value = null
                        _isInActiveGroup.value = false
                        stopOtherMembersListeners()
                        _isInitialGroupMembershipLoaded.value = true // No active membership, so initial load is done
                    }
                } else {
                    Log.d("GroupMonitorService", "No group memberships found for current user.")
                    _activeGroupMember.value = null
                    _activeGroup.value = null
                    _isInActiveGroup.value = false
                    stopOtherMembersListeners()
                    _isInitialGroupMembershipLoaded.value = true // No memberships at all, initial load is done
                }
            }
    }

    /**
     * Listens for details of the currently active group.
     * @param groupId The ID of the active group.
     * @param onInitialLoadComplete Optional callback for initial load completion.
     */
    private fun listenForActiveGroupDetails(groupId: String, onInitialLoadComplete: (() -> Unit)? = null) {
        activeGroupDetailsListener?.remove()

        activeGroupDetailsListener = db.collection("groups").document(groupId)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.w("GroupMonitorService", "Listen for active group details failed.", e)
                    _activeGroup.value = null
                    onInitialLoadComplete?.invoke()
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    val group = snapshot.toObject(Groups::class.java)
                    if (group != null) {
                        Log.d("GroupMonitorService", "Active group details updated: ${group.groupName}")
                        _activeGroup.value = group
                    } else {
                        Log.d("GroupMonitorService", "Active group data is null, or group no longer exists.")
                        _activeGroup.value = null
                    }
                } else {
                    Log.d("GroupMonitorService", "Active group document no longer exists in Firestore for ID: $groupId. Triggering cleanup.")
                    _activeGroup.value = null
                }
                onInitialLoadComplete?.invoke()
            }
    }

    /**
     * Listens for other active members within the specified group.
     * @param groupId The ID of the group.
     * @param currentUserId The ID of the current user, to exclude them from the "other members" list.
     */
    private fun listenForOtherGroupMembers(groupId: String, currentUserId: String) {
        otherGroupMembersListener?.remove()

        if (groupId.isEmpty()) {
            _otherGroupMembers.value = emptyList()
            stopOtherMembersLocationsListener()
            stopOtherMembersProfilesListeners()
            return
        }

        otherGroupMembersListener = db.collection("groupMembers")
            .whereEqualTo("groupId", groupId)
            .addSnapshotListener { snapshots, e ->
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
     * @param userIds A list of user IDs whose locations to monitor.
     */
    private fun listenForOtherMembersLocations(userIds: List<String>) {
        otherMembersLocationsListener?.remove()

        if (userIds.isEmpty()) {
            _otherMembersLocations.value = emptyList()
            return
        }

        val distinctUserIds = userIds.distinct()
        if (distinctUserIds.size > 10) {
            Log.w("GroupMonitorService", "Too many users (${distinctUserIds.size}) for single 'whereIn' query. Limiting to first 10 for locations.")
        }

        otherMembersLocationsListener = db.collection("current_user_locations")
            .whereIn("userId", distinctUserIds.take(10))
            .addSnapshotListener { snapshots, e ->
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
     * @param userIds A list of user IDs whose profiles to monitor.
     */
    private fun listenForOtherMembersProfiles(userIds: List<String>) {
        stopOtherMembersProfilesListeners()

        if (userIds.isEmpty()) {
            _otherMembersProfiles.value = emptyMap()
            return
        }

        val newListeners = mutableMapOf<String, ListenerRegistration>()

        userIds.forEach { userId ->
            val listener = db.collection("users").document(userId)
                .addSnapshotListener { snapshot, e ->
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
     * Starts the LocationTrackingService (Foreground Service).
     * @param interval The desired location update interval.
     * @param isSharing Boolean indicating if location sharing is enabled.
     */
    private fun startLocationTrackingService(interval: Long, isSharing: Boolean) {
        val serviceIntent = Intent(context, LocationTrackingService::class.java).apply {
            action = LocationTrackingService.ACTION_START_SERVICE
            putExtra(LocationTrackingService.EXTRA_LOCATION_INTERVAL, interval)
            putExtra(LocationTrackingService.EXTRA_IS_SHARING_LOCATION, isSharing)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
        Log.d("GroupMonitorService", "Requested LocationTrackingService START.")
    }

    /**
     * Stops the LocationTrackingService (Foreground Service).
     */
    private fun stopLocationTrackingService() {
        val serviceIntent = Intent(context, LocationTrackingService::class.java).apply {
            action = LocationTrackingService.ACTION_STOP_SERVICE
        }
        context.stopService(serviceIntent)
        Log.d("GroupMonitorService", "Requested LocationTrackingService STOP.")
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
