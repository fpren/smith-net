package com.guildofsmiths.trademesh.ui.documents

import com.guildofsmiths.trademesh.planner.types.ExecutionItem
import com.guildofsmiths.trademesh.ui.jobboard.Job
import com.guildofsmiths.trademesh.ui.jobboard.JobStatus
import com.guildofsmiths.trademesh.ui.timetracking.TimeEntry

/**
 * Guild of Smiths Document System
 * 
 * Three document types with strict availability rules:
 * 1. PROPOSAL - Generated from compiled plan, before work begins
 * 2. REPORT - Generated after inspection job completes
 * 3. INVOICE - Generated when work is done and time is logged
 */

// ════════════════════════════════════════════════════════════════════
// DOCUMENT TYPE
// ════════════════════════════════════════════════════════════════════

enum class DocumentType(val displayName: String, val icon: String) {
    PROPOSAL("Proposal", "[P]"),
    REPORT("Report", "[R]"),
    INVOICE("Invoice", "[I]")
}

// ════════════════════════════════════════════════════════════════════
// DOCUMENT AVAILABILITY
// ════════════════════════════════════════════════════════════════════

/**
 * Document availability status - computed deterministically from job state.
 */
data class DocumentAvailability(
    val type: DocumentType,
    val isAvailable: Boolean,
    val reason: String,
    val prerequisites: List<String> = emptyList()
)

/**
 * Compute document availability for a job.
 * Rules are deterministic based on job state and time entries.
 */
object DocumentAvailabilityRules {
    
    /**
     * Check if Proposal is available.
     * 
     * RULES:
     * - Available IMMEDIATELY after successful compile (has execution items)
     * - Also available if a job exists (was created from compiled plan)
     * - Read-only: Always true (proposals don't change)
     * 
     * Proposal is generated from compiled plan data, showing scope of work
     * before any actual work begins. It captures tasks, materials, and labor.
     */
    fun checkProposal(
        job: Job?,
        executionItems: List<ExecutionItem>?
    ): DocumentAvailability {
        // Proposal available IMMEDIATELY after successful compile
        val hasCompiledItems = !executionItems.isNullOrEmpty()
        val hasJob = job != null
        
        return when {
            hasCompiledItems -> DocumentAvailability(
                type = DocumentType.PROPOSAL,
                isAvailable = true,
                reason = "Plan compiled (${executionItems!!.size} items)"
            )
            hasJob -> DocumentAvailability(
                type = DocumentType.PROPOSAL,
                isAvailable = true,
                reason = "Job available"
            )
            else -> DocumentAvailability(
                type = DocumentType.PROPOSAL,
                isAvailable = false,
                reason = "No compiled plan",
                prerequisites = listOf("Compile a plan first")
            )
        }
    }
    
    /**
     * Check if Report is available.
     * 
     * RULES:
     * - Available: Job has passed inspection phase (status is REVIEW or DONE)
     * - REVIEW = currently in inspection
     * - DONE = inspection completed
     * - Uses: Work log, observations, notes from inspection
     * 
     * Report is specifically a post-inspection document that captures
     * observations, findings, and recommendations from the inspection phase.
     */
    fun checkReport(
        job: Job?
    ): DocumentAvailability {
        if (job == null) {
            return DocumentAvailability(
                type = DocumentType.REPORT,
                isAvailable = false,
                reason = "No job selected",
                prerequisites = listOf("Create a job first")
            )
        }
        
        // Report available after inspection phase (REVIEW or DONE status)
        val isInspectionPhase = job.status == JobStatus.REVIEW
        val isPostInspection = job.status == JobStatus.DONE
        val isInspectionComplete = isInspectionPhase || isPostInspection
        
        // Check if job has work log entries (evidence of inspection)
        val hasWorkLog = job.workLog.isNotEmpty()
        
        return when {
            isPostInspection && hasWorkLog -> DocumentAvailability(
                type = DocumentType.REPORT,
                isAvailable = true,
                reason = "Inspection complete (${job.workLog.size} entries)"
            )
            isPostInspection && !hasWorkLog -> DocumentAvailability(
                type = DocumentType.REPORT,
                isAvailable = true,
                reason = "Ready (add notes for richer report)"
            )
            isInspectionPhase -> DocumentAvailability(
                type = DocumentType.REPORT,
                isAvailable = true,
                reason = "Inspection in progress"
            )
            job.status == JobStatus.IN_PROGRESS -> DocumentAvailability(
                type = DocumentType.REPORT,
                isAvailable = false,
                reason = "Work in progress",
                prerequisites = listOf(
                    "Complete work phase first",
                    "Submit for inspection (REVIEW)"
                )
            )
            else -> DocumentAvailability(
                type = DocumentType.REPORT,
                isAvailable = false,
                reason = "Waiting for inspection",
                prerequisites = listOf(
                    "Complete the work phase (IN_PROGRESS)",
                    "Submit for inspection (REVIEW)"
                )
            )
        }
    }
    
