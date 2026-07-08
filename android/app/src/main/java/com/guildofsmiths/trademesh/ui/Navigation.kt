package com.guildofsmiths.trademesh.ui

/**
 * Navigation routes for the app.
 */
object NavRoutes {
    // AUTH FLOW
    const val AUTH = "auth"
    const val ONBOARDING = "onboarding"
    const val WELCOME = "welcome"

    // MAIN
    const val DASHBOARD = "dashboard"
    const val PLAN = "plan"  // Proposals screen
    @Deprecated("Removed in Task 9") const val PLAN_MODE = "plan_mode"
    @Deprecated("Removed in Task 9") const val INTENT = "intent"
    @Deprecated("Removed in Task 9") const val SOLO_DASHBOARD = "solo_dashboard"

    // JOB PIPELINE
    const val JOB_PIPELINE = "job_pipeline/{jobId}"
    const val NEW_JOB = "new_job"

    // EXISTING
    const val JOB_BOARD = "job_board"
    const val TIME_TRACKING = "time_clock"
    const val ARCHIVE = "archive"
    const val SETTINGS = "settings"
    const val PROFILE = "profile"

    // MESSAGING
    const val CHAT_LIST = "chat_list"
    const val BEACON_LIST = "beacons"
    const val CHANNEL_LIST = "channels/{beaconId}"
    const val CONVERSATION = "conversation/{beaconId}/{channelId}"
    const val CONVERSATION_DM = "conversation/{beaconId}/{channelId}?dmPeerId={dmPeerId}&dmPeerName={dmPeerName}"
    const val CREATE_CHANNEL = "create_channel/{beaconId}"
    const val CREATE_BEACON = "create_beacon"
    const val PEERS = "peers"
    const val NEW_CONVERSATION = "new_conversation"
    const val INCOMING = "incoming"
    const val SCAN_ID = "scan_id"

    // REPORT & SUPPLY
    const val REPORT = "report"
    const val SUPPLY = "supply"

    // EXPENSES (BOL-style)
    const val EXPENSES = "expenses"
    const val JOB_EXPENSES = "job_expenses/{jobId}"
    const val EXPENSE_CATEGORIES = "expense_categories"
    const val EXPENSE_CSV_IMPORT = "expense_csv_import"
    const val BOL_LEGAL_SETTINGS = "bol_legal_settings"

    // LOST & FOUND (GPS breadcrumb for a crew member)
    const val LOST_AND_FOUND = "lost_and_found/{userId}"
    fun lostAndFound(userId: String) = "lost_and_found/${android.net.Uri.encode(userId)}"

    // DISPATCH
    const val DISPATCH = "dispatch"

    // MAP
    const val MAP = "map"

    // CLIENTS
    const val CLIENTS = "clients"
    const val CLIENT_DETAIL = "client_detail/{clientName}"

    // HELPERS
    fun jobPipeline(jobId: String) = "job_pipeline/$jobId"
    fun jobExpenses(jobId: String) = "job_expenses/$jobId"
    fun channelList(beaconId: String) = "channels/$beaconId"
    fun conversation(beaconId: String, channelId: String) = "conversation/$beaconId/$channelId"
    fun conversationDM(beaconId: String, channelId: String, peerId: String, peerName: String) =
        "conversation/$beaconId/$channelId?dmPeerId=$peerId&dmPeerName=$peerName"
    fun createChannel(beaconId: String) = "create_channel/$beaconId"
    fun clientDetail(clientName: String) = "client_detail/${android.net.Uri.encode(clientName)}"
}
