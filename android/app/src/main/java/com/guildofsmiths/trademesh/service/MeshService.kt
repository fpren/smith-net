package com.guildofsmiths.trademesh.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.AdvertisingSet
import android.bluetooth.le.AdvertisingSetCallback
import android.bluetooth.le.AdvertisingSetParameters
import android.bluetooth.le.BluetoothLeAdvertiser
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.guildofsmiths.trademesh.R
import com.guildofsmiths.trademesh.data.Message
import com.guildofsmiths.trademesh.data.PeerRepository
import com.guildofsmiths.trademesh.engine.BoundaryEngine
import java.nio.ByteBuffer
import java.util.ArrayDeque
import java.util.UUID

/**
 * MeshService: BLE-based mesh communication service.
 * 
 * Beacon format (25 bytes max for BLE service data):
 * - senderId: 8 bytes (truncated/padded UTF-8)
 * - timestamp: 8 bytes (long, big-endian)
 * - content: up to 9 bytes (UTF-8, truncated if needed)
 * 
 * Channel invites are sent as special messages with content starting with "/invite:"
 * 
 * Total: 16 bytes fixed + up to 9 bytes content = 25 bytes max
 */
class MeshService : Service() {
    
    companion object {
        private const val TAG = "MeshService"
        
        /** Guild of Smiths mesh service UUID - FIXED for all devices */
        val MESH_SERVICE_UUID: UUID = UUID.fromString("0000F00D-0000-1000-8000-00805F9B34FB")
        val MESH_SERVICE_PARCEL_UUID = ParcelUuid(MESH_SERVICE_UUID)
        
        /** Notification channel for foreground service */
        private const val CHANNEL_ID = "mesh_service_channel"
        private const val NOTIFICATION_ID = 1001
        
        /** Scan idle timeout in milliseconds (5 minutes for testing) */
        private const val SCAN_IDLE_TIMEOUT_MS = 300_000L
        
        /** Advertise duration in milliseconds (10 seconds per message) */
        private const val ADVERTISE_DURATION_MS = 10_000L
        
        /** Maximum outbound queue size */
        private const val MAX_OUTBOUND_QUEUE_SIZE = 50
        
        /** Beacon payload constraints — BLE 4.x legacy ads limit: ~20 bytes service data */
        private const val SENDER_ID_BYTES = 4      // Shortened sender ID
        private const val CHANNEL_HASH_BYTES = 2   // 2-byte channel hash for routing
        private const val TIMESTAMP_BYTES = 4      // Use 4-byte timestamp (seconds, not ms)
        private const val MAX_CONTENT_BYTES = 10   // Message content (~10 chars due to BLE limits)
        private const val MAX_PAYLOAD_BYTES = SENDER_ID_BYTES + CHANNEL_HASH_BYTES + TIMESTAMP_BYTES + MAX_CONTENT_BYTES // 20
        
        /** Special channel hash for invites */
        private const val INVITE_CHANNEL_HASH: Short = 0x7FFF.toShort()
        
        /** Special channel hash for channel deletions (tombstones) */
        private const val DELETE_CHANNEL_HASH: Short = 0x7FFE.toShort()
        
        /** Special channel hash for ACK packets */
        private const val ACK_CHANNEL_HASH: Short = 0x7FFD.toShort()
    }
    
    private val binder = MeshBinder()
    
    private var bluetoothManager: BluetoothManager? = null
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bleAdvertiser: BluetoothLeAdvertiser? = null
    private var bleScanner: BluetoothLeScanner? = null
    
    private val handler = Handler(Looper.getMainLooper())
    
    private var isScanning = false
    private var isAdvertising = false
    
    /** Queue of messages to broadcast */
    private val outboundQueue = ArrayDeque<Message>()
    
    /** Set of recently seen message hashes for deduplication */
    private val recentPayloadHashes = LinkedHashSet<Int>()
    private val maxRecentHashes = 100
    
    /** Cache of recent outbound messages for retry lookup */
    private val recentOutboundMessages = LinkedHashMap<String, Message>(50, 0.75f, true)
    private val maxRecentOutbound = 50
    
    /** Scan timeout runnable */
    private val scanTimeoutRunnable = Runnable {
        Log.i(TAG, "⏱️ Scan idle timeout (${SCAN_IDLE_TIMEOUT_MS}ms) - stopping scan")
        stopScanning()
    }
    
    /** Advertise stop runnable */
    private val advertiseStopRunnable = Runnable {
        Log.d(TAG, "⏱️ Advertise duration complete - stopping")
        stopAdvertising()
        // Ensure scanning is still active after advertising
        if (!isScanning) {
            Log.d(TAG, "🔄 Restarting scan after advertise")
            startScanning()
        }
        processOutboundQueue()
    }
    
    inner class MeshBinder : Binder() {
        fun getService(): MeshService = this@MeshService
    }
    
    override fun onBind(intent: Intent?): IBinder = binder
    
