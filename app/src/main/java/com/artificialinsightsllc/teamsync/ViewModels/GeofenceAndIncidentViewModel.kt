// In file: app/src/main/java/com/artificialinsightsllc/teamsync/ViewModels/GeofenceAndIncidentViewModel.kt
package com.artificialinsightsllc.teamsync.ViewModels

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.artificialinsightsllc.teamsync.Models.GeofenceZone
import com.artificialinsightsllc.teamsync.Models.GeofenceRule
import com.artificialinsightsllc.teamsync.Models.GeofenceAssignment
import com.artificialinsightsllc.teamsync.Models.GeofencePackage
import com.artificialinsightsllc.teamsync.Models.Incident
import com.artificialinsightsllc.teamsync.Models.IncidentLogEntry
import com.artificialinsightsllc.teamsync.Models.IncidentPersonnelStatus
import com.artificialinsightsllc.teamsync.Models.GroupSettings
import com.artificialinsightsllc.teamsync.Services.FirestoreService
import com.artificialinsightsllc.teamsync.Services.GroupMonitorService
import com.artificialinsightsllc.teamsync.TeamSyncApplication
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.UUID

class GeofenceAndIncidentViewModel(application: Application) : AndroidViewModel(application) {

    // Dependencies
    internal val firestoreService: FirestoreService = FirestoreService() // Made internal
    internal val auth: FirebaseAuth = FirebaseAuth.getInstance() // Made internal
    internal val groupMonitorService: GroupMonitorService = (application as TeamSyncApplication).groupMonitorService // Made internal

    // UI State Flows (Geofence Zones)
    private val _activeGeofenceZones = MutableStateFlow<List<GeofenceZone>>(emptyList())
    val activeGeofenceZones: StateFlow<List<GeofenceZone>> = _activeGeofenceZones.asStateFlow()

    private val _selectedGeofenceZone = MutableStateFlow<GeofenceZone?>(null)
    val selectedGeofenceZone: StateFlow<GeofenceZone?> = _selectedGeofenceZone.asStateFlow()

    private val _geofenceRulesForSelectedZone = MutableStateFlow<List<GeofenceRule>>(emptyList())
    val geofenceRulesForSelectedZone: StateFlow<List<GeofenceRule>> = _geofenceRulesForSelectedZone.asStateFlow()

    private val _geofenceAssignmentsForSelectedZone = MutableStateFlow<List<GeofenceAssignment>>(emptyList())
    val geofenceAssignmentsForSelectedZone: StateFlow<List<GeofenceAssignment>> = _geofenceAssignmentsForSelectedZone.asStateFlow()

    // UI State Flows (Incidents)
    private val _activeIncidents = MutableStateFlow<List<Incident>>(emptyList())
    val activeIncidents: StateFlow<List<Incident>> = _activeIncidents.asStateFlow()

    private val _selectedIncident = MutableStateFlow<Incident?>(null)
    val selectedIncident: StateFlow<Incident?> = _selectedIncident.asStateFlow()

    private val _incidentLogEntries = MutableStateFlow<List<IncidentLogEntry>>(emptyList())
    val incidentLogEntries: StateFlow<List<IncidentLogEntry>> = _incidentLogEntries.asStateFlow()

    // UI State Flows (Packages & Settings)
    private val _geofencePackages = MutableStateFlow<List<GeofencePackage>>(emptyList())
    val geofencePackages: StateFlow<List<GeofencePackage>> = _geofencePackages.asStateFlow()

    private val _groupSettings = MutableStateFlow<GroupSettings?>(null)
    val groupSettings: StateFlow<GroupSettings?> = _groupSettings.asStateFlow()

    // Loading/Error States
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    init {
        Log.d("GeofenceAndIncidentVM", "ViewModel initialized.")
        setupDataListeners()
    }

