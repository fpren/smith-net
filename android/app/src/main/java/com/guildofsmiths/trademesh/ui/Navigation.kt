package com.guildofsmiths.trademesh.ui

/**
 * Navigation routes for the app.
 * 
 * CANONICAL FLOW:
 * APP LAUNCH → LOGIN → AUTH SUCCESS → PLAN (first) → DASHBOARD (hub)
 * 
 * Dashboard is the central hub that routes to:
 * - PLAN (creation canvas)
 * - JOB BOARD (execution view)
 * - MESSAGES (unified messaging)
 * - TIME (time tracking)
 */
object NavRoutes {
    // ════════════════════════════════════════════════════════════════
    // AUTH FLOW
    // ════════════════════════════════════════════════════════════════
    const val AUTH = "auth"              // C-01: Authentication screen
    const val ONBOARDING = "onboarding"  // Post-registration guided setup
    const val WELCOME = "welcome"        // Legacy welcome screen
    
    // ════════════════════════════════════════════════════════════════
    // MAIN FLOW (Post-Auth)
    // ════════════════════════════════════════════════════════════════
    const val PLAN = "plan"              // P-01: PLAN Canvas (first after auth)
    const val PLAN_WITH_JOB = "plan?jobId={jobId}"  // Plan with job context from Job Board
    const val DASHBOARD = "dashboard"    // Central operational hub
    
    // ════════════════════════════════════════════════════════════════
    // DASHBOARD ROUTES (Accessible from Dashboard)
    // ════════════════════════════════════════════════════════════════
    const val JOB_BOARD = "job_board"     // C-11: Job Board / Task View
    const val TIME_TRACKING = "time_clock" // C-12: Time Tracking Core
    const val ARCHIVE = "archive"          // C-13: Archive / History View
    const val SETTINGS = "settings"        // Global settings
    const val PROFILE = "profile"          // User profile
    
    // ════════════════════════════════════════════════════════════════
    // MESSAGING ROUTES
    // ════════════════════════════════════════════════════════════════
    const val BEACON_LIST = "beacons"      // Messages hub (2 peers: Online + Mesh)
    const val CHANNEL_LIST = "channels/{beaconId}"
    const val CONVERSATION = "conversation/{beaconId}/{channelId}"
    const val CONVERSATION_DM = "conversation/{beaconId}/{channelId}?dmPeerId={dmPeerId}&dmPeerName={dmPeerName}"
    const val DASHBOARD_CHANNELS = "dashboard_channels"  // Discover & join channels
    const val CREATE_CHANNEL = "create_channel/{beaconId}"
    const val CREATE_BEACON = "create_beacon"
    const val PEERS = "peers"
    
    // ════════════════════════════════════════════════════════════════
    // HELPER FUNCTIONS
    // ════════════════════════════════════════════════════════════════
    fun channelList(beaconId: String) = "channels/$beaconId"
    fun conversation(beaconId: String, channelId: String) = "conversation/$beaconId/$channelId"
    fun conversationDM(beaconId: String, channelId: String, peerId: String, peerName: String) = 
        "conversation/$beaconId/$channelId?dmPeerId=$peerId&dmPeerName=$peerName"
    fun createChannel(beaconId: String) = "create_channel/$beaconId"
    fun planWithJob(jobId: String) = "plan?jobId=$jobId"
}
