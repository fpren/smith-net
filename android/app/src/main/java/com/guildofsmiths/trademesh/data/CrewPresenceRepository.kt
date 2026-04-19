package com.guildofsmiths.trademesh.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ClockStatus(val label: String) {
    ON_CLOCK("On Clock"),
    OFF_CLOCK("Off Clock"),
    ON_BREAK("On Break")
}

data class CrewPresenceInfo(
    val id: String,
    val userId: String = "",               // maps to DM system user ID
    val name: String,
    val trade: String,
    val status: ClockStatus,
    val clockInTime: Long? = null,
    val currentJobId: String? = null,
    val currentJobTitle: String? = null,
    val currentSite: String? = null,
    val phone: String? = null,
    val lastSeen: Long = System.currentTimeMillis(),
    val taskDescription: String? = null,
    val taskProgress: Int? = null  // 0-100
)

/**
 * Crew presence data. Currently returns mock data tied to demo jobs.
 * Will later merge real data from mesh peers + cloud.
 */
object CrewPresenceRepository {

    private val _crew = MutableStateFlow<List<CrewPresenceInfo>>(emptyList())
    val crew: StateFlow<List<CrewPresenceInfo>> = _crew.asStateFlow()

    init {
        seedMockCrew()
    }

    fun getActiveCount(): Int = _crew.value.count { it.status == ClockStatus.ON_CLOCK }
    fun getTotalCount(): Int = _crew.value.size

    fun getCrewAtSite(site: String): List<CrewPresenceInfo> =
        _crew.value.filter { it.currentSite?.equals(site, ignoreCase = true) == true }

    fun getCrewBySite(): Map<String, List<CrewPresenceInfo>> =
        _crew.value
            .filter { it.currentSite != null }
            .groupBy { it.currentSite!! }

    /** SmithAI supervisor entry — always present when AI is enabled */
    fun getCrewWithAI(): List<CrewPresenceInfo> {
        val aiMode = UserPreferences.getAISupervisorMode()
        val aiEntry = if (aiMode != "off") listOf(
            CrewPresenceInfo(
                id = "smith-ai",
                name = "SmithAI",
                trade = "Supervisor",
                status = ClockStatus.ON_CLOCK,
                clockInTime = null,
                lastSeen = System.currentTimeMillis()
            )
        ) else emptyList()
        return aiEntry + _crew.value
    }

    fun getCrew(): List<CrewPresenceInfo> = _crew.value

    fun getCrewById(id: String): CrewPresenceInfo? = _crew.value.find { it.id == id }

    fun getCrewByUserId(userId: String): CrewPresenceInfo? = _crew.value.find { it.userId == userId }

    fun getCrewByName(name: String): CrewPresenceInfo? = _crew.value.find { it.name == name }

    fun getOnClockCrew(): List<CrewPresenceInfo> =
        _crew.value.filter { it.status == ClockStatus.ON_CLOCK }

    fun getOffClockCrew(): List<CrewPresenceInfo> =
        _crew.value.filter { it.status != ClockStatus.ON_CLOCK }

    private fun seedMockCrew() {
        val now = System.currentTimeMillis()
        val hour = 3_600_000L

        _crew.value = listOf(
            CrewPresenceInfo(
                id = "crew-1",
                userId = "user-mike-r",
                name = "Mike R.",
                trade = "Electrician",
                status = ClockStatus.ON_CLOCK,
                clockInTime = now - (2 * hour + 15 * 60_000),
                currentJobId = "demo-1",
                currentJobTitle = "200A Panel Upgrade",
                currentSite = "847 Flatbush Ave, Brooklyn NY",
                phone = "718-555-0301",
                taskDescription = "Panel upgrade",
                taskProgress = 60
            ),
            CrewPresenceInfo(
                id = "crew-2",
                userId = "user-sarah-l",
                name = "Sarah L.",
                trade = "Plumber",
                status = ClockStatus.ON_CLOCK,
                clockInTime = now - (1 * hour + 30 * 60_000),
                currentJobId = "demo-1",
                currentJobTitle = "200A Panel Upgrade",
                currentSite = "847 Flatbush Ave, Brooklyn NY",
                phone = "718-555-0402",
                taskDescription = "Conduit run",
                taskProgress = 40
            ),
            CrewPresenceInfo(
                id = "crew-3",
                userId = "user-james-k",
                name = "James K.",
                trade = "HVAC",
                status = ClockStatus.OFF_CLOCK,
                clockInTime = null,
                currentJobId = null,
                currentJobTitle = null,
                currentSite = null,
                phone = "347-555-0193",
                lastSeen = now - 2 * hour
            ),
            CrewPresenceInfo(
                id = "crew-4",
                userId = "user-carlos-m",
                name = "Carlos M.",
                trade = "Carpenter",
                status = ClockStatus.OFF_CLOCK,
                clockInTime = null,
                currentJobId = null,
                currentJobTitle = null,
                currentSite = null,
                phone = "917-555-0574",
                lastSeen = now - 24 * hour
            ),
            CrewPresenceInfo(
                id = "crew-5",
                userId = "user-dana-w",
                name = "Dana W.",
                trade = "Electrician",
                status = ClockStatus.ON_BREAK,
                clockInTime = now - (3 * hour + 2 * 60_000),
                currentJobId = "demo-3",
                currentJobTitle = "Bathroom GFI Install",
                currentSite = "55 W 125th St, Apt 4B, Manhattan NY",
                phone = "646-555-0887",
                taskDescription = "GFCI wiring",
                taskProgress = 80
            )
        )
    }
}