    private fun setupDataListeners() {
        viewModelScope.launch {
            // Listen for active group changes to fetch relevant geofences, incidents, and settings
            groupMonitorService.activeGroup.collectLatest { activeGroup ->
                val groupId = activeGroup?.groupID
                if (groupId != null) {
                    Log.d("GeofenceAndIncidentVM", "Active group changed to $groupId. Fetching group-specific data.")
                    loadGeofenceZonesForGroup(groupId)
                    loadActiveIncidentsForGroup(groupId)
                    loadGroupSettings(groupId)
                    loadGeofencePackages(auth.currentUser?.uid) // Load packages for the current user
                } else {
                    Log.d("GeofenceAndIncidentVM", "No active group. Clearing geofence/incident data.")
                    _activeGeofenceZones.value = emptyList()
                    _activeIncidents.value = emptyList()
                    _groupSettings.value = null
                    _selectedGeofenceZone.value = null
                    _selectedIncident.value = null
                    _geofenceRulesForSelectedZone.value = emptyList()
                    _geofenceAssignmentsForSelectedZone.value = emptyList()
                    _incidentLogEntries.value = emptyList()
                    _geofencePackages.value = emptyList()
                }
            }
        }

        // Listen for changes to selected GeofenceZone to load its rules and assignments
        viewModelScope.launch {
            _selectedGeofenceZone.collectLatest { zone ->
                if (zone != null) {
                    Log.d("GeofenceAndIncidentVM", "Selected GeofenceZone: ${zone.id}. Loading rules and assignments.")
                    loadGeofenceRulesForZone(zone.id)
                    loadGeofenceAssignmentsForZone(zone.id)
                } else {
                    _geofenceRulesForSelectedZone.value = emptyList()
                    _geofenceAssignmentsForSelectedZone.value = emptyList()
                }
            }
        }

        // Listen for changes to selected Incident to load its log entries
        viewModelScope.launch {
            _selectedIncident.collectLatest { incident ->
                if (incident != null) {
                    Log.d("GeofenceAndIncidentVM", "Selected Incident: ${incident.id}. Loading log entries.")
                    loadIncidentLogEntries(incident.id)
                } else {
                    _incidentLogEntries.value = emptyList()
                }
            }
        }
    }

    // --- GeofenceZone Operations ---

