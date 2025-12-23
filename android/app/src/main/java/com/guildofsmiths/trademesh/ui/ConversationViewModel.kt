package com.guildofsmiths.trademesh.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.guildofsmiths.trademesh.data.Beacon
import com.guildofsmiths.trademesh.data.BeaconRepository
import com.guildofsmiths.trademesh.data.Channel
import com.guildofsmiths.trademesh.data.ChannelType
import com.guildofsmiths.trademesh.data.Message
import com.guildofsmiths.trademesh.data.MessageRepository
import com.guildofsmiths.trademesh.data.UserPreferences
import com.guildofsmiths.trademesh.engine.BoundaryEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

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
}
