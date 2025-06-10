package com.artificialinsightsllc.teamsync.Database

import com.artificialinsightsllc.teamsync.Models.NotificationEntity
import kotlinx.coroutines.flow.Flow

class NotificationRepository(private val notificationDao: NotificationDao) {

    suspend fun insertNotification(notification: NotificationEntity) {
        notificationDao.insertNotification(notification)
    }

    fun getAllNotifications(): Flow<List<NotificationEntity>> {
        return notificationDao.getAllNotifications()
    }

    suspend fun getNotificationById(notificationId: Int): NotificationEntity? {
        return notificationDao.getNotificationById(notificationId)
    }

    // NEW: Implement the method to retrieve a notification by messageId and timestamp
    suspend fun getNotificationByMessageIdAndTimestamp(messageId: String?, timestamp: Long): NotificationEntity? {
        return notificationDao.getNotificationByMessageIdAndTimestamp(messageId, timestamp)
    }

    suspend fun markAsRead(notificationId: Int) {
        notificationDao.markAsRead(notificationId)
    }

    suspend fun deleteNotification(notificationId: Int) {
        notificationDao.deleteNotification(notificationId)
    }

    suspend fun deleteAllNotifications() {
        notificationDao.deleteAllNotifications()
    }
}
