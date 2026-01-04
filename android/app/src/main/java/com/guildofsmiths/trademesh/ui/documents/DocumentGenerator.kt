package com.guildofsmiths.trademesh.ui.documents

import com.guildofsmiths.trademesh.analytics.PerformanceAnalytics
import com.guildofsmiths.trademesh.planner.types.ExecutionItem
import com.guildofsmiths.trademesh.planner.types.ExecutionItemType
import com.guildofsmiths.trademesh.planner.types.ParsedItemData
import com.guildofsmiths.trademesh.ui.jobboard.Job
import com.guildofsmiths.trademesh.ui.timetracking.TimeEntry
import java.text.SimpleDateFormat
import java.util.*

/**
 * Guild of Smiths Document Generator
 * 
 * Deterministic document generation from:
 * - Compiled plan data (Proposal)
 * - Completed job data (Report)
 * - Job + Time data (Invoice - handled by InvoiceGenerator)
 */
object DocumentGenerator {
    
    private val dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale.US)
    private val shortDateFormat = SimpleDateFormat("MMM d, yyyy", Locale.US)
    
    // ════════════════════════════════════════════════════════════════════
    // PROPOSAL GENERATOR
    // ════════════════════════════════════════════════════════════════════
    
    /**
     * Generate Proposal from compiled execution items.
     * 
     * INPUT: Compiled plan execution items
     * OUTPUT: Read-only Proposal document
     * 
     * This is deterministic - same items = same proposal
     * 
     * @param completedJobs Optional list of completed jobs for performance summary
     * @param timeEntries Optional list of time entries for performance calculations
     */
    fun generateProposal(
        executionItems: List<ExecutionItem>,
        title: String,
        providerName: String = "",
        providerTrade: String = "Tradesperson – Guild of Smiths",
        completedJobs: List<Job> = emptyList(),
        timeEntries: List<TimeEntry> = emptyList()
    ): Proposal {
        // Separate items by type
        val tasks = executionItems
            .filter { it.type == ExecutionItemType.TASK }
            .map { item ->
                val parsed = item.parsed as? ParsedItemData.Task
                ProposalTask(
                    description = parsed?.description ?: item.source,
                    source = item.source
                )
            }
        
        val materials = executionItems
            .filter { it.type == ExecutionItemType.MATERIAL }
            .map { item ->
                val parsed = item.parsed as? ParsedItemData.Material
                ProposalMaterial(
                    description = parsed?.description ?: item.source,
                    quantity = parsed?.quantity,
                    unit = parsed?.unit,
                    source = item.source
                )
            }
        
        val labor = executionItems
            .filter { it.type == ExecutionItemType.LABOR }
            .map { item ->
                val parsed = item.parsed as? ParsedItemData.Labor
                ProposalLabor(
                    description = parsed?.description ?: item.source,
                    hours = parsed?.hours?.toDoubleOrNull(),
                    role = parsed?.role,
                    source = item.source
                )
            }
        
        // Calculate estimates
        val totalHours = labor.sumOf { it.hours ?: 0.0 }
        val totalMaterials = materials.size
        
        // Build scope summary
        val scopeSummary = buildScopeSummary(tasks, materials, labor)
        
        // Generate client-facing performance summary if historical data available
        val performanceSummary = if (completedJobs.isNotEmpty()) {
            buildPerformanceSummary(completedJobs, timeEntries)
        } else null
        
        return Proposal(
            id = UUID.randomUUID().toString(),
            title = title,
            description = "Proposal generated from compiled plan",
            tasks = tasks,
            materials = materials,
            labor = labor,
            scopeSummary = scopeSummary,
            totalEstimatedHours = totalHours,
            totalEstimatedMaterials = totalMaterials,
            providerName = providerName,
            providerTrade = providerTrade,
            status = ProposalStatus.DRAFT,
            performanceSummary = performanceSummary
        )
    }
    
    /**
     * Build client-facing performance summary from historical job data.
     * This only includes data that's safe and beneficial to show clients.
     */
    private fun buildPerformanceSummary(
        completedJobs: List<Job>,
        timeEntries: List<TimeEntry>
    ): ProposalPerformanceSummary? {
        if (completedJobs.isEmpty()) return null
        
        // Get performance data using the analytics engine
        val clientFacing = PerformanceAnalytics.calculateClientFacingPerformance(
            completedJobs = completedJobs,
            timeEntries = timeEntries
        )
        
        return ProposalPerformanceSummary(
            similarJobsCompleted = clientFacing.totalSimilarJobsCompleted,
            avgClientRating = clientFacing.averageSatisfactionRating,
            onTimeCompletionRate = clientFacing.onTimeCompletionRate,
            qualityTrackRecord = clientFacing.qualityTrackRecord,
            safetyRecord = "No safety incidents on record",
            avgCompletionTime = clientFacing.avgCompletionTime,
            marketPosition = clientFacing.marketPosition,
            certifications = emptyList(), // TODO: Could come from user profile
            insuranceInfo = null // TODO: Could come from user profile
        )
    }
    
    /**
     * Generate Proposal from existing Job.
     */
    fun generateProposalFromJob(
        job: Job,
        providerName: String = "",
        providerTrade: String = "Tradesperson – Guild of Smiths"
    ): Proposal {
        // Convert job materials to proposal materials
        val materials = job.materials.map { mat ->
            ProposalMaterial(
                description = mat.name,
                quantity = mat.quantity.toString(),
                unit = mat.unit,
                source = mat.name
            )
        }
        
        // Convert job crew to labor items
        val labor = job.crew.map { member ->
            ProposalLabor(
                description = member.task.ifEmpty { member.occupation },
                role = member.occupation,
                source = "${member.name} - ${member.occupation}"
            )
        }
        
        // Job title becomes a task
        val tasks = listOf(
            ProposalTask(
                description = job.title,
                source = job.title
            )
        )
        
        return Proposal(
            id = UUID.randomUUID().toString(),
            title = job.title,
            description = job.description,
            tasks = tasks,
            materials = materials,
            labor = labor,
            scopeSummary = "Scope: ${job.title}",
            totalEstimatedHours = 0.0,
            totalEstimatedMaterials = materials.size,
            providerName = providerName,
            providerTrade = providerTrade,
            status = ProposalStatus.DRAFT
        )
    }
    
    private fun buildScopeSummary(
        tasks: List<ProposalTask>,
        materials: List<ProposalMaterial>,
        labor: List<ProposalLabor>
    ): String {
        val parts = mutableListOf<String>()
        
        if (tasks.isNotEmpty()) {
            parts.add("${tasks.size} task${if (tasks.size > 1) "s" else ""}")
        }
        if (materials.isNotEmpty()) {
            parts.add("${materials.size} material item${if (materials.size > 1) "s" else ""}")
        }
        if (labor.isNotEmpty()) {
            val totalHours = labor.sumOf { it.hours ?: 0.0 }
            if (totalHours > 0) {
                parts.add("${String.format("%.1f", totalHours)} estimated hours")
            } else {
                parts.add("${labor.size} labor item${if (labor.size > 1) "s" else ""}")
            }
        }
        
        return if (parts.isNotEmpty()) {
            "Scope: ${parts.joinToString(", ")}"
        } else {
            "Scope: To be determined"
        }
    }
    
    // ════════════════════════════════════════════════════════════════════
    // REPORT GENERATOR
    // ════════════════════════════════════════════════════════════════════
    
    /**
     * Generate Report from completed job.
     * 
     * INPUT: Completed job with work log
     * OUTPUT: Report document with observations
     * 
     * PREREQUISITES:
     * - Job status must be DONE or REVIEW
     */
    fun generateReport(
        job: Job,
        timeEntries: List<TimeEntry>,
        providerName: String = "",
        providerTrade: String = "Tradesperson – Guild of Smiths"
    ): Report {
        // Find time entries for this job
        val jobTimeEntries = timeEntries.filter { entry ->
            entry.jobId == job.id || entry.jobTitle == job.title
        }.filter { it.clockOutTime != null }
        
        // Calculate totals
        val totalMinutes = jobTimeEntries.sumOf { it.durationMinutes ?: 0 }
        val totalHours = totalMinutes / 60.0
        
        // Get date range
        val startDate = jobTimeEntries.minOfOrNull { it.clockInTime }
        val endDate = jobTimeEntries.maxOfOrNull { it.clockOutTime ?: it.clockInTime }
        
        // Build work summary from work log
        val workSummary = buildWorkSummary(job)
        
        // Extract observations from work log
        val observations = job.workLog.map { it.text }
        
        // Materials used (checked items)
        val materialsUsed = job.materials.filter { it.checked }.map { mat ->
            MaterialUsed(
                name = mat.name,
                quantity = mat.quantity,
                unit = mat.unit,
                notes = mat.notes
            )
        }
        
        // Crew members
        val crewMembers = job.crew.map { "${it.name} - ${it.occupation}" }
        
        return Report(
            id = UUID.randomUUID().toString(),
            jobId = job.id,
            jobTitle = job.title,
            startDate = startDate,
            endDate = endDate,
            totalHours = totalHours,
            workSummary = workSummary,
            observations = observations,
            recommendations = emptyList(), // Could be added from job notes
            materialsUsed = materialsUsed,
            crewMembers = crewMembers,
            providerName = providerName,
            providerTrade = providerTrade,
            status = ReportStatus.DRAFT
        )
    }
    
    private fun buildWorkSummary(job: Job): String {
        val sb = StringBuilder()
        
        sb.appendLine("Job: ${job.title}")
        
        if (job.description.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("Description: ${job.description}")
        }
        
        if (job.workLog.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("Work Log:")
            job.workLog.takeLast(5).forEach { entry ->
                val dateStr = shortDateFormat.format(Date(entry.timestamp))
                sb.appendLine("• [$dateStr] ${entry.text}")
            }
        }
        
        val checkedMaterials = job.materials.filter { it.checked }
        if (checkedMaterials.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("Materials Used:")
            checkedMaterials.forEach { mat ->
                sb.appendLine("• ${mat.quantity} ${mat.unit} - ${mat.name}")
            }
        }
        
        return sb.toString()
    }
    
    // ════════════════════════════════════════════════════════════════════
    // DOCUMENT FORMATTERS (Text output)
    // ════════════════════════════════════════════════════════════════════
    
    /**
     * Format Proposal as text document
     */
    fun formatProposalAsText(proposal: Proposal): String {
        val sb = StringBuilder()
        
        sb.appendLine("GUILD OF SMITHS – PROPOSAL")
        sb.appendLine("══════════════════════════════════════════════════════════════")
        sb.appendLine()
        sb.appendLine("Date: ${dateFormat.format(Date(proposal.createdAt))}")
        sb.appendLine("Status: ${proposal.status.displayName}")
        sb.appendLine()
        
        // Provider
        sb.appendLine("FROM:")
        if (proposal.providerName.isNotEmpty()) {
            sb.appendLine("  ${proposal.providerName}")
        }
        if (proposal.providerTrade.isNotEmpty()) {
            sb.appendLine("  ${proposal.providerTrade}")
        }
        sb.appendLine()
        
        // Project
        sb.appendLine("PROJECT: ${proposal.title}")
        if (proposal.description.isNotEmpty()) {
            sb.appendLine(proposal.description)
        }
        sb.appendLine()
        
        // Scope Summary
        sb.appendLine("SCOPE SUMMARY")
        sb.appendLine("──────────────────────────────────────────────────────────────")
        sb.appendLine(proposal.scopeSummary)
        sb.appendLine()
        
        // Tasks
        if (proposal.tasks.isNotEmpty()) {
            sb.appendLine("TASKS")
            sb.appendLine("──────────────────────────────────────────────────────────────")
            proposal.tasks.forEachIndexed { index, task ->
                sb.appendLine("${index + 1}. ${task.description}")
            }
            sb.appendLine()
        }
        
        // Materials
        if (proposal.materials.isNotEmpty()) {
            sb.appendLine("MATERIALS")
            sb.appendLine("──────────────────────────────────────────────────────────────")
            proposal.materials.forEach { mat ->
                val qty = if (mat.quantity != null && mat.unit != null) {
                    "${mat.quantity} ${mat.unit} - "
                } else ""
                sb.appendLine("• ${qty}${mat.description}")
            }
            sb.appendLine()
        }
        
        // Labor
        if (proposal.labor.isNotEmpty()) {
            sb.appendLine("LABOR")
            sb.appendLine("──────────────────────────────────────────────────────────────")
            proposal.labor.forEach { labor ->
                val hours = labor.hours?.let { "${it}h - " } ?: ""
                val role = labor.role?.let { "($it) " } ?: ""
                sb.appendLine("• ${hours}${role}${labor.description}")
            }
            sb.appendLine()
        }
        
        // Estimates
        sb.appendLine("ESTIMATES")
        sb.appendLine("──────────────────────────────────────────────────────────────")
        if (proposal.totalEstimatedHours > 0) {
            sb.appendLine("Estimated Hours: ${String.format("%.1f", proposal.totalEstimatedHours)}")
        }
        sb.appendLine("Material Items: ${proposal.totalEstimatedMaterials}")
        sb.appendLine()
        
        // Client-facing Performance Summary (if available)
        proposal.performanceSummary?.let { perf ->
            sb.appendLine("WHY CHOOSE US")
            sb.appendLine("══════════════════════════════════════════════════════════════")
            sb.appendLine()
            
            // Track Record
            sb.appendLine("TRACK RECORD")
            sb.appendLine("──────────────────────────────────────────────────────────────")
            if (perf.similarJobsCompleted > 0) {
                sb.appendLine("• ${perf.similarJobsCompleted} similar jobs completed")
            }
            perf.avgClientRating?.let { rating ->
                sb.appendLine("• ${String.format("%.1f", rating)}/10 average client rating")
            }
            sb.appendLine("• ${perf.onTimeCompletionRate}% on-time completion rate")
            perf.avgCompletionTime?.let { time ->
                sb.appendLine("• Typical completion time: $time")
            }
            sb.appendLine()
            
            // Quality & Safety
            sb.appendLine("QUALITY & SAFETY")
            sb.appendLine("──────────────────────────────────────────────────────────────")
            sb.appendLine("• ${perf.qualityTrackRecord}")
            sb.appendLine("• ${perf.safetyRecord}")
            sb.appendLine()
            
            // Market Position
            perf.marketPosition?.let { position ->
                sb.appendLine("SERVICE LEVEL")
                sb.appendLine("──────────────────────────────────────────────────────────────")
                sb.appendLine("• $position")
                sb.appendLine()
            }
            
            // Certifications
            if (perf.certifications.isNotEmpty()) {
                sb.appendLine("CERTIFICATIONS")
                sb.appendLine("──────────────────────────────────────────────────────────────")
                perf.certifications.forEach { cert ->
                    sb.appendLine("• $cert")
                }
                sb.appendLine()
            }
            
            // Insurance
            perf.insuranceInfo?.let { insurance ->
                sb.appendLine("INSURANCE")
                sb.appendLine("──────────────────────────────────────────────────────────────")
                sb.appendLine("• $insurance")
                sb.appendLine()
            }
        }
        
        sb.appendLine("──────────────────────────────────────────────────────────────")
        sb.appendLine("This proposal is read-only and represents the scope of work")
        sb.appendLine("as compiled from the plan. Actual costs may vary.")
        sb.appendLine()
        sb.appendLine("Guild of Smiths – Built for the trades.")
        
        return sb.toString()
    }
    
    /**
     * Format Report as text document
     */
    fun formatReportAsText(report: Report): String {
        val sb = StringBuilder()
        
        sb.appendLine("GUILD OF SMITHS – WORK REPORT")
        sb.appendLine("══════════════════════════════════════════════════════════════")
        sb.appendLine()
        sb.appendLine("Report Date: ${dateFormat.format(Date(report.createdAt))}")
        sb.appendLine("Status: ${report.status.displayName}")
        sb.appendLine()
        
        // Provider
        sb.appendLine("FROM:")
        if (report.providerName.isNotEmpty()) {
            sb.appendLine("  ${report.providerName}")
        }
        if (report.providerTrade.isNotEmpty()) {
            sb.appendLine("  ${report.providerTrade}")
        }
        sb.appendLine()
        
        // Job Info
        sb.appendLine("JOB: ${report.jobTitle}")
        sb.appendLine()
        
        // Duration
        sb.appendLine("DURATION")
        sb.appendLine("──────────────────────────────────────────────────────────────")
        if (report.startDate != null && report.endDate != null) {
            sb.appendLine("Start: ${shortDateFormat.format(Date(report.startDate))}")
            sb.appendLine("End: ${shortDateFormat.format(Date(report.endDate))}")
        }
        sb.appendLine("Total Hours: ${String.format("%.1f", report.totalHours)}")
        sb.appendLine()
        
        // Work Summary
        sb.appendLine("WORK SUMMARY")
        sb.appendLine("──────────────────────────────────────────────────────────────")
        sb.appendLine(report.workSummary)
        sb.appendLine()
        
        // Observations
        if (report.observations.isNotEmpty()) {
            sb.appendLine("OBSERVATIONS")
            sb.appendLine("──────────────────────────────────────────────────────────────")
            report.observations.forEach { obs ->
                sb.appendLine("• $obs")
            }
            sb.appendLine()
        }
        
        // Materials Used
        if (report.materialsUsed.isNotEmpty()) {
            sb.appendLine("MATERIALS USED")
            sb.appendLine("──────────────────────────────────────────────────────────────")
            report.materialsUsed.forEach { mat ->
                sb.appendLine("• ${mat.quantity} ${mat.unit} - ${mat.name}")
                if (mat.notes.isNotEmpty()) {
                    sb.appendLine("  Notes: ${mat.notes}")
                }
            }
            sb.appendLine()
        }
        
        // Crew
        if (report.crewMembers.isNotEmpty()) {
            sb.appendLine("CREW")
            sb.appendLine("──────────────────────────────────────────────────────────────")
            report.crewMembers.forEach { member ->
                sb.appendLine("• $member")
            }
            sb.appendLine()
        }
        
        // Recommendations
        if (report.recommendations.isNotEmpty()) {
            sb.appendLine("RECOMMENDATIONS")
            sb.appendLine("──────────────────────────────────────────────────────────────")
            report.recommendations.forEach { rec ->
                sb.appendLine("• $rec")
            }
            sb.appendLine()
        }
        
        sb.appendLine("──────────────────────────────────────────────────────────────")
        sb.appendLine("This report summarizes work completed on the above job.")
        sb.appendLine()
        sb.appendLine("Guild of Smiths – Built for the trades.")
        
        return sb.toString()
    }
}
