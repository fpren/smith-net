package com.guildofsmiths.trademesh.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.guildofsmiths.trademesh.data.Beacon
import com.guildofsmiths.trademesh.data.BeaconRepository
import com.guildofsmiths.trademesh.data.Channel
import com.guildofsmiths.trademesh.data.ChannelType
import com.guildofsmiths.trademesh.data.MediaAttachment
import com.guildofsmiths.trademesh.data.MediaType
import com.guildofsmiths.trademesh.data.Message
import com.guildofsmiths.trademesh.data.MessageRepository
import com.guildofsmiths.trademesh.data.UserPreferences
import com.guildofsmiths.trademesh.engine.BoundaryEngine
import com.guildofsmiths.trademesh.service.MediaHelper
import com.guildofsmiths.trademesh.service.MediaUploadManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

/**
 * ViewModel for the conversation screen.
 * Manages messages for a specific beacon and channel.
 */
class ConversationViewModel(application: Application) : AndroidViewModel(application) {
    
    /** Current beacon ID */
    private val _beaconId = MutableStateFlow("default")
    val beaconId: StateFlow<String> = _beaconId.asStateFlow()
    
    /** Current channel ID */
    private val _channelId = MutableStateFlow("general")
    val channelId: StateFlow<String> = _channelId.asStateFlow()
    
    /** Observable message list filtered by current beacon/channel */
    val messages: StateFlow<List<Message>> = combine(
        MessageRepository.allMessages,
        _beaconId,
        _channelId
    ) { messages, beaconId, channelId ->
        messages.filter { it.beaconId == beaconId && it.channelId == channelId }
            .sortedBy { it.timestamp }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )
    
