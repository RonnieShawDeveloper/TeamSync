// In file: app/src/main/java/com/artificialinsightsllc/teamsync/Services/MarkerMonitorService.kt
package com.artificialinsightsllc.teamsync.Services

import android.util.Log
import com.artificialinsightsllc.teamsync.Models.MapMarker
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.cancel

class MarkerMonitorService(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    private val monitorScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _mapMarkers = MutableStateFlow<List<MapMarker>>(emptyList())
    val mapMarkers: StateFlow<List<MapMarker>> = _mapMarkers.asStateFlow()

    private var markersListener: ListenerRegistration? = null
    private var currentGroupId: String? = null

    /**
     * Starts listening for map markers for a specific group.
     * If already listening to a group, it stops the previous listener first.
     * @param groupId The ID of the group to monitor markers for.
     */
    fun startMonitoringMarkers(groupId: String) {
        Log.d("MarkerMonitorService", "startMonitoringMarkers called for groupId: $groupId") // ADD THIS LOG
        if (currentGroupId == groupId) {
            Log.d("MarkerMonitorService", "Already monitoring markers for group $groupId. No change needed.")
            return
        }

        stopMonitoringMarkers() // Stop any existing listener
        currentGroupId = groupId

        Log.d("MarkerMonitorService", "Starting to monitor markers for group: $groupId")
        markersListener = db.collection("mapMarkers")
            .whereEqualTo("groupId", groupId)
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    Log.w("MarkerMonitorService", "Listen for map markers failed.", e)
                    _mapMarkers.value = emptyList()
                    return@addSnapshotListener
                }

                if (snapshots != null) {
                    val markers = snapshots.documents.mapNotNull { doc ->
                        doc.toObject(MapMarker::class.java)?.copy(id = doc.id)
                    }
                    _mapMarkers.value = markers
                    Log.d("MarkerMonitorService", "Updated ${markers.size} map markers for group $groupId.")
                } else {
                    _mapMarkers.value = emptyList()
                    Log.d("MarkerMonitorService", "No map markers found for group $groupId.")
                }
            }
    }

    /**
     * Stops monitoring map markers and clears the current list.
     */
    fun stopMonitoringMarkers() {
        markersListener?.remove()
        markersListener = null
        currentGroupId = null
        _mapMarkers.value = emptyList()
        Log.d("MarkerMonitorService", "Stopped monitoring map markers.")
    }

    /**
     * Shuts down the coroutine scope and stops all monitoring.
     */
    fun shutdown() {
        stopMonitoringMarkers()
        monitorScope.cancel()
        Log.d("MarkerMonitorService", "MarkerMonitorService shutdown.")
    }
}