    // Bluetooth state receiver to stop service when BT is disabled
    private val bluetoothStateReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == android.bluetooth.BluetoothAdapter.ACTION_STATE_CHANGED) {
                val state = intent.getIntExtra(
                    android.bluetooth.BluetoothAdapter.EXTRA_STATE,
                    android.bluetooth.BluetoothAdapter.ERROR
                )
                // #region agent log
                try {
                    val data = mapOf(
                        "sessionId" to "debug-session",
                        "runId" to "bluetooth-detection-test",
                        "hypothesisId" to "B",
                        "location" to "MeshService.kt:144",
                        "message" to "Bluetooth state change detected",
                        "data" to mapOf(
                            "newState" to state,
                            "stateName" to when (state) {
                                android.bluetooth.BluetoothAdapter.STATE_OFF -> "OFF"
                                android.bluetooth.BluetoothAdapter.STATE_TURNING_OFF -> "TURNING_OFF"
                                android.bluetooth.BluetoothAdapter.STATE_ON -> "ON"
                                android.bluetooth.BluetoothAdapter.STATE_TURNING_ON -> "TURNING_ON"
                                else -> "UNKNOWN"
                            },
                            "willStopService" to (state == android.bluetooth.BluetoothAdapter.STATE_OFF || state == android.bluetooth.BluetoothAdapter.STATE_TURNING_OFF)
                        ),
                        "timestamp" to System.currentTimeMillis()
                    )
                    val jsonPayload = org.json.JSONObject(data).toString()
                    val url = java.net.URL("http://127.0.0.1:7242/ingest/0adb3485-1a4e-45bf-a3c0-30e8c05573e2")
                    val connection = url.openConnection() as java.net.HttpURLConnection
                    connection.requestMethod = "POST"
                    connection.setRequestProperty("Content-Type", "application/json")
                    connection.doOutput = true
                    connection.outputStream.write(jsonPayload.toByteArray())
                    connection.inputStream.close()
                } catch (e: Exception) {
                    // Ignore logging errors
                }
                // #endregion

                when (state) {
                    android.bluetooth.BluetoothAdapter.STATE_OFF,
                    android.bluetooth.BluetoothAdapter.STATE_TURNING_OFF -> {
                        Log.i(TAG, "🔴 Bluetooth disabled - stopping MeshService")
                        stopSelf()
                    }
                }
            }
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "════════════════════════════════════════")
        Log.i(TAG, "🚀 MeshService onCreate")
        Log.i(TAG, "   UUID: $MESH_SERVICE_UUID")
        Log.i(TAG, "════════════════════════════════════════")
        
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        
        initializeBluetooth()
        BoundaryEngine.registerMeshService(this)
        
        // Set up ACK retry callback
        MeshAckManager.setRetryCallback { messageId ->
            retryMessageById(messageId)
        }
        
        // Register Bluetooth state receiver
        val filter = android.content.IntentFilter(android.bluetooth.BluetoothAdapter.ACTION_STATE_CHANGED)
        registerReceiver(bluetoothStateReceiver, filter)
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "▶️ MeshService onStartCommand - starting scan")
        startScanning()
        return START_STICKY
    }
    
    override fun onDestroy() {
        Log.i(TAG, "🛑 MeshService onDestroy")
        
        // Unregister Bluetooth state receiver
        try {
            unregisterReceiver(bluetoothStateReceiver)
        } catch (e: Exception) {
            Log.w(TAG, "Bluetooth receiver already unregistered")
        }
        
        handler.removeCallbacks(scanTimeoutRunnable)
        handler.removeCallbacks(advertiseStopRunnable)
        stopScanning()
        stopAdvertising()
        MeshAckManager.clearAll()
        BoundaryEngine.unregisterMeshService()
        BoundaryEngine.stopHeartbeat()
        
        // Remove the notification
        stopForeground(STOP_FOREGROUND_REMOVE)
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager?.cancel(NOTIFICATION_ID)
        
        super.onDestroy()
    }
    
    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Mesh Service",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "BLE mesh communication service"
            setShowBadge(false)
        }
        
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }
    
    private fun createNotification(): Notification {
        val pendingIntent = packageManager.getLaunchIntentForPackage(packageName)?.let {
            PendingIntent.getActivity(
                this, 0, it,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("smith net active")
            .setContentText("mesh running")
            .setSmallIcon(R.drawable.ic_mesh_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }
    
    private fun initializeBluetooth() {
        bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        bluetoothAdapter = bluetoothManager?.adapter
        
        if (bluetoothAdapter == null) {
            Log.e(TAG, "❌ Bluetooth not available on this device")
            return
        }
        
        if (bluetoothAdapter?.isEnabled != true) {
            Log.e(TAG, "❌ Bluetooth is disabled - please enable it")
            return
        }
        
        bleAdvertiser = bluetoothAdapter?.bluetoothLeAdvertiser
        bleScanner = bluetoothAdapter?.bluetoothLeScanner
        
        Log.i(TAG, "✅ Bluetooth initialized:")
        Log.i(TAG, "   Advertiser: ${if (bleAdvertiser != null) "available" else "NOT SUPPORTED"}")
        Log.i(TAG, "   Scanner: ${if (bleScanner != null) "available" else "NOT SUPPORTED"}")
        
        if (bleAdvertiser == null) {
            Log.e(TAG, "❌ BLE advertising not supported on this device!")
        }
        if (bleScanner == null) {
            Log.e(TAG, "❌ BLE scanning not supported on this device!")
        }
    }
    
    // ══════════════════════════════════════════════════════════════════
    // SERIALIZATION - Message <-> Bytes (25 bytes max)
    // ══════════════════════════════════════════════════════════════════
    
    /**
     * Get a 2-byte hash of a channel ID for routing.
     */
    private fun channelHash(channelId: String): Short {
        return (channelId.hashCode() and 0x7FFF).toShort() // Keep positive, reserve 0x7FFF for invites
    }
    
    /**
     * Serialize a Message to beacon bytes.
     * Format: [senderId: 4][channelHash: 2][timestamp: 4][content: up to 10]
     * Total max: 20 bytes (fits in BLE service data)
     */
    private fun serializeToBeacon(message: Message): ByteArray {
        val buffer = ByteBuffer.allocate(MAX_PAYLOAD_BYTES)
        
        // SenderId: 4 bytes fixed (truncate or pad with zeros)
        val senderBytes = message.senderId.toByteArray(Charsets.UTF_8)
        for (i in 0 until SENDER_ID_BYTES) {
            buffer.put(if (i < senderBytes.size) senderBytes[i] else 0)
        }
        
        // Channel hash: 2 bytes - use special hashes for special channels
        val hash = when (message.channelId) {
            "__INVITE__" -> INVITE_CHANNEL_HASH
            "__DELETE__" -> DELETE_CHANNEL_HASH
            "_ack" -> ACK_CHANNEL_HASH
            else -> channelHash(message.channelId)
        }
        Log.d(TAG, "📤 Serializing: channel=${message.channelId}, hash=$hash, content='${message.content}'")
        buffer.putShort(hash)
        
        // Timestamp: 4 bytes (seconds since epoch, not milliseconds)
        val timestampSeconds = (message.timestamp / 1000).toInt()
        buffer.putInt(timestampSeconds)
        
        // Content: up to 10 bytes (truncate if needed)
        val contentBytes = message.content.toByteArray(Charsets.UTF_8)
        val contentLen = minOf(contentBytes.size, MAX_CONTENT_BYTES)
        buffer.put(contentBytes, 0, contentLen)
        
        // Return only the bytes we wrote
        val totalLen = SENDER_ID_BYTES + CHANNEL_HASH_BYTES + TIMESTAMP_BYTES + contentLen
        val result = ByteArray(totalLen)
        buffer.rewind()
        buffer.get(result)
        
        return result
    }
    
    /**
     * Deserialize beacon bytes to a Message.
     * Returns null if parsing fails or message is for a channel we haven't joined.
     */
    private fun deserializeFromBeacon(data: ByteArray): Message? {
        val minLen = SENDER_ID_BYTES + CHANNEL_HASH_BYTES + TIMESTAMP_BYTES
        if (data.size < minLen) {
            Log.w(TAG, "   ⚠️ Payload too short: ${data.size} bytes (need at least $minLen)")
            return null
        }
        
        return try {
            val buffer = ByteBuffer.wrap(data)
            
            // SenderId: 4 bytes, trim trailing zeros
            val senderBytes = ByteArray(SENDER_ID_BYTES)
            buffer.get(senderBytes)
            val senderId = String(senderBytes, Charsets.UTF_8).trimEnd('\u0000')
            
            // Channel hash: 2 bytes
            val channelHashValue = buffer.getShort()
            
            // Timestamp: 4 bytes (seconds since epoch)
            val timestampSeconds = buffer.getInt()
            val timestamp = timestampSeconds.toLong() * 1000
            
            // Content: remaining bytes
            val contentLen = data.size - minLen
            val content = if (contentLen > 0) {
                val contentBytes = ByteArray(contentLen)
                buffer.get(contentBytes)
                String(contentBytes, Charsets.UTF_8)
            } else {
                ""
            }
            
            Log.d(TAG, "   📦 Parsed: sender=$senderId, channelHash=$channelHashValue, content='$content'")
            
            // Check if this is a channel invite (special hash)
            if (channelHashValue == INVITE_CHANNEL_HASH) {
                Log.i(TAG, "📨 Received channel invite: #$content from $senderId")
                BoundaryEngine.onChannelInviteReceived(content.hashCode(), content, senderId)
                return null // Don't display invite as a regular message
            }
            
            // Check if this is a channel deletion (tombstone)
            if (channelHashValue == DELETE_CHANNEL_HASH) {
                Log.i(TAG, "🗑️ Received channel deletion: #$content from $senderId")
                BoundaryEngine.onChannelDeletionReceived(content, senderId)
                return null // Don't display deletion as a regular message
            }
            
            // Check if this is an ACK packet
            if (channelHashValue == ACK_CHANNEL_HASH) {
                // Return a message object so ACK can be processed
                val id = "${senderId}_${timestamp}_ack"
                return Message(
                    id = id,
                    beaconId = "default",
                    channelId = "_ack",
                    senderId = senderId,
                    senderName = senderId,
                    timestamp = timestamp,
                    content = content,
                    isMeshOrigin = true
                )
            }
            
            // Resolve channel by hash - only show if we're a member
            val hashForLookup = channelHashValue.toInt() and 0xFFFF // Ensure unsigned
            Log.d(TAG, "   🔍 Looking up channel hash: $hashForLookup (raw: $channelHashValue)")
            val channelId = BoundaryEngine.resolveChannelByHash(hashForLookup)
            if (channelId == null) {
                Log.d(TAG, "   ⚠️ Message for unknown/unjoined channel (hash: $hashForLookup) - ignoring")
                return null
            }
            Log.d(TAG, "   ✅ Resolved to channel: #$channelId")
            
            // Generate a deterministic ID from payload for deduplication
            val id = "${senderId}_${timestamp}_${channelHashValue}"
            
            // Look up peer's display name if we've seen them before
            val knownPeer = PeerRepository.getPeer(senderId)
            val displayName = knownPeer?.userName ?: senderId
            
            Message(
                id = id,
                beaconId = "default",
                channelId = channelId,
                senderId = senderId,
                senderName = displayName,
                timestamp = timestamp,
                content = content,
                isMeshOrigin = true
            )
        } catch (e: Exception) {
            Log.e(TAG, "   ❌ Parse error: ${e.message}")
            null
        }
    }
    
    /**
     * Broadcast a channel invite over BLE.
     * Creates a special message with INVITE_CHANNEL_HASH as channelId.
     * Content format: "channelName|channelId" to include both name and UUID
     */
    fun broadcastInvite(channelId: String, channelName: String, senderId: String) {
        Log.i(TAG, "📢 Broadcasting channel invite: #$channelName ($channelId)")

        // Create invite content with both name and ID: "name|uuid"
        val inviteContent = "$channelName|$channelId".take(MAX_CONTENT_BYTES)

        // Create an invite message - use special channelId that will serialize to INVITE_CHANNEL_HASH
        val inviteMessage = Message(
            senderId = senderId,
            senderName = senderId,
            channelId = "__INVITE__", // Special marker - will be replaced in serialization
            content = inviteContent,
            isMeshOrigin = true
        )

        // Manually serialize with invite hash and broadcast
        broadcastInvitePayload(inviteMessage)
    }
    
    /**
     * Broadcast a channel deletion (tombstone) over BLE.
     * Notifies peers that a channel has been deleted by its owner.
     */
    fun broadcastDeletion(channelId: String, channelName: String, senderId: String) {
        Log.i(TAG, "🗑️ Broadcasting channel deletion: #$channelName")
        
        val deletionMessage = Message(
            senderId = senderId,
            senderName = senderId,
            channelId = "__DELETE__", // Special marker
            content = channelName.take(MAX_CONTENT_BYTES), // Channel name in content
            isMeshOrigin = true
        )
        
        broadcastDeletionPayload(deletionMessage)
    }
    
    /**
     * Broadcast deletion payload directly.
     */
    private fun broadcastDeletionPayload(message: Message) {
        val buffer = ByteBuffer.allocate(MAX_PAYLOAD_BYTES)
        
        // SenderId: 4 bytes
        val senderBytes = message.senderId.toByteArray(Charsets.UTF_8)
        for (i in 0 until SENDER_ID_BYTES) {
            buffer.put(if (i < senderBytes.size) senderBytes[i] else 0)
        }
        
        // Channel hash: special deletion marker
        buffer.putShort(DELETE_CHANNEL_HASH)
        
        // Timestamp: 4 bytes
        val timestampSeconds = (message.timestamp / 1000).toInt()
        buffer.putInt(timestampSeconds)
        
        // Content: channel name (up to MAX_CONTENT_BYTES)
        val contentBytes = message.content.toByteArray(Charsets.UTF_8)
        val contentLen = minOf(contentBytes.size, MAX_CONTENT_BYTES)
        buffer.put(contentBytes, 0, contentLen)
        
        val totalLen = SENDER_ID_BYTES + CHANNEL_HASH_BYTES + TIMESTAMP_BYTES + contentLen
        val payload = ByteArray(totalLen)
        buffer.rewind()
        buffer.get(payload)
        
        Log.i(TAG, "🗑️ Broadcasting deletion for: ${message.content}")
        Log.i(TAG, "   Payload size: ${payload.size} bytes")
        
        startAdvertisingPayload(payload)
    }
    
    /**
     * Broadcast invite payload directly (bypasses normal queue).
     */
    private fun broadcastInvitePayload(message: Message) {
        val buffer = ByteBuffer.allocate(MAX_PAYLOAD_BYTES)
        
        // SenderId: 4 bytes (matches current payload format)
        val senderBytes = message.senderId.toByteArray(Charsets.UTF_8)
        for (i in 0 until SENDER_ID_BYTES) {
            buffer.put(if (i < senderBytes.size) senderBytes[i] else 0)
        }
        
        // Channel hash: special invite marker
        buffer.putShort(INVITE_CHANNEL_HASH)
        
        // Timestamp: 4 bytes (seconds since epoch, matches current format)
        val timestampSeconds = (message.timestamp / 1000).toInt()
        buffer.putInt(timestampSeconds)
        
        // Content: channel name (up to MAX_CONTENT_BYTES)
        val contentBytes = message.content.toByteArray(Charsets.UTF_8)
        val contentLen = minOf(contentBytes.size, MAX_CONTENT_BYTES)
        buffer.put(contentBytes, 0, contentLen)
        
        val totalLen = SENDER_ID_BYTES + CHANNEL_HASH_BYTES + TIMESTAMP_BYTES + contentLen
        val payload = ByteArray(totalLen)
        buffer.rewind()
        buffer.get(payload)
        
        Log.i(TAG, "📢 Broadcasting invite for: ${message.content}")
        Log.i(TAG, "   Payload size: ${payload.size} bytes")
        
        // Broadcast immediately
        startAdvertisingPayload(payload)
    }
    
    /**
     * Start advertising a raw payload.
     */
    private fun startAdvertisingPayload(payload: ByteArray) {
        if (!checkBlePermissions()) {
            Log.e(TAG, "❌ Missing BLE permissions")
            return
        }
        
        if (isAdvertising) {
            stopAdvertising()
        }
        
        val advertiseData = AdvertiseData.Builder()
            .addServiceUuid(MESH_SERVICE_PARCEL_UUID)
            .addServiceData(MESH_SERVICE_PARCEL_UUID, payload)
            .setIncludeDeviceName(false)
            .setIncludeTxPowerLevel(false)
            .build()
        
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
            .setConnectable(false)
            .setTimeout(ADVERTISE_DURATION_MS.toInt())
            .build()
        
        try {
            bleAdvertiser?.startAdvertising(settings, advertiseData, advertiseCallback)
            Log.i(TAG, "📤 Invite advertisement started")
        } catch (e: SecurityException) {
            Log.e(TAG, "❌ SecurityException: ${e.message}")
        }
    }
    
    // ══════════════════════════════════════════════════════════════════
    // SCANNING
    // ══════════════════════════════════════════════════════════════════
    
    fun startScanning() {
        if (isScanning) {
            Log.d(TAG, "📡 Already scanning - resetting timeout")
            resetScanTimeout()
            return
        }
        
        if (!checkBlePermissions()) {
            Log.e(TAG, "❌ BLE permissions not granted - cannot scan")
            return
        }
        
        val scanner = bleScanner
        if (scanner == null) {
            Log.e(TAG, "❌ BLE scanner not available")
            return
        }
        
        // Filter for our exact service UUID
        val scanFilter = ScanFilter.Builder()
            .setServiceUuid(MESH_SERVICE_PARCEL_UUID)
            .build()
        
        // Scan settings - scan for BOTH legacy and extended advertisements
        val scanSettingsBuilder = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_BALANCED)
            .setReportDelay(0) // Immediate reporting
        
        // On API 26+, explicitly enable ALL PHYs but keep legacy=true to receive both types
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            scanSettingsBuilder.setPhy(ScanSettings.PHY_LE_ALL_SUPPORTED)
            // Note: setLegacy(true) is the default - receives legacy ads
            // Extended ads are received automatically when PHY_LE_ALL_SUPPORTED is set
        }
        
        val scanSettings = scanSettingsBuilder.build()
        
        try {
            scanner.startScan(listOf(scanFilter), scanSettings, scanCallback)
            isScanning = true
            BoundaryEngine.updateScanningState(true)
            Log.i(TAG, "════════════════════════════════════════")
            Log.i(TAG, "📡 SCAN STARTED")
            Log.i(TAG, "   Filter UUID: $MESH_SERVICE_UUID")
            Log.i(TAG, "   Mode: LOW_POWER")
            Log.i(TAG, "   Timeout: ${SCAN_IDLE_TIMEOUT_MS}ms")
            Log.i(TAG, "════════════════════════════════════════")
            resetScanTimeout()
            
            // Start heartbeat to keep peers aware of our presence
            BoundaryEngine.startHeartbeat()
        } catch (e: SecurityException) {
            Log.e(TAG, "❌ SecurityException starting scan: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error starting scan: ${e.message}")
        }
    }
    
    fun stopScanning() {
        if (!isScanning) return
        
        handler.removeCallbacks(scanTimeoutRunnable)
        
        try {
            bleScanner?.stopScan(scanCallback)
            isScanning = false
            BoundaryEngine.updateScanningState(false)
            BoundaryEngine.stopHeartbeat()
            Log.i(TAG, "⏹️ SCAN STOPPED")
        } catch (e: SecurityException) {
            Log.e(TAG, "❌ SecurityException stopping scan: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error stopping scan: ${e.message}")
        }
    }
    
    /**
     * Check if currently scanning.
     */
    fun isScanningActive(): Boolean = isScanning
    
    /**
     * Check if currently advertising.
     */
    fun isAdvertisingActive(): Boolean = isAdvertising
    
    private fun resetScanTimeout() {
        handler.removeCallbacks(scanTimeoutRunnable)
        handler.postDelayed(scanTimeoutRunnable, SCAN_IDLE_TIMEOUT_MS)
    }
    
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            handleScanResult(result)
        }
        
        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            Log.d(TAG, "📡 Batch scan: ${results.size} results")
            results.forEach { handleScanResult(it) }
        }
        
        override fun onScanFailed(errorCode: Int) {
            val errorMsg = when (errorCode) {
                SCAN_FAILED_ALREADY_STARTED -> "Already started"
                SCAN_FAILED_APPLICATION_REGISTRATION_FAILED -> "App registration failed"
                SCAN_FAILED_INTERNAL_ERROR -> "Internal error"
                SCAN_FAILED_FEATURE_UNSUPPORTED -> "Feature unsupported"
                else -> "Unknown error"
            }
            Log.e(TAG, "❌ SCAN FAILED: $errorMsg (code $errorCode)")
            isScanning = false
            
            // Retry after delay
            handler.postDelayed({ startScanning() }, 5000)
        }
    }
    
    private fun handleScanResult(result: ScanResult) {
        val device = result.device
        val rssi = result.rssi
        val scanRecord = result.scanRecord
        
        Log.d(TAG, "────────────────────────────────────────")
        Log.d(TAG, "📥 SCAN RESULT")
        Log.d(TAG, "   Device: ${device?.address ?: "unknown"}")
        Log.d(TAG, "   RSSI: $rssi dBm")
        
        if (scanRecord == null) {
            Log.w(TAG, "   ⚠️ No scan record")
            return
        }
        
        // Get service data for our UUID
        val serviceData = scanRecord.getServiceData(MESH_SERVICE_PARCEL_UUID)
        
        if (serviceData == null) {
            Log.w(TAG, "   ⚠️ No service data for our UUID")
            // Log what UUIDs we did find
            val uuids = scanRecord.serviceUuids
            Log.d(TAG, "   Found UUIDs: ${uuids?.joinToString() ?: "none"}")
            return
        }
        
        Log.d(TAG, "   ✅ Service data: ${serviceData.size} bytes")
        Log.d(TAG, "   Hex: ${serviceData.joinToString("") { "%02X".format(it) }}")
        
        // Reset scan timeout on activity
        resetScanTimeout()
        
        // Extract sender ID early for peer tracking (even if message is filtered out)
        val senderId = extractSenderId(serviceData)
        if (senderId != null) {
            // Track peer immediately - even if we don't display their message
            PeerRepository.onPeerSeen(senderId, senderId, rssi)
            Log.d(TAG, "   👤 Peer tracked: $senderId")
        }
        
        // Deduplicate by payload hash
        val payloadHash = serviceData.contentHashCode()
        if (recentPayloadHashes.contains(payloadHash)) {
            Log.d(TAG, "   ⏭️ Duplicate payload - ignoring")
            return
        }
        
        // Add to recent hashes (with LRU eviction)
        recentPayloadHashes.add(payloadHash)
        if (recentPayloadHashes.size > maxRecentHashes) {
            recentPayloadHashes.remove(recentPayloadHashes.first())
        }
        
        // Parse message
        val message = deserializeFromBeacon(serviceData)
        if (message == null) {
            Log.d(TAG, "   ℹ️ Message filtered (invite or unjoined channel)")
            return
        }
        
        // Check if this is an ACK packet
        if (MeshAckManager.isAckPacket(message.content)) {
            val originalMessageId = MeshAckManager.extractAckMessageId(message.content)
            if (originalMessageId != null) {
                Log.d(TAG, "   📨 ACK packet received for: ${originalMessageId.take(8)}...")
                MeshAckManager.onAckReceived(originalMessageId)
            }
            return
        }
        
        // Regular message (chunking disabled - unreliable over BLE)
        Log.i(TAG, "   ✅ PARSED MESSAGE:")
        Log.i(TAG, "      ID: ${message.id}")
        Log.i(TAG, "      Sender: ${message.senderId}")
        Log.i(TAG, "      Content: \"${message.content}\"")
        Log.i(TAG, "      Timestamp: ${message.timestamp}")
        Log.i(TAG, "      RSSI: $rssi dBm")
        Log.d(TAG, "────────────────────────────────────────")
        
        // Send ACK for non-presence messages
        if (MeshAckManager.requiresAck(message.channelId, message.content)) {
            sendAck(message.id, message.senderId)
        }
        
        // Route to BoundaryEngine with RSSI for peer tracking
        BoundaryEngine.onMeshMessageReceived(this, message, rssi)
    }
    
    /**
     * Extract sender ID from raw payload for peer tracking.
     * Works even if the full message can't be parsed.
     */
    private fun extractSenderId(data: ByteArray): String? {
        if (data.size < SENDER_ID_BYTES) return null
        return try {
            val senderBytes = data.copyOfRange(0, SENDER_ID_BYTES)
            String(senderBytes, Charsets.UTF_8).trimEnd('\u0000')
        } catch (e: Exception) {
            null
        }
    }
    
    // ══════════════════════════════════════════════════════════════════
    // ADVERTISING
    // ══════════════════════════════════════════════════════════════════
    
    /**
     * Broadcast a message via BLE advertising.
     * Automatically chunks long messages into multiple advertisements.
     */
    fun broadcastMessage(message: Message) {
        Log.i(TAG, "════════════════════════════════════════")
        Log.i(TAG, "📤 BROADCAST REQUEST")
        Log.i(TAG, "   Content: \"${message.content}\"")
        Log.i(TAG, "   Sender: ${message.senderId}")
        
        // For now, truncate long messages with "..." indicator
        // BLE mesh payload limit is ~10 chars, chunking is unreliable over air
        val truncatedContent = if (message.content.length > 10) {
            message.content.take(7) + "..."
        } else {
            message.content
        }
        
        val finalMessage = if (truncatedContent != message.content) {
            Log.i(TAG, "   ⚠️ Truncating: '${message.content}' → '$truncatedContent'")
            message.copy(content = truncatedContent)
        } else {
            message
        }
        
        // Broadcast (potentially truncated) message
        broadcastSingleMessage(finalMessage)
    }
    
    /**
     * Broadcast a single message (or chunk) via BLE advertising.
     */
    private fun broadcastSingleMessage(message: Message) {
        Log.d(TAG, "📤 Broadcasting: \"${message.content}\"")
        
        // Register for ACK tracking if required
        if (MeshAckManager.requiresAck(message.channelId, message.content)) {
            MeshAckManager.registerOutbound(message.id, message.content)
            // Cache for retry
            synchronized(recentOutboundMessages) {
                recentOutboundMessages[message.id] = message
                if (recentOutboundMessages.size > maxRecentOutbound) {
                    val oldest = recentOutboundMessages.keys.first()
                    recentOutboundMessages.remove(oldest)
                }
            }
        }
        
        if (!checkBlePermissions()) {
            Log.e(TAG, "   ❌ BLE permissions not granted")
            queueOutboundMessage(message)
            return
        }
        
        val advertiser = bleAdvertiser
        if (advertiser == null) {
            Log.e(TAG, "   ❌ BLE advertiser not available")
            queueOutboundMessage(message)
            return
        }
        
        // Serialize message
        val payload = serializeToBeacon(message)
        Log.i(TAG, "   Payload: ${payload.size} bytes")
        Log.i(TAG, "   Hex: ${payload.joinToString("") { "%02X".format(it) }}")
        
        if (payload.size > MAX_PAYLOAD_BYTES) {
            Log.e(TAG, "   ❌ Payload too large: ${payload.size} > $MAX_PAYLOAD_BYTES")
            queueOutboundMessage(message)
            return
        }
        
        // Stop any existing advertising first
        if (isAdvertising) {
            Log.d(TAG, "   Stopping previous advertisement...")
            stopAdvertising()
        }
        
        // Always use legacy advertising - extended ads not reliably supported on most devices
        // BLE 4.x hardware limits service data to ~20 bytes (10 chars after headers)
        startLegacyAdvertising(advertiser, payload, message)
    }
    
    @Suppress("DEPRECATION")
    private fun startLegacyAdvertising(advertiser: BluetoothLeAdvertiser, payload: ByteArray, message: Message) {
        // Legacy advertising has ~20 byte limit for service data
        val truncatedPayload = if (payload.size > 20) payload.copyOf(20) else payload
        
        val advertiseSettings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_POWER)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
            .setConnectable(false)
            .setTimeout(0)
            .build()
        
        val advertiseData = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .setIncludeTxPowerLevel(false)
            .addServiceUuid(MESH_SERVICE_PARCEL_UUID)
            .addServiceData(MESH_SERVICE_PARCEL_UUID, truncatedPayload)
            .build()
        
        try {
            advertiser.startAdvertising(advertiseSettings, advertiseData, advertiseCallback)
            Log.i(TAG, "   ⏳ Starting legacy advertisement (${truncatedPayload.size} bytes)...")
            Log.i(TAG, "════════════════════════════════════════")
        } catch (e: SecurityException) {
            Log.e(TAG, "   ❌ SecurityException: ${e.message}")
            queueOutboundMessage(message)
        } catch (e: Exception) {
            Log.e(TAG, "   ❌ Error: ${e.message}")
            queueOutboundMessage(message)
        }
    }
    
    private var currentAdvertisingSet: AdvertisingSet? = null
    
    private fun startExtendedAdvertising(advertiser: BluetoothLeAdvertiser, payload: ByteArray, message: Message) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            startLegacyAdvertising(advertiser, payload, message)
            return
        }
        
        try {
            val parameters = AdvertisingSetParameters.Builder()
                .setLegacyMode(false) // Use extended advertising
                .setConnectable(false)
                .setScannable(true)
                .setInterval(AdvertisingSetParameters.INTERVAL_LOW) // ~100ms
                .setTxPowerLevel(AdvertisingSetParameters.TX_POWER_MEDIUM)
                .build()
            
            val advertiseData = AdvertiseData.Builder()
                .setIncludeDeviceName(false)
                .setIncludeTxPowerLevel(false)
                .addServiceUuid(MESH_SERVICE_PARCEL_UUID)
                .addServiceData(MESH_SERVICE_PARCEL_UUID, payload)
                .build()
            
            val extendedCallback = object : AdvertisingSetCallback() {
                override fun onAdvertisingSetStarted(advertisingSet: AdvertisingSet?, txPower: Int, status: Int) {
                    if (status == AdvertisingSetCallback.ADVERTISE_SUCCESS) {
                        currentAdvertisingSet = advertisingSet
                        isAdvertising = true
                        Log.i(TAG, "════════════════════════════════════════")
                        Log.i(TAG, "📢 EXTENDED ADVERTISE STARTED")
                        Log.i(TAG, "   Payload: ${payload.size} bytes")
                        Log.i(TAG, "   TxPower: $txPower")
                        Log.i(TAG, "   Duration: ${ADVERTISE_DURATION_MS}ms")
                        Log.i(TAG, "════════════════════════════════════════")
                        
                        if (!isScanning) startScanning()
                        handler.postDelayed({ stopExtendedAdvertising() }, ADVERTISE_DURATION_MS)
                    } else {
                        Log.e(TAG, "❌ Extended advertising failed: $status, falling back to legacy")
                        startLegacyAdvertising(advertiser, payload, message)
                    }
                }
                
                override fun onAdvertisingSetStopped(advertisingSet: AdvertisingSet?) {
                    isAdvertising = false
                    currentAdvertisingSet = null
                    Log.i(TAG, "⏹️ EXTENDED ADVERTISE STOPPED")
                    processOutboundQueue()
                }
            }
            
            advertiser.startAdvertisingSet(parameters, advertiseData, null, null, null, extendedCallback)
            Log.i(TAG, "   ⏳ Starting extended advertisement (${payload.size} bytes)...")
            
        } catch (e: Exception) {
            Log.e(TAG, "   ❌ Extended advertising error: ${e.message}, falling back to legacy")
            startLegacyAdvertising(advertiser, payload, message)
        }
    }
    
    private fun stopExtendedAdvertising() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            currentAdvertisingSet?.let { set ->
                try {
                    bleAdvertiser?.stopAdvertisingSet(object : AdvertisingSetCallback() {})
                } catch (e: Exception) {
                    Log.e(TAG, "Error stopping extended advertising: ${e.message}")
                }
            }
            currentAdvertisingSet = null
        }
        isAdvertising = false
        processOutboundQueue()
    }
    
    fun stopAdvertising() {
        handler.removeCallbacks(advertiseStopRunnable)
        
        if (!isAdvertising) return
        
        try {
            bleAdvertiser?.stopAdvertising(advertiseCallback)
            isAdvertising = false
            Log.i(TAG, "⏹️ ADVERTISE STOPPED")
        } catch (e: SecurityException) {
            Log.e(TAG, "❌ SecurityException stopping advertising: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error stopping advertising: ${e.message}")
        }
    }
    
    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            isAdvertising = true
            Log.i(TAG, "════════════════════════════════════════")
            Log.i(TAG, "📢 ADVERTISE STARTED")
            Log.i(TAG, "   Mode: ${settingsInEffect.mode}")
            Log.i(TAG, "   TxPower: ${settingsInEffect.txPowerLevel}")
            Log.i(TAG, "   Duration: ${ADVERTISE_DURATION_MS}ms")
            Log.i(TAG, "════════════════════════════════════════")
            
            // Ensure scanning is active to receive responses
            if (!isScanning) {
                startScanning()
            }
            
            // Schedule stop after duration
            handler.postDelayed(advertiseStopRunnable, ADVERTISE_DURATION_MS)
        }
        
        override fun onStartFailure(errorCode: Int) {
            isAdvertising = false
            val errorMsg = when (errorCode) {
                ADVERTISE_FAILED_DATA_TOO_LARGE -> "Data too large"
                ADVERTISE_FAILED_TOO_MANY_ADVERTISERS -> "Too many advertisers"
                ADVERTISE_FAILED_ALREADY_STARTED -> "Already started"
                ADVERTISE_FAILED_INTERNAL_ERROR -> "Internal error"
                ADVERTISE_FAILED_FEATURE_UNSUPPORTED -> "Feature unsupported"
                else -> "Unknown error"
            }
            Log.e(TAG, "════════════════════════════════════════")
            Log.e(TAG, "❌ ADVERTISE FAILED: $errorMsg (code $errorCode)")
            Log.e(TAG, "════════════════════════════════════════")
            
            // Retry next message after delay
            handler.postDelayed({ processOutboundQueue() }, 2000)
        }
    }
    
    // ══════════════════════════════════════════════════════════════════
    // OUTBOUND QUEUE
    // ══════════════════════════════════════════════════════════════════
    
    private fun queueOutboundMessage(message: Message) {
        synchronized(outboundQueue) {
            if (outboundQueue.size >= MAX_OUTBOUND_QUEUE_SIZE) {
                Log.w(TAG, "⚠️ Outbound queue full - dropping oldest")
                outboundQueue.removeFirst()
            }
            outboundQueue.addLast(message)
            Log.d(TAG, "📥 Queued message (queue size: ${outboundQueue.size})")
        }
    }
    
    private fun processOutboundQueue() {
        val message: Message?
        synchronized(outboundQueue) {
            message = outboundQueue.pollFirst()
        }
        
        if (message != null) {
            Log.d(TAG, "📤 Processing queued message...")
            broadcastMessage(message)
        }
    }
    
    fun getOutboundQueueSize(): Int = outboundQueue.size
    
    /**
     * Retry a message by ID (called by MeshAckManager).
     */
    private fun retryMessageById(messageId: String) {
        val message = synchronized(recentOutboundMessages) {
            recentOutboundMessages[messageId]
        }
        
        if (message == null) {
            Log.w(TAG, "⚠️ Cannot retry - message not in cache: ${messageId.take(8)}...")
            return
        }
        
        Log.i(TAG, "🔄 Retrying broadcast: ${messageId.take(8)}...")
        
        // Re-serialize and broadcast (don't re-register for ACK)
        val payload = serializeToBeacon(message)
        broadcastPayloadDirect(payload)
    }
    
    /**
     * Broadcast a raw payload directly (for retries and ACKs).
     */
    private fun broadcastPayloadDirect(payload: ByteArray) {
        if (!checkBlePermissions()) return
        
        val advertiser = bleAdvertiser ?: return
        
        if (isAdvertising) {
            stopAdvertising()
        }
        
        val advertiseSettings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_POWER)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
            .setConnectable(false)
            .setTimeout(0)
            .build()
        
        val advertiseData = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .setIncludeTxPowerLevel(false)
            .addServiceUuid(MESH_SERVICE_PARCEL_UUID)
            .addServiceData(MESH_SERVICE_PARCEL_UUID, payload)
            .build()
        
        try {
            advertiser.startAdvertising(advertiseSettings, advertiseData, advertiseCallback)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Direct broadcast error: ${e.message}")
        }
    }
    
    /**
     * Send an ACK for a received message.
     */
    fun sendAck(originalMessageId: String, originalSenderId: String) {
        Log.d(TAG, "📨 Sending ACK for: ${originalMessageId.take(8)}...")
        
        val ackContent = MeshAckManager.createAckContent(originalMessageId)
        
        val ackMessage = Message(
            senderId = com.guildofsmiths.trademesh.data.UserPreferences.getUserId(),
            senderName = com.guildofsmiths.trademesh.data.UserPreferences.getDisplayName(),
            channelId = "_ack", // Special ACK channel
            content = ackContent,
            isMeshOrigin = true
        )
        
        val payload = serializeToBeacon(ackMessage)
        broadcastPayloadDirect(payload)
    }
    
    // ══════════════════════════════════════════════════════════════════
    // PERMISSIONS
    // ══════════════════════════════════════════════════════════════════
    
    private fun checkBlePermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val scan = ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN)
            val advertise = ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADVERTISE)
            val connect = ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
            
            val hasAll = scan == PackageManager.PERMISSION_GRANTED &&
                    advertise == PackageManager.PERMISSION_GRANTED &&
                    connect == PackageManager.PERMISSION_GRANTED
            
            if (!hasAll) {
                Log.e(TAG, "❌ Missing BLE permissions:")
                Log.e(TAG, "   BLUETOOTH_SCAN: ${scan == PackageManager.PERMISSION_GRANTED}")
                Log.e(TAG, "   BLUETOOTH_ADVERTISE: ${advertise == PackageManager.PERMISSION_GRANTED}")
                Log.e(TAG, "   BLUETOOTH_CONNECT: ${connect == PackageManager.PERMISSION_GRANTED}")
            }
            hasAll
        } else {
            val location = ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            if (location != PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, "❌ Missing ACCESS_FINE_LOCATION permission")
            }
            location == PackageManager.PERMISSION_GRANTED
        }
    }
    
    // ══════════════════════════════════════════════════════════════════
    // STATUS
    // ══════════════════════════════════════════════════════════════════
    
    fun isScanning(): Boolean = isScanning
    fun isAdvertising(): Boolean = isAdvertising
}
