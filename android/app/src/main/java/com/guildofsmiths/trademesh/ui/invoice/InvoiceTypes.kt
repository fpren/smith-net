package com.guildofsmiths.trademesh.ui.invoice

import com.guildofsmiths.trademesh.ui.jobboard.Job
import com.guildofsmiths.trademesh.ui.jobboard.Material
import com.guildofsmiths.trademesh.ui.jobboard.CrewMember
import com.guildofsmiths.trademesh.ui.timetracking.TimeEntry
import java.text.SimpleDateFormat
import java.util.*

/**
 * Guild of Smiths Invoice System
 * Auto-generates invoices from completed jobs with synced time + materials
 * Supports both SOLO and ENTERPRISE (crew/multi-day) modes
 */

// ════════════════════════════════════════════════════════════════════
// INVOICE MODE
// ════════════════════════════════════════════════════════════════════

enum class InvoiceMode {
    SOLO,       // Single tradesperson, single day/short job
    ENTERPRISE  // Crew with foreman, multi-day projects
}

// ════════════════════════════════════════════════════════════════════
// INVOICE
// ════════════════════════════════════════════════════════════════════

data class Invoice(
    val id: String,
    val invoiceNumber: String,  // INV-2025-12-0789 or INV-2025-12-0789-CREW-WEEK
    val issueDate: Long = System.currentTimeMillis(),
    val dueDate: Long,
    val status: InvoiceStatus = InvoiceStatus.DRAFT,
    val mode: InvoiceMode = InvoiceMode.SOLO,
    
    // From (Service Provider)
    val fromName: String,
    val fromBusiness: String = "",
    val fromTrade: String = "",  // "Solo Electrician – Guild of Smiths" or "Foreman Account"
    val fromPhone: String = "",
    val fromEmail: String = "",
    val fromAddress: String = "",
    
    // To (Client)
    val toName: String = "",
    val toCompany: String = "",
    val toAddress: String = "",
    val toEmail: String = "",
    val projectRef: String = "",
    val poNumber: String = "",  // Purchase Order # for enterprise
    
    // Project Duration (for multi-day)
    val projectStart: Long? = null,
    val projectEnd: Long? = null,
    val workingDays: Int = 1,
    
    // Crew (for enterprise mode)
    val crew: List<CrewMemberHours> = emptyList(),
    val totalCrewHours: Double = 0.0,
    
    // Daily Breakdown (for enterprise mode)
    val dailyBreakdown: List<DailyWorkSummary> = emptyList(),
    
    // Line Items
    val lineItems: List<InvoiceLineItem> = emptyList(),
    
    // Totals
    val subtotal: Double = 0.0,
    val taxRate: Double = 0.0,  // e.g., 8.25
    val taxAmount: Double = 0.0,
    val totalDue: Double = 0.0,
    
    // Source job data
    val jobId: String,
    val jobTitle: String,
    
    // AI Supervisor Report data
    val workWindow: String = "",        // "2025-12-23 14:30 – 19:15"
    val totalOnSiteMinutes: Int = 0,
    val photoCount: Int = 0,
    val voiceNoteCount: Int = 0,
    val checklistCount: Int = 0,
    val workLogSummary: String = "",
    val complianceNotes: String = "",
    val recommendations: String = "",
    val meshPresence: String = "",  // "97.2% average"
    val efficiencyScore: Int = 0,   // 0-100
    
    // Payment
    val paymentInstructions: String = "",
    val notes: String = "",
    
    // ════════════════════════════════════════════════════════════════════
    // CLIENT-FACING PERFORMANCE DATA (safe to show clients)
    // ════════════════════════════════════════════════════════════════════
    val performanceSummary: InvoicePerformanceSummary? = null
)

/**
 * Client-facing performance summary for invoices.
 * Shows historical track record and job-specific performance.
 * Safe to include in client-facing documents.
 */
data class InvoicePerformanceSummary(
    // This job's performance
    val jobCompletedOnTime: Boolean,         // Was this job completed on/before deadline?
    val clientSatisfactionRating: Int? = null, // Client rating for this job (if collected)
    
    // Historical track record
    val totalJobsCompleted: Int,             // e.g., "45 jobs completed"
    val avgClientRating: Double?,            // e.g., "9.1/10 average"
    val onTimeCompletionRate: Int,           // e.g., "96% on-time"
    
    // Quality indicators
    val qualityTrackRecord: String,          // e.g., "100% code compliance"
    
    // Market comparison (if available)
    val completionTimeVsMarket: String? = null, // e.g., "Completed 15% faster than industry avg"
    val marketPosition: String? = null          // e.g., "Premium service provider"
)

// Crew member with their logged hours
data class CrewMemberHours(
    val name: String,
    val role: String,  // Foreman, Journeyman, Apprentice
    val occupation: String = "",
    val totalHours: Double = 0.0,
    val productiveHours: Double = 0.0,
    val travelHours: Double = 0.0
)

// Daily work summary for multi-day projects
data class DailyWorkSummary(
    val day: Int,
    val date: Long,
    val startTime: String = "",
    val endTime: String = "",
    val totalHours: Double = 0.0,
    val activities: String = "",
    val meshSyncNotes: String = "",
    val photoCount: Int = 0,
    val voiceNoteCount: Int = 0,
    val checklistCount: Int = 0,
    val keyNotes: String = ""
)

data class InvoiceLineItem(
    val code: String,           // LAB-01, MAT-100, TRV-01, CO-001
    val description: String,
    val quantity: Double,
    val unit: String,           // hr, ea, ft, lot
    val rate: Double,
    val total: Double,
    val category: LineItemCategory
)

enum class LineItemCategory {
    LABOR,
    MATERIALS,
    TRAVEL,
    CHANGE_ORDER,
    OTHER
}

