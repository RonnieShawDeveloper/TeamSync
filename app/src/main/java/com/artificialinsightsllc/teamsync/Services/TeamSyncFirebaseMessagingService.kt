package com.artificialinsightsllc.teamsync.Services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
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
import android.app.PendingIntent
import androidx.core.app.TaskStackBuilder
import com.artificialinsightsllc.teamsync.MainActivity
import com.artificialinsightsllc.teamsync.Models.NotificationType
import java.util.HashMap // Ensure HashMap is imported

class TeamSyncFirebaseMessagingService : FirebaseMessagingService() {

    private lateinit var notificationRepository: com.artificialinsightsllc.teamsync.Database.NotificationRepository
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        private const val TAG = "TeamSyncFCMService"
        private const val CHANNEL_ID = "teamsync_notifications_channel"
        private const val CHANNEL_NAME = "TeamSync Notifications"
        private const val NOTIFICATION_REQUEST_CODE = 1

        const val DEEPLINK_SCHEME = "teamsync"
        const val DEEPLINK_HOST = "notification"
        const val DEEPLINK_PATH_DETAIL = "detail"
        const val DEEPLINK_PATH_LIST = "list"
        const val NOTIFICATION_ID_PARAM = "notificationId"

        const val FCM_DATA_PAYLOAD_KEY = "fcm_data_payload"
    }

    override fun onCreate() {
        super.onCreate()
        notificationRepository = (application as TeamSyncApplication).notificationRepository
        Log.d(TAG, "TeamSyncFirebaseMessagingService onCreate: NotificationRepository initialized.")
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "Refreshed token: $token")
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d(TAG, "From: ${remoteMessage.from}")
        Log.d(TAG, "Message data payload (from onMessageReceived): ${remoteMessage.data}")
        Log.d(TAG, "Message Notification Body (from onMessageReceived): ${remoteMessage.notification?.body}")

        if (remoteMessage.data.isNotEmpty()) {
            saveNotificationToLocalDb(remoteMessage)
        }

        val title = remoteMessage.notification?.title ?: remoteMessage.data["title"]
        val body = remoteMessage.notification?.body ?: remoteMessage.data["body"]
        // CHANGED: Pass the entire remoteMessage to sendNotification
        sendNotification(title, body, remoteMessage.data, remoteMessage)
    }

    private fun saveNotificationToLocalDb(remoteMessage: RemoteMessage) {
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
                notificationRepository.insertNotification(notificationEntity)
                Log.d(TAG, "Notification saved to local DB via onMessageReceived: ${notificationEntity.title} - ${notificationEntity.body}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save notification to local DB from onMessageReceived: ${e.message}", e)
            }
        }
    }

    // CHANGED: Added remoteMessage parameter to sendNotification
    private fun sendNotification(title: String?, body: String?, dataPayload: Map<String, String>, remoteMessage: RemoteMessage) {
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

        val baseUri = Uri.parse("$DEEPLINK_SCHEME://$DEEPLINK_HOST/$DEEPLINK_PATH_LIST")
        val deepLinkUriBuilder = baseUri.buildUpon()

        dataPayload.forEach { (key, value) ->
            deepLinkUriBuilder.appendQueryParameter(key, value)
        }
        val deepLinkUri = deepLinkUriBuilder.build()

        Log.d(TAG, "Deep link URI being built: $deepLinkUri")

        val intent = Intent(Intent.ACTION_VIEW, deepLinkUri, this, MainActivity::class.java).apply {
            putExtra(FCM_DATA_PAYLOAD_KEY, HashMap(dataPayload))
        }

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
            .setContentIntent(pendingIntent)

        // CORRECTED: notificationId now correctly uses remoteMessage from the parameter
        val notificationId = remoteMessage.messageId?.hashCode() ?: 0
        notificationManager.notify(notificationId, notificationBuilder.build())
    }
}
