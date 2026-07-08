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
    val expensesNote: String = "",
    val crewSize: Int = 1,
    val crew: List<CrewMember> = emptyList(),
    // Workflow fields
    val materials: List<Material> = emptyList(),
    val expenses: List<JobExpense> = emptyList(),
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
    // Solo contractor lifecycle fields
    val clientPhone: String = "",
    val clientAddress: String = "",
    val proposalId: String? = null,
    val invoiceId: String? = null,
    val hourlyRate: Double = 0.0,
    val photos: List<String> = emptyList(),
    val stage: JobStage = JobStage.LEAD,
    val equipmentList: List<String> = emptyList(),
    // Daily logs — AI-generated end-of-day summaries per date
    val dailyLogs: List<DailyJobLog> = emptyList(),
    // Financials — deposit collected before invoice
    val depositCollected: Double = 0.0,
    val depositNote: String? = null,
    // Proposal — estimated labor hours used for proposal generation
    val estimatedHours: Double = 0.0,
    // Per-job override for which legal preset groups appear on the BOL footer.
    // BOTH is the default — the preview sheet smart-resolves from client + vendor origins.
    val legalFooterScope: LegalFooterScope = LegalFooterScope.BOTH,
    // Geofence center for clock-in validation. Lazy-geocoded from clientAddress
    // on first open; may be manually overridden in the job form.
    val latitude: Double? = null,
    val longitude: Double? = null,
    val geofenceRadiusMeters: Int = 75
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
    val receiptPhoto: String? = null
)

// ════════════════════════════════════════════════════════════════════
// EXPENSES — BOL-style itemized per-job expenses
// ════════════════════════════════════════════════════════════════════

/**
 * A single expense line on a job. Category is a stable slug resolved via
 * ExpenseCategoryRepository so users can add/rename/hide their own.
 */
data class JobExpense(
    val id: String = java.util.UUID.randomUUID().toString(),
    val category: String,                 // stable slug: "material", "permit_fee", "fuel", user-custom…
    val description: String,
    val quantity: Double = 1.0,
    val unit: String = "ea",              // ea, ft, lot, hr, mi, gal, day, lb, box
    val unitCost: Double = 0.0,
    val vendor: String = "",              // or sub name / permit authority / fuel station
    val referenceNumber: String? = null,  // permit#, receipt#, sub invoice#, BOL#
    val receiptPhoto: String? = null,
    val incurredAt: Long = System.currentTimeMillis(),
    val hazardous: Boolean = false,       // BOL HM flag
    val freightTerm: FreightTerm = FreightTerm.NA,
    val notes: String? = null,
    val aiEstimated: Boolean = false      // true when unitCost was auto-filled by SmithAI
) {
    val totalCost: Double get() = quantity * unitCost
}

enum class FreightTerm(val displayName: String) {
    PREPAID("Prepaid"),
    COLLECT("Collect"),
    THIRD_PARTY("3rd Party"),
    NA("N/A")
}

/**
 * Scope of legal clauses rendered on a BOL's footer. Set per-job at authoring
 * time; the share-preview can override for a single send.
 * - DOMESTIC: only US_DOMESTIC + US_STATES groups (US-only engagements)
 * - INTERNATIONAL: only INTL_COMMERCIAL + INTERNATIONAL_CARRIAGE (cross-border)
 * - BOTH: every enabled group — default, smart-resolves from client + vendor
 *   nationality when BOTH is set
 */
enum class LegalFooterScope { DOMESTIC, INTERNATIONAL, BOTH }

/**
 * User-customizable category definition. Persisted by ExpenseCategoryRepository.
 * id is the stable slug stored on JobExpense.category; displayName/shortCode/colorHex
 * are shown in the UI.
 */
data class ExpenseCategoryDef(
    val id: String,
    val displayName: String,
    val shortCode: String,
    val colorHex: String = "#8C6B2A",
    val hidden: Boolean = false,
    val builtIn: Boolean = false,
    val sortOrder: Int = 0
)

// Work log entry
data class WorkLogEntry(
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val author: String = ""
)

// Daily job log — AI-generated end-of-day summary per job per date
data class DailyJobLog(
    val id: String = java.util.UUID.randomUUID().toString(),
    val jobId: String,
    val date: Long,                              // midnight of the day
    val hoursWorked: Double = 0.0,
    val crewPresent: List<String> = emptyList(), // crew member names
    val materialsCheckedCount: Int = 0,
    val materialsCostToday: Double = 0.0,
    val workerNotes: List<WorkLogEntry> = emptyList(),
    val timeEntryIds: List<String> = emptyList(),
    val summaryStandard: String = "",            // "3.5h, 2 materials, $140"
    val summaryDetailed: String = "",            // "Apr 18 — 3.5h, conduit run, 2 materials checked"
    val summaryNarrative: String? = null,        // AI narrative, null if unavailable
    val autoGenerated: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val status: DailyLogStatus = DailyLogStatus.PENDING_AI
)

enum class DailyLogStatus {
    PENDING_AI,     // waiting for AI narrative
    COMPLETE,       // all summaries filled
    FAILED_AI       // AI call failed, rule-based only
}

enum class JobStatus(val displayName: String, val icon: String) {
    BACKLOG("Backlog", "░"),
    SCHEDULED("Scheduled", "◷"),
    TODO("To Do", "▒"),
    IN_PROGRESS("In Progress", "▓"),
    REVIEW("Review", "█"),
    DONE("Done", "✓"),
    ARCHIVED("Archived", "▫")
}

enum class JobStage(val displayName: String, val icon: String) {
    LEAD("Lead", "(○)"),
    PROPOSAL("Proposal", "(○)"),
    APPROVED("Approved", "(○)"),
    IN_PROGRESS("In Progress", "(○)"),
    REVIEW("Review", "(○)"),
    INVOICE("Invoice", "(○)"),
    CLOSED("Closed", "(●)")
}

fun JobStatus.toStage(): JobStage = when (this) {
    JobStatus.BACKLOG -> JobStage.LEAD
    JobStatus.SCHEDULED -> JobStage.LEAD
    JobStatus.TODO -> JobStage.LEAD
    JobStatus.IN_PROGRESS -> JobStage.IN_PROGRESS
    JobStatus.REVIEW -> JobStage.REVIEW
    JobStatus.DONE -> JobStage.CLOSED
    JobStatus.ARCHIVED -> JobStage.CLOSED // Note: also set isArchived=true when migrating ARCHIVED jobs
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
