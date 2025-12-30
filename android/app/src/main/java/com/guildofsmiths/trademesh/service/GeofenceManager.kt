package com.guildofsmiths.trademesh.service

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.*

/**
 * C-13: GPS Geofence Clocking
 * 
 * Automatically clock in/out based on GPS location entering/exiting
 * predefined job site geofences.
 * 
 * Features:
 * - Define circular geofences around job sites
 * - Auto clock-in when entering a geofence
 * - Auto clock-out when exiting a geofence
 * - Dwell time requirements (must stay for X minutes to trigger)
 * - Background location monitoring
 */
object GeofenceManager {

    private const val TAG = "GeofenceManager"
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    
    private var locationManager: LocationManager? = null
    private var locationListener: LocationListener? = null
    private var appContext: Context? = null
    
    // ════════════════════════════════════════════════════════════════════
    // STATE
    // ════════════════════════════════════════════════════════════════════
    
    private val _isEnabled = MutableStateFlow(false)
    val isEnabled: StateFlow<Boolean> = _isEnabled.asStateFlow()
    
    private val _isTracking = MutableStateFlow(false)
    val isTracking: StateFlow<Boolean> = _isTracking.asStateFlow()
    
    private val _currentLocation = MutableStateFlow<Location?>(null)
    val currentLocation: StateFlow<Location?> = _currentLocation.asStateFlow()
    
    private val _currentGeofence = MutableStateFlow<Geofence?>(null)
    val currentGeofence: StateFlow<Geofence?> = _currentGeofence.asStateFlow()
    
    private val _geofences = MutableStateFlow<List<Geofence>>(emptyList())
    val geofences: StateFlow<List<Geofence>> = _geofences.asStateFlow()
    
    // Tracking state
    private var lastGeofenceId: String? = null
    private var geofenceEntryTime: Long? = null
    private var hasTriggeredClockIn = false
    
    // Configuration
    private var dwellTimeMs = 2 * 60 * 1000L // 2 minutes before auto clock-in
    private var exitGraceMs = 5 * 60 * 1000L // 5 minutes grace before auto clock-out
    private var locationIntervalMs = 30 * 1000L // Check every 30 seconds
    private var exitDetectionTime: Long? = null
    
    // ════════════════════════════════════════════════════════════════════
    // INITIALIZATION
    // ════════════════════════════════════════════════════════════════════
    
    fun initialize(context: Context) {
        appContext = context.applicationContext
        locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        Log.i(TAG, "GeofenceManager initialized")
        
        // Load saved geofences
        loadGeofences()
    }
    
    // ════════════════════════════════════════════════════════════════════
    // GEOFENCE MANAGEMENT
    // ════════════════════════════════════════════════════════════════════
    
    /**
     * Add a new geofence for a job site.
     */
    fun addGeofence(
        name: String,
        latitude: Double,
        longitude: Double,
        radiusMeters: Float = 100f,
        jobId: String? = null,
        autoClockIn: Boolean = true,
        autoClockOut: Boolean = true
    ): Geofence {
        val geofence = Geofence(
            id = UUID.randomUUID().toString(),
            name = name,
            latitude = latitude,
            longitude = longitude,
            radiusMeters = radiusMeters,
            jobId = jobId,
            autoClockIn = autoClockIn,
            autoClockOut = autoClockOut,
            createdAt = System.currentTimeMillis()
        )
        
        val updated = _geofences.value.toMutableList()
        updated.add(geofence)
        _geofences.value = updated
        
        saveGeofences()
        
        Log.i(TAG, "Added geofence: ${geofence.name} at (${geofence.latitude}, ${geofence.longitude})")
        return geofence
    }
    
    /**
     * Remove a geofence.
     */
    fun removeGeofence(id: String) {
        val updated = _geofences.value.filter { it.id != id }
        _geofences.value = updated
        saveGeofences()
        
        // If currently in this geofence, clear state
        if (lastGeofenceId == id) {
            lastGeofenceId = null
            _currentGeofence.value = null
            geofenceEntryTime = null
            hasTriggeredClockIn = false
        }
        
        Log.i(TAG, "Removed geofence: $id")
    }
    
