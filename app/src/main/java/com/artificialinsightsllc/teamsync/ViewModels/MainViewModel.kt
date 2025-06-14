// In file: app/src/main/java/com/artificialinsightsllc/teamsync/ViewModels/MainViewModel.kt
package com.artificialinsightsllc.teamsync.ViewModels

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.artificialinsightsllc.teamsync.Models.GroupMembers
import com.artificialinsightsllc.teamsync.Models.Groups
import com.artificialinsightsllc.teamsync.Models.Locations
import com.artificialinsightsllc.teamsync.Models.MapMarker
import com.artificialinsightsllc.teamsync.Models.UserModel
import com.artificialinsightsllc.teamsync.Services.FirestoreService
import com.artificialinsightsllc.teamsync.Services.GroupMonitorService
import com.artificialinsightsllc.teamsync.Services.MarkerMonitorService
import com.artificialinsightsllc.teamsync.TeamSyncApplication
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import com.google.android.gms.maps.model.CameraPosition
import com.artificialinsightsllc.teamsync.Navigation.NavRoutes
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged


class MainViewModel(application: Application) : AndroidViewModel(application) {

    // Dependencies (Services)
    private val app = application as TeamSyncApplication
    private val groupMonitorService: GroupMonitorService = app.groupMonitorService
    private val markerMonitorService: MarkerMonitorService = app.markerMonitorService
    private val firestoreService: FirestoreService = FirestoreService()
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    // NEW: Inject GroupChatViewModel for badge updates and navigation
    private val groupChatViewModel: GroupChatViewModel = GroupChatViewModel(application)


    // UI State exposed to MainScreen
    private val _currentUserId = MutableStateFlow<String?>(null)
    val currentUserId: StateFlow<String?> = _currentUserId.asStateFlow()

    private val _currentUserLocation = MutableStateFlow<Locations?>(null)
    val currentUserLocation: StateFlow<Locations?> = _currentUserLocation.asStateFlow()

    private val _currentUserModel = MutableStateFlow<UserModel?>(null)
    val currentUserModel: StateFlow<UserModel?> = _currentUserModel.asStateFlow()

    private val _isInActiveGroup = MutableStateFlow(false)
    val isInActiveGroup: StateFlow<Boolean> = _isInActiveGroup.asStateFlow()

    private val _activeGroup = MutableStateFlow<Groups?>(null)
    val activeGroup: StateFlow<Groups?> = _activeGroup.asStateFlow()

    private val _otherMembersLocations = MutableStateFlow<List<Locations>>(emptyList())
    val otherMembersLocations: StateFlow<List<Locations>> = _otherMembersLocations.asStateFlow()

    private val _otherMembersProfiles = MutableStateFlow<Map<String, UserModel>>(emptyMap())
    val otherMembersProfiles: StateFlow<Map<String, UserModel>> = _otherMembersProfiles.asStateFlow()

    private val _mapMarkers = MutableStateFlow<List<MapMarker>>(emptyList())
    val mapMarkers: StateFlow<List<MapMarker>> = _mapMarkers.asStateFlow()

    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

    private val _mapLockedToUserLocation = MutableStateFlow(true)
    val mapLockedToUserLocation: StateFlow<Boolean> = _mapLockedToUserLocation.asStateFlow()

    private val _showInstructionsOverlay = MutableStateFlow(false)
    val showInstructionsOverlay: StateFlow<Boolean> = _showInstructionsOverlay.asStateFlow()

    private val _countdownString = MutableStateFlow("")
    val countdownString: StateFlow<String> = _countdownString.asStateFlow()

    private val _showExpiredGroupDialog = MutableStateFlow(false)
    val showExpiredGroupDialog: StateFlow<Boolean> = _showExpiredGroupDialog.asStateFlow()

    private val _expiredGroupDialogMessage = MutableStateFlow<String?>(null)
    val expiredGroupDialogMessage: StateFlow<String?> = _expiredGroupDialogMessage.asStateFlow()

    private val _showCustomMarkerInfoDialog = MutableStateFlow(false)
    val showCustomMarkerInfoDialog: StateFlow<Boolean> = _showCustomMarkerInfoDialog.asStateFlow()

    private val _showMapLoadingDialog = MutableStateFlow(true)
    val showMapLoadingDialog: StateFlow<Boolean> = _showMapLoadingDialog.asStateFlow()

    private val _toastEvent = MutableSharedFlow<String>()
    val toastEvent: SharedFlow<String> = _toastEvent.asSharedFlow()

    private val _navigationEvent = MutableSharedFlow<String>()
    val navigationEvent: SharedFlow<String> = _navigationEvent.asSharedFlow()

    private val _lastKnownCameraPosition = MutableStateFlow<CameraPosition?>(null)
    val lastKnownCameraPosition: StateFlow<CameraPosition?> = _lastKnownCameraPosition.asStateFlow()

    // NEW: Expose new chat messages count from GroupChatViewModel
    val newChatMessagesCount: StateFlow<Int> = groupChatViewModel.newMessagesCount

