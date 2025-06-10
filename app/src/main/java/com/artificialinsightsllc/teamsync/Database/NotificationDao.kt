package com.artificialinsightsllc.teamsync.Database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.artificialinsightsllc.teamsync.Models.NotificationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NotificationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotification(notification: NotificationEntity)

    @Query("SELECT * FROM notifications ORDER BY timestamp DESC")
    fun getAllNotifications(): Flow<List<NotificationEntity>> // Flow to observe changes in real-time

    @Query("SELECT * FROM notifications WHERE id = :notificationId")
    suspend fun getNotificationById(notificationId: Int): NotificationEntity?

    // NEW: Query to retrieve a notification by messageId and timestamp
    @Query("SELECT * FROM notifications WHERE messageId = :messageId AND timestamp = :timestamp LIMIT 1")
    suspend fun getNotificationByMessageIdAndTimestamp(messageId: String?, timestamp: Long): NotificationEntity?


    @Query("UPDATE notifications SET isRead = 1 WHERE id = :notificationId")
    suspend fun markAsRead(notificationId: Int)

    @Query("DELETE FROM notifications WHERE id = :notificationId")
    suspend fun deleteNotification(notificationId: Int)

    @Query("DELETE FROM notifications")
    suspend fun deleteAllNotifications()
}
