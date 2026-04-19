package com.guildofsmiths.trademesh.ai

import com.guildofsmiths.trademesh.ui.jobboard.Job
import com.guildofsmiths.trademesh.ui.jobboard.JobStage

/**
 * Prompt templates for the AI Supervisor.
 * Builds context from real app data for focused use cases.
 */
object AIPrompts {

    val SYSTEM: String get() {
        val isSolo = com.guildofsmiths.trademesh.data.RoleContext.isSolo()
        return if (isSolo) {
            "You are SmithAI, a personal assistant for a solo tradesperson using SmithNet. You help track jobs, clients, materials, and schedule. Be direct, practical, and brief. No fluff. Speak like a helpful assistant — clear and actionable."
        } else {
            "You are SmithAI, a construction supervisor assistant for SmithNet. You observe jobs, crew, and operations. Be direct, practical, and brief. No fluff. Speak like an experienced foreman — clear and actionable."
        }
    }

    /**
     * Generate a job status summary.
     */
    fun jobSummary(job: Job): String {
        val checkedMaterials = job.materials.count { it.checked }
        val totalMaterials = job.materials.size
        val materialsBudget = job.materials.sumOf { it.quantity * it.unitCost }
        val materialsSpent = job.materials.filter { it.checked }.sumOf { it.totalCost }
        val daysSinceCreated = ((System.currentTimeMillis() - job.createdAt) / 86_400_000).toInt()
        val daysSinceUpdate = ((System.currentTimeMillis() - job.updatedAt) / 86_400_000).toInt()

        return """Summarize this job status in 2 sentences:
Client: ${job.clientName ?: "Unknown"}
Site: ${job.clientAddress}
Stage: ${job.stage.displayName}
Days active: $daysSinceCreated (last update: ${daysSinceUpdate}d ago)
Materials: $checkedMaterials/$totalMaterials checked (${"$%.0f".format(materialsSpent)}/${"$%.0f".format(materialsBudget)})
Crew size: ${job.crewSize}
Hourly rate: $${job.hourlyRate}/hr"""
    }

    /**
     * Flag concerns across all active jobs.
     */
    fun checkIn(jobs: List<Job>): String {
        val jobLines = jobs.filter { it.stage != JobStage.CLOSED }.map { job ->
            val daysSinceUpdate = ((System.currentTimeMillis() - job.updatedAt) / 86_400_000).toInt()
            val checkedMaterials = job.materials.count { it.checked }
            val totalMaterials = job.materials.size
            val materialsBudget = job.materials.sumOf { it.quantity * it.unitCost }
            val materialsSpent = job.materials.filter { it.checked }.sumOf { it.totalCost }
            val budgetPct = if (materialsBudget > 0) (materialsSpent / materialsBudget * 100).toInt() else 0

            "- ${job.clientName ?: job.title}: ${job.stage.displayName}, updated ${daysSinceUpdate}d ago, materials $checkedMaterials/$totalMaterials (${budgetPct}% budget used)"
        }.joinToString("\n")

        val isSolo = com.guildofsmiths.trademesh.data.RoleContext.isSolo()
        val prompt = if (isSolo) {
            "Review your active jobs and flag anything that needs your attention. Only flag real issues — jobs with no progress in 2+ days, budget concerns (80%+ materials spent), or missing materials. Max 3 bullet points. If everything looks fine, say so."
        } else {
            "Review these active jobs and flag anything that needs attention. Only flag real issues — stale jobs (no update 2+ days), budget concerns (80%+ spent), or missing materials. Max 3 bullet points. If everything looks fine, say so."
        }
        return """$prompt

$jobLines"""
    }

    /**
     * Summarize a stage transition for a job.
     */
    fun stageChange(job: Job, oldStage: JobStage, newStage: JobStage): String {
        val checkedMaterials = job.materials.count { it.checked }
        val totalMaterials = job.materials.size
        val totalCost = job.materials.sumOf { it.totalCost } + (job.hourlyRate * 8)
        val crewNames = job.crew.joinToString(", ") { it.name }.ifBlank { "none assigned" }

        return """Job stage changed. Give a 1-sentence status update:
Client: ${job.clientName ?: "Unknown"}
Job: ${job.title}
Moved from: ${oldStage.displayName} → ${newStage.displayName}
Materials: $checkedMaterials/$totalMaterials checked
Crew: $crewNames
Est. value: ${"$%.0f".format(totalCost)}
Be brief and actionable."""
    }

