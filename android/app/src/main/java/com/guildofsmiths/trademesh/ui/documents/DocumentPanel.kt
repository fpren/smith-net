package com.guildofsmiths.trademesh.ui.documents

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.guildofsmiths.trademesh.planner.types.ExecutionItem
import com.guildofsmiths.trademesh.ui.ConsoleTheme
import com.guildofsmiths.trademesh.ui.jobboard.Job
import com.guildofsmiths.trademesh.ui.timetracking.TimeEntry

/**
 * Document Panel UI Component
 * 
 * Shows document availability status for a job with:
 * - Visual indicators (green = ready, gray = not ready)
 * - Reason text
 * - Prerequisites if not ready
 * - Generate action when ready
 */

@Composable
fun DocumentPanel(
    job: Job?,
    executionItems: List<ExecutionItem>?,
    timeEntries: List<TimeEntry>,
    onGenerateProposal: (Proposal) -> Unit,
    onGenerateReport: (Report) -> Unit,
    onGenerateInvoice: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Compute availability for all document types
    val availability = remember(job, executionItems, timeEntries) {
        DocumentAvailabilityRules.checkAllDocuments(job, executionItems, timeEntries)
    }
    
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(8.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header
        Text(
            text = "Documents",
            style = ConsoleTheme.header.copy(
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
        )
        
        // Document status cards
        availability.forEach { docAvail ->
            DocumentStatusCard(
                availability = docAvail,
                job = job,
                executionItems = executionItems,
                timeEntries = timeEntries,
                onGenerate = {
                    when (docAvail.type) {
                        DocumentType.PROPOSAL -> {
                            val proposal = if (executionItems?.isNotEmpty() == true) {
                                DocumentGenerator.generateProposal(
                                    executionItems = executionItems,
                                    title = job?.title ?: "Proposal"
                                )
                            } else if (job != null) {
                                DocumentGenerator.generateProposalFromJob(job)
                            } else null
                            proposal?.let { onGenerateProposal(it) }
                        }
                        DocumentType.REPORT -> {
                            if (job != null) {
                                val report = DocumentGenerator.generateReport(job, timeEntries)
                                onGenerateReport(report)
                            }
                        }
                        DocumentType.INVOICE -> {
                            onGenerateInvoice()
                        }
                    }
                }
            )
        }
    }
}

@Composable
private fun DocumentStatusCard(
    availability: DocumentAvailability,
    job: Job?,
    executionItems: List<ExecutionItem>?,
    timeEntries: List<TimeEntry>,
    onGenerate: () -> Unit
) {
    val isAvailable = availability.isAvailable
    val statusColor = if (isAvailable) ConsoleTheme.success else ConsoleTheme.textMuted
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isAvailable) ConsoleTheme.success.copy(alpha = 0.05f)
                else Color(0xFFF5F5F5),
                RoundedCornerShape(6.dp)
            )
            .then(
                if (isAvailable) Modifier.clickable { onGenerate() }
                else Modifier
            )
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icon and type
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Status indicator
                Text(
                    text = availability.type.icon,
                    style = ConsoleTheme.body.copy(
                        color = statusColor,
                        fontWeight = FontWeight.Bold
                    )
                )
                
                // Document type name
                Text(
                    text = availability.type.displayName,
                    style = ConsoleTheme.body.copy(
                        fontWeight = FontWeight.Medium,
                        color = if (isAvailable) ConsoleTheme.text else ConsoleTheme.textMuted
                    )
                )
                
                // Ready indicator
                if (isAvailable) {
                    Text(
                        text = "●",
                        style = ConsoleTheme.caption.copy(
                            color = ConsoleTheme.success,
                            fontSize = 8.sp
                        )
                    )
                }
            }
            
            // Reason / Status text
            Text(
                text = availability.reason,
                style = ConsoleTheme.caption.copy(
                    color = if (isAvailable) ConsoleTheme.success else ConsoleTheme.textMuted,
                    fontSize = 11.sp
                )
            )
            
            // Prerequisites (if not available)
            if (!isAvailable && availability.prerequisites.isNotEmpty()) {
                Column(
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    availability.prerequisites.forEach { prereq ->
                        Text(
                            text = "→ $prereq",
                            style = ConsoleTheme.caption.copy(
                                color = ConsoleTheme.textDim,
                                fontSize = 10.sp
                            )
                        )
                    }
                }
            }
        }
        
        // Generate action
        if (isAvailable) {
            Text(
                text = "GENERATE →",
                style = ConsoleTheme.caption.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = ConsoleTheme.accent,
                    fontSize = 11.sp
                )
            )
        }
    }
}

