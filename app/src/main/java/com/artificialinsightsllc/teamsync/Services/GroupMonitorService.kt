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
import kotlinx.coroutines.runBlocking // Used for synchronous profile fetch, consider async alternative for production

class GroupMonitorService(
    private val context: Context,
    private val firestoreService: FirestoreService = FirestoreService(),
    internal val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val markerMonitorService: MarkerMonitorService = MarkerMonitorService() // NEW: Inject MarkerMonitorService
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

    // NEW: Flag to indicate if initial group membership data has been loaded
    private val _isInitialGroupMembershipLoaded = MutableStateFlow(false)


    private var currentUserGroupMembershipsListener: ListenerRegistration? = null
    private var activeGroupDetailsListener: ListenerRegistration? = null
    private var otherGroupMembersListener: ListenerRegistration? = null
    private var otherMembersLocationsListener: ListenerRegistration? = null
    private var otherMembersProfilesListeners: MutableMap<String, ListenerRegistration> = mutableMapOf()

    init {
        Log.d("GroupMonitorService", "GroupMonitorService initialized.")
        startMonitoring()
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

    private fun stopAllListeners() {
        currentUserGroupMembershipsListener?.remove()
        currentUserGroupMembershipsListener = null
        activeGroupDetailsListener?.remove()
        activeGroupDetailsListener = null
        stopOtherMembersListeners()
        markerMonitorService.stopMonitoringMarkers() // NEW: Stop marker monitoring
        Log.d("GroupMonitorService", "Stopped all Firestore listeners.")
        _isInitialGroupMembershipLoaded.value = false // Reset flag on full stop
    }


    private fun startMonitoring() {
        monitorScope.launch {
            auth.addAuthStateListener { firebaseAuth ->
                val user = firebaseAuth.currentUser
                if (user != null) {
                    Log.d("GroupMonitorService", "User logged in: ${user.uid}. Starting group member listener.")
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

        monitorScope.launch {
            combine(
                _activeGroupMember,
                _activeGroup,
                _isLocationSharingGloballyEnabled,
                _isInitialGroupMembershipLoaded // NEW: Add initial data loaded flag
            ) { member, group, isSharingGloballyEnabled, isInitialLoad -> // Unpack the new flow
                // NEW: Only proceed if initial data has been loaded
                if (!isInitialLoad) {
                    Log.d("GroupMonitorService", "Combine waiting for initial data load...")
                    return@combine
                }

                val groupIsValidAndActive = group != null && (group.groupEndTimestamp?.let { it > System.currentTimeMillis() } ?: true)
                val memberIsActive = member != null && member.unjoinedTimestamp == null
                val inGroup = memberIsActive && groupIsValidAndActive

                _isInActiveGroup.value = inGroup

                val interval = member?.personalLocationUpdateIntervalMillis
                    ?: group?.locationUpdateIntervalMillis
                    ?: 300000L
                _effectiveLocationUpdateInterval.value = interval

                val sharingEnabled = member?.personalIsSharingLocationOverride
                    ?: member?.sharingLocation
                    ?: true
                _isLocationSharingGloballyEnabled.value = sharingEnabled

                Log.d("GroupMonitorService", "isInActiveGroup: $inGroup, Effective Interval: $interval, Sharing Enabled: $sharingEnabled (Initial Load: $isInitialLoad)")

                if (inGroup && sharingEnabled) {
                    startLocationTrackingService(interval, sharingEnabled)
                    group?.groupID?.let { groupId ->
                        member?.userId?.let { currentUserId ->
                            listenForOtherGroupMembers(groupId, currentUserId)
                            markerMonitorService.startMonitoringMarkers(groupId) // NEW: Start marker monitoring
                        }
                    }
                } else {
                    stopLocationTrackingService()
                    stopOtherMembersListeners()
                    markerMonitorService.stopMonitoringMarkers() // NEW: Stop marker monitoring
                }
            }.collect { /* collect to keep the flow active */ }
        }
    }

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
                    _isInitialGroupMembershipLoaded.value = true // Set true even on error/no data
                    return@addSnapshotListener
                }

                if (snapshots != null && !snapshots.isEmpty) {
                    val allMemberships = snapshots.documents.mapNotNull { doc ->
                        doc.toObject(GroupMembers::class.java)?.copy(id = doc.id)
                    }
                    val activeMemberships = allMemberships.filter { it.unjoinedTimestamp == null }

                    var primaryActiveMember: GroupMembers? = null
                    val currentUserProfile = runBlocking(Dispatchers.IO) { // Consider async alternative for production
                        firestoreService.getUserProfile(userId).getOrNull()
                    }
                    val selectedActiveGroupId = currentUserProfile?.selectedActiveGroupId

                    if (selectedActiveGroupId != null) {
                        primaryActiveMember = activeMemberships.find { it.groupId == selectedActiveGroupId }
                        if (primaryActiveMember == null) {
                            Log.d("GroupMonitorService", "Selected active group ($selectedActiveGroupId) not found or not active. Falling back to most recent.")
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
                        // Pass a callback to listenForActiveGroupDetails to set the flag after its first update
                        listenForActiveGroupDetails(primaryActiveMember.groupId) {
                            _isInitialGroupMembershipLoaded.value = true // Set true after group details are loaded
                        }
                    } else {
                        Log.d("GroupMonitorService", "No active group memberships found for current user.")
                        _activeGroupMember.value = null
                        _activeGroup.value = null
                        _isInActiveGroup.value = false
                        stopOtherMembersListeners()
                        _isInitialGroupMembershipLoaded.value = true // Set true if no active membership
                    }
                } else {
                    Log.d("GroupMonitorService", "No group memberships found for current user.")
                    _activeGroupMember.value = null
                    _activeGroup.value = null
                    _isInActiveGroup.value = false
                    stopOtherMembersListeners()
                    _isInitialGroupMembershipLoaded.value = true // Set true if no memberships at all
                }
            }
    }

    // Modified to accept a callback for when initial data is loaded
    private fun listenForActiveGroupDetails(groupId: String, onInitialLoadComplete: (() -> Unit)? = null) {
        activeGroupDetailsListener?.remove()

        activeGroupDetailsListener = db.collection("groups").document(groupId)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.w("GroupMonitorService", "Listen for active group details failed.", e)
                    _activeGroup.value = null
                    onInitialLoadComplete?.invoke() // Call callback on error too
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
                onInitialLoadComplete?.invoke() // Call callback after successful load
            }
    }

    private fun listenForOtherGroupMembers(groupId: String, currentUserId: String) {
        otherGroupMembersListener?.remove()

        if (groupId.isEmpty()) { // Added check for empty groupId
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

    private fun listenForOtherMembersLocations(userIds: List<String>) {
        otherMembersLocationsListener?.remove()

        if (userIds.isEmpty()) {
            _otherMembersLocations.value = emptyList()
            return
        }

        // Firestore 'whereIn' clause limit is 10, so chunk the userIds
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


    // --- RE-ADDED: startLocationTrackingService and stopLocationTrackingService ---
    // These methods are essential for managing the Android Foreground Service.
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

    private fun stopLocationTrackingService() {
        val serviceIntent = Intent(context, LocationTrackingService::class.java).apply {
            action = LocationTrackingService.ACTION_STOP_SERVICE
        }
        context.stopService(serviceIntent)
        Log.d("GroupMonitorService", "Requested LocationTrackingService STOP.")
    }

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

    fun shutdown() {
        stopAllListeners()
        monitorScope.cancel()
        Log.d("GroupMonitorService", "GroupMonitorService shutdown.")
    }

    fun clearUserMessage() {
        _userMessage.value = null
    }
}