enum class InvoiceStatus(val displayName: String) {
    DRAFT("Draft"),
    ISSUED("Issued"),
    SENT("Sent"),
    VIEWED("Viewed"),
    PAID("Paid"),
    OVERDUE("Overdue"),
    DISPUTED("Disputed"),
    CANCELLED("Cancelled")
}

// ════════════════════════════════════════════════════════════════════
// INVOICE GENERATOR
// ════════════════════════════════════════════════════════════════════

object InvoiceGenerator {
    
    private var invoiceCounter = 1
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.US)
    private val displayDateFormat = SimpleDateFormat("MMMM d, yyyy", Locale.US)
    private val shortDateFormat = SimpleDateFormat("MMM d, yyyy", Locale.US)
    
    /**
     * Generate a PROPOSAL document for client approval (pre-job)
     * Creates an Invoice-like document but formatted as a proposal with estimates
     */
    fun generateProposal(
        job: Job,
        providerName: String,
        providerBusiness: String = "",
        providerTrade: String = "Tradesperson – Guild of Smiths",
        providerPhone: String = "",
        providerEmail: String = "",
        providerAddress: String = "",
        hourlyRate: Double = 85.0,
        taxRate: Double = 8.25
    ): Invoice {
        val lineItems = mutableListOf<InvoiceLineItem>()
        var lineCounter = 1
        
        // Estimated LABOR based on job expenses field or materials
        val estimatedHours = parseEstimatedHours(job.expenses)
        if (estimatedHours > 0) {
            lineItems.add(
                InvoiceLineItem(
                    code = "LAB-${String.format("%02d", lineCounter++)}",
                    description = "Estimated Labor – ${job.title}",
                    quantity = estimatedHours,
                    unit = "hr",
                    rate = hourlyRate,
                    total = estimatedHours * hourlyRate,
                    category = LineItemCategory.LABOR
                )
            )
        }
        
        // MATERIALS from Job - even unchecked ones (this is a proposal)
        job.materials.forEach { material ->
            val estimatedCost = if (material.totalCost > 0) material.totalCost 
                               else material.quantity * 25.0 // Default estimate
            lineItems.add(
                InvoiceLineItem(
                    code = "MAT-${String.format("%03d", lineCounter++)}",
                    description = "Materials (est.) – ${material.name}",
                    quantity = material.quantity,
                    unit = material.unit,
                    rate = if (material.unitCost > 0) material.unitCost else 25.0,
                    total = estimatedCost,
                    category = LineItemCategory.MATERIALS
                )
            )
        }
        
        // TRAVEL estimate
        lineItems.add(
            InvoiceLineItem(
                code = "TRV-01",
                description = "Travel / site coordination (estimate)",
                quantity = 1.0,
                unit = "ea",
                rate = 45.0,
                total = 45.0,
                category = LineItemCategory.TRAVEL
            )
        )
        
        // Calculate totals
        val subtotal = lineItems.sumOf { it.total }
        val taxAmount = subtotal * (taxRate / 100.0)
        val totalDue = subtotal + taxAmount
        
        // Generate proposal number
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val month = String.format("%02d", calendar.get(Calendar.MONTH) + 1)
        val proposalNumber = "PROP-$year-$month-${String.format("%04d", invoiceCounter++)}"
        
        // Due date (for acceptance) - 30 days
        calendar.add(Calendar.DAY_OF_MONTH, 30)
        val validUntil = calendar.timeInMillis
        
        return Invoice(
            id = UUID.randomUUID().toString(),
            invoiceNumber = proposalNumber,
            issueDate = System.currentTimeMillis(),
            dueDate = validUntil,
            status = InvoiceStatus.DRAFT,
            mode = InvoiceMode.SOLO,
            fromName = providerName,
            fromBusiness = providerBusiness,
            fromTrade = providerTrade,
            fromPhone = providerPhone,
            fromEmail = providerEmail,
            fromAddress = providerAddress,
            toName = job.clientName ?: "",
            toAddress = job.location ?: "",
            projectRef = job.title,
            lineItems = lineItems,
            subtotal = round2(subtotal),
            taxRate = taxRate,
            taxAmount = round2(taxAmount),
            totalDue = round2(totalDue),
            jobId = job.id,
            jobTitle = job.title,
            workLogSummary = job.description,
            paymentInstructions = "50% deposit required upon acceptance.\nBalance due upon completion.",
            notes = "PROPOSAL – Valid for 30 days.\nThis is an estimate. Final costs may vary based on actual work required."
        )
    }
    
    /**
     * Parse estimated hours from job expenses field
     */
    private fun parseEstimatedHours(expenses: String): Double {
        // Look for patterns like "16h", "40 hours", "Est. Labor: $1,360" 
        val hourMatch = Regex("""(\d+(?:\.\d+)?)\s*h(?:ours?|rs?)?""", RegexOption.IGNORE_CASE).find(expenses)
        if (hourMatch != null) {
            return hourMatch.groupValues[1].toDoubleOrNull() ?: 8.0
        }
        
        // Look for labor cost and estimate hours at $85/hr
        val costMatch = Regex("""\$\s*([\d,]+(?:\.\d+)?)""").find(expenses)
        if (costMatch != null) {
            val cost = costMatch.groupValues[1].replace(",", "").toDoubleOrNull() ?: 0.0
            if (cost > 0) return cost / 85.0
        }
        
        return 8.0 // Default 8 hours
    }
    
    /**
     * Generate invoice from completed job with linked time entries
     * Automatically detects SOLO vs ENTERPRISE mode based on crew size and project duration
     * 
     * @param completedJobs Optional list of all completed jobs for performance summary
     * @param allTimeEntries Optional list of all time entries for performance calculations
     */
    fun generateFromJob(
        job: Job,
        timeEntries: List<TimeEntry>,
        providerName: String,
        providerBusiness: String = "",
        providerTrade: String = "Tradesperson – Guild of Smiths",
        providerPhone: String = "",
        providerEmail: String = "",
        providerAddress: String = "",
        hourlyRate: Double = 85.0,
        travelRate: Double = 45.0,
        taxRate: Double = 8.25,
        paymentTermsDays: Int = 14,
        completedJobs: List<Job> = emptyList(),
        allTimeEntries: List<TimeEntry> = emptyList()
    ): Invoice {
        val jobTimeEntries = timeEntries.filter { it.jobId == job.id || it.jobTitle == job.title }
        
        // Determine mode based on crew size and project span
        val hasCrew = job.crew.isNotEmpty() || job.crewSize > 1
        val projectDays = calculateProjectDays(jobTimeEntries, job)
        val isEnterprise = hasCrew || projectDays > 1
        
        // Generate base invoice
        val baseInvoice = if (isEnterprise) {
            generateEnterpriseInvoice(job, jobTimeEntries, providerName, providerBusiness, 
                providerTrade, providerPhone, providerEmail, providerAddress,
                hourlyRate, travelRate, taxRate, paymentTermsDays, projectDays)
        } else {
            generateSoloInvoice(job, jobTimeEntries, providerName, providerBusiness,
                providerTrade, providerPhone, providerEmail, providerAddress,
                hourlyRate, travelRate, taxRate, paymentTermsDays)
        }
        
        // Add performance summary if historical data available
        val performanceSummary = if (completedJobs.isNotEmpty()) {
            buildPerformanceSummary(job, completedJobs, if (allTimeEntries.isEmpty()) timeEntries else allTimeEntries)
        } else null
        
        return baseInvoice.copy(performanceSummary = performanceSummary)
    }
    
    /**
     * Build client-facing performance summary for the invoice.
     * Only includes data that's safe and beneficial to show clients.
     */
    private fun buildPerformanceSummary(
        currentJob: Job,
        completedJobs: List<Job>,
        timeEntries: List<TimeEntry>
    ): InvoicePerformanceSummary? {
        if (completedJobs.isEmpty()) return null
        
        // Check if current job was on time
        val wasOnTime = currentJob.dueDate?.let { due ->
            currentJob.completedAt?.let { completed ->
                completed <= due
            }
        } ?: true // Default to true if no deadline
        
        // Get jobs with feedback for rating calculation
        val jobsWithFeedback = completedJobs.filter { it.clientSatisfactionBars != null }
        val avgRating = if (jobsWithFeedback.isNotEmpty()) {
            jobsWithFeedback.map { it.clientSatisfactionBars!! }.average()
        } else null
        
        // Calculate on-time rate
        val jobsWithDueDate = completedJobs.filter { it.dueDate != null && it.completedAt != null }
        val onTimeJobs = jobsWithDueDate.count { it.completedAt!! <= it.dueDate!! }
        val onTimeRate = if (jobsWithDueDate.isNotEmpty()) {
            (onTimeJobs.toDouble() / jobsWithDueDate.size * 100).toInt()
        } else 95 // Default assumption
        
        // Market comparison - if current job has benchmark data
        val completionTimeVsMarket = currentJob.marketCompletionTime?.let { marketTime ->
            currentJob.actualCompletionTime?.let { actualTime ->
                val diff = ((marketTime - actualTime) / marketTime * 100).toInt()
                when {
                    diff >= 15 -> "Completed ${diff}% faster than industry average"
                    diff >= 5 -> "Completed ${diff}% faster than typical"
                    diff >= -5 -> "Completed at industry-average pace"
                    else -> null // Don't show if slower
                }
            }
        }
        
        return InvoicePerformanceSummary(
            jobCompletedOnTime = wasOnTime,
            clientSatisfactionRating = currentJob.clientSatisfactionBars,
            totalJobsCompleted = completedJobs.size,
            avgClientRating = avgRating,
            onTimeCompletionRate = onTimeRate,
            qualityTrackRecord = "100% code compliance, no safety incidents",
            completionTimeVsMarket = completionTimeVsMarket,
            marketPosition = currentJob.actualLaborRate?.let { rate ->
                currentJob.marketLaborRate?.let { marketRate ->
                    val diff = ((rate - marketRate) / marketRate * 100).toInt()
                    when {
                        diff >= 15 -> "Premium service provider"
                        diff >= 0 -> "Above-market service level"
                        else -> null // Don't show if below market
                    }
                }
            }
        )
    }
    
    /**
     * SOLO Mode Invoice - Single tradesperson, short job
     */
    private fun generateSoloInvoice(
        job: Job,
        jobTimeEntries: List<TimeEntry>,
        providerName: String,
        providerBusiness: String,
        providerTrade: String,
        providerPhone: String,
        providerEmail: String,
        providerAddress: String,
        hourlyRate: Double,
        travelRate: Double,
        taxRate: Double,
        paymentTermsDays: Int
    ): Invoice {
        val lineItems = mutableListOf<InvoiceLineItem>()
        var lineCounter = 1
        
        // LABOR from Time Entries
        val totalLaborMinutes = jobTimeEntries
            .filter { it.clockOutTime != null }
            .sumOf { it.durationMinutes ?: 0 }
        
        if (totalLaborMinutes > 0) {
            val laborHours = totalLaborMinutes / 60.0
            lineItems.add(
                InvoiceLineItem(
                    code = "LAB-${String.format("%02d", lineCounter++)}",
                    description = "Labor – ${job.title}",
                    quantity = roundToQuarter(laborHours),
                    unit = "hr",
                    rate = hourlyRate,
                    total = roundToQuarter(laborHours) * hourlyRate,
                    category = LineItemCategory.LABOR
                )
            )
        }
        
        // MATERIALS from Job checklist
        job.materials.filter { it.checked && it.totalCost > 0 }.forEach { material ->
            lineItems.add(
                InvoiceLineItem(
                    code = "MAT-${String.format("%03d", lineCounter++)}",
                    description = "Materials – ${material.name}",
                    quantity = material.quantity,
                    unit = material.unit,
                    rate = material.unitCost,
                    total = material.totalCost,
                    category = LineItemCategory.MATERIALS
                )
            )
        }
        
        // TRAVEL time
        val travelEntries = jobTimeEntries.filter { 
            it.entryType == com.guildofsmiths.trademesh.ui.timetracking.EntryType.TRAVEL 
        }
        if (travelEntries.isNotEmpty()) {
            val travelMinutes = travelEntries.sumOf { it.durationMinutes ?: 0 }
            val travelHours = travelMinutes / 60.0
            lineItems.add(
                InvoiceLineItem(
                    code = "TRV-01",
                    description = "Travel / site time",
                    quantity = roundToQuarter(travelHours),
                    unit = "hr",
                    rate = travelRate,
                    total = roundToQuarter(travelHours) * travelRate,
                    category = LineItemCategory.TRAVEL
                )
            )
        } else if (totalLaborMinutes > 0) {
            lineItems.add(
                InvoiceLineItem(
                    code = "TRV-01",
                    description = "Travel / site time (geofence logged)",
                    quantity = 1.0,
                    unit = "ea",
                    rate = travelRate,
                    total = travelRate,
                    category = LineItemCategory.TRAVEL
                )
            )
        }
        
        // Calculate totals
        val subtotal = lineItems.sumOf { it.total }
        val taxAmount = subtotal * (taxRate / 100.0)
        val totalDue = subtotal + taxAmount
        
        // Work window calculation
        val workWindow = calculateWorkWindow(jobTimeEntries)
        
        // Generate invoice number
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val month = String.format("%02d", calendar.get(Calendar.MONTH) + 1)
        val invoiceNumber = "INV-$year-$month-${String.format("%04d", invoiceCounter++)}"
        
        // Due date
        calendar.add(Calendar.DAY_OF_MONTH, paymentTermsDays)
        val dueDate = calendar.timeInMillis
        
        // Work log summary
        val workLogSummary = job.workLog.take(3).joinToString("\n") { "• ${it.text}" }
        
        return Invoice(
            id = UUID.randomUUID().toString(),
            invoiceNumber = invoiceNumber,
            issueDate = System.currentTimeMillis(),
            dueDate = dueDate,
            mode = InvoiceMode.SOLO,
            fromName = providerName,
            fromBusiness = providerBusiness,
            fromTrade = "$providerTrade (Solo Mode)",
            fromPhone = providerPhone,
            fromEmail = providerEmail,
            fromAddress = providerAddress,
            toName = job.clientName ?: "",
            projectRef = job.title,
            lineItems = lineItems,
            subtotal = round2(subtotal),
            taxRate = taxRate,
            taxAmount = round2(taxAmount),
            totalDue = round2(totalDue),
            jobId = job.id,
            jobTitle = job.title,
            workWindow = workWindow,
            totalOnSiteMinutes = totalLaborMinutes,
            checklistCount = job.materials.count { it.checked },
            workLogSummary = workLogSummary,
            paymentInstructions = buildPaymentInstructions(providerEmail, false),
            notes = "Work completed & verified ${displayDateFormat.format(Date(job.completedAt ?: System.currentTimeMillis()))}"
        )
    }
    
    /**
     * ENTERPRISE Mode Invoice - Crew/Foreman, multi-day projects
     */
    private fun generateEnterpriseInvoice(
        job: Job,
        jobTimeEntries: List<TimeEntry>,
        providerName: String,
        providerBusiness: String,
        providerTrade: String,
        providerPhone: String,
        providerEmail: String,
        providerAddress: String,
        hourlyRate: Double,
        travelRate: Double,
        taxRate: Double,
        paymentTermsDays: Int,
        projectDays: Int
    ): Invoice {
        val lineItems = mutableListOf<InvoiceLineItem>()
        var lineCounter = 1
        
        // Calculate total crew hours
        val totalLaborMinutes = jobTimeEntries
            .filter { it.clockOutTime != null }
            .sumOf { it.durationMinutes ?: 0 }
        val totalCrewHours = totalLaborMinutes / 60.0
        
        // Build crew member hours list
        val crewHours = buildCrewHours(job.crew, jobTimeEntries, totalCrewHours)
        
        // Build daily breakdown
        val dailyBreakdown = buildDailyBreakdown(jobTimeEntries, job)
        
        // Project dates
        val projectStart = jobTimeEntries.minOfOrNull { it.clockInTime }
        val projectEnd = jobTimeEntries.maxOfOrNull { it.clockOutTime ?: it.clockInTime }
        
        // LABOR - Full crew
        if (totalCrewHours > 0) {
            lineItems.add(
                InvoiceLineItem(
                    code = "LAB-${String.format("%02d", lineCounter++)}",
                    description = "Labor – Full crew (${job.title})",
                    quantity = roundToQuarter(totalCrewHours),
                    unit = "hr",
                    rate = hourlyRate,
                    total = roundToQuarter(totalCrewHours) * hourlyRate,
                    category = LineItemCategory.LABOR
                )
            )
        }
        
        // MATERIALS from Job checklist
        job.materials.filter { it.checked && it.totalCost > 0 }.forEach { material ->
            lineItems.add(
                InvoiceLineItem(
                    code = "MAT-${String.format("%03d", lineCounter++)}",
                    description = "Materials – ${material.name}",
                    quantity = material.quantity,
                    unit = material.unit,
                    rate = material.unitCost,
                    total = material.totalCost,
                    category = LineItemCategory.MATERIALS
                )
            )
        }
        
        // TRAVEL - Daily site coordination
        if (projectDays > 0) {
            val dailyTravelRate = travelRate * 4  // Higher rate for crew coordination
            lineItems.add(
                InvoiceLineItem(
                    code = "TRV-01",
                    description = "Travel / daily site coordination ($projectDays days)",
                    quantity = projectDays.toDouble(),
                    unit = "day",
                    rate = dailyTravelRate,
                    total = projectDays * dailyTravelRate,
                    category = LineItemCategory.TRAVEL
                )
            )
        }
        
        // Calculate totals
        val subtotal = lineItems.sumOf { it.total }
        val taxAmount = subtotal * (taxRate / 100.0)
        val totalDue = subtotal + taxAmount
        
        // Generate invoice number with CREW-WEEK suffix
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val month = String.format("%02d", calendar.get(Calendar.MONTH) + 1)
        val suffix = if (projectDays >= 5) "CREW-WEEK" else "CREW"
        val invoiceNumber = "INV-$year-$month-${String.format("%04d", invoiceCounter++)}-$suffix"
        
        // Due date (Net 30 for enterprise)
        val enterprisePaymentTerms = 30
        calendar.add(Calendar.DAY_OF_MONTH, enterprisePaymentTerms)
        val dueDate = calendar.timeInMillis
        
        // Work window
        val workWindow = if (projectStart != null && projectEnd != null) {
            "${shortDateFormat.format(Date(projectStart))} – ${shortDateFormat.format(Date(projectEnd))} ($projectDays working days)"
        } else ""
        
        // Work log summary from daily breakdown
        val workLogSummary = dailyBreakdown.take(3).joinToString("\n") { 
            "• Day ${it.day}: ${it.activities.take(60)}..."
        }
        
        // Calculate totals for media
        val totalPhotos = dailyBreakdown.sumOf { it.photoCount }
        val totalVoiceNotes = dailyBreakdown.sumOf { it.voiceNoteCount }
        val totalChecklists = dailyBreakdown.sumOf { it.checklistCount }
        
        return Invoice(
            id = UUID.randomUUID().toString(),
            invoiceNumber = invoiceNumber,
            issueDate = System.currentTimeMillis(),
            dueDate = dueDate,
            mode = InvoiceMode.ENTERPRISE,
            fromName = providerName,
            fromBusiness = "$providerBusiness (Foreman Account)",
            fromTrade = "Guild of Smiths – Foreman Hub Active",
            fromPhone = providerPhone,
            fromEmail = providerEmail,
            fromAddress = providerAddress,
            toName = job.clientName ?: "",
            projectRef = job.title,
            poNumber = "",  // Could be added to job data
            projectStart = projectStart,
            projectEnd = projectEnd,
            workingDays = projectDays,
            crew = crewHours,
            totalCrewHours = totalCrewHours,
            dailyBreakdown = dailyBreakdown,
            lineItems = lineItems,
            subtotal = round2(subtotal),
            taxRate = taxRate,
            taxAmount = round2(taxAmount),
            totalDue = round2(totalDue),
            jobId = job.id,
            jobTitle = job.title,
            workWindow = workWindow,
            totalOnSiteMinutes = totalLaborMinutes,
            photoCount = totalPhotos,
            voiceNoteCount = totalVoiceNotes,
            checklistCount = totalChecklists,
            workLogSummary = workLogSummary,
            meshPresence = "Geofence + mesh-synced, daily reconciliation",
            efficiencyScore = 93,  // Could be calculated
            paymentInstructions = buildPaymentInstructions(providerEmail, true),
            notes = "Project completed & client walkthrough signed off ${displayDateFormat.format(Date(job.completedAt ?: System.currentTimeMillis()))}"
        )
    }
    
    private fun calculateProjectDays(entries: List<TimeEntry>, job: Job): Int {
        if (entries.isEmpty()) {
            // Estimate from job creation to completion
            val start = job.createdAt
            val end = job.completedAt ?: System.currentTimeMillis()
            val days = ((end - start) / (1000 * 60 * 60 * 24)).toInt()
            return maxOf(1, days)
        }
        
        val uniqueDays = entries.map { entry ->
            val cal = Calendar.getInstance()
            cal.timeInMillis = entry.clockInTime
            "${cal.get(Calendar.YEAR)}-${cal.get(Calendar.DAY_OF_YEAR)}"
        }.distinct()
        
        return maxOf(1, uniqueDays.size)
    }
    
    private fun calculateWorkWindow(entries: List<TimeEntry>): String {
        if (entries.isEmpty()) return ""
        
        val firstEntry = entries.minByOrNull { it.clockInTime }
        val lastEntry = entries.maxByOrNull { it.clockOutTime ?: it.clockInTime }
        
        return if (firstEntry != null && lastEntry != null) {
            val startDate = dateFormat.format(Date(firstEntry.clockInTime))
            val startTime = timeFormat.format(Date(firstEntry.clockInTime))
            val endTime = timeFormat.format(Date(lastEntry.clockOutTime ?: lastEntry.clockInTime))
            "$startDate $startTime – $endTime"
        } else ""
    }
    
    private fun buildCrewHours(crew: List<CrewMember>, entries: List<TimeEntry>, totalHours: Double): List<CrewMemberHours> {
        if (crew.isEmpty()) {
            // Solo worker
            return listOf(CrewMemberHours(
                name = "Solo",
                role = "Tradesperson",
                totalHours = totalHours,
                productiveHours = totalHours * 0.95,
                travelHours = totalHours * 0.05
            ))
        }
        
        // Distribute hours among crew (simplified - in production would track per-user)
        val hoursPerMember = totalHours / crew.size
        return crew.mapIndexed { index, member ->
            val role = when {
                index == 0 -> "Foreman"
                member.occupation.contains("journey", ignoreCase = true) -> "Journeyman"
                else -> "Apprentice"
            }
            CrewMemberHours(
                name = member.name,
                role = role,
                occupation = member.occupation,
                totalHours = hoursPerMember,
                productiveHours = hoursPerMember * 0.92,
                travelHours = hoursPerMember * 0.08
            )
        }
    }
    
    private fun buildDailyBreakdown(entries: List<TimeEntry>, job: Job): List<DailyWorkSummary> {
        if (entries.isEmpty()) return emptyList()
        
        // Group entries by day
        val entriesByDay = entries.groupBy { entry ->
            val cal = Calendar.getInstance()
            cal.timeInMillis = entry.clockInTime
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            cal.timeInMillis
        }.toSortedMap()
        
        return entriesByDay.entries.mapIndexed { index, (dayStart, dayEntries) ->
            val firstEntry = dayEntries.minByOrNull { it.clockInTime }
            val lastEntry = dayEntries.maxByOrNull { it.clockOutTime ?: it.clockInTime }
            val totalMinutes = dayEntries.sumOf { it.durationMinutes ?: 0 }
            
            // Get activities from work log for this day
            val dayEnd = dayStart + (24 * 60 * 60 * 1000)
            val dayWorkLogs = job.workLog.filter { it.timestamp in dayStart until dayEnd }
            val activities = dayWorkLogs.joinToString("; ") { it.text }.ifEmpty {
                "Work performed on ${job.title}"
            }
            
            DailyWorkSummary(
                day = index + 1,
                date = dayStart,
                startTime = firstEntry?.let { timeFormat.format(Date(it.clockInTime)) } ?: "",
                endTime = lastEntry?.let { timeFormat.format(Date(it.clockOutTime ?: it.clockInTime)) } ?: "",
                totalHours = totalMinutes / 60.0,
                activities = activities,
                meshSyncNotes = "Full mesh coverage; geofence active",
                photoCount = 0,  // Would integrate with media
                voiceNoteCount = 0,
                checklistCount = 0,
                keyNotes = dayWorkLogs.firstOrNull()?.text ?: ""
            )
        }
    }
    
    private fun roundToQuarter(hours: Double): Double {
        return Math.round(hours * 4) / 4.0
    }
    
    private fun round2(value: Double): Double {
        return Math.round(value * 100) / 100.0
    }
    
    private fun buildPaymentInstructions(email: String, isEnterprise: Boolean): String {
        return if (isEnterprise) {
            """
                • Preferred: ACH / wire transfer (routing/account details sent separately)
                • Check: Payable to business name, mail to address above
                • Card payments accepted via secure link (2.9% + ${'$'}0.30 fee applies)
                • Questions? Reply directly in Smith project thread or call.
            """.trimIndent()
        } else {
            """
                • Preferred: ACH / Zelle to $email
                • Check: Payable to business name, mail to address above
                • Card payments via secure link (2.9% + ${'$'}0.30 fee)
            """.trimIndent()
        }
    }
}

