package com.guildofsmiths.trademesh.data

import com.guildofsmiths.trademesh.ui.jobboard.Job
import com.guildofsmiths.trademesh.ui.jobboard.Material
import com.guildofsmiths.trademesh.ui.jobboard.TimeEntry

/**
 * Shared Job - Collaborative multi-person job management
 */
data class SharedJob(
    val id: String,
    val baseJob: Job,  // Original job data

    // Collaboration metadata
    val collaborators: List<JobCollaborator> = emptyList(),
    val leadCollaborator: String,  // Who coordinates and creates final invoice
    val collaborationMode: CollaborationMode = CollaborationMode.TEAM_EFFORT,

    // Status progression
    val status: SharedJobStatus = SharedJobStatus.ACTIVE,

    // Reports and sign-offs
    val individualReports: List<JobReport> = emptyList(),
    val signOffs: List<JobSignOff> = emptyList(),

    // Timestamps
    val sharedAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
    val invoicedAt: Long? = null
)

/**
 * Job Collaborator - Individual participant in shared job
 */
data class JobCollaborator(
    val userId: String,
    val userName: String,
    val role: CollaboratorRole,
    val joinedAt: Long = System.currentTimeMillis(),
    val status: CollaboratorStatus = CollaboratorStatus.ACTIVE
)

/**
 * Individual report from a collaborator
 */
data class JobReport(
    val collaboratorId: String,
    val reportType: ReportType,
    val content: String,  // JSON serialized report data
    val submittedAt: Long = System.currentTimeMillis(),
    val attachments: List<String> = emptyList()  // File URLs
)

/**
 * Sign-off from a collaborator
 */
data class JobSignOff(
    val collaboratorId: String,
    val approved: Boolean,
    val comments: String = "",
    val signedAt: Long = System.currentTimeMillis()
)

/**
 * Collaboration mode - how the team works together
 */
enum class CollaborationMode {
    TEAM_EFFORT,       // All work together as one team
    DIVIDED_LABOR,     // Work divided into specific tasks
    SUPERVISED,        // Lead supervises, others execute
}

/**
 * Shared job status progression
 */
enum class SharedJobStatus {
    ACTIVE,             // Job shared, collaborators working
    REPORTS_PENDING,    // Waiting for all collaborators to submit reports
    REPORTS_SUBMITTED,  // All reports submitted, ready for review
    SIGN_OFF_PENDING,   // Reports submitted, waiting for approvals
    SIGNED_OFF,         // All collaborators approved
    COMPLETED,          // Job marked complete
    INVOICED,           // Invoice generated
}

/**
 * Collaborator role in the shared job
 */
enum class CollaboratorRole {
    LEAD,           // Coordinates, creates final invoice
    CONTRIBUTOR,    // Does work, submits reports
    REVIEWER,       // Can review but not contribute work
}

/**
 * Individual collaborator status
 */
enum class CollaboratorStatus {
    INVITED,        // Invited but not accepted
    ACTIVE,         // Participating in job
    SUBMITTED,      // Submitted their report
    SIGNED_OFF,     // Approved completion
    DROPPED_OUT,    // Left the collaboration
}

/**
 * Type of report a collaborator can submit
 */
enum class ReportType {
    TIME_ENTRIES,   // Time worked
    WORK_PERFORMED, // Description of work done
    MATERIALS_USED, // Materials consumed
    PHOTOS,         // Photo documentation
    MEASUREMENTS,   // Dimensions, readings, etc.
    ISSUES,         // Problems encountered and solutions
}

/**
 * Aggregated report combining all collaborator inputs
 */
data class AggregatedReport(
    val jobId: String,
    val totalTime: Double = 0.0,
    val totalMaterials: List<Material> = emptyList(),
    val workDescriptions: List<String> = emptyList(),
    val photoUrls: List<String> = emptyList(),
    val issues: List<String> = emptyList(),
    val measurements: List<String> = emptyList(),
    val aggregatedAt: Long = System.currentTimeMillis(),
    val aggregatedBy: String  // Who created the aggregation
)