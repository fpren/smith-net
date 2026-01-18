package com.guildofsmiths.trademesh.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.guildofsmiths.trademesh.MainActivity
import com.guildofsmiths.trademesh.R
import com.guildofsmiths.trademesh.data.SupabaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * RealtimeService: Foreground service that keeps the Supabase Realtime
 * connection alive even when the app is in the background.
 *
 * This ensures users receive notifications for incoming messages.
 */
class RealtimeService : Service() {

    companion object {
        private const val TAG = "RealtimeService"
        private const val CHANNEL_ID = "smith_net_realtime"
        private const val CHANNEL_NAME = "Background Connection"
        private const val NOTIFICATION_ID = 9999

        private var isRunning = false

        fun isServiceRunning(): Boolean = isRunning

        fun start(context: Context) {
            if (isRunning) {
                Log.d(TAG, "Service already running")
                return
            }

            val intent = Intent(context, RealtimeService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            Log.i(TAG, "Starting RealtimeService")
        }

        fun stop(context: Context) {
            val intent = Intent(context, RealtimeService::class.java)
            context.stopService(intent)
            Log.i(TAG, "Stopping RealtimeService")
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var reconnectJob: kotlinx.coroutines.Job? = null

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "RealtimeService created")
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "RealtimeService onStartCommand")

        // Start as foreground service immediately
        val notification = createForegroundNotification()
        startForeground(NOTIFICATION_ID, notification)

        isRunning = true

        // Ensure Realtime is connected
        connectRealtime()

        // Return STICKY so the service restarts if killed
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Log.i(TAG, "RealtimeService destroyed")
        isRunning = false
        reconnectJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_MIN  // Minimal - hidden from status bar
            ).apply {
                description = "Keeps message connection active"
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createForegroundNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("smith net")
            .setContentText("connected")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)  // Minimal priority
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)  // Hide on lock screen
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun connectRealtime() {
        serviceScope.launch {
            try {
                // Check if Supabase is initialized
                if (SupabaseAuth.client == null) {
                    Log.w(TAG, "Supabase client not initialized, waiting...")
                    delay(2000)
                }

                // Connect SupabaseChat if not connected
                if (!SupabaseChat.isConnected.value) {
                    Log.i(TAG, "Connecting SupabaseChat from service...")
                    SupabaseChat.connect()
                }

                // Monitor connection and reconnect if needed
                startConnectionMonitor()

            } catch (e: Exception) {
                Log.e(TAG, "Error connecting Realtime", e)
                // Retry after delay
                delay(5000)
                connectRealtime()
            }
        }
    }

    private fun startConnectionMonitor() {
        reconnectJob?.cancel()
        reconnectJob = serviceScope.launch {
            while (true) {
                delay(30000) // Check every 30 seconds

                if (!SupabaseChat.isConnected.value) {
                    Log.w(TAG, "Realtime disconnected, reconnecting...")
                    try {
                        SupabaseChat.connect()
                    } catch (e: Exception) {
                        Log.e(TAG, "Reconnection failed", e)
                    }
                }
            }
        }
    }
}
