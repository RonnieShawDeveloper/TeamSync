package com.artificialinsightsllc.teamsync.ViewModels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.artificialinsightsllc.teamsync.Database.NotificationRepository
import com.artificialinsightsllc.teamsync.Models.NotificationEntity
import com.artificialinsightsllc.teamsync.TeamSyncApplication
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class NotificationViewModel(application: Application) : AndroidViewModel(application) {

    private val notificationRepository: NotificationRepository
    private val _notifications = MutableStateFlow<List<NotificationEntity>>(emptyList())
    val notifications: StateFlow<List<NotificationEntity>> = _notifications.asStateFlow()

    init {
        // Get the NotificationRepository instance from the Application class
        val app = application as TeamSyncApplication
        notificationRepository = app.notificationRepository

        // Start collecting all notifications from the repository
        viewModelScope.launch {
            notificationRepository.getAllNotifications().collectLatest { notificationsList ->
                _notifications.value = notificationsList
            }
        }
    }

    /**
     * Marks a specific notification as read in the local database.
     * @param notificationId The ID of the notification to mark as read.
     */
    fun markNotificationAsRead(notificationId: Int) {
        viewModelScope.launch {
            notificationRepository.markAsRead(notificationId)
        }
    }

    /**
     * Deletes a specific notification from the local database.
     * @param notificationId The ID of the notification to delete.
     */
    fun deleteNotification(notificationId: Int) {
        viewModelScope.launch {
            notificationRepository.deleteNotification(notificationId)
        }
    }

    /**
     * Deletes all notifications from the local database.
     */
    fun deleteAllNotifications() {
        viewModelScope.launch {
            notificationRepository.deleteAllNotifications()
        }
    }

    /**
     * Inserts a new notification into the local database.
     * This function is used by the UI to save a simulated "sent" message.
     * @param notification The NotificationEntity to insert.
     */
    fun insertNotification(notification: NotificationEntity) {
        viewModelScope.launch {
            notificationRepository.insertNotification(notification)
        }
    }
}