/**
 * Compact document readiness indicator for job cards
 */
@Composable
fun DocumentReadinessIndicator(
    job: Job?,
    timeEntries: List<TimeEntry>,
    modifier: Modifier = Modifier
) {
    if (job == null) return
    
    val invoiceAvail = remember(job, timeEntries) {
        DocumentAvailabilityRules.checkInvoice(job, timeEntries)
    }
    val reportAvail = remember(job) {
        DocumentAvailabilityRules.checkReport(job)
    }
    
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Report indicator
        DocumentIndicatorChip(
            type = DocumentType.REPORT,
            isAvailable = reportAvail.isAvailable
        )
        
        // Invoice indicator
        DocumentIndicatorChip(
            type = DocumentType.INVOICE,
            isAvailable = invoiceAvail.isAvailable
        )
    }
}

@Composable
private fun DocumentIndicatorChip(
    type: DocumentType,
    isAvailable: Boolean
) {
    val color = if (isAvailable) ConsoleTheme.success else ConsoleTheme.textMuted
    
    Row(
        modifier = Modifier
            .background(
                color.copy(alpha = 0.1f),
                RoundedCornerShape(4.dp)
            )
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = type.icon,
            style = ConsoleTheme.caption.copy(
                fontSize = 9.sp,
                color = color
            )
        )
        Text(
            text = if (isAvailable) "Ready" else "—",
            style = ConsoleTheme.caption.copy(
                fontSize = 9.sp,
                color = color
            )
        )
    }
}

/**
 * Proposal Preview Dialog
 */
