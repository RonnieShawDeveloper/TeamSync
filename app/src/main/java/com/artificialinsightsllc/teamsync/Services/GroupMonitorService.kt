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
import com.google.firebase.messaging.FirebaseMessaging // NEW: Import FirebaseMessaging


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

    private val _currentUserLocation = MutableStateFlow<Locations?>(null)
    val currentUserLocation: StateFlow<Locations?> = _currentUserLocation.asStateFlow()


    private var currentUserGroupMembershipsListener: ListenerRegistration? = null
    private var currentUserProfileListener: ListenerRegistration? = null
    private var activeGroupDetailsListener: ListenerRegistration? = null
    private var activeGroupUsersLocationsListener: ListenerRegistration? = null // Corrected name for scalable location queries
    private var otherMembersProfilesListeners: MutableMap<String, ListenerRegistration> = mutableMapOf()

    private var currentUserLocationListener: ListenerRegistration? = null

    private val _uiPermissionsGranted = MutableStateFlow(false)
    val uiPermissionsGranted: StateFlow<Boolean> = _uiPermissionsGranted.asStateFlow()

    private var locationTrackingIntentJob: Job? = null

    private var lastProcessedActiveGroupId: String? = null
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

    private fun stopActiveGroupUsersLocationsListener() {
        activeGroupUsersLocationsListener?.remove()
        activeGroupUsersLocationsListener = null
        _otherMembersLocations.value = emptyList()
        Log.d("GroupMonitorService", "Stopped active group users' locations listener.")
    }

    private fun stopMapRelatedListeners() {
        stopActiveGroupUsersLocationsListener()
        stopOtherMembersProfilesListeners()
        markerMonitorService.stopMonitoringMarkers()
        Log.d("GroupMonitorService", "Stopped all map-related listeners.")
    }

    fun stopAllListeners() {
        currentUserGroupMembershipsListener?.remove()
        currentUserGroupMembershipsListener = null
        currentUserProfileListener?.remove()
        currentUserProfileListener = null
        activeGroupDetailsListener?.remove()
        activeGroupDetailsListener = null
        currentUserLocationListener?.remove()
        currentUserLocationListener = null

        stopMapRelatedListeners()

        Log.d("GroupMonitorService", "Stopped all Firestore listeners.")

        _groupDetailsStatus.value = GroupDetailsStatus.LOADING
        _activeGroup.value = null
        _activeGroupMember.value = null
        _isInActiveGroup.value = false
        _otherGroupMembers.value = emptyList()
        _otherMembersLocations.value = emptyList()
        _otherMembersProfiles.value = emptyMap()
        _effectiveLocationUpdateInterval.value = 300000L
        _currentUserLocation.value = null

        lastProcessedActiveGroupId = null
        locationTrackingIntentJob?.cancel()

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
        locationTrackingIntentJob?.cancel()
        locationTrackingIntentJob = monitorScope.launch {
            delay(100)
            sendLocationTrackingIntent(interval, isSharing, stopServiceCompletely, memberId, activeTrackingGroupId)
        }
    }

    /**
     * Sends the actual intent to LocationTrackingService.
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
        if (currentUserProfileListener == null) {
            auth.addAuthStateListener { firebaseAuth ->
                val user = firebaseAuth.currentUser
                if (user != null) {
                    Log.d("GroupMonitorService", "Auth State Listener: User logged in: ${user.uid}. Starting primary data listeners.")
                    monitorScope.launch {
                        listenForCurrentUserProfile(user.uid)
                        listenForCurrentUserGroupMemberships(user.uid)
                        listenForCurrentUserLocation(user.uid)
                        manageFCMTokenAndSubscriptions(user.uid) // NEW: Call FCM management here
                    }
                } else {
                    Log.d("GroupMonitorService", "Auth State Listener: User logged out. Stopping all monitoring.")
                    stopAllListeners()
                    _userMessage.value = "You have been logged out."
                }
            }
            val initialUser = auth.currentUser
            if (initialUser != null) {
                Log.d("GroupMonitorService", "startMonitoring: User already logged in on initial call: ${initialUser.uid}. Initiating primary listeners directly.")
                monitorScope.launch {
                    listenForCurrentUserProfile(initialUser.uid)
                    listenForCurrentUserGroupMemberships(initialUser.uid)
                    listenForCurrentUserLocation(initialUser.uid)
                    manageFCMTokenAndSubscriptions(initialUser.uid) // NEW: Call FCM management here on initial app start
                }
            } else {
                Log.d("GroupMonitorService", "startMonitoring: No user logged in on initial call. Waiting for AuthStateListener.")
            }
        } else {
            Log.d("GroupMonitorService", "startMonitoring: AuthStateListener already active. Skipping re-attachment.")
        }

        monitorScope.launch {
            val app = context.applicationContext as TeamSyncApplication

            var lastDesiredServiceRunningState: Boolean? = null
            var lastDesiredInterval: Long? = null
            var lastDesiredMemberId: String? = null
            var lastDesiredActiveTrackingGroupId: String? = null

            combine(
                _activeGroupMember,
                _activeGroup,
                _groupDetailsStatus,
                _uiPermissionsGranted,
                app.locationServiceRunningState
            ) { member, group, detailsStatus, uiPermissionsGranted, actualServiceStateTriple ->
                val actualServiceIsRunning = actualServiceStateTriple.first
                val actualTrackingInterval = actualServiceStateTriple.second
                val actualTrackingMemberId = actualServiceStateTriple.third

                val memberIsActive = member != null && member.unjoinedTimestamp == null
                val groupIsValidAndActive = group != null && (group.groupEndTimestamp?.let { it > System.currentTimeMillis() } ?: true)

                Log.d("GroupMonitorService", "Combine Evaluation: Member=${member?.id}, Group=${group?.groupID}, Status=$detailsStatus, UI Perms=$uiPermissionsGranted, ActualServiceRunning=$actualServiceIsRunning, ActualInterval=$actualTrackingInterval, ActualMemberId=$actualTrackingMemberId")


                val desiredServiceRunning: Boolean
                val desiredInterval: Long
                val desiredMemberId: String?
                val desiredActiveTrackingGroupId: String?

                if (memberIsActive && groupIsValidAndActive && uiPermissionsGranted) {
                    val nonNullMember = member!!
                    val nonNullGroup = group!!
                    desiredServiceRunning = true
                    desiredInterval = nonNullMember.personalLocationUpdateIntervalMillis
                        ?: nonNullGroup.locationUpdateIntervalMillis
                                ?: 300000L
                    desiredMemberId = nonNullMember.id
                    desiredActiveTrackingGroupId = nonNullGroup.groupID

                    _isInActiveGroup.value = true
                    Log.d("GroupMonitorService", "Group ACTIVE & member active & perms granted. Desired service state: RUNNING, interval: $desiredInterval, activeTrackingGroupId: $desiredActiveTrackingGroupId.")
                } else {
                    desiredServiceRunning = false
                    desiredInterval = 0L
                    desiredMemberId = null
                    desiredActiveTrackingGroupId = null

                    _isInActiveGroup.value = false
                    Log.d("GroupMonitorService", "Conditions for active tracking NOT met. Desired service state: STOPPED.")
                }

                _effectiveLocationUpdateInterval.value = desiredInterval

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
     * NEW: Manages FCM token retrieval and topic subscriptions.
     * This function should be called upon successful user authentication.
     */
    private suspend fun manageFCMTokenAndSubscriptions(userId: String) {
        Log.d("GroupMonitorService", "Managing FCM token and subscriptions for user: $userId")
        // 1. Get current FCM token
        val fcmToken = try {
            FirebaseMessaging.getInstance().token.await()
        } catch (e: Exception) {
            Log.e("GroupMonitorService", "Failed to get FCM token: ${e.message}", e)
            null
        }

        if (fcmToken != null) {
            Log.d("GroupMonitorService", "FCM Token retrieved: $fcmToken")
            // 2. Save token to user profile if it's new or changed
            val currentUserProfile = firestoreService.getUserProfile(userId).getOrNull()
            if (currentUserProfile == null || currentUserProfile.fcmToken != fcmToken) {
                val updatedUser = (currentUserProfile ?: UserModel(authId = userId, userId = userId)).copy(fcmToken = fcmToken)
                firestoreService.saveUserProfile(updatedUser)
                    .onSuccess { Log.d("GroupMonitorService", "FCM token saved/updated in user profile for $userId.") }
                    .onFailure { e -> Log.e("GroupMonitorService", "Failed to save FCM token for $userId: ${e.message}", e) }
            } else {
                Log.d("GroupMonitorService", "FCM token for $userId is already up-to-date in Firestore.")
            }

            // 3. Subscribe to global "TeamSync" topic
            try {
                FirebaseMessaging.getInstance().subscribeToTopic("TeamSync").await()
                Log.d("GroupMonitorService", "Subscribed to TeamSync topic.")
            } catch (e: Exception) {
                Log.e("GroupMonitorService", "Failed to subscribe to TeamSync topic: ${e.message}", e)
            }

            // 4. Manage subscriptions for active groups
            // This part will be enhanced in a later step when we can dynamically manage group topic subscriptions.
            // For now, we'll ensure it runs when memberships are updated.
        } else {
            Log.w("GroupMonitorService", "FCM token is null, skipping token saving and topic subscriptions.")
        }
    }


    /**
     * Listens for changes to the current user's UserModel document, primarily for `selectedActiveGroupId`.
     */
    private fun listenForCurrentUserProfile(userId: String) {
        currentUserProfileListener?.remove()

        Log.d("GroupMonitorService", "listenForCurrentUserProfile: Attaching listener for user profile $userId.")
        currentUserProfileListener = db.collection("users").document(userId)
            .addSnapshotListener { snapshot, e ->
                Log.d("GroupMonitorService", "listenForCurrentUserProfile: Snapshot received for user profile $userId. Exists: ${snapshot?.exists()}, Error: ${e?.message}")
                if (e != null) {
                    Log.w("GroupMonitorService", "Listen for user profile $userId failed.", e)
                    val oldStatus = _groupDetailsStatus.value
                    if (oldStatus != GroupDetailsStatus.ERROR) {
                        _groupDetailsStatus.value = GroupDetailsStatus.ERROR
                        Log.d("GroupMonitorService", "Group details status changed: $oldStatus -> ERROR due to user profile listener error.")
                    }
                    Log.d("GroupMonitorService", "Clearing active group state due to user profile listener error.")
                    _activeGroupMember.value = null
                    _activeGroup.value = null
                    _isInActiveGroup.value = false
                    stopMapRelatedListeners()
                    lastProcessedActiveGroupId = null
                    activeGroupDetailsListener?.remove()
                    activeGroupDetailsListener = null
                    _otherMembersLocations.value = emptyList()
                    _otherMembersProfiles.value = emptyMap()
                    _currentUserLocation.value = null
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    val userProfile = snapshot.toObject(UserModel::class.java)
                    val newSelectedActiveGroupId = userProfile?.selectedActiveGroupId
                    Log.d("GroupMonitorService", "User profile updated. New selectedActiveGroupId: $newSelectedActiveGroupId, Last processed: $lastProcessedActiveGroupId")

                    val currentActiveGroupIdInState = _activeGroup.value?.groupID
                    val shouldReevaluateBecauseGroupExpiredOrDeleted = (newSelectedActiveGroupId == currentActiveGroupIdInState) &&
                            (_groupDetailsStatus.value == GroupDetailsStatus.EXPIRED || _groupDetailsStatus.value == GroupDetailsStatus.NOT_FOUND)

                    if (newSelectedActiveGroupId != lastProcessedActiveGroupId || shouldReevaluateBecauseGroupExpiredOrDeleted) {
                        Log.d("GroupMonitorService", "Selected active group ID changed or current group expired/deleted: $lastProcessedActiveGroupId -> $newSelectedActiveGroupId. Re-evaluating active group for tracking. Re-eval reason: shouldReevaluateBecauseGroupExpiredOrDeleted=$shouldReevaluateBecauseGroupExpiredOrDeleted")
                        lastProcessedActiveGroupId = newSelectedActiveGroupId

                        if (newSelectedActiveGroupId != null && newSelectedActiveGroupId.isNotBlank()) {
                            monitorScope.launch {
                                val membershipsResult = firestoreService.getGroupMembershipsForUser(userId).getOrNull() ?: emptyList()
                                val selectedMembership = membershipsResult.find {
                                    it.groupId == newSelectedActiveGroupId && it.unjoinedTimestamp == null
                                }

                                if (selectedMembership != null) {
                                    Log.d("GroupMonitorService", "Found active membership for selected group: ${selectedMembership.groupId}. Setting as primary active.")
                                    _activeGroupMember.value = selectedMembership
                                    _groupDetailsStatus.value = GroupDetailsStatus.LOADING
                                    listenForActiveGroupDetails(selectedMembership.groupId)
                                    listenForActiveGroupUsersLocations(selectedMembership.groupId, userId)
                                    markerMonitorService.startMonitoringMarkers(selectedMembership.groupId)
                                    // RE-SUBSCRIBE/UNSUBSCRIBE TO GROUP TOPICS HERE when active memberships change
                                    manageGroupTopicSubscriptions(userId, membershipsResult) // NEW: Call manage group topics
                                } else {
                                    Log.d("GroupMonitorService", "Selected active group ($newSelectedActiveGroupId) has no active membership for user $userId. Clearing active group state.")
                                    _activeGroupMember.value = null
                                    _activeGroup.value = null
                                    _isInActiveGroup.value = false
                                    stopMapRelatedListeners()
                                    val oldStatus = _groupDetailsStatus.value
                                    if (oldStatus != GroupDetailsStatus.NOT_FOUND) {
                                        _groupDetailsStatus.value = GroupDetailsStatus.NOT_FOUND
                                        Log.d("GroupMonitorService", "Group details status changed: $oldStatus -> NOT_FOUND (selected group inactive).")
                                    }
                                    activeGroupDetailsListener?.remove()
                                    activeGroupDetailsListener = null
                                    _otherMembersLocations.value = emptyList()
                                    _otherMembersProfiles.value = emptyMap()
                                    _currentUserLocation.value = null
                                    monitorScope.launch {
                                        firestoreService.updateUserSelectedActiveGroup(userId, null)
                                    }
                                    // RE-SUBSCRIBE/UNSUBSCRIBE TO GROUP TOPICS HERE when active memberships change (after clearing active group)
                                    manageGroupTopicSubscriptions(userId, membershipsResult) // NEW: Call manage group topics
                                }
                            }
                        } else {
                            Log.d("GroupMonitorService", "New selected active group ID is null/blank. Clearing active group state for tracking.")
                            _activeGroupMember.value = null
                            _activeGroup.value = null
                            _isInActiveGroup.value = false
                            stopMapRelatedListeners()
                            val oldStatus = _groupDetailsStatus.value
                            if (oldStatus != GroupDetailsStatus.NOT_FOUND) {
                                _groupDetailsStatus.value = GroupDetailsStatus.NOT_FOUND
                                Log.d("GroupMonitorService", "Group details status changed: $oldStatus -> NOT_FOUND (explicitly no active group).")
                            }
                            activeGroupDetailsListener?.remove()
                            activeGroupDetailsListener = null
                            _otherMembersLocations.value = emptyList()
                            _otherMembersProfiles.value = emptyMap()
                            _currentUserLocation.value = null
                            // RE-SUBSCRIBE/UNSUBSCRIBE TO GROUP TOPICS HERE
                            monitorScope.launch {
                                val memberships = firestoreService.getGroupMembershipsForUser(userId).getOrNull() ?: emptyList()
                                manageGroupTopicSubscriptions(userId, memberships) // NEW: Call manage group topics
                            }
                        }
                    } else {
                        Log.d("GroupMonitorService", "Selected active group ID unchanged ($newSelectedActiveGroupId). No re-evaluation of active group state needed.")
                        if (newSelectedActiveGroupId != null && newSelectedActiveGroupId.isNotBlank()) {
                            if (_activeGroup.value == null || _groupDetailsStatus.value == GroupDetailsStatus.NOT_FOUND || _groupDetailsStatus.value == GroupDetailsStatus.EXPIRED) {
                                Log.d("GroupMonitorService", "Active group document might be missing or expired, re-attaching listeners for $newSelectedActiveGroupId.")
                                _groupDetailsStatus.value = GroupDetailsStatus.LOADING
                                listenForActiveGroupDetails(newSelectedActiveGroupId)
                                listenForActiveGroupUsersLocations(newSelectedActiveGroupId, userId)
                                markerMonitorService.startMonitoringMarkers(newSelectedActiveGroupId)
                                // Only call manageGroupTopicSubscriptions if a user profile change has triggered it, and we are managing group state
                                monitorScope.launch {
                                    val memberships = firestoreService.getGroupMembershipsForUser(userId).getOrNull() ?: emptyList()
                                    manageGroupTopicSubscriptions(userId, memberships)
                                }
                            } else {
                                Log.d("GroupMonitorService", "Active group ($newSelectedActiveGroupId) appears healthy. Skipping listener re-attachment.")
                            }
                        } else {
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
                            _otherMembersLocations.value = emptyList()
                            _otherMembersProfiles.value = emptyMap()
                            _currentUserLocation.value = null
                            // RE-SUBSCRIBE/UNSUBSCRIBE TO GROUP TOPICS HERE
                            monitorScope.launch {
                                val memberships = firestoreService.getGroupMembershipsForUser(userId).getOrNull() ?: emptyList()
                                manageGroupTopicSubscriptions(userId, memberships) // NEW: Call manage group topics
                            }
                        }
                    }
                } else {
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
                    _otherMembersLocations.value = emptyList()
                    _otherMembersProfiles.value = emptyMap()
                    _currentUserLocation.value = null
                }
            }
    }

    /**
     * Listens for changes to the current user's group memberships from Firestore.
     */
    private fun listenForCurrentUserGroupMemberships(userId: String) {
        if (currentUserGroupMembershipsListener == null || (auth.currentUser?.uid != null && currentUserGroupMembershipsListener?.equals(userId) == false)) {
            currentUserGroupMembershipsListener?.remove()
            Log.d("GroupMonitorService", "listenForCurrentUserGroupMemberships: Attaching listener for user $userId's group memberships (for TeamList/GroupsList Data).")

            currentUserGroupMembershipsListener = db.collection("groupMembers")
                .whereEqualTo("userId", userId)
                .addSnapshotListener { snapshots, e ->
                    Log.d("GroupMonitorService", "listenForCurrentUserGroupMemberships (Secondary Listener): Listener triggered for user $userId. Error: ${e?.message}, Snapshots: ${snapshots?.size()}")
                    if (e != null) {
                        Log.w("GroupMonitorService", "Secondary listen for current user's group memberships failed.", e)
                        _otherGroupMembers.value = emptyList()
                        return@addSnapshotListener
                    }

                    if (snapshots != null) {
                        val allMemberships = snapshots.documents.mapNotNull { doc ->
                            doc.toObject(GroupMembers::class.java)?.copy(id = doc.id)
                        }
                        val activeMembershipsFromAll = allMemberships.filter { it.unjoinedTimestamp == null }

                        _otherGroupMembers.value = activeMembershipsFromAll
                        Log.d("GroupMonitorService", "Updated _otherGroupMembers with ${activeMembershipsFromAll.size} active memberships for TeamList/GroupsList.")

                        // NEW: Also manage group topic subscriptions when memberships change
                        // This ensures that if a user is added to a group or leaves a group,
                        // their FCM subscriptions are updated accordingly.
                        monitorScope.launch {
                            manageGroupTopicSubscriptions(userId, activeMembershipsFromAll)
                        }
                    }
                }
        }
    }

    /**
     * NEW: Manages FCM topic subscriptions/unsubscriptions for group channels.
     * This ensures the user is subscribed to topics for groups they are actively members of,
     * and unsubscribed from groups they have left.
     *
     * @param userId The ID of the current user.
     * @param currentActiveMemberships The list of all currently active group memberships for the user.
     */
    private suspend fun manageGroupTopicSubscriptions(userId: String, currentActiveMemberships: List<GroupMembers>) {
        val currentSubscribedTopics = mutableSetOf<String>()
        // In a real app, you'd ideally keep track of currently subscribed topics
        // to avoid unnecessary API calls. For simplicity here, we'll resubscribe
        // to all active ones and rely on FCM's idempotency.
        // For unsubscribing, you'd need a more robust tracking system or query FCM directly.
        // For now, we'll focus on ensuring active subscriptions are in place.

        // Get all groups the user is currently a member of.
        val activeGroupIds = currentActiveMemberships.map { it.groupId }.toSet()

        // Fetch group details for active groups to get fcmName
        val activeGroups = firestoreService.getGroupsByIds(activeGroupIds.toList()).getOrNull() ?: emptyList()

        // Subscribe to active group topics
        activeGroups.forEach { group ->
            val topicName = "group_${group.groupID}"
            currentSubscribedTopics.add(topicName) // Keep track for potential future unsubscribes
            try {
                FirebaseMessaging.getInstance().subscribeToTopic(topicName).await()
                Log.d("GroupMonitorService", "Subscribed to group topic: $topicName for user $userId.")
            } catch (e: Exception) {
                Log.e("GroupMonitorService", "Failed to subscribe to group topic $topicName: ${e.message}", e)
            }
        }

        // IMPROVEMENT: To truly handle unsubscribes for groups no longer joined,
        // you would need to:
        // 1. Maintain a persistent local record (e.g., in Room) of all topics the user is subscribed to.
        // 2. Compare this local list with `currentActiveMemberships`.
        // 3. Unsubscribe from any topics in the local list that are *not* in `currentActiveMemberships`.
        // This is a more advanced feature for a later iteration if needed for resource optimization.
        Log.d("GroupMonitorService", "Finished managing group topic subscriptions for user $userId. Currently active topics: $currentSubscribedTopics")
    }


    /**
     * Listens for details of the currently active group.
     */
    private fun listenForActiveGroupDetails(groupId: String) {
        if (activeGroupDetailsListener == null || (activeGroup.value?.groupID != groupId)) {
            activeGroupDetailsListener?.remove()
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
     * Listens for the current user's location from the `current_user_locations` collection.
     */
    private fun listenForCurrentUserLocation(userId: String) {
        currentUserLocationListener?.remove()

        Log.d("GroupMonitorService", "listenForCurrentUserLocation: Attaching listener for current user's location: $userId.")

        currentUserLocationListener = db.collection("current_user_locations").document(userId)
            .addSnapshotListener { snapshot, e ->
                Log.d("GroupMonitorService", "listenForCurrentUserLocation: Snapshot received for user $userId. Exists: ${snapshot?.exists()}, Error: ${e?.message}")
                if (e != null) {
                    Log.w("GroupMonitorService", "Listen for current user's location failed.", e)
                    _currentUserLocation.value = null
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    val location = snapshot.toObject(Locations::class.java)
                    _currentUserLocation.value = location
                    Log.d("GroupMonitorService", "Updated current user's location: ${location?.latitude}, ${location?.longitude}")
                } else {
                    _currentUserLocation.value = null
                    Log.d("GroupMonitorService", "Current user's location document not found or cleared for $userId.")
                }
            }
    }


    /**
     * Listens for the current location of users who are actively tracking for the specified group.
     */
    private fun listenForActiveGroupUsersLocations(groupId: String, currentUserId: String) {
        activeGroupUsersLocationsListener?.remove()

        Log.d("GroupMonitorService", "listenForActiveGroupUsersLocations: Attaching listener for active group users in group: $groupId.")

        if (groupId.isEmpty()) {
            _otherMembersLocations.value = emptyList()
            _otherMembersProfiles.value = emptyMap()
            stopOtherMembersProfilesListeners()
            Log.d("GroupMonitorService", "listenForActiveGroupUsersLocations: Group ID is empty, skipping listener attachment.")
            return
        }

        activeGroupUsersLocationsListener = db.collection("current_user_locations")
            .whereEqualTo("activeTrackingGroupId", groupId)
            .addSnapshotListener { snapshots, e ->
                Log.d("GroupMonitorService", "listenForActiveGroupUsersLocations: Snapshot received for group $groupId. Error: ${e?.message}, Snapshots: ${snapshots?.size()}")
                if (e != null) {
                    Log.w("GroupMonitorService", "Listen for active group users' locations failed.", e)
                    _otherMembersLocations.value = emptyList()
                    _otherMembersProfiles.value = emptyMap()
                    stopOtherMembersProfilesListeners()
                    return@addSnapshotListener
                }

                if (snapshots != null) {
                    val activeLocations = snapshots.documents.mapNotNull { doc ->
                        doc.toObject(Locations::class.java)
                    }.filter {
                        it.userId != currentUserId
                    }
                    _otherMembersLocations.value = activeLocations
                    Log.d("GroupMonitorService", "Updated ${activeLocations.size} other active group members' locations from current_user_locations.")

                    val newProfileUserIds = activeLocations.map { it.userId }.distinct()
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
     */
    private fun listenForOtherMembersProfiles(userIds: List<String>) {
        val newUserIdsSet = userIds.distinct().toSet()
        if (newUserIdsSet == activelyMonitoredProfileUserIds) {
            Log.d("GroupMonitorService", "listenForOtherMembersProfiles: Monitored profile user IDs are unchanged. Skipping listener re-attachment.")
            return
        }

        stopOtherMembersProfilesListeners()
        activelyMonitoredProfileUserIds = newUserIdsSet

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
