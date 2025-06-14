// In file: app/src/main/java/com/artificialinsightsllc/teamsync/Services/FirestoreService.kt
package com.artificialinsightsllc.teamsync.Services

import android.util.Log
import com.artificialinsightsllc.teamsync.Models.Groups
import com.artificialinsightsllc.teamsync.Models.GroupMembers
import com.artificialinsightsllc.teamsync.Models.Locations
import com.artificialinsightsllc.teamsync.Models.UserModel
import com.artificialinsightsllc.teamsync.Models.MapMarker
import com.artificialinsightsllc.teamsync.Models.ChatMessage
import com.artificialinsightsllc.teamsync.Models.Comment
import com.artificialinsightsllc.teamsync.Models.GeofenceZone // NEW
import com.artificialinsightsllc.teamsync.Models.GeofenceRule // NEW
import com.artificialinsightsllc.teamsync.Models.GeofenceAssignment // NEW
import com.artificialinsightsllc.teamsync.Models.GeofencePackage // NEW
import com.artificialinsightsllc.teamsync.Models.Incident // NEW
import com.artificialinsightsllc.teamsync.Models.IncidentLogEntry // NEW
import com.artificialinsightsllc.teamsync.Models.GroupSettings // NEW
import com.artificialinsightsllc.teamsync.Models.IncidentPersonnelStatus
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.FieldValue // NEW: for updating map fields
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
    private val chatMessagesCollection = db.collection("chatMessages")
    private val commentsCollection = db.collection("comments")
    private val geofenceZonesCollection = db.collection("geofenceZones") // NEW
    private val geofenceRulesCollection = db.collection("geofenceRules") // NEW
    private val geofenceAssignmentsCollection = db.collection("geofenceAssignments") // NEW
    private val geofencePackagesCollection = db.collection("geofencePackages") // NEW
    private val incidentsCollection = db.collection("incidents") // NEW
    private val incidentLogsCollection = db.collection("incidentLogs") // NEW
    private val groupSettingsCollection = db.collection("groupSettings") // NEW

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

    // --- Chat Message Methods ---
    suspend fun addChatMessage(chatMessage: ChatMessage): Result<String> {
        return try {
            // Ensure message ID is set or generated
            val docRef = if (chatMessage.id.isNotEmpty()) {
                chatMessagesCollection.document(chatMessage.id).set(chatMessage).await()
                chatMessagesCollection.document(chatMessage.id)
            } else {
                chatMessagesCollection.add(chatMessage).await()
            }
            Result.success(docRef.id)
        } catch (e: Exception) {
            Log.e("FirestoreService", "Failed to add chat message: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun getChatMessages(
        groupId: String,
        limit: Long,
        startAfterTimestamp: Long? = null,
        startAfterDocumentId: String? = null // For consistent pagination with startAfter
    ): Result<List<ChatMessage>> {
        return try {
            var query: Query = chatMessagesCollection
                .whereEqualTo("groupId", groupId)
                .orderBy("timestamp", Query.Direction.DESCENDING) // Order by latest first
                .limit(limit)

            if (startAfterTimestamp != null && startAfterDocumentId != null) {
                // To support consistent pagination (load more older items)
                val lastDocSnapshot = chatMessagesCollection.document(startAfterDocumentId).get().await()
                if (lastDocSnapshot.exists()) {
                    query = query.startAfter(lastDocSnapshot)
                } else {
                    Log.w("FirestoreService", "Last document for pagination not found: $startAfterDocumentId. Fetching from timestamp only.")
                    // Fallback to timestamp if document not found
                    query = query.startAfter(startAfterTimestamp)
                }
            } else if (startAfterTimestamp != null) {
                // Fallback for initial pull-to-refresh or simpler pagination from a timestamp
                query = query.startAfter(startAfterTimestamp)
            }

            val querySnapshot = query.get().await()
            val messages = querySnapshot.documents.mapNotNull { doc ->
                doc.toObject(ChatMessage::class.java)?.copy(id = doc.id)
            }
            Result.success(messages)
        } catch (e: Exception) {
            Log.e("FirestoreService", "Failed to get chat messages: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun getNewChatMessages(
        groupId: String,
        startAtTimestamp: Long // Get messages *after* this timestamp
    ): Result<List<ChatMessage>> {
        return try {
            val querySnapshot = chatMessagesCollection
                .whereEqualTo("groupId", groupId)
                .whereGreaterThan("timestamp", startAtTimestamp)
                .orderBy("timestamp", Query.Direction.ASCENDING) // Order by oldest first for new messages
                .get()
                .await()
            val messages = querySnapshot.documents.mapNotNull { doc ->
                doc.toObject(ChatMessage::class.java)?.copy(id = doc.id)
            }
            Result.success(messages)
        } catch (e: Exception) {
            Log.e("FirestoreService", "Failed to get new chat messages: ${e.message}", e)
            Result.failure(e)
        }
    }


    suspend fun updateChatMessageLikes(messageId: String, newLikes: List<String>): Result<Void?> {
        return try {
            chatMessagesCollection.document(messageId).update("likes", newLikes).await()
            Result.success(null)
        } catch (e: Exception) {
            Log.e("FirestoreService", "Failed to update chat message likes: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun incrementChatMessageCommentCount(messageId: String): Result<Void?> {
        return try {
            // Get current count, increment, then update. Transaction would be safer in high concurrency.
            db.runTransaction { transaction ->
                val docRef = chatMessagesCollection.document(messageId)
                val snapshot = transaction.get(docRef)
                val newCommentCount = (snapshot.getLong("commentCount") ?: 0) + 1
                transaction.update(docRef, "commentCount", newCommentCount)
                transaction.update(docRef, "lastCommentTimestamp", System.currentTimeMillis())
                null
            }.await()
            Result.success(null)
        } catch (e: Exception) {
            Log.e("FirestoreService", "Failed to increment comment count: ${e.message}", e)
            Result.failure(e)
        }
    }

    // --- NEW: Chat message deletion methods ---
    suspend fun deleteChatMessage(messageId: String): Result<Void?> {
        return try {
            chatMessagesCollection.document(messageId).delete().await()
            Log.d("FirestoreService", "Chat message $messageId deleted successfully.")
            Result.success(null)
        } catch (e: Exception) {
            Log.e("FirestoreService", "Failed to delete chat message $messageId: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun deleteAllCommentsForMessage(messageId: String): Result<Void?> {
        return try {
            val commentsToDelete = commentsCollection.whereEqualTo("messageId", messageId).get().await()
            val batch = db.batch()
            for (document in commentsToDelete.documents) {
                batch.delete(document.reference)
            }
            batch.commit().await()
            Log.d("FirestoreService", "Deleted ${commentsToDelete.size()} comments for message $messageId.")
            Result.success(null)
        } catch (e: Exception) {
            Log.e("FirestoreService", "Failed to delete comments for message $messageId: ${e.message}", e)
            Result.failure(e)
        }
    }


    // --- NEW: Comment Methods ---
    suspend fun addComment(comment: Comment): Result<String> {
        return try {
            val docRef = commentsCollection.add(comment).await()
            Result.success(docRef.id)
        } catch (e: Exception) {
            Log.e("FirestoreService", "Failed to add comment: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun getCommentsForMessage(
        messageId: String,
        limit: Long,
        startAfterTimestamp: Long? = null,
        startAfterDocumentId: String? = null
    ): Result<List<Comment>> {
        return try {
            var query: Query = commentsCollection
                .whereEqualTo("messageId", messageId)
                .orderBy("timestamp", Query.Direction.ASCENDING) // Comments usually sorted oldest first
                .limit(limit)

            if (startAfterTimestamp != null && startAfterDocumentId != null) {
                val lastDocSnapshot = commentsCollection.document(startAfterDocumentId).get().await()
                if (lastDocSnapshot.exists()) {
                    query = query.startAfter(lastDocSnapshot)
                } else {
                    Log.w("FirestoreService", "Last comment document for pagination not found: $startAfterDocumentId. Fetching from timestamp only.")
                    query = query.startAfter(startAfterTimestamp)
                }
            } else if (startAfterTimestamp != null) {
                query = query.startAfter(startAfterTimestamp)
            }

            val querySnapshot = query.get().await()
            val comments = querySnapshot.documents.mapNotNull { doc ->
                doc.toObject(Comment::class.java)?.copy(id = doc.id)
            }
            Result.success(comments)
        } catch (e: Exception) {
            Log.e("FirestoreService", "Failed to get comments for message: ${e.message}", e)
            Result.failure(e)
        }
    }

    // --- NEW: GeofenceZone CRUD Operations ---
    suspend fun addGeofenceZone(zone: GeofenceZone): Result<String> {
        return try {
            val docRef = if (zone.id.isNotEmpty()) {
                geofenceZonesCollection.document(zone.id).set(zone).await()
                geofenceZonesCollection.document(zone.id)
            } else {
                geofenceZonesCollection.add(zone).await()
            }
            Result.success(docRef.id)
        } catch (e: Exception) {
            Log.e("FirestoreService", "Failed to add geofence zone: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun getGeofenceZone(zoneId: String): Result<GeofenceZone?> {
        return try {
            val doc = geofenceZonesCollection.document(zoneId).get().await()
            Result.success(doc.toObject(GeofenceZone::class.java)?.copy(id = doc.id))
        } catch (e: Exception) {
            Log.e("FirestoreService", "Failed to get geofence zone $zoneId: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun getGeofenceZonesForGroup(groupId: String): Result<List<GeofenceZone>> {
        return try {
            val querySnapshot = geofenceZonesCollection
                .whereEqualTo("groupId", groupId)
                .get()
                .await()
            val zones = querySnapshot.documents.mapNotNull { doc ->
                doc.toObject(GeofenceZone::class.java)?.copy(id = doc.id)
            }
            Result.success(zones)
        } catch (e: Exception) {
            Log.e("FirestoreService", "Failed to get geofence zones for group $groupId: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun updateGeofenceZone(zone: GeofenceZone): Result<Void?> {
        return try {
            geofenceZonesCollection.document(zone.id).set(zone).await()
            Result.success(null)
        } catch (e: Exception) {
            Log.e("FirestoreService", "Failed to update geofence zone ${zone.id}: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun deleteGeofenceZone(zoneId: String): Result<Void?> {
        return try {
            // Potentially add a batch deletion for associated rules/assignments if not handled by security rules
            geofenceZonesCollection.document(zoneId).delete().await()
            Log.d("FirestoreService", "Geofence zone $zoneId deleted.")
            Result.success(null)
        } catch (e: Exception) {
            Log.e("FirestoreService", "Failed to delete geofence zone $zoneId: ${e.message}", e)
            Result.failure(e)
        }
    }

    // --- NEW: GeofenceRule CRUD Operations ---
    suspend fun addGeofenceRule(rule: GeofenceRule): Result<String> {
        return try {
            val docRef = if (rule.id.isNotEmpty()) {
                geofenceRulesCollection.document(rule.id).set(rule).await()
                geofenceRulesCollection.document(rule.id)
            } else {
                geofenceRulesCollection.add(rule).await()
            }
            Result.success(docRef.id)
        } catch (e: Exception) {
            Log.e("FirestoreService", "Failed to add geofence rule: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun getGeofenceRulesForZone(geofenceZoneId: String): Result<List<GeofenceRule>> {
        return try {
            val querySnapshot = geofenceRulesCollection
                .whereEqualTo("geofenceZoneId", geofenceZoneId)
                .get()
                .await()
            val rules = querySnapshot.documents.mapNotNull { doc ->
                doc.toObject(GeofenceRule::class.java)?.copy(id = doc.id)
            }
            Result.success(rules)
        } catch (e: Exception) {
            Log.e("FirestoreService", "Failed to get geofence rules for zone $geofenceZoneId: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun updateGeofenceRule(rule: GeofenceRule): Result<Void?> {
        return try {
            geofenceRulesCollection.document(rule.id).set(rule).await()
            Result.success(null)
        } catch (e: Exception) {
            Log.e("FirestoreService", "Failed to update geofence rule ${rule.id}: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun deleteGeofenceRule(ruleId: String): Result<Void?> {
        return try {
            geofenceRulesCollection.document(ruleId).delete().await()
            Log.d("FirestoreService", "Geofence rule $ruleId deleted.")
            Result.success(null)
        } catch (e: Exception) {
            Log.e("FirestoreService", "Failed to delete geofence rule $ruleId: ${e.message}", e)
            Result.failure(e)
        }
    }

    // --- NEW: GeofenceAssignment CRUD Operations ---
    suspend fun addGeofenceAssignment(assignment: GeofenceAssignment): Result<String> {
        return try {
            val docRef = if (assignment.id.isNotEmpty()) {
                geofenceAssignmentsCollection.document(assignment.id).set(assignment).await()
                geofenceAssignmentsCollection.document(assignment.id)
            } else {
                geofenceAssignmentsCollection.add(assignment).await()
            }
            Result.success(docRef.id)
        } catch (e: Exception) {
            Log.e("FirestoreService", "Failed to add geofence assignment: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun getGeofenceAssignmentsForZone(geofenceZoneId: String): Result<List<GeofenceAssignment>> {
        return try {
            val querySnapshot = geofenceAssignmentsCollection
                .whereEqualTo("geofenceZoneId", geofenceZoneId)
                .get()
                .await()
            val assignments = querySnapshot.documents.mapNotNull { doc ->
                doc.toObject(GeofenceAssignment::class.java)?.copy(id = doc.id)
            }
            Result.success(assignments)
        } catch (e: Exception) {
            Log.e("FirestoreService", "Failed to get geofence assignments for zone $geofenceZoneId: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun getGeofenceAssignmentsForEntity(assignedEntityId: String): Result<List<GeofenceAssignment>> {
        return try {
            val querySnapshot = geofenceAssignmentsCollection
                .whereEqualTo("assignedEntityId", assignedEntityId)
                .get()
                .await()
            val assignments = querySnapshot.documents.mapNotNull { doc ->
                doc.toObject(GeofenceAssignment::class.java)?.copy(id = doc.id)
            }
            Result.success(assignments)
        } catch (e: Exception) {
            Log.e("FirestoreService", "Failed to get geofence assignments for entity $assignedEntityId: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun updateGeofenceAssignment(assignment: GeofenceAssignment): Result<Void?> {
        return try {
            geofenceAssignmentsCollection.document(assignment.id).set(assignment).await()
            Result.success(null)
        } catch (e: Exception) {
            Log.e("FirestoreService", "Failed to update geofence assignment ${assignment.id}: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun deleteGeofenceAssignment(assignmentId: String): Result<Void?> {
        return try {
            geofenceAssignmentsCollection.document(assignmentId).delete().await()
            Log.d("FirestoreService", "Geofence assignment $assignmentId deleted.")
            Result.success(null)
        } catch (e: Exception) {
            Log.e("FirestoreService", "Failed to delete geofence assignment $assignmentId: ${e.message}", e)
            Result.failure(e)
        }
    }

    // --- NEW: GeofencePackage CRUD Operations ---
    suspend fun addGeofencePackage(pkg: GeofencePackage): Result<String> {
        return try {
            val docRef = if (pkg.id.isNotEmpty()) {
                geofencePackagesCollection.document(pkg.id).set(pkg).await()
                geofencePackagesCollection.document(pkg.id)
            } else {
                geofencePackagesCollection.add(pkg).await()
            }
            Result.success(docRef.id)
        } catch (e: Exception) {
            Log.e("FirestoreService", "Failed to add geofence package: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun getGeofencePackage(packageId: String): Result<GeofencePackage?> {
        return try {
            val doc = geofencePackagesCollection.document(packageId).get().await()
            Result.success(doc.toObject(GeofencePackage::class.java)?.copy(id = doc.id))
        } catch (e: Exception) {
            Log.e("FirestoreService", "Failed to get geofence package $packageId: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun getGeofencePackagesByCreator(createdByUserId: String): Result<List<GeofencePackage>> {
        return try {
            val querySnapshot = geofencePackagesCollection
                .whereEqualTo("createdByUserId", createdByUserId)
                .get()
                .await()
            val packages = querySnapshot.documents.mapNotNull { doc ->
                doc.toObject(GeofencePackage::class.java)?.copy(id = doc.id)
            }
            Result.success(packages)
        } catch (e: Exception) {
            Log.e("FirestoreService", "Failed to get geofence packages for creator $createdByUserId: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun updateGeofencePackage(pkg: GeofencePackage): Result<Void?> {
        return try {
            geofencePackagesCollection.document(pkg.id).set(pkg).await()
            Result.success(null)
        } catch (e: Exception) {
            Log.e("FirestoreService", "Failed to update geofence package ${pkg.id}: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun deleteGeofencePackage(packageId: String): Result<Void?> {
        return try {
            geofencePackagesCollection.document(packageId).delete().await()
            Log.d("FirestoreService", "Geofence package $packageId deleted.")
            Result.success(null)
        } catch (e: Exception) {
            Log.e("FirestoreService", "Failed to delete geofence package $packageId: ${e.message}", e)
            Result.failure(e)
        }
    }

    // --- NEW: Incident CRUD Operations ---
    suspend fun addIncident(incident: Incident): Result<String> {
        return try {
            val docRef = if (incident.id.isNotEmpty()) {
                incidentsCollection.document(incident.id).set(incident).await()
                incidentsCollection.document(incident.id)
            } else {
                incidentsCollection.add(incident).await()
            }
            Result.success(docRef.id)
        } catch (e: Exception) {
            Log.e("FirestoreService", "Failed to add incident: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun getIncident(incidentId: String): Result<Incident?> {
        return try {
            val doc = incidentsCollection.document(incidentId).get().await()
            Result.success(doc.toObject(Incident::class.java)?.copy(id = doc.id))
        } catch (e: Exception) {
            Log.e("FirestoreService", "Failed to get incident $incidentId: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun getActiveIncidentsForGroup(groupId: String): Result<List<Incident>> {
        return try {
            val querySnapshot = incidentsCollection
                .whereEqualTo("groupId", groupId)
                .whereIn("status", listOf("OPEN", "DISPATCHED", "RESPONDING", "ON_SCENE")) // Only active statuses
                .get()
                .await()
            val incidents = querySnapshot.documents.mapNotNull { doc ->
                doc.toObject(Incident::class.java)?.copy(id = doc.id)
            }
            Result.success(incidents)
        } catch (e: Exception) {
            Log.e("FirestoreService", "Failed to get active incidents for group $groupId: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun updateIncident(incident: Incident): Result<Void?> {
        return try {
            incidentsCollection.document(incident.id).set(incident).await()
            Result.success(null)
        } catch (e: Exception) {
            Log.e("FirestoreService", "Failed to update incident ${incident.id}: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun updateIncidentPersonnelStatus(
        incidentId: String,
        userId: String,
        newStatus: Map<String, Any?> // Map to allow partial updates (e.g., specific fields of IncidentPersonnelStatus)
    ): Result<Void?> {
        return try {
            // Firestore does not directly support updating nested map fields with FieldValue.
            // We need to fetch the document, update the map locally, then save the entire map.
            // A transaction is safer for concurrent updates.
            db.runTransaction { transaction ->
                val incidentRef = incidentsCollection.document(incidentId)
                val snapshot = transaction.get(incidentRef)
                val incident = snapshot.toObject(Incident::class.java)

                if (incident == null) {
                    throw Exception("Incident not found for update: $incidentId")
                }

                val currentPersonnelMap = incident.assignedPersonnel.toMutableMap()
                val currentPersonnelStatus = currentPersonnelMap[userId]

                val updatedPersonnelStatus = if (currentPersonnelStatus != null) {
                    // Update existing status with new values
                    val mutableMap = mutableMapOf<String, Any?>()
                    currentPersonnelStatus.let { status ->
                        mutableMap["currentStatus"] = status.currentStatus
                        mutableMap["enteredZoneTime"] = status.enteredZoneTime
                        mutableMap["exitedZoneTime"] = status.exitedZoneTime
                        mutableMap["timeInZoneMillis"] = status.timeInZoneMillis
                        mutableMap["lastStatusUpdateTime"] = status.lastStatusUpdateTime
                        mutableMap["lastStatusUpdateMessage"] = status.lastStatusUpdateMessage
                    }
                    // Apply newStatus over current fields
                    newStatus.forEach { (key, value) ->
                        mutableMap[key] = value
                    }
                    // Reconstruct the data class from the map for type safety or simply update the map
                    IncidentPersonnelStatus(
                        currentStatus = mutableMap["currentStatus"] as? String ?: "UNKNOWN",
                        enteredZoneTime = mutableMap["enteredZoneTime"] as? Long,
                        exitedZoneTime = mutableMap["exitedZoneTime"] as? Long,
                        timeInZoneMillis = mutableMap["timeInZoneMillis"] as? Long,
                        lastStatusUpdateTime = mutableMap["lastStatusUpdateTime"] as? Long ?: System.currentTimeMillis(),
                        lastStatusUpdateMessage = mutableMap["lastStatusUpdateMessage"] as? String
                    )
                } else {
                    // Create new IncidentPersonnelStatus if none exists for this user
                    IncidentPersonnelStatus(
                        currentStatus = newStatus["currentStatus"] as? String ?: "ASSIGNED",
                        enteredZoneTime = newStatus["enteredZoneTime"] as? Long,
                        exitedZoneTime = newStatus["exitedZoneTime"] as? Long,
                        timeInZoneMillis = newStatus["timeInZoneMillis"] as? Long,
                        lastStatusUpdateTime = newStatus["lastStatusUpdateTime"] as? Long ?: System.currentTimeMillis(),
                        lastStatusUpdateMessage = newStatus["lastStatusUpdateMessage"] as? String
                    )
                }

                currentPersonnelMap[userId] = updatedPersonnelStatus
                transaction.update(incidentRef, "assignedPersonnel", currentPersonnelMap)
                null
            }.await()
            Result.success(null)
        } catch (e: Exception) {
            Log.e("FirestoreService", "Failed to update incident personnel status for $incidentId, user $userId: ${e.message}", e)
            Result.failure(e)
        }
    }


    suspend fun deleteIncident(incidentId: String): Result<Void?> {
        return try {
            // Consider deleting associated geofence zone and log entries in a batch or cloud function
            incidentsCollection.document(incidentId).delete().await()
            Log.d("FirestoreService", "Incident $incidentId deleted.")
            Result.success(null)
        } catch (e: Exception) {
            Log.e("FirestoreService", "Failed to delete incident $incidentId: ${e.message}", e)
            Result.failure(e)
        }
    }

    // --- NEW: IncidentLogEntry CRUD Operations ---
    suspend fun addIncidentLogEntry(logEntry: IncidentLogEntry): Result<String> {
        return try {
            val docRef = incidentLogsCollection.add(logEntry).await()
            Result.success(docRef.id)
        } catch (e: Exception) {
            Log.e("FirestoreService", "Failed to add incident log entry: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun getIncidentLogEntries(incidentId: String, limit: Long = 100): Result<List<IncidentLogEntry>> {
        return try {
            val querySnapshot = incidentLogsCollection
                .whereEqualTo("incidentId", incidentId)
                .orderBy("timestamp", Query.Direction.ASCENDING)
                .limit(limit)
                .get()
                .await()
            val logEntries = querySnapshot.documents.mapNotNull { doc ->
                doc.toObject(IncidentLogEntry::class.java)?.copy(id = doc.id)
            }
            Result.success(logEntries)
        } catch (e: Exception) {
            Log.e("FirestoreService", "Failed to get incident log entries for $incidentId: ${e.message}", e)
            Result.failure(e)
        }
    }

    // --- NEW: GroupSettings CRUD Operations ---
    suspend fun saveGroupSettings(settings: GroupSettings): Result<Void?> {
        return try {
            // Use set with the ID from settings.id (which should be group ID)
            groupSettingsCollection.document(settings.id).set(settings).await()
            Result.success(null)
        } catch (e: Exception) {
            Log.e("FirestoreService", "Failed to save group settings for group ${settings.groupId}: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun getGroupSettings(groupId: String): Result<GroupSettings?> {
        return try {
            val doc = groupSettingsCollection.document(groupId).get().await()
            Result.success(doc.toObject(GroupSettings::class.java)?.copy(id = doc.id))
        } catch (e: Exception) {
            Log.e("FirestoreService", "Failed to get group settings for group $groupId: ${e.message}", e)
            Result.failure(e)
        }
    }
}
