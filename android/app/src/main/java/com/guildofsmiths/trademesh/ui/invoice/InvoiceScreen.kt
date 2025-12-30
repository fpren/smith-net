package com.guildofsmiths.trademesh.ui.invoice

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.guildofsmiths.trademesh.ui.ConsoleHeader
import com.guildofsmiths.trademesh.ui.ConsoleSeparator
import com.guildofsmiths.trademesh.ui.ConsoleTheme
import java.text.SimpleDateFormat
import java.util.*

/**
 * Invoice Preview Screen
 * Displays generated invoice in Guild of Smiths format
 * Supports both SOLO and ENTERPRISE modes
 */

@Composable
fun InvoicePreviewDialog(
    invoice: Invoice,
    onDismiss: () -> Unit,
    onShare: (String) -> Unit = {}
) {
    val dateFormat = remember { SimpleDateFormat("MMMM d, yyyy", Locale.US) }
    val shortDateFormat = remember { SimpleDateFormat("MMM d, yyyy", Locale.US) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ConsoleTheme.background,
        modifier = Modifier.fillMaxWidth(0.98f).fillMaxHeight(0.95f),
        title = {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(text = "INVOICE PREVIEW", style = ConsoleTheme.header)
                        Text(
                            text = if (invoice.mode == InvoiceMode.ENTERPRISE) "[ENTERPRISE/CREW]" else "[SOLO]",
                            style = ConsoleTheme.caption.copy(
                                color = if (invoice.mode == InvoiceMode.ENTERPRISE) ConsoleTheme.accent else ConsoleTheme.success
                            )
                        )
                    }
                    Text(
                        text = "X",
                        style = ConsoleTheme.action,
                        modifier = Modifier.clickable { onDismiss() }
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // ═══════════════════════════════════════════════════
                // HEADER
                // ═══════════════════════════════════════════════════
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ConsoleTheme.surface)
                        .padding(12.dp)
                ) {
                    Column {
                        Text(
                            text = if (invoice.mode == InvoiceMode.ENTERPRISE) 
                                "GUILD OF SMITHS INVOICE (ENTERPRISE)" 
                                else "GUILD OF SMITHS INVOICE",
                            style = ConsoleTheme.header,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "──────────────────────────────",
                            style = ConsoleTheme.caption,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Invoice details
                InvoiceRow("Invoice #", invoice.invoiceNumber)
                InvoiceRow("Issue Date", dateFormat.format(Date(invoice.issueDate)))
                InvoiceRow("Due Date", "${dateFormat.format(Date(invoice.dueDate))} (Net ${daysBetween(invoice.issueDate, invoice.dueDate)})")
                
                // Enterprise: Project Duration
                if (invoice.mode == InvoiceMode.ENTERPRISE && invoice.projectStart != null) {
                    InvoiceRow("Project Duration", invoice.workWindow)
                }

                ConsoleSeparator()

                // ═══════════════════════════════════════════════════
                // FROM
                // ═══════════════════════════════════════════════════
                Text(text = "FROM:", style = ConsoleTheme.captionBold)
                Column(modifier = Modifier.padding(start = 16.dp)) {
                    Text(text = invoice.fromName, style = ConsoleTheme.body)
                    if (invoice.fromBusiness.isNotEmpty()) {
                        Text(text = invoice.fromBusiness, style = ConsoleTheme.body)
                    }
                    if (invoice.fromTrade.isNotEmpty()) {
                        Text(text = invoice.fromTrade, style = ConsoleTheme.caption)
                    }
                    if (invoice.fromPhone.isNotEmpty()) {
                        Text(text = "Phone: ${invoice.fromPhone}", style = ConsoleTheme.caption)
                    }
                    if (invoice.fromEmail.isNotEmpty()) {
                        Text(text = "Email: ${invoice.fromEmail}", style = ConsoleTheme.caption)
                    }
                }

                ConsoleSeparator()

                // ═══════════════════════════════════════════════════
                // TO
                // ═══════════════════════════════════════════════════
                if (invoice.toName.isNotEmpty() || invoice.projectRef.isNotEmpty()) {
                    Text(text = "TO:", style = ConsoleTheme.captionBold)
                    Column(modifier = Modifier.padding(start = 16.dp)) {
                        if (invoice.toName.isNotEmpty()) {
                            Text(text = invoice.toName, style = ConsoleTheme.body)
                        }
                        if (invoice.toCompany.isNotEmpty()) {
                            Text(text = invoice.toCompany, style = ConsoleTheme.body)
                        }
                        if (invoice.projectRef.isNotEmpty()) {
                            Text(text = "Project: ${invoice.projectRef}", style = ConsoleTheme.caption)
                        }
                        if (invoice.poNumber.isNotEmpty()) {
                            Text(text = "PO #: ${invoice.poNumber}", style = ConsoleTheme.caption)
                        }
                    }
                    ConsoleSeparator()
                }

                // ═══════════════════════════════════════════════════
                // CREW DEPLOYMENT (Enterprise only)
                // ═══════════════════════════════════════════════════
                if (invoice.mode == InvoiceMode.ENTERPRISE && invoice.crew.isNotEmpty()) {
                    Text(text = "CREW DEPLOYMENT", style = ConsoleTheme.captionBold)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(ConsoleTheme.surface)
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        invoice.crew.forEach { member ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "${member.role}: ${member.name}",
                                    style = ConsoleTheme.body
                                )
                                Text(
                                    text = "${String.format("%.1f", member.totalHours)}h",
                                    style = ConsoleTheme.bodyBold
                                )
                            }
                        }
                        ConsoleSeparator()
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Total crew-hours logged:",
                                style = ConsoleTheme.captionBold
                            )
                            Text(
                                text = "${String.format("%.1f", invoice.totalCrewHours)}h",
                                style = ConsoleTheme.bodyBold.copy(color = ConsoleTheme.accent)
                            )
                        }
                        Text(
                            text = "(${invoice.meshPresence})",
                            style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted)
                        )
                    }
                    ConsoleSeparator()
                }

                // ═══════════════════════════════════════════════════
                // DAILY BREAKDOWN (Enterprise only)
                // ═══════════════════════════════════════════════════
                if (invoice.mode == InvoiceMode.ENTERPRISE && invoice.dailyBreakdown.isNotEmpty()) {
                    Text(text = "DAILY BREAKDOWN", style = ConsoleTheme.captionBold)
                    
                    invoice.dailyBreakdown.forEach { day ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(ConsoleTheme.surface)
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Day ${day.day} – ${shortDateFormat.format(Date(day.date))}",
                                    style = ConsoleTheme.bodyBold
                                )
                                Text(
                                    text = "${String.format("%.1f", day.totalHours)}h",
                                    style = ConsoleTheme.bodyBold.copy(color = ConsoleTheme.accent)
                                )
                            }
                            if (day.startTime.isNotEmpty() && day.endTime.isNotEmpty()) {
                                Text(
                                    text = "${day.startTime} – ${day.endTime}",
                                    style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted)
                                )
                            }
                            Text(
                                text = day.activities.take(100) + if (day.activities.length > 100) "..." else "",
                                style = ConsoleTheme.caption
                            )
                            if (day.meshSyncNotes.isNotEmpty()) {
                                Text(
                                    text = "Mesh: ${day.meshSyncNotes}",
                                    style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    ConsoleSeparator()
                }

                // ═══════════════════════════════════════════════════
                // LINE ITEMS
                // ═══════════════════════════════════════════════════
                Text(text = "LINE ITEMS", style = ConsoleTheme.captionBold)

                // Header row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ConsoleTheme.surface)
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Description",
                        style = ConsoleTheme.captionBold,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "Qty",
                        style = ConsoleTheme.captionBold,
                        modifier = Modifier.width(50.dp),
                        textAlign = TextAlign.End
                    )
                    Text(
                        text = "Rate",
                        style = ConsoleTheme.captionBold,
                        modifier = Modifier.width(70.dp),
                        textAlign = TextAlign.End
                    )
                    Text(
                        text = "Amount",
                        style = ConsoleTheme.captionBold,
                        modifier = Modifier.width(80.dp),
                        textAlign = TextAlign.End
                    )
                }

                // Line items
                invoice.lineItems.forEach { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = item.description, style = ConsoleTheme.body)
                            Text(
                                text = "[${item.code}]",
                                style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted)
                            )
                        }
                        Text(
                            text = formatQty(item.quantity, item.unit),
                            style = ConsoleTheme.body,
                            modifier = Modifier.width(50.dp),
                            textAlign = TextAlign.End
                        )
                        Text(
                            text = formatCurrency(item.rate),
                            style = ConsoleTheme.body,
                            modifier = Modifier.width(70.dp),
                            textAlign = TextAlign.End
                        )
                        Text(
                            text = formatCurrency(item.total),
                            style = ConsoleTheme.bodyBold,
                            modifier = Modifier.width(80.dp),
                            textAlign = TextAlign.End
                        )
                    }
                }

                ConsoleSeparator()

                // ═══════════════════════════════════════════════════
                // TOTALS
                // ═══════════════════════════════════════════════════
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Column(horizontalAlignment = Alignment.End) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(text = "Subtotal:", style = ConsoleTheme.body)
                            Text(
                                text = formatCurrency(invoice.subtotal),
                                style = ConsoleTheme.body,
                                modifier = Modifier.width(80.dp),
                                textAlign = TextAlign.End
                            )
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                text = "Tax (${invoice.taxRate}%):",
                                style = ConsoleTheme.body
                            )
                            Text(
                                text = formatCurrency(invoice.taxAmount),
                                style = ConsoleTheme.body,
                                modifier = Modifier.width(80.dp),
                                textAlign = TextAlign.End
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(text = "TOTAL DUE:", style = ConsoleTheme.header)
                            Text(
                                text = formatCurrency(invoice.totalDue),
                                style = ConsoleTheme.header.copy(color = ConsoleTheme.accent),
                                modifier = Modifier.width(80.dp),
                                textAlign = TextAlign.End
                            )
                        }
                    }
                }

                ConsoleSeparator()

                // ═══════════════════════════════════════════════════
                // PAYMENT INSTRUCTIONS
                // ═══════════════════════════════════════════════════
                Text(text = "PAYMENT INSTRUCTIONS", style = ConsoleTheme.captionBold)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ConsoleTheme.surface)
                        .padding(12.dp)
                ) {
                    invoice.paymentInstructions.lines().forEach { line ->
                        if (line.isNotBlank()) {
                            Text(text = line, style = ConsoleTheme.caption)
                        }
                    }
                }

                // ═══════════════════════════════════════════════════
                // AI SUPERVISOR REPORT
                // ═══════════════════════════════════════════════════
                ConsoleSeparator()
                
                Text(
                    text = if (invoice.mode == InvoiceMode.ENTERPRISE) 
                        "SUPERVISOR REPORT (Foreman / Crew Summary)" 
                        else "AI SUPERVISOR REPORT",
                    style = ConsoleTheme.captionBold
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ConsoleTheme.accent.copy(alpha = 0.05f))
                        .border(1.dp, ConsoleTheme.accent.copy(alpha = 0.3f))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (invoice.workWindow.isNotEmpty()) {
                        Text(
                            text = "• Job executed: ${invoice.workWindow}",
                            style = ConsoleTheme.body
                        )
                    }
                    if (invoice.totalOnSiteMinutes > 0) {
                        val hours = invoice.totalOnSiteMinutes / 60
                        val mins = invoice.totalOnSiteMinutes % 60
                        Text(
                            text = "• Total on-site: ${hours}h ${mins}m",
                            style = ConsoleTheme.body
                        )
                    }
                    
                    // Enterprise: Crew hours breakdown
                    if (invoice.mode == InvoiceMode.ENTERPRISE && invoice.crew.isNotEmpty()) {
                        Text(text = "• Hours Summary:", style = ConsoleTheme.body)
                        val crewSummary = invoice.crew.joinToString(" | ") { 
                            "${it.name}: ${String.format("%.1f", it.totalHours)}h" 
                        }
                        Text(
                            text = "  $crewSummary",
                            style = ConsoleTheme.caption
                        )
                    }
                    
                    // Media counts
                    if (invoice.photoCount > 0 || invoice.voiceNoteCount > 0 || invoice.checklistCount > 0) {
                        Text(
                            text = "• Media: ${invoice.photoCount} photos, ${invoice.voiceNoteCount} voice notes, ${invoice.checklistCount} checklists",
                            style = ConsoleTheme.body
                        )
                    }
                    
                    // Efficiency score (Enterprise)
                    if (invoice.mode == InvoiceMode.ENTERPRISE && invoice.efficiencyScore > 0) {
                        Text(
                            text = "• Efficiency score: ${invoice.efficiencyScore}/100",
                            style = ConsoleTheme.body.copy(color = ConsoleTheme.success)
                        )
                    }
                    
                    if (invoice.workLogSummary.isNotEmpty()) {
                        Text(text = "• Work summary:", style = ConsoleTheme.body)
                        invoice.workLogSummary.lines().forEach { line ->
                            if (line.isNotBlank()) {
                                Text(
                                    text = "  $line",
                                    style = ConsoleTheme.caption
                                )
                            }
                        }
                    }
                }

                // ═══════════════════════════════════════════════════
                // NOTES
                // ═══════════════════════════════════════════════════
                if (invoice.notes.isNotEmpty()) {
                    ConsoleSeparator()
                    Text(text = "NOTES", style = ConsoleTheme.captionBold)
                    Text(text = "• ${invoice.notes}", style = ConsoleTheme.caption)
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Footer
                Text(
                    text = if (invoice.mode == InvoiceMode.ENTERPRISE)
                        "Guild of Smiths – Built for the trades. Foreman Hub active."
                        else "Guild of Smiths – Built for the trades.",
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
                        onShare(InvoiceFormatter.formatAsText(invoice))
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
private fun InvoiceRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = "$label:", style = ConsoleTheme.caption)
        Text(text = value, style = ConsoleTheme.body)
    }
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
