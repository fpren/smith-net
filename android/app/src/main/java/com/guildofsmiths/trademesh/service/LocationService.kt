package com.guildofsmiths.trademesh.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.guildofsmiths.trademesh.MainActivity
import com.guildofsmiths.trademesh.R
import com.guildofsmiths.trademesh.data.CrewPresenceRepository
import com.guildofsmiths.trademesh.data.LocationSharingPreferences
import com.guildofsmiths.trademesh.data.LocationTrailRepository
import com.guildofsmiths.trademesh.data.PresenceApiClient
import com.guildofsmiths.trademesh.data.UserPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
/**
 * Foreground service that tracks the device's GPS location while clocked in.
 * Writes updates to [CrewPresenceRepository] (for the live-crew view) and
 * [LocationTrailRepository] (for Lost & Found / breadcrumb trails).
 *
 * Started from [TimeTrackingViewModel.clockIn] via [start], stopped on
 * clock-out via [stop]. Silently no-ops if the user has turned location
 * sharing off in Settings or denied the OS permission.
 */
class LocationService : Service() {

    companion object {
        private const val TAG = "LocationService"
        private const val CHANNEL_ID = "smith_net_location"
        private const val NOTIFICATION_ID = 5001

        const val ACTION_START = "com.guildofsmiths.trademesh.location.START"
        const val ACTION_STOP = "com.guildofsmiths.trademesh.location.STOP"

        fun start(context: Context) {
            val intent = Intent(context, LocationService::class.java).apply { action = ACTION_START }
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, LocationService::class.java))
        }
    }

    private var locationManager: LocationManager? = null
    private var listener: LocationListener? = null
    private var running = false

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val presenceApi by lazy { PresenceApiClient(HttpClientFactory.client) }
    private var lastPostAtMs: Long = 0L
    private val postIntervalMs: Long = 60_000L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager?
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
        }

        val prefs = LocationSharingPreferences.state.value
        if (!prefs.enabled) {
            Log.i(TAG, "Location sharing disabled in prefs — not starting.")
            stopSelf()
            return START_NOT_STICKY
        }
        if (!hasPermission()) {
            Log.i(TAG, "Location permission not granted — not starting.")
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(NOTIFICATION_ID, buildNotification())
        beginUpdates(prefs.cadence.seconds)
        running = true
        return START_STICKY
    }

    override fun onDestroy() {
        serviceScope.cancel()
        stopUpdates()
        running = false
        super.onDestroy()
    }

    private fun beginUpdates(intervalSeconds: Int) {
        val lm = locationManager ?: return
        if (!hasPermission()) return
        if (intervalSeconds <= 0 || intervalSeconds == Int.MAX_VALUE) {
            Log.i(TAG, "Cadence=MANUAL — service idle; call writeOneShot from UI instead.")
            return
        }

        val userId = UserPreferences.getUserId()
        listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                record(userId, location)

                val now = System.currentTimeMillis()
                if (now - lastPostAtMs < postIntervalMs) return
                lastPostAtMs = now

                val accuracy: Float? = if (location.hasAccuracy()) location.accuracy else null
                val battery: Int? = readBatteryPct()

                serviceScope.launch {
                    try {
                        val ok = presenceApi.postLocation(
                            lat = location.latitude,
                            lng = location.longitude,
                            accuracyM = accuracy,
                            batteryPct = battery,
                        )
                        if (!ok) {
                            Log.w(TAG, "postLocation returned no-open-shift; stopping service")
                            stopSelf()
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "postLocation failed (will retry on next emission): ${e.message}")
                    }
                }
            }
            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }

        val minTimeMs = intervalSeconds * 1000L
        val minDistanceM = 5f
        try {
            if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                lm.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER, minTimeMs, minDistanceM, listener!!, Looper.getMainLooper()
                )
            }
            if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                lm.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER, minTimeMs, minDistanceM, listener!!, Looper.getMainLooper()
                )
            }
            // Seed an immediate fix from last known.
            lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)?.let { record(userId, it) }
                ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)?.let { record(userId, it) }
            Log.i(TAG, "Started updates every ${intervalSeconds}s for $userId")
        } catch (se: SecurityException) {
            Log.w(TAG, "SecurityException requesting location updates", se)
            stopSelf()
        }
    }

    private fun stopUpdates() {
        val lm = locationManager ?: return
        val l = listener ?: return
        try { lm.removeUpdates(l) } catch (t: Throwable) { /* ignore */ }
        listener = null
    }

    private fun record(userId: String, loc: Location) {
        CrewPresenceRepository.upsertLocation(
            userId = userId,
            latitude = loc.latitude,
            longitude = loc.longitude,
            accuracyMeters = if (loc.hasAccuracy()) loc.accuracy else null,
            timestamp = loc.time
        )
        LocationTrailRepository.record(
            userId = userId,
            latitude = loc.latitude,
            longitude = loc.longitude,
            accuracyMeters = if (loc.hasAccuracy()) loc.accuracy else null,
            timestamp = loc.time,
            source = loc.provider ?: "gps"
        )
    }

    private fun readBatteryPct(): Int? {
        return try {
            val bm = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        } catch (e: Exception) {
            null
        }
    }

    private fun hasPermission(): Boolean = ContextCompat.checkSelfPermission(
        this, Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
        this, Manifest.permission.ACCESS_COARSE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    private fun ensureChannel() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            val ch = NotificationChannel(
                CHANNEL_ID,
                "Location tracking",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "GPS tracking for clock-in validation and crew locate"
                setShowBadge(false)
            }
            nm.createNotificationChannel(ch)
        }
    }

    private fun buildNotification(): android.app.Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pi = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Smith Net tracking")
            .setContentText("Location active for clock-in validation. Tap to manage.")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setContentIntent(pi)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