    /**
     * Update geofence settings.
     */
    fun updateGeofence(geofence: Geofence) {
        val updated = _geofences.value.map { 
            if (it.id == geofence.id) geofence else it 
        }
        _geofences.value = updated
        saveGeofences()
    }
    
    // ════════════════════════════════════════════════════════════════════
    // TRACKING CONTROL
    // ════════════════════════════════════════════════════════════════════
    
    /**
     * Enable geofence tracking.
     */
    fun enable(): Boolean {
        val context = appContext ?: run {
            Log.e(TAG, "Context not initialized")
            return false
        }
        
        // Check location permission
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Location permission not granted")
            return false
        }
        
        _isEnabled.value = true
        Log.i(TAG, "Geofence tracking enabled")
        return true
    }
    
    /**
     * Disable geofence tracking.
     */
    fun disable() {
        stopTracking()
        _isEnabled.value = false
        Log.i(TAG, "Geofence tracking disabled")
    }
    
    /**
     * Start location tracking.
     */
    @SuppressLint("MissingPermission")
    fun startTracking() {
        if (!_isEnabled.value) {
            Log.w(TAG, "Cannot start tracking - geofencing not enabled")
            return
        }
        
        val manager = locationManager ?: return
        
        locationListener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                onLocationUpdate(location)
            }
            
            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }
        
        try {
            // Try GPS first, fall back to network
            val provider = when {
                manager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
                manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
                else -> null
            }
            
            if (provider != null) {
                manager.requestLocationUpdates(
                    provider,
                    locationIntervalMs,
                    10f, // min distance in meters
                    locationListener!!,
                    Looper.getMainLooper()
                )
                _isTracking.value = true
                Log.i(TAG, "▓▓▓ GEOFENCE TRACKING STARTED (provider: $provider) ▓▓▓")
            } else {
                Log.e(TAG, "No location provider available")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start location tracking: ${e.message}")
        }
    }
    
    /**
     * Stop location tracking.
     */
    fun stopTracking() {
        locationListener?.let { listener ->
            locationManager?.removeUpdates(listener)
        }
        locationListener = null
        _isTracking.value = false
        Log.i(TAG, "░░░ GEOFENCE TRACKING STOPPED ░░░")
    }
    
    // ════════════════════════════════════════════════════════════════════
    // LOCATION PROCESSING
    // ════════════════════════════════════════════════════════════════════
    
    private fun onLocationUpdate(location: Location) {
        _currentLocation.value = location
        
        // Check which geofence (if any) we're in
        val insideGeofence = findContainingGeofence(location)
        
        if (insideGeofence != null) {
            onInsideGeofence(insideGeofence, location)
        } else {
            onOutsideAllGeofences()
        }
    }
    
    private fun findContainingGeofence(location: Location): Geofence? {
        return _geofences.value.find { geofence ->
            val distance = calculateDistance(
                location.latitude, location.longitude,
                geofence.latitude, geofence.longitude
            )
            distance <= geofence.radiusMeters
        }
    }
    
    private fun onInsideGeofence(geofence: Geofence, location: Location) {
        val now = System.currentTimeMillis()
        
        // Clear exit detection
        exitDetectionTime = null
        
        // Check if this is a new geofence entry
        if (lastGeofenceId != geofence.id) {
            Log.i(TAG, "▶ Entered geofence: ${geofence.name}")
            lastGeofenceId = geofence.id
            _currentGeofence.value = geofence
            geofenceEntryTime = now
            hasTriggeredClockIn = false
        }
        
        // Check dwell time for auto clock-in
        val entryTime = geofenceEntryTime ?: now
        val dwellTime = now - entryTime
        
        if (!hasTriggeredClockIn && 
            geofence.autoClockIn && 
            dwellTime >= dwellTimeMs) {
            
            Log.i(TAG, "✓ Dwell time met (${dwellTime / 1000}s) - triggering auto clock-in")
            triggerAutoClockIn(geofence)
            hasTriggeredClockIn = true
        }
    }
    
    private fun onOutsideAllGeofences() {
        val now = System.currentTimeMillis()
        val currentGeofenceVal = _currentGeofence.value
        
        if (currentGeofenceVal != null) {
            // First detection of exit
            if (exitDetectionTime == null) {
                Log.i(TAG, "◀ Exited geofence: ${currentGeofenceVal.name} (grace period started)")
                exitDetectionTime = now
            }
            
            // Check if grace period has passed
            val exitTime = exitDetectionTime!!
            val outsideTime = now - exitTime
            
            if (outsideTime >= exitGraceMs) {
                Log.i(TAG, "✓ Exit grace period passed (${outsideTime / 1000}s) - confirming exit")
                
                if (currentGeofenceVal.autoClockOut && hasTriggeredClockIn) {
                    triggerAutoClockOut(currentGeofenceVal)
                }
                
                // Clear state
                lastGeofenceId = null
                _currentGeofence.value = null
                geofenceEntryTime = null
                hasTriggeredClockIn = false
                exitDetectionTime = null
            }
        }
    }
    
    // ════════════════════════════════════════════════════════════════════
    // AUTO CLOCK IN/OUT
    // ════════════════════════════════════════════════════════════════════
    
    private fun triggerAutoClockIn(geofence: Geofence) {
        Log.i(TAG, "╔═══════════════════════════════════════╗")
        Log.i(TAG, "║  AUTO CLOCK IN @ ${geofence.name}")
        Log.i(TAG, "╚═══════════════════════════════════════╝")
        
        scope.launch {
            try {
                // Log the event - integrate with time tracking API
                Log.i(TAG, "Clock in event: jobId=${geofence.jobId}, location=${geofence.name}")
                
                // TODO: Integrate with time tracking backend when ready
            } catch (e: Exception) {
                Log.e(TAG, "Auto clock-in failed: ${e.message}")
            }
        }
    }
    
    private fun triggerAutoClockOut(geofence: Geofence) {
        Log.i(TAG, "╔═══════════════════════════════════════╗")
        Log.i(TAG, "║  AUTO CLOCK OUT @ ${geofence.name}")
        Log.i(TAG, "╚═══════════════════════════════════════╝")
        
        scope.launch {
            try {
                Log.i(TAG, "Clock out event: location=${geofence.name}")
                
                // TODO: Integrate with time tracking backend when ready
            } catch (e: Exception) {
                Log.e(TAG, "Auto clock-out failed: ${e.message}")
            }
        }
    }
    
    // ════════════════════════════════════════════════════════════════════
    // UTILITIES
    // ════════════════════════════════════════════════════════════════════
    
    /**
     * Calculate distance between two GPS coordinates using Haversine formula.
     */
    private fun calculateDistance(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double
    ): Float {
        val earthRadius = 6371000.0 // meters
        
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        
        return (earthRadius * c).toFloat()
    }
    
    private fun loadGeofences() {
        // TODO: Load from SharedPreferences or database
        _geofences.value = emptyList()
    }
    
    private fun saveGeofences() {
        // TODO: Save to SharedPreferences or database
    }
    
    /**
     * Get current status.
     */
    fun getStatus(): GeofenceStatus {
        return GeofenceStatus(
            isEnabled = _isEnabled.value,
            isTracking = _isTracking.value,
            currentGeofence = _currentGeofence.value,
            geofenceCount = _geofences.value.size,
            hasTriggeredClockIn = hasTriggeredClockIn
        )
    }
    
    // ════════════════════════════════════════════════════════════════════
    // DATA CLASSES
    // ════════════════════════════════════════════════════════════════════
    
    data class Geofence(
        val id: String,
        val name: String,
        val latitude: Double,
        val longitude: Double,
        val radiusMeters: Float,
        val jobId: String? = null,
        val autoClockIn: Boolean = true,
        val autoClockOut: Boolean = true,
        val createdAt: Long
    )
    
    data class GeofenceStatus(
        val isEnabled: Boolean,
        val isTracking: Boolean,
        val currentGeofence: Geofence?,
        val geofenceCount: Int,
        val hasTriggeredClockIn: Boolean
    )
}