    data class MarkerDisplayInfo(
        val title: String,
        val timestamp: Long,
        val speed: Float?,
        val bearing: Float?,
        val profilePhotoUrl: String?,
        val latLng: com.google.android.gms.maps.model.LatLng?,
        val mapMarker: MapMarker? = null,
        val userId: String? = null
    )
    private val _currentMarkerInfo = MutableStateFlow<MarkerDisplayInfo?>(null)
    val currentMarkerInfo: StateFlow<MarkerDisplayInfo?> = _currentMarkerInfo.asStateFlow()


    init {
        Log.d("MainViewModel", "MainViewModel initialized.")

        // Listen to Auth state changes
        auth.addAuthStateListener { firebaseAuth ->
            _currentUserId.value = firebaseAuth.currentUser?.uid
            Log.d("MainViewModel", "Auth state changed, currentUserId: ${_currentUserId.value}")
            if (firebaseAuth.currentUser == null) {
                _currentUserModel.value = null
                _currentUserLocation.value = null
                _isInActiveGroup.value = false
                _activeGroup.value = null
                _otherMembersLocations.value = emptyList()
                _otherMembersProfiles.value = emptyMap()
                _mapMarkers.value = emptyList()
                _showMapLoadingDialog.value = false
                _showInstructionsOverlay.value = false
                _lastKnownCameraPosition.value = null
                // Ensure GroupChatViewModel also clears its state if MainViewModel is re-initialized due to logout
                // or if it needs specific cleanup on logout
                groupChatViewModel.markChatAsSeen() // Resets count on logout/re-init
            }
        }
        _currentUserId.value = auth.currentUser?.uid


        viewModelScope.launch {
            groupMonitorService.currentUserLocation.collectLatest { location ->
                _currentUserLocation.value = location
                Log.d("MainViewModel", "Collected currentUserLocation: ${location?.latitude}, ${location?.longitude}")

                if (location != null && !_showInstructionsOverlay.value && _showMapLoadingDialog.value) {
                    _showMapLoadingDialog.value = false
                    Log.d("MainViewModel", "Dismissed map loading dialog as current user location received.")
                }
            }
        }
        viewModelScope.launch {
            groupMonitorService.isInActiveGroup.collectLatest { isInGroup ->
                _isInActiveGroup.value = isInGroup
                Log.d("MainViewModel", "Collected isInActiveGroup: $isInGroup")
            }
        }
        viewModelScope.launch {
            groupMonitorService.activeGroup.collectLatest { group ->
                _activeGroup.value = group
                Log.d("MainViewModel", "Collected activeGroup: ${group?.groupName}")
            }
        }
        viewModelScope.launch {
            groupMonitorService.otherMembersLocations.collectLatest { locations ->
                _otherMembersLocations.value = locations
                Log.d("MainViewModel", "Collected otherMembersLocations: ${locations.size} members")
            }
        }
        viewModelScope.launch {
            groupMonitorService.otherMembersProfiles.collectLatest { profiles ->
                _otherMembersProfiles.value = profiles
                Log.d("MainViewModel", "Collected otherMembersProfiles: ${profiles.size} profiles")
            }
        }
        viewModelScope.launch {
            groupMonitorService.userMessage.collectLatest { message ->
                _userMessage.value = message
                if (message?.contains("has expired. You have been automatically removed.") == true) {
                    _expiredGroupDialogMessage.value = message
                    _showExpiredGroupDialog.value = true
                }
                Log.d("MainViewModel", "Collected userMessage: $message")
            }
        }

        viewModelScope.launch {
            markerMonitorService.mapMarkers.collectLatest { markers ->
                _mapMarkers.value = markers
                Log.d("MainViewModel", "Collected mapMarkers: ${markers.size} markers")
            }
        }

        viewModelScope.launch {
            _currentUserId.collectLatest { userId ->
                if (userId != null) {
                    val userResult = firestoreService.getUserProfile(userId)
                    userResult.onSuccess { userModel ->
                        _currentUserModel.value = userModel
                        Log.d("MainViewModel", "Initial currentUserModel loaded: ${userModel.displayName}")
                        if (userModel.mainInstructionsSeen != true) {
                            _showInstructionsOverlay.value = true
                        }
                    }.onFailure { e ->
                        Log.e("MainViewModel", "Failed to load current user model for $userId: ${e.message}")
                        _userMessage.value = "Error loading user profile: ${e.message}"
                    }
                } else {
                    _currentUserModel.value = null
                    _showMapLoadingDialog.value = false
                    Log.d("MainViewModel", "Dismissed map loading dialog as no authenticated user found.")
                }
            }
        }

        viewModelScope.launch {
            _activeGroup.collectLatest { group ->
                val endTime = group?.groupEndTimestamp
                if (endTime != null) {
                    while (true) {
                        val remaining = endTime - System.currentTimeMillis()
                        if (remaining > 0) {
                            val days = TimeUnit.MILLISECONDS.toDays(remaining)
                            val hours = TimeUnit.MILLISECONDS.toHours(remaining) % 24
                            val minutes = TimeUnit.MILLISECONDS.toMinutes(remaining) % 60
                            val seconds = TimeUnit.MILLISECONDS.toSeconds(remaining) % 60
                            _countdownString.value = String.format("%02d:%02d:%02d:%02d", days, hours, minutes, seconds)
                        } else {
                            _countdownString.value = "00:00:00:00"
                            break
                        }
                        kotlinx.coroutines.delay(1000L)
                    }
                } else {
                    _countdownString.value = ""
                }
            }
        }
    }