@Composable
fun ProposalPreviewDialog(
    proposal: Proposal,
    onDismiss: () -> Unit,
    onShare: (String) -> Unit = {}
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ConsoleTheme.background,
        modifier = Modifier
            .fillMaxWidth(0.98f)
            .fillMaxHeight(0.9f),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(text = "PROPOSAL", style = ConsoleTheme.header)
                    Text(
                        text = "[${proposal.status.displayName.uppercase()}]",
                        style = ConsoleTheme.caption.copy(color = ConsoleTheme.accent)
                    )
                }
                Text(
                    text = "X",
                    style = ConsoleTheme.action,
                    modifier = Modifier.clickable { onDismiss() }
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Title
                Text(
                    text = proposal.title,
                    style = ConsoleTheme.header.copy(fontSize = 18.sp),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Scope Summary
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ConsoleTheme.surface)
                        .padding(12.dp)
                ) {
                    Text(
                        text = proposal.scopeSummary,
                        style = ConsoleTheme.body
                    )
                }
                
                // Tasks
                if (proposal.tasks.isNotEmpty()) {
                    SectionHeader("TASKS")
                    proposal.tasks.forEachIndexed { index, task ->
                        Text(
                            text = "${index + 1}. ${task.description}",
                            style = ConsoleTheme.body,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
                
                // Materials
                if (proposal.materials.isNotEmpty()) {
                    SectionHeader("MATERIALS")
                    proposal.materials.forEach { mat ->
                        val qty = if (mat.quantity != null && mat.unit != null) {
                            "${mat.quantity} ${mat.unit} - "
                        } else ""
                        Text(
                            text = "• ${qty}${mat.description}",
                            style = ConsoleTheme.body,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
                
                // Labor
                if (proposal.labor.isNotEmpty()) {
                    SectionHeader("LABOR")
                    proposal.labor.forEach { labor ->
                        val hours = labor.hours?.let { "${it}h - " } ?: ""
                        Text(
                            text = "• ${hours}${labor.description}",
                            style = ConsoleTheme.body,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
                
                // Estimates
                SectionHeader("ESTIMATES")
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ConsoleTheme.accent.copy(alpha = 0.05f))
                        .padding(12.dp)
                ) {
                    Column {
                        if (proposal.totalEstimatedHours > 0) {
                            Text(
                                text = "Estimated Hours: ${String.format("%.1f", proposal.totalEstimatedHours)}",
                                style = ConsoleTheme.body
                            )
                        }
                        Text(
                            text = "Material Items: ${proposal.totalEstimatedMaterials}",
                            style = ConsoleTheme.body
                        )
                    }
                }
                
                // Footer
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "This proposal is read-only. Actual costs may vary.",
                    style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    text = "[>] SHARE",
                    style = ConsoleTheme.action,
                    modifier = Modifier.clickable {
                        onShare(DocumentGenerator.formatProposalAsText(proposal))
                    }
                )
                Text(
                    text = "[OK] DONE",
                    style = ConsoleTheme.action.copy(color = ConsoleTheme.success),
                    modifier = Modifier.clickable { onDismiss() }
                )
            }
        },
        dismissButton = {}
    )
}

/**
 * Report Preview Dialog
 */
@Composable
fun ReportPreviewDialog(
    report: Report,
    onDismiss: () -> Unit,
    onShare: (String) -> Unit = {}
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ConsoleTheme.background,
        modifier = Modifier
            .fillMaxWidth(0.98f)
            .fillMaxHeight(0.9f),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(text = "WORK REPORT", style = ConsoleTheme.header)
                    Text(
                        text = "[${report.status.displayName.uppercase()}]",
                        style = ConsoleTheme.caption.copy(color = ConsoleTheme.accent)
                    )
                }
                Text(
                    text = "X",
                    style = ConsoleTheme.action,
                    modifier = Modifier.clickable { onDismiss() }
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Job Title
                Text(
                    text = report.jobTitle,
                    style = ConsoleTheme.header.copy(fontSize = 18.sp),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Duration
                SectionHeader("DURATION")
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ConsoleTheme.surface)
                        .padding(12.dp)
                ) {
                    Column {
                        Text(
                            text = "Total Hours: ${String.format("%.1f", report.totalHours)}",
                            style = ConsoleTheme.bodyBold.copy(color = ConsoleTheme.accent)
                        )
                    }
                }
                
                // Work Summary
                SectionHeader("WORK SUMMARY")
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ConsoleTheme.surface)
                        .padding(12.dp)
                ) {
                    Text(
                        text = report.workSummary,
                        style = ConsoleTheme.body
                    )
                }
                
                // Observations
                if (report.observations.isNotEmpty()) {
                    SectionHeader("OBSERVATIONS")
                    report.observations.forEach { obs ->
                        Text(
                            text = "• $obs",
                            style = ConsoleTheme.body,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
                
                // Materials Used
                if (report.materialsUsed.isNotEmpty()) {
                    SectionHeader("MATERIALS USED")
                    report.materialsUsed.forEach { mat ->
                        Text(
                            text = "• ${mat.quantity} ${mat.unit} - ${mat.name}",
                            style = ConsoleTheme.body,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
                
                // Crew
                if (report.crewMembers.isNotEmpty()) {
                    SectionHeader("CREW")
                    report.crewMembers.forEach { member ->
                        Text(
                            text = "• $member",
                            style = ConsoleTheme.body,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
                
                // Footer
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Guild of Smiths – Built for the trades.",
                    style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    text = "[>] SHARE",
                    style = ConsoleTheme.action,
                    modifier = Modifier.clickable {
                        onShare(DocumentGenerator.formatReportAsText(report))
                    }
                )
                Text(
                    text = "[OK] DONE",
                    style = ConsoleTheme.action.copy(color = ConsoleTheme.success),
                    modifier = Modifier.clickable { onDismiss() }
                )
            }
        },
        dismissButton = {}
    )
}

@Composable
private fun SectionHeader(text: String) {
    Column {
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = text,
            style = ConsoleTheme.captionBold.copy(
                letterSpacing = 1.sp
            )
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(ConsoleTheme.textMuted.copy(alpha = 0.3f))
        )
        Spacer(modifier = Modifier.height(4.dp))
    }
}