    /**
     * Check if Invoice is available.
     * 
     * RULES:
     * - ONLY available when job is DONE AND has completed time entries
     * - NOT available during work (IN_PROGRESS)
     * - NOT available during inspection (REVIEW) - must complete first
     * - NO automatic generation - user must explicitly request
     * 
     * Invoice is the final document, generated only after:
     * 1. Work is complete (passed through IN_PROGRESS)
     * 2. Inspection is complete (passed through REVIEW)
     * 3. Job marked DONE
     * 4. Time has been logged
     */
    fun checkInvoice(
        job: Job?,
        timeEntries: List<TimeEntry>
    ): DocumentAvailability {
        if (job == null) {
            return DocumentAvailability(
                type = DocumentType.INVOICE,
                isAvailable = false,
                reason = "No job selected",
                prerequisites = listOf("Create a job first")
            )
        }
        
        // Find time entries for this job
        val jobTimeEntries = timeEntries.filter { entry ->
            entry.jobId == job.id || entry.jobTitle == job.title
        }
        val completedEntries = jobTimeEntries.filter { it.clockOutTime != null }
        
        val hasTimeEntries = completedEntries.isNotEmpty()
        val totalMinutes = completedEntries.sumOf { it.durationMinutes ?: 0 }
        
        // Invoice ONLY available when job is DONE (not REVIEW)
        val isFullyComplete = job.status == JobStatus.DONE
        val isInReview = job.status == JobStatus.REVIEW
        val isInProgress = job.status == JobStatus.IN_PROGRESS
        
        return when {
            // READY: Job DONE + time logged
            isFullyComplete && hasTimeEntries -> DocumentAvailability(
                type = DocumentType.INVOICE,
                isAvailable = true,
                reason = "Ready (${formatMinutes(totalMinutes)} logged)"
            )
            
            // BLOCKED: In Review (inspection not complete)
            isInReview && hasTimeEntries -> DocumentAvailability(
                type = DocumentType.INVOICE,
                isAvailable = false,
                reason = "Inspection pending",
                prerequisites = listOf("Complete inspection (mark as DONE)")
            )
            
            // BLOCKED: Job done but no time
            isFullyComplete && !hasTimeEntries -> DocumentAvailability(
                type = DocumentType.INVOICE,
                isAvailable = false,
                reason = "No time logged",
                prerequisites = listOf("Log time entries for this job")
            )
            
            // BLOCKED: Work in progress
            isInProgress -> DocumentAvailability(
                type = DocumentType.INVOICE,
                isAvailable = false,
                reason = "Work in progress",
                prerequisites = listOf(
                    "Complete work phase",
                    "Submit for inspection (REVIEW)",
                    "Complete inspection (DONE)",
                    if (!hasTimeEntries) "Log time entries" else null
                ).filterNotNull()
            )
            
            // BLOCKED: Not started
            else -> DocumentAvailability(
                type = DocumentType.INVOICE,
                isAvailable = false,
                reason = "Not started",
                prerequisites = listOf(
                    "Start work (IN_PROGRESS)",
                    "Complete work phase",
                    "Submit for inspection (REVIEW)",
                    "Complete inspection (DONE)",
                    "Log time entries"
                )
            )
        }
    }
    
    /**
     * Get all document availability states for a job
     */
    fun checkAllDocuments(
        job: Job?,
        executionItems: List<ExecutionItem>?,
        timeEntries: List<TimeEntry>
    ): List<DocumentAvailability> {
        return listOf(
            checkProposal(job, executionItems),
            checkReport(job),
            checkInvoice(job, timeEntries)
        )
    }
    