    // --- Actions/Event Handlers ---

    fun setLastKnownCameraPosition(cameraPosition: CameraPosition) {
        _lastKnownCameraPosition.value = cameraPosition
        Log.d("MainViewModel", "Last known camera position updated: ${cameraPosition.target.latitude}, ${cameraPosition.target.longitude}, Zoom: ${cameraPosition.zoom}")
    }

    fun toggleMapLock() {
        _mapLockedToUserLocation.value = !_mapLockedToUserLocation.value
        viewModelScope.launch {
            if (_mapLockedToUserLocation.value) {
                _toastEvent.emit("Map Locked to User Location")
            } else {
                _toastEvent.emit("Map Unlocked (can roam)")
            }
        }
    }

    fun showToastEvent(message: String) {
        viewModelScope.launch {
            _toastEvent.emit(message)
        }
    }

    fun dismissInstructionsOverlay() {
        viewModelScope.launch {
            _showInstructionsOverlay.value = false
            _currentUserModel.value?.let { user ->
                val updatedUser = user.copy(mainInstructionsSeen = true)
                firestoreService.saveUserProfile(updatedUser).onSuccess {
                    _currentUserModel.value = updatedUser
                    Log.d("MainViewModel", "mainInstructionsSeen updated to true in Firestore.")
                }.onFailure { e ->
                    Log.e("MainViewModel", "Failed to update mainInstructionsSeen: ${e.message}")
                    _userMessage.value = "Failed to save instruction preference: ${e.message}"
                }
            }
            if (_currentUserLocation.value == null && _showMapLoadingDialog.value) {
                _showMapLoadingDialog.value = false
                Log.d("MainViewModel", "Dismissed map loading dialog as instructions dismissed and no location yet.")
            }
        }
    }

    fun dismissExpiredGroupDialog() {
        _showExpiredGroupDialog.value = false
        _expiredGroupDialogMessage.value = null
        groupMonitorService.clearUserMessage()
    }

    fun showMarkerInfoDialog(markerInfo: MarkerDisplayInfo) {
        _currentMarkerInfo.value = markerInfo
        _showCustomMarkerInfoDialog.value = true
    }

    fun dismissMarkerInfoDialog() {
        _showCustomMarkerInfoDialog.value = false
        _currentMarkerInfo.value = null
    }

    fun clearUserMessage() {
        groupMonitorService.clearUserMessage()
    }

    // NEW: Function to trigger new message count check in GroupChatViewModel
    fun checkForNewChatMessagesForBadge() {
        groupChatViewModel.checkForNewMessagesForBadge()
    }

    // Navigation functions
    fun navigateToShutdown() {
        viewModelScope.launch { _navigationEvent.emit(NavRoutes.SHUTDOWN) }
    }

    fun navigateToUserSettings() {
        viewModelScope.launch { _navigationEvent.emit(NavRoutes.USER_SETTINGS) }
    }

    fun navigateToAddMapMarker() {
        viewModelScope.launch { _navigationEvent.emit(NavRoutes.ADD_MAP_MARKER) }
    }

    fun navigateToCreateGroup() {
        viewModelScope.launch { _navigationEvent.emit(NavRoutes.CREATE_GROUP) }
    }

    // UPDATED: Navigate to GEOFENCE_MANAGEMENT instead of old GEOFENCE route
    fun navigateToGeofence() {
        viewModelScope.launch { _navigationEvent.emit(NavRoutes.GEOFENCE_MANAGEMENT) }
    }

    fun navigateToTeamList() {
        viewModelScope.launch { _navigationEvent.emit(NavRoutes.TEAM_LIST) }
    }

    fun navigateToGroupsList() {
        viewModelScope.launch { _navigationEvent.emit(NavRoutes.GROUPS_LIST) }
    }

    fun navigateToNotifications() {
        viewModelScope.launch { _navigationEvent.emit(NavRoutes.NOTIFICATIONS) }
    }

    // NEW: Navigation function for GroupChatScreen
    fun navigateToGroupChat() {
        viewModelScope.launch { _navigationEvent.emit(NavRoutes.GROUP_CHAT) }
    }

    // NEW: Navigation function for GroupStatusScreen
    fun navigateToGroupStatus() {
        viewModelScope.launch { _navigationEvent.emit(NavRoutes.GROUP_STATUS) }
    }
}