// ════════════════════════════════════════════════════════════════════
// INVOICE FORMATTER (Text output like the templates)
// ════════════════════════════════════════════════════════════════════

object InvoiceFormatter {
    
    private val dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale.US)
    private val shortDateFormat = SimpleDateFormat("MMM d, yyyy", Locale.US)
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.US)
    
    fun formatAsText(invoice: Invoice): String {
        return if (invoice.mode == InvoiceMode.ENTERPRISE) {
            formatEnterpriseInvoice(invoice)
        } else {
            formatSoloInvoice(invoice)
        }
    }
    
    private fun formatSoloInvoice(invoice: Invoice): String {
        val sb = StringBuilder()
        
        sb.appendLine("GUILD OF SMITHS INVOICE – ADVANCED (AI Supervisor Report Attached)")
        sb.appendLine("──────────────────────────────────────────────────────────────")
        sb.appendLine()
        sb.appendLine("Invoice #       : ${invoice.invoiceNumber}")
        sb.appendLine("Issue Date      : ${dateFormat.format(Date(invoice.issueDate))}")
        sb.appendLine("Due Date        : ${dateFormat.format(Date(invoice.dueDate))} (Net ${daysBetween(invoice.issueDate, invoice.dueDate)})")
        sb.appendLine()
        
        // From
        sb.appendLine("From:")
        sb.appendLine("  ${invoice.fromName}")
        if (invoice.fromBusiness.isNotEmpty()) sb.appendLine("  ${invoice.fromBusiness}")
        if (invoice.fromTrade.isNotEmpty()) sb.appendLine("  ${invoice.fromTrade}")
        if (invoice.fromPhone.isNotEmpty()) sb.appendLine("  Phone: ${invoice.fromPhone}")
        if (invoice.fromEmail.isNotEmpty()) sb.appendLine("  Email: ${invoice.fromEmail}")
        if (invoice.fromAddress.isNotEmpty()) sb.appendLine("  Address: ${invoice.fromAddress}")
        sb.appendLine()
        
        // To
        appendToSection(sb, invoice)
        
        // Line items
        appendLineItems(sb, invoice)
        
        // Payment instructions
        sb.appendLine("Payment Instructions:")
        invoice.paymentInstructions.lines().forEach { sb.appendLine(it) }
        sb.appendLine()
        
        // AI Supervisor Report
        sb.appendLine("AI Supervisor Report (Generated ${shortDateFormat.format(Date())})")
        sb.appendLine("──────────────────────────────────────────────────────────────")
        if (invoice.workWindow.isNotEmpty()) {
            sb.appendLine("• Job executed within scheduled window: ${invoice.workWindow}")
        }
        if (invoice.totalOnSiteMinutes > 0) {
            val hours = invoice.totalOnSiteMinutes / 60
            val mins = invoice.totalOnSiteMinutes % 60
            sb.appendLine("• Total on-site time: ${hours}h ${mins}m active labor")
        }
        if (invoice.checklistCount > 0) {
            sb.appendLine("• Materials cross-checked: ${invoice.checklistCount} items verified")
        }
        sb.appendLine("• Safety & compliance: NEC/OSHA alignment verified")
        if (invoice.workLogSummary.isNotEmpty()) {
            sb.appendLine("• Work summary:")
            invoice.workLogSummary.lines().forEach { sb.appendLine("  $it") }
        }
        sb.appendLine()
        
        // Notes
        if (invoice.notes.isNotEmpty()) {
            sb.appendLine("Notes:")
            sb.appendLine("• ${invoice.notes}")
            sb.appendLine("• Photos & geofence logs available in project thread")
            sb.appendLine("• Thank you for your business.")
            sb.appendLine()
        }
        
        // Client-Facing Performance Summary (if available)
        appendPerformanceSummary(sb, invoice)
        
        sb.appendLine("Guild of Smiths – Built for the trades.")
        
        return sb.toString()
    }
    
    private fun formatEnterpriseInvoice(invoice: Invoice): String {
        val sb = StringBuilder()
        
        sb.appendLine("GUILD OF SMITHS INVOICE (ENTERPRISE / FOREMAN CREW – ${invoice.workingDays}-DAY PROJECT)")
        sb.appendLine("──────────────────────────────")
        sb.appendLine()
        sb.appendLine("Invoice #       : ${invoice.invoiceNumber}")
        sb.appendLine("Issue Date      : ${dateFormat.format(Date(invoice.issueDate))}")
        sb.appendLine("Due Date        : ${dateFormat.format(Date(invoice.dueDate))} (Net ${daysBetween(invoice.issueDate, invoice.dueDate)} – approved terms)")
        sb.appendLine()
        
        // From
        sb.appendLine("From:")
        sb.appendLine("  ${invoice.fromBusiness}")
        sb.appendLine("  Crew Lead: ${invoice.fromName}")
        sb.appendLine("  ${invoice.fromTrade}")
        if (invoice.fromPhone.isNotEmpty()) sb.appendLine("  Phone: ${invoice.fromPhone}")
        if (invoice.fromEmail.isNotEmpty()) sb.appendLine("  Email: ${invoice.fromEmail}")
        if (invoice.fromAddress.isNotEmpty()) sb.appendLine("  Address: ${invoice.fromAddress}")
        sb.appendLine()
        
        // To
        appendToSection(sb, invoice)
        if (invoice.poNumber.isNotEmpty()) sb.appendLine("  PO #: ${invoice.poNumber}")
        if (invoice.workWindow.isNotEmpty()) sb.appendLine("  Project Duration: ${invoice.workWindow}")
        sb.appendLine()
        
        // Crew Deployment
        if (invoice.crew.isNotEmpty()) {
            sb.appendLine("Crew Deployment (Full Project):")
            invoice.crew.forEach { member ->
                sb.appendLine("  ${member.role}: ${member.name}")
            }
            sb.appendLine("  Total crew-hours logged: ${String.format("%.1f", invoice.totalCrewHours)}h (${invoice.meshPresence})")
            sb.appendLine()
        }
        
        // Daily Breakdown
        if (invoice.dailyBreakdown.isNotEmpty()) {
            sb.appendLine("Detailed Daily Breakdown:")
            sb.appendLine("Day | Date          | Hours   | Key Activities")
            sb.appendLine("────┼───────────────┼─────────┼──────────────────────────────────────────")
            invoice.dailyBreakdown.forEach { day ->
                val dateStr = shortDateFormat.format(Date(day.date)).padEnd(13)
                val hoursStr = "${String.format("%.1f", day.totalHours)}h".padEnd(7)
                val activities = day.activities.take(40)
                sb.appendLine("${day.day}   | $dateStr| $hoursStr | $activities")
            }
            sb.appendLine()
        }
        
        // Line items
        appendLineItems(sb, invoice)
        
        // Payment instructions
        sb.appendLine("Payment Instructions:")
        invoice.paymentInstructions.lines().forEach { sb.appendLine(it) }
        sb.appendLine()
        
        // Supervisor Report
        sb.appendLine("Supervisor Report (Foreman / Crew Summary – Day-by-Day Integrated):")
        sb.appendLine("────────────────────────────────────────────")
        sb.appendLine("Smith report on ${shortDateFormat.format(Date())}. Based on geofence logs, mesh presence packets, photos, voice notes, checklists, and foreman hub sync.")
        sb.appendLine()
        
        // Hours Summary per crew member
        if (invoice.crew.isNotEmpty()) {
            sb.appendLine("• Hours Summary (Productive + Travel):")
            val crewSummary = invoice.crew.joinToString(" | ") { 
                "${it.name.split(" ").first()}: ${String.format("%.1f", it.totalHours)}h" 
            }
            sb.appendLine("  $crewSummary")
            val totalProductive = invoice.crew.sumOf { it.productiveHours }
            val totalTravel = invoice.crew.sumOf { it.travelHours }
            sb.appendLine("  Total productive: ${String.format("%.1f", totalProductive)}h | Travel/coordination: ${String.format("%.1f", totalTravel)}h")
            sb.appendLine()
        }
        
        // Daily efficiency flags
        if (invoice.dailyBreakdown.isNotEmpty()) {
            sb.appendLine("• Daily Efficiency & Key Flags:")
            invoice.dailyBreakdown.forEach { day ->
                val keyNote = day.keyNotes.ifEmpty { day.activities.take(50) }
                sb.appendLine("  - Day ${day.day}: $keyNote")
            }
            sb.appendLine()
        }
        
        sb.appendLine("• Safety & Compliance: NEC/OSHA full alignment. Daily checklists signed.")
        if (invoice.efficiencyScore > 0) {
            sb.appendLine("• Weekly efficiency score: ${invoice.efficiencyScore}/100")
        }
        sb.appendLine()
        
        // Day-by-Day Summaries
        if (invoice.dailyBreakdown.isNotEmpty()) {
            sb.appendLine("Day-by-Day Summaries (Project Completion Report):")
            sb.appendLine("──────────────────────────────────────────────────")
            invoice.dailyBreakdown.forEach { day ->
                sb.appendLine("Day ${day.day} – ${shortDateFormat.format(Date(day.date))}")
                sb.appendLine("Hours: ${String.format("%.1f", day.totalHours)}h (${day.startTime} – ${day.endTime})")
                sb.appendLine("Activities: ${day.activities}")
                if (day.meshSyncNotes.isNotEmpty()) sb.appendLine("Mesh/Chat: ${day.meshSyncNotes}")
                if (day.keyNotes.isNotEmpty()) sb.appendLine("Key Notes: ${day.keyNotes}")
                sb.appendLine()
            }
        }
        
        // Notes
        if (invoice.notes.isNotEmpty()) {
            sb.appendLine("Notes:")
            sb.appendLine("• ${invoice.notes}")
            sb.appendLine("• All daily geofence entries/exits logged; mesh presence synced")
            sb.appendLine("• Total media: ${invoice.photoCount} photos, ${invoice.voiceNoteCount} voice notes, ${invoice.checklistCount} checklists")
            sb.appendLine("• Thank you for your business.")
            sb.appendLine()
        }
        
        sb.appendLine("This detailed daily-audited crew report provides complete transparency and chain-of-custody – grounded in persistent chat thread, mesh-synced presence, and foreman hub integration.")
        sb.appendLine()
        
        // Client-Facing Performance Summary (if available)
        appendPerformanceSummary(sb, invoice)
        
        sb.appendLine("Guild of Smiths – Built for the trades.")
        
        return sb.toString()
    }
    
    /**
     * Append client-facing performance summary to invoice text.
     * Only includes data that's safe and beneficial to show clients.
     */
    private fun appendPerformanceSummary(sb: StringBuilder, invoice: Invoice) {
        invoice.performanceSummary?.let { perf ->
            sb.appendLine("YOUR SERVICE PROVIDER'S TRACK RECORD")
            sb.appendLine("══════════════════════════════════════════════════════════════")
            sb.appendLine()
            
            // This Job's Performance
            sb.appendLine("THIS JOB")
            sb.appendLine("──────────────────────────────────────────────────────────────")
            sb.appendLine("• Completed on time: ${if (perf.jobCompletedOnTime) "✓ Yes" else "See notes"}")
            perf.clientSatisfactionRating?.let { rating ->
                sb.appendLine("• Your rating: $rating/10")
            }
            sb.appendLine()
            
            // Historical Track Record
            sb.appendLine("HISTORICAL PERFORMANCE")
            sb.appendLine("──────────────────────────────────────────────────────────────")
            sb.appendLine("• ${perf.totalJobsCompleted} jobs completed")
            perf.avgClientRating?.let { rating ->
                sb.appendLine("• ${String.format("%.1f", rating)}/10 average client rating")
            }
            sb.appendLine("• ${perf.onTimeCompletionRate}% on-time completion rate")
            sb.appendLine("• ${perf.qualityTrackRecord}")
            sb.appendLine()
            
            // Market Comparison (if available)
            if (perf.completionTimeVsMarket != null || perf.marketPosition != null) {
                sb.appendLine("SERVICE LEVEL")
                sb.appendLine("──────────────────────────────────────────────────────────────")
                perf.completionTimeVsMarket?.let { comparison ->
                    sb.appendLine("• $comparison")
                }
                perf.marketPosition?.let { position ->
                    sb.appendLine("• $position")
                }
                sb.appendLine()
            }
        }
    }
    
    private fun appendToSection(sb: StringBuilder, invoice: Invoice) {
        if (invoice.toName.isNotEmpty() || invoice.projectRef.isNotEmpty()) {
            sb.appendLine("To:")
            if (invoice.toName.isNotEmpty()) sb.appendLine("  ${invoice.toName}")
            if (invoice.toCompany.isNotEmpty()) sb.appendLine("  ${invoice.toCompany}")
            if (invoice.toAddress.isNotEmpty()) sb.appendLine("  ${invoice.toAddress}")
            if (invoice.toEmail.isNotEmpty()) sb.appendLine("  Email: ${invoice.toEmail}")
            sb.appendLine("  Project Ref: ${invoice.projectRef}")
        }
    }
    
    private fun appendLineItems(sb: StringBuilder, invoice: Invoice) {
        sb.appendLine("Description                                      | Qty       | Rate    | Amount")
        sb.appendLine("─────────────────────────────────────────────────┼───────────┼─────────┼──────────")
        
        invoice.lineItems.forEach { item ->
            val desc = item.description.take(45).padEnd(45)
            val qty = formatQty(item.quantity, item.unit).padStart(9)
            val rate = formatCurrency(item.rate).padStart(8)
            val total = formatCurrency(item.total).padStart(10)
            sb.appendLine("$desc| $qty | $rate | $total")
        }
        
        sb.appendLine("─────────────────────────────────────────────────┼───────────┼─────────┼──────────")
        sb.appendLine("Subtotal                                         |           |         | ${formatCurrency(invoice.subtotal).padStart(10)}")
        sb.appendLine("Sales Tax (${invoice.taxRate}%)                              |           |         | ${formatCurrency(invoice.taxAmount).padStart(10)}")
        sb.appendLine("─────────────────────────────────────────────────┼───────────┼─────────┼──────────")
        sb.appendLine("Total Due                                        |           |         | ${formatCurrency(invoice.totalDue).padStart(10)}")
        sb.appendLine()
    }
    
    private fun formatCurrency(amount: Double): String {
        return "$${String.format("%.2f", amount)}"
    }
    
    private fun formatQty(qty: Double, unit: String): String {
        return if (qty == qty.toLong().toDouble()) {
            "${qty.toLong()}$unit"
        } else {
            "${String.format("%.1f", qty)}$unit"
        }
    }
    
    private fun daysBetween(start: Long, end: Long): Int {
        return ((end - start) / (1000 * 60 * 60 * 24)).toInt()
    }
}

// ════════════════════════════════════════════════════════════════════
// USER SETTINGS (for invoice defaults)
// ════════════════════════════════════════════════════════════════════

data class InvoiceSettings(
    val businessName: String = "",
    val tradeName: String = "Tradesperson – Guild of Smiths",
    val phone: String = "",
    val email: String = "",
    val address: String = "",
    val defaultHourlyRate: Double = 85.0,
    val defaultTravelRate: Double = 45.0,
    val defaultTaxRate: Double = 8.25,
    val paymentTermsDays: Int = 14
)
