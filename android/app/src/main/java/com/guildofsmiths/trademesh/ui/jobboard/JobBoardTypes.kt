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
    val tags: List<String> = emptyList(),
    // Additional fields
    val toolsNeeded: String = "",
    val expenses: String = "",
    val crewSize: Int = 1,
    val crew: List<CrewMember> = emptyList(),
    // Workflow fields
    val materials: List<Material> = emptyList(),
    val workLog: List<WorkLogEntry> = emptyList(),
    // Scheduling fields
    val estimatedStartDate: Long? = null,
    val estimatedEndDate: Long? = null,
    val actualStartDate: Long? = null,
    val actualEndDate: Long? = null,
    // Archive fields
    val isArchived: Boolean = false,
    val archivedAt: Long? = null,
    val archiveReason: String? = null,
    // Related messages for archive/history
    val relatedMessageIds: List<String> = emptyList(),
    val relatedChannelId: String? = null,
    // Future: AI-generated summary of chat history
    val chatSummary: String? = null,
    
    // ════════════════════════════════════════════════════════════════════
    // CLIENT FEEDBACK - Bar-based ratings (1-10 scale)
    // ════════════════════════════════════════════════════════════════════
    val clientSatisfactionBars: Int? = null,     // 1-10 bars (██████████)
    val clientFeedbackText: String? = null,      // Optional client comments
    val feedbackRecordedAt: Long? = null,        // When feedback was collected
    
    // ════════════════════════════════════════════════════════════════════
    // INTERNAL PERFORMANCE METRICS - Bar-based scores (1-10 scale)
    // These are for internal use only, NOT shown to clients
    // ════════════════════════════════════════════════════════════════════
    val profitabilityScore: Int? = null,         // 1-10 bars, +/- margin %
    val operationalScore: Int? = null,           // 1-10 bars, workflow efficiency
    val timeManagementScore: Int? = null,        // 1-10 bars, actual vs estimated
    val qualityScore: Int? = null,               // 1-10 bars, issues/defects
    
    // ════════════════════════════════════════════════════════════════════
    // BENCHMARK DATA - Industry/market comparisons
    // ════════════════════════════════════════════════════════════════════
    val marketLaborRate: Double? = null,         // Local avg labor rate ($/hr)
    val marketCompletionTime: Double? = null,    // Industry avg hours for job type
    val marketProfitMargin: Double? = null,      // Industry avg profit margin %
    
    // ════════════════════════════════════════════════════════════════════
    // ACTUAL PERFORMANCE DATA - Calculated from job completion
    // ════════════════════════════════════════════════════════════════════
    val actualLaborRate: Double? = null,         // Your actual $/hr charged
    val actualCompletionTime: Double? = null,    // Actual hours taken
    val actualProfitMargin: Double? = null,      // Actual profit margin %
    
    // ════════════════════════════════════════════════════════════════════
    // DOCUMENT REGENERATION - Store execution items for archive
    // ════════════════════════════════════════════════════════════════════
    val executionItemsJson: String? = null       // Serialized execution items for doc regeneration
)

data class CrewMember(
    val name: String,
    val occupation: String,
    val task: String = ""
)

// Material checklist item with cost tracking for invoice
data class Material(
    val name: String,
    val notes: String = "",
    val checked: Boolean = false,
    val checkedAt: Long? = null,
    // Cost tracking for invoice generation
    val quantity: Double = 1.0,
    val unit: String = "ea",  // ea, ft, lot, hr, etc.
    val unitCost: Double = 0.0,
    val totalCost: Double = 0.0,
    val vendor: String = "",
    val receiptPhoto: String? = null,
    // Assignment - who is responsible for this material
    val assignedTo: String? = null,
    // Link to related task (for auto-assignment)
    val relatedTaskId: String? = null
)

// Work log entry
data class WorkLogEntry(
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val author: String = ""
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

// ════════════════════════════════════════════════════════════════════
// TASK ASSIGNMENT
// ════════════════════════════════════════════════════════════════════

/**
 * Filter mode for viewing tasks
 */
enum class TaskFilterMode(val displayName: String) {
    ALL("All Tasks"),
    MY_TASKS("My Tasks"),
    UNASSIGNED("Unassigned")
}

/**
 * Represents a member who can be assigned tasks
 */
data class AssignableMember(
    val id: String,
    val name: String,
    val role: String = ""
)

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
