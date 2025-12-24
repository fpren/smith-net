package com.guildofsmiths.trademesh.ui.timetracking

/**
 * C-12: Time Tracking Types
 * Data models for time logging UI
 */

// ════════════════════════════════════════════════════════════════════
// TIME ENTRY
// ════════════════════════════════════════════════════════════════════

data class TimeEntry(
    val id: String,
    val userId: String,
    val userName: String,
    val clockInTime: Long,
    val clockOutTime: Long? = null,
    val durationMinutes: Int? = null,
    val jobId: String? = null,
    val jobTitle: String? = null,
    val projectId: String? = null,
    val location: String? = null,
    val entryType: EntryType = EntryType.REGULAR,
    val source: EntrySource = EntrySource.MANUAL,
    val createdAt: Long,
    val immutableHash: String,
    val notes: List<TimeNote> = emptyList(),
    val status: EntryStatus = EntryStatus.COMPLETED
)

enum class EntryType(val displayName: String, val icon: String) {
    REGULAR("Regular", "▓"),
    OVERTIME("Overtime", "█"),
    BREAK("Break", "░"),
    TRAVEL("Travel", "→"),
    ON_CALL("On Call", "◎")
}

enum class EntrySource(val displayName: String) {
    MANUAL("Manual"),
    GEOFENCE("GPS"),
    BEACON("Beacon"),
    MESH("Mesh")
}

enum class EntryStatus(val displayName: String) {
    ACTIVE("Active"),
    COMPLETED("Completed"),
    PENDING_REVIEW("Pending"),
    APPROVED("Approved"),
    DISPUTED("Disputed")
}

// ════════════════════════════════════════════════════════════════════
// TIME NOTE
// ════════════════════════════════════════════════════════════════════

data class TimeNote(
    val id: String,
    val text: String,
    val addedBy: String,
    val addedAt: Long,
    val type: String = "note"
)

// ════════════════════════════════════════════════════════════════════
// SUMMARIES
// ════════════════════════════════════════════════════════════════════

data class DailySummary(
    val date: String,
    val userId: String,
    val entries: List<TimeEntry>,
    val totalMinutes: Int,
    val regularMinutes: Int,
    val overtimeMinutes: Int,
    val breakMinutes: Int
)

data class WeeklySummary(
    val weekStart: String,
    val userId: String,
    val dailySummaries: List<DailySummary>,
    val totalMinutes: Int,
    val regularMinutes: Int,
    val overtimeMinutes: Int
)

// ════════════════════════════════════════════════════════════════════
// STATUS
// ════════════════════════════════════════════════════════════════════

data class ClockStatus(
    val isClockedIn: Boolean,
    val activeEntry: TimeEntry?,
    val currentTime: Long
)