    private fun loadGeofenceZonesForGroup(groupId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            val result = firestoreService.getGeofenceZonesForGroup(groupId)
            result.onSuccess { zones ->
                _activeGeofenceZones.value = zones
                Log.d("GeofenceAndIncidentVM", "Loaded ${zones.size} geofence zones for group $groupId.")
            }.onFailure { e ->
                _errorMessage.value = "Failed to load geofence zones: ${e.message}"
                Log.e("GeofenceAndIncidentVM", "Error loading geofence zones: ${e.message}", e)
            }
            _isLoading.value = false
        }
    }

    // CHANGED: Return type from Unit to Result<String>
    // CHANGED: Removed direct isLoading manipulation here, let the calling site handle it if it needs to wrap this call
    suspend fun addGeofenceZone(zone: GeofenceZone): Result<String> {
        val result = firestoreService.addGeofenceZone(zone)
        result.onSuccess { docId ->
            Log.d("GeofenceAndIncidentVM", "Added geofence zone with ID: $docId")
            // Reload zones to reflect changes
            groupMonitorService.activeGroup.value?.groupID?.let { loadGeofenceZonesForGroup(it) }
        }.onFailure { e ->
            Log.e("GeofenceAndIncidentVM", "Error adding geofence zone: ${e.message}", e)
        }
        return result // Return the result
    }

    fun updateGeofenceZone(zone: GeofenceZone) {
        viewModelScope.launch {
            _isLoading.value = true
            val result = firestoreService.updateGeofenceZone(zone)
            result.onSuccess {
                Log.d("GeofenceAndIncidentVM", "Updated geofence zone: ${zone.id}")
                // Reload zones to reflect changes
                groupMonitorService.activeGroup.value?.groupID?.let { loadGeofenceZonesForGroup(it) }
            }.onFailure { e ->
                _errorMessage.value = "Failed to update geofence zone: ${e.message}"
                Log.e("GeofenceAndIncidentVM", "Error updating geofence zone: ${e.message}", e)
            }
            _isLoading.value = false
        }
    }

    fun deleteGeofenceZone(zoneId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            val result = firestoreService.deleteGeofenceZone(zoneId)
            result.onSuccess {
                Log.d("GeofenceAndIncidentVM", "Deleted geofence zone: $zoneId")
                // Reload zones to reflect changes
                groupMonitorService.activeGroup.value?.groupID?.let { loadGeofenceZonesForGroup(it) }
            }.onFailure { e ->
                _errorMessage.value = "Failed to delete geofence zone: ${e.message}"
                Log.e("GeofenceAndIncidentVM", "Error deleting geofence zone: ${e.message}", e)
            }
            _isLoading.value = false
        }
    }

    fun setSelectedGeofenceZone(zone: GeofenceZone?) {
        _selectedGeofenceZone.value = zone
    }

    // --- GeofenceRule Operations ---

    private fun loadGeofenceRulesForZone(geofenceZoneId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            val result = firestoreService.getGeofenceRulesForZone(geofenceZoneId)
            result.onSuccess { rules ->
                _geofenceRulesForSelectedZone.value = rules
                Log.d("GeofenceAndIncidentVM", "Loaded ${rules.size} rules for zone $geofenceZoneId.")
            }.onFailure { e ->
                _errorMessage.value = "Failed to load geofence rules: ${e.message}"
                Log.e("GeofenceAndIncidentVM", "Error loading geofence rules: ${e.message}", e)
            }
            _isLoading.value = false
        }
    }

    fun addGeofenceRule(rule: GeofenceRule) {
        viewModelScope.launch {
            _isLoading.value = true
            val result = firestoreService.addGeofenceRule(rule)
            result.onSuccess { docId ->
                Log.d("GeofenceAndIncidentVM", "Added geofence rule with ID: $docId")
                _selectedGeofenceZone.value?.id?.let { loadGeofenceRulesForZone(it) } // Reload rules
            }.onFailure { e ->
                _errorMessage.value = "Failed to add geofence rule: ${e.message}"
                Log.e("GeofenceAndIncidentVM", "Error adding geofence rule: ${e.message}", e)
            }
            _isLoading.value = false
        }
    }

    fun updateGeofenceRule(rule: GeofenceRule) {
        viewModelScope.launch {
            _isLoading.value = true
            val result = firestoreService.updateGeofenceRule(rule)
            result.onSuccess {
                Log.d("GeofenceAndIncidentVM", "Updated geofence rule: ${rule.id}")
                _selectedGeofenceZone.value?.id?.let { loadGeofenceRulesForZone(it) } // Reload rules
            }.onFailure { e ->
                _errorMessage.value = "Failed to update geofence rule: ${e.message}"
                Log.e("GeofenceAndIncidentVM", "Error updating geofence rule: ${e.message}", e)
            }
            _isLoading.value = false
        }
    }

    fun deleteGeofenceRule(ruleId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            val result = firestoreService.deleteGeofenceRule(ruleId)
            result.onSuccess {
                Log.d("GeofenceAndIncidentVM", "Deleted geofence rule: $ruleId")
                _selectedGeofenceZone.value?.id?.let { loadGeofenceRulesForZone(it) } // Reload rules
            }.onFailure { e ->
                _errorMessage.value = "Failed to delete geofence rule: ${e.message}"
                Log.e("GeofenceAndIncidentVM", "Error deleting geofence rule: ${e.message}", e)
            }
            _isLoading.value = false
        }
    }

    // --- GeofenceAssignment Operations ---

    private fun loadGeofenceAssignmentsForZone(geofenceZoneId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            val result = firestoreService.getGeofenceAssignmentsForZone(geofenceZoneId)
            result.onSuccess { assignments ->
                _geofenceAssignmentsForSelectedZone.value = assignments
                Log.d("GeofenceAndIncidentVM", "Loaded ${assignments.size} assignments for zone $geofenceZoneId.")
            }.onFailure { e ->
                _errorMessage.value = "Failed to load geofence assignments: ${e.message}"
                Log.e("GeofenceAndIncidentVM", "Error loading geofence assignments: ${e.message}", e)
            }
            _isLoading.value = false
        }
    }

    fun addGeofenceAssignment(assignment: GeofenceAssignment) {
        viewModelScope.launch {
            _isLoading.value = true
            val result = firestoreService.addGeofenceAssignment(assignment)
            result.onSuccess { docId ->
                Log.d("GeofenceAndIncidentVM", "Added geofence assignment with ID: $docId")
                _selectedGeofenceZone.value?.id?.let { loadGeofenceAssignmentsForZone(it) } // Reload assignments
            }.onFailure { e ->
                _errorMessage.value = "Failed to add geofence assignment: ${e.message}"
                Log.e("GeofenceAndIncidentVM", "Error adding geofence assignment: ${e.message}", e)
            }
            _isLoading.value = false
        }
    }

    fun updateGeofenceAssignment(assignment: GeofenceAssignment) {
        viewModelScope.launch {
            _isLoading.value = true
            val result = firestoreService.updateGeofenceAssignment(assignment)
            result.onSuccess {
                Log.d("GeofenceAndIncidentVM", "Updated geofence assignment: ${assignment.id}")
                _selectedGeofenceZone.value?.id?.let { loadGeofenceAssignmentsForZone(it) } // Reload assignments
            }.onFailure { e ->
                _errorMessage.value = "Failed to update geofence assignment: ${e.message}"
                Log.e("GeofenceAndIncidentVM", "Error updating geofence assignment: ${e.message}", e)
            }
            _isLoading.value = false
        }
    }

    fun deleteGeofenceAssignment(assignmentId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            val result = firestoreService.deleteGeofenceAssignment(assignmentId)
            result.onSuccess {
                Log.d("GeofenceAndIncidentVM", "Deleted geofence assignment: $assignmentId")
                _selectedGeofenceZone.value?.id?.let { loadGeofenceAssignmentsForZone(it) } // Reload assignments
            }.onFailure { e ->
                _errorMessage.value = "Failed to delete geofence assignment: ${e.message}"
                Log.e("GeofenceAndIncidentVM", "Error deleting geofence assignment: ${e.message}", e)
            }
            _isLoading.value = false
        }
    }

    // --- GeofencePackage Operations ---

    private fun loadGeofencePackages(userId: String?) {
        if (userId == null) {
            _geofencePackages.value = emptyList()
            return
        }
        viewModelScope.launch {
            _isLoading.value = true
            val result = firestoreService.getGeofencePackagesByCreator(userId)
            result.onSuccess { packages ->
                _geofencePackages.value = packages
                Log.d("GeofenceAndIncidentVM", "Loaded ${packages.size} geofence packages for user $userId.")
            }.onFailure { e ->
                _errorMessage.value = "Failed to load geofence packages: ${e.message}"
                Log.e("GeofenceAndIncidentVM", "Error loading geofence packages: ${e.message}", e)
            }
            _isLoading.value = false
        }
    }

    fun addGeofencePackage(pkg: GeofencePackage) {
        viewModelScope.launch {
            _isLoading.value = true
            val result = firestoreService.addGeofencePackage(pkg)
            result.onSuccess { docId ->
                Log.d("GeofenceAndIncidentVM", "Added geofence package with ID: $docId")
                loadGeofencePackages(auth.currentUser?.uid) // Reload packages
            }.onFailure { e ->
                _errorMessage.value = "Failed to add geofence package: ${e.message}"
                Log.e("GeofenceAndIncidentVM", "Error adding geofence package: ${e.message}", e)
            }
            _isLoading.value = false
        }
    }

    fun applyGeofencePackageToGroup(packageId: String) {
        val currentGroupId = groupMonitorService.activeGroup.value?.groupID
        val currentUserId = auth.currentUser?.uid
        if (currentGroupId == null || currentUserId == null) {
            _errorMessage.value = "No active group or user logged in to apply package."
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            // TODO: Implement the actual logic to:
            // 1. Fetch the GeofencePackage
            // 2. Parse its zoneDataJson
            // 3. Delete/deactivate existing geofences/rules/assignments for the current group (optional, based on desired behavior)
            // 4. Create new GeofenceZone, GeofenceRule, GeofenceAssignment documents in Firestore for the current group.
            //    Crucially, new IDs must be generated, and parentZoneId/geofenceZoneId references updated.
            _errorMessage.value = "Applying package functionality is not yet implemented."
            Log.w("GeofenceAndIncidentVM", "Applying package functionality is a TODO.")
            _isLoading.value = false
        }
    }

    // --- Incident Operations ---

    private fun loadActiveIncidentsForGroup(groupId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            val result = firestoreService.getActiveIncidentsForGroup(groupId)
            result.onSuccess { incidents ->
                _activeIncidents.value = incidents
                Log.d("GeofenceAndIncidentVM", "Loaded ${incidents.size} active incidents for group $groupId.")
            }.onFailure { e ->
                _errorMessage.value = "Failed to load active incidents: ${e.message}"
                Log.e("GeofenceAndIncidentVM", "Error loading active incidents: ${e.message}", e)
            }
            _isLoading.value = false
        }
    }

    // CHANGED: Return type from Unit to Result<String>
    // CHANGED: Removed direct isLoading manipulation here, let the calling site handle it if it needs to wrap this call
    suspend fun addIncident(incident: Incident): Result<String> {
        val result = firestoreService.addIncident(incident)
        result.onSuccess { docId ->
            Log.d("GeofenceAndIncidentVM", "Added incident with ID: $docId")
            groupMonitorService.activeGroup.value?.groupID?.let { loadActiveIncidentsForGroup(it) }
        }.onFailure { e ->
            Log.e("GeofenceAndIncidentVM", "Error adding incident: ${e.message}", e)
        }
        return result // Return the result
    }

    fun updateIncident(incident: Incident) {
        viewModelScope.launch {
            _isLoading.value = true
            val result = firestoreService.updateIncident(incident)
            result.onSuccess {
                Log.d("GeofenceAndIncidentVM", "Updated incident: ${incident.id}")
                groupMonitorService.activeGroup.value?.groupID?.let { loadActiveIncidentsForGroup(it) }
            }.onFailure { e ->
                _errorMessage.value = "Failed to update incident: ${e.message}"
                Log.e("GeofenceAndIncidentVM", "Error updating incident: ${e.message}", e)
            }
            _isLoading.value = false
        }
    }

    fun updateIncidentPersonnelStatus(incidentId: String, userId: String, newStatus: Map<String, Any?>) {
        viewModelScope.launch {
            _isLoading.value = true
            val result = firestoreService.updateIncidentPersonnelStatus(incidentId, userId, newStatus)
            result.onSuccess {
                Log.d("GeofenceAndIncidentVM", "Updated personnel status for incident $incidentId, user $userId.")
                groupMonitorService.activeGroup.value?.groupID?.let { loadActiveIncidentsForGroup(it) } // Reload to reflect
            }.onFailure { e ->
                _errorMessage.value = "Failed to update personnel status: ${e.message}"
                Log.e("GeofenceAndIncidentVM", "Error updating personnel status: ${e.message}", e)
            }
            _isLoading.value = false
        }
    }

    fun deleteIncident(incidentId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            val result = firestoreService.deleteIncident(incidentId)
            result.onSuccess {
                Log.d("GeofenceAndIncidentVM", "Deleted incident: $incidentId")
                groupMonitorService.activeGroup.value?.groupID?.let { loadActiveIncidentsForGroup(it) }
            }.onFailure { e ->
                _errorMessage.value = "Failed to delete incident: ${e.message}"
                Log.e("GeofenceAndIncidentVM", "Error deleting incident: ${e.message}", e)
            }
            _isLoading.value = false
        }
    }

    fun setSelectedIncident(incident: Incident?) {
        _selectedIncident.value = incident
    }

    // --- IncidentLogEntry Operations ---

    private fun loadIncidentLogEntries(incidentId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            val result = firestoreService.getIncidentLogEntries(incidentId)
            result.onSuccess { logs ->
                _incidentLogEntries.value = logs
                Log.d("GeofenceAndIncidentVM", "Loaded ${logs.size} log entries for incident $incidentId.")
            }.onFailure { e ->
                _errorMessage.value = "Failed to load incident log entries: ${e.message}"
                Log.e("GeofenceAndIncidentVM", "Error loading incident log entries: ${e.message}", e)
            }
            _isLoading.value = false
        }
    }

    // CHANGED: Return type from Unit to Result<String>
    // CHANGED: Removed direct isLoading manipulation here, let the calling site handle it if it needs to wrap this call
    suspend fun addIncidentLogEntry(logEntry: IncidentLogEntry): Result<String> {
        val result = firestoreService.addIncidentLogEntry(logEntry)
        result.onSuccess { docId ->
            Log.d("GeofenceAndIncidentVM", "Added incident log entry with ID: $docId")
            _selectedIncident.value?.id?.let { loadIncidentLogEntries(it) } // Reload logs
        }.onFailure { e ->
            Log.e("GeofenceAndIncidentVM", "Error adding incident log entry: ${e.message}", e)
        }
        return result // Return the result
    }

    // --- GroupSettings Operations ---

    private fun loadGroupSettings(groupId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            val result = firestoreService.getGroupSettings(groupId)
            result.onSuccess { settings ->
                _groupSettings.value = settings
                Log.d("GeofenceAndIncidentVM", "Loaded group settings for group $groupId.")
            }.onFailure { e ->
                _errorMessage.value = "Failed to load group settings: ${e.message}"
                Log.e("GeofenceAndIncidentVM", "Error loading group settings: ${e.message}", e)
            }
            _isLoading.value = false
        }
    }

    fun saveGroupSettings(settings: GroupSettings) {
        viewModelScope.launch {
            _isLoading.value = true
            val result = firestoreService.saveGroupSettings(settings)
            result.onSuccess {
                Log.d("GeofenceAndIncidentVM", "Saved group settings for group ${settings.groupId}.")
                // No explicit reload needed, as listener will trigger update to _groupSettings
            }.onFailure { e ->
                _errorMessage.value = "Failed to save group settings: ${e.message}"
                Log.e("GeofenceAndIncidentVM", "Error saving group settings: ${e.message}", e)
            }
            _isLoading.value = false
        }
    }

    // --- General UI helpers ---
    // NEW: Public function to set error message from UI
    fun setErrorMessage(message: String?) {
        _errorMessage.value = message
    }

    fun clearErrorMessage() {
        _errorMessage.value = null
    }

    override fun onCleared() {
        super.onCleared()
        Log.d("GeofenceAndIncidentVM", "ViewModel onCleared.")
        // Listeners managed by GroupMonitorService, no explicit listener removal needed here.
    }
}
