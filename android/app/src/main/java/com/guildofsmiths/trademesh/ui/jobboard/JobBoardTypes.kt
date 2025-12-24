package com.guildofsmiths.trademesh.ui.jobboard

/**
 * C-11: Job Board Types
 * Data models for task management UI
 */

// ════════════════════════════════════════════════════════════════════
// JOB
// ════════════════════════════════════════════════════════════════════

data class Job(
    val id: String,
    val title: String,
    val description: String = "",
    val projectId: String? = null,
    val clientName: String? = null,
    val location: String? = null,
    val status: JobStatus = JobStatus.BACKLOG,
    val priority: Priority = Priority.MEDIUM,
    val createdBy: String,
    val assignedTo: List<String> = emptyList(),
    val createdAt: Long,
    val updatedAt: Long,
    val dueDate: Long? = null,
    val completedAt: Long? = null,
    val tags: List<String> = emptyList()
)

enum class JobStatus(val displayName: String, val icon: String) {
    BACKLOG("Backlog", "░"),
    TODO("To Do", "▒"),
    IN_PROGRESS("In Progress", "▓"),
    REVIEW("Review", "█"),
    DONE("Done", "✓"),
    ARCHIVED("Archived", "▫")
}

enum class Priority(val displayName: String, val icon: String) {
    LOW("Low", "▽"),
    MEDIUM("Medium", "◇"),
    HIGH("High", "△"),
    URGENT("Urgent", "▲")
}

// ════════════════════════════════════════════════════════════════════
// TASK (Sub-item)
// ════════════════════════════════════════════════════════════════════

data class Task(
    val id: String,
    val jobId: String,
    val title: String,
    val description: String? = null,
    val status: TaskStatus = TaskStatus.PENDING,
    val assignedTo: String? = null,
    val createdBy: String,
    val createdAt: Long,
    val updatedAt: Long,
    val completedAt: Long? = null,
    val order: Int = 0,
    val checklist: List<ChecklistItem> = emptyList()
)

enum class TaskStatus(val displayName: String) {
    PENDING("Pending"),
    IN_PROGRESS("In Progress"),
    DONE("Done"),
    BLOCKED("Blocked")
}

data class ChecklistItem(
    val id: String,
    val text: String,
    val checked: Boolean = false,
    val checkedAt: Long? = null,
    val checkedBy: String? = null
)

// ════════════════════════════════════════════════════════════════════
// BOARD COLUMN
// ════════════════════════════════════════════════════════════════════

data class BoardColumn(
    val status: JobStatus,
    val jobs: List<Job>
)
