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
import com.artificialinsightsllc.teamsync.TeamSyncApplication // Import TeamSyncApplication

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
    private var currentUserProfileListener: ListenerRegistration? = null
    private var activeGroupDetailsListener: ListenerRegistration? = null
    private var activeGroupUsersLocationsListener: ListenerRegistration? = null // Corrected name for scalable location queries
    private var otherMembersProfilesListeners: MutableMap<String, ListenerRegistration> = mutableMapOf()

    private val _uiPermissionsGranted = MutableStateFlow(false)
    val uiPermissionsGranted: StateFlow<Boolean> = _uiPermissionsGranted.asStateFlow()

    private var locationTrackingIntentJob: Job? = null

    // Variable to store the last processed active group ID to prevent redundant listener re-attachments
    private var lastProcessedActiveGroupId: String? = null
    // New variable to track the actively monitored user IDs for profiles (for cleanup)
    private var activelyMonitoredProfileUserIds: Set<String> = emptySet()


    init {
        Log.d("GroupMonitorService", "GroupMonitorService initialized. Awaiting explicit startMonitoring call.")
    }

    /**
     * Public method to set the permission status from the UI (MainScreen or PreCheckScreen).
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
        activelyMonitoredProfileUserIds = emptySet() // Reset this set
        Log.d("GroupMonitorService", "Stopped other members' profile listeners and reset activelyMonitoredProfileUserIds.")
    }

    private fun stopActiveGroupUsersLocationsListener() { // Renamed from stopOtherMembersLocationsListener
        activeGroupUsersLocationsListener?.remove()
        activeGroupUsersLocationsListener = null
        _otherMembersLocations.value = emptyList()
        Log.d("GroupMonitorService", "Stopped active group users' locations listener.")
    }

    private fun stopMapRelatedListeners() { // NEW: A consolidated function to stop all map-related listeners
        stopActiveGroupUsersLocationsListener() // Stops the main map location listener
        stopOtherMembersProfilesListeners() // Stops individual profile listeners for map markers
        markerMonitorService.stopMonitoringMarkers() // Stops map marker listener
        Log.d("GroupMonitorService", "Stopped all map-related listeners.")
    }

    fun stopAllListeners() {
        // Stop all top-level listeners
        currentUserGroupMembershipsListener?.remove()
        currentUserGroupMembershipsListener = null
        currentUserProfileListener?.remove()
        currentUserProfileListener = null
        activeGroupDetailsListener?.remove()
        activeGroupDetailsListener = null

        // Stop all map-related data listeners for other members
        stopMapRelatedListeners()

        Log.d("GroupMonitorService", "Stopped all Firestore listeners.")

        // Reset all relevant StateFlows to their initial/empty state
        _groupDetailsStatus.value = GroupDetailsStatus.LOADING
        _activeGroup.value = null
        _activeGroupMember.value = null
        _isInActiveGroup.value = false
        _otherGroupMembers.value = emptyList() // Ensure TeamList data is also cleared
        _otherMembersLocations.value = emptyList() // Ensure map locations are cleared
        _otherMembersProfiles.value = emptyMap() // Ensure map profiles are cleared
        _effectiveLocationUpdateInterval.value = 300000L // Reset to default

        lastProcessedActiveGroupId = null // Reset this on full stop
        locationTrackingIntentJob?.cancel() // Cancel any pending intent dispatches

        // Ensure LocationTrackingService is stopped completely
        sendLocationTrackingIntentDebounced(0L, false, true, null, null)
    }

    /**
     * Debounces sending intents to LocationTrackingService to avoid excessive calls.
     */
    private fun sendLocationTrackingIntentDebounced(
        interval: Long,
        isSharing: Boolean,
        stopServiceCompletely: Boolean,
        memberId: String?,
        activeTrackingGroupId: String?
    ) {
        locationTrackingIntentJob?.cancel() // Cancel any previous pending intent
        locationTrackingIntentJob = monitorScope.launch {
            delay(100) // Small delay to coalesce rapid state changes
            sendLocationTrackingIntent(interval, isSharing, stopServiceCompletely, memberId, activeTrackingGroupId)
        }
    }

    /**
     * Sends the actual intent to LocationTrackingService.
     * @param interval Desired location update interval.
     * @param isSharing True if location should be actively shared.
     * @param stopServiceCompletely If true, sends ACTION_STOP_SERVICE, otherwise ACTION_UPDATE_TRACKING_STATE.
     * @param memberId The Firestore document ID of the active GroupMembers record.
     * @param activeTrackingGroupId The ID of the group the user is currently actively tracking for.
     */
    private fun sendLocationTrackingIntent(
        interval: Long,
        isSharing: Boolean,
        stopServiceCompletely: Boolean,
        memberId: String?,
        activeTrackingGroupId: String?
    ) {
        val intent = Intent(context, LocationTrackingService::class.java).apply {
            action = if (stopServiceCompletely) LocationTrackingService.ACTION_STOP_SERVICE else LocationTrackingService.ACTION_UPDATE_TRACKING_STATE
            putExtra(LocationTrackingService.EXTRA_LOCATION_INTERVAL, interval)
            putExtra(LocationTrackingService.EXTRA_IS_SHARING_LOCATION, isSharing)
            putExtra(LocationTrackingService.EXTRA_ACTIVE_GROUP_MEMBER_ID, memberId)
            putExtra(LocationTrackingService.EXTRA_ACTIVE_TRACKING_GROUP_ID, activeTrackingGroupId)
        }
        if (stopServiceCompletely) {
            context.stopService(intent)
            Log.d("GroupMonitorService", "Explicitly sending ACTION_STOP_SERVICE to LocationTrackingService.")
        } else {
            context.startService(intent)
            Log.d("GroupMonitorService", "Sending ACTION_UPDATE_TRACKING_STATE to LocationTrackingService: isSharing=$isSharing, interval=$interval, memberId=$memberId, activeTrackingGroupId=$activeTrackingGroupId.")
        }
    }

    /**
     * Starts the main monitoring process for user's group memberships and location tracking.
     * This should be called once from `TeamSyncApplication.onCreate()`.
     */
    fun startMonitoring() {
        // Ensure the AuthStateListener is added only once.
        // It's the primary entry point for managing listeners based on auth state changes.
        if (currentUserProfileListener == null) { // This check prevents adding the listener multiple times if startMonitoring is called more than once (e.g., from multiple Activities)
            auth.addAuthStateListener { firebaseAuth ->
                val user = firebaseAuth.currentUser
                if (user != null) {
                    Log.d("GroupMonitorService", "Auth State Listener: User logged in: ${user.uid}. Starting primary data listeners.")
                    monitorScope.launch {
                        // These two calls are the main drivers for managing group state and tracking:
                        listenForCurrentUserProfile(user.uid) // This drives the active group logic and map listeners
                        listenForCurrentUserGroupMemberships(user.uid) // This provides the full list of user's memberships for other screens
                    }
                } else {
                    Log.d("GroupMonitorService", "Auth State Listener: User logged out. Stopping all monitoring.")
                    stopAllListeners() // This will also stop LocationTrackingService
                    _userMessage.value = "You have been logged out."
                }
            }
            // Trigger an immediate evaluation if a user is already logged in when startMonitoring is called.
            // This handles cases where `onAuthStateChanged` might not fire immediately for already active sessions.
            val initialUser = auth.currentUser
            if (initialUser != null) {
                Log.d("GroupMonitorService", "startMonitoring: User already logged in on initial call: ${initialUser.uid}. Initiating primary listeners directly.")
                monitorScope.launch {
                    listenForCurrentUserProfile(initialUser.uid)
                    listenForCurrentUserGroupMemberships(initialUser.uid)
                }
            } else {
                Log.d("GroupMonitorService", "startMonitoring: No user logged in on initial call. Waiting for AuthStateListener.")
            }
        } else {
            Log.d("GroupMonitorService", "startMonitoring: AuthStateListener already active. Skipping re-attachment.")
        }


        // This 'combine' block is responsible for deciding WHEN LocationTrackingService should run
        // and with what parameters, based on current app state and permissions.
        monitorScope.launch {
            val app = context.applicationContext as TeamSyncApplication

            var lastDesiredServiceRunningState: Boolean? = null
            var lastDesiredInterval: Long? = null
            var lastDesiredMemberId: String? = null
            var lastDesiredActiveTrackingGroupId: String? = null

            combine(
                _activeGroupMember, // Driven by selectedActiveGroupId from user profile
                _activeGroup,       // Driven by group details listener
                _groupDetailsStatus, // Driven by group details listener
                _uiPermissionsGranted, // Driven by UI (permissions check)
                app.locationServiceRunningState // Actual state of LocationTrackingService
            ) { member, group, detailsStatus, uiPermissionsGranted, actualServiceStateTriple ->
                val actualServiceIsRunning = actualServiceStateTriple.first
                val actualTrackingInterval = actualServiceStateTriple.second
                val actualTrackingMemberId = actualServiceStateTriple.third

                // We don't need currentUserId here, as it's passed to relevant functions
                // val currentUserId = auth.currentUser?.uid

                val memberIsActive = member != null && member.unjoinedTimestamp == null
                val groupIsValidAndActive = group != null && (group.groupEndTimestamp?.let { it > System.currentTimeMillis() } ?: true)

                Log.d("GroupMonitorService", "Combine Evaluation: Member=${member?.id}, Group=${group?.groupID}, Status=$detailsStatus, UI Perms=$uiPermissionsGranted, ActualServiceRunning=$actualServiceIsRunning, ActualInterval=$actualTrackingInterval, ActualMemberId=$actualTrackingMemberId")


                val desiredServiceRunning: Boolean
                val desiredInterval: Long
                val desiredMemberId: String?
                val desiredActiveTrackingGroupId: String?

                // Determine desired state based on active member, group status, and permissions
                if (memberIsActive && groupIsValidAndActive && uiPermissionsGranted) {
                    val nonNullMember = member!!
                    val nonNullGroup = group!!
                    desiredServiceRunning = true
                    desiredInterval = nonNullMember.personalLocationUpdateIntervalMillis
                        ?: nonNullGroup.locationUpdateIntervalMillis
                                ?: 300000L // Fallback default
                    desiredMemberId = nonNullMember.id
                    desiredActiveTrackingGroupId = nonNullGroup.groupID // User is actively tracking for THIS group

                    _isInActiveGroup.value = true // Indicate active group is present for UI
                    // Map-related listeners (other members locations/profiles, map markers)
                    // are now started/stopped by listenForCurrentUserProfile when it sets _activeGroup.
                    // This combine flow only controls the LocationTrackingService.
                    Log.d("GroupMonitorService", "Group ACTIVE & member active & perms granted. Desired service state: RUNNING, interval: $desiredInterval, activeTrackingGroupId: $desiredActiveTrackingGroupId.")
                } else {
                    desiredServiceRunning = false // Conditions not met for active tracking
                    desiredInterval = 0L
                    desiredMemberId = null
                    desiredActiveTrackingGroupId = null // Not actively tracking for any group

                    _isInActiveGroup.value = false // Indicate no active group for tracking purposes
                    // Map-related listeners are now stopped by listenForCurrentUserProfile when it clears _activeGroup.
                    Log.d("GroupMonitorService", "Conditions for active tracking NOT met. Desired service state: STOPPED.")
                }

                _effectiveLocationUpdateInterval.value = desiredInterval // Update internal flow for UI

                // Now, dispatch intent ONLY if desired state differs from actual state
                // This logic ensures we don't send redundant start/stop/update commands
                val shouldSendStartOrUpdateIntent = desiredServiceRunning &&
                        (!actualServiceIsRunning ||
                                desiredInterval != lastDesiredInterval ||
                                desiredMemberId != lastDesiredMemberId ||
                                desiredActiveTrackingGroupId != lastDesiredActiveTrackingGroupId)

                val shouldSendStopIntent = !desiredServiceRunning && actualServiceIsRunning

                if (shouldSendStartOrUpdateIntent) {
                    sendLocationTrackingIntentDebounced(desiredInterval, true, false, desiredMemberId, desiredActiveTrackingGroupId)
                    lastDesiredServiceRunningState = desiredServiceRunning
                    lastDesiredInterval = desiredInterval
                    lastDesiredMemberId = desiredMemberId
                    lastDesiredActiveTrackingGroupId = desiredActiveTrackingGroupId
                } else if (shouldSendStopIntent) {
                    sendLocationTrackingIntentDebounced(0L, false, true, null, null)
                    lastDesiredServiceRunningState = desiredServiceRunning
                    lastDesiredInterval = desiredInterval
                    lastDesiredMemberId = desiredMemberId
                    lastDesiredActiveTrackingGroupId = null
                } else {
                    Log.d("GroupMonitorService", "No change in desired service state or interval. Skipping intent dispatch. Current desired: Running=$desiredServiceRunning, Interval=$desiredInterval, Member=$desiredMemberId, TrackingGroup=$desiredActiveTrackingGroupId. Actual: Running=$actualServiceIsRunning, Interval=$actualTrackingInterval, Member=$actualTrackingMemberId")
                }

            }.collect { /* collect to keep the flow active */ }
        }
    }


    /**
     * Listens for changes to the current user's UserModel document, primarily for `selectedActiveGroupId`.
     * This is the authoritative source for the user's actively selected group.
     */
    private fun listenForCurrentUserProfile(userId: String) {
        currentUserProfileListener?.remove() // Remove any old listener

        Log.d("GroupMonitorService", "listenForCurrentUserProfile: Attaching listener for user profile $userId.")
        currentUserProfileListener = db.collection("users").document(userId)
            .addSnapshotListener { snapshot, e ->
                Log.d("GroupMonitorService", "listenForCurrentUserProfile: Snapshot received for user profile $userId. Exists: ${snapshot?.exists()}, Error: ${e?.message}")
                if (e != null) {
                    Log.w("GroupMonitorService", "Listen for user profile $userId failed.", e)
                    // On error, assume no active group for tracking
                    val oldStatus = _groupDetailsStatus.value
                    if (oldStatus != GroupDetailsStatus.ERROR) {
                        _groupDetailsStatus.value = GroupDetailsStatus.ERROR
                        Log.d("GroupMonitorService", "Group details status changed: $oldStatus -> ERROR due to user profile listener error.")
                    }
                    // Explicitly clear all active group state if there's a listener error
                    Log.d("GroupMonitorService", "Clearing active group state due to user profile listener error.")
                    _activeGroupMember.value = null
                    _activeGroup.value = null
                    _isInActiveGroup.value = false
                    stopMapRelatedListeners() // Stops all map-related data listeners
                    lastProcessedActiveGroupId = null // Ensure this is reset
                    activeGroupDetailsListener?.remove()
                    activeGroupDetailsListener = null
                    _otherMembersLocations.value = emptyList() // Clear map-related data
                    _otherMembersProfiles.value = emptyMap()
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    val userProfile = snapshot.toObject(UserModel::class.java)
                    val newSelectedActiveGroupId = userProfile?.selectedActiveGroupId
                    Log.d("GroupMonitorService", "User profile updated. New selectedActiveGroupId: $newSelectedActiveGroupId, Last processed: $lastProcessedActiveGroupId")

                    // ONLY re-evaluate active group state if the selectedActiveGroupId has truly changed
                    // or if the current active group has expired/been deleted (checked via _groupDetailsStatus)
                    val currentActiveGroupIdInState = _activeGroup.value?.groupID // Current group actively being displayed on map
                    val shouldReevaluateBecauseGroupExpiredOrDeleted = (newSelectedActiveGroupId == currentActiveGroupIdInState) &&
                            (_groupDetailsStatus.value == GroupDetailsStatus.EXPIRED || _groupDetailsStatus.value == GroupDetailsStatus.NOT_FOUND)

                    if (newSelectedActiveGroupId != lastProcessedActiveGroupId || shouldReevaluateBecauseGroupExpiredOrDeleted) {
                        Log.d("GroupMonitorService", "Selected active group ID changed or current group expired/deleted: $lastProcessedActiveGroupId -> $newSelectedActiveGroupId. Re-evaluating active group for tracking. Re-eval reason: shouldReevaluateBecauseGroupExpiredOrDeleted=$shouldReevaluateBecauseGroupExpiredOrDeleted")
                        lastProcessedActiveGroupId = newSelectedActiveGroupId // Update last processed ID

                        if (newSelectedActiveGroupId != null && newSelectedActiveGroupId.isNotBlank()) {
                            // User has selected an active group, proceed to find membership and details
                            monitorScope.launch {
                                val membershipsResult = firestoreService.getGroupMembershipsForUser(userId).getOrNull() ?: emptyList()
                                val selectedMembership = membershipsResult.find {
                                    it.groupId == newSelectedActiveGroupId && it.unjoinedTimestamp == null
                                }

                                if (selectedMembership != null) {
                                    Log.d("GroupMonitorService", "Found active membership for selected group: ${selectedMembership.groupId}. Setting as primary active.")
                                    _activeGroupMember.value = selectedMembership
                                    _groupDetailsStatus.value = GroupDetailsStatus.LOADING // Set to LOADING state for group details fetch
                                    listenForActiveGroupDetails(selectedMembership.groupId) // Listen for group details
                                    listenForActiveGroupUsersLocations(selectedMembership.groupId, userId) // Start scalable location listener
                                    markerMonitorService.startMonitoringMarkers(selectedMembership.groupId) // Start map marker listener
                                } else {
                                    // Selected group ID exists in profile, but no active membership found for it.
                                    // This means the user is no longer an active member of that specifically selected group.
                                    Log.d("GroupMonitorService", "Selected active group ($newSelectedActiveGroupId) has no active membership for user $userId. Clearing active group state.")
                                    _activeGroupMember.value = null
                                    _activeGroup.value = null
                                    _isInActiveGroup.value = false
                                    stopMapRelatedListeners() // Stops all map-related data listeners
                                    val oldStatus = _groupDetailsStatus.value
                                    if (oldStatus != GroupDetailsStatus.NOT_FOUND) {
                                        _groupDetailsStatus.value = GroupDetailsStatus.NOT_FOUND
                                        Log.d("GroupMonitorService", "Group details status changed: $oldStatus -> NOT_FOUND (selected group inactive).")
                                    }
                                    activeGroupDetailsListener?.remove()
                                    activeGroupDetailsListener = null
                                    _otherMembersLocations.value = emptyList() // Clear map-related data
                                    _otherMembersProfiles.value = emptyMap()
                                    // Clear selectedActiveGroupId in user profile in Firestore
                                    monitorScope.launch {
                                        firestoreService.updateUserSelectedActiveGroup(userId, null)
                                    }
                                }
                            }
                        } else {
                            // newSelectedActiveGroupId is null or blank - user explicitly wants no active group.
                            Log.d("GroupMonitorService", "New selected active group ID is null/blank. Clearing active group state for tracking.")
                            _activeGroupMember.value = null
                            _activeGroup.value = null
                            _isInActiveGroup.value = false
                            stopMapRelatedListeners() // Stops all map-related data listeners
                            val oldStatus = _groupDetailsStatus.value
                            if (oldStatus != GroupDetailsStatus.NOT_FOUND) {
                                _groupDetailsStatus.value = GroupDetailsStatus.NOT_FOUND
                                Log.d("GroupMonitorService", "Group details status changed: $oldStatus -> NOT_FOUND (explicitly no active group).")
                            }
                            activeGroupDetailsListener?.remove()
                            activeGroupDetailsListener = null
                            _otherMembersLocations.value = emptyList() // Clear map-related data
                            _otherMembersProfiles.value = emptyMap()
                        }
                    } else {
                        Log.d("GroupMonitorService", "Selected active group ID unchanged ($newSelectedActiveGroupId). No re-evaluation of active group state needed.")
                        // If selected ID is unchanged, and it's not null, ensure the group details listener is still active
                        // This handles cases where the group itself might expire or be deleted while selected.
                        if (newSelectedActiveGroupId != null && newSelectedActiveGroupId.isNotBlank()) {
                            // If _activeGroup or _groupDetailsStatus implies a problem, re-trigger
                            if (_activeGroup.value == null || _groupDetailsStatus.value == GroupDetailsStatus.NOT_FOUND || _groupDetailsStatus.value == GroupDetailsStatus.EXPIRED) {
                                Log.d("GroupMonitorService", "Active group document might be missing or expired, re-attaching listeners for $newSelectedActiveGroupId.")
                                _groupDetailsStatus.value = GroupDetailsStatus.LOADING
                                listenForActiveGroupDetails(newSelectedActiveGroupId)
                                listenForActiveGroupUsersLocations(newSelectedActiveGroupId, userId)
                                markerMonitorService.startMonitoringMarkers(newSelectedActiveGroupId)
                            } else {
                                Log.d("GroupMonitorService", "Active group ($newSelectedActiveGroupId) appears healthy. Skipping listener re-attachment.")
                            }
                        } else {
                            // If newSelectedActiveGroupId is null, and it's unchanged (meaning it was already null),
                            // ensure everything is in the NOT_FOUND/stopped state.
                            val oldStatus = _groupDetailsStatus.value
                            if (oldStatus != GroupDetailsStatus.NOT_FOUND) {
                                _groupDetailsStatus.value = GroupDetailsStatus.NOT_FOUND
                                Log.d("GroupMonitorService", "Group details status changed: $oldStatus -> NOT_FOUND (explicitly no active group).")
                            }
                            _activeGroupMember.value = null
                            _activeGroup.value = null
                            _isInActiveGroup.value = false
                            stopMapRelatedListeners()
                            activeGroupDetailsListener?.remove()
                            activeGroupDetailsListener = null
                            _otherMembersLocations.value = emptyList() // Clear map-related data
                            _otherMembersProfiles.value = emptyMap()
                        }
                    }
                } else {
                    // User profile document does not exist, treat as no active group.
                    Log.d("GroupMonitorService", "User profile document not found. Clearing active group state.")
                    _activeGroupMember.value = null
                    _activeGroup.value = null
                    _isInActiveGroup.value = false
                    stopMapRelatedListeners()
                    val oldStatus = _groupDetailsStatus.value
                    if (oldStatus != GroupDetailsStatus.NOT_FOUND) {
                        _groupDetailsStatus.value = GroupDetailsStatus.NOT_FOUND
                        Log.d("GroupMonitorService", "Group details status changed: $oldStatus -> NOT_FOUND (profile not found).")
                    }
                    activeGroupDetailsListener?.remove()
                    activeGroupDetailsListener = null
                    _otherMembersLocations.value = emptyList() // Clear map-related data
                    _otherMembersProfiles.value = emptyMap()
                }
            }
    }

    /**
     * Listens for changes to the current user's group memberships from Firestore.
     * This listener updates the list of all active/inactive memberships a user is part of,
     * primarily for screens like `TeamListScreen` and `GroupsListScreen`.
     * It does *not* directly drive the "active group for tracking" logic on the map.
     */
    private fun listenForCurrentUserGroupMemberships(userId: String) {
        // Ensure this listener is only attached once or if userId changes
        if (currentUserGroupMembershipsListener == null || (auth.currentUser?.uid != null && currentUserGroupMembershipsListener?.equals(userId) == false)) {
            currentUserGroupMembershipsListener?.remove() // Remove old listener if exists for a different user
            Log.d("GroupMonitorService", "listenForCurrentUserGroupMemberships: Attaching listener for user $userId's group memberships (for TeamList/GroupsList Data).")

            currentUserGroupMembershipsListener = db.collection("groupMembers")
                .whereEqualTo("userId", userId)
                .addSnapshotListener { snapshots, e ->
                    Log.d("GroupMonitorService", "listenForCurrentUserGroupMemberships (Secondary Listener): Listener triggered for user $userId. Error: ${e?.message}, Snapshots: ${snapshots?.size()}")
                    if (e != null) {
                        Log.w("GroupMonitorService", "Secondary listen for current user's group memberships failed.", e)
                        _otherGroupMembers.value = emptyList() // Clear list on error
                        return@addSnapshotListener
                    }

                    if (snapshots != null) {
                        val allMemberships = snapshots.documents.mapNotNull { doc ->
                            doc.toObject(GroupMembers::class.java)?.copy(id = doc.id)
                        }
                        val activeMembershipsFromAll = allMemberships.filter { it.unjoinedTimestamp == null }

                        // Update _otherGroupMembers for TeamListScreen etc.
                        _otherGroupMembers.value = activeMembershipsFromAll
                        Log.d("GroupMonitorService", "Updated _otherGroupMembers with ${activeMembershipsFromAll.size} active memberships for TeamList/GroupsList.")
                    }
                }
        }
    }


    /**
     * Listens for details of the currently active group.
     */
    private fun listenForActiveGroupDetails(groupId: String) {
        // Only remove and re-attach if the groupId actually changes or listener is null
        if (activeGroupDetailsListener == null || (activeGroup.value?.groupID != groupId)) { // Use activeGroup.value.groupID for comparison
            activeGroupDetailsListener?.remove() // Remove old listener if exists for a different group
            Log.d("GroupMonitorService", "listenForActiveGroupDetails: Attaching listener for group $groupId.")
            activeGroupDetailsListener = db.collection("groups").document(groupId)
                .addSnapshotListener { snapshot, e ->
                    Log.d("GroupMonitorService", "listenForActiveGroupDetails: Snapshot received for group $groupId. Exists: ${snapshot?.exists()}, Error: ${e?.message}")
                    if (e != null) {
                        Log.w("GroupMonitorService", "Listen for active group details failed for $groupId.", e)
                        _activeGroup.value = null
                        val oldStatus = _groupDetailsStatus.value
                        if (oldStatus != GroupDetailsStatus.ERROR) {
                            _groupDetailsStatus.value = GroupDetailsStatus.ERROR
                            Log.d("GroupMonitorService", "Group details status changed: $oldStatus -> ERROR")
                        }
                        // Explicitly clear selected active group in user profile if there's a group details error
                        val currentUserId = auth.currentUser?.uid
                        if (currentUserId != null) {
                            monitorScope.launch {
                                firestoreService.updateUserSelectedActiveGroup(currentUserId, null)
                            }
                        }
                        return@addSnapshotListener
                    }

                    if (snapshot != null && snapshot.exists()) {
                        val group = snapshot.toObject(Groups::class.java)
                        if (group != null) {
                            Log.d("GroupMonitorService", "Active group details updated: ${group.groupName}")
                            _activeGroup.value = group
                            val newStatus = if (group.groupEndTimestamp?.let { it > System.currentTimeMillis() } ?: true) {
                                GroupDetailsStatus.ACTIVE
                            } else {
                                GroupDetailsStatus.EXPIRED
                            }
                            val oldStatus = _groupDetailsStatus.value
                            if (oldStatus != newStatus) {
                                _groupDetailsStatus.value = newStatus
                                Log.d("GroupMonitorService", "Group details status changed: $oldStatus -> $newStatus")
                                if (newStatus == GroupDetailsStatus.EXPIRED) {
                                    _userMessage.value = "Group '${group.groupName}' has expired. You have been automatically removed."
                                    // Also clear active group in user profile if it expires
                                    val currentUserId = auth.currentUser?.uid
                                    if (currentUserId != null) {
                                        monitorScope.launch {
                                            firestoreService.updateUserSelectedActiveGroup(currentUserId, null)
                                        }
                                    }
                                }
                            }
                        } else {
                            Log.d("GroupMonitorService", "Active group data for $groupId is null or malformed. Treating as NOT_FOUND.")
                            _activeGroup.value = null
                            val oldStatus = _groupDetailsStatus.value
                            if (oldStatus != GroupDetailsStatus.NOT_FOUND) {
                                _groupDetailsStatus.value = GroupDetailsStatus.NOT_FOUND
                                Log.d("GroupMonitorService", "Group details status changed: $oldStatus -> NOT_FOUND")
                            }
                            // Also clear active group in user profile if the group data is malformed
                            val currentUserId = auth.currentUser?.uid
                            if (currentUserId != null) {
                                monitorScope.launch {
                                    firestoreService.updateUserSelectedActiveGroup(currentUserId, null)
                                }
                            }
                        }
                    } else {
                        Log.d("GroupMonitorService", "Active group document $groupId no longer exists in Firestore. Triggering cleanup.")
                        _activeGroup.value = null
                        val oldStatus = _groupDetailsStatus.value
                        if (oldStatus != GroupDetailsStatus.NOT_FOUND) {
                            _groupDetailsStatus.value = GroupDetailsStatus.NOT_FOUND
                            Log.d("GroupMonitorService", "Group details status changed: $oldStatus -> NOT_FOUND")
                        }
                        // Also clear active group in user profile if the group is deleted
                        val currentUserId = auth.currentUser?.uid
                        if (currentUserId != null) {
                            monitorScope.launch {
                                firestoreService.updateUserSelectedActiveGroup(currentUserId, null)
                            }
                        }
                    }
                }
        } else {
            Log.d("GroupMonitorService", "listenForActiveGroupDetails: Listener for group $groupId is already active. Skipping re-attachment.")
        }
    }

    /**
     * NEW: Listens for the current location of users who are actively tracking for the specified group.
     * This uses the scalable `whereEqualTo("activeTrackingGroupId", groupId)` query.
     */
    private fun listenForActiveGroupUsersLocations(groupId: String, currentUserId: String) {
        activeGroupUsersLocationsListener?.remove() // Remove any old listener

        Log.d("GroupMonitorService", "listenForActiveGroupUsersLocations: Attaching listener for active group users in group: $groupId.")

        if (groupId.isEmpty()) {
            _otherMembersLocations.value = emptyList()
            _otherMembersProfiles.value = emptyMap()
            stopOtherMembersProfilesListeners() // Clear associated profiles
            Log.d("GroupMonitorService", "listenForActiveGroupUsersLocations: Group ID is empty, skipping listener attachment.")
            return
        }

        activeGroupUsersLocationsListener = db.collection("current_user_locations")
            .whereEqualTo("activeTrackingGroupId", groupId) // Scalable query!
            .addSnapshotListener { snapshots, e ->
                Log.d("GroupMonitorService", "listenForActiveGroupUsersLocations: Snapshot received for group $groupId. Error: ${e?.message}, Snapshots: ${snapshots?.size()}")
                if (e != null) {
                    Log.w("GroupMonitorService", "Listen for active group users' locations failed.", e)
                    _otherMembersLocations.value = emptyList()
                    _otherMembersProfiles.value = emptyMap()
                    stopOtherMembersProfilesListeners() // Ensure profile listeners are stopped on error
                    return@addSnapshotListener
                }

                if (snapshots != null) {
                    val activeLocations = snapshots.documents.mapNotNull { doc ->
                        doc.toObject(Locations::class.java)
                    }.filter {
                        // Filter out current user's location
                        it.userId != currentUserId
                    }
                    _otherMembersLocations.value = activeLocations
                    Log.d("GroupMonitorService", "Updated ${activeLocations.size} other active group members' locations from current_user_locations.")

                    val newProfileUserIds = activeLocations.map { it.userId }.distinct()
                    // Re-attach profile listeners only for users whose locations are currently being displayed.
                    listenForOtherMembersProfiles(newProfileUserIds)
                } else {
                    _otherMembersLocations.value = emptyList()
                    _otherMembersProfiles.value = emptyMap()
                    stopOtherMembersProfilesListeners()
                }
            }
    }


    /**
     * Listens for profile updates of specified other members.
     * This function is now called by `listenForActiveGroupUsersLocations` to ensure
     * we only fetch profiles for users whose locations are actively being tracked for the current group.
     */
    private fun listenForOtherMembersProfiles(userIds: List<String>) {
        // Only update listeners if the set of user IDs has actually changed.
        val newUserIdsSet = userIds.distinct().toSet()
        if (newUserIdsSet == activelyMonitoredProfileUserIds) {
            Log.d("GroupMonitorService", "listenForOtherMembersProfiles: Monitored profile user IDs are unchanged. Skipping listener re-attachment.")
            return // No change, do nothing
        }

        stopOtherMembersProfilesListeners() // Stop previous listeners before setting up new ones
        activelyMonitoredProfileUserIds = newUserIdsSet // Update the set of actively monitored IDs

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
