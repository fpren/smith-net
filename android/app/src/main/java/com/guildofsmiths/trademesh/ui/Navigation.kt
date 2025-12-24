package com.guildofsmiths.trademesh.ui

/**
 * Navigation routes for the app.
 */
object NavRoutes {
    const val AUTH = "auth"  // C-01: Authentication screen
    const val WELCOME = "welcome"
    const val BEACON_LIST = "beacons"
    const val CHANNEL_LIST = "channels/{beaconId}"
    const val CONVERSATION = "conversation/{beaconId}/{channelId}"
    const val CONVERSATION_DM = "conversation/{beaconId}/{channelId}?dmPeerId={dmPeerId}&dmPeerName={dmPeerName}"
    const val SETTINGS = "settings"
    const val PEERS = "peers"
    const val CREATE_CHANNEL = "create_channel/{beaconId}"
    const val CREATE_BEACON = "create_beacon"
    
    // Separate components (C-11, C-12)
    const val JOB_BOARD = "job_board"     // C-11: Job Board / Task View
    const val TIME_TRACKING = "time_clock" // C-12: Time Tracking Core
    
    fun channelList(beaconId: String) = "channels/$beaconId"
    fun conversation(beaconId: String, channelId: String) = "conversation/$beaconId/$channelId"
    fun conversationDM(beaconId: String, channelId: String, peerId: String, peerName: String) = 
        "conversation/$beaconId/$channelId?dmPeerId=$peerId&dmPeerName=$peerName"
    fun createChannel(beaconId: String) = "create_channel/$beaconId"
}
