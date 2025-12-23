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
        
        /** Beacon payload constraints — reduced for broader device support */
        private const val SENDER_ID_BYTES = 4      // Shortened sender ID
        private const val CHANNEL_HASH_BYTES = 2   // 2-byte channel hash for routing
        private const val TIMESTAMP_BYTES = 4      // Use 4-byte timestamp (seconds, not ms)
        private const val MAX_CONTENT_BYTES = 10   // Message content
        private const val MAX_PAYLOAD_BYTES = SENDER_ID_BYTES + CHANNEL_HASH_BYTES + TIMESTAMP_BYTES + MAX_CONTENT_BYTES // 20
        
        /** Special channel hash for invites */
        private const val INVITE_CHANNEL_HASH: Short = 0x7FFF.toShort()
        
        /** Special channel hash for channel deletions (tombstones) */
        private const val DELETE_CHANNEL_HASH: Short = 0x7FFE.toShort()
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
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "▶️ MeshService onStartCommand - starting scan")
        startScanning()
        return START_STICKY
    }
    
    override fun onDestroy() {
        Log.i(TAG, "🛑 MeshService onDestroy")
        handler.removeCallbacks(scanTimeoutRunnable)
        handler.removeCallbacks(advertiseStopRunnable)
        stopScanning()
        stopAdvertising()
        BoundaryEngine.unregisterMeshService()
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
        
        // Channel hash: 2 bytes
        val hash = channelHash(message.channelId)
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
     */
    fun broadcastInvite(channelId: String, channelName: String, senderId: String) {
        Log.i(TAG, "📢 Broadcasting channel invite: #$channelName")
        
        // Create an invite message - use special channelId that will serialize to INVITE_CHANNEL_HASH
        val inviteMessage = Message(
            senderId = senderId,
            senderName = senderId,
            channelId = "__INVITE__", // Special marker - will be replaced in serialization
            content = channelName.take(MAX_CONTENT_BYTES), // Channel name goes in content
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
        
        // Balanced scan settings for better reception
        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_BALANCED)
            .setReportDelay(0) // Immediate reporting
            .build()
        
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
        
        Log.i(TAG, "   ✅ PARSED MESSAGE:")
        Log.i(TAG, "      ID: ${message.id}")
        Log.i(TAG, "      Sender: ${message.senderId}")
        Log.i(TAG, "      Content: \"${message.content}\"")
        Log.i(TAG, "      Timestamp: ${message.timestamp}")
        Log.i(TAG, "      RSSI: $rssi dBm")
        Log.d(TAG, "────────────────────────────────────────")
        
        // Route to BoundaryEngine with RSSI for peer tracking
        BoundaryEngine.onMeshMessageReceived(message, rssi)
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
     * Uses low-power, non-connectable mode for battery efficiency.
     */
    fun broadcastMessage(message: Message) {
        Log.i(TAG, "════════════════════════════════════════")
        Log.i(TAG, "📤 BROADCAST REQUEST")
        Log.i(TAG, "   Content: \"${message.content}\"")
        Log.i(TAG, "   Sender: ${message.senderId}")
        
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
        
        // Build advertise settings - LOW POWER, NON-CONNECTABLE
        val advertiseSettings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_POWER)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM) // Medium for better range
            .setConnectable(false)
            .setTimeout(0) // No timeout - we manage it ourselves
            .build()
        
        // Build advertise data with service UUID and service data
        val advertiseData = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .setIncludeTxPowerLevel(false)
            .addServiceUuid(MESH_SERVICE_PARCEL_UUID)
            .addServiceData(MESH_SERVICE_PARCEL_UUID, payload)
            .build()
        
        try {
            advertiser.startAdvertising(advertiseSettings, advertiseData, advertiseCallback)
            Log.i(TAG, "   ⏳ Starting advertisement...")
            Log.i(TAG, "════════════════════════════════════════")
        } catch (e: SecurityException) {
            Log.e(TAG, "   ❌ SecurityException: ${e.message}")
            queueOutboundMessage(message)
        } catch (e: Exception) {
            Log.e(TAG, "   ❌ Error: ${e.message}")
            queueOutboundMessage(message)
        }
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
