package com.artificialinsightsllc.teamsync.Services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri // NEW: Import Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.artificialinsightsllc.teamsync.R
import com.artificialinsightsllc.teamsync.TeamSyncApplication
import com.artificialinsightsllc.teamsync.Models.NotificationEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import android.app.PendingIntent // NEW: Import PendingIntent
import androidx.core.app.TaskStackBuilder // NEW: Import TaskStackBuilder
import com.artificialinsightsllc.teamsync.MainActivity // NEW: Import MainActivity


class TeamSyncFirebaseMessagingService : FirebaseMessagingService() {

    private lateinit var notificationRepository: com.artificialinsightsllc.teamsync.Database.NotificationRepository
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        private const val TAG = "TeamSyncFCMService"
        private const val CHANNEL_ID = "teamsync_notifications_channel"
        private const val CHANNEL_NAME = "TeamSync Notifications"
        private const val NOTIFICATION_REQUEST_CODE = 1 // Unique request code for PendingIntent

        // Define the deep link URI scheme and host
        const val DEEPLINK_SCHEME = "teamsync"
        const val DEEPLINK_HOST = "notification"
        const val DEEPLINK_PATH_DETAIL = "detail" // For specific notification
        const val DEEPLINK_PATH_LIST = "list"     // For general list
        const val NOTIFICATION_ID_PARAM = "notificationId" // Parameter name for ID in deep link
    }

    override fun onCreate() {
        super.onCreate()
        notificationRepository = (application as TeamSyncApplication).notificationRepository
        Log.d(TAG, "TeamSyncFirebaseMessagingService onCreate: NotificationRepository initialized.")
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "Refreshed token: $token")
        // GroupMonitorService will handle saving this token to the user's profile in Firestore.
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d(TAG, "From: ${remoteMessage.from}")

        // Save data message to local database first, to get a local notification ID
        val notificationEntity = NotificationEntity(
            messageId = remoteMessage.messageId,
            title = remoteMessage.notification?.title ?: remoteMessage.data["title"],
            body = remoteMessage.notification?.body ?: remoteMessage.data["body"],
            timestamp = remoteMessage.sentTime,
            type = remoteMessage.data["type"],
            senderId = remoteMessage.data["senderId"],
            groupId = remoteMessage.data["groupId"],
            isRead = false,
            dataPayload = remoteMessage.data.toString()
        )

        serviceScope.launch {
            try {
                // Insert and then retrieve the inserted entity to get its auto-generated ID
                notificationRepository.insertNotification(notificationEntity)
                val insertedNotification = notificationRepository.getNotificationByMessageIdAndTimestamp(
                    notificationEntity.messageId, notificationEntity.timestamp) // Assuming a helper for this
                val localNotificationId = insertedNotification?.id ?: 0 // Use 0 as fallback if retrieval fails

                Log.d(TAG, "Notification saved to local DB. Local ID: $localNotificationId")

                // Now display notification with deep link using the local ID
                sendNotification(remoteMessage.notification?.title, remoteMessage.notification?.body, localNotificationId)

            } catch (e: Exception) {
                Log.e(TAG, "Failed to save notification to local DB or send system notification: ${e.message}", e)
                // Fallback to send notification without a specific deep link if saving fails
                sendNotification(remoteMessage.notification?.title, remoteMessage.notification?.body, null)
            }
        }
    }

    /**
     * Create and show a simple notification containing the received FCM message.
     * @param localNotificationId The local Room database ID of the notification, if available.
     * Used for deep linking to specific notification. Null if not available.
     */
    private fun sendNotification(title: String?, body: String?, localNotificationId: Int?) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications for TeamSync alerts and messages"
            }
            notificationManager.createNotificationChannel(channel)
        }

        // Build the deep link URI
        val deepLinkUri = if (localNotificationId != null) {
            Uri.parse("$DEEPLINK_SCHEME://$DEEPLINK_HOST/$DEEPLINK_PATH_DETAIL?$NOTIFICATION_ID_PARAM=$localNotificationId")
        } else {
            Uri.parse("$DEEPLINK_SCHEME://$DEEPLINK_HOST/$DEEPLINK_PATH_LIST")
        }
        Log.d(TAG, "Deep link URI: $deepLinkUri")


        // Create an Intent that will launch MainActivity and handle the deep link
        val intent = Intent(Intent.ACTION_VIEW, deepLinkUri, this, MainActivity::class.java).apply {
            // These flags ensure that when the activity is launched, it starts a new task
            // and clears any existing activities above it in the stack.
            // This is good for launching from a notification.
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        // Use TaskStackBuilder to create a synthetic back stack for proper navigation
        val pendingIntent: PendingIntent? = TaskStackBuilder.create(this).run {
            addNextIntentWithParentStack(intent)
            getPendingIntent(NOTIFICATION_REQUEST_CODE, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }


        val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title ?: "TeamSync Notification")
            .setContentText(body)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent) // Set the PendingIntent here

        // Use a unique ID for each notification if localNotificationId is available, otherwise a generic one
        val notificationId = localNotificationId ?: 0 // Use 0 for generic notifications, or a unique ID from FCM messageId hash
        notificationManager.notify(notificationId, notificationBuilder.build())
    }
}