    /**
     * Draft a message to crew about a specific job.
     */
    fun staffMessage(jobTitle: String, crewName: String, context: String): String {
        return """Draft a brief message to $crewName about the "$jobTitle" job.
Context: $context
Keep under 40 words. Professional but casual tone. No greeting needed."""
    }

    /**
     * Generate a daily log summary for a job.
     */
    fun dailyLogSummary(
        jobTitle: String,
        clientName: String?,
        hoursWorked: Double,
        crewPresent: List<String>,
        materialsChecked: Int,
        materialsCost: Double,
        workerNotes: List<String>,
        timeNotes: List<String>
    ): String {
        val notesBlock = (workerNotes + timeNotes).joinToString("\n") { "- $it" }.ifBlank { "- No notes" }
        val crewBlock = crewPresent.joinToString(", ").ifBlank { "solo" }

        return """Write a 2-3 sentence end-of-day summary for this job. Include what was accomplished, any issues, and next steps. Be direct like a foreman's field report.

Job: $jobTitle
Client: ${clientName ?: "Unknown"}
Hours today: ${String.format("%.1f", hoursWorked)}h
Crew: $crewBlock
Materials checked: $materialsChecked (${"$%.0f".format(materialsCost)} spent)
Worker notes:
$notesBlock"""
    }

    /**
     * Check if a worker note needs clarification.
     */
    fun clarifyNote(noteText: String, jobTitle: String): String {
        return """A worker left this note on the "$jobTitle" job:
"$noteText"

Does this note make sense as a work log entry? If yes, reply with exactly: CLEAR
If no, reply with ONE short question to clarify what they mean. Max 15 words."""
    }

    /**
     * Generate a proposal based on scope of work and trade.
     * Output is structured for parsing.
     */
    fun generateProposal(scopeStatement: String, trade: String, clientName: String? = null): String {
        val clientLine = if (clientName != null) "\nClient: $clientName" else ""
        return """You are helping a $trade create a job proposal.
Scope of work: "$scopeStatement"$clientLine

Generate a practical proposal with:
1. 3-6 specific tasks to complete this work (numbered)
2. Equipment needed (bulleted)
3. Supplies and materials needed (bulleted)
4. Recommended crew size (number)

Use this exact format:
TASKS:
1. First task
2. Second task
EQUIPMENT:
- Item one
- Item two
SUPPLIES:
- Material one
- Material two
CREW: 2

Be specific to the trade and scope. Use real materials and tools a $trade would use. Keep it practical."""
    }

    /**
     * Detect if a job needs attention based on thresholds.
     * Returns a description of the issue, or null if the job is fine.
     */
    fun detectIssue(job: Job): String? {
        val daysSinceUpdate = ((System.currentTimeMillis() - job.updatedAt) / 86_400_000).toInt()
        val materialsBudget = job.materials.sumOf { it.quantity * it.unitCost }
        val materialsSpent = job.materials.filter { it.checked }.sumOf { it.totalCost }
        val budgetPct = if (materialsBudget > 0) (materialsSpent / materialsBudget * 100).toInt() else 0

        return when {
            job.stage == JobStage.CLOSED -> null
            daysSinceUpdate >= 3 && job.stage == JobStage.IN_PROGRESS ->
                "No update in ${daysSinceUpdate} days — job may be stalled"
            budgetPct >= 80 ->
                "Materials budget ${budgetPct}% used (${"$%.0f".format(materialsSpent)}/${"$%.0f".format(materialsBudget)})"
            job.stage == JobStage.INVOICE && daysSinceUpdate >= 5 ->
                "Invoice pending ${daysSinceUpdate} days — follow up with client"
            else -> null
        }
    }
}
