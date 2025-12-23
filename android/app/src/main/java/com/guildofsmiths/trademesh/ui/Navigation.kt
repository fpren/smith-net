package com.guildofsmiths.trademesh.ui

/**
 * Navigation routes for the app.
 */
object NavRoutes {
    const val WELCOME = "welcome"
    const val BEACON_LIST = "beacons"
    const val CHANNEL_LIST = "channels/{beaconId}"
    const val CONVERSATION = "conversation/{beaconId}/{channelId}"
    const val CONVERSATION_DM = "conversation/{beaconId}/{channelId}?dmPeerId={dmPeerId}&dmPeerName={dmPeerName}"
    const val SETTINGS = "settings"
    const val PEERS = "peers"
    const val CREATE_CHANNEL = "create_channel/{beaconId}"
    const val CREATE_BEACON = "create_beacon"
    
    fun channelList(beaconId: String) = "channels/$beaconId"
    fun conversation(beaconId: String, channelId: String) = "conversation/$beaconId/$channelId"
    fun conversationDM(beaconId: String, channelId: String, peerId: String, peerName: String) = 
        "conversation/$beaconId/$channelId?dmPeerId=$peerId&dmPeerName=$peerName"
    fun createChannel(beaconId: String) = "create_channel/$beaconId"
}
