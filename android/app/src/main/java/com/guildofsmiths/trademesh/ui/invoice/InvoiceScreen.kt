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
import com.guildofsmiths.trademesh.ui.theme2.LocalSmithColors
import com.guildofsmiths.trademesh.ui.theme2.SmithDialog
import com.guildofsmiths.trademesh.ui.theme2.SmithType
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
    onShare: (String) -> Unit = {},
    bolText: String? = null,
    onShareBol: (String) -> Unit = {},
    onPreviewRendered: (() -> Unit)? = null
) {
    val colors = LocalSmithColors.current
    val dateFormat = remember { SimpleDateFormat("MMMM d, yyyy", Locale.US) }
    val shortDateFormat = remember { SimpleDateFormat("MMM d, yyyy", Locale.US) }
    // Default to OFF — user must opt in to bundle the BOL with the invoice
    var attachBol by remember { mutableStateOf(false) }
    
    SmithDialog(
        title = "Invoice preview",
        onDismiss = onDismiss,
        sizeFraction = 0.98f to 0.95f,
        actions = {
            Column(horizontalAlignment = Alignment.End) {
                if (bolText != null) {
                    Row(
                        modifier = Modifier.padding(bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .background(
                                    if (attachBol) colors.accent else colors.bgPanel
                                )
                                .clickable { attachBol = !attachBol },
                            contentAlignment = Alignment.Center
                        ) {
                            if (attachBol) {
                                Text("✓", style = SmithType.caption.copy(color = colors.inkOnAccent))
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Attach BOL to invoice",
                            style = SmithType.caption.copy(color = colors.ink),
                            modifier = Modifier.clickable { attachBol = !attachBol }
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (onPreviewRendered != null) {
                        Text(
                            text = "[>] PREVIEW & SHARE",
                            style = SmithType.action.copy(color = colors.accent),
                            modifier = Modifier.clickable { onPreviewRendered() }
                        )
                    }
                    Text(
                        text = "[>] TEXT",
                        style = SmithType.action.copy(color = colors.inkMuted),
                        modifier = Modifier.clickable {
                            val invoiceText = InvoiceFormatter.formatAsText(invoice)
                            val payload = if (attachBol && bolText != null) invoiceText + "\n\n" + bolText else invoiceText
                            onShare(payload)
                        }
                    )
                    Text(
                        text = "[OK] DONE",
                        style = SmithType.action.copy(color = colors.statusOnline),
                        modifier = Modifier.clickable { onDismiss() }
                    )
                }
            }
        },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (invoice.mode == InvoiceMode.ENTERPRISE) "[ENTERPRISE/CREW]" else "[SOLO]",
                style = SmithType.caption.copy(
                    color = if (invoice.mode == InvoiceMode.ENTERPRISE) colors.accent else colors.statusOnline
                )
            )
            Text(
                text = "X",
                style = SmithType.action.copy(color = colors.accent),
                modifier = Modifier.clickable { onDismiss() }
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
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
                        .background(colors.bgPanel)
                        .padding(12.dp)
                ) {
                    Column {
                        Text(
                            text = if (invoice.mode == InvoiceMode.ENTERPRISE) 
                                "GUILD OF SMITHS INVOICE (ENTERPRISE)" 
                                else "GUILD OF SMITHS INVOICE",
                            style = SmithType.header.copy(color = colors.ink),
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "──────────────────────────────",
                            style = SmithType.caption.copy(color = colors.inkMuted),
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
                Text(text = "FROM:", style = SmithType.captionBold.copy(color = colors.inkMuted))
                Column(modifier = Modifier.padding(start = 16.dp)) {
                    Text(text = invoice.fromName, style = SmithType.body.copy(color = colors.ink))
                    if (invoice.fromBusiness.isNotEmpty()) {
                        Text(text = invoice.fromBusiness, style = SmithType.body.copy(color = colors.ink))
                    }
                    if (invoice.fromTrade.isNotEmpty()) {
                        Text(text = invoice.fromTrade, style = SmithType.caption.copy(color = colors.inkMuted))
                    }
                    if (invoice.fromPhone.isNotEmpty()) {
                        Text(text = "Phone: ${invoice.fromPhone}", style = SmithType.caption.copy(color = colors.inkMuted))
                    }
                    if (invoice.fromEmail.isNotEmpty()) {
                        Text(text = "Email: ${invoice.fromEmail}", style = SmithType.caption.copy(color = colors.inkMuted))
                    }
                }

                ConsoleSeparator()

                // ═══════════════════════════════════════════════════
                // TO
                // ═══════════════════════════════════════════════════
                if (invoice.toName.isNotEmpty() || invoice.projectRef.isNotEmpty()) {
                    Text(text = "TO:", style = SmithType.captionBold.copy(color = colors.inkMuted))
                    Column(modifier = Modifier.padding(start = 16.dp)) {
                        if (invoice.toName.isNotEmpty()) {
                            Text(text = invoice.toName, style = SmithType.body.copy(color = colors.ink))
                        }
                        if (invoice.toCompany.isNotEmpty()) {
                            Text(text = invoice.toCompany, style = SmithType.body.copy(color = colors.ink))
                        }
                        if (invoice.projectRef.isNotEmpty()) {
                            Text(text = "Project: ${invoice.projectRef}", style = SmithType.caption.copy(color = colors.inkMuted))
                        }
                        if (invoice.poNumber.isNotEmpty()) {
                            Text(text = "PO #: ${invoice.poNumber}", style = SmithType.caption.copy(color = colors.inkMuted))
                        }
                    }
                    ConsoleSeparator()
                }

                // ═══════════════════════════════════════════════════
                // CREW DEPLOYMENT (Enterprise only)
                // ═══════════════════════════════════════════════════
                if (invoice.mode == InvoiceMode.ENTERPRISE && invoice.crew.isNotEmpty()) {
                    Text(text = "CREW DEPLOYMENT", style = SmithType.captionBold.copy(color = colors.inkMuted))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(colors.bgPanel)
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
                                    style = SmithType.body.copy(color = colors.ink)
                                )
                                Text(
                                    text = "${String.format("%.1f", member.totalHours)}h",
                                    style = SmithType.bodyBold.copy(color = colors.ink)
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
                                style = SmithType.captionBold.copy(color = colors.inkMuted)
                            )
                            Text(
                                text = "${String.format("%.1f", invoice.totalCrewHours)}h",
                                style = SmithType.bodyBold.copy(color = colors.accent)
                            )
                        }
                        Text(
                            text = "(${invoice.meshPresence})",
                            style = SmithType.caption.copy(color = colors.inkMuted)
                        )
                    }
                    ConsoleSeparator()
                }

                // ═══════════════════════════════════════════════════
                // DAILY BREAKDOWN (Enterprise only)
                // ═══════════════════════════════════════════════════
                if (invoice.mode == InvoiceMode.ENTERPRISE && invoice.dailyBreakdown.isNotEmpty()) {
                    Text(text = "DAILY BREAKDOWN", style = SmithType.captionBold.copy(color = colors.inkMuted))
                    
                    invoice.dailyBreakdown.forEach { day ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(colors.bgPanel)
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Day ${day.day} – ${shortDateFormat.format(Date(day.date))}",
                                    style = SmithType.bodyBold.copy(color = colors.ink)
                                )
                                Text(
                                    text = "${String.format("%.1f", day.totalHours)}h",
                                    style = SmithType.bodyBold.copy(color = colors.accent)
                                )
                            }
                            if (day.startTime.isNotEmpty() && day.endTime.isNotEmpty()) {
                                Text(
                                    text = "${day.startTime} – ${day.endTime}",
                                    style = SmithType.caption.copy(color = colors.inkMuted)
                                )
                            }
                            Text(
                                text = day.activities.take(100) + if (day.activities.length > 100) "..." else "",
                                style = SmithType.caption.copy(color = colors.inkMuted)
                            )
                            if (day.meshSyncNotes.isNotEmpty()) {
                                Text(
                                    text = "Mesh: ${day.meshSyncNotes}",
                                    style = SmithType.caption.copy(color = colors.inkMuted)
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
                Text(text = "LINE ITEMS", style = SmithType.captionBold.copy(color = colors.inkMuted))

                // Header row: Description | Amount. Qty x Rate moves to a per-item
                // sub-line so the description has room in the narrow preview and the
                // amount stays right-aligned instead of wrapping one char per line.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.bgPanel)
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Description",
                        style = SmithType.captionBold.copy(color = colors.inkMuted),
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "Amount",
                        style = SmithType.captionBold.copy(color = colors.inkMuted),
                        textAlign = TextAlign.End
                    )
                }

                // Line items
                invoice.lineItems.forEach { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            Text(text = item.description, style = SmithType.body.copy(color = colors.ink))
                            Text(
                                text = "[${item.code}]  ${formatQty(item.quantity, item.unit)} x ${formatCurrency(item.rate)}",
                                style = SmithType.caption.copy(color = colors.inkMuted)
                            )
                        }
                        Text(
                            text = formatCurrency(item.total),
                            style = SmithType.bodyBold.copy(color = colors.ink),
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
                            Text(text = "Subtotal:", style = SmithType.body.copy(color = colors.ink))
                            Text(
                                text = formatCurrency(invoice.subtotal),
                                style = SmithType.body.copy(color = colors.ink),
                                modifier = Modifier.width(80.dp),
                                textAlign = TextAlign.End
                            )
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                text = "Tax (${invoice.taxRate}%):",
                                style = SmithType.body.copy(color = colors.ink)
                            )
                            Text(
                                text = formatCurrency(invoice.taxAmount),
                                style = SmithType.body.copy(color = colors.ink),
                                modifier = Modifier.width(80.dp),
                                textAlign = TextAlign.End
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(text = "TOTAL DUE:", style = SmithType.header.copy(color = colors.ink))
                            Text(
                                text = formatCurrency(invoice.totalDue),
                                style = SmithType.header.copy(color = colors.accent),
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
                Text(text = "PAYMENT INSTRUCTIONS", style = SmithType.captionBold.copy(color = colors.inkMuted))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.bgPanel)
                        .padding(12.dp)
                ) {
                    invoice.paymentInstructions.lines().forEach { line ->
                        if (line.isNotBlank()) {
                            Text(text = line, style = SmithType.caption.copy(color = colors.inkMuted))
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
                    style = SmithType.captionBold.copy(color = colors.inkMuted)
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.accent.copy(alpha = 0.05f))
                        .border(1.dp, colors.accent.copy(alpha = 0.3f))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (invoice.workWindow.isNotEmpty()) {
                        Text(
                            text = "• Job executed: ${invoice.workWindow}",
                            style = SmithType.body.copy(color = colors.ink)
                        )
                    }
                    if (invoice.totalOnSiteMinutes > 0) {
                        val hours = invoice.totalOnSiteMinutes / 60
                        val mins = invoice.totalOnSiteMinutes % 60
                        Text(
                            text = "• Total on-site: ${hours}h ${mins}m",
                            style = SmithType.body.copy(color = colors.ink)
                        )
                    }
                    
                    // Enterprise: Crew hours breakdown
                    if (invoice.mode == InvoiceMode.ENTERPRISE && invoice.crew.isNotEmpty()) {
                        Text(text = "• Hours Summary:", style = SmithType.body.copy(color = colors.ink))
                        val crewSummary = invoice.crew.joinToString(" | ") { 
                            "${it.name}: ${String.format("%.1f", it.totalHours)}h" 
                        }
                        Text(
                            text = "  $crewSummary",
                            style = SmithType.caption.copy(color = colors.inkMuted)
                        )
                    }
                    
                    // Media counts
                    if (invoice.photoCount > 0 || invoice.voiceNoteCount > 0 || invoice.checklistCount > 0) {
                        Text(
                            text = "• Media: ${invoice.photoCount} photos, ${invoice.voiceNoteCount} voice notes, ${invoice.checklistCount} checklists",
                            style = SmithType.body.copy(color = colors.ink)
                        )
                    }
                    
                    // Efficiency score (Enterprise)
                    if (invoice.mode == InvoiceMode.ENTERPRISE && invoice.efficiencyScore > 0) {
                        Text(
                            text = "• Efficiency score: ${invoice.efficiencyScore}/100",
                            style = SmithType.body.copy(color = colors.statusOnline)
                        )
                    }
                    
                    if (invoice.workLogSummary.isNotEmpty()) {
                        Text(text = "• Work summary:", style = SmithType.body.copy(color = colors.ink))
                        invoice.workLogSummary.lines().forEach { line ->
                            if (line.isNotBlank()) {
                                Text(
                                    text = "  $line",
                                    style = SmithType.caption.copy(color = colors.inkMuted)
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
                    Text(text = "NOTES", style = SmithType.captionBold.copy(color = colors.inkMuted))
                    Text(text = "• ${invoice.notes}", style = SmithType.caption.copy(color = colors.inkMuted))
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Footer
                Text(
                    text = if (invoice.mode == InvoiceMode.ENTERPRISE)
                        "Guild of Smiths – Built for the trades. Foreman Hub active."
                        else "Guild of Smiths – Built for the trades.",
                    style = SmithType.caption.copy(color = colors.inkMuted),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }
    }
}

@Composable
private fun InvoiceRow(label: String, value: String) {
    val colors = LocalSmithColors.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = "$label:", style = SmithType.caption.copy(color = colors.inkMuted))
        Text(text = value, style = SmithType.body.copy(color = colors.ink))
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