    private fun formatMinutes(minutes: Int): String {
        val hours = minutes / 60
        val mins = minutes % 60
        return "${hours}h ${mins}m"
    }
}

// ════════════════════════════════════════════════════════════════════
// PROPOSAL
// ════════════════════════════════════════════════════════════════════

/**
 * Proposal document generated from compiled plan.
 * Read-only, available before work begins.
 */
data class Proposal(
    val id: String,
    val createdAt: Long = System.currentTimeMillis(),
    val title: String,
    val description: String = "",
    
    // From compiled plan
    val tasks: List<ProposalTask> = emptyList(),
    val materials: List<ProposalMaterial> = emptyList(),
    val labor: List<ProposalLabor> = emptyList(),
    
    // Scope summary
    val scopeSummary: String = "",
    val totalEstimatedHours: Double = 0.0,
    val totalEstimatedMaterials: Int = 0,
    
    // Provider info
    val providerName: String = "",
    val providerTrade: String = "",
    
    // Status
    val status: ProposalStatus = ProposalStatus.DRAFT,
    
    // ════════════════════════════════════════════════════════════════════
    // CLIENT-FACING PERFORMANCE DATA (safe to show clients)
    // ════════════════════════════════════════════════════════════════════
    val performanceSummary: ProposalPerformanceSummary? = null
)

/**
 * Client-facing performance summary for proposals.
 * Safe to include - only shows positive track record data.
 */
data class ProposalPerformanceSummary(
    // Historical track record
    val similarJobsCompleted: Int,           // e.g., "12 similar jobs completed"
    val avgClientRating: Double?,            // e.g., "9.2/10 average rating"
    val onTimeCompletionRate: Int,           // e.g., "95% on-time completion"
    
    // Quality assurance
    val qualityTrackRecord: String,          // e.g., "100% code compliance"
    val safetyRecord: String,                // e.g., "No safety incidents"
    
    // Market position (vs industry)
    val avgCompletionTime: String?,          // e.g., "Typically completed in 6 hours"
    val marketPosition: String?,             // e.g., "Premium service (+18% above market)"
    
    // Certifications/credentials
    val certifications: List<String> = emptyList(),  // e.g., ["Licensed Master Electrician"]
    val insuranceInfo: String? = null                // e.g., "Fully insured - $2M liability"
)

data class ProposalTask(
    val description: String,
    val source: String  // Original line from plan
)

data class ProposalMaterial(
    val description: String,
    val quantity: String? = null,
    val unit: String? = null,
    val source: String
)

data class ProposalLabor(
    val description: String,
    val hours: Double? = null,
    val role: String? = null,
    val source: String
)

enum class ProposalStatus(val displayName: String) {
    DRAFT("Draft"),
    SENT("Sent"),
    ACCEPTED("Accepted"),
    DECLINED("Declined"),
    EXPIRED("Expired")
}

// ════════════════════════════════════════════════════════════════════
// REPORT
// ════════════════════════════════════════════════════════════════════

/**
 * Report document generated from completed job.
 * Uses work log, notes, and observations.
 */
data class Report(
    val id: String,
    val createdAt: Long = System.currentTimeMillis(),
    val jobId: String,
    val jobTitle: String,
    
    // Job summary
    val startDate: Long? = null,
    val endDate: Long? = null,
    val totalHours: Double = 0.0,
    
    // Work performed
    val workSummary: String = "",
    val observations: List<String> = emptyList(),
    val recommendations: List<String> = emptyList(),
    
    // Materials used
    val materialsUsed: List<MaterialUsed> = emptyList(),
    
    // Crew (if any)
    val crewMembers: List<String> = emptyList(),
    
    // Provider info
    val providerName: String = "",
    val providerTrade: String = "",
    
    // Status
    val status: ReportStatus = ReportStatus.DRAFT
)

data class MaterialUsed(
    val name: String,
    val quantity: Double = 1.0,
    val unit: String = "ea",
    val notes: String = ""
)

enum class ReportStatus(val displayName: String) {
    DRAFT("Draft"),
    FINALIZED("Finalized"),
    SENT("Sent")
}
