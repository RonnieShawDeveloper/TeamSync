// In file: app/src/main/java/com/artificialinsightsllc/teamsync/Services/FirestoreService.kt
package com.artificialinsightsllc.teamsync.Services

import com.artificialinsightsllc.teamsync.Models.Groups
import com.artificialinsightsllc.teamsync.Models.GroupMembers
import com.artificialinsightsllc.teamsync.Models.Locations
import com.artificialinsightsllc.teamsync.Models.UserModel
import com.artificialinsightsllc.teamsync.Models.MapMarker // NEW IMPORT
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
    private val mapMarkersCollection = db.collection("mapMarkers") // NEW: Collection for map markers

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

    // --- NEW: Update user's selected active group ID ---
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
        return try {
            val docRef = groupMembersCollection.add(member).await()
            Result.success(docRef.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun saveGroupMember(member: GroupMembers): Result<Void?> {
        return try {
            groupMembersCollection.document(member.id).set(member).await()
            Result.success(null)
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

    // NEW: Get all active group memberships for a specific group
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

    suspend fun getGroupsByIds(groupIds: List<String>): Result<List<Groups>> {
        if (groupIds.isEmpty()) {
            return Result.success(emptyList())
        }
        return try {
            val results = mutableListOf<Groups>()
            // Firestore 'whereIn' clause has a limit of 10 items
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

    // NEW: Get user profiles by a list of user IDs
    suspend fun getUserProfilesByIds(userIds: List<String>): Result<List<UserModel>> {
        if (userIds.isEmpty()) {
            return Result.success(emptyList())
        }
        return try {
            val results = mutableListOf<UserModel>()
            // Firestore 'whereIn' clause has a limit of 10 items
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

    // NEW: Get current locations by a list of user IDs
    suspend fun getCurrentLocationsByIds(userIds: List<String>): Result<List<Locations>> {
        if (userIds.isEmpty()) {
            return Result.success(emptyList())
        }
        return try {
            val results = mutableListOf<Locations>()
            // Firestore 'whereIn' clause has a limit of 10 items
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


    // --- NEW: Find a group by access code and optional password ---
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

    // NEW: Map Marker Management Methods
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
