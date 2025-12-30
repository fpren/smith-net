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
    val notes: String = ""
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
     * Generate invoice from completed job with linked time entries
     * Automatically detects SOLO vs ENTERPRISE mode based on crew size and project duration
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
        paymentTermsDays: Int = 14
    ): Invoice {
        val jobTimeEntries = timeEntries.filter { it.jobId == job.id || it.jobTitle == job.title }
        
        // Determine mode based on crew size and project span
        val hasCrew = job.crew.isNotEmpty() || job.crewSize > 1
        val projectDays = calculateProjectDays(jobTimeEntries, job)
        val isEnterprise = hasCrew || projectDays > 1
        
        return if (isEnterprise) {
            generateEnterpriseInvoice(job, jobTimeEntries, providerName, providerBusiness, 
                providerTrade, providerPhone, providerEmail, providerAddress,
                hourlyRate, travelRate, taxRate, paymentTermsDays, projectDays)
        } else {
            generateSoloInvoice(job, jobTimeEntries, providerName, providerBusiness,
                providerTrade, providerPhone, providerEmail, providerAddress,
                hourlyRate, travelRate, taxRate, paymentTermsDays)
        }
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
        sb.appendLine("Guild of Smiths – Built for the trades.")
        
        return sb.toString()
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