    /** Current channel info - handles regular channels and DM channels */
    val currentChannel: StateFlow<Channel?> = combine(
        BeaconRepository.beacons,
        _beaconId,
        _channelId
    ) { beacons, beaconId, channelId ->
        // Check if it's a DM channel (starts with "dm_")
        if (channelId.startsWith("dm_")) {
            // Create a synthetic DM channel object
            Channel(
                id = channelId,
                beaconId = beaconId,
                name = channelId,  // Will be overridden by UI based on peer name
                type = ChannelType.DM
            )
        } else {
            beacons.find { it.id == beaconId }?.channels?.find { it.id == channelId }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )
    
    /** Current beacon info */
    val currentBeacon: StateFlow<Beacon?> = combine(
        BeaconRepository.beacons,
        _beaconId
    ) { beacons, beaconId ->
        beacons.find { it.id == beaconId }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )
    
    /** Local user ID (from UserPreferences) */
    private val localUserId: String = UserPreferences.getUserId()
    
    /** Local user display name (from UserPreferences) */
    private var localUserName: String = UserPreferences.getDisplayName()
    
    /**
     * Set the current beacon and channel.
     */
    fun setChannel(beaconId: String, channelId: String) {
        _beaconId.value = beaconId
        _channelId.value = channelId
        
        // Set as active in repository
        BeaconRepository.setActiveBeacon(beaconId)
        BeaconRepository.setActiveChannel(channelId)
        
        // Clear unread for this channel
        BeaconRepository.clearUnread(beaconId, channelId)
    }
    
    /**
     * Send a message via the appropriate path (mesh or chat).
     * Message is created locally and routed by BoundaryEngine.
     * 
     * @param content The message text
     * @param recipientId Optional recipient for DM (null = broadcast to channel)
     * @param recipientName Optional recipient display name
     */
    fun sendMessage(content: String, recipientId: String? = null, recipientName: String? = null) {
        // CRITICAL LOG - must appear
        android.util.Log.e("SEND_DEBUG", "████████ VM sendMessage CALLED ████████")
        android.util.Log.e("SEND_DEBUG", "   Content: '$content'")
        android.util.Log.e("SEND_DEBUG", "   RecipientId: ${recipientId ?: "BROADCAST"}")
        
        if (content.isBlank()) {
            android.util.Log.w("ConversationVM", "   ⚠️ Content is blank - ignoring")
            return
        }
        
        // For DMs, use a private channel ID based on both user IDs (truncated to 4 chars for BLE)
        val actualChannelId = if (recipientId != null) {
            // Create deterministic DM channel ID using SHORT IDs (4 chars) for BLE compatibility
            val myIdShort = localUserId.take(4)
            val recipientIdShort = recipientId.take(4)
            val dmChannelId = "dm_${listOf(myIdShort, recipientIdShort).sorted().joinToString("_")}"
            android.util.Log.i("ConversationVM", "   📨 DM to $recipientName using channel: $dmChannelId")
            
            // Ensure DM channel exists in repository
            BeaconRepository.getOrCreateDM(_beaconId.value, localUserId, recipientId, recipientName ?: recipientId)
            
            // Join the DM channel so we can receive replies
            BoundaryEngine.joinChannel(dmChannelId)
            android.util.Log.i("ConversationVM", "   ✅ Joined DM channel: $dmChannelId")
            
            dmChannelId
        } else {
            _channelId.value
        }
        
        android.util.Log.e("SEND_DEBUG", "   ChannelId: $actualChannelId")
        
        val message = Message(
            beaconId = _beaconId.value,
            channelId = actualChannelId,
            senderId = localUserId,
            senderName = localUserName,
            content = content,
            isMeshOrigin = BoundaryEngine.shouldUseMesh(getApplication()),
            recipientId = recipientId,
            recipientName = recipientName
        )
        
        android.util.Log.i("ConversationVM", "   Routing message via BoundaryEngine...")
        BoundaryEngine.routeMessage(getApplication(), message)
        android.util.Log.i("ConversationVM", "   ✅ Message routed")
    }
    
    /**
     * Set the local user's display name.
     */
    fun setUserName(name: String) {
        if (name.isNotBlank()) {
            localUserName = name
            UserPreferences.setUserName(name)
        }
    }
    
    /**
     * Get the current local user name.
     */
    fun getUserName(): String = localUserName
    
    /**
     * Get the local user ID for message ownership detection.
     */
    fun getLocalUserId(): String = localUserId
    
    /**
     * Get message count for debugging.
     */
    fun getMessageCount(): Int = MessageRepository.getMessageCount()
    
    /**
     * Get pending sync count for debugging.
     */
    fun getPendingSyncCount(): Int = MessageRepository.getPendingSyncCount()
    
    /**
     * Handle message action (delete or archive).
     */
    fun handleMessageAction(message: Message, action: MessageAction) {
        when (action) {
            MessageAction.DELETE_FOR_ME -> {
                // Remove locally only
                MessageRepository.removeMessage(message.id)
                android.util.Log.i("ConversationVM", "Deleted message locally: ${message.id}")
            }
            MessageAction.DELETE_FOR_EVERYONE -> {
                // Remove locally and from backend
                MessageRepository.removeMessage(message.id)
                android.util.Log.i("ConversationVM", "Requesting backend deletion for: ${message.id}")
                BoundaryEngine.deleteMessageFromBackend(message)
            }
            MessageAction.ARCHIVE -> {
                // Archive message (mark as archived, keep in storage)
                MessageRepository.archiveMessage(message.id)
                android.util.Log.i("ConversationVM", "Archived message: ${message.id}")
            }
        }
    }
    
    // ══════════════════════════════════════════════════════════════════
    // MEDIA MESSAGING
    // ══════════════════════════════════════════════════════════════════
    
    /** Pending camera capture file */
    private var pendingCameraFile: File? = null
    
    /** Voice recording state */
    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()
    
    /**
     * Create a URI for camera capture.
     * Call this before launching camera intent.
     */
    fun createCameraUri(): Uri? {
        val result = MediaHelper.createCameraImageUri(getApplication())
        pendingCameraFile = result?.second
        return result?.first
    }
    
    /**
     * Handle camera capture result.
     * Call this after camera returns successfully.
     */
    fun onCameraCaptured(recipientId: String? = null, recipientName: String? = null) {
        val file = pendingCameraFile ?: return
        pendingCameraFile = null
        
        if (!file.exists() || file.length() == 0L) {
            android.util.Log.w("ConversationVM", "Camera capture file is empty or missing")
            return
        }
        
        android.util.Log.i("ConversationVM", "📷 Camera captured: ${file.absolutePath}")
        
        val (width, height) = MediaHelper.getImageDimensions(file)
        
        sendMediaMessage(
            mediaType = MediaType.IMAGE,
            localPath = file.absolutePath,
            mimeType = "image/jpeg",
            fileName = file.name,
            fileSize = file.length(),
            width = width,
            height = height,
            recipientId = recipientId,
            recipientName = recipientName
        )
    }
    
    /** Pending DM peer for voice recording */
    private var pendingVoiceRecipientId: String? = null
    private var pendingVoiceRecipientName: String? = null
    
    /**
     * Start voice recording.
     */
    fun startVoiceRecording(recipientId: String? = null, recipientName: String? = null): Boolean {
        if (_isRecording.value) return false
        
        pendingVoiceRecipientId = recipientId
        pendingVoiceRecipientName = recipientName
        
        val path = MediaHelper.startVoiceRecording(getApplication())
        if (path != null) {
            _isRecording.value = true
            android.util.Log.i("ConversationVM", "🎤 Voice recording started")
            return true
        }
        return false
    }
    
    /**
     * Toggle voice recording - start if stopped, stop and send if recording.
     */
    fun toggleVoiceRecording(recipientId: String? = null, recipientName: String? = null) {
        if (_isRecording.value) {
            stopVoiceRecording()
        } else {
            startVoiceRecording(recipientId, recipientName)
        }
    }
    
    /**
     * Stop voice recording and send.
     */
    fun stopVoiceRecording() {
        if (!_isRecording.value) return
        _isRecording.value = false
        
        val result = MediaHelper.stopVoiceRecording()
        if (result != null) {
            android.util.Log.i("ConversationVM", "🎤 Voice recorded: ${result.durationMs}ms")
            
            // Only send if recording is at least 500ms
            if (result.durationMs >= 500) {
                sendMediaMessage(
                    mediaType = MediaType.VOICE,
                    localPath = result.file.absolutePath,
                    mimeType = "audio/mp4",
                    fileName = result.file.name,
                    fileSize = result.file.length(),
                    duration = result.durationMs,
                    recipientId = pendingVoiceRecipientId,
                    recipientName = pendingVoiceRecipientName
                )
            } else {
                android.util.Log.w("ConversationVM", "🎤 Recording too short (${result.durationMs}ms), discarding")
                result.file.delete()
            }
        }
        
        pendingVoiceRecipientId = null
        pendingVoiceRecipientName = null
    }
    
    /**
     * Cancel voice recording.
     */
    fun cancelVoiceRecording() {
        if (_isRecording.value) {
            _isRecording.value = false
            MediaHelper.cancelVoiceRecording()
            pendingVoiceRecipientId = null
            pendingVoiceRecipientName = null
            android.util.Log.i("ConversationVM", "🎤 Voice recording cancelled")
        }
    }
    
    /**
     * Handle file selection from picker.
     */
    fun onFileSelected(uri: Uri, recipientId: String? = null, recipientName: String? = null) {
        val fileInfo = MediaHelper.copyFileFromUri(getApplication(), uri)
        if (fileInfo != null) {
            android.util.Log.i("ConversationVM", "📁 File selected: ${fileInfo.fileName}")
            
            sendMediaMessage(
                mediaType = MediaType.FILE,
                localPath = fileInfo.file.absolutePath,
                mimeType = fileInfo.mimeType,
                fileName = fileInfo.fileName,
                fileSize = fileInfo.fileSize,
                recipientId = recipientId,
                recipientName = recipientName
            )
        }
    }
    
    /**
     * Send a media message (image, voice, or file).
     */
    private fun sendMediaMessage(
        mediaType: MediaType,
        localPath: String,
        mimeType: String?,
        fileName: String?,
        fileSize: Long,
        duration: Long = 0,
        width: Int = 0,
        height: Int = 0,
        recipientId: String? = null,
        recipientName: String? = null
    ) {
        android.util.Log.i("ConversationVM", "📤 Sending media message: $mediaType")
        
        val actualChannelId = if (recipientId != null) {
            val myIdShort = localUserId.take(4)
            val recipientIdShort = recipientId.take(4)
            "dm_${listOf(myIdShort, recipientIdShort).sorted().joinToString("_")}"
        } else {
            _channelId.value
        }
        
        val media = MediaAttachment(
            type = mediaType,
            localPath = localPath,
            mimeType = mimeType,
            fileName = fileName,
            fileSize = fileSize,
            duration = duration,
            width = width,
            height = height,
            isQueued = !BoundaryEngine.isOnline.value  // Queue if offline
        )
        
        val message = Message(
            beaconId = _beaconId.value,
            channelId = actualChannelId,
            senderId = localUserId,
            senderName = localUserName,
            content = when (mediaType) {
                MediaType.IMAGE -> "Sent a photo"
                MediaType.VOICE -> "Sent a voice message"
                MediaType.FILE -> "Sent a file: $fileName"
                else -> ""
            },
            mediaType = mediaType,
            media = media,
            recipientId = recipientId,
            recipientName = recipientName
        )
        
        // Route via BoundaryEngine (handles online/offline logic)
        BoundaryEngine.routeMessage(getApplication(), message)
        
        // If online, also upload the media file
        if (BoundaryEngine.isOnline.value) {
            viewModelScope.launch {
                MediaUploadManager.uploadAndSendMedia(message) { success ->
                    if (success) {
                        android.util.Log.i("ConversationVM", "✅ Media uploaded and sent")
                    } else {
                        android.util.Log.w("ConversationVM", "⚠️ Media upload failed, queued for later")
                    }
                }
            }
        }
    }
}
