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
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay
import com.artificialinsightsllc.teamsync.TeamSyncApplication
import com.google.firebase.messaging.FirebaseMessaging


class GroupMonitorService(
    internal val context: Context,
    private val firestoreService: FirestoreService = FirestoreService(),
    internal val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val markerMonitorService: MarkerMonitorService
) {
    private val monitorScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    public enum class GroupDetailsStatus {
        LOADING,
        ACTIVE,
        EXPIRED,
        NOT_FOUND,
        ERROR
    }

    private val _activeGroupMember = MutableStateFlow<GroupMembers?>(null)
    val activeGroupMember: StateFlow<GroupMembers?> = _activeGroupMember.asStateFlow()

    private val _activeGroup = MutableStateFlow<Groups?>(null)
    val activeGroup: StateFlow<Groups?> = _activeGroup.asStateFlow()

    private val _isInActiveGroup = MutableStateFlow(false)
    val isInActiveGroup: StateFlow<Boolean> = _isInActiveGroup.asStateFlow()

    private val _effectiveLocationUpdateInterval = MutableStateFlow(300000L)
    val effectiveLocationUpdateInterval: StateFlow<Long> = _effectiveLocationUpdateInterval.asStateFlow()

    // REPLACED: _otherGroupMembers is now strictly for *all* memberships of the current user for GroupsList
    private val _userAllMemberships = MutableStateFlow<List<GroupMembers>>(emptyList())
    val userAllMemberships: StateFlow<List<GroupMembers>> = _userAllMemberships.asStateFlow()

    // NEW: This will hold all active GroupMembers for the *currently selected active group*
    private val _activeGroupAllMembers = MutableStateFlow<List<GroupMembers>>(emptyList())
    val activeGroupAllMembers: StateFlow<List<GroupMembers>> = _activeGroupAllMembers.asStateFlow()


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

    // NEW: Expose currentUserModel (previously implicitly used in MainViewModel)
    private val _currentUserModel = MutableStateFlow<UserModel?>(null)
    val currentUserModel: StateFlow<UserModel?> = _currentUserModel.asStateFlow()


    private var currentUserGroupMembershipsListener: ListenerRegistration? = null // For userAllMemberships
    private var currentUserProfileListener: ListenerRegistration? = null // For _currentUserModel
    private var activeGroupDetailsListener: ListenerRegistration? = null
    private var activeGroupUsersLocationsListener: ListenerRegistration? = null
    private var otherMembersProfilesListeners: MutableMap<String, ListenerRegistration> = mutableMapOf()

    private var activeGroupAllMembersListener: ListenerRegistration? = null // NEW Listener for _activeGroupAllMembers

    private var currentUserLocationListener: ListenerRegistration? = null

    private val _uiPermissionsGranted = MutableStateFlow(false)
    val uiPermissionsGranted: StateFlow<Boolean> = _uiPermissionsGranted.asStateFlow()

    private var locationTrackingIntentJob: Job? = null
    private var logoutDebounceJob: Job? = null


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
        activelyMonitoredProfileUserIds = emptySet()
        Log.d("GroupMonitorService", "Stopped other members' profile listeners and reset activelyMonitoredProfileUserIds.")
    }

    private fun stopActiveGroupUsersLocationsListener() {
        activeGroupUsersLocationsListener?.remove()
        activeGroupUsersLocationsListener = null
        _otherMembersLocations.value = emptyList()
        Log.d("GroupMonitorService", "Stopped active group users' locations listener.")
    }

    private fun stopActiveGroupAllMembersListener() {
        activeGroupAllMembersListener?.remove()
        activeGroupAllMembersListener = null
        _activeGroupAllMembers.value = emptyList()
        Log.d("GroupMonitorService", "Stopped active group ALL members listener.")
    }

    private fun stopMapRelatedListeners() {
        stopActiveGroupUsersLocationsListener()
        stopOtherMembersProfilesListeners()
        stopActiveGroupAllMembersListener()
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
        _userAllMemberships.value = emptyList()
        _activeGroupAllMembers.value = emptyList()
        _otherMembersLocations.value = emptyList()
        _otherMembersProfiles.value = emptyMap()
        _effectiveLocationUpdateInterval.value = 300000L
        _currentUserLocation.value = null
        _currentUserModel.value = null

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
                    logoutDebounceJob?.cancel() // If user logs in during debounce, cancel previous job
                    Log.d("GroupMonitorService", "Auth State Listener: User logged in: ${user.uid}. Starting primary data listeners.")
                    monitorScope.launch {
                        listenForCurrentUserProfile(user.uid)
                        listenForUserAllMemberships(user.uid)
                        listenForCurrentUserLocation(user.uid)
                        manageFCMTokenAndSubscriptions(user.uid)
                    }
                } else {
                    // Implement debounce for logout check
                    logoutDebounceJob?.cancel()
                    logoutDebounceJob = monitorScope.launch {
                        Log.d("GroupMonitorService", "Auth State Listener: User is NULL. Debouncing logout check for 1 second...")
                        delay(1000L) // Wait for 1 second
                        if (auth.currentUser == null) { // Re-check after delay
                            Log.d("GroupMonitorService", "Auth State Listener: User is still NULL after debounce. Stopping all monitoring.")
                            stopAllListeners()
                            // Removed the problematic _userMessage.value assignment here
                        } else {
                            Log.d("GroupMonitorService", "Auth State Listener: User is NOT NULL after debounce. Session restored.")
                            auth.currentUser?.uid?.let {
                                listenForCurrentUserProfile(it)
                                listenForUserAllMemberships(it)
                                listenForCurrentUserLocation(it)
                                manageFCMTokenAndSubscriptions(it)
                            }
                        }
                    }
                }
            }
            val initialUser = auth.currentUser
            if (initialUser != null) {
                Log.d("GroupMonitorService", "startMonitoring: User already logged in on initial call: ${initialUser.uid}. Initiating primary listeners directly.")
                monitorScope.launch {
                    listenForCurrentUserProfile(initialUser.uid)
                    listenForUserAllMemberships(initialUser.uid)
                    listenForCurrentUserLocation(initialUser.uid)
                    manageFCMTokenAndSubscriptions(initialUser.uid)
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
     * Manages FCM token retrieval and topic subscriptions.
     * This function should be called upon successful user authentication.
     */
    private suspend fun manageFCMTokenAndSubscriptions(userId: String) {
        Log.d("GroupMonitorService", "Managing FCM token and subscriptions for user: $userId")
        val fcmToken = try {
            FirebaseMessaging.getInstance().token.await()
        } catch (e: Exception) {
            Log.e("GroupMonitorService", "Failed to get FCM token: ${e.message}", e)
            null
        }

        if (fcmToken != null) {
            Log.d("GroupMonitorService", "FCM Token retrieved: $fcmToken")
            val currentUserProfile = firestoreService.getUserProfile(userId).getOrNull()
            if (currentUserProfile == null || currentUserProfile.fcmToken != fcmToken) {
                val updatedUser = (currentUserProfile ?: UserModel(authId = userId, userId = userId)).copy(fcmToken = fcmToken)
                firestoreService.saveUserProfile(updatedUser)
                    .onSuccess { Log.d("GroupMonitorService", "FCM token saved/updated in user profile for $userId.") }
                    .onFailure { e -> Log.e("GroupMonitorService", "Failed to save FCM token for $userId: ${e.message}", e) }
            } else {
                Log.d("GroupMonitorService", "FCM token for $userId is already up-to-date in Firestore.")
            }

            try {
                FirebaseMessaging.getInstance().subscribeToTopic("TeamSync").await()
                Log.d("GroupMonitorService", "Subscribed to TeamSync topic.")
            } catch (e: Exception) {
                Log.e("GroupMonitorService", "Failed to subscribe to TeamSync topic: ${e.message}", e)
            }

            // Manage subscriptions for active groups
            monitorScope.launch {
                val memberships = firestoreService.getGroupMembershipsForUser(userId).getOrNull() ?: emptyList()
                manageGroupTopicSubscriptions(userId, memberships)
            }
        } else {
            Log.w("GroupMonitorService", "FCM token is null, skipping token saving and topic subscriptions.")
        }
    }


    /**
     * Listens for changes to the current user's UserModel document.
     * This is for _currentUserModel StateFlow and for observing selectedActiveGroupId.
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
                    _currentUserModel.value = null
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    val userProfile = snapshot.toObject(UserModel::class.java)
                    _currentUserModel.value = userProfile
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
                                    listenForActiveGroupAllMembers(selectedMembership.groupId)
                                    markerMonitorService.startMonitoringMarkers(selectedMembership.groupId)
                                    manageGroupTopicSubscriptions(userId, membershipsResult)
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
                                    _activeGroupAllMembers.value = emptyList()
                                    monitorScope.launch {
                                        firestoreService.updateUserSelectedActiveGroup(userId, null)
                                    }
                                    manageGroupTopicSubscriptions(userId, membershipsResult)
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
                            _activeGroupAllMembers.value = emptyList()
                            monitorScope.launch {
                                val memberships = firestoreService.getGroupMembershipsForUser(userId).getOrNull() ?: emptyList()
                                manageGroupTopicSubscriptions(userId, memberships)
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
                                listenForActiveGroupAllMembers(newSelectedActiveGroupId)
                                markerMonitorService.startMonitoringMarkers(newSelectedActiveGroupId)
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
                            _activeGroupAllMembers.value = emptyList()
                            monitorScope.launch {
                                val memberships = firestoreService.getGroupMembershipsForUser(userId).getOrNull() ?: emptyList()
                                manageGroupTopicSubscriptions(userId, memberships)
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
                    _currentUserModel.value = null
                }
            }
    }

    /**
     * Listens for changes to the current user's group memberships from Firestore.
     * This populates `_userAllMemberships`.
     */
    private fun listenForUserAllMemberships(userId: String) {
        if (currentUserGroupMembershipsListener == null || (auth.currentUser?.uid != null && currentUserGroupMembershipsListener?.equals(userId) == false)) {
            currentUserGroupMembershipsListener?.remove()
            Log.d("GroupMonitorService", "listenForUserAllMemberships: Attaching listener for user $userId's group memberships (for TeamList/GroupsList Data).")

            currentUserGroupMembershipsListener = db.collection("groupMembers")
                .whereEqualTo("userId", userId)
                .addSnapshotListener { snapshots, e ->
                    Log.d("GroupMonitorService", "listenForUserAllMemberships: Listener triggered for user $userId. Error: ${e?.message}, Snapshots: ${snapshots?.size()}")
                    if (e != null) {
                        Log.w("GroupMonitorService", "Listen for current user's ALL group memberships failed.", e)
                        _userAllMemberships.value = emptyList()
                        return@addSnapshotListener
                    }

                    if (snapshots != null) {
                        val allMemberships = snapshots.documents.mapNotNull { doc ->
                            doc.toObject(GroupMembers::class.java)?.copy(id = doc.id)
                        }
                        _userAllMemberships.value = allMemberships
                        Log.d("GroupMonitorService", "Updated _userAllMemberships with ${allMemberships.size} memberships for TeamList/GroupsList.")

                        monitorScope.launch {
                            manageGroupTopicSubscriptions(userId, allMemberships.filter { it.unjoinedTimestamp == null })
                        }
                    }
                }
        }
    }

    /**
     * Listens for all GroupMembers documents within the active group.
     * This populates `_activeGroupAllMembers`.
     */
    private fun listenForActiveGroupAllMembers(groupId: String) {
        if (activeGroupAllMembersListener == null || (activeGroup.value?.groupID != groupId)) {
            activeGroupAllMembersListener?.remove()
            Log.d("GroupMonitorService", "listenForActiveGroupAllMembers: Attaching listener for ALL group members in group: $groupId.")

            if (groupId.isEmpty()) {
                _activeGroupAllMembers.value = emptyList()
                Log.d("GroupMonitorService", "listenForActiveGroupAllMembers: Group ID is empty, skipping listener attachment.")
                return
            }

            activeGroupAllMembersListener = db.collection("groupMembers")
                .whereEqualTo("groupId", groupId)
                .addSnapshotListener { snapshots, e ->
                    Log.d("GroupMonitorService", "listenForActiveGroupAllMembers: Snapshot received for group $groupId. Error: ${e?.message}, Snapshots: ${snapshots?.size()}")
                    if (e != null) {
                        Log.w("GroupMonitorService", "Listen for ALL group members failed.", e)
                        _activeGroupAllMembers.value = emptyList()
                        return@addSnapshotListener
                    }

                    if (snapshots != null) {
                        val members = snapshots.documents.mapNotNull { doc ->
                            doc.toObject(GroupMembers::class.java)?.copy(id = doc.id)
                        }.filter { it.unjoinedTimestamp == null }
                        _activeGroupAllMembers.value = members
                        Log.d("GroupMonitorService", "Updated _activeGroupAllMembers with ${members.size} active members for group $groupId.")
                    } else {
                        _activeGroupAllMembers.value = emptyList()
                    }
                }
        }
    }


    /**
     * Manages FCM topic subscriptions/unsubscriptions for group channels.
     */
    private suspend fun manageGroupTopicSubscriptions(userId: String, currentActiveMemberships: List<GroupMembers>) {
        val currentSubscribedTopics = mutableSetOf<String>()

        val activeGroupIds = currentActiveMemberships.map { it.groupId }.toSet()

        val activeGroups = firestoreService.getGroupsByIds(activeGroupIds.toList()).getOrNull() ?: emptyList()

        activeGroups.forEach { group ->
            val topicName = "group_${group.groupID}"
            currentSubscribedTopics.add(topicName)
            try {
                FirebaseMessaging.getInstance().subscribeToTopic(topicName).await()
                Log.d("GroupMonitorService", "Subscribed to group topic: $topicName for user $userId.")
            } catch (e: Exception) {
                Log.e("GroupMonitorService", "Failed to subscribe to group topic $topicName: ${e.message}", e)
            }
        }

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

                    val newProfileUserIds = activeLocations.map { it.userId }.distinct().toMutableSet()
                    auth.currentUser?.uid?.let { newProfileUserIds.add(it) }
                    listenForOtherMembersProfiles(newProfileUserIds.toList())
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
