package com.guildofsmiths.trademesh.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.guildofsmiths.trademesh.MainActivity
import com.guildofsmiths.trademesh.R
import com.guildofsmiths.trademesh.data.Message
import com.guildofsmiths.trademesh.data.MediaType
import com.guildofsmiths.trademesh.data.UserPreferences

/**
 * NotificationHelper: Shows local notifications for incoming messages
 * when app is in background.
 */
object NotificationHelper {
    
    private const val TAG = "NotificationHelper"
    
    private const val CHANNEL_ID_MESSAGES = "smith_net_messages"
    private const val CHANNEL_NAME_MESSAGES = "Messages"
    
    private const val CHANNEL_ID_MESH = "smith_net_mesh"
    private const val CHANNEL_NAME_MESH = "Mesh Activity"
    
    private var notificationId = 1000
    
    /** Track if app is in foreground to avoid notifications */
    private var isAppInForeground = true
    
    /**
     * Initialize notification channels.
     * Call this from Application.onCreate()
     */
    fun initialize(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            // Message notifications channel
            val messageChannel = NotificationChannel(
                CHANNEL_ID_MESSAGES,
                CHANNEL_NAME_MESSAGES,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "New message notifications"
                enableVibration(true)
                setShowBadge(true)
            }
            notificationManager.createNotificationChannel(messageChannel)
            
            // Mesh activity channel (lower priority)
            val meshChannel = NotificationChannel(
                CHANNEL_ID_MESH,
                CHANNEL_NAME_MESH,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "BLE mesh activity notifications"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(meshChannel)
            
            Log.d(TAG, "Notification channels created")
        }
    }
    
    /**
     * Set app foreground state.
     * Call from Activity.onResume/onPause
     */
    fun setAppForeground(foreground: Boolean) {
        isAppInForeground = foreground
        Log.i(TAG, "🔔 App foreground state changed: $foreground")
    }

    /**
     * Check if a message should trigger a notification based on user preferences.
     * Returns true if notification should be shown.
     */
    private fun shouldShowNotification(message: Message): Boolean {
        val currentUserId = UserPreferences.getUserId()
        val currentUserName = UserPreferences.getUserName()

        // Check if this is a direct message to the user
        val isDirectMessage = message.recipientId != null && message.recipientId == currentUserId

        // Check if user is mentioned in the message (@username or @displayName)
        val isMentioned = currentUserName.isNotBlank() &&
            (message.content.contains("@$currentUserName", ignoreCase = true) ||
             message.content.contains("@${currentUserId}", ignoreCase = true))

        // Check if this is a group message (not a DM)
        val isGroupMessage = message.recipientId == null

        // If user is mentioned and mentions are enabled, always show
        if (isMentioned && UserPreferences.isNotifyMentionsEnabled()) {
            Log.d(TAG, "🔔 Notification allowed: user mentioned")
            return true
        }

        // If it's a direct message to the user and direct messages are enabled, show
        if (isDirectMessage && UserPreferences.isNotifyDirectMessagesEnabled()) {
            Log.d(TAG, "🔔 Notification allowed: direct message")
            return true
        }

        // If it's a group message and group messages are enabled, show
        if (isGroupMessage && UserPreferences.isNotifyGroupMessagesEnabled()) {
            Log.d(TAG, "🔔 Notification allowed: group message")
            return true
        }

        // No preference matched, don't show notification
        Log.d(TAG, "🔔 Notification blocked: no matching preference (isDM=$isDirectMessage, isGroup=$isGroupMessage, isMentioned=$isMentioned)")
        return false
    }

    /**
     * Show notification for incoming message.
     * Only shows if app is in background and passes notification filters.
     */
    fun showMessageNotification(context: Context, message: Message) {
        Log.i(TAG, "🔔 showMessageNotification called - isAppInForeground=$isAppInForeground, sender=${message.senderName}")
        if (isAppInForeground) {
            Log.i(TAG, "🔔 App in foreground, skipping notification for: ${message.content.take(30)}")
            return
        }

        // Don't notify for own messages or system messages
        if (message.senderName == "You" || message.senderName == "System") {
            return
        }

        // Check notification preferences
        if (!shouldShowNotification(message)) {
            Log.i(TAG, "🔔 Notification filtered by user preferences for: ${message.content.take(30)}")
            return
        }
        
        try {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("channelId", message.channelId)
                putExtra("beaconId", message.beaconId)
            }
            
            val pendingIntent = PendingIntent.getActivity(
                context,
                notificationId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            // Build notification content
            val title = message.senderName
            val content = when (message.mediaType) {
                MediaType.IMAGE -> "[▣] Sent a photo"
                MediaType.VOICE -> "[▶] Sent a voice message"
                MediaType.VIDEO -> "[▶] Sent a video"
                MediaType.FILE -> "[■] Sent a file"
                else -> message.content.take(100)
            }
            
            val channelId = if (message.isMeshOrigin) CHANNEL_ID_MESH else CHANNEL_ID_MESSAGES
            
            val notification = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(title)
                .setContentText(content)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setGroup("smith_net_messages")
                .build()
            
            val notificationManager = NotificationManagerCompat.from(context)
            
            // Check notification permission for Android 13+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) 
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    Log.w(TAG, "Notification permission not granted")
                    return
                }
            }
            
            notificationManager.notify(notificationId++, notification)
            Log.d(TAG, "📬 Notification shown: $title - $content")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show notification", e)
        }
    }
    
    /**
     * Show summary notification for multiple messages.
     */
    fun showSummaryNotification(context: Context, messageCount: Int) {
        if (isAppInForeground || messageCount < 2) return
        
        try {
            val summary = NotificationCompat.Builder(context, CHANNEL_ID_MESSAGES)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("Smith Net")
                .setContentText("$messageCount new messages")
                .setGroup("smith_net_messages")
                .setGroupSummary(true)
                .setAutoCancel(true)
                .build()
            
            val notificationManager = NotificationManagerCompat.from(context)
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) 
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    return
                }
            }
            
            notificationManager.notify(0, summary)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show summary notification", e)
        }
    }
    
    /**
     * Cancel all notifications.
     */
    fun cancelAll(context: Context) {
        NotificationManagerCompat.from(context).cancelAll()
    }
}
