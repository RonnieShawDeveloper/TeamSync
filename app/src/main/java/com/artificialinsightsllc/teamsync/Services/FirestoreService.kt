// In file: app/src/main/java/com/artificialinsightsllc/teamsync/Services/FirestoreService.kt
package com.artificialinsightsllc.teamsync.Services

import android.util.Log
import com.artificialinsightsllc.teamsync.Models.Groups
import com.artificialinsightsllc.teamsync.Models.GroupMembers
import com.artificialinsightsllc.teamsync.Models.Locations
import com.artificialinsightsllc.teamsync.Models.UserModel
import com.artificialinsightsllc.teamsync.Models.MapMarker
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class FirestoreService(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    private val usersCollection = db.collection("users")
    private val groupsCollection = db.collection("groups")
    private val groupMembersCollection = db.collection("groupMembers")
    private val locationsHistoryCollection = db.collection("locationsHistory")
    private val currentLocationsCollection = db.collection("current_user_locations")
    private val mapMarkersCollection = db.collection("mapMarkers")

    // --- Existing User Profile Methods ---
    suspend fun getUserProfile(uid: String): Result<UserModel> {
        return try {
            val doc = usersCollection.document(uid).get().await()
            val user = doc.toObject(UserModel::class.java)
            if (user != null) Result.success(user)
            else Result.failure(Exception("User not found"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun saveUserProfile(user: UserModel): Result<Void?> {
        return try {
            usersCollection.document(user.authId).set(user).await()
            Result.success(null)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // --- Update user's selected active group ID ---
    suspend fun updateUserSelectedActiveGroup(userId: String, groupId: String?): Result<Void?> {
        return try {
            usersCollection.document(userId).update("selectedActiveGroupId", groupId).await()
            Result.success(null)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // --- Group Management Methods ---
    suspend fun createGroup(group: Groups): Result<Void?> {
        return try {
            groupsCollection.document(group.groupID).set(group).await()
            Result.success(null)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun addGroupMember(member: GroupMembers): Result<String> {
        Log.d("FirestoreService", "Adding group member: $member")
        return try {
            val docRef = groupMembersCollection.add(member).await()
            Result.success(docRef.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun saveGroupMember(member: GroupMembers): Result<Void?> {
        Log.d("FirestoreService", "Saving group member: $member")
        return try {
            groupMembersCollection.document(member.id).set(member).await()
            Result.success(null)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // --- RESTORED: Get all active group memberships for a specific group ---
    suspend fun getGroupMembershipsForGroup(groupId: String): Result<List<GroupMembers>> {
        return try {
            val querySnapshot = groupMembersCollection
                .whereEqualTo("groupId", groupId)
                .get()
                .await()
            val memberships = querySnapshot.documents.mapNotNull { doc ->
                doc.toObject(GroupMembers::class.java)?.copy(id = doc.id)
            }
            Result.success(memberships)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getGroupMembershipsForUser(userId: String): Result<List<GroupMembers>> {
        return try {
            val querySnapshot = groupMembersCollection.whereEqualTo("userId", userId).get().await()
            val memberships = querySnapshot.documents.mapNotNull { doc ->
                doc.toObject(GroupMembers::class.java)?.copy(id = doc.id)
            }
            Result.success(memberships)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getGroupsByIds(groupIds: List<String>): Result<List<Groups>> {
        if (groupIds.isEmpty()) {
            return Result.success(emptyList())
        }
        return try {
            val results = mutableListOf<Groups>()
            groupIds.chunked(10).forEach { chunk ->
                val querySnapshot = groupsCollection.whereIn("groupID", chunk).get().await()
                results.addAll(querySnapshot.documents.mapNotNull { doc ->
                    doc.toObject(Groups::class.java)
                })
            }
            Result.success(results)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getUserProfilesByIds(userIds: List<String>): Result<List<UserModel>> {
        if (userIds.isEmpty()) {
            return Result.success(emptyList())
        }
        return try {
            val results = mutableListOf<UserModel>()
            userIds.chunked(10).forEach { chunk ->
                val querySnapshot = usersCollection.whereIn("userId", chunk).get().await()
                results.addAll(querySnapshot.documents.mapNotNull { doc ->
                    doc.toObject(UserModel::class.java)
                })
            }
            Result.success(results)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getCurrentLocationsByIds(userIds: List<String>): Result<List<Locations>> {
        if (userIds.isEmpty()) {
            return Result.success(emptyList())
        }
        return try {
            val results = mutableListOf<Locations>()
            userIds.chunked(10).forEach { chunk ->
                val querySnapshot = currentLocationsCollection.whereIn("userId", chunk).get().await()
                results.addAll(querySnapshot.documents.mapNotNull { doc ->
                    doc.toObject(Locations::class.java)
                })
            }
            Result.success(results)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }


    suspend fun findGroupByAccessCode(accessCode: String, password: String?): Result<Groups?> {
        return try {
            var query = groupsCollection.whereEqualTo("groupAccessCode", accessCode)
            if (!password.isNullOrBlank()) {
                query = query.whereEqualTo("groupAccessPassword", password)
            }
            val querySnapshot = query.get().await()
            val group = querySnapshot.documents.firstOrNull()?.toObject(Groups::class.java)
            Result.success(group)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }


    // --- Location Management Methods ---
    suspend fun addLocationLog(location: Locations): Result<String> {
        return try {
            val docRef = locationsHistoryCollection.add(location).await()
            Result.success(docRef.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun saveCurrentLocation(location: Locations): Result<Void?> {
        return try {
            currentLocationsCollection.document(location.userId).set(location).await()
            Result.success(null)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // --- Update specific fields of an existing GroupMembers document ---
    suspend fun updateGroupMemberStatus(
        memberId: String,
        newLat: Double,
        newLon: Double,
        newUpdateTime: Long,
        newBatteryLevel: Int?,
        newBatteryChargingStatus: String?,
        newAppStatus: String?
    ): Result<Void?> {
        return try {
            val updates = mutableMapOf<String, Any>(
                "lastKnownLocationLat" to newLat,
                "lastKnownLocationLon" to newLon,
                "lastLocationUpdateTime" to newUpdateTime,
                "online" to true // Always set online when updating location/status
            )
            newBatteryLevel?.let { updates["batteryLevel"] = it }
            newBatteryChargingStatus?.let { updates["batteryChargingStatus"] = it }
            newAppStatus?.let { updates["appStatus"] = it }

            groupMembersCollection.document(memberId).update(updates).await()
            Result.success(null)
        } catch (e: Exception) {
            Log.e("FirestoreService", "Failed to update group member status for $memberId: ${e.message}")
            Result.failure(e)
        }
    }

    // NEW: Function to get location history for a user within a time range
    suspend fun getLocationsHistoryForUser(userId: String, startTime: Long, endTime: Long): Result<List<Locations>> {
        return try {
            val querySnapshot = locationsHistoryCollection
                .whereEqualTo("userId", userId)
                .whereGreaterThanOrEqualTo("timestamp", startTime)
                .whereLessThanOrEqualTo("timestamp", endTime)
                .orderBy("timestamp") // Order by timestamp to process chronologically
                .get()
                .await()
            val locations = querySnapshot.documents.mapNotNull { doc ->
                doc.toObject(Locations::class.java)
            }
            Log.d("FirestoreService", "Fetched ${locations.size} history locations for user $userId from $startTime to $endTime.")
            Result.success(locations)
        } catch (e: Exception) {
            Log.e("FirestoreService", "Failed to fetch location history for user $userId: ${e.message}")
            Result.failure(e)
        }
    }

    // --- Map Marker Management Methods ---
    suspend fun addMapMarker(marker: MapMarker): Result<String> {
        return try {
            val docRef = mapMarkersCollection.add(marker).await()
            Result.success(docRef.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getMapMarkersForGroup(groupId: String): Result<List<MapMarker>> {
        return try {
            val querySnapshot = mapMarkersCollection
                .whereEqualTo("groupId", groupId)
                .get()
                .await()
            val markers = querySnapshot.documents.mapNotNull { doc ->
                doc.toObject(MapMarker::class.java)?.copy(id = doc.id)
            }
            Result.success(markers)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
