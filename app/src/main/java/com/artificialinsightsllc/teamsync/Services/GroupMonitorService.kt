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
    // NEW: Listener for the user's profile document (for selectedActiveGroupId)
    private var currentUserProfileListener: ListenerRegistration? = null
    private var activeGroupDetailsListener: ListenerRegistration? = null
    private var otherGroupMembersListener: ListenerRegistration? = null
    private var otherMembersLocationsListener: ListenerRegistration? = null
    private var otherMembersProfilesListeners: MutableMap<String, ListenerRegistration> = mutableMapOf()

    private val _uiPermissionsGranted = MutableStateFlow(false)
    val uiPermissionsGranted: StateFlow<Boolean> = _uiPermissionsGranted.asStateFlow()

    private var locationTrackingIntentJob: Job? = null

    private val _currentlyMonitoredOtherUserIds = MutableStateFlow<Set<String>>(emptySet())

    // Variable to store the last processed active group ID to prevent redundant listener re-attachments
    private var lastProcessedActiveGroupId: String? = null


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
        // Reset currently monitored user IDs when all other listeners are stopped
        _currentlyMonitoredOtherUserIds.value = emptySet()
        Log.d("GroupMonitorService", "Stopped other members' data listeners.")
    }

    fun stopAllListeners() {
        currentUserGroupMembershipsListener?.remove()
        currentUserGroupMembershipsListener = null
        currentUserProfileListener?.remove() // NEW: Stop profile listener on full shutdown
        currentUserProfileListener = null
        activeGroupDetailsListener?.remove()
        activeGroupDetailsListener = null
        stopOtherMembersListeners() // This will also reset _currentlyMonitoredOtherUserIds
        markerMonitorService.stopMonitoringMarkers()
        Log.d("GroupMonitorService", "Stopped all Firestore listeners.")
        _groupDetailsStatus.value = GroupDetailsStatus.LOADING // Reset to LOADING state for cleanliness
        _activeGroup.value = null
        _activeGroupMember.value = null
        _isInActiveGroup.value = false
        lastProcessedActiveGroupId = null // Reset this on full stop
        locationTrackingIntentJob?.cancel() // Cancel any pending intent dispatches
        // Ensure LocationTrackingService is stopped if it's currently running (via ACTION_STOP_SERVICE)
        sendLocationTrackingIntent(0L, false, true, null)
    }

    /**
     * Debounces sending intents to LocationTrackingService to avoid excessive calls.
     */
    private fun sendLocationTrackingIntentDebounced(interval: Long, isSharing: Boolean, stopServiceCompletely: Boolean, memberId: String?) {
        locationTrackingIntentJob?.cancel() // Cancel any previous pending intent
        locationTrackingIntentJob = monitorScope.launch {
            delay(100) // Small delay to coalesce rapid state changes
            sendLocationTrackingIntent(interval, isSharing, stopServiceCompletely, memberId)
        }
    }

    /**
     * Sends the actual intent to LocationTrackingService.
     * @param interval Desired location update interval.
     * @param isSharing True if location should be actively shared.
     * @param stopServiceCompletely If true, sends ACTION_STOP_SERVICE, otherwise ACTION_UPDATE_TRACKING_STATE.
     * @param memberId The Firestore document ID of the active GroupMembers record.
     */
    private fun sendLocationTrackingIntent(interval: Long, isSharing: Boolean, stopServiceCompletely: Boolean, memberId: String?) {
        val intent = Intent(context, LocationTrackingService::class.java).apply {
            action = if (stopServiceCompletely) LocationTrackingService.ACTION_STOP_SERVICE else LocationTrackingService.ACTION_UPDATE_TRACKING_STATE
            putExtra(LocationTrackingService.EXTRA_LOCATION_INTERVAL, interval)
            putExtra(LocationTrackingService.EXTRA_IS_SHARING_LOCATION, isSharing)
            putExtra(LocationTrackingService.EXTRA_ACTIVE_GROUP_MEMBER_ID, memberId)
        }
        if (stopServiceCompletely) {
            context.stopService(intent) // stopService and startService (for FGS) are separate
            Log.d("GroupMonitorService", "Explicitly sending ACTION_STOP_SERVICE to LocationTrackingService.")
        } else {
            // For updates or starting, use startService (which becomes startForegroundService on O+)
            context.startService(intent)
            Log.d("GroupMonitorService", "Sending ACTION_UPDATE_TRACKING_STATE to LocationTrackingService: isSharing=$isSharing, interval=$interval, memberId=$memberId.")
        }
    }

    /**
     * Starts the main monitoring process for user's group memberships and location tracking.
     * This should be called once the user is authenticated and UI permissions are checked.
     * @param initialSelectedGroupId An optional group ID to prioritize as active.
     */
    fun startMonitoring(initialSelectedGroupId: String? = null) {
        // AuthStateListener for dynamic login/logout events (fires when auth state changes)
        if (currentUserGroupMembershipsListener == null) { // Only attach auth listener and initial setup once
            auth.addAuthStateListener { firebaseAuth ->
                val user = firebaseAuth.currentUser
                if (user != null) {
                    Log.d("GroupMonitorService", "Auth State Listener: User logged in: ${user.uid}.")
                    // When auth state changes to logged in, perform the full group membership and profile check
                    monitorScope.launch {
                        listenForCurrentUserProfile(user.uid) // This will drive the whole process
                        listenForCurrentUserGroupMemberships(user.uid) // This provides the full list of memberships
                    }
                } else {
                    Log.d("GroupMonitorService", "Auth State Listener: User logged out. Stopping all monitoring.")
                    stopAllListeners() // This will also stop LocationTrackingService
                    _userMessage.value = "You have been logged out."
                }
            }
        }

        // IMPORTANT: Perform an immediate check on startup if user is already logged in.
        // This handles cases where `onAuthStateChanged` might not fire immediately or for initial state.
        val initialUser = auth.currentUser
        if (initialUser != null) {
            Log.d("GroupMonitorService", "startMonitoring: User already logged in on initial call: ${initialUser.uid}. Initiating primary listeners directly.")
            monitorScope.launch {
                listenForCurrentUserProfile(initialUser.uid) // This will drive the whole process
                listenForCurrentUserGroupMemberships(initialUser.uid) // This provides the full list of memberships
            }
        } else {
            Log.d("GroupMonitorService", "startMonitoring: No user logged in on initial call. Waiting for AuthStateListener.")
        }


        // Combine flow to react to changes in active group, group details, and UI permissions
        monitorScope.launch {
            val app = context.applicationContext as TeamSyncApplication

            var lastDesiredServiceRunningState: Boolean? = null
            var lastDesiredInterval: Long? = null
            var lastDesiredMemberId: String? = null

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

                val currentUserId = auth.currentUser?.uid

                val memberIsActive = member != null && member.unjoinedTimestamp == null
                val groupIsValidAndActive = group != null && (group.groupEndTimestamp?.let { it > System.currentTimeMillis() } ?: true)

                Log.d("GroupMonitorService", "Combine Evaluation: Member=${member?.groupId}, Group=${group?.groupID}, Status=$detailsStatus, UI Perms=$uiPermissionsGranted, ActualServiceRunning=$actualServiceIsRunning, ActualInterval=$actualTrackingInterval, ActualMemberId=$actualTrackingMemberId")


                val desiredServiceRunning: Boolean // True if service should be running AND actively tracking location
                val desiredInterval: Long
                val desiredMemberId: String?

                // Determine desired state based on active member, group status, and permissions
                if (memberIsActive && groupIsValidAndActive && uiPermissionsGranted) {
                    val nonNullMember = member!!
                    val nonNullGroup = group!!
                    desiredServiceRunning = true
                    desiredInterval = nonNullMember.personalLocationUpdateIntervalMillis
                        ?: nonNullGroup.locationUpdateIntervalMillis
                                ?: 300000L // Fallback default
                    desiredMemberId = nonNullMember.id

                    _isInActiveGroup.value = true // Indicate active group is present for UI
                    listenForOtherGroupMembers(nonNullGroup.groupID, nonNullMember.userId) // Monitor other members in THIS active group
                    markerMonitorService.startMonitoringMarkers(nonNullGroup.groupID) // Monitor markers for THIS active group
                    Log.d("GroupMonitorService", "Group ACTIVE & member active & perms granted. Desired service state: RUNNING, interval: $desiredInterval.")
                } else {
                    desiredServiceRunning = false // Conditions not met for active tracking
                    desiredInterval = 0L
                    desiredMemberId = null
                    _isInActiveGroup.value = false // Indicate no active group for tracking purposes
                    stopOtherMembersListeners() // Stop monitoring other members (if any)
                    markerMonitorService.stopMonitoringMarkers() // Stop markers (if any)
                    Log.d("GroupMonitorService", "Conditions for active tracking NOT met. Desired service state: STOPPED.")
                }


                // Now, dispatch intent ONLY if desired state differs from actual state
                val shouldSendStartOrUpdateIntent = desiredServiceRunning &&
                        (!actualServiceIsRunning ||
                                desiredInterval != actualTrackingInterval ||
                                desiredMemberId != actualTrackingMemberId)

                val shouldSendStopIntent = !desiredServiceRunning && actualServiceIsRunning

                if (shouldSendStartOrUpdateIntent) {
                    sendLocationTrackingIntentDebounced(desiredInterval, true, false, desiredMemberId) // Start or update
                    lastDesiredServiceRunningState = desiredServiceRunning
                    lastDesiredInterval = desiredInterval
                    lastDesiredMemberId = desiredMemberId
                } else if (shouldSendStopIntent) {
                    sendLocationTrackingIntentDebounced(0L, false, true, null) // Stop completely
                    lastDesiredServiceRunningState = desiredServiceRunning
                    lastDesiredInterval = desiredInterval
                    lastDesiredMemberId = desiredMemberId
                } else {
                    Log.d("GroupMonitorService", "No change in desired service state or interval. Skipping intent dispatch. Current desired: Running=$desiredServiceRunning, Interval=$desiredInterval, Member=$desiredMemberId. Actual: Running=$actualServiceIsRunning, Interval=$actualTrackingInterval, Member=$actualTrackingMemberId")
                }

                _effectiveLocationUpdateInterval.value = desiredInterval // Update internal flow for UI
            }.collect { /* collect to keep the flow active */ }
        }
    }


    /**
     * NEW: Listens for changes to the current user's UserModel document, primarily for `selectedActiveGroupId`.
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
                    stopOtherMembersListeners()
                    lastProcessedActiveGroupId = null
                    activeGroupDetailsListener?.remove()
                    activeGroupDetailsListener = null
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    val userProfile = snapshot.toObject(UserModel::class.java)
                    val newSelectedActiveGroupId = userProfile?.selectedActiveGroupId
                    Log.d("GroupMonitorService", "User profile updated. New selectedActiveGroupId: $newSelectedActiveGroupId, Last processed: $lastProcessedActiveGroupId")

                    // ONLY re-evaluate active group state if the selectedActiveGroupId has truly changed
                    if (newSelectedActiveGroupId != lastProcessedActiveGroupId) {
                        Log.d("GroupMonitorService", "Selected active group ID changed: $lastProcessedActiveGroupId -> $newSelectedActiveGroupId. Re-evaluating active group for tracking.")
                        lastProcessedActiveGroupId = newSelectedActiveGroupId // Update last processed ID

                        if (newSelectedActiveGroupId != null && newSelectedActiveGroupId.isNotBlank()) {
                            // User has selected an active group, proceed to find membership and details
                            // We need to fetch current group memberships to confirm active status
                            monitorScope.launch {
                                val membershipsResult = firestoreService.getGroupMembershipsForUser(userId).getOrNull() ?: emptyList()
                                val selectedMembership = membershipsResult.find {
                                    it.groupId == newSelectedActiveGroupId && it.unjoinedTimestamp == null
                                }

                                if (selectedMembership != null) {
                                    Log.d("GroupMonitorService", "Found active membership for selected group: ${selectedMembership.groupId}. Setting as primary active.")
                                    _activeGroupMember.value = selectedMembership
                                    // Set to LOADING state for group details fetch
                                    _groupDetailsStatus.value = GroupDetailsStatus.LOADING
                                    listenForActiveGroupDetails(selectedMembership.groupId)
                                } else {
                                    // Selected group ID exists in profile, but no active membership found for it.
                                    // This means the user is no longer an active member of that specifically selected group.
                                    Log.d("GroupMonitorService", "Selected active group ($newSelectedActiveGroupId) has no active membership for user $userId. Clearing active group state.")
                                    // Clear active group state and remove selected ID from profile in Firestore
                                    _activeGroupMember.value = null
                                    _activeGroup.value = null
                                    _isInActiveGroup.value = false
                                    stopOtherMembersListeners()
                                    markerMonitorService.stopMonitoringMarkers()
                                    val oldStatus = _groupDetailsStatus.value
                                    if (oldStatus != GroupDetailsStatus.NOT_FOUND) {
                                        _groupDetailsStatus.value = GroupDetailsStatus.NOT_FOUND
                                        Log.d("GroupMonitorService", "Group details status changed: $oldStatus -> NOT_FOUND (selected group inactive).")
                                    }
                                    activeGroupDetailsListener?.remove()
                                    activeGroupDetailsListener = null
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
                            stopOtherMembersListeners()
                            markerMonitorService.stopMonitoringMarkers()
                            val oldStatus = _groupDetailsStatus.value
                            if (oldStatus != GroupDetailsStatus.NOT_FOUND) {
                                _groupDetailsStatus.value = GroupDetailsStatus.NOT_FOUND
                                Log.d("GroupMonitorService", "Group details status changed: $oldStatus -> NOT_FOUND (explicitly no active group).")
                            }
                            activeGroupDetailsListener?.remove()
                            activeGroupDetailsListener = null
                        }
                    } else {
                        Log.d("GroupMonitorService", "Selected active group ID unchanged ($newSelectedActiveGroupId). No re-evaluation of active group state needed.")
                        // If selected ID is unchanged, and it's not null, ensure the group details listener is still active
                        // This handles cases where the group itself might expire or be deleted while selected.
                        if (newSelectedActiveGroupId != null && newSelectedActiveGroupId.isNotBlank()) {
                            listenForActiveGroupDetails(newSelectedActiveGroupId)
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
                            stopOtherMembersListeners()
                            markerMonitorService.stopMonitoringMarkers()
                            activeGroupDetailsListener?.remove()
                            activeGroupDetailsListener = null
                        }
                    }
                } else {
                    // User profile document does not exist, treat as no active group.
                    Log.d("GroupMonitorService", "User profile document not found. Clearing active group state.")
                    _activeGroupMember.value = null
                    _activeGroup.value = null
                    _isInActiveGroup.value = false
                    stopOtherMembersListeners()
                    markerMonitorService.stopMonitoringMarkers()
                    val oldStatus = _groupDetailsStatus.value
                    if (oldStatus != GroupDetailsStatus.NOT_FOUND) {
                        _groupDetailsStatus.value = GroupDetailsStatus.NOT_FOUND
                        Log.d("GroupMonitorService", "Group details status changed: $oldStatus -> NOT_FOUND (profile not found).")
                    }
                    activeGroupDetailsListener?.remove()
                    activeGroupDetailsListener = null
                }
            }
    }

    /**
     * Listens for changes to the current user's group memberships from Firestore.
     * This listener updates the list of all active/inactive memberships a user is part of.
     * It does *not* directly drive the "active group for tracking" logic, but provides
     * the data for `listenForCurrentUserProfile` to pick from.
     *
     * IMPORTANT: This method is now intended to be called only once initially on startup
     * and should not contain logic that re-triggers the main active group evaluation.
     */
    private fun listenForCurrentUserGroupMemberships(userId: String) {
        // Ensure this listener is only attached once or if userId changes
        if (currentUserGroupMembershipsListener == null || currentUserGroupMembershipsListener?.equals(userId) == false) { // Basic check, could be more robust
            currentUserGroupMembershipsListener?.remove() // Remove old listener if exists for a different user
            Log.d("GroupMonitorService", "listenForCurrentUserGroupMemberships: Attaching listener for user $userId's group memberships.")

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
                        Log.d("GroupMonitorService", "Updated _otherGroupMembers with ${activeMembershipsFromAll.size} active memberships.")

                        // Now, we need to ensure other members' data listeners are updated based on this new set
                        val newMemberUserIds = activeMembershipsFromAll.map { it.userId }.distinct().toSet()
                        if (newMemberUserIds != _currentlyMonitoredOtherUserIds.value) {
                            Log.d("GroupMonitorService", "Monitored user IDs for other members changed. Re-attaching other members' data listeners. Old: ${_currentlyMonitoredOtherUserIds.value}, New: $newMemberUserIds")
                            stopOtherMembersLocationsListener()
                            stopOtherMembersProfilesListeners()
                            listenForOtherMembersLocations(newMemberUserIds.toList())
                            listenForOtherMembersProfiles(newMemberUserIds.toList())
                            _currentlyMonitoredOtherUserIds.value = newMemberUserIds
                        }
                    }
                }
        }
    }


    /**
     * Listens for details of the currently active group.
     */
    private fun listenForActiveGroupDetails(groupId: String) {
        // Only remove and re-attach if the groupId actually changes or listener is null
        if (activeGroupDetailsListener == null || (activeGroup.value?.groupID != groupId)) {
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
                            }
                        } else {
                            Log.d("GroupMonitorService", "Active group data for $groupId is null or malformed. Treating as NOT_FOUND.")
                            _activeGroup.value = null
                            val oldStatus = _groupDetailsStatus.value
                            if (oldStatus != GroupDetailsStatus.NOT_FOUND) {
                                _groupDetailsStatus.value = GroupDetailsStatus.NOT_FOUND
                                Log.d("GroupMonitorService", "Group details status changed: $oldStatus -> NOT_FOUND")
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
                    }
                }
        } else {
            Log.d("GroupMonitorService", "listenForActiveGroupDetails: Listener for group $groupId is already active. Skipping re-attachment.")
        }
    }

    /**
     * Listens for other active members within the specified group.
     */
    private fun listenForOtherGroupMembers(groupId: String, currentUserId: String) {
        // Only remove and re-attach if the groupId actually changes or listener is null
        if (otherGroupMembersListener == null || otherGroupMembersListener?.equals(groupId) == false) { // Basic check
            otherGroupMembersListener?.remove()
            Log.d("GroupMonitorService", "listenForOtherGroupMembers: Attaching listener for group $groupId, excluding $currentUserId.")

            if (groupId.isEmpty()) {
                _otherGroupMembers.value = emptyList()
                stopOtherMembersLocationsListener()
                stopOtherMembersProfilesListeners()
                _currentlyMonitoredOtherUserIds.value = emptySet() // Reset here too
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
                        _currentlyMonitoredOtherUserIds.value = emptySet() // Reset on error
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

                        val newMemberUserIds = otherMembers.map { it.userId }.distinct().toSet()

                        // Only stop and re-attach sub-listeners if the set of users to monitor has changed.
                        if (newMemberUserIds != _currentlyMonitoredOtherUserIds.value) {
                            Log.d("GroupMonitorService", "Monitored user IDs changed. Re-attaching other members' data listeners. Old: ${_currentlyMonitoredOtherUserIds.value}, New: $newMemberUserIds")
                            stopOtherMembersLocationsListener()
                            stopOtherMembersProfilesListeners()
                            listenForOtherMembersLocations(newMemberUserIds.toList()) // Convert to List for whereIn
                            listenForOtherMembersProfiles(newMemberUserIds.toList()) // Convert to List for profile listeners
                            _currentlyMonitoredOtherUserIds.value = newMemberUserIds
                        } else {
                            Log.d("GroupMonitorService", "Monitored user IDs are unchanged. Skipping re-attachment of other members' data listeners.")
                        }

                        Log.d("GroupMonitorService", "Found ${otherMembers.size} other active members.")
                    }
                }
        } else {
            Log.d("GroupMonitorService", "listenForOtherGroupMembers: Listener for group $groupId is already active. Skipping re-attachment.")
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

        val distinctUserIds = userIds.distinct().take(10) // Firestore 'whereIn' limit is 10

        if (distinctUserIds.isEmpty()) { // If take(10) resulted in empty list
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
        // This function will now be called by `listenForOtherGroupMembers` only when `newMemberUserIds != _currentlyMonitoredOtherUserIds.value`
        // so `stopOtherMembersProfilesListeners()` will be called before calling this.
        // Therefore, we don't need to call it here.
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
